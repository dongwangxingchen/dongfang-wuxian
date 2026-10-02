package cc.nkbr.lanzouplus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * [DFW-91] 版本号**只保留一处**，并且这一处**不用手改**。
 *
 * ## 用户原话（2026-10-01）
 * > 「版本号的话，你要是改一堆地方的话……可能会漏掉。你就找一个地方放版本号吧，
 * > 在**检查更新四个字右侧**放着版本号。然后关于东方无限底部的和其他地方显示版本号的地方，
 * > 你都给我删除，优化掉。**不然的话，你每次更新都得改一堆地方。**」
 *
 * ## 为什么"改一堆地方"是真问题（不是用户多虑）
 * 上一个窗口就**真的漏过一次**：改完版本号没同步 `docs/plan/current-state.md`，
 * 是 `DocTimelinessJvmTest` 变红才发现的。文档会腐烂，门禁不会。
 *
 * ## 这条测试守四件事
 * 1. **UI 上只有一处**版本号（「检查更新」右侧），别处不许再渲染；
 * 2. 那一处是**自动来的**（`BuildConfig.VERSION_NAME`），不是硬编码字符串；
 * 3. **代码里只有一个数字**（`val buildCode`），versionName 由它算出来；
 * 4. `tools/release.sh` 是**自动同步**事实页，而不是"不同步就报错"——
 *    报错靠人记得改，自动写入才是想漏都漏不掉。
 */
class VersionNumberSinglePlaceJvmTest {

    private val root: File = run {
        var dir = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (!File(dir, "AGENTS.md").isFile && dir.parentFile != null) dir = dir.parentFile
        dir
    }

    private fun read(rel: String) = File(root, rel).readText(Charsets.UTF_8)

    private val main by lazy { read("app/src/main/java/cc/nkbr/lanzouplus/MainActivity.java") }

    /** UI 上唯一允许出现版本号的那一行：设置页「检查更新」。 */
    private val checkUpdateRow =
        "settingsAction(R.drawable.ic_refresh,\"检查更新\",BuildConfig.VERSION_NAME,v->manualCheckForUpdates())"

    /**
     * 守卫本体抽成函数，**反向探针才能把坏代码真的喂进来**。
     *
     * ⚠️ 原来那版第一条探针是 `bad.contains("BuildConfig.VERSION_NAME") && bad.contains("text(")`——
     * 两个操作数都是字符串字面量，**编译期恒真**，跟产品代码毫无关系，等于"假装验过了"。
     * 正确写法见 `RenderFolderNullSafetyJvmTest.theGuardActuallyDetectsAnUnguardedCall`。
     */
    private fun linesRenderingTheVersion(src: String): List<String> =
        src.lines().filter { it.contains("BuildConfig.VERSION_NAME") && it.contains("text(") }

    private fun hardCodesVersionName(src: String): Boolean =
        Regex("versionName = \"\\d+\\.\\d+\\.\\d+\"").containsMatchIn(src)

    @Test
    fun versionNameIsRenderedInExactlyOnePlace() {
        assertTrue("设置页「检查更新」必须把版本号放在右侧（DFW-91）", main.contains(checkUpdateRow))

        // 版本号不许再被 text(...) 直接渲染成 UI 文本。
        val rendered = linesRenderingTheVersion(main)
        assertTrue(
            "版本号只允许在「检查更新」那一行出现；这几行又在渲染它了：" +
                rendered.joinToString(" | ") { it.trim().take(90) },
            rendered.isEmpty(),
        )

        // 「已是最新版本（1.0.22）」这类文案也要去掉 —— 版本号就在上一行，不丢信息。
        assertFalse(
            "「已是最新版本」后面不许再拼版本号（用户要求别处全删）",
            main.contains("\" 已是最新版本（\"+BuildConfig.VERSION_NAME"),
        )
    }

