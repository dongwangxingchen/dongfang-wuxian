// ============================================================================
// [DFWX PATCH P4/P16] 本文件基于上游 re-ovo/rikkahub RouteActivity.kt。
// 相对上游的差异：
//  P4  ：AndroidManifest 中本 Activity 的 MAIN/LAUNCHER intent-filter 已移除
//        （宿主桌面入口是 cc.nkbr.lanzouplus.MainActivity，避免双图标）。
//  P16 ：AppRoutes() 函数体已顶层化到 `ui/routes/AppRoutes.kt`，本文件只保留
//        Activity 外壳（导航栈持有、外部 intent 分发、音量键监听、图像加载器装配），
//        并在 setContent 里调用顶层 AppRoutes：
//          - 原来 SideEffect 里的 `navStack = backStack; while (pendingIntents...)`
//            注入逻辑，改由 DeepLinkSink 回调（`backStackSink`）承载，行为不变；
//          - 原来 `this@RouteActivity.openUsageAccessSettings()` 改为传 context。
// 其余（SafeMode 崩溃检查、dispatchKeyEvent、disableNavigationBarContrast、
// handleIntent 的目的地映射、Screen sealed interface）与上游一致。
// 同步上游时：覆盖本文件后重放 P4 与 P16 两处。
// ============================================================================
package me.rerere.rikkahub

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavKey
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.network.cachecontrol.CacheControlCacheStrategy
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import kotlinx.serialization.Serializable
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.ui.activity.SafeModeActivity
import me.rerere.rikkahub.ui.routes.AppRoutes
import me.rerere.rikkahub.ui.theme.RikkahubTheme
import me.rerere.rikkahub.utils.CrashHandler
import okhttp3.OkHttpClient
import org.koin.android.ext.android.inject

private const val TAG = "RouteActivity"
private const val ACTION_TRANSLATE = "me.rerere.rikkahub.action.TRANSLATE"

class RouteActivity : ComponentActivity() {
    private val okHttpClient by inject<OkHttpClient>()
    private val settingsStore by inject<SettingsStore>()
    private var navStack: MutableList<NavKey>? = null
    private val pendingIntents = ArrayDeque<Intent>()

    // Volume key listener registry — last registered handler wins
    internal val volumeKeyListeners = mutableListOf<(isVolumeUp: Boolean) -> Boolean>()

    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            val isVolumeUp = when (event.keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> true
                KeyEvent.KEYCODE_VOLUME_DOWN -> false
                // [DFWX PATCH P16] 内嵌态的音量键经共享桥转发（宿主 MainActivity.dispatchKeyEvent）；
                // 独立入口这里仍是本地列表，行为与上游一致。
                else -> return super.dispatchKeyEvent(event)
            }
            if (volumeKeyListeners.lastOrNull()?.invoke(isVolumeUp) == true) return true
            if (me.rerere.rikkahub.dfwx.VolumeKeyBridge.dispatch(isVolumeUp)) return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        disableNavigationBarContrast()
        super.onCreate(savedInstanceState)
        if (CrashHandler.hasCrashed(this)) {
            startActivity(Intent(this, SafeModeActivity::class.java))
            finish()
            return
        }
        if (savedInstanceState == null) {
            handleIntent(intent)
        }
        setContent {
            RikkahubTheme {
                setSingletonImageLoaderFactory { context ->
                    ImageLoader.Builder(context)
                        .crossfade(true)
                        .components {
                            add(
                                OkHttpNetworkFetcherFactory(
                                    callFactory = { okHttpClient },
                                    cacheStrategy = { CacheControlCacheStrategy() },
                                )
                            )
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                add(AnimatedImageDecoder.Factory())
                            } else {
                                add(GifDecoder.Factory())
                            }
                            add(SvgDecoder.Factory(scaleToDensity = true))
                        }
                        .build()
                }
                // [DFWX PATCH P16] 上游此处为 AppRoutes()（成员函数，含内联的
                // navStack/pendingIntents 注入）；现改为顶层函数 + DeepLinkSink 回调。
                AppRoutes(
                    context = this,
                    deepLinks = { backStack ->
                        navStack = backStack
                        while (pendingIntents.isNotEmpty()) {
                            handleIntent(pendingIntents.removeFirst())
                        }
                    },
                )
            }
        }
    }

    private fun disableNavigationBarContrast() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val backStack = navStack ?: run {
            // Compose 尚未创建导航栈，待就绪后处理。
            pendingIntents.addLast(intent)
            return
        }
        val destination = when (intent.action) {
            ACTION_TRANSLATE -> Screen.Translator
            Intent.ACTION_SEND -> Screen.ShareHandler(
                text = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty(),
                streamUri = intent.getStringExtra(Intent.EXTRA_STREAM),
            )
            Intent.ACTION_PROCESS_TEXT -> Screen.ShareHandler(
                text = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString().orEmpty(),
            )
            else -> intent.getStringExtra("conversationId")?.let { Screen.Chat(it) }
        }
        if (destination != null && backStack.lastOrNull() != destination) {
            backStack.add(destination)
        }
    }
}

