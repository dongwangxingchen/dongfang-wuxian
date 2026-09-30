package cc.nkbr.lanzouplus;

import android.content.Context;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;

/**
 * [DFWX] DFW-66：**顶部通知条**——像手机收到消息那样从屏幕顶部滑下来。
 *
 * ## 用户要求（2026-09-30 原话）
 * > "b 从顶部往下滑出来，像我们手机收到的消息一样。不过适配的好，如果文字多了也得适配好，
 * >  少了也得适配好。2~3 秒消失，我也可以直接滑动，把它往上滑、往左滑、往右滑给滑掉。"
 *
 * 逐条对应：
 * - **从顶部滑下来**：入场 `translationY: -高度 → 0`，300ms standard 缓动。
 * - **像消息通知**：悬浮圆角卡片 + 图标 + 正文，**没有遮罩**——通知不是模态的，
 *   盖一层灰会让整个界面变暗，那是对话框的做法。
 * - **文字多寡都要适配**：容器 `WRAP_CONTENT`，正文最多 4 行、超出省略号；
 *   一行时是紧凑胶囊，多行时自动长高，内边距不随行数变（否则会看着一跳一跳）。
 * - **2~3 秒消失**：短提示 2500ms、重要提示 5000ms（沿用 `showNotice` 原有语义）。
 * - **可滑动关闭**：向上 / 向左 / 向右滑超过阈值即滑掉；**向下不关**——它是从上面下来的，
 *   往下拽没有"关闭"语义。
 *
 * ## 铁律
 * 触摸路径禁物理弹簧回位：跟手阶段**直接 set**，松手回位走**有界 VPA**（180ms）。
 * 自动消失也走有界 VPA，不用弹簧。
 *
 * ## 与 SlideSheet 的区别
 * `SlideSheet` 是**模态面板**（带 0.32 遮罩、会吃掉空白处点击）；
 * 本类是**非模态提示**（无遮罩、空白处点击穿透到下面的界面）。两者不能合并。
 */
final class NoticeBanner {

  private static final int ENTER_MS = 300;
  private static final int EXIT_MS = 200;
  private static final int RESET_MS = 180;
  private static final int DISMISS_DP = 72;
  /** 正文最多几行。再多就不该用通知条了——应该去用面板。 */
  private static final int MAX_LINES = 4;

  private final Context context;
  private final ViewGroup container;
  private final View banner;
  private final Runnable onDismissed;
  private final boolean motionEnabled;
  private final int topMargin;

  private final FrameLayout overlay;
  private final Runnable autoDismiss;

  private float startX, startY;
  private int axis;
  private boolean dragging;
  private boolean showing;
  private boolean dismissed;

  private NoticeBanner(Context context, ViewGroup container, View banner, Runnable onDismissed,
                       boolean motionEnabled, int topMargin, long autoDismissMs) {
    this.context = context;
    this.container = container;
    this.banner = banner;
    this.onDismissed = onDismissed;
    this.motionEnabled = motionEnabled;
    this.topMargin = topMargin;
    this.autoDismiss = () -> { if (showing) dismiss(); };
    this.autoDismissMs = autoDismissMs;

    // 无遮罩：通知不是模态的。空白处**不拦截**触摸，直接穿透到下面的界面。
    overlay = new FrameLayout(context);
    overlay.setLayoutParams(new ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    overlay.setClipChildren(false);
    FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP);
    params.topMargin = topMargin;
    overlay.addView(banner, params);
    installGesture();
  }

  private final long autoDismissMs;

  static NoticeBanner create(Context context, ViewGroup container, View banner, Runnable onDismissed,
                             boolean motionEnabled, int topMargin, long autoDismissMs) {
    return new NoticeBanner(context, container, banner, onDismissed, motionEnabled, topMargin, autoDismissMs);
  }

  /** 正文最多 4 行，超出省略。供构建 banner 的调用方复用，避免两处写死不同的值。 */
  static int maxLines() { return MAX_LINES; }

  private int dp(int value) {
    return (int) (value * context.getResources().getDisplayMetrics().density + .5f);
  }

  private PathInterpolator standard() { return new PathInterpolator(0.2f, 0f, 0f, 1f); }

  boolean isShowing() { return showing; }

  View view() { return banner; }

