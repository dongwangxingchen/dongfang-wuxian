package cc.nkbr.lanzouplus;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * [DFW-88] **用隐藏 WebView 解阿里云 WAF 的 `acw_sc__v2` 挑战。**
 *
 * ## 为什么必须这么做（不是偷懒，是算不出来）
 *
 * 蓝奏云的下载链路上有一道阿里云 WAF 的 JS 挑战：请求会返回一段**混淆过的脚本**，
 * 脚本在浏览器里算出一个 cookie（`acw_sc__v2`）再重载，才给真实直链。
 *
 * 项目里原本自己实现了一套算法（`LanzouCore.acwCookie`，用的是社区流传多年的
 * `POS` / `MASK` 常量）。**2026-10-02 实测证明它已经算错了**：
 *
 * ```
 * 输入 arg1 : F77233DFEDA62054F49DC87FA10FB5F66BA7C277
 * 我们的算法: 6abf7392de1a1081a42f758c3c621cd2d4ffbf12   ← 算出来的
 * 浏览器真值: 6abf74188c6f4d858f38dc65def97ab45998323d   ← 实际正确的
 * ```
 *
 * 阿里云改过算法，常量失效了。于是：
 * · 算出的 cookie 不被接受 → `getGuarded` 重试 3 轮 → 抛「蓝奏 ACW 验证未完成」
 * · 而**这个异常在用户界面上什么都不显示** —— 下载项就停在「解析中」，永远不动。
 *
 * ## 为什么不去追新的常量
 * 追常量是**打地鼠**：阿里云每次调整，我们就得重新逆向一遍混淆脚本，
 * 期间所有用户都下载不了。而这段脚本**本来就是给浏览器执行的** ——
 * 我们手上就有浏览器（WebView）。让它自己算，**永远不会过期**。
 *
 * ## 实测依据
 * 2026-10-02 用真实 Chromium 加载同一个挑战地址：
 * ```
 * cookie 数: 3   →  acw_tc, cdn_sec_tc, acw_sc__v2
 * 最后一个响应: https://c1031.dmpdmp.com/.../*.apk  (200)
 * 下载事件: SD Maid.apk          ← 真的开始下载了
 * ```
 * **全程零人工交互** —— 没有滑块、没有点选、没有验证码，1 秒内自己算完。
 * （详见 `docs/audit/20261002-dfdfw88-download-rootcause.md`。）
 *
 * ## 线程模型
 * WebView 只能在主线程创建和使用，而调用方在下载线程。
 * 所以：主线程建 WebView + 加载 → 调用线程 `await` → 拿到 cookie → 主线程销毁。
 * **不能反过来**（在调用线程直接 new WebView 会抛）。
 */
final class WafCookieSolver {

  /** 挑战页最长等多久。实测 1 秒内出结果，5 秒是给弱网/慢机的余量。 */
  private static final long SOLVE_TIMEOUT_MS = 5000;

  /** 轮询间隔：cookie 是脚本异步算出来的，没有回调可挂，只能轮询。 */
  private static final long POLL_INTERVAL_MS = 120;

  /**
   * cookie 缓存：同一个域名算一次就够。
   * 阿里云的 cookie 有有效期，缓存太久会拿到过期的；
   * 这里只缓存**本次进程生命周期内**的结果，并且**只在成功时缓存**。
   */
  private static final Map<String, String> CACHE = new LinkedHashMap<>();

  private static final Handler MAIN = new Handler(Looper.getMainLooper());

  private WafCookieSolver() {}

