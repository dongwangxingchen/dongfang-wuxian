# 已归档：旧计划与治理文档（plan 类）

> **这些是旧资料，只作历史证据，不再指挥任何开发工作。**
> 归档日期：2026-09-27。归档≠删除，内容保持原样、原文件名。

## 当前替代入口

所有工作以新入口为准：

1. `docs/plan/DFWX-MASTER-PLAN.md`（唯一总计划）
2. `docs/plan/current-state.md`（当前事实）
3. `docs/plan/decisions.md`（已确认决定）
4. `docs/plan/risk-register.md`（风险台账）
5. `docs/tasks/README.md`（任务队列）

## 归档清单

| 原路径 | 现路径 | 内容 | 归档原因 |
|---|---|---|---|
| `docs/plan/20260925-maintainability-governance/` | `docs/archive/plan/20260925-maintainability-governance/` | 16 个文件：维护性治理包（基线、风险、架构、路线、任务索引等 G0 文档） | 已被 `DFWX-MASTER-PLAN.md` + `risk-register.md` + `decisions.md` 取代；其中仍有效的 SEC-001 证据已固化为任务卡 `docs/tasks/DFWX-SEC-001.md` |
| `docs/plan/20260926-long-term-execution/` | `docs/archive/plan/20260926-long-term-execution/` | 17 个文件：长期专题执行计划（T1–T24 专题卡、T5-S 设计稿 mockup 等） | 专题已重整为新任务卡体系（BRAND/UI/STAB 等系列）；T5-S 设计结论已固化到对应任务卡，设计锚点以任务卡记载为准 |

## 仍可作证据的内容

- `20260925-maintainability-governance/` 里的风险调查、架构分析、测试基线记录，其中引用的文件行号仍可作为调查起点（但行号已随代码演进失效，必须重新核对）。
- `20260926-long-term-execution/` 里的专题研究、竞品参考、设计稿截图（HTML mockup），做 UI 专题时可作参考素材。

## 不可再当作依据的内容

- 旧任务顺序、旧"已完成"标记、旧优先级。
- 与 `decisions.md` 冲突的旧决定（例如内置 AI 保留过渡方案——已改为整体移除重接）。
