# DFWX-BASE-001：交接入口和历史资料归档

状态：已完成（2026-09-27：唯一入口建成；5 个旧目录已归档至 docs/archive/plan、docs/archive/tasks，各带 ARCHIVED.md；SEC-001 旧路径引用已修正；git diff --check 通过）
前置：无

## 目标

建立后续 AI 唯一阅读入口，并把旧计划、旧研究和旧专题按类别移入 `docs/archive/`，保留历史证据，不让旧文件继续直接指挥源码修改。

## 必读

- `AGENTS.md`
- `docs/plan/DFWX-MASTER-PLAN.md`
- `docs/plan/current-state.md`
- `docs/plan/decisions.md`
- `docs/plan/risk-register.md`
- `docs/tasks/README.md`

## 允许修改

- `docs/plan/` 当前入口文档；
- `docs/tasks/README.md`；
- `docs/tasks/DFWX-*.md`；
- `docs/archive/` 归档目录和说明。

## 禁止修改

- `app/`、`rikkahub/`、Gradle、Manifest、`local.properties`；
- 交接包、保险箱和任何用户数据；
- 不删除历史文件；
- 不在归档过程中重写历史内容；
- 不提交、push、发布。

## 归档规则

1. 先列出源文件清单和目标路径；
2. 确认当前仍在执行的计划、代码证据和用户已确认决定；
3. 旧治理包、旧长期执行计划、旧 UI/搜索/性能专题分别归档；
4. 每个归档目录添加 `ARCHIVED.md`，记录原路径、原因和替代入口；
5. 文件名保持原名，不添加“最新版、修改版、终版”等后缀；
6. 移动完成后修正当前入口中的链接；
7. `git diff --check` 和路径/链接检查通过后才算完成。

## 验收

- 后续 AI 只需阅读当前入口即可知道从哪开始；
- `docs/archive/` 下所有材料明确写明“历史资料，不是当前实施命令”；
- 没有删除、覆盖或泄露凭据；
- Git 工作树中只出现文档移动/说明改动。

## 回退

只回退本任务产生的文档移动和说明文件，不触碰源码及其他未提交改动。
