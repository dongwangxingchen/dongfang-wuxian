package me.rerere.common.dfwx

/**
 * [DFWX PATCH P31] AI 日志脱敏（SEC-004 / DFW-8）。
 *
 * 背景：AI 请求链路上有三处会把凭据写进日志——
 *  1. `RequestLoggingInterceptor`（自研请求日志，默认关、仅内存，但用户可手动打开）；
 *  2. `DataSourceModule` 里 okhttp 官方 `HttpLoggingInterceptor`（`Level.HEADERS`，**release 也常开**，落 Logcat）；
 *  3. 各 Provider 的 SSE `onEvent` 全量响应体 `Log.d`。
 * 而日志页有「一键复制」，用户随手把日志发出去就等于把 API Key 一起送人。
 *
 * 本对象只做**纯字符串处理**，不依赖 Android：唯一的目的是让脱敏规则可以被 JVM 测试逐条钉死，
 * 而不是散落在拦截器里靠人肉 review。所有消费点（拦截器、日志页、Provider 日志）都应走这里，
 * 避免"改了一处、漏了另一处"。
 *
 * 设计原则：宁可多打码，不可漏。**不确定是否是凭据时，一律按凭据处理**。
 */
object DfwxLogRedactor {

    /** 打码后的占位符。故意用固定长度，避免通过长度侧信道猜出原值。 */
    const val MASK = "██"

    /**
     * 需要整体打码的请求/响应头。命中即整值替换（不保留前缀，
     * 因为 `Bearer sk-xxx` 这类前缀本身就带有信息量）。
     */
    private val SENSITIVE_HEADERS = setOf(
        "authorization",
        "proxy-authorization",
        "cookie",
        "set-cookie",
        "x-api-key",
        "api-key",
        "apikey",
        "x-goog-api-key",
        "x-auth-token",
        "x-access-token",
        "x-amz-security-token",
        "openai-api-key",
        "anthropic-api-key",
        "x-api-token",
    )

    /**
     * URL 查询参数里需要打码的键。Google Gemini 把 key 放在 `?key=`，
     * 各家也常用 `access_token` / `api_key`，所以这里按"名字像凭据就打码"。
     */
    private val SENSITIVE_QUERY_KEYS = setOf(
        "key",
        "api_key",
        "apikey",
        "api-key",
        "access_token",
        "accesstoken",
        "token",
        "auth",
        "authorization",
        "password",
        "passwd",
        "secret",
        "client_secret",
        "signature",
        "sig",
        "credential",
        "session",
        "sessionid",
    )

    /** 值里出现这些前缀时，即便 header 名不认识也按凭据处理（防未来新增头漏网）。 */
    private val CREDENTIAL_VALUE_PREFIXES = listOf(
        "bearer ",
        "basic ",
        "sk-",
        "sk_",
        "sk-ant-",
        "aiza",
        "ya29.",
        "ghp_",
        "github_pat_",
    )

    /** 该 header 名是否需要打码。 */
    fun isSensitiveHeader(name: String?): Boolean {
        if (name.isNullOrBlank()) return false
        val lower = name.trim().lowercase()
        if (lower in SENSITIVE_HEADERS) return true
        // 兜底：任何以 token/secret/key 结尾的自定义头都按凭据处理
        return lower.endsWith("-token") || lower.endsWith("_token") ||
            lower.endsWith("-key") || lower.endsWith("_key") ||
            lower.endsWith("-secret") || lower.endsWith("_secret")
    }

    /** 该查询参数名是否需要打码。 */
    fun isSensitiveQueryKey(name: String?): Boolean {
        if (name.isNullOrBlank()) return false
        val lower = name.trim().lowercase()
        return lower in SENSITIVE_QUERY_KEYS ||
            lower.endsWith("_token") || lower.endsWith("token") ||
            lower.endsWith("_secret") || lower.endsWith("_key") ||
            lower.contains("password")
    }

