package cc.nkbr.lanzouplus

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [DFW-128 2026-10-03] WAF cookie 的「新鲜度」判据 —— 「永远解析中」根因链的第 ① 段。
 *
 * ## 缺陷
 * `cookieFor()` 读的是**进程级全局 CookieManager**，里面可能还留着上一轮解出的 `acw_sc__v2`。
 * 旧判据只 `contains("acw_sc__v2")`，于是**第一个 tick（120ms）就"成功"返回** ——
 * 那时挑战页根本没跑完，返回的是**过期的旧 cookie**，还被写进缓存。
 * 之后这个域名每次都用这个死 cookie → 过不了阿里云 WAF →
 * `LanzouCore` 抛「蓝奏 ACW 验证未完成」→ `DirectLinkResolver` 误判成限流 →
 * 静默重试、永不回调 → **界面永远「解析中」**。
 *
 * ## 判据
 * 只有**与求解前不一样**的 cookie 才算解开 —— 因为调用方只在现有 cookie 不灵时才来求解。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-560dpi")
class WafCookieFreshnessJvmTest {

    companion object {
        @BeforeClass @JvmStatic fun silenceAutoImport() { MainActivity.LIBRARY_AUTO_IMPORT = false }

        private const val OLD = "acw_sc__v2=OLD_DEAD_VALUE; other=1"
        private const val NEW = "acw_sc__v2=BRAND_NEW_VALUE; other=1"
    }

    /** **核心回归：跟求解前一模一样的 cookie 不算解开。** 这正是那个 bug。 */
    @Test
    fun aCookieIdenticalToTheOneWeAlreadyHadIsNotASolve() {
        assertFalse(
            "求解前后 cookie 一模一样 = 挑战页还没跑完，拿到的是**过期旧 cookie**。\n" +
                "认了它就会写进缓存，此后该域名一直用死 cookie —— 这正是「永远解析中」的源头。",
            WafCookieSolver.isFreshSolve(OLD, OLD),
        )
    }

    /** 真的解出来了（值变了）才算成功。 */
    @Test
    fun aGenuinelyNewCookieCountsAsASolve() {
        assertTrue("求解后 cookie 变了，说明挑战真的过了", WafCookieSolver.isFreshSolve(NEW, OLD))
    }

    /** 进来时根本没有 cookie，解出来了 —— 正常首次求解。 */
    @Test
    fun theFirstSolveFromNothingCounts() {
        assertTrue("首次求解（之前没有 cookie）必须算成功", WafCookieSolver.isFreshSolve(NEW, ""))
        assertTrue("cookieBefore 为 null 时也要能正常工作", WafCookieSolver.isFreshSolve(NEW, null))
    }

    /** 压根没有目标 cookie 的，一律不算。 */
    @Test
    fun aCookieWithoutTheChallengeValueNeverCounts() {
        assertFalse(
            "没有 acw_sc__v2 就不是解出来的 WAF cookie",
            WafCookieSolver.isFreshSolve("sessionid=abc", OLD),
        )
        assertFalse("空串不算", WafCookieSolver.isFreshSolve("", OLD))
        assertFalse("null 不算", WafCookieSolver.isFreshSolve(null, OLD))
    }
}
