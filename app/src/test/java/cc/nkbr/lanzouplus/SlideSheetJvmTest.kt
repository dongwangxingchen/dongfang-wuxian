package cc.nkbr.lanzouplus

import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
 * [DFWX] DFW-66：可滑动面板的**双向**手势真值表。
 *
 * 用户要求两种面板：
 * - 更新弹窗：**从底部往上升** → 往下滑关闭
 * - 提示条：**从顶部往下滑出来** → 往上滑关闭
 *
 * 两者共用一套手势核心，只有"从哪边进出"和"往哪边滑算关闭"不同。
 * 这里把**两个方向各自的真值表**都钉死——搞反方向的话，用户往上一滑面板纹丝不动，
 * 或者反方向轻轻一碰就飞走。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class SlideSheetJvmTest {

    companion object {
        @BeforeClass @JvmStatic fun silenceAutoImport() { MainActivity.LIBRARY_AUTO_IMPORT = false }
    }

    private class Harness(val a: MainActivity, val content: LinearLayout, val sheet: SlideSheet) {
        var dismissed = false
    }

    private fun harness(edge: SlideSheet.Edge, configure: (SlideSheet) -> SlideSheet = { it }): Harness {
        val a = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        a.suppressUiMotion = true
        shadowOf(Looper.getMainLooper()).idle()
        val content = LinearLayout(a)
        content.orientation = LinearLayout.VERTICAL
        content.addView(TextView(a).apply { text = "面板内容" }, ViewGroup.LayoutParams(-1, 200))
        lateinit var h: Harness
        val sheet = SlideSheet.create(a, a.findViewById(android.R.id.content), content,
            { h.dismissed = true }, false, edge)
        h = Harness(a, content, configure(sheet))
        h.sheet.show()
        shadowOf(Looper.getMainLooper()).idle()
        return h
    }

    private fun swipe(panel: View, dx: Float, dy: Float) {
        panel.dispatchTouchEvent(MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 100f, 100f, 0))
        panel.dispatchTouchEvent(MotionEvent.obtain(0L, 10L, MotionEvent.ACTION_MOVE, 100f + dx * 0.1f, 100f + dy * 0.1f, 0))
        panel.dispatchTouchEvent(MotionEvent.obtain(0L, 20L, MotionEvent.ACTION_MOVE, 100f + dx, 100f + dy, 0))
        panel.dispatchTouchEvent(MotionEvent.obtain(0L, 30L, MotionEvent.ACTION_UP, 100f + dx, 100f + dy, 0))
        shadowOf(Looper.getMainLooper()).idle()
    }

    // ── 底部面板（更新弹窗）：往下滑关闭 ──────────────────────────────────

    @Test
    fun bottomSheet_swipeDown_dismisses() {
        val h = harness(SlideSheet.Edge.BOTTOM)
        swipe(h.content, 0f, 400f)
        assertTrue("底部面板向下滑必须关闭", h.dismissed)
    }

    @Test
    fun bottomSheet_swipeUp_doesNotDismiss() {
        // 它从下面升上来，往上拽没有"关闭"语义——跟了反而像"面板松了"。
        val h = harness(SlideSheet.Edge.BOTTOM)
        swipe(h.content, 0f, -400f)
        assertFalse("底部面板向上滑不得关闭", h.dismissed)
    }

    @Test
    fun bottomSheet_sitsAtBottom() {
        val h = harness(SlideSheet.Edge.BOTTOM)
        val params = h.content.layoutParams
        assertTrue(
            "底部面板必须贴屏幕底",
            params is FrameLayout.LayoutParams && params.gravity == Gravity.BOTTOM,
        )
    }

    // ── 顶部通知条：往上滑关闭 ────────────────────────────────────────────

    @Test
    fun topSheet_swipeUp_dismisses() {
        val h = harness(SlideSheet.Edge.TOP)
        swipe(h.content, 0f, -400f)
        assertTrue("顶部面板向上滑必须关闭", h.dismissed)
    }

    @Test
    fun topSheet_swipeDown_doesNotDismiss() {
        val h = harness(SlideSheet.Edge.TOP)
        swipe(h.content, 0f, 400f)
        assertFalse("顶部面板向下滑不得关闭", h.dismissed)
    }

    @Test
    fun topSheet_sitsAtTop() {
        val h = harness(SlideSheet.Edge.TOP)
        val params = h.content.layoutParams
        assertTrue(
            "顶部面板必须贴屏幕顶",
            params is FrameLayout.LayoutParams && params.gravity == Gravity.TOP,
        )
    }

    // ── 两个方向都支持左右滑关闭 ──────────────────────────────────────────

    @Test
    fun bothEdges_swipeLeftAndRight_dismiss() {
        val left = harness(SlideSheet.Edge.BOTTOM)
        swipe(left.content, -400f, 0f)
        assertTrue("向左滑必须关闭", left.dismissed)

        val right = harness(SlideSheet.Edge.TOP)
        swipe(right.content, 400f, 0f)
        assertTrue("向右滑必须关闭", right.dismissed)
    }

    // ── 阈值与回位 ────────────────────────────────────────────────────────

    @Test
    fun smallDrag_resetsWithoutDismissing() {
        val h = harness(SlideSheet.Edge.BOTTOM)
        swipe(h.content, 0f, 40f)
        assertFalse("小幅拖动不得关闭", h.dismissed)
        assertEquals("回位后位移必须归零", 0f, h.content.translationY, 0.01f)
    }

    @Test
    fun directionsCanBeDisabledIndividually() {
        val vertical = harness(SlideSheet.Edge.BOTTOM) { it.swipeAway(false) }
        swipe(vertical.content, 0f, 400f)
        assertFalse("关掉纵向后不得关闭（强制更新）", vertical.dismissed)

        val horizontal = harness(SlideSheet.Edge.BOTTOM) { it.swipeHorizontal(false) }
        swipe(horizontal.content, -400f, 0f)
        swipe(horizontal.content, 400f, 0f)
        assertFalse("关掉横向后不得关闭", horizontal.dismissed)
    }

    // ── 遮罩与返回键 ──────────────────────────────────────────────────────

    @Test
    fun scrimTap_respectsTheSwitch() {
        val allowed = harness(SlideSheet.Edge.BOTTOM)
        (allowed.content.parent as ViewGroup).getChildAt(0).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue("默认点遮罩应关闭", allowed.dismissed)

        val blocked = harness(SlideSheet.Edge.BOTTOM) { it.dismissOnScrimTap(false) }
        (blocked.content.parent as ViewGroup).getChildAt(0).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse("关掉后点遮罩不得关闭（强制更新）", blocked.dismissed)
    }

    @Test
    fun handleBack_consumesAndClosesOrKeeps() {
        val closable = harness(SlideSheet.Edge.BOTTOM)
        assertTrue(closable.sheet.handleBack())
        assertTrue("允许时返回键应关闭", closable.dismissed)

        val forced = harness(SlideSheet.Edge.BOTTOM) { it.dismissOnBack(false) }
        assertTrue("强制更新：返回键必须被消费掉，不能穿透", forced.sheet.handleBack())
        assertFalse("但不得关闭", forced.dismissed)
    }

    // ── 生命周期 ──────────────────────────────────────────────────────────

    @Test
    fun dismiss_isIdempotent_andRemovesOverlay() {
        val h = harness(SlideSheet.Edge.BOTTOM)
        val overlay = h.content.parent as ViewGroup
        h.sheet.dismiss()
        h.sheet.dismiss()
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(h.sheet.isShowing())
        assertNull("遮罩必须移除，否则留下一个吃掉所有触摸的透明层", overlay.parent)
    }

    @Test
    fun showTwice_doesNotStack() {
        val h = harness(SlideSheet.Edge.BOTTOM)
        val first = h.content.parent
        h.sheet.show()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("重复 show 必须复用同一个遮罩", first, h.content.parent)
    }
}
