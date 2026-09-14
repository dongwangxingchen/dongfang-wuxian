package me.rerere.rikkahub.dfwx

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.data.datastore.SettingsStore

/**
 * [DFWX PATCH P11] 东方无限内置默认渠道播种器。
 *
 * 首次启动时，若设置里还没有本内置渠道，则把宿主注入的默认中转站
 * （res: dfwx_default_ai_url / dfwx_default_ai_key / dfwx_default_ai_model，
 *  由宿主 :app 的 flavor resValue 在构建期写入，Key 不进源码）种成：
 *  - 一个 OpenAI 兼容渠道（名称"智能中转(内置)"）
 *  - 一个 glm-5.3 模型（官方规格：1M 上下文 / 128K 最大输出 / 纯文本 / 支持推理与函数调用，
 *    docs.z.ai/guides/llm/glm-5.3；本 app 无 token 级上下文字段，上下文按消息条数截断，默认 0=不限）
 *  - 全局默认模型与快速模型（标题生成等）都指向它；默认助手 maxTokens=128000（模型最大输出）
 *
 * 幂等与用户主权：SharedPreferences("dfwx_seed") 标记只播一次；用户删除该渠道后不会被复活；
 * 已存在同 baseUrl 渠道（老数据）也不重复种。同步上游时需重放（见 rikkahub/PATCHES.md）。
 */
object BuiltinProviderSeeder {
    private const val TAG = "DfwxSeeder"

    fun seedIfNeeded(context: Context, scope: CoroutineScope, store: SettingsStore) {
        val prefs = context.getSharedPreferences("dfwx_seed", Context.MODE_PRIVATE)
        if (prefs.getBoolean("done", false)) return
        val url = readRes(context, "dfwx_default_ai_url")
        val key = readRes(context, "dfwx_default_ai_key")
        val modelId = readRes(context, "dfwx_default_ai_model")
        if (url.isBlank() || key.isBlank() || modelId.isBlank()) {
            Log.w(TAG, "host did not inject default ai config, skip seeding")
            return
        }
        scope.launch(Dispatchers.IO) {
            runCatching {
                val settings = store.settingsFlowRaw.first()
                val exists = settings.providers.any {
                    it is ProviderSetting.OpenAI && it.baseUrl == url
                }
                if (exists) {
                    prefs.edit().putBoolean("done", true).apply()
                    return@launch
                }
                val model = Model(
                    modelId = modelId,
                    displayName = modelId,
                    abilities = listOf(ModelAbility.TOOL, ModelAbility.REASONING),
                )
                val provider = ProviderSetting.OpenAI(
                    name = "智能中转(内置)",
                    apiKey = key,
                    baseUrl = url,
                    models = listOf(model),
                )
                store.update(
                    settings.copy(
                        providers = settings.providers + provider,
                        chatModelId = model.id,
                        fastModelId = model.id,
                        themeId = "dfwx", // [DFWX PATCH P17] 默认套用东方无限品牌配色，与宿主观感一致
                        assistants = settings.assistants.mapIndexed { index, assistant ->
                            if (index == 0) {
                                assistant.copy(chatModelId = model.id, maxTokens = 128000)
                            } else {
                                assistant
                            }
                        },
                    )
                )
                prefs.edit().putBoolean("done", true).apply()
                Log.i(TAG, "seeded builtin provider $modelId @ $url")
            }.onFailure {
                Log.e(TAG, "seed builtin provider failed", it)
            }
        }
    }

    private fun readRes(context: Context, name: String): String = runCatching {
        val id = context.resources.getIdentifier(name, "string", context.packageName)
        if (id != 0) context.getString(id) else ""
    }.getOrDefault("")
}
