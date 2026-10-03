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
     *
     * [2026-10-03] 加了 `widthDp` 参数。**改前宽度被硬编码成 411dp**，于是
     * 「360dp 窄屏」这个最容易出问题的场景**零覆盖** —— 而用户真机就有 360dp 的可能
     * （1260px ÷ 3.5 = 360）。窄屏溢出、页头挤掉保存路径这类问题全都测不出来。
     */
    private fun layoutDownloads(
        entries: List<Pair<String, String>> = emptyList(),
        fontScale: Float = 1f,
        heightDp: Int = 891,
        widthDp: Int = 411,
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
        val w = (widthDp * d).toInt()
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

        /* [2026-10-03] 改的是**断言方式，不是断言强度**。
           改前这条断言页头里有一个文本为「保存路径」的标签；本轮把那个**文字标签**去掉了
           （文件夹图标已经表达含义，contentDescription 也念得出来），换来的宽度全给了路径字符串本身。
           所以这里改成按 **contentDescription** 认这个入口 —— 它仍然是"保存路径"那个可点区域，
           强度不变：入口没了照样红。 */
        val pathEntry = findPathEntry(header)
        assertTrue("页头必须还有「保存路径」这个可点入口（按 contentDescription 认）", pathEntry != null)

        assertTrue(
            "页头里的三个元素（标题/保存路径/⋮）都必须有非零宽度，实测 " +
                "header=${header.width}px 文本=$found",
            header.width > 0 && a.downloadHeaderMenuButton.width > 0 && (pathEntry?.width ?: 0) > 0,
        )
    }

    /** 页头里那个「保存路径」可点区域：按 contentDescription 前缀认，不依赖它内部有几个 TextView。 */
    private fun findPathEntry(root: View): View? {
        if (root.contentDescription?.toString()?.startsWith("保存路径") == true) return root
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) findPathEntry(root.getChildAt(i))?.let { return it }
        }
        return null
    }

    /**
     * **[2026-10-03 新增] 360dp 窄屏下，保存路径必须真的看得见。**
     *
     * 这是本轮修掉的一个真缺陷（体检报告 A5）：页头里标题和路径**都是 weight=1**，各分 144dp，
     * 而路径内部固定件占 105dp → 留给路径字符串只剩 **39dp ≈ 4 个 9sp 字**。
     * 用户真机截图里的「…ad/东方无限」是几何必然，不是偶发。
     *
     * 修法：标题改 wrap_content（它只要约 92dp）+ 去掉冗余的「保存路径」文字标签。
     * 这条用例守的就是"别再退回去"——**在 360dp 上**守，因为 411dp 上本来就不那么紧。
     *
     * 阈值取 **150dp**，这是**量出来的**不是拍的（2026-10-03 实测）：
     *   · 标题改回 weight=1（等于撤回本修复）→ 路径 **101.7dp**
     *   · 本修复生效                        → 路径 **233.9dp**
     *
     * ⚠️ 第一版阈值我写的是 100dp，**结果反向探针没变红** —— 因为另外两处改动（去掉「保存路径」
     * 文字标签、图标 25→22dp）本身已经腾出宽度，撤回 weight 修复后仍有 101.7dp，正好压在阈值之上。
     * 也就是说**那条测试当时是空的**。150dp 落在 101.7 与 233.9 之间，两边各留约 50dp，
     * 已用探针确认会变红。教训：阈值必须落在"修好"和"没修"两个实测值**之间**，
     * 凭感觉取一个"看起来够低"的值，测试就是装饰。
     */
    @Test
    fun on360dpTheSavePathStillHasRoomToBeRead() {
        val a = layoutDownloads(listOf("任务.zip" to MainActivity.DOWNLOAD_COMPLETED), widthDp = 360)
        val pathEntry = findPathEntry(a.pageHeaderRow ?: throw AssertionError("页头不存在"))
            ?: throw AssertionError("页头里找不到保存路径入口")
        val pathText = a.downloadPathText ?: throw AssertionError("downloadPathText 不存在")
        val widthDp = pathText.width / a.resources.displayMetrics.density
        assertTrue(
            "360dp 下保存路径至少要能显示 150dp 的字符。原始代码只剩约 39dp ≈ 4 个字" +
                "（真机截图里就是「…ad/东方无限」）；只去掉标签但不改权重仍有 101.7dp，依然偏窄。" +
                "实测 ${pathText.width}px = ${"%.1f".format(widthDp)}dp",
            widthDp >= 150f,
        )
        assertTrue("保存路径入口本身也要有宽度", pathEntry.width > 0)
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
     * **[2026-10-03 补：上面那条测试是假绿，这里补真的]**
     *
     * 上面那条比的是**声明的 dp 之和**（`layoutParams.height` 相加），
     * 与**渲染后的文字高度**完全无关。所以 fontScale=1.8 时文字被裁，它**照样是绿的** ——
     * 它给了"字体放大已经守住了"的错误信心。这是体检报告点名的第 2 号假绿。
     *
     * 这里守两件真的事：
     *  ① **容器高必须随 fontScale 长大**（DFW-13 规矩，见 `MainActivity.dpText` 的注释：
     *     "为了装文字而定的高度都改用 dpText()，否则系统字体调大后文字变大、容器不变 → 被裁切"）；
     *  ② **渲染出来的文字真的装得下**：用该 TextView 在当前 fontScale 下的
     *     `Paint.FontMetrics` 算出单行文字高，断言盒子至少有那么高。
     *
     * 改前卡片用的是 `dp(22)/dp(32)/dp(60)/dp(76)`，**同一页**的操作行却用了 `dpText(44)` ——
     * 一页两套规矩。这条用例就是钉死卡片这一半。
     */
    @Test
    fun cardTextBoxesGrowWithTheSystemFontScale_soTextIsNotClipped() {
        val normal = layoutDownloads(listOf("任务.zip" to MainActivity.DOWNLOAD_RUNNING), fontScale = 1f)
        val big = layoutDownloads(listOf("任务.zip" to MainActivity.DOWNLOAD_RUNNING), fontScale = 1.8f)

        fun middleBox(a: MainActivity): View = (firstRow(a) as ViewGroup).let { row ->
            (0 until row.childCount).map { row.getChildAt(it) }
                .first { (it.layoutParams as? LinearLayout.LayoutParams)?.weight ?: 0f > 0f }
        }

        val normalH = (middleBox(normal).layoutParams as LinearLayout.LayoutParams).height
        val bigH = (middleBox(big).layoutParams as LinearLayout.LayoutParams).height
        assertTrue(
            "字体放大 1.8 倍时，卡片文字容器的高度必须跟着长（改前用 dp() 固定，纹丝不动）。" +
                "实测 fontScale 1.0 → ${normalH}px，1.8 → ${bigH}px",
            bigH > normalH * 1.5f,
        )

        /* 渲染层再核一遍：文字真的装得进盒子。 */
        val card = firstRow(big)
        val name = findTextView(card) { it.text?.toString() == "任务.zip" }
            ?: throw AssertionError("卡片里找不到文件名 TextView")
        val fm = name.paint.fontMetrics
        val oneLine = fm.bottom - fm.top
        assertTrue(
            "文件名盒子高 ${name.layoutParams.height}px 装不下它自己一行文字（${"%.1f".format(oneLine)}px）—— " +
                "这就是「字体调大后文字被裁」的形态",
            name.layoutParams.height >= oneLine,
        )
    }

    private fun findTextView(root: View, match: (TextView) -> Boolean): TextView? {
        if (root is TextView && match(root)) return root
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) findTextView(root.getChildAt(i), match)?.let { return it }
        }
        return null
    }

    /**
     * **[2026-10-03 新增] 行内动作按钮的触控区必须 ≥48dp。**
     *
     * 改前实测 `dp(38)×dp(42)`（已完成态）、`dp(42)×dp(44)` / `dp(38)×dp(44)`（批量卡片）——
     * 全都不达 token §4:128 的 48dp，而且**同角色的两个按钮宽度还不一致（42 vs 38）**。
     *
     * 为什么以前没人发现：`iconButton` 里写了 `setMinimumWidth/Height(dp(44))`，
     * 看着像已经守住了。但那**在固定 LayoutParams 下不起作用** —— 固定尺寸走 `MeasureSpec.EXACTLY`，
     * `View.getDefaultSize` 在 EXACTLY 分支直接取 specSize、忽略 minimum。
     * 所以这条用例断言的是**渲染后的实际宽高**，不是 layoutParams 声明值。
     */
    @Test
    fun everyDownloadRowActionMeetsThe48dpTouchTarget() {
        for (state in listOf(MainActivity.DOWNLOAD_COMPLETED, MainActivity.DOWNLOAD_RUNNING)) {
            val a = layoutDownloads(listOf("任务.zip" to state))
            val buttons = mutableListOf<View>()
            fun walk(v: View) {
                if (v is android.widget.ImageButton) buttons.add(v)
                if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
            }
            walk(firstRow(a))
            assertTrue("状态 $state 的行里应当有动作按钮", buttons.isNotEmpty())
            val d = a.resources.displayMetrics.density
            for (b in buttons) {
                val w = b.width / d
                val h = b.height / d
                assertTrue(
                    "触控区必须 ≥48dp（token §4:128）。状态 $state 的「${b.contentDescription}」" +
                        "实测 ${"%.1f".format(w)}×${"%.1f".format(h)}dp",
                    w >= 47.5f && h >= 47.5f,
                )
            }
        }
    }

    /**
     * **[2026-10-03 新增] 空态不许退回"一行灰字白板"。**
     *
     * 改前是 `text("暂无下载记录",14,MUTED)` 一行灰字居中 —— 一整屏纯黑中央一行灰字，
     * 没有图标、没有下一步引导。空态在"还没用起来"时占满整屏，是「质感」最容易被感知的地方。
     * 现在要求：**至少一个图标 + 至少两行文字（标题 + 下一步）**。
     */
    @Test
    fun theEmptyStateHasAnIconAndGuidanceNotJustOneLineOfText() {
        val a = layoutDownloads(emptyList())
        assertTrue("空列表里应当渲染出空态", a.downloadList.childCount > 0)
        val empty = a.downloadList.getChildAt(0)
        var images = 0
        var texts = 0
        fun walk(v: View) {
            if (v is android.widget.ImageView) images += 1
            if (v is TextView) texts += 1
            if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
        }
        walk(empty)
        assertTrue("空态必须有图标（改前是一行灰字白板），实测 images=$images", images >= 1)
        assertTrue("空态必须有标题 + 下一步提示两行文字，实测 texts=$texts", texts >= 2)
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
