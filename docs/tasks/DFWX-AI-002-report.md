# DFWX-AI-002 设计报告：新版 AI 宿主接入边界

> 起草日期：2026-09-27。纯设计任务，本报告未修改任何源码、未构建、未操作设备。
> 输入：`docs/tasks/DFWX-AI-001-report.md`、`docs/plan/decisions.md`、`docs/plan/current-state.md`、`rikkahub/PATCHES.md`、`/tmp/dfwx-upstream/upstream_RouteActivity.kt`（2.5.4 对照物，已实际读取）。
> 证据等级：A（全部结论出自当前源码实读，路径+行号见正文）；个别标注"待验证"的项为静态分析推不出的事实。

## 〇、一句话方案

**以"上游 RouteActivity 结构重构 + 窄 wrapper"替换 421 行复制**：把上游 `AppRoutes` 从 RouteActivity 成员函数顶层化为 `ui/routes/AppRoutes.kt`（唯一结构 diff：5 个实例依赖收敛为 2 个参数），`dfwx/RikkaHubEmbed.kt` 缩为约 40 行的"深色主题 + AppRoutes 调用"，宿主侧维持现有 ComposeView 缓存/预热/IME 滑行机制不动，同时修复宿主返回桥的自我递归风险（`MainActivity.java:264`）。

## 一、现状证据（补丁叠加为什么必须换）

补丁叠加方式的真实形态——`dfwx/RikkaHubEmbed.kt`（421 行）是上游 `RouteActivity.AppRoutes()` 的全文复制，且已开始腐烂：

1. **乱码三处**：`RikkaHubEmbed.kt:128-130`（KDoc 整段 GBK 乱码）、`:377`（DEBUG 角标文案 `"[寮€鍙戞ā寮廬"`，用户可见于 debug 包）、`:409`（数据库迁移弹窗 `鈫?`，用户可见）。上游 diff 对照文件 `/tmp/dfwx-upstream/diff_RouteActivity.txt` 已确认两个文件内容 1:1 对应（仅 5 处机械差异）。
2. **头注释失实**（`RikkaHubEmbed.kt:127-130`）：声称"音量键桥接为参数"，实际签名只有 `activity/onBackStackReady/onOpenUsageAccessSettings` 三参（`:133-137`）——音量键滚动在内嵌态未接（PATCHES.md P16 已登记）。
3. **onBackStackReady 是空回调**：`AiPageHost.kt:38` 传 `{ }`——宿主完全不知道 vendor 导航栈深度，导致返回桥只能靠猜（见第四节 Back 设计）。
4. **主题双层强制深色包裹**：`AiPageHost.kt:31` 和 `RikkaHubEmbed.kt:140` 各包一层 `RikkahubTheme(colorMode = ColorMode.DARK)`，外层是死代码。
5. **上游对照确认不可原样抽取**（AI-001 待验证 #13，回答：需要窄 wrapper）：上游 `AppRoutes()` 是 RouteActivity 成员函数（`upstream_RouteActivity.kt:236-579`），实例依赖共 5 个：`navStack`（:143）、`pendingIntents`（:144）、`handleIntent`（:212-232）、`readBooleanPreference`/`readStringPreference`（:255-261，实为 Context 扩展）、`openUsageAccessSettings`（:246，Context 扩展）。前 3 个是真实例成员，后 2 个传 Context 即可。

宿主侧现状（复用资产，不动）：

| 机制 | 位置 | 状态 |
|---|---|---|
| AI 入口 + DEGRADED 拦截 | `MainActivity.java:484`（`goToDestination(4)`） | 保留 |
| 权限一次性引导 | `AiPermissionGate.java:42-64` + `MainActivity.java:484` `maybeGuideAiPermissions` | 保留 |
| ComposeView 缓存复用 | `MainActivity.java:489-497`（`showAiEmbedded`）、`:506-554`（`aiComposeView()`） | 保留 |
| 8s 预组合 | `MainActivity.java:556`（`prewarmAiCompose`） | 保留 |
| IME 重算 + 两段跳滑行 | `MainActivity.java:499-554`（`dfwxImeInsets`/`DfwxImeGlide`/insets listener） | 保留（归宿主） |
| 状态栏/导航条/刘海消费 + 防双计 | `MainActivity.java:466-468` | 保留 |
| 返回（预返回动画 + 分发） | `MainActivity.java:217-266`（`backCallback`/`canHandleBack`/`performSystemBack`） | **有缺陷，需修**（§4.3） |
| vendor RouteActivity 独立入口（SEND/PROCESS_TEXT/TRANSLATE/shortcuts） | `rikkahub/app/src/main/AndroidManifest.xml:70-95`，P4 只摘了 LAUNCHER | 保留 |

