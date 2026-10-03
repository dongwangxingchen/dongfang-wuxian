package cc.nkbr.lanzouplus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [DFWX] DFW-60：维护/停更拦截的**真值表**。
 *
 * 这张卡有三条**方向相反**的诉求要同时成立，任何一条做错都会造成严重后果：
 *   ① 后台开了就必须拦（否则维护是摆设）；
 *   ② 服务器故障时**绝不能**把用户锁在外面（fail-open）；
 *   ③ 后台误开时用户/作者本人要能自救（隐藏后门），但不能变成永久绕过。
 */
class MaintenanceGateJvmTest {

    private fun snapshot(
        maintenanceOn: Boolean = false,
        blocked: Boolean = false,
        title: String = "",
        body: String = "",
        until: String = "",
    ) = RemoteConfigClient.Snapshot(
        true, true,
        RemoteConfigClient.Control(maintenanceOn, blocked, title, body, until),
        null, emptyList(), emptyList(),
    )

    // ── 该不该拦 ──────────────────────────────────────────────────────────

    @Test
    fun blocksWhenMaintenanceOn() {
        val screen = MaintenanceGate().decide(snapshot(maintenanceOn = true, title = "维护中", body = "稍后回来"))
        assertTrue("后台开了维护就必须拦", screen.block)
        assertEquals("维护中", screen.title)
        assertEquals("稍后回来", screen.body)
    }

    @Test
    fun blocksWhenHardBlockedEvenWithoutMaintenanceFlag() {
        // blocked 是"强制停更"那个更重的开关，独立于 maintenanceOn 生效。
        val screen = MaintenanceGate().decide(snapshot(blocked = true, title = "已停止维护"))
        assertTrue("blocked=true 必须拦，不能因为 maintenanceOn=false 就放行", screen.block)
    }

    @Test
    fun doesNotBlockWhenServerSaysNormal() {
        assertFalse("后台正常时不得拦", MaintenanceGate().decide(snapshot()).block)
    }

    // ── fail-open（最关键的一条）──────────────────────────────────────────

    @Test
    fun failsOpenWhenServerUnreachable() {
        // 后台不可达 → Snapshot.unavailable() → 必须放行。
        // 若这里按"维护中"处理，服务器一挂全体用户都进不去 App；
        // 把可用性押在一个 2C2G 小服务器上，代价远大于"公告晚显示几分钟"。
        assertFalse(
            "服务器连不上时必须按正常放行（fail-open），绝不因服务器故障锁死用户",
            MaintenanceGate().decide(RemoteConfigClient.Snapshot.unavailable()).block,
        )
    }

    @Test
    fun failsOpenOnNullSnapshot() {
        assertFalse("拿不到配置时按正常处理", MaintenanceGate().decide(null).block)
    }

    // ── 文案兜底 ──────────────────────────────────────────────────────────

    @Test
    fun emptyText_fallsBackSoUserDoesNotSeeBlankScreen() {
        // 后台可能只开了开关没填文案。全黑屏会让用户以为 App 坏了，必须给兜底话术。
        val screen = MaintenanceGate().decide(snapshot(maintenanceOn = true))
        assertTrue(screen.block)
        assertTrue("标题为空时要兜底，不能留空", screen.title.isNotEmpty())
        assertTrue("正文为空时要兜底，不能留空", screen.body.isNotEmpty())
    }

    // ── 维护时间区块 ──────────────────────────────────────────────────────

    @Test
    fun untilBlock_hiddenWhenEmpty_shownWhenFilled() {
        assertEquals("留空 → 不显示该区块（用户明确要求）", "", MaintenanceGate.untilText(""))
        assertEquals("留空 → 不显示该区块", "", MaintenanceGate.untilText("   "))
        assertEquals("留空 → 不显示该区块", "", MaintenanceGate.untilText(null))
        assertEquals("填了就要显示", "10月1日 20:00", MaintenanceGate.untilText("10月1日 20:00"))
    }

