package cc.nkbr.lanzouplus;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.LinearLayout;

/** v1.5.0「高级苹果」液态玻璃底栏（研究依据 07-研究报告/苹果液态玻璃与材质交互-研究-v1.5.0.md P52-P58、
 *  苹果动效人格-研究-v1.5.0.md 规格表；只服务于 apple 主题，wide 平板布局不构建）。
 *  交互：长按（系统长按时长）底栏 → 玻璃胶囊从指下弹出（缩放弹入 + 触觉反馈）→ 跟手移动
 *  （translationX 直设 + 按速度液态拉伸 scaleX≤1.18/scaleY-10%/微旋转 4°）→ 途经 tab 整体微放大预高亮
 *  → 松手弹簧吸附（弱过冲曲线），吸附后回调切页。跟手动画永不缩放时长（R2 降级纪律）。
 *  玻璃视觉 = 半透明冷灰渐变 + 顶缘 1px 高光 + 投影（假磨砂标准配方；底栏为纯色，真模糊无细节可折，不引入 RenderEffect）。
 *  性能红线（R1）：玻璃面积 ≤ 屏 25%（胶囊约 6%）；静止零提交（只在拖拽/动画帧 invalidate）。 */
class GlassNavBar extends LinearLayout {
  /** 切页回调（复用 MainActivity.goToDestination 的分流与守卫） */
  interface Go {void to(int destination);}

  private static final int[] DESTINATIONS = {0, 4, 2, 5, 3}; // 软件库 / AI 对话 / 下载 / 工具箱 / 设置
  private final float density;
  private final Go go;
  private final int currentDestination;
  private final boolean motion;
  private final int longPressTimeout;

  private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final RectF capsule = new RectF();

  private Drawable bubble;                 // 挂在 ViewOverlay 上的玻璃胶囊
  private boolean dragging;                // 长按已激活，正在跟手
  private float grabOffset;                // 按点相对胶囊左缘的偏移（跟手不跳变）
  private float bubbleCenter;              // 胶囊中心 x（px）
  private float downRawX, downRawY, lastRawX, lastRawY;
  private int hoverIndex = -1;             // 途经预高亮的 tab
  private VelocityTracker tracker;
  private long downTime;

  GlassNavBar(Context context, float density, Go go, int currentDestination, boolean motion) {
    super(context);
    this.density = density;
    this.go = go;
    this.currentDestination = currentDestination;
    this.motion = motion;
    this.longPressTimeout = ViewConfiguration.getLongPressTimeout();
    fill.setShadowLayer(dp(5), 0, dp(2), 0x33000000);
  }

  private float dp(int v) {return v * density;}

  //—— 触控：onIntercept 负责长按判定，激活后整条事件流归胶囊 ————
  @Override public boolean onInterceptTouchEvent(MotionEvent ev) {
    switch (ev.getActionMasked()) {
      case MotionEvent.ACTION_DOWN:
        downRawX = ev.getRawX();downRawY = ev.getRawY();lastRawX = ev.getRawX();lastRawY = ev.getRawY();downTime = SystemClock.uptimeMillis();
        postDelayed(this::activate, longPressTimeout);
        break;
      case MotionEvent.ACTION_MOVE:
        lastRawX = ev.getRawX();lastRawY = ev.getRawY();
        if (!dragging && (Math.abs(ev.getRawX() - downRawX) > ViewConfiguration.get(getContext()).getScaledTouchSlop()
            || Math.abs(ev.getRawY() - downRawY) > ViewConfiguration.get(getContext()).getScaledTouchSlop() * 3)) cancelLp();
        break;
      default:
        cancelLp();
        if (dragging) {finishDrag();return true;}
        break;
    }
    return dragging;
  }

  @Override public boolean onTouchEvent(MotionEvent ev) {
    if (!dragging) return false;
    if (ev.getActionMasked() == MotionEvent.ACTION_MOVE) {
      if (tracker != null) tracker.addMovement(ev);
      dragTo(ev.getRawX());
    } else finishDrag();
    return true;
  }

  private void cancelLp() {removeCallbacks(this::activate);}

