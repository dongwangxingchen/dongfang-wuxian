package cc.nkbr.lanzouplus;

import java.io.File;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * [DFW-101] **统一事件日志：任何不合理问题都要留下现场，不做针对性埋点。**
 *
 * ## 这张卡要解决的真问题
 * 用户连续 5 个版本报「下载一直显示解析中」，因为**没有任何现场**，
 * 只能靠猜——猜了 5 轮才定位到根因（AliCloud `acw_sc__v2` 算法变了 + `/tp/` 正则误匹配"举报"链接）。
 * 之前那套埋点是**针对性**的：谁想到某个 bug，就在那个 bug 的代码旁边插一行。
 * 没想到的地方出事，就一点证据都没有。
 *
 * ## 所以这里换一种做法：给"异常"本身修一条通道，而不是给"已知 bug"打点
 * 1. **一个入口**：全应用所有异常路径都写同一个文件、同一套格式，不再各写各的；
 * 2. **一把锁**：所有写入串行化（DFW-14 的历史问题——多线程同时写会把现场撕开，
 *    恰恰在最需要看清的时候把证据毁掉）。这把锁是**通用**的：任何文件走
 *    {@link #appendLocked(File, String)} 都受它保护，不需要每个写入点自己加锁；
 * 3. **面包屑**：内存里滚动保留最近 {@link #BREADCRUMB_CAPACITY} 条事件（零磁盘 I/O），
 *    于是**任何**后来的失败都能把它之前发生了什么一起交出来——不需要事先知道
 *    "哪个动作值得记录"，只要是走过 {@link #event} 的动作就自动在案；
 * 4. **出事自动抓现场**：{@link #failure} 附带堆栈 + 面包屑，
 *    {@link #scene} 附带全部线程栈（主线程卡死时由看门狗调用）。
 *
 * ## 容量上限（写死在常量里，不许无限涨）
 * 单个文件 {@link #MAX_BYTES} = 256KB；保留 {@link #MAX_FILES} = 2 份（当前 + 1 份归档），
 * 即**磁盘总占用硬上限 512KB**。写满就把当前文件改名为 `.1`（旧的 `.1` 被覆盖），
 * 新文件从零开始——**保留最近的，丢掉最老的**。
 *
 * ## 格式（沿用 lessons.md 第七节的 `DFX|` 约定）
 * ```
 * DFX|2026-10-02 10:31:02.417|main|resolve|start|url=https://...|n=3
 * DFX|2026-10-02 10:31:27.902|pool-3|resolve|timeout|ms=25000
 * ```
 * 一条事件**永远只占一行**：值里的换行会被转义成 `\n` 字面量。
 * 这是"并发写不撕行"之外的第二道保险——即使写入真的交错，也能一眼看出边界。
 *
 * ## 铁律
 * 日志自身**绝不许抛异常、绝不许阻塞主流程**：所有公开方法内部吞掉一切 `Throwable`。
 * 目录不可写时退化成"什么都不记"，而不是让业务代码失败。
 */
final class DfLog {
  private DfLog(){}

  // ── 容量与滚动（唯一真相，改这里就够）──────────────────────────────────

  /** 单个日志文件的字节上限。 */
  static final int MAX_BYTES = 256 * 1024;

  /** 保留份数（当前 + 归档）。磁盘总上限 = MAX_BYTES × MAX_FILES。 */
  static final int MAX_FILES = 2;

  /** 内存面包屑条数上限（零磁盘 I/O，所以可以比文件容量宽松）。 */
  static final int BREADCRUMB_CAPACITY = 128;

  /** 单条堆栈最多记多少帧（防止一条异常把整个文件挤掉）。 */
  static final int MAX_STACK_FRAMES = 30;

  /**
   * 单个字段最多多少字符。
   *
   * 必须有：调用方可能把一整段 HTML / 一个 300KB 的测试载荷当消息传进来，
   * 不截断的话**一条事件就能吃掉整个文件**，把真正的现场挤出去。
   */
  static final int MAX_FIELD_CHARS = 512;

  /** {@link #failure} 附带多少条面包屑（比 {@link #scene} 少：失败是高频的，卡死是低频的）。 */
  static final int FAILURE_BREADCRUMBS = 20;

  /**
   * 卡死现场最多展开多少个线程。
   *
   * 必须封顶：现场文本是要塞进导出窗口（{@link #EXPORT_BYTES}）给用户发出来的，
   * 几十个线程 × 30 帧能到 90KB —— 一份没人看得完、还会把**触发原因那一行**挤出窗口的现场，
   * 等于没有现场。主线程排最前，其余按"非守护优先"排，保证最可能相关的先展开。
   */
  static final int MAX_SCENE_THREADS = 10;

  /** 卡死现场每个线程最多多少帧（比失败的 30 帧少：卡在哪一步，浅层帧就够看出来了）。 */
  static final int MAX_SCENE_FRAMES = 16;

  /** 导出进崩溃报告时最多带多少字节（报告要塞进剪贴板/分享，不能无限大）。 */
  static final int EXPORT_BYTES = 24 * 1024;

  /** 统一事件日志的文件名。 */
  static final String LOG_NAME = "dfwx-events.log";

  /** 滚动归档文件名（只有一份，覆盖式）。 */
  static final String ARCHIVE_NAME = "dfwx-events.log.1";

  /** 行前缀：与 lessons.md 第七节的 `DFX|` 结构化日志约定一致。 */
  static final String PREFIX = "DFX|";

  private static final String TIME_PATTERN = "yyyy-MM-dd HH:mm:ss.SSS";

  // ── 状态 ──────────────────────────────────────────────────────────────

  /**
   * **全局写锁**。它保护的不只是本类自己的文件，还包括
   * {@link #appendLocked(File, String)} 传进来的任何文件（例如历史遗留的 `download.log`）。
   *
   * 为什么必须是同一把锁：`download.log` 历史上由 `DirectLinkResolver` 与 `LanzouCore`
   * **两个类各写一份、各自都没加锁**——两个线程同时 append 时行会互相插入。
   * 只要它们都走这个入口，就自动被串行化，不需要各自实现一遍。
   */
  private static final Object LOCK = new Object();

  /** 权威目录：应用外部私有目录，**不需要任何权限**，一定写得进去。 */
  private static volatile File primaryDir;

  /** 镜像目录：公共 `Download/东方无限/崩溃日志/`，用户能直接翻到；写失败就永久放弃它。 */
  private static volatile File mirrorDir;

  /** 镜像一旦写失败就不再重试（否则每个事件都要抛一次异常，纯浪费）。 */
  private static volatile boolean mirrorBroken;

  /** 面包屑环形缓冲：最近发生的事，内存里，崩溃/卡死时一次性交出来。 */
  private static final ArrayDeque<String> BREADCRUMBS = new ArrayDeque<>();

  /** 时间格式化器不是线程安全的，放在锁里用同一份（避免每个事件 new 一个）。 */
  private static final SimpleDateFormat TIME = new SimpleDateFormat(TIME_PATTERN, Locale.US);

  /**
   * 临界区并发探针：记录"同一时刻有几个写入者正在锁里"。
   *
   * 为什么生产代码里要留这个东西：**锁的有效性必须能被证明，不能靠"看起来加了锁"**。
   * 本项目在 DFW-14 上已经吃过一次亏 —— 当时的并发测试在有锁/无锁两种实现下都是绿的
   * （小段 append 在 OS 层近似原子），等于没测。这个探针把"互斥"变成一条可断言的数字：
   * 有锁时 {@link #maxConcurrentWriters()} 恒为 1，把 `synchronized` 去掉就必然 > 1。
   * 开销是两个 AtomicInteger 的加减，只在写日志时发生。
   */
  private static final java.util.concurrent.atomic.AtomicInteger INSIDE = new java.util.concurrent.atomic.AtomicInteger();
  private static final java.util.concurrent.atomic.AtomicInteger MAX_INSIDE = new java.util.concurrent.atomic.AtomicInteger();

  // ── 装配 ──────────────────────────────────────────────────────────────

  /**
   * 装配日志目录。`primary` 为 null 时整个日志系统退化成空操作（不抛、不记）。
   * `mirror` 可以为 null。
   *
   * 双写沿用 `crash.log` 已验证过的思路（v1.22.10）：
   * 私有目录是**权威副本**（崩溃在授权之前也留得下），公共目录是**给用户手取的副本**。
   */
  static void install(File primary, File mirror) {
    synchronized (LOCK) {
      primaryDir = primary;
      mirrorDir = mirror;
      mirrorBroken = false;
    }
  }

  static boolean isInstalled() {
    return primaryDir != null;
  }

  /** 当前日志文件（不存在也返回路径，调用方可据此判断"还没写过"）。 */
  static File logFile() {
    File dir = primaryDir;
    return dir == null ? null : new File(dir, LOG_NAME);
  }

  static File archiveFile() {
    File dir = primaryDir;
    return dir == null ? null : new File(dir, ARCHIVE_NAME);
  }

  // ── 写入 API ──────────────────────────────────────────────────────────

  /**
   * 记一条事件（**会落盘**，同时进入面包屑）。
   *
   * `keyValues` 是**交替的 key/value 对**：`event("resolve","start","url",url,"n",3)`。
   * 奇数个参数时最后一个被忽略（不抛）。
   */
  static void event(String area, String event, Object... keyValues) {
    write(formatLine(area, event, keyValues), true);
  }

  /**
   * 只记面包屑（**纯内存，不碰磁盘**）。
   *
   * 给"高频、单看没意义、但出事时是黄金线索"的动作用：解析走到哪一步、
   * 网络请求打到哪个 host、页面切到哪一页……平时零成本，出事时全部交出来。
   */
  static void breadcrumb(String area, String event, Object... keyValues) {
    String line = formatLine(area, event, keyValues);
    synchronized (LOCK) {
      pushBreadcrumbLocked(line);
    }
  }

  /**
   * 记一次失败：事件行 + 异常类名/消息 + 堆栈（截断到 {@link #MAX_STACK_FRAMES} 帧）
   * + **出事前最近的面包屑**。
   *
   * 这是"不做针对性埋点"的关键：调用方只要在**任何**异常出口调它，
   * 就能自动带上"这个异常之前应用在干什么"，而不需要事先在每条路径上插点。
   */
  static void failure(String area, String event, Throwable error, Object... keyValues) {
    StringBuilder sb = new StringBuilder(formatLine(area, event, keyValues));
    sb.append('\n');
    if (error != null) {
      sb.append("  ").append(error.getClass().getName());
      String message = error.getMessage();
      if (message != null && !message.isEmpty()) sb.append(": ").append(oneLine(message));
      sb.append('\n');
      StackTraceElement[] frames = error.getStackTrace();
      int limit = Math.min(frames == null ? 0 : frames.length, MAX_STACK_FRAMES);
      for (int i = 0; i < limit; i++) sb.append("    at ").append(frames[i]).append('\n');
      if (frames != null && frames.length > limit) {
        sb.append("    ... 还有 ").append(frames.length - limit).append(" 帧\n");
      }
    }
    appendBreadcrumbs(sb, FAILURE_BREADCRUMBS);
    write(sb.toString(), true);
  }

  /**
   * 抓一次现场：**多个线程的栈** + 最近的面包屑。
   *
   * 主线程卡死（"卡住了 / 没反应"）时由 {@link MainThreadStallWatchdog} 调用。
   * 崩溃时由 {@link App#writeCrashLog} 调用。
   * 注意这里**故意不止抓主线程**：卡死往往是一个后台线程持锁、主线程等它，
   * 只看主线程栈会漏掉真凶。但也不是无限抓 —— 见 {@link #MAX_SCENE_THREADS} 的说明。
   */
  static void scene(String reason) {
    String header = formatLine("scene", "capture", "reason", reason);
    StringBuilder sb = new StringBuilder(header);
    sb.append('\n');
    int total = 0;
    int shown = 0;
    /*
     * 没有可写目录时跳过线程枚举：抓线程栈是这里唯一的重活，
     * 抓完没地方放就是纯浪费（而面包屑仍然照记，不丢时间线）。
     */
    if (isInstalled()) {
      Thread[] threads = orderThreadsForScene(enumerateThreads());
      total = threads.length;
      sb.append("  -- 线程现场（共 ").append(total).append(" 个，最多展开 ").append(MAX_SCENE_THREADS).append(" 个）--\n");
      for (Thread thread : threads) {
        if (thread == null) continue;
        if (shown >= MAX_SCENE_THREADS) break;
        sb.append("  ").append(MainThreadStallWatchdog.describeThread(thread, MAX_SCENE_FRAMES)).append('\n');
        shown++;
      }
      if (total > shown) {
        sb.append("  ... 还有 ").append(total - shown).append(" 个线程未展开（守护线程优先省略）\n");
      }
    }
    appendBreadcrumbs(sb, BREADCRUMB_CAPACITY);
    /*
     * 结尾再写一次原因。
     *
     * 不是冗余：导出走的是**尾部窗口**（只取最近 24K）。一个现场如果比窗口还大，
     * 被切掉的恰恰是**开头那行"为什么抓这个现场"** —— 用户发过来的就是一堆没有标题的线程栈。
     * 结尾补一行，窗口怎么切都留得住"这是什么事故"。
     */
    sb.append("  ── 现场结束：").append(oneLine(reason)).append("（线程 ").append(total)
        .append(" 个，展开 ").append(shown).append(" 个）──\n");
    write(sb.toString(), true);
  }

  /**
   * 现场里线程的展开顺序：**主线程第一**，然后非守护线程，最后守护线程。
   *
   * 卡死的第一嫌疑人永远是主线程；守护线程（各种池、GC 辅助线程）数量最多、最不重要，
   * 排最后才能在被 {@link #MAX_SCENE_THREADS} 截断时先牺牲它们。
   */
  private static Thread[] orderThreadsForScene(Thread[] threads) {
    if (threads == null || threads.length <= 1) return threads == null ? new Thread[0] : threads;
    List<Thread> list = new ArrayList<>();
    for (Thread thread : threads) if (thread != null) list.add(thread);
    Thread resolvedMain = null;
    try {
      android.os.Looper looper = android.os.Looper.getMainLooper();
      if (looper != null) resolvedMain = looper.getThread();
    } catch (Throwable ignored) {
      // 纯 JVM 单测里没有 Looper 实现：退化成"当前线程优先"，不影响排序的意义
      resolvedMain = Thread.currentThread();
    }
    // 必须是 final：下面的比较器 lambda 要捕获它
    final Thread main = resolvedMain;
    list.sort((left, right) -> {
      int scoreLeft = scenePriority(left, main);
      int scoreRight = scenePriority(right, main);
      if (scoreLeft != scoreRight) return Integer.compare(scoreLeft, scoreRight);
      return left.getName().compareTo(right.getName());
    });
    return list.toArray(new Thread[0]);
  }

  private static int scenePriority(Thread thread, Thread main) {
    if (main != null && thread == main) return 0;
    return thread.isDaemon() ? 2 : 1;
  }

  /**
   * 通用**加锁追加**：给本类之外的日志文件用（历史遗留的 `download.log` 就是两个类在写）。
   *
   * 之所以做成公开入口而不是让各处自己 `synchronized`：加锁这件事**只在一个地方实现**，
   * 新增写入点不可能忘记加锁——这正是 DFW-14 出问题的根因。
   * 超限时清空（沿用这两个文件原有的 256KB 语义，行为不变）。
   */
  static void appendLocked(File file, String line) {
    if (file == null || line == null) return;
    synchronized (LOCK) {
      enterCritical();
      try {
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory()) //noinspection ResultOfMethodCallIgnored
          parent.mkdirs();
        if (file.exists() && file.length() > MAX_BYTES) //noinspection ResultOfMethodCallIgnored
          file.delete();
        FileOutputStream out = new FileOutputStream(file, true);
        try {
          // 只压平换行（保证"一条记录一行"），**不截断**：这是通用原语，
          // 调用方写多长就写多长，容量由上面的 MAX_BYTES 上限兜住。
          out.write(flatten(line).getBytes(StandardCharsets.UTF_8));
          out.write('\n');
        } finally {
          out.close();
        }
      } catch (Throwable ignored) {
        // 埋点绝不能影响主流程
      } finally {
        exitCritical();
      }
    }
  }

  // ── 读取 / 清理 ───────────────────────────────────────────────────────

  /** 导出用：归档 + 当前，只取尾部 {@link #EXPORT_BYTES} 字节。没有内容时返回空串。 */
  static String readForExport() {
    String all = readAll();
    if (all.isEmpty()) return "";
    byte[] bytes = all.getBytes(StandardCharsets.UTF_8);
    if (bytes.length <= EXPORT_BYTES) return all;
    int start = bytes.length - EXPORT_BYTES;
    // 从换行处切开，避免导出内容以半行开头（半行会让读者以为是日志损坏）
    while (start < bytes.length && bytes[start] != '\n') start++;
    // 跳过那个换行本身：导出必须以一条完整事件的 `DFX|` 开头
    if (start < bytes.length) start++;
    return new String(bytes, start, bytes.length - start, StandardCharsets.UTF_8);
  }

  /** 归档在前、当前在后，拼成完整内容。读不到就是空串。 */
  static String readAll() {
    synchronized (LOCK) {
      String older = readWhole(archiveFile());
      String current = readWhole(logFile());
      if (older.isEmpty()) return current;
      if (current.isEmpty()) return older;
      return older + current;
    }
  }

  /** 清空日志与面包屑（「清除崩溃记录」会一并调用）。 */
  static void clear() {
    synchronized (LOCK) {
      deleteQuietlyLocked(logFile());
      deleteQuietlyLocked(archiveFile());
      BREADCRUMBS.clear();
      File mirror = mirrorDir;
      if (mirror != null) {
        deleteQuietlyLocked(new File(mirror, LOG_NAME));
        deleteQuietlyLocked(new File(mirror, ARCHIVE_NAME));
      }
    }
  }

  // ── 测试钩子 ──────────────────────────────────────────────────────────

  /** 当前面包屑条数（验证环形上限用）。 */
  static int breadcrumbCount() {
    synchronized (LOCK) {
      return BREADCRUMBS.size();
    }
  }

  /** 面包屑快照（验证"出事时能交出前因"用）。 */
  static List<String> breadcrumbs() {
    synchronized (LOCK) {
      return new ArrayList<>(BREADCRUMBS);
    }
  }

  /** 单行格式化（可直测，不需要碰文件系统）。 */
  static String formatLine(String area, String event, Object... keyValues) {
    StringBuilder sb = new StringBuilder(PREFIX);
    sb.append(nowLocked()).append('|');
    sb.append(oneLine(Thread.currentThread().getName())).append('|');
    sb.append(oneLine(area == null ? "-" : area)).append('|');
    sb.append(oneLine(event == null ? "-" : event));
    if (keyValues != null) {
      for (int i = 0; i + 1 < keyValues.length; i += 2) {
        sb.append('|').append(oneLine(String.valueOf(keyValues[i]))).append('=');
        sb.append(oneLine(String.valueOf(keyValues[i + 1])));
      }
    }
    return sb.toString();
  }

  /**
   * 锁有效性的**可断言证据**：观察到的"同时身处临界区"的最大线程数。
   * 有锁时恒为 1；把 `synchronized` 去掉，8 个线程并发写时必然 > 1。
   */
  static int maxConcurrentWriters() {
    return MAX_INSIDE.get();
  }

  /** 重置并发探针（每个测试自己从零开始量）。 */
  static void resetConcurrencyProbe() {
    MAX_INSIDE.set(0);
    INSIDE.set(0);
  }

  // ── 内部实现 ──────────────────────────────────────────────────────────

  private static void write(String text, boolean withMirror) {
    synchronized (LOCK) {
      enterCritical();
      try {
        pushBreadcrumbLocked(firstLine(text));
        File primary = primaryDir;
        if (primary != null) appendTextLocked(new File(primary, LOG_NAME), text);
        if (withMirror) {
          File mirror = mirrorDir;
          if (mirror != null && !mirrorBroken) {
            boolean ok = appendTextLocked(new File(mirror, LOG_NAME), text);
            if (!ok) mirrorBroken = true;
          }
        }
      } finally {
        exitCritical();
      }
    }
  }

  /** 进入临界区：记录并发度峰值（见 {@link #INSIDE} 的说明）。 */
  private static void enterCritical() {
    int now = INSIDE.incrementAndGet();
    MAX_INSIDE.accumulateAndGet(now, Math::max);
  }

  private static void exitCritical() {
    INSIDE.decrementAndGet();
  }

  private static void pushBreadcrumbLocked(String line) {
    BREADCRUMBS.addLast(line);
    while (BREADCRUMBS.size() > BREADCRUMB_CAPACITY) BREADCRUMBS.removeFirst();
  }

  private static void appendBreadcrumbs(StringBuilder sb, int max) {
    List<String> snapshot;
    synchronized (LOCK) {
      snapshot = new ArrayList<>(BREADCRUMBS);
    }
    int start = Math.max(0, snapshot.size() - max);
    sb.append("  -- 出事前最近 ").append(snapshot.size() - start).append(" 条事件（新→旧）--\n");
    for (int i = snapshot.size() - 1; i >= start; i--) {
      sb.append("  ").append(snapshot.get(i)).append('\n');
    }
  }

  /** 追加 + 滚动。返回是否写入成功（镜像据此决定要不要永久放弃）。 */
  private static boolean appendTextLocked(File file, String text) {
    try {
      File parent = file.getParentFile();
      if (parent != null && !parent.isDirectory()) //noinspection ResultOfMethodCallIgnored
        parent.mkdirs();
      rotateIfNeededLocked(file);
      /*
       * 必须补换行：`event()` 传进来的是**没有结尾换行**的单行文本，
       * 不补的话连续两条事件会在文件里粘成一行 —— 日志看起来还在，
       * 实际上已经无法按行解析（`readForExport` 找不到换行会把整段判成"半行"丢掉）。
       * 这个 bug 是 `readForExport_returnsContentAndNeverStartsWithHalfLine` 抓出来的。
       */
      String payload = text.endsWith("\n") ? text : text + "\n";
      FileOutputStream out = new FileOutputStream(file, true);
      try {
        out.write(payload.getBytes(StandardCharsets.UTF_8));
      } finally {
        out.close();
      }
      return true;
    } catch (Throwable ignored) {
      return false;
    }
  }

  /**
   * 滚动：写满就把当前文件改名成 `.1`（覆盖旧的 `.1`），新文件从零开始。
   *
   * 为什么不学 `crash.log` 直接删掉：删掉是"丢掉全部历史"，滚动是"丢掉最老的、留下最近的"。
   * 排查时最近的那段才是现场，越老越没用。
   */
  private static void rotateIfNeededLocked(File file) {
    if (!file.exists()) return;
    if (file.length() < MAX_BYTES) return;
    File archive = new File(file.getParentFile(), ARCHIVE_NAME);
    deleteQuietlyLocked(archive);
    //noinspection ResultOfMethodCallIgnored
    file.renameTo(archive);
  }

  private static String readWhole(File file) {
    try {
      if (file == null || !file.exists() || file.length() == 0) return "";
      long size = file.length();
      RandomAccessFile raf = new RandomAccessFile(file, "r");
      try {
        byte[] buf = new byte[(int) Math.min(size, Integer.MAX_VALUE)];
        raf.readFully(buf);
        return new String(buf, StandardCharsets.UTF_8);
      } finally {
        raf.close();
      }
    } catch (Throwable ignored) {
      return "";
    }
  }

  private static void deleteQuietlyLocked(File file) {
    try {
      if (file != null && file.exists()) //noinspection ResultOfMethodCallIgnored
        file.delete();
    } catch (Throwable ignored) {
      // 清理失败不影响任何功能
    }
  }

  private static Thread[] enumerateThreads() {
    try {
      ThreadGroup group = Thread.currentThread().getThreadGroup();
      ThreadGroup root = group;
      while (root != null && root.getParent() != null) root = root.getParent();
      if (root == null) root = group;
      Thread[] buffer = new Thread[Math.max(16, root.activeCount() * 2)];
      int count = root.enumerate(buffer, true);
      if (count <= 0) return new Thread[0];
      Thread[] out = new Thread[count];
      System.arraycopy(buffer, 0, out, 0, count);
      return out;
    } catch (Throwable ignored) {
      return new Thread[0];
    }
  }

  /**
   * 一条事件永远只占一行：换行/回车压成空格，超长截断。
   *
   * 两道保险缺一不可：不压换行，多行消息会把日志撕成看起来像"多条事件"的样子；
   * 不截断，一条 300KB 的消息就能把整个文件吃掉。
   */
  private static String oneLine(String value) {
    if (value == null) return "";
    // 先截断再压平：调用方可能传进来 300KB 的载荷，先压平等于白白复制一大段
    if (value.length() > MAX_FIELD_CHARS) {
      return flatten(value.substring(0, MAX_FIELD_CHARS)
          + "…(截断 " + (value.length() - MAX_FIELD_CHARS) + " 字)");
    }
    return flatten(value);
  }

  /** 换行 → 空格：保证"一条记录占一行"，否则多行消息会被读成好几条事件。 */
  private static String flatten(String value) {
    if (value == null) return "";
    if (value.indexOf('\n') < 0 && value.indexOf('\r') < 0) return value;
    return value.replace("\r\n", " ").replace('\n', ' ').replace('\r', ' ');
  }

  private static String firstLine(String text) {
    int index = text.indexOf('\n');
    return index < 0 ? text : text.substring(0, index);
  }

  private static String nowLocked() {
    synchronized (TIME) {
      return TIME.format(new Date());
    }
  }
}
