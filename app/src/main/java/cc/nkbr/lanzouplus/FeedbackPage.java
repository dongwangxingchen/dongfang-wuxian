package cc.nkbr.lanzouplus;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

/**
 * [DFW-97] 「反馈与建议」页。
 *
 * ## 用户 2026-10-02 定调（原话）
 * > 「你那个反馈问题的页面就去掉没用的乱七八糟的吧，只留下网页和暗夜模式，
 * >   别搞乱七八糟的，提交成功后就提示用户提交完成，请留意邮箱。返回到主页面。
 * >   能理解我意思吗，不要打一堆补丁」
 *
 * ## 所以这一版**只做三件事**
 * 1. 把网页显示出来（全屏 WebView）；
 * 2. 给网页套上暗夜模式（走共享的 {@link WebDarkMode}，不是本页自己一套）；
 * 3. 提交成功后提示一句「提交完成，请留意邮箱」，然后**自动回主页面**。
 *
 * ## 被删掉的东西（都是上一版我加的"补丁"，也正是出问题的来源）
 * · **自绘顶栏**（标题 + 胶囊 + 一串 dp 计算）—— 用户要的是网页，不是我的壳；
 * · **「感谢条」整套**（SURFACE 底、PRIMARY 描边、淡入位移、返回按钮）——
 *   提交完就回主页了，根本没有机会看到它；
 * · **`skipPreviewMode()` 自动点预览横条** —— 那是给**错的分享页链接**打的补丁；
 *   换成 form 直链后本来就不需要；
 * · **换肤脚本里每 1.2 秒读一次 `document.body.innerText` 的轮询** ——
 *   那个属性会强制整页重排，是用户反馈的"页面又卡又慢"的真凶，已改成事件驱动。
 *
 * ## 还留着的（都是**功能必需**，不是装饰）
 * · 左上角返回：没有它用户出不去（用全站约定的 `ic_back` 矢量图标，不用字体字符 `‹`）；
 * · 文件选择器：表单第 3 题就是传图/视频/文件，没有它"上传文件点不动"；
 * · `setAllowContentAccess(true)`：选择器返回 `content://` URI，关掉 WebView 就读不了；
 * · 提交回调：用户明确要的"提交成功后提示"。
 */
public class FeedbackPage extends Activity {

  /** 反馈表 **form 直链**（不是 share 分享页 —— 分享页要手动切视图、还带预览横条）。 */
  static final String FEEDBACK_URL =
      "https://flowus.cn/form/511c82ce-70dd-4a74-82c4-12f3a65d6496?code=NDH3Z3";

  private static final int REQ_PICK_FILE = 1001;

  private WebView web;
  private ValueCallback<Uri[]> fileCallback;

  @Override
  protected void onCreate(Bundle state) {
    super.onCreate(state);
    int bg = backgroundColor();
    applyEdgeToEdge(bg);

    FrameLayout root = new FrameLayout(this);
    root.setBackgroundColor(bg);

    web = new WebView(this);
    hardenWebView(web.getSettings());
    web.setWebViewClient(new WebViewClient() {
      @Override
      public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest request) {
        String url = request.getUrl() == null ? "" : request.getUrl().toString();
        return leaveApp(url);
      }

      @Override
      public void onPageFinished(WebView v, String url) {
        // 每次加载完都套一次：SPA 内部跳转不触发这个回调，但刷新/重进需要
        WebDarkMode.apply(FeedbackPage.this, v);
      }
    });
    web.setWebChromeClient(new WebChromeClient() {
      @Override
      public boolean onShowFileChooser(
          WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
        return openFileChooser(callback, params);
      }
    });
    web.addJavascriptInterface(new Bridge(), "DFWX");
    root.addView(web, new FrameLayout.LayoutParams(-1, -1));

    root.addView(buildBackButton(bg), backButtonParams());
    setContentView(root);

