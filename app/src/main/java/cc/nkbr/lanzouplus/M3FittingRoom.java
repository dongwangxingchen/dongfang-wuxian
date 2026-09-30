package cc.nkbr.lanzouplus;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * [DFWX] DFW-67：**风格试衣间**——把 M3 token 应用到代表性组件上，先看效果再决定要不要全量重置。
 *
 * ## 为什么先做这个
 * 用户 2026-09-30 要求按 Material Design 3 做全站重置。但主机区是 **2700+ 行程序化 View 代码**，
 * 全量重置意味着**每一屏都会动**，改错了代价很大。所以先做一页可对照的预览：
 * 喜欢再全量推，不喜欢只废掉这一页。
 * （这与项目规范 §7 原本就有的"图标试衣间"是同一思路。）
 *
 * ## 这一页展示什么
 * 1. **颜色角色**——M3 的 30+ 个角色色，配对的 on-color 直接印在色块上（能立刻看出对比度够不够）
 * 2. **形状阶**——官方 9 档，标注 dp
 * 3. **字阶**——官方 15 个 type role，标注 size/lineHeight/weight
 * 4. **组件**——按钮 5 种 / 卡片 3 种 / 列表项 / 芯片 / 开关 / FAB / 底部导航 / 对话框
 * 5. **对照**——同一个东西"现在的样子 vs M3 的样子"并排，这是最关键的一节
 *
 * ## 实现约束
 * 纯 Java 手写 View，**零新增依赖**（与项目铁律一致）。所有圆角用**精确 dp**，
 * 不能走 `MainActivity.solidShape()`——那个方法把圆角压成 26/20/8 三档，
 * **表达不了 M3 的 9 档形状阶**（这本身就是主机区与 M3 的差距之一）。
 */
final class M3FittingRoom {

  private final MainActivity host;

  M3FittingRoom(MainActivity host) { this.host = host; }

  // ── 基础绘制helper ────────────────────────────────────────────────────────

  private int dp(int value) { return host.dp(value); }

  /** 精确圆角矩形。**不用** host.solidShape()——它会把圆角压成三档。 */
  private GradientDrawable roundRect(int color, int radiusDp) {
    GradientDrawable g = new GradientDrawable();
    g.setColor(color);
    g.setCornerRadius(dp(radiusDp));
    return g;
  }

  /** 全圆（pill）：半径取高度一半。 */
  private GradientDrawable pill(int color) { return roundRect(color, 999); }

  private GradientDrawable stroked(int fill, int strokeColor, int radiusDp) {
    GradientDrawable g = roundRect(fill, radiusDp);
    g.setStroke(dp(1), strokeColor);
    return g;
  }

  private TextView m3Text(String s, M3Tokens.TypeRole role, int color) {
    TextView v = host.text(s, (int) role.sizeSp, color, role.weight);
    v.setTextSize(role.sizeSp);
    // 行高按 role 的 lineHeight/size 比例设，尽量贴近官方规格。
    v.setLineSpacing(dp(1), role.lineHeightSp / role.sizeSp);
    return v;
  }

  private TextView label(String s, int color) {
    return m3Text(s, M3Tokens.LABEL_MEDIUM, color);
  }

  // ── 页面骨架 ──────────────────────────────────────────────────────────────

  View build() {
    LinearLayout col = new LinearLayout(host);
    col.setOrientation(LinearLayout.VERTICAL);
    col.setBackgroundColor(M3Tokens.BACKGROUND);
    col.setPadding(dp(16), dp(8), dp(16), dp(32));

    col.addView(sectionTitle("Material Design 3 · 风格试衣间", "这一页只是预览，尚未接管全站"));
    col.addView(comparisonSection());
    col.addView(colorRolesSection());
    col.addView(shapeScaleSection());
    col.addView(typeScaleSection());
    col.addView(buttonsSection());
    col.addView(cardsSection());
    col.addView(listSection());
    col.addView(chipsAndControlsSection());
    col.addView(navSection());

    ScrollView scroll = new ScrollView(host);
    scroll.setFillViewport(true);
    scroll.addView(col, new ScrollView.LayoutParams(-1, -2));
    return scroll;
  }

