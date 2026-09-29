# RikkaHub vendor 补丁清单（东方无限定制层）

**当前上游锚点：re-ovo/rikkahub tag `2.5.5`（versionCode 190 / versionName "2.5.5"），2026-09-28 clean import。**
源码快照放在 `/tmp/rikkahub-255/rikkahub-2.5.5/`（临时目录，重装需重新下载）；回退点 = git tag `dfwx-pre-255-import`；旧补丁台账备份 = `~/heiyao/PATCHES-231-backup-20260928.md`。

**同步流程**：拉上游新 tag → 按 `/tmp/rsync-excludes.txt` 的排除清单做目录级 rsync 覆盖 vendor → 按下表逐条重放补丁 → 构建 + 测试验收。
（历史：2.5.1 master 快照 → 2026-09-28 升 2.5.5。旧台账里 2.5.1 时代的路径与描述已在本次重写中更新，逐条对照 P1-P25。）

## 本次（2.5.5）结构变更要点

1. **上游 2.5.5 把路由表抽成了 `RouteActivity.AppRoutes()`**（仍是成员函数）。东方无限把 AI 页做成内嵌形态，需要顶层函数 → vendored 为 `ui/routes/AppRoutes.kt`（见 P16）。这是让"跟随上游更新"变成可行的关键：不再维护 400+ 行复制品，只维护 4 处差异。
2. **新增依赖**：haze-glass / haze-glass-material3（2.5.5 新增，已在版本目录对齐）。
3. **版本目录**：`gradle/libs.versions.toml` 逐条手改对齐（不可整份覆盖，本仓有 robolectric 等自有条目）。
4. **material-color-utilities**：上游 2.5.5 该目录为空，本仓保留 43 个 .kt（`material3/build.gradle.kts` 的 `kotlin.srcDir` 依赖它）。**rsync 时必须保护**。
5. **quickjs 换库**：上游已从 `wang.harlon.quickjs:wrapper-android` 换成 `io.github.dokar3:quickjs-kt:1.0.15`（API 完全不同）。
6. **floatingx 3.0.0**：上游已迁到 `io.github.petterpx:floatingx-app`（PATCHES 旧登记的迁移触发条件已满足，本仓跟进）。

## 已打补丁（相对上游 2.5.5）

