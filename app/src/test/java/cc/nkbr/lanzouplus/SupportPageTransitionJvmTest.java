package cc.nkbr.lanzouplus;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowActivity;

/**
 * [DFW-74] 诚信付费页（独立 Activity）**进出场动画与站内子页统一**的守卫。
 *
 * 用户 2026-10-01：
 * > "诚信付费的那个预返回动画与其他的不一样，就是崩溃日志或者是其他的那种返回效果并不一样。
 * >  能不能给它们统一为一个非常好的效果？"
 *
 * 站内子页的转场语义（MainActivity.animatePage，不许改）：
 * 推入 300ms —— 新页从右侧 28% 屏宽滑入，旧页原地缩到 94% 并压暗到 55%；
 * 返回 240ms —— 上层页向右滑走并淡出，下层页不透明不动。
 * 本用例把"付费页必须引用同一套动画资源"钉死，四条退场路径（返回箭头 / 降级链接 / 系统返回 / finish）都算。
 *
 * 另外钉住一条**真机可用性**：`overridePendingTransition` 在 onCreate 里会被 AMS 丢弃
 * （AOSP ActivityClientController 有 RESUMED/PAUSING 状态门禁），所以 API 34+ 必须同时设
 * `overrideActivityTransition` —— 那才是"被启动页自己定进场动画"的可靠路径。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w411dp-h891dp-420dpi")
public class SupportPageTransitionJvmTest {

  @BeforeClass public static void silenceAutoImport() { MainActivity.LIBRARY_AUTO_IMPORT = false; }

  private static SupportActivity page() {
    SupportActivity a = Robolectric.buildActivity(SupportActivity.class).setup().get();
    Shadows.shadowOf(Looper.getMainLooper()).idle();
    return a;
  }

  private static View find(View view, String contentDescription) {
    if (contentDescription.contentEquals(view.getContentDescription() == null ? "" : view.getContentDescription())) {
      return view;
    }
    if (view instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) view;
      for (int i = 0; i < group.getChildCount(); i++) {
        View hit = find(group.getChildAt(i), contentDescription);
        if (hit != null) return hit;
      }
    }
    return null;
  }

  private static View findText(View view, String text) {
    if (view instanceof TextView && text.contentEquals(((TextView) view).getText())) return view;
    if (view instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) view;
      for (int i = 0; i < group.getChildCount(); i++) {
        View hit = findText(group.getChildAt(i), text);
        if (hit != null) return hit;
      }
    }
    return null;
  }

  // ── 资源本身 ────────────────────────────────────────────────────────────

  @Test public void allFourTransitionResourcesExist() {
    int[] ids = {R.anim.dfwx_page_open_in, R.anim.dfwx_page_open_out,
        R.anim.dfwx_page_close_in, R.anim.dfwx_page_close_out};
    for (int id : ids) assertTrue("四个转场资源都必须真实存在且非 0", id != 0);
  }

  // ── 打开：与站内"推入"同一套 ────────────────────────────────────────────

  @Test public void open_setsTheSamePushTransitionAsInAppSubPages() {
    ShadowActivity shadow = Shadows.shadowOf(page());
    assertEquals("进场必须是站内推入那条（新页右滑入）",
        R.anim.dfwx_page_open_in, shadow.getPendingTransitionEnterAnimationResourceId());
    assertEquals("旧页（设置页）必须被压暗退回",
        R.anim.dfwx_page_open_out, shadow.getPendingTransitionExitAnimationResourceId());
  }

  @Test public void open_alsoSetsTheApi34OverrideThatActuallyWorksOnDevice() {
    ShadowActivity shadow = Shadows.shadowOf(page());
    ShadowActivity.OverriddenActivityTransition open =
        shadow.getOverriddenActivityTransition(Activity.OVERRIDE_TRANSITION_OPEN);
    assertNotNull("API 34+ 必须设 overrideActivityTransition：overridePendingTransition 在 onCreate 里"
        + "会被 AMS 的状态门禁（RESUMED/PAUSING）丢弃，只靠它进场动画在真机上不会出现", open);
    assertEquals(R.anim.dfwx_page_open_in, open.enterAnim);
    assertEquals(R.anim.dfwx_page_open_out, open.exitAnim);

    ShadowActivity.OverriddenActivityTransition close =
        shadow.getOverriddenActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE);
    assertNotNull("关闭转场也要在同一处设好（系统返回等所有关闭路径共用）", close);
    assertEquals(R.anim.dfwx_page_close_in, close.enterAnim);
    assertEquals(R.anim.dfwx_page_close_out, close.exitAnim);
  }

  // ── 返回：三条用户可走的退场路径，必须都是同一套 ────────────────────────

  @Test public void backArrow_usesTheUnifiedCloseTransition() {
    SupportActivity a = page();
    View back = find(a.root, "返回");
    assertNotNull("页头左上角返回箭头必须在", back);
    back.performClick();
    assertCloseTransition(a);
  }

  @Test public void skipLink_usesTheUnifiedCloseTransition() {
    SupportActivity a = page();
    View skip = findText(a.root, "暂时不支持，继续使用");
    assertNotNull("不付费也能走的降级出口必须在", skip);
    skip.performClick();
    assertCloseTransition(a);
  }

  @Test public void systemBack_usesTheUnifiedCloseTransition() {
    SupportActivity a = page();
    a.onBackPressed();
    assertCloseTransition(a);
  }

  private static void assertCloseTransition(SupportActivity a) {
    assertTrue("必须真的退出了这一页", a.isFinishing());
    ShadowActivity shadow = Shadows.shadowOf(a);
    assertEquals("退场：下层页（设置页）原样露出、不透明不动",
        R.anim.dfwx_page_close_in, shadow.getPendingTransitionEnterAnimationResourceId());
    assertEquals("退场：上层页（付费页）向右滑走并淡出",
        R.anim.dfwx_page_close_out, shadow.getPendingTransitionExitAnimationResourceId());
  }
}
