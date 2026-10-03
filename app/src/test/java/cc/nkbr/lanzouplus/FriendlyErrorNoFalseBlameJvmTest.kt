package cc.nkbr.lanzouplus

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * [DFW-36] **不许再把「源」当成坏人说出去。**
 *
 * ## 这条测试守的是什么
 *
 * `MainActivity.friendlyError()` 是全站唯一的「异常 → 给人看的话」出口，47 个调用点都从那里过。
 * 它里面原本有一条：
 *
 * ```
 * if(raw.contains("不受信任")||raw.contains("跳转")) return "该源已失效或跳转异常";
 * ```
 *
 * 这句话在 v1.0.5 → v1.0.14 期间**被证伪过四次**，四次没有一次是源的问题：
 *
 * | 真根因 | 出处 |
 * |---|---|
 * | 官方把下载 API 迁到 `api.ilanzou.com`，白名单正则漏了 | DFW-103 |
 * | 上一轮修复的 http 降级被「只认 https」当场拒掉 | DFW-104 |
 * | 域名池里 `wwc.lanzoux.com` 的证书过期 | DFW-88 |
 * | 阿里云 WAF 滑块页 | DFW-107 |
 *
 * 代价不是「话说错了」这么轻：**用户看到「源已失效」会去删源，而源是好的。**
 * 所以这不是文案偏好问题，是会造成数据损失的错误结论。
 *
 * 详见 `docs/research/dfw36-失效源识别方案.md`（§1.4 四次证伪、§3.2 文案红线）。
 */
class FriendlyErrorNoFalseBlameJvmTest {

    private val root: File = run {
        var dir = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (!File(dir, "AGENTS.md").isFile && dir.parentFile != null) dir = dir.parentFile
        dir
    }

    private fun main() =
        File(root, "app/src/main/java/cc/nkbr/lanzouplus/MainActivity.java").readText(Charsets.UTF_8)

    /** 按花括号配对取方法体（**不靠空行猜边界**）。 */
    private fun bodyOf(src: String, signature: String): String {
        val start = src.indexOf(signature)
        assertTrue("找不到方法：$signature（测试需要更新）", start >= 0)
        val open = src.indexOf('{', start)
        var depth = 0
        for (i in open until src.length) {
            when (src[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return src.substring(open, i + 1)
                }
            }
        }
        error("$signature 花括号不配对")
    }

    /**
     * 从方法体里抠出**所有中文字符串字面量**。
     *
     * ⚠️ 只取字面量、不取整段源码 —— 因为这段历史的注释里**必须**留着「该源已失效」这几个字
     * （它们是证据），整段匹配会把注释判成违规，测试就废了。
     */
    private fun chineseLiterals(body: String): List<String> =
        Regex("\"([^\"]*)\"").findAll(body)
            .map { it.groupValues[1] }
            .filter { it.any { ch -> ch.code in 0x4E00..0x9FFF } }
            .toList()

    /**
     * 这条文案有没有把锅甩给「源」。
     *
     * ⚠️ **必须先排除「明确否认」的写法。**
     * 正确的新文案是「…请稍后重试。**不是这个源坏了**」—— 它同样含「源」和「坏了」两个词，
     * 朴素的子串匹配会把它判成违规（这个坑是写完测试跑第一遍时踩到的）。
     * 所以这里先看有没有否认语，再判有没有甩锅。
     */
    private fun blamesTheSource(message: String): Boolean {
        if (message.contains("不是这个源") || message.contains("不是源")) return false
        return message.contains("源") && (message.contains("失效") || message.contains("坏了"))
    }

    @Test
    fun friendlyErrorNeverBlamesTheSourceForAnAddressProblem() {
        val body = bodyOf(main(), "String friendlyError(Throwable error){")
        val offenders = chineseLiterals(body).filter(::blamesTheSource)
        assertTrue(
            "friendlyError 是 47 个调用点的公共出口，它说的每一句话都会被当成结论。" +
                "不许出现「源 + 失效/坏了」这种把地址层故障甩给源的说法 —— " +
                "这句话被证伪过四次，而用户看到它会去删源。违规文案：$offenders",
            offenders.isEmpty(),
        )
    }

    @Test
    fun theUntrustedBranchSaysWhatActuallyMightBeWrong() {
        val body = bodyOf(main(), "String friendlyError(Throwable error){")
        assertTrue(
            "「不受信任 / 跳转」这一支必须说明可能是蓝奏换了地址或加了安全校验 —— " +
                "那才是四次证伪里反复出现的真根因",
            body.contains("不受信任") && body.contains("蓝奏换了链接地址"),
        )
        assertTrue(
            "文案必须让用户知道**可以不删源**（这是本卡最想防的后果）",
            chineseLiterals(body).any { it.contains("不是这个源") },
        )
    }

    /* ───────────────────────── 鉴别力 ───────────────────────── */

    /**
     * 喂进改动前的原文必须被判成违规 —— 否则上面两条是假绿。
     */
    @Test
    fun theCheckActuallyRejectsTheOldMessage() {
        val old = """if(raw.contains("不受信任")||raw.contains("跳转"))return"该源已失效或跳转异常";"""
        val oldLiterals = chineseLiterals(old)
        assertTrue(
            "改动前那句必须被判成违规（否则本测试没有鉴别力）",
            oldLiterals.any(::blamesTheSource),
        )

        // 另一种甩锅写法也要能抓到
        assertTrue("「源坏了」这种写法同样要抓到", blamesTheSource("这个源坏了"))
        assertTrue("「源已失效」要抓到", blamesTheSource("该源已失效"))

        // 不该误伤的：这两句说的是**分享/直链**，不是软件源
        assertFalse(
            "「分享已失效或文件已删除」说的是用户打开的那个分享，不是软件源，不许误伤",
            blamesTheSource("分享已失效或文件已删除"),
        )
        assertFalse(
            "「直链已失效，正在重新解析」说的是直链过期（会自愈），不是软件源，不许误伤",
            blamesTheSource("直链已失效，正在重新解析"),
        )
        // 明确否认的写法不能算甩锅（新文案就是这个形状）
        assertFalse(
            "「不是这个源坏了」是**否认**，不是甩锅 —— 朴素子串匹配会误判，这条钉住它",
            blamesTheSource("蓝奏换了链接地址或加了安全校验，请稍后重试。不是这个源坏了"),
        )
    }
}
