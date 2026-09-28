// ============================================================================
// [DFWX PATCH P16] 本文件是上游 RouteActivity.AppRoutes() 的顶层化版本。
//
// 背景：东方无限把 AI 界面作为宿主主界面的内嵌页（ComposeView 进宿主 pageFrame），
// 而不是独立 Activity。上游 AppRoutes 是 RouteActivity 的成员函数，依赖 4 个实例成员
// （okHttpClient / settingsStore / navStack / pendingIntents / handleIntent），
// 内嵌态没有 RouteActivity 实例，因此把它整体搬出为顶层函数。
//
// 相对上游的差异（**只有这 4 处**，改上游后重放时逐条对照）：
//   1. 签名：无参成员函数 → (context, deepLinks, onNavDepthChanged) 三参顶层函数；
//   2. `settingsStore` 实例成员 → `koinInject<SettingsStore>()`；
//   3. `this@RouteActivity.openUsageAccessSettings()` → `context.openUsageAccessSettings()`，
//      `readBooleanPreference`/`readStringPreference` 为 Context 扩展 → 显式传 context；
//   4. `SideEffect { navStack = backStack; while (pendingIntents...) ... }`
//      → `SideEffect { deepLinks.onBackStackReady(backStack) }` + 栈深上报。
//      独立入口（SEND/PROCESS_TEXT/shortcuts）的 intent 注入逻辑留在 RouteActivity，
//      通过 DeepLinkSink 回调注入，行为不变。
//
// 其余 300+ 行与上游逐字节一致（含 DEBUG 角标、数据库迁移遮罩）。
// 同步上游时：覆盖上游 RouteActivity 后，把 AppRoutes 函数体整段搬来，再按上述 4 条改。
// ============================================================================
package me.rerere.rikkahub.ui.routes

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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
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
import com.dokar.sonner.Toaster
import com.dokar.sonner.rememberToasterState
import me.rerere.rikkahub.BuildConfig
import me.rerere.rikkahub.R
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
import me.rerere.rikkahub.utils.openUsageAccessSettings
import me.rerere.workspace.WorkspaceStorageArea
import org.koin.compose.koinInject
import kotlin.uuid.Uuid

/**
 * [DFWX PATCH P16] 深层链接注入点：独立入口（RouteActivity 的 SEND/PROCESS_TEXT/TRANSLATE/
 * shortcuts intent）通过本接口把 intent 转成的目的地塞进 vendor 导航栈。
 * 内嵌态没有外部 intent，用 [NoDeepLinks]。
 */
fun interface DeepLinkSink {
    /** backStack 就绪时被调用；实现方负责消费滞留的 intent。 */
    fun onBackStackReady(backStack: MutableList<NavKey>)
}

