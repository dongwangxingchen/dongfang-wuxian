package cc.nkbr.lanzouplus

import android.graphics.drawable.GradientDrawable
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * [BRAND-001] 诚信付费页的结构与交互守卫。
 *
 * ## 这一页的红线（比"好不好看"更要紧）
 * 1. **不付费也能走**：页面上必须永远有一条可见、可点、有反馈的降级出口
 *    （「暂时不支持，继续使用」）。这条出口如果变成一个点下去毫无反应的裸文字，
 *    用户会以为软件坏了——而这一页恰恰是唯一会让用户产生"是不是要逼我付钱"疑虑的地方。
 * 2. **解锁不能连点**：解锁会整页重建，连点两次会在重建途中再触发一次，
 *    表现是按钮闪一下/白屏一帧。
 * 3. **￥5 必须一眼可见**（用户 2026-10-01 明确要求价格留在中段），不能因为排版改动被挤走。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class SponsorPageJvmTest {

    private fun activity(): SupportActivity {
        val a = Robolectric.buildActivity(SupportActivity::class.java).setup().get()
        shadowOf(Looper.getMainLooper()).idle()
        return a
    }

    private fun textsIn(view: View, out: MutableList<String> = mutableListOf()): List<String> {
        if (view is TextView) view.text?.toString()?.let { if (it.isNotEmpty()) out.add(it) }
        if (view is ViewGroup) for (i in 0 until view.childCount) textsIn(view.getChildAt(i), out)
        return out
    }

    private fun find(view: View, match: (View) -> Boolean): View? {
        if (match(view)) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) find(view.getChildAt(i), match)?.let { return it }
        return null
    }

    /** 往上找最近一个"卡片"（card() 造的就是带 GradientDrawable 背景的 LinearLayout）。 */
    private fun nearestCard(view: View?): View? {
        var cur = view?.parent
        while (cur is View) {
            if (cur is ViewGroup && cur.background is GradientDrawable) return cur
            cur = cur.parent
        }
        return null
    }

    // ── 红线 1：降级出口 ──────────────────────────────────────────────────

    @Test
    fun escapeHatch_isVisibleClickableAndHasFeedback() {
        val a = activity()
        val skip = find(a.root) { it is TextView && (it.text?.toString() == "暂时不支持，继续使用") }
        assertNotNull("不付费也能走的出口必须还在（这是本页红线）", skip)
        assertTrue("必须可点", skip!!.isClickable)
        assertTrue("必须可聚焦（无障碍）", skip.isFocusable)
        assertNotNull(
            "必须带按压反馈：裸文字点下去毫无回应，用户会以为软件坏了",
            skip.background,
        )
        assertTrue(
            "必须能读出来它是干什么的",
            (skip.contentDescription?.toString() ?: "").contains("暂时不支持"),
        )
    }

    @Test
    fun escapeHatch_actuallyClosesThePage() {
        val a = activity()
        val skip = find(a.root) { it is TextView && (it.text?.toString() == "暂时不支持，继续使用") }!!
        skip.performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue("点了「暂时不支持」必须能退出去", a.isFinishing)
    }

    // ── 红线 2：解锁防连点 ────────────────────────────────────────────────

    @Test
    fun unlock_ctaIsGuardedAgainstDoubleTap() {
        val a = activity()
        val cta = find(a.root) { it is Button && (it.text?.toString() ?: "").contains("诚信付费") }
        assertNotNull("主按钮必须还在", cta)
        assertFalse("点之前不该是解锁中", a.unlocking)

        cta!!.performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue("点一次就该进入解锁流程", a.unlocking)
        assertTrue("解锁后应切到感谢页", a.thankYouMode)
        assertEquals("点一次只该真正解锁一次", 1, a.unlockInvocations)

        // 再点一次（用户连点）：不许抛异常、不许把状态弄乱、更不许再解锁一次
        cta.performClick()
        cta.performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue("连点第二次必须被吞掉", a.thankYouMode)
        assertEquals("连点不许重复执行解锁流程（整页重建不是幂等的）", 1, a.unlockInvocations)
    }

    @Test
    fun unlock_ctaCarriesAnExplicitDescription() {
        val a = activity()
        val cta = find(a.root) { it is Button && (it.text?.toString() ?: "").contains("诚信付费") }!!
        val desc = cta.contentDescription?.toString() ?: ""
        assertTrue("主按钮必须说清「付了会怎样、不付会怎样」：实际=$desc", desc.contains("不付费也可以完整使用"))
    }

    // ── 红线 3：价格在中段、且是一个设计过的块 ────────────────────────────

    @Test
    fun priceStaysVisibleInAMiddleBlock() {
        val a = activity()
        val amount = find(a.root) { it is TextView && it.text?.toString() == "￥5" }
        assertNotNull("￥5 必须仍然可见（用户 2026-10-01 明确要求）", amount)
        assertNotNull("价格必须落在卡片里，而不是夹在两段文字之间的裸行", nearestCard(amount))
        assertTrue(
            "价格的两条说明必须还在",
            textsIn(a.root).containsAll(listOf("诚信付费 · 一次付清", "承诺永久更新 · 绝不停更")),
        )
    }

    @Test
    fun pageReadsAsThreeStackedBlocks() {
        // 开发者信 / 权益 / 价格 —— 三块同一节奏，才像一页设计过的产品页。
        val a = activity()
        val cards = mutableListOf<View>()
        fun walk(view: View) {
            if (view is ViewGroup && view.background is GradientDrawable && view !== a.root) cards.add(view)
            if (view is ViewGroup) for (i in 0 until view.childCount) walk(view.getChildAt(i))
        }
        walk(a.root)
        assertTrue("至少要有 信 / 权益 / 价格 / 收款码 四块卡片，实际 ${cards.size}", cards.size >= 4)
    }

    // ── 已解锁态 ──────────────────────────────────────────────────────────

    @Test
    fun thankYouPage_keepsTheSameHeaderAndSaysWhatIsUnlocked() {
        val a = activity()
        Support.unlock(a)
        a.renderThankYou()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(a.thankYouMode)
        val texts = textsIn(a.root)
        assertTrue("感谢页要说清解锁了什么", texts.any { it.contains("全部下载权限已开放") })
        assertTrue("还要有感谢页的标题", texts.any { it.contains("已解锁") })
        // 返回控件必须和付费页长得一样：同一个左上角「←」
        val back = find(a.root) { it.contentDescription?.toString() == "返回" }
        assertNotNull("感谢页必须有左上角返回", back)
    }

    @Test
    fun unpaidPage_doesNotPretendToHaveVerifiedThePayment() {
        // 卡片红线：本地确认不许包装成"已向支付平台核验"。
        val a = activity()
        val texts = textsIn(a.root).joinToString("|")
        assertFalse("不许出现「已核验/已到账」这类谎话：$texts", texts.contains("已核验") || texts.contains("支付平台"))
        assertTrue("必须诚实说明是本地标记", texts.contains("解锁记录保存在本机 · 不付费也可以完整使用"))
    }

    @Test
    fun pageNeverShowsTheDeletedLines() {
        // 用户 2026-10-01 逐字要求删掉的两行，不许因为排版改动被加回来。
        val a = activity()
        val texts = textsIn(a.root).joinToString("|")
        assertFalse("「诚信付费 ￥5 · 一次付清 · 承诺永久更新」必须保持删除", texts.contains("诚信付费 ￥5 · 一次付清 · 承诺永久更新"))
        assertFalse("页头 kicker「诚信付费 · 自愿」必须保持删除", texts.contains("诚信付费 · 自愿"))
        assertEquals(
            "权益只留用户口述的那一条",
            1,
            textsIn(a.root).count { it.contains("每周自费续 1 万次 DeepSeek v4.1") },
        )
    }
}
