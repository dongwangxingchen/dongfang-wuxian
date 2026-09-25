package me.rerere.rikkahub.dfwx

import android.content.Context
import android.content.SharedPreferences
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.material3.Typography
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
 * [DFWX PATCH P24 v1.21.3] 全站字体切换（宿主设置·外观：系统字体/思源黑体，存宿主 ui_prefs_v1/font_choice）：
 * 本侧直接监听该偏好驱动重组，缓存复用的 ComposeView 无需销毁重建、界面状态不丢。
 * ① Material 文本（标题/按钮/输入框/菜单）：把思源黑体 VF（wght 100-900）从宿主 assets 复制到 filesDir，
 *   经 Font(file, weight) 按 wght 轴实例化构建 Typography 注入 RikkaHubEmbed（API 26+ 真·字重轴）；
 * ② 聊天气泡：ChatList 内层 ChatFontProvider 只认 displaySetting（外层 CompositionLocal 覆盖不过它），
 *   故走 RikkaHub 自带「聊天字体=自定义」机制——displaySetting 同步为 CUSTOM 指向同一份 filesDir 副本
 *   （loadCustomFontFamily 取默认字重 400，粗体由合成加粗兜底）；系统字体时只回收我们写入的 CUSTOM，
 *   不碰用户在 AI 设置里手动选的其他聊天字体。
 * 字体复制失败/缺失时一律回退系统字体，绝不让字体问题崩 app。
 */
fun createRikkaHubEmbedView(activity: ComponentActivity): android.view.View {
    val view = ComposeView(activity)
    view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    // [DFWX PATCH P24 修复] 字体状态在组合外创建：宿主把本 View 摘下窗口缓存（detach）时 ViewTree owner 链断裂，
    // 此时任何 setState 触发的重组都会因 LocalViewModelStoreOwner 缺失而 FATAL（v1.21.3 模拟器实测），
    // 故偏好监听加 isAttachedToWindow 守卫、notoFile 上屏经 view.post（detach 时自动排队到挂回后执行），挂回窗口再补读偏好。
    val prefs = activity.getSharedPreferences(DFWX_FONT_PREFS, Context.MODE_PRIVATE)
    val notoChoice = mutableStateOf(prefs.getInt(DFWX_FONT_KEY, 0) == 1)
    val notoFile = mutableStateOf<File?>(null)
    view.setContent {
        // [DFWX PATCH P24] 宿主字体选择 → Compose 状态：notoChoice 跟随偏好，notoFile 只有副本就绪才非空
        DisposableEffect(prefs) {
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
                if (key == DFWX_FONT_KEY && view.isAttachedToWindow) notoChoice.value = p.getInt(DFWX_FONT_KEY, 0) == 1
            }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
        }
        RikkahubTheme(colorMode = ColorMode.DARK) {
            val settingsStore = koinInject<SettingsStore>()
            LaunchedEffect(notoChoice.value) {
                if (notoChoice.value) {
                    dfwxSetChatFontCustom(activity, settingsStore)
                    val file = dfwxEnsureNotoFontFile(activity)
                    view.post { notoFile.value = file }
                } else {
                    dfwxResetChatFontCustom(settingsStore)
                    view.post { notoFile.value = null }
                }
            }
            RikkaHubEmbed(
                activity = activity,
                onBackStackReady = { },
                onOpenUsageAccessSettings = { activity.openUsageAccessSettings() },
                dfwxTypography = dfwxTypography(notoFile.value),
            )
        }
    }
    view.addOnAttachStateChangeListener(object : android.view.View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: android.view.View) {
            notoChoice.value = prefs.getInt(DFWX_FONT_KEY, 0) == 1
        }
        override fun onViewDetachedFromWindow(v: android.view.View) {}
    })
    return view
}

private const val DFWX_FONT_PREFS = "ui_prefs_v1"
private const val DFWX_FONT_KEY = "font_choice"
private const val DFWX_CHAT_FONT_DIR = "dfwx"
private const val DFWX_CHAT_FONT_FILE = "NotoSansSC-VF.ttf"
private const val DFWX_CHAT_FONT_ASSET = "fonts/NotoSansSC-VF.ttf"