  void show() {
    if (showing) return;
    showing = true;
    dismissed = false;
    container.addView(overlay);

    banner.post(() -> {
      float offset = banner.getHeight() > 0 ? banner.getHeight() + topMargin : dp(600);
      if (!motionEnabled) {
        banner.setTranslationY(0f);
        banner.setAlpha(1f);
      } else {
        banner.setTranslationY(-offset);
        banner.setAlpha(0f);
        banner.animate().translationY(0f).alpha(1f)
            .setDuration(ENTER_MS).setInterpolator(standard()).start();
      }
      if (autoDismissMs > 0) banner.postDelayed(autoDismiss, autoDismissMs);
    });
  }

  void dismiss() {
    if (!showing || dismissed) return;
    dismissed = true;
    showing = false;
    banner.removeCallbacks(autoDismiss);
    if (!motionEnabled) {
      removeNow();
      return;
    }
    float offset = banner.getHeight() > 0 ? banner.getHeight() + topMargin : dp(600);
    banner.animate().translationY(-offset).alpha(0f)
        .setDuration(EXIT_MS).setInterpolator(standard())
        .withEndAction(this::removeNow).start();
  }

  private void removeNow() {
    if (overlay.getParent() == container) container.removeView(overlay);
    banner.setTranslationY(0f);
    banner.setTranslationX(0f);
    banner.setAlpha(1f);
    if (onDismissed != null) onDismissed.run();
  }

  private void setIntercept(View view, boolean intercept) {
    ViewParent parent = view.getParent();
    if (parent != null) parent.requestDisallowInterceptTouchEvent(intercept);
  }

  /** 跟手拖动 + 松手判定。方向判定 5dp，与项目其它手势一致。 */
  private void installGesture() {
    final int touchSlop = Math.max(dp(5), ViewConfiguration.get(context).getScaledTouchSlop());
    banner.setOnTouchListener((view, event) -> {
      switch (event.getActionMasked()) {
        case MotionEvent.ACTION_DOWN:
          startX = event.getRawX();
          startY = event.getRawY();
          axis = 0;
          dragging = false;
          view.animate().cancel();
          // 用户开始操作时取消自动消失：正在看的时候被收走很烦。
          banner.removeCallbacks(autoDismiss);
          setIntercept(view, true);
          return true;
        case MotionEvent.ACTION_MOVE: {
          float dx = event.getRawX() - startX;
          float dy = event.getRawY() - startY;
          if (axis == 0 && Math.max(Math.abs(dx), Math.abs(dy)) > touchSlop) {
            axis = Math.abs(dx) >= Math.abs(dy) ? 1 : 2;
          }
          dragging = true;
          if (axis == 1) {
            view.setTranslationX(dx);
          } else if (dy < 0f) {
            // 只跟"往上滑"——它是从上面下来的，往下拽没有关闭语义。
            view.setTranslationY(dy);
          }
          float progress = axis == 1
              ? Math.min(1f, Math.abs(dx) / Math.max(1f, dp(260)))
              : Math.min(1f, Math.max(0f, -dy) / Math.max(1f, dp(260)));
          view.setAlpha(1f - progress * 0.6f);
          return true;
        }
        case MotionEvent.ACTION_UP: {
          setIntercept(view, false);
          float dx = event.getRawX() - startX;
          float dy = event.getRawY() - startY;
          boolean hit = (axis == 1 && Math.abs(dx) > dp(DISMISS_DP))
              || (axis == 2 && -dy > dp(DISMISS_DP));
          if (hit) {
            dismiss();
            return true;
          }
          if (dragging) resetToRest();
          // **无论有没有拖动，松手都要重新计时**。
          // 只在小拖动分支里重计时的话，用户轻点一下（未滑动）就会把自动消失"点没了"——
          // 通知永久留在屏幕上，且没有任何办法关掉（本组件测试抓到的真 bug）。
          if (autoDismissMs > 0) banner.postDelayed(autoDismiss, autoDismissMs);
          return dragging;
        }
        case MotionEvent.ACTION_CANCEL: {
          setIntercept(view, false);
          if (dragging) resetToRest();
          if (autoDismissMs > 0) banner.postDelayed(autoDismiss, autoDismissMs);
          return true;
        }
        default:
          return false;
      }
    });
  }

  private void resetToRest() {
    dragging = false;
    if (!motionEnabled) {
      banner.setTranslationX(0f);
      banner.setTranslationY(0f);
      banner.setAlpha(1f);
      return;
    }
    banner.animate().translationX(0f).translationY(0f).alpha(1f)
        .setDuration(RESET_MS).setInterpolator(standard()).start();
  }
}
