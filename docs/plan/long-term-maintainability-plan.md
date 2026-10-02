# 东方无限：长期可维护规范化方案

> 建立日期：**2026-10-02**。基线：分支 `test`，HEAD `f832ca6`（DFW-91）。
> 适用源码根目录：`/Users/lishaowei/heiyao/src`。
>
> **本文的定位**：不是又一份"治理包"，而是**一次减法审查**。结论按证据分等级
> （A=当前源码/命令输出/真实产物，B=项目规范，C=历史文档，D=推测，沿用 `current-state.md` §6）。
> 凡本文与当前源码冲突，以源码为准。
>
> **一句话结论**：这个仓库缺的不是"规范文件"，而是**现有门禁的可信度**。
> 它已经建起了比绝大多数个人项目都强的工程设施（分层门禁脚本、发版断言、约 87 个 JVM 守卫测试、
> 文档一致性测试），但其中**最重要的一层在 CI 上 100 次运行 0 次绿**；
> 同时它已经用两天时间证明过一次"堆规范文件"的路走不通（16 个文件的治理包 2026-09-27 被整体归档）。
> 所以本文的建议是：**修好已有的，只补三件缺的，明确拒掉一批"看起来专业"的噪音。**

---

## 1. 现状盘点（全部为实测）

### 1.1 已经有的（不是"应该有"，是"已经在跑"）

| 设施 | 证据 | 评价 |
|---|---|---|
| 分层门禁脚本（本地与 CI 共用） | `tools/ci-gate.sh:1-140`，三层 `docs`/`unit`/`apk`，`:38` `:64` `:80` | **本项目最强资产之一**：脚本头部 `:4-8` 写明"本地与 CI 必须同一套命令"，这是很多团队都没做到的 |
| CI 工作流 3 个 | `.github/workflows/ci-gates.yml`(147 行)、`android-build.yml`(32 行)、`lanzouplus-empty.yml`(56 行) | 结构对，但见 §1.3 的实测结论 |
| 发版断言脚本 | `tools/release.sh:1-260`；`build` 强制先跑 `verify`（`:145`），publish 默认 dry-run（`:218`） | 把技能文档里的红线变成了可执行断言，方向完全正确 |
| JVM 守卫测试约 87 个 | `app/src/test/java/cc/nkbr/lanzouplus/` 实测 87 个文件（84 tracked + 3 untracked，2026-10-02 采样；其他窗口仍在并发新增） | **最有特色的资产**：Robolectric + 静态源码断言 |
| 文档一致性守卫 | `DocsConsistencyJvmTest.kt:1-…`（README 不得声称手表可用）、`DocTimelinessJvmTest.kt`（AGENTS.md 不得把过期文档当事实源） | 把"文档会腐烂"做成了测试——业界罕见 |
| 仓库卫生守卫 | `RepoHygieneJvmTest.kt:1-87`（`git ls-files` 查调试残留、`.gitignore` 必须锚定） | 把 `:28` 直接调 `git` 进测试，务实 |
| 版本号守卫 2 个 | `VersionSchemeJvmTest.kt`（versionCode↔versionName 必须一一对应）、`VersionNumberSinglePlaceJvmTest.kt`（DFW-91：版本号只许一处） | 修的是"改了名字忘改号 → 用户永远收不到更新"这种**界面看不出来**的错 |
| 依赖可复现性守卫 | `DependencyHygieneJvmTest.kt:1-…`（禁 `-SNAPSHOT`/浮动版本） | 有效；R-12 已闭环 |
| 决策台账 | `docs/plan/decisions.md`（42 条，6 次提交，仍在更新） | 单文件决策日志，**已经在工作** |
| 风险台账 | `docs/plan/risk-register.md`（R-01–R-21，42 行） | 含"不能把'已验证'一直写成'待验证'"（`:41`）这种自我纠偏规则 |
| 事实页 / 总计划 / 任务入口 | `docs/plan/current-state.md`(237 行)、`DFWX-MASTER-PLAN.md`(179 行)、`docs/tasks/README.md` | 唯一入口已建成，R-14 已闭环 |
| 踩坑台账 | `docs/agents/lessons.md`(171 行，含"覆盖声明"与踩坑全文) | AGENTS.md 已瘦身并改为按需检索，方向正确 |
| 发版技能（**已入库**） | `.zcode/skills/dfwx-release/SKILL.md`（4497 字节，`git ls-files .zcode/` 可见 4 个技能） | 发版知识不是只活在某人脑子里，bus factor 已改善 |
| 仓库卫生基础 | `.gitattributes`（`* text=auto` + 按后缀钉 eol）、`.gitignore`（含"无锚规则曾吞源码包"的历史注释与根锚定修复） | 实测索引已 LF 归一化：`git grep --cached -Il $'\r' -- '*.java' '*.kt' '*.md'` 零命中 |
| 许可与 SBOM 简版 | `LICENSE`(AGPL-3.0)、`README.md:111-124` 致谢表、`docs/THIRD-PARTY-NOTICES.md`(5210 字节) | 且 `tools/release.sh:89-93` 每次发版强制校验署名义务 |

### 1.2 缺的（逐条实测确认不存在）

```
CHANGELOG.md                    MISSING
CONTRIBUTING.md                 MISSING
CODEOWNERS / .github/CODEOWNERS MISSING
.editorconfig                   MISSING
.github/dependabot.yml          MISSING
.github/PULL_REQUEST_TEMPLATE.md MISSING
.github/ISSUE_TEMPLATE/         MISSING
docs/adr/ 或 docs/decisions/    MISSING
```

（命令：对上述路径逐个 `[ -e ]` 判定，全部不存在。）

**但"缺"不等于"该补"**——见 §4。

### 1.3 已经建了、但实际不工作的（本轮最重要的发现）

**CI 门禁从来没有绿过。** 实测（`gh run list --repo dongwangxingchen/dongfang-wuxian --limit 100`）：

```
49  failure   ci-gates
49  failure   Smoke build (empty debug)
 1  cancelled ci-gates
 1  cancelled Smoke build (empty debug)
→ success 计数：0
最早一次运行：2026-09-30T11:56:37Z
```

三次独立的根因，**全部发生在"装 Android SDK"这一步，没有一次跑到真正的门禁**：

