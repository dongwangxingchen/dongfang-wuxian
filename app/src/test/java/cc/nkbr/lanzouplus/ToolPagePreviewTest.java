package cc.nkbr.lanzouplus;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.Display;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import app.cash.paparazzi.Paparazzi;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.List;

/** 全工具页截图自检：每个工具渲染一张 PNG，供人工/视觉复查布局与美观（v1.2.3）。 */
@RunWith(Parameterized.class)
public class ToolPagePreviewTest {
  @Rule public final Paparazzi paparazzi = new Paparazzi();

  private final String toolId;

  public ToolPagePreviewTest(String toolId) {this.toolId = toolId;}

  @Parameterized.Parameters(name = "{0}")
  public static List<String> tools() {
    List<String> ids = new ArrayList<>();
    ids.add("__list__");
    for (String id : Toolbox.allToolIds()) ids.add(id);
    return ids;
  }

  @Test public void renderToolPage() {
    PreviewHost host = new PreviewHost(paparazzi.getContext());
    host.newRoot();
    ToolHost toolHost = new ToolHost(host);
    if ("__list__".equals(toolId)) toolHost.renderList(); else toolHost.renderTool(toolId);
    paparazzi.snapshot(host.rootView, toolId);
  }

  static final class PreviewHost implements ToolHost.Host {
    final Context ctx;
    final float density;
    LinearLayout rootView;
    final Handler ui = new Handler(Looper.getMainLooper());

    PreviewHost(Context ctx) {this.ctx = ctx;density = ctx.getResources().getDisplayMetrics().density;}

    void newRoot() {rootView = new LinearLayout(ctx);rootView.setOrientation(LinearLayout.VERTICAL);rootView.setBackgroundColor(Color.rgb(11, 10, 18));}

    @Override public int dp(int v) {return Math.round(v * density);}
    @Override public int BG() {return Color.rgb(11, 10, 18);}
    @Override public int TEXT() {return Color.rgb(242, 240, 247);}
    @Override public int MUTED() {return Color.rgb(154, 147, 171);}
    @Override public int SURFACE() {return Color.rgb(22, 20, 31);}
    @Override public int PRIMARY() {return Color.rgb(167, 139, 250);}
    @Override public int DIV() {return Color.rgb(38, 35, 50);}
    @Override public boolean motionEnabled() {return false;}
    @Override public String toolBytes(long value) {return value < 1024 ? value + " B" : (value / 1048576) + " MB";}
    @Override public LinearLayout root() {return rootView;}
    @Override public void showNotice(String message, boolean longLived) {}
    @Override public void openTool(String id) {}
    @Override public void popToolBack() {}
    @Override public void startScreenTest() {}
    @Override public boolean startTorch() {return false;}
    @Override public void stopTorch() {}
    @Override public void startBrownNoise() {}
    @Override public void stopBrownNoise() {}
    @Override public void speakTts(String value) {}
    @Override public void stopTts() {}
    @Override public void toolHostSketch(LinearLayout body) {body.addView(new TextView(ctx));}
    @Override public void toolHostRuler(LinearLayout body) {body.addView(new TextView(ctx));}
    @Override public void toolHostLevel(LinearLayout body) {body.addView(new TextView(ctx));}
    @Override public void pickToolImage() {}
    @Override public void runImageCompressPending() {}
    @Override public Runnable levelCleanup() {return null;}
    @Override public void setLevelCleanup(Runnable value) {}
    @Override public int pageDirection() {return 0;}
    @Override public void setPageDirection(int value) {}
    @Override public int toolQuality() {return 70;}
    @Override public void setToolQuality(int value) {}
    @Override public Uri toolImageUri() {return null;}
    @Override public void setToolImageUri(Uri value) {}
    @Override public String toolImageInfoText() {return "";}
    @Override public void setToolImageInfoText(String value) {}
    @Override public ImageButton iconButton(int icon, String description) {ImageButton b = new ImageButton(ctx);b.setImageResource(icon);b.setContentDescription(description);return b;}
    @Override public android.graphics.drawable.GradientDrawable solidShape(int color, int radius) {android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    @Override public Drawable filterRipple(Drawable content) {return content;}
    @Override public void animateSection(LinearLayout section, LinearLayout content, ImageView arrow, boolean open) {}
    @Override public TextView primaryHeader(String title) {TextView t = new TextView(ctx);t.setText(title);t.setTextColor(TEXT());t.setPadding(dp(8), dp(8), dp(8), dp(8));rootView.addView(t);return t;}
    @Override public Object getSystemService(String name) {return ctx.getSystemService(name);}
    @Override public Resources getResources() {return ctx.getResources();}
    @Override public WindowManager getWindowManager() {return (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);}
    @Override public Display getDisplay() {return null;}
    @Override public ApplicationInfo getApplicationInfo() {return ctx.getApplicationInfo();}
    @Override public PackageManager getPackageManager() {return ctx.getPackageManager();}
    @Override public String getPackageName() {return ctx.getPackageName();}
    @Override public Intent registerReceiver(android.content.BroadcastReceiver receiver, android.content.IntentFilter filter) {return ctx.registerReceiver(receiver, filter);}
    @Override public Context context() {return ctx;}
  }
}
