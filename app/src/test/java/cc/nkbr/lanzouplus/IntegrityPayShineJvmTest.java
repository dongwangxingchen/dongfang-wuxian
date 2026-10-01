package cc.nkbr.lanzouplus;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.animation.ValueAnimator;
import android.graphics.drawable.GradientDrawable;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

/**
 * [DFW-74] 设置页「诚信付费」按钮**扫光的结构不变量**守卫。
 *
 * 用户 2026-10-01 真机截图反馈（逐字）：
 * > "当我付费成功后……我的设置界面，它不是会有一个动画效果吗？但是现在没有适配好，
 * >  我发现它那个白色的流光效果只覆盖了 2/3 的地方，然后看起来有那种割裂感。"
 *
 * 像素好不好看没法在 JVM 里断言，但**"只覆盖 2/3"是个几何事实**，可以钉死：
 * <ol>
 *   <li><b>铺满全高</b>：扫光层的绘制框必须等于按钮整块（含 padding 区），
 *       旧实现固定 40dp 高、垂直居中，而按钮实测约 60~66dp —— 40/62≈65%，正是"2/3"。</li>
 *   <li><b>不能反过来把按钮撑高</b>：按钮在设置页是 wrap_content 高度，若把扫光层天真地设成
 *       MATCH_PARENT，FrameLayout 会按"父容器给的可用高度"量它，按钮会变成一屏高（v1.4.2 踩过）。</li>
 *   <li><b>水平渐变、峰值不变刺眼</b>：旧的是 TL_BR 斜带，在圆角矩形里被 clipToOutline 切出斜硬边；
 *       峰值白光必须 ≤ 旧版的 35%（0x59）。</li>
 *   <li><b>红线不变</b>：动画关（motionEnabled=false）不启动；detach 后动画必须被取消。</li>
 *   <li><b>全程无死角</b>：t=0 亮带右缘贴按钮左缘、t=1 亮带左缘贴按钮右缘 ——
 *       按钮宽度上每一点都被扫到，周期里没有"完全无光"的空转时段（旧公式在 t≈0.70 就移出右缘，30% 空转）。</li>
 * </ol>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w411dp-h891dp-420dpi")
public class IntegrityPayShineJvmTest {

  private static final float DENSITY = 2.625f;   // 420dpi
  private static final int SCREEN_W = 1079;      // 411dp @ 420dpi
  private static final int TALL_ENOUGH = 2000;   // 故意给一个远超按钮内容的高度上限，用来暴露"被撑高"

  @BeforeClass public static void silenceAutoImport() { MainActivity.LIBRARY_AUTO_IMPORT = false; }

  private static IntegrityPayButton button(boolean paid, boolean motionEnabled) {
    return new IntegrityPayButton(RuntimeEnvironment.getApplication(), ThemeEngine.LEGACY,
        DENSITY, paid, motionEnabled, null);
  }

  /** 扫光层 = 按钮直接子 View 里唯一的"纯 View"（内容盒是 LinearLayout，不会误判）。 */
  private static View shineOf(IntegrityPayButton b) {
    List<View> plain = new ArrayList<>();
    for (int i = 0; i < b.getChildCount(); i++) {
      if (b.getChildAt(i).getClass() == View.class) plain.add(b.getChildAt(i));
    }
    assertEquals("已付费态应当只有一个扫光层", 1, plain.size());
    return plain.get(0);
  }

  /** 按真实调用方式量一遍：宽度 EXACTLY、高度由调用方给规格（设置页是 wrap_content → AT_MOST）。 */
  private static void layoutWrapped(IntegrityPayButton b, int widthPx, int heightMode, int heightPx) {
    b.measure(View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(heightPx, heightMode));
    b.layout(0, 0, b.getMeasuredWidth(), b.getMeasuredHeight());
  }

  // ── 1. 铺满全高（"只覆盖 2/3"的直接回归） ───────────────────────────────

  @Test public void shine_coversTheWholeButtonIncludingItsPadding() {
    IntegrityPayButton b = button(true, false);
    layoutWrapped(b, SCREEN_W, View.MeasureSpec.AT_MOST, TALL_ENOUGH);
    View shine = shineOf(b);

    assertTrue("按钮得先真的被量出来", b.getHeight() > 0);
    assertEquals("扫光层必须与按钮同高（旧实现 40dp vs 按钮 ~62dp，就是用户看到的 2/3）",
        b.getHeight(), shine.getHeight());
    assertEquals("上缘必须贴住按钮上缘（含 padding 区，否则顶部留一条硬边）", 0, shine.getTop());
    assertEquals("下缘必须贴住按钮下缘", b.getHeight(), shine.getBottom());
    assertTrue("亮带宽度必须 > 0", shine.getWidth() > 0);
    assertTrue("亮带不能比按钮还宽（要扫的是按钮本身）", shine.getWidth() <= b.getWidth());
    assertTrue("亮带宽度下限 132dp：旧实现固定 110dp，宽屏上只是'一小块'",
        shine.getWidth() >= Math.round(DENSITY * 132f));
  }

  @Test public void shine_doesNotInflateTheWrapContentButtonHeight() {
    IntegrityPayButton b = button(true, false);
    layoutWrapped(b, SCREEN_W, View.MeasureSpec.AT_MOST, TALL_ENOUGH);
    assertTrue("按钮高度必须由内容决定，不能被 MATCH_PARENT 的扫光层撑到父容器上限（实测高 " + b.getHeight() + "px）",
        b.getHeight() < Math.round(DENSITY * 120f));
    // 反证：上限给得再大，按钮高度也不跟着变
    IntegrityPayButton b2 = button(true, false);
    layoutWrapped(b2, SCREEN_W, View.MeasureSpec.AT_MOST, TALL_ENOUGH * 2);
    assertEquals("高度上限翻倍也不该改变按钮高度", b.getHeight(), b2.getHeight());
  }

  // ── 2. 渐变方向与亮度 ───────────────────────────────────────────────────

  @Test public void shine_isAHorizontalBandNoBrighterThanBefore() {
    View shine = shineOf(button(true, false));
    assertNotNull("扫光层必须有渐变背景", shine.getBackground());
    assertTrue("背景必须是 GradientDrawable", shine.getBackground() instanceof GradientDrawable);
    GradientDrawable g = (GradientDrawable) shine.getBackground();
    assertEquals("必须是水平渐变（斜向渐变会在圆角矩形里被切出斜硬边）",
        GradientDrawable.Orientation.LEFT_RIGHT, g.getOrientation());

    int[] colors = g.getColors();
    assertNotNull("渐变必须有多段色标（透明→白→透明）", colors);
    assertTrue("至少三段", colors.length >= 3);
    assertEquals("左端必须全透明（否则亮带入场就是一条硬边）", 0, colors[0] >>> 24);
    assertEquals("右端必须全透明", 0, colors[colors.length - 1] >>> 24);
    int peak = 0;
    for (int c : colors) peak = Math.max(peak, c >>> 24);
    assertTrue("峰值白光不得超过旧版的 35%（0x59 = 89）—— 修观感不等于加刺眼", peak <= 0x59);
    assertTrue("也不能暗到看不见（旧版可见峰值约 31%）", peak >= 0x40);
  }

  // ── 3. 红线：未付费没有扫光 / 动画开关 / detach 取消 ─────────────────────

  @Test public void unpaid_hasNoShineAtAll() {
    IntegrityPayButton b = button(false, true);
    for (int i = 0; i < b.getChildCount(); i++) {
      assertFalse("未付费态不许有任何扫光层", b.getChildAt(i).getClass() == View.class);
    }
    assertNull(b.shine);
    assertNull(b.shineAnimator);
  }

  @Test public void motionDisabled_neverStartsTheAnimator() {
    IntegrityPayButton b = button(true, false);
    attach(b);
    View shine = shineOf(b);
    assertTrue("前置条件：按钮真的挂到窗口上了", b.isAttachedToWindow());
    assertNull("motionEnabled=false 时不许启动扫光", b.shineAnimator);
    assertEquals("没启动就不该有位移/透明度残留（快照首帧无残影）", 0f, shine.getAlpha(), 0.001f);
    assertEquals(0f, shine.getTranslationX(), 0.001f);
  }

  @Test public void detach_cancelsTheRunningAnimator() {
    IntegrityPayButton b = button(true, true);
    SupportActivity host = Robolectric.buildActivity(SupportActivity.class).setup().get();
    // 不 idle：Robolectric 的 ShadowValueAnimator 把 setRepeatCount 拦在 shadow 里，
    // 真实动画器仍是 repeatCount=0，推进虚拟时间会让它"跑完一轮就结束"（纯测试环境假象）。
    host.root.addView(b, new ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    assertNotNull("motionEnabled=true 且已付费 → 挂上窗口就该开始扫光", b.shineAnimator);
    // Robolectric 的 ShadowValueAnimator 拦截了 setRepeatCount（真值存在 shadow 里），所以走 shadow 读
    assertEquals("扫光必须是无限循环的",
        ValueAnimator.INFINITE, Shadows.shadowOf(b.shineAnimator).getActualRepeatCount());
    // isStarted 是 start() 里同步置位的，比 isRunning（依赖帧脉冲）稳，不会被测试环境的时钟抖动影响
    assertTrue("挂上窗口后动画必须真的被 start()", b.shineAnimator.isStarted());

    ValueAnimator started = b.shineAnimator;
    ((ViewGroup) b.getParent()).removeView(b);
    assertFalse("离开窗口必须取消动画（省电红线）", started.isStarted());
    assertFalse("取消后不许还在跑", started.isRunning());
    assertNull("动画器引用也要清掉", b.shineAnimator);
  }

  // ── 4. 几何：全程扫过、没有空转 ─────────────────────────────────────────

  @Test public void sweep_startsAndEndsExactlyAtTheEdges_andNeverLeavesAGap() {
    float w = SCREEN_W;
    float band = Math.max(DENSITY * 132f, w * 0.62f);

    assertEquals("t=0：亮带右缘必须正好贴住按钮左缘（起点即出界，不留空转）",
        0f, IntegrityPayButton.shineOffset(0f, w, band) + band, 0.01f);
    assertEquals("t=1：亮带左缘必须正好贴住按钮右缘（终点即出界）",
        w, IntegrityPayButton.shineOffset(1f, w, band), 0.01f);

    float previous = Float.NEGATIVE_INFINITY;
    for (int i = 0; i <= 200; i++) {
      float left = IntegrityPayButton.shineOffset(i / 200f, w, band);
      assertTrue("位移必须单调递增（否则会来回抖）", left > previous);
      previous = left;
    }

    for (int i = 0; i <= 100; i++) {
      float x = w * i / 100f;
      boolean covered = false;
      for (int k = 0; k <= 2000 && !covered; k++) {
        float left = IntegrityPayButton.shineOffset(k / 2000f, w, band);
        covered = left <= x + 0.01f && x <= left + band + 0.01f;
      }
      assertTrue("按钮上每一点都必须被亮带扫到，x=" + x, covered);
    }
  }

  /** 把按钮挂进一个真窗口（SupportActivity 的根 View 已经 attach 到 WindowManager）。 */
  private static void attach(IntegrityPayButton b) {
    SupportActivity host = Robolectric.buildActivity(SupportActivity.class).setup().get();
    host.root.addView(b, new ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    Shadows.shadowOf(Looper.getMainLooper()).idle();
  }
}
