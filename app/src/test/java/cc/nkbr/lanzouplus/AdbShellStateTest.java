package cc.nkbr.lanzouplus;

import android.content.ServiceConnection;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * DFWX-ADB-001：ADB/Shizuku 状态车道的竞态与线程边界回归（审计 H-P1-4）。
 * <p>手动车道 + 可控平台让"旧 generation 结果晚到"可以确定性复现：平台调用内触发更新的状态事件
 * （等价于 binder 线程回调），验证旧代次结果与旧代次绑定请求都被丢弃，且调用线程不做 binder 工作。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class AdbShellStateTest {

  /** 手动车道：任务只入队、由测试显式 drain，在途任务因此完全可控。 */
  static final class ManualLane implements Executor {
    private final ArrayDeque<Runnable> queue = new ArrayDeque<>();
    @Override public void execute(Runnable task) { synchronized (queue) { queue.add(task); } }
    int drain() { int ran = 0; while (true) { Runnable task; synchronized (queue) { task = queue.poll(); } if (task == null) return ran; task.run(); ran++; } }
    int pending() { synchronized (queue) { return queue.size(); } }
  }

  /** 可控平台：每步行为可赋值，并可在任意平台调用内触发"更新的状态事件"。 */
  static final class FakePlatform implements AdbShellManager.Platform {
    boolean installed = true, ping = true, preV11 = false, rationale = false;
    int permission = android.content.pm.PackageManager.PERMISSION_GRANTED, uid = 2000;
    int pingCount, permissionCheckCount, uidCount, requestCount, bindCount, unbindCount, addCount, removeCount;
    Runnable onPing = () -> {}, onPermissionCheck = () -> {}, onGetUid = () -> {}, onBind = () -> {};
    Throwable failOnPermissionCheck;
    ServiceConnection connection;
    AdbShellManager.Events events = new AdbShellManager.Events() {
      @Override public void binderReceived() {}
      @Override public void binderDead() {}
      @Override public void permissionResult(int requestCode, int result) {}
    };

    @Override public boolean installed() { return installed; }
    @Override public boolean pingBinder() { pingCount++; onPing.run(); return ping; }
    @Override public boolean isPreV11() { return preV11; }
    @Override public int checkSelfPermission() { permissionCheckCount++; onPermissionCheck.run(); if (failOnPermissionCheck != null) throw new IllegalStateException("binder 事务失败", failOnPermissionCheck); return permission; }
    @Override public boolean shouldShowRequestPermissionRationale() { return rationale; }
    @Override public int getUid() { uidCount++; onGetUid.run(); return uid; }
    @Override public void requestPermission(int requestCode) { requestCount++; }
    @Override public void bindUserService(ServiceConnection value) { bindCount++; connection = value; onBind.run(); }
    @Override public void unbindUserService(ServiceConnection value) { unbindCount++; }
    @Override public void addEvents(AdbShellManager.Events value) { addCount++; events = value; }
    @Override public void removeEvents(AdbShellManager.Events value) { removeCount++; }
  }

  /** 本地 Binder 桩：AIDL Stub 构造时 attachInterface，asInterface 走同进程直通路径。 */
  static final class FakeShellService extends IAdbShellService.Stub {
    @Override public void destroy() {}
    @Override public String installApk(android.os.ParcelFileDescriptor source, long size) { return "OK\ninstalled"; }
  }

  static final class Recorder implements AdbShellManager.Listener {
    final List<AdbShellManager.State> states = new ArrayList<>();
    @Override public void changed(AdbShellManager.Snapshot snapshot) { states.add(snapshot.state); }
  }

  private static int count(List<AdbShellManager.State> states, AdbShellManager.State state) {
    int total = 0;
    for (AdbShellManager.State value : states) if (value == state) total++;
    return total;
  }

  private static AdbShellManager.State last(Recorder recorder) {
    return recorder.states.get(recorder.states.size() - 1);
  }

  /** 绑定成功即回连（模拟 Shizuku 立即回调 onServiceConnected）。 */
  private static void connectServiceOnBind(FakePlatform platform) {
    platform.onBind = () -> platform.connection.onServiceConnected(null, new FakeShellService());
  }

  /* 状态检查不得在调用线程做 binder 工作：refresh() 立即返回，平台调用只发生在车道上。 */
  @Test public void refreshPerformsNoPlatformWorkOnCallerThread() {
    FakePlatform platform = new FakePlatform();
    ManualLane lane = new ManualLane();
    AdbShellManager manager = new AdbShellManager(platform, new Recorder(), lane);

    manager.refresh();
    assertEquals("refresh() 不得在调用线程访问平台", 0, platform.pingCount);
    assertEquals(1, lane.pending());

    lane.drain();
    assertEquals(1, platform.pingCount);
  }

  /* 检查进行中到来更新的状态请求：旧代次结果与旧代次绑定请求都必须丢弃。
     这正是 H-P1-4 的触发形状——晚评估的旧结果覆盖新状态。 */
  @Test public void staleEvaluationDiscardedWhenNewerRequestArrivesMidCheck() {
    FakePlatform platform = new FakePlatform();
    ManualLane lane = new ManualLane();
    Recorder recorder = new Recorder();
    AdbShellManager manager = new AdbShellManager(platform, recorder, lane);
    final boolean[] fired = {false};
    platform.onPing = () -> { if (!fired[0]) { fired[0] = true; platform.events.binderReceived(); } };
    connectServiceOnBind(platform);

    manager.start();
    lane.drain();

    assertEquals("旧代次不得发布 CONNECTING", 1, count(recorder.states, AdbShellManager.State.CONNECTING));
    assertEquals("最终状态必须是 READY", AdbShellManager.State.READY_SHELL, last(recorder));
    assertEquals("旧代次不得发起绑定", 1, platform.bindCount);
    assertTrue(manager.ready());
  }

  /* 检查进行中 binder 死亡：在途评估不得再把状态发布成 CONNECTING/READY，也不得再发起绑定。 */
  @Test public void binderDeathMidCheckCannotPublishStaleStateOrBind() {
    FakePlatform platform = new FakePlatform();
    ManualLane lane = new ManualLane();
    Recorder recorder = new Recorder();
    AdbShellManager manager = new AdbShellManager(platform, recorder, lane);
    connectServiceOnBind(platform);

    manager.start();
    lane.drain();
    assertEquals(AdbShellManager.State.READY_SHELL, manager.snapshot().state);
    int connectingBefore = count(recorder.states, AdbShellManager.State.CONNECTING);

    // 死亡事件发生在读取 uid 之后、状态决策之前：此时 service 已被清空，
    // 在途评估会误判成"权限已授予但服务未连接"，进而发布 CONNECTING 并再发起绑定。
    platform.onGetUid = () -> { platform.ping = false; platform.events.binderDead(); };
    manager.refresh();
    lane.drain();

    assertEquals(AdbShellManager.State.NOT_RUNNING, manager.snapshot().state);
    assertFalse(manager.ready());
    assertEquals("断链后不得再发起绑定", 1, platform.bindCount);
    assertEquals("旧代次不得把断链误报成 CONNECTING", connectingBefore, count(recorder.states, AdbShellManager.State.CONNECTING));
  }

  /* 权限状态在检查进行中变化：旧代次的 READY 不得覆盖新代次已发布的 DENIED。 */
  @Test public void staleReadyCannotOverrideNewerDeniedState() {
    FakePlatform platform = new FakePlatform();
    ManualLane lane = new ManualLane();
    Recorder recorder = new Recorder();
    AdbShellManager manager = new AdbShellManager(platform, recorder, lane);
    connectServiceOnBind(platform);

    manager.start();
    lane.drain();
    assertEquals(AdbShellManager.State.READY_SHELL, manager.snapshot().state);
    int readyBefore = count(recorder.states, AdbShellManager.State.READY_SHELL);

    // 在途评估已读到"已授权 + uid"，此刻权限被撤销并通知 —— 它手里的 READY 已经过期
    platform.onGetUid = () -> {
      platform.permission = android.content.pm.PackageManager.PERMISSION_DENIED;
      platform.rationale = true;
      platform.events.permissionResult(AdbShellManager.PERMISSION_REQUEST, android.content.pm.PackageManager.PERMISSION_DENIED);
    };
    manager.refresh();
    lane.drain();

    assertEquals(AdbShellManager.State.DENIED, manager.snapshot().state);
    assertEquals("旧代次的 READY 不得在 DENIED 之后发布", readyBefore, count(recorder.states, AdbShellManager.State.READY_SHELL));
    assertFalse(manager.ready());
  }

  /* 断链重连：binder 死亡复位 service 与 binding 标记，重达后必须能再次绑定并回到 READY。 */
  @Test public void reconnectAfterBinderDeathBindsAgain() {
    FakePlatform platform = new FakePlatform();
    ManualLane lane = new ManualLane();
    AdbShellManager manager = new AdbShellManager(platform, new Recorder(), lane);
    connectServiceOnBind(platform);

    manager.start();
    lane.drain();
    assertTrue(manager.ready());

    platform.ping = false;
    platform.events.binderDead();
    lane.drain();
    assertFalse("binder 死亡后不得保持 ready", manager.ready());

    platform.ping = true;
    platform.events.binderReceived();
    lane.drain();
    assertTrue("重连后必须能再次绑定并回到 READY", manager.ready());
    assertEquals("binding 标记必须在断链时复位，否则第二次绑定被跳过", 2, platform.bindCount);
  }

  /* 平台异常必须落到可定位的 ERROR（含原因），并清除 service 防止半连接被当成可用。 */
  @Test public void platformFailurePublishesLocatableError() {
    FakePlatform platform = new FakePlatform();
    ManualLane lane = new ManualLane();
    AdbShellManager manager = new AdbShellManager(platform, new Recorder(), lane);
    connectServiceOnBind(platform);

    manager.start();
    lane.drain();
    platform.failOnPermissionCheck = new IllegalStateException("binder 事务失败", new RuntimeException("dead object"));

    manager.refresh();
    lane.drain();

    assertEquals(AdbShellManager.State.ERROR, manager.snapshot().state);
    assertTrue("错误详情必须可定位", manager.snapshot().detail.contains("binder 事务失败"));
    assertFalse(manager.ready());
  }

  /* 授权申请按最近已发布状态决策，且不在调用线程做 binder 检查；已拒绝不重复申请。 */
  @Test public void requestPermissionUsesPublishedStateWithoutBlocking() {
    FakePlatform platform = new FakePlatform();
    ManualLane lane = new ManualLane();
    AdbShellManager manager = new AdbShellManager(platform, new Recorder(), lane);
    connectServiceOnBind(platform);
    platform.permission = android.content.pm.PackageManager.PERMISSION_DENIED;

    manager.start();
    lane.drain();
    assertEquals(AdbShellManager.State.NEEDS_PERMISSION, manager.snapshot().state);

    assertTrue("等待授权时应发起申请", manager.requestPermission());
    assertEquals("申请不得在调用线程执行", 0, platform.requestCount);
    lane.drain();
    assertEquals(1, platform.requestCount);

    platform.rationale = true;
    manager.refresh();
    lane.drain();
    assertEquals(AdbShellManager.State.DENIED, manager.snapshot().state);

    assertFalse("已拒绝时不得再发起申请", manager.requestPermission());
    lane.drain();
    assertEquals("已拒绝时不得再次发出申请", 1, platform.requestCount);
  }

  /* 快照过期时的申请：状态说"等待授权"但重新核实时已授权，不得再向 Shizuku 发申请。 */
  @Test public void requestPermissionRechecksBeforeSending() {
    FakePlatform platform = new FakePlatform();
    ManualLane lane = new ManualLane();
    AdbShellManager manager = new AdbShellManager(platform, new Recorder(), lane);
    connectServiceOnBind(platform);
    platform.permission = android.content.pm.PackageManager.PERMISSION_DENIED;

    manager.start();
    lane.drain();
    assertEquals(AdbShellManager.State.NEEDS_PERMISSION, manager.snapshot().state);

    // 用户已在系统/Shizuku 侧授权，但本类快照还是旧的 NEEDS_PERMISSION
    platform.permission = android.content.pm.PackageManager.PERMISSION_GRANTED;
    manager.requestPermission();
    lane.drain();

    assertEquals("重新核实发现已授权，不得重复发申请", 0, platform.requestCount);
    assertEquals("应发布真实状态", AdbShellManager.State.READY_SHELL, manager.snapshot().state);
  }

  /* 平台调用卡住（binder 被冻结）不得阻塞调用线程：refresh() 与 close() 都必须立即返回。 */
  @Test public void hangingPlatformCallNeverBlocksCaller() {
    FakePlatform platform = new FakePlatform();
    ManualLane lane = new ManualLane();
    AdbShellManager manager = new AdbShellManager(platform, new Recorder(), lane);

    final long hangMs = 300;
    platform.onPing = () -> { try { Thread.sleep(hangMs); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); } };

    long started = System.nanoTime();
    manager.start();
    manager.refresh();
    manager.refresh();
    manager.close();
    long elapsedMs = (System.nanoTime() - started) / 1_000_000;

    assertTrue("调用线程不得被 binder 调用拖住（实测 " + elapsedMs + "ms）", elapsedMs < hangMs / 2);
    assertEquals("卡住的平台调用只能发生在车道上", 0, platform.pingCount);
    lane.drain();
    assertEquals("关闭任务仍须在车道上完成解绑", 1, platform.unbindCount);
  }

  /* 未连接时安装必须给出可定位原因（含当前状态标题），而不是含糊的失败。 */
  @Test public void installReportsCurrentStateWhenNotConnected() {
    FakePlatform platform = new FakePlatform();
    ManualLane lane = new ManualLane();
    AdbShellManager manager = new AdbShellManager(platform, new Recorder(), lane);
    platform.ping = false;

    manager.start();
    lane.drain();
    assertEquals(AdbShellManager.State.NOT_RUNNING, manager.snapshot().state);

    AdbShellManager.InstallResult result = manager.install(null, null, 0);
    assertFalse(result.success);
    assertTrue("失败原因需含状态标题便于定位：" + result.message, result.message.contains("未连接"));
  }

  /* 关闭：不在调用线程做 binder 工作（onDestroy 走主线程），关闭后不再发布任何状态。 */
  @Test public void closeDefersBinderWorkAndStopsPublishing() {
    FakePlatform platform = new FakePlatform();
    ManualLane lane = new ManualLane();
    Recorder recorder = new Recorder();
    AdbShellManager manager = new AdbShellManager(platform, recorder, lane);
    connectServiceOnBind(platform);

    manager.start();
    lane.drain();
    int published = recorder.states.size();

    manager.close();
    assertEquals("close() 不得在调用线程解绑", 0, platform.unbindCount);
    lane.drain();
    assertEquals(1, platform.unbindCount);
    assertEquals(1, platform.removeCount);

    manager.refresh();
    lane.drain();
    assertEquals("关闭后不得再发布状态", published, recorder.states.size());
    assertFalse(manager.ready());
    assertFalse(manager.install(null, null, 0).success);
  }
}
