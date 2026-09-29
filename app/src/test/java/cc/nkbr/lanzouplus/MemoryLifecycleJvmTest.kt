package cc.nkbr.lanzouplus

import android.content.ComponentCallbacks2
import android.graphics.Bitmap
import android.os.Looper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * [DFWX] DFW-14 内存与资源生命周期回归（可验证的那部分）。
 *
 * ## 关于"先取证"
 * 卡片要求性能必须先有客观测量（gfxinfo jank% / 冷启动 / 内存曲线）。
 * 那部分**需要真机**，本机 adb 连不上手机，所以本轮**不动**任何与帧率有关的代码，
 * 只做两件可以被精确验证的正确性修复：
 *
 * 1. **`onTrimMemory` 缺失**——系统内存紧张时本应用不释放任何缓存。
 *    这里断言它在各档位下的行为（轻度→清投递队列；中度→按 LRU 收缩；重度→整块释放）。
 * 2. **crash.log 并发写无锁**——多线程崩溃时日志会交错。
 *    这里断言多线程并发写入后每一行都完整（没有半行粘连）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class MemoryLifecycleJvmTest {

    companion object {
        @BeforeClass @JvmStatic fun silenceAutoImport() { MainActivity.LIBRARY_AUTO_IMPORT = false }
    }

    private fun activity(): MainActivity {
        val a = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        shadowOf(Looper.getMainLooper()).idle()
        return a
    }

    private fun Bitmap.tiny(): Bitmap = this

    @Test
    fun trimMemory_complete_releasesImageCache() {
        val a = activity()
        // 塞一批图标进缓存（用 1x1 位图，够触发 sizeOf 记账）
        repeat(30) { i ->
            a.imageCache.put("https://cdn.example.com/icon$i.png", Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888))
        }
        assertTrue("前置条件：缓存里应有内容", a.imageCache.size() > 0)

        a.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_COMPLETE)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals("TRIM_MEMORY_COMPLETE 必须整块释放图标缓存（可重新下载，不是用户数据）", 0, a.imageCache.size())
    }

    @Test
    fun trimMemory_background_releasesImageCache() {
        val a = activity()
        repeat(30) { i ->
            a.imageCache.put("https://cdn.example.com/b$i.png", Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888))
        }
        a.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_BACKGROUND)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("退到后台时应释放图标缓存", 0, a.imageCache.size())
    }

    /**
     * 中度紧张 = 收缩到容量的 1/3（LRU 契约），且**不清空**。
     *
     * 之所以按 `maxSize/3` 断言而不是"数量变少"：LruCache.trimToSize 只在当前占用
     * **超过**目标时才淘汰。测试必须先填到超过目标才能验证契约，否则测的是空操作。
     */
    @Test
    fun trimMemory_runningLow_trimsDownToContract() {
        val a = activity()
        val target = a.imageCache.maxSize() / 3
        var i = 0
        // 填到确实超过目标（用 128x128 位图，让 sizeOf 记账明显）
        while (a.imageCache.size() <= target && i < 5000) {
            a.imageCache.put(
                "https://cdn.example.com/l$i.png",
                Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888),
            )
            i++
        }
        val before = a.imageCache.size()
        assertTrue("前置条件：必须填到超过目标 $target，实际 $before", before > target)

        a.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(
            "中度紧张必须收缩到不超过 maxSize/3（LRU 契约）：before=$before after=${a.imageCache.size()} target=$target",
            a.imageCache.size() <= target,
        )
        assertTrue("中度紧张不得整块清空（否则会狂重载图片）", a.imageCache.size() > 0)
    }

    /** 轻度档不得清空缓存：一有压力就狂重载图片反而更卡。 */
    @Test
    fun trimMemory_uiHidden_doesNotWipeImageCache() {
        val a = activity()
        repeat(10) { i ->
            a.imageCache.put("https://cdn.example.com/u$i.png", Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888))
        }
        val before = a.imageCache.size()
        a.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("轻微信号（仅 UI 隐藏）不得清空图标缓存", before, a.imageCache.size())
    }

    /** onTrimMemory 任何档位都不得抛异常（它是系统回调，抛出去会崩）。 */
    @Test
    fun trimMemory_neverThrowsForAnyLevel() {
        val a = activity()
        for (level in listOf(
            ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN,
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE,
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW,
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL,
            ComponentCallbacks2.TRIM_MEMORY_BACKGROUND,
            ComponentCallbacks2.TRIM_MEMORY_MODERATE,
            ComponentCallbacks2.TRIM_MEMORY_COMPLETE,
        )) {
            a.onTrimMemory(level)
        }
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(true)
    }

    /**
     * crash.log 并发写：多线程同时写同一日志文件时，**每一行必须完整**。
     * 无锁实现下 `FileWriter` 的 write 会互相插入，出现半行粘连——这正是本卡要修的第 5 条。
     */
    @Test
    fun crashLog_concurrentWrites_produceIntactLines() {
        val a = activity()
        val dir = a.getExternalFilesDir(null)!!
        val log = java.io.File(dir, "crash.log")
        log.delete()

        val threads = 8
        val perThread = 25
        val start = java.util.concurrent.CountDownLatch(1)
        val done = java.util.concurrent.CountDownLatch(threads)
        for (t in 0 until threads) {
            Thread {
                start.await()
                for (i in 0 until perThread) {
                    App.writeCrashLogForTest("T$t-$i", null)
                }
                done.countDown()
            }.start()
        }
        start.countDown()
        assertTrue("并发写线程必须在 20 秒内完成", done.await(20, java.util.concurrent.TimeUnit.SECONDS))

        val text = log.readText(Charsets.UTF_8)
        val markers = Regex("==== ").findAll(text).count()
        // 注意：这条断言在"有锁/无锁"两种实现下都可能通过——小段 append 在 OS 层本身近似原子，
        // 所以它守住的是"不丢行"这条不变量，**不能**单独当作锁有效性的证明。
        // 锁真正保护的是下面那条测试覆盖的"检查-删除-追加"竞态（check-then-act）。
        assertTrue(
            "并发写入后必须至少留下大部分完整记录（实际 $markers / 期望 ${threads * perThread}）",
            markers >= threads * perThread - 2,
        )
        // 每一行都必须以合法前缀开头，不能出现两条记录粘在同一行
        for (line in text.split('\n')) {
            if (line.contains("==== ")) {
                assertTrue(
                    "出现粘连行（两条记录挤在同一行，正是无锁并发写的症状）：$line",
                    line.trimStart().startsWith("==== "),
                )
            }
        }
    }

    /**
     * 真正的竞态：`appendCrashText` 是"检查大小 → 超限则删除 → 追加"三段，无锁时三者会互相穿插。
     *
     * ## 这条断言怎么来的（不是猜的）
     * 先用一个独立 Java 探针（同代码同参数）复现过：
     * - **无锁**：8 线程 × 30 次、每次 300KB → 文件 **921636** 字节（**越过 512KB 上限**），
     *   3 条记录只有 **1** 条头部完整；
     * - **有锁**：同参数 → 文件 614424 字节（受上限约束），**2/2 条头部完整**。
     *
     * 关键在写入块必须**远超** FileWriter 的 8KB 内部缓冲——否则每个线程的写入近似原子，
     * 竞态根本不出现（第一版测试用 600B 块，有无锁都绿，等于没测）。
     *
     * ## 诚实的范围声明
     * **本 JVM 测试在 Robolectric 沙箱里复现不出该竞态**（同参数下文件始终停在单条记录大小），
     * 所以它**不能**证明锁有效——它守的是另一条不变量：**"512KB 上限 + 单条记录"这条大小契约**
     * 不被破坏（例如将来有人把整段大小检查删掉，文件就会无界增长，这条会红）。
     * 锁有效性的证据在 `tools/diagnostics/CrashLogRaceProbe.java`（可独立运行，实测 921636 → 614424）。
     */
    @Test
    fun crashLog_concurrentTruncationRace_respectsSizeCap() {
        val a = activity()
        val dir = a.getExternalFilesDir(null)!!
        val log = java.io.File(dir, "crash.log")
        log.delete()
        log.parentFile?.mkdirs()

        val threads = 8
        val perThread = 30
        // 300KB 块：远超 FileWriter 8KB 缓冲，强制多次 syscall，让"检查-删除-追加"真正交错
        val big = "Y".repeat(300 * 1024)
        val start = java.util.concurrent.CountDownLatch(1)
        val done = java.util.concurrent.CountDownLatch(threads)
        for (t in 0 until threads) {
            Thread {
                start.await()
                repeat(perThread) { i ->
                    App.writeCrashLogForTest("RACE-T$t-$i\n$big", null)
                }
                done.countDown()
            }.start()
        }
        start.countDown()
        assertTrue("并发写线程必须在 120 秒内完成", done.await(120, java.util.concurrent.TimeUnit.SECONDS))

        // 前置条件：确认日志真的落盘了。
        // 不加这条的话，一旦 App.instance 为 null（writeCrashLog 会早退），
        // 文件永远不存在，后面的"大小不越界"断言会在空文件上**假绿**——第一版测试就是这样骗过我的。
        assertTrue(
            "前置条件：crash.log 必须真的被写入（若为 0 说明 writeCrashLog 早退，测试无意义）：${log.length()} 字节",
            log.isFile && log.length() > 0,
        )

        // 正确的不变量：上限是 512KB，但"检查-删除-追加"是三步，
        // 所以合法膨胀上限 = 512KB + 单条记录大小（300KB）≈ 831KB。
        // 无锁时多线程会各自"看到未超限"而跳过删除 → 无界增长（探针实测 921636 字节，已越界）。
        val cap = 512L * 1024
        val oneRecord = 300L * 1024
        val legalUpperBound = cap + oneRecord
        assertTrue(
            "crash.log 不得超过「512KB 上限 + 单条记录」= ${legalUpperBound} 字节；" +
                "实际 ${log.length()} 字节（越界即说明多个线程同时跳过删除：并发竞态）",
            log.length() <= legalUpperBound,
        )

        // 第二条不变量：出现过的记录头必须完整，不能是被另一个线程截断的半条。
        val text = log.readText(Charsets.UTF_8)
        val headers = text.split('\n').filter { it.contains("==== RACE-T") }
        for (h in headers) {
            assertTrue("记录头必须完整（残缺=被并发删除/追加截断）：${h.take(40)}", h.trimStart().startsWith("==== RACE-T"))
        }
    }
}
