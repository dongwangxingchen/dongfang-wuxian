package cc.nkbr.lanzouplus

import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
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
 * [DFW-25 / UI-003] 下载页的**状态对等**守卫。
 *
 * 本卡要治的是"视觉与状态不统一"。下载页有 7 种状态，其中 5 种会出现在列表行上：
 * 等待中 / 解析中 / 下载中 / 已暂停 / 已取消 / 已完成 / 失败。
 *
 * ## 发现并修掉的真问题
 * `bindDownloadActions()` 原来只覆盖了「已完成」（2 个按钮）和「进行中/已暂停」（2 个按钮），
 * **失败与已取消一个行内按钮都没有**。后果：
 *  - 失败行只能靠"点整行"重试，而界面上没有任何提示说可以点；
 *  - 取消之后想清掉这条记录，只能长按进多选，或去菜单用「全部删除记录」。
 * 三种状态的可行动作数是 2 / 2 / 0 —— 这就是不统一，而且恰好落在用户最需要出口的两种状态上。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class DownloadStateParityJvmTest {

    companion object {
        @BeforeClass @JvmStatic fun silenceAutoImport() { MainActivity.LIBRARY_AUTO_IMPORT = false }
    }

    private fun activity(): MainActivity {
        val a = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        shadowOf(Looper.getMainLooper()).idle()
        return a
    }

    private fun entry(name: String, state: String): MainActivity.DownloadEntry {
        val e = MainActivity.DownloadEntry()
        e.name = name
        e.state = state
        return e
    }

    /** 造一条指定状态的下载记录并渲染下载页，返回该行的行内按钮。 */
    private fun rowButtons(state: String): List<ImageButton> {
        val a = activity()
        a.downloadEntries.clear()
        val e = entry("测试文件-$state.bin", state)
        a.downloadEntries.add(e)
        a.showDownloads()
        shadowOf(Looper.getMainLooper()).idle()
        a.renderDownloads("")
        shadowOf(Looper.getMainLooper()).idle()
        val row = a.downloadRows[e]
        assertNotNull("状态 $state 必须渲染出一行", row)
        val buttons = mutableListOf<ImageButton>()
        fun walk(view: View) {
            if (view is ImageButton) buttons.add(view)
            if (view is ViewGroup) for (i in 0 until view.childCount) walk(view.getChildAt(i))
        }
        walk(row!!)
        return buttons
    }

    private fun descriptions(buttons: List<ImageButton>) =
        buttons.map { it.contentDescription?.toString() ?: "" }

    // ── 每种状态都要有行内出口 ────────────────────────────────────────────

    @Test
    fun completedRow_offersDeleteFileAndDeleteRecord() {
        val desc = descriptions(rowButtons(MainActivity.DOWNLOAD_COMPLETED))
        assertEquals("已完成应有 2 个行内动作", 2, desc.size)
        assertTrue("要能删本地文件：$desc", desc.any { it.contains("删除本地文件") })
        assertTrue("要能只删记录：$desc", desc.any { it.contains("仅删除下载记录") })
    }

    @Test
    fun runningRow_offersPauseAndCancel() {
        val desc = descriptions(rowButtons(MainActivity.DOWNLOAD_RUNNING))
        assertEquals("下载中应有 2 个行内动作", 2, desc.size)
        assertTrue("要能暂停：$desc", desc.any { it.contains("暂停下载") })
        assertTrue("要能取消：$desc", desc.any { it.contains("取消下载") })
    }

    @Test
    fun pausedRow_offersResumeAndCancel() {
        val desc = descriptions(rowButtons(MainActivity.DOWNLOAD_PAUSED))
        assertEquals("已暂停应有 2 个行内动作", 2, desc.size)
        assertTrue("要能继续：$desc", desc.any { it.contains("继续下载") })
        assertTrue("要能取消：$desc", desc.any { it.contains("取消下载") })
    }

    @Test
    fun failedRow_offersRetryAndDeleteRecord() {
        val desc = descriptions(rowButtons(MainActivity.DOWNLOAD_FAILED))
        assertEquals(
            "下载失败是本卡修掉的真问题：原来一个行内按钮都没有，用户根本不知道能重试",
            2,
            desc.size,
        )
        assertTrue("要能直接重试：$desc", desc.any { it.contains("重新下载") })
        assertTrue("要能清掉这条失败记录：$desc", desc.any { it.contains("仅删除下载记录") })
    }

    @Test
    fun cancelledRow_offersDeleteRecord() {
        val desc = descriptions(rowButtons(MainActivity.DOWNLOAD_CANCELLED))
        assertEquals("已取消至少要能清掉记录", 1, desc.size)
        assertTrue("要能清掉这条记录：$desc", desc[0].contains("仅删除下载记录"))
    }

    @Test
    fun noStateIsLeftWithoutAnyRowLevelExit() {
        // 这条是本卡的**总纲**：任何会出现在列表上的状态，都不许一个出口都没有。
        val states = listOf(
            MainActivity.DOWNLOAD_COMPLETED,
            MainActivity.DOWNLOAD_RUNNING,
            MainActivity.DOWNLOAD_PAUSED,
            MainActivity.DOWNLOAD_FAILED,
            MainActivity.DOWNLOAD_CANCELLED,
            MainActivity.DOWNLOAD_RESOLVING,
            MainActivity.DOWNLOAD_WAITING,
        )
        for (state in states) {
            assertTrue(
                "状态「$state」在列表行上一个出口都没有 —— 用户只能靠长按多选或全局菜单",
                descriptions(rowButtons(state)).isNotEmpty(),
            )
        }
    }

    // ── 无障碍描述必须说清能做什么 ────────────────────────────────────────

    @Test
    fun failedAndCancelledRows_sayHowToGetOut() {
        for (state in listOf(MainActivity.DOWNLOAD_FAILED, MainActivity.DOWNLOAD_CANCELLED)) {
            val a = activity()
            a.downloadEntries.clear()
            val e = entry("测试文件.bin", state)
            a.downloadEntries.add(e)
            a.showDownloads()
            shadowOf(Looper.getMainLooper()).idle()
            a.renderDownloads("")
            shadowOf(Looper.getMainLooper()).idle()
            val row = a.downloadRows[e]!!
            val desc = row.contentDescription?.toString() ?: ""
            assertTrue("状态「$state」的行描述必须告诉用户还能做什么：实际=$desc", desc.contains("右侧可"))
        }
    }

    // ── 空态两种情形必须说清楚 ────────────────────────────────────────────

    @Test
    fun emptyStates_distinguishNoRecordsFromNoMatches() {
        val a = activity()
        a.downloadEntries.clear()
        a.showDownloads()
        shadowOf(Looper.getMainLooper()).idle()
        a.renderDownloads("")
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(
            "一条记录都没有时要说「暂无下载记录」",
            textsIn(a.downloadList).contains("暂无下载记录"),
        )

        a.downloadEntries.add(entry("有的文件.bin", MainActivity.DOWNLOAD_COMPLETED))
        a.renderDownloads("")
        shadowOf(Looper.getMainLooper()).idle()
        a.renderDownloads("绝对匹配不到的查询词")
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(
            "有记录但筛不出来时要说「没有匹配的下载记录」——不能跟真的一条都没有混为一谈",
            textsIn(a.downloadList).contains("没有匹配的下载记录"),
        )
    }

    private fun textsIn(view: View?, out: MutableList<String> = mutableListOf()): List<String> {
        if (view == null) return out
        if (view is TextView) view.text?.toString()?.let { if (it.isNotEmpty()) out.add(it) }
        if (view is ViewGroup) for (i in 0 until view.childCount) textsIn(view.getChildAt(i), out)
        return out
    }
}
