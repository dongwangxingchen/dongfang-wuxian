package cc.nkbr.lanzouplus

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * [DFW-35] 弹窗与外框底座的**一致性守卫**。
 *
 * ## 为什么单独有这条测试
 *
 * DFW-35 的盘点（`docs/research/dfw35-弹窗盘点.md`）把 63 个弹窗逐个过了一遍，
 * 结论是：**绝大多数都走 `roundDialog` 共享底座，只有 1 个自己写了一套。**
 *
 * 而「只有 1 个自己写了一套」正是最难发现的那种不一致 ——
 * 它不会崩、不会报错，只是**看着跟别的不一样**，所以能一直躺着没人发现。
 * 靠人眼在 63 个弹窗里找它是不可靠的，得让机器盯着。
 *
 * ## 两条被守住的规矩
 *
 * **① 弹窗宽度必须走共享底座的公式**：`min(dp(560), safeContentWidth() - dp(28))`。
 * 下载历史弹窗原来是 `min(dp(600), widthPixels - dp(28))`，两处都不一样：
 * 上限 600 vs 560（差 40dp），基准是**原始屏宽**而不是**扣掉 host 内边距后的可用宽度**。
 * 窄屏和分屏下它会比别的弹窗宽出去一截。
 *
 * **② 弹窗按钮必须走 `styleDialogButton`**。下载历史那个按钮手工写了 8 行，
 * 漏了 4 项：`setStateListAnimator(null)`（安卓默认的点击抬升动画没关）、
 * `applePressScale(b)`（全站统一的按压缩放没有）、`setGravity(CENTER)`（文字居中没设），
 * 字号 12 和内边距 dp(10) 也和底座的 13 / dp(12) 对不上。
 *
 * ## 测试禁区（本仓既有教训）
 * `MainActivity.java` 的导入路径会经 `addUserSourcesBatch` → `probeSingleSource`
 * **对 85 个源发真实网络请求**。本测试**只读源码文本、不实例化 Activity**，所以安全。
 */
class WebHistoryDialogConsistencyJvmTest {

    private val root: File = run {
        var dir = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (!File(dir, "AGENTS.md").isFile && dir.parentFile != null) dir = dir.parentFile
        dir
    }

    private fun main() =
        File(root, "app/src/main/java/cc/nkbr/lanzouplus/MainActivity.java").readText(Charsets.UTF_8)

    /** 按花括号配对取方法体。**不靠空行猜边界**（DFW-98 就是这么删错东西的）。 */
    private fun bodyOf(src: String, signature: String): String {
        val start = src.indexOf(signature)
        assertTrue("找不到方法：$signature（测试需要更新）", start >= 0)
        val open = src.indexOf('{', start)
        var depth = 0
        for (i in open until src.length) {
            when (src[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return src.substring(open, i + 1)
                }
            }
        }
        error("$signature 花括号不配对")
    }

    /* ───────────────────────── 判据（纯函数，便于反向探针） ───────────────────────── */

    /**
     * 这个弹窗体是不是走了共享底座的宽度公式？
     *
     * ⚠️ 判据只看**代码**，所以先把块注释剥掉 ——
     * 否则新写的注释里会提到旧值 `dp(600)`，把检查喂成假绿。
     * （本仓踩过同类的坑：`indexOf` 匹配到注释里的路径。）
     * 注意本文件里**不能出现块注释的结束标记字面量**，否则会把上面这段 KDoc 提前终结
     * —— 刚才就是这么编译失败的，用正则拼出来避开它。
     */
    private fun usesSharedDialogWidth(dialogBody: String): Boolean {
        val code = dialogBody.replace(blockComment, " ")
        return code.contains("safeContentWidth()") && !code.contains("widthPixels")
    }

    /** 块注释的正则。**用拼接写出来**，避免源码里出现结束标记的字面量把 KDoc 终结掉。 */
    private val blockComment = Regex("/\\*" + "[\\s\\S]*?" + "\\*/")

