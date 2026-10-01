plugins { alias(libs.plugins.android.application) }

tasks.withType<JavaCompile>().configureEach { options.compilerArgs.add("-g:none") }

// [DFWX AI-004] 内置 AI 渠道的构建期注入已移除（含 ai.default.key 读取、resValue 三件套）。
// 用户拍板：不再内置任何 API；后续免费额度只在官方群聊发放，由用户自行在 AI 设置里填写渠道。
// v1.9.0（问题表单#5）：签名口令不入源码，读 local.properties。
// [DFWX-SEC-003] fail-closed：缺失时不再回退硬编码口令——release 构建直接失败并指明缺什么（见 buildTypes 门禁）；
// CI 云构建用 -Pdfwx.unsigned 显式跳过签名出无签名包。
//
// [DFWX] DFW-58 已完成签名证书重建，DFW-57 遗留的三处旧品牌字面量已全部迁移：
//   keystore 文件 heiyao.keystore → dongfang-wuxian.keystore
//   别名     heiyao              → dongfang
//   口令键   heiyao.storePassword/heiyao.keyPassword → dfwx.storePassword/dfwx.keyPassword
// 证书 DN 也从 CN=HeiYao/O=BlackObsidian 换成 CN=Dongfang Wuxian/O=Dongfang Wuxian。
// **注意**：换了证书 = 换了签名，与所有历史包（v1.22.x 及更早）签名不一致，
// 老用户必须先卸载再装新包（用户已确认可重装，且无历史用户需要兼容升级）。
fun signingSecret(name: String): String? = run {
 val f = rootProject.file("local.properties")
 if (f.exists()) f.readLines().firstOrNull { it.trim().startsWith("$name=") }?.substringAfter('=')?.trim()?.ifEmpty { null } else null
}
val dfwxStorePassword: String? = signingSecret("dfwx.storePassword")
val dfwxKeyPassword: String? = signingSecret("dfwx.keyPassword")

