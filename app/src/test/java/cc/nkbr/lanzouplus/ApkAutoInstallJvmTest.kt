package cc.nkbr.lanzouplus

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * [DFW-131 2026-10-03] APK 下载完成后自动弹出安装界面。
 *
 * ## 用户原话
 * > 「无论是更新软件，还是下载其他apk，下载好了之后，我的安装界面会直接自动提出来，
 * >  就是说会直接自动开始安装，然后让我点击授权，而不是一直在那个下载界面停着」
 *
 * ## 为什么以前不弹
 * `startResolvedTransfer()` 完成回调里本来就有自动安装：
 * ```java
 * if(entry.autoInstall) runOnUiThread(...)
 * ```
 * 但 `autoInstall` 的**唯一来源** `requestItemDownload(item,false)` 把它**写死成 false**，
 * 于是那句判断永远不成立，安装界面永远不出现。
 *
 * ## 修法
 * 判据从「那个从没被置真的 boolean」改成**文件类型**（是不是 `.apk`）——
 * 用户关心的是「我下的是个安装包」，不是「链路内部把哪个变量设成了什么」。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-560dpi")
class ApkAutoInstallJvmTest {

    companion object {
        @BeforeClass @JvmStatic fun silenceAutoImport() { MainActivity.LIBRARY_AUTO_IMPORT = false }

        private val repoRoot: File = run {
            var dir = File(System.getProperty("user.dir") ?: ".").absoluteFile
            while (!File(dir, "app/src/main/AndroidManifest.xml").isFile && dir.parentFile != null) dir = dir.parentFile!!
            dir
        }
    }

    private fun entry(name: String?): MainActivity.DownloadEntry {
        val e = MainActivity.DownloadEntry()
        e.name = name
        return e
    }

    /** APK 必须被认出来 —— 这是"下完弹不弹安装界面"的判据。 */
    @Test
    fun anApkIsRecognisedRegardlessOfLetterCase() {
        assertTrue("普通 .apk", MainActivity.isApkEntry(entry("Memento - 个人数据库 v5.9.3 Pro_@.apk")))
        assertTrue("大写 .APK 也要认", MainActivity.isApkEntry(entry("资源猫_v5.0.8纯净版.APK")))
        assertTrue("带空格带中文的都要认", MainActivity.isApkEntry(entry("东方无限-v1.0.0.apk")))
    }

    /** **非 APK 一律不许触发安装界面** —— 误触发会把用户吓一跳，也是权限滥用。 */
    @Test
    fun nonApkFilesNeverTriggerInstall() {
        for (name in listOf("报告.pdf", "音乐.mp4", "素材.zip", "说明.txt", "图片.png", "备份.apk.zip")) {
            assertFalse("「$name」不是 APK，不许弹安装界面", MainActivity.isApkEntry(entry(name)))
        }
    }

    /** 边界：空名、null 都不许抛异常（下载项刚建出来时 name 可能还没填）。 */
    @Test
    fun aNamelessEntryIsNotAnApkAndDoesNotThrow() {
        assertFalse("空文件名不算 APK", MainActivity.isApkEntry(entry("")))
        assertFalse("null 文件名不算 APK", MainActivity.isApkEntry(entry(null)))
        assertFalse("null 条目不算 APK", MainActivity.isApkEntry(null))
    }

    /**
     * **接线守卫：那条 else-if 分支必须真的在。**
     *
     * 上面几条只证明"判据对"，证明不了"它被接到了下载完成回调上"。
     * 少了这条，有人把分支删掉，上面照样全绿，而用户下完 APK 还是什么都不发生 ——
     * 正是这个 bug 原本的样子。
     */
    @Test
    fun theAutoInstallBranchIsActuallyWiredIntoTheCompletionCallback() {
        val source = File(repoRoot, "app/src/main/java/cc/nkbr/lanzouplus/MainActivity.java").readText()
        assertTrue(
            "下载完成回调里必须有「是 APK 就自动弹安装」这一条分支",
            source.contains("else if(isApkEntry(entry))runOnUiThread(()->{"),
        )
        assertTrue(
            "必须走 autoInstallCompletedEntry（它会再确认是 .apk、尊重来源策略、支持静默安装），" +
                "而不是直接 installEntry",
            source.contains("autoInstallCompletedEntry(entry);});"),
        )
    }

    /**
     * **判据不许再退回 `entry.autoInstall`。**
     *
     * 那个标志在普通下载路径上被写死成 false（`requestItemDownload(item,false)`），
     * 拿它当判据就是"永远不弹"。这条守的是别再改回去。
     */
    @Test
    fun theTriggerMustNotDependOnTheAutoInstallFlagAlone() {
        val source = File(repoRoot, "app/src/main/java/cc/nkbr/lanzouplus/MainActivity.java").readText()
        assertTrue(
            "必须存在以文件类型为判据的分支",
            source.contains("isApkEntry(entry)"),
        )
        assertFalse(
            "不许把写死为 false 的 requestItemDownload(item,false) 当成「用户不想自动装」的依据 —— " +
                "那个 false 只是没人改过，不是用户的意愿",
            source.contains("requestItemDownload(item,false)") &&
                !source.contains("isApkEntry(entry)"),
        )
    }
}