    @Test
    fun aboutPageNoLongerCarriesTheVersion() {
        assertFalse(
            "「关于东方无限」页底部原先有一行 `PRODUCT_NAME+\"  \"+BuildConfig.VERSION_NAME`，必须删掉",
            main.contains("TextView verText=text(PRODUCT_NAME+\"  \"+BuildConfig.VERSION_NAME"),
        )
    }

    /**
     * 维护页的隐藏后门**必须活着**，但**不许再显示版本号**，点击也不许有任何反馈。
     *
     * 后门是应急逃生口：后台误开维护模式时，用户唯一的自助通道。
     * 一旦点击有反馈（提示/震动/变色），这个后门就会被普通用户发现。
     */
    @Test
    fun maintenanceBackdoorSurvivesButShowsNoVersion() {
        assertFalse(
            "维护页那行不许再显示版本号（用户要求版本号只留一处）",
            main.contains("TextView version=text(PRODUCT_NAME+\" \"+BuildConfig.VERSION_NAME"),
        )
        assertTrue(
            "维护页那行仍要是可点的（后门靠连点 7 次解锁）",
            main.contains("TextView version=text(PRODUCT_NAME,11,MUTED)"),
        )
        assertTrue(
            "后门必须仍然调用 maintenanceGate().tapVersion()",
            main.contains("maintenanceGate().tapVersion()"),
        )
        assertTrue(
            "第 7 次点击后要弹出说明（用户亲自写的文案）",
            main.contains("void showBackdoorNotice()"),
        )
        assertTrue(
            "说明文案必须是用户给的那段，一字不改",
            main.contains("测试后门已开启，这是我刻意留下的备用测试通道，如果你是用户，还请小心使用"),
        )
        assertTrue(
            "文案最后那句「我无法保障你的安全」必须完整保留（不能被通知条截断，所以用对话框）",
            main.contains("我无法保障你的安全。"),
        )
    }

    @Test
    fun versionNameIsDerivedFromASingleNumber() {
        val gradle = read("app/build.gradle.kts")
        assertTrue(
            "版本号必须只有一个数字来源：`val buildCode = <数字>`（DFW-91）",
            Regex("val buildCode = \\d+").containsMatchIn(gradle),
        )
        /*
         * [DFW-97] 规则变了：用户 2026-10-02 要求测试期 versionName 固定 1.0.0
         * （「我还没发布正式版呢，咱们做的一直是测试版」）。
         *
         * 所以原来那条"versionName 必须由 buildCode 算出来"作废了 ——
         * 继续留着只会逼人把版本名改回去。
         *
         * 真正要守的换成两条：
         * ① **仍然只有一个数字来源**（`val buildCode`），改版本只改它一个；
         * ② versionName 固定为 1.0.0，且 versionCode 由 buildCode 提供（只增不减，见 VersionSchemeJvmTest）。
         */
        assertTrue(
            "versionName 必须固定为 1.0.0（DFW-97 测试期约定）",
            gradle.contains("versionName = \"1.0.0\""),
        )
        assertTrue(
            "versionCode 必须仍然由 buildCode 提供（只改一个数字，DFW-91）",
            gradle.contains("versionCode = buildCode"),
        )
        assertFalse(
            "不许把 buildCode 直接写进 versionName（那会退回到「测试期版本名乱跳」）",
            gradle.contains("versionName = \"\${buildCode"),
        )
    }