object NoDeepLinks : DeepLinkSink {
    override fun onBackStackReady(backStack: MutableList<NavKey>) = Unit
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun AppRoutes(
    context: Context,
    deepLinks: DeepLinkSink = NoDeepLinks,
    onNavDepthChanged: (Int) -> Unit = {},
) {
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
                is AppEvent.OpenUsageAccessSettings -> context.openUsageAccessSettings()
                is AppEvent.ChatGenerationUpdate -> Unit // 由 ChatNotificationManager 消费
                is AppEvent.ChatGenerationEnded -> Unit // 由 ChatNotificationManager 消费
            }
        }
    }
    val migrationState by DatabaseMigrationTracker.state.collectAsStateWithLifecycle()

    val startScreen = Screen.Chat(
        id = if (context.readBooleanPreference("create_new_conversation_on_start", true)) {
            Uuid.random().toString()
        } else {
            context.readStringPreference(
                "lastConversationId",
                Uuid.random().toString()
            ) ?: Uuid.random().toString()
        }
    )

    val backStack = rememberNavBackStack(startScreen)
    SideEffect {
        deepLinks.onBackStackReady(backStack)
    }
    // [DFWX PATCH P16] 栈深上报：宿主据此决定系统返回是交给 Compose 还是自己收权回主页。
    LaunchedEffect(backStack.size) {
        onNavDepthChanged(backStack.size)
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

                        entry<Screen.History> {
                            HistoryPage()
                        }

                        entry<Screen.Favorite> {
                            FavoritePage()
                        }

                        entry<Screen.Assistant> {
                            AssistantPage()
                        }

                        entry<Screen.AssistantDetail> { key ->
                            AssistantDetailPage(key.id)
                        }

                        entry<Screen.AssistantBasic> { key ->
                            AssistantBasicPage(key.id)
                        }

                        entry<Screen.AssistantPrompt> { key ->
                            AssistantPromptPage(key.id)
                        }

                        entry<Screen.AssistantMemory> { key ->
                            AssistantMemoryPage(key.id)
                        }

                        entry<Screen.AssistantRequest> { key ->
                            AssistantRequestPage(key.id)
                        }

                        entry<Screen.AssistantMcp> { key ->
                            AssistantMcpPage(key.id)
                        }

                        entry<Screen.AssistantLocalTool> { key ->
                            AssistantLocalToolPage(key.id)
                        }

                        entry<Screen.AssistantInjections> { key ->
                            AssistantExtensionsPage(key.id)
                        }

                        entry<Screen.Translator> {
                            TranslatorPage()
                        }

                        entry<Screen.Setting> {
                            SettingPage()
                        }

                        entry<Screen.Backup> {
                            BackupPage()
                        }

                        entry<Screen.ImageGen> {
                            ImageGenPage()
                        }

                        entry<Screen.WebView> { key ->
                            WebViewPage(key.url, key.contentId)
                        }

                        entry<Screen.SettingTheme> {
                            SettingThemePage()
                        }

                        entry<Screen.SettingPreferences> {
                            SettingPreferencesPage()
                        }

                        entry<Screen.SettingPreferencesTheme> {
                            SettingPreferencesThemePage()
                        }

                        entry<Screen.SettingPreferencesNotification> {
                            SettingPreferencesNotificationPage()
                        }

                        entry<Screen.SettingPreferencesGeneral> {
                            SettingPreferencesGeneralPage()
                        }

                        entry<Screen.SettingPreferencesUI> {
                            SettingPreferencesUIPage()
                        }

                        entry<Screen.SettingPreferencesNetwork> {
                            SettingPreferencesNetworkPage()
                        }

                        entry<Screen.SettingProvider> {
                            SettingProviderPage()
                        }

                        entry<Screen.SettingProviderDetail> { key ->
                            val id = Uuid.parse(key.providerId)
                            SettingProviderDetailPage(id = id)
                        }

                        entry<Screen.SettingModels> {
                            SettingModelPage()
                        }

                        entry<Screen.SettingAbout> {
                            SettingAboutPage()
                        }

                        entry<Screen.SettingSearch> {
                            SettingSearchPage()
                        }

                        entry<Screen.SettingSearchDetail> { key ->
                            val id = Uuid.parse(key.serviceId)
                            SettingSearchDetailPage(id)
                        }

                        entry<Screen.SettingSpeech> {
                            SettingSpeechPage()
                        }

                        entry<Screen.SettingMcp> {
                            SettingMcpPage()
                        }

                        entry<Screen.SettingDonate> {
                            SettingDonatePage()
                        }

                        entry<Screen.SettingFiles> {
                            SettingFilesPage()
                        }

                        entry<Screen.SettingWeb> {
                            SettingWebPage()
                        }

                        entry<Screen.Debug> {
                            DebugPage()
                        }

                        entry<Screen.Log> {
                            LogPage()
                        }

                        entry<Screen.Extensions> {
                            ExtensionsPage()
                        }

                        entry<Screen.QuickMessages> {
                            QuickMessagesPage()
                        }

                        entry<Screen.Prompts> {
                            PromptPage()
                        }

                        entry<Screen.Skills> {
                            SkillsPage()
                        }

                        entry<Screen.Workspaces> {
                            WorkspacePage()
                        }

                        entry<Screen.WorkspaceDetail> { key ->
                            WorkspaceDetailPage(key.id)
                        }

                        entry<Screen.WorkspaceTerminal> { key ->
                            WorkspaceTerminalPage(key.id)
                        }

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

                        entry<Screen.MessageSearch> {
                            SearchPage()
                        }

                        entry<Screen.Stats> {
                            StatsPage()
                        }
                    }
                )
                if (BuildConfig.DEBUG) {
                    Text(
                        text = "[开发模式]",
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
                                text = stringResource(R.string.db_migrating),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            if (state != null) {
                                Text(
                                    text = "v${state.from} → v${state.to}",
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
