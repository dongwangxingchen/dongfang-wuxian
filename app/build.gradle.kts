plugins { alias(libs.plugins.android.application); id("app.cash.paparazzi") }

tasks.withType<JavaCompile>().configureEach { options.compilerArgs.add("-g:none") }

// v1.7.0 开源合规：默认 AI 渠道的 Key 不进源码。优先读 local.properties 的 ai.default.key（该文件不入 git）；
// 未配置时注入空串，App 首启该渠道留空、由用户自行填写。
val defaultAiKey: String = run {
 val f = rootProject.file("local.properties")
 if (f.exists()) f.readLines().firstOrNull { it.trim().startsWith("ai.default.key=") }?.substringAfter('=')?.trim() ?: "" else ""
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
  versionCode = 1030024
  versionName = "1.8.1"
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
   resValue("string", "app_name", "东方无限")
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
     storePassword = "heiyao2026"
     keyAlias = "heiyao"
     keyPassword = "heiyao2026"
    }
   }
   signingConfig = signingConfigs.getByName("heiyao")
  }
 }
}

dependencies {
 implementation(project(":rikkahub-app"))   // v1.8.0 整搬 RikkaHub（UI/数据/网络全量，见 rikkahub/ 目录）
 implementation("dev.rikka.shizuku:api:13.1.5")
 implementation("dev.rikka.shizuku:provider:13.1.5")
 compileOnly("androidx.annotation:annotation:1.3.0")
}
