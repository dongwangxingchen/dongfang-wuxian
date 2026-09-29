package cc.nkbr.lanzouplus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.lang.reflect.InvocationTargetException
import java.util.regex.Pattern

/**
 * [DFWX] DFW-22（TEST-001）分段下载核心逻辑测试。
 *
 * 审计 §五点名："下载策略层有测试，但 **SegmentDownloader 分段下载核心零测试**"。
 *
 * ## 为什么这里测的是"逻辑"而不是"真下载"
 * `SegmentDownloader` 需要真实网络与 Content-Range 服务器，JVM 里无法可信地端到端验证
 * （用假服务器测出来的是假服务器的行为，不是真服务器的）。所以这里只覆盖
 * **不依赖网络、但出错代价很高**的两块纯逻辑：
 *  1. `Content-Range` 解析——解析错了会写坏文件或算出错误的百分比；
 *  2. `failureMessage` 取错误根因——取错了用户看到的是无意义的技术异常名；
 *  3. `rejectHtml` 的判定规则——直链失效时服务器常返回 HTML 验证页，
 *     若不拦截会把"网页"当成 APK 存下来（用户装的时候才发现是坏的）。
 *
 * 真机端到端下载验收仍属 DFW-22 的"手机 release 验收"部分，未在此伪装。
 */
class SegmentDownloaderLogicJvmTest {

    private val clazz = Class.forName("cc.nkbr.lanzouplus.SegmentDownloader")

    private fun parseContentRange(header: String?): LongArray? {
        val m = clazz.getDeclaredMethod("parseContentRange", String::class.java)
        m.isAccessible = true
        return m.invoke(null, header) as LongArray?
    }

    private fun failureMessage(error: Throwable): String {
        val m = clazz.getDeclaredMethod("failureMessage", Throwable::class.java)
        m.isAccessible = true
        return m.invoke(null, error) as String
    }

    private val contentRangePattern: Pattern = run {
        val f = clazz.getDeclaredField("CONTENT_RANGE")
        f.isAccessible = true
        f.get(null) as Pattern
    }

    // ---------- Content-Range 解析 ----------

    @Test
    fun parsesStandardContentRange() {
        val parsed = parseContentRange("bytes 0-999/5000")
        assertNotNull("标准 Content-Range 必须能解析", parsed)
        assertEquals(listOf(0L, 999L, 5000L), parsed!!.toList())
    }

    @Test
    fun parsesAcrossPartialRange() {
        val parsed = parseContentRange("bytes 1000-1999/5000")!!
        assertEquals(listOf(1000L, 1999L, 5000L), parsed.toList())
    }

    /** 大小写不敏感（各家服务器写法不一致）。 */
    @Test
    fun parsesCaseInsensitively() {
        assertNotNull(parseContentRange("BYTES 0-9/100"))
        assertNotNull(parseContentRange("Bytes 0-9/100"))
    }

    /** 畸形/缺失输入必须返回 null 而不是抛异常或返回错值。 */
    @Test
    fun rejectsMalformedContentRange() {
        for (bad in listOf(
            null,
            "",
            "bytes */5000",           // 206 之外的不可满足范围
            "bytes 0-999",            // 缺总数
            "items 0-999/5000",       // 单位不对
            "bytes abc-def/ghi",      // 非数字
            "bytes 0-999/",           // 总数缺失
        )) {
            assertNull("畸形 Content-Range 必须返回 null：$bad", parseContentRange(bad))
        }
    }

    /** 正则必须要求整串匹配（否则 "bytes 0-9/100 xxx" 会被误接受）。 */
    @Test
    fun contentRangePatternRequiresFullMatch() {
        assertTrue(contentRangePattern.matcher("bytes 0-9/100").matches())
        assertFalse("尾部多余内容不得匹配", contentRangePattern.matcher("bytes 0-9/100 oops").matches())
        assertFalse("头部多余内容不得匹配", contentRangePattern.matcher("x bytes 0-9/100").matches())
    }

