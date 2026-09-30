package cc.nkbr.lanzouplus

import android.os.Looper
import android.view.View
import android.view.ViewGroup
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

/**
 * [DFWX] DFW-70：**子页面返回控件统一**的守卫。
 *
 * 用户 2026-10-01：
 * > "我希望你把这种类型的子页面返回按钮统一一下，别这个子页面左上角是叉号关闭，
 * >  那个子页面是右上角箭头关闭，这不纯胡闹呢？"
 *
 * 已拍板的全站约定：**`ic_back` = 返回上一页，一律在左上角；`ic_close` 只留给弹窗/浮层**。
 *
 * 只读审计的结果：全项目的 `ic_back` 本来就都在左侧（没有右侧箭头），
 * 真正不一致的是**诚信付费页**——它是全项目唯一用**文字「✕」**当关闭按钮、而且放在**右上角**的页面
 * （`SupportActivity` 的支持页与感谢页各一处）。本用例把它钉住。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class SupportPageHeaderJvmTest {

    companion object {
        @BeforeClass @JvmStatic fun silenceAutoImport() { MainActivity.LIBRARY_AUTO_IMPORT = false }
    }

    private fun rootOf(a: SupportActivity): View = a.findViewById(android.R.id.content)

    private fun descriptionsIn(view: View, out: MutableList<String> = mutableListOf()): List<String> {
        view.contentDescription?.toString()?.let { out.add(it) }
        if (view is ViewGroup) for (i in 0 until view.childCount) descriptionsIn(view.getChildAt(i), out)
        return out
    }

    private fun textsIn(view: View, out: MutableList<String> = mutableListOf()): List<String> {
        if (view is android.widget.TextView) view.text?.toString()?.let { if (it.isNotEmpty()) out.add(it) }
        if (view is ViewGroup) for (i in 0 until view.childCount) textsIn(view.getChildAt(i), out)
        return out
    }

    private fun supportActivity(): SupportActivity {
        val a = Robolectric.buildActivity(SupportActivity::class.java).setup().get()
        shadowOf(Looper.getMainLooper()).idle()
        return a
    }

    @Test
    fun supportPage_hasTheUnifiedBackControl() {
        val a = supportActivity()
        val root = rootOf(a)
        assertNotNull(root)
        assertTrue(
            "诚信付费页必须有统一语义的「返回」控件：${descriptionsIn(root)}",
            descriptionsIn(root).contains("返回"),
        )
    }

    @Test
    fun supportPage_noLongerUsesTheTopRightTextCross() {
        val a = supportActivity()
        val root = rootOf(a)
        val descs = descriptionsIn(root)
        assertFalse(
            "旧实现的「关闭支持页面」语义已被替换成统一的「返回」：$descs",
            descs.contains("关闭支持页面"),
        )
        val texts = textsIn(root)
        assertFalse(
            "不得再用文字「✕」当关闭按钮（全项目唯一这样干的就是这一页）：$texts",
            texts.contains("✕"),
        )
    }

    @Test
    fun supportPage_stillKeepsItsProductCopy() {
        // 反向守卫：改页头不能把"诚信付费"的承诺文案改掉或删掉。
        val a = supportActivity()
        val joined = textsIn(rootOf(a)).joinToString("\n")
        for (copy in listOf("诚信付费", "￥5", "暂时不支持，继续使用")) {
            assertTrue("页面必须保留原有文案「$copy」", joined.contains(copy))
        }
    }
}
