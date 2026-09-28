package me.rerere.rikkahub.dfwx

/**
 * [DFWX PATCH P16] 音量键共享桥。
 *
 * 上游 2.5.5 的 RouteActivity 是独立 Activity，ChatList 通过
 * `activity?.volumeKeyListeners` 注册（上游 ChatList.kt:230）——依赖 Activity 类型。
 *
 * 东方无限是内嵌形态：AppRoutes 跑在宿主 MainActivity 的 ComposeView 里，
 * ChatList 拿到的 activity 是宿主 MainActivity，拿不到 RouteActivity 的
 * 实例成员列表。因此这里提供进程级注册表，两处都走它：
 *
 * - 内嵌态：宿主在 dispatchKeyEvent 里调 [dispatch]（宿主 MainActivity 转发）；
 * - 独立态：RouteActivity.dispatchKeyEvent 先查自己的实例列表（上游语义不变），
 *   再落到本桥（宿主若有转发也能生效）。
 *
 * 语义与上游一致：最后一个注册者优先（LIFO），返回 true 表示已消费、不再下传。
 */
object VolumeKeyBridge {
    private val listeners = mutableListOf<(isVolumeUp: Boolean) -> Boolean>()

    /** 注册监听；返回注销句柄。最后一个注册者优先，与上游 `lastOrNull()` 语义一致。 */
    fun add(listener: (isVolumeUp: Boolean) -> Boolean): () -> Unit {
        listeners.add(listener)
        return { listeners.remove(listener) }
    }

    /** 返回 true 表示已消费。 */
    fun dispatch(isVolumeUp: Boolean): Boolean =
        listeners.lastOrNull()?.invoke(isVolumeUp) == true
}
