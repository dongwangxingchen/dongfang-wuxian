# DFW-36：失效源识别、提示与反馈 —— 实现方案

> 调研日期：**2026-10-03**。基线：分支 `test`，HEAD `f832ca6`。
> 性质：**只读调研**，本文不含任何代码改动；`MainActivity.java` 有他人未提交改动，本文只引用、不触碰。
> 所有结论给 `文件:行号`。凡本文与源码冲突，以源码为准。
>
> **一句话结论**：这个应用**已经知道**是哪个源失败了，但它**把这个源名丢掉了**，
> 而且它把至少 **4 类与源无关的故障**统一显示成同一句「该源已失效或跳转异常」——
> 这句话在 v1.0.5→v1.0.19 期间被反复证伪。所以本卡的正确解法不是"加一个探测"，
> 而是**把已有的证据接上、把文案按证据分层**；"源失效"必须是**最后一个格子**的结论。

---

## 1. 现状：某个源挂了的时候，代码实际会怎样、用户看到什么

### 1.1 先分清两层"源"（很多讨论把这两层混了）

| 层 | 是什么 | 证据 |
|---|---|---|
| **域名池（10 个）** | 蓝奏的镜像域名，用于换线/拼直链镜像 | `LanzouCore.java:104`（数组），池内条目见 `:110`，取用入口 `baseOrigins()` `:115` |
| **内置源（84 个）** | 软件库清单，每行 `标题<TAB>URL[<TAB>密码]`，共 85 行 | `app/src/empty/assets/r`（85 行），由 `LanzouCore.recommendations()` `LanzouCore.java:1003` 读取 |

canonical 域名是 `https://oreojiang.lanzout.com`：`LanzouCore.java:111`（`CANONICAL_SOURCE_ORIGIN`）。
卡片里说的"84 源"与 `assets/r` 行数一致；卡片的"50 源要密码"与 `LanzouUnlockFieldsJvmTest.kt:11` 的记载一致。

### 1.2 全源搜索时，单个源失败 → **源名和原因都被丢弃**

这是本卡真正的现状根因，只有两行：

```java
// MainActivity.java:3839
@Override public void onFailure(String source){synchronized(globalSearch){if(session==searchGeneration)globalSearch.failures++;}}
```

- 契约在 `Models.java:131`：`default void onFailure(String current) {}`（属于 `Models.Progress`，接口起于 `Models.java:112`）。
  **回调把源名传出来了**（参数名 `current`），调用处却**只 `failures++`，`source` 变量从未被使用**。
- 唯一展示在 `MainActivity.java:2844`（`refreshSearchUi`）：
  `right = ... globalSearch.failures==0 ? "已完成" : "已完成 · "+globalSearch.failures+" 源异常"`。
  **用户拿到的是一个数字**：不知道是哪个源、不知道为什么、点不开、重试不了。

结论（证据等级 A）：**失败是"可观测"的，但观测结果是"一个整数"。** 本卡要先修的就是这个断点，不是加探测。

### 1.3 整体搜索炸掉时 → 用户看到的是那句被证伪过的话

```java
// MainActivity.java:3845
}catch(Exception e){if(session==searchGeneration){synchronized(globalSearch){globalSearch.failures++;}finishSearchJob(session);showNotice(friendlyError(e),true);}}
```

`friendlyError(Throwable)` 是**全站唯一的异常→人话出口**（注释在 `MainActivity.java:770-775`，
明确写着"47 个调用点都从这里过"，并在 `:777` 用 `DfLog.failure("ui","user-visible-error",error)` 落日志）。
它的实现体在 **`MainActivity.java:778`**，是一条中文子串级联。

其中最关键、也是本卡要拆掉的那一条：

```java
if(raw.contains("不受信任")||raw.contains("跳转"))return "该源已失效或跳转异常";
```

### 1.4 这句话已经被证伪过四次（本卡最重要的事实）

