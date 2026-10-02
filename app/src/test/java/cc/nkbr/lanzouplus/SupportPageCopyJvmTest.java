package cc.nkbr.lanzouplus;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.graphics.Insets;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

/**
 * [DFWX] 诚信付费页「文案 + 窗口 insets」守卫（用户 2026-10-01 口述）。
 *
 * 两件事各由一组断言钉住：
 * <ol>
 *   <li><b>文案</b>：页头 kicker「诚信付费 · 自愿」、标题下副标「诚信付费 ￥5 · 一次付清 · 承诺永久更新」、
 *       权益区 3 条 emoji 短语全部删除；改为「1 条奖杯图标 + 长句」，而 ￥5 仍在中段价格区可见。</li>
 *   <li><b>insets</b>：本页是独立 Activity，此前没处理窗口 insets，页头返回箭头被状态栏压住（用户截图）。
 *       现在 edge-to-edge + 根 View 吃 systemBars padding —— 底部导航条同理不再压住小字。
 *       Robolectric 没有真状态栏，这里走**真实的 dispatch 路径**（{@code dispatchApplyWindowInsets}）
 *       灌一组 systemBars，断言根 View 的 padding 真的被填上；真机上由系统走同一条回调。</li>
 * </ol>
 *
 * 设置页的「诚信付费」按钮同步守卫：未付费态那行小字整行不显示，两种状态都带奖杯图标。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w411dp-h891dp-420dpi")
public class SupportPageCopyJvmTest {

  @BeforeClass public static void silenceAutoImport() { MainActivity.LIBRARY_AUTO_IMPORT = false; }

  private static SupportActivity supportActivity() {
    SupportActivity a = Robolectric.buildActivity(SupportActivity.class).setup().get();
    Shadows.shadowOf(Looper.getMainLooper()).idle();
    return a;
  }

  private static void collectTexts(View view, List<String> out) {
    if (view instanceof TextView) {
      CharSequence text = ((TextView) view).getText();
      if (text != null && text.length() > 0) out.add(text.toString());
    }
    if (view instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) view;
      for (int i = 0; i < group.getChildCount(); i++) collectTexts(group.getChildAt(i), out);
    }
  }

  private static String textsOf(View root) {
    List<String> texts = new ArrayList<>();
    collectTexts(root, texts);
    return String.join("\n", texts);
  }

  private static String supportPageTexts() { return textsOf(supportActivity().root); }

  // ── 任务 A：文案 ────────────────────────────────────────────────────────

  @Test public void supportPage_dropsEveryLineTheUserDeleted() {
    String joined = supportPageTexts();
    String[] deleted = {"学生免费", "一次付清 · 承诺永久更新", "诚信付费 · 自愿"};
    for (String gone : deleted) {
      assertFalse("用户 2026-10-01 口述删掉的文案不得再出现在支持页：「" + gone + "」\n页面全文：\n" + joined,
          joined.contains(gone));
    }
  }

  /**
   * [DFW-99 2026-10-02] 底部那行**假话**不得回来。
   *
   * 原文案是「重装软件也会保留赞助状态」——**事实相反**：
   * `AndroidManifest.xml` 里 `allowBackup="false"`，系统备份被关掉，
   * 所以卸载重装一定丢（用户原话：「我们不是每一次点击按钮后赞助成功吗？但是删了就没了」）。
   *
   * 承诺一个做不到的事，比不承诺更糟：用户重装后发现要重新付费，会觉得自己被骗。
   * 现在写的是「解锁记录保存在本机 · 不付费也可以完整使用」——只陈述事实。
   *
   * 这条断言守的是**语义**不是措辞：任何"重装/换机后仍然保留"的意思都不许出现。
   */
  @Test public void supportPage_neverClaimsTheUnlockSurvivesReinstall() {
    String joined = supportPageTexts();
    String[] forbidden = {
        "重装软件也会保留赞助状态",
        "重装也会保留",
        "重装后仍然",
        "换机后仍然",
        "永久保留",
        "永久有效",
    };
    for (String claim : forbidden) {
      assertFalse("这行是在承诺「重装/换机后还在」，但 allowBackup=false，事实相反，"
              + "用户重装后要重新付费会觉得被骗：「" + claim + "」\n页面全文：\n" + joined,
          joined.contains(claim));
    }
  }

  @Test public void supportPage_keepsTheSinglePerkSentence() {
    String joined = supportPageTexts();
    assertTrue("权益区必须是用户口述的那一条（每周自费续 1 万次 DeepSeek v4.1）：\n" + joined,
        joined.contains("1 万次 DeepSeek v4.1"));
    assertTrue("口述文案的其余部分也要在：\n" + joined, joined.contains("自动刷新") && joined.contains("请诚信付费"));
  }

  @Test public void supportPage_keepsThePriceVisible() {
    String joined = supportPageTexts();
    assertTrue("「￥5」必须保持可见（删副标不能把价格删掉）：\n" + joined, joined.contains("￥5"));
    assertTrue("价格区下方两行副标保留：\n" + joined,
        joined.contains("诚信付费 · 一次付清") && joined.contains("承诺永久更新 · 绝不停更"));
  }

  @Test public void supportPage_keepsTheDeveloperLetter() {
    String joined = supportPageTexts();
    assertTrue("开发者信（letterCard）保留不动：\n" + joined, joined.contains("这个应用没有广告，也不强制付费。"));
  }

  // ── 任务 A：窗口 insets ────────────────────────────────────────────────

  @Test public void supportPage_rootTakesSystemBarInsetsAsPadding() {
    SupportActivity a = supportActivity();
    assertNotNull("支持页根 View 必须存在", a.root);
    // 真机上报的 systemBars：状态栏 96px、导航条 132px（Robolectric 无系统栏，这里灌同一条 dispatch 路径）
    WindowInsets systemBars = new WindowInsets.Builder()
        .setInsets(WindowInsets.Type.systemBars(), Insets.of(0, 96, 0, 132))
        .build();
    a.root.dispatchApplyWindowInsets(systemBars);
    assertTrue("顶部必须让开状态栏（返回箭头被压住就是这个 padding 缺失）：" + a.root.getPaddingTop(),
        a.root.getPaddingTop() > 0);
    assertTrue("底部必须让开导航条（脚注小字同理）：" + a.root.getPaddingBottom(),
        a.root.getPaddingBottom() > 0);
    assertEquals("insets 应原样落成 padding", 96, a.root.getPaddingTop());
    assertEquals("insets 应原样落成 padding", 132, a.root.getPaddingBottom());
  }

  // ── 任务 B：设置页顶部「诚信付费」按钮 ──────────────────────────────────

  private static IntegrityPayButton payButton(boolean paid) {
    return new IntegrityPayButton(RuntimeEnvironment.getApplication(), ThemeEngine.LEGACY, 2.625f, paid, false, null);
  }

  /** 找「横排 = 图标 + 文字」的标题行（照抄 SelectionBarJvmTest 的图标/文字同排判定）。 */
  private static LinearLayout titleRow(View view) {
    if (view instanceof LinearLayout) {
      LinearLayout row = (LinearLayout) view;
      boolean hasLabel = false, hasIcon = false;
      for (int i = 0; i < row.getChildCount(); i++) {
        hasLabel |= row.getChildAt(i) instanceof TextView;
        hasIcon |= row.getChildAt(i) instanceof ImageView;
      }
      if (row.getOrientation() == LinearLayout.HORIZONTAL && hasLabel && hasIcon) return row;
    }
    if (view instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) view;
      for (int i = 0; i < group.getChildCount(); i++) {
        LinearLayout found = titleRow(group.getChildAt(i));
        if (found != null) return found;
      }
    }
    return null;
  }

  @Test public void integrityPayButton_unpaidShowsNoSubLineButKeepsTheTrophy() {
    IntegrityPayButton button = payButton(false);
    String joined = textsOf(button);
    assertTrue("未付费仍有大字：「诚信付费 · 自愿」\n" + joined, joined.contains("诚信付费 · 自愿"));
    assertFalse("未付费态那行小字整行不显示：\n" + joined, joined.contains("学生免费"));
    assertFalse("未付费态那行小字整行不显示：\n" + joined, joined.contains("￥5"));
    LinearLayout row = titleRow(button);
    assertNotNull("大字左边必须有奖杯图标（横排 图标+文字）", row);
    assertEquals("标题行必须水平居中（图标与文字垂直居中对齐）",
        Gravity.CENTER_VERTICAL, row.getGravity() & Gravity.VERTICAL_GRAVITY_MASK);
    View icon = row.getChildAt(0);
    assertTrue("图标是 ImageView 且已加载矢量 drawable", icon instanceof ImageView && ((ImageView) icon).getDrawable() != null);
    assertEquals("图标是装饰性的，不单独播报", View.IMPORTANT_FOR_ACCESSIBILITY_NO, icon.getImportantForAccessibility());
    assertEquals("按钮的无障碍描述保持不变", "诚信付费，自愿支持开发", button.getContentDescription().toString());
  }

  @Test public void integrityPayButton_paidKeepsTheThanksLineAndTheTrophy() {
    IntegrityPayButton button = payButton(true);
    String joined = textsOf(button);
    assertTrue("已付费大字保留：\n" + joined, joined.contains("已诚信付费 ✓"));
    assertTrue("已付费那行小字保留：\n" + joined, joined.contains("感谢支持 · 全部权限已开放"));
    assertNotNull("已付费态同样带奖杯（两种状态保持一致）", titleRow(button));
    assertEquals("按钮的无障碍描述保持不变", "已诚信付费，全部权限已开放", button.getContentDescription().toString());
  }
}
