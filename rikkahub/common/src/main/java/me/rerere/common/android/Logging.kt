package me.rerere.common.android

import me.rerere.common.dfwx.DfwxLogRedactor

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

private const val MAX_RECENT_LOGS = 100

@Serializable
sealed class LogEntry {
    abstract val id: Uuid
    abstract val timestamp: Long
    abstract val tag: String

    @Serializable
    data class TextLog(
        override val id: Uuid = Uuid.random(),
        override val timestamp: Long = System.currentTimeMillis(),
        override val tag: String,
        val message: String
    ) : LogEntry()

    @Serializable
    data class RequestLog(
        override val id: Uuid = Uuid.random(),
        override val timestamp: Long = System.currentTimeMillis(),
        override val tag: String,
        val url: String,
        val method: String,
        val requestHeaders: Map<String, String> = emptyMap(),
        val requestBody: String? = null,
        val responseCode: Int? = null,
        val responseHeaders: Map<String, String> = emptyMap(),
        val durationMs: Long? = null,
        val error: String? = null
    ) : LogEntry()
}

object Logging {
    private val recentLogs = arrayListOf<LogEntry>()
    @Volatile
    private var requestLoggingEnabled = false

    fun log(tag: String, message: String) {
        addLog(LogEntry.TextLog(tag = tag, message = message))
    }

    /**
     * [DFWX PATCH P31] SEC-004（DFW-8）：请求日志**在入库这一层**统一脱敏。
     *
     * 为什么放这里而不是只放在调用方：这是唯一的存储入口。调用方再多、将来新增 Provider 忘了脱敏，
     * 也绕不过这一道。安全边界应该做成结构性的，而不是靠"每个调用点都记得调用脱敏函数"。
     *
     * 脱敏覆盖：URL 里的凭据型查询参数与 userinfo、Authorization/Cookie/x-api-key 等头、
     * 以及请求体（只保留字节数与内容类型，绝不保留正文——聊天正文属用户隐私）。
     */
    fun logRequest(entry: LogEntry.RequestLog) {
        if (!requestLoggingEnabled) return
        addLog(redact(entry))
    }

    private fun redact(entry: LogEntry.RequestLog): LogEntry.RequestLog = entry.copy(
        url = DfwxLogRedactor.redactUrl(entry.url),
        requestHeaders = DfwxLogRedactor.redactHeaders(entry.requestHeaders),
        responseHeaders = DfwxLogRedactor.redactHeaders(entry.responseHeaders),
        requestBody = entry.requestBody?.let { body ->
            // 拦截器已经写成形状串（"<N bytes, type>"）；这里兜住直接塞正文的调用方。
            if (body.startsWith("<")) body
            else DfwxLogRedactor.describeBody(null, body.toByteArray(Charsets.UTF_8).size.toLong())
        },
    )

    fun isRequestLoggingEnabled(): Boolean = requestLoggingEnabled

    fun setRequestLoggingEnabled(enabled: Boolean) {
        requestLoggingEnabled = enabled
    }

    private fun addLog(entry: LogEntry) {
        synchronized(recentLogs) {
            recentLogs.add(0, entry)
            if (recentLogs.size > MAX_RECENT_LOGS) {
                recentLogs.removeLastOrNull()
            }
        }
    }

    fun getRecentLogs(): List<LogEntry> {
        synchronized(recentLogs) {
            return recentLogs.toList()
        }
    }

    fun getTextLogs(): List<LogEntry.TextLog> {
        synchronized(recentLogs) {
            return recentLogs.filterIsInstance<LogEntry.TextLog>()
        }
    }

    fun getRequestLogs(): List<LogEntry.RequestLog> {
        synchronized(recentLogs) {
            return recentLogs.filterIsInstance<LogEntry.RequestLog>()
        }
    }

    fun clear() {
        synchronized(recentLogs) {
            recentLogs.clear()
        }
    }
}
