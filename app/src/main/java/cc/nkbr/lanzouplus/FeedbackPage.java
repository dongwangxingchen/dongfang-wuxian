package cc.nkbr.lanzouplus;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * [DFW-97] 「反馈与建议」页：用 WebView 打开 FlowUs 反馈表，并把它**换肤成《东方无限》的样子**。
 *
 * ## 用户原话（2026-10-02）
 * > 「设置页加一个『反馈与建议』的入口，点进去打开 flowus 那个反馈表，
 * > 但是**它的风格和我的软件不一样，你得统一一下**。」
 *
 * ## 换肤为什么不用类名选择器
 * FlowUs 是第三方 SPA，类名是构建产物（带哈希、随时会变），
 * 靠 `[class*="banner"]` 这种猜法迟早失效。**实测第一版就是这么翻车的**：
 * 把 `body` 背景刷黑、文字刷成浅色，但真正承载白底的是内层容器 ——
 * 结果变成"白底浅字"，**比不换肤还糟**（截图见 `docs/plan/dfw-97-feedback-page.md`）。
 *
 * 最终做法：注入的脚本**遍历 DOM、读每个元素真实的计算样式**，
 * 按明度重映射（接近白的底 → 深色；接近黑的字 → 浅色；饱和蓝 → 品牌紫），
 * 并用 `MutationObserver` 跟上动态内容。与类名无关，FlowUs 改版也不会失效。
 *
 * ## 三条铁律
 * 1. **只改颜色与可见性，绝不改布局、绝不改 `pointer-events`** ——
 *    改布局会让 FlowUs 自己的表单逻辑错位，改 `pointer-events` 会让用户点不动。
 * 2. **不做"猜着点"**：预览模式横条只在**确实找到那条文字**时才点；找不到就算了。
 * 3. 脚本失败不影响页面可用（全部包在 try 里）——换肤是锦上添花，不是功能前提。
 */
public final class FeedbackPage extends Activity {

  /** 反馈表地址。FlowUs 分享页，用户 2026-10-02 确认用它。 */
  static final String FEEDBACK_URL = "https://flowus.cn/dongfang/share/d0183611-c568-4d63-98ed-f28b81dff202";

  static final String EXTRA_URL = "dfwx.feedback.url";

  WebView web;
  FrameLayout host;
  LinearLayout thanksBar;
  String skinScript = "";
  boolean backCallbackRegistered;
  int BG, SURFACE, TEXT, MUTED, DIV, PRIMARY;

  /**
   * [DFW-97] 预测性返回（DFW-17 同款，照抄 `LanzouWebActivity`）。
   *
   * 为什么必须做：本 App 的 targetSdk 是 37，**`enableOnBackInvokedCallback` 的默认值就是 true**，
   * 也就是系统会走"预测性返回"。此时如果页面不注册 `OnBackInvokedCallback`，
   * 系统会**直接结束 Activity**，`onBackPressed()` 根本不会被调用 ——
   * 结果是"在网页里翻了几层之后按返回，直接退出了反馈页，而不是退回上一页"。
   *
   * 语义与 `LanzouWebActivity` 一致：网页还能后退 → 注册回调自己处理；
   * 已经在网页历史起点 → 注销回调，把返回交回系统，于是"退出本页"这一步恢复预览动画。
   */
  final android.window.OnBackInvokedCallback backCallback = this::handleBack;

  @Override public void onCreate(Bundle state) {
    super.onCreate(state);
    applyPalette();
    skinScript = readAsset("feedback_skin.js");

    FrameLayout root = new FrameLayout(this);
    root.setBackgroundColor(BG);
    host = root;

    web = new WebView(this);
    web.setBackgroundColor(BG);
    hardenWebView(web.getSettings());
    web.setWebViewClient(new WebViewClient() {
      @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
        return r != null && r.isForMainFrame() && leaveApp(r.getUrl() == null ? "" : r.getUrl().toString());
      }
      @Override public boolean shouldOverrideUrlLoading(WebView v, String url) {
        return leaveApp(url == null ? "" : url);
      }
      @Override public void onPageFinished(WebView v, String url) {
        syncBackCallback();
        // 每次页面加载完都注入一次：FlowUs 是 SPA，内部跳转不会重新触发 onPageFinished，
        // 但刷新/重进时需要重新换肤。
        injectSkin();
      }
    });
    web.setWebChromeClient(new WebChromeClient());
    // 唯一暴露给网页的能力：告诉我们"提交成功了"。**不暴露任何数据、不暴露任何读写方法**。
    web.addJavascriptInterface(new Bridge(), "DFWX");

