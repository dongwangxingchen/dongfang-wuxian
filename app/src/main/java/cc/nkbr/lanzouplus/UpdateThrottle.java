package cc.nkbr.lanzouplus;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * [DFWX] DFW-29（ARCH-001）宿主领域拆分 · 第三步：**更新检查的节流策略**。
 *
 * ## 责任边界
 * 只回答一个问题：**"现在该不该自动检查更新？"** 外加"记下这次检查时间"。
 * 不做网络、不做 UI、不决定提示文案——那些仍在 `MainActivity`（与 DFW-7 的分工一致）。
 *
 * ## 为什么要单独成类
 * DFW-7 引入这条策略时，节流判定与 `SharedPreferences` 读写是内联在 MainActivity 里的，
 * 于是"24 小时节流"这件事**只能通过启动 Activity 间接验证**。
 * 抽出来之后可以用一个假的偏好存储直测边界（首次放行 / 窗口内拦截 / 窗口外放行 /
 * 时钟回拨 / 存储异常）。
 *
 * ## 行为等价性
 * 判定与写入逻辑与 DFW-7 的实现一致（同样的键名 `last_auto_ms`、同样的 24h 间隔常量），
 * 只是把"读哪里写哪里"抽成接口，便于测试与后续替换。
 */
final class UpdateThrottle {
  /** 自动检查的最小间隔：24h（与 DFW-7 的常量一致）。 */
  static final long INTERVAL_MS = MainActivity.AUTO_UPDATE_CHECK_INTERVAL_MS;

  /** 偏好存储抽象：生产用 SharedPreferences，测试用内存实现。 */
  interface Store {
    long lastCheckAt();
    void setLastCheckAt(long at);
  }

  private final Store store;

  UpdateThrottle(Store store) { this.store = store; }

  /** 生产构造：绑定 `update_check` 偏好的 `last_auto_ms`。 */
  static UpdateThrottle forContext(Context context) {
    return new UpdateThrottle(new Store() {
      private SharedPreferences prefs() {
        return context.getSharedPreferences(MainActivity.UPDATE_CHECK_PREFS, Context.MODE_PRIVATE);
      }
      @Override public long lastCheckAt() {
        try { return prefs().getLong("last_auto_ms", 0L); } catch (Exception error) { return 0L; }
      }
      @Override public void setLastCheckAt(long at) {
        try { prefs().edit().putLong("last_auto_ms", at).apply(); }
        catch (Exception ignored) { /* 记不上时间不该影响使用，只是可能多查一次 */ }
      }
    });
  }

  long lastCheckAt() { return store.lastCheckAt(); }

  /**
   * 距上次自动检查是否已超过间隔。
   *
   * **时钟回拨防护**：如果记录的时间"来自未来"（用户改过系统时间、或跨设备恢复过数据），
   * 差值会是负数 → 直接视为"已过期、可以检查"，否则会被一条脏记录**永久锁死**不再更新。
   */
  boolean isDue(long now) {
    long last = store.lastCheckAt();
    if (last <= 0L) return true;              // 从没查过
    long elapsed = now - last;
    if (elapsed < 0L) return true;            // 记录来自未来 → 视为可查，避免永久锁死
    return elapsed >= INTERVAL_MS;
  }

  void remember(long at) { store.setLastCheckAt(at); }
}
