package cc.nkbr.lanzouplus;

import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * DFWX-STAB-001：下载历史持久化生命周期（修审计 H-P1-1 主线程落盘）。
 *
 * 写永远发生在独立后台线程（download-history）：debounce 合并高频状态更新，
 * flushAndClose 关闭后不再接受新写、尽力异步提交最后一次写，调用线程
 * （主线程 onDestroy 路径）不等待磁盘——最坏丢最近一个 debounce 窗口内的
 * 更新，进程重建时由 loadDownloadHistory 的非终态归一化兜底（可解释）。
 * 单键 putString+commit 由 SharedPreferences 保证原子性：写中断不会留下
 * 半份历史，旧值完整保留。
 */
final class DownloadHistoryStore implements AutoCloseable {
  interface Sink { void write(String json) throws Exception; }

  private final Supplier<String> payload;
  private final Sink sink;
  private final long debounceMs;
  private final ScheduledThreadPoolExecutor io;
  private final Object lock = new Object();
  private boolean closed, dirty;
  private java.util.concurrent.ScheduledFuture<?> scheduled;

  DownloadHistoryStore(Supplier<String> payload, Sink sink) { this(payload, sink, MainActivity.DOWNLOAD_PERSIST_DEBOUNCE_MS); }

  DownloadHistoryStore(Supplier<String> payload, Sink sink, long debounceMs) {
    this.payload = payload;
    this.sink = sink;
    this.debounceMs = Math.max(1L, debounceMs);
    io = new ScheduledThreadPoolExecutor(1, r -> { Thread t = new Thread(r, "download-history"); t.setDaemon(true); return t; });
    io.setRemoveOnCancelPolicy(true);
  }

  /** 标记历史已变化：debounce 窗口内的多次标记合并为一次写。 */
  void markChanged() {
    synchronized (lock) {
      if (closed) return;
      dirty = true;
      if (scheduled == null) scheduled = io.schedule(this::drain, debounceMs, TimeUnit.MILLISECONDS);
    }
  }

  private void drain() {
    synchronized (lock) {
      if (closed) return;
      dirty = false;
    }
    writeOnce();
    synchronized (lock) {
      scheduled = null;
      if (dirty && !closed) scheduled = io.schedule(this::drain, debounceMs, TimeUnit.MILLISECONDS);
    }
  }

  /** 立即写一次（后台线程语义；序列化或落盘失败不抛出，旧历史保持原样）。 */
  void writeOnce() {
    String json;
    try { json = payload.get(); } catch (Throwable error) {
      android.util.Log.w("DownloadHistoryStore", "serialize failed: " + error.getMessage(), error);
      return;
    }
    if (json == null) return;
    try { sink.write(json); } catch (Throwable error) {
      android.util.Log.w("DownloadHistoryStore", "persist failed: " + error.getMessage(), error);
    }
  }

  /** 关闭并尽力提交最后一次写：不阻塞调用线程，关闭后 markChanged 为 no-op。
   * 只有存在未落盘变更（dirty）才补最后一次写——干净关闭不产生多余写入。 */
  void flushAndClose() {
    boolean pendingWrite;
    synchronized (lock) {
      pendingWrite = dirty;
      closed = true;
      dirty = false;
      if (scheduled != null) scheduled.cancel(false);
      scheduled = null;
    }
    if (pendingWrite) {
      try { io.execute(this::writeOnce); } catch (RejectedExecutionException ignored) {
        android.util.Log.w("DownloadHistoryStore", "final write rejected: " + ignored.getMessage(), ignored);
      }
    }
    io.shutdown(); // 非 shutdownNow：允许已提交的写完整落盘
  }

  boolean isClosed() { synchronized (lock) { return closed; } }

  @Override public void close() { flushAndClose(); }
}