| # | 真根因 | 证据 | 用户看到 |
|---|---|---|---|
| ① | 蓝奏**官方把下载 API 迁到** `api.ilanzou.com`，而可信域名正则 `(?:lanzou[a-z0-9]?\|lanzov\|lanzn)[.]com` 匹配不到 `ilanzou` → `requireLanzouPage()` 抛"跳转到了不受信任的地址" | DFW-103，commit `7044bff`；守卫测试 `LanzouHostAllowlistJvmTest.kt:9-25` | 「该源已失效或跳转异常」（用户从 v1.0.5 报到 v1.0.14） |
| ② | DFW-102 加了"https 失败降级 http"，但 `requireLanzouPage()` **只认 https**，降级后的 http 当场被自己拒掉 | DFW-104，commit `25614aa`（提交信息里有完整因果链） | 同上**换汤不换药**：「只是把 TLS 失败换成了这个错误」 |
| ③ | 域名池里 `wwc.lanzoux.com` **证书已过期** → TLS 握手失败 | DFW-88，commit `820dbda`；完整现场记在 `LanzouCore.java:104` 的注释里 | 「无法解析下载链接」 |
| ④ | 阿里云 WAF **滑块**验证页（`captchaV2`）→ `requireActiveShare()` 抛 `PageCapabilityException` | DFW-107，commit `7ef92a2`；DFW-108 `docs/plan/dfw-108-waf-challenge.md`（社区 9 仓库 0 命中，纯算法无解） | 解析失败 / 该源异常 |

**这四次没有一次是源的问题。** 这正是卡片铁律的由来。所以本方案的硬约束是：
**「源失效」只能由"分享已取消"这类直接证据得出**（见 §2）。

### 1.5 已经有、但没被用上的基础设施（这是好消息）

这个项目**不缺探测能力**，缺的是把结果接到 UI 上：

| 已有能力 | 位置 | 现状 |
|---|---|---|
| 10 个域名并行探测，带并发自适应 | `LanzouCore.probeBaseOrigins()` `:116` | **只在"基础链接"对话框里用** |
| 单域名探测，含 UA 学习 | `probeBaseOrigin()` `:120` | 同上 |
| 启动时探测 canonical 域名 | `probeStartupBaseOrigin()` `:123-124`，UI 在 `MainActivity.java:3820` | 已接线：弹「基础链接连接异常」+ 建议测速（`:3820` 原文） |
| **探测结果三态** | `BaseOriginProbe{origin, latencyMillis, available, challenge}` `LanzouCore.java:325`；进度回调 `:326` | 已用：对话框显示 `可用 / UA 验证 / 不可用 · N ms`（`MainActivity.java:5095`） |
| **每源 × 每 UA 的学习状态** | `SourceProfile` `LanzouCore.java:260`：`directoryUa/searchUa/directUa`、`directorySeen/directoryAvailable`、`searchSeen/searchAvailable`、`androidTemplate/desktopTemplate` | **已采集，未呈现** |
| **路由候选含 UA 维度** | `RouteCandidate{url,origin,ua,scope}` `:255`，`key()=origin+'\n'+ua`；生成 `routeCandidatesForUa()` `:149`；竞速 `raceRoutes()` `:1165` | 已在跑 |
| 每次路由尝试的结果（含失败的异常） | `RouteOutcome<T>{route,value,error}` `:259` | **异常被消费后丢弃，没留档** |
| UA 探测结果（含耗时/条数） | `UaProbe{folder,session,error,elapsed,items,directory,search,ua}` `:276` | 内部用 |
| **按源复测**（用户主动触发） | `LanzouCore.retestSources()` `:949` / `:951`；结果模型 `Models.SourceTestResult{source,originalUrl,success,userSource,applied}` `Models.java:35-40`；进度 `Models.SourceTestProgress` `Models.java:41-44`；UI `MainActivity.retestSelectedSources()` `:3994` | 已接线，但**只报"通过/失败"，不带原因** |
| 全站用户可见错误的落盘通道 | `DfLog.failure("ui","user-visible-error",error)` `MainActivity.java:777` | **有证据，但只进日志、不回 UI** |

