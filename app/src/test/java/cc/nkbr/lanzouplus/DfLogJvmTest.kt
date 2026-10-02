package cc.nkbr.lanzouplus

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * [DFW-101] 统一事件日志（`DfLog`）直测。
 *
 * ## 这张卡要证明的三件事
 * 1. **留得下**：任何异常路径写进来，都能在文件里找到结构化的一行（时间/线程/区域/事件/上下文）；
 * 2. **不会涨爆**：有明确容量上限 + 滚动，写再多磁盘占用也有硬上限；
 * 3. **并发不撕行**：多线程同时写，行与行不互相插入 —— 这是 DFW-14 的历史问题，
 *    当时只修了 `crash.log`，而 `download.log` 有两个类在写、谁都没加锁，一直漏着。
 *
 * ## 关于第 3 条的诚实说明
 * 「小段 append 在 OS 层本身近似原子」，所以**光看文件内容**的并发测试在有锁/无锁下都可能是绿的
 * （本项目在 DFW-14 上已经吃过一次这个亏，见 `MemoryLifecycleJvmTest` 里的注释）。
 * 所以这里额外用 `DfLog.maxConcurrentWriters()` 直接量「同一时刻有几个线程在临界区里」：
 * 有锁恒为 1，把 `synchronized` 去掉必然 > 1。**这条才是反向探针会变红的那条。**
 */
class DfLogJvmTest {

    @get:Rule val tmp = TemporaryFolder()

    @After
    fun tearDown() {
        // 不把临时目录留给后面的测试类
        DfLog.install(null, null)
        DfLog.clear()
    }

    private fun install(name: String): File {
        val dir = tmp.newFolder(name)
        DfLog.install(dir, null)
        DfLog.clear()
        return dir
    }

    // ---------- ① 留得下：结构化一行 ----------

    @Test
    fun event_writesOneStructuredLineWithTimestampThreadAreaAndContext() {
        val dir = install("basic")
        DfLog.event("resolve", "start", "url", "https://example.com/x", "n", 3)

        val text = File(dir, DfLog.LOG_NAME).readText(Charsets.UTF_8)
        val lines = text.trim().split('\n')
        assertEquals("一条事件只能占一行（多行会被读成好几条事件）", 1, lines.size)

        val line = lines[0]
        assertTrue("必须带 DFX| 前缀（lessons.md 第七节的结构化日志约定）：$line", line.startsWith(DfLog.PREFIX))
        assertTrue("必须带区域：$line", line.contains("|resolve|"))
        assertTrue("必须带事件名：$line", line.contains("|start|"))
        assertTrue("必须带 key=value 上下文：$line", line.contains("|url=https://example.com/x|"))
        assertTrue("数字上下文也要在：$line", line.contains("|n=3"))
        assertTrue(
            "必须带线程名（不知道哪个线程干的就没法定位）：$line",
            line.contains("|" + Thread.currentThread().name + "|"),
        )
        assertTrue("必须带时间戳：$line", Regex("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3}").containsMatchIn(line))
    }

    /** 多行消息必须被压成一行 —— 否则一条日志会被读成好几条，现场反而更难看清。 */
    @Test
    fun formatLine_flattensNewlinesIntoOneLine() {
        val line = DfLog.formatLine("ui", "boom", "msg", "第一行\n第二行\r\n第三行")
        assertFalse("不得残留换行：$line", line.contains('\n'))
        assertFalse("不得残留回车：$line", line.contains('\r'))
        assertTrue("内容必须保留（只是被压平）：$line", line.contains("第一行 第二行 第三行"))
    }

    /** 超大字段必须截断并说明截了多少 —— 否则一条消息就能把整个文件吃掉。 */
    @Test
    fun formatLine_truncatesHugeFieldAndReportsHowMuchWasDropped() {
        val line = DfLog.formatLine("ui", "boom", "msg", "X".repeat(5000))
        assertTrue("必须截断：实际长度 ${line.length}", line.length < 2000)
        assertTrue("必须写明截断了多少：$line", line.contains("截断 4488 字"))
    }

