package cc.nkbr.lanzouplus;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * [DFWX] 远程控制与更新体系 · 后台数据客户端（2026-09-30 立项）。
 *
 * ## 职责
 * 从用户自建后台（PocketBase，**`http://39.106.33.135/pb/`** —— 注意是 http，
 * 理由见下面「为什么用 http 而不是 https」；这里原来误写成 https，与常量 `BASE` 矛盾）读取四类数据：
 * 总控（维护/停用）、版本、公告、更新记录。**只读，不写**。
 *
 * ## 为什么用 http 而不是 https
 * 用户 2026-09-30 决策：走 HTTP，安全性由**下载后三重校验**保证
 * （sha256 + 包名 + 签名证书，系统级比对，篡改包装不上）。
 * 好处是**用户零维护**——不用管证书、不用管 7 天续期。
 * 后台管理页另走 HTTPS（浏览器强制升级所致），二者互不影响。
 *
 * ## 关键设计：fail-open
 * 用户原话要求"权限很大"（能锁死软件），但那也意味着**服务器一挂就是全员打不开**。
 * 所以这里拉不到数据一律返回 {@link Snapshot#unavailable()}，
 * 由调用方按"一切正常"处理——**宁可漏拦，绝不因服务器故障把用户锁在门外**。
 * 这是与"高权限控制"的必要对冲（决策 #39）。
 */
final class RemoteConfigClient {
  /** 后台地址。走服务器自身（国内快），不依赖 GitHub。 */
  static final String BASE = "http://39.106.33.135/pb";
  private static final String API = BASE + "/api/collections/";
  /*
   * [DFW-103] 超时收紧：原来是 6s + 8s，最坏情况用户要等 **14 秒**公告才弹出来。
   * 服务器在国内（北京），一个几百字节的 JSON 用不了这么久；
   * 宁可早点失败（失败就不弹，不打扰），也不要让用户干等。
   */
  private static final int CONNECT_TIMEOUT_MS = 3000;
  private static final int READ_TIMEOUT_MS = 4000;
  private static final int JSON_LIMIT = 256 * 1024;

  private RemoteConfigClient() {}

  // ── 数据模型 ────────────────────────────────────────────────────────────

  /** 总控（后台只有一行）。 */
  static final class Control {
    final boolean maintenanceOn;
    final boolean blocked;
    final String maintenanceTitle;
    final String maintenanceBody;
    /** 维护时间文本，用户随便填（"永久"、"10月1日 20:00"…）；空则不显示。 */
    final String maintenanceUntil;
    /** [DFW-73] 内置渠道远程覆盖：后台留空则沿用 APK 内置值（见 app/build.gradle.kts 的 dfwx_ai_*）。 */
    final String aiBaseUrl;
    final String aiToken;
    final String aiModel;
    /** [DFW-77] 用户在 App 里看到的名字；空 = 跟模型名一样。 */
    final String aiDisplayName;
    /** [DFW-77] 请求路径；空 = 用 RikkaHub 的默认 /chat/completions。 */
    final String aiChatPath;
    final int aiMaxTokens;
    final boolean aiDisabled;

    Control(boolean maintenanceOn, boolean blocked, String title, String body, String until) {
      this(maintenanceOn, blocked, title, body, until, "", "", "", "", "", 0, false);
    }

    Control(boolean maintenanceOn, boolean blocked, String title, String body, String until,
            String aiBaseUrl, String aiToken, String aiModel, String aiDisplayName, String aiChatPath,
            int aiMaxTokens, boolean aiDisabled) {
      this.maintenanceOn = maintenanceOn;
      this.blocked = blocked;
      this.maintenanceTitle = title == null ? "" : title.trim();
      this.maintenanceBody = body == null ? "" : body.trim();
      this.maintenanceUntil = until == null ? "" : until.trim();
      this.aiBaseUrl = aiBaseUrl == null ? "" : aiBaseUrl.trim();
      this.aiToken = aiToken == null ? "" : aiToken.trim();
      this.aiModel = aiModel == null ? "" : aiModel.trim();
      this.aiDisplayName = aiDisplayName == null ? "" : aiDisplayName.trim();
      this.aiChatPath = aiChatPath == null ? "" : aiChatPath.trim();
      this.aiMaxTokens = aiMaxTokens;
      this.aiDisabled = aiDisabled;
    }

    /** 是否需要拦截整个应用。 */
    boolean blocksApp() { return maintenanceOn || blocked; }

    /** 是否有独立的"维护时间"区块可显示。 */
    boolean hasUntil() { return !maintenanceUntil.isEmpty(); }

    static Control normal() { return new Control(false, false, "", "", ""); }
  }

  /** 版本信息（后台只有一行）。 */
  static final class Release {
    final String versionName;
    final long versionCode;
    final String apkUrl;
    final String sha256;
    final long size;
    /** `soft` 软更新 / `force` 强制更新 / `off` 不提示。 */
    final String updateMode;

    Release(String versionName, long versionCode, String apkUrl, String sha256, long size, String updateMode) {
      this.versionName = versionName == null ? "" : versionName.trim();
      this.versionCode = versionCode;
      this.apkUrl = apkUrl == null ? "" : apkUrl.trim();
      this.sha256 = sha256 == null ? "" : sha256.trim().toLowerCase(Locale.ROOT);
      this.size = size;
      this.updateMode = updateMode == null ? "soft" : updateMode.trim();
    }

    boolean isForce() { return "force".equalsIgnoreCase(updateMode); }
    boolean isOff() { return "off".equalsIgnoreCase(updateMode); }
  }

  /**
   * 一条公告。
   *
   * ## 弹出模式（DFW-70 改版）
   * 用户要求三种（2026-09-30 原话）：
   * - **静默**：发布后**不弹**，只在菜单里亮红点，等用户自己点进来看
   * - **一次性**：弹出一次，之后不再弹
   * - **永久**：每次打开软件都弹
   *
   * 原来只有一个 `popup` 布尔值（弹 / 不弹），表达不了"永久"与"一次性"的区别。
   * 现在用字符串 `popupMode` 表达，并**兼容读取旧的布尔字段**：
   * `popup=true` → 一次性、`popup=false` → 静默。
   * 这样后台字段迁移前后 App 都能正确工作，不会出现"改后台那一刻大家全都收不到公告"。
   */
  static final class Notice {
    static final String MODE_SILENT = "silent";
    static final String MODE_ONCE = "once";
    static final String MODE_ALWAYS = "always";

    final String id, title, body, level;
    final boolean pinned;
    /** 见类注释；只会是三选一，非法值一律回落到"一次性"（宁可多弹一次，也不要静默漏掉公告）。 */
    final String popupMode;
    final long createdMs;

    Notice(String id, String title, String body, String level, boolean pinned, String popupMode, long createdMs) {
      this.id = id == null ? "" : id;
      this.title = title == null ? "" : title.trim();
      this.body = body == null ? "" : body.trim();
      this.level = level == null ? "normal" : level.trim();
      this.pinned = pinned;
      this.popupMode = normalizeMode(popupMode);
      this.createdMs = createdMs;
    }

    /** 兼容旧布尔字段的构造：true → 一次性，false → 静默。 */
    Notice(String id, String title, String body, String level, boolean pinned, boolean popup, long createdMs) {
      this(id, title, body, level, pinned, popup ? MODE_ONCE : MODE_SILENT, createdMs);
    }

    static String normalizeMode(String raw) {
      if (raw != null) {
        String value = raw.trim().toLowerCase(java.util.Locale.ROOT);
        if (MODE_SILENT.equals(value)) return MODE_SILENT;
        if (MODE_ALWAYS.equals(value)) return MODE_ALWAYS;
        if (MODE_ONCE.equals(value)) return MODE_ONCE;
      }
      // 空值/拼错 → 一次性。**不能默认静默**：那会让后台一次手误把公告全部吞掉，用户永远看不到。
      return MODE_ONCE;
    }

    boolean isSilent() { return MODE_SILENT.equals(popupMode); }
    boolean isAlwaysPopup() { return MODE_ALWAYS.equals(popupMode); }

    /** 是否会自动弹（静默以外都会进弹窗候选，具体还取决于"读过没有"）。 */
    boolean autoPopup() { return !isSilent(); }

    boolean isUrgent() { return "urgent".equalsIgnoreCase(level); }
    boolean isImportant() { return "important".equalsIgnoreCase(level); }
  }

  /** 一条更新记录。 */
  static final class Changelog {
    final String id, versionName, date, highlights;
    Changelog(String id, String versionName, String date, String highlights) {
      this.id = id == null ? "" : id;
      this.versionName = versionName == null ? "" : versionName.trim();
      this.date = date == null ? "" : date.trim();
      this.highlights = highlights == null ? "" : highlights.trim();
    }
  }

  /**
   * 一次拉取的全部结果。
   *
   * {@code reachable=false} 表示后台不可达——此时 {@link #control()} 返回"正常"、
   * 版本为空、公告与更新记录为空，调用方据此静默降级（fail-open）。
   */
  static final class Snapshot {
    final boolean reachable;
    /**
     * [2026-10-03 修已读状态被误清空] **「公告这一路」自己拉成功了没有。**
     *
     * 为什么不能拿 {@link #reachable} 代替它（这是一个真 bug 的根因）：
     * `reachable` 的定义是「**四个集合里任意一个**拉到了」（见 {@link #fetch} 里 `anyOk` 的赋值），
     * 而「公告路失败、其余三路正常」时 `reachable=true` 但 `notices` 是**空表**。
     * 调用方 `NoticeCenter.prunedReadIds` 拿它当「公告集合拉到了」用，
     * 于是 `alive` 为空 → `read.retainAll(空)` → **把已读集合清空并落盘** →
     * 用户读过的「一次性」公告下次开机**再弹一遍**。
     *
     * 这正是 `NoticeCenter` 那段注释里声称已修掉的投诉（用户 2026-10-01「发布后只弹一次你没做」）——
     * 上一次只堵住了 `snapshot == null` 这一种，**没覆盖「快照可达但公告路单独失败」**。
     *
     * 所以这里把「公告路成功」单独记一个标志，让调用方能区分这两件事：
     *   · `noticeOk=false` → **不知道**后台有哪些公告 → 绝不动已读集合；
     *   · `noticeOk=true` 且 `notices` 为空 → 后台**确实**没有公告了 → 该清理就清理。
     */
    final boolean noticeOk;
    private final Control control;
    private final Release release;
    private final List<Notice> notices;
    private final List<Changelog> changelogs;

    Snapshot(boolean reachable, boolean noticeOk, Control control, Release release, List<Notice> notices, List<Changelog> changelogs) {
      this.reachable = reachable;
      this.noticeOk = noticeOk;
      this.control = control;
      this.release = release;
      this.notices = notices == null ? Collections.emptyList() : notices;
      this.changelogs = changelogs == null ? Collections.emptyList() : changelogs;
    }

    static Snapshot unavailable() {
      return new Snapshot(false, false, Control.normal(), null, Collections.emptyList(), Collections.emptyList());
    }

    Control control() { return control == null ? Control.normal() : control; }
    Release release() { return release; }
    List<Notice> notices() { return notices; }
    List<Changelog> changelogs() { return changelogs; }

    /** 需要弹窗的公告（后台勾了 popup）。 */
    List<Notice> popupNotices() {
      List<Notice> out = new ArrayList<>();
      for (Notice n : notices) if (n.autoPopup()) out.add(n);
      return out;
    }
  }

  // ── 拉取 ────────────────────────────────────────────────────────────────

  /**
   * 拉取全部配置。**任何一路失败都不影响其它路**——例如公告服务异常时，
   * 版本检查仍然可用（反之亦然）。这是"静默刷新"能稳定工作的前提。
   *
   * 调用方必须在**后台线程**执行（本方法阻塞）。
   */
  /**
   * [DFW-103] 上一次成功拉到的**四个集合的原始 JSON**。
   *
   * 为什么存原始 JSON 而不是存解析后的对象：还原时能**复用同一套解析器**
   * （`parseControl` / `parseRelease` / `parseNotices` / `parseChangelogs`），
   * 不会出现"缓存格式和线上格式不一致"这种只在离线路径才炸的坑。
   */
  static final class Raw {
    JSONArray control, release, notice, changelog;
  }

  static Snapshot fetch() {
    return fetch(null);
  }

  private static final String CACHE_PREFS = "remote_config_cache-v1";

  /** 把这一次成功拉到的原始 JSON 落盘（下次启动先用它，网络回来再覆盖）。 */
  /**
   * 写缓存 —— **按集合合并，绝不整串覆盖**。
   *
   * [DFW-97 审计发现的静默失效] 原来这里是"新建一个 JSONObject，把非 null 的集合塞进去，
   * 然后整串写回"。而调用方（`MainActivity.maybeFetchNotices`）只要
   * `snapshot.reachable` 为真就落盘，**而 reachable 的定义是"四个集合里任意一个拉到了"**
   * （见 `:341` 附近的赋值）。
   *
   * 于是最坏情况：公告接口抖一下、其余三路正常 →
   * 新 Raw 里 `notice == null` → 写回去的缓存**没有公告** →
   * 用户下次启动读到的缓存是"没有公告"的版本，红点和弹窗全没了，
   * **而且日志里一个字都没有**（整个方法包在 catch 里，覆盖写本身也不报错）。
   *
   * 这与"公告拉不到"那条 NPE 是同一类问题：**错误被静默吞掉，表现为功能凭空消失**。
   *
   * 现在改成：先把旧缓存读出来，**只覆盖这次真正拿到的集合**，其余原样保留。
   */
  static void storeCache(android.content.Context ctx, Raw raw) {
    if (ctx == null || raw == null) return;
    try {
      JSONObject o = new JSONObject();
      // 旧缓存先铺底：这次没拿到的集合沿用上次的，而不是被抹成"没有"
      String previous = ctx.getSharedPreferences(CACHE_PREFS, android.content.Context.MODE_PRIVATE)
          .getString("raw", "");
      if (!previous.isEmpty()) {
        try {
          JSONObject old = new JSONObject(previous);
          java.util.Iterator<String> keys = old.keys();
          while (keys.hasNext()) {
            String key = keys.next();
            JSONArray value = old.optJSONArray(key);
            if (value != null) o.put(key, value);
          }
        } catch (Exception ignored) { /* 旧缓存坏了就当没有，用这次的 */ }
      }
      // 这次真正拿到的集合覆盖上去
      if (raw.control != null) o.put("control", raw.control);
      if (raw.release != null) o.put("release", raw.release);
      if (raw.notice != null) o.put("notice", raw.notice);
      if (raw.changelog != null) o.put("changelog", raw.changelog);
      ctx.getSharedPreferences(CACHE_PREFS, android.content.Context.MODE_PRIVATE)
          .edit().putString("raw", o.toString()).apply();
    } catch (Exception ignored) { /* 缓存失败无所谓，下次重新拉 */ }
  }

  /**
   * 读上次的快照。读不到或解析失败一律返回 {@code null}（调用方按"没有缓存"处理）。
   *
   * [DFW-103] 用户反馈"公告弹窗弹出太慢"——根因是启动后要**等一次完整网络往返**才弹。
   * 有了它，第二次以后打开软件是**零延迟**弹出来的（先弹上次的，网络回来再补新的）。
   */
  static Snapshot loadCache(android.content.Context ctx) {
    if (ctx == null) return null;
    try {
      String raw = ctx.getSharedPreferences(CACHE_PREFS, android.content.Context.MODE_PRIVATE)
          .getString("raw", "");
      if (raw == null || raw.isEmpty()) return null;
      JSONObject o = new JSONObject(raw);

      Control control = Control.normal();
      JSONArray ctl = o.optJSONArray("control");
      if (ctl != null && ctl.length() > 0) control = parseControl(ctl.getJSONObject(0));

      Release release = null;
      JSONArray rel = o.optJSONArray("release");
      if (rel != null && rel.length() > 0) release = parseRelease(rel.getJSONObject(0));

      JSONArray notice = o.optJSONArray("notice");
      JSONArray changelog = o.optJSONArray("changelog");
      /* 缓存是按集合合并写的（见 storeCache）：**notice 这个 key 存在**才说明"我们真的拿到过公告列表"
         （哪怕是空数组，也代表后台当时确实没有公告）。key 不存在 = 从没成功拉到过 →
         noticeOk=false，让 NoticeCenter 不要动已读集合。 */
      return new Snapshot(true, notice != null, control, release,
          parseNotices(notice == null ? new JSONArray() : notice),
          parseChangelogs(changelog == null ? new JSONArray() : changelog));
    } catch (Exception ignored) {
      return null;
    }
  }

  /**
   * [2026-10-03] **短时去重：同一进程里 {@link #FETCH_MEMO_MS} 毫秒内的多次 `fetch()` 只打一次网络。**
   *
   * ## 为什么需要（2026-10-03 对抗性复查的发现）
   * 冷启动时有**三路**各自调 `fetch()`：
   *   · `MainActivity.maybeCheckForUpdates`（有 60 秒落盘节流）
   *   · `MainActivity.maybeEnterMaintenance`（**无**节流）
   *   · `MainActivity.maybeFetchNotices`（**无**节流）
   * 而一次 `fetch()` 要拉 **4 个集合** ⇒ 每次冷启动最多 **12 个请求**打同一台 2核2G 的 VPS。
   *
   * 复查同时指出：那次提交里写的「把最坏情况下的请求量钉在每分钟一次」**只覆盖了 1/3** ——
   * 另外两路完全没节流。这里从**源头**去重，比给每一路各加一个节流更彻底。
   *
   * ## 为什么是"短时记忆"而不是"启动时拉一次再分发"
   * 后者要改三个调用方的时序（谁先谁后、失败了怎么办），改动面大。
   * 短时记忆**对调用方完全透明**：谁先到谁发请求，后面的直接复用。
   *
   * ## 为什么失败**不**缓存
   * 只缓存 `anyOk`（这一轮真的拿到了东西）的结果。全失败时不留记忆 ——
   * 否则一次网络抖动会被放大成"接下来 3 秒连重试都不会发生"。
   *
   * 3 秒是拍的值：要盖住"冷启动三路几乎同时发"这个窗口，又不能长到让手动刷新拿到旧数据。
   */
  private static final long FETCH_MEMO_MS = 3000L;
  private static final Object FETCH_MEMO_LOCK = new Object();
  private static Snapshot fetchMemoSnapshot;
  private static Raw fetchMemoRaw;
  private static long fetchMemoAt;

  /**
   * **测试接缝**：非 null 时 `get()` 直接用它返回，既不校验 `isJvmUnitTest()` 也不打网络。
   * 生产代码里**永远是 null**。
   *
   * ## 为什么值得在关键路径上开这个口子
   * `fetch()` 是**全部远程读取的唯一咽喉**（更新 / 维护拦截 / 公告 / 更新记录都走它），
   * 但在 2026-10-03 之前它**一条测试都没有** —— 因为 `get()` 里那道
   * `App.isJvmUnitTest()` 守卫让它在单元测试里必然抛异常。
   *
   * 于是"改了这个咽喉，但没法证明改对了"成了常态。上面那个 `FETCH_MEMO_MS` 去重
   * 就是必须能验的一例：它一旦写错（比如把失败也缓存），后果是
   * **一次网络抖动被放大成"接下来 3 秒连重试都不会发生"**，而线上完全看不出来。
   *
   * 有这个接缝，才第一次能给 `fetch()` 写真正的行为测试。
   */
  static java.util.function.Function<String, JSONObject> testResponder;

  /** 测试用：清掉去重记忆。生产不调用。 */
  static void resetFetchMemoForTest() {
    synchronized (FETCH_MEMO_LOCK) {
      fetchMemoSnapshot = null;
      fetchMemoRaw = null;
      fetchMemoAt = 0L;
    }
  }

  private static Raw copyRaw(Raw from, Raw to) {
    to.control = from.control;
    to.release = from.release;
    to.notice = from.notice;
    to.changelog = from.changelog;
    return to;
  }

  static Snapshot fetch(Raw sink) {
    synchronized (FETCH_MEMO_LOCK) {
      if (fetchMemoSnapshot != null && System.currentTimeMillis() - fetchMemoAt < FETCH_MEMO_MS) {
        if (sink != null && fetchMemoRaw != null) copyRaw(fetchMemoRaw, sink);
        return fetchMemoSnapshot;
      }
    }
    /*
     * 内部**始终**用一个本地 Raw 收集原始 JSON —— 不依赖调用方有没有传 sink。
     *
     * ⚠️ 这个坑是写测试时**实测撞出来的**：第一版只在 `sink != null` 时收集，
     * 于是"第一个调用者不传 sink"（更新检查就是）会把记忆存成 `null` Raw，
     * 后面传 sink 的调用者（公告，要用它落盘缓存）命中记忆时**什么都拿不到** ——
     * 表现为"公告能显示，但下次启动没有离线缓存"，**线上完全看不出来**。
     */
    Raw collected = new Raw();
    Control control = Control.normal();
    Release release = null;
    List<Notice> notices = Collections.emptyList();
    List<Changelog> changelogs = Collections.emptyList();
    boolean anyOk = false;
    /* [2026-10-03] 「公告这一路」自己成功没有 —— 与 anyOk 分开记。
       两者必须分开：anyOk 是"任意一路成功"（给 reachable 用，决定要不要落盘缓存），
       noticeOk 是"公告这一路成功"（给 NoticeCenter 用，决定敢不敢清理已读集合）。 */
    boolean noticeOk = false;

    try {
      JSONArray items = items("control");
      collected.control = items;
      if (items.length() > 0) { control = parseControl(items.getJSONObject(0)); anyOk = true; }
    } catch (Exception ignored) { /* fail-open：保持 normal */ }

    try {
      JSONArray items = items("release");
      collected.release = items;
      if (items.length() > 0) {
        Release parsed = parseRelease(items.getJSONObject(0));
        if (parsed != null) { release = parsed; anyOk = true; }
      }
    } catch (Exception ignored) { /* 更新检查失败不影响公告 */ }

    try {
      JSONArray items = items("notice");
      collected.notice = items;
      notices = parseNotices(items);
      anyOk = true;
      /* 只有**这一路真的走完**才置位。下面 catch 吞掉的失败路径不置位 ——
         这正是"公告接口抖一下 → 已读集合被清空 → 已读公告重复弹"的开关。 */
      noticeOk = true;
    } catch (Exception ignored) { /* 公告失败不影响更新 */ }

    try {
      JSONArray items = items("changelog");
      collected.changelog = items;
      changelogs = parseChangelogs(items);
      anyOk = true;
    } catch (Exception ignored) { /* 更新记录失败不影响其它 */ }

    Snapshot snapshot = new Snapshot(anyOk, noticeOk, control, release, notices, changelogs);
    /*
     * 只缓存"这一轮真的拿到了东西"的结果。全失败时**不留记忆** ——
     * 否则一次网络抖动会被放大成"接下来 3 秒连重试都不会发生"。
     */
    if (sink != null) copyRaw(collected, sink);
    if (anyOk) {
      synchronized (FETCH_MEMO_LOCK) {
        fetchMemoSnapshot = snapshot;
        fetchMemoRaw = copyRaw(collected, new Raw());
        fetchMemoAt = System.currentTimeMillis();
      }
    }
    return snapshot;
  }

  /** 取某集合的记录数组（只看启用项，按后台排序）。 */
  private static JSONArray items(String collection) throws IOException {
    // 注意：PocketBase 0.40 的 `created` **不是可排序字段**，用它排序会返回 400
    // （实测 sort=-created / sort=created 都是 400，sort=-id 才是 200）。
    // 这个坑很隐蔽：单条写入成功（200），但列表查询静默失败——表现为"发布成功但看不到"。
    JSONObject body = get(API + collection + "/records?perPage=200&sort=-id");
    JSONArray items = body.optJSONArray("items");
    return items == null ? new JSONArray() : items;
  }

  private static Control parseControl(JSONObject o) {
    return new Control(
        o.optBoolean("maintenanceOn", false),
        o.optBoolean("blocked", false),
        o.optString("maintenanceTitle", ""),
        o.optString("maintenanceBody", ""),
        o.optString("maintenanceUntil", ""),
        // [DFW-73] 内置渠道远程覆盖（后台字段缺省 = 空 → 沿用 APK 内置值，fail-open）
        o.optString("ai_base_url", ""),
        o.optString("ai_token", ""),
        o.optString("ai_model", ""),
        // [DFW-77] 显示名与请求路径：后台控制台新增的两个字段（老后台没这两列时取空串，行为不变）
        o.optString("ai_display_name", ""),
        o.optString("ai_chat_path", ""),
        o.optInt("ai_max_tokens", 0),
        o.optBoolean("ai_disabled", false));
  }

  private static Release parseRelease(JSONObject o) {
    String versionName = o.optString("versionName", "").trim();
    String apkUrl = o.optString("apkUrl", "").trim();
    String sha256 = o.optString("sha256", "").trim();
    long size = o.optLong("size", 0L);
    // 缺关键字段的记录视为无效（绝不拿一条半残配置去提示更新）
    if (versionName.isEmpty() || apkUrl.isEmpty()) return null;
    if (!sha256.matches("[0-9a-fA-F]{64}")) return null;
    if (size <= 0) return null;
    return new Release(versionName, o.optLong("versionCode", 0L), apkUrl, sha256, size,
        o.optString("updateMode", "soft"));
  }

  private static List<Notice> parseNotices(JSONArray array) {
    List<Notice> out = new ArrayList<>();
    long now = System.currentTimeMillis();
    for (int i = 0; i < array.length(); i++) {
      JSONObject o = array.optJSONObject(i);
      if (o == null) continue;
      if (!o.optBoolean("enabled", true)) continue;
      String title = o.optString("title", "").trim();
      String body = o.optString("body", "").trim();
      if (title.isEmpty() && body.isEmpty()) continue;
      // 优先读新字段 popupMode；后台还没迁移时回落到旧的 popup 布尔（见 Notice 类注释）。
      //
      // **必须区分"字段不存在"与"字段存在但为空"**（本逻辑第一版把两者混为一谈，被测试抓出来）：
      // - `popupMode` **存在**（哪怕为空）→ 认它是新数据，空值交给 normalizeMode 回落到"一次性"；
      // - `popupMode` **不存在**但 `popup` 存在 → 认它是迁移前的旧数据，按布尔映射；
      // - 两个都不存在 → 也是"一次性"（宁可多弹一次，也不能因为后台漏填就把公告全吞掉）。
      String mode;
      if (o.has("popupMode")) {
        mode = o.optString("popupMode", "").trim();
      } else if (o.has("popup")) {
        mode = o.optBoolean("popup", false) ? Notice.MODE_ONCE : Notice.MODE_SILENT;
      } else {
        mode = "";
      }
      out.add(new Notice(o.optString("id", ""), title, body, o.optString("level", "normal"),
          o.optBoolean("pinned", false), mode, createdMs(o, now)));
    }
    // 置顶优先，其余保持后台顺序
    List<Notice> pinned = new ArrayList<>(), rest = new ArrayList<>();
    for (Notice n : out) (n.pinned ? pinned : rest).add(n);
    pinned.addAll(rest);
    return pinned;
  }

  private static List<Changelog> parseChangelogs(JSONArray array) {
    List<Changelog> out = new ArrayList<>();
    for (int i = 0; i < array.length(); i++) {
      JSONObject o = array.optJSONObject(i);
      if (o == null) continue;
      if (!o.optBoolean("enabled", true)) continue;
      String version = o.optString("versionName", "").trim();
      String highlights = o.optString("highlights", "").trim();
      if (version.isEmpty() && highlights.isEmpty()) continue;
      out.add(new Changelog(o.optString("id", ""), version, o.optString("date", ""), highlights));
    }
    return out;
  }

  /** PocketBase 的 created 形如 `2026-09-30 13:37:18.123Z`；解析失败则退化为"现在"。 */
  private static long createdMs(JSONObject o, long fallback) {
    try {
      String raw = o.optString("created", "");
      if (raw.isEmpty()) return fallback;
      String iso = raw.replace(' ', 'T');
      if (!iso.endsWith("Z")) iso = iso + "Z";
      java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
      fmt.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
      java.util.Date d = fmt.parse(iso);
      return d == null ? fallback : d.getTime();
    } catch (Exception ignored) {
      return fallback;
    }
  }

  /** 简单 GET → JSON。固定 host，不跟随跨域跳转（与 UpdateClient 同款保守策略）。 */
  private static JSONObject get(String endpoint) throws IOException {
    /*
     * [DFW-124 2026-10-02] 单元测试里**不发真实网络请求**，直接当作"服务器不可达"。
     *
     * 这里是全部远程读取的**唯一咽喉**：`fetch()` / `fetch(Raw)` 都经此。
     * 不在这里拦的话，每一个创建 `MainActivity` 的 Robolectric 用例都会**真的打生产服务器**——
     * `MainActivity:305/309/310` 那三个 `ui.post` 启动任务（查更新 / 维护 / 公告）都是异步的，
     * 回调什么时候落地取决于本机网速，于是：
     *
     *   - **测试不确定**：同一份代码连跑会随机红（DFW-113 记录的就是这个）；
     *   - 回调会**改被测对象的字段**：`maybeFetchNotices` 的网络回调会走到
     *     `maybePopupNotices()` → `showNoticeDialog()` → 覆盖 `MainActivity.noticeDialog`，
     *     于是 `assertNull(a.noticeDialog)` 在"请求刚好这时回来"时必然失败
     *     （2026-10-02 实测复现：`NoticeFlowJvmTest.kt:135`，约 1/6 概率）；
     *   - **测试有副作用**：往生产服务器发请求，还会被服务器上的真实数据牵着走
     *     （线上真有一条 `popupMode=once` 的公告，会主动弹窗）。
     *
     * 抛 `IOException` 而不是返回 null，是为了让调用方走它们**本来就有的失败分支**：
     * 这条链路每一层都是 fail-open（`catch (Exception ignored)` 后按"拉不到"处理），
     * 所以测试看到的就是线上"服务器挂了"的确定性行为，语义与生产完全一致。
     *
     * 判据用「Robolectric 在不在 classpath 上」（同 `App.isJvmUnitTest()`）：
     * 发布 APK 里没有 Robolectric，恒为 false，不需要开关，也不引入任何依赖。
     */
    if (testResponder != null) return testResponder.apply(endpoint);
    if (App.isJvmUnitTest()) throw new IOException("单元测试不发真实网络请求（DFW-124）");
    URL url = new URL(endpoint);
    String expectedHost = url.getHost().toLowerCase(Locale.ROOT);
    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
    try {
      connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
      connection.setReadTimeout(READ_TIMEOUT_MS);
      connection.setInstanceFollowRedirects(false);
      connection.setRequestProperty("User-Agent", "DongfangWuxian-Remote");
      connection.setRequestProperty("Accept", "application/json");
      connection.setRequestProperty("Accept-Encoding", "identity");
      int code = connection.getResponseCode();
      if (code != HttpURLConnection.HTTP_OK) throw new IOException("后台请求失败 HTTP " + code);
      // 双保险：确认没有跳到别的 host（防 DNS/中间人把配置引流）
      URL finalUrl = connection.getURL();
      if (finalUrl != null && !expectedHost.equals(finalUrl.getHost().toLowerCase(Locale.ROOT))) {
        throw new IOException("后台地址被重定向到不可信主机");
      }
      long length = connection.getContentLengthLong();
      if (length > JSON_LIMIT) throw new IOException("后台响应过大");
      try (InputStream input = connection.getInputStream();
           ByteArrayOutputStream output = new ByteArrayOutputStream(length > 0 ? (int) length : 4096)) {
        byte[] buffer = new byte[4096];
        int total = 0;
        for (int count; (count = input.read(buffer)) > 0; ) {
          total += count;
          if (total > JSON_LIMIT) throw new IOException("后台响应过大");
          output.write(buffer, 0, count);
        }
        return new JSONObject(new String(output.toByteArray(), StandardCharsets.UTF_8));
      } catch (org.json.JSONException error) {
        throw new IOException("后台响应格式无效", error);
      }
    } finally {
      connection.disconnect();
    }
  }
}
