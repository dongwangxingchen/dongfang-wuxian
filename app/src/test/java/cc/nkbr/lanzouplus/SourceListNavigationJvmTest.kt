package cc.nkbr.lanzouplus

import android.os.Looper
import org.junit.Assert.assertEquals
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class SourceListNavigationJvmTest {
    companion object { @BeforeClass @JvmStatic fun s() { MainActivity.LIBRARY_AUTO_IMPORT = false } }

    private fun activity(): MainActivity {
        val a = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        shadowOf(Looper.getMainLooper()).idle()
        return a
    }
    private fun back(a: MainActivity) { a.lastSystemBackAt = -10_000L; a.performSystemBack(); shadowOf(Looper.getMainLooper()).idle() }

    @Test fun fromSettings_backReturnsToSettings() {
        val a = activity()
        a.showSettings(); shadowOf(Looper.getMainLooper()).idle()
        a.showSourcesFromSettings(); shadowOf(Looper.getMainLooper()).idle()
        assertEquals("应已进入源列表", 1, a.pageKind)
        back(a)
        assertEquals("从设置进的资源源管理，返回必须回设置（不是软件库）", 4, a.pageKind)
    }

    @Test fun fromSettings_withRetainedState_backStillReturnsToSettings() {
        val a = activity()
        // 先制造一份"留存态"（模拟用户之前从软件库进过源列表并进了某个源）
        a.showSources(); shadowOf(Looper.getMainLooper()).idle()
        a.retainSourceListPage()
        a.showSettings(); shadowOf(Looper.getMainLooper()).idle()
        a.showSourcesFromSettings(); shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, a.pageKind)
        back(a)
        assertEquals("留存态恢复后返回也必须回设置", 4, a.pageKind)
    }

    @Test fun fromSettings_transitionIsAPush_notAPop() {
        val a = activity()
        a.showSources(); shadowOf(Looper.getMainLooper()).idle()
        a.retainSourceListPage()
        a.showSettings(); shadowOf(Looper.getMainLooper()).idle()
        a.showSourcesFromSettings(); shadowOf(Looper.getMainLooper()).idle()
        assertEquals(
            "从设置点进「资源源管理」是**进入**，必须播推入（+1）；旧实现只有 0/-1 两支，" +
                "从设置进来会播成返回动画 —— 用户看到的就是「加载动画有问题」",
            1,
            a.lastPageTransitionDirection,
        )
    }

    @Test fun fromFolderBack_transitionIsAPop() {
        val a = activity()
        a.showSources(); shadowOf(Looper.getMainLooper()).idle()
        a.retainSourceListPage()
        // 模拟"从源列表进了某个源/文件夹，再退回来"：不带 entry 标志的恢复
        a.sourceListEntry = false
        a.openSourceList(); shadowOf(Looper.getMainLooper()).idle()
        assertEquals("从文件夹退回源列表是**返回**，必须播弹出（-1）", -1, a.lastPageTransitionDirection)
    }

    @Test fun navBallSwitch_transitionIsAFadeThrough() {
        val a = activity()
        a.primaryNavigationSwitch = true
        a.openSourceList(); shadowOf(Looper.getMainLooper()).idle()
        assertEquals("悬浮球切档要的是淡入淡出（0），不能变成推入/弹出", 0, a.lastPageTransitionDirection)
    }
}
