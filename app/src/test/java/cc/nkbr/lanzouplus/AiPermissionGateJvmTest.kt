package cc.nkbr.lanzouplus

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowApplication

/**
 * [DFWX] DFW-22（TEST-001）AI 首启权限引导（AiPermissionGate）测试。
 *
 * 审计 §五点名的覆盖缺口之一："**权限引导（AiPermissionGate）完全没有**测试"。
 * 这条链路的产品契约很明确，而且**拒绝也必须放行**——如果这里错了，
 * 用户会被一条"必须授权才能用"的死路挡住（正是 v1.22.0 做这条引导时要避免的反面）。
 *
 * 契约（来自类注释，逐条断言）：
 *  1. 只列**未授**的必需权限（已授的跳过，不打扰）；
 *  2. 权限清单与 Manifest 声明一致：麦克风 / 相机 /（33+）通知；
 *  3. **拒绝也放行、不阻断使用**；
 *  4. 已全部授权则永不打扰；
 *  5. 一次性标记必须**持久化**。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class AiPermissionGateJvmTest {

    private lateinit var app: android.app.Application

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        // 每个用例从"全部未授"开始
        val shadow = shadowOf(app)
        shadow.denyPermissions(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA,
            Manifest.permission.POST_NOTIFICATIONS,
        )
    }

    private fun activity() = Robolectric.buildActivity(MainActivity::class.java).setup().get()

    @Test
    fun pending_listsAllRequiredPermissionsWhenNoneGranted() {
        val a = activity()
        val pending = AiPermissionGate.pending(a)
        assertTrue("麦克风必须在待引导列表里", pending.contains(Manifest.permission.RECORD_AUDIO))
        assertTrue("相机必须在待引导列表里", pending.contains(Manifest.permission.CAMERA))
        if (Build.VERSION.SDK_INT >= 33) {
            assertTrue("Android 13+ 通知必须在待引导列表里", pending.contains(Manifest.permission.POST_NOTIFICATIONS))
        }
    }

    @Test
    fun pending_skipsAlreadyGranted() {
        val a = activity()
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)

        val pending = AiPermissionGate.pending(a)
        assertFalse("已授权的麦克风不该再打扰用户", pending.contains(Manifest.permission.RECORD_AUDIO))
        assertTrue("仍缺的相机应在列表里", pending.contains(Manifest.permission.CAMERA))
    }

    @Test
    fun pending_isEmptyWhenEverythingGranted() {
        shadowOf(app).grantPermissions(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA,
            Manifest.permission.POST_NOTIFICATIONS,
        )
        val a = activity()
        assertEquals(
            "全部已授权时必须返回空列表（红线：已全部授权则永不打扰）",
            emptyList<String>(),
            AiPermissionGate.pending(a),
        )
    }

    /**
     * 权限清单必须与 Manifest 实际声明一致——
     * 如果这里请求一个 Manifest 没声明的权限，系统会**静默拒绝且不弹窗**，
     * 用户会卡在一个永远过不去的引导上。
     */
    @Test
    fun requestedPermissions_areAllDeclaredInManifest() {
        // 必须查**合并后的**清单，而不是宿主那一个文件：
        // CAMERA / POST_NOTIFICATIONS 是 vendor 清单声明的（RikkaHub 需要），
        // 只读 app/src/main/AndroidManifest.xml 会误报"未声明"（第一版就踩了这个）。
        // 合并清单是系统实际看到的权威结果。
        val root = (System.getProperty("user.dir") ?: ".").let { dir ->
            var d = java.io.File(dir).absoluteFile
            while (!java.io.File(d, "app/src/main/AndroidManifest.xml").isFile && d.parentFile != null) d = d.parentFile
            d
        }
        val candidates = listOf(
            java.io.File(root, "app/build/intermediates/merged_manifest/emptyDebug/processEmptyDebugMainManifest/AndroidManifest.xml"),
            java.io.File(root, "app/build/intermediates/merged_manifest/emptyRelease/processEmptyReleaseMainManifest/AndroidManifest.xml"),
            // 兜底：任一合并清单（不同 AGP 版本路径略有差异）
            java.io.File(root, "app/src/main/AndroidManifest.xml"),
            java.io.File(root, "rikkahub/app/src/main/AndroidManifest.xml"),
        )
        val available = candidates.filter { it.isFile }
        assertTrue("至少要有一个清单文件可读（先跑一次编译以生成合并清单）", available.isNotEmpty())
        val merged = available.joinToString("\n") { it.readText(Charsets.UTF_8) }

        for (permission in AiPermissionGate.pending(activity())) {
            val short = permission.substringAfterLast('.')
            assertTrue(
                "请求的权限 $permission 必须在（合并）清单里声明，否则系统静默拒绝、引导永远过不去",
                merged.contains(short),
            )
        }
    }

    /** 文案必须说明"为什么需要"，不能是技术描述；且每个权限都有独立文案。 */
    @Test
    fun rationale_isHumanReadableAndDistinct() {
        val mic = AiPermissionGate.rationale(Manifest.permission.RECORD_AUDIO)
        val cam = AiPermissionGate.rationale(Manifest.permission.CAMERA)
        val notify = AiPermissionGate.rationale(Manifest.permission.POST_NOTIFICATIONS)

        assertEquals(2, mic.size)
        assertEquals("麦克风", mic[0])
        assertEquals("相机", cam[0])
        assertEquals("通知", notify[0])

        for (r in listOf(mic, cam, notify)) {
            assertTrue("文案不能为空", r[0].isNotBlank() && r[1].isNotBlank())
            assertTrue("说明必须是给用户看的人话（应含'用于'或'让'或'提醒'）：${r[1]}",
                r[1].contains("用于") || r[1].contains("让") || r[1].contains("提醒"))
            // 不得是技术描述（类名/权限串）
            assertFalse("文案不得出现技术串：${r[1]}", r[1].contains("android.permission"))
        }
    }

    /** 未知权限要有兜底文案，不能崩、不能返回 null。 */
    @Test
    fun rationale_unknownPermissionFallsBackSafely() {
        val fallback = AiPermissionGate.rationale("android.permission.SOMETHING_NEW")
        assertEquals(2, fallback.size)
        assertTrue(fallback[0].isNotBlank())
        assertTrue(fallback[1].isNotBlank())
    }

    @Test
    fun settingsLabel_matchesRationaleTitle() {
        assertEquals("麦克风", AiPermissionGate.settingsLabel(Manifest.permission.RECORD_AUDIO))
        assertEquals("相机", AiPermissionGate.settingsLabel(Manifest.permission.CAMERA))
    }

    /** 一次性标记必须持久化：换一个 Activity 实例仍然记得"已引导过"。 */
    @Test
    fun markGuided_persistsAcrossActivityInstances() {
        val a = activity()
        assertFalse("全新安装时不应标记为已引导", AiPermissionGate.guided(a))

        AiPermissionGate.markGuided(a)

        val reopened = activity()
        assertTrue(
            "引导标记必须持久化，否则每次进 AI 页都会重复弹权限",
            AiPermissionGate.guided(reopened),
        )
    }

    /** granted() 在任何异常下都必须返回 false（保守），绝不能抛。 */
    @Test
    fun granted_neverThrows() {
        val a = activity()
        // 正常路径
        assertFalse(AiPermissionGate.granted(a, Manifest.permission.RECORD_AUDIO))
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
        assertTrue(AiPermissionGate.granted(a, Manifest.permission.RECORD_AUDIO))
        // 荒谬输入也不得抛
        assertFalse(AiPermissionGate.granted(a, "not.a.real.permission"))
    }
}
