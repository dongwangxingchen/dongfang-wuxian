package me.rerere.rikkahub.data.dfwx

import me.rerere.ai.provider.CustomBody
import me.rerere.ai.provider.CustomHeader
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.data.datastore.DEFAULT_AUTO_MODEL_ID
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Lorebook
import me.rerere.rikkahub.utils.JsonInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

/**
 * [DFWX AI-003] 数据保护层契约测试（纯 JVM，无 Robolectric）。
 *
 * 红线对照（docs/archive/tasks/legacy-cards/DFWX-AI-003.md）：
 *  - 不清空 settings.providers、不删用户 Provider —— 用例 4/5/9/10
 *  - 用户 Provider 序列化完全保留 —— 用例 1/2（往返用 JsonInstant，即 SettingsStore.persistSettings 的同一编码器）
 *  - 悬空模型引用安全修复 —— 用例 6/7
 *  - 迁移幂等 —— 用例 8
 *  - 助手设置不被清空 —— 用例 2/7/9/10
 *  - 所有 Key 一律假数据，断言不涉及 Key 内容
 */
class SettingsDataGuardTest {

    // ---- 假数据夹具（禁止真实 Key） ----
    private val seedName = "智能中转(内置)"
    private val seedUrl = "https://relay.example.test/v1"
    private val seedKey = "fake-seeded-key-not-real"
    private val seedModelId = "glm-5.3"
    private val seedModelUuid = Uuid.parse("00000000-0000-4000-8000-000000000001")
    private val seedProviderUuid = Uuid.parse("00000000-0000-4000-8000-0000000000a1")

    private val identity = SettingsDataGuard.SeededProviderIdentity(
        name = seedName,
        baseUrl = seedUrl,
        modelIds = listOf(seedModelId),
        apiKey = seedKey,
    )

    /** 与 BuiltinProviderSeeder 播种形状一致的内置渠道（身份可证明） */
    private fun seededProvider(
        name: String = seedName,
        baseUrl: String = seedUrl,
        apiKey: String = seedKey,
        models: List<Model> = listOf(
            Model(
                modelId = seedModelId,
                displayName = seedModelId,
                id = seedModelUuid,
                abilities = listOf(ModelAbility.TOOL, ModelAbility.REASONING),
            )
        ),
    ) = ProviderSetting.OpenAI(
        id = seedProviderUuid,
        name = name,
        baseUrl = baseUrl,
        apiKey = apiKey,
        models = models,
    )

    /** 用户自建 OpenAI 渠道（字段尽量全，验证序列化保真） */
    private fun userOpenAi(): ProviderSetting.OpenAI {
        val m1 = Model(
            modelId = "my-model-a",
            displayName = "模型A",
            id = Uuid.parse("10000000-0000-4000-8000-000000000001"),
            abilities = listOf(ModelAbility.TOOL),
            customHeaders = listOf(CustomHeader(name = "X-Test", value = "1")),
            customBodies = listOf(CustomBody(key = "extra", value = kotlinx.serialization.json.JsonPrimitive("x"))),
        )
        val m2 = Model(
            modelId = "my-model-b",
            displayName = "模型B",
            id = Uuid.parse("10000000-0000-4000-8000-000000000002"),
            tools = setOf(me.rerere.ai.provider.BuiltInTools.Search),
            providerOverwrite = ProviderSetting.OpenAI(
                name = "overwrite",
                baseUrl = "https://overwrite.example.test/v1",
                apiKey = "fake-user-key-not-real",
            ),
        )
        return ProviderSetting.OpenAI(
            id = Uuid.parse("20000000-0000-4000-8000-000000000001"),
            name = "我的中转",
            apiKey = "fake-user-key-not-real",
            baseUrl = "https://my.example.test/v1",
            models = listOf(m1, m2),
            enabled = false,
            useResponseApi = true,
            includeHistoryReasoning = false,
        )
    }

    private fun userGoogle() = ProviderSetting.Google(
        id = Uuid.parse("20000000-0000-4000-8000-000000000002"),
        name = "我的Gemini",
        apiKey = "fake-google-key-not-real",
        vertexAI = true,
        models = listOf(
            Model(
                modelId = "gemini-x",
                id = Uuid.parse("10000000-0000-4000-8000-000000000003"),
            )
        ),
    )

