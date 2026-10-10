package me.rerere.rikkahub.dfwx

/**
 * [DFW-130] 把底层异常翻译成「东方语气」的人话。
 *
 * ## 为什么要这个文件
 * 渠道炸了的时候，付费用户看到的是 `Failed to get response: 401 {...}` 这种英文机器话，
 * 第一反应是「付了钱用不了 = 作者跑路了」。DeepSeek 官方当年一句干巴巴的
 * 「服务器繁忙，请稍后再试」被全网吐槽，就是前车之鉴。
 * 文案目标（用户原话）："给出像我一样说话的语气的提示，不至于让他们觉得付费之后我跑路了"。
 *
 * ## 设计约束
 *  1. **纯函数、零 Android 依赖**：输入 Throwable，输出可空文案；JVM 单测直接覆盖。
 *  2. **认不出来就返回 null**：ErrorCard 对 null 走原有展示，行为与改造前完全一致——
 *     分类器只做增量，不做替换，把回归风险压到零。
 *  3. **不吞原始错误**：ErrorCard 的复制按钮仍复制原始 message，方便用户反馈、方便排查。
 *  4. **内置渠道判断用启发式**：HTTP 层的异常不带 host，只有连接层异常（UnknownHost 等）
 *     的 message 里才有。所以「内置渠道专属文案」的判据是二选一：
 *     a) 异常 message 里出现内置渠道 baseUrl 的 host；
 *     b) 内置渠道当前处于「已付费 + 已启用」状态（付费用户默认走的就是这条渠道）。
 *     (b) 对自配渠道的用户偶尔会误命中，但文案本身不提任何具体地址，读起来不突兀，
 *     换来的是「付费用户看到安抚话术」这个主场景稳定命中。
 */
object DfwxErrorMessages {

    /** 一条人话提示。文案里不许出现上游地址、Key 等任何敏感内容（有测试钉死）。 */
    data class Friendly(val text: String)

    fun explain(error: Throwable): Friendly? {
        val msg = error.message ?: ""
        val lower = msg.lowercase()
        val builtin = isBuiltinFlavored(error, msg)

        // ── 连接层：异常类型本身就说明问题，最可靠 ──
        val networkLike = when (error) {
            is java.net.UnknownHostException,
            is java.net.ConnectException,
            is javax.net.ssl.SSLException,
            is java.net.SocketTimeoutException,
            is java.net.SocketException,
            -> true
            else -> false
        }
        // OkHttp 有时把流断包成 IOException，类型判不出来，靠 message 关键词兜
        val brokenStream = listOf(
            "connection reset", "broken pipe", "unexpected end of stream", "stream was reset",
        ).any { lower.contains(it) }

        if (networkLike || brokenStream || lower.contains("econnrefused")) {
            return if (lower.contains("timeout") || error is java.net.SocketTimeoutException) {
                Friendly(if (builtin) TIMEOUT_BUILTIN else TIMEOUT)
            } else {
                Friendly(if (builtin) NETWORK_BUILTIN else NETWORK)
            }
        }

        // ── HTTP 层：ChatCompletionsAPI.kt:106 的 "Failed to get response: <code> <body>" ──
        val code = Regex("failed to get response:\\s*(\\d{3})").find(lower)?.groupValues?.get(1)
        if (code != null) {
            return httpCategory(code.toInt(), builtin)
        }

        // ── 内容层：流式 decoder / 上游 JSON 透出的关键词（one-api 系中转站的常见话术）──
        if (lower.contains("insufficient") || lower.contains("quota") || lower.contains("余额")
            || lower.contains("余额不足") || msg.contains("欠费")
        ) {
            return Friendly(if (builtin) QUOTA_BUILTIN else QUOTA)
        }
        if (lower.contains("rate limit") || lower.contains("too many requests")
            || msg.contains("请求过于频繁") || msg.contains("稍后再试")
        ) {
            return Friendly(if (builtin) RATE_BUILTIN else RATE)
        }
        // 上游回了不合法的响应（choices is null / message is null）
        if (lower.contains("choices is null") || lower.contains("message is null")
            || lower.contains("failed to get response")
        ) {
            return Friendly(if (builtin) GARBAGE_BUILTIN else GARBAGE)
        }

        // 认不出来：交还原有展示逻辑
        return null
    }

    private fun httpCategory(code: Int, builtin: Boolean): Friendly = when {
        code == 401 || code == 403 -> Friendly(if (builtin) AUTH_BUILTIN else AUTH)
        code == 402 -> Friendly(if (builtin) QUOTA_BUILTIN else QUOTA)
        code == 404 -> Friendly(if (builtin) GARBAGE_BUILTIN else GARBAGE)
        code == 429 -> Friendly(if (builtin) RATE_BUILTIN else RATE)
        code in 500..599 -> Friendly(if (builtin) SERVER_BUILTIN else SERVER)
        else -> Friendly(if (builtin) GARBAGE_BUILTIN else GARBAGE)
    }

    private fun isBuiltinFlavored(error: Throwable, msg: String): Boolean {
        val baseUrl = DfwxBuiltinChannel.current().baseUrl
        val host = baseUrl
            .removePrefix("https://")
            .removePrefix("http://")
            .substringBefore('/')
            .trim()
        if (host.isNotEmpty() && msg.contains(host)) return true
        // 启发式 b：付费且启用的用户，默认选中的就是内置渠道
        return DfwxBuiltinChannel.isPaid() &&
            DfwxBuiltinChannel.current().enabled &&
            DfwxBuiltinChannel.current().token.isNotBlank()
    }

    // ── 文案表。语气按用户要求：像东方本人说话，口语、诚实、给下一步、不恐慌。 ──
    // 修改文案时同步改 DfwxErrorMessagesTest 里的锚点断言。

    private const val NETWORK_BUILTIN =
        "连不上服务器了。先看看手机网络断没断；网络正常还这样的话，就是我这边的事，我在修——东方"
    private const val NETWORK =
        "连不上服务器。先看看手机网络断没断，或者换个网（比如切流量）再试。"
    private const val TIMEOUT_BUILTIN =
        "这回等太久了，先给你停了。再发一次试试；老是这样就是我这边慢，不是你的问题——东方"
    private const val TIMEOUT =
        "等太久超时了。再发一次试试；网络不好的话换个网。"
    private const val AUTH_BUILTIN =
        "渠道钥匙这会儿对不上号。放心，你的付费好好的，是我这边配置的事，我在修——东方"
    private const val AUTH =
        "这个渠道拒绝了请求（鉴权失败）。检查一下 API Key 有没有填对。"
    private const val QUOTA_BUILTIN =
        "中转站的额度这会儿不够了。我在处理，你的付费没受影响，稍等再试——东方"
    private const val QUOTA =
        "这个渠道的额度不够了（余额或配额用完）。去服务商那边看看，或者换个渠道。"
    private const val RATE_BUILTIN =
        "这会儿用的人有点多，挤了。歇一两分钟再发就行，什么都不用改。"
    private const val RATE =
        "请求太频繁，被限流了。歇一两分钟再发。"
    private const val SERVER_BUILTIN =
        "中转站这会儿抽风了，不是你的问题，也不影响你的付费。我盯着呢，稍等——东方"
    private const val SERVER =
        "AI 服务这会儿出了故障。等几分钟再试。"
    private const val GARBAGE_BUILTIN =
        "上游回了口不对劲的响应，一般是它那边临时出岔子。再试一次，老这样就找我反馈。"
    private const val GARBAGE =
        "上游回了不正常的响应。再试一次；老出问题的话反馈给我。"
}
