package cc.nkbr.lanzouplus

import android.os.Looper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * [DFWX] DFW-67：**返回回调启用状态的同步守卫**。
 *
 * 用户 2026-10-01：
 * > "当我在软件库界面点击搜索按钮后，长按某个出现的安装包，会出现下面的菜单对吧，
 * >  我按理来说用安卓手机的反馈键（也就是边缘滑动）可以返回上一个界面（这个菜单会关掉），
 * >  但是我却**直接退出了软件**。但是我如果在主页打开软件库里的某个软件库，
 * >  就是不在搜索界面，那么我的交互逻辑是正常的。"
 *
 * ## 根因（只读诊断坐实，证据等级 A）
 * `backCallback` 的 enabled 状态是"**派生但被缓存**"的：只在**切页**时通过
 * `settlePageTransition()` / `refreshNavBall()` 重算（`MainActivity.syncBackCallbackEnabled()`）。
 * 而"进搜索态"和"长按进选择模式"**都不切页** → 回调停在"根页面 = 禁用"的旧状态 →
 * 系统发现没有任何 enabled 回调 → 自己接管返回 → `finish()`，App 直接退出。
 * `performSystemBack()` 里那条 `if(selectionMode){exitSelection();return;}` **根本没机会执行**。
 *
 * 目录页之所以正常，纯粹是因为它**切了页**、顺带同步了一次。
 *
 * 判据因此不是"返回之后发生了什么"，而是 **`backCallback.isEnabled` 本身**——
 * 因为一旦它是 false，App 内的返回代码就完全不在链路上。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class BackCallbackSyncJvmTest {

    companion object {
        @BeforeClass @JvmStatic fun silenceAutoImport() { MainActivity.LIBRARY_AUTO_IMPORT = false }
    }

    private fun activity(): MainActivity {
        val a = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        shadowOf(Looper.getMainLooper()).idle()
        return a
    }

    /**
     * **核心不变量**：回调的启用状态必须**始终等于** `canHandleBack()` 的结果。
     *
     * 一旦两者漂移，就说明"改了判定条件却没人重新同步"——用户丢返回键的全部原因都在这里。
     * 这比"某一步之后是不是 enabled"更耐得住时序：无论谁在什么时候把状态设成什么，
     * 只要和判定函数对不上就是 bug。
     */
    private fun assertNoDrift(a: MainActivity) {
        assertEquals(
            "回调启用状态与 canHandleBack() 漂移了：判定=${a.canHandleBack()} 实际=${a.backCallback.isEnabled}",
            a.canHandleBack(), a.backCallback.isEnabled,
        )
    }

    /** 绕开 260ms 返回节流（Robolectric 里 uptimeMillis 很小，不绕会无条件早退 → 假绿）。 */
    private fun pressBack(a: MainActivity) {
        a.lastSystemBackAt = -10_000L
        a.performSystemBack()
        shadowOf(Looper.getMainLooper()).idle()
    }

    // ── 基线：首页静息时**也启用**（DFW-73 改了语义，不是回归） ──────────────

    /**
     * 这条以前断言的是「禁用」（让系统接管、播"回桌面"预览）。
     * 2026-10-01 用户明确改了口径：
     * > "在顶级页面并且没有子页面的情况下，返回就是先提示『再返回一次退出软件』，然后再退出。"
     *
     * 所以顶级页也必须**自己**吃掉这次返回——一旦交还系统，Activity 立刻 finish，
     * 横幅根本没机会出现。作为交换，根页面不再做跟手预览
     * （`predictiveBackPreviewAllowed()` 在顶级根返回 false），页面不会莫名其妙地缩一下又弹回去。
     */
    @Test
    fun restingOnHome_backCallbackIsEnabled_byDesign() {
        val a = activity()
        a.navigateHome()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(
            "顶级页必须自己消费返回，否则「再返回一次退出软件」永远没机会出现",
            a.backCallback.isEnabled,
        )
        assertNoDrift(a)
    }

    // ── 核心：进搜索态必须立刻启用 ─────────────────────────────────────────

    @Test
    fun enteringHomeSearch_enablesTheBackCallbackImmediately() {
        val a = activity()
        a.navigateHome()
        shadowOf(Looper.getMainLooper()).idle()

        a.showHomeSearchMode(false)
        shadowOf(Looper.getMainLooper()).idle()
        assertNoDrift(a)

        assertTrue(
            "进搜索态后必须立刻重算回调：搜索态不切页，等不到切页那次同步 —— 用户就是在这一步丢掉返回键的",
            a.backCallback.isEnabled,
        )
    }

    @Test
    fun searchThenBack_exitsSearchInsteadOfTheApp() {
        val a = activity()
        a.navigateHome()
        shadowOf(Looper.getMainLooper()).idle()
        a.showHomeSearchMode(false)
        shadowOf(Looper.getMainLooper()).idle()

        pressBack(a)

        assertFalse("返回应退出搜索态", a.homeSearchFocused)
        assertFalse("绝不能退出应用", a.isFinishing)
        assertEquals("仍在首页", 0, a.pageKind)
    }

    // ── 用户报的那条：搜索态长按 → 返回只关菜单 ────────────────────────────

    @Test
    fun longPressSelection_backClosesTheMenu_andDoesNotExitTheApp() {
        val a = activity()
        a.navigateHome()
        shadowOf(Looper.getMainLooper()).idle()
        a.showHomeSearchMode(false)
        shadowOf(Looper.getMainLooper()).idle()

        a.enterSelection()
        shadowOf(Looper.getMainLooper()).idle()
        // ← 探针验证过：这一条能抓住"enterSelection 忘了同步"（只有它时会红）
        assertNoDrift(a)

        assertTrue(
            "长按进选择模式后，返回回调必须是启用的 —— 这就是用户报的那个 bug",
            a.backCallback.isEnabled,
        )

        val kindBefore = a.pageKind
        pressBack(a)

        assertFalse("返回应该只关掉选择工具条", a.selectionMode)
        assertEquals("不得切换页面", kindBefore, a.pageKind)
        assertFalse("**绝不能退出应用**（用户原话：我却直接退出了软件）", a.isFinishing)
    }

    @Test
    fun folderPage_selectionBackStillWorks() {
        // 反向守卫：目录页本来就是好的，修 bug 不能把它改坏。
        val a = activity()
        val source = Models.Source()
        source.title = "测试库"
        source.url = "https://example.com"
        source.password = ""
        a.showFolderPage(source, false)
        shadowOf(Looper.getMainLooper()).idle()

        a.enterSelection()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue("目录页选择模式下回调同样要启用", a.backCallback.isEnabled)

        pressBack(a)
        assertFalse("目录页返回同样只关工具条", a.selectionMode)
        assertFalse("目录页不得退出应用", a.isFinishing)
    }

    @Test
    fun enterSelection_fromRestingHome_keepsTheCallbackInSync() {
        // **隔离用例**：不经过搜索态，从"首页静息（回调禁用）"直接进选择模式。
        //
        // 为什么必须单独写一条：搜索态那条链路里 `showHomeSearchMode` 已经顺带把回调打开了，
        // 会**掩盖** `enterSelection` 自己的漏同步（探针实测：只撤掉 enterSelection 的同步，
        // 其它用例全绿）。选择模式的同步不能指望别处替它做。
        val a = activity()
        a.navigateHome()
        shadowOf(Looper.getMainLooper()).idle()
        // [DFW-73] 首页静息现在也是**启用**（顶级页要自己吃掉返回，先提示"再返回一次退出软件"）。
        assertTrue("前置：首页静息时回调是启用的（DFW-73 语义）", a.backCallback.isEnabled)

        // **刻意不 idle()**：探针实测，一旦让 Looper 空转，别处的延迟任务会顺带把回调打开，
        // 漏同步就被掩盖了（撤掉修复也全绿）。这里只认"这一刻"的同步结果。
        a.enterSelection()

        assertNoDrift(a)
        assertTrue("选择模式自己必须把返回回调打开", a.backCallback.isEnabled)
    }

    @Test
    fun exitingHomeSearchFocus_keepsTheCallbackInSync() {
        // 同样做隔离：退出搜索态后必须立刻回到禁用，否则系统"回桌面"预览会被 App 抢走，语义就错了。
        // 这条链路里没有别的同步点替它兜底。
        val a = activity()
        a.navigateHome()
        shadowOf(Looper.getMainLooper()).idle()
        a.showHomeSearchMode(false)
        shadowOf(Looper.getMainLooper()).idle()

        // 同样刻意不 idle()（见上一条用例的说明）。
        a.exitHomeSearchFocus()

        assertNoDrift(a)
        assertTrue("退回首页静息后仍保持启用（DFW-73：顶级页自己消费返回）", a.backCallback.isEnabled)
    }

    // ── 退出子态后标志要回到正确状态 ───────────────────────────────────────

    @Test
    fun exitingSelection_keepsTheCallbackConsistent() {
        val a = activity()
        a.navigateHome()
        shadowOf(Looper.getMainLooper()).idle()
        a.showHomeSearchMode(false)
        shadowOf(Looper.getMainLooper()).idle()
        a.enterSelection()
        shadowOf(Looper.getMainLooper()).idle()

        a.exitSelection()
        shadowOf(Looper.getMainLooper()).idle()
        assertNoDrift(a)

        // 还在搜索态，所以仍应启用（systemBackAction 还在）。
        assertTrue("退出选择模式后仍在搜索态，回调应保持启用", a.backCallback.isEnabled)

        a.exitHomeSearchFocus()
        shadowOf(Looper.getMainLooper()).idle()
        assertNoDrift(a)
        assertTrue("退回首页静息后仍保持启用（DFW-73：顶级页自己消费返回）", a.backCallback.isEnabled)
    }

    // ── 同类隐患：更新面板 ─────────────────────────────────────────────────

    @Test
    fun updateSheet_isPartOfTheBackDecision() {
        // `canHandleBack()` 里有一条 `offerSheet != null && offerSheet.isShowing()`，
        // 而面板出现/消失**都不切页** —— 不补同步就是同一个 bug 的另一个入口
        // （首页静息时弹出更新面板，按返回会直接退出 App 而不是关掉面板）。
        // 面板本体需要完整 offer 才建得起来，由 UpdateDialogJvmTest 覆盖；
        // 这里只钉住"没面板时不算数"这条前提，防止有人把判定条件删掉。
        val a = activity()
        a.navigateHome()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("前置：此刻没有更新面板", null, a.offerSheet)
        // [DFW-73] 首页静息现在**也**要拦截返回（顶级页先提示"再返回一次退出软件"），
        // 所以这里只能钉住"面板不在时判定不依赖面板"，不能再断言 false。
        val decisionWithoutSheet = a.canHandleBack()
        assertTrue("首页静息也必须拦截返回（DFW-73 语义）", decisionWithoutSheet)
    }
}
