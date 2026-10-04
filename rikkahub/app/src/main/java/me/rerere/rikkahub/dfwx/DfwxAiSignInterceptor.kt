package me.rerere.rikkahub.dfwx

import android.util.Log
import okhttp3.Interceptor
import okhttp3.Response
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * [DFW-147] 内置 AI 渠道的请求签名拦截器。
 *
 * ## 解决什么问题
 *
 * 内置渠道的令牌必须烧进 APK，而 **APK 里任何密钥都能被反编译扒出来** ——
 * 这是客户端凭据的固有性质，业界没有例外。所以真正的防线不是"扒不出来"，而是：
 *
 * 1. **光有令牌没用** —— 必须能算出签名（本文件负责这一条）；
 * 2. **泄露一个不牵连别人** —— 每台设备独立标识；
 * 3. **就算被用也有上限** —— 服务端配额 + 限流。
 *
 * 加了这个之后，`curl -H "Authorization: Bearer <扒来的令牌>"` 会被服务端拒掉：
 * 攻击者还得反编译出签名密钥、并重新实现下面这套算法才能用。
 *
 * ## 签名算法（服务端 `server/dfwx-ai-guard/server.py` 必须一字不差地对上）
 *
 * ```
 * payload = ts + "\n" + nonce + "\n" + method + "\n" + path
 * sig     = HMAC-SHA256(signKey, payload)  的十六进制小写
 * ```
 *
 * - `ts`：Unix 秒（十进制）
 * - `nonce`：每次请求都不同的随机十六进制串（32 字符）
 * - `method`：大写 HTTP 方法
 * - `path`：请求路径，**不含查询串**（服务端也会剥掉，两边一致）
 *
 * ## 为什么只签自家服务器的请求
 *
 * 用户在 RikkaHub 里配的第三方渠道（OpenAI、Claude 等）**一个字节都不能碰** ——
 * 给它们的请求加我们自己的头，轻则被对方拒绝，重则泄露我们的存在。
 * 所以这里按 host 判断，只对自家服务器生效。
 *
 * ## 失败时怎么办
 *
 * **一律放行**（fail-open）：签名算不出来（密钥没注入、随机数取不到）时不能抛异常，
 * 否则用户会看到"AI 完全不能用"，而真正的原因只是一个构建期配置没配上。
 * 服务端此时会在日志里记 `bad-ts`/`bad-signature`，能看出来。
 */
object DfwxAiSign {
    private const val TAG = "DfwxAiSign"

    /** 签名密钥。由宿主 `:app` 在启动时通过 [install] 推过来；空 = 不签名。 */
    @Volatile
    private var signKey: String = ""

    /** 本机标识。用于服务端按设备计数/封禁；不是机密，但每台机器不同。 */
    @Volatile
    private var deviceId: String = ""

    private val random = SecureRandom()

    fun install(key: String, device: String) {
        signKey = key.trim()
        deviceId = device.trim()
    }

    /** 这个 URL 是不是走我们自己的服务器（只有它才需要签名）。 */
    fun shouldSign(host: String, configuredBaseUrl: String): Boolean {
        if (signKey.isEmpty()) return false
        val base = configuredBaseUrl.trim()
        if (base.isEmpty()) return false
        val baseHost = runCatching { java.net.URI(base).host ?: "" }.getOrDefault("")
        return baseHost.isNotEmpty() && baseHost.equals(host, ignoreCase = true)
    }

    private fun hex(bytes: ByteArray): String {
        val out = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            out.append("0123456789abcdef"[v shr 4])
            out.append("0123456789abcdef"[v and 0x0F])
        }
        return out.toString()
    }

    private fun nonce(): String {
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        return hex(bytes)
    }

    /**
     * 给请求加上签名头。返回 null 表示"这次不签"（调用方原样放行）。
     */
    fun signHeaders(method: String, path: String): Map<String, String>? {
        val key = signKey
        if (key.isEmpty()) return null
        return try {
            val ts = (System.currentTimeMillis() / 1000L).toString()
            val n = nonce()
            val payload = ts + "\n" + n + "\n" + method.uppercase() + "\n" + path
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacSHA256"))
            val sig = hex(mac.doFinal(payload.toByteArray(Charsets.UTF_8)))
            buildMap {
                put("X-DFWX-Ts", ts)
                put("X-DFWX-Nonce", n)
                put("X-DFWX-Sig", sig)
                if (deviceId.isNotEmpty()) put("X-DFWX-Device", deviceId)
            }
        } catch (t: Throwable) {
            // fail-open：签不出来就放行，让服务端的 observe 日志去暴露问题
            Log.w(TAG, "sign failed, request passes unsigned: " + t.message, t)
            null
        }
    }
}

/**
 * [DFW-147] 把签名头挂到发往自家服务器的 AI 请求上。
 *
 * 只认 [baseUrl] 这个 host，其余请求原样透传。
 */
class DfwxAiSignInterceptor(
    private val baseUrl: () -> String,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val configured = baseUrl()
        if (!DfwxAiSign.shouldSign(request.url.host, configured)) {
            return chain.proceed(request)
        }
        val headers = DfwxAiSign.signHeaders(request.method, request.url.encodedPath) ?: return chain.proceed(request)
        val builder = request.newBuilder()
        headers.forEach { (name, value) -> builder.header(name, value) }
        return chain.proceed(builder.build())
    }
}
