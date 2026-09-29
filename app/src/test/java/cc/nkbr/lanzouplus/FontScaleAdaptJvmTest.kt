package cc.nkbr.lanzouplus

import android.content.res.Configuration
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * [DFWX] DFW-13 字体缩放适配回归。
 *
 * ## 取证结论（这轮最重要的产出，先说清楚测试为什么这样写）
 *
 * 卡片假设的失效机制"sp 文字放进固定 dp 容器被裁切"，机制本身成立，
 * 但**在 JVM 里无法用文本度量来验证**：实测 Robolectric 的字体度量是桩实现——
 * `Paint.textSize=70f` 时 `measureText("中")` 返回 **1.0px**，`StaticLayout.lineCount` 恒为 1。
 * （仓库里早有一条同源记录：`SettingsStructureTest.java:20` 写着"native canvas 无 CJK 字形输出"。）
 * 因此任何"量文字宽度判断截断"的诊断都会得出**零问题**的假结论。
 *
 * ## 所以本测试断言的是**不依赖字形的因果链**
 *
 * 不测"文字会不会被裁"（JVM 测不准），而测"**装文字的盒子有没有跟着字体一起变大**"：
 * 这是可精确断言的数值关系，也是修复的真正全部内容。
 * 只要盒子随 fontScale 等比放大，文字变大多少、空间就多出多少，比例不变 → 不会裁切。
 *
 * 真机截图对照仍按卡片要求保留为最终验收（本机 adb 未连手机）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class FontScaleAdaptJvmTest {

    companion object {
        @BeforeClass @JvmStatic fun silenceAutoImport() { MainActivity.LIBRARY_AUTO_IMPORT = false }
    }

    private fun setFontScale(scale: Float) {
        val app = RuntimeEnvironment.getApplication()
        val config = Configuration(app.resources.configuration)
        config.fontScale = scale
        @Suppress("DEPRECATION")
        app.resources.updateConfiguration(config, app.resources.displayMetrics)
    }

    private fun withScale(scale: Float, block: (MainActivity) -> Unit) {
        setFontScale(scale)
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        controller.setup()
        shadowOf(Looper.getMainLooper()).idle()
        try {
            block(controller.get())
        } finally {
            controller.destroy()
        }
    }

    /** 前置事实：证明 JVM 的字体度量确实是桩，所以别的测试才不会误用文本宽度。 */
    @Test
    fun robolectricTextMetricsAreStubbed_soWeMustNotMeasureGlyphs() {
        val paint = android.graphics.Paint().apply { textSize = 70f }
        val measured = paint.measureText("中")
        assertTrue(
            "若这条断言失败，说明 Robolectric 的字体度量已变成真实实现——" +
                "那就可以改用文本宽度做更直接的裁剪断言（结果=$measured）。",
            measured <= 2f,
        )
    }

    @Test
    fun textContainers_scaleWithFontScale() {
        // dpText 是修复的核心：高度必须随 fontScale 增长
        withScale(1.0f) { a ->
            val normal = a.dpText(56)
            withScale(2.0f) { b ->
                val big = b.dpText(56)
                assertTrue(
                    "字体放大一倍后，文字容器高度必须显著变大（原 ${normal}px → 现 ${big}px）",
                    big > normal * 1.5,
                )
            }
        }
    }

    @Test
    fun dpStaysIndependentOfFontScale_spacingMustNotInflate() {
        // 与文字无关的尺寸（间距/图标）不能跟着放大，否则界面被整体拉肿
        withScale(1.0f) { a ->
            val normal = a.dp(56)
            withScale(2.0f) { b ->
                assertEquals("dp() 必须与字体缩放无关（间距/图标不该被拉肿）", normal, b.dp(56))
            }
        }
    }

    @Test
    fun dpTextIsCapped_soExtremeScaleDoesNotExplodeLayout() {
        withScale(4.0f) { a ->
            val capped = a.dpText(100)
            val atTwo = a.dp(100) * 1.8f
            assertTrue(
                "极端 fontScale 必须有上限（1.8 倍），否则一屏放不下一行（实际 ${capped}px）",
                capped <= atTwo + 2,
            )
        }
    }

    /** 条目卡片：整体高度与标题/副标题容器都必须用 dpText，而不是写死 dp。 */
    @Test
    fun gridItemCard_usesFontScaledHeights() {
        withScale(1.0f) { a ->
            val item = Models.Item().apply {
                title = "一个很长的软件名称示例"
                url = "https://wwc.lanzouw.com/iFont"
            }
            val smallRow = a.itemRow(item)
            val smallParam = a.itemLayout(0, 2).height
            val smallSettings = a.settingsRowHeight()

            withScale(2.0f) { b ->
                val bigRow = b.itemRow(item)
                val bigParam = b.itemLayout(0, 2).height
                val bigSettings = b.settingsRowHeight()
                assertTrue("卡片整体高度必须随字体变大：$smallParam → $bigParam", bigParam > smallParam)
                assertTrue("设置行高必须随字体变大：$smallSettings → $bigSettings", bigSettings > smallSettings)
                // 标题容器（卡片内那个 15sp×2 行的盒子）必须跟着变大
                assertTrue(
                    "标题容器高度必须随字体变大（原 ${titleBoxHeight(smallRow)}px → 现 ${titleBoxHeight(bigRow)}px）",
                    titleBoxHeight(bigRow) > titleBoxHeight(smallRow),
                )
            }
        }
    }

    /** 找到卡片里那个"最多两行标题"的 TextView 的容器高度。 */
    private fun titleBoxHeight(row: View): Int {
        var found = -1
        fun walk(v: View) {
            if (v is android.widget.TextView && v.maxLines == 2 && found < 0) {
                found = v.layoutParams?.height ?: -1
            }
            if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
        }
        walk(row)
        return found
    }

    /** 页面在 fontScale 2.0 下仍能整体渲染（不抛异常、有内容）。 */
    @Test
    fun keyPagesStillRenderAtLargeFontScale() {
        withScale(2.0f) { a ->
            a.showSettings()
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue("设置页在大字体下必须仍有内容", a.root.childCount > 0)

            a.showTools()
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue("工具箱在大字体下必须仍有内容", a.root.childCount > 0)

            a.showCrashLogPage()
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue("崩溃日志页在大字体下必须仍有内容", a.root.childCount > 0)
        }
    }
}
