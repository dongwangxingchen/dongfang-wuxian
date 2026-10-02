# 2026-10-02 测试可信度审计（假绿 / 脆弱锚点 / 覆盖缺口）

> **范围**：`app/src/test/java/cc/nkbr/lanzouplus/`（90 个测试类）+
> `tools/ci-gate.sh` / `.github/workflows/ci-gates.yml` 的模块覆盖 + 12 个 gradle 模块的
> `build/test-results/**` 产物。
> **方法**：只读。逐文件解析 + 脚本化提取全部源码锚点（555 条），把每条锚点回源到
> `app/src/main/**` / `rikkahub/**` 的实际行，判定它落在**代码**还是**注释**；
> 对每个断言做"操作数是否全是字面量"的判定；用 `git ls-files` / `git log --since` 核交付面与时效。
> **未做**：**没跑 gradle**（lead 在跑，避免抢 CPU/线程）、没改任何测试或产品代码、
> 没 commit、没 push。唯一写入的文件就是本报告。
> **⚠️ 测试产物取证时点**：本报告开头的完整统计抓取于 **2026-10-02 10:0x**（当时 lead 的
> gradle 尚未开始重写 `app/build/test-results/`）；10:17 之后该目录被清空重建。
> **10:2x gradle 跑完后已取到终值：90 个类 / 761 例 / 0 失败 / 0 错误 / 0 跳过**（见 §10.1）。
>
> **⚠️ 审计期间工作树被别的 agent 改动过，改动随后被提交为 `93858db 复查① 自查发现并修掉两处`**。
> 本报告的行号以 **HEAD = `93858db`** 为准（收尾时工作树 == HEAD，已逐条复核）：
> 1. `app/src/main/java/cc/nkbr/lanzouplus/MainActivity.java` **+20 行**（在 `crossFadeHomeSection`
>    里加了 DFW-95 的 600ms 幂等兜底，插入点在原 `:1123`）。因此 **`:1123` 之后的 MainActivity 行号整体 +20**：
>    `// 后台不可达` 由 `:1217` 变为 **`:1237`**，`loading("正在刷新目录")` 注释由 `:2255` 变为 **`:2275`**；
>    `:654`（`solidShape`）、`:589`、`:983`、`:981`、`:990`、`:416`、`:2474` 已按新行号核对无误。
> 2. `app/src/test/java/cc/nkbr/lanzouplus/SearchPageBottomAndBackJvmTest.kt` **+51 行**
>    （补了 `theCrossFadeAlwaysSettlesEvenWhenInterrupted` 的 `animate().cancel()` 与一段诚实登记，见 §9 第 3 行）。
> 3. **同一提交把 `FeedbackPageSmokeJvmTest.kt` 纳入 git** —— 本报告 §7 的那条 P1 因此**已被修复**（详见 §7）。
>
> **这两处代码/测试改动都不是本报告做的**，本报告没有修改任何测试或产品代码。
>
> 收尾时 `git status --porcelain`：
> ```
> ?? docs/audit/20261002-test-integrity.md     ← 本报告（唯一由我新增的文件）
> ?? docs/handover/
> ```
> `git ls-files --others --exclude-standard -- 'app/src/test/*' 'rikkahub/*/src/test/*'` 返回空
> —— **已无 untracked 的测试文件**。

---

## 0. 结论速览

| # | 等级 | 结论 | 位置 |
|---|---|---|---|
| 1 | **P0** | **8 条**"反向探针"是**字面量自比**，编译期就恒真，证明不了守卫有鉴别力（分布在 6 个测试类） | `FeedbackEntryAndSkinJvmTest.kt:175,176`、`UpdateVerificationPolicyJvmTest.kt:132,137`、`SearchPageBottomAndBackJvmTest.kt:162,164`、`VersionNumberSinglePlaceJvmTest.kt:179`、`DownloadOutcomeNoticeJvmTest.kt:129` |
| 2 | **P0** | 一条**全路径恒过**的测试：`try{...}catch(RuntimeException){}` 无任何断言，抛与不抛都绿 | `NoticeCenterJvmTest.kt:192-206` |
| 3 | **P0** | 一批 `assertTrue(source.contains("foo()"))` 只能被**方法定义**满足，删掉调用照样绿 | `NavBallJvmTest.kt` 6 处等 |
| 4 | **P1** | 4 条 `assertTrue` 断言的对象是**注释**，不是代码 | `TopBannerAndUpdateJvmTest.kt:98` 等 |
| 5 | **P1** | 一条"跨文件一致性"测试**从不读另一个文件**，且它引用的行号**已经失效** | `SupportPageFeedbackJvmTest.kt:124-134` |
| 6 | **P1** | 9 个模块 **336 条测试**不在 `ci-gate.sh unit` 门禁内 | `tools/ci-gate.sh:77-90` |
| 7 | **P1 → 审计期间已修复** | 新增的反馈页冒烟测试曾**未进 git**（untracked）→ CI checkout 后不存在；`93858db` 已纳入 | `FeedbackPageSmokeJvmTest.kt`（§7） |
| 8 | **P2** | 4 条截图测试无断言，只能证明"没抛异常"，不能证明画面正确 | `HomeShotsJvmTest.kt:60,80,97`、`ToolsShotsJvmTest.kt:49` |
| 9 | **P2** | `assertTrue(true)` ×3、无断言"不崩"测试 ×1 | `MemoryLifecycleJvmTest.kt:132` 等 |

---

## 1. P0-① 反向探针集体失效：8 条断言是"字面量 contains 字面量"

### 问题

项目已经形成"守卫必须配反向探针"的规范（`RenderFolderNullSafetyJvmTest` 是范本，`theGuardActuallyDetectsAnUnguardedCall` 真的喂坏代码给守卫函数）。
但至少 6 个测试类的反向探针写成了**两个字符串字面量互相 contains** ——
`"pointer-events: none".contains("pointer-events")` 这种表达式在**编译期**就能求值为 `true`，
Kotlin 只是没把它优化掉；它**完全没有调用被测的守卫逻辑**。

