# DFWX-AI-001 研究报告：RikkaHub 稳定版本核对与 AI 重做边界

> 核对日期：2026-09-27。只读研究，未修改任何源码、未构建、未操作设备。
> 本报告可直接作为 DFWX-AI-002（接入边界设计）的输入。

## 一、核对结论（报告必答项）

### 1. 官方来源、tag/commit 和核对日期

- 仓库：https://github.com/rikkahub/rikkahub（AGPL-3.0）
- 截至 2026-09-27 的最新稳定版：**RikkaHub 2.5.4**
- tag commit：`7263dd36194ace478cd82ccd1067590f8fd04fc5`，versionCode 189
- 来源：官方 releases/latest API 与 2.5.4 release 页
  - https://api.github.com/repos/rikkahub/rikkahub/releases/latest
  - https://github.com/rikkahub/rikkahub/releases/tag/2.5.4
  - https://github.com/rikkahub/rikkahub/commit/7263dd36194ace478cd82ccd1067590f8fd04fc5

### 2. 当前 vendor 与基线差异（当前 vendor 是否可称为稳定版）

**不能称为最新稳定版。** 准确描述为：

> 一个以 RikkaHub **2.5.1/186** 为版本标记、混入部分 master 后续代码、并叠加东方无限定制补丁的**不可精确复现 vendor 快照**。

证据：

- `rikkahub/app/build.gradle.kts` 版本标记为 2.5.1/186；
- `rikkahub/PATCHES.md` 自述混入了 master 快照；
- `rikkahub/.git` 不存在，无法证明对应某个精确上游 commit。

### 3. 代码证据 vs 待验证

代码证据（本地文件已核实）：版本标记、PATCHES.md 内容、`RikkaHubEmbed.kt` 约 421 行路由复制、`AiPageHost.kt` 组合策略、`BuiltinProviderSeeder.kt` 读 resValue 播种、`AppModule.kt:52-58` 仍保留 `Firebase.analytics` 装配、`RequestLoggingInterceptor.kt:9-23,65-73` 仅脱敏 Proxy-Authorization、`MessageNodeEntity.kt:21-31` 消息以 JSON 字符串存储。

仍需验证（14 项，见第四节）：不得凭猜测决定。

## 二、补丁保留矩阵（PATCHES.md 对照）

| 补丁 | 处理建议 | 说明 |
|---|---|---|
| P1 | 保留并重写 | application 转 library，按 2.5.4 结构重做 |
| P2 | 重写 | 2.5.1/186 过时，更新为 2.5.4/189 provenance（宿主版本独立，不混用） |
| P3 | 暂不恢复 | baselineprofile、videogen 先验证宿主是否需要 |
| P4 | 保留 | 删除 RikkaHub 第二 launcher |
| P5 | 待审计 | Firebase fake resource 不得默认保留 |
| P6/P6b | 条件保留 | 仅在没有 web-ui 时保留降级/占位逻辑 |
| P7 | 保留 | `packageName` 适配 library |
| P8 | 保留并重写 | AGP 9 library 配置重新对照 2.5.4 |
| P9 | 保留并验证 | keep rule 不污染 consumer rules |
| P10 | 保留 | 宿主 Application 继承（open class） |
| P11 | 重写 | 只播种非敏感默认配置，删除 APK 内置真实 key |
| P12 | 保留并复核 | 禁止引导用户安装独立 RikkaHub |
| P13 | 停止并查明 | 表中无定义，不能凭猜测补写 |
| P14 | 保留并重做 | 冷启动背景策略，服从宿主主题 |
| P15 | 保留+再审计 | Crashlytics 已移除，但 **Firebase Analytics 仍有装配**（AppModule.kt:52-58），需单独决定去留 |
| P16 | 大幅重写 | 缩小 Compose embed wrapper，不复制完整旧 AppRoutes |
| P17 | 重做 | 保留品牌意图，按 Material 体系重新实现 |
| P18 | 删除或隔离 | 手表不再是测试和发布目标 |
| P19 | 拆分保留 | 空态保留；IME/insets 归宿主边界 |
| P24 | 一次性保留 | 旧字体存量迁移完成后退出主流程 |
| EmojiBurst | 待验证 | 对照 2.5.4 是否已修复，有测试证据才保留 |
| floatingx 2.3.7 pin | 待验证 | 确认 2.5.4 API 不兼容 3.x 才保留 |
| 乱码注释 | 修复 | 重写文件时清理，确保 UTF-8 |
| 其他未登记改动 | 先登记 | 未登记前不得声称补丁可重放 |

## 三、接入方案骨架（11 阶段，AI-002 起细化）

