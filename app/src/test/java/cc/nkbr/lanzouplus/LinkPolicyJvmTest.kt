package cc.nkbr.lanzouplus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [DFWX] DFW-29（ARCH-001）链接策略直测——**抽取之前这些规则根本无法这样测**。
 *
 * ## 这张测试的意义不只是"覆盖了代码"
 * `LinkPolicy` 里的规则是从 2200+ 行的 `MainActivity` 里搬出来的。
 * 搬之前，想验证「`http://wwc.lanzouw.com/x` 算不算蓝奏链接」必须**启动整个 Activity**
 * （Robolectric 全套），既慢又只能间接断言；搬之后这里几毫秒就能逐条钉死。
 *
 * 卡片（ARCH-001）要求"先补测试，再移动实现，最后删旧入口"，
 * 并且"新入口和旧入口行为一致或差异有决策记录"。本轮的做法是：
 * 实现搬到 `LinkPolicy`，`MainActivity` 保留**同名转发方法**，所以外部调用点一行未改、
 * 可独立回退（还原那几个 shim 即可）。
 *
 * ## 行为等价性说明（唯一的两处改写，均在 LinkPolicy 注释中记录）
 * 1. `Uri.parse(x).getHost()` → `hostOf(x)`：纯字符串取 host，
 *    因为 `android.net.Uri` 在无 Android 运行时不可用，而这里只需要 host；
 * 2. `preferredLanzouUrl` 原本 `Uri.parse(value).toString()`（解析再序列化），
 *    对已是字符串的 URL 等价于原值，故直接返回。
 */
class LinkPolicyJvmTest {

    // ---------- hostOf：纯字符串取 host（替代 Uri.parse） ----------

    @Test
    fun hostOf_handlesCommonUrlShapes() {
        assertEquals("wwc.lanzouw.com", LinkPolicy.hostOf("https://wwc.lanzouw.com/iAbc"))
        assertEquals("wwc.lanzouw.com", LinkPolicy.hostOf("http://wwc.lanzouw.com/iAbc?pwd=1"))
        assertEquals("wwc.lanzouw.com", LinkPolicy.hostOf("https://WWC.LanzouW.com"))
        assertEquals("example.com", LinkPolicy.hostOf("https://example.com:8443/path"))
        assertEquals("example.com", LinkPolicy.hostOf("https://user:pass@example.com/x"))
        assertEquals("", LinkPolicy.hostOf(""))
        assertEquals("", LinkPolicy.hostOf(null))
    }

    // ---------- normalizedWebUrl：补 https 前缀 ----------

    @Test
    fun normalizedWebUrl_addsHttpsWhenMissing() {
        assertEquals("https://wwc.lanzouw.com/x", LinkPolicy.normalizedWebUrl("wwc.lanzouw.com/x"))
        assertEquals("https://wwc.lanzouw.com/x", LinkPolicy.normalizedWebUrl("  wwc.lanzouw.com/x  "))
    }

    @Test
    fun normalizedWebUrl_keepsExistingScheme() {
        assertEquals("http://a.com/x", LinkPolicy.normalizedWebUrl("http://a.com/x"))
        assertEquals("https://a.com/x", LinkPolicy.normalizedWebUrl("https://a.com/x"))
    }

    /**
     * 尾部 **ASCII** 句读会被剔除。
     *
     * 注意：原实现的字符集是 `.,;:!?)]}`——**不含中文句号「。」**。
     * 我最初按直觉写了中文句号的用例，结果红了；核对源码后确认这是**原有行为**，
     * 本卡要求"新入口与旧入口行为一致"，所以**不去"顺手改好"它**，
     * 而是把真实行为如实钉在这里（并记下这个已知局限，避免以后误以为是回归）。
     */
    @Test
    fun normalizedWebUrl_trimsTrailingAsciiPunctuation() {
        assertEquals("https://a.com/x", LinkPolicy.normalizedWebUrl("a.com/x,"))
        assertEquals("https://a.com/x", LinkPolicy.normalizedWebUrl("a.com/x)"))
        assertEquals("https://a.com/x", LinkPolicy.normalizedWebUrl("a.com/x!"))
    }

    /** 已知局限（原有行为，非本次引入）：中文句号不会被剔除。 */
    @Test
    fun normalizedWebUrl_knownLimitation_chineseFullStopIsNotTrimmed() {
        assertEquals(
            "这是原有行为（字符集为 ASCII 句读）；如要改成剔除中文句号，需单独评估影响",
            "https://a.com/x。",
            LinkPolicy.normalizedWebUrl("a.com/x。"),
        )
    }

    @Test
    fun normalizedWebUrl_emptyStaysEmpty() {
        assertEquals("", LinkPolicy.normalizedWebUrl(""))
        assertEquals("", LinkPolicy.normalizedWebUrl(null))
        assertEquals("", LinkPolicy.normalizedWebUrl("   "))
    }

