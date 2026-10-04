package cc.nkbr.lanzouplus

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * [DFWX] DFW-57 品牌清理守卫：软件只叫「东方无限」，不再有「黑曜 / HeiYao」。
 *
 * ## 用户要求（2026-09-30）
 * "不要有什么黑曜的文字和拼音了，以后这个软件就叫东方无限"，并选择 **A：代码内部也全部改名**。
 *
 * ## 为什么需要守卫
 * 这类改名**很容易漏**：改了一处、漏了另一处，而**没有任何测试会红**。
 * 尤其是"持久化键名"——改错了会让老数据读不到（本卡就遇到两个：
 * `heiyao_origin_v101` 与 `heiyao_diagnostics`，前者已保留向后兼容读取）。
 *
 * 注意：测试代码里的 `<本地目录>/` 是**工作区目录路径**（仓库就放在 `$HOME/heiyao`），
 * 不是品牌，**不属于清理范围**——所以本测试只检查 `src/main/`。
 */
class BrandingCleanlinessJvmTest {

    private val root: File = run {
        var dir = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (!File(dir, "app/src/main/AndroidManifest.xml").isFile && dir.parentFile != null) dir = dir.parentFile
        dir
    }

    private val mainDir = File(root, "app/src/main")

    private fun allFiles(dir: File): List<File> =
        dir.walkTopDown().filter { it.isFile }.toList()

    /** 用户可见资源（res/）里不得有任何旧品牌痕迹。 */
    @Test
    fun resourcesHaveNoLegacyBranding() {
        val offenders = allFiles(File(mainDir, "res")).filter { f ->
            val t = f.readText(Charsets.UTF_8)
            Regex("heiyao|HeiYao|黑曜|黑耀", RegexOption.IGNORE_CASE).containsMatchIn(t)
        }.map { it.relativeTo(root).path }
        assertTrue("res/ 里不得残留旧品牌：\n${offenders.joinToString("\n")}", offenders.isEmpty())
    }

    /** 应用清单里不得残留（应用名必须是东方无限）。 */
    @Test
    fun manifestHasNoLegacyBranding() {
        val manifest = File(mainDir, "AndroidManifest.xml").readText(Charsets.UTF_8)
        assertFalse(
            "AndroidManifest 不得残留旧品牌",
            Regex("heiyao|HeiYao|黑曜|黑耀", RegexOption.IGNORE_CASE).containsMatchIn(manifest),
        )
    }

    /**
     * Java/Kotlin 源码里只允许**一处**旧品牌：`dfwx_origin_v101` 的向后兼容读取。
     *
     * 那处是**刻意保留**的：它保证任何已写过旧键的设备不会被重复迁移、
     * 进而覆盖用户自定义的蓝奏域名。除它之外不得再有。
     */
    @Test
    fun sourceOnlyKeepsTheDocumentedBackwardCompatibleKey() {
        val offenders = mutableListOf<String>()
        for (f in allFiles(mainDir).filter { it.name.endsWith(".java") || it.name.endsWith(".kt") }) {
            f.readLines().forEachIndexed { i, line ->
                if (Regex("heiyao|HeiYao|黑曜|黑耀", RegexOption.IGNORE_CASE).containsMatchIn(line)) {
                    // 抹掉两处**明确允许**的东西：
                    // ① 持久化键的向后兼容读取（理由见上）；
                    // ② 本地参考库的**目录路径** `黑曜/06-开源参考库`——那是工作区里的文件夹名，
                    //    不是软件品牌。注释里引用它（"规格见参考库…"）完全正常，
                    //    不豁免的话每写一次出处就误报一次（本卡踩过）。
                    val inner = line
                        .replace("heiyao_origin_v101", "")
                        .replace("黑曜/06-开源参考库", "")
                    if (Regex("heiyao|HeiYao|黑曜|黑耀", RegexOption.IGNORE_CASE).containsMatchIn(inner)) {
                        offenders.add("${f.relativeTo(root)}:${i + 1}")
                    }
                }
            }
        }
        assertTrue(
            "源码里除 dfwx_origin_v101 的向后兼容读取外，不得再有旧品牌：\n${offenders.joinToString("\n")}",
            offenders.isEmpty(),
        )
    }

