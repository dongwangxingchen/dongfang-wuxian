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
 * [DFWX] DFW-61 / DFW-70：公告中心的**界面与接线验收**。
 *
 * DFW-70 的变化（用户 2026-09-30 要求）：
 * - 公告入口从首页铃铛**挪到悬浮球菜单第 6 项**（放在「设置」下面）。
 *   原因：首页铃铛位置不对，而且**关掉公告它就消失**（只在有未读时出现），用户以为功能没了。
 * - 现在是**常驻可见 + 有未读亮红点**。
 * - 三档模式：静默 / 一次性 / 永久。
 *
 * 策略层（未读/排序/垃圾回收/三模式）在 `NoticeCenterJvmTest` 测；这里验**接线**。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class NoticeCenterUiJvmTest {

    companion object {
        @BeforeClass @JvmStatic fun silenceAutoImport() { MainActivity.LIBRARY_AUTO_IMPORT = false }
    }

    private fun notice(
        id: String,
        title: String = "公告",
        mode: String = RemoteConfigClient.Notice.MODE_ONCE,
        pinned: Boolean = false,
        createdMs: Long = 0L,
    ) = RemoteConfigClient.Notice(id, title, "正文内容", "normal", pinned, mode, createdMs)

    private fun snapshot(vararg notices: RemoteConfigClient.Notice) =
        RemoteConfigClient.Snapshot(true, true, RemoteConfigClient.Control.normal(), null, notices.toList(), emptyList())

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

    // ── 入口：挪进悬浮球菜单 ──────────────────────────────────────────────

    @Test
    fun navBall_hasSixItems_includingNoticeBelowSettings() {
        val a = activity()
        assertNotNull("悬浮球必须已安装", a.navBall)
        assertEquals("菜单应有 6 项（新增「公告」）", 6, a.navBall!!.itemCount())
        assertTrue("菜单必须含「公告」项", a.navBall!!.hasNoticeItem())
    }

    @Test
    fun settingsPage_stillHasNoticeEntry() {
        // 设置页入口保留（两处都能进，不多余）。
        val a = activity()
        a.showSettings()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue("设置页仍应有「公告」入口：${textsIn(a.root)}", textsIn(a.root).contains("公告"))
    }

    @Test
    fun homeNoLongerHasBell() {
        // 用户明确要求把首页那个放错位置、关掉就消失的铃铛移走。
        val a = activity()
        assertTrue("首页不该再有铃铛行", a.noticeBellRowGoneForTest())
    }

    // ── 红点 ──────────────────────────────────────────────────────────────

    @Test
    fun noticeItem_hasNoDotWhenNothingUnread() {
        val a = activity()
        a.noticeSnapshot = snapshot()
        a.refreshNoticeBell()
        shadowOf(Looper.getMainLooper()).idle()
        a.navBall!!.refreshNoticeBadge()
        assertFalse("没有未读时不该有红点", a.navBall!!.noticeDotVisible())
    }

    @Test
    fun noticeItem_showsDotWhenUnread() {
        val a = activity()
        a.noticeSnapshot = snapshot(notice("a"), notice("b"))
        a.refreshNoticeBell()
        shadowOf(Looper.getMainLooper()).idle()
        a.navBall!!.refreshNoticeBadge()
        assertTrue("有未读时必须在「公告」项上亮红点", a.navBall!!.noticeDotVisible())
    }

    @Test
    fun dot_disappearsAfterReading() {
        // 红点不能永久亮着——看过就该灭。
        val a = activity()
        a.noticeSnapshot = snapshot(notice("a"))
        a.refreshNoticeBell()
        a.navBall!!.refreshNoticeBadge()
        assertTrue(a.navBall!!.noticeDotVisible())

        a.showNoticeCenter()
        shadowOf(Looper.getMainLooper()).idle()
        a.navBall!!.refreshNoticeBadge()
        assertFalse("打开公告后红点必须消失", a.navBall!!.noticeDotVisible())
    }

    @Test
    fun dot_isStillThereForSilentNotices() {
        // 静默模式：不弹窗，但**红点必须有**——否则用户完全无从知道有新公告。
        val a = activity()
        a.noticeSnapshot = snapshot(notice("s", mode = RemoteConfigClient.Notice.MODE_SILENT))
        a.refreshNoticeBell()
        a.navBall!!.refreshNoticeBadge()
        assertTrue("静默公告也要亮红点（它只是不弹窗）", a.navBall!!.noticeDotVisible())
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
        assertTrue("置顶必须排在前面", labels.indexOf("置顶公告") < labels.indexOf("普通公告"))
    }

    @Test
    fun noticeCenter_showsEmptyStateWhenNoNotices() {
        val a = activity()
        a.noticeSnapshot = snapshot()
        a.showNoticeCenter()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue("没有公告要给空态，不能一片空白", textsIn(a.root).contains("暂无公告"))
    }

    @Test
    fun noticeCenter_showsLevelTags_onlyForImportantAndUrgent() {
        val a = activity()
        a.noticeSnapshot = snapshot(
            RemoteConfigClient.Notice("n1", "甲公告", "正文", "normal", false, RemoteConfigClient.Notice.MODE_ONCE, 10),
            RemoteConfigClient.Notice("n2", "乙公告", "正文", "important", false, RemoteConfigClient.Notice.MODE_ONCE, 20),
            RemoteConfigClient.Notice("n3", "丙公告", "正文", "urgent", false, RemoteConfigClient.Notice.MODE_ONCE, 30),
        )
        a.showNoticeCenter()
        shadowOf(Looper.getMainLooper()).idle()
        val labels = textsIn(a.root)
        // 标题刻意不叫"重要/紧急"，否则标题会和等级标签重名，把计数弄成 2（本测试第一版就踩了）。
        assertTrue("重要公告要挂「重要」标签", labels.contains("重要"))
        assertTrue("紧急公告要挂「紧急」标签", labels.contains("紧急"))
        assertEquals("只有重要/紧急挂标签，普通不挂", 1, labels.count { it == "重要" })
        assertEquals("只有重要/紧急挂标签，普通不挂", 1, labels.count { it == "紧急" })
    }

    @Test
    fun newNoticeAfterReading_lightsDotAgain() {
        val a = activity()
        a.noticeSnapshot = snapshot(notice("a"))
        a.refreshNoticeBell()
        a.showNoticeCenter()
        shadowOf(Looper.getMainLooper()).idle()
        a.navBall!!.refreshNoticeBadge()
        assertFalse("读过之后红点应消失", a.navBall!!.noticeDotVisible())

        a.showHomeLanding()
        a.noticeSnapshot = snapshot(notice("a"), notice("new"))
        a.refreshNoticeBell()
        a.navBall!!.refreshNoticeBadge()
        assertTrue("后台再发一条必须重新亮红点", a.navBall!!.noticeDotVisible())
    }

    // ── 三档弹出模式（与策略层对齐）──────────────────────────────────────

    @Test
    fun silentMode_neverPops() {
        val a = activity()
        val s = snapshot(notice("s", mode = RemoteConfigClient.Notice.MODE_SILENT))
        assertTrue("静默模式不得进弹窗候选", a.noticeCenter().popupNotices(s).isEmpty())
    }

    @Test
    fun onceMode_popsOnlyWhenUnread() {
        val a = activity()
        val s = snapshot(notice("o", mode = RemoteConfigClient.Notice.MODE_ONCE))
        assertEquals("未读时应弹", 1, a.noticeCenter().popupNotices(s).size)
        a.noticeCenter().markRead("o")
        assertTrue("读过之后不再弹", a.noticeCenter().popupNotices(s).isEmpty())
    }

    @Test
    fun alwaysMode_popsEveryTime_evenAfterReading() {
        // 用户要的"永久弹出"：每次打开软件都弹，无视已读。
        val a = activity()
        val s = snapshot(notice("p", mode = RemoteConfigClient.Notice.MODE_ALWAYS))
        assertEquals("首次应弹", 1, a.noticeCenter().popupNotices(s).size)
        a.noticeCenter().markRead("p")
        assertEquals("读过之后**仍然**要弹（这正是「永久」的含义）", 1, a.noticeCenter().popupNotices(s).size)
    }

    @Test
    fun noticeItem_isNotVisibleInMenuBeforeAnyFetch() {
        val a = activity()
        a.noticeSnapshot = null
        a.refreshNoticeBell()
        a.navBall!!.refreshNoticeBadge()
        assertFalse("还没拉到公告时不该有红点", a.navBall!!.noticeDotVisible())
    }
}
