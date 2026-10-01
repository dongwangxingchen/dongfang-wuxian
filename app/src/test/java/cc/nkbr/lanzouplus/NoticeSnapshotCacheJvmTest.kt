package cc.nkbr.lanzouplus

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * [DFW-103] 公告快照的本地缓存：**让公告弹窗"秒弹"**。
 *
 * ## 用户反馈（2026-10-01）
 * > 「公告弹窗弹出太慢。」
 *
 * ## 根因（`MainActivity.maybeFetchNotices` 原文）
 * 启动后 `ui.post(this::maybeFetchNotices)` → 先发一次**完整网络请求**（超时连接 6s + 读取 8s）
 * → **拿到数据才弹**。而且**没有任何本地缓存**，每次都是现拉。
 * 网络好也要几百毫秒，网络差最坏 14 秒。
 *
 * ## 修法
 * 把上一次成功拉到的**四个集合的原始 JSON** 存盘；启动时先用它渲染并弹窗（零延迟），
 * 网络回来再覆盖。存原始 JSON 而不是存解析后的对象，是为了还原时**复用同一套解析器**
 * —— 否则会出现"缓存格式和线上格式不一致"这种只在离线路径才炸的坑。
 *
 * ## 这个测试为什么必须挂 Robolectric
 * 走的是 `SharedPreferences` + `org.json`，纯 JVM 下两者都是空桩（一调用就抛
 * "not mocked"）。所以这里的"真跑一遍存→读"是有实际鉴别力的，
 * 比"读源码断言字符串存在"强得多。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NoticeSnapshotCacheJvmTest {

    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    private fun raw(
        notices: String? = null,
        control: String? = null,
        release: String? = null,
        changelog: String? = null,
    ): RemoteConfigClient.Raw {
        val r = RemoteConfigClient.Raw()
        control?.let { r.control = JSONArray(it) }
        release?.let { r.release = JSONArray(it) }
        notices?.let { r.notice = JSONArray(it) }
        changelog?.let { r.changelog = JSONArray(it) }
        return r
    }

    private fun noticeJson(
        id: String = "n1",
        title: String = "测试公告",
        body: String = "正文",
        popupMode: String = "once",
        enabled: Boolean = true,
    ) = JSONObject()
        .put("id", id)
        .put("title", title)
        .put("body", body)
        .put("popupMode", popupMode)
        .put("enabled", enabled)

    @Test
    fun storeThenLoad_roundTripsTheNotice() {
        RemoteConfigClient.storeCache(ctx, raw(notices = JSONArray().put(noticeJson()).toString()))

        val snapshot = RemoteConfigClient.loadCache(ctx)
        assertNotNull("存进去的快照必须能读回来（否则秒弹根本无从谈起）", snapshot)
        assertEquals(1, snapshot!!.notices().size)
        val n = snapshot.notices()[0]
        assertEquals("n1", n.id)
        assertEquals("测试公告", n.title)
        assertEquals(RemoteConfigClient.Notice.MODE_ONCE, n.popupMode)
    }

    /** 没有缓存时必须老实返回 null，让调用方按"没有缓存"处理，而不是抛异常或返回假快照。 */
    @Test
    fun loadCache_withoutAnyCache_returnsNull() {
        assertNull("第一次装完软件还没有缓存，必须是 null", RemoteConfigClient.loadCache(ctx))
    }

    /**
     * 缓存被人改坏 / 旧版本残留 / 磁盘截断 —— 都不许崩。
     *
     * 两种坏法**期望不同**，这是有意的：
     * - 根本不是 JSON → 连解析都进不去，返回 null（当作没有缓存）；
     * - 是 JSON 但结构不对（某个集合不是数组）→ **安全降级成"该集合为空"**，仍返回可用快照。
     *   这比整体作废更好：其它集合还能正常用，网络回来之后自然会被覆盖。
     */
    @Test
    fun loadCache_withCorruptedData_neverCrashes() {
        ctx.getSharedPreferences("remote_config_cache-v1", Context.MODE_PRIVATE)
            .edit().putString("raw", "{这不是合法 JSON").apply()
        assertNull("连 JSON 都不是，只能当作没有缓存", RemoteConfigClient.loadCache(ctx))

        ctx.getSharedPreferences("remote_config_cache-v1", Context.MODE_PRIVATE)
            .edit().putString("raw", JSONObject().put("notice", "不是数组").toString()).apply()
        val degraded = RemoteConfigClient.loadCache(ctx)
        assertNotNull("是 JSON 但结构不对时安全降级成空快照（不许崩）", degraded)
        assertEquals("结构不对的那个集合当作空", 0, degraded!!.notices().size)
    }

    /**
     * **缓存必须复用线上那套解析器**，而不是自己写一套简化的。
     * 这里用"`enabled:false` 的公告要被过滤掉"来证明解析器真的跑了 ——
     * 如果缓存路径是自己 `new Notice(...)` 出来的，这个过滤就会被漏掉，
     * 结果就是"后台已经停用的公告还在弹"。
     */
    @Test
    fun loadCache_reusesTheRealParsers() {
        val notices = JSONArray()
            .put(noticeJson(id = "on", title = "要显示的"))
            .put(noticeJson(id = "off", title = "已停用的", enabled = false))
        RemoteConfigClient.storeCache(ctx, raw(notices = notices.toString()))

        val snapshot = RemoteConfigClient.loadCache(ctx)
        assertNotNull(snapshot)
        assertEquals("停用的公告必须被解析器过滤掉", 1, snapshot!!.notices().size)
        assertEquals("on", snapshot.notices()[0].id)
    }

    /** 四个集合都要能还原 —— 少一个就会出现"更新记录页空白"这种半残状态。 */
    @Test
    fun loadCache_restoresEveryCollection() {
        RemoteConfigClient.storeCache(
            ctx,
            raw(
                control = JSONArray().put(JSONObject().put("maintenanceOn", false).put("blocked", false)).toString(),
                release = JSONArray().put(
                    JSONObject().put("versionName", "9.9.9").put("versionCode", 90909)
                        .put("apkUrl", "http://example.invalid/a.apk").put("updateMode", "soft")
                        .put("sha256", "a".repeat(64)).put("size", 12345678L),
                ).toString(),
                notices = JSONArray().put(noticeJson()).toString(),
                changelog = JSONArray().put(
                    JSONObject().put("versionName", "9.9.9").put("date", "2026-10-01").put("highlights", "测试"),
                ).toString(),
            ),
        )

        val snapshot = RemoteConfigClient.loadCache(ctx)
        assertNotNull(snapshot)
        assertTrue("reachable 必须为 true，否则调用方会当成「没拉到」", snapshot!!.reachable)
        assertNotNull("版本信息要还原（更新检查用）", snapshot.release())
        assertEquals("9.9.9", snapshot.release()!!.versionName)
        assertEquals(1, snapshot.notices().size)
        assertEquals("更新记录要还原（更新记录页用）", 1, snapshot.changelogs().size)
    }

    /** 只存了部分集合（例如某次只有公告拉成功）也要能用，不能因为缺一块就整体作废。 */
    @Test
    fun loadCache_toleratesMissingCollections() {
        RemoteConfigClient.storeCache(ctx, raw(notices = JSONArray().put(noticeJson()).toString()))
        val snapshot = RemoteConfigClient.loadCache(ctx)
        assertNotNull("只拉到公告也应该可用", snapshot)
        assertEquals(1, snapshot!!.notices().size)
        assertNull("没存到的部分返回 null，由调用方兜底", snapshot.release())
        assertEquals(0, snapshot.changelogs().size)
    }

    /**
     * 启动现在会**两次**走到 `maybePopupNotices`（先缓存、后网络），
     * 所以必须按 id 去重，否则用户会看到**两个叠在一起的公告对话框**。
     *
     * 这条是**源码级**守卫（不是行为级）：`showNoticeDialog` 用的是自定义面板，
     * Robolectric 里没有一个稳定的"当前弹了几个"的可观察量。
     * 与其写一条看着像行为测试、其实什么都没验的假绿，不如老实做源码断言 + 反向探针。
     */
    @Test
    fun theSameNoticeIsNotPoppedTwice() {
        val main = File(repoRoot(), "app/src/main/java/cc/nkbr/lanzouplus/MainActivity.java")
            .readText(Charsets.UTF_8)
        val start = main.indexOf("void maybePopupNotices(){")
        assertTrue("找不到 maybePopupNotices", start >= 0)
        val open = main.indexOf('{', start)
        var depth = 0
        var end = open
        for (i in open until main.length) {
            when (main[i]) {
                '{' -> depth++
                '}' -> { depth--; if (depth == 0) { end = i; break } }
            }
        }
        val body = main.substring(open, end)

        assertTrue(
            "必须按 id 去重（否则缓存弹一次、网络回来又弹一次，两个对话框叠在一起）",
            body.contains("if(first.id.equals(lastPopupNoticeId))return;"),
        )
        assertTrue(
            "去重判断必须发生在真正弹窗**之前**",
            body.indexOf("lastPopupNoticeId") < body.indexOf("showNoticeDialog("),
        )
        assertTrue("要记住弹过哪条", body.contains("lastPopupNoticeId=first.id;"))
    }

    private fun repoRoot(): File {
        var dir = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (!File(dir, "AGENTS.md").isFile && dir.parentFile != null) dir = dir.parentFile
        return dir
    }

    /**
     * [DFW-103] 回归守卫：`fetch(Raw sink)` 里**不许直接解析 `sink.xxx`**。
     *
     * 正常路径是 `fetch()` → `fetch(null)`，此时 `sink` 是 null。
     * 如果写成 `notices = parseNotices(sink.notice)`，`fetch(null)` 会抛 NPE，
     * 而这个 NPE 被外面那圈 `catch (Exception ignored)` **静默吞掉** ——
     * 表现为"公告和更新记录永远拉不到"，而日志里一个字都没有。
     * 这个坑本次实际写出来过，靠编译后的复查发现。
     */
    @Test
    fun fetchMustNotDereferenceTheNullableSink() {
        val src = File(repoRoot(), "app/src/main/java/cc/nkbr/lanzouplus/RemoteConfigClient.java")
            .readText(Charsets.UTF_8)
        val start = src.indexOf("static Snapshot fetch(Raw sink) {")
        assertTrue("找不到 fetch(Raw sink)", start >= 0)
        val open = src.indexOf('{', start)
        var depth = 0
        var end = open
        for (i in open until src.length) {
            when (src[i]) {
                '{' -> depth++
                '}' -> { depth--; if (depth == 0) { end = i; break } }
            }
        }
        val body = src.substring(open, end)

        assertFalse(
            "fetch(null) 是正常路径，直接解引用 sink 会抛 NPE 并被静默吞掉：" +
                "表现为公告/更新记录永远拉不到、日志里却什么都没有",
            body.contains("parseNotices(sink.") || body.contains("parseChangelogs(sink."),
        )
        assertTrue("四个集合都要用局部变量接住", Regex("JSONArray items = items\\(").findAll(body).count() == 4)
    }
}
