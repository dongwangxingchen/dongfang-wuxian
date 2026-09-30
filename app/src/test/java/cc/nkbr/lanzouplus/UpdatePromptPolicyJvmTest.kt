package cc.nkbr.lanzouplus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [DFWX] DFW-59：更新弹窗的**模式取舍真值表**。
 *
 * 这张卡最容易出的错不是崩溃，而是**少一个或错一个按钮**——
 * 用户明确要求"强制更新"时不能有取消/不再显示；"软更新"时四个按钮齐全；
 * 而"不再显示"又必须**只针对该具体版本**（换版本照弹）。
 * 这些都是策略真值，适合在这里直接钉死，而不是靠翻对话框按钮间接验证。
 */
class UpdatePromptPolicyJvmTest {

    /** 内存版偏好：模拟"用户点过不再显示"的落盘。 */
    private class FakeStore(var value: Long = 0L) : UpdatePromptPolicy.Store {
        override fun dismissedVersionCode(): Long = value
        override fun setDismissedVersionCode(versionCode: Long) { value = versionCode }
    }

    private fun policy(store: FakeStore = FakeStore()) = UpdatePromptPolicy(store)

    // ── 模式解析 ──────────────────────────────────────────────────────────

    @Test
    fun modeParsing_unknownFallsBackToSoft_neverTrapsUserInForce() {
        assertEquals(UpdatePromptPolicy.Mode.FORCE, UpdatePromptPolicy.Mode.parse("force"))
        assertEquals(UpdatePromptPolicy.Mode.OFF, UpdatePromptPolicy.Mode.parse("off"))
        assertEquals(UpdatePromptPolicy.Mode.SOFT, UpdatePromptPolicy.Mode.parse("soft"))
        // 大小写、空格都要容忍（后台是人手填的）
        assertEquals(UpdatePromptPolicy.Mode.FORCE, UpdatePromptPolicy.Mode.parse(" FORCE "))
        // 关键：字段写错/为空/拼错时必须是 SOFT。
        // 若默认成 FORCE，后台一次手误就能把全体用户锁进"无法取消"的强制更新。
        assertEquals(UpdatePromptPolicy.Mode.SOFT, UpdatePromptPolicy.Mode.parse(null))
        assertEquals(UpdatePromptPolicy.Mode.SOFT, UpdatePromptPolicy.Mode.parse(""))
        assertEquals(UpdatePromptPolicy.Mode.SOFT, UpdatePromptPolicy.Mode.parse("forced"))
        assertEquals(UpdatePromptPolicy.Mode.SOFT, UpdatePromptPolicy.Mode.parse("yes"))
    }

    // ── 按钮集合 ──────────────────────────────────────────────────────────

    @Test
    fun forceMode_hasNoCancelAndNoNeverRemind() {
        val b = UpdatePromptPolicy.buttonsFor(UpdatePromptPolicy.Mode.FORCE)
        assertTrue("强制更新必须有「立即更新」", b.alwaysUpdate)
        assertFalse("强制更新**不得**有取消（用户要求：无取消）", b.cancel)
        assertFalse("强制更新**不得**有「不再显示」（用户要求：无不再显示）", b.neverRemind)
    }

    @Test
    fun softMode_hasAllFourButtons() {
        val b = UpdatePromptPolicy.buttonsFor(UpdatePromptPolicy.Mode.SOFT)
        assertTrue("软更新有「立即更新」", b.alwaysUpdate)
        assertTrue("软更新有取消", b.cancel)
        assertTrue("软更新有「不再显示」", b.neverRemind)
    }

    @Test
    fun offMode_showsNothing() {
        val b = UpdatePromptPolicy.buttonsFor(UpdatePromptPolicy.Mode.OFF)
        assertFalse("关闭模式不该弹窗，也就不该有任何按钮", b.alwaysUpdate || b.cancel || b.neverRemind)
    }

    // ── 该不该弹 ──────────────────────────────────────────────────────────

