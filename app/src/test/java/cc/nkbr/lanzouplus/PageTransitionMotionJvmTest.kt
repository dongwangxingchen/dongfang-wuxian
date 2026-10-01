package cc.nkbr.lanzouplus

import android.os.Looper
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

/**
 * [DFW-79] 软件库进出动画：关掉"预判返回"的预览，并把"渐隐"改成纯位移。
 *
 * 用户 2026-10-01：
 * > "打开软件库后，再退出某个蓝奏链接后……我不希望有预动画。就是说这个我就不需要有那些
 * >  提前预判我返回的动画了，因为看起来很难看很卡。"
 * > "我有点不太喜欢那种动画效果了，就是那种有点渐隐式的……每次打开或关闭的时候，
 * >  总感觉不够丝滑。我希望你给它改成很有效率感、很顺畅、回馈感不错的那种动画效果。"
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class PageTransitionMotionJvmTest {

    companion object {
        @BeforeClass @JvmStatic fun silenceAutoImport() { MainActivity.LIBRARY_AUTO_IMPORT = false }

        private val repoRoot: File = run {
            var dir = File(System.getProperty("user.dir") ?: ".").absoluteFile
            while (!File(dir, "app/src/main/AndroidManifest.xml").isFile && dir.parentFile != null) dir = dir.parentFile!!
            dir
        }

        private fun methodBody(source: String, signature: String): String {
            val at = source.indexOf(signature)
            assertTrue("源码里找不到 $signature", at > 0)
            val open = source.indexOf('{', at)
            var depth = 0
            for (i in open until source.length) {
                when (source[i]) {
                    '{' -> depth++
                    '}' -> { depth--; if (depth == 0) return stripComments(source.substring(open, i + 1)) }
                }
            }
            throw AssertionError("花括号不配对：$signature")
        }

        /** 断言只对代码生效，不被修复注释里引用的旧写法误伤。 */
        private fun stripComments(code: String): String {
            val out = StringBuilder(code.length)
            var i = 0
            while (i < code.length) {
                val c = code[i]
                if (c == '"' || c == '\'') {
                    out.append(c); i++
                    while (i < code.length) {
                        val d = code[i]; out.append(d)
                        if (d == '\\') { if (i + 1 < code.length) { out.append(code[i + 1]); i++ } }
                        else if (d == c) break
                        i++
                    }
                    i++
                } else if (c == '/' && i + 1 < code.length && code[i + 1] == '/') {
                    while (i < code.length && code[i] != '\n') i++
                } else if (c == '/' && i + 1 < code.length && code[i + 1] == '*') {
                    i += 2
                    while (i + 1 < code.length && !(code[i] == '*' && code[i + 1] == '/')) i++
                    i += 2
                } else { out.append(c); i++ }
            }
            return out.toString()
        }

        private fun mainSource() = File(repoRoot, "app/src/main/java/cc/nkbr/lanzouplus/MainActivity.java").readText()

        /**
         * 取 `animatePage` 的**原始**源码（不剥注释）。
         *
         * 为什么不用剥注释的版本：这个方法里注释很长，剥离器一旦在某个字符上判断错就会
         * 悄悄吞掉一大段代码 —— 那样 `assertFalse(...)` 会**假绿**（什么都没断言到）。
         * 所以这里改成原始切片 + 只挑**不可能出现在注释里**的 needle（带 `next.` / `previous.` 前缀的调用）。
         */
        private fun animatePageRaw(): String {
            val raw = mainSource()
            val at = raw.indexOf("void animatePage(View previous,View next,int direction)")
            assertTrue("找不到 animatePage", at > 0)
            val next = raw.indexOf("\n  void ", at + 10)
            return raw.substring(at, if (next > at) next else raw.length)
        }
    }

    // ── 预判返回：必须关掉 ───────────────────────────────────────────────

    @Test fun backGesture_doesNotPreAnimateThePage() {
        val a = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(
            "用户明确不要『提前预判返回』的动画：侧滑过程中页面必须完全不动，只有真正返回时才走转场",
            a.predictiveBackPreviewAllowed(),
        )
    }

    @Test fun thePreviewSwitchIsKept_soItCanBeTurnedBackOn() {
        // 不删代码、只关开关：用户之前要求开过（v1.22.1），随时可能再要。
        val raw = mainSource()
        val body = methodBody(raw, "boolean predictiveBackPreviewAllowed()")
        assertTrue("必须是显式返回 false 的开关，而不是把整段预览代码删掉", body.contains("return false"))
        // 注释在 methodBody 里被剥掉了，所以这里查原始源码，确认关掉的理由被写下来了
        val at = raw.indexOf("boolean predictiveBackPreviewAllowed()")
        val region = raw.substring((at - 1400).coerceAtLeast(0), at)
        assertTrue("注释要写清这是用户最新意见覆盖了历史要求", region.contains("DFW-79"))
    }

    // ── 转场：不许再有 alpha ─────────────────────────────────────────────

    @Test fun pushAndPop_arePureMotion_noFadeAnywhere() {
        val pushPop = animatePageRaw().substringAfter("int travel=")
        assertTrue("切片必须真的取到推入/返回那一段（否则下面的 assertFalse 是假绿）", pushPop.length > 200)
        assertFalse(
            "推入/返回不许再碰 alpha：OLED 纯黑底上『淡出』观感就是『变暗糊掉』，用户说的『不够丝滑』就是它",
            pushPop.contains(".alpha("),
        )
        assertFalse("也不许再缩放下层页（0.94 那档像整页往里塌）", pushPop.contains("scaleX(0.94f)"))
    }

    @Test fun pushAndPop_areParallaxSlides_bothPagesMove() {
        val pushPop = animatePageRaw().substringAfter("int travel=")
        assertTrue("新页/上层页要整屏位移", pushPop.contains("next.animate().translationX(0f)"))
        assertTrue("旧页要向左让出视差", pushPop.contains("previous.animate().translationX(-parallax)"))
        assertTrue("返回时下层页从 -parallax 归位", pushPop.contains("next.setTranslationX(-parallax)"))
        assertTrue("返回时上层页整屏滑走", pushPop.contains("previous.animate().translationX(travel)"))
    }

    @Test fun durations_areSnappy_andExitNeverOutlastsEntry() {
        val body = animatePageRaw()
        assertTrue("推入 280ms", body.contains(".setDuration(280)"))
        assertTrue("返回 240ms", body.contains(".setDuration(240)"))
        assertFalse("推入不许再回到 300ms", body.contains(".setDuration(300)"))
    }

    @Test fun fadeThrough_isKeptForTabSwitches_butLessMuddy() {
        val fade = animatePageRaw().substringBefore("int travel=")
        assertTrue("切片必须真的取到 fadeThrough 那一段", fade.contains("if(direction==0)"))
        assertTrue("切 tab 之间没有空间关系，淡入淡出语义正确、必须保留", fade.contains("next.setAlpha(0f)"))
        assertFalse("但 0.92 那档缩放太『缩』，要收到 0.96", fade.contains("next.setScaleX(0.92f)"))
        assertTrue("新档是 0.96", fade.contains("next.setScaleX(0.96f)"))
    }
}