    // ---------- 蓝奏域名识别 ----------

    @Test
    fun recognisesRealLanzouHosts() {
        for (host in listOf(
            "wwc.lanzouw.com", "wwc.lanzoux.com", "oreojiang.lanzout.com",
            "www.lanzoup.com", "lanzouo.com", "lanzouz.com", "lanzov.com",
        )) {
            assertTrue("$host 应识别为蓝奏域名", LinkPolicy.isRealLanzouHost(host))
        }
    }

    /** 本产品自己的发布域名（.nkbr.cc 上的 lanzou* 主机）**不算**蓝奏云真实域名。 */
    @Test
    fun rejectsProductReleaseHosts() {
        assertFalse("产品发布域名不得被当成蓝奏云", LinkPolicy.isRealLanzouHost("lanzouplus.nkbr.cc"))
        assertTrue("但要能被 isProductReleaseHost 认出来", LinkPolicy.isProductReleaseHost("lanzouplus.nkbr.cc"))
    }

    @Test
    fun rejectsNonLanzouHosts() {
        for (host in listOf("example.com", "lanzo.com", "lanzou.com.evil.com", "notlanzou.com")) {
            assertFalse("$host 不是蓝奏域名", LinkPolicy.isRealLanzouHost(host))
        }
    }

    /** 大小写与结尾点号都要容错（用户粘贴的链接形态很杂）。 */
    @Test
    fun lanzouHost_checkIsCaseInsensitiveAndToleratesTrailingDots() {
        assertTrue(LinkPolicy.isRealLanzouHost("WWC.LANZOUW.COM"))
        assertTrue(LinkPolicy.isRealLanzouHost("wwc.lanzouw.com."))
    }

    @Test
    fun isLanzouUrl_worksOnFullUrls() {
        assertTrue(LinkPolicy.isLanzouUrl("https://wwc.lanzouw.com/iAbc"))
        assertTrue("裸域名也应被补前缀后识别", LinkPolicy.isLanzouUrl("wwc.lanzouw.com/iAbc"))
        assertFalse(LinkPolicy.isLanzouUrl("https://example.com/x"))
        assertFalse(LinkPolicy.isLanzouUrl(""))
    }

    // ---------- 分享输入面收窄（DFW-12） ----------

    @Test
    fun shareableWebUrl_acceptsOnlyHttpAndHttps() {
        assertTrue(LinkPolicy.isShareableWebUrl("https://wwc.lanzouw.com/x"))
        assertTrue(LinkPolicy.isShareableWebUrl("http://wwc.lanzouw.com/x"))
        for (bad in listOf(
            "file:///etc/passwd", "content://com.other/secret", "javascript:alert(1)",
            "intent://evil#Intent;scheme=x;end", "market://details?id=x", "", "   ",
        )) {
            assertFalse("必须拒绝：$bad", LinkPolicy.isShareableWebUrl(bad))
        }
    }

    @Test
    fun shareableWebUrl_rejectsUrlWithoutHost() {
        assertFalse(LinkPolicy.isShareableWebUrl("https://"))
        assertFalse(LinkPolicy.isShareableWebUrl("http://"))
    }

    // ---------- 从聊天混合文本里取链接 ----------

    @Test
    fun extractSharedUrl_picksLinkFromChatMessage() {
        val text = "分享一个软件\nhttps://wwc.lanzouw.com/iABC123\n提取码：8x2k"
        assertEquals("https://wwc.lanzouw.com/iABC123", LinkPolicy.extractSharedUrl(text))
    }

    @Test
    fun extractSharedUrl_takesFirstWhenMultiple() {
        val url = LinkPolicy.extractSharedUrl("先看 https://example.com/a 然后 https://wwc.lanzouw.com/iXYZ")
        assertEquals("https://example.com/a", url)
    }

    /** 没有链接时返回原文，交给上层补 https（用户可能只发了裸域名）。 */
    @Test
    fun extractSharedUrl_fallsBackToPlainText() {
        assertEquals("wwc.lanzouw.com/iABC", LinkPolicy.extractSharedUrl("wwc.lanzouw.com/iABC"))
    }

    @Test
    fun extractSharedUrl_handlesEmptyInput() {
        assertEquals("", LinkPolicy.extractSharedUrl(""))
        assertEquals("", LinkPolicy.extractSharedUrl(null))
        assertEquals("", LinkPolicy.extractSharedUrl("   "))
    }

    // ---------- webUrlEnd ----------

    @Test
    fun webUrlEnd_stripsTrailingAsciiPunctuation() {
        val s = "https://a.com/x)."
        // 逐步剔除 ')' 与 '.'，剩 "https://a.com/x"（15 个字符）
        assertEquals(15, LinkPolicy.webUrlEnd(s, 0, s.length))
        assertEquals("https://a.com/x", s.substring(0, LinkPolicy.webUrlEnd(s, 0, s.length)))
    }
}
