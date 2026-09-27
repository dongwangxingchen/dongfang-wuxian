package cc.nkbr.lanzouplus;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * v1.22.0 悬浮导航球：替代底部导航栏，常驻 host 层（跨越全部页面含 AI 页），零权限（Activity 内普通 View）。
 *
 * 铁律（v1.19.7 真机卡死事故教训，见 AGENTS.md）：全部动效走 VPA（ViewPropertyAnimator）+ 有界
 * PathInterpolator，禁止 dynamicanimation（弹簧 settle 无上界→渲染风暴→ANR）；ACTION_DOWN 必须
 * cancel 旧动画；只动 translation/scale/alpha/rotation；触控循环零分配。
 *
 * v1.22.1 菜单改版（真机实测后修正）：原「弧形展开」在手机竖屏右下角几何上不成立——360dp 宽屏
 * 塞不下 5 个 60dp 项，实测项互相重叠且最外侧项被屏幕裁切。改为**胶囊菜单列**（图标+文字同排的
 * 横向胶囊，竖直堆叠），永不重叠、任意球位都放得下，观感更简约高级；动效改为克制的滑入+淡入
 * （无过冲、40ms 交错），符合用户"简约动效"要求。
 */
final class NavBall {
  interface Host {
    int dp(int v);
    Context context();
    int SURFACE2(); int PRIMARY(); int PRIMARY_HI(); int PRIMARY_LO(); int TEXT(); int MUTED(); int BORDER();
    boolean motionEnabled();
    void goToDestination(int destination);
    int currentDestination();
    int contentWidth(); int contentHeight();
    boolean isAiPage();
  }

  // 球
  private static final int BALL_DP = 56, BALL_ICON_DP = 25;
  private static final int EDGE_DP = 14, EDGE_TOP_DP = 14, EDGE_BOTTOM_DP = 36;
  // 胶囊菜单项
  private static final int PILL_H_DP = 46, PILL_W_DP = 124, PILL_ICON_DP = 22, PILL_GAP_DP = 8, PILL_STACK_GAP_DP = 9;
  private static final int PILL_PAD_DP = 13;
  // 动效（简约：无过冲、有界）
  private static final long OPEN_MS = 260, CLOSE_MS = 170, STAGGER_MS = 40, CLOSE_STAGGER_MS = 16, SNAP_MS = 400, ICON_MS = 200;
  private static final int SCRIM_ALPHA = 76; // ~30%
  private static final float AI_SAFE_RATIO = 0.78f;
  private static final float PRESS_SCALE = 0.94f, DRAG_SCALE = 1.06f, PILL_PRESS_SCALE = 0.97f;
  // M3E 官方换算曲线（wear-ui-system.md §2）
  private static final PathInterpolator SPATIAL = new PathInterpolator(0.38f, 1.21f, 0.22f, 1f);
  private static final PathInterpolator EFFECT = new PathInterpolator(0.34f, 0.80f, 0.34f, 1f);
  private static final PathInterpolator PRESS_UP = new PathInterpolator(0.2f, 0.9f, 0.3f, 1.05f);

  /** 菜单项（自上而下顺序，与旧底栏一致） */
  private static final int[] ITEM_DEST = {0, 4, 2, 5, 3};
  private static final int[] ITEM_ICON = {R.drawable.ic_home, R.drawable.ic_ai, R.drawable.ic_download, R.drawable.ic_tools, R.drawable.ic_settings};
  private static final String[] ITEM_LABEL = {"软件库", "AI 对话", "下载", "工具箱", "设置"};

  private final Host host;
  private final View scrim;
  private final FrameLayout ball;
  private final ImageView gridIcon, closeIcon;
  private final GradientDrawable ballBg;
  private final Pill[] pills = new Pill[ITEM_DEST.length];
  private final int touchSlop;
  private final Handler main = new Handler(Looper.getMainLooper());