/** [DFWX PATCH P24] 确保思源黑体 VF 在 filesDir 就绪（从宿主 assets 复制，同一 APK），返回就绪文件；失败返回 null 回退系统字体。 */
private suspend fun dfwxEnsureNotoFontFile(context: Context): File? = withContext(Dispatchers.IO) {
    runCatching {
        val dir = File(context.filesDir, DFWX_CHAT_FONT_DIR)
        val target = File(dir, DFWX_CHAT_FONT_FILE)
        if (!target.isFile || target.length() <= 0L) {
            dir.mkdirs()
            context.assets.open(DFWX_CHAT_FONT_ASSET).use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        }
        target.takeIf { it.isFile && it.length() > 0L }
    }.getOrNull()
}

/** [DFWX PATCH P24] 聊天气泡字体走 RikkaHub 自带「聊天字体=自定义」（ChatList 内层 ChatFontProvider 只认 displaySetting）。 */
private suspend fun dfwxSetChatFontCustom(context: Context, store: SettingsStore) {
    runCatching {
        val relativePath = "$DFWX_CHAT_FONT_DIR/$DFWX_CHAT_FONT_FILE"
        val ready = dfwxEnsureNotoFontFile(context) != null
        if (!ready) return
        store.update { settings ->
            val d = settings.displaySetting
            if (d.chatFontFamily == ChatFontFamily.CUSTOM && d.chatCustomFontPath == relativePath) settings
            else settings.copy(
                displaySetting = d.copy(
                    chatFontFamily = ChatFontFamily.CUSTOM,
                    chatCustomFontPath = relativePath,
                    chatCustomFontName = "思源黑体",
                )
            )
        }
    }
}

/** [DFWX PATCH P24] 系统字体：仅当 CUSTOM 是我们自己写入的（同路径）才恢复 DEFAULT，不碰用户手选的其他聊天字体。 */
private suspend fun dfwxResetChatFontCustom(store: SettingsStore) {
    runCatching {
        store.update { settings ->
            val d = settings.displaySetting
            if (d.chatFontFamily == ChatFontFamily.CUSTOM && d.chatCustomFontPath == "$DFWX_CHAT_FONT_DIR/$DFWX_CHAT_FONT_FILE") {
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
}

/** [DFWX PATCH P24] 思源黑体 Typography：全部 Material 样式换 fontFamily，Font(file, weight) 按最近字重解析
 *  （API 26+ 经 FontVariation 实例化 wght 轴）；系统字体（null）维持上游默认。 */
@Composable
private fun dfwxTypography(file: File?): Typography {
    val base = remember { Typography() }
    return remember(file) {
        if (file == null) {
            base
        } else {
            val family = FontFamily(
                Font(file, weight = FontWeight.Normal),
                Font(file, weight = FontWeight.Medium),
                Font(file, weight = FontWeight.SemiBold),
                Font(file, weight = FontWeight.Bold),
            )
            Typography(
                displayLarge = base.displayLarge.copy(fontFamily = family),
                displayMedium = base.displayMedium.copy(fontFamily = family),
                displaySmall = base.displaySmall.copy(fontFamily = family),
                headlineLarge = base.headlineLarge.copy(fontFamily = family),
                headlineMedium = base.headlineMedium.copy(fontFamily = family),
                headlineSmall = base.headlineSmall.copy(fontFamily = family),
                titleLarge = base.titleLarge.copy(fontFamily = family),
                titleMedium = base.titleMedium.copy(fontFamily = family),
                titleSmall = base.titleSmall.copy(fontFamily = family),
                bodyLarge = base.bodyLarge.copy(fontFamily = family),
                bodyMedium = base.bodyMedium.copy(fontFamily = family),
                bodySmall = base.bodySmall.copy(fontFamily = family),
                labelLarge = base.labelLarge.copy(fontFamily = family),
                labelMedium = base.labelMedium.copy(fontFamily = family),
                labelSmall = base.labelSmall.copy(fontFamily = family),
            )
        }
    }
}
