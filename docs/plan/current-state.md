# 东方无限：当前状态

> 这是当前事实页，不是历史计划。源码、命令输出和当前规范优先于旧文档。
> 更新日期：2026-09-29（v1.22.11：崩溃日志页两处真机反馈——文件夹真建出来 + 顶部按钮不再被自动滚走）

## 1. 工作区

- 桌面交接目录：`/Users/<用户名>/Desktop/东方无限`（仅存<本地备份> zip，勿删勿动）
- 实际源码根目录：`/Users/<用户名><仓库根>`
- 当前分支：`test`
- 当前 HEAD：见 `git log --oneline -1`（2026-09-29 起含 v1.22.9 提交；v1.22.10 改动尚未提交，见 §1.2）
- 工作树：**源码零改动**；`docs/handover/` 为 untracked（**故意不提交，勿顺手入库**）
- 回退点：git tag **`dfwx-pre-255-import`**（2.5.5 导入前）；vendor 备份 `<本地目录>/vendor-backup-before-255-20260928.tar.gz`（43MB）；旧补丁台账 `<本地目录>/PATCHES-231-backup-20260928.md`（三者**勿删**）
- applicationId：`dfwx.dongdang`；宿主 Java 包：`cc.nkbr.lanzouplus`
- `rikkahub/`：Kotlin/Compose vendor（**上游锚点 = re-ovo/rikkahub tag `2.5.5`，versionCode 190**）与东方无限补丁层
- 上游源码快照 `/tmp/rikkahub-255/`：**已清空**（临时目录），需要时重新下载
- 桌面<本地备份> `东方无限_交接总包_20260920.zip`：禁止删除或移动
- 版本号：versionCode `1039031` / versionName `1.22.11`（`app/build.gradle.kts:33-34`）；下一个版本递增为 `1039032` / `1.22.12`

## 1.1 v1.22.9 变更（2026-09-29，BRAND-003）

- **根因（A 级，A/B 对照实验坐实）**：宿主 `app/build.gradle.kts` 的 aapt2 参数 `--no-xml-namespaces`
  （v1.0.2 起误带 20 余版本）把**所有 res 二进制 XML 的命名空间 URI 一并剥离**。Compose 矢量图解析器
  读属性走带命名空间的查找（`TypedArrayUtils.hasAttribute` → `getAttributeValue(ANDROID_NS, "viewportWidth")`），
  查不到就退回 0f → `painterResource` 抛 `XmlPullParserException: <VectorGraphic> tag requires viewportWidth > 0`
  → 点"赞助"即闪退。**同类受害者**：docx/pdf 附件图标、deepthink 图标（共 59 个宿主矢量图 + vendor 矢量图）。
- **修复**：移除该参数，`app/build.gradle.kts` 留 7 行注释锁死红线。
- **回归守卫**：新增 `app/src/test/java/cc/nkbr/lanzouplus/ApkXmlNamespaceJvmTest.kt`（2 例，
  修复前 2 红、修复后 2 绿，已验鉴别力）。
- **崩溃报告升级（用户要求）**：`App.java` 新增 `diagnostics()`（版本/设备/系统/ABI/屏幕/内存/运行时长，
  字段参照 ACRA 约定，不含任何凭据）；崩溃日志页新增"保存为文件 / 分享报告文件"（复用 `DownloadFileProvider`
  的 `crash` 路径，无需新权限）；文件名纯 ASCII `dfwx-crash-<时间>-v<版本>.txt`（同 v1.22.8 资产名事故根因）。
  守卫测试 `CrashReportExportJvmTest.kt`（4 例）。
- **测试**：宿主 **57 例全绿**（基线 51 + 新增 6）。

## 1.2 v1.22.10 变更（2026-09-29，崩溃日志落盘位置 + 权限时机）

用户反馈（DFW-4 评论原话）："按键从最底部改到最顶部……按理来说应该在我下载后问我要存储权限，然后在 MT 管理器里创建一个文件夹叫东方无限……崩溃日志什么的就会在里面……而不是现在这样，我甚至在 MT 管理器里面都没有找到我那文件夹，你起的名字太刁钻了。"

