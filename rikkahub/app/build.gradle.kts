// ============================================================================
// [DFWX PATCH] 本文件基于上游 re-ovo/rikkahub tag 2.5.5 的 app/build.gradle.kts
// 做了 application→library 转换（东方无限整搬）。相对上游的全部改动：
//  1. plugins：android.application → android.library；移除 firebase-crashlytics、
//     baselineprofile 插件（Firebase SDK 依赖与源码零改动，仅去映射上传；Firebase 占位
//     res 值在 defaultConfig 注入，见 P5）。
//  2. defaultConfig：移除 applicationId/versionCode/versionName/targetSdk/ndk
//     abiFilters（由宿主 :app 即 cc.nkbr.lanzouplus 统一管辖，本模块为库）。
//  3. 移除 splits、signingConfigs、buildAll 任务、debug.applicationIdSuffix。
//  4. buildConfigField 的 VERSION_NAME/VERSION_CODE 由动态读取改为字面量
//     "2.5.5"/"190" —— 【同步上游新版本时需手动同步这两个值】。
//  5. dependencies：移除 baselineProfile(project(":app:baselineprofile")) 与
//     implementation(project(":videogen"))（app 源码 0 处引用 videogen，已证可裁）。
//  6. 移除 implementation(libs.firebase.crashlytics) 与 di/AppModule 的注销装配（P15）。
//  7. 保留 Robolectric JVM 测试基建（testOptions + 测试依赖，宿主自有扫描测试用）。
//  8. compileSdk 保留 `compileSdk = 37` 旧写法（库模块已验证可用；上游 application
//     模块使用 `compileSdk { version = release(37) { minorApiLevel = 2 } }` 新 DSL）。
// 同步上游更新时：覆盖本文件为上游新版后，按上述 8 条重放。
// ============================================================================
import com.android.build.api.dsl.Packaging
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    // [DFWX PATCH P1] 上游为 android.application；本模块作为库并入东方无限单 APK
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    // [DFWX PATCH P1] 上游在此处声明 google.services / firebase.crashlytics / baselineprofile：
    //  google-services 插件在 library 模块上直接 no-op（打印警告不生成资源），
    //  会导致运行期 Firebase.crashlytics/analytics 取不到 google_app_id 而抛异常。
    //  改为在 defaultConfig 手动注入 Firebase 必需的 res 值（等价于插件生成的 values.xml）。
    //  crashlytics 插件与 baselineprofile 插件同理移除（见 P15 / P3）。
}

