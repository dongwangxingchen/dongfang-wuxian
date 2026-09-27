package me.rerere.ai.provider

import okhttp3.Connection
import okhttp3.Dns
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * DFWX-NET-001: 普通外部 AI/API HTTPS-only 策略回归测试。
 *
 * 策略约束：
 * - 仅允许 HTTPS
 * - 拒绝携带 userinfo（账号/密码）的地址
 * - 拒绝本机/内网/link-local/私网/multicast 字面量与本地域名后缀
 * - 拒绝解析到私网地址的域名（DNS rebinding 防护，解析器可注入以保证测试确定性）
 * - 解析失败时 fail-closed
 * - 重定向目标 URL 作为普通输入走同一策略判定
 */
class AiUrlPolicyTest {

  // ---------- 负向：协议与凭据 ----------

  @Test
  fun rejectsNonHttpsSchemes() {
    assertEquals(
      AiUrlPolicy.REASON_SCHEME,
      AiUrlPolicy.staticRejectionReason("http://api.example.com/v1".toHttpUrl())
    )
    // ftp 无法构造为 HttpUrl（OkHttp 只支持 http/https），在字符串入口拒绝
    assertEquals(
      AiUrlPolicy.REASON_INVALID,
      AiUrlPolicy.rejectionReason("ftp://api.example.com/v1")
    )
  }

  @Test
  fun rejectsBlankAndMalformedBaseUrlStrings() {
    assertEquals(AiUrlPolicy.REASON_EMPTY, AiUrlPolicy.rejectionReason(null))
    assertEquals(AiUrlPolicy.REASON_EMPTY, AiUrlPolicy.rejectionReason("  "))
    assertEquals(AiUrlPolicy.REASON_INVALID, AiUrlPolicy.rejectionReason("not a url"))
    assertEquals(
      AiUrlPolicy.REASON_SCHEME,
      AiUrlPolicy.rejectionReason("http://api.example.com/v1")
    )
    assertEquals(
      AiUrlPolicy.REASON_PRIVATE,
      AiUrlPolicy.rejectionReason("https://10.0.0.8/v1")
    )
    assertNull(AiUrlPolicy.rejectionReason("https://api.example.com/v1"))
  }

  @Test
  fun rejectsCredentialsInUrl() {
    assertEquals(
      AiUrlPolicy.REASON_USERINFO,
      AiUrlPolicy.staticRejectionReason("https://user:secret@api.example.com/v1".toHttpUrl())
    )
    assertEquals(
      AiUrlPolicy.REASON_USERINFO,
      AiUrlPolicy.staticRejectionReason("https://api-key@api.example.com/v1".toHttpUrl())
    )
  }

  // ---------- 负向：私网/本机字面量与本地域名 ----------

  @Test
  fun rejectsLocalAndPrivateLiterals() {
    val rejected = listOf(
      "https://localhost/v1",
      "https://127.0.0.1/v1",
      "https://10.0.0.8/v1",
      "https://172.16.0.8/v1",
      "https://172.31.255.255/v1",
      "https://192.168.1.8/v1",
      "https://100.64.0.8/v1",
      "https://169.254.169.254/v1",
      "https://0.0.0.0/v1",
      "https://224.0.0.1/v1",
      "https://240.0.0.1/v1",
      "https://198.18.0.1/v1",
      "https://192.0.0.8/v1",
      "https://[::1]/v1",
      "https://[fe80::1]/v1",
      "https://[fd12:3456::1]/v1",
      "https://[::ffff:192.168.1.8]/v1",
    )
    for (url in rejected) {
      assertEquals(
        "expected rejection for $url",
        AiUrlPolicy.REASON_PRIVATE,
        AiUrlPolicy.staticRejectionReason(url.toHttpUrl())
      )
    }
  }

  @Test
  fun rejectsLocalSuffixHostnames() {
    val rejected = listOf(
      "https://printer.local/v1",
      "https://host.localhost/v1",
      "https://db.internal/v1",
      "https://home.arpa/v1",
    )
    for (url in rejected) {
      assertEquals(
        "expected rejection for $url",
        AiUrlPolicy.REASON_PRIVATE,
        AiUrlPolicy.staticRejectionReason(url.toHttpUrl())
      )
    }
  }

