package cc.nkbr.lanzouplus

import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * [DFW-97] 「反馈与建议」页的**启动冒烟测试**。
 *
 * 为什么必须有：这是一个**新增的 Activity**，里面有一个 WebView 和一次 assets 读取。
 * 静态守卫（`FeedbackEntryAndSkinJvmTest`）只能证明"代码写对了"，
 * 证明不了"点进去不崩"。新增 Activity 最容易出的两类事故：
 * 1. 忘了注册进 Manifest → `ActivityNotFoundException` 直接崩；
 * 2. `getAssets().open()` 抛异常没接住 → 白屏。
 * 这两类都不会被静态断言抓到，只有真启一次才知道。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class FeedbackPageSmokeJvmTest {

    private fun launch(): FeedbackPage {
        val c = Robolectric.buildActivity(FeedbackPage::class.java)
        c.setup()
        shadowOf(Looper.getMainLooper()).idle()
        return c.get()
    }

    @Test
    fun itLaunchesWithoutCrashingAndShowsItsOwnChrome() {
        val page = launch()
        assertNotNull("Activity 必须真的起来了（Manifest 注册 + onCreate 不抛）", page.web)
        assertNotNull("必须有根布局", page.host)

        val labels = mutableListOf<String>()
        fun walk(v: View) {
            if (v is TextView) labels.add(v.text.toString())
            if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
        }
        walk(page.host)

        assertTrue("顶栏要有标题「反馈与建议」：$labels", labels.contains("反馈与建议"))
        assertTrue("要有返回按钮", labels.contains("‹"))
        assertTrue("感谢提示条要预置好「返回软件」按钮：$labels", labels.contains("返回软件"))
    }

    /**
     * 换肤脚本必须**真的读进来了**。
     * 读不到时 `readAsset` 会退化成空串（页面仍然可用，只是不换肤）——
     * 这个降级是对的，但**必须能被测出来**，否则 assets 改名/漏打包会静默失效。
     */
    @Test
    fun theDarkModeScriptIsActuallyLoadedFromAssets() {
        val page = launch()
        val script = WebDarkMode.script(page)
        assertTrue("暗夜模式脚本不能是空的（空了就说明 assets 没读到或没打包）", script.length > 500)
        assertTrue("读到的必须是暗夜模式脚本本身", script.contains("getComputedStyle"))
    }

    /**
     * [DFW-97 修正] **顶栏不能压到状态栏上。**
     * 用户截图里 FlowUs 的面包屑和系统状态栏叠在一起 —— 根因是 WebView 铺满全屏。
     */
    @Test
    fun theWebViewStartsBelowTheTopBar() {
        val page = launch()
        val webLp = page.web.layoutParams as android.widget.FrameLayout.LayoutParams
        val barLp = page.host.getChildAt(1).layoutParams as android.widget.FrameLayout.LayoutParams
        val expected = page.statusBarInset() + page.dp(48)
        assertEquals(
            "WebView 必须从「状态栏 + 顶栏」之下开始，否则站点内容会顶进状态栏（用户截图里的叠字）",
            expected,
            webLp.topMargin,
        )
        assertEquals("顶栏自己不该再带 topMargin（占位在容器内部）", 0, barLp.topMargin)
    }

    /** 提交成功 → 弹自己的提示条（而不是把第三方英文 toast 甩给用户）。 */
    @Test
    fun theThanksBarOnlyAppearsAfterSubmit() {
        val page = launch()
        assertEquals("一开始不能显示感谢条", View.GONE, page.thanksBar.visibility)
        // 模拟网页回调
        page.runOnUiThread { page.showThanks() }
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("提交后必须显示", View.VISIBLE, page.thanksBar.visibility)

        // 幂等：重复回调不许叠出第二条
        page.runOnUiThread { page.showThanks() }
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("重复回调也只能有一条", View.VISIBLE, page.thanksBar.visibility)
    }

    @Test
    fun theWebViewIsActuallyConfiguredAsExpected() {
        val page = launch()
        val ws = page.web.settings
        assertTrue("FlowUs 是 SPA，必须开 JS", ws.javaScriptEnabled)
        assertTrue("表单状态依赖 localStorage", ws.domStorageEnabled)
        assertTrue("不许读本地文件", !ws.allowFileAccess)
        assertTrue("不许读 content://", !ws.allowContentAccess)
        assertTrue("不许从 file:// 跨源", !ws.allowFileAccessFromFileURLs)
        assertTrue("不许通用跨源访问", !ws.allowUniversalAccessFromFileURLs)
    }
}