| # | 位置 | 实测报错 | 根因 |
|---|---|---|---|
| ① | `lanzouplus-empty.yml:42-43`（`android-actions/setup-android@v3`） | `Warning: Failed to find package 'tools'` → `sdkmanager` exit 1 | v3 的默认 `packages` 是 `tools platform-tools`；Google 已于 **2026-09-15** 下线 `tools` 包。上游 README 已把默认值改为 `platform-tools`，v4 才修 |
| ② | `ci-gates.yml:67` `packages: 'platforms;android-37,build-tools;37.0.0'` | `Failed to find package 'platforms;android-37,build-tools;37.0.0'` | `packages` 是**空格/换行分隔的列表**，这里传成了一个逗号串 |
| ③ | `ci-gates.yml:67` 与 `lanzouplus-empty.yml:48` 的包名 | 即使改成空格分隔也会失败 | 本机 `sdkmanager --list` 实测：平台包真名是 **`platforms;android-37.0`**（`$HOME/Library/Android/sdk/platforms/` 下目录也是 `android-37.0`），不存在 `platforms;android-37` |

**唯一稳定通过的是 `docs` 静态层**：`ci-gates.yml:37-46` 的 job 每次都是 ✓（7–8 秒）。

**为什么这条比"缺 CHANGELOG"重要得多**（证据等级 A）：
`tools/ci-gate.sh:3-8` 自己写着，建这套东西就是为了修"CI 从不跑任何 test 任务"的历史教训
（审计 B-4）。现在的事实是**反向的同一种病**：门禁在 CI 上恒定红灯，
而 `risk-register.md:26` 诚实登记了"CI 是否真的在 GitHub 上跑过、跑绿过，本轮未核实"——
本轮核实了：**没跑绿过**。一个恒红的门禁不是"更严格"，它是**训练人忽略红灯**；
再叠加 98 次同样的失败，就构成典型的告警疲劳。
连带后果：`tools/release.sh:82-83` 的 `verify` 会在本地跑 `ci-gate.sh docs` + `unit`（本地是绿的），
所以**本地与 CI 的结论已经不一致**——恰好违背了这套脚本存在的唯一理由。

### 1.4 "有但没人用"的

| 对象 | 证据 | 说明 |
|---|---|---|
| ~~`tools/*.ps1` 7 个~~ **已删除** | `git ls-files tools/`：`count_catch.ps1`、`fix_catch_batch1/3.ps1`、`fix_catch_main.ps1`、`fix_route_region.ps1`、`list_catch_ctx.ps1`、`splice_routeactivity.ps1`；全仓 `grep -rl` 无任何 md/sh/yml 引用；最后一次提交 `2275502`（2026-09-15） | **2026-10-02 由 DFW-122 第 7 条 `git rm` 删除**（7 个，未归档 —— git 历史即归档；删除前 `git grep -E '7 个名字'` 全仓唯一命中就是本行）。原判：Windows 时代一次性脚本，Mac 上不可执行（`lessons.md:94` 自己也标注"仅回 Windows 适用"） |
| `tools/B3ToolsTest.java`、`ConvertToolsTest.java`、`TextStatsTest.java` | 在 `tools/` 而不是 `app/src/test/`，无任何构建脚本引用 | 不在任何 test 源集里，等于不跑 |
| `tools/parked-tests/`（6 个） | `lessons.md:67` 说明 paparazzi 与 AGP9 不兼容已停泊 | 停泊是有意为之，但停在 `tools/` 顶层会让人以为是活代码 |
| `android-build.yml` | 只在 `workflow_dispatch` 触发；`gh run list` 中**从未出现过名为 `android-build` 的运行** | 与 `lanzouplus-empty.yml` 的冒烟编译职责重叠，且它连 `setup-android` 都没有（依赖 runner 预装 SDK） |
| `docs/agents/lessons.md:54` 的"发版三件套" | 规则写着"每版发布附**用户视角更新说明**（≤10 条）"；全仓 `grep -rn "用户视角更新说明"` **只命中这一行自己** | 规则无人执行；`tools/release.sh:227` 的 Release notes 是 `--notes "东方无限 $tag"` 空壳 |

### 1.5 已经试过并且失败的（最重要的历史证据）

`docs/archive/plan/20260925-maintainability-governance/` —— **16 个文件**的"长期维护治理包"
（基线、风险台账、目标架构、路线图、质量门禁、安全治理、发布治理、任务索引、任务模板、
待证据清单、文档登记册、决策日志、前端研究、G0 验收清单）。
`docs/archive/plan/20260926-long-term-execution/` —— **17 个文件**的长期专题路线（T1–T24）。

两者都在 **2026-09-27 被整体归档**，理由是"已被 `DFWX-MASTER-PLAN.md` + `risk-register.md` +
`decisions.md` 取代"（`docs/archive/plan/ARCHIVED.md`）。从建立到归档**只隔了两天**。

替代它的东西是：**4 个文件**（总计划 / 事实页 / 决策 / 风险）+ 一个任务面板。
那 4 个文件活到今天并且还在更新；33 个文件进了 `docs/archive/`（`docs/` 共 113 个 tracked 文件，
其中 96 个在 `archive/`）。

**这条历史事实是本文所有"不做"判断的主要依据**：在这个仓库里，
**文档的数量与它被使用的概率成反比**。

---

## 2. 差距清单

按「缺失 / 不规范 / 有但没人用」三类。每条只写**为什么这个仓库需要它**，
不写"业界都这么做"。标注证据等级。

### 2.1 缺失（建议补：3 条）

#### G-1｜CI 门禁的可信度（等级 A，**最高优先**）

**缺什么**：一套**实际能绿**的 CI 门禁，或者一份明确的"CI 只负责哪一层"的声明。

**为什么这个仓库需要它**：
1. 它的发布流程**天然依赖本地**（CI 无 keystore，`ci-gates.yml:16-18` 明确只做 unsigned 构建）。
   所以 CI 在这个项目里的唯一价值就是"**在我没跑本地门禁时替我兜底**"。
   现在它连兜底都做不到——它连测试都没跑到。
2. `tools/ci-gate.sh:4-8` 把"本地与 CI 结论可对照"写成了这套设施的设计目标。
   当前状态是**目标已经失败**：本地绿、CI 红，且红了 49 次没人管。
3. 恒红的门禁会让人对"红"脱敏。告警疲劳的定义就是"过量告警导致脱敏、对真实事故响应能力下降"。

**注意**：修它需要先做一个**判断**，不是单纯改字符串——见 §3 的 P0。

#### G-2｜人可读的发版记录（等级 A）

**缺什么**：一个**面向人的**、按版本组织的变更记录（`CHANGELOG.md`），
并把它接到 `tools/release.sh publish` 的 `--notes`。

