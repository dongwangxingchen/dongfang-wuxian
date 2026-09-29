package me.rerere.rikkahub.dfwx

import me.rerere.common.android.LogEntry
import me.rerere.common.android.Logging
import me.rerere.rikkahub.data.ai.RequestLoggingInterceptor
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [DFWX] DFW-8 / SEC-004 端到端脱敏回归：**驱动真实的拦截器**，不是只测纯函数。
 *
 * 为什么必须这样做：脱敏规则写得再对，只要拦截器忘了调用，凭据照样进日志。
 * 本测试喂一个带 Authorization / Cookie / x-api-key、URL 带 `?key=`、body 带聊天正文的请求，
 * 然后断言真正落进 `Logging` 的那条记录里**找不到任何一个秘密**。
 */
class RequestLoggingRedactionTest {

    /**
     * `Interceptor.Chain` 有几十个成员，手写实现又臭又长。用动态代理只接管
     * `request()` / `proceed()`，其余方法返回该类型的默认值即可——这里只验证脱敏，
     * 不验证 okhttp 自身的链行为。
     */
    private fun fakeChain(request: Request): Interceptor.Chain {
        val handler = java.lang.reflect.InvocationHandler { _, method, args ->
            when (method.name) {
                "request" -> request
                "proceed" -> Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .header("Set-Cookie", "session=TOP_SECRET_COOKIE")
                    .body(
                        """{"choices":[{"delta":{"content":"用户聊天正文SECRET_REPLY"}}]}"""
                            .toResponseBody("application/json".toMediaType())
                    )
                    .build()
                "toString" -> "FakeChain"
                "hashCode" -> System.identityHashCode(request)
                "equals" -> false
                else -> defaultValue(method.returnType)
            }
        }
        return java.lang.reflect.Proxy.newProxyInstance(
            Interceptor.Chain::class.java.classLoader,
            arrayOf(Interceptor.Chain::class.java),
            handler,
        ) as Interceptor.Chain
    }

    private fun defaultValue(type: Class<*>): Any? = when (type) {
        java.lang.Boolean.TYPE -> false
        java.lang.Integer.TYPE -> 0
        java.lang.Long.TYPE -> 0L
        else -> null
    }

    @After
    fun cleanup() {
        Logging.setRequestLoggingEnabled(false)
    }

    @Test
    fun realInterceptor_neverPersistsCredentials() {
        Logging.setRequestLoggingEnabled(true)

        val request = Request.Builder()
            .url("https://api.example.com/v1/chat/completions?key=AIzaSyLEAKED_QUERY&alt=sse")
            .header("Authorization", "Bearer sk-LEAKED_AUTH")
            .header("Cookie", "session=LEAKED_COOKIE")
            .header("x-api-key", "LEAKED_API_KEY")
            .header("Content-Type", "application/json")
            .post("""{"messages":[{"role":"user","content":"我的私人问题 LEAKED_USER_TEXT"}]}"""
                .toRequestBody("application/json".toMediaType()))
            .build()

        RequestLoggingInterceptor().intercept(fakeChain(request))

        val entry = Logging.getRecentLogs().filterIsInstance<LogEntry.RequestLog>().firstOrNull()
        requireNotNull(entry) { "拦截器必须记录一条请求日志（否则测试没覆盖到东西）" }

        val dump = buildString {
            append(entry.url).append('\n')
            append(entry.requestBody).append('\n')
            entry.requestHeaders.forEach { (k, v) -> append(k).append('=').append(v).append('\n') }
            entry.responseHeaders.forEach { (k, v) -> append(k).append('=').append(v).append('\n') }
        }

        for (secret in listOf(
            "LEAKED_AUTH", "sk-LEAKED_AUTH", "LEAKED_COOKIE", "TOP_SECRET_COOKIE",
            "LEAKED_API_KEY", "AIzaSyLEAKED_QUERY", "LEAKED_USER_TEXT", "SECRET_REPLY",
        )) {
            assertFalse("日志里绝不能出现「$secret」：\n$dump", dump.contains(secret))
        }

        // 但必须留下定位故障所需的信息
        assertTrue("端点路径要保留：${entry.url}", entry.url.contains("/v1/chat/completions"))
        assertTrue("非凭据查询参数要保留：${entry.url}", entry.url.contains("alt=sse"))
        assertEquals(200, entry.responseCode)
        assertTrue("请求体只留形状，不留正文：${entry.requestBody}", entry.requestBody!!.startsWith("<"))
    }

    /**
     * 关闭日志时不得产生新记录（默认态）。
     *
     * 注意：`Logging` 是进程级 object，同一 JVM 里前一个测试写入的记录仍然在，
     * 所以这里断言的是**增量**而不是"列表为空"（否则测试之间会互相污染而假红）。
     */
    @Test
    fun disabledLogging_recordsNothing() {
        Logging.setRequestLoggingEnabled(false)
        val before = Logging.getRecentLogs().size
        val request = Request.Builder()
            .url("https://api.example.com/v1/chat")
            .header("Authorization", "Bearer sk-X")
            .post("{}".toRequestBody("application/json".toMediaType()))
            .build()
        RequestLoggingInterceptor().intercept(fakeChain(request))
        assertEquals(
            "日志关闭时不得写入任何新记录",
            before,
            Logging.getRecentLogs().size,
        )
    }

    /**
     * 关键防线：即便**调用方完全不做脱敏**、直接把原始凭据塞进 `LogEntry.RequestLog`，
     * 存储层也必须拦住。这样将来新增 Provider 忘了脱敏也不会泄露。
     */
    @Test
    fun storageLayer_redactsEvenWhenCallerForgot() {
        Logging.setRequestLoggingEnabled(true)
        Logging.logRequest(
            LogEntry.RequestLog(
                tag = "HTTP",
                url = "https://api.example.com/v1?key=RAW_QUERY_SECRET",
                method = "POST",
                requestHeaders = mapOf("Authorization" to "Bearer RAW_HEADER_SECRET"),
                requestBody = "{\"content\":\"RAW_BODY_SECRET\"}",
                responseHeaders = mapOf("Set-Cookie" to "RAW_COOKIE_SECRET"),
            )
        )

        val entry = Logging.getRecentLogs().filterIsInstance<LogEntry.RequestLog>().first()
        val dump = entry.url + entry.requestBody + entry.requestHeaders.values + entry.responseHeaders.values
        for (secret in listOf("RAW_QUERY_SECRET", "RAW_HEADER_SECRET", "RAW_BODY_SECRET", "RAW_COOKIE_SECRET")) {
            assertFalse("存储层必须拦住未脱敏的调用方，但「$secret」漏了：$dump", dump.contains(secret))
        }
    }
}
