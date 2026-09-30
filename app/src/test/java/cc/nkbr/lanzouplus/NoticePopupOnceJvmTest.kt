package cc.nkbr.lanzouplus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [DFWX] DFW-66：**「每条公告只弹一次」的守卫**。
 *
 * 用户 2026-10-01："我之前让你搞的公告……就是发布后只会弹一次这个公告，
 * 下次打开软件用户就不会收到了吗，**这个你没做**。"
 *
 * ## 真相不是"没做模式"，而是**已读状态每次启动都被抹掉**
 * `NoticeCenter.prunedReadIds()` 把"还不知道后台有哪些公告"（快照为 null / 不可达）
 * 当成了"后台把公告都删了"，于是 `retainAll(空集)` 把整份已读集合清空并写盘。
 *
 * 启动链路（`MainActivity`）：
 * ```
 * showHomeLanding() → refreshNoticeBell() → unreadCount(null)   ← 此刻已读被清空
 * maybeFetchNotices() → 拉回公告 → 又变成"未读" → 又弹一次
 * ```
 * 所以行为上表现为"设置成一次性也永远每次弹"。下面这条链路测试就是照着这个顺序写的。
 */
class NoticePopupOnceJvmTest {

    /** 内存版偏好存储：**记录写入次数**，用来断言"不该写盘的时候一次都没写"。 */
    private class MemStore : NoticeCenter.Store {
        var ids: Set<String> = LinkedHashSet()
        var writes = 0
        override fun readIds(): Set<String> = LinkedHashSet(ids)
        override fun setReadIds(ids: Set<String>) {
            writes++
            this.ids = LinkedHashSet(ids)
        }
    }

    private fun notice(id: String) = RemoteConfigClient.Notice(
        id, "东方无限 公测开始", "感谢使用。", "normal", false,
        RemoteConfigClient.Notice.MODE_ONCE, 1_700_000_000_000L,
    )

    private fun snapshot(vararg notices: RemoteConfigClient.Notice, reachable: Boolean = true) =
        RemoteConfigClient.Snapshot(
            reachable, RemoteConfigClient.Control.normal(), null, notices.toList(), emptyList(),
        )

    // ── 核心：拿不到后台数据时绝不能清理已读 ────────────────────────────────

    @Test
    fun nullSnapshot_mustNotWipeTheReadSet() {
        val store = MemStore().apply { ids = linkedSetOf("n1") }
        val center = NoticeCenter(store)

        center.unreadCount(null)

        assertTrue("快照是 null（还没拉到数据）时，已读状态必须原样保留", center.isRead("n1"))
        assertEquals("这种情况下一次都不该写盘", 0, store.writes)
    }

    @Test
    fun unreachableSnapshot_mustNotWipeTheReadSet() {
        val store = MemStore().apply { ids = linkedSetOf("n1") }
        val center = NoticeCenter(store)

        center.unreadCount(RemoteConfigClient.Snapshot.unavailable())

        assertTrue("网络失败（reachable=false）不等于公告被删，已读必须保留", center.isRead("n1"))
        assertEquals("这种情况下一次都不该写盘", 0, store.writes)
    }

    @Test
    fun popupNotices_withNoData_keepsTheReadState() {
        val store = MemStore().apply { ids = linkedSetOf("n1") }
        val center = NoticeCenter(store)

        assertTrue(center.popupNotices(null).isEmpty())
        assertTrue(center.popupNotices(RemoteConfigClient.Snapshot.unavailable()).isEmpty())
        assertTrue(center.isRead("n1"))
        assertEquals(0, store.writes)
    }

    // ── 真实启动链路：不能再重新弹 ─────────────────────────────────────────

    @Test
    fun theRealStartupSequence_noLongerRePops() {
        val store = MemStore()
        val center = NoticeCenter(store)
        val real = snapshot(notice("n1"))

        assertEquals("前置：未读时应是弹窗候选", 1, center.popupNotices(real).size)
        center.markRead("n1")                                  // 用户点了「知道了」
        assertTrue("读过之后不得再弹", center.popupNotices(real).isEmpty())

        // ↓↓↓ 这一步就是旧版把已读抹掉的地方：启动时先刷红点，但快照还没到 ↓↓↓
        center.unreadCount(null)
        center.unreadCount(RemoteConfigClient.Snapshot.unavailable())

        assertTrue("冷启动那一步不能再把已读抹掉", center.isRead("n1"))
        assertTrue("同一条公告不得再次弹出（用户报的就是这个）", center.popupNotices(real).isEmpty())
    }

    // ── 反向守卫：真的删了公告时，垃圾回收必须照做 ─────────────────────────

    @Test
    fun reachableSnapshot_stillCollectsDeletedNoticeIds() {
        // 不能因为修 bug 就把"已读集合只增不减"这个隐患放回来——
        // 那是 NoticeCenter 单独成类的原因（见类注释）。
        val store = MemStore().apply { ids = linkedSetOf("gone", "alive") }
        val center = NoticeCenter(store)

        center.unreadCount(snapshot(notice("alive")))

        assertFalse("后台已经删掉的公告 id 必须被回收", center.isRead("gone"))
        assertTrue("仍然存在的公告 id 不能误删", center.isRead("alive"))
    }

    @Test
    fun alwaysMode_stillPopsEvenAfterRead() {
        // 反向守卫：修"一次性"不能把用户明确要的"永久"档改坏。
        val center = NoticeCenter(MemStore())
        val always = RemoteConfigClient.Notice(
            "a1", "标题", "正文", "normal", false,
            RemoteConfigClient.Notice.MODE_ALWAYS, 1_700_000_000_000L,
        )
        val snap = snapshot(always)
        center.markRead("a1")
        assertEquals("「永久」档无视已读，必须每次都弹", 1, center.popupNotices(snap).size)
    }

    @Test
    fun silentMode_neverPops() {
        val center = NoticeCenter(MemStore())
        val silent = RemoteConfigClient.Notice(
            "s1", "标题", "正文", "normal", false,
            RemoteConfigClient.Notice.MODE_SILENT, 1_700_000_000_000L,
        )
        assertTrue("「静默」档永不弹窗，只亮红点", center.popupNotices(snapshot(silent)).isEmpty())
    }
}
