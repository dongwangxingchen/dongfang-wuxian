package cc.nkbr.lanzouplus

import android.graphics.Color
import android.graphics.Outline
import android.os.Looper
import org.junit.Assert.assertEquals
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
 * [DFWX] DFW-72：**玻璃材质（Liquid Glass）规格守卫**。
 *
 * 用户 2026-10-01：「公告做的太丑了，质感也有问题……给我感觉雾蒙蒙的，没有通透感。」
 * 并且明确要求「去 GitHub 找现成的开源项目，不要纯靠 Skill 约束」。
 *
 * ## 取值不是拍脑袋，是有出处的
 * - **模糊半径**：AOSP《Window blurs》(source.android.com/docs/core/display/window-blurs)
 *   明说背景模糊 `80px` 是「毛玻璃」档、blur behind `20px` 是「景深」档、
 *   **超过 150px 会显著掉性能**。
 * - **API 存在性**：`javap` 实查 `android-37.0/android.jar`，确认
 *   `Window.setBackgroundBlurRadius(int)` / `FLAG_BLUR_BEHIND` / `setBlurBehindRadius` /
 *   `WindowManager.isCrossWindowBlurEnabled()` 全部存在。
 * - **光学配方**：`Kyant0/AndroidLiquidGlass`(3970★)、`chrisbanes/haze`(2573★)、
 *   `styropyr0/Prismal`(MIT)、`conorluddy/LiquidGlassReference`(iOS 26 规格)。
 *
 * ## 这个文件守的是什么
 * 「丑」和「雾蒙蒙」不会抛异常、不会让测试变红，只会让人一直觉得难看。
 * 所以把**材质配方本身变成可断言的数字**：顶边必须比底边亮、底面不能归零、
 * 玻璃底必须够透、降级底必须够实、任何人都不能把品牌色塞回底色。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class NoticeGlassJvmTest {

    companion object {
        @BeforeClass @JvmStatic fun silenceAutoImport() { MainActivity.LIBRARY_AUTO_IMPORT = false }
    }

    private fun activity(): MainActivity {
        val a = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        shadowOf(Looper.getMainLooper()).idle()
        return a
    }

    private fun notice(id: String = "n1") =
        RemoteConfigClient.Notice(
            id, "东方无限 公测开始", "感谢使用。", "normal", false,
            RemoteConfigClient.Notice.MODE_ONCE, 1_700_000_000_000L,
        )

    private fun spread(color: Int): Int =
        maxOf(Color.red(color), Color.green(color), Color.blue(color)) -
            minOf(Color.red(color), Color.green(color), Color.blue(color))

    // ── 模糊半径：官方推荐值 + 密度换算 + 性能上限 ──────────────────────────

    @Test
    fun backgroundBlur_equalsAospFrostedGlassValue_atReferenceDensity() {
        assertEquals(
            "AOSP 给的『毛玻璃』档是 80px（3.0 密度基准）",
            GlassSurface.BLUR_BG_BASE_PX, GlassSurface.backgroundBlurPx(3f),
        )
    }

    @Test
    fun blurBehind_equalsAospDepthOfFieldValue_atReferenceDensity() {
        assertEquals(
            "AOSP 给的『景深』档是 20px",
            GlassSurface.BLUR_BEHIND_BASE_PX, GlassSurface.blurBehindPx(3f),
        )
    }

    @Test
    fun blurBehind_isWeakerThanBackgroundBlur() {
        // 两个官方数字不能混用：把景深值当毛玻璃用会几乎没有模糊，
        // 把毛玻璃值当景深用会把整屏糊成一片——那正是用户说的「雾蒙蒙」。
        assertTrue(GlassSurface.blurBehindPx(2.625f) < GlassSurface.backgroundBlurPx(2.625f))
    }

    @Test
    fun blurRadius_scalesWithScreenDensity() {
        // 模糊半径是屏幕像素量。照抄 80px 会让低密度机器糊一倍、高密度机器看不清。
        assertTrue(GlassSurface.backgroundBlurPx(2f) < GlassSurface.backgroundBlurPx(3f))
        assertTrue(GlassSurface.backgroundBlurPx(4f) > GlassSurface.backgroundBlurPx(3f))
    }

    @Test
    fun blurRadius_neverExceedsAospPerformanceCeiling() {
        // AOSP：Avoid blur radii higher than 150 px, as this will significantly impact performance.
        for (density in listOf(0f, 1f, 2f, 2.625f, 3f, 4f, 6f, 12f)) {
            assertTrue(
                "density=$density 的背景模糊超过 AOSP 性能上限",
                GlassSurface.backgroundBlurPx(density) <= GlassSurface.BLUR_MAX_PX,
            )
            assertTrue(
                "density=$density 的景深模糊超过 AOSP 性能上限",
                GlassSurface.blurBehindPx(density) <= GlassSurface.BLUR_MAX_PX,
            )
        }
    }

    @Test
    fun blurRadius_isAlwaysPositive() {
        // 0 表示「不模糊」。走到这条路径说明判断出错了，不能静默退化成无模糊。
        assertTrue(GlassSurface.backgroundBlurPx(0f) > 0)
        assertTrue(GlassSurface.blurBehindPx(0f) > 0)
    }

    // ── 玻璃填充：AOSP『Handle blur enabled and disabled states』────────────────

    @Test
    fun glassFill_isTranslucentEnoughToSeeThrough() {
        val alpha = Color.alpha(GlassSurface.windowFillColor(Color.BLACK, true))
        assertTrue(
            "模糊可用时底色必须够透，否则背后虚化的画面透不出来、又变成一块灰板子（实测 alpha=$alpha）",
            alpha in 90..150,
        )
    }

    @Test
    fun fallbackFill_isNearlyOpaque() {
        // AOSP 原文：blur 被关掉时要 increase the alpha of the window background drawable，
        // 否则本窗口内容会和下层窗口的内容直接叠在一起、读不清。
        val alpha = Color.alpha(GlassSurface.windowFillColor(Color.BLACK, false))
        assertTrue("模糊不可用时必须按官方指引把底色做实（实测 alpha=$alpha）", alpha >= 240)
    }

    @Test
    fun disabledBlur_raisesDimAmount() {
        assertTrue(
            "没模糊时必须加大 dim，把注意力重新拉回弹窗（AOSP 明确要求）",
            GlassSurface.dimAmount(false) > GlassSurface.dimAmount(true),
        )
    }

    @Test
    fun glassFill_staysNeutral_notPurple() {
        // 用户已经因为发紫说过一次「廉价」。这条守卫不能撤：品牌色只配做点缀，
        // 一旦塞进底色，整块面板就会偏色。
        val fill = GlassSurface.windowFillColor(Color.BLACK, true)
        assertTrue("玻璃底色必须是中性的（实测色偏=${spread(fill)}）", spread(fill) <= 4)
    }

    @Test
    fun fallbackFill_staysNeutral_andStaysInTheBrightnessLadder() {
        val fill = GlassSurface.opaqueFill(Color.BLACK)
        assertTrue("降级底色也必须是中性的（实测色偏=${spread(fill)}）", spread(fill) <= 4)
        // 规范 §5 亮度阶梯：Surface 高档 11–14%（白 12% ≈ 31/255）
        assertTrue("降级底色应落在 §5 的 11–14% 档，实测 R=${Color.red(fill)}", Color.red(fill) in 24..40)
    }

    @Test
    fun glassFill_isDarkerThanFallbackFill() {
        // 有模糊时背景本身提供层次；底色再亮就发灰。两者不该是同一个数。
        assertTrue(Color.red(GlassSurface.glassFill(Color.BLACK)) < Color.red(GlassSurface.opaqueFill(Color.BLACK)))
    }

    // ── 边缘高光：玻璃感的来源。这一组把「顶部亮、对侧微亮」数字化 ────────────

    @Test
    fun rim_topEdgeIsBrighterThanBottomEdge() {
        assertTrue("光从上面来：顶边必须比底边亮", GlassSurface.rimAlphaAt(0f) > GlassSurface.rimAlphaAt(1f))
    }

    @Test
    fun rim_bottomEdgeStillGlows_slightly() {
        // Prismal（MIT）：抛光玻璃「被照到的那条边」和「对侧那条边」会同时反光，只是强弱不同。
        // 底部若直接归零，面板会像一张贴纸而不是一块玻璃。
        assertTrue("对侧 rim 不能是 0，否则边缘没有厚度感", GlassSurface.rimAlphaAt(1f) > 0f)
    }

    @Test
    fun rim_topStrength_staysInTheSpecRange() {
        // 规范 §5：高光/玻璃描边白 18–26%，且只出现一侧。
        assertTrue(
            "顶边高光应在 18%–26% 之间，实测 ${GlassSurface.rimAlphaAt(0f)}",
            GlassSurface.rimAlphaAt(0f) in 0.18f..0.26f,
        )
    }

    @Test
    fun rim_isMonotonicFromTopToBottom() {
        var previous = GlassSurface.rimAlphaAt(0f)
        for (i in 1..10) {
            val value = GlassSurface.rimAlphaAt(i / 10f)
            assertTrue("rim 必须单调变暗，不能忽明忽暗", value <= previous + 1e-6f)
            previous = value
        }
    }

    @Test
    fun sheen_fadesToZero_withinTheTopHalf() {
        assertEquals("顶边高光最强", GlassSurface.SHEEN, GlassSurface.sheenAlphaAt(0f), 1e-6f)
        assertEquals(0f, GlassSurface.sheenAlphaAt(GlassSurface.SHEEN_STOP), 1e-6f)
        assertEquals(0f, GlassSurface.sheenAlphaAt(1f), 1e-6f)
        assertTrue("高光必须一路衰减", GlassSurface.sheenAlphaAt(0.2f) < GlassSurface.sheenAlphaAt(0.05f))
        // 高光只该在顶部一小段。铺满整块就成了整块变白，那是发灰不是通透。
        assertTrue(GlassSurface.SHEEN_STOP <= 0.5f)
    }

    @Test
    fun spot_peaksInsideTheTopEdge_andDiesAtBothEnds() {
        assertEquals(0f, GlassSurface.spotAlphaAt(0f), 1e-6f)
        assertEquals(GlassSurface.SPOT_PEAK, GlassSurface.spotAlphaAt(GlassSurface.SPOT_CENTER), 1e-6f)
        assertEquals(0f, GlassSurface.spotAlphaAt(1f), 1e-6f)
        // 镜面热点必须落在顶部那一条带上（画成整块会把顶部洗白）
        assertTrue(GlassSurface.SPOT_BAND in 0.1f..0.4f)
    }

    @Test
    fun spot_isAsymmetric_litFromUpperLeft() {
        // iOS 26 规格：高光来自上方偏左。左右对称的亮带看着像塑料。
        assertTrue("热点应偏左上，不该正好在中点", GlassSurface.SPOT_CENTER < 0.5f)
    }

    // ── Drawable 行为 ─────────────────────────────────────────────────────

    @Test
    fun panel_offersRoundedOutline_soChildrenAreClippedCorrectly() {
        val drawable = GlassSurface.panel(28, 1f)
        drawable.setBounds(0, 0, 400, 600)
        val outline = Outline()
        drawable.getOutline(outline)
        assertTrue("面板必须提供圆角 outline，否则内容会被直角裁切、圆角白做", outline.radius > 0f)
    }

    @Test
    fun panel_isTranslucent_soTheBlurBehindItShowsThrough() {
        // 关键：面板自身必须是透明的。若这里填了不透明色，窗口背景的模糊会被盖死，
        // 又回到「一块灰板子」——这正是要修的病。
        val drawable = GlassSurface.panel(28, 1f)
        assertEquals(android.graphics.PixelFormat.TRANSLUCENT, drawable.opacity)
    }

    @Test
    fun windowBackground_isRoundedShapeDrawable_requiredByAospForBlurCorners() {
        // AOSP：圆角窗口要让模糊区域跟着圆，必须把带圆角的 ShapeDrawable 设为窗口背景 drawable。
        val background = GlassSurface.windowBackground(28, GlassSurface.windowFillColor(Color.BLACK, true))
        assertTrue("窗口背景必须是 ShapeDrawable（圆角只能写在这里）", background is android.graphics.drawable.ShapeDrawable)
        background.setBounds(0, 0, 400, 600)
        val outline = Outline()
        background.getOutline(outline)
        assertTrue("窗口背景必须带圆角，模糊轮廓才会跟着圆", outline.radius > 0f)
    }

    @Test
    fun windowFillColor_keepsTheRequestedAlpha() {
        assertEquals(
            "填充色的 alpha 必须等于策略值，否则透不透就失控了",
            Math.round(255 * GlassSurface.FILL_ALPHA_BLUR),
            Color.alpha(GlassSurface.windowFillColor(Color.BLACK, true)),
        )
    }

    // ── 弹窗真的用上了这套配方（不是只写了个没被调用的类）────────────────────

    @Test
    fun shownDialog_appliesTheGlassWindowBackground() {
        val a = activity()
        a.showNoticeDialog(notice(), 0, null)
        shadowOf(Looper.getMainLooper()).idle()
        // 弹窗是否真的启用模糊取决于系统状态（省电模式/开发者选项都会关掉），
        // 但「当前是否启用」的记录必须与查询结果一致——否则降级路径会走错。
        assertEquals(GlassSurface.isBlurEnabled(a), a.noticeDialogBlurApplied)
        val background = a.noticeDialogWindowBackground
        assertNotNull("弹窗必须真的把玻璃背景装到窗口上（不能只写了个没人调用的类）", background)
        assertTrue("窗口背景必须是带圆角的 ShapeDrawable", background is android.graphics.drawable.ShapeDrawable)
        background!!.setBounds(0, 0, 400, 600)
        val outline = Outline()
        background.getOutline(outline)
        assertTrue("窗口背景必须带圆角，模糊轮廓才会跟着圆", outline.radius > 0f)
    }
}
