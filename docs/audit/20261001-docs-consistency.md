# 2026-10-01 文档一致性复核报告

> **范围**：`docs/plan/risk-register.md`、`docs/plan/current-state.md`、`docs/plan/decisions.md`、
> `docs/tasks/README.md` + 面板卡 `DFW-19`。
> **方法**：每一条修正都先独立复核（`grep` / `wc -l` / `sed` / `git log` / `taskctl` / `curl` / `ssh` 只读），
> **不接受口头结论**。凡与 lead 初步结论不符者，在第 2 节明确写出。
> **未做**：没跑 gradle（另有 agent 在用机器）、没 commit、没 push、没碰任何代码与 `rikkahub/**`、
> 没改 `docs/handover/README.md`。
> **⚠️ 复核期间 HEAD 移动过（两次），版本号也动过**：本报告开始时 HEAD = `185e747`（v1.0.3）；
> 复核进行中，**另一个窗口**先后提交了 `5d9dd40 DFW-86 动效曲线统一` 与
> `edead6a DFW-42 首次启动引导`，并把版本号推到 **v1.0.4**（`app/build.gradle.kts:51-52`）。
> `edead6a` 的 `git add` 范围**顺带把我改的 4 个文档与本报告一起提交了**（见第 4 节末尾的提醒）。
> **这些代码改动与本报告无关、不是本报告做的**；凡受影响的数字，本报告都同时标注了版本。
> 复核结束时的 HEAD = `edead6a`，`current-state.md` 已由对方同步为 `1.0.4`。
> 改动前工作树已有 2 项与本任务无关的既存改动
> （` M .zcode/skills/codebase-memory-cli/SKILL.md`、`?? docs/handover/`），本报告未触碰它们。

---

## 0. 结论速览

| 条目 | 文档原话 | 复核结论 | 是否采纳 lead 的判断 |
|---|---|---|---|
| R-09 | 赞助/关于/致谢"整体重做未开始" | **过期**：两卡均已实施完毕，7 个守卫类 65 例 | 采纳 |
| R-12 | `sqlite-android:-SNAPSHOT` 风险仍在 | **过期**：已钉到不可变 commit | 采纳（措辞需收窄，见 1.2） |
| R-13 | CI 尚未完成分层门禁 | **过期**：`docs`/`unit`/`apk` 三层已建成 | 采纳 |
| R-10 | `MainActivity.java` 现约 2000 行 | **过期**：`185e747` 实测 **4242** 行，当晚 `5d9dd40` **4267** 行 | 采纳 |
| R-15 | 真机待验 | **过期**：用户 **2026-09-29** 已真机验收通过 | ❌ **lead 说错** |
| R-16 | 根因未定（lead 让写"真机现象未确认"） | **过期**：契约测试是 **5 例不是 3 例**；真机 **2026-10-01 已确认"没有错位了"** | ❌ **lead 说错两处** |
| R-11 | 帧级证据未完成 | **复核中状态变了**：DFW-86 当天已建卡并**当晚取证+修复入库**（`5d9dd40`），卡 `in_review`；但取证是**静态**的，真机帧级证据仍未完成 | 采纳（补充了 DFW-23→DFW-86→修复这条完整时间线） |
| R-01/R-08/R-17/R-19/R-20/R-21 | 真机待验 | **成立**，已补"截至 2026-10-01"时间戳 | 采纳 |
| R-19 行号 | `ModelList.kt:846` | **失效**：实际 `:876` | 额外发现 |
| R-20 放行清单 | "蓝奏 8 个域名池 + 回环/.local" | **不完整**：还放行了自有后台 `39.106.33.135`；用例数是 **8 不是 6** | 额外发现 |
| DFW-19 | "App 端一行代码未写" | **过期**：五条链路全部已接入，四条在**生产使用** | 采纳 |
| DFW-44/45/47/48 | "仅存档/剩余并入其它卡" | **确认可关**（4 张） | 采纳 |
| DFW-49 | 同上 | ⚠️ **不建议关**：其"现状修正"已被 DFW-73 推翻 | ❌ 与 lead 预期不同 |
| `current-state.md` §2 | lead 加的"清理已被撤销" | **写错了**：清理器仍在跑 | ❌ **lead 说错** |
| `current-state.md` §4.2 | 服务器已接入、控制台 `/admin/` | **正确**，`/admin/` 实测为当前版 | 采纳 |
| `current-state.md` §4.3 | 已恢复内置渠道 | **正确** | 采纳 |
| `docs/tasks/README.md` | `taskctl issue list ... --thread-id dfwx` | **命令报错** | 额外发现 |
| `decisions.md` #42 | 控制台入口 `/pb/` | **过期**：`/pb/` 是旧版控制台 | 额外发现 |

---

## 1. 逐条复核（原话 → 事实 → 证据 → 改成了什么）

### 1.1 R-09 赞助/关于/致谢页面

**文档原话**（`risk-register.md:16`）：
> 赞助页当前**还多了一个闪退（R-15）需先修**；整体重做未开始

**实际事实**：结论双重过期 —— ① 闪退 R-15 早已修完并真机验收；② 页面本身早已实施完毕。

**证据**：
```console
$ find . -path ./build -prune -o -type f \( -name "Support*.kt" -o -name "Brand*.kt" \) -print | grep -v /build/
./app/src/test/java/cc/nkbr/lanzouplus/BrandCreditsJvmTest.kt
./app/src/test/java/cc/nkbr/lanzouplus/SupportPageHeaderJvmTest.kt
./app/src/test/java/cc/nkbr/lanzouplus/SupportPageInAppJvmTest.kt
./app/src/test/java/cc/nkbr/lanzouplus/BrandingCleanlinessJvmTest.kt
./app/src/test/java/cc/nkbr/lanzouplus/SupportPageCopyJvmTest.java
./app/src/test/java/cc/nkbr/lanzouplus/SupportPageTransitionJvmTest.java
./app/src/test/java/cc/nkbr/lanzouplus/SupportPageFeedbackJvmTest.kt

$ for f in <上面 7 个>; do grep -c '@Test' $f; done
11 3 10 7 7 8 19          # 合计 65 例

$ git log --oneline -- app/src/test/java/cc/nkbr/lanzouplus/SupportPageHeaderJvmTest.kt
a9cc034 DFW-28（BRAND-002）品牌信任页核对：内容已完整，补回归守卫防止被删
```
`git show a9cc034` 提交信息逐项列出：三人署名+头像、感谢语逐字一致、9 项参考全在、AGPL-3.0 署名 —— **全部已满足**，
该轮工作是"把它们钉死"而不是改文案。赞助页侧另有 `362c6c1 DFW-27 BRAND-001 赞助页结构与交互收口`。
两卡在面板上都是 `in_review`（**未 done**，真机视觉验收没记录）。

