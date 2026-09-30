package cc.nkbr.lanzouplus

import android.os.Looper
import android.view.View
import android.view.ViewGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * [DFWX] DFW-72：**公告弹窗的按钮语义 + 公告页的导航结构**守卫。
 *
 * 用户 2026-10-01 真机反馈两条（都是我在代码里能指到的实打实的 bug）：
 *
 * ① 「我点击知道后，按理来说它不应该会跳转到那个公告完整界面的。应该是直接关掉这个弹窗。」
 *    → 旧代码文案是「知道了」、绑的动作却是 `showNoticeCenter()`，文案与行为不一致。
 *
 * ② 「我点击那个公告按钮之后，它就会先打开设置界面，然后再打开公告……设置变成了主页面，
 *    公告变成了子页面……我从公告退出来后，默认是达到了设置界面。」
 *    → 旧代码 `primaryBase(fromHome?0:3)` 从悬浮球进来走 `false`，先把设置建成了底座。
 *
 * 这两条都不会崩、不会报错，只会让用户觉得"这东西没做对"——所以必须用测试钉住。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class NoticeFlowJvmTest {

    companion object {
        @BeforeClass @JvmStatic fun silenceAutoImport() { MainActivity.LIBRARY_AUTO_IMPORT = false }
    }

    private fun notice(
        id: String = "n1",
        title: String = "东方无限 公测开始",
        body: String = "短正文",
        level: String = "normal",
    ) = RemoteConfigClient.Notice(
        id, title, body, level, false, RemoteConfigClient.Notice.MODE_ONCE, 1_700_000_000_000L,
    )

    private fun activity(): MainActivity {
        val a = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        shadowOf(Looper.getMainLooper()).idle()
        return a
    }

    /**
     * 真按一次返回键。
     *
     * 必须绕开 260ms 返回节流（`if(uptimeMillis()-lastSystemBackAt<260)return;`）：
     * Robolectric 里 uptimeMillis 很小而 lastSystemBackAt=0，不绕过就会无条件早退，
     * 断言会**因为错误的原因变绿**（DFW-60 已经实测踩过一次，见 UpdateDialogJvmTest）。
     */
    private fun pressBack(a: MainActivity) {
        a.lastSystemBackAt = -10_000L
        a.performSystemBack()
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun findCapScroll(view: View): MainActivity.MaxHeightScrollView? {
        if (view is MainActivity.MaxHeightScrollView) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) findCapScroll(view.getChildAt(i))?.let { return it }
        return null
    }

    // ── ① 「知道了」必须只是关窗 ────────────────────────────────────────────

    @Test
    fun ack_closesTheDialog_andDoesNotNavigateAnywhere() {
        val a = activity()
        val kindBefore = a.pageKind
        a.showNoticeDialog(notice(), 0, null)
        shadowOf(Looper.getMainLooper()).idle()
        assertNotNull(a.noticeDialogPrimary)
        assertEquals("没有别的公告时，唯一动作就是「知道了」", "知道了", a.noticeDialogPrimary.text.toString())

        a.noticeDialogPrimary.performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertNull("点「知道了」必须关掉弹窗", a.noticeDialog)
        assertNotEquals("点「知道了」绝不能打开公告页（用户 2026-10-01 明确要求）", 10, a.pageKind)
        assertEquals("点「知道了」不该改变当前所在页", kindBefore, a.pageKind)
    }

    @Test
    fun ack_neverNavigates_evenWhenMoreNoticesRemain() {
        val a = activity()
        val kindBefore = a.pageKind
        a.showNoticeDialog(notice(), 2, null)
        shadowOf(Looper.getMainLooper()).idle()
        assertNotNull("还有未读时才该出现第二个按钮", a.noticeDialogSecondary)

        a.noticeDialogSecondary.performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertNull(a.noticeDialog)
        assertEquals("「知道了」在任何情况下都只是关窗", kindBefore, a.pageKind)
    }

    @Test
    fun viewAll_isTheOnlyActionThatOpensTheNoticePage() {
        val a = activity()
        a.showNoticeDialog(notice(), 2, null)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("还有未读时主按钮应是「查看全部」", "查看全部", a.noticeDialogPrimary.text.toString())

        a.noticeDialogPrimary.performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertNull(a.noticeDialog)
        assertEquals("「查看全部」才应该打开公告页", 10, a.pageKind)
    }

    @Test
    fun singleAction_whenNothingElseToRead() {
        // 旧版有「关闭」+「知道了」两个按钮，但两个都只是关窗——摆两个是噪音，
        // 还让人以为它们有区别。现在少于两条时只留一个。
        val a = activity()
        a.showNoticeDialog(notice(), 0, null)
        shadowOf(Looper.getMainLooper()).idle()
        assertNull("只有一条公告时不该出现第二个按钮", a.noticeDialogSecondary)
        assertEquals("知道了", a.noticeDialogPrimary.text.toString())
    }

    @Test
    fun urgentNotice_stillHasAnAcknowledgeAction() {
        val a = activity()
        a.showNoticeDialog(notice(level = "urgent"), 0, null)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("紧急公告也得让用户能确认", "知道了", a.noticeDialogPrimary.text.toString())
        assertNull(a.noticeDialogSecondary)
    }

    // ── ② 公告是顶级页，不是设置的子页 ──────────────────────────────────────

    @Test
    fun noticePage_isNeverBuiltOnTopOfSettings() {
        val a = activity()
        a.showHomeLanding()
        shadowOf(Looper.getMainLooper()).idle()
        a.showNoticeCenter()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(10, a.pageKind)
        assertNotEquals(
            "公告不该把设置当底座（设置=3）——旧版就是这里让公告变成设置的子页",
            3, a.primaryDestinationForTest(),
        )
    }

    @Test
    fun fromHome_backFromNoticeReturnsHome() {
        val a = activity()
        a.navigateHome()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(0, a.pageKind)

        a.showNoticeCenter()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("公告页应正常打开", 10, a.pageKind)

        pressBack(a)
        assertEquals("从首页进公告，返回必须回首页", 0, a.pageKind)
    }

    @Test
    fun fromSettings_backFromNoticeReturnsSettings() {
        val a = activity()
        a.showSettings()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(4, a.pageKind)

        a.showNoticeCenter()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(10, a.pageKind)

        pressBack(a)
        assertEquals("从设置进公告，返回必须回设置", 4, a.pageKind)
    }

    @Test
    fun navBall_lightsExactlyOneItem_whenNoticePageIsShowing() {
        val a = activity()
        a.showHomeLanding()
        shadowOf(Looper.getMainLooper()).idle()
        a.showNoticeCenter()
        shadowOf(Looper.getMainLooper()).idle()
        assertNotNull(a.navBall)
        assertEquals(
            "公告页在前台时菜单里只能亮一项，否则会同时亮「公告」和「软件库」",
            1, a.navBall.activePillCountForTest(),
        )
    }

    // ── ③ 那条「几百 dp 空白」的高度自适应 ──────────────────────────────────

    @Test
    fun bodyContainer_wrapsContent_andKeepsACap() {
        val a = activity()
        a.showNoticeDialog(notice(body = "很短的一条公告"), 0, null)
        shadowOf(Looper.getMainLooper()).idle()
        val scroller = findCapScroll(a.noticeDialog!!.window!!.decorView)
        assertNotNull("正文容器必须是可限高的 MaxHeightScrollView", scroller)
        assertEquals(
            "正文容器高度必须是 WRAP_CONTENT——旧版写死 420dp，短公告因此空出一大片",
            ViewGroup.LayoutParams.WRAP_CONTENT, scroller!!.layoutParams.height,
        )
        assertTrue("仍要上限兜底，长公告才不会撑爆屏幕", scroller.maxHeightPx() > 0)
    }

    @Test
    fun bodyCap_leavesRoomForChrome_andStaysAFloatingLayer() {
        val a = activity()
        val cap = a.noticeBodyMaxHeight()
        val screen = a.safeContentHeight()
        assertTrue("正文上限必须给标题/元信息/按钮留出位置", cap < screen)
        assertTrue("弹窗高过屏幕 58% 就不像浮层、像整页了", cap <= screen * 0.58f + 1f)
        assertTrue(cap > 0)
    }
}
