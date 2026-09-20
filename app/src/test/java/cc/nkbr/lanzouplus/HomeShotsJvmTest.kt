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
 * 尺寸=主流手机：w412dp-h915dp-420dpi。手表尺寸同类测试见 ToolsShotsJvmTest（w343dp）。
 * 修复点回归目标：顶部留白上限 64dp、chips 横带随搜索位移不重叠、列表高度统一、结果卡 104dp 不裁切。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-420dpi")
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
        if (w <= 0 || h <= 0) { w = 1082; h = 2402 }
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
}