    /**
     * 构建配置里**一处旧品牌都不许有**（DFW-58 重建证书后收紧）。
     *
     * DFW-57 时这里还放行三处"绑定仓库外载体"的字面量（口令键、`keyAlias`、keystore 文件名），
     * 因为那时改字符串不等于改实体、硬改会构建失败。
     * DFW-58 已把这三样**连实体一起换掉**（新 keystore `dongfang-wuxian.keystore`、别名 `dongfang`、
     * 口令键 `dfwx.*`），所以现在没有任何理由再放行——本测试随之收紧为零容忍。
     *
     * 保留这条注释是为了让后人知道：**当时放行是有原因的，不是漏改**；原因消失后守卫必须收紧，
     * 否则"临时的例外"会永久留在测试里，变成下一代假绿。
     */
    @Test
    fun gradleHasNoLegacyBrandingAtAll() {
        val t = File(root, "app/build.gradle.kts").readText(Charsets.UTF_8)
        // 只看**代码行**：注释里要解释这段历史，若一起扫会被自己的说明文字判红。
        val leftovers = t.lines().filterNot { line ->
            val s = line.trimStart()
            s.startsWith("//") || s.startsWith("/*") || s.startsWith("*")
        }.filter { Regex("heiyao|黑曜|黑耀", RegexOption.IGNORE_CASE).containsMatchIn(it) }
            .map { it.trim() }
        assertTrue(
            "app/build.gradle.kts 的代码里不得再有旧品牌（DFW-58 已连 keystore 一起换掉）：\n${leftovers.joinToString("\n")}",
            leftovers.isEmpty(),
        )
        // 正向：新签名要素必须真的接上，否则构建期会静默对不上。
        for (need in listOf(
            "signingSecret(\"dfwx.storePassword\")",
            "signingSecret(\"dfwx.keyPassword\")",
            "../dongfang-wuxian.keystore",
            "keyAlias = \"dongfang\"",
            "create(\"dfwx\")",
            "getByName(\"dfwx\")",
        )) {
            assertTrue("新签名要素 $need 必须存在", t.contains(need))
        }
    }

    /** 类名与样式名必须已改（防止只改注释、漏改标识符）。 */
    @Test
    fun identifiersWereRenamed() {
        assertTrue(
            "骨架屏类应为 DfwxSkeleton",
            File(mainDir, "java/cc/nkbr/lanzouplus/DfwxSkeleton.java").isFile,
        )
        assertFalse(
            "旧的 HeiYaoSkeleton.java 应已删除",
            File(mainDir, "java/cc/nkbr/lanzouplus/HeiYaoSkeleton.java").isFile,
        )
        for (name in listOf("values/styles.xml", "values-night/styles.xml")) {
            val t = File(mainDir, "res/$name").readText(Charsets.UTF_8)
            assertTrue("$name 的对话框样式应已改名为 DfwxDialog", t.contains("DfwxDialog"))
        }
    }

    /**
     * 用户能看到的文案里不得出现上游英文名 `LanzouPlus`。
     *
     * 这条是**实测发现的真实缺陷**：改名时漏了三处**用户可见**的字面量——
     * 安装未知来源权限的弹窗正文、分享可导入源的菜单项与文件名、导出源规则的文件名。
     * 它们会直接出现在用户眼前（其中两处还出现在系统文件选择器里），
     * 而上面那些扫 `heiyao|黑曜` 的断言**一个都抓不到**，因为漏的是另一个词。
     *
     * 唯一放行的是「参考与致谢」的署名条目：那里必须写上游项目真名，
     * 抹掉它反而是**不署名**，属于弄反了方向。
     */
    @Test
    fun userVisibleTextHasNoUpstreamEnglishName() {
        // 只查**字符串字面量**，不查标识符：`checkLanzouPlusUpdate` 这类方法名含该词但不是用户可见文案。
        // 也不能只匹配精确的 `"LanzouPlus"`——第一版就是这么写的，结果探针用
        // `"LanzouPlus 选择分享方式"` 一试就漏了（守卫过窄 = 假绿）。
        val literal = Regex("\"([^\"\\\\]|\\\\.)*\"")
        val offenders = mutableListOf<String>()
        for (f in allFiles(mainDir).filter { it.name.endsWith(".java") || it.name.endsWith(".kt") }) {
            f.readLines().forEachIndexed { i, line ->
                // 整行注释里的提及不算用户可见。
                val s = line.trimStart()
                if (s.startsWith("//") || s.startsWith("*") || s.startsWith("/*")) return@forEachIndexed
                // 致谢条目（ACK_ITEMS）是刻意署名，必须保留上游真名。
                if (line.contains("ACK_ITEMS")) return@forEachIndexed
                val hit = literal.findAll(line).any { it.value.contains("LanzouPlus") }
                if (hit) offenders.add("${f.relativeTo(root)}:${i + 1}")
            }
        }
        assertTrue(
            "用户可见文案里不得出现上游英文名「LanzouPlus」（致谢署名除外）：\n${offenders.joinToString("\n")}",
            offenders.isEmpty(),
        )
        // 反方向：致谢条目必须**保留**上游真名，否则变成不署名。
        val ack = File(mainDir, "java/cc/nkbr/lanzouplus/MainActivity.java").readText(Charsets.UTF_8)
        assertTrue("「参考与致谢」必须保留上游项目真名 LanzouPlus", ack.contains("{\"LanzouPlus\""))
    }

    /** 应用名必须是「东方无限」（各语言都要覆盖，防 vendor 的 RikkaHub 命中）。 */
    @Test
    fun appNameIsDongfangWuxianEverywhere() {
        val stringsFiles = allFiles(File(mainDir, "res")).filter { it.name == "strings.xml" }
        assertTrue("应存在多个语言的 strings.xml", stringsFiles.size >= 3)
        for (f in stringsFiles) {
            val t = f.readText(Charsets.UTF_8)
            if (t.contains("name=\"app_name\"")) {
                assertTrue(
                    "${f.relativeTo(root)} 的 app_name 必须是「东方无限」",
                    t.contains("<string name=\"app_name\">东方无限</string>"),
                )
            }
        }
    }
}