**为什么这个仓库需要它**（三条独立理由，每条都有本仓证据）：
1. **规则已经存在但无人执行**：`docs/agents/lessons.md:54` 要求"每版发布附用户视角更新说明（≤10 条）"，
   全仓只有这一行提到它。规则没有落点，就会腐烂。给它一个文件 + 一个硬失败，规则才成立。
2. **Release 页面现在是空壳**：`tools/release.sh:227` 写死 `--notes "东方无限 $tag"`。
   而 `SECURITY.md:14-17` 向用户承诺"Release 页面附带 SHA-256、可自行校验"——
   用户在 Release 页看到的是一个只有标题的空说明。AGPL-3.0 分发场景下，
   用户拿到新包却不知道这版改了什么。
3. **现有更新记录是运行时依赖，不是仓库资产**：App 内的"更新记录"来自自建 PocketBase 的
   `changelog` 集合（`decisions.md:82-84`，`ChangelogJvmTest.kt:22-33` 守的是这条链路）。
   按 `decisions.md:80` 的 fail-open 决定，服务器故障时正常放行——也就是说
   **服务器一挂，历史更新记录就查不到了**。仓库里没有副本。
   `current-state.md` §1.1–§1.5 里有逐版本记录，但那是**会被压缩改写的状态页**
   （表头自己写着"§2–§5 记录的是 v1.22.x 时期的历史现场，很多已经不再成立"），
   不适合当长期档案。

**刻意排除的替代方案**：不要用 `git log` 自动生成。
Keep a Changelog 的第一条原则就是"Changelogs are *for humans*, not machines"，
并专门有一节讲"用 commit log diff 当 changelog 是坏做法：里面全是噪音"。

#### G-3｜技术债台账（等级 A）

**缺什么**：一处记录"我们**故意**欠下的债 + 什么时候必须还"的地方，且**数字不会腐烂**。

**为什么这个仓库需要它**：
1. **已经发生过一次严重的腐烂**。`risk-register.md:23`（R-10）原文写"`MainActivity.java`
   现约 2000 行"。同一行的 2026-10-01 更正视实测：写下该数字时是 2006 行，
   一天之内涨了 838 行到 4242 行。
   **本轮实测（2026-10-02）**：同一次调查内三次采样分别是 **4408 → 4433 → 4453 行**——
   **四十分钟里又涨了 45 行**（期间该文件一直处于他人未提交状态）。
   台账里的数字没有任何机制去复测它，所以它只能腐烂。
   **而且它同时在两个地方腐烂**：任务面板上的 `DFW-29`（ARCH-001，`in_progress`）标题里也写着
   "MainActivity **1981 行**"——同一个数字、同一份过期。**这不是某个人粗心，是没有机制。**
2. **风险 ≠ 债**。`risk-register.md:3` 自己声明"只记录当前风险和证据状态"。
   技术债（"我们知道这里丑，但先这样"）混进风险表，结果是两边都读不清：
   R-10 既不是"会不会发生"的风险，也不是"已闭环/未闭环"能表达的东西。
3. **这个仓库的解法应该是"可复测"，而不是"写得更详细"**。
   它已经在做架构适应度函数（fitness function）这件事了——
   `RepoHygieneJvmTest.kt:28` 直接调 `git ls-files` 来验证仓库卫生，
   `VersionSchemeJvmTest.kt` 直接读 `BuildConfig` 验证版本号不变量。
   技术债台账应该沿用同一招：**不写数字，写预算，让测试当那把尺子**。

### 2.2 不规范（建议修：3 条）

#### G-4｜决策记录的"取代"语义（等级 B）

**现状**：`docs/plan/decisions.md` 是一个**单文件、就地编辑**的决策日志（42 条，6 次提交）。
表头自己承认过一次表头没跟上正文（`:5-6`），#42 是**就地追加更正**（`:97-101`）。

**为什么这个仓库需要它**：ADR 的核心纪律不是"一决策一文件"，而是
"**决定一旦被接受就不该被改写，而应该被取代，并链接到取代它的那条**"，
这样才留下"什么决定统治了哪段时间"的清晰日志。
就地改写的风险是：接手者看到的是**当前结论**，看不到**它推翻过什么**——
而本项目恰恰多次依赖"原判断—新证据—新结论"这个链条
（`risk-register.md:42` 明确要求"新证据推翻旧结论时，保留原结论和更正原因"）。

**为什么不需要"完整 ADR 体系"**：见 §4。

#### G-5｜静态检查层的缺口：Android Lint 完全没跑（等级 A）

**现状**：`app/build.gradle.kts` 里**没有 `lint {}` 块**，`tools/ci-gate.sh` 的 `docs` 层是纯 grep，
`unit` 层只跑测试，`apk` 层只做产物断言。**全流程没有任何静态代码分析**。

**为什么这个仓库需要它**：
1. 它是**零新增依赖**的——Lint 是 Android Gradle Plugin 自带的，
   官方明确"It's strongly recommended that you correct any errors that lint detects before publishing your app"，
   并且可以直接从命令行跑。
2. 这个项目有 `AGENTS.md:38` 的铁律"禁止引入第三方依赖"，
   所以 detekt / ktlint 这类方案**天然出局**；Lint 是唯一不违规的选择。
3. 它修的是这个项目真实踩过的坑类型：`current-state.md` §1.1 记录了一次
   "aapt2 `--no-xml-namespaces` 剥离资源命名空间导致 Compose 矢量图闪退"的事故——
   Lint 正是检查资源/清单/API 兼容性这类结构性问题的工具。

**风险**：存量告警会很多。所以必须用 **baseline**（Lint 官方支持生成 baseline 文件，
把存量问题冻结、只挡新增），而不是一上来就 `abortOnError` 全量。**且 baseline 文件必须入库**，
否则每次都是全量红。

#### G-6｜文档索引与实际结构漂移（等级 A）

**现状**：`docs/plan/README.md`（13 行）列的"配套页面"只覆盖本目录 **4 个**文件
（`DFWX-MASTER-PLAN.md` / `current-state.md` / `decisions.md` / `risk-register.md`）+ 2 个外部链接，
而 `docs/plan/` 实际有 **11 个** `.md`（不含本文）。
**没有**被登记的包括：5 个 `dfw-*.md` 专题文档、`efficiency-record.md`、`remote-control-plan.md`。