  private boolean attached, menuOpen, dragging;
  private boolean menuClosing; // 收起动画进行中（此间胶囊的触摸只被吞掉，不打断淡出）
  private Runnable hideGuard;  // 收起动画到点强制隐藏的兜底（防任何原因漏掉 withEndAction）
  private float downRawX, downRawY, startTx, startTy;
  private float userCx, userCy;      // 用户位置（球心，内容区 px，持久化）
  private float currentCx, currentCy; // 当前显示位置（球心，含 AI 页避让）

  private final class Pill {
    final int destination;
    final LinearLayout view;
    final ImageView icon;
    final TextView label;
    final GradientDrawable bg;
    float targetX, targetY; // 展开后的落位（按下时吸附用）
    boolean swallowed;      // 本次手势已被吞掉（收起期间按下），后续事件一并吃掉
    Pill(int destination, String text, int iconRes) {
      this.destination = destination;
      Context ctx = host.context();
      bg = new GradientDrawable();
      bg.setCornerRadius(host.dp(PILL_H_DP) / 2f);
      view = new LinearLayout(ctx);
      view.setOrientation(LinearLayout.HORIZONTAL);
      view.setGravity(Gravity.CENTER_VERTICAL);
      view.setPadding(host.dp(PILL_PAD_DP), 0, host.dp(PILL_PAD_DP), 0);
      view.setClickable(true);
      view.setFocusable(true);
      view.setContentDescription(text);
      view.setBackground(bg);
      view.setElevation(host.dp(12)); // 高于球（10dp），展开时不被球压住
      view.setVisibility(View.GONE);
      icon = new ImageView(ctx);
      icon.setImageResource(iconRes);
      view.addView(icon, new LinearLayout.LayoutParams(host.dp(PILL_ICON_DP), host.dp(PILL_ICON_DP)));
      label = new TextView(ctx);
      label.setText(text);
      label.setTextSize(13);
      label.setSingleLine(true);
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
      lp.leftMargin = host.dp(9);
      view.addView(label, lp);
      view.setOnClickListener(v -> {
        v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        closeMenu();
        host.goToDestination(destination);
      });
      pressFeedback(this);
    }
  }

  NavBall(Host host) {
    this.host = host;
    Context ctx = host.context();
    touchSlop = ViewConfiguration.get(ctx).getScaledTouchSlop();

    scrim = new View(ctx);
    scrim.setBackgroundColor(SCRIM_ALPHA << 24);
    scrim.setVisibility(View.GONE);
    scrim.setClickable(true);
    scrim.setOnClickListener(v -> closeMenu());

    ball = new FrameLayout(ctx);
    ball.setClickable(true);
    ball.setFocusable(true);
    ball.setSoundEffectsEnabled(false);
    ball.setContentDescription("导航菜单");
    ball.setElevation(host.dp(10));
    ballBg = new GradientDrawable();
    ballBg.setShape(GradientDrawable.OVAL);
    ball.setBackground(ballBg);
    gridIcon = new ImageView(ctx);
    gridIcon.setImageResource(R.drawable.ic_nav_grid);
    closeIcon = new ImageView(ctx);
    closeIcon.setImageResource(R.drawable.ic_close);
    closeIcon.setAlpha(0f);
    closeIcon.setRotation(-45f);
    int iconPx = host.dp(BALL_ICON_DP);
    ball.addView(gridIcon, new FrameLayout.LayoutParams(iconPx, iconPx, Gravity.CENTER));
    ball.addView(closeIcon, new FrameLayout.LayoutParams(iconPx, iconPx, Gravity.CENTER));
    ball.setOnTouchListener(this::onBallTouch);

    for (int i = 0; i < pills.length; i++) pills[i] = new Pill(ITEM_DEST[i], ITEM_LABEL[i], ITEM_ICON[i]);
    loadPosition();
    refreshColors();
  }

  // ── 挂载 ─────────────────────────────────────────────────────────────