**改成了什么**：`R-09` 等级改为 **✅ 已闭环（真机视觉验收待用户）**，保留原结论加删除线并注明"2026-10-01 复核：该结论已过期"，
附 7 类 65 例的实测数字与两个提交号，剩余项写"手机 release 视觉验收（DFW-27/DFW-28 停在 in_review）"。

---

### 1.2 R-12 SNAPSHOT / JitPack

**文档原话**（`risk-register.md:19`）：
> 上游已固定 tag `2.5.5`（改善）；`sqlite-android:-SNAPSHOT`（jitpack）风险仍在

**实际事实**：已钉到不可变 commit，且全仓构建配置里再无可变版本。

**证据**：
```console
$ grep -n "sqlite-android" rikkahub/gradle/libs.versions.toml
69:sqlite-android = "80cedc8888df2fe1d22d7f9bf8d9287f621624be"
191:sqlite-android = { module = "com.github.rikkahub:sqlite-android", version.ref = "sqlite-android" }

$ sed -n '64,68p' rikkahub/gradle/libs.versions.toml
# [DFWX PATCH P36] DFW-30：原为 "-SNAPSHOT"（JitPack 浮动版本 = 默认分支最新 commit）。
# ... 已钉到**不可变 commit** ...

$ ls docs/THIRD-PARTY-NOTICES.md && find . -name DependencyHygieneJvmTest*
./app/src/test/java/cc/nkbr/lanzouplus/DependencyHygieneJvmTest.kt   # 4 例
```
`rikkahub/PATCHES.md:231-249`（P36 段）记录：改回 `-SNAPSHOT` 立刻 2 红，鉴别力已验。

**⚠️ 措辞需要收窄**：lead 说"仓库里已搜不到 SNAPSHOT"**不严谨** —— 该词在 `rikkahub/PATCHES.md`（历史叙述）、
`rikkahub/ai/src/test/resources/stream-traces/README.md`（`UPDATE_STREAM_TRACE_SNAPSHOTS` 环境变量）、
旧审计归档里都还在。准确说法是：**版本目录与构建脚本里零命中**。

**改成了什么**：`R-12` 改为 **✅ 已闭环（完整 SBOM 待用户批准）**，写明钉版 commit、`grep` 的准确范围、
上游锚点是 tag、SBOM 简版已建、守卫 4 例；残留写"完整 SBOM（CycloneDX，需批准）+ 依赖锁定
（全仓无 `gradle.lockfile`/`verification-metadata.xml`）"；并注明 `PATCHES.md:98` 的"已知风险登记"仍写着旧风险
（vendor 区，本轮未改）。

---

### 1.3 R-13 CI 分层门禁

**文档原话**（`risk-register.md:20`）：
> 尚未完成分层门禁

**实际事实**：三层门禁已建成，本地与 CI 同一个脚本。

**证据**：
```console
$ ls -la tools/ci-gate.sh && grep -n "docs)\|unit)\|apk)\|all)" tools/ci-gate.sh
-rwx--x--x@ 1 lishaowei  staff  6253 Sep 30 05:24 tools/ci-gate.sh
134:case "${1:-all}" in
135:  docs) gate_docs ;;
136:  unit) gate_unit ;;
137:  apk)  gate_apk ;;
138:  all)  gate_docs; gate_unit; gate_apk ;;

$ git log --oneline --diff-filter=A -- tools/ci-gate.sh .github/workflows/ci-gates.yml
04ea81f DFW-31 CI 分层门禁：新增唯一门禁脚本 + 三层工作流（替换"从不跑测试"的旧流程）
```
`.github/workflows/ci-gates.yml` 三个 job 用 `needs` 串成 docs → unit → apk；CI 无 keystore，
apk 层只做 unsigned 构建并把产物改名 `dongfang-wuxian-UNSIGNED-DO-NOT-DISTRIBUTE.apk`。

**改成了什么**：`R-13` 改为 **✅ 已闭环**，写明三层、同一脚本、串行关系；
诚实标注残留"CI 是否真的在 GitHub 上跑过跑绿，本轮未核实（没查 Actions 运行记录）"。

---

### 1.4 R-10 `MainActivity.java` 行数

**文档原话**（`risk-register.md:17`）：
> `MainActivity.java` 现约 2000 行

**实际事实**：**4242 行**（复核开始时），比写下该数字时涨了一倍多；复核结束时已是 **4267 行**。

**证据**：
```console
$ wc -l app/src/main/java/cc/nkbr/lanzouplus/MainActivity.java
    4242 app/src/main/java/cc/nkbr/lanzouplus/MainActivity.java      # 复核开始时（HEAD 185e747）

$ for c in 6eb27b7 86fc478 8379e92 185e747; do git show $c:.../MainActivity.java | wc -l; done
2006 (2026-09-28)  3719 (2026-10-01 11:37)  3404 (2026-10-01 02:50)  4242 (2026-10-01 19:39)

# 复核进行中另一窗口提交了 5d9dd40（DFW-86 动效曲线统一），行数又涨了：
$ git show 185e747:.../MainActivity.java | wc -l   # 4242
$ git show 5d9dd40:.../MainActivity.java | wc -l   # 4267
$ git diff --stat 185e747 5d9dd40
 .../MainActivity.java                    | 59 ++++++++++-----
 .../MotionCurveUniformityJvmTest.kt      | 88 ++++++++++++++++++++++
 2 files changed, 130 insertions(+), 17 deletions(-)
```
`git log -S "现约 2000 行"` 定位到写下该数字的提交是 `b0ddbbd`，当期实测 2006 行 —— **当时是准的，现在是错的**。
一天之内（`8379e92` → `185e747`）就长了 838 行，随后 `5d9dd40` 又加 25 行。

