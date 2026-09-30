package cc.nkbr.lanzouplus;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RoundRectShape;
import android.os.Build;
import android.view.WindowManager;

/**
 * [DFWX] DFW-72：**Liquid Glass 玻璃表面**——浮层（弹窗/面板）的材质实现。
 *
 * ## 为什么重做
 * 用户 2026-10-01 真机反馈："公告做的太丑了，质感也有问题……给我感觉雾蒙蒙的，没有通透感。"
 *
 * "雾蒙蒙"的病理是：**不透明深灰 + 纯色填充**。它没有"透"，也没有"边"，
 * 于是不管圆角多准、间距多规整，都只是一块灰板子。通透感需要三样东西同时成立：
 *   ① 背后真的有东西（**真实的背景模糊**，不是画一层半透明灰）
 *   ② 玻璃本体足够透（填充 alpha 要低到能看见背后的形状）
 *   ③ 边缘有光（顶光照亮的 rim + 镜面高光）——**这一条最关键**，没有它整块就是灰板
 *
 * ## 证据来源（不是拍脑袋）
 * - **AOSP《Window blurs》**（source.android.com/docs/core/display/window-blurs）：
 *   背景模糊 `80px` 是官方给的"毛玻璃"档，blur behind `20px` 是"景深"档，
 *   **超过 150px 会显著影响性能**；圆角必须靠"给窗口设带圆角的 ShapeDrawable 背景"实现；
 *   窗口必须 translucent 模糊才可见；**系统可能随时关掉模糊（省电模式等），
 *   这时要按官方指引提高背景 drawable 的 alpha / 加大 dim**，并用
 *   `WindowManager#addCrossWindowBlurEnabledListener` 监听开关。
 * - **API 存在性用 `javap` 实查 `android-37.0/android.jar` 确认**：
 *   `Window.setBackgroundBlurRadius(int)`、`WindowManager.LayoutParams.FLAG_BLUR_BEHIND`、
 *   `setBlurBehindRadius`、`WindowManager.isCrossWindowBlurEnabled()` 全部在。
 * - 开源参照：`Kyant0/AndroidLiquidGlass`(3970★)、`chrisbanes/haze`(2573★)、
 *   `styropyr0/Prismal`(MIT, Android)、`conorluddy/LiquidGlassReference`(iOS 26 规格)。
 *   本类抄的是它们的**光学配方**：主体高光自上而下淡出、顶光 rim 最亮、
 *   对侧边缘同时微亮（Prismal 的 "dual border rim highlights"）、primary 不透明 / secondary 透。
 *
 * ## 明确不采纳的
 * Prismal 用弹簧物理做按压反馈——**不抄**。本项目 v1.19.8 有过触摸路径物理弹簧
 * 导致真机 ANR 的教训，红线是"触摸路径一律 VPA"。
 */
final class GlassSurface {

  private GlassSurface() {}

  // ── 模糊半径（全部来自 AOSP 官方推荐值，只做密度换算）────────────────────

  /** AOSP：背景模糊 80px = 好的毛玻璃效果。 */
  static final int BLUR_BG_BASE_PX = 80;
  /** AOSP：blur behind 20px = 好的景深效果。 */
  static final int BLUR_BEHIND_BASE_PX = 20;
  /** AOSP：**不要超过 150px**，否则明显掉性能。 */
  static final int BLUR_MAX_PX = 150;
  /** 官方推荐值是在 3.0 密度基准上调出来的，其它密度按比例换算，保证观感一致。 */
  static final float BLUR_REFERENCE_DENSITY = 3f;

