package cc.nkbr.lanzouplus

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser

/**
 * [DFWX BRAND-003] 矢量图资源命名空间回归。
 *
 * 症状（真机 v1.22.8，证据等级 A）：AI 设置 → 赞助 → 点击即闪退。
 * 堆栈：painterResource(R.drawable.afdian) → Compose XmlVectorParser
 *       → XmlPullParserException: Binary XML file line #5<VectorGraphic> tag requires viewportWidth > 0
 *
 * 根因：宿主 app/build.gradle.kts 的 aapt2 参数含 `--no-xml-namespaces`，它会把最终 APK 里
 * 所有 res 下的二进制 XML 命名空间 URI 一并剥离。对同一份 afdian.xml 做过 A/B 对照：
 * 不带该参数时字符串池含 'android' 与 'http://schemas.android.com/apk/res/android'，
 * 带该参数时两者都没了，属性的 ns 字段变成 -1。
 *
 * Compose 读矢量图属性走的是带命名空间的查找
 * （androidx.core TypedArrayUtils.hasAttribute → parser.getAttributeValue(
 *   "http://schemas.android.com/apk/res/android", "viewportWidth")）；
 * 命名空间被剥离后该查找恒为 null，getNamedFloat 退回默认值 0f，于是抛
 * "<VectorGraphic> tag requires viewportWidth > 0"。
 *
 * 本用例直接复现 Compose 的查找方式：只要宿主构建再次剥掉命名空间，这里必红。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ApkXmlNamespaceJvmTest {

    private val androidNs = "http://schemas.android.com/apk/res/android"

    private fun context(): Context = RuntimeEnvironment.getApplication()

    /** afdian 是崩溃当事人；android:viewportWidth=24 写在源码里，必须能被命名空间查找到。 */
    @Test
    fun vectorDrawable_keepsAndroidNamespace_forViewportWidth() {
        val ctx = context()
        val id = ctx.resources.getIdentifier("afdian", "drawable", ctx.packageName)
        assertNotNull("afdian 资源必须存在（赞助页图标，BRAND-003 当事人）", id)
        assert(id != 0) { "afdian 资源必须存在（赞助页图标，BRAND-003 当事人）" }

        val parser = ctx.resources.getXml(id)
        var viewportWidth: String? = null
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            if (parser.name == "vector") {
                viewportWidth = parser.getAttributeValue(androidNs, "viewportWidth")
            }
        }
        parser.close()

        assertEquals(
            "矢量图 android:viewportWidth 必须能按命名空间读到；读到 null 说明 aapt2 的 " +
                "no-xml-namespaces 又把命名空间剥掉了，Compose 会在 painterResource 时崩溃（BRAND-003）",
            24f,
            viewportWidth?.toFloatOrNull(),
        )
    }

    /** 兜底：宿主与 vendor 的矢量图共用同一 aapt2 参数，命名空间必须全部保留。 */
    @Test
    fun vectorDrawable_keepsNamespace_forAllVectors() {
        val ctx = context()
        val names = listOf("afdian", "deepthink", "ic_add", "ic_search")
        var checked = 0
        for (name in names) {
            val id = ctx.resources.getIdentifier(name, "drawable", ctx.packageName)
            if (id == 0) continue
            checked++
            val parser = ctx.resources.getXml(id)
            var attrCount = 0
            var namespacedAttrCount = 0
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType != XmlPullParser.START_TAG) continue
                for (i in 0 until parser.attributeCount) {
                    attrCount++
                    if (androidNs == parser.getAttributeNamespace(i)) namespacedAttrCount++
                }
            }
            parser.close()
            assertEquals(
                "资源 $name 的 android 命名空间属性全部丢失（attr=$attrCount, ns=$namespacedAttrCount）——" +
                    "说明 no-xml-namespaces 生效，Compose 矢量图解析会崩",
                attrCount,
                namespacedAttrCount,
            )
        }
        assert(checked > 0) { "至少应校验到一个矢量图资源" }
    }
}