## 二、三条边界

### 2.1 宿主区（`app/src/main/java/cc/nkbr/lanzouplus/`，纯 Java）

宿主拥有：Activity 生命周期、View 挂载/复用/预热、页面切换动画、系统 insets 消费与 IME 派发、系统返回分发与预返回动画、权限引导、DEGRADED 降级、`adjustResize` 清单属性（`app/src/main/AndroidManifest.xml:15`）。
宿主**不拥有**：任何 Compose 组合树内部状态、vendor 导航栈内容、ChatVM/Provider/Room/DataStore。

### 2.2 vendor 区（`rikkahub/`，Kotlin/Compose）

vendor 拥有：主题（含 DongfangTheme preset）、导航栈（NavDisplay/backStack）、全部页面、TTS/ASR、Toaster、事件总线、Room/DataStore/SecretStore、BuiltinProviderSeeder、RouteActivity 独立入口的 intent 分发。
vendor **不拥有**：Activity（内嵌态运行于宿主 MainActivity）、窗口 insets（宿主已裁剪 statusBars/navigationBars/cutout，只透传 ime——`MainActivity.java:467-468`）、系统返回的最终决策权（栈深 1 时宿主收走）。

### 2.3 数据迁移边界

数据文件全部在 vendor 命名空间内，宿主代码不得直接读写：

| 迁移项 | 现状 | 边界归属 |
|---|---|---|
| 内置渠道/默认 Key 播种 | `BuiltinProviderSeeder.kt:31-83`（resValue 注入，`dfwx_seed` prefs 幂等） | **按 decisions.md #3 整体移除**（AI-005 执行）：默认配置只留名称/baseUrl/默认模型，secret 走 SecretStore；移除时 SharedPreferences `dfwx_seed` 标记保留不清理（幂等语义已死则无复活风险） |
| 聊天字体存量迁移 | `AiPageHost.kt:46-73`（v1.21.5 一次性迁移） | vendor（P24 已到期：v1.23 起用户群均 ≥v1.21.5，实施卡保留一版后随 BuiltinProviderSeeder 同批移除，删除前先取证最低升级路径） |
| 主题迁移 sakura→dfwx | `RikkaHubEmbed.kt:164-168` | vendor，一次性，保留至 2.5.4 重做实施完成 |
| Room schema/migration | `DatabaseMigrationTracker`（`RikkaHubEmbed.kt:182,385-416` 迁移遮罩） | vendor，禁 destructive reset（decisions.md #10） |
| 用户自建 Provider/助手/历史 | vendor DataStore/Room | 禁止因重做被误删（decisions.md #10） |

**迁移红线**：2.5.4 clean import 后实施卡必须先跑"旧数据打开新版本"迁移演练（本地 Room 文件副本 + Robolectric），失败不得删旧数据。

## 三、核心方案：上游结构重构 + 窄 wrapper

### 3.1 vendor 侧改动清单（2.5.4 clean import 之后做）

**改动 A（唯一上游结构 diff）：`AppRoutes` 顶层化**

新建 `rikkahub/app/src/main/java/me/rerere/rikkahub/ui/routes/AppRoutes.kt`，内容 = 上游 `RouteActivity.AppRoutes()`（`upstream_RouteActivity.kt:236-579`）整体迁出，签名改为：

```kotlin
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun AppRoutes(
    context: Context,                       // 供 readBooleanPreference/readStringPreference/openUsageAccessSettings（Context 扩展）
    deepLinks: DeepLinkSink = DeepLinkSink.None,
)
```

`DeepLinkSink` 是 5 行接口（放同文件），封装上游 SideEffect 里的实例成员协作（`upstream_RouteActivity.kt:266-271`）：

```kotlin
fun interface DeepLinkSink {
    /** backStack 就绪时被调用；返回 true 表示已消费后续 intent 注入 */
    fun onBackStackReady(backStack: MutableList<NavKey>)
}
object NoDeepLinks : DeepLinkSink { override fun onBackStackReady(backStack: MutableList<NavKey>) {} }
```

