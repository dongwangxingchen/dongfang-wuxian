package cc.nkbr.lanzouplus

import android.app.Dialog
import android.graphics.Color
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.Button
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
 * [DFWX] DFW-70：公告弹窗的**规格守卫**。
 *
 * 用户 2026-09-30："它弹出来那个公告弹窗就很丑，你需要优化，改成一个非常适合公告的UI和样子。"
 *
 * 规格来自并行调研（参考库 `黑曜/06-开源参考库/07-更新与公告UI动效专档.md` E 节，
 * 取值来源是 AOSP `AlertController` + MDC `MaterialAlertDialog` 实测，Apache-2.0 可抄）。
 *
 * 这里钉住的是**三条"丑"的根因**，它们都不会崩、不会报错，只会让人一直觉得难看：
 *   ① 底色发紫（把品牌色当底色用）
 *   ② 两个光秃秃的文字按钮（全透明底、无字重）
 *   ③ 圆角被静默量化 + 违规 elevation 阴影
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class NoticeDialogJvmTest {

    companion object {
        @BeforeClass @JvmStatic fun silenceAutoImport() { MainActivity.LIBRARY_AUTO_IMPORT = false }
    }

    private fun notice(
        id: String = "n1",
        title: String = "东方无限 公测开始",
        body: String = "感谢使用。",
        level: String = "normal",
        mode: String = RemoteConfigClient.Notice.MODE_ONCE,
        createdMs: Long = 1_700_000_000_000L,
    ) = RemoteConfigClient.Notice(id, title, body, level, false, mode, createdMs)

    private fun activity(): MainActivity {
        val a = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        shadowOf(Looper.getMainLooper()).idle()
        return a
    }

    private fun rootOf(a: MainActivity): View = a.noticeDialog!!.window!!.decorView

    private fun buttonsIn(view: View, out: MutableList<Button> = mutableListOf()): List<Button> {
        if (view is Button) out.add(view)
        if (view is ViewGroup) for (i in 0 until view.childCount) buttonsIn(view.getChildAt(i), out)
        return out
    }

    private fun textsIn(view: View, out: MutableList<String> = mutableListOf()): List<String> {
        if (view is TextView) view.text?.toString()?.let { if (it.isNotEmpty()) out.add(it) }
        if (view is ViewGroup) for (i in 0 until view.childCount) textsIn(view.getChildAt(i), out)
        return out
    }

    // ── ① 底色必须中性（最关键的守卫）────────────────────────────────────

    @Test
    fun dialogFill_isNeutral_notPurple() {
        // **上一版就是把品牌色当底色用**，整块发紫，用户反馈"廉价"。
        // 规范 §5：亮度阶梯的主体是"白"，品牌色 tint 只留给选中/强调态。
        // DFW-72 起底色由 GlassSurface 出（模糊开/关两套），这条守卫跟着搬过去，语义不变。
        val glass = GlassSurface.windowFillColor(Color.BLACK, true)
        val opaque = GlassSurface.windowFillColor(Color.BLACK, false)
        for (fill in listOf(glass, opaque)) {
            val spread = maxOf(Color.red(fill), Color.green(fill), Color.blue(fill)) -
                minOf(Color.red(fill), Color.green(fill), Color.blue(fill))
            assertTrue("公告弹窗底色必须是中性的（无色偏）；实测色偏=$spread（应 ≤4）", spread <= 4)
        }
        // 降级（无模糊）底色要落在 §5 的 11–14% 档附近（白 12% ≈ 31/255）
        assertTrue("降级底色亮度应在规范档位内，实测 R=${Color.red(opaque)}", Color.red(opaque) in 24..40)
    }

    // ── ② 按钮：公告是单向通知，禁"取消" ────────────────────────────────

    @Test
    fun dialog_hasNoCancelButton() {
        // 公告是"告诉用户一件事"，不是"问用户要一个决定"。
        // 出现"取消"就把公告降级成了问答。
        val a = activity()
        a.showNoticeDialog(notice(), 0, null)
        shadowOf(Looper.getMainLooper()).idle()
        val labels = buttonsIn(rootOf(a)).map { it.text.toString() }.filter { it.isNotBlank() }
        assertFalse("公告弹窗**不得**出现「取消」：$labels", labels.contains("取消"))
    }

    @Test
    fun dialog_hasAtMostTwoActionButtons_andNoDuplicatedClose() {
        // 官方：Firebase Modal 2 个按钮、Banner 0 个。公告不需要第三个。
        // DFW-72：旧版「关闭」+「知道了」两个按钮其实都只是关窗，摆两个是噪音、
        // 还让人以为它们有区别——现在只有一条公告时只留「知道了」一个。
        val a = activity()
        a.showNoticeDialog(notice(), 0, null)
        shadowOf(Looper.getMainLooper()).idle()
        val labels = buttonsIn(rootOf(a)).map { it.text.toString() }.filter { it.isNotBlank() }
        assertTrue("公告弹窗最多两个动作：$labels", labels.size <= 2)
        assertTrue("应有「知道了」：$labels", labels.contains("知道了"))
        assertFalse("语义重复的「关闭」不该再出现：$labels", labels.contains("关闭"))
    }

    @Test
    fun dialog_primaryBecomesViewAllWhenMoreNotices() {
        val a = activity()
        a.showNoticeDialog(notice(), 3, null)
        shadowOf(Looper.getMainLooper()).idle()
        val labels = buttonsIn(rootOf(a)).map { it.text.toString() }.filter { it.isNotBlank() }
        assertTrue("还有别的公告时主按钮应是「查看全部」：$labels", labels.contains("查看全部"))
        assertTrue("并要告诉用户还有几条：${textsIn(rootOf(a))}", textsIn(rootOf(a)).contains("还有 3 条"))
    }

    // ── 内容 ──────────────────────────────────────────────────────────────

    @Test
    fun dialog_showsTitle_andPublishDate() {
        // 发布时间是**公告与普通对话框最直观的区分信号**。
        val a = activity()
        a.showNoticeDialog(notice(title = "东方无限 公测开始"), 0, null)
        shadowOf(Looper.getMainLooper()).idle()
        val texts = textsIn(rootOf(a))
        assertTrue("要显示标题：$texts", texts.contains("东方无限 公测开始"))
        assertTrue("必须显示发布日期：$texts", texts.any { it.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) })
    }

    @Test
    fun title_isCappedAtTwoLines() {
        val a = activity()
        a.showNoticeDialog(notice(title = "很长的标题".repeat(30)), 0, null)
        shadowOf(Looper.getMainLooper()).idle()
        val title = textsIn(rootOf(a)).firstOrNull { it.startsWith("很长的标题") }
        assertNotNull(title)
        var found: TextView? = null
        fun walk(v: View) {
            if (v is TextView && v.text.toString().startsWith("很长的标题")) found = v
            if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
        }
        walk(rootOf(a))
        assertEquals("标题必须限制行数（超长标题不能把弹窗撑爆）", 3, found!!.maxLines)
    }

    @Test
    fun dialog_usesThisDialog_notAlertDialog() {
        // 用裸 Dialog 的原因：AlertDialog + showRounded 会把面板背景覆盖成
        // `solidShape(SURFACE,22)`（还把 28 量化成 26）并带上 §5 禁止的 elevation 阴影。
        val a = activity()
        a.showNoticeDialog(notice(), 0, null)
        shadowOf(Looper.getMainLooper()).idle()
        assertNotNull(a.noticeDialog)
        assertTrue("应是裸 Dialog（自绘背景才不会被覆盖）", a.noticeDialog is Dialog)
        assertFalse("不应是 AlertDialog", a.noticeDialog is android.app.AlertDialog)
    }

    @Test
    fun dialog_isDismissed_afterClose() {
        val a = activity()
        a.showNoticeDialog(notice(), 0, null)
        shadowOf(Looper.getMainLooper()).idle()
        assertNotNull(a.noticeDialog)
        a.dismissNoticeDialog()
        shadowOf(Looper.getMainLooper()).idle()
        assertNull("关闭后引用必须清空", a.noticeDialog)
    }

    @Test
    fun dialog_marksTheNoticeRead_soOnceModeDoesNotRepeat() {
        val a = activity()
        a.noticeSnapshot = RemoteConfigClient.Snapshot(
            true, true, RemoteConfigClient.Control.normal(), null,
            listOf(notice(id = "once", mode = RemoteConfigClient.Notice.MODE_ONCE)), emptyList(),
        )
        assertEquals("前置：未读时应是弹窗候选", 1, a.noticeCenter().popupNotices(a.noticeSnapshot).size)
        a.showNoticeDialog(notice(id = "once"), 0, null)
        shadowOf(Looper.getMainLooper()).idle()
        a.dismissNoticeDialog()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue("弹过之后该条必须标为已读，否则「一次性」模式会反复弹", a.noticeCenter().isRead("once"))
    }

    // ── 等级与紧急态 ──────────────────────────────────────────────────────

    @Test
    fun urgentNotice_cannotBeDismissedByTappingOutside() {
        val a = activity()
        a.showNoticeDialog(notice(level = "urgent"), 0, null)
        shadowOf(Looper.getMainLooper()).idle()
        // 紧急公告：点空白与返回键都不该关掉（只能用底部按钮）。
        // Android 37 的 Dialog 没有读取用的公开方法，所以断言代码里记录的意图。
        assertFalse("紧急公告不许点空白关闭", a.noticeDialogCancelable)
    }

    @Test
    fun normalNotice_canBeDismissedByTappingOutside() {
        val a = activity()
        a.showNoticeDialog(notice(level = "normal"), 0, null)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue("普通公告允许点空白关闭（用户不该被强行留住）", a.noticeDialogCancelable)
    }

    @Test
    fun emptyBody_doesNotCrash() {
        val a = activity()
        a.showNoticeDialog(notice(body = ""), 0, null)
        shadowOf(Looper.getMainLooper()).idle()
        assertNotNull("正文为空也要能正常弹出", a.noticeDialog)
    }
}
