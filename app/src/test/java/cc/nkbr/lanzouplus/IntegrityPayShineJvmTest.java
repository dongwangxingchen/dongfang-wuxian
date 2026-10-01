package cc.nkbr.lanzouplus;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.animation.ValueAnimator;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * [DFW-74 / DFW-75] 设置页「诚信付费」按钮**扫光的几何不变量**守卫。
 *
 * 用户 2026-10-01 两次真机反馈（逐字）：
 * <blockquote>
 *   第一次："我发现它那个白色的流光效果只覆盖了 2/3 的地方，然后看起来有那种割裂感。"
 *   第二次："它那个流光效果并没有在诚信付费上面做的很好……你别在这里整的只有 2/3 的地方，
 *          而且还有明显的矩形感觉，反正是挺奇怪的吧。"
 * </blockquote>
 *
 * 像素好不好看没法在 JVM 里断言，但"只覆盖 2/3"和"矩形感"**都是几何事实**，可以钉死。
 *
 * <h2>上一版为什么还是不对</h2>
 * 上一版加了一个子 View 当亮带再平移它，于是：
 * <ol>
 *   <li>子 View 有确定宽度，渐变只能铺在这个宽度里 → 亮带在视觉上就是一块**矩形**；</li>
 *   <li>透明度包络 {@code sin(πt)} 在 t=0/1 时 alpha=0，而亮带恰好在那两个时刻扫过按钮的
 *       <b>左右两端</b> → 两端永远只被"半亮"扫过；亮度峰值（t=0.5）时亮带只覆盖中间 62%，
 *       <b>正好就是"只覆盖 2/3 的地方"</b>。</li>
 * </ol>
 *
 * <h2>现在守什么</h2>
 * 不用子 View，直接在 dispatchDraw 里画一条铺满按钮宽度的渐变柔光：
 * 全程不做透明度包络，亮带从"完全在左缘外"扫到"完全在右缘外"。
 * 于是按钮上每一点都在**峰值亮度**下被扫过一次 —— 这是"覆盖 2/3"的反命题。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w411dp-h891dp-420dpi")
public class IntegrityPayShineJvmTest {

  private static final float DENSITY = 2.625f;   // 420dpi
  private static final int SCREEN_W = 1079;      // 411dp @ 420dpi
  private static final int TALL_ENOUGH = 2000;

  @BeforeClass public static void silenceAutoImport() { MainActivity.LIBRARY_AUTO_IMPORT = false; }

  private static IntegrityPayButton button(boolean paid, boolean motionEnabled) {
    return new IntegrityPayButton(RuntimeEnvironment.getApplication(), ThemeEngine.LEGACY,
        DENSITY, paid, motionEnabled, null);
  }