| # | 文件 | 改动 | 原因 |
|---|---|---|---|
| P1 | `app/build.gradle.kts` | plugins：android.application→android.library；去 firebase-crashlytics、baselineprofile 插件 | 上游 app 需作为库并入东方无限单 APK；crashlytics 仅去掉映射上传插件，SDK 与源码零改动 |
| P2 | `app/build.gradle.kts` | defaultConfig 删 applicationId/versionCode/versionName/targetSdk/ndk.abiFilters；删 splits/signingConfigs/buildAll/applicationIdSuffix；VERSION_NAME/VERSION_CODE 改字面量 **"2.5.5"/"190"**（同步时手动跟版本）；compileSdk 沿用旧 DSL `compileSdk = 37`（上游已改 `compileSdk { version = release(37) { minorApiLevel = 2 } }`，库模块不需要） | 库模块无这些概念，宿主 :app 统一管辖 |
| P3 | `app/build.gradle.kts` | dependencies 删 `baselineProfile(project(":app:baselineprofile"))` 与 `implementation(project(":videogen"))` | baselineprofile 模块未搬；videogen 在 app 源码 0 引用（已证） |
| P4 | `app/src/main/AndroidManifest.xml` | RouteActivity 移除 MAIN/LAUNCHER intent-filter（SEND/PROCESS_TEXT/TRANSLATE/shortcuts 保留） | 宿主桌面入口是 cc.nkbr.lanzouplus.MainActivity，避免双图标 |
| P5 | `app/build.gradle.kts` | 手动注入 Firebase 占位 res 值（google_app_id/google_api_key/google_crash_reporting_api_key/gcm_defaultSenderId/project_id，全假值）+ buildFeatures.resValues=true；**不**套用 google-services 插件（插件对 library 模块 no-op，会导致运行期 Firebase 取不到 google_app_id 崩溃） | 保 Firebase 源码（di/AppModule.kt、ChatVM.kt 硬引用）零改动可编译；运行期静默失败不崩 |
| P6 | `web/build.gradle.kts` | buildWebUi 任务用 `enabled = webUiDirFile.exists()` 控制（web-ui 目录不存在→跳过前端构建，控制台降级为占位页）；配套把脚本级对象预收敛为 java.io.File/Boolean 并避免 onlyIf 闭包（.kts 顶层 val 是脚本属性，闭包引用=configuration cache 脚本引用污染，2026-09-14 实测） | 本工程未 vendor web-ui 前端（无 Node/pnpm）；装好后自动恢复完整构建；configuration cache 需要 |
| P7 | `app/src/main/java/.../ui/activity/ShortcutHandlerActivity.kt` | `BuildConfig.APPLICATION_ID` → `packageName` | APPLICATION_ID 是 application 模块专属字段，app 转 library 后不存在（P1 的连带适配） |
| P8 | `app/build.gradle.kts` | 删 `androidResources.generateLocaleConfig`（library 无此 API）；`srcDirs`→`srcDir`（AGP9 弃用且脚本编译按错误处理） | AGP 9.4 库模块 DSL 限制 |
| P9 | `app/src/main/keepRules/` → `dfwxKeepRules/`（目录更名） | AGP 9 库模块自动把 `src/main/keepRules/` 当 consumer 规则，其中 `-dontobfuscate` 是全局项被禁止；宿主 :app 的 proguard-rules.pro 已并入同样内容，等效 | app→library 连带适配 |
| P10 | `app/src/main/java/.../RikkaHubApp.kt` | `class` → `open class` | 宿主 Application（cc.nkbr.lanzouplus.App）需继承它做进程级初始化；Kotlin 类默认 final |
| P11 | **【AI-004 已整体删除】** 原为新增 `dfwx/BuiltinProviderSeeder.kt` + RikkaHubApp 播种内置渠道（v1.8.1–v1.22.6） | 用户 2026-09-28 拍板：不再内置任何 API，后续免费额度只在官方群聊发放、由用户自行配置。构建期 resValue 注入（`dfwx_default_ai_url/_model/_key`）与 `keep.xml` 对应条目已一并移除；RikkaHubApp 改为调用 **P26** 的存量清理器。**同步上游时：不要恢复播种器**（上游本无此文件，无需动作） | 产品方向变更 |
| P12 | `app/src/main/java/.../utils/UpdateChecker.kt` | checkUpdate() 发出 Loading 后 `return@flow` 短路（代码保留不可达原逻辑） | 禁用上游更新检查：原会请求 updates.rikka-ai.com 并在抽屉引导用户下载 RikkaHub 官方 APK（签名不同成并存应用，问题表单#1）；UpdateCard 因无 Success 数据不展示。宿主功能定制；同步上游时需重放 |
| P13 | —（编号空洞，废弃引用） | 全仓代码零定义：.kt/.kts 无任何 P13 标记（2026-09-27 审计与本次复核一致）；docs 内 "P13" 引用（DFWX-AI-001-report.md 等）均指该空洞本身 | 预留编号未使用，同步上游时跳过 |
| P14 | `app/src/main/res/values/themes.xml` | Theme.Rikkahub 增加 `android:windowBackground`=#0B0A12 | 冷启动窗口底色对齐宿主深色，防浅色系统下进 AI 页白闪（问题表单#14）。宿主体验定制；同步上游时需重放 |
| P6b | 新增 `web/src/main/resources/static/index.html` + `web/src/main/resources/.gitignore` 加 `!static/index.html` 例外 | web-ui 未打包时控制台显示占位说明页而非裸 404（问题表单#16）；装好前端构建后被真实产物覆盖 | 宿主体验定制；同步上游时需重放 |
| P15 | `app/build.gradle.kts` 删 `implementation(libs.firebase.crashlytics)`；`di/AppModule.kt` 删 crashlytics import 与 Koin 装配 | crashlytics 无官方构建插件时，FirebaseInitProvider 的 EAGER 组件因 build ID 缺失抛 IllegalStateException——**早于 Application.onCreate，DEGRADED 保底接不住**，Android 8.1 真机实测整循环崩溃（crash.log 实证，2026-09-14）；全仓审计确认 crashlytics 零消费点，摘除无功能损失 | 宿主稳定性定制；同步上游时需重放 |
| P16 | **v2.5.5 重构（结构性质变）**：新增 `ui/routes/AppRoutes.kt`（=上游 `RouteActivity.AppRoutes()` 函数体**逐字节搬运**为顶层函数，差异只有 4 处，文件头有清单）；新增 `dfwx/VolumeKeyBridge.kt`（进程级音量键注册表）；`RouteActivity.kt` 瘦身为 Activity 外壳（保留 SafeMode 检查 / handleIntent / Screen sealed interface / 本地 `volumeKeyListeners` 上游语义）；`dfwx/RikkaHubEmbed.kt` 从 421 行复制品缩为约 60 行壳（只做 Coil 装配 + DeepLinkSink 选择）；`dfwx/AiPageHost.kt` 为宿主 Java 桥接入口。ChatList.kt 音量键同时注册到本地列表与 VolumeKeyBridge；宿主 `MainActivity.dispatchKeyEvent` 在 AI 页转发进桥 | 东方无限底栏"AI"=主界面内嵌页（ComposeView 进宿主 pageFrame），不再跳转独立 Activity。**v2.5.5 的关键改进：不再维护 400+ 行路由复制品**——上游改路由时只需把 AppRoutes 函数体重搬一次 + 按文件头的 4 条改，长期维护成本大幅下降（这正是用户 2026-09-28 提出的"后续他们更新我们也会更新"要求）。**同步上游时：覆盖后把上游 RouteActivity 的 AppRoutes 函数体整段替换进 AppRoutes.kt，再按文件头 4 条改** | 宿主交互定制 + 长期可维护性（用户验收硬指标） |
| P17 | 新增 `ui/theme/presets/DongfangTheme.kt`（色板=宿主 ThemeEngine：**BG#000000**（v1.19.7 起 OLED 真黑）/SURFACE#16141F/PRIMARY#A78BFA/TEXT#F2F0F7/MUTED#9A93AB/DIV#262332）+ `PresetThemes` 列表**置首**（全新安装默认主题）+ `findPresetTheme` 兜底改 `DongfangThemePreset` + `SettingThemePage.kt` 删自定义主题后回落 "dfwx"；主题名走自有资源 `R.string.dfwx_theme_name`（新增 `res/values/dfwx.xml`，**不用 vendor 的 `app_name`**——后者在 values-zh 等 6 个语言里仍是 "RikkaHub"）；存量迁移见 `AiPageHost.dfwxMigrateDefaultTheme`（sakura→dfwx，只改上游默认值，用户选过的主题不碰） | AI 界面配色/质感与东方无限一体（用户验收硬指标）；用户仍可在 设置→主题 更换 | 品牌定制；同步上游时需重放（新增文件 ×2 + 列表置首 + findPresetTheme 兜底 + SettingThemePage 一行 + PresetTheme.kt import） |
| P18 | `dfwx/AiPageHost.kt` **单处** | 内嵌 AI 页强制深色：`createRikkaHubEmbedView` 以 `RikkahubTheme(colorMode = ColorMode.DARK)` 包裹（AiPageHost.kt:37），不随系统浅色变白。**v2.5.5 起内层 RikkaHubEmbed 不再重复包裹主题**（2.5.4 时代双层主题互相覆盖是动画/适配异常来源之一，用户 2026-09-28 明确提到"以前打了太多补丁，动画效果或者适配全出问题了"） | 宿主整体是深色东方风格，内嵌页若随系统浅色会视觉割裂；同步上游时需重放（单处 colorMode 参数） |
| P19 | —（**已移除**，2026-09-30 v1.22.11 后续） | 原：`ui/pages/chat/ChatList.kt` 空会话占位两行文案（"开始你的对话"/"在下方输入框提问，AI 会在这里回复"）。**用户明确要求删除，已整块移除**：`messageNodes.isEmpty()` 分支、`DfwxEmptyChatState` item、`if/else` 包裹与收尾大括号全部删掉，`ChatList.kt` 与上游 2.5.5 差异回到只剩 P16（已用上游快照 `diff` 核验）。 | 用户 2026-09-28/29 两次要求"把文字删掉，这些无用的"（`DFWX-UI-006`）。**同步上游时不要再重放本补丁** |
| P20 | —（**已移除**，同上） | 原：P19 空态占位居中改沉底（`Alignment.BottomCenter`、距输入框约 24dp）。随 P19 一并移除，空会话回到上游原样的整页空白。 | 同 P19。**同步上游时不要再重放** |
| P21 | —（**已移除**，同上） | 原：空态 `contentPadding` 去掉 32dp 滚底空间 + `Column(padding(bottom=10.dp))`。随 P19 一并移除，`contentPadding` 恢复上游单行写法（不做空态/非空分支）。 | 同 P19。**同步上游时不要再重放** |
| P22 / P23 | —（编号空洞，未使用） | 代码与台账内均无定义（本次复核：全仓 `.kt/.kts/.java/.xml` 无 P22/P23 标记，旧台账 `~/heiyao/PATCHES-231-backup-20260928.md` 亦无此编号）。编号保留占位不回收，避免历史引用错位；同步上游时跳过 | 与 P13 同类（预留编号未使用） |
| P24 | `dfwx/AiPageHost.kt` | v1.21.5 全站字体切换（v1.21.3–v1.21.4 引入）整体移除，宿主与 AI 页一律回系统字体；仅保留一次性存量迁移 `dfwxMigrateLegacyChatFont`：聊天字体为 CUSTOM 且路径带 "dfwx/" 前缀（旧版写入的 filesDir 字体副本）时回退 DEFAULT 并清理副本文件，用户手动选的其他自定义字体不碰 | v1.21.3/v1.21.4 曾把聊天气泡字体写成 CUSTOM 指向 filesDir 字体副本，字体功能下架后该副本不再维护，须回退默认并清理；同步上游时需重放（迁移逻辑随宿主存量退出后删除） |
| **P25** | `data/datastore/PreferencesStore.kt` 两处：读取处 `dynamicColor = preferences[DYNAMIC_COLOR] == true`（上游 `!= false`）、`Settings` data class 默认值 `val dynamicColor: Boolean = false`（上游 true） | **【高优先级】上游 dynamicColor 默认开，`Theme.kt:57-61` 的 `settings.dynamicColor && SDK_INT >= S -> dynamicDarkColorScheme(context)` 会在 Android 12+ **完全绕过预设主题**改用系统壁纸取色——东方主题名义生效实际失效（色板、OLED 真黑全部不生效）。关闭后预设主题（含 DongfangTheme）才真正生效；用户仍可自行在设置里开启动态取色。**同步上游时必须重放这两处** | 品牌配色必现性；不重放则 Android 12+ 上东方配色全失效 |
| **P26** | 新增 `dfwx/DfwxBuiltinProviderCleanup.kt` + `RikkaHubApp.onCreate` 在 `extractBuiltinSkills()` 后调用 `removeLegacySeedIfNeeded(this, get<AppScope>(), get<SettingsStore>())` | 【AI-004】老版本（v1.8.1–v1.22.6）装过并播过"智能中转(内置)"渠道的用户，升级后把这条渠道连同悬空模型引用一起清理。**保守身份证明**：只有名称+baseUrl+模型列表逐字段与历史播种形状完全一致才删（走 AI-003 的 `SettingsDataGuard.removeSeededProviders`）；用户改过任何一处（改名/换地址/加删模型）即视为自有渠道保留；不校验也不读取 Key（无法证明则保留）。只跑一次（`dfwx_seed` prefs 的 `ai004_removed` 标记），失败不写标记以便重试；日志只记数量与字段名 | 用户 2026-09-28 拍板移除全部内置 API；存量用户需要一次性清理。**同步上游时需重放**（新增自有文件 + RikkaHubApp 一行） |
| **P28** | 新增 `data/dfwx/SettingsDataGuard.kt`（+ 契约测试 `data/dfwx/SettingsDataGuardTest.kt`，12 例） | 【AI-003 数据保护层】改任何 settings 之前的**保守安全闸**，四个能力：`isProvablySeeded`（渠道身份证明——名称 + baseUrl + 模型 id 列表逐字段全等才算"这条是本仓播的"，任一字段被用户改过即判为自有渠道，永不动用户数据）；`removeSeededProviders`（只删可证明身份的渠道，返回 before/after/report，不读不校验 Key）；`sanitizeDanglingModelReferences`（渠道删除后把 chat/fast/translate/compress 四个模型引用中指向已消失模型的重置为 `DEFAULT_AUTO_MODEL_ID`，防悬空）；`checkMigrationInvariants`（迁移后置校验）。**所有涉及设置迁移/清理的代码都必须经此层，不得直接改 settings**；目前消费方是 P26。文件在本仓自有 `data/dfwx/` 目录（rsync 排除清单保护），**同步上游时无需重搬，但新增消费方时要复用而不能绕过** | 用户数据不可误伤（用户 2026-09-28 明确"正式版别出问题"）；AI-004 的存量清理依赖此层 |

