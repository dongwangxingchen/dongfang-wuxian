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
import java.io.File

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

        private val repoRoot: File = run {
            var dir = File(System.getProperty("user.dir") ?: ".").absoluteFile
            while (!File(dir, "app/src/main/AndroidManifest.xml").isFile && dir.parentFile != null) dir = dir.parentFile!!
            dir
        }
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
            reachable, reachable,
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

    // ── [DFW-76] 更新后**不再自动弹** ────────────────────────────────────

    /**
     * 用户 2026-10-01 原话：
     * > "我发现刚刚我的软件弹出来了一个弹窗，说我的软件已经更新到了 1.0.0……
     * >  要不就不弹弹窗了吧。你去设置界面，把它放到参考与致谢的下面。"
     * > "反正你去掉更新后加载了什么吧。就是更新后更新了啥，那个弹窗去掉吧。"
     *
     * 所以：更新完成后**什么都不弹**；要看更新记录，自己从设置页点进去。
     * 顺带解释用户"好像是点了公告才弹"的疑惑：旧实现是启动后延迟 900ms 弹的，
     * 在这 900ms 内做任何操作（包括点公告）都会看起来像是那次操作触发的。现在没有这个延迟弹窗了。
     */
    @Test
    fun afterAnUpdate_nothingPopsUpOnItsOwn() {
        val a = activity()
        a.getPreferences(0).edit().putLong("last_update_stamp", -1L).apply()
        a.showSettings()
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(3000))
        /*
         * [DFW-97] 断言收窄到「不许弹**更新记录**」，而不是「不许有任何弹窗」。
         *
         * 为什么改：用户 2026-10-02 要求「一开软件就要存储权限和通知权限」，
         * 首启会弹一个**权限说明**弹窗（那是他要的）。原来那句
         * `assertNull(getLatestAlertDialog())` 会把权限弹窗也算成违规，属于误伤 ——
         * 它真正要守的是"更新记录不许自己弹出来"，不是"启动后一个弹窗都不许有"。
         */
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        val title = if (dialog == null) "" else shadowOf(dialog).title?.toString().orEmpty()
        assertFalse(
            "更新后不许再自动弹更新记录（实际弹出的标题：$title）",
            title.contains("更新记录"),
        )
    }

    @Test
    fun theAutoPopupIsGoneFromTheSource_forGood() {
        val src = File(repoRoot, "app/src/main/java/cc/nkbr/lanzouplus/MainActivity.java").readText()
        assertFalse("自动弹更新记录的那套判定必须已经删干净", src.contains("maybeShowChangelogAfterUpdate"))
        assertFalse(src.contains("shouldShowChangelogAfterUpdate"))
        assertFalse("连同它的安装戳记录一起删掉", src.contains("last_update_stamp"))
    }

    // ── [DFW-76] 入口挪到页脚：参考与致谢的下面 ──────────────────────────

    @Test
    fun settingsFooter_putsChangelogRightBelowAcknowledgements() {
        val a = activity()
        a.showSettings()
        shadowOf(Looper.getMainLooper()).idle()
        val labels = textsIn(a.root).filter {
            it == "崩溃日志" || it == "参考与致谢" || it == "更新记录" ||
                it.startsWith("检查更新") || it.startsWith("关于")
        }
        assertEquals(
            "页脚顺序必须是 崩溃日志 → 参考与致谢 → 更新记录 → 检查更新 → 关于（用户指定更新记录放参考与致谢下面）",
            listOf("崩溃日志", "参考与致谢", "更新记录"),
            labels.take(3),
        )
        assertTrue("检查更新必须还在", labels.any { it.startsWith("检查更新") })
        assertTrue("关于必须还在", labels.any { it.startsWith("关于") })
    }

    @Test
    fun changelogIsNoLongerInTheDataAndAboutSection() {
        // 同一页里只该出现一次「更新记录」，而且是在页脚那段。
        val a = activity()
        a.showSettings()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("「更新记录」整页只许有一个入口", 1, textsIn(a.root).count { it == "更新记录" })
    }

    // ── [DFW-76] 两个不合适的图标 ─────────────────────────────────────────

    @Test
    fun acknowledgementsRow_doesNotUseAChevron() {
        // 用户原话："顺便把参考与致谢那个按钮给改一改，就是那个按钮左边的图标。
        //           那个图标我感觉并不适合参考与致谢。"
        // 旧图标 ic_expand 是个"向下箭头"，语义是"可展开"，跟致谢毫无关系。
        val src = File(repoRoot, "app/src/main/java/cc/nkbr/lanzouplus/MainActivity.java").readText()
        assertFalse(
            "参考与致谢不许再用展开箭头当图标",
            src.contains("settingsAction(R.drawable.ic_expand,\"参考与致谢\""),
        )
        assertTrue(
            "参考与致谢应该用「致谢」语义的图标（心形）",
            src.contains("settingsAction(R.drawable.ic_tool_heart,\"参考与致谢\""),
        )
    }

    @Test
    fun aboutRow_doesNotUseTheCopyIcon() {
        // 用户原话："还有关于东方无限也不太适合那个按钮。" —— 旧图标 ic_copy 是"复制"。
        val src = File(repoRoot, "app/src/main/java/cc/nkbr/lanzouplus/MainActivity.java").readText()
        assertFalse("关于不许再用「复制」图标", src.contains("settingsAction(R.drawable.ic_copy,\"关于\""))
        assertTrue(
            "关于应该用信息图标",
            src.contains("settingsAction(R.drawable.ic_tool_info,\"关于\""),
        )
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
            /*
             * [DFW-97] 条目现在**自动编号**（用户要求「用数字列出来」），
             * 所以渲染出来的是「1、原文」而不是原文本身 —— 断言要跟着放宽，
             * 但仍然要求"原文一字不少"（编号只是前缀，不许把内容改掉）。
             */
            assertTrue(
                "必须列出改动条目「${item.highlights}」（现在带自动编号前缀）",
                texts.any { it.contains(item.highlights) },
            )
        }
        // 按后台排序（新→旧）原样展示
        val order = history.map { texts.indexOf("v${it.versionName}") }
        assertEquals("必须按新→旧排列（后台 sort=-id）", order.sorted(), order)
        assertTrue("顺序不能是「都没找到」的假绿", order.all { it >= 0 })
    }

    /**
     * [DFW-97] **条目必须自动编号。** 用户 2026-10-02：
     * 「每次更新都应该让普通人能看懂，还用数字列出来」。
     *
     * 在客户端编而不是让后台写「1. 2. 3.」：后台以后加条目的人不用记着编号，
     * 少一个会出错的手工步骤。空行自动跳过。
     */
    @Test
    fun entriesAreNumberedAutomatically() {
        val a = activity()
        val multi = entry("1.0.9", "2026-10-09", "第一件事\n\n第二件事\n第三件事")
        a.showChangelogDialog("更新记录", "", listOf(multi))
        shadowOf(Looper.getMainLooper()).idle()
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        val texts = textsIn(shadowOf(dialog).view!!)
        val body = texts.firstOrNull { it.contains("第一件事") }
        assertNotNull("改动条目必须渲染出来", body)
        assertTrue("必须带编号 1", body!!.contains("1、第一件事"))
        assertTrue("必须带编号 2", body.contains("2、第二件事"))
        assertTrue("必须带编号 3（空行要跳过，不能跳号）", body.contains("3、第三件事"))
        assertFalse("空行不许占一个号", body.contains("4、"))
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
        /*
         * [DFW-97] 后台有数据时**不再追加内置条目** —— 用户实测发现更新记录里出现了
         * 两条 v1.0.0（后台一条、内置一条）。所以内置那条只在后台**完全没有**时才兜底。
         * 这里后台给了 30 条，就应该是 30 条，一条不多一条不少。
         */
        assertEquals("历史一条都不能少（后台 30 条，不追加内置）", 30, textsIn(view!!).count { it.startsWith("v1.0.") })
    }

    /**
     * [DFW-97] **后台已经有 1.0.0 时，不许再出现第二条 v1.0.0。**
     *
     * 用户 2026-10-02 截图：「你看看咋多了，应该只有 1.0.0 啊」——
     * 更新记录里出现了两条 v1.0.0，因为内置那条原来是无条件追加的。
     */
    @Test
    fun theBetaEntryIsNeverDuplicated() {
        val a = activity()
        val backend = listOf(entry("1.0.0", "2026-10-01", "后台自己的公测说明"))
        a.showChangelogDialog("更新记录", "", backend)
        shadowOf(Looper.getMainLooper()).idle()
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        val texts = textsIn(shadowOf(dialog).view!!)
        assertEquals(
            "后台已经有 1.0.0 时，只许出现一条 v1.0.0",
            1,
            texts.count { it == "v1.0.0" },
        )
        assertTrue(
            "必须用后台那条的内容，不许被内置的顶掉",
            texts.any { it.contains("后台自己的公测说明") },
        )
    }

    @Test
    fun emptyHistory_stillShowsThePublicBetaOpening() {
        /*
         * [DFW-97] 行为变了：用户 2026-10-02 要求「更新记录，你现在改成『公测开始』」。
         *
         * 后台没配、或者用户此刻网络不通时，更新记录**不该是一片空白** ——
         * 第一条就应该是"这个软件从哪开始的"。所以现在**内置一条 1.0.0「公测开始」**，
         * 无论后台有没有数据都会显示。
         * 原来那条「暂时读不到更新记录」的占位**保留为兜底**（万一有人把内置条目删了）。
         */
        val a = activity()
        a.showChangelogDialog("更新记录", "", emptyList())
        shadowOf(Looper.getMainLooper()).idle()
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        assertNotNull(dialog)
        val texts = textsIn(shadowOf(dialog).view!!)
        assertTrue(
            "后台没有数据时也必须显示内置的「公测开始」，不能给个空壳",
            texts.any { it.contains("公测开始") },
        )
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
