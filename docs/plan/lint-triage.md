# DFW-123 lint 清零并纳入门禁 —— 第一阶段只读分析

> 任务卡：`DFW-123 lint 清零并纳入门禁（现在 14 个 error 没人看）`
> 卡片 id `d70b31dd-65af-4ed4-bfbd-1d7e91257a2e`（面板里显示为 **DFW-112**，编号错位是历史遗留）
> 阶段：**第一阶段（只读分析）**，未修改任何现有文件。
> 分析时间：2026-10-02 深夜。分析依据：`app/build/reports/lint-results-emptyRelease.{txt,xml}`（**2026-10-02 10:32 生成**）+ 当前工作区源码。

---

## 0. 先说一个必须先解决的时效性问题（重要）

**那份 lint 报告已经不是当前代码的报告了。**

| 事实 | 证据 |
|---|---|
| 报告生成于 **10:32** | `app/build/reports/lint-results-emptyRelease.txt` mtime |
| `FeedbackPage.java` 在 **14:51** 被改过 | 文件 mtime；`git status` 干净（已提交） |
| 该文件**现在已经带了抑制** | `app/src/main/java/cc/nkbr/lanzouplus/FeedbackPage.java:282` = `@SuppressLint("GestureBackNavigation")` |
| `LanzouWebActivity.java` 在 **14:15** 被改过，同样已抑制 | `LanzouWebActivity.java:93` |

结论：报告里那 2 条 `GestureBackNavigation` 中，**`FeedbackPage` 那条已经在 10:32 之后被修掉了**（另一窗口所为），当前源码不会再报。
`LanzouWebActivity` 不在 14 条里，正是因为它早已带抑制 —— 反过来印证"带抑制 = 不报"这条规律。

**所以：报告的"14 条"必须理解为"10:32 时刻的 14 条"；按当前源码逐条核对后剩 13 条。**
（`MainActivity.java` 22:00 之后另有他人在改，行号也已漂移，见下表"报告行号 / 当前行号"两列。）

**阶段二第一件事必须是重跑一次 lint 拿新基线，本文所有"当前行号"以此为准再校一次。**

---

## 1. 真实 error 条数（核实结果）

| 口径 | 条数 |
|---|---|
| 报告文件自述 | **14** |
| XML 里 `severity="Error"` 的 `<issue>` 节点数 | **14**（与自述一致，无遗漏） |
| 报告末尾统计行 | `14 errors, 116 warnings`（`lint-results-emptyRelease.txt:716`） |
| **按当前源码逐条核对后仍会报** | **13**（`FeedbackPage` 那条已修） |

**"14 条"是真的，不是标题党；但其中 1 条已经过期。** 卡片担心的"接进门禁会一次红 14 条"，实际会红 **13** 条。

### 1.1 阶段二实测结果：**0 error / 118 warning**

| 时点 | error | warning | 证据 |
|---|---|---|---|
| 10:32 那份报告（**旧代码**） | 14 | 116 | `lint-results-emptyRelease.txt:716` |
| 阶段二冷跑（**修复后**） | **0** | **118** | 新报告 `lint-results-emptyRelease.txt:632` = `0 errors, 118 warnings`；XML `severity="Error"` 计数 = **0**；`BUILD SUCCESSFUL in 7m 14s` |

四个被处置的 issue 类型在新报告里**全部为 0**（脚本核对）：
`MissingPermission` 0、`GestureBackNavigation` 0、`NewApi` 0、`RestrictedApi` 0。

⚠️ **诚实声明（重要）**：**我没有观测到"13"这个中间状态。**
因为我在跑基线之前就已经把 9 条 A 类 + 4 条 B 类全部改完了 —— 重跑时直接是 0。
"13" 是阶段一**静态核对**的结论（14 减去已修的 `FeedbackPage`），不是实测值。
它的作用是说明"卡片说的 14 条里有一条已经不用管了"。

**"这 9 条确实是被我的改动消掉的"** 这件事，用**反向探针**证明（§10.3），不是靠推断：
摘掉 `@RequiresApi(30)` → 那 5 条 `NewApi` **原样回来，连列号都一致**（110/153/194/224/245）。

---

## 2. 逐条清单

> 级别列全部为 **Error**（lint 的 `severity="Error"`）。
> "报告行号"= 10:32 那份报告里的行号；"当前行号"= 我按内容在当前源码里定位到的行号。

| # | Issue id | 文件:行号（当前） | 报告行号 | 完整消息（原文摘要） | 分类 |
|---|---|---|---|---|---|
| 1 | `MissingPermission` | `ToolHost.java:1469` | 1469 | Call requires permission which may be rejected by user: code should explicitly check ... or explicitly handle a potential `SecurityException` | **A** |
| 2 | `GestureBackNavigation` | ~~`FeedbackPage.java:275`~~ | 275 | `onBackPressed` is no longer called for back gestures; migrate to AndroidX's `OnBackPressedDispatcher` | **已修复** |
| 3 | `GestureBackNavigation` | `SupportActivity.java:126` | 126 | 同上 | **B** |
| 4 | `NewApi` | `MainActivity.java:954` | 934 | Call requires API level 29 (current min is 26): `android.graphics.Insets#of` | **A** |
| 5 | `NewApi` | `MainActivity.java:954` | 934 | Call requires API level 29: `android.view.WindowInsets.Builder#build` | **A** |
| 6 | `NewApi` | `MainActivity.java:954` | 934 | Call requires API level 29: `new android.view.WindowInsets.Builder` | **A** |
| 7 | `NewApi` | `MainActivity.java:954` | 934 | Call requires API level 30: `WindowInsets.Builder#setInsets` | **A** |
| 8 | `NewApi` | `MainActivity.java:954` | 934 | Call requires API level 30: `WindowInsets.Type#ime` | **A** |
| 9 | `NewApi` | `MainActivity.java:2025` | 1950 | Call requires API level 31: `android.view.Window#setBackgroundBlurRadius` | **A** |
| 10 | `NewApi` | `MainActivity.java:2027` | 1952 | Call requires API level 31: `WindowManager.LayoutParams#setBlurBehindRadius` | **A** |
| 11 | `NewApi` | `MainActivity.java:2063` | 1988 | Call requires API level 31: `WindowManager#removeCrossWindowBlurEnabledListener` | **A** |
| 12 | `RestrictedApi` | `MainActivity.java:224` | 224 | `ComponentActivity.dispatchKeyEvent` can only be called from within the same library group prefix | **B** |
| 13 | `RestrictedApi` | `MainActivity.java:231` | 231 | 同上（`super.dispatchKeyEvent` 调用点） | **B** |
| 14 | `RestrictedApi` | `MainActivity.java:231` | 231 | 同上（同一次调用的第二个引用位置） | **B** |

