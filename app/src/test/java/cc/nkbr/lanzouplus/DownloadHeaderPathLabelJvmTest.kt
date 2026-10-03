package cc.nkbr.lanzouplus

import android.os.Looper
import android.text.TextUtils
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
 * [2026-10-03 用户要求] 下载页页头的「保存路径」文字标签。
 *
 * ## 为什么删了又要加回来
 * 上一轮我把它删了，理由是"文件夹图标已经表达了含义"。
 * **用户看到截图后说：加回来，优化好。**
 *
 * 我复盘了一下，删它是对的、但理由不完整：
 * 没有标签时那串 `…al-files/Download/东方无限` 在用户眼里是**一串来路不明的字符**；
 * 有标签才知道它是什么。**图标的含义是设计者眼里的，标签的含义是用户眼里的。**
 *
 * ## 加回来为什么不会把路径挤没
 * 路径文字是 **START 截断**（`TruncateAt.START`），被吃掉的是**开头**，
 * 也就是 `storage/emulated/0/Download` 这种"不看也知道"的固定前缀；
 * 而用户真正要看的**结尾目录名**（「东方无限」）永远在。
 * 所以"标签占掉几十 dp"换来的是"这串字有了名字"，代价方向是对的。
 *
 * ⚠️ **本文件不断言像素宽度**：Robolectric 的文字测量是假的
 * （实测标题「下载历史」被量成 18px），拿它断宽度只会写出假绿。
 * 这里只守**结构性事实**，宽度交给真机。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-560dpi")
class DownloadHeaderPathLabelJvmTest {

    companion object {
        @BeforeClass @JvmStatic fun silenceAutoImport() { MainActivity.LIBRARY_AUTO_IMPORT = false }

        private const val LABEL = "保存路径"

        private val repoRoot: File = run {
            var dir = File(System.getProperty("user.dir") ?: ".").absoluteFile
            while (!File(dir, "app/src/main/AndroidManifest.xml").isFile && dir.parentFile != null) dir = dir.parentFile!!
            dir
        }
    }

    private fun activity(): MainActivity {
        val c = Robolectric.buildActivity(MainActivity::class.java)
        c.setup(); shadowOf(Looper.getMainLooper()).idle()
        return c.get()
    }

    /** 在页头里按文字找那个标签。 */
    private fun findLabel(root: View, wanted: String): TextView? {
        if (root is TextView && root.text?.toString() == wanted) return root
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) findLabel(root.getChildAt(i), wanted)?.let { return it }
        }
        return null
    }

    /** **用户要的那句：标签必须在。** */
    @Test
    fun theHeaderCarriesTheSavePathLabel() {
        val a = activity()
        a.showDownloads(); shadowOf(Looper.getMainLooper()).idle()
        val header = a.root.getChildAt(0)
        assertNotNull("页头里必须有「$LABEL」文字标签 —— 用户明确要求加回来", findLabel(header, LABEL))
    }

    /**
     * **路径文字必须仍然是 START 截断。**
     *
     * 这一条是"加标签不会让用户失去信息"的**唯一依据**：
     * 只有从开头截，结尾的目录名才保得住。
     * 哪天有人改成 END 截断，用户就再也看不到自己存到哪个文件夹了 —— 那才是真的挤没了。
     */
    @Test
    fun thePathIsStillTruncatedFromTheStartSoTheFolderNameSurvives() {
        val a = activity()
        a.showDownloads(); shadowOf(Looper.getMainLooper()).idle()
        val pathText = a.downloadPathText
        assertNotNull("页头必须有路径文字", pathText)
        assertEquals(
            "路径必须从**开头**截断：这样被吃掉的是 storage/emulated/0 这种固定前缀，" +
                "而用户真正要看的结尾目录名永远看得见",
            TextUtils.TruncateAt.START,
            pathText.ellipsize,
        )
        assertTrue("路径必须单行", pathText.maxLines == 1 || pathText.lineCount <= 1)
    }

    /** 路径文字必须吃掉剩余空间（weight=1），否则加标签后它会变成固定窄条。 */
    @Test
    fun thePathTextStillTakesTheRemainingWidth() {
        val a = activity()
        a.showDownloads(); shadowOf(Looper.getMainLooper()).idle()
        val lp = a.downloadPathText.layoutParams as LinearLayout.LayoutParams
        assertEquals("路径文字必须是 weight=1，吃满剩余宽度", 1f, lp.weight)
        assertEquals("weight=1 的宽度必须是 0（由 weight 分配）", 0, lp.width)
    }

    /**
     * **接线守卫：标签是加在路径区里的，不是别处。**
     *
     * 上面几条只证明"页头某处有个「保存路径」"。这条确保它和图标、路径文字
     * 在**同一个 LinearLayout** 里，也就是真的长在路径行上。
     */
    @Test
    fun theLabelSitsInTheSameRowAsTheFolderIconAndThePath() {
        val a = activity()
        a.showDownloads(); shadowOf(Looper.getMainLooper()).idle()
        val pathText = a.downloadPathText
        val row = pathText.parent as? ViewGroup
        assertNotNull("路径文字必须在一个容器里", row)
        var hasIcon = false
        var hasLabel = false
        for (i in 0 until row!!.childCount) {
            val child = row.getChildAt(i)
            if (child is android.widget.ImageView) hasIcon = true
            if (child is TextView && child.text?.toString() == LABEL) hasLabel = true
        }
        assertTrue("路径行里必须有文件夹图标（用户喜欢的那版就是图标+标签+路径）", hasIcon)
        assertTrue("「$LABEL」标签必须和路径文字在**同一行**里", hasLabel)
    }

    /** 标签必须自己写进源码 —— 防止有人"优化"时又把它删掉。 */
    @Test
    fun theLabelIsActuallyConstructedInTheSource() {
        val source = File(repoRoot, "app/src/main/java/cc/nkbr/lanzouplus/MainActivity.java").readText()
        assertTrue(
            "源码里必须真的构造了「$LABEL」标签",
            source.contains("text(\"保存路径\"") || source.contains("text(\"$LABEL\""),
        )
    }
}
