package cc.nkbr.lanzouplus;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * [DFWX] DFW-61：**公告中心的未读与排序策略**。
 *
 * ## 用户要求（2026-09-30）
 * > "公告是发布式的，并且可以叠加"、"随时可以看"、"弹窗可选（一个开关）"、
 * > "未读红点要有专属的、非常丝滑流畅的动画，不要硬切换"。
 *
 * ## 责任边界
 * 只回答三件事，不碰 UI、不碰动画：
 *   ① 哪些公告要显示、按什么顺序；
 *   ② 哪几条是**未读**（红点数字从哪来）；
 *   ③ 已读状态怎么记、怎么**不无限膨胀**。
 * 红点动画、列表渲染、弹窗仍在 {@link MainActivity}。
 *
 * ## 为什么单独成类
 * "已读"这件事有个隐蔽的长期风险：**公告会被你（后台）删掉，但已读 id 会永远留在本地**。
 * 不清理的话，这个集合只增不减，几年后变成一个没人敢动的垃圾堆。
 * 所以本类在每次计算时**顺手把已消失的公告 id 从已读集合里剔除**——这条必须有测试。
 */
final class NoticeCenter {

  /** 偏好存储抽象：生产用 SharedPreferences，测试用内存实现。 */
  interface Store {
    Set<String> readIds();
    void setReadIds(Set<String> ids);
  }

  private final Store store;

  NoticeCenter(Store store) { this.store = store; }

  /** 生产构造：绑定 `notice_center` 偏好的 `read_ids`。 */
  static NoticeCenter forContext(Context context) {
    return new NoticeCenter(new Store() {
      private SharedPreferences prefs() {
        return context.getSharedPreferences("notice_center", Context.MODE_PRIVATE);
      }
      @Override public Set<String> readIds() {
        try {
          Set<String> values = prefs().getStringSet("read_ids", Collections.<String>emptySet());
          return values == null ? new LinkedHashSet<String>() : new LinkedHashSet<>(values);
        } catch (Exception error) {
          return new LinkedHashSet<>();
        }
      }
      @Override public void setReadIds(Set<String> ids) {
        try {
          // 必须传一份拷贝：SharedPreferences 不保证持有的是我们给的那个集合实例。
          prefs().edit().putStringSet("read_ids", new LinkedHashSet<>(ids)).apply();
        } catch (Exception ignored) { /* 记不住只是红点不消，不该影响使用 */ }
      }
    });
  }

  /**
   * 要显示的公告，已排好序。
   *
   * 排序：**置顶优先，其次按时间倒序**。用户要求"发布式、可叠加"，
   * 所以多条公告是同层堆叠而不是互相覆盖；置顶是他手动挑出来的，必须永远在最上面。
   *
   * 没有 id 的公告（后台异常数据）**直接丢弃**：它们既无法被标记已读、也无法被删除追踪，
   * 留着只会让红点永远消不掉。
   */
  List<RemoteConfigClient.Notice> visible(RemoteConfigClient.Snapshot snapshot) {
    if (snapshot == null) return Collections.emptyList();
    List<RemoteConfigClient.Notice> out = new ArrayList<>();
    for (RemoteConfigClient.Notice notice : snapshot.notices()) {
      if (notice == null || notice.id.isEmpty()) continue;
      out.add(notice);
    }
    Collections.sort(out, (left, right) -> {
      if (left.pinned != right.pinned) return left.pinned ? -1 : 1;
      return Long.compare(right.createdMs, left.createdMs);
    });
    return out;
  }

  /**
   * 未读公告。
   *
   * **顺带做垃圾回收**：把本地已读集合里"后台已经没有的公告 id"剔除并落盘。
   * 不做这件事，已读集合会随公告的增删只增不减，长期变成无法清理的垃圾。
   */
  List<RemoteConfigClient.Notice> unread(RemoteConfigClient.Snapshot snapshot) {
    List<RemoteConfigClient.Notice> all = visible(snapshot);
    Set<String> alive = new LinkedHashSet<>();
    for (RemoteConfigClient.Notice notice : all) alive.add(notice.id);

    Set<String> read = store.readIds();
    if (read.retainAll(alive)) store.setReadIds(read);   // 只在真的删掉了东西时才写盘

    List<RemoteConfigClient.Notice> out = new ArrayList<>();
    for (RemoteConfigClient.Notice notice : all) {
      if (!read.contains(notice.id)) out.add(notice);
    }
    return out;
  }

  int unreadCount(RemoteConfigClient.Snapshot snapshot) {
    return unread(snapshot).size();
  }

  boolean isRead(String id) {
    return id != null && store.readIds().contains(id);
  }

  void markRead(String id) {
    if (id == null || id.isEmpty()) return;
    Set<String> read = store.readIds();
    if (read.add(id)) store.setReadIds(read);
  }

  void markAllRead(RemoteConfigClient.Snapshot snapshot) {
    Set<String> read = store.readIds();
    boolean changed = false;
    for (RemoteConfigClient.Notice notice : visible(snapshot)) {
      if (read.add(notice.id)) changed = true;
    }
    if (changed) store.setReadIds(read);
  }

  /**
   * 需要**主动弹窗**的公告：后台勾了 `popup` 且**还没读过**。
   *
   * 两条都要满足：勾了 popup 但已读的再弹一次就是骚扰；
   * 没勾 popup 的即便未读也只该在红点里提示，不该打断用户。
   */
  List<RemoteConfigClient.Notice> popupNotices(RemoteConfigClient.Snapshot snapshot) {
    List<RemoteConfigClient.Notice> out = new ArrayList<>();
    for (RemoteConfigClient.Notice notice : unread(snapshot)) {
      if (notice.popup) out.add(notice);
    }
    return out;
  }

  /**
   * 红点上显示的数字文本。
   *
   * 超过 99 显示 `99+`：角标宽度有限，三位数会把圆点撑成一条，既难看也会挤到旁边图标。
   */
  static String badgeText(int unreadCount) {
    if (unreadCount <= 0) return "";
    if (unreadCount > 99) return "99+";
    return String.valueOf(unreadCount);
  }

  /** 红点是否该出现。0 条未读时**整个角标消失**（用户要求：有未读才出现）。 */
  static boolean badgeVisible(int unreadCount) {
    return unreadCount > 0;
  }
}
