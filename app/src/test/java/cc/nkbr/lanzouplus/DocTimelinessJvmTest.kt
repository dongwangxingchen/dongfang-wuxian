package cc.nkbr.lanzouplus

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * [DFWX] 文档守卫：时效性 + 公开安全性。
 *
 * ## 危害一：过期文档被当成事实源（DFW-5）
 * 曾经有一份仓库外的「项目认知」笔记停更于 v1.2.1 / 2026-09-09，却被
 * `AGENTS.md` 列为「会话开始第 1 件事」。它含 Windows 路径、旧 minSdk/compileSdk/AGP，
 * 以及**已被移除的"签名口令明文写在源码里"**（v1.9.0 起口令只在 `local.properties`）。
 * 照它建立全貌会被带偏——本项目已实际踩到过。
 * 现在 `AGENTS.md` 不再指向任何仓库外笔记，事实一律以 `docs/plan/current-state.md` 为准。
 *
 * ## 危害二：公开文件里写了本机环境（2026-10-04 修）
 * `AGENTS.md` 曾经写满本机信息——macOS 用户名（等于真名拼音）、内网 IP、手机型号、
 * 服务器规格与到期日、凭据文件位置、本地目录结构。**这个仓库是公开的。**
 * 现在 `agentsMd_isPublicSafe` 用机械判据守着这一条。
 *
 * `docs/design/wear-ui-system.md` 同理：按 Wear 圆屏写，与「只测手机」（decisions #13）冲突，
 * 但其中的 MotionToken / 形状 / 字阶等**仍然有效**，所以不打散、只标注适用范围。
 */
class DocTimelinessJvmTest {

    private val root: File = run {
        var dir = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (!File(dir, "AGENTS.md").isFile && dir.parentFile != null) dir = dir.parentFile
        dir
    }

    private fun read(rel: String) = File(root, rel).readText(Charsets.UTF_8)

    /**
     * [DFW-97 审计] **交接文档的抬头版本号必须跟得上当前版本。**
     *
     * 为什么专门加这条：`docs/handover/README.md` 是「30 秒接手的单一入口」，
     * 但它**连续两轮审计都被点出过期**——
     * 上一轮写 1.0.1（实际 1.0.3），这一轮写 1.0.21（实际 1.0.25，落后 4 个版本），
     * §4 的表里 5 张卡标着"待做"其实全部已发版。
     *
     * 机制原因：原有的 `DocTimelinessJvmTest` 只断言"文件里**某处**出现过当前 versionName"，
     * 而 `release.sh` 只 sed 版本号那一行 —— 于是**版本号行自动同步、抬头永远烂**。
     * 这条测试改成直接盯抬头那一句。
     *
     * 该目录按 AGENTS.md 是**故意不提交**的本机现场，所以 CI 上不存在：
     * 文件不在就跳过（这是设计，不是失败）。
     */
    @Test
    fun handoverHeaderTracksTheCurrentVersion() {
        val handover = File(root, "docs/handover/README.md")
        if (!handover.isFile) return   // CI / 换机器时不存在，属正常

        val text = handover.readText(Charsets.UTF_8)
        // [DFW-118 路线 B] 只读 `val appVersionName = "x.y.z"` 这一处，
        // versionCode 由它推导（major*10000 + minor*100 + patch）——
        // 与 app/build.gradle.kts 里的推导规则保持同一份算术。
        val gradle = read("app/build.gradle.kts")
        val versionName = Regex("val appVersionName = \"([0-9.]+)\"").find(gradle)!!.groupValues[1]
        val buildCode = versionName.split(".")
            .let { (major, minor, patch) -> major.toInt() * 10000 + minor.toInt() * 100 + patch.toInt() }
            .toString()

        assertTrue(
            "交接文档抬头必须写着当前版本 $versionName（内部序号 $buildCode）（这是「30 秒接手」的第一句话，" +
                "写错会让人一开始就按错误的版本认知开工）：" +
                text.lines().take(20).joinToString(" | ") { it.trim().take(60) },
            text.lines().take(20).any { it.contains(versionName) },
        )
        assertFalse(
            "§4 里不许还留着「待做」—— 做完了就要改，否则接手的人会去做已经做完的事",
            text.contains("| 待做 |"),
        )
    }