### 1.6 异常分类学：能区分四类失败的信号**已经存在**

`LanzouCore` 里已经有一批**语义明确**的异常类型——这是本方案的地基（全部在 `LanzouCore.java`）：

| 异常 | 行号 | 携带信息 | 它其实在说什么 |
|---|---|---|---|
| `ShareCancelledException` | `:277` | 消息 `"分享已取消"` | **唯一能证明"这个源真的没了"的信号** |
| `WafChallengeException` | `:300-305` | `challengeUrl` | 蓝奏要人机验证（**不是源坏**） |
| `PageCapabilityException` | `:280` | 消息文本 | **当前 UA 打不开**（抛出点 `:1796`） |
| `PageRateLimitedException` | `:278` | 消息 | 限频（**不是源坏**） |
| `RecoverableBrowseException` | `:279` | 消息 | 可恢复 |
| `PageWaitException` | `:307` | `delayMillis` | **我们自己的调度在排队**（不是网络慢） |
| `DirectRetryException` | `:245` | `retryAfterMs`, `rateLimited` | 直链需重试/被限流 |
| `DirectPasswordException` | `:246` | 消息 | 需要密码 |
| `SocketTimeoutException` | `:500`（`directRemainingMillis`） | — | 真的超时 |
| `SearchCancelled` | `:335` | — | 用户取消 |

页面级判据同样已有：`isUnavailableShareShell(html)` `:1794`（含 `403`/`404`/`router.parklogic.com`/`share/404a.css` 等特征）、
`requireActiveShare(page)` `:1796`（按"分享已取消 → WAF → 可解析 → 不可用壳"的顺序判定）。

**所以：四类失败的判别不需要新算法，只需要把已有异常/判据映射成一个带语义的枚举，并让它活着走到 UI。**

---

## 2. 怎么可靠区分这四件事

### 2.1 判别的总原则

> **"源失效"永远排在最后；前面的格子没排干净，就不许下这个结论。**

### 2.2 判定矩阵（每一格都写清"唯一可靠证据"和"现有信号"）

| 结论 | 唯一可靠证据 | 现有信号（可直接复用） | 绝不接受为证据 |
|---|---|---|---|
| **A. 蓝奏整体挂了** | 同一时刻、**多个不同域名**、**同一 URL 模式**全部失败（TLS/连接/5xx/超时），而本地网络本身正常 | `probeBaseOrigins()` `:116` 天然就是这个矩阵（10 域名并行）；`BaseOriginProbe.origin` `:325` | "某个源打不开"；单域名失败 |
| **B. 某域名被拉黑（UA 问题）** | **同一域名 + 同一 URL**，换 UA 成功（默认 UA 失败 → 学习到的 UA 成功） | `RouteCandidate{origin,ua}` `:255` 的 `key()`；`SourceProfile.directoryUa/searchUa` `:260` 学习值；`UaProbe{ua,error}` `:276` | 只试过一个 UA 就断定"域名坏了" |
| **C. 这个源真的失效** | `ShareCancelledException`（`:277`）**或** `isUnavailableShareShell()`（`:1794`）明确命中，**且**在 ≥2 个不同 UA 下一致；**且**跨 ≥3 次、跨时间 | `ShareCancelledException` 已存在；`isUnavailableShareShell` 已存在 | 超时、TLS 失败、WAF、限频、`PageCapabilityException`（**这四类都是 A/B/D**） |
| **D. 只是网络慢** | 有字节/页在推进；或 `PageWaitException.delayMillis>0`（`:307`，是**我们的调度**）；或同一请求在时限内成功 | `BaseOriginProbe.latencyMillis` `:325`；`UaProbe.elapsed` `:276`；`PageWaitException.delayMillis` `:307` | 一次 `SocketTimeoutException` |

### 2.3 必须遵守的判别顺序（禁止跳步）

