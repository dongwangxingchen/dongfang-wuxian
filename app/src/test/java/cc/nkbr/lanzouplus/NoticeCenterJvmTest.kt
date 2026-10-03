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

    /**
     * 正常快照：公告这一路**拉成功了**（`noticeOk=true`）。
     *
     * [2026-10-03] 构造签名多了第二个参数 `noticeOk` —— 它和 `reachable` 不是一回事，
     * 理由见下面 `noticeRouteFailure...` 那条用例。
     */
    private fun snapshot(vararg notices: RemoteConfigClient.Notice) =
        RemoteConfigClient.Snapshot(
            true, true, RemoteConfigClient.Control.normal(), null, notices.toList(), emptyList(),
        )

    /**
     * 「四个集合里**别的**拉到了，但公告这一路失败了」的快照。
     *
     * 这正是真实世界里最常见的那种"部分失败"：公告接口超时/返回非 200/格式坏
     * （`RemoteConfigClient.get()` 这些情况都抛 IOException，被 `fetch` 的 catch 吞掉），
     * 而 control / release / changelog 三路正常。
     */
    private fun snapshotWithoutNotices() =
        RemoteConfigClient.Snapshot(
            true, false, RemoteConfigClient.Control.normal(), null, emptyList(), emptyList(),
        )

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

    /**
     * 存储坏掉时**必须给一个可预期的结果**，而不是崩、也不是静默把公告当成已读藏起来。
     *
     * [2026-10-03 修一条零断言用例] 这条原来长这样：
     * ```
     * try { c.unreadCount(s) } catch (expected: RuntimeException) { }
     * ```
     * **抛与不抛都绿** —— 断言数是 0，测试名承诺的 `degradesToUnread` 零覆盖。
     * 把产品改成"读失败就当全部已读"，红点永久消失，这条测试照样通过。
     * 现在改成**显式二选一都断言**：要么正常返回未读数、要么抛出可预期的 RuntimeException，
     * 两条分支各自断言，没有第三条路。
     */
    @Test
    fun storeFailure_doesNotCrash_andDegradesToUnread() {
        val broken = object : NoticeCenter.Store {
            override fun readIds(): MutableSet<String> = throw RuntimeException("disk full")
            override fun setReadIds(ids: MutableSet<String>) { throw RuntimeException("disk full") }
        }
        val c = NoticeCenter(broken)
        val s = snapshot(notice("a"))

        var returned: Int? = null
        var thrown: Throwable? = null
        try {
            returned = c.unreadCount(s)
        } catch (t: Throwable) {
            thrown = t
        }

        when {
            thrown != null -> {
                // 允许抛，但**只允许可预期的 RuntimeException**：
                // 抛 Error（比如 StackOverflowError / AssertionError）说明是别的问题，要暴露出来。
                assertTrue(
                    "存储坏掉只该抛 RuntimeException，实际抛了 " + thrown!!.javaClass.name,
                    thrown is RuntimeException,
                )
            }
            returned != null -> {
                // 允许不抛，但那时**必须仍然把公告当成未读**（宁可红点不消，也不能藏公告）。
                assertTrue(
                    "存储读失败时不许把公告当成已读藏起来 —— 返回的未读数应当是 1，实际 $returned",
                    returned!! >= 1,
                )
            }
            else -> throw AssertionError("既没返回也没抛异常，这条路径不可达")
        }
    }

    // ── [2026-10-03] 已读集合的垃圾回收：公告路单独失败时绝不许清 ──────────

    /**
     * **BUG 回归守卫**：公告这一路单独失败时，已读集合**一个都不许被清掉**。
     *
     * ## 原来的 bug（读码坐实，非推测）
     * `RemoteConfigClient.fetch()` 里四路各自 try/catch，`anyOk` 表示"**任意**一路成功"
     * （这就是 `Snapshot.reachable` 的语义，那边注释也写明了）。
     * 而 `NoticeCenter.prunedReadIds` 把 `reachable` 当成"**公告**这一路成功了"用：
     * ```
     * if (snapshot == null || !snapshot.reachable) return read;   // 只挡住"完全不可达"
     * for (Notice n : visible(snapshot)) alive.add(n.id);         // 公告路失败 → alive 为空
     * if (read.retainAll(alive)) store.setReadIds(read);          // → 已读集合被清空并落盘
     * ```
     * 于是「公告接口抖一下 + 其余三路正常」→ 用户读过的「一次性」公告**下次开机再弹一遍**。
     *
     * 这恰恰是那段注释里声称已修掉的投诉（用户 2026-10-01「发布后只弹一次你没做」）——
     * 上一次只堵了 `snapshot == null`，没覆盖"可达但公告路失败"。
     *
     * ## 这条用例怎么抓
     * 先让用户读过公告 a，再喂一个"公告路失败"的快照，断言 a **仍然是已读**。
     * 反向探针（把 `noticeOk` 改回 `reachable`）会让这条精确变红。
     */
    @Test
    fun noticeRouteFailure_mustNotWipeTheReadSet() {
        val store = FakeStore()
        val c = center(store)
        c.markRead("a")
        assertTrue("前置条件：a 应当已读", c.isRead("a"))

        // 公告路失败：reachable=true（别的路成功了），但 notices 是空表。
        c.unreadCount(snapshotWithoutNotices())

        assertTrue(
            "公告接口单独失败时**绝不能**清空已读集合 —— 清了的话用户读过的" +
                "「一次性」公告下次开机会再弹一遍（这正是用户 2026-10-01 反馈的那个 bug）",
            c.isRead("a"),
        )
    }

    /**
     * 但「公告路**成功**、后台确实把公告删光了」时，已读集合**必须**被清理。
     *
     * 这是上一条的对照组：修 bug 不能修成"永远不清理" ——
     * 那样已读集合会随公告增删只增不减，长期变成清不掉的垃圾（原注释里写明了这个顾虑）。
     * 两条一起才说明 `noticeOk` 这个区分是**真的在区分**，而不是把清理整个关掉。
     */
    @Test
    fun noticeRouteSuccessWithEmptyBackend_stillPrunesTheReadSet() {
        val store = FakeStore()
        val c = center(store)
        c.markRead("已删除的旧公告")
        assertTrue(c.isRead("已删除的旧公告"))

        // 公告路成功（noticeOk=true）且后台一条公告都没有 → 该回收就回收。
        c.unreadCount(snapshot())

        assertFalse(
            "公告路成功且后台确实没有公告时，已读集合应当被回收（否则会无限膨胀）",
            c.isRead("已删除的旧公告"),
        )
    }
}
