package cc.nkbr.lanzouplus

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * [DFW-90] 更新包校验：**去掉"版本名必须一字不差"，但安全底线一条不少**。
 *
 * ## 用户原话（2026-10-01）
 * > 「你刚刚跟我说的，必须得出个 1.0.6 版本的包才能更新，但是我不希望这样。
 * > 我希望的是，只要我在后台改了当前版本，版本号比软件的版本号大，那它就会推送我的下载链接。」
 *
 * 追问后用户说得很直白：「我主要是想减轻你的维护负担，**别忘记改某些地方的版本号**」。
 *
 * ## 装包前的四道校验，各自的作用
 *
 * | 校验 | 作用 | 处理 |
 * |---|---|---|
 * | 包名一致 | 确认这确实是《东方无限》 | **留** |
 * | 签名一致 | 确认是作者自己打的包，不是别人拿名字塞病毒 | **必须留** |
 * | 版本名一字不差 | 后台写的必须和包里写的一模一样 | **删**（就是它最烦） |
 * | 真实版本序号更大 | 确认包真的比用户手上的新 | **留**（报错改成人话） |
 *
 * ## 为什么"真实版本序号"这条不能一起删
 * 后台填的版本号只决定「要不要提醒更新」；用户真正装上的还是那个包。
 * 包本身不比当前版本新 → 装完版本没变 → 后台还说有新版本 → **无限循环提示**。
 * 这是物理限制，不是保守。
 */
class UpdateVerificationPolicyJvmTest {

    private val root: File = run {
        var dir = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (!File(dir, "AGENTS.md").isFile && dir.parentFile != null) dir = dir.parentFile
        dir
    }

    private val main by lazy {
        File(root, "app/src/main/java/cc/nkbr/lanzouplus/MainActivity.java").readText(Charsets.UTF_8)
    }

    /** 按花括号配对取方法体（不靠空行猜边界 —— DFW-98 就是这么删错东西的）。 */
    private fun bodyOf(signature: String): String {
        val start = main.indexOf(signature)
        assertTrue("找不到方法：$signature", start >= 0)
        val open = main.indexOf('{', start)
        var depth = 0
        for (i in open until main.length) {
            when (main[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return main.substring(open, i + 1)
                }
            }
        }
        error("$signature 花括号不配对")
    }

    private val verify by lazy { bodyOf("Uri verifyArchiveUpdate(Uri uri,String expectedVersion)") }

    @Test
    fun theVersionNameMustMatchRuleIsGone() {
        assertFalse(
            "「后台写的版本名必须和包里的一字不差」这条必须删掉（DFW-90）",
            verify.contains("version.equals(archive.versionName)"),
        )
        assertFalse("那条校验的报错文案也该消失", verify.contains("更新包版本不一致"))
    }

    @Test
    fun theThreeRealSafetyChecksAllRemain() {
        assertTrue("包名必须校验", verify.contains("!getPackageName().equals(archive.packageName)"))
        assertTrue("签名必须校验（这条是防假冒安装包的唯一防线）", verify.contains("signatureDigests(current).equals(signatureDigests(archive))"))
        assertTrue(
            "真实版本序号必须校验（否则用户会陷入「下载→安装→还是旧版→又提示」的循环）",
            verify.contains("archiveCode<=currentCode"),
        )
        assertTrue("校验通过后仍然要封存再安装", verify.contains("temporary.renameTo(verified)"))
    }

    @Test
    fun theRemainingErrorIsInPlainLanguage() {
        assertTrue(
            "版本没提升的报错要让人看得懂，并说清「包本身不够新」这件事",
            verify.contains("这个安装包并不比当前版本新"),
        )
        assertFalse(
            "原来的「更新包版本代码未提升」没人看得懂，必须消失",
            verify.contains("更新包版本代码未提升"),
        )
    }

    /**
     * 版本名的格式校验必须放宽成"非空"。
     *
     * 后台控制台对版本名**没有格式校验**，是自由文本。用户填 `1.0.24-beta` 这类写法时，
     * 旧的严格 `x.y.z` 正则会直接拒掉整个更新，而报错只有一句"版本信息无效" —— 极难排查。
     * 去掉"版本名一致"校验后，version 只剩一个用途：拼临时文件名。
     */
    @Test
    fun theVersionFormatGateNoLongerBlocksFreeText() {
        assertFalse(
            "不许再拿严格 x.y.z 正则卡住整个更新",
            verify.contains(".matches("),
        )
        assertTrue("只要非空就够了", verify.contains("if(version.isEmpty())throw new IOException(\"更新包版本信息无效\")"))
        assertTrue(
            "版本名要拼进临时文件名，必须过滤掉文件名非法字符",
            verify.contains("replaceAll(\"[^0-9A-Za-z._-]\",\"_\")"),
        )
    }

    /** 手动检查更新的入口也不许再用严格正则把用户挡在外面。 */
    @Test
    fun theManualEntryGateIsRelaxedToo() {
        val entry = bodyOf("void requestVerifiedUpdateDownload(Models.Item item,String version)")
        assertFalse(
            "入口处不许再有严格 x.y.z 正则（后台版本名是自由文本）",
            entry.contains(".matches("),
        )
        assertTrue("入口只校验非空", entry.contains("if(expected.isEmpty())"))
    }

    /** 反向探针：确认上面的检查真的能识别坏代码。 */
    @Test
    fun theChecksActuallyDetectBadCode() {
        val badOld = "if(!version.equals(archive.versionName))throw new IOException(\"更新包版本不一致\");"
        assertTrue(
            "坏代码（版本名一致校验又回来了）必须被识别",
            badOld.contains("version.equals(archive.versionName)"),
        )
        val badNoSafety = "if(archive==null||!getPackageName().equals(archive.packageName))throw new IOException(\"x\");"
        assertFalse(
            "坏代码（只剩包名校验、丢了签名与版本序号）不该被判成合格",
            badNoSafety.contains("signatureDigests") && badNoSafety.contains("archiveCode<=currentCode"),
        )
        // 锚点必须真的在源码里，否则上面全在自说自话
        assertTrue("签名校验的锚点必须与源码逐字一致", verify.contains("signatureDigests(current).equals(signatureDigests(archive))"))
    }
}
