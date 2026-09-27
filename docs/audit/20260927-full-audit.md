# 《东方无限》全仓只读审计报告

> 审计日期：2026-09-27。**全程只读，仓库零改动**（`git status` 与审计前一致）。
> 方法：三路并行子代理（宿主 Java / vendor 补丁层 / 构建测试层）+ 人工复核上游比对 + `gh api` 在线验证。
> 证据等级：除标注"待验证"外全部为 A 级（当前源码 / 命令输出实测）。每条带 文件:行号。

---

## 一、文档与代码的硬矛盾（7 条）

| # | 矛盾 | 证据 |
|---|---|---|
| M-1 | **决策 #9 说"普通外部 AI/API 只允许 HTTPS"，manifest 全局放行明文流量** | `app/src/main/AndroidManifest.xml:10` `android:usesCleartextTraffic="true"`（PATCHES.md 自认是为 http 中转站开的）。任何 http 请求——AI 自定义渠道、蓝奏跳转——都不受拦截 |
| M-2 | **决策 #12 说"release 缺签名配置必须失败，禁止硬编码 fallback"，实际是 fail-open** | `app/build.gradle.kts:13-20` 口令缺失时回退**源码内硬编码默认口令**（弱口令字面量）；`app/build.gradle.kts:66-79` `-Pdfwx.unsigned` 开关直接跳过签名产出 unsigned 包。无任何 fail-closed 校验 |
| M-3 | **补丁台账断档**：PATCHES.md 只登记到 P19，代码里存在 P20/P21/P24 | P20/P21 在 `rikkahub/app/src/main/java/me/rerere/rikkahub/ui/pages/chat/ChatList.kt:307,323,333`（v1.19.9 引入）；P24 在 `dfwx/AiPageHost.kt:23,49`（v1.21.5 字体移除+存量迁移）。**P13 全仓零定义**（编号空洞）；**P18 标记错位**——台账定义是"手表适配"，代码里 `[DFWX PATCH P18]`（`AiPageHost.kt:22`、`dfwx/RikkaHubEmbed.kt:138`）标的却是"强制深色"（v1.11.1 引入，从未按正确编号登记） |
| M-4 | **"测试没跑不能标记完成" vs current-state.md 声称"DownloadPolicyTest 已通过"**——该测试连同其被测实现全部 untracked，CI checkout 后根本不存在这些文件 | `git status`：`DownloadSourcePolicy.java`、`DownloadUrlPolicy.java`、`DownloadPolicyTest.java` 均为 `??`。CI 两个工作流**从不跑任何 test 任务**（`.github/workflows/android-build.yml` 只 assemble+ABI 断言；`lanzouplus-empty.yml` 只编译冒烟）。本地跑过 ≠ CI 验证过 |
| M-5 | **RikkaHubEmbed.kt 头注释声称"音量键桥接为参数"，实际签名无此参数** | `dfwx/RikkaHubEmbed.kt:133-137` 只有 `activity` / `onBackStackReady` / `onOpenUsageAccessSettings` 三个参数。上游 2.5.4 的音量键滚动（`RouteActivity.dispatchKeyEvent` + `volumeKeyListeners`）在内嵌态**完全失效**（宿主 MainActivity 是 ComponentActivity，不经 RouteActivity） |
| M-6 | **PATCHES.md 上游锚点过时**：写的是 `re-ovo/rikkahub`（旧组织名）+ vendor 目录 `<仓库根>\rikkahub\`（Windows 路径） | `gh api` 实测：`re-ovo/rikkahub` 已重定向至 `rikkahub/rikkahub`（组织改名）；本机在 macOS `<本地目录>/src`。台账写于 Windows 时代，跨机器未更新 |
| M-7 | **lessons.md 规定 Conventional Commits（feat/fix/docs 前缀），git log 全是随意中文** | `git log --oneline -15`：如 "v1.22.4 版本号 1039024"、"动效三修：删开关行图标闪烁bug…"，无一条带前缀 |

---

## 二、宿主 Java 区 bug / 隐患

### P0 候选（待真机验证）

| 编号 | 问题 | 证据 |
|---|---|---|
| H-P0a | **onDestroy 关闭顺序隐患**：`core.close();directResolver.close()` 在 `ui.removeCallbacksAndMessages(null)` 之前；DirectLinkResolver.close 同步遍历 inflight 回调调 `failed()`，回调链会触碰已 teardown 的 UI | `MainActivity.java:209`、`DirectLinkResolver.java:98`、`MainActivity.java:1850` |
| H-P0b | **AdbShellService 先写 stdin 后读 stdout**：pm install 若早期输出超 64KB pipe 缓冲会卡死至 5 分钟超时（理论风险，正常 pm 输出小） | `AdbShellService.java:24,33` |

### P1（治理计划已预言的 + 新发现）

| 编号 | 问题 | 证据 |
|---|---|---|
| H-P1-1 | **onDestroy 主线程同步落盘，最长阻塞 4+1 秒**；失败分支在主线程直接 `commit()` 写全量 JSON | `MainActivity.java:209,352,351` |
| H-P1-2 | **onCreate 主线程同步 I/O**（SP 删除 + 首次全量 JSON 解析） | `MainActivity.java:130-131` |
| H-P1-3 | **静态强引用 Activity**：`static MainActivity ACTIVE_OWNER`——有清理但多实例场景泄漏窗口贯穿整个生命周期 | `MainActivity.java:115,209` |
| H-P1-4 | **AdbShellManager 无 generation/序号防竞态**：晚评估的旧状态可覆盖新状态（READY 被 CONNECTING 覆盖 → `ready()` 误判 → 静默安装失败误报）；回调线程不受控（binder 线程 / installers 线程都 publish） | `AdbShellManager.java:52-62,69,37,83`。对照：更新下载链有 `controlGeneration` 序号（`MainActivity.java:658`）——ADB 恰恰缺这个 |
| H-P1-5 | **Shizuku binder transact 仍可上主线程**：v1.19.3 ANR 修复只是 `postDelayed 3000ms` 延迟，`refresh()` 的 `pingBinder/checkSelfPermission/getUid` 依旧主线程执行，ROM 冻结 Shizuku 时无超时保护 | `MainActivity.java:128-131`、`AdbShellManager.java:54` |
| H-P1-6 | **UI 线程批量 SAF 删除**：对话框回调主线程逐个 `ContentResolver.delete + openFileDescriptor`，删 N 个文件全程阻塞 UI | `MainActivity.java:1757,1602,1691` |
| H-P1-7 | **更新校验临时文件名固定**：并发校验（手动+自动重试）读写同一 `verified-update.apk` 互相覆盖，可能把合法更新误判"摘要不一致"（不会放行坏包，fail-safe 方向） | `MainActivity.java:659` |
| H-P1-8 | **静默吞异常最重两处**：85 库标题同步失败无日志无重试；TransferCoordinator starter 抛 RuntimeException 被吞任务静默消失 | `MainActivity.java:188`、`TransferCoordinator.java:28` |

### P2（代码卫生精选）

- 下载历史**静默截断 100 条**永久丢弃无提示（`MainActivity.java:351`）
- 更新校验缺 versionName 交叉比对，旧版 APK 只要 digest 自洽即放行（`MainActivity.java:659`）
- 镜像更新端点空壳死代码（`UpdateClient.java:16-17` 两个空串常量）
- `persistDownloadHistory` 无锁读写字段可见性问题（后果仅多/少一次落盘，`MainActivity.java:348`）
- 分贝仪裸线程靠 60ms 轮询标志退出（`ToolHost.java:1429,1444`）
- crash.log 并发写无锁交错（`App.java:74-77,98`）
- `SegmentDownloader.java:102` 慢路径全量读文件只为求大小
- `deleteOnExit` 列表无界增长 Android 上永不执行（`AdbShellManager.java:83`）
- 下载历史落盘用 `commit()` 非原子、非 temp+rename（正样本：`LanzouCore.java:1480` writeAtomic）

**正面确认**（避免误伤）：`downloadEntries` 为 CopyOnWriteArrayList；更新下载有 generation 序号；DownloadFileProvider **无路径穿越**（canonical 先行 + 父目录锁定 + exported=false）；UpdateClient 已实现大小/SHA-256/包名/签名/host 白名单校验；吞异常大部分已带 Log.w。

---

## 三、vendor 区（rikkahub/）

### 乱码（仅 2 处，全仓扫描）

1. `dfwx/RikkaHubEmbed.kt:128-130`——P16 KDoc 整段 GBK mojibake
2. `dfwx/RikkaHubEmbed.kt:409`——**用户可见**："v${from} 鈫?v${to}"（数据库迁移弹窗里的箭头乱码）

### 安全

| 编号 | 问题 | 证据 |
|---|---|---|
| V-S1 | **请求日志脱敏只覆盖 Proxy-Authorization 一个头**：`Authorization`、`X-API-Key`、`Cookie` 原样记录；URL query token（Gemini `?key=`）不脱敏；requestBody 原样。内置中转站 Key 走 Authorization 头——开启日志后明文可见，且 **LogPage 有一键复制**（泄露路径确认） | `data/ai/RequestLoggingInterceptor.kt:18,35,51,67-68`、`ui/pages/log/LogPage.kt:345`。缓解：日志默认关闭 + 仅内存 100 条环形不落盘（`common/.../Logging.kt:52,6`） |
| V-S2 | **APK 内嵌真实中转站 Key**（"开箱即用"的必然代价）：resValue 进 APK，`raw/keep.xml` 显式防裁剪，解包即可提取。中转站是否有额度防护**待验证** | seeder 消费链 `BuiltinProviderSeeder.kt:35,85-88`；key 源头在 local.properties **未进 git（安全）**；但本机 `app/build/` 生成目录有明文——**build 目录别进备份/截图/issue** |
| V-S3 | **Firebase Analytics 仍是活代码**：P15 只摘了 crashlytics；`ChatVM.kt` 注入并 5 处 `analytics.logEvent(...)`（payload 为 null）。假配置下预期静默失败，但 PATCHES.md 自己标"待真机确认"仍未确认 | `di/AppModule.kt:56-58`、`ui/pages/chat/ChatVM.kt:60,209-276` |

### 合规（好消息）

- `rikkahub/AGENTS.md` 确系上游文件，PATCHES.md:52 已声明不作为本工程指令——无滥用
- UpdateChecker P12 短路完好未被破坏（`utils/UpdateChecker.kt:44` return@flow）
- BuiltinProviderSeeder 幂等合规：done 标记持久、播种失败不写标记、**用户删除后不复活**
- 上游坐标确认：`rikkahub/rikkahub`（re-ovo 为改名前旧名），AGPL 署名义务对象无歧义

---

## 四、构建 / 依赖 / CI

| 编号 | 问题 | 证据 |
|---|---|---|
| B-1 | **sqlite-android 浮动 SNAPSHOT 未钉版**（全仓唯一）：`com.github.rikkahub:sqlite-android:-SNAPSHOT` 解析为上游默认分支最新 commit——供应链风险 + 不可复现，最高优先级之一 | `rikkahub/gradle/libs.versions.toml:64,184` |
| B-2 | **签名 fail-open**（同 M-2）：硬编码默认口令 + unsigned 开关，与决策 #12 直接冲突 | `app/build.gradle.kts:13-20,66-79` |
| B-3 | **debug 包只含 x86_64**：真机（arm64）装不上 debug——所有"真机验证"实际上永远是 release 包，任何"debug 下测过"的说法都不成立 | `app/build.gradle.kts:83` |
| B-4 | **CI 不跑任何测试、不跑 lint**：约 90 个测试类 CI 上从未执行；untracked 的 DownloadPolicyTest 在 CI checkout 后不存在，冒烟绿灯不代表安全策略可编译 | `.github/workflows/android-build.yml`、`lanzouplus-empty.yml` |
| B-5 | **vendor 版本号手改**：`VERSION_NAME/VERSION_CODE = "2.5.1"/"186"` 字面量，同步上游时需手工改——当前实际是"2.5.1 标记混 master 代码"（AI-001 已判不可复现） | `rikkahub/app/build.gradle.kts:50-56` |
| B-6 | `requestLegacyExternalStorage="true"` 在 targetSdk 37 下**完全无效**（死配置，仅 target≤29 生效） | `AndroidManifest.xml:10` |
| B-7 | 权限组合 `MANAGE_EXTERNAL_STORAGE` + `REQUEST_INSTALL_PACKAGES`：Play 上架会被重点审查（当前 GitHub 分发可接受，仅登记） | `AndroidManifest.xml:6-7` |
| B-8 | jitpack SNAPSHOT 之外其余坐标均已钉版；rikkahub/settings.gradle.kts 的 `mavenLocal()` 不参与宿主构建（仅独立构建用），无污染 | `settings.gradle:21,17` |

---

## 五、测试覆盖缺口（五高风险域）

| 域 | 覆盖 |
|---|---|
| AI provider/请求构造 | **最好**：rikkahub/ai 33 类 + stream 回放、DB 迁移、备份恢复 |
| 更新校验 | 有（UpdateClientJvmTest） |
| 下载策略层 | 有但 **untracked**（DownloadPolicyTest）；**SegmentDownloader 分段下载核心零测试** |
| 权限引导（AiPermissionGate） | **完全没有** |
| ADB/Shizuku（AdbShellManager/Service） | **完全没有**（恰是竞态问题所在，见 H-P1-4） |

停泊测试：`tools/parked-tests/` 6 个 paparazzi 测试（AGP9 不兼容已实证）；`app/src/test/snapshots/` 约 100 张基线 PNG 被 ignore + 工具链已摘——**基线库当前无人能对比，纯遗留**。

---

## 六、git / 仓库卫生

- **已跟踪垃圾**：`cb_err.txt`、`lb_resp.txt`（根目录调试残留，在 git 里）
- **工作区遗留**（ignore 已覆盖但文件还在）：根目录截图 jpg、record_log*.txt、support_test_log*.txt、unit_compile_log.txt
- **归档移动做了一半**：`docs/tasks/perf-interaction/` 两个文件物理移到 `docs/archive/` 但删除未提交、archive 侧整批 untracked——checkout 旧 commit 两处都不完整
- **高危未提交**：下载策略三文件（安全实现+测试）、整个 `docs/plan/` 治理文档、25 张任务卡全部 untracked——**回滚/换机即全部丢失**
- `local.properties` 确认不在 git（红线安全）

---

## 七、上游 2.5.4 比对补充（AI-002 输入）

- 上游 `RouteActivity.kt` 734 行（blob `bef7871`）；`AppRoutes()` 是其**成员函数**（236-580 行），依赖 `navStack/pendingIntents/handleIntent/readBooleanPreference/openUsageAccessSettings` 5 个实例成员——**不能原样抽取，必须窄 wrapper**（回答 AI-001 报告待验证 #13）
- 本地 RouteActivity 的 import 段+类体与上游**逐行相同**；`Screen` sealed interface **完全一致**
- 上游多 4 模块：`videogen`（宿主 0 引用，可继续裁）/ `locale-tui` / `trace-cli` / `web-ui`（P6 守卫相关）
- 本地独有 vendor 改动：`dfwx/` 包 + sakura→dfwx 主题迁移（`RikkaHubEmbed.kt:165-167`，未登记）

---

## 八、行动建议（按优先级，供决策，未执行）

1. **P0 数据安全**：把 untracked 的下载策略三文件 + docs 治理文档**尽快提交**（现在一次误操作就全丢）——但提交需用户批准（规矩：不自动 commit）
2. **M-2 签名 fail-open**：加"口令/keystore 缺失即 throw"的 fail-closed（对应 SEC-003 卡）
3. **B-1 SNAPSHOT 钉版**（对应 DEP-001 卡）
4. **M-1 cleartext**：改 networkSecurityConfig 白名单制（对应 NET-001 卡）
5. **V-S1 日志脱敏**补 Authorization/X-API-Key/Cookie/query token（对应 SEC-004 卡）
6. **H-P1-4 ADB generation** 竞态（对应 ADB-001 卡）
7. 台账补登记 P20/P21/P24 + 修 P18 错位 + 查明 P13 + 清乱码两处（其中 `:409` 用户可见）
8. CI 至少加 `:app:testEmptyDebugUnitTest` 一个 test 任务
9. 顺手清理：cb_err.txt/lb_resp.txt 出库、根目录日志截图归档

> 注：以上多数项已有对应任务卡（SEC-003/SEC-004/NET-001/ADB-001/DEP-001 等）——本审计等于给这些卡提前提供了证据弹药，不是推翻队列。
