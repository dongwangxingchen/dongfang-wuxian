package cc.nkbr.lanzouplus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [DFWX] DFW-59：更新供给归一的**真值表**。
 *
 * 两个来源（自有后台 / GitHub）字段与能力都不同，UI 只认归一后的 `UpdateOffer`。
 * 这里钉三件最容易错、且错了用户会直接受害的事：
 *   ① **没有 sha256 就不能装**（三重校验的第一重没了，等于装未知包）；
 *   ② 版本号换算必须与 `app/build.gradle.kts` 的编号规则一致；
 *   ③ 后台缺 versionCode 时不许静默丢掉更新（那会变成"后台有新版但软件不提示"）。
 */
class UpdateOfferJvmTest {

    private val validDigest = "sha256:" + "a".repeat(64)

    /** 反射构造 RemoteConfigClient.Release（它是包级 final 类，字段 final，测试里直接用构造器）。 */
    private fun release(
        versionName: String = "1.0.1",
        versionCode: Long = 10001L,
        apkUrl: String = "http://39.106.33.135/apk/dongfang-wuxian-v1.0.1.apk",
        sha256: String = validDigest,
        size: Long = 36_600_000L,
        updateMode: String = "soft",
    ) = RemoteConfigClient.Release(versionName, versionCode, apkUrl, sha256, size, updateMode)

    // ── 版本号换算 ────────────────────────────────────────────────────────

    @Test
    fun versionCodeDerivation_matchesGradleScheme() {
        // 与 app/build.gradle.kts 的注释一致：1.0.0→10000、1.0.1→10001、1.1.0→10100、2.0.0→20000
        assertEquals(10000L, UpdateOffer.codeFromName("1.0.0"))
        assertEquals(10001L, UpdateOffer.codeFromName("1.0.1"))
        assertEquals(10100L, UpdateOffer.codeFromName("1.1.0"))
        assertEquals(20000L, UpdateOffer.codeFromName("2.0.0"))
        // GitHub tag 带 v 前缀
        assertEquals(10001L, UpdateOffer.codeFromName("v1.0.1"))
        assertEquals(10001L, UpdateOffer.codeFromName(" 1.0.1 "))
        assertEquals(12345L, UpdateOffer.codeFromName("1.23.45"))
    }

    @Test
    fun versionCodeDerivation_rejectsGarbageInsteadOfSilentlyGuessing() {
        // 返回 0 表示"这个来源不可用"。若这里瞎猜成某个数字，
        // 会把一个解析不了的版号当成真实版本去和已装版本比较，可能误判成"有更新"或"没更新"。
        assertEquals(0L, UpdateOffer.codeFromName(""))
        assertEquals(0L, UpdateOffer.codeFromName(null))
        assertEquals(0L, UpdateOffer.codeFromName("1.0"))
        assertEquals(0L, UpdateOffer.codeFromName("1.0.0.0"))
        assertEquals(0L, UpdateOffer.codeFromName("1.x.0"))
        assertEquals(0L, UpdateOffer.codeFromName("最新版"))
        assertEquals(0L, UpdateOffer.codeFromName("-1.0.0"))
    }

    // ── 自有后台 → 供给 ───────────────────────────────────────────────────

    @Test
    fun remoteOffer_carriesModeAndDigest() {
        val offer = UpdateOffer.fromRemote(release(updateMode = "force"), "修了几个问题", "https://github.com/x/y", "")
        assertNotNull(offer)
        offer!!
        assertEquals("1.0.1", offer.versionName)
        assertEquals(10001L, offer.versionCode)
        assertEquals(validDigest, offer.digest)
        assertEquals(UpdatePromptPolicy.Mode.FORCE, offer.mode)
        assertEquals("修了几个问题", offer.body)
        assertTrue("有 url+摘要+大小 → 可以走 App 内下载", offer.installable())
    }

    @Test
    fun remoteOffer_missingVersionCode_fallsBackToNameInsteadOfDroppingTheUpdate() {
        // 后台老数据可能没填 versionCode。若此处当成 0，用户会看到"后台明明发了新版，软件却不提示"。
        val offer = UpdateOffer.fromRemote(release(versionCode = 0L), "", "", "")
        assertNotNull("缺 versionCode 不该让整条更新消失", offer)
        assertEquals("应从 versionName 换算出来", 10001L, offer!!.versionCode)
    }