    private fun userClaude() = ProviderSetting.Claude(
        id = Uuid.parse("20000000-0000-4000-8000-000000000003"),
        name = "我的Claude",
        apiKey = "fake-claude-key-not-real",
        promptCaching = true,
        models = listOf(
            Model(
                modelId = "claude-x",
                id = Uuid.parse("10000000-0000-4000-8000-000000000004"),
            )
        ),
    )

    private val userAssistant = Assistant(
        id = Uuid.parse("30000000-0000-4000-8000-000000000001"),
        name = "写作助手",
        systemPrompt = "你是一个写作助手",
        temperature = 0.7f,
        maxTokens = 4096,
        enableWebSearch = true,
        regexes = listOf(
            me.rerere.rikkahub.data.model.AssistantRegex(
                id = Uuid.parse("30000000-0000-4000-8000-000000000002"),
                name = "替换",
                findRegex = "a+",
                replaceString = "b",
            )
        ),
    )

    private fun settings(
        providers: List<ProviderSetting>,
        assistants: List<Assistant> = listOf(userAssistant),
        chatModelId: Uuid = DEFAULT_AUTO_MODEL_ID,
        fastModelId: Uuid = DEFAULT_AUTO_MODEL_ID,
        assistantModelId: Uuid? = null,
        favoriteModels: List<Uuid> = emptyList(),
    ) = Settings(
        providers = providers,
        assistants = assistants,
        assistantId = assistants.first().id,
        chatModelId = chatModelId,
        fastModelId = fastModelId,
        translateModeId = DEFAULT_AUTO_MODEL_ID,
        compressModelId = DEFAULT_AUTO_MODEL_ID,
        favoriteModels = favoriteModels,
        lorebooks = listOf(
            Lorebook(id = Uuid.parse("40000000-0000-4000-8000-000000000001"), name = "世界书", enabled = true)
        ),
    )

    // ---- 1. 用户 Provider 往返序列化完全保留 ----

    @Test
    fun `user providers survive json round trip exactly`() {
        val providers = listOf(userOpenAi(), userGoogle(), userClaude(), seededProvider())
        val json = JsonInstant.encodeToString(providers)
        val decoded: List<ProviderSetting> = JsonInstant.decodeFromString(json)

        assertEquals(providers, decoded)
    }

    // ---- 2. 整个 Settings（含助手设置）往返序列化保留 ----

    @Test
    fun `full settings with assistants survive json round trip`() {
        val original = settings(
            providers = listOf(userOpenAi(), seededProvider()),
            chatModelId = DEFAULT_AUTO_MODEL_ID,
            assistantModelId = null,
        )
        val json = JsonInstant.encodeToString(original)
        val decoded: Settings = JsonInstant.decodeFromString(json)

        // 整对象 equals 不可靠：Settings.searchServices 元素（BingLocalOptions 等）未实现 equals，
        // 引用比较必炸。序列化字符串比对更符合"序列化完全保留"的验证本意，且能捕获 equals 漏掉的字段丢失。
        assertEquals(json, JsonInstant.encodeToString(decoded))
        assertEquals(original.assistants, decoded.assistants)
        assertEquals(original.lorebooks, decoded.lorebooks)
    }

    // ---- 3. 已修改的播种渠道无法证明身份 → 不可移除（保留） ----

    @Test
    fun `modified seeded providers are not provably seeded`() {
        val cases = listOf(
            "改名" to seededProvider(name = "我的中转"),
            "换 baseUrl" to seededProvider(baseUrl = "https://other.example.test/v1"),
            "加模型" to seededProvider(
                models = listOf(
                    Model(modelId = seedModelId, displayName = seedModelId),
                    Model(modelId = "extra-model"),
                )
            ),
            "换 Key" to seededProvider(apiKey = "fake-user-rotated-key"),
            "删模型" to seededProvider(models = emptyList()),
            "不同类型" to ProviderSetting.Google(
                id = seedProviderUuid,
                name = seedName,
                apiKey = "fake-google-key",
                baseUrl = seedUrl,
            ),
        )
        for ((label, provider) in cases) {
            assertTrue(
                "已修改的播种渠道（$label）不应被判定为可证明播种身份",
                !SettingsDataGuard.isProvablySeeded(provider, identity)
            )
        }
    }