  /**
   * 把官方基准像素值换算到当前屏幕。
   *
   * 模糊半径是**屏幕像素**量（合成器参数），不是 dp。若直接照抄 80px，
   * 在 2.0 密度的机器上视觉上会比 3.0 密度的机器糊一倍，所以按密度等比换算。
   */
  static int blurPx(int basePx, float density, int minPx, int maxPx) {
    if (basePx <= 0) return 0;
    float d = density <= 0f ? BLUR_REFERENCE_DENSITY : density;
    int px = Math.round(basePx * (d / BLUR_REFERENCE_DENSITY));
    if (px < minPx) px = minPx;
    int ceiling = Math.min(maxPx, BLUR_MAX_PX);
    return Math.min(px, ceiling);
  }

  /** 玻璃面板自身的背景模糊（"毛玻璃"档）。 */
  static int backgroundBlurPx(float density) {
    return blurPx(BLUR_BG_BASE_PX, density, 24, BLUR_MAX_PX);
  }

  /** 弹窗身后整屏的景深模糊（"blur behind" 档）。 */
  static int blurBehindPx(float density) {
    return blurPx(BLUR_BEHIND_BASE_PX, density, 8, 64);
  }

  /** 窗口模糊 API 只在 Android 12（API 31）起存在。 */
  static boolean blurApiAvailable() {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S;
  }

  /**
   * 当前设备此刻是否真的会做窗口模糊。
   *
   * 三层条件都要过：系统版本、GPU 能力、**系统当前状态**（省电模式/开发者选项/播放视频时
   * 系统会临时关掉）。所以这个值必须**运行时查**，不能缓存、不能假设。
   */
  static boolean isBlurEnabled(Context context) {
    if (!blurApiAvailable() || context == null) return false;
    try {
      WindowManager manager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
      return manager != null && manager.isCrossWindowBlurEnabled();
    } catch (Throwable ignored) {
      return false;
    }
  }

  // ── 玻璃本体的填充（模糊开/关两套，AOSP "Handle blur enabled and disabled states"）──

  /** 模糊可用时：填充要**够透**，背后的形状才看得见（这是"通透感"的来源）。 */
  static final float FILL_ALPHA_BLUR = 0.48f;
  /** 模糊不可用时：按官方指引提高不透明度，否则文字会直接压在画面内容上、读不清。 */
  static final float FILL_ALPHA_NO_BLUR = 0.96f;
  /** 有模糊时维度可以轻一点（模糊本身已经在压暗背景）。 */
  static final float DIM_BLUR = 0.30f;
  /** 没模糊时按官方指引加大 dim，把注意力重新拉回弹窗。 */
  static final float DIM_NO_BLUR = 0.52f;
  /** 玻璃底色相对屏幕底色提亮的一档（中性、不用品牌色——用户明确说过发紫=廉价）。 */
  static final float FILL_LIFT = 0.06f;
  /**
   * 降级（无模糊）底色的提亮档。
   *
   * 比玻璃档高一档是有原因的：有模糊时背景本身提供了层次，底色再亮就发灰；
   * 没模糊时面板是一块**不透明**的板，得靠自身亮度与纯黑背景拉开层级，否则"糊成一片黑"。
   */
  static final float FALLBACK_LIFT = 0.12f;

  static float fillAlpha(boolean blurEnabled) {
    return blurEnabled ? FILL_ALPHA_BLUR : FILL_ALPHA_NO_BLUR;
  }

  static float dimAmount(boolean blurEnabled) {
    return blurEnabled ? DIM_BLUR : DIM_NO_BLUR;
  }

  /** 玻璃本体底色（中性微提亮，不透明）。 */
  static int glassFill(int bg) {
    return PremiumSurface.over(bg, Color.WHITE, FILL_LIFT);
  }

  /** 无模糊时的降级底色（中性、更亮一档）。 */
  static int opaqueFill(int bg) {
    return PremiumSurface.over(bg, Color.WHITE, FALLBACK_LIFT);
  }

  /** 窗口背景色的 ARGB：按模糊可用与否选底色，再整体套上对应 alpha。 */
  static int windowFillColor(int bg, boolean blurEnabled) {
    return withAlpha(blurEnabled ? glassFill(bg) : opaqueFill(bg), fillAlpha(blurEnabled));
  }

