package cc.nkbr.lanzouplus

import android.os.Looper
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

/**
 * [DFW-97] 设置页「反馈与建议」入口 + FlowUs 反馈页换肤。
 *
 * ## 用户原话（2026-10-02）
 * > 「设置页加一个『反馈与建议』的入口，点进去打开 flowus 那个反馈表，
 * > 但是**它的风格和我的软件不一样，你得统一一下**。」
 *
 * ## 换肤这件事本身踩过一次坑，所以守卫写得细
 * 第一版用的是**类名选择器**（`[class*="banner"]` 那一套）。实测直接翻车：
 * 把 `body` 背景刷黑、文字刷成浅色，但真正承载白底的是**内层容器** ——
 * 结果变成"白底浅字"，**比不换肤还糟**。
 *
 * 最终改成"遍历 DOM 读计算样式、按明度重映射"，与类名无关。
 * 下面几条守卫钉的就是那次教训：
 * 1. 不许再出现靠类名猜的选择器；
 * 2. 不许碰 `pointer-events`（碰了用户就点不动表单）；
 * 3. 不许改布局属性（`display` 除外——那是用来隐藏 FlowUs 自己的顶栏/底栏的）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class FeedbackEntryAndSkinJvmTest {

    companion object {
        @BeforeClass @JvmStatic fun silence() { MainActivity.LIBRARY_AUTO_IMPORT = false }
    }

    private val root: File = run {
        var dir = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (!File(dir, "AGENTS.md").isFile && dir.parentFile != null) dir = dir.parentFile
        dir
    }

    private fun read(rel: String) = File(root, rel).readText(Charsets.UTF_8)

    private val main by lazy { read("app/src/main/java/cc/nkbr/lanzouplus/MainActivity.java") }
    private val page by lazy { read("app/src/main/java/cc/nkbr/lanzouplus/FeedbackPage.java") }
    private val skin by lazy { read("app/src/main/assets/feedback_skin.js") }
    private val manifest by lazy { read("app/src/main/AndroidManifest.xml") }

    @Test
    fun settingsPageHasTheFeedbackEntryAboveCheckUpdate() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        shadowOf(Looper.getMainLooper()).idle()
        activity.showSettings()
        shadowOf(Looper.getMainLooper()).idle()

        val labels = mutableListOf<String>()
        fun walk(v: android.view.View) {
            if (v is android.widget.TextView) labels.add(v.text.toString())
            if (v is android.view.ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
        }
        walk(activity.root)

        assertTrue("设置页必须有「反馈与建议」入口：$labels", labels.any { it == "反馈与建议" })
        assertTrue("「检查更新」入口必须还在（DFW-7 的核心出路）：$labels", labels.any { it.startsWith("检查更新") })
        assertTrue(
            "「反馈与建议」必须排在「检查更新」前面（用户指定位置）",
            labels.indexOf("反馈与建议") < labels.indexOfFirst { it.startsWith("检查更新") },
        )
    }

    @Test
    fun thePageIsRegisteredAndOpensTheRealForm() {
        assertTrue(
            "FeedbackPage 必须注册进 Manifest（否则 startActivity 直接崩）",
            manifest.contains("android:name=\".FeedbackPage\""),
        )
        assertTrue("必须是 exported=false（只有 App 自己能开）", manifest.contains(".FeedbackPage\" android:exported=\"false\""))
        assertTrue(
            "设置项必须真的跳转过去",
            main.contains("startActivity(new Intent(this,FeedbackPage.class))"),
        )
        assertTrue(
            "必须指向用户确认的那个反馈表地址",
            page.contains("https://flowus.cn/dongfang/share/d0183611-c568-4d63-98ed-f28b81dff202"),
        )
    }

    /** WebView 必须显式钉死安全基线（与 LanzouWebActivity 同款，DFW-10）。 */
    @Test
    fun theWebViewIsHardened() {
        for (need in listOf(
            "setAllowFileAccess(false)",
            "setAllowContentAccess(false)",
            "setAllowFileAccessFromFileURLs(false)",
            "setAllowUniversalAccessFromFileURLs(false)",
            "setGeolocationEnabled(false)",
            "MIXED_CONTENT_NEVER_ALLOW",
        )) {
            assertTrue("WebView 安全基线缺了 $need", page.contains(need))
        }
        assertTrue("JS 是必须的（FlowUs 是 SPA）", page.contains("setJavaScriptEnabled(true)"))
        assertTrue(
            "暴露给网页的桥必须只有一个「提交成功」信号，不许有读写能力",
            page.contains("@JavascriptInterface public void onSubmitted()"),
        )
    }

    /**
     * 换肤的**三条铁律**：只改颜色与可见性，绝不改布局、绝不改 `pointer-events`。
     * 改布局会让 FlowUs 自己的表单逻辑错位，改 `pointer-events` 会让用户点不动。
     */
    @Test
    fun theSkinOnlyTouchesColorsAndVisibility() {
        assertTrue("换肤脚本必须存在且非空", skin.length > 500)
        assertFalse(
            "不许碰 pointer-events（碰了用户就点不动表单）—— CSS 与 JS 两种写法都算",
            skin.contains("pointer-events") || skin.contains("pointerEvents"),
        )
        for (bad in listOf("display", "position", "width", "height", "margin", "padding", "transform")) {
            assertFalse(
                "换肤不许改布局属性 `$bad`（只允许改颜色）",
                Regex("style\\.setProperty\\(\\s*[\"']" + bad).containsMatchIn(skin),
            )
        }
        assertTrue("必须真的在重映射背景色", skin.contains("background-color"))
        assertTrue("必须真的在重映射文字色", skin.contains("color"))
    }

    /**
     * 换肤必须**按计算样式重映射**，不许退回"靠类名猜选择器"。
     * 第一版就是这么翻车的：把 body 刷黑、文字刷浅，但白底在内层容器上 → 白底浅字，比不换肤还糟。
     */
    @Test
    fun theSkinRemapsByComputedStyleNotByClassNames() {
        assertTrue("必须读计算样式", skin.contains("getComputedStyle"))
        assertTrue("必须有明度计算", skin.contains("lum"))
        assertTrue(
            "必须用 MutationObserver 跟上动态内容（FlowUs 是 SPA，内容随时重建）",
            skin.contains("MutationObserver"),
        )
        // 先剥掉注释再查 —— 文件里那句"为什么不用类名选择器"的解释本身含 `[class*=`，
        // 不剥注释的话守卫会命中自己的说明文字（本次实际踩到）。
        val code = skin.lines()
            .filterNot { it.trimStart().startsWith("*") || it.trimStart().startsWith("//") || it.trimStart().startsWith("/*") }
            .joinToString("\n")
        assertFalse(
            "不许再出现靠类名猜的 CSS 选择器（第一版翻车的原因）",
            Regex("\\[class\\*=").containsMatchIn(code),
        )
    }

    /** 预览模式横条只能"找到了才点"，找不到就算了 —— 不许乱点。 */
    @Test
    fun previewModeIsSkippedOnlyWhenActuallyFound() {
        assertTrue("必须识别预览模式横条", skin.contains("preview mode"))
        assertTrue(
            "必须先确认元素真实可见（宽高都大于 0）再点，避免点空气",
            skin.contains("getBoundingClientRect") && skin.contains("r.width <= 0"),
        )
        assertTrue(
            "提交成功要回调 App，让 App 弹自己的提示而不是甩第三方英文 toast",
            skin.contains("DFWX") && skin.contains("onSubmitted"),
        )
    }

    /** 反向探针：确认上面的检查真的能识别坏代码。 */
    @Test
    fun theChecksActuallyDetectBadCode() {
        // 两种写法都要能被识别（CSS 里是 `pointer-events`，JS 里是 `pointerEvents`）
        assertTrue("坏代码（CSS 写法）必须被识别", "pointer-events: none".contains("pointer-events"))
        assertTrue("坏代码（JS 写法）必须被识别", "el.style.pointerEvents='none'".contains("pointerEvents"))
        assertTrue(
            "坏代码（改布局属性）必须被识别",
            Regex("style\\.setProperty\\(\\s*[\"']" + "display").containsMatchIn("""el.style.setProperty("display","none")"""),
        )
        assertTrue(
            "坏代码（靠类名猜选择器）必须被识别",
            Regex("\\[class\\*=").containsMatchIn("""[class*="banner"] { display:none }"""),
        )
        assertTrue("锚点必须真的在源码里", skin.contains("getComputedStyle"))
    }
}
