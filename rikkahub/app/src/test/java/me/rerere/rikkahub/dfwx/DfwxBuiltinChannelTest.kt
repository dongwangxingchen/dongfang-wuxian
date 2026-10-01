package me.rerere.rikkahub.dfwx

import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [DFW-73] 内置渠道（服务端中转）的身份识别与付费门。
 *
 * ## 为什么这几条非测不可
 * 这条渠道的钱是用户自己掏的（"我每周只会给它设定10块钱"），而拦截点只有一处：
 * `ModelList.kt` 里 `shouldBlockSelection()` 返回 true 才弹引导窗。
 * 这一处如果**判断反了**，结果是"未付费的人白用"或者"付了费的人被拦"，两种都是用户直接可见的事故，
 * 而且写错的时候编译、启动、聊天全都正常——只有真机点一下才看得出来。所以这里把判据钉死。
 *
 * 另外两条红线也在这里守住：
 *  1. **上游地址与真实 Key 不许出现在客户端**——本文件里只允许出现我们自己服务器的地址，
 *     任何 `aizhongzhuan` / `sk-` 出现在断言里都说明有东西漏进来了；
 *  2. **未注入宿主配置时按未付费处理**（fail-closed）：宁可多弹一次引导，也不让未付费的人白用。
 */
class DfwxBuiltinChannelTest {

    private val proxyUrl = "https://39.106.33.135/ai/v1"

    @After
    fun tearDown() {
        DfwxBuiltinChannel.install(DfwxBuiltinChannel.Config())
        DfwxBuiltinChannel.paidProvider = null
    }

    private fun installChannel(enabled: Boolean = true) {
        DfwxBuiltinChannel.install(
            DfwxBuiltinChannel.Config(
                baseUrl = proxyUrl,
                token = "dfwx-test-token",
                modelId = "deepseek-v4.1-flash",
                maxTokens = 8192,
                enabled = enabled,
            )
        )
    }

    /** 造一个"内置渠道 + 一个模型"，返回模型对象（findProvider 是按 model.id 反查的，必须用同一个对象）。 */
    private fun builtinProvider(modelId: String = "deepseek-v4.1-flash"): Pair<ProviderSetting.OpenAI, Model> {
        val model = Model(modelId = modelId, displayName = modelId)
        return ProviderSetting.OpenAI(
            name = DfwxBuiltinChannel.PROVIDER_NAME,
            apiKey = "dfwx-test-token",
            baseUrl = proxyUrl,
            models = listOf(model),
        ) to model
    }

    private fun userProvider(): Pair<ProviderSetting.OpenAI, Model> {
        val model = Model(modelId = "gpt-4o", displayName = "gpt-4o")
        return ProviderSetting.OpenAI(
            name = "我自己的渠道",
            apiKey = "sk-user-own-key",
            baseUrl = "https://api.openai.com/v1",
            models = listOf(model),
        ) to model
    }

    // ── 身份识别 ──────────────────────────────────────────────────────────

    @Test
    fun identity_matchesBuiltinProviderByBaseUrl() {
        installChannel()
        val (provider, _) = builtinProvider()
        assertTrue("内置渠道必须被认出来", DfwxBuiltinChannel.isBuiltin(provider))
    }

    @Test
    fun identity_matchesEvenIfUserRenamedIt() {
        installChannel()
        val (provider, _) = builtinProvider()
        // 名字被改掉、只剩 baseUrl 对得上 —— 仍必须认出（否则用户在设置里改个名就能绕过付费门）
        assertTrue(DfwxBuiltinChannel.isBuiltin(provider.copy(name = "随便改的名字")))
    }

    @Test
    fun identity_toleratesTrailingSlashAndCase() {
        installChannel()
        val (provider, _) = builtinProvider()
        assertTrue(DfwxBuiltinChannel.isBuiltin(provider.copy(baseUrl = "$proxyUrl/")))
        assertTrue(DfwxBuiltinChannel.isBuiltin(provider.copy(baseUrl = "  $proxyUrl  ")))
        assertTrue(DfwxBuiltinChannel.isBuiltin(provider.copy(baseUrl = proxyUrl.uppercase())))
    }

    @Test
    fun identity_neverMatchesUserOwnProvider() {
        installChannel()
        val (provider, _) = userProvider()
        assertFalse("用户自己的渠道绝不能被当成内置渠道", DfwxBuiltinChannel.isBuiltin(provider))
    }

