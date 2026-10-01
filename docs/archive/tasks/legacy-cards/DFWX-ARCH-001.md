# DFWX-ARCH-001：宿主领域渐进拆分

状态：待执行
前置：TEST-001

## 目标

逐步降低 `MainActivity` 复杂度，形成 UI → Coordinator → Policy/State Machine → Repository/Platform Adapter 边界。

## 顺序

SettingsRepository → ExternalActionRouter → DownloadCoordinator → DownloadHistoryStore → AdbStateController → UpdateCoordinator → SearchCoordinator → SettingsPageBuilder。

## 规则

一次只抽一个领域；先补测试，再移动实现，最后删旧入口；每次可独立回退；不借架构任务顺手改视觉或产品规则；不覆盖未提交改动。

## 验收

新入口和旧入口行为一致或差异有决策记录；测试通过；调用图和责任边界写入任务证据；MainActivity 继续可编译。
