package cc.nkbr.lanzouplus;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.os.Build;
import android.os.Looper;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * [DFW-75] 诚信付费页（独立 Activity）转场守卫。
 *
 * 用户 2026-10-01 两轮真机反馈：
 * <blockquote>
 *   第一轮："诚信付费的那个预返回动画与其他的不一样……能不能给它们统一为一个非常好的效果？"<br>
 *   第二轮："在设置界面点击诚信付费后，那个进入的动画有问题。我希望你可以改为和崩溃日志以及
 *          关于东方无限这些界面一样的进入方式。然后退出的时候也不用加上预加载动画了。"
 * </blockquote>
 *
 * 所以本页现在只有**一条**动画：进场从右侧滑入（方向与站内子页推入一致）。
 * 退场不做任何转场。
 *
 * <h2>为什么不能给"调用方窗口"加动画（上一版翻车点）</h2>
 * 站内推入会同时把下面那页缩到 0.94 并压暗到 55%，因为那是**同一个窗口里的另一个 View**，
 * 底下是应用自己的黑底。而付费页是**独立 Activity**：把设置页窗口整体调成 55% 透明，
 * 透出来的是**桌面壁纸**，再加整窗缩放露出黑边 —— 用户看到的就是"进入的动画有问题"。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w411dp-h891dp-420dpi")
public class SupportPageTransitionJvmTest {

  private static SupportActivity activity() {
    return Robolectric.buildActivity(SupportActivity.class).setup().get();
  }

  private static String source(String path) {
    try {
      File root = new File(System.getProperty("user.dir")).getAbsoluteFile();
      while (root != null && !new File(root, "app/src/main/AndroidManifest.xml").isFile()) root = root.getParentFile();
      return new String(Files.readAllBytes(new File(root, path).toPath()), StandardCharsets.UTF_8);
    } catch (Exception error) {
      throw new AssertionError("读不到源码 " + path, error);
    }
  }

  // ── 进场 ──────────────────────────────────────────────────────────────

  @Test public void open_slidesTheIncomingPageInFromTheRight() {
    SupportActivity a = activity();
    assertEquals(
        "进场必须是站内那条右滑入资源",
        R.anim.dfwx_page_open_in,
        Shadows.shadowOf(a).getPendingTransitionEnterAnimationResourceId()
    );
  }

  @Test public void open_neverAnimatesTheCallerWindow() {
    // 这条是上一版翻车的直接守卫：调用方窗口的动画必须是 0（不动它）。
    // 给它加 alpha 会让设置页变半透明、透出桌面壁纸；加 scale 会露出黑边。
    SupportActivity a = activity();
    assertEquals(
        "调用方（设置页）窗口不许有转场动画：独立 Activity 上做 alpha/scale 会透出桌面壁纸和黑边",
        0,
        Shadows.shadowOf(a).getPendingTransitionExitAnimationResourceId()
    );
  }

  @Test public void openAnim_isAPureSlide_noAlphaNoScale() {
    String anim = source("app/src/main/res/anim/dfwx_page_open_in.xml");
    assertTrue("必须是位移滑入", anim.contains("<translate"));
    assertFalse("进场页自己不许淡入（站内推入时新页 alpha 恒为 1）", anim.contains("<alpha"));
    assertFalse("进场页不许缩放", anim.contains("<scale"));
    assertTrue("方向必须是从右侧进来", anim.contains("fromXDelta=\"28%p\""));
  }

  // ── 退场：不做动画 ────────────────────────────────────────────────────

  @Test public void backArrow_closesWithoutAnyTransition() {
    SupportActivity a = activity();
    a.closePage();
    org.robolectric.Shadows.shadowOf(Looper.getMainLooper()).idle();
    assertTrue("返回箭头必须能退出", a.isFinishing());
    assertEquals(
        "用户明确要求退场不加动画：不许再设进场转场",
        0,
        Shadows.shadowOf(a).getPendingTransitionEnterAnimationResourceId()
    );
    assertEquals(
        "退场也不许给下层页设动画",
        0,
        Shadows.shadowOf(a).getPendingTransitionExitAnimationResourceId()
    );
  }

  @Test public void skipLink_closesWithoutAnyTransition() {
    SupportActivity a = activity();
    a.renderSupportPage();
    android.widget.TextView skip = null;
    java.util.ArrayDeque<android.view.View> queue = new java.util.ArrayDeque<>();
    queue.add(a.root);
    while (!queue.isEmpty()) {
      android.view.View v = queue.poll();
      if (v instanceof android.widget.TextView
          && "暂时不支持，继续使用".contentEquals(((android.widget.TextView) v).getText())) {
        skip = (android.widget.TextView) v;
        break;
      }
      if (v instanceof android.view.ViewGroup) {
        for (int i = 0; i < ((android.view.ViewGroup) v).getChildCount(); i++) {
          queue.add(((android.view.ViewGroup) v).getChildAt(i));
        }
      }
    }
    assertTrue("降级出口必须还在", skip != null);
    skip.performClick();
    org.robolectric.Shadows.shadowOf(Looper.getMainLooper()).idle();
    assertTrue("点了「暂时不支持」必须能退出去", a.isFinishing());
    assertEquals(0, Shadows.shadowOf(a).getPendingTransitionEnterAnimationResourceId());
  }

  @Test public void systemBack_closesWithoutAnyTransition() {
    SupportActivity a = activity();
    a.onBackPressed();
    org.robolectric.Shadows.shadowOf(Looper.getMainLooper()).idle();
    assertTrue("系统返回必须能退出", a.isFinishing());
    assertEquals(0, Shadows.shadowOf(a).getPendingTransitionEnterAnimationResourceId());
  }

  // ── 资源卫生 ──────────────────────────────────────────────────────────

  @Test public void noOrphanTransitionAnimationsLeftBehind() {
    // 上一版为"退场 + 调用方窗口"建了 3 个动画，现在都用不到了；留着会让人以为还有转场。
    for (String gone : new String[]{"dfwx_page_open_out", "dfwx_page_close_in", "dfwx_page_close_out"}) {
      File f = new File(System.getProperty("user.dir"), "app/src/main/res/anim/" + gone + ".xml");
      File root = new File(System.getProperty("user.dir")).getAbsoluteFile();
      while (!new File(root, "app/src/main/AndroidManifest.xml").isFile() && root.getParentFile() != null) {
        root = root.getParentFile();
      }
      f = new File(root, "app/src/main/res/anim/" + gone + ".xml");
      assertFalse("已经不再使用的转场动画应当删掉，否则会误导后来人：" + gone, f.isFile());
    }
  }

  @Test public void transitionsAreRegisteredInOnCreate() {
    String src = source("app/src/main/java/cc/nkbr/lanzouplus/SupportActivity.java");
    assertTrue("onCreate 必须装转场", src.contains("installPageTransitions();"));
    assertTrue("API 34+ 必须走 overrideActivityTransition（overridePendingTransition 在 onCreate 会被系统丢弃）",
        src.contains("overrideActivityTransition(OVERRIDE_TRANSITION_OPEN"));
    assertTrue("低版本保留 overridePendingTransition", src.contains("overridePendingTransition(R.anim.dfwx_page_open_in,0)"));
    assertTrue("退场必须显式清掉转场（overridePendingTransition(0,0)）", src.contains("overridePendingTransition(0,0)"));
    assertFalse("退场不许再设任何转场", src.contains("overridePendingTransition(R.anim.dfwx_page_close"));
    assertNotEquals("占位：Build 常量必须可解析", 0, Build.VERSION.SDK_INT + 1);
  }
}
