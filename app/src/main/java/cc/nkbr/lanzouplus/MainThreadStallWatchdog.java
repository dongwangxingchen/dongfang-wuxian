package cc.nkbr.lanzouplus;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * [DFW-101] **主线程卡死看门狗：把"卡住了 / 没反应"也变成有现场的事故。**
 *
 * ## 为什么必须有它
 * 现有的 `crash.log` 只在**抛异常**时留下东西。但用户最常报的是
 * 「点了没反应」「卡住了」「一直转圈」——这类问题**一行日志都不会产生**，
 * 因为没有任何异常被抛出：主线程只是在等一把锁、等一次磁盘 I/O、
 * 或者在一个死循环里转。没有看门狗，这类事故在证据上等于**从未发生过**。
 *
 * ## 它怎么做到"不做针对性埋点"
 * 它不关心**为什么**卡。它只做一件通用的事：
 * 每隔一段时间往主线程消息队列里放一个"打卡"任务，然后等。
 * 主线程只要还活着就会跑到它；**超过阈值还没跑到，就说明主线程被堵住了**——
 * 这时立刻把现场抓下来（全部线程栈 + 出事前的面包屑）。
 *
 * 于是：不管是哪段代码、哪个 bug、哪个第三方库把主线程堵住的，
 * 都会被同一套机制记下来，**不需要事先知道 bug 在哪**。
 *
 * ## 成本与安全
 * - 单条守护线程，`MIN_PRIORITY`，`daemon`（进程结束即消失，不会吊住退出）；
 * - 每次循环只做一次 `Handler.post` + 一次 `sleep`，不占 CPU；
 * - 同一个进程**最多只起一个**（{@link #STARTED} 保证幂等），
 *   避免 Robolectric 里反复创建 Application 时线程越堆越多
 *   （lessons.md 第八节-4 记过这个坑：测试 JVM 线程只增不减会一次红 184 条）；
 * - 看门狗自身**绝不许抛**：任何异常都吞掉继续下一轮，否则它自己就成了新的崩溃源。
 */
final class MainThreadStallWatchdog {
  private MainThreadStallWatchdog(){}

  /** 主线程多久没打卡算"卡死"。5 秒：短于系统 ANR 的 5 秒输入超时，能在用户感知到"卡了"的同一时刻留证。 */
  static final long DEFAULT_STALL_MS = 5000L;

  /**
   * 同一次卡顿的重复上报冷却。30 秒内只记一次——
   * 卡死时每一轮都会判定失败，不冷却就会把 512KB 的日志瞬间灌满，
   * 反而把"卡之前发生了什么"挤掉。
   */
  static final long DEFAULT_COOLDOWN_MS = 30000L;

  /** 最多一个看门狗线程。 */
  private static final AtomicBoolean STARTED = new AtomicBoolean(false);

  private static final AtomicInteger REPORTS = new AtomicInteger();

  private static final AtomicLong LAST_REPORT_AT = new AtomicLong(0L);

  private static volatile Thread worker;

  static void start() {
    start(DEFAULT_STALL_MS, DEFAULT_COOLDOWN_MS);
  }

  /**
   * 起看门狗。阈值可注入是为了**能被测**：测试用 100ms 而不是 5s。
   * 重复调用是安全的（只有第一次生效）。
   */
  static void start(long stallMs, long cooldownMs) {
    if (stallMs <= 0L || cooldownMs < 0L) return;
    if (!STARTED.compareAndSet(false, true)) return;
    try {
      Thread thread = new Thread(() -> loop(stallMs, cooldownMs), "dfwx-stall-watchdog");
      thread.setDaemon(true);
      thread.setPriority(Thread.MIN_PRIORITY);
      worker = thread;
      thread.start();
    } catch (Throwable ignored) {
      // 起不来就当没有看门狗，绝不影响启动
      STARTED.set(false);
    }
  }

  /** 停止看门狗（只给测试用；生产进程里它跟着进程一起结束）。 */
  static void stopForTest() {
    Thread thread = worker;
    worker = null;
    STARTED.set(false);
    if (thread != null) thread.interrupt();
  }

  /** 已上报的卡死次数（测试断言用）。 */
  static int reportCount() {
    return REPORTS.get();
  }

  static void resetForTest() {
    REPORTS.set(0);
    LAST_REPORT_AT.set(0L);
  }

  /** 等一次上报，最多等 `timeoutMs`。返回是否等到了（测试用，避免 sleep 猜时间）。 */
  static boolean awaitReport(long timeoutMs) {
    long deadline = System.currentTimeMillis() + timeoutMs;
    while (System.currentTimeMillis() < deadline) {
      if (REPORTS.get() > 0) return true;
      try {
        Thread.sleep(20L);
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
        return false;
      }
    }
    return REPORTS.get() > 0;
  }

  // ── 核心循环 ──────────────────────────────────────────────────────────

  private static void loop(long stallMs, long cooldownMs) {
    Handler main;
    try {
      main = new Handler(Looper.getMainLooper());
    } catch (Throwable ignored) {
      return;
    }
    AtomicBoolean beat = new AtomicBoolean(false);
    while (!Thread.currentThread().isInterrupted()) {
      try {
        beat.set(false);
        main.post(() -> beat.set(true));
        Thread.sleep(stallMs);
        if (beat.get()) continue;

        long now = System.currentTimeMillis();
        if (shouldReport(now, LAST_REPORT_AT.get(), cooldownMs)) {
          LAST_REPORT_AT.set(now);
          REPORTS.incrementAndGet();
          DfLog.scene("main-thread-stall");
        }

        /*
         * 卡住期间不能空转重报：等到主线程恢复（打卡终于跑到）或者冷却窗口结束再继续。
         * 上限取 cooldownMs 与 stallMs 的较大者，保证"一直卡着"也能周期性再记一次
         * （否则一个永久卡死只会留下一条记录，看不出它卡了多久）。
         */
        long waitUntil = System.currentTimeMillis() + Math.max(cooldownMs, stallMs);
        while (!beat.get() && System.currentTimeMillis() < waitUntil
            && !Thread.currentThread().isInterrupted()) {
          Thread.sleep(Math.min(200L, stallMs));
        }
      } catch (InterruptedException stop) {
        Thread.currentThread().interrupt();
        return;
      } catch (Throwable ignored) {
        // 看门狗自己绝不许把进程带下去
      }
    }
  }

  // ── 纯函数（可直测，不需要真的把主线程卡住）──────────────────────────

  /**
   * 冷却判定：距上次上报不足 `cooldownMs` 就不重复上报。
   * 抽成纯函数是为了能在毫秒级单测里穷举边界，而不是靠 `sleep` 猜。
   */
  static boolean shouldReport(long nowMs, long lastReportMs, long cooldownMs) {
    if (lastReportMs <= 0L) return true;
    if (cooldownMs <= 0L) return true;
    return nowMs - lastReportMs >= cooldownMs;
  }

  /**
   * 把一个线程的现场压成一段可读文本：`名字|状态|优先级|是否守护` + 栈帧。
   *
   * 抓现场时**必须带上线程状态**：`BLOCKED`（等锁）和 `WAITING`（等通知）
   * 指向完全不同的根因，只看栈帧看不出这个区别。
   */
  static String describeThread(Thread thread) {
    return describeThread(thread, DfLog.MAX_STACK_FRAMES);
  }

  /**
   * 同上，但限制帧数。抓现场时**必须带上线程状态**：`BLOCKED`（等锁）和 `WAITING`（等通知）
   * 指向完全不同的根因，只看栈帧看不出这个区别。
   */
  static String describeThread(Thread thread, int maxFrames) {
    if (thread == null) return "(null thread)";
    StringBuilder sb = new StringBuilder();
    sb.append(thread.getName())
        .append('|').append(thread.getState())
        .append("|prio=").append(thread.getPriority())
        .append(thread.isDaemon() ? "|daemon" : "")
        .append("|alive=").append(thread.isAlive());
    StackTraceElement[] frames;
    try {
      frames = thread.getStackTrace();
    } catch (Throwable ignored) {
      frames = null;
    }
    int limit = Math.min(frames == null ? 0 : frames.length, Math.max(0, maxFrames));
    for (int i = 0; i < limit; i++) {
      sb.append("\n    at ").append(frames[i]);
    }
    if (frames != null && frames.length > limit) {
      sb.append("\n    ... 还有 ").append(frames.length - limit).append(" 帧");
    }
    return sb.toString();
  }
}
