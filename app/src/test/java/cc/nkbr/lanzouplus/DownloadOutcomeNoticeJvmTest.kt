package cc.nkbr.lanzouplus

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * [DFW-102] 下载结果通知：**从顶部滑下、带文件名**。
 *
 * ## 用户要求（2026-10-01 原话）
 * > 「你可以加入下载完毕或下载失败的通知，而且**写明白啥东西下载好了，别只写个下载完成**。
 * > 用你推荐的那个从上往下显示的通知吧。」
 *
 * ## 这条测试守三个具体的坑
 *
 * **① 通知必须带文件名。**
 * 只写「下载完成」等于没说 —— 批量下十个文件时用户根本不知道是哪个好了。
 *
 * **② 通知必须挂在"状态真的变了"这个条件上。**
 * `drainDownloadUi` 会反复处理同一条目（进度每 100ms 一次）。
 * 如果不加 `stateChanged` 判断，下载过程中会每秒弹十次通知。
 *
 * **③ 启动时从历史记录恢复的旧条目不能重播通知（最容易漏的一条）。**
 * 恢复路径原先只同步了 `savedState`，没同步 `uiState`；而 `uiState` 默认是空串，
 * 于是一条「已完成」的旧记录第一次流经 `drainDownloadUi` 时会被判成"刚刚变化"，
 * **每开一次软件就重播一遍几天前的下载完成通知**。
 * 修法是恢复时把 `uiState` 一起同步 —— 这条断言就是防它回退。
 */
class DownloadOutcomeNoticeJvmTest {

    private val root: File = run {
        var dir = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (!File(dir, "AGENTS.md").isFile && dir.parentFile != null) dir = dir.parentFile
        dir
    }

    private fun main() =
        File(root, "app/src/main/java/cc/nkbr/lanzouplus/MainActivity.java").readText(Charsets.UTF_8)

    /** 按花括号配对取方法体。**不靠空行猜边界**（DFW-98 就是这么删错东西的）。 */
    private fun bodyOf(src: String, signature: String): String {
        val start = src.indexOf(signature)
        assertTrue("找不到方法：$signature（测试需要更新）", start >= 0)
        val open = src.indexOf('{', start)
        var depth = 0
        for (i in open until src.length) {
            when (src[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return src.substring(open, i + 1)
                }
            }
        }
        error("$signature 花括号不配对")
    }

    /** 取 `drainDownloadUi` 里处理单个条目的那一段（到 `if(rebuild)` 为止）。 */
    private fun drainPerEntry(src: String): String {
        val body = bodyOf(src, "void drainDownloadUi(){")
        val from = body.indexOf("for(DownloadEntry entry:changed){")
        assertTrue("drainDownloadUi 里找不到逐条处理循环", from >= 0)
        val to = body.indexOf("if(rebuild)renderDownloads", from)
        assertTrue("drainDownloadUi 的循环收尾标记找不到", to > from)
        return body.substring(from, to)
    }

    @Test
    fun noticeCarriesTheFileName() {
        /*
         * [DFW-137 2026-10-03] 这条原来直接断言 `noteDownloadOutcome` 的方法体里有 `entry.name`。
         * 这一轮把取名字抽成了 `downloadEntryName()`（因为完成提示要去抖、汇总也要用同一个名字），
         * 断言就脱靶了 —— 但**要求没变**：用户明确说过「写明白啥东西下载好了，别只写个下载完成」。
         *
         * 所以改成**顺着调用链一路验到底**，比原来只看一个方法体更强：
         * 少了任何一环（取名字 / 汇总用上名字 / 名字拼进文案）都会红。
         */
        val src = main()
        val body = bodyOf(src, "void noteDownloadOutcome(DownloadEntry entry){")
        assertTrue("完成态要单独处理", body.contains("DOWNLOAD_COMPLETED"))
        assertTrue("失败态要单独处理", body.contains("DOWNLOAD_FAILED"))
        assertTrue(
            "失败提示必须走顶部滑下的通知条 showTopBanner",
            body.contains("showTopBanner("),
        )
        assertFalse(
            "不许再退回旧的 showNotice 胶囊（用户明确要「从上往下显示的那个」）",
            body.contains("showNotice("),
        )
        assertTrue(
            "完成提示必须经过 flashDownloadSummary（去抖后的唯一出口）",
            body.contains("flashDownloadSummary("),
        )

        val summary = bodyOf(src, "private void flashDownloadSummary(DownloadEntry entry){")
        assertTrue("完成汇总必须走 showTopBanner", summary.contains("showTopBanner("))
        assertTrue("完成汇总必须带上文件名", summary.contains("downloadEntryName(entry)"))
        assertTrue("文件名必须真的拼进文案里", summary.contains("\"+\"name") || summary.contains("+name"))

        val helper = bodyOf(src, "private static String downloadEntryName(DownloadEntry entry){")
        assertTrue(
            "downloadEntryName 必须真的取 entry.name —— 这是用户「别只写个下载完成」那条要求的落点",
            helper.contains("entry.name"),
        )
    }

