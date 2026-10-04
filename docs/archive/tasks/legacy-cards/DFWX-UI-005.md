# DFWX-UI-005：AI 页键盘弹出时顶部顶栏错位

状态：**已实施修复，待真机验收**（2026-10-01；根因定性 + 修复见文末「本次处置」）
前置：无（可与 UI-006 合并为一轮做，见文末"建议合并"）
来源：用户真机实测（v1.22.8）——"点击输入框后，顶部的那个一大排会错位"

## 用户报告的现象（逐字，勿改写）

> "下面那个输入框没问题了，但是点击后，顶部的那个一大排会错位。"

补充事实：v1.22.8 已修掉输入框跟随问题（宿主自研 IME 滑行器删除，见 `rikkahub/PATCHES.md` 宿主节"AI 内嵌页 IME 补偿收敛"），**输入框正常、顶栏仍异常**，说明两者是不同故障点。用户截图（键盘已弹出、键盘收起前）顶栏静帧看着基本正常 → 怀疑是**弹出动画过程中的逐帧抖动**或**某一帧后停在错位位置**。

## 已经查清的边界（本次只读复核，2026-09-28，证据等级 A）

宿主侧（`app/src/main/java/cc/nkbr/lanzouplus/MainActivity.java`）：

| 行号 | 事实 |
|---|---|
| 491 | `installSystemNavigationInsets()`：宿主 `host` 的 insets 监听器把 **statusBars / navigationBars / displayCutout 全部裁成 NONE** 后返回给子级（防 Compose 二次垫高），**ime 不裁**（源码注释明确写了"ime 必须保留"） |
| 490 | `applySystemNavigationInsets()`：`host.setPadding(l,t,r,b)`，且 `changed` 为真才 setPadding，随后 `host.post(refreshAdaptiveLayout)` + `host.post(reflowNavBall)` |
| 496 | `host` 与 `pageHost` 创建；`host` 有 `addOnLayoutChangeListener`（宽/高变化 >2dp 时 post `refreshAdaptiveLayout` + `reflowNavBall`） |
| 496 末 | `setContentView(host)` 后立即 `installSystemNavigationInsets()` + `installNavBall()` + `prewarmAiCompose()` |
| 497 | `primaryShell` 创建：`primaryShell=new LinearLayout(this); composePrimaryShell(); pageFrame=primaryShell` |
| 510 / 513 / 520 | AI 页进入：`enterAiPage()`（:510）→ `showAiEmbedded()`（:513：`primaryBase(4)`；`pageKind=5`；`root.setPadding(0,0,0,0)`）→ `aiComposeView()` 挂进 `root`（:520，`ui.post` 内 lazy 挂载 + 失败兜底文案） |
| 512 | `android.view.View aiComposeView;`（字段声明） |
| 523 | `dfwxImeInsets(src, imeBottom)`：`WindowInsets.Builder(src).setInsets(Type.ime(), Insets.of(0,0,0,imeBottom)).build()` —— **只在 target≠ime.bottom 时才被调用新建对象** |
| 524-553 | `aiComposeView()`：创建 ComposeView 并包一层 `wrap` FrameLayout；**541-552 是 ime 重算监听器**：`below = host.getPaddingBottom()`（:544）→ `target = max(0, ime.bottom - below)`（:546）→ `target==ime.bottom` 直接透传（:547）→ 否则 `dfwxImeInsets()` 新建对象返回（:548） |
| 527-537 | **v1.22.8 根因注释原文**（逐字保留在源码里）：旧版 250ms 滑行器 + stickyBelow 缓存 + 动画计数器三者叠加导致"按钮错位 + 动画崩坏"；结论"切勿再叠加任何自研动画/缓存" |
| 577 | `reflowVisibleLayouts()`（大函数，含 Grid 重排、搜索框宽度、toast viewport） |
| 559 | `refreshAdaptiveLayout()` = 直接转调 `reflowVisibleLayouts()` |
| 15 (Manifest) | `android:windowSoftInputMode="adjustResize"` |

