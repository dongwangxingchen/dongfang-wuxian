package cc.nkbr.lanzouplus

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.lang.reflect.Field
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * [DFWX] 搜索长时间停在 "3/50 个源·已完成 0·找到 4" 的四项回归守卫。
 *
 * ## 用户症状（只读调查结论，行号对 HEAD `69bb1e3`）
 * 1. `executeNetwork` 的提前 `return` / `InterruptedException` 退出路径不复位
 *    `apiInFlight` / `dirInFlight`。慢源被 `rotateSourceLocked` 置 `active=false` 后，
 *    `queueApiLocked`/`queueDirectoryLocked` 因 in-flight 恒真永不再入队 →
 *    `finishSourceLocked` 永不通过 → `done` 冻结 → `run()` 的 `done>=total` 永不满足。
 * 2. `ROUTE_PRESSURE` 只增不减（只有成功时 -1），无上限无衰减 → 压力可自锁在极低并发。
 * 3. `adaptiveSourceWorkers` 没有下限，`capacity` 可以被压力算到 1。
 * 4. `run()` 没有总预算，`cancelled`/`done>=total` 之外的退出路径不存在 → "永不收尾"。
 *
 * 本测试用反射驱动真实的 `SearchCoordinator`（不是"读源码猜行为"），
 * 只有第 10 条是源码级装配断言，且用大括号配对取方法体。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class LanzouSearchStallJvmTest {

    private companion object {
        val coreCls: Class<*> = LanzouCore::class.java
        val stateCls: Class<*> = Class.forName("cc.nkbr.lanzouplus.LanzouCore\$SourceSearchState")
        val coordinatorCls: Class<*> = Class.forName("cc.nkbr.lanzouplus.LanzouCore\$SearchCoordinator")
        val jobCls: Class<*> = Class.forName("cc.nkbr.lanzouplus.LanzouCore\$SearchNetworkJob")
        const val DEFAULT_BUDGET_MS = 180000L
    }

    private val pressureField: Field = coreCls.getDeclaredField("ROUTE_PRESSURE").apply { isAccessible = true }
    private val pressureAtField: Field = coreCls.getDeclaredField("routePressureAt").apply { isAccessible = true }

    @Before
    fun setUp() {
        resetPressure()
        setSearchBudget(DEFAULT_BUDGET_MS)
    }

    @After
    fun tearDown() {
        resetPressure()
        setSearchBudget(DEFAULT_BUDGET_MS)
    }

    // ---------------------------------------------------------------- helpers

    private fun declaredMethod(cls: Class<*>, name: String, vararg types: Class<*>): java.lang.reflect.Method {
        val method = cls.getDeclaredMethod(name, *types)
        method.isAccessible = true
        return method
    }

    private fun field(target: Any, name: String): Field {
        var cls: Class<*>? = target.javaClass
        while (cls != null) {
            try {
                return cls.getDeclaredField(name).apply { isAccessible = true }
            } catch (ignored: NoSuchFieldException) {
                cls = cls.superclass
            }
        }
        error("找不到字段 $name")
    }

    private fun setField(target: Any, name: String, value: Any?) = field(target, name).set(target, value)

    private fun getField(target: Any, name: String): Any? = field(target, name).get(target)

    private fun newCore(): LanzouCore {
        val ctor = coreCls.getDeclaredConstructor(android.content.Context::class.java)
        ctor.isAccessible = true
        return ctor.newInstance(RuntimeEnvironment.getApplication()) as LanzouCore
    }

    private fun source(url: String, title: String, searchable: Boolean): Models.Source {
        val value = Models.Source()
        value.url = url
        value.title = title
        value.kind = Models.SOURCE_OFFICIAL
        value.searchable = searchable
        return value
    }

    private fun newState(source: Models.Source, query: String = "q"): Any {
        val ctor = stateCls.getDeclaredConstructor(Models.Source::class.java, String::class.java, List::class.java)
        ctor.isAccessible = true
        return ctor.newInstance(source, query, emptyList<Models.Item>())
    }

    private fun newCoordinator(
        core: LanzouCore,
        states: List<Any>,
        options: Models.SearchOptions = Models.SearchOptions(),
        progress: Models.Progress? = null,
    ): Any {
        // SearchCoordinator is a non-static inner class, so its reflected constructor
        // carries the enclosing LanzouCore instance as the first parameter.
        val ctor = coordinatorCls.getDeclaredConstructor(
            coreCls,
            List::class.java,
            List::class.java,
            Set::class.java,
            Models.SearchOptions::class.java,
            Models.Progress::class.java,
        )
        ctor.isAccessible = true
        val out = java.util.Collections.synchronizedList(ArrayList<Models.Item>())
        val seen = ConcurrentHashMap.newKeySet<String>()
        return ctor.newInstance(core, states, out, seen, options, progress)
    }

    private fun newJob(state: Any, api: Boolean): Any {
        val ctor = jobCls.getDeclaredConstructor(stateCls, java.lang.Boolean.TYPE)
        ctor.isAccessible = true
        return ctor.newInstance(state, api)
    }

    /**
     * `executeNetwork` 在生产里跑在 searchPool 的工作线程上，且它的中断路径会
     * `Thread.currentThread().interrupt()`。这里也放到独立线程执行，避免把中断标志
     * 留在 JUnit 线程上污染后续用例。
     */
    private fun executeNetwork(coordinator: Any, job: Any) {
        val failure = java.util.concurrent.atomic.AtomicReference<Throwable?>()
        val worker = Thread({
            try {
                declaredMethod(coordinatorCls, "executeNetwork", jobCls).invoke(coordinator, job)
            } catch (error: Throwable) {
                failure.set(error)
            }
        }, "test-execute-network")
        worker.isDaemon = true
        worker.start()
        worker.join(15000L)
        assertFalse("executeNetwork 未在 15s 内返回", worker.isAlive)
        failure.get()?.let { throw AssertionError("executeNetwork 抛出异常", it) }
    }

    private fun runCoordinator(coordinator: Any): List<Models.Item> {
        @Suppress("UNCHECKED_CAST")
        return declaredMethod(coordinatorCls, "run").invoke(coordinator) as List<Models.Item>
    }

    private fun doneOf(coordinator: Any): Int =
        (getField(coordinator, "done") as java.util.concurrent.atomic.AtomicInteger).get()

    private fun totalOf(coordinator: Any): Int = getField(coordinator, "total") as Int

    private fun boolOf(target: Any, name: String): Boolean = getField(target, name) as Boolean

    // ------------------------------------------------------- route pressure

    private fun pressureCounter() = pressureField.get(null) as java.util.concurrent.atomic.AtomicInteger

    private fun resetPressure() {
        pressureCounter().set(0)
        pressureAtField.setLong(null, System.nanoTime())
    }

    private fun routePressure(): Int = declaredMethod(coreCls, "routePressure").invoke(null) as Int

    private fun observeRouteFailure(error: Exception) =
        declaredMethod(coreCls, "observeRouteFailure", Exception::class.java).invoke(null, error)

    private fun observeRouteSuccess() = declaredMethod(coreCls, "observeRouteSuccess").invoke(null)

    private fun decayedPressure(value: Int, nowNanos: Long, updatedAtNanos: Long): Int =
        declaredMethod(
            coreCls,
            "decayedRoutePressure",
            Integer.TYPE,
            java.lang.Long.TYPE,
            java.lang.Long.TYPE,
        ).invoke(null, value, nowNanos, updatedAtNanos) as Int

    private fun adaptiveSourceWorkers(requested: Int, taskCount: Int): Int =
        declaredMethod(coreCls, "adaptiveSourceWorkers", Integer.TYPE, Integer.TYPE)
            .invoke(null, requested, taskCount) as Int

    private fun adaptiveNetworkWorkers(taskCount: Int): Int =
        declaredMethod(coreCls, "adaptiveNetworkWorkers", Integer.TYPE)
            .invoke(null, taskCount) as Int

    private fun setSearchBudget(millis: Long) =
        declaredMethod(coreCls, "setSearchBudgetMillis", java.lang.Long.TYPE).invoke(null, millis)

    private fun rateLimited(): Exception = java.io.IOException("请求频率受限 429")

    // =========================================================== 1. in-flight

    /**
     * 反向探针：注释掉 `executeNetwork` finally 里的 `releaseInFlightLocked(job)`，
     * 本用例必须变红（apiInFlight 保持 true）。
     *
     * `active=false` 让 `stopped(state)` 为真 → `NetworkGovernor.acquireForeground`
     * 直接抛 InterruptedException（就是 :252 那条泄漏路径）。
     * `paused=true` 让 finally 里的 refill/pump 变成空操作，测试不会发起真实网络请求。
     */
    @Test
    fun interruptedWorkerReleasesApiInFlight() {
        val core = newCore()
        val state = newState(source("https://a.lanzout.com/aaa", "A", searchable = false))
        val coordinator = newCoordinator(core, listOf(state))
        setField(coordinator, "paused", true)
        setField(state, "active", false)
        setField(state, "apiInFlight", true)

        executeNetwork(coordinator, newJob(state, api = true))

        assertFalse("API 在飞标记必须在 InterruptedException 路径被复位", boolOf(state, "apiInFlight"))
    }

    @Test
    fun interruptedWorkerReleasesDirectoryInFlightAndRestoresReadiness() {
        val core = newCore()
        val state = newState(source("https://b.lanzout.com/bbb", "B", searchable = false))
        val coordinator = newCoordinator(core, listOf(state))
        setField(coordinator, "paused", true)
        setField(state, "active", false)
        setField(state, "dirInFlight", true)
        setField(state, "dirReady", false)

        executeNetwork(coordinator, newJob(state, api = false))

        assertFalse("目录在飞标记必须被复位", boolOf(state, "dirInFlight"))
        assertTrue("目录就绪位必须恢复，否则轮换回来后永不再入队", boolOf(state, "dirReady"))
    }

    @Test
    fun releaseOnlyTouchesTheJobOwnSource() {
        val core = newCore()
        val target = newState(source("https://c.lanzout.com/ccc", "C", searchable = false))
        val other = newState(source("https://d.lanzout.com/ddd", "D", searchable = false))
        val coordinator = newCoordinator(core, listOf(target, other))
        setField(coordinator, "paused", true)
        setField(target, "active", false)
        setField(other, "active", false)
        setField(target, "apiInFlight", true)
        setField(other, "apiInFlight", true)

        executeNetwork(coordinator, newJob(target, api = true))

        assertFalse(boolOf(target, "apiInFlight"))
        assertTrue("另一个源的在飞标记不允许被误复位", boolOf(other, "apiInFlight"))
    }

    @Test
    fun releaseIsIdempotent() {
        val core = newCore()
        val state = newState(source("https://e.lanzout.com/eee", "E", searchable = false))
        val coordinator = newCoordinator(core, listOf(state))
        setField(coordinator, "paused", true)
        setField(state, "active", false)
        setField(state, "apiInFlight", true)
        val job = newJob(state, api = true)

        executeNetwork(coordinator, job)
        executeNetwork(coordinator, job)

        assertFalse(boolOf(state, "apiInFlight"))
    }

    // ================================================== 2. pressure cap + decay

    @Test
    fun routePressureIsCapped() {
        resetPressure()
        repeat(500) { observeRouteFailure(rateLimited()) }
        val pressure = routePressure()
        assertTrue("压力必须被硬上限封顶，实际=$pressure", pressure <= 15)
        assertTrue("压力必须真的涨上去，否则上限断言是假绿，实际=$pressure", pressure == 15)
    }

    @Test
    fun routePressureDecaysWithElapsedTime() {
        val now = System.nanoTime()
        val decay = 15_000L * 1_000_000L
        assertEquals("15s 静默应抵消 1 点", 9, decayedPressure(10, now, now - decay))
        assertEquals("45s 静默应抵消 3 点", 7, decayedPressure(10, now, now - 3 * decay))
        assertEquals("长时间静默必须归零，不能变负数", 0, decayedPressure(10, now, now - 300 * decay))
        assertEquals("零压力保持零", 0, decayedPressure(0, now, now - 300 * decay))
        assertEquals("未到衰减周期不衰减", 10, decayedPressure(10, now, now - 1000L))
    }

    @Test
    fun routePressureIsFoldedIntoReads() {
        resetPressure()
        repeat(12) { observeRouteFailure(rateLimited()) }
        assertEquals(12, routePressure())
        // 把"最后一次更新"推回 60s 前：按 15s 衰减 4 点，读取时无状态折算。
        pressureAtField.setLong(null, System.nanoTime() - 60_000L * 1_000_000L)
        assertEquals(8, routePressure())
    }

    @Test
    fun routePressureNeverGoesNegative() {
        resetPressure()
        repeat(50) { observeRouteSuccess() }
        assertEquals(0, routePressure())
    }

    // =========================================================== 3. worker floor

    @Test
    fun sourceWorkersHaveAFloorButNeverExceedDemand() {
        resetPressure()
        repeat(500) { observeRouteFailure(rateLimited()) }
        // demand=8 时 floor 与 demand 相等，结果与堆/压力无关，恒为 8；
        // 修复前同一输入会被 capacity 压到 1（见反向探针）。
        assertEquals("上限压力下也不允许压到 8 以下", 8, adaptiveSourceWorkers(0, 8))
        assertTrue("50 个源时也必须 >= 8，实际=${adaptiveSourceWorkers(0, 50)}", adaptiveSourceWorkers(0, 50) >= 8)
        assertEquals("下限不得突破 demand", 3, adaptiveSourceWorkers(0, 3))
        assertEquals("下限不得突破显式请求", 2, adaptiveSourceWorkers(2, 50))
        assertEquals("taskCount<=0 仍然是 0", 0, adaptiveSourceWorkers(0, 0))
    }

    @Test
    fun activeLimitNeverExceedsHttpLimit() {
        resetPressure()
        repeat(500) { observeRouteFailure(rateLimited()) }
        for (total in intArrayOf(1, 2, 7, 8, 9, 50, 200)) {
            val active = Math.max(1, adaptiveSourceWorkers(0, total))
            for (matched in 0..3) {
                val http = adaptiveNetworkWorkers(Math.max(1, active + matched))
                assertTrue("total=$total matched=$matched active=$active http=$http", http >= active)
                assertTrue("active 不得大于 total=$total", active <= total)
            }
        }
    }

    // ============================================================ 4. budget

    @Test(timeout = 20000)
    fun budgetExpiryClosesOutEverySourceWithoutFakingDone() {
        setSearchBudget(300L)
        val core = newCore()
        val state = newState(source("https://f.lanzout.com/fff", "F", searchable = false))
        // 卡在"等下一页调度"的源：没有任何可入队的网络工作，不靠预算就永远不动。
        setField(state, "folder", Models.Item())
        setField(state, "dirReady", false)
        val progress = RecordingProgress()
        val coordinator = newCoordinator(core, listOf(state), progress = progress)
        assertEquals(1, totalOf(coordinator))

        val started = System.nanoTime()
        val out = runCoordinator(coordinator)
        val elapsedMs = (System.nanoTime() - started) / 1_000_000L

        assertTrue("预算到点后 run() 必须返回，实际 ${elapsedMs}ms", elapsedMs < 15000L)
        assertEquals("done 必须由 finishSourceLocked 正常推进到 total", totalOf(coordinator), doneOf(coordinator))
        assertTrue("未完成的源必须走正常收尾（terminal）", boolOf(state, "terminal"))
        assertTrue("截断的源必须标记为失败", boolOf(state, "dirFailed"))
        assertTrue("必须告知调用方失败：${progress.failures}", progress.failures.contains("F"))
        assertTrue("截断的源绝不能被上报为目录索引已完成：${progress.indexed}", progress.indexed.isEmpty())
        assertEquals(emptyList<Models.Item>(), out)
    }

    @Test(timeout = 20000)
    fun zeroBudgetKeepsTheSearchUnbounded() {
        // 0 = NO_DEADLINE：不引入预算时行为必须与今天一致（本用例只断言接线，不跑完整搜索）。
        setSearchBudget(0L)
        val core = newCore()
        val state = newState(source("https://g.lanzout.com/ggg", "G", searchable = false))
        setField(state, "folder", Models.Item())
        setField(state, "dirReady", false)
        val coordinator = newCoordinator(core, listOf(state))
        assertFalse(declaredMethod(coordinatorCls, "searchBudgetExpired").invoke(coordinator) as Boolean)
    }

    // ================================================ 10. source-level wiring

    private val coreSource: String = File(
        (System.getProperty("user.dir") ?: ".").let { dir ->
            var d = File(dir).absoluteFile
            while (!File(d, "src/main/java/cc/nkbr/lanzouplus/LanzouCore.java").isFile && d.parentFile != null) d = d.parentFile!!
            d
        },
        "src/main/java/cc/nkbr/lanzouplus/LanzouCore.java",
    ).readText(Charsets.UTF_8)

    /** 按大括号配对取方法体，避免"截固定长度导致窗口越界到隔壁方法"的假绿。 */
    private fun blockAfter(anchor: String): String {
        val start = coreSource.indexOf(anchor)
        require(start >= 0) { "找不到锚点：$anchor" }
        val open = coreSource.indexOf('{', start)
        var depth = 0
        var i = open
        while (i < coreSource.length) {
            when (coreSource[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return coreSource.substring(open + 1, i)
                }
            }
            i++
        }
        error("未闭合：$anchor")
    }

    @Test
    fun runChecksTheBudgetBeforeItCanWaitForever() {
        val body = blockAfter("List<Models.Item> run()throws InterruptedException{")
        val budgetCheck = body.indexOf("searchBudgetExpired()")
        val firstWait = body.indexOf("wait(50L)")
        assertTrue("run() 必须查总预算", budgetCheck >= 0)
        assertTrue("预算检查必须在第一个等待点之前", budgetCheck in 0 until firstWait)
        assertTrue("预算到点必须走 expireBudgetLocked 收尾", body.contains("expireBudgetLocked()"))
    }

    @Test
    fun executeNetworkReleasesInFlightOnItsFinallyPath() {
        val body = blockAfter("private void executeNetwork(SearchNetworkJob job){")
        val finallyIndex = body.indexOf("finally{synchronized(this){")
        assertTrue("executeNetwork 必须有统一收尾块", finallyIndex >= 0)
        assertTrue(
            "统一收尾块必须先复位在飞标记",
            body.substring(finallyIndex).startsWith("finally{synchronized(this){releaseInFlightLocked(job);"),
        )
    }

    private class RecordingProgress : Models.Progress {
        val failures = CopyOnWriteArrayList<String>()
        val indexed = CopyOnWriteArrayList<String>()
        override fun onProgress(done: Int, total: Int, found: Int, current: String?) = Unit
        override fun onFailure(current: String?) {
            failures.add(current ?: "")
        }

        override fun onIndexSource(sourceId: String?, current: String?) {
            indexed.add(sourceId ?: "")
        }
    }
}
