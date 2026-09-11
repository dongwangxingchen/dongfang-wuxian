package cc.nkbr.lanzouplus;

import android.content.Context;
import android.widget.LinearLayout;
import app.cash.paparazzi.Paparazzi;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.List;

/** 「诚信付费」顶部按钮截图自检（v1.4.2）：三主题各渲染一张，未付费/已付费双状态同框，
 *  供人工/视觉复查双状态差异与主题适配（动画关 → 扫光停在第 0 帧，alpha=0 无残影）。 */
@RunWith(Parameterized.class)
public class IntegrityPayButtonPreviewTest {
  @Rule public final Paparazzi paparazzi = new Paparazzi();

  private final String themeId;

  public IntegrityPayButtonPreviewTest(String themeId) {this.themeId = themeId;}

  @Parameterized.Parameters(name = "{0}")
  public static List<String[]> themes() {
    List<String[]> cases = new ArrayList<>();
    cases.add(new String[]{"nova"});
    cases.add(new String[]{"legacy"});
    cases.add(new String[]{"apple"});
    return cases;
  }

  @Test public void renderPayStates() {
    ThemeEngine.Design design = ThemeEngine.byId(themeId);
    Context ctx = paparazzi.getContext();
    float density = ctx.getResources().getDisplayMetrics().density;
    int pad = Math.round(14 * density);
    LinearLayout root = new LinearLayout(ctx);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setBackgroundColor(design.bg);
    root.setPadding(pad, pad, pad, pad);
    IntegrityPayButton unpaid = new IntegrityPayButton(ctx, design, density, false, false, () -> {});
    root.addView(unpaid, new LinearLayout.LayoutParams(-1, -2));
    IntegrityPayButton paid = new IntegrityPayButton(ctx, design, density, true, false, () -> {});
    LinearLayout.LayoutParams paidLp = new LinearLayout.LayoutParams(-1, -2);
    paidLp.topMargin = Math.round(12 * density);
    root.addView(paid, paidLp);
    paparazzi.snapshot(root, "paybtn-" + themeId);
  }
}