body 内唯一 diff：`SideEffect { navStack = backStack; while (pendingIntents...) handleIntent(...) }`（:266-271）替换为 `SideEffect { deepLinks.onBackStackReady(backStack) }`；`this@RouteActivity.openUsageAccessSettings()`（:246）替换为 `context.openUsageAccessSettings()`。**其余 300+ 行逐字节保持上游原文**，上游升级重放粒度从"整文件对照"降为"对照这一个函数体"。

`RouteActivity.kt` 相应瘦身：`AppRoutes()` 调用点（现 `RouteActivity.kt:242-251` 的 P16 委托）改为 `AppRoutes(context = this, deepLinks = { navStack = it; while (pendingIntents.isNotEmpty()) handleIntent(pendingIntents.removeFirst()) })`（SAM 转换）。navStack/pendingIntents/handleIntent/volumeKeyListeners/dispatchKeyEvent/SafeMode 检查全部保留原样（:141-233），独立入口行为零变化。

**改动 B：`dfwx/RikkaHubEmbed.kt` 从 421 行缩为约 40 行**

```kotlin
@Composable
fun RikkaHubEmbed(
    activity: ComponentActivity,
    onOpenUsageAccessSettings: () -> Unit,
) {
    RikkahubTheme(colorMode = ColorMode.DARK) {          // 单层主题，删除 AiPageHost 的第二层
        setSingletonImageLoaderFactory { /* 原样保留 RikkaHubEmbed.kt:142-160 */ }
        AppRoutes(context = activity, deepLinks = NoDeepLinks)
    }
}
```

一并执行：删双层主题包裹（§一.4）；修复 `:377`、`:409`、KDoc 三处乱码；DEBUG 角标、迁移遮罩（:385-416）随 AppRoutes 原文带走。**不再声明 `onBackStackReady` 参数**——宿主需要的栈深由改动 C 显式上报（3.3）。

**改动 C：栈深上报**

`AppRoutes` 的 `DeepLinkSink` 同时承担上报职责，或独立小参数 `onNavDepthChanged: (Int) -> Unit = {}`（推荐后者，语义单一；在 `NavDisplay` 外层用 `LaunchedEffect(backStack.size)` 上报）。vendor 每次栈变化通知宿主，宿主存 `aiNavDepth`。

**改动 D：音量键桥（补 AI-001 留给本卡的决定——补）**

vendor 新增 `dfwx/VolumeKeyBridge.kt`：

```kotlin
object VolumeKeyBridge {
    val listeners = mutableListOf<(isVolumeUp: Boolean) -> Boolean>()
    /** 返回 true 表示已消费 */
    fun dispatch(isVolumeUp: Boolean): Boolean = listeners.lastOrNull()?.invoke(isVolumeUp) == true
}
```

上游 `RouteActivity.dispatchKeyEvent`（`upstream_RouteActivity.kt:150-161`）与宿主 `MainActivity.dispatchKeyEvent` 各加 3 行转发（消费即短路，两入口互斥场景不存在——同一时刻只有一个前台）。语音朗读翻页在内嵌态恢复可用，`volumeKeyListeners`（`RouteActivity.kt:148`）的注册点改为向 VolumeKeyBridge 注册（vendor 内一行）。

### 3.2 宿主侧改动清单

只动 `MainActivity.java` 三处 + 新增一个极小持有，其余全部保留现状：

1. `MainActivity.java:515`：`createRikkaHubEmbedView(this)` 调用签名不变（AiPageHost 保持 73 行，仅删外层主题与字体迁移到期后的清理，见 §2.3）。
2. `aiComposeView()`（:506-554）与 `prewarmAiCompose`（:556）零改动。
3. 新增字段 `volatile int aiNavDepth;` + setter（vendor 上报落点）。
4. 返回桥修复（见 3.3）。

### 3.3 被否掉的备选

| 备选 | 否决理由 |
|---|---|
| 维持 421 行复制 + 脚本对照重放 | AI-001 补丁矩阵已判 P16"大幅重写"（AI-001-report §二）；复制品已腐烂（乱码、注释失实），重放永远靠人眼对照整文件 |
| 给上游 AppRoutes 增加可选参数而不顶层化 | 内嵌态没有 RouteActivity 实例，成员函数无法被宿主 Activity 的组合树调用；仍是死路 |
| 内嵌态改成启动透明 RouteActivity 套娃 | 回到"两个 App 割裂感"（P16 立项动机，PATCHES.md P16 行），违背用户验收硬指标 |
| 宿主用 reflection 调 AppRoutes | 违反 AGENTS.md 铁律 2（禁止发明 API）、混淆不可控 |
| 返回桥维持 `hasEnabledCallbacks()` 判断 | 见 §4.3 递归分析，静态可证缺陷，不能带入新方案 |