**为什么这个仓库需要它**：`DFWX-MASTER-PLAN.md:22-33` 定义的"唯一接手入口"是一条**有序阅读链**。
索引漂移的直接后果是接手者（下一个 AI 窗口）会漏读专题文档，
而本项目的历史教训正是"旧文档误导接手者"（`DocTimelinessJvmTest.kt:9-13` 记录的 DFW-5 事故）。
这一条**成本极低**（改一个 13 行文件），但它是"入口是否可信"的问题。

### 2.3 有但没人用（建议清：2 条）

#### G-7｜Windows 时代死脚本与孤儿测试（等级 A）

**现状**：见 §1.4。`tools/` 下 ~~7 个 `.ps1`~~（**2026-10-02 已按 DFW-122 第 7 条 `git rm` 删除**）、
3 个孤儿 `.java` 测试、`tools/parked-tests/` 6 个。

> ✅ **执行状态（2026-10-02，DFW-122）**：G-7 里的 **7 个 `.ps1` 已删除**；
> 3 个孤儿测试与 `tools/parked-tests/` 不在本卡范围。删除方式选用 `git rm` 而非本文 P5 原本建议的
> `git mv` 归档（见 §P5）——理由是项目已有 `git` 历史作为归档，且 DFW-122 验收要求
> "文档里不再有指向已删除文件的引用"。下方"为什么需要清理"的判断仍然成立。

**为什么这个仓库需要清理**：
`lessons.md:68` 自己标注"命令速查（**Windows 时代**，仅回 Windows 适用）"，`lessons.md:94` 标注 PowerShell 坑"仅回 Windows 适用"，
而 `AGENTS.md:24-32` 全篇已是 Mac 路径（`~/heiyao/src`）与真机流程。
留着它们的代价不是磁盘，而是**误导**：下一个接手者/AI 在 `tools/` 里找构建入口时，
会先看到 `make_release_*.ps1` 这一堆**看起来像发版脚本**的东西，
而真正的入口是 `tools/release.sh`。本项目已经因为"文档看起来可信但内容是旧的"栽过一次（DFW-5）。

#### G-8｜重复的冒烟工作流（等级 A）

**现状**：`android-build.yml`（从未运行过）与 `lanzouplus-empty.yml`（49 次全红）职责重叠，
都是"验证任意 commit 能编译"。

**为什么这个仓库需要它收敛**：3 个 workflow、235 行 YAML，
其中 2 个做同一件事且都失败；剩下 1 个是真正的分层门禁。
维护面越小，修好 G-1 的概率越大。

---

## 3. 落地清单（按优先级）

> 每条写清：**改动文件 / 验收方式 / 风险**。
> **P0 与 P1 必须先由用户拍板**（涉及"要不要保留 CI"这种方向性选择，按 `AGENTS.md:43` 铁律 8 先给选项）。

### P0｜让 CI 门禁有可信度（对应 G-1）

**先做一个决定**，三选一：

| 选项 | 内容 | 代价 |
|---|---|---|
| A（推荐） | 修好 `unit` 层：`setup-android` 升到 v4；`packages` 改为空格分隔的 `'platforms;android-37.0 build-tools;37.0.0'`；`lanzouplus-empty.yml:48` 同步改包名 | 改 2 个文件、几行；需验证 GitHub runner 上确实能装到 `android-37.0` |
| B | 降级：CI 只保留**稳定通过的 `docs` 静态层**，`unit`/`apk` 明确标注"只在本地跑" | 更省事，但失去"忘了跑门禁时有人兜底"的价值 |
| C | 删掉 `ci-gates.yml` 的 `unit`/`apk` job，只留 `lanzouplus-empty.yml` 的冒烟编译 | 门禁完全退回本地，与 `tools/ci-gate.sh:4-8` 的设计目标冲突 |

**若选 A，改动文件**：
- `.github/workflows/ci-gates.yml:64-67`（`packages` 格式 + 包名）
- `.github/workflows/lanzouplus-empty.yml:42`（升 v4）、`:48`（包名）
- 可选：删除 `.github/workflows/android-build.yml`（G-8）

**验收方式**（必须给证据，不能只看"改了"）：
1. 本机先证明包名正确：`$HOME/Library/Android/sdk/cmdline-tools/latest/bin/sdkmanager --list | grep 'platforms;android-3'`
   —— 已实测输出 `platforms;android-37.0 | 2 | Android SDK Platform 37.0`（**这就是 ③ 的证据**）。
2. push 后 `gh run list --repo dongwangxingchen/dongfang-wuxian --workflow ci-gates.yml --limit 1`
   必须出现 `success`，或至少 `unit` job 到达 `Run unit gate` 步骤。
3. 若 `unit` 层因测试环境问题仍红，**必须**在 `ci-gates.yml` 顶部注释写明"这一层目前不可信，别拿它当门禁"，
   并在 `risk-register.md` 登记——**宁可诚实地红，也不要假装在保护**。

**风险**：`compileSdk = 37`（`app/build.gradle.kts:27`）对应 `android-37.0` 这个新式小版本平台名，
GitHub runner 上是否可下载**尚未验证**（本机可下载不等于 runner 可下载）。
**这是本条唯一的未知项，必须在验收里显式确认。**

**依据**：
- setup-android 默认 `packages` 是 `platform-tools`、`tools` 包已下线：<https://github.com/android-actions/setup-android>
- 上游 issue（含 Google 2026-09-15 下线 `tools` 的时间点）：<https://github.com/android-actions/setup-android/issues/537>
- `sdkmanager` 包路径语法：<https://developer.android.com/tools/sdkmanager>
- 恒红告警的脱敏机制：<https://incident.io/blog/alert-fatigue-solutions-for-dev-ops-teams-in-2025-what-works>
- 未修复的破窗会传递"没人在乎"的信号：<https://ghostinthedata.info/posts/2026/2026-05-09-broken-window-theory/>

### P1｜技术债台账做成"可复测的预算"（对应 G-3）

**改动文件**：
- **新增** `docs/plan/tech-debt.md`：只放**表格**，每行 `ID | 位置 | 债是什么 | 触发条件（什么时候必须还） | 复测方式`。**不写会腐烂的数字**。
- **新增** `app/src/test/java/cc/nkbr/lanzouplus/TechDebtLedgerJvmTest.kt`：
  - 断言台账文件存在、每行单元格非空（防止写成空壳）；
  - **直接测量**并守住预算，例如 `MainActivity.java` 行数不得超过一个显式常量；
  - 失败信息必须给出"怎么办"（抽一个协作类，或者**有意识地**提高预算并说明理由）。
