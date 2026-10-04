package cc.nkbr.lanzouplus

import me.rerere.rikkahub.dfwx.DfwxBuiltinChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * [DFW-77] 后台控制台 → App 的「内置 AI 渠道」远程控制链路。
 *
 * 用户 2026-10-01：
 * > "我希望你在我的控制台里面再加一个板块。我在里面可以改什么呢？可以改 URL、Key、上下文长度、
 * >  最大输出、模型名字等等等等的，反正就是和官方那个界面一样的设置。
 * >  但是我是远程控制的，而不是在软件内控制的。"
 *
 * 这条链路的每一段都必须真的接上，否则"在控制台改了但用户那边没变"是查不出来的：
 * `control` 记录 → `RemoteConfigClient.parseControl` → `BuiltinAiChannel.applyRemote`
 * → `DfwxBuiltinChannel.Config` → 播种进 RikkaHub 设置。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class BuiltinChannelRemoteJvmTest {

    companion object {
        @BeforeClass @JvmStatic fun silenceAutoImport() { MainActivity.LIBRARY_AUTO_IMPORT = false }

        /** 后台一行 `control` 记录（只列 ai_* 那几列）。 */
        private fun controlJson(
            baseUrl: String = "",
            token: String = "",
            model: String = "",
            displayName: String = "",
            chatPath: String = "",
            maxTokens: Int = 0,
            disabled: Boolean = false,
        ) = """
            {"maintenanceOn":false,"blocked":false,"maintenanceTitle":"","maintenanceBody":"",
             "maintenanceUntil":"","ai_base_url":"$baseUrl","ai_token":"$token","ai_model":"$model",
             "ai_display_name":"$displayName","ai_chat_path":"$chatPath",
             "ai_max_tokens":$maxTokens,"ai_disabled":$disabled}
        """.trimIndent()

        private val parseControl = RemoteConfigClient::class.java
            .getDeclaredMethod("parseControl", org.json.JSONObject::class.java)
            .apply { isAccessible = true }

        private fun parse(json: String): RemoteConfigClient.Control =
            parseControl.invoke(null, org.json.JSONObject(json)) as RemoteConfigClient.Control
    }

    private fun boot() {
        // 先注入 APK 内置值（applyRemote 要求 bakedUrl 非空），再喂后台配置。
        BuiltinAiChannel.install(RuntimeEnvironment.getApplication())
    }

    @Test fun controlParsesEveryRemoteField() {
        val c = parse(controlJson("https://s/ai/v1", "tok", "glm-5.3-flashx", "东方无限 · 极速", "/v1/chat/completions", 4096, true))
        assertEquals("https://s/ai/v1", c.aiBaseUrl)
        assertEquals("tok", c.aiToken)
        assertEquals("glm-5.3-flashx", c.aiModel)
        assertEquals("后台新加的显示名字段必须被解析", "东方无限 · 极速", c.aiDisplayName)
        assertEquals("后台新加的请求路径字段必须被解析", "/v1/chat/completions", c.aiChatPath)
        assertEquals(4096, c.aiMaxTokens)
        assertTrue(c.aiDisabled)
    }

    @Test fun oldBackendWithoutTheNewColumns_stillWorks() {
        // 后台还没升级时，记录里没有 ai_display_name / ai_chat_path 两列 —— 必须 fail-open，
        // 不能因为缺字段就把内置渠道弄坏（老后台 + 新 APK 是最容易出事的组合）。
        val c = parse("""{"maintenanceOn":false,"ai_base_url":"https://s/ai/v1","ai_token":"tok","ai_model":"m","ai_max_tokens":4096}""")
        assertEquals("", c.aiDisplayName)
        assertEquals("", c.aiChatPath)
        assertEquals("m", c.aiModel)
    }

    @Test fun remoteOverride_reachesTheVendorChannel() {
        boot()
        BuiltinAiChannel.applyRemote(parse(controlJson("https://s/ai/v1", "tok-2", "kimi-k3", "东方无限 · 长文", "/v1/chat/completions", 4096)))
        val cfg = DfwxBuiltinChannel.current()
        assertEquals("服务器地址必须被后台覆盖", "https://s/ai/v1", cfg.baseUrl)
        assertEquals("令牌必须被后台覆盖（换令牌不用发版）", "tok-2", cfg.token)
        assertEquals("模型名必须被后台覆盖（控制台『切换模型』走的就是这条）", "kimi-k3", cfg.modelId)
        assertEquals("显示名必须被后台覆盖", "东方无限 · 长文", cfg.displayName)
        assertEquals("请求路径必须被后台覆盖", "/v1/chat/completions", cfg.chatPath)
        assertEquals(4096, cfg.maxTokens)
        assertTrue(cfg.enabled)
    }

    @Test fun disablingFromTheConsole_actuallyDisablesTheChannel() {
        boot()
        BuiltinAiChannel.applyRemote(parse(controlJson("https://s/ai/v1", "tok", "m", "", "", 4096, true)))
        assertFalse("控制台停用后，这条渠道必须真的不可用", DfwxBuiltinChannel.current().enabled)
    }

    @Test fun blankRemoteFields_keepTheBakedInValues() {
        // 后台留空 = 用安装包自带值。这是 fail-open 的核心：误填空不会把内置渠道弄坏。
        boot()
        val baked = DfwxBuiltinChannel.current()
        assertNotNull(baked)
        assertTrue("APK 内置服务器地址必须非空（它是常量）", baked.baseUrl.isNotEmpty())

        // ⚠️ 令牌**可能为空**，这是设计，不是故障。
        // 2026-10-04 安全修复后，令牌不再硬编码在 build.gradle.kts 里（那会被公开仓库泄露），
        // 改从 local.properties / 环境变量读 —— 公开克隆里没有这个值。
        // 没配令牌 → 内置渠道不可用（用户仍可自配渠道），但**绝不能因此崩溃**。
        BuiltinAiChannel.applyRemote(parse(controlJson("", "", "", "", "", 0)))
        val after = DfwxBuiltinChannel.current()
        assertEquals("留空时服务器地址保持 APK 内置值", baked.baseUrl, after.baseUrl)
        assertEquals("留空时令牌保持 APK 内置值", baked.token, after.token)
        assertEquals("留空时模型名保持 APK 内置值", baked.modelId, after.modelId)
        assertEquals("留空时最大输出保持 APK 内置值", baked.maxTokens, after.maxTokens)
    }

    /**
     * [安全守卫 2026-10-04] **公开仓库里不得出现内置渠道的应用令牌。**
     *
     * ## 为什么加这条
     * 令牌原本硬编码在 `app/build.gradle.kts`，而本仓库是**公开**的 ——
     * 等于任何人打开 GitHub 就能抄走，连 APK 都不用下。实测已被外部用脚本调用。
     *
     * ## 为什么必须是机械判据
     * "记得别把密钥提交"这种靠自觉的规矩，拦截率是 0。
     * 这条测试直接扫源码树，只要令牌再被写回去就立刻变红。
     */
    @Test fun repoMustNotContainTheBuiltInToken() {
        val root = run {
            var dir = java.io.File(System.getProperty("user.dir") ?: ".").absoluteFile
            while (!java.io.File(dir, "AGENTS.md").isFile && dir.parentFile != null) dir = dir.parentFile
            dir
        }
        val offenders = mutableListOf<String>()
        // ⚠️ 只扫 **git 跟踪的文件** —— 那才是真正会被公开的集合。
        //    不能扫整个磁盘：local.properties 是本地文件（已在 .gitignore 里），
        //    它**本来就应该**存令牌，扫它会误报。
        val tracked = runCatching {
            val p = ProcessBuilder("git", "ls-files")
                .directory(root).redirectErrorStream(true).start()
            val out = p.inputStream.bufferedReader().readText()
            p.waitFor()
            out.lineSequence().filter { it.isNotBlank() }.toList()
        }.getOrDefault(emptyList())

        tracked
            .filter { it.endsWith(".kts") || it.endsWith(".java") || it.endsWith(".kt") ||
                it.endsWith(".gradle") || it.endsWith(".xml") || it.endsWith(".json") ||
                it.endsWith(".yml") || it.endsWith(".yaml") || it.endsWith(".md") }
            .forEach { rel ->
                val f = java.io.File(root, rel)
                if (!f.isFile) return@forEach
                val text = runCatching { f.readText() }.getOrNull() ?: return@forEach
                // dfwx + 32 位以上十六进制 = 我们的应用令牌形态
                if (Regex("dfwx[0-9a-f]{32,}").containsMatchIn(text)) offenders += rel
            }

        assertTrue(
            "内置渠道令牌是公开仓库不该有的凭据，必须从 local.properties / 环境变量读。\n" +
                "命中文件：$offenders",
            offenders.isEmpty(),
        )
    }

    /**
     * [DFW-147] 构建脚本里，`dfwx_ai_*` 这几个**敏感**资源只能用变量赋值，不许写字面量。
     *
     * ## 为什么需要这条
     *
     * 令牌事故的根因是 `resValue("string", "dfwx_ai_token", "dfwx2779…")` ——
     * 密钥以字面量形式躺在**公开仓库**里。把那一处改成读 local.properties 只是修了症状：
     * 下次有人加一个新密钥（比如这次的签名密钥），照样可能顺手写成字面量。
     *
     * 上面那条 `repoMustNotContainTheBuiltInToken` 只能抓 `dfwx` + 32 位十六进制这一种形态，
     * **抓不到签名密钥（64 位纯十六进制）** —— 而 64 位十六进制在仓库里到处都是
     * （README 和发布站的 APK sha256 就是），不能拿它当判据，会满屏误报。
     *
     * 所以换个角度：**不看值长什么样，看它是不是字面量。**
     * 只要第三个参数是字符串字面量就变红，跟值的内容无关 —— 机械、无歧义、零误报。
     */
    @Test fun buildScript_neverInlinesAiSecrets() {
        val root = run {
            var dir = java.io.File(System.getProperty("user.dir") ?: ".").absoluteFile
            while (!java.io.File(dir, "AGENTS.md").isFile && dir.parentFile != null) dir = dir.parentFile
            dir
        }
        val script = java.io.File(root, "app/build.gradle.kts")
        assertTrue("找不到 app/build.gradle.kts", script.isFile)
        val text = script.readText()

        // 只查**名字像密钥**的键：token / key / secret / password。
        // 不查全部 dfwx_ai_* —— 像 dfwx_ai_max_tokens="8192" 这种非机密配置，
        // 写成字面量完全正常，一起查会误报（实测踩到过一次）。
        // ⚠️ 必须按**段**精确匹配，不能用子串 —— 实测踩到：`max_tokens` 里含 `token`，
        // 用子串匹配会把非机密的 `dfwx_ai_max_tokens` 一起判红。
        val secretSegments = setOf("token", "key", "secret", "password")
        val inlined = Regex(
            "resValue\\(\\s*\"string\"\\s*,\\s*\"(dfwx_ai_[a-z_]+)\"\\s*,\\s*\""
        ).findAll(text)
            .map { it.groupValues[1] }
            .filter { name -> name.split("_").any { it.lowercase() in secretSegments } }
            .toList()

        assertTrue(
            "这些 AI 资源被写成了字面量，密钥会随公开仓库泄露：$inlined。\n" +
                "改成从 local.properties / 环境变量读（见同文件里的 dfwxSecret）。",
            inlined.isEmpty()
        )
    }

}
