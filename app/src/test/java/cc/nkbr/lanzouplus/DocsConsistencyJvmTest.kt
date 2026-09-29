package cc.nkbr.lanzouplus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * [DFWX] DFW-18 对外文档一致性守卫。
 *
 * 背景：README 曾声称"手机与手表都好用""arm64 手表上是一个真正可用的独立 APP"，
 * 但手表测试早在 2026-09-24 就**永久停止**（`docs/plan/decisions.md` #13）；
 * `SECURITY.md` 的版本号停在 `1.8.x`，并且仍在说"内置默认 AI 渠道的 Key 打包在 APK 内"
 * （AI-004 之后已不再内置任何 Key）与"`usesCleartextTraffic=true`"（DFW-10 已收敛为默认拒绝明文）。
 *
 * 文档是给用户和后续 AI 看的，**写错比不写更糟**：会让人以为手表被支持、以为包里有内置 Key。
 * 所以把这几条钉成测试——文档再漂移就会红。
 */
class DocsConsistencyJvmTest {

    private val projectRoot: File = run {
        var dir = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (!File(dir, "README.md").isFile && dir.parentFile != null) dir = dir.parentFile
        dir
    }

    private fun read(name: String) = File(projectRoot, name).readText(Charsets.UTF_8)

    @Test
    fun readme_doesNotClaimWatchSupport() {
        val readme = read("README.md")
        assertFalse(
            "README 不得再声称手表可用（decisions #13：手表测试永久停止）",
            readme.contains("手机与手表都好用"),
        )
        assertFalse(
            "README 不得再声称在手表上是可用的独立 APP",
            readme.contains("在 arm64 手表上是一个真正可用的独立 APP"),
        )
        assertFalse("README 不得把手表写进系统要求", readme.contains("手机 / 手表均可"))
        // 但必须如实说明历史与现状，不能装作没做过
        assertTrue(
            "README 应如实说明手表测试已永久停止（而不是悄悄删掉）",
            readme.contains("手表测试已永久停止") || readme.contains("手表测试永久停止"),
        )
    }

    @Test
    fun readme_doesNotClaimBuiltinApiKey() {
        val readme = read("README.md")
        assertFalse(
            "README 不得声称内置了公共体验渠道（AI-004 之后不再内置任何 Key）",
            readme.contains("预置一个**有限额的公共体验渠道**"),
        )
        assertFalse(
            "README 不得在支持作者一节提到预置渠道",
            readme.contains("预置的公共体验渠道"),
        )
        assertTrue("README 必须明确说明不内置 Key", readme.contains("不内置任何 API Key"))
    }

    /** 工具数量必须与代码里真实的工具表一致（README 三处都写了这个数字）。 */
    @Test
    fun readme_toolCountMatchesCode() {
        val readme = read("README.md")
        val toolbox = File(
            projectRoot,
            "app/src/main/java/cc/nkbr/lanzouplus/Toolbox.java",
        ).readText(Charsets.UTF_8)
        val declared = Regex("\\{\"[a-z0-9_]+\",").findAll(toolbox).count()
        assertTrue("TOOLS 表应能数出工具条目", declared > 0)
        assertTrue(
            "README 写的是 37 个工具，代码里实际 $declared 个；不一致必须同步",
            readme.contains("$declared 个离线小工具") || readme.contains("$declared 个小工具"),
        )
    }

    @Test
    fun security_hasCurrentVersionAndCorrectAssetName() {
        val security = read("SECURITY.md")
        assertFalse("SECURITY.md 不得停留在 1.8.x", security.contains("`1.8.x`"))
        assertTrue(
            "SECURITY.md 必须写明真实资产命名 dongfang-wuxian-vX.Y.Z.apk",
            security.contains("dongfang-wuxian-vX.Y.Z.apk"),
        )
        assertFalse(
            "SECURITY.md 不得再写旧的带描述后缀的资产名",
            security.contains("东方无限-vX.Y.Z-release-"),
        )
        assertFalse("SECURITY.md 不得再提 gofile 作为分发渠道", security.contains("gofile"))
    }

    @Test
    fun security_doesNotClaimBuiltinKeyOrGlobalCleartext() {
        val security = read("SECURITY.md")
        assertFalse(
            "SECURITY.md 不得再声称内置 AI Key 打包在 APK 内（AI-004 已移除）",
            security.contains("内置默认 AI 渠道的 Key 以资源形式打包在 APK 内"),
        )
        assertFalse(
            "SECURITY.md 不得再声称 usesCleartextTraffic=true（DFW-10 已改为默认拒绝明文）",
            security.contains("`usesCleartextTraffic=true`"),
        )
        assertTrue(
            "SECURITY.md 必须说明明文流量已收敛且是有界放行",
            security.contains("默认拒绝明文 HTTP"),
        )
        assertTrue("SECURITY.md 必须披露不再收集遥测", security.contains("不再上报"))
        assertTrue("SECURITY.md 必须披露崩溃日志不上传且不含凭据", security.contains("不会自动上传"))
    }

    /** AGPL 与上游署名是许可义务，任何文档整理都不得删。 */
    @Test
    fun licenseAndUpstreamAttributionPreserved() {
        val readme = read("README.md")
        val security = read("SECURITY.md")
        assertTrue("README 必须保留 AGPL-3.0 署名", readme.contains("AGPL-3.0"))
        assertTrue("SECURITY.md 必须保留 AGPL-3.0 义务说明", security.contains("AGPL-3.0"))
        assertTrue("README 必须保留 RikkaHub 上游署名（AGPL 义务）", readme.contains("RikkaHub"))
        assertTrue("README 必须保留上游仓库链接以便对照", readme.contains("rikkahub/rikkahub"))
    }

    /** DFW-20 收尾：README 的"无追踪/不申请多余权限"必须与实现一致（不得退回绝对说法）。 */
    @Test
    fun readmeClaims_areBackedByImplementation() {
        val readme = read("README.md")
        // "无追踪"现在属实（DFW-9 已移除 Firebase），但必须同时说明权限用途，
        // 不能只喊口号——用户需要知道存储/安装权限拿去干什么。
        assertTrue("README 应声明无广告无追踪", readme.contains("无广告、无追踪"))
        assertTrue("README 必须说明权限用途（DFW-20：承诺要有依据）",
            readme.contains("拒绝不影响其它功能"))
        assertTrue("README 应说明不内置 Key", readme.contains("不内置任何 AI Key"))
        // 已移除的能力不得再被描述为可用
        assertFalse("README 不得再写内置体验渠道", readme.contains("预置一个**有限额的公共体验渠道**"))
    }
}
