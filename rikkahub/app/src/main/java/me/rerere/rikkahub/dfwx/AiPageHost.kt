package me.rerere.rikkahub.dfwx

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.data.datastore.ChatFontFamily
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.ui.theme.ColorMode
import me.rerere.rikkahub.ui.theme.RikkahubTheme
import me.rerere.rikkahub.utils.openUsageAccessSettings
import org.koin.compose.koinInject

/**
 * [DFWX PATCH P16] 供宿主（纯 Java）创建内嵌 AI 界面的桥接入口：
 * 宿主 MainActivity（androidx.activity.ComponentActivity）把返回的 ComposeView
 * 挂进自己的页面容器即可；返回键经宿主 OnBackPressedDispatcher 自然桥接。
 * [DFWX PATCH P18] 外层主题强制深色，与宿主东方风格一致（内层 RikkaHubEmbed 同参）。
 * [DFWX PATCH P24 v1.21.5] 全站字体切换（v1.21.3–v1.21.4）整体移除：宿主与 AI 页一律回系统字体，
 * 字体资产/偏好键/监听机制全部下架；仅保留一次性存量迁移——v1.21.3/v1.21.4 曾把聊天气泡
 * 写成 CUSTOM 指向我们的 filesDir 字体副本，升级后该文件不再维护，须回退 DEFAULT 并清理副本。
 */
fun createRikkaHubEmbedView(activity: ComponentActivity): android.view.View {
    val view = ComposeView(activity)
    view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    view.setContent {
        RikkahubTheme(colorMode = ColorMode.DARK) {
            val settingsStore = koinInject<SettingsStore>()
            LaunchedEffect(Unit) {
                dfwxMigrateLegacyChatFont(activity, settingsStore)
            }
            RikkaHubEmbed(
                activity = activity,
                onBackStackReady = { },
                onOpenUsageAccessSettings = { activity.openUsageAccessSettings() },
            )
        }
    }
    return view
}

private const val DFWX_LEGACY_FONT_DIR = "dfwx"
private val DFWX_LEGACY_FONT_FILES = arrayOf("NotoSansSC-VF.ttf", "NotoSansSC-VF.2.ttf", "SpaceGrotesk-VF.ttf")

/** [DFWX PATCH P24 v1.21.5] 字体切换移除后的存量迁移：仅当聊天字体是我们写入的（CUSTOM 且路径带 "dfwx/" 前缀，
 *  覆盖 v1.21.3 "dfwx/NotoSansSC-VF.ttf" 与 v1.21.4 "dfwx/NotoSansSC-VF.2.ttf"）才回退 DEFAULT；
 *  用户在 AI 设置手动选的其他自定义聊天字体不碰。顺带清理 filesDir 里的旧字体副本。 */
private suspend fun dfwxMigrateLegacyChatFont(context: Context, store: SettingsStore) {
    runCatching {
        store.update { settings ->
            val d = settings.displaySetting
            if (d.chatFontFamily == ChatFontFamily.CUSTOM && d.chatCustomFontPath.startsWith("$DFWX_LEGACY_FONT_DIR/")) {
                settings.copy(
                    displaySetting = d.copy(
                        chatFontFamily = ChatFontFamily.DEFAULT,
                        chatCustomFontPath = "",
                        chatCustomFontName = "",
                    )
                )
            } else settings
        }
    }
    withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.filesDir, DFWX_LEGACY_FONT_DIR)
            for (name in DFWX_LEGACY_FONT_FILES) File(dir, name).delete()
        }
    }
}