android {
    namespace = "me.rerere.rikkahub"
    // [DFWX PATCH] 库模块沿用旧 DSL（上游 application 模块已切 compileSdk { version = release(37) }
    //  新写法）；若未来 AGP 强制新 DSL，改此处即可。
    compileSdk = 37

    defaultConfig {
        // [DFWX PATCH P2] 上游在此声明 applicationId/minSdk/targetSdk/versionCode/versionName
        //  与 ndk.abiFilters：库模块无这些概念，全部由宿主 :app 统一管辖。
        minSdk = 26

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // [DFWX PATCH P5] 占位 Firebase 配置（原方案=google-services 插件+假 json，但插件对库模块无效）。
        // 值全部为假：Firebase 初始化可完成，上报静默失败，不崩、不外发。同步上游无需动。
        resValue("string", "google_app_id", "1:000000000000:android:0000000000000000000000")
        resValue("string", "google_api_key", "AIzaSyA0000000000000000000000000000000000")
        resValue("string", "google_crash_reporting_api_key", "AIzaSyA0000000000000000000000000000000000")
        resValue("string", "gcm_defaultSenderId", "000000000000")
        resValue("string", "project_id", "dfwx-placeholder")
    }

    // [DFWX PATCH P2] 上游此处有 splits 块（ABI 分包 + universal APK）：库模块不产出 APK，移除。

    // [DFWX PATCH P2] 上游此处有 signingConfigs（读 local.properties 签名）：由宿主 :app 管辖，移除。

    buildTypes {
        release {
            // [DFWX PATCH P2] 上游此处为 signingConfig = signingConfigs.getByName("release")，随签名配置移除。
            // [DFWX PATCH P4/VERSION] 上游此处动态读取 defaultConfig 的版本；库模块无 defaultConfig 版本，
            //  改字面量 —— 【同步上游新版本时需手动同步这两个值】。
            buildConfigField("String", "VERSION_NAME", "\"2.5.5\"")
            buildConfigField("String", "VERSION_CODE", "\"190\"")
        }
        debug {
            // [DFWX PATCH P2] 上游此处有 applicationIdSuffix = ".debug"，随 applicationId 移除。
            buildConfigField("String", "VERSION_NAME", "\"2.5.5\"")
            buildConfigField("String", "VERSION_CODE", "\"190\"")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true   // [DFWX PATCH P5] AGP 9 默认关闭，上面 Firebase 占位 resValue 需要
    }
    sourceSets {
        // [DFWX PATCH P8] 上游为 srcDirs(...)（AGP9 弃用且脚本编译按错误处理），改单数 srcDir。
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }
    // [DFWX PATCH P8] 上游此处有 androidResources { generateLocaleConfig = true }（library 无此 API），移除。
    packaging {
        jniLibs {
            useLegacyPackaging = true
            pickFirsts += "lib/*/libtermux.so"
        }
    }
    // [DFWX PATCH] Robolectric JVM 点击测试：无真机/无模拟器环境下在开发机上直接渲染 Compose 页面做回归
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            // 整个 Embed 外壳 + Robolectric 本身吃内存，默认 512m~1g 在扫描后段会 OOM
            all { it.maxHeapSize = "4g" }
        }
    }
    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions.optIn.add("androidx.compose.material3.ExperimentalMaterial3Api")
        compilerOptions.optIn.add("androidx.compose.material3.ExperimentalMaterial3ExpressiveApi")
        compilerOptions.optIn.add("androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi")
        compilerOptions.optIn.add("androidx.compose.animation.ExperimentalAnimationApi")
        compilerOptions.optIn.add("androidx.compose.animation.ExperimentalSharedTransitionApi")
        compilerOptions.optIn.add("androidx.compose.foundation.ExperimentalFoundationApi")
        compilerOptions.optIn.add("androidx.compose.foundation.layout.ExperimentalLayoutApi")
        compilerOptions.optIn.add("kotlin.uuid.ExperimentalUuidApi")
        compilerOptions.optIn.add("kotlin.time.ExperimentalTime")
        compilerOptions.optIn.add("kotlinx.coroutines.ExperimentalCoroutinesApi")
        compilerOptions.optIn.add("androidx.navigation3.runtime.ExperimentalNavigation3Api")
    }
}

composeCompiler {
    stabilityConfigurationFiles.add(
        project.layout.projectDirectory.file("compose_compiler_config.conf")
    )
}

// [DFWX PATCH P2] 上游此处注册 buildAll 任务（assembleRelease + bundleRelease）：库模块不适用，移除。

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

// Local JVM tests need the desktop native library instead of the Android AAR.
configurations.matching { it.name.endsWith("UnitTestRuntimeClasspath") }.configureEach {
    resolutionStrategy.dependencySubstitution {
        substitute(module("io.github.dokar3:quickjs-kt-android"))
            .using(module("io.github.dokar3:quickjs-kt-jvm:${libs.versions.quickjs.get()}"))
    }
}

