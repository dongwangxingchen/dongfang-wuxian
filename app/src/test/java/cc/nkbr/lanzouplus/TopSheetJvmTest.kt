package cc.nkbr.lanzouplus

import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
 * [DFWX] DFW-65：顶部面板的手势与生命周期。
 *
 * 用户要求"从上面滑出来，然后往上滑一下、或者往左右滑，可以丝滑地滑出去"。
 * 这里把**三方向可关 + 向下不关 + 强制模式全关**做成真值表——
 * 这类"少关一个方向"的缺陷在真机上很难被发现，但用户一滑就察觉。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class TopSheetJvmTest {

    companion object {
        @BeforeClass @JvmStatic fun silenceAutoImport() { MainActivity.LIBRARY_AUTO_IMPORT = false }
    }

    private fun activity(): MainActivity {
        val a = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        a.suppressUiMotion = true
        shadowOf(Looper.getMainLooper()).idle()
        return a
    }

    private class Harness(val a: MainActivity, val content: LinearLayout, val sheet: TopSheet) {
        var dismissed = false
    }

    private fun harness(configure: (TopSheet) -> TopSheet = { it }): Harness {
        val a = activity()
        val content = LinearLayout(a)
        content.orientation = LinearLayout.VERTICAL
        content.addView(TextView(a).apply { text = "面板内容" }, ViewGroup.LayoutParams(-1, 200))
        lateinit var h: Harness
        val sheet = TopSheet.create(a, a.findViewById(android.R.id.content), content, { h.dismissed = true }, false)
        h = Harness(a, content, configure(sheet))
        h.sheet.show()
        shadowOf(Looper.getMainLooper()).idle()
        return h
    }

    /** 在面板上模拟一次滑动。MotionEvent.obtain 会把 rawX/rawY 一并设为给定值。 */
    private fun swipe(panel: View, dx: Float, dy: Float) {
        val down = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 100f, 100f, 0)
        panel.dispatchTouchEvent(down)
        // 先来一小步越过 5dp 方向判定阈值，再走完全程
        val probe = MotionEvent.obtain(0L, 10L, MotionEvent.ACTION_MOVE, 100f + dx * 0.1f, 100f + dy * 0.1f, 0)
        panel.dispatchTouchEvent(probe)
        val move = MotionEvent.obtain(0L, 20L, MotionEvent.ACTION_MOVE, 100f + dx, 100f + dy, 0)
        panel.dispatchTouchEvent(move)
        val up = MotionEvent.obtain(0L, 30L, MotionEvent.ACTION_UP, 100f + dx, 100f + dy, 0)
        panel.dispatchTouchEvent(up)
        shadowOf(Looper.getMainLooper()).idle()
    }

    // ── 三个方向都能滑走（用户要求）────────────────────────────────────────

    @Test
    fun swipeUp_dismisses() {
        val h = harness()
        swipe(h.content, 0f, -400f)
        assertTrue("向上滑超过阈值必须关闭", h.dismissed)
    }

    @Test
    fun swipeLeft_dismisses() {
        val h = harness()
        swipe(h.content, -400f, 0f)
        assertTrue("向左滑超过阈值必须关闭", h.dismissed)
    }

    @Test
    fun swipeRight_dismisses() {
        val h = harness()
        swipe(h.content, 400f, 0f)
        assertTrue("向右滑超过阈值必须关闭", h.dismissed)
    }

    // ── 不该关的情况 ──────────────────────────────────────────────────────

    @Test
    fun swipeDown_doesNotDismiss() {
        // 面板已经在屏幕顶部，往下拽没有"关闭"语义。若这里也关，用户轻轻下拉就会误关。
        val h = harness()
        swipe(h.content, 0f, 400f)
        assertFalse("向下滑不得关闭", h.dismissed)
    }

    @Test
    fun smallDrag_resetsWithoutDismissing() {
        // 没到阈值就松手 → 回位，不关。这是"跟手但不误关"的关键。
        val h = harness()
        swipe(h.content, 0f, -40f)
        assertFalse("小幅拖动不得关闭", h.dismissed)
        assertEquals("回位后位移必须归零", 0f, h.content.translationY, 0.01f)
    }

    @Test
    fun verticalDismissDisabled_ignoresUpwardSwipe() {
        // 强制更新场景：三个方向都要能单独关掉。
        val h = harness { it.swipeUp(false) }
        swipe(h.content, 0f, -400f)
        assertFalse("关掉上滑后，向上滑不得关闭", h.dismissed)
    }

    @Test
    fun horizontalDismissDisabled_ignoresLeftAndRight() {
        val h = harness { it.swipeHorizontal(false) }
        swipe(h.content, -400f, 0f)
        swipe(h.content, 400f, 0f)
        assertFalse("关掉横向后，左右滑都不得关闭", h.dismissed)
    }

    // ── 遮罩与返回键 ──────────────────────────────────────────────────────

    @Test
    fun scrimTap_dismissesByDefault() {
        val h = harness()
        // 遮罩是 overlay 的第一个子 view（在面板之下）
        val overlay = h.content.parent as ViewGroup
        val scrim = overlay.getChildAt(0)
        scrim.performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue("默认点遮罩应关闭", h.dismissed)
    }

    @Test
    fun scrimTap_doesNothingWhenDisabled() {
        val h = harness { it.dismissOnScrimTap(false) }
        val overlay = h.content.parent as ViewGroup
        overlay.getChildAt(0).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse("关掉后点遮罩不得关闭（强制更新）", h.dismissed)
    }

    @Test
    fun handleBack_closesWhenAllowed() {
        val h = harness()
        assertTrue("返回键应被消费", h.sheet.handleBack())
        assertTrue("允许时返回键应关闭面板", h.dismissed)
    }

    @Test
    fun handleBack_consumesButKeepsSheetWhenDisabled() {
        // 强制更新：返回键必须被吃掉（不能穿透去关页面/退 App），但面板不能关。
        val h = harness { it.dismissOnBack(false) }
        assertTrue("返回键必须被消费掉，不能穿透", h.sheet.handleBack())
        assertFalse("但强制更新不得关闭", h.dismissed)
    }

    // ── 生命周期 ──────────────────────────────────────────────────────────

    @Test
    fun dismiss_isIdempotent_andRemovesOverlay() {
        val h = harness()
        // 注意：面板是**遮罩的子 view**，所以移除遮罩后 content.parent 仍是遮罩。
        // 要验的是遮罩本身已脱离窗口——留着一个透明全屏层会吃掉所有触摸。
        val overlay = h.content.parent as ViewGroup
        h.sheet.dismiss()
        h.sheet.dismiss()
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse("关闭后不得仍标记为显示中", h.sheet.isShowing())
        assertNull("遮罩必须从容器里移除，否则会留下一个吃掉所有触摸的透明层", overlay.parent)
    }

    @Test
    fun showTwice_doesNotStackTwoOverlays() {
        val h = harness()
        val firstParent = h.content.parent
        h.sheet.show()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("重复 show 必须复用同一个遮罩，不能叠加", firstParent, h.content.parent)
    }

    @Test
    fun panel_isAtTopOfScreen() {
        val h = harness()
        val params = h.content.layoutParams
        assertTrue("面板必须贴顶部（从上方滑入）", params is android.widget.FrameLayout.LayoutParams &&
            (params as android.widget.FrameLayout.LayoutParams).gravity == android.view.Gravity.TOP)
    }
}
