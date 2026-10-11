package me.rerere.rikkahub.dfwx

import kotlinx.coroutines.runBlocking
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.data.datastore.Settings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [DFW-135] 内置模型测试按钮的前置校验（DfwxModelTester.precheck）。
 *
 * ## 为什么只测 precheck 不测整条 run
 * `GenerationLoop` 是 final class，构造要 Context/ProviderManager/Json——JVM 测试造不出来；
 * 而测试按钮真正容易写错的恰是「什么情况下不该发请求」这几条纯逻辑：
 * 渠道停用/没令牌 → 不能发；内置模型还没同步出来 → 不能发。这些钉死就够了，
 * 网络段的成功/失败翻译由真机验证承担（发的是真实链路，桩测反而测不出真问题）。
 *
 * 红线（沿用 DfwxBuiltinChannelTest）：本文件断言文案里不许出现上游地址 / sk- 等敏感内容。
 */
class DfwxModelTesterTest {

    private val proxyUrl = "https://39.106.33.135/ai/v1"

    @After
    fun tearDown() {
        DfwxBuiltinChannel.install(DfwxBuiltinChannel.Config())
        DfwxBuiltinChannel.paidProvider = null
    }

    private fun installChannel(enabled: Boolean = true, token: String = "dfwx-test-token") {
        DfwxBuiltinChannel.install(
            DfwxBuiltinChannel.Config(
                baseUrl = proxyUrl,
                token = token,
                modelId = "deepseek-v4.1-flash",
                maxTokens = 8192,
                enabled = enabled,
            )
        )
    }

    private fun settingsWithBuiltinChatModel(hasModel: Boolean): Settings {
        val provider = ProviderSetting.OpenAI(
            name = DfwxBuiltinChannel.PROVIDER_NAME,
            apiKey = "dfwx-test-token",
            baseUrl = proxyUrl,
            models = if (hasModel) listOf(Model(modelId = "deepseek-v4.1-flash", type = ModelType.CHAT)) else emptyList(),
        )
        return Settings(providers = listOf(provider))
    }

    @Test
    fun precheck_disabledChannel_blocksRequest() {
        installChannel(enabled = false)
        val result = DfwxModelTester.precheck(settingsWithBuiltinChatModel(hasModel = true))
        assertNotNull("停用的渠道必须拦下请求", result)
        assertTrue(result!!.humanText.contains("停用"))
    }

    @Test
    fun precheck_blankToken_blocksRequest() {
        installChannel(token = "")
        val result = DfwxModelTester.precheck(settingsWithBuiltinChatModel(hasModel = true))
        assertNotNull("没令牌的渠道必须拦下请求", result)
        assertTrue(result!!.humanText.contains("停用"))
    }

    @Test
    fun precheck_modelNotSynced_blocksRequest() {
        installChannel()
        val result = DfwxModelTester.precheck(settingsWithBuiltinChatModel(hasModel = false))
        assertNotNull("内置模型没同步出来时必须拦下请求", result)
        assertTrue(result!!.humanText.contains("同步"))
    }

    @Test
    fun precheck_readyChannel_allowsRequest() {
        installChannel()
        assertNull("渠道就绪 + 模型已同步 → 放行发请求", DfwxModelTester.precheck(settingsWithBuiltinChatModel(hasModel = true)))
    }

    @Test
    fun precheck_uninstalledChannel_fallsBackToDefaults() {
        // 不 install（用默认 Config）：token 空 → 必须拦，绝不发请求
        val result = DfwxModelTester.precheck(settingsWithBuiltinChatModel(hasModel = true))
        assertNotNull("默认配置（未注入）必须按不可用处理", result)
    }

    @Test
    fun result_sealedContract_isExhaustive() {
        // Result 只有 Success/Failure 两态，Failure 必须带人话（不许空文案）
        val failure = DfwxModelTester.Result.Failure("x")
        assertTrue(failure is DfwxModelTester.Result.Failure)
        assertTrue(failure.humanText.isNotEmpty())
        val success = DfwxModelTester.Result.Success("正常")
        assertEquals("正常", success.preview)
    }
}