**改成了什么**：改为"**2026-10-01 实测：`185e747` = 4242 行，当晚 `5d9dd40` = 4267 行**"，
注明"比写下该数字时涨了一倍多（当期 2006 行）"，把风险描述补为"风险等级**上升**，不是持平"，
并记下当晚 DFW-42 首次启动引导**已经在按"只挂一行、实现另起 `FirstRunGuide.java`"的方式做**——
方向与本项（渐进拆分）一致，属于正面信号。

---

### 1.5 R-15 赞助闪退的真机状态 —— ❌ **lead 说错**

**lead 的指令**：R-15"只补充'截至 2026-10-01 仍未验'的时间戳，别改结论"。

**实际事实**：**真机验收在 2026-09-29 就完成了**，R-15 的"真机待验"是过期状态，不该再写"仍未验"。

**证据**：
```console
$ taskctl comment list DFW-1
---- 2026-09-29T14:49:23.612Z
修好了，改的很棒，一点bug也没有。
---- 2026-09-29T15:01:24.975Z
用户 2026-09-29 真机验收通过：「修好了，改的很棒，一点bug也没有。」——BRAND-003 赞助闪退彻底闭环（v1.22.9）。本卡关闭。

$ taskctl issue get DFW-1   # status: done
```
（DFW-1 = BRAND-003 = R-15 的责任卡，状态 `done`。）

**改成了什么**：`R-15` 改为 **✅ 已闭环（真机已验）**，保留原根因叙述，把"真机装机验收（待用户）"替换为
验收日期与用户原话，并注明"台账原写'真机待验'**已过期**"。

---

### 1.6 R-16 顶栏错位 —— ❌ **lead 说错两处**

**lead 的指令**：R-16"复核后注明'已有契约测试，但真机现象是否消失未确认'"，并称契约测试是 **3 例**。

**实际事实（两处都错）**：
1. `AiInsetsContractJvmTest` 是 **5 例**，不是 3 例；
2. 真机现象**已经确认消失** —— 用户 2026-10-01 原话「**没有错位了**」。

**证据**：
```console
$ grep -c "@Test" app/src/test/java/cc/nkbr/lanzouplus/AiInsetsContractJvmTest.kt
5

$ git log --oneline --follow -- .../AiInsetsContractJvmTest.kt
86fc478 DFW-2 UI-005 AI 页顶栏错位：把 insets 契约的两端钉成回归守卫
$ git show 86fc478:.../AiInsetsContractJvmTest.kt | grep -c "@Test"
5                      # 创建时就是 5 例，不是后来加的

$ taskctl comment list DFW-2
---- 2026-10-01T06:19:04.798Z
## 用户真机验收通过（2026-10-01）
用户原话：「那三个测试的我测过了，没有错位了，动画性能也行，AI发消息后也不限流。」
- 顶栏错位：**不再错位**
```
`taskctl issue get DFW-2` → `status: done`。5 个用例分别是：TopAppBar 必须清零 `windowInsets`、
宿主裁系统栏但保留 ime、insets 处理禁 `ValueAnimator`/`WindowInsetsAnimation`/`stickyBelow`/`postOnAnimation`、
键盘弹出前后 `paddingTop` 恒定、底部留白取导航条与刘海较大值。

**改成了什么**：`R-16` 改为 **✅ 已闭环（真机已验）**，写明"契约守卫 **5 例**（若写 3 例即误）"，
并把"根因未定/真机未确认"标为过期。同时保留 DFW-2 评论里那条重要限定：
P38 的 `windowInsets` 清零**不改变任何一帧几何**，用户看到的好转对应 DFW-72 的修复（`8379e92`）。

---

### 1.7 R-11 动画取证

**文档原话**（`risk-register.md:18`）：历史改动存在，帧级证据未完成。

**实际事实**：成立，但需补一段"当天先关后开"的经过，否则下一个接手的人会以为 DFW-23 的"已闭环"还有效。

**证据**：
```console
$ taskctl issue list --project dfwx-android | grep DFW-86
DFW-86 | backlog | DFW-86 R-11 动画帧级取证：为什么这个 app 的动画整体感觉不对
（createdAt 2026-10-01T11:49:34.550Z = 北京时间 19:49）

$ taskctl comment list DFW-23
---- 2026-10-01T06:19:05.271Z（= 北京时间 14:19）
用户真机验收通过（2026-10-01）……动画性能：**可以接受**
```
即：**14:19 关闭 DFW-23 → 19:49 新建 DFW-86**，中间用户再次抱怨。DFW-86 描述自述"用户连续三轮抱怨动画
（割裂/预动画/渐隐不丝滑）"，并写明"**禁止在取证完成前改动画代码**"。

**⚠️ 复核进行中这张卡又变了**：另一窗口当晚提交 `5d9dd40 DFW-86 动效曲线统一`，DFW-86 由 `backlog` → `in_review`：
```console
$ git log -1 --format=%B 5d9dd40 | head -20
DFW-86 动效曲线统一：修掉用户三轮"割裂"反馈的真正根因
## 取证结论（这次没有逐点打补丁，先做全站清点）
**① 37 条动画语句里，22 条设了时长却没设曲线。**
ViewPropertyAnimator 在不设曲线时用的是安卓默认的 AccelerateDecelerateInterpolator
……与"快速起步、长尾减速"的 M3 emphasized 手感正好相反。……全站同时存在两个缓动族
**② 顺带查了物理弹簧：SpringAnimation 15 处、DynamicAnimation 8 处。**……已登记，未擅自改。

$ taskctl issue get DFW-86
status: in_review | v2 | updated 2026-10-01T11:54:29Z
```
提交里**作者自己"诚实登记"**了两条残留，对台账很关键：
① "这是**静态取证**，不是真机帧级取证……**没有测过修复后的真机手感** —— 曲线统一是必要条件，不是充分条件"；
② 下一个怀疑对象是**时长档位**（15 档，"明显是随手写的，不是设计出来的"）。

