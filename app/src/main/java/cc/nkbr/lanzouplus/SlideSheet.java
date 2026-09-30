package cc.nkbr.lanzouplus;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;

/**
 * [DFWX] DFW-65/66：**可滑动面板**——从屏幕某一边滑入、可滑走关闭。
 *
 * ## 两个方向，一套手势
 * 用户 2026-09-30 明确要求两种：
 * - **更新弹窗**："从底部往上升，丝滑"→ {@link Edge#BOTTOM}
 * - **提示条**："从顶部往下滑出来，像手机收到的消息一样"→ {@link Edge#TOP}
 *
 * 两者的差别只有"从哪边进出"和"往哪边滑算关闭"；跟手、方向判定、阈值、
 * 回位动画、触摸拦截全都一样。所以做成一个类 + 一个方向参数，**不写两套**。
 *
 * ## 为什么自己写
 * Material 的 `BottomSheetBehavior` 是现成方案，但要引入 `com.google.android.material`
 * （本项目只有 activity/dynamicanimation/annotation，引新库违反铁律），
 * 而且它只支持底部、不支持顶部通知条。项目里已有 `MainActivity.swipePanel()`
 * （下载提示条在用）实现了单方向滑走，本类把它的思路补成双向 + 两侧进出。
 *
 * ## 铁律（v1.19.8 真机 ANR 事故的覆盖结论）
 * **触摸路径禁物理弹簧回位**：
 * - 跟手阶段**直接 `set` 位移，不起任何动画器**（零逐帧重绘开销）；
 * - 松手回位走**有界 VPA**（180ms），不是无界弹簧（弱机上 settle 不可控 → 渲染风暴 → ANR）。
 *
 * ## 动效 token（规范 §2.1）
 * 入场 300ms / 退场 200ms，M3 standard 缓动 `(0.2, 0, 0, 1)`；遮罩 `alpha 0.32`。
 * 自检项"入场时长 ≥ 退场时长"成立。
 */
final class SlideSheet {

  /** 面板从哪一边进出。也决定了"往哪个方向滑算关闭"。 */
  enum Edge { TOP, BOTTOM }

  private static final float SCRIM_ALPHA = 0.32f;
  private static final int ENTER_MS = 300;
  private static final int EXIT_MS = 200;
  private static final int RESET_MS = 180;
  /** 触发关闭的滑动距离。与 `swipePanel` 的 72dp 一致，避免同一 App 两套手感。 */
  private static final int DISMISS_DP = 72;

  private final Context context;
  private final ViewGroup container;
  private final View content;
  private final Runnable onDismissed;
  private final boolean motionEnabled;
  private final Edge edge;

  private final FrameLayout overlay;
  private final View scrim;

  private boolean swipeAway = true;
  private boolean swipeHorizontal = true;
  private boolean dismissOnScrimTap = true;
  private boolean dismissOnBack = true;
  private boolean showing;
  private boolean dismissed;

  private float startX, startY;
  private int axis;              // 0=未定 1=横向 2=纵向
  private boolean dragging;

