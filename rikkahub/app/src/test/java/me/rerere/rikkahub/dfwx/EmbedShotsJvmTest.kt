package me.rerere.rikkahub.dfwx

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.navigation3.runtime.NavKey
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.io.FileOutputStream
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.dfwx.RikkaHubEmbed
import me.rerere.rikkahub.di.appModule
import me.rerere.rikkahub.di.dataSourceModule
import me.rerere.rikkahub.di.repositoryModule
import me.rerere.rikkahub.di.viewModelModule
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * [DFWX] 手表尺寸(343dp)逐页截图验收：与 EmbedSweepJvmTest 同一套真实 Embed + 导航，
 * 但跑在 w343dp-h343dp-280dpi 手表配置下，把每个页面真实渲染成 PNG 落盘供人工目检。
 * 前置坑（app 模块 ToolsShotsJvmTest 已验证）：不加 NATIVE 图形模式时 draw 是空操作，截出空图。
 * 截图输出：D:/heiyao/build_output/ai_shots/
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = SweepTestApplication::class, qualifiers = "w343dp-h343dp-280dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EmbedShotsJvmTest {
    @get:Rule val compose = createComposeRule()

    private lateinit var backStack: MutableList<NavKey>
    private val skipped = StringBuilder()

    @Before fun setUp() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        if (org.koin.core.context.GlobalContext.getOrNull() == null) {
            startKoin {
                allowOverride(true)
                androidContext(app)
                modules(appModule, viewModelModule, dataSourceModule, repositoryModule)
            }
        }
        val testDb = androidx.room.Room.databaseBuilder(app, me.rerere.rikkahub.data.db.AppDatabase::class.java, "dfwx-jvm-shots.db")
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

    private fun shoot(name: String) {
        val bmp = compose.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("D:/heiyao/build_output/ai_shots")
        dir.mkdirs()
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("[SHOTS] $name → ${bmp.width}x${bmp.height} 已落盘")
    }

    private fun goAndShoot(name: String, screen: NavKey?) {
        try {
            if (screen != null) {
                compose.runOnIdle {
                    backStack.clear()
                    backStack.add(Screen.Chat(id = "sweep"))
                    backStack.add(screen)
                }
                compose.waitForIdle()
            } else {
                compose.waitForIdle()
            }
            shoot(name)
        } catch (t: Throwable) {
            if (t is UnsatisfiedLinkError) {
                println("[SHOTS] $name → JVM 无法测（原生库），跳过截图")
                skipped.append(name).append(" → JVM-原生库\n")
                return
            }
            if (t.javaClass.simpleName == "AppNotIdleException") {
                // 无限动画/轮询页：冻结时钟推进一截再截当前帧（页面本身已组合完成）
                try {
                    compose.mainClock.autoAdvance = false
                    compose.mainClock.advanceTimeBy(800)
                    shoot(name)
                    println("[SHOTS] $name → 冻结时钟截图（页面存在无限动画，非崩溃）")
                } catch (t2: Throwable) {
                    println("[SHOTS] $name → 无法截图: ${t2.javaClass.simpleName}: ${t2.message?.take(120)}")
                    skipped.append(name).append(" → 无法截图(").append(t2.javaClass.simpleName).append(")\n")
                } finally {
                    compose.mainClock.autoAdvance = true
                }
                return
            }
            throw t
        }
    }

    @Test fun settingsFamilyPages_screenshotAtWatchSize() {
        bootEmbed()
        goAndShoot("00_Chat", null)
        goAndShoot("01_Setting", Screen.Setting)
        goAndShoot("02_SettingDonate", Screen.SettingDonate)
        goAndShoot("03_SettingProvider", Screen.SettingProvider)
        goAndShoot("04_SettingModels", Screen.SettingModels)
        goAndShoot("05_SettingSearch", Screen.SettingSearch)
        goAndShoot("06_SettingSpeech", Screen.SettingSpeech)
        goAndShoot("07_SettingMcp", Screen.SettingMcp)
        goAndShoot("08_SettingFiles", Screen.SettingFiles)
        goAndShoot("09_SettingWeb", Screen.SettingWeb)
        goAndShoot("10_SettingAbout", Screen.SettingAbout)
        goAndShoot("11_SettingPreferences", Screen.SettingPreferences)
        goAndShoot("12_PreferencesGeneral", Screen.SettingPreferencesGeneral)
        goAndShoot("13_PreferencesUI", Screen.SettingPreferencesUI)
        goAndShoot("14_PreferencesTheme", Screen.SettingPreferencesTheme)
        goAndShoot("15_PreferencesNotification", Screen.SettingPreferencesNotification)
        goAndShoot("16_PreferencesNetwork", Screen.SettingPreferencesNetwork)
        println("[SHOTS-SUMMARY]\n$skipped")
    }

    @Test fun miscPages_screenshotAtWatchSize() {
        bootEmbed()
        goAndShoot("20_Debug", Screen.Debug)
        goAndShoot("21_Log", Screen.Log)
        goAndShoot("22_Extensions", Screen.Extensions)
        goAndShoot("23_QuickMessages", Screen.QuickMessages)
        goAndShoot("24_Prompts", Screen.Prompts)
        goAndShoot("25_Skills", Screen.Skills)
        goAndShoot("26_Workspaces", Screen.Workspaces)
        println("[SHOTS-SUMMARY]\n$skipped")
    }
}