- 首行内容建议：`TD-01 | app/src/main/java/cc/nkbr/lanzouplus/MainActivity.java | 宿主超大 Activity（DFWX-ARCH-001） | 行数超过预算，或再往里加一个新领域逻辑 | TechDebtLedgerJvmTest 直接测量`。
- 其余条目从 `DFWX-MASTER-PLAN.md:114-116`（宿主渐进拆分顺序）和 `risk-register.md` 的"残留"项搬过来。

**预算数字需要用户拍板**：本轮实测 **4453 行**（同一次调查内从 4408 涨到 4453，见 §2.1 G-3）。
建议先定 **4600**（给正常迭代留余量，但明确"只许往下拆"）。
定太高等于没定；定太低会挡住正常迭代。**这是一个产品决策，不是技术细节**——它决定了接下来是先拆还是先加功能。

**验收方式**：
- `JAVA_HOME=… ./gradlew -p . :app:testEmptyDebugUnitTest --tests '*TechDebtLedgerJvmTest*'` 必须绿；
- **验鉴别力**（本项目的既有做法，见 `current-state.md:114`）：把预算临时调低 100 行，测试必须变红；恢复后变绿。
  **没有这一步就不算完成**——"写了就绿"的空测试在本项目已被明确点名。
- 台账里每一条都能被人手工复跑（`复测方式` 一列可执行）。

**风险**：
- 预算型测试容易被"顺手调高"。**缓解**：调高预算必须同时改台账并写明理由，`git log` 里可见——让提高预算变成一次**有意识的、可追溯的**行为。
- 不要把这个测试写成"扫全部大文件"的通用规则，否则会红一片。

**依据**：
- 架构适应度函数 / 自动治理策略：<https://continuous-architecture.org/practices/fitness-functions/>、
  <https://www.oreilly.com/library/view/building-evolutionary-architectures/9781492097532/ch04.html>
- 该做法在本仓已有先例（`RepoHygieneJvmTest.kt:28` 调 git、`VersionSchemeJvmTest.kt` 读 BuildConfig）

### P2｜补 `CHANGELOG.md` 并接到发版（对应 G-2）

**改动文件**：
- **新增** `CHANGELOG.md`：Keep a Changelog 1.1.0 格式（`Added/Changed/Fixed/Removed/Security`），
  ISO 日期（`YYYY-MM-DD`），最新在前，顶部留 `Unreleased`。
  **用中文写"用户视角"条目**——它同时就是 `lessons.md:54` 要求的那份"用户视角更新说明"。
- **改** `tools/release.sh:224-227`：`publish` 前从 `CHANGELOG.md` 抽出该 tag 的段落，
  作为 `--notes`；**抽不到就直接失败**（这是把规则变成断言，和这个脚本已有的做法一致）。
- **新增** `app/src/test/java/cc/nkbr/lanzouplus/ReleaseNotesCoverageJvmTest.kt`：
  断言 `CHANGELOG.md` 里存在当前 `BuildConfig.VERSION_NAME` 对应的段落，
  且段落非空、至少有一条 `- ` 条目。
- 回填最近 5–8 个版本（内容可从 `current-state.md` §1.1–§1.5 与 `git log` 蒸馏，
  但**必须人工改写成人话**，不能直接贴 commit 标题）。

**验收方式**：
- 守卫测试绿；且**验鉴别力**：临时删掉当前版本段落 → 必须红。
- 干跑发版：`tools/release.sh publish vX.Y.Z <apk>`（默认 dry-run，`release.sh:218`）——
  确认打印的 notes 就是 `CHANGELOG.md` 里那一段。
- 真发一次后 `gh release view <tag> --json body` 确认说明非空。

**风险**：
1. **"又多了个要手改的地方"**——这正是用户 DFW-91 明确反对的（"不然的话你每次更新都得改一堆地方"）。
   **缓解**：`CHANGELOG.md` **不写 versionCode**，只写版本名 + 日期 + 人话条目；
   且守卫测试保证"没写就红"，不靠人记。真正的版本号仍然只有 `app/build.gradle.kts:56` 的 `val buildCode` 一处。
2. 与 App 内"更新记录"（服务器 `changelog` 集合）重复。
   **缓解**：把仓库版定位为**归档副本 + Release 说明的来源**，不要求与服务器逐条同步；
   长期可考虑让服务器从仓库版同步，但**那是另一张卡，不在本方案内**。

**依据**：
- Keep a Changelog 1.1.0（人可读、每版必录、ISO 日期、`Unreleased` 段、GitHub Releases 是"不可移植的 changelog"）：<https://keepachangelog.com/en/1.1.0/>
- 本项目 `SECURITY.md:14-17` 已向用户承诺 Release 页可校验 → 空说明与承诺不匹配
- `docs/agents/lessons.md:54` 的既有规则（当前零执行）

### P3｜给决策日志加"取代"语义（对应 G-4，**最小改动**）

**改动文件**：只改 `docs/plan/decisions.md` 的表头规则，**不新建目录**。

加三行规则（放在 `:1-6` 的说明区）：
1. 每条决定必须带四要素：**决定 / 为什么 / 被什么取代 / 证据**；
2. **不得就地改写已生效的决定**——改主意时**新增一条**，并在旧条末尾写
   `→ 已被 #N 取代（日期）`；
3. 表头日期必须与正文最新条目一致（`DocTimelinessJvmTest.kt` 已有同类守卫可扩展）。

**验收方式**：
- 先按新规则把已有的"就地改写"案例补成"取代"形式（例：`current-state.md:152-153` 的
  AI-004 → DFW-73 推翻记录，和 `decisions.md:97-101` 的 #42 路径更正），
- 扩展 `DocTimelinessJvmTest` 或新增小测试：断言 `decisions.md` 里每条 `→ 已被 #` 都指向存在的编号；
  表头日期 ≥ 最后一条日期。

**风险**：规则太细会没人看。**所以只加这 3 行，不加模板文件、不加目录、不加编号工具。**

**依据**：
- ADR 应当是**短**的（"just a couple of pages"）、"一旦接受就不该被重新打开或修改，而应被取代并链接到取代它的决定"、"最重要的是简短"：<https://martinfowler.com/bliki/ArchitectureDecisionRecord.html>
- MADR 的最小模板核心：Title / Context and Problem Statement / Considered Options / Decision Outcome：<https://adr.github.io/madr/>

### P4｜把 Android Lint 接进 `docs` 层（对应 G-5）

