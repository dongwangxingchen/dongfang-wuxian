package cc.nkbr.lanzouplus

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * [DFWX] DFW-5 文档时效治理守卫。
 *
 * ## 发现的危害
 * `~/heiyao/东方无限-项目认知.md` 停更于 **v1.2.1 / 2026-09-09**，而 `src/AGENTS.md` 第一节
 * 把它列为「会话开始第 1 件事」。它含 Windows 路径、旧 minSdk/compileSdk/AGP、
 * 以及**已被移除的"签名口令明文写在源码里"**（v1.9.0 起口令只在 `local.properties`）。
 * 照它建立全貌会被带偏——本项目已实际踩到过。
 *
 * 该文件在仓库外（`~/heiyao/`，非 git 仓），本卡不改它，而是**修掉指向它的那条指令**：
 * 让接手者知道它是历史背景、事实以 `docs/plan/current-state.md` 为准。
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
        val versionName = read("app/build.gradle.kts")
            .let { Regex("val buildCode = (\\d+)").find(it)!!.groupValues[1].toInt() }
            .let { "${it / 10000}.${(it / 100) % 100}.${it % 100}" }

        assertTrue(
            "交接文档抬头必须写着当前版本 $versionName（这是「30 秒接手」的第一句话，" +
                "写错会让人一开始就按错误的版本认知开工）：" +
                text.lines().take(20).joinToString(" | ") { it.trim().take(60) },
            text.lines().take(20).any { it.contains(versionName) },
        )
        assertFalse(
            "§4 里不许还留着「待做」—— 做完了就要改，否则接手的人会去做已经做完的事",
            text.contains("| 待做 |"),
        )
    }

    /** AGENTS.md 不得再把过期文件当成会话开始的事实源。 */
    @Test
    fun agentsMd_warnsAgainstStaleProjectDoc() {
        val agents = read("AGENTS.md")
        assertTrue(
            "AGENTS.md 必须明确提示「项目认知文档」已过期、不要再当事实源",
            agents.contains("不要再把") && agents.contains("东方无限-项目认知.md"),
        )
        assertTrue(
            "必须指明当前事实源是 current-state.md",
            agents.contains("docs/plan/current-state.md"),
        )
        assertTrue(
            "必须点出最危险的过期内容（口令明文写在源码里）已被移除",
            agents.contains("local.properties"),
        )
        // 不能再出现"读该文件建立全貌"这种无条件指令
        assertFalse(
            "不得再写「读该项目认知文档建立全貌」这类无条件指令",
            agents.contains("读 `~/heiyao/东方无限-项目认知.md` 建立全貌"),
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