- **操作卡置顶**：崩溃日志页的 保存/分享/复制/清除 从正文之下移到正文之上（长日志不用滑到底）。
- **落盘位置改为公共目录** `Download/东方无限/崩溃日志/`：
  - 报告 `dfwx-crash-<时间>-v<版本>.txt` + 固定名副本 `dfwx-crash-latest.txt`（方便下次直接取）；
  - `crash.log` **双写**：公共目录（用户可见）+ 应用外部私有目录（无需权限的权威副本，崩溃发生在授权之前也不丢现场）；
  - 清除崩溃记录改为**二次确认**，并连公共目录副本一起清。
- **权限时机改为按需**：不再启动即弹"管理所有文件"，只在用户点 保存/分享 时才申请（`ensureCrashReportWritable()`）；授权成功后自动续跑原操作。
- **目录先出现**：拿到权限后（或启动时已授权）静默创建 `Download/东方无限/崩溃日志/` 并放一个空 `crash.log`，用户在文件管理器里立刻看得到；下载完成时也会 ensure 一次。
- **顺手修的**：删除死代码 `maybeRequestStartupStorageAccess()`；`storageAccessGranted()` 全包 try/catch（Robolectric 下 `Environment.isExternalStorageManager()` 直接抛 `ArrayIndexOutOfBoundsException`，曾带崩 10 例界面测试）；`DownloadFileProvider` 移除已废的 `crash` 私有目录分支，分享统一走 `shared` 真实路径只读通道。
- **测试**：宿主 **59 例全绿**（v1.22.9 的 57 + 新增 3 − 改写 1 例）。
- **产物**：`dongfang-wuxian-v1.22.10.apk`，37046462 字节，sha256 `d3bf6a76b09730e59030cca70e32f985d6c7f8d4071cc5ccc02792ee642a683a`，badging `versionCode=1039030 / versionName=1.22.10 / native-code: arm64-v8a`，已归档 `<本地目录>/黑曜/03-构建产物/`。

## 1.3 v1.22.11 变更（2026-09-29 第二轮真机反馈，DFW-4）

用户原话："我授予权限之后，我的 MT 管理器里面并没有看到东方无限为名的文件夹"；"点击崩溃日志开启后，首先打开的界面是从顶部开始能看到的 4 个按钮，但是不到一秒钟，瞬间就是闪了一下，然后看到了崩溃日志的顶部……挺画蛇添足的"。

- **① 文件夹没出现（根因：只调 mkdirs 不看结果）**：旧 `ensureCrashFolder()` 静默吞掉失败，"建成功"与"建失败"在界面上完全一样。
  - 改为**回读确认** `folder.isDirectory()` 并返回真实结果；成功置 `crashFolderEnsured` 标志；
  - 放一份空 `crash.log` 占位（部分文件管理器不显示空目录）；
  - `onResume` 补建一次（幂等、放后台线程），用户在系统设置里**自己**开权限也能被接住，不必非走应用引导；
  - 崩溃日志页新增**常驻「打开崩溃日志文件夹」**入口（`DocumentsContract` 目录 URI，失败兜底复制路径 + 打开文件管理器）；
  - 新增状态卡，把**绝对路径**和"文件夹是否已就绪"直接写进页面，用户不用去文件管理器里猜。
- **② 打开后瞬间闪一下、顶部按钮被滚走（根因：setTextIsSelectable 让正文成为触摸模式焦点）**：
  正文 `setTextIsSelectable(true)` 同时使它在触摸模式下可获焦；页面铺开后系统把焦点自动交给子树里第一个触摸可获焦视图（那块长正文），
  ScrollView 随即把它滚进可视区，**实测 scrollY 0→756**，顶部操作卡因此被顶出屏幕。
  - 修法：`scroll.setFocusableInTouchMode(true)` + `setDescendantFocusability(FOCUS_BEFORE_DESCENDANTS)`，让滚动容器自己当锚点，焦点落容器上不产生任何滚动；进页后再 `scrollTo(0,0)` 兜底。
  - **不砍选区**：正文保留 `setTextIsSelectable(true)`，长按复制能力不受影响（有专门断言守住）。
