package me.rerere.rikkahub.data.ai

import me.rerere.common.android.LogEntry
import me.rerere.common.android.Logging
import me.rerere.common.dfwx.DfwxLogRedactor
import okhttp3.Interceptor
import okhttp3.Response
import okio.Buffer

class RequestLoggingInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        if (!Logging.isRequestLoggingEnabled()) {
            return chain.proceed(chain.request())
        }

        val request = chain.request()
        val startTime = System.currentTimeMillis()

        // [DFWX PATCH P31] SEC-004：一律走统一脱敏出口（DfwxLogRedactor），
        // 不再记录原始 header / URL 凭据 / 请求正文。正文只留"形状"（字节数+类型），
        // 因为聊天正文本身是用户隐私，调试 AI 请求几乎从不需要看全文。
        val requestHeaders = DfwxLogRedactor.redactHeaders(request.headers.toMap())
        val requestBody = request.body?.let { body ->
            val contentType = body.contentType()?.toString()
            // 注意：这里仍然读取 Buffer 以获得准确字节数（流式 body 无法预先知道长度），
            // 但读到的内容**不会**进入日志，只用于计数。
            val buffer = Buffer()
            body.writeTo(buffer)
            DfwxLogRedactor.describeBody(contentType, buffer.size)
        }

        val response: Response
        var error: String? = null

        try {
            response = chain.proceed(request)
        } catch (e: Exception) {
            error = e.message
            Logging.logRequest(
                LogEntry.RequestLog(
                    tag = "HTTP",
                    url = DfwxLogRedactor.redactUrl(request.url.toString()),
                    method = request.method,
                    requestHeaders = requestHeaders,
                    requestBody = requestBody,
                    error = error
                )
            )
            throw e
        }

        val durationMs = System.currentTimeMillis() - startTime
        val responseHeaders = response.headers.toMap() // 统一在下方经 DfwxLogRedactor.redactHeaders 出口

        Logging.logRequest(
            LogEntry.RequestLog(
                tag = "HTTP",
                url = DfwxLogRedactor.redactUrl(request.url.toString()),
                method = request.method,
                requestHeaders = requestHeaders,
                requestBody = requestBody,
                responseCode = response.code,
                responseHeaders = DfwxLogRedactor.redactHeaders(responseHeaders),
                durationMs = durationMs,
                error = error
            )
        )

        return response
    }

    /** 原样取值；脱敏统一由 [DfwxLogRedactor] 负责，避免这里另立一套规则导致漂移。 */
    private fun okhttp3.Headers.toMap(): Map<String, String> {
        return names().associateWith { name -> get(name) ?: "" }
    }
}
