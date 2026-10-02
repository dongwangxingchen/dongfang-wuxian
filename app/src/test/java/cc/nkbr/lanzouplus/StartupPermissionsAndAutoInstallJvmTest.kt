package cc.nkbr.lanzouplus

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * [DFW-97] 首启权限引导 + 下载完自动安装。
 *
 * ## 用户 2026-10-02 原话
 * > 「一开软件就要『存储权限』和『通知权限』，其他的按照需要打开，明白不。
 * >   然后默认改为下载好自动跳转安装界面。」
 *
 * ## 为什么这些必须钉住
 * 这两件事都属于"**静默失效**"那一类：写错了不会崩、不会报错，
 * 只会表现为"用户觉得这软件怎么没反应"——
 *  · 首启不问存储权限 → 用户点下载，直接失败，还以为软件坏了；
 *  · 不问通知权限 → 下载进度永远看不到（Android 13+ 默认拒绝）；
 *  · 自动安装没开 → 下载完什么都不发生，用户不知道下一步该干嘛。
 * 三种都不会有异常日志，只有用户困惑。所以用测试钉死。
 */
class StartupPermissionsAndAutoInstallJvmTest {

    private val root: File = run {
        var dir = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (!File(dir, "AGENTS.md").isFile && dir.parentFile != null) dir = dir.parentFile
        dir
    }

    private fun read(rel: String) = File(root, rel).readText(Charsets.UTF_8)

    private val manifest get() = read("app/src/main/AndroidManifest.xml")
    private val activity get() = read("app/src/main/java/cc/nkbr/lanzouplus/MainActivity.java")

    /**
     * 守卫本体抽成函数 —— 这样"反向探针"才能**真的把坏代码喂进来**。
     *
     * ⚠️ 本项目踩过的假绿教训：反向探针如果写成
     * `assertTrue("...".contains("..."))`（两个操作数都是字面量），
     * 编译期恒真、跟产品代码毫无关系，等于没有探针。见 docs/agents/lessons.md 八-2。
     */
    private fun asksForStorageOnStartup(src: String): Boolean =
        src.contains("requestStartupPermissions") &&
            src.contains("requestManageAllFilesAccess(") &&
            Regex("requestStartupPermissions[\\s\\S]{0,400}requestManageAllFilesAccess").containsMatchIn(src)

    private fun asksForNotifications(src: String): Boolean =
        src.contains("POST_NOTIFICATIONS") && src.contains("STARTUP_NOTIFICATION_PERMISSION")

    private fun autoInstallsByDefault(src: String): Boolean =
        Regex("void beginDownload\\(Models\\.Item item\\)\\s*\\{\\s*beginDownload\\(item,null,true\\)")
            .containsMatchIn(src)

    // ── 反向探针：坏代码必须被判为"有问题" ──────────────────────────────

    @Test
    fun theGuardsActuallyDetectBadCode() {
        assertFalse(
            "没接启动引导的代码必须被判红",
            asksForStorageOnStartup("void beginDownload(Models.Item item){beginDownload(item,null,false);}"),
        )
        assertFalse("不问通知权限的代码必须被判红", asksForNotifications("requestPermissions(new String[]{\"x\"}, 1);"))
        assertFalse(
            "写死 autoInstall=false 的代码必须被判红",
            autoInstallsByDefault("void beginDownload(Models.Item item){beginDownload(item,null,false);}"),
        )
        // 好代码必须被判绿
        assertTrue(
            "真接上了就该判绿",
            asksForStorageOnStartup(
                "void requestStartupPermissions(){requestManageAllFilesAccess(\"x\",this::next,true);}",
            ),
        )
        assertTrue(
            "真接上了就该判绿",
            autoInstallsByDefault("void beginDownload(Models.Item item){\n    beginDownload(item,null,true);\n  }"),
        )
    }

    // ── ① 首启要存储权限 ────────────────────────────────────────────────

    @Test
    fun startupAsksForStorageFirst() {
        assertTrue(
            "首启必须主动要存储权限（不问的话用户点下载会直接失败，还不知道为什么）",
            asksForStorageOnStartup(activity),
        )
        assertTrue(
            "只许问一次，之后进出页面不再骚扰",
            activity.contains("askedStartupPermissions"),
        )
        assertTrue(
            "要放在首屏画出来之后再问，否则系统弹窗会被启动过程盖住",
            Regex("askedStartupPermissions[\\s\\S]{0,200}postDelayed").containsMatchIn(activity),
        )
    }

    // ── ② 首启要通知权限 ────────────────────────────────────────────────

    @Test
    fun startupAsksForNotificationsToo() {
        assertTrue(
            "清单里必须有 POST_NOTIFICATIONS（Android 13+ 是运行时权限，不声明连问都问不了）",
            manifest.contains("android.permission.POST_NOTIFICATIONS"),
        )
        assertTrue("代码里必须真的去要这个权限", asksForNotifications(activity))
        assertTrue(
            "必须是先存储后通知 —— 存储是命脉，通知只是看得见进度",
            activity.indexOf("requestStartupPermissions()") < activity.indexOf("requestStartupNotificationPermission()")
                || Regex("requestStartupPermissions[\\s\\S]{0,600}requestStartupNotificationPermission")
                    .containsMatchIn(activity),
        )
        assertTrue(
            "低版本系统没有这个权限，必须跳过而不是硬要（否则在 Android 12 上会抛）",
            Regex("requestStartupNotificationPermission[\\s\\S]{0,400}SDK_INT<33").containsMatchIn(activity),
        )
    }

    // ── ③ 下载完自动跳安装界面 ──────────────────────────────────────────

    @Test
    fun downloadsAutoInstallByDefault() {
        assertTrue(
            "软件库点下载必须默认自动安装（用户 2026-10-02 明确要求）",
            autoInstallsByDefault(activity),
        )
        assertTrue(
            "自动安装仍要过来源策略：外部链接与来源不明记录必须仍然需要确认",
            activity.contains("allowsAutomaticInstall"),
        )
        assertTrue(
            "策略本体必须保持 fail-closed（未知来源一律要确认）",
            read("app/src/main/java/cc/nkbr/lanzouplus/DownloadSourcePolicy.java")
                .contains("return EXTERNAL.equals(source) || LEGACY.equals(source);"),
        )
    }
}