**改成了什么**：`R-11` 等级改为 **P1（2026-10-01 重新打开后已取证并修复）**，写完整时间线
（DFW-23 验收关闭 → 用户再抱怨 → 建 DFW-86 → `5d9dd40` 取证+修复入库），
明确"**帧级证据仍未完成**（本轮是静态取证）"、下一证据改为"真机手感验收 + 时长档位是否收编"，
并保留"DFW-23 的已闭环不再代表本风险消失"这句提醒。

---

### 1.8 R-01 / R-08 / R-17 / R-19 / R-20 / R-21 的真机时间戳

**复核方式**：全卡评论扫描，找真机验收证据：
```console
$ for id in <全部 86 张卡>; do taskctl comment list $id | grep -E '真机验收通过|真机通过|用户确认'; done
DFW-46 | 2026-10-01T06:19:05.748Z | 真机验收通过   # 顶栏错位/动画性能/切页不断流
DFW-23 | 2026-10-01T06:19:05.271Z | 真机验收通过   # 同上
DFW-2  | 2026-10-01T06:19:04.798Z | 真机验收通过   # 同上
DFW-1  | 2026-09-29T15:01:24.975Z | 真机验收通过   # 赞助闪退
```
**全仓只有这两批真机验收记录**，且都不覆盖 R-01（外部下载负向链路）、R-08（Shizuku 复测）、
R-17（空会话无文字）、R-19（附件图标冒烟）、R-20（蓝奏解析/AI 请求/图片加载）、R-21（新版本提示）。
所以这 6 条的"真机待验"**成立**，已按要求补"截至 2026-10-01 仍未验"。
`docs/handover/README.md` §5「只有用户能做的事」也仍把"真机验收"列为未完成项，交叉印证。

**顺手更正的两处硬错误**：
- `R-19` 引用的 `ModelList.kt:846` **已失效**：
  ```console
  $ grep -rn "painterResource" rikkahub/app/src/main/java/ | grep deepthink
  .../ui/components/ai/ModelList.kt:876:  painter = painterResource(R.drawable.deepthink),
  .../ui/pages/chat/Export.kt:678:        painter = painterResource(R.drawable.deepthink),
  ```
  `ChatMessage.kt:537/545`（docx/pdf）仍然有效。已把 846 更正为 **876**。
- `R-20` 的放行清单**漏了自有后台**：
  ```console
  $ grep -n "39.106" app/src/main/res/xml/network_security_config.xml
  56:        <domain includeSubdomains="false">39.106.33.135</domain>
  ```
  该放行是 decisions #33（HTTP + 三重校验）的实现。同时 `NetworkSecurityConfigJvmTest` 实测 **8 例**
  （`grep -c "@Test"` = 8，含 2 例清单死配置检查），台账旧文写的 6 例不准。两处都已更正。

---

### 1.9 DFW-19 面板卡

**卡片原标题**：
> 远程后端接入：服务器已上线但 App 端一行代码未写（远程公告 + 自有更新源，待立项）

**实际事实**：五条链路**全部已接入**，其中四条**已在生产使用**（服务器上当前发布的正是 v1.0.3）。

**证据（App 端代码）**：
```console
$ ls -la app/src/main/java/cc/nkbr/lanzouplus/{RemoteConfigClient,NoticeBanner,UpdateClient}.java
RemoteConfigClient.java  18859 Oct  1 14:50
NoticeBanner.java         9040 Sep 30 20:59
UpdateClient.java        10907 Oct  1 11:50
$ grep -n "BASE =" app/src/main/java/cc/nkbr/lanzouplus/RemoteConfigClient.java
37:  static final String BASE = "http://39.106.33.135/pb";
$ ls server/dfwx-admin/index.html server/dfwx-upload/server.py
```

**证据（服务器外网实探，全部只读）**：
```console
$ curl -s http://39.106.33.135/health
{"status":"ok","service":"dfwx-backend"}

$ curl -s "http://39.106.33.135/pb/api/collections/release/records?perPage=3"
{"items":[{"apkUrl":"http://39.106.33.135/apk/dongfang-wuxian-v1.0.3.apk",
 "sha256":"6a0814812887186377c17ab1a94adcef470a9f0e9676ea55dc4ef417a80d5c54",
 "size":36828141,"updateMode":"soft","versionCode":10003,"versionName":"1.0.3"}],"totalItems":1}

$ curl -s "http://39.106.33.135/pb/api/collections/notice/records?perPage=2"
{"items":[{"title":"东方无限 正式公测","enabled":true,"level":"normal",...}],"totalItems":1}

$ curl -sI http://39.106.33.135/apk/dongfang-wuxian-v1.0.3.apk | head -4
HTTP/1.1 200 OK
Content-Type: application/vnd.android.package-archive
Content-Length: 36828141                     # 与集合记录一致

$ curl -sk -o /dev/null -w "%{http_code}\n" https://39.106.33.135/ai/v1/models
401                                            # 端点存在且受保护，未泄露上游

$ curl -sk -o /dev/null -w "%{http_code} %{size_download}\n" https://39.106.33.135/admin/
200 39289                                      # 网页控制台

$ ssh dfwx 'ls -l /etc/nginx/dfwx-ai-secret.conf; ls /var/www/dfwx/apk/'
-rw------- 1 root root 1090 Oct  1 10:13 /etc/nginx/dfwx-ai-secret.conf     # 600 ✅
dongfang-wuxian-v1.0.0.apk … v1.0.1 / v1.0.2 / v1.0.3 / v1.22.19.apk
```

**改成了什么**：用 `taskctl issue update` 改了标题与描述（详见第 4 节）。新标题：
> 远程后端接入：已完整接入并跑通（公告/更新/APK 分发/AI 中转/网页控制台）——本卡转为真机验收与加固

新描述逐条列出"链路 / App 端代码 / 服务器端实探结果"三列证据表，把原"待用户拍板的 4 个问题"
标为已被 DFW-59/61/65/66/70 解决，剩余改为"真机验收 + DFW-82 联动 + Key 轮换/MFA"。