    /**
     * [DFW-137 2026-10-03] **下载完成必须去抖：一批文件只弹一条。**
     *
     * 原来每下完一个文件弹一条「已下载 · 文件名」，下三个弹三条；
     * 配上「新提示把旧提示掐掉」的老行为，屏幕上只剩一片闪。
     * 现在等 700ms，期间再来就重置计时，最后只弹一条汇总。
     *
     * ⚠️ **失败提示刻意不去抖** —— 出错要立刻说，晚 700ms 都可能让用户以为软件卡住了。
     */
    @Test
    fun completedDownloadsAreDebouncedSoABatchShowsOneNotice() {
        val body = bodyOf(main(), "void noteDownloadOutcome(DownloadEntry entry){")
        assertTrue(
            "完成提示必须先把上一次的计时取消（去抖的核心，少了它还是逐个弹）",
            body.contains("removeCallbacks(downloadSummaryRunnable)"),
        )
        assertTrue("必须重新计时", body.contains("postDelayed(downloadSummaryRunnable"))
        assertFalse(
            "失败提示不许去抖 —— 出错要立刻说",
            Regex("DOWNLOAD_FAILED\\s*\\)?\\s*\\{[^}]*postDelayed").containsMatchIn(body),
        )
    }

    @Test
    fun noticeOnlyFiresOnARealStateChange() {
        val perEntry = drainPerEntry(main())
        assertTrue(
            "下载结果通知必须挂在 drainDownloadUi 里（那是所有状态变化的唯一汇聚点，分散写必然漏）",
            perEntry.contains("noteDownloadOutcome(entry)"),
        )
        assertTrue(
            "必须受 stateChanged 保护 —— 否则下载过程中每 100ms 弹一次通知",
            Regex("if\\(stateChanged\\)noteDownloadOutcome\\(entry\\)").containsMatchIn(perEntry),
        )
        assertTrue(
            "stateChanged 必须先于通知调用被算出来",
            perEntry.indexOf("stateChanged=") < perEntry.indexOf("noteDownloadOutcome(entry)"),
        )
    }

    /**
     * 反向探针的正面：恢复历史记录时必须同步 `uiState`。
     * 少了它，每开一次软件就把几天前的"下载完成"重播一遍。
     */
    @Test
    fun historyRestoreSyncsUiStateSoOldNoticesDoNotReplay() {
        val src = main()
        val marker = "entry.savedState=entry.state;"
        val at = src.indexOf(marker)
        assertTrue("找不到历史记录的恢复点", at >= 0)
        val line = src.substring(at, src.indexOf('\n', at))
        assertTrue(
            "恢复历史记录时必须同步 entry.uiState=entry.state —— " +
                "否则旧记录会被当成「刚刚完成」，每次启动重播一遍下载完成通知",
            line.contains("entry.uiState=entry.state"),
        )
    }

    /** 反向探针：确认 `bodyOf` 真的取到方法体，而不是取空/取错范围（否则上面全是假绿）。 */
    @Test
    fun theBodyExtractionActuallyGrabsTheMethod() {
        val body = bodyOf(main(), "void noteDownloadOutcome(DownloadEntry entry){")
        assertTrue("取到的应该是方法体而不是空串", body.length > 200)
        assertTrue("取到的范围里应该含有该方法的特征代码", body.contains("showTopBanner("))
        // 喂一段假的：必须能抓出"没有 stateChanged 保护"和"缺 uiState 同步"这两种坏代码
        assertFalse(
            "坏代码（没有 stateChanged 保护）不该被误判成合格",
            guardsOnStateChanged("for(DownloadEntry entry:changed){noteDownloadOutcome(entry);}"),
        )
        assertTrue(
            "修好的代码必须被认出来",
            guardsOnStateChanged("for(DownloadEntry entry:changed){if(stateChanged)noteDownloadOutcome(entry);}"),
        )
        assertFalse(
            "坏代码（恢复时没同步 uiState）不该被误判成合格",
            syncsUiStateOnRestore("entry.savedState=entry.state;entry.savedPercent=1;"),
        )
        assertTrue(
            "修好的代码必须被认出来",
            syncsUiStateOnRestore("entry.savedState=entry.state;entry.uiState=entry.state;"),
        )
    }

    /**
     * 守卫本体抽成函数，**反向探针才能把坏代码真的喂进来**。
     *
     * ⚠️ 原来那版第二条探针是 `"…".contains("…")` 字面量自比，编译期恒真、零鉴别力。
     */
    private fun guardsOnStateChanged(src: String): Boolean =
        Regex("if\\(stateChanged\\)noteDownloadOutcome\\(entry\\)").containsMatchIn(src)

    private fun syncsUiStateOnRestore(src: String): Boolean = src.contains("entry.uiState=entry.state")
}
