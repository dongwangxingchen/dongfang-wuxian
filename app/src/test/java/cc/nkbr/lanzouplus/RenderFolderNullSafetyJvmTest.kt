package cc.nkbr.lanzouplus

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * [DFW-99] `renderFolder` 的空指针守卫 —— 用户真机崩溃的直接原因。
 *
 * ## 真机崩溃（用户 2026-10-01 提交的崩溃日志）
 * ```
 * NullPointerException: Attempt to invoke virtual method
 *   'void android.view.View.setVisibility(int)' on a null object reference
 *   at cc.nkbr.lanzouplus.MainActivity.renderFolder
 * ```
 * 同一个崩溃在 v1.0.9 和 v1.0.10 上**反复发生**（日志里连着两条）。
 *
 * ## 根因
 * `progress` 这个字段在界面重置时**被显式置为 null**（`resetUi()` 一类的地方），
 * 但 `renderFolder` 里对它的调用**有的判了 null、有的没判** ——
 * 同一段代码里上一行有保护、下一行没有：
 * ```
 * if(progress!=null)progress.setVisibility(View.GONE);return;   // 有保护
 * progress.setVisibility(View.GONE);                            // 没保护 ← 崩在这里
 * ```
 * 这种"一半保护一半不保护"是最容易漏的：写的时候以为都判过了。
 *
 * ## 这条测试守什么
 * `renderFolder` 里**任何** `view.setVisibility(...)` 调用，
 * 在**同一条语句内**必须先出现 `<view>!=null` 的判空。
 * 新增调用忘了判空就会红。
 */
class RenderFolderNullSafetyJvmTest {

    private val root: File = run {
        var dir = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (!File(dir, "AGENTS.md").isFile && dir.parentFile != null) dir = dir.parentFile
        dir
    }

    private fun main() = File(root, "app/src/main/java/cc/nkbr/lanzouplus/MainActivity.java").readText(Charsets.UTF_8)

    /** 取出 `void renderFolder(...)` 这个方法体（按花括号配对，不靠空行 —— 踩过坑）。 */
    private fun renderFolderBody(src: String): String {
        val start = src.indexOf("  void renderFolder(Models.Folder f){")
        assertTrue("找不到 renderFolder 方法，测试需要更新", start >= 0)
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
        error("renderFolder 花括号不配对")
    }

    /**
     * ⚠️ **必须只看当前这一条语句**，不能看整个方法体。
     *
     * 第一版写成"匹配点之前的整个方法体里有没有 `<view>!=null`" —— 结果是**假绿**：
     * 方法体前面已经有别的 `if(progress!=null)progress.setVisibility(...)`，
     * 于是后面那条**没判空的**调用也被误判成"已保护"。
     * 反向探针（把保护去掉，看守卫会不会红）当场抓出了这个假绿 ——
     * **反向探针不是形式，它救过一次。**
     *
     * 现在按语句切分：取匹配点往前最近的 `;` `{` `}` 之后的那一段，只在这一段里找判空。
     */
    private fun unguardedCalls(body: String): List<String> =
        Regex("([A-Za-z_][A-Za-z0-9_]*)\\.setVisibility\\(").findAll(body)
            .filter { m ->
                val view = m.groupValues[1]
                val from = body.substring(0, m.range.first)
                    .lastIndexOfAny(charArrayOf(';', '{', '}'))
                val statement = body.substring(if (from < 0) 0 else from + 1, m.range.first)
                !statement.replace(" ", "").contains("$view!=null")
            }
            .map { it.groupValues[1] }
            .distinct()
            .toList()

    @Test
    fun renderFolder_neverCallsSetVisibilityOnAPossiblyNullView() {
        val offenders = unguardedCalls(renderFolderBody(main()))
        assertTrue(
            "renderFolder 里这些 View 没判空就调 setVisibility，会 NPE 崩溃（真机已复现）：$offenders",
            offenders.isEmpty(),
        )
    }

    /** 反向探针：确认这条守卫**真的能红** —— 喂一段没判空的代码必须被抓出来。 */
    @Test
    fun theGuardActuallyDetectsAnUnguardedCall() {
        val bad = "{ progress.setVisibility(0); }"
        assertTrue("守卫必须能抓出没判空的调用（否则就是假绿）", unguardedCalls(bad).contains("progress"))
        val good = "{ if(progress!=null)progress.setVisibility(0); }"
        assertTrue("判过空的不该被抓", unguardedCalls(good).isEmpty())
        // ⚠️ 这条是专门针对第一版假绿写的回归：前面判过空，**不代表后面那条也判了**
        val mixed = "{ if(progress!=null)progress.setVisibility(0);progress.setVisibility(0); }"
        assertTrue(
            "前面判过空不能给后面那条背书 —— 第一版守卫就是栽在这里（假绿）",
            unguardedCalls(mixed).contains("progress"),
        )
    }

    /** 花括号配对取方法体这件事本身也要自检，否则取错范围 = 假绿。 */
    @Test
    fun theBodyExtractionActuallyGrabsTheMethod() {
        val body = renderFolderBody(main())
        assertTrue("取到的应该是方法体而不是空串", body.length > 200)
        assertTrue("取到的范围里应该含有该方法的特征代码", body.contains("activeFolderProfile"))
    }
}