**改动文件**：
- `app/build.gradle.kts`：加 `lint {}` 块，指向入库的 baseline；
- **新增** `app/lint-baseline.xml`（由 `./gradlew :app:lintEmptyDebug` 生成后入库）；
- `tools/ci-gate.sh`：新增 `lint` 步骤，**放在 `docs` 之后、`unit` 之前**（快、无需设备）；
- `.github/workflows/ci-gates.yml`：`docs` job 里加一步（或在 `unit` job 里跑，视耗时定）。

**分层理由**：快跑的先跑。测试金字塔的核心主张就是"把跑得快的测试放在流水线更早的阶段"，
"越往上层，测试应该越少"。

**验收方式**：
- 本机 `JAVA_HOME=… ./gradlew -p . :app:lintEmptyDebug` 跑通，生成 baseline；
- 制造一个新告警（如删掉某个已声明权限对应的使用），确认 Lint 能抓到；
- `tools/ci-gate.sh docs` 全绿。

**风险**：
- **baseline 会掩盖存量问题**——这是刻意的取舍，必须写在 `ci-gate.sh` 注释里（沿用本仓"红线写在脚本注释"的习惯）；
- 若 Lint 首次全量跑耗时过长（vendor 11 个模块），**只对 `:app` 开**，vendor 区按上游 catalog 不动
  （`AGENTS.md:38` 的 vendor 例外）。

**依据**：
- Lint 是 AGP 自带、可命令行运行、官方"强烈建议发布前修掉"：<https://developer.android.com/studio/write/lint>
- 流水线分层与快速反馈：<https://martinfowler.com/articles/practical-test-pyramid.html>

### P5｜仓库清理（对应 G-7、G-8）

**改动文件**：
- ~~`git mv tools/*.ps1 docs/archive/windows-era/`（7 个）——**归档不删除**，符合 `docs/archive/README.md:13` 的"不删除历史证据"；~~
  ✅ **实际执行（2026-10-02，DFW-122 第 7 条）：改为 `git rm` 直接删除，未归档。**
  原建议的归档方案没有采纳，理由：`git` 历史本身就是不可删除的归档（`git show 2275502:tools/count_catch.ps1` 永远可取回），
  而 `docs/archive/` 按 `docs/archive/README.md` 的定位是**文档**归档；把这 7 个 Windows 时代脚本搬进去，
  等于把"误导下一位接手者"的东西从 `tools/` 挪到了另一个他会看到的地方。删除前已 `git grep` 全仓确认零引用。
- `git mv tools/{B3ToolsTest,ConvertToolsTest,TextStatsTest}.java tools/parked-tests/`；
- 删除 `.github/workflows/android-build.yml`（从未运行、与冒烟工作流重复）；
- `tools/` 下另加一行 `README.md`，写清"入口只有 `ci-gate.sh` / `release.sh` / `dfwx-publish-apk.sh`"。

**验收方式**：`git ls-files tools/` 输出只剩活跃文件；`tools/ci-gate.sh all` 仍绿；
`gh workflow list` 只剩 2 个。

**风险**：低。历史都在 git 里，可随时取回。**唯一注意**：`git mv` 会改路径，`risk-register.md` / `lessons.md` 里若有旧路径引用需同步。

### P6｜文档索引守卫（对应 G-6）

**改动文件**：`docs/plan/README.md` 补齐本目录 11 个 `.md`；
扩展 `DocsConsistencyJvmTest.kt`：断言 `docs/plan/` 下每个 `.md` 都在 `README.md` 里被提及。

**验收方式**：测试绿；**验鉴别力**：新建一个 `.md` 不登记 → 必须红。

**风险**：低。但**不要**把规则扩展到 `docs/archive/`（96 个文件，会让测试变成负担）。

---

## 4. 明确"不做"的清单

> 判据只有一条：**它能不能减少这个仓库未来的返工？**
> 不能的，无论多"专业"，都是负债。这个仓库已经用 33 个归档文件证明过"堆文件"的代价。
>
> **出处约定**（按 `AGENTS.md:36` 铁律 1"项目代码给 `文件:行号`，规范给链接"）：
> 依据是**本仓已生效的决定或规则**时给 `文件:行号`；依据是**外部规范**时给 URL。两者不互相替代。

### ❌ 不做 1｜CODEOWNERS / PR 模板 / issue 模板 / 分支保护

**实测事实**：`gh pr list --state all` → **0 条**；`gh issue list --state all` → **0 条**；
`gh api repos/.../branches/test/protection` → **`Branch not protected`（404）**；
`git branch --show-current` → `test`；255 个提交全部直推。

**为什么不做**：GitHub 官方对 CODEOWNERS 的定义就是
"当有人打开修改其所有代码的 pull request 时，代码所有者会被自动请求评审"，
以及"在作者合并 PR 前要求代码所有者批准"。这个仓库**没有 PR**，
所以 CODEOWNERS 的**全部功能**（自动请求评审、强制批准）都无处生效。
issue 模板同理：0 个 issue。
分支保护在这个项目里还有一个反效果：CI 恒红（§1.3），一旦开启 required status checks，
**所有合并都会被永久挡住**——而官方定义就是"所有必需的检查都必须通过，协作者才能合并"。

**唯一可能沾边的场景**：如果将来真的接受外部贡献，再按需加。开源指南的立场也是
"在项目最早阶段，你的 CONTRIBUTING 文件可以很简单"——即按需生长，不是预置。

**依据**：<https://docs.github.com/repositories/managing-your-repositorys-settings-and-features/customizing-your-repository/about-code-owners>、
<https://docs.github.com/repositories/configuring-branches-and-merges-in-your-repository/defining-the-mergeability-of-pull-requests/about-protected-branches>、
<https://opensource.guide/starting-a-project/>

### ❌ 不做 2｜`dependabot.yml`

**为什么不做**：Dependabot 版本更新的工作方式是"**为每个需要更新的依赖打开一个 pull request**"。
在一个 0 PR 的仓库里，它的产出就是一堆没人处理的 PR——
上游自己的博客都得专门写一篇"驯服 Dependabot：把更新分组、放慢节奏"来治这个噪音。

**这个仓库有更合适的替代，而且已经在跑**：
`DependencyHygieneJvmTest.kt` 禁浮动版本（`-SNAPSHOT` / `latest.release` / `+` / 动态区间），
`rikkahub/gradle/libs.versions.toml:69` 已钉到不可变 commit，
`docs/THIRD-PARTY-NOTICES.md` 记录组件与"已移除组件"。
**这比自动 PR 更贴合本仓**：本项目真正的依赖风险是"JitPack 上的浮动版本绕过评审"（审计 B-1），
不是"版本落后"。而且 vendor 区依赖按上游 catalog 管理（`AGENTS.md:38` 例外条款），
自动升级会直接破坏"路径一一对应、可 diff 跟随上游"这个**核心可维护性设计**。