sealed interface Screen : NavKey {
    @Serializable
    data class Chat(
        val id: String,
        val text: String? = null,
        val files: List<String> = emptyList(),
        val nodeId: String? = null
    ) : Screen

    @Serializable
    data class ShareHandler(val text: String, val streamUri: String? = null) : Screen

    @Serializable
    data object History : Screen

    @Serializable
    data object Favorite : Screen

    @Serializable
    data object Assistant : Screen

    @Serializable
    data class AssistantDetail(val id: String) : Screen

    @Serializable
    data class AssistantBasic(val id: String) : Screen

    @Serializable
    data class AssistantPrompt(val id: String) : Screen

    @Serializable
    data class AssistantMemory(val id: String) : Screen

    @Serializable
    data class AssistantRequest(val id: String) : Screen

    @Serializable
    data class AssistantMcp(val id: String) : Screen

    @Serializable
    data class AssistantLocalTool(val id: String) : Screen

    @Serializable
    data class AssistantInjections(val id: String) : Screen

    @Serializable
    data object Translator : Screen

    @Serializable
    data object Setting : Screen

    @Serializable
    data object Backup : Screen

    @Serializable
    data object ImageGen : Screen

    @Serializable
    data class WebView(val url: String = "", val contentId: String = "") : Screen

    @Serializable
    data object SettingTheme : Screen

    @Serializable
    data object SettingPreferences : Screen

    @Serializable
    data object SettingPreferencesTheme : Screen

    @Serializable
    data object SettingPreferencesNotification : Screen

    @Serializable
    data object SettingPreferencesGeneral : Screen

    @Serializable
    data object SettingPreferencesUI : Screen

    @Serializable
    data object SettingPreferencesNetwork : Screen

    @Serializable
    data object SettingProvider : Screen

    @Serializable
    data class SettingProviderDetail(val providerId: String) : Screen

    @Serializable
    data object SettingModels : Screen

    @Serializable
    data object SettingAbout : Screen

    @Serializable
    data object SettingSearch : Screen

    @Serializable
    data class SettingSearchDetail(val serviceId: String) : Screen

    @Serializable
    data object SettingSpeech : Screen

    @Serializable
    data object SettingMcp : Screen

    @Serializable
    data object SettingDonate : Screen

    @Serializable
    data object SettingFiles : Screen

    @Serializable
    data object SettingWeb : Screen

    @Serializable
    data object Debug : Screen

    @Serializable
    data object Log : Screen

    @Serializable
    data object Extensions : Screen

    @Serializable
    data object QuickMessages : Screen

    @Serializable
    data object Prompts : Screen

    @Serializable
    data object Skills : Screen

    @Serializable
    data object Workspaces : Screen

    @Serializable
    data class WorkspaceDetail(val id: String) : Screen

    @Serializable
    data class WorkspaceTerminal(val id: String) : Screen

    @Serializable
    data class WorkspaceFileEditor(val id: String, val area: String, val path: String) : Screen

    @Serializable
    data class SkillDetail(val skillName: String) : Screen

    @Serializable
    data object MessageSearch : Screen

    @Serializable
    data object Stats : Screen
}