## 已重放的历史补丁（2.5.1 时代登记，2.5.5 重放时归并为以下条目）

| # | 文件 | 改动 | 说明 |
|---|---|---|---|
| **U4** | `ui/components/easteregg/EmojiBurst.kt` | 逐帧循环加空闲挂起：`pendingBursts.isEmpty() && particles.isEmpty()` 时 `snapshotFlow{...}.first{...}` 等第一个粒子再恢复；配套 import `snapshotFlow` 与 `kotlinx.coroutines.flow.first` | 上游 `while(true)+withFrameNanos` 无条件空转——About 页一打开就持续烧 CPU/GPU（Robolectric 扫描也因此 60s 不空闲）。**同步上游时需重放** |
| **U5/U6/U7** | `ui/pages/extensions/QuickMessagesPage.kt`、`skills/SkillsPage.kt`、`workspace/WorkspacePage.kt` | 空态提示 Text 加 `modifier = Modifier.padding(horizontal = 72.dp)` | 空态提示会被右下 ExtendedFAB 压住；72dp 使文字避开 FAB 左缘。**同步上游时需重放三处** |
| **U8** | `ui/pages/setting/SettingWebPage.kt` | LazyColumn `contentPadding` 由 `PaddingValues(8.dp)` 改为 `PaddingValues(top=8.dp, start=8.dp, end=8.dp, bottom=88.dp)` | 底部给 ExtendedFAB（56dp 高 + 16dp 边距）留净空，否则 Start 按钮压住最后一张卡片。**同步上游时需重放** |

