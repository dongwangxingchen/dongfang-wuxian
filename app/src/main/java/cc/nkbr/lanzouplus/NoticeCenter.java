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
  /**
   * [DFWX] 本机版本名（如 `1.0.0`），用于**公告分版本**过滤。
   *
   * 空 = 不过滤（全都显示）。拿不到版本名时**必须**走这一支：
   * 硬过滤会把所有带适用版本的公告全部吞掉，那是"静默丢内容"，比多显示一条严重得多。
   */
  private final String appVersionName;

  NoticeCenter(Store store) { this(store, ""); }

  NoticeCenter(Store store, String appVersionName) {
    this.store = store;
    this.appVersionName = appVersionName == null ? "" : appVersionName.trim();
  }

  /** 生产构造：绑定 `notice_center` 偏好的 `read_ids`，并带上本机版本名。 */
  static NoticeCenter forContext(Context context, String appVersionName) {
    return new NoticeCenter(contextStore(context), appVersionName);
  }

  /** 兼容旧调用方：不带版本名 = 不过滤。 */
  static NoticeCenter forContext(Context context) {
    return forContext(context, "");
  }

  private static Store contextStore(Context context) {
    return new Store() {
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
    };
  }

  /**
   * 这条公告适不适用于当前版本。
   *
   * ## 规则（2026-10-03 用户批准的「公告分版本」设计）
   * - 公告**没填**适用版本 → **通用**，所有版本都看。老数据、老 APK 都走这一支。
   * - 公告**填了** → 只有版本名一致的当前版本才看。用户已确认接受这个后果：
   *   升级之后，上一个版本的公告不再显示。
   *
   * ## 为什么按"版本名"而不是 versionCode
   * 版本名是用户能看懂、后台能手填的那个（`1.0.0`）；versionCode 是本项目**派生**出来的
   * （`1.0.0→10000`，见 `app/build.gradle.kts`），让后台手填一个派生值只会填错。
   *
   * ## 为什么拿不到本机版本名时不过滤
   * 拿不到还硬过滤 = 把所有带版本的公告全部吞掉 = **静默丢内容**。
   * 宁可多显示一条，也不能让用户永远看不到公告。
   */
  static boolean appliesTo(String noticeVersion, String appVersionName) {
    String wanted = normalizeVersion(noticeVersion);
    if (wanted.isEmpty()) return true;
    String mine = normalizeVersion(appVersionName);
    if (mine.isEmpty()) return true;
    return wanted.equalsIgnoreCase(mine);
  }

  /** 去掉空白和开头的 `v`/`V`：后台可能填 `v1.0.0`，而版本名是 `1.0.0`，两者必须算同一条。 */
  static String normalizeVersion(String raw) {
    if (raw == null) return "";
    String value = raw.trim();
    while (!value.isEmpty() && (value.charAt(0) == 'v' || value.charAt(0) == 'V')) {
      value = value.substring(1).trim();
    }
    return value;
  }

  /**
   * 要显示的公告，已排好序。
   *
   * 排序：**置顶优先，其次按时间倒序**。用户要求"发布式、可叠加"，
   * 所以多条公告是同层堆叠而不是互相覆盖；置顶是他手动挑出来的，必须永远在最上面。
   *
   * 没有 id 的公告（后台异常数据）**直接丢弃**：它们既无法被标记已读、也无法被删除追踪，
   * 留着只会让红点永远消不掉。
   *
   * [DFWX] **公告分版本**的过滤也收口在这里**一处**（2026-10-03）。
   * 为什么不散落到调用方：红点数、弹窗、全部已读、已读集合回收**全都**经过本方法，
   * 收口一处就自动一致；散到四处，早晚会漏掉一处——那种漏法是"某处显示别的版本的公告"，
   * 而且只在特定版本组合下才复现。
   */
  List<RemoteConfigClient.Notice> visible(RemoteConfigClient.Snapshot snapshot) {
    if (snapshot == null) return Collections.emptyList();
    List<RemoteConfigClient.Notice> out = new ArrayList<>();
    for (RemoteConfigClient.Notice notice : snapshot.notices()) {
      if (notice == null || notice.id.isEmpty()) continue;
      if (!appliesTo(notice.versionName, appVersionName)) continue;
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
    Set<String> read = prunedReadIds(snapshot);
    List<RemoteConfigClient.Notice> out = new ArrayList<>();
    for (RemoteConfigClient.Notice notice : visible(snapshot)) {
      if (!read.contains(notice.id)) out.add(notice);
    }
    return out;
  }

  /**
   * 本地已读集合，**顺带做垃圾回收**：剔除后台已经没有的公告 id 并落盘。
   * 不做这件事，已读集合会随公告增删只增不减，长期变成无法清理的垃圾。
   * 只在**真的删掉了东西**时才写盘（否则每次算未读都写盘是浪费）。
   *
   * ⚠️ [DFWX] 这里的 `alive` **刻意不过滤版本**（2026-10-03 加公告分版本时特意分开的）。
   *
   * 本方法判断的是「**后台还有没有这条公告**」，不是「**当前版本要不要显示它**」——
   * 这两件事不一样。如果拿 {@link #visible} 来建 `alive`，含义就偷偷变成了后者：
   * 一条「1.0.1 的公告」在 1.0.0 上不显示，它的已读 id 就会被当成垃圾清掉。
   * 今天看不出问题（用户不会降级），但它把两个概念焊死了，以后任何一处改动
   * 都会连带影响另一处。所以这里显式地不共用。
   */
  private Set<String> prunedReadIds(RemoteConfigClient.Snapshot snapshot) {
    Set<String> read = store.readIds();
    // **拿不到"后台到底有哪些公告"时绝不能清理**。旧实现把这件事当成"公告都被删了"，
    // 于是这条链路会**每次启动都把已读集合清空并写盘**：
    //   showHomeLanding() → refreshNoticeBell() → unreadCount(null) → 这里 → 清空
    // 等公告拉回来时它又变成"未读" → 又弹一次。用户 2026-10-01 反馈的
    // "发布后只弹一次你没做"就是它——不是没写模式，是已读状态每次开机都被抹掉。
    //
    // [2026-10-03 修第二个漏洞] 上次只堵住了 `snapshot == null` 这一种，
    // 用的是 `!snapshot.reachable` —— 但 **`reachable` 的语义是"四个集合里任意一个拉到了"**
    // （`RemoteConfigClient.fetch` 里 `anyOk` 的赋值，那边注释也写明了）。
    // 于是还有一个洞：**公告这一路单独失败、其余三路正常**时 `reachable=true`，
    // 而 `notices` 是空表 → `alive` 为空 → `read.retainAll(空)` → 已读集合被清空并落盘。
    // 触发条件很日常：公告接口超时/返回非 200/格式坏（`get()` 这些情况都抛 IOException，
    // 被 fetch 的 catch 吞掉），而 control/release/changelog 正常。
    //
    // 现在改用 `noticeOk`：它**只**代表"公告这一路自己成功了"。
    //   · noticeOk=false → 不知道后台有哪些公告 → 一律不动已读集合（fail-safe）；
    //   · noticeOk=true 且公告为空 → 后台确实清空了公告 → 正常做垃圾回收。
    if (snapshot == null || !snapshot.noticeOk) return read;
    Set<String> alive = new LinkedHashSet<>();
    for (RemoteConfigClient.Notice notice : snapshot.notices()) {
      if (notice == null || notice.id.isEmpty()) continue;
      alive.add(notice.id);
    }
    if (read.retainAll(alive)) store.setReadIds(read);
    return read;
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
   * ## 三种模式（DFW-70，用户 2026-09-30 要求）
   * - **静默**：永不自动弹，只亮红点，等用户自己点进菜单看
   * - **一次性**：弹一次，读过就不再弹（`popup=true` 的旧数据落到这一档）
   * - **永久**：每次打开软件都弹，**无视已读**（用户明确要"一直弹出来显示这个公告"）
   *
   * 注意"永久"这一档是**刻意无视已读状态**的：用户要的就是"每次打开都能看到"。
   * 其余两档仍然尊重已读——否则同一条件读过的公告反复弹就是骚扰。
   */
  List<RemoteConfigClient.Notice> popupNotices(RemoteConfigClient.Snapshot snapshot) {
    List<RemoteConfigClient.Notice> out = new ArrayList<>();
    Set<String> read = prunedReadIds(snapshot);
    for (RemoteConfigClient.Notice notice : visible(snapshot)) {
      if (notice.isSilent()) continue;               // 静默：永不弹
      if (notice.isAlwaysPopup()) { out.add(notice); continue; }  // 永久：无视已读
      if (!read.contains(notice.id)) out.add(notice);            // 一次性：未读才弹
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