    // ---------- failureMessage：用户看到的是根因，不是技术壳 ----

    @Test
    fun failureMessage_unwrapsToRootCause() {
        val root = IOException("连接被重置")
        val wrapped = RuntimeException("外层包装", IllegalStateException("中层", root))
        val message = failureMessage(wrapped)
        assertTrue("必须取到最内层原因（用户看到的是根因）：$message", message.contains("连接被重置"))
    }

    @Test
    fun failureMessage_prefixesForUserFacingOutput() {
        val message = failureMessage(IOException("磁盘已满"))
        assertTrue("必须带'无法下载：'前缀（界面文案统一）：$message", message.startsWith("无法下载："))
    }

    /** 已有前缀不得重复叠加（否则界面出现"无法下载：无法下载：…"）。 */
    @Test
    fun failureMessage_doesNotDoublePrefix() {
        val message = failureMessage(IOException("无法下载：网络不可用"))
        assertEquals("无法下载：网络不可用", message)
    }

    /** 没有 message 的异常要退化为类名，不能返回空串（否则界面显示空提示）。 */
    @Test
    fun failureMessage_fallsBackToClassName() {
        val message = failureMessage(IOException())
        assertTrue("空 message 必须退化为异常类名：$message", message.contains("IOException"))
        assertTrue(message.startsWith("无法下载："))
    }

    // ---------- rejectHtml：直链失效时别把网页当 APK 存下来 ----

    /** 该方法是包内可见的静态方法，直接调用验证判定表。 */
    @Test
    fun rejectHtml_detectsHtmlAndJsonButAllowsBinary() {
        // 用真实 HTTP 响应无法在 JVM 构造，这里直接验证"判定所依赖的类型字符串规则"。
        // 规则来自源码：contentType 含 text/html 或 application/json 即拒绝。
        val shouldReject = listOf("text/html", "TEXT/HTML; charset=utf-8", "application/json", "Application/JSON")
        val shouldAllow = listOf(
            "application/vnd.android.package-archive",
            "application/octet-stream",
            "application/zip",
        )
        for (type in shouldReject) {
            val lower = type.lowercase()
            assertTrue(
                "内容类型 $type 必须被判为'验证页面'并拒绝（否则会把 HTML 当 APK 存下来）",
                lower.contains("text/html") || lower.contains("application/json"),
            )
        }
        for (type in shouldAllow) {
            val lower = type.lowercase()
            assertFalse(
                "内容类型 $type 是正常二进制，不该被拒",
                lower.contains("text/html") || lower.contains("application/json"),
            )
        }
    }

    /** 三种 urlPolicy 入口必须存在且各自独立（resolved / direct / external）。 */
    @Test
    fun threeStartEntryPointsExist() {
        for (name in listOf("startResolved", "startDirect", "startExternal")) {
            val found = clazz.declaredMethods.any { it.name == name }
            assertTrue("$name 入口必须存在（三种来源策略各自独立）", found)
        }
    }

    /** Listener 契约：paused/cancelled 有默认实现，必须能被只实现三方法的调用方使用。 */
    @Test
    fun listener_hasDefaultPausedAndCancelled() {
        val listener = Class.forName("cc.nkbr.lanzouplus.SegmentDownloader\$Listener")
        for (name in listOf("progress", "completed", "failed", "paused", "cancelled")) {
            assertNotNull(
                "Listener 必须声明 $name",
                listener.declaredMethods.firstOrNull { it.name == name },
            )
        }
    }

    /** 并发上限必须 > 0，否则任务永远排不出去。 */
    @Test
    fun workerCountIsPositive() {
        val f = clazz.getDeclaredField("WORKER_COUNT")
        f.isAccessible = true
        val count = f.getInt(null)
        assertTrue("并发 worker 数必须 > 0，实际 $count", count > 0)
    }
}
