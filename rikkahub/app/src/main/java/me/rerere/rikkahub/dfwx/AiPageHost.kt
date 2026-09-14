package me.rerere.rikkahub.dfwx

import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import me.rerere.rikkahub.ui.theme.RikkahubTheme
import me.rerere.rikkahub.utils.openUsageAccessSettings

/**
 * [DFWX PATCH P16] 供宿主（纯 Java）创建内嵌 AI 界面的桥接入口：
 * 宿主 MainActivity（androidx.activity.ComponentActivity）把返回的 ComposeView
 * 挂进自己的页面容器即可；返回键经宿主 OnBackPressedDispatcher 自然桥接。
 */
fun createRikkaHubEmbedView(activity: ComponentActivity): android.view.View {
    val view = ComposeView(activity)
    view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    view.setContent {
        RikkahubTheme {
            RikkaHubEmbed(
                activity = activity,
                onBackStackReady = { },
                onOpenUsageAccessSettings = { activity.openUsageAccessSettings() },
            )
        }
    }
    return view
}
