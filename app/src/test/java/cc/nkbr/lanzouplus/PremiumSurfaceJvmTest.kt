package cc.nkbr.lanzouplus

import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [DFWX] DFW-68：悬浮球菜单的**配色真值表** + 表面绘制的颜色运算。
 *
 * 用户反馈：右下角悬浮球展开的 5 个按钮"颜色和风格以及质感挺不好的"。
 *
 * 查下来根因有三个，其中第一个是**硬缺陷**：
 *   ① 球与胶囊的描边色 `BORDER` 与填充色 `SURFACE2` **是同一个色**（都 #262332）
 *      → 描边根本看不见，整块是一片平的深灰紫，没有边缘定义；
 *   ② 胶囊里**图标比文字暗一档**（MUTED vs TEXT），看着不像一体；
 *   ③ 纯色填充、没有高光 → 没有"材质感"（规范 §5 早就要求 `PremiumSurfaceDrawable`
 *      的 topHighlight，但从未实现）。
 *
 * **必须挂 Robolectric**：`android.graphics.Color` 的静态方法在纯 JVM 测试里是未实现的桩
 * （项目已知陷阱，见 lessons.md），直接用会抛 "Method red in android.graphics.Color not mocked"。
 *
 * 本测试把这三条钉死——尤其第①条，它是"看着廉价"的直接来源，却不会崩、不会报错，
 * 只会让人一直觉得"哪里不对"。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PremiumSurfaceJvmTest {

    private val bg = Color.BLACK
    private val primary = 0xFFA78BFA.toInt()
    private val primaryHi = 0xFFC494FF.toInt()
    private val text = 0xFFF2F0F7.toInt()

    private fun lum(c: Int): Double =
        0.2126 * Color.red(c) + 0.7152 * Color.green(c) + 0.0722 * Color.blue(c)

    // ── 颜色运算 ──────────────────────────────────────────────────────────

    @Test
    fun over_compositesAlphaOntoBase() {
        // 纯黑底 + 纯白 50% = 中灰
        val half = PremiumSurface.over(Color.BLACK, Color.WHITE, 0.5f)
        assertEquals(128, Color.red(half))
        assertEquals(128, Color.green(half))
        assertEquals(128, Color.blue(half))
        // alpha=0 → 原色；alpha=1 → 完全被 tint 取代
        assertEquals(Color.BLACK, PremiumSurface.over(Color.BLACK, Color.WHITE, 0f))
        assertEquals(Color.WHITE, PremiumSurface.over(Color.BLACK, Color.WHITE, 1f))
    }

    @Test
    fun over_clampsOutOfRangeAlpha_insteadOfProducingGarbage() {
        // 传错参数不该产出越界颜色（否则会画出透明或异常的色块）
        assertEquals(Color.BLACK, PremiumSurface.over(Color.BLACK, Color.WHITE, -1f))
        assertEquals(Color.WHITE, PremiumSurface.over(Color.BLACK, Color.WHITE, 9f))
    }

    @Test
    fun lightenAndDarken_moveInTheRightDirection() {
        assertTrue("提亮后应更亮", lum(PremiumSurface.lighten(primary, 0.2f)) > lum(primary))
        assertTrue("压暗后应更暗", lum(PremiumSurface.darken(primary, 0.2f)) < lum(primary))
    }

    @Test
    fun surfaceOnBlack_followsTheBrightnessLadder() {
        // 规范 §5：层级越高越亮。这条保证"用亮度表达层级"这件事真的成立。
        val low = PremiumSurface.surfaceOnBlack(primary, PremiumSurface.LEVEL_SURFACE_LOW)
        val high = PremiumSurface.surfaceOnBlack(primary, PremiumSurface.LEVEL_SURFACE_HIGH)
        val higher = PremiumSurface.surfaceOnBlack(primary, PremiumSurface.LEVEL_SURFACE_HIGHER)
        val selected = PremiumSurface.surfaceOnBlack(primary, PremiumSurface.LEVEL_SELECTED)
        assertTrue("Surface High 应亮于 Low", lum(high) > lum(low))
        assertTrue("更高一档应更亮", lum(higher) > lum(high))
        assertTrue("选中态（品牌色 16–24%）应最亮", lum(selected) > lum(higher))
    }

    // ── 悬浮球：描边必须看得见（DFW-68 的根因）────────────────────────────

    @Test
    fun ball_strokeIsDifferentFromFill_soTheEdgeIsActuallyVisible() {
        val p = NavBall.ballPalette(bg, primary, primaryHi)
        assertNotEquals(
            "球的描边色绝不能等于填充色——那正是原缺陷（两者都是 #262332，等于没有描边）",
            p[0], p[1],
        )
        assertTrue("描边应比填充亮，否则在暗底上看不见", lum(p[1]) > lum(p[0]))
    }

    @Test
    fun ball_isBrighterThanPills_soTheMainEntryStandsOut() {
        // 球是主入口，胶囊是次级。层级要能看出来，否则一片糊。
        val ball = NavBall.ballPalette(bg, primary, primaryHi)
        val pill = NavBall.pillPalette(bg, primary, primaryHi, text, false)
        assertTrue("球应比未选中胶囊更亮", lum(ball[0]) > lum(pill[0]))
    }

    // ── 胶囊：描边可见 + 图标文字同色 + 选中态分明 ────────────────────────

    @Test
    fun pill_strokeIsDifferentFromFill_bothStates() {
        for (active in listOf(false, true)) {
            val p = NavBall.pillPalette(bg, primary, primaryHi, text, active)
            assertNotEquals("胶囊描边不得等于填充（active=$active）", p[0], p[1])
            assertTrue("描边应比填充亮（active=$active）", lum(p[1]) > lum(p[0]))
        }
    }

    @Test
    fun pill_contentColorIsUnified_iconAndLabelUseTheSameColor() {
        // 返回数组里**只有一个内容色**（下标 2），图标与文字都用它——
        // 从结构上就杜绝了"图标比文字暗一档"这种不统一。
        val inactive = NavBall.pillPalette(bg, primary, primaryHi, text, false)
        assertEquals("未选中：内容色 = 正文色", text, inactive[2])
        val active = NavBall.pillPalette(bg, primary, primaryHi, text, true)
        assertEquals("选中：内容色 = 亮主色", primaryHi, active[2])
        assertEquals("两个状态都只有 3 个返回值（填充/描边/内容）", 3, inactive.size)
    }

    @Test
    fun pill_activeStateIsBrighterThanInactive() {
        val off = NavBall.pillPalette(bg, primary, primaryHi, text, false)
        val on = NavBall.pillPalette(bg, primary, primaryHi, text, true)
        assertTrue("选中态填充应更亮（品牌色 tint 更高）", lum(on[0]) > lum(off[0]))
        assertTrue("选中态描边应更亮（用亮主色）", lum(on[1]) > lum(off[1]))
    }

    @Test
    fun pill_contentStaysReadableAgainstItsOwnFill() {
        // 选中态曾经踩过坑：底色太亮 + 亮字 → 糊在一起（代码里留着那条真机采样注释）。
        // 这里用亮度差做守卫：内容与填充必须有足够分离度。
        for (active in listOf(false, true)) {
            val p = NavBall.pillPalette(bg, primary, primaryHi, text, active)
            val diff = Math.abs(lum(p[2]) - lum(p[0]))
            assertTrue(
                "内容色与填充的亮度差过小，会糊在一起（active=$active, diff=$diff）",
                diff > 40.0,
            )
        }
    }

    // ── 亮度阶梯常量与规范一致 ────────────────────────────────────────────

    @Test
    fun brightnessLadder_matchesTheSpecRanges() {
        // 规范 §5：Surface Low 5–7% / High 8–10%（可至 11–14%）/ Selected 品牌色 16–24%
        assertTrue(PremiumSurface.LEVEL_SURFACE_LOW in 0.05f..0.07f)
        assertTrue(PremiumSurface.LEVEL_SURFACE_HIGH in 0.08f..0.10f)
        assertTrue(PremiumSurface.LEVEL_SURFACE_HIGHER in 0.11f..0.14f)
        assertTrue(PremiumSurface.LEVEL_SELECTED in 0.16f..0.24f)
        // 普通描边白 8–12%；高光/强调描边白 18–26%
        assertTrue(PremiumSurface.STROKE_NORMAL in 0.08f..0.12f)
        assertTrue(PremiumSurface.STROKE_EMPHASIS in 0.18f..0.26f)
    }
}
