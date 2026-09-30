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
 * [DFWX] DFW-65：**顶部面板（Top Sheet）**——从屏幕上方滑入、可上滑/左右滑关闭。
 *
 * ## 为什么自己写
 * 用户要求"弹窗从软件上面往下滑出来，然后再往上滑一下、或者往左右滑，可以丝滑地滑出去"。
 * - Material 的 `BottomSheetBehavior` 是现成方案，但**只能从底部弹、只支持垂直拖拽**，
 *   而且要把 `com.google.android.material` 引进来（本项目只有 activity/dynamicanimation/annotation，
 *   引新库违反铁律）。方向不对，用不上。
 * - 项目里已有 `MainActivity.swipePanel()`（下载提示条在用），已经实现了跟手拖动 + 方向判定 +
 *   阈值关闭 + 拖动淡出。本类把它的思路**从单方向扩成三方向**（上/左/右），
 *   并补上"从上方滑入"的入场。
 *
 * ## 铁律（v1.19.8 真机 ANR 事故的覆盖结论）
 * **触摸路径禁物理弹簧回位**：
 * - 跟手阶段**直接 `setTranslationX/Y`，不起任何动画器**（零逐帧重绘开销）；
 * - 松手回位走**有界 VPA**（180ms），不是无界弹簧（弱机上无界弹簧 settle 不可控 → 渲染风暴 → ANR）。
 *
 * ## 动效 token（规范 §2.1）
 * 入场 300ms / 退场 200ms，M3 standard 缓动 `(0.2, 0, 0, 1)`；遮罩 `alpha 0.32`。
 * 自检项"入场时长 ≥ 退场时长"在此成立。
 */
final class TopSheet {

  /** 遮罩浓度：规范 §2.1 定为 0.32（配真黑底，再深会与页面糊成一片）。 */
  private static final float SCRIM_ALPHA = 0.32f;
  private static final int ENTER_MS = 300;
  private static final int EXIT_MS = 200;
  private static final int RESET_MS = 180;
  /** 触发关闭的滑动距离。与 `swipePanel` 的 72dp 保持一致，避免同一 App 两套手感。 */
  private static final int DISMISS_DP = 72;

  private final Context context;
  private final ViewGroup container;
  private final View content;
  private final Runnable onDismissed;
  private final boolean motionEnabled;

  private final FrameLayout overlay;
  private final View scrim;

  private boolean swipeUp = true;
  private boolean swipeHorizontal = true;
  private boolean dismissOnScrimTap = true;
  private boolean dismissOnBack = true;
  private boolean showing;
  private boolean dismissed;

  private float startX, startY;
  private int axis;              // 0=未定 1=横向 2=纵向
  private boolean dragging;