```
第 0 步  排除"我们自己"：PageWaitException(:307) / SearchCancelled(:335) / 用户暂停
         → 这些根本不是失败，不能计入"源异常"
第 1 步  排除"限频与验证"：PageRateLimitedException(:278) / DirectRetryException.rateLimited(:245)
         / WafChallengeException(:300)  → 可动作，不是源坏
第 2 步  排除"域名/地址层"：requireLanzouPage 的"不受信任"、TLS 失败、isUnavailableShareShell(:1794)
         → 这是 A 或 B，**绝不许落到 C**
第 3 步  排除"UA 层"：PageCapabilityException(:280)，或换 UA 后成功
         → 这是 B，**绝不许落到 C**
第 4 步  只有到这里，且拿到 ShareCancelledException(:277) / 明确的取消分享壳
         → 才允许说"这个源打不开了"，而且仍然**不许自动删源**
```

### 2.4 交叉矩阵怎么"零成本"拿到

关键洞察：**不要新增探测请求**。搜索过程本来就在跑 `routeCandidatesForUa()` `:149`
和 `raceRoutes()` `:1165`，每一次尝试都是一个 `RouteOutcome{route,value,error}` `:259`。
把**已经产生的** `RouteOutcome` 按 `route.key()`（= `origin+'\n'+ua`，`:255`）归一下档，矩阵就有了。

多 UA 的额外交叉**只在用户主动点"重试这个源"时做**——`retestSources()` `:949` 已经是这个入口，
UI 是 `retestSelectedSources()` `:3994`。

---

## 3. UI 上怎么表达

### 3.1 分层：让用户"知道"，而不是"读诊断报告"

| 层 | 触发条件 | 位置 | 文案方向 |
|---|---|---|---|
| **L1 源级标记** | 单个源失败 | 源列表条目 + 搜索状态行 | 条目名字变灰 + 小标记；状态行 `已完成 · N 源异常` **变成可点**（`MainActivity.refreshSearchUi()` `:2844`） |
| **L2 可点开的清单** | 用户点 L1 | 复用 `MainActivity.retestSelectedSources()` `:3994` 已有的进度面板/对话框形态 | 每行：`源名 · 一句话原因 · [重试这个源]` |
| **L3 全局横幅** | **只在 §2.2-A 成立时**（多域名同时失败） | 复用 `MainActivity.java:3820` 已有的「基础链接连接异常」对话框形态 | 「蓝奏整体连不上，不是你的源坏了」+ 建议测速（`:3820` 原文已是"建议立即测速并更换可用基础链接"，保持"建议+用户点"） |

### 3.2 文案红线（这是本卡的核心交付）

| 场景 | ✅ 可以说 | ❌ 不可以说 |
|---|---|---|
| UA 被拉黑 | 「当前方式打不开，正在换一种方式重试」 | 该源已失效 |
| 域名不受信任 / TLS 失败 | 「地址不受信任（**可能是蓝奏换了域名，不是这个源坏了**）」 | 该源已失效或跳转异常 |
| WAF 验证 | 「蓝奏要求人机验证，需要你过一下」+ 可动作入口（DFW-108） | 该源已失效 |
| 限频 / 排队 | 「请求太快/正在排队，稍后自动继续」 | 该源异常 |
| 超时（未超次数） | 「网络较慢，正在继续」 | 该源异常 |
| **只有拿到取消分享证据** | 「这个源已经打不开了（分享已取消）」 | —— |

**硬性要求**：`MainActivity.java:778` 那条
`if(raw.contains("不受信任")||raw.contains("跳转"))return "该源已失效或跳转异常";`
**必须拆开**——"不受信任/跳转"要单独成一条，文案里**不许出现"失效"二字**。

### 3.3 要不要「反馈」入口

**要，但只做"一键复制诊断信息"，不做新后端字段。** 理由（每条都有本仓依据）：

1. 本项目已有可导出的本地日志与崩溃报告（`SECURITY.md:34-36`：崩溃日志含版本/设备/系统/ABI/内存，
   **不含 URL、密码或任何凭据**），"复制诊断"是在既有能力上加一个按钮，**零新基础设施**。
