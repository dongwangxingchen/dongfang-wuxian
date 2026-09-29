# DFWX-BRAND-003：AI 设置页赞助按钮点击闪退

状态：**✅ 已修复（v1.22.9），JVM 回归已绿；真机验收待用户装机**
前置：无（独立可做）
来源：用户真机实测（v1.22.8）原话——"并且设置界面（AI对话的）的赞助按钮点击后会闪退"

## 结论（2026-09-29，证据等级 A）

**根因 = 宿主 `app/build.gradle.kts` 的 aapt2 参数 `--no-xml-namespaces`。**

它会把最终 APK 里**所有 res 二进制 XML 的命名空间 URI 一并剥离**。Compose 的矢量图解析器
（`androidx.compose.ui.graphics.vector.compat.XmlVectorParser`）读属性走的是**带命名空间的查找**：
`androidx.core.content.res.TypedArrayUtils.hasAttribute(parser, "viewportWidth")` →
`parser.getAttributeValue("http://schemas.android.com/apk/res/android", "viewportWidth")`。
命名空间被剥离后该查找恒为 null，`getNamedFloat` 退回默认值 `0f`，于是抛
`XmlPullParserException: Binary XML file line #5<VectorGraphic> tag requires viewportWidth > 0`
→ 组合期异常 → 点"赞助"即闪退。**这就是"JVM 测试全绿、真机必崩"的差异所在**（见下方"为什么以前测不出"）。

### 决定性 A/B 对照（可复现，证据等级 A）

同一份 `rikkahub/app/src/main/res/drawable/afdian.xml`，用 aapt2 只改一个参数分别 link：

| link 参数 | 字符串池 | 属性 ns 字段 | 真机后果 |
|---|---|---|---|
| 不带 `--no-xml-namespaces` | 含 `'android'` + `'http://schemas.android.com/apk/res/android'` | 0x01010402 等 | 正常 |
| 带 `--no-xml-namespaces` | **两者都消失** | **-1（无命名空间）** | **闪退** |

v1.22.8 发布 APK 实测：`res/2A.xml`（afdian）命名空间出现次数 = **0**；
移除该参数重建后 = **1**（修复已验证进包）。

### 用户真机堆栈（2026-09-29，患者原文）

```
Thread: main
Caused by: org.xmlpull.v1.XmlPullParserException: Binary XML file line #5<VectorGraphic> tag requires viewportWidth > 0
	at kotlin.collections.SetsKt.painterResource(Unknown Source:1411)
	at ...ComposableSingletons$SettingMcpPageKt$$ExternalSyntheticLambda0.invoke(...)   ← R8 内联后类名错位，非真实调用点
	at ...ui.pages.setting.SettingAboutPageKt.DonateMethodsCardGroup(...)               ← 真实调用点
```

注：堆栈里 `SettingMcpPageKt` 是 R8 类合并/内联造成的**误导性符号**；权威定位依据是
`DonateMethodsCardGroup` + `SettingDonatePage.kt:114` 的 `painterResource(R.drawable.afdian)`。
用户回答确认：**点击后立即闪退 / 以前也这样 / 无"支持开发者"弹窗 / 进入赞助页就崩**
——与"渲染期解析矢量图失败"完全吻合（也排除了原候选 B 弹窗时序、候选 D 无浏览器）。

### 为什么以前一直测不出（本卡最关键的教训）

1. **JVM/Robolectric 走的是 `apk-for-local-test.ap_`，其资源是 debug 单元测试资源包**
   ——本仓 debug 单元测试包里的 `afdian` 命名空间同样是 0，但 Robolectric 的 `getXml()`
   不经过 Compose 的 `TypedArrayUtils` 带命名空间查找路径，所以页面能渲染、测试全绿。
2. 现有两条测试（`SettingDonatePageJvmTest`、`EmbedSweepJvmTest:86`）都只验证"页面能推栈/能渲染"，
   **没有验证资源本身的命名空间完整性**。
3. 该参数自 v1.0.2 起就在（`git log -S` 确认 2026-09-04 引入），**20 余个版本一直带着**——
   直到 v1.22.8 上游 2.5.5 首次在宿主可达路径引入 `painterResource(矢量图)`（`SettingDonatePage.kt:114`）
   才被引爆。这也解释了"以前版本也这样"（从 2.5.5 导入起即存在）。

