# DFW-29（ARCH-001）宿主领域拆分：进度与边界记录

> 卡片要求"调用图与责任边界写入任务证据"、"每个领域单独提交"。
> 本文件记录已抽出的领域、以及 MainActivity 里**还留着什么**（避免下一轮重复劳动）。

## 已抽出

### 1. `LinkPolicy`（本轮，提交见 DFW-29 第一次提交）

- **责任**：链接的识别、归一化与输入面收窄。**纯字符串逻辑，不碰 UI / 存储 / 网络。**
- **内容**：`webUrlEnd` / `hostOf` / `normalizedWebUrl` / `isProductReleaseHost` /
  `isRealLanzouHost` / `isLanzouUrl` / `preferredLanzouUrl` / `isShareableWebUrl` / `extractSharedUrl`
- **调用关系**：`MainActivity` 里保留**同名转发方法** → 所有既有调用点（8 处 `normalizedWebUrl`、
  15 处 `preferredLanzouUrl` 等）**一行未改**。所以这一刀可以独立回退（还原那几个 shim 即可）。
- **为什么先抽它**：不依赖任何 Android API，最容易验证等价性；抽出来才第一次能直测。
- **唯一的两处改写**（已在代码注释记录）：
  1. `Uri.parse(x).getHost()` → `hostOf(x)`（纯字符串取 host，`Uri` 在无 Android 运行时时不可用）；
  2. `preferredLanzouUrl` 原本 `Uri.parse(value).toString()`，对已是字符串的 URL 等价于原值。
- **等价性证据**：抽取后全量宿主测试 **190 例全绿**（含所有既有测试）；
  另用"把 LinkPolicy 的域名判定改成恒真"验证 shim 确实走了新实现（2 红）。

## 已知局限（如实记录，不是本次引入）

`webUrlEnd` 剔除的尾部字符集是 ASCII 的 `.,;:!?)]}`，**不含中文句号「。」**。
本轮**没有顺手改**——ARCH-001 明确"不借架构任务顺手改产品规则"。
要改的话单独评估（会影响"用户从微信粘链接带中文句号"的场景）。

## MainActivity 现状与后续顺序

`MainActivity.java` 约 2247 行。卡片建议顺序与实际盘点：

| 顺序 | 领域 | 现状 |
|---|---|---|
| 1 | `SettingsRepository` | 未抽。`loadSearchSettings()`/`persistSearchSettings()` 体量大、键多，但**与生命周期/UI 强耦合**（持久化后要 refresh 界面），
需要先定义回调接口 |
| 2 | `ExternalActionRouter` | 未抽。`handleExternalAction`/`sharedText`/`extractSharedUrl` 中**后两个已随 LinkPolicy 落地**，
剩下 intent 分发本身（很短） |
| 3 | `DownloadCoordinator` | **已部分存在**：`TransferCoordinator` / `TransferTerminal` / `DownloadHistoryStore` /
`DownloadUrlPolicy` / `DownloadSourcePolicy` 都在，ARCH-001 要收拢的是 `MainActivity` 里的调用方 |
| 4 | `CrashLogPresenter` | 未抽。崩溃日志页读写与展示（`showCrashLogPage` 等）自成一块，且**已有测试**
（`CrashLogPageJvmTest` / `CrashReportExportJvmTest`），适合下一个动 |
| 5 | `UpdateCoordinator` | 未抽。DFW-7 刚加过触发与节流，逻辑集中，可抽 |
| 6 | `SearchCoordinator` | 未抽。与 `LanzouCore` 并发搜索强耦合，风险最高，**建议最后做** |

## 本轮结论

按卡片"一次只抽一个领域、每个领域单独提交"的规则，本轮只做 `LinkPolicy` 一刀。
它在"价值/风险"上是最优起点：纯逻辑、可直测、可独立回退，且为后续拆分立下
"新入口建类 + 旧入口转发 + 全量测试守恒"的做法模板。
