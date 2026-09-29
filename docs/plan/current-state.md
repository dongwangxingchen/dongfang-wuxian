# 东方无限：当前状态

> 这是当前事实页，不是历史计划。源码、命令输出和当前规范优先于旧文档。
> 更新日期：2026-09-29（v1.22.9：BRAND-003 赞助闪退根因修复 + 崩溃报告可导出）

## 1. 工作区

- 桌面交接目录：`/Users/lishaowei/Desktop/东方无限`（仅存交接包 zip，勿删勿动）
- 实际源码根目录：`/Users/lishaowei/heiyao/src`
- 当前分支：`test`
- 当前 HEAD：见 `git log --oneline -1`（2026-09-29 起含 v1.22.9 提交）
- 工作树：**源码零改动**；`docs/handover/` 为 untracked（**故意不提交，勿顺手入库**）
- 回退点：git tag **`dfwx-pre-255-import`**（2.5.5 导入前）；vendor 备份 `~/heiyao/vendor-backup-before-255-20260928.tar.gz`（43MB）；旧补丁台账 `~/heiyao/PATCHES-231-backup-20260928.md`（三者**勿删**）
- applicationId：`dfwx.dongdang`；宿主 Java 包：`cc.nkbr.lanzouplus`
- `rikkahub/`：Kotlin/Compose vendor（**上游锚点 = re-ovo/rikkahub tag `2.5.5`，versionCode 190**）与东方无限补丁层
- 上游源码快照 `/tmp/rikkahub-255/`：**已清空**（临时目录），需要时重新下载
- 桌面交接包 `东方无限_交接总包_20260920.zip`：禁止删除或移动
- 版本号：versionCode `1039029` / versionName `1.22.9`（`app/build.gradle.kts:33-34`）；下一个版本递增为 `1039030` / `1.22.10`

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

1. 去 aizhongzhuan.cc 停用/更换 v1.22.6 公开 APK 中已泄露的 AI Key；
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
