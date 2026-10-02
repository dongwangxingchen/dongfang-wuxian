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
         * 中间那段历史（DFW-97「测试期 versionName 固定 1.0.0」）已作废：
         * 那是拿"版本名不动、序号手写"顶了一阵，代价就是序号必然忘改 ——
         * 已经实际出过一次（DFW-117：后台填 10028 而手机装着 10034，更新弹窗永远不出现）。
         *
         * [DFW-118 2026-10-02] 用户选路线 B，于是这条**又成立**了，而且比原来更强。
         * 原话：
         * > 「版本序号能不能和版本号一样？都是 1.0.0，然后 1.0.1，这样不断叠加下去？」
         * > 「路线 B 吧，底子打得好是很好的。」
         *
         * 现在 `versionName` 是**唯一数字源**，`versionCode` 由它推导
         * （见 `app/build.gradle.kts` 里 `appVersionName.split(".")` 那段），
         * 所以两者必须严丝合缝 —— 反算得出来才算对。
         */
        assertEquals(
            "versionCode 必须等于由 versionName 推出的值（路线 B：versionName 是唯一数字源）：" +
                "versionName=${BuildConfig.VERSION_NAME} versionCode=${BuildConfig.VERSION_CODE}",
            codeFromName(BuildConfig.VERSION_NAME),
            BuildConfig.VERSION_CODE,
        )
    }

    /**
     * 新方案（路线 B）下**已发布过的最高版本名**。
     *
     * **这一行只许往上改**：它是"不许悄悄把版本调小"的最后一道闸。
     * 调小了安卓会把新包当降级、直接拒绝安装（提示「应用未安装」），界面上完全看不出来 ——
     * 这是最阴的一类事故，也正是 DFW-117 那次"发了版却不弹更新"的同一个根。
     *
     * ⚠️ 切换路线 B 的第一版（1.0.0 = 10000）**比用户机上装的旧计数器 10034 小**，
     * 必须卸载重装一次 —— 用户已知情并同意（「我完全不怕重新安装一个呀」）。
     * 这一行记的是**新方案**的已发布版本，别再混进旧计数器的数字。
     */
    private val highestReleased = "1.0.0"

    /** 版本只许往上走 —— 用户机器上装着的版本永远不能比新包更高。 */
    @Test
    fun neverGoesBackwards() {
        val floor = codeFromName(highestReleased)
        assertTrue(
            "版本不许低于已发布过的 ${highestReleased}（内部序号 ${floor}）：" +
                "实际 ${BuildConfig.VERSION_NAME} / ${BuildConfig.VERSION_CODE}",
            BuildConfig.VERSION_CODE >= floor,
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