  void attach(FrameLayout hostView) {
    if (attached) return;
    attached = true;
    hostView.addView(scrim, new FrameLayout.LayoutParams(-1, -1));
    hostView.addView(ball, new FrameLayout.LayoutParams(host.dp(BALL_DP), host.dp(BALL_DP)));
    for (Pill p : pills) {
      hostView.addView(p.view, new FrameLayout.LayoutParams(host.dp(PILL_W_DP), host.dp(PILL_H_DP)));
    }
    if (userCx <= 0f || userCy <= 0f) defaultPosition();
    applyPosition(false);
  }

  /** 内容区尺寸变化（insets/旋转/宽屏切换）后重新收敛位置；展开中直接收起（菜单几何已失效） */
  void onHostLayout() {
    if (!attached) return;
    if (menuOpen) closeMenu();
    else if (!menuClosing) forceHidePills();
    applyPosition(false);
  }

  /** 页面切换：刷新高亮 + AI 页避让 */
  void onDestinationChanged() {
    if (!attached) return;
    if (menuOpen) closeMenu();
    else if (!menuClosing) forceHidePills();
    refreshPillColors();
    applyPosition(true);
  }

  /** 立即清掉所有胶囊（不发动画）。用于页面切换/布局变化时清理任何残留，避免「胶囊卡在屏幕上」。 */
  private void forceHidePills() {
    for (Pill p : pills) {
      p.view.animate().cancel();
      p.view.setVisibility(View.GONE);
      p.view.setAlpha(1f);
      p.view.setScaleX(1f);
      p.view.setScaleY(1f);
    }
  }

  void refreshColors() {
    ballBg.setColor(host.SURFACE2());
    ballBg.setStroke(Math.max(1, host.dp(1)), host.BORDER());
    gridIcon.setColorFilter(host.TEXT());
    closeIcon.setColorFilter(host.TEXT());
    scrim.setBackgroundColor(SCRIM_ALPHA << 24);
    refreshPillColors();
  }

  private void refreshPillColors() {
    int current = host.currentDestination();
    for (Pill p : pills) {
      boolean active = p.destination == current;
      // 选中态配色（真机采样修正）：PRIMARY #A78BFA 是浅紫，做底色会和亮字糊在一起（实测底#C191FC/字#C494FF 几乎同色）。
      // 改为「深紫实底 + 亮紫字 + 亮紫描边」——底色压到 24% alpha 且叠在 SURFACE2 上再压暗，保证字面清晰。
      p.bg.setColor(active ? blend(host.SURFACE2(), host.PRIMARY(), 0.22f) : host.SURFACE2());
      p.bg.setStroke(Math.max(1, host.dp(1)), active ? host.PRIMARY_HI() : host.BORDER());
      p.icon.setColorFilter(active ? host.PRIMARY_HI() : host.MUTED());
      p.label.setTextColor(active ? host.PRIMARY_HI() : host.TEXT());
    }
  }

  /** 把 tint 色按 ratio 混到 base 上（结果不透明，避免与背景再合成后变亮） */
  private static int blend(int base,int tint,float ratio) {
    int r=(int)(android.graphics.Color.red(base)+(android.graphics.Color.red(tint)-android.graphics.Color.red(base))*ratio);
    int g=(int)(android.graphics.Color.green(base)+(android.graphics.Color.green(tint)-android.graphics.Color.green(base))*ratio);
    int b=(int)(android.graphics.Color.blue(base)+(android.graphics.Color.blue(tint)-android.graphics.Color.blue(base))*ratio);
    return android.graphics.Color.rgb(r,g,b);
  }

  // ── 位置 ─────────────────────────────────────────────────────────────

  private void defaultPosition() {
    int cw = host.contentWidth(), ch = host.contentHeight();
    float r = host.dp(BALL_DP) / 2f;
    userCx = Math.max(r, cw - host.dp(EDGE_DP) - r);
    userCy = Math.max(r, ch - host.dp(EDGE_BOTTOM_DP) - r);
  }

