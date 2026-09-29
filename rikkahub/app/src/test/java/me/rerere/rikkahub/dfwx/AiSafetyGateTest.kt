package me.rerere.rikkahub.dfwx

import me.rerere.ai.provider.AiPolicyDns
import me.rerere.ai.provider.AiUrlPolicy
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.net.InetAddress

/**
 * [DFWX] DFW-21（AI-005）AI 安全与生命周期**接线门禁**。
 *
 * ## 这张卡与 DFW-8 的分工（卡片备注里写明了）
 * 卡片备注："SEC-004（本面板 DFW-8）是本卡的下游：本卡建门禁，DFW-8 治具体脱敏实现"。
 * 所以这里**不重复测脱敏规则**（那在 `DfwxLogRedactorTest` 与 `RequestLoggingRedactionTest` 里），
 * 而是守住一件事：**这些防线必须真的接在 AI 链路上**。
 *
 * 为什么"接线"必须单独测：`AiUrlPolicy` 有 18 个用例全绿，
 * 但如果 Koin 里忘了把它 `addInterceptor` 上去，那 18 个用例一个都不会生效——
 * 测试全绿、线上裸奔。这类缺陷写"策略逻辑测试"永远抓不到。
 *
 * ## 测法
 * 用源码级断言（读 `DataSourceModule.kt`）而不是反射启动 Koin——
 * 因为 Robolectric 里完整启动 Koin 图会带起数据库/GMS 等一堆无关依赖，
 * 而这里要验的是"构建语句里有没有这几行"，源码就是最直接的证据。
 * 同时用真实类做行为验证，两类证据互补。
 */
class AiSafetyGateTest {

