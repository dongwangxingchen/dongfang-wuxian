package cc.nkbr.lanzouplus

import android.os.Build
import android.os.Looper
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
 * [DFWX] DFW-17 预测性返回（侧滑预览）回归。
 *
 * ## 先纠正卡片的前提（重要）
 * 卡片引用的注释原文"OnBackInvokedCallback 会禁用系统预返回动画**（v1.19.x 起就一直是这样，
 * 所以用户从没见过预览效果）**"——**这段描述的是已被修掉的旧状态**。同一段注释的
 * 后半句写的才是现状：v1.22.1 已改为官方路线
 * （`OnBackPressedDispatcher` + `androidx.activity.OnBackAnimationCallback`，
 * 实现了 handleOnBackStarted/Progressed/Cancelled），并且**根页面禁用 callback**
 * 让系统"回桌面"预览自动出现（`syncBackCallbackEnabled()` ← `canHandleBack()`）。
 * 所以"全站不可用"与事实不符，MainActivity 侧是通的。
 *
 * ## 本轮真正修的是另一处
 * `LanzouWebActivity` 里有一个**无条件注册的裸 OnBackInvokedCallback**——
 * 而"注册了回调"本身就等于告诉系统"本页自己处理返回"，系统因此**不再播放预览**。
 * 已改为按 `web.canGoBack()` 条件注册：还能后退时自己处理；到历史起点就注销、交回系统。
 *
 * ## 还修了一个隐藏 bug
 * 原来用 `this::handleBack` 注册。方法引用每次求值都产生**新对象**，
 * 拿新对象去 unregister 注销不掉旧的那个（MainActivity 的 v1.22.1 注释记着同一个坑）。
 * 现在保存单一实例 `backCallback` 再注册/注销。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class PredictiveBackJvmTest {

    companion object {
        @BeforeClass @JvmStatic fun silenceAutoImport() { MainActivity.LIBRARY_AUTO_IMPORT = false }
    }

    private fun mainActivity(): MainActivity {
        val a = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        shadowOf(Looper.getMainLooper()).idle()
        return a
    }

    // ---------- MainActivity：现状确实是通的（顺手加回归，防退化） ----------

    /**
     * [DFW-73] 这条以前断言"根页面不接管返回，让系统播回桌面预览"。
     * 2026-10-01 用户改了口径：顶级页要**先提示「再返回一次退出软件」、第二次才退出**，
     * 所以返回键必须由应用自己吃掉（交还系统 = 立刻 finish，横幅没机会出现）。
     * 代价是根页面不再有系统预览；应用内跟手预览也一并关掉，避免"缩一下又弹回去"。
     */
    @Test
    fun home_doesInterceptBack_butSuppressesTheInAppPreview() {
        val a = mainActivity()
        a.showHomeLanding()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(
            "顶级页必须自己接管返回，否则「再返回一次退出软件」没机会出现",
            a.canHandleBack(),
        )
        assertFalse(
            "顶级根不做跟手预览（页面本来就不会离开）",
            a.predictiveBackPreviewAllowed(),
        )
    }

    @Test
    fun subPages_doInterceptBack() {
        val a = mainActivity()
        a.showSettings()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue("子页必须接管返回（否则会直接退出应用）", a.canHandleBack())
    }

    /** MainActivity 必须走 androidx 的动画回调（能收 started/progressed/cancelled），而不是裸平台回调。 */
    @Test
    fun mainActivityUsesAnimationCallback_implementingPreviewHooks() {
        val a = mainActivity()
        assertNotNull("必须安装 backCallback", a.backCallback)
        // 反射确认覆写了预测性返回的三个钩子（androix OnBackPressedCallback 的动画扩展）
        val cls = a.backCallback!!.javaClass
        val methods = cls.declaredMethods.map { it.name }.toSet()
        for (hook in listOf("handleOnBackStarted", "handleOnBackProgressed", "handleOnBackCancelled")) {
            assertTrue(
                "backCallback 必须实现 $hook（否则拿不到跟手进度、无法做预览）：实际方法=$methods",
                methods.contains(hook),
            )
        }
    }

    @Test
    fun manifest_enablesOnBackInvokedCallback() {
        // 没有这个开关，Android 13+ 上根本不会走新返回路径、也不会有预测性返回
        val manifest = java.io.File(
            (System.getProperty("user.dir") ?: ".").let { dir ->
                var d = java.io.File(dir).absoluteFile
                while (!java.io.File(d, "src/main/AndroidManifest.xml").isFile && d.parentFile != null) d = d.parentFile
                d
            },
            "src/main/AndroidManifest.xml",
        ).readText(Charsets.UTF_8)
        assertTrue(
            "MainActivity 必须声明 enableOnBackInvokedCallback=true，否则预测性返回不会启用",
            manifest.contains("android:enableOnBackInvokedCallback=\"true\""),
        )
    }

    // ---------- LanzouWebActivity：本轮修的地方 ----------

    @Test
    fun webActivity_usesSingleReusableCallbackInstance() {
        // 反射确认 backCallback 是 final 字段（单一实例）——方法引用每次新建对象的坑
        val field = LanzouWebActivity::class.java.getDeclaredField("backCallback")
        assertTrue("backCallback 必须是 final 单一实例（方法引用每次新建对象会导致注销无效）",
            java.lang.reflect.Modifier.isFinal(field.modifiers))
        assertEquals(
            "backCallback 类型必须是平台 OnBackInvokedCallback",
            android.window.OnBackInvokedCallback::class.java,
            field.type,
        )
    }

    @Test
    fun webActivity_registersCallbackConditionally_notUnconditionally() {
        val source = java.io.File(
            (System.getProperty("user.dir") ?: ".").let { dir ->
                var d = java.io.File(dir).absoluteFile
                while (!java.io.File(d, "src/main/java/cc/nkbr/lanzouplus/LanzouWebActivity.java").isFile && d.parentFile != null) d = d.parentFile
                d
            },
            "src/main/java/cc/nkbr/lanzouplus/LanzouWebActivity.java",
        ).readText(Charsets.UTF_8)

        assertTrue("必须存在按网页历史条件同步的方法", source.contains("void syncBackCallback()"))
        assertTrue("必须能在到历史起点时注销回调（否则系统永远不播预览）",
            source.contains("unregisterOnBackInvokedCallback"))
        assertTrue("注册时必须复用同一个 callback 实例", source.contains("registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT,backCallback)"))
        assertTrue("注销时必须用同一个实例", source.contains("unregisterOnBackInvokedCallback(backCallback)"))
        assertFalse(
            "不得再出现无条件注册裸回调的写法（那正是禁用预览的原因）",
            source.contains("this::handleBack);"),
        )
    }

    @Test
    fun webActivity_syncsOnPageFinished() {
        val source = java.io.File(
            (System.getProperty("user.dir") ?: ".").let { dir ->
                var d = java.io.File(dir).absoluteFile
                while (!java.io.File(d, "src/main/java/cc/nkbr/lanzouplus/LanzouWebActivity.java").isFile && d.parentFile != null) d = d.parentFile
                d
            },
            "src/main/java/cc/nkbr/lanzouplus/LanzouWebActivity.java",
        ).readText(Charsets.UTF_8)
        assertTrue(
            "每次网页加载完成后都要重新同步回调状态（网页历史的可后退性会随加载变化）",
            source.contains("onPageFinished") && source.contains("syncBackCallback();"),
        )
    }
}