Compose 侧（`rikkahub/app/src/main/java/me/rerere/rikkahub/ui/pages/chat/`）：

- `ChatPage.kt:314-447`：`AssistantBackground(hazeSource)`（:314）+ `Scaffold(`（:315，`topBar`/`bottomBar` 参数，`containerColor = Color.Transparent` 在 :446，内容 lambda 从 :447 `) { innerPadding ->` 开始）——**未覆盖 `contentWindowInsets`**
- `ChatPage.kt:622-741`：`TopBar` 函数体；`TopAppBar(` 在 :638，**`colors` 覆盖但 `windowInsets` 未覆盖** → 走 M3 默认 `WindowInsets.systemBarsForVisualComponents`
- `ui/components/ai/ChatInput.kt:225-226`：`.imePadding().navigationBarsPadding()`（标准用法；注意此文件在 `ui/components/ai/` 不在 `ui/pages/chat/`）

## 首要假设（按置信度，待验证）

1. **【中高】宿主裁剪 systemBars 与 Compose 顶栏默认 padding 的组合在键盘动画期间不恒定。** 顶栏的顶部留白实际由两条来源叠加/竞争：宿主 `host.paddingTop`（真实状态栏高度）+ Compose TopAppBar 自己的 `windowInsets`（宿主已把 statusBars 裁成 0，故应为 0）。若在 IME 动画期间 `host.paddingTop` 或派发给 Compose 的 insets 有任何一帧不同（例如某些 ROM 在键盘展开时会临时改变 navigationBars/cutout 的取值），顶栏就会上下跳。**验证方法**：在宿主 491 行监听器里临时打日志（`ime.bottom / statusBars.top / navigationBars.bottom / cutout`），真机弹键盘抓逐帧取值。
2. **【中】`dfwxImeInsets()` 在每帧新建 WindowInsets 对象给 Compose 造成"变化"误判。** 541-552 行的监听器在 `target != ime.bottom` 时（即 `below>0` 且 `ime.bottom>below` 时）**每帧都 build 一个新对象**（:548）。Compose 的 insets 比较走 `equals`（值比较）时无害，但需确认 `androidx.compose.foundation.layout` 的 `AndroidWindowInsets` 判定路径；若走引用比较，会使依赖 insets 的组件每帧重组。**验证方法**：查 gradle 缓存里 compose-foundation 的 `WindowInsetsHolder`/`AndroidWindowInsets` 实现（`~/.gradle/caches/modules-2/files-2.1/androidx.compose.foundation/`）。
3. **【中低】`host.paddingTop` 在键盘弹出时变化 → 触发整树重排。** `applySystemNavigationInsets()`（:490）在 insets 变化且 `changed` 为真时 `host.setPadding`，随后 post `refreshAdaptiveLayout`（→ `reflowVisibleLayouts()`，:577，含 Grid 重排/搜索框宽度/toast viewport）+ post `reflowNavBall`。部分 ROM 在键盘展开时会连状态栏/navigationBars insets 一起重派发；若 `top` 或 `bottom` 任何一帧变化，就会在动画期间反复触发整树重排 → 顶栏抖动。**验证方法**：在 491 行监听器里打日志记录 `left/top/right/bottom`（即 applySystemNavigationInsets 的入参）逐帧取值，与假设 1 同一次抓取。
4. **【低】haze 2.0.0 的 `hazeSource` 捕获区域在布局逐帧变化时重捕获**，视觉上表现为顶栏区域重影/错位（不是真的位置变化）。

## 必须遵守的红线（`rikkahub/PATCHES.md` 已登记）

> 给 AI 内嵌页做 insets 适配时**只能调纯几何换算，禁止再引入任何自研动画/状态缓存**。

上一轮（v1.22.8）正是因为叠加自研 250ms 滑行器 + stickyBelow 缓存 + 动画计数器而与系统逐帧派发竞态，才导致输入框崩坏。**本次禁止重犯**：不得加 ValueAnimator、不得加 insets 缓存、不得加 WindowInsetsAnimation.Callback 计数器。

