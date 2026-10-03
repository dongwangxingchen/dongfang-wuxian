package cc.nkbr.lanzouplus

import android.app.AlertDialog
import android.os.Looper
import android.view.View
import android.view.ViewGroup
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
 * [DFW-113 / DFW-114] 下载页全局操作的回归。
 *
 * ## 这一轮（2026-10-03）发生了什么
 * 全局操作原来在列表上方**独立一行**（两颗胶囊：暂停全部 / 取消全部）。那一行有三个毛病：
 *   ① 只在有任务时出现 → 列表跟着上下跳 44dp；
 *   ② 只有两颗胶囊还右对齐 → 左边一大片空，看着像掉在那儿（用户：「错位了」）；
 *   ③ 顶部因此多出**第 5 条横杠**（有任务时 236dp）。
 * 现在整行搬进页头 ⋯ 菜单，顶部**恒定 4 条、不再随任务增减跳动**。
 *
 * ## 这些用例守什么
 * 断言的是**真实视图树状态**（可见性 / 菜单里到底有没有那几项），不是源码字符串 ——
 * 每条都用同一段产品代码喂两种输入（空 vs 有记录、暂停 vs 下载中），
 * 所以任何一条失效都意味着状态机真的坏了。
 *
 * ⚠️ 这一条**必须真的点开菜单再查**：只断言 ⋯ 按钮存在是不够的 ——
 * 按钮在、点击没接上，用户就再也暂停不了、也删不掉记录。
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

    private fun texts(view: View?, out: MutableList<String> = mutableListOf()): List<String> {
        if (view == null) return out
        if (view is TextView) view.text?.toString()?.let { if (it.isNotEmpty()) out.add(it) }
        if (view is ViewGroup) for (i in 0 until view.childCount) texts(view.getChildAt(i), out)
        return out
    }

    /** 打开页头 ⋯ 菜单并返回里面所有文字。 */
    private fun openHeaderMenu(a: MainActivity): List<String> {
        assertEquals("页头 ⋯ 必须先可见才谈得上打开", View.VISIBLE, a.downloadHeaderMenuButton.visibility)
        a.downloadHeaderMenuButton.performClick()
        idle()
        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        assertTrue("点页头 ⋯ 必须真的弹出菜单", dialog.isShowing)
        return texts(dialog.window!!.decorView)
    }

    /**
     * 空历史 → ⋯ 隐藏；有记录 → ⋯ 出现。
     *
     * 沿用本项目一贯的「不摆一颗点不动的按钮」原则：菜单里的项在零记录时全是空操作。
     */
    @Test
    fun headerMenuHidesItselfWhenThereIsNothingToActOn() {
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        controller.setup(); idle()
        val a = controller.get()

        a.showDownloads(); idle()
        assertEquals("空历史时 ⋯ 必须隐藏（里面每一项都是空操作）", View.GONE, a.downloadHeaderMenuButton.visibility)

        a.downloadEntries.add(entry("测试包.zip", MainActivity.DOWNLOAD_COMPLETED, 100))
        a.showDownloads(); idle()
        assertEquals("有记录时 ⋯ 必须出现", View.VISIBLE, a.downloadHeaderMenuButton.visibility)

        controller.destroy()
    }

    /**
     * **有在跑的任务 → 菜单里必须有「暂停全部」和「取消全部」。**
     *
     * 这是本轮入口迁移的核心：这两个动作原来在列表上方一行，现在只能从 ⋯ 进。
     * 搬丢了就是用户**再也暂停不了下载**。
     */
    @Test
    fun runningDownloadPutsPauseAndCancelIntoTheHeaderMenu() {
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        controller.setup(); idle()
        val a = controller.get()
        a.downloadEntries.add(entry("无限画质增强器_v2.3.1.apk", MainActivity.DOWNLOAD_RUNNING, 47))
        a.showDownloads(); idle()

        val labels = openHeaderMenu(a)
        assertTrue("有任务在跑时菜单里必须有「暂停全部」：$labels", labels.contains("暂停全部"))
        assertTrue("有任务在跑时菜单里必须有「取消全部」：$labels", labels.contains("取消全部"))

        controller.destroy()
    }

    /**
     * **全部已暂停 → 菜单里给的是「继续全部」而不是「暂停全部」。**
     *
     * 真实失败模式：暂停全部是个**双向开关**（`togglePauseAllActive`：有进行中就全暂停，否则全继续）。
     * 把标签写死成「暂停全部」，在"全部已暂停"状态下点下去其实是继续 —— 标签与动作不符。
     */
    @Test
    fun pausedDownloadOffersResumeInsteadOfPause() {
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        controller.setup(); idle()
        val a = controller.get()
        a.downloadEntries.add(entry("已暂停的包.zip", MainActivity.DOWNLOAD_PAUSED, 30))
        a.showDownloads(); idle()

        val labels = openHeaderMenu(a)
        assertTrue("全部已暂停时菜单里必须是「继续全部」：$labels", labels.contains("继续全部"))
        assertFalse("全部已暂停时不该再出现「暂停全部」：$labels", labels.contains("暂停全部"))
        assertFalse(
            "没有在跑的任务时不该出现「取消全部」（点了没反应的对象）：$labels",
            labels.contains("取消全部"),
        )

        controller.destroy()
    }

    /** 删除类清理入口必须一直在（不管有没有任务在跑）。 */
    @Test
    fun maintenanceMenuCarriesTheThreeCleanupActions() {
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        controller.setup(); idle()
        val a = controller.get()
        a.downloadEntries.add(entry("测试包.zip", MainActivity.DOWNLOAD_COMPLETED, 100))
        a.showDownloads(); idle()

        val labels = openHeaderMenu(a)
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

    /**
     * **源码级守卫：列表上方那第 5 条横杠不许回来。**
     *
     * 这一轮把它整行搬进 ⋯ 菜单，顶部因此**恒定 4 条、不再随任务增减跳动**（有任务时省下 44dp）。
     * 光靠人记着是记不住的 —— 谁哪天觉得"暂停全部放外面更方便"再把它加回 `showDownloads`，
     * 用户就会重新看到那个"左边一大片空、还跟上一行贴在一起"的怪形态。
     */
    @Test
    fun theFourthBarAboveTheListMustNotComeBack() {
        val source = File(repoRoot, "app/src/main/java/cc/nkbr/lanzouplus/MainActivity.java").readText()
        assertFalse(
            "showDownloads 里不许再把操作行加进页面 —— 全局操作现在全在页头 ⋯ 菜单里",
            source.contains("root.addView(downloadActionRow()"),
        )
        assertFalse(
            "操作行的构造函数也不该再存在（搬进菜单后它就是死代码）",
            source.contains("LinearLayout downloadActionRow()"),
        )
    }
}
