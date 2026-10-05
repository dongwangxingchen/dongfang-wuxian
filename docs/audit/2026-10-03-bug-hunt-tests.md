# 2026-10-03 测试可信度审计（假绿 / 脆弱锚点 / 覆盖缺口）

> **代码冻结**：`953eb26`（分支 `test`，工作树干净）
> **审计对象**：`app/src/test/java/cc/nkbr/lanzouplus/` —— **100 个测试类 / 855 条 `@Test`**
> **方法**：只读 + 脚本化扫描（注释与字符串先做**等长空白化**以保证行号不漂），
> 再对每条候选**逐条回源**到 `app/src/main/**` 与 `rikkahub/**` 的实际行，判定它落在代码还是注释。
> **未做**：没跑 gradle（lead 在构建）、没改任何产品代码/测试/脚本、没 `git add`/`commit`/`push`、
> 没读 `local.properties`。唯一写入的文件就是本报告。
> **口径说明**：本报告**不把字符串锚点一刀切**。源码级守卫（Manifest/gradle/文档/设计 token/JS 资产/
> 调用顺序）是合理的，因为运行时在 Robolectric 里根本观察不到；只把「**本可用运行时行为断言、
> 却退化成读源码文本**」的算作偷懒。下面每条都注明了属于哪一类。

---

## 第 1 节：实锤的 bug

### 1.1 【P0】一条零断言的测试：抛与不抛都绿

**位置**：`app/src/test/java/cc/nkbr/lanzouplus/NoticeCenterJvmTest.kt:193-207`

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

**为什么不可信（证明）**：方法体里**没有任何 `assert*`、没有 `fail()`、没有 `throw`**。
脚本判据：`@Test` 方法体内 `assert\w*\(|fail\(|assertThat\(|Assertions\.` 全部不匹配，且无 `throw`。
于是两种结果**都通过**：

- `unreadCount` 抛 `RuntimeException` → 被 catch 吞掉 → 绿；
- `unreadCount` 正常返回 → 什么都不发生 → 绿。

**它现在会漏掉什么真 bug**：测试名承诺的 `degradesToUnread`（存储坏掉时**按未读处理、红点不消**）
**零覆盖**。如果哪天有人把 `NoticeCenter` 改成"读失败就当全部已读"，红点会永久消失、
用户再也看不到公告 —— 这条测试**照样绿**。
另外注释里说的"生产实现 `forContext` 内部已 try/catch 兜底"描述的**是另一条代码路径**：
本用例直接 `NoticeCenter(broken)` 构造，根本没走 `forContext`。

**建议怎么改**：断言落在可观察结果上，而不是"有没有抛"：

```kotlin
assertEquals("存储坏掉时宁可红点不消，也不能当成已读", 1, c.unreadCount(s))
```
若 `NoticeCenter(broken)` 直接抛，那正说明生产实现**缺**兜底 —— 磁盘满/权限被撤时就是崩溃，
**这条测试本来就该红**。

---

### 1.2 【P0】5 个测试类靠「上一个类泄漏的静态标志」才不联网 —— 单跑必触发 85 源真实探测

这是 DFW-124 的**同类 bug，但换了一条 DFW-124 没覆盖的网络路径**。

**证据链**：

| # | 事实 | 位置 |
|---|---|---|
| 1 | `onCreate` 里**无条件**调 `loadRecommendations()` | `MainActivity.java:182` |
| 2 | 它检查 `LIBRARY_AUTO_IMPORT`（`static volatile`，**默认 `true`**）与 `librariesImported.v4` | `MainActivity.java:339`、`:349` |
| 3 | 通过检查后在 `io` 线程池里调 `core.syncLibraryTitles(...)` + `core.addUserSourcesBatch(batch)` | `MainActivity.java:357-359` |
| 4 | `addUserSourcesBatch` 对每个源提交 `resolveUserSource(...)` | `LanzouCore.java:758` |
| 5 | `resolveUserSource` → `singleSource(...probeSingleSource(...))` / `detectSource(...)` = **真实网络** | `LanzouCore.java:795-800` |
| 6 | **`LanzouCore.java` 全文 `isJvmUnitTest` 出现 0 次**（`grep -c` = 0） | `LanzouCore.java` |
| 7 | DFW-124 的守卫**只在** `RemoteConfigClient.get()` | `RemoteConfigClient.java:506` |
| 8 | `App.isJvmUnitTest()` 全仓只被 2 处使用：`App.java:154`、`RemoteConfigClient.java:506` | — |