    @Test
    fun offMode_neverPrompts() {
        assertFalse(
            "off 模式：即使目标版本更新也不弹",
            policy().shouldPrompt(UpdatePromptPolicy.Mode.OFF, 10001L, 10000L),
        )
    }

    @Test
    fun sameOrOlderVersion_neverPrompts() {
        val p = policy()
        assertFalse("同版本不弹", p.shouldPrompt(UpdatePromptPolicy.Mode.SOFT, 10000L, 10000L))
        assertFalse("后台版本比已装版本旧时不弹（防后台配错导致降级）", p.shouldPrompt(UpdatePromptPolicy.Mode.SOFT, 9999L, 10000L))
        assertFalse("强制模式同样不该提示降级", p.shouldPrompt(UpdatePromptPolicy.Mode.FORCE, 9999L, 10000L))
        assertTrue("目标更新时必须弹", p.shouldPrompt(UpdatePromptPolicy.Mode.SOFT, 10001L, 10000L))
    }

    @Test
    fun neverRemind_isScopedToThatExactVersion_only() {
        val store = FakeStore()
        val p = policy(store)

        assertTrue("首次遇到 10001 应弹", p.shouldPrompt(UpdatePromptPolicy.Mode.SOFT, 10001L, 10000L))
        p.rememberDismissed(10001L)
        assertFalse("点过不再显示后，同一版 10001 不得再弹", p.shouldPrompt(UpdatePromptPolicy.Mode.SOFT, 10001L, 10000L))
        // 这是用户明确要求的语义："只针对该具体版本（换版本照弹）"
        assertTrue(
            "换成 10002 必须照弹——「不再显示」不是「永不更新」",
            p.shouldPrompt(UpdatePromptPolicy.Mode.SOFT, 10002L, 10000L),
        )

        // 记忆必须落盘在 store 上（换 Activity 实例仍生效）
        assertEquals("被忽略的版本号要存下来", 10001L, store.dismissedVersionCode())
    }

    @Test
    fun forceMode_ignoresNeverRemind() {
        val store = FakeStore(value = 10001L)
        val p = policy(store)
        assertTrue(
            "强制更新无视历史「不再显示」：否则用户点过一次就能永久绕过强制（名不副实）",
            p.shouldPrompt(UpdatePromptPolicy.Mode.FORCE, 10001L, 10000L),
        )
    }

    @Test
    fun manualCheck_clearsDismissedSoUserCanSeeItAgain() {
        val store = FakeStore(value = 10001L)
        val p = policy(store)
        assertFalse("先确认被记住的状态确实拦住了", p.shouldPrompt(UpdatePromptPolicy.Mode.SOFT, 10001L, 10000L))
        p.clearDismissed()
        assertEquals("手动检查应清掉记忆", 0L, p.dismissedVersionCode())
        assertTrue(
            "用户主动点「检查更新」时必须能看到该版本——否则手动入口给出「已是最新」是骗人的",
            p.shouldPrompt(UpdatePromptPolicy.Mode.SOFT, 10001L, 10000L),
        )
    }

    @Test
    fun storeFailure_degradesToPrompting_notToSilence() {
        // 存储读失败时必须"弹"而不是"不弹"：更新提示漏掉是用户感知不到的静默故障，
        // 多弹一次只是轻微打扰，两者代价不对等。
        val broken = object : UpdatePromptPolicy.Store {
            override fun dismissedVersionCode(): Long = 0L
            override fun setDismissedVersionCode(versionCode: Long) { throw RuntimeException("disk full") }
        }
        val p = UpdatePromptPolicy(broken)
        assertTrue("读不到记忆时宁可多弹一次", p.shouldPrompt(UpdatePromptPolicy.Mode.SOFT, 10001L, 10000L))
        // 写失败不得把异常抛给调用方（点「不再显示」不该崩）
        p.rememberDismissed(10001L)
    }
}