**附带发现（已写进卡里）**：旧静态接口 `/api/version.json` 仍在（HTTP 200）但**已停更**
（`latest.versionName` 还是 `1.22.x`），App 现在读 PocketBase。将来清理静态接口时别把 `/pb/` 一起删了。

---

### 1.10 `current-state.md` 的三处（lead 已改一半，逐处复核）

**(a) §2 "不再内置任何 API" 加删除线 + "已被推翻" —— 方向对，但 lead 补的最后一句写错了。**

lead 写的原文：
> AI-004 的删除动作由 `DfwxBuiltinProviderCleanup` 执行，现在该清理**已被撤销**。

**实际事实：清理器没有被撤销，仍在跑。**
```console
$ grep -n "DfwxBuiltinProviderCleanup\|DfwxBuiltinChannel" rikkahub/app/src/main/java/me/rerere/rikkahub/RikkaHubApp.kt
43:import me.rerere.rikkahub.dfwx.DfwxBuiltinProviderCleanup
118:        DfwxBuiltinProviderCleanup.removeLegacySeedIfNeeded(this, get<AppScope>(), get<SettingsStore>())
121:        // 与 AI-004 的清理器并存不冲突：清理器认的是"智能中转(内置)"那个老身份，这里是新身份「内置渠道」。
123:        DfwxBuiltinChannel.syncIfNeeded(get<AppScope>(), get<SettingsStore>())
```
`DfwxBuiltinProviderCleanup.kt:45-73` 的实现体也完整保留。真实关系是**两个身份并存**：
清理器删老身份「智能中转(内置)」（`DfwxBuiltinProviderCleanup.kt:37-43`，`aizhongzhuan.cc` + `glm-5.3`），
`DfwxBuiltinChannel` 每启动同步新身份「内置渠道」（`DfwxBuiltinChannel.kt:49` `https://39.106.33.135/ai/v1`）。
代码注释自己写着"并存不冲突"。

**改成了什么**：保留 lead 的删除线与"已被推翻"，把最后那句换成带行号的更正段（含 `RikkaHubApp.kt:118/120-123`、
`DfwxBuiltinProviderCleanup.kt:37-43/45-73`、`DfwxBuiltinChannel.kt:49`），明确写"**这句是错的**"。

**(b) §4.2 "服务器 APP 端未接入" 的更正 —— 正确。**
复核了它列的每一项（远程公告 / 远程更新清单 / APK 分发 / 内置 AI 渠道中转 / 网页控制台 `/admin/`），
服务器实探全部成立（见 1.9）。特别是 **`/admin/` 这个路径是对的**：
```console
$ curl -sk https://39.106.33.135/admin/ -o /tmp/admin.html && wc -c /tmp/admin.html
   39289 /tmp/admin.html
$ cmp -s server/dfwx-admin/index.html /tmp/admin.html && echo 一致
一致
$ grep -c "AI 渠道" /tmp/admin.html
5
```
本地源码与线上 `/admin/` **逐字节一致**，且含 DFW-80 的「AI 渠道」标签页。

**(c) §4.3 "已恢复内置渠道" —— 正确**，与 `DfwxBuiltinChannel.kt` 的实现和 DFW-73 卡一致。
其中"真 Key 只在服务器 `/etc/nginx/dfwx-ai-secret.conf`，600"这条也实探核对过（见 1.9，`-rw-------`）。

**(d) `DocTimelinessJvmTest` 的版本号断言 —— 未被破坏。**
该测试要求 `current-state.md` 同时含 `versionName` 与 `BuildConfig.VERSION_NAME`（当前 `1.0.3`）：
```console
$ python3 -c "t=open('docs/plan/current-state.md').read(); print('versionName' in t, '1.0.3' in t)"
True True
$ bash tools/ci-gate.sh docs
✅ 文档与卫生门禁通过
```
（`docs` 层是纯 `grep`/`git` 静态检查，**不调 gradle**，所以可以安全跑。）

---

### 1.11 `docs/tasks/README.md` 的 taskctl 命令 —— 额外发现

**文档原话**（旧版第 10 行）：
```bash
taskctl issue list --project dfwx-android --thread-id dfwx
```
**实测报错**：
```console
$ taskctl issue list --project dfwx-android --thread-id dfwx
{"schemaVersion":2,"error":{"code":"USAGE_ERROR","message":"Unknown option --thread-id"}}

$ taskctl issue list --project dfwx-android        # 不带才对
{"tasks":[... 86 张 ...]}
```
`taskctl --help` 的用法行写得很清楚：`issue list [--project PROJECT_ID] [--status STATUS] [--archived ...]` —— **没有** `--thread-id`。
`--thread-id` 只对 `issue create/update/move/archive/restore/relation` 与 `comment add/update/delete` 有效。

**改成了什么**：改成正确的三条示例（`issue list` / `issue list --status in_review` / `issue get`），
加一条显式警告说明"哪些命令才支持 `--thread-id`"，并补上改标题/描述的正确姿势（含 `--if-version` 乐观锁说明）。

---

### 1.12 `decisions.md` #42 的控制台入口 —— 额外发现

**文档原话**：入口 `https://39.106.33.135/pb/`
**实测**：`/pb/` 返回的是**旧版**控制台。
```console
$ curl -sk https://39.106.33.135/pb/ -o /tmp/pb.html; wc -c /tmp/pb.html
   22182 /tmp/pb.html                       # vs /admin/ 的 39289
$ grep -c "AI 渠道" /tmp/pb.html
0                                            # 没有 DFW-80 加的标签页
$ diff <(head -30 /tmp/admin.html) <(head -30 /tmp/pb.html)
< <meta http-equiv="Cache-Control" content="no-store, no-cache, must-revalidate">   # /admin/ 有 no-store
$ ssh dfwx 'ls -d /var/www/dfwx/*'
/var/www/dfwx/admin  /var/www/dfwx/api  /var/www/dfwx/apk      # 站点里只有 admin，没有 pb
```
这正是 DFW-85「控制台页面缓存导致用户看不到新功能（已修一半，需彻底解决）」的现象，该卡仍 `in_review`。
给用户发 `/pb/` 链接会让他看到没有 AI 渠道标签页的旧界面 —— **会直接导致误判"功能没做"**。

