plugins { alias(libs.plugins.android.application) }

tasks.withType<JavaCompile>().configureEach { options.compilerArgs.add("-g:none") }

// v1.7.0 开源合规：默认 AI 渠道的 Key 不进源码。优先读 local.properties 的 ai.default.key（该文件不入 git）；
// 未配置时注入空串，App 首启该渠道留空、由用户自行填写。
val defaultAiKey: String = run {
 val f = rootProject.file("local.properties")
 if (f.exists()) f.readLines().firstOrNull { it.trim().startsWith("ai.default.key=") }?.substringAfter('=')?.trim() ?: "" else ""
}
// v1.9.0（问题表单#5）：签名口令不入源码，读 local.properties 的 heiyao.storePassword/heiyao.keyPassword
// （缺失时回退原值，保证无该文件的构建环境仍可出包）。
val heiyaoStorePassword: String = run {
 val f = rootProject.file("local.properties")
 if (f.exists()) f.readLines().firstOrNull { it.trim().startsWith("heiyao.storePassword=") }?.substringAfter('=')?.trim() ?: "heiyao2026" else "heiyao2026"
}
val heiyaoKeyPassword: String = run {
 val f = rootProject.file("local.properties")
 if (f.exists()) f.readLines().firstOrNull { it.trim().startsWith("heiyao.keyPassword=") }?.substringAfter('=')?.trim() ?: "heiyao2026" else "heiyao2026"
}

android {
 namespace = "cc.nkbr.lanzouplus"
 compileSdk = 37
 buildFeatures { buildConfig = true; aidl = true; resValues = true }  // AGP 9 起 resValues 默认关闭，flavor 的 resValue(app_name) 需要
 androidResources { additionalParameters += listOf("--no-xml-namespaces", "--no-compile-sdk-metadata") }
 defaultConfig {
  applicationId = "dfwx.dongdang"
  minSdk = 26      // v1.8.0：24→26，RikkaHub 模块（convention minSdk 26）清单合并要求
  targetSdk = 37   // v1.8.0：对齐上游 RikkaHub 2.5.1
  versionCode = 1032000
  versionName = "1.13.0"
  ndk { abiFilters += listOf("arm64-v8a") }  // RikkaHub native（quickjs/sqlite/termux）只出 arm64：真机 arm64，x86_64 会使体积翻倍
 }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 packaging {
  jniLibs { useLegacyPackaging = true; pickFirsts += "lib/*/libtermux.so" }  // 对齐上游：workspace/termux 双处提供同名 so
 }
 flavorDimensions += "catalog"
 productFlavors {
  create("empty") {
   dimension = "catalog"
   isDefault = true
   applicationId = "dfwx.dongdang"
   buildConfigField("boolean", "IS_FULL", "false")
   buildConfigField("String", "OFFICIAL_URL", "\"https://github.com/nekobyran/lanzouplus\"")
   // v1.9.1：应用名改用独立资源名 dfwx_app_name——rikkahub 库在 values-zh 等 6 个语言里也定义了
   // app_name="RikkaHub"，中文系统资源解析优先 values-zh，会导致桌面名字变成 RikkaHub（真机实测）。
   resValue("string", "dfwx_app_name", "东方无限")
   // v1.8.1：内置默认 AI 渠道（RikkaHub 播种器 dfwx/BuiltinProviderSeeder 读取；Key 走 local.properties 不进源码）
   resValue("string", "dfwx_default_ai_url", "https://www.aizhongzhuan.cc/v1")
   resValue("string", "dfwx_default_ai_model", "glm-5.3")
   resValue("string", "dfwx_default_ai_key", defaultAiKey)
  }
 }
 buildTypes {
  getByName("release") {
   isMinifyEnabled = true; isShrinkResources = true
   proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
   signingConfigs {
    create("heiyao") {
     storeFile = rootProject.file("../heiyao.keystore")
     storePassword = heiyaoStorePassword
     keyAlias = "heiyao"
     keyPassword = heiyaoKeyPassword
    }
   }
   signingConfig = signingConfigs.getByName("heiyao")
  }
 }
}

dependencies {
 implementation(project(":rikkahub-app"))   // v1.8.0 整搬 RikkaHub（UI/数据/网络全量，见 rikkahub/ 目录）
 implementation("androidx.activity:activity:1.13.0")   // v1.10.0 内嵌 AI 页：MainActivity 需作为 Compose 的 OnBackPressedDispatcherOwner（版本对齐上游 catalog activityCompose）
 implementation("dev.rikka.shizuku:api:13.1.5")
 implementation("dev.rikka.shizuku:provider:13.1.5")
 compileOnly("androidx.annotation:annotation:1.3.0")
}
