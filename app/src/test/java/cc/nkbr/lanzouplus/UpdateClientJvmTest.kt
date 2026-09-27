package cc.nkbr.lanzouplus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [DFWX] 更新检查回归（T6-B）。
 *
 * 背景：UpdateClient 原先指向**上游仓库** `nekobyran/lanzouplus`（最新 v1.6.4）与自建端点
 * `lanzouplus.nkbr.cc`（最新 v1.4.8），而本 App 版本是 1.21.x。
 * `compare(latest,current)<=0` 因此恒成立 → `check()` 永远返回 null → 更新提示永不出现。
 *
 * 修复要点：
 *  1. 仓库指向自有 `dongwangxingchen/dongfang-wuxian`；
 *  2. 资产名由固定 "LanzouPlus.apk" 改为前缀匹配 `dongfang-wuxian-v<semver>.apk`；
 *  3. 未部署镜像时镜像端点为空，不再退回到上游下载地址。
 */
class UpdateClientJvmTest {

    private fun asset(name: String): Boolean {
        val m = UpdateClient::class.java.getDeclaredMethod("isReleaseAsset", String::class.java)
        m.isAccessible = true
        return m.invoke(null, name) as Boolean
    }

    private fun checkUrlAllowed(url: String): Boolean {
        val m = UpdateClient::class.java.getDeclaredMethod("isAllowedDownloadUrl", java.net.URL::class.java)
        m.isAccessible = true
        return m.invoke(null, java.net.URL(url)) as Boolean
    }

    @Test
    fun acceptsOwnReleaseAssetNames() {
        assertTrue(asset("dongfang-wuxian-v1.21.4.apk"))
        assertTrue(asset("dongfang-wuxian-v1.0.0.apk"))
        assertTrue(asset("dongfang-wuxian-v10.20.30.apk"))
    }

    /** 上游资产名与任意后缀都不应被接受（防止再次误指上游）。 */
    @Test
    fun rejectsForeignAndMalformedAssetNames() {
        assertFalse("上游资产名必须拒绝", asset("LanzouPlus.apk"))
        assertFalse("带描述后缀的资产名必须拒绝", asset("dongfang-wuxian-v1.21.4-beta.apk"))
        assertFalse("缺少版本号必须拒绝", asset("dongfang-wuxian-v.apk"))
        assertFalse("非 apk 必须拒绝", asset("dongfang-wuxian-v1.21.4.zip"))
        assertFalse("路径穿越式命名必须拒绝", asset("dongfang-wuxian-v1.21.4.apk/../evil"))
        assertFalse("空名必须拒绝", asset(""))
    }

    @Test
    fun allowsOwnedDownloadHosts() {
        assertTrue(checkUrlAllowed("https://github.com/dongwangxingchen/dongfang-wuxian/releases/download/v1.21.4/dongfang-wuxian-v1.21.4.apk"))
        assertTrue(checkUrlAllowed("https://objects.githubusercontent.com/foo/dongfang-wuxian-v1.21.4.apk"))
        assertTrue(checkUrlAllowed("https://lanzouplus.nkbr.cc/download/dongfang-wuxian-v1.21.4.apk"))
    }

    @Test
    fun rejectsUntrustedDownloadHosts() {
        assertFalse("上游域名必须拒绝", checkUrlAllowed("https://lanzouplus.nkbr.cc.evil.com/x.apk"))
        assertFalse("明文 http 必须拒绝", checkUrlAllowed("http://github.com/x/dongfang-wuxian-v1.21.4.apk"))
        assertFalse("无关域名必须拒绝", checkUrlAllowed("https://evil.com/dongfang-wuxian-v1.21.4.apk"))
    }

    /** 版本比较方向：同版本不提示，旧版本不提示，新版本才提示。 */
    @Test
    fun versionComparisonIsDirectional() {
        val parse = UpdateClient::class.java.getDeclaredMethod("parseVersion", String::class.java)
        parse.isAccessible = true
        val compare = UpdateClient::class.java.getDeclaredMethod("compare", LongArray::class.java, LongArray::class.java)
        compare.isAccessible = true
        fun cmp(a: String, b: String): Int =
            compare.invoke(null, parse.invoke(null, a), parse.invoke(null, b)) as Int
        assertEquals("同版本应返回 0", 0, cmp("1.21.4", "1.21.4"))
        assertTrue("1.21.4 应低于 1.21.5", cmp("1.21.4", "1.21.5") < 0)
        assertTrue("v 前缀必须等价", cmp("v1.21.4", "1.21.4") == 0)
        assertTrue("1.21.4 应高于上游 1.6.4", cmp("1.21.4", "1.6.4") > 0)
    }
}