android {
 namespace = "cc.nkbr.lanzouplus"
 compileSdk = 37
 buildFeatures { buildConfig = true; aidl = true; resValues = true }  // AGP 9 起 resValues 默认关闭，flavor 的 resValue(app_name) 需要
 // [DFWX BRAND-003] 严禁再加 `--no-xml-namespaces`（v1.0.2 起曾误带 20 余个版本，v1.22.9 移除）。
 // 该参数会把**所有 res 二进制 XML 的命名空间 URI 一并剥离**（对同一份 afdian.xml 做过 A/B 对照：
 // 带该参数时字符串池里 'android' 与 'http://schemas.android.com/apk/res/android' 全消失、属性 ns 字段=-1）。
 // Compose 的矢量图解析走带命名空间的查找（TypedArrayUtils.hasAttribute → getAttributeValue(ANDROID_NS, "viewportWidth")），
 // 命名空间没了就查不到 → getNamedFloat 退回 0f → painterResource 抛
 // "<VectorGraphic> tag requires viewportWidth > 0" → 真机点击闪退（BRAND-003 根因）。
 // 回归守卫：app/src/test/java/cc/nkbr/lanzouplus/ApkXmlNamespaceJvmTest.kt
 androidResources { additionalParameters += listOf("--no-compile-sdk-metadata") }
 defaultConfig {
  applicationId = "dfwx.dongdang"
  minSdk = 26      // v1.8.0：24→26，RikkaHub 模块（convention minSdk 26）清单合并要求
  targetSdk = 37   // v1.8.0：对齐上游 RikkaHub 2.5.1
  // ── 版本号（2026-09-30 归零，用户决定）──────────────────────────────────
  // 历史：v1.2.x → v1.22.19（versionCode 1039039），全部转入 GitHub 预发布作"测试专区"存档。
  // 现在起从 **1.0.0 重新计数**，之后的更新都基于此。
  //
  // 编号规则（新版）：versionCode = major*10000 + minor*100 + patch
  //   1.0.0 → 10000    1.0.1 → 10001    1.1.0 → 10100    2.0.0 → 20000
  // 递增即可被安卓识别为升级；与 versionName 一一对应，便于人核对。
  //
  // 为什么可以归零：用户会卸载旧版重装（无老用户需要兼容升级）。
  // 若将来想改回大数字：只需保证**新的 versionCode 大于所有已发布过的值**。
  versionCode = 10016
  versionName = "1.0.16"
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
 // [DFWX] 品牌守卫（BrandingCleanlinessJvmTest）会**读本脚本本体**校验签名字面量，
 // 但 Gradle 默认不把 build 脚本算作测试输入——实测只改 `keyAlias` 时 testEmptyDebugUnitTest
 // 仍报 UP-TO-DATE，守卫在**最需要它的场景**下假绿。这里显式把脚本声明为输入：脚本一改，测试即重跑。
 tasks.withType<Test>().configureEach {
  inputs.file(file("build.gradle.kts")).withPropertyName("dfwxAppBuildScript")
  // [DFWX] 同理，**多个守卫测试会在运行时直接读源码文本**（品牌清理、M3 token 对齐、文档时效…），
  // 而 Gradle 只跟踪编译产物。不把源码树声明为输入的话，改了源码而测试报 UP-TO-DATE，
  // 守卫就在最该生效的时候静默失效——DFW-57 与 DFW-71 都实测踩到过这个陷阱。
  inputs.dir(file("src/main")).withPropertyName("dfwxAppSources")
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
    // [DFW-73] 内置渠道回归（用户 2026-10-01 拍板，方案 = 服务端中转）：
    //   这里注入的是**我们自己服务器的地址**和**应用令牌**，不是上游中转站地址、也不是真实 Key；
    //   上游地址与真 Key 只存在于服务器 /etc/nginx/dfwx-ai-secret.conf（600），抓包抓不到。
    //   令牌放在客户端就是可被扒的（用户已知情并接受："防君子就行了，靠诚信"）；
    //   万一被白用，服务器换令牌 + 后台下发 ai_token 即可，不必发版。
    //   后台可远程覆盖：RemoteConfigClient 的 control 行 ai_base_url / ai_token / ai_model / ai_max_tokens。
    resValue("string", "dfwx_ai_url", "https://39.106.33.135/ai/v1")
    resValue("string", "dfwx_ai_token", "dfwx277977b74841c78b838e547d8c62df0424c7dcae1c0cab8e")
    resValue("string", "dfwx_ai_model", "deepseek-v4.1-flash")
    resValue("string", "dfwx_ai_max_tokens", "8192")
   // [DFWX AI-004] 原内置渠道 resValue 三件套（dfwx_default_ai_url/_model/_key）已移除：
   // 不再把任何中转站地址、模型或 Key 注入 APK。用户的 AI 渠道全部由用户自行配置。
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
    val dfwxKeystore = rootProject.file("../dongfang-wuxian.keystore")
    // [DFWX-SEC-003] fail-closed 门禁（configuration cache 兼容：纯配置期判断，不挂 taskGraph 钩子）：
    // 本次构建涉及 release 任务而签名要素缺失时直接终止构建；错误只点名缺失项，绝不包含口令值。
    // 聚合任务（build/assemble 等）也会间接产出 release 变体，一并纳入拦截。
    val requestsRelease = gradle.startParameter.taskNames.any {
     it.contains("Release") || it in setOf("build", "assemble", "check", "bundle", "connectedCheck", "connectedAndroidTest")
    }
    if (requestsRelease) {
     val missing = buildList {
      if (!dfwxKeystore.isFile) add("keystore 文件 ${dfwxKeystore.path} 不存在")
      if (dfwxStorePassword == null) add("local.properties 缺少 dfwx.storePassword")
      if (dfwxKeyPassword == null) add("local.properties 缺少 dfwx.keyPassword")
     }
     if (missing.isNotEmpty()) throw GradleException(
      "DFWX-SEC-003：release 签名配置不完整，构建终止（fail-closed）。\n" +
       missing.joinToString("\n") { "  - $it" } +
       "\n  正式出包：在 local.properties 补齐上述签名配置后重试。" +
       "\n  CI 测试：追加 -Pdfwx.unsigned 显式允许无签名包（不适用于正式发布）。"
     )
    }
    signingConfigs {
     create("dfwx") {
      storeFile = dfwxKeystore
      storePassword = dfwxStorePassword
      keyAlias = "dongfang"
      keyPassword = dfwxKeyPassword
     }
    }
    signingConfig = signingConfigs.getByName("dfwx")
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
