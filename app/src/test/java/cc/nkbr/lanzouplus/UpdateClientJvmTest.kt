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

    /**
     * [2026-10-03] **自有服务器必须放行 —— 这是本次修的 bug。**
     *
     * 用户报的：「点击下载 → 下载失败 · dongfang-wuxian-v1.0.1.apk：该源已失效或跳转异常」
     *
     * 根因：后台记录里的 apkUrl 指向**我们自己的服务器**（`39.106.33.135`），
     * 但这张白名单里根本没有它 —— 于是 `SegmentDownloader` 判定"更新下载地址不受信任"
     * 直接抛错，被 `friendlyError` 显示成「该源已失效或跳转异常」。
     * **服务器上那个文件明明好好的。**
     *
     * 现在主机名从 `RemoteConfigClient.BASE` 推导（只留一处事实源），
     * 所以这条测试直接照着 BASE 构造 URL —— 换服务器时它会跟着走，不需要改测试。
     */
    @Test
    fun allowsOurOwnServer() {
        val host = java.net.URL(RemoteConfigClient.BASE).host
        assertTrue("https 到自有服务器必须放行", checkUrlAllowed("https://$host/apk/dongfang-wuxian-v1.0.0.apk"))
        assertTrue(
            "http 到自有服务器也要放行：版本元数据本身就是从这台机器用 http 读来的，" +
                "给下载单独要求 https 挡不住能改元数据的人；真正的边界是下载后的签名校验",
            checkUrlAllowed("http://$host/apk/dongfang-wuxian-v1.0.0.apk"),
        )
    }

    /** 放宽必须**有界**：只认精确主机名，不能变成后缀/子域通配。 */
    @Test
    fun ownServerRelaxation_isExactHostOnly() {
        val host = java.net.URL(RemoteConfigClient.BASE).host
        assertFalse("形近域名必须拒绝", checkUrlAllowed("https://$host.evil.com/apk/x.apk"))
        assertFalse("前置前缀也必须拒绝", checkUrlAllowed("https://evil-$host/apk/x.apk"))
        assertFalse("非 http(s) 协议必须拒绝", checkUrlAllowed("ftp://$host/apk/x.apk"))
        assertFalse("带用户信息必须拒绝", checkUrlAllowed("http://user:pw@$host/apk/x.apk"))
    }

    /**
     * [2026-10-03 补] **放宽的边界还包括端口。**
     *
     * 原来自有服务器分支是 `host.equals(own)` 直接 `return true` —— 不检查端口，
     * 于是 `http://<host>:8080/x.apk` 也被放行，与注释自称的「放宽是**有界**的」矛盾。
     * 2026-10-03 对抗性复查用 JDK 实跑 17 个 URL 时发现了这条。
     */
    @Test
    fun ownServerRelaxation_isAlsoBoundedByPort() {
        val host = java.net.URL(RemoteConfigClient.BASE).host
        assertTrue("默认端口（不带 :port）必须放行", checkUrlAllowed("http://$host/apk/x.apk"))
        assertTrue("显式 :80 必须放行", checkUrlAllowed("http://$host:80/apk/x.apk"))
        assertTrue("显式 :443 必须放行", checkUrlAllowed("https://$host:443/apk/x.apk"))
        assertFalse("非标端口必须拒绝 —— 否则「有界」这句话就是假的", checkUrlAllowed("http://$host:8080/apk/x.apk"))
        assertFalse("非标端口必须拒绝（https 同理）", checkUrlAllowed("https://$host:8443/apk/x.apk"))
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
