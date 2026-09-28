# DFWX-BRAND-003：AI 设置页赞助按钮点击闪退

状态：待执行（**根因未定位**，第一优先级是拿到真机崩溃堆栈）
前置：无（独立可做）
来源：用户真机实测（v1.22.8）原话——"并且设置界面（AI对话的）的赞助按钮点击后会闪退"

## 用户原话与理解

> "并且设置界面（AI对话的）的赞助按钮点击后会闪退"

理解为：AI 对话页 → 打开 RikkaHub 侧设置 → 点"赞助"（`donate`）条目 → 应用闪退。

**需要向用户确认的二义性**（开工前用正文文字问，勿用按钮组件）：
1. 是"点一下立刻退到桌面"，还是"进入赞助页后过一两秒才退"？
2. 是刚装上 v1.22.8 第一次点就闪退，还是之前版本也这样？
3. 闪退前有没有出现"支持开发者"的弹窗（见下方候选 B）？

## 相关代码（全部在上游原样路径下，2.5.5 导入前后**零差异**）

| 文件:行 | 内容 |
|---|---|
| `rikkahub/app/src/main/java/me/rerere/rikkahub/ui/pages/setting/SettingPage.kt:354-358` | 设置列表"赞助"条目：`item(onClick = { navController.navigate(Screen.SettingDonate) }, leadingContent = { Icon(HugeIcons.InLove, null) }, ...)` |
| `SettingPage.kt:95-118` | **候选 B 出处**：`if (settings.launchCount > 100 && (settings.launchCount - settings.sponsorAlertDismissedAt) >= 50)` 时弹 AlertDialog，confirm 按钮先 `vm.updateSettings(...)` 再 `navController.navigate(Screen.SettingDonate)` |
| `rikkahub/.../ui/pages/setting/SettingDonatePage.kt:53` | 页面本体：`LargeFlexibleTopAppBar`（`scrollBehavior`，`containerColor=CustomColors.topBarColors.containerColor`）；下方 `DonateMethodsCardGroup()`（:74 调用）与 `Sponsors(...)` |
| `SettingDonatePage.kt:92-121` | `DonateMethodsCardGroup()`：两个 `CardGroup.item`，Kofi（`AsyncImage(model=R.drawable.kofi)`）与爱发电（`Icon(painterResource(R.drawable.afdian))`）；onClick 走 `context.openUrl(...)` |
| `SettingDonatePage.kt:125-172` | `Sponsors()`：`koinInject<SponsorAPI>()`（:126）+ `produceState`（:127）`runCatching { sponsorAPI.getSponsors() }`（:129）；`UiState.Success → LazyVerticalGrid(AsyncImage + Text)` / `Loading → CircularWavyProgressIndicator` / `Error → Text(error.message)` |
| `rikkahub/.../data/api/SponsorAPI.kt` | Retrofit 接口，`baseUrl = "https://sponsors.rikka-ai.com"`，`@GET("/sponsors")`；`create()` 用 `get<OkHttpClient>()` 共享 client |
| `rikkahub/.../di/DataSourceModule.kt:194` | `single { SponsorAPI.create(get()) }`（属 `dataSourceModule`，启动时装载） |
| `rikkahub/.../ui/routes/AppRoutes.kt:391-392` | `entry<Screen.SettingDonate> { SettingDonatePage() }` |
| `rikkahub/.../RouteActivity.kt:262` | `@Serializable data object SettingDonate : Screen`（无参，导航无需传值） |
| `rikkahub/.../ui/components/nav/BackButton.kt` | 依赖 `LocalNavController`（默认值 `error("No Navigator provided")`） |
| `rikkahub/.../utils/ContextUtil.kt:118` | `openUrl` = `CustomTabsIntent.Builder().build().launchUrl(...)`，包在 `runCatching` 里（失败只弹 Toast，不会崩） |

## 已经排除的（本次只读复核，证据等级 A——**开工时勿重复这些检查**）