dependencies {
    implementation(libs.quickjs)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.termux.terminal.view)
    implementation(libs.guava.listenablefuture)

    // Compose
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material3.adaptive)
    implementation(libs.androidx.material3.adaptive.layout)

    // Navigation 3
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.material3.adaptive.navigation3)

    // [DFWX PATCH P32] firebase-analytics 移除（DFW-9，对外承诺"无追踪"）。
    // BOM 必须保留：它自身不引入任何依赖，但 MLKit barcode-scanning 传递依赖
    // firebase-encoders / datatransport，去掉 BOM 后这些传递依赖会漂到未缓存版本（离线构建直接失败）。
    // 保留 BOM = 只做版本对齐，不新增遥测组件。
    implementation(platform(libs.firebase.bom))
    // [DFWX PATCH P15] crashlytics SDK 移除：无官方构建插件时其 Firebase EAGER 组件
    // 因 build ID 缺失在 FirebaseInitProvider 阶段崩溃（早于 Application），真机实测；
    // 代码侧配套删除 AppModule 的 crashlytics 装配（全仓零消费点）。同步上游需重放。

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // Image metadata extractor
    // https://github.com/drewnoakes/metadata-extractor
    implementation(libs.metadata.extractor)

    // Haze (background blur and glass)
    implementation(libs.haze)
    implementation(libs.haze.blur)
    implementation(libs.haze.blur.material3)
    implementation(libs.haze.glass)
    implementation(libs.haze.glass.material3)

    // koin
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)
    implementation(libs.koin.compose)
    implementation(libs.koin.androidx.workmanager)

    // jetbrains markdown parser
    implementation(libs.jetbrains.markdown)

    // okhttp
    implementation(libs.okhttp)
    implementation(libs.okhttp.sse)
    implementation(libs.retrofit)
    implementation(libs.retrofit.serialization.json)

    // ktor client
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    // ucrop
    implementation(libs.ucrop)

    // pebble (template engine)
    implementation(libs.pebble)

    // java-diff-utils (unified diff)
    implementation(libs.diffutils)

    // coil
    implementation(libs.coil.compose)
    implementation(libs.coil.gif)
    implementation(libs.coil.okhttp)
    implementation(libs.coil.svg)
    implementation(libs.coil.cache.control)

    // serialization
    implementation(libs.kotlinx.serialization.json)

    // YAML front matter
    implementation(libs.snakeyaml)

    // zxing
    implementation(libs.zxing.core)

    // quickie (qrcode scanner)
    implementation(libs.quickie.bundled)
    implementation(libs.barcode.scanning)
    implementation(libs.androidx.camera.core)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.paging)
    // [DFWX PATCH P3] 上游此处有 baselineProfile(project(":app:baselineprofile"))：模块未搬，移除。
    ksp(libs.androidx.room.compiler)

    // Paging3
    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.paging.compose)

    // Apache Commons Text
    implementation(libs.commons.text)

    // Toast (Sonner)
    implementation(libs.sonner)

    // Reorderable (https://github.com/Calvin-LL/Reorderable/)
    implementation(libs.reorderable)

    // lucide icons
    implementation(libs.lucide.icons)
    implementation(libs.huge.icons)

    // image viewer
    implementation(libs.image.viewer)

    // JLatexMath
    // https://github.com/rikkahub/jlatexmath-android
    implementation(libs.jlatexmath)
    implementation(libs.jlatexmath.font.greek)
    implementation(libs.jlatexmath.font.cyrillic)

    // mcp
    implementation(libs.modelcontextprotocol.kotlin.sdk)

    // jmDNS (mDNS/Bonjour for .local hostname)
    implementation(libs.jmdns)

    // SLF4J Android binding — routes Ktor/SLF4J logs to logcat
    implementation(libs.slf4j.api)
    implementation(libs.slf4j.android)

    // sqlite-android (requery SQLite for Android)
    implementation(libs.sqlite.android)

    // modules
    implementation(project(":ai"))
    implementation(project(":web"))
    implementation(project(":document"))
    implementation(project(":highlight"))
    implementation(project(":search"))
    implementation(project(":speech"))
    // [DFWX PATCH P3] 上游此处有 implementation(project(":videogen"))：app 源码 0 处引用，移除。
    implementation(project(":common"))
    implementation(project(":material3"))
    implementation(project(":workspace"))
    implementation(project(":oauth"))
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar", "*.aar"))))
    implementation(kotlin("reflect"))

    // Leak Canary
    // debugImplementation(libs.leakcanary.android)

    // tests
    testImplementation(libs.junit)
    // [DFWX PATCH] Robolectric JVM 测试链（宿主自有扫描/回归测试用；上游无）
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.ui.test.junit4)
    testImplementation(libs.androidx.ui.test.manifest)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    androidTestImplementation(libs.androidx.room.testing)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
