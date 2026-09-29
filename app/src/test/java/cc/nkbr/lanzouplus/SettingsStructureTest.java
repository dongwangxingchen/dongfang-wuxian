package cc.nkbr.lanzouplus;

import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ScrollView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** T5-S 设置重做的 JVM 结构回归：四分区信息架构、内联搜索过滤与展开态恢复。
 *  <p>位图截图在本机 Robolectric 环境不可用（native canvas 无 CJK 字形输出），
 *  视觉验收以真机为准；本测试兜底结构与过滤逻辑。 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "w411dp-h891dp-420dpi")
public class SettingsStructureTest {

  private static View sectionContent(View section) {
    // 四分区 = [header, content]；底部关于区卡片无 header，content 即唯一子视图
    ViewGroup g = (ViewGroup) section;
    return g.getChildAt(g.getChildCount() > 1 ? 1 : 0);
  }

  /** 按内容描述在 footer 行里找入口（分隔线没有描述，自然被跳过）。 */
  private static View findRowByDescription(ViewGroup rows, String description) {
    for (int i = 0; i < rows.getChildCount(); i++) {
      View row = rows.getChildAt(i);
      CharSequence desc = row.getContentDescription();
      if (desc != null && description.contentEquals(desc)) return row;
    }
    return null;
  }

  private static int visibleRows(View content) {
    ViewGroup g = (ViewGroup) content;
    int n = 0;
    for (int i = 0; i < g.getChildCount(); i++) if (g.getChildAt(i).getVisibility() == View.VISIBLE) n++;
    return n;
  }

  @Test
  public void settingsStructureAndSearch() throws Exception {
    MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();
    assertNotNull(activity);
    activity.showSettings();
    ShadowLooper.idleMainLooper();

    assertEquals(4, activity.pageKind);
    // root = 页头 + 搜索框 + 滚动区
    assertEquals(3, activity.root.getChildCount());
    assertTrue(activity.root.getChildAt(1) instanceof ViewGroup
        && ((ViewGroup) activity.root.getChildAt(1)).getChildAt(0) instanceof EditText);
    assertTrue(activity.root.getChildAt(2) instanceof ScrollView);

    // 五区注册（四分区 + 底部关于区卡片）；常用默认展开，其余收起，底部卡片内容常显
    assertEquals(5, activity.settingsSearchSections.size());
    assertEquals(View.VISIBLE, sectionContent(activity.settingsSearchSections.get(0)).getVisibility());
    assertEquals(View.GONE, sectionContent(activity.settingsSearchSections.get(1)).getVisibility());
    assertEquals(View.GONE, sectionContent(activity.settingsSearchSections.get(2)).getVisibility());
    assertEquals(View.GONE, sectionContent(activity.settingsSearchSections.get(3)).getVisibility());
    assertEquals(View.VISIBLE, sectionContent(activity.settingsSearchSections.get(4)).getVisibility());

    // 搜索「识别并发」：只性能分区命中，其余整组隐藏，不出现空态
    EditText input = activity.settingsSearchInput;
    assertNotNull(input);
    input.setText("识别并发");
    ShadowLooper.idleMainLooper(400, java.util.concurrent.TimeUnit.MILLISECONDS);
    assertEquals(View.GONE, activity.settingsSearchSections.get(0).getVisibility());
    assertEquals(View.VISIBLE, activity.settingsSearchSections.get(1).getVisibility());
    assertEquals(View.GONE, activity.settingsSearchSections.get(2).getVisibility());
    assertEquals(View.GONE, activity.settingsSearchSections.get(3).getVisibility());
    assertEquals(View.GONE, activity.settingsSearchEmpty.getVisibility());
    assertTrue("性能分区应命中至少 1 行（下载面板整块可见）", visibleRows(sectionContent(activity.settingsSearchSections.get(1))) >= 1);

    // 搜索「崩溃日志」：命中底部关于区（已从数据与关于移出）
    input.setText("崩溃日志");
    ShadowLooper.idleMainLooper(400, java.util.concurrent.TimeUnit.MILLISECONDS);
    assertEquals(View.GONE, activity.settingsSearchSections.get(0).getVisibility());
    assertEquals(View.GONE, activity.settingsSearchSections.get(3).getVisibility());
    assertEquals(View.VISIBLE, activity.settingsSearchSections.get(4).getVisibility());

    // 无命中：全部隐藏 + 空态出现
    input.setText("绝不存在的关键词xyz");
    ShadowLooper.idleMainLooper(400, java.util.concurrent.TimeUnit.MILLISECONDS);
    for (View section : activity.settingsSearchSections) assertEquals(View.GONE, section.getVisibility());
    assertEquals(View.VISIBLE, activity.settingsSearchEmpty.getVisibility());

    // 清空：分区全部恢复，展开态回到默认（常用开、其余收起）
    input.setText("");
    ShadowLooper.idleMainLooper(400, java.util.concurrent.TimeUnit.MILLISECONDS);
    for (View section : activity.settingsSearchSections) assertEquals(View.VISIBLE, section.getVisibility());
    assertEquals(View.VISIBLE, sectionContent(activity.settingsSearchSections.get(0)).getVisibility());
    assertEquals(View.GONE, sectionContent(activity.settingsSearchSections.get(1)).getVisibility());
    assertEquals(View.GONE, sectionContent(activity.settingsSearchSections.get(2)).getVisibility());
    assertEquals(View.GONE, sectionContent(activity.settingsSearchSections.get(3)).getVisibility());
    assertEquals(View.VISIBLE, sectionContent(activity.settingsSearchSections.get(4)).getVisibility());
    assertEquals(View.GONE, activity.settingsSearchEmpty.getVisibility());
  }