**所以第二条网络路径完全没有守卫**，唯一压制它的是测试自己设的那个静态标志。

**为什么它现在"看起来没事"**：`app/build.gradle.kts:122` 设了 `it.forkEvery = 24` ——
**24 个测试类共用一个 JVM**。`LIBRARY_AUTO_IMPORT` 是静态字段，一旦某个类在 `@BeforeClass`
里把它置 `false`，**同一 JVM 里后面所有类都跟着受益**，而且**没有任何类把它还原**。

**哪 5 个类没有自己设**（它们会启动完整 `MainActivity.setup()`）：

| 类 | 调用点 | 是否静音 |
|---|---|---|
| `AiPermissionGateJvmTest.kt` | `:51` `buildActivity(MainActivity::class.java).setup().get()` | ❌ |
| `CrashReportExportJvmTest.kt` | `:29`、`:62`、`:84`、`:101`（**4 处**） | ❌ |
| `DfLogWiringJvmTest.kt` | `:46` | ❌ |
| `SearchPageBudgetJvmTest.kt` | `:41` | ❌ |
| `SettingsStructureTest.java` | `:51`、`:111` | ❌ |

（对照：**44 个类**在 `@BeforeClass` 里写了 `MainActivity.LIBRARY_AUTO_IMPORT = false`，
例如 `AiInsetsContractJvmTest.kt:43`。）

**触发条件（不用跑就能推出来）**：`tools/run-tests.sh` 的文档把 `--class` 当成主要用法，
而：

```
bash tools/run-tests.sh --class SearchPageBudgetJvmTest
bash tools/run-tests.sh --class AiPermissionGateJvmTest
bash tools/run-tests.sh --class DfLogWiringJvmTest
bash tools/run-tests.sh --class CrashReportExportJvmTest
bash tools/run-tests.sh --class SettingsStructureTest
```

**只跑这一个类** → 没有任何静音类在它之前跑过 → 标志保持默认 `true` →
`loadRecommendations()` 走进去 → 对内置清单里的**每一个源**发真实网络探测。

**它现在会漏掉/造成什么**：
1. **测试会真的打生产蓝奏云**（不是"慢一点"，是**测试结果依赖本机网速与对端限流**）；
2. `CrashReportExportJvmTest` 一条用例建 4 次 Activity，可能触发 4 轮探测；
3. 全量跑时是否触发**取决于 Gradle 的类发现顺序** —— 这类"顺序决定行为"正是
   DFW-113 记录的"门禁随机变红"的同款根因，只是这次表现为"随机联网"而不是"随机红"；
4. 在**没有网**的 CI runner 上它表现为"正常"，所以**在 CI 上永远发现不了** —— 只在开发机上咬人。

**建议怎么改**（任选其一，推荐第 1 条）：
1. **把守卫下沉到 `LanzouCore`**，与 `RemoteConfigClient` 共用同一个判据（`App.isJvmUnitTest()`，
   项目已经在 `App.java:158-163` 的注释里写明"判据只有这一处实现，不要在别处再抄一份"）——
   在 `resolveUserSource` / `probeSingleSource` 入口直接抛，和 `RemoteConfigClient.java:506` 同款。
   这样**任何**忘了设标志的类都绕不过去，安全边界变成结构性的。
2. 退一步：把 `LIBRARY_AUTO_IMPORT` 的默认值改成"看 `App.isJvmUnitTest()`"，
   即 `static volatile boolean LIBRARY_AUTO_IMPORT = !App.isJvmUnitTest();`
   —— 一处改动，44 个 `@BeforeClass` 就都可以删掉，也不再依赖执行顺序。
3. 加一条守卫测试：断言"未静音时启动 MainActivity 不得产生网络请求"，
   并把 5 个类补上静音。

---

### 1.3 【P1】`NavBallJvmTest` 6 条锚点能被「方法定义行」满足 —— 删掉调用照样绿

