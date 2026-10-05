# 东方无限：当前风险台账

> 只记录当前风险和证据状态。完成一项后必须补真实命令、测试或产物证据。
> 更新日期：**2026-10-02（v1.0.26）**——本轮逐条复核后更正：R-09/R-12/R-13 由"未开始/仍在"改为已闭环，
> R-15/R-16 补上真机验收结论，R-10 更正真实行数（2000 → 4242，当晚 4267），R-11 补 DFW-86 取证与修复，
> R-19 更正失效行号，R-20 补自有后台放行与真实用例数；其余真机待验项统一补"截至 2026-10-01"时间戳。
> 逐条证据与命令见 `docs/audit/20261001-docs-consistency.md`。
> **本台账的测量基线**：复核开始时 HEAD `185e747`，期间另一窗口提交了 `5d9dd40`（DFW-86），
> 涉及行数的条目已同时标注两个版本的值。
> **2026-10-02 深夜追加**：新增 **R-22**（预测性返回手势不覆盖三个 Activity 的 `onBackPressed`）——
> 来源是 DFW-123 的 lint `GestureBackNavigation`，逐条分类见 `docs/plan/lint-triage.md`。
> 其余条目本轮**未复核**，行号仍以上述基线为准。
> 上一版更新日期：2026-09-30（v1.22.13）。