后果：这类探针永远不会红，但它让"反向探针"这条检查项**在评审时显示为已满足**。
一旦守卫真的退化成恒真，没有任何机制会发现 —— 这正是任务里说的"反向探针注入了坏代码，测试没红"的同构问题。

### 清单（已逐条回源确认两个操作数都是字面量）

| 位置 | 断言原文 | 为什么恒真 |
|---|---|---|
| `app/src/test/java/cc/nkbr/lanzouplus/FeedbackEntryAndSkinJvmTest.kt:175` | `assertTrue("坏代码（CSS 写法）必须被识别", "pointer-events: none".contains("pointer-events"))` | 左右都是字面量 |
| `.../FeedbackEntryAndSkinJvmTest.kt:176` | `assertTrue("坏代码（JS 写法）必须被识别", "el.style.pointerEvents='none'".contains("pointerEvents"))` | 左右都是字面量 |
| `.../UpdateVerificationPolicyJvmTest.kt:132` | `badOld.contains("version.equals(archive.versionName)")` | `badOld` 是 L129 的字面量，且被查的子串就写在它里面 |
| `.../UpdateVerificationPolicyJvmTest.kt:137` | `assertFalse(badNoSafety.contains("signatureDigests") && badNoSafety.contains("archiveCode<=currentCode"))` | `badNoSafety` 是 L134 的字面量，里面本来就没有这两个词 |
| `.../SearchPageBottomAndBackJvmTest.kt:162` | `assertTrue("坏代码（又扣 64dp）必须能被识别", badGap.contains("-dp(64)"))` | `badGap` 是 L161 的字面量，`-dp(64)` 是它的一部分 |
| `.../SearchPageBottomAndBackJvmTest.kt:164` | `assertFalse("坏代码（硬切）不该被当成走了交叉淡化", badBack.contains("crossFadeHomeSection"))` | 同上 |
| `.../VersionNumberSinglePlaceJvmTest.kt:179` | `bad.contains("BuildConfig.VERSION_NAME") && bad.contains("text(")` | `bad` 是 L176 的字面量 |
| `.../DownloadOutcomeNoticeJvmTest.kt:129` | `assertFalse("坏代码（恢复时没同步 uiState）不该被判成合格", "entry.savedState=entry.state;entry.savedPercent=1;".contains("entry.uiState=entry.state"))` | 左右都是字面量 |

### 怎么改

反向探针必须**把坏代码喂给真正的守卫函数**，而不是复述"坏代码长什么样"。
参照 `RenderFolderNullSafetyJvmTest.kt:96-107` 的正确写法（把 `bad` 传进 `unguardedCalls(bad)`）。

以 `UpdateVerificationPolicyJvmTest.kt:128-138` 为例，把"检查"抽成可调用函数：

```kotlin
// 生产侧：把散落的 verify.contains(...) 抽成谓词
private fun hasVersionNameMatchCheck(src: String) = src.contains("version.equals(archive.versionName)")
private fun hasSignatureCheck(src: String)     = src.contains("signatureDigests(current).equals(signatureDigests(archive))")
private fun hasVersionCodeCheck(src: String)   = src.contains("archiveCode<=currentCode")

// 探针侧：喂真的坏代码字符串
assertTrue(hasVersionNameMatchCheck("if(!version.equals(archive.versionName))throw ..."))
assertFalse(hasSignatureCheck("if(archive==null||!getPackageName().equals(archive.packageName))throw ..."))
```

其余 5 处同理。**验收方式**：把生产代码里的那行改坏，探针必须变红。

---

## 2. P0-② 一条恒过的测试：`NoticeCenterJvmTest.storeFailure_doesNotCrash_andDegradesToUnread`

**位置**：`app/src/test/java/cc/nkbr/lanzouplus/NoticeCenterJvmTest.kt:192-206`

```kotlin
@Test
fun storeFailure_doesNotCrash_andDegradesToUnread() {
    val broken = object : NoticeCenter.Store {
        override fun readIds(): MutableSet<String> = throw RuntimeException("disk full")
        override fun setReadIds(ids: MutableSet<String>) { throw RuntimeException("disk full") }
    }
    val c = NoticeCenter(broken)
    val s = snapshot(notice("a"))
    try {
        c.unreadCount(s)
    } catch (expected: RuntimeException) {
        // 生产实现 forContext 内部已 try/catch 兜底，这里是"存储被换成坏的"时的兜底预期
    }
}
```

**为什么是问题**：
- 方法体里**一条断言都没有**；
- `try/catch` 把两种结果都变成了通过：抛 `RuntimeException` → 被吞；不抛 → 什么都不发生；
- 测试名承诺的第二半 **"degradesToUnread"（降级为未读）根本没被验证** —— 这个契约目前**零覆盖**。
- 这是比 `assertTrue(true)` 更危险的一种假绿：`assertTrue(true)` 至少一眼看得出来，
  而这条看起来在测"存储坏掉时的兜底行为"。

**怎么改**：断言要落在**可观察结果**上，而不是"有没有抛"：

```kotlin
val c = NoticeCenter(broken)
val s = snapshot(notice("a"))
// 契约：读失败时必须按"未读"处理（红点不消），而不是当成已读藏起来
assertEquals("存储坏掉时宁可红点不消，也不能当成已读", 1, c.unreadCount(s))
assertTrue("角标必须仍然可见", NoticeCenter.badgeVisible(c.unreadCount(s)))
```
若 `NoticeCenter(broken)` 直接抛异常（说明生产实现没有兜底），那**测试应当红**——
因为"存储坏掉不崩"正是这条测试要守的东西。真机现场是磁盘满/权限被撤，抛出去就是崩溃。

---

## 3. P0-③ 只断言"方法存在"：锚点被**方法定义**满足

### 问题

`source.contains("foo()")` 在整个源文件上搜索时，**方法定义行本身**就满足它。
于是"删掉所有调用点、只留一个空方法"这种最典型的回退，测试不会红。