    @Test
    fun identity_matchesByProviderNameSoRemoteUrlChangeDoesNotDuplicate() {
        installChannel()
        val (provider, _) = builtinProvider()
        // 后台把 ai_base_url 换成新地址后，老渠道的 baseUrl 就对不上了；
        // 如果只认 baseUrl，同步时会再多播一条重复渠道。名字兜住这种情况。
        DfwxBuiltinChannel.install(
            DfwxBuiltinChannel.Config(baseUrl = "https://example.com/ai/v1", token = "t", modelId = "m", maxTokens = 1, enabled = true)
        )
        assertTrue(DfwxBuiltinChannel.isBuiltin(provider))
    }

    // ── 付费门 ────────────────────────────────────────────────────────────

    @Test
    fun gate_blocksUnpaidUserSelectingBuiltinChannel() {
        installChannel()
        DfwxBuiltinChannel.paidProvider = { false }
        val (provider, model) = builtinProvider()
        assertTrue(
            "未付费点内置渠道必须被拦下来弹引导窗",
            DfwxBuiltinChannel.shouldBlockSelection(model, listOf(provider)),
        )
    }

    @Test
    fun gate_allowsPaidUser() {
        installChannel()
        DfwxBuiltinChannel.paidProvider = { true }
        val (provider, model) = builtinProvider()
        assertFalse(DfwxBuiltinChannel.shouldBlockSelection(model, listOf(provider)))
    }

    @Test
    fun gate_neverTouchesUserOwnProviders() {
        installChannel()
        DfwxBuiltinChannel.paidProvider = { false }
        val (provider, model) = userProvider()
        assertFalse(
            "未付费也不能拦用户自己的渠道——那是在砸用户自己配好的东西",
            DfwxBuiltinChannel.shouldBlockSelection(model, listOf(provider)),
        )
    }

    @Test
    fun gate_failsClosedWhenHostNeverInstalled() {
        DfwxBuiltinChannel.install(DfwxBuiltinChannel.Config())
        DfwxBuiltinChannel.paidProvider = null
        assertFalse("宿主没注入付费状态时，isPaid() 必须是 false", DfwxBuiltinChannel.isPaid())
    }

    @Test
    fun gate_failsClosedWhenPaidProviderMissing() {
        installChannel()
        DfwxBuiltinChannel.paidProvider = null
        val (provider, model) = builtinProvider()
        assertTrue(
            "宿主没告诉我们付费状态时，默认按未付费拦下来（宁可多弹一次）",
            DfwxBuiltinChannel.shouldBlockSelection(model, listOf(provider)),
        )
    }

    @Test
    fun gate_allowsEverythingWhenChannelDisabled() {
        // 后台 ai_disabled=true，或令牌为空 → 渠道不可用 → 放行（此时上游也连不上，拦着反而让人莫名其妙）
        installChannel(enabled = false)
        DfwxBuiltinChannel.paidProvider = { false }
        val (provider, model) = builtinProvider()
        assertFalse(DfwxBuiltinChannel.shouldBlockSelection(model, listOf(provider)))
    }

    @Test
    fun gate_allowsWhenModelNotInAnyProviderList() {
        installChannel()
        DfwxBuiltinChannel.paidProvider = { false }
        val orphan = Model(modelId = "deepseek-v4.1-flash", displayName = "deepseek-v4.1-flash")
        assertFalse(DfwxBuiltinChannel.shouldBlockSelection(orphan, emptyList()))
    }

    // ── 红线 ──────────────────────────────────────────────────────────────

    @Test
    fun config_defaultsAreOurOwnServerOnly() {
        assertEquals(proxyUrl, DfwxBuiltinChannel.DEFAULT_BASE_URL)
        assertFalse(
            "客户端默认地址里不许出现上游中转站",
            DfwxBuiltinChannel.DEFAULT_BASE_URL.contains("aizhongzhuan"),
        )
        // 默认令牌必须是空 —— 真令牌由宿主 resValue 注入，本模块源码里一个字都不该有。
        assertEquals("", DfwxBuiltinChannel.Config().token)
    }

    @Test
    fun config_maxOutputIs8192() {
        assertEquals(8192, DfwxBuiltinChannel.DEFAULT_MAX_TOKENS)
    }

    @Test
    fun config_modelNameIsExactlyTheUpstreamId() {
        // 用户 2026-10-01 明确纠正过："名字你不要显示为 DeepSeek4.1，得叫 deepseek-v4.1-flash"
        assertEquals("deepseek-v4.1-flash", DfwxBuiltinChannel.DEFAULT_MODEL_ID)
    }
}