**分类合计：A = 9 条，B = 4 条，已修复 = 1 条，C（暂不修） = 0 条。**
另有一条**不产生 lint error 但值得进风险台账**的已知限制，见 §3.3。

---

## 3. 逐条分类依据与具体修法

### 3.1 A 类：真问题（要改代码，不是抑制）

#### A-1　`ToolHost.java:1469` — 麦克风权限只靠"上游保证"，本地没有兜底（1 条）

**现场**（`ToolHost.java:1465-1471`）：

```java
action(actions,"开始测量",()->act.requestToolMicPermission(()->{
  if(running.get())return;
  try{
    int rate=44100,min=AudioRecord.getMinBufferSize(...);
    android.media.AudioRecord r=new android.media.AudioRecord(...MIC...);   // ← 1469，被报
```

**为什么是 A 而不是"误报"**：调用链**当前确实是对的** —— 外面套了 `requestToolMicPermission`（`MainActivity.java:3396`，未授权就 `requestPermissions` 后 `return`），而且整段被 `try{...}catch(Exception e){act.showNotice("无法启动麦克风："+e.getMessage(),true);}`（`ToolHost.java:1491`）包住，`SecurityException` 会被接住并提示用户。

**但这是"非局部保证"**：
1. 权限可能在"用户点同意"和"真的 new AudioRecord"之间被撤销（系统设置里撤、或权限自动回收）；
2. `requestToolMicPermission` 的实现将来若改成"拒绝也回调"，这里立刻变成崩溃点；
3. 这段代码能成立，依赖读者同时看两个文件。

**具体怎么修**（在 `ToolHost.java:1468` 之前、`try{` 之内插入）：

```java
android.content.Context micCtx=act.context();
if(micCtx==null||micCtx.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)!=android.content.pm.PackageManager.PERMISSION_GRANTED){
  act.showNotice("没有麦克风权限，无法测量",true);
  return;
}
```

- `checkSelfPermission` 是 API 23+，项目 minSdk 26（`AGENTS.md` 二）；
- lint 的 `MissingPermission` **认这个守卫**，会自然消失，不需要任何 suppress；
- 顺带把"权限被撤销"这条真实边界补上了 —— 是加功能，不是消音。

---

#### A-2　`MainActivity.java:954` — `dfwxImeInsets` 的 API 契约只存在于调用点（5 条）

**现场**：

```java
// MainActivity.java:954
private static android.view.WindowInsets dfwxImeInsets(android.view.WindowInsets src,int imeBottom){
  return new android.view.WindowInsets.Builder(src)                                   // 29
      .setInsets(android.view.WindowInsets.Type.ime(),                                 // 30
                 android.graphics.Insets.of(0,0,0,imeBottom))                          // 29
      .build();                                                                        // 29
}
```

**唯一调用点在守卫之内**（`MainActivity.java:971` → `:979`）：

```java
if(Build.VERSION.SDK_INT>=30){
  wrap.setOnApplyWindowInsetsListener((v,insets)->{
    ...
    return dfwxImeInsets(insets,target);      // 979
```

**为什么 lint 还是报**：`NewApi` 的 `SDK_INT` 检查**不跨方法传播**。调用方的 `>=30` 对 `dfwxImeInsets` 内部不可见，所以方法体里的 5 个 29/30 API 全被报。这是**抽方法**必然产生的误报，**当前运行期是安全的**（`>=30` 已覆盖 29 与 30 的要求）。

**具体怎么修**：给方法加契约注解（`androidx.annotation` 已是 `compileOnly` 依赖，`app/build.gradle.kts:214`）：

```java
@androidx.annotation.RequiresApi(30)
private static android.view.WindowInsets dfwxImeInsets(...)
```

选它而不是 `@SuppressLint` 的理由：
- 它**如实声明**了契约（"调用我的人必须自己保证 ≥30"），而不是让 lint 闭嘴；
- 加了之后 lint 会把检查**推到调用点** —— 将来谁在没守卫的地方调它，照样报错，鉴别力还在；
- 项目里目前**没有任何** `@RequiresApi` / `@TargetApi` / `@SuppressLint("NewApi")` 用法（全仓 grep 为空），这是第一处，正好把规矩立起来。

---

#### A-3　`MainActivity.java:2025 / 2027` — 背景模糊依赖"调用方一定传 blur=false"（2 条）

**现场**（`MainActivity.java:2016-2029`）：

```java
void applyNoticeWindowGlass(android.view.Window window,boolean blur,int radius,float density){
  ...
  if(!blur)return;                                                     // 2023
  window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
  window.setBackgroundBlurRadius(GlassSurface.backgroundBlurPx(density));   // 2025 ← 31
  android.view.WindowManager.LayoutParams lp=window.getAttributes();
  lp.setBlurBehindRadius(GlassSurface.blurBehindPx(density));               // 2027 ← 31
```

**当前是安全的**，但安全来自**另一个类**：

| 环节 | 位置 | 内容 |
|---|---|---|
| blur 参数的唯一来源 | `MainActivity.java:1844` | `final boolean blur=GlassSurface.isBlurEnabled(this);` |
| 该来源的内部守卫 | `GlassSurface.java:92-94` | `blurApiAvailable()` → `Build.VERSION.SDK_INT>=Build.VERSION_CODES.S` |
| 该来源的复核 | `GlassSurface.java:103` | `if (!blurApiAvailable() \|\| context == null) return false;` |

即：`SDK<31 → isBlurEnabled=false → blur=false → :2023 提前 return → API31 调用不可达`。
**这条推理链跨了 2 个文件、3 个方法，lint 看不见，而且方法签名 `boolean blur` 对任何新调用方都是敞开的** —— 谁哪天传个 `true` 进来，API 26 设备上就是 `NoSuchMethodError`（注意 `:2025` 那两行**没有** try/catch 兜底）。

**具体怎么修**（把非局部不变量变成局部守卫，一行）：

```java
if(!blur)return;
if(Build.VERSION.SDK_INT<31)return;   // [DFW-123] 本方法内的模糊 API 全在 31
```

加这一行之后：lint 报错自然消失（同一方法体内的 `SDK_INT` 检查它认），**运行期语义完全不变**，而且方法自身变成"随便谁调都安全"。这是加固，不是消音。

---

#### A-4　`MainActivity.java:2063` — 解除模糊监听同样只靠"监听器必为 null"（1 条）

**现场**（`MainActivity.java:2057-2065`）：

```java
void unregisterNoticeBlurListener(){
  java.util.function.Consumer<Boolean> listener=noticeBlurListener;
  noticeBlurListener=null;
  if(listener==null)return;                        // 2060
  try{
    android.view.WindowManager manager=(WindowManager)getSystemService(WINDOW_SERVICE);
    if(manager!=null)manager.removeCrossWindowBlurEnabledListener(listener);   // 2063 ← 31
  }catch(Throwable ignored){}
}
```

