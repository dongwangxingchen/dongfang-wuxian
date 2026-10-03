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

        /**
         * [DFW-135 2026-10-03] **第三种模板：`id="ddown"` + JS 写入的 `/tp/…?webtp=`。**
         *
         * 这是**逐字节从线上抓下来的真实页面**（不是编的）：
         * `curl -A "<Android UA>" https://yoyodadada.lanzouw.com/iEDWr2dg6vih`
         * 返回 1667 字节，分享的是 `*裁剪助手1.0.0.exe`（93.9 M）。
         *
         * 它为什么一直解析失败：三种模板里它最"朴素"——
         * 没有 `id="downurl"`、没有 iframe `/fn?`、没有 `class="appfile"`、
         * 没有 `id="filenajax"`，`pageTemplate()` 也不认它（既没有 folder2/3/4.css
         * 也没有 t0.css）→ 五条判据全落空 → `requireActiveShare()` 直接抛异常。
         *
         * 注意标题结尾是「**蓝奏云网盘**」而不是「蓝奏云」——这一处差别单独有测试盯着。
         */
        private val DDOWN_TP_PAGE = """
            <!DOCTYPE html>
            <html>
            <head>
            <meta http-equiv="Content-Type" content="text/html; charset=utf-8" />
            <meta name="viewport" content="width=device-width,initial-scale=1.0,maximum-scale=1.0,user-scalable=0" />
            <title>*裁剪助手1.0.0.exe - 蓝奏云网盘</title>
            <meta name="description" content="文件大小：93.9 M" />
            <link rel="shortcut icon" href="https://images.bakstotre.com/assets/favicon.ico">
            <link href="https://images.bakstotre.com/assets/share/m3.css" rel="stylesheet" type="text/css">
            </head>
            <body>
            <div class="top">
            </div>
            <div class="mb">
            <div style="margin-top: 100px;">
            </div>
            <div class="mico" style="background-image: url(https://images.bakstotre.com/assets/images/type/exe.gif);">
            </div>
            <div class="md">*裁剪助手1.0.0.exe </div>
            <div class="mf">
            <span class="mt2">分享:</span>yoyodadada <span class="mt2">
            </span>2024-10-27  <span class="mt2">
            <a href="/home/?f=205716547&report=1" target="_blank">举报</a>
            </span>
            </div>
            <div class="mad">
            </div>
            <div class="mh">
            <a href="/tp/#205716547" id="ddown">下载( 93.9 M )</a>
            </div>
            </div>
            <script>
            const link = document.getElementById('ddown');
            link.href = '/tp/iEDWr2dg6vih?webtp=BDVQMAhsBmNVM1Q2BmJWYlA9U2RQcwMzADcAM1M4VmZXYQFjXjcBYgUvC29WMFZkWy8FOAdpU39SZAA_bUjYDLAQ1UDAIKQY0VWdUMAYxVmRQOVMwUGwDNwBiADBTPlZvVzUBYl4xATcFNQs4VjdWMVs1BTQHa1MwUmUAMlJrAzEEM1AxCGQGLlUz';
            </script>
            <div style="display:none">
            <script src="https://statics.woozooo.com/img/bd.js">
            </script>
            </div>
            </body>
            </html>
        """.trimIndent()

        /** 标题结尾是「蓝奏云网盘」的那种（多两个字就整串匹配失败）。 */
        private val TITLE_WITH_WANGPAN = """
            <html><head>
            <title>某个文件.rar - 蓝奏云网盘</title>
            <meta name="description" content="文件大小：12.3 M">
            </head><body><p>没有任何下载入口标记</p></body></html>
        """.trimIndent()
    }

    /**
     * **[DFW-135 核心回归] `id="ddown"` + `/tp/…?webtp=` 模板必须被认出来。**
     *
     * 用户原话：「除了 apk 格式外的其他格式东西都解析失败的问题解决了没，还是忘了？」
     * 这个模板正是那些"其他格式"（exe / rar / zip…）用的。
     *
     * 修之前：`requireActiveShare()` 抛「当前 UA 未返回可解析的蓝奏分享页」→
     * 界面一直停在「解析中」，最后报解析失败。
     */
    @Test
    fun theDdownTpStyleSharePageIsRecognised() {
        assertTrue(
            "线上真实的 id=ddown + /tp/…?webtp= 页面必须被认成单文件分享页。\n" +
                "这是「非 APK 文件全都解析失败」的根因页面。",
            LanzouCore.isSingleFileSharePage(DDOWN_TP_PAGE),
        )
    }

    /**
     * 标题结尾多两个字（「蓝奏云」→「蓝奏云网盘」）不该让整条判据失效。
     *
     * 这一页**故意不带任何下载入口标记**，只靠标题 + description 认，
     * 所以它单独测的就是那条 `String.matches()` 全串匹配的坑。
     */
    @Test
    fun theTitleSuffixAfterLanzouIsStillAccepted() {
        assertTrue(
            "标题「… - 蓝奏云网盘」后面多两个字，不该让判据失效；" +
                "原来的正则写到「蓝奏云」就结束，而 matches() 要求整串匹配。",
            LanzouCore.isSingleFileSharePage(TITLE_WITH_WANGPAN),
        )
    }

    /**
     * 下载入口必须从 **JS** 里取，不能取成那个举报链接。
     *
     * 页面里有两个 `/tp/` 开头的 href：
     *   - `<a href="/tp/#205716547" id="ddown">`  ← 举报占位符，取到它必然解析失败
     *   - `link.href = '/tp/iEDWr2dg6vih?webtp=…'` ← 真入口
     * 判据是「带不带 `?webtp=`」。
     */
    @Test
    fun theDownloadEntryComesFromTheScriptNotTheReportLink() {
        val href = LanzouCore.directTransferHref(DDOWN_TP_PAGE)
        assertTrue("必须取到 JS 里那个 /tp/ 入口，实际取到的是「$href」", href.startsWith("/tp/iEDWr2dg6vih"))
        assertTrue("必须带 ?webtp= 参数，实际是「$href」", href.contains("?webtp="))
        assertFalse("不许取到举报占位符 /tp/#205716547", href.contains("#205716547"))
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