**位置**：`app/src/test/java/cc/nkbr/lanzouplus/NavBallJvmTest.kt`

讽刺的是，这个类的 KDoc（`:36-44`）**自己写明了这个坑**：

> 第一版用"从方法名起截 500 字符"来取 body，结果窗口越界到了**紧随其后的另一个方法定义**里，
> 于是 `body.contains("forceHidePills()")` 因为看到隔壁的方法签名而恒真——把调用删掉测试照样绿（假绿）。

并为此写了 `methodBody()`（`:45`）。但 **12 个 `@Test` 里只有 2 个用了它**（`:101`、`:118`），
其余仍是整文件 `source.contains(...)`。

**逐条证明**（锚点在 `NavBall.java` 里的全部命中位置）：

| 测试位置 | 断言 | `NavBall.java` 命中 | 删掉调用仍绿？ |
|---|---|---|---|
| `NavBallJvmTest.kt:89` | `source.contains("snapToEdge()")` | 定义 `:417`；调用 `:467`、`:479` | ✅ **是**（定义还在） |
| `NavBallJvmTest.kt:110` | `source.contains("pressFeedback(")` | 调用 `:147`；**定义 `:606`** | ✅ **是** |
| `NavBallJvmTest.kt:133` | `source.contains("forceHidePills()")` | 调用 `:220`、`:228`、`:581`；**定义 `:234`** | ✅ **是** |
| `NavBallJvmTest.kt:69` | `source.contains("persistPosition()")` | **定义 `:412`**；调用 `:424` | ✅ **是** |
| `NavBallJvmTest.kt:70` | `source.contains("loadPosition()")` | 调用 `:198`；**定义 `:406`** | ✅ **是** |
| `NavBallJvmTest.kt:142` | `source.contains("AI_SAFE_RATIO")` | **声明 `:61`**；唯一使用 `:393` | ✅ **是** |

（**反例**：`NavBallJvmTest.kt:111` 的 `source.contains("performHapticFeedback(")` 只命中
调用点 `NavBall.java:136`、`:468`、`:471`，删掉调用**会**红 —— 这条是**有效的**。
可见问题不是"用了 contains"，而是**锚点选到了定义行**。）

**它现在会漏掉什么真 bug**：把 `NavBall` 的持久化调用删掉、或者把 `AI_SAFE_RATIO`
的使用删掉、或者让菜单不再强制清理胶囊 —— 用户会看到**悬浮球位置丢失 / 挡住 AI 输入框 /
切页后胶囊残留**，而这 6 条测试**全绿**。

**建议怎么改**：剩下 10 个测试改用已经写好的 `methodBody("<签名>")` 再断言
（`NavBallJvmTest.kt:45` 的实现是对的：花括号配对 + `stripComments`）；
常量类锚点（`AI_SAFE_RATIO`）改成断言"使用点"而不是"名字出现"，例如断言
`NavBall.java` 里 `AI_SAFE_RATIO` 出现次数 `>= 2`。

---

### 1.4 【P1】一条"跨文件一致性"测试从不读另一个文件，且它钉住的行号**已经失效**

**位置**：`app/src/test/java/cc/nkbr/lanzouplus/SupportPageFeedbackJvmTest.kt:124-134`

```kotlin
assertTrue("必须在注释里写明这条规则抄自哪里（那个文件在动）", src.contains("MainActivity.solidShape"))
assertTrue("必须写明抄的是哪个版本的文件", src.contains("MainActivity.java:599"))
```

其中 `src` **只有** `SupportActivity.java`。两条断言命中的都是
`SupportActivity.java:486` 的一句注释：

> 量化规则**抄自站内 `MainActivity.solidShape`（HEAD 69bb1e3 的 MainActivity.java:599，

**两层问题**：
1. 测试注释声称"以后站内改了，这里能立刻看出来两边是不是还一致"，但**从不打开 `MainActivity.java`** ——
   站内改了、本页没改，测试**仍然绿**。真正的跨文件一致性（把 `solidShape` 的量化表达式抽出来逐字比对）
   没有做。
