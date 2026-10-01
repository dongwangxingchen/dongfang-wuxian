package me.rerere.rikkahub.dfwx

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findProvider
import kotlin.uuid.Uuid

/**
 * [DFW-73] 东方无限「内置渠道」（用户 2026-10-01 拍板，方案 = **服务端中转**）。
 *
 * ## 用户原话与由此定下的边界
 * > "你不用给他记次数，我每周只会给它设定10块钱，所以你只需要做好防止被别人抓包的就行"
 * > "防君子就行了，靠诚信就行，他们自愿付费" / "我不希望我这个URL会飞走"
 *
 * 所以这条渠道**没有配额、没有计数**，唯一目标是：**上游中转站地址与真实 Key 一个字节都不进 APK**。
 * 客户端只知道自己服务器的地址（`https://39.106.33.135/ai/v1`）和一个**应用令牌**；
 * 上游地址与真 Key 在服务器 `/etc/nginx/dfwx-ai-secret.conf`（600）。
 * 令牌在客户端天然可被扒（用户已知情接受）；万一被白用，服务器换令牌 + 后台下发即可，不必发版。
 *
 * ## 本文件的职责
 *  1. 播种：首次启动把「内置渠道 / deepseek-v4.1-flash」写进 RikkaHub 设置（幂等、可远程改）；
 *  2. 身份识别：哪些 Provider 属于内置渠道（只认 baseUrl，用户改名也算）；
 *  3. 付费门：未诚信付费时选中内置渠道要弹窗引导（见 ModelList.kt 的调用点）；
 *  4. 只读：内置渠道不出现在 AI 设置的渠道列表里，用户改不到（改由后台下发覆盖）。
 *
 * ## 为什么宿主注入而不是本模块硬编码
 * 地址与令牌来自宿主 `:app` 的 `resValue("string","dfwx_ai_*")`（见 app/build.gradle.kts），
 * 由 `cc.nkbr.lanzouplus.BuiltinAiChannel` 在启动时调 [install] 推过来。
 * 本模块（vendor 库）**不引用任何宿主类**，保持与上游同步时不用改这里。
 *
 * 同步上游时需重放（新增自有文件，不在上游补丁范围内，见 rikkahub/PATCHES.md P39）。
 */
object DfwxBuiltinChannel {
    private const val TAG = "DfwxBuiltinAi"

    const val PROVIDER_NAME = "内置渠道"
    const val DEFAULT_BASE_URL = "https://39.106.33.135/ai/v1"
    const val DEFAULT_MODEL_ID = "deepseek-v4.1-flash"
    const val DEFAULT_MAX_TOKENS = 8192

    /** 渠道配置。令牌为空 = 这条渠道不可用（宿主没注入 / 用户关了远程开关）。 */
    data class Config(
        val baseUrl: String = DEFAULT_BASE_URL,
        val token: String = "",
        val modelId: String = DEFAULT_MODEL_ID,
        val maxTokens: Int = DEFAULT_MAX_TOKENS,
        val enabled: Boolean = true,
    )

    @Volatile
    private var config: Config? = null

    /** 宿主（:app）启动时注入。 */
    fun install(value: Config) {
        config = value
    }

    fun current(): Config = config ?: Config()

    /**
     * 宿主注入的"是否已诚信付费"（宿主侧 `Support.unlocked`）。
     * 未注入时**视为未付费**：宁可多弹一次引导，也不让未付费的人白用（这是用户的本意）。
     */
    @Volatile
    var paidProvider: (() -> Boolean)? = null

    fun isPaid(): Boolean = paidProvider?.invoke() ?: false

    private fun normalized(url: String): String = url.trim().trimEnd('/').lowercase()

    /**
     * 只认 baseUrl、并**额外认名字**。
     *
     * 为什么要带名字：后台可以远程改 `ai_base_url`，改完以后老渠道的 baseUrl 就对不上新地址了，
     * 只按 baseUrl 找会在设置里再播一条重复渠道。名字匹配只在"名字恰好叫「内置渠道」且是 OpenAI 兼容类型"
     * 时命中，用户改不到这个渠道（它不出现在 AI 设置的渠道列表里），所以不会误伤用户自己的渠道。
     */
    fun isBuiltin(provider: ProviderSetting): Boolean =
        provider is ProviderSetting.OpenAI &&
            (provider.name == PROVIDER_NAME || normalized(provider.baseUrl) == normalized(current().baseUrl))

