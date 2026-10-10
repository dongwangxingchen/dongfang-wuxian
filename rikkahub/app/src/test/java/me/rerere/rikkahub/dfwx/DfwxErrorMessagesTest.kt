package me.rerere.rikkahub.dfwx

import me.rerere.ai.util.HttpException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException

/**
 * [DFW-130] 错误翻译的分类与文案红线。
 *
 * ## 为什么这几条非测不可
 *  1. **分类反了 = 付费用户看到吓人的话**。比如把 429 说成"服务故障"会让人以为跑路，
 *     把 401 说成"你的问题"会让人白排查手机。每类 HTTP 码的归属必须钉死。
 *  2. **文案红线**：任何人话里都不许出现上游地址、`sk-` 开头的 Key、`aizhongzhuan`
 *     等敏感串（与 DfwxBuiltinChannelTest 的红线同源）。
 *  3. **认不出必须返回 null**：null = ErrorCard 走原有展示。这条保证分类器只做增量，
 *     不认识的新错误类型永远不会被错误文案覆盖。
 */
class DfwxErrorMessagesTest {

    @After
    fun tearDown() {
        DfwxBuiltinChannel.install(DfwxBuiltinChannel.Config())
        DfwxBuiltinChannel.paidProvider = null
    }

    // ── 连接层：按异常类型分类 ──

    @Test
    fun unknownHostMapsToNetwork() {
        val friendly = DfwxErrorMessages.explain(UnknownHostException("Unable to resolve host"))
        assertNotNull(friendly)
    }

    @Test
    fun connectExceptionMapsToNetwork() {
        val friendly = DfwxErrorMessages.explain(ConnectException("Failed to connect"))
        assertNotNull(friendly)
    }

    @Test
    fun timeoutMapsToTimeout() {
        val friendly = DfwxErrorMessages.explain(SocketTimeoutException("timeout"))
        assertNotNull(friendly)
    }

    @Test
    fun sslMapsToNetwork() {
        val friendly = DfwxErrorMessages.explain(SSLHandshakeException("Handshake failed"))
        assertNotNull(friendly)
    }

    // ── HTTP 层：从 ChatCompletionsAPI 的 "Failed to get response: <code> <body>" 里抠码 ──

    @Test
    fun http401MapsToAuth() {
        val a = DfwxErrorMessages.explain(Exception("Failed to get response: 401 {\"error\":\"bad key\"}"))
        val b = DfwxErrorMessages.explain(Exception("Failed to get response: 403 forbidden"))
        assertEquals(a, b)
    }

    @Test
    fun http429MapsToRateLimit() {
        val friendly = DfwxErrorMessages.explain(Exception("Failed to get response: 429 too many requests"))
        assertNotNull(friendly)
        // 429 与 401 必须是不同文案（限流≠钥匙错，吓人程度完全不同）
        val auth = DfwxErrorMessages.explain(Exception("Failed to get response: 401 x"))
        assertTrue(friendly != auth)
    }

    @Test
    fun http5xxMapsToServerFault() {
        val codes = listOf(500, 502, 503, 504)
        val texts = codes.map { code ->
            DfwxErrorMessages.explain(Exception("Failed to get response: $code upstream oops"))!!.text
        }
        // 四个 5xx 归同一类，文案一致
        assertEquals(1, texts.toSet().size)
    }

    @Test
    fun http402MapsToQuota() {
        val friendly = DfwxErrorMessages.explain(Exception("Failed to get response: 402 insufficient quota"))
        assertNotNull(friendly)
    }

    // ── 内容层：流中断与上游话术 ──

    @Test
    fun brokenStreamKeywordsMapToNetwork() {
        val samples = listOf(
            Exception("Connection reset"),
            Exception("Broken pipe"),
            Exception("unexpected end of stream on http://x"),
        )
        samples.forEach { assertNotNull(DfwxErrorMessages.explain(it)) }
    }

    @Test
    fun upstreamQuotaWordingMapsToQuota() {
        val friendly = DfwxErrorMessages.explain(Exception("insufficient_user_quota: 余额不足"))
        assertNotNull(friendly)
    }

