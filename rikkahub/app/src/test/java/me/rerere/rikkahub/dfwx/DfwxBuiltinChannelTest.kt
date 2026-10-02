package me.rerere.rikkahub.dfwx

import me.rerere.ai.provider.Model
import me.rerere.ai.provider.Modality
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.data.datastore.DEFAULT_ASSISTANT_ID
import me.rerere.rikkahub.data.datastore.Settings
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

    // ── 同步：幂等 + 模型 id 跨轮稳定（DFW-26 审计查出的必现 bug） ──────────

    /**
     * 这条守的是一个**必现且用户可见**的 bug（DFW-26 审计查出）：
     * `Model.id` 默认是 `Uuid.random()`，每构造一次就换一个；而 RikkaHub 的 `findModelById()`
     * 按 `model.id` 精确匹配。旧实现每轮同步都新建 `Model(...)`，于是用户重启后
     * `chatModelId` 指向上一轮的随机 id → 查不到 → AI 页弹「请先选择模型」，**每次重启都要重选一次**。
     */
    // ── [DFW-77] 远程控制：显示名 / 请求路径 / 换模型 ─────────────────────

    /**
     * 用户 2026-10-01：
     * > "我希望你在我的控制台里面再加一个板块。我在里面可以改什么呢？
     * >  可以改URL、Key、上下文长度、最大输出、模型名字等等等等的……
     * >  但是我是远程控制的，而不是在软件内控制的。"
     *
     * 所以后台控制台改的每一项都必须**真的落到设置里**，而且不能把用户自己的渠道带坏。
     */
    @Test
    fun remoteDisplayName_isDecoupledFromTheUpstreamModelId() {
        DfwxBuiltinChannel.install(
            DfwxBuiltinChannel.Config(
                baseUrl = proxyUrl,
                token = "dfwx-test-token",
                modelId = "glm-5.3-flashx",
                displayName = "东方无限 · 极速",
                maxTokens = 8192,
            ),
        )
        val settings = DfwxBuiltinChannel.buildSyncedSettings(
            Settings().copy(providers = emptyList()),
            DfwxBuiltinChannel.current(),
        )
        val model = settings.providers.single { DfwxBuiltinChannel.isBuiltin(it) }.models.single()
        assertEquals("发给上游的必须是后台配的真实模型名", "glm-5.3-flashx", model.modelId)
        assertEquals("界面上要显示后台配的显示名", "东方无限 · 极速", model.displayName)
    }

    @Test
    fun blankDisplayName_fallsBackToTheModelId() {
        installChannel()
        val settings = DfwxBuiltinChannel.buildSyncedSettings(
            Settings().copy(providers = emptyList()),
            DfwxBuiltinChannel.current(),
        )
        val model = settings.providers.single { DfwxBuiltinChannel.isBuiltin(it) }.models.single()
        assertEquals("没配显示名就回落到模型名，不能显示空白", model.modelId, model.displayName)
    }

    @Test
    fun remoteChatPath_isApplied_andBlankFallsBackToTheDefault() {
        DfwxBuiltinChannel.install(
            DfwxBuiltinChannel.Config(
                baseUrl = proxyUrl,
                token = "t",
                modelId = "m",
                chatPath = "/v1/chat/completions",
                maxTokens = 4096,
            ),
        )
        val custom = DfwxBuiltinChannel.buildSyncedSettings(
            Settings().copy(providers = emptyList()),
            DfwxBuiltinChannel.current(),
        ).providers.single { DfwxBuiltinChannel.isBuiltin(it) } as me.rerere.ai.provider.ProviderSetting.OpenAI
        assertEquals("后台配的请求路径必须生效", "/v1/chat/completions", custom.chatCompletionsPath)

        installChannel()
        val fallback = DfwxBuiltinChannel.buildSyncedSettings(
            Settings().copy(providers = emptyList()),
            DfwxBuiltinChannel.current(),
        ).providers.single { DfwxBuiltinChannel.isBuiltin(it) } as me.rerere.ai.provider.ProviderSetting.OpenAI
        assertEquals(
            "没配请求路径就用 RikkaHub 的默认值",
            DfwxBuiltinChannel.DEFAULT_CHAT_PATH,
            fallback.chatCompletionsPath,
        )
    }

    @Test
    fun switchingTheRemoteModel_keepsTheSameProviderAndModelIds() {
        // 后台"切换模型"只是换 modelId/displayName，**不许**换 Provider/Model 的 UUID ——
        // 换了的话用户当前选中的模型就悬空了，等于每次远程换模型都要用户重选。
        installChannel()
        val fresh = Settings().copy(providers = emptyList())
        val before = DfwxBuiltinChannel.buildSyncedSettings(fresh, DfwxBuiltinChannel.current())
        val beforeProvider = before.providers.single { DfwxBuiltinChannel.isBuiltin(it) }
        val beforeModel = beforeProvider.models.single()

        DfwxBuiltinChannel.install(
            DfwxBuiltinChannel.Config(
                baseUrl = proxyUrl,
                token = "dfwx-test-token",
                modelId = "kimi-k3",
                displayName = "东方无限 · 长文",
                maxTokens = 8192,
            ),
        )
        val after = DfwxBuiltinChannel.buildSyncedSettings(before, DfwxBuiltinChannel.current())
        val afterProvider = after.providers.single { DfwxBuiltinChannel.isBuiltin(it) }
        val afterModel = afterProvider.models.single()

        assertEquals("换模型不许换 Provider UUID", beforeProvider.id, afterProvider.id)
        assertEquals("换模型不许换 Model UUID", beforeModel.id, afterModel.id)
        assertEquals("modelId 要真的换过去", "kimi-k3", afterModel.modelId)
        assertEquals("用户选中的模型不能悬空", before.chatModelId, after.chatModelId)
    }

    @Test
    fun sync_isIdempotent_andKeepsTheModelIdStable() {
        installChannel()
        val cfg = DfwxBuiltinChannel.current()
        val fresh = Settings().copy(providers = emptyList())
        val first = DfwxBuiltinChannel.buildSyncedSettings(fresh, cfg)
        val second = DfwxBuiltinChannel.buildSyncedSettings(first, cfg)

        val firstModelId = first.providers.single { DfwxBuiltinChannel.isBuiltin(it) }.models.single().id
        val secondModelId = second.providers.single { DfwxBuiltinChannel.isBuiltin(it) }.models.single().id
        assertEquals("模型 id 必须跨轮稳定，否则用户每次重启都要重选模型", firstModelId, secondModelId)
        assertEquals(
            "第二次同步不该再改任何东西（否则每次启动都白写一遍 DataStore）",
            first,
            second,
        )
    }

    @Test
    fun sync_preservesTheUsersSelectedModelAcrossLaunches() {
        installChannel()
        val cfg = DfwxBuiltinChannel.current()
        val first = DfwxBuiltinChannel.buildSyncedSettings(Settings().copy(providers = emptyList()), cfg)
        // 模拟"用户重启"：拿上一轮的 settings 再同步一次
        val second = DfwxBuiltinChannel.buildSyncedSettings(first, cfg)
        val provider = second.providers.single { DfwxBuiltinChannel.isBuiltin(it) }
        assertTrue(
            "重启后 chatModelId 必须仍能解析到内置模型（否则 AI 页会弹「请先选择模型」）",
            provider.models.any { it.id == second.chatModelId },
        )
    }

    @Test
    fun sync_keepsAnExistingBuiltinProviderInsteadOfDuplicatingIt() {
        installChannel()
        val cfg = DfwxBuiltinChannel.current()
        val once = DfwxBuiltinChannel.buildSyncedSettings(Settings().copy(providers = emptyList()), cfg)
        val twice = DfwxBuiltinChannel.buildSyncedSettings(once, cfg)
        assertEquals("内置渠道只能有一条，重复同步不许叠出第二条", 1, twice.providers.count { DfwxBuiltinChannel.isBuiltin(it) })
        assertEquals(
            "渠道 id 也要沿用旧的",
            once.providers.single { DfwxBuiltinChannel.isBuiltin(it) }.id,
            twice.providers.single { DfwxBuiltinChannel.isBuiltin(it) }.id,
        )
    }

    /**
     * [DFW-108 2026-10-02] **老用户回归用例 —— 这一条是补盲区。**
     *
     * ## 用户报的现象
     * > 「东方助手，它不是配置过提示词和其他东西吗……为什么现在全都清空了呢？
     * >   我的内置 AI 不应是这个头像吧」
     * 界面上：名字是「东方助手」，提示词空、头像变成模型图标。
     *
     * ## 为什么之前的测试没抓到
     * 现有覆盖人设的用例**全都是"全新安装"前提**（`providers = emptyList()`）。
     * 而真实用户早就有自己的渠道、全局模型也早就换成了别人的 ——
     * 那种情况下旧判据会**静默跳过**，默认助手一直是 vendor 的空壳。
     * 名字看着还在，是因为 UI 用了 vendor 的字符串兜底，**跟人设写没写进去无关**。
     *
     * 这条测试就是那个真实场景：**有别人的渠道 + 全局模型是别人的**，
     * 默认助手仍然必须拿到名字 / 提示词 / 头像。
     */
    @Test
    fun existingUser_whoseGlobalModelIsNotTheBuiltinOne_stillGetsThePersona() {
        installChannel()
        val cfg = DfwxBuiltinChannel.current()
        val (userProvider, userModel) = userProvider()
        val base = Settings().copy(providers = listOf(userProvider), chatModelId = userModel.id)

        val after = DfwxBuiltinChannel.buildSyncedSettings(base, cfg)
        val a = after.assistants.first { it.id == DEFAULT_ASSISTANT_ID }

        assertEquals(
            "老用户的默认助手也必须拿到「东方助手」这个名字",
            DfwxAssistantProfile.ASSISTANT_NAME,
            a.name,
        )
        assertTrue(
            "老用户的默认助手也必须拿到软件使用知识 —— 否则它答不了「这个软件怎么用」",
            a.systemPrompt.isNotBlank(),
        )
        assertEquals(
            "老用户的默认助手也必须用我们给的头像（用户给的那张紫色发光星体）",
            Avatar.Image(DfwxAssistantProfile.AI_AVATAR_URL),
            a.avatar,
        )
        assertTrue(
            "必须打开 useAssistantAvatar，否则聊天里显示的是模型图标而不是我们的头像",
            a.useAssistantAvatar,
        )
        assertEquals(
            "给默认助手写人设，绝不能顺手把用户自己的模型选择顶掉",
            userModel.id,
            after.chatModelId,
        )
    }

    @Test
    fun sync_neverTouchesAUsersOwnProvidersOrModelChoice() {
        installChannel()
        val cfg = DfwxBuiltinChannel.current()
        val (userProvider, userModel) = userProvider()
        val base = Settings().copy(providers = listOf(userProvider), chatModelId = userModel.id)
        val after = DfwxBuiltinChannel.buildSyncedSettings(base, cfg)
        assertEquals("用户自己的渠道必须原样保留", userProvider, after.providers.first())
        assertEquals("已有自己渠道的用户，模型选择不许被内置渠道顶掉", userModel.id, after.chatModelId)
    }

    /**
     * 这条守的是 DFW-26 审计查出的第 3 条：`maxTokens=8192` 曾经**静默失效**。
     * 原因有二：`Assistant.chatModelId` 默认是 null（跟随全局），且模型 id 每轮都变。
     */
    @Test
    fun sync_appliesMaxTokensToAssistantsThatFollowTheBuiltinModel() {
        installChannel()
        val cfg = DfwxBuiltinChannel.current()
        val after = DfwxBuiltinChannel.buildSyncedSettings(Settings().copy(providers = emptyList()), cfg)
        val builtinModelId = after.providers.single { DfwxBuiltinChannel.isBuiltin(it) }.models.single().id
        assertEquals("首装必须把内置模型设成默认模型", builtinModelId, after.chatModelId)
        assertTrue("至少要有助手跟着内置模型", after.assistants.isNotEmpty())
        for (assistant in after.assistants) {
            if (assistant.chatModelId == null || assistant.chatModelId == builtinModelId) {
                assertEquals(
                    "跟随内置模型的助手必须拿到后台配的最大输出（8192）",
                    cfg.maxTokens,
                    assistant.maxTokens,
                )
            }
        }
    }
    // ── [DFW-81] 看图能力 ────────────────────────────────────────────────

    /**
     * 用户 2026-10-01：给 AI 发图片，它只能看到"括号图片"。
     *
     * 链条：`Model.inputModalities` 默认只有 TEXT →
     * `OcrTransformer` 判定"模型看不见图" → 走 OCR 降级 →
     * OCR 模型没配 → `performOcr` 返回字面量 `"[Image]"` → 图片被替换成这段文字发给上游。
     *
     * 上游实测**支持**看图（1x1 红色 PNG：deepseek-v4.1-flash 答"粉色"、glm-5.3-flash 答"深红"），
     * 所以必须显式声明 IMAGE 输入。
     */
    @Test
    fun builtinModel_declaresImageInput_soImagesAreNotReplacedByTheImagePlaceholder() {
        val seeded = DfwxBuiltinChannel.buildSyncedSettings(Settings().copy(providers = emptyList()))
        val model = seeded.providers
            .filterIsInstance<ProviderSetting.OpenAI>()
            .first { it.name == DfwxBuiltinChannel.PROVIDER_NAME }
            .models.single()
        assertTrue(
            "内置模型必须声明 IMAGE 输入，否则 OcrTransformer 会把图片替换成字面量 [Image]（用户看到的『括号图片』）",
            Modality.IMAGE in model.inputModalities,
        )
        assertTrue("文本输入当然也要保留", Modality.TEXT in model.inputModalities)
        assertTrue("输出仍然只声明文本（这个模型不产图）", Modality.IMAGE !in model.outputModalities)
    }

    @Test
    fun reSync_keepsTheImageInputModality() {
        // 幂等性：第二轮同步（例如后台改了模型名之后）不能把 IMAGE 丢掉。
        val first = DfwxBuiltinChannel.buildSyncedSettings(Settings().copy(providers = emptyList()))
        val second = DfwxBuiltinChannel.buildSyncedSettings(first)
        val model = second.providers
            .filterIsInstance<ProviderSetting.OpenAI>()
            .first { it.name == DfwxBuiltinChannel.PROVIDER_NAME }
            .models.single()
        assertTrue("重复同步后仍须保留 IMAGE 输入", Modality.IMAGE in model.inputModalities)
    }

    // ── [DFW-84] 东方助手：名字 / 头像 / 软件知识 ─────────────────────────

    private fun builtinAssistant(settings: Settings): Assistant {
        val model = settings.providers
            .filterIsInstance<ProviderSetting.OpenAI>()
            .first { it.name == DfwxBuiltinChannel.PROVIDER_NAME }
            .models.single()
        return settings.assistants.first { it.chatModelId == model.id || it.chatModelId == null }
    }

    @Test
    fun freshInstall_seedsTheDongfangAssistant_withNameAvatarAndKnowledge() {
        val seeded = DfwxBuiltinChannel.buildSyncedSettings(Settings().copy(providers = emptyList()))
        val a = builtinAssistant(seeded)
        assertEquals("默认助手的名字必须是东方助手", DfwxAssistantProfile.ASSISTANT_NAME, a.name)
        assertTrue("必须带上软件使用知识，否则它答不了『这个软件怎么用』", a.systemPrompt.isNotBlank())
        // 知识里必须真的有"怎么用"的硬事实，而不是空话
        for (fact in listOf("软件库", "工具箱", "Download/东方无限", "诚信付费", "内置渠道")) {
            assertTrue("手册里缺少关键事实：$fact", a.systemPrompt.contains(fact))
        }
        assertFalse(
            "用户明确说不要制作历史 —— 提示词里不许出现内部实现/版本痕迹",
            a.systemPrompt.contains("versionCode") || a.systemPrompt.contains("DFW-"),
        )
        assertEquals("内置助手要用我们给的头像", Avatar.Image(DfwxAssistantProfile.AI_AVATAR_URL), a.avatar)
        assertTrue(
            "必须打开 useAssistantAvatar，否则聊天里显示的是模型图标而不是我们的头像",
            a.useAssistantAvatar,
        )
    }

    @Test
    fun freshInstall_seedsTheUserAvatarOnlyWhenUnset() {
        val seeded = DfwxBuiltinChannel.buildSyncedSettings(Settings().copy(providers = emptyList()))
        assertEquals(
            "用户头像默认给图3（紫色发光人形）",
            Avatar.Image(DfwxAssistantProfile.USER_AVATAR_URL),
            seeded.displaySetting.userAvatar,
        )
    }

    @Test
    fun userCustomisations_areNeverOverwritten() {
        val mine = Avatar.Emoji("🐳")
        val before = Settings().copy(providers = emptyList()).let { base ->
            base.copy(
                assistants = base.assistants.map { it.copy(name = "我的助手", systemPrompt = "自定义提示词", avatar = mine) },
                displaySetting = base.displaySetting.copy(userAvatar = mine),
            )
        }
        val after = DfwxBuiltinChannel.buildSyncedSettings(before)
        val a = after.assistants.first { it.name == "我的助手" }
        assertEquals("用户改过的名字不许被覆盖", "我的助手", a.name)
        assertEquals("用户写过的提示词不许被覆盖", "自定义提示词", a.systemPrompt)
        assertEquals("用户选过的头像不许被覆盖", mine, a.avatar)
        assertEquals("用户选过的用户头像不许被覆盖", mine, after.displaySetting.userAvatar)
    }

    @Test
    fun assistantsOnOtherProviders_areLeftAlone() {
        // 用户自己接的渠道：名字/头像/提示词一个都不许动 —— 用户说"别人对接新 API 站的时候用他们默认的"
        val other = me.rerere.ai.provider.Model(modelId = "gpt-x", displayName = "GPT-X")
        val otherProvider = ProviderSetting.OpenAI(
            name = "我自己的渠道",
            apiKey = "sk-x",
            baseUrl = "https://api.example.com",
            models = listOf(other),
        )
        val base = Settings().copy(providers = emptyList())
        val mine = base.copy(
            providers = base.providers + otherProvider,
            assistants = base.assistants.map { it.copy(chatModelId = other.id, avatar = Avatar.Dummy, name = "", systemPrompt = "") },
        )
        val after = DfwxBuiltinChannel.buildSyncedSettings(mine)
        for (a in after.assistants.filter { it.chatModelId == other.id }) {
            assertEquals("非内置助手的名字不许被改", "", a.name)
            assertEquals("非内置助手的提示词不许被改", "", a.systemPrompt)
            assertEquals("非内置助手的头像不许被改", Avatar.Dummy, a.avatar)
            assertFalse("非内置助手不许被打开 useAssistantAvatar", a.useAssistantAvatar)
        }
    }

    @Test
    fun theProfileIsIdempotent() {
        val once = DfwxBuiltinChannel.buildSyncedSettings(Settings().copy(providers = emptyList()))
        val twice = DfwxBuiltinChannel.buildSyncedSettings(once)
        assertEquals("第二轮同步必须与第一轮完全相同（否则每次启动都白写 DataStore）", once, twice)
    }

    // ── [DFW-84] 提示词结构（依据 Anthropic 官方提示工程文档）──────────────

    /**
     * 依据：Anthropic《Prompting best practices》——
     * > "Structure prompts with XML tags ... especially when your prompt mixes instructions,
     * >  context, examples, and variable inputs. Wrapping each type of content in its own tag
     * >  reduces misinterpretation."
     *
     * 我们的提示词正是"行为规则 + 一大块软件手册 + FAQ + 底线"混在一起的形态，
     * 所以必须把**指令**和**资料**用标签分开；否则模型容易把手册里的陈述句当成对它下的指令。
     */
    @Test
    fun systemPrompt_separatesInstructionsFromReferenceMaterialWithXmlTags() {
        val p = DfwxAssistantProfile.SYSTEM_PROMPT
        for (tag in listOf("role", "style", "manual", "faq", "rules")) {
            assertTrue("提示词必须用 <$tag> 标签分段（官方建议：混装内容要各自成标签）", p.contains("<$tag>") && p.contains("</$tag>"))
        }
        // 标签必须配对且不嵌套错乱
        for (tag in listOf("role", "style", "manual", "faq", "rules")) {
            assertEquals("<$tag> 开闭标签数量必须相等", p.split("<$tag>").size, p.split("</$tag>").size)
        }
        assertTrue(
            "必须明确告诉模型 <manual> 是资料而不是指令，否则它会照着手册的陈述句行动",
            p.contains("这是给你查阅的资料") && p.contains("不是对你的指令"),
        )
        // 顺序：指令在前、资料在中、底线在后 —— 首尾都是"对模型说的话"
        assertTrue("<role> 必须在最前面", p.trimStart().startsWith("<role>"))
        assertTrue("<rules> 必须在最后面", p.trimEnd().endsWith("</rules>"))
    }

    // ── [DFW-114] 能力开关：只开一次，之后尊重用户 ──────────────────────────

    /**
     * 升级后**第一次**启动：内置助手必须拿到「记忆」和「翻聊天记录」。
     *
     * 用户 2026-10-02 要求「东方助手要非常强大」，但 RikkaHub 这两个能力
     * **默认全关**（`Assistant.kt:29` / `:31`），所以开箱即用的助手其实
     * 记不住任何事、也答不了"我们上次聊了什么" —— 从界面上完全看不出来。
     */
    @Test
    fun capabilities_firstRun_enablesMemoryAndRecentChatsForTheBuiltinAssistant() {
        installChannel()
        val cfg = DfwxBuiltinChannel.current()
        val (userProvider, userModel) = userProvider()
        val base = Settings().copy(providers = listOf(userProvider), chatModelId = userModel.id)

        val after = DfwxBuiltinChannel.buildSyncedSettings(base, cfg, enableCapabilities = true)
        val a = after.assistants.first { it.id == DEFAULT_ASSISTANT_ID }

        assertTrue(
            "首次同步必须打开「记忆」—— 否则助手记不住任何事，用户会以为它变笨了",
            a.enableMemory,
        )
        assertTrue(
            "首次同步必须打开「翻聊天记录」—— 否则它答不了「我们上次聊了什么」",
            a.enableRecentChatsReference,
        )
    }

    /**
     * **之后**每次启动都不许再动这两个开关 —— 用户手动关掉必须被尊重。
     *
     * ## 这是本卡最容易做错的地方
     * 这两个字段是 `Boolean`、默认 `false`，**分不出「用户主动关掉了」和「从来没设置过」**
     * （字符串/头像可以靠 `isBlank()` / `Avatar.Dummy` 判断"还没设过"，布尔值没有"空"）。
     *
     * 一旦写成"每次启动都硬开"，用户关掉的开关会在下次冷启时**自己弹回来**，
     * 而他在界面上找不到原因 —— 这种"设置不生效"的 bug 最难查。
     *
     * 所以真正要守的是：**`enableCapabilities = false` 时，一个 bit 都不许动。**
     */
    @Test
    fun capabilities_laterRuns_neverOverrideTheUsersChoice() {
        installChannel()
        val cfg = DfwxBuiltinChannel.current()
        val (userProvider, userModel) = userProvider()
        val userTurnedItOff = Settings().copy(
            providers = listOf(userProvider),
            chatModelId = userModel.id,
            assistants = Settings().assistants.map {
                if (it.id == DEFAULT_ASSISTANT_ID) {
                    it.copy(enableMemory = false, enableRecentChatsReference = false)
                } else {
                    it
                }
            },
        )

        val after = DfwxBuiltinChannel.buildSyncedSettings(
            userTurnedItOff,
            cfg,
            enableCapabilities = false,
        )
        val a = after.assistants.first { it.id == DEFAULT_ASSISTANT_ID }

        assertFalse("用户手动关掉的「记忆」不许在下次启动时自己弹回来", a.enableMemory)
        assertFalse("用户手动关掉的「翻聊天记录」不许自己弹回来", a.enableRecentChatsReference)
    }

    /**
     * 重复同步必须**零变化**。
     *
     * 这个类的注释里已经写明："`next == settings` 永远为假 → 每次启动都白写一遍 DataStore"。
     * 能力开关如果写成每次都给新对象，就会重新引入这个问题。
     */
    @Test
    fun capabilities_areIdempotent() {
        installChannel()
        val cfg = DfwxBuiltinChannel.current()
        val (userProvider, userModel) = userProvider()
        val base = Settings().copy(providers = listOf(userProvider), chatModelId = userModel.id)

        val once = DfwxBuiltinChannel.buildSyncedSettings(base, cfg, enableCapabilities = true)
        val twice = DfwxBuiltinChannel.buildSyncedSettings(once, cfg, enableCapabilities = true)

        assertEquals(
            "第二次同步必须与第一次完全相同（否则每次启动都白写一遍 DataStore）",
            once,
            twice,
        )
    }

    /**
     * 用户自己接的渠道，一个开关都不许碰 —— 与「人设只给内置渠道」是同一条红线。
     *
     * 用户明确要求过：「别人对接新 API 站的时候用他们默认的」。
     */
    @Test
    fun capabilities_neverTouchAssistantsBoundToOtherProviders() {
        installChannel()
        val cfg = DfwxBuiltinChannel.current()
        val (userProvider, userModel) = userProvider()
        // 把默认助手显式指到用户自己的渠道 —— 这时它就不该再被我们"播种"。
        val onUserProvider = Settings().copy(
            providers = listOf(userProvider),
            chatModelId = userModel.id,
            assistants = Settings().assistants.map {
                if (it.id == DEFAULT_ASSISTANT_ID) it.copy(chatModelId = userModel.id) else it
            },
        )

        val after = DfwxBuiltinChannel.buildSyncedSettings(
            onUserProvider,
            cfg,
            enableCapabilities = true,
        )
        val a = after.assistants.first { it.id == DEFAULT_ASSISTANT_ID }

        assertFalse(
            "助手被显式指到用户自己的渠道后，能力开关也不该由我们打开",
            a.enableMemory,
        )
        assertFalse(
            "同上，「翻聊天记录」也不许动",
            a.enableRecentChatsReference,
        )
    }

}
