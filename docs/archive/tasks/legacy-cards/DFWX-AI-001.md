# DFWX-AI-001：核对 RikkaHub 稳定版本和 vendor 差异

状态：已完成（2026-09-27，报告见 `docs/tasks/DFWX-AI-001-report.md`）
前置：DFWX-BASE-001

> 核心结论：官方最新稳定版为 **RikkaHub 2.5.4**（commit `7263dd36`，versionCode 189）；当前 vendor 是 2.5.1/186 标记混入 master 快照的不可精确复现状态，**不能称为稳定版**。接入以 2.5.4 clean import 为基线。执行时若发现报告与当前代码有出入，以当前代码为准并在报告上追加修订。

## 目标

确定可作为 AI 重做基线的 RikkaHub 官方稳定 tag 或固定 commit，区分上游代码、东方无限宿主适配和已经过时的补丁。此任务不改源码。

## 允许修改

- 仅允许新增或更新本任务的研究报告：`docs/tasks/DFWX-AI-001-report.md`；
- 若用户明确要求，才更新 `rikkahub/PATCHES.md` 的研究备注；本任务默认不改。

## 禁止修改

- 不替换 `rikkahub/` 文件；
- 不改 `app/`、Gradle、Manifest 或数据结构；
- 不读取、输出或复制 `local.properties`；
- 不操作手机；
- 不提交、push、发布。

## 研究内容

1. 核对 RikkaHub 官方仓库和稳定版本依据；
2. 记录当前 vendor 的真实基线，不能把 `master` 快照直接称为最新稳定版；
3. 对照 `rikkahub/PATCHES.md` 逐项标记保留、重做、删除、待验证；
4. 盘点宿主 AI 入口：Application、RouteActivity、RikkaHubEmbed、AiPageHost、ComposeView、返回和主题桥；
5. 盘点上游升级会影响的 Provider、模型、助手、聊天历史和 DataStore 序列化结构；
6. 给出版本固定、补丁重放、测试和回退建议。

## 报告必须回答

- 官方来源、tag/commit 和核对日期；
- 当前 vendor 与基线差异；
- 哪些结论是代码证据，哪些仍需验证；
- 为什么该版本适合作为接入基线；
- 升级最小文件范围；
- 失败时如何恢复当前 vendor。

## 验收

- 报告不包含凭据或私有代码外传；
- 能明确说明“当前 vendor 是否可称为稳定版”；
- 没有未经证据发明 API；
- 产出可直接被 DFWX-AI-002 使用。
