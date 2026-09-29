package cc.nkbr.lanzouplus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * [DFWX] DFW-29（ARCH-001）第二步：崩溃日志落盘/读取**直测**。
 *
 * 抽取前这些逻辑直接依赖 `getExternalFilesDir()`，只能在启动 Activity 之后间接验证；
 * 现在 `CrashLogStore` 接收目录参数，于是可以用临时目录毫秒级直测边界情况
 * （超长日志截断、目录不存在、写失败静默、只清自己的文件）。
 *
 * 行为等价性由"抽取后全量宿主测试守恒"保证；这里补的是**以前测不到的边界**。
 */
class CrashLogStoreJvmTest {

    @get:Rule val tmp = TemporaryFolder()

    private fun dir(name: String): File = tmp.newFolder(name)

    // ---------- 写报告 ----------

    @Test
    fun writeReport_createsMainFileAndLatestCopy() {
        val d = dir("crash")
        val file = CrashLogStore.writeReport(d, "1.2.3", "崩溃内容")

        assertNotNull(file)
        assertTrue("主文件必须存在", file!!.isFile)
        assertEquals("崩溃内容", file.readText(Charsets.UTF_8))
        assertTrue("文件名必须带 dfwx-crash- 前缀", file.name.startsWith(CrashLogStore.REPORT_PREFIX))
        assertTrue("文件名必须带版本号", file.name.contains("1.2.3"))
        assertTrue("文件名必须纯 ASCII", file.name.all { it.code in 32..126 })

        val latest = File(d, CrashLogStore.LATEST_NAME)
        assertTrue("固定名副本必须存在（方便用户下次直接取）", latest.isFile)
        assertEquals("崩溃内容", latest.readText(Charsets.UTF_8))
    }

    /** 目录不存在时必须自动创建（用户首次导出时目录可能还没建）。 */
    @Test
    fun writeReport_createsMissingDirectory() {
        val nested = File(tmp.root, "a/b/c")
        assertFalse(nested.exists())
        val file = CrashLogStore.writeReport(nested, "1.0.0", "x")
        assertNotNull("目录不存在时应自动创建并写入", file)
        assertTrue(file!!.isFile)
    }

    /** 目录为 null 时返回 null，不抛（调用方据此提示"保存失败"）。 */
    @Test
    fun writeReport_nullDirectoryReturnsNull() {
        assertNull(CrashLogStore.writeReport(null, "1.0.0", "x"))
    }

    /** 中文内容必须按 UTF-8 原样落盘。 */
    @Test
    fun writeReport_preservesUtf8Chinese() {
        val content = "── 最近一次崩溃 ──\njava.lang.RuntimeException: 测试中文"
        val file = CrashLogStore.writeReport(dir("utf8"), "1.0.0", content)!!
        assertEquals(content, file.readText(Charsets.UTF_8))
    }

    // ---------- 文件名 ----------

    @Test
    fun reportFileName_isAsciiAndCarriesVersion() {
        val name = CrashLogStore.reportFileName("9.9.9")
        assertTrue(name.startsWith("dfwx-crash-"))
        assertTrue(name.endsWith(".txt"))
        assertTrue(name.contains("9.9.9"))
        assertTrue("必须纯 ASCII（中文名会被分享/保存链路截断，v1.22.8 事故同源）",
            name.all { it.code in 32..126 })
    }

    // ---------- 只清自己的文件 ----------

    @Test
    fun reportFiles_onlyMatchesOwnPrefix() {
        val d = dir("list")
        File(d, "dfwx-crash-a.txt").writeText("a")
        File(d, "dfwx-crash-b.txt").writeText("b")
        File(d, "dfwx-crash-latest.txt").writeText("c")
        File(d, "用户自己放的文件.txt").writeText("keep")
        File(d, "notes.md").writeText("keep")
        File(d, "subdir").mkdirs()

        val found = CrashLogStore.reportFiles(d).map { it.name }.sorted()
        assertEquals(
            "只应列出 dfwx-crash- 前缀的文件，且不含目录",
            listOf("dfwx-crash-a.txt", "dfwx-crash-b.txt", "dfwx-crash-latest.txt"),
            found,
        )
    }

    @Test
    fun reportFiles_handlesMissingDirectory() {
        assertTrue(CrashLogStore.reportFiles(null).isEmpty())
        assertTrue(CrashLogStore.reportFiles(File(tmp.root, "nope")).isEmpty())
        assertTrue(CrashLogStore.reportFiles(File(tmp.root, "file.txt").apply { writeText("x") }).isEmpty())
    }

    // ---------- 尾部读取 ----------

    @Test
    fun readTail_returnsWholeFileWhenSmall() {
        val f = File(dir("tail"), "crash.log")
        f.writeText("短内容")
        assertEquals("短内容", CrashLogStore.readTail(f))
    }

    /** 超长日志只读尾部（避免把几十 MB 日志整个塞进内存/界面）。 */
    @Test
    fun readTail_truncatesToTailWindow() {
        val f = File(dir("tail2"), "crash.log")
        val head = "HEAD".repeat(10000)          // 40000 字节，远超 12000
        val tail = "TAIL-MARKER"
        f.writeText(head + tail, Charsets.UTF_8)

        val read = CrashLogStore.readTail(f)
        assertEquals("读取长度应正好是尾部窗口", CrashLogStore.TAIL_BYTES, read.toByteArray(Charsets.UTF_8).size)
        assertTrue("必须包含文件末尾内容", read.endsWith(tail))
        // 读的是尾部窗口：应恰好从偏移 40000-12000 处开始，因此不含最前面的那段
        assertFalse("不得包含文件最开头的内容", read.startsWith("HEADHEAD"))
    }

    @Test
    fun readTail_handlesMissingOrEmptyFile() {
        assertEquals("", CrashLogStore.readTail(null))
        assertEquals("", CrashLogStore.readTail(File(tmp.root, "nope.log")))
        val empty = File(dir("empty"), "crash.log").apply { createNewFile() }
        assertEquals("", CrashLogStore.readTail(empty))
    }

    /** 读取任何异常都必须退化为空串（界面不能因为日志文件损坏而崩）。 */
    @Test
    fun readTail_neverThrows() {
        val d = dir("dirAsFile")
        assertEquals("传目录进去也不得抛", "", CrashLogStore.readTail(d))
    }

    // ---------- 静默写入 / 静默删除 ----------

    @Test
    fun writeBytesQuietly_createsParentsAndNeverThrows() {
        val target = File(File(tmp.root, "p/q"), "x.txt")
        CrashLogStore.writeBytesQuietly(target, "data".toByteArray())
        assertTrue("应自动创建父目录", target.isFile)
        assertEquals("data", target.readText())
        // null 与不可写路径都不得抛
        CrashLogStore.writeBytesQuietly(null, "x".toByteArray())
        CrashLogStore.writeBytesQuietly(tmp.root, "x".toByteArray())
    }

    @Test
    fun deleteQuietly_handlesNullAndMissing() {
        CrashLogStore.deleteQuietly(null)
        CrashLogStore.deleteQuietly(File(tmp.root, "missing.txt"))
        val f = File(tmp.root, "todelete.txt").apply { writeText("x") }
        CrashLogStore.deleteQuietly(f)
        assertFalse(f.exists())
    }
}