**依据**：<https://docs.github.com/code-security/reference/supply-chain-security/dependabot-options-reference>、
<https://github.blog/security/supply-chain-security/tame-dependabot-group-your-updates-slow-the-cadence-keep-security-fast/>

### ❌ 不做 3｜Gradle 模块化拆成 `:core:* / :feature:*`（Now in Android 式）

**为什么不做**——三条独立证据，**包括官方自己**：
1. **官方架构建议明确分级**：Android 官方把"使用 domain 层"标为 **"Recommended in big apps"**，
   并写明"在小型应用里，你可以选择把 data 层类型放在一个 `data` 包或模块里"；
   整份文档的开头就是"把这些建议当作**建议**而不是硬性要求，按你的应用情况调整"。
2. **Now in Android 自己说要平衡**：这份被广泛当作模块化范本的项目文档原文写着，
   他们的目标是"在**过度模块化一个相对小的应用**与展示一套适用于更大代码库的模式之间找到平衡"，
   并明确"**如果你的 data 层很小，把它放在单个模块里完全没问题**"。
   官方模块化指南第一句也是"There is no single modularization strategy that fits all projects."
3. **本仓的规模不支持它**：主机区实测 **46 个 Java 文件**（`git ls-files 'app/src/main/java/**'`），
   而 vendor 区**本来就是 11 个模块**（`settings.gradle` 实测：12 条 `include`，其中 11 条指向 `rikkahub/`）。
   也就是说这个仓库**已经有**一套模块化体系了——在上游代码那一侧，而且做得对
   （路径与上游一一对应，便于 diff 跟随）。

**真正的债是"类太大"，不是"模块太少"**：`MainActivity.java` 4453 行（2026-10-02 实测）、
`LanzouCore.java` 1757 行、`ToolHost.java` 1535 行（2026-10-02 实测）。
把 46 个文件拆进 10 个 Gradle 模块，**不会让这 3 个文件少一行**，
却会带来 build 配置、版本目录、模块间依赖可见性的持续维护成本。

**该做的是它已经在计划里的事**：`DFWX-MASTER-PLAN.md:114-116` §F 已经定好了顺序
（`SettingsRepository` → `ExternalActionRouter` → `DownloadCoordinator` → …），
原则是"每次先测试、再移动、再删旧入口；**不一次性重写 `MainActivity`**"。
这正是增量替换（Strangler Fig）的写法。**保持这个方向，只是要给它一个预算和节奏（P1）。**

**依据**：<https://developer.android.com/topic/architecture/recommendations>、
<https://github.com/android/nowinandroid/blob/main/docs/ModularizationLearningJourney.md>、
<https://developer.android.com/topic/modularization/patterns>、
<https://martinfowler.com/bliki/StranglerFigApplication.html>、
<https://shopify.engineering/refactoring-legacy-code-strangler-fig-pattern>

### ❌ 不做 4｜一决策一文件的 `docs/adr/` 树 + 完整 MADR 模板

**为什么不做**：
1. `decisions.md` **已经在工作**：42 条、6 次提交、仍在更新，而且接手链
   （`DFWX-MASTER-PLAN.md:22-33`）明确指向它。它没坏。
2. **这个仓库试过重的结构，两天就归档了**：16 个文件的治理包里就包含 `12-decision-log.md`
   和 `11-document-registry.md`，2026-09-27 整体进 `docs/archive/`。
3. MADR 的完整模板含 `Decision Drivers` / `Considered Options` / `Pros and Cons of the Options`
   / `Confirmation` / `More Information` 等节。对一个**单人 + AI 协作**的仓库，
   为 42 条已有决定逐条补齐这些节的成本，远大于它带来的收益——
   而且 ADR 作者自己强调的核心是"**最重要的是简短**"，不是"模板要全"。

**做的是 P3**：只给现有文件加"取代语义 + 四要素"，不新建目录、不加模板文件。

**依据**：<https://martinfowler.com/bliki/ArchitectureDecisionRecord.html>、<https://adr.github.io/madr/>

### ❌ 不做 5｜`.editorconfig`

**为什么不做**——**证据不足**，不是"理念上反对"：
1. `.editorconfig` 的官方定位是"帮助**多个开发者在同一个项目上**跨各种编辑器和 IDE
   保持一致的编码风格"。本仓是单人 + AI，且工具链固定（Android Studio + Gradle）。
2. **实际风险已经被覆盖且实测有效**：`.gitattributes` 的 `* text=auto` + 按后缀钉 `eol` 已经管住了
   行尾这个唯一会真正造成 diff 噪音的东西。实测：
   `git grep --cached -Il $'\r' -- '*.java' '*.kt' '*.md'` → **零命中**（索引已 LF 归一化）；
   `git status --short` 对工作树里含 CRLF 的文件报 clean（说明是归一化行为，不是脏改动）。
3. 历史里也**没有**空白字符 churn 的证据。

**什么情况下应该改变这个结论**（写在这里，免得将来重新争论）：
一旦真的出现"多个 AI 工具/编辑器轮流改同一批文件导致格式 diff 噪音"的**实际事故**，
再加——那时它就有据可依了。

**依据**：<https://editorconfig.org/>、<https://git-scm.com/docs/gitattributes>

### ❌ 不做 6｜`CONTRIBUTING.md`

**为什么不做**：CONTRIBUTING 的价值是"告诉**外部贡献者**怎么参与"。
这个仓库 0 PR、0 issue、单人开发，**没有外部贡献者**。
它已经有的替代品**更强**：`AGENTS.md`(9243 字节) 是给"下一个 AI 窗口"看的贡献规范，
`docs/handover/README.md` 是 30 秒接手页，`.zcode/skills/dfwx-release/SKILL.md` 是发版规范。
再加一个 `CONTRIBUTING.md` 只会**增加一条可能腐烂的入口**——
而"多个入口互相矛盾"正是这个项目花了好几轮才治理掉的问题
（`docs/archive/plan/ARCHIVED.md` 记录 33 个文件被归档就是为了收拢入口）。

**依据**：<https://opensource.guide/starting-a-project/>（CONTRIBUTING 面向贡献者）

### ❌ 不做 7｜Conventional Commits 强制

