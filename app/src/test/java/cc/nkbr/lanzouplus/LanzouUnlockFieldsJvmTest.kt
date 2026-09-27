package cc.nkbr.lanzouplus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [DFWX] 蓝奏目录密码解锁字段回归（T6-A）。
 *
 * 背景：内置 84 个软件库中 50 个带访问密码，用户实测"一直加载中 → 解析失败"。
 * 根因是 [LanzouCore.formValues] 抓不到页面 JS 里的 `lx`/`fid`/`pg`，导致提交给
 * `/filemoreajax.php` 的字段不完整，服务端回 `{"zt":4,"info":"请刷新，重试0"}`。
 *
 * 页面真实写法（2026-09-26 抓取自 yoyodadada.lanzouw.com/b01dk67eh）：
 *   'lx':2,          <- 裸数字，无引号
 *   'fid':5436127,   <- 裸数字，无引号
 *   'pg':pgs,        <- 变量引用，而 pgs 是 `pgs =1;`（无 var 关键字）
 *
 * 用例用页面原文片段，防止再次退化。
 */
class LanzouUnlockFieldsJvmTest {

    /** 页面 `file()` 里 data 对象的原文片段（缩进/换行/裸数字都照抄）。 */
    private val pageJs = """
        var imx5mh ='合集-360工具';
        var pwd;
        var pgs;
        var ib0hiv = '1790404524';
        var _gytko = '6a3e5ca53dd03292b6ef510561222598';
        pgs =1;
        function file(){
            var pwd = document.getElementById('pwd').value;
            jQuery.ajax({
                type : 'post',
                url : '/filemoreajax.php?file=5436127',
                data : {
                'lx':2,
                'fid':5436127,
                'uid':'140470',
                'puid':'VWQAY1o3UDAIaQttAHkCMgRpVGgHaAM1CzwDNlYzUWIHMwd9XjEGYlI2A2MBZVJlUjgOPQ_c_c',
                'pg':pgs,
                'rep':'0',
                't':ib0hiv,
                'k':_gytko,
                'up':1,
                            'ls':1,
                'pwd':pwd            },
                dataType : 'json'
            });
        }
    """.trimIndent()

    private fun formValues(html: String): Map<String, String> {
        val method = LanzouCore::class.java.getDeclaredMethod("formValues", String::class.java)
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return method.invoke(null, html) as Map<String, String>
    }

    @Test
    fun extractsBareNumberFields() {
        val fields = formValues(pageJs)
        // 裸数字（无引号）必须能取到 —— 修复前这两项直接丢失。
        assertEquals("lx", "2", fields["lx"])
        assertEquals("fid", "5436127", fields["fid"])
    }

    @Test
    fun resolvesVariableAssignedWithoutVarKeyword() {
        // `pgs =1;` 没有 var 前缀；`'pg':pgs` 必须解析成 1。
        assertEquals("pg", "1", formValues(pageJs)["pg"])
    }

    @Test
    fun keepsQuotedAndIdentifierFields() {
        val fields = formValues(pageJs)
        assertEquals("uid", "140470", fields["uid"])
        assertEquals("rep", "0", fields["rep"])
        assertEquals("up", "1", fields["up"])
        assertEquals("ls", "1", fields["ls"])
        assertEquals("t", "1790404524", fields["t"])
        assertEquals("k", "6a3e5ca53dd03292b6ef510561222598", fields["k"])
    }

    /**
     * 解锁请求的字段集必须覆盖服务端要求的三个关键项。
     * 实测：只补 lx+fid 得 zt=2（"没有了"，仅部分）；再加 pg 才 zt=1（33 项）。
     */
    @Test
    fun unlockPayloadCarriesRequiredFields() {
        val fields = formValues(pageJs)
        assertTrue("缺少 lx 会导致 zt=4（请刷新，重试0）", fields.containsKey("lx"))
        assertTrue("缺少 fid 会导致 zt=4", fields.containsKey("fid"))
        assertTrue("缺少 pg 会退化为 zt=2（列表不完整）", fields.containsKey("pg"))
    }

    /** 字段名不得被后续页面模板改名后静默丢弃。 */
    @Test
    fun doesNotInventFields() {
        val fields = formValues("<html><body>no js here</body></html>")
        assertTrue("空页面不应解析出任何字段", fields.isEmpty())
    }
}
