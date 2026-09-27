# DFWX-CI-001：CI 分层质量门禁

状态：待执行
前置：TEST-001、DEP-001

## 目标

让 PR、测试、unsigned release 和正式发布各有清晰门禁，失败时不产出不可信包。

## 覆盖

JVM、静态检查、Manifest/导出组件、依赖、unsigned release、签名配置、ABI、APK 资产命名、SBOM/许可证和关键安全测试。

## 禁止

不把签名秘密写入仓库或日志；不改变用户确认的 Release 命名；不自动发布；不让 Debug 任务混入 release 门禁。

## 验收

每层有明确命令、失败条件、产物和保留时间；本地与 CI 结论可对照；门禁文档与 workflow 一致。
