package cc.nkbr.lanzouplus

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [DFWX] 崩溃报告导出（用户 2026-09-29 要求：报告要能保存成文件、带全部信息，方便直接发文件）。
 *
 * 覆盖三件容易写错的事：
 * 1. 文件名必须是纯 ASCII —— 中文名在分享/保存链路上会被截断（v1.22.8 发版资产名 404 事故同源）；
 * 2. 正文必须含环境诊断段（版本/设备/系统/ABI/内存），否则用户发来的文件无法定位问题；
 * 3. 诊断段不得包含任何凭据（报告中绝不出现 key/token/password 字样）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CrashReportExportJvmTest {

    private fun context(): Context = ApplicationProvider.getApplicationContext()

    @Test
    fun reportFileName_isAsciiSafe_andCarriesVersion() {
        val activity = org.robolectric.Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val name = activity.crashReportFileName()

        assertTrue("文件名必须以 .txt 结尾：$name", name.endsWith(".txt"))
        assertTrue("文件名必须含 dfwx 前缀：$name", name.startsWith("dfwx-crash-"))
        assertTrue("文件名必须含版本号：$name", name.contains(BuildConfig.VERSION_NAME))
        assertTrue(
            "文件名必须是纯 ASCII，否则会被分享/保存链路截断（v1.22.8 同源事故）：$name",
            name.all { it.code in 32..126 },
        )
    }

    @Test
    fun diagnostics_containsEnvironmentFacts_forBugTriage() {
        val text = App.diagnostics()

        // ACRA 报告字段约定里最关键的几类：应用版本、设备、系统、ABI、内存
        for (required in listOf("版本", "厂商", "型号", "Android", "ABI", "物理内存", "堆内存")) {
            assertTrue("环境诊断缺少「$required」：\n$text", text.contains(required))
        }
        assertTrue("诊断必须含包名：\n$text", text.contains("dfwx.dongdang"))
    }

    @Test
    fun diagnostics_neverLeaksCredentials() {
        val lower = App.diagnostics().lowercase()
        for (forbidden in listOf("password", "storepassword", "keypassword", "api_key", "apikey", "authorization", "token")) {
            assertTrue("崩溃报告不得包含凭据字样「$forbidden」", !lower.contains(forbidden))
        }
    }

    @Test
    fun writeCrashReportFile_persistsUtf8Content_inPublicCrashFolder() {
        val activity = org.robolectric.Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val content = "── 最近一次崩溃（最新） ──\njava.lang.RuntimeException: 测试中文内容\n"

        val file = activity.writeCrashReportFile(content)
        assertNotNull("崩溃报告文件必须写入成功", file)
        assertTrue("文件必须真实存在", file!!.isFile)
        assertEquals("内容必须按 UTF-8 原样落盘（含中文）", content, file.readText(Charsets.UTF_8))
        // 用户 2026-09-29 反馈"在 MT 管理器里甚至没找到那个文件夹"：报告必须落在
        // Download/东方无限/崩溃日志 这种文件管理器直接可见的公共路径，而不是 Android/data 私有目录。
        val path = file.absolutePath
        assertTrue(
            "报告必须落在公共「东方无限/崩溃日志」目录，实际：$path",
            path.contains("东方无限") && path.contains("崩溃日志"),
        )
        assertTrue(
            "报告不得落在文件管理器看不见的 Android/data 私有目录，实际：$path",
            !path.contains("/Android/data/"),
        )
    }

    @Test
    fun writeCrashReportFile_alsoLeavesFixedNameLatestCopy() {
        val activity = org.robolectric.Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val content = "崩溃内容"

        activity.writeCrashReportFile(content)

        val latest = activity.crashLogExportFile()
        assertNotNull("固定名副本路径必须可解析", latest)
        assertTrue("固定名副本 dfwx-crash-latest.txt 必须存在，便于用户下次直接取用：$latest", latest!!.isFile)
        assertEquals("固定名副本内容必须与报告一致", content, latest.readText(Charsets.UTF_8))
        assertTrue(
            "固定名必须是纯 ASCII：${latest.name}",
            latest.name.all { it.code in 32..126 },
        )
    }

    @Test
    fun crashFolderLabel_namesTheVisibleFolder() {
        val activity = org.robolectric.Robolectric.buildActivity(MainActivity::class.java).setup().get()

        // 提示文案必须把目录名告诉用户，否则用户还是不知道去哪儿找（用户原话："你起的名字太刁钻了"）。
        assertEquals("Download/东方无限/崩溃日志", activity.crashFolderLabel())
    }
}