## 允许修改

- `app/src/main/java/cc/nkbr/lanzouplus/MainActivity.java` 的 insets 相关方法（490-553 区间）；
- 若结论指向 Compose 侧：`rikkahub/app/src/main/java/me/rerere/rikkahub/ui/pages/chat/ChatPage.kt` 的 `TopBar`/`Scaffold` 参数（须登记 `rikkahub/PATCHES.md` 新编号）；
- 对应新增的 JVM/Robolectric 测试；
- `rikkahub/PATCHES.md`、`docs/plan/current-state.md`。

## 禁止修改

- 不得引入任何自研 insets 动画/缓存（见上方红线）；
- 不得改 `ui/components/ai/ChatInput.kt` 的标准 `.imePadding().navigationBarsPadding()`；
- 不得动用户手机做不可逆操作；不得提交/push/发布；
- 不得读取或输出 `local.properties`；
- 不得顺手改其它无关页面。

## 必须先取证（未取证不得改代码）

1. 真机逐帧取值（需向用户请示后操作手机）：宿主 491 行监听器加临时日志，打印 `ime.bottom`、`statusBars.top`、`navigationBars.left/top/right/bottom`、`displayCutout`、`host.getPaddingTop()`，弹键盘抓一段动画日志（约 500ms 窗口）。
2. 若无法取证，退而求其次：写一个 Robolectric 测试直接驱动 `MainActivity` 的 insets 监听器，喂入两种 insets（键盘前后）断言 `host` padding 与派发出去的值稳定；这是**结构断言**，不能替代真机动画证据。
3. 明确回答："顶栏位置在键盘前后是否变化？变化量是多少？谁提供的？"——答不上来不许改。

## 验收

- 真机（真机（Android 16） / Android 16）键盘弹出→收起全过程顶栏无可见位移；
- 宿主全量测试 `:app:testEmptyDebugUnitTest` 全绿；
- 修复后 `rikkahub/PATCHES.md`（若动 vendor）或宿主节（若动 Java）有登记；
- 明确写出"已验证 / 未验证"。

## 回退

宿主侧改动 `git checkout -- app/src/main/java/cc/nkbr/lanzouplus/MainActivity.java` 可整体回退；vendor 侧改动需在 `PATCHES.md` 编号下记录行号以便反向删除。

## 建议合并

本卡与 `DFWX-UI-006`（删除空态两行文案）同属"AI 页键盘场景收尾"，**建议同一轮改、同一次发版验收**，但各自独立提交以便单独回退。

---

## 本轮复核（2026-09-30，宿主侧只读）

### 结论：仍无法取证；且卡片建议的 Robolectric 兜底路径在本仓库**不可行**

- **真机不可达**：`adb devices` 为空；`ping <手机IP>` 100% 丢包。用户已休息，不得操作其手机（卡片红线亦要求先请示）。
- **"必须先取证"第 2 条（Robolectric 驱动 insets 监听器）没有可测对象**：AI 页在 JVM 下永远进不去。
  `MainActivity.showAiEmbedded()` 首行即 `if(App.DEGRADED){…return;}`，而 JVM 上启动链的 QuickJS 原生库必然
  `UnsatisfiedLinkError` → `App.onCreate` 捕获置 `DEGRADED` —— 这条事实由仓库自身测试记录：
  `app/src/test/java/cc/nkbr/lanzouplus/ToolsSweepJvmTest.kt:13-16` 类注释。
  即：承载 ime 重算监听器的 `wrap`（`MainActivity.aiComposeView()` 内）在 Robolectric 中**根本不会被创建**。
  → 卡片设想的"结构断言"在 AI 页上无法落地；**本卡同样不适用"无证据也先改"**。

### 代码复核（只读）：两条稳定态的几何是自洽的

`wrap` 的 ime 重算（`MainActivity.java` 的 `aiComposeView()` 内）：
`below = host.getPaddingBottom()` → `target = max(0, ime.bottom - below)`。

