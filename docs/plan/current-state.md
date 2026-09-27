# 东方无限：当前状态

> 这是当前事实页，不是历史计划。源码、命令输出和当前规范优先于旧文档。
> 更新日期：2026-09-27

## 1. 工作区

- 桌面交接目录：`/Users/lishaowei/Desktop/东方无限`
- 实际源码根目录：`/Users/lishaowei/heiyao/src`
- 当前分支：`test`
- 当前 HEAD：`871b690`（AI-003/NET-001/SEC-003/台账治理四提交，v1.22.5 后）
- applicationId：`dfwx.dongdang`
- 宿主 Java 包：`cc.nkbr.lanzouplus`
- `rikkahub/`：Kotlin/Compose vendor 和东方无限补丁
- 桌面交接包 `东方无限_交接总包_20260920.zip`：禁止删除或移动

## 2. 当前未提交改动

工作区干净（2026-09-28 验证）。AI-002 设计报告、AI-003 数据保护层、NET-001 AI 地址策略、SEC-003 签名 fail-closed、补丁台账治理均已入库。接手时仍须先运行 `git status --short` 复核。

## 3. 已有证据

- 外部下载已分为 `lanzou`、`external`、`update`、`legacy`；
- 外部下载入口已做 HTTPS、凭据、loopback、私网和 DNS fail-closed 策略（SEC-001，v1.22.5 起在发版链路中）；
- 外部/legacy APK 不允许自动安装或 ADB/Shizuku 静默安装，安装前需要用户确认；
- AI-002 设计报告已入库：AppRoutes 顶层化 + 窄 wrapper + 返回桥栈深守卫（`docs/tasks/DFWX-AI-002-report.md`）；
- AI-003 数据保护层已入库：`SettingsDataGuard`（保守身份证明/悬空引用修复/迁移不变量门），12 项契约测试全过（含聊天历史保护，`data/dfwx/` 下）；
- NET-001 已入库：vendor AI 链 HTTPS-only（`AiUrlPolicy` + 拦截器 + DNS 防线，18 用例；AI 专用 client 派生，不影响非 AI 流量）；
- SEC-003 已入库：release 签名 fail-closed，缺配置构建失败，`-Pdfwx.unsigned` CI 开关保留；
- 补丁台账已补齐 P20/P21/P24，修正 P18，P13 标注废弃；RikkaHubEmbed.kt 三处乱码已修；
- 全量 `:app:testEmptyDebugUnitTest`、`:rikkahub-app:testDebugUnitTest`、`:ai:testDebugUnitTest` 均通过（2026-09-28 本机 JVM 实测，CI 仍不含测试任务）。

## 4. 当前尚未完成

- AI-004：移除内置 AI 播种与构建注入（依赖 AI-003 保护层，已具备；实施拆分见 AI-002 报告 §五）；
- AI-005：AI 生命周期、日志、HTTPS 和回归门禁（SEC-004 日志脱敏的前置）；
- SEC-004 日志脱敏、STAB-001 下载稳定性、ADB-001 竞态（前置 STAB-001）、TEST-001 测试矩阵；
- 赞助页、关于页、致谢页和品牌信任页面重做；
- release 真机负向链路验证（用户已豁免日常下载/安装类验证）；
- UI 四卡、宿主领域拆分、vendor/依赖/CI/SBOM/许可证治理、动画帧级证据。

## 5. 当前第一执行任务

已完成（2026-09-27/28）：BASE-001 归档、AI-001 基线核对（2.5.4，`7263dd36`）、SEC-001 下载/安装边界、AI-002 设计报告、AI-003 数据保护层、NET-001 AI 地址 HTTPS-only、SEC-003 签名 fail-closed、补丁台账治理。

**下一个执行任务是 `DFWX-STAB-001`**（下载历史、并发和生命周期；前置 SEC-001/NET-001 均已具备），其后按 `docs/tasks/README.md` 队列推进（ADB-001 → TEST-001 → …）。研究报告与当前代码有出入时，以当前代码为准。

## 6. 证据等级

- A：当前源码、当前测试输出或真实产物检查；
- B：项目规范和已核对的任务文档；
- C：历史计划、旧截图、旧版本行号；
- D：模型推测。

任何任务完成说明必须写明证据等级和未验证部分。