- **测试**：新增 `CrashLogPageJvmTest`（4 例，**验证过鉴别力**：去掉焦点锚点修复后立刻 1 红）。宿主 **63 例全绿**。
- **产物**：`dongfang-wuxian-v1.22.11.apk`，badging `versionCode=1039031 / versionName=1.22.11 / native-code: arm64-v8a`，已归档 `<本地目录>/黑曜/03-构建产物/`。

## 2. 当前完成度（v1.22.8 覆盖范围）

2.5.1 → 2.5.5 clean import 已完成并已入库（提交 `6eb27b7`），补丁按 `rikkahub/PATCHES.md` 重放：

- **P16 顶层化**：上游 `RouteActivity.AppRoutes()` 抽为顶层 `ui/routes/AppRoutes.kt`（差异仅 4 处，文件头有清单）；`RouteActivity` 瘦身为外壳；`dfwx/VolumeKeyBridge.kt` + `dfwx/RikkaHubEmbed.kt`（约 60 行壳）。**不再维护 400+ 行路由复制品——这是"上游更新我们也能跟"的长期可维护性底座。**
- **AI-004 落地**：删除内置渠道播种与构建期 resValue 注入，新增 `dfwx/DfwxBuiltinProviderCleanup.kt` 清理存量（走 AI-003 保守身份证明）。**不再内置任何 API。**
- **P17/P25 品牌化**：`DongfangTheme` 置首 + 全新安装默认主题；背景 OLED 真黑 `#000000`；关闭上游默认开启的 `dynamicColor`（否则 Android 12+ 系统壁纸取色会完全绕过预设主题）。
- **U1/NET-001 + U4–U8 重放**：AI 链 HTTPS-only 四道防线；EmojiBurst 空闲挂起；三处空态 72dp FAB 净空；SettingWebPage 88dp 底部净空。
- **依赖跟进**：haze-glass（2.5.5 新增）、quickjs 换 `io.github.dokar3:quickjs-kt:1.0.15`、floatingx 迁 `io.github.petterpx:*:3.0.0`。
- **宿主 IME 补偿收敛（v1.22.8，提交 `953fa0e`）**：删除宿主自研 250ms IME 滑行器 / stickyBelow 粘滞缓存 / 动画计数器（**与系统逐帧派发竞态，是旧版遗留、越打补丁越糟的 bug 根因**），只保留 `target = ime.bottom - host.getPaddingBottom()` 一条纯几何换算。**红线：禁止再给 AI 内嵌页叠加任何自研 insets 动画/缓存。**

## 3. 已有证据

- 外部下载已分为 `lanzou`、`external`、`update`、`legacy`；外部下载入口已做 HTTPS、凭据、loopback、私网和 DNS fail-closed 策略（SEC-001，v1.22.5 起在发版链路中）；
- 外部/legacy APK 不允许自动安装或 ADB/Shizuku 静默安装，安装前需要用户确认；
- AI-002 设计报告已入库（`docs/tasks/DFWX-AI-002-report.md`），**已按报告落地 P16**；
- AI-003 数据保护层已入库：`SettingsDataGuard`（保守身份证明/悬空引用修复/迁移不变量门），12 项契约测试全过（`data/dfwx/` 下）；
- NET-001 已入库并重放：vendor AI 链 HTTPS-only（`AiUrlPolicy` + 拦截器 + DNS 防线，18 用例；AI 专用 client 派生，不影响非 AI 流量）；
- SEC-003 已入库：release 签名 fail-closed，缺配置构建失败，`-Pdfwx.unsigned` CI 开关保留；
- STAB-001 已入库（`54837c9`）：下载历史 debounce 落盘 + flush 不阻塞主线程 + generation/owner 双校验；
- ADB-001 已入库（`0e37e6a`）：ADB/Shizuku 状态车道 + generation 闸门（修旧评估覆盖新状态的竞态）；
- 补丁台账 `rikkahub/PATCHES.md` 已按 2.5.5 基线重写（P1–P28 + U1/U4–U8 全部登记，含"同步上游时需重放"标注）+ 宿主节含 v1.22.8 IME 收敛条目；
- **测试基线（2026-09-29 本机 JVM 实测）**：宿主 **57 例全绿**（v1.22.9 新增 6 例）；vendor 侧 2026-09-28 基线 628 例全绿（宿主 51 + vendor app 240 + ai 199 + common 1 + search 16 + highlight 53 + material3 1 + oauth 2 + speech 43 + web 1 + workspace 20 + document 1）；
- **release 产物证据**：v1.22.8 APK 内 `drawable/afdian`、`drawable/kofi`、`string/donate_page_*` 均存在；dex 含 `SponsorAPI`、`SettingDonatePage`、`/sponsors` 与 `https://sponsors.rikka-ai.com` 字符串（赞助闪退排查的排除项依据）。