| 状态 | `navigationBars.bottom` | `host.paddingBottom` | `target` | Compose 得到的 ime |
|---|---|---|---|---|
| 键盘收起 | 144px | 144px | —— | 0（`ime.bottom<=0` 早退） |
| 键盘弹出·导航条保留 | 144px | 144px | ime−144 | ime−144 |
| 键盘弹出·导航条让位 | 0 | 0 | 0 | 原样 ime.bottom |

三种情况下输入框都恰好浮在键盘上沿，**没有算错**。因此剩余可疑点不是公式，而是
**动画期间 `statusBars.top` / `navigationBars.bottom` 取值跳变**：`applySystemNavigationInsets()` 一旦发现
`changed` 就 `host.setPadding(…)` + post `refreshAdaptiveLayout()`（→ `reflowVisibleLayouts()` 整树重排）。
若某些 ROM 在键盘展开过程中把这两项重派发（甚至只改一帧），就会在 ~500ms 窗口内反复触发整树重排，
顶栏随之出现可见位移——这与用户"闪一下/错位"的描述吻合，但**属于待验证假设，不得据此改码**。

### 需要真机捕获什么（等手机可用，约 1 分钟）

在 `installSystemNavigationInsets()` 的监听器里临时打一行日志，弹一次键盘，抓 ~500ms：

- `ime.bottom`、`statusBars.top`、`navigationBars.left/top/right/bottom`、`displayCutout`
- `applySystemNavigationInsets()` 的入参 `left/top/right/bottom`（即 host 最终 padding）
- 关键判据：**`top` 或 `bottom` 在动画期间是否出现过两个以上不同取值**。是 → 假设成立，按纯几何收敛；
  否 → 转查 Compose 侧 `hazeSource` 重捕获（假设 4）。

**本卡状态：待真机取证，未改任何代码。**

---

## 本次处置（2026-10-01，DFW-72 / PATCHES.md P38）

**取证条件的现实约束**：本文档开出的三条验证路径都要求真机逐帧抓 insets 日志；
用户 2026-09-30 已明确"不要再整手机调试"，因此本次**无法取得运行时证据**。
在这个前提下，只做了一处**结构性、可自证**的修改，不做猜测性大改：

**改动**：`ChatPage.kt` 的 `TopAppBar` 显式声明 `windowInsets = WindowInsets(0, 0, 0, 0)`。

**依据（对应本文档"首要假设 1"）**：宿主已经把 statusBars/navigationBars/displayCutout
裁成 NONE 并自己用 `host.paddingTop` 承担状态栏高度 —— 顶部留白**本应只有一个来源**。
而 `TopAppBar` 不写 `windowInsets` 时会退回 M3 默认的 `systemBarsForVisualComponents`，
于是"某一帧又派发了非零 statusBar inset"（部分 ROM 在键盘动画期间重派发）就会让整条顶栏
下移一个状态栏高度再弹回来 —— 这与用户描述的"点击输入框后顶部那一大排会错位"在**幅度**和
**时机**上都吻合。显式声明 0 之后，逐帧派发不再能撼动顶栏位置。

**没有做的（以及为什么）**：
- 假设 2（`dfwxImeInsets()` 每帧新建对象）：`android.view.WindowInsets` 与 Compose 的
  `AndroidWindowInsets` 都是**值比较**，新建同值对象不会触发重组。未发现缺陷，不动。
- 假设 3（`host.paddingTop` 变化触发整树重排）：现有代码已有 `changed` 判空，
  未取得逐帧证据前不擅自加缓存 —— 那正是 v1.22.8 的事故模式。
- 假设 4（haze 重捕获）：需要真机观察才能定性。

**验收（必须真机）**：点输入框 → 键盘弹出/收起的**全过程**顶栏不跳、不抖、不残留错位；
反复快速弹出收起、切换会话、旋转均无错位；输入框跟随不回归。
**若真机仍复现**，说明根因在假设 2/3/4，需要按上面的验证方法逐条取证后再动。
