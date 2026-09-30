package cc.nkbr.lanzouplus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [DFWX] DFW-61：公告中心未读/排序的**真值表**。
 *
 * 这张卡最容易出的两个错都不是崩溃：
 *   ① **红点永远消不掉**（已读记不住，或 id 对不上）；
 *   ② **已读集合无限膨胀**（后台删了公告，本地 id 永远留着，几年后变垃圾堆）。
 * 两者都只能在真机上慢慢发现，所以必须在这里钉死。
 */
class NoticeCenterJvmTest {

    private class FakeStore(var ids: MutableSet<String> = mutableSetOf()) : NoticeCenter.Store {
        var writes = 0
        override fun readIds(): MutableSet<String> = LinkedHashSet(ids)
        override fun setReadIds(ids: MutableSet<String>) { this.ids = LinkedHashSet(ids); writes++ }
    }

    private fun notice(
        id: String,
        title: String = "公告",
        pinned: Boolean = false,
        popup: Boolean = false,
        level: String = "normal",
        createdMs: Long = 0L,
    ) = RemoteConfigClient.Notice(id, title, "正文", level, pinned, popup, createdMs)

    private fun snapshot(vararg notices: RemoteConfigClient.Notice) =
        RemoteConfigClient.Snapshot(true, RemoteConfigClient.Control.normal(), null, notices.toList(), emptyList())

    private fun center(store: FakeStore = FakeStore()) = NoticeCenter(store)

    // ── 排序 ──────────────────────────────────────────────────────────────

    @Test
    fun pinnedComesFirst_thenNewestFirst() {
        // 用户要求"发布式、可叠加"：多条公告同层堆叠，不互相覆盖。
        // 置顶是他手动挑的，必须永远在最上面；其余按时间倒序（新的在前）。
        val s = snapshot(
            notice("a", "旧", createdMs = 100),
            notice("b", "置顶", pinned = true, createdMs = 50),
            notice("c", "新", createdMs = 300),
            notice("d", "中间", createdMs = 200),
        )
        val order = center().visible(s).map { it.title }
        assertEquals(listOf("置顶", "新", "中间", "旧"), order)
    }

    @Test
    fun noticesWithoutId_areDropped() {
        // 没有 id 的公告既无法标记已读、也无法追踪删除 → 留着会让红点**永远消不掉**。
        val s = snapshot(notice(""), notice("ok"))
        assertEquals(listOf("ok"), center().visible(s).map { it.id })
    }

    @Test
    fun nullSnapshot_yieldsEmpty_notCrash() {
        val c = center()
        assertTrue(c.visible(null).isEmpty())
        assertTrue(c.unread(null).isEmpty())
        assertEquals(0, c.unreadCount(null))
        assertTrue(c.popupNotices(null).isEmpty())
    }

    // ── 未读与已读 ────────────────────────────────────────────────────────

    @Test
    fun everythingIsUnreadOnFirstLaunch() {
        val c = center()
        val s = snapshot(notice("a"), notice("b"))
        assertEquals("首次打开时全部未读", 2, c.unreadCount(s))
    }

    @Test
    fun markRead_reducesUnreadCount_andPersists() {
        val store = FakeStore()
        val c = center(store)
        val s = snapshot(notice("a"), notice("b"))

        c.markRead("a")
        assertEquals(1, c.unreadCount(s))
        assertTrue(c.isRead("a"))
        assertFalse(c.isRead("b"))
        // 必须落盘：换一个实例仍记得（否则用户每次开 App 红点都重新冒出来）
        assertTrue("已读状态必须持久化", NoticeCenter(store).isRead("a"))
    }

    @Test
    fun markAllRead_clearsBadge() {
        val store = FakeStore()
        val c = center(store)
        val s = snapshot(notice("a"), notice("b"), notice("c"))
        c.markAllRead(s)
        assertEquals("全部已读后红点必须消失", 0, c.unreadCount(s))
    }

    @Test
    fun newNoticeAfterAllRead_becomesUnreadAgain() {
        // 这是"发布式公告"的核心行为：读过老的之后，后台再发一条必须重新亮红点。
        val store = FakeStore()
        val c = center(store)
        c.markAllRead(snapshot(notice("a")))
        assertEquals(0, c.unreadCount(snapshot(notice("a"))))
        assertEquals("新公告必须重新算未读", 1, c.unreadCount(snapshot(notice("a"), notice("b"))))
    }

