package cc.nkbr.lanzouplus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * [DFW-86] 动效**时长刻度**守卫 —— 缓动曲线统一之后的第二半。
 *
 * ## 取证结论（2026-10-01）
 * 收敛前 `MainActivity` 里有 **15 个不同时长**：
 * `70/80/90/120/130/140/160/170/190/200/220/240/250/280/300`，
 * 其中**只有 200/250/300 三档**在规范 `docs/design/wear-ui-system.md` 第 68 行列出的
 * MDC `motion_duration` 官方刻度上，其余 **12 档都是随手写的**。
 *
 * 规范早就写好了刻度（`short1 50 / short2 100 / short3 150 / short4 200 / medium1 250 / medium2 300 …`），
 * 代码却另跑一套 —— 这跟缓动曲线是同一类问题：**规范写了一套，代码没跟上**。
 *
 * ## 外部依据（不是我们拍的）
 * - Material 3《Easing and duration》：Enter 用 long（500ms）、Exit 用 short（200ms）
 * - Emil Kowalski：UI 动画一律 300ms 以内
 * - Norton Design System：open 250ms / close 200ms
 */
class MotionDurationScaleJvmTest {

    private val root: File = run {
        var dir = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (!File(dir, "AGENTS.md").isFile && dir.parentFile != null) dir = dir.parentFile
        dir
    }

    private fun source(rel: String) = File(root, rel).readText(Charsets.UTF_8)

    private val main by lazy { source("app/src/main/java/cc/nkbr/lanzouplus/MainActivity.java") }

    /** 规范第 68 行列出的完整 M3 时长刻度 —— 这是**唯一权威**，不许在别处发明数值。 */
    private val m3Scale = setOf(
        50, 100, 150, 200, 250, 300, 350, 400, 450, 500, 550, 600, 700, 800, 900, 1000,
    )

    private fun durationsIn(src: String): List<Int> =
        Regex("\\.setDuration\\((\\d+)\\)").findAll(src).map { it.groupValues[1].toInt() }.toList()

    /**
     * 允许的**显式例外**：页面转场的 280/240。
     *
     * 它们不走通用刻度，因为那是上一轮按真机反馈刻意调过的
     * （`PageTransitionMotionJvmTest` 里有 `assertFalse("推入不许再回到 300ms")` 守着）。
     * [DFW-86] 收敛刻度时我机械地把它们套成了 300/250，**把那个决定覆盖掉了**，测试立刻变红。
     * 一条统一规则不该碾过有测试守护的刻意决定 —— 所以登记成具名例外，而不是放宽规则或改测试。
     */
    private val documentedExceptions = setOf(280, 240)

    @Test
    fun everyAnimationDurationIsOnTheOfficialScale() {
        val offenders = durationsIn(main).filterNot { it in m3Scale || it in documentedExceptions }.distinct().sorted()
        assertTrue(
            "这些时长不在规范的 M3 刻度上（规范第 68 行）：$offenders —— 新动画请用 DUR_* 常量",
            offenders.isEmpty(),
        )
    }

    @Test
    fun theScaleIsActuallySmall_enoughToBeASystem() {
        val used = durationsIn(main).distinct().sorted()
        assertTrue(
            "实际用到的档位数必须收敛（收敛前是 15 档）：现在 $used",
            used.size <= 7,
        )
        assertTrue("档位不能少到只有一档（那说明退场/入场没区分）", used.size >= 3)
    }

    /** 常量必须与规范刻度一致 —— 改了常量就等于全站节奏变了，必须显式决定。 */
    @Test
    fun namedConstantsMatchTheSpecScale() {
        val m = Regex("static final int DUR_EXIT_FAST=(\\d+),DUR_SMALL=(\\d+),DUR_BASE=(\\d+),DUR_MEDIUM=(\\d+),DUR_LARGE=(\\d+)")
            .find(main)
        assertTrue("必须声明五个 DUR_* 常量（新增动画按语义挑，不许写裸数字）", m != null)
        val values = m!!.groupValues.drop(1).map { it.toInt() }
        for (v in values) assertTrue("DUR 常量 $v 不在规范刻度上", v in m3Scale)
        assertEquals("五档必须严格递增", values.sorted(), values)
        assertEquals("五档不许重复", values.distinct().size, values.size)
        assertTrue("最大档不得超过 300ms（Emil Kowalski：UI 动画一律 300ms 以内）", values.max() <= 300)
        // 页面转场那两档是**故意保留字面量**的例外 —— 见 MainActivity 里 DUR_PAGE_* 的注释：
        // 老守卫 PageTransitionMotionJvmTest 查的就是字面量，抽成常量会逼着改那条守卫，
        // 而「改一条已有的守卫测试」正是最容易制造假绿的动作。
        assertTrue("页面转场必须保留 280（老守卫的字面量断言）", main.contains(".setDuration(280)"))
    }

    /**
     * 反向探针：确认这条测试**真的能红**。
     * 喂一个不在刻度上的值（137ms），守卫必须抓出来。
     */
    @Test
    fun theGuardActuallyDetectsAnOffScaleDuration() {
        val fake = "void f(){ v.animate().alpha(1f).setDuration(137).start(); }"
        assertEquals("守卫必须能抓出刻度外的时长（否则就是假绿）", listOf(137), durationsIn(fake))
        assertTrue("137 不在刻度上", durationsIn(fake).any { it !in m3Scale })
    }

    /** 退场不该比入场慢 —— 规范第 89 行的自检项，M3 官方方向也一致。 */
    @Test
    fun noExitIsSlowerThanItsEnter() {
        val pairs = Regex("(\\w+)\\.animate\\(\\)\\.([^;]*?)\\.setDuration\\((\\d+)\\)")
            .findAll(main)
            .groupBy({ it.groupValues[1] }, { Triple(it.groupValues[2], it.groupValues[3].toInt(), it.value) })
        val violations = mutableListOf<String>()
        for ((view, items) in pairs) {
            val exits = items.filter { it.first.contains("alpha(0f)") }.map { it.second }
            val enters = items.filter { it.first.contains("alpha(1f)") }.map { it.second }
            if (exits.isNotEmpty() && enters.isNotEmpty() && exits.max() > enters.min()) {
                violations += "$view: 退场 ${exits.max()}ms > 入场 ${enters.min()}ms"
            }
        }
        assertTrue("退场比入场还慢会显得「进得急、走得慢」（规范第 89 行）：$violations", violations.isEmpty())
    }
}
