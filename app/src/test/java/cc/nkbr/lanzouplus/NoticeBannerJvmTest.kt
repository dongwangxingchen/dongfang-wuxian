package cc.nkbr.lanzouplus

import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
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
import java.time.Duration

/**
 * [DFWX] DFW-66：顶部通知条（"像手机收到的消息"）。
 *
 * 用户原话："b 从顶部往下滑出来，像我们手机收到的消息一样。不过适配的好，
 * 如果文字多了也得适配好，少了也得适配好。2~3 秒消失，我也可以直接滑动，
 * 把它往上滑、往左滑、往右滑给滑掉。"
 *
 * 逐条验：从顶部、无遮罩、2~3 秒自动消失、三方向可滑掉、向下不关、文字多寡都适配。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class NoticeBannerJvmTest {

    companion object {
        @BeforeClass @JvmStatic fun silenceAutoImport() { MainActivity.LIBRARY_AUTO_IMPORT = false }
    }

    private fun activity(): MainActivity {
        val a = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        a.suppressUiMotion = true
        shadowOf(Looper.getMainLooper()).idle()
        return a
    }

    private class Harness(val a: MainActivity, val card: LinearLayout, val banner: NoticeBanner) {
        var dismissed = false
    }

    private fun harness(autoDismissMs: Long = 2500L, message: String = "正在检查更新…"): Harness {
        val a = activity()
        val card = LinearLayout(a)
        card.orientation = LinearLayout.HORIZONTAL
        val label = TextView(a).apply {
            text = message
            maxLines = NoticeBanner.maxLines()
            ellipsize = TextUtils.TruncateAt.END
        }
        card.addView(label, ViewGroup.LayoutParams(0, -2))
        lateinit var h: Harness
        val banner = NoticeBanner.create(a, a.findViewById(android.R.id.content), card,
            { h.dismissed = true }, false, 24, autoDismissMs)
        h = Harness(a, card, banner)
        h.banner.show()
        shadowOf(Looper.getMainLooper()).idle()
        return h
    }

    private fun swipe(view: View, dx: Float, dy: Float) {
        view.dispatchTouchEvent(MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 100f, 100f, 0))
        view.dispatchTouchEvent(MotionEvent.obtain(0L, 10L, MotionEvent.ACTION_MOVE, 100f + dx * 0.1f, 100f + dy * 0.1f, 0))
        view.dispatchTouchEvent(MotionEvent.obtain(0L, 20L, MotionEvent.ACTION_MOVE, 100f + dx, 100f + dy, 0))
        view.dispatchTouchEvent(MotionEvent.obtain(0L, 30L, MotionEvent.ACTION_UP, 100f + dx, 100f + dy, 0))
        shadowOf(Looper.getMainLooper()).idle()
    }

    // ── 形态：从顶部、无遮罩 ──────────────────────────────────────────────

    @Test
    fun banner_sitsAtTheTop() {
        val h = harness()
        val params = h.card.layoutParams
        assertTrue(
            "通知条必须贴屏幕顶（从上面滑下来）",
            params is FrameLayout.LayoutParams && params.gravity == Gravity.TOP,
        )
    }

    @Test
    fun banner_hasNoScrim_soItDoesNotDarkenTheScreen() {
        // 通知不是模态的。盖一层灰会让整个界面变暗——那是对话框的做法，不是消息通知。
        val h = harness()
        val overlay = h.card.parent as ViewGroup
        assertEquals("通知条容器里只应有通知本身，不该有遮罩", 1, overlay.childCount)
        assertFalse("容器本身不得可点（否则空白处会吃掉触摸）", overlay.isClickable)
    }

    // ── 自动消失 ──────────────────────────────────────────────────────────

    @Test
    fun banner_autoDismisses_afterTheGivenDelay() {
        val h = harness(autoDismissMs = 2500L)
        assertTrue("刚显示时应在", h.banner.isShowing())
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(2600))
        assertFalse("2~3 秒后必须自动消失", h.banner.isShowing())
        assertTrue("消失后要回调（调用方据此清引用）", h.dismissed)
    }

    @Test
    fun banner_doesNotDismissBeforeTheDelay() {
        val h = harness(autoDismissMs = 2500L)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1000))
        assertTrue("还没到时间不得提前消失", h.banner.isShowing())
    }

    @Test
    fun longLivedNotice_staysLonger() {
        // 沿用 showNotice 的语义：短提示 2500ms、重要提示 5000ms。
        val h = harness(autoDismissMs = 5000L)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(3000))
        assertTrue("重要提示 3 秒时仍应在（否则用户来不及看）", h.banner.isShowing())
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(2200))
        assertFalse("5 秒后应消失", h.banner.isShowing())
    }

    // ── 三方向滑掉 ────────────────────────────────────────────────────────

    @Test
    fun swipeUp_dismisses() {
        val h = harness(autoDismissMs = 0L)
        swipe(h.card, 0f, -400f)
        assertTrue("向上滑必须滑掉（用户明确要求）", h.dismissed)
    }

    @Test
    fun swipeLeft_dismisses() {
        val h = harness(autoDismissMs = 0L)
        swipe(h.card, -400f, 0f)
        assertTrue("向左滑必须滑掉", h.dismissed)
    }

    @Test
    fun swipeRight_dismisses() {
        val h = harness(autoDismissMs = 0L)
        swipe(h.card, 400f, 0f)
        assertTrue("向右滑必须滑掉", h.dismissed)
    }

    @Test
    fun swipeDown_doesNotDismiss() {
        // 它是从上面下来的，往下拽没有"关闭"语义。
        val h = harness(autoDismissMs = 0L)
        swipe(h.card, 0f, 400f)
        assertFalse("向下滑不得关闭", h.dismissed)
    }

    @Test
    fun smallDrag_resetsWithoutDismissing() {
        val h = harness(autoDismissMs = 0L)
        swipe(h.card, 0f, -40f)
        assertFalse("小幅拖动不得关闭", h.dismissed)
        assertEquals("回位后位移归零", 0f, h.card.translationY, 0.01f)
    }

    @Test
    fun touchingBanner_cancelsAutoDismiss_soItIsNotYankedAwayWhileReading() {
        // 用户正在看/正在滑的时候被自动收走会很烦。
        val h = harness(autoDismissMs = 2500L)
        h.card.dispatchTouchEvent(MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 100f, 100f, 0))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(3000))
        assertTrue("用户按住期间不得自动消失", h.banner.isShowing())
        // 松手（未滑走）后重新计时
        h.card.dispatchTouchEvent(MotionEvent.obtain(0L, 20L, MotionEvent.ACTION_UP, 100f, 100f, 0))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(2600))
        assertFalse("松手后重新计时并消失", h.banner.isShowing())
    }

    // ── 文字多寡适配 ──────────────────────────────────────────────────────

    @Test
    fun longText_isCappedAndEllipsized_notUnbounded() {
        // 文字多要适配：最多 4 行、超出省略号；否则超长公告会把通知条撑满整屏。
        val long = "这是一条很长的提示。" + "内容继续。" .repeat(50)
        val h = harness(autoDismissMs = 0L, message = long)
        val label = h.card.getChildAt(0) as TextView
        assertEquals("正文必须限制最大行数", NoticeBanner.maxLines(), label.maxLines)
        assertEquals("超出必须省略，不能无限撑高", TextUtils.TruncateAt.END, label.ellipsize)
    }

    @Test
    fun shortText_usesTheSameLayout() {
        // 文字少也要适配：容器是 wrap_content，一行时自然收成紧凑胶囊，不需要另一套布局。
        val h = harness(autoDismissMs = 0L, message = "已是最新版本")
        val label = h.card.getChildAt(0) as TextView
        assertEquals("已是最新版本", label.text.toString())
        assertTrue("容器必须 wrap_content 才能随文字多寡自动伸缩", h.card.layoutParams.height != 0)
    }

    // ── 生命周期 ──────────────────────────────────────────────────────────

    @Test
    fun dismiss_isIdempotent_andRemovesOverlay() {
        val h = harness(autoDismissMs = 0L)
        val overlay = h.card.parent as ViewGroup
        h.banner.dismiss()
        h.banner.dismiss()
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(h.banner.isShowing())
        assertNull("必须从容器移除，否则留下一层挡住触摸的空壳", overlay.parent)
    }

    // ── 与 MainActivity 的接线 ────────────────────────────────────────────

    @Test
    fun showUpdateNotice_buildsAndShowsTheBanner() {
        val a = activity()
        a.showUpdateNotice("正在检查更新…", false)
        shadowOf(Looper.getMainLooper()).idle()
        assertNotNull("更新链路必须能弹出新通知条", a.updateNoticeBanner)
        assertTrue(a.updateNoticeBanner!!.isShowing())
    }

    @Test
    fun showUpdateNotice_replacesPrevious_soNoticesDoNotStack() {
        // 连点「检查更新」时旧条先撤，避免叠罗汉。
        val a = activity()
        a.showUpdateNotice("正在检查更新…", false)
        shadowOf(Looper.getMainLooper()).idle()
        val first = a.updateNoticeBanner
        a.showUpdateNotice("正在检查更新…", false)
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse("旧条必须已被替换/关闭", first === a.updateNoticeBanner && first!!.isShowing())
    }
}