| ID | 等级 | 风险 | 当前状态 | 下一证据 | 责任任务 |
|---|---|---|---|---|---|
| R-01 | P1 | 外部下载可能越过安全边界 | 已完成第一轮来源隔离、HTTPS 和安装确认策略；手机 release、重定向和完整 APK 校验未完成（用户已豁免日常下载/安装类真机验证）。**截至 2026-10-01 仍未验，本轮无新证据** | fake 重定向、APK 包名/签名/版本测试 | DFWX-SEC-001 |
| R-02 | ✅ 已闭环 | ~~旧 AI 接入和 vendor 补丁继续叠加导致不可维护~~ | **2.5.5 clean import 完成（`6eb27b7`）**：补丁台账按上游 2.5.5 基线重写、路由复制品由 400+ 行降为 4 处差异、rsync 排除清单落盘 | 已完成 | DFWX-AI-001/002 |
| R-03 | ✅ 已闭环 | ~~内置 AI Key/URL/模型仍可能进入构建资源~~ | **AI-004 已完成**：播种器与 resValue 注入移除，存量走 `DfwxBuiltinProviderCleanup` 清理。**2026-10-01 补充**：AI-004 的"不内置"决定已被 DFW-73 部分推翻（恢复「内置渠道」，改走服务端中转），但**本条风险仍成立**——APK 里只有自有服务器地址与应用令牌，上游地址与真 Key 只在服务器 | 已完成 | DFWX-AI-004 / DFW-73 |
| R-04 | ✅ 已闭环 | ~~用户 Provider、模型或聊天历史被迁移误删~~ | **AI-003 已完成**：`SettingsDataGuard` 保守身份证明 + 12 项契约测试（`SettingsDataGuardTest` 11 例 + `SettingsDataGuardChatHistoryTest` 1 例，实测 `grep -c @Test` 合计 12） | 已完成 | DFWX-AI-003 |
| R-05 | ✅ 已闭环 | ~~release 缺少签名时使用 fallback 或产出不可信包~~ | **SEC-003 已完成**：硬编码回退已删，缺配置构建失败 | 已完成 | DFWX-SEC-003 |
| R-06 | ✅ 已闭环 | ~~AI 请求/响应/SSE 日志泄露 Key 或用户内容~~ | **SEC-004 已完成（v1.22.12）**：取证发现真正的常开泄露点是 okhttp `Level.HEADERS`（release 也开）与 4 个 Provider 的 SSE 全量响应体，而非任务卡点名的自研拦截器（那个默认关闭）。三处全部收敛；新增 `DfwxLogRedactor` 并把脱敏做进 `Logging.logRequest()` 唯一存储入口。三道防线逐一验过鉴别力 | 已完成 | DFWX-SEC-004 / DFW-8 |
| R-07 | ✅ 已闭环 | ~~下载历史写入、进程重建和生命周期不稳定~~ | **STAB-001 已完成（`54837c9`）**：debounce 落盘 + 不阻塞主线程 + generation/owner 双校验 | 已完成 | DFWX-STAB-001 |
| R-08 | ✅ 已闭环（真机待验） | ~~ADB/Shizuku 后台状态旧结果覆盖新状态或阻塞主线程~~ | **ADB-001 已完成（`0e37e6a`）**：状态车道 + generation 闸门；真机 Shizuku 复测待用户授权。**截至 2026-10-01 仍未验**（用户当天那次真机验收只覆盖顶栏错位/动画性能/切页不断流三项，未含 Shizuku） | 真机复测 | DFWX-ADB-001 |
| R-09 | ✅ 已闭环（真机视觉验收待用户） | ~~赞助、关于和致谢页面入口/状态/文案割裂~~ | ~~赞助页当前还多了一个闪退（R-15）需先修；整体重做未开始~~ **2026-10-01 复核：该结论已过期。** ① 闪退（R-15）已于 v1.22.9 修复并由用户真机验收（2026-09-29）；② 页面本身：BRAND-001 赞助页结构与交互收口（`362c6c1`）、BRAND-002 关于/致谢逐项核对结论"内容已完整，补回归守卫防止被删"（`a9cc034`）。守卫测试实测 **7 个类共 65 例**：`BrandCreditsJvmTest` 11 + `SupportPageHeaderJvmTest` 3 + `SupportPageInAppJvmTest` 10 + `BrandingCleanlinessJvmTest` 7 + `SupportPageCopyJvmTest` 7 + `SupportPageTransitionJvmTest` 8 + `SupportPageFeedbackJvmTest` 19。**剩余：手机 release 视觉验收**（DFW-27/DFW-28 停在 `in_review`） | 手机 release 视觉验收 | DFWX-BRAND-001/002 → DFW-27 / DFW-28 |
| R-10 | P1 | 宿主超大 Activity 继续堆补丁 | 已确定渐进拆分顺序，尚未开始；~~`MainActivity.java` 现约 2000 行~~ **2026-10-01 实测：`185e747` = 4242 行，当晚 `5d9dd40` = 4267 行；2026-10-02 复查再测 = 4527 行（又涨了）**——不但没拆，还比台账写下"约 2000 行"时**涨了一倍多**（写下该数字的 `b0ddbbd` 当期实测 2006 行；一天之内 `8379e92`→`185e747` 就长了 838 行）。风险等级**上升**，不是持平。注：当晚 DFW-42 首次启动引导已在按"只挂一行、实现另起 `FirstRunGuide.java`"的方式做，方向与本项一致 | 每个领域独立测试和回退 diff | DFWX-ARCH-001 → DFW-29 |
| R-11 | P1（**2026-10-01 重新打开后已取证并修复**） | 动画"抽帧/过快"根因未确认 | 历史改动存在，~~帧级证据未完成~~；**注意：v1.22.8 的 IME 崩溃正是"未取证就叠自研动画"的教训——本项必须坚持先取证**。**2026-10-01 经过**：当天 UI-001（DFW-23）曾由用户口头验收后关闭 → 用户再次抱怨（连续三轮"割裂/预动画/渐隐不丝滑"）→ 傍晚新建专卡 **DFW-86** 做全站取证 → **当晚已取证并修复入库（`5d9dd40`）**。取证结论：`MainActivity` 37 条动画语句里 **22 条设了时长却没设曲线**，`ViewPropertyAnimator` 默认走 `AccelerateDecelerateInterpolator`，与站内声明的 M3 emphasized 手感相反 → **全站同时存在两个缓动族**，这才是"割裂"的根因（不是某处参数写错）。修法：懒建 `standardEase()` 并给 22 条补上曲线；复查后 0 条使用默认曲线；新增 `MotionCurveUniformityJvmTest`（4 例，含"喂假代码必须抓出来"的鉴别力自检），反向探针 4 红。**残留（作者自己在提交里"诚实登记"）**：这是**静态取证、不是真机帧级取证**，修复后的真机手感未测；曲线统一是必要条件不是充分条件，下一个怀疑对象是**时长档位**（15 档，明显随手写）；弹簧与规范"触摸路径禁物理弹簧"的矛盾**只登记未改** | 真机手感验收 + 时长档位是否收编 | DFW-86（原 DFWX-UI-001 / DFW-23） |
| R-12 | ✅ 已闭环（完整 SBOM 待用户批准） | ~~vendor master 快照、SNAPSHOT/JitPack 和许可证不可追溯~~ | **2026-10-01 复核：`-SNAPSHOT` 已不存在。** `rikkahub/gradle/libs.versions.toml:69` = `sqlite-android = "80cedc8888df2fe1d22d7f9bf8d9287f621624be"`（不可变 commit；:64-68 有 P36 注释说明原因）；全仓 `grep -i snapshot` 在**版本目录与构建脚本里零命中**（其余命中只有 `PATCHES.md` 的历史叙述、`snapshotFlow` API 名、旧审计归档）；上游锚点 = tag `2.5.5`（非 master）；SBOM 简版 `docs/THIRD-PARTY-NOTICES.md` 已建；守卫 `DependencyHygieneJvmTest`（4 例，改回 `-SNAPSHOT` 立刻 2 红）。**残留**：完整 SBOM（CycloneDX）与依赖锁定（全仓无 `gradle.lockfile` / `verification-metadata.xml`）未做，前者按"禁止擅自引入依赖"红线需用户批准。另注：`rikkahub/PATCHES.md:98`「已知风险登记」仍写着 SNAPSHOT 风险（未同步 P36 结论），该文件属 vendor 区，本轮未改 | 完整 SBOM（需用户批准） | DFWX-DEP-001 → DFW-30 |
| R-13 | ✅ 已闭环 | ~~CI 只验证部分构建链路~~ | **2026-10-01 复核：分层门禁已建成。** `tools/ci-gate.sh`（DFW-31，`04ea81f`）提供 `docs` / `unit` / `apk` **三层**，本地与 CI **同一个脚本**（"结论可对照"）；`.github/workflows/ci-gates.yml` 三层 job 串行 `needs`（docs→unit→apk），替换"从不跑任何 test 任务"的旧工作流；CI 无 keystore，只做 unsigned 构建，并把产物改名 `dongfang-wuxian-UNSIGNED-DO-NOT-DISTRIBUTE.apk` 防误分发。**残留（不构成本风险）**：CI 是否真的在 GitHub 上跑过、跑绿过，本轮未核实（未查 Actions 运行记录） | 已完成（CI 实跑记录未核） | DFWX-CI-001 → DFW-31 || R-13 | ✅ 已闭环 | ~~CI 只验证部分构建链路~~ | **2026-10-01 复核：分层门禁已建成。** `tools/ci-gate.sh`（DFW-31，`04ea81f`）提供 `docs` / `unit` / `apk` **三层**，本地与 CI **同一个脚本**（"结论可对照"）；`.github/workflows/ci-gates.yml` 三层 job 串行 `needs`（docs→unit→apk），替换"从不跑任何 test 任务"的旧工作流；CI 无 keystore，只做 unsigned 构建，并把产物改名 `dongfang-wuxian-UNSIGNED-DO-NOT-DISTRIBUTE.apk` 防误分发。**残留（不构成本风险）**：CI 是否真的在 GitHub 上跑过、跑绿过，本轮未核实（未查 Actions 运行记录） | 已完成（CI 实跑记录未核） | DFWX-CI-001 → DFW-31 |