**当前安全**：`noticeBlurListener` 只在 `registerNoticeBlurListener`（`:2037`）里赋值，而它在 `:2039` 有 `if(!GlassSurface.blurApiAvailable()||window==null)return;`。所以 API<31 时 `listener` 恒为 null → `:2060` 提前返回。
而且这一条**另有 `catch(Throwable ignored)`**（连 `NoSuchMethodError` 都会吞掉），**风险等级低于 A-3**。

**仍然归 A 的理由**：它和 A-3 是同一个病 —— "能不出事，靠的是别人先挡了一道"。同一个方法里的两行代码，读者没法只看一处就判断安全。

**具体怎么修**：`:2057` 方法体第一行加

```java
if(Build.VERSION.SDK_INT<31)return;   // [DFW-123] removeCrossWindowBlurEnabledListener 是 API 31
```

两个调用点（`:1974` 弹窗 dismiss 回调、`:2038` register 内部）都受影响，但语义不变（低版本本来就无事可做）。

---

### 3.2 B 类：误报 / 不适用（要抑制，且必须带理由）

#### B-1　`SupportActivity.java:126` — `onBackPressed` 覆盖（1 条）

**现场**：

```java
/** 系统返回（边缘滑动 / 返回键）也走同一个退场，保证两条路径观感一致。 */
@Override public void onBackPressed(){
  closePage();          // overridePendingTransition(0,0); finish();
}
```

**为什么判 B**：
1. `onBackPressed()` 在**返回键 / 三键导航**下仍然有效 —— 本类的用途（`SupportActivity` 的退场转场）在这两条路径上是对的；
2. 项目对这个 issue 已经**两次给出同样的处置**：`FeedbackPage.java:282`、`LanzouWebActivity.java:93` 都是 `@SuppressLint("GestureBackNavigation")`。第三条如果改成 `OnBackPressedDispatcher`，就会出现"同类页面三种返回实现"——正是 `lessons.md` 反复讲的"改样式时必然只改一处"的反面；
3. 迁移到 `OnBackPressedDispatcher` 是一次**行为改动**（要接 `androidx.activity` 的回调、要让 `closePage` 在 predictive back 里也生效），不该塞进"lint 清零"这张卡里做。

**具体怎么修**（与既有两处保持逐字一致的写法）：

```java
/** [DFW-123] 与 FeedbackPage/LanzouWebActivity 同款：本类只需返回键/三键导航的退场，
    预测性返回手势的迁移见 risk-register（已记为已知限制）。 */
@android.annotation.SuppressLint("GestureBackNavigation")
@Override public void onBackPressed(){
  closePage();
}
```

**不写 `<issue id="GestureBackNavigation">` 到 `lint.xml` 的理由**：`lint.xml` 的粒度是"文件 + issue"，会把这个文件**将来**新加的 `onBackPressed` 也一起静音；写在这里则只影响这一个方法，且读代码的人立刻看得到理由。

---

#### B-2　`MainActivity.java:224 / 231` — `dispatchKeyEvent` 覆盖与 `super` 调用（3 条）

**现场**：

```java
// MainActivity.java:30
public final class MainActivity extends androidx.activity.ComponentActivity implements ToolHost.Host {

// MainActivity.java:221-232
/** [DFWX PATCH P16] 内嵌 AI 页的音量键滚动：上游音量键监听挂在 RouteActivity 实例上，
    内嵌态（AppRoutes 跑在本 Activity 的 ComposeView 里）拿不到该实例，故经进程级
    VolumeKeyBridge 注册与分发。仅在 AI 页（pageKind==5）且监听者消费时才拦截。 */
@Override public boolean dispatchKeyEvent(android.view.KeyEvent event){      // 224 ← 报
  if(pageKind==5&&event.getAction()==android.view.KeyEvent.ACTION_DOWN){ ... }
  return super.dispatchKeyEvent(event);                                      // 231 ← 报 ×2
}
```

**为什么判 B**：
1. 报的是 **`RestrictedApi`**，消息原文是"只能从同一个 library group 前缀里调用"（`referenced groupId=androidx.core`，调用方 `groupId=dongfangwuxian`）。这是 androidx 用 `@RestrictTo(LIBRARY_GROUP_PREFIX)` 表达的**内部策略**，不是运行期限制；
2. 覆盖一个 public 方法并调 `super` 是标准 Java 用法。**这里不调 `super` 才是 bug** —— `ComponentActivity.dispatchKeyEvent` 负责把返回键喂给 `OnBackPressedDispatcher`，去掉它等于把返回键处理弄坏；
3. 本方法是被 `[DFWX PATCH P16]` 明确引入的（注释在 `:221-223`），它有真实的、别处替代不了的需求（音量键桥），不是随手写的。

**具体怎么修**：

```java
/** [DFW-123] RestrictedApi 误报：覆盖 public 方法并调 super 是标准用法；
    此处若去掉 super 会破坏返回键 → OnBackPressedDispatcher 的喂键链路（PATCH P16 需要覆盖本身）。
    androidx 的 @RestrictTo(LIBRARY_GROUP_PREFIX) 是内部策略标记，不构成运行期限制。 */
@android.annotation.SuppressLint("RestrictedApi")
@Override public boolean dispatchKeyEvent(android.view.KeyEvent event){
```

同样不建议写进 `lint.xml`：那会把 `MainActivity.java`（当前 4900+ 行、将来还要加）里**所有**将来的 `RestrictedApi` 一起静音。

---

### 3.3 C 类：已知但暂不修（进风险台账，不产生 lint error）

**没有一条 lint error 属于"就这样放着"** —— 13 条全部有明确处置（9 修 + 4 抑制）。

但 **B-1 背后有一个真实的、必须记账的限制**，建议在 `risk-register.md` 追加一条（编号顺延到 **R-22**）：

| 字段 | 内容 |
|---|---|
| ID | R-22 |
| 等级 | P3 |
| 风险 | 三个 Activity（`FeedbackPage` / `LanzouWebActivity` / `SupportActivity`）用 `onBackPressed()` 处理返回，**Android 13+ 的预测性返回手势（predictive back）不会走这条路**，用户在边缘滑动时可能看到"预测动画回退到上一屏"而不是本页的退场动画 |
| 当前状态 | 已知、**有意接受**。三处均为 `@SuppressLint("GestureBackNavigation")`；项目对"返回观感"已有统一转场实现，改成 `OnBackPressedDispatcher` 属行为改动，未排期 |
| 下一证据 | 真机（vivo V2425A / Android 16）开"预测性返回手势"后，边缘滑动这三个页面，录屏对比是否有动画割裂 |
| 责任任务 | 待建卡（建议并入下一轮 UI 体验批次） |