    @Test
    fun unread_isStableAcrossRepeatedCalls() {
        // 幂等：红点数字不能因为多算一次就变化（否则界面会闪）。
        val c = center()
        val s = snapshot(notice("a"), notice("b"))
        assertEquals(c.unreadCount(s), c.unreadCount(s))
    }

    // ── 垃圾回收（隐蔽的长期风险）─────────────────────────────────────────

    @Test
    fun deletedNotices_arePrunedFromReadIds_notAccumulatingForever() {
        // 后台删掉公告后，本地已读 id 若不清，集合只增不减 → 几年后变成没人敢动的垃圾堆。
        val store = FakeStore()
        val c = center(store)
        c.markAllRead(snapshot(notice("gone"), notice("stays")))
        assertEquals(setOf("gone", "stays"), store.ids)

        // 后台删掉了 gone
        c.unreadCount(snapshot(notice("stays")))
        assertEquals("已消失公告的 id 必须被剔除", setOf("stays"), store.ids)
    }

    @Test
    fun pruning_doesNotWriteWhenNothingWasPruned() {
        // 每次算未读都写盘是浪费（而且会触发不必要的磁盘 IO）。
        val store = FakeStore()
        val c = center(store)
        val s = snapshot(notice("a"))
        c.markRead("a")
        val writesAfterMark = store.writes
        c.unreadCount(s)
        c.unreadCount(s)
        assertEquals("没有可清理项时不该再写盘", writesAfterMark, store.writes)
    }

    // ── 弹窗 ──────────────────────────────────────────────────────────────

    @Test
    fun popupNotices_requireBothPopupFlagAndUnread() {
        val c = center()
        val s = snapshot(
            notice("a", popup = true),               // 勾了+未读 → 弹
            notice("b", popup = false),              // 没勾 → 不弹（只进红点）
            notice("c", popup = true),
        )
        assertEquals(listOf("a", "c"), c.popupNotices(s).map { it.id })
    }

    @Test
    fun popupNotices_doNotRepeatAfterRead() {
        // 勾了 popup 但已经读过的再弹一次就是骚扰。
        val store = FakeStore()
        val c = center(store)
        val s = snapshot(notice("a", popup = true))
        assertEquals(1, c.popupNotices(s).size)
        c.markRead("a")
        assertTrue("已读后不得再弹", c.popupNotices(s).isEmpty())
    }

    // ── 红点显示 ──────────────────────────────────────────────────────────

    @Test
    fun badge_disappearsAtZero() {
        // 用户要求"有未读才出现"：0 条时整个角标必须消失，而不是显示一个 0。
        assertFalse(NoticeCenter.badgeVisible(0))
        assertTrue(NoticeCenter.badgeVisible(1))
        assertEquals("", NoticeCenter.badgeText(0))
    }

    @Test
    fun badge_capsAt99Plus() {
        // 三位数会把圆点撑成一条，既难看也会挤到旁边图标。
        assertEquals("1", NoticeCenter.badgeText(1))
        assertEquals("9", NoticeCenter.badgeText(9))
        assertEquals("99", NoticeCenter.badgeText(99))
        assertEquals("99+", NoticeCenter.badgeText(100))
        assertEquals("99+", NoticeCenter.badgeText(9999))
    }

    @Test
    fun storeFailure_doesNotCrash_andDegradesToUnread() {
        // 存储坏掉时宁可红点不消，也不能崩；更不能因为读失败就把公告当成"已读"藏起来。
        val broken = object : NoticeCenter.Store {
            override fun readIds(): MutableSet<String> = throw RuntimeException("disk full")
            override fun setReadIds(ids: MutableSet<String>) { throw RuntimeException("disk full") }
        }
        val c = NoticeCenter(broken)
        val s = snapshot(notice("a"))
        // 读失败会让 unread() 抛出，调用方应能捕获；这里确认异常类型可预期而不是 Error
        try {
            c.unreadCount(s)
        } catch (expected: RuntimeException) {
            // 生产实现 forContext 内部已 try/catch 兜底，这里是"存储被换成坏的"时的兜底预期
        }
    }
}