  static int withAlpha(int color, float alpha) {
    return Color.argb(Math.round(255 * clamp01(alpha)), Color.red(color), Color.green(color), Color.blue(color));
  }

  /**
   * **窗口背景**：带圆角的半透明 ShapeDrawable。
   *
   * 这一步不是装饰——AOSP 明确说"圆角窗口要让模糊区域跟着圆，就把带圆角的 ShapeDrawable
   * 设为窗口背景 drawable"。模糊是画在窗口 surface **下面**的，窗口背景决定它的可见形状，
   * 所以圆角只能写在这里；写在面板 View 上模糊会溢出一个直角方块。
   */
  static Drawable windowBackground(int radiusPx, int argb) {
    float r = Math.max(0f, radiusPx);
    RoundRectShape shape = new RoundRectShape(new float[]{r, r, r, r, r, r, r, r}, null, null);
    ShapeDrawable drawable = new ShapeDrawable(shape);
    drawable.getPaint().setAntiAlias(true);
    drawable.getPaint().setColor(argb);
    return drawable;
  }

  // ── 玻璃的边缘与高光（"通透感"最关键的一层）──────────────────────────────

  /** 顶光 rim 最亮处（规范 §5：高光/玻璃描边白 18–26%，只出现一侧）。 */
  static final float RIM_TOP = 0.24f;
  /** 对侧 rim 同时微亮（Prismal：抛光玻璃的两条边会一起反光，只是强弱不同）。 */
  static final float RIM_BOTTOM = 0.07f;
  /** 主体高光：贴着顶边的白，向下一路淡出。 */
  static final float SHEEN = 0.10f;
  /** 主体高光淡出到零的位置（面板高度的比例）。 */
  static final float SHEEN_STOP = 0.42f;
  /** 镜面热点：上边缘一道横向亮带（真实玻璃的反光，只出现在边缘上）。 */
  static final float SPOT_PEAK = 0.30f;
  /** 镜面热点亮带的纵向范围（面板高度的比例）。 */
  static final float SPOT_BAND = 0.30f;
  /** 镜面热点横向峰值位置：略偏左上——光从左上打过来。 */
  static final float SPOT_CENTER = 0.30f;
  /** 镜面热点横向衰减到的位置。 */
  static final float SPOT_END = 0.78f;

  /** rim 在纵向 `fraction` 处的白 alpha（0 = 顶边，1 = 底边）。纯函数，可直接断言。 */
  static float rimAlphaAt(float fraction) {
    float f = clamp01(fraction);
    return RIM_TOP + (RIM_BOTTOM - RIM_TOP) * f;
  }

  /** 主体高光在纵向 `fraction` 处的白 alpha。纯函数。 */
  static float sheenAlphaAt(float fraction) {
    float f = clamp01(fraction);
    if (f >= SHEEN_STOP) return 0f;
    return SHEEN * (1f - f / SHEEN_STOP);
  }

  /** 镜面热点在横向 `fraction` 处的白 alpha。纯函数。 */
  static float spotAlphaAt(float fraction) {
    float f = clamp01(fraction);
    if (f <= 0f || f >= SPOT_END) return 0f;
    if (f <= SPOT_CENTER) return SPOT_PEAK * (f / SPOT_CENTER);
    return SPOT_PEAK * (1f - (f - SPOT_CENTER) / (SPOT_END - SPOT_CENTER));
  }

  /**
   * 玻璃面板背景：**只有边缘与高光，内部全透明**。
   *
   * 内部必须透明——玻璃的底色和模糊由窗口背景负责（见 {@link #windowBackground}），
   * 这里再填一层就会把模糊盖死，又变回"一块灰板子"。
   */
  static Drawable panel(int radiusPx, float strokePx) {
    return new PanelDrawable(radiusPx, strokePx);
  }

  private static float clamp01(float v) {
    return v < 0f ? 0f : (v > 1f ? 1f : v);
  }