    /** 这个弹窗的按钮是不是走全站统一样式？ */
    private fun usesSharedButtonStyle(dialogBody: String): Boolean =
        dialogBody.replace(blockComment, " ").contains("styleDialogButton(")

    /* ───────────────────────── 断言 ───────────────────────── */

    @Test
    fun downloadHistoryDialogTakesItsWidthFromTheSharedBase() {
        val body = bodyOf(main(), "void showWebDownloadHistoryDialogNow(")
        assertTrue(
            "下载历史弹窗必须用 safeContentWidth() 算宽度（扣掉 host 内边距后的可用宽度），" +
                "而不是 getDisplayMetrics().widthPixels（原始屏宽）—— 后者在窄屏/分屏下会宽出去",
            usesSharedDialogWidth(body),
        )
    }

    @Test
    fun downloadHistoryDialogButtonsGoThroughTheSharedStyle() {
        val body = bodyOf(main(), "void showWebDownloadHistoryDialogNow(")
        assertTrue(
            "下载历史弹窗的按钮必须走 styleDialogButton —— 手工写的版本漏掉了" +
                "关闭点击抬升动画、按压缩放、文字居中三项，字号和内边距也和全站对不上",
            usesSharedButtonStyle(body),
        )
    }

    /**
     * 全仓守卫：**不许有第二个弹窗自己算宽度。**
     *
     * `dp(600)` 在别处是合法的（`:1008 itemColumns` 的网格列数、`:1048 wideNavigation` 的宽屏阈值），
     * 所以这里不去禁那个值，只禁「拿原始屏宽当弹窗宽度」这个**写法**。
     */
    @Test
    fun noDialogSizesItselfFromTheRawScreenWidth() {
        val src = main().replace(Regex("/\\*[\\s\\S]*?\\*/"), " ")
        assertFalse(
            "不许有弹窗拿 getDisplayMetrics().widthPixels 当窗口宽度 —— " +
                "所有弹窗宽度都必须来自 roundDialog 的共享公式",
            Regex("p\\.width\\s*=\\s*[^;]*widthPixels").containsMatchIn(src),
        )
    }

    /* ───────────────────────── 鉴别力（反向探针的正面） ───────────────────────── */

    /**
     * 喂坏代码必须被判成坏 —— 否则上面三条全是假绿。
     *
     * 这里用的是**改动前的原文**，逐字取自 `git show` 的历史版本。
     */
    @Test
    fun theChecksActuallyRejectTheOldCode() {
        val oldWidth = """p.width=Math.min(dp(600),host.getResources().getDisplayMetrics().widthPixels-dp(28));"""
        assertFalse(
            "改动前那行（dp(600) + 原始屏宽）必须被判成不合格",
            usesSharedDialogWidth(oldWidth),
        )

        val oldButton = """Button closeOrBack=new Button(host);closeOrBack.setTextColor(PRIMARY);""" +
            """closeOrBack.setTextSize(12);closeOrBack.setAllCaps(false);closeOrBack.setMinWidth(0);""" +
            """closeOrBack.setMinimumWidth(0);closeOrBack.setMinHeight(dp(44));""" +
            """closeOrBack.setMinimumHeight(dp(44));closeOrBack.setPadding(dp(10),0,dp(10),0);""" +
            """closeOrBack.setBackground(filterRipple(new ColorDrawable(Color.TRANSPARENT)));"""
        assertFalse(
            "改动前那串手工按钮样式必须被判成没走统一函数",
            usesSharedButtonStyle(oldButton),
        )

        val fixed = """Button closeOrBack=new Button(host);styleDialogButton(closeOrBack,true);"""
        assertTrue("改好的写法必须被认出来", usesSharedButtonStyle(fixed))

        // 注释里提到旧值不许把检查喂成假绿
        val commented = """p.width=Math.max(dp(1),Math.min(dp(560),safeContentWidth()-dp(28)));""" +
            """/* 原来是 dp(600) + widthPixels */"""
        assertTrue(
            "注释里提到旧值不该影响判断（判据必须先剥掉块注释）",
            usesSharedDialogWidth(commented),
        )
    }
}
