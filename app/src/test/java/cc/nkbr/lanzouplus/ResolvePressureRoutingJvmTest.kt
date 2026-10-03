package cc.nkbr.lanzouplus

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

/**
 * [DFW-128 2026-10-03] 「永远解析中」的根因回归。
 *
 * ## 这个 bug 长什么样
 * 用户**从 v1.0.5 一路报到 v1.0.12**：下载永远停在「解析中」，不崩溃、不报错、不超时。
 *
 * ## 根因链（三段，缺一不可）
 * ```
 * ① WafCookieSolver 把**过期的旧 cookie** 当成解开的结果（已修，见 WafCookieFreshnessJvmTest）
 *      ↓
 * ② LanzouCore 用这个死 cookie 过不了阿里云 WAF，抛
 *    new DirectRetryException("蓝奏 ACW 验证未完成", 1000, rateLimited=false)
 *      ↓
 * ③ DirectLinkResolver.upstreamPressure() **只按报错文字判断**，关键词里有「验证」→ 必然命中
 *    → 走进那条**静默重试**分支 → return 而不调用 finished()
 *    → 回调永远不发生 → 界面永远「解析中」
 * ```
 *
 * ## 本文件守的是第 ③ 段
 * 这是**最隐蔽**的一段：前两段只是"失败"，失败本身可以接受；
 * 第三段把"失败"变成了"永远不说结果"，这才是用户真正受的苦。
 *
 * ⚠️ 判据本身（`upstreamPressure`）已放开到包内可见，就是为了能直接测它 ——
 * 它一行代码决定了"失败"还是"永远等待"。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-560dpi")
class ResolvePressureRoutingJvmTest {

    companion object {
        @BeforeClass @JvmStatic fun silenceAutoImport() { MainActivity.LIBRARY_AUTO_IMPORT = false }
    }

    /** 构造真机上真正抛的那个异常：ACW 验证失败，**且明确标注不是限流**。 */
    private fun acwFailure() =
        LanzouCore.DirectRetryException("蓝奏 ACW 验证未完成", 1000L, false)

    /** 构造一个**真的**上游限流异常，用来确认修复没把正常路径一起干掉。 */
    private fun realThrottle() =
        LanzouCore.DirectRetryException("请求过于频繁，请稍后再试", 1000L, true)

    /**
     * **核心回归：ACW 验证失败不许被当成"上游限流"。**
     *
     * 被当成限流 → 走静默重试分支 → 永不回调 → 永远「解析中」。
     * 这一条红了，就意味着用户又要对着永远转的「解析中」干等。
     */
    @Test
    fun anAcwChallengeFailureIsNotUpstreamThrottling() {
        assertFalse(
            "「蓝奏 ACW 验证未完成」是要解 WAF 挑战（换新 cookie），**不是**上游限流。\n" +
                "把它当限流会让解析走进静默重试分支、永不回调 —— 这正是用户从 v1.0.5 报到 v1.0.12 的 bug。",
            DirectLinkResolver.upstreamPressure(acwFailure()),
        )
    }

    /**
     * **别把正常路径一起干掉**：真的限流仍然要判成限流。
     *
     * 这条防的是"矫枉过正" —— 有人为了修上面那条，把整个限流检测删掉，
     * 于是被限流时不再降并发，直接硬撞，反而更容易被封。
     */
    @Test
    fun aRealRateLimitedFailureIsStillThrottling() {
        assertTrue(
            "明确标注 rateLimited=true 的异常必须仍被当成上游限流，否则降并发保护就没了",
            DirectLinkResolver.upstreamPressure(realThrottle()),
        )
    }

    /** 结构化标志必须比文字优先：带 rateLimited=true 但文字里没有限流关键词，也要判成限流。 */
    @Test
    fun theStructuredFlagBeatsTheMessageText() {
        val disguised = LanzouCore.DirectRetryException("出了点问题", 1000L, true)
        assertTrue("rateLimited=true 是结构化信号，比报错文字可信", DirectLinkResolver.upstreamPressure(disguised))
    }

    /**
     * 普通 IOException 里的「验证」二字也不该再触发限流判定。
     *
     * 关键词表里的「验证」已删除。留着它，任何一句带「验证」的网络报错都会被误判。
     */
    @Test
    fun aPlainErrorMentioningVerificationIsNotThrottling() {
        assertFalse(
            "普通异常消息里出现「验证」不该被当成上游限流",
            DirectLinkResolver.upstreamPressure(IOException("蓝奏 ACW 验证未完成")),
        )
    }

    /** 真限流的关键词仍然要认（429 / too many requests / 频率）。 */
    @Test
    fun genuineThrottleKeywordsAreStillRecognised() {
        assertTrue("429 必须仍被认成限流", DirectLinkResolver.upstreamPressure(IOException("HTTP 429")))
        assertTrue("too many requests 必须仍被认成限流", DirectLinkResolver.upstreamPressure(IOException("Too Many Requests")))
        assertTrue("「频率」必须仍被认成限流", DirectLinkResolver.upstreamPressure(IOException("请求频率过高")))
    }

    /**
     * **第二层保险：压力重试必须有限。**
     *
     * 就算将来又冒出一种"文字碰巧命中"的错误，也不许再变成无限重试。
     * 这条是源码级守卫 —— 常量的存在本身就是约束，谁删掉它谁红。
     */
    @Test
    fun thePressureRetryLoopIsBounded() {
        val source = java.io.File(
            repoRoot(),
            "app/src/main/java/cc/nkbr/lanzouplus/DirectLinkResolver.java",
        ).readText()
        assertTrue(
            "压力重试必须有次数上限常量，否则又会退化成「无限重试 + 永不回调」",
            source.contains("MAX_PRESSURE_RETRIES"),
        )
        assertTrue(
            "静默重试分支必须先判上限再进入，不能无条件 return",
            source.contains("pressureRetries<MAX_PRESSURE_RETRIES&&upstreamPressure(error)"),
        )
    }

    private fun repoRoot(): java.io.File {
        var dir = java.io.File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (!java.io.File(dir, "app/src/main/AndroidManifest.xml").isFile && dir.parentFile != null) {
            dir = dir.parentFile!!
        }
        return dir
    }
}
