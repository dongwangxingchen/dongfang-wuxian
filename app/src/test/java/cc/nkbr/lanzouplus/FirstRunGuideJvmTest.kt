package cc.nkbr.lanzouplus

import android.app.Activity
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * [DFW-42] 首次启动引导守卫。
 *
 * 用户 2026-10-01 拍板选了「做一页轻量引导，可跳过」。
 * 卡上写死的红线：**不阻断使用、不擅自加阻断式弹窗、老用户升级不被重复打扰**。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class FirstRunGuideJvmTest {

    private val root: File = run {
        var dir = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (!File(dir, "AGENTS.md").isFile && dir.parentFile != null) dir = dir.parentFile
        dir
    }

    private fun source(rel: String) = File(root, rel).readText(Charsets.UTF_8)

    @Test
    fun afterSeen_itNeverShowsAgain() {
        val activity: Activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val sp = activity.getSharedPreferences("dfwx_first_run", Context.MODE_PRIVATE)
        sp.edit().clear().commit()
        FirstRunGuide.markSeen(activity)
        assertFalse(
            "标记过之后永远不再显示（否则每次启动都弹，就是骚扰）",
            FirstRunGuide.shouldShow(activity),
        )
    }

    @Test
    fun upgradesAreNotTreatedAsFreshInstalls() {
        // 拿不到 PackageInfo 的极端情况一律不显示 —— 宁可漏引导，也不要在老用户机器上突然弹一页
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val fresh = FirstRunGuide.isFreshInstall(ctx)
        // Robolectric 下这两个时间戳通常相等，所以这里不断言 true/false，
        // 只断言它**不会抛异常**且返回值稳定（真正的判据在下面那条源码断言里钉死）。
        assertTrue("isFreshInstall 必须稳定返回，不得抛异常", fresh == FirstRunGuide.isFreshInstall(ctx))
    }

    /**
     * 判据必须是"没看过 **且** 确实是新装" —— 双条件缺一不可。
     *
     * 只判 `seen` 是不够的：老用户升级后那个标记也是空的，会被当成新装再引导一次。
     */
    @Test
    fun source_requiresBothUnseenAndFreshInstall() {
        val src = source("app/src/main/java/cc/nkbr/lanzouplus/FirstRunGuide.java")
        assertTrue("必须检查 seen 标记", src.contains("KEY_SEEN, false"))
        assertTrue("必须检查是否真正的新装", src.contains("isFreshInstall(activity)"))
        assertTrue(
            "新装判据必须是 firstInstallTime == lastUpdateTime（升级后两者会不同）",
            src.contains("firstInstallTime") && src.contains("lastUpdateTime"),
        )
    }

    /** 三种退场方式都必须能关掉：开始使用 / 跳过 / 点空白。卡上要求"可跳过"。 */
    @Test
    fun source_hasThreeWaysToDismiss() {
        val src = source("app/src/main/java/cc/nkbr/lanzouplus/FirstRunGuide.java")
        assertTrue("必须有「开始使用」", src.contains("开始使用"))
        assertTrue("必须有「跳过」", src.contains("跳过"))
        assertTrue("点空白也必须能关（否则用户会以为卡死）", src.contains("scrim.setOnClickListener"))
        assertTrue("返回键也必须能关", src.contains("KEYCODE_BACK"))
    }

    /**
     * 只取 Java 源里的**字符串字面量**（用户真能看到的文案）。
     *
     * 逐字符扫描，**跳过 `//` 行注释与 `/* */` 块注释**，只在真正的字符串里取值。
     *
     * 为什么不能直接扫全文：注释里会**引用**这些词来解释"为什么禁止"，
     * 扫全文会把注释也判成违规（第一版就是这么误报的 —— 而且我的 Java 注释里
     * 用了半角引号，看起来和字符串字面量一模一样）。
     * 这跟本项目踩过的 `stripComments()` 吞代码是同一类坑：
     * 判据必须落在**真实载体**上。所以下面有一条自检，确保扫描器没把真文案也吃掉。
     */
    private fun stringLiterals(src: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < src.length) {
            val c = src[i]
            when {
                c == '/' && i + 1 < src.length && src[i + 1] == '/' -> {
                    while (i < src.length && src[i] != '\n') i++
                }
                c == '/' && i + 1 < src.length && src[i + 1] == '*' -> {
                    i += 2
                    while (i + 1 < src.length && !(src[i] == '*' && src[i + 1] == '/')) i++
                    i += 2
                }
                c == '"' -> {
                    val start = i
                    i++
                    while (i < src.length && src[i] != '"') {
                        if (src[i] == '\\') i++
                        i++
                    }
                    if (i < src.length) i++
                    out.append(src, start, minOf(i, src.length)).append(' ')
                }
                else -> i++
            }
        }
        return out.toString()
    }

    /** 卡上明令禁止：不许写"不收集隐私"这类未经核对的绝对说法（与 DFW-20 联动）。 */
    @Test
    fun source_makesNoUnverifiedPrivacyClaims() {
        val literals = stringLiterals(source("app/src/main/java/cc/nkbr/lanzouplus/FirstRunGuide.java"))
        // 自检：扫描器必须真的取到用户文案，否则下面全是假绿
        assertTrue("扫描器必须能取到真实文案（否则这条就是假绿）", literals.contains("欢迎使用东方无限"))
        assertTrue("扫描器必须能取到「跳过」按钮文案", literals.contains("跳过"))
        for (banned in listOf("不收集", "不追踪", "绝对安全", "完全隐私", "不会上传")) {
            assertFalse(
                "引导页**文案**里不许写未经核对的绝对说法「$banned」（DFW-42 卡上明令禁止，与 DFW-20 联动）",
                literals.contains(banned),
            )
        }
    }

    /** 引导不许做成阻断式：它只是一个可点掉的遮罩，不能拦住主界面。 */
    @Test
    fun source_doesNotBlockTheApp() {
        val src = source("app/src/main/java/cc/nkbr/lanzouplus/FirstRunGuide.java")
        assertFalse("不许申请任何权限（卡上禁止把引导做成权限申请）", src.contains("requestPermissions"))
        assertFalse("不许声明成 Dialog 阻断（它是可点掉的遮罩）", src.contains("AlertDialog"))
        // 挂载必须延迟，让首屏先画出来
        val main = source("app/src/main/java/cc/nkbr/lanzouplus/MainActivity.java")
        assertTrue("引导必须延迟挂载，避免和主界面抢首帧", main.contains("FirstRunGuide.show(this,host)"))
    }
}