**我不把这条写成 A 的理由**：迁移会改变返回动画行为，属于用户可见的观感改动，按 `AGENTS.md` 三-8 应作为方案选型给用户看，不该在"lint 清零"里顺手做。

---

## 4. `app/lint.xml` 现状

**不存在**。核实方式：

```
find . -name "lint.xml" -not -path "*/build/*" -not -path "*/node_modules/*"   → 无输出
grep -n "lint" app/build.gradle.kts build.gradle.kts settings.gradle.kts        → 无输出
ls -d build-logic buildSrc                                                      → 无（没有约定插件藏 lint 配置）
```

即 **lint 目前 100% 跑在 AGP 默认配置上**，没有任何一处自定义。这解释了为什么 14 条 error 一条都没被压过。

阶段二若要引入 `lint.xml`，标准位置是 **`app/lint.xml`**（AGP 按模块目录自动查找）；若想放仓库根则必须在 `app/build.gradle.kts` 的 `android { lint { lintConfig = file("...") } }` 里显式指路。

---

## 5. 门禁该插在哪一层、用什么命令

### 5.1 现有三层结构（`tools/ci-gate.sh`，共 207 行）

| 层 | 入口 | 干什么 | 依赖 Android SDK | CI 里的 job / 超时 |
|---|---|---|---|---|
| `docs` | `gate_docs`（`:54-90`） | 纯静态：调试残留、README 手表话术、明文流量开关、资产命名、**未跟踪测试**（DFW-122，`:76-87`） | 否（不需要 JDK，`:39-42`） | `静态门禁` `timeout-minutes: 10`（`ci-gates.yml:38-40`） |
| `unit` | `gate_unit`（`:93-138`） | **4 次** gradle 调用：`:app:testEmptyDebugUnitTest`（`:96`）、`:rikkahub-app:testDebugUnitTest`（`:100`）、`:common:testDebugUnitTest`（`:103`）、9 个 vendor 模块串行 `--max-workers=1`（`:126-135`） | 是 | `JVM 测试门禁` `timeout-minutes: 90`（`ci-gates.yml:50-52`） |
| `apk` | `gate_apk`（`:141-199`） | `:app:assembleEmptyRelease`（`:143`）+ aapt 断言 ABI / 广告权限 / 清单开关 | 是 | `release 产物门禁` `timeout-minutes: 120`（`ci-gates.yml:88-90`） |
| 分发 | `case`（`:201-207`） | `docs \| unit \| apk \| all` | — | 三个 job 各自 `tools/ci-gate.sh <层>`（`:46 / :72 / :114`） |

`unit` 层开头有一句很关键（`:22-33`）：**脚本自己把 `ulimit -u` 顶到硬上限**，因为 Robolectric 749 条用例要几千个线程、软上限 2666 会一次红 184 条。

### 5.2 建议：**新增独立的 `lint` 层**，不要并进 `unit`

```bash
# ---------------------------------------------------------------- Lint
gate_lint() {
  step "Lint 门禁：emptyRelease 变体（0 error）"
  # 任务名含 Release 不含 Debug（v1.18.0 ABI 翻转事故红线）
  ./gradlew "${GRADLE_ARGS[@]}" :app:lintEmptyRelease
  echo "✅ Lint 门禁通过"
}
```

并把它加进 `case` 分发（`:201-207`）：`lint) gate_lint ;;`，同时 `all) gate_docs; gate_unit; gate_lint; gate_apk ;;`。

**为什么独立成层，而不是塞进 `unit`：**

| 理由 | 说明 |
|---|---|
| 日常迭代频率 | `unit` 是本地最常跑的一层（今天这个窗口就跑了十几次）。lint 要**编译 release 变体**，会明显拉长它 |
| 失败语义 | `unit` 红 = "测试挂了"；lint 红 = "静态检查挂了"。混在一起后，"红的是什么"要靠翻日志才知道 |
| 并行度 | 独立层 = CI 可以开**独立 job**；但要注意现有 job 是 `needs:` 串联的，必须挂 `needs: docs` 才能与 `unit` 并发（详见 §10） |
| `lint.xml` 归因 | 单独一层时，"lint 红了 → 去看 lint 报告"是唯一路径，不会和测试报告混 |

**CI 侧（已实现）**：在 `.github/workflows/ci-gates.yml` 新增了 `lint` job，`timeout-minutes: 45`，`needs: docs`。
**注意不要写成"三个 job 并行"** —— 现有 job 是 `docs → unit → apk` 用 `needs:` **串起来**的。
`lint` 挂 `needs: docs` 才能与 `unit` 并发；挂 `needs: unit` 会变成串行叠加。**实测耗时给出的修正见 §6.5。**

### 5.3 命令为什么是 `:app:lintEmptyRelease`

- 项目 release 变体叫 **`emptyRelease`**（`tools/ci-gate.sh:143` 用的就是 `:app:assembleEmptyRelease`，产物在 `app/build/outputs/apk/empty/release/*.apk`，`:150`）；
- 现有报告文件名就叫 `lint-results-emptyRelease.*`，即这个任务**已经在被跑**，只是结果没人接；
- 任务名里**只有 `Release`、没有 `Debug`**，满足 `AGENTS.md` 二的那条红线。**绝对不要**为了快而改成 `lintEmptyDebug`。

### 5.4 `abortOnError`（**已实测确认：默认 true，本次实锤**）

实测（2026-10-02 深夜）：**有 error 时任务真的会失败**。
反向探针那一次（摘掉 `@RequiresApi(30)`）实测输出：

```
> Task :app:lintEmptyRelease
BUILD FAILED in 28s          ← 有 5 条 error
```

而清零后同一条命令是 `BUILD SUCCESSFUL`。所以 AGP 的 `abortOnError=true` **确实是默认且生效的**，退出码本身就携带了"0 error"语义。

**但我仍然在 `gate_lint` 里补了一道兜底断言**，理由是防"静默失效"：哪天有人把 `abortOnError` 改成 `false`（或用了 `lintOptions` 的等价开关），任务会**永远绿**，而门禁看起来还在跑。兜底断言直接数报告里的 error：

```bash
errors="$(grep -c 'severity="Error"' "$report" || true)"
[[ "$errors" == "0" ]] || fail "lint 报告里还有 ${errors} 条 error"
```

**实测过这条断言是敏感的**：对着探针期间那份报告，它数出 `5`；对着清零后的报告，它数出 `0`。
（注意 `grep -c` 在计数为 0 时**退出码是 1**，所以必须带 `|| true`，否则 `set -e` 会把脚本自己杀掉 —— 这正是"只在错误分支才执行的代码"那一类坑，`lessons.md` 十二-3 记过。）

---

## 6. 耗时风险评估

### 6.1 已有实测数据：**原来没有，本轮补上了**

