package cc.nkbr.lanzouplus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * [DFW-86] 动效曲线一致性守卫 —— 用户连续三轮反馈"割裂"的根因就在这里。
 *
 * ## 取证结论（2026-10-01）
 * `MainActivity` 的注释里一直写着"站内其它动效**一律** `PathInterpolator(0.2,0,0,1)`"，
 * 但**没有任何机制保证它**：`ViewPropertyAnimator` 在**不设曲线**时用的是安卓默认的
 * `AccelerateDecelerateInterpolator` —— 那是**另一条曲线**（两头慢、中间快），
 * 与"快速起步、长尾减速"的 M3 emphasized **手感正好相反**。
 *
 * 修复前实测：37 条动画语句里 **22 条没设曲线**，即全站**同时存在两个缓动族**。
 * 所以"割裂"不是某一处参数写错了，而是**同一屏里的两个元素可能走在不同的曲线上**。
 *
 * ## 这条测试守什么
 * 任何"设了时长却没设曲线"的动画，都是偷偷用默认曲线 —— 一律不许再出现。
 * 新增动画时必须显式选曲线（标准曲线用 `standardEase()`）。
 */
class MotionCurveUniformityJvmTest {

    private val root: File = run {
        var dir = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (!File(dir, "AGENTS.md").isFile && dir.parentFile != null) dir = dir.parentFile
        dir
    }

    private fun source(rel: String) = File(root, rel).readText(Charsets.UTF_8)

    /**
     * 按 `;` 切语句，找"含 animate() 且设了 duration 但整条语句里没有任何曲线"的。
     *
     * 注意判据是**整条语句**里有没有曲线 —— 因为 `ViewPropertyAnimator` 的链式调用
     * 允许把 `.setInterpolator()` 放在 `.setDuration()` 之前或之后，两种写法都对。
     */
    private fun uncurvedAnimations(src: String): List<String> =
        src.split(';')
            .filter { it.contains("setDuration(") && it.contains("animate()") }
            .filterNot { it.contains("nterpolator") }
            .map { it.trim() }

    @Test
    fun mainActivity_hasNoAnimationWithoutAnExplicitCurve() {
        val src = source("app/src/main/java/cc/nkbr/lanzouplus/MainActivity.java")
        val offenders = uncurvedAnimations(src)
        assertTrue(
            "这些动画设了时长却没设曲线，会偷偷用安卓默认的 AccelerateDecelerate（另一条曲线）：\n" +
                offenders.joinToString("\n") { "  - " + it.take(140) },
            offenders.isEmpty(),
        )
    }

    /** 反向探针：确认这条测试**真的能红** —— 拿一段故意不设曲线的代码喂给它。 */
    @Test
    fun theGuardActuallyDetectsAMissingCurve() {
        val fake = "void f(){ v.animate().alpha(1f).setDuration(200).start(); }"
        assertEquals("守卫必须能抓出没曲线的动画（否则就是假绿）", 1, uncurvedAnimations(fake).size)
        val good = "void f(){ v.animate().alpha(1f).setDuration(200).setInterpolator(standardEase()).start(); }"
        assertEquals("显式设了曲线的不该被抓", 0, uncurvedAnimations(good).size)
    }

    /** 标准曲线必须懒建 —— 静态字段会在 Robolectric 下抛 ExceptionInInitializerError（踩过）。 */
    @Test
    fun standardEase_isLazilyCreated_notAStaticField() {
        val src = source("app/src/main/java/cc/nkbr/lanzouplus/MainActivity.java")
        assertTrue("必须有 standardEase() 这个懒建入口", src.contains("PathInterpolator standardEase(){"))
        assertTrue(
            "standardEase 的字段声明**不能**带 static —— PathInterpolator 构造会碰 android.graphics.Path，" +
                "类初始化时创建会让 Robolectric 抛 ExceptionInInitializerError",
            !Regex("static\\s+(final\\s+)?PathInterpolator\\s+standardEase").containsMatchIn(src),
        )
    }

    /** 曲线数值本身也要钉死：改了这个数就等于全站手感变了。 */
    @Test
    fun standardEase_keepsTheAgreedValues() {
        val src = source("app/src/main/java/cc/nkbr/lanzouplus/MainActivity.java")
        assertTrue(
            "标准曲线必须是 PathInterpolator(0.2f,0f,0f,1f)（= M3 emphasized 减速段），" +
                "改它等于全站动效手感一起变，必须显式决定",
            src.contains("new PathInterpolator(0.2f,0f,0f,1f)"),
        )
    }
}
