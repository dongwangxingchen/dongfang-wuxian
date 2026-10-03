package cc.nkbr.lanzouplus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * [DFW-36] **搜索失败时，要说出是哪个源，而不是只报一个数字。**
 *
 * ## 这条测试守的是什么
 *
 * `Models.java:131` 的 `onFailure(String current)` 把**源标题**传了出来
 * （`LanzouCore` 里传的是 `state.source.title`），
 * 但 `MainActivity` 的回调**只做了 `failures++`，那个参数从头到尾没被用过**。
 *
 * 后果：全源搜索里挂了几个源时，用户只看到状态行一个「已完成 · 3 源异常」——
 * 不知道是哪个源、不知道为什么、点不开、重试不了。
 * **而源名一直在手里，只是被丢掉了。**
 *
 * 这一条不需要新探测、不需要网络，纯粹是把已有的证据接上。
 *
 * 详见 `docs/research/dfw36-失效源识别方案.md` §1.2。
 */
class SearchFailedSourceNamesJvmTest {

    private val root: File = run {
        var dir = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (!File(dir, "AGENTS.md").isFile && dir.parentFile != null) dir = dir.parentFile
        dir
    }

    private fun main() =
        File(root, "app/src/main/java/cc/nkbr/lanzouplus/MainActivity.java").readText(Charsets.UTF_8)

    private fun names(vararg n: String) = LinkedHashSet(n.toList())

    /* ───────────── 后缀的取舍（纯函数，直接喂数据） ───────────── */

    @Test
    fun noFailuresMeansNoSuffix() {
        assertEquals("一个源都没挂时不该多出任何字", "", MainActivity.failedSourceSuffix(names()))
        assertEquals("null 也要安全", "", MainActivity.failedSourceSuffix(null))
        assertEquals("全空白不该显示成空名字", "", MainActivity.failedSourceSuffix(names("", "  ")))
    }

    @Test
    fun oneOrTwoNamesAreListedInFull() {
        assertEquals("只挂一个就说一个", "：某某软件库", MainActivity.failedSourceSuffix(names("某某软件库")))
        assertEquals(
            "挂两个就都说出来（第一个是最先失败的，通常是根因）",
            "：甲源、乙源",
            MainActivity.failedSourceSuffix(names("甲源", "乙源")),
        )
    }

    /**
     * 状态行是**一行文字**，塞不下十几个源名。
     * 历史上有一次 UA 被拉黑，**84 个源一起挂**——全列出来会把状态行撑爆，反而什么都看不清。
     */
    @Test
    fun manyNamesAreCappedButStillCarryTheCount() {
        val many = names("甲源", "乙源", "丙源", "丁源", "戊源")
        val suffix = MainActivity.failedSourceSuffix(many)
        assertTrue("必须给出总数，否则用户不知道规模：$suffix", suffix.contains("5"))
        assertFalse("不许把名字全列出来（状态行只有一行）：$suffix", suffix.contains("戊源"))
        assertTrue("前两个必须显示（最先失败的最可能是根因）：$suffix", suffix.contains("甲源") && suffix.contains("乙源"))
    }

    @Test
    fun duplicatesAreCountedOnce() {
        // LinkedHashSet 天然去重；这里钉住「同一个源失败多次只算一次」
        val repeated = names("甲源")
        repeated.add("甲源")
        assertEquals("同一个源失败两次不该显示两遍", "：甲源", MainActivity.failedSourceSuffix(repeated))
    }

    /* ───────────── 接线守卫（源码级） ───────────── */

    private fun bodyOf(src: String, signature: String): String {
        val start = src.indexOf(signature)
        assertTrue("找不到：$signature（测试需要更新）", start >= 0)
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
     * **本卡的核心断言**：`onFailure` 必须真的把源名留下来。
     *
     * 改动前它只写 `globalSearch.failures++`，参数 `source` 从未出现 ——
     * 所以这条断言在改动前会失败。
     */
    @Test
    fun onFailureActuallyKeepsTheSourceName() {
        val src = main()
        val sig = "@Override public void onFailure(String source){"
        val body = bodyOf(src, sig)
        assertTrue(
            "onFailure 必须把源名记下来（failedSources.add），" +
                "否则用户永远只能看到一个「N 源异常」的数字 —— 而这正是 DFW-36 的真根因",
            body.contains("failedSources.add("),
        )
        assertTrue("仍然要计数", body.contains("failures++"))
    }

    @Test
    fun theStatusLineActuallyUsesTheSuffix() {
        val src = main()
        assertTrue(
            "状态行必须真的用上 failedSourceSuffix —— " +
                "只在别处定义了函数却没接线，等于没做（本仓踩过这种假绿）",
            src.contains("源异常\"+failedSourceSuffix("),
        )
    }
}