`NavBallJvmTest` 自己的类注释（`NavBallJvmTest.kt:36-43`）**已经写明了这个坑**：

> 第一版用"从方法名起截 500 字符"来取 body，结果窗口越界到了紧随其后的另一个方法定义里，
> 于是 `body.contains("forceHidePills()")` 因为看到隔壁的方法签名而恒真——把调用删掉测试照样绿（假绿）。

为此作者写了 `methodBody()`（`:45`）。但**12 个测试方法里只有 2 个用了它**（`:101`、`:118`），
其余 10 个仍在整文件上 `source.contains(...)`。修法只落地了 1/6。

### 清单（已回源确认锚点只被定义行满足）

| 测试位置 | 断言 | 锚点实际命中的行 |
|---|---|---|
| `NavBallJvmTest.kt:133` | `assertTrue("必须有强制清理胶囊的方法", source.contains("forceHidePills()"))` | `NavBall.java:234` `private void forceHidePills() {`（**定义**）。删掉 `NavBall.java:220`/`:228` 两处调用，测试仍绿 |
| `NavBallJvmTest.kt:89` | `assertTrue("松手必须贴边", source.contains("snapToEdge()"))` | `NavBall.java:417` `private void snapToEdge() {`（**定义**）。删掉 `:467`/`:479` 调用仍绿 |
| `NavBallJvmTest.kt:110` | `assertTrue("菜单项必须有按压反馈", source.contains("pressFeedback("))` | `NavBall.java:606` `private void pressFeedback(Pill pill) {`（**定义**） |
| `NavBallJvmTest.kt:69` | `assertTrue("必须持久化球心坐标", source.contains("persistPosition()"))` | `NavBall.java:412` `private void persistPosition() {`（**定义**） |
| `NavBallJvmTest.kt:70` | `assertTrue("必须能恢复球心坐标", source.contains("loadPosition()"))` | `NavBall.java:406` `private void loadPosition() {`（**定义**） |
| `NavBallJvmTest.kt:142` | `assertTrue("必须为 AI 页留避让区", source.contains("AI_SAFE_RATIO"))` | `NavBall.java:61` `private static final float AI_SAFE_RATIO = 0.78f;`（**声明**）。删掉 `:393` 的唯一使用仍绿 |
| `FolderLoadPolicyJvmTest.kt:262` | `assertTrue("必须 attach 起 detach 停", ring.contains("onDetachedFromWindow"))` | `DfwxLoadingRing.java:96` `@Override protected void onDetachedFromWindow() {`（**定义**） |
| `FolderLoadPolicyJvmTest.kt:263` | `assertTrue("关掉动效时要能停在静态弧上", ring.contains("setRingProgress"))` | `DfwxLoadingRing.java:71` `void setRingProgress(float value) {`（**定义**） |
| `SearchListScrollJvmTest.kt:104-107` | `assertTrue("必须有窗口化追加渲染", source.contains("maybeAppendSearchWindow") \|\| source.contains("appendSearchWindow"))` | `MainActivity.java:2474` `void maybeAppendSearchWindow(){...}`（**定义**） |

### 怎么改

1. `NavBallJvmTest` 把剩下 10 个测试全部改成 `methodBody("<签名>")` 再断言 —— helper 已经写好了，
   只是没用上。`menu_hasScrimDismissAndForceClear` 应断言 `methodBody("void onDestinationChanged()")`
   里同时有 `closeMenu()` 与 `forceHidePills()`（与 `:118-127` 那条重复，可合并）。
2. `AI_SAFE_RATIO` 这类**常量**不能只查名字，要查使用点：断言 `snapToEdge`/`clamp` 的方法体里
   出现 `AI_SAFE_RATIO`，或直接断言 `NavBall.java` 中 `AI_SAFE_RATIO` 出现次数 `>= 2`（声明 + 至少一处使用）。
3. `FolderLoadPolicyJvmTest:262-263` 的意图是"attach 起、detach 停"，应断言
   `onAttachedToWindow` 方法体里有 `postInvalidateOnAnimation`/`animating=true`，
   `onDetachedFromWindow` 方法体里把动画标志关掉 —— 而不是"这两个方法存在"。
4. `SearchListScrollJvmTest:104` 若要守"窗口化追加真的会发生"，应断言
   `renderSearchResults` 的方法体里**调用了** `maybeAppendSearchWindow()`，而不是它存在。

---

## 4. P1-① 断言的对象是**注释**，不是代码

### 清单

| 位置 | 断言 | 命中的注释 |
|---|---|---|
| `TopBannerAndUpdateJvmTest.kt:98` | `assertTrue("顶部通知条必须明确标注为非模态", src.contains("非模态"))` | `NoticeBanner.java:36` Javadoc `本类是**非模态提示**（无遮罩、空白处点击穿透到下面的界面）` |
| `SupportPageFeedbackJvmTest.kt:132` | `assertTrue("必须在注释里写明这条规则抄自哪里", src.contains("MainActivity.solidShape"))` | `SupportActivity.java:476` 注释 |
| `SupportPageFeedbackJvmTest.kt:133` | `assertTrue("必须写明抄的是哪个版本的文件", src.contains("MainActivity.java:599"))` | 同一行注释 |
| `PageTransitionMotionJvmTest.kt:114` | `assertTrue("注释要写清这是用户最新意见覆盖了历史要求", region.contains("DFW-79"))` | `MainActivity.java:416/981/990` 注释 |
| `TopBannerAndUpdateJvmTest.kt:115` | `val branchEnd = after.indexOf("// 后台不可达")` | `MainActivity.java:1237` 注释（审计开始时为 `:1217`，因并发改动 +20），被当作**切片边界** |

### 为什么是问题

