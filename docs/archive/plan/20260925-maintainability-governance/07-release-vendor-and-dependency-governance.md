# 07｜发布、vendor、依赖与合规

## 1. Release

- release 缺少签名配置必须失败；禁止硬编码签名 fallback；
- `local.properties` 不进 Git、CI、日志或文档；
- release 和 Debug 任务分开；
- 真实验收使用 arm64 release 包；
- APK 包名、版本、ABI、签名摘要和升级路径必须核验；
- GitHub Release 标题只能是 `vX.Y.Z`；资产只能是 `dongfang-wuxian-vX.Y.Z.apk`；
- 测试期版本递增只为覆盖安装，正式版本重置需单独设计 versionCode 兼容，不直接改低；
- 发布前履行 AGPL-3.0 源码义务，保留 RikkaHub 署名；
- 不直接把测试 APK 装到用户手机，默认走 GitHub 供用户自行获取。

## 2. CI 门禁

### PR

- 相关 JVM 测试；
- 全仓测试；
- lint 和 `git diff --check`；
- secrets 扫描；
- 动态 SNAPSHOT/未锁定依赖检查；
- merged Manifest 导出差异检查。

### test 分支

- Debug smoke；
- unsigned release 结构构建；
- APK 包名、版本、ABI 检查；
- URL、更新、Provider 和安装负向测试；
- 依赖树、许可证和 SBOM 产物。

### release

- 读取 `dfwx-release` Skill；
- 受保护 Secret 注入签名；
- 真实签名和证书摘要核验；
- 资产命名、SHA-256、升级安装和源码交付核验；
- 未通过任何一项不得发布。

## 3. vendor

`rikkahub/` 不是可以随手改的普通目录。每次变更要记录：

- 上游仓库和固定 commit/tag；
- 当前 vendor 基线；
- DFWX patch 编号、目的和顺序；
- 冲突处理；
- 哪些上游行为被保留，哪些被替换；
- 对宿主接口和测试的影响。

不能一边做宿主架构拆分一边整体升级 vendor。先固定基线，再单独做 vendor 专题。

## 4. 依赖

优先处理：

- `sqlite-android = "-SNAPSHOT"`；
- JitPack 生产依赖；
- 未锁定的传递依赖；
- 宿主直接版本号与 vendor catalog 风格不一致。

目标：固定版本或 commit、Gradle dependency locking、dependency verification、artifact hash、SBOM 和许可证报告。宿主不新增未经批准的第三方依赖。

## 5. 合规

发行包必须能说明：

- AGPL-3.0 源码获取方式；
- RikkaHub 上游署名和修改说明；
- 第三方 LICENSE/NOTICE；
- vendor 版本和 patch；
- APK 与源码、构建和签名的对应关系。