1. **不是本地补丁被覆盖**：`git diff 6eb27b7^ 6eb27b7 -- <上述文件>` 全部无差异（2.5.5 导入前后逐字节相同）。
2. **不是 Koin 未注册**：`DataSourceModule.kt:194` 有 `single { SponsorAPI.create(get()) }`；`RikkaHubApp.kt:75-79` 启动时装载 `dataSourceModule`。
3. **不是路由 key 缺参数**：`Screen.SettingDonate` 是 `data object`，无必填参数。
4. **不是资源缺失（release 包）**：`v1.22.8` APK 内 `drawable/afdian`、`drawable/kofi`、`string/donate_page_*`、`string/setting_page_sponsor_*` 全部存在且有值（`aapt dump resources` 实测）。
5. **不是类被 R8 移除**（`dexdump -a -f` 权威核验，`~/Library/Android/sdk/build-tools/36.0.0/dexdump`；注意 `dexdump` 不在 PATH，必须用绝对路径）：release dex（classes2.dex）含 `SponsorAPI`（`Lme/rerere/rikkahub/data/api/SponsorAPI;`，接口方法 `getSponsors` 在位，`@GET("/sponsors")` 注解**存活**：`VISIBILITY_RUNTIME Lretrofit2/http/GET; value="/sponsors"`）、`Sponsor$$serializer`、`Sponsor$Companion`、`Sponsor` 模型类；`/sponsors` 与 `https://sponsors.rikka-ai.com` 字符串均在 dex 中。两处需要正确理解的名字：① `SponsorAPI$Companion` 在 `mapping.txt` 里是 `-> R8$$REMOVED$$CLASS$$2075`，属 R8 对无状态伴生对象的**内联移除**（调用点 `SponsorAPI.create(get())` 已内联，`DataSourceModuleKt.dataSourceModule$lambda$0$18` 的 mapping 行可见），不是类丢失；② `SettingDonatePageKt` 作为**独立类**在 dex 中不存在，它被 R8 **类合并**进 `Lme/rerere/rikkahub/ui/pages/setting/SettingAboutPageKt;`——`SettingDonatePage` 是该类里的一个方法（`name: 'SettingDonatePage'`，`type: '(ILandroidx/compose/runtime/GapComposer;)V'`，PUBLIC STATIC FINAL），同类里还合并了 `DonateMethodsCardGroup`、`Sponsors`、`SettingFilesPage`、`SettingModelPage` 等；`Screen$SettingDonate` 独立存在。**两者都不是"代码丢失"，不能当作排除项之外的怀疑点**。
6. **不是网络失败导致**：`Sponsors()` 用 `runCatching` + `UiState.Error`，网络不通只会显示错误文字（JVM 无外网时就是这样，测试全绿）。
7. **JVM 层测不出**：两条现有测试都覆盖本页且**当前全绿**——
   - `rikkahub/app/src/test/java/me/rerere/rikkahub/dfwx/SettingDonatePageJvmTest.kt`（真实渲染 + 网络失败不崩，1 例）；
   - `rikkahub/app/src/test/java/me/rerere/rikkahub/dfwx/EmbedSweepJvmTest.kt:86`（真实 Embed 外壳逐页推栈，含 `"SettingDonate(赞助)"`）。
   → **这正是本卡的关键情报**：JVM（debug、无 R8、直接推栈）测不到，真机（release、**经 R8**、**经点击**）会崩。差异只有两类：**R8 优化** 与 **点击路径/真实渲染环境**。

## 首要假设（按置信度，全部未验证）

**候选 A【中高】R8 优化在 release 上破坏了某个运行期依赖。**
`-dontobfuscate` 已开（不重命名），但 **shrinking / optimization 仍在跑**（`app/build/outputs/mapping/emptyRelease/mapping.txt` 实测 `SponsorAPI$Companion -> R8$$REMOVED$$CLASS$$2075`）。Retrofit 依赖**方法注解 + 泛型签名**在运行期解析：若某条 keep 规则没覆盖到本接口在**多模块 + 库态**（上游 app 已转 library，见 `PATCHES.md` P1）下的路径，真机上 `Retrofit.create()` 或首次调用会抛异常。**验证方式**：真机装 release 包，用 `adb logcat` 抓 `FATAL EXCEPTION`（或让用户在应用内"设置→崩溃日志"复制堆栈——宿主 `App.installCrashLogger` 是全局 `Thread.setDefaultUncaughtExceptionHandler`，vendor 的崩溃也会落盘到 `getExternalFilesDir/crash.log`）。**这是第一步，必须做。**