**为什么不做**：
1. **本仓已经明确废止过它**：`docs/agents/lessons.md:88`（2026-09-27 审计消解）写着——
   历史 60+ 提交均为"vX.Y.Z + 中文要点"风格，前缀条文**从未执行过**，规范与现实对齐，不引入前缀。
   现在 255 个提交仍是这个风格。**推翻一条已经消解过的决定需要新证据，而新证据不存在。**
2. **它的收益是自动化驱动的**：规范自己列的理由是"自动生成 CHANGELOG""根据提交类型自动决定语义化版本号提升"。
   本项目的版本号已经**只有一处**（`app/build.gradle.kts:56` 的 `val buildCode`），
   且有 `VersionSchemeJvmTest` / `VersionNumberSinglePlaceJvmTest` 守着；
   变更记录按 P2 是**人工撰写面向用户的条目**，而 Keep a Changelog 专门警告过
   "不要把 commit log diff 倒进 changelog"。**两个自动化收益在这里都不成立。**

**依据**：<https://www.conventionalcommits.org/en/v1.0.0/>、<https://keepachangelog.com/en/1.1.0/>、本仓 `docs/agents/lessons.md:88`

### ❌ 不做 8｜严格 SemVer 化版本号

**为什么不做**：SemVer 的第一条硬性要求是"**使用语义化版本的软件必须声明一个公共 API**"。
它是一个**给依赖方用的契约**；本仓库交付的是一个 Android APK，没有程序化消费的公共 API。
而且本仓已有自己的编号方案（`versionCode = major*10000 + minor*100 + patch`，
`app/build.gradle.kts:45`）并配了守卫测试，方案本身是自洽的。
**换成严格 SemVer 只会破坏已有的守卫，换不来任何东西。**

**依据**：<https://semver.org/>

### ❌ 不做 9｜完整 SBOM（CycloneDX）与 Gradle 依赖锁定

**为什么不做**：
1. 它会引入**新的 Gradle 插件**，直接撞 `AGENTS.md:38` 铁律 3"禁止引入第三方依赖，除非用户明确批准"。
2. 它已经在 `risk-register.md:25`（R-12 残留）登记为"**需用户批准**"——**程序是对的，不要绕过它**。
3. 现有替代已经覆盖了主要风险：`docs/THIRD-PARTY-NOTICES.md`（SBOM 简版，含已移除组件）、
   `DependencyHygieneJvmTest`（禁浮动版本）、`tools/release.sh:89-93`（发版强制校验 AGPL/署名/第三方清单）。

**结论**：保持现状，等用户就 R-12 拍板。
**如果将来要做**，先看官方做法再决定引入哪个插件（Gradle 自带的依赖校验，
比第三方 CycloneDX 插件更贴近"零新依赖"这条铁律）：
<https://docs.gradle.org/current/userguide/dependency_verification.html>

**依据**：本仓 `AGENTS.md:38`（禁止擅自引入依赖）、`docs/plan/risk-register.md:25`（R-12 残留：需用户批准）、
<https://docs.gradle.org/current/userguide/dependency_verification.html>

### ❌ 不做 10｜把 `docs/handover/` 提交入库

**为什么不做**：`decisions.md:53`（#29）是**用户 2026-09-28 明确拍板**的：
"`docs/handover/` 交接文档**故意不提交、不 push**"，
并且"若发现 GitHub 上出现 `docs/handover/` 痕迹，说明有人违反了该决定，应回退"。
`tools/release.sh:127` 还专门为它做了排除（否则"工作区不干净"检查会永远失败、变成噪音）。
**这条不是缺口，是设计。** 任何"顺手把它入库让它更规范"的冲动都应该被这条挡住。

**代价要如实说明**：换机器或目录被删时，30 秒接手页**找不回来**（`AGENTS.md:75-76` 已如实登记）。
**缓解**：这正是 §1.2/§2.1 要求"仓库内文档必须自足"的理由——
接手者退回到 `docs/plan/` 的 4 个文件 + `AGENTS.md` 也必须能开工。
**这个代价是用户已知并接受的，不需要"修"。**

**依据**：本仓 `docs/plan/decisions.md:53`（用户决定优先于任何外部规范）

---

## 5. 优先级总览

| 优先级 | 项目 | 对应差距 | 改动量 | 需用户拍板 |
|---|---|---|---|---|
| **P0** | CI 门禁可信度（修 / 降级 / 删，三选一） | G-1 | 2 个 YAML，几行 | ✅ 方向性 |
| **P1** | 技术债台账做成可复测预算 | G-3 | 1 新 md + 1 新测试 | ✅ 预算数字 |
| **P2** | `CHANGELOG.md` + 接到 release notes | G-2 | 1 新 md + 改 release.sh + 1 新测试 | ❌ |
| P3 | `decisions.md` 加"取代"语义（3 行规则） | G-4 | 1 个文件表头 | ❌ |
| P4 | Android Lint 接进 `docs` 层（零新依赖） | G-5 | build.gradle + baseline + ci-gate.sh | ❌ |
| P5 | 清 Windows 死脚本 / 去重工作流 | G-7, G-8 | 纯 `git mv` + 删 1 个 yml | ❌ |
| P6 | 文档索引守卫 | G-6 | 改 13 行 + 扩展已有测试 | ❌ |

**做完 P0–P2 的净效果**：
1. 门禁重新有信号（而不是恒红的噪音）；
2. 技术债第一次有了"不会被忘记"的机制（R-10 那种"约 2000 行"的腐烂不可能再发生）；
3. `lessons.md:54` 那条沉睡的规则变成硬失败，Release 页面不再是空壳。

**做完这一整套，仓库里新增 3 个文件**（`CHANGELOG.md`、`docs/plan/tech-debt.md`、`app/lint-baseline.xml`）
**+ 1–3 个守卫测试**（纯新增测试文件，不是新机制），**同时删除/归档 11 个以上**（7 个 `.ps1` + 3 个孤儿测试 + 1 个重复 workflow）。
（其中 **7 个 `.ps1` 已于 2026-10-02 由 DFW-122 删除**，其余待做。）
这是刻意的：**规范化的目标是让下一个接手者更快开工，不是让目录更好看。**

---

## 6. 给下一个接手者的一句话

这个仓库最值钱的东西不是它的文档，是它**已经把"文档会不会腐烂"变成了测试**这件事。
所以继续加文档之前，先问一句：**这条规则能不能写成一个会变红的测试？**
能，就写测试；不能，就**别写**——`docs/archive/` 里躺着 33 个文件的答案。