  /** T4：底部关于区三行全部改独立页面（不弹窗），返回链=systemBackAction 回设置页 */
  @Test
  public void aboutSectionPagesNavigation() throws Exception {
    MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();
    assertNotNull(activity);
    activity.showSettings();
    ShadowLooper.idleMainLooper();

    ViewGroup footer = (ViewGroup) activity.settingsSearchSections.get(4);
    ViewGroup rows = (ViewGroup) footer.getChildAt(0);
    // DFW-7 起 footer 多了「检查更新」行与分隔线，行数不再固定。
    // 改成按 contentDescription 定位，避免每次增删入口都要改下标（原本按下标写死，加一行即红）。
    assertTrue("footer 必须至少有分隔线与各入口", rows.getChildCount() >= 5);
    View aboutRow = findRowByDescription(rows, "关于东方无限");
    View ackRow = findRowByDescription(rows, "参考与致谢");
    View crashRow = findRowByDescription(rows, "崩溃日志");
    assertNotNull("footer 必须有关于入口", aboutRow);
    assertNotNull("footer 必须有参考与致谢入口", ackRow);
    assertNotNull("footer 必须有崩溃日志入口", crashRow);

    aboutRow.performClick();
    ShadowLooper.idleMainLooper();
    assertEquals(7, activity.pageKind);
    assertNotNull("关于页应注册系统返回", activity.systemBackAction);
    Runnable aboutBack = activity.systemBackAction;
    aboutBack.run();
    ShadowLooper.idleMainLooper();
    assertEquals(4, activity.pageKind);

    ackRow.performClick();
    ShadowLooper.idleMainLooper();
    assertEquals(8, activity.pageKind);
    Runnable ackBack = activity.systemBackAction;
    ackBack.run();
    ShadowLooper.idleMainLooper();
    assertEquals(4, activity.pageKind);

    crashRow.performClick();
    ShadowLooper.idleMainLooper();
    assertEquals(9, activity.pageKind);
    Runnable crashBack = activity.systemBackAction;
    crashBack.run();
    ShadowLooper.idleMainLooper();
    assertEquals(4, activity.pageKind);
  }
}
