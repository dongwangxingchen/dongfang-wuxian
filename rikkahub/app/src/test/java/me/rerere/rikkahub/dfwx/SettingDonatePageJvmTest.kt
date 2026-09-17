package me.rerere.rikkahub.dfwx

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation3.runtime.NavKey
import androidx.test.core.app.ApplicationProvider
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.Navigator
import me.rerere.rikkahub.ui.pages.setting.SettingDonatePage
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [DFWX] 无真机/无模拟器环境下的 JVM 点击级回归（Robolectric 在开发机上直接渲染 Compose 页面）。
 *
 * 背景：用户报告「AI 对话设置里点赞助按钮卡退」。本测试把赞助页在 JVM 里完整组合一遍：
 * 页面必须渲染出赞助方式卡片（Kofi / 爱发电），且赞助列表网络失败（JVM 无外网代理时必失败）
 * 走 UiState.Error 占位，不得有任何未捕获异常冒泡——冒泡即测试红，红即拿到真实堆栈。
 */
// application=纯 Application：跳过 RikkaHubApp.onCreate（其 QuickJS/SQLite/Termux 原生库是 arm64 .so，
// JVM 上必然 UnsatisfiedLinkError）；Koin 由测试自行启动最小模块，页面只依赖 SponsorAPI。
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = SweepTestApplication::class)
class SettingDonatePageJvmTest {
    @get:Rule val compose = createComposeRule()

    @Before fun setUp() {
        // Koin 保持进程级存活（不要在 @After stopKoin——Embed 的 settingsFlow 挂起发射会在
        // Compose 规则拆卸期撞上已停止的 Koin 抛 IllegalStateException 污染测试结果）。
        // 与 EmbedSweepJvmTest 用同一套完整模块：同一 JVM 内无论谁先启动，两边都可用。
        if (org.koin.core.context.GlobalContext.getOrNull() == null) {
            val app = ApplicationProvider.getApplicationContext<Application>()
            startKoin {
                androidContext(app)
                modules(
                    me.rerere.rikkahub.di.appModule,
                    me.rerere.rikkahub.di.viewModelModule,
                    me.rerere.rikkahub.di.dataSourceModule,
                    me.rerere.rikkahub.di.repositoryModule,
                )
            }
        }
    }

    @Test fun donatePage_rendersCards_andSurvivesNetworkFailure() {
        compose.setContent {
            MaterialTheme {
                // 复刻 RikkaHubEmbed.kt:202 的 provider：BackButton 依赖 LocalNavController
                CompositionLocalProvider(
                    LocalNavController provides Navigator(mutableStateListOf<NavKey>(Screen.SettingDonate))
                ) {
                    SettingDonatePage()
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("Kofi").assertExists()
        compose.onNodeWithText("爱发电").assertExists()
    }
}
