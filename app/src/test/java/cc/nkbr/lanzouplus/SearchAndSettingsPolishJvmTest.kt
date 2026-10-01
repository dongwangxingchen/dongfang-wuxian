package cc.nkbr.lanzouplus

import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
 * [DFW-39 / DFW-24] 两处"看得见但没人测过"的修复守卫。
 *
 * ## DFW-39：搜索停在「3/50 个源 · 已完成 0」
 * 只读调查（对 HEAD `69bb1e3` 逐行核对）确认的一半原因是**慢源轮换从来没开过**：
 * `searchOptions` 旧式 `requested<=0 || requested>=total ? 0 : batchSeconds*1000`，
 * 而默认是自动模式（`sessionSearchConcurrency=0`）→ 时间片恒为 0 →
 * 轮换调度器首行 `if(switchDelay<=0)return;` 直接返回 → 慢源占着名额跑到底，其余源无限排队。
 *
 * ## DFW-24：内联搜索把分区内容强制显示后，箭头还指着"收起"
 * 这是设置页唯一的逻辑可见 bug：视觉上箭头方向反了，读屏用户被告知"已收起"。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class SearchAndSettingsPolishJvmTest {

    companion object {
        @BeforeClass @JvmStatic fun silenceAutoImport() { MainActivity.LIBRARY_AUTO_IMPORT = false }

        private val repoRoot: File = run {
            var dir = File(System.getProperty("user.dir") ?: ".").absoluteFile
            while (!File(dir, "app/src/main/AndroidManifest.xml").isFile && dir.parentFile != null) dir = dir.parentFile!!
            dir
        }

        /** 按花括号配对取出某个方法的完整方法体（避免用 contains 假绿）。 */
        private fun methodBody(src: String, signature: String): String {
            val at = src.indexOf(signature)
            assertTrue("源码里找不到 $signature", at > 0)
            val open = src.indexOf('{', at)
            assertTrue("找不到方法体起始", open > 0)
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
            throw AssertionError("花括号不配对：$signature")
        }
    }

    private fun activity(): MainActivity {
        val a = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        shadowOf(Looper.getMainLooper()).idle()
        return a
    }

    // ── DFW-39：自动模式必须真的轮换 ─────────────────────────────────────

    @Test fun autoMode_stillGetsATimeSlice() {
        assertTrue(
            "自动模式（线程数=0）必须拿到 >0 的时间片，否则轮换调度器首行就 return，" +
                "慢源占槽跑到底 —— 这就是用户看到的『停在 3/50』的一半原因",
            MainActivity.sourceSwitchDelayMillis(0, 50, 15) > 0L,
        )
    }

    @Test fun manualModeBelowTotal_getsATimeSlice() {
        assertTrue(MainActivity.sourceSwitchDelayMillis(3, 50, 15) > 0L)
    }

    @Test fun oneThreadPerSource_needsNoRotation() {
        assertEquals(
            "每个源一条线程时不需要轮换",
            0L,
            MainActivity.sourceSwitchDelayMillis(50, 50, 15),
        )
    }

    @Test fun singleSource_needsNoRotation() {
        assertEquals(0L, MainActivity.sourceSwitchDelayMillis(0, 1, 15))
        assertEquals(0L, MainActivity.sourceSwitchDelayMillis(1, 1, 15))
    }

    @Test fun timeSlice_isAtLeastOneSecond_evenIfUserSetsZero() {
        // 用户把"慢源轮换时间"设成 0 或负数时，不能算出 0 —— 那等于又把轮换关掉。
        assertTrue("时间片必须 ≥1s", MainActivity.sourceSwitchDelayMillis(0, 50, 0) >= 1000L)
        assertTrue("负数也要兜住", MainActivity.sourceSwitchDelayMillis(0, 50, -5) >= 1000L)
    }

    @Test fun searchOptions_actuallyUsesThePureFunction() {
        val src = File(repoRoot, "app/src/main/java/cc/nkbr/lanzouplus/MainActivity.java").readText()
        // [DFW-96] 时间片逻辑现在住在带 maxPages 的那个重载里（为了把"全局每源 3 页"和
        // "单源翻页"拆成两个独立预算）；三参版本只负责转调。断言跟着搬，但**两条都要守**：
        // 入口必须转调、真正的实现必须仍走纯函数。
        val entry = methodBody(src, "Models.SearchOptions searchOptions(int total)")
        assertTrue(
            "三参入口必须转调四参重载（否则两个预算会各写一份）",
            entry.contains("searchOptions(total,SEARCH_MAX_PAGES_GLOBAL)"),
        )
        val body = methodBody(src, "Models.SearchOptions searchOptions(int total,int maxPages)")
        assertTrue(
            "searchOptions 必须走 sourceSwitchDelayMillis，不许再把时间片逻辑内联回去",
            body.contains("sourceSwitchDelayMillis(requested,total,"),
        )
        assertFalse("旧的内联判据必须已经删掉", body.contains("requested<=0||requested>=Math.max(1,total)?0L"))
    }

    // ── DFW-24：搜索过滤要同步箭头与读屏描述 ─────────────────────────────

    private fun arrows(a: MainActivity): List<ImageView> {
        val out = mutableListOf<ImageView>()
        fun walk(v: View) {
            if (v is ImageView && v.id == R.id.dfwx_section_arrow) out.add(v)
            if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
        }
        walk(a.root)
        return out
    }

    @Test fun searchFilter_expandsTheArrowToo() {
        val a = activity()
        a.showSettings()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue("设置页必须有分区箭头", arrows(a).isNotEmpty())

        a.applySettingsFilter("下载")
        shadowOf(Looper.getMainLooper()).idle()
        val expanded = arrows(a).filter { it.parent?.parent?.let { p -> (p as? View)?.visibility } == View.VISIBLE }
        assertTrue("搜索必须命中至少一个分区（否则这条用例没意义）", expanded.isNotEmpty())
        for (arrow in expanded) {
            assertEquals(
                "搜索命中后分区内容被强制展开，箭头必须同步指向『已展开』（180°）",
                180f,
                arrow.rotation,
                0.01f,
            )
            val header = arrow.parent as View
            val desc = header.contentDescription?.toString() ?: ""
            assertTrue("读屏描述必须说『已展开』，否则读屏用户被误导：实际=$desc", desc.contains("已展开"))
        }
    }

    @Test fun clearingTheSearch_restoresTheArrowToTheUsersOwnState() {
        val a = activity()
        a.showSettings()
        shadowOf(Looper.getMainLooper()).idle()
        val before = arrows(a).map { it.rotation }
        a.applySettingsFilter("下载")
        shadowOf(Looper.getMainLooper()).idle()
        a.applySettingsFilter("")
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("清空搜索后箭头要回到用户自己的展开态", before, arrows(a).map { it.rotation })
    }

    // ── DFW-24：按压反馈 ─────────────────────────────────────────────────

    @Test fun pressScaleTiers_existAndAreTheSpecValues() {
        assertEquals("圆按钮档", 0.94f, MainActivity.PRESS_SCALE_ROUND, 0.0001f)
        assertEquals("长条行/胶囊档", 0.96f, MainActivity.PRESS_SCALE_PILL, 0.0001f)
    }

    @Test fun iconButton_usesTheRoundTier() {
        val a = activity()
        val b = a.iconButton(R.drawable.ic_back, "返回")
        // 按下一次，断言弹簧目标值是 0.94 档（圆按钮）
        b.dispatchTouchEvent(android.view.MotionEvent.obtain(0L, 0L, android.view.MotionEvent.ACTION_DOWN, 1f, 1f, 0))
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue("圆形图标按钮按下必须缩到 0.94", b.scaleX < 0.999f)
    }

    @Test fun theTwoHighFrequencyListRows_havePressFeedback() {
        // 软件库卡片与源列表行是全 App 最高频的两个点击面，原来**连 ripple 都没有**。
        val src = File(repoRoot, "app/src/main/java/cc/nkbr/lanzouplus/MainActivity.java").readText()
        for (sig in listOf("View itemRow(Models.Item", "View sourceRow(Models.Source")) {
            val body = methodBody(src, sig)
            assertTrue("$sig 必须补上 ripple（filterRipple）", body.contains("filterRipple("))
            assertTrue("$sig 必须补上按压缩放（applePressScale）", body.contains("applePressScale("))
            assertFalse("$sig 不许再是纯透明底、零反馈", body.contains("setBackgroundColor(Color.TRANSPARENT)"))
        }
    }

    @Test fun pressFeedback_firesAHapticOnRelease() {
        val src = File(repoRoot, "app/src/main/java/cc/nkbr/lanzouplus/MainActivity.java").readText()
        val body = methodBody(src, "public void applePressScale(View v,float pressedScale)")
        assertTrue(
            "规范 §3：按下的确认反馈要包含一次轻触觉；之前全仓只有悬浮球三处有",
            body.contains("performHapticFeedback("),
        )
        assertNotNull(body)
    }

    // ── DFW-24：分区副标题不再用"禁用档"亮度 ─────────────────────────────

    @Test fun sectionCaption_isNotTheDisabledBrightness() {
        val src = File(repoRoot, "app/src/main/java/cc/nkbr/lanzouplus/MainActivity.java").readText()
        val at = src.indexOf("TextView caption=text(summary,")
        assertTrue(at > 0)
        val line = src.substring(at, src.indexOf('\n', at))
        assertTrue("分区副标题要与行内 hint 同档（SET_T2）", line.contains("SET_T2"))
        assertFalse("不许再用 SET_T3（白 40% = 禁用档）", line.contains("SET_T3"))
    }
}