  private View sectionTitle(String title, String subtitle) {
    LinearLayout box = new LinearLayout(host);
    box.setOrientation(LinearLayout.VERTICAL);
    box.setPadding(0, dp(20), 0, dp(10));
    box.addView(m3Text(title, M3Tokens.HEADLINE_SMALL, M3Tokens.ON_BACKGROUND));
    if (subtitle != null && !subtitle.isEmpty()) {
      TextView sub = m3Text(subtitle, M3Tokens.BODY_MEDIUM, M3Tokens.ON_SURFACE_VARIANT);
      LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
      p.topMargin = dp(4);
      box.addView(sub, p);
    }
    return box;
  }

  private TextView groupLabel(String s) {
    TextView v = label(s, M3Tokens.PRIMARY);
    v.setPadding(0, dp(14), 0, dp(6));
    return v;
  }

  // ── 1. 对照：现在 vs M3 ───────────────────────────────────────────────────

  /**
   * 最关键的一节：把同一个组件用"现在的做法"和"M3 的做法"并排，
   * 一眼看出差在哪。先看这个，再看后面的规格表。
   */
  private View comparisonSection() {
    LinearLayout box = new LinearLayout(host);
    box.setOrientation(LinearLayout.VERTICAL);
    box.addView(sectionTitle("① 对照：现在 vs M3", "同一件东西，两种做法。这是最该先看的一节"));

    // —— 主按钮 ——
    box.addView(groupLabel("主按钮"));
    LinearLayout row = new LinearLayout(host);
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.addView(compareCell("现在", currentButton("立即更新"), "圆角 14 / 高 50 / 15sp"),
        new LinearLayout.LayoutParams(0, -2, 1f));
    LinearLayout.LayoutParams gap = new LinearLayout.LayoutParams(dp(10), 1);
    row.addView(new View(host), gap);
    row.addView(compareCell("M3", m3Button("立即更新", M3ButtonStyle.FILLED), "全圆 / 高 40 / 14sp·Medium"),
        new LinearLayout.LayoutParams(0, -2, 1f));
    box.addView(row);

    // —— 卡片 ——
    box.addView(groupLabel("信息卡片"));
    LinearLayout row2 = new LinearLayout(host);
    row2.setOrientation(LinearLayout.HORIZONTAL);
    row2.addView(compareCell("现在", currentCard(), "圆角 20 / 面 #16141F"),
        new LinearLayout.LayoutParams(0, -2, 1f));
    row2.addView(new View(host), new LinearLayout.LayoutParams(dp(10), 1));
    row2.addView(compareCell("M3", m3Card(), "圆角 12 / 面 surfaceContainerHigh"),
        new LinearLayout.LayoutParams(0, -2, 1f));
    box.addView(row2);

    // —— 列表项 ——
    box.addView(groupLabel("列表项"));
    box.addView(compareCell("现在", currentListItem(), "标题 15sp·580 / 副标题 11sp"));
    box.addView(compareCell("M3", m3ListItem(), "标题 BodyLarge 16sp·400 / 副标题 BodyMedium 14sp"));

    return box;
  }

