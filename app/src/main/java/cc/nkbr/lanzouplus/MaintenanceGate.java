package cc.nkbr.lanzouplus;

/**
 * [DFWX] DFW-60：**维护 / 停更拦截的取舍策略**——"这次该不该拦、拦了显示什么、后门怎么开"。
 *
 * ## 用户要求（2026-09-30 原话）
 * > "只显示公告，直接没有按钮，而且点击空白处也不能关闭，就是纯粹的通知"
 * > "维护时间用单独的 UI 界面显示…甚至我可以写上永久"
 * > "隐藏后门：连点版本号 7 次可跳过 → 防止误开后用户和你自己都被锁在外面"
 *
 * ## 责任边界
 * 只回答策略，不碰 UI、不碰网络：拦不拦、显示哪几块、后门开没开。
 * 真正画全屏页、吃掉返回键仍在 {@link MainActivity}。
 *
 * ## 为什么单独成类
 * 这里有三条**方向相反**的诉求，必须同时成立，靠 UI 代码里的 if/else 很难验：
 *   ① 要能真的拦住（后台开了就必须拦，否则维护是摆设）；
 *   ② 又不能把用户和自己**永久锁死**（服务器故障、后台误开）；
 *   ③ 所以既要 fail-open，又要一个不写在界面上的后门。
 * 三者是真值表，适合直接测。
 */
final class MaintenanceGate {

  /** 隐藏后门：连点版本号多少次解锁。用户指定 7 次。 */
  static final int BACKDOOR_TAPS = 7;

  /**
   * 本次会话是否已被后门解锁。
   *
   * **刻意只存内存、不落盘**：落盘的话后门就从"应急逃生口"变成"永久绕过开关"，
   * 下次启动也不再拦——那等于维护功能作废。放内存意味着重启 App 恢复拦截，
   * 既能自救，又不会被长期利用。
   */
  private boolean unlockedThisSession;
  private int taps;

  /** 一次拦截的展示内容。 */
  static final class Screen {
    final boolean block;
    final String title;
    final String body;
    /** "维护时间"区块的文本；空串表示**不显示该区块**（用户要求：留空就不显示）。 */
    final String untilText;

    Screen(boolean block, String title, String body, String untilText) {
      this.block = block;
      this.title = title == null ? "" : title;
      this.body = body == null ? "" : body;
      this.untilText = untilText == null ? "" : untilText;
    }
  }

  /**
   * 决定这次要不要拦、显示什么。
   *
   * **fail-open 是这里的核心裁决**：`control` 为 null 或 `reachable=false` 时一律**不拦**。
   * 后台不可达时若按"维护中"处理，服务器一挂全体用户就进不去 App——把可用性押在一个
   * 2C2G 的小服务器上，代价远大于"维护公告晚显示几分钟"。
   */
  Screen decide(RemoteConfigClient.Snapshot snapshot) {
    RemoteConfigClient.Control control = snapshot == null ? null : snapshot.control();
    if (control == null) return new Screen(false, "", "", "");
    if (unlockedThisSession) return new Screen(false, "", "", "");
    if (!control.blocksApp()) return new Screen(false, "", "", "");
    // 后台可能只开了开关却没填文案。此时给一句兜底，否则用户看到全黑屏会以为 App 坏了。
    String title = control.maintenanceTitle.isEmpty() ? "暂时无法使用" : control.maintenanceTitle;
    String body = control.maintenanceBody.isEmpty()
        ? "我们正在处理，请稍后再试。"
        : control.maintenanceBody;
    return new Screen(true, title, body, untilText(control.maintenanceUntil));
  }

  /**
   * "维护时间"区块的显示文本。
   *
   * 用户要求"留空不显示；填永久就显示永久"。这里**原样透传**用户填的内容而不做日期解析：
   * 他是人手填的自由文本（"永久"、"10月1日 20:00"、"等通知"…），
   * 任何自作聪明的解析都会把"永久"这种词弄坏——用户明确说了他可以写"永久"。
   */
  static String untilText(String maintenanceUntil) {
    if (maintenanceUntil == null) return "";
    String value = maintenanceUntil.trim();
    return value.isEmpty() ? "" : value;
  }

  /**
   * 记录一次对版本号的点击。返回**本次点击是否恰好触发了后门解锁**。
   *
   * 只在真正达到阈值的那一次返回 true，避免调用方每点一次都弹一次提示。
   * 超过阈值后继续点不再重复触发（解锁已生效）。
   */
  boolean tapVersion() {
    if (unlockedThisSession) return false;
    taps++;
    if (taps >= BACKDOOR_TAPS) {
      unlockedThisSession = true;
      return true;
    }
    return false;
  }

  boolean unlocked() { return unlockedThisSession; }

  /** 供测试与"重新检查"用：把后门状态清回未解锁。 */
  void resetBackdoor() {
    unlockedThisSession = false;
    taps = 0;
  }

  /** 距离解锁还差几次点击（已解锁返回 0）。仅用于调试，不显示给用户。 */
  int tapsRemaining() {
    if (unlockedThisSession) return 0;
    return Math.max(0, BACKDOOR_TAPS - taps);
  }
}
