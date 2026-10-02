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
    private val skin by lazy { read("app/src/main/assets/web_dark_mode.js") }
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
            // [DFW-97 修正] 第一版放的是分享页，那是错的：分享页默认开在表格视图，
            // 还要用户自己点下拉切视图，而且带 Preview mode 横条。
            // 用户 2026-10-02 给出正确地址并指出问题。
            "必须指向用户给的那个 form 直链（不是 share 分享页）",
            page.contains("https://flowus.cn/form/511c82ce-70dd-4a74-82c4-12f3a65d6496?code=NDH3Z3"),
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
    /**
     * 守卫本体抽成函数 —— 这样"反向探针"才能**真的把坏代码喂进来**。
     *
     * ⚠️ 这里有一个本项目实际踩过的假绿教训：第一版的反向探针写成
     * `assertTrue("pointer-events: none".contains("pointer-events"))` ——
     * 两个操作数都是字面量，**编译期就恒真**，跟被测代码一点关系都没有。
     * 结果是"守卫配了反向探针"这条检查项看起来已满足，实际零鉴别力。
     * 正确写法见 `RenderFolderNullSafetyJvmTest.theGuardActuallyDetectsAnUnguardedCall`。
     */
    private fun skinTouchesPointerEvents(src: String): Boolean =
        stripJsComments(src).let { it.contains("pointer-events") || it.contains("pointerEvents") }

    private fun skinSetsLayoutProperty(src: String, prop: String): Boolean =
        Regex("style\\.setProperty\\(\\s*[\"']" + prop).containsMatchIn(src)

    /** 剥掉注释再查 —— 文件里解释"为什么不用类名选择器"的那句本身含 `[class*=`。 */
    private fun stripJsComments(src: String): String = src.lines()
        .filterNot {
            val t = it.trimStart()
            t.startsWith("*") || t.startsWith("//") || t.startsWith("/*")
        }
        .joinToString("\n")

    private fun skinUsesClassSelectors(src: String): Boolean =
        Regex("\\[class\\*=").containsMatchIn(stripJsComments(src))

    @Test
    fun theSkinOnlyTouchesColorsAndVisibility() {
        assertTrue("换肤脚本必须存在且非空", skin.length > 500)
        assertFalse(
            "不许碰 pointer-events（碰了用户就点不动表单）—— CSS 与 JS 两种写法都算",
            skinTouchesPointerEvents(skin),
        )
        for (bad in listOf("display", "position", "width", "height", "margin", "padding", "transform")) {
            assertFalse(
                "换肤不许改布局属性 `$bad`（只允许改颜色）",
                skinSetsLayoutProperty(skin, bad),
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
        assertFalse(
            "不许再出现靠类名猜的 CSS 选择器（第一版翻车的原因）",
            skinUsesClassSelectors(skin),
        )
    }

    /**
     * [DFW-97] **所有内置网页都走暗夜模式**（用户 2026-10-02：「我们软件所有网站都打开默认暗夜模式」）。
     *
     * 为什么专门守这条：第一版只改了反馈页，蓝奏云网页页没改 ——
     * 用户看到的就是"有的页面是暗的、有的不是"。两个页面必须共用同一份脚本。
     */
    @Test
    fun everyWebPageGetsTheSameDarkMode() {
        assertTrue(
            "必须有共享的暗夜模式工具（否则两个页面各写一套，改一处忘一处）",
            File(root, "app/src/main/java/cc/nkbr/lanzouplus/WebDarkMode.java").isFile,
        )
        val helper = read("app/src/main/java/cc/nkbr/lanzouplus/WebDarkMode.java")
        assertTrue("脚本文件名必须只有一处硬编码", helper.contains("web_dark_mode.js"))

        val lanzou = read("app/src/main/java/cc/nkbr/lanzouplus/LanzouWebActivity.java")
        assertTrue("蓝奏云网页页必须也注入暗夜模式", lanzou.contains("WebDarkMode.apply("))
        assertTrue("反馈页必须也注入暗夜模式", page.contains("WebDarkMode.apply("))

        assertTrue(
            "注入必须发生在 onPageFinished（页面每次加载完都要来一次）",
            lanzou.contains("onPageFinished") && lanzou.indexOf("WebDarkMode.apply(") > lanzou.indexOf("onPageFinished"),
        )
        assertTrue(
            "读不到脚本时必须退化成「不换肤」而不是抛异常（绝不能因此白屏）",
            helper.contains("cached = \"\"") && helper.contains("catch (Exception ignored)"),
        )
    }

    /**
     * [DFW-97 修正] **顶栏不能和状态栏打架。**
     *
     * 用户 2026-10-02 的截图里，FlowUs 自己的面包屑和系统状态栏图标叠在一起，
     * 因为第一版把 WebView 铺满全屏、顶栏只是浮在上面 —— 站点内容就画到状态栏里去了。
     * 现在：顶栏容器自带状态栏占位，WebView 从「状态栏 + 顶栏」之下开始。
     */
    @Test
    fun theTopBarDoesNotCollideWithTheStatusBar() {
        assertTrue("顶栏必须有状态栏占位", page.contains("statusBarInset()"));
        assertTrue("WebView 必须下移到顶栏之下", page.contains("FrameLayout.LayoutParams webParams()"))
        assertTrue(
            "下移量必须等于「状态栏 + 顶栏」",
            page.contains("lp.topMargin = statusBarInset() + dp(48);"),
        )
        assertFalse(
            "顶栏参数里不该再单独加 topMargin（占位已经进到容器里了，重复加会顶两次）",
            Regex("topBarParams[\\s\\S]{0,200}topMargin").containsMatchIn(page),
        )
    }

    /**
     * 反向探针：**把坏代码真的喂进守卫函数**，确认它会变红。
     *
     * 这一版是重写的 —— 原来那版是字面量自比，恒真，等于没验（见上面 `skinTouchesPointerEvents` 的注释）。
     */
    @Test
    fun theChecksActuallyDetectBadCode() {
        // ① 碰 pointer-events：CSS 与 JS 两种写法都必须被抓
        assertTrue(
            "坏代码（CSS 写法）必须被守卫抓出来",
            skinTouchesPointerEvents("html { pointer-events: none !important; }"),
        )
        assertTrue(
            "坏代码（JS 写法）必须被守卫抓出来",
            skinTouchesPointerEvents("el.style.pointerEvents = 'none';"),
        )
        assertFalse("干净代码不该被误伤", skinTouchesPointerEvents("el.style.color = '#fff';"))
        assertFalse(
            "注释里写「绝不碰 pointer-events」不该被误判（脚本头部的铁律说明里就有这句，本次实际踩到）",
            skinTouchesPointerEvents(" * 2. **绝不碰 pointer-events** —— 碰了用户就点不动页面。"),
        )

        // ② 改布局属性必须被抓
        assertTrue(
            "坏代码（改 display）必须被抓出来",
            skinSetsLayoutProperty("""el.style.setProperty("display","none")""", "display"),
        )
        assertTrue(
            "坏代码（改 margin）必须被抓出来",
            skinSetsLayoutProperty("""el.style.setProperty('margin','0')""", "margin"),
        )
        assertFalse(
            "改颜色不该被误伤",
            skinSetsLayoutProperty("""el.style.setProperty("color","#fff")""", "display"),
        )

        // ③ 靠类名猜选择器必须被抓，但**注释里的解释文字不算**
        assertTrue(
            "坏代码（类名选择器）必须被抓出来",
            skinUsesClassSelectors("""[class*="banner"] { display: none }"""),
        )
        assertFalse(
            "注释里解释「为什么不用类名选择器」不该被误判成坏代码（本次实际踩到过）",
            skinUsesClassSelectors(" * 靠 `[class*=\"banner\"]` 这种猜法迟早失效"),
        )

        // ④ 锚点必须真的在源码里，否则上面全在自说自话
        assertTrue("锚点必须与源码逐字一致", skin.contains("getComputedStyle"))
    }
}
