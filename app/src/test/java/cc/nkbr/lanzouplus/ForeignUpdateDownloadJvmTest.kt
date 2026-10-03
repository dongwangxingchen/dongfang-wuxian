package cc.nkbr.lanzouplus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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

    /** 下载本身是成功的 —— 降级**不许**碰状态字段，否则又变成"下载失败"了。 */
    @Test
    fun demotion_doesNotTouchDownloadState() {
        val e = updateEntry()
        val before = e.state
        MainActivity.demoteForeignUpdateToExternal(e)
        assertEquals("降级只改「来源」语义，不许动下载状态", before, e.state)
    }

    @Test
    fun demotion_toleratesNull() {
        MainActivity.demoteForeignUpdateToExternal(null)
    }

    // ── 两类失败必须能区分开 ────────────────────────────────────────────────

    /**
     * 捕获方靠 `instanceof NotOurAppException` 分流，所以这个类型必须存在且可区分。
     * 如果哪天有人把它改回裸 `IOException`，分流就会失效、又变回"下载失败"。
     */
    @Test
    fun notOurApp_isAnIOExceptionButDistinguishable() {
        val error = MainActivity.NotOurAppException("更新包签名不一致")
        assertTrue("必须是 IOException（捕获方按 IOException 兜底，不能漏）", error is IOException)
        assertTrue("必须是 NotOurAppException，捕获方靠它分流",
            error is MainActivity.NotOurAppException)
        val corrupt = IOException("更新包摘要不一致")
        assertFalse("'文件坏了'那类不能也算成 NotOurAppException，否则会当成下载成功",
            corrupt is MainActivity.NotOurAppException)
    }

    // ── 源码级：接线必须真的接上 ────────────────────────────────────────────

    private fun mainActivitySource(): String =
        java.io.File("src/main/java/cc/nkbr/lanzouplus/MainActivity.java").readText()

    /**
     * `verifyUpdateApk()` 必须在**包名/签名**那一支抛 `NotOurAppException`，
     * 在**摘要**那一支继续抛裸 `IOException`。抛错类型搞反，整个分流就反了：
     * 文件坏了会被当成"下载成功"，而别的 App 会被当成"下载失败"。
     */
    @Test
    fun verifyUpdateApk_throwsTheRightTypeForEachFailure() {
        val src = mainActivitySource()
        assertTrue(
            "包名/签名不一致必须抛 NotOurAppException（否则分不了流）",
            src.contains("throw new NotOurAppException(\"更新包签名不一致\")"),
        )
        assertTrue(
            "摘要不一致必须继续抛裸 IOException（那是真的下载坏了，该报失败）",
            src.contains("throw new IOException(\"更新包摘要不一致\")"),
        )
    }

    /**
     * [2026-10-03] 三种情况原来**合并成一个条件、共用一句话**：
     * `if(archive==null||包名不等||签名不等) throw new NotOurAppException("更新包签名不一致")`。
     * 后果：用户遇到的是「包名不一致」，报出来的却是「签名不一致」——
     * 排障时会被引到完全错误的方向（去查签名，而问题在包名）。
     * 同一个 App 里的 `verifyArchiveUpdate` 早就是分开的，这里对齐。
     */
    @Test
    fun verifyUpdateApk_separatesPackageNameFromSignature() {
        val src = mainActivitySource()
        assertTrue(
            "包名不一致要有自己的消息",
            src.contains("throw new NotOurAppException(\"更新包包名不一致\")"),
        )
        assertTrue(
            "无法解析要有自己的消息（摘要已过，说明文件和服务端一致，是包本身有问题）",
            src.contains("throw new NotOurAppException(\"更新包不是有效的 APK\")"),
        )
        assertFalse(
            "不许再把三种情况合并成一个条件",
            src.contains("if(archive==null||!getPackageName().equals(archive.packageName)||"),
        )
    }

    /**
     * [2026-10-03] 「文件真坏」那条路原来**不删坏字节**：完整大小留在磁盘上、名字还是 `.part`，
     * 用户看不到、App 读不了，而重试还会去续传这份坏文件（`resumeTargetAvailable` 对 file: 恒真）
     * → 拼出混合文件、永远校验不过。必须删掉，重试才是一次干净的全量下载。
     */
    @Test
    fun corruptDownload_discardsTheBadBytes() {
        val src = mainActivitySource()
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
        assertTrue(
            "「文件真坏」分支必须删掉坏字节，否则留下几十兆孤儿 .part 且重试永远不自愈",
            branch.contains("discardCancelledPartial(entry)"),
        )
        // 顺序：先标失败、再删（删会重置 percent/target，必须在状态定下来之后）
        assertTrue(
            "删除必须发生在标记失败之后",
            branch.indexOf("discardCancelledPartial(entry)") > 0,
        )
    }

    /**
     * 捕获方必须先判 `NotOurAppException`，而且必须调用降级函数。
     *
     * 这两条一起构成"接线"：只判类型不降级 → 用户点安装会再撞一次校验；
     * 只降级不判类型 → 永远走不到。
     */
    @Test
    fun catchBlock_branchesOnNotOurAppBeforeFailingTheDownload() {
        val src = mainActivitySource()
        val branchAt = src.indexOf("if(error instanceof NotOurAppException){")
        assertTrue("捕获方必须显式分流 NotOurAppException", branchAt > 0)
        val demoteAt = src.indexOf("demoteForeignUpdateToExternal(entry)", branchAt)
        assertTrue("分流分支里必须调用降级函数", demoteAt > branchAt)
        val failAt = src.indexOf("entry.error=\"更新包校验未通过：\"", branchAt)
        assertTrue(
            "降级必须发生在「标记下载失败」之前（否则先失败就回不来了）",
            failAt > demoteAt,
        )
        assertNotNull(src)
    }
}