    @Test
    fun formatLine_ignoresDanglingKeyWithoutValue() {
        val line = DfLog.formatLine("a", "b", "onlyKey")
        assertFalse("奇数个参数时最后一个必须被忽略，而不是拼出 onlyKey=null：$line", line.contains("onlyKey"))
    }

    /**
     * 连续写多条事件，文件里必须是**一行一条**。
     *
     * 这条是回归守卫：第一版实现只把 `text` 原样追加，而单条事件**没有结尾换行**，
     * 于是 N 条事件在文件里粘成一整行 —— 文件看着有内容，实际已无法按行解析，
     * 导出时会被当成"半行"整段丢掉（日志等于没有）。
     */
    @Test
    fun event_multipleEventsStayOnePerLine() {
        val dir = install("newlines")
        repeat(5) { i -> DfLog.event("load", "tick", "i", i) }

        val text = File(dir, DfLog.LOG_NAME).readText(Charsets.UTF_8)
        assertTrue("文件必须以换行结尾（否则最后一条会与下一条粘连）", text.endsWith("\n"))
        val lines = text.trim().split('\n')
        assertEquals("5 条事件必须是 5 行，不能粘成一行：${lines.size}", 5, lines.size)
        for ((index, line) in lines.withIndex()) {
            assertTrue("第 $index 行必须是一条完整事件：$line", line.startsWith(DfLog.PREFIX))
            assertTrue("第 $index 行必须带自己的序号：$line", line.contains("|i=$index"))
        }
    }

    // ---------- ② 不会涨爆：容量上限 + 滚动 ----------

    /**
     * 写满就滚动：当前文件改名成 `.1`（覆盖旧的 `.1`），新文件从零开始。
     * 断言的是**磁盘总占用有硬上限**（`MAX_BYTES × MAX_FILES`），而不是具体哪个文件有多大。
     */
    @Test
    fun event_rollsOverAndKeepsDiskUsageBounded() {
        val dir = install("rotate")
        val pad = "Z".repeat(600)

        var written = 0
        // 上限 256KB、每条约 700 字节 → 400 条足够触发至少一次滚动，留足余量
        while (written < 800 && !File(dir, DfLog.ARCHIVE_NAME).isFile) {
            DfLog.event("load", "tick", "pad", pad)
            written++
        }

        val archive = File(dir, DfLog.ARCHIVE_NAME)
        assertTrue("写了 $written 条（约 ${written * 700 / 1024}KB）后必须出现归档文件", archive.isFile)

        val current = File(dir, DfLog.LOG_NAME)
        assertTrue(
            "当前文件不得超过单文件上限：${current.length()} > ${DfLog.MAX_BYTES}",
            current.length() <= DfLog.MAX_BYTES,
        )
        val total = current.length() + archive.length()
        assertTrue(
            "磁盘总占用必须有硬上限（实际 ${total / 1024}KB，上限 ${DfLog.MAX_BYTES * DfLog.MAX_FILES / 1024}KB）",
            total <= DfLog.MAX_BYTES.toLong() * DfLog.MAX_FILES,
        )
    }

    /** 归档里必须是**较老**的那段，当前文件是较新的 —— 滚动方向搞反会把现场丢掉。 */
    @Test
    fun event_archiveKeepsOlderEventsAndCurrentKeepsNewer() {
        val dir = install("rotate-order")
        val pad = "Z".repeat(600)
        var written = 0
        while (written < 800 && !File(dir, DfLog.ARCHIVE_NAME).isFile) {
            DfLog.event("load", "tick", "pad", pad)
            written++
        }
        // 滚动之后至少再写一条，保证两个文件都有内容
        DfLog.event("load", "after-roll", "pad", pad)

        val current = File(dir, DfLog.LOG_NAME).readText(Charsets.UTF_8)
        val archive = File(dir, DfLog.ARCHIVE_NAME).readText(Charsets.UTF_8)
        assertTrue("当前文件必须含有滚动之后写的那条", current.contains("|after-roll|"))
        assertFalse("归档里不该有滚动之后的事件", archive.contains("|after-roll|"))
    }