    root.addView(web, new FrameLayout.LayoutParams(-1, -1));

    root.addView(buildTopBar(), topBarParams());
    root.addView(buildThanksBar(), thanksParams());

    setContentView(root);
    web.loadUrl(getIntent().getStringExtra(EXTRA_URL) == null
        ? FEEDBACK_URL : getIntent().getStringExtra(EXTRA_URL));
  }

  // ── 外观 ────────────────────────────────────────────────────────────

  void applyPalette() {
    ThemeEngine.Design d = ThemeEngine.active(this);
    BG = d.bg; SURFACE = d.surface; TEXT = d.text; MUTED = d.muted; DIV = d.border; PRIMARY = d.primary;
    Window w = getWindow();
    w.setStatusBarColor(BG);
    w.setNavigationBarColor(BG);
    int flags = w.getDecorView().getSystemUiVisibility();
    flags &= ~(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
    w.getDecorView().setSystemUiVisibility(flags);
  }

  /** 顶栏：返回 + 标题。与 App 内其它二级页同一套观感。 */
  View buildTopBar() {
    LinearLayout bar = new LinearLayout(this);
    bar.setGravity(Gravity.CENTER_VERTICAL);
    bar.setBackgroundColor(BG);
    bar.setPadding(dp(6), 0, dp(12), 0);

    TextView back = new TextView(this);
    back.setText("‹");
    back.setTextSize(26);
    back.setTextColor(PRIMARY);
    back.setGravity(Gravity.CENTER);
    back.setContentDescription("返回");
    back.setBackground(ripple());
    back.setOnClickListener(v -> finish());
    bar.addView(back, new LinearLayout.LayoutParams(dp(44), dp(48)));

    TextView title = new TextView(this);
    title.setText("反馈与建议");
    title.setTextColor(TEXT);
    title.setTextSize(16);
    title.setTypeface(AppFonts.bold(this));
    bar.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1));
    return bar;
  }

  FrameLayout.LayoutParams topBarParams() {
    FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, dp(48), Gravity.TOP);
    lp.topMargin = statusBarInset();
    return lp;
  }

  FrameLayout.LayoutParams thanksParams() {
    FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
    lp.setMargins(dp(14), 0, dp(14), dp(20));
    return lp;
  }

  /**
   * 提交成功后弹出的**我们自己的**提示条。
   *
   * 为什么不直接把 FlowUs 那句英文 toast 留给用户看：那是第三方站点的文案，
   * 和 App 的语言、配色、措辞都不一致；而且它不告诉用户"接下来怎么办"。
   * 这里给一句话 + 一个「返回软件」按钮，用户不用自己找返回。
   */
  LinearLayout buildThanksBar() {
    thanksBar = new LinearLayout(this);
    thanksBar.setOrientation(LinearLayout.VERTICAL);
    thanksBar.setPadding(dp(16), dp(14), dp(16), dp(12));
    GradientDrawable bg = new GradientDrawable();
    bg.setColor(SURFACE);
    bg.setCornerRadius(dp(18));
    bg.setStroke(dp(1), PRIMARY);
    thanksBar.setBackground(bg);
    thanksBar.setElevation(dp(12));
    thanksBar.setVisibility(View.GONE);

    TextView head = new TextView(this);
    head.setText("已收到你的反馈，感谢！");
    head.setTextColor(TEXT);
    head.setTextSize(15);
    head.setTypeface(AppFonts.bold(this));
    thanksBar.addView(head, new LinearLayout.LayoutParams(-1, -2));

    TextView sub = new TextView(this);
    sub.setText("我会尽快看，重要问题会优先处理。");
    sub.setTextColor(MUTED);
    sub.setTextSize(12);
    sub.setPadding(0, dp(6), 0, dp(10));
    thanksBar.addView(sub, new LinearLayout.LayoutParams(-1, -2));

    TextView back = new TextView(this);
    back.setText("返回软件");
    back.setTextColor(Color.WHITE);
    back.setTextSize(14);
    back.setTypeface(AppFonts.bold(this));
    back.setGravity(Gravity.CENTER);
    GradientDrawable button = new GradientDrawable();
    button.setColor(PRIMARY);
    button.setCornerRadius(dp(12));
    back.setBackground(button);
    back.setOnClickListener(v -> finish());
    thanksBar.addView(back, new LinearLayout.LayoutParams(-1, dp(44)));
    return thanksBar;
  }

  void showThanks() {
    if (thanksBar == null || thanksBar.getVisibility() == View.VISIBLE) return;
    thanksBar.setVisibility(View.VISIBLE);
    thanksBar.setAlpha(0f);
    thanksBar.setTranslationY(dp(24));
    thanksBar.animate().alpha(1f).translationY(0f).setDuration(200).start();
  }

  // ── WebView 硬化 ─────────────────────────────────────────────────────
  //
  // 与 LanzouWebActivity 同一套官方安全基线（DFW-10）：显式钉死，不吃平台默认值。
  @SuppressLint("SetJavaScriptEnabled")
  void hardenWebView(WebSettings ws) {
    ws.setJavaScriptEnabled(true);          // FlowUs 是 SPA，不跑 JS 就是白屏
    ws.setDomStorageEnabled(true);          // 表单状态依赖 localStorage
    ws.setAllowFileAccess(false);
    ws.setAllowContentAccess(false);
    ws.setAllowFileAccessFromFileURLs(false);
    ws.setAllowUniversalAccessFromFileURLs(false);
    ws.setGeolocationEnabled(false);
    ws.setSaveFormData(false);
    if (Build.VERSION.SDK_INT >= 21) {
      ws.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
    }
    ws.setUserAgentString(ws.getUserAgentString());
  }

  /** 只拦"离开 App"的外链，站内跳转照常。 */
  boolean leaveApp(String url) {
    String u = url == null ? "" : url.trim();
    if (u.isEmpty()) return false;
    android.net.Uri uri;
    try { uri = android.net.Uri.parse(u); } catch (Exception ignored) { return false; }
    String scheme = uri.getScheme();
    if (scheme == null || scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https")) return false;
    try {
      android.content.Intent external = new android.content.Intent(android.content.Intent.ACTION_VIEW, uri);
      external.addCategory(android.content.Intent.CATEGORY_BROWSABLE);
      external.setFlags(0);
      startActivity(external);
    } catch (Exception ignored) { /* 没有应用能处理就算了 */ }
    return true;
  }

  void injectSkin() {
    if (web == null || skinScript.isEmpty()) return;
    // 包在 IIFE 里执行，异常不会污染页面全局
    web.evaluateJavascript(skinScript, null);
  }

  String readAsset(String name) {
    try (InputStream in = getAssets().open(name);
         BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
      StringBuilder sb = new StringBuilder();
      String line;
      while ((line = reader.readLine()) != null) sb.append(line).append('\n');
      return sb.toString();
    } catch (Exception ignored) {
      // 读不到就退化成"不换肤"——页面本身仍然可用，绝不能因此白屏
      return "";
    }
  }

  /** 网页 → App 的唯一通道。只有"提交成功"这一个信号，没有任何读写能力。 */
  final class Bridge {
    @JavascriptInterface public void onSubmitted() {
      runOnUiThread(FeedbackPage.this::showThanks);
    }
  }

  // ── 返回 ─────────────────────────────────────────────────────────────

  void syncBackCallback() {
    if (Build.VERSION.SDK_INT < 33) return;
    boolean canGoBack = web != null && web.canGoBack();
    if (canGoBack == backCallbackRegistered) return;
    try {
      if (canGoBack) {
        getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
            android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, backCallback);
        backCallbackRegistered = true;
      } else {
        getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(backCallback);
        backCallbackRegistered = false;
      }
    } catch (Exception error) {
      android.util.Log.w("FeedbackPage", "syncBackCallback: " + error.getMessage(), error);
    }
  }

  void handleBack() {
    if (web != null && web.canGoBack()) web.goBack();
    else finish();
  }

  @SuppressLint("GestureBackNavigation") @Override public void onBackPressed() {
    handleBack();
  }

  @Override protected void onDestroy() {
    if (web != null) {
      host.removeView(web);
      web.destroy();
      web = null;
    }
    super.onDestroy();
  }

  // ── 小工具 ───────────────────────────────────────────────────────────

  int statusBarInset() {
    int id = getResources().getIdentifier("status_bar_height", "dimen", "android");
    return id > 0 ? getResources().getDimensionPixelSize(id) : dp(24);
  }

  GradientDrawable ripple() {
    GradientDrawable g = new GradientDrawable();
    g.setColor(Color.TRANSPARENT);
    return g;
  }

  int dp(int v) {
    return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
  }
}
