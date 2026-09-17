package me.rerere.rikkahub.dfwx

import android.app.Application
import java.io.File

/**
 * [DFWX] JVM 测试专用 Application：Robolectric 默认的 getExternalCacheDir/getExternalFilesDir
 * 返回 null，而 RepositoryModule 等 Koin 单例直接 File(externalCacheDir…) 会 NPE（真机上非空）。
 * 这里保证两个 API 永远返回可用目录，等价真机行为。
 */
class SweepTestApplication : Application() {
    private fun externalDir(): File = File(cacheDir, "external").apply { mkdirs() }
    override fun getExternalCacheDir(): File? = externalDir()
    override fun getExternalFilesDir(type: String?): File? = File(externalDir(), type ?: "files").apply { mkdirs() }

    override fun onCreate() {
        super.onCreate()
        // RepositoryModule 的 ProotShellRunner 读 applicationInfo.nativeLibraryDir（真机=arm64 .so 目录，
        // Robolectric 上为 null）→ JVM 测试给个假目录，WorkspaceManager 创建不崩即可（不会真执行 proot）
        applicationInfo.nativeLibraryDir = File(cacheDir, "native").apply { mkdirs() }.absolutePath
        // appModule 的 FirebaseAnalytics 需要 FirebaseApp 已初始化（真机由 FirebaseInitProvider 兜底，
        // JVM 测试进程没有该 provider）→ 用 defaultConfig 里的占位 resValue 手动初始化
        com.google.firebase.FirebaseApp.initializeApp(this)
    }
}
