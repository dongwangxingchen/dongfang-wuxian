package me.rerere.common.dfwx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [DFWX] DFW-8 / SEC-004：AI 日志脱敏规则的真值表。
 *
 * 这些规则是**安全边界**，不是显示偏好，所以必须逐条钉死。
 * 反例集比正例集更重要：这里刻意放入"名字不认识但值像凭据"的用例，
 * 防止将来新增 Provider 时悄悄绕过。
 */
class DfwxLogRedactorTest {

    // ---------- 头 ----------

    @Test
    fun redactsKnownCredentialHeaders() {
        for (name in listOf("Authorization", "authorization", "Proxy-Authorization", "Cookie", "Set-Cookie",
            "X-Api-Key", "x-api-key", "api-key", "X-Goog-Api-Key", "x-auth-token")) {
            assertEquals("头 $name 必须打码", DfwxLogRedactor.MASK, DfwxLogRedactor.redactHeaderValue(name, "whatever-value"))
        }
    }

    /** 关键防线：名字不在名单里，但值长得像凭据 → 仍然打码。 */
    @Test
    fun redactsUnknownHeaderWhoseValueLooksLikeCredential() {
        assertEquals(DfwxLogRedactor.MASK, DfwxLogRedactor.redactHeaderValue("X-Custom", "Bearer sk-abcdef123456"))
        assertEquals(DfwxLogRedactor.MASK, DfwxLogRedactor.redactHeaderValue("X-Whatever", "sk-ant-api03-xxxxx"))
        assertEquals(DfwxLogRedactor.MASK, DfwxLogRedactor.redactHeaderValue("X-Other", "AIzaSyA0000000000000000"))
    }

    /** 名字带 token/secret/key 后缀的自定义头也按凭据处理。 */
    @Test
    fun redactsCustomHeadersBySuffix() {
        assertTrue(DfwxLogRedactor.isSensitiveHeader("X-My-Token"))
        assertTrue(DfwxLogRedactor.isSensitiveHeader("X-My_Key"))
        assertTrue(DfwxLogRedactor.isSensitiveHeader("X-Foo-Secret"))
    }

    /** 不能误伤普通头：把 Content-Type 打码会让日志变得没用。 */
    @Test
    fun keepsHarmlessHeadersReadable() {
        assertEquals("application/json", DfwxLogRedactor.redactHeaderValue("Content-Type", "application/json"))
        assertEquals("gzip", DfwxLogRedactor.redactHeaderValue("Accept-Encoding", "gzip"))
        assertEquals("", DfwxLogRedactor.redactHeaderValue("X-Empty", null))
    }

    @Test
    fun redactHeaders_preservesOrderAndMasksOnlySensitive() {
        val out = DfwxLogRedactor.redactHeaders(
            linkedMapOf(
                "Content-Type" to "application/json",
                "Authorization" to "Bearer sk-x",
                "Accept" to "*/*",
            )
        )
        assertEquals(listOf("Content-Type", "Authorization", "Accept"), out.keys.toList())
        assertEquals("application/json", out["Content-Type"])
        assertEquals(DfwxLogRedactor.MASK, out["Authorization"])
        assertEquals("*/*", out["Accept"])
    }

    // ---------- URL ----------

    @Test
    fun redactsCredentialQueryParams_butKeepsEndpoint() {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0:streamGenerateContent?key=AIzaSySECRET&alt=sse"
        val redacted = DfwxLogRedactor.redactUrl(url)
        assertFalse("查询里的 key 值不得出现", redacted.contains("AIzaSySECRET"))
        assertTrue("端点路径必须保留，否则日志无法定位故障", redacted.contains("/v1beta/models/gemini-2.0"))
        assertTrue("非凭据参数应保留", redacted.contains("alt=sse"))
        assertTrue(redacted.contains("key=" + DfwxLogRedactor.MASK))
    }

    @Test
    fun redactsUserInfoInAuthority() {
        val redacted = DfwxLogRedactor.redactUrl("https://user:pa55w0rd@api.example.com/v1/chat")
        assertFalse("URL 里的 user:pass 不得保留", redacted.contains("pa55w0rd"))
        assertTrue(redacted.contains("api.example.com"))
    }

    @Test
    fun redactsTokenVariantsAcrossProviders() {
        for (url in listOf(
            "https://api.anthropic.com/v1/messages?access_token=abc123",
            "https://api.openai.com/v1/chat?api_key=sk-abc",
            "https://x.com/v1?token=t0ken",
            "https://x.com/v1?client_secret=sss",
            "https://x.com/v1?session=deadbeef",
        )) {
            val r = DfwxLogRedactor.redactUrl(url)
            assertFalse("凭据值不得出现在：$url → $r", r.contains("abc123") || r.contains("sk-abc") || r.contains("t0ken") || r.contains("sss") || r.contains("deadbeef"))
            assertTrue("必须保留主机名：$r", r.contains("https://"))
        }
    }

    /** URL 没有 query 时原样保留（且不得因为重写而改变路径）。 */
    @Test
    fun keepsQuerylessUrlIntact() {
        val url = "https://api.example.com/v1/models"
        assertEquals(url, DfwxLogRedactor.redactUrl(url))
    }

    /** 非法/畸形输入绝不抛异常。 */
    @Test
    fun malformedUrlNeverThrows() {
        for (bad in listOf(null, "", "   ", "ht tp://[bad", "://", "https://")) {
            DfwxLogRedactor.redactUrl(bad) // 抛异常即测试失败
        }
        assertEquals("", DfwxLogRedactor.redactUrl(null))
    }

    /** 带 fragment 的 URL：fragment 保留、query 里的凭据仍然要打码。 */
    @Test
    fun redactsQueryWhileKeepingFragment() {
        val r = DfwxLogRedactor.redactUrl("https://h/p?token=SECRET123#frag")
        assertFalse(r.contains("SECRET123"))
        assertTrue("fragment 应保留：$r", r.endsWith("#frag"))
        assertTrue(r.contains("token=" + DfwxLogRedactor.MASK))
    }

    // ---------- body / SSE ----------

    @Test
    fun bodyIsNeverRecorded_onlyShape() {
        val described = DfwxLogRedactor.describeBody("application/json; charset=utf-8", 1234)
        assertEquals("<1234 bytes, application/json>", described)
        assertFalse("描述里不得含任何正文", described.contains("chat"))
        assertEquals("<empty>", DfwxLogRedactor.describeBody("application/json", 0))
        assertEquals("<12 bytes>", DfwxLogRedactor.describeBody(null, 12))
    }

    @Test
    fun sseEventKeepsTypeButNotContent() {
        val s = DfwxLogRedactor.describeSseEvent("message", 250)
        assertEquals("message <250 bytes>", s)
        assertEquals("message <7 bytes>", DfwxLogRedactor.describeSseEvent(null, 7))
    }
}