**改成了什么**：在 #42 的"入口"一行加删除线 + 更正为 `/admin/`，附 39289/22182 字节、
`no-store` 头、`cmp` 逐字节一致三条证据，并注明"给用户/自己发控制台链接时一律用 `/admin/`"。
同时把 `decisions.md` 表头的"更新日期：2026-09-28 晚"更正为 2026-10-01（表头当时就没跟上 #31–#42）。

---

## 2. lead 说错的地方（必读）

| # | lead 的说法 | 实际情况 | 证据 |
|---|---|---|---|
| 1 | R-15 只加"截至 2026-10-01 仍未验"的时间戳，别改结论 | **真机验收 2026-09-29 就完成了**，写"仍未验"是新的错误信息 | `taskctl comment list DFW-1`；DFW-1 `status: done` |
| 2 | R-16"已有契约测试（3 例），但真机现象是否消失未确认" | ① 契约测试是 **5 例**；② 真机 **2026-10-01 已确认"没有错位了"** | `grep -c "@Test"` = 5；`taskctl comment list DFW-2`；DFW-2 `status: done` |
| 3 | `current-state.md` §2"`DfwxBuiltinProviderCleanup` 现在该清理**已被撤销**" | **没被撤销，仍在跑**，与新的 `DfwxBuiltinChannel` 并存、各管一个身份 | `RikkaHubApp.kt:118` 与 `:120-123` 注释原文 |
| 4 | taskctl 改卡用 `--if_version <version>` | 该参数名不存在，实际是 **`--if-version`**（连字符） | `taskctl issue update ... --if_version 1` → `Unknown option --if_version` |
| 5 | "仓库里已搜不到 SNAPSHOT" | 构建配置里确实没有，但 `PATCHES.md` / 测试资源 / 旧审计归档里还有该词 | `grep -rn -i snapshot .` |
| 6 | R-19 只需补时间戳 | 它还带着一个**失效行号**：`ModelList.kt:846` 实际是 `:876` | `grep -rn painterResource .../ModelList.kt` |
| 7 | R-20 只需补时间戳 | 它的放行清单**漏了自有后台 `39.106.33.135`**，用例数 6 实际是 **8** | `network_security_config.xml:56`；`grep -c "@Test"` = 8 |
| 8 | DFW-49 归入"建议关闭" | **不建议关**：它的"现状修正"（AI-004 已移除全部内置渠道，T3-G 前提消失）已被 **DFW-73 于 2026-10-01 推翻** | DFW-49 描述末段 vs `DfwxBuiltinChannel.kt` |

lead **说对**的：R-09（7 个测试类 ✅ 实测就是 7 个）、R-12 主结论、R-13、R-10（4242 ✅）、
R-11（DFW-86 ✅）、DFW-19（五条链路 ✅ 全部实探成立）。

---

## 3. 建议关闭但没关的卡清单（**未自行关闭，交 lead 决定**）

### 3.1 建议关闭（4 张，`canceled` 或 `done` 均可）

| 卡 | 现状态 | 为什么可以关 | 复核证据 |
|---|---|---|---|
| **DFW-44** | backlog | 卡内自述"工作已并入 DFW-24，本卡不单独开工"。声称的已完成项实测都在 | `grep -c` in `MainActivity.java`：`buildSettingsSearch` 2、`applySettingsFilter` 2、`refreshSettingsInPlace` 6 |
| **DFW-45** | backlog | 卡内自述"工作已并入 DFW-25（UI-003），本卡仅存档" | 与 DFW-25（`in_review`）范围一致，无独立剩余项 |
| **DFW-47** | backlog | 卡内自述"**无需再做**，保留此卡仅为记录归档" | `AiPermissionGate.java` 存在（`KEY_DONE="guided"`、`guided(Activity)`）；`MainActivity.java:889` 仍走 `maybeGuideAiPermissions(this::enterAiPage)`。⚠️ 卡里引的 `MainActivity.java:1266/:1273` 已漂移到 `:889` 附近 |
| **DFW-48** | backlog | 卡内自述"剩余部分见 DFW-2（UI-005）"，而 **DFW-2 已 `done`**，且用户 2026-10-01 真机确认"没有错位了" | `taskctl issue get DFW-2` → `done`；DFW-72（同题，`in_review`）另承接 |

> 建议关闭时在评论里写一句"工作已由 DFW-xx 承接，2026-10-01 复核关闭"，保留可追溯性。

### 3.2 **不建议关闭**（1 张）—— 与 lead 的预期相反

**DFW-49（T3/T3-G 五分诚信赞助产品边界）**：卡内"现状修正"段写着
> 归档 T3-G「只限制内置 AI 渠道」：**AI-004 已移除全部内置渠道**（用户 2026-09-28 拍板），
> 所以 T3-G 的前提已变——现在没有"内置渠道"可限制了。

**这句话在 2026-10-01 已被推翻**：用户当天拍板**恢复「内置渠道」**（DFW-73，走服务端中转），
代码里 `DfwxBuiltinChannel` 已是活实现（`DEFAULT_BASE_URL = "https://39.106.33.135/ai/v1"`，
模型 `deepseek-v4.1-flash`，未付费点击弹引导窗）。

也就是说 **T3-G 的前提又回来了**：现在**确实有**"内置渠道"这件事需要考虑限制/引导边界。
建议动作：**不关**，改描述（把"AI-004 已移除"更正为"DFW-73 已恢复，走服务端中转"），
并明确它与 DFW-27（BRAND-001，`in_review`）的合并关系是否仍然成立。

### 3.3 顺手发现、同样建议处置的卡（不在 lead 清单里）