    /** 通用加锁原语自己的容量兜底（历史 `download.log` 就是 256KB 清空语义）。 */
    @Test
    fun appendLocked_capsLegacyFileSize() {
        val dir = install("legacy-cap")
        val legacy = File(dir, "download.log")
        repeat(6) { DfLog.appendLocked(legacy, "L".repeat(100_000)) }
        assertTrue(
            "超过上限时必须清空重来，而不是无限追加：${legacy.length()}",
            legacy.length() <= DfLog.MAX_BYTES + 100_000L,
        )
    }

    // ---------- ③ 并发不撕行（DFW-14 的通用解） ----------

    /**
     * **锁有效性的确定性证据**（这条就是反向探针会变红的那条）。
     *
     * `maxConcurrentWriters()` 量的是「同一时刻身处临界区的线程数」。
     * 锁真的生效 → 恒为 1；把 `synchronized` 去掉 → 6 个线程各写 128KB，必然 > 1。
     * 不依赖"文件内容看起来对不对"这种近似原子的假象。
     */
    @Test
    fun appendLocked_keepsWritersMutuallyExclusive() {
        val dir = install("lock")
        val legacy = File(dir, "download.log")
        DfLog.resetConcurrencyProbe()

        val threads = 6
        val payload = "Y".repeat(128 * 1024)
        val start = CountDownLatch(1)
        val done = CountDownLatch(threads)
        for (t in 0 until threads) {
            Thread {
                start.await()
                DfLog.appendLocked(legacy, "T$t#$payload#END")
                done.countDown()
            }.start()
        }
        start.countDown()
        assertTrue("并发写线程必须在 30 秒内完成", done.await(30, TimeUnit.SECONDS))

        assertEquals(
            "有锁时任何时刻只能有一个线程在临界区；>1 说明锁没生效（DFW-14 的复现条件）",
            1,
            DfLog.maxConcurrentWriters(),
        )
    }

    /**
     * 并发写完的每一行都必须完整、可识别。
     *
     * 诚实声明：小段 append 在 OS 层近似原子，**这条在无锁实现下也可能是绿的**，
     * 所以它守的是"不丢行 / 不粘连"这条不变量，锁的证明在上面那条。
     * 总写入量刻意压在单文件上限以内，避免容量兜底把文件清掉导致断言失真。
     */
    @Test
    fun appendLocked_concurrentWritersLeaveEveryLineIntact() {
        val dir = install("lock-content")
        val legacy = File(dir, "download.log")

        val threads = 8
        val payload = "Y".repeat(12 * 1024)
        val start = CountDownLatch(1)
        val done = CountDownLatch(threads)
        for (t in 0 until threads) {
            Thread {
                start.await()
                DfLog.appendLocked(legacy, "T$t#$payload#END")
                done.countDown()
            }.start()
        }
        start.countDown()
        assertTrue("并发写线程必须在 30 秒内完成", done.await(30, TimeUnit.SECONDS))

        val lines = legacy.readText(Charsets.UTF_8).trim().split('\n')
        assertEquals("8 个线程各写一条，且总量未触顶，应当正好 8 行", threads, lines.size)
        val expected = Regex("T\\d+#Y+#END")
        for (line in lines) {
            assertTrue("出现被撕开的行（正是无锁并发写的症状）：${line.take(40)}…", expected.matches(line))
        }
        assertEquals(
            "8 个不同的线程编号都该出现",
            (0 until threads).map { "T$it#" }.toSet(),
            lines.map { it.substringBefore('#') + "#" }.toSet(),
        )
    }

    // ---------- 面包屑：出事前发生了什么 ----------