  /**
   * 同步解出 `url` 所属域名的 WAF cookie；失败返回空串（**绝不抛**）。
   *
   * 返回空串时调用方按"没解开"处理，走原来的失败路径 ——
   * 求解器是**增强**，不是新的失败点。
   */
  static String solve(Context context, String url) {
    if (context == null || url == null || url.isEmpty()) return "";
    String host = hostOf(url);
    if (host.isEmpty()) return "";
    synchronized (CACHE) {
      String cached = CACHE.get(host);
      if (cached != null && !cached.isEmpty()) return cached;
    }

    final Context app = context.getApplicationContext();
    final CountDownLatch done = new CountDownLatch(1);
    final String[] result = new String[] {""};
    final WebView[] holder = new WebView[1];

    MAIN.post(
        () -> {
          try {
            holder[0] = buildWebView(app);
            holder[0].loadUrl(url);
          } catch (Throwable error) {
            // 建不起来就直接认输，别把主线程拖住
            done.countDown();
            return;
          }
          // 轮询等待脚本算完 cookie
          MAIN.postDelayed(new Runnable() {
            int waited = 0;

            @Override
            public void run() {
              String cookie = cookieFor(url);
              boolean has = cookie.contains("acw_sc__v2");
              if (has || waited >= SOLVE_TIMEOUT_MS) {
                result[0] = has ? cookie : "";
                done.countDown();
                return;
              }
              waited += POLL_INTERVAL_MS;
              MAIN.postDelayed(this, POLL_INTERVAL_MS);
            }
          }, POLL_INTERVAL_MS);
        });

    try {
      // 多等 1 秒：给主线程建 WebView + 销毁留出时间，避免调用方先超时、主线程还在跑
      done.await(SOLVE_TIMEOUT_MS + 1500, TimeUnit.MILLISECONDS);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
    }

    // 无论成功与否都要销毁：WebView 不销毁会漏一整个渲染进程
    MAIN.post(() -> destroyQuietly(holder[0]));

    String solved = result[0] == null ? "" : result[0];
    if (!solved.isEmpty()) {
      synchronized (CACHE) {
        CACHE.put(host, solved);
      }
    }
    return solved;
  }

  /** 挑战解出来之后，同一域名下的资源请求要带上这套 cookie。 */
  static String cachedCookie(String url) {
    String host = hostOf(url);
    if (host.isEmpty()) return "";
    synchronized (CACHE) {
      String value = CACHE.get(host);
      return value == null ? "" : value;
    }
  }

  /** 供测试与排障：清掉缓存。 */
  static void clearCache() {
    synchronized (CACHE) {
      CACHE.clear();
    }
  }

  @SuppressLint("SetJavaScriptEnabled")
  private static WebView buildWebView(Context context) {
    WebView web = new WebView(context);
    WebSettings settings = web.getSettings();
    // 挑战脚本必须能跑 —— 这是整个类存在的唯一理由
    settings.setJavaScriptEnabled(true);
    settings.setDomStorageEnabled(true);
    // 不加载图片/不占资源：我们只要那段脚本跑完，不要画面
    settings.setBlockNetworkImage(true);
    settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
    // 让它像一次正常浏览，而不是脚本（阿里云会看这些）
    settings.setUserAgentString(LanzouCore.ANDROID_UA);
    CookieManager cookies = CookieManager.getInstance();
    cookies.setAcceptCookie(true);
    try {
      cookies.setAcceptThirdPartyCookies(web, true);
    } catch (Throwable ignored) {
      // 老版本没有这个方法；不设也能过
    }
    web.setWebViewClient(new WebViewClient());
    // 尺寸给 1×1：不需要可见，也不能是 0（0 尺寸的 WebView 在部分 ROM 上不执行 JS）
    web.measure(
        android.view.View.MeasureSpec.makeMeasureSpec(1, android.view.View.MeasureSpec.EXACTLY),
        android.view.View.MeasureSpec.makeMeasureSpec(1, android.view.View.MeasureSpec.EXACTLY));
    web.layout(0, 0, 1, 1);
    return web;
  }

  private static String cookieFor(String url) {
    try {
      String value = CookieManager.getInstance().getCookie(url);
      return value == null ? "" : value;
    } catch (Throwable ignored) {
      return "";
    }
  }

  private static void destroyQuietly(WebView web) {
    if (web == null) return;
    try {
      web.stopLoading();
      web.loadUrl("about:blank");
      web.destroy();
    } catch (Throwable ignored) {
      // 销毁失败不能影响下载流程
    }
  }

  private static String hostOf(String url) {
    try {
      String host = new java.net.URL(url).getHost();
      return host == null ? "" : host;
    } catch (Throwable ignored) {
      return "";
    }
  }
}