  private void applyPosition(boolean animate) {
    int cw = host.contentWidth(), ch = host.contentHeight();
    if (cw <= 0 || ch <= 0) return;
    float r = host.dp(BALL_DP) / 2f;
    float cx = clamp(userCx, host.dp(EDGE_DP) + r, cw - host.dp(EDGE_DP) - r);
    float cy = clamp(userCy, host.dp(EDGE_TOP_DP) + r, ch - host.dp(EDGE_BOTTOM_DP) - r);
    if (host.isAiPage()) cy = Math.min(cy, ch * AI_SAFE_RATIO - r);
    currentCx = cx;
    currentCy = cy;
    float tx = cx - r, ty = cy - r;
    if (animate && host.motionEnabled()) {
      ball.animate().translationX(tx).translationY(ty).setDuration(SNAP_MS).setInterpolator(SPATIAL).start();
    } else {
      ball.animate().cancel();
      ball.setTranslationX(tx);
      ball.setTranslationY(ty);
    }
  }

  private void loadPosition() {
    SharedPreferences p = host.context().getSharedPreferences("nav_ball", Context.MODE_PRIVATE);
    userCx = p.getFloat("cx", -1f);
    userCy = p.getFloat("cy", -1f);
  }

  private void persistPosition() {
    host.context().getSharedPreferences("nav_ball", Context.MODE_PRIVATE).edit().putFloat("cx", userCx).putFloat("cy", userCy).apply();
  }

  /** 松手贴最近边（左右优先，保持纵向位置） */
  private void snapToEdge() {
    int cw = host.contentWidth(), ch = host.contentHeight();
    float r = host.dp(BALL_DP) / 2f;
    float cx = clamp(currentCx, host.dp(EDGE_DP) + r, cw - host.dp(EDGE_DP) - r);
    float cy = clamp(currentCy, host.dp(EDGE_TOP_DP) + r, ch - host.dp(EDGE_BOTTOM_DP) - r);
    userCx = cx < cw / 2f ? host.dp(EDGE_DP) + r : cw - host.dp(EDGE_DP) - r;
    userCy = cy;
    persistPosition();
    applyPosition(true);
  }

  // ── 触摸 ─────────────────────────────────────────────────────────────

  private boolean onBallTouch(View v, MotionEvent ev) {
    switch (ev.getActionMasked()) {
      case MotionEvent.ACTION_DOWN:
        ball.animate().cancel(); // 铁律③：DOWN 必须 cancel 旧动画
        downRawX = ev.getRawX();
        downRawY = ev.getRawY();
        startTx = ball.getTranslationX();
        startTy = ball.getTranslationY();
        dragging = false;
        ball.setScaleX(PRESS_SCALE);
        ball.setScaleY(PRESS_SCALE);
        return true;
      case MotionEvent.ACTION_MOVE: {
        if (!dragging) {
          float dx = ev.getRawX() - downRawX, dy = ev.getRawY() - downRawY;
          if (dx * dx + dy * dy > touchSlop * touchSlop) {
            dragging = true;
            if (menuOpen) closeMenu();
            ball.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            ball.animate().scaleX(DRAG_SCALE).scaleY(DRAG_SCALE).setDuration(120).setInterpolator(EFFECT).start();
          } else return true;
        }
        int cw = host.contentWidth(), ch = host.contentHeight();
        float r = host.dp(BALL_DP) / 2f;
        float cx = clamp(startTx + r + (ev.getRawX() - downRawX), host.dp(EDGE_DP) + r, cw - host.dp(EDGE_DP) - r);
        float cy = clamp(startTy + r + (ev.getRawY() - downRawY), host.dp(EDGE_TOP_DP) + r, ch - host.dp(EDGE_BOTTOM_DP) - r);
        currentCx = cx;
        currentCy = cy;
        ball.setTranslationX(cx - r);
        ball.setTranslationY(cy - r);
        return true;
      }
      case MotionEvent.ACTION_UP:
        ball.animate().cancel();
        if (dragging) {
          ball.animate().scaleX(1f).scaleY(1f).setDuration(200).setInterpolator(PRESS_UP)
              .withEndAction(() -> ball.setLayerType(View.LAYER_TYPE_NONE, null)).start();
          snapToEdge();
          ball.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        } else {
          ball.animate().scaleX(1f).scaleY(1f).setDuration(220).setInterpolator(PRESS_UP).start();
          ball.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
          if (menuOpen) closeMenu(); else openMenu();
        }
        dragging = false;
        return true;
      case MotionEvent.ACTION_CANCEL:
        ball.animate().cancel();
        ball.animate().scaleX(1f).scaleY(1f).setDuration(200).setInterpolator(PRESS_UP).start();
        if (dragging) snapToEdge();
        dragging = false;
        return true;
      default:
        return false;
    }
  }

