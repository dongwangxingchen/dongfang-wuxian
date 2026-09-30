package cc.nkbr.lanzouplus

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.lang.reflect.Method
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [DFWX] 远程控制与更新体系：后台数据客户端测试。
 *
 * 后端（PocketBase）已经上线并实测可用，但**App 端解析逻辑必须独立验证**——
 * 解析错了会直接导致"公告不显示"、"维护不开"或"更新指向错地址"，
 * 而这些在真机上很难复现（后台改一次数据要等）。
 *
 * 这里用**反射调用私有解析方法**，喂真实形状的 JSON（与实测的后台响应一致），
 * 逐条钉住边界。网络层（`get`）不在此测——那需要真服务器，属集成验证。
 *
 * **为什么必须挂 Robolectric**：`org.json` 在纯 JVM 单测里是**未实现的空桩**
 * （Android 把它当系统库），一调用就抛
 * `RuntimeException: Method optBoolean in org.json.JSONObject not mocked`。
 * 而本类全部逻辑都建立在 JSON 解析上，所以必须由 Robolectric 提供真实实现。
 * （同类坑此前在 `android.util.Log` 上踩过一次，见 `CrashLogStore`。）
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RemoteConfigClientJvmTest {

    private val cls = Class.forName("cc.nkbr.lanzouplus.RemoteConfigClient")

    private fun invoke(name: String, vararg args: Pair<Class<*>, Any?>): Any? {
        val types = args.map { it.first }.toTypedArray()
        val values = args.map { it.second }.toTypedArray()
        val m: Method = cls.getDeclaredMethod(name, *types)
        m.isAccessible = true
        return m.invoke(null, *values)
    }

    private fun parseControl(json: String): Any =
        invoke("parseControl", JSONObject::class.java to JSONObject(json))!!

    private fun parseRelease(json: String): Any? =
        invoke("parseRelease", JSONObject::class.java to JSONObject(json))

    @Suppress("UNCHECKED_CAST")
    private fun parseNotices(json: String): List<Any> =
        invoke("parseNotices", JSONArray::class.java to JSONArray(json)) as List<Any>

    @Suppress("UNCHECKED_CAST")
    private fun parseChangelogs(json: String): List<Any> =
        invoke("parseChangelogs", JSONArray::class.java to JSONArray(json)) as List<Any>

    /**
     * 反射调方法 / 读字段。
     *
     * 两个必须注意的点（都踩过）：
     * 1. 数据类是**包私有**、方法也是包私有 → 必须 `getDeclaredMethod` + `setAccessible`，
     *    `getMethod` 只找 public 会抛 `NoSuchMethodException`；
     * 2. **Java 字段没有 Kotlin 风格的自动 getter**——`final String title` 只能读字段，
     *    不存在 `getTitle()`。第一版我按 Kotlin 习惯写 `getSha256()` 等，全部找不到。
     */
    private fun call(target: Any, method: String): Any? {
        val m = target.javaClass.getDeclaredMethod(method)
        m.isAccessible = true
        return m.invoke(target)
    }

    private fun callBoolean(target: Any, method: String): Boolean = call(target, method) as Boolean

    /** 读 Java 字段（等价于 Kotlin 里的属性访问）。 */
    private fun field(target: Any, name: String): Any? {
        val f = target.javaClass.getDeclaredField(name)
        f.isAccessible = true
        return f.get(target)
    }

    private fun fieldString(target: Any, name: String): String = field(target, name) as String

    private fun fieldBoolean(target: Any, name: String): Boolean = field(target, name) as Boolean

    // ---------- control：维护/停用 ----------

    @Test
    fun control_allOff_doesNotBlock() {
        val c = parseControl("""{"maintenanceOn":false,"blocked":false,"maintenanceTitle":"","maintenanceBody":"","maintenanceUntil":""}""")
        assertFalse("全关时不得拦截应用", callBoolean(c, "blocksApp"))
        assertFalse("无维护时间时不显示该区块", callBoolean(c, "hasUntil"))
    }

    @Test
    fun control_maintenanceOn_blocksApp() {
        val c = parseControl("""{"maintenanceOn":true,"blocked":false,"maintenanceTitle":"维护中","maintenanceBody":"升级中","maintenanceUntil":"10月1日 20:00"}""")
        assertTrue("maintenanceOn=true 必须拦截整个应用", callBoolean(c, "blocksApp"))
        assertTrue("填了维护时间就应显示该区块", callBoolean(c, "hasUntil"))
        assertEquals("维护中", fieldString(c, "maintenanceTitle"))
        assertEquals("10月1日 20:00", fieldString(c, "maintenanceUntil"))
    }

    /** 用户明确要求：文字内容全部由后台决定，包括"永久停更"这类说法。 */
    @Test
    fun control_acceptsArbitraryUserText() {
        val c = parseControl("""{"maintenanceOn":true,"blocked":false,"maintenanceTitle":"已停更","maintenanceBody":"不再维护，感谢支持","maintenanceUntil":"永久"}""")
        assertEquals("已停更", fieldString(c, "maintenanceTitle"))
        assertEquals("不再维护，感谢支持", fieldString(c, "maintenanceBody"))
        assertEquals("永久", fieldString(c, "maintenanceUntil"))
    }

    @Test
    fun control_blockedAloneAlsoBlocks() {
        val c = parseControl("""{"maintenanceOn":false,"blocked":true,"maintenanceTitle":"","maintenanceBody":"","maintenanceUntil":""}""")
        assertTrue("blocked=true 同样必须拦截", callBoolean(c, "blocksApp"))
    }

    /** 缺失字段必须安全退化，不能崩（后台字段可能被改动）。 */
    @Test
    fun control_missingFieldsAreSafe() {
        val c = parseControl("""{}""")
        assertFalse(callBoolean(c, "blocksApp"))
        assertEquals("", fieldString(c, "maintenanceTitle"))
    }

    // ---------- release：版本 + 更新模式 ----------

    private val validRelease =
        """{"versionName":"1.22.20","versionCode":1039040,"apkUrl":"http://39.106.33.135/apk/x.apk","sha256":"${"a".repeat(64)}","size":100,"updateMode":"soft"}"""

    @Test
    fun release_parsesAllFields() {
        val r = parseRelease(validRelease)!!
        assertEquals("1.22.20", fieldString(r, "versionName"))
        assertEquals("soft", fieldString(r, "updateMode"))
        assertFalse(callBoolean(r, "isForce"))
        assertFalse(callBoolean(r, "isOff"))
    }

    /** 三种更新模式（用户要求可叠加控制）：软 / 强制 / 不提示。 */
    @Test
    fun release_updateModesAreDistinguished() {
        assertTrue(callBoolean(parseRelease(validRelease.replace("\"soft\"", "\"force\""))!!, "isForce"))
        assertTrue(callBoolean(parseRelease(validRelease.replace("\"soft\"", "\"off\""))!!, "isOff"))
        assertTrue(callBoolean(parseRelease(validRelease)!!, "isForce").not())
    }

    /** 缺关键字段的记录必须视为无效——绝不拿半残配置去提示更新。 */
    @Test
    fun release_rejectsIncompleteRecords() {
        assertNull("缺 apkUrl", parseRelease(validRelease.replace("\"http://39.106.33.135/apk/x.apk\"", "\"\"")))
        assertNull("缺版本号", parseRelease(validRelease.replace("\"1.22.20\"", "\"\"")))
        assertNull("sha256 格式不对", parseRelease(validRelease.replace("a".repeat(64), "deadbeef")))
        assertNull("size 为 0", parseRelease(validRelease.replace("\"size\":100", "\"size\":0")))
    }

    /** sha256 必须归一化为小写（服务端可能给大写）。 */
    @Test
    fun release_sha256IsNormalisedToLowerCase() {
        val r = parseRelease(validRelease.replace("a".repeat(64), "A".repeat(64)))!!
        assertEquals("a".repeat(64), fieldString(r, "sha256"))
    }

    @Test
    fun release_defaultModeIsSoftWhenMissing() {
        val json = validRelease.replace(",\"updateMode\":\"soft\"", "")
        val r = parseRelease(json)!!
        assertEquals("缺 updateMode 时默认软更新（最不打扰）", "soft", fieldString(r, "updateMode"))
    }

    // ---------- notice：公告 ----------

    @Test
    fun notices_skipsDisabledAndEmpty() {
        val list = parseNotices("""
        [
          {"id":"1","title":"公告A","body":"内容A","level":"normal","enabled":true},
          {"id":"2","title":"禁用","body":"内容","level":"normal","enabled":false},
          {"id":"3","title":"","body":"","level":"normal","enabled":true}
        ]""")
        assertEquals("只应保留启用的非空公告", 1, list.size)
        assertEquals("公告A", fieldString(list[0], "title"))
    }

    /** 用户要求"发布式、可叠加"：多条都要保留。 */
    @Test
    fun notices_preserveAllEnabledEntries() {
        val list = parseNotices("""
        [
          {"id":"1","title":"公告1","body":"x","enabled":true},
          {"id":"2","title":"公告2","body":"y","enabled":true},
          {"id":"3","title":"公告3","body":"z","enabled":true}
        ]""")
        assertEquals("三条公告必须全部保留（可叠加）", 3, list.size)
    }

    /** 置顶的必须排在最前。 */
    @Test
    fun notices_pinnedComeFirst() {
        val list = parseNotices("""
        [
          {"id":"1","title":"普通","body":"x","pinned":false,"enabled":true},
          {"id":"2","title":"置顶","body":"y","pinned":true,"enabled":true}
        ]""")
        assertEquals("置顶公告必须排第一", "置顶", fieldString(list[0], "title"))
    }

    @Test
    fun notices_levelsAreRecognised() {
        val list = parseNotices("""
        [
          {"id":"1","title":"紧急","body":"x","level":"urgent","enabled":true},
          {"id":"2","title":"重要","body":"y","level":"important","enabled":true},
          {"id":"3","title":"普通","body":"z","level":"normal","enabled":true}
        ]""")
        assertTrue(callBoolean(list[0], "isUrgent"))
        assertTrue(callBoolean(list[1], "isImportant"))
        assertFalse(callBoolean(list[2], "isUrgent"))
    }

    /** popup 开关：用户要求"想弹就弹、不想弹就静默发"。 */
    @Test
    fun notices_popupMode_isParsed() {
        // [DFW-70] 三档模式：silent（静默）/ once（一次性）/ always（永久）。
        val list = parseNotices("""
        [
          {"id":"1","title":"静默的","body":"x","popupMode":"silent","enabled":true},
          {"id":"2","title":"一次的","body":"y","popupMode":"once","enabled":true},
          {"id":"3","title":"永久的","body":"z","popupMode":"always","enabled":true}
        ]""")
        assertEquals("静默", RemoteConfigClient.Notice.MODE_SILENT, fieldString(list[0], "popupMode"))
        assertEquals("一次性", RemoteConfigClient.Notice.MODE_ONCE, fieldString(list[1], "popupMode"))
        assertEquals("永久", RemoteConfigClient.Notice.MODE_ALWAYS, fieldString(list[2], "popupMode"))
    }

    @Test
    fun notices_legacyPopupBoolean_isStillHonoured() {
        // **兼容读取**：后台还没迁移到 popupMode 时，旧布尔字段必须仍然管用——
        // 否则切字段的那一刻全体用户都收不到公告。
        val list = parseNotices("""
        [
          {"id":"1","title":"旧的弹的","body":"x","popup":true,"enabled":true},
          {"id":"2","title":"旧的不弹","body":"y","popup":false,"enabled":true}
        ]""")
        assertEquals("旧 popup=true 应落到「一次性」", RemoteConfigClient.Notice.MODE_ONCE, fieldString(list[0], "popupMode"))
        assertEquals("旧 popup=false 应落到「静默」", RemoteConfigClient.Notice.MODE_SILENT, fieldString(list[1], "popupMode"))
    }

    @Test
    fun notices_unknownPopupMode_fallsBackToOnce_neverToSilent() {
        // 拼错/空值 → 一次性（宁可多弹一次，也不能因为后台一次手误把公告全吞掉）。
        val list = parseNotices("""
        [
          {"id":"1","title":"拼错的","body":"x","popupMode":"alway","enabled":true},
          {"id":"2","title":"空的","body":"y","popupMode":"","enabled":true}
        ]""")
        assertEquals(RemoteConfigClient.Notice.MODE_ONCE, fieldString(list[0], "popupMode"))
        assertEquals(RemoteConfigClient.Notice.MODE_ONCE, fieldString(list[1], "popupMode"))
    }

    @Test
    fun notices_newFieldWins_overLegacyBoolean() {
        val list = parseNotices("""
        [
          {"id":"1","title":"新字段优先","body":"x","popup":false,"popupMode":"always","enabled":true}
        ]""")
        assertEquals("同时存在时应以新字段为准", RemoteConfigClient.Notice.MODE_ALWAYS, fieldString(list[0], "popupMode"))
    }

    // ---------- changelog：更新记录 ----------

    @Test
    fun changelogs_parseAndFilter() {
        val list = parseChangelogs("""
        [
          {"id":"1","versionName":"1.22.20","date":"2026-10-01","highlights":"修复A\n新增B","enabled":true},
          {"id":"2","versionName":"1.22.19","date":"2026-09-30","highlights":"旧版","enabled":false}
        ]""")
        assertEquals("只保留启用的记录", 1, list.size)
        assertEquals("1.22.20", fieldString(list[0], "versionName"))
        assertEquals("2026-10-01", fieldString(list[0], "date"))
        assertTrue("多行改动要保留换行", fieldString(list[0], "highlights").contains("\n"))
    }

    /** 用户要求"能看图每个日期、每个改的地方"：历史必须可叠加。 */
    @Test
    fun changelogs_keepHistory() {
        val list = parseChangelogs("""
        [
          {"id":"1","versionName":"1.0.3","date":"2026-10-03","highlights":"c","enabled":true},
          {"id":"2","versionName":"1.0.2","date":"2026-10-02","highlights":"b","enabled":true},
          {"id":"3","versionName":"1.0.1","date":"2026-10-01","highlights":"a","enabled":true}
        ]""")
        assertEquals("更新历史必须完整保留", 3, list.size)
    }

    // ---------- fail-open：服务器不可达 ----------

    /**
     * **这是本设计最重要的一条**：服务器连不上时必须按"一切正常"处理。
     * 否则用户的服务器一挂，全体用户当场打不开软件（决策 #39）。
     */
    @Test
    fun snapshot_unavailableFailsOpen() {
        // unavailable() 是 **Snapshot 内部类**的静态工厂，不是 RemoteConfigClient 的成员
        val snapshotClass = Class.forName("cc.nkbr.lanzouplus.RemoteConfigClient\$Snapshot")
        val factory = snapshotClass.getDeclaredMethod("unavailable")
        factory.isAccessible = true
        val snapshot = factory.invoke(null)!!
        assertFalse("不可达必须标记 unreachable", fieldBoolean(snapshot, "reachable"))
        val control = call(snapshot, "control")!!
        assertFalse("不可达时绝不能拦截应用（fail-open）", callBoolean(control, "blocksApp"))
        assertNull("不可达时没有版本信息", call(snapshot, "release"))
        @Suppress("UNCHECKED_CAST")
        val notices = call(snapshot, "notices") as List<Any>
        assertTrue("不可达时公告为空（静默降级）", notices.isEmpty())
    }

    // ---------- 接线：白名单必须放行（否则 Android 会直接掐断）----------

    @Test
    fun networkSecurityConfig_allowsTheBackendHost() {
        val config = File(
            (System.getProperty("user.dir") ?: ".").let { dir ->
                var d = File(dir).absoluteFile
                while (!File(d, "src/main/res/xml/network_security_config.xml").isFile && d.parentFile != null) d = d.parentFile
                d
            },
            "src/main/res/xml/network_security_config.xml",
        ).readText(Charsets.UTF_8)

        val host = Class.forName("cc.nkbr.lanzouplus.RemoteConfigClient")
            .getDeclaredField("BASE").let { it.isAccessible = true; it.get(null) as String }
            .removePrefix("http://").removePrefix("https://").substringBefore("/").substringBefore(":")
        assertTrue(
            "后台主机 $host 必须在明文白名单里，否则 Android 会直接掐断请求（配置再对也白搭）",
            config.contains(">$host<"),
        )
        assertTrue("base-config 仍必须默认拒绝明文（不能因为加白名单就全局放开）",
            config.contains("""cleartextTrafficPermitted="false""""))
    }
}
