package cc.nkbr.lanzouplus

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * [DFWX] DFW-55（T18）搜索结果滚动与长列表：**嵌套滚动豁免**回归守卫。
 *
 * ## 历史缺陷（`docs/agents/lessons.md` 2026-09-21 晚②，用户当时报告"搜索底部留白截断"）
 * 搜索模式下外层 `homeScroll` 在内层 move 超过 touchSlop 后**无条件抢走竖向手势**，
 * 于是：手指直滑搜索结果**完全不动**；窗口化渲染 `maybeAppendSearchWindow` 永不触发
 * → 长列表**底部留白截断**。
 *
 * 修法是 DOWN 时 `requestDisallowInterceptTouchEvent(true)`，让内层接管结果区滑动。
 * 关键点在于：**这个修复只对"内层真正收到 DOWN"的路径有效**——
 * 如果哪天有人重构掉这段 dispatchTouchEvent，症状会以"列表滑不动/底部空白"的形式回来，
 * 而**没有任何测试会红**。所以把它钉住。
 *
 * ## 为什么这里不做"排序"相关断言
 * 卡片（T17）明确禁止"客户端全量排序"，且要求先研究接口能力——那需要真机/网络取证，
 * 属待决策项，详见任务评论，**不在此测试里假装完成**。
 */
class SearchListScrollJvmTest {

    private val source: String = File(
        (System.getProperty("user.dir") ?: ".").let { dir ->
            var d = File(dir).absoluteFile
            while (!java.io.File(d, "src/main/java/cc/nkbr/lanzouplus/MainActivity.java").isFile && d.parentFile != null) d = d.parentFile
            d
        },
        "src/main/java/cc/nkbr/lanzouplus/MainActivity.java",
    ).readText(Charsets.UTF_8)

    /** 按大括号配对取方法体（避免"截固定长度导致窗口越界到隔壁方法"的假绿）。 */
    private fun blockAfter(anchor: String): String {
        val start = source.indexOf(anchor)
        require(start >= 0) { "找不到锚点：$anchor" }
        val open = source.indexOf('{', start)
        var depth = 0
        var i = open
        while (i < source.length) {
            when (source[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return source.substring(open + 1, i)
                }
            }
            i++
        }
        error("未闭合：$anchor")
    }

    @Test
    fun searchResultArea_disallowsParentInterceptOnPointerDown() {
        // 文件里有多处 requestDisallowInterceptTouchEvent（拖动条、下拉刷新等都各自有），
        // 必须精确定位到**搜索结果的滚动容器**那一处 —— 用它的方法签名锚点，
        // 而不是拿"第一次出现"凑数（第一版就是这么误判的）。
        val anchor = "@Override public boolean dispatchTouchEvent(android.view.MotionEvent e){"
        val idx = source.indexOf(anchor)
        assertTrue(
            "搜索结果的滚动容器必须自绘 dispatchTouchEvent（外层 homeScroll 会抢走竖向手势，" +
                "导致列表滑不动、底部留白截断）：找不到该重写",
            idx >= 0,
        )
        // 取它所在匿名类的这一个方法体（到 super.dispatchTouchEvent 为止）
        val end = source.indexOf("return super.dispatchTouchEvent(e);", idx)
        assertTrue("必须调用 super.dispatchTouchEvent(e)", end > idx)
        val block = source.substring(idx, end)

        assertTrue(
            "DOWN 时必须请求父级不要拦截（否则外层抢走手势）",
            block.contains("MotionEvent.ACTION_DOWN") &&
                block.contains("requestDisallowInterceptTouchEvent(true)"),
        )
        assertTrue(
            "UP/CANCEL 必须释放豁免（只请求不释放会让外层永久收不到手势）",
            block.contains("MotionEvent.ACTION_UP") && block.contains("MotionEvent.ACTION_CANCEL") &&
                block.contains("requestDisallowInterceptTouchEvent(false)"),
        )
    }

    /** 豁免必须发生在 `super.dispatchTouchEvent(e)` **之前**（否则事件已被消费/转发）。 */
    @Test
    fun disallowHappensBeforeSuperDispatch() {
        val anchor = "@Override public boolean dispatchTouchEvent(android.view.MotionEvent e){"
        val start = source.indexOf(anchor)
        assertTrue("必须能找到搜索容器的手势重写", start >= 0)
        val sup = source.indexOf("return super.dispatchTouchEvent(e);", start)
        assertTrue("豁免之后必须调用 super.dispatchTouchEvent", sup > start)
        val block = source.substring(start, sup)
        val inDown = block.indexOf("requestDisallowInterceptTouchEvent(true)")
        assertTrue("true 豁免必须在该方法体内", inDown > 0)
        assertTrue(
            "豁免必须写在 super 调用之前（实现即如此）",
            inDown < block.length,
        )
    }

    /** 长列表必须有窗口化增量渲染（T18 的另一半：底部留白截断）。 */
    @Test
    fun longListHasWindowedAppendAndScrollTrigger() {
        assertTrue(
            "必须有窗口化追加渲染（否则长结果集会一次性铺满、卡顿且截断）",
            source.contains("maybeAppendSearchWindow") || source.contains("appendSearchWindow"),
        )
    }

    /** 文件夹优先排序仍在（不要让排序相关改动把它弄丢）。 */
    @Test
    fun folderFirstOrderingPreserved() {
        val idx = source.indexOf("static void sortSearchItems(")
        assertTrue("sortSearchItems 必须存在", idx >= 0)
        val body = blockAfter("static void sortSearchItems(")
        assertTrue(
            "搜索结果必须保持'文件夹优先'顺序（用户可理解的既有语义）：$body",
            body.contains("folder"),
        )
    }
}
