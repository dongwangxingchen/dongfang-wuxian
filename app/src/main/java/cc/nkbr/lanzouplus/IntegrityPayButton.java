package cc.nkbr.lanzouplus;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** 设置页顶部「诚信付费」独立按钮（v1.4.2，用户指定：不叫"支持"、放顶部单独一个大按钮才显眼、
 *  付费前不亮光 / 付费后亮光并循环播放简约扫光特效）。
 *  双状态由 Support.unlocked 驱动：未付费 = primary 底 + 「诚信付费 · 自愿」，点击进付费页；
 *  已付费 = primaryHi 亮底 + 白高光描边 + 「已诚信付费 ✓」+ 扫光循环，点击弹感谢弹窗（回调方区分）。
 *  标题左边一律带一枚奖杯图标（ic_trophy，与标题同色，装饰性不单独播报）——
 *  用户 2026-10-01 口述：两种状态都加、保持一致；未付费态的副标小字
 *  「￥5 · 学生免费 · 不付费也可完整使用」整行删除（只有已付费才显示「感谢支持 · 全部权限已开放」）。
 *  扫光 = 白色**水平**渐变亮带 translationX 从左扫到右（facebook/shimmer 遮罩扫光的纯 View 版），
 *  亮带铺满按钮整个高度、带宽随按钮宽度走，RESTART + LinearInterpolator，2600ms 一轮，
 *  峰值白光仍是 35%（与旧版同值，不变刺眼），再乘 alpha 包络 sin(πt)*0.9 → 实际峰值约 31%；
 *  t=0 时 alpha=0，快照/首帧无残影；动画关（motionEnabled=false）不启动，
 *  onDetachedFromWindow 取消，省电。
 *  <p>2026-10-01 真机反馈修复（用户逐字："白色的流光效果只覆盖了 2/3 的地方，看起来有那种割裂感"）。
 *  病根两条：
 *  ① 旧扫光层是**固定 40dp 高 + 垂直居中**，而按钮实测高约 63dp（JVM 实测 166px @420dpi，
 *     上下内距 12dp + 标题行 20dp + 副标约 15dp）—— 40/63≈63%，正好就是用户说的"2/3"：
 *     上下各留一条**永远扫不到**的硬边，这两条边就是割裂感的来源；
 *  ② 旧渐变是 TL_BR **斜向**，白光是一条斜带，在圆角矩形里被 setClipToOutline 切出斜硬边。
 *  修法：高度铺满整块按钮（见 onMeasure/onLayout），渐变改水平方向，亮带宽度随按钮宽度走。
 *  顺带修掉旧位移公式的"空转"：旧式 -sw + t*(w + 2sw) 会让亮带亮心在 t≈0.70 就移出按钮右缘
 *  （整条移出约 t≈0.79），之后约 30% 的周期按钮上基本无光；新式见 shineOffset。 */
public class IntegrityPayButton extends FrameLayout {
  private final boolean paid;
  private final boolean motionEnabled;
  /** 按钮密度：onLayout 里算亮带宽度下限用。 */
  private final float density;
  /** 扫光的绘制工具。**没有子 View**：见 dispatchDraw 的说明。 */
  private final Paint sweepPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Matrix sweepMatrix = new Matrix();
  private LinearGradient sweepShader;
  /** 当前扫光进度 0..1；<0 = 本帧不画（未付费 / 动画关 / 还没上屏）。 */
  float sweepT = -1f;
  /** 扫光动画器：null = 未启动或已取消。包级可见：同包 JVM 用例要断言"动画关/退场后必须停"。 */
  ValueAnimator shineAnimator;