    /** 面包屑必须只留内存、有硬上限 —— 否则高频动作会把内存吃光。 */
    @Test
    fun breadcrumb_isBoundedToCapacityAndKeepsNewest() {
        val dir = install("crumbs")
        repeat(DfLog.BREADCRUMB_CAPACITY + 40) { i -> DfLog.breadcrumb("ui", "tick", "i", i) }

        assertEquals("面包屑条数必须有硬上限", DfLog.BREADCRUMB_CAPACITY, DfLog.breadcrumbCount())
        val all = DfLog.breadcrumbs()
        assertTrue("必须保留最新的那条", all.last().contains("|i=${DfLog.BREADCRUMB_CAPACITY + 39}"))
        assertTrue("必须丢掉最老的那条", all.none { it.contains("|i=0") })

        // 面包屑是纯内存的：不该因此产生日志文件
        assertFalse("面包屑不该落盘", File(dir, DfLog.LOG_NAME).exists())
    }

    // ---------- 出事自动抓现场 ----------

    /** 失败记录必须带异常类名、消息、堆栈，以及**出事前的面包屑**。 */
    @Test
    fun failure_carriesStackAndRecentBreadcrumbs() {
        val dir = install("failure")
        DfLog.breadcrumb("resolve", "step", "name", "拿到分享页")
        DfLog.breadcrumb("resolve", "step", "name", "解 WAF cookie")
        DfLog.failure("resolve", "explode", IllegalStateException("cookie 算法变了"), "url", "https://x/y")

        val text = File(dir, DfLog.LOG_NAME).readText(Charsets.UTF_8)
        assertTrue("必须记下异常类型", text.contains("java.lang.IllegalStateException"))
        assertTrue("必须记下异常消息", text.contains("cookie 算法变了"))
        assertTrue("必须带上下文", text.contains("|url=https://x/y"))
        assertTrue("必须带堆栈", text.contains("at cc.nkbr.lanzouplus.DfLogJvmTest"))
        assertTrue("必须带出事前的面包屑（否则只能看到结果、看不到前因）", text.contains("解 WAF cookie"))
        assertTrue("面包屑要标出是「出事前」", text.contains("出事前最近"))
    }

    /** 卡死现场必须带**多个线程**的栈 + 线程状态 —— 只抓主线程会漏掉持锁的真凶。 */
    @Test
    fun scene_capturesThreadStacksWithState() {
        val dir = install("scene")
        DfLog.breadcrumb("ui", "notice", "msg", "下载中")
        DfLog.scene("main-thread-stall")

        val text = File(dir, DfLog.LOG_NAME).readText(Charsets.UTF_8)
        assertTrue("必须写明触发原因", text.contains("|reason=main-thread-stall"))
        assertTrue("必须写明抓了多少线程", text.contains("线程现场（共 "))
        assertTrue("必须含当前线程的栈", text.contains(Thread.currentThread().name))
        assertTrue("必须带线程状态（BLOCKED 与 WAITING 指向完全不同的根因）", text.contains("RUNNABLE"))
        assertTrue("卡死现场也要带面包屑", text.contains("下载中"))
        assertTrue("结尾必须再写一次原因（导出走尾部窗口，标题会被切掉）", text.contains("现场结束：main-thread-stall"))
    }

    /**
     * 现场不能无限大。
     *
     * 导出只取最近 24K；一个几十线程 × 30 帧的现场能到 90KB ——
     * 被切掉的恰恰是**开头那行"为什么抓这个现场"**，用户发过来就是一堆没有标题的线程栈。
     * 所以现场必须有明确上限，并且结尾补一行标题兜底。
     */
    @Test
    fun scene_isBoundedAndKeepsItsReasonInTheTail() {
        val dir = install("scene-size")
        DfLog.scene("main-thread-stall")

        val text = File(dir, DfLog.LOG_NAME).readText(Charsets.UTF_8)
        val threadsShown = Regex("\\n  ([^\\n]+)\\|(RUNNABLE|BLOCKED|WAITING|TIMED_WAITING|NEW|TERMINATED)\\|prio=")
            .findAll(text).count()
        assertTrue(
            "展开的线程数必须有上限（实际 $threadsShown，上限 ${DfLog.MAX_SCENE_THREADS}）",
            threadsShown <= DfLog.MAX_SCENE_THREADS,
        )
        assertTrue(
            "整个现场必须明显小于导出窗口（实际 ${text.toByteArray(Charsets.UTF_8).size} 字节，" +
                "窗口 ${DfLog.EXPORT_BYTES}），否则标题会被切掉",
            text.toByteArray(Charsets.UTF_8).size < DfLog.EXPORT_BYTES,
        )
        assertTrue(
            "标题行必须在导出窗口内（尾部窗口会切掉开头）",
            DfLog.readForExport().contains("main-thread-stall"),
        )
    }

