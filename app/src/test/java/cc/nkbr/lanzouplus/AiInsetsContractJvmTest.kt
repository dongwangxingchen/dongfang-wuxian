package cc.nkbr.lanzouplus

import android.graphics.Insets
import android.os.Looper
import android.view.WindowInsets
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
import java.io.File

/**
 * [DFW-2 / UI-005] AI 内嵌页的 insets 契约守卫。
 *
 * ## 用户报告的现象
 * > "下面那个输入框没问题了，但是点击后，顶部的那个一大排会错位。"
 *
 * ## 已经查清的契约（宿主侧）
 * `MainActivity.installSystemNavigationInsets()` 把 **statusBars / navigationBars / displayCutout 全部裁成 NONE**
 * 再派发给子级，顶部留白由 `host.paddingTop` 承担；**ime 不裁**（AI 输入框的 `imePadding` 靠它）。
 * 既然宿主已经把系统栏吃掉了，Compose 侧**必须**把 `TopAppBar` 自己的 `windowInsets` 清零，
 * 否则 M3 默认的 `TopAppBarDefaults.windowInsets` 会在某些帧再垫一个状态栏高度 → 顶栏下移再弹回。
 *
 * ## 为什么用源码级断言
 * 这是**逐帧**问题，静态截图和 Robolectric 都复现不了抖动本身；
 * 但"契约的两端有没有被谁单方面改掉"是可以在源码层钉死的，而这类改动正是回归的唯一来源
 * （改一个不留神，真机上又要等用户报一次）。所以这里守的是**契约**，不是像素。
 *
 * ⚠️ 本测试**不能**替代真机确认：键盘弹出/收起的全过程顶栏不跳、不抖、不残留错位，仍需用户实测。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class AiInsetsContractJvmTest {

    companion object {
        @BeforeClass @JvmStatic fun silenceAutoImport() { MainActivity.LIBRARY_AUTO_IMPORT = false }

        private val repoRoot: File = run {
            var dir = File(System.getProperty("user.dir") ?: ".").absoluteFile
            while (!File(dir, "app/src/main/AndroidManifest.xml").isFile && dir.parentFile != null) dir = dir.parentFile!!
            dir
        }
    }

    private fun source(path: String) = File(repoRoot, path).readText(Charsets.UTF_8)

    private fun activity(): MainActivity {
        val a = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        shadowOf(Looper.getMainLooper()).idle()
        return a
    }

    private fun insets(statusTop: Int, navBottom: Int, imeBottom: Int): WindowInsets =
        WindowInsets.Builder()
            .setInsets(WindowInsets.Type.statusBars(), Insets.of(0, statusTop, 0, 0))
            .setInsets(WindowInsets.Type.navigationBars(), Insets.of(0, 0, 0, navBottom))
            .setInsets(WindowInsets.Type.displayCutout(), Insets.NONE)
            .setInsets(WindowInsets.Type.ime(), Insets.of(0, 0, 0, imeBottom))
            .build()

    // ── 契约两端 ──────────────────────────────────────────────────────────

    @Test
    fun composeTopBar_clearsItsOwnWindowInsets() {
        val chatPage = source("rikkahub/app/src/main/java/me/rerere/rikkahub/ui/pages/chat/ChatPage.kt")
        val topAppBarAt = chatPage.indexOf("TopAppBar(")
        assertTrue("ChatPage 里必须还有 TopAppBar（找不到说明文件结构变了，本守卫要跟着改）", topAppBarAt > 0)
        val block = chatPage.substring(topAppBarAt, minOf(chatPage.length, topAppBarAt + 2500))
        assertTrue(
            "宿主已经把 statusBars/navigationBars 裁成 NONE 并用 host.paddingTop 承担顶部留白，" +
                "Compose 顶栏**必须**把 windowInsets 清零，否则 M3 默认值会在某些帧再垫一个状态栏高度 → 顶栏错位。",
            block.contains("windowInsets = WindowInsets(0, 0, 0, 0)"),
        )
    }

    @Test
    fun host_clipsSystemBarsButKeepsIme() {
        val src = source("app/src/main/java/cc/nkbr/lanzouplus/MainActivity.java")
        assertTrue(
            "宿主必须把 statusBars 裁成 NONE（顶部留白只由 host.paddingTop 提供）",
            src.contains("setInsets(android.view.WindowInsets.Type.statusBars(),none)"),
        )
        assertTrue(
            "宿主必须把 navigationBars 裁成 NONE",
            src.contains("setInsets(android.view.WindowInsets.Type.navigationBars(),none)"),
        )
        assertTrue(
            "宿主必须把 displayCutout 裁成 NONE（否则刘海屏会双计）",
            src.contains("setInsets(android.view.WindowInsets.Type.displayCutout(),none)"),
        )
        assertFalse(
            "**ime 绝不能裁**：AI 输入框的 imePadding 全靠它浮到键盘上方（v1.22.8 输入框崩坏就是这条被绕过）",
            src.contains("setInsets(android.view.WindowInsets.Type.ime(),none)"),
        )
    }

    @Test
    fun host_imeRecomputeStaysPureGeometry() {
        // 红线（PATCHES.md v1.22.8 教训）：这里只能做纯几何换算，
        // 一旦有人再叠自研动画/缓存/计数器，就会与系统逐帧派发竞态，输入框再次崩坏。
        val src = source("app/src/main/java/cc/nkbr/lanzouplus/MainActivity.java")
        val start = src.indexOf("android.view.View aiComposeView(){")
        assertTrue("找不到 aiComposeView()，本守卫要跟着改", start > 0)
        val block = src.substring(start, minOf(src.length, start + 4200))
        // 只看**代码行**：注释里正解释着"当年就是用 ValueAnimator 崩的"，不能把注释也算成违规。
        val code = block.lines()
            .filterNot { it.trimStart().startsWith("//") || it.trimStart().startsWith("*") || it.trimStart().startsWith("/*") }
            .joinToString("\n")
        for (forbidden in listOf("ValueAnimator", "WindowInsetsAnimation", "stickyBelow", "postOnAnimation")) {
            assertFalse(
                "AI 页 insets 处理里不许再出现「$forbidden」（v1.22.8 自研动画事故红线）",
                code.contains(forbidden),
            )
        }
        assertTrue("必须保留纯几何换算：target = ime.bottom - host.getPaddingBottom()", code.contains("ime.bottom-below"))
    }

    // ── 行为：键盘弹出前后 host 的顶部留白必须恒定 ────────────────────────

    @Test
    fun hostPaddingTop_staysConstantWhenKeyboardComesUp() {
        val a = activity()
        val host = a.host
        assertNotNull("宿主容器必须已经建好", host)

        // 键盘收起
        host.dispatchApplyWindowInsets(insets(statusTop = 96, navBottom = 132, imeBottom = 0))
        shadowOf(Looper.getMainLooper()).idle()
        val topBefore = host.paddingTop
        val bottomBefore = host.paddingBottom

        // 键盘弹出（statusBars 在部分 ROM 上会重派发，这里连它一起变）
        host.dispatchApplyWindowInsets(insets(statusTop = 96, navBottom = 132, imeBottom = 780))
        shadowOf(Looper.getMainLooper()).idle()
        val topAfter = host.paddingTop
        val bottomAfter = host.paddingBottom

        assertTrue("顶部留白必须来自状态栏（96dp→px 应 > 0），实际 $topBefore", topBefore > 0)
        assertEquals(
            "键盘弹出**不许**改变 host 的顶部留白：顶栏错位的根因就是顶部留白在动画期变了",
            topBefore, topAfter,
        )
        assertEquals(
            "键盘弹出**不许**改变 host 的底部留白（ime 由子级自己消费）",
            bottomBefore, bottomAfter,
        )
    }

    @Test
    fun hostPadding_usesTheLargerOfNavigationBarAndCutout() {
        val a = activity()
        val host = a.host
        // 导航条 132，刘海底部 200 —— 必须取大的那个，否则刘海屏底部会被压
        val withCutout = WindowInsets.Builder()
            .setInsets(WindowInsets.Type.statusBars(), Insets.of(0, 96, 0, 0))
            .setInsets(WindowInsets.Type.navigationBars(), Insets.of(0, 0, 0, 132))
            .setInsets(WindowInsets.Type.displayCutout(), Insets.of(0, 0, 0, 200))
            .build()
        host.dispatchApplyWindowInsets(withCutout)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(
            "底部留白必须取导航条与刘海的较大值，实际 ${host.paddingBottom}",
            host.paddingBottom >= 200,
        )
    }
}