    /**
     * 事实页的同步必须是**自动写入**，不能只是"不同步就报错"——
     * 报错靠人记得改，上一个窗口就是漏了之后被测试抓出来的。
     */
    @Test
    fun releaseScriptAutoSyncsTheFactPage() {
        val sh = read("tools/release.sh")
        assertTrue("release.sh 必须从 buildCode 读版本号（唯一来源）", sh.contains("val buildCode = [0-9]+"))
        assertTrue(
            "release.sh 必须**自动写入** current-state.md，而不是只做校验后报错",
            sh.contains("SED_INPLACE") && sh.contains("current-state.md 已同步到 \${name}（自动写入）"),
        )
        assertFalse(
            "旧写法（只 fail 不自动改）必须消失",
            sh.contains("未同步到当前版本 \${name}（接手者会读到旧状态）"),
        )
        // [DFW-91] 出包必须先过 verify。
        // 以前 verify 与 build 是两个互不相干的子命令，只跑 build 就直接出包 ——
        // v1.0.15 那次"带红门禁发布"就是这么发生的。现在 build 自己先跑 verify。
        assertTrue(
            "release.sh 的 cmd_build 必须自己先调用 cmd_verify（否则又能绕过门禁出包）",
            Regex("cmd_build\\(\\)\\s*\\{[^}]*cmd_verify").containsMatchIn(sh),
        )
    }

    /**
     * shell 脚本里 `$变量` 后面**紧跟中文**是个真陷阱：
     * bash 会把全角字符的字节当成变量名的一部分，报 `name?: unbound variable`。
     * 本次实际踩到过（`ok "…已同步到 $name（自动写入）"` 直接让 release.sh 崩在 verify 阶段），
     * 而且另外两处潜伏在**只在出错分支才执行**的 echo 里 —— 平时跑不到，出事时才发现。
     * 一律写成 `${变量}`。
     */
    @Test
    fun shellScriptsNeverGlueAVariableToChineseText() {
        val trap = Regex("\\\\$[A-Za-z_][A-Za-z0-9_]*[^\\x00-\\x7F]")
        // 扫描 tools/ 下**所有** shell 脚本 —— 写死三个文件名是不够的：
        // 本次新加的 tools/bump-version.sh 就立刻又踩了同一个坑（`$CODE（`）。
        val scripts = File(root, "tools").listFiles { f -> f.name.endsWith(".sh") }!!
            .map { "tools/" + it.name }.sorted()
        assertTrue("tools/ 下应当有若干 shell 脚本，扫描列表不该为空", scripts.size >= 4)
        for (rel in scripts) {
            val hits = read(rel).lines().withIndex()
                .flatMap { (i, line) -> trap.findAll(line).map { "${rel}:${i + 1} ${it.value}" } }
            assertTrue(
                "这些地方 `\$变量` 后面紧跟中文，bash 会把中文字节吞进变量名：${hits.joinToString(" | ")}",
                hits.isEmpty(),
            )
        }
    }

    /** 反向探针：确认上面的检查真的能识别坏代码，不是永远为真。 */
    @Test
    fun theChecksActuallyDetectBadCode() {
        // ① 别处又渲染版本号 —— 必须被真守卫抓出来
        assertTrue(
            "坏代码（别处又渲染版本号）必须被识别",
            linesRenderingTheVersion(
                "TextView v=text(PRODUCT_NAME+\" \"+BuildConfig.VERSION_NAME,12,MUTED);",
            ).isNotEmpty(),
        )
        assertTrue(
            "好代码（只是把版本号当参数传给设置项，没有 text() 渲染）不该被误判",
            linesRenderingTheVersion(
                "settingsAction(R.drawable.ic_refresh,\"检查更新\",BuildConfig.VERSION_NAME,v->x())",
            ).isEmpty(),
        )
        // ② versionName 又写死成字符串 —— 必须被正则抓到
        assertTrue(
            "坏代码（versionName 写死）必须被识别",
            hardCodesVersionName("""  versionName = "1.0.22""""),
        )
        // ③ 好代码不该被误判
        assertFalse(
            "好代码（由 buildCode 推导）不该被误判成写死",
            hardCodesVersionName("""  versionName = "${'$'}{buildCode / 10000}.x""""),
        )
        // ④ 唯一那处 UI 的锚点字符串必须真的在源码里，否则上面全在自说自话
        assertEquals(
            "「检查更新」那一行的锚点必须与源码逐字一致",
            1,
            Regex(Regex.escape(checkUpdateRow)).findAll(main).count(),
        )
    }
}
