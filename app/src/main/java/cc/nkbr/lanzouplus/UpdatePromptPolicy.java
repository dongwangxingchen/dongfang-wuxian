package cc.nkbr.lanzouplus;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * [DFWX] DFW-59：**更新提示的取舍策略**——"这一版该不该弹、弹了给哪几个按钮"。
 *
 * ## 责任边界
 * 只回答策略问题，不碰网络、不碰 UI、不下载：
 *   ① 后台给的三档模式（软更新 / 强制更新 / 关闭）各自该给哪些按钮；
 *   ② "不再显示"只对**该具体版本**生效（换版本照弹），并负责它的落盘记忆；
 *   ③ 同一版本在该模式下是否应该弹窗。
 * 真正弹窗、下载、校验、安装仍在 {@link MainActivity}（与 DFW-7/DFW-59 的分工一致）。
 *
 * ## 为什么单独成类
 * 用户对这块的要求是**分档且可叠加**的（"软更新 / 强制更新，用户可控"），
 * 而"强制模式下不能有取消"这种事，如果内联在 `MainActivity` 里，
 * 就只能靠启动 Activity + 翻对话框按钮来间接验证，且**漏一个按钮很难被发现**。
 * 抽出来之后可以直接对模式 × 记忆的组合写真值表。
 *
 * ## 与 DFW-56 的关系
 * DFW-56 把版本比较改成了 **versionCode 优先**（versionName 只用于显示）。
 * 本类沿用该结论：判断"是否同一版本"用 versionCode，避免
 * "改了 versionName 却漏改 versionCode" 时把同一版反复当新版弹。
 */
final class UpdatePromptPolicy {

  /** 后台 `release.updateMode` 的三档。 */
  enum Mode {
    /** 软更新：四个按钮齐全，可取消、可不再显示。 */
    SOFT,
    /** 强制更新：只有「立即更新」+「其他下载方式」，**无取消、无不再显示**。 */
    FORCE,
    /** 关闭：完全不弹（后台临时召回某版时用）。 */
    OFF;

    /** 未知/空值一律按软更新处理——后台字段写错时**不能**把用户锁死在强制更新里。 */
    static Mode parse(String raw) {
      if (raw == null) return SOFT;
      String value = raw.trim().toLowerCase(java.util.Locale.ROOT);
      if ("force".equals(value)) return FORCE;
      if ("off".equals(value)) return OFF;
      return SOFT;
    }
  }

  /** 弹窗上该出现哪些按钮。强制模式下取消与"不再显示"必须缺席。 */
  static final class Buttons {
    final boolean cancel;
    final boolean neverRemind;
    final boolean alwaysUpdate;

    Buttons(boolean cancel, boolean neverRemind, boolean alwaysUpdate) {
      this.cancel = cancel;
      this.neverRemind = neverRemind;
      this.alwaysUpdate = alwaysUpdate;
    }
  }

  /** 偏好存储抽象：生产用 SharedPreferences，测试用内存实现。 */
  interface Store {
    /** 用户点过"不再显示"的版本号（versionCode）；0 表示没有。 */
    long dismissedVersionCode();
    void setDismissedVersionCode(long versionCode);
  }

  private final Store store;

  UpdatePromptPolicy(Store store) { this.store = store; }

  /** 生产构造：绑定 `update_check` 偏好的 `dismissed_version_code`。 */
  static UpdatePromptPolicy forContext(Context context) {
    return new UpdatePromptPolicy(new Store() {
      private SharedPreferences prefs() {
        return context.getSharedPreferences(MainActivity.UPDATE_CHECK_PREFS, Context.MODE_PRIVATE);
      }
      @Override public long dismissedVersionCode() {
        try { return prefs().getLong("dismissed_version_code", 0L); } catch (Exception error) { return 0L; }
      }
      @Override public void setDismissedVersionCode(long versionCode) {
        try { prefs().edit().putLong("dismissed_version_code", versionCode).apply(); }
        catch (Exception ignored) { /* 记不上只是可能多弹一次，不该影响使用 */ }
      }
    });
  }

  /**
   * 该版本现在是否应该弹窗。
   *
   * @param mode         后台模式
   * @param versionCode  后台声明的目标版本
   * @param currentCode  已安装版本的 versionCode
   *
   * 三条拒绝条件：模式为 off；目标不比当前新；**软更新下**用户已对该版本点过"不再显示"。
   * 强制更新**无视**"不再显示"——这是刻意的：既然标注为强制，就不能被一个历史点击永久绕过，
   * 否则"强制"名不副实（用户自己把开关调到 force，就是想让它必须更新）。
   */
  boolean shouldPrompt(Mode mode, long versionCode, long currentCode) {
    if (mode == Mode.OFF) return false;
    if (versionCode <= currentCode) return false;
    if (mode == Mode.FORCE) return true;
    return store.dismissedVersionCode() != versionCode;
  }

  /**
   * 用户点了"不再显示"：只记住**这一版**，换版本照弹。
   *
   * 这里的 try/catch 是刻意的防御：生产实现（{@link #forContext}）自己已吞掉偏好读写异常，
   * 但本类是可被独立构造的（测试、将来的其它存储），而**点"不再显示"绝不该崩**——
   * 记不住大不了下次再弹一次，代价是轻微打扰；抛出去则是崩溃。
   */
  void rememberDismissed(long versionCode) {
    try {
      store.setDismissedVersionCode(versionCode);
    } catch (Exception ignored) { /* 记不上只是可能多弹一次 */ }
  }

  /** 清掉记忆（例如用户手动检查更新时，应无视历史上的"不再显示"）。 */
  void clearDismissed() {
    try {
      store.setDismissedVersionCode(0L);
    } catch (Exception ignored) { /* 同上 */ }
  }

  long dismissedVersionCode() { return store.dismissedVersionCode(); }

  /** 按模式给出按钮集合。 */
  static Buttons buttonsFor(Mode mode) {
    if (mode == Mode.FORCE) return new Buttons(false, false, true);
    if (mode == Mode.OFF) return new Buttons(false, false, false);
    return new Buttons(true, true, true);
  }
}