2. 用户是单人使用场景，**最快的闭环是把诊断文本粘给 AI**，而不是建一个后端集合再写一个管理页。
3. 反馈通道一旦上传，就要面对 `decisions.md:23`（第 11 条）的凭据红线与 `decisions.md:82-84`（后台改动）的额外维护面。
4. 卡片的目标原文是"用户可在设置反馈源名"——**"复制诊断信息"已满足这个目标**，且更强（带证据，不只是源名）。

诊断包**只允许**包含：源标题、域名、UA 编号、HTTP 状态码、异常**类名**、累计失败次数、时间戳。
**禁止**包含：完整 URL、提取码/密码、Cookie（含 `acw_sc__v*`）、响应体全文。

---

## 4. 实现方案

> 原则：**改动面尽量小、尽量加而不改、尽量复用 §1.5 已有的东西**。
> 下面写"改哪个方法/加什么"，**不写 diff**（`MainActivity.java` 当前有他人未提交改动，由执行者落地）。

### 4.1 新增：失败语义类型（纯数据，JVM 可测，无 Android 依赖）

放 `Models.java`（该文件已是纯模型层）：

```java
enum FailureKind { SELF_PACED, RATE_LIMITED, WAF_CHALLENGE, ORIGIN_UNTRUSTED,
                   UA_BLOCKED, SLOW, SHARE_CANCELLED, UNKNOWN }

static final class SourceFailure {
  final String sourceId, sourceTitle;
  final FailureKind kind;
  final String headline;      // 给用户看的一句话（见 §3.2 文案表）
  final String evidence;      // 给诊断包用：异常类名 + HTTP 状态 + 试过的 (origin,ua) 列表
  final boolean userActionable;// true 时 UI 给按钮（去过验证/重试）
  final long atMillis;
}
```

### 4.2 新增：纯函数分类器

`LanzouCore.classifyFailure(Throwable error)` → `FailureKind`，**无副作用、无网络、无 Android 依赖**。
映射表直接照 §1.6 的异常分类学：

- `ShareCancelledException`(`:277`) → `SHARE_CANCELLED`
- `WafChallengeException`(`:300`) → `WAF_CHALLENGE`（`userActionable=true`）
- `PageRateLimitedException`(`:278`) / `DirectRetryException.rateLimited`(`:245`) → `RATE_LIMITED`
- `PageWaitException`(`:307`) / `SearchCancelled`(`:335`) → `SELF_PACED`（**不计入失败**）
- `PageCapabilityException`(`:280`) → `UA_BLOCKED`
- `"不受信任"/"跳转"`（`requireLanzouPage` 抛出）→ `ORIGIN_UNTRUSTED`  ← **绝不映射成 SHARE_CANCELLED**
- `SocketTimeoutException`(`:500`) → `SLOW`
- 其他 → `UNKNOWN`（**不允许默认成"源失效"**）

### 4.3 改：把失败原因送到 UI（只加不改，零破坏性）

1. `Models.java:131` 附近**新增**一个 default 方法（保留 `onFailure` 不动，避免破坏既有实现）：
   `default void onSourceFailure(SourceFailure failure) {}`
2. `MainActivity.java:3839`：`onFailure(String source)` 里**除 `failures++` 外**，把 `source` 存进
   `globalSearch` 的新字段（如 `LinkedHashMap<String,SourceFailure> failureReasons`）。
   **这是本卡最关键的一处改动**：现在 `source` 变量被完全丢弃。
3. 采集原因的挂点：`LanzouCore` 内部源搜索收尾处（`finishSourceLocked(SourceSearchState, boolean failed)` `:403`）
   —— 那里已经是"一个源判定终结"的唯一位置，把 `state` 里已有的 `Exception` 过一遍 `classifyFailure` 即可。

### 4.4 改：UI 接线（3 处，均为小改）