2. 被钉住的 `MainActivity.java:599` **已经不是** `solidShape`；实测在 `953eb26` 上
   `solidShape` 在 **`MainActivity.java:677`**（`grep -n "public GradientDrawable solidShape"`）。
   测试在给一条过期引用**背书**。

（补充准确：`:130` 那条**确实**逐字钉住了量化表达式 `radius>=24?26:...`，
但钉的是 `SupportActivity.java` 里**它自己那份副本** —— 与宿主是否分叉无关。
所以这条测试守的是"本页副本没被乱改"，**不是**它名字与注释声称的"两边是否还一致"。）

**它现在会漏掉什么真 bug**：两页圆角分叉 —— 用户会看到支持页的卡片圆角和全站不一样，
而这正是这条测试声称在守的东西。

**建议怎么改**：读 `MainActivity.java`，用 `methodBody("public GradientDrawable solidShape(int color,int radius)")`
抽出宿主规则，与 `SupportActivity` 的逐字比较；注释里的行号换成"方法名 + 提交号"
（行号必然随文件增长失效，不该进注释）。

---

### 1.5 【P2】用注释当「切片边界」—— 红绿都取决于注释

**位置**：`app/src/test/java/cc/nkbr/lanzouplus/TopBannerAndUpdateJvmTest.kt:115-116`

```kotlin
val branchEnd = after.indexOf("// 后台不可达")            // MainActivity.java:1279 的一句注释
assertTrue("后台分支必须能被切出来（找不到 GitHub 兜底那一行的注释）", branchEnd > 0)
val branch = after.substring(0, branchEnd)
```

后面两条断言（`:120` `branch.contains("return null;")`、`:124` `assertFalse(branch.contains("UpdateClient.check("))`）
**全部建立在这个切片上**。

**两个方向都坏**：
- 有人在 `MainActivity.java:1279` **之前**再写一句 `// 后台不可达` → `indexOf` 取到更早的位置 →
  切片被截短 → `:120` **假红**（代码没错，测试红了）；
- 兜底代码被挪到注释**之后** → `:124` 的 `assertFalse` **假红**；
- 反过来，把注释挪走但代码语义不变 → 红。

**建议怎么改**：边界换成**代码锚点**（例如那句 `if(...)` 语句本身），或直接按花括号配对取
`if/else` 分支体。**注释不能当边界。**

---

## 第 2 节：验证过是对的

这一节和上一节同样重要 —— 它是"真的找不到问题了"这句话的底气所在。

### 2.1 字面量自比（编译期恒真）：**0 条** ✅

全仓 855 条 `@Test` 扫描"两个操作数都是字面量的 `assertTrue/assertFalse`"，
**唯一候选全部是误报**，而且**两处历史假绿都已修好并写成了教训**：

| 曾经的假绿 | 现在的状态 |
|---|---|
| `"pointer-events: none".contains("pointer-events")` | 已抽成真守卫函数 `skinTouchesPointerEvents(src)`（`FeedbackEntryAndSkinJvmTest.kt:153-154`），并在 `:144-152` 的 KDoc 里**把这段假绿原文贴出来当反面教材** |
| `"...".contains("...")` | 同样抽成守卫函数（`StartupPermissionsAndAutoInstallJvmTest.kt:48-51`），教训写在 `:36-42` |

**验证方法**：先对源码做**等长空白化**去注释（保留偏移，行号不漂），再提取断言实参，
把字符串字面量归一成 `L` 后检查剩余标识符是否全是 `contains/Regex/true/false` 这类白名单词。
初版扫描把 `BrandCreditsJvmTest.kt:97` 的 `all.contains(...)` 误判成恒真 —— 原因是
我的白名单里混进了 `all`，而 `all` 恰好是那个类的运行时变量名（`textsOf(openAbout().root).joinToString`）。
**这条是真实的行为断言，不是假绿。**

### 2.2 异常被吞掉（`catch` 之后没有 `fail`）：**0 条** ✅

全仓 **11 处** `catch` 块不含 `fail()`，逐条回源后**全部合格**：

