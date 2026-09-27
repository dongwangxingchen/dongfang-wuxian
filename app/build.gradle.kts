plugins { alias(libs.plugins.android.application) }

tasks.withType<JavaCompile>().configureEach { options.compilerArgs.add("-g:none") }

// v1.7.0 开源合规：默认 AI 渠道的 Key 不进源码。优先读 local.properties 的 ai.default.key（该文件不入 git）；
// 未配置时注入空串，App 首启该渠道留空、由用户自行填写。
val defaultAiKey: String = run {
 val f = rootProject.file("local.properties")
 if (f.exists()) f.readLines().firstOrNull { it.trim().startsWith("ai.default.key=") }?.substringAfter('=')?.trim() ?: "" else ""
}
// v1.9.0（问题表单#5）：签名口令不入源码，读 local.properties 的 heiyao.storePassword/heiyao.keyPassword。
// [DFWX-SEC-003] fail-closed：缺失时不再回退硬编码口令——release 构建直接失败并指明缺什么（见 buildTypes 门禁）；
// CI 云构建用 -Pdfwx.unsigned 显式跳过签名出无签名包。
fun signingSecret(name: String): String? = run {
 val f = rootProject.file("local.properties")
 if (f.exists()) f.readLines().firstOrNull { it.trim().startsWith("$name=") }?.substringAfter('=')?.trim()?.ifEmpty { null } else null
}
val heiyaoStorePassword: String? = signingSecret("heiyao.storePassword")
val heiyaoKeyPassword: String? = signingSecret("heiyao.keyPassword")

