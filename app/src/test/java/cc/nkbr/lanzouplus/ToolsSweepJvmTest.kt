package cc.nkbr.lanzouplus

import android.os.Looper
import android.view.View
import android.view.ViewGroup
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * [DFWX] 工具箱 35 页 JVM 点击级回归（Robolectric 启动真实 MainActivity 逐个打开工具页再返回）。
 *
 * 环境说明：启动链的 QuickJS 原生库在 JVM 上必然 UnsatisfiedLinkError → App.onCreate 捕获置
 * DEGRADED（AI 模块停用），工具箱是纯 Java UI 完全不受影响——等价真机上"AI 初始化失败保主功能"
 * 的产品形态。尺寸模拟手表：w343dp-h343dp-280dpi（600px@280dpi，与 dfwx_watch AVD 一致）。
 *
 * v1.17.3：除崩溃外还断言【无横向溢出】——radix 页 4 个按钮曾挤出一行，第 4 个完全在屏外
 * 不可点（目检截图才发现）。此处程序化遍历视图树兜住同类回归：任何子视图超出根宽度即失败。
 * 折叠线以下的行目检看不到，这条断言是唯一覆盖。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w343dp-h343dp-280dpi")
class ToolsSweepJvmTest {

    companion object {
        // v1.19.0：内置清单 85 源，不静默的话每次启动 MainActivity 都会触发批量导入的真实网络探测
        @BeforeClass @JvmStatic fun silenceAutoImport() { MainActivity.LIBRARY_AUTO_IMPORT = false }
    }

    private fun collectOverflows(root: View, rootWidth: Int, out: StringBuilder) {
        if (root.width > 0 && root.right > rootWidth + 2) {
            out.append("  越界: ").append(root.javaClass.simpleName)
            out.append(" right=").append(root.right).append('/').append(rootWidth)
            val desc = root.contentDescription
            if (desc != null) out.append(" desc=").append(desc)
            out.append('\n')
        }
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) collectOverflows(root.getChildAt(i), rootWidth, out)
        }
    }

    @Test fun all35ToolPages_renderPopBackAndNoHorizontalOverflow() {
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        controller.setup()
        shadowOf(Looper.getMainLooper()).idle()
        val activity = controller.get()
        val rootWidth = activity.window.decorView.width

        val problems = StringBuilder()
        for (t in Toolbox.TOOLS) {
            val id = t[0]
            val name = t[1]
            try {
                activity.openTool(id)
                shadowOf(Looper.getMainLooper()).idle()
                val overflows = StringBuilder()
                collectOverflows(activity.window.decorView, rootWidth, overflows)
                if (overflows.isNotEmpty()) {
                    problems.append(name).append('/').append(id).append(" 横向溢出：\n").append(overflows)
                }
            } catch (e: Throwable) {
                problems.append("openTool(").append(name).append('/').append(id).append(") → ").append(e).append('\n')
                break // UI 树已破，后续结果不可信，先修第一个
            }
            try {
                activity.popToolBack()
                shadowOf(Looper.getMainLooper()).idle()
            } catch (e: Throwable) {
                problems.append("popToolBack(").append(name).append('/').append(id).append(") → ").append(e).append('\n')
                break
            }
        }
        controller.destroy()
        if (problems.isNotEmpty()) {
            throw AssertionError("工具页存在崩溃或子视图越出屏幕右缘（手表 343dp 下不可点/不可见）：\n$problems")
        }
    }
}
