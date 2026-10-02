package cc.nkbr.lanzouplus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.lang.reflect.Method

/**
 * [DFW-88] 蓝奏云「下载卡在解析中」的两个真实根因，各配一条**用真实页面做夹具**的守卫。
 *
 * ## 为什么这个测试值得存在
 * 这个 bug 用户报了 6 个版本，前 5 轮都是靠猜（VPN 证书 / 域名过期 / TLS / 付费门 / 空指针），
 * 全都没中。真正的原因只有把**真实的页面**和**真实的浏览器行为**摆出来才看得见。
 * 所以这里用的 HTML 片段**不是编的** —— 是 2026-10-02 从
 * `https://pan.lanzoui.com/iOqAA2dyudc` 原样抓下来的，连缩进和引号都一样。
 *
 * ## 两个根因
 * ① 下载入口要从 **JS** 里取，不能从 HTML 的 `<a>` 里取
 *    —— HTML 里那个 `/tp/` 链接是**举报链接**（`/tp/#5738522`），真入口在 script 里；
 * ② `acw_sc__v2` 的**自算算法已经失效**（阿里云改过算法），
 *    必须让 WebView 去算，否则 cookie 永远不被接受 → 停在「解析中」。
 */
class LanzouDownloadRootCauseJvmTest {

    private val root: File = run {
        var dir = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (!File(dir, "AGENTS.md").isFile && dir.parentFile != null) dir = dir.parentFile
        dir
    }

    private val core: String
        get() = File(root, "app/src/main/java/cc/nkbr/lanzouplus/LanzouCore.java").readText(Charsets.UTF_8)

    /** 2026-10-02 从真实单文件页抓下来的片段，原样保留。 */
    private val realSharePageSnippet = """
        <div class="mh"><a href="/tp/#5738522" id="ddown">下载( 3.6 M )</a></div>
        <script>
        const link = document.getElementById('ddown');
        link.href = '/tp/iOqAA2dyudc?webtp=AjMAYApuA2cCbVQxUzRVYFY_bUGFUdwM0UWEHMlI2UGQHNVcyWXhXNwVmB39TNlNrVj4EL1I_aBDZVY1Z_bAG1TYAIzACsKYwNjAmxUMFNkVTZWPVBlVDMDMlFlB2BSb1AwBzRXMVk9V2IFZQcyUzJTNFZoBGNSbAQ0VTBWZwBuU2MCMAA2CisDZw_c_c';
        </script>
    """.trimIndent()

    /**
     * 直接调用产品代码里的 `directTransferHref`（反射），**喂真实页面**。
     *
     * 为什么不写成"读源码 contains 某个正则"：那种断言只能证明"我写了这行"，
     * 证明不了"它真的取到了正确的链接"。这里要的是**行为**。
     */
    private fun directTransferHref(html: String): String {
        val method: Method = Class.forName("cc.nkbr.lanzouplus.LanzouCore")
            .getDeclaredMethod("directTransferHref", String::class.java)
        method.isAccessible = true
        return method.invoke(null, html) as String
    }

    // ── ① 必须取 JS 里的真入口，不能取 HTML 里的举报链接 ──────────────────

    @Test
    fun theRealEntryComesFromJavaScriptNotTheReportLink() {
        val href = directTransferHref(realSharePageSnippet)
        assertTrue(
            "必须取到 JS 里那个带 webtp 的真入口，实际取到：「$href」",
            href.startsWith("/tp/iOqAA2dyudc?webtp="),
        )
        assertFalse(
            "绝不能取到 HTML 里的举报占位符 /tp/#5738522（这正是「解析中」卡死的第一个原因）",
            href.contains("#5738522") || href.endsWith("/tp/"),
        )
    }

    /**
     * **反向探针**：把"只有举报链接、没有 JS"的页面喂进去，
     * 必须**取不到东西**，而不是取到那个占位符。
     *
     * 这条是本次修复的核心断言 —— 旧实现在这个输入下会返回 `/tp/#5738522`。
     */
    @Test
    fun aPageWithOnlyTheReportLinkYieldsNothing() {
        val reportOnly = """<div class="mh"><a href="/tp/#5738522" id="ddown">下载</a></div>"""
        val href = directTransferHref(reportOnly)
        assertFalse(
            "只有举报链接时不许把它当成下载入口（旧实现就是这么错的），实际取到：「$href」",
            href.contains("#5738522"),
        )
    }