    /**
     * 头值脱敏：名字命中就打码；名字没命中但**值本身长得像凭据**也打码。
     * 后者是关键——新增一个 `X-Foo-Token` 而不改本文件时，仍然不会泄露。
     */
    fun redactHeaderValue(name: String, value: String?): String {
        if (value == null) return ""
        if (isSensitiveHeader(name)) return MASK
        val lower = value.trim().lowercase()
        if (lower.startsWith("sk-") || lower.length >= 20 && CREDENTIAL_VALUE_PREFIXES.any { lower.startsWith(it) }) {
            return MASK
        }
        return value
    }

    /** 整张头表脱敏（保持原顺序，值可能为多值，用 ", " 连接）。 */
    fun redactHeaders(headers: Map<String, String>?): Map<String, String> {
        if (headers.isNullOrEmpty()) return emptyMap()
        val out = LinkedHashMap<String, String>(headers.size)
        for ((name, value) in headers) out[name] = redactHeaderValue(name, value)
        return out
    }

    /**
     * URL 脱敏：保留 scheme/host/path（定位故障需要知道打了哪个接口），
     * 只把**凭据型查询参数的值**换成 [MASK]；userinfo（`https://user:pass@host`）整段抹掉。
     *
     * 不用 `java.net.URI`：它对不合法/带中文的 URL 会抛异常，而日志路径必须绝不抛。
     * 手写解析在失败时可安全退化为"整体打码"。
     */
    fun redactUrl(url: String?): String {
        if (url.isNullOrBlank()) return ""
        return try {
            val queryIndex = url.indexOf('?')
            val hashIndex = url.indexOf('#')
            val pathEnd = listOf(queryIndex, hashIndex).filter { it >= 0 }.minOrNull() ?: url.length
            var head = url.substring(0, pathEnd)
            val tail = url.substring(pathEnd)

            // 抹掉 userinfo
            val schemeEnd = head.indexOf("://")
            if (schemeEnd >= 0) {
                val authorityStart = schemeEnd + 3
                val authorityEnd = head.indexOf('/', authorityStart).let { if (it < 0) head.length else it }
                val authority = head.substring(authorityStart, authorityEnd)
                val at = authority.lastIndexOf('@')
                if (at >= 0) {
                    head = head.substring(0, authorityStart) + authority.substring(at + 1) +
                        head.substring(authorityEnd)
                }
            }
            if (queryIndex < 0) return head + tail

            val fragment = if (hashIndex > queryIndex) url.substring(hashIndex) else ""
            val query = url.substring(queryIndex + 1, if (hashIndex > queryIndex) hashIndex else url.length)
            val redactedQuery = query.split('&').joinToString("&") { pair ->
                val eq = pair.indexOf('=')
                if (eq < 0) pair
                else {
                    val name = pair.substring(0, eq)
                    if (isSensitiveQueryKey(name)) "$name=$MASK" else pair
                }
            }
            head + "?" + redactedQuery + fragment
        } catch (t: Throwable) {
            // 解析失败时绝不原样透传：宁可丢掉整个 URL 也不冒泄露风险
            MASK
        }
    }

    /**
     * 请求/响应体脱敏：**默认完全不记录内容**，只给出一个足以定位故障的形状描述
     * （字节数 + 内容类型）。这是"最小有用信息"原则：调试 AI 请求几乎从不需要看完整 body，
     * 但经常需要知道"到底发出去了多少、是不是空"。
     *
     * 注意：本函数**故意不提供**"只打码敏感字段仍保留正文"的模式——
     * 聊天正文本身就是用户隐私，不该进日志。
     */
    fun describeBody(contentType: String?, byteCount: Long): String {
        if (byteCount <= 0) return "<empty>"
        val type = contentType?.substringBefore(';')?.trim().orEmpty()
        return if (type.isEmpty()) "<$byteCount bytes>" else "<$byteCount bytes, $type>"
    }

    /** SSE 事件体：只留事件类型与字节数，绝不留内容。 */
    fun describeSseEvent(type: String?, byteCount: Int): String {
        val t = type?.takeIf { it.isNotBlank() } ?: "message"
        return "$t <$byteCount bytes>"
    }
}
