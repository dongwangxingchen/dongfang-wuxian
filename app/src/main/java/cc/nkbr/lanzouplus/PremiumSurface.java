package cc.nkbr.lanzouplus;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;

/**
 * [DFWX] DFW-68：**高级表面绘制**——把规范 §5 的 `PremiumSurfaceDrawable` 配方落地。
 *
 * ## 为什么需要它
 * 规范 `docs/design/wear-ui-system.md` §5 早就写明了配方：
 * > PremiumSurfaceDrawable：baseFill（带色半透明）+ stroke 0.5–1dp + **topHighlight（左上→右下极弱渐变）**
 * > + pressedFill（+2~4% luminance）+ selectedTint + cornerMode
 * 以及 OLED 亮度阶梯：
 * > Screen #000000 / Surface Low 5–7% / High 8–10% / Selected 品牌色 16–24% tint /
 * > **普通描边白 8–12%；高光/玻璃描边白 18–26%，只出现一侧**
 *
 * **但它从未被实现**——全仓没有这个类。于是各处只能直接用 `solidShape()` 纯色填充，
 * 结果就是"一块平的深灰紫"：没有边缘定义、没有层次、没有质感。
 *
 * ## 核心手法：用"极弱渐变"造出材质感
 * 纯黑底上 `elevation` 阴影几乎不可见（这本身就是规范 §5 说的"不用 elevation 阴影"的原因）。
 * 所以层级与质感必须靠**填充色的亮度差**和**单侧高光**表达：
 * 一块从上左到下右由亮到暗的极弱渐变，就能让平面产生"被光打到的斜面"错觉。
 *
 * ## 与 M3 的关系
 * M3 用 `surfaceContainer*` 五档表达层级、用 state layer 表达交互态，思路同源。
 * 本类不引 Material 库，只抄"分层 + 状态叠加"的原理（见 `M3Tokens` 的调研结论）。
 */
final class PremiumSurface {

  private PremiumSurface() {}

  // ── 颜色运算（纯函数，可直接测）────────────────────────────────────────────

  /**
   * 把半透明色 `tint` 按 `alpha` 叠到不透明底色 `base` 上，返回不透明结果。
   *
   * 为什么不直接用带 alpha 的颜色：多个半透明层叠在一起时，最终颜色取决于**下面还有什么**，
   * 换个背景就变样。算成不透明色可以让"这块表面长什么样"完全确定、可预期、可测。
   */
  static int over(int base, int tint, float alpha) {
    float a = alpha < 0f ? 0f : (alpha > 1f ? 1f : alpha);
    int r = Math.round(Color.red(base) + (Color.red(tint) - Color.red(base)) * a);
    int g = Math.round(Color.green(base) + (Color.green(tint) - Color.green(base)) * a);
    int b = Math.round(Color.blue(base) + (Color.blue(tint) - Color.blue(base)) * a);
    return Color.rgb(clamp255(r), clamp255(g), clamp255(b));
  }

  /** 提亮：朝白色混 `ratio`。用于"左上角被光照到"的那一端。 */
  static int lighten(int color, float ratio) {
    return over(color, Color.WHITE, ratio);
  }

  /** 压暗：朝黑色混 `ratio`。 */
  static int darken(int color, float ratio) {
    return over(color, Color.BLACK, ratio);
  }

  private static int clamp255(int v) { return v < 0 ? 0 : (v > 255 ? 255 : v); }

  // ── 表面构造 ─────────────────────────────────────────────────────────────

  /**
   * 带单侧高光的圆角表面——规范 §5 的 `topHighlight`。
   *
   * @param fill    底色（应是不透明色，用 {@link #over} 先合成好）
   * @param radiusPx 圆角像素（可以是小数，胶囊就是高度的一半）
   * @param strokePx 描边像素（0 = 不画）
   * @param strokeColor 描边色
   * @param highlightRatio 高光强度：上左端相对底色提亮的比例（规范建议 0.06–0.12，极弱）
   */
  static GradientDrawable withHighlight(int fill, float radiusPx, int strokePx, int strokeColor, float highlightRatio) {
    GradientDrawable g;
    if (highlightRatio <= 0f) {
      g = new GradientDrawable();
      g.setColor(fill);
    } else {
      // TL_BR = 左上 → 右下。上左提亮、下右回到原色，产生"被光斜照"的斜面感。
      g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
          new int[]{lighten(fill, highlightRatio), fill});
    }
    g.setCornerRadius(radiusPx);
    if (strokePx > 0) g.setStroke(strokePx, strokeColor);
    return g;
  }

  /** 胶囊（全圆）表面。半径取高度一半。 */
  static GradientDrawable pill(int fill, int heightPx, int strokePx, int strokeColor, float highlightRatio) {
    return withHighlight(fill, heightPx / 2f, strokePx, strokeColor, highlightRatio);
  }

  /** 圆形表面（球）。 */
  static GradientDrawable circle(int fill, int strokePx, int strokeColor, float highlightRatio) {
    GradientDrawable g;
    if (highlightRatio <= 0f) {
      g = new GradientDrawable();
      g.setColor(fill);
    } else {
      g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
          new int[]{lighten(fill, highlightRatio), fill});
    }
    g.setShape(GradientDrawable.OVAL);
    if (strokePx > 0) g.setStroke(strokePx, strokeColor);
    return g;
  }

  // ── 规范 §5 的亮度阶梯（把"层级"变成可调用的常量）──────────────────────────

  /** Screen 层：#000000（真黑）。 */
  static final float LEVEL_SCREEN = 0f;
  /** Surface Low：5–7%。 */
  static final float LEVEL_SURFACE_LOW = 0.06f;
  /** Surface High：8–10%。 */
  static final float LEVEL_SURFACE_HIGH = 0.09f;
  /** Surface 更高一档（规范允许至 11–14%）。 */
  static final float LEVEL_SURFACE_HIGHER = 0.12f;
  /** Selected：品牌色 16–24% tint。 */
  static final float LEVEL_SELECTED = 0.20f;
  /** 普通描边：白 8–12%。 */
  static final float STROKE_NORMAL = 0.10f;
  /** 高光/强调描边：白 18–26%（或品牌色等效强度）。 */
  static final float STROKE_EMPHASIS = 0.22f;
  /** 高光强度（左上端提亮比例）。 */
  static final float HIGHLIGHT = 0.08f;

  /** 在纯黑底上按亮度阶梯取一档表面色（用品牌色 tint，保留品牌调性）。 */
  static int surfaceOnBlack(int brandColor, float level) {
    return over(Color.BLACK, brandColor, level);
  }
}
