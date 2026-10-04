# DFWX-RELEASE-001：release 回归和 GitHub 交付

状态：待执行
前置：CI-001、SEC-003、TEST-001

## 目标

完成手机 release 级回归和可追溯的 GitHub 测试版交付。

## 发布红线

- 发版前阅读 `<本地技能目录>/dfwx-release/SKILL.md`；
- 只构建 arm64 release；
- 不使用含 Debug 的 release 任务名；
- Release 标题纯 `vX.Y.Z`；
- 资产名 `dongfang-wuxian-vX.Y.Z.apk`；
- 不输出签名密码、Token、Cookie；
- 发 APK 同时满足 AGPL-3.0 源码义务和 RikkaHub 署名；
- 不直接安装到用户手机，先给用户下载链接；
- 未经用户明确同意不卸载、清数据、删除文件或发布。

## 验收

构建、aapt ABI、签名/unsigned 结论、关键链路测试、手机 release 证据、源码和许可证交付记录齐全。
