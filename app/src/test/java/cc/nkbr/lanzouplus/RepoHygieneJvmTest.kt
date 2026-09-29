package cc.nkbr.lanzouplus

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * [DFWX] DFW-15 仓库卫生守卫：调试残留不得再被 git 跟踪，
 * 且 .gitignore 规则必须**锚定到仓库根**。
 *
 * 为什么要测 .gitignore 的写法：仓库自己踩过这个坑——
 * 无锚的 `log/`、`backup/` 曾吞掉 `ui/pages/{log,backup}` 源码包，
 * 导致交接时漏提交 8 个 .kt 文件（.gitignore 里的注释有记录）。
 * 所以这里不只检查"残留被忽略"，还检查"规则是锚定的"。
 */
class RepoHygieneJvmTest {

    private val projectRoot: File = run {
        var dir = File(System.getProperty("user.dir") ?: ".").absoluteFile
        // 单测工作目录是模块目录（app/），向上找到含 .gitignore 的仓库根
        while (!File(dir, ".gitignore").isFile && dir.parentFile != null) dir = dir.parentFile
        dir
    }

    private fun gitLsFiles(): List<String> {
        val out = StringBuilder()
        val proc = ProcessBuilder("git", "ls-files")
            .directory(projectRoot)
            .redirectErrorStream(true)
            .start()
        proc.inputStream.bufferedReader().use { out.append(it.readText()) }
        assertTrue("git ls-files 必须成功执行", proc.waitFor() == 0)
        return out.toString().split('\n').filter { it.isNotBlank() }
    }

    @Test
    fun debugResidueIsNotTracked() {
        val tracked = gitLsFiles()
        val residuePattern = Regex("(cb_err|lb_resp|record_log|support_test_log|unit_compile_log)", RegexOption.IGNORE_CASE)
        val offenders = tracked.filter { residuePattern.containsMatchIn(it) }
        assertTrue(
            "调试残留不得再被 git 跟踪（本卡 DFW-15），仍跟踪的有：$offenders",
            offenders.isEmpty(),
        )
    }

    /** .gitignore 必须把残留规则锚定到仓库根（前导 /），否则会吞掉任意层级的同名源码包。 */
    @Test
    fun gitignoreRulesAreAnchoredToRepoRoot() {
        val ignore = File(projectRoot, ".gitignore").readText(Charsets.UTF_8)
        val lines = ignore.split('\n').map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }

        // 本卡新增的残留规则必须是锚定形式
        for (name in listOf("cb_err.txt", "lb_resp.txt", "record_log.txt", "unit_compile_log.txt")) {
            assertTrue(
                "`.gitignore` 里 `$name` 必须以 `/` 开头锚定到仓库根，避免吞掉任意层级同名文件",
                lines.contains("/$name"),
            )
        }
        // 历史教训的护栏：log/ 与 backup/ 必须保持锚定
        assertTrue(".gitignore 的 log/ 规则必须锚定为 /log/（曾吞 ui/pages/log 源码包）", lines.contains("/log/"))
        assertTrue(".gitignore 的 backup/ 规则必须锚定为 /backup/（曾吞 ui/pages/backup 源码包）", lines.contains("/backup/"))
    }

    /** 锚定规则不能误伤真实源码目录。 */
    @Test
    fun anchoredRulesDoNotSwallowSourceDirectories() {
        val sourceDirs = listOf(
            "rikkahub/app/src/main/java/me/rerere/rikkahub/ui/pages/log",
            "rikkahub/app/src/main/java/me/rerere/rikkahub/ui/pages/backup",
        )
        for (rel in sourceDirs) {
            val dir = File(projectRoot, rel)
            if (!dir.isDirectory) continue
            val proc = ProcessBuilder("git", "check-ignore", "-q", rel)
                .directory(projectRoot)
                .redirectErrorStream(true)
                .start()
            val exit = proc.waitFor()
            assertFalse(
                "源码目录 $rel 不得被 .gitignore 规则命中（历史上无锚规则吞过这类目录）",
                exit == 0,
            )
        }
    }
}
