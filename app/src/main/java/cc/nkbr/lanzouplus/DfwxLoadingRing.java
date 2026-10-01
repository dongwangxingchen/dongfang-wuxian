package cc.nkbr.lanzouplus;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.view.animation.LinearInterpolator;

/**
 * [DFW-54] 全站唯一的加载环（自绘，替掉系统 {@code ProgressBar}）。
 *
 * ## 为什么不用系统 ProgressBar
 * 目录页那个紫色圆环一直是 {@code ProgressBar(progressBarStyleSmall)}，它的颜色来自
 * {@code styles.xml} 的 {@code colorAccent}，形态/粗细/节奏都不受我们控制，
 * 而且同一个 View 在"跟手拖动"时是 determinate、松手后切 indeterminate —— 切换时
 * 系统会重建 drawable，视觉上就是**形态突变**。
 * 项目规范也早就写了"弃默认 ProgressBar，自绘 2–2.5dp 加载环"。
 *
 * ## 节奏为什么是 650ms
 * 这个数不是拍的：Material 3 Expressive 官方 LoadingIndicator 的 morph 周期实测就是
 * {@code 650ms}（从本机 gradle 缓存的 MDC 1.14.0 aar 里反出来的）。
 * 我们不复刻它的多边形 morph（那要引入 graphics-shapes + 几百行插值），只借它的节奏。
 *
 * ## 弧长为什么要"呼吸"
 * 恒定弧长匀速转 = 廉价感。这里用一个三角波让扫过角在 60°↔300° 之间往返，
 * 同时整体匀速旋转 —— 就是 AOSP 原生那个"呼吸弧"，算法思路参考 Android-SpinKit（MIT）。
 * 全程**只有一个** {@code drawArc} 调用点，杜绝 if/else 双形态瞬切。
 *
 * ## 生命周期
 * 照 {@code DfwxSkeleton} 的写法：attach 起、detach 停；关掉动效时画一条静态弧。
 */
final class DfwxLoadingRing extends View {
  /** 一圈的周期（M3E 官方 morph 节奏实测值）。 */
  static final long SPIN_MS = 650L;
  /** 弧长呼吸的下限与上限（度）。 */
  static final float SWEEP_MIN = 60f;
  static final float SWEEP_MAX = 300f;

  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final RectF arc = new RectF();
  private final float stroke;
  private final boolean motion;
  private float phase;
  private float progress;
  private boolean spinning;
  private ValueAnimator animator;

  DfwxLoadingRing(Context context, int color, float strokeDp, boolean motion) {
    super(context);
    this.motion = motion;
    this.stroke = strokeDp * getResources().getDisplayMetrics().density;
    paint.setStyle(Paint.Style.STROKE);
    paint.setStrokeWidth(this.stroke);
    paint.setStrokeCap(Paint.Cap.ROUND);
    paint.setColor(color);
    setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
  }

  /** 换色（主题切换时用）。 */
  void setRingColor(int color) { paint.setColor(color); invalidate(); }

  /**
   * 跟手进度（0..1）：不转，弧长随手指推进。
   *
   * 为什么要和"转"共用一个 View：旧实现是系统 ProgressBar 的
   * determinate → indeterminate 切换，那会让系统**重建 drawable**，
   * 视觉上就是松手瞬间形态突变一下。这里同一个环、同一套画笔，只是弧长来源不同。
   */
  void setRingProgress(float value) {
    progress = value < 0f ? 0f : (value > 1f ? 1f : value);
    invalidate();
  }

  /** 开始/停止旋转。停止后环停在静态弧上。 */
  void setRingSpinning(boolean value) {
    if (spinning == value) return;
    spinning = value;
    if (value) startSpin(); else stopSpin();
    invalidate();
  }

  /** 当前扫过角（度）：三角波，前 1/3 涨到上限、后 2/3 收回下限。 */
  static float sweepFor(float t) {
    float p = t - (float) Math.floor(t);
    float tri = p < (1f / 3f) ? p * 3f : (1f - p) * 1.5f;
    return SWEEP_MIN + (SWEEP_MAX - SWEEP_MIN) * tri;
  }

  @Override protected void onAttachedToWindow() {
    super.onAttachedToWindow();
    startSpin();
  }

  @Override protected void onDetachedFromWindow() {
    stopSpin();
    super.onDetachedFromWindow();
  }

  void startSpin() {
    if (!motion || animator != null) return;
    spinning = true;
    animator = ValueAnimator.ofFloat(0f, 1f);
    animator.setDuration(SPIN_MS);
    animator.setRepeatCount(ValueAnimator.INFINITE);
    animator.setRepeatMode(ValueAnimator.RESTART);
    animator.setInterpolator(new LinearInterpolator());
    animator.addUpdateListener(a -> { phase = (float) a.getAnimatedValue(); invalidate(); });
    animator.start();
  }

  void stopSpin() {
    if (animator != null) { animator.cancel(); animator = null; }
    spinning = false;
  }

  @Override protected void onDraw(Canvas canvas) {
    float w = getWidth(), h = getHeight();
    if (w <= 0 || h <= 0) return;
    float inset = stroke / 2f + 1f;
    arc.set(inset, inset, w - inset, h - inset);
    float start = spinning ? phase * 360f - 90f : -90f;
    float sweep = spinning ? sweepFor(phase) : (SWEEP_MIN + (SWEEP_MAX - SWEEP_MIN) * progress);
    canvas.drawArc(arc, start, sweep, false, paint);
  }

  @Override protected void onMeasure(int widthSpec, int heightSpec) {
    int size = (int) Math.ceil(stroke * 2f + getResources().getDisplayMetrics().density * 16f);
    setMeasuredDimension(resolveSize(size, widthSpec), resolveSize(size, heightSpec));
  }
}
