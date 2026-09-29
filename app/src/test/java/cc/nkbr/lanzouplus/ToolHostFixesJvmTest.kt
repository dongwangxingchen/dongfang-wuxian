package cc.nkbr.lanzouplus

import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import org.junit.Assert.assertEquals
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
 * [DFWX] DFW-11 工具箱缺陷回归：重复按钮 / 结果出口 / 秒表计圈。
 *
 * 三件事都是**结构可验证**的（不需要真机）：
 *  1. UUID 工具的主/次按钮整行重复（复核证实的真实缺陷）；
 *  2. 结果卡只有"复制"、"没有分享"（用户拿到结果后没有出口）；
 *  3. 秒表"计圈"逐条覆盖，只留最后一条。
 *
 * 断言刻意打在"按钮文案集合"和"计圈行数"这类可观察事实上，
 * 而不是去读源码文本——避免测试变成源码的复读机。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class ToolHostFixesJvmTest {

    companion object {
        @BeforeClass @JvmStatic fun silenceAutoImport() { MainActivity.LIBRARY_AUTO_IMPORT = false }
    }

    private fun texts(view: View, out: MutableList<String> = mutableListOf()): List<String> {
        if (view is TextView) view.text?.toString()?.let { if (it.isNotEmpty()) out.add(it) }
        if (view is ViewGroup) for (i in 0 until view.childCount) texts(view.getChildAt(i), out)
        return out
    }

    /** 收集所有带 contentDescription 的按钮标签。 */
    private fun clickableLabels(view: View, out: MutableList<String> = mutableListOf()): List<String> {
        if (view is TextView && view.isClickable) {
            view.contentDescription?.toString()?.let { if (it.isNotEmpty()) out.add(view.text?.toString() ?: "") }
        }
        if (view is ViewGroup) for (i in 0 until view.childCount) clickableLabels(view.getChildAt(i), out)
        return out
    }

    private fun openTool(activity: MainActivity, id: String) {
        activity.openTool(id)
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun uuidTool_hasNoDuplicateActionRow() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        shadowOf(Looper.getMainLooper()).idle()
        openTool(activity, "uuid")

        val labels = texts(activity.root)
        val regenerate = labels.count { it == "重新生成" }
        val copyAll = labels.count { it == "复制全部" }
        assertEquals("「重新生成」按钮只应出现一次（原本整行重复了两遍）：$labels", 1, regenerate)
        assertEquals("「复制全部」按钮只应出现一次（原本整行重复了两遍）：$labels", 1, copyAll)
    }

    @Test
    fun resultCard_offersBothCopyAndShare() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        shadowOf(Looper.getMainLooper()).idle()
        openTool(activity, "uuid")

        val labels = texts(activity.root)
        assertTrue("结果卡必须有复制入口：$labels", labels.contains("复制"))
        assertTrue("结果卡必须有分享入口（DFW-11 的核心诉求：结果要有出口）：$labels", labels.contains("分享"))
    }

    @Test
    fun stopwatch_lapsAreAccumulatedNotOverwritten() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        shadowOf(Looper.getMainLooper()).idle()
        openTool(activity, "stopwatch")

        val lapButton = findClickable(activity.root, "计圈")
        assertNotNull("秒表必须有「计圈」按钮", lapButton)
        assertNotNull("秒表必须有「开始/暂停」按钮", findClickable(activity.root, "开始/暂停"))

        // 连续计圈三次
        repeat(3) {
            lapButton!!.performClick()
            shadowOf(Looper.getMainLooper()).idle()
        }

        val textsNow = texts(activity.root)
        // 逐条计圈 = 单行文本；结果卡里那份是 join 起来的多行，单独统计，别混在一起数
        val lapRows = textsNow.filter { it.startsWith("第 ") && it.contains("圈") && !it.contains("\n") }
        assertEquals("三次计圈必须留下三行，不能互相覆盖（原实现只留最后一条）：$textsNow", 3, lapRows.size)
        assertTrue("应有累计摘要行：$textsNow", textsNow.any { it.startsWith("共 3 圈") })
        assertTrue("第一圈必须还在（逐条保留）：$textsNow", lapRows.any { it.startsWith("第 1 圈") })
        assertTrue(
            "结果卡里必须能拿到「全部计圈」（复制/分享出去的是完整记录）：$textsNow",
            textsNow.any { !it.contains("\n") && it.startsWith("第 1 圈") } &&
                textsNow.any { it.contains("\n") && it.startsWith("第 1 圈") && it.contains("第 3 圈") },
        )
    }

    @Test
    fun stopwatch_resetClearsLaps() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        shadowOf(Looper.getMainLooper()).idle()
        openTool(activity, "stopwatch")

        val lapButton = findClickable(activity.root, "计圈")!!
        repeat(2) { lapButton.performClick(); shadowOf(Looper.getMainLooper()).idle() }
        assertEquals(2, texts(activity.root).count { it.startsWith("第 ") && it.contains("圈") && !it.contains("\n") })

        findClickable(activity.root, "清零")!!.performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(
            "清零必须同时清掉计圈列表，否则旧圈会残留到下一轮",
            0,
            texts(activity.root).count { it.startsWith("第 ") && it.contains("圈") && !it.contains("\n") },
        )
    }

    private fun findClickable(view: View, label: String): View? {
        if (view is TextView && view.isClickable && view.text?.toString() == label) return view
        if (view is Button && view.text?.toString() == label) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) findClickable(view.getChildAt(i), label)?.let { return it }
        }
        return null
    }
}
