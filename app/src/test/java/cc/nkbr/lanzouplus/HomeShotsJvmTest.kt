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
 * [DFWX] 手机尺寸视觉验收（v1.19.2 UI 修复回归）：Robolectric 原生图形在 JVM 上真实渲染
 * 首页落位 / 搜索历史 / 搜索结果三态并截成 PNG（~/heiyao/build_output/phone_shots/）。
 * 尺寸=iQOO Neo 10：w448dp-h996dp-450dpi（硬件 6.78" 2800×1260 1.5K，450/160=2.8125 缩放）。
 * 手表尺寸同类测试见 ToolsShotsJvmTest（w343dp）。
 * 修复点回归目标：顶部留白上限 64dp、chips 横带随搜索位移不重叠、列表高度统一、结果卡 104dp 不裁切。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-560dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)  // 必须：LEGACY 模式 draw 是空操作，截出来全是同一张垃圾位图
class HomeShotsJvmTest {

    companion object {
        // v1.19.0：内置清单 85 源，不静默的话每次启动 MainActivity 都会触发批量导入的真实网络探测
        @BeforeClass @JvmStatic fun silenceAutoImport() { MainActivity.LIBRARY_AUTO_IMPORT = false }
    }

    private fun idle() {
        shadowOf(Looper.getMainLooper()).idle()
    }

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
    }

    /** JVM 测试库为空（core.recommendations() 查库），种两个假库让 chips 横带真实出现——重叠修复必须带横带验证 */
    private fun seedLibraries(activity: MainActivity) {
        for (name in listOf("精选软件库", "安卓软件库")) {
            val s = Models.Source()
            s.title = name
            s.url = "https://example.com/$name"
            activity.libraries.add(s)
        }
    }

    @Test fun captureHomeAndSearchStates() {
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        controller.setup()
        idle()
        val activity = controller.get()
        seedLibraries(activity)
        activity.showHomeLanding()
        idle()
        shot(activity, "00_home_landing")
        activity.showHomeSearchMode(false)
        idle()
        shot(activity, "01_search_history")
        activity.runSearch("测试")
        idle()
        shot(activity, "02_search_results")
        controller.destroy()
    }

    /** v1.22.3 边距取证：首页库胶囊 + 设置页左右留白（用户反馈"左右黑边像遮挡按钮"）。
     *  用同一 1260×2801 尺寸渲染，供像素测量与真机截图对照。 */
    @Test fun captureMarginsForAudit() {
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        controller.setup()
        idle()
        val activity = controller.get()
        seedLibraries(activity)
        activity.showHomeLanding()
        idle()
        shot(activity, "10_margin_home")
        activity.showSettings()
        idle()
        shot(activity, "11_margin_settings")
        controller.destroy()
    }

    /** v1.22.3 全页边距审计：五个主页面全部按真机 360dp 渲染，供统一边距规范比对
     *  （首页 18dp / 设置 24dp / 其余页面各自 padding 不一致 = 用户反馈"奇怪的边距"）。 */
    @Test fun captureAllPagesForMarginAudit() {
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        controller.setup()
        idle()
        val activity = controller.get()
        seedLibraries(activity)
        activity.showHomeLanding(); idle(); shot(activity, "20_page_home")
        activity.showSources(); idle(); shot(activity, "21_page_sources")
        activity.showDownloads(); idle(); shot(activity, "22_page_downloads")
        activity.showTools(); idle(); shot(activity, "23_page_tools")
        activity.showSettings(); idle(); shot(activity, "24_page_settings")
        controller.destroy()
    }

    /**
     * [DFW-114 2026-10-03] 下载界面第二轮重做的**视觉取证**。
     *
     * 为什么要有这几张：几何断言能证明「宽度 ≥48dp」「盒子没溢出」，证明不了「看起来对不对」。
     * 本轮改的全是观感（触控目标、卡片面、图标底、胶囊形、空态、层级），**必须让人真的看一眼**。
     *
     * ⚠️ 这几张**故意放在本类里，不另开新类**。踩过的坑（2026-10-03 实测）：
     * 另开一个带 `@GraphicsMode(NATIVE)` 的测试类，会让 `forkEvery = 24` 的分叉分组改变，
     * 结果 `IntegrityPayShineJvmTest > unpaidButtonHasNoSweep` 开始报
     * `java.nio.file.FileSystemAlreadyExistsException`（android-all 的 ZipFileSystem 被重复打开）。
     * 实测因果：新类移出去 → 全量 901 条 0 失败；放回来 → 1 条红，两次完全一致（确定性）。
     * 所以**不要为了加截图而新增 GraphicsMode 类**，加到已有类里。
     */
    @Test fun captureDownloadStatesForAudit() {
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        controller.setup()
        idle()
        val activity = controller.get()

        fun entry(name: String, state: String, percent: Int): MainActivity.DownloadEntry {
            val e = MainActivity.DownloadEntry()
            e.name = name
            e.state = state
            e.percent = percent
            e.totalBytes = 15_200_000L
            e.downloadedBytes = 15_200_000L * percent / 100
            return e
        }

        // 空态：本轮把它从「一行灰字」改成「图标 + 标题 + 下一步」。
        activity.showDownloads(); idle()
        shot(activity, "30_download_empty")

        activity.downloadEntries.add(entry("东方无限-v1.0.0.apk", MainActivity.DOWNLOAD_COMPLETED, 100))
        activity.downloadEntries.add(entry("一个名字很长很长很长很长很长很长很长的压缩包.zip", MainActivity.DOWNLOAD_RUNNING, 47))
        activity.downloadEntries.add(entry("下载失败的文件.pdf", MainActivity.DOWNLOAD_FAILED, 12))
        activity.downloadEntries.add(entry("排队等待中.mp4", MainActivity.DOWNLOAD_WAITING, 0))
        activity.renderDownloads(""); idle()
        shot(activity, "31_download_list")

        // 多选：本轮修掉「动作按钮和复选框挤在一行」之后的样子。
        activity.enterDownloadSelection()
        activity.toggleDownloadSelection(activity.downloadEntries[0])
        idle()
        shot(activity, "32_download_selection")

        controller.destroy()
    }
}
