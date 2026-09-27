package me.rerere.rikkahub.data.dfwx

import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.data.datastore.DEFAULT_AUTO_MODEL_ID
import me.rerere.rikkahub.data.datastore.DEFAULT_ASSISTANT_ID
import me.rerere.rikkahub.data.datastore.Settings
import kotlin.uuid.Uuid

/**
 * [DFWX AI-003] Provider / 模型 / 助手 / 聊天数据保护层。
 *
 * 为 AI-004（移除内置 Provider 播种，decisions.md #3）提供安全网：
 *  - **保守身份证明**：只有"可证明为播种器原样输出"的渠道才允许被移除；
 *    任何字段对不上（用户改名/换地址/加删模型/换 Key）一律保留（decisions.md #10，
 *    "无法证明身份时保留"）。判断不依赖任何 Key 的内容输出——Key 仅做内存等值比较（可选）。
 *  - **悬空引用修复**：移除渠道后，指向被删模型的全局引用回到 [DEFAULT_AUTO_MODEL_ID]
 *    （全新安装等价的安全空态），助手引用回到 null（跟随全局默认模型），收藏夹过滤死引用。
 *    imageGenerationModelId / ocrModelId 的"悬空"是产品既有的未配置语义（新装即随机 UUID），
 *    不做处理。
 *  - **迁移不变量门**：[checkMigrationInvariants] 是任何 settings 迁移的验收门，
 *    AI-004 执行移除后必须调用且违规必须为空。聊天历史在 Room 中，本层是纯函数、
 *    无任何 DB/Context 依赖，结构上不可能清空聊天（契约由 SettingsDataGuardChatHistoryTest 钉死）。
 *
 * 所有函数均为纯函数、幂等；不读文件、不读 SharedPreferences、不输出日志。
 * 身份事实由调用方构造（AI-004 从宿主 resValue 或历史已知常量取得，参考
 * me.rerere.rikkahub.dfwx.BuiltinProviderSeeder 的播种形状）。
 */
object SettingsDataGuard {

    /**
     * 播种渠道的已知身份事实（须与播种时写入的字段一致）。
     *
     * @param name 播种渠道名（如"智能中转(内置)"）
     * @param baseUrl 播种渠道地址
     * @param modelIds 播种的模型业务 ID 列表（含顺序，须完全一致；用户加/删模型即视为已修改）
     * @param apiKey 播种 Key；null = 调用方不做 Key 校验（不读取 Key 的场景）。
     *   提供时做内存等值比较，永不输出。
     */
    data class SeededProviderIdentity(
        val name: String,
        val baseUrl: String,
        val modelIds: List<String>,
        val apiKey: String? = null,
    )

    /** 不变量规则标识（测试与调用方按 rule 断言） */
    enum class Rule(val description: String) {
        /** 非播种的用户 Provider（自建/导入/已修改）必须原样保留 */
        USER_PROVIDERS_PRESERVED("非播种用户 Provider 必须全部原样保留"),

        /** 助手列表不得减少，助手设置除 chatModelId（合法修复目标）外不得被篡改 */
        ASSISTANTS_PRESERVED("助手设置不得被清空或篡改"),

        /** 全局/助手/收藏夹的模型引用不得悬空 */
        MODEL_REFERENCES_VALID("模型引用不得悬空"),

        /** 与 Provider 移除迁移无关的字段不得改变 */
        UNRELATED_FIELDS_PRESERVED("与迁移无关的设置字段不得改变"),
    }

    data class InvariantViolation(val rule: Rule, val detail: String)

    /** 一次安全移除的结果报告（供 AI-004 记录/断言，不包含任何 Key） */
    data class RemovalReport(
        val removedProviderIds: List<Uuid>,
        val keptProviderCount: Int,
        val repairedReferenceFields: List<String>,
    )

    /**
     * 判定 provider 是否"可证明"为未经修改的播种渠道。
     * 只认 OpenAI 兼容类型 + 名称 + baseUrl + 模型业务 ID 列表（+ 可选 Key）完全一致；
     * 其余情况一律返回 false（保守保留）。
     */
    fun isProvablySeeded(provider: ProviderSetting, identity: SeededProviderIdentity): Boolean {
        if (provider !is ProviderSetting.OpenAI) return false
        if (provider.name != identity.name) return false
        if (provider.baseUrl != identity.baseUrl) return false
        if (provider.models.map { it.modelId } != identity.modelIds) return false
        identity.apiKey?.let { if (provider.apiKey != it) return false }
        return true
    }