    // ---------- 导出 / 清理 ----------

    @Test
    fun readForExport_returnsContentAndNeverStartsWithHalfLine() {
        val dir = install("export")
        repeat(300) { i -> DfLog.event("load", "tick", "i", i, "pad", "P".repeat(200)) }

        val exported = DfLog.readForExport()
        assertTrue("导出内容不能为空", exported.isNotEmpty())
        assertTrue(
            "导出不得超过上限：${exported.toByteArray(Charsets.UTF_8).size}",
            exported.toByteArray(Charsets.UTF_8).size <= DfLog.EXPORT_BYTES + 1,
        )
        assertTrue("导出必须以完整的一行开头（半行会被误读成日志损坏）", exported.startsWith(DfLog.PREFIX))
        assertTrue("必须带最近的内容", exported.contains("|i=299"))
    }

    @Test
    fun readForExport_isEmptyWhenNothingWritten() {
        install("export-empty")
        assertEquals("", DfLog.readForExport())
    }

    @Test
    fun clear_removesLogsAndBreadcrumbs() {
        val dir = install("clear")
        DfLog.event("a", "b")
        DfLog.breadcrumb("a", "c")
        assertTrue(File(dir, DfLog.LOG_NAME).isFile)

        DfLog.clear()
        assertFalse("日志文件必须被清掉", File(dir, DfLog.LOG_NAME).exists())
        assertFalse("归档也要清掉", File(dir, DfLog.ARCHIVE_NAME).exists())
        assertEquals("面包屑也要清掉（否则清除后仍能读到旧现场）", 0, DfLog.breadcrumbCount())
    }

    /** 没装配目录时整个日志系统必须退化成空操作，**绝不许抛**（否则日志本身成了崩溃源）。 */
    @Test
    fun neverThrowsWhenNotInstalled() {
        DfLog.install(null, null)
        DfLog.clear()
        DfLog.event("a", "b", "k", "v")
        DfLog.breadcrumb("a", "b")
        DfLog.failure("a", "b", RuntimeException("x"))
        DfLog.scene("x")
        DfLog.appendLocked(null, "x")
        DfLog.appendLocked(File(tmp.root, "nope/x.log"), null)

        assertEquals("没有目录就没有可导出的内容", "", DfLog.readForExport())
        /*
         * 面包屑**故意不跟着 install 一起失效**：它只占内存、有硬上限，
         * 而且如果目录是稍后才可用的（外部存储挂载晚），先前那条时间线正是最有价值的。
         * 所以这里断言的是"有上限、不抛"，不是"清零"。
         */
        assertTrue(
            "没有目录时面包屑仍要受容量约束（实际 ${DfLog.breadcrumbCount()}）",
            DfLog.breadcrumbCount() <= DfLog.BREADCRUMB_CAPACITY,
        )
    }

    /** 目录不可写（传一个"文件"当目录）时也不许抛。 */
    @Test
    fun neverThrowsWhenDirectoryIsActuallyAFile() {
        val file = File(tmp.root, "not-a-dir.txt").apply { writeText("x") }
        DfLog.install(file, null)
        DfLog.event("a", "b")
        DfLog.failure("a", "b", RuntimeException("x"))
        assertEquals("写不进去就是空串，而不是抛异常", "", DfLog.readForExport())
        assertNotNull(file)
    }
}
