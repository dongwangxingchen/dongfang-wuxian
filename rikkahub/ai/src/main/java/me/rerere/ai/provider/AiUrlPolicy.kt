package me.rerere.ai.provider

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.net.InetAddress
import java.util.Locale

/**
 * DFWX-NET-001: 普通外部 AI/API 请求地址策略（HTTPS-only）。
 *
 * 仅适用于 vendor AI 请求链，独立于官方更新 allowlist 与蓝奏云解析策略。
 * 拒绝原因稳定、只给类别，不暴露解析细节。
 */
object AiUrlPolicy {
  const val REASON_EMPTY = "AI 服务地址为空"
  const val REASON_INVALID = "AI 服务地址无效"
  const val REASON_SCHEME = "仅支持 HTTPS 的 AI 服务地址"
  const val REASON_USERINFO = "AI 服务地址不允许携带账号或密码"
  const val REASON_PRIVATE = "AI 服务地址不允许指向本机或内网地址"
  const val REASON_UNVERIFIED = "无法验证 AI 服务地址"

  /** 字符串入口（如 ProviderSetting.baseUrl）：空值、非法 URL 与非 HTTPS 协议均拒绝。 */
  fun rejectionReason(raw: String?): String? {
    if (raw.isNullOrBlank()) return REASON_EMPTY
    val url = try {
      raw.trim().toHttpUrl()
    } catch (_: Exception) {
      return REASON_INVALID
    }
    return staticRejectionReason(url)
  }

  /**
   * 纯静态判定：协议 / 凭据 / 主机名形态与 IP 字面量，不做网络解析。
   * 返回 null 表示放行。
   */
  fun staticRejectionReason(url: HttpUrl): String? {
    if (!url.isHttps) return REASON_SCHEME
    if (url.username.isNotEmpty() || url.password.isNotEmpty()) return REASON_USERINFO
    val host = url.host.trim().trimEnd('.').lowercase(Locale.ROOT)
    if (host.isEmpty()) return REASON_UNVERIFIED
    if (isLocalHostname(host)) return REASON_PRIVATE
    if (looksLikeLiteralAddress(host)) {
      // 字面量解析失败按拒绝处理，不允许变成绕过
      val addresses = try {
        InetAddress.getAllByName(host)
      } catch (_: Exception) {
        return REASON_PRIVATE
      }
      if (addresses.any { isPrivateAddress(it) }) return REASON_PRIVATE
    }
    return null
  }

  /**
   * 连接前的完整判定：静态检查 + 域名解析校验（DNS rebinding 防护）。
   * 在请求入口的工作线程调用；解析失败时 fail-closed。
   */
  fun resolvedRejectionReason(
    url: HttpUrl,
    resolver: (String) -> List<InetAddress> = AiUrlPolicy::defaultResolve,
  ): String? {
    staticRejectionReason(url)?.let { return it }
    val host = url.host.trim().trimEnd('.').lowercase(Locale.ROOT)
    val addresses = try {
      resolver(host)
    } catch (_: Exception) {
      return REASON_UNVERIFIED
    }
    if (addresses.isEmpty()) return REASON_UNVERIFIED
    if (addresses.any { isPrivateAddress(it) }) return REASON_PRIVATE
    return null
  }

  fun defaultResolve(host: String): List<InetAddress> = InetAddress.getAllByName(host).toList()

  internal fun isLocalHostname(host: String): Boolean =
    host == "localhost" ||
      host.endsWith(".localhost") ||
      host.endsWith(".local") ||
      host.endsWith(".internal") ||
      host == "home.arpa"

  internal fun looksLikeLiteralAddress(host: String): Boolean {
    if (host.indexOf(':') >= 0) return true
    var digitOrDot = true
    for (c in host) {
      if (c !in '0'..'9' && c != '.') {
        digitOrDot = false
        break
      }
    }
    return digitOrDot && host.indexOf('.') >= 0
  }

  internal fun isPrivateAddress(address: InetAddress): Boolean {
    if (address.isAnyLocalAddress
      || address.isLoopbackAddress
      || address.isLinkLocalAddress
      || address.isSiteLocalAddress
      || address.isMulticastAddress
    ) return true
    val bytes = address.address
    if (bytes.size == 4) return isPrivateIpv4(bytes, 0)
    if (bytes.size == 16) {
      val first = bytes[0].toInt() and 0xff
      val second = bytes[1].toInt() and 0xff
      // IPv6 unique local (fc00::/7) 与 link-local (fe80::/10)
      if ((first and 0xfe) == 0xfc) return true
      if (first == 0xfe && (second and 0xc0) == 0x80) return true
      // IPv4-mapped IPv6 ::ffff:a.b.c.d
      var mapped = true
      for (i in 0 until 10) if (bytes[i].toInt() != 0) mapped = false
      if (mapped && (bytes[10].toInt() and 0xff) == 0xff && (bytes[11].toInt() and 0xff) == 0xff) {
        return isPrivateIpv4(bytes, 12)
      }
    }
    return false
  }

  private fun isPrivateIpv4(bytes: ByteArray, offset: Int): Boolean {
    val first = bytes[offset].toInt() and 0xff
    val second = bytes[offset + 1].toInt() and 0xff
    val third = bytes[offset + 2].toInt() and 0xff
    if (first == 0 || first == 10 || first == 127 || first >= 240) return true
    if (first == 169 && second == 254) return true
    if (first == 172 && second in 16..31) return true
    if (first == 192 && second == 168) return true
    if (first == 100 && second in 64..127) return true
    if (first == 198 && (second == 18 || second == 19)) return true
    return first == 192 && second == 0 && third == 0
  }
}
