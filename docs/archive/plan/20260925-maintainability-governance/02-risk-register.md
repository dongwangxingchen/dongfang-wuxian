# 02｜当前风险台账

> 严重度和状态按当前审计证据整理。`已确认`表示代码路径或配置事实已确认，不表示运行时利用已经完成；`待验证`表示不能仅凭静态代码下结论。

| 编号 | 严重度 | 风险 | 证据 | 状态 | 第一修复方向 |
|---|---|---|---|---|---|
| F-01 | P0/条件性危急 | 外部 Intent 可能进入普通下载，再与自动/静默安装组合 | `MainActivity.java:163-164,1701-1727,1795-1806`；`SegmentDownloader.java:33-39,156-169`；合并 Manifest 导出入口 | 静态隔离和 JVM 负向测试已通过；完整触发链与真实安装器行为待手机 release 实测 | 保持外部/legacy 强制确认，禁止自动/静默安装；补 fake Intent 和手机负向证据 |
| F-02 | P1 | 普通下载 URL、缓存直链和重定向没有统一信任策略 | `DirectLinkResolver.java:54,59-106`；`DownloadUrlPolicy.java`；`SegmentDownloader.java:156-169` | 外部下载已与直链缓存分离；HTTPS、重定向、私网/loopback 和 DNS fail-closed 有 JVM 覆盖；真实重定向链待测试 | 保持分享 URL、解析直链、更新 URL、外部 HTTPS 四类边界；补本地重定向 fake 测试 |
| F-03 | P1 | 全局允许 cleartext | `app/src/main/AndroidManifest.xml` application 配置 | 配置已确认，实际 HTTP 使用待盘点 | 外部服务 HTTPS-only；loopback 例外单独审核 |
| F-04 | P0 | release 签名缺失时存在硬编码 fallback | `app/build.gradle.kts` signing 配置 | 代码事实已确认 | 缺失即失败，不恢复默认密码 |
| F-05 | P0 | 默认 AI Key 可通过 resource 注入 APK，并由 seeder 保存 | `app/build.gradle.kts`；`BuiltinProviderSeeder.kt:31-77` | 注入路径已确认，具体包内值需逐包检查 | 移除内置 AI 模型/渠道播种；不误伤用户自建 Provider |
| F-06 | P1 | AI request/response/SSE 日志可能记录鉴权和用户内容 | `RequestLoggingInterceptor.kt:18-73`；vendor `ResponseAPI.kt` | 记录路径已确认，release 开关和持久化需实测 | 默认关闭；header allowlist；body/SSE 不记录 |
| F-07 | P1 | `onDestroy()` 主线程等待历史写入 | `MainActivity.java` 的 `onDestroy()`/`flushDownloadHistoryNow()` | 代码路径已确认 | 异步、原子、可恢复持久化 |
| F-08 | P1 | ADB/Shizuku refresh 可能在主线程执行 Binder 调用 | `AdbShellManager.refresh()`；`MainActivity.onResume()` | 调用路径已确认，阻塞时间待测 | 后台串行刷新、generation 去旧 |
| F-09 | P1 | CI 缺少完整测试、lint、安全、依赖和发布门禁 | `.github/workflows/*.yml` | 配置事实已确认 | 分 PR/test/release 三层门禁 |
| F-10 | P1 | `sqlite-android=-SNAPSHOT` 与 JitPack 削弱可复现性 | version catalog、`settings.gradle` | 配置事实已确认 | 固定版本/commit、locking、verification、SBOM |
| F-11 | P1 | MainActivity/LanzouCore/ToolHost 职责过载 | 文件规模和调用图谱 | 架构事实已确认 | 先补测试，再按领域渐进抽取 |
| F-12 | P1 | vendor master 快照和人工 PATCH 重放难复现 | `rikkahub/PATCHES.md` | 管理风险已确认 | 固定上游 commit/tag，自动重放检查 |
| F-13 | P2 | crash logger 重复安装、日志内容可能过宽 | `App.java` | 代码路径已确认 | 只安装一次、内部存储、脱敏和保留上限 |
| F-14 | P2 | WebView、导出组件和 Manifest 权限面较宽 | Manifest、`LanzouWebActivity.java`、vendor WebView | 攻击面已确认，高权限利用未证明 | host/scheme/组件 allowlist，逐项权限台账 |
| F-15 | P2 | 敏感密码、分享链接、下载历史、剪贴板和 crash 内容留存边界不清 | `Models.java`、`MainActivity.java`、`App.java` | 代码线索已确认，实际可见性待测 | 数据分类、最小留存、清理入口和日志脱敏 |
| F-16 | P2 | backup 规则和 Manifest 存在漂移风险 | vendor backup XML、merged Manifest | 当前变体结果需逐变体核实 | 明确关闭/排除敏感数据并加构建检查 |
| F-17 | P2 | License/NOTICE/SBOM 证据不完整 | vendor README/LICENSE、依赖树 | 合规缺口待盘点 | 自动生成依赖许可证和源码交付清单 |
| F-18 | P3 | UI 动画异常根因未证实 | `LumaSwitch.java`、自动安装 sync 链 | 只能确认额外 sync，掉帧/重启动画待采样 | 先做 setter/cancel/frame 证据，不先调参数 |
| F-19 | P3 | Mimosa SHA-1 命中来自头像颜色映射 | `UIAvatar.kt:360` | 语义已确认，非认证用途 | 后续可换非密码学 hash，不按 P0 处理 |

## 优先级

第一批：F-01、F-02、F-04、F-05、F-06、F-03。

第二批：F-07、F-08、F-09、F-10、F-12。

第三批：F-11、F-13、F-14、F-15、F-16、F-17、F-18、F-19。

任何条目在修复前都要补“最小可失败测试”；静态结论被新证据推翻时，保留原判断并新增纠偏记录，不静默覆盖历史。