    /**
     * 未付费的用户在模型选择器里点到了内置渠道的模型 → 返回 true 表示**要拦下来弹窗**。
     * 已付费 / 不是内置渠道 / 找不到归属渠道 → false（放行）。
     */
    fun shouldBlockSelection(model: Model, providers: List<ProviderSetting>): Boolean {
        if (isPaid()) return false
        if (!current().enabled || current().token.isBlank()) return false
        val provider = model.findProvider(providers = providers, checkOverwrite = false) ?: return false
        return isBuiltin(provider)
    }

    /** 最近一次同步用的 scope/store：后台下发覆盖后要能立刻重同步一次（见 [syncNow]）。 */
    @Volatile
    private var lastScope: CoroutineScope? = null

    @Volatile
    private var lastStore: SettingsStore? = null

    /** 后台下发了新的渠道配置之后调用：让改动**当次启动**就生效，不用等下次冷启。 */
    fun syncNow() {
        val scope = lastScope ?: return
        val store = lastStore ?: return
        syncIfNeeded(scope, store)
    }

    /**
     * 播种 / 重新同步内置渠道。**每次启动都跑**（不是只跑一次）：
     * 后台改了地址、令牌、模型名或最大输出之后，用户下次打开软件就生效，不必发版。
     * 只有在真的有字段变化时才写 settings，避免每次启动都写一遍 DataStore。
     */
    fun syncIfNeeded(scope: CoroutineScope, store: SettingsStore) {
        lastScope = scope
        lastStore = store
        val cfg = current()
        if (!cfg.enabled || cfg.token.isBlank()) {
            Log.i(TAG, "builtin channel disabled or token missing, skip")
            return
        }
        scope.launch(Dispatchers.IO) {
            runCatching {
                val settings = store.settingsFlowRaw.first()
                val freshInstall = settings.providers.isEmpty()
                val model = Model(
                    modelId = cfg.modelId,
                    displayName = cfg.modelId,
                    abilities = listOf(ModelAbility.TOOL, ModelAbility.REASONING),
                )
                val index = settings.providers.indexOfFirst { isBuiltin(it) }
                val provider = ProviderSetting.OpenAI(
                    id = if (index >= 0) settings.providers[index].id else Uuid.random(),
                    name = PROVIDER_NAME,
                    apiKey = cfg.token,
                    baseUrl = cfg.baseUrl,
                    models = listOf(model),
                )
                val providers = if (index >= 0) {
                    settings.providers.toMutableList().also { it[index] = provider }
                } else {
                    settings.providers + provider
                }
                // 最大输出挂在助手上（RikkaHub 的 maxTokens 是助手字段，模型没有这个字段）。
                // 只动"正在用内置渠道"的助手，绝不碰用户自己配置的助手。
                val assistants = settings.assistants.map { assistant ->
                    if (assistant.chatModelId == model.id) assistant.copy(maxTokens = cfg.maxTokens) else assistant
                }
                val next = settings.copy(
                    providers = providers,
                    // 全新安装：直接把内置渠道设成默认模型，装完就能聊天（用户要的"内置"就是这个意思）。
                    // 已有自己渠道的用户：**不动他的模型选择**。
                    chatModelId = if (freshInstall) model.id else settings.chatModelId,
                    fastModelId = if (freshInstall) model.id else settings.fastModelId,
                    assistants = assistants,
                )
                if (next == settings) return@launch
                store.update(next)
                Log.i(TAG, "builtin channel synced (fresh=$freshInstall, existed=${index >= 0})")
            }.onFailure {
                Log.e(TAG, "builtin channel sync failed", it)
            }
        }
    }

    /** 兼容保留：老版本按 resValue 名字查找资源的写法已废弃，改由宿主直接注入。 */
    @Suppress("unused")
    private fun legacyResLookup(context: Context, name: String): String = runCatching {
        val id = context.resources.getIdentifier(name, "string", context.packageName)
        if (id != 0) context.getString(id) else ""
    }.getOrDefault("")
}