    @Test
    fun remoteOffer_withoutDigest_isNotInstallable() {
        // 三重校验的第一重是 sha256。没有摘要就装，等于放弃校验。
        val offer = UpdateOffer.fromRemote(release(sha256 = ""), "", "", "")!!
        assertFalse("没摘要 → 不能走 App 内下载", offer.installable())
    }

    @Test
    fun remoteOffer_withoutSize_isNotInstallable() {
        val offer = UpdateOffer.fromRemote(release(size = 0L), "", "", "")!!
        assertFalse("没大小 → 无法校验完整性，不能装", offer.installable())
    }

    @Test
    fun remoteOffer_emptyVersionNameIsRejected() {
        // 空版号换算不出 code → versionCode=0；此时不该被当成可用供给。
        val offer = UpdateOffer.fromRemote(release(versionName = "", versionCode = 0L), "", "", "")!!
        assertEquals(0L, offer.versionCode)
        assertFalse("版号缺失时不可安装（否则 UI 会显示一个空版本号的弹窗）", offer.installable())
    }

    @Test
    fun nullRelease_yieldsNull() {
        assertNull(UpdateOffer.fromRemote(null, "", "", ""))
    }

    // ── 「其他下载方式」第二层 ────────────────────────────────────────────

    @Test
    fun alternates_alwaysLabelGithubAsNeedingVpn() {
        val offer = UpdateOffer.fromRemote(release(), "", "https://github.com/dongwangxingchen/dongfang-wuxian", "")!!
        val alts = offer.alternates()
        assertEquals(1, alts.size)
        // 国内直连 github.com 不通是事实。不标注会让用户点了打不开、以为软件坏了。
        assertTrue("GitHub 条目必须标注「需科学上网」", alts[0].label.contains("需科学上网"))
        assertEquals("https://github.com/dongwangxingchen/dongfang-wuxian", alts[0].url)
    }

    @Test
    fun alternates_includeMirrorPageOnlyWhenConfigured() {
        val without = UpdateOffer.fromRemote(release(), "", "https://github.com/x/y", "")!!
        assertEquals("后台没填第三方页时不该出现空条目", 1, without.alternates().size)

        val with = UpdateOffer.fromRemote(release(), "", "https://github.com/x/y", "https://flowus.cn/xxx")!!
        assertEquals(2, with.alternates().size)
        assertTrue("后台填了第三方页就应多一个渠道（不用改代码）", with.alternates().any { it.url == "https://flowus.cn/xxx" })
    }

    @Test
    fun alternates_areImmutable() {
        val offer = UpdateOffer.fromRemote(release(), "", "https://github.com/x/y", "")!!
        try {
            (offer.alternates() as MutableList<UpdateOffer.Alt>).add(UpdateOffer.Alt("注入", "http://evil"))
            // 能改就说明返回了可变列表 —— 调用方一次手滑就能污染这份数据
            assertTrue("alternates() 必须返回不可变列表", false)
        } catch (expected: UnsupportedOperationException) {
            // 正确
        }
    }

    // ── GitHub → 供给 ────────────────────────────────────────────────────

    @Test
    fun githubOffer_isAlwaysSoftMode() {
        // GitHub 没有"强制更新"这个概念。若这里漏成 FORCE，用户会因为一个兜底来源被锁死无法取消。
        val info = UpdateClient.UpdateInfo(
            "1.0.1", "notes", "https://github.com/x/y/releases/download/v1.0.1/dongfang-wuxian-v1.0.1.apk",
            "", validDigest, 1000L, false,
        )
        val offer = UpdateOffer.fromGithub(info, "https://github.com/x/y")!!
        assertEquals(UpdatePromptPolicy.Mode.SOFT, offer.mode)
        assertEquals(10001L, offer.versionCode)
        assertTrue(offer.installable())
    }

    @Test
    fun nullGithubInfo_yieldsNull() {
        assertNull(UpdateOffer.fromGithub(null, "https://github.com/x/y"))
    }

    // ── 可用性守卫 ───────────────────────────────────────────────────────

