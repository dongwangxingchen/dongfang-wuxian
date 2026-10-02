package cc.nkbr.lanzouplus

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar
import java.util.TimeZone

/**
 * [DFW-122] 工具箱**算法**回归：把 `tools/` 下三个孤儿测试的向量搬回真实现。
 *
 * ## 为什么有这个文件
 * `tools/B3ToolsTest.java`、`tools/ConvertToolsTest.java`、`tools/TextStatsTest.java`
 * 三个文件带着**很下本的测试向量**（GB/T 2260 省级行政区划码、寿星公式立春、
 * RFC 4648 编码向量、ITU 摩斯全表），但它们：
 * 1. 躺在 `tools/`，**不在任何 test 源集**里 —— 所以**从来没被跑过**；
 * 2. 自己把算法**复制了一份**再测那份副本（三个文件的头注释都写着"与 Toolbox.java 同步的副本"）；
 * 3. 不 import 任何 `cc.nkbr.lanzouplus` 代码。
 *
 * 结果是最坏的一种：**用户可感知的算法零真覆盖，而那份副本测试给人"它们是绿的"的错觉**。
 * 这个文件把向量**原样**搬过来，但每条都改成**调用真的 `Toolbox`**。
 * 从此 `Toolbox` 的实现改了，这里会红；副本与实现分叉，这里也会红。
 *
 * ## 哪些工具在这次覆盖里
 * 生肖/立春（25）、身份证省份（26）、年龄虚岁（27）、Base64（15）、URL（16）、
 * 时间戳（17）、进制（18）、摩斯（19）、文本统计（14）。
 * 覆盖前唯一真测试是 `ToolboxLogicJvmTest.kt`，它只测 `rgbToHsl` / `colorInfo` / `rmsToDb`。
 *
 * ## 为什么整类挂 Robolectric
 * `Toolbox.base64Encode` / `base64Decode`（`Toolbox.java:396`、`:406`）内部用的是
 * **`android.util.Base64`**，纯 JVM 下会直接抛 "not mocked"。
 * 其余用例都是纯 JDK，本来普通 JUnit 就够 —— 但一个类里混两种 runner 做不到，
 * 而 base64 是这次必须覆盖的重头（RFC 4648 全套向量），所以整类统一挂 Robolectric。
 *
 * ## 时区：为什么要在测试里钉死 GMT+8
 * `Toolbox.stampToDate`（`Toolbox.java:112-120`）与 `dateToStamp`（`:121-130`）都用
 * **设备默认时区**（没有 `setTimeZone`），而副本向量是按 **GMT+8** 算的
 * （副本原注释：「测试固定；产品用设备默认（中国用户即 +8）」）。
 * 本机/CI 若是 UTC，`stampToDate(0)` 会得到 `1970-01-01 00:00:00` 而不是向量里的
 * `08:00:00` —— 那不是算法错，是**测试环境与目标用户不一致**。
 * 所以这里在 `@Before` 把默认时区钉成 GMT+8，跑完在 `@After` 还原，不污染其它测试。
 *
 * ## 与副本的已知差异（都已在这里显式处理，不是偷偷改期望值）
 * - `nominalAge(Calendar, Calendar)` 在 `Toolbox` 里**不存在**：虚岁是 `ageCalc` 里的**内联**表达式
 *   （`Toolbox.java:300`），且 `ageCalc` 只吃出生日期、内部取"现在"。
 *   副本那两条 Calendar 向量的期望值（27 / 2）在 2026 年与真实现**完全一致**，
 *   这里改用"规则 + 当年年份"的写法，既保留原值又不会到 2027 年就假红。
 * - 摩斯在 `Toolbox` 里是**一个**方法 `morseConvert(toMorse, value)`（`:154`），
 *   副本拆成了 `morseEncode` / `morseDecode` 两个；这里按布尔参数对号入座。
 * - 副本的 `weeks` 用 `DAY_OF_WEEK-2` 再夹到 0，**星期天会算成"周一"**（副本 bug）；
 *   `Toolbox.java:118-119` 用 `DAY_OF_WEEK-1`，是对的。见本文件最后一条用例。
 *
 * ## 向量来源
 * `tools/B3ToolsTest.java:45-68`、`tools/ConvertToolsTest.java:155-202`、
 * `tools/TextStatsTest.java:72-100`。**期望值一个都没改。**
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ToolboxAlgorithmsJvmTest {

    private var savedTimeZone: TimeZone? = null

    @Before
    fun pinTimeZoneToChina() {
        savedTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("GMT+8"))
    }

    @After
    fun restoreTimeZone() {
        savedTimeZone?.let { TimeZone.setDefault(it) }
    }

    // ─────────────────────────── 25 生肖 / 立春 ───────────────────────────

    /**
     * 寿星公式立春日：[Toolbox.lichunDay]（`Toolbox.java:585-587`）。
     *
     * 如果这里错了，用户在立春前后生日查生肖会**整整差一个属相** ——
     * 这是传统命理里最忌讳的错，而且用户没有任何办法自己判断。
     */
    @Test
    fun lichunDay_matchesTheAlmanacForTheSampledYears() {
        assertEquals("2024 年立春 = 2/4（万年历）", 4, Toolbox.lichunDay(2024))
        assertEquals("2023 年立春 = 2/4（万年历）", 4, Toolbox.lichunDay(2023))
        assertEquals("2021 年立春 = 2/3（万年历）", 3, Toolbox.lichunDay(2021))
    }

    /**
     * 生肖按立春精确判定：[Toolbox.zodiacPrecise]（`Toolbox.java:589-596`）。
     *
     * 守的是"立春前出生算上一年属相"这条规则。
     * 如果这里错了，2 月初出生的用户会看到**错的属相**，而且他自己多半不知道，
     * 拿去跟长辈对不上还以为是 App 有问题。
     */
    @Test
    fun zodiacPrecise_switchesAtLichunNotAtNewYear() {
        assertEquals("2024-02-03 立春前一天，仍属兔", "兔", Toolbox.zodiacPrecise(2024, 2, 3))
        assertEquals("2024-02-04 立春当日，换属龙", "龙", Toolbox.zodiacPrecise(2024, 2, 4))
        assertEquals("2024-06-15 立春后，属龙", "龙", Toolbox.zodiacPrecise(2024, 6, 15))
        // 副本原注：2023 立春 2/4，前一日仍属虎（2022 年）
        assertEquals("2023-02-03 立春前一天，仍属虎", "虎", Toolbox.zodiacPrecise(2023, 2, 3))
        assertEquals("2023-02-04 立春当日，换属兔", "兔", Toolbox.zodiacPrecise(2023, 2, 4))
    }

    /**
     * 立春公式只覆盖 2001-2099，范围外回退按公历年：[Toolbox.zodiacPrecise]（`:591`）。
     *
     * 守"不假装精确"：1980 年出生的人拿到的应该是**老实按公历年算**的结果，
     * 而不是一个用超范围公式算出来的、看着精确其实错的属相。
     */
    @Test
    fun zodiacPrecise_outOfRangeFallsBackToThePlainYear() {
        assertEquals(
            "1990 不在 2001-2099，必须回退公历年算法",
            Toolbox.zodiacOf(1990),
            Toolbox.zodiacPrecise(1990, 5, 20),
        )
    }

    // ─────────────────────────── 26 身份证省份 ───────────────────────────

    /**
     * 省级行政区划码（GB/T 2260）：[Toolbox.provinceOf]（`Toolbox.java:606-618`）。
     *
     * 覆盖四大区各抽一个（华北 / 华南 / 港澳 / 西北），守住 34 个码值表不被改坏。
     * 如果漏了某一行，那一省的用户查身份证会看到"未知地区码" —— App 看着像坏了。
     */
    @Test
    fun provinceOf_decodesTheGb2260ProvinceCodes() {
        assertEquals("11 = 北京", "北京", Toolbox.provinceOf("110101199001011234"))
        assertEquals("44 = 广东", "广东", Toolbox.provinceOf("440101199001011234"))
        assertEquals("81 = 香港", "香港", Toolbox.provinceOf("810101199001012345"))
        assertEquals("65 = 新疆", "新疆", Toolbox.provinceOf("650101199001011234"))
    }

    // ─────────────────────────── 27 年龄 / 虚岁 ───────────────────────────

    /** 从 `ageCalc` 的四行输出里取"虚岁"那一行（`Toolbox.java:304` 的第三行）。 */
    private fun nominalLine(ageCalcOutput: String): String =
        ageCalcOutput.lines().firstOrNull { it.startsWith("虚岁 ") } ?: "（没有虚岁行）$ageCalcOutput"

    /**
     * 虚岁规则 = **出生即 1 岁，每跨一个公历元旦 +1**，与生日无关：
     * [Toolbox.ageCalc]（`Toolbox.java:300`）。
     *
     * 副本向量（`tools/B3ToolsTest.java:63-68`）：
     * - `(出生 2000-06-15, 现在 2026-08-15) → 27`
     * - `(出生 2025-12-31, 现在 2026-01-01) → 2`
     *
     * `Toolbox` 没有 `nominalAge(Calendar, Calendar)` 这个入口（虚岁是 `ageCalc` 内的内联表达式），
     * 所以这里把**同一规则**改写成不依赖"哪一年跑"的形式：
     * 上面两条向量在 **2026 年**与本断言**逐字等价**（`2026-2000+1 = 27`、`2026-2025+1 = 2`），
     * 之后年份也不会假红。
     *
     * 如果这里错了，用户会看到虚岁比真实小 1 岁 —— 长辈面前报错岁数，是会被当场纠正的尴尬。
     */
    @Test
    fun ageCalc_nominalAge_countsCalendarYearsNotBirthdays() {
        val thisYear = Calendar.getInstance().get(Calendar.YEAR)
        val expectFor2000 = "虚岁 ${thisYear - 2000 + 1} 岁"

        assertEquals(
            "2000-06-15 出生：虚岁只与年份有关",
            expectFor2000,
            nominalLine(Toolbox.ageCalc("2000-06-15")),
        )
        assertEquals(
            "2000-12-31 出生，与 06-15 同一年 → 虚岁必须完全相同（差半年生日不影响虚岁）",
            expectFor2000,
            nominalLine(Toolbox.ageCalc("2000-12-31")),
        )
        assertEquals(
            "去年最后一天出生 + 跨一个元旦 → 虚岁 2（出生即 1 岁）",
            "虚岁 2 岁",
            nominalLine(Toolbox.ageCalc("${thisYear - 1}-12-31")),
        )
    }

    // ─────────────────────────── 15 Base64 ───────────────────────────

    /**
     * RFC 4648 §10 全套测试向量：[Toolbox.base64Encode]（`Toolbox.java:395-400`）。
     *
     * 七个向量把三种填充长度全覆盖（无填充 / 一个 `=` / 两个 `=`）。
     * 如果填充错了，用户复制的 Base64 拿去别处解会失败，而他自己看不出是哪一步坏的。
     */
    @Test
    fun base64Encode_rfc4648Vectors() {
        val plains = arrayOf("", "f", "fo", "foo", "foob", "fooba", "foobar")
        val expect = arrayOf("", "Zg==", "Zm8=", "Zm9v", "Zm9vYg==", "Zm9vYmE=", "Zm9vYmFy")
        for (i in plains.indices) {
            assertEquals(
                "RFC 4648 向量 #$i（输入 ${plains[i].length} 字节）",
                expect[i],
                Toolbox.base64Encode(plains[i], false, false),
            )
        }
    }

    /**
     * 中文往返 + URL-safe 变体 + 去填充：[Toolbox.base64Encode] / [Toolbox.base64Decode]（`:395-408`）。
     *
     * URL-safe 那条用的是**含 `?` `"` `>` 的技巧串** —— 它编码后必然含 `+` 或 `/`，
     * 正是 URL 里会被吃掉的两个字符。如果 `+`→`-` / `/`→`_` 的替换漏了，
     * 用户把结果粘进 URL 就会坏，而且是**只有部分输入才坏**的那种偶发。
     */
    @Test
    fun base64_urlSafeAndPaddingVariants() {
        assertEquals(
            "中文 + 空格的往返不能丢字",
            "东方无限 foobar!",
            Toolbox.base64Decode(Toolbox.base64Encode("东方无限 foobar!", false, false)),
        )

        // 副本原文：' subjects?' + '"' + '>' + '>' + '>' + '?'
        val tricky = " subjects?\">>>?"
        assertEquals(
            "URL-safe 就是把标准表的 + / 换成 - _，逐字相等",
            Toolbox.base64Encode(tricky, false, false).replace('+', '-').replace('/', '_'),
            Toolbox.base64Encode(tricky, true, false),
        )
        // 自检：这条技巧串必须真的编码出 + 或 /，否则上面那条替换断言是空的（等于没测）
        val stdB64 = Toolbox.base64Encode(tricky, false, false)
        assertTrue(
            "技巧串必须真的产生 + 或 /（实测 $stdB64），否则 URL-safe 断言无意义",
            stdB64.contains('+') || stdB64.contains('/'),
        )

        assertEquals("去填充：\"f\" → \"Zg\"", "Zg", Toolbox.base64Encode("f", false, true))
    }

    /**
     * 解码侧容错：[Toolbox.base64Decode]（`Toolbox.java:401-408`）。
     *
     * 三条守三种真实输入：无填充、换行（用户从网页复制常带回车）、以及垃圾。
     * 垃圾那条要求**诚实报错**而不是抛异常或返回半截乱码 ——
     * 用户看到"解码失败"会去改输入，看到乱码只会以为软件坏了。
     */
    @Test
    fun base64Decode_toleratesMissingPaddingAndWhitespaceButRejectsGarbage() {
        assertEquals("无填充的 URL-safe 串要能解", "foobar", Toolbox.base64Decode("Zm9vYmFy"))
        assertEquals("中间的换行要能吃掉", "foobar", Toolbox.base64Decode("Zm9v\nYmFy"))
        assertTrue(
            "非 Base64 字符必须诚实报错（不能抛异常、也不能返回乱码）",
            Toolbox.base64Decode("!!不是base64!!").startsWith("解码失败"),
        )
    }

    // ─────────────────────────── 16 URL 编解码 ───────────────────────────

    /**
     * 路径口径 vs 表单口径：[Toolbox.urlEncode]（`Toolbox.java:410-421`）。
     *
     * 两个口径只差"空格变 `%20` 还是 `+`"，但**用错口径的链接对端会解成另一个东西**，
     * 而且浏览器往往不报错、只是查不到 —— 这是最难自己排查的一类。
     * 保留字那条（`/ ? = &` 全部百分号化）是路径口径的关键：少转一个 `&`，
     * 后面的内容就被当成新参数了。
     */
    @Test
    fun urlEncode_pathModeVersusFormMode() {
        assertEquals("中文按 UTF-8 逐字节百分号化", "%E4%B8%9C%E6%96%B9", Toolbox.urlEncode("东方", false))
        assertEquals("路径口径：空格 = %20", "a%20b", Toolbox.urlEncode("a b", false))
        assertEquals("表单口径：空格 = +", "a+b", Toolbox.urlEncode("a b", true))
        assertEquals(
            "路径口径必须把 / ? = & 全部转义（少转一个 & 后面就被当成新参数）",
            "a%2Fb%3Fc%3Dd%26e",
            Toolbox.urlEncode("a/b?c=d&e", false),
        )
        assertEquals(
            "表单口径同样要转义保留字",
            "a%2Fb%3Fc%3Dd%26e",
            Toolbox.urlEncode("a/b?c=d&e", true),
        )
    }

    /**
     * 解码侧：[Toolbox.urlDecode]（`Toolbox.java:422-438`）。
     *
     * `+` **只在表单口径**还原成空格 —— 路径里 `+` 就是加号本身。
     * 如果一律还原，用户解码带 `+` 的路径会多出空格。
     * 最后一条守"诚实容错"：半个 `%` 转义原样留着，不吞字符也不抛异常。
     */
    @Test
    fun urlDecode_respectsTheModeAndKeepsBadEscapes() {
        assertEquals(
            "路径口径：%20 → 空格，+ 原样保留",
            "东方 a+b",
            Toolbox.urlDecode("%E4%B8%9C%E6%96%B9%20a+b", false),
        )
        assertEquals(
            "表单口径：%20 与 + 都还原成空格",
            "东方 a b",
            Toolbox.urlDecode("%E4%B8%9C%E6%96%B9%20a+b", true),
        )
        assertEquals(
            "编解码往返必须逐字还原（含 ~ . _ - 和裸 %）",
            "q=东方~._-100%",
            Toolbox.urlDecode(Toolbox.urlEncode("q=东方~._-100%", false), false),
        )
        assertEquals(
            "无效转义原样保留（诚实容错，不吞字符）",
            "100%%ZZ",
            Toolbox.urlDecode("100%%ZZ", false),
        )
    }

    // ─────────────────────────── 17 时间戳 ───────────────────────────

    /**
     * 秒 / 毫秒自动识别与自动判别方向：[Toolbox.stampToDate]（`:112-120`）、
     * [Toolbox.timestampAuto]（`:106-111`）。
     *
     * `< 100000000000` 当秒、否则当毫秒 —— 这条阈值如果错了，
     * 用户粘一个秒级时间戳会看到 **1970 年**（乘错方向）或**公元 5 万年**（多乘一次）。
     * 向量按 GMT+8 算（见类注释的时区说明）。
     */
    @Test
    fun stampToDate_autoDetectsSecondsVersusMilliseconds() {
        assertEquals(
            "纯数字走时间戳分支，与直接调 stampToDate(0) 必须一致",
            Toolbox.stampToDate(0L),
            Toolbox.timestampAuto("0"),
        )
        assertTrue(
            "epoch 0 在 GMT+8 是 1970-01-01 08:00:00：${Toolbox.stampToDate(0L)}",
            Toolbox.stampToDate(0L).contains("1970-01-01 08:00:00"),
        )
        assertTrue(
            "秒级 1e9 → 2001-09-09 09:46:40：${Toolbox.stampToDate(1000000000L)}",
            Toolbox.stampToDate(1000000000L).contains("2001-09-09 09:46:40"),
        )
        assertTrue(
            "毫秒级 1e12 要认出来并得到同一时刻：${Toolbox.timestampAuto("1000000000000")}",
            Toolbox.timestampAuto("1000000000000").contains("2001-09-09 09:46:40"),
        )
    }

    /**
     * 日期反查时间戳：[Toolbox.dateToStamp]（`Toolbox.java:121-130`）。
     *
     * 带时间与不带时间两种输入都要认（不带时间按当天 00:00）。
     * 如果 `yyyy-MM-dd` 那条分支坏了，用户只填日期会看到"无法解析" —— 而这是最常见的用法。
     */
    @Test
    fun dateToStamp_acceptsBothDateAndDateTime() {
        assertTrue(
            "带时间：2001-09-09 09:46:40 → 1000000000：${Toolbox.dateToStamp("2001-09-09 09:46:40")}",
            Toolbox.dateToStamp("2001-09-09 09:46:40").contains("1000000000"),
        )
        assertTrue(
            "只填日期按当天 00:00（GMT+8）= 999964800：${Toolbox.dateToStamp("2001-09-09")}",
            Toolbox.dateToStamp("2001-09-09").contains("999964800"),
        )
        assertEquals(
            "无法解析的输入，两个入口给同一句人话（不能一个报错一个崩）",
            Toolbox.dateToStamp("abc"),
            Toolbox.timestampAuto("abc"),
        )
    }

    /**
     * 新增守卫（副本没有这条）：星期名映射。
     *
     * 副本用 `DAY_OF_WEEK-2` 再夹到 0，**星期天会被显示成"周一"**；
     * `Toolbox.java:118-119` 用 `DAY_OF_WEEK-1` 才是对的。
     * 2001-09-09 是**星期天** —— 正好是副本会错、真实现对的那一天。
     * 如果哪天有人把 `-1` 改成 `-2`，用户看到的就是错的星期。
     */
    @Test
    fun stampToDate_mapsSundayToSundayNotMonday() {
        val out = Toolbox.stampToDate(1000000000L)
        assertTrue("2001-09-09 是星期天，必须显示周日而不是周一：$out", out.contains("星期：周日"))
    }

    // ─────────────────────────── 18 进制转换 ───────────────────────────

    /**
     * 2/8/10/16 互转 + 前缀剥离 + 负数：[Toolbox.radixConvert]（`Toolbox.java:132-139`）。
     *
     * 负号那条最容易踩：有些实现会先剥前缀再取绝对值，把 `-10` 的符号丢了。
     * 溢出那条守"诚实报错"——`Long.parseLong` 超 64 位会抛，
     * 必须翻译成人话而不是崩（用户会粘很长的十六进制串进来）。
     */
    @Test
    fun radixConvert_handlesNegativePrefixAndOverflow() {
        assertTrue(
            "-10(16) 的十六进制表示必须带负号：${Toolbox.radixConvert("-10", 16)}",
            Toolbox.radixConvert("-10", 16).contains("十六进制：-10"),
        )
        assertTrue(
            "-10(16) = -16(10)，符号不能在转换中丢掉：${Toolbox.radixConvert("-10", 16)}",
            Toolbox.radixConvert("-10", 16).contains("十进制：-16"),
        )
        assertTrue(
            "base36 的 z = 35：${Toolbox.radixConvert("z", 36)}",
            Toolbox.radixConvert("z", 36).contains("十进制：35"),
        )
        assertTrue(
            "0x 前缀要剥掉：${Toolbox.radixConvert("0xFF", 16)}",
            Toolbox.radixConvert("0xFF", 16).contains("十进制：255"),
        )
        assertTrue(
            "0b 前缀要剥掉：${Toolbox.radixConvert("0b1010", 2)}",
            Toolbox.radixConvert("0b1010", 2).contains("十进制：10"),
        )
        assertTrue(
            "超出 64 位要报人话而不是崩：${Toolbox.radixConvert("fffffffffffffffff", 16)}",
            Toolbox.radixConvert("fffffffffffffffff", 16).startsWith("无法按"),
        )
    }

    // ─────────────────────────── 19 摩斯电码 ───────────────────────────

    /**
     * 摩斯编码：[Toolbox.morseConvert]`(true, …)`（`Toolbox.java:154-171`）。
     *
     * 数字最容易被漏（编码表只写 26 个字母的实现很常见），
     * 而 `0` 是唯一的"五个划" —— 它错了单独一条就能看出来。
     */
    @Test
    fun morseConvert_encodesLettersDigitsAndSkipsUnencodable() {
        assertEquals("SOS 是摩斯最经典的向量", "... --- ...", Toolbox.morseConvert(true, "SOS"))
        assertEquals("数字 123", ".---- ..--- ...--", Toolbox.morseConvert(true, "123"))
        assertEquals("0 = 五个划", "-----", Toolbox.morseConvert(true, "0"))
        assertTrue(
            "中文无法编码时必须诚实报数，不能静默吞掉：${Toolbox.morseConvert(true, "a你b")}",
            Toolbox.morseConvert(true, "a你b").contains("跳过 1 个"),
        )
    }

    /**
     * 摩斯解码：[Toolbox.morseConvert]`(false, …)`（`Toolbox.java:172-183`）。
     *
     * 四条守四件事：反向解对、单词间隙 `/`、**全角 `·—` 容错**
     * （用户从聊天软件里复制过来的几乎必然是这两个字符，不是半角 `.` `-`）、
     * 以及未知电码诚实计数。全角容错那条如果坏了，用户会觉得"复制过来就解不了"。
     */
    @Test
    fun morseConvert_decodesAndToleratesFullWidthDotsAndDashes() {
        assertEquals("反向解码", "SOS", Toolbox.morseConvert(false, "... --- ..."))
        assertEquals(
            "字母+单词间隙+数字的整串往返",
            "HELLO WORLD 123",
            Toolbox.morseConvert(false, Toolbox.morseConvert(true, "Hello World 123")),
        )
        assertEquals(
            "标点也要能往返（! 在 ITU 表里是 -.-.--）",
            "A.B,C?D!",
            Toolbox.morseConvert(false, Toolbox.morseConvert(true, "a.b,c?d!")),
        )
        assertEquals("·— 是全角写法，必须等同于 .- = A", "A", Toolbox.morseConvert(false, "·—"))
        assertEquals(
            "全角与半角混排：·— / ... / ——— / ···· → A S O H",
            "ASOH",
            Toolbox.morseConvert(false, "·— ... ——— ····"),
        )
        assertTrue(
            "未知电码要计数报出（........ 不是任何合法码）：${Toolbox.morseConvert(false, "........ ----.")}",
            Toolbox.morseConvert(false, "........ ----.").contains("1 个未知电码"),
        )
    }

    // ─────────────────────────── 14 文本统计 ───────────────────────────

    /** `int[10]` 每一格的业务含义（`Toolbox.java:329` 的口径注释）。 */
    private val statSlotNames = arrayOf(
        "字数(汉字+英文单词)", "汉字", "英文单词", "数字串",
        "字符(含空白)", "字符(不含空白)", "句数", "段落", "行数", "标点",
    )

    /** 逐格比对并报出**是哪一格**错的（照搬副本 `TextStatsTest.check` 的诊断方式，但消息更具体）。 */
    private fun assertStats(name: String, text: String, expect: IntArray) {
        val got = Toolbox.textStatsAll(text)
        for (i in 0 until 10) {
            assertEquals(
                "[$name] 统计口径第 $i 格「${statSlotNames[i]}」不符 —— 输入=${text.replace("\n", "\\n")} 实得=${got.toList()}",
                expect[i],
                got[i],
            )
        }
    }

    /**
     * 五个统计向量：[Toolbox.textStatsAll]（`Toolbox.java:330-359`）。
     *
     * 这五条一起守 **10 个格子互相不串**：
     * - 空文本全 0（不能返回 null、不能 NaN）；
     * - 纯中文（。！ 既是终止符也计标点）；
     * - 中英混排（词数按"连续字母数字算一个词"，数字串单独计）；
     * - 数字串与标点（`a 12 b345!` → 3 个词、2 个数字串）；
     * - 段落/行/句三者的口径差异（`\n\n` 是**一个**段落分隔，但**两**行）。
     *
     * 用户直接用这个页面对字数，任何一格错了都会当场对不上他的预期。
     */
    @Test
    fun textStatsAll_fiveCanonicalVectors() {
        assertStats("empty", "", intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0))
        assertStats("pure-cn", "你好世界。再见！", intArrayOf(6, 6, 0, 0, 8, 8, 2, 1, 1, 2))
        assertStats("mixed", "Hello 世界 world123！", intArrayOf(4, 2, 2, 1, 18, 16, 1, 1, 1, 1))
        assertStats("nums", "a 12 b345!", intArrayOf(3, 0, 3, 2, 10, 8, 1, 1, 1, 1))
        assertStats("paras", "第一段。\n\n第二段line。\n第二段续", intArrayOf(11, 10, 1, 0, 19, 16, 3, 3, 4, 2))
    }

    /**
     * 尾句补句规则：[Toolbox.textStatsAll]（`Toolbox.java:350-353`）。
     *
     * "你好。世界" 结尾没有终止符 —— 如果只数终止符，用户会看到"1 句"，
     * 但屏幕上明明有两句话。这条守的是"末尾那段也算一句"。
     */
    @Test
    fun textStatsAll_tailWithoutTerminatorCountsAsASentence() {
        val s = Toolbox.textStatsAll("你好。世界")
        assertEquals("末尾没有句号的半句也要算一句：${s.toList()}", 2, s[6])
    }

    /**
     * 高频字词 Top5 与大小写归一：[Toolbox.textTopFreq]（`Toolbox.java:361-380`）。
     *
     * 中文那句里"的"恰好出现 3 次，是唯一的最高频字 —— 排序错的话它会掉到后面去。
     * 大小写那条守 `The` / `THE` / `the` 合并成一个词；
     * 如果没归一，用户看到的"高频词"会被同一个词的三种写法占满，等于没统计。
     */
    @Test
    fun textTopFreq_ranksByCountAndFoldsCase() {
        val top = Toolbox.textTopFreq("的春风 的天空 我和他 the The CAT the 的")
        assertTrue("最高频汉字必须排第一：$top", top.contains("高频汉字：的×3"))
        assertTrue("最高频单词必须排第一：$top", top.contains("the×3"))

        val folded = Toolbox.textTopFreq("The THE the")
        assertTrue("The / THE / the 要合并成 the×3：$folded", folded.contains("the×3"))
    }
}