    /**
     * 仓库根（`/Users/lishaowei/heiyao/src`）。
     *
     * 注意：vendor 模块的单测工作目录是 `rikkahub/`（它自己也有 settings.gradle.kts），
     * 所以**不能**用"第一个含 settings.gradle.kts 的目录"来判定根——
     * 那样会停在 rikkahub/ 下，路径全部拼错。这里用宿主侧的独有文件当锚点。
     */
    private val repoRoot: File = run {
        var dir = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir.parentFile != null && !File(dir, "app/src/main/java/cc/nkbr/lanzouplus/MainActivity.java").isFile) {
            dir = dir.parentFile
        }
        dir
    }

    private fun dataSourceModule(): String =
        File(repoRoot, "rikkahub/app/src/main/java/me/rerere/rikkahub/di/DataSourceModule.kt")
            .readText(Charsets.UTF_8)

    // ---------- 接线：四道防线必须在 AI client 上 ----------

    @Test
    fun aiClient_isBuiltWithAllFourDefenses() {
        val src = dataSourceModule()
        // 取出 ProviderManager 的那段构造（AI 链专用 client）
        val start = src.indexOf("ProviderManager(")
        assertTrue("DataSourceModule 必须构造 ProviderManager", start >= 0)
        val block = src.substring(start, minOf(src.length, start + 1200))

        assertTrue(
            "AI client 必须挂 AiUrlPolicyInterceptor 作为应用拦截器（请求入口判定）",
            block.contains("addInterceptor(AiUrlPolicyInterceptor())"),
        )
        assertTrue(
            "AI client 必须挂 network 版 AiUrlPolicyInterceptor(resolve = false)（覆盖重定向每一跳）",
            block.contains("addNetworkInterceptor(AiUrlPolicyInterceptor(resolve = false))"),
        )
        assertTrue(
            "AI client 必须使用 AiPolicyDns（DNS rebinding 最终防线：连接前拒绝内网解析）",
            block.contains(".dns(AiPolicyDns())"),
        )
        assertTrue(
            "AI client 必须 followSslRedirects(false)（禁 https→http 跨协议重定向，fail-closed）",
            block.contains("followSslRedirects(false)"),
        )
    }

    /** AI 专用 client 必须是**派生**出来的，不能污染共享 client（否则 WebDav/搜索也受影响）。 */
    @Test
    fun aiClient_isDerivedFromSharedClient_notMutatingIt() {
        val src = dataSourceModule()
        val start = src.indexOf("ProviderManager(")
        val block = src.substring(start, minOf(src.length, start + 1200))
        assertTrue(
            "AI client 必须从共享 client newBuilder 派生（保留连接池，且不改动共享实例）",
            block.contains("get<OkHttpClient>().newBuilder()"),
        )
    }

    // ---------- 行为：策略类本身仍然拒绝危险地址 ----------

    @Test
    fun policy_rejectsCleartextAndPrivateTargets() {
        for (url in listOf(
            "http://api.example.com/v1",
            "https://user:pass@api.example.com/v1",
            "https://127.0.0.1/v1",
            "https://localhost/v1",
            "https://192.168.1.10/v1",
            "https://10.0.0.5/v1",
        )) {
            assertNotNull(
                "危险地址必须被拒绝：$url",
                AiUrlPolicy.staticRejectionReason(url.toHttpUrl()),
            )
        }
        // 正常 https 公网地址放行
        assertEquals(null, AiUrlPolicy.staticRejectionReason("https://api.openai.com/v1".toHttpUrl()))
    }

    @Test
    fun policyDns_rejectsPrivateResolution() {
        val privateDns = AiPolicyDns { listOf(InetAddress.getByName("127.0.0.1")) }
        var rejected = false
        try {
            privateDns.lookup("evil.example.com")
        } catch (e: Exception) {
            rejected = true
        }
        assertTrue("解析到回环地址必须拒绝（DNS rebinding）", rejected)

        val publicDns = AiPolicyDns { listOf(InetAddress.getByName("1.1.1.1")) }
        assertEquals(1, publicDns.lookup("ok.example.com").size)
    }

    // ---------- 日志边界：AI 链路上的日志出口必须已脱敏 ----------

    @Test
    fun aiLoggingPath_isRedactedAtTheStorageChokePoint() {
        // DFW-8 把脱敏做进 Logging.logRequest()（唯一存储入口）。
        // 这里守的是"那个入口还在用脱敏"——一旦有人回退，AI 日志立刻裸奔。
        val logging = File(
            repoRoot,
            "rikkahub/common/src/main/java/me/rerere/common/android/Logging.kt",
        ).readText(Charsets.UTF_8)
        assertTrue(
            "Logging.logRequest 必须经过 redact（DFW-8 的结构性防线）",
            logging.contains("addLog(redact(entry))"),
        )
        assertTrue("必须真的定义了 redact()", logging.contains("private fun redact("))
    }

    /** 四个 Provider 的 SSE 日志不得再直接打印响应体内容。 */
    @Test
    fun providerSseLogs_doNotPrintRawPayload() {
        val providers = listOf(
            "rikkahub/ai/src/main/java/me/rerere/ai/provider/providers/openai/ChatCompletionsAPI.kt",
            "rikkahub/ai/src/main/java/me/rerere/ai/provider/providers/openai/ResponseAPI.kt",
            "rikkahub/ai/src/main/java/me/rerere/ai/provider/providers/claude/ClaudeProvider.kt",
            "rikkahub/ai/src/main/java/me/rerere/ai/provider/providers/google/GoogleProvider.kt",
        )
        for (rel in providers) {
            val text = File(repoRoot, rel).readText(Charsets.UTF_8)
            // 只检查**真实代码行**：补丁注释里会逐字引用"原为 Log.d(...)"以便同步上游时重放，
            // 按整文件 contains 判会把这些说明性注释误报成违规（第一版就是这样假红的）。
            val codeLines = text.lines().filter { !it.trimStart().startsWith("//") && !it.trimStart().startsWith("*") }
            for (bad in listOf("onEvent: \$data", "onEvent: \$id/\$type \$data", "type=\$type, data=\$data")) {
                val offender = codeLines.firstOrNull { it.contains(bad) }
                assertTrue(
                    "$rel 不得在代码里直接打印 SSE 响应体（$bad）：$offender",
                    offender == null,
                )
            }
            assertTrue(
                "$rel 的 onEvent 日志必须走 describeSseEvent（只留类型与字节数）",
                text.contains("describeSseEvent"),
            )
        }
    }

    /** okhttp 官方日志不得回到 HEADERS（release 常开且会打全部头）。 */
    @Test
    fun okhttpLoggingStaysAtBasic_notHeaders() {
        val src = dataSourceModule()
        assertTrue(
            "okhttp 日志级别必须保持 BASIC（HEADERS 会把 Authorization/Cookie 打进 Logcat，且 release 常开）",
            src.contains("HttpLoggingInterceptor.Level.BASIC"),
        )
        assertTrue(
            "不得再出现 Level.HEADERS",
            !src.contains("HttpLoggingInterceptor.Level.HEADERS"),
        )
    }

    /** 上游自带的更新检查必须保持 P12 短路（不能被误恢复成"会自己连上游"）。 */
    @Test
    fun upstreamUpdateChecker_staysShortCircuited() {
        val checker = File(
            repoRoot,
            "rikkahub/app/src/main/java/me/rerere/rikkahub/utils/UpdateChecker.kt",
        )
        assertTrue("上游 UpdateChecker 必须存在", checker.isFile)
        val text = checker.readText(Charsets.UTF_8)
        // 短路 = emit(Loading) 后面紧跟 return@flow（中间可能隔注释行）。
        // 不能要求两串连续出现——补丁注释正好插在它们之间（第一版因此假红）。
        assertTrue("必须保留 P12 短路：找不到 emit(UiState.Loading)", text.contains("emit(UiState.Loading)"))
        assertTrue("必须保留 P12 短路：找不到 return@flow", text.contains("return@flow"))
        val emitIdx = text.indexOf("emit(UiState.Loading)")
        val returnIdx = text.indexOf("return@flow", emitIdx)
        assertTrue("P12 短路的 return@flow 必须在 emit(Loading) 之后", returnIdx > emitIdx)
        // 且短路必须发生在发起网络请求之前：return@flow 必须在第一个 client.newCall 之前
        val callIdx = text.indexOf("client.newCall")
        assertTrue(
            "短路必须早于任何网络请求（否则等于没短路）：return@flow@$returnIdx, newCall@$callIdx",
            callIdx < 0 || returnIdx < callIdx,
        )
    }

    /** 宿主与 vendor 都不许把 API Key 写进构建资源（AI-004）。 */
    @Test
    fun noApiKeyInjectedIntoBuildResources() {
        for (rel in listOf("app/build.gradle.kts", "rikkahub/app/build.gradle.kts")) {
            val text = File(repoRoot, rel).readText(Charsets.UTF_8)
            // 注意区分：`google_api_key` 是 Firebase 的**占位**资源（P5 遗留，值全 0 的假 key），
            // 不是 AI 渠道 Key。要拦的是"把用户的 AI Key 注入构建资源"（AI-004 已移除）。
            val offenders = text.lines().filter { line ->
                val l = line.substringBefore("//")
                l.contains("resValue") &&
                    (l.contains("google_api_key", ignoreCase = true) == false) &&
                    Regex("(?i)(ai[_-]?key|ai_default|default_key|DEFAULT_AI)").containsMatchIn(l)
            }
            assertTrue("$rel 不得再注入 AI Key 资源（AI-004）：\n${offenders.joinToString("\n")}", offenders.isEmpty())
        }
    }
}
