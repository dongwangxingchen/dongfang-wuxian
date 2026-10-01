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
 * [DFWX-UI-TOKEN] 任务 A 守卫：**描边色必须真的能看见**。
 *
 * ## 原缺陷（不会崩、不会报错，只会让人觉得"哪里不对"）
 * `ThemeEngine.LEGACY` 里 `surface2` 与 `border` **是同一个色值** `#262332`：
 * 同一个值既当"面"（填充）又当"描边"→ **等于没有描边**。
 * 纯黑底上的卡片/胶囊是一片平的深灰紫，没有边缘定义、没有层次
 * （悬浮球菜单已经因为同一根因重做过取色，见 `PremiumSurfaceJvmTest`；这次修的是 token 源头）。
 *
 * 同一个 `#262332` 对纯黑只有 **1.37:1**，对卡面 `#16141F` 只有 1.19:1，
 * 远低于 `docs/archive/plan/20260926-long-term-execution/cards/T5-S-settings-redesign.md`
 * §10.6 第 1 条与 §10.5 自检清单要求的 **≥1.5:1**（该卡指定的目标值是 **#3A3548**）。
 *
 * ## 为什么这两者**必须**是不同的值
 * 职责不同：`surface2` 是**面**，要落在规范 §5 的 Surface 亮度档里；
 * `border` 是**描边**，必须比它所描的那个面更亮，否则描边在面上看不见。
 * 一个值无法同时满足"面"和"比面更亮的线"这两种要求 —— 这就是原缺陷的根因。
 *
 * ## 为什么必须挂 Robolectric
 * `android.graphics.Color.red/green/blue` 在纯 JVM 里是未实现的桩（项目已知陷阱），
 * 亮度与对比度都靠它算。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class ThemeTokenContrastJvmTest {

    private val design = ThemeEngine.LEGACY

    /** WCAG 2.2 相对亮度（官方公式，sRGB 先去 gamma）。 */
    private fun relLuminance(color: Int): Double {
        fun channel(v: Int): Double {
            val c = v / 255.0
            return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(Color.red(color)) +
            0.7152 * channel(Color.green(color)) +
            0.0722 * channel(Color.blue(color))
    }

    /** WCAG 对比度（1:1 到 21:1）。 */
    private fun contrast(a: Int, b: Int): Double {
        val la = relLuminance(a)
        val lb = relLuminance(b)
        val hi = maxOf(la, lb)
        val lo = minOf(la, lb)
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun hex(color: Int): String = String.format("#%06X", color and 0xFFFFFF)

    // ── 根因：一个色不能既当"面"又当"描边" ──────────────────────────────

    @Test
    fun surface2AndBorderAreNotTheSameValue() {
        assertNotEquals(
            "surface2（面）与 border（描边）不得是同一个色值——那正是原缺陷" +
                "（两者都 #262332，等于没有描边）：surface2=${hex(design.surface2)} border=${hex(design.border)}",
            design.surface2,
            design.border,
        )
    }

    @Test
    fun borderIsBrighterThanTheSurfaceItOutlines() {
        // 描边要"浮"在面上：比它描的那个面更亮，否则在暗底上看不见。
        assertTrue(
            "描边必须比卡面更亮：border=${hex(design.border)} surface=${hex(design.surface)}",
            relLuminance(design.border) > relLuminance(design.surface),
        )
        assertTrue(
            "描边也必须比它旁边的面板色更亮：border=${hex(design.border)} surface2=${hex(design.surface2)}",
            relLuminance(design.border) > relLuminance(design.surface2),
        )
    }

    // ── 硬指标：描边对比度 ≥1.5:1（§10.6 / §10.5）─────────────────────────

    @Test
    fun borderMeetsTheOnePointFiveContrastFloor_againstPureBlack() {
        val ratio = contrast(design.border, design.bg)
        assertTrue(
            "描边对纯黑的对比度必须 ≥1.5:1（§10.6 目标；原 #262332 只有 1.37:1）。" +
                "实测 border=${hex(design.border)} ratio=${"%.2f".format(ratio)}:1",
            ratio >= 1.5,
        )
    }

    @Test
    fun borderMeetsTheOnePointFiveContrastFloor_againstTheCardSurface() {
        // §10.6 原文那句「现 1.19:1 太糊」量的就是"描边 vs 卡面 #16141F"这一对，
        // 所以这条才是它真正要求达标的口径。
        val ratio = contrast(design.border, design.surface)
        assertTrue(
            "描边对卡面的对比度必须 ≥1.5:1（§10.6 原口径：现 1.19:1 太糊）。" +
                "实测 border=${hex(design.border)} surface=${hex(design.surface)} ratio=${"%.2f".format(ratio)}:1",
            ratio >= 1.5,
        )
    }

    @Test
    fun borderIsTheDocumentedValue_notSomethingCloseToIt() {
        // §10.6 第 1 条逐字指定「描边 #262332 → 提亮到 #3A3548 档」。钉住这个值，
        // 避免以后有人"顺手调一个差不多的紫灰"把已核验过的对比度改掉。
        assertEquals("描边色必须是 §10.6 指定的 #3A3548", 0xFF3A3548.toInt(), design.border)
    }

    @Test
    fun theOldInvisibleValueIsGoneFromBothTokens() {
        // 原缺陷值 #262332 不得再出现在这两个 token 上（避免有人改一半）。
        val old = 0xFF262332.toInt()
        assertNotEquals("border 不得退回 #262332（1.37:1，看不见）", old, design.border)
        assertNotEquals("surface2 不得退回 #262332", old, design.surface2)
    }

    // ── surface2：面必须落在 §5 的亮度阶梯里，且四层单调 ─────────────────

    @Test
    fun surface2IsBrighterThanTheCardSurface_andDarkerThanTheStroke() {
        // 规范 §5 的亮度阶梯：层级越高越亮。四层必须严格单调，否则"用亮度表达层级"不成立。
        val screen = relLuminance(design.bg)
        val card = relLuminance(design.surface)
        val panel = relLuminance(design.surface2)
        val stroke = relLuminance(design.border)
        assertTrue("页底 < 卡面（实测 ${hex(design.bg)} < ${hex(design.surface)}）", screen < card)
        assertTrue("卡面 < 面板 surface2（实测 ${hex(design.surface)} < ${hex(design.surface2)}）", card < panel)
        assertTrue("面板 < 描边（实测 ${hex(design.surface2)} < ${hex(design.border)}）", panel < stroke)
    }

    @Test
    fun cardSurfaceKeepsTheDocumentedOnePointOneToThreeRatioOverBlack() {
        // §10.5 自检清单：「卡面比页底亮 1.1–1.3:1 且零投影」。这条同时守住"改描边不能把卡面一起改亮"。
        val ratio = contrast(design.surface, design.bg)
        assertTrue(
            "卡面比页底应亮 1.1–1.3:1（§10.5）。实测 ${"%.2f".format(ratio)}:1",
            ratio in 1.1..1.3,
        )
    }

    @Test
    fun surface2LandsInTheDocumentedSurfaceHighBand() {
        // §5：Surface Low/High = 白或品牌色 5–7% / 8–10%（High 至 11–14%）。
        // surface2 是卡面之上的一档"内嵌面板"，取 High 上档 11–14%。
        // 用"白叠加等效比例"量：取三通道里最高的那个通道（面板带紫偏，蓝通道最高）。
        val maxChannel = maxOf(Color.red(design.surface2), Color.green(design.surface2), Color.blue(design.surface2))
        val whiteOverlay = maxChannel / 255.0
        assertTrue(
            "surface2 应落在 §5「High 至 11–14%」档。实测白叠加等效 ${"%.1f".format(whiteOverlay * 100)}%" +
                "（${hex(design.surface2)}）",
            whiteOverlay in 0.11..0.15,
        )
    }

    // ── 语义审计：别把"面"和"描边"用反 ───────────────────────────────────

    @Test
    fun designExposesDistinctRolesForFillAndStroke() {
        // 结构性守卫：11 色表里 surface/surface2/border 必须是三个不同角色，
        // 不能靠"两个字段指向同一个色"来偷懒。
        val roles = setOf(design.surface, design.surface2, design.border)
        assertEquals(
            "surface / surface2 / border 必须是三个不同的色值（各司其职）：" +
                "surface=${hex(design.surface)} surface2=${hex(design.surface2)} border=${hex(design.border)}",
            3,
            roles.size,
        )
    }

    @Test
    fun theWholeLadderStaysInsideTheDarkThemeItWasApprovedWith() {
        // 用户 2026-10-01 拍板"保留现在这套 OLED 黑 + 紫的视觉语言，增量优化"：
        // 这次只动描边/面板两个 token，底色与主色不得被动过。
        assertEquals("页底仍是 OLED 真黑", 0xFF000000.toInt(), design.bg)
        assertEquals("卡面仍是 #16141F", 0xFF16141F.toInt(), design.surface)
        assertEquals("主色仍是经典紫 #A78BFA", 0xFFA78BFA.toInt(), design.primary)
        assertEquals("亮主色仍是 #C494FF", 0xFFC494FF.toInt(), design.primaryHi)
        assertEquals("正文色仍是 #F2F0F7", 0xFFF2F0F7.toInt(), design.text)
    }
}