## 4. 当前尚未完成

### 4.1 真机反馈的三项（当前优先，详见任务卡）

- **`DFWX-BRAND-003` 赞助按钮闪退** —— ✅ **代码已修（v1.22.9）**，根因=aapt2 `--no-xml-namespaces` 剥离资源命名空间
  （详见 §1.1）；回归测试 2 例已绿，宿主 57 例全绿。**剩余：真机装机验收**（用户装 v1.22.9 release 包验证赞助页不闪退）。
- **`DFWX-UI-005` AI 页顶栏键盘错位**：v1.22.8 后输入框正常、顶栏仍错位。**未取证前不许改代码**，且禁止再加自研 insets 动画/缓存。
- **`DFWX-UI-006` AI 页空态两行文案删除**：用户已明确要求删除（本地补丁 P19/P20/P21），删后与上游一致、同步成本下降。

### 4.1.1 崩溃报告导出（用户 2026-09-29 要求）

✅ 已实现（`App.diagnostics()` + 崩溃日志页"保存为文件/分享报告文件"，守卫测试 4 例）。
**剩余：真机验证保存与分享链路实际可用。**

### 4.2 原计划队列

- AI-005（AI 生命周期、日志、HTTPS 和回归门禁；SEC-004 的前置）；
- SEC-004 日志脱敏；TEST-001 测试矩阵；
- 赞助页重做（BRAND-001）、关于/致谢/隐私/许可证页（BRAND-002）；
- UI 四卡（UI-001 动画取证 / UI-002 设置页 / UI-003 下载页 / UI-004 AI 页）、宿主领域拆分（ARCH-001）、vendor/依赖/CI/SBOM/许可证治理（DEP/CI/RELEASE）；
- release 真机负向链路验证（用户已豁免日常下载/安装类验证）；
- **内部服务器（已上线，APP 端未接入）**：阿里云轻量 `39.106.33.135`，已跑 nginx，接口 `/health`、`/api/notice.json`、`/api/version.json`、`/apk/`；APP 端接入代码**尚未开发**（远程公告/远程更新为待立项需求）。**任何窗口可直接 `ssh dfwx '<命令>'`**；手册见技能 `dfwx-server`。

### 4.3 用户待办（只有用户能做）

1. 去 <已停用渠道> 停用/更换 v1.22.6 公开 APK 中已泄露的 AI Key；
2. 阿里云控制台开启 MFA + 操作保护。

## 5. 当前第一执行任务

**`DFWX-BRAND-003`（赞助闪退）** —— ✅ 代码已修并出 v1.22.9 包；**当前第一执行改为"真机装机验收 v1.22.9"**。
其后 `DFWX-UI-005`、`DFWX-UI-006`（建议合并同一轮），再回到原队列 `DFWX-TEST-001` → AI-005 → SEC-004 → …

研究报告与当前代码有出入时，以当前代码为准。

## 6. 证据等级

- A：当前源码、当前测试输出或真实产物检查；
- B：项目规范和已核对的任务文档；
- C：历史计划、旧截图、旧版本行号；
- D：模型推测。

任何任务完成说明必须写明证据等级和未验证部分。