| 位置 | 判定 |
|---|---|
| `NetworkIsolationJvmTest.kt:28`、`:33` | `thrown = ...`，随后 `:78`/`:82` 断言 —— **合格**，且是全仓最规范的写法 |
| `UpdateThrottleJvmTest.kt:86` | `threw = true` 后被断言 —— 合格 |
| `UpdateOfferJvmTest.kt:114` | `try { 变异不可变集合; assertTrue(false) } catch (UnsupportedOperationException) {}` —— `AssertionError` 不被该 catch 吞掉，是**正确的**"不该走到这里"标记 |
| `ToolsSweepJvmTest.kt:55`、`:63` | 把错误追加进 `problems`，循环结束后 `throw AssertionError` —— 合格 |
| `LanzouSearchStallJvmTest.kt:50`（反射走父类）、`:118`（`failure.set(error)`） | 合格 |
| `AdbShellStateTest.java:247` | `Thread.currentThread().interrupt()` 还原中断位 —— 合格 |
| `SupportPageFeedbackJvmTest.kt:176` | 递归查找里 `catch { null }` —— 合格 |
| **`NoticeCenterJvmTest.kt:204`** | ❌ **不合格**，见 §1.1 |

（判据不是"catch 里有没有 `fail()`"，而是"**捕获到的值后续有没有被断言引用**" ——
按后者扫描，`NoticeCenterJvmTest` 是唯一命中。）

### 2.3 `NetworkIsolationJvmTest` 是全仓假绿防治的范本 ✅

`app/src/test/java/cc/nkbr/lanzouplus/NetworkIsolationJvmTest.kt:31-48` 的 KDoc 完整记录了
**lead 提到的那个坑**："第一版写成纯 JVM 测试 → 反向探针的红是 `org.json.JSONObject not mocked`
→ 桩一崩，`fetch()` 内部 `catch (Exception ignored)` 把它吞掉 → **第二条用例删掉守卫也照样绿**"。
它给出的结论是**必须挂 `RobolectricTestRunner`**（`:50-51`），并且用"报错信息必须带 `DFW-124` 标记"
（`:82`）来区分"**被守卫主动拒绝**"和"**碰巧连不上**" —— 这正是防止"没网时假绿"的正确做法。
**建议把这段判据推广**：任何"错误路径"测试都要能区分"**产品主动走了错误分支**"与"**环境碰巧不可用**"。

### 2.4 `ToolHostFixesJvmTest` 是「拒绝源码复读机」的范本 ✅

`app/src/test/java/cc/nkbr/lanzouplus/ToolHostFixesJvmTest.kt:27-28` 明确写着：

> 断言刻意打在"按钮文案集合"和"计圈行数"这类可观察事实上，**而不是去读源码文本**——避免测试变成源码的复读机。

实测该类的 **源码内容锚点为 0**：它遍历真实视图树收集 `TextView` 文案、`performClick()` 后数计圈行数
（`:93-108`）、断言清零后旧圈不残留（`:112-128`）。**这是本仓 UI 类测试的模板。**

### 2.5 源码内容锚点的真实规模：244 条 / 31 个类，**绝大多数是合理守卫** ✅

初版我用 `.contains(` 计数得到"765 条锚点"，那是**严重高估** —— 它把
`labels.contains("复制")`、`it.contains("圈")` 这类**运行时集合/字符串**断言也算了进去。
改成"接收者必须是读过文件的变量/函数"后：

| 类别 | 条数 | 判定 |
|---|---|---|
| **真正锚定文件内容** | **244**（31 个类） | 见下 |
| 运行时值断言（集合/字符串） | 其余 | **不是**源码锚点，是行为断言 |

244 条里，**大部分属于合理守卫**，理由逐类核过：

- `DocsConsistencyJvmTest`(27)、`DocTimelinessJvmTest`(12)、`ChangelogJvmTest`(7)、
  `DependencyHygieneJvmTest`(3)、`RepoHygieneJvmTest` —— **文档/依赖清单本身就是被测产物**，
  没有"运行时行为"可测，只能读文本。**合理。**
- `NetworkSecurityConfigJvmTest`(10)、`BrandingCleanlinessJvmTest`(5)、
  `StartupPermissionsAndAutoInstallJvmTest`、`ApkXmlNamespaceJvmTest` ——
  Manifest / `network_security_config.xml` / gradle 的声明，Robolectric **观察不到**。**合理。**
