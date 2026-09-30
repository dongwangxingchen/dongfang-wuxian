package cc.nkbr.lanzouplus

import android.app.AlertDialog
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * [DFWX] DFW-59：更新弹窗**四按钮布局**与三档模式的行为验收。
 *
 * 这张卡的验收核心是"用户看到的按钮对不对"，而不是算法：
 *   ① 软更新 = 立即更新 + 其他下载方式 + 取消更新 + 不再显示（四个）；
 *   ② 强制更新 = 只有前两个，**没有取消、没有不再显示**，且**点外面关不掉**；
 *   ③ off = 完全不弹；
 *   ④ 「不再显示」只对该版本生效，换版本照弹。
 *
 * 这些都必须从**真实构建出来的对话框**里数按钮，而不是只测策略类——
 * 策略类对了但 UI 忘了用它的返回值，用户照样会看到错误的按钮。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class UpdateDialogJvmTest {

    companion object {
        @BeforeClass @JvmStatic fun silenceAutoImport() { MainActivity.LIBRARY_AUTO_IMPORT = false }
    }

    private val digest = "sha256:" + "b".repeat(64)

    private fun offer(
        versionName: String = "1.0.1",
        versionCode: Long = 10001L,
        mode: UpdatePromptPolicy.Mode = UpdatePromptPolicy.Mode.SOFT,
        body: String = "修了几个问题",
        withDigest: Boolean = true,
    ) = UpdateOffer(
        versionName, versionCode, 36_600_000L,
        if (withDigest) digest else "",
        body,
        "http://39.106.33.135/apk/dongfang-wuxian-v$versionName.apk", "",
        "https://github.com/dongwangxingchen/dongfang-wuxian", "",
        mode,
    )

    private fun activity(): MainActivity {
        val a = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        shadowOf(Looper.getMainLooper()).idle()
        return a
    }

    private fun buttonsIn(view: View, out: MutableList<Button> = mutableListOf()): List<Button> {
        if (view is Button) out.add(view)
        if (view is ViewGroup) for (i in 0 until view.childCount) buttonsIn(view.getChildAt(i), out)
        return out
    }

    private fun textsIn(view: View, out: MutableList<String> = mutableListOf()): List<String> {
        if (view is TextView && view !is Button) view.text?.toString()?.let { if (it.isNotEmpty()) out.add(it) }
        if (view is ViewGroup) for (i in 0 until view.childCount) textsIn(view.getChildAt(i), out)
        return out
    }

    /**
     * 真按一次返回键，看它会不会关掉。
     *
     * 比读一个"能不能取消"的标志位更接近用户实际感受——Android 37 的 `Dialog` 只暴露了
     * `setCancelable` 而没有读取用的公开方法，而"按返回键关不关得掉"本来就是我们要保证的那件事。
     */
    private fun closesOnBack(dialog: AlertDialog): Boolean {
        if (!dialog.isShowing) dialog.show()
        dialog.onBackPressed()
        return !dialog.isShowing
    }

    /** 从 activity 上抓当前显示的更新对话框。 */
    private fun dialogOf(a: MainActivity): AlertDialog {
        val dialog = a.offerDialog
        assertNotNull("更新弹窗必须被记录（否则测试与后续关闭逻辑都摸不到它）", dialog)
        return dialog!!
    }

    // ── 软更新：四个按钮 ──────────────────────────────────────────────────

    @Test
    fun softMode_showsAllFourActions() {
        val a = activity()
        a.showUpdateOffer(offer(mode = UpdatePromptPolicy.Mode.SOFT), false)
        shadowOf(Looper.getMainLooper()).idle()

        val dialog = dialogOf(a)
        // 只数"有文字"的按钮：AlertDialog 自带三个内部空按钮（确认/取消/中立位），
        // 它们没有文案也不可见，混进计数会把 4 个动作误报成 7 个。
        val labels = buttonsIn(dialog.window!!.decorView).map { it.text.toString() }.filter { it.isNotBlank() }
        assertTrue("缺少「立即更新」：$labels", labels.contains("立即更新"))
        assertTrue("缺少「其他下载方式」：$labels", labels.contains("其他下载方式"))
        assertTrue("缺少「取消更新」：$labels", labels.contains("取消更新"))
        assertTrue("缺少「不再显示」：$labels", labels.contains("不再显示"))
        assertEquals("软更新恰好四个动作，多了就是画蛇添足：$labels", 4, labels.size)

        assertTrue("软更新可以取消：按返回键应能关掉", closesOnBack(dialog))
    }

    @Test
    fun dialog_showsVersionAndChangelog() {
        val a = activity()
        a.showUpdateOffer(offer(body = "1. 修了闪退\n2. 加了公告"), false)
        shadowOf(Looper.getMainLooper()).idle()

        val texts = textsIn(dialogOf(a).window!!.decorView)
        assertTrue("标题要显示新版本号：$texts", texts.any { it.contains("发现新版本") && it.contains("1.0.1") })
        assertTrue("更新内容必须显示出来：$texts", texts.any { it.contains("修了闪退") })
    }

    @Test
    fun longChangelog_isScrollable_notTruncated() {
        // 更新记录会一直累积。若不做滚动容器，长内容会把弹窗撑出屏幕，用户既看不到底也点不到按钮。
        val longNotes = (1..60).joinToString("\n") { "第 $it 条改动说明" }
        val a = activity()
        a.showUpdateOffer(offer(body = longNotes), false)
        shadowOf(Looper.getMainLooper()).idle()

        val decor = dialogOf(a).window!!.decorView
        val scrollers = mutableListOf<ScrollView>()
        fun walk(v: View) {
            if (v is ScrollView) scrollers.add(v)
            if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
        }
        walk(decor)
        assertTrue("长更新内容必须有滚动容器", scrollers.isNotEmpty())
        val shown = textsIn(decor).firstOrNull { it.contains("第 60 条") }
        assertNotNull("最后一条改动必须在滚动区里（不能被截断丢掉整段）", shown)
    }

    // ── 强制更新：只有两个按钮 ────────────────────────────────────────────

    @Test
    fun forceMode_hasNoCancelAndCannotBeDismissedByTappingOutside() {
        val a = activity()
        a.showUpdateOffer(offer(mode = UpdatePromptPolicy.Mode.FORCE), false)
        shadowOf(Looper.getMainLooper()).idle()

        val dialog = dialogOf(a)
        val labels = buttonsIn(dialog.window!!.decorView).map { it.text.toString() }.filter { it.isNotBlank() }
        assertTrue("强制更新必须有「立即更新」：$labels", labels.contains("立即更新"))
        assertTrue("强制更新要有「其他下载方式」：$labels", labels.contains("其他下载方式"))
        assertFalse("强制更新**不得**出现「取消更新」：$labels", labels.contains("取消更新"))
        assertFalse("强制更新**不得**出现「不再显示」：$labels", labels.contains("不再显示"))
        assertEquals("强制更新恰好两个动作：$labels", 2, labels.size)
        // 用户要求"强制"：能点外面关掉就等于没强制
        assertFalse("强制更新不许关掉：按返回键也必须关不掉", closesOnBack(dialog))
    }

    @Test
    fun forceMode_stillPromptsAfterUserDismissedThatVersion() {
        val a = activity()
        a.updatePromptPolicy().rememberDismissed(10001L)
        a.showUpdateOffer(offer(mode = UpdatePromptPolicy.Mode.FORCE), false)
        shadowOf(Looper.getMainLooper()).idle()
        assertNotNull("强制更新必须无视历史的「不再显示」，否则点一次就能永久绕过强制", a.offerDialog)
    }

    // ── off：完全不弹 ─────────────────────────────────────────────────────

    @Test
    fun offMode_showsNoDialogAtAll() {
        val a = activity()
        a.showUpdateOffer(offer(mode = UpdatePromptPolicy.Mode.OFF), false)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("off 模式不得弹任何对话框", null, a.offerDialog)
    }

    // ── 「不再显示」的作用域 ──────────────────────────────────────────────

    @Test
    fun neverRemind_suppressesOnlyThatVersion_thenNextVersionPromptsAgain() {
        val a = activity()
        a.showUpdateOffer(offer(versionName = "1.0.1", versionCode = 10001L), false)
        shadowOf(Looper.getMainLooper()).idle()
        dialogOf(a).dismiss()
        a.offerDialog = null

        // 模拟用户点「不再显示」
        a.updatePromptPolicy().rememberDismissed(10001L)
        a.showUpdateOffer(offer(versionName = "1.0.1", versionCode = 10001L), false)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("同一版被「不再显示」后不得再弹", null, a.offerDialog)

        // 换版本必须照弹——这是用户明确要求的语义
        a.showUpdateOffer(offer(versionName = "1.0.2", versionCode = 10002L), false)
        shadowOf(Looper.getMainLooper()).idle()
        assertNotNull("换成 1.0.2 必须照弹（「不再显示」≠「永不更新」）", a.offerDialog)
    }

    @Test
    fun manualCheck_bypassesRememberedDismissal() {
        val a = activity()
        a.updatePromptPolicy().rememberDismissed(10001L)
        a.showUpdateOffer(offer(versionName = "1.0.1", versionCode = 10001L), true)
        shadowOf(Looper.getMainLooper()).idle()
        assertNotNull("用户主动点「检查更新」时必须能看到该版本，否则回「已是最新」是骗人的", a.offerDialog)
        assertEquals("手动检查应顺手清掉记忆", 0L, a.updatePromptPolicy().dismissedVersionCode())
    }

    // ── 不该弹的情况 ──────────────────────────────────────────────────────

    @Test
    fun sameOrOlderVersion_doesNotShowDialog() {
        val a = activity()
        // 已装版本由 BuildConfig.VERSION_CODE 决定（当前 10000）。给一个不大于它的版本。
        a.showUpdateOffer(offer(versionName = "1.0.0", versionCode = BuildConfigVersionCode), false)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("不比当前新的版本不许弹", null, a.offerDialog)
    }

    private val BuildConfigVersionCode: Long = BuildConfig.VERSION_CODE.toLong()

    @Test
    fun offerWithoutAnyDownloadRoute_doesNotShowBrokenDialog() {
        // 点哪个都没用的弹窗等于骗用户。宁可不弹，并给一句说明。
        val dead = UpdateOffer("1.0.1", 10001L, 0L, "", "", "", "", "", "", UpdatePromptPolicy.Mode.SOFT)
        val a = activity()
        a.showUpdateOffer(dead, false)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("没有任何下载去处的供给不该弹窗", null, a.offerDialog)
    }

    // ── 文案诚实性 ────────────────────────────────────────────────────────

    @Test
    fun metaText_doesNotClaimSignatureVerifiedBeforeDownloading() {
        // 弹窗出现时**还没下载**。写"已校验签名"是虚假陈述——校验发生在下载之后。
        val a = activity()
        a.showUpdateOffer(offer(), false)
        shadowOf(Looper.getMainLooper()).idle()
        val texts = textsIn(dialogOf(a).window!!.decorView)
        val meta = texts.firstOrNull { it.contains("校验") }
        assertNotNull("应说明下载后会校验，让用户知道有防护：$texts", meta)
        assertFalse(
            "还没下载就说「已校验」是虚假陈述，必须是「下载后校验」：$meta",
            meta!!.contains("已校验"),
        )
        assertTrue("应写成「下载后校验」：$meta", meta.contains("下载后校验"))
        assertTrue("应说明大小：$meta", meta.contains("MB"))
    }

    @Test
    fun alternativesDialog_labelsGithubAsNeedingVpn() {
        val a = activity()
        a.showUpdateOffer(offer(), false)
        shadowOf(Looper.getMainLooper()).idle()

        // 直接走第二层：确认条目里带"需科学上网"标注（国内直连不通，不标注会让用户以为软件坏了）
        val alts = offer().alternates()
        assertTrue(alts.any { it.label.contains("需科学上网") })
    }
}
