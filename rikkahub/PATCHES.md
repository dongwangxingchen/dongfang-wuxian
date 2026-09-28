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
| P12 | `app/src/main/java/.../utils/UpdateChecker.kt` | checkUpdate() 发出 Loading 后 `return@flow` 短路（代码保留不可达原逻辑） | 禁用上游更新检查：原会请求 updates.rikka-ai.com 并在抽屉引导用户下载 RikkaHub 官方 APK（签名不同成并存应用，问题表单#1）；UpdateCard 因无 Success 数据不展示 | 宿主功能定制；同步上游时需重放 |
| P13 | —（编号空洞，废弃引用） | 全仓代码零定义：.kt/.kts 无任何 P13 标记（2026-09-27 审计与本次复核一致）；docs 内 "P13" 引用（DFWX-AI-001-report.md 等）均指该空洞本身 | 预留编号未使用，同步上游时跳过 |
| P14 | `app/src/main/res/values/themes.xml` | Theme.Rikkahub 增加 `android:windowBackground`=#0B0A12 | 冷启动窗口底色对齐宿主深色，防浅色系统下进 AI 页白闪（问题表单#14） | 宿主体验定制；同步上游时需重放 |
| P6b | 新增 `web/src/main/resources/static/index.html` + `web/src/main/resources/.gitignore` 加 `!static/index.html` 例外 | web-ui 未打包时控制台显示占位说明页而非裸 404（问题表单#16）；装好前端构建后被真实产物覆盖 | 宿主体验定制；同步上游时需重放 |
| P15 | `app/build.gradle.kts` 删 `implementation(libs.firebase.crashlytics)`；`di/AppModule.kt` 删 crashlytics import 与 Koin 装配 | crashlytics 无官方构建插件时，FirebaseInitProvider 的 EAGER 组件因 build ID 缺失抛 IllegalStateException——**早于 Application.onCreate，DEGRADED 保底接不住**，Android 8.1 真机实测整循环崩溃（crash.log 实证，2026-09-14）；全仓审计确认 crashlytics 零消费点，摘除无功能损失 | 宿主稳定性定制；同步上游时需重放 |
| P16 | **v2.5.5 重构（结构性质变）**：新增 `ui/routes/AppRoutes.kt`（=上游 `RouteActivity.AppRoutes()` 函数体**逐字节搬运**为顶层函数，差异只有 4 处，文件头有清单）；新增 `dfwx/VolumeKeyBridge.kt`（进程级音量键注册表）；`RouteActivity.kt` 瘦身为 Activity 外壳（保留 SafeMode 检查 / handleIntent / Screen sealed interface / 本地 `volumeKeyListeners` 上游语义）；`dfwx/RikkaHubEmbed.kt` 从 421 行复制品缩为约 60 行壳（只做 Coil 装配 + DeepLinkSink 选择）；`dfwx/AiPageHost.kt` 为宿主 Java 桥接入口。ChatList.kt 音量键同时注册到本地列表与 VolumeKeyBridge；宿主 `MainActivity.dispatchKeyEvent` 在 AI 页转发进桥 | 东方无限底栏"AI"=主界面内嵌页（ComposeView 进宿主 pageFrame），不再跳转独立 Activity。**v2.5.5 的关键改进：不再维护 400+ 行路由复制品**——上游改路由时只需把 AppRoutes 函数体重搬一次 + 按文件头的 4 条改，长期维护成本大幅下降（这正是用户 2026-09-28 提出的"后续他们更新我们也会更新"要求）。**同步上游时：覆盖后把上游 RouteActivity 的 AppRoutes 函数体整段替换进 AppRoutes.kt，再按文件头 4 条改** | 宿主交互定制 + 长期可维护性（用户验收硬指标） |
| P17 | 新增 `ui/theme/presets/DongfangTheme.kt`（色板=宿主 ThemeEngine：**BG#000000**（v1.19.7 起 OLED 真黑）/SURFACE#16141F/PRIMARY#A78BFA/TEXT#F2F0F7/MUTED#9A93AB/DIV#262332）+ `PresetThemes` 列表**置首**（全新安装默认主题）+ `findPresetTheme` 兜底改 `DongfangThemePreset` + `SettingThemePage.kt` 删自定义主题后回落 "dfwx"；主题名走自有资源 `R.string.dfwx_theme_name`（新增 `res/values/dfwx.xml`，**不用 vendor 的 `app_name`**——后者在 values-zh 等 6 个语言里仍是 "RikkaHub"）；存量迁移见 `AiPageHost.dfwxMigrateDefaultTheme`（sakura→dfwx，只改上游默认值，用户选过的主题不碰） | AI 界面配色/质感与东方无限一体（用户验收硬指标）；用户仍可在 设置→主题 更换 | 品牌定制；同步上游时需重放（新增文件 ×2 + 列表置首 + findPresetTheme 兜底 + SettingThemePage 一行 + PresetTheme.kt import） |
| P18 | `dfwx/AiPageHost.kt` **单处** | 内嵌 AI 页强制深色：`createRikkaHubEmbedView` 以 `RikkahubTheme(colorMode = ColorMode.DARK)` 包裹（AiPageHost.kt:37），不随系统浅色变白。**v2.5.5 起内层 RikkaHubEmbed 不再重复包裹主题**（2.5.4 时代双层主题互相覆盖是动画/适配异常来源之一，用户 2026-09-28 明确提到"以前打了太多补丁，动画效果或者适配全出问题了"） | 宿主整体是深色东方风格，内嵌页若随系统浅色会视觉割裂；同步上游时需重放（单处 colorMode 参数） |
| P19 | `ui/pages/chat/ChatList.kt`：ChatFontProvider 内 LazyColumn 首个内容块前加 `messageNodes.isEmpty()` 分支——空会话渲染居中空态（`fillParentMaxSize` Box + "开始你的对话"/"在下方输入框提问，AI 会在这里回复" 两行 Text，纯排版无图标：hugeicons 是预编译依赖无法确认图标清单），非空走原 itemsIndexed；新增 import `fillParentMaxSize`。配套宿主侧：`app/src/main/AndroidManifest.xml` MainActivity 补 `windowSoftInputMode="adjustResize"`（上游 rikkahub/app/src/main/AndroidManifest.xml:73 有、宿主漏配 → adjustUnspecified，键盘行为偏离上游假设） | 上游无空态设计：空会话时列表只剩 ScrollBottom Spacer，内嵌 AI 页整页空白（用户截图 ⑤） | 宿主体验定制；同步上游时需重放（ChatList 空态分支；清单属性随宿主走） |
| P20 | `ui/pages/chat/ChatList.kt` | P19 空态占位居中改沉底贴输入框（v1.19.9）：空态 Box `contentAlignment` 改 `Alignment.BottomCenter`，文案沉底、距输入框约 24dp，键盘弹出时随 viewport 一起浮上来 | 居中空态在键盘弹出（viewport 变矮）时上下全是空白；对齐 ChatGPT/谷歌 Gemini 空态贴输入框惯例；同步上游时需重放 |
| P21 | `ui/pages/chat/ChatList.kt` | 空态 contentPadding 去掉非空消息列表专用的 32dp 滚底呼吸空间（空态仅留 `innerPadding.bottom`，Box 底贴 bottomBar 顶）；空态 Column `padding(bottom = 10.dp)`——加上文字行盒与卡片内边距后视觉缝 ≈24dp（实测 116→74px） | 32dp 滚底空间对空态生效会把贴底文案连同 24dp padding 一起抬离输入框（实测键盘缝恒定 69dp）；同步上游时需重放 |
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

排除清单已从 `/tmp/rsync-excludes.txt` 落盘到仓库内 **`rikkahub/.dfwx-rsync-excludes.txt`**（40 行，含本次补加的 `app/src/main/keepRules/`）——原先只存在 /tmp，重启即失。同步时用：

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
