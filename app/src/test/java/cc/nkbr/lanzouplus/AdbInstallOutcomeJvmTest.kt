package cc.nkbr.lanzouplus

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [DFWX] DFW-53 静默安装结果判定回归（语言无关）。
 *
 * ## 修的是什么
 * 原判定是 `exit==0 && message.contains("success")`。
 * `pm install` 的输出是**本地化**的——中文系统返回"成功"，日文返回"成功しました"，
 * **只有英文系统才含 "success"**。于是在中文/日文系统上：
 * **安装成功也会被判为失败**，用户看到"失败"（其实已装好），重试又遇到"已存在"，更混乱。
 *
 * 这是典型的"英文环境测不出来"的缺陷——本项目开发者环境正是中文，本该第一时间暴露，
 * 但静默安装需要 ADB/Shizuku 授权，真机验证成本高，所以一直没被发现（审计 T11 点名）。
 *
 * ## 新判定
 * **以退出码为准**（语言无关的客观事实）；文本只用于展示，并在少数"退出码 0 但输出是失败文本"
 * 的 ROM 上做保守兜底。
 */
class AdbInstallOutcomeJvmTest {

    private fun outcome(exit: Int, message: String) = AdbShellService.installOutcome(exit, message)

    private fun isOk(result: String) = result.startsWith("OK")
    private fun isError(result: String) = result.startsWith("ERROR")

    // ---------- 核心修复：非英文系统的成功输出必须被判为成功 ----------

    @Test
    fun chineseSuccessIsRecognised() {
        // 中文系统 `pm install` 成功时的典型输出
        assertTrue("中文系统的'成功'必须被判为成功（原实现只看 success 会误判）：${outcome(0, "成功")}", isOk(outcome(0, "成功")))
    }

    @Test
    fun japaneseSuccessIsRecognised() {
        assertTrue("日文系统必须被判为成功：${outcome(0, "成功しました")}", isOk(outcome(0, "成功しました")))
    }

    @Test
    fun englishSuccessIsStillRecognised() {
        assertTrue(isOk(outcome(0, "Success")))
        assertTrue(isOk(outcome(0, "success")))
    }

    /** 空输出 + 退出码 0：也是成功（不应因为"没看到 success 字样"就判失败）。 */
    @Test
    fun emptyOutputWithZeroExitIsSuccess() {
        assertTrue("退出码 0 且无输出 = 成功（原实现会因缺 'success' 误判失败）", isOk(outcome(0, "")))
    }

    @Test
    fun otherLocaleSuccessIsRecognised() {
        // 韩文/俄文等：只要退出码 0 就算成功
        assertTrue(isOk(outcome(0, "성공")))
        assertTrue(isOk(outcome(0, "успешно")))
    }

    // ---------- 失败必须被判为失败 ----------

    @Test
    fun nonZeroExitIsFailure() {
        assertTrue(isError(outcome(1, "Failure [INSTALL_FAILED_ALREADY_EXISTS]")))
        assertTrue(isError(outcome(255, "error: no devices")))
        assertTrue(isError(outcome(1, "")))
    }

    /** 少数 ROM 退出码 0 却带失败文本 —— 保守兜底。 */
    @Test
    fun zeroExitButFailureTextIsTreatedAsFailure() {
        assertTrue("退出码 0 但输出是 FAILURE，必须按失败处理", isError(outcome(0, "Failure [INSTALL_FAILED_INSUFFICIENT_STORAGE]")))
        assertTrue("中文失败词也要兜住", isError(outcome(0, "安装失败：空间不足")))
        assertTrue("拒绝也要兜住", isError(outcome(0, "Permission denied")))
    }

    /** 绝不能把成功文本误判成失败（回归方向）。 */
    @Test
    fun successTextIsNeverMisjudgedAsFailure() {
        for (msg in listOf("成功", "成功しました", "Success", "success", "성공", "успешно", "インストール完了")) {
            assertTrue("「$msg」是成功文本，不得判为失败：${outcome(0, msg)}", isOk(outcome(0, msg)))
        }
    }

    /** 历史问题：英文环境测不出这个 bug，所以补一条"曾经会红"的断言组合。 */
    @Test
    fun regression_theOldRuleWouldHaveFailedHere() {
        // 旧规则 = exit==0 && contains("success")
        val oldRuleWouldFail = { msg: String -> !(0 == 0 && msg.lowercase().contains("success")) }
        assertTrue("中文'成功'在旧规则下确实会被判失败（这正是修复的动机）", oldRuleWouldFail("成功"))
        assertTrue("新规则下必须判成功", isOk(outcome(0, "成功")))
    }

    /** 结果串必须便于上层解析（OK/ERROR 开头 + 换行 + 原文）。 */
    @Test
    fun resultFormatIsParseable() {
        val ok = outcome(0, "成功")
        assertTrue("成功结果应以 OK\\n 开头：$ok", ok.startsWith("OK\n"))
        val err = outcome(1, "Failure")
        assertTrue("失败结果应以 ERROR\\n 开头：$err", err.startsWith("ERROR\n"))
        assertFalse("失败结果不应带 OK 前缀", err.startsWith("OK"))
    }

    /** null 输出不得崩。 */
    @Test
    fun nullMessageIsSafe() {
        assertTrue(isOk(AdbShellService.installOutcome(0, null)))
        assertTrue(isError(AdbShellService.installOutcome(1, null)))
    }
}
