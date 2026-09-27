# 08｜任务索引

> 每项任务必须单独研究、实施、测试和回退。任务名不是授权；实际开工前先核对当前代码和本包的证据状态。

| ID | 任务 | 前置 | 允许范围 | 完成标准 |
|---|---|---|---|---|
| DFWX-DOC-001 | G0 治理包和证据基线 | 无 | 本治理包文档 | 文档通过只读验收 |
| DFWX-SEC-001 | 外部 Intent 与安装边界 | DOC-001 | MainActivity、下载/安装策略、负向测试 | 外部输入不能直达自动/静默安装 |
| DFWX-SEC-002 | 移除内置 AI 模型/渠道 | DOC-001 | app 构建注入、Seeder、迁移测试 | 不再播种；用户自建 Provider 不受影响 |
| DFWX-SEC-003 | release 签名 fail-closed | DOC-001 | app 构建配置、CI 验证 | 缺配置构建失败，无秘密泄露 |
| DFWX-SEC-004 | AI 日志最小化 | DOC-001 | vendor 日志链和测试 | 不记录凭据、完整 body、SSE |
| DFWX-NET-001 | HTTPS-only 与 URL policy | SEC-001 | 外部 URL policy、重定向和测试 | 普通外部地址 HTTPS-only，策略分型 |
| DFWX-STAB-001 | 历史持久化与生命周期 | SEC-001 | MainActivity、HistoryStore、测试 | 销毁不阻塞，写入原子可恢复 |
| DFWX-ADB-001 | ADB 后台状态控制 | DOC-001 | AdbShellManager、controller、fake 测试 | 旧结果不覆盖新结果，主线程不阻塞 |
| DFWX-TEST-001 | 高风险链路测试矩阵 | SEC-001/NET-001 | test source、fake、产物检查 | P0/P1 均有可失败测试 |
| DFWX-CI-001 | CI 质量门禁 | TEST-001 | workflows、脚本和报告 | PR/test/release 分层门禁 |
| DFWX-DEP-001 | vendor/依赖/合规 | TEST-001 | catalog、patch、SBOM/NOTICE | 构建可复现、来源可追溯 |
| DFWX-ARCH-001 | 宿主领域渐进拆分 | TEST-001 | 一次一个领域 | 每次抽取独立可回退 |
| DFWX-UI-001 | 动画和性能证据 | TEST-001 | debug 观测、UI 专题和指标 | 根因证实后再改动画 |
| DFWX-UI-002 | 设置页专题 | UI-001 | 设置 UI、状态和测试 | 手机 release 用户验收 |
| DFWX-UI-003 | 下载页专题 | UI-002 | 下载 UI、状态和测试 | 与状态机一致、无裁切 |
| DFWX-UI-004 | Compose AI 专题 | ARCH-001/DEP-001 | vendor patch 和 Compose UI | 上游边界可追踪、输入和返回稳定 |

## 每项任务必须填写

- 问题和证据；
- 已确认/待验证；
- 用户边界；
- 允许和禁止文件；
- 最小实现；
- 回归测试；
- 验收和回退；
- 对相邻任务的影响。

## 推荐执行顺序

```text
DOC-001
→ SEC-001 / SEC-002 / SEC-003 / SEC-004 / NET-001
→ STAB-001 / ADB-001
→ TEST-001
→ CI-001 / DEP-001
→ ARCH-001
→ UI-001 → UI-002 → UI-003 → UI-004
```

可并行只读研究，不并行修改共享核心文件。用户手机实测和发布仍受项目现行请示与红线约束。
