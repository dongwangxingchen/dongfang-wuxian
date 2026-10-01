# DFWX-SEC-004：AI 日志最小化和脱敏

状态：待执行
前置：AI-002、AI-005

## 目标

AI 请求、响应和 SSE 调试信息只保留定位故障所需的事件、状态和耗时，不记录凭据、完整 body 或用户内容。

## 允许修改

- RikkaHub AI 请求/响应/SSE 日志链；
- 对应 fake、日志扫描和 JVM 测试；
- `rikkahub/PATCHES.md`。

## 禁止

不输出 API Key、Authorization、Cookie、完整 URL 查询凭据、完整请求/响应 body、SSE 内容和聊天正文；不读取或输出 `local.properties`。

## 验收

正常、失败、取消、SSE 中断四类路径均通过日志断言；保留 request id、provider 类型、状态、错误类别和耗时等最小字段；编译和回归通过。