**候选 B【中】"支持开发者"弹窗的导航时序问题。**
`SettingPage.kt:95-118`：`launchCount>100` 且距上次提醒 ≥50 次启动时弹窗；confirm 按钮 **先 `vm.updateSettings(...)` 再 `navController.navigate(...)`**。`updateSettings` 会让 `settings` StateFlow 发出新值 → 触发**当前组合重组**，而用户点击的确认框可能正在被销毁中；在**组合/重组期间执行导航**是 Compose 的已知危险区。**验证方式**：先问用户闪退前是否见过该弹窗；若是，在 JVM 里写一个 `launchCount` 满足条件的 Robolectric 测试复现（这是可写测试的路径）。

**候选 C【中低】`HugeIcons.InLove` 在 release 下的初始化**。
设置列表的赞助条目用 `Icon(HugeIcons.InLove, null)`。hugeicons 是**预编译依赖**（`PATCHES.md` P19 提到"无法确认图标清单"），若该图标字段在 R8 下被处理掉/初始化异常，点击或渲染会抛 `NoSuchFieldError`/`ExceptionInInitializerError`。注意：JVM 扫描测试已渲染过该条目且未崩，所以**这需要 R8 才成立**——与候选 A 同源，A 的日志能直接证实或排除。

**候选 D【低】`CustomTabsIntent` 在真机无浏览器/无 CustomTabs 支持时抛异常**。但 `openUrl` 有 `runCatching` 兜底，理论上不崩；且赞助页**刚进入时并未调 openUrl**。除非用户是点了 Kofi/爱发电卡片才崩——**向用户确认"是一进页面就崩，还是点了卡片才崩"**。

## 允许修改

- 若定位到 vendor 代码：`rikkahub/` 对应文件 + `rikkahub/PATCHES.md` 登记新编号；
- 若定位到 R8/keep 规则：`app/proguard-rules.pro` 或宿主 `app/build.gradle.kts`；
- 新增的回归测试（JVM/Robolectric；**若要覆盖 R8 路径，需真机或 `assembleEmptyRelease` 产物检查**）；
- `docs/plan/current-state.md`、`docs/plan/risk-register.md`。

## 禁止修改

- 不得删除赞助入口（那是用户自愿支持入口，也涉及上游尊重）；
- 不得引入绕过上游付费的能力；
- 不得在日志里打印任何凭据；
- 不得读取或输出 `local.properties`；
- 不得操作用户手机做不可逆操作；不得提交/push/发布。

## 执行顺序（严格）

1. **拿真机堆栈**（需先向用户请示操作手机，或其自行用"设置→崩溃日志"复制粘贴）：这一步产出决定后面所有方向，**没有堆栈不许改代码**。
2. 若拿不到堆栈 → 退而求其次：本地构建 release 包 + 真机复现（需用户配合），或在 JVM 补"点击级"测试（比现有测试更接近真实：走 `SettingPage` 的条目 onClick，而不是直接推栈）。
3. 定位后写最小修复 + 回归测试。
4. 若最终判定"上游代码在我们的 R8 配置下必然崩"→ 考虑加 keep 规则（比改 vendor 更符合"长期跟上游"方针）。

## 验收

- 真机（vivo X200 Pro mini / Android 16，**release 包**）：AI 设置 → 赞助 → 页面正常打开、可返回、不闪退；
- 若改动 vendor 或 keep 规则：`rikkahub/PATCHES.md` / `app/proguard-rules.pro` 有登记；
- 新增回归测试（至少 JVM 点击级）；
- 明确写出"已验证 / 未验证"。

## 回退

vendor 改动 `git checkout -- <file>`；keep 规则改动 `git checkout -- app/proguard-rules.pro`。release 回退点 = v1.22.8 的 APK（`~/heiyao/黑曜/03-构建产物/东方无限-v1.22.8.apk`）。

## 与 BRAND-001 的关系

`DFWX-BRAND-001` 是"赞助页重做"（信息层级/视觉/文案）。**本卡只修闪退，不改设计**。闪退修完后再做重做，避免两件事混在一个提交里。