    @Test
    fun `unmodified seeded provider is provably seeded`() {
        assertTrue(SettingsDataGuard.isProvablySeeded(seededProvider(), identity))
        // 不提供 Key 事实时（不读 Key 的调用方），其余身份字段一致即可证明
        assertTrue(
            SettingsDataGuard.isProvablySeeded(seededProvider(), identity.copy(apiKey = null))
        )
    }

    // ---- 4. 安全移除：只移除可证明项，其余原样保留 ----

    @Test
    fun `removeSeededProviders removes only provable seed and keeps user providers`() {
        val user = listOf(userOpenAi(), userGoogle(), userClaude())
        val before = settings(providers = user + seededProvider())

        val (after, report) = SettingsDataGuard.removeSeededProviders(before, listOf(identity))

        assertEquals(user, after.providers)
        assertEquals(listOf(seedProviderUuid), report.removedProviderIds)
        assertEquals(3, report.keptProviderCount)
    }

    // ---- 5. 移除播种渠道时引用其模型的助手/全局字段得到修复，其余助手设置原样保留 ----

    @Test
    fun `dangling model references are repaired and assistant settings preserved`() {
        val user = listOf(userOpenAi())
        val before = settings(
            providers = user + seededProvider(),
            chatModelId = seedModelUuid,
            fastModelId = seedModelUuid,
            assistantModelId = seedModelUuid,
            favoriteModels = listOf(
                seedModelUuid,
                Uuid.parse("10000000-0000-4000-8000-000000000001"),
            ),
        )

        val (after, report) = SettingsDataGuard.removeSeededProviders(before, listOf(identity))

        // 全局引用 → 安全空态（全新安装等价值）
        assertEquals(DEFAULT_AUTO_MODEL_ID, after.chatModelId)
        assertEquals(DEFAULT_AUTO_MODEL_ID, after.fastModelId)
        assertEquals(DEFAULT_AUTO_MODEL_ID, after.translateModeId)
        assertEquals(DEFAULT_AUTO_MODEL_ID, after.compressModelId)
        // 助手引用 → null（跟随全局默认模型）
        assertEquals(null, after.assistants.single().chatModelId)
        // 收藏夹去掉死引用，保留活引用
        assertEquals(
            listOf(Uuid.parse("10000000-0000-4000-8000-000000000001")),
            after.favoriteModels,
        )
        // 助手其余设置逐字段保留
        val a = after.assistants.single()
        assertEquals(userAssistant.name, a.name)
        assertEquals(userAssistant.systemPrompt, a.systemPrompt)
        assertEquals(userAssistant.temperature, a.temperature)
        assertEquals(userAssistant.maxTokens, a.maxTokens)
        assertEquals(userAssistant.enableWebSearch, a.enableWebSearch)
        assertEquals(userAssistant.regexes, a.regexes)
        // 助手列表与选中助手不变
        assertEquals(before.assistants, after.assistants.map { it.copy(chatModelId = before.assistants.single().chatModelId) })
        assertEquals(before.assistantId, after.assistantId)
        // 无关字段不变（主题/世界书等）
        assertEquals(before.lorebooks, after.lorebooks)
        assertEquals(before.themeId, after.themeId)
        // 修复行为被上报
        assertTrue("chatModelId" in report.repairedReferenceFields)
        assertTrue("fastModelId" in report.repairedReferenceFields)
    }

    // ---- 6. 引用有效时 sanitize 是无操作（不清空、不改写正常数据） ----

    @Test
    fun `sanitize is no-op when all references are valid`() {
        val userModel = Uuid.parse("10000000-0000-4000-8000-000000000001")
        val before = settings(
            providers = listOf(userOpenAi()),
            chatModelId = userModel,
            fastModelId = userModel,
            assistantModelId = userModel,
            favoriteModels = listOf(userModel),
        )

        val (after, repaired) = SettingsDataGuard.sanitizeDanglingModelReferences(before)

        assertEquals(before, after)
        assertTrue(repaired.isEmpty())
    }