## 四、任务卡 7 维度逐一覆盖

### 4.1 Activity/ComposeView 生命周期

- **责任人划分**：宿主拥有 View 层——挂载（`showAiEmbedded:489`）、复用（:491）、INVISIBLE 预组合（`prewarmAiCompose:556`）、切走时的 detach 由现有 `basePage` 页面重建机制处理；vendor 拥有组合层——`ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed`（`AiPageHost.kt:29`）保证 Activity 销毁时释放组合，`rememberSaveableStateHolderNavEntryDecorator` + `rememberViewModelStoreNavEntryDecorator`（`RikkaHubEmbed.kt:225-228`）保证导航条目状态与 VM 生命周期。
- **ViewTreeLifecycleOwner 依赖**：ComposeView 解析 lifecycle 的路径是沿 View 树向上找，宿主 `ComponentActivity`（P19 已改）在 decor view 上设置了 ViewTreeLifecycleOwner，缓存复用（detach 再 attach）不破坏此链——现状已验证（v1.10.0 起真机在用）。**待验证项 V-1**：Robolectric 下模拟 detach→reattach 断言组合不崩溃（纳入 EmbedSweepJvmTest）。
- **测试方式**：EmbedSweepJvmTest.kt（已存在，Robolectric 真实 Embed+导航）；新增一条"backStack pop 到底后宿主 aiNavDepth==1"断言。

### 4.2 AI 页面进入/退出/系统返回

- **进入链**：`goToDestination(4)`（:484）→ DEGRADED 拦截 → `maybeGuideAiPermissions`（AiPermissionGate.java:42-64，宿主拥有）→ `enterAiPage`（:486）→ `showAiEmbedded`（:489）→ 缓存命中直挂 / 未命中挂"正在初始化"占位（:495）+ 下一帧换装（:496）。全部保留，零设计变更。
- **退出**：AI 页无独立退出动画语义（tab 原位淡切，:486 注释），切走即 `primaryBase` 重建其他页，ComposeView 从 root 移除但对象缓存（`aiComposeView` 字段，:488）。
- **系统返回（本方案的核心修复）**：
  - **现状缺陷（静态分析，需真机复验——待验证项 V-2）**：`performSystemBack`（`MainActivity.java:264`）AI 分支为 `if(hasEnabledCallbacks()){onBackPressed();return;}navigateHome()`。`backCallback` 自身在 AI 页 enabled（`canHandleBack:257` `pageKind==5→true`）。androidx dispatcher 的 `onBackPressed()` 只调用**最新注册的 enabled callback**——当 Compose 侧无 enabled BackHandler 时，最新 enabled 就是 backCallback 自己 → `handleOnBackPressed`（:245-248）→ `performSystemBack` → 无限递归。现状未炸的原因大概率是 Compose 内容总有 enabled BackHandler，但这是靠运气不是靠契约。
  - **修复设计**：AI 分支改为显式栈深守卫——
    ```java
    if(pageKind==5){
      if(aiNavDepth>1){getOnBackPressedDispatcher().onBackPressed();return;}
      navigateHome();return;
    }
    ```
    `aiNavDepth` 由改动 C 上报。`backCallback.isEnabled` 在 AI 页保持 true（保预返回动画）。**取舍登记**：栈深 1 时若抽屉/底部弹层开着，按返回直接回主页而非先关弹层——内嵌态产品级取舍（上游独立态无此问题），写入 PATCHES.md；若后续证伪（真机发现关键弹层依赖 back 关闭），升级方案为 vendor 侧补一个 `onOverlayBackEnabled` 布尔上报，不改架构。
  - **边界规则**：vendor 栈深 >1 → 宿主把 back 交给 dispatcher（Compose BackHandler/NavDisplay 消费）；栈深 ==1 → 宿主直接 `navigateHome()`，不经 dispatcher。vendor 永远不调用 `finish()`/`navigateHome()`。

### 4.3 键盘、窗口尺寸、主题、状态恢复

