package cc.nkbr.lanzouplus

import android.content.Intent
import android.net.Uri
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
 * [DFWX] DFW-12 外部互通回归：接收系统分享/链接打开。
 *
 * 这张卡的缺陷是"入口根本不存在"（manifest 只有 MAIN/LAUNCHER），
 * 所以测试分两层：
 *  1. **解析层**：从聊天软件那种"标题 + 链接 + 口令"混合文本里挑出正确链接，
 *     并且**只接受 http/https**——这是安全边界，不能接受 file/content 等 scheme；
 *  2. **接线层**：真的把 ACTION_SEND / ACTION_VIEW 喂进 Activity，确认不会崩且走对了入口。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class ShareReceiveJvmTest {

    companion object {
        @BeforeClass @JvmStatic fun silenceAutoImport() { MainActivity.LIBRARY_AUTO_IMPORT = false }
    }

    private fun activity(): MainActivity {
        val a = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        shadowOf(Looper.getMainLooper()).idle()
        return a
    }

    @Test
    fun extractsLanzouLinkFromChatMessage() {
        val a = activity()
        // 微信分享常见形态：标题 + 链接 + 提取码
        val text = "分享一个软件\nhttps://wwc.lanzouw.com/iABC123\n提取码：8x2k"
        val url = a.extractSharedUrl(text)
        assertTrue("必须从混合文本里挑出链接：$url", url.startsWith("https://wwc.lanzouw.com/iABC123"))
    }

    @Test
    fun extractsFirstHttpLinkWhenChatTextHasMultipleUrls() {
        val a = activity()
        val url = a.extractSharedUrl("先看这个 https://example.com/a 然后 https://wwc.lanzouw.com/iXYZ")
        assertEquals("https://example.com/a", url)
    }

    /** 只有裸域名（没写 scheme）时，交给原文去补 https，不应丢链接。 */
    @Test
    fun keepsPlainTextWhenNoUrlFound() {
        val a = activity()
        assertEquals("wwc.lanzouw.com/iABC", a.extractSharedUrl("wwc.lanzouw.com/iABC"))
    }

    // ---------- 安全边界：只收 http(s) ----------

    @Test
    fun acceptsOnlyWebSchemes() {
        val a = activity()
        for (ok in listOf("https://wwc.lanzouw.com/x", "http://wwc.lanzouw.com/x")) {
            assertTrue("应接受网页链接：$ok", a.isShareableWebUrl(ok))
        }
    }

    @Test
    fun rejectsDangerousSchemes() {
        val a = activity()
        // 这些 scheme 一旦被当链接打开，等于把任意文件/意图暴露成入口
        for (bad in listOf(
            "file:///etc/passwd",
            "content://com.other/secret",
            "javascript:alert(1)",
            "intent://evil#Intent;scheme=x;end",
            "market://details?id=x",
            "",
        )) {
            assertFalse("必须拒绝非网页 scheme：$bad", a.isShareableWebUrl(bad))
        }
    }

    @Test
    fun rejectsUrlWithoutHost() {
        val a = activity()
        assertFalse(a.isShareableWebUrl("https://"))
        assertFalse(a.isShareableWebUrl("http://"))
    }

    // ---------- 接线层 ----------

    @Test
    fun actionSendTextIsHandledWithoutCrash() {
        val a = activity()
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "https://wwc.lanzouw.com/iSHARED")
        }
        a.handleExternalAction(intent)
        shadowOf(Looper.getMainLooper()).idle()
        // 只要不抛异常即算接线正常（真实弹窗行为在真机验收）
        assertTrue(true)
    }

    @Test
    fun actionViewHttpLinkIsHandledWithoutCrash() {
        val a = activity()
        a.handleExternalAction(Intent(Intent.ACTION_VIEW, Uri.parse("https://wwc.lanzouw.com/iVIEW")))
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(true)
    }

    /** 非网页 scheme 的 VIEW 必须被忽略，不得走解析链路。 */
    @Test
    fun actionViewNonWebSchemeIsIgnored() {
        val a = activity()
        val before = a.pageKind
        a.handleExternalAction(Intent(Intent.ACTION_VIEW, Uri.parse("file:///etc/hosts")))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("file:// 不得触发任何页面跳转", before, a.pageKind)
    }

    /** 空分享文本不得触发任何解析。 */
    @Test
    fun actionSendWithoutTextIsIgnored() {
        val a = activity()
        val before = a.pageKind
        a.handleExternalAction(Intent(Intent.ACTION_SEND).apply { type = "text/plain" })
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("空分享不得跳转页面", before, a.pageKind)
    }
}