  /** 长按激活：玻璃胶囊在指下弹出（250ms 缩放弹入，R2 长按菜单规格）+ 触觉反馈 */
  private void activate() {
    if (getChildCount() < 2) return;
    dragging = true;
    tracker = VelocityTracker.obtain();
    tracker.addMovement(MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_DOWN, lastRawX, lastRawY, 0));
    int w = getWidth();
    float tabW = w / (float) getChildCount();
    float capW = Math.min(tabW * 0.86f, dp(96)), capH = dp(52);
    bubbleCenter = Math.max(capW / 2f, Math.min(w - capW / 2f, lastRawX));
    grabOffset = lastRawX - (bubbleCenter - capW / 2f);
    bubble = new GlassCapsule(capW, capH);
    bubble.setAlpha(0);
    getOverlay().add(bubble);
    layoutCapsule(capW, capH, 1f, 0f);
    if (motion) {// 弹入：scale 0.6→1 + alpha 淡入（R2 规格表#13 长按菜单：250ms Overshoot 1.2）
      final float pw = bubble.getBounds().width(), ph = bubble.getBounds().height();
      final float baseTop = (getHeight() - ph) / 2f;
      android.animation.ValueAnimator pop = android.animation.ValueAnimator.ofFloat(0, 1);
      pop.setDuration(250);
      pop.setInterpolator(new android.view.animation.OvershootInterpolator(1.2f));
      pop.addUpdateListener(a -> {
        float t = (float) a.getAnimatedValue();
        bubble.setAlpha(Math.round(255f * t));
        float s = 0.6f + 0.4f * t;
        int bw = Math.round(pw * s), bh = Math.round(ph * s);
        bubble.setBounds(Math.round(bubbleCenter - bw / 2f), Math.round(baseTop + (ph - bh) / 2f),
            Math.round(bubbleCenter + bw / 2f), Math.round(baseTop + (ph + bh) / 2f));
        postInvalidateOnAnimation();
      });
      pop.start();
    }
    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
    hoverIndex = hoverIndexOf(bubbleCenter);
    scaleHover(hoverIndex, 1.08f);
  }

  private void dragTo(float rawX) {
    lastRawX = rawX;
    if (bubble == null) return;
    int w = getWidth();
    float tabW = w / (float) getChildCount();
    float capW = Math.min(tabW * 0.86f, dp(96)), capH = dp(52);
    float half = capW / 2f;
    float target = rawX - grabOffset + half;
    float min = half, max = w - half;
    if (target < min) target = min + (target - min) * 0.6f;  // 两端软限位 ×0.6（R1）
    if (target > max) target = max + (target - max) * 0.6f;
    if (tracker != null) tracker.computeCurrentVelocity(1000, 8000);
    float vx = tracker != null ? tracker.getXVelocity() : 0;
    bubbleCenter = target;
    layoutCapsule(capW, capH, stretchOf(vx), vx);
    int index = hoverIndexOf(bubbleCenter);
    if (index != hoverIndex) {// 途经 tab 预高亮 + 越线轻触觉（R1 CLOCK_TICK）
      scaleHover(hoverIndex, 1f);
      hoverIndex = index;
      scaleHover(hoverIndex, 1.08f);
      performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
    }
    invalidateOverlay();
  }

  /** 液态拉伸（R1）：scaleX = 1+min(|vx|/6000,1)×0.18，scaleY -10%，微旋转 ±4° */
  private float stretchOf(float vx) {
    float t = Math.min(Math.abs(vx) / 6000f, 1f);
    return t;
  }

  private void layoutCapsule(float capW, float capH, float stretch, float vx) {
    float w = capW * (1 + 0.18f * stretch), h = capH * (1 - 0.10f * stretch);
    float left = bubbleCenter - w / 2f;
    float top = (getHeight() - capH) / 2f + (capH - h) / 2f;
    bubble.setBounds(Math.round(left), Math.round(top), Math.round(left + w), Math.round(top + h));
    if (bubble instanceof GlassCapsule) ((GlassCapsule) bubble).rotation = Math.max(-4f, Math.min(4f, vx / 6000f * 4f));
    invalidateOverlay();
  }

  private void invalidateOverlay() {postInvalidateOnAnimation();}

  /** 松手：吸附到最近 tab（弱过冲 300ms），吸附后切页，胶囊淡出移除 */
  private void finishDrag() {
    cancelLp();
    if (!dragging) return;
    dragging = false;
    if (tracker != null) {tracker.recycle();tracker = null;}
    final Drawable cap = bubble;
    bubble = null;
    if (cap == null) return;
    int w = getWidth();
    float tabW = w / (float) getChildCount();
    int target = Math.max(0, Math.min(getChildCount() - 1, Math.round((bubbleCenter - tabW / 2f) / tabW)));
    int destination = target < DESTINATIONS.length ? DESTINATIONS[target] : currentDestination;
    scaleHover(hoverIndex, 1f);
    hoverIndex = -1;
    final int startAlpha = cap.getAlpha();
    if (motion && destination != currentDestination) {// 吸附小回弹后淡出，再交回调切页
      android.animation.ValueAnimator snap = android.animation.ValueAnimator.ofFloat(0, 1);
      snap.setDuration(300);
      snap.setInterpolator(new android.view.animation.PathInterpolator(0.2f, 0.9f, 0.3f, 1.05f));
      snap.addUpdateListener(a -> {
        float t = (float) a.getAnimatedValue();
        cap.setAlpha(Math.round(startAlpha * (1 - t * t)));
        postInvalidateOnAnimation();
      });
      snap.addListener(new android.animation.AnimatorListenerAdapter() {
        @Override public void onAnimationEnd(android.animation.Animator a) {getOverlay().remove(cap);if (go != null) go.to(destination);}
      });
      snap.start();
    } else {
      getOverlay().remove(cap);
      if (go != null && destination != currentDestination) go.to(destination);
    }
  }

  private int hoverIndexOf(float centerX) {
    float tabW = getWidth() / (float) getChildCount();
    return Math.max(0, Math.min(getChildCount() - 1, (int) (centerX / tabW)));
  }

  private void scaleHover(int index, float scale) {
    if (index < 0 || index >= getChildCount()) return;
    View item = getChildAt(index);
    item.animate().cancel();
    if (motion) item.animate().scaleX(scale).scaleY(scale).setDuration(120).start();
    else {item.setScaleX(scale);item.setScaleY(scale);}
  }

  @Override protected void onDetachedFromWindow() {
    cancelLp();
    if (tracker != null) {tracker.recycle();tracker = null;}
    if (bubble != null) {getOverlay().remove(bubble);bubble = null;}
    dragging = false;
    super.onDetachedFromWindow();
  }

  /** 玻璃胶囊 Drawable：冷灰渐变 + 顶缘高光描边 + 投影（假磨砂配方，R1 P52-P58） */
  private final class GlassCapsule extends Drawable {
    final float capW, capH;
    float rotation;
    GlassCapsule(float capW, float capH) {this.capW = capW;this.capH = capH;}
    @Override public void draw(Canvas canvas) {
      RectF b = new RectF(getBounds());
      if (b.isEmpty()) return;
      float cx = b.centerX(), cy = b.centerY();
      canvas.save();
      canvas.rotate(rotation, cx, cy);
      RectF cap = new RectF(cx - capW / 2f, cy - capH / 2f, cx + capW / 2f, cy + capH / 2f);
      float r = capH / 2f;
      if (fill.getShader() == null) fill.setShader(new LinearGradient(0, cap.top, 0, cap.bottom,
          new int[]{0x99F4F9FF, 0x80DCE6F2, 0x8CCFDBEA}, new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
      canvas.drawRoundRect(cap, r, r, fill);
      stroke.setStyle(Paint.Style.STROKE);
      stroke.setStrokeWidth(Math.max(1, dp(1)));
      stroke.setColor(0x8CFFFFFF);
      canvas.drawRoundRect(cap, r, r, stroke);
      canvas.restore();
    }
    @Override public int getOpacity() {return android.graphics.PixelFormat.TRANSLUCENT;}
    @Override public void setAlpha(int alpha) {fill.setAlpha(alpha);stroke.setAlpha(alpha);invalidateSelf();}
    @Override public void setColorFilter(android.graphics.ColorFilter cf) {}
  }
}
