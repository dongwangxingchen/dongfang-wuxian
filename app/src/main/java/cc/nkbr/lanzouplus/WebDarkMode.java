package cc.nkbr.lanzouplus;

import android.content.Context;
import android.webkit.WebView;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * [DFW-97] **所有内置网页统一走暗夜模式。**
 *
 * ## 用户要求（2026-10-02）
 * > 「我们软件所有网站都打开默认暗夜模式。」
 *
 * ## 做法
 * 把 `assets/web_dark_mode.js` 注入到每个 WebView 页面。
 * 脚本不猜类名（第三方站点的类名是构建产物、随时会变），
 * 而是**遍历 DOM 读每个元素真实的计算样式**，按明度重映射：
 * 接近白的底 → 深色、接近黑的字 → 浅色、饱和亮蓝 → 品牌紫，
 * 并用 `MutationObserver` 跟上 SPA 的动态内容。
 *
 * ## 三条铁律（脚本里也写着，这里再钉一遍）
 * 1. **只改颜色，绝不改布局** —— 改布局会让站点自己的逻辑错位。
 * 2. **绝不碰 pointer-events** —— 碰了用户就点不动页面。
 * 3. 脚本读不到 / 执行失败**只是"没变暗"**，页面照样能用 ——
 *    换肤是锦上添花，不是功能前提，绝不能因此白屏。
 *
 * ## 为什么抽成独立类
 * 网页页现在有两处（`LanzouWebActivity` 与 `FeedbackPage`）。
 * 复制两份 asset 读取逻辑的话，改一处忘一处就是"有的页面变暗、有的没变"——
 * 而这正是用户最初反馈的现象（只有反馈页被改了，蓝奏云页没改）。
 */
final class WebDarkMode {

  /** 脚本只在第一次读盘，之后复用（asset 内容不会变）。 */
  private static String cached;

  private WebDarkMode() {}

  /** 读脚本内容；读不到返回空串（调用方按"不变暗"处理，不抛）。 */
  static synchronized String script(Context context) {
    if (cached != null) return cached;
    if (context == null) return "";
    try (InputStream in = context.getAssets().open("web_dark_mode.js");
         BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
      StringBuilder sb = new StringBuilder();
      String line;
      while ((line = reader.readLine()) != null) sb.append(line).append('\n');
      cached = sb.toString();
    } catch (Exception ignored) {
      // 读不到就退化成"不换肤"。**绝不能因此白屏** —— 页面本身仍然可用。
      cached = "";
    }
    return cached;
  }

  /**
   * 把暗夜模式注入到指定 WebView。**页面每次加载完都要调一次**：
   * SPA 内部跳转不会重新触发 `onPageFinished`，但刷新/重进时需要重新注入。
   */
  static void apply(Context context, WebView web) {
    if (web == null) return;
    String script = script(context);
    if (script.isEmpty()) return;
    try {
      web.evaluateJavascript(script, null);
    } catch (Exception ignored) {
      // 注入失败只是没变暗，页面照常
    }
  }
}
