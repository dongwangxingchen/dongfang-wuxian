package me.rerere.rikkahub.dfwx

import me.rerere.rikkahub.data.datastore.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * [DFWX] DFW-20（README 承诺与实际一致）收尾守卫。
 *
 * README 写着"**无广告、无追踪、不申请多余权限，所有核心功能开箱即用**"。
 * 这张卡要求"等 DFW-7/9/10 落地后统一核对，把承诺改成与实现一致的实话（或反过来改实现）"。
 * 那三张卡已完成，这里把核对结论钉死：
 *
 * | 承诺 | 现在的实际 | 结论 |
 * |---|---|---|
 * | 无追踪 | Firebase Analytics 已整链移除（DFW-9），包内无广告/归因权限 | ✅ 属实 |
 * | 不申请多余权限 | 明文已收敛（DFW-10）；存储/安装权限按需申请并在 README 说明 | ✅ 属实（已说明用途） |
 * | 核心功能开箱即用 | 更新检查已复活（DFW-7） | ✅ 属实 |
 * | （新发现）局域网 Web 控制台 | 上游默认监听 `0.0.0.0` + JWT 关闭 | ⚠️ 已改为**默认仅本机** |
 */
class TelemetryAndExposureTest {

    private val repoRoot: File = run {
        var dir = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir.parentFile != null && !File(dir, "app/src/main/java/cc/nkbr/lanzouplus/MainActivity.java").isFile) {
            dir = dir.parentFile
        }
        dir
    }

    /** 对外承诺"无追踪"：代码里不得再有遥测 SDK 的调用点。 */
    @Test
    fun noTelemetrySdkCallsRemain() {
        val offenders = mutableListOf<String>()
        File(repoRoot, "rikkahub").walkTopDown()
            .filter { it.isFile && (it.name.endsWith(".kt") || it.name.endsWith(".kts")) }
            .filterNot { it.path.contains("/build/") }
            // 排除本测试自身：它当然含 "logEvent(" 这些字面量（第一版就因此自匹配假红）
            .filterNot { it.name == "TelemetryAndExposureTest.kt" }
            .forEach { file ->
                val text = file.readText(Charsets.UTF_8)
                // 排除补丁说明注释行（会逐字引用被移除的代码以便同步上游时重放）
                val codeLines = text.lines().filter { !it.trimStart().startsWith("//") && !it.trimStart().startsWith("*") }
                for (line in codeLines) {
                    if (line.contains("logEvent(") || line.contains("FirebaseAnalytics")) {
                        offenders.add("${file.relativeTo(repoRoot)}: ${line.trim().take(90)}")
                    }
                }
            }
        assertTrue("不得再有任何遥测上报调用（README 承诺'无追踪'）：\n${offenders.joinToString("\n")}", offenders.isEmpty())
    }

    /** Firebase Analytics 依赖必须不在（BOM 保留是允许的，见 PATCHES P32）。 */
    @Test
    fun firebaseAnalyticsDependencyIsRemoved() {
        val build = File(repoRoot, "rikkahub/app/build.gradle.kts").readText(Charsets.UTF_8)
        val implLines = build.lines().filter { it.trimStart().startsWith("implementation(") }
        assertTrue(
            "不得再引入 firebase-analytics 依赖：\n${implLines.filter { it.contains("analytics") }.joinToString("\n")}",
            implLines.none { it.contains("firebase.analytics") },
        )
    }

    /** 局域网 Web 控制台必须**默认仅本机**（上游默认 0.0.0.0 + 无鉴权 = 明显暴露面）。 */
    @Test
    fun webConsoleDefaultsToLocalhostOnly() {
        assertEquals(
            "Web 控制台必须默认仅本机监听（用户需要时可在设置里显式开放到局域网）",
            true,
            Settings(init = true).webServerLocalhostOnly,
        )
    }

    @Test
    fun webConsoleIsOptIn_notRunningByDefault() {
        assertFalse(
            "Web 控制台必须默认关闭（用户主动开启才启动服务）",
            Settings(init = true).webServerEnabled,
        )
    }

    /** README 的承诺必须仍是"有依据的实话"：不得出现被移除能力的旧说法。 */
    @Test
    fun readmeClaimsStillMatchImplementation() {
        val readme = File(repoRoot, "README.md").readText(Charsets.UTF_8)
        // 承诺本身可以留（现在属实），但不得再出现已被移除能力的描述
        assertFalse("README 不得再写'预置公共体验渠道'（AI-004 已移除内置 Key）",
            readme.contains("预置一个**有限额的公共体验渠道**"))
        assertFalse("README 不得再声称手表可用（decisions #13）",
            readme.contains("手机与手表都好用"))
        assertTrue("README 应说明不内置 Key", readme.contains("不内置任何 API Key"))
    }
}