  private static void measure(IntegrityPayButton b) {
    b.measure(View.MeasureSpec.makeMeasureSpec(SCREEN_W, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(TALL_ENOUGH, View.MeasureSpec.AT_MOST));
    b.layout(0, 0, b.getMeasuredWidth(), b.getMeasuredHeight());
  }

  private static String source(String path) {
    try {
      File root = new File(System.getProperty("user.dir")).getAbsoluteFile();
      while (root != null && !new File(root, "app/src/main/AndroidManifest.xml").isFile()) root = root.getParentFile();
      return new String(Files.readAllBytes(new File(root, path).toPath()), StandardCharsets.UTF_8);
    } catch (Exception error) {
      throw new AssertionError("读不到源码 " + path, error);
    }
  }

  // ── 核心：全程覆盖，没有"只覆盖 2/3" ──────────────────────────────────

  @Test public void everyPointOnTheButtonGetsThePeakSweep() {
    // 用户"只覆盖 2/3"的直接反命题：按钮宽度上**每一个点**都必须存在某个时刻 t，
    // 使亮带把该点包在"亮核"里（渐变 0.35~0.65 那一段）。
    float width = SCREEN_W;
    float band = button(true, true).shineBandWidth(width);
    for (int i = 0; i <= 100; i++) {
      float x = width * i / 100f;
      boolean covered = false;
      for (int j = 0; j <= 2000; j++) {
        float t = j / 2000f;
        float left = IntegrityPayButton.shineOffset(t, width, band);
        if (x >= left + band * 0.35f && x <= left + band * 0.65f) { covered = true; break; }
      }
      assertTrue("按钮上 x=" + x + " 这一点从没被亮核扫到过（就是'只覆盖 2/3'）", covered);
    }
  }

  @Test public void sweepStartsFullyOutsideTheLeftEdgeAndEndsFullyOutsideTheRightEdge() {
    float width = SCREEN_W;
    float band = button(true, true).shineBandWidth(width);
    float left0 = IntegrityPayButton.shineOffset(0f, width, band);
    float left1 = IntegrityPayButton.shineOffset(1f, width, band);
    assertTrue("t=0 时整条亮带必须在按钮左缘之外（否则一上屏就有一块光糊在左边）", left0 + band <= 0.01f);
    assertTrue("t=1 时整条亮带必须在按钮右缘之外", left1 >= width - 0.01f);
    float previous = Float.NEGATIVE_INFINITY;
    for (int j = 0; j <= 200; j++) {
      float at = IntegrityPayButton.shineOffset(j / 200f, width, band);
      assertTrue("扫光位移必须单调，不能来回弹", at > previous);
      previous = at;
    }
  }

  @Test public void alphaIsNotEnveloped_otherwiseTheEndsNeverLightUp() {
    // 上一版的真凶：sin(πt) 包络在 t=0/1 时为 0，而亮带恰好在那两个时刻扫过左右两端。
    String src = source("app/src/main/java/cc/nkbr/lanzouplus/IntegrityPayButton.java");
    assertFalse("不许再用 sin(πt) 之类的透明度包络（那正是'只覆盖 2/3'的原因）", src.contains("Math.sin"));
    assertFalse("不许再对整条扫光 setAlpha", src.contains("shine.setAlpha") || src.contains("setAlpha((float)"));
    assertEquals("峰值白光仍是 35%（0x59）—— 与最初版本同值，不会更刺眼",
        0x59, IntegrityPayButton.shinePeakAlpha());
  }

  // ── 没有"矩形亮块" ────────────────────────────────────────────────────

  @Test public void thereIsNoChildViewActingAsTheLightBand() {
    // 子 View 有确定宽度 → 视觉上就是一块矩形。现在改成直接画，按钮里不该再有这个 View。
    IntegrityPayButton b = button(true, true);
    measure(b);
    int plain = 0;
    for (int i = 0; i < b.getChildCount(); i++) {
      View child = b.getChildAt(i);
      if (!(child instanceof ViewGroup)) plain++;
    }
    assertEquals("已付费按钮里不该再有'纯 View'形式的扫光层（那就是矩形感的来源）", 0, plain);
  }

  @Test public void sweepIsDrawnUnderTheText_notOverIt() {
    String src = source("app/src/main/java/cc/nkbr/lanzouplus/IntegrityPayButton.java");
    int draw = src.indexOf("canvas.drawRect(");
    int children = src.indexOf("super.dispatchDraw(canvas);");
    assertTrue("扫光必须真的被画出来", draw > 0);
    assertTrue("super.dispatchDraw 必须存在", children > 0);
    assertTrue("扫光要画在 super.dispatchDraw **之前**（否则会盖住文字）", draw < children);
  }

  @Test public void bandIsWideEnoughToReadAsALightSweep_notAThinLine() {
    float width = SCREEN_W;
    float band = button(true, true).shineBandWidth(width);
    assertTrue("亮带至少占按钮一半宽（实际 " + band + "/" + width + "）", band >= width * 0.5f);
    assertTrue("窄屏下限 96dp（实际 " + band + "）", band >= DENSITY * 96f - 0.5f);
  }

  // ── 红线：不动画 / 退场必须停 ─────────────────────────────────────────

  @Test public void motionDisabled_neverStartsTheSweep() {
    IntegrityPayButton b = button(true, false);
    measure(b);
    Shadows.shadowOf(Looper.getMainLooper()).idle();
    assertNull("动画关时不许启动扫光", b.shineAnimator);
    assertTrue("没动画时本帧不该画扫光", b.sweepT < 0f);
  }

  @Test public void detach_cancelsTheRunningAnimator() {
    IntegrityPayButton b = button(true, true);
    measure(b);
    // Robolectric 里 addView 到未挂窗口的父容器不会触发 onAttachedToWindow，直接派发这两个回调。
    Shadows.shadowOf(b).callOnAttachedToWindow();
    Shadows.shadowOf(Looper.getMainLooper()).idle();
    assertNotNull("挂上窗口后扫光应在跑", b.shineAnimator);
    // ValueAnimator 要有时间推进才会派发第一帧
    Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(120));
    assertTrue("跑起来后应该真的在画", b.sweepT >= 0f);
    ValueAnimator running = b.shineAnimator;
    Shadows.shadowOf(b).callOnDetachedFromWindow();
    Shadows.shadowOf(Looper.getMainLooper()).idle();
    assertFalse("退场后动画必须停（省电红线）", running.isRunning());
  }

  @Test public void unpaidButtonHasNoSweep() {
    IntegrityPayButton b = button(false, true);
    measure(b);
    assertNull("未付费态不许有扫光", b.shineAnimator);
    assertTrue(b.sweepT < 0f);
  }

  // ── 不能反过来把按钮撑高（v1.4.2 老坑，删掉 onMeasure 之后要确认仍然成立） ──

  @Test public void paidButtonDoesNotGetStretchedToOneScreen() {
    IntegrityPayButton b = button(true, true);
    measure(b);
    assertTrue("按钮高度必须由内容决定，不能变成一屏高（实际 " + b.getMeasuredHeight() + "）",
        b.getMeasuredHeight() < TALL_ENOUGH / 2);
    assertTrue("按钮高度也不该塌成 0", b.getMeasuredHeight() > 0);
  }
}