| 位置 | 现状 | 改成 |
|---|---|---|
| `MainActivity.refreshSearchUi()` `:2844` | `"已完成 · N 源异常"`（纯文本） | 同一个字符串，但状态行**可点**，点开 L2 清单 |
| `MainActivity.retestSelectedSources()` `:3994` | `(ok?"通过":"失败")` | `(ok?"通过":failure.headline)` |
| `MainActivity.friendlyError()` `:778` | `"不受信任"/"跳转"` → 「该源已失效或跳转异常」 | 拆成 `ORIGIN_UNTRUSTED` 专用文案（§3.2） |

### 4.5 明确不做的实现细节

- **不新增任何网络探测请求**（复用 `RouteOutcome` `:259` 与 `retestSources()` `:949`）
- **不改 `onFailure` 的签名**（只新增 default 方法）
- **不在 `MainActivity` 里写新逻辑**（分类器放 `LanzouCore`/`Models`，`MainActivity` 只接线）
- **不自动删源、不自动禁用源、不自动换域名**（见 §5）

### 4.6 测试方案

统一用 `bash tools/run-tests.sh`（`AGENTS.md` 第四节：不要直接敲 `./gradlew`）：

| 测试 | 类型 | 断言什么 |
|---|---|---|
| `SourceFailureClassifyJvmTest` | 纯 JUnit，**不需要 Robolectric** | 把 §1.6 每个异常类型喂进 `classifyFailure`，断言 kind 一一对应 |
| `SourceFailureNoFalsePositiveJvmTest` | 纯 JUnit + 源码断言 | **本卡最重要的守卫**：① `ORIGIN_UNTRUSTED` / `UA_BLOCKED` / `WAF_CHALLENGE` / `SLOW` / `RATE_LIMITED` 的输出文案里**不含"失效"二字**；② `friendlyError()` 源码里不存在"不受信任"直通"已失效"的路径 |
| `SourceFailureEvidenceJvmTest` | 纯 JUnit | `SourceFailure.evidence` 含异常类名与 (origin,ua) 列表，且**不含 URL 全文/密码/Cookie** |

**测试必须验鉴别力**（本仓既有做法，见 `current-state.md:114`）：把分类器故意改坏
（例如让 `PageCapabilityException` 返回 `SHARE_CANCELLED`），测试必须变红。

**测试禁区**（有血泪教训）：`MainActivity.java:414-422` 记载，
`SearchPageBudgetJvmTest` 单跑会经 `MainActivity:349` → `addUserSourcesBatch` → `probeSingleSource`
**对 85 个源发真实网络请求**。所以新测试**一律不碰 `MainActivity` 的导入路径**，
或显式走 `isJvmUnitTest` 闸门。

---

## 5. 风险：哪些做法会把好源误判成坏源

这是本卡最容易翻车的地方（卡片明令"禁止删源"，且历史误判代价极高）。逐条列，**每条都配防护**：

