package me.rerere.rikkahub.dfwx

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.dfwx.SettingsDataGuard

/**
 * [DFWX AI-004] 内置 AI 渠道整体移除（用户拍板 2026-09-28）：
 * 不再内置任何 API；后续免费额度只在官方群聊发放，由用户自行在 AI 设置里填。
 *
 * 本文件承担两件事：
 *  1. **新装不播种**：v1.8.1 起的 BuiltinProviderSeeder 已删除，构建期 resValue 注入已移除；
 *  2. **存量一次清理**：老版本装过并播过"智能中转(内置)"渠道的用户，升级后把这条渠道移除，
 *     并修复指向它的悬空模型引用。
 *
 * 安全边界（严格照 decisions.md #7/#10 与 AI-003 保护层）：
 *  - **只删可证明身份的**：名称 + baseUrl + 模型列表逐字段一致才算"原样播种"。用户改过任何一处
 *    （改名/换地址/加删模型）就视为用户自己的渠道，一律保留；
 *  - Key 不做校验也不读取——若曾因 Key 轮换而不同，也无法证明身份，那就保留（宁可留不可误删）；
 *  - 聊天历史在 Room 里，本迁移只动 settings.providers 与模型引用字段，结构上不可能碰到聊天；
 *  - 只跑一次（SharedPreferences 标记），且失败不写标记以便下次重试。
 *
 * 同步上游时需重放（新增自有文件，不在上游补丁范围内）。
 */
object DfwxBuiltinProviderCleanup {
    private const val TAG = "DfwxAi004"
    private const val PREFS = "dfwx_seed"
    private const val KEY_DONE = "ai004_removed"

    /** v1.8.1–v1.22.6 期间播种写入的身份事实（含历史地址，避免漏删）。
     *  历史地址来自该区间的构建注入：aizhongzhuan.cc 中转站 + glm-5.3 单模型。 */
    private val LEGACY_SEEDED_IDENTITIES = listOf(
        SettingsDataGuard.SeededProviderIdentity(
            name = "智能中转(内置)",
            baseUrl = "https://www.aizhongzhuan.cc/v1",
            modelIds = listOf("glm-5.3"),
        ),
    )

    fun removeLegacySeedIfNeeded(context: Context, scope: CoroutineScope, store: SettingsStore) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_DONE, false)) return
        scope.launch(Dispatchers.IO) {
            runCatching {
                val before = store.settingsFlowRaw.first()
                if (before.providers.isEmpty()) {
                    prefs.edit().putBoolean(KEY_DONE, true).apply()
                    return@launch
                }
                val (after, report) = SettingsDataGuard.removeSeededProviders(before, LEGACY_SEEDED_IDENTITIES)
                if (report.removedProviderIds.isEmpty()) {
                    // 没有可证明的内置渠道（未播过 / 用户已改过 / 已被用户删除）：不写 settings，直接记完成
                    prefs.edit().putBoolean(KEY_DONE, true).apply()
                    return@launch
                }
                store.update(after)
                prefs.edit().putBoolean(KEY_DONE, true).apply()
                // 只记录数量与字段名，绝不输出 Key 或 baseUrl
                Log.i(
                    TAG,
                    "removed ${report.removedProviderIds.size} legacy builtin provider(s), " +
                        "kept ${report.keptProviderCount}, repaired ${report.repairedReferenceFields.size} reference(s)"
                )
            }.onFailure {
                Log.e(TAG, "legacy builtin provider cleanup failed", it)
            }
        }
    }
}