    /**
     * 安全移除播种渠道：仅移除可证明项，随后修复所有悬空模型引用。
     * 幂等：重复调用第二次起不再移除任何 Provider、不再修复任何引用。
     */
    fun removeSeededProviders(
        settings: Settings,
        identities: List<SeededProviderIdentity>,
    ): Pair<Settings, RemovalReport> {
        val removableIds = settings.providers
            .filter { provider -> identities.any { isProvablySeeded(provider, it) } }
            .map { it.id }
            .toSet()
        val kept = settings.providers.filter { it.id !in removableIds }
        val (sanitized, repaired) = sanitizeDanglingModelReferences(settings.copy(providers = kept))
        return sanitized to RemovalReport(
            removedProviderIds = removableIds.toList(),
            keptProviderCount = kept.size,
            repairedReferenceFields = repaired,
        )
    }

    /**
     * 修复指向不存在模型的所有引用（幂等）：
     *  - chatModelId / fastModelId / translateModeId / compressModelId → [DEFAULT_AUTO_MODEL_ID]
     *    （全新安装的等价值；UI 对该哨兵值即"未选择模型"安全空态）
     *  - assistant.chatModelId → null（跟随全局默认模型；null 本就是该字段"未指定"语义）
     *  - favoriteModels → 过滤掉不存在模型的死引用（与 settingsFlow 的清理语义一致）
     * 返回新 Settings 与被修复的字段名列表（"assistant(<id>).chatModelId" 形式标注助手级修复）。
     */
    fun sanitizeDanglingModelReferences(settings: Settings): Pair<Settings, List<String>> {
        val realModelIds = settings.providers.flatMap { it.models }.map { it.id }.toSet()
        val repaired = mutableListOf<String>()
        var next = settings

        fun Uuid.isDangling() = this != DEFAULT_AUTO_MODEL_ID && this !in realModelIds

        if (next.chatModelId.isDangling()) {
            next = next.copy(chatModelId = DEFAULT_AUTO_MODEL_ID); repaired += "chatModelId"
        }
        if (next.fastModelId.isDangling()) {
            next = next.copy(fastModelId = DEFAULT_AUTO_MODEL_ID); repaired += "fastModelId"
        }
        if (next.translateModeId.isDangling()) {
            next = next.copy(translateModeId = DEFAULT_AUTO_MODEL_ID); repaired += "translateModeId"
        }
        if (next.compressModelId.isDangling()) {
            next = next.copy(compressModelId = DEFAULT_AUTO_MODEL_ID); repaired += "compressModelId"
        }

        val assistants = next.assistants.map { assistant ->
            val modelId = assistant.chatModelId
            if (modelId != null && modelId.isDangling()) {
                repaired += "assistant(${assistant.id}).chatModelId"
                assistant.copy(chatModelId = null)
            } else {
                assistant
            }
        }
        if (assistants != next.assistants) {
            next = next.copy(assistants = assistants)
        }

        val favorites = next.favoriteModels.filter { it in realModelIds }
        if (favorites != next.favoriteModels) {
            next = next.copy(favoriteModels = favorites); repaired += "favoriteModels"
        }

        return next to repaired
    }