  private View compareCell(String tag, View content, String spec) {
    LinearLayout cell = new LinearLayout(host);
    cell.setOrientation(LinearLayout.VERTICAL);
    TextView t = label(tag, M3Tokens.ON_SURFACE_VARIANT);
    cell.addView(t);
    LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
    cp.topMargin = dp(6);
    cell.addView(content, cp);
    TextView s = label(spec, M3Tokens.ON_SURFACE_VARIANT);
    s.setAlpha(0.7f);
    LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2);
    sp.topMargin = dp(6);
    cell.addView(s, sp);
    return cell;
  }

  /** 现在的主按钮：圆角 14、高 50、15sp —— 与 DFW-66 里那个一致。 */
  private View currentButton(String text) {
    TextView b = host.text(text, 15, host.BG, 500);
    b.setGravity(Gravity.CENTER);
    b.setBackground(pill(host.PRIMARY));
    b.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(50)));
    return b;
  }

  /** 现在的卡片：圆角 20（项目 Card token）、面 SURFACE。 */
  private View currentCard() {
    LinearLayout card = new LinearLayout(host);
    card.setOrientation(LinearLayout.VERTICAL);
    card.setBackground(roundRect(host.SURFACE, 20));
    card.setPadding(dp(14), dp(12), dp(14), dp(12));
    card.addView(host.text("东方无限", 15, host.TEXT, 580));
    TextView sub = host.text("项目大小 36.6 MB", 11, host.MUTED);
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
    p.topMargin = dp(4);
    card.addView(sub, p);
    return card;
  }

  private View currentListItem() {
    LinearLayout item = new LinearLayout(host);
    item.setOrientation(LinearLayout.HORIZONTAL);
    item.setGravity(Gravity.CENTER_VERTICAL);
    item.setPadding(dp(16), dp(8), dp(16), dp(8));
    ImageView icon = new ImageView(host);
    icon.setImageResource(R.drawable.ic_folder);
    icon.setColorFilter(host.PRIMARY);
    item.addView(icon, new LinearLayout.LayoutParams(dp(24), dp(24)));
    LinearLayout texts = new LinearLayout(host);
    texts.setOrientation(LinearLayout.VERTICAL);
    LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, -2, 1);
    tp.leftMargin = dp(12);
    texts.addView(host.text("我的文件", 15, host.TEXT, 580));
    texts.addView(host.text("共 128 个项目", 11, host.MUTED));
    item.addView(texts, tp);
    return item;
  }

  // ── 2. 颜色角色 ───────────────────────────────────────────────────────────

  private View colorRolesSection() {
    LinearLayout box = new LinearLayout(host);
    box.setOrientation(LinearLayout.VERTICAL);
    box.addView(sectionTitle("② 颜色角色", "M3 的 30+ 个角色色。字色就是配对的那个 on-color——对比度不够会立刻看出来"));

    box.addView(groupLabel("主色系 Primary"));
    box.addView(swatchRow(new int[][]{
        {M3Tokens.PRIMARY, M3Tokens.ON_PRIMARY},
        {M3Tokens.PRIMARY_CONTAINER, M3Tokens.ON_PRIMARY_CONTAINER},
    }, new String[]{"primary", "primaryContainer"}));

    box.addView(groupLabel("次色 / 三色 Secondary · Tertiary"));
    box.addView(swatchRow(new int[][]{
        {M3Tokens.SECONDARY_CONTAINER, M3Tokens.ON_SECONDARY_CONTAINER},
        {M3Tokens.TERTIARY_CONTAINER, M3Tokens.ON_TERTIARY_CONTAINER},
    }, new String[]{"secondaryContainer", "tertiaryContainer"}));

    box.addView(groupLabel("错误 Error"));
    box.addView(swatchRow(new int[][]{
        {M3Tokens.ERROR_CONTAINER, M3Tokens.ON_ERROR_CONTAINER},
        {M3Tokens.ERROR, M3Tokens.ON_ERROR},
    }, new String[]{"errorContainer", "error"}));

    box.addView(groupLabel("表面阶梯 Surface Containers"));
    box.addView(swatchRow(new int[][]{
        {M3Tokens.SURFACE_CONTAINER_LOWEST, M3Tokens.ON_SURFACE},
        {M3Tokens.SURFACE_CONTAINER_LOW, M3Tokens.ON_SURFACE},
    }, new String[]{"ContainerLowest", "ContainerLow"}));
    box.addView(swatchRow(new int[][]{
        {M3Tokens.SURFACE_CONTAINER, M3Tokens.ON_SURFACE},
        {M3Tokens.SURFACE_CONTAINER_HIGH, M3Tokens.ON_SURFACE},
    }, new String[]{"Container", "ContainerHigh"}));
    box.addView(swatchRow(new int[][]{
        {M3Tokens.SURFACE_CONTAINER_HIGHEST, M3Tokens.ON_SURFACE},
        {M3Tokens.SURFACE_VARIANT, M3Tokens.ON_SURFACE_VARIANT},
    }, new String[]{"ContainerHighest", "surfaceVariant"}));

    box.addView(groupLabel("描边 Outline"));
    box.addView(swatchRow(new int[][]{
        {M3Tokens.OUTLINE, M3Tokens.ON_SURFACE},
        {M3Tokens.OUTLINE_VARIANT, M3Tokens.ON_SURFACE_VARIANT},
    }, new String[]{"outline", "outlineVariant"}));

    return box;
  }

  private View swatchRow(int[][] colors, String[] names) {
    LinearLayout row = new LinearLayout(host);
    row.setOrientation(LinearLayout.HORIZONTAL);
    for (int i = 0; i < colors.length; i++) {
      LinearLayout cell = new LinearLayout(host);
      cell.setOrientation(LinearLayout.VERTICAL);
      cell.setGravity(Gravity.CENTER);
      cell.setBackground(roundRect(colors[i][0], M3Tokens.SHAPE_MEDIUM));
      cell.setPadding(dp(10), dp(14), dp(10), dp(14));
      TextView name = m3Text(names[i], M3Tokens.LABEL_MEDIUM, colors[i][1]);
      name.setGravity(Gravity.CENTER);
      cell.addView(name);
      TextView hex = m3Text(String.format("#%06X", 0xFFFFFF & colors[i][0]), M3Tokens.LABEL_SMALL, colors[i][1]);
      hex.setAlpha(0.8f);
      hex.setGravity(Gravity.CENTER);
      cell.addView(hex);
      LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1f);
      if (i > 0) p.leftMargin = dp(10);
      row.addView(cell, p);
    }
    LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
    rp.topMargin = dp(6);
    row.setLayoutParams(rp);
    return row;
  }

  // ── 3. 形状阶 ─────────────────────────────────────────────────────────────

  private View shapeScaleSection() {
    LinearLayout box = new LinearLayout(host);
    box.setOrientation(LinearLayout.VERTICAL);
    box.addView(sectionTitle("③ 形状阶", "官方 9 档（从我们 APK 里那版 material3 反编译得到）"));

    int[] values = {0, 4, 8, 12, 16, 20, 28, 32, 48};
    String[] names = {"None", "ExtraSmall", "Small", "Medium", "Large", "LargeIncreased",
        "ExtraLarge", "ExtraLargeIncreased", "ExtraExtraLarge"};
    LinearLayout row = new LinearLayout(host);
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setGravity(Gravity.BOTTOM);
    for (int i = 0; i < values.length; i++) {
      LinearLayout cell = new LinearLayout(host);
      cell.setOrientation(LinearLayout.VERTICAL);
      cell.setGravity(Gravity.CENTER_HORIZONTAL);
      View block = new View(host);
      block.setBackground(stroked(M3Tokens.PRIMARY_CONTAINER, M3Tokens.OUTLINE, values[i]));
      cell.addView(block, new LinearLayout.LayoutParams(dp(30), dp(30)));
      TextView t = m3Text(String.valueOf(values[i]), M3Tokens.LABEL_SMALL, M3Tokens.ON_SURFACE_VARIANT);
      t.setGravity(Gravity.CENTER);
      cell.addView(t);
      LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1f);
      row.addView(cell, p);
    }
    box.addView(row);
    TextView note = m3Text("现在主机区只有 3 档（26/20/8），且全仓还有 12 个随手写的圆角值",
        M3Tokens.BODY_SMALL, M3Tokens.ON_SURFACE_VARIANT);
    note.setPadding(0, dp(10), 0, 0);
    box.addView(note);
    return box;
  }

  // ── 4. 字阶 ───────────────────────────────────────────────────────────────

  private View typeScaleSection() {
    LinearLayout box = new LinearLayout(host);
    box.setOrientation(LinearLayout.VERTICAL);
    box.addView(sectionTitle("④ 字阶", "官方 15 个 type role（现在主机区有 24 种随手写的字号）"));

    for (M3Tokens.TypeRole role : M3Tokens.TYPE_SCALE) {
      LinearLayout row = new LinearLayout(host);
      row.setOrientation(LinearLayout.HORIZONTAL);
      row.setGravity(Gravity.CENTER_VERTICAL);
      row.setPadding(0, dp(6), 0, dp(6));

      TextView sample = m3Text("东方无限 Aa", role, M3Tokens.ON_BACKGROUND);
      row.addView(sample, new LinearLayout.LayoutParams(0, -2, 1f));

      TextView spec = m3Text(role.name + " · " + (int) role.sizeSp + "/" + (int) role.lineHeightSp
          + " · " + role.weight, M3Tokens.LABEL_SMALL, M3Tokens.ON_SURFACE_VARIANT);
      spec.setGravity(Gravity.END);
      row.addView(spec, new LinearLayout.LayoutParams(-2, -2));
      box.addView(row);
    }
    return box;
  }

  // ── 5. 按钮 5 种 ──────────────────────────────────────────────────────────

  enum M3ButtonStyle { FILLED, TONAL, OUTLINED, TEXT, ELEVATED }

  /**
   * M3 按钮：高 40dp、全圆、LabelLarge(14sp/500)、左右内边距 24dp。
   * 五种样式的区别只在**颜色角色**与**有没有描边/阴影**。
   */
  private View m3Button(String text, M3ButtonStyle style) {
    TextView b = m3Text(text, M3Tokens.LABEL_LARGE, M3Tokens.ON_PRIMARY);
    b.setGravity(Gravity.CENTER);
    switch (style) {
      case FILLED:
        b.setTextColor(M3Tokens.ON_PRIMARY);
        b.setBackground(pill(M3Tokens.PRIMARY));
        break;
      case TONAL:
        b.setTextColor(M3Tokens.ON_SECONDARY_CONTAINER);
        b.setBackground(pill(M3Tokens.SECONDARY_CONTAINER));
        break;
      case OUTLINED:
        b.setTextColor(M3Tokens.PRIMARY);
        b.setBackground(stroked(Color.TRANSPARENT, M3Tokens.OUTLINE, 999));
        break;
      case TEXT:
        b.setTextColor(M3Tokens.PRIMARY);
        b.setBackground(pill(Color.TRANSPARENT));
        break;
      case ELEVATED:
      default:
        b.setTextColor(M3Tokens.PRIMARY);
        b.setBackground(pill(M3Tokens.SURFACE_CONTAINER_LOW));
        b.setElevation(dp(1));
        break;
    }
    b.setPadding(dp(M3Tokens.BUTTON_PADDING_H), 0, dp(M3Tokens.BUTTON_PADDING_H), 0);
    b.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(M3Tokens.BUTTON_HEIGHT)));
    host.applePressScale(b);
    return b;
  }

  private View buttonsSection() {
    LinearLayout box = new LinearLayout(host);
    box.setOrientation(LinearLayout.VERTICAL);
    box.addView(sectionTitle("⑤ 按钮（5 种）", "M3 的五种按钮，区别只在颜色角色与描边"));
    String[][] rows = {
        {"Filled", "主操作"}, {"Tonal", "次操作"}, {"Outlined", "中优先级"},
        {"Text", "最低优先级"}, {"Elevated", "需要浮起来时"},
    };
    M3ButtonStyle[] styles = {M3ButtonStyle.FILLED, M3ButtonStyle.TONAL, M3ButtonStyle.OUTLINED,
        M3ButtonStyle.TEXT, M3ButtonStyle.ELEVATED};
    for (int i = 0; i < styles.length; i++) {
      LinearLayout row = new LinearLayout(host);
      row.setOrientation(LinearLayout.HORIZONTAL);
      row.setGravity(Gravity.CENTER_VERTICAL);
      LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
      rp.topMargin = dp(8);
      View btn = m3Button(rows[i][0], styles[i]);
      btn.setLayoutParams(new LinearLayout.LayoutParams(dp(140), dp(M3Tokens.BUTTON_HEIGHT)));
      row.addView(btn);
      TextView note = m3Text(rows[i][1], M3Tokens.BODY_MEDIUM, M3Tokens.ON_SURFACE_VARIANT);
      LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(0, -2, 1f);
      np.leftMargin = dp(12);
      row.addView(note, np);
      box.addView(row, rp);
    }
    return box;
  }

  // ── 6. 卡片 3 种 ──────────────────────────────────────────────────────────

  private View m3Card() {
    LinearLayout card = new LinearLayout(host);
    card.setOrientation(LinearLayout.VERTICAL);
    card.setBackground(roundRect(M3Tokens.SURFACE_CONTAINER_HIGH, M3Tokens.SHAPE_CARD));
    card.setPadding(dp(M3Tokens.CARD_PADDING), dp(M3Tokens.CARD_PADDING),
        dp(M3Tokens.CARD_PADDING), dp(M3Tokens.CARD_PADDING));
    card.addView(m3Text("东方无限", M3Tokens.TITLE_MEDIUM, M3Tokens.ON_SURFACE));
    TextView sub = m3Text("项目大小 36.6 MB", M3Tokens.BODY_MEDIUM, M3Tokens.ON_SURFACE_VARIANT);
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
    p.topMargin = dp(4);
    card.addView(sub, p);
    return card;
  }

  private View cardsSection() {
    LinearLayout box = new LinearLayout(host);
    box.setOrientation(LinearLayout.VERTICAL);
    box.addView(sectionTitle("⑥ 卡片（3 种）", "M3 用 surface 容器层级 + 描边表达层级，不靠阴影"));
    box.addView(groupLabel("Filled（填充）"));
    box.addView(m3Card());
    box.addView(groupLabel("Outlined（描边）"));
    LinearLayout outlined = new LinearLayout(host);
    outlined.setOrientation(LinearLayout.VERTICAL);
    outlined.setBackground(stroked(Color.TRANSPARENT, M3Tokens.OUTLINE_VARIANT, M3Tokens.SHAPE_CARD));
    outlined.setPadding(dp(16), dp(16), dp(16), dp(16));
    outlined.addView(m3Text("东方无限", M3Tokens.TITLE_MEDIUM, M3Tokens.ON_SURFACE));
    outlined.addView(m3Text("项目大小 36.6 MB", M3Tokens.BODY_MEDIUM, M3Tokens.ON_SURFACE_VARIANT));
    box.addView(outlined);
    return box;
  }

  // ── 7. 列表项 ─────────────────────────────────────────────────────────────

  /** M3 列表项：高 56dp、左右 16dp、标题 BodyLarge、副标题 BodyMedium、前导图标 24dp。 */
  private View m3ListItem() {
    LinearLayout item = new LinearLayout(host);
    item.setOrientation(LinearLayout.HORIZONTAL);
    item.setGravity(Gravity.CENTER_VERTICAL);
    item.setPadding(dp(M3Tokens.LIST_ITEM_PADDING_H), 0, dp(M3Tokens.LIST_ITEM_PADDING_H), 0);
    ImageView icon = new ImageView(host);
    icon.setImageResource(R.drawable.ic_folder);
    icon.setColorFilter(M3Tokens.ON_SURFACE_VARIANT);
    item.addView(icon, new LinearLayout.LayoutParams(dp(24), dp(24)));
    LinearLayout texts = new LinearLayout(host);
    texts.setOrientation(LinearLayout.VERTICAL);
    LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, -2, 1);
    tp.leftMargin = dp(16);
    texts.addView(m3Text("我的文件", M3Tokens.BODY_LARGE, M3Tokens.ON_SURFACE));
    texts.addView(m3Text("共 128 个项目", M3Tokens.BODY_MEDIUM, M3Tokens.ON_SURFACE_VARIANT));
    item.addView(texts, tp);
    item.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(M3Tokens.LIST_ITEM_HEIGHT)));
    return item;
  }

  private View listSection() {
    LinearLayout box = new LinearLayout(host);
    box.setOrientation(LinearLayout.VERTICAL);
    box.addView(sectionTitle("⑦ 列表项", "高 56dp · 左右 16dp · 标题 BodyLarge · 副标题 BodyMedium"));
    box.addView(m3ListItem());
    box.addView(m3ListItem());
    box.addView(m3ListItem());
    return box;
  }

  // ── 8. 芯片与控件 ─────────────────────────────────────────────────────────

  private View m3Chip(String text, boolean selected) {
    TextView chip = m3Text(text, M3Tokens.LABEL_LARGE,
        selected ? M3Tokens.ON_SECONDARY_CONTAINER : M3Tokens.ON_SURFACE_VARIANT);
    chip.setGravity(Gravity.CENTER);
    chip.setBackground(selected
        ? pill(M3Tokens.SECONDARY_CONTAINER)
        : stroked(Color.TRANSPARENT, M3Tokens.OUTLINE, M3Tokens.SHAPE_CHIP));
    chip.setPadding(dp(M3Tokens.CHIP_PADDING_H), 0, dp(M3Tokens.CHIP_PADDING_H), 0);
    chip.setLayoutParams(new LinearLayout.LayoutParams(-2, dp(M3Tokens.CHIP_HEIGHT)));
    host.applePressScale(chip);
    return chip;
  }

  /** M3 开关：轨道 52×32、滑块 24。**不是**项目现在那个 LumaSwitch 的尺寸。 */
  private View m3Switch(boolean on) {
    LinearLayout track = new LinearLayout(host);
    track.setGravity(on ? Gravity.END | Gravity.CENTER_VERTICAL : Gravity.START | Gravity.CENTER_VERTICAL);
    track.setBackground(pill(on ? M3Tokens.PRIMARY : M3Tokens.SURFACE_CONTAINER_HIGHEST));
    if (!on) {
      GradientDrawable g = pill(M3Tokens.SURFACE_CONTAINER_HIGHEST);
      g.setStroke(dp(2), M3Tokens.OUTLINE);
      track.setBackground(g);
    }
    track.setPadding(dp(4), 0, dp(4), 0);
    View thumb = new View(host);
    thumb.setBackground(pill(on ? M3Tokens.ON_PRIMARY : M3Tokens.OUTLINE));
    track.addView(thumb, new LinearLayout.LayoutParams(dp(M3Tokens.SWITCH_THUMB), dp(M3Tokens.SWITCH_THUMB)));
    track.setLayoutParams(new LinearLayout.LayoutParams(dp(M3Tokens.SWITCH_TRACK_WIDTH), dp(M3Tokens.SWITCH_TRACK_HEIGHT)));
    return track;
  }

  private View chipsAndControlsSection() {
    LinearLayout box = new LinearLayout(host);
    box.setOrientation(LinearLayout.VERTICAL);
    box.addView(sectionTitle("⑧ 芯片 · 开关", "芯片高 32dp；开关轨道 52×32（现在项目那个是 54×36）"));

    box.addView(groupLabel("Filter Chip（可选中）"));
    LinearLayout chips = new LinearLayout(host);
    chips.setOrientation(LinearLayout.HORIZONTAL);
    chips.addView(m3Chip("全部", true));
    LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-2, dp(M3Tokens.CHIP_HEIGHT));
    cp.leftMargin = dp(8);
    View c2 = m3Chip("文档", false);
    c2.setLayoutParams(cp);
    chips.addView(c2);
    box.addView(chips);

    box.addView(groupLabel("Switch（开 / 关）"));
    LinearLayout switches = new LinearLayout(host);
    switches.setOrientation(LinearLayout.HORIZONTAL);
    switches.addView(m3Switch(true));
    View off = m3Switch(false);
    LinearLayout.LayoutParams op = new LinearLayout.LayoutParams(dp(M3Tokens.SWITCH_TRACK_WIDTH), dp(M3Tokens.SWITCH_TRACK_HEIGHT));
    op.leftMargin = dp(16);
    off.setLayoutParams(op);
    switches.addView(off);
    box.addView(switches);
    return box;
  }

  // ── 9. 底部导航 ───────────────────────────────────────────────────────────

  /**
   * M3 底部导航栏：高 80dp，选中项用 **secondaryContainer 药丸指示器**（64×32）包住图标。
   * 这是 M3 最有辨识度的元素之一。
   */
  private View navSection() {
    LinearLayout box = new LinearLayout(host);
    box.setOrientation(LinearLayout.VERTICAL);
    box.addView(sectionTitle("⑨ 底部导航栏", "M3 的选中态是 secondaryContainer 药丸指示器，不是变色文字"));

    LinearLayout bar = new LinearLayout(host);
    bar.setOrientation(LinearLayout.HORIZONTAL);
    bar.setGravity(Gravity.CENTER_VERTICAL);
    bar.setBackgroundColor(M3Tokens.SURFACE_CONTAINER);
    bar.setPadding(0, dp(12), 0, dp(12));

    int[] icons = {R.drawable.ic_home, R.drawable.ic_sources, R.drawable.ic_download, R.drawable.ic_settings};
    String[] names = {"首页", "软件库", "下载", "设置"};
    for (int i = 0; i < icons.length; i++) {
      boolean active = i == 0;
      LinearLayout item = new LinearLayout(host);
      item.setOrientation(LinearLayout.VERTICAL);
      item.setGravity(Gravity.CENTER);

      LinearLayout indicator = new LinearLayout(host);
      indicator.setGravity(Gravity.CENTER);
      if (active) indicator.setBackground(pill(M3Tokens.SECONDARY_CONTAINER));
      ImageView icon = new ImageView(host);
      icon.setImageResource(icons[i]);
      icon.setColorFilter(active ? M3Tokens.ON_SECONDARY_CONTAINER : M3Tokens.ON_SURFACE_VARIANT);
      indicator.addView(icon, new LinearLayout.LayoutParams(dp(24), dp(24)));
      indicator.setLayoutParams(new LinearLayout.LayoutParams(
          dp(M3Tokens.NAV_INDICATOR_WIDTH), dp(M3Tokens.NAV_INDICATOR_HEIGHT)));
      item.addView(indicator);

      TextView t = m3Text(names[i], M3Tokens.LABEL_MEDIUM,
          active ? M3Tokens.ON_SURFACE : M3Tokens.ON_SURFACE_VARIANT);
      t.setGravity(Gravity.CENTER);
      LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-2, -2);
      tp.topMargin = dp(4);
      item.addView(t, tp);

      bar.addView(item, new LinearLayout.LayoutParams(0, -2, 1f));
    }
    box.addView(bar);
    return box;
  }
}
