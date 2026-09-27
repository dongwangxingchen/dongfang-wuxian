package cc.nkbr.lanzouplus;

import android.app.Application;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

/**
 * DFWX-STAB-001：下载历史持久化生命周期回归（修审计 H-P1-1 主线程落盘）。
 *
 * DownloadHistoryStore 约束：
 * - markChanged 合并 debounce 窗口内的多次标记为一次写；
 * - 写永远发生在独立后台线程，不在调用线程同步等磁盘；
 * - flushAndClose 关闭后不再接受新写，且尽力提交最后一次写（不阻塞调用线程）；
 * - 序列化抛异常时 sink 不被调用——SharedPreferences 保留旧值（写入中断可恢复）。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class DownloadHistoryStoreTest {

  private static final class RecordingSink implements DownloadHistoryStore.Sink {
    final List<String> writes = new ArrayList<>();
    final List<Thread> threads = new ArrayList<>();

    @Override public synchronized void write(String json) {
      writes.add(json);
      threads.add(Thread.currentThread());
    }

    synchronized int count() { return writes.size(); }
    synchronized String last() { return writes.isEmpty() ? null : writes.get(writes.size() - 1); }
    synchronized Thread lastThread() { return threads.isEmpty() ? null : threads.get(threads.size() - 1); }
  }

  private static void await(CountDownLatch latch) throws InterruptedException {
    assertTrue("等待后台写入超时", latch.await(10, TimeUnit.SECONDS));
  }

  /** 轮询等待条件成立（后台 debounce 线程完成写入）。 */
  private static void awaitTrue(java.util.function.BooleanSupplier condition) throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    while (!condition.getAsBoolean()) {
      if (System.nanoTime() > deadline) throw new AssertionError("等待条件超时");
      Thread.sleep(5);
    }
  }

  @Test
  public void markChangedWritesOnceAfterDebounce() throws Exception {
    RecordingSink sink = new RecordingSink();
    DownloadHistoryStore store = new DownloadHistoryStore(() -> "{\"v\":1}", sink, 20);
    store.markChanged();
    store.markChanged();
    store.markChanged();
    awaitTrue(() -> sink.count() >= 1);
    Thread.sleep(80); // debounce 稳定期，确认没有多余写
    assertEquals("debounce 窗口内的多次标记必须合并为一次写", 1, sink.count());
    assertEquals("{\"v\":1}", sink.last());
    assertNotSame("写必须发生在后台持久化线程，而非调用线程", Thread.currentThread(), sink.lastThread());
    store.flushAndClose();
  }

  @Test
  public void serializationFailureKeepsPreviousHistoryIntact() throws Exception {
    RecordingSink sink = new RecordingSink();
    AtomicReference<String> payload = new AtomicReference<>("{\"v\":\"good\"}");
    DownloadHistoryStore store = new DownloadHistoryStore(payload::get, sink, 10);
    store.markChanged();
    awaitTrue(() -> sink.count() == 1);
    // 注入序列化中断（模拟进程在 JSON 构造中途被杀）
    payload.set(null);
    CountDownLatch failed = new CountDownLatch(1);
    new Thread(() -> { try { store.writeOnce(); } finally { failed.countDown(); } }).start();
    await(failed);
    Thread.sleep(60);
    assertEquals("序列化失败的写不得触碰 sink，旧历史保持原样", 1, sink.count());
    assertEquals("{\"v\":\"good\"}", sink.last());
    store.flushAndClose();
  }

  @Test
  public void flushAndCloseDoesNotBlockCallerUntilWriteCompletes() throws Exception {
    RecordingSink sink = new RecordingSink();
    AtomicReference<String> payload = new AtomicReference<>("\"final\"");
    DownloadHistoryStore store = new DownloadHistoryStore(payload::get, sink, 10_000);
    store.markChanged(); // 尚未到期，未写
    assertEquals(0, sink.count());
    long start = System.nanoTime();
    store.flushAndClose(); // 必须立即返回，不得等磁盘
    long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
    assertTrue("flushAndClose 不得在调用线程同步等待落盘（耗时 " + elapsedMs + "ms）", elapsedMs < 2000);
    awaitTrue(() -> sink.count() == 1); // 关闭前尽力提交的最后一次写异步完成
    assertEquals("\"final\"", sink.last());
  }

  @Test
  public void closedStoreRejectsFurtherChanges() throws Exception {
    RecordingSink sink = new RecordingSink();
    DownloadHistoryStore store = new DownloadHistoryStore(() -> "\"x\"", sink, 10);
    store.markChanged();
    awaitTrue(() -> sink.count() == 1);
    store.flushAndClose();
    awaitTrue(() -> store.isClosed());
    store.markChanged(); // 关闭后的标记必须是 no-op
    Thread.sleep(120);
    assertEquals("close 后不得再产生写入", 1, sink.count());
  }

  @Test
  public void runsOnRobolectricMainApplication() {
    // 保证 store 使用的 android.util.Log 在 Robolectric 主环境可用（主线程断言基线）
    Application application = RuntimeEnvironment.getApplication();
    assertFalse(application == null);
  }
}
