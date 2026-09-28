# 东方无限：当前状态

> 这是当前事实页，不是历史计划。源码、命令输出和当前规范优先于旧文档。
> 更新日期：2026-09-28（RikkaHub 2.5.5 clean import 完成）

## 1. 工作区

- 桌面交接目录：`/Users/<用户名>/Desktop/东方无限`（仅存<本地备份> zip，勿删勿动）
- 实际源码根目录：`/Users/<用户名><仓库根>`
- 当前分支：`test`
- 当前 HEAD：`0e37e6a`（ADB-001 状态车道 + generation 闸门）
- **未提交在途**：RikkaHub 2.5.1 → **2.5.5 clean import**（vendor 整体替换 + 补丁重放）
- 回退点：git tag **`dfwx-pre-255-import`**；vendor 备份 `<本地目录>/vendor-backup-before-255-20260928.tar.gz`（43MB）；旧补丁台账 `<本地目录>/PATCHES-231-backup-20260928.md`
- applicationId：`dfwx.dongdang`；宿主 Java 包：`cc.nkbr.lanzouplus`
- `rikkahub/`：Kotlin/Compose vendor（**上游锚点 = re-ovo/rikkahub tag `2.5.5`，versionCode 190**）与东方无限补丁层
- 上游源码快照：`/tmp/rikkahub-255/rikkahub-2.5.5/`（临时目录，重装需重下）
- 桌面<本地备份> `东方无限_交接总包_20260920.zip`：禁止删除或移动

## 2. 当前在途变更（RikkaHub 2.5.5 clean import）

vendor 由上游 2.5.5 整体替换后重放全部补丁。**补丁台账权威来源 = `rikkahub/PATCHES.md`**（P1–P26 + U1 + U4–U8），同步操作清单落盘在 `rikkahub/.dfwx-rsync-excludes.txt`。

本次关键结构变更：

- **P16 顶层化**：上游 `RouteActivity.AppRoutes()` 成员函数抽为顶层 `ui/routes/AppRoutes.kt`，差异仅 4 处；`RouteActivity` 瘦身为 Activity 外壳；新增 `dfwx/VolumeKeyBridge.kt`（进程级音量键注册表，宿主 `MainActivity.dispatchKeyEvent` 转发）；`dfwx/RikkaHubEmbed.kt` 由 421 行复制品缩为约 60 行壳。**不再维护 400+ 行路由复制品——这是"上游更新我们也能跟"的长期可维护性底座。**
- **AI-004 落地**：删除 `dfwx/BuiltinProviderSeeder.kt`，移除构建期 resValue 注入（`dfwx_default_ai_url/_model/_key`）与 `keep.xml` 对应条目，新增 `dfwx/DfwxBuiltinProviderCleanup.kt` 清理存量（走 AI-003 保守身份证明）。**不再内置任何 API。**
- **P17 品牌化**：`DongfangTheme` 置首 + 全新安装默认主题；背景改 OLED 真黑 `#000000`；主题名走自有资源 `R.string.dfwx_theme_name`；`findPresetTheme` 兜底东方主题。
- **P25（新增）**：关闭上游默认开启的 `dynamicColor`——否则 Android 12+ 会用系统壁纸取色完全绕过预设主题，东方配色名义生效实际失效。
- **U1/NET-001 重放**：AI 链 HTTPS-only 四道防线（拦截器 + 每跳校验 + DNS 防 rebinding + 禁跨协议重定向）。
- **U4–U8 重放**：EmojiBurst 空闲挂起（原上游逐帧空转烧 CPU）；三处空态 72dp FAB 净空；SettingWebPage 88dp 底部净空。
- **依赖跟进**：haze-glass（2.5.5 新增）、quickjs 换 `io.github.dokar3:quickjs-kt:1.0.15`、floatingx 迁 `io.github.petterpx:*:3.0.0`（旧登记的迁移触发条件已满足，已完成）。
- **新发现的同步陷阱**：`app/src/main/keepRules/` 必须排除（P9 已改名 `dfwxKeepRules/`，AGP9 库模块会拒绝 consumer 规则中的 `-dontobfuscate`）；已补进 `.dfwx-rsync-excludes.txt`。

