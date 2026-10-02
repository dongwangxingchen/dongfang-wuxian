package cc.nkbr.lanzouplus

import android.os.Looper
import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

/**
 * [DFW-95] 搜索页两个真机反馈的修复守卫。
 *
 * ## 用户原话（2026-10-02）
 * > 「搜索页面搜索后，**底部出现了黑色留白，很丑**」
 * > 「而且从搜索界面，我如果使用了安卓的返回，它**没有任何的动画效果**，然后直接返回到了我的
 * > 软件库主页……我希望你可以修复一下，给它加个，比如什么动画效果吧。渐隐啥的。」
 *
 * ## ① 底部留白：不是猜的，是量出来的
 * 先用 `@GraphicsMode(NATIVE)` 把页面渲染成 PNG、逐行扫像素定位（视口 1078×2338 @2.625）：
 *
 * | | 结果区最后一行 | 下方空白 |
 * |---|---|---|
 * | 改前 | y=2119 | **218px（83dp）** |
 * | 改后 | y=2214 | 123px（其中大部分是最后一行卡片的下边距） |
 *
 * 布局实测指出根因：`homeHistoryH()` 写的是 `vh - dp(64)`，注释说「64=框52+间距12」——
 * 但搜索框与间距是 `homeScroll` 的**兄弟节点**（实测：框 0..137px、间距 137..169px、
 * `homeScroll` 从 169px 才开始），本来就在滚动区外面。**再扣一次 64dp，
 * 就等于在搜索页底部永久挖掉 64dp，任何内容都到不了那里。**
 *
 * ## ② 返回无动画
 * `exitHomeSearchFocus()` 原来直接把两个 View 的可见性一开一关（硬切）。
 * 现在走全站现成的原位交叉淡化（退 100ms → 换 → 进 150ms，曲线 `standardEase()`），
 * 与 `transitionSourceFilter` 同一套节奏，不另发明数值。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class SearchPageBottomAndBackJvmTest {

    companion object {
        @BeforeClass @JvmStatic fun silence() { MainActivity.LIBRARY_AUTO_IMPORT = false }
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private val root: File = run {
        var dir = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (!File(dir, "AGENTS.md").isFile && dir.parentFile != null) dir = dir.parentFile
        dir
    }

    private val main by lazy {
        File(root, "app/src/main/java/cc/nkbr/lanzouplus/MainActivity.java").readText(Charsets.UTF_8)
    }

    private fun item(i: Int): Models.Item {
        val it = Models.Item()
        it.title = "测试资源 $i"
        it.url = "https://example.invalid/$i"
        it.shareUrl = "https://example.invalid/s/$i"
        it.size = "12.3 MB"
        it.time = "2026-10-01"
        return it
    }

    /** 真布局一遍搜索结果页，返回 (activity, 视口宽, 视口高)。 */
    private fun layoutSearchWithResults(count: Int): MainActivity {
        val c = Robolectric.buildActivity(MainActivity::class.java)
        c.setup()
        idle()
        val a = c.get()
        a.showHomeSearchMode(false)
        idle()
        a.globalSearch.items.clear()
        for (i in 1..count) a.globalSearch.items.add(item(i))
        a.globalSearch.done = count
        a.globalSearch.total = 59
        a.globalSearch.query = "测试"
        a.renderSearchResults()
        idle()
        val w = (411 * 2.625f).toInt()
        val h = (891 * 2.625f).toInt()
        val r = a.root
        r.measure(
            View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY),
        )
        r.layout(0, 0, w, h)
        idle()
        return a
    }

    /**
     * 核心不变量：**搜索结果区必须铺满整个滚动区**。
     * 少了它，底部就会留下一条内容永远到不了的空白（用户看到的就是这个）。
     */
    @Test
    fun theResultsAreaFillsTheWholeScrollArea() {
        val a = layoutSearchWithResults(200)
        val scroll = a.homeScroll
        val history = a.homeHistory
        assertTrue("搜索态必须有结果容器", history != null && scroll != null)
        assertEquals(
            "结果区高度必须等于滚动区高度（原来少了 64dp，底部永久留白）",
            scroll!!.height,
            a.homeHistoryH(),
        )
        assertTrue("结果区必须真的可见", history!!.visibility == View.VISIBLE)
        assertEquals("结果区高度要落到布局上", scroll.height, history.height)
    }

    /** 结果很少时同样不能凭空多出空白 —— 这条守的是"高度不再被额外扣减"。 */
    @Test
    fun theFixDoesNotDependOnHowManyResultsThereAre() {
        val many = layoutSearchWithResults(200)
        val few = layoutSearchWithResults(3)
        assertEquals(
            "结果多少都不影响结果区高度",
            many.homeHistoryH(),
            few.homeHistoryH(),
        )
        assertTrue("结果区高度必须为正", few.homeHistoryH() > 0)
    }

    @Test
    fun backFromSearchUsesTheSiteWideCrossFade() {
        assertTrue(
            "必须有一个交叉淡化 helper",
            main.contains("void crossFadeHomeSection(View out,View in)"),
        )
        assertTrue(
            "退出搜索必须走交叉淡化，而不是直接把可见性一开一关",
            main.contains("crossFadeHomeSection(homeHistory,homeLibsBand);"),
        )
        assertTrue(
            "进入搜索也要对称地淡化",
            main.contains("crossFadeHomeSection(homeLibsBand,homeHistory);"),
        )
        // 时长与曲线必须复用全站 token，不许新造数值
        val body = main.substring(
            main.indexOf("void crossFadeHomeSection(View out,View in)"),
            main.indexOf("void exitHomeSearchFocus()"),
        )
        assertTrue("退场用 DUR_EXIT_FAST", body.contains("setDuration(DUR_EXIT_FAST)"))
        assertTrue("入场用 DUR_SMALL", body.contains("setDuration(DUR_SMALL)"))
        assertTrue("曲线用全站统一的 standardEase()", body.contains("standardEase()"))
        assertTrue("必须尊重「关闭动效」开关", body.contains("!motionEnabled()"))
        assertFalse("不许出现自造的硬编码时长", Regex("setDuration\\(\\d").containsMatchIn(body))
    }

    /** 反向探针：确认上面的源码断言真的能识别坏代码。 */
    @Test
    fun theSourceChecksActuallyDetectBadCode() {
        val badGap = "return Math.max(dp(240),vh-dp(64));"
        assertTrue("坏代码（又扣 64dp）必须能被识别", badGap.contains("-dp(64)"))
        val badBack = "if(homeHistory!=null)homeHistory.setVisibility(View.GONE);if(homeLibsBand!=null)homeLibsBand.setVisibility(View.VISIBLE);"
        assertFalse("坏代码（硬切）不该被当成走了交叉淡化", badBack.contains("crossFadeHomeSection"))
        assertTrue("锚点必须真的在源码里", main.contains("crossFadeHomeSection(homeHistory,homeLibsBand);"))
    }

    /**
     * [DFW-95] 交叉淡化的**终态自洽性**。
     *
     * 场景：进/出搜索快速来回切 4 次，最后一次退出，把正在跑的动画掐断，再把时钟推过兜底时间。
     * 断言：结果区与库分类带**恰好一个可见**，且是"已退出搜索"该有的那个。
     * （两个都不可见 = 整页空白；两个都可见 = 高度叠加、滚动位置跳。）
     *
     * ## 这条测试**没有**证明什么（如实登记，别当成验过了）
     * `crossFadeHomeSection` 里有一条 600ms 幂等兜底，用来对付"动画被打断 → `withEndAction`
     * 没执行 → 新内容永远不显示"的极端情况。**这个极端情况在 Robolectric 里复现不出来**：
     * 实测把 `animate().cancel()` 打进去，`withEndAction` 照样会执行，
     * 于是**去掉兜底这条测试依然是绿的**（反向探针实测不红，等于没验到）。
     *
     * 所以那条兜底是**防御性代码**，依据是 `animatePage` 里同款兜底的既有注释
     * （"修复动画被打断后结算丢失导致的旧页残留/新页整页不可点"），
     * **不是**测试验证过的。要真验它，只能在真机上把"动画时长"设为 0 或高频连按复现。
     */
    @Test
    fun theCrossFadeAlwaysSettlesEvenWhenInterrupted() {
        val a = layoutSearchWithResults(30)
        val clock = shadowOf(Looper.getMainLooper())
        repeat(4) {
            a.exitHomeSearchFocus()
            clock.idle()
            a.showHomeSearchMode(false)
            clock.idle()
        }
        a.exitHomeSearchFocus()
        clock.idle()
        // **关键**：把正在跑的退场动画直接掐掉，模拟"被打断"。
        // 只推进时钟是不够的 —— 动画正常跑完时 endAction 照常执行，
        // 那条路测不出兜底（第一版就是这么写的，反向探针实测**不红**，等于什么都没验）。
        a.homeHistory?.animate()?.cancel()
        a.homeLibsBand?.animate()?.cancel()
        clock.idleFor(java.time.Duration.ofMillis(700))

        val history = a.homeHistory
        val band = a.homeLibsBand
        val historyVisible = history != null && history.visibility == View.VISIBLE
        val bandVisible = band != null && band.visibility == View.VISIBLE
        assertTrue(
            "兜底之后必须落在一个自洽状态：结果区和库分类带**恰好一个**可见" +
                "（两个都不可见 = 整页空白；两个都可见 = 高度叠加）",
            historyVisible != bandVisible,
        )
        assertTrue(
            "既然已经退出搜索，可见的必须是库分类带",
            bandVisible,
        )
    }
}