    web.loadUrl(FEEDBACK_URL);
  }

  // ───────────────────────────────────── 返回按钮（唯一的一点点壳）

  /**
   * 左上角返回。用全站约定的 `ic_back` **矢量图标**，不用字体字符 `‹` ——
   * 用户 2026-10-02 反馈过 `‹` "看着偏"，根因是字体字形的光学重心不等于布局中心。
   */
  private ImageButton buildBackButton(int bg) {
    ImageButton back = new ImageButton(this);
    back.setImageResource(R.drawable.ic_back);
    back.setColorFilter(primaryColor());
    back.setContentDescription("返回");
    back.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
    back.setPadding(dp(10), dp(10), dp(10), dp(10));
    back.setBackground(ripple(bg));
    back.setOnClickListener(v -> finish());
    return back;
  }

  private FrameLayout.LayoutParams backButtonParams() {
    // 落在状态栏之下；44dp 是安卓无障碍最小触摸目标
    FrameLayout.LayoutParams lp =
        new FrameLayout.LayoutParams(dp(44), dp(44), Gravity.TOP | Gravity.START);
    lp.topMargin = statusBarInset();
    lp.leftMargin = dp(4);
    return lp;
  }

  // ───────────────────────────────────── 网页配置

  private void hardenWebView(WebSettings ws) {
    ws.setJavaScriptEnabled(true);   // FlowUs 是 SPA，不跑 JS 就是白屏
    ws.setDomStorageEnabled(true);   // 表单状态依赖 localStorage
    ws.setAllowFileAccess(false);    // 网页不许直接读设备文件系统
    /*
     * 必须是 true：系统文件选择器返回的是 `content://` URI，关掉它 WebView 就读不了 ——
     * 表现为"选完文件没反应"。这仍是最小必要权限：
     * 网页只能读用户**亲手选中的那一个** URI。
     */
    ws.setAllowContentAccess(true);
    ws.setAllowFileAccessFromFileURLs(false);
    ws.setAllowUniversalAccessFromFileURLs(false);
    ws.setGeolocationEnabled(false);
    ws.setSaveFormData(false);
    if (Build.VERSION.SDK_INT >= 21) {
      ws.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
    }
  }

  /**
   * 拉起系统文件选择器。
   *
   * 网页里 `<input type="file">` 被点击时 WebView 会回调这里，而
   * `WebChromeClient` 的**默认实现是"什么都不做"** —— 所以不实现它，
   * 用户点上传就是"点了没反应、也不报错"。
   */
  private boolean openFileChooser(
      ValueCallback<Uri[]> callback, WebChromeClient.FileChooserParams params) {
    // 上一次还没回来又点了一次：先把旧的按"取消"结掉，否则 WebView 会一直等
    if (fileCallback != null) {
      fileCallback.onReceiveValue(null);
      fileCallback = null;
    }
    fileCallback = callback;
    try {
      Intent intent = params.createIntent();
      if (params.getMode() == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE) {
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
      }
      startActivityForResult(intent, REQ_PICK_FILE);
      return true;
    } catch (Exception error) {
      android.util.Log.w("FeedbackPage", "onShowFileChooser: " + error.getMessage(), error);
      fileCallback = null;
      return false;
    }
  }

  /**
   * 选择器回来了。三种情况都要处理：单选、多选（`ClipData`）、用户取消（null）。
   * **无论哪条路径都必须调一次 `onReceiveValue`**，否则网页那边的
   * `<input type="file">` 会一直卡在等待中，用户看到的还是"点了没反应"。
   */
  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    if (requestCode != REQ_PICK_FILE) {
      super.onActivityResult(requestCode, resultCode, data);
      return;
    }
    ValueCallback<Uri[]> callback = fileCallback;
    fileCallback = null;
    if (callback == null) return;
    Uri[] result = null;
    if (resultCode == RESULT_OK && data != null) {
      if (data.getClipData() != null) {
        int count = data.getClipData().getItemCount();
        result = new Uri[count];
        for (int i = 0; i < count; i++) {
          result[i] = data.getClipData().getItemAt(i).getUri();
        }
      } else if (data.getData() != null) {
        result = new Uri[] {data.getData()};
      }
    }
    callback.onReceiveValue(result);
  }

  /** 出了反馈表就把链接交给系统（用户可能点邮箱、点 FlowUs 首页）。 */
  private boolean leaveApp(String url) {
    if (url == null || url.isEmpty()) return false;
    // 反馈表自身（含提交后的跳转）留在本页
    if (url.contains("511c82ce-70dd-4a74-82c4-12f3a65d6496")) return false;
    try {
      startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
    } catch (Exception ignored) {
      // 没有浏览器可接就留在本页，不崩
      return false;
    }
    return true;
  }

  // ───────────────────────────────────── 提交完成

  /**
   * 网页那边提交成功后调过来。
   *
   * 用户要的就是一句话 + 回主页：
   * 「提交成功后就提示用户提交完成，请留意邮箱。返回到主页面。」
   * 所以这里**不做**任何花哨的"感谢条" —— 提示完就结束本页。
   */
  final class Bridge {
    @JavascriptInterface
    public void onSubmitted() {
      runOnUiThread(
          () -> {
            Toast.makeText(FeedbackPage.this, "提交完成，请留意邮箱", Toast.LENGTH_LONG).show();
            // 给提示一点时间被看到再退出
            if (web != null) web.postDelayed(FeedbackPage.this::finish, 1200);
          });
    }
  }

  // ───────────────────────────────────── 系统外观

  private void applyEdgeToEdge(int bg) {
    Window window = getWindow();
    window.setStatusBarColor(bg);
    window.setNavigationBarColor(bg);
    int flags = window.getDecorView().getSystemUiVisibility();
    flags &= ~(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
    window.getDecorView().setSystemUiVisibility(flags);
  }

  private int backgroundColor() {
    try {
      return ThemeEngine.active(this).bg;
    } catch (Exception ignored) {
      return Color.BLACK;
    }
  }

  private int primaryColor() {
    try {
      return ThemeEngine.active(this).primary;
    } catch (Exception ignored) {
      return 0xFF7C4DFF;
    }
  }

  private int statusBarInset() {
    int id = getResources().getIdentifier("status_bar_height", "dimen", "android");
    return id > 0 ? getResources().getDimensionPixelSize(id) : dp(24);
  }

  @SuppressLint("GestureBackNavigation")
  @Override
  public void onBackPressed() {
    // 网页还能后退就先退网页，否则退出本页
    if (web != null && web.canGoBack()) web.goBack();
    else finish();
  }

  @Override
  protected void onDestroy() {
    if (web != null) {
      ViewGroup parent = (ViewGroup) web.getParent();
      if (parent != null) parent.removeView(web);
      web.destroy();
      web = null;
    }
    super.onDestroy();
  }

  private Drawable ripple(int bg) {
    return new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), new ColorDrawable(bg), null);
  }

  private int dp(int value) {
    return Math.round(value * getResources().getDisplayMetrics().density);
  }
}