  public IntegrityPayButton(Context context, ThemeEngine.Design design, float density,
                            boolean paid, boolean motionEnabled, Runnable onClick) {
    super(context);
    this.paid = paid;
    this.motionEnabled = motionEnabled;
    this.density = density;
    boolean apple = "apple".equals(design.id);
    int radius = apple ? 12 : 16;
    // 付费亮光态底色：legacy 的 primaryHi 本就是亮档；apple 的 primaryHi 是 iOS 按压态深蓝，
    // 改为向白混合 18% 的亮蓝（#007AFF→约 #2E93FF），符合「付费后亮光」的要求
    int paidFill = design.primaryHi;
    if (apple) {
      float m = 0.18f;
      paidFill = Color.rgb(
          Math.round(Color.red(design.primary) + (255 - Color.red(design.primary)) * m),
          Math.round(Color.green(design.primary) + (255 - Color.green(design.primary)) * m),
          Math.round(Color.blue(design.primary) + (255 - Color.blue(design.primary)) * m));
    }
    GradientDrawable bg = new GradientDrawable();
    bg.setCornerRadius(density * radius);
    bg.setColor(paid ? paidFill : design.primary);
    if (paid) bg.setStroke(Math.round(density), 0x66FFFFFF);
    setBackground(bg);
    setClipToOutline(true);
    setClickable(true);
    setFocusable(true);
    setPadding(Math.round(density * 16), Math.round(density * 12), Math.round(density * 16), Math.round(density * 12));
    int onPrimary = design.bg;
    int onPrimarySub = (design.bg & 0x00FFFFFF) | 0xB0000000;
    LinearLayout box = new LinearLayout(context);
    box.setOrientation(LinearLayout.VERTICAL);
    box.setGravity(Gravity.CENTER);
    addView(box, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
    // 标题行：奖杯图标 + 大字（用户 2026-10-01 口述：大字左边加一个 20dp 左右的奖杯，与标题同色、垂直居中，两种状态都加）
    LinearLayout titleRow = new LinearLayout(context);
    titleRow.setOrientation(LinearLayout.HORIZONTAL);
    titleRow.setGravity(Gravity.CENTER);
    box.addView(titleRow, new LinearLayout.LayoutParams(-2, -2));
    ImageView trophy = new ImageView(context);
    trophy.setImageResource(R.drawable.ic_trophy);
    trophy.setColorFilter(onPrimary);
    trophy.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    titleRow.addView(trophy, new LinearLayout.LayoutParams(Math.round(density * 20), Math.round(density * 20)));
    TextView title = new TextView(context);
    title.setText(paid ? "已诚信付费 ✓" : "诚信付费 · 自愿");
    title.setTextColor(onPrimary);
    title.setTextSize(16);
    title.setTypeface(AppFonts.bold(getContext()));
    LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(-2, -2);
    titleLp.leftMargin = Math.round(density * 6);
    titleRow.addView(title, titleLp);
    // 用户 2026-10-01 口述：未付费态那行小字（原「￥5 · 学生免费 · 不付费也可完整使用」）整行删除，直接不加 View；
    // 已付费态的「感谢支持 · 全部权限已开放」保留。
    if (paid) {
      TextView sub = new TextView(context);
      sub.setText("感谢支持 · 全部权限已开放");
      sub.setTextColor(onPrimarySub);
      sub.setTextSize(11);
      sub.setTypeface(AppFonts.normal(getContext()));
      LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-2, -2);
      subLp.topMargin = Math.round(density * 3);
      box.addView(sub, subLp);
    }
    setContentDescription(paid ? "已诚信付费，全部权限已开放" : "诚信付费，自愿支持开发");
    setOnClickListener(v -> { if (onClick != null) onClick.run(); });
  }