### 修复

- `app/build.gradle.kts:28`：参数改为 `listOf("--no-compile-sdk-metadata")`（**移除 `--no-xml-namespaces`**），
  并留 7 行注释锁死红线 + 指向回归测试。
- **新增可失败回归测试** `app/src/test/java/cc/nkbr/lanzouplus/ApkXmlNamespaceJvmTest.kt`（2 例）：
  先复现 Compose 的查找方式（`getAttributeValue(ANDROID_NS, "viewportWidth")`），
  再兜底断言宿主+vendor 多个矢量图的 android 命名空间属性一个都不能少。
  **已验证鉴别力：修复前 2 例全红，修复后 2 例全绿。**

### 影响面（为什么不只影响赞助页）

宿主 59 个矢量图 drawable + vendor 的 `deepthink/pdf/docx/patreon/rabbit/afdian` 等**全部**受影响。
任何走 `painterResource()` 的入口都是潜在闪退点：
`ChatMessage.kt:537/545`（docx/pdf 附件图标）、`ModelList.kt:846` 与 `Export.kt:678`（deepthink 图标）
——本次一并修好。**这不是单点 bug，是全仓级的构建参数错误。**

---

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

## 首要假设（已于 2026-09-29 全部作废，保留作追溯）

原列 4 条候选（A R8 / B 弹窗时序 / C HugeIcons / D CustomTabs）**全部被真机堆栈推翻**：
真实根因是 aapt2 的 `--no-xml-namespaces` 剥离命名空间导致的矢量图解析失败（见文首结论）。
其中：候选 B 被用户回答"无弹窗"直接排除；候选 A 的 R8 只是让堆栈符号错位（类合并/内联），并非崩溃原因；
候选 C/D 均与 `painterResource` 解析期异常无关。**留此节仅为防止后来者重复猜测。**

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

## 执行顺序（本次实际执行，留作复盘）

1. ✅ 拿到真机崩溃堆栈（用户自行用"设置→崩溃日志→复制全部日志"导出）。
2. ✅ 读堆栈定位到 `painterResource` + `XmlPullParserException: ViewportWidth > 0`。
3. ✅ 只读取证：aapt2 `--no-xml-namespaces` 语义 + 本仓 APK 内命名空间实测 + **A/B 对照实验**。
4. ✅ 先写可失败回归测试，确认修复前 **2 例全红**。
5. ✅ 最小修复：移除构建参数（未改任何 vendor 代码、未加 keep 规则）。
6. ✅ 修复后回归 **2 例全绿**、宿主全量 **57 例全绿**、release 出包并核对 APK 内命名空间已恢复。

## 验收

- [x] 回归测试：`ApkXmlNamespaceJvmTest`（2 例）修复前红、修复后绿；
- [x] 宿主全量 `:app:testEmptyDebugUnitTest` **57 例全绿**（基线 51 + 新增 6）；
- [x] release 包 `aapt dump badging` 断言 `native-code: arm64-v8a` + 版本号一致；
- [x] release APK 内 `res/2A.xml`(afdian) 命名空间出现次数 = 1（v1.22.8 为 0）；
- [ ] **真机验收（待用户装机）**：AI 设置 → 赞助 → 页面正常打开、可返回、不闪退；
      顺带验证 docx/pdf 附件图标与 deepthink 图标不再有同类闪退。
- 未改动 vendor 代码、未加 keep 规则 → 无需登记 `rikkahub/PATCHES.md`（**本卡的修复全在宿主构建配置**）。

## 回退

`git checkout -- app/build.gradle.kts app/src/test/java/cc/nkbr/lanzouplus/ApkXmlNamespaceJvmTest.kt`。
release 回退点 = v1.22.8 的 APK（`~/heiyao/黑曜/03-构建产物/东方无限-v1.22.8.apk`）。
**注意：回退该参数 = 把闪退放回去**，非必要不要回退。

## 与 BRAND-001 的关系

`DFWX-BRAND-001` 是"赞助页重做"（信息层级/视觉/文案）。**本卡只修闪退，不改设计**。闪退修完后再做重做，避免两件事混在一个提交里。

## 与 BRAND-001 的关系

`DFWX-BRAND-001` 是"赞助页重做"（信息层级/视觉/文案）。**本卡只修闪退，不改设计**。闪退修完后再做重做，避免两件事混在一个提交里。
