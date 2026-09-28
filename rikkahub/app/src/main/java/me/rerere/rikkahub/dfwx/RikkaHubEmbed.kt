package me.rerere.rikkahub.dfwx

import android.content.Context
import androidx.compose.runtime.Composable
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.network.cachecontrol.CacheControlCacheStrategy
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import me.rerere.rikkahub.ui.routes.AppRoutes
import me.rerere.rikkahub.ui.routes.DeepLinkSink
import me.rerere.rikkahub.ui.routes.NoDeepLinks
import okhttp3.OkHttpClient
import org.koin.compose.koinInject

/**
 * [DFWX PATCH P16 v2.5.5] 可内嵌的 RikkaHub 界面入口（宿主东方无限底栏"AI"页直接承载）。
 *
 * 上游 2.5.5 起路由表已抽为顶层 `AppRoutes`（ui/routes/AppRoutes.kt），本文件只负责
 * 内嵌形态独有的两件事：
 *   1. 装配 Coil 单例图片加载器（走共享 OkHttpClient，复用宿主代理与证书配置）；
 *   2. 决定 DeepLinkSink——生产路径用 [NoDeepLinks]（内嵌页不接收 SEND/PROCESS_TEXT intent，
 *      那些只在独立入口 RouteActivity 分发）；JVM 测试可注入自定义 sink 以取得 backStack
 *      驱动导航（见 EmbedShotsJvmTest / EmbedSweepJvmTest）。
 *
 * 主题由调用方单层施加（见 AiPageHost.createRikkaHubEmbedView，P18 强制深色），
 * 本文件不再包裹主题——2.5.4 时代的双层主题是动画/适配异常的来源之一。
 */
@Composable
fun RikkaHubEmbed(
    activity: Context,
    deepLinks: DeepLinkSink = NoDeepLinks,
) {
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
    AppRoutes(
        context = activity,
        deepLinks = deepLinks,
    )
}
