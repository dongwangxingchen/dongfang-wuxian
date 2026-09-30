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
        assertEquals(
            "versionCode 与 versionName 必须符合编号规则（改了名字没改号 = 用户收不到更新）：" +
                "versionName=${BuildConfig.VERSION_NAME}, versionCode=${BuildConfig.VERSION_CODE}",
            codeFromName(BuildConfig.VERSION_NAME),
            BuildConfig.VERSION_CODE,
        )
    }

    /** 归零后的起点必须是 1.0.0 / 10000（用户 2026-09-30 决定）。 */
    @Test
    fun startsFromOneZeroZero() {
        assertEquals("归零起点应为 1.0.0", "1.0.0", BuildConfig.VERSION_NAME)
        assertEquals("归零起点版本号应为 10000", 10000, BuildConfig.VERSION_CODE)
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