阶段一查了三处，**都没有 lint 的耗时记录**（`current-state.md` 无 lint 条目；`lessons.md` 第九/十二节只提到看门狗 5 秒与 `BUILD SUCCESSFUL in 1s` 假象；`ci-gates.yml` 原本没有 lint job）。

**阶段二实测（2026-10-02 深夜）—— 同一条命令 `./gradlew --offline -p <repo> :app:lintEmptyRelease`，`/usr/bin/time -p` 记 `real`：**

| 场景 | 前置动作 | `real` | gradle 自报 | 说明 |
|---|---|---|---|---|
| **冷跑** | `rm -rf app/build/reports/lint-results-emptyRelease.* app/build/intermediates/lint*` | **434.93s ≈ 7 分 15 秒** | `BUILD SUCCESSFUL in 7m 14s`（16 executed） | **CI 每次都是这个量级**（CI 无 gradle 缓存） |
| **热跑（无改动）** | 紧跟一次，什么都没改 | **2.06s** | `BUILD SUCCESSFUL in 1s`（1 executed, 340 up-to-date） | 全 UP-TO-DATE，无参考价值 |
| **热跑（改一行之后）** | 摘掉 `@RequiresApi(30)` 后重跑（见 §10.3 反向探针） | **29.00s** | `BUILD FAILED in 28s`（5 executed, 336 up-to-date） | **这才是本地日常的真实代价** |
| `bash tools/ci-gate.sh lint`（缓存命中） | 还原后 | **3.55s** | `BUILD SUCCESSFUL in 3s` | 走 build cache，`✅ Lint 门禁通过（0 error）` |

**结论修正（重要）**：我在阶段一推测"几十秒~数分钟"，**实测冷跑 7 分 15 秒，比我猜的慢**。
本地日常改一行只花 **~29 秒**（可接受），但 **CI 每 PR 要付 ~7 分钟**。

### 6.2 可以在阶段二直接量（建议这么做）

```bash
# 冷跑（清掉 lint 产物，反映 CI 首次执行）
rm -rf app/build/reports/lint-results-emptyRelease.* app/build/intermediates/lint*
/usr/bin/time -p ./gradlew :app:lintEmptyRelease        # 记 real

# 热跑（daemon 已在、release 已编译，反映"改一行代码后"的日常代价）
/usr/bin/time -p ./gradlew :app:lintEmptyRelease
```

### 6.3 阶段一的定性推测 vs 实测结论

| 阶段一的判断 | 置信度 | **实测结论** |
|---|---|---|
| 冷跑会明显慢于热跑 | 高 | ✅ 成立，且差距极大：**434.93s vs 2.06s** |
| **不应放进 `unit` 层** | 高 | ✅ 成立。冷跑 7m15s 是 `unit` 全量（3m36s，见 §10.4）的**两倍**，塞进去会把最常跑的一层拖垮 |
| 独立 CI job 关键路径不增加 | 中 | ⚠️ **部分不成立**，见 §6.5 |
| 单次耗时落在"几十秒~数分钟" | 低（经验值） | ❌ **猜错了**：冷跑 7 分 15 秒，超出该区间。热跑（改一行）29s 落在区间内 |

### 6.4 冷跑的 7 分钟花在哪（**有任务清单为证**）

冷跑日志里被执行的任务含 **12 个 lint 分析任务**：

```
:app:lintAnalyzeEmptyRelease  +  :rikkahub-app:lintAnalyzeRelease
:web:  :common:  :document:  :highlight:  :material3:  :oauth:
:workspace:  :ai:  :speech:  :search:      ← 11 个 vendor 模块
```

即 **`:app:lintEmptyRelease` 会把全部依赖模块一起 lint**（AGP 的 `checkDependencies` 行为），
7 分钟里大部分大概率花在这 11 个 vendor 模块上（它们各自最多几百条 warning）。
**这是推测，不是实测** —— 要证实需要改 `app/build.gradle.kts` 加 `lint { checkDependencies = false }` 再量一次，
而 `app/build.gradle.kts` **不在本卡批准的写入范围内**，所以我没有动它。

### 6.5 对 CI 关键路径的真实影响（**修正我阶段一的乐观结论**）

现有链路：`docs → {unit, lint} → apk`（`apk` 仍只 `needs: unit`）。

| 量 | 数值（实测） |
|---|---|
| `unit` 全量 | **3m 36s** |
| `lint` 冷跑 | **7m 15s** |
| 结论 | `{unit, lint}` 这一段**由 lint 决定，约 7m15s**；比只有 unit 时多约 **3m39s** |

**"关键路径不增加"是不准确的** —— `apk` 的**开始时间**确实不受影响（它只等 `unit`），
但**整个 workflow 的完成时间**会被 lint 拖长约 3 分 39 秒。
`timeout-minutes: 45` 对 7 分钟绰绰有余，这一项没问题。

**给 Lead 的取舍建议 → Lead 已决策（2026-10-02）：采用 A，B 与 C 均否。**

| 方案 | 代价 | 收益 | **决策** |
|---|---|---|---|
| **A. 维持现状（PR + push 都跑）** | 每 PR 多 ~3m39s CI 墙钟 | PR 阶段就能拦住权限/API 版本这类"运行时才炸"的问题 | ✅ **采纳（已实现）** |
| B. 加 `if:` 限制成 push-only（同 `apk` 的写法） | PR 不跑 lint，问题可能漏到 main | PR 保持 ~3m36s，人少时省额度 | ❌ **否掉**：等于回到"发现得晚"，而这套 CI 存在的**唯一理由**就是早发现（本仓库 CI 无 keystore、发版在本地做，见 `ci-gates.yml:16-18`，所以 CI 的价值就是"拦住能拦的"） |
| C. `lint { checkDependencies = false }` | 未实测；且**vendor 区 lint 从此永不运行** | 若 7 分钟主要来自 vendor 模块，可能砍掉大半 | ❌ **暂不采纳**：为省 3m39s 换一个**永久的覆盖缺口**，不划算 |

**7m15s 冷跑判定为可接受**：GitHub Actions 免费额度足够；且它**不阻塞本地发版流程**
（本地有缓存：`ci-gate.sh lint` 3.55s、改一行 29s）。`timeout-minutes: 45` 余量充足。

#### 6.5.1 C 方案：已评估、暂不采纳 —— **若将来 CI 时间真的紧张，从这里接着查**

留给未来的人（省一轮排查）。根因线索（**推测，未实测**，但方向明确）：

**冷跑日志里被执行的是 12 个 lint 分析任务，不是 1 个**：

```
:app:lintAnalyzeEmptyRelease  +  :rikkahub-app:lintAnalyzeRelease
:web:  :common:  :document:  :highlight:  :material3:  :oauth:
:workspace:  :ai:  :speech:  :search:      ← 11 个 vendor 模块
```

