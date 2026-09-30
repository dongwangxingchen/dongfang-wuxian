package cc.nkbr.lanzouplus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * [DFWX] DFW-59：**更新供给的统一表示**——把两个来源（自有后台 / GitHub）归一成同一种东西。
 *
 * ## 为什么要归一
 * 用户要求"自有服务器优先，GitHub 兜底"。两个来源字段名、版本比较方式、甚至"有没有 sha256"
 * 都不一样（后台给 `sha256`+`size`，GitHub 只给 `digest`+asset 元信息）。
 * 若让 UI 直接分头处理，就会在**弹窗里长出两套 if/else**，而"到底该显示哪个版本号、
 * 哪个按钮给哪个链接"这类判断会散落各处——正是本项目 v1.22.8 自研动画事故的同一类错误。
 * 所以先在这里归一，UI 只认一个类型。
 *
 * ## 版本比较
 * 沿用 DFW-56 的结论：**比较用 versionCode，显示用 versionName**。
 * 只比 versionName 会在"改了名字漏改 code"时把同一版反复当新版弹（DFW-56 守卫的正是这个）。
 * GitHub 侧只有 tag（形如 v1.0.1），所以由 {@link #codeFromName} 换算。
 */
final class UpdateOffer {

  final String versionName;
  final long versionCode;
  final long size;
  /** `sha256:<64 hex>`；为空表示**来源没给摘要**——此时不得安装（见 {@link #installable()}）。 */
  final String digest;
  /** 更新内容（后台 changelog 或 GitHub release body）。 */
  final String body;
  /** 主下载地址（自有服务器，国内直连）。 */
  final String primaryUrl;
  /** 兜底下载地址（GitHub 资产）。 */
  final String fallbackUrl;
  /** 「其他下载方式」里的 GitHub 发布页。 */
  final String githubPage;
  /** 「其他下载方式」里的第三方页面（后台可填，留空则该项不显示）。 */
  final String mirrorPage;
  final UpdatePromptPolicy.Mode mode;

  UpdateOffer(String versionName, long versionCode, long size, String digest, String body,
              String primaryUrl, String fallbackUrl, String githubPage, String mirrorPage,
              UpdatePromptPolicy.Mode mode) {
    this.versionName = versionName == null ? "" : versionName;
    this.versionCode = versionCode;
    this.size = size;
    this.digest = digest == null ? "" : digest;
    this.body = body == null ? "" : body;
    this.primaryUrl = primaryUrl == null ? "" : primaryUrl;
    this.fallbackUrl = fallbackUrl == null ? "" : fallbackUrl;
    this.githubPage = githubPage == null ? "" : githubPage;
    this.mirrorPage = mirrorPage == null ? "" : mirrorPage;
    this.mode = mode == null ? UpdatePromptPolicy.Mode.SOFT : mode;
  }

  /**
   * 能否走"App 内下载 + 三重校验"这条路。
   *
   * **摘要缺失一律拒绝**：三重校验的第一重就是 sha256，没有摘要就无法证明下到的包是完整的。
   * 宁可不给这个按钮，也不能装一个校验不了的包。
   */
  boolean installable() {
    return !primaryUrl.isEmpty() && !digest.isEmpty() && size > 0 && versionCode > 0;
  }

  /** 至少得有一个能点的下载去处，否则这个弹窗不该出现（点哪个都没用等于骗用户）。 */
  boolean hasAnyDownload() {
    return installable() || !fallbackUrl.isEmpty() || !githubPage.isEmpty() || !mirrorPage.isEmpty();
  }

  /** 大小文本；未知大小返回空串（不显示"0 B"这种误导信息）。 */
  String sizeText() {
    if (size <= 0) return "";
    String[] units = {"B", "KB", "MB", "GB", "TB"};
    double value = size;
    int unit = 0;
    while (value >= 1024 && unit < units.length - 1) { value /= 1024; unit++; }
    return String.format(java.util.Locale.ROOT, value >= 100 ? "%.0f %s" : "%.1f %s", value, units[unit]);
  }

  /**
   * 「其他下载方式」第二层的条目。
   *
   * 顺序即推荐顺序：GitHub 页放前面但**标注"需科学上网"**——国内直连不通是事实，
   * 不标注会让用户点了打不开还以为软件坏了。第三方页（后台填的 FlowUs 等）留空则整项不出现，
   * 这样用户在后台填了就自动多一个渠道，不用改代码。
   */
  List<Alt> alternates() {
    List<Alt> out = new ArrayList<>();
    if (!githubPage.isEmpty()) out.add(new Alt("GitHub 发布页（需科学上网）", githubPage));
    if (!mirrorPage.isEmpty()) out.add(new Alt("其他下载页", mirrorPage));
    if (!fallbackUrl.isEmpty()) out.add(new Alt("GitHub 直链（需科学上网）", fallbackUrl));
    return Collections.unmodifiableList(out);
  }

  /** 第二层的一个下载去处。 */
  static final class Alt {
    final String label;
    final String url;
    Alt(String label, String url) { this.label = label; this.url = url; }
  }

  // ── 来源归一 ────────────────────────────────────────────────────────────

  /**
   * 自有后台 → 供给。后台的 versionCode 直接可用（DFW-56 已让后台维护它）。
   * 后台缺 versionCode（老数据）时用 versionName 换算兜底，而不是当成 0 丢掉——
   * 否则用户会看到"后台有新版但软件死活不提示"。
   */
  static UpdateOffer fromRemote(RemoteConfigClient.Release release, String body, String githubPage, String mirrorPage) {
    if (release == null) return null;
    long code = release.versionCode > 0 ? release.versionCode : codeFromName(release.versionName);
    return new UpdateOffer(
        release.versionName, code, release.size, normalizeDigest(release.sha256), body,
        release.apkUrl, "", githubPage, mirrorPage, UpdatePromptPolicy.Mode.parse(release.updateMode));
  }

  /** GitHub → 供给。GitHub 没有"模式"概念，一律软更新（可取消、可不再显示）。 */
  static UpdateOffer fromGithub(UpdateClient.UpdateInfo info, String githubPage) {
    if (info == null) return null;
    return new UpdateOffer(
        info.version, codeFromName(info.version), info.size, normalizeDigest(info.digest), info.body,
        info.primaryUrl(), info.fallbackUrl(), githubPage, "",
        UpdatePromptPolicy.Mode.SOFT);
  }

  /**
   * 统一摘要格式为 `sha256:<64 hex>`。
   *
   * **这是两个来源的格式差异，不统一就会静默失效**：
   * - 后台 `release.sha256` 存的是**裸 64 位 hex**（`RemoteConfigClient` 就是这么校验的）；
   * - GitHub 的 `digest` 是**带 `sha256:` 前缀**的形式（`UpdateClient` 就是这么校验的）；
   * - 而三重校验里的比对写的是 `("sha256:"+hex(...)).equals(info.digest)`。
   *
   * 不做归一的话，**自有服务器下到的包会 100% 校验失败**（"更新包摘要不一致"）——
   * 下载看着成功、装永远装不上。这是"两个来源各自都对、拼起来错"的典型，必须有测试钉住。
   */
  static String normalizeDigest(String raw) {
    if (raw == null) return "";
    String value = raw.trim().toLowerCase(java.util.Locale.ROOT);
    if (value.isEmpty()) return "";
    return value.startsWith("sha256:") ? value : "sha256:" + value;
  }

  /**
   * `1.2.3` → `10203`，与 `app/build.gradle.kts` 的编号规则一致（major*10000+minor*100+patch）。
   * 解析不了返回 0（调用方据此判断"这个来源不可用"，而不是当成 0 版）。
   */
  static long codeFromName(String versionName) {
    if (versionName == null) return 0L;
    String value = versionName.trim();
    if (value.startsWith("v") || value.startsWith("V")) value = value.substring(1);
    String[] parts = value.split("\\.");
    if (parts.length != 3) return 0L;
    long[] numbers = new long[3];
    for (int i = 0; i < 3; i++) {
      try {
        numbers[i] = Long.parseLong(parts[i].trim());
      } catch (NumberFormatException error) {
        return 0L;
      }
      if (numbers[i] < 0) return 0L;
    }
    return numbers[0] * 10000L + numbers[1] * 100L + numbers[2];
  }
}
