package cc.nkbr.lanzouplus

import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ScrollView
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * [DFWX] DFW-59 / DFW-65：更新面板的**行为验收**。
 *
 * DFW-65 把它从居中 `AlertDialog` 改成了**顶部面板**（从上方滑入、可上滑/左右滑关闭），
 * 所以这里的取件方式从"找对话框"改成"找面板"，其余断言意图不变：
 *   ① 软更新 = 立即更新 + 其他下载方式 + 取消更新 + 不再显示（四个）；
 *   ② 强制更新 = 只有前两个，**没有取消、没有不再显示**，且**滑不走、返回键也关不掉**；
 *   ③ off = 完全不弹；
 *   ④ 「不再显示」只对该版本生效，换版本照弹。
 *
 * 这些都必须从**真实构建出来的面板**里数按钮——策略对了但 UI 忘了用它的返回值，用户照样看到错的按钮。
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

    /**
     * 关掉动效再取 Activity：显隐因此是同步的，断言不必等动画结束。
     * （顶部面板的入场/退场都是有界 VPA，测试里等动画只会让用例变脆。）
     */
    private fun activity(): MainActivity {
        val a = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        a.suppressUiMotion = true
        shadowOf(Looper.getMainLooper()).idle()
        return a
    }

    private fun sheetOf(a: MainActivity): TopSheet {
        val sheet = a.offerSheet
        assertNotNull("更新面板必须被记录（否则测试与后续关闭逻辑都摸不到它）", sheet)
        return sheet!!
    }

    private fun panelOf(a: MainActivity): View = sheetOf(a).panel()

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

    /** 面板上的按钮文案（去掉空文案的内部按钮）。 */
    private fun labelsOf(a: MainActivity): List<String> =
        buttonsIn(panelOf(a)).map { it.text.toString() }.filter { it.isNotBlank() }

    /**
     * 真按一次返回键，看面板关不关得掉。
     *
     * 必须绕开 260ms 返回节流：`performSystemBack` 开头就是
     * `if(uptimeMillis()-lastSystemBackAt<260) return;`，而 Robolectric 里 uptimeMillis 很小、
     * lastSystemBackAt=0 → 不绕过的话它无条件早退，断言会**因为错误的原因变绿**（DFW-60 实测踩过）。
     */
    private fun backClosesSheet(a: MainActivity): Boolean {
        a.lastSystemBackAt = -10_000L
        a.performSystemBack()
        shadowOf(Looper.getMainLooper()).idle()
        return a.offerSheet == null
    }

    // ── 软更新：四个按钮 ──────────────────────────────────────────────────

    @Test
    fun softMode_showsAllFourActions() {
        val a = activity()
        a.showUpdateOffer(offer(mode = UpdatePromptPolicy.Mode.SOFT), false)
        shadowOf(Looper.getMainLooper()).idle()

        val labels = labelsOf(a)
        assertTrue("缺少「立即更新」：$labels", labels.contains("立即更新"))
        assertTrue("缺少「其他下载方式」：$labels", labels.contains("其他下载方式"))
        assertTrue("缺少「取消更新」：$labels", labels.contains("取消更新"))
        assertTrue("缺少「不再显示」：$labels", labels.contains("不再显示"))
        assertEquals("软更新恰好四个动作，多了就是画蛇添足：$labels", 4, labels.size)
    }

    @Test
    fun dialog_showsVersionAndChangelog() {
        val a = activity()
        a.showUpdateOffer(offer(body = "1. 修了闪退\n2. 加了公告"), false)
        shadowOf(Looper.getMainLooper()).idle()

        val texts = textsIn(panelOf(a))
        assertTrue("标题要显示新版本号：$texts", texts.any { it.contains("发现新版本") && it.contains("1.0.1") })
        assertTrue("更新内容必须显示出来：$texts", texts.any { it.contains("修了闪退") })
    }

    @Test
    fun longChangelog_isScrollable_notTruncated() {
        // 更新记录会一直累积。若不做滚动容器，长内容会把面板撑出屏幕，用户既看不到底也点不到按钮。
        val longNotes = (1..60).joinToString("\n") { "第 $it 条改动说明" }
        val a = activity()
        a.showUpdateOffer(offer(body = longNotes), false)
        shadowOf(Looper.getMainLooper()).idle()

        val panel = panelOf(a)
        val scrollers = mutableListOf<ScrollView>()
        fun walk(v: View) {
            if (v is ScrollView) scrollers.add(v)
            if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
        }
        walk(panel)
        assertTrue("长更新内容必须有滚动容器", scrollers.isNotEmpty())
        assertNotNull(
            "最后一条改动必须在滚动区里（不能被截断丢掉整段）",
            textsIn(panel).firstOrNull { it.contains("第 60 条") },
        )
    }

    // ── 强制更新：只有两个按钮、且关不掉 ──────────────────────────────────

    @Test
    fun forceMode_hasNoCancelAndNoNeverRemind() {
        val a = activity()
        a.showUpdateOffer(offer(mode = UpdatePromptPolicy.Mode.FORCE), false)
        shadowOf(Looper.getMainLooper()).idle()

        val labels = labelsOf(a)
        assertTrue("强制更新必须有「立即更新」：$labels", labels.contains("立即更新"))
        assertTrue("强制更新要有「其他下载方式」：$labels", labels.contains("其他下载方式"))
        assertFalse("强制更新**不得**出现「取消更新」：$labels", labels.contains("取消更新"))
        assertFalse("强制更新**不得**出现「不再显示」：$labels", labels.contains("不再显示"))
        assertEquals("强制更新恰好两个动作：$labels", 2, labels.size)
    }

    @Test
    fun forceMode_backKeyIsConsumedButDoesNotDismiss() {
        // 用户要求"强制"：能关掉就等于没强制。返回键必须被吃掉，且面板仍在。
        val a = activity()
        a.showUpdateOffer(offer(mode = UpdatePromptPolicy.Mode.FORCE), false)
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue("拦截期间返回键必须由我们消费", a.canHandleBack())
        assertFalse("强制更新按返回键不得关闭", backClosesSheet(a))
        assertNotNull("面板必须仍在", a.offerSheet)
    }

    @Test
    fun softMode_backKeyClosesTheSheet() {
        val a = activity()
        a.showUpdateOffer(offer(mode = UpdatePromptPolicy.Mode.SOFT), false)
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue("软更新时返回键也应可用", a.canHandleBack())
        assertTrue("软更新按返回键应关闭面板", backClosesSheet(a))
    }

    @Test
    fun forceMode_stillPromptsAfterUserDismissedThatVersion() {
        val a = activity()
        a.updatePromptPolicy().rememberDismissed(10001L)
        a.showUpdateOffer(offer(mode = UpdatePromptPolicy.Mode.FORCE), false)
        shadowOf(Looper.getMainLooper()).idle()
        assertNotNull("强制更新必须无视历史的「不再显示」，否则点一次就能永久绕过强制", a.offerSheet)
    }

    // ── off：完全不弹 ─────────────────────────────────────────────────────

    @Test
    fun offMode_showsNothingAtAll() {
        val a = activity()
        a.showUpdateOffer(offer(mode = UpdatePromptPolicy.Mode.OFF), false)
        shadowOf(Looper.getMainLooper()).idle()
        assertNull("off 模式不得弹任何面板", a.offerSheet)
    }

    // ── 「不再显示」的作用域 ──────────────────────────────────────────────

    @Test
    fun neverRemind_suppressesOnlyThatVersion_thenNextVersionPromptsAgain() {
        val a = activity()
        a.showUpdateOffer(offer(versionName = "1.0.1", versionCode = 10001L), false)
        shadowOf(Looper.getMainLooper()).idle()
        a.dismissOfferDialog()

        // 模拟用户点「不再显示」
        a.updatePromptPolicy().rememberDismissed(10001L)
        a.showUpdateOffer(offer(versionName = "1.0.1", versionCode = 10001L), false)
        shadowOf(Looper.getMainLooper()).idle()
        assertNull("同一版被「不再显示」后不得再弹", a.offerSheet)

        // 换版本必须照弹——这是用户明确要求的语义
        a.showUpdateOffer(offer(versionName = "1.0.2", versionCode = 10002L), false)
        shadowOf(Looper.getMainLooper()).idle()
        assertNotNull("换成 1.0.2 必须照弹（「不再显示」≠「永不更新」）", a.offerSheet)
    }

    @Test
    fun manualCheck_bypassesRememberedDismissal() {
        val a = activity()
        a.updatePromptPolicy().rememberDismissed(10001L)
        a.showUpdateOffer(offer(versionName = "1.0.1", versionCode = 10001L), true)
        shadowOf(Looper.getMainLooper()).idle()
        assertNotNull("用户主动点「检查更新」时必须能看到该版本，否则回「已是最新」是骗人的", a.offerSheet)
        assertEquals("手动检查应顺手清掉记忆", 0L, a.updatePromptPolicy().dismissedVersionCode())
    }

    // ── 不该弹的情况 ──────────────────────────────────────────────────────

    @Test
    fun sameOrOlderVersion_doesNotShowSheet() {
        val a = activity()
        a.showUpdateOffer(offer(versionName = "1.0.0", versionCode = BuildConfig.VERSION_CODE.toLong()), false)
        shadowOf(Looper.getMainLooper()).idle()
        assertNull("不比当前新的版本不许弹", a.offerSheet)
    }

    @Test
    fun offerWithoutAnyDownloadRoute_doesNotShowBrokenSheet() {
        // 点哪个都没用的面板等于骗用户。宁可不弹，并给一句说明。
        val dead = UpdateOffer("1.0.1", 10001L, 0L, "", "", "", "", "", "", UpdatePromptPolicy.Mode.SOFT)
        val a = activity()
        a.showUpdateOffer(dead, false)
        shadowOf(Looper.getMainLooper()).idle()
        assertNull("没有任何下载去处的供给不该弹窗", a.offerSheet)
    }

    // ── 文案诚实性 ────────────────────────────────────────────────────────

    @Test
    fun metaText_doesNotClaimSignatureVerifiedBeforeDownloading() {
        // 面板出现时**还没下载**。写"已校验签名"是虚假陈述——校验发生在下载之后。
        val a = activity()
        a.showUpdateOffer(offer(), false)
        shadowOf(Looper.getMainLooper()).idle()
        val meta = textsIn(panelOf(a)).firstOrNull { it.contains("校验") }
        assertNotNull("应说明下载后会校验，让用户知道有防护", meta)
        assertFalse("还没下载就说「已校验」是虚假陈述，必须是「下载后校验」：$meta", meta!!.contains("已校验"))
        assertTrue("应写成「下载后校验」：$meta", meta.contains("下载后校验"))
        assertTrue("应说明大小：$meta", meta.contains("MB"))
    }

    @Test
    fun alternativesDialog_labelsGithubAsNeedingVpn() {
        // 国内直连 github 不通是事实。不标注会让用户点了打不开、以为软件坏了。
        val alts = offer().alternates()
        assertTrue(alts.any { it.label.contains("需科学上网") })
    }

    // ── 顶部面板的形态（DFW-65 的新要求）──────────────────────────────────

    @Test
    fun sheet_hasDragHandle_soUsersDiscoverTheyCanSwipeItAway() {
        // 没有手柄，用户根本不会想到能滑走——功能等于不存在。
        val a = activity()
        a.showUpdateOffer(offer(), false)
        shadowOf(Looper.getMainLooper()).idle()
        val panel = panelOf(a) as ViewGroup
        assertTrue("面板顶部必须有拖拽手柄", panel.childCount > 0)
        val handle = panel.getChildAt(0)
        assertTrue(
            "第一个子 view 应是细长的手柄（宽 > 高）",
            handle.layoutParams.width > handle.layoutParams.height,
        )
    }

    @Test
    fun sheet_isAttachedToWindow_andSitsAtTheTop() {
        val a = activity()
        a.showUpdateOffer(offer(), false)
        shadowOf(Looper.getMainLooper()).idle()
        val panel = panelOf(a)
        assertNotNull("面板必须真的挂到界面上", panel.parent)
        val params = panel.layoutParams
        assertTrue(
            "面板必须挂在顶部（从上面滑下来）",
            params is FrameLayout.LayoutParams && params.gravity == Gravity.TOP,
        )
    }
}
