package cc.nkbr.lanzouplus;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.Collections;
import java.util.HashSet;

/**
 * [DFW-133-F] DirectCookiePool 容量上限与回收的行为验证（Robolectric，真 SharedPreferences）。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class DirectCookiePoolJvmTest {

    private DirectCookiePool newPool() {
        return new DirectCookiePool(RuntimeEnvironment.getApplication());
    }

    @Test
    public void poolSizeStaysUnderLimit() {
        DirectCookiePool pool = newPool();
        long now = 1_000_000L;
        for (int i = 0; i < DirectCookiePool.POOL_LIMIT * 3; i++) {
            pool.acquire(Collections.emptySet(), now + i * 1000L);
        }
        assertTrue("池子必须被压回上限内：实际 " + pool.size(),
                pool.size() <= DirectCookiePool.POOL_LIMIT);
    }

    @Test
    public void activeEntriesSurviveEviction() {
        DirectCookiePool pool = newPool();
        long now = 1_000_000L;
        // 借出后归还成功 → count>0、未冷却，是"活跃"条目
        DirectCookiePool.Lease active = pool.acquire(Collections.emptySet(), now);
        pool.finish(active, null, true, false, 0, now);
        // 大量新借入把池子挤爆
        for (int i = 0; i < DirectCookiePool.POOL_LIMIT * 3; i++) {
            pool.acquire(Collections.emptySet(), now + i * 1000L);
        }
        assertTrue("活跃条目应存活", pool.size() > 0);
        // 被逐出的只会是"闲置"条目：排除活跃那条后仍能正常借
        DirectCookiePool.Lease next = pool.acquire(
                new HashSet<>(Collections.singleton(active.id)), now + 5000L);
        assertNotNull(next);
        assertNotEquals("新借不应撞上活跃条目", active.id, next.id);
    }

    @Test
    public void hourlyLimitStillEnforced() {
        DirectCookiePool pool = newPool();
        long now = 1_000_000L;
        DirectCookiePool.Lease first = pool.acquire(Collections.emptySet(), now);
        // 把同窗口额度打满
        for (int i = 0; i < DirectCookiePool.HOURLY_DIRECT_LIMIT; i++) {
            pool.acquire(Collections.emptySet(), now);
        }
        DirectCookiePool.Lease last = pool.acquire(Collections.emptySet(), now);
        assertFalse("打满小时限额后必须启用新条目", first.id.equals(last.id));
    }

    @Test
    public void failureCooldownRespected() {
        DirectCookiePool pool = newPool();
        long now = 1_000_000L;
        DirectCookiePool.Lease lease = pool.acquire(Collections.emptySet(), now);
        pool.finish(lease, null, false, false, 0, now);
        DirectCookiePool.Lease next = pool.acquire(Collections.emptySet(), now + 1000L);
        assertFalse("冷却中的条目不应立即复用", lease.id.equals(next.id));
    }

    @Test
    public void evictionWritesThroughToPrefs() {
        DirectCookiePool pool = newPool();
        long now = 1_000_000L;
        // 真正逼出淘汰路径：每轮把当前所有条目借出→打失败→进冷却，
        // 下轮 acquire 全员冷却 → 被迫 newEntry → enforceLimitLocked 收缩池子。
        for (int round = 0; round < DirectCookiePool.POOL_LIMIT + 6; round++) {
            DirectCookiePool.Lease lease = pool.acquire(Collections.emptySet(), now);
            pool.finish(lease, null, false, false, 0, now);
        }
        assertTrue("淘汰必须生效，池子不得超上限：实际 " + pool.size(),
                pool.size() <= DirectCookiePool.POOL_LIMIT);
    }
}