- `MotionCurveUniformityJvmTest`(2)、`MotionDurationScaleJvmTest`、`ThemeTokenContrastJvmTest` ——
  动效刻度与设计 token，属于"规范钉死"。**合理。**
- `FeedbackEntryAndSkinJvmTest`(8) —— 被测物是 **`assets/feedback_skin.js` 这个 JS 文件**，
  不执行 JS 就只能读文本。**合理**（真正的缺口是"没人执行过这个 JS"，见 §3.3）。
- `FolderLoadPolicyJvmTest`(19) —— **处理得最好的一类**：`methodBody()` 做花括号配对 +
  `stripComments()`（`:57-70`），注释在断言前已被剥掉，所以"旧代码在这里 `loading(...)`"
  这类注释**不会**造成假绿或假红。**合理。**
- `DfLogJvmTest`(15) —— 日志脱敏规则的真值表 + 源码锚点。**合理。**

**属于偷懒的只有少数**，已在 §1.3（`NavBallJvmTest` 6 条）、§1.4（`SupportPageFeedbackJvmTest` 2 条）、
§1.5（`TopBannerAndUpdateJvmTest` 1 条）逐条列出。

### 2.6 「锚点只出现在注释里」的 18 条命中，**15 条是我的扫描器误报** ✅

脚本报 18 条"锚点只命中注释"，逐条回源后：

| 位置 | 判定 |
|---|---|
| `FeedbackEntryAndSkinJvmTest.kt:92` | **误报** —— 扫描器把 `https://` 里的 `//` 当成了行注释 |
| `FeedbackEntryAndSkinJvmTest.kt:148`、`:154` | **误报** —— 命中的是 `web_dark_mode.js` 的注释，而被测文件是 `feedback_skin.js`；`:148` 本身还在 KDoc 里 |
| `FeedbackEntryAndSkinJvmTest.kt:197`、`:218`、`:226` | **误报** —— 命中的是 `WebDarkMode.java` / `LanzouWebActivity.java` 的注释，断言对象是**另一个文件** |
| `LanzouDownloadRootCauseJvmTest.kt:69`、`:85` | **误报** —— 断言对象是**运行时算出来的 `href`**，不是源码 |
| `NoticeSnapshotCacheJvmTest.kt:259` | **误报** —— 断言对象是 `MainActivity.java` 的切片，命中行在 `RemoteConfigClient.java` |
| `LanzouDownloadRootCauseJvmTest.kt:155`、`:156` | **合理** —— 用例名就叫 `theStaleAlgorithmIsDocumentedAsBroken`，**故意**断言文档写了这件事 |
| `FolderLoadPolicyJvmTest.kt:212`、`:229`、`:250` | **合理** —— `methodBody()` 已 `stripComments()`，注释不会参与断言 |
| `PageTransitionMotionJvmTest.kt:114` | **有意为之** —— `:111` 的注释写明"注释在 methodBody 里被剥掉了，所以这里查原始源码"，是**文档守卫**。但依赖 `at - 1400` 这个魔法窗口，脆弱 |
| **`SupportPageFeedbackJvmTest.kt:132`、`:133`** | ❌ **真问题**，见 §1.4 |
| **`TopBannerAndUpdateJvmTest.kt:115`** | ❌ **真问题**（切片边界），见 §1.5 |

**教训**：这类扫描的误报率很高（本次 18 报 15 误报），**任何一条都必须回源确认才能进报告**。

### 2.7 其它验证过的项 ✅

- **`:app` 0 跳过** —— 无 `@Ignore`、无 `assumeTrue` 造成的静默失效。
- **`TimeZone.setDefault` 只有 1 处**（`ToolboxAlgorithmsJvmTest.kt:71`），且 `:74-77` 的 `@After`
  有还原 —— 不会污染其它测试。
- **`System.setProperty` / `Locale.setDefault` / `System.setOut` 零命中** —— 没有这类全局污染。
- **`@FixMethodOrder` 零命中** —— 没有测试显式依赖方法执行顺序。
- **`Thread.sleep` 11 处**，集中在 `MainThreadStallWatchdogJvmTest`（看门狗，必须真等）与
  `DownloadHistoryStoreTest`（文件时间戳），**属合理**；但它们是全量跑变慢与偶发红的来源之一。

