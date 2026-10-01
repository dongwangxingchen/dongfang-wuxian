package cc.nkbr.lanzouplus

import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.Button
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
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files

/**
 * [DFW-78] **诚信付费 = 站内页**的结构/转场守卫。
 *
 * 用户 2026-10-01 第二次投诉这一页的动画：
 * > "点击诚信付费按钮后的动画效果和关闭那个页面的动画效果太磨叽且不自然不流畅，重置，
 * >  用和打开关于东方无限页面一样的动画效果。"
 *
 * 为什么必须是站内页（这也是本文件所有断言的立足点）：站内推入会把**下面那页**缩到 0.94 并压暗到
 * 55%，那是**同一个窗口里的另一块 View**，底下是应用自己的黑底；独立 Activity 上做同样的事，
 * 透出来的是**桌面壁纸**，所以永远做不成"和关于东方无限一样"。
 *
 * 于是本文件钉住四件事：
 * 1. `openSupportActivity()` 进的是**站内页**（`pageKind == MainActivity.PAGE_KIND_SUPPORT`），
 *    而且**不再 startActivity**（反向探针：改回 `startActivity(...)` 这条必红）；
 * 2. 它是一条设置子页：`systemBackAction != null`，系统返回能回设置；
 * 3. 进/出都走 `animatePage`，方向分别是 **+1（推入）/ -1（弹出）**；
 * 4. 解锁动效 ≤300ms（规范 §2.1 铁律）。
 *
 * 另附文案守卫：站内页的用户可见文案必须与旧的 `SupportActivity` **逐条相同**——
 * 搬内容时改掉一个字这里就会红（"文案一个字都不许改"是这一页的红线）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class SupportPageInAppJvmTest {

    companion object {
        @BeforeClass @JvmStatic fun silenceAutoImport() { MainActivity.LIBRARY_AUTO_IMPORT = false }
    }

    private fun activity(): MainActivity {
        val a = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        shadowOf(Looper.getMainLooper()).idle()
        return a
    }

    /** 从设置页点开诚信付费页（用户真实路径：设置 → 顶部诚信付费按钮 → openSupportActivity）。 */
    private fun openSupportFromSettings(): MainActivity {
        val a = activity()
        a.showSettings()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("前置：确实停在设置页", 4, a.pageKind)
        a.openSupportActivity()
        shadowOf(Looper.getMainLooper()).idle()
        return a
    }

    private fun textsIn(view: View?, out: MutableList<String> = mutableListOf()): List<String> {
        if (view is TextView) view.text?.toString()?.let { if (it.isNotEmpty()) out.add(it) }
        if (view is ViewGroup) for (i in 0 until view.childCount) textsIn(view.getChildAt(i), out)
        return out
    }

    private fun find(view: View?, match: (View) -> Boolean): View? {
        if (view == null) return null
        if (match(view)) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) find(view.getChildAt(i), match)?.let { return it }
        return null
    }

    private fun source(path: String): String {
        var root = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (!File(root, "app/src/main/AndroidManifest.xml").isFile && root.parentFile != null) root = root.parentFile
        return String(Files.readAllBytes(File(root, path).toPath()), StandardCharsets.UTF_8)
    }

    // ── ① 进的是站内页，不再是独立 Activity ──────────────────────────────

    @Test
    fun openSupportActivity_entersTheInAppPage() {
        val a = openSupportFromSettings()
        assertEquals(
            "诚信付费必须是站内页（pageKind = PAGE_KIND_SUPPORT = 11）",
            MainActivity.PAGE_KIND_SUPPORT,
            a.pageKind,
        )
        assertNotNull("站内页必须有内容容器 supportBody", a.supportBody)
        assertTrue("站内页必须真的建出了内容", a.supportBody!!.childCount > 0)
    }

    /**
     * **反向探针的靶子**：把 `openSupportActivity()` 改回 `startActivity(new Intent(this,SupportActivity.class))`
     * 之后，这条立刻变红（会真的启动一个 Activity），而"进站内页"的断言也会一起红。
     */
    @Test
    fun openSupportActivity_noLongerStartsASeparateActivity() {
        val a = openSupportFromSettings()
        assertNull(
            "诚信付费不许再走独立 Activity（窗口转场永远做不成和关于页一样）",
            shadowOf(a).nextStartedActivity,
        )
    }

    @Test
    fun supportPage_isASettingsSubpage_whoseSystemBackReturnsToSettings() {
        val a = openSupportFromSettings()
        assertNotNull("站内子页必须注册系统返回（返回要能回设置）", a.systemBackAction)

        // 必须绕过 performSystemBack 开头那条 260ms 节流（Robolectric 里 uptimeMillis 很小、
        // lastSystemBackAt=0 → 不绕过会无条件早退，测试会因为错误的原因变绿）。
        a.lastSystemBackAt = -10_000L
        a.performSystemBack()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("系统返回必须回到设置页", 4, a.pageKind)
    }

    @Test
    fun escapeHatch_stillExistsAndAlsoReturnsToSettings() {
        val a = openSupportFromSettings()
        val skip = find(a.supportBody) { it is TextView && it.text?.toString() == "暂时不支持，继续使用" }
        assertNotNull("不付费也能走的出口必须还在（这一页的红线）", skip)
        assertTrue("必须可点", skip!!.isClickable)
        assertNotNull("必须带按压反馈底色", skip.background)

        skip.performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("点「暂时不支持」必须能退出这一页（回设置）", 4, a.pageKind)
    }

    // ── ② 进/出都走 animatePage，方向 +1 / -1 ───────────────────────────

    @Test
    fun enterAndExit_bothGoThroughAnimatePageWithTheSubpageDirections() {
        val a = activity()
        a.showSettings()
        shadowOf(Looper.getMainLooper()).idle()

        a.openSupportActivity()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(
            "进这一页必须是站内**推入**（animatePage 方向 +1），与关于东方无限一致",
            1,
            a.lastPageTransitionDirection,
        )

        a.lastSystemBackAt = -10_000L
        a.performSystemBack()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(
            "出这一页必须是站内**弹出**（animatePage 方向 -1）",
            -1,
            a.lastPageTransitionDirection,
        )
    }

    @Test
    fun backArrow_alsoPopsWithTheSubpageDirection() {
        val a = openSupportFromSettings()
        val back = find(a.root) {
            it.contentDescription?.toString() == "返回" && it is android.widget.ImageButton
        }
        assertNotNull("站内页必须有统一的左上角「返回」（aboutBackBar 提供）", back)
        back!!.performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("返回箭头也必须回设置", 4, a.pageKind)
        assertEquals("返回箭头必须走弹出方向", -1, a.lastPageTransitionDirection)
    }

    // ── ③ 解锁动效 ≤300ms ────────────────────────────────────────────────

    @Test
    fun unlockAnimation_isInsideTheThreeHundredMsRedLine() {
        assertTrue(
            "解锁动效必须 ≤300ms（规范 §2.1 动效 VPA 红线）：实际 ${MainActivity.SUPPORT_UNLOCK_MS}",
            MainActivity.SUPPORT_UNLOCK_MS <= 300,
        )
        val src = source("app/src/main/java/cc/nkbr/lanzouplus/MainActivity.java")
        assertTrue(
            "解锁动效必须走那个 ≤300ms 的常量（不许再写死一个更大的数字）",
            src.contains(".setDuration(SUPPORT_UNLOCK_MS)"),
        )
        assertFalse("360ms 不得回来（>300ms 是历史事故复盘定的红线）", src.contains("setDuration(360)"))
    }

    // ── ④ 解锁在同一页里换内容，且防连点 ─────────────────────────────────

    @Test
    fun unlock_swapsContentInPlaceAndGuardsAgainstDoubleTap() {
        val a = openSupportFromSettings()
        val cta = find(a.supportBody) { it is Button && (it.text?.toString() ?: "").contains("诚信付费") }
        assertNotNull("主按钮必须还在", cta)
        assertFalse("点之前不该是解锁中", a.supportUnlocking)

        cta!!.performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue("点一次就该进入解锁流程", a.supportUnlocking)
        assertTrue("解锁后应切到感谢页", a.supportThankYouMode)
        assertEquals("点一次只该真正解锁一次", 1, a.supportUnlockInvocations)
        assertTrue(
            "感谢页要在同一个站内页里换内容（pageKind 不变，不跳窗口）",
            a.pageKind == MainActivity.PAGE_KIND_SUPPORT,
        )
        assertTrue("感谢页文案要在", textsIn(a.supportBody).any { it.contains("全部下载权限已开放") })

        cta.performClick()
        cta.performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("连点不许重复执行解锁流程（整页重建不是幂等的）", 1, a.supportUnlockInvocations)
    }

    // ── ⑤ 文案逐条搬过来，一个字都不许改 ─────────────────────────────────

    @Test
    fun pageCopy_isIdenticalToTheLegacyActivityPage() {
        val inApp = openSupportFromSettings()
        val inAppTexts = textsIn(inApp.supportBody).toSet()

        val legacy = Robolectric.buildActivity(SupportActivity::class.java).setup().get()
        shadowOf(Looper.getMainLooper()).idle()
        val legacyTexts = textsIn(legacy.root).toSet()

        assertEquals(
            "站内页的文案必须与旧的 SupportActivity 逐条相同（搬内容不许改字）",
            legacyTexts.sorted(),
            inAppTexts.sorted(),
        )    }

    @Test
    fun pageNeverBringsBackTheLinesTheUserDeleted() {
        val a = openSupportFromSettings()
        val joined = textsIn(a.supportBody).joinToString("\n")
        for (gone in listOf(
            "诚信付费 ￥5 · 一次付清 · 承诺永久更新",
            "诚信付费 · 自愿",
            "一次付清 · 承诺永久更新",
            "学生免费",
        )) {
            assertFalse("用户 2026-10-01 口述删掉的文案不得回来：「$gone」\n$joined", joined.contains(gone))
        }
        assertTrue("权益只留用户口述的那一条：\n$joined", joined.contains("每周自费续 1 万次 DeepSeek v4.1"))
        assertTrue("￥5 必须保持可见：\n$joined", joined.contains("￥5"))
        assertTrue("必须诚实说明是本地标记：\n$joined", joined.contains("解锁记录保存在本机 · 不付费也可以完整使用"))
    }
}
