package cc.nkbr.lanzouplus

import android.content.ComponentCallbacks2
import android.os.Looper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * [DFWX] DFW-40 图标失败记忆（负缓存）回归。
 *
 * ## 修的是什么
 * 原实现的失败路径只打一行日志、**什么都不记**：于是 404/超时的图标
 * **每次滚动回可视区都会重新发起网络请求**。弱网下每个坏图标都要等一次 3 秒连接超时，
 * 列表滚动因此发顿，而且同一个坏 URL 可能被反复请求几十次。
 *
 * 修法：失败后在 TTL（5 分钟）内**不再重试**，且该记忆是**有界**的
 * （LruCache 限容 256，不会无界增长），并随 `onTrimMemory` 释放。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class ImageFailureCacheJvmTest {

    companion object {
        @BeforeClass @JvmStatic fun silenceAutoImport() { MainActivity.LIBRARY_AUTO_IMPORT = false }
    }

    private fun activity(): MainActivity {
        val a = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        shadowOf(Looper.getMainLooper()).idle()
        return a
    }

    @Test
    fun freshUrlIsNotTreatedAsFailed() {
        val a = activity()
        assertFalse("没失败过的 URL 不应被跳过", a.imageRecentlyFailed("https://cdn.example.com/ok.png"))
    }

    @Test
    fun failedUrlIsSkippedWithinTtl() {
        val a = activity()
        a.rememberImageFailure("https://cdn.example.com/broken.png")
        assertTrue(
            "刚失败过的 URL 必须被跳过（否则每次滚动都重新请求，弱网下反复等 3 秒超时）",
            a.imageRecentlyFailed("https://cdn.example.com/broken.png"),
        )
    }

    /** TTL 过期后要允许再试——否则服务端恢复了图也永远拿不到。 */
    @Test
    fun failureExpiresAfterTtl() {
        val a = activity()
        a.rememberImageFailure("https://cdn.example.com/maybe.png")
        // 直接把时间戳改老，模拟 TTL 过期
        a.imageFailures.put(
            "https://cdn.example.com/maybe.png",
            System.currentTimeMillis() - MainActivity.IMAGE_FAILURE_TTL_MS - 1000,
        )
        assertFalse("TTL 过期后必须允许重试", a.imageRecentlyFailed("https://cdn.example.com/maybe.png"))
        // 过期项应被顺手清掉
        assertEquals("读取到过期项后应移除它", null, a.imageFailures.get("https://cdn.example.com/maybe.png"))
    }

    /** 记忆必须是有界的：塞远超容量的条目也不能无界增长。 */
    @Test
    fun failureMemoryIsBounded() {
        val a = activity()
        repeat(2000) { a.rememberImageFailure("https://cdn.example.com/f$it.png") }
        assertTrue(
            "失败记忆必须有界（LruCache 限容），实际 ${a.imageFailures.size()}",
            a.imageFailures.size() <= 256,
        )
    }

    @Test
    fun nullUrlIsSafe() {
        val a = activity()
        assertFalse(a.imageRecentlyFailed(null))
        a.rememberImageFailure(null)
        assertTrue("记 null 不应抛，也不应污染缓存", a.imageFailures.size() == 0)
    }

    /** 内存紧张时应一并释放失败记忆（与 DFW-14 的 onTrimMemory 联动）。 */
    @Test
    fun trimMemoryClearsFailureMemory() {
        val a = activity()
        repeat(50) { a.rememberImageFailure("https://cdn.example.com/t$it.png") }
        assertTrue(a.imageFailures.size() > 0)

        a.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_COMPLETE)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals("TRIM_MEMORY_COMPLETE 必须一并清掉失败记忆", 0, a.imageFailures.size())
    }

    /** 不同 URL 必须互不影响（不能因为一个坏图跳过别的图）。 */
    @Test
    fun failuresArePerUrl() {
        val a = activity()
        a.rememberImageFailure("https://cdn.example.com/bad.png")
        assertTrue(a.imageRecentlyFailed("https://cdn.example.com/bad.png"))
        assertFalse("另一个 URL 不应被牵连", a.imageRecentlyFailed("https://cdn.example.com/good.png"))
    }

    /**
     * **接线**：机制存在不等于被用上。
     * `requestImage` 必须在发起网络请求**之前**查失败记忆，并在解不出图时写入。
     * 只测 `imageRecentlyFailed()` 本身的话，把调用点删掉测试照样全绿——线上却依旧反复重试。
     */
    @Test
    fun requestImage_actuallyConsultsAndRecordsFailureMemory() {
        val source = java.io.File(
            (System.getProperty("user.dir") ?: ".").let { dir ->
                var d = java.io.File(dir).absoluteFile
                while (!java.io.File(d, "src/main/java/cc/nkbr/lanzouplus/MainActivity.java").isFile && d.parentFile != null) d = d.parentFile
                d
            },
            "src/main/java/cc/nkbr/lanzouplus/MainActivity.java",
        ).readText(Charsets.UTF_8)

        val start = source.indexOf("void requestImage(String url,ImageView view)")
        assertTrue("必须能找到 requestImage", start >= 0)
        val body = source.substring(start, minOf(source.length, start + 2500))

        assertTrue(
            "requestImage 必须在发请求前查询失败记忆（否则负缓存形同虚设、坏图仍反复重试）",
            body.contains("imageRecentlyFailed(url)"),
        )
        assertTrue(
            "requestImage 必须在解图失败时写入失败记忆",
            body.contains("rememberImageFailure(url)"),
        )
        // 短路必须发生在建立连接之前
        val guard = body.indexOf("imageRecentlyFailed(url)")
        val connect = body.indexOf("openConnection()")
        assertTrue(
            "失败记忆的短路必须早于建立连接（否则照样会等一次超时）：guard=\$guard connect=\$connect",
            connect < 0 || guard < connect,
        )
    }

    /** onTrimMemory 必须同时释放失败记忆（与 DFW-14 联动）。 */
    @Test
    fun trimHandlerReferencesFailureMemory() {
        val source = java.io.File(
            (System.getProperty("user.dir") ?: ".").let { dir ->
                var d = java.io.File(dir).absoluteFile
                while (!java.io.File(d, "src/main/java/cc/nkbr/lanzouplus/MainActivity.java").isFile && d.parentFile != null) d = d.parentFile
                d
            },
            "src/main/java/cc/nkbr/lanzouplus/MainActivity.java",
        ).readText(Charsets.UTF_8)
        val start = source.indexOf("public void onTrimMemory(int level)")
        assertTrue("必须能找到 onTrimMemory", start >= 0)
        val body = source.substring(start, minOf(source.length, start + 1800))
        assertTrue("onTrimMemory 必须清理失败记忆", body.contains("imageFailures.evictAll()"))
    }
}
