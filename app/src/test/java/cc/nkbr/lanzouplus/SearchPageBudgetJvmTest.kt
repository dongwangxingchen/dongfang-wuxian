package cc.nkbr.lanzouplus

import android.os.Looper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * [DFW-96] 搜索提速：**全局搜索每个源最多翻 3 页**。
 *
 * ## 用户反馈与拍板（2026-10-02）
 * > 「搜索速度还是太慢」→ 追问后明确：**「全部搜完要等太久」**。
 * > 给出的选项里用户选了 **A：全局搜索每个源最多翻 3 页**。
 *
 * ## 实测出来的根因（不是猜的）
 * | 事实 | 出处 |
 * |---|---|
 * | 全局搜索原来复用 `sessionSearchMaxPages`，默认 0 被当成 **1000 页** | `LanzouCore.java:393` |
 * | 同域名每页至少间隔 **1100ms** | `LanzouCore.java:62` `ORIGIN_PAGE_SLOT_MS` |
 * | 开了文件夹递归后每个子文件夹再翻一遍 | `LanzouCore.java:1558-1573` |
 * | 搜索总预算 **180 秒** | `LanzouCore.java:86` `DEFAULT_SEARCH_BUDGET_MS` |
 *
 * 所以"跑满 3 分钟"是常态。59 个源每个都往死里翻，翻不完才怪。
 *
 * ## 关键：**不能把单源搜索一起砍了**
 * `sessionSearchMaxPages` 在界面上叫「**单源翻页**」（`perfTierFacts`），它的本意是
 * "点进一个源之后能翻多深"。用户抱怨的是全局搜索，单源深挖是他自己主动缩小的范围，
 * 一起砍掉就是**用户没要求过的功能回退**。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class SearchPageBudgetJvmTest {

    private fun activity(): MainActivity {
        val a = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        shadowOf(Looper.getMainLooper()).idle()
        return a
    }

    @Test
    fun globalSearchIsCappedAtThreePagesPerSource() {
        assertEquals(
            "全局搜索每个源的上限必须是 3 页（用户 2026-10-02 拍板）",
            3,
            MainActivity.SEARCH_MAX_PAGES_GLOBAL,
        )
        val options = activity().searchOptions(59)
        assertEquals("全局搜索必须用 3 页上限", 3, options.maxPages)
        assertTrue("必须要求翻到底（由 maxPages 决定深度）", options.untilLastPage)
    }

    /**
     * 单源搜索**不许**被全局上限误伤。
     * 这条是本卡最容易搞砸的地方：两处都调 `searchOptions(...)`，
     * 图省事改一个默认值就会把单源深挖一起砍掉。
     */
    @Test
    fun singleSourceSearchKeepsItsOwnDepth() {
        val a = activity()
        a.sessionSearchMaxPages = 20
        val single = a.searchOptions(1, a.sessionSearchMaxPages)
        assertEquals("单源搜索要用「单源翻页」设置，不能被全局 3 页盖掉", 20, single.maxPages)
        assertNotEquals(
            "单源上限与全局上限是两个独立的值",
            MainActivity.SEARCH_MAX_PAGES_GLOBAL,
            single.maxPages,
        )

        // 0 表示"无限"（界面上就是这么显示的），也必须原样传下去
        a.sessionSearchMaxPages = 0
        assertEquals(
            "「无限」必须原样传下去（0 由 LanzouCore.pageLimit 解释成 1000）",
            0,
            a.searchOptions(1, a.sessionSearchMaxPages).maxPages,
        )
    }

    /** 用户要能在设置里**看见**这条上限，否则"为什么搜不到深处的东西"无从解释。 */
    @Test
    fun thePerfTierTextShowsTheGlobalCap() {
        val facts = activity().perfTierFacts()
        assertTrue("性能档说明里要写清全局每源 3 页：$facts", facts.contains("全局每源 3 页"))
        assertTrue("单源翻页仍要单独显示：$facts", facts.contains("单源翻页"))
    }

    /**
     * 反向探针：确认"两个值确实不同"这件事是真的被区分了 ——
     * 如果哪天有人把全局改成 `searchOptions(total, sessionSearchMaxPages)`，
     * 上面那条 `assertNotEquals` 就会因为两者相同而变红。
     */
    @Test
    fun theTwoBudgetsAreGenuinelyIndependent() {
        val a = activity()
        a.sessionSearchMaxPages = 20
        assertEquals("全局：固定 3", 3, a.searchOptions(59).maxPages)
        assertEquals("单源：跟设置走", 20, a.searchOptions(1, a.sessionSearchMaxPages).maxPages)
    }
}