- **键盘/IME**：全部归宿主（AI-001 P19 判定维持）。`MainActivity.java:499-554` 的 ime 重算/粘滞缓存/滑行机制与 vendor 无耦合（vendor 只消费 ime insets 做 `imePadding`），2.5.4 重做后机制原样可用。**待验证项 V-3**：2.5.4 的 ChatPage 输入框 insets 修饰符若上游有变（如换 `safeDrawingPadding`），需重验两段跳表现。
- **窗口尺寸**：宿主 `onConfigurationChanged`（:577）+ `configChanges` 全量声明（`AndroidManifest.xml:15`）→ 旋转/分屏不重建 Activity，Compose 组合树随 View 保留；`adjustResize` 已补（P19 配套）。vendor 侧 Compose 自适应布局随容器尺寸自然响应。
- **主题**：单层 `RikkahubTheme(colorMode = ColorMode.DARK)`（改动 B）；DongfangTheme preset 保持置首 + seeder 播 `themeId="dfwx"`（`BuiltinProviderSeeder.kt:67`）——该行为随 AI-005 的 seeder 重写保留语义（decisions.md #23 配色锁软件紫暗系）。用户主动选的其他主题不被强制覆盖（现有语义，`RikkaHubEmbed.kt:138-140` 注释，仅迁移 sakura）。
- **状态恢复**：进程不被杀场景——View 缓存机制天然保留；进程被杀重建——vendor `rememberNavBackStack` + saveable decorator 走 Activity savedState（组合树挂在宿主 Activity 上，状态恢复由宿主 Activity 的 savedState 承载）。**待验证项 V-4**：进程被杀重建后 AI 页自动恢复（Robolectric `recreate` 场景纳入 Sweep 测试）。

### 4.4 空态/错误态/加载态/流式输出

| 态 | 责任人 | 位置 | 测试方式 |
|---|---|---|---|
| 空会话 | vendor | P19 `ChatList.kt` 空态分支 + P20/P21（`ChatList.kt:307/323/333`，PATCHES 台账补登记项） | EmbedShotsJvmTest 截图目检（343dp 表现随手表停测改为手机 360dp 重拍） |
| 首开加载 | 宿主 | `MainActivity.java:495` "正在初始化 AI 对话…" 占位 + :496 换装 | 真机冷启 8 秒内点 AI（dfwx-verify L1） |
| 迁移中 | vendor | `RikkaHubEmbed.kt:385-416` 迁移遮罩（随 AppRoutes 原文保留，修 `:409` 乱码） | Room 迁移演练（§2.3）+ JVM 断言遮罩文案 UTF-8 |
| 初始化失败 | 宿主 | DEGRADED 拦截 :484/:490 + lazy attach 失败兜底 :496 catch 分支 | 单元：`App.DEGRADED=true` 下 `goToDestination(4)` 不挂 ComposeView（Robolectric） |
| 流式输出 | vendor | ChatVM/ChatGeneration 事件流，宿主零参与 | `rikkahub/ai/src/test` 既有覆盖 + 真机 L1 冒烟 |

### 4.5 宿主导航与 RikkaHub 内部导航边界

- **唯一通道**：宿主→vendor 只有 `createRikkaHubEmbedView(activity)` 一个函数调用；vendor→宿主只有三条回调：`onOpenUsageAccessSettings`（`AiPageHost.kt:39`）、`onNavDepthChanged`（改动 C）、音量键消费（改动 D，双向）。禁止再增加任何 vendor→宿主调用面；vendor 内部不得 import `cc.nkbr.lanzouplus.*`（Gradle 依赖方向 `:app → :rikkahub-app` 单向，`settings.gradle`）。
- **内部导航完全自治**：vendor 栈内 push/pop/转场（NavDisplay transitionSpec）宿主不感知不干预；宿主导航（tab 切换/返回）只在栈深 1 时收权（§4.2）。
- **独立 RouteActivity 并存语义**：SEND/PROCESS_TEXT/TRANSLATE/shortcuts 仍走独立 RouteActivity（vendor manifest），它的 backStack 通过 `DeepLinkSink` SAM 注入（§3.1 改动 A），与内嵌态的 `NoDeepLinks` 互不干扰。**待验证项 V-5**：外部分享文本 → RouteActivity 打发后，与内嵌 AI 页是两个独立导航栈，返回键行为符合预期（真机 L2）。

### 4.6 上游升级时补丁重放策略

改动 A 之后，vendor 对上游文件的 diff 收敛为**一个函数 + 一个接口**：

