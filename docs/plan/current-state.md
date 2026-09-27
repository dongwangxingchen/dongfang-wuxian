# 东方无限：当前状态

> 这是当前事实页，不是历史计划。源码、命令输出和当前规范优先于旧文档。
> 更新日期：2026-09-27

## 1. 工作区

- 桌面交接目录：`/Users/<用户名>/Desktop/东方无限`
- 实际源码根目录：`/Users/<用户名><仓库根>`
- 当前分支：`test`
- 当前 HEAD：`30af1f0`（v1.22.4）
- applicationId：`dfwx.dongdang`
- 宿主 Java 包：`cc.nkbr.lanzouplus`
- `rikkahub/`：Kotlin/Compose vendor 和东方无限补丁
- 桌面<本地备份> `东方无限_交接总包_20260920.zip`：禁止删除或移动

## 2. 当前未提交改动

当前工作树不是干净的。已知未提交内容包括：

- 宿主外部下载来源、HTTPS URL 和安装边界改动；
- 对应 `DownloadPolicyTest`；
- `docs/plan/` 治理文档；
- `docs/tasks/` 既有专题文档。

任何接手 AI 必须先运行 `git status --short`，不得 reset、checkout 覆盖、清理或假定这些改动无效。

## 3. 已有证据

- 外部下载已分为 `lanzou`、`external`、`update`、`legacy`；
- 外部下载入口已做 HTTPS、凭据、loopback、私网和 DNS fail-closed 策略；
- 外部/legacy APK 不允许自动安装或 ADB/Shizuku 静默安装，安装前需要用户确认；
- `DownloadPolicyTest` 已通过（2026-09-27 本机 JVM 实测）；**注意：该测试与被测策略文件当前为 untracked，CI 不含任何测试任务——本地通过 ≠ CI 验证**（2026-09-27 全仓审计 M-4）；
- 最近完整 `testEmptyDebugUnitTest` 已通过（同为本地执行，CI 不跑测试）；
- 设置页部分按压、图标闪烁和展开收起历史改动已在 v1.22.2–v1.22.4 中实现，但还没有完成最终手机 release 验收。

以上不等于外部 APK 已完成包名/签名/版本校验，也不等于手机 release 负向链路已验证。

## 4. 当前尚未完成

- 旧 AI 对话整体替换为确认版本的 RikkaHub；
- AI 宿主接入、Provider/模型/聊天数据迁移和生命周期回归；
- 移除东方无限内置 AI 播种和构建注入；
- 赞助页、关于页、致谢页和品牌信任页面重做；
- 外部 APK 完整校验、真实重定向和 DNS rebinding 证据；
- release 签名缺配置 fail-closed；
- AI 请求/响应/SSE 日志脱敏；
- 下载历史、生命周期、ADB/Shizuku 和 crash logger 治理；
- 宿主领域拆分、vendor/依赖/CI/SBOM/许可证治理；
- 动画帧级根因证据和手机 release 性能验收。

## 5. 当前第一执行任务

2026-09-27 已完成的两件底座事项：

1. 旧文档已分类归档到 `docs/archive/plan/`、`docs/archive/tasks/`（原文件名保留，各目录有 `ARCHIVED.md` 说明）；
2. RikkaHub 稳定版已核对：官方最新稳定版 **2.5.4**（commit `7263dd36`，versionCode 189）；当前 vendor 为 2.5.1/186 混 master 快照，不能称稳定版。报告见 `docs/tasks/DFWX-AI-001-report.md`。

下一个执行任务是 `DFWX-AI-002`（设计新版 AI 宿主接入边界），其后按 `docs/tasks/README.md` 队列推进。研究报告与当前代码有出入时，以当前代码为准。

## 6. 证据等级

- A：当前源码、当前测试输出或真实产物检查；
- B：项目规范和已核对的任务文档；
- C：历史计划、旧截图、旧版本行号；
- D：模型推测。

任何任务完成说明必须写明证据等级和未验证部分。
