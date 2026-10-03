package cc.nkbr.lanzouplus

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [DFW-129 2026-10-03] 单文件分享页识别 —— 「No group 1」崩溃的回归。
 *
 * ## 真机日志（用户提供，2026-10-03）
 * ```
 * 10-03 18:04:41  解析失败 -> [IndexOutOfBoundsException] No group 1        ← yoyodadada.lanzouw.com
 * 10-03 19:02:06  解析成功 -> https://c1031.dmpdmp.com/...                  ← oreojiang.lanzout.com
 * ```
 * **同一个 App、同一套代码，一个域名每次都成、另一个每次都炸。**
 *
 * ## 根因
 * `LanzouCore.isSingleFileSharePage()` 里有四个 `cap()` 调用，其中 iframe 那个
 * **正则漏了捕获组**，而 `cap()` 里调的是 `m.group(1)`：
 * ```
 * m.find() ? unescape(m.group(1).trim()) : ""     ← 只判了"有没有匹配"，没判"有没有捕获组"
 * ```
 * 于是只要页面里有 `<iframe src=".../fn?...">` 就**匹配成功然后抛异常**。
 *
 * 新版蓝奏单文件页正好是 iframe 格式（`lanzouw.com` 那批），
 * 老版页面是 `id="downurl"` 格式（`lanzout.com` 那批）——
 * **这就是「有的链接能下、有的不行」的真正原因，不是网络也不是限流。**
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-560dpi")
class SingleFilePageDetectionJvmTest {

    companion object {
        @BeforeClass @JvmStatic fun silenceAutoImport() { MainActivity.LIBRARY_AUTO_IMPORT = false }

        /** 新版蓝奏单文件页：靠 iframe 承载 `/fn?...` 下载入口。就是它把解析炸掉的。 */
        private val IFRAME_PAGE = """
            <!DOCTYPE html><html><head>
            <meta name="description" content="文件大小：42.0 M">
            <title>Memento - 个人数据库 v5.9.3 Pro - 蓝奏云</title>
            </head><body>
            <div class="appfile"><div class="filetitle">Memento</div></div>
            <iframe class="ifr2" src="https://developer.lanzoug.com/fn?ABC123DEF" frameborder="0"></iframe>
            </body></html>
        """.trimIndent()

        /** 老版蓝奏单文件页：直接给 `id="downurl"`。这一种一直没坏。 */
        private val DOWNURL_PAGE = """
            <!DOCTYPE html><html><head>
            <title>资源猫 v5.0.8 - 蓝奏云</title>
            </head><body>
            <div class="appfile"></div>
            <a id="downurl" href="https://developer.lanzoug.com/file/abc">下载</a>
            </body></html>
        """.trimIndent()
    }

    /**
     * **核心回归：iframe 版页面必须被认出来，而且不许抛异常。**
     *
     * 修之前这里抛 `IndexOutOfBoundsException: No group 1` —— 真机日志里那一条。
     */
    @Test
    fun theIframeStyleSharePageIsRecognisedWithoutThrowing() {
        assertTrue(
            "新版蓝奏单文件页（iframe 承载 /fn? 入口）必须被认成单文件页。\n" +
                "修之前这里抛 IndexOutOfBoundsException: No group 1 —— 用户日志里那条。",
            LanzouCore.isSingleFileSharePage(IFRAME_PAGE),
        )
    }

    /** 老版页面不能被这次修改弄坏。 */
    @Test
    fun theLegacyDownurlStyleSharePageStillWorks() {
        assertTrue("老版 id=downurl 格式必须仍然被认出来", LanzouCore.isSingleFileSharePage(DOWNURL_PAGE))
    }

    /** 两个版本同时出现也要认。 */
    @Test
    fun aPageCarryingBothMarkersIsStillRecognised() {
        assertTrue(LanzouCore.isSingleFileSharePage(IFRAME_PAGE.replace("</body>", "<a id=\"downurl\" href=\"/x\">下</a></body>")))
    }

    /** 不是分享页的要老老实实返回 false，不能因为加了防御就一律返回 true。 */
    @Test
    fun aNonSharePageIsNotMisdetected() {
        assertFalse("普通网页不许被误判成单文件分享页", LanzouCore.isSingleFileSharePage("<html><body><p>hello</p></body></html>"))
        assertFalse("空 HTML 不算", LanzouCore.isSingleFileSharePage(""))
        assertFalse("null 不许抛异常", LanzouCore.isSingleFileSharePage(null))
    }

    /** 目录分享页（有 filemoreajax）必须被排除，不能被当成单文件。 */
    @Test
    fun aDirectorySharePageIsStillExcluded() {
        val directory = """
            <html><body>
            <iframe src="/fn?ABC"></iframe>
            <script>var xx = 'url: "/filemoreajax.php?file=123"';</script>
            </body></html>
        """.trimIndent()
        assertFalse("目录分享页必须排除，哪怕它也有 iframe", LanzouCore.isSingleFileSharePage(directory))
    }
}
