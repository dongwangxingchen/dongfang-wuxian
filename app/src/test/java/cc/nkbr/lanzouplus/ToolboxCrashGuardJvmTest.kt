package cc.nkbr.lanzouplus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [2026-10-03 bug 排查] 工具箱两个**主线程崩溃**的回归守卫。
 *
 * 这两条是队友深审时找到的，我逐行回源码复核后确认成立，然后在这里钉死。
 * 它们共同的特征是：**异常本身可以确定，但只在特定输入下触发** ——
 * 所以手点很可能点不出来，而用户粘贴一次就崩。
 *
 * ## BUG-1：时间戳输入框，粘贴 20 位数字 → 主线程崩
 * `Toolbox.java` 的 `timestampAuto` 里 `Long.parseLong(v)` 原来**裸着没有 try/catch**，
 * 而唯一调用点在 `ToolHost.java` 的 `TextWatcher.afterTextChanged` ——
 * **主线程、每敲一个字符跑一次**。20 位数字超出 long → `NumberFormatException` 直抛主线程。
 * 旁证这是漏写而非设计：同一方法的兄弟分支 `dateToStamp` **是有 try/catch 的**。
 *
 * ## BUG-2：随机数，下限 0 上限 2147483647 → 主线程崩
 * `span = max - min + 1` 用 **int** 算，`0..Integer.MAX_VALUE` 的真实跨度是 2^31，
 * 溢出成负数 → `SecureRandom.nextInt(span)` 抛 `IllegalArgumentException: bound must be positive`。
 * **反直觉点**：勾了「去重」反而不崩（`count > span` 在 span 为负时恒真，提前返回提示串），
 * 所以崩的恰恰是**默认那条路径**。上限框带 `TYPE_NUMBER_FLAG_SIGNED`，10 位数输得进去。
 *
 * 这两条都是纯逻辑，不需要 Robolectric —— 用最轻的跑法就能守住。
 */
class ToolboxCrashGuardJvmTest {

    // ── BUG-1：时间戳 ────────────────────────────────────────────────

    /**
     * 核心不变量：**无论用户往输入框里粘贴什么，`timestampAuto` 都不许抛。**
     *
     * 它跑在 `TextWatcher` 里，抛 = 用户正在打字时整个界面崩掉。
     * 所以这里覆盖的不是"某个数字"，而是**一整类输入**：超长、边界、全 9、非数字。
     */
    @Test
    fun timestampAutoNeverThrows_noMatterWhatIsPasted() {
        val inputs = listOf(
            "9999999999999999999",      // 19 位全 9 —— 超过 Long.MAX_VALUE(9223372036854775807)，照样溢出
            "12345678901234567890",     // 20 位
            "9".repeat(64),             // 极端超长
            "9223372036854775807",      // Long.MAX_VALUE，合法边界
            "9223372036854775808",      // Long.MAX_VALUE + 1，刚好越界
            "0", "-1", "0000",          // 零与负号（负号不匹配 \d+，会走日期分支）
            "  1700000000  ",           // 前后空格
            "", "   ",                  // 空
            "abc", "2026-10-03",        // 普通文本 / 日期
            "2026-13-45",               // 非法日期
            "2026-10-03 08:54:09",      // 合法日期
            "١٢٣٤٥",                    // 阿拉伯数字（不应被 \d+ 之外的规则搞崩）
        )
        for (input in inputs) {
            val out = try {
                Toolbox.timestampAuto(input)
            } catch (t: Throwable) {
                throw AssertionError(
                    "输入 " + input.take(30) + " 时抛了 " + t.javaClass.name + " —— " +
                        "这个方法跑在 TextWatcher 里（主线程），抛 = 用户打字时崩溃",
                    t,
                )
            }
            assertTrue("输入 " + input.take(20) + " 应当有返回文案，实际为空", out.isNotEmpty())
        }
    }

    /** 超长数字要给**能照着改**的提示，而不是空串或"格式错误"这种误导性文案。 */
    @Test
    fun tooLongTimestampGetsAnActionableHintNotAGenericError() {
        val out = Toolbox.timestampAuto("99999999999999999999999")
        assertTrue(
            "超长数字的提示里应当写清上限，实际：$out",
            out.contains("9223372036854775807"),
        )
        assertFalse("不该说成日期格式错误，会把人带偏：$out", out.contains("2000-06-15"))
    }

    /**
     * 合法输入必须仍然正常工作 —— 修崩溃不能把功能修坏。
     *
     * ## ⚠️ 必须钉住时区，否则「本地绿、CI 红」
     *
     * `Toolbox.stampToDate` 用的是 `SimpleDateFormat`，它走**运行环境的默认时区**。
     * 而 1700000000 秒这个瞬间：
     *
     * | 时区 | 换算结果 |
     * |---|---|
     * | Asia/Shanghai (UTC+8) | 2023-11-15 06:13:20 |
     * | UTC | **2023-11-14 22:13:20** |
     *
     * 原来这里直接断言 `contains("2023-11-15")` —— 开发机是 UTC+8 所以一直绿，
     * GitHub Actions 的 runner 是 UTC，于是**每次推送 CI 都红**，
     * 而且红在一个和本次改动毫无关系的用例上（实测 CI 日志：
     * `979 tests completed, 1 failed`）。
     *
     * 修法不是把断言改宽（那等于不测了），而是**显式钉住时区**：
     * 断言保持精确，跑在哪个时区结果都一样。用完立刻还原，不污染其它用例。
     */
    @Test
    fun validTimestampsStillConvertCorrectly() {
        val original = java.util.TimeZone.getDefault()
        try {
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Shanghai"))
            // 1700000000 秒 = 2023-11-15 06:13:20 (UTC+8)，周二
            val out = Toolbox.timestampAuto("1700000000")
            assertTrue("合法秒级时间戳应当正常换算，实际：$out", out.contains("2023-11-15"))
            assertTrue("应当带上星期，实际：$out", out.contains("星期：") || out.contains("周"))
            assertTrue("应当给出 ISO 8601 形式，实际：$out", out.contains("2023-11-15T06:13:20"))
        } finally {
            java.util.TimeZone.setDefault(original)
        }
    }

