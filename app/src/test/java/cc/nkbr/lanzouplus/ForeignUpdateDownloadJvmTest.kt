package cc.nkbr.lanzouplus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

/**
 * [2026-10-03] 「更新通道下到了一个不是我们的包」时，**下载该算成功，安装该被拦住**。
 *
 * ## 用户报的问题
 * 后台指向别的软件时，文件**完整下载成功**了，界面却显示
 * 「下载失败 · dongfang-wuxian-v1.0.1.apk：更新包校验未通过」。用户原话：
 * > 「我不管上传什么软件，它都可以正常下载。我不在乎它能不能覆盖安装，
 * >   毕竟下载别的软件肯定不能覆盖安装，对不对？」
 *
 * 根因不是"限制死了"，而是**两件事被拍扁成了一件**：
 *   · `verifyUpdateApk()` 里「摘要不一致」（文件真的坏了）和
 *     「包名/签名不一致」（文件没问题，只是不是我们的 App）本来就抛**不同的消息**；
 *   · 但调用方 `downloadLanzouPlusUpdate()` 的 catch **不看消息**，
 *     一律 `entry.state=DOWNLOAD_FAILED; entry.error="更新包校验未通过"`。
 *
 * ## 为什么不能顺着用户把校验删掉
 * 用户的论证是「反正安卓自己会拦」。**这个论证只对了一半**：
 * 安卓只在**包名相同**时才拒绝覆盖安装；包名**不同**的 APK 会被系统当成
 * **一个全新的 App 装上去**，不报任何错。而更新元数据（apkUrl/sha256）是从
 * `http://39.106.33.135/pb` **明文**读来的，能改它的人就能把 apkUrl 换成任意 APK。
 * 所以包名 + 签名校验是**唯一**能拦住"被引导去装一个陌生 App"的那道闸。
 *
 * ## 折中（本测试守的就是它）
 * **保校验，只改表现**：校验不过时把记录**降级成外部来源** ——
 * 下载算完成、文件保留，但要走和外部链接下载**完全一样**的手续
 * （点安装先过 `showExternalInstallConfirmation` 确认），绝不自动装。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class ForeignUpdateDownloadJvmTest {

    /** 造一条"从更新通道下到的"记录，字段都按更新通道的真实形态填满。 */
    private fun updateEntry(): MainActivity.DownloadEntry =
        MainActivity.DownloadEntry().apply {
            source = DownloadSourcePolicy.UPDATE
            expectedUpdateVersion = "1.0.1"
            autoInstall = true
            updateVerified = false
            installConfirmationGranted = true
            name = "dongfang-wuxian-v1.0.1.apk"
            state = MainActivity.DOWNLOAD_COMPLETED
        }

    // ── 前置：确认造出来的记录**确实**处在"会被自动安装"的状态 ──────────────

    /**
     * 没有这条，后面几条测试就可能是假绿 —— 如果 `UPDATE` 本来就要求确认，
     * 那"降级成 EXTERNAL"这个动作等于什么都没做，测试照样通过。
     *
     * ⚠️ 2026-10-03 对抗性复查指出：还缺**另一半**前提 ——
     * 整条降级链的唯一目的是"落到一个**必须确认**的来源上"，
     * 而 `EXTERNAL` 本身"必须确认"这个事实**从来没有被任何断言直接钉住**。
     * 如果哪天 `DownloadSourcePolicy` 把 `EXTERNAL` 改成可自动安装，
     * 降级就会**静默失效**（条目降级了，但照样自动装），而这里所有测试仍然绿。
     */
    @Test
    fun precondition_externalSourceIsTheOneThatRequiresConfirmation() {
        assertTrue("EXTERNAL 必须是要确认的来源 —— 否则「降级」等于没降",
            DownloadSourcePolicy.requiresInstallConfirmation(DownloadSourcePolicy.EXTERNAL))
        assertFalse("EXTERNAL 绝不能允许自动安装",
            DownloadSourcePolicy.allowsAutomaticInstall(DownloadSourcePolicy.EXTERNAL))
        assertFalse("EXTERNAL 绝不能允许静默安装",
            DownloadSourcePolicy.allowsSilentInstall(DownloadSourcePolicy.EXTERNAL))
    }

    /**
     * 没有这条，后面几条测试就可能是假绿 —— 如果 `UPDATE` 本来就要求确认，
     * 那"降级成 EXTERNAL"这个动作等于什么都没做，测试照样通过。
     */
    @Test
    fun precondition_updateSourceIsTrustedAndAutoInstalls() {
        val e = updateEntry()
        assertFalse("更新通道本来不该要求确认，否则后面的降级测试没意义",
            DownloadSourcePolicy.requiresInstallConfirmation(e.source))
        assertTrue("更新通道本来允许自动安装，否则后面的降级测试没意义",
            DownloadSourcePolicy.allowsAutomaticInstall(e.source))
        assertTrue("更新通道的记录本来带 expectedUpdateVersion",
            e.expectedUpdateVersion.isNotEmpty())
        assertTrue("更新通道的记录本来 autoInstall=true", e.autoInstall)
    }

    // ── 降级本身 ────────────────────────────────────────────────────────────

    @Test
    fun foreignUpdate_isDemotedToExternalSource() {
        val e = updateEntry()
        MainActivity.demoteForeignUpdateToExternal(e)
        assertEquals("必须降级成外部来源", DownloadSourcePolicy.EXTERNAL, e.source)
    }

    /**
     * 降级的**目的**：让用户点安装时先看到"外部来源"确认。
     * 这条直接问 `DownloadSourcePolicy`（真正做决定的那两个函数），不是问字段。
     */
    @Test
    fun demotedEntry_requiresUserConfirmationBeforeInstall() {
        val e = updateEntry()
        MainActivity.demoteForeignUpdateToExternal(e)
        assertTrue("降级后点安装必须先过用户确认",
            DownloadSourcePolicy.requiresInstallConfirmation(e.source))
        assertFalse("降级后绝不允许自动/静默安装",
            DownloadSourcePolicy.allowsAutomaticInstall(e.source))
        assertFalse("降级后不允许静默安装",
            DownloadSourcePolicy.allowsSilentInstall(e.source))
    }

    /**
     * `expectedUpdateVersion` 非空 + `updateVerified=false` ⇒ `installEntry()` 会
     * 再走一次 `verifyCloudUpdateEntry()`，而那个校验**永远过不了**（包不是我们的），
     * 用户点安装只会再看到一次"校验未通过"。所以必须清空。
     */
    @Test
    fun demotedEntry_willNotReenterCloudUpdateVerification() {
        val e = updateEntry()
        MainActivity.demoteForeignUpdateToExternal(e)
        assertTrue("必须清空 expectedUpdateVersion，否则点安装会再撞一次校验",
            e.expectedUpdateVersion.isEmpty())
        assertFalse("updateVerified 必须是 false（它本来就没通过校验）", e.updateVerified)
    }

    @Test
    fun demotedEntry_neverAutoInstalls() {
        val e = updateEntry()
        MainActivity.demoteForeignUpdateToExternal(e)
        assertFalse("降级后绝不自动安装", e.autoInstall)
        assertFalse("之前的确认不能沿用，必须重新确认", e.installConfirmationGranted)
    }

    /**
     * 下载本身是成功的 —— 降级**不许**碰下载进度字段，否则用户会看到
     * "下载完成"却显示 **0% / 0 字节**。
     *
     * ⚠️ 2026-10-03 对抗性复查指出：这条原来**只断言了 `state`**，
     * 把 `percent` / `downloadedBytes` / `totalBytes` 清零它照样绿。已补齐。
     */
    @Test
    fun demotion_doesNotTouchDownloadState() {
        val e = updateEntry()
        e.percent = 100
        e.downloadedBytes = 36_840_658L
        e.totalBytes = 36_840_658L
        e.verifiedTotalBytes = 36_840_658L
        val beforeState = e.state
        MainActivity.demoteForeignUpdateToExternal(e)
        assertEquals("降级只改「来源」语义，不许动下载状态", beforeState, e.state)
        assertEquals("进度不能被清零，否则用户看到「下载完成 0%」", 100, e.percent)
        assertEquals("已下载字节不能被清零", 36_840_658L, e.downloadedBytes)
        assertEquals("总字节不能被清零", 36_840_658L, e.totalBytes)
        assertEquals("已校验字节不能被清零", 36_840_658L, e.verifiedTotalBytes)
    }

    /**
     * 传 `null` 不许抛 —— 捕获分支里 `entry` 理论上是非空的，但防御性检查在那里，
     * 就不许它变成新的崩溃点。
     *
     * ⚠️ 2026-10-03 对抗性复查指出：这条原来是**零断言**（只调了一次方法），
     * 靠"JUnit 未捕获异常即失败"隐式生效 —— 能红，但**意图在报告里完全不可见**，
     * 而且一旦有人套一层 `try/catch` 就立刻变成真·假绿（本仓库发生过一次）。
     * 改成显式断言。
     */
    @Test
    fun demotion_toleratesNull() {
        try {
            MainActivity.demoteForeignUpdateToExternal(null)
        } catch (error: Throwable) {
            throw AssertionError("传 null 不许抛：catch 分支里 entry 可能为 null，抛了会把下载流程带崩", error)
        }
    }

    // ── 两类失败必须能区分开 ────────────────────────────────────────────────

    /**
     * 捕获方靠 `instanceof UpdateNotInstallableException` 分流，所以这个类型必须存在且可区分。
     * 如果哪天有人把它改回裸 `IOException`，分流就会失效、又变回"下载失败"。
     */
    @Test
    fun notInstallableUpdate_isAnIOExceptionButDistinguishable() {
        val error = MainActivity.UpdateNotInstallableException("更新包签名不一致")
        assertTrue("必须是 IOException（捕获方按 IOException 兜底，不能漏）", error is IOException)
        assertTrue("必须是 UpdateNotInstallableException，捕获方靠它分流",
            error is MainActivity.UpdateNotInstallableException)
        val corrupt = IOException("更新包摘要不一致")
        assertFalse("'文件坏了'那类不能也算成 UpdateNotInstallableException，否则会当成下载成功",
            corrupt is MainActivity.UpdateNotInstallableException)
    }

    // ── 源码级：接线必须真的接上 ────────────────────────────────────────────

    private fun mainActivitySource(): String =
        java.io.File("src/main/java/cc/nkbr/lanzouplus/MainActivity.java").readText()

    /** 从 `from` 切到 `to`（不含）—— 只看这一段方法体，避免全仓匹配到别处。 */
    private fun slice(src: String, from: String, to: String): String {
        val a = src.indexOf(from)
        assertTrue("找不到切片起点：$from", a > 0)
        val b = src.indexOf(to, a)
        assertTrue("找不到切片终点：$to", b > a)
        return src.substring(a, b)
    }

    /**
     * [2026-10-03] **DFW-90 的「真实版本序号更大」底线，两条校验通道都必须有。**
     *
     * DFW-90 的原文（`docs/plan/dfw-90-95-96-103-batch.md:20-24`）：
     * > 「真实版本序号更大 | 确认包真的比用户手上的新 | **留**
     * >   包本身不比当前版本新 → 装完版本没变 → 后台还说有新版本 → **无限循环提示**。
     * >   这是物理限制，不是保守。」
     *
     * **但那条守卫当时只加进了 `verifyArchiveUpdate`（本地归档通道）。**
     * 而用户走的是"点更新提示 → 下载"这条通道，它调的是 `verifyUpdateApk` ——
     * 于是后台只要把序号填得比包大（记录 10001、包还是 10000），
     * App 就会下载 → 放行 → 装上 → 版本没变 → 后台还说有新版本 → **永远提示**。
     *
     * 这条测试守的就是"**两条通道都得有**"。只守住一条 = 用户那条是裸的。
     */
    @Test
    fun bothVerificationPathsRejectANonNewerPackage() {
        val src = mainActivitySource()
        val updateChannel = slice(src, "void verifyUpdateApk(", "InputStream openUriInput(")
        val archiveChannel = slice(src, "Uri verifyArchiveUpdate(", "void autoInstallCompletedEntry(")
        assertTrue(
            "更新通道（用户点「下载」走的就是这条）必须比版本序号 —— 否则后台填个大序号就永远提示更新",
            updateChannel.contains("archiveCode<=currentCode"),
        )
        assertTrue(
            "本地归档通道也必须比版本序号（DFW-90 原本就在这里，别被删掉）",
            archiveChannel.contains("archiveCode<=currentCode"),
        )
    }

    /**
     * 「不比当前版本新」必须归到 `UpdateNotInstallableException`（下载算完成、降级成外部来源），
     * **不能**归到裸 `IOException` —— 否则用户会看到"下载失败"，而这正是本次要修的那个 bug 形态。
     */
    @Test
    fun nonNewerPackageIsTreatedAsDownloadedButNotInstallable() {
        val src = mainActivitySource()
        val updateChannel = slice(src, "void verifyUpdateApk(", "InputStream openUriInput(")
        assertTrue(
            "「不比当前版本新」必须抛 UpdateNotInstallableException，不能抛 IOException",
            updateChannel.contains("throw new UpdateNotInstallableException(\"这个包并不比当前版本新"),
        )
    }

    /**
     * 去掉块注释（含 Javadoc）与**整行行注释**，再做断言。
     *
     * ## 为什么必须这一步（2026-10-03 对抗性复查抓出来的真·假绿）
     * 原来直接在整份源码上 `contains(...)` —— 于是**把那一句代码注释掉，字符串还在文件里，
     * 断言照样通过**。复查给出的最小反例：
     * ```java
     * // if(!signatureDigests(current).equals(signatureDigests(archive)))throw new UpdateNotInstallableException("更新包签名不一致");
     * ```
     * 这一行注释掉之后，签名校验**已经没了**（能改 apkUrl 的人就能让 App 去装陌生包），
     * 而测试报告显示"签名校验存在"。这正是本仓库反复强调的假绿。
     *
     * 只去掉**整行**行注释（`trimStart()` 后以 `//` 开头），不动行尾 —— 因为
     * 源码里有 `"http://…"` 这种含 `//` 的字符串字面量，粗暴按 `//` 切会把它截断。
     */
    private fun stripComments(src: String): String {
        val noBlock = Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL).replace(src, " ")
        return noBlock.lineSequence()
            .filterNot { it.trimStart().startsWith("//") }
            .joinToString("\n")
    }

    /**
     * `verifyUpdateApk()` 必须在**包名/签名**那一支抛 `UpdateNotInstallableException`，
     * 在**摘要**那一支继续抛裸 `IOException`。抛错类型搞反，整个分流就反了：
     * 文件坏了会被当成"下载成功"，而别的 App 会被当成"下载失败"。
     */
    @Test
    fun verifyUpdateApk_throwsTheRightTypeForEachFailure() {
        val body = stripComments(slice(mainActivitySource(), "void verifyUpdateApk(", "InputStream openUriInput("))
        assertTrue(
            "包名/签名不一致必须抛 UpdateNotInstallableException（否则分不了流）",
            body.contains("throw new UpdateNotInstallableException(\"更新包签名不一致\")"),
        )
        assertTrue(
            "摘要不一致必须继续抛裸 IOException（那是真的下载坏了，该报失败）",
            body.contains("throw new IOException(\"更新包摘要不一致\")"),
        )
    }

    /**
     * [2026-10-03] 三种情况原来**合并成一个条件、共用一句话**：
     * `if(archive==null||包名不等||签名不等) throw new UpdateNotInstallableException("更新包签名不一致")`。
     * 后果：用户遇到的是「包名不一致」，报出来的却是「签名不一致」——
     * 排障时会被引到完全错误的方向（去查签名，而问题在包名）。
     * 同一个 App 里的 `verifyArchiveUpdate` 早就是分开的，这里对齐。
     *
     * ⚠️ 复查指出这条的 `assertFalse` **空格敏感**：旧的合并条件加个空格写回去就能绕过。
     * 改成用正则容忍空白。
     */
    @Test
    fun verifyUpdateApk_separatesPackageNameFromSignature() {
        val body = stripComments(slice(mainActivitySource(), "void verifyUpdateApk(", "InputStream openUriInput("))
        assertTrue(
            "包名不一致要有自己的消息",
            body.contains("throw new UpdateNotInstallableException(\"更新包包名不一致\")"),
        )
        assertTrue(
            "无法解析要有自己的消息（摘要已过，说明文件和服务端一致，是包本身有问题）",
            body.contains("throw new UpdateNotInstallableException(\"更新包不是有效的 APK\")"),
        )
        val merged = Regex("if\\(\\s*archive==null\\s*\\|\\|\\s*!getPackageName\\(\\)\\.equals\\(archive\\.packageName\\)\\s*\\|\\|")
        assertFalse(
            "不许再把「无法解析 / 包名不一致 / 签名不一致」三种情况合并成一个条件（空格变体也不行）",
            merged.containsMatchIn(body),
        )
    }

    /**
     * [2026-10-03] 「文件真坏」那条路原来**不删坏字节**：完整大小留在磁盘上、名字还是 `.part`，
     * 用户看不到、App 读不了，而重试还会去续传这份坏文件（`resumeTargetAvailable` 对 file: 恒真）
     * → 拼出混合文件、永远校验不过。必须删掉，重试才是一次干净的全量下载。
     */
    @Test
    fun corruptDownload_discardsTheBadBytes() {
        val src = stripComments(mainActivitySource())
        val failAt = src.indexOf("entry.error=\"更新包校验未通过：\"")
        assertTrue("找不到「文件真坏」那条失败分支", failAt > 0)
        /*
         * ⚠️ **必须只看这一段分支内部**，不能全仓 `indexOf`。
         *
         * 第一版就是全仓找的，结果**反向探针把它抓出来了**：把这里的调用删掉，测试照样绿 ——
         * 因为"取消下载"那条路径里也有一次同名的 `discardCancelledPartial(entry)`，
         * `indexOf` 匹配到了那一处。这正是本仓库反复强调的"假绿"。
         */
        val branchEnd = src.indexOf("}});}", failAt)
        assertTrue("找不到失败分支的结尾", branchEnd > failAt)
        val branch = src.substring(failAt, branchEnd)
        val discardAt = branch.indexOf("discardCancelledPartial(entry)")
        assertTrue(
            "「文件真坏」分支必须删掉坏字节，否则留下几十兆孤儿 .part 且重试永远不自愈",
            discardAt > 0,
        )
        /*
         * 顺序：先标失败、再删（删会重置 percent/target，必须在状态定下来之后）。
         *
         * ⚠️ 复查指出原来那句 `discardAt > 0` **恒真**（上一行已经断言了 contains），
         * 测不出任何顺序 —— 把调用挪到 `entry.error=…` 之前它照样绿。
         * 改成与分支内的位置做真比较。
         */
        val errorAt = branch.indexOf("entry.error=\"更新包校验未通过：\"")
        assertTrue("分支内必须能定位到「标记失败」那一句", errorAt >= 0)
        assertTrue(
            "删除必须发生在「标记失败」之后（删会重置 percent/target，顺序反了状态就丢了）",
            discardAt > errorAt,
        )
    }

    /**
     * 捕获方必须先判 `UpdateNotInstallableException`、必须调用降级函数、**而且必须 `return`**。
     *
     * ⚠️ 2026-10-03 对抗性复查给出的最小反例：**把 `return;` 删掉，原来那四条断言全部仍然通过**
     * （三个锚点位置都没变），但该分支会继续往下走到「标记下载失败」——
     * **用户看到的正是本次要修的那个 bug**（下载成功了却显示失败）。
     * 所以必须断言分支体里有 `return;`，且**不含** `DOWNLOAD_FAILED`。
     */
    @Test
    fun catchBlock_branchesOnNotOurAppBeforeFailingTheDownload() {
        val src = stripComments(mainActivitySource())
        val branchAt = src.indexOf("if(error instanceof UpdateNotInstallableException){")
        assertTrue("捕获方必须显式分流 UpdateNotInstallableException", branchAt > 0)
        /*
         * 切片边界必须落在**失败分支的起点** `boolean failed;` 上。
         * 不能用 `entry.error="更新包校验未通过："` 当边界 —— 同一行里
         * `entry.state=DOWNLOAD_FAILED;` 在它**之前**，会被切进来，于是下面那条
         * 「不许出现 DOWNLOAD_FAILED」永远失败（这是修这条测试时实际踩到的）。
         */
        val failBranchAt = src.indexOf("boolean failed;", branchAt)
        assertTrue("找不到「文件真坏」那条失败分支的起点", failBranchAt > branchAt)
        val demoteAt = src.indexOf("demoteForeignUpdateToExternal(entry)", branchAt)
        assertTrue(
            "降级必须发生在「标记下载失败」之前，否则先失败就回不来了",
            demoteAt in (branchAt + 1) until failBranchAt,
        )

        val branch = src.substring(branchAt, failBranchAt)
        assertTrue(
            "分流分支必须 `return;` —— 否则会继续走到「标记下载失败」，用户又看到「下载失败」",
            branch.contains("return;"),
        )
        assertFalse(
            "分流分支里不许出现 DOWNLOAD_FAILED",
            branch.contains("DOWNLOAD_FAILED"),
        )
    }
}
