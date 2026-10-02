package cc.nkbr.lanzouplus

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * [DFW-101] **通用留证通道的接线测试**（不是某个 bug 的埋点测试）。
 *
 * ## 这张卡验收的是"通道"，所以测的也必须是通道
 * 用户原话：「任何不合理问题都要留下现场，**不做针对性埋点**」。
 * 意思是：证据不能靠"谁想到了就在哪里插一行"，而要靠**几个所有异常都必经的出口**。
 * 全项目只有四个这样的出口，测住这四个，就等于测住了"以后任何一个新 bug 都会留下现场"：
 *
 * | 出口 | 覆盖什么 | 位置 |
 * |---|---|---|
 * | `MainActivity.friendlyError` | **任何**会显示给用户的错误（47 个调用点） | `MainActivity.java` |
 * | `MainActivity.showNotice` | 应用对用户说过的**每一句话**（139 处） | `MainActivity.java` |
 * | `App.writeCrashLog` | 任何线程的未捕获异常 + DEGRADED | `App.java` |
 * | `DirectLinkResolver.trace` / `LanzouCore.wafLog` | 解析与 WAF 链路的每一步 | 两个类 |
 *
 * 任何一个出口被改回去（比如有人觉得"这里记日志没必要"顺手删掉），
 * 对应测试必须变红 —— 否则"通用"就退化成"碰运气"。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class DfLogWiringJvmTest {

    @get:Rule val tmp = TemporaryFolder()

    @After
    fun tearDown() {
        DfLog.install(null, null)
        DfLog.clear()
    }

    private fun activity(): MainActivity =
        Robolectric.buildActivity(MainActivity::class.java).setup().get()

    /**
     * 装配临时目录。
     *
     * **必须在 Activity 建好之后调用**：`App.onCreate` 会把 DfLog 指到应用真实目录，
     * 这里覆盖它，测试才不会去动真实文件、也不会读到别的测试留下的内容。
     */
    private fun useTempLog(): File {
        val dir = tmp.newFolder("events")
        DfLog.install(dir, null)
        DfLog.clear()
        return dir
    }

    private fun logText(dir: File): String {
        val file = File(dir, DfLog.LOG_NAME)
        return if (file.isFile) file.readText(Charsets.UTF_8) else ""
    }

    // ---------- 出口 ①：任何用户可见的错误 ----------

    /**
     * `friendlyError` 是全站唯一"把异常变成给人看的话"的地方，47 个调用点都从这里过。
     * 只要它记一笔，"任何会显示给用户的错误"就自动留证 —— 不需要在任何具体 bug 旁边插点。
     */
    @Test
    fun friendlyError_recordsEveryUserVisibleError() {
        val a = activity()
        val dir = useTempLog()

        val message = a.friendlyError(IllegalStateException("cookie 算法变了"))

        assertTrue("先确认它仍然在正常工作：$message", message.isNotEmpty())
        val text = logText(dir)
        assertTrue("用户可见的错误必须自动进事件日志：$text", text.contains("|ui|user-visible-error"))
        assertTrue("必须记下异常类型：$text", text.contains("java.lang.IllegalStateException"))
        assertTrue("必须记下异常消息（定位靠它）：$text", text.contains("cookie 算法变了"))
    }

    /** 同一个出口连续报错时不能漏记，也不能粘行。 */
    @Test
    fun friendlyError_recordsEachOccurrenceOnItsOwnLine() {
        val a = activity()
        val dir = useTempLog()

        a.friendlyError(RuntimeException("第一次"))
        a.friendlyError(RuntimeException("第二次"))

        val text = logText(dir)
        assertTrue("两次都要记下来", text.contains("第一次") && text.contains("第二次"))
        val occurrences = text.split('\n').count { it.contains("|ui|user-visible-error") }
        assertTrue("两次错误必须是两行：实际 $occurrences 行", occurrences >= 2)
    }

    // ---------- 出口 ②：应用对用户说过的话 ----------

    /**
     * `showNotice` 是全站提示的唯一出口（139 处）。
     * 它记的是**面包屑**（纯内存）：用户回头报"卡住了"时，
     * 「什么都没提示」和「提示了但没看懂」是两种完全不同的故障，这条时间线能立刻区分。
     */
    @Test
    fun showNotice_leavesABreadcrumbTrail() {
        val a = activity()
        useTempLog()
        DfLog.clear()

        a.showNotice("下载链接解析中，请稍候", false)

        val crumbs = DfLog.breadcrumbs()
        assertTrue(
            "提示必须进面包屑时间线：$crumbs",
            crumbs.any { it.contains("|notice|") && it.contains("下载链接解析中") },
        )
    }

    // ---------- 出口 ③：崩溃 ----------

    /** 崩溃除了写 `crash.log`，还要进统一事件流（带上面包屑前因）。 */
    @Test
    fun writeCrashLog_alsoFeedsTheEventLog() {
        activity()
        val dir = useTempLog()
        DfLog.breadcrumb("ui", "notice", "msg", "点了一下下载")

        App.writeCrashLogForTest("WIRING-PROBE", IllegalStateException("boom"))

        val text = logText(dir)
        assertTrue("崩溃必须进统一事件流：$text", text.contains("|crash|"))
        assertTrue("必须带崩溃类型", text.contains("WIRING-PROBE"))
        assertTrue("必须带崩溃前的面包屑（只看结果看不到前因）", text.contains("点了一下下载"))
    }

    // ---------- 出口 ④：解析链路（两个类共用一把锁） ----------

    /**
     * `download.log` 历史上由 `DirectLinkResolver` 与 `LanzouCore` **各写一份、都没加锁**，
     * 并发时行会互相插入（DFW-14 只修了 `crash.log`，这个文件漏了）。
     *
     * 这里做**源码级守卫**：两个写入点必须都走 `DfLog.appendLocked`，
     * 且不许再出现自己 `new FileOutputStream(..., true)` 的无锁追加。
     * 用源码扫描而不是反射，是因为这两个方法是 private static、没有可注入的缝；
     * 而这条守卫要防的恰恰是"以后有人又照着老写法抄一遍"。
     */
    @Test
    fun bothDownloadTraceWriters_goThroughTheSharedLock() {
        val resolver = read("app/src/main/java/cc/nkbr/lanzouplus/DirectLinkResolver.java")
        val core = read("app/src/main/java/cc/nkbr/lanzouplus/LanzouCore.java")

        for ((name, source) in listOf("DirectLinkResolver" to resolver, "LanzouCore" to core)) {
            assertTrue(
                "$name 的解析埋点必须走 DfLog.appendLocked（否则又变成无锁写入）",
                source.contains("DfLog.appendLocked("),
            )
            assertTrue(
                "$name 的解析埋点必须同时进统一事件流",
                source.contains("DfLog.event("),
            )
            assertFalse(
                "$name 不得再自己 new FileOutputStream 追加 download.log（无锁写法，DFW-14 复发点）",
                Regex("new\\s+(java\\.io\\.)?FileOutputStream\\(file,\\s*true\\)").containsMatchIn(source),
            )
        }
    }

    // ---------- 导出：记下来还要拿得到 ----------

    /** 报告必须带上事件日志整段 —— 否则又是一次"埋点加了却取不出来"（DFW-88 的教训）。 */
    @Test
    fun buildCrashReport_carriesTheEventLog() {
        val a = activity()
        useTempLog()
        DfLog.failure("resolve", "explode", IllegalStateException("现场内容-MARKER"))

        val report = a.buildCrashReport()

        assertTrue("报告必须带事件日志段：$report", report.contains("应用事件日志"))
        assertTrue("报告必须带现场内容：$report", report.contains("现场内容-MARKER"))
        assertTrue("报告仍必须带环境诊断（没被新段落挤掉）", report.contains("环境诊断"))
        assertTrue("报告仍必须带下载解析日志段（DFW-88 的既有契约）", report.contains("下载解析日志"))
    }

    /**
     * **只有卡死、没有崩溃时，报告也必须非空。**
     *
     * 这条是回归守卫：`buildCrashReport` 原本在"没有崩溃堆栈 + crash.log 为空"时直接返回空串，
     * 而"卡住了/没反应"恰恰**不产生任何崩溃** —— 用户点导出会拿到一份空报告，
     * 看门狗辛苦抓的现场一个字节都发不出来，整张卡就白做了。
     */
    @Test
    fun buildCrashReport_isNotEmptyWhenOnlyAStallHappened() {
        val a = activity()
        clearCrashSources(a)
        useTempLog()
        DfLog.scene("main-thread-stall")

        val report = a.buildCrashReport()

        assertTrue("只有卡死现场时报告不能是空串", report.isNotEmpty())
        assertTrue("必须带上卡死现场", report.contains("main-thread-stall"))
        assertTrue("必须能看出这是应用事件日志段", report.contains("应用事件日志"))
    }

    /** 真的什么都没有时仍要返回空串 —— 否则崩溃页会把"无内容"当成"有内容"显示。 */
    @Test
    fun buildCrashReport_staysEmptyWhenThereIsNothingAtAll() {
        val a = activity()
        clearCrashSources(a)
        useTempLog()

        assertTrue("无崩溃、无日志时必须返回空串", a.buildCrashReport().isEmpty())
    }

    /**
     * 把"崩溃来源"清空，才能验证"什么都没有"这条契约。
     *
     * 必须做：Robolectric 里 `App.onCreate` 会因为 Koin 重复启动而走 DEGRADED 分支写一条
     * `crash.log`，于是报告永远非空 —— 不清掉的话下面两条测试测的其实是别的东西。
     */
    private fun clearCrashSources(a: MainActivity) {
        me.rerere.rikkahub.utils.CrashHandler.clearCrashed(a)
        a.privateCrashLogFile()?.delete()
        App.publicCrashLogFile()?.delete()
    }

    /** 「清除崩溃记录」必须连事件日志一起清，否则清完还能导出旧现场。 */
    @Test
    fun clearCrashArtifacts_alsoClearsTheEventLog() {
        val a = activity()
        val dir = useTempLog()
        DfLog.event("resolve", "trace", "msg", "旧现场")
        assertTrue("前置条件：事件日志已写入", logText(dir).contains("旧现场"))

        a.clearCrashArtifacts()

        assertFalse("清除后不得再残留事件日志", logText(dir).contains("旧现场"))
        assertTrue("面包屑也要清掉", DfLog.breadcrumbCount() == 0)
    }

    private fun read(path: String): String = File(repoRoot(), path).readText(Charsets.UTF_8)

    /**
     * 仓库根：从 `user.dir` 往上找 `AGENTS.md`（与 `StartupPermissionsAndAutoInstallJvmTest` 同一套做法，
     * 免得依赖测试具体的执行目录）。
     */
    private fun repoRoot(): File {
        var dir = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (!File(dir, "AGENTS.md").isFile && dir.parentFile != null) dir = dir.parentFile
        return dir
    }
}
