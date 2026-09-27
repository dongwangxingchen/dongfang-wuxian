package me.rerere.ai.provider

import okhttp3.Dns
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.net.InetAddress
import java.net.UnknownHostException

/**
 * DFWX-NET-001: AI 请求链的 URL 策略拦截器，在请求入口（proceed 之前）判定放行/拒绝。
 *
 * 两种用法：
 * - application 拦截器（resolve = true，默认）：对初始请求做完整判定，
 *   含 DNS rebinding 解析校验（[AiUrlPolicy.resolvedRejectionReason]）；
 * - network 拦截器（resolve = false）：对每个实际网络请求（含 OkHttp 重定向 follow-up）
 *   做静态判定；私网解析拦截由 [AiPolicyDns] 在连接建立前完成。
 *
 * 跨协议（https -> http）重定向由 client 的 followSslRedirects(false) 直接不跟随（fail-closed）。
 */
class AiUrlPolicyInterceptor(
  private val resolve: Boolean = true,
  private val resolver: (String) -> List<InetAddress> = AiUrlPolicy::defaultResolve,
) : Interceptor {
  override fun intercept(chain: Interceptor.Chain): Response {
    val url = chain.request().url
    val reason = if (resolve) {
      AiUrlPolicy.resolvedRejectionReason(url, resolver)
    } else {
      AiUrlPolicy.staticRejectionReason(url)
    }
    if (reason != null) throw IOException(reason)
    return chain.proceed(chain.request())
  }
}

/**
 * DNS 层防护：任何一次连接（包括重定向 follow-up）解析到本机/内网/link-local/私网/
 * multicast 地址即拒绝。这是 DNS rebinding 防护的最终判定点，紧贴连接建立前执行。
 */
class AiPolicyDns(
  private val delegate: Dns = Dns.SYSTEM,
) : Dns {
  override fun lookup(hostname: String): List<InetAddress> {
    val addresses = delegate.lookup(hostname)
    if (addresses.isEmpty()) throw UnknownHostException(AiUrlPolicy.REASON_UNVERIFIED)
    if (addresses.any { AiUrlPolicy.isPrivateAddress(it) }) {
      throw UnknownHostException(AiUrlPolicy.REASON_PRIVATE)
    }
    return addresses
  }
}
