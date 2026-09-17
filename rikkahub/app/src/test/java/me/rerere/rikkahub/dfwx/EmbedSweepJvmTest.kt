package me.rerere.rikkahub.dfwx

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation3.runtime.NavKey
import androidx.test.core.app.ApplicationProvider
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.dfwx.RikkaHubEmbed
import me.rerere.rikkahub.di.appModule
import me.rerere.rikkahub.di.dataSourceModule
import me.rerere.rikkahub.di.repositoryModule
import me.rerere.rikkahub.di.viewModelModule
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [DFWX] 逐页扫描回归：在 JVM 里启动【真实】RikkaHubEmbed 外壳（完整 Koin + 主题 + 导航宿主），
 * 然后模拟用户依次进入每个设置子页——等价于"挨个点进去看会不会崩"。
 *
 * 环境说明（不当作产品 bug 的两类失败）：
 *  1. UnsatisfiedLinkError：页面懒加载 quickjs/termux 等 arm64 原生库，JVM 上必然失败 → 标记 JVM-无法测；
 *  2. JVM 无外网导致的网络失败：页面应显示错误占位而不是崩溃，崩溃才算 bug。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = SweepTestApplication::class)
class EmbedSweepJvmTest {
    @get:Rule val compose = createComposeRule()

    private lateinit var backStack: MutableList<NavKey>

    @Before fun setUp() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        // Koin 保持进程级存活；同一 JVM 若已被其他测试类启动则复用（模块集一致）
        if (org.koin.core.context.GlobalContext.getOrNull() == null) {
            startKoin {
                allowOverride(true)
                androidContext(app)
                modules(appModule, viewModelModule, dataSourceModule, repositoryModule)
            }
        }
        // [JVM 环境] AppDatabaseFactory 固定走 requery sqlite 原生库（arm 专属 .so，JVM 必炸）
        // + onOpen 回调里 jieba_dict/fts5 自定义函数也依赖该原生库 → 测试中覆盖为 framework
        // 默认实现（Robolectric 自带 sqlite4java，JVM 可用）；真机不受影响。
        val testDb = androidx.room.Room.databaseBuilder(app, me.rerere.rikkahub.data.db.AppDatabase::class.java, "dfwx-jvm-test.db")
            .fallbackToDestructiveMigration(true)
            .build()
        org.koin.core.context.loadKoinModules(org.koin.dsl.module { single { testDb } })
    }

    private fun bootEmbed() {
        val holder = mutableListOf<MutableList<NavKey>>()
        compose.setContent {
            RikkaHubEmbed(
                activity = ApplicationProvider.getApplicationContext(),
                onBackStackReady = { holder.add(it) },
                onOpenUsageAccessSettings = {},
            )
        }
        compose.waitForIdle()
        check(holder.isNotEmpty()) { "Embed 未上报 backStack" }
        backStack = holder.first()
    }

    /** 清空并推入新页面栈（等价从聊天页点设置再点子页） */
    private fun go(screen: NavKey) {
        compose.runOnIdle {
            backStack.clear()
            backStack.add(Screen.Chat(id = "sweep"))
            backStack.add(screen)
        }
        compose.waitForIdle()
    }

    @Test fun settingsFamilyPages_allRenderWithoutCrash() {
        bootEmbed()

        val pages: List<Pair<String, NavKey>> = listOf(
            "Setting(根)" to Screen.Setting,
            "SettingDonate(赞助)" to Screen.SettingDonate,
            "SettingProvider(服务商)" to Screen.SettingProvider,
            "SettingModels(模型)" to Screen.SettingModels,
            "SettingSearch(搜索)" to Screen.SettingSearch,
            "SettingSpeech(语音)" to Screen.SettingSpeech,
            "SettingMcp(MCP)" to Screen.SettingMcp,
            "SettingFiles(文件)" to Screen.SettingFiles,
            "SettingWeb(网络)" to Screen.SettingWeb,
            "SettingAbout(关于)" to Screen.SettingAbout,
            "SettingPreferences(偏好根)" to Screen.SettingPreferences,
            "SettingPreferencesGeneral(通用)" to Screen.SettingPreferencesGeneral,
            "SettingPreferencesUI(界面)" to Screen.SettingPreferencesUI,
            "SettingPreferencesTheme(主题)" to Screen.SettingPreferencesTheme,
            "SettingPreferencesNotification(通知)" to Screen.SettingPreferencesNotification,
            "SettingPreferencesNetwork(网络偏好)" to Screen.SettingPreferencesNetwork,
        )

        sweepPages(pages)
    }

    private fun sweepPages(pages: List<Pair<String, NavKey>>) {
        val crashed = StringBuilder()
        for ((name, screen) in pages) {
            try {
                go(screen)
            } catch (t: Throwable) {
                if (t is UnsatisfiedLinkError) {
                    println("[SWEEP] $name → JVM 无法测（原生库）: ${t.message}")
                    continue
                }
                if (t.javaClass.simpleName == "AppNotIdleException") {
                    // 等价于页面存在"永不停止的动画/轮询"（如无限加载指示器），非直接崩溃；
                    // 记录但不中断——导航树仍可用，继续扫后续页面
                    crashed.append(name).append(" → [不空闲]疑似无限加载动画/轮询: ")
                        .append(t.message?.take(100)).append('\n')
                    continue
                }
                crashed.append(name).append(" → ").append(t).append('\n')
                break // 组合树已破，后续页面结果不可信，先修第一个
            }
        }
        if (crashed.isNotEmpty()) {
            throw AssertionError("以下页面在 JVM 真实渲染时异常（[不空闲]=疑似无限动画需真机确认，其余=等价用户点击卡退）：\n$crashed")
        }
    }

    @Test fun miscPages_allRenderWithoutCrash() {
        bootEmbed()

        val pages: List<Pair<String, NavKey>> = listOf(
            "Debug(调试)" to Screen.Debug,
            "Log(日志)" to Screen.Log,
            "Extensions(扩展)" to Screen.Extensions,
            "QuickMessages(快捷消息)" to Screen.QuickMessages,
            "Prompts(提示词)" to Screen.Prompts,
            "Skills(技能)" to Screen.Skills,
            "Workspaces(工作区)" to Screen.Workspaces,
        )

        sweepPages(pages)
    }
}
