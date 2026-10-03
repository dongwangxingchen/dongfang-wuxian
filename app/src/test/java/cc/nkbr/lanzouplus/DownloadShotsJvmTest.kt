package cc.nkbr.lanzouplus

import android.graphics.Bitmap
import android.os.Looper
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/**
 * [DFWX] 下载界面第二轮重做（DFW-114）的**视觉取证**。
 *
 * 为什么要有这个文件：几何断言能证明"宽度 ≥48dp""盒子没溢出"，但证明不了"看起来对不对"。
 * 本轮改的全是观感（触控目标、卡片圆角、胶囊形、空态、层级），**必须让人真的看一眼**。
 * 用户原话：「让我可以看到一个全新的、非常完美的、非常适合我的界面」——
 * 那就把渲染结果直接落成 PNG 给他看，而不是拿数字说服他。
 *
 * 输出：`~/heiyao/build_output/phone_shots/3x_download_*.png`
 * 两个宽度各截一遍：**360dp（用户真机可能的窄屏）** 与 411dp（项目一直用的基准）。
 *
 * `@GraphicsMode(NATIVE)` 是必须的：LEGACY 模式下 `draw()` 是空操作，截出来全是同一张垃圾位图
 * （`HomeShotsJvmTest` 里已经写明这个坑）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DownloadShotsJvmTest {

    companion object {
        @BeforeClass @JvmStatic fun silenceAutoImport() { MainActivity.LIBRARY_AUTO_IMPORT = false }
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun shot(activity: MainActivity, name: String) {
        val decor = activity.window.decorView
        var w = decor.width
        var h = decor.height
        if (w <= 0 || h <= 0) { w = 1260; h = 2800 }
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        decor.draw(android.graphics.Canvas(bmp))
        val out = File(System.getProperty("user.home") + "/heiyao/build_output/phone_shots/$name.png")
        out.parentFile.mkdirs()
        FileOutputStream(out).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
        println("[shot] $name → ${out.absolutePath} (${w}x${h})")
    }

    private fun entry(name: String, state: String, percent: Int = 0): MainActivity.DownloadEntry {
        val e = MainActivity.DownloadEntry()
        e.name = name
        e.state = state
        e.percent = percent
        e.totalBytes = 15_200_000L
        e.downloadedBytes = 15_200_000L * percent / 100
        return e
    }

    /** 三种状态的混合列表 —— 一眼能看出卡片面、图标底、进度条、动作按钮的层级对不对。 */
    private fun seed(activity: MainActivity) {
        activity.downloadEntries.add(entry("东方无限-v1.0.0.apk", MainActivity.DOWNLOAD_COMPLETED, 100))
        activity.downloadEntries.add(entry("一个名字很长很长很长很长很长很长很长的压缩包.zip", MainActivity.DOWNLOAD_RUNNING, 47))
        activity.downloadEntries.add(entry("下载失败的文件.pdf", MainActivity.DOWNLOAD_FAILED, 12))
        activity.downloadEntries.add(entry("排队等待中.mp4", MainActivity.DOWNLOAD_WAITING, 0))
    }

    /** 空态：本轮把它从「一行灰字」改成「图标 + 标题 + 下一步」。 */
    @Test
    @Config(qualifiers = "w360dp-h800dp-560dpi")
    fun captureEmptyStateAt360dp() {
        val c = Robolectric.buildActivity(MainActivity::class.java); c.setup(); idle()
        val a = c.get()
        a.showDownloads(); idle()
        shot(a, "30_download_empty_360dp")
        c.destroy()
    }

    /** 有记录：卡片质感、触控目标、层级、长文件名截断。 */
    @Test
    @Config(qualifiers = "w360dp-h800dp-560dpi")
    fun capturePopulatedListAt360dp() {
        val c = Robolectric.buildActivity(MainActivity::class.java); c.setup(); idle()
        val a = c.get()
        seed(a)
        a.showDownloads(); idle()
        shot(a, "31_download_list_360dp")
        c.destroy()
    }

    /** 同一个页面在项目基准宽度（411dp）下的样子，用来和 360dp 对照。 */
    @Test
    @Config(qualifiers = "w411dp-h891dp-420dpi")
    fun capturePopulatedListAt411dp() {
        val c = Robolectric.buildActivity(MainActivity::class.java); c.setup(); idle()
        val a = c.get()
        seed(a)
        a.showDownloads(); idle()
        shot(a, "32_download_list_411dp")
        c.destroy()
    }

    /** 多选模式：复选框 + 底部选择条，确认没被这轮尺寸改动挤坏。 */
    @Test
    @Config(qualifiers = "w360dp-h800dp-560dpi")
    fun captureSelectionModeAt360dp() {
        val c = Robolectric.buildActivity(MainActivity::class.java); c.setup(); idle()
        val a = c.get()
        seed(a)
        a.showDownloads(); idle()
        a.enterDownloadSelection()
        a.toggleDownloadSelection(a.downloadEntries[0])
        idle()
        shot(a, "33_download_selection_360dp")
        c.destroy()
    }
}