- `banner_isNonModal_andCanBeDismissed`（`:95-101`）这个名字承诺"验证通知条是非模态的"。
  实际只验证了**源码里出现过"非模态"四个字**。把 `NoticeBanner` 改成带遮罩的模态弹窗、
  同时把 Javadoc 改成"本类是模态对话框" —— 测试照样绿（`非模态` 这个词仍在）。
  **这个契约（无遮罩 / 点击穿透）目前零覆盖。**
- `TopBannerAndUpdateJvmTest.kt:115` 更脆：用一句注释当切片边界。
  若有人在这条注释**之前**再写一句 `// 后台不可达`，切片变空，
  `assertTrue(branch.contains("return null;"))` 会**假红**；反之若把兜底代码挪到注释之后，
  `assertFalse(branch.contains("UpdateClient.check("))` 也会假红。**红/绿都取决于注释，不取决于行为。**

### 怎么改

1. `banner_isNonModal_andCanBeDismissed` 改成行为断言：
   ```kotlin
   val banner = NoticeBanner(...)   // 或 a.showTopBanner(...)
   assertNull("非模态 = 没有遮罩层", banner.scrim)          // 若内部有 scrim 字段
   assertFalse("非模态 = 空白处点击不被吃掉", banner.isClickable)
   // 更强的做法：在 banner 下方放一个可点 View，点 (x,y) 落在 banner 之外，断言下方 View 收到点击
   ```
   注释断言可以保留，但**必须换名字**（如 `noticeBanner_documentsItsNonModalContract`），
   不能顶着行为契约的名字。
2. `TopBannerAndUpdateJvmTest.kt:115` 的切片边界改成**代码锚点**（如
   `after.indexOf("UpdateClient.check(")` 之前的那条 `if(` 语句），或直接按花括号配对取
   `if/else` 分支体。**注释不能当边界。**

---

## 5. P1-② 一条"跨文件一致性"测试从不读另一个文件，且它引用的行号已失效

**位置**：`SupportPageFeedbackJvmTest.kt:124-134`

```kotlin
@Test
fun solidShape_ruleAndItsProvenanceAreWrittenDownInTheSource() {
    // 那个规则在**另一个窗口正在改的文件**里，所以这里要求本页把"抄的是哪条规则"写在注释里，
    // 并逐字钉住量化表达式 —— 以后站内改了，这里能立刻看出来两边是不是还一致。
    val src = source("app/src/main/java/cc/nkbr/lanzouplus/SupportActivity.java")
    assertTrue("量化表达式必须与站内一致（>=24→26 / 14–23→20 / 5–13→8 / <5 原样）",
        src.contains("radius>=24?26:radius>=14?20:radius>=5?8:radius"))
    assertTrue("必须在注释里写明这条规则抄自哪里（那个文件在动）", src.contains("MainActivity.solidShape"))
    assertTrue("必须写明抄的是哪个版本的文件", src.contains("MainActivity.java:599"))
}
```

**为什么是问题（两层）**：

1. **它做不到自己声称的事。** 注释明写"以后站内改了，这里能立刻看出来两边是不是还一致"，
   但整个测试**只读了 `SupportActivity.java` 一个文件**，从头到尾没有打开 `MainActivity.java`。
   真正的"两边一致"验证是：读 `MainActivity.solidShape` 的方法体，抽出
   `radius>=24?26:radius>=14?20:radius>=5?8:radius`，与 `SupportActivity.java` 里的逐字比较。
   **现在站内改了、本页没改 → 测试仍然绿。**
2. **它钉住的行号已经过期，而测试在给这个过期背书。** 断言 `src.contains("MainActivity.java:599")`
   实际验证的是 `SupportActivity.java:476` 的这句注释：
   > 量化规则**抄自站内 `MainActivity.solidShape`（HEAD 69bb1e3 的 MainActivity.java:599，

   实测 `MainActivity.java:599` **已经不是** `solidShape`；`solidShape` 现在在
   **`MainActivity.java:654`**（`grep -n "solidShape" MainActivity.java` 首个定义命中 `:654`）。
   注释因为写死了 `HEAD 69bb1e3` 尚可辩解为"历史快照"，但**测试只断言这个字符串存在**，
   等于把"跨文件引用仍然准确"这件事变成了一条永远为真的注释检查。

**怎么改**：

```kotlin
private val mainSrc = source("app/src/main/java/cc/nkbr/lanzouplus/MainActivity.java")

@Test
fun solidShape_ruleIsLiterallyTheSameAsTheHostApp() {
    val hostRule = Regex("g\\.setCornerRadius\\(dp\\(([^)]+)\\)\\)").find(
        methodBody(mainSrc, "public GradientDrawable solidShape(int color,int radius)")
    )!!.groupValues[1]
    val supportRule = Regex("dp\\(([^)]+)\\)").find(
        methodBody(source(".../SupportActivity.java"), "GradientDrawable solidShape(int color,int radius)")
    )!!.groupValues[1]
    assertEquals("支持页的圆角量化规则必须与站内逐字一致（否则两页圆角会分叉）", hostRule, supportRule)
}
```
另外把注释里的 `MainActivity.java:599` 换成**方法名 + 提交号**（如
`MainActivity.solidShape @ 69bb1e3`），行号会随文件增长必然失效，不该进注释。

---

## 6. P1-③ 9 个模块 336 条测试不在门禁内

**位置**：`tools/ci-gate.sh:77-90`（`gate_unit()`）

```bash
gate_unit() {
  ./gradlew ... :app:testEmptyDebugUnitTest            # 761 例
  ./gradlew ... :rikkahub-app:testDebugUnitTest        # 289 例
  ./gradlew ... :common:testDebugUnitTest              #  14 例
}
```

`settings.gradle` 里共 **12 个模块**有 `src/test`。门禁只跑了 3 个。
其余 9 个模块的测试**可以跑、也跑过**（`build/test-results/testDebugUnitTest/` 有产物），
只是没进任何门禁：

