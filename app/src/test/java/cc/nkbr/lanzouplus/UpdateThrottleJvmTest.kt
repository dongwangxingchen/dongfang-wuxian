package cc.nkbr.lanzouplus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [DFWX] DFW-29（ARCH-001）第三步：更新节流策略**直测**。
 *
 * DFW-7 引入这条策略时，判定与偏好读写内联在 MainActivity 里，
 * "24 小时节流"**只能通过启动 Activity 间接验证**，边界情况（时钟回拨、存储异常）
 * 根本没法构造。抽成 `UpdateThrottle` + `Store` 接口后，这里用内存实现逐条钉死。
 */
class UpdateThrottleJvmTest {

    /** 内存版存储：可注入任意时间戳，也能模拟异常。 */
    private class FakeStore(var value: Long = 0L, val failOnWrite: Boolean = false) : UpdateThrottle.Store {
        var writes = 0
        override fun lastCheckAt(): Long = value
        override fun setLastCheckAt(at: Long) {
            if (failOnWrite) throw RuntimeException("模拟存储失败")
            writes++
            value = at
        }
    }

    private val now = 1_700_000_000_000L

    // ---------- 基本节流语义 ----------

    @Test
    fun firstRun_isDue() {
        val t = UpdateThrottle(FakeStore(0L))
        assertTrue("从没查过时必须允许检查", t.isDue(now))
    }

    @Test
    fun withinWindow_isNotDue() {
        val t = UpdateThrottle(FakeStore(now - 60_000L))          // 1 分钟前查过
        assertFalse("24h 窗口内不得再自动检查", t.isDue(now))
    }

    @Test
    fun exactlyAtInterval_isDue() {
        val t = UpdateThrottle(FakeStore(now - UpdateThrottle.INTERVAL_MS))
        assertTrue("正好到达间隔时应放行（边界取 >=）", t.isDue(now))
    }

    @Test
    fun justBeforeInterval_isNotDue() {
        val t = UpdateThrottle(FakeStore(now - UpdateThrottle.INTERVAL_MS + 1))
        assertFalse("差 1 毫秒仍应在窗口内", t.isDue(now))
    }

    @Test
    fun afterWindow_isDue() {
        val t = UpdateThrottle(FakeStore(now - UpdateThrottle.INTERVAL_MS * 2))
        assertTrue("超过窗口必须放行", t.isDue(now))
    }

    // ---------- 时钟回拨 / 脏数据（真实边界） ----------

    /**
     * 记录的时间"来自未来"（用户改过系统时间、或从别的设备恢复过数据）：
     * 差值会是负数。若直接比较 `elapsed < INTERVAL`，这条脏记录会**永久锁死**自动更新。
     * 所以实现必须把它当作"可查"。
     */
    @Test
    fun futureTimestamp_doesNotLockOutForever() {
        val t = UpdateThrottle(FakeStore(now + 365L * 24 * 3600 * 1000))   // 记录来自一年后
        assertTrue(
            "记录时间在未来时必须仍允许检查，否则一条脏数据会永久锁死自动更新",
            t.isDue(now),
        )
    }

    @Test
    fun negativeTimestamp_isTreatedAsNeverChecked() {
        val t = UpdateThrottle(FakeStore(-12345L))
        assertTrue("负数时间戳应视为'从没查过'", t.isDue(now))
    }

    // ---------- 写入 ----------

    @Test
    fun remember_persistsTimestamp() {
        val store = FakeStore()
        val t = UpdateThrottle(store)
        t.remember(now)
        assertEquals("写入后必须能读回", now, t.lastCheckAt())
        assertEquals(1, store.writes)
    }

    /** 记时间失败不得影响使用（最坏只是多查一次，不能崩）。 */
    @Test
    fun remember_survivesStoreFailure() {
        val store = FakeStore(failOnWrite = true)
        val t = UpdateThrottle(store)
        // 生产实现吞掉异常；FakeStore 会抛，所以这里断言"抛出的正是存储异常"，
        // 用来确认我们确实走到写入路径（生产侧在 forContext 的 Store 里 try/catch）。
        var threw = false
        try {
            t.remember(now)
        } catch (e: RuntimeException) {
            threw = true
        }
        assertTrue("应确实走到写入路径", threw)
    }

    /** 生产构造器必须绑定到 `update_check` 偏好（键名不能漂移，否则节流失效）。 */
    @Test
    fun productionStoreUsesExpectedPreferenceKeys() {
        val source = java.io.File(
            (System.getProperty("user.dir") ?: ".").let { dir ->
                var d = java.io.File(dir).absoluteFile
                while (!java.io.File(d, "src/main/java/cc/nkbr/lanzouplus/UpdateThrottle.java").isFile && d.parentFile != null) d = d.parentFile
                d
            },
            "src/main/java/cc/nkbr/lanzouplus/UpdateThrottle.java",
        ).readText(Charsets.UTF_8)
        assertTrue("必须使用 update_check 偏好名", source.contains("MainActivity.UPDATE_CHECK_PREFS"))
        assertTrue("必须使用 last_auto_ms 键（与既有数据兼容，改名会让老用户重新开始计时）",
            source.contains("\"last_auto_ms\""))
    }

    /** 间隔常量必须与 MainActivity 的既有常量一致（避免两份真相源）。 */
    @Test
    fun intervalMatchesMainActivityConstant() {
        assertEquals(MainActivity.AUTO_UPDATE_CHECK_INTERVAL_MS, UpdateThrottle.INTERVAL_MS)
        assertEquals(24L * 60 * 60 * 1000, UpdateThrottle.INTERVAL_MS)
    }
}