  private SlideSheet(Context context, ViewGroup container, View content,
                     Runnable onDismissed, boolean motionEnabled, Edge edge) {
    this.context = context;
    this.container = container;
    this.content = content;
    this.onDismissed = onDismissed;
    this.motionEnabled = motionEnabled;
    this.edge = edge == null ? Edge.TOP : edge;

    overlay = new FrameLayout(context);
    overlay.setLayoutParams(new ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    scrim = new View(context);
    scrim.setBackgroundColor(Color.BLACK);
    scrim.setAlpha(0f);
    scrim.setClickable(true);
    overlay.addView(scrim, new FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    int gravity = this.edge == Edge.TOP ? Gravity.TOP : Gravity.BOTTOM;
    FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, gravity);
    overlay.addView(content, panelParams);
    installGesture();
  }

  static SlideSheet create(Context context, ViewGroup container, View content,
                           Runnable onDismissed, boolean motionEnabled, Edge edge) {
    return new SlideSheet(context, container, content, onDismissed, motionEnabled, edge);
  }

  /** 是否允许"往它出来的那一边"滑走关闭。强制更新场景应关掉。 */
  SlideSheet swipeAway(boolean enabled) { this.swipeAway = enabled; return this; }

  SlideSheet swipeHorizontal(boolean enabled) { this.swipeHorizontal = enabled; return this; }

  SlideSheet dismissOnScrimTap(boolean enabled) { this.dismissOnScrimTap = enabled; return this; }

  SlideSheet dismissOnBack(boolean enabled) { this.dismissOnBack = enabled; return this; }

  /** 面板完全在屏幕外时的位移量（正值）。 */
  private float offscreenOffset() {
    int height = content.getHeight();
    return height > 0 ? height : dp(600);
  }

  /** 面板静止时应在的位置。 */
  private float restTranslation() { return 0f; }

  private int dp(int value) {
    return (int) (value * context.getResources().getDisplayMetrics().density + .5f);
  }

  boolean isShowing() { return showing; }

  View panel() { return content; }

  boolean dismissOnBack() { return dismissOnBack; }

  void show() {
    if (showing) return;
    showing = true;
    dismissed = false;
    container.addView(overlay);
    scrim.setOnClickListener(v -> { if (dismissOnScrimTap) dismiss(); });

    content.post(() -> {
      float offset = offscreenOffset();
      if (!motionEnabled) {
        scrim.setAlpha(SCRIM_ALPHA);
        content.setTranslationY(restTranslation());
        return;
      }
      scrim.setAlpha(0f);
      content.setTranslationY(offscreenTranslation(offset));
      scrim.animate().alpha(SCRIM_ALPHA).setDuration(ENTER_MS).setInterpolator(standard()).start();
      content.animate().translationY(restTranslation()).setDuration(ENTER_MS).setInterpolator(standard()).start();
    });
  }

  /** 屏幕外方向：顶部面板在负方向，底部面板在正方向。 */
  private float offscreenTranslation(float offset) {
    return edge == Edge.TOP ? -offset : offset;
  }

  /** M3 standard 缓动 `(0.2, 0, 0, 1)`——规范 §2.1 的默认曲线。 */
  private PathInterpolator standard() {
    return new PathInterpolator(0.2f, 0f, 0f, 1f);
  }

  void dismiss() {
    if (!showing || dismissed) return;
    dismissed = true;
    showing = false;
    if (!motionEnabled) {
      removeNow();
      return;
    }
    scrim.animate().alpha(0f).setDuration(EXIT_MS).setInterpolator(standard()).start();
    content.animate().translationY(offscreenTranslation(offscreenOffset()))
        .setDuration(EXIT_MS).setInterpolator(standard())
        .withEndAction(this::removeNow).start();
  }

  private void removeNow() {
    if (overlay.getParent() == container) container.removeView(overlay);
    content.setTranslationY(restTranslation());
    content.setTranslationX(0f);
    if (onDismissed != null) onDismissed.run();
  }

  /** 跟手期间拦截父容器的触摸抢占；松手必须放回，否则外层列表会永久失去滚动。 */
  private void setIntercept(View view, boolean intercept) {
    ViewParent parent = view.getParent();
    if (parent != null) parent.requestDisallowInterceptTouchEvent(intercept);
  }

  /** 该位移方向是否属于"往出口走"（只有朝出口方向的拖动才跟手）。 */
  private boolean towardExit(float dy) {
    return edge == Edge.TOP ? dy < 0f : dy > 0f;
  }

  /** 松手时是否已达到关闭条件。 */
  private boolean reachedDismissThreshold(float dx, float dy) {
    if (axis == 1 && swipeHorizontal && Math.abs(dx) > dp(DISMISS_DP)) return true;
    if (axis == 2 && swipeAway && towardExit(dy) && Math.abs(dy) > dp(DISMISS_DP)) return true;
    return false;
  }

  /**
   * 跟手拖动 + 松手判定。
   *
   * 方向判定用 5dp 阈值（与 `swipePanel` 一致）：一旦锁定轴向就只在该轴上跟手，
   * 避免"想左右滑结果面板在上下抖"。
   */
  private void installGesture() {
    final int touchSlop = Math.max(dp(5), ViewConfiguration.get(context).getScaledTouchSlop());
    content.setOnTouchListener((view, event) -> {
      switch (event.getActionMasked()) {
        case MotionEvent.ACTION_DOWN:
          startX = event.getRawX();
          startY = event.getRawY();
          axis = 0;
          dragging = false;
          view.animate().cancel();
          setIntercept(view, true);
          return true;
        case MotionEvent.ACTION_MOVE: {
          float dx = event.getRawX() - startX;
          float dy = event.getRawY() - startY;
          if (axis == 0 && Math.max(Math.abs(dx), Math.abs(dy)) > touchSlop) {
            axis = Math.abs(dx) >= Math.abs(dy) ? 1 : 2;
          }
          if (axis == 1 && !swipeHorizontal) return true;
          if (axis == 2 && !swipeAway) return true;
          dragging = true;
          // 跟手阶段**直接 set**（零动画器）。
          // 纵向只允许朝出口方向跟手——反方向拖没有语义，跟了反而像"面板松了"。
          if (axis == 1) {
            view.setTranslationX(dx);
          } else if (towardExit(dy)) {
            view.setTranslationY(dy);
          }
          // 拖动时遮罩跟着淡：让"快要关掉了"有连续的视觉反馈，而不是松手才突变。
          float progress = axis == 1
              ? Math.min(1f, Math.abs(dx) / Math.max(1f, dp(260)))
              : Math.min(1f, Math.abs(dy) / Math.max(1f, dp(260)));
          scrim.setAlpha(SCRIM_ALPHA * (1f - progress * 0.6f));
          return true;
        }
        case MotionEvent.ACTION_UP: {
          setIntercept(view, false);
          float dx = event.getRawX() - startX;
          float dy = event.getRawY() - startY;
          if (reachedDismissThreshold(dx, dy)) {
            dismiss();
            return true;
          }
          if (dragging) {
            resetToRest();
            return true;
          }
          // 没拖动 = 一次点击，交回给内容自己的点击处理。
          return false;
        }
        case MotionEvent.ACTION_CANCEL: {
          setIntercept(view, false);
          if (dragging) resetToRest();
          return true;
        }
        default:
          return false;
      }
    });
  }

  /** 松手回位：**有界 VPA**（180ms），不是弹簧——触摸路径禁弹簧是本项目铁律。 */
  private void resetToRest() {
    dragging = false;
    if (!motionEnabled) {
      content.setTranslationX(0f);
      content.setTranslationY(restTranslation());
      scrim.setAlpha(SCRIM_ALPHA);
      return;
    }
    content.animate().translationX(0f).translationY(restTranslation())
        .setDuration(RESET_MS).setInterpolator(standard()).start();
    scrim.animate().alpha(SCRIM_ALPHA).setDuration(RESET_MS).start();
  }

  /** 返回键处理。返回 true 表示"已消费"，调用方据此不再往下传。 */
  boolean handleBack() {
    if (!showing) return false;
    if (!dismissOnBack) return true;   // 消费掉但不关闭
    dismiss();
    return true;
  }
}