| 卡 | 现象 | 证据 |
|---|---|---|
| **DFW-82** | 功能**已实现并入库**（提交 `afb895b`），卡却仍停 `backlog`、0 条评论 | `git log --oneline` 含 `afb895b DFW-81/82 … 修好更新功能（versionCode 死结）`；`taskctl issue get DFW-82` → `backlog` |
| **DFW-23** | 已 `done`，但同题的 DFW-86 于同日重新立卡 —— 建议在 DFW-23 补一条评论指向 DFW-86，避免"看到 done 以为动画已结案" | DFW-23 评论 2026-10-01T06:19；DFW-86 createdAt 2026-10-01T11:49 |
| **DFW-86** | 复核中被另一窗口推到 `in_review`（`5d9dd40`，静态取证+22 条曲线补齐）。建议：**别急着 done** —— 提交自己写着"没有测过修复后的真机手感"、且时长档位（15 档）是下一个怀疑对象 | `git show 5d9dd40`；`taskctl issue get DFW-86` → `in_review` v2 |
| **DFW-64** | `in_progress`：官方 `v1.0.0` GitHub release 未发；handover 说"等用户拍板" | `docs/handover/README.md` §4 |

---

## 4. 本轮实际改了什么

### 4.1 文件（4 个，全部在授权范围内；**未碰任何代码**）

| 文件 | 改动 |
|---|---|
| `docs/plan/risk-register.md` | 表头日期更新 + 标注测量基线（`185e747` / `5d9dd40`）；R-09/R-12/R-13 改为已闭环并附证据；R-15/R-16 补真机验收结论；R-10 更正行数（2000→4242，并注 4267）；R-11 补 DFW-23→DFW-86→`5d9dd40` 的完整时间线与"静态取证≠帧级证据"；R-01/R-08/R-17/R-19/R-20/R-21 补时间戳；R-19 行号 846→876；R-20 补自有后台放行 + 用例数 6→8；R-03 补 DFW-73 影响；R-04 补 12 例构成；R-18 补 `release.sh` 与服务器分发面；"结论规则"加一条"也不能把已验证一直写成待验证" |
| `docs/plan/current-state.md` | 只改 §2 的一处错误陈述（"清理已被撤销"→带行号的更正段），并保留 lead 的删除线与"已被推翻" |
| `docs/plan/decisions.md` | #42 入口 `/pb/`→`/admin/`（附证据）；表头更新日期更正 |
| `docs/tasks/README.md` | 更正 `taskctl issue list` 的错误参数，补 `--if-version` 与 `--description-file` 用法 |

`git diff --stat`（**只列本报告改的 4 个文件**；同期的代码改动属另一窗口，见文首说明）：
```
 docs/plan/current-state.md | 11 ++++++++++-
 docs/plan/decisions.md     | 11 +++++++++--
 docs/plan/risk-register.md | 45 +++++++++++++++++++++++-----------------
 docs/tasks/README.md       | 14 ++++++++++++--
```
（另新建 `docs/audit/20261001-docs-consistency.md` = 本文件。）

> ⚠️ **两件事必须让 lead 知道**：
>
> **(1) 我没有 commit，但我的文件被别人的 commit 卷进去了。**
> `edead6a DFW-42 首次启动引导` 的 `git add` 范围包含了
> `docs/plan/{current-state,decisions,risk-register}.md`、`docs/tasks/README.md` 与本报告 ——
> 即**我的文档改动是随那张卡一起入库的**，提交信息里**没有提到文档修正**。
> 我全程只用了 `write`/`edit` + `taskctl`，`git reflog` 里没有任何我的 commit 记录。
> 当前仍有**未提交**的增量：`risk-register.md`（R-10/R-11 的 4267 行与 DFW-86 时间线）
> 与本报告的后半部分 —— 这两处需要 lead 决定怎么收尾。
>
> **(2) 不要把我改的文件与同期代码改动混在一起提交。** 复核结束时 `git status` 里还有
> `M app/build.gradle.kts`（版本已推到 1.0.4）与既存的 `M .zcode/skills/codebase-memory-cli/SKILL.md`、
> `?? docs/handover/` —— **都不是本报告的产物**。本报告按 lead 要求**没有 commit、没有 push**。

### 4.2 面板卡（1 张）

```console
$ taskctl issue update DFW-19 --thread-id dfwx \
    --title "远程后端接入：已完整接入并跑通（公告/更新/APK 分发/AI 中转/网页控制台）——本卡转为真机验收与加固" \
    --description-file /tmp/dfw19-desc.md --if-version 1
✅ identifier: DFW-19  version: 1 → 2  status: backlog
```
- 标题：由"服务器已上线但 App 端一行代码未写（…待立项）"改为与事实相符的新标题
- 描述：整篇重写为"链路 / App 端代码 / 服务器端实探结果"三列证据表 + 剩余事项 + 旧接口提醒 + 红线
- **状态未动**（仍 `backlog`）：它现在该做的事是"真机验收与加固"，是否转 `todo`/`in_review` 由 lead 决定

---

## 5. 我没查实的 / 已知残留

1. **CI 是否真的在 GitHub 上跑过、跑绿过** —— 只核到工作流文件与脚本本身，没查 Actions 运行记录（R-13 已如实标注）。
2. **DFW-44/45/47/48/49 的关闭** —— 按 lead 要求**只给清单不代关**；DFW-49 明确建议不关。
3. **R-19 的附件图标真机冒烟** —— 用户 2026-09-29 那句"一点bug也没有"是笼统验收，
   记录里明确写的验证点是"赞助页不闪退"；附件/模型列表那条只是当时列的"顺带冒烟"，
   **没有单独确认记录**，所以我按"仍未验"处理，没有替用户宣布通过。
4. **R-21 的真机新版本提示** —— 服务器上确实有 v1.0.3 的 release 记录（更新链路的服务端已就绪），
   但"用户在手机上真的看到过一次新版本提示"没有任何评论/截图证据，按未验处理。
5. **`rikkahub/PATCHES.md:98` 仍写着 `-SNAPSHOT` 风险** —— 属 vendor 区，**本轮禁止改动**，
   已在 R-12 里注明。建议 lead 派一张卡或在下次同步上游时顺手清掉。
6. **服务器上 `/api/version.json` 已停更**（仍返回 `latest.versionName = "1.22.x"`）——
   App 已不读它，但它还活着；没动服务器，只在 DFW-19 描述里留了提醒。
