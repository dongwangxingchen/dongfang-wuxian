package cc.nkbr.lanzouplus

import android.os.Looper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * [DFWX] DFW-16 本地凭据边界回归。
 *
 * 卡片的四条里，可被 JVM 精确验证的是这几条：
 * 1. 分享密码明文留在下载历史里，且此前**没有清理入口** → 断言清理逻辑真的清干净；
 * 2. 清理只清密码、**不动下载记录**（卡片红线）→ 断言记录条数与其它字段不变；
 * 3. 崩溃诊断不得含带凭据的 URL / 密码（第 4 条）→ 断言 diagnostics 内容。
 *
 * （清单层 `allowBackup` / `requestLegacyExternalStorage` 的最终值走 aapt 证据，
 *   见提交信息与验收说明，不在 JVM 测试范围内。）
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class LocalCredentialBoundaryJvmTest {

    companion object {
        @BeforeClass @JvmStatic fun silenceAutoImport() { MainActivity.LIBRARY_AUTO_IMPORT = false }
    }

    private fun activity(): MainActivity {
        val a = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        shadowOf(Looper.getMainLooper()).idle()
        return a
    }

    private fun entryWithPassword(password: String, shareUrl: String): MainActivity.DownloadEntry =
        MainActivity.DownloadEntry().apply {
            name = "示例文件.apk"
            this.shareUrl = shareUrl
            this.password = password
            state = MainActivity.DOWNLOAD_COMPLETED
        }

    @Test
    fun clearingPasswords_removesPlaintextPassword() {
        val a = activity()
        val e = entryWithPassword("a1b2", "https://wwc.lanzouw.com/iSecret")
        a.downloadEntries.add(e)

        a.clearStoredSharePasswords()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals("分享密码必须被清空", "", e.password)
    }

    @Test
    fun clearingPasswords_stripsPasswordFromShareUrl() {
        val a = activity()
        val e = entryWithPassword("a1b2", "https://wwc.lanzouw.com/iSecret?pwd=a1b2")
        a.downloadEntries.add(e)

        a.clearStoredSharePasswords()
        shadowOf(Looper.getMainLooper()).idle()

        assertFalse("URL 里的 pwd= 参数也必须剥掉：${e.shareUrl}", e.shareUrl.contains("a1b2"))
        assertFalse("不得留下空参数残留：${e.shareUrl}", e.shareUrl.endsWith("?") || e.shareUrl.endsWith("&"))
        assertTrue("链接主体必须保留：${e.shareUrl}", e.shareUrl.contains("iSecret"))
    }

    /** 卡片红线：不删除用户的下载历史，只提供清理入口。 */
    @Test
    fun clearingPasswords_keepsDownloadRecords() {
        val a = activity()
        val e1 = entryWithPassword("aaa1", "https://wwc.lanzouw.com/iOne")
        val e2 = entryWithPassword("", "https://wwc.lanzouw.com/iTwo")
        val e3 = entryWithPassword("ccc3", "https://wwc.lanzouw.com/iThree")
        a.downloadEntries.addAll(listOf(e1, e2, e3))
        val before = a.downloadEntries.size

        a.clearStoredSharePasswords()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals("清理密码不得删除任何下载记录", before, a.downloadEntries.size)
        assertEquals("文件名不得被改动", "示例文件.apk", e1.name)
        assertEquals("链接主体不得被改动", "https://wwc.lanzouw.com/iOne", e1.shareUrl)
        assertEquals("本就没有密码的记录不应被影响", "", e2.password)
    }

    /** 无密码时点击不得报错、不得误报"已清除"。 */
    @Test
    fun clearingPasswords_whenNoneStored_isSafe() {
        val a = activity()
        a.downloadEntries.add(entryWithPassword("", "https://wwc.lanzouw.com/iNone"))
        a.clearStoredSharePasswords()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, a.downloadEntries.size)
    }

    /** 第 4 条：崩溃诊断不得含任何 URL / 密码 / 凭据字样。 */
    @Test
    fun crashDiagnostics_containNoCredentialsOrUrls() {
        val text = App.diagnostics()
        for (forbidden in listOf("http://", "https://", "pwd=", "password", "密码", "token", "Bearer ")) {
            assertFalse("崩溃诊断不得包含「$forbidden」：\n$text", text.contains(forbidden, ignoreCase = true))
        }
    }

    @Test
    fun crashDiagnostics_stillUsefulForTriage() {
        val text = App.diagnostics()
        for (required in listOf("版本", "厂商", "型号", "Android", "ABI")) {
            assertTrue("诊断必须保留定位故障所需字段「$required」", text.contains(required))
        }
    }
}
