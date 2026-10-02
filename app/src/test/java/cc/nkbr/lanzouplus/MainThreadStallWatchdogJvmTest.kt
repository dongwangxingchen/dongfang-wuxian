package cc.nkbr.lanzouplus

import android.os.Looper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * [DFW-101] 主线程卡死看门狗直测。
 *
 * ## 为什么这条必须有测试
 * 「卡住了 / 没反应」是用户最常报、而现有日志**完全看不见**的一类事故：
 * 没有任何异常被抛出，`crash.log` 里一个字都不会有。看门狗是唯一能把它变成证据的东西。
 * 一个没被测过的看门狗等于没有看门狗 —— 它自己坏了也没人知道。
 *
 * ## 怎么在 Robolectric 里真的制造一次"卡死"
 * Robolectric 的主 Looper 默认是 **paused**：投进去的任务只有 `idle()` 才会执行。
 * 测试代码本身就跑在主线程上，于是：
 *  - **不调 `idle()`** → 打卡任务永远排不上 → 看门狗判定主线程卡死（这就是"卡住了"）；
 *  - **持续调 `idle()`** → 打卡正常跑完 → 看门狗不该误报。
 * 两个方向都测，才能同时证明"抓得到"和"不误报"。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class MainThreadStallWatchdogJvmTest {

    @get:Rule val tmp = TemporaryFolder()

    @Before
    fun setUp() {
        // App.onCreate 可能已经起过一个默认 5 秒阈值的看门狗，先停掉避免它串场
        MainThreadStallWatchdog.stopForTest()
        MainThreadStallWatchdog.resetForTest()
        DfLog.install(tmp.newFolder("events"), null)
        DfLog.clear()
    }

    @After
    fun tearDown() {
        MainThreadStallWatchdog.stopForTest()
        MainThreadStallWatchdog.resetForTest()
        DfLog.install(null, null)
        DfLog.clear()
    }

    // ---------- 纯函数：冷却判定 ----------

    /**
     * 冷却必须有，而且要能算准。
     * 卡死时每一轮循环都会判定失败，不冷却就会把 512KB 的日志瞬间灌满，
     * 反而把"卡之前发生了什么"挤掉。
     */
    @Test
    fun shouldReport_respectsCooldownWindow() {
        assertTrue("第一次（还没上报过）必须上报", MainThreadStallWatchdog.shouldReport(1000L, 0L, 30000L))
        assertFalse("刚上报过就不能再报", MainThreadStallWatchdog.shouldReport(1500L, 1000L, 30000L))
        assertFalse("冷却窗口内不能报", MainThreadStallWatchdog.shouldReport(30999L, 1000L, 30000L))
        assertTrue("正好到窗口边界就该报", MainThreadStallWatchdog.shouldReport(31000L, 1000L, 30000L))
        assertTrue("超过窗口当然要报", MainThreadStallWatchdog.shouldReport(99999L, 1000L, 30000L))
        assertTrue("冷却为 0 表示不限流", MainThreadStallWatchdog.shouldReport(1001L, 1000L, 0L))
    }

    // ---------- 纯函数：线程现场描述 ----------

    /**
     * 抓现场必须带**线程状态**：`BLOCKED`（等锁）与 `WAITING`（等通知）
     * 指向完全不同的根因，只看栈帧看不出这个区别。
     */
    @Test
    fun describeThread_includesNameStatePriorityAndFrames() {
        val gate = CountDownLatch(1)
        val release = CountDownLatch(1)
        val worker = Thread({
            gate.countDown()
            release.await()
        }, "dfwx-probe-thread")
        worker.isDaemon = true
        worker.start()
        assertTrue(gate.await(5, TimeUnit.SECONDS))
        /*
         * [2026-10-02 Lead 修正] 这里原来紧接着就断言线程状态是 WAITING/TIMED_WAITING —— **会偶发假红**。
         *
         * 原因：`gate.countDown()` 一返回主线程就醒了，但探针线程**此刻还没走到**
         * `release.await()`，那一瞬间它的状态是 `RUNNABLE`。
         * 实测报错过：`dfwx-probe-thread|RUNNABLE|prio=5|daemon|alive=true`。
         *
         * 这是典型的"拿两个线程的调度顺序当保证"。
         * 改成**轮询等它真的进入等待态**（有上限，不会挂死），再断言。
         */
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (worker.state != Thread.State.WAITING && worker.state != Thread.State.TIMED_WAITING &&
            System.nanoTime() < deadline
        ) {
            Thread.sleep(2)
        }
        try {
            val text = MainThreadStallWatchdog.describeThread(worker)
            assertTrue("必须带线程名：$text", text.startsWith("dfwx-probe-thread"))
            assertTrue("必须带线程状态：$text", text.contains("WAITING") || text.contains("TIMED_WAITING"))
            assertTrue("必须带优先级：$text", text.contains("|prio="))
            assertTrue("必须带是否守护线程：$text", text.contains("|daemon"))
            assertTrue("必须带栈帧：$text", text.contains("at "))
            assertTrue("栈里应当能看到这个测试方法", text.contains("MainThreadStallWatchdogJvmTest"))
        } finally {
            release.countDown()
            worker.join(5000)
        }
    }

    @Test
    fun describeThread_handlesNullWithoutThrowing() {
        assertTrue(MainThreadStallWatchdog.describeThread(null).isNotEmpty())
    }

    // ---------- 端到端：真的抓得到 / 真的不误报 ----------

    /**
     * **主线程被堵住时必须留下现场**（这条就是"卡住了"的答案）。
     *
     * 测试代码跑在主线程上，这里故意不 `idle()`：打卡任务排不进去，
     * 看门狗睡够阈值后判定卡死 → 写一条 `main-thread-stall` 现场。
     */
    @Test
    fun stall_isCapturedWithFullThreadDump() {
        MainThreadStallWatchdog.start(150L, 0L)
        // 占住主线程：Robolectric 的 paused looper 不会自己跑打卡任务
        Thread.sleep(1200L)

        assertTrue(
            "主线程被堵住超过阈值后必须上报（实际 ${MainThreadStallWatchdog.reportCount()} 次）",
            MainThreadStallWatchdog.awaitReport(3000L),
        )

        val text = File(tmp.root, "events/${DfLog.LOG_NAME}").readText(Charsets.UTF_8)
        assertTrue("现场必须写明是主线程卡死：$text", text.contains("|reason=main-thread-stall"))
        assertTrue("现场必须带全线程栈：$text", text.contains("线程现场（"))
        assertTrue(
            "现场必须能看出是哪个线程被堵住了：$text",
            text.contains(Thread.currentThread().name),
        )
    }

    /**
     * **不能误报**：主线程正常（打卡任务能跑完）时一次都不许上报。
     *
     * 误报比不报更糟 —— 用户拿到一份满是"卡死"的报告，真正那次反而找不到。
     */
    @Test
    fun noStallIsReportedWhenMainLooperKeepsUp() {
        MainThreadStallWatchdog.start(150L, 0L)
        val mainLooper = shadowOf(Looper.getMainLooper())
        val deadline = System.currentTimeMillis() + 900L
        while (System.currentTimeMillis() < deadline) {
            mainLooper.idle()
            Thread.sleep(30L)
        }
        mainLooper.idle()

        assertEquals("主线程一直在正常打卡，不该有任何上报", 0, MainThreadStallWatchdog.reportCount())
        assertFalse(
            "不该产生卡死现场",
            File(tmp.root, "events/${DfLog.LOG_NAME}").let { it.isFile && it.readText().contains("main-thread-stall") },
        )
    }

    /** 重复 `start()` 不能起出第二个线程（lessons.md 第八节-4：测试 JVM 线程只增不减会一次红 184 条）。 */
    @Test
    fun start_isIdempotent() {
        val before = Thread.getAllStackTraces().keys.count { it.name == "dfwx-stall-watchdog" }
        MainThreadStallWatchdog.start(150L, 0L)
        MainThreadStallWatchdog.start(150L, 0L)
        MainThreadStallWatchdog.start(150L, 0L)
        Thread.sleep(150L)
        val after = Thread.getAllStackTraces().keys.count { it.name == "dfwx-stall-watchdog" }
        assertTrue(
            "无论 start 几次，最多只能有一个看门狗线程（之前 $before 个，之后 $after 个）",
            after - before <= 1,
        )
    }

    /** 阈值非法时直接不启动，而不是起一个疯狂误报的线程。 */
    @Test
    fun start_rejectsInvalidThresholds() {
        MainThreadStallWatchdog.start(0L, 0L)
        MainThreadStallWatchdog.start(-1L, 1000L)
        Thread.sleep(200L)
        assertEquals("非法阈值不该起线程", 0, MainThreadStallWatchdog.reportCount())
    }
}