| 模块 | 测试文件 | 上次产出结果的用例数 | 结果时间 | 之后该模块 main 源是否改过 |
|---|---|---|---|---|
| `:ai` | 26 | **199** | 2026-09-28 17:56 | ✅ **改过**（`bbf689d` 2026-09-30，4 个文件） |
| `:highlight` | 6 | 53 | 2026-09-28 21:55 | 否 |
| `:speech` | 12 | 43 | 2026-09-28 21:55 | 否 |
| `:workspace` | 3 | 20 | 2026-09-28 21:55 | 否 |
| `:search` | 4 | 16 | 2026-09-28 21:55 | 否 |
| `:oauth` | 2 | 2 | 2026-09-28 21:54 | 否 |
| `:document` | 1 | 1 | 2026-09-28 21:55 | 否 |
| `:material3` | 1 | 1 | 2026-09-28 21:54 | 否 |
| `:web` | 1 | 1 | 2026-09-28 21:54 | 否 |
| **合计** | **57** | **336** | — | — |

**为什么是问题**：
- `.github/workflows/ci-gates.yml` 的注释明确写了历史教训："CI 从不跑任何 test 任务，
  导致约 90+ 个测试类在 CI 上从未执行"。**这次收敛到了 3 个模块，另外 9 个模块复刻了同一个坑**，
  只是规模小一点（57 个类 / 336 例）。
- `:ai` 是**真的踩到了**：`bbf689d DFW-8 AI 日志脱敏` 改了
  `ClaudeProvider.kt` / `GoogleProvider.kt` / `ChatCompletionsAPI.kt` / `ResponseAPI.kt` 四个文件
  （把 `Log.d("onEvent: $data")` 改成只留事件类型与字节数），
  而 `:ai` 的 199 条测试**自 2026-09-28 起没再跑过**。
  脱敏**规则**本身有测试（`DfwxLogRedactorTest` 在 `:common`、`RequestLoggingRedactionTest` 在
  `:rikkahub-app`，两个模块都在门禁内 ✅），但**这四个 SSE 文件自身的改动没有任何测试** ——
  `:ai` 的 26 个测试文件里，`grep -rl "redact\|脱敏\|sanitiz"` 命中 0。

**怎么改**：
1. `gate_unit()` 补上其余 9 个模块（或至少 `:ai`）：
   ```bash
   step "JVM 测试门禁：vendor 其余模块"
   ./gradlew "${GRADLE_ARGS[@]}" \
     :ai:testDebugUnitTest :highlight:testDebugUnitTest :speech:testDebugUnitTest \
     :workspace:testDebugUnitTest :search:testDebugUnitTest :oauth:testDebugUnitTest \
     :document:testDebugUnitTest :material3:testDebugUnitTest :web:testDebugUnitTest
   ```
   代价是门禁时间上升；如果嫌慢，至少把 `:ai` 加进去（它是唯一在动的 vendor 模块），
   其余 8 个在 `ci-gates.yml` 里加一条**每周定时任务**跑一遍全量。
2. 更根本的做法：改成 `./gradlew test`（跑所有模块所有变体），
   或写一个"扫描 `settings.gradle` 的每个模块并逐一执行 `test*UnitTest`"的循环，
   这样**新增模块会自动进入门禁**，不会再次漂移。
3. `:ai` 的 SSE 日志改动补一条守卫（断言四个 provider 文件里不再出现
   `Log.d("onEvent: $data")` 这种打印完整响应体的写法）。

---

## 7. P1-④ 新增的反馈页冒烟测试曾未进 git（**审计期间已被 `93858db` 修复**）

**位置**：`app/src/test/java/cc/nkbr/lanzouplus/FeedbackPageSmokeJvmTest.kt`

**审计开始时的状态**（`git status` 显示 `??`）：

```
$ git status --porcelain app/src/test/
 M app/src/test/java/cc/nkbr/lanzouplus/SearchPageBottomAndBackJvmTest.kt
?? app/src/test/java/cc/nkbr/lanzouplus/FeedbackPageSmokeJvmTest.kt

$ git ls-files app/src/test/java/cc/nkbr/lanzouplus/ | wc -l
89        # 磁盘上有 90 个
```

**为什么当时是问题**：
- 这是"反馈页"（本轮 5 个改动点之一）**唯一的行为级测试** ——
  `FeedbackEntryAndSkinJvmTest` 全是源码文本断言，只有它真的启动 `FeedbackPage`
  验证 Manifest 注册 + assets 读取 + WebView 配置（4 例，`FeedbackPageSmokeJvmTest.kt:39-95`）。
- 它不进 git → **CI checkout 之后这个文件不存在** → 本地绿与 CI 绿**不再等价**。
- 讽刺的是 `.github/workflows/ci-gates.yml:8-10` 正好记录了历史上**完全相同的**事故：
  > 历史上出现过"CI 冒烟绿灯但安全策略测试在 checkout 后根本不存在"（untracked 的 `DownloadPolicyTest`）这类假绿。

  `DownloadPolicyTest` 这次已经是 tracked 了（`git ls-files` 命中），但**同一个坑原地复现**。

**现状（2026-10-02 10:30 复核）**：`93858db 复查① 自查发现并修掉两处` 已把
`FeedbackPageSmokeJvmTest.kt` 纳入 git。复核命令：

```
$ git ls-files app/src/test/java/cc/nkbr/lanzouplus/ | wc -l
90                                   # 与磁盘一致
$ git ls-files app/src/test/java/cc/nkbr/lanzouplus/ | grep FeedbackPageSmoke
app/src/test/java/cc/nkbr/lanzouplus/FeedbackPageSmokeJvmTest.kt   ✅
$ git ls-files --others --exclude-standard -- 'app/src/test/*' 'rikkahub/*/src/test/*'
（空）                                # 已无 untracked 测试文件
```

**仍然建议固化的门禁**（这次是人工发现的，下次未必；纯静态、零成本，加进 `gate_docs()`）：

```bash
# 测试文件不得只存在于工作树：untracked 的测试 = CI 上不存在的测试（DFW-31 教训）
untracked_tests="$(git ls-files --others --exclude-standard -- '*/src/test/*')"
[[ -z "$untracked_tests" ]] || fail "有未纳入 git 的测试文件（CI 上不会执行）：\n$untracked_tests"
```