    // ── BUG-2：随机数 ────────────────────────────────────────────────

    /**
     * 核心不变量：**跨度溢出时不许崩，也不许静默给出错误结果。**
     *
     * `0 .. Integer.MAX_VALUE` 是用户真能输进去的组合（上限框允许 10 位数），
     * 而它正好是 int 溢出的临界点。
     */
    @Test
    fun randomNumbersNeverThrowsOnHugeOrOverflowingRanges() {
        val cases = listOf(
            // (min, max, count, unique, sort, 说明)
            intArrayOf(0, Int.MAX_VALUE, 1, 0, 0) to "默认路径：不勾去重 —— 这是原来崩的那条",
            intArrayOf(0, Int.MAX_VALUE, 1, 1, 0) to "勾了去重（原来靠 count>span 侥幸不崩）",
            intArrayOf(Int.MIN_VALUE, Int.MAX_VALUE, 1, 0, 0) to "全 int 范围，跨度 2^32",
            intArrayOf(-1, Int.MAX_VALUE, 5, 0, 0) to "负下限 + 上限",
            intArrayOf(0, Int.MAX_VALUE, 200, 0, 2) to "上限个数的批量",
        )
        for ((c, why) in cases) {
            val out = try {
                Toolbox.randomNumbers(c[0], c[1], c[2], c[3] == 1, c[4])
            } catch (t: Throwable) {
                throw AssertionError(
                    "$why（min=${c[0]} max=${c[1]} count=${c[2]} unique=${c[3] == 1}）抛了 " +
                        t.javaClass.name + ": " + t.message,
                    t,
                )
            }
            assertTrue("$why 应当返回文案，实际为空", out.isNotEmpty())
        }
    }

    /** 区间真的太大时，要**明说太大**（而不是崩、也不是悄悄给个错范围的结果）。 */
    @Test
    fun overflowingRangeIsRejectedWithAClearMessage() {
        val out = Toolbox.randomNumbers(0, Int.MAX_VALUE, 1, false, 0)
        assertTrue(
            "区间溢出时应当明确告知区间太大，实际：$out",
            out.contains("区间太大"),
        )
    }

    /** 正常区间必须照常工作，而且结果真的落在区间内。 */
    @Test
    fun normalRangesStillProduceInRangeNumbers() {
        val out = Toolbox.randomNumbers(1, 6, 3, false, 0)
        val nums = out.split(Regex("[\\s\\n]+")).filter { it.isNotBlank() }.map { it.toInt() }
        assertEquals("应当生成 3 个数，实际：$out", 3, nums.size)
        for (n in nums) {
            assertTrue("生成的 $n 超出了 1..6", n in 1..6)
        }
    }

    /** 去重模式仍须真的去重（修跨度不能把去重修坏）。 */
    @Test
    fun uniqueModeStillDeduplicates() {
        val out = Toolbox.randomNumbers(1, 5, 5, true, 1)
        val nums = out.split(Regex("[\\s\\n]+")).filter { it.isNotBlank() }.map { it.toInt() }
        assertEquals("1..5 取 5 个去重，应当正好 5 个", 5, nums.size)
        assertEquals("去重后不该有重复值：$nums", 5, nums.toSet().size)
    }

    /** 上限小于下限仍然走原来的提示，不要因为新加的分支把它吞掉。 */
    @Test
    fun reversedBoundsStillReportTheOldMessage() {
        assertEquals("上限需不小于下限", Toolbox.randomNumbers(10, 1, 1, false, 0))
    }

    // ── P0：测试里不许偷偷联网 ─────────────────────────────────────

    /**
     * [DFW-124 第二条联网路径] **单元测试里 `LIBRARY_AUTO_IMPORT` 必须是 false。**
     *
     * 这个开关原来写死 `= true`，靠 44 个测试类各自在 `@BeforeClass` 里关掉。
     * 实测有 5 个类会启动完整 `MainActivity.setup()` 却没关它，而
     * `app/build.gradle.kts` 的 `forkEvery = 24` 让它们**白蹭**同批前面类的 false ——
     * 全量跑看不出来，**单跑（`tools/run-tests.sh --class X`）就真发 85 个源的网络请求**。
     *
     * 现在默认值取唯一判据 `App.isJvmUnitTest()`，这条用例把它钉死：
     * 谁改回 `= true`（或改用别的判据），立刻红。
     */
    @Test
    fun libraryAutoImportIsOffInsideUnitTests() {
        assertTrue(
            "这个用例必须跑在 JVM 单测里（Robolectric 在 classpath 上）才有意义",
            App.isJvmUnitTest(),
        )
        assertFalse(
            "单元测试里 LIBRARY_AUTO_IMPORT 必须为 false —— 否则 MainActivity 启动时会对 " +
                "85 个内置源发真实网络探测（DFW-124 的第二条联网路径）",
            MainActivity.LIBRARY_AUTO_IMPORT,
        )
    }
}
