# spec.md — 交互性能优化（切页/下载页/放大器）

日期：2026-09-24。依据：`黑曜/05-调研报告/20260924-应用交互卡顿根因审计.md`（实测+代码审计）+
官方文档核验（developer.android.com：Reduce overdraw / Loading Large Bitmaps Efficiently /
Build for Billions-improve startup latency，已通过 VPN 抓取存档 /tmp/doc_*.txt）。

## 目标（可量化）

| 指标 | 基线（模拟器 GPU host 实测） | 目标 |
|---|---|---|
| 真实切 tab 主线程帧耗时 | p90 36ms / max 64ms | p90 ≤ 15ms / max ≤ 25ms |
| 切设置 tab 的页面构建 | 200+ View 同帧 | Phase D 后：shell+nav 零重建；页面内容按需缓存 |
| 下载页搜索每键 | 全量重建列表 | 110-280ms 去抖 + 分帧 |
| 全屏背景层数 | 3 层同色 | 1 层（官方 overdraw 规则） |
| 图标解码 | 原图尺寸入 8MB 缓存 | 官方 inSampleSize 两段式，目标 ≤96px |

## 硬约束（违反即回退）

1. **视觉零变化**：改前改后截图必须一致（RoBolectric JVM 截图测试 + 模拟器截图目检）。
2. **行为零变化**：全量 `:app:testEmptyDebugUnitTest` 通过；切页状态重置语义保留
   （basePage 的 80 字段 teardown 是异步工作守卫，不许删）。
3. **不引新依赖**：无 RecyclerView（项目零 androidx UI 库），列表优化走代码内目录页
   `appendFolderRowsFrame` 分帧模板。
4. **不改视觉 token**：转场动画参数只降弱设备档，标准设备曲线不动（wear-ui-system.md §2）。
5. 分支 test，每阶段验证门禁通过才 commit（回退单位=阶段）。

## 阶段

- **Phase A 放大器**：A1 去 overdraw（root/primaryShell 裸背景，host 单层，官方规则"同色容器
  留窗口一层"）；A2 requestImage 官方两段式 inSampleSize（目标 96px）；A3 启动主线程 IO
  （loadDownloadHistory/LanzouCore 源解析）挪后台+完成后 rebind。
- **Phase B 下载页**：B1 renderDownloads 分帧+visible 窗口（套 appendFolderRowsFrame 模式）；
  B2 下载搜索接去抖（对齐源列表 110ms/目录 280ms，取 200ms）。
- **Phase C 设置页**：开关回调原地更新（去掉 showSettings() 全量重建的两处调用）。
- **Phase D 外壳常驻**：primaryDestination>=0 且 shell 已存在时复用 root/primaryShell/primaryNav
  （clear 内容子 View 而非重建）；弱设备（isLowRamDevice/totalMem≤1.5GB）转场降 ≤150ms 淡切。

## 风险与回退

- 最大风险：Phase D 复用外壳时 layout 监听器/宽高状态残留 → 由 refreshAdaptiveLayout 的
  adaptPrimaryShell 重建路径兜底（宽度突变仍走重建）。回退单位=单阶段 commit。
- A3 竞态风险：后台加载完成前 UI 不得读空数据 → 完成后 post 到 UI 线程 rebind，字段 volatile。