| 重放单位 | 对照物 | 机械度 |
|---|---|---|
| `ui/routes/AppRoutes.kt` | 上游 RouteActivity 内 AppRoutes 函数体 | 逐行对照，无实例依赖转发心智负担 |
| `dfwx/RikkaHubEmbed.kt`（~40 行） | 无上游对应（纯 DFWX 层） | 不随上游变 |
| `dfwx/AiPageHost.kt` | 同上 | 不随上游变 |
| PATCHES.md 既有 P1-P24 | 上游对应文件 | 沿用 AI-001 矩阵逐项标注 |

流程：拉 2.5.x tag → clean import → 先跑 `EmbedSweepJvmTest` 确认 clean 态可编译 → 按上表重放 → diff 自检（`git diff clean-import-tag -- rikkahub/app/.../RouteActivity.kt` 必须只呈现改动 A 形状）→ 构建验收。**未登记改动不得进入重放表**（AI-001 §二尾行规则）。

### 4.7 测试替身和回退点

- **替身点（设计即注入）**：
  - `DeepLinkSink` 接口：JVM 测试注入 fake intent 序列，替代真实 SEND intent（EmbedSweepJvmTest 直接可用）；
  - `onNavDepthChanged: (Int) -> Unit`：测试捕获栈深序列断言返回桥行为；
  - 宿主 `aiNavDepth`：int 字段，Java 侧 Robolectric 测试直接置值模拟 vendor 上报；
  - `VolumeKeyBridge.listeners`：测试注 fake listener 断言消费优先级。
- **测试基建**：vendor 侧复用 `EmbedSweepJvmTest.kt`/`EmbedShotsJvmTest.kt`/`SweepTestApplication.kt`（`rikkahub/app/src/test/java/me/rerere/rikkahub/dfwx/`，Robolectric + NATIVE 图形，真实 Koin + Room）；宿主侧无 Compose 测试基建，AI 桥逻辑（返回守卫、DEGRADED、navDepth）用 Robolectric 纯逻辑断言，不截图。
- **回退点**：
  - R1：实施卡开工前打 tag `dfwx-ai002-pre`（当前冻结点）；
  - R2：改动 A（顶层化）独立提交——纯移动+参数化，可单独 revert；
  - R3：改动 B/C/D 独立提交；宿主侧改动独立提交；
  - R4：全链验收点 tag `dfwx-ai002-done`；
  - 旧入口删除不在本设计范围（AI-003/004 给证据后才执行，任务卡禁止事项）。回退动作 = 按提交逐个 revert，禁 `git reset --hard`（AI-001 阶段 11 沿用）。

## 五、实施卡拆分建议（供 AI-004/005 引用）

1. **AI-004a（vendor 结构重构）**：2.5.4 clean import 前提下执行改动 A → B → C → D + 乱码修复；验收 = Sweep/Shots 测试全绿 + `:app:assembleEmptyRelease` + aapt 断言。
2. **AI-004b（宿主桥修正）**：`aiNavDepth` 字段 + 返回守卫替换 `MainActivity.java:264` AI 分支；验收 = Robolectric 返回守卫测试 + 真机 L1（进入/返回/键盘/预返回动画）。
3. **AI-005（数据层）**：seeder 重写（decisions.md #3、#10）、P24 到期清理、字体/主题迁移收口——边界见 §2.3。
4. 每卡完成必须同步 `rikkahub/PATCHES.md`（P16 条目改写为新方案描述，乱码项闭环）。

## 六、遗留验证清单（进 risk-register）

| # | 事项 | 验证方式 |
|---|---|---|
| V-1 | 缓存 ComposeView detach→reattach 组合存活 | Robolectric 场景断言 |
| V-2 | `MainActivity.java:264` 递归风险是否真机可复现（决定修复紧迫级） | 真机 AI 根态连续返回 |
| V-3 | 2.5.4 ChatPage insets 修饰符是否兼容宿主 ime 滑行机制 | 真机键盘两段跳回归 |
| V-4 | 进程被杀后 AI 页 savedState 恢复 | Robolectric recreate |
| V-5 | 独立 RouteActivity 与内嵌态双栈并存返回行为 | 真机 L2 分享链路 |
| V-6 | 2.5.4 Room schema 与当前 vendor 差异（AI-001 待验证 #9 的继续项） | 迁移演练 |

（完）