  // ── 胶囊菜单列 ───────────────────────────────────────────────────────

  private void openMenu() {
    if (menuOpen) return;
    menuOpen = true;
    menuClosing = false;
    if (hideGuard != null) { ball.removeCallbacks(hideGuard); hideGuard = null; }
    refreshPillColors();
    boolean motion = host.motionEnabled();

    scrim.setVisibility(View.VISIBLE);
    scrim.setAlpha(0f);
    if (motion) scrim.animate().alpha(1f).setDuration(OPEN_MS).setInterpolator(EFFECT).start();
    else scrim.setAlpha(1f);
    animateBallIcon(true);

    int cw = host.contentWidth(), ch = host.contentHeight();
    int pillW = host.dp(PILL_W_DP), pillH = host.dp(PILL_H_DP);
    float r = host.dp(BALL_DP) / 2f;
    float stackGap = host.dp(PILL_STACK_GAP_DP);
    // 竖直方向：球在下半屏→向上生长；球在上半屏→向下生长（始终留在屏内）
    float stackH = pills.length * pillH + (pills.length - 1) * stackGap;
    boolean growUp = currentCy > stackH + host.dp(EDGE_TOP_DP) + r;
    // 水平方向：贴球所在的半屏（右半屏→胶囊右对齐球右缘；左半屏→左对齐）
    boolean alignRight = currentCx >= cw / 2f;
    float ballTop = currentCy - r, ballBottom = currentCy + r;
    float ballLeft = currentCx - r, ballRight = currentCx + r;

    for (int i = 0; i < pills.length; i++) {
      Pill p = pills[i];
      float left = alignRight ? Math.max(host.dp(EDGE_DP), ballRight - pillW) : Math.min(cw - host.dp(EDGE_DP) - pillW, ballLeft);
      float top = growUp
          ? ballTop - stackGap - (pills.length - i) * pillH - (pills.length - 1 - i) * stackGap
          : ballBottom + stackGap + i * (pillH + stackGap);
      float from = alignRight ? host.dp(18) : -host.dp(18);
      p.targetX = left;
      p.targetY = top;
      p.view.animate().cancel();
      p.view.setVisibility(View.VISIBLE);
      p.view.setTranslationX(left + from);
      p.view.setTranslationY(top);
      p.view.setScaleX(0.94f);
      p.view.setScaleY(0.94f);
      p.view.setAlpha(0f);
      // 从贴近球的一端先出（growUp 时最下面那颗=设置先出）
      long delay = growUp ? (pills.length - 1 - i) * STAGGER_MS : i * STAGGER_MS;
      if (motion) {
        p.view.animate().translationX(left).alpha(1f).scaleX(1f).scaleY(1f)
            .setStartDelay(delay).setDuration(OPEN_MS).setInterpolator(EFFECT).start();
      } else {
        p.view.setTranslationX(left);
        p.view.setAlpha(1f);
        p.view.setScaleX(1f);
        p.view.setScaleY(1f);
      }
    }
  }