## 安全链路补丁（上游无此能力，属本仓新增防线）

| # | 文件 | 改动 | 说明 |
|---|---|---|---|
| **U1 / DFWX-NET-001** | 新增 `ai/src/main/java/me/rerere/ai/provider/AiUrlPolicy.kt`、`AiUrlPolicyInterceptor.kt`、`ai/src/test/.../AiUrlPolicyTest.kt`（18 用例）；挂载点 `app/src/main/java/me/rerere/rikkahub/di/DataSourceModule.kt` 的 `ProviderManager` 装配 | AI 链专用 OkHttp client 从共享 client **派生**（复用连接池/超时，不影响 WebDav/Search 等非 AI 流量），四道防线：① `AiUrlPolicyInterceptor`（application 拦截器）做请求入口完整判定（协议必须 https、拒绝 URL 内嵌凭据、拒字面量写死的本机/内网地址，并对域名做一次解析校验防 rebinding）；② 同名 network 拦截器（`resolve = false`）对**每一跳**（含重定向 follow-up）做静态判定；③ `AiPolicyDns` 在连接前拒绝解析到本机/内网/保留地址的任何一跳（rebinding 最终防线）；④ `followSslRedirects(false)` 使 https→http 跨协议重定向 fail-closed | 【安全】AI 请求可指向用户自配中转站，若不加限制可被诱导访问内网/环回服务（SSRF）或经明文 HTTP 泄露 API Key。**同步上游时必须重放**：DataSourceModule 的 ProviderManager 装配段（上游为 `ProviderManager(client = get(), context = get())`），三个自有文件由 rsync 排除清单保护、无需重搬 |

