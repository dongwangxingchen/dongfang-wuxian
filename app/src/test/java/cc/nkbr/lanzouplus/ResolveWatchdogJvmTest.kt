package cc.nkbr.lanzouplus

import android.os.Looper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.time.Duration

/**
 * [DFW-125 2026-10-03] 解析看门狗。
 *
 * ## 为什么这个测试必须存在
 * 用户**从 v1.0.5 一路报到 v1.0.12**，症状始终是「下载永远停在解析中」：
 * 不崩溃、不报错、不超时，界面无限等待。
 *
 * DFW-101（2026-10-01）修过一次，**没修好**。原因很隐蔽：
 * 当时把看门狗写在 `updateDownloadUi()` 里，靠"这个函数被反复调用"来计时。
 * 但 `updateDownloadUi()` 根本没有周期性调用者 ——
 * 解析**成功**才有进度回调，解析**失败**才有失败回调，
 * 而它要防的恰恰是"解析器什么都不回调"。
 * **看门狗在它唯一要防的场景里是死的。**
 *
 * ## 所以这些用例守的是"独立性"
 * 每一个都**刻意不调用** `updateDownloadUi()`，直接排定时器 + 推进虚拟时钟。
 * 只要有人再把看门狗改回"寄生在别的调用上"，这里立刻红。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-560dpi")
class ResolveWatchdogJvmTest {

    companion object {
        @BeforeClass @JvmStatic fun silenceAutoImport() { MainActivity.LIBRARY_AUTO_IMPORT = false }

        /** 比 RESOLVE_WATCHDOG_MS(25000) 多一秒，确保越过阈值。 */
        private const val PAST_TIMEOUT_MS = 26_000L

        private val repoRoot: File = run {
            var dir = File(System.getProperty("user.dir") ?: ".").absoluteFile
            while (!File(dir, "app/src/main/AndroidManifest.xml").isFile && dir.parentFile != null) dir = dir.parentFile!!
            dir
        }
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    /** 推进虚拟时钟 —— 这是本测试能验证"超时"的关键，不用真的等 25 秒。 */
    private fun advance(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    private fun activity(): MainActivity {
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        controller.setup()
        idle()
        return controller.get()
    }

    private fun stuckEntry(name: String = "卡住的文件.zip"): MainActivity.DownloadEntry {
        val e = MainActivity.DownloadEntry()
        e.name = name
        e.shareUrl = "https://example.com/abc"
        e.state = MainActivity.DOWNLOAD_RESOLVING
        return e
    }

    /**
     * **核心用例：一次都不调用 `updateDownloadUi()`，看门狗也必须自己触发。**
     *
     * 这正是 DFW-101 失败的地方 —— 旧实现只有 `updateDownloadUi()` 被再次调用才会检查，
     * 而解析器卡住时它永远不会被调用。
     */
    @Test
    fun theWatchdogFiresOnItsOwnWithoutAnyOtherCall() {
        val a = activity()
        val e = stuckEntry()
        a.downloadEntries.add(e)

        // 注意：这里**没有**调用 updateDownloadUi —— 就是要证明它不依赖那个。
        a.armResolveWatchdog(e, e.controlGeneration)
        idle()

        assertEquals("还没到点就不该动它，否则正常解析会被误杀", MainActivity.DOWNLOAD_RESOLVING, e.state)

        advance(PAST_TIMEOUT_MS)

        assertEquals("到点必须判失败，不许永远转圈", MainActivity.DOWNLOAD_FAILED, e.state)
        assertTrue(
            "失败原因必须说人话（用户看不懂堆栈）：实际是「${e.error}」",
            e.error.contains("解析超时"),
        )
        assertTrue("必须写明等了多少秒，用户才知道要不要重试", e.error.contains("25"))
        assertEquals("失败后速度必须清零", 0L, e.speedBps)
    }

    /**
     * 解析**已经成功**之后再超时，不许把它改成失败。
     *
     * 真实场景：解析在第 24 秒成功、开始传输，第 25 秒定时器才到点 ——
     * 如果这里不校验状态，就会把一个正在下载的任务打成失败。
     */
    @Test
    fun theWatchdogDoesNotKillAnEntryThatAlreadyResolved() {
        val a = activity()
        val e = stuckEntry()
        a.downloadEntries.add(e)
        a.armResolveWatchdog(e, e.controlGeneration)
        idle()

        // 定时器到点前，解析成功并进入下载中
        e.state = MainActivity.DOWNLOAD_RUNNING
        advance(PAST_TIMEOUT_MS)

        assertEquals("已经不在解析中了，定时器不许动它", MainActivity.DOWNLOAD_RUNNING, e.state)
        assertTrue("不许写入失败原因", e.error.isEmpty())
    }

    /**
     * 用户**重试**之后，旧定时器不许误杀新的一次解析。
     *
     * 真实场景：第 1 次解析卡住 → 用户点重试（`controlGeneration` 自增）→
     * 第 1 次的定时器这时才到点。如果不比对 generation，就会把**刚开始的第 2 次**打成失败，
     * 用户会觉得"点了重试立刻就失败"，比不修还糟。
     */
    @Test
    fun theWatchdogDoesNotKillANewerResolveAttempt() {
        val a = activity()
        val e = stuckEntry()
        a.downloadEntries.add(e)

        val staleGeneration = e.controlGeneration
        a.armResolveWatchdog(e, staleGeneration)
        idle()

        // 用户重试：generation 自增，重新进入解析中
        e.controlGeneration = staleGeneration + 1
        e.state = MainActivity.DOWNLOAD_RESOLVING

        advance(PAST_TIMEOUT_MS)

        assertEquals(
            "旧定时器属于上一次解析，不许把用户刚重试的这一次打成失败",
            MainActivity.DOWNLOAD_RESOLVING,
            e.state,
        )
    }

    /** 已经取消的任务，定时器到点也不许改状态。 */
    @Test
    fun theWatchdogLeavesCancelledEntriesAlone() {
        val a = activity()
        val e = stuckEntry()
        a.downloadEntries.add(e)
        a.armResolveWatchdog(e, e.controlGeneration)
        idle()

        e.stopRequested = true
        e.state = MainActivity.DOWNLOAD_CANCELLED
        advance(PAST_TIMEOUT_MS)

        assertEquals(MainActivity.DOWNLOAD_CANCELLED, e.state)
        assertTrue("取消的任务不该被写成失败原因", e.error.isEmpty())
    }

    /**
     * 超时阈值必须是**有限**的。
     *
     * 这条看着废话，但它守的是这次修复的实质：
     * 只要 `RESOLVE_WATCHDOG_MS` 还是有限值，卡住的解析就一定会以失败告终；
     * 哪天有人把它改成 0 或无限，用户就又要对着永远转的「解析中」干等。
     */
    @Test
    fun theTimeoutIsFiniteAndShortEnoughToBeUseful() {
        val ms = MainActivity.RESOLVE_WATCHDOG_MS
        assertTrue("超时阈值必须是正数，实测 $ms", ms > 0)
        assertTrue("超过 60 秒的等待对用户来说就是「卡死了」，实测 ${ms}ms", ms <= 60_000L)
    }

    /**
     * **接线守卫：定时器必须真的被排上。**
     *
     * 上面那些用例是**直接调用** `armResolveWatchdog` 的，所以它们只能证明"机制本身对"，
     * 证明不了"它被接在了下载流程上"。少了这一条，有人把调用点删掉，
     * 上面 5 条照样全绿，而用户的「解析中」又回到永远转圈 —— 正是 DFW-101 的翻版。
     */
    @Test
    fun theWatchdogIsActuallyArmedWhenAnEntryStartsResolving() {
        val source = File(repoRoot, "app/src/main/java/cc/nkbr/lanzouplus/MainActivity.java").readText()
        assertTrue(
            "resolveEntryDownload 在进入「解析中」时必须排上定时器，否则看门狗就是个没人调用的摆设",
            source.contains("if(waiting)armResolveWatchdog(entry,generation);"),
        )
    }

    /**
     * **旧的寄生式看门狗必须彻底消失。**
     *
     * 它（`updateDownloadUi()` 里靠重复调用计时的那套）在它唯一要防的场景里是死的。
     * 留着它不只是死代码 —— 它会让下一个读代码的人以为"已经有超时了"，从而不去找真正的原因。
     */
    @Test
    fun theOldParasiticWatchdogIsGoneForGood() {
        val source = File(repoRoot, "app/src/main/java/cc/nkbr/lanzouplus/MainActivity.java").readText()
        assertFalse(
            "旧的寄生式看门狗（resolveWatchdog.put）必须删除，不许与新的定时器并存",
            source.contains("resolveWatchdog.put("),
        )
        assertFalse(
            "那个只用于寄生计时的 Map 字段也该一并删掉",
            source.contains("Map<DownloadEntry,Long> resolveWatchdog"),
        )
    }
}
