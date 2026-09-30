package cc.nkbr.lanzouplus

import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
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
 * [DFWX] DFW-71：设置页返回行为的**回归守卫**。
 *
 * 用户 2026-09-30 报告两个症状：
 * > "我从里面退出来后，会回到我点击前的那个位置，但是它就回到最顶部了，或者是去软件库那个界面了。"
 *
 * 并行诊断给出的根因（本测试直接针对它们）：
 * - **缺陷 A（回顶）**：设置子页返回都走 `showSettings()`，而它是**整页重建**
 *   （`primaryBase(3)` → `basePage()` 会 new 一棵全新的视图树），新 `ScrollView` 的 scrollY 必然是 0。
 *   目录页有 `FolderPageState.scrollY` 那套恢复机制，**设置页一个都没有**。
 *   同理分区展开态也被硬编码默认值重置。
 * - **缺陷 B（跳到软件库）**：设置里的「资源源管理」调 `showSources()`，
 *   它把 `systemBackAction` 清空并把目的地设成 1 → 返回落进兜底 `navigateHome()` → 软件库。
 *   **这是必然的，不是偶发**——"有时候"取决于用户点的是哪个按钮。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class SettingsBackNavigationJvmTest {

    companion object {
        @BeforeClass @JvmStatic fun silenceAutoImport() { MainActivity.LIBRARY_AUTO_IMPORT = false }
    }

    private fun activity(): MainActivity {
        val a = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        shadowOf(Looper.getMainLooper()).idle()
        return a
    }

    private fun findScrollWithDescription(view: View, description: String): ScrollView? {
        if (view is ScrollView && view.contentDescription?.toString() == description) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) findScrollWithDescription(view.getChildAt(i), description)?.let { return it }
        }
        return null
    }

    /** 找到某个设置分区的表头（它的 contentDescription 形如「高级，已展开，点击收起」）。 */
    private fun findSectionHeader(view: View, title: String): View? {
        val desc = view.contentDescription?.toString() ?: ""
        if (desc.startsWith("$title，已")) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) findSectionHeader(view.getChildAt(i), title)?.let { return it }
        }
        return null
    }

    private fun textsIn(view: View, out: MutableList<String> = mutableListOf()): List<String> {
        if (view is TextView) view.text?.toString()?.let { if (it.isNotEmpty()) out.add(it) }
        if (view is ViewGroup) for (i in 0 until view.childCount) textsIn(view.getChildAt(i), out)
        return out
    }

    /** 让设置页的 ScrollView 真正布局出来，否则 scrollTo 会被内容高度裁成 0。 */
    private fun layoutSettings(a: MainActivity): ScrollView {
        a.showSettings()
        shadowOf(Looper.getMainLooper()).idle()
        val scroll = findScrollWithDescription(a.root, "设置")
        assertNotNull("设置页必须有 contentDescription=「设置」的 ScrollView（恢复滚动位置就是靠它）", scroll)
        val width = 1080
        val height = 2400
        scroll!!.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        scroll.layout(0, 0, width, height)
        return scroll
    }

    // ── 缺陷 A：滚动位置 ──────────────────────────────────────────────────

    /** 滚到"内容可滚范围内"的偏移并返回它（直接写死大数值会被 ScrollView 裁到上限，本测试第一版就踩了）。 */
    private fun scrollSettingsToTarget(a: MainActivity): Int {
        val scroll = layoutSettings(a)
        assertTrue("设置内容应高于一屏，否则滚动位置无从谈起", scroll.getChildAt(0).height > 2400)
        val maxScroll = (scroll.getChildAt(0).height - scroll.height).coerceAtLeast(1)
        val target = minOf(120, maxScroll)
        scroll.scrollTo(0, target)
        assertEquals("前置条件：确实滚到了 $target（可滚上限 $maxScroll）", target, scroll.scrollY)
        return target
    }

    private fun reopenedSettingsScroll(a: MainActivity): ScrollView {
        val reopened = findScrollWithDescription(a.root, "设置")!!
        reopened.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(2400, View.MeasureSpec.EXACTLY),
        )
        reopened.layout(0, 0, 1080, 2400)
        return reopened
    }

    private fun pressBack(a: MainActivity) {
        // 绕开 260ms 节流，否则会因"太频繁"被直接丢弃 → 断言因为错误的原因变绿
        a.lastSystemBackAt = -10_000L
        a.performSystemBack()
        shadowOf(Looper.getMainLooper()).idle()
    }

    /**
     * **真链路**：进设置子页 → 按返回 → 必须停在离开前的位置。
     *
     * ⚠️ 这条用例以前是**假绿**：它直接调 `a.showSettings()` 模拟"返回"，
     * 而那时 `pageKind` 仍是 4，恰好命中了写在 `showSettings()` 内部的记录语句。
     * 用户真实走的是"进子页 → 返回"，那一刻 `pageKind` 已经是子页的值 —— 永远记不到，必然回顶。
     * 现在记录点搬到了 `basePage()`（离开设置页的那一刻），并且本用例走真链路。
     */
    @Test
    fun settingsScrollPosition_survivesSubPageReturn() {
        val a = activity()
        val target = scrollSettingsToTarget(a)

        a.showAboutPage()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("前置：确实进了子页", 7, a.pageKind)

        pressBack(a)
        assertEquals("返回必须回到设置页", 4, a.pageKind)
        assertEquals("返回设置页必须停在离开前的位置，不能回顶", target, reopenedSettingsScroll(a).scrollY)
    }

    @Test
    fun settingsScrollPosition_survivesEveryEntry() {
        // 用户 2026-10-01："**全部按钮都是啊**，点击进去后退出，绝对返回到顶部，
        // 这不是个问题，这是必然事件，我需要你修复这个问题。"
        // 所以不能只测一个入口 —— 每个入口都要过一遍。
        val entries: List<Pair<String, (MainActivity) -> Unit>> = listOf(
            "关于" to { x -> x.showAboutPage() },
            "参考与致谢" to { x -> x.showAcknowledgementsPage() },
            "崩溃日志" to { x -> x.showCrashLogPage() },
            "公告" to { x -> x.showNoticeCenter() },
            "资源源管理" to { x -> x.showSourcesFromSettings() },
        )
        for ((name, open) in entries) {
            val a = activity()
            val target = scrollSettingsToTarget(a)

            open(a)
            shadowOf(Looper.getMainLooper()).idle()

            pressBack(a)
            assertEquals("[$name] 返回必须回到设置页", 4, a.pageKind)
            assertEquals(
                "[$name] 返回设置页必须停在离开前的位置，不能回顶",
                target, reopenedSettingsScroll(a).scrollY,
            )
        }
    }

    @Test
    fun reenteringSettingsFromMenu_startsAtTop() {
        // 反向守卫：从悬浮球**重新进入**设置是"全新进入"，不是"返回"，必须回顶部。
        // 否则用户点设置会莫名停在半中间，反而变成新 bug。
        val a = activity()
        scrollSettingsToTarget(a)

        a.showAboutPage()
        shadowOf(Looper.getMainLooper()).idle()
        pressBack(a)
        assertEquals("前置：返回后回到设置", 4, a.pageKind)

        a.navigateHome()
        shadowOf(Looper.getMainLooper()).idle()
        a.goToDestination(3)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("前置：确实重新进了设置", 4, a.pageKind)
        assertEquals("从悬浮球重新进入设置应回到顶部", 0, reopenedSettingsScroll(a).scrollY)
    }

    @Test
    fun settingsScrollPosition_defaultsToTopOnFirstEntry() {
        // 反向守卫：不能因为"记住位置"就让**第一次**进设置也落在中间。
        val a = activity()
        val scroll = layoutSettings(a)
        assertEquals("首次进设置应从顶部开始", 0, scroll.scrollY)
    }

    // ── 缺陷 A 附：分区展开态 ─────────────────────────────────────────────

    @Test
    fun sectionExpansion_survivesSubPageReturn() {
        val a = activity()
        layoutSettings(a)

        // 「高级」默认收起，点开它
        val header = findSectionHeader(a.root, "高级")
        assertNotNull("应能找到「高级」分区表头", header)
        assertTrue("前置条件：「高级」默认是收起的", header!!.contentDescription.toString().contains("已收起"))
        header.performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue("点击后应展开", header.contentDescription.toString().contains("已展开"))

        // 返回设置页（整页重建）
        a.showSettings()
        shadowOf(Looper.getMainLooper()).idle()

        val reopened = findSectionHeader(a.root, "高级")
        assertNotNull(reopened)
        assertTrue(
            "返回后展开过的分区必须仍展开——否则用户会看到界面'跳'一下：${reopened!!.contentDescription}",
            reopened.contentDescription.toString().contains("已展开"),
        )
    }

    // ── 缺陷 B：资源源管理返回目标 ────────────────────────────────────────

    @Test
    fun sourceListFromSettings_backReturnsToSettings_notHome() {
        val a = activity()
        a.showSettings()
        shadowOf(Looper.getMainLooper()).idle()

        a.showSourcesFromSettings()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("前置条件：已离开设置页（目的地变成 1=资源源列表）", 1, a.primaryDestinationForTest())

        // 用户按返回
        a.lastSystemBackAt = -10_000L   // 绕开 260ms 节流，否则会因"太频繁"被直接丢弃
        a.performSystemBack()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals("从设置进的资源源管理，返回必须回设置页（3），不能回软件库（0）", 3, a.primaryDestinationForTest())
        assertNotNull("返回后应该真的在设置页上", findScrollWithDescription(a.root, "设置"))
        assertFalse("绝不能出现软件库首页", textsIn(a.root).contains("搜索一下"))
    }

    @Test
    fun sourceListFromMenu_backStillGoesHome() {
        // 反向守卫：从悬浮球菜单进资源源管理时，返回仍然回首页——那是原来的正确语义，不能被改坏。
        val a = activity()
        a.showSources()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, a.primaryDestinationForTest())

        a.lastSystemBackAt = -10_000L
        a.performSystemBack()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("从菜单进的资源源管理，返回应回首页（0）", 0, a.primaryDestinationForTest())
    }
}
