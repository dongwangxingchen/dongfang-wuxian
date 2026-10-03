package cc.nkbr.lanzouplus

import android.os.Looper
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
import java.io.File

/**
 * [DFW-73] 用户 2026-10-01 真机反馈的两条：顶部提示统一 + 「检查更新」不该报失败。
 *
 * ## 用户原话
 * > "图一图二分别展现了它的样子和它滑入的时候那个特别烂的动画效果。我不是告诉你了，
 * >  让你全部都用图三的那个顶部的样式吗？那个顶部的那种出现方式、那个样子，
 * >  才是我们应该把所有弹窗都支持的。后续不管弹出什么，都是用这个弹出来的。"
 * > "检查更新是否起效呢？我现在点击后，它显示检查更新失败，无法获取更新信息。
 * >  最新版就最新版，有更新就弹出来。"
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class TopBannerAndUpdateJvmTest {

    companion object {
        @BeforeClass @JvmStatic fun silenceAutoImport() { MainActivity.LIBRARY_AUTO_IMPORT = false }

        private val repoRoot: File = run {
            var dir = File(System.getProperty("user.dir") ?: ".").absoluteFile
            while (!File(dir, "app/src/main/AndroidManifest.xml").isFile && dir.parentFile != null) dir = dir.parentFile!!
            dir
        }
    }

    private fun activity(): MainActivity {
        val a = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        shadowOf(Looper.getMainLooper()).idle()
        return a
    }

    private fun source(path: String) = File(repoRoot, path).readText(Charsets.UTF_8)

    // ── 全站提示统一成顶部通知条 ──────────────────────────────────────────

    @Test
    fun showNotice_usesTheTopBanner_notTheOldFloatingPill() {
        val a = activity()
        a.showNotice("初始界面已设为AI对话", false)
        shadowOf(Looper.getMainLooper()).idle()

        assertNotNull("showNotice 必须走顶部通知条（图三那套），不能再飘右上角的小胶囊", a.activeNoticeBanner)
        assertEquals(
            "旧的飘窗层不该再被 showNotice 用到（它那套『从左边横滑 + 淡入』就是用户说的割裂感）",
            0,
            a.toastLayer.childCount,
        )
    }

    @Test
    fun showNotice_keepsOneAtATimeAndQueuesTheRest() {
        /*
         * [DFW-137 2026-10-03] 这条原来叫 `showNotice_replacesInsteadOfStacking`，
         * 断言的是「第二条必须把第一条顶掉」。
         *
         * 要求本身是**「同一时刻只留一条，不许叠罗汉」**，顶掉只是当时选的手段。
         * 那个手段有个实测出来的副作用：新提示一来就把上一条 dismiss 掉，
         * 两条相隔不到显示时长时**第一条会被当场砍掉，用户一个字都没看到**——
         * 那正是「话太多」的真实体感（不是字多，是说了等于没说）。
         *
         * 现在仍然是「同一时刻只留一条」，但改成**排队**：
         * 第一条说完，第二条再上。所以这里断言两件事——
         * ①屏幕上只有一条（不叠罗汉）；②第二条没被丢掉（在队列里等着）。
         */
        val a = activity()
        a.showNotice("第一条", false)
        shadowOf(Looper.getMainLooper()).idle()
        val first = a.activeNoticeBanner
        assertNotNull("第一条应该已经在显示", first)
        a.showNotice("第二条", false)
        shadowOf(Looper.getMainLooper()).idle()
        assertNotNull(a.activeNoticeBanner)
        assertTrue("同一时刻只留一条，不许叠罗汉", first === a.activeNoticeBanner)
        assertEquals("第二条必须排队等着，不许被丢掉", 1, a.pendingNotices.size)
    }

    @Test
    fun updateNotice_sharesTheSameConstructor() {
        // 两处各写一份卡片构造 = 改样式时必然只改一处，那正是"两个组件看着不一样"的来源。
        val src = source("app/src/main/java/cc/nkbr/lanzouplus/MainActivity.java")
        val start = src.indexOf("void showUpdateNotice(")
        assertTrue("找不到 showUpdateNotice", start > 0)
        val block = src.substring(start, minOf(src.length, start + 400))
        assertTrue(
            "showUpdateNotice 必须复用 showTopBanner（唯一差异只该是图标）",
            block.contains("showTopBanner("),
        )
        assertTrue(
            "showNotice 也必须复用同一个构造点",
            src.substring(src.indexOf("public void showNotice(")).take(400).contains("showTopBanner("),
        )
    }

    @Test
    fun banner_isNonModal_andCanBeDismissed() {
        // 通知不是对话框：不许有遮罩、不许吃掉空白处点击（NoticeBanner 的契约）。
        val src = source("app/src/main/java/cc/nkbr/lanzouplus/NoticeBanner.java")
        assertTrue("顶部通知条必须明确标注为非模态", src.contains("非模态"))
        assertFalse("不许给通知条加遮罩", src.contains("SCRIM_ALPHA"))
    }

    // ── 检查更新：后台能回答就别报失败 ────────────────────────────────────

    @Test
    fun backendAnswerIsAuthoritative_noGithubFallbackWhenItCanConclude() {
        val src = source("app/src/main/java/cc/nkbr/lanzouplus/MainActivity.java")
        val start = src.indexOf("UpdateOffer resolveUpdateOffer()")
        assertTrue("找不到 resolveUpdateOffer", start > 0)
        val block = src.substring(start, minOf(src.length, start + 4000))
        val marker = "if(fromServer!=null&&fromServer.versionCode>BuildConfig.VERSION_CODE)return fromServer;"
        val at = block.indexOf(marker)
        assertTrue("resolveUpdateOffer 必须保留「后台版本更新就直接用它」这条", at > 0)
        // 取「后台分支」的整段：从这条 return 之后，到 GitHub 兜底那一段之前。
        val after = block.substring(at + marker.length)
        val branchEnd = after.indexOf("// 后台不可达")
        assertTrue("后台分支必须能被切出来（找不到 GitHub 兜底那一行的注释）", branchEnd > 0)
        val branch = after.substring(0, branchEnd)
        assertTrue(
            "后台可达且回答了「最新版本」之后必须**就此定论**（return null），实际这一分支里没有 return null：" + branch,
            branch.contains("return null;"),
        )
        assertFalse(
            "后台能回答的问题，绝不许再往下打 GitHub 兜底 —— 那条路径 404 时用户看到的就是「检查更新失败」：" + branch,
            branch.contains("UpdateClient.check("),
        )
    }

    @Test
    fun updateClient_treatsHttp404AsNoRelease_notAsFailure() {
        assertTrue(
            "404 表示「这个源上没有任何正式版本」，不是故障 —— 把它当失败就是用户看到的「检查更新失败」",
            UpdateClient.isNoReleaseError("更新请求失败 HTTP 404"),
        )
        assertFalse(
            "别的错误码仍然是真故障，不许被这条吞掉",
            UpdateClient.isNoReleaseError("更新请求失败 HTTP 500"),
        )
        assertFalse(UpdateClient.isNoReleaseError(null))
        assertFalse(UpdateClient.isNoReleaseError("更新地址不受信任"))
    }

    // ── 页面不透明：修「上半部分打开了、下半部分透明」 ────────────────────

    @Test
    fun everyPageFrameIsOpaque() {
        val a = activity()
        assertNotNull("页面帧必须自带底色（否则转场时下面那截会透出上一页）", a.pageFrame!!.background)
        a.showSettings()
        shadowOf(Looper.getMainLooper()).idle()
        assertNotNull("设置页的页面帧也必须不透明", a.pageFrame!!.background)
        a.showCrashLogPage()
        shadowOf(Looper.getMainLooper()).idle()
        assertNotNull(
            "崩溃日志页的页面帧也必须不透明 —— 用户报的就是它『上半部分打开、下半部分透明』",
            a.pageFrame!!.background,
        )
    }

    @Test
    fun crashLogEntry_doesNotLeaveTheOldPageVisibleUnderneath() {
        val a = activity()
        a.showSettings()
        shadowOf(Looper.getMainLooper()).idle()
        a.showCrashLogPage()
        shadowOf(Looper.getMainLooper()).idle()
        // 转场结束后 pageHost 里只该剩当前页；且当前页是不透明的
        assertEquals("转场结束后不该还有旧页挂在 pageHost 里", 1, a.pageHost.childCount)
        assertNotNull(a.pageFrame!!.background)
    }

    @Test
    fun oldFloatingToastHelpersAreStillUsedOnlyByDownloadCards() {
        // showNotice 已经不走旧底座了；但下载进度卡片（带进度条的那种）仍然用它，
        // 这条只是防止有人"顺手"把 addToastPanel 删掉导致下载卡片编译不过。
        val src = source("app/src/main/java/cc/nkbr/lanzouplus/MainActivity.java")
        assertTrue(src.contains("void addToastPanel("))
        assertTrue(src.contains("void dismissPanel("))
    }

    @Test
    fun updateNoticeIconAndNoticeIconAreDifferent() {
        val src = source("app/src/main/java/cc/nkbr/lanzouplus/MainActivity.java")
        assertTrue(src.contains("R.drawable.ic_notifications));"))
        assertTrue(src.contains("R.drawable.ic_refresh));"))
    }

    @Test
    fun noNoticeIsSilentlySwallowedBeforeTheHostExists() {
        // showNotice 现在依赖 sheetHost()；host 还没建好时不能 NPE（旧实现会直接崩在 toastLayer.addView）。
        val src = source("app/src/main/java/cc/nkbr/lanzouplus/MainActivity.java")
        val start = src.indexOf("void showTopBanner(")
        assertTrue(start > 0)
        /*
         * [2026-10-02 Lead 修正] 这里原来写死「从方法名往后 700 个字符」——**是个脆弱锚点**。
         *
         * 判空那行当时在偏移 679 处，**离窗口边界只剩 21 个字符**。
         * 于是任何人往这个方法里加几行注释，测试就会假红 —— 当天就真的踩到了
         * （给面包屑埋点补注释，把判空挤到 909，测试立刻红）。
         *
         * 一条会因为"加了注释"就变红的测试，守不住任何东西，只会让人不信任门禁。
         * 改成**取整个方法体**：从方法名往后，到下一个「两个空格缩进的非空白字符」为止 ——
         * 那要么是方法自己的收尾大括号，要么是下一个同层成员声明，两种情况都对。
         */
        val rest = src.substring(start)
        val nextDecl = Regex("\\n  \\S").find(rest, 1)
        val block = if (nextDecl != null) rest.substring(0, nextDecl.range.first) else rest
        assertTrue("必须先判容器是否可用再建通知条", block.contains("if(container==null)return;"))
    }
}