  private void closeMenu() {
    if (!menuOpen) return;
    menuOpen = false;
    menuClosing = true;
    animateBallIcon(false);
    boolean motion = host.motionEnabled();
    if (motion) {
      scrim.animate().alpha(0f).setDuration(CLOSE_MS).setInterpolator(EFFECT)
          .withEndAction(() -> { if (!menuOpen) scrim.setVisibility(View.GONE); }).start();
    } else {
      scrim.setVisibility(View.GONE);
    }
    int cw = host.contentWidth();
    boolean alignRight = currentCx >= cw / 2f;
    float to = alignRight ? host.dp(18) : -host.dp(18);
    for (int i = 0; i < pills.length; i++) {
      Pill p = pills[i];
      p.view.animate().cancel();
      if (motion) {
        p.view.animate().translationX(p.view.getTranslationX() + to).alpha(0f).scaleX(0.94f).scaleY(0.94f)
            .setStartDelay(i * CLOSE_STAGGER_MS).setDuration(CLOSE_MS).setInterpolator(EFFECT)
            .withEndAction(() -> { if (!menuOpen) p.view.setVisibility(View.GONE); }).start();
      } else {
        p.view.setVisibility(View.GONE);
      }
    }
    // 兜底：任何原因导致 withEndAction 没跑（动画被打断/被系统回收），到点也强制隐藏——
    // 修「收起瞬间误触胶囊 → 胶囊永久留在屏幕上」的 bug（v1.22.2 真机实测）
    if (hideGuard != null) main.removeCallbacks(hideGuard);
    hideGuard = () -> {
      hideGuard = null;
      menuClosing = false;
      if (menuOpen) return;
      forceHidePills();
    };
    main.postDelayed(hideGuard, (pills.length - 1) * CLOSE_STAGGER_MS + CLOSE_MS + 120L);
  }

  private void animateBallIcon(boolean open) {
    if (!host.motionEnabled()) {
      gridIcon.setAlpha(open ? 0f : 1f);
      gridIcon.setRotation(open ? 45f : 0f);
      closeIcon.setAlpha(open ? 1f : 0f);
      closeIcon.setRotation(open ? 0f : -45f);
      return;
    }
    gridIcon.animate().cancel();
    closeIcon.animate().cancel();
    gridIcon.animate().alpha(open ? 0f : 1f).rotation(open ? 45f : 0f).setDuration(ICON_MS).setInterpolator(EFFECT).start();
    closeIcon.animate().alpha(open ? 1f : 0f).rotation(open ? 0f : -45f).setDuration(ICON_MS).setInterpolator(EFFECT).start();
  }

  // ── 工具 ─────────────────────────────────────────────────────────────

  /** 苹果按压缩放（与 MainActivity.applePressScale 同规格；球/胶囊自带背景，只做 scale）。
   *  v1.22.2 修 bug：收起动画进行中按下胶囊时**吞掉整段手势**——原先按下会 cancel 掉淡出动画，
   *  连同 withEndAction（隐藏自己）一起被取消，胶囊就永久留在屏幕上（真机实测：残留的是最后
   *  淡出的「工具箱」「设置」）。入场动画未跑完时按下则先吸附到落位，避免停在半路。 */
  private void pressFeedback(Pill pill) {
    final View v = pill.view;
    v.setOnTouchListener((view, event) -> {
      int action = event.getActionMasked();
      if (action == MotionEvent.ACTION_DOWN) {
        // 收起中按下：整段手势只吞不处理，让淡出正常跑完（否则 cancel 会连隐藏动作一起取消）
        if (menuClosing || !menuOpen) {
          pill.swallowed = true;
          if (!menuOpen && !menuClosing) view.setVisibility(View.GONE); // 兜底：不该可见的胶囊直接收起
          return true;
        }
        pill.swallowed = false;
        view.animate().cancel();
        view.setTranslationX(pill.targetX);
        view.setTranslationY(pill.targetY);
        view.setAlpha(1f);
        view.setScaleX(1f);
        view.setScaleY(1f);
        if (!host.motionEnabled()) return false;
        view.setScaleX(PILL_PRESS_SCALE);
        view.setScaleY(PILL_PRESS_SCALE);
      } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
        boolean swallow = pill.swallowed;
        pill.swallowed = false;
        if (swallow) return true; // 同一次手势的后续事件一并吃掉
        if (!host.motionEnabled()) return false;
        view.animate().scaleX(1f).scaleY(1f).setDuration(220).setInterpolator(PRESS_UP).start();
      }
      return false;
    });
  }

  private float clamp(float value, float min, float max) {
    return value < min ? min : (value > max ? max : value);
  }
}
