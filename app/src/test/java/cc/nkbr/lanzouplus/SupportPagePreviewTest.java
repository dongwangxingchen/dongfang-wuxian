package cc.nkbr.lanzouplus;

import android.view.View;
import android.view.Window;
import android.widget.LinearLayout;
import app.cash.paparazzi.Paparazzi;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.List;

/** 支持开发页（SupportActivity）双主题截图自检（v1.3.2）：支持页 + 已支持感谢页各渲染一张 PNG。
 *  JVM Paparazzi 环境无法完整 attach Activity（onCreate/setContentView 依赖真实窗口），
 *  因此像 PipelineSnapshotTest 一样直接构建内容视图：复用 SupportActivity 的渲染方法填充
 *  挂到 paparazzi Context 上的 root，保证截图与真机页面同源。 */
@RunWith(Parameterized.class)
public class SupportPagePreviewTest {
  @Rule public final Paparazzi paparazzi = new Paparazzi();

  private final String themeId;
  private final String pageKind;

  public SupportPagePreviewTest(String themeId, String pageKind) {this.themeId = themeId;this.pageKind = pageKind;}

  @Parameterized.Parameters(name = "{0}-{1}")
  public static List<String[]> cases() {
    List<String[]> cases = new ArrayList<>();
    for (String theme : new String[]{"nova", "legacy"}) {
      cases.add(new String[]{theme, "support"});
      cases.add(new String[]{theme, "thanks"});
    }
    return cases;
  }

  @Test public void renderSupportPage() throws Exception {
    SupportActivity activity = new SupportActivity() {
      @Override public Window getWindow() {return new SupportPreviewWindow(paparazzi.getContext());}
    };
    // attach 到 Paparazzi 的渲染 Context：attachBaseContext 是 protected，统一走反射
    java.lang.reflect.Method attach = android.content.ContextWrapper.class.getDeclaredMethod("attachBaseContext", android.content.Context.class);
    attach.setAccessible(true);
    attach.invoke(activity, paparazzi.getContext());
    ThemeEngine.Design d = ThemeEngine.byId(themeId);
    activity.density = paparazzi.getContext().getResources().getDisplayMetrics().density;
    activity.BG = d.bg;activity.SURFACE = d.surface;activity.SURFACE2 = d.surface2;activity.BORDER = d.border;
    activity.PRIMARY = d.primary;activity.PRIMARY_HI = d.primaryHi;activity.PRIMARY_LO = d.primaryLo;
    activity.TEXT = d.text;activity.MUTED = d.muted;
    activity.root = new LinearLayout(paparazzi.getContext());
    activity.root.setOrientation(LinearLayout.VERTICAL);
    activity.root.setBackgroundColor(d.bg);
    if ("support".equals(pageKind)) activity.renderSupportPage(); else activity.renderThankYou();
    paparazzi.snapshot(activity.root, "support-" + pageKind + "-" + themeId);
  }

  /** 渲染专用的窗口桩：Paparazzi 无真实 Window；SupportActivity.applyPalette 只碰状态栏/导航栏颜色两个抽象方法，
   *  其余 Window 抽象方法给最小空实现保证可实例化。 */
  @SuppressWarnings("deprecation")
  static final class SupportPreviewWindow extends Window {
    SupportPreviewWindow(android.content.Context context) {super(context);}
    @Override public void setStatusBarColor(int color) {}
    @Override public int getStatusBarColor() {return 0;}
    @Override public void setNavigationBarColor(int color) {}
    @Override public int getNavigationBarColor() {return 0;}
    @Override public void addContentView(View v, android.view.ViewGroup.LayoutParams p) {}
    @Override public void closeAllPanels() {}
    @Override public void closePanel(int id) {}
    @Override public View getCurrentFocus() {return null;}
    @Override public View getDecorView() {return null;}
    @Override public android.view.LayoutInflater getLayoutInflater() {return android.view.LayoutInflater.from(getContext());}
    @Override public int getVolumeControlStream() {return 0;}
    @Override public void invalidatePanelMenu(int id) {}
    @Override public boolean isFloating() {return false;}
    @Override public boolean isShortcutKey(int keyCode, android.view.KeyEvent event) {return false;}
    @Override protected void onActive() {}
    @Override public void onConfigurationChanged(android.content.res.Configuration cfg) {}
    @Override public void openPanel(int id, android.view.KeyEvent event) {}
    @Override public View peekDecorView() {return null;}
    @Override public boolean performContextMenuIdentifierAction(int id, int flag) {return false;}
    @Override public boolean performPanelIdentifierAction(int panelId, int identifier, int flags) {return false;}
    @Override public boolean performPanelShortcut(int panelId, int keyCode, android.view.KeyEvent event, int flags) {return false;}
    @Override public void restoreHierarchyState(android.os.Bundle state) {}
    @Override public android.os.Bundle saveHierarchyState() {return null;}
    @Override public void setBackgroundDrawable(android.graphics.drawable.Drawable d) {}
    @Override public void setChildDrawable(int featureId, android.graphics.drawable.Drawable d) {}
    @Override public void setChildInt(int featureId, int value) {}
    @Override public void setContentView(View v) {}
    @Override public void setContentView(View v, android.view.ViewGroup.LayoutParams p) {}
    @Override public void setContentView(int layoutRes) {}
    @Override public void setDecorCaptionShade(int shade) {}
    @Override public void setFeatureDrawable(int featureId, android.graphics.drawable.Drawable d) {}
    @Override public void setFeatureDrawableAlpha(int featureId, int alpha) {}
    @Override public void setFeatureDrawableResource(int featureId, int resId) {}
    @Override public void setFeatureDrawableUri(int featureId, android.net.Uri uri) {}
    @Override public void setFeatureInt(int featureId, int value) {}
    @Override public void setResizingCaptionDrawable(android.graphics.drawable.Drawable d) {}
    @Override public void setTitle(CharSequence title) {}
    @Override public void setTitleColor(int color) {}
    @Override public void setVolumeControlStream(int stream) {}
    @Override public boolean superDispatchGenericMotionEvent(android.view.MotionEvent event) {return false;}
    @Override public boolean superDispatchKeyEvent(android.view.KeyEvent event) {return false;}
    @Override public boolean superDispatchKeyShortcutEvent(android.view.KeyEvent event) {return false;}
    @Override public boolean superDispatchTouchEvent(android.view.MotionEvent event) {return false;}
    @Override public boolean superDispatchTrackballEvent(android.view.MotionEvent event) {return false;}
    @Override public void takeInputQueue(android.view.InputQueue.Callback callback) {}
    @Override public void takeKeyEvents(boolean enable) {}
    @Override public void takeSurface(android.view.SurfaceHolder.Callback2 callback) {}
    @Override public void togglePanel(int id, android.view.KeyEvent event) {}
  }
}