    /**
     * AGENTS.md 是**公开仓库**的一部分，必须只写项目规范、不写本机环境。
     *
     * ## 为什么这条比原来的更严
     * 原来只断言「AGENTS.md 有没有提醒那个过期文档」。2026-10-04 发现真正的危害更大：
     * 那份文件里塞满了**本机信息**——macOS 用户名（等于真名拼音）、内网 IP、
     * 手机型号、服务器规格与到期日、凭据文件位置、本地目录结构。
     * 这些在公开仓库里等于给作者做了个完整画像。
     *
     * 所以改成三条：**该指的指到、该提的提到、不该有的一律不能有**。
     * 第三条是新增的机械判据——靠人自觉不写隐私，拦截率是 0。
     */
    @Test
    fun agentsMd_isPublicSafe() {
        val agents = read("AGENTS.md")

        // ① 当前事实源必须指对
        assertTrue(
            "必须指明当前事实源是 docs/plan/current-state.md",
            agents.contains("docs/plan/current-state.md"),
        )

        // ② 安全红线必须提到
        assertTrue(
            "必须点出签名口令只在 local.properties 且该文件不入库",
            agents.contains("local.properties"),
        )

        // ③ 本文件必须声明自己是公开的（提醒后续维护者）
        assertTrue(
            "AGENTS.md 必须声明「本文件是公开仓库的一部分」，否则后人会继续往里写机器信息",
            agents.contains("公开仓库"),
        )

        // ④ 机械判据：任何本机特征都不许出现
        val forbidden = mapOf(
            "macOS 家目录（会泄露真名拼音）" to Regex("""/Users/[A-Za-z0-9._-]+"""),
            "内网 IP" to Regex("""\b192\.168\.\d+\.\d+"""),
            "本地家目录相对路径" to Regex("""~/[A-Za-z0-9._-]+/"""),
            "手机型号/无线调试端口" to Regex("""\b5555\b"""),
            "凭据文件位置" to Regex("""server\.txt"""),
            "本机代理端口" to Regex("""127\.0\.0\.1:7890"""),
        )
        val hits = forbidden.filterValues { it.containsMatchIn(agents) }.keys
        assertTrue(
            "AGENTS.md 是公开文件，不得出现本机信息。命中：$hits\n" +
                "这些内容应该写进仓库外的本地笔记，不要提交。",
            hits.isEmpty(),
        )
    }

    /** wear 设计规范必须标注"手表尺寸约束已停用"，同时保留仍然有效的 token 部分。 */
    @Test
    fun wearDesignDoc_marksWatchScopeObsoleteButKeepsTokens() {
        val doc = read("docs/design/wear-ui-system.md")
        assertTrue(
            "必须标注手表尺寸约束已停用（decisions #13：只测手机）",
            doc.contains("手表尺寸约束已停用"),
        )
        assertTrue("必须引用决策编号，便于追溯", doc.contains("#13"))
        // 仍然有效的 token 章节不得被删（这是"不要删、只标注"的要求）
        for (section in listOf("MotionTokens", "按压反馈", "形状", "字阶", "图标语言")) {
            assertTrue("仍然有效的「$section」章节不得被删掉", doc.contains(section))
        }
    }

    /** 文档里不得再出现"口令明文写在源码里"这类已经被修掉的事实描述。 */
    @Test
    fun docs_doNotClaimPasswordsAreInSource() {
        // 说明：AGENTS.md 里会**引用**这个错误说法来警示，所以只检查真正的规范/设计文档
        for (rel in listOf("docs/design/wear-ui-system.md")) {
            val text = read(rel)
            assertFalse(
                "$rel 不得声称签名口令写在源码里（v1.9.0 起只在 local.properties）",
                text.contains("明文写在 app/build.gradle.kts"),
            )
        }
    }

    /** 事实页必须带上当前版本号，接手者据此核时效。 */
    @Test
    fun currentStateDoc_carriesCurrentVersion() {
        val state = read("docs/plan/current-state.md")
        assertTrue(
            "current-state.md 必须写明当前 versionName（接手者据此判断时效）",
            state.contains("versionName") && state.contains(BuildConfig.VERSION_NAME),
        )
    }
}