**注：U2/U3 编号未使用**（代码内无此标记，仅 U4–U8 有实体补丁；编号保留占位不回收，避免历史引用错位）。

## 同步上游的操作清单（rsync 排除清单的落盘副本）

排除清单已从 `/tmp/rsync-excludes.txt` 落盘到仓库内 **`rikkahub/.dfwx-rsync-excludes.txt`**（43 行，含本次补加的 `app/src/main/keepRules/`）——原先只存在 /tmp，重启即失。同步时用：

```
rsync -a --delete --exclude-from=rikkahub/.dfwx-rsync-excludes.txt /path/to/upstream-2.5.x/ rikkahub/
```

排除清单包含三类：构建产物；上游不带入的目录（web-ui/videogen/baselineprofile/CI 等）；**东方无限自有资产的目标侧保护**（dfwx/ 目录、DongfangTheme.kt、AiUrlPolicy*.kt、material-color-utilities/、PATCHES.md、web static/index.html、版本目录）。

**尤其注意 `app/src/main/keepRules/` 必须排除**（P9）：本仓已把该目录改名为 `dfwxKeepRules/`（AGP 9 库模块会把 `src/main/keepRules/` 当 consumer 规则，其中 `-dontobfuscate` 是全局项被禁止，构建会失败）。若同步时把上游的 `keepRules/` 带回来，AGP9 会在配置期报错——**这是 2026-09-28 复核时新发现的同步陷阱，已补进排除清单**。

## 宿主侧配套（不在 vendor 内，随宿主版本走）

- `settings.gradle`：includeBuild("rikkahub/build-logic") + versionCatalogs 导入上游 toml + 模块名对齐上游（:ai/:common/...），上游 :app → `:rikkahub-app`。
- 根 `build.gradle.kts`/`gradle.properties`/wrapper：AGP 9.3.1 + Gradle 9.6（腾讯镜像）+ 上游 JVM/并发参数；wrapper 原因：services.gradle.org 本机不可达。
- `:app`（cc.nkbr.lanzouplus）：minSdk 24→26、targetSdk/compileSdk 37、abiFilters=arm64-v8a、packaging pickFirsts libtermux、proguard 并入上游 keepRules 原文、依赖 `:rikkahub-app`、Application 换 `.App extends RikkaHubApp`（上游 application 节点属性合并时宿主优先，name 必须宿主显式声明）、usesCleartextTraffic=true（支持 http 中转站，对齐上游行为）。
- 宿主侧 `App.onCreate` 对 `super.onCreate()` 全链 try-catch + `App.DEGRADED` 标志（2026-09-14 问题表单#2）：RikkaHub 启动链任一环抛非受控异常（备份 journal 损坏/QuickJS 原生库失败）时降级保住蓝奏云主功能，MainActivity 的 AI 入口按 DEGRADED 拦截提示；同步上游无需动作（纯宿主文件）。
- **品牌名覆盖（P27，宿主资源，2026-09-28 新增）**：新增宿主 `app/src/main/res/values{,-zh,-zh-rTW,-ja,-ko-rKR,-ru,-ar}/strings.xml`，各定义 `app_name="东方无限"`。vendor 在同样的 7 个语言目录里定义了 `app_name="RikkaHub"`，被三处运行期代码取用——`ChatGenerationForegroundService`（AI 生成进行中的前台通知标题）、`WorkspaceDocumentsProvider`（系统"文件"应用里的 AI 工作区入口标题，以及新建文档默认文件名）、`SettingAboutPage`（关于页头部大字）。宿主模块同名资源按语言逐一覆盖并胜出，**7 个语言目录一个都不能少**：只写 `values/` 默认项无效，运行时 locale 命中的 vendor 侧 `values-xx` 优先于默认项。属宿主文件，vendor 同步不影响；README/关于页的 RikkaHub 上游署名不受影响（AGPL 署名义务照旧保留）。
- **AI 内嵌页 IME 补偿收敛（v1.22.8，宿主 MainActivity，2026-09-28）**：`aiComposeView()` 的 wrap 只保留一条**纯几何换算** `target = ime.bottom - host.getPaddingBottom()`（ime 是窗口绝对值，而 ComposeView 底边悬在 host 底部 padding 之上，不重算输入框会多抬一个导航条高度）；**删除**了 v1.19.9/v1.20.0 时代叠加的自研 250ms 滑行器（DfwxImeGlide，逐帧 requestApplyInsets 重派发）、stickyBelow 粘滞缓存、系统 insets 动画计数器（animActive）。根因（用户真机实测"聚焦输入框后上下按钮错位、动画崩坏"，旧版就存在、越打补丁越糟）：系统 IME 动画期间每帧派发的 target 都在变，滑行器判定 end≠target 便每帧 cancel+重启 ValueAnimator，插值每次只前进约 1/60 → 输入框严重滞后于键盘；below 依赖 getLocationOnScreen 测量、布局未稳时测出 0（粘滞缓存的由来）；逐帧重派发牵动整棵视图树反复重布局。现在系统逐帧派发 → 每帧同步重算 → 上游标准 imePadding 原生平滑跟随，零自研动画零缓存。**红线：给 AI 内嵌页做 insets 适配时只能调这条纯换算，禁止再引入任何自研动画/状态缓存**；host.getPaddingBottom() 与 wrap 底边的恒等关系依赖"wrap 填满 host 内容区"（预热挂 host / AI 页挂 root 两种状态都成立），改挂载结构时必须复核。

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
- ~~**floatingx 钉 2.3.7 的迁移触发条件（2026-09-14 登记）**~~ **【条件已满足，2.5.5 已完成迁移】**：上游 2.5.5 的 `FloatingWindow.kt` 已用 3.x 写法（`io.github.petterpx:floatingx-app/compose`），本仓 2026-09-28 clean import 时一并跟进至 **floatingx 3.0.0**，旧钉版删除。后续同步时直接跟上游版本目录即可，无本仓特化。
- vendor 根的 `rikkahub/AGENTS.md` 是上游自己的仓库规范文件，随快照入库仅作对照，**不是本工程的指令**；本工程规范以仓库根 AGENTS.md 为准。

