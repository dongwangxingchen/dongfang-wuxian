package cc.nkbr.lanzouplus

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.time.Duration

/**
 * [DFWX-UI-TOKEN] 任务 B 守卫：**付费页收口**（付费页自成一区、与站内不一致的五条）。
 *
 * 审计出的五条（行号对 HEAD `69bb1e3`）：
 * 1. 本地 `solidShape(int,int)` **同名但行为不同**于站内 `MainActivity.solidShape`（站内把半径量化到
 *    26/20/8 三档，这里不量化）；
 * 2. 本地 `ripple()` 拿 `BORDER`（不透明灰）当涟漪色 —— 规范 §3 要求主反馈是**按压 scale + 主色**，
 *    **不用灰 ripple 当主反馈**；
 * 3. 全页**没有** `applePressScale` 同规格的按压反馈；
 * 4. `playUnlockAnimation` **360ms**，破规范 §2.1「动效 VPA ≤300ms」铁律（历史事故复盘定的红线）；
 * 5. 同一页两种卡片圆角：普通卡 20 vs 收款码卡 16。
 *
 * **红线（有测试守，不许改坏）**：不付费也能完整使用；本页任何位置都要保留"暂时不支持"退出路径。
 * 所以本文件除了守上面五条，还额外守一条：**新加的按压反馈不许把点击吞掉**。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class SupportPageFeedbackJvmTest {

    @Before
    fun motionOn() {
        // 系统动画开关必须显式打开：本页的按压/回弹都走 motionEnabled() 门控，
        // 门控关掉时"没有动画"是正确行为，那样测不出任何东西。
        Settings.Global.putFloat(
            RuntimeEnvironment.getApplication().contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        )
    }

    private fun activity(): SupportActivity {
        val a = Robolectric.buildActivity(SupportActivity::class.java).setup().get()
        shadowOf(Looper.getMainLooper()).idle()
        return a
    }

    private fun find(view: View, match: (View) -> Boolean): View? {
        if (match(view)) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) find(view.getChildAt(i), match)?.let { return it }
        return null
    }

    private fun backButton(a: SupportActivity): View =
        find(a.root) { it is ImageButton && it.contentDescription?.toString() == "返回" }
            ?: error("付费页必须有左上角「返回」")

    private fun confirmButton(a: SupportActivity): Button =
        find(a.root) { it is Button && it.text?.toString()?.contains("诚信付费") == true } as Button

    private fun skipLink(a: SupportActivity): TextView =
        find(a.root) { it is TextView && it.text?.toString() == "暂时不支持，继续使用" } as TextView

    /** 真派发一次触摸事件（走 View.dispatchTouchEvent，与真机同一条路径）。 */
    private fun touch(v: View, action: Int) {
        val now = SystemClock.uptimeMillis()
        val event = MotionEvent.obtain(now, now, action, 1f, 1f, 0)
        v.dispatchTouchEvent(event)
        event.recycle()
    }

    private fun source(path: String): String {
        var root = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (!File(root, "app/src/main/AndroidManifest.xml").isFile && root.parentFile != null) root = root.parentFile
        return String(Files.readAllBytes(File(root, path).toPath()), StandardCharsets.UTF_8)
    }

    // ── ① 圆角量化：抄站内同一套四档规则 ──────────────────────────────────

    @Test
    fun solidShape_quantisesRadiusToTheSameFourTiersAsTheRestOfTheApp() {
        val a = activity()
        // 站内规则（MainActivity.solidShape）：>=24→26(Panel) / 14–23→20(Card) / 5–13→8(Micro) / <5 原样
        val cases = mapOf(
            0 to 0, 3 to 3, 4 to 4,
            5 to 8, 8 to 8, 13 to 8,
            14 to 20, 16 to 20, 20 to 20, 23 to 20,
            24 to 26, 26 to 26, 30 to 26,
        )
        for ((requested, expected) in cases) {
            val actual = a.solidShape(Color.BLACK, requested).cornerRadius
            assertEquals(
                "半径 $requested 应被量化到 $expected 档（站内同一套四档规则）",
                a.dp(expected).toFloat(),
                actual,
                0.01f,
            )
        }
    }

    @Test
    fun solidShape_ruleAndItsProvenanceAreWrittenDownInTheSource() {
        // 那个规则在**另一个窗口正在改的文件**里，所以这里要求本页把"抄的是哪条规则"写在注释里，
        // 并逐字钉住量化表达式 —— 以后站内改了，这里能立刻看出来两边是不是还一致。
        val src = source("app/src/main/java/cc/nkbr/lanzouplus/SupportActivity.java")
        assertTrue(
            "量化表达式必须与站内一致（>=24→26 / 14–23→20 / 5–13→8 / <5 原样）",
            src.contains("radius>=24?26:radius>=14?20:radius>=5?8:radius"),
        )
        assertTrue("必须在注释里写明这条规则抄自哪里（那个文件在动）", src.contains("MainActivity.solidShape"))
        assertTrue("必须写明抄的是哪个版本的文件", src.contains("MainActivity.java:599"))
    }

    // ── ⑤ 同页卡片圆角必须只有一个值 ─────────────────────────────────────

    @Test
    fun everyCardOnThePageSharesOneCornerRadius_theCardToken() {
        val a = activity()
        val radii = mutableListOf<Float>()
        fun walk(v: View) {
            if (v is ViewGroup && v.background is GradientDrawable) {
                radii.add((v.background as GradientDrawable).cornerRadius)
            }
            if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
        }
        walk(a.root)
        assertTrue("支持页应有 信/权益/价格/收款码 四张卡，实际 ${radii.size}", radii.size >= 4)
        assertEquals(
            "同一页不许有两种卡片圆角（原缺陷：普通卡 20 / 收款码卡 16）。实测各卡半径：$radii",
            1,
            radii.distinct().size,
        )
        assertEquals(
            "卡片圆角必须收敛到 Card token 20dp（规范 §4：信息卡片 radius 20dp 唯一值）",
            a.dp(20).toFloat(),
            radii.first(),
            0.01f,
        )
    }

    @Test
    fun theCodeCardItselfIsAtTheCardToken_notSixteen() {
        // 反向锁定具体那张卡：靠码图的 contentDescription 找它，避免上面那条"全都一样"被
        // 有人把四张卡一起改成别的档位后仍然通过。
        val a = activity()
        val code = find(a.root) {
            it is android.widget.ImageView && (it.contentDescription?.toString() ?: "").contains("微信收款码")
        }
        assertNotNull("收款码图必须还在", code)
        var card: View? = code!!.parent as View?
        while (card != null && !(card is ViewGroup && card.background is GradientDrawable)) card = card.parent as View?
        assertNotNull("收款码必须有卡片底色", card)
        assertEquals(
            "收款码卡圆角必须是 Card token 20dp（原来 16，与同页其它卡不一致）",
            a.dp(20).toFloat(),
            (card!!.background as GradientDrawable).cornerRadius,
            0.01f,
        )
    }

    @Test
    fun codeCardCallSiteStatesTheCardTokenInTheSource() {
        // 渲染结果已经由 solidShape 的量化保证（写 16 也会被量化成 20），所以这条守的是**可读性**：
        // 调用点必须直接写 Card token，别让后来人看到一个 16 还得去猜它最终渲染成几档。
        val src = source("app/src/main/java/cc/nkbr/lanzouplus/SupportActivity.java")
        assertTrue(
            "收款码卡的圆角必须显式写成 Card token 20dp（规范 §4 唯一值）",
            src.contains("solidShape(Color.WHITE,20)"),
        )
        assertFalse("收款码卡不许再写 16（同页两档圆角的来源）", src.contains("solidShape(Color.WHITE,16)"))
    }

    // ── ② 涟漪降级为辅助：不再是"灰罩"，改用主色淡染 ─────────────────────

    /** 反射读 `RippleDrawable` 的涟漪色（框架没有公开 getter）。 */
    private fun rippleColorOf(ripple: RippleDrawable): Int? {
        fun colorStateListIn(holder: Any?, depth: Int): android.content.res.ColorStateList? {
            if (holder == null || depth < 0) return null
            if (holder is android.content.res.ColorStateList) return holder
            for (field in holder.javaClass.declaredFields) {
                field.isAccessible = true
                val value = try {
                    field.get(holder)
                } catch (ignored: Throwable) {
                    null
                }
                if (value is android.content.res.ColorStateList) return value
                if (depth > 0 && value != null && value.javaClass.name.startsWith("android.graphics.drawable")) {
                    colorStateListIn(value, depth - 1)?.let { return it }
                }
            }
            return null
        }
        return colorStateListIn(ripple, 2)?.defaultColor
    }

    @Test
    fun ripple_isAuxiliaryAndPrimaryTinted_notTheOpaqueBorderGrey() {
        val a = activity()
        val skip = skipLink(a)
        val ripple = skip.background as RippleDrawable
        val color = rippleColorOf(ripple)
        assertNotNull("读不到涟漪色（RippleDrawable 结构变了），这条守卫需要跟着更新", color)
        assertEquals(
            "涟漪色必须是主色 16% 淡染（与站内 filterRipple 的 ThemeEngine.tint(PRIMARY,42) 同值）",
            ThemeEngine.tint(a.PRIMARY, 42),
            color,
        )
        assertFalse(
            "涟漪色不许再是 BORDER（不透明灰）—— 规范 §3：不用灰 ripple 当主反馈",
            color == a.BORDER,
        )
    }

    @Test
    fun ripple_noLongerUsesBorderAsItsColor_inTheSourceEither() {
        // 反射那条读的是"渲染出来的结果"；这条读源码，防止有人用别的写法把灰罩加回来。
        val src = source("app/src/main/java/cc/nkbr/lanzouplus/SupportActivity.java")
        assertTrue(
            "ripple() 必须改用主色淡染 ThemeEngine.tint(PRIMARY,42)",
            src.contains("ColorStateList.valueOf(ThemeEngine.tint(PRIMARY,42))"),
        )
        assertFalse(
            "ripple() 不许再拿 BORDER 当涟漪色",
            src.contains("ColorStateList.valueOf(BORDER)"),
        )
    }

    // ── ③ 按压反馈：与站内同规格（按下 0.96 / 松手 220ms 回弹）────────────

    @Test
    fun everyClickableControlCarriesPressFeedback() {
        // 行为断言而不是读 `getOnTouchListener()`（那是 @hide，SDK 存根里没有、android-all 里也不一定有）：
        // "按下即形变"本身就是"这个控件确实挂了按压反馈"的证据。
        val a = activity()
        for ((name, v) in listOf(
            "返回箭头" to backButton(a),
            "主 CTA" to confirmButton(a),
            "暂时不支持（降级出口）" to skipLink(a),
        )) {
            assertEquals("$name 初始不该是缩小的", 1f, v.scaleX, 0.001f)
            touch(v, MotionEvent.ACTION_DOWN)
            assertEquals(
                "$name 必须有按压反馈（原来全页只有一层灰 ripple）：按下应缩到 0.96",
                0.96f,
                v.scaleX,
                0.001f,
            )
            assertEquals("$name 两个方向都要缩", 0.96f, v.scaleY, 0.001f)
        }
    }

    @Test
    fun pressRelease_animatesBackToFullSizeWithinTheThreeHundredMsRedLine() {
        val a = activity()
        val back = backButton(a)
        // 清掉点击副作用（点返回会关页），只留触摸监听——本用例测的是回弹本身。
        back.setOnClickListener(null)
        touch(back, MotionEvent.ACTION_DOWN)
        assertEquals(0.96f, back.scaleX, 0.001f)
        touch(back, MotionEvent.ACTION_UP)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300))
        assertEquals("松手必须回到 1.0", 1f, back.scaleX, 0.001f)
        assertEquals("松手必须回到 1.0", 1f, back.scaleY, 0.001f)
    }

    @Test
    fun pressCancel_alsoReturnsToFullSize() {
        val a = activity()
        val skip = skipLink(a)
        touch(skip, MotionEvent.ACTION_DOWN)
        assertEquals(0.96f, skip.scaleX, 0.001f)
        touch(skip, MotionEvent.ACTION_CANCEL)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300))
        assertEquals("取消手势也必须回位（否则控件会卡在缩小状态）", 1f, skip.scaleX, 0.001f)
    }

    @Test
    fun pressFeedback_doesNotSwallowTheClick() {
        // **红线守卫**：加了 OnTouchListener 之后，"暂时不支持"这条唯一的降级出口必须还能走。
        // 触摸监听返回 false 才能让点击继续传给 OnClickListener。
        val a = activity()
        val skip = skipLink(a)
        touch(skip, MotionEvent.ACTION_DOWN)
        touch(skip, MotionEvent.ACTION_UP)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue("加了按压反馈之后，点「暂时不支持」必须仍然能退出", a.isFinishing)
    }

    @Test
    fun pressFeedbackConstants_matchTheSiteWideRecipe() {
        assertTrue("松手回弹时长必须 ≤300ms（规范 §2.1 动效 VPA 红线）", SupportActivity.REBOUND_MS <= 300)
        assertEquals("松手回弹时长与站内那条 220ms 配方一致", 220L, SupportActivity.REBOUND_MS)
        assertEquals("按下缩放用站内统一的 0.96（规范 §3）", 0.96f, SupportActivity.PRESS_SCALE, 0.001f)
    }

    // ── ④ 解锁动效 ≤300ms（红线）─────────────────────────────────────────

    @Test
    fun unlockAnimation_finishesWellInsideTheThreeHundredMsRedLine() {
        val a = activity()
        a.renderThankYou()
        val heart = find(a.root) { it is TextView && it.text?.toString() == "❤" }
        assertNotNull("感谢页的心形必须还在", heart)
        assertEquals("解锁动效必须真的起跑（起手 0.6 缩放）", 0.6f, heart!!.scaleX, 0.001f)
        assertEquals("解锁动效必须真的起跑（起手全透明）", 0f, heart.alpha, 0.001f)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(260))
        assertEquals(
            "260ms 时必须已经落位 —— 原 360ms 破规范 §2.1「动效 VPA ≤300ms」铁律",
            1f,
            heart.scaleX,
            0.001f,
        )
        assertEquals("260ms 时必须已经完全不透明", 1f, heart.alpha, 0.001f)
    }

    @Test
    fun unlockAnimation_noLongerUsesTheIllegalThreeSixty() {
        val src = source("app/src/main/java/cc/nkbr/lanzouplus/SupportActivity.java")
        assertFalse("360ms 不得回来（>300ms 是历史事故复盘定的红线）", src.contains("setDuration(360)"))
        assertTrue("必须走那个 ≤300ms 的常量", src.contains(".setDuration(REBOUND_MS)"))
    }

    // ── 红线：降级出口本身 ───────────────────────────────────────────────

    @Test
    fun escapeHatch_stillExistsVisibleAndDescribed() {
        val a = activity()
        val skip = skipLink(a)
        assertTrue("必须可点", skip.isClickable)
        assertTrue("必须可聚焦（无障碍）", skip.isFocusable)
        assertNotNull("必须有按压反馈底色", skip.background)
        assertTrue(
            "必须能读出来它是干什么的",
            skip.contentDescription?.toString()?.contains("暂时不支持") == true,
        )
    }

    @Test
    fun backControl_isAnIconButton_notABareColourDrawable() {
        // 附带守卫：页头返回必须是统一语义的图标按钮，且带按压反馈底色。
        val a = activity()
        val back = backButton(a)
        assertNotNull(back.background)
        assertTrue("返回控件必须走主色（ic_back + PRIMARY 着色）", back is ImageButton)
    }

    @Test
    fun pressTint_isTheSiteRecipe_notSomethingInvented() {
        // 站内 v1.21.0 配方：白 8% SRC_ATOP（MainActivity.applePressScale）。
        // 这里跟随站内，保证"按下去的反应"两页一致。
        assertEquals(0x14FFFFFF, SupportActivity.PRESS_TINT)
    }

    @Test
    fun pressScale_ignoresNullInsteadOfCrashing() {
        val a = activity()
        a.pressScale(null)
    }

    @Test
    fun unusedColourDrawableImportStillCompilesWithTheRippleChange() {
        // 纯结构守卫：ripple() 仍然接受任意 content drawable（透明底也允许，SRC_ATOP 不会加灰罩）。
        val a = activity()
        assertNotNull(a.ripple(ColorDrawable(Color.TRANSPARENT)) as Drawable)
    }
}
