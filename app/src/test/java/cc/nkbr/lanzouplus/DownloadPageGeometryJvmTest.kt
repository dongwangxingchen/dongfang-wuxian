package cc.nkbr.lanzouplus

import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
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
 * [DFW-113 2026-10-03] 下载页**几何**守卫：chorome 不许把列表挤没。
 *
 * ## 用户真机截图（问题现场）
 * 「下载历史」页上，**「更多」那颗胶囊孤零零挂在右边、把下载记录挤了出去**。
 *
 * ## 根因（实测几何，不是观感）
 * 页面把 **4 行固定高度** + 1 行 `weight=1` 依次塞进同一个垂直 `LinearLayout`：
 * 页头 / 搜索框 / 状态筛选 / 扩展名筛选 / 操作行 / 列表。
 * `LinearLayout` 竖直方向先满足所有固定高度、再按 weight 分剩余，**列表是唯一会被压缩的那个**。
 *
 * 而操作行里「暂停全部/取消全部」只在 `hasWork` 时可见 —— 于是"有历史记录但没在跑的任务"时，
 * 整排只剩一颗「更多」贴在最右（实测 `chip0=GONE chip1=GONE chip2=VISIBLE`），
 * 既占一整行高度、又长得像排版坏了。用户 2026-10-03 截图反馈的就是它。
 *
 * ## 本轮修法（这两条用例守的就是它）
 * ① 「更多」搬进页头做 ⋮（`downloadHeaderMenuButton`）；
 * ② 操作行**只在 hasWork 时出现**，其余时候整行 GONE —— 省下的 `dpText(44)` 还给列表。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class DownloadPageGeometryJvmTest {

    companion object {
        @BeforeClass @JvmStatic fun silenceAutoImport() { MainActivity.LIBRARY_AUTO_IMPORT = false }
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun entry(name: String, state: String): MainActivity.DownloadEntry {
        val e = MainActivity.DownloadEntry()
        e.name = name
        e.state = state
        e.percent = 47
        e.totalBytes = 15_200_000L
        e.downloadedBytes = 6_000_000L
        return e
    }

    /**
     * 打开下载页并**真布局一遍**。
     *
     * 布局方式照 `SearchPageBottomAndBackJvmTest.layoutSearchWithResults`：Robolectric 不会自动跑
     * 真实布局，必须自己递 MeasureSpec 再 `layout()`，否则 `getHeight()` 恒为 0，
     * 断言会**因为错误的原因变绿**（本项目吃过这个亏）。
     */
    private fun layoutDownloads(
        entries: List<Pair<String, String>> = emptyList(),
        fontScale: Float = 1f,
        heightDp: Int = 891,
    ): MainActivity {
        val c = Robolectric.buildActivity(MainActivity::class.java)
        c.setup()
        idle()
        val a = c.get()
        if (fontScale != 1f) {
            val cfg = android.content.res.Configuration(a.resources.configuration)
            cfg.fontScale = fontScale
            a.resources.updateConfiguration(cfg, a.resources.displayMetrics)
        }
        for ((name, state) in entries) a.downloadEntries.add(entry(name, state))
        a.showDownloads()
        idle()
        val d = a.resources.displayMetrics.density
        val w = (411 * d).toInt()
        val h = (heightDp * d).toInt()
        a.root.measure(
            View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY),
        )
        a.root.layout(0, 0, w, h)
        idle()
        return a
    }

    /** 列表区顶层（`draggableList` 的 FrameLayout）——即 `root` 里唯一 weight>0 的子 View。 */
    private fun listRegion(a: MainActivity): View {
        val root = a.root
        for (i in 0 until root.childCount) {
            val child = root.getChildAt(i)
            val lp = child.layoutParams
            if (lp is LinearLayout.LayoutParams && lp.weight > 0f) return child
        }
        throw AssertionError("下载页里找不到 weight>0 的列表区，root 子 View 数=${root.childCount}")
    }

    // ── ① 列表区必须拿到正高度，而且要拿到"大头" ──────────────────────────

    @Test
    fun theListRegionKeepsMostOfTheScreen() {
        // 有在跑的任务 → 操作行会出现（这是 chrome 最多的情况），列表仍必须拿到大头。
        val a = layoutDownloads(listOf("无限画质增强器_v2.3.1.apk" to MainActivity.DOWNLOAD_RUNNING))
        val region = listRegion(a)
        val rootHeight = a.root.height
        assertTrue(
            "列表区高度必须为正，实测 ${region.height}px —— 为 0 就是被固定高度的 chrome 挤没了" +
                "（用户截图那个「更多骑到列表上」的形态）",
            region.height > 0,
        )
        assertTrue(
            "列表区至少该拿到页面一半高度。实测 ${region.height}/$rootHeight = " +
                "${"%.0f".format(region.height * 100f / rootHeight)}%",
            region.height * 2 > rootHeight,
        )
    }

    @Test
    fun theActionRowNeverOverlapsTheListRegion() {
        val a = layoutDownloads(listOf("无限画质增强器_v2.3.1.apk" to MainActivity.DOWNLOAD_RUNNING))
        val row = a.downloadActionRowView ?: throw AssertionError("操作行不存在")
        val region = listRegion(a)
        assertEquals("有在跑的任务时操作行应当可见", View.VISIBLE, row.visibility)
        assertTrue(
            "操作行底边(${row.bottom}) 不许越过列表区顶边(${region.top})。" +
                "越过 = 「暂停全部/取消全部」会盖在下载记录上",
            row.bottom <= region.top,
        )
    }

    /** 字体放大是真机上的触发条件（`dpText` 上限 1.8×），必须单独守。 */
    @Test
    fun theListStillKeepsItsShare_whenTheUserScaledUpTheFont() {
        val a = layoutDownloads(
            listOf("无限画质增强器_v2.3.1.apk" to MainActivity.DOWNLOAD_RUNNING),
            fontScale = 1.8f,
        )
        val region = listRegion(a)
        assertTrue("字体放大 1.8 倍后列表区仍必须为正，实测 ${region.height}px", region.height > 0)
        assertTrue(
            "字体放大 1.8 倍后列表区仍该拿到页面一半高度，实测 ${region.height}/${a.root.height}",
            region.height * 2 > a.root.height,
        )
        val row = a.downloadActionRowView!!
        assertTrue("字体放大后操作行也不许压到列表", row.bottom <= region.top)
    }

    // ── ② 操作行隐藏时，省下的高度必须**真的还给列表** ────────────────────

    /**
     * 这条是"不再挤压列表"的**量化证明**：全部完成（无 hasWork）时操作行 GONE，
     * 列表区必须比"有在跑的任务"时**严格更高**。
     * 只断言 `visibility==GONE` 是不够的 —— GONE 了但高度没还给列表，等于白改。
     */
    @Test
    fun hidingTheActionRowActuallyGivesTheHeightBackToList() {
        val running = layoutDownloads(listOf("任务.zip" to MainActivity.DOWNLOAD_RUNNING))
        val done = layoutDownloads(listOf("任务.zip" to MainActivity.DOWNLOAD_COMPLETED))

        assertEquals("有在跑的任务 → 操作行可见", View.VISIBLE, running.downloadActionRowView.visibility)
        assertEquals("全部完成 → 操作行隐藏", View.GONE, done.downloadActionRowView.visibility)

        val runningRegion = listRegion(running)
        val doneRegion = listRegion(done)
        assertTrue(
            "操作行隐藏后列表区必须更高。实测 无任务=${doneRegion.height}px / 有任务=${runningRegion.height}px",
            doneRegion.height > runningRegion.height,
        )
        val gained = doneRegion.height - runningRegion.height
        assertTrue(
            "回收的高度应当至少是操作行自身高度（实测回收 ${gained}px，" +
                "操作行 lp.height=${running.downloadActionRowView.layoutParams.height}）",
            gained >= running.downloadActionRowView.layoutParams.height,
        )
    }

    // ── ③ 「更多」必须真的在页头，而且能开菜单 ───────────────────────────

    @Test
    fun theOverflowEntryPointLivesInTheHeaderAndIsReachable() {
        val a = layoutDownloads(listOf("任务.zip" to MainActivity.DOWNLOAD_COMPLETED))
        val menu = a.downloadHeaderMenuButton ?: throw AssertionError("页头 ⋮ 不存在")
        assertEquals("有记录时页头 ⋮ 必须可见", View.VISIBLE, menu.visibility)

        // 它必须挂在 pageHeaderRow 里（不是挂在列表上方的操作行里）——这是本轮迁移的实质。
        var p = menu.parent
        var depth = 0
        while (p != null && depth < 6) {
            if (p === a.pageHeaderRow) return
            p = (p as? View)?.parent
            depth++
        }
        throw AssertionError("页头 ⋮ 不在 pageHeaderRow 里，说明它没真的搬进页头")
    }

    // ── ④ 两条筛选条仍须能横滚（芯片数超过屏宽时不许被裁）──────────────

    @Test
    fun bothFilterStripsAreHorizontallyScrollable() {
        val a = layoutDownloads()
        for ((name, strip) in listOf(
            "状态筛选条" to a.downloadFilterStrip,
            "扩展名筛选条" to a.downloadExtensionStrip,
        )) {
            requireNotNull(strip) { "$name 不存在" }
            assertTrue(
                "$name 必须挂在一个 HorizontalScrollView 里 —— 8 个扩展名芯片在 411dp 屏上放不下，" +
                    "不横滚就会被裁掉。实测父类=${strip.parent?.javaClass?.simpleName}",
                strip.parent is android.widget.HorizontalScrollView,
            )
        }
    }

    @Test
    fun theExtensionStripActuallyCarriesEveryChip() {
        val a = layoutDownloads()
        val strip = a.downloadExtensionStrip
        assertEquals(
            "扩展名筛选条应当有 8 个芯片（全部/安装包/应用程序/压缩包/文本/视频/音乐/其他）",
            8,
            strip.childCount,
        )
        val labels = (0 until strip.childCount).map { (strip.getChildAt(it) as TextView).text.toString() }
        assertTrue("必须包含「其他」这一档，实测 $labels", labels.contains("其他"))
    }

    // ── ⑤ 页头不许因为多了一个 ⋮ 就把标题或保存路径挤掉 ─────────────────

    @Test
    fun theHeaderKeepsTitleSavePathAndOverflowTogether() {
        val a = layoutDownloads(listOf("任务.zip" to MainActivity.DOWNLOAD_COMPLETED))
        val header = a.pageHeaderRow ?: throw AssertionError("页头不存在")
        val found = mutableListOf<String>()
        fun walk(v: View) {
            if (v is TextView && v.text != null) found.add(v.text.toString())
            if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
        }
        walk(header)
        assertTrue("页头必须还有标题「下载历史」，实测 $found", found.any { it.contains("下载历史") })
        assertTrue("页头必须还有「保存路径」，实测 $found", found.any { it.contains("保存路径") })
        assertTrue(
            "页头里的三个元素（标题/保存路径/⋮）都必须有非零宽度，实测 " +
                "header=${header.width}px 文本=$found",
            header.width > 0 && a.downloadHeaderMenuButton.width > 0,
        )
    }

    // ── ⑥ [DFW-113 2026-10-03] 卡片质感：不许退回"裸行" ────────────────

    private fun firstRow(a: MainActivity): View {
        assertTrue("列表里应当至少有一条卡片", a.downloadList.childCount > 0)
        return a.downloadList.getChildAt(0)
    }

    /**
     * 卡片必须有**自己的面**。
     *
     * 改前这一行只有 padding、`background == null` —— N 条记录糊成一片，条目之间没有边界。
     * 这是用户说"下载界面还是旧的/没优化好"时最直接的观感来源之一。
     */
    @Test
    fun eachDownloadRowHasItsOwnCardSurface() {
        val a = layoutDownloads(listOf("任务.zip" to MainActivity.DOWNLOAD_COMPLETED))
        val row = firstRow(a)
        assertTrue(
            "下载卡片必须有自己的背景（SURFACE + 描边），不能是裸行（background==null）。" +
                "实测 background=${row.background}",
            row.background != null,
        )
    }

    /**
     * 卡片之间必须有间距 —— 否则"一条记录 = 一个对象"读不出来。
     * 间距落在 `LayoutParams` 上（不是 padding），所以单独断言。
     */
    @Test
    fun downloadCardsLeaveAGapBetweenThem() {
        val a = layoutDownloads(
            listOf("甲.zip" to MainActivity.DOWNLOAD_COMPLETED, "乙.zip" to MainActivity.DOWNLOAD_COMPLETED),
        )
        assertEquals("应当有两条卡片", 2, a.downloadList.childCount)
        val lp = a.downloadList.getChildAt(0).layoutParams as LinearLayout.LayoutParams
        assertTrue(
            "卡片之间必须有下间距，实测 bottomMargin=${lp.bottomMargin}px",
            lp.bottomMargin > 0,
        )
        assertTrue("卡片左右也要留边，实测 leftMargin=${lp.leftMargin}px", lp.leftMargin > 0)
    }

    /**
     * **内容不许溢出容器**（这条抓的是一个真实存在的溢出）。
     *
     * 改前：`middle` 容器高 dp(70)，里面三件是 dp(24)+dp(44)+dp(5) = **dp(73)** —— 超 3dp，
     * 进度条下沿被裁。这类"写死的子项高度加起来超过父容器"的错，布局不会报错、只会静默裁掉。
     */
    @Test
    fun theCardContentActuallyFitsInsideItsBox() {
        val a = layoutDownloads(listOf("任务.zip" to MainActivity.DOWNLOAD_RUNNING))
        val row = firstRow(a)
        val cardHeight = row.layoutParams.height
        // 找出卡片里的竖直中列（weight=1 的那个），校验它的子项总高 ≤ 它自己的高。
        val middleView = (0 until (row as ViewGroup).childCount)
            .map { row.getChildAt(it) }
            .firstOrNull { (it.layoutParams as? LinearLayout.LayoutParams)?.weight ?: 0f > 0f }
            ?: throw AssertionError("卡片里找不到 weight=1 的中列")
        val middle = middleView as ViewGroup
        val middleHeight = (middle.layoutParams as LinearLayout.LayoutParams).height
        val childrenSum = (0 until middle.childCount).sumOf { middle.getChildAt(it).layoutParams.height }
        assertTrue(
            "中列内容总高 ($childrenSum) 不许超过中列容器高 ($middleHeight) —— " +
                "超过了就会静默裁掉最下面那个（进度条）",
            childrenSum <= middleHeight,
        )
        assertTrue(
            "中列容器高 ($middleHeight) 也不许超过卡片高 ($cardHeight)",
            middleHeight <= cardHeight,
        )
    }

    /**
     * 图标底不许用 `BG`。
     *
     * 改前是 `solidShape(BG,9)` —— BG 画在 BG 页面上等于**隐形**，
     * 42dp 的图标位看起来是空的，用户会觉得"卡片缺了一块"。
     */
    @Test
    fun theFileIconSitsOnAVisiblePlateNotOnThePageBackground() {
        val a = layoutDownloads(listOf("任务.zip" to MainActivity.DOWNLOAD_COMPLETED))
        val row = firstRow(a) as ViewGroup
        val icon = (0 until row.childCount).map { row.getChildAt(it) }.filterIsInstance<android.widget.ImageView>().firstOrNull()
            ?: throw AssertionError("卡片里找不到图标")
        assertTrue("图标必须有底色块（用来区分文件类型）", icon.background != null)
        assertTrue(
            "图标底不该用页面底色 BG —— 同色等于隐形，42dp 图标位看起来是空的。实测 BG=${a.BG} SURFACE2=${a.SURFACE2}",
            a.SURFACE2 != a.BG,
        )
        assertTrue("图标尺寸应当非零", icon.layoutParams.width > 0 && icon.layoutParams.height > 0)
    }
}
