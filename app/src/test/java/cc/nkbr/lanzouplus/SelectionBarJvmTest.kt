package cc.nkbr.lanzouplus

import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import org.junit.Assert.assertEquals
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
 * [DFWX] DFW-68：**长按工具条必须每个动作都带中文标签**。
 *
 * 用户 2026-10-01：
 * > "长按软件库界面某个软件出现的底部菜单太丑了，而且**还没中文标注**，
 * >  希望你可以把它优化成**排列整齐的按钮形式**，非常美观。"
 *
 * ## 旧实现的病根（只读审计坐实）
 * 六个动作全是 `ImageButton`，语义**只写在 `contentDescription` 里**——
 * 读屏能读、屏幕上一个字都没有；"复制"和"下载"两个图标形状又接近，用户只能靠猜。
 * 手机宽度下六个图标必然折成两行，加摘要行构成 144dp 的双层块，这就是"排列不整齐"的来源。
 *
 * 现在每一格是「图标 + 中文标签」，整格可点，等宽网格排布。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class SelectionBarJvmTest {

    companion object {
        @BeforeClass @JvmStatic fun silenceAutoImport() { MainActivity.LIBRARY_AUTO_IMPORT = false }
    }

    private fun activity(): MainActivity {
        val a = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        shadowOf(Looper.getMainLooper()).idle()
        return a
    }

    /** 动作格 = 同时含图标的和文字子视图的 LinearLayout（摘要行/全选按钮不满足）。 */
    private fun actionCells(view: View, out: MutableList<ViewGroup> = mutableListOf()): List<ViewGroup> {
        if (view is ViewGroup) {
            val hasLabel = (0 until view.childCount).any { view.getChildAt(it) is TextView }
            val hasIcon = (0 until view.childCount).any { view.getChildAt(it) is ImageView }
            if (view is LinearLayout && hasLabel && hasIcon) out.add(view)
            for (i in 0 until view.childCount) actionCells(view.getChildAt(i), out)
        }
        return out
    }

    private fun labelOf(cell: ViewGroup): String {
        for (i in 0 until cell.childCount) {
            val child = cell.getChildAt(i)
            if (child is TextView) return child.text?.toString() ?: ""
        }
        return ""
    }

    private fun findGrid(view: View): GridLayout? {
        if (view is GridLayout) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) findGrid(view.getChildAt(i))?.let { return it }
        return null
    }

    private fun enterSelection(a: MainActivity) {
        a.navigateHome()
        shadowOf(Looper.getMainLooper()).idle()
        a.enterSelection()
        shadowOf(Looper.getMainLooper()).idle()
    }

    // ── 核心：每个动作都要有中文标签 ───────────────────────────────────────

    @Test
    fun everyActionCell_carriesAChineseLabel() {
        val a = activity()
        enterSelection(a)
        assertNotNull("应已建立长按工具条", a.selectionBar)

        val cells = actionCells(a.selectionBar!!)
        // 「重命名自建文件夹」默认 GONE（只在选中单个自建合集时出现），所以默认是 5 格。
        assertEquals("目录/搜索页长按默认应有 5 个可见动作格", 5, cells.size)

        val labels = cells.map { labelOf(it) }
        for (expected in listOf("复制", "下载", "分享", "加入分类", "退出")) {
            assertTrue("长按工具条必须有中文标签「$expected」，实测标签=$labels", labels.contains(expected))
        }
        assertTrue(
            "不得出现空标签（旧实现就是这样：一个字都没有）",
            labels.none { it.isBlank() },
        )
    }

    @Test
    fun everyActionCell_keepsItsFullAccessibilityDescription() {
        // 短标签给人看；完整语义仍要留给读屏（"复制" vs "复制所选原链接" 是两种受众）。
        val a = activity()
        enterSelection(a)
        val cells = actionCells(a.selectionBar!!)
        for (cell in cells) {
            val desc = cell.contentDescription?.toString() ?: ""
            assertTrue("每格都要有读屏可读的完整说明，实测空的是第 ${cells.indexOf(cell)} 格", desc.isNotBlank())
        }
    }

    // ── 排列整齐：等宽网格 ─────────────────────────────────────────────────

    @Test
    fun actionCells_areLaidOutInAnEvenGrid() {
        val a = activity()
        enterSelection(a)
        val bar = a.selectionBar!!
        val grid = findGrid(bar)
        assertNotNull("手机上动作区应该折行成等宽网格（旧实现是硬编码两行、图标乱排）", grid)

        val cells = actionCells(bar)
        val widths = cells.map { it.layoutParams.width }.distinct()
        assertEquals(
            "所有动作格必须等宽（width=0 + weight 均分），实测各格宽度=$widths",
            listOf(0), widths,
        )
        assertTrue("网格列数必须能装下全部动作", grid!!.columnCount * grid.rowCount >= cells.size)
    }

    @Test
    fun actionCell_isTallEnoughForIconAndLabel() {
        val a = activity()
        enterSelection(a)
        val cell = actionCells(a.selectionBar!!).first()
        val height = cell.layoutParams.height
        assertTrue(
            "格子高度必须容得下 24dp 图标 + 11sp 中文（实测 $height px，要求 ≥ ${a.dp(MainActivity.SELECTION_CELL_H)} px）",
            height >= a.dp(MainActivity.SELECTION_CELL_H),
        )
    }

    // ── 反向守卫：退出后清干净 ─────────────────────────────────────────────

    @Test
    fun exitingSelection_clearsTheBar() {
        val a = activity()
        enterSelection(a)
        a.exitSelection()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("退出多选后工具条引用必须清空", null, a.selectionBar)
    }
}