    /**
     * 迁移不变量验收门：AI-004（及未来任何 settings 迁移）执行后必须调用，
     * 返回的违规列表必须为空才算迁移合规。
     *
     * 校验内容：
     *  1. [Rule.USER_PROVIDERS_PRESERVED]：before 中每个非播种 Provider 在 after 中原样存在；
     *  2. [Rule.ASSISTANTS_PRESERVED]：助手不减少、不丢失、不被篡改（chatModelId 除外——
     *     悬空修复的合法目标；且必须是"悬空→null"方向）；
     *  3. [Rule.MODEL_REFERENCES_VALID]：全局引用 ∈ 存活模型 ∪ {AUTO 哨兵}；助手引用 null 或存活；
     *     收藏夹 ⊆ 存活模型；assistantId 可解析（settingsFlow 会补种默认助手，故默认 ID 恒有效）；
     *  4. [Rule.UNRELATED_FIELDS_PRESERVED]：除上述迁移目标字段外，其余设置逐字段一致。
     */
    fun checkMigrationInvariants(
        before: Settings,
        after: Settings,
        identities: List<SeededProviderIdentity>,
    ): List<InvariantViolation> {
        val violations = mutableListOf<InvariantViolation>()

        // 1. 用户 Provider 保留
        for (provider in before.providers) {
            val provablySeeded = identities.any { isProvablySeeded(provider, it) }
            if (provablySeeded) continue
            val survivors = after.providers.filter { it.id == provider.id }
            if (survivors.size != 1 || survivors[0] != provider) {
                violations += InvariantViolation(
                    Rule.USER_PROVIDERS_PRESERVED,
                    "用户 Provider ${provider.id}（${provider.name}）丢失或被篡改",
                )
            }
        }

        // 2. 助手保留（数量、存在性、内容）
        if (after.assistants.size < before.assistants.size) {
            violations += InvariantViolation(
                Rule.ASSISTANTS_PRESERVED,
                "助手列表被清空/减少: before=${before.assistants.size} after=${after.assistants.size}",
            )
        }
        for (assistant in before.assistants) {
            val afterAssistant = after.assistants.find { it.id == assistant.id }
            if (afterAssistant == null) {
                violations += InvariantViolation(
                    Rule.ASSISTANTS_PRESERVED,
                    "助手 ${assistant.id}（${assistant.name}）丢失",
                )
            } else if (afterAssistant.copy(chatModelId = assistant.chatModelId) != assistant) {
                violations += InvariantViolation(
                    Rule.ASSISTANTS_PRESERVED,
                    "助手 ${assistant.id}（${assistant.name}）设置被篡改",
                )
            }
        }

        // 3. 模型引用有效
        val validModelIds = after.providers.flatMap { it.models }.map { it.id }.toSet() + DEFAULT_AUTO_MODEL_ID
        if (after.chatModelId !in validModelIds) {
            violations += InvariantViolation(Rule.MODEL_REFERENCES_VALID, "chatModelId 悬空: ${after.chatModelId}")
        }
        if (after.fastModelId !in validModelIds) {
            violations += InvariantViolation(Rule.MODEL_REFERENCES_VALID, "fastModelId 悬空: ${after.fastModelId}")
        }
        if (after.translateModeId !in validModelIds) {
            violations += InvariantViolation(Rule.MODEL_REFERENCES_VALID, "translateModeId 悬空: ${after.translateModeId}")
        }
        if (after.compressModelId !in validModelIds) {
            violations += InvariantViolation(Rule.MODEL_REFERENCES_VALID, "compressModelId 悬空: ${after.compressModelId}")
        }
        for (assistant in after.assistants) {
            val modelId = assistant.chatModelId
            if (modelId != null && modelId !in validModelIds) {
                violations += InvariantViolation(
                    Rule.MODEL_REFERENCES_VALID,
                    "助手 ${assistant.id} 的 chatModelId 悬空: $modelId",
                )
            }
        }
        for (favorite in after.favoriteModels) {
            if (favorite !in validModelIds) {
                violations += InvariantViolation(
                    Rule.MODEL_REFERENCES_VALID,
                    "收藏模型悬空: $favorite",
                )
            }
        }
        val validAssistantIds = after.assistants.map { it.id }.toSet() + DEFAULT_ASSISTANT_ID
        if (after.assistantId !in validAssistantIds) {
            violations += InvariantViolation(
                Rule.MODEL_REFERENCES_VALID,
                "选中助手引用无效: ${after.assistantId}",
            )
        }

        // 4. 无关字段不变（把迁移合法目标字段清零后整体比对）
        if (stripMigrationFields(before) != stripMigrationFields(after)) {
            violations += InvariantViolation(
                Rule.UNRELATED_FIELDS_PRESERVED,
                "除 Provider/模型引用/助手模型字段外的设置被迁移改变",
            )
        }

        return violations
    }

    /**
     * 把"Provider 移除迁移"允许改变的字段清零，用于无关字段一致性比对。
     * 允许改变的只有：providers、四个全局模型引用、favoriteModels、助手（chatModelId）。
     */
    private fun stripMigrationFields(settings: Settings): Settings = settings.copy(
        providers = emptyList(),
        chatModelId = DEFAULT_AUTO_MODEL_ID,
        fastModelId = DEFAULT_AUTO_MODEL_ID,
        translateModeId = DEFAULT_AUTO_MODEL_ID,
        compressModelId = DEFAULT_AUTO_MODEL_ID,
        favoriteModels = emptyList(),
        assistants = emptyList(),
    )
}