---

## 第 3 节：假绿清单（每条附证明）

| # | 位置 | 证明（为什么它不可能红） | 漏掉什么 |
|---|---|---|---|
| 1 | `NoticeCenterJvmTest.kt:193-207` | 方法体内 `assert\w*\(|fail\(|assertThat\(|Assertions\.` **全部不匹配**，且无 `throw`；`try/catch` 让抛与不抛都正常结束 | `degradesToUnread` 契约零覆盖（§1.1） |
| 2 | `NavBallJvmTest.kt:89` | 锚点 `snapToEdge()` 在 `NavBall.java` 的**定义行 `:417`** 即满足 | 删掉 `:467`/`:479` 调用 → 悬浮球不再贴边，绿 |
| 3 | `NavBallJvmTest.kt:110` | 锚点 `pressFeedback(` 由**定义 `NavBall.java:606`** 满足 | 删掉 `:147` 调用 → 菜单项无按压反馈，绿 |
| 4 | `NavBallJvmTest.kt:133` | 锚点 `forceHidePills()` 由**定义 `NavBall.java:234`** 满足 | 删掉 `:220`/`:228` → 切页胶囊残留，绿 |
| 5 | `NavBallJvmTest.kt:69` | 锚点 `persistPosition()` 由**定义 `NavBall.java:412`** 满足 | 删掉 `:424` 调用 → 悬浮球位置不持久化，绿 |
| 6 | `NavBallJvmTest.kt:70` | 锚点 `loadPosition()` 由**定义 `NavBall.java:406`** 满足 | 删掉 `:198` 调用 → 位置不恢复，绿 |
| 7 | `NavBallJvmTest.kt:142` | 锚点 `AI_SAFE_RATIO` 由**声明 `NavBall.java:61`** 满足 | 删掉 `:393` 使用 → 悬浮球挡住 AI 输入框，绿 |
| 8 | `HomeShotsJvmTest.kt:60`、`:80`、`:97` | 方法体内无断言；判据只有"渲染时没抛异常" | 画面回归（截断/重叠/错位）——**这类测试不产生回归信号**，只产出 PNG 供人工目检 |
| 9 | `ToolsShotsJvmTest.kt:50` | 同上 | 同上 |
| 10 | `SupportPageFeedbackJvmTest.kt:378-381` | 方法体只有 `a.pressScale(null)`，无断言 | `pressScale` 传 null 时若**改了别的状态**（例如把 scale 置 0），绿 |

> **关于第 8/9 条的分寸**：这两个类的类注释**已经比较诚实**（写明是"视觉验收/截图"），
> 属于**建议**而非缺陷。真正的风险是它们在"我们有 855 条守卫"的叙事里被当成保护 ——
> 建议在类注释里明确写"**本类不产生回归信号，仅供人工目检**"，或改名加 `Shot`/`Capture` 前缀。

---

## 第 4 节：无法静态判定、需要跑的

| # | 要跑什么 | 验证什么 | 预期 |
|---|---|---|---|
| 1 | `bash tools/run-tests.sh --class SearchPageBudgetJvmTest`（**先开抓包或看 logcat / 用 `ss -tn` 看连接**） | §1.2：单跑是否真的对蓝奏云发起请求 | 若看到到 `*.lanzou*.com` / `<你的服务器>` 的连接 → **实锤**。也可临时把 `LIBRARY_AUTO_IMPORT` 默认改 `false` 再跑一次做对照 |
| 2 | 对 §1.2 的 5 个类各单跑一次，比对耗时 | 联网路径是否存在（联网的类会明显慢） | 单跑耗时 vs 全量跑该类的耗时差异 |
| 3 | 把 `NoticeCenter.unreadCount` 改成"读失败返回 0"，跑 `NoticeCenterJvmTest` | §1.1 的假绿是否真的无鉴别力 | **预期仍然全绿**（这就是证明） |
| 4 | 删掉 `NavBall.java:220`/`:228` 的 `forceHidePills()` 调用，跑 `NavBallJvmTest` | §1.3 的 6 条锚点 | **预期仍然全绿** |
| 5 | 给 §1.3 那 6 条锚点各做一次反向探针（把定义行改名） | 它们是否真的只被定义满足 | **预期仍绿**（改名后 `contains` 失败 → 红，说明确实只匹配名字） |
| 6 | 在 `MainActivity.java:1279` 的注释**之前**再加一句 `// 后台不可达`，跑 `TopBannerAndUpdateJvmTest` | §1.5 的脆弱性 | **预期 `:120` 假红** |
| 7 | `bash tools/run-tests.sh --rerun` 连跑 3 次 | 是否有残留的顺序依赖（`forkEvery=24` + 静态标志） | 若某次结果不同 → 顺序依赖确认 |
| 8 | `bash tools/run-tests.sh --class NoticeFlowJvmTest` 连跑 10 次 | DFW-124 的随机红是否真的根治 | 类注释声称曾 6 次红 1 次 |