    // ---- 7. 幂等：重复执行结果不变 ----

    @Test
    fun `remove and sanitize are idempotent`() {
        val user = listOf(userOpenAi(), userGoogle())
        val before = settings(
            providers = user + seededProvider(),
            chatModelId = seedModelUuid,
            assistantModelId = seedModelUuid,
        )

        val (first, report1) = SettingsDataGuard.removeSeededProviders(before, listOf(identity))
        val (second, report2) = SettingsDataGuard.removeSeededProviders(first, listOf(identity))

        assertEquals(first, second)
        assertTrue(report2.removedProviderIds.isEmpty())

        val (sanitized1, repaired1) = SettingsDataGuard.sanitizeDanglingModelReferences(first)
        val (sanitized2, repaired2) = SettingsDataGuard.sanitizeDanglingModelReferences(sanitized1)

        assertEquals(sanitized1, sanitized2)
        assertTrue(repaired2.isEmpty())
        // 第二轮 sanitize 也不产生新修复（第一轮已收敛）
        if (repaired1.isNotEmpty()) assertTrue(repaired2.isEmpty())
    }

    // ---- 8. 迁移不变量：受保护迁移通过校验 ----

    @Test
    fun `invariants pass for guarded migration`() {
        val user = listOf(userOpenAi(), userGoogle())
        val before = settings(providers = user + seededProvider(), chatModelId = seedModelUuid)

        val (after, _) = SettingsDataGuard.removeSeededProviders(before, listOf(identity))

        val violations = SettingsDataGuard.checkMigrationInvariants(before, after, listOf(identity))
        assertTrue("受保护迁移不应有不变量违规: $violations", violations.isEmpty())
    }

    // ---- 9. 迁移不变量：清空 providers / 删用户 Provider 被捕获 ----

    @Test
    fun `invariants catch user provider loss`() {
        val user = listOf(userOpenAi(), userGoogle())
        val before = settings(providers = user)

        // 模拟一次粗暴迁移：只留播种渠道（删光了用户渠道）
        val wiped = before.copy(providers = listOf(seededProvider()))
        val v1 = SettingsDataGuard.checkMigrationInvariants(before, wiped, listOf(identity))
        assertTrue(v1.any { it.rule == SettingsDataGuard.Rule.USER_PROVIDERS_PRESERVED })

        // 模拟清空 providers
        val emptied = before.copy(providers = emptyList())
        val v2 = SettingsDataGuard.checkMigrationInvariants(before, emptied, listOf(identity))
        assertTrue(v2.isNotEmpty())
    }

    // ---- 10. 迁移不变量：清空助手 / 悬空引用被捕获 ----

    @Test
    fun `invariants catch assistant wipe and dangling references`() {
        val before = settings(
            providers = listOf(userOpenAi(), seededProvider()),
            chatModelId = seedModelUuid,
        )

        // 清空助手
        val assistantsWiped = before.copy(assistants = emptyList())
        val v1 = SettingsDataGuard.checkMigrationInvariants(before, assistantsWiped, listOf(identity))
        assertTrue(v1.any { it.rule == SettingsDataGuard.Rule.ASSISTANTS_PRESERVED })

        // 留下悬空引用（providers 被清但 chatModelId 未修）
        val dangling = before.copy(providers = emptyList())
        val v2 = SettingsDataGuard.checkMigrationInvariants(before, dangling, listOf(identity))
        assertTrue(v2.any { it.rule == SettingsDataGuard.Rule.MODEL_REFERENCES_VALID })

        // 助手设置被篡改（systemPrompt 丢失）
        val tampered = before.copy(
            assistants = before.assistants.map { it.copy(systemPrompt = "") }
        )
        val v3 = SettingsDataGuard.checkMigrationInvariants(before, tampered, listOf(identity))
        assertTrue(v3.any { it.rule == SettingsDataGuard.Rule.ASSISTANTS_PRESERVED })
    }
}