    @Test
    fun hasAnyDownload_isFalseOnlyWhenEveryRouteIsEmpty() {
        // 点哪个都没用的弹窗等于骗用户，这种供给不该出现。
        val dead = UpdateOffer("", 0L, 0L, "", "", "", "", "", "", UpdatePromptPolicy.Mode.SOFT)
        assertFalse("四条路全空 → 不该弹", dead.hasAnyDownload())

        val onlyPage = UpdateOffer("1.0.1", 10001L, 0L, "", "", "", "", "https://github.com/x/y", "", UpdatePromptPolicy.Mode.SOFT)
        assertTrue("只有发布页也算有去处", onlyPage.hasAnyDownload())
    }

    @Test
    fun sizeText_isEmptyWhenUnknown_notZeroBytes() {
        val unknown = UpdateOffer("1.0.1", 10001L, 0L, validDigest, "", "http://a/b.apk", "", "", "", UpdatePromptPolicy.Mode.SOFT)
        assertEquals("大小未知时不许显示「0 B」这种误导信息", "", unknown.sizeText())

        val known = UpdateOffer("1.0.1", 10001L, 36_600_000L, validDigest, "", "http://a/b.apk", "", "", "", UpdatePromptPolicy.Mode.SOFT)
        assertTrue("已知大小要能读出来", known.sizeText().contains("MB"))
    }

    // ── 摘要格式归一（两个来源格式不同，不统一就静默失效）────────────────

    @Test
    fun remoteOffer_plainHexDigest_isNormalizedToPrefixedForm() {
        // **后台存的就是裸 64 位 hex**（RemoteConfigClient 的校验正则就是 [0-9a-fA-F]{64}），
        // 而三重校验比对的是 "sha256:"+hex。不归一的话自有服务器下到的包 100% 校验失败——
        // 下载看着成功、装永远装不上。这是"两个来源各自都对、拼起来错"的典型。
        val hex = "c".repeat(64)
        val offer = UpdateOffer.fromRemote(release(sha256 = hex), "", "", "")!!
        assertEquals("后台裸 hex 必须补上 sha256: 前缀", "sha256:$hex", offer.digest)
    }

    @Test
    fun githubOffer_prefixedDigest_isKeptAsIs() {
        val hex = "d".repeat(64)
        val info = UpdateClient.UpdateInfo(
            "1.0.1", "notes", "https://github.com/x/y/releases/download/v1.0.1/dongfang-wuxian-v1.0.1.apk",
            "", "sha256:$hex", 1000L, false,
        )
        val offer = UpdateOffer.fromGithub(info, "https://github.com/x/y")!!
        assertEquals("GitHub 已带前缀，不得重复叠加", "sha256:$hex", offer.digest)
    }

    @Test
    fun normalizeDigest_isIdempotent_andCaseInsensitive() {
        val hex = "e".repeat(64)
        assertEquals("sha256:$hex", UpdateOffer.normalizeDigest(hex))
        assertEquals("sha256:$hex", UpdateOffer.normalizeDigest("sha256:$hex"))
        assertEquals(
            "重复归一不得叠加前缀",
            "sha256:$hex",
            UpdateOffer.normalizeDigest(UpdateOffer.normalizeDigest(hex)),
        )
        // 后台正则允许大写 A-F，归一必须统一成小写，否则校验比对会因大小写不等而失败
        assertEquals("大写要归一成小写", "sha256:$hex", UpdateOffer.normalizeDigest("E".repeat(64)))
        assertEquals("带前缀的大写也要归一", "sha256:$hex", UpdateOffer.normalizeDigest("SHA256:" + "E".repeat(64)))
    }

    @Test
    fun normalizeDigest_emptyStaysEmpty_soInstallableStaysFalse() {
        assertEquals("", UpdateOffer.normalizeDigest(""))
        assertEquals("", UpdateOffer.normalizeDigest("   "))
        assertEquals("", UpdateOffer.normalizeDigest(null))
        // 空摘要绝不能变成 "sha256:"（那会让 installable() 误判为可安装）
        assertFalse("空摘要归一后仍不可安装", UpdateOffer.fromRemote(release(sha256 = ""), "", "", "")!!.installable())
    }
}