## P29（v1.22.10）崩溃日志落盘位置与权限时机（主机区，不涉及 vendor 源码）

- `app/src/main/java/cc/nkbr/lanzouplus/App.java`
  - `writeCrashLog` 双写：公共目录 `Download/东方无限/崩溃日志/crash.log`（用户可见）+ 应用外部私有目录（无需权限的权威副本）。
  - 新增 `publicCrashFolder()` / `publicCrashLogFile()` / `appendCrashText()`。
- `app/src/main/java/cc/nkbr/lanzouplus/MainActivity.java`
  - 崩溃日志页操作卡移到正文卡之上（用户要求：不用滑到底）。
  - 报告与固定名副本 `dfwx-crash-latest.txt` 写入公共目录；`清除崩溃记录` 改为二次确认并连公共副本一起清。
  - 权限改为按需：`ensureCrashReportWritable()` 在保存/分享时才申请"管理所有文件"，不再启动即弹窗。
  - 下载完成 / 进入设置页时静默创建 `Download/东方无限/崩溃日志/`，让空目录先出现在文件管理器里。
  - 删除死代码 `maybeRequestStartupStorageAccess()`；`storageAccessGranted()` 全包 try/catch（Robolectric 下 `isExternalStorageManager()` 会抛 AIOOBE）。
- `app/src/main/java/cc/nkbr/lanzouplus/DownloadFileProvider.java`：移除已废的 `crash` 私有目录分支，分享统一走 `shared`（真实路径）只读路径。

验证：`JAVA_HOME=... ./gradlew :app:testEmptyDebugUnitTest --offline` → 59 例全绿。

## P30（v1.22.11）崩溃日志页两处真机反馈（主机区，不涉及 vendor 源码）

- `MainActivity.ensureCrashFolder()`：`mkdirs()` 后**回读确认**并返回结果（旧实现静默吞失败，导致"文件夹到底建没建"无从判断）；放空 `crash.log` 占位让空目录在文件管理器里可见；成功置 `crashFolderEnsured` 幂等标志。
- `MainActivity.onResume()`：用户自行在系统设置里授予"管理所有文件"后，回前台补建一次崩溃目录（后台线程，不做主线程 IO）。
- `MainActivity.showCrashLogPage()`：
  - 新增常驻「打开崩溃日志文件夹」（`com.android.externalstorage.documents` 目录 URI，兜底复制路径 + 打开文件管理器）；
  - 新增状态卡显示**绝对路径**与就绪状态；
  - **修"打开后闪一下、顶部按钮被滚走"**：正文 `setTextIsSelectable(true)` 使其成为触摸模式可获焦视图，系统自动聚焦后 ScrollView 把正文滚进可视区（实测 scrollY 0→756），顶部操作卡被顶出屏幕。改为 `scroll.setFocusableInTouchMode(true)` + `FOCUS_BEFORE_DESCENDANTS` 让滚动容器自己当焦点锚点，并在进页后 `scrollTo(0,0)`。正文选区能力保留。

验证：`JAVA_HOME=... ./gradlew :app:testEmptyDebugUnitTest --offline` → 63 例全绿；新增 `CrashLogPageJvmTest` 4 例，去掉焦点锚点后 1 红（鉴别力已验）。

## P31（v1.22.12）AI 日志脱敏（SEC-004 / DFW-8）——三处泄露点全部收敛

用户安全要求：AI 日志只保留定位故障所需信息，不得记录任何凭据或用户内容。

**取证后发现任务卡只说对了一半**：卡里点名的自研 `RequestLoggingInterceptor` 其实**默认关闭、仅内存 100 条**；
真正在 release 里**常开且无门禁**的是另外两处。

