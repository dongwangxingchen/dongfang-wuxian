package cc.nkbr.lanzouplus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.cert.CertificateException
import javax.net.ssl.SSLHandshakeException

/**
 * [DFW-36 2026-10-04] 失效源分类器的行为测试。
 *
 * 这张卡的全部意义是：**不要让用户以为源坏了，从而去删源。**
 * 历史上真的发生过 —— `lessons.md` 2026-09-26 记录了一次翻案，
 * 84 个源被判「失效」，真实原因却是默认 UA 被 CDN 拉黑 + 解锁请求少发字段。
 *
 * 所以这些测试断言的重点不是「分类对不对」，
 * 而是 **「会不会把临时故障误判成源没了」**。
 */
class FailureClassifyJvmTest {

  @Test
  fun nullErrorIsBudgetNotGone() {
    // 预算截断（搜索时间用完了）不是源出错，更不是源没了。
    // 这是最容易被误判成「源失效」的场景之一。
    val kind = LanzouCore.classifyFailure(null)
    assertEquals(Models.FailureKind.BUDGET, kind)
    assertNotEquals(Models.FailureKind.GONE, kind)
  }

  @Test
  fun timeoutIsTimeoutNotGone() {
    val kind = LanzouCore.classifyFailure(SocketTimeoutException("timeout"))
    assertEquals(Models.FailureKind.TIMEOUT, kind)
    assertNotEquals(Models.FailureKind.GONE, kind)
  }

  @Test
  fun tlsFailureIsNetworkNotGone() {
    // DFW-88 实证：wwc.lanzoux.com 证书过期导致下载全失败，但源本身是好的。
    // 这条必须判成「网络异常」，判成「源失效」就会重演误删。
    val kind = LanzouCore.classifyFailure(SSLHandshakeException("certificate verify failed"))
    assertEquals(Models.FailureKind.NETWORK, kind)
    assertNotEquals(Models.FailureKind.GONE, kind)
  }

  @Test
  fun certificateExceptionIsNetwork() {
    val kind = LanzouCore.classifyFailure(CertificateException("expired"))
    assertEquals(Models.FailureKind.NETWORK, kind)
  }

  @Test
  fun plainIoExceptionIsNetworkNotGone() {
    // 兜底规则：普通 IOException 归 NETWORK（临时、可重试），
    // **绝不**归 GONE —— 宁可让用户重试一次，也不要让他去删源。
    val kind = LanzouCore.classifyFailure(UnknownHostException("no route"))
    assertEquals(Models.FailureKind.NETWORK, kind)
    assertNotEquals(Models.FailureKind.GONE, kind)
  }

  @Test
  fun unknownThrowableIsUnknownNotGone() {
    // 最关键的一条兜底：完全不认识的异常也不能说「源没了」。
    val kind = LanzouCore.classifyFailure(IllegalStateException("something odd"))
    assertEquals(Models.FailureKind.UNKNOWN, kind)
    assertNotEquals(Models.FailureKind.GONE, kind)
  }

  @Test
  fun onlySourceSaysGoneIsGone() {
    // 只有源站自己明确说「分享已取消 / 文件不存在」时才允许判 GONE。
    assertEquals(Models.FailureKind.GONE, LanzouCore.classifyFailure(IOException("分享已取消")))
    assertEquals(Models.FailureKind.GONE, LanzouCore.classifyFailure(IOException("文件不存在")))
    assertEquals(Models.FailureKind.GONE, LanzouCore.classifyFailure(IOException("分享不存在")))
  }

  @Test
  fun parseFailureIsOursNotUsers() {
    // 「无效直链 / 解析失败」= 蓝奏页面结构变了，是**我们要修的**。
    // 这类必须能和 GONE 区分开，否则用户会以为源坏了。
    val kind = LanzouCore.classifyFailure(IOException("蓝奏返回了无效直链"))
    assertEquals(Models.FailureKind.PARSE, kind)
    assertNotEquals(Models.FailureKind.GONE, kind)
  }

  @Test
  fun wrappedCauseIsStillClassified() {
    // 真实场景里异常经常被包装。只看最外层会全部落到 UNKNOWN，
    // 那就等于分类器没起作用。
    val wrapped = IOException("wrap", SSLHandshakeException("cert"))
    assertEquals(Models.FailureKind.NETWORK, LanzouCore.classifyFailure(wrapped))

    val wrappedTimeout = IOException("wrap", SocketTimeoutException("t"))
    assertEquals(Models.FailureKind.TIMEOUT, LanzouCore.classifyFailure(wrappedTimeout))
  }

  @Test
  fun everyKindHasNonEmptyLabelAndAdvice() {
    // 每个分类都必须有「短标签」和「下一步做什么」。
    // 空 advice 会让弹窗出现一行空白 —— 用户看到的就是「这软件坏了」。
    for (kind in Models.FailureKind.values()) {
      assertTrue("${kind.name} 的 label 不能为空", kind.label.isNotBlank())
      assertTrue("${kind.name} 的 advice 不能为空", kind.advice.isNotBlank())
    }
  }

  @Test
  fun noAdviceTellsUserTheSourceIsDead() {
    /*
     * 这是整张卡的护栏。
     * 除了 GONE 自己，任何一档的文案里都不许出现「源失效 / 源坏了 / 已失效」，
     * 否则就等于换了个地方继续误导用户去删源。
     */
    val forbidden = listOf("源失效", "源已失效", "源坏了", "该源失效")
    for (kind in Models.FailureKind.values()) {
      if (kind == Models.FailureKind.GONE) continue
      for (word in forbidden) {
        assertFalse(
          "${kind.name} 的文案里出现了「$word」，会误导用户删源：${kind.advice}",
          kind.advice.contains(word) || kind.label.contains(word)
        )
      }
    }
  }

  @Test
  fun goneIsTheOnlyKindThatBlamesTheSource() {
    // 反向断言：GONE 必须**是**唯一说「失效」的那一档，
    // 否则上面那个测试可能因为「所有档都不说失效」而变成空测试。
    assertTrue(
      "GONE 的文案应该明确说分享没了",
      Models.FailureKind.GONE.label.contains("失效") || Models.FailureKind.GONE.advice.contains("失效")
    )
  }

  @Test
  fun unknownKindIsIncludedInTheEnum() {
    // UNKNOWN 必须存在：分类器遇到不认识的东西要有个去处，
    // 而不是抛异常或返回 null。
    assertTrue(Models.FailureKind.values().any { it == Models.FailureKind.UNKNOWN })
  }

  @Test
  fun realPasswordExceptionIsPassword() {
    // 用 LanzouCore 自己的异常类，不是复刻的假类 —— 这样才证明分类器
    // 真的认识线上会抛出来的那个类型。
    val kind = LanzouCore.classifyFailure(LanzouCore.DirectPasswordException())
    assertEquals(Models.FailureKind.PASSWORD, kind)
    assertNotEquals(Models.FailureKind.GONE, kind)
  }

  @Test
  fun realRateLimitedRetryIsRateLimitNotTimeout() {
    // 同样是 DirectRetryException，rateLimited=true 和 false 必须分开：
    // 前者是「等一会儿」（用户什么都不用做），后者才需要重试。
    val limited = LanzouCore.classifyFailure(
      LanzouCore.DirectRetryException("限频", 3000L, true)
    )
    assertEquals(Models.FailureKind.RATE_LIMIT, limited)

    val notLimited = LanzouCore.classifyFailure(
      LanzouCore.DirectRetryException("重试", 1000L, false)
    )
    assertEquals(Models.FailureKind.TIMEOUT, notLimited)
    assertNotEquals(limited, notLimited)
  }
}