## 3. 已有证据

- 外部下载已分为 `lanzou`、`external`、`update`、`legacy`；
- 外部下载入口已做 HTTPS、凭据、loopback、私网和 DNS fail-closed 策略（SEC-001，v1.22.5 起在发版链路中）；
- 外部/legacy APK 不允许自动安装或 ADB/Shizuku 静默安装，安装前需要用户确认；
- AI-002 设计报告已入库：AppRoutes 顶层化 + 窄 wrapper + 返回桥栈深守卫（`docs/tasks/DFWX-AI-002-report.md`）——**本次 2.5.5 import 已按该报告落地 P16**；
- AI-003 数据保护层已入库：`SettingsDataGuard`（保守身份证明/悬空引用修复/迁移不变量门），12 项契约测试全过（含聊天历史保护，`data/dfwx/` 下）；
- NET-001 已入库并重放：vendor AI 链 HTTPS-only（`AiUrlPolicy` + 拦截器 + DNS 防线，18 用例；AI 专用 client 派生，不影响非 AI 流量）；
- SEC-003 已入库：release 签名 fail-closed，缺配置构建失败，`-Pdfwx.unsigned` CI 开关保留；
- STAB-001 已入库（`54837c9`）：下载历史 debounce 落盘 + flush 不阻塞主线程 + generation/owner 双校验；
- ADB-001 已入库（`0e37e6a`）：ADB/Shizuku 状态车道 + generation 闸门（修旧评估覆盖新状态的竞态）；
- 补丁台账 `rikkahub/PATCHES.md` 已按 2.5.5 基线重写（P25/P26/U1/U4–U8 全部登记，含"同步上游时需重放"标注）；
- **测试基线（2026-09-28 本机 JVM 实测，2.5.5 替换后）**：宿主 51 例 + vendor 240 例 + ai 199 例 + common 全绿，合计 **490 例**。

## 4. 当前尚未完成

- **release 构建终验**：`:app:assembleEmptyRelease` 全量 R8 + aapt 检查（arm64-v8a、单 launcher、APK 内不再有 `dfwx_default_ai_*` 三个资源）——**进行中**；
- **真机验证**：2.5.5 内嵌 AI 页在真机的表现（嵌入、主题、音量键滚动、动画）——需要用户手机，须先请示；
- AI-005：AI 生命周期、日志、HTTPS 和回归门禁（SEC-004 日志脱敏的前置）；
- SEC-004 日志脱敏；TEST-001 测试矩阵；
- 赞助页、关于页、致谢页和品牌信任页面重做；
- release 真机负向链路验证（用户已豁免日常下载/安装类验证）；
- UI 四卡、宿主领域拆分、vendor/依赖/CI/SBOM/许可证治理、动画帧级证据；
- **用户待办（只有用户能做）**：① 去 <已停用渠道> 停用/更换 v1.22.6 公开 APK 中已泄露的 AI Key；② 阿里云控制台开启 MFA + 操作保护。

## 5. 当前第一执行任务

已完成：BASE-001 归档、AI-001 基线核对、SEC-001 下载/安装边界、AI-002 设计报告、AI-003 数据保护层、NET-001、SEC-003 签名 fail-closed、补丁台账治理、STAB-001、ADB-001、**RikkaHub 2.5.5 clean import（P16 顶层化 + AI-004 + P17/P25 品牌化 + U1/U4–U8 重放）**。

**下一个执行任务是 `DFWX-TEST-001`**（测试矩阵；STAB-001 与 ADB-001 均已入库，前置齐备），其后按 `docs/tasks/README.md` 队列推进（AI-005 → SEC-004 → …）。研究报告与当前代码有出入时，以当前代码为准。

## 6. 证据等级

- A：当前源码、当前测试输出或真实产物检查；
- B：项目规范和已核对的任务文档；
- C：历史计划、旧截图、旧版本行号；
- D：模型推测。

任何任务完成说明必须写明证据等级和未验证部分。
