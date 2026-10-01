# DFWX-UI-004：新版 AI Compose 页面

状态：待执行
前置：AI-005、UI-001、ARCH-001

## 目标

在新版 RikkaHub 接入稳定后，完成 AI 页面输入、流式输出、空态、错误态、键盘、返回、主题和生命周期验收。

## 禁止

不在旧 AI 接入上继续叠补丁；不删除用户 Provider/模型/聊天数据；不把页面展示状态和 DataStore 各自当状态源；不记录聊天内容和凭据。

## 验收

JVM/Compose 结构测试、请求取消和重建测试、手机 release 交互验收、vendor patch 可追溯。
