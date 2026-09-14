package me.rerere.rikkahub.dfwx

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.network.cachecontrol.CacheControlCacheStrategy
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import com.dokar.sonner.Toaster
import com.dokar.sonner.rememberToasterState
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.db.DatabaseMigrationTracker
import me.rerere.rikkahub.data.db.MigrationState
import me.rerere.rikkahub.data.event.AppEvent
import me.rerere.rikkahub.data.event.AppEventBus
import me.rerere.rikkahub.ui.components.ui.TTSController
import me.rerere.rikkahub.ui.context.LocalASRState
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.context.LocalSharedTransitionScope
import me.rerere.rikkahub.ui.context.LocalTTSState
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.context.Navigator
import me.rerere.rikkahub.ui.hooks.readBooleanPreference
import me.rerere.rikkahub.ui.hooks.readStringPreference
import me.rerere.rikkahub.ui.hooks.rememberCustomAsrState
import me.rerere.rikkahub.ui.hooks.rememberCustomTtsState
import me.rerere.rikkahub.ui.pages.assistant.AssistantPage
import me.rerere.rikkahub.ui.pages.assistant.detail.AssistantBasicPage
import me.rerere.rikkahub.ui.pages.assistant.detail.AssistantDetailPage
import me.rerere.rikkahub.ui.pages.assistant.detail.AssistantExtensionsPage
import me.rerere.rikkahub.ui.pages.assistant.detail.AssistantLocalToolPage
import me.rerere.rikkahub.ui.pages.assistant.detail.AssistantMcpPage
import me.rerere.rikkahub.ui.pages.assistant.detail.AssistantMemoryPage
import me.rerere.rikkahub.ui.pages.assistant.detail.AssistantPromptPage
import me.rerere.rikkahub.ui.pages.assistant.detail.AssistantRequestPage
import me.rerere.rikkahub.ui.pages.backup.BackupPage
import me.rerere.rikkahub.ui.pages.chat.ChatPage
import me.rerere.rikkahub.ui.pages.debug.DebugPage
import me.rerere.rikkahub.ui.pages.extensions.ExtensionsPage
import me.rerere.rikkahub.ui.pages.extensions.PromptPage
import me.rerere.rikkahub.ui.pages.extensions.QuickMessagesPage
import me.rerere.rikkahub.ui.pages.extensions.skills.SkillDetailPage
import me.rerere.rikkahub.ui.pages.extensions.skills.SkillsPage
import me.rerere.rikkahub.ui.pages.extensions.workspace.WorkspaceDetailPage
import me.rerere.rikkahub.ui.pages.extensions.workspace.WorkspaceFileEditorPage
import me.rerere.rikkahub.ui.pages.extensions.workspace.WorkspacePage
import me.rerere.rikkahub.ui.pages.extensions.workspace.WorkspaceTerminalPage
import me.rerere.workspace.WorkspaceStorageArea
import me.rerere.rikkahub.ui.pages.favorite.FavoritePage
import me.rerere.rikkahub.ui.pages.history.HistoryPage
import me.rerere.rikkahub.ui.pages.imggen.ImageGenPage
import me.rerere.rikkahub.ui.pages.log.LogPage
import me.rerere.rikkahub.ui.pages.search.SearchPage
import me.rerere.rikkahub.ui.pages.setting.SettingAboutPage
import me.rerere.rikkahub.ui.pages.setting.SettingDonatePage
import me.rerere.rikkahub.ui.pages.setting.SettingFilesPage
import me.rerere.rikkahub.ui.pages.setting.SettingMcpPage
import me.rerere.rikkahub.ui.pages.setting.SettingModelPage
import me.rerere.rikkahub.ui.pages.setting.SettingPage
import me.rerere.rikkahub.ui.pages.setting.SettingPreferencesGeneralPage
import me.rerere.rikkahub.ui.pages.setting.SettingPreferencesNetworkPage
import me.rerere.rikkahub.ui.pages.setting.SettingPreferencesNotificationPage
import me.rerere.rikkahub.ui.pages.setting.SettingPreferencesPage
import me.rerere.rikkahub.ui.pages.setting.SettingPreferencesThemePage
import me.rerere.rikkahub.ui.pages.setting.SettingPreferencesUIPage
import me.rerere.rikkahub.ui.pages.setting.SettingProviderDetailPage
import me.rerere.rikkahub.ui.pages.setting.SettingProviderPage
import me.rerere.rikkahub.ui.pages.setting.SettingSearchDetailPage
import me.rerere.rikkahub.ui.pages.setting.SettingSearchPage
import me.rerere.rikkahub.ui.pages.setting.SettingSpeechPage
import me.rerere.rikkahub.ui.pages.setting.SettingThemePage
import me.rerere.rikkahub.ui.pages.setting.SettingWebPage
import me.rerere.rikkahub.ui.pages.share.handler.ShareHandlerPage
import me.rerere.rikkahub.ui.pages.stats.StatsPage
import me.rerere.rikkahub.ui.pages.translator.TranslatorPage
import me.rerere.rikkahub.ui.pages.webview.WebViewPage
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import me.rerere.rikkahub.ui.theme.RikkahubTheme
import okhttp3.OkHttpClient
import org.koin.compose.koinInject
import kotlin.uuid.Uuid