> **2026-10-02 复查更正（R-13 补记）**：上面那句「分层门禁已建成」只说明**脚本写好了**，
> 不说明**它在 CI 上跑得通**。实测 `gh run list --limit 100`：**failure 91 / cancelled 5 / success 4**，
> 也就是说从建起来那天起它一次都没绿过 —— 而"红了很久"本身就是最大的掩护，
> 所有人都默认"CI 就是红的"，没人去看日志。真正的根因有三层，逐层修完才通：
>
> 1. **平台包名写错**：`platforms;android-37` 的真名是 `platforms;android-37.0`；
>    `ci-gates.yml` 还把 `packages` 传成逗号串（该空格分隔）；冒烟流程用
>    `setup-android@v3` 且没给 `packages`，该 action 默认装**已下线**的 `tools` 包。
> 2. **`gradlew` 在 git 里的模式是 100644**（没有可执行位）—— 检出后
>    `./gradlew: Permission denied`（exit 126）。本地磁盘上是 `-rwxr-xr-x`，
>    所以本地永远正常、只有 CI 挂。
> 3. **APK 门禁自己的正则写错了**：断言 `usesCleartextTraffic` 必须为 false 的模式写成
>    `...)="(type 0x12)0x0"`，而 aapt 真实输出是 `...)=(type 0x12)0x0`（**没有引号**）。
>    多一个 `"` 就永远匹配不上 —— 红的是门禁自己，不是代码。`allowBackup` 那条同病。
>
> **2026-10-02 实测结果**：`36964048067` 三个 job **全部 success**
> —— 静态门禁 5s / JVM 测试门禁 251s / release 产物门禁 594s，
> 加上 Smoke build（`36964048064`，7m48s）也 success。
> **这是这个仓库的 CI 第一次完整通过。**
> 同轮还把 9 个模块 336 例测试（占全仓 24%）补进了 unit 门禁。
| R-14 | ✅ 已闭环 | ~~历史计划过多导致后续 AI 误读~~ | **BASE-001 已完成**：唯一入口建成、旧目录归档；`docs/tasks/README.md` 现有"当前现场速查"节 | 已完成 | DFWX-BASE-001 |
| R-15 | ✅ 已闭环（**真机已验**） | ~~AI 设置页"赞助"按钮点击后闪退（真机 v1.22.8）~~ | **已修（v1.22.9）**：根因=宿主 aapt2 参数 `--no-xml-namespaces` 剥离全部 res 二进制 XML 命名空间 → Compose 矢量图解析器（走带命名空间的查找）读不到 `viewportWidth` → 退回 0f → `painterResource` 抛 `XmlPullParserException`。A/B 对照实验坐实；回归测试 `ApkXmlNamespaceJvmTest`（2 例）修复前 2 例红、修复后绿；release APK 内命名空间 0→1。**2026-10-01 复核：真机验收早已完成**——用户 2026-09-29 原话「修好了，改的很棒，一点bug也没有。」（DFW-1 评论，卡已关闭）。台账原写"真机待验"**已过期** | 已完成 | DFWX-BRAND-003 → DFW-1 |
| R-16 | ✅ 已闭环（**真机已验**） | ~~AI 页键盘弹出时顶部顶栏错位（真机 v1.22.8）~~ | 现象曾确认（v1.22.8 后输入框正常、顶栏仍错位）；~~根因未定~~。**2026-10-01 复核**：① 契约守卫 `AiInsetsContractJvmTest` **5 例**（`86fc478`；台账旧文若写 3 例即误）——钉住 TopAppBar 显式清零 `windowInsets`、宿主裁 statusBars/navigationBars/displayCutout 但**保留 ime**、insets 处理里禁 `ValueAnimator`/`WindowInsetsAnimation`/`stickyBelow`/`postOnAnimation`、键盘弹出前后 `paddingTop` 必须恒定；② **用户 2026-10-01 真机验收通过**：「没有错位了」（DFW-2 评论，卡已关闭）——"真机现象是否消失未确认"**已过期**。注：DFW-2 自己的评论记着 P38 那条清零实际不改变任何一帧几何，用户看到的好转对应 DFW-72 的修复（`8379e92`） | 已完成 | DFWX-UI-005 → DFW-2 / DFW-72 |
| R-17 | ✅ 已闭环（真机待验） | ~~AI 页空会话占位文案在键盘弹出时位置异常，且用户已判定为无用~~ | **已删除（v1.22.12）**：P19/P20/P21 整块移除，`ChatList.kt` 与上游 2.5.5 diff 只剩已声明保留的 P16（用上游快照核验）。文案在仓库内零引用。**截至 2026-10-01 真机仍未确认**（用户当天验收的三项不含"空会话无文字"） | 真机确认空会话无文字 | DFWX-UI-006 / DFW-3 |
| R-18 | P2 | 发布资产命名/校验流程曾出错（v1.22.8 中文名截断致下载 404） | 已修（重新上传 + sha256 核对）；防复发条款已写进技能 `dfwx-release`（`5a854f1`）。**2026-10-01 补充**：红线已从"人记"升级为"可执行断言"——`tools/release.sh`（`verify` / `build` / `publish`，publish 默认 dry-run）已建成；此后多次发版（v1.22.9 → v1.0.3）未再复现。**新的分发面**：自有服务器 `http://<你的服务器>/apk/` 已成主路径（DFW-59，decisions #33），APK 用版本化文件名、不覆盖固定名 | 下次发版按 `tools/release.sh` 执行 | DFWX-RELEASE-001 → DFW-32 |
| R-19 | P1 | **同类隐患（R-15 的孪生面）**：宿主 59 个矢量图 + vendor 矢量图全部依赖该命名空间。除赞助页外，docx/pdf 附件图标（`ui/components/message/ChatMessage.kt:537/545`）、deepthink 图标（`ui/components/ai/ModelList.kt:876`、`ui/pages/chat/Export.kt:678`）走同一 `painterResource` 路径，此前均为潜在闪退点 | 随 R-15 一并修复（同一构建参数）；回归测试已覆盖命名空间完整性。**2026-10-01 更正**：`ModelList.kt` 行号由 846 更正为 **876**（846 已因文件变动失效）。**截至 2026-10-01 真机冒烟仍未做**——DFW-1 的验收只明确覆盖"赞助页不闪退"，附件/模型列表那条只是当时列的"顺带冒烟"，无单独确认记录 | 真机冒烟：AI 页发带附件消息、模型列表展开 | DFWX-BRAND-003 → DFW-1 |
| R-20 | ✅ 已闭环 | ~~清单全局放开明文流量（`usesCleartextTraffic="true"` 且无网络安全配置），与 decisions #9 冲突~~ | **已修（v1.22.13）**：新增 `res/xml/network_security_config.xml`，base-config 默认拒绝明文；仅按需放行**有界**目标。**2026-10-01 更正**：放行清单不止"蓝奏 8 个域名池 + 回环/`.local`"——`network_security_config.xml:56` 还放行**自有后台 `<你的服务器>`**（decisions #33：走 HTTP，安全性由 sha256+包名+签名证书三重校验保证）。宿主与 vendor 的 WebView 同步收紧 file/content 访问与混合内容。守卫测试 `NetworkSecurityConfigJvmTest` 实测 **10 例**（不是 6 例、也不是 8 例 —— 这两个数字都被改过至少一次，2026-10-02 重新数过）：钉死"不得出现全局放行/通配符"。**截至 2026-10-01 真机冒烟仍未做** | 真机冒烟：蓝奏解析、AI 请求、图片加载 | DFW-10 |
| R-21 | ✅ 已闭环 | ~~更新检查是死代码：`maybeCheckForUpdates()` 零调用点，用户永远收不到新版本提示~~ | **已修（v1.22.12）**：补启动后 6 秒静默检查 + 24h 落盘节流 + 设置页手动入口。`UpdateTriggerJvmTest`（4 例）断言"接线"而非算法，验过鉴别力。**2026-10-01 补充**：更新源已从"仅 GitHub"改为**自有后台优先 + GitHub 兜底**（DFW-59 `d852559`，fail-open），随后 DFW-82 修掉"控制台 versionCode 不自增导致永远判定最新"的死结。**截至 2026-10-01，"真机看到一次新版本提示"仍未作为独立证据记录**（DFW-82 卡仍停 `backlog`，无验收评论） | 真机验一次新版本提示 | DFW-7 / DFW-59 / DFW-82 |
| R-22 | P3 | **预测性返回手势（predictive back）不覆盖三个 Activity 的返回处理**：`FeedbackPage.onBackPressed()`（`FeedbackPage.java:284`）、`LanzouWebActivity.onBackPressed()`（`LanzouWebActivity.java:93`）、`SupportActivity.onBackPressed()`（`SupportActivity.java:126`，DFW-123 加抑制后行号右移）走的都是旧的 `onBackPressed()` 回调。返回键与三键导航下**正常**；但 Android 13+ 用户开启「预测性返回手势」时边缘滑动**不走这条路**，用户可能看到系统默认的"滑回上一屏"预览而不是本页统一的退场转场（观感割裂）。来源：DFW-123 lint 的 `GestureBackNavigation`，见 `docs/plan/lint-triage.md` §3.2 B-1 | **已知、有意接受**。三处均为 `@SuppressLint("GestureBackNavigation")` + 行内理由注释；迁移到 `OnBackPressedDispatcher` 会**改动返回动画行为（用户可见）**，属方案选型，不塞进 lint 清零卡。**截至 2026-10-02 未在真机验过** | 真机（真机（Android 16） / Android 16）开「预测性返回手势」后，边缘滑动这三个页面，录屏对比是否有动画割裂 | 待建卡（建议并入下一轮 UI 体验批次） |

## 结论规则

- P0 未闭环不能进入正式发布；
- P1 必须有负责人、测试和明确未验证项；
- 不能把"代码已改"写成"真实设备已验证"；
- **也不能把"已验证"一直写成"待验证"**（2026-10-01 新增：R-09/R-15/R-16 就是这么腐烂的）；
- 新证据推翻旧结论时，保留原结论和更正原因。
