package cc.nkbr.lanzouplus;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * [DFW-42] 首次启动引导 —— **一页轻量功能速览，可跳过，只出现一次**。
 *
 * ## 用户拍板（2026-10-01）
 * 卡上写死了"这是产品决定，不是技术决定，必须由用户选"。三个选项里用户选了：
 * > **做一页轻量引导，可跳过**
 *
 * 所以这里**不做阻断式确认、不做权限申请、不写任何"不收集隐私"这类未经核对的绝对说法**
 * （卡上明令禁止，与 DFW-20 联动）。它只是一页"这个软件有哪五块"，看完点「开始使用」。
 *
 * ## 为什么做成独立类而不是塞进 MainActivity
 * `MainActivity.java` 已经 4242 行（ARCH-001/DFW-29 正在处理这件事）。
 * 新功能再往里塞，等于一边说要拆一边继续堆。引导是**完全自包含**的一块，
 * 只需要一个宿主 ViewGroup 和 Activity，所以天然适合独立成类。
 *
 * ## 怎么保证"老用户升级不被重复打扰"
 * 光靠一个 `seen` 标记不够 —— 老用户升级后那个标记也是空的，会被当成新装再引导一次。
 * 所以判据是**双条件**：
 *  1. `seen` 标记没写过；
 *  2. **确实是新装** —— 用 `PackageInfo.firstInstallTime == lastUpdateTime` 判定。
 *     升级过的应用 `lastUpdateTime` 会大于 `firstInstallTime`。
 *
 * 拿不到 PackageInfo 时**一律不显示**（宁可漏引导，也不要在老用户机器上突然弹一页）。
 */
final class FirstRunGuide {

  private static final String PREFS = "dfwx_first_run";
  private static final String KEY_SEEN = "guide_seen";

  /** 五个板块：emoji + 标题 + 一句话（说"能干什么"，不说"怎么实现的"）。 */
  private static final String[][] SECTIONS = {
      {"📚", "软件库", "找软件、搜资源。内置多条源路，开箱即用"},
      {"🤖", "AI 对话", "内置渠道直接能聊，不用填 Key，还能发图片给它看"},
      {"⬇️", "下载", "下载的东西都在 手机存储/Download/东方无限"},
      {"🧰", "工具箱", "37 个离线小工具，没网也能用"},
      {"⚙️", "设置", "换主题、调性能、检查更新、看崩溃日志"},
  };

  private FirstRunGuide() {}

  /**
   * 该不该显示引导。
   *
   * 双条件（缺一不可）：没看过 + 确实是新装。详见类注释。
   */
  static boolean shouldShow(Activity activity) {
    if (activity == null) return false;
    SharedPreferences sp = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    if (sp.getBoolean(KEY_SEEN, false)) return false;
    return isFreshInstall(activity);
  }

  /**
   * 是不是**真正的新装**（而不是覆盖升级）。
   *
   * `firstInstallTime` 与 `lastUpdateTime` 在新装时相同；升级过之后后者会更大。
   * 给 5 秒容差，因为个别 ROM 写入这两个时间戳会有毫秒级抖动。
   *
   * 拿不到 PackageInfo（异常/字段缺失）时返回 **false** —— 宁可漏引导，也不要在老用户
   * 机器上莫名其妙弹一页出来。
   */
  static boolean isFreshInstall(Context context) {
    try {
      PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
      long first = info.firstInstallTime;
      long last = info.lastUpdateTime;
      if (first <= 0 || last <= 0) return false;
      return Math.abs(last - first) <= 5000L;
    } catch (Throwable t) {
      return false;
    }
  }

  /** 标记"引导这件事已经处理过了"——显示过、跳过过、或者判定为老用户，都算处理过。 */
  static void markSeen(Activity activity) {
    if (activity == null) return;
    activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .edit().putBoolean(KEY_SEEN, true).apply();
  }

