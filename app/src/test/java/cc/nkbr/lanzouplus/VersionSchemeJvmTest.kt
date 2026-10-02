package cc.nkbr.lanzouplus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [DFWX] DFW-56 版本号方案守卫。
 *
 * ## 为什么需要这个测试
 * 发版时最容易犯的错是**只改了 `versionName`、忘了改 `versionCode`**。
 * 后果很隐蔽：安装包看着是"1.0.1"，但安卓认为它和 1.0.0 是同一个版本
 * → **用户永远收不到更新提示**，而你从界面上完全看不出来。
 *
 * 反过来，只改 `versionCode` 忘改 `versionName`，用户看到的版本号就会与实际不符。
 *
 * 所以这里把**编号规则**钉死：`versionCode = major*10000 + minor*100 + patch`。
 *
 * ## 背景（2026-09-30 用户决定）
 * 软件整体**归零为 1.0.0**，之后的更新都基于此。
 * v1.2.x–v1.22.x（versionCode 1039039）全部转入 GitHub 预发布作"测试专区"存档。
 */
class VersionSchemeJvmTest {

    /** 由 versionName 按规则推出版本号。 */
    private fun codeFromName(name: String): Int {
        val parts = name.split(".")
        assertEquals("版本名必须是 x.y.z 三段：$name", 3, parts.size)
        val (major, minor, patch) = parts.map { it.toInt() }
        return major * 10000 + minor * 100 + patch
    }

    @Test
    fun versionNameAndCodeAgree() {
        /*
         * [DFW-97] 规则变了 —— 用户 2026-10-02：
         * > 「改成 1.0.0 版本啊，我还没发布正式版呢，咱们做的一直是测试版，
         * >   所以一直改为 1.0.0 好吧？」
         *
         * 于是 versionName 固定 "1.0.0"（给人看的），versionCode 继续递增（给安卓看的）。
         * **原来那条"versionName 必须能反算出 versionCode"的断言随之作废** ——
         * 它编码的是旧规则，继续留着只会逼人把 versionName 改回去。
         *
         * 现在真正要守的不变量换成了：**versionCode 只增不减**。
         * 理由：安卓靠 versionCode 判断"能不能覆盖安装"。
         * 一旦它比用户机器上的小，新包会被当成降级、直接拒绝安装（提示"应用未安装"），
         * 而界面上完全看不出来 —— 这是最阴的一类事故。
         */
        assertEquals(
            "测试期 versionName 固定为 1.0.0（用户 2026-10-02 指定）：实际 ${BuildConfig.VERSION_NAME}",
            "1.0.0",
            BuildConfig.VERSION_NAME,
        )
        assertTrue(
            "versionCode 必须 ≥ 归零起点 10000：实际 ${BuildConfig.VERSION_CODE}",
            BuildConfig.VERSION_CODE >= 10000,
        )
        // 已发布过的最高内部码（v1.0.27 = 10027）。**这一行只许往上改**：
        // 它是"不许悄悄把内部码调小"的最后一道闸 —— 调小了用户就装不上。
        assertTrue(
            "versionCode 不许低于已发布过的最高值 10027（调小 = 用户覆盖安装会被系统拒绝）：" +
                "实际 ${BuildConfig.VERSION_CODE}",
            BuildConfig.VERSION_CODE >= 10027,
        )
    }

    /** 归零后的起点必须是 1.0.0 / 10000（用户 2026-09-30 决定）。 */
    @Test
    fun startsFromOneZeroZero() {
        /*
         * 2026-09-30 归零：从 1.0.0 / 10000 重新计数（见 docs/plan/current-state.md §1）。
         *
         * **这里不能写死当前版本。** 旧版断言的是 `"1.0.0"` / `10000` 两个字面量，
         * 等于"永远不许升版本" —— 2026-10-01 升到 1.0.2 时本类立刻变红。
         * 该守的是**不变量**：归零之后内部码只许往上走。
         * （"版本号与版本名一一对应"那条随 DFW-97 作废：用户要求测试期 versionName 固定 1.0.0。）
         */
        assertTrue(
            "归零起点是 1.0.0 / 10000，之后只许往上走：当前 ${BuildConfig.VERSION_NAME} / ${BuildConfig.VERSION_CODE}",
            BuildConfig.VERSION_CODE >= 10000,
        )
        assertTrue(
            "测试期 versionName 固定 1.0.0（DFW-97，用户 2026-10-02 指定）：实际 ${BuildConfig.VERSION_NAME}",
            BuildConfig.VERSION_NAME == "1.0.0",
        )
        val major = BuildConfig.VERSION_NAME.substringBefore('.').toInt()
        assertTrue("major 至少是 1（换 major 意味着又一次重排，要显式决定）", major >= 1)
    }

    /** 规则本身要自洽：递增的版本名必须给出递增的版本号（否则更新检测会错乱）。 */
    @Test
    fun schemeIsMonotonic() {
        val samples = listOf("1.0.0", "1.0.1", "1.0.9", "1.1.0", "1.9.9", "2.0.0")
            .map { it to codeFromName(it) }
        for (i in 1 until samples.size) {
            assertTrue(
                "版本号必须随版本名单调递增：${samples[i - 1]} (${samples[i - 1].second}) " +
                    "-> ${samples[i]} (${samples[i].second})",
                samples[i].second > samples[i - 1].second,
            )
        }
    }

    /** 进位不能撞车：1.0.99 不该越过 1.1.0 太多，各段必须互不干扰。 */
    @Test
    fun schemeHasNoCarryCollision() {
        val cases = mapOf(
            "1.0.0" to 10000, "1.0.1" to 10001, "1.0.99" to 10099,
            "1.1.0" to 10100, "1.1.1" to 10101, "1.99.99" to 19999, "2.0.0" to 20000,
        )
        for ((name, expected) in cases) {
            assertEquals("$name 应换算为 $expected", expected, codeFromName(name))
        }
    }
}
