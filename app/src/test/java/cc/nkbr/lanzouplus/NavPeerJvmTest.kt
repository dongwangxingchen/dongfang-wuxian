package cc.nkbr.lanzouplus

import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * [DFW-73] 悬浮球六项**平级** + 顶级页"再返回一次退出软件"的回归守卫。
 *
 * 用户 2026-10-01 口述（原文）：
 * > "我点击那个公告按钮……它就会先打开设置界面，然后再打开公告，这会导致公告变成了子页面"
 * > "所有界面都要平级；在顶级页面并且没有子页面的情况下，返回就是先提示『再返回一次退出软件』，然后再退出"
 * > "侧滑返回不要总是回到软件库"
 *
 * 三个症状各自的根因（本测试直接钉住它们）：
 *  A. **公告页点悬浮球的『软件库』没反应** —— 公告页用 `primaryBase(0)` 当底座，
 *     `goToDestination(0)` 开头的 `destination == primaryDestination` 守卫把它整个吞掉。
 *  B. **顶级页返回一律回软件库** —— `performSystemBack` 结尾的 `if(primaryDestination>0) navigateHome()`。
 *  C. **顶级页第一下返回就直接退出** —— `canHandleBack()` 在根页面返回 false，
 *     把返回键交还系统，Activity 立刻 finish，"再返回一次退出软件"的横幅根本没机会出现。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class NavPeerJvmTest {

    companion object {
        @BeforeClass @JvmStatic fun silenceAutoImport() { MainActivity.LIBRARY_AUTO_IMPORT = false }
    }

    private fun activity(): MainActivity {
        val a = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        shadowOf(Looper.getMainLooper()).idle()
        return a
    }

    /** 绕开 260ms 节流，否则第二次返回会被"太频繁"直接丢弃 → 断言因为错误的原因变绿。 */
    private fun pressBack(a: MainActivity) {
        a.lastSystemBackAt = -10_000L
        a.performSystemBack()
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun textsIn(view: View, out: MutableList<String> = mutableListOf()): List<String> {
        if (view is TextView) view.text?.toString()?.let { if (it.isNotEmpty()) out.add(it) }
        if (view is ViewGroup) for (i in 0 until view.childCount) textsIn(view.getChildAt(i), out)
        return out
    }

    // ── 缺陷 A：公告页不是"软件库那一档" ────────────────────────────────────

    @Test
    fun noticePage_usesItsOwnTopLevelDestination() {
        val a = activity()
        a.showNoticeCenter()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals("前置：确实在公告页", 10, a.pageKind)
        assertEquals(
            "公告页的 primaryDestination 不能是 0 —— 否则 goToDestination(0) 会被守卫直接吞掉",
            MainActivity.DEST_NOTICE,
            a.primaryDestinationForTest(),
        )
    }

    @Test
    fun noticePage_tappingSoftwareLibraryActuallyGoesThere() {
        val a = activity()
        a.showNoticeCenter()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("前置：确实在公告页", 10, a.pageKind)

        a.goToDestination(0)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals("在公告页点悬浮球的『软件库』必须真的进软件库", 0, a.pageKind)
        assertEquals(0, a.primaryDestinationForTest())
    }

    @Test
    fun tappingNoticeAgainWhileOnNoticePageIsANoOp() {
        // 六项平级 = 连「点自己没反应」这一条也要一致：
        // 另外五项走 goToDestination，第一行 `destination==primaryDestination` 直接 return。
        // 公告若没有这个守卫，会在公告页上把公告**重建一遍**，顺手把「从哪来」弄丢
        // （重建时 noticeReturnAction 读到的 pageKind 已经是 10，退化成 navigateHome → 软件库）。
        val a = activity()
        a.showSettings()
        shadowOf(Looper.getMainLooper()).idle()
        a.showNoticeCenter()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("前置：在公告页", 10, a.pageKind)

        a.navBall!!.clickNoticeItem()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals("点「公告」自己不该换页", 10, a.pageKind)
        assertEquals(
            "也不该重建（重建会换掉 primaryDestination）",
            MainActivity.DEST_NOTICE,
            a.primaryDestinationForTest(),
        )

        pressBack(a)
        assertEquals("返回仍然要回进来那一页（设置），证明页面没被重建", 4, a.pageKind)
    }

    @Test
    fun noticePage_everyOtherNavItemIsDirectlyReachable() {
        // 用户说的是"六个界面全部平级"，所以除 AI（需要权限引导链，单测环境不可靠）以外都要过一遍。
        val cases = listOf(0 to 0, 2 to 2, 5 to 6, 3 to 4)
        for ((destination, expectedPageKind) in cases) {
            val a = activity()
            a.showNoticeCenter()
            shadowOf(Looper.getMainLooper()).idle()
            a.goToDestination(destination)
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(
                "从公告页点悬浮球第 $destination 项必须直达（期望 pageKind=$expectedPageKind）",
                expectedPageKind,
                a.pageKind,
            )
        }
    }

    @Test
    fun noticePage_isTopLevelButStillRemembersWhereYouCameFrom() {
        // 公告页虽然也是顶级页，但它记着"从哪来"（DFW-72 的结论，用户明确要的），
        // 所以它**不算**"没有上一层"的顶级根，返回仍然回原来那一页。
        val a = activity()
        a.showSettings()
        shadowOf(Looper.getMainLooper()).idle()
        a.showNoticeCenter()
        shadowOf(Looper.getMainLooper()).idle()

        assertFalse("公告页有 systemBackAction，不该被当成顶级根", a.atTopLevelRoot())

        pressBack(a)
        assertEquals("从设置进公告，返回必须回设置", 4, a.pageKind)
    }

    // ── 缺陷 B + C：顶级页"再返回一次退出软件" ─────────────────────────────

    @Test
    fun backCallback_isAlwaysEnabled_otherwiseBannerNeverShows() {
        val a = activity()
        assertTrue("顶级页也必须自己消费返回键，否则系统直接 finish，横幅没机会出现", a.canHandleBack())
        assertEquals(0, a.pageKind)
        assertTrue("软件库就是顶级根", a.atTopLevelRoot())
    }

    @Test
    fun rootBack_showsBannerAndStaysOnThePage() {
        val a = activity()
        assertEquals(0, a.pageKind)

        pressBack(a)

        assertEquals("第一次返回不许退出，也不许换页", 0, a.pageKind)
        assertFalse("第一次返回不许退出", a.isFinishing)
        assertNotNull("第一次返回必须弹顶部横幅", a.exitConfirmBanner)
        assertEquals(
            "横幅文案必须是用户定死的那句",
            "再返回一次退出软件",
            a.exitConfirmBanner!!.contentDescription?.toString(),
        )
        assertTrue("横幅里要真的有这句字", textsIn(a.exitConfirmBanner!!).contains("再返回一次退出软件"))
    }

    @Test
    fun rootBack_secondWithinWindowExits() {
        val a = activity()
        pressBack(a)
        assertFalse(a.isFinishing)
        pressBack(a)
        assertTrue("窗口内第二次返回才真的退出", a.isFinishing)
    }

    @Test
    fun rootBack_afterWindowExpiresAsksAgain() {
        val a = activity()
        pressBack(a)
        // 把"上一次提示时刻"推到窗口之外（等价于等了 2 秒以后再按一次）
        a.lastExitConfirmAt = a.lastExitConfirmAt - MainActivity.EXIT_CONFIRM_WINDOW_MS - 1
        pressBack(a)
        assertFalse("超出窗口的返回只重新提示，不许退出", a.isFinishing)
    }

    @Test
    fun settingsIsAlsoARoot_notSoftwareLibrary() {
        // 用户抱怨的核心：从设置侧滑返回，结果掉到软件库去了。
        val a = activity()
        a.goToDestination(3)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("前置：在设置页", 4, a.pageKind)

        pressBack(a)

        assertEquals("顶级页返回不许把用户甩回软件库", 4, a.pageKind)
        assertFalse(a.isFinishing)
        assertNotNull(a.exitConfirmBanner)
    }

    @Test
    fun downloadsAndToolsAreRootsToo() {
        for (destination in listOf(2, 5)) {
            val a = activity()
            a.goToDestination(destination)
            shadowOf(Looper.getMainLooper()).idle()
            val kind = a.pageKind
            assertTrue("第 $destination 项本身应是顶级根", a.atTopLevelRoot())
            pressBack(a)
            assertEquals("第 $destination 项返回不许换页", kind, a.pageKind)
            assertFalse("第 $destination 项第一次返回不许退出", a.isFinishing)
        }
    }

    // ── 子页返回语义不许被连带改坏 ─────────────────────────────────────────

    @Test
    fun subPageBack_stillGoesUpExactlyOneLevel() {
        val a = activity()
        a.showSettings()
        shadowOf(Looper.getMainLooper()).idle()
        a.showAboutPage()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("前置：在关于页", 7, a.pageKind)
        assertFalse("子页不是顶级根", a.atTopLevelRoot())

        pressBack(a)

        assertEquals("子页返回回到设置", 4, a.pageKind)
        assertFalse("子页返回绝不能退出应用", a.isFinishing)
    }

    @Test
    fun sourceListFromSettings_backGoesToSettingsNotSoftwareLibrary() {
        val a = activity()
        a.showSettings()
        shadowOf(Looper.getMainLooper()).idle()
        a.showSourcesFromSettings()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("前置：在资源源管理页", 1, a.pageKind)

        pressBack(a)

        assertEquals("资源源管理返回必须回设置", 4, a.pageKind)
    }

    // ── 预测式返回预览：AI 页 / 顶级页不跟手 ────────────────────────────────

    @Test
    fun predictivePreview_isSuppressedOnTopLevelRoot() {
        val a = activity()
        assertFalse(
            "顶级页的返回语义只是「再返回一次退出软件」，页面本身不离开，所以不该有跟手缩放",
            a.predictiveBackPreviewAllowed(),
        )
        a.showSettings()
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(a.predictiveBackPreviewAllowed())
        a.showAboutPage()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue("子页必须有跟手预览（预测式返回）", a.predictiveBackPreviewAllowed())
    }

    @Test
    fun predictivePreview_isSuppressedOnAiPage() {
        val a = activity()
        a.pageKind = 5
        assertFalse("用户要求 AI 对话保持原有动画，不参与应用内跟手预览", a.predictiveBackPreviewAllowed())
    }

    // ── 初始界面设置 ─────────────────────────────────────────────────────

    @Test
    fun startDestination_onlyAcceptsTheThreeOptionsUserGave() {
        val a = activity()

        assertEquals("默认必须是软件库，老用户升级后行为不许变", 0, a.startDestinationPreference())

        for (value in listOf(0, 4, 5)) {
            a.setStartDestinationPreference(value)
            assertEquals(value, a.startDestinationPreference())
        }

        // 用户只给了三个选项：软件库 / AI 对话 / 工具箱。
        // 下载(2)、设置(3)、公告(6) 以及任何垃圾值都必须回落软件库，不许放进来。
        for (value in listOf(1, 2, 3, 6, -1, 99)) {
            a.setStartDestinationPreference(value)
            assertEquals("非法值 $value 必须回落成软件库", 0, a.startDestinationPreference())
        }
    }

    @Test
    fun startDestination_rowOffersExactlyThreeChipsAndWritesThrough() {
        val a = activity()
        val row = a.buildStartDestinationRow()
        val chips = mutableListOf<TextView>()
        fun collect(view: View) {
            if (view is TextView && view.getTag() is Int) chips.add(view)
            if (view is ViewGroup) for (i in 0 until view.childCount) collect(view.getChildAt(i))
        }
        collect(row)
        assertEquals("初始界面只能有用户给的三个选项", 3, chips.size)
        assertEquals(listOf("软件库", "AI 对话", "工具箱"), chips.map { it.text.toString() })

        chips[2].performClick()
        assertEquals("点『工具箱』必须写进偏好", 5, a.startDestinationPreference())
        assertTrue("选中的芯片必须标出已选（无障碍）", chips[2].contentDescription.contains("已选择"))
        assertFalse("没选中的不许谎报已选", chips[0].contentDescription.contains("已选择"))
    }

    // ── 弹窗动画统一（自查，防止有人把 windowAnimationStyle 删了） ──────────

    @Test
    fun allAlertDialogsShareOneWindowAnimation() {
        // 弹窗动画统一走 AppTheme → alertDialogTheme(DfwxDialog) → windowAnimationStyle，
        // 于是所有 AlertDialog 自动一致，不需要改任何调用点。
        // 这里从资源里读回来，删掉任意一环都会红。
        val context: android.content.Context = org.robolectric.RuntimeEnvironment.getApplication()
        val theme = context.resources.newTheme()
        theme.applyStyle(R.style.DfwxDialog, true)
        val typed = theme.obtainStyledAttributes(
            intArrayOf(android.R.attr.windowAnimationStyle),
        )
        val styleRes = typed.getResourceId(0, 0)
        typed.recycle()
        assertTrue("DfwxDialog 必须指定 windowAnimationStyle", styleRes != 0)

        val animTheme = context.resources.newTheme()
        animTheme.applyStyle(styleRes, true)
        val anim = animTheme.obtainStyledAttributes(
            intArrayOf(
                android.R.attr.activityOpenEnterAnimation,
                android.R.attr.activityCloseExitAnimation,
            ),
        )
        val enter = anim.getResourceId(0, 0)
        val exit = anim.getResourceId(1, 0)
        anim.recycle()
        assertEquals(R.anim.dfwx_dialog_in, enter)
        assertEquals(R.anim.dfwx_dialog_out, exit)
    }

    @Test
    fun noNavItemIsLeftWithoutASwitcher() {
        // 悬浮球六项：0 软件库 / 4 AI / 2 下载 / 5 工具箱 / 3 设置 / -2 公告。
        // 这条只做一个廉价的"没人被漏掉"守卫：DEST_NOTICE 不能和任何一项撞号。
        val navItems = listOf(0, 4, 2, 5, 3, -2)
        assertFalse("公告哨兵值不能和悬浮球任何一项撞号", navItems.contains(MainActivity.DEST_NOTICE))
    }

    @Test
    fun exitBanner_hangsOnHostSoTheStatusBarCannotCoverIt() {
        // 用户发的图一就是"页面顶部被状态栏挡住"，所以横幅必须挂在 host 的内容区里
        // （host 的 padding 由 installSystemNavigationInsets 按真实系统栏设置），
        // 而**不能**挂进 root（root 一个系统栏 padding 都没有）。
        val a = activity()
        pressBack(a)
        val banner = a.exitConfirmBanner!!
        assertTrue("横幅必须直接挂在 host 上", banner.parent === a.host)
        assertFalse("横幅绝不能挂进页面的 root", banner.parent === a.root)
    }
}
