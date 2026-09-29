# 东方无限：当前风险台账

> 只记录当前风险和证据状态。完成一项后必须补真实命令、测试或产物证据。
> 更新日期：2026-09-30（v1.22.13：R-06/R-17 闭环；新增 R-20 明文流量已收敛；DFW-7 更新检查死代码已修）

| ID | 等级 | 风险 | 当前状态 | 下一证据 | 责任任务 |
|---|---|---|---|---|---|
| R-01 | P1 | 外部下载可能越过安全边界 | 已完成第一轮来源隔离、HTTPS 和安装确认策略；手机 release、重定向和完整 APK 校验未完成（用户已豁免日常下载/安装类真机验证） | fake 重定向、APK 包名/签名/版本测试 | DFWX-SEC-001 |
| R-02 | ✅ 已闭环 | ~~旧 AI 接入和 vendor 补丁继续叠加导致不可维护~~ | **2.5.5 clean import 完成（`6eb27b7`）**：补丁台账按上游 2.5.5 基线重写、路由复制品由 400+ 行降为 4 处差异、rsync 排除清单落盘 | 已完成 | DFWX-AI-001/002 |
| R-03 | ✅ 已闭环 | ~~内置 AI Key/URL/模型仍可能进入构建资源~~ | **AI-004 已完成**：播种器与 resValue 注入移除，存量走 `DfwxBuiltinProviderCleanup` 清理 | 已完成 | DFWX-AI-004 |
| R-04 | ✅ 已闭环 | ~~用户 Provider、模型或聊天历史被迁移误删~~ | **AI-003 已完成**：`SettingsDataGuard` 保守身份证明 + 12 项契约测试 | 已完成 | DFWX-AI-003 |
| R-05 | ✅ 已闭环 | ~~release 缺少签名时使用 fallback 或产出不可信包~~ | **SEC-003 已完成**：硬编码回退已删，缺配置构建失败 | 已完成 | DFWX-SEC-003 |
| R-06 | ✅ 已闭环 | ~~AI 请求/响应/SSE 日志泄露 Key 或用户内容~~ | **SEC-004 已完成（v1.22.12）**：取证发现真正的常开泄露点是 okhttp `Level.HEADERS`（release 也开）与 4 个 Provider 的 SSE 全量响应体，而非任务卡点名的自研拦截器（那个默认关闭）。三处全部收敛；新增 `DfwxLogRedactor` 并把脱敏做进 `Logging.logRequest()` 唯一存储入口。三道防线逐一验过鉴别力 | 已完成 | DFWX-SEC-004 / DFW-8 |
| R-07 | ✅ 已闭环 | ~~下载历史写入、进程重建和生命周期不稳定~~ | **STAB-001 已完成（`54837c9`）**：debounce 落盘 + 不阻塞主线程 + generation/owner 双校验 | 已完成 | DFWX-STAB-001 |
| R-08 | ✅ 已闭环 | ~~ADB/Shizuku 后台状态旧结果覆盖新状态或阻塞主线程~~ | **ADB-001 已完成（`0e37e6a`）**：状态车道 + generation 闸门；真机 Shizuku 复测待用户授权 | 真机复测 | DFWX-ADB-001 |
| R-09 | P1 | 赞助、关于和致谢页面入口/状态/文案割裂 | 赞助页当前**还多了一个闪退（R-15）需先修**；整体重做未开始 | JVM 结构测试和手机 release 验收 | DFWX-BRAND-001/002 |
| R-10 | P1 | 宿主超大 Activity 继续堆补丁 | 已确定渐进拆分顺序，尚未开始；`MainActivity.java` 现约 2000 行 | 每个领域独立测试和回退 diff | DFWX-ARCH-001 |
| R-11 | P1 | 动画"抽帧/过快"根因未确认 | 历史改动存在，帧级证据未完成；**注意：v1.22.8 的 IME 崩溃正是"未取证就叠自研动画"的教训——本项必须坚持先取证** | setter/cancel/layout/frame interval/gfxinfo 对照 | DFWX-UI-001 |
| R-12 | P1 | vendor master 快照、SNAPSHOT/JitPack 和许可证不可追溯 | 上游已固定 tag `2.5.5`（改善）；`sqlite-android:-SNAPSHOT`（jitpack）风险仍在 | 上游 commit、依赖锁定、SBOM、NOTICE | DFWX-DEP-001 |
| R-13 | P1 | CI 只验证部分构建链路 | 尚未完成分层门禁 | JVM、Manifest、unsigned release、签名和资产检查 | DFWX-CI-001 |
| R-14 | ✅ 已闭环 | ~~历史计划过多导致后续 AI 误读~~ | **BASE-001 已完成**：唯一入口建成、旧目录归档；`docs/tasks/README.md` 现有"当前现场速查"节 | 已完成 | DFWX-BASE-001 |
| R-15 | ✅ 已闭环（真机待验） | ~~AI 设置页"赞助"按钮点击后闪退（真机 v1.22.8）~~ | **已修（v1.22.9）**：根因=宿主 aapt2 参数 `--no-xml-namespaces` 剥离全部 res 二进制 XML 命名空间 → Compose 矢量图解析器（走带命名空间的查找）读不到 `viewportWidth` → 退回 0f → `painterResource` 抛 `XmlPullParserException`。A/B 对照实验坐实；回归测试 `ApkXmlNamespaceJvmTest` 修复前 2 例红、修复后绿；宿主 57 例全绿；release APK 内命名空间 0→1 | 真机装机验收（待用户） | DFWX-BRAND-003 |
| R-19 | P1 | **同类隐患（R-15 的孪生面）**：宿主 59 个矢量图 + vendor 矢量图全部依赖该命名空间。除赞助页外，docx/pdf 附件图标（`ChatMessage.kt:537/545`）、deepthink 图标（`ModelList.kt:846`、`Export.kt:678`）走同一 `painterResource` 路径，此前均为潜在闪退点 | 随 R-15 一并修复（同一构建参数）；回归测试已覆盖命名空间完整性 | 真机冒烟：AI 页发带附件消息、模型列表展开 | DFWX-BRAND-003 |
| R-16 | P1 | AI 页键盘弹出时顶部顶栏错位（真机 v1.22.8） | 现象已确认（v1.22.8 后输入框正常、顶栏仍错位），根因未定；宿主 insets 裁剪链 + Compose TopAppBar 默认 padding 为候选机制 | 真机逐帧 insets 日志（需请示操作用户手机） | DFWX-UI-005 |
| R-17 | ✅ 已闭环（真机待验） | ~~AI 页空会话占位文案在键盘弹出时位置异常，且用户已判定为无用~~ | **已删除（v1.22.12）**：P19/P20/P21 整块移除，`ChatList.kt` 与上游 2.5.5 diff 只剩已声明保留的 P16（用上游快照核验）。文案在仓库内零引用 | 真机确认空会话无文字 | DFWX-UI-006 / DFW-3 |
| R-18 | P2 | 发布资产命名/校验流程曾出错（v1.22.8 中文名截断致下载 404） | 已修（重新上传 + sha256 核对）；防复发条款已写进技能 `dfwx-release`（`5a854f1`） | 下次发版按新流程执行 | DFWX-RELEASE-001 |
| R-20 | ✅ 已闭环 | ~~清单全局放开明文流量（`usesCleartextTraffic="true"` 且无网络安全配置），与 decisions #9 冲突~~ | **已修（v1.22.13）**：新增 `res/xml/network_security_config.xml`，base-config 默认拒绝明文；仅按需放行**有界**域名（蓝奏 8 个域名池 + 回环/`.local`）。宿主与 vendor 的 WebView 同步收紧 file/content 访问与混合内容。守卫测试 `NetworkSecurityConfigJvmTest`（6 例）钉死"不得出现全局放行/通配符" | 真机冒烟：蓝奏解析、AI 请求、图片加载 | DFW-10 |
| R-21 | ✅ 已闭环 | ~~更新检查是死代码：`maybeCheckForUpdates()` 零调用点，用户永远收不到新版本提示~~ | **已修（v1.22.12）**：补启动后 6 秒静默检查 + 24h 落盘节流 + 设置页手动入口。`UpdateTriggerJvmTest`（4 例）断言"接线"而非算法，验过鉴别力 | 真机验一次新版本提示（需先发一个更高版本） | DFW-7 |

## 结论规则

- P0 未闭环不能进入正式发布；
- P1 必须有负责人、测试和明确未验证项；
- 不能把"代码已改"写成"真实设备已验证"；
- 新证据推翻旧结论时，保留原结论和更正原因。
