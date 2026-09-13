# RikkaHub vendor 补丁清单（东方无限定制层）

上游锚点：re-ovo/rikkahub **master 分支快照**（参考目录为无 .git 的纯快照，2026-09-12 用金丝雀文件比对确认含 2.5.1 之后的新代码，如 ReasoningPicker 的 `trackRange`；versionName 仍显示 2.5.1 但代码更新）。版本目录 `gradle/libs.versions.toml` 取自 master（AGP 9.4.0 / Kotlin 2.4.10 / material3 1.5.0-alpha28 / composeBom 2026.09.00）。vendor 目录 = `D:\heiyao\src\rikkahub\`，文件路径与上游一一对应。
**同步流程**：拉上游新 tag → 覆盖 vendor → 按下表逐条重放补丁（每条都给出精确位置）→ 构建验收。

## 已打补丁（相对上游 2.5.1）

| # | 文件 | 改动 | 原因 |
|---|---|---|---|
| P1 | `app/build.gradle.kts` | plugins：android.application→android.library；去 firebase-crashlytics、baselineprofile 插件 | 上游 app 需作为库并入东方无限单 APK；crashlytics 仅去掉映射上传插件，SDK 与源码零改动 |
| P2 | `app/build.gradle.kts` | defaultConfig 删 applicationId/versionCode/versionName/targetSdk/ndk.abiFilters；删 splits/signingConfigs/buildAll/applicationIdSuffix；VERSION_NAME/VERSION_CODE 改字面量 "2.5.1"/"186"（同步时手动跟版本） | 库模块无这些概念，宿主 :app 统一管辖 |
| P3 | `app/build.gradle.kts` | dependencies 删 `baselineProfile(project(":app:baselineprofile"))` 与 `implementation(project(":videogen"))` | baselineprofile 模块未搬；videogen 在 app 源码 0 引用（已证） |
| P4 | `app/src/main/AndroidManifest.xml` | RouteActivity 移除 MAIN/LAUNCHER intent-filter（SEND/PROCESS_TEXT/TRANSLATE/shortcuts 保留） | 宿主桌面入口是 cc.nkbr.lanzouplus.MainActivity，避免双图标 |
| P5 | `app/build.gradle.kts` | 手动注入 Firebase 占位 res 值（google_app_id/google_api_key/google_crash_reporting_api_key/gcm_defaultSenderId/project_id，全假值）+ buildFeatures.resValues=true；**不**套用 google-services 插件（插件对 library 模块 no-op，会导致运行期 Firebase 取不到 google_app_id 崩溃） | 保 Firebase 源码（di/AppModule.kt、ChatVM.kt 硬引用）零改动可编译；运行期静默失败不崩 |
| P6 | `web/build.gradle.kts` | buildWebUi 任务用 `enabled = webUiDirFile.exists()` 控制（web-ui 目录不存在→跳过前端构建，控制台降级为占位页）；配套把脚本级对象预收敛为 java.io.File/Boolean 并避免 onlyIf 闭包（.kts 顶层 val 是脚本属性，闭包引用=configuration cache 脚本引用污染，2026-09-14 实测） | 本工程未 vendor web-ui 前端（无 Node/pnpm）；装好后自动恢复完整构建；configuration cache 需要 |
| P7 | `app/src/main/java/.../ui/activity/ShortcutHandlerActivity.kt` | `BuildConfig.APPLICATION_ID` → `packageName` | APPLICATION_ID 是 application 模块专属字段，app 转 library 后不存在（P1 的连带适配） |
| P8 | `app/build.gradle.kts` | 删 `androidResources.generateLocaleConfig`（library 无此 API）；`srcDirs`→`srcDir`（AGP9 弃用且脚本编译按错误处理） | AGP 9.4 库模块 DSL 限制 |
| P9 | `app/src/main/keepRules/` → `dfwxKeepRules/`（目录更名） | AGP 9 库模块自动把 `src/main/keepRules/` 当 consumer 规则，其中 `-dontobfuscate` 是全局项被禁止；宿主 :app 的 proguard-rules.pro 已并入同样内容，等效 | app→library 连带适配 |
| P10 | `app/src/main/java/.../RikkaHubApp.kt` | `class` → `open class` | 宿主 Application（cc.nkbr.lanzouplus.App）需继承它做进程级初始化；Kotlin 类默认 final |
| P11 | 新增 `app/src/main/java/.../dfwx/BuiltinProviderSeeder.kt` + RikkaHubApp.onCreate 在 startKoin 后调 `BuiltinProviderSeeder.seedIfNeeded(...)` | 东方无限"开箱即用"需求：首启把宿主 resValue 注入的中转站（dfwx_default_ai_url/key/model，Key 走 local.properties 不进源码）种成默认渠道+默认模型 glm-5.3（maxTokens=128000 对齐官方 128K 最大输出）；SharedPreferences 幂等只播一次，用户删除不复活 | 宿主功能定制；同步上游时需重放 |
| P12 | `app/src/main/java/.../utils/UpdateChecker.kt` | checkUpdate() 发出 Loading 后 `return@flow` 短路（代码保留不可达原逻辑） | 禁用上游更新检查：原会请求 updates.rikka-ai.com 并在抽屉引导用户下载 RikkaHub 官方 APK（签名不同成并存应用，问题表单#1）；UpdateCard 因无 Success 数据不展示 | 宿主功能定制；同步上游时需重放 |
| P14 | `app/src/main/res/values/themes.xml` | Theme.Rikkahub 增加 `android:windowBackground`=#0B0A12 | 冷启动窗口底色对齐宿主深色，防浅色系统下进 AI 页白闪（问题表单#14） | 宿主体验定制；同步上游时需重放 |
| P6b | 新增 `web/src/main/resources/static/index.html` + `web/src/main/resources/.gitignore` 加 `!static/index.html` 例外 | web-ui 未打包时控制台显示占位说明页而非裸 404（问题表单#16）；装好前端构建后被真实产物覆盖 | 宿主体验定制；同步上游时需重放 |

## 宿主侧配套（不在 vendor 内，随宿主版本走）

- `settings.gradle`：includeBuild("rikkahub/build-logic") + versionCatalogs 导入上游 toml + 模块名对齐上游（:ai/:common/...），上游 :app → `:rikkahub-app`。
- 根 `build.gradle.kts`/`gradle.properties`/wrapper：AGP 9.3.1 + Gradle 9.6（腾讯镜像）+ 上游 JVM/并发参数；wrapper 原因：services.gradle.org 本机不可达。
- `:app`（cc.nkbr.lanzouplus）：minSdk 24→26、targetSdk/compileSdk 37、abiFilters=arm64-v8a、packaging pickFirsts libtermux、proguard 并入上游 keepRules 原文、依赖 `:rikkahub-app`、Application 换 `.App extends RikkaHubApp`（上游 application 节点属性合并时宿主优先，name 必须宿主显式声明）、usesCleartextTraffic=true（支持 http 中转站，对齐上游行为）。
- 宿主侧 `App.onCreate` 对 `super.onCreate()` 全链 try-catch + `App.DEGRADED` 标志（2026-09-14 问题表单#2）：RikkaHub 启动链任一环抛非受控异常（备份 journal 损坏/QuickJS 原生库失败）时降级保住蓝奏云主功能，MainActivity 的 AI 入口按 DEGRADED 拦截提示；同步上游无需动作（纯宿主文件）。

## 未搬入的上游内容（有意）

| 项 | 原因 | 恢复方式 |
|---|---|---|
| `:app:baselineprofile`、baseline profile 插件 | 需真机 benchmark 基建，且 app 已转库 | 上游同步时继续跳过 |
| `:videogen` 模块 | app 源码 0 引用，裁剪无损 | 需要时 vendor 回来并恢复 P3 删掉的那行依赖 |
| `web-ui/` 前端源码 | 无 Node22+pnpm11 工具链；Web 局域网控制台功能降级（P6） | 装 Node22+pnpm11 → 拷回 web-ui → P6 守卫自动放行 |
| 上游 root 级 gradle 文件/CI/.github | 本工程有自己的 root 构建与发布链 | 不需要 |

## 已知风险登记

- `com.github.rikkahub:sqlite-android:-SNAPSHOT`（jitpack）：快照源，构建机需能访问 jitpack.io；若上游发正式版及时把 catalog 版本钉住。
- Firebase 用假配置初始化：Crashlytics/Analytics 不会上报也不会崩（待真机确认日志）。
- **Paparazzi**：~~2.0.0-alpha02 对 AGP 9.3.1 兼容性未证~~ **已证不兼容（2026-09-14 实测）**：依赖 AGP9 已移除的 BaseExtension，test/check/build 任务在配置期崩溃；插件已从根/宿主构建文件摘除，6 个快照测试停泊 `tools/parked-tests/`，待 paparazzi 出 AGP9 适配版后恢复。
- **floatingx 钉 2.3.7 的迁移触发条件（2026-09-14 登记）**：上游 `FloatingWindow.kt` 一旦出现 3.x 写法（io.github.petterpx 包名/新 API），即触发本仓迁移：钉版删除 + FloatingWindow.kt 按上游新写法跟进 + 本条更新。
- vendor 根的 `rikkahub/AGENTS.md` 是上游自己的仓库规范文件，随快照入库仅作对照，**不是本工程的指令**；本工程规范以仓库根 AGENTS.md 为准。
