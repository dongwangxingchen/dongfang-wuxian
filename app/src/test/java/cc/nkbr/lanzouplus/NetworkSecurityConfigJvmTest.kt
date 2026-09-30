package cc.nkbr.lanzouplus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * [DFWX] DFW-10 网络安全配置守卫。
 *
 * 背景：宿主清单原本是全局 `usesCleartextTraffic="true"`，与 `docs/plan/decisions.md #9`
 * 「普通外部 AI/API 只允许 HTTPS」冲突。现在改为声明 networkSecurityConfig。
 *
 * 这类配置最容易"改着改着又放开全局"，所以这里用测试把两件事钉死：
 *  1. 清单**必须**指向 networkSecurityConfig，且 `usesCleartextTraffic` 不得为 true；
 *  2. 配置里的放行域名**必须是有界清单**，不得出现通配符式全局放行。
 *
 * 这些是纯文本断言（不依赖 Robolectric），跑得飞快且不受构建变体影响。
 */
class NetworkSecurityConfigJvmTest {

    private val projectRoot: File = run {
        // 单测工作目录 = 模块目录（app/），兜底向上找到含 src/main 的那层
        var dir = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (!File(dir, "src/main/AndroidManifest.xml").isFile && dir.parentFile != null) {
            dir = dir.parentFile
        }
        dir
    }

    private fun manifest(): String =
        File(projectRoot, "src/main/AndroidManifest.xml").readText(Charsets.UTF_8)

    private fun config(): String =
        File(projectRoot, "src/main/res/xml/network_security_config.xml").readText(Charsets.UTF_8)

    @Test
    fun manifest_doesNotGloballyAllowCleartext() {
        val manifest = manifest()
        assertFalse(
            "清单不得再出现 usesCleartextTraffic=\"true\"（DFW-10 的全局明文口子）",
            manifest.contains("usesCleartextTraffic=\"true\""),
        )
        assertTrue(
            "清单必须声明 networkSecurityConfig，否则 API 24+ 上就没有任何明文策略",
            manifest.contains("android:networkSecurityConfig=\"@xml/network_security_config\""),
        )
        assertTrue(
            "合并 vendor 清单时需要用 tools:replace 覆盖 networkSecurityConfig",
            manifest.contains("android:networkSecurityConfig"),
        )
    }

    @Test
    fun config_defaultsToDeny() {
        val config = config()
        assertTrue(
            "base-config 必须显式拒绝明文（默认拒绝原则）",
            Regex("<base-config\\s+cleartextTrafficPermitted=\"false\"").containsMatchIn(config),
        )
    }

    /** 放行项必须逐个列域名，不允许出现"允许所有域名"的写法。 */
    @Test
    fun config_onlyPermitsBoundedDomains() {
        val config = config()
        assertFalse(
            "不得对任意域名放行明文（出现 cleartextTrafficPermitted=\"true\" 的 base-config 即为全局口子）",
            Regex("<base-config[^>]*cleartextTrafficPermitted=\"true\"").containsMatchIn(config),
        )
        // 放行的 host 必须落在已知白名单里。
        // 新增条目必须**先找到代码里的调用点**（本测试的用意就是逼这一条）：
        //  - lanzou* : LanzouCore.validatedRouteOrigin() / parseUserSourceInput() 接受 http 蓝奏链接
        //  - localhost / 127.0.0.1 / ::1 / local : 内置 Web 服务 + MCP OAuth 回调（回环不出设备）
        //  - 39.106.33.135 : RemoteConfigClient（DFW-19 自建后台：公告/版本/总控/更新记录），
        //    用户决策 #33 走 HTTP，安全性由下载后 sha256+包名+签名三重校验保证。
        //    调用点：RemoteConfigClient.BASE = "http://39.106.33.135/pb"
        val allowed = setOf(
            "lanzout.com", "lanzoux.com", "lanzouw.com", "lanzoup.com",
            "lanzouo.com", "lanzouz.com", "lanzou.com", "lanzov.com",
            "localhost", "127.0.0.1", "::1", "local",
            "39.106.33.135",
        )
        val declared = Regex("<domain[^>]*>([^<]+)</domain>")
            .findAll(config).map { it.groupValues[1].trim() }.toList()
        assertTrue("配置里应声明若干放行域名", declared.isNotEmpty())
        for (host in declared) {
            assertTrue("发现未在白名单内的明文放行域名：$host（放行前请先在代码里找到它的调用点）", host in allowed)
        }
        assertFalse(
            "不得出现通配符域名放行",
            declared.any { it == "*" || it.startsWith("*.") },
        )
    }

    /** 蓝奏域名池必须成组放行（用户可粘贴 http 分享链接，LanzouCore 显式接受 http）。 */
    @Test
    fun config_permitsLanzouDomainPool() {
        val config = config()
        for (host in listOf("lanzout.com", "lanzoux.com", "lanzouw.com", "lanzoup.com")) {
            assertTrue("蓝奏域名 $host 必须放行，否则 http 分享链接会解析失败", config.contains(">$host<"))
        }
    }

    /** 回环明文必须放行：内置 Web 服务与 MCP OAuth 回调都走 loopback。 */
    @Test
    fun config_permitsLoopbackForLocalServers() {
        val config = config()
        for (host in listOf("localhost", "127.0.0.1")) {
            assertTrue("回环 $host 必须放行（内置 Web 服务 / MCP OAuth 回调）", config.contains(">$host<"))
        }
    }

    /** 非蓝奏、非回环的第三方域名不得被放行明文。 */
    @Test
    fun config_doesNotPermitArbitraryThirdPartyHosts() {
        val config = config()
        for (host in listOf("googleapis.com", "openai.com", "anthropic.com", "github.com", "baidu.com")) {
            assertFalse("$host 不应被放行明文（整链应走 HTTPS）", config.contains(">$host<"))
        }
        assertEquals("配置文件必须存在且非空", true, config.isNotBlank())
    }

    /**
     * DFW-16 第 2 条：`requestLegacyExternalStorage` 在 targetSdk 37 下**完全无效**
     * （该属性仅 target≤29 生效），是死配置。
     *
     * 卡片的验收要求是"merged manifest 实际值有 aapt 证据"——aapt 证据在提交信息里；
     * 这里守的是"别又被谁加回来"：属性既无作用，留着只会误导后来者以为本应用在用旧存储模型。
     */
    @Test
    fun manifest_hasNoDeadRequestLegacyExternalStorage() {
        val manifest = manifest()
        assertFalse(
            "requestLegacyExternalStorage 在 targetSdk 37 下无任何作用（死配置），不得再出现",
            manifest.contains("requestLegacyExternalStorage"),
        )
    }

    /** allowBackup 必须为 false 且靠 tools:replace 压过 vendor 的 true（防备份把本地数据带走）。 */
    @Test
    fun manifest_disablesBackupAndReplacesVendorValue() {
        val manifest = manifest()
        assertTrue(
            "allowBackup 必须显式为 false（本地含分享密码等敏感数据，不该被云备份带走）",
            manifest.contains("android:allowBackup=\"false\""),
        )
        assertTrue(
            "vendor 清单声明 allowBackup=true，宿主必须用 tools:replace 覆盖，否则清单合并失败或用错值",
            manifest.contains("android:allowBackup"),
        )
        assertTrue(
            "tools:replace 必须包含 allowBackup",
            manifest.contains("android:allowBackup,"),
        )
    }
}