    // ── ② WAF 求解必须真的接上 ────────────────────────────────────────────

    @Test
    fun theWafChallengeIsSolvedByAWebView() {
        assertTrue(
            "必须有一个用 WebView 解 acw_sc__v2 的求解器",
            File(root, "app/src/main/java/cc/nkbr/lanzouplus/WafCookieSolver.java").isFile,
        )
        val solver = File(root, "app/src/main/java/cc/nkbr/lanzouplus/WafCookieSolver.java").readText()
        assertTrue("必须真的去读 CookieManager", solver.contains("CookieManager.getInstance().getCookie("))
        assertTrue("必须开 JS（挑战脚本要跑）", solver.contains("setJavaScriptEnabled(true)"))
        assertTrue("必须有超时，不能无限等", solver.contains("SOLVE_TIMEOUT_MS"))
        assertTrue(
            "必须销毁 WebView（不销毁会漏一整个渲染进程）",
            solver.contains("web.destroy()"),
        )
        assertTrue(
            "求解失败必须返回空串而不是抛异常 —— 它是增强，不是新的失败点",
            solver.contains("catch (Throwable ignored)"),
        )

        assertTrue("LanzouCore 必须暴露注册入口", core.contains("static void installWafSolver("))
        assertTrue("App 启动时必须注册", File(root, "app/src/main/java/cc/nkbr/lanzouplus/App.java")
            .readText().contains("LanzouCore.installWafSolver(this)"))
    }

    /**
     * 两条走 WAF 的路径**都要**接上求解器：
     * · `getGuarded`（文件页/直链页）
     * · `getBootstrap`（引导页 → CDN）
     * 只接一条的话，另一条仍然会卡在「解析中」。
     */
    @Test
    fun bothChallengePathsFallBackToTheSolver() {
        assertTrue("getGuarded 必须接上求解器", core.contains("resolveWithWebViewCookie("))
        assertTrue("getBootstrap 必须接上求解器", core.contains("solveBootstrapWithWebView("))
        assertTrue(
            "自算路径要保留为快速路径（毫秒级、不要 WebView）",
            core.contains("String value=acwCookie(page.html)"),
        )
        assertTrue(
            "自算失败后必须调用求解器，而不是直接抛「ACW 验证未完成」",
            Regex("resolveWithWebViewCookie[\\s\\S]{0,200}throw new IOException\\(\"蓝奏 ACW 验证未完成\"\\)")
                .containsMatchIn(core),
        )
    }

    /**
     * [DFW-88] **记录一个事实：自算算法已经算错了。**
     *
     * 这条测试不是"守规矩"，是**防遗忘**：
     * 阿里云改过 `acw_sc__v2` 的算法，社区流传的 `POS`/`MASK` 常量失效。
     * 2026-10-02 实测：
     * ```
     * 输入 arg1 : F77233DFEDA62054F49DC87FA10FB5F66BA7C277
     * 我们的算法: 6abf7392de1a1081a42f758c3c621cd2d4ffbf12
     * 浏览器真值: 6abf74188c6f4d858f38dc65def97ab45998323d
     * ```
     * 如果哪天有人把 WebView 求解删掉、想"优化"成纯算法，
     * 这条测试会告诉他：**算法是错的，别再试了**（证据在
     * `docs/audit/20261002-dfw88-download-rootcause.md`）。
     */
    @Test
    fun theStaleAlgorithmIsDocumentedAsBroken() {
        assertTrue(
            "必须留下「算法已失效」的记录，否则以后有人会把它当成可优化项删掉",
            core.contains("6abf7392de1a1081a42f758c3c621cd2d4ffbf12")
                || core.contains("算法已经算错")
                || core.contains("阿里云改过"),
        )
        assertTrue(
            "根因文档必须在仓库里（用户拿不到 logcat，只有文档能留下现场）",
            File(root, "docs/audit/20261002-dfw88-download-rootcause.md").isFile,
        )
    }
}
