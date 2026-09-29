package cc.nkbr.lanzouplus

import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * [DFWX] DFW-7 更新检查"可达性"回归。
 *
 * 这张卡的核心不是"更新逻辑对不对"（`UpdateClientJvmTest` 已覆盖），而是
 * **整条更新链零调用点**——`maybeCheckForUpdates()` 定义在 `MainActivity.java` 却没有任何人调用它，
 * 所以用户永远收不到新版本提示。这类缺陷写"逻辑测试"是抓不到的：方法本身实现正确，只是没人调。
 *
 * 因此这里断言的是**接线**（wiring）而不是算法：
 * 1. 设置页必须有"检查更新"可见入口，并且点了能真的走到检查（可观察后果 = 开始检查的提示）；
 * 2. 自动检查必须带时间节流，不能在每次冷启动都打网络；
 * 3. 自动检查的时间戳必须落盘（旧实现只用进程内 AtomicBoolean，当天反复冷启会反复请求）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class UpdateTriggerJvmTest {

    companion object {
        @BeforeClass @JvmStatic fun silenceAutoImport() { MainActivity.LIBRARY_AUTO_IMPORT = false }
    }

    private fun textsIn(view: View, out: MutableList<String> = mutableListOf()): List<String> {
        if (view is TextView) view.text?.toString()?.let { if (it.isNotEmpty()) out.add(it) }
        if (view is ViewGroup) for (i in 0 until view.childCount) textsIn(view.getChildAt(i), out)
        return out
    }

    @Test
    fun settingsPage_hasManualCheckUpdateEntry() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        shadowOf(Looper.getMainLooper()).idle()

        activity.showSettings()
        shadowOf(Looper.getMainLooper()).idle()

        val labels = textsIn(activity.root)
        assertTrue(
            "设置页必须有可见的「检查更新」入口，否则用户没有手动出路（DFW-7 的核心缺陷）：$labels",
            labels.any { it.startsWith("检查更新") },
        )
        assertTrue(
            "入口上要带上当前版本号，用户才知道自己在哪个版本：$labels",
            labels.any { it.startsWith("检查更新") && it.contains(BuildConfig.VERSION_NAME) },
        )
    }

    @Test
    fun manualCheck_reachesTheUpdateChain() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        shadowOf(Looper.getMainLooper()).idle()

        activity.manualCheckForUpdates()
        shadowOf(Looper.getMainLooper()).idle()

        // 手动检查会同步把 running 置位（真正的网络在 io 线程），这是"接线通了"的可观察证据。
        // 死代码状态下这个方法根本没人调，running 永远是 false。
        assertTrue(
            "手动检查必须真的启动检查流程（否则就是又一处死代码）",
            activity.updateCheckRunning.get(),
        )
    }

    @Test
    fun autoCheck_isThrottled_notEveryColdStart() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        shadowOf(Looper.getMainLooper()).idle()

        // 首次：无任何记录 → 允许自动检查
        assertEquals("首次冷启动应放行自动检查", 0L, activity.lastAutoUpdateCheckMs())

        // 模拟"刚刚查过" → 24h 内必须被节流拦住，不得再打网络
        activity.rememberAutoUpdateCheck(System.currentTimeMillis())
        assertTrue(
            "刚查过之后 24h 内不得再自动查询（避免每次冷启动都打网络）",
            System.currentTimeMillis() - activity.lastAutoUpdateCheckMs() < MainActivity.AUTO_UPDATE_CHECK_INTERVAL_MS,
        )

        // 时间戳必须落盘：换一个 Activity 实例仍能读到（旧实现只在进程内存里）
        val reopened = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(
            "节流时间戳必须持久化，否则同一天反复冷启会反复请求",
            reopened.lastAutoUpdateCheckMs() > 0L,
        )
    }

    /**
     * 自动检查节流必须真的生效：模拟"刚查过"后调用自动入口，不得再次进入检查。
     * 这条直接打 `maybeCheckForUpdates()`，是节流逻辑的真值表。
     */
    @Test
    fun autoCheck_skipsWhenWithinThrottleWindow() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        shadowOf(Looper.getMainLooper()).idle()

        activity.rememberAutoUpdateCheck(System.currentTimeMillis())
        activity.maybeCheckForUpdates()
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(
            "24h 内自动入口必须被节流拦住（updateCheckRunning 不得被置位）",
            !activity.updateCheckRunning.get(),
        )
    }
}
