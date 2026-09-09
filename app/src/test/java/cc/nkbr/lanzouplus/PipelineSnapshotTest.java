package cc.nkbr.lanzouplus;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import app.cash.paparazzi.Paparazzi;
import org.junit.Rule;
import org.junit.Test;

/** Paparazzi 管线自检：先验证纯代码 View → PNG 渲染通路可用（v1.2.2 引入）。 */
public class PipelineSnapshotTest {
  @Rule public final Paparazzi paparazzi = new Paparazzi();

  @Test public void programmaticViewRenders() {
    LinearLayout box = new LinearLayout(paparazzi.getContext());
    box.setOrientation(LinearLayout.VERTICAL);
    box.setGravity(Gravity.CENTER);
    box.setBackgroundColor(Color.rgb(11, 10, 18));
    TextView brand = new TextView(paparazzi.getContext());
    brand.setText("东方无限 · Paparazzi 自检");
    brand.setTextColor(Color.rgb(167, 139, 250));
    brand.setTextSize(18);
    brand.setGravity(Gravity.CENTER);
    brand.setPadding(48, 48, 48, 48);
    GradientDrawable bg = new GradientDrawable();
    bg.setColor(Color.rgb(22, 20, 31));
    bg.setCornerRadius(36);
    box.setBackground(bg);
    box.addView(brand, new LinearLayout.LayoutParams(-2, -2));
    paparazzi.snapshot(box);
  }
}
