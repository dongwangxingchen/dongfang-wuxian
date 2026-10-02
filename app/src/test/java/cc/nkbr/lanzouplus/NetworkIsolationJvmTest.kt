package cc.nkbr.lanzouplus

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.lang.reflect.InvocationTargetException

/**
 * [DFW-124 2026-10-02] **测试里不许打真实网络**的守卫。
 *
 * ## 它守的是什么
 *
 * `MainActivity` 启动时有三个异步任务会去读服务器：
 * `MainActivity.java:305`（查更新）、`:309`（维护公告）、`:310`（公告与红点），
 * 都是 `ui.post(...)`。在 Robolectric 里它们跑在**真实 JVM** 上，
 * 所以请求**真的会发到生产服务器**（`39.106.33.135`）。
 *
 * 后果不是"慢一点"，是**测试不确定**：回调什么时候落地取决于本机网速，
 * 而回调会**直接改被测对象的字段**——`maybeFetchNotices()` 的网络回调会走到
 * `maybePopupNotices()` → `showNoticeDialog()` → 覆盖 `MainActivity.noticeDialog`，
 * 于是 `NoticeFlowJvmTest` 的 `assertNull(a.noticeDialog)` 就会红。
 *
 * 2026-10-02 实测：同一份代码，`NoticeFlowJvmTest` 跑 6 次红 1 次（约 17%），
 * 而且**每次红的用例还不一样** —— 这正是 DFW-113 记录的"门禁随机变红"的真身。
 *
 * ## 为什么第二条用例非写不可（防假绿）
 *
 * 第一条「测试里 `fetch()` 应该报不可达」**单独看是假绿**：跑测试的机器本来就没网的话，
 * 不写守卫它也是绿的。真正有鉴别力的是第二条——它直接戳**咽喉方法**
 * `RemoteConfigClient.get()`，断言它是被守卫**主动拒绝**的，而不是"碰巧连不上"：
 * 报错信息里必须带 `DFW-124` 标记。
 *
 * **反向探针**：把 `RemoteConfigClient.get()` 里那行守卫删掉，这两条都会红——
 * `get()` 会真的连上生产服务器并返回 JSON；`fetch()` 会报 `reachable=true`。
 *
 * ## 为什么必须挂 Robolectric（这不是"顺手加的"）
 *
 * 第一版写成了**纯 JVM 测试**，结果反向探针的红是
 * `Method toString in org.json.JSONObject not mocked` —— 那说明 `org.json` 走的是 AGP 的桩。
 * 桩一崩，`fetch()` 内部 `catch (Exception ignored)` 就把它吞掉并返回"不可达"，
 * 于是第二条用例**删掉守卫也照样绿**，是个假绿。
 * 挂上 `RobolectricTestRunner` 之后 `org.json` 才是真实现，
 * "删守卫 → `reachable=true` → 红" 这条因果才成立。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class NetworkIsolationJvmTest {

    /**
     * 咽喉方法 `RemoteConfigClient.get(String)` 在测试里必须**被守卫拒绝**。
     *
     * 用反射是因为它是 `private`：这条测的正是"守卫在不在"，走公开 API 看不到它。
     * 本项目已有反射测试的先例（`RemoteConfigClientJvmTest.kt:37`）。
     */
    @Test
    fun theNetworkChokePoint_isActivelyRefused_notJustUnreachable() {
        val cls = Class.forName("cc.nkbr.lanzouplus.RemoteConfigClient")
        val get = cls.getDeclaredMethod("get", String::class.java)
        get.isAccessible = true

        var returned: Any? = null
        var thrown: Throwable? = null
        try {
            // 用真实接口地址：守卫若失效，这一发就真的打到生产服务器上去了。
            returned = get.invoke(null, "http://39.106.33.135/pb/api/collections/notice/records?perPage=5")
        } catch (wrapper: InvocationTargetException) {
            thrown = wrapper.cause ?: wrapper
        } catch (other: Throwable) {
            thrown = other
        }

        assertNull("测试里 get() 绝不能真把请求发出去。它返回了：$returned", returned)
        assertNotNull("测试里 get() 必须抛出异常（当作「服务器不可达」处理）", thrown)
        assertTrue(
            "必须是被 DFW-124 的守卫**主动拒绝**的，而不是碰巧连不上 —— " +
                "否则跑测试的机器一没网，这条就假绿了。实际抛出：$thrown",
            thrown!!.message?.contains("DFW-124") == true,
        )
    }

    /**
     * 上层入口 `fetch()` 在测试里必须报「不可达」。
     *
     * `fetch(Raw)` 总失败时返回的是 `Snapshot(reachable=false, …)` 而**不是 null**
     * （见 `RemoteConfigClient.java:373`），这正是线上"服务器挂了"时走的那条 fail-open 路径——
     * 所以测试看到的行为与生产语义完全一致，不是特例分支。
     */
    @Test
    fun fetch_reportsUnreachable_insteadOfReadingProduction() {
        val snapshot = RemoteConfigClient.fetch()
        assertFalse(
            "测试里 fetch() 必须报「不可达」。reachable=true 说明它真的读到了生产服务器的数据：" +
                "公告 ${snapshot.notices().size} 条 / 版本 ${snapshot.release()?.versionName}",
            snapshot.reachable,
        )
    }
}
