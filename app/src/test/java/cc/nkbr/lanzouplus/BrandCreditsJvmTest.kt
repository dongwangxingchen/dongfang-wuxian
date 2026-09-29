package cc.nkbr.lanzouplus

import android.os.Looper
import android.view.View
import android.view.ViewGroup
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
import java.io.File

/**
 * [DFWX] DFW-28（BRAND-002）品牌信任页回归：**已存在的内容不得被删**。
 *
 * 这张卡的性质特殊——它的目标（关于页/致谢页）**已经实现**，本轮是"核对与守住"。
 * 归档 T4 明确要求"必须逐项核对，**不能为版面清爽删人删感谢**"。
 * 所以这里把每一项都钉成断言：以后谁为了"页面清爽"删掉某个署名，
 * 测试会立刻红，而不是等到用户发现"感谢页少了人"。
 *
 * 关键的 AGPL 义务：RikkaHub 署名与许可证声明属于**法律义务**，不只是排版内容。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class BrandCreditsJvmTest {

    companion object {
        @BeforeClass @JvmStatic fun silenceAutoImport() { MainActivity.LIBRARY_AUTO_IMPORT = false }
    }

    /** 归档 T4 要求感谢语一字不改。 */
    private val requiredThanks =
        "感谢各位朋友的支持与帮助，因为有你们，我才可以更好的将我的想法实现出来。并帮助更多的人，有你们在，吾道不孤。"

    /** 9 项历史参考必须全部在列。 */
    private val requiredReferences = listOf(
        "RikkaHub", "LanzouPlus", "AndroidVeil", "Material Symbols",
        "Lottie Android", "SmoothBottomBar", "langchain4j", "openai-java", "UX Planet",
    )

    private fun textsOf(view: View, out: MutableList<String> = mutableListOf()): List<String> {
        if (view is TextView) view.text?.toString()?.let { if (it.isNotEmpty()) out.add(it) }
        if (view is ViewGroup) for (i in 0 until view.childCount) textsOf(view.getChildAt(i), out)
        return out
    }

    private fun openAbout(): MainActivity {
        val a = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        shadowOf(Looper.getMainLooper()).idle()
        a.showAboutPage()
        shadowOf(Looper.getMainLooper()).idle()
        return a
    }

    private fun openAck(): MainActivity {
        val a = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        shadowOf(Looper.getMainLooper()).idle()
        a.showAcknowledgementsPage()
        shadowOf(Looper.getMainLooper()).idle()
        return a
    }

    // ---------- 三人署名 ----------

    @Test
    fun aboutPage_creditsAllThreePeople() {
        val texts = textsOf(openAbout().root)
        for (name in listOf("东方", "晨宇", "晚意借北风")) {
            assertTrue("关于页必须保留「$name」的署名（归档 T4：不能为版面清爽删人）：$texts", texts.contains(name))
        }
        assertTrue("必须标明制作者", texts.contains("制作者"))
        assertTrue("必须标明辅助开发者", texts.contains("辅助开发者"))
        assertTrue("必须标明嗷呜小屋作者", texts.contains("嗷呜小屋作者"))
    }

    /** 每位署名者的说明文字也要在（不是只留名字）。 */
    @Test
    fun aboutPage_keepsEachPersonsDescription() {
        val all = textsOf(openAbout().root).joinToString("\n")
        for (keyword in listOf("全部设计与开发", "开发环境", "嗷呜小屋软件库资源")) {
            assertTrue("署名说明必须保留「$keyword」", all.contains(keyword))
        }
    }

    // ---------- 感谢语（逐字） ----------

    @Test
    fun aboutPage_keepsThanksVerbatim() {
        val texts = textsOf(openAbout().root)
        assertEquals(
            "感谢语必须一字不改（归档 T4 明确要求）",
            true,
            texts.any { it == requiredThanks },
        )
    }

    // ---------- 9 项参考 ----------

    @Test
    fun acknowledgementsPage_listsAllNineReferences() {
        val texts = textsOf(openAck().root)
        val joined = texts.joinToString("\n")
        for (ref in requiredReferences) {
            assertTrue("致谢页必须保留参考项目「$ref」：$texts", joined.contains(ref))
        }
    }

    /** AGPL-3.0 义务：RikkaHub 的许可与用途说明必须在页面上可见（不只是 README 里）。 */
    @Test
    fun acknowledgementsPage_disclosesAgplObligation() {
        val joined = textsOf(openAck().root).joinToString("\n")
        assertTrue("RikkaHub 条目必须标注 AGPL-3.0（许可证义务）", joined.contains("AGPL-3.0"))
        assertTrue("必须说明 RikkaHub 是 AI 对话核心参考", joined.contains("AI 对话核心参考"))
    }

    // ---------- 链接可点（每项参考都挂着可打开的项目页） ----------

    @Test
    fun acknowledgementsPage_everyRowIsClickableToProjectPage() {
        val root = openAck().root
        val clickable = mutableListOf<String>()
        fun walk(v: View) {
            val desc = v.contentDescription?.toString()
            if (desc != null && desc.endsWith("，打开项目页")) clickable.add(desc)
            if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
        }
        walk(root)
        assertEquals(
            "9 项参考每一行都必须可点开项目页（否则用户无法追溯来源）：$clickable",
            requiredReferences.size,
            clickable.size,
        )
    }

    // ---------- 隐私政策：不得出现"不收集隐私"这类未核对的绝对说法 ----------

    @Test
    fun privacyPolicy_avoidsUnverifiedAbsoluteClaims() {
        val texts = textsOf(openAbout().root).joinToString("\n")
        assertTrue("关于页必须有隐私政策", texts.contains("隐私政策"))
        // 卡片红线："写'不收集隐私'这类绝对说法前必须先核对数据流"
        for (absolute in listOf("绝对不收集", "不收集任何隐私", "完全匿名")) {
            assertTrue("隐私政策不得出现未核对的绝对说法「$absolute」", !texts.contains(absolute))
        }
        // 应当如实说明"数据保存在本机"与"访问哪些外部服务"
        assertTrue("隐私政策应说明数据保存在本机", texts.contains("保存在本机"))
        assertTrue("隐私政策应如实列出会访问的外部服务", texts.contains("更新检查"))
    }

    // ---------- 页面注册与返回 ----------

    @Test
    fun pages_areRegisteredWithBackAction() {
        val about = openAbout()
        assertEquals("关于页 pageKind 应为 7", 7, about.pageKind)
        assertNotNull("关于页必须注册系统返回", about.systemBackAction)

        val ack = openAck()
        assertEquals("致谢页 pageKind 应为 8", 8, ack.pageKind)
        assertNotNull("致谢页必须注册系统返回", ack.systemBackAction)
    }

    @Test
    fun aboutPage_showsCurrentVersion() {
        val joined = textsOf(openAbout().root).joinToString("\n")
        assertTrue(
            "关于页必须显示当前版本号（便于用户报障时提供）：期望 ${BuildConfig.VERSION_NAME}",
            joined.contains(BuildConfig.VERSION_NAME),
        )
    }

    /** 头像资源必须存在（三个人都有图，不能出现空白头像）。 */
    @Test
    fun avatars_existForAllThreePeople() {
        val resDir = File(
            (System.getProperty("user.dir") ?: ".").let { dir ->
                var d = File(dir).absoluteFile
                while (!File(d, "src/main/res").isDirectory && d.parentFile != null) d = d.parentFile
                d
            },
            "src/main/res",
        )
        for (avatar in listOf("avatar_dongfang", "avatar_chenyu", "avatar_wanyi")) {
            val found = resDir.walkTopDown().any { it.isFile && it.name.startsWith(avatar) }
            assertTrue("头像资源 $avatar 必须存在（否则署名行是空白头像）", found)
        }
    }
}