    @Test
    fun untilText_passesThroughUserTextVerbatim_includingPermanent() {
        // 用户原话："甚至我可以写上永久"。所以**绝不能**做日期解析或格式校验——
        // 任何自作聪明的解析都会把"永久"这类自由文本弄坏。
        assertEquals("永久", MaintenanceGate.untilText("永久"))
        assertEquals("永久", MaintenanceGate.untilText("  永久  "))
        assertEquals("等通知", MaintenanceGate.untilText("等通知"))
        assertEquals("<日期> 到期", MaintenanceGate.untilText("<日期> 到期"))
    }

    @Test
    fun untilBlock_isCarriedIntoTheScreen() {
        val screen = MaintenanceGate().decide(snapshot(maintenanceOn = true, until = "永久"))
        assertEquals("永久", screen.untilText)
        val blank = MaintenanceGate().decide(snapshot(maintenanceOn = true))
        assertEquals("没填就不带出区块", "", blank.untilText)
    }

    // ── 隐藏后门 ──────────────────────────────────────────────────────────

    @Test
    fun backdoorUnlocksExactlyOnTheSeventhTap() {
        val gate = MaintenanceGate()
        // 前 6 次都不该解锁，也不该误报"已触发"
        for (i in 1 until MaintenanceGate.BACKDOOR_TAPS) {
            assertFalse("第 $i 次点击不该解锁", gate.tapVersion())
            assertFalse("第 $i 次点击后仍应处于拦截态", gate.unlocked())
        }
        assertTrue("第 7 次点击应触发解锁", gate.tapVersion())
        assertTrue(gate.unlocked())
    }

    @Test
    fun backdoorDoesNotRepeatFireAfterUnlock() {
        val gate = MaintenanceGate()
        repeat(MaintenanceGate.BACKDOOR_TAPS) { gate.tapVersion() }
        assertTrue(gate.unlocked())
        // 解锁后再点不该反复返回 true（否则调用方会重复弹提示）
        assertFalse("已解锁后继续点不得重复触发", gate.tapVersion())
        assertFalse(gate.tapVersion())
    }

    @Test
    fun backdoorUnlock_actuallyLetsUserThrough() {
        val gate = MaintenanceGate()
        val control = snapshot(maintenanceOn = true, title = "维护中")
        assertTrue("解锁前必须拦", gate.decide(control).block)
        repeat(MaintenanceGate.BACKDOOR_TAPS) { gate.tapVersion() }
        assertFalse("解锁后必须放行（否则后门等于没做，用户/作者会被锁在外面）", gate.decide(control).block)
    }

    @Test
    fun backdoorIsSessionOnly_notPersistedAcrossRestart() {
        // 关键语义：后门是"应急逃生口"，不是"永久绕过开关"。
        // 若落盘，下次启动也不再拦 → 维护功能作废。这里用"新实例"模拟重启。
        val first = MaintenanceGate()
        repeat(MaintenanceGate.BACKDOOR_TAPS) { first.tapVersion() }
        assertTrue(first.unlocked())

        val afterRestart = MaintenanceGate()
        assertFalse("重启后必须恢复拦截（后门只存活于本次会话）", afterRestart.unlocked())
        assertTrue(
            "重启后后台仍开着维护 → 仍要拦",
            afterRestart.decide(snapshot(maintenanceOn = true)).block,
        )
    }

    @Test
    fun tapsRemaining_countsDownThenStopsAtZero() {
        val gate = MaintenanceGate()
        assertEquals(MaintenanceGate.BACKDOOR_TAPS, gate.tapsRemaining())
        gate.tapVersion()
        assertEquals(MaintenanceGate.BACKDOOR_TAPS - 1, gate.tapsRemaining())
        repeat(MaintenanceGate.BACKDOOR_TAPS) { gate.tapVersion() }
        assertEquals("已解锁后不再倒计时", 0, gate.tapsRemaining())
    }

    @Test
    fun resetBackdoor_reArmsTheGate() {
        val gate = MaintenanceGate()
        repeat(MaintenanceGate.BACKDOOR_TAPS) { gate.tapVersion() }
        assertTrue(gate.unlocked())
        gate.resetBackdoor()
        assertFalse(gate.unlocked())
        assertEquals("重置后点击计数也要归零", MaintenanceGate.BACKDOOR_TAPS, gate.tapsRemaining())
        assertTrue("重置后应重新拦截", gate.decide(snapshot(maintenanceOn = true)).block)
    }
}