| # | 误判做法 | 为什么必然会误判 | 防护 |
|---|---|---|---|
| **R1** | 把 `PageCapabilityException`(`:280`) 当源失效 | 它的原意就是"**当前 UA** 打不开"（抛出点 `:1796` 原文）；这正是历史双根因之一（UA 被 CDN 拉黑，影响全部 84 源） | 该异常**永远只能**产生 `UA_BLOCKED`；分类器里显式断言 |
| **R2** | 把"不受信任/跳转"当源失效 | **已证伪 4 次**（DFW-103/104/88/107，§1.4）。DFW-103 是官方迁域名、DFW-104 是我们自己的修复造成的 | 单独 `ORIGIN_UNTRUSTED`，文案写"可能是蓝奏换了域名，**不是这个源坏了**" |
| **R3** | 把 WAF 滑块当源失效 | DFW-108 证实是**社区未解**问题、必须人工过验证；`docs/plan/dfw-108-waf-challenge.md` 记载用户 v1.0.5→v1.0.19 一直失败 | `WAF_CHALLENGE` 必须**可动作**（引导去过验证），不是"源坏了" |
| **R4** | 把限频/排队当源异常 | `PageRateLimitedException`(`:278`) 与 `PageWaitException`(`:307`，"目录请求等待调度")**都是我们自己的调度或对端的限流**，与源无关 | 两者都**不计入** `failures`（现状 `MainActivity.java:3839` 是无差别 `++`，这本身就要改） |
| **R5** | 单次失败就判定失效 | 一次超时/一次 WAF 什么都不能证明 | 必须**跨 ≥3 次、跨时间、≥2 个 UA 一致**才升级为"疑似失效"；且**永不自动删源** |
| **R6** | 用"探测失败"当结论 | DFW-36 卡片评论已写死："先建可观测的探测……**跳过第 1 步就会重演上次的误判**" | 探测必须记 **HTTP 状态 / UA / 请求字段 / 响应体特征** 四元组，不许只记成功失败 |
| **R7** | 为了"多试几个"而**换域名** | DFW-106（commit `3abc671`）采到社区源码的明确约束：**"蓝奏风控 Cookie 与域名关联，强制换域名会导致校验失败"** | 交叉探测**只换 UA，不换域名**；不新增任何换域名逻辑 |
| **R8** | 整体挂了就**自动**换基础链接 | 会擅自改用户设置，且 `MainActivity.java:3820` 现有设计就是"建议立即测速并更换"（**建议 + 用户点**） | 保持"建议 + 用户决定"，不自动改 |
| **R9** | 在 JVM 测试里触发真实网络 | `MainActivity.java:414-422` 记录了 `SearchPageBudgetJvmTest` 单跑会对 85 个源发真请求（DFW-124 的第二条联网路径） | 新测试限纯函数级；必要时走 `isJvmUnitTest` 闸门 |
| **R10** | 诊断包泄漏凭据 | `decisions.md:23`（第 11 条）红线；`SECURITY.md:34-36` 已定崩溃日志标准 | 白名单字段（见 §3.3），不含 URL 全文/密码/Cookie |
| **R11** | 为了让标记"看起来有用"而**提前变灰** | 误判一次用户就会去删源（卡片最怕的后果） | 标记只在 §2.3 第 4 步之后出现；未定论时显示"正在确认"而非"不可用" |

---

## 6. 落地顺序（建议）

| 步 | 做什么 | 为什么这个顺序 | 验收 |
|---|---|---|---|
| 1 | `FailureKind` + `SourceFailure` + `classifyFailure` + `SourceFailureClassifyJvmTest` | 纯函数、零风险、先行可测；**不碰 UI** | 分类器测试全绿且验过鉴别力 |
| 2 | 把 `source` 与异常接进 `globalSearch`（§4.3） | 修掉"源名被丢弃"这个真根因 | 单测断言失败后能取到源名与 kind |
| 3 | 拆 `friendlyError()` `:778` 那条映射（§4.4） | 直接消灭那句被证伪 4 次的话 | `SourceFailureNoFalsePositiveJvmTest` 绿 |
| 4 | L1/L2 UI（可点状态行 + 清单 + 逐源重试） | 用户价值在这里兑现 | 真机看：挂一个源，能点开看到是哪个源、为什么 |
| 5 | L3 全局横幅（**只在多域名同时失败时**） | 优先级最低，且需要 §2.2-A 的判定 | 断网/改 hosts 模拟 |
| 6 | "复制诊断信息"入口（§3.3） | 有了证据才好反馈 | 复制内容不含凭据 |

**第 1–3 步就是本卡的最小可用版本**：它不新增任何网络请求、不碰 `MainActivity` 的网络路径，
却已经把"用户看到一句被证伪的话"变成"用户看到一句可验证的话"。

---

## 7. 不建议做的做法（避免以后有人乱改）

