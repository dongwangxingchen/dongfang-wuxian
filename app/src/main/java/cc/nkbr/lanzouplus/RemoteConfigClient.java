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
 * 从用户自建后台（PocketBase，`https://39.106.33.135/pb/`）读取四类数据：
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
  private static final int CONNECT_TIMEOUT_MS = 6000;
  private static final int READ_TIMEOUT_MS = 8000;
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

    Control(boolean maintenanceOn, boolean blocked, String title, String body, String until) {
      this.maintenanceOn = maintenanceOn;
      this.blocked = blocked;
      this.maintenanceTitle = title == null ? "" : title.trim();
      this.maintenanceBody = body == null ? "" : body.trim();
      this.maintenanceUntil = until == null ? "" : until.trim();
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

  /** 一条公告。 */
  static final class Notice {
    final String id, title, body, level;
    final boolean pinned, popup;
    final long createdMs;

    Notice(String id, String title, String body, String level, boolean pinned, boolean popup, long createdMs) {
      this.id = id == null ? "" : id;
      this.title = title == null ? "" : title.trim();
      this.body = body == null ? "" : body.trim();
      this.level = level == null ? "normal" : level.trim();
      this.pinned = pinned;
      this.popup = popup;
      this.createdMs = createdMs;
    }

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
    private final Control control;
    private final Release release;
    private final List<Notice> notices;
    private final List<Changelog> changelogs;

    Snapshot(boolean reachable, Control control, Release release, List<Notice> notices, List<Changelog> changelogs) {
      this.reachable = reachable;
      this.control = control;
      this.release = release;
      this.notices = notices == null ? Collections.emptyList() : notices;
      this.changelogs = changelogs == null ? Collections.emptyList() : changelogs;
    }

    static Snapshot unavailable() {
      return new Snapshot(false, Control.normal(), null, Collections.emptyList(), Collections.emptyList());
    }

    Control control() { return control == null ? Control.normal() : control; }
    Release release() { return release; }
    List<Notice> notices() { return notices; }
    List<Changelog> changelogs() { return changelogs; }

    /** 需要弹窗的公告（后台勾了 popup）。 */
    List<Notice> popupNotices() {
      List<Notice> out = new ArrayList<>();
      for (Notice n : notices) if (n.popup) out.add(n);
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
  static Snapshot fetch() {
    Control control = Control.normal();
    Release release = null;
    List<Notice> notices = Collections.emptyList();
    List<Changelog> changelogs = Collections.emptyList();
    boolean anyOk = false;

    try {
      JSONArray items = items("control");
      if (items.length() > 0) { control = parseControl(items.getJSONObject(0)); anyOk = true; }
    } catch (Exception ignored) { /* fail-open：保持 normal */ }

    try {
      JSONArray items = items("release");
      if (items.length() > 0) {
        Release parsed = parseRelease(items.getJSONObject(0));
        if (parsed != null) { release = parsed; anyOk = true; }
      }
    } catch (Exception ignored) { /* 更新检查失败不影响公告 */ }

    try {
      notices = parseNotices(items("notice"));
      anyOk = true;
    } catch (Exception ignored) { /* 公告失败不影响更新 */ }

    try {
      changelogs = parseChangelogs(items("changelog"));
      anyOk = true;
    } catch (Exception ignored) { /* 更新记录失败不影响其它 */ }

    return new Snapshot(anyOk, control, release, notices, changelogs);
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
        o.optString("maintenanceUntil", ""));
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
      out.add(new Notice(o.optString("id", ""), title, body, o.optString("level", "normal"),
          o.optBoolean("pinned", false), o.optBoolean("popup", false), createdMs(o, now)));
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
    URL url = new URL(endpoint);
    String expectedHost = url.getHost().toLowerCase(Locale.ROOT);
    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
    try {
      connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
      connection.setReadTimeout(READ_TIMEOUT_MS);
      connection.setInstanceFollowRedirects(false);
      connection.setRequestProperty("User-Agent", "LanzouPlus-Remote");
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