即 `:app:lintEmptyRelease` 会把**全部依赖模块**一起 lint（AGP 的依赖 lint 行为），
所以那 7 分钟里**大部分大概率不在宿主代码上**。

**将来若要重启 C 方案，按这个顺序做**（每步都要实测，不要只看推断）：
1. 先 `cp app/build/reports/lint-results-emptyRelease.*` 到仓库外**留基线**（**别犯"先删后量"的错**，已固化进 `docs/agents/lessons.md` 第九节第 4 条，见本文 §11）；
2. 在 `app/build.gradle.kts` 的 `android {}` 里加 `lint { checkDependencies = false }`；
3. `rm -rf` lint 产物后 `/usr/bin/time -p` 冷跑一次，与本文 §6.1 的 **434.93s** 对比；
4. **同时**确认宿主模块的 error 数仍是 0、且 issue 总数没有异常下降（下降过多说明把宿主的问题也吞掉了）；
5. 若收益 < 1 分钟，直接放弃；若收益显著，**必须**在 `ci-gates.yml` 里单独补一个 vendor lint job，
   否则就是用"vendor 永久不 lint"换时间 —— 这正是 Lead 否掉它的理由。

### 6.6 退路（若嫌慢，按优先级）

1. **本地按需跑**：`gate_lint` 保留，本地日常只用 `unit`（改一行热跑 29s，其实可以接受）。在 `AGENTS.md` 四的表格补一行"改了清单/权限/API 调用/新增 Activity 时先跑 `tools/ci-gate.sh lint`"。
2. **量 `lint { checkDependencies = false }`**（需批准改 `app/build.gradle.kts`）：见 §6.4。
3. **`lint.xml` 只对 warning 做策略**：warning 有 118 条（见 §10.4），error 已是 0，不影响门禁。
4. **不要**为了快而降级到 Debug 变体（`AGENTS.md` 二的红线）。

---

## 7. 建议执行顺序（阶段二）—— **已全部执行，结果见 §10.3**

> 下表是阶段一拟的计划；实际执行结果（含反向探针与全量回归的原始数据）在 **§10.3 / §10.4**。
> 保留此表是为了对照"计划的判据"和"实际拿到的证据"。

| 步 | 动作 | 产出 / 判据 |
|---|---|---|
| 0 | 另存仓库外备份（`lessons.md` 第十一节铁律） | 备份路径 |
| 1 | **重跑 `:app:lintEmptyRelease` 拿新基线** | 新报告 + 实测 `real` 秒数；确认是否仍为 14 条、`FeedbackPage` 那条是否确实消失 |
| 2 | 应用 **A-1**（`ToolHost` 权限本地守卫） | 该 error 消失，且麦克风工具在"拒绝权限"下提示正确 |
| 3 | 应用 **A-2**（`dfwxImeInsets` 加 `@RequiresApi(30)`） | 5 条 NewApi 消失；确认调用点 `:979` 仍在 `SDK_INT>=30` 内、未新增报错 |
| 4 | 应用 **A-3 / A-4**（两处 blur 方法内补 `SDK_INT<31` 守卫） | 3 条 NewApi 消失；**行为回归验证**：公告弹窗在 API 26 模拟/低版本路径不崩（Robolectric 现有公告弹窗用例应保持全绿） |
| 5 | 应用 **B-1**（`SupportActivity` 抑制 + 理由注释） | 1 条消失；写法与 `FeedbackPage:282`/`LanzouWebActivity:93` 逐字一致 |
| 6 | 应用 **B-2**（`dispatchKeyEvent` 抑制 + 理由注释） | 3 条消失；**回归**：`TopBannerAndUpdateJvmTest` 等返回键/音量键相关用例保持绿 |
| 7 | 重跑 lint，确认 **0 error** | 报告里 `0 errors, N warnings`；warning 逐类给出解释清单 |
| 8 | 建 `app/lint.xml`（**仅当**确有必要时） | 每条 `<issue>` 上方必须有 `<!-- 理由 -->` XML 注释；无裸 ignore |
| 9 | 改 `tools/ci-gate.sh`：加 `gate_lint` + `lint` 分发 + `all` | `bash tools/ci-gate.sh lint` 真跑一次，记录实测耗时 |
| 10 | 改 `.github/workflows/ci-gates.yml`：加第 4 个并行 job | YAML 合法 + 本地 `ci-gate.sh lint` 与 CI 同命令 |
| 11 | 风险台账追加 **R-22**（预测性返回手势） | `docs/plan/risk-register.md` 表格新增一行 |
| 12 | 全量回归：`bash tools/run-tests.sh` + `tools/ci-gate.sh docs` | 基线不可倒退（`unit` 层今日基线见下） |

**回归基线提醒**：`unit` 层当前基线是 `:app` **819 条 / 0 失败**（HEAD `4e02f6d`；DFW-124 连跑三轮全绿，见 `c07cae6`）。
**跑测试一律用 `bash tools/run-tests.sh`，不要直接敲 `./gradlew`** —— 它会把 `ulimit -u` 顶到硬上限并清残留 worker。
2026-10-02 就是因为绕过它，同一份代码跑出「2 红 / 3 红 / 0 红」三种结果（依据：`AGENTS.md` 四的表格、`lessons.md` 十二、`c07cae6` 提交信息）。

---

## 8. 边界声明：哪些是我核实过的、哪些是推测

### 我**核实过**的（有命令输出或代码位置为证）

| 结论 | 证据 |
|---|---|
| error 恰好 14 条，warning 116 条 | `lint-results-emptyRelease.txt:716` + XML `severity="Error"` 计数 = 14 |
| 报告生成于 10:32，`FeedbackPage` 在 14:51 被改并已带抑制 | 文件 mtime + `FeedbackPage.java:282` + `git status` 干净 |
| 14 条的每一处代码现场 | 逐条读源码（行号见 §2 表） |
| `dfwxImeInsets` 唯一调用点在 `SDK_INT>=30` 内 | `MainActivity.java:954 / 971 / 979` |
| blur 安全性来自 `GlassSurface` 两个方法的守卫 | `GlassSurface.java:92-94`、`:103`；`MainActivity.java:1844`、`:2023`、`:2060` |
| `MainActivity` 继承 `androidx.activity.ComponentActivity` | `MainActivity.java:30` |
| 仓库里**没有** `lint.xml`、没有任何 lint 配置、没有 build-logic | §4 的三条命令 |
| `androidx.annotation` 可用（`@RequiresApi` 能用） | `app/build.gradle.kts:214` `compileOnly("androidx.annotation:annotation:1.3.0")` |
| 项目此前**没用过** `@RequiresApi`/`@TargetApi` | 全仓 grep 为空 |
| `ci-gate.sh` 三层结构与分发 | 通读 `tools/ci-gate.sh`（改造前 207 行） |
| ~~CI 三个 job 并行~~ → **是 `needs:` 串行的** | `.github/workflows/ci-gates.yml`：`unit needs: docs`、`apk needs: unit`。**这一条我在阶段一写错了，阶段二自己纠正**（见 §6.5） |
| 项目已有两处 `@SuppressLint("GestureBackNavigation")` 先例 | `FeedbackPage.java:282`、`LanzouWebActivity.java:93` |
| 无 lint 耗时记录 | 三处检索均为空（§6.1） |