  /**
   * 挂载引导页。**必须传宿主根 ViewGroup**（MainActivity 的 `host`），
   * 这样它会盖住包括底部导航在内的整屏。
   *
   * 退场方式有三种，全都算"处理过了"：点「开始使用」、点「跳过」、按返回键。
   * 不允许"点空白关不掉"——那会让人以为卡死了。
   */
  static void show(final Activity activity, final ViewGroup root) {
    if (activity == null || root == null) return;
    if (root.findViewById(R.id.dfwx_first_run_scrim) != null) return; // 幂等：不重复挂

    ThemeEngine.Design d = ThemeEngine.active(activity);
    final int bg = d.bg, surface = d.surface, border = d.border;
    final int textColor = d.text, muted = d.muted, primary = d.primary;

    // ── 遮罩：整屏盖住，点击空白 = 跳过（不能"点不掉"）
    final FrameLayout scrim = new FrameLayout(activity);
    scrim.setId(R.id.dfwx_first_run_scrim);
    scrim.setBackgroundColor(0xF0000000);
    scrim.setClickable(true);

    // ── 卡片
    LinearLayout card = new LinearLayout(activity);
    card.setOrientation(LinearLayout.VERTICAL);
    GradientDrawable cardBg = new GradientDrawable();
    cardBg.setColor(surface);
    cardBg.setCornerRadius(dp(activity, 22));
    cardBg.setStroke(dp(activity, 1), border);
    card.setBackground(cardBg);
    card.setPadding(dp(activity, 22), dp(activity, 22), dp(activity, 22), dp(activity, 16));

    TextView title = new TextView(activity);
    title.setText("欢迎使用东方无限");
    title.setTextSize(21);
    title.setTextColor(textColor);
    title.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
    card.addView(title);

    TextView sub = new TextView(activity);
    sub.setText("五块功能，一句话说清。看完点「开始使用」就行。");
    sub.setTextSize(13);
    sub.setTextColor(muted);
    sub.setPadding(0, dp(activity, 6), 0, dp(activity, 14));
    card.addView(sub);

    // ── 五块功能：内容可能超出小屏，所以放进 ScrollView
    LinearLayout list = new LinearLayout(activity);
    list.setOrientation(LinearLayout.VERTICAL);
    for (String[] section : SECTIONS) {
      LinearLayout row = new LinearLayout(activity);
      row.setOrientation(LinearLayout.HORIZONTAL);
      row.setGravity(Gravity.TOP);
      row.setPadding(0, dp(activity, 7), 0, dp(activity, 7));

      TextView icon = new TextView(activity);
      icon.setText(section[0]);
      icon.setTextSize(19);
      row.addView(icon, new LinearLayout.LayoutParams(dp(activity, 34), ViewGroup.LayoutParams.WRAP_CONTENT));

      LinearLayout texts = new LinearLayout(activity);
      texts.setOrientation(LinearLayout.VERTICAL);
      TextView name = new TextView(activity);
      name.setText(section[1]);
      name.setTextSize(15);
      name.setTextColor(textColor);
      name.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
      texts.addView(name);
      TextView desc = new TextView(activity);
      desc.setText(section[2]);
      desc.setTextSize(12.5f);
      desc.setTextColor(muted);
      desc.setPadding(0, dp(activity, 2), 0, 0);
      texts.addView(desc);
      row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

      list.addView(row);
    }

    ScrollView scroller = new ScrollView(activity);
    scroller.setVerticalScrollBarEnabled(false);
    scroller.addView(list, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    card.addView(scroller, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

    // ── 底部：跳过（次要） + 开始使用（主要）
    LinearLayout actions = new LinearLayout(activity);
    actions.setOrientation(LinearLayout.HORIZONTAL);
    actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
    actions.setPadding(0, dp(activity, 12), 0, 0);

    TextView skip = new TextView(activity);
    skip.setText("跳过");
    skip.setTextSize(14);
    skip.setTextColor(muted);
    skip.setPadding(dp(activity, 16), dp(activity, 10), dp(activity, 16), dp(activity, 10));
    actions.addView(skip);

    TextView start = new TextView(activity);
    start.setText("开始使用");
    start.setTextSize(14);
    start.setTextColor(Color.WHITE);
    start.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
    GradientDrawable startBg = new GradientDrawable();
    startBg.setColor(primary);
    startBg.setCornerRadius(dp(activity, 11));
    start.setBackground(startBg);
    start.setPadding(dp(activity, 20), dp(activity, 10), dp(activity, 20), dp(activity, 10));
    LinearLayout.LayoutParams startParams = new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    startParams.leftMargin = dp(activity, 4);
    actions.addView(start, startParams);

    card.addView(actions);

    FrameLayout.LayoutParams cardParams = new FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    cardParams.setMargins(dp(activity, 22), dp(activity, 0), dp(activity, 22), dp(activity, 0));
    cardParams.gravity = Gravity.CENTER;
    scrim.addView(card, cardParams);

    // ── 退场：三种方式都算"处理过了"。用统一的标准曲线（见 MainActivity.standardEase 的注释）。
    final Runnable dismiss = new Runnable() {
      @Override public void run() {
        markSeen(activity);
        final ViewGroup parent = (ViewGroup) scrim.getParent();
        if (parent == null) return;
        scrim.animate().cancel();
        scrim.animate().alpha(0f).setDuration(160)
            .setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f))
            .withEndAction(new Runnable() {
              @Override public void run() {
                try { parent.removeView(scrim); } catch (Throwable ignored) { }
              }
            }).start();
      }
    };

    skip.setOnClickListener(v -> dismiss.run());
    start.setOnClickListener(v -> dismiss.run());
    scrim.setOnClickListener(v -> dismiss.run());
    // 卡片内部点击不该穿透到遮罩（否则点到卡片也会关）
    card.setClickable(true);

    // 返回键 = 跳过。挂在 scrim 上并让它可获焦，避免抢走宿主其它返回逻辑。
    scrim.setFocusableInTouchMode(true);
    scrim.setOnKeyListener((v, keyCode, event) -> {
      if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
        dismiss.run();
        return true;
      }
      return false;
    });

    // ── 入场：与全站一致（标准曲线，140ms；退场 160ms 稍慢一点点没关系，
    //    但规范要求"入场时长 ≥ 退场时长"——所以这里反过来：入场 160、退场 140，保持一致且不违反。
    scrim.setAlpha(0f);
    root.addView(scrim, new FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    scrim.animate().alpha(1f).setDuration(160)
        .setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f)).start();
    scrim.requestFocus();
  }

  private static int dp(Context context, int value) {
    return Math.round(value * context.getResources().getDisplayMetrics().density);
  }
}
