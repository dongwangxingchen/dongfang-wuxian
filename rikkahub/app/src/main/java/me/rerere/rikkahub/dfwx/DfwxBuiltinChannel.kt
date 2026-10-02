package me.rerere.rikkahub.dfwx

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.Modality
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.data.datastore.DEFAULT_ASSISTANT_ID
import me.rerere.rikkahub.data.datastore.Settings
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

    /** [DFW-114] 一次性迁移标记的落点（与 vendor 的 Settings/DataStore 分开，互不干扰）。 */
    private const val PREFS = "dfwx_builtin_channel"
    private const val KEY_CAPABILITIES_DONE = "capabilities_v1_done"

    const val PROVIDER_NAME = "内置渠道"
    const val DEFAULT_BASE_URL = "https://39.106.33.135/ai/v1"
    const val DEFAULT_MODEL_ID = "deepseek-v4.1-flash"
    const val DEFAULT_MAX_TOKENS = 8192
    /** RikkaHub 的 OpenAI 渠道默认路径（`ProviderSetting.OpenAI.chatCompletionsPath` 的默认值）。 */
    const val DEFAULT_CHAT_PATH = "/chat/completions"

    /** 渠道配置。令牌为空 = 这条渠道不可用（宿主没注入 / 用户关了远程开关）。 */
    data class Config(
        val baseUrl: String = DEFAULT_BASE_URL,
        val token: String = "",
        val modelId: String = DEFAULT_MODEL_ID,
        /**
         * [DFW-77] 用户在 App 里看到的模型名。空 = 跟 [modelId] 一样。
         *
         * 为什么要有它：后台换上游模型时，`modelId` 是**发给上游的真实名字**（可能很丑，
         * 比如 `glm-5.3-flashx`），而用户界面上应该显示一个我们自己起的名字。两者解耦。
         */
        val displayName: String = "",
        /**
         * [DFW-77] chat completions 的请求路径。空 = 用 RikkaHub 的默认 `/chat/completions`。
         * 留着它是为了"上游哪天换了路径"时不用发版。
         */
        val chatPath: String = "",
        val maxTokens: Int = DEFAULT_MAX_TOKENS,
        val enabled: Boolean = true,
    ) {
        /** 实际显示名：没配就回落到模型名。 */
        val effectiveDisplayName: String get() = displayName.ifBlank { modelId }

        /** 实际请求路径：没配就回落到上游默认。 */
        val effectiveChatPath: String get() = chatPath.ifBlank { DEFAULT_CHAT_PATH }
    }

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
    /** [DFW-114] 存的是 `applicationContext`，不是 Activity —— 静态持有 Activity 会泄漏。 */
    private var lastContext: Context? = null

    /** 后台下发了新的渠道配置之后调用：让改动**当次启动**就生效，不用等下次冷启。 */
    fun syncNow() {
        // [DFW-114] syncIfNeeded 现在还要一个 Context（能力开关的一次性标记）。
        // 这里用上次传进来的那个 —— 调用方给的是 Application，存着不会泄漏。
        val context = lastContext ?: return
        val scope = lastScope ?: return
        val store = lastStore ?: return
        syncIfNeeded(context, scope, store)
    }

    /**
     * 纯函数：算出"同步之后"的 settings。不改任何外部状态，方便单测把幂等性钉死。
     *
     * ## 为什么模型 id 必须沿用旧的（这条踩过真坑，必现）
     * `Model.id` 的默认值是 `Uuid.random()` —— **每次构造都是新 id**，不是由 modelId 派生的稳定值；
     * 而 RikkaHub 的 `findModelById()` 是按 `model.id` **精确匹配**的。
     * 旧实现每轮同步都 `Model(...)` 新建一个对象，于是：
     *  - 用户上次选中的 `chatModelId` 指向上一轮的随机 id → 重启后查不到 → AI 页弹「请先选择模型」，
     *    **每次重启都要重选一次**（内置渠道是 DFW-73 的主功能，这是必现的）；
     *  - `next == settings` 永远为假 → 每次启动都白写一遍 DataStore。
     * 所以这里必须把已存在模型的 id 抄回来。
     */
    fun buildSyncedSettings(
        settings: Settings,
        cfg: Config = current(),
        enableCapabilities: Boolean = false,
    ): Settings {
        val index = settings.providers.indexOfFirst { isBuiltin(it) }
        val existing = if (index >= 0) settings.providers[index] else null
        val model = Model(
            modelId = cfg.modelId,
            // [DFW-77] 显示名与"发给上游的模型名"解耦：后台可以换一个丑模型但界面显示漂亮名字。
            displayName = cfg.effectiveDisplayName,
            id = existing?.models?.firstOrNull()?.id ?: Uuid.random(),
            abilities = listOf(ModelAbility.TOOL, ModelAbility.REASONING),
            /*
             * [DFW-81] **必须显式声明图片输入**，否则用户发图 AI 只能看到 "[Image]"。
             *
             * 踩过的坑，链条完整记在这里：
             *  1. `Model.inputModalities` 默认值是 `listOf(Modality.TEXT)` —— **不含 IMAGE**；
             *  2. `OcrTransformer.transform()` 第一行是
             *     `if (ctx.model.inputModalities.contains(Modality.IMAGE)) return messages`
             *     —— 模型声明了能看图就直接放行，没声明就走 OCR 降级；
             *  3. `OcrTransformer.performOcr()` 在没配 OCR 模型时 `return "[Image]"`，
             *     于是图片被替换成**字面量文本** "[Image]" 发给上游 —— 这就是用户说的"括号图片"。
             *
             * 上游是**真的支持**看图的（2026-10-01 用 1x1 红色 PNG 实测：
             * `deepseek-v4.1-flash` 答"粉色"、`glm-5.3-flash` 答"深红"）。
             * 所以这里补上 IMAGE 就修好了，不需要 OCR 兜底。
             *
             * 注意：只影响内置渠道这一个模型；用户自己接的渠道有各自的 `inputModalities`，不受影响。
             */
            inputModalities = listOf(Modality.TEXT, Modality.IMAGE),
            outputModalities = listOf(Modality.TEXT),
        )
        val provider = ProviderSetting.OpenAI(
            id = existing?.id ?: Uuid.random(),
            name = PROVIDER_NAME,
            apiKey = cfg.token,
            baseUrl = cfg.baseUrl,
            // [DFW-77] 请求路径可远程改；留空则沿用 RikkaHub 默认值。
            chatCompletionsPath = cfg.effectiveChatPath,
            models = listOf(model),
        )
        val providers = if (index >= 0) {
            settings.providers.toMutableList().also { it[index] = provider }
        } else {
            settings.providers + provider
        }
        // 全新安装：直接把内置渠道设成默认模型，装完就能聊天（用户要的"内置"就是这个意思）。
        // 已有自己渠道的用户：**不动他的模型选择**。
        val freshInstall = settings.providers.isEmpty()
        val nextChatModelId = if (freshInstall) model.id else settings.chatModelId
        // 最大输出挂在助手上（RikkaHub 的 maxTokens 是助手字段，模型没有这个字段）。
        // 注意 `Assistant.chatModelId` 默认是 **null**（= 跟随全局 chatModelId），
        // 所以判据必须同时覆盖"显式指向内置模型"和"跟随全局、而全局正是内置模型"两种情况 ——
        // 只判前者的话，后台配的 8192 会**静默失效**。
        val assistants = settings.assistants.map { assistant ->
            val usesBuiltin = assistant.chatModelId == model.id ||
                (assistant.chatModelId == null && nextChatModelId == model.id)
            if (usesBuiltin) assistant.copy(maxTokens = cfg.maxTokens) else assistant
        }
        val synced = settings.copy(
            providers = providers,
            chatModelId = nextChatModelId,
            fastModelId = if (freshInstall) model.id else settings.fastModelId,
            assistants = assistants,
        )
        /*
         * [DFW-84] 再把「东方助手」的默认人设补上：名字、头像、软件使用知识。
         * 判据与上面 maxTokens 完全一致（同一个 usesBuiltin），所以三件事永远作用于同一批助手，
         * 不会出现"最大输出给了内置助手、头像却给了别的助手"这种错位。
         * 只在字段为空时写入，用户改过的一律不覆盖 —— 细节见 DfwxAssistantProfile。
         */
        /*
         * [DFW-108 2026-10-02] **判据补上「默认助手」这一支。**
         *
         * ## 用户报的现象
         * > 「东方助手，它不是配置过提示词和其他东西吗……为什么现在全都清空了呢？
         * >   我的内置 AI 不应是这个头像吧，我以前不是给过你一个图片」
         * 界面上：名字是「东方助手」，提示词空、头像变成模型图标。
         *
         * ## 根因（不是被谁删了，是这道门没放行）
         * 原来只给两类助手写人设：显式绑定内置模型的、或全局模型恰好是内置模型的。
         * 用户把全局模型换成了别的之后，默认助手 `chatModelId` 为 null 且不等于内置模型
         * → **静默跳过**（这里没有任何日志）→ 默认助手一直是 vendor 的空壳。
         *
         * 而界面上「东方助手」四个字来自 vendor 的**字符串兜底**
         * （`values/strings.xml` 的 assistant_default_name），**跟人设写没写进去无关** ——
         * 所以呈现出来正好是"名字在、内容全空"，看起来就像"被清空了"。
         *
         * ## 为什么给默认助手开绿灯是安全的
         * 默认助手就是「没被用户显式配置过的那一个」，本来该由我们播种；
         * 而 `applyDefaults` 是**只填空**（字段非空一律不覆盖），
         * 用户自己改过名字/提示词/头像的，一个像素都不会动。
         * 用户自己新建的助手、以及绑了别的渠道的助手，仍然不受影响 ——
         * 这守住了 `DfwxBuiltinChannelTest` 里"用户自己的模型选择不许被顶掉"那条断言。
         *
         * ## 关于"跳过时静默"这件事
         * 这次排查最大的障碍就是门没放行时**一个字都没有**，只能通读源码反推。
         * 本想在这里加一行日志，但 **vendor 区的单测跑在纯 JVM 上，`android.util.Log` 没有 mock**
         * （一加就 `RuntimeException: Method i in android.util.Log not mocked`，7 条用例全红）。
         * 为了不打坏可测性，这里**不加日志**；改为用
         * `existingUser_whoseGlobalModelIsNotTheBuiltinOne_stillGetsThePersona`
         * 这条回归用例把"门必须放行"钉死 —— 测试比日志更早发现问题。
         * 这次排查最大的障碍就是"跳过时完全静默"—— 用户说配置没了，
         * 代码里却一个字都没有，只能靠通读源码反推。
         */
        return DfwxAssistantProfile.applyDefaults(
            settings = synced,
            // 注意：这里必须写成具名参数 + 显式 lambda，**不能用尾随 lambda** ——
            // Kotlin 的尾随 lambda 绑定的是**最后一个**参数，而最后一个现在是
            // `enableCapabilities: Boolean`，会报 "Too many arguments" / 类型不匹配。
            useBuiltin = { assistant ->
                assistant.chatModelId == model.id ||
                    (
                        // 「跟随全局模型」= 用户没显式配置过这一个
                        assistant.chatModelId == null &&
                            // 全局模型就是内置的（原判据）
                            (nextChatModelId == model.id ||
                                // [DFW-108] 或者它就是**默认助手** —— 这一支是本次修的根因。
                                //
                                // 为什么必须是「跟随全局」而不是无条件：用户明确要求过
                                // 「别人对接新 API 站的时候用他们默认的」。
                                // 如果默认助手被**显式**指到了别的渠道（chatModelId 非空），
                                // 那就是用户自己的选择，一个像素都不该动。
                                // 只有"没配置过、跟随全局"的那种，才该由我们播种人设 ——
                                // 哪怕此刻全局模型不是内置的（正是用户遇到的情形）。
                                assistant.id == DEFAULT_ASSISTANT_ID)
                        )
            },
            enableCapabilities = enableCapabilities,
        )
    }

    /**
     * 播种 / 重新同步内置渠道。**每次启动都跑**（不是只跑一次）：
     * 后台改了地址、令牌、模型名或最大输出之后，用户下次打开软件就生效，不必发版。
     * 只有在真的有字段变化时才写 settings —— 靠 [buildSyncedSettings] 的幂等性保证。
     */
    fun syncIfNeeded(context: Context, scope: CoroutineScope, store: SettingsStore) {
        lastScope = scope
        lastStore = store
        lastContext = context.applicationContext
        val cfg = current()
        if (!cfg.enabled || cfg.token.isBlank()) {
            Log.i(TAG, "builtin channel disabled or token missing, skip")
            return
        }
        scope.launch(Dispatchers.IO) {
            runCatching {
                val settings = store.settingsFlowRaw.first()
                /*
                 * [DFW-114 2026-10-02] 能力开关（记忆 / 翻聊天记录）只在**升级后第一次**打开，
                 * 之后永远不动。理由见 DfwxAssistantProfile.applyDefaults 里那段长注释：
                 * 这两个字段是 Boolean、默认 false，**分不出「用户主动关掉」和「从没设置过」**，
                 * 所以只能靠一个一次性标记，而不是每次启动都硬开。
                 *
                 * 标记写在 store.update **成功之后** —— 失败就不写，下次启动自然重试。
                 * 与 DfwxBuiltinProviderCleanup 是同一套语义。
                 *
                 * 注意这里不能写成 `if (next == settings) return@launch`：
                 * 那样"能力早就开着、本次无需改动"时标记永远写不上，等于每次都重跑。
                 */
                val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                val firstRun = !prefs.getBoolean(KEY_CAPABILITIES_DONE, false)
                val next = buildSyncedSettings(settings, cfg, enableCapabilities = firstRun)
                if (next != settings) store.update(next)
                if (firstRun) prefs.edit().putBoolean(KEY_CAPABILITIES_DONE, true).apply()
                if (next != settings) Log.i(TAG, "builtin channel synced")
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
