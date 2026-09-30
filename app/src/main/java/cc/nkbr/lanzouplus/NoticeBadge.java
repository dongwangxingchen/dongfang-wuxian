package cc.nkbr.lanzouplus;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.TextView;

import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringAnimation;

/**
 * [DFWX] DFW-61：**未读红点**（自绘）。
 *
 * ## 为什么不抄现成的
 * 调研结论（参考库 `06/07-更新与公告UI动效专档.md`）：Material Components 的
 * `BadgeDrawable.java`（1527 行）里 `ValueAnimator / ObjectAnimator / SpringAnimation /
 * TimeInterpolator / setDuration` 出现次数 **= 0**——**官方角标根本没有动画**，
 * `setVisible` 只改状态。所以"丝滑"这件事官方没有现成实现，只能自绘。
 *
 * ## 动画模型（遵循规范 §2/§7.5）
 * - **入场**：`SPATIAL_DEFAULT 350/0.75` 一次 overshoot（规范允许 spatial 族轻微 overshoot，
 *   且明确禁止 `scale 1→.9→1.1→1` 那种三段人为 bounce）。数字从 0 直接出现，**不做滚动**——
 *   0→1 时滚动毫无信息量，纯属画蛇添足。
 * - **数字变化**：已显示时改数字做一次轻微 pop，让变化"被看见"，而不是硬切换。
 * - **退场**：缩到 0 后 `GONE`，避免留下一个占位空点。
 * - 全部走**非触摸**路径，所以允许用弹簧（触摸路径的红线是"禁弹簧回位、一律 VPA"，与此无关）。
 *
 * 同一 view **复用** SpringAnimation 实例：`animateToFinalPosition` 重定目标时速度连续，
 * 快速连续变更不会硬切（这正是 v1.22.3 给按压反馈定的同一套理由）。
 */
final class NoticeBadge extends TextView {

  /**
   * 入场弹簧 = 规范 §2 的 `SPATIAL_DEFAULT`（350/0.75）。
   * 项目里没有统一的动效常量类（各处直接内联），这里就近定义并标注出处，
   * 避免以后有人看到魔数不知道它从哪来。
   */
  private static final float ENTER_STIFFNESS = 350f;
  private static final float ENTER_DAMPING = 0.75f;

  private final SpringAnimation scaleXSpring;
  private final SpringAnimation scaleYSpring;
  private boolean motionEnabled = true;
  private int count;
  private boolean appeared;

  NoticeBadge(Context context) {
    super(context);
    setGravity(Gravity.CENTER);
    setTextSize(11);
    setTextColor(0xFFFFFFFF);
    setIncludeFontPadding(false);
    setSingleLine(true);
    setPadding(dp(6), 0, dp(6), 0);
    setMinWidth(dp(18));
    setMinHeight(dp(18));
    setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    setScaleX(0f);
    setScaleY(0f);
    setVisibility(GONE);
    scaleXSpring = new SpringAnimation(this, DynamicAnimation.SCALE_X, 1f);
    scaleYSpring = new SpringAnimation(this, DynamicAnimation.SCALE_Y, 1f);
    applySpring(scaleXSpring, ENTER_STIFFNESS, ENTER_DAMPING);
    applySpring(scaleYSpring, ENTER_STIFFNESS, ENTER_DAMPING);
  }

  private void applySpring(SpringAnimation spring, float stiffness, float damping) {
    spring.getSpring().setStiffness(stiffness).setDampingRatio(damping);
  }

  private int dp(int value) {
    return (int) (value * getResources().getDisplayMetrics().density + .5f);
  }

  /** 系统关掉动画时直接给终态（不能只靠 spring，否则用户会看到一个卡在 0 缩放的红点）。 */
  void setMotionEnabled(boolean enabled) {
    motionEnabled = enabled;
  }

  int count() { return count; }

  /**
   * 设置未读数。`0` 表示隐藏整个角标。
   *
   * 用户要求"有未读才出现"，所以 0 必须让角标**彻底消失**，而不是显示一个 0 或留个空位。
   */
  void setCount(int value) {
    int next = Math.max(0, value);
    boolean wasVisible = count > 0;
    count = next;
    String text = NoticeCenter.badgeText(next);
    if (!text.contentEquals(getText())) setText(text);
    setContentDescription(next <= 0 ? "" : ("未读公告 " + next + " 条"));
    if (next <= 0) {
      hide(wasVisible);
      return;
    }
    show(wasVisible);
  }

  private void show(boolean alreadyVisible) {
    setVisibility(VISIBLE);
    if (!motionEnabled) {
      setScaleX(1f);
      setScaleY(1f);
      appeared = true;
      return;
    }
    if (!alreadyVisible) {
      // 入场：从 0 由弹簧弹到 1，一次轻微 overshoot。
      setScaleX(0f);
      setScaleY(0f);
      scaleXSpring.animateToFinalPosition(1f);
      scaleYSpring.animateToFinalPosition(1f);
    } else {
      // 已显示时数字变了：从 0.86 弹回 1，做一次"被看见"的 pop。
      setScaleX(0.86f);
      setScaleY(0.86f);
      scaleXSpring.animateToFinalPosition(1f);
      scaleYSpring.animateToFinalPosition(1f);
    }
    appeared = true;
  }

  private void hide(boolean wasVisible) {
    if (!wasVisible || !appeared) {
      setVisibility(GONE);
      setScaleX(0f);
      setScaleY(0f);
      return;
    }
    if (!motionEnabled) {
      setVisibility(GONE);
      setScaleX(0f);
      setScaleY(0f);
      return;
    }
    scaleXSpring.animateToFinalPosition(0f);
    scaleYSpring.animateToFinalPosition(0f);
    // 缩到 0 再真正 GONE；否则会留一个不可见但仍占位的方块。
    postDelayed(() -> {
      if (count <= 0) setVisibility(GONE);
    }, 260);
  }

  /** 从 view 树摘下时取消弹簧，避免弹簧持着已废弃的 view 继续逐帧重绘。 */
  @Override protected void onDetachedFromWindow() {
    super.onDetachedFromWindow();
    scaleXSpring.cancel();
    scaleYSpring.cancel();
  }

  /** 红点底色：用本站主题的红，保证与配色同源（不用系统 `colorError`）。 */
  void applyTheme(int errorColor) {
    GradientDrawable shape = new GradientDrawable();
    shape.setColor(errorColor);
    shape.setCornerRadius(dp(11));
    setBackground(shape);
  }
}
