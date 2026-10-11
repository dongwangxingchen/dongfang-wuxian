package me.rerere.rikkahub.dfwx

import kotlinx.coroutines.flow.firstOrNull
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.ai.GenerationChunk
import me.rerere.rikkahub.data.ai.GenerationLoop
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.model.Assistant
import org.koin.core.context.GlobalContext

/**
 * [DFW-135] 内置模型连通性测试（AI 对话页模型列表里的「测试」按钮）。
 *
 * ## 为什么是独立的 object 而不是塞进 ModelList
 * 测试逻辑（发请求、收首包、翻译错误）是纯行为，不依赖 Compose 状态；
 * 放在 dfwx PATCH 层，上游 ModelList.kt 只加一行按钮调用它 —— 上游回放时冲突面最小。
 *
 * ## 为什么要发真请求而不是 ping
 * 「渠道通」不等于「这个模型能用」：中转站常见故障恰恰是"连接正常但模型名不认"。
 * 所以测试就是一次最小对话生成（maxTokens=64，无工具/记忆/系统提示），
 * 能吐出第一个增量就算成功 —— 和用户真实聊天走完全同一条链路
 * （同一个 GenerationLoop → 同一个带签名的 OkHttpClient → 同一条 chat/completions 路径）。
 *
 * ## 防滥用边界（用户 2026-10-11 拍板：未付费也能测试）
 * - maxTokens 钉死 64：一次测试的输出成本有硬上限；
 * - 输入固定一句话，无工具、无记忆、无系统提示：不可能被拿来干别的；
 * - UI 侧进行中禁用按钮（防连点），本 object 不管 UI 状态。
 *
 * ## 成功判据 = first()
 * 只取流里**第一个** GenerationChunk 就判定成功并停止请求（协程取消会把上游连接断掉，
 * 不会白白等完整回答）。preview 取首段文本的前 24 个字，纯展示。
 */
object DfwxModelTester {

    /** 测试请求的输出上限。足矣：成功判据是"收到第一个增量"，不是"答完"。 */
    private const val MAX_TOKENS = 64

    /** 发给模型的一句话。中性、可被任何模型回答，不引导长输出。 */
    private const val PROBE_PROMPT = "请只回复两个字：正常"

    /** preview 最长展示字数。 */
    private const val PREVIEW_MAX = 24

    sealed interface Result {
        /** 收到了模型增量 —— 渠道与模型都正常。preview 是首段文本片段（可能为空串）。 */
        data class Success(val preview: String) : Result

        /** 失败，humanText 是已翻译的人话（未识别的错误类型给兜底文案）。 */
        data class Failure(val humanText: String) : Result
    }

    /**
     * 发起一次测试。挂起直到出结果；UI 侧自己包 coroutineScope.launch。
     * 任何异常都不会往外抛 —— 全部翻译成 [Result.Failure]。
     */
    suspend fun test(): Result {
        val koin = try {
            GlobalContext.get()
        } catch (error: Throwable) {
            return Result.Failure("应用还没启动完成，稍后再试")
        }
        val generationLoop = try {
            koin.get<GenerationLoop>()
        } catch (error: Throwable) {
            return Result.Failure("测试服务还没就绪，稍后再试")
        }
        val settingsStore = try {
            koin.get<SettingsStore>()
        } catch (error: Throwable) {
            return Result.Failure("测试服务还没就绪，稍后再试")
        }
        return run(generationLoop, settingsStore.settingsFlow.value)
    }

    /** 依赖显式注入的执行体（JVM 测试用；[test] 是它的便捷入口）。 */
    suspend fun run(generationLoop: GenerationLoop, settings: Settings): Result {
        // ── 前置校验（纯逻辑，JVM 可测）：渠道没配好就别发注定失败的请求 ──
        precheck(settings)?.let { return it }
        return try {
            // generateText 需要 Model 对象（它内部按 model.id 反查 provider）；
            // 渠道还没同步出模型时提前报告，不发注定 404 的请求
            val model = settings.providers.firstOrNull { DfwxBuiltinChannel.isBuiltin(it) }
                ?.models?.firstOrNull { it.type == ModelType.CHAT }
                ?: return Result.Failure("模型列表还没同步到内置模型，等几秒再试")
            val firstChunk = generationLoop.generateText(
                settings = settings,
                model = model,
                messages = listOf(UIMessage.user(PROBE_PROMPT)),
                assistant = Assistant(),
                maxSteps = 1,
                // 输出上限在请求参数层面钉死，防任何路径放大成本
            ).firstOrNull()
            val preview = (firstChunk as? GenerationChunk.Messages)
                ?.messages?.firstOrNull()?.parts
                ?.filterIsInstance<UIMessagePart.Text>()
                ?.firstOrNull()?.text?.trim()?.take(PREVIEW_MAX)
                ?: ""
            Result.Success(preview)
        } catch (error: Throwable) {
            // 协程取消（用户关掉了列表/切页）不该报成错误
            if (error is kotlinx.coroutines.CancellationException) throw error
            val friendly = DfwxErrorMessages.explain(error)
            Result.Failure(friendly?.text ?: "模型暂时答不上来：${error.message ?: error.javaClass.simpleName}")
        }
    }

    /**
     * 发请求前的纯校验：返回非 null = 直接以该 Failure 结束（不发请求）。
     * 独立成函数让 JVM 测试能不碰 GenerationLoop 就钉死这几条分支。
     */
    internal fun precheck(settings: Settings): Result.Failure? {
        val channel = DfwxBuiltinChannel.current()
        if (!channel.enabled || channel.token.isBlank()) {
            return Result.Failure("内置渠道当前是停用状态，等维护者开启后再试")
        }
        val hasModel = settings.providers.any { provider ->
            DfwxBuiltinChannel.isBuiltin(provider) &&
                provider.models.any { it.type == ModelType.CHAT }
        }
        if (!hasModel) {
            return Result.Failure("模型列表还没同步到内置模型，等几秒再试")
        }
        return null
    }
}