  // ---------- 正向：HTTPS 公网 ----------

  @Test
  fun allowsPublicHttpsHosts() {
    val allowed = listOf(
      "https://api.openai.com/v1",
      "https://api.anthropic.com/v1",
      "https://generativelanguage.googleapis.com/v1beta",
    )
    for (url in allowed) {
      assertNull(
        "expected allowance for $url",
        AiUrlPolicy.staticRejectionReason(url.toHttpUrl())
      )
    }
  }

  @Test
  fun allowsPublicLiteralAddresses() {
    // 公网字面量与紧邻私网段边界的公网地址不应被误伤
    val allowed = listOf(
      "https://8.8.8.8/v1",
      "https://172.32.0.1/v1",
      "https://[2606:4700::6810:84e5]/v1",
    )
    for (url in allowed) {
      assertNull(
        "expected allowance for $url",
        AiUrlPolicy.staticRejectionReason(url.toHttpUrl())
      )
    }
  }

  // ---------- DNS rebinding 防护（注入 resolver，不依赖真实网络） ----------

  private val publicV4 = InetAddress.getByAddress(
    byteArrayOf(93.toByte(), 184.toByte(), 216.toByte(), 34.toByte())
  )
  private val privateV4 = InetAddress.getByAddress(byteArrayOf(10, 0, 0, 8))
  private val loopbackV4 = InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1))

  @Test
  fun resolvedPolicyRejectsHostnamesResolvingToPrivateAddresses() {
    assertEquals(
      AiUrlPolicy.REASON_PRIVATE,
      AiUrlPolicy.resolvedRejectionReason(
        "https://rebinding.example.com/v1".toHttpUrl()
      ) { listOf(publicV4, privateV4) }
    )
    assertEquals(
      AiUrlPolicy.REASON_PRIVATE,
      AiUrlPolicy.resolvedRejectionReason(
        "https://rebinding.example.com/v1".toHttpUrl()
      ) { listOf(loopbackV4) }
    )
  }

  @Test
  fun resolvedPolicyAllowsHostnamesResolvingToPublicAddressesOnly() {
    assertNull(
      AiUrlPolicy.resolvedRejectionReason(
        "https://api.example.com/v1".toHttpUrl()
      ) { listOf(publicV4) }
    )
  }

  @Test
  fun resolvedPolicyFailsClosedWhenResolutionFails() {
    assertEquals(
      AiUrlPolicy.REASON_UNVERIFIED,
      AiUrlPolicy.resolvedRejectionReason(
        "https://host.invalid/v1".toHttpUrl()
      ) { throw UnknownHostException("dns error") }
    )
    assertEquals(
      AiUrlPolicy.REASON_UNVERIFIED,
      AiUrlPolicy.resolvedRejectionReason(
        "https://empty.example.com/v1".toHttpUrl()
      ) { emptyList() }
    )
  }

  @Test
  fun resolvedPolicyAppliesStaticChecksFirst() {
    // 静态可判定的（协议/凭据/字面量）不需要等解析结果
    assertEquals(
      AiUrlPolicy.REASON_SCHEME,
      AiUrlPolicy.resolvedRejectionReason(
        "http://api.example.com/v1".toHttpUrl()
      ) { listOf(publicV4) }
    )
    assertEquals(
      AiUrlPolicy.REASON_PRIVATE,
      AiUrlPolicy.resolvedRejectionReason(
        "https://10.0.0.8/v1".toHttpUrl()
      ) { listOf(publicV4) }
    )
  }

  // ---------- 重定向目标：作为普通 URL 输入同一策略 ----------

  @Test
  fun redirectTargetsAreJudgedByTheSamePolicy() {
    // https -> http 跨协议重定向目标：拒绝
    assertEquals(
      AiUrlPolicy.REASON_SCHEME,
      AiUrlPolicy.staticRejectionReason("http://mirror.example.com/v1".toHttpUrl())
    )
    // 重定向到私网字面量：拒绝
    assertEquals(
      AiUrlPolicy.REASON_PRIVATE,
      AiUrlPolicy.staticRejectionReason("https://10.0.0.5/v1".toHttpUrl())
    )
    // 重定向到本地域名：拒绝
    assertEquals(
      AiUrlPolicy.REASON_PRIVATE,
      AiUrlPolicy.staticRejectionReason("https://mirror.db.internal/v1".toHttpUrl())
    )
    // 重定向到解析到私网的域名：拒绝（rebinding）
    assertEquals(
      AiUrlPolicy.REASON_PRIVATE,
      AiUrlPolicy.resolvedRejectionReason(
        "https://mirror.example.com/v1".toHttpUrl()
      ) { listOf(privateV4) }
    )
    // 重定向到 HTTPS 公网：放行
    assertNull(
      AiUrlPolicy.staticRejectionReason("https://mirror.example.com/v1".toHttpUrl())
    )
  }

  // ---------- 拦截器行为：请求入口（proceed 之前）判定 ----------

  @Test
  fun interceptorRejectsDisallowedUrlBeforeProceeding() {
    val chain = FakeChain(
      "https://10.0.0.8/v1".toHttpUrl(),
      resolver = { listOf(publicV4) },
    )
    val interceptor = AiUrlPolicyInterceptor(resolve = true, resolver = chain.resolver)
    try {
      interceptor.intercept(chain)
      assertTrue("expected IOException", false)
    } catch (expected: IOException) {
      assertEquals(AiUrlPolicy.REASON_PRIVATE, expected.message)
    }
    assertFalse("proceed must not be called for rejected request", chain.proceeded)
  }

  @Test
  fun networkVariantRejectsNonHttpsSchemeWithoutResolution() {
    val chain = FakeChain(
      "http://api.example.com/v1".toHttpUrl(),
      resolver = { throw AssertionError("static check must not resolve DNS") },
    )
    val interceptor = AiUrlPolicyInterceptor(resolve = false, resolver = chain.resolver)
    try {
      interceptor.intercept(chain)
      assertTrue("expected IOException", false)
    } catch (expected: IOException) {
      assertEquals(AiUrlPolicy.REASON_SCHEME, expected.message)
    }
    assertFalse(chain.proceeded)
  }

  @Test
  fun interceptorAllowsAndProceedsForPublicHttps() {
    val chain = FakeChain(
      "https://api.example.com/v1".toHttpUrl(),
      resolver = { listOf(publicV4) },
    )
    val interceptor = AiUrlPolicyInterceptor(resolve = true, resolver = chain.resolver)
    val response = interceptor.intercept(chain)
    assertTrue(chain.proceeded)
    assertEquals(200, response.code)
  }

  // ---------- DNS 层：连接前拦截私网解析（覆盖重定向目标） ----------

  @Test
  fun aiPolicyDnsRejectsPrivateResolution() {
    val dns = AiPolicyDns { listOf(publicV4, privateV4) }
    try {
      dns.lookup("rebinding.example.com")
      assertTrue("expected UnknownHostException", false)
    } catch (expected: UnknownHostException) {
      assertEquals(AiUrlPolicy.REASON_PRIVATE, expected.message)
    }
  }

  @Test
  fun aiPolicyDnsRejectsEmptyResolutionFailClosed() {
    val dns = AiPolicyDns { emptyList() }
    try {
      dns.lookup("empty.example.com")
      assertTrue("expected UnknownHostException", false)
    } catch (expected: UnknownHostException) {
      assertEquals(AiUrlPolicy.REASON_UNVERIFIED, expected.message)
    }
  }

  @Test
  fun aiPolicyDnsAllowsPublicResolution() {
    val dns: Dns = AiPolicyDns { listOf(publicV4) }
    val resolved = dns.lookup("api.example.com")
    assertEquals(listOf(publicV4), resolved)
  }

  // ---------- 测试工具 ----------

  private class FakeChain(
    url: okhttp3.HttpUrl,
    val resolver: (String) -> List<InetAddress>,
  ) : Interceptor.Chain {
    var proceeded = false
      private set
    private val request = Request.Builder().url(url).build()
    private val cannedResponse = Response.Builder()
      .request(request)
      .protocol(Protocol.HTTP_1_1)
      .code(200)
      .message("OK")
      .build()

    override fun request(): Request = request

    override fun proceed(request: Request): Response {
      proceeded = true
      return cannedResponse
    }

    override fun connection(): Connection? = null

    override fun call(): okhttp3.Call = error("not used in test")

    override val followSslRedirects: Boolean = false
    override val followRedirects: Boolean = false
    override val dns: Dns = Dns.SYSTEM
    override val socketFactory: javax.net.SocketFactory = javax.net.SocketFactory.getDefault()
    override val retryOnConnectionFailure: Boolean = false
    override val authenticator: okhttp3.Authenticator = okhttp3.Authenticator.NONE
    override val cookieJar: okhttp3.CookieJar = okhttp3.CookieJar.NO_COOKIES
    override val cache: okhttp3.Cache? = null
    override val proxy: java.net.Proxy? = null
    override val proxySelector: java.net.ProxySelector = object : java.net.ProxySelector() {
      override fun select(uri: java.net.URI?): MutableList<java.net.Proxy> =
        mutableListOf(java.net.Proxy.NO_PROXY)

      override fun connectFailed(
        uri: java.net.URI?,
        sa: java.net.SocketAddress?,
        ioe: java.io.IOException?,
      ) {
      }
    }
    override val proxyAuthenticator: okhttp3.Authenticator = okhttp3.Authenticator.NONE
    override val sslSocketFactoryOrNull: javax.net.ssl.SSLSocketFactory? = null
    override val x509TrustManagerOrNull: javax.net.ssl.X509TrustManager? = null
    override val hostnameVerifier: javax.net.ssl.HostnameVerifier =
      javax.net.ssl.HostnameVerifier { _, _ -> true }
    override val certificatePinner: okhttp3.CertificatePinner = okhttp3.CertificatePinner.DEFAULT
    override val connectionPool: okhttp3.ConnectionPool = okhttp3.ConnectionPool()
    override val eventListener: okhttp3.EventListener = okhttp3.EventListener.NONE

    override fun connectTimeoutMillis(): Int = 10_000

    override fun withConnectTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this

    override fun readTimeoutMillis(): Int = 10_000

    override fun withReadTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this

    override fun writeTimeoutMillis(): Int = 10_000

    override fun withWriteTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this

    override fun withDns(dns: Dns): Interceptor.Chain = this

    override fun withSocketFactory(socketFactory: javax.net.SocketFactory): Interceptor.Chain = this

    override fun withRetryOnConnectionFailure(retryOnConnectionFailure: Boolean): Interceptor.Chain =
      this

    override fun withAuthenticator(authenticator: okhttp3.Authenticator): Interceptor.Chain = this

    override fun withCookieJar(cookieJar: okhttp3.CookieJar): Interceptor.Chain = this

    override fun withCache(cache: okhttp3.Cache?): Interceptor.Chain = this

    override fun withProxy(proxy: java.net.Proxy?): Interceptor.Chain = this

    override fun withProxySelector(proxySelector: java.net.ProxySelector): Interceptor.Chain = this

    override fun withProxyAuthenticator(proxyAuthenticator: okhttp3.Authenticator): Interceptor.Chain =
      this

    override fun withSslSocketFactory(
      sslSocketFactory: javax.net.ssl.SSLSocketFactory?,
      x509TrustManager: javax.net.ssl.X509TrustManager?,
    ): Interceptor.Chain = this

    override fun withHostnameVerifier(hostnameVerifier: javax.net.ssl.HostnameVerifier): Interceptor.Chain =
      this

    override fun withCertificatePinner(certificatePinner: okhttp3.CertificatePinner): Interceptor.Chain =
      this

    override fun withConnectionPool(connectionPool: okhttp3.ConnectionPool): Interceptor.Chain = this
  }
}