---

## 8. P2 级发现

### 8.1 截图测试无断言

| 位置 | 测试 | 说明 |
|---|---|---|
| `HomeShotsJvmTest.kt:60` | `captureHomeAndSearchStates()` | 只渲染 + 写 PNG 到 `~/heiyao/build_output/phone_shots/` |
| `HomeShotsJvmTest.kt:80` | `captureMarginsForAudit()` | 同上 |
| `HomeShotsJvmTest.kt:97` | `captureAllPagesForMarginAudit()` | 同上 |
| `ToolsShotsJvmTest.kt:49` | `captureHomeToolsListAndAll35ToolPages()` | 同上 |

这些是**视觉取证工具**，不是回归测试：它们能红的前提是 `showXxx()` 抛异常。
`captureAllPagesForMarginAudit` 这个名字（"全页边距审计"）尤其容易被误读成"边距被守住了"。
**建议**：类名/方法名加 `Shot`/`Capture` 前缀（已经是了），并在类注释里明确写
"本类不产生回归信号，仅供人工目检"，避免在"我们有 761 条守卫"的叙事里被当成保护。
（这两个类的注释目前已经比较诚实，属于**建议**而非缺陷。）

### 8.2 `assertTrue(true)` ×3

| 位置 | 上下文 |
|---|---|
| `MemoryLifecycleJvmTest.kt:132` | `trimMemory_neverThrowsForAnyLevel` 的结尾 |
| `ShareReceiveJvmTest.kt:108` | `actionSendTextIsHandledWithoutCrash` 的结尾 |
| `ShareReceiveJvmTest.kt:116` | `actionViewHttpLinkIsHandledWithoutCrash` 的结尾 |

三条都是"不抛异常即通过"的冒烟测试，`assertTrue(true)` 是**冗余噪音**（把隐式的
"没抛"伪装成显式断言）。**建议删掉 `assertTrue(true)`**，改成注释说明
"本测试的判据是'不抛异常'"；或者升级为真断言 —— 例如 `ShareReceiveJvmTest.kt:99-109`
完全可以断言 `a.pageKind` 变成了搜索结果页（同文件 `:126`/`:136` 就是这么做的），
比"没崩"强得多。

### 8.3 无断言的"不崩"测试

`SupportPageFeedbackJvmTest.kt:377-381`：

```kotlin
@Test
fun pressScale_ignoresNullInsteadOfCrashing() {
    val a = activity()
    a.pressScale(null)
}
```

判据同样是"不抛"。可接受（`null` 是真实路径），但方法名里的
`ignoresNull` 建议补一条可观察断言，例如断言 `a` 的某个状态未被改动。

---

## 9. 覆盖缺口：本轮 5 个改动点逐一核对

| # | 改动点 | 对应测试 | 结论 |
|---|---|---|---|
| 1 | 更新校验（DFW-90） | `UpdateVerificationPolicyJvmTest.kt`（8 例，源码锚点） | ✅ 有测试。但 3 条反向探针里 **2 条是恒真**（§1），实际只剩 1 条有鉴别力 |
| 2 | 搜索页高度（DFW-95①） | `SearchPageBottomAndBackJvmTest.kt:104-130`（真 measure+layout 后断言高度） | ✅ **质量最好的一条**：真实测量，不是文本断言 |
| 3 | 搜索页返回动画（DFW-95②） | `SearchPageBottomAndBackJvmTest.kt:132-156`（源码锚点）+ `:168-217`（打断后兜底状态，真行为） | ⚠️ 部分。**交叉淡化的存在**是源码断言（`:134-145`）；**兜底逻辑**那条是行为测试，且**工作树当前版本已主动登记"它没有证明什么"**（`:175-183`：去掉 600ms 兜底这条测试依然绿，反向探针实测不红）——这种"如实登记未验证项"的做法值得全仓推广，但它意味着 `MainActivity.crossFadeHomeSection` 的 600ms 兜底目前**仍是未验证的防御性代码**。3 条反向探针里 **2 条恒真**（§1） |
| 4 | 公告缓存（DFW-103） | `NoticeSnapshotCacheJvmTest.kt`（11 例，Robolectric 真存真读 + 坏数据 + 去重源码断言） | ✅ **质量最好的一类**：存→读往返、损坏数据降级、解析器复用都是真行为断言。`:50-75` 的注释还主动说明了"为什么这里只能做源码断言"，很诚实 |
| 5 | 反馈页（DFW-97） | `FeedbackEntryAndSkinJvmTest.kt`（源码锚点）+ `FeedbackPageSmokeJvmTest.kt`（真启动，4 例） | ⚠️ 有测试，但**冒烟测试未进 git**（§7），CI 上不存在；换肤脚本的"只改颜色"是字符串黑名单（见下） |

**额外缺口（本轮新引入的代码）**：

- `app/src/main/assets/feedback_skin.js`（128 行，DFW-97 新增）的守卫全部是
  `skin.contains("...")` / `skin.contains("...") == false` 的字符串黑名单
  （`FeedbackEntryAndSkinJvmTest.kt:120-141`）。
  **没有任何测试真的执行这个 JS**。若脚本语法错误（比如少个括号），
  `skin.length > 500` 与所有 `contains` 仍然通过，而真机上换肤**静默失效**
  （`FeedbackPage.readAsset` 读得到文件就返回内容，WebView 解析失败不报错到 App）。
  **建议**：在 Robolectric 里把 `feedback_skin.js` 塞进 WebView 并断言
  `evaluateJavascript("typeof window.__dfwxApplied")` 之类的副作用标记，
  或至少用 JS 解析器（项目已有 QuickJS）做一次语法校验。
- `:ai` 的 4 个 SSE 日志文件改动（DFW-8）**零测试**，且所在模块不在门禁内（§6）。

---

