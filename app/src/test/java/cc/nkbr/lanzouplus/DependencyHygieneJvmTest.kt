package cc.nkbr.lanzouplus

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * [DFWX] DFW-30 依赖可复现性守卫。
 *
 * 背景（审计 B-1）：`rikkahub/gradle/libs.versions.toml` 里
 * `com.github.rikkahub:sqlite-android` 的版本写作 **`-SNAPSHOT`**——
 * 在 JitPack 上这等于"默认分支最新 commit"。两个后果：
 *  1. **不可复现**：今天解到的 AAR 与下个月解到的可能不是同一份代码；
 *  2. **绕过评审**：上游推一个新 commit 就直接进了安装包，没有任何人看过。
 *
 * 已钉到不可变 commit `80cedc8888df2fe1d22d7f9bf8d9287f621624be`（JitPack 构建记录确认可取）。
 * 本测试守住"别再有浮动版本"，并检查 SBOM 记录存在。
 */
class DependencyHygieneJvmTest {

    private val projectRoot: File = run {
        var dir = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (!File(dir, "settings.gradle").isFile && dir.parentFile != null) dir = dir.parentFile
        dir
    }

    private fun versionCatalogs(): List<File> = listOf(
        File(projectRoot, "rikkahub/gradle/libs.versions.toml"),
        File(projectRoot, "gradle/libs.versions.toml"),
    ).filter { it.isFile }

    /** 浮动版本：SNAPSHOT / latest.release / latest.integration / 纯 `+` / 动态区间。 */
    private fun floatingVersionLines(text: String): List<String> =
        text.lines().filter { line ->
            val body = line.substringBefore('#')
            if (!body.contains("=")) return@filter false
            val value = body.substringAfter('=').trim().trim('"')
            value.contains("SNAPSHOT", ignoreCase = true) ||
                value.contains("latest.release", ignoreCase = true) ||
                value.contains("latest.integration", ignoreCase = true) ||
                value == "+" ||
                // Gradle 动态版本区间，如 [1.0,2.0) / (1.0,2.0]。
                // 用明确的字符串判断而不是正则字符类，避免正则语法错误（第一版就栽在这里）。
                ((value.startsWith("[") || value.startsWith("(")) &&
                    (value.endsWith("]") || value.endsWith(")")))
        }

    @Test
    fun noFloatingVersionsInCatalogs() {
        val offenders = versionCatalogs().flatMap { file ->
            floatingVersionLines(file.readText(Charsets.UTF_8)).map { "${file.name}: $it" }
        }
        assertTrue(
            "版本目录里不得出现浮动版本（不可复现 + 绕过评审）：\n${offenders.joinToString("\n")}",
            offenders.isEmpty(),
        )
    }

    /** 具体钉住本次修的这个依赖——防止有人"手滑"改回 SNAPSHOT。 */
    @Test
    fun sqliteAndroidIsPinnedToImmutableCommit() {
        val vendor = File(projectRoot, "rikkahub/gradle/libs.versions.toml")
        assertTrue("vendor 版本目录必须存在", vendor.isFile)
        val text = vendor.readText(Charsets.UTF_8)
        val line = text.lines().firstOrNull { it.trimStart().startsWith("sqlite-android =") }
        assertTrue("必须能找到 sqlite-android 版本声明", line != null)
        assertFalse("sqlite-android 不得再用 -SNAPSHOT", line!!.contains("SNAPSHOT", ignoreCase = true))
        assertTrue(
            "sqlite-android 必须钉到 40 位不可变 commit：$line",
            Regex("""[0-9a-f]{40}""").containsMatchIn(line),
        )
    }

    /** SBOM / 许可证记录必须存在（DFW-30 验收："依赖来源和许可证有记录"）。 */
    @Test
    fun thirdPartyNoticesExistAndCoverKeyItems() {
        val notices = File(projectRoot, "docs/THIRD-PARTY-NOTICES.md")
        assertTrue("必须存在第三方组件与许可证清单 docs/THIRD-PARTY-NOTICES.md", notices.isFile)
        val text = notices.readText(Charsets.UTF_8)
        for (required in listOf("AGPL-3.0", "RikkaHub", "LanzouPlus", "sqlite-android", "MIT", "Apache-2.0")) {
            assertTrue("SBOM 清单必须覆盖「$required」", text.contains(required))
        }
        assertTrue(
            "SBOM 必须记录 sqlite-android 的钉版处置（否则读者不知道这个风险已处理）",
            text.contains("80cedc8888df2fe1d22d7f9bf8d9287f621624be"),
        )
    }

    /** 上游锚点必须固定在 tag，而不是 master/快照（DFW-30：上游 tag/commit 可追溯）。 */
    @Test
    fun upstreamAnchorIsATagNotBranch() {
        val patches = File(projectRoot, "rikkahub/PATCHES.md")
        assertTrue("补丁台账必须存在", patches.isFile)
        val text = patches.readText(Charsets.UTF_8)
        assertTrue(
            "台账必须写明上游锚点是 tag 2.5.5（而不是 master/快照）",
            text.contains("2.5.5"),
        )
        assertFalse(
            "台账不得把 master 当稳定上游锚点",
            Regex("""锚点\s*[:=]?\s*master""").containsMatchIn(text),
        )
    }
}
