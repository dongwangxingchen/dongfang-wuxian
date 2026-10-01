package cc.nkbr.lanzouplus

import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog

/**
 * [DFW-62] 更新记录：更新后弹一次 + 设置页常驻入口。
 *
 * 用户 2026-09-30 原话：
 * > "每次更新完后会弹出来一个弹窗，可以看到每次的更新记录，往下滑一滑，
 * >  就能看到每个日期、每个改的地方"
 *
 * ## 为什么这几条要钉死
 * 这类"弹一次"的功能有两个典型翻车方式，而且都不会报错：
 *  1. **每次冷启都弹**（判定写成了"有没有更新记录"而不是"版本有没有变"）——用户会被烦死；
 *  2. **全新安装也弹**（没有历史记录时把 last=0 当成"更新过"）——第一次打开就糊一屏更新记录。
 * 所以判定被抽成纯函数 [MainActivity.shouldShowChangelogAfterUpdate]，这里逐种输入钉死。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class ChangelogJvmTest {

    companion object {
        @BeforeClass @JvmStatic fun silenceAutoImport() { MainActivity.LIBRARY_AUTO_IMPORT = false }
    }

    private fun activity(): MainActivity {
        val a = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        shadowOf(Looper.getMainLooper()).idle()
        return a
    }

    private fun entry(version: String, date: String, highlights: String) =
        RemoteConfigClient.Changelog("id-$version", version, date, highlights)

    private fun snapshot(reachable: Boolean, entries: List<RemoteConfigClient.Changelog>) =
        RemoteConfigClient.Snapshot(
            reachable,
            RemoteConfigClient.Control.normal(),
            null,
            emptyList(),
            entries,
        )

    private val history = listOf(
        entry("1.0.3", "2026-10-03", "第三个版本的改动"),
        entry("1.0.2", "2026-10-02", "第二个版本的改动"),
        entry("1.0.1", "2026-10-01", "第一个版本的改动"),
    )

    private fun textsIn(view: View, out: MutableList<String> = mutableListOf()): List<String> {
        if (view is TextView) view.text?.toString()?.let { if (it.isNotEmpty()) out.add(it) }
        if (view is ViewGroup) for (i in 0 until view.childCount) textsIn(view.getChildAt(i), out)
        return out
    }

    // ── 该不该弹：纯判定 ──────────────────────────────────────────────────

    @Test
    fun upgradedWithHistory_shows() {
        assertTrue(
            "装过新版本 + 后台有更新记录 → 必须弹",
            MainActivity.shouldShowChangelogAfterUpdate(10000L, 10001L, snapshot(true, history)),
        )
    }

    @Test
    fun freshInstall_neverShows() {
        assertFalse(
            "全新安装没有「更新」这回事：第一次打开就弹一屏更新记录是骚扰",
            MainActivity.shouldShowChangelogAfterUpdate(0L, 10000L, snapshot(true, history)),
        )
    }

    @Test
    fun sameVersion_neverShows() {
        assertFalse(
            "版本没变就是普通冷启，不许弹（用户要的是「更新后弹一次」，不是每次都弹）",
            MainActivity.shouldShowChangelogAfterUpdate(10000L, 10000L, snapshot(true, history)),
        )
    }

    @Test
    fun unreachableBackend_neverShows() {
        assertFalse(
            "后台拉不到就不弹：宁可这次不弹，也不拿本地残留当历史（会显示过期内容）",
            MainActivity.shouldShowChangelogAfterUpdate(10000L, 10001L, snapshot(false, history)),
        )
        assertFalse(MainActivity.shouldShowChangelogAfterUpdate(10000L, 10001L, null))
    }

    @Test
    fun emptyHistory_neverShows() {
        assertFalse(
            "后台没有更新记录 → 没东西可展示，不弹空弹窗",
            MainActivity.shouldShowChangelogAfterUpdate(10000L, 10001L, snapshot(true, emptyList())),
        )
    }

    // ── 真链路：装过新版本时确实会弹，且只弹一次 ────────────────────────────

    @Test
    fun upgradedLaunch_actuallyShowsDialog_andRecordsVersionSoItPopsOnlyOnce() {
        val a = activity()
        val current = MainActivity.updateStamp(a.packageLastUpdateTime(), BuildConfig.VERSION_CODE)
        // 造"上次启动记的是另一个包"（覆盖安装前的那一次）
        a.getPreferences(0).edit().putLong("last_update_stamp", current - 1).apply()

        a.maybeShowChangelogAfterUpdate(snapshot(true, history))
        shadowOf(Looper.getMainLooper()).idle()
        // 弹窗是延迟 900ms 弹的（不与首屏渲染抢主线程），把时间推过去
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(1200))

        assertEquals(
            "判定完必须立刻写回当前安装戳，否则同一次安装会反复弹",
            current,
            a.getPreferences(0).getLong("last_update_stamp", 0L),
        )
        assertNotNull("装过新包时应该真的弹出更新记录", ShadowAlertDialog.getLatestAlertDialog())

        // 第二次（同一次安装的普通冷启）不该再弹
        ShadowAlertDialog.getLatestAlertDialog()?.dismiss()
        shadowOf(Looper.getMainLooper()).idle()
        ShadowAlertDialog.reset()
        a.maybeShowChangelogAfterUpdate(snapshot(true, history))
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(1200))
        assertEquals(
            "同一次安装第二次启动不许再弹",
            current,
            a.getPreferences(0).getLong("last_update_stamp", 0L),
        )
        assertNull("第二次启动不该再弹更新记录", ShadowAlertDialog.getLatestAlertDialog())
    }

    // ── 弹窗内容：全部历史、按新→旧、可滚动 ────────────────────────────────

    @Test
    fun dialogListsEveryEntryNewestFirst() {
        val a = activity()
        a.showChangelogDialog("更新记录", "往下滑可以看全部历史更新记录", history)
        shadowOf(Looper.getMainLooper()).idle()

        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        assertNotNull("应该真的弹出更新记录弹窗", dialog)
        val view = shadowOf(dialog).view
        assertNotNull("弹窗必须有自定义内容视图", view)
        val texts = textsIn(view!!)

        for (item in history) {
            assertTrue("必须列出 v${item.versionName}", texts.any { it == "v${item.versionName}" })
            assertTrue("必须列出日期 ${item.date}", texts.contains(item.date))
            assertTrue("必须列出改动条目「${item.highlights}」", texts.contains(item.highlights))
        }
        // 按后台排序（新→旧）原样展示
        val order = history.map { texts.indexOf("v${it.versionName}") }
        assertEquals("必须按新→旧排列（后台 sort=-id）", order.sorted(), order)
        assertTrue("顺序不能是「都没找到」的假绿", order.all { it >= 0 })
    }

    @Test
    fun dialogBodyScrollsInsteadOfBlowingUpTheScreen() {
        // 后台的更新记录会一直累积；不给上限，弹窗会被撑到屏幕外，下面的历史根本划不到。
        val a = activity()
        val many = (1..30).map { entry("1.0.$it", "2026-10-$it", "第 $it 条改动") }
        a.showChangelogDialog("更新记录", "", many)
        shadowOf(Looper.getMainLooper()).idle()
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        assertNotNull(dialog)
        val view = shadowOf(dialog).view
        assertNotNull(view)
        assertTrue("内容必须包在可滚动容器里（不然长历史看不到）", view is android.widget.ScrollView)
        assertEquals("30 条历史一条都不能少", 30, textsIn(view!!).count { it.startsWith("v1.0.") })
    }

    @Test
    fun emptyHistory_showsAPlaceholderInsteadOfAnEmptyDialog() {
        val a = activity()
        a.showChangelogDialog("更新记录", "", emptyList())
        shadowOf(Looper.getMainLooper()).idle()
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        assertNotNull(dialog)
        val texts = textsIn(shadowOf(dialog).view!!)
        assertTrue("没记录时要说清楚，不能给个空壳", texts.any { it.contains("暂时读不到更新记录") })
    }

    // ── 常驻入口 ──────────────────────────────────────────────────────────

    @Test
    fun settingsPage_hasAStandingChangelogEntry() {
        val a = activity()
        a.showSettings()
        shadowOf(Looper.getMainLooper()).idle()
        var found: View? = null
        fun walk(view: View) {
            if (view.contentDescription?.toString() == "更新记录") { found = view; return }
            if (view is ViewGroup) for (i in 0 until view.childCount) walk(view.getChildAt(i))
        }
        walk(a.root)
        assertNotNull("设置页必须有常驻「更新记录」入口（不必等更新，随时可看）", found)
        // 顺手确认它和「公告」是两件不同的事，别被合并成一条
        var notice: View? = null
        fun walkNotice(view: View) {
            if (view.contentDescription?.toString() == "公告") { notice = view; return }
            if (view is ViewGroup) for (i in 0 until view.childCount) walkNotice(view.getChildAt(i))
        }
        walkNotice(a.root)
        assertNotNull("公告入口必须还在", notice)
    }

    // ── 更新弹窗里的"更新内容"仍然按版本号匹配 ─────────────────────────────

    @Test
    fun updateOfferStillPullsHighlightsByVersionName() {
        val a = activity()
        assertEquals(
            "更新弹窗的『更新内容』必须按版本号从后台更新记录里取",
            "第三个版本的改动",
            a.changelogTextFor(snapshot(true, history), "1.0.3"),
        )
        assertEquals("版本号对不上就不硬凑", "", a.changelogTextFor(snapshot(true, history), "9.9.9"))
        assertEquals("没有快照时返回空串，不许 NPE", "", a.changelogTextFor(null, "1.0.3"))
    }
}