0. **冻结当前事实**：只读 patch inventory（已登记/未登记/仅注释无可重放步骤），每项标 保留/重写/删除/待验证；
1. **固定官方上游**：以 2.5.4 tag `7263dd3` 做 clean import，记录 repo/tag/commit/versionName/versionCode；宿主版本号与 RikkaHub provenance 分离；
2. **重放结构性宿主补丁**：library 化、去第二 launcher、签名/ABI 统一、open class、packageName、AGP9、keep rules、更新检查禁用、Crashlytics 移除；Firebase Analytics 必须先审计再决定；
3. **重建 Compose 宿主接入**：宿主继续负责 tab 切换/生命周期/loading/insets/back 分发/权限引导/intent 转发；vendor 负责 Compose UI/ChatVM/Provider/Room/DataStore；`RikkaHubEmbed.kt` 从复制 421 行 AppRoutes 改为基于官方 2.5.4 RouteActivity 的小型 wrapper；
4. **权限引导边界**：`AiPermissionGate.java` 保留（麦克风/相机/Android13+通知、一次性标记、拒绝不阻断、不重复打扰），7 个场景必须测试；
5. **Provider/密钥迁移**：删除真实默认 key 的 resValue 注入；默认配置只含名称/baseUrl/默认模型；secret 走 SecretStore（Keystore 保护，密文落盘）；迁移保留原 Provider/Model/Assistant 引用，Seeder 幂等且用户删除后不复活，迁移失败不删旧数据；
6. **聊天数据/备份**：Room migration 保留（禁 destructive reset）；备份分"普通导出（无 secret）"与"完整备份（密码加密+版本+完整性校验）"；恢复失败不覆盖现有数据；ExportSerializer 禁止接入 Provider secret；
7. **请求日志脱敏**：默认关闭详细日志；Authorization/Proxy-Authorization/X-API-Key/Cookie/Bearer/URL query token 全部脱敏；release 禁敏感 body 日志；
8. **UI 重做范围**：保留 AI 能力，以 2.5.4 Compose 页面为基线重新整理主题 token；P18 手表改动删除或通用化；
9. **补丁矩阵执行**：按第二节逐项落地；
10. **测试门禁**：复用 `rikkahub/ai/src/test`、`EmbedShotsJvmTest` 等现有测试；新增 SecretStore/迁移/幂等/备份加密/日志脱敏/APK secret 扫描；构建只认 `:app:assembleEmptyRelease` + aapt 终验；
11. **回退点**：按阶段建独立提交/tag；不用 `git reset --hard`；不覆盖宿主未提交修改。

## 四、不能凭猜测决定的事项（开工前必须先取证）

1. 当前 vendor 对应的未知 master commit；
2. P13 的真实含义；
3. 2.5.4 是否已包含 EmojiBurst 修复；
4. floatingx 3.x 是否兼容当前调用方；
5. baselineprofile 能否在 library 结构中恢复；
6. `:videogen` 是否仍被宿主功能使用；
7. web-ui 是否需要正式引入；
8. Firebase Analytics 是否有真实消费点；
9. 2.5.4 与当前 Room schema/migration 的差异；
10. SecretStore 采用的平台 API/依赖版本；
11. 加密备份是否支持跨设备恢复；
12. 密钥迁移后是否需要兼容旧版本降级；
13. 官方路由能否直接抽取，还是需要窄范围 wrapper API；
14. 任何没有实际 diff/测试/官方文档支持的性能结论。

## 五、主要本地证据文件

- `rikkahub/PATCHES.md`、`rikkahub/app/build.gradle.kts`、`settings.gradle`
- `app/src/main/java/cc/nkbr/lanzouplus/`：MainActivity.java、App.java、AiPermissionGate.java
- `rikkahub/app/src/main/java/me/rerere/rikkahub/dfwx/`：AiPageHost.kt、RikkaHubEmbed.kt、BuiltinProviderSeeder.kt
- `rikkahub/app/src/main/java/me/rerere/rikkahub/data/ai/`：RequestLoggingInterceptor.kt、AIRequestInterceptor.kt
- `rikkahub/app/src/main/java/me/rerere/rikkahub/data/`：export/ExportSerializer.kt、db/entity/MessageNodeEntity.kt、sync/BackupManager.kt、datastore/PreferencesStore.kt
- `rikkahub/app/src/main/java/me/rerere/rikkahub/di/AppModule.kt`
- `rikkahub/ai/src/main/java/me/rerere/ai/provider/`：ProviderSetting.kt、Model.kt

## 六、失败恢复方式

- clean import 独立成阶段提交/tag，vendor 可随时回到它重放；
- 当前 vendor 冻结点保留工作树快照后再动；
- Room migration 只做可验证的 forward migration；
- SecretStore 迁移前先明确旧版本能否读新密文，不能则"完成迁移后不支持降级"写入发布门禁。

## 七、2026-09-27 全仓审计补充（补丁台账修正输入）

来源：`docs/audit/20260927-full-audit.md`（M-3/M-5/M-6、V 区乱码、上游比对节）。PATCHES.md 属 vendor 区，本报告阶段不改，修正登记由 AI-002 及实施卡执行：

- PATCHES.md 只登记到 P19：P20/P21（`ChatList.kt:307/323/333`，v1.19.9 空态贴输入框）与 P24（`AiPageHost.kt:23,49`，v1.21.5 字体移除）代码存在但未登记；
- P18 标记错位：台账定义为"手表适配"，代码中 `[DFWX PATCH P18]` 实际是"强制深色"（`AiPageHost.kt:22`、`RikkaHubEmbed.kt:138`）；
- P13 全仓零定义（编号空洞，维持"不能凭猜测补写"结论）；
- 乱码两处：`RikkaHubEmbed.kt:128-130` KDoc 整段乱码；`:409` 数据库迁移弹窗 `鈫?`（用户可见）；
- `RikkaHubEmbed.kt` 头注释声称"音量键桥接为参数"，实际签名只有 activity/onBackStackReady/onOpenUsageAccessSettings 三参——注释失实，音量键滚动在内嵌态未接（AI-002 设计 wrapper 时决定是否补此桥）；
- 上游 2.5.4 比对补充：`AppRoutes()` 是 RouteActivity 的成员函数（依赖 navStack/pendingIntents/handleIntent 等 5 个实例成员），**不能原样抽取，必须窄 wrapper**——回答本报告"待验证 #13"；
- PATCHES.md 头部锚点过时（旧组织名 re-ovo + Windows 路径 `D:\heiyao\src\rikkahub\`），重写 provenance 时一并修正。