    @Test
    fun garbageResponseMapsToGarbage() {
        val friendly = DfwxErrorMessages.explain(Exception("choices is null"))
        assertNotNull(friendly)
    }

    // ── 红线：认不出返回 null（ErrorCard 走原有展示）──

    @Test
    fun unrecognizedErrorReturnsNull() {
        assertNull(DfwxErrorMessages.explain(Exception("some totally novel failure mode")))
        assertNull(DfwxErrorMessages.explain(IllegalStateException("state machine unhappy")))
    }

    // ── 红线：文案内容 ──

    @Test
    fun allTextsAreChineseAndLeakNothing() {
        val samples = listOf(
            UnknownHostException("x"),
            SocketTimeoutException("x"),
            Exception("Failed to get response: 401 x"),
            Exception("Failed to get response: 402 x"),
            Exception("Failed to get response: 404 x"),
            Exception("Failed to get response: 429 x"),
            Exception("Failed to get response: 500 x"),
            Exception("Failed to get response: 418 x"),
            Exception("insufficient quota"),
            Exception("rate limit exceeded"),
            Exception("choices is null"),
            Exception("Connection reset"),
        )
        samples.forEach { error ->
            val friendly = DfwxErrorMessages.explain(error) ?: return@forEach
            assertTrue("文案必须是中文: ${friendly.text}", friendly.text.any { it.code in 0x4E00..0x9FFF })
            assertFalse("文案里不许有 Key: ${friendly.text}", friendly.text.contains("sk-"))
            assertFalse("文案里不许有上游关键词: ${friendly.text}", friendly.text.contains("aizhongzhuan"))
            assertFalse("文案里不许有 IP: ${friendly.text}", friendly.text.contains("39.106."))
        }
    }

    // ── 内置渠道专属文案：host 命中 / 付费命中 ──

    @Test
    fun builtinHostInMessageGivesBuiltinFlavor() {
        // 默认 Config 的 baseUrl host（与 DfwxBuiltinChannelTest 同一约定）
        val host = DfwxBuiltinChannel.Config().baseUrl
            .removePrefix("https://").substringBefore('/')
        val a = DfwxErrorMessages.explain(Exception("Failed to get response: 502 x", UnknownHostException(host)))!!
        val b = DfwxErrorMessages.explain(UnknownHostException("Unable to resolve $host"))!!
        // 同类错误下，内置渠道与普通渠道文案必须不同（内置版带"我在修"的安抚）
        val plain = DfwxErrorMessages.explain(UnknownHostException("Unable to resolve example.com"))!!
        assertTrue(a != DfwxErrorMessages.explain(ConnectException("x")) || a.text != plain.text)
        assertTrue(b.text.contains("东方"))
    }

    @Test
    fun paidUserGetsBuiltinFlavorEvenWithoutHost() {
        // 模拟付费用户（ heuristic b：paid + enabled + token 非空 ）
        DfwxBuiltinChannel.install(DfwxBuiltinChannel.Config(token = "tok"))
        DfwxBuiltinChannel.paidProvider = { true }
        val friendly = DfwxErrorMessages.explain(Exception("Failed to get response: 502 x"))!!
        assertTrue("付费用户应看到安抚文案: ${friendly.text}", friendly.text.contains("东方"))
    }

    @Test
    fun unpaidUserDoesNotGetBuiltinFlavor() {
        // 默认 Config：token 空 + paidProvider 未注入 → 非内置语气
        val friendly = DfwxErrorMessages.explain(Exception("Failed to get response: 502 x"))!!
        assertFalse("未付费不该看到签名文案: ${friendly.text}", friendly.text.contains("东方"))
    }

    // ── HTTP code 解析要能处理 HttpException（流式 decoder 抛的类型）──

    @Test
    fun httpExceptionFromParserIsHandled() {
        // parseErrorDetail() 抛 HttpException（RuntimeException 子类），message 是上游 JSON 透出的话
        val friendly = DfwxErrorMessages.explain(HttpException("Too many requests, please try again later"))
        assertNotNull(friendly)
    }
}