| 泄露点 | 位置 | 原状态 | 处置 |
|---|---|---|---|
| A 自研请求日志 | `data/ai/RequestLoggingInterceptor.kt` | 默认关、仅 `Proxy-Authorization` 脱敏，URL/头/body 原样 | 全部改走 `DfwxLogRedactor`；body 只留形状 |
| B okhttp 官方日志 | `di/DataSourceModule.kt` | `Level.HEADERS` **release 常开**，只脱敏 Proxy-Authorization → Logcat | 降为 `Level.BASIC`，并把凭据头/查询参数登记进 okhttp 自带 redact 表 |
| C Provider SSE | `ai/.../{openai/ChatCompletionsAPI,openai/ResponseAPI,claude/ClaudeProvider,google/GoogleProvider}.kt` | `Log.d("onEvent: $data")` 打印**完整响应体** | 只留事件类型与字节数 |

**新增 `common/src/main/java/me/rerere/common/dfwx/DfwxLogRedactor.kt`**（放在 `common` 模块：`ai` 与 `app` 都依赖它，
`ai` 看不到 `app`）。纯字符串处理、无 Android 依赖，规则可被 JVM 测试逐条钉死。

脱敏规则要点：
- 头：名单命中即打码（Authorization/Cookie/Set-Cookie/x-api-key/x-goog-api-key…），**并且**"名字不认识但值像凭据"也打码
  （`Bearer `/`sk-`/`sk-ant-`/`AIza`…）——防将来新增 Provider 悄悄绕过；`-token`/`_key`/`-secret` 后缀的自定义头一律命中。
- URL：保留 scheme/host/path（定位故障必需），只打码凭据型查询参数值，并抹掉 `user:pass@` userinfo。
- body：**默认不记录内容**，只保留 `<字节数, 内容类型>`；不提供"只打码敏感字段仍保留正文"的模式（聊天正文属用户隐私）。
- 解析失败时整体打码，绝不原样透传；任何路径都不抛异常。

**结构性防线**：`common/.../Logging.kt` 的 `logRequest()` 是唯一存储入口，脱敏在**入库这一层**执行。
即便调用方忘了脱敏，也绕不过去——安全边界做成结构性的，而不是靠每个调用点自觉。

验证：`DfwxLogRedactorTest`（规则真值表，含畸形 URL、fragment、误伤保护）、
`RequestLoggingRedactionTest`（**驱动真实拦截器**，并额外覆盖"调用方未脱敏"的旁路场景）。
三道防线逐一验证过鉴别力——分别把拦截器脱敏、存储层脱敏改回去，对应测试立刻变红。
`:rikkahub-app:testDebugUnitTest` 243 例全绿；`:common:testDebugUnitTest` 14 例全绿。

## P32（v1.22.13）Firebase Analytics 整链移除（DFW-9）

**背景**：README 对外写着"无广告、无追踪"，但 vendor 区仍活装配 Firebase Analytics 并在 AI 主流程上报 5 个行为事件。
P15 只摘过 crashlytics，Analytics 一直留着。用户要求"弄清 APK 内有哪些会上报/联网的组件，能关的关掉"。

**改动（整链，不留半截）**：
| 文件 | 改动 |
|---|---|
| `ui/pages/chat/ChatVM.kt` | 删注入 `FirebaseAnalytics` + 5 处 `logEvent`（ai_send_message / ai_edit_message / ai_regenerate_at_message / ai_tool_approval / ai_tool_answer） |
| `di/ViewModelModule.kt` | 删 `analytics = get()` 构造参数 |
| `di/AppModule.kt` | 删 `import com.google.firebase.*` 与 `single { Firebase.analytics }` |
| `app/build.gradle.kts` | 删 `implementation(libs.firebase.analytics)`；**保留 BOM**（见下） |
| `test/.../SweepTestApplication.kt` | 删手动 `FirebaseApp.initializeApp(this)`（已无消费方） |

**为什么必须保留 firebase-bom**：BOM 自身不引入任何依赖，但 MLKit `barcode-scanning` 传递依赖
`firebase-encoders` / `datatransport` / `firebase-annotations`。去掉 BOM 后这些传递依赖会漂到未缓存版本，
离线构建直接失败（实测报 `No cached version of com.google.firebase:firebase-encoders:16.1.0`）。
保留 BOM = 只做版本对齐，**不新增任何遥测组件**。

**P5 的占位 resValue 暂时保留**：虽然 Analytics 已无消费方，但摘掉 `resValue` 会牵动
`buildFeatures.resValues = true` 与若干历史假设，收益不抵风险；本卡范围内先留（已标注为可清理项）。

**APK 级证据（客观、可复现）**：`aapt dump permissions` 对比 v1.22.11 → v1.22.13，
权限从 31 条降到 27 条，**消失的 4 条全部是广告/归因相关**：
- `android.permission.ACCESS_ADSERVICES_AD_ID`
- `android.permission.ACCESS_ADSERVICES_ATTRIBUTION`
- `com.google.android.gms.permission.AD_ID`
- `com.google.android.finsky.permission.BIND_GET_INSTALL_REFERRER_SERVICE`

验证：`:rikkahub-app:testDebugUnitTest` 243 例全绿；`:common:testDebugUnitTest` 14 例全绿；
release badging `versionCode=1039033 / versionName=1.22.13 / native-code: arm64-v8a`。

