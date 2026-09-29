package cc.nkbr.lanzouplus

import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
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
 * [DFWX] 崩溃日志页两条真机反馈的守卫（用户 2026-09-29 第二轮）：
 *
 * ① "我授予权限之后，我的 MT 管理器里面并没有看到东方无限为名的文件夹"
 *    —— 目录建完必须能被文件管理器看见，而不是只在本进程里存在。
 * ② "打开界面先看到顶部 4 个按钮，不到一秒瞬间闪一下，然后看到崩溃日志的顶部"
 *    —— 根因：正文设了 setTextIsSelectable(true)，它同时在触摸模式下可获焦，
 *    系统在页面铺开后把焦点自动给了正文，ScrollView 遂把正文滚到顶（实测 scrollY 0→756），
 *    于是顶部操作卡被滚出屏幕。滚动容器必须自己占住触摸模式焦点当锚点。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class CrashLogPageJvmTest {

    companion object {
        @BeforeClass @JvmStatic fun silenceAutoImport() { MainActivity.LIBRARY_AUTO_IMPORT = false }
    }

    private fun firstTouchModeFocusable(view: View): View? {
        if (view.isFocusableInTouchMode && view.visibility == View.VISIBLE) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) firstTouchModeFocusable(view.getChildAt(i))?.let { return it }
        }
        return null
    }

    private fun findScroll(view: View): ScrollView? {
        if (view is ScrollView) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) findScroll(view.getChildAt(i))?.let { return it }
        return null
    }

    private fun logBody(view: View): TextView? {
        if (view is TextView && view.text.length > 200) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) logBody(view.getChildAt(i))?.let { return it }
        return null
    }

    /** 造一份足够长的 crash.log，复现真机"一大堆崩溃日志"的场景后打开崩溃日志页。 */
    private fun openCrashPageWithLongLog(): MainActivity {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        shadowOf(Looper.getMainLooper()).idle()
        val dir = activity.getExternalFilesDir(null)!!
        File(dir, "crash.log").writeText("X".repeat(4000) + "\njava.lang.RuntimeException: 崩溃堆栈\n".repeat(200))
        activity.showCrashLogPage()
        shadowOf(Looper.getMainLooper()).idle()
        return activity
    }

    @Test
    fun longLogPage_doesNotAutoScrollAwayFromTopButtons() {
        val activity = openCrashPageWithLongLog()
        val scroll = findScroll(activity.root)
        assertNotNull("崩溃日志页必须有滚动容器", scroll)

        // 系统在触摸模式下会把焦点交给子树里第一个"触摸模式可获焦"的视图，
        // 接收焦点者会被 ScrollView 滚进可视区。锚点必须是滚动容器本身，不能是巨大的正文。
        val anchor = firstTouchModeFocusable(scroll!!)
        assertSame(
            "进页面时触摸模式焦点锚点必须是滚动容器本身，否则系统会自动把正文滚到顶部、顶掉操作卡：$anchor",
            scroll,
            anchor,
        )

        scroll.scrollTo(0, 0)
        anchor!!.requestFocus()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("拿到触摸模式焦点后不得把页面滚离顶部（用户看到的'瞬间闪一下'）", 0, scroll.scrollY)
    }

    @Test
    fun longLogBody_stillSelectableForCopy() {
        val activity = openCrashPageWithLongLog()
        val body = logBody(activity.root)
        assertNotNull("长日志必须渲染出正文", body)
        assertTrue("正文仍要允许长按选中复制（不能为了不抢焦点而砍掉选区）", body!!.isTextSelectable)
    }

    @Test
    fun topButtons_comeBeforeLogBody_soNoScrollingNeeded() {
        val activity = openCrashPageWithLongLog()
        val body = activity.root.getChildAt(1) as ViewGroup          // ScrollView
        val content = body.getChildAt(0) as ViewGroup                // 纵向 body
        var opsIndex = -1
        var logIndex = -1
        for (i in 0 until content.childCount) {
            val card = content.getChildAt(i) as? ViewGroup ?: continue
            val hasLogBody = logBody(card) != null
            val hasAction = firstActionLabel(card) != null
            if (hasAction && opsIndex < 0) opsIndex = i
            if (hasLogBody && logIndex < 0) logIndex = i
        }
        assertTrue("必须渲染出操作卡", opsIndex >= 0)
        assertTrue("必须渲染出日志卡", logIndex >= 0)
        assertTrue(
            "操作卡必须排在日志正文之前，否则又得滑到底（用户第一轮反馈）：ops=$opsIndex log=$logIndex",
            opsIndex < logIndex,
        )
        val opsCard = content.getChildAt(opsIndex) as ViewGroup
        assertNotNull("操作卡里必须有「打开崩溃日志文件夹」入口（用户找不到文件夹时的自助出路）", firstActionLabel(opsCard, "打开崩溃日志文件夹"))
    }

    private fun firstActionLabel(view: View, expected: String? = null): String? {
        val desc = view.contentDescription?.toString()
        if (desc != null && desc.isNotEmpty() && (expected == null || desc == expected)) return desc
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) firstActionLabel(view.getChildAt(i), expected)?.let { return it }
        }
        return null
    }

    @Test
    fun crashFolder_isCreatedAndVisibleInPublicStorage() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue("建目录必须回读确认成功，而不是只信 mkdirs 的返回值", activity.ensureCrashFolder())
        assertTrue("重复调用必须幂等（每次进页面都会调）", activity.ensureCrashFolder())

        val folder = App.publicCrashFolder()
        assertNotNull("公共崩溃目录必须可解析", folder)
        assertTrue("公共崩溃目录必须真实存在：$folder", folder!!.isDirectory)
        assertTrue(
            "目录必须是文件管理器可见的公共路径（东方无限/崩溃日志），实际：$folder",
            folder.absolutePath.contains("东方无限") && folder.absolutePath.contains("崩溃日志"),
        )
        assertTrue("目录里必须留一份 crash.log 占位，否则空目录在部分文件管理器里不可见", File(folder, "crash.log").isFile)
    }
}