**阶段二新增的核实项（全部有命令输出）：**

| 结论 | 证据 |
|---|---|
| 修复后 **0 error / 118 warning** | 新报告 `lint-results-emptyRelease.txt:632`；XML `severity="Error"` = 0；`BUILD SUCCESSFUL in 7m 14s` |
| 四个 issue 类型全部清零 | 脚本统计 XML：`MissingPermission`/`GestureBackNavigation`/`NewApi`/`RestrictedApi` 各 0 条 |
| **lint 真的在管我的改动（不是假绿）** | 反向探针：摘掉 `@RequiresApi(30)` → `BUILD FAILED in 28s`，XML error 计数 **5**，且 5 条全是 `NewApi @ MainActivity.java:975`，**列号 110/153/194/224/245 与原始报告逐字一致** |
| `abortOnError` 默认 true 且生效 | 有 error → `BUILD FAILED`；清零 → `BUILD SUCCESSFUL`（§5.4） |
| 兜底断言 `grep -c 'severity="Error"'` 有效 | 探针期间数出 5，清零后数出 0 |
| 实测耗时 | 冷跑 434.93s / 无改动热跑 2.06s / 改一行热跑 29.00s（§6.1） |
| `ci-gate.sh docs` 通过 | `✅ 文档与卫生门禁通过` |
| `ci-gate.sh lint` 通过（含兜底断言） | `✅ Lint 门禁通过（0 error）`，real 3.55s |
| 我改的行**没有引入任何 warning** | 落在我改动的行号区间上的 warning = **0 条**；我新建的 `DfLog.java` / `MainThreadStallWatchdog.java` 也各 **0 条** |
| 全量回归 842 条 / 0 失败 | `bash tools/run-tests.sh --rerun` → `BUILD SUCCESSFUL in 3m 36s`（§10.4） |

### 我**推测**的（阶段二已全部验完，结果如下）

| 阶段一的推测 | 结果 |
|---|---|
| `:app:lintEmptyRelease` 会因 `abortOnError=true` 直接失败 | ✅ **证实**（有 error 时 `BUILD FAILED in 28s`） |
| lint 冷跑"几十秒~数分钟" | ❌ **证伪**：冷跑 **434.93s ≈ 7m15s**，超出该区间 |
| 重跑后仍会报 13 条 | ❌ **未观测到 13**：改完才跑基线，直接得到 0。13 只是静态核对的推断值，非实测 |
| warning 116 条的构成 | ✅ 已分类（前几名见 §10.4；且仍**没有**逐条给解释 —— 118 条 warning 不在本卡验收范围内） |

**到目前为止，本文档里已经不存在"未验证就当成结论"的项**；唯一仍标为推测的是 §6.4 的"7 分钟主要花在 vendor 模块上"。

---

## 9. 一句话结论

**14 条是真的，其中 1 条已在 10:32 之后被另一个窗口修掉；剩下 13 条里 9 条是"护栏没落到本地"的真问题（改代码即清零，不需要抑制）、4 条是项目已有先例的误报（带理由行内抑制）。全部处置后实测 `0 error / 118 warning`，lint 已作为独立的 `lint` 层接进 `ci-gate.sh`，并在 CI 里与 `unit` 并发跑。**

**但阶段一的"关键路径不变"要打个折**：实测冷跑 **7m15s**（是 `unit` 全量 3m36s 的两倍），
所以它虽然不推迟 `apk` 的**开始**，却会把整个 workflow 拖长约 **3m39s**。取舍方案见 §6.5。

---

## 10. 阶段二执行记录（代码已改 + **验证已完成**）

> 阶段二的范围经 Lead 批准，写入范围被限定为：
> `ToolHost.java` / `MainActivity.java`（只碰指定几处）/ `SupportActivity.java` /
> `tools/ci-gate.sh` / `.github/workflows/ci-gates.yml` / `docs/plan/risk-register.md` / 本文档。
> ❌ 明确不许碰：`RemoteConfigClient.java`、`App.java`、`tools/run-tests.sh`、`app/src/test/**`。
> 本节在 **未跑 gradle** 的前提下记录"改了什么"，**所有验证结论留空，等 lint 重跑后回填**。

### 10.1 已改动的文件与位置

| # | 文件 | 位置（改后行号） | 改动 | 对应 §3 条目 |
|---|---|---|---|---|
| 1 | `ToolHost.java` | `:1467` 起（原 `try{` 之后、`:1469` AudioRecord 之前） | 新增本地 `checkSelfPermission(RECORD_AUDIO)` 守卫 + 未授权提示并 return | A-1 |
| 2 | `MainActivity.java` | `:976` | `dfwxImeInsets` 前加 `@androidx.annotation.RequiresApi(30)` + 理由 javadoc | A-2 |
| 3 | `MainActivity.java` | `:2038` 方法内（`if(!blur)return;` 之后） | 加 `if(Build.VERSION.SDK_INT<31)return;` + 理由注释 | A-3 |
| 4 | `MainActivity.java` | `:2091` 方法内（`if(listener==null)return;` 之后） | 加 `if(Build.VERSION.SDK_INT<31)return;` + 理由注释 | A-4 |
| 5 | `MainActivity.java` | `:236` | `dispatchKeyEvent` 前加 `@android.annotation.SuppressLint("RestrictedApi")` + 理由 javadoc | B-2 |
| 6 | `SupportActivity.java` | `:126` 起 | `onBackPressed` 前加 `@android.annotation.SuppressLint("GestureBackNavigation")` + 理由 javadoc | B-1 |
| 7 | `tools/ci-gate.sh` | `gate_unit` 之后新增 `gate_lint()`；`case` 加 `lint` 分发；`all` 加 `gate_lint`；头部用法注释加一行 | 新增 `lint` 层 | §5.2 |
| 8 | `.github/workflows/ci-gates.yml` | 头部注释 + `unit` 与 `apk` 之间新增 `lint` job | 新 job，`needs: docs`（与 `unit` 并发） | §5.2 |
| 9 | `docs/plan/risk-register.md` | 表尾新增 `R-22` + 头部追加一行说明 | 预测性返回手势记账 | §3.3 |

### 10.2 Lead 特别要求核实的一条：**方法签名一个字没动**

改完立刻用 `grep -rn` 对照，**改动前后调用点数量完全一致**：