android {
 namespace = "cc.nkbr.lanzouplus"
 compileSdk = 37
 buildFeatures { buildConfig = true; aidl = true; resValues = true }  // AGP 9 起 resValues 默认关闭，flavor 的 resValue(app_name) 需要
 androidResources { additionalParameters += listOf("--no-xml-namespaces", "--no-compile-sdk-metadata") }
 defaultConfig {
  applicationId = "dfwx.dongdang"
  minSdk = 26      // v1.8.0：24→26，RikkaHub 模块（convention minSdk 26）清单合并要求
  targetSdk = 37   // v1.8.0：对齐上游 RikkaHub 2.5.1
  versionCode = 1039025
  versionName = "1.22.5"
 }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 packaging {
  jniLibs { useLegacyPackaging = true; pickFirsts += "lib/*/libtermux.so" }  // 对齐上游：workspace/termux 双处提供同名 so
 }
 // [DFWX] Robolectric JVM 点击测试（工具箱 35 页等 Java UI）：无真机/无模拟器环境的验收路线
 testOptions {
  unitTests {
   isIncludeAndroidResources = true
   all { it.maxHeapSize = "3g" }
  }
 }
 flavorDimensions += "catalog"
 productFlavors {
  create("empty") {
   dimension = "catalog"
   isDefault = true
   applicationId = "dfwx.dongdang"
   buildConfigField("boolean", "IS_FULL", "false")
   buildConfigField("String", "OFFICIAL_URL", "\"https://github.com/dongwangxingchen/dongfang-wuxian\"")
   // v1.9.1：应用名改用独立资源名 dfwx_app_name——rikkahub 库在 values-zh 等 6 个语言里也定义了
   // app_name="RikkaHub"，中文系统资源解析优先 values-zh，会导致桌面名字变成 RikkaHub（真机实测）。
   resValue("string", "dfwx_app_name", "东方无限")
   // v1.8.1：内置默认 AI 渠道（RikkaHub 播种器 dfwx/BuiltinProviderSeeder 读取；Key 走 local.properties 不进源码）
   resValue("string", "dfwx_default_ai_url", "https://www.<已停用渠道>/v1")
   resValue("string", "dfwx_default_ai_model", "glm-5.3")
   resValue("string", "dfwx_default_ai_key", defaultAiKey)
  }
 }
 buildTypes {
  getByName("release") {
   isMinifyEnabled = true; isShrinkResources = true
   proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
   // v1.19.2：GitHub Actions 云构建用 -Pdfwx.unsigned 跳过签名（keystore 在仓库外，CI 上不存在；
   // 正式签名仍在本地出包，CI 先验证工具链）。开关关闭时行为与历史完全一致。
   val ciUnsigned = providers.gradleProperty("dfwx.unsigned").isPresent
   if (!ciUnsigned) {
    val heiyaoKeystore = rootProject.file("../heiyao.keystore")
    // [DFWX-SEC-003] fail-closed 门禁（configuration cache 兼容：纯配置期判断，不挂 taskGraph 钩子）：
    // 本次构建涉及 release 任务而签名要素缺失时直接终止构建；错误只点名缺失项，绝不包含口令值。
    // 聚合任务（build/assemble 等）也会间接产出 release 变体，一并纳入拦截。
    val requestsRelease = gradle.startParameter.taskNames.any {
     it.contains("Release") || it in setOf("build", "assemble", "check", "bundle", "connectedCheck", "connectedAndroidTest")
    }
    if (requestsRelease) {
     val missing = buildList {
      if (!heiyaoKeystore.isFile) add("keystore 文件 ${heiyaoKeystore.path} 不存在")
      if (heiyaoStorePassword == null) add("local.properties 缺少 heiyao.storePassword")
      if (heiyaoKeyPassword == null) add("local.properties 缺少 heiyao.keyPassword")
     }
     if (missing.isNotEmpty()) throw GradleException(
      "DFWX-SEC-003：release 签名配置不完整，构建终止（fail-closed）。\n" +
       missing.joinToString("\n") { "  - $it" } +
       "\n  正式出包：在 local.properties 补齐上述签名配置后重试。" +
       "\n  CI 测试：追加 -Pdfwx.unsigned 显式允许无签名包（不适用于正式发布）。"
     )
    }
    signingConfigs {
     create("heiyao") {
      storeFile = heiyaoKeystore
      storePassword = heiyaoStorePassword
      keyAlias = "heiyao"
      keyPassword = heiyaoKeyPassword
     }
    }
    signingConfig = signingConfigs.getByName("heiyao")
   }
   ndk { abiFilters += "arm64-v8a" }  // v1.18.0 修复：release 只出 arm64（真机）——按构建类型静态判断，与任务名无关
  }
  getByName("debug") {
   ndk { abiFilters += "x86_64" }  // debug 出 x86_64 供模拟器/测试（AGP9 Variant.ndk 已移除，改经典 DSL 按构建类型配置）
  }
 }
}

dependencies {
 implementation(project(":rikkahub-app"))   // v1.8.0 整搬 RikkaHub（UI/数据/网络全量，见 rikkahub/ 目录）
 implementation("androidx.activity:activity:1.13.0")   // v1.10.0 内嵌 AI 页：MainActivity 需作为 Compose 的 OnBackPressedDispatcherOwner（版本对齐上游 catalog activityCompose）
 implementation("dev.rikka.shizuku:api:13.1.5")
 implementation("dev.rikka.shizuku:provider:13.1.5")
 // v1.19.7：弹簧按压反馈基座（wear-ui-system.md §10 a 类，用户 2026-09-22 批准；1.1.0 已在 gradle 缓存，rikkahub 传递同源）
 implementation("androidx.dynamicanimation:dynamicanimation:1.1.0")   // v1.22.3 恢复：v1.19.8 删弹簧时误删，v1.22.2 用户反馈按压僵硬（"点到石头上"），按 §2/§3 弹簧配方统一全站按压
 compileOnly("androidx.annotation:annotation:1.3.0")
 // Robolectric JVM 点击测试（与 rikkahub-app 同版本，测试放 src/test/java/cc/nkbr/lanzouplus/）
 testImplementation(libs.junit)
 testImplementation(libs.robolectric)
 testImplementation("androidx.test:core:1.7.0")
}