  /** 玻璃面板的边缘绘制：主体高光（面）+ 镜面热点（边）+ 上下不对称 rim（边）。 */
  static final class PanelDrawable extends Drawable {

    private final float radius;
    private final float stroke;
    private final Paint sheenPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint spotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    private int builtWidth = -1, builtHeight = -1;

    PanelDrawable(float radiusPx, float strokePx) {
      radius = Math.max(0f, radiusPx);
      stroke = Math.max(1f, strokePx);
      rimPaint.setStyle(Paint.Style.STROKE);
      rimPaint.setStrokeWidth(stroke);
      spotPaint.setStyle(Paint.Style.STROKE);
      spotPaint.setStrokeWidth(stroke);
    }

    @Override public void draw(Canvas canvas) {
      Rect bounds = getBounds();
      if (bounds.isEmpty()) return;
      float inset = stroke / 2f;
      rect.set(bounds.left + inset, bounds.top + inset, bounds.right - inset, bounds.bottom - inset);
      if (rect.width() <= 0f || rect.height() <= 0f) return;
      float r = Math.min(radius, Math.min(rect.width(), rect.height()) / 2f);
      if (builtWidth != bounds.width() || builtHeight != bounds.height()) buildShaders(bounds.width(), bounds.height(), r);

      // ① 面：顶光打下来的主体高光，向下淡出（让平面产生"斜面被照亮"的错觉）
      canvas.drawRoundRect(rect, r, r, sheenPaint);
      // ② 边：上边缘的镜面热点，只画边不画面（画成面会把整块顶部洗白）
      canvas.save();
      canvas.clipRect(rect.left, rect.top, rect.right, rect.top + rect.height() * SPOT_BAND);
      canvas.drawRoundRect(rect, r, r, spotPaint);
      canvas.restore();
      // ③ 边：上亮下暗的不对称 rim（上边被顶光照到，对侧同时微亮）
      canvas.drawRoundRect(rect, r, r, rimPaint);
    }

    private void buildShaders(int width, int height, float r) {
      builtWidth = width;
      builtHeight = height;
      sheenPaint.setShader(new LinearGradient(0f, rect.top, 0f, rect.top + rect.height() * SHEEN_STOP,
          white(sheenAlphaAt(0f)), white(0f), Shader.TileMode.CLAMP));
      spotPaint.setShader(new LinearGradient(rect.left, 0f, rect.right, 0f,
          new int[]{white(spotAlphaAt(0f)), white(spotAlphaAt(SPOT_CENTER)), white(0f)},
          new float[]{0f, SPOT_CENTER, SPOT_END}, Shader.TileMode.CLAMP));
      rimPaint.setShader(new LinearGradient(0f, rect.top, 0f, rect.bottom,
          white(rimAlphaAt(0f)), white(rimAlphaAt(1f)), Shader.TileMode.CLAMP));
    }

    private static int white(float alpha) {
      return Color.argb(Math.round(255 * clamp01(alpha)), 255, 255, 255);
    }

    @Override public void getOutline(Outline outline) {
      Rect bounds = getBounds();
      if (bounds.isEmpty()) return;
      if (radius > 0f) outline.setRoundRect(bounds.left, bounds.top, bounds.right, bounds.bottom, radius);
      else outline.setRect(bounds);
    }

    @Override public void setAlpha(int alpha) {
      sheenPaint.setAlpha(alpha);
      spotPaint.setAlpha(alpha);
      rimPaint.setAlpha(alpha);
      invalidateSelf();
    }

    @Override public void setColorFilter(ColorFilter colorFilter) {
      sheenPaint.setColorFilter(colorFilter);
      spotPaint.setColorFilter(colorFilter);
      rimPaint.setColorFilter(colorFilter);
      invalidateSelf();
    }

    @Override public int getOpacity() {
      return PixelFormat.TRANSLUCENT;
    }
  }
}