**我没跑任何一条** —— lead 明确要求不要跑 gradle。

---

## 第 5 节：盲区（我没来得及看的）

1. **`rikkahub/`（vendor 区）的 DFWX PATCH 层**完全没审。`rikkahub/app` 有 289 条测试，
   但 PATCH 层与上游测试的对应关系、以及"上游代码被 DFWX 改动后上游测试还成不成立"没有查。
2. **`MainActivity.java`（4971 行）的 155 个 `catch`** 只做了数量统计，没有逐个核对
   "这个错误分支有没有测试覆盖"。这是最大的单点盲区。
3. **`LanzouCore.java`（1918 行）的 101 个 `catch`** 同上。本次只查了它的联网路径。
4. **`ToolHost.java`（1558 行）的 23 个 `catch`** 同上。
5. **UI 布局回归**：`HomeShotsJvmTest` / `ToolsShotsJvmTest` 产出的 PNG 没有做像素级基线比对，
   所以"视觉回归"目前是**人工目检**而非自动守卫。
6. **权限拒绝 / 磁盘满 / 通知点击 / 深链**这些系统级路径：
   `StartupPermissionsAndAutoInstallJvmTest` 覆盖了 Manifest 声明与自动安装的部分，
   但**权限被拒绝后的降级行为**没有看到运行时断言。
7. **`WafCookieSolver.java`（217 行，6 个 catch）** 的算法正确性：`LanzouDownloadRootCauseJvmTest`
   断言了"旧算法已算错"这件事**被文档记录**，但没有断言新算法**算对**（因为它依赖 WebView 求解，
   JVM 里跑不了）。
8. **`DfLog.java`（593 行）的落盘失败路径**（磁盘满）只有源码锚点，没有真实 IO 失败的运行时验证。
9. 本次没有对**测试自身的运行时行为**做动态验证（全部是静态分析 + 回源）—— 
   凡是标注"需要跑"的都在第 4 节。

---

## 附：本次用到的判据（可复现）

> 1/2/3 三个扫描脚本写在 `/tmp` 下（本次会话的临时文件，**不保证还在**）。
> 判据本身在正文里已逐条写清（§2.1 字面量自比、§2.5 真源码锚点、§2.6 注释锚点），
> 需要复现时按正文描述重写即可；4/5/6 是直接可用的命令。

```bash
# 1) 字面量自比：先把注释做等长空白化（行号才不漂），再检查断言实参是否只剩字面量
python3 /tmp/scan2.py

# 2) 真源码锚点：接收者必须是"读过文件的变量/函数"（否则 labels.contains("复制") 会被误算）
python3 /tmp/anchors.py

# 3) 锚点只命中注释：命中行 ∈ 注释 且 ∉ 去注释后的源码
python3 /tmp/comment_only.py

# 4) 静态标志泄漏面
grep -rn "LIBRARY_AUTO_IMPORT" app/src/main/java app/src/test | grep -v "= false"

# 5) 联网路径是否被守卫
grep -c "isJvmUnitTest" app/src/main/java/cc/nkbr/lanzouplus/LanzouCore.java     # → 0
grep -rn "isJvmUnitTest()" app/src/main/java/cc/nkbr/lanzouplus/ | grep -v "static boolean"

# 6) fork 批次
grep -n "forkEvery" app/build.gradle.kts                                        # → :122 forkEvery = 24
```
