package cc.nkbr.lanzouplus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * [DFWX] DFW-67：M3 token 的**一致性守卫**。
 *
 * 这次 UI 重置的核心目标是"让 App 的两半用同一套设计语言"：
 * - 一半是 AI 页（Compose），已经有完整的 M3 角色色板 `DongfangTheme.kt`
 * - 另一半是主机区（Java），原本只有 11 个扁平色
 *
 * 所以 `M3Tokens.java` 的颜色**不是新编的**，而是抄 `DongfangTheme.kt` 的 dark scheme。
 * 本测试**直接从那个 Kotlin 文件里解析出真实色值**来比对——
 * 否则哪天有人改了 Compose 侧的主题色而忘了同步这里，两半又会重新分叉，
 * 而且**不会有人发现**（一边看着正常，另一边就是差一点）。
 *
 * 这是"跨语言、跨模块的单一真相源"守卫，比单边断言更有价值。
 */
class M3TokensJvmTest {

    private val root: File = run {
        var dir = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (!File(dir, "app/src/main/AndroidManifest.xml").isFile && dir.parentFile != null) dir = dir.parentFile
        dir
    }

    private val dongfangTheme: String by lazy {
        val f = File(root,
            "rikkahub/app/src/main/java/me/rerere/rikkahub/ui/theme/presets/DongfangTheme.kt")
        assertTrue("必须能找到 Compose 侧的 M3 主题定义（它是颜色真相源）：${f.path}", f.isFile)
        f.readText(Charsets.UTF_8)
    }

    /** 从 DongfangTheme.kt 里读某个 `private val xxxDark = Color(0xFF......)` 的真实色值。 */
    private fun darkColor(name: String): Long {
        val m = Regex("private val ${name}Dark = Color\\(0x([0-9A-Fa-f]{8})\\)").find(dongfangTheme)
        assertTrue("DongfangTheme.kt 里找不到 ${name}Dark —— 颜色真相源已改名或改结构，守卫必须跟着更新",
            m != null)
        return m!!.groupValues[1].toLong(16)
    }

    private fun assertSameRole(role: String, actual: Int) {
        val expected = darkColor(role)
        assertEquals(
            "M3Tokens.$role 必须与 DongfangTheme.kt 的 ${role}Dark 完全一致（两半共用一个色板）",
            expected, actual.toLong() and 0xFFFFFFFFL,
        )
    }

    // ── 颜色：与 Compose 侧逐个角色对齐 ────────────────────────────────────

    @Test
    fun colorRoles_matchTheComposeSideExactly() {
        assertSameRole("primary", M3Tokens.PRIMARY)
        assertSameRole("onPrimary", M3Tokens.ON_PRIMARY)
        assertSameRole("primaryContainer", M3Tokens.PRIMARY_CONTAINER)
        assertSameRole("onPrimaryContainer", M3Tokens.ON_PRIMARY_CONTAINER)

        assertSameRole("secondary", M3Tokens.SECONDARY)
        assertSameRole("onSecondary", M3Tokens.ON_SECONDARY)
        assertSameRole("secondaryContainer", M3Tokens.SECONDARY_CONTAINER)
        assertSameRole("onSecondaryContainer", M3Tokens.ON_SECONDARY_CONTAINER)

        assertSameRole("tertiary", M3Tokens.TERTIARY)
        assertSameRole("onTertiary", M3Tokens.ON_TERTIARY)
        assertSameRole("tertiaryContainer", M3Tokens.TERTIARY_CONTAINER)
        assertSameRole("onTertiaryContainer", M3Tokens.ON_TERTIARY_CONTAINER)

        assertSameRole("error", M3Tokens.ERROR)
        assertSameRole("onError", M3Tokens.ON_ERROR)
        assertSameRole("errorContainer", M3Tokens.ERROR_CONTAINER)
        assertSameRole("onErrorContainer", M3Tokens.ON_ERROR_CONTAINER)

        assertSameRole("background", M3Tokens.BACKGROUND)
        assertSameRole("onBackground", M3Tokens.ON_BACKGROUND)
        assertSameRole("surface", M3Tokens.SURFACE)
        assertSameRole("onSurface", M3Tokens.ON_SURFACE)
        assertSameRole("surfaceVariant", M3Tokens.SURFACE_VARIANT)
        assertSameRole("onSurfaceVariant", M3Tokens.ON_SURFACE_VARIANT)
        assertSameRole("outline", M3Tokens.OUTLINE)
        assertSameRole("outlineVariant", M3Tokens.OUTLINE_VARIANT)
        assertSameRole("scrim", M3Tokens.SCRIM)
        assertSameRole("inverseSurface", M3Tokens.INVERSE_SURFACE)
        assertSameRole("inverseOnSurface", M3Tokens.INVERSE_ON_SURFACE)
        assertSameRole("inversePrimary", M3Tokens.INVERSE_PRIMARY)
    }

    @Test
    fun surfaceContainerLadder_matchesTheComposeSide() {
        // M3 用 surface 容器阶梯表达层级（纯黑下阴影看不见），这五档是最容易漏的。
        assertSameRole("surfaceDim", M3Tokens.SURFACE_DIM)
        assertSameRole("surfaceBright", M3Tokens.SURFACE_BRIGHT)
        assertSameRole("surfaceContainerLowest", M3Tokens.SURFACE_CONTAINER_LOWEST)
        assertSameRole("surfaceContainerLow", M3Tokens.SURFACE_CONTAINER_LOW)
        assertSameRole("surfaceContainer", M3Tokens.SURFACE_CONTAINER)
        assertSameRole("surfaceContainerHigh", M3Tokens.SURFACE_CONTAINER_HIGH)
        assertSameRole("surfaceContainerHighest", M3Tokens.SURFACE_CONTAINER_HIGHEST)
    }

