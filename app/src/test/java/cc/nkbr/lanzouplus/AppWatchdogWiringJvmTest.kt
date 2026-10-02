package cc.nkbr.lanzouplus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * [DFW-124 2026-10-02] `App.onCreate()` **不许在 JVM 单元测试里启动主线程卡死看门狗**。
 *
 * ## 为什么这条值得单独守
 *
 * 看门狗的判据是「往主线程消息队列 post 一个打卡任务，超时还没跑到 = 主线程卡死」。
 * 而 Robolectric 的 Looper 默认是 **PAUSED** —— 队列里的任务**不会自己跑**。
 * 于是打卡永远完不成，**每个活得超过 5 秒的测试类都会被判成"主线程卡死"**，
 * 白写一份现场（`DfLog.scene()` 会抓 `DfLog.MAX_SCENE_THREADS` 条线程栈）。
 *
 * 这不是"测试环境的小毛病"：它会往日志里灌**假事故**，
 * 而这份日志存在的意义恰恰是"用户报问题时留下真实现场"—— 假的会把真的挤掉。
 *
 * ## 反向探针
 *
 * 把 `App.installEventLog()` 里那句 `if (isJvmUnitTest()) return;` 删掉，本测试必须变红。
 * 如果删了还绿，说明这条守卫是假的。
 *
 * 注意：看门狗本身的行为**没有失去覆盖**——
 * `MainThreadStallWatchdogJvmTest` 是直接调 `start(阈值, 冷却)` 测的，不经过 `App`。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class AppWatchdogWiringJvmTest {

    private fun watchdogThreads(): Int =
        Thread.getAllStackTraces().keys.count { it.name == "dfwx-stall-watchdog" }

    /**
     * Robolectric 在测试方法执行**之前**就已经把 Application 建好了，
     * 也就是说 `onCreate()` 早就跑过 —— 所以这里不需要"造一个 Application"，
     * 直接检查结果即可：测试进程里**不该存在**看门狗线程。
     *
     * 用 `RuntimeEnvironment.getApplication()` 只是为了**确保** Application 已初始化
     * （它本身就是那个触发点），避免测试在"Application 还没建"的状态下空跑。
     */
    @Test
    fun appDoesNotStartTheStallWatchdogUnderJvmUnitTests() {
        RuntimeEnvironment.getApplication()

        // 看门狗是异步起线程的，给它一点时间冒出来（真起了的话）。
        Thread.sleep(300L)

        assertEquals(
            "Robolectric 的 Looper 是暂停的，看门狗在这里必然误报「主线程卡死」，" +
                "会往日志里灌假事故 —— App.onCreate() 里必须先判 isJvmUnitTest() 再启动它",
            0,
            watchdogThreads(),
        )
    }

    /**
     * 反向确认判据本身是有效的：测试环境里 `isJvmUnitTest()` 必须为真。
     *
     * 这条是防"守卫退化成空测试"：如果哪天判据失效了（Robolectric 改包名、
     * 或者有人把判断写反），上一条测试会因为"看门狗压根没起"而**永远是绿的**，
     * 而它本该在有人删掉守卫时变红。所以单独把判据本身钉住。
     */
    @Test
    fun theJvmUnitTestDetectorItselfWorks() {
        val m = App::class.java.getDeclaredMethod("isJvmUnitTest")
        m.isAccessible = true
        assertTrue(
            "测试跑在 Robolectric 里，这个判据必须为 true —— 否则上一条守卫会退化成恒真的空测试",
            m.invoke(null) as Boolean,
        )
    }
}
