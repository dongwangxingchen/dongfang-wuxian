# DFWX-NET-001：普通外部 AI/API HTTPS-only

状态：✅ 已完成（2026-09-28）——`AiUrlPolicy` + 请求拦截器 + DNS rebinding 防线，18 用例全绿；派生 AI 专用 client，不影响非 AI 流量。已随 2.5.5 clean import 重放（`6eb27b7`）
前置：AI-002、SEC-001

## 目标

普通外部 AI/API 地址默认只允许 HTTPS；loopback HTTP 不在本任务偷偷放行，若未来需要另立任务。

## 允许修改

- AI URL policy、请求入口、重定向校验和测试；
- 任务文档。

## 禁止

不把官方更新 allowlist、蓝奏云解析策略和普通外部 API 策略混用；不允许 userinfo、本机、内网、link-local、私网、multicast 和未验证 DNS；不读取凭据。

## 验收

HTTP/FTP、凭据 URL、私网 literal、私网 DNS 和跳转目标均有负向测试；HTTPS 公网地址可通过；失败原因稳定可定位。