1. **不做"自动删源 / 自动禁用源"** —— 卡片明令（"不自动批量删除源"），且 §1.4 证明"源失效"的表象骗过所有人一次。
2. **不做"失败即判定源失效"的二值逻辑** —— §2.2 的矩阵就是为反对它而存在的。
3. **不建新后端反馈通道** —— 先做"一键复制诊断信息"（§3.3），零新基础设施、零隐私面。
4. **不做"自动换域名 / 自动换基础链接"** —— DFW-106（`:3abc671`）与 `MainActivity.java:3820` 的既有设计都反对。
5. **不在搜索过程中新增探测请求** —— 复用 `RouteOutcome`(`:259`) 与 `retestSources()`(`:949`) 已经产生的证据。
6. **不把「该源已失效或跳转异常」留在原地** —— 它是本卡要消灭的头号文案（§1.4 四次证伪）。
7. **不动 UA 顺序 / 不加新指纹头** —— DFW-107（`7ef92a2`）刚按活跃项目证据调过，不属于本卡范围。
8. **不改 `onFailure` 签名** —— 只新增 default 方法，避免破坏既有实现。
9. **不用"探测失败"当证据** —— 必须四元组（HTTP 状态 / UA / 请求字段 / 响应体特征），见 R6。
10. **不在 `MainActivity` 里堆新逻辑** —— 分类与判定放 `LanzouCore`/`Models`，`MainActivity` 只接线。

---

## 附：本次调研引用一览

| 主题 | 位置 |
|---|---|
| 域名池 10 条 | `LanzouCore.java:104`、`:110`、`:115` |
| canonical 域名 | `LanzouCore.java:111` |
| 内置源清单 84 条 | `app/src/empty/assets/r`（85 行）、`LanzouCore.java:1003` |
| 源失败回调契约 | `Models.java:112`（接口）、`Models.java:131`（`onFailure`） |
| **源名被丢弃处** | `MainActivity.java:3839` |
| 失败计数展示 | `MainActivity.java:2844` |
| **全站唯一错误出口** | `MainActivity.java:770-778`（`friendlyError`，含 `:777` `DfLog.failure`） |
| 启动域名探测告警 | `MainActivity.java:3820` |
| 域名测速对话框（三态） | `MainActivity.java:5095` |
| 按源复测 UI | `MainActivity.java:3994` |
| 每源状态模型 | `LanzouCore.java:260`（`SourceProfile`） |
| 路由候选（含 UA） | `LanzouCore.java:255`（`RouteCandidate`）、`:149`、`:1165`（`raceRoutes`） |
| 路由结果（含异常） | `LanzouCore.java:259`（`RouteOutcome`） |
| UA 探测结果 | `LanzouCore.java:276`（`UaProbe`） |
| 域名探测结果 | `LanzouCore.java:325`（`BaseOriginProbe`）、`:326` |
| 异常分类学 | `LanzouCore.java:245,246,277,278,279,280,300,307,335` |
| 页面判据 | `LanzouCore.java:1794`（`isUnavailableShareShell`）、`:1796`（`requireActiveShare`） |
| 源终结判定位置 | `LanzouCore.java:403`（`finishSourceLocked`） |
| 复测入口 | `LanzouCore.java:949`、`:951`；`Models.java:35-44` |
| 历史误判四案 | commit `7044bff`(DFW-103)、`25614aa`(DFW-104)、`820dbda`(DFW-88)、`7ef92a2`(DFW-107) |
| 不换域名约束 | commit `3abc671`(DFW-106) |
| WAF 滑块背景 | `docs/plan/dfw-108-waf-challenge.md` |
| 既有守卫测试 | `LanzouHostAllowlistJvmTest.kt`、`LanzouUnlockFieldsJvmTest.kt`、`LanzouSearchStallJvmTest.kt`、`LanzouDownloadRootCauseJvmTest.kt` |
| 测试联网禁区 | `MainActivity.java:414-422` |
| 凭据红线 | `docs/plan/decisions.md:23`（第 11 条）；`SECURITY.md:34-36` |
| 跑测试方式 | `AGENTS.md` 第四节（一律 `bash tools/run-tests.sh`） |
