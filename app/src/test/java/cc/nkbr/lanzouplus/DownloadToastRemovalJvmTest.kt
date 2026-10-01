package cc.nkbr.lanzouplus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * [DFW-94] 「下载悬浮窗」必须彻底消失，但**下载页 / 下载历史 / 顶部通知条必须活着**。
 *
 * ## 用户要求（2026-10-01 原话）
 * - 删悬浮窗：**"没啥用，还丑的慌，而且还挡地方。"**
 * - 保记录：**"我都已经有个下载界面了，怎么可能不保留所有的记录呢？"**
 *
 * ## 为什么删一个 UI 还要写测试
 * 这套悬浮卡和「顶部通知条」「批量整理进度条」「源测试进度条」**共用同一个浮层底座**
 * （`toastLayer` / `toastPanel()` / `addToastPanel` / `dismissPanel` / `swipePanel`）。
 * 删的时候手一滑就会连底座一起端走 —— 那不是"少了个卡片"，
 * 而是**全站通知、批量整理进度、下载页全部失灵**，而且编译能过、跑起来才发现。
 *
 * 这个项目真的出过这种事：DFW-98 按"最近的上一个空行"定位方法起点删代码，
 * 把相邻的 `showSupportNotice` 和 `onTrimMemory` 一起删了。
 *
 * ## 这条测试守什么
 * 1. **删干净**：悬浮窗的 29 个符号在 `MainActivity.java` 里必须 **0 命中**
 *    （留一个 `taskToasts` 字段就是留了个永远为空的幽灵状态）。
 * 2. **没删过头**：10 个共用底座符号必须 **≥1 命中**。
 */
class DownloadToastRemovalJvmTest {

    private val root: File = run {
        var dir = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (!File(dir, "AGENTS.md").isFile && dir.parentFile != null) dir = dir.parentFile
        dir
    }

    private fun main() =
        File(root, "app/src/main/java/cc/nkbr/lanzouplus/MainActivity.java").readText(Charsets.UTF_8)

    /** 悬浮窗专属符号：字段、内部类、方法、以及只为它存在的标记位。 */
    private val deletedSymbols = listOf(
        // 浮层状态
        "downloadToastEntries",
        "taskToasts",
        "toastLabels",
        "toastBars",
        "mergedDownloadToast",
        // 内部类
        "BatchToastState",
        "DownloadPopupHolder",
        // 单卡
        "createDownloadToast",
        "tapDownloadToast",
        "holdDownloadToast",
        "updateDownloadToast",
        "dismissDownloadToast",
        "detachDownloadToast",
        "visibleDownloadToastEntries",
        // 合并卡
        "createMergedDownloadToast",
        "updateMergedDownloadToast",
        "detachMergedDownloadToast",
        "syncDownloadToastPresentation",
        "showDownloadToastHistory",
        // 浮层自身的小工具（改完只剩它自己没人调）
        "detachToastNow",
        // 批量解析计数（只为合并卡显示"解析 x"）
        "createBatchParsingToast",
        "finishBatchParsing",
        "finishDownloadResolution",
        // DownloadEntry / PendingRetry 上的悬浮窗标记位
        "suppressParseToast",
        "preserveToast",
        "toastDismissed",
        "dismissScheduled",
        "resolveDone",
        "resolutionAdvanced",
    )

    /**
     * 共用底座 + 用户要保留的记录功能。**少一个都不行。**
     * 这些名字一旦消失，意味着"删悬浮窗"把别的功能一起带走了。
     */
    private val keptSymbols = listOf(
        // 浮层底座（顶部通知条、批量整理进度、源测试进度都在用）
        "toastPanel()",
        "addToastPanel",
        "swipePanel",
        "dismissPanel",
        "updateToastViewport",
        "toastLayer",
        // 用户明确要保留的"记录"
        "showWebDownloadHistoryDialogNow",
        "renderDownloads",
        // 批量下载队列推进机制（和提示卡无关，别误删）
        "advanceBatch",
        "dirtyDownloadUi",
    )

    private fun hits(src: String, symbol: String): Int {
        var count = 0
        var from = src.indexOf(symbol)
        while (from >= 0) {
            count++
            from = src.indexOf(symbol, from + symbol.length)
        }
        return count
    }

    @Test
    fun floatingDownloadCard_isCompletelyGone() {
        val src = main()
        val leftover = deletedSymbols.filter { hits(src, it) > 0 }
        assertTrue(
            "悬浮窗这些符号还在 MainActivity.java 里（DFW-94 没删干净）：$leftover",
            leftover.isEmpty(),
        )
    }

    @Test
    fun sharedToastFoundation_survivesTheRemoval() {
        val src = main()
        val missing = keptSymbols.filter { hits(src, it) == 0 }
        assertTrue(
            "这些是顶部通知条/下载页共用的底座，被误删了 —— 全站通知会失灵：$missing",
            missing.isEmpty(),
        )
    }

    /**
     * 反向探针 1：确认守卫**真的能红**。
     *
     * 喂一段改前的真实代码（从 `createDownloadToast` 抄下来的原句），
     * 守卫必须把它认出来。否则"0 命中"可能只是因为**符号名写错了**
     * —— 那才是最阴的假绿：测试永远绿，代码其实没删。
     */
    @Test
    fun theGuardActuallyDetectsTheOldCode() {
        val oldSnippet = "taskToasts.put(entry,panel);toastLabels.put(entry,label);" +
            "toastBars.put(entry,bar);addToastPanel(panel,80);" +
            "swipePanel(panel,()->{entry.toastDismissed=true;downloadToastEntries.remove(entry);" +
            "dismissDownloadToast(entry,panel);syncDownloadToastPresentation();},()->tapDownloadToast(entry));"
        val detected = deletedSymbols.filter { hits(oldSnippet, it) > 0 }
        assertTrue(
            "反向探针失败：改前的真实代码居然一个符号都没命中，说明符号名写错了（假绿）",
            detected.size >= 8,
        )
        // ⚠️ 这里**故意不断言**"旧代码里不含保留符号" —— 底座本来就是两边共用的
        // （`addToastPanel` / `swipePanel` 在悬浮卡和顶部通知条里都出现），
        // 断言它反而会把"共用底座"这个事实写死成错误预期。
    }

    /** 反向探针 2：`hits` 本身要准 —— 数错个数会让上面两条断言全部失去意义。 */
    @Test
    fun theHitCounterCountsCorrectly() {
        assertEquals(0, hits("abc", "zzz"))
        assertEquals(1, hits("a taskToasts b", "taskToasts"))
        assertEquals(3, hits("taskToasts taskToasts taskToasts", "taskToasts"))
    }
}
