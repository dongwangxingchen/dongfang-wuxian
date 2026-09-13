package cc.nkbr.lanzouplus;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import app.cash.paparazzi.Paparazzi;
import org.junit.Rule;
import org.junit.Test;

/** Paparazzi 管线自检：先验证纯代码 View → PNG 渲染通路可用（v1.2.2 引入）。
 *  v1.4.0 追加 apple 主题整页冒烟：用 ThemeEngine 真实 token 渲染一个分组卡片段落，
 *  验证浅色分组体系（灰底白卡 + 1px 描边）在渲染管线上成立。 */
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

  @Test public void appleGroupedSectionRenders() {
    ThemeEngine.Design d = ThemeEngine.byId("apple");
    android.content.Context ctx = paparazzi.getContext();
    float density = ctx.getResources().getDisplayMetrics().density;
    LinearLayout root = new LinearLayout(ctx);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setPadding(Math.round(16 * density), Math.round(12 * density), Math.round(16 * density), Math.round(12 * density));
    root.setBackgroundColor(d.bg);
    // 模拟 iOS inset grouped 区块：白卡 12dp 圆角 + 内行 44dp + 分隔线从文字起点画（此处简化为整宽 1px）
    LinearLayout card = new LinearLayout(ctx);
    card.setOrientation(LinearLayout.VERTICAL);
    GradientDrawable cardBg = new GradientDrawable();
    cardBg.setColor(d.surface);
    cardBg.setCornerRadius(12 * density);
    cardBg.setStroke(Math.max(1, Math.round(density)), d.border);
    card.setBackground(cardBg);
    String[] rows = {"支持开发", "下载时请求自愿支持", "外观"};
    for (int i = 0; i < rows.length; i++) {
      TextView row = new TextView(ctx);
      row.setText(rows[i]);
      row.setTextColor(d.text);
      row.setTextSize(16);
      row.setPadding(Math.round(16 * density), 0, Math.round(16 * density), 0);
      row.setGravity(Gravity.CENTER_VERTICAL);
      row.setMinimumHeight(Math.round(44 * density));
      card.addView(row, new LinearLayout.LayoutParams(-1, Math.round(44 * density)));
      if (i < rows.length - 1) {
        android.view.View divider = new android.view.View(ctx);
        divider.setBackgroundColor(d.border);
        android.view.View inset = new android.view.View(ctx);
        inset.setBackgroundColor(d.surface);
        LinearLayout insetWrap = new LinearLayout(ctx);
        insetWrap.setOrientation(LinearLayout.HORIZONTAL);
        insetWrap.setBackgroundColor(d.border);
        android.view.View line = new android.view.View(ctx);
        LinearLayout lineWrap = new LinearLayout(ctx);
        lineWrap.setOrientation(LinearLayout.VERTICAL);
        line.setBackgroundColor(d.border);
        android.widget.LinearLayout.LayoutParams lineLp = new LinearLayout.LayoutParams(-1, Math.max(1, Math.round(density)));
        lineLp.setMargins(Math.round(16 * density), 0, 0, 0);
        card.addView(line, lineLp);
      }
    }
    root.addView(card, new LinearLayout.LayoutParams(-1, -2));
    paparazzi.snapshot(root, "apple-grouped-section");
  }
}
