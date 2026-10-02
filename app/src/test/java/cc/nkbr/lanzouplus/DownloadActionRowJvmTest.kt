package cc.nkbr.lanzouplus

import android.app.AlertDialog
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import java.io.File

/**
 * [DFW-113] 下载页全局操作行回归（2026-10-02 重做）。
 *
 * 改前：这排是 5 个光秃秃的图标（用户反馈「下载UI界面有点丑」），
 * 且「清除保存的分享密码」错用了复制图标 ic_copy。
 * 改后：图标 + 文字标签胶囊，低频删除类操作收进「更多」菜单。
 *
 * 这些用例断言的是**真实视图树状态**（可见性 / 胶囊文案 / 菜单内容），不是源码字符串：
 * 每个用例都用同一段产品代码喂两种输入（空 vs 有记录、暂停 vs 下载中），
 * 所以任何一条失效都意味着状态机真的坏了。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-560dpi")
class DownloadActionRowJvmTest {

    companion object {
        @BeforeClass @JvmStatic fun silenceAutoImport() { MainActivity.LIBRARY_AUTO_IMPORT = false }

        private val repoRoot: File = run {
            var dir = File(System.getProperty("user.dir") ?: ".").absoluteFile
            while (!File(dir, "app/src/main/AndroidManifest.xml").isFile && dir.parentFile != null) dir = dir.parentFile!!
            dir
        }
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun entry(name: String, state: String, percent: Int = 0): MainActivity.DownloadEntry {
        val e = MainActivity.DownloadEntry()
        e.name = name
        e.state = state
        e.percent = percent
        e.totalBytes = 1_000_000
        e.downloadedBytes = percent * 10_000L
        return e
    }

    /** 胶囊文案 = 第 2 个子 View（第 1 个是图标 ImageView）。 */
    private fun chipLabel(chip: LinearLayout?): String {
        requireNotNull(chip) { "胶囊不存在" }
        assertTrue("胶囊应当是「图标 + 文字」两个子 View，实际 ${chip.childCount}", chip.childCount >= 2)
        return (chip.getChildAt(1) as TextView).text.toString()
    }

    /** 操作行上真正可见的胶囊文案（隐藏的不算）。 */
    private fun visibleChipLabels(a: MainActivity): List<String> {
        val row = a.downloadActionRowView ?: return emptyList()
        if (row.visibility != View.VISIBLE) return emptyList()
        val out = mutableListOf<String>()
        val strip = ((row.getChildAt(0) as ViewGroup).getChildAt(0) as ViewGroup)
        for (i in 0 until strip.childCount) {
            val chip = strip.getChildAt(i)
            if (chip.visibility == View.VISIBLE) out.add(chipLabel(chip as LinearLayout))
        }
        return out
    }

    private fun texts(view: View?, out: MutableList<String> = mutableListOf()): List<String> {
        if (view == null) return out
        if (view is TextView) view.text?.toString()?.let { if (it.isNotEmpty()) out.add(it) }
        if (view is ViewGroup) for (i in 0 until view.childCount) texts(view.getChildAt(i), out)
        return out
    }

    @Test
    fun actionRowFollowsDownloadState() {
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        controller.setup()
        idle()
        val a = controller.get()

        // ① 空历史：整行不出现（暂停/取消都没有对象，更多里的删除两项也是空操作）
        a.showDownloads()
        idle()
        assertEquals("空历史时不该摆出一排点了没反应的按钮", View.GONE, a.downloadActionRowView.visibility)

        // ② 有进行中的任务：三个带文字的胶囊，文案各不相同
        a.downloadEntries.add(entry("无限画质增强器_v2.3.1.apk", MainActivity.DOWNLOAD_RUNNING, 47))
        a.showDownloads()
        idle()
        assertEquals(View.VISIBLE, a.downloadActionRowView.visibility)
        assertEquals(listOf("暂停全部", "取消全部", "更多"), visibleChipLabels(a))

        // ③ 全部已暂停：左胶囊必须改口叫「继续全部」——它点下去执行的是 resumeAllPaused。
        //    改前的实现把标签写死成「暂停全部」，点下去却是继续，属于「标签与动作不符」。
        for (e in a.downloadEntries) e.state = MainActivity.DOWNLOAD_PAUSED
        a.showDownloads()
        idle()
        assertEquals(listOf("继续全部", "取消全部", "更多"), visibleChipLabels(a))
        assertEquals("继续全部", chipLabel(a.downloadPauseControlButton))

        // ④ 全部结束：暂停/取消都该消失，只留「更多」；文案要能和左边的区分开
        for (e in a.downloadEntries) e.state = MainActivity.DOWNLOAD_COMPLETED
        a.showDownloads()
        idle()
        assertEquals(View.VISIBLE, a.downloadActionRowView.visibility)
        assertEquals(listOf("更多"), visibleChipLabels(a))

        // ⑤ 回到有进行中：胶囊必须回来（证明 ④ 是状态驱动而不是一次性隐藏）
        a.downloadEntries.add(entry("新任务.zip", MainActivity.DOWNLOAD_RUNNING, 3))
        a.showDownloads()
        idle()
        assertEquals(listOf("暂停全部", "取消全部", "更多"), visibleChipLabels(a))

        controller.destroy()
    }

    @Test
    fun maintenanceMenuCarriesTheThreeCleanupActions() {
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        controller.setup()
        idle()
        val a = controller.get()
        a.showDownloads()
        idle()
        a.showDownloadMaintenanceMenu()
        idle()

        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        assertTrue("「更多」菜单必须真的弹出来", dialog.isShowing)
        val labels = texts(dialog.window!!.decorView)
        for (expected in listOf("删除全部记录", "删除全部文件", "清除保存的分享密码")) {
            assertTrue("菜单里必须有「$expected」这一行：$labels", labels.contains(expected))
        }
        controller.destroy()
    }

    /**
     * 语义守卫：分享密码清理入口必须挂在钥匙图标上。
     * 这是源码级守卫（图标选择没法从视图树反查资源 id），但它有真实失败模式——
     * 谁把这一项改回 ic_copy 就立刻红。
     */
    @Test
    fun passwordCleanupIsWiredToTheKeyIconNotTheCopyIcon() {
        val source = File(repoRoot, "app/src/main/java/cc/nkbr/lanzouplus/MainActivity.java").readText()
        assertTrue(
            "清除分享密码必须挂在钥匙图标上",
            source.contains("settingsAction(R.drawable.ic_key,\"清除保存的分享密码\""),
        )
        assertFalse(
            "清除分享密码不许再用复制图标 ic_copy",
            source.contains("R.drawable.ic_copy,\"清除历史里保存的分享密码\""),
        )
    }
}