| 方法 | 改动前 | 改动后 | 签名 |
|---|---|---|---|
| `applyNoticeWindowGlass` | 1 处定义 + 1 处调用 | **1 + 1** | 未动 |
| `unregisterNoticeBlurListener` | 1 处定义 + 2 处调用 | **1 + 2** | 未动 |
| `registerNoticeBlurListener` | 1 处定义 + 1 处调用 | **1 + 1** | 未动 |
| `dfwxImeInsets` | 1 处定义 + 1 处调用 | **1 + 1** | 未动（只加注解） |
| `dispatchKeyEvent` | 1 处定义 + 1 处 `super` 调用 | **1 + 1** | 未动（只加注解） |

（A-3/A-4 的守卫都加在**已有提前返回之后、API 调用之前**，所以低版本行为逐字不变；
A-2 只加注解，方法体一个字没改。）

### 10.3 验证清单 —— **全部跑完，逐条给出原始结果**

| # | 项 | 命令 | 结果 |
|---|---|---|---|
| 1 | 拿新基线 | `rm -rf app/build/reports/lint-results-emptyRelease.* app/build/intermediates/lint*` 后 `/usr/bin/time -p ./gradlew --offline :app:lintEmptyRelease` | **`BUILD SUCCESSFUL in 7m 14s`，real 434.93s**；报告 `0 errors, 118 warnings`。**没观察到 13 这个中间态**（原因见 §1.1 的诚实声明） |
| 2 | 确认清零 | 同上 | `0 errors`；XML `severity="Error"` = **0**；四个 issue 类型各 **0** 条 |
| 3 | 实测耗时 | 见 §6.1 四行表 | 冷跑 **434.93s**；无改动热跑 **2.06s**；**改一行热跑 29.00s**；`ci-gate.sh lint` 缓存命中 **3.55s** |
| 4 | **反向探针** | `cp` 备份 → 摘掉 `MainActivity.java` 的 `@RequiresApi(30)` → 重跑 → 还原 | **`BUILD FAILED in 28s`**；XML error = **5**，5 条全是 `NewApi @ MainActivity.java:975`，**列号 110/153/194/224/245 与原始 10:32 报告逐字一致** → 证明 lint 真在管这次改动。还原后 `shasum -a 256` 与备份**完全一致**（`66836a6b…`） |
| 5 | 门禁自检 | `bash tools/ci-gate.sh docs` | `✅ 文档与卫生门禁通过` |
| 6 | 门禁自检 | `bash tools/ci-gate.sh lint` | `✅ Lint 门禁通过（0 error）`（含我加的兜底断言），real 3.55s |
| 7 | 全量回归 | `bash tools/run-tests.sh --rerun` | `BUILD SUCCESSFUL in 3m 36s`，**842 条 / 99 类 / 0 失败 / 0 错误 / 0 跳过**（详见 §10.4） |
| 8 | 收尾 | `./gradlew --stop` + `pkill -f GradleWorkerMain` | 残留 worker **0**、残留 daemon **0** |

### 10.4 全量回归的完整计数（Lead 要求原样给出）

命令：`bash tools/run-tests.sh --rerun`（**没有直接敲 gradlew**，走的是项目规定的入口）
任务：`:app:testEmptyDebugUnitTest`（`run-tests.sh` 默认只跑宿主；vendor 需 `--vendor`）

| 指标 | 实测值 | Lead 的预期 | 差异 |
|---|---|---|---|
| 总用例数 | **842** | 842 | ✅ 一致 |
| 测试类数 | **99** | 98 | ⚠️ **差 1**（见下） |
| 失败 | **0** | 0 | ✅ |
| 错误 | **0** | — | ✅ |
| 跳过 | **0** | — | ✅ |
| 耗时 | **3m 36s** | — | — |
| `FAILED` 出现次数（日志全文） | **0** | 0 | ✅ |

**关于"99 vs 98"**：我用两种独立方式数了，都是 99 ——
① `app/build/test-results/testEmptyDebugUnitTest/TEST-*.xml` 去重后 **99** 个，且**没有一个是用例数为 0 的空壳**；
② `app/src/test/java/cc/nkbr/lanzouplus/` 下的测试源文件 **99** 个。
两个新类都已计入：`NetworkIsolationJvmTest` = 2 条、`ToolboxAlgorithmsJvmTest` = 19 条。
**所以是 99 个类，不是 98** —— 大概是"原 821/96 + 2 + 19"里的 96 少数了一个。

**warning 构成（118 条，前 8 名）**：`SetTextI18n` 48、`ObsoleteSdkInt` 17、`ClickableViewAccessibility` 13、
`ApplySharedPref` 6、`ExifInterface` 6、`ViewConstructor` 5、`SdCardPath` 3、`DrawAllocation` 3。
**本卡验收只要求 error 归零**，warning 未逐条解释；`SetTextI18n` 一家占 41%，是宿主全面走程序化 View 的必然结果。

**一个我该做得更好的地方**：阶段一删旧报告时（`rm -rf …lint-results-emptyRelease.*`）**没有先把旧报告的 warning 清单存下来**，
所以 `116 → 118` 这 +2 条**无法归因**（可能是别人的改动，也可能是我的）。
我能给的替代证据是：**落在我改动的行号区间上的 warning = 0 条，我新建的两个文件也各 0 条**（脚本核对过）。
下次这类"先删后量"的操作，应当先把基线存到仓库外。

---

## 11. 本轮固化进长期记忆的教训

按 Lead 指示，只挑**通用**的那一条固化（不是只对 lint 成立的），并且**并入已有章节，不开新章**：

| 教训 | 固化位置 | 为什么值得固化 |
|---|---|---|
| **「先删后量」会把基线一起删掉，导致差异无法归因 —— 要删先存到仓库外** | `docs/agents/lessons.md` **第九节第 4 条**（构建/测试环境坑） | 它对**任何一次"重跑对比"**都成立（测试报告、构建产物、`--rerun` 全量前后对比），不只 lint。本轮已有"`+2` 条 warning 无法归因"的实例证明代价 |

**没有固化进 lessons 的（只留在本文档）**：
- lint 冷跑 7m15s / 改一行 29s 的具体数字 —— 属于 lint 专项，查本文 §6.1；
- A/B/C 方案的取舍 —— 属于本题决策，查本文 §6.5；
- `@RequiresApi` 优于 `@SuppressLint("NewApi")` 的理由 —— 属于 A 类修法的具体证据，查 §3.1 A-2。

**为什么这么分**：`lessons.md` 第九节开头自己写着"构建/测试一失败先读这一节"，
往里塞只有 lint 才用得上的细节会稀释它的检索价值。
且该节已有"两个九"的历史遗留（`## 九` 与 `## 八、2026-10-02…` 实为同层重复编号），
**不要再制造第三个**。
