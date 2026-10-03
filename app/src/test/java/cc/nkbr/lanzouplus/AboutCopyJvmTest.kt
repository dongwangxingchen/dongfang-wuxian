package cc.nkbr.lanzouplus

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [DFW-132 2026-10-03] 「软件介绍」与「隐私政策」文案。
 *
 * ## 用户的要求（原话）
 * > 把我那个关于东方无限界面的那个。软件介绍和隐私政策重新优化一下。
 * > 可以说的专业一点，不过需要普通人能听懂。然后是对我们软件有帮助的。
 *
 * 用户在三个风格里选了 **🅐 说明书式**（清楚直白、先说能做什么）。
 *
 * ## 为什么文案要有测试
 * 文案不是排版，是**产品的一部分**：
 * - 写空了 / 留了占位符 → 用户翻到「关于」页看到半成品
 * - 隐私政策漏掉一条承诺 → 是**诚信问题**，不是错别字
 * - 写过头（"绝对安全""永久免费"）→ 给自己挖坑，也让用户不信
 *
 * 这三类都必须能被测试拦住。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-560dpi")
class AboutCopyJvmTest {

    companion object {
        @BeforeClass @JvmStatic fun silenceAutoImport() { MainActivity.LIBRARY_AUTO_IMPORT = false }

        private val INTRO = MainActivity.ABOUT_INTRO_TEXT
        private val POLICY = MainActivity.PRIVACY_POLICY_TEXT

        /** 占位符、未完成的痕迹。 */
        private val PLACEHOLDERS = listOf("TODO", "FIXME", "XXX", "待填", "待补", "占位", "{", "}", "lorem")
    }

    /** 两段文案都必须真的有内容 —— 空文案是最容易悄悄发生的"改坏"。 */
    @Test
    fun bothTextsAreActuallyWritten() {
        assertTrue("软件介绍不能为空", INTRO.trim().length >= 40)
        assertTrue("隐私政策不能为空", POLICY.trim().length >= 100)
    }

    /** 不许留占位符。 */
    @Test
    fun neitherTextLeavesPlaceholdersBehind() {
        for (p in PLACEHOLDERS) {
            assertFalse("软件介绍里残留占位符「$p」", INTRO.contains(p, ignoreCase = true))
            assertFalse("隐私政策里残留占位符「$p」", POLICY.contains(p, ignoreCase = true))
        }
    }

    /**
     * **隐私政策必须如实覆盖这几件事。**
     *
     * 每一条都对应 App 的一个真实行为，少写一条就等于少承诺一条：
     */
    @Test
    fun thePrivacyPolicyCoversEveryRealDataBehaviour() {
        val mustCover = mapOf(
            "不建账号体系" to listOf("账号"),
            "不收集身份信息" to listOf("手机号", "身份"),
            "不读通讯录/位置/相册" to listOf("通讯录"),
            "数据只存本机" to listOf("保存在你自己的手机上", "本机"),
            "什么时候联网" to listOf("联网"),
            "下载文件不经我们服务器" to listOf("不经过我们的服务器"),
            "权限按需申请" to listOf("权限"),
            "静默安装是可选的" to listOf("静默安装"),
        )
        for ((what, keywords) in mustCover) {
            assertTrue(
                "隐私政策必须写明「$what」—— 这是对用户的承诺，少一条就是少一条",
                keywords.any { POLICY.contains(it) },
            )
        }
    }

    /**
     * **不许承诺过头。**
     *
     * "绝对安全""永久免费""100% 无风险"这类话，用户第一次遇到反例就不再信任你，
     * 而且真的出事时是法律风险。**宁可少说，不可说满。**
     */
    @Test
    fun neitherTextOverPromises() {
        val banned = listOf("绝对安全", "绝对可靠", "永久免费", "100%", "百分百", "永不", "保证不会", "零风险")
        for (word in banned) {
            assertFalse("软件介绍里不许出现「$word」这类说满的话", INTRO.contains(word))
            assertFalse("隐私政策里不许出现「$word」这类说满的话", POLICY.contains(word))
        }
    }

    /**
     * **普通人能听懂**：不能出现法律黑话。
     *
     * 用户原话是"需要普通人能听懂"，所以这些词一个都不该有 ——
     * 有就说明写成了法务模板，而不是给人看的说明。
     */
    @Test
    fun thePrivacyPolicyAvoidsLegalese() {
        val jargon = listOf("兹", "特此", "本协议", "前述", "乙方", "甲方", "包括但不限于", "视为", "恕不")
        for (word in jargon) {
            assertFalse("隐私政策里不该出现法律黑话「$word」，用户看不懂", POLICY.contains(word))
        }
    }

    /** 说明书式：隐私政策要**分条**，不能是一整坨。 */
    @Test
    fun thePrivacyPolicyIsBrokenIntoSections() {
        val numbered = Regex("^[一二三四五六七八九十]、", RegexOption.MULTILINE).findAll(POLICY).count()
        assertTrue(
            "用户选的是「说明书式」，隐私政策必须分条（实测只有 $numbered 条）—— 一整坨没人读得下去",
            numbered >= 5,
        )
    }

    /** 软件介绍要分段/分块，不能是一整段。 */
    @Test
    fun theIntroIsAlsoStructured() {
        assertTrue("软件介绍必须分段，不能是一整坨", INTRO.count { it == '\n' } >= 4)
    }
}
