package cc.nkbr.lanzouplus

import android.net.Uri
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.ImageButton
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * [DFW-97] 反馈页的**冒烟测试** —— 只验"用户真的能用"，不验我自己的装饰。
 *
 * ## 为什么必须有这一层
 * 静态守卫（`FeedbackEntryAndSkinJvmTest`）只能证明"代码写对了"，
 * 证明不了"点进去不崩"。Activity 最容易出两类事故：
 * ① 忘了注册进 Manifest → `ActivityNotFoundException` 直接崩；
 * ② `getAssets().open()` 抛异常没接住 → 白屏。
 * 这两类都只有**真启一次**才知道。
 *
 * ## 用户 2026-10-02 定调
 * > 「去掉没用的乱七八糟的吧，只留下网页和暗夜模式……提交成功后就提示用户提交完成，
 * >   请留意邮箱。返回到主页面。」
 * 所以这一版测试也只盯四件事：网页在、暗夜模式在、返回键在、上传能用。
 * **不再测顶栏/感谢条** —— 那些东西已经被删了，测试跟着删，
 * 不留"测一个已删功能"的空壳（那种测试要么编译不过、要么恒绿，都是负担）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class FeedbackPageSmokeJvmTest {

    companion object {
        @BeforeClass
        @JvmStatic
        fun silenceAutoImport() {
            MainActivity.LIBRARY_AUTO_IMPORT = false
        }
    }

    private fun launch(): FeedbackPage {
        val page = Robolectric.buildActivity(FeedbackPage::class.java).setup().get()
        shadowOf(Looper.getMainLooper()).idle()
        return page
    }

    private fun findWebView(root: View): WebView? {
        if (root is WebView) return root
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                findWebView(root.getChildAt(i))?.let { return it }
            }
        }
        return null
    }

    private fun collect(root: View, predicate: (View) -> Boolean): List<View> {
        val out = mutableListOf<View>()
        if (predicate(root)) out.add(root)
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) out.addAll(collect(root.getChildAt(i), predicate))
        }
        return out
    }

    /** ① 点进去不崩，而且真的在加载那张表。 */
    @Test
    fun itLaunchesAndLoadsTheRealForm() {
        val page = launch()
        val web = findWebView(page.window.decorView)
        assertNotNull("页面上必须有一个 WebView，否则「只留下网页」无从谈起", web)
        assertEquals(
            "必须加载用户给的 form 直链（不是 share 分享页）",
            "https://flowus.cn/form/511c82ce-70dd-4a74-82c4-12f3a65d6496?code=NDH3Z3",
            web!!.url,
        )
    }

    /** ② 暗夜模式脚本真的从 assets 读进来了（读不到会静默退化成"不换肤"）。 */
    @Test
    fun theDarkModeScriptIsActuallyLoadedFromAssets() {
        val script = WebDarkMode.script(ApplicationProvider.getApplicationContext())
        assertTrue("脚本不能是空的（空了说明 assets 没读到或没打包）", script.length > 500)
        assertTrue("读到的必须是暗夜模式脚本本身", script.contains("getComputedStyle"))
    }

    /** ③ 返回键在，而且是全站约定的矢量图标 + 有无障碍描述。 */
    @Test
    fun theBackButtonIsThereAndAccessible() {
        val page = launch()
        val buttons = collect(page.window.decorView) { it is ImageButton }
        assertTrue("必须有一个返回按钮，否则用户出不去", buttons.isNotEmpty())
        assertTrue(
            "返回按钮必须带无障碍描述",
            buttons.any { it.contentDescription?.toString() == "返回" },
        )
    }

    /**
     * ④ **上传文件必须能弹选择器。**
     *
     * 这是用户实测的 bug：「上传文件点不动」。
     * 根因是 `WebChromeClient` 的 `onShowFileChooser` **默认实现什么都不做** ——
     * 不实现它，网页里的 `<input type="file">` 被点了就是毫无反应、也不报错。
     * 这条测试直接把回调打进去，断言它返回 true（= 我真的处理了）。
     */
    @Test
    fun tappingUploadActuallyOpensAFileChooser() {
        val page = launch()
        val web = findWebView(page.window.decorView)!!
        val chrome = web.webChromeClient
        assertNotNull("必须设置 WebChromeClient，否则连回调都不会来", chrome)

        // 用匿名对象而不是 SAM lambda：`ValueCallback<Uri[]>` 在 Kotlin 里是
        // `ValueCallback<Array<Uri>>`，空 lambda 会被推断成 `() -> Unit`，编译不过。
        val callback = object : ValueCallback<Array<Uri>> {
            override fun onReceiveValue(value: Array<Uri>?) { }
        }
        val params = object : WebChromeClient.FileChooserParams() {
            override fun isCaptureEnabled() = false
            override fun getAcceptTypes() = arrayOf("*/*")
            override fun getMode() = WebChromeClient.FileChooserParams.MODE_OPEN
            override fun getFilenameHint() = null
            override fun getTitle(): CharSequence? = null
            override fun createIntent() =
                android.content.Intent(android.content.Intent.ACTION_GET_CONTENT)
        }
        assertTrue(
            "onShowFileChooser 必须被真的实现并返回 true —— 否则用户点上传就是没反应",
            chrome!!.onShowFileChooser(web, callback, params),
        )
    }

    /** ⑤ WebView 该关的都关了、该开的开了 —— 安全基线与上传必需项都在。 */
    @Test
    fun theWebViewIsConfiguredAsExpected() {
        val page = launch()
        val ws = findWebView(page.window.decorView)!!.settings
        assertTrue("FlowUs 是 SPA，必须开 JS", ws.javaScriptEnabled)
        assertTrue("表单状态依赖 localStorage", ws.domStorageEnabled)
        assertFalse("网页不许直接读设备文件系统", ws.allowFileAccess)
        assertTrue(
            "必须允许 content:// —— 文件选择器返回的就是它，关掉会导致选完文件没反应",
            ws.allowContentAccess,
        )
    }
}
