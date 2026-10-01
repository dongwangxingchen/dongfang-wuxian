package cc.nkbr.lanzouplus

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
 * [DFWX] DFW-60：维护页的**行为验收**（策略层已在 MaintenanceGateJvmTest 测过）。
 *
 * 这里只测用户能感受到的三件事，而且必须从**真实构建出来的界面**上验：
 *   ① 没有任何按钮、点空白处不关（用户原话："直接没有按钮，点击空白处也不能关闭"）；
 *   ② 返回键无效（返回键一按就退出 = 拦截形同虚设）；
 *   ③ 连点版本号 7 次能自救（否则后台误开就把用户和作者一起锁死）。
 *
 * 策略对了但界面忘了用它的返回值，用户照样会被放进来或锁死——所以这层不能省。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class MaintenanceOverlayJvmTest {

    companion object {
        @BeforeClass @JvmStatic fun silenceAutoImport() { MainActivity.LIBRARY_AUTO_IMPORT = false }
    }

    private fun activity(): MainActivity {
        val a = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        shadowOf(Looper.getMainLooper()).idle()
        return a
    }

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

    private fun versionLabel(a: MainActivity): TextView? {
        val overlay = a.maintenanceOverlay ?: return null
        var found: TextView? = null
        fun walk(v: View) {
            // [DFW-91] 后门标签**不再显示版本号**（版本号只留「检查更新」右侧），
            // 现在它就是产品名本身。后门照旧挂在它上面。
            if (v is TextView && v.text.toString() == MainActivity.PRODUCT_NAME) {
                found = v
            }
            if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
        }
        walk(overlay)
        return found
    }

    private fun screen(
        title: String = "维护中",
        body: String = "正在升级，稍后回来",
        until: String = "",
    ) = MaintenanceGate.Screen(true, title, body, until)

    // ── 覆盖与内容 ────────────────────────────────────────────────────────

    @Test
    fun overlay_coversScreenAndShowsNotice() {
        val a = activity()
        a.showMaintenanceOverlay(screen(title = "维护中", body = "正在升级"))
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue("铺了维护页就必须进入拦截态", a.maintenanceBlocking)
        val overlay = a.maintenanceOverlay
        assertNotNull("维护页必须真的挂上去", overlay)
        val texts = textsIn(overlay!!)
        assertTrue("要显示后台填的标题：$texts", texts.contains("维护中"))
        assertTrue("要显示后台填的正文：$texts", texts.contains("正在升级"))
    }

    @Test
    fun overlay_hasNoButtonsAtAll() {
        // 用户原话："直接没有按钮"。任何 Button 出现都是违背要求。
        val a = activity()
        a.showMaintenanceOverlay(screen())
        shadowOf(Looper.getMainLooper()).idle()

        val labels = buttonsIn(a.maintenanceOverlay!!).map { it.text.toString() }.filter { it.isNotBlank() }
        assertTrue("维护页不得有任何按钮，实际有：$labels", labels.isEmpty())
    }

    @Test
    fun tappingBlankArea_doesNotDismiss() {
        // 用户原话："点击空白处也不能关闭"。空白处必须可点（否则触摸会穿透到下面界面），但点了什么都不做。
        val a = activity()
        a.showMaintenanceOverlay(screen())
        shadowOf(Looper.getMainLooper()).idle()

        val overlay = a.maintenanceOverlay!!
        overlay.performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue("点空白处不得关闭维护页", a.maintenanceBlocking)
        assertNotNull("维护页必须仍在", a.maintenanceOverlay)
    }

    // ── 返回键无效 ────────────────────────────────────────────────────────

    @Test
    fun backKey_isSwallowed() {
        val a = activity()
        a.showMaintenanceOverlay(screen())
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue("拦截期间返回键必须由我们消费（否则会回主页/退出应用）", a.canHandleBack())
        // **必须绕过 260ms 返回节流**：`performSystemBack` 开头就是
        // `if(uptimeMillis()-lastSystemBackAt<260) return;`，而 Robolectric 里 uptimeMillis 很小、
        // lastSystemBackAt=0 → 不绕过的话它无条件早退，测试会**因为错误的原因变绿**
        // （本测试第一版正是如此：探针撤掉维护守卫后仍然全绿，属于典型假绿）。
        a.lastSystemBackAt = -10_000L
        a.performSystemBack()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue("按返回键后维护页必须仍在（用户要求：返回键无效）", a.maintenanceBlocking)
        assertFalse("拦截期间不得结束 Activity（正常路径会 finishAfterTransition）", a.isFinishing)
    }

    @Test
    fun backKey_withoutGuard_wouldActuallyFinish_provingTheTestIsDiscriminating() {
        // 这条是"守卫的守卫"：证明上一条**真的能**发现守卫被撤掉。
        // 做法：先确认没有维护拦截时，同样的调用确实会结束 Activity——
        // 如果这里也是 false，说明上一条的 assertFalse(isFinishing) 根本没有鉴别力。
        val a = activity()
        // [DFW-73] 顶级页的返回语义变成"再返回一次退出软件"：第一次只弹横幅，
        // 所以这条鉴别力探针要按**两次**才等价于旧行为的"按一次就退出"。
        a.lastSystemBackAt = -10_000L
        a.performSystemBack()
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse("第一次返回只提示，不该退出", a.isFinishing)
        a.lastSystemBackAt = -10_000L
        a.performSystemBack()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(
            "窗口内第二次返回应真的结束 Activity（若这里是 false，说明 backKey_isSwallowed 的断言是空的）",
            a.isFinishing,
        )
    }

    @Test
    fun backKey_worksNormallyAgainAfterDismiss() {
        // 反向守卫：不能因为"拦返回键"把返回键永久弄坏。
        val a = activity()
        a.showMaintenanceOverlay(screen())
        shadowOf(Looper.getMainLooper()).idle()
        a.dismissMaintenanceOverlay()
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse("解除拦截后不得继续吞返回键", a.maintenanceBlocking)
    }

    // ── 维护时间区块 ──────────────────────────────────────────────────────

    @Test
    fun untilBlock_renderedAsItsOwnSection_whenFilled() {
        val a = activity()
        a.showMaintenanceOverlay(screen(until = "永久"))
        shadowOf(Looper.getMainLooper()).idle()

        val texts = textsIn(a.maintenanceOverlay!!)
        assertTrue("要有独立的「维护时间」标签：$texts", texts.contains("维护时间"))
        assertTrue("要原样显示用户填的内容（可写「永久」）：$texts", texts.contains("永久"))
    }

    @Test
    fun untilBlock_absentWhenEmpty() {
        val a = activity()
        a.showMaintenanceOverlay(screen(until = ""))
        shadowOf(Looper.getMainLooper()).idle()

        val texts = textsIn(a.maintenanceOverlay!!)
        assertFalse("留空时不该出现「维护时间」区块：$texts", texts.contains("维护时间"))
    }

    // ── 隐藏后门 ──────────────────────────────────────────────────────────

    @Test
    fun backdoor_sevenTapsOnTheBottomLabel_releasesTheGate() {
        val a = activity()
        a.showMaintenanceOverlay(screen())
        shadowOf(Looper.getMainLooper()).idle()

        val label = versionLabel(a)
        assertNotNull("维护页底部必须有个可点的标签（后门就挂在它上面）", label)

        for (i in 1 until MaintenanceGate.BACKDOOR_TAPS) {
            label!!.performClick()
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue("第 $i 次点击不该解锁（否则后门太容易被误触）", a.maintenanceBlocking)
        }
        label!!.performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertFalse("连点 7 次必须能自救（后台误开时用户和作者不能被永久锁死）", a.maintenanceBlocking)
        assertNull("解锁后维护页应从界面上移除", a.maintenanceOverlay)
    }

    @Test
    fun backdoor_doesNotLeakIntoTheNoticeText() {
        // 后门是应急逃生口，界面上**不得**出现任何提示（否则等于告诉所有人怎么绕过维护）。
        val a = activity()
        a.showMaintenanceOverlay(screen())
        shadowOf(Looper.getMainLooper()).idle()

        val texts = textsIn(a.maintenanceOverlay!!).joinToString(" ")
        for (hint in listOf("连点", "7 次", "七次", "跳过", "后门")) {
            assertFalse("界面上不得提示后门用法（出现「$hint」）：$texts", texts.contains(hint))
        }
    }

    @Test
    fun dismissing_whenNoOverlay_isSafe() {
        // 幂等：重复解除、或在没铺过的时候解除，都不该崩。
        val a = activity()
        a.dismissMaintenanceOverlay()
        a.dismissMaintenanceOverlay()
        assertFalse(a.maintenanceBlocking)
    }

    @Test
    fun showingTwice_doesNotStackTwoOverlays() {
        // 启动检查可能被触发多次（回前台/重试）。重复铺必须幂等，否则会叠出多层拦截页。
        val a = activity()
        a.showMaintenanceOverlay(screen())
        shadowOf(Looper.getMainLooper()).idle()
        val first = a.maintenanceOverlay
        a.showMaintenanceOverlay(screen())
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("重复调用必须复用同一个覆盖层，不能叠加", first, a.maintenanceOverlay)
    }

    // ── 与策略层的接线 ────────────────────────────────────────────────────

    @Test
    fun failOpen_unreachableServer_neverShowsOverlay() {
        // 端到端接线守卫：后台不可达时**绝不能**铺维护页。
        // 若这里错了，服务器一挂全体用户都进不去 App——比"公告晚显示几分钟"严重得多。
        val a = activity()
        val screenFromUnreachable = a.maintenanceGate().decide(RemoteConfigClient.Snapshot.unavailable())
        assertFalse("服务器不可达时必须放行（fail-open）", screenFromUnreachable.block)
        // 只有 block=true 才该铺页；这里确认真的没铺
        assertEquals("不该有覆盖层", null, a.maintenanceOverlay)
    }
}