## 10. 测试是否真的在跑：统计与门禁覆盖

### 10.1 用例统计（**2026-10-02 10:0x 快照**，非终值）

```
$ python3 - <<'EOF'   # 解析 app/build/test-results/testEmptyDebugUnitTest/*.xml
classes=90  tests=761  failures=0  errors=0  skipped=0
EOF
```

- **`:app`（emptyDebug 变体）：90 个测试类 / 761 条用例 / 0 失败 / 0 错误 / 0 跳过**（**终值**）
- 用例数最多的 5 个类：`NoticeGlassJvmTest`(24)、`NavPeerJvmTest`(22)、
  `RemoteConfigClientJvmTest`(22)、`SupportPageFeedbackJvmTest`(19)、`UpdateOfferJvmTest`(19)
- 测试源文件数 90，**90 个全部有对应 XML**（写作过程中刚加入的 `FeedbackPageSmokeJvmTest`，4 例，
  已在终值里，`failures=0`）
- **0 跳过**是好事：没有 `@Ignore` 造成的静默失效

### 10.2 门禁覆盖（`tools/ci-gate.sh unit`）

| 门禁跑的模块 | 用例数 | 结果时间 |
|---|---|---|
| `:app`（`testEmptyDebugUnitTest`） | 761 | 2026-10-02 10:2x |
| `:rikkahub-app`（`testDebugUnitTest`） | 289 | 2026-10-01 19:31 |
| `:common`（`testDebugUnitTest`） | 14 | 2026-09-30 03:25 |
| **门禁内小计** | **1064** | — |
| **门禁外（9 个模块）** | **336** | 2026-09-28（见 §6） |

**结论**：项目文档里说的"756 条 JVM 守卫"只覆盖 `:app` 一个模块；
真实存在的 JVM 测试总量是 **1400 条**，门禁跑到 **1064 条（76.0%）**，
**336 条（24.0%）从不进任何门禁**。其中 `:ai` 的 199 条在代码改动之后没再跑过。

`ci-gates.yml` 的 artifact 上传路径（`app/build/reports/tests/**`、
`rikkahub/app/**`、`rikkahub/common/**`）也只覆盖这 3 个模块 ——
即使有人手动跑了 `:ai` 的测试并失败，CI 也拿不到报告。

### 10.3 门禁本身的两处可加固点

1. `ci-gate.sh:25` 的注释写的是"**749** 条用例"，实际已是 **761**（`:app`）+ 289 + 14 = 1064。
   注释里的数字会漂，建议删掉具体数字，改成"上千条用例"。
2. `gate_unit()` 没有跑 `:app` 的另一个 flavor 变体。`app/build.gradle.kts:81-82` 有
   `flavorDimensions += "catalog"`，门禁只跑 `emptyDebug`。
   若还有其它 flavor 承载不同的资源/清单差异，那些差异不会被 JVM 测试覆盖
   （`assembleEmptyRelease` 的 ABI 断言在 `gate_apk()` 里有，属于另一层）。

---

## 11. 已查、确认没问题的清单

以下是任务点名要查、**逐条核过、结论为"没问题"**的：

### 11.1 恒真断言类

- ✅ **`assertTrue(true)` / `assertFalse(false)` / `assertEquals(1,1)` 的穷举扫描**：
  全仓只命中 3 处 `assertTrue(true)`（§8.2），无其它形态的恒真字面量断言。
- ✅ **断言对象是"自己刚构造的局部变量"**：扫描 `assertEquals(x, x)` 同变量自比、
  `assertNotNull(new X())` 构造式断言，**零命中**。
- ✅ **`contains("")` / `size() >= 0` / `size() > -1`**：**零命中**。
- ✅ **`UpdateOfferJvmTest.kt:138` 的 `assertTrue(false)`**：初看像"永远失败的断言却全绿"，
  回源确认它在 `try` 块内，`catch (UnsupportedOperationException)` 只接 `UnsupportedOperationException`，
  `AssertionError` 不会被吞 —— 这是**正确的**"不该走到这里"标记，不是缺陷。

### 11.2 锚点类

- ✅ **锚点与源码不一致但测试仍绿**：对 555 条锚点做了"回源定位"扫描，
  **没有发现"锚点已从源码消失、测试却还绿"的情况**（这类会直接红，属于脆弱而非假绿）。
  唯一的"引用已失效却仍绿"是 `SupportPageFeedbackJvmTest.kt:133` 的行号注释（§5），
  属于注释而非代码锚点。
- ✅ **`FolderLoadPolicyJvmTest` 的三条 `assertFalse` 会误伤注释吗**：
  `:212` `loading("正在刷新目录")`、`:229` `DecelerateInterpolator`、`:250` `progressBarStyleSmall`
  这三个锚点在 `MainActivity.java` 里**只出现在注释中**（`:2275`（审计开始时为 `:2255`）/ `:589` / `DfwxLoadingRing.java:15`），
  看似危险；但该类的 `methodBody()` 实现了 `stripComments()`（`FolderLoadPolicyJvmTest.kt:57-70`），
  注释在断言前已被剥掉，**且注释都在方法体之外** —— 判定正确，**不是假绿也不是假红**。
  这一条是项目里处理得最好的范例（把"为什么必须剥注释"写进了代码注释）。
- ✅ **`PageTransitionMotionJvmTest.kt:146` `fade.contains("if(direction==0)")`**：
  初扫命中"注释行"，回源确认是 `MainActivity.java:983` 的一行式块注释
  `/* ... */if(direction==0){...}` —— 代码就在同一行，**是真实代码锚点**，误报。
- ✅ **`FeedbackEntryAndSkinJvmTest.kt:122` `skin.contains("pointer-events")`**：
  初扫命中 `FeedbackPage.java:47-48` 的注释，但该断言的对象是 `feedback_skin.js`
  （另一份文件），且是 `assertFalse` —— **判定正确**，误报。