/**
 * [DFWX PATCH P16] 鍙唴宓岀殑 RikkaHub 鐣岄潰鍏ュ彛锛堝涓讳笢鏂规棤闄愬簳鏍?AI"椤电洿鎺ユ壙杞斤紝
 * 涓嶅啀璺宠浆鐙珛 Activity锛夈€傚唴瀹逛笌 RouteActivity.AppRoutes 涓€涓€瀵瑰簲锛? * 鍚屾涓婃父鏃讹紝瀵圭収涓婃父 AppRoutes 鐨勫鍒犻噸鏀炬湰鏂囦欢锛堝樊寮備粎锛? *  1. settingsStore/okHttpClient 鏀逛负 koinInject 鑾峰彇锛? *  2. backStack 鐢?rememberNavBackStack 鍒涘缓鍚庣粡 onBackStackReady 鍥炶皟涓婃姤锛? *  3. OpenUsageAccessSettings 浜嬩欢涓庨煶閲忛敭妗ユ帴涓哄洖璋冨弬鏁帮紱
 *  4. 鏃?CrashHandler/SafeMode 妫€鏌ワ紙瀹夸富 Application 宸插厹搴曪級銆? *  5. 璇ョ晫闈㈣繍琛屼簬瀹夸富 Activity 鍐咃紝BackHandler 璧板涓?OnBackPressedDispatcher銆? */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun RikkaHubEmbed(
    activity: Context,
    onBackStackReady: (MutableList<NavKey>) -> Unit,
    onOpenUsageAccessSettings: () -> Unit,
) {
    RikkahubTheme {
        val okHttpClient: OkHttpClient = koinInject()
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
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                        add(AnimatedImageDecoder.Factory())
                    } else {
                        add(GifDecoder.Factory())
                    }
                    add(SvgDecoder.Factory(scaleToDensity = true))
                }
                .build()
        }
        val settingsStore = koinInject<SettingsStore>()
        val toastState = rememberToasterState()
        val settings by settingsStore.settingsFlow.collectAsStateWithLifecycle()
        val tts = rememberCustomTtsState()
        val asr = rememberCustomAsrState()
        val eventBus = koinInject<AppEventBus>()
        LaunchedEffect(tts) {
            eventBus.events.collect { event ->
                when (event) {
                    is AppEvent.Speak -> tts.speak(event.text)
                    is AppEvent.OpenUsageAccessSettings -> onOpenUsageAccessSettings()
                    is AppEvent.ChatGenerationUpdate -> Unit
                    is AppEvent.ChatGenerationEnded -> Unit
                }
            }
        }
        val migrationState by DatabaseMigrationTracker.state.collectAsStateWithLifecycle()

        val startScreen = Screen.Chat(
            id = if (activity.readBooleanPreference("create_new_conversation_on_start", true)) {
                Uuid.random().toString()
            } else {
                activity.readStringPreference(
                    "lastConversationId",
                    Uuid.random().toString()
                ) ?: Uuid.random().toString()
            }
        )

        val backStack = rememberNavBackStack(startScreen)
        SideEffect {
            onBackStackReady(backStack)
        }

        SharedTransitionLayout {
            CompositionLocalProvider(
                LocalNavController provides Navigator(backStack),
                LocalSharedTransitionScope provides this,
                LocalSettings provides settings,
                LocalToaster provides toastState,
                LocalTTSState provides tts,
                LocalASRState provides asr,
            ) {
                Toaster(
                    state = toastState,
                    darkTheme = LocalDarkMode.current,
                    richColors = true,
                    alignment = Alignment.TopCenter,
                    showCloseButton = true,
                )
                TTSController()
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .semantics { testTagsAsResourceId = true }
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    NavDisplay(
                        backStack = backStack,
                        entryDecorators = listOf(
                            rememberSaveableStateHolderNavEntryDecorator(),
                            rememberViewModelStoreNavEntryDecorator(),
                        ),
                        modifier = Modifier.fillMaxSize(),
                        onBack = { backStack.removeLastOrNull() },
                        transitionSpec = {
                            if (backStack.size == 1) fadeIn() togetherWith fadeOut()
                            else {
                                slideInHorizontally { it } togetherWith
                                    slideOutHorizontally { -it / 2 } + scaleOut(targetScale = 0.7f) + fadeOut()
                            }
                        },
                        popTransitionSpec = {
                            slideInHorizontally { -it / 2 } + scaleIn(initialScale = 0.7f) + fadeIn() togetherWith
                                slideOutHorizontally { it }
                        },
                        predictivePopTransitionSpec = {
                            slideInHorizontally { -it / 2 } + scaleIn(initialScale = 0.7f) + fadeIn() togetherWith
                                slideOutHorizontally { it }
                        },
                        entryProvider = entryProvider {
                            entry<Screen.Chat>(
                                metadata = NavDisplay.transitionSpec { fadeIn() togetherWith fadeOut() }
                                    + NavDisplay.popTransitionSpec { fadeIn() togetherWith fadeOut() }
                            ) { key ->
                                ChatPage(
                                    id = Uuid.parse(key.id),
                                    text = key.text,
                                    files = key.files.map { it.toUri() },
                                    nodeId = key.nodeId?.let { Uuid.parse(it) }
                                )
                            }

                            entry<Screen.ShareHandler> { key ->
                                ShareHandlerPage(
                                    text = key.text,
                                    image = key.streamUri
                                )
                            }

                            entry<Screen.History> { HistoryPage() }

                            entry<Screen.Favorite> { FavoritePage() }

                            entry<Screen.Assistant> { AssistantPage() }

                            entry<Screen.AssistantDetail> { key -> AssistantDetailPage(key.id) }

                            entry<Screen.AssistantBasic> { key -> AssistantBasicPage(key.id) }

                            entry<Screen.AssistantPrompt> { key -> AssistantPromptPage(key.id) }

                            entry<Screen.AssistantMemory> { key -> AssistantMemoryPage(key.id) }

                            entry<Screen.AssistantRequest> { key -> AssistantRequestPage(key.id) }

                            entry<Screen.AssistantMcp> { key -> AssistantMcpPage(key.id) }

                            entry<Screen.AssistantLocalTool> { key -> AssistantLocalToolPage(key.id) }

                            entry<Screen.AssistantInjections> { key -> AssistantExtensionsPage(key.id) }

                            entry<Screen.Translator> { TranslatorPage() }

                            entry<Screen.Setting> { SettingPage() }

                            entry<Screen.Backup> { BackupPage() }

                            entry<Screen.ImageGen> { ImageGenPage() }

                            entry<Screen.WebView> { key -> WebViewPage(key.url, key.contentId) }

                            entry<Screen.SettingTheme> { SettingThemePage() }

                            entry<Screen.SettingPreferences> { SettingPreferencesPage() }

                            entry<Screen.SettingPreferencesTheme> { SettingPreferencesThemePage() }

                            entry<Screen.SettingPreferencesNotification> { SettingPreferencesNotificationPage() }

                            entry<Screen.SettingPreferencesGeneral> { SettingPreferencesGeneralPage() }

                            entry<Screen.SettingPreferencesUI> { SettingPreferencesUIPage() }

                            entry<Screen.SettingPreferencesNetwork> { SettingPreferencesNetworkPage() }

                            entry<Screen.SettingProvider> { SettingProviderPage() }

                            entry<Screen.SettingProviderDetail> { key ->
                                val id = Uuid.parse(key.providerId)
                                SettingProviderDetailPage(id = id)
                            }

                            entry<Screen.SettingModels> { SettingModelPage() }

                            entry<Screen.SettingAbout> { SettingAboutPage() }

                            entry<Screen.SettingSearch> { SettingSearchPage() }

                            entry<Screen.SettingSearchDetail> { key ->
                                val id = Uuid.parse(key.serviceId)
                                SettingSearchDetailPage(id)
                            }

                            entry<Screen.SettingSpeech> { SettingSpeechPage() }

                            entry<Screen.SettingMcp> { SettingMcpPage() }

                            entry<Screen.SettingDonate> { SettingDonatePage() }

                            entry<Screen.SettingFiles> { SettingFilesPage() }

                            entry<Screen.SettingWeb> { SettingWebPage() }

                            entry<Screen.Debug> { DebugPage() }

                            entry<Screen.Log> { LogPage() }

                            entry<Screen.Extensions> { ExtensionsPage() }

                            entry<Screen.QuickMessages> { QuickMessagesPage() }

                            entry<Screen.Prompts> { PromptPage() }

                            entry<Screen.Skills> { SkillsPage() }

                            entry<Screen.Workspaces> { WorkspacePage() }

                            entry<Screen.WorkspaceDetail> { key -> WorkspaceDetailPage(key.id) }

                            entry<Screen.WorkspaceTerminal> { key -> WorkspaceTerminalPage(key.id) }

                            entry<Screen.WorkspaceFileEditor> { key ->
                                WorkspaceFileEditorPage(
                                    id = key.id,
                                    area = WorkspaceStorageArea.valueOf(key.area),
                                    path = key.path,
                                )
                            }

                            entry<Screen.SkillDetail> { key ->
                                SkillDetailPage(skillName = key.skillName)
                            }

                            entry<Screen.MessageSearch> { SearchPage() }

                            entry<Screen.Stats> { StatsPage() }
                        }
                    )
                    if (me.rerere.rikkahub.BuildConfig.DEBUG) {
                        Text(
                            text = "[寮€鍙戞ā寮廬",
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                        )
                    }
                    AnimatedVisibility(
                        visible = migrationState is MigrationState.Migrating,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        val state = migrationState as? MigrationState.Migrating
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                CircularProgressIndicator()
                                Text(
                                    text = stringResource(me.rerere.rikkahub.R.string.db_migrating),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                if (state != null) {
                                    Text(
                                        text = "v${state.from} 鈫?v${state.to}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
