package cc.nkbr.lanzouplus

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * [DFWX] v1.19.0 内置软件库清单回归：资产 r 经 LanzouCore.recommendations() 解析出 ≥50 个
 * 唯一蓝奏源（标题/URL 非空、全 HTTPS、无重复、提取码列在位），主页胶囊条数据源就位，
 * 且首库保持「电子香菜软件库」（home 落地源契约：MainActivity.copySource(values.get(0), home)）。
 *
 * LIBRARY_AUTO_IMPORT=false 静默批量导入——85 源导入会对全部源做真实网络探测，绝不能进 JVM 测试。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w343dp-h343dp-280dpi")
class LibrariesCatalogJvmTest {

    companion object {
        @BeforeClass @JvmStatic fun silenceAutoImport() { MainActivity.LIBRARY_AUTO_IMPORT = false }
    }

    @Test fun builtInCatalog_parsesFiftyPlusUniqueLanzouSources() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val core = LanzouCore(context)
        try {
            val sources = core.recommendations()
            assertTrue("内置源应≥50，实际 ${sources.size}", sources.size >= 50)
            val urls = HashSet<String>()
            val problems = StringBuilder()
            for (s in sources) {
                if (s.title.isEmpty()) problems.append("空标题: ").append(s.url).append('\n')
                if (!s.url.startsWith("https://")) problems.append("非 HTTPS: ").append(s.url).append('\n')
                if (!urls.add(s.url)) problems.append("重复 URL: ").append(s.url).append('\n')
                if (s.url.contains("yoyodada.lanzou")) problems.append("域名手误(少da): ").append(s.url).append('\n')
            }
            assertEquals(problems.toString(), "", problems.toString())
        } finally {
            core.close()
        }
    }

    @Test fun homeLibraryStrip_rendersEveryBuiltInLibraryWithCanonicalFirst() {
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        controller.setup()
        shadowOf(Looper.getMainLooper()).idle()
        val activity = controller.get()
        try {
            assertTrue("主页 libraries 应≥50，实际 ${activity.libraries.size}", activity.libraries.size >= 50)
            assertEquals("首库必须是电子香菜（home 落地源契约）", "电子香菜软件库", activity.libraries[0].title)
        } finally {
            controller.destroy()
        }
    }
}