  private TopSheet(Context context, ViewGroup container, View content, Runnable onDismissed, boolean motionEnabled) {
    this.context = context;
    this.container = container;
    this.content = content;
    this.onDismissed = onDismissed;
    this.motionEnabled = motionEnabled;

    overlay = new FrameLayout(context);
    overlay.setLayoutParams(new ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    scrim = new View(context);
    scrim.setBackgroundColor(Color.BLACK);
    scrim.setAlpha(0f);
    scrim.setClickable(true);
    overlay.addView(scrim, new FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP);
    overlay.addView(content, panelParams);
    installGesture();
  }

  static TopSheet create(Context context, ViewGroup container, View content, Runnable onDismissed, boolean motionEnabled) {
    return new TopSheet(context, container, content, onDismissed, motionEnabled);
  }

  /** 允许的关闭方向。强制更新场景应把三个都关掉——"不能关"的语义必须是硬的。 */
  TopSheet swipeUp(boolean enabled) { this.swipeUp = enabled; return this; }

  TopSheet swipeHorizontal(boolean enabled) { this.swipeHorizontal = enabled; return this; }

  /** 点遮罩是否关闭。强制更新必须设为 false。 */
  TopSheet dismissOnScrimTap(boolean enabled) { this.dismissOnScrimTap = enabled; return this; }

  /** 返回键是否可关闭。强制更新必须设为 false。 */
  TopSheet dismissOnBack(boolean enabled) { this.dismissOnBack = enabled; return this; }

  boolean dismissOnBack() { return dismissOnBack; }

  boolean isShowing() { return showing; }

  View panel() { return content; }

  private int dp(int value) {
    return (int) (value * context.getResources().getDisplayMetrics().density + .5f);
  }

  void show() {
    if (showing) return;
    showing = true;
    dismissed = false;
    container.addView(overlay);
    scrim.setOnClickListener(v -> { if (dismissOnScrimTap) dismiss(); });

    // 从屏幕上方滑入：初始位移 = 面板高度（未知时给一个足够大的兜底，保证一开始完全在屏幕外）。
    content.post(() -> {
      float offset = content.getHeight() > 0 ? content.getHeight() : dp(600);
      if (!motionEnabled) {
        scrim.setAlpha(SCRIM_ALPHA);
        content.setTranslationY(0f);
        return;
      }
      scrim.setAlpha(0f);
      content.setTranslationY(-offset);
      scrim.animate().alpha(SCRIM_ALPHA).setDuration(ENTER_MS).setInterpolator(standard()).start();
      content.animate().translationY(0f).setDuration(ENTER_MS).setInterpolator(standard()).start();
    });
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
    float offset = content.getHeight() > 0 ? content.getHeight() : dp(600);
    scrim.animate().alpha(0f).setDuration(EXIT_MS).setInterpolator(standard()).start();
    content.animate().translationY(-offset).setDuration(EXIT_MS).setInterpolator(standard())
        .withEndAction(this::removeNow).start();
  }

  private void removeNow() {
    if (overlay.getParent() == container) container.removeView(overlay);
    content.setTranslationY(0f);
    content.setTranslationX(0f);
    if (onDismissed != null) onDismissed.run();
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
          // 跟手期间不让父容器（外层滚动）抢走触摸，否则滑到一半会被截断。
          setIntercept(view, true);
          return true;
        case MotionEvent.ACTION_MOVE: {
          float dx = event.getRawX() - startX;
          float dy = event.getRawY() - startY;
          if (axis == 0 && Math.max(Math.abs(dx), Math.abs(dy)) > touchSlop) {
            axis = Math.abs(dx) >= Math.abs(dy) ? 1 : 2;
          }
          if (axis == 1 && !swipeHorizontal) return true;
          if (axis == 2 && !swipeUp) return true;
          dragging = true;
          // 跟手阶段**直接 set**（零动画器）。向下拖不做跟手——面板已在顶部，往下拽没有语义。
          if (axis == 1) {
            view.setTranslationX(dx);
          } else if (dy < 0f) {
            view.setTranslationY(dy);
          }
          // 拖动时遮罩跟着淡：让"快要关掉了"这件事有连续的视觉反馈，而不是松手才突变。
          float progress = axis == 1
              ? Math.min(1f, Math.abs(dx) / Math.max(1f, dp(260)))
              : Math.min(1f, Math.max(0f, -dy) / Math.max(1f, dp(260)));
          scrim.setAlpha(SCRIM_ALPHA * (1f - progress * 0.6f));
          return true;
        }
        case MotionEvent.ACTION_UP: {
          setIntercept(view, false);
          float dx = event.getRawX() - startX;
          float dy = event.getRawY() - startY;
          boolean hit = (axis == 1 && swipeHorizontal && Math.abs(dx) > dp(DISMISS_DP))
              || (axis == 2 && swipeUp && -dy > dp(DISMISS_DP));
          if (hit) {
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

  /** 跟手期间拦截父容器的触摸抢占；松手必须放回，否则外层列表会永久失去滚动。 */
  private void setIntercept(View view, boolean intercept) {
    ViewParent parent = view.getParent();
    if (parent != null) parent.requestDisallowInterceptTouchEvent(intercept);
  }

  /** 松手回位：**有界 VPA**（180ms），不是弹簧——触摸路径禁弹簧是本项目铁律。 */
  private void resetToRest() {
    dragging = false;
    if (!motionEnabled) {
      content.setTranslationX(0f);
      content.setTranslationY(0f);
      scrim.setAlpha(SCRIM_ALPHA);
      return;
    }
    content.animate().translationX(0f).translationY(0f).setDuration(RESET_MS).setInterpolator(standard()).start();
    scrim.animate().alpha(SCRIM_ALPHA).setDuration(RESET_MS).start();
  }

  /** 遮罩是否吃掉了这次返回。强制更新时返回 false，调用方据此决定要不要放行。 */
  boolean handleBack() {
    if (!showing) return false;
    if (!dismissOnBack) return true;   // 消费掉但不关闭
    dismiss();
    return true;
  }
}