7. **`docs/handover/README.md` §0 写"版本 1.0.1"，实际是 1.0.3** —— 该文件是 lead 的现场文件，
   **按要求未改**，仅在此报告。
8. **`decisions.md` #41 的 `/pb/_/`（PocketBase 自带后台）** 实测仍 200 可用，未改。
9. **没跑 gradle**（遵守 lead 约束，另有 agent 在用机器），所以
   "改动后全量 JVM 测试是否仍全绿"**未验证**；不过本轮只改 4 个 markdown + 1 张面板卡，
   受影响的守卫实际上只有 `DocTimelinessJvmTest`（版本号，已单独自检通过）。
10. **HEAD 在复核期间移动过两次**（`185e747` → `5d9dd40` → `edead6a`），版本号也从 1.0.3 推到 **1.0.4**。
    本报告的所有测量都标了版本；**若 lead 之后看到数字对不上，先看是不是又有人提交了**。
    我没有、也不会去核对另一窗口那些代码改动是否正确 —— 那超出本报告范围。
    版本一致性我顺手核过：`app/build.gradle.kts:51-52` = `10004 / "1.0.4"`，
    `current-state.md` 已含 `versionName` 与 `1.0.4` → `DocTimelinessJvmTest` 可过。
11. **DFW-86 的修复是否真的解决"割裂"** —— 未验证，且提交作者自己也说这是静态取证。
    台账 R-11 已按"仍未完成帧级证据"记录，没有替它宣布闭环。
12. **本报告的文档改动已被 `edead6a` 顺带提交**（不是我做的 commit），
    其中 `risk-register.md` 的 R-10/R-11 最新修订与本报告后半部分**仍在工作树里未提交**。
    是否补一个只含文档的提交、以及提交信息怎么写，**留给 lead 决定**。

**关于 `BrandingCleanlinessJvmTest` —— 顺手纠正一条流传的说法**：
`docs/handover/README.md` §3 写"注释与文档里不许出现 `黑曜`、`heiyao`（`BrandingCleanlinessJvmTest` 会红）"，
**这句不准确**。实测该测试的扫描根是 `app/src/main/`：
```console
$ grep -n "app/src/main\|docs" app/src/test/java/cc/nkbr/lanzouplus/BrandingCleanlinessJvmTest.kt
26:        while (!File(dir, "app/src/main/AndroidManifest.xml").isFile && dir.parentFile != null) dir = dir.parentFile
30:    private val mainDir = File(root, "app/src/main")
```
它只扫 `app/src/main/` 下的 `res/`、`AndroidManifest.xml` 与 Java/Kotlin 源码，**不扫 `docs/`**。
而 `docs/` 里本来就有该词（归档路径与目录名），例如：
```console
$ grep -rn "黑曜\|heiyao" docs/plan/
docs/plan/current-state.md:12:  - 实际源码根目录：`/Users/lishaowei/heiyao/src`
docs/plan/current-state.md:16:  - 回退点：… `~/heiyao/vendor-backup-before-255-20260928.tar.gz` …
docs/plan/current-state.md:59:  … 已归档 `~/heiyao/黑曜/03-构建产物/`
docs/plan/decisions.md:43:22. 调研三层查…先查本地黑曜参考库…
```
这些是**工作区路径与本地归档目录名**（仓库本身就在 `/Users/lishaowei/heiyao`），属既存事实，不是品牌残留，
本轮**一个都没动、也没新增**：我改动的 4 个文档里，新增行中零命中该词。
本报告为避免制造噪音，正文引用归档路径处统一用"本地归档目录"代替，不写出该词。


---

## 6. 一键复现（本轮全部证据命令）

```bash
cd ~/heiyao/src

# R-09
find . -name "Support*JvmTest*" -o -name "Brand*JvmTest*" | grep -v /build/
for f in app/src/test/java/cc/nkbr/lanzouplus/{BrandCredits,SupportPageHeader,SupportPageInApp,BrandingCleanliness,SupportPageFeedback}JvmTest.kt \
         app/src/test/java/cc/nkbr/lanzouplus/{SupportPageCopy,SupportPageTransition}JvmTest.java; do grep -c '@Test' "$f"; done

# R-10
wc -l app/src/main/java/cc/nkbr/lanzouplus/MainActivity.java

# R-12
grep -n "sqlite-android" rikkahub/gradle/libs.versions.toml
grep -rn --binary-files=without-match -i snapshot . --exclude-dir=.git --exclude-dir=build

# R-13
sed -n '134,139p' tools/ci-gate.sh; ls .github/workflows/

# R-16
grep -c '@Test' app/src/test/java/cc/nkbr/lanzouplus/AiInsetsContractJvmTest.kt
taskctl comment list DFW-2

# R-11 / 真机验收全景（86 张卡逐张查评论，约需 1 分钟）
taskctl issue get DFW-86
for id in $(taskctl issue list --project dfwx-android \
    | python3 -c "import json,sys;print(' '.join(t['identifier'] for t in json.load(sys.stdin)['tasks']))"); do
  taskctl comment list $id 2>/dev/null | grep -q "真机验收通过" && echo "$id"
done
# 实测输出：DFW-46 / DFW-23 / DFW-2 / DFW-1

# DFW-19 / 服务器（全部只读）
curl -s http://39.106.33.135/health
curl -s "http://39.106.33.135/pb/api/collections/release/records?perPage=3"
curl -s "http://39.106.33.135/pb/api/collections/notice/records?perPage=2"
curl -sI http://39.106.33.135/apk/dongfang-wuxian-v1.0.3.apk
curl -sk -o /dev/null -w "%{http_code}\n" https://39.106.33.135/ai/v1/models
curl -sk https://39.106.33.135/admin/ -o /tmp/admin.html && cmp -s server/dfwx-admin/index.html /tmp/admin.html && echo 一致
ssh dfwx 'ls -l /etc/nginx/dfwx-ai-secret.conf; ls /var/www/dfwx/apk/'

# current-state 守卫自检（纯静态，不调 gradle）
bash tools/ci-gate.sh docs
```
