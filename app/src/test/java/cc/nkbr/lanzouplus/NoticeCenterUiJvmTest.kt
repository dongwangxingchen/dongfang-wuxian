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
 * [DFWX] DFW-61：公告中心与未读红点的**界面验收**。
 *
 * 策略层（未读/排序/垃圾回收）已在 `NoticeCenterJvmTest` 测过；这里验的是**接线**：
 * 入口在不在、红点该亮时亮不该亮时灭、打开公告后红点是否消掉。
 * 策略对了但界面忘了调它，用户照样看不到公告或红点永远不消。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class NoticeCenterUiJvmTest {

    companion object {
        @BeforeClass @JvmStatic fun silenceAutoImport() { MainActivity.LIBRARY_AUTO_IMPORT = false }
    }

    private fun notice(id: String, title: String = "公告", pinned: Boolean = false, popup: Boolean = false, createdMs: Long = 0L) =
        RemoteConfigClient.Notice(id, title, "正文内容", "normal", pinned, popup, createdMs)

    private fun snapshot(vararg notices: RemoteConfigClient.Notice) =
        RemoteConfigClient.Snapshot(true, RemoteConfigClient.Control.normal(), null, notices.toList(), emptyList())

    private fun activity(): MainActivity {
        val a = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        shadowOf(Looper.getMainLooper()).idle()
        a.showHomeLanding()
        shadowOf(Looper.getMainLooper()).idle()
        return a
    }

    private fun textsIn(view: View, out: MutableList<String> = mutableListOf()): List<String> {
        if (view is TextView) view.text?.toString()?.let { if (it.isNotEmpty()) out.add(it) }
        if (view is ViewGroup) for (i in 0 until view.childCount) textsIn(view.getChildAt(i), out)
        return out
    }

    // ── 入口 ──────────────────────────────────────────────────────────────

    @Test
    fun settingsPage_hasNoticeEntry() {
        val a = activity()
        a.showSettings()
        shadowOf(Looper.getMainLooper()).idle()
        val labels = textsIn(a.root)
        assertTrue("设置页必须有「公告」入口，否则用户找不到公告中心：$labels", labels.contains("公告"))
    }

    @Test
    fun homeHasBellRow() {
        val a = activity()
        assertNotNull("首页必须挂铃铛行（用户要求首页右上角铃铛）", a.noticeBellRow)
    }

    // ── 红点该亮时亮、不该亮时灭 ──────────────────────────────────────────

    @Test
    fun bellRow_isHiddenWhenNoUnread() {
        val a = activity()
        a.noticeSnapshot = snapshot()
        a.refreshNoticeBell()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(
            "没有未读时整行必须隐藏（用户要求：有未读才出现，常驻空铃铛是视觉噪音）",
            View.GONE, a.noticeBellRow!!.visibility,
        )
        assertNotNull(a.noticeBadge)
        assertEquals("没有未读时角标数字应为 0", 0, a.noticeBadge!!.count())
    }

    @Test
    fun bellRow_appearsWhenUnread_andShowsCount() {
        val a = activity()
        a.noticeSnapshot = snapshot(notice("a"), notice("b"), notice("c"))
        a.refreshNoticeBell()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals("有未读时整行必须出现", View.VISIBLE, a.noticeBellRow!!.visibility)
        assertEquals("红点数字要等于未读数", 3, a.noticeBadge!!.count())
        assertEquals("红点文本", "3", a.noticeBadge!!.text.toString())
    }

    @Test
    fun badgeText_capsAt99Plus() {
        val a = activity()
        val many = (1..120).map { notice("n$it") }.toTypedArray()
        a.noticeSnapshot = snapshot(*many)
        a.refreshNoticeBell()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("超过 99 必须显示 99+，否则三位数会把圆点撑成一条", "99+", a.noticeBadge!!.text.toString())
    }

    // ── 公告列表页 ────────────────────────────────────────────────────────

    @Test
    fun noticeCenter_rendersAllNotices_pinnedFirst() {
        val a = activity()
        a.noticeSnapshot = snapshot(
            notice("a", "普通公告", createdMs = 100),
            notice("b", "置顶公告", pinned = true, createdMs = 50),
        )
        a.showNoticeCenter()
        shadowOf(Looper.getMainLooper()).idle()

        val labels = textsIn(a.root)
        assertTrue("要显示普通公告：$labels", labels.contains("普通公告"))
        assertTrue("要显示置顶公告：$labels", labels.contains("置顶公告"))
        assertTrue("置顶要有标识：$labels", labels.contains("置顶"))
        // 置顶必须排在前面
        val pinnedIndex = labels.indexOf("置顶公告")
        val normalIndex = labels.indexOf("普通公告")
        assertTrue("置顶必须排在普通公告之前（$pinnedIndex vs $normalIndex）", pinnedIndex < normalIndex)
    }

    @Test
    fun noticeCenter_showsEmptyStateWhenNoNotices() {
        val a = activity()
        a.noticeSnapshot = snapshot()
        a.showNoticeCenter()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue("没有公告时要给空态，不能是一片空白：${textsIn(a.root)}", textsIn(a.root).contains("暂无公告"))
    }

    @Test
    fun openingNoticeCenter_clearsUnread_andHidesBell() {
        // 用户"随时可以看"，看完就不该再亮红点——否则红点永远消不掉。
        val a = activity()
        a.noticeSnapshot = snapshot(notice("a"), notice("b"))
        a.refreshNoticeBell()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(2, a.noticeBadge!!.count())

        a.showNoticeCenter()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("打开公告后未读必须清零", 0, a.noticeBadge!!.count())
        assertEquals("红点必须消失", View.GONE, a.noticeBellRow!!.visibility)
    }

    @Test
    fun readState_survivesReturningHome() {
        // 回到首页时不能"红点又冒出来"——那是用户最反感的观感 bug 之一。
        val a = activity()
        a.noticeSnapshot = snapshot(notice("a"))
        a.refreshNoticeBell()
        a.showNoticeCenter()
        shadowOf(Looper.getMainLooper()).idle()

        a.showHomeLanding()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("返回首页后红点不得复活", View.GONE, a.noticeBellRow!!.visibility)
    }

    @Test
    fun newNoticeAfterReading_lightsUpAgain() {
        // "发布式公告"的核心：读过老的之后，后台再发一条必须重新亮红点。
        val a = activity()
        a.noticeSnapshot = snapshot(notice("a"))
        a.refreshNoticeBell()
        a.showNoticeCenter()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(0, a.noticeBadge!!.count())

        a.showHomeLanding()
        a.noticeSnapshot = snapshot(notice("a"), notice("new"))
        a.refreshNoticeBell()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("新公告必须重新亮红点", 1, a.noticeBadge!!.count())
        assertEquals(View.VISIBLE, a.noticeBellRow!!.visibility)
    }

    // ── 弹窗开关 ──────────────────────────────────────────────────────────

    @Test
    fun popupOnlyForNoticesFlaggedByBackend() {
        // 后台的 popup 开关是唯一决定因素：没勾的只进红点，不该打断用户。
        val a = activity()
        val s = snapshot(notice("plain", "普通", popup = false), notice("loud", "要弹的", popup = true))
        a.noticeSnapshot = s
        val popups = a.noticeCenter().popupNotices(s)
        assertEquals("只有勾了 popup 的才弹", listOf("loud"), popups.map { it.id })
    }

    @Test
    fun badgeView_isNotVisibleBeforeAnyFetch() {
        // 还没拉到公告时不该显示红点（否则用户会看到一个无意义的 0 或空点）。
        val a = activity()
        a.noticeSnapshot = null
        a.refreshNoticeBell()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(View.GONE, a.noticeBellRow!!.visibility)
        assertEquals(0, a.noticeBadge!!.count())
    }

    @Test
    fun noticeCenter_showsLevelTags_onlyForImportantAndUrgent() {
        // 卡要求等级色（普通/重要/紧急）。普通**不挂标签**——每条都挂等于没有重点。
        val a = activity()
        a.noticeSnapshot = snapshot(
            RemoteConfigClient.Notice("n1", "普通公告", "正文", "normal", false, false, 10),
            RemoteConfigClient.Notice("n2", "重要公告", "正文", "important", false, false, 20),
            RemoteConfigClient.Notice("n3", "紧急公告", "正文", "urgent", false, false, 30),
        )
        a.showNoticeCenter()
        shadowOf(Looper.getMainLooper()).idle()

        val labels = textsIn(a.root)
        assertTrue("重要公告要有「重要」标签：$labels", labels.contains("重要"))
        assertTrue("紧急公告要有「紧急」标签：$labels", labels.contains("紧急"))
        // 普通公告不得挂标签：只有两个标签存在
        assertEquals("只有重要/紧急挂标签，普通不挂", 1, labels.count { it == "重要" })
        assertEquals("只有重要/紧急挂标签，普通不挂", 1, labels.count { it == "紧急" })
    }
}