- ✅ **`NavBallJvmTest.kt:41` 的类注释**：已正确记录"第一版假绿"的教训，
  且 `methodBody()` 用花括号配对而非长度切片 —— **helper 本身是正确的**（问题是没被用满，见 §3）。
- ✅ **`RenderFolderNullSafetyJvmTest`**（任务里点名的历史事故）：已修复到位。
  按**语句**而非整个方法体判定判空（`:72-83`），并且有 3 条真正的反向探针
  （`:96-107`，含专门针对第一版假绿的 `mixed` 用例）和 1 条 body 提取自检（`:111-115`）。
  **这是全仓反向探针写得最规范的类，可作为其余测试的模板。**

### 11.3 行为测试类（质量确认）

- ✅ `NoticeSnapshotCacheJvmTest.kt`：Robolectric 真存真读、损坏数据两种降级路径、
  "缓存必须复用线上解析器"用 `enabled:false` 被过滤来证明 —— **真行为断言**，
  不是文本断言。`:41-48` 还诚实说明了"为什么另一条只能做源码断言"。
- ✅ `SearchPageBottomAndBackJvmTest.kt:104-130`：真 `measure()` + `layout()` 后断言高度，
  是"搜索页高度"这个改动**唯一**有鉴别力的验证。
- ⚠️ `SearchPageBottomAndBackJvmTest.kt:186-217`：模拟 4 次快速来回切 + `animate().cancel()`
  + 推进 700ms，断言界面落在自洽状态。**写法本身是好的**（工作树当前版本），
  但作者已在 `:175-183` 主动登记：**去掉被守的 600ms 兜底，这条测试依然绿**（反向探针实测不红）。
  即：它守住了"终态自洽"这个**结果**，没有守住"兜底真的生效"这个**机制**。
  **这条注释的诚实度是全仓最高的** —— 建议把"反向探针实测不红 = 该条未验证"这个登记格式
  推广到 §1 那 8 条恒真探针上。
- ✅ `SearchPageBudgetJvmTest.kt`：`:97-103` 的 `theTwoBudgetsAreGenuinelyIndependent`
  是**真探针**（真的调用 `searchOptions(59)` 与 `searchOptions(1, 20)` 并比较）。
- ✅ `ToolsSweepJvmTest.kt:47-82`：真遍历 35 个工具页，收集越界视图后 `throw AssertionError`
  （我的"无断言"初扫把它误报为无断言，回源确认判据是 `throw`）。
- ✅ `FeedbackPageSmokeJvmTest.kt:39-95`：真启动 Activity、真读 assets、真断言 WebView 配置 ——
  **内容质量好**，问题只在于未进 git（§7）。
- ✅ **`:app` 的 0 跳过**：无 `@Ignore`、无 `assumeTrue` 造成的静默失效。

### 11.4 门禁类

- ✅ `ci-gate.sh` 与 `ci-gates.yml` **共用同一个脚本**（不是两套命令），
  这条"本地与 CI 结论可对照"的设计是成立的。
- ✅ `gate_docs()` 的静态检查（调试残留、明文流量、ABI 资产名）逻辑正确、无假绿。
- ✅ `ci-gate.sh:20-23` 的 `ulimit -u` 处理有据（注释解释了 749 例会耗尽线程），
  `|| true` 吞错是合理降级。
- ✅ 历史上 untracked 的 `DownloadPolicyTest.java` **现在已经 tracked**（`git ls-files` 命中）。

---

## 12. 建议的修复顺序

| 顺序 | 动作 | 影响面 | 成本 |
|---|---|---|---|
| 1 | ~~`git add FeedbackPageSmokeJvmTest.kt`~~ **→ 审计期间已由 `93858db` 完成**；**只剩**给 `gate_docs()` 加"测试文件不得 untracked"检查（防复发） | 防止"本地绿≠CI绿"再次发生 | 5 分钟 |
| 2 | 修 `NoticeCenterJvmTest.kt:192-206`，补真断言 | 恢复一个**零覆盖**的契约 | 15 分钟 |
| 3 | 8 条恒真反向探针改成"喂坏代码给真守卫函数" | 恢复反向探针的鉴别力 | 1–2 小时 |
| 4 | `NavBallJvmTest` 剩余 10 个测试改用 `methodBody()` | 恢复 6 条守卫 | 30 分钟 |
| 5 | `gate_unit()` 补 `:ai`（其余 8 个模块走每周定时） | 覆盖 199 条停滞的测试 | 10 分钟 |
| 6 | `TopBannerAndUpdateJvmTest.kt:98` 改成行为断言；`:115` 切片边界改代码锚点 | 恢复"非模态"契约 | 30 分钟 |
| 7 | `SupportPageFeedbackJvmTest.kt:124-134` 改成真的跨文件比对 | 让测试名与行为一致 | 20 分钟 |
| 8 | `feedback_skin.js` 补一次真实执行/语法校验 | 防换肤静默失效 | 30 分钟 |

---

## 附：本报告用到的复核命令（可复现）

```bash
# 用例统计（10:0x 快照）
python3 -c "import glob,xml.etree.ElementTree as ET; \
  fs=glob.glob('app/build/test-results/testEmptyDebugUnitTest/*.xml'); \
  print(sum(int(ET.parse(f).getroot().get('tests',0)) for f in fs))"

# 恒真断言：两个字面量互相 contains（操作数里没有任何标识符）
python3 /tmp/taut.py        # 见 §1 清单

# 锚点回源：555 条锚点 → 落在注释还是代码
python3 /tmp/anchor3.py     # 见 §4 清单

# 交付面
git status --porcelain app/src/test/
git ls-files app/src/test/java/cc/nkbr/lanzouplus/ | wc -l   # 89（磁盘 90）

# 门禁覆盖
grep -n "gradlew" tools/ci-gate.sh
ls -d rikkahub/*/build/test-results/*/                        # 12 个模块都有产物

# 行号失效
grep -n "solidShape" app/src/main/java/cc/nkbr/lanzouplus/MainActivity.java   # 定义在 :654，非 :599
```
