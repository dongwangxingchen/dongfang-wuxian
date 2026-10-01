package cc.nkbr.lanzouplus

import android.os.Looper
import org.junit.Assert.assertEquals
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
import java.util.regex.Pattern

/**
 * [DFW-38] 「软件库很多链接加载失败 / 加载太慢」的守卫。
 *
 * 只读调查（对 HEAD 逐行核对 + 用 App 的完整字段集实测上游）得到的关键结论：
 * **不是源坏了** —— 抽样 10 个内置源 10/10 成功（zt=1，0.49–1.65s），跨域名重写 5 个域名全部成功，
 * 服务端每页只要 0.22–1.33s。用户两张截图「50 项 / 100 项」正好是第 1/2 页（PAGE_SIZE=50）。
 *
 * 所以这一轮修的全是**客户端策略**：
 * ① 翻页硬地板 3000ms → 1100ms（客户端自造的 14 倍放大）；
 * ② 有缓存时不再转圈说「正在刷新目录」（SWR）；
 * ③ 刷新失败不再把能看的目录换成「解析失败」占位页（stale-if-error）；
 * ④ 诊断链把原始异常消息喂给解释函数，403/限流的专门解释才到得了用户眼前；
 * ⑤ 蓝奏域名白名单补上真实在用的 `lanzn.com`；
 * ⑥ 错误壳页判定不再扫条目名（软件合集里 `nginx-1.24.zip` 很常见，会整页误杀）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class FolderLoadPolicyJvmTest {

    companion object {
        @BeforeClass @JvmStatic fun silenceAutoImport() { MainActivity.LIBRARY_AUTO_IMPORT = false }

        private val repoRoot: File = run {
            var dir = File(System.getProperty("user.dir") ?: ".").absoluteFile
            while (!File(dir, "app/src/main/AndroidManifest.xml").isFile && dir.parentFile != null) dir = dir.parentFile!!
            dir
        }

        private fun src(rel: String) = File(repoRoot, rel).readText()

        /** 花括号配对取方法体，避免 contains 假绿。 */
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

        /**
         * 去掉注释再做断言。
         *
         * 这不是为了"让测试好过"，而是为了让断言**只对代码生效**：本轮的修复注释里
         * 天然会引用被删掉的旧写法（"旧代码在这里 loading("正在刷新目录")"），
         * 如果不断言前剥离注释，就会出现"注释里提一下旧代码就把测试判红"的假红。
         * 字符串字面量要保护，否则 URL 里的 `//` 会被当成行注释。
         */
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

        /** 读 LanzouCore 的私有静态常量。 */
        private fun coreInt(name: String): Int {
            val f = LanzouCore::class.java.getDeclaredField(name)
            f.isAccessible = true
            return f.getInt(null)
        }

        private fun lanzouHost(): Pattern {
            val f = LanzouCore::class.java.getDeclaredField("LANZOU_HOST")
            f.isAccessible = true
            return f.get(null) as Pattern
        }
    }

    private fun activity(): MainActivity {
        val a = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        shadowOf(Looper.getMainLooper()).idle()
        return a
    }

    // ── ① 翻页硬地板 ─────────────────────────────────────────────────────

    @Test fun pageFloor_noLongerAddsThreeSecondsOnTopOfTheOriginSlot() {
        val floor = coreInt("NEXT_PAGE_FLOOR_MS")
        val originSlot = coreInt("ORIGIN_PAGE_SLOT_MS")
        assertTrue(
            "翻页地板必须降下来：服务端实测每页 0.22–1.33s，旧值 3000ms 是客户端自造的 14 倍放大（实际=$floor）",
            floor <= 1500,
        )
        assertTrue(
            "但也不能归零 —— 对上游要有基本礼貌。应当与 origin 槽位同档（实际 floor=$floor slot=$originSlot）",
            floor >= originSlot,
        )
    }

    // ── ⑤ 蓝奏域名白名单 ─────────────────────────────────────────────────

    @Test fun lanznCom_isRecognisedAsALanzouDomain() {
        val host = lanzouHost()
        for (domain in listOf(
            "yoyodadada.lanzn.com", "www.lanzn.com", "lanzn.com",
            "oreojiang.lanzout.com", "wwc.lanzoux.com", "www.lanzouw.com", "www.lanzoup.com",
        )) {
            assertTrue("$domain 必须被认成蓝奏域名（内置源清单里 19/85 条用 lanzn.com）", host.matcher(domain).matches())
        }
        assertFalse("非蓝奏域名不许放进来", host.matcher("example.com").matches())
        assertFalse("非蓝奏域名不许放进来", host.matcher("lanzn.com.evil.net").matches())
    }

    // ── ⑥ 错误壳页判定 ───────────────────────────────────────────────────

    private fun folder(title: String, items: List<String>): Models.Folder {
        val f = Models.Folder()
        f.title = title
        for (name in items) {
            val item = Models.Item()
            item.title = name
            item.description = ""
            item.error = ""
            f.items.add(item)
        }
        return f
    }

    @Test fun aFolderWithItems_isNeverTreatedAsAnErrorShell() {
        val a = activity()
        // 软件合集里文件名带 nginx / 403 太常见了 —— 旧实现会把整页判成"解析失败"
        val healthy = folder("nginx 工具集", listOf("nginx-1.24.zip", "403绕过工具.apk", "cloudflare配置.rar"))
        assertFalse(
            "条目非空就说明拿到的是真目录，不许判成错误壳页（旧实现会误杀）",
            a.directoryErrorFolder(healthy),
        )
    }

    @Test fun aRealForbiddenShell_isStillDetected() {
        val a = activity()
        assertTrue("真 403 壳页（无条目 + 标题写明）仍要判出来", a.directoryErrorFolder(folder("403 Forbidden", emptyList())))
        assertTrue(a.directoryErrorFolder(folder("Service Unavailable", emptyList())))
        assertTrue(a.directoryErrorFolder(folder("Attention Required! | Cloudflare", emptyList())))
    }

    @Test fun anOrdinaryEmptyFolder_isNotAnError() {
        val a = activity()
        assertFalse("正常空目录不许判成错误", a.directoryErrorFolder(folder("百度网盘", emptyList())))
        assertFalse(a.directoryErrorFolder(folder("正在解析目录", emptyList())))
    }

    // ── ④ 诊断链 ─────────────────────────────────────────────────────────

    @Test fun forbiddenErrors_getTheirOwnExplanation() {
        val a = activity()
        val text = a.directoryParseFailureReason("403 Forbidden")
        assertTrue("403 必须有专门解释，而不是泛泛的『没有返回可用目录数据』：$text", text.contains("403"))
        assertFalse("不许落到最泛兜底", text.contains("可能是域名策略、临时风控、网络异常或分享状态变化"))
    }

    @Test fun rateLimitErrors_getTheirOwnExplanation() {
        val a = activity()
        val text = a.directoryParseFailureReason("429 too many requests")
        assertTrue("限流必须有专门解释：$text", text.contains("429") || text.contains("频率"))
    }

    @Test fun theCatchBlockFeedsTheRawMessageToTheExplainer_notTheFriendlyOne() {
        // 这是本轮的根因之一：friendlyError 会把 "403 Forbidden" 洗成"操作失败，请稍后重试"，
        // 而解释函数恰恰靠原始关键词判定 —— 于是专门解释永远到不了用户眼前。
        val body = methodBody(src("app/src/main/java/cc/nkbr/lanzouplus/MainActivity.java"), "void openFolder(Models.Source source,boolean refresh)")
        assertTrue("必须取原始异常消息", body.contains("error.getMessage()"))
        assertTrue("解释函数必须吃原始消息", body.contains("directoryParseFailureReason(raw)"))
        assertFalse("不许再拿 friendlyError 的结果去判定", body.contains("directoryParseFailureReason(message)"))
    }

    // ── ②③ SWR / stale-if-error ──────────────────────────────────────────

    @Test fun cachedOpen_doesNotKeepSpinningWhileRefreshingInTheBackground() {
        val body = methodBody(src("app/src/main/java/cc/nkbr/lanzouplus/MainActivity.java"), "void openFolder(Models.Source source,boolean refresh)")
        assertFalse(
            "有缓存时不许再说『正在刷新目录』—— 内容已经能看了，还转圈就是用户说的『慢』",
            body.contains("loading(\"正在刷新目录\")"),
        )
        assertTrue("有缓存时应当直接把进度条收起来", body.contains("progress.setVisibility(View.GONE)"))
    }

    @Test fun refreshFailure_keepsTheContentAlreadyOnScreen() {
        val body = methodBody(src("app/src/main/java/cc/nkbr/lanzouplus/MainActivity.java"), "void openFolder(Models.Source source,boolean refresh)")
        assertTrue(
            "stale-if-error：屏幕上已有内容时，刷新失败只提示、不把内容换成失败占位页",
            body.contains("if(rendered.get()){showNotice("),
        )
    }

    // ── 下拉/上拉：动画与文案 ────────────────────────────────────────────

    @Test fun pullRelease_usesTheAppWideCurve_notTheM2Decelerate() {
        val body = methodBody(src("app/src/main/java/cc/nkbr/lanzouplus/MainActivity.java"), "void animatePullOffsetTo(float value,boolean animated)")
        assertFalse("不许再用 M2 遗留的 DecelerateInterpolator", body.contains("DecelerateInterpolator"))
        assertTrue("曲线要与站内一致", body.contains("PathInterpolator(0.2f,0f,0f,1f)"))
        assertTrue("时长取 androidx SwipeRefreshLayout 的官方松手时序 200ms", body.contains("setDuration(200)"))
    }

    @Test fun theServerThrottleIsNoLongerShownAsACountdown() {
        val body = methodBody(src("app/src/main/java/cc/nkbr/lanzouplus/MainActivity.java"), "void updateMoreWaiting()")
        assertFalse("不许再给『剩余 N 秒』倒计时 —— 那是服务端限速，用户无法行动", body.contains("剩余 "))
        assertTrue("只说一句正在加载", body.contains("正在加载下一页"))
    }

    @Test fun statusLineRightLabel_isNotPaintedAsAnAction() {
        val body = methodBody(src("app/src/main/java/cc/nkbr/lanzouplus/MainActivity.java"), "void updateItemCount(int shown)")
        assertTrue("点不动的标签不许用强调色", body.contains("setTextColor(MUTED)"))
        assertTrue("本地还有余量时直接报数量（纯本地计算，不撒谎）", body.contains("还有 "))
    }

    // ── 加载环 ───────────────────────────────────────────────────────────

    @Test fun theFolderIndicator_noLongerUsesASystemProgressBar() {
        val body = methodBody(src("app/src/main/java/cc/nkbr/lanzouplus/MainActivity.java"), "FrameLayout draggableFolderList(")
        assertFalse("指示器不许再用系统 ProgressBar（形态/颜色/节奏都不受控）", body.contains("progressBarStyleSmall"))
        assertTrue("必须换成自绘环", body.contains("new DfwxLoadingRing("))
        assertFalse(
            "也不许再是全宽 SURFACE 通栏（和列表 BG 之间会出现明显的颜色断层）",
            body.contains("FrameLayout.LayoutParams(-1,dp(64),Gravity.BOTTOM)"),
        )
    }

    @Test fun theLoadingRing_hasOneArcAndTheOfficialRhythm() {
        val ring = src("app/src/main/java/cc/nkbr/lanzouplus/DfwxLoadingRing.java")
        assertEquals("一个 drawArc 调用点，杜绝双形态瞬切", 1, Regex("drawArc\\(").findAll(ring).count())
        assertTrue("周期取 M3E 官方 morph 节奏 650ms", ring.contains("SPIN_MS = 650L"))
        assertTrue("必须 attach 起 detach 停", ring.contains("onDetachedFromWindow"))
        assertTrue("关掉动效时要能停在静态弧上", ring.contains("setRingProgress"))
    }
}