**残留待办（另开卡，不在本卡范围）**：MLKit `barcode-scanning` 仍会传递依赖部分 `com.google.android.gms`；
若用户要求"零 Google 组件"，需评估扫码功能的替代方案。

## P33（v1.22.13）明文流量收敛 + WebView 最小权限（DFW-10）

**背景**：宿主清单 `android:usesCleartextTraffic="true"`（PATCHES.md 自认是为 http 中转站开的），
全仓无 `networkSecurityConfig`，与 `docs/plan/decisions.md #9`「普通外部 AI/API 只允许 HTTPS」冲突。

**关键平台事实**（先查证再动手）：API 24+ 上一旦存在 `networkSecurityConfig`，
清单里的 `usesCleartextTraffic` **会被忽略**——所以配置文件才是唯一真相源。
**并且**：用户自填的 AI 渠道/http 中转站这条路，NET-001 早已用 `AiUrlPolicy` 在请求入口强制 HTTPS-only，
因此收紧明文**不会**改坏该功能（它本来就被拦）。

**改动**：
- 新增 `app/src/main/res/xml/network_security_config.xml`：`base-config cleartextTrafficPermitted="false"`（默认拒绝），
  仅**有界**放行两类域名：
  1. 蓝奏域名池 8 个（`LanzouCore.validatedRouteOrigin()` 与 `parseUserSourceInput()` 都显式接受 http 分享链接）；
  2. 回环与本地域名 `localhost` / `127.0.0.1` / `::1` / `local`（内置 Web 服务 + MCP OAuth 回调走 loopback，流量不出设备）。
- 宿主清单：`usesCleartextTraffic` 改 false，加 `android:networkSecurityConfig`，并补进 `tools:replace`
  （合并 vendor 清单时不加会被覆盖）。
- 宿主 `LanzouWebActivity` 与 vendor `ui/components/webview/WebView.kt`：显式钉死
  `allowFileAccess=false` / `allowContentAccess=false` / `allowFileAccessFromFileURLs=false` /
  `allowUniversalAccessFromFileURLs=false` / `mixedContentMode=MIXED_CONTENT_NEVER_ALLOW`。
  已核对 vendor 的静态资源走 `WebViewLocalAssets` 拦截器从 assets 读，**不依赖** file/content 访问，
  故这些收紧零功能损失。

**明确不放行**（有意为之）：任意第三方域名（含 AI 渠道、CDN）——宁可资源加载失败也不为它开口子。
已核对 Mermaid/代码预览 WebView 无 http CDN 依赖（baseUrl = `https://rikkahub.local`）。

**守卫**：`NetworkSecurityConfigJvmTest`（6 例）——清单不得出现 `usesCleartextTraffic="true"`、
base-config 必须默认拒绝、放行域名必须落在白名单内、不得出现通配符、蓝奏池与回环必须在放行列表里、
第三方域名不得被放行。

验证：`:app:testEmptyDebugUnitTest` 全绿；合并后清单确认 `networkSecurityConfig` 已生效且 `usesCleartextTraffic="false"`。

## P36（v1.22.18）依赖钉版与 SBOM（DFW-30）

**供应链风险处置**：`rikkahub/gradle/libs.versions.toml` 里
`com.github.rikkahub:sqlite-android` 版本原写作 **`-SNAPSHOT`**——在 JitPack 上这等于"默认分支最新 commit"。
两个后果：① **不可复现**（今天解到的 AAR 与下个月可能不是同一份代码）；
② **绕过评审**（上游推新 commit 就直接进安装包，没人看过）。审计 B-1 列为最高优先级之一。

**已钉到不可变 commit `80cedc8888df2fe1d22d7f9bf8d9287f621624be`**：
该 commit 由 JitPack 构建记录确认构建成功（`/api/builds/com.github.rikkahub/sqlite-android` 返回其 `ok`），
pom 与 aar 均实测可取（HTTP 200）。`compileDebugKotlin` 与 `:rikkahub-app:testDebugUnitTest` 在钉版后均通过。

**全仓浮动版本复查**：核对两个 `libs.versions.toml` 与各 `build.gradle.kts`，
除上述一处外**再无** `SNAPSHOT` / `latest.release` / `+` / 动态版本区间。

**新增 `docs/THIRD-PARTY-NOTICES.md`**（SBOM 简版）：登记本仓库许可（AGPL-3.0）、
上游核心组件署名义务（RikkaHub tag 2.5.5 / LanzouPlus）、供应链风险处置、
直接依赖按许可归类、**已移除组件记录**（Firebase Analytics/Crashlytics/Paparazzi，
避免"以为还在"）、以及完整 SBOM 的生成方式与三条诚实待办。

**守卫**：`DependencyHygieneJvmTest`（4 例）——版本目录不得出现浮动版本、
`sqlite-android` 必须钉到 40 位 commit、SBOM 必须存在且覆盖关键项（含本次钉版记录）、
上游锚点必须是 tag 而非 master。鉴别力已验：把版本改回 `-SNAPSHOT`，立刻 2 红。

**未做（需用户批准，属"引入第三方依赖"）**：完整 SBOM（CycloneDX / license-report 插件）。
项目红线要求不擅自引入新依赖，故本轮只出人工可审计的清单。