    // ── 形状阶：官方反编译值 ──────────────────────────────────────────────

    @Test
    fun shapeScale_matchesTheOfficialMaterial3Tokens() {
        // 这组值是从我们 APK 里那版 material3 的 ShapeTokens 反编译得到的。
        // 写死在这里是为了防止有人"顺手调一下圆角"——那会让整套形状阶失去官方依据。
        assertEquals(0, M3Tokens.SHAPE_NONE)
        assertEquals(4, M3Tokens.SHAPE_EXTRA_SMALL)
        assertEquals(8, M3Tokens.SHAPE_SMALL)
        assertEquals(12, M3Tokens.SHAPE_MEDIUM)
        assertEquals(16, M3Tokens.SHAPE_LARGE)
        assertEquals(20, M3Tokens.SHAPE_LARGE_INCREASED)
        assertEquals(28, M3Tokens.SHAPE_EXTRA_LARGE)
        assertEquals(32, M3Tokens.SHAPE_EXTRA_LARGE_INCREASED)
        assertEquals(48, M3Tokens.SHAPE_EXTRA_EXTRA_LARGE)
    }

    // ── 字阶：官方 15 个 role ─────────────────────────────────────────────

    @Test
    fun typeScale_hasAllFifteenRoles_withOfficialSizes() {
        assertEquals("M3 的 type scale 恰好 15 个 role", 15, M3Tokens.TYPE_SCALE.size)

        // 抽查几档官方值（size/lineHeight/weight/tracking）。
        // tracking 用 **Android 侧 2025 值**：官方体系内部 Android 与官网 Web token 就不一致
        // （displayLarge −0.2 vs −0.25、titleMedium 0.2 vs 0.15、bodyMedium 0.2 vs 0.25），
        // 以 Android 为准——那是 APK 里跑的那一版。参考库 06/08 §3.1。
        assertEquals(57f, M3Tokens.DISPLAY_LARGE.sizeSp, 0.01f)
        assertEquals(64f, M3Tokens.DISPLAY_LARGE.lineHeightSp, 0.01f)
        assertEquals(16f, M3Tokens.BODY_LARGE.sizeSp, 0.01f)
        assertEquals(24f, M3Tokens.BODY_LARGE.lineHeightSp, 0.01f)
        assertEquals(14f, M3Tokens.LABEL_LARGE.sizeSp, 0.01f)
        assertEquals(11f, M3Tokens.LABEL_SMALL.sizeSp, 0.01f)
        assertEquals(-0.2f, M3Tokens.DISPLAY_LARGE.trackingEm, 0.001f)
        assertEquals(0.2f, M3Tokens.TITLE_MEDIUM.trackingEm, 0.001f)
        assertEquals(0.2f, M3Tokens.BODY_MEDIUM.trackingEm, 0.001f)
        // 字重只有 400/500 两档——M3 就是靠这两档 + 字号做层级，不像主机区现在有 5 档零散值。
        for (role in M3Tokens.TYPE_SCALE) {
            assertTrue("${role.name} 的字重应是 400 或 500（M3 官方只这两档）", role.weight == 400 || role.weight == 500)
            assertTrue("${role.name} 的行高必须 ≥ 字号", role.lineHeightSp >= role.sizeSp)
        }
    }

    @Test
    fun navBarHeight_isTheOfficialContainerHeight() {
        // 官方 NavigationBarTokens.ContainerHeight = 64dp；80 是 M3E 的 tall 变体。
        assertEquals(64, M3Tokens.NAV_BAR_HEIGHT)
    }

    @Test
    fun typeScale_namesAreUnique_andCoverAllFamilies() {
        val names = M3Tokens.TYPE_SCALE.map { it.name }
        assertEquals("role 名不得重复", names.size, names.toSet().size)
        for (family in listOf("Display", "Headline", "Title", "Body", "Label")) {
            assertEquals("$family 应有 Large/Medium/Small 三档",
                3, names.count { it.startsWith(family) })
        }
    }

    // ── 状态层与动效 ──────────────────────────────────────────────────────

    @Test
    fun stateLayerOpacities_areTheOfficialValues() {
        assertEquals(0.08f, M3Tokens.STATE_HOVER, 0.001f)
        assertEquals(0.10f, M3Tokens.STATE_FOCUS, 0.001f)
        assertEquals(0.10f, M3Tokens.STATE_PRESSED, 0.001f)
        assertEquals(0.16f, M3Tokens.STATE_DRAGGED, 0.001f)
        assertEquals(0.38f, M3Tokens.STATE_DISABLED_CONTENT, 0.001f)
    }

    @Test
    fun motionTokens_agreeWithTheProjectSpec() {
        // 与 docs/design/wear-ui-system.md §2.1 一致：入场 300 / 退场 200，standard 缓动 (0.2,0,0,1)。
        assertEquals(300L, M3Tokens.DURATION_MEDIUM_2)
        assertEquals(200L, M3Tokens.DURATION_SHORT_4)
        assertEquals(0.2f, M3Tokens.EASING_STANDARD[0], 0.001f)
        assertEquals(1f, M3Tokens.EASING_STANDARD[3], 0.001f)
        assertTrue("自检项：入场时长 ≥ 退场时长", M3Tokens.DURATION_MEDIUM_2 >= M3Tokens.DURATION_SHORT_4)
    }
}
