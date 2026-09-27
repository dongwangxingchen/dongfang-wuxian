# 东方无限：任务执行入口

> 所有后续 AI 只从这里挑任务。任务卡名称保持稳定，不使用“最新版、修改版、终版”等后缀。

## 接手顺序

```text
DFWX-BASE-001
→ DFWX-AI-001
→ DFWX-AI-002
→ DFWX-AI-003
→ DFWX-AI-004
→ DFWX-AI-005
→ DFWX-SEC-001 / DFWX-SEC-003 / DFWX-SEC-004 / DFWX-NET-001
→ DFWX-STAB-001 / DFWX-ADB-001
→ DFWX-TEST-001
→ DFWX-BRAND-001 / DFWX-BRAND-002
→ DFWX-UI-001 / DFWX-UI-002 / DFWX-UI-003 / DFWX-UI-004
→ DFWX-ARCH-001
→ DFWX-DEP-001 / DFWX-CI-001 / DFWX-RELEASE-001
```

只读研究可以并行；修改 `MainActivity`、PreferencesStore、vendor 核心或构建配置时不得并行改同一文件。

## 当前任务卡

- `DFWX-BASE-001`：唯一入口、当前状态和旧文档归档；
- `DFWX-AI-001`：核对 RikkaHub 稳定版本和当前 vendor 差异；
- `DFWX-AI-002`：设计新版 AI 宿主接入边界；
- `DFWX-AI-003`：Provider、模型、助手和聊天数据保护；
- `DFWX-AI-004`：移除东方无限内置 AI 播种和构建注入；
- `DFWX-AI-005`：AI 生命周期、日志、HTTPS 和回归门禁；
- `DFWX-SEC-001`：外部下载和安装边界；
- `DFWX-SEC-003`：release 签名 fail-closed；
- `DFWX-SEC-004`：AI 日志最小化和脱敏；
- `DFWX-NET-001`：普通外部 AI/API HTTPS-only 与重定向；
- `DFWX-STAB-001`：下载历史、并发和生命周期；
- `DFWX-ADB-001`：ADB/Shizuku 后台状态控制；
- `DFWX-TEST-001`：高风险链路测试矩阵；
- `DFWX-BRAND-001`：赞助页重做；
- `DFWX-BRAND-002`：关于、致谢、隐私和许可证页；
- `DFWX-UI-001`：动画根因与性能证据；
- `DFWX-UI-002`：设置页；
- `DFWX-UI-003`：下载页和软件库入口；
- `DFWX-UI-004`：新版 AI Compose 页面；
- `DFWX-ARCH-001`：宿主领域渐进拆分；
- `DFWX-DEP-001`：vendor、依赖、SBOM、LICENSE/NOTICE；
- `DFWX-CI-001`：CI 分层门禁；
- `DFWX-RELEASE-001`：release 回归和 GitHub 交付。

## 每张任务卡必须包含

1. 当前问题和代码证据；
2. 用户边界；
3. 前置任务；
4. 允许修改文件；
5. 明确禁止文件和操作；
6. 最小实现；
7. 可失败测试；
8. 验收命令；
9. 未验证证据；
10. 回退方式；
11. 完成后更新哪些文档。

## AI 接手模板

```text
先读 AGENTS.md、docs/plan/DFWX-MASTER-PLAN.md、current-state.md、decisions.md、risk-register.md 和本任务卡。
先运行 git status --short，不得清理未提交改动。
只修改任务卡允许范围；发现范围不足先停止并报告，不顺手扩展。
先补可失败测试，再做最小实现；跑完测试、编译和 diff 检查后才能标记完成。
不要读取或输出 local.properties，不操作手机不可逆动作，不提交、push 或发布。
```

## 开工前必须先向用户汇报（铁规矩）

每张任务卡动手之前，先用大白话向用户汇报，得到用户同意后才开工：

1. 这张卡要干什么事（允许打比方，禁止术语轰炸）；
2. 大概分几步、会动到软件里哪些地方；
3. 做完之后用户能看到什么变化；
4. 有没有风险（尤其是可能弄坏数据或已有功能的地方）。

汇报默认在对话里等用户答复；用户明确说"你看着办"才可以跳过等待。干完之后同样用大白话汇报：做了什么、验证过什么、哪些还没验证。

## 用户提问规则

向用户提问时一律用正文文字列出选项，不用按钮/选项组件。

旧任务、旧研究和旧截图在 `docs/archive/`，只作证据，不作当前命令。

## 全仓审计（2026-09-27）

`docs/audit/20260927-full-audit.md` 是开工前的全仓只读审计，抽查发现全部属实。执行 SEC-003 / SEC-004 / NET-001 / ADB-001 / DEP-001 / CI-001 / STAB-001 等卡前，先读报告对应节——报告里的 M-*/H-*/V-*/B-* 编号可直接作为任务卡证据引用。两处修正：H-P0b（pm install pipe 死锁）为理论风险降 P2；B-3（debug 只含 x86_64）是已知约定（lessons 六 2026-09-22 条），非新问题。
