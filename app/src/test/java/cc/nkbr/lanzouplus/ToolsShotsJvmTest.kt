package cc.nkbr.lanzouplus

import android.graphics.Bitmap
import android.os.Looper
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
 * [DFWX] 手表尺寸视觉验收：Robolectric 原生图形在 JVM 上真实渲染每一页并截成 PNG
 * （D:/heiyao/build_output/tools_shots/），供逐页人工查看适配问题（文字截断/触控目标
 * 过小/溢出）。尺寸=手表：w343dp-h343dp-280dpi（600px@280dpi，与 dfwx_watch AVD 一致）。
 * 结尾 popToolBack 落到工具列表页，一并截图。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w343dp-h343dp-280dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)  // 必须：LEGACY 模式 draw 是空操作，截出来全是同一张垃圾位图
class ToolsShotsJvmTest {

    private fun idle() {
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun shot(activity: MainActivity, name: String) {
        val decor = activity.window.decorView
        var w = decor.width
        var h = decor.height
        if (w <= 0 || h <= 0) { w = 600; h = 600 }
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        decor.draw(android.graphics.Canvas(bmp))
        val out = File("D:/heiyao/build_output/tools_shots/$name.png")
        out.parentFile.mkdirs()
        FileOutputStream(out).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
    }

    @Test fun captureHomeToolsListAndAll35ToolPages() {
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        controller.setup()
        idle()
        val activity = controller.get()
        shot(activity, "00_home")
        for (t in Toolbox.TOOLS) {
            activity.openTool(t[0])
            idle()
            shot(activity, "tool_" + t[0])
            activity.popToolBack()
            idle()
        }
        // 循环结束最后一次 popToolBack 时栈空 → showTools() 落在工具列表页
        shot(activity, "01_tools_list")
        controller.destroy()
    }
}
