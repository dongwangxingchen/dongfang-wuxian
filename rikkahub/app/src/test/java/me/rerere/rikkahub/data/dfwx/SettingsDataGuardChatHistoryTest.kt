package me.rerere.rikkahub.data.dfwx

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.db.entity.ConversationEntity
import me.rerere.rikkahub.data.model.Assistant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.uuid.Uuid

/**
 * [DFWX AI-003] 聊天历史保护测试：数据保护层执行一次完整的"移除播种渠道"迁移
 * （AI-004 将来要做的动作），真实 Room 内存库中的聊天正文必须原样保留。
 *
 * 保护机制本身就是"纯函数只变换 Settings、绝不触碰 Room"，
 * 本测试把该契约钉死：迁移前后 conversation 表逐行逐字段一致。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = me.rerere.rikkahub.dfwx.SweepTestApplication::class)
class SettingsDataGuardChatHistoryTest {

    private lateinit var db: AppDatabase

    private val seedModelUuid = Uuid.parse("00000000-0000-4000-8000-000000000001")
    private val identity = SettingsDataGuard.SeededProviderIdentity(
        name = "智能中转(内置)",
        baseUrl = "https://relay.example.test/v1",
        modelIds = listOf("glm-5.3"),
        apiKey = "fake-seeded-key-not-real",
    )

    @Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun conversation(id: String, title: String, nodes: String) = ConversationEntity(
        id = id,
        assistantId = "0950e2dc-9bd5-4801-afa3-aa887aa36b4e",
        title = title,
        nodes = nodes,
        createAt = 1000L,
        updateAt = 2000L,
        chatSuggestions = "[]",
        isPinned = false,
    )

    @Test
    fun `chat history and assistant settings survive guard migration`() = runBlocking {
        // 模拟存量数据：两条聊天（含正文 nodes JSON）+ 用户助手 + 播种渠道
        val dao = db.conversationDao()
        val chats = listOf(
            conversation("chat-1", "第一条聊天", """[{"role":"user","parts":[{"type":"text","text":"你好"}]}]"""),
            conversation("chat-2", "置顶的重要聊天", """[{"role":"assistant","parts":[{"type":"text","text":"长回复正文"}]}]"""),
        )
        chats.forEach { dao.insert(it) }

        val userProvider = ProviderSetting.OpenAI(
            id = Uuid.parse("20000000-0000-4000-8000-000000000001"),
            name = "我的中转",
            apiKey = "fake-user-key-not-real",
            baseUrl = "https://my.example.test/v1",
            models = listOf(
                Model(
                    modelId = "my-model",
                    id = Uuid.parse("10000000-0000-4000-8000-000000000001"),
                )
            ),
        )
        val seeded = ProviderSetting.OpenAI(
            id = Uuid.parse("00000000-0000-4000-8000-0000000000a1"),
            name = identity.name,
            apiKey = identity.apiKey!!,
            baseUrl = identity.baseUrl,
            models = listOf(
                Model(
                    modelId = "glm-5.3",
                    displayName = "glm-5.3",
                    id = seedModelUuid,
                    abilities = listOf(ModelAbility.TOOL, ModelAbility.REASONING),
                )
            ),
        )
        val assistant = Assistant(
            id = Uuid.parse("30000000-0000-4000-8000-000000000001"),
            name = "写作助手",
            systemPrompt = "你是一个写作助手",
        )
        val before = Settings(
            providers = listOf(userProvider, seeded),
            assistants = listOf(assistant),
            assistantId = assistant.id,
            chatModelId = seedModelUuid,
        )

        // 执行受保护迁移（AI-004 将来调用的同一路径）
        val (after, report) = SettingsDataGuard.removeSeededProviders(before, listOf(identity))
        val violations = SettingsDataGuard.checkMigrationInvariants(before, after, listOf(identity))

        // 迁移本身合规
        assertTrue("不变量违规: $violations", violations.isEmpty())
        assertEquals(listOf(userProvider), after.providers)

        // 聊天历史逐行逐字段原样保留（正文一个字符都不能少）
        val survived = dao.getAll().first()
        assertEquals(chats.size, survived.size)
        assertEquals(chats.sortedBy { it.id }, survived.sortedBy { it.id })

        // 幂等重放：再迁一次，聊天历史仍然完好
        val (again, _) = SettingsDataGuard.removeSeededProviders(after, listOf(identity))
        assertEquals(after, again)
        assertEquals(chats.sortedBy { it.id }, dao.getAll().first().sortedBy { it.id })
    }
}
