# DFWX-SEC-003：release 签名 fail-closed

状态：✅ 已完成（2026-09-28）——硬编码的签名口令回退已删除（不回显具体值），缺配置时 release 构建直接失败；`-Pdfwx.unsigned` CI 开关保留
前置：BASE-001

## 目标

release 缺少 keystore、alias 或密码配置时直接失败，不使用硬编码 fallback，不泄露秘密。

## 允许修改

- `app/build.gradle.kts`；
- CI 校验脚本/workflow；
- 构建测试和发布文档。

## 禁止

- 不读取、输出或提交 `local.properties` 内容；
- 不把密码写入脚本、日志、任务卡或 CI 明文；
- 不改变用户已确认的 release 命名和 ABI 规则；
- 不执行发布。

## 必须验证

1. 缺失配置的 release 构建失败且错误明确；
2. 测试构建可使用明确的 unsigned 开关，不假装是正式包；
3. 日志不出现密码、KeyStore 内容或 Token；
4. 配置存在时只验证流程，不输出值；
5. CI 与本地行为一致。

## 回退

只回退本任务的构建配置和门禁改动，保留已有下载和 UI 未提交改动。