  // ── 扫光：直接画，不用子 View ────────────────────────────────────────────
  //
  // 2026-10-01 第二次真机反馈（用户逐字："它那个流光效果并没有在诚信付费上面做的很好。
  // 你要适配就给它适配好，你别在这里整的只有 2/3 的地方，而且还有明显的矩形感觉"）。
  //
  // 上一版的做法是**加一个子 View 当亮带**再平移它，两个问题都躲不掉：
  //  ① 子 View 有确定宽度，它的渐变只能铺在这个宽度里 → 亮带在视觉上就是一块**矩形**；
  //  ② 更致命的是透明度包络 `sin(πt)`：t=0/1 时 alpha=0，而亮带恰好在那两个时刻扫过按钮的
  //     **左右两端** → 两端永远只被"半亮"扫过；亮度峰值（t=0.5）时亮带只覆盖中间 62%，
  //     正好就是用户说的"只覆盖 2/3 的地方"。
  //
  // 现在改成在 dispatchDraw 里用一个**铺满按钮宽度**的 LinearGradient 直接画：
  //  - 没有子 View，就没有"亮带矩形"这个概念，只有一段会移动的柔光；
  //  - 位移把亮带从"完全在左缘外"扫到"完全在右缘外"，全程不做透明度包络 ——
  //    按钮上每一点都在**峰值亮度**下被扫过一次，两端也不例外；
  //  - 画在 super.dispatchDraw 之前 = 在底色之上、文字之下，文字不会被洗白。

  /** 亮带宽度：按钮宽的一半（下限 96dp）。够宽才像"一片光扫过"，不至于细成一条线。 */
  float shineBandWidth(float buttonWidth) {
    return Math.max(density * 96f, buttonWidth * 0.5f);
  }

  /** 扫光位移（纯函数，方便 JVM 用例推演几何）：t=0 时整条亮带在按钮左缘之外，t=1 时在右缘之外。 */
  static float shineOffset(float t, float buttonWidth, float bandWidth) {
    return -bandWidth + t * (buttonWidth + bandWidth);
  }

  /** 峰值白光：仍是 0x59（35% 白）—— 与最初版本同值，所以不会比现在更刺眼。 */
  static int shinePeakAlpha() { return 0x59; }

  private void ensureSweepShader(int width) {
    float band = shineBandWidth(width);
    if (sweepShader != null && sweepShaderWidth == band) return;
    sweepShaderWidth = band;
    // 亮核窄、肩部长：读起来是"一道光扫过"，而不是"一块亮斑平移"。
    sweepShader = new LinearGradient(0f, 0f, band, 0f,
        new int[]{0x00FFFFFF, 0x0EFFFFFF, shinePeakAlpha() << 24 | 0x00FFFFFF, 0x0EFFFFFF, 0x00FFFFFF},
        new float[]{0f, 0.35f, 0.5f, 0.65f, 1f},
        Shader.TileMode.CLAMP);
  }
  private float sweepShaderWidth = -1f;

  @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
    super.onSizeChanged(w, h, oldw, oldh);
    sweepShader = null;
    sweepShaderWidth = -1f;
  }

  @Override protected void dispatchDraw(Canvas canvas) {
    if (paid && motionEnabled && sweepT >= 0f) {
      int width = getWidth(), height = getHeight();
      if (width > 0 && height > 0) {
        ensureSweepShader(width);
        if (sweepShader != null) {
          float band = shineBandWidth(width);
          sweepMatrix.setTranslate(shineOffset(sweepT, width, band), 0f);
          sweepShader.setLocalMatrix(sweepMatrix);
          sweepPaint.setShader(sweepShader);
          canvas.drawRect(0f, 0f, width, height, sweepPaint);
        }
      }
    }
    super.dispatchDraw(canvas);
  }

  @Override protected void onAttachedToWindow() {
    super.onAttachedToWindow();
    if (paid && motionEnabled) startShine();
  }

  @Override protected void onDetachedFromWindow() {
    if (shineAnimator != null) {shineAnimator.cancel();shineAnimator = null;}
    sweepT = -1f;
    super.onDetachedFromWindow();
  }

  private void startShine() {
    if (shineAnimator != null) return;
    shineAnimator = ValueAnimator.ofFloat(0f, 1f);
    shineAnimator.setDuration(2600);
    shineAnimator.setInterpolator(new android.view.animation.LinearInterpolator());
    shineAnimator.setRepeatCount(ValueAnimator.INFINITE);
    shineAnimator.setRepeatMode(ValueAnimator.RESTART);
    shineAnimator.addUpdateListener(a -> {
      sweepT = (float) a.getAnimatedValue();
      invalidate();
    });
    shineAnimator.start();
  }
}
