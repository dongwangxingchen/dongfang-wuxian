package cc.nkbr.lanzouplus

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * [DFW-103] 蓝奏可信域名白名单守卫 —— 本次"下载全挂"的真根因。
 *
 * ## 故障
 * 用户从 v1.0.5 报到 v1.0.14 都下不了，报错「该源已失效或跳转异常」。
 *
 * ## 真根因（用真实分享链接取证）
 * 蓝奏**把下载 API 迁到了新域名**：
 * ```
 * https://api.ilanzou.com/unproved/pd/url?id=...&token=...&type=2
 * ```
 * 而旧白名单正则 `(?:lanzou[a-z0-9]?|lanzov|lanzn)[.]com` **匹配不到 `ilanzou.com`**
 * （标签是 `ilanzou`，不以 `lanzou` 开头），于是 `requireLanzouPage()` 判定
 * "跳转到了不受信任的地址"并抛错 —— 用户看到的就是「该源已失效或跳转异常」。
 *
 * **这不是我们改坏的，是官方迁移了域名。** 用户"以前能下载现在咋不行了"的判断是对的。
 *
 * ## 这条测试守两件事
 * 1. `ilanzou.com` 及其子域**必须放行**（否则下载链路再断）；
 * 2. **不能为了放行而写成宽松匹配** —— 钓鱼域名必须继续被拒。
 *    白名单一旦写成 `.*lanzou.*` 之类，`lanzou.com.evil.com` 这种就会被放过。
 */
class LanzouHostAllowlistJvmTest {

    private val root: File = run {
        var dir = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (!File(dir, "AGENTS.md").isFile && dir.parentFile != null) dir = dir.parentFile
        dir
    }

    private fun pattern(): Regex {
        val src = File(root, "app/src/main/java/cc/nkbr/lanzouplus/LanzouCore.java").readText(Charsets.UTF_8)
        val m = Regex("LANZOU_HOST=Pattern\\.compile\\(\"([^\"]+)\"\\)").find(src)
        assertTrue("找不到 LANZOU_HOST 正则，测试需要更新", m != null)
        // Java 的 matches() = 整串匹配，所以这里用 fullmatch 语义
        return Regex(m!!.groupValues[1].replace("(?i)", ""), RegexOption.IGNORE_CASE)
    }

    private fun allowed(host: String) = pattern().matches(host)

    @Test
    fun ilanzouIsAllowed_theDomainThatBrokeDownloads() {
        assertTrue(
            "api.ilanzou.com 必须放行 —— 蓝奏已把下载 API 迁到这里，" +
                "不放行会让所有下载报「该源已失效或跳转异常」（真机已复现）",
            allowed("api.ilanzou.com"),
        )
        assertTrue("ilanzou.com 主域也要放行", allowed("ilanzou.com"))
    }

    @Test
    fun theClassicLanzouDomainsStayAllowed() {
        for (host in listOf(
            "www.lanzoux.com", "oreojiang.lanzout.com", "www.lanzouw.com",
            "www.lanzoup.com", "www.lanzouo.com", "www.lanzouz.com", "lanzou.com", "lanzov.com",
        )) {
            assertTrue("$host 应当继续放行（既有线路不能因为这次修改被误伤）", allowed(host))
        }
    }

    @Test
    fun lookalikeDomainsAreStillRejected() {
        // 放行 ilanzou 时最容易写成宽松匹配；这几条就是防那个的
        for (host in listOf(
            "evil.com",
            "lanzou.com.evil.com",   // 前缀伪装
            "ilanzou.com.evil.com",
            "notlanzou.com",
            "evil-ilanzou.com",
            "lanzou.evil.com",
        )) {
            assertFalse("$host 不是蓝奏域名，必须继续拒绝（白名单不能为了放行新域名而放松）", allowed(host))
        }
    }

    /** 反向探针：确认这条守卫**真的能红**。 */
    @Test
    fun theGuardActuallyDetectsTheOldBrokenPattern() {
        val old = Regex("(?i)(?:[a-z0-9-]+[.])*(?:lanzou[a-z0-9]?|lanzov|lanzn)[.]com", RegexOption.IGNORE_CASE)
        assertFalse(
            "旧正则必须匹配不到 api.ilanzou.com —— 这正是当初下载全挂的原因（否则本测试没意义）",
            old.matches("api.ilanzou.com"),
        )
    }
}
