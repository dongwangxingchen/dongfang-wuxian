# 东方无限：当前状态

> 这是当前事实页，不是历史计划。源码、命令输出和当前规范优先于旧文档。
> 更新日期：**2026-10-01（v1.0.8：修下载失败真根因=域名池里有证书过期的域名 / 动效曲线与时长统一 / 首启引导）**
>
> ⚠️ 本文下半部分（§2–§5）记录的是 v1.22.x 时期的历史现场，**很多已经不再成立**。
> 要开工请直接看 **`docs/handover/README.md`**（30 秒接手的单一入口）。

## 1. 工作区

- 桌面交接目录：`/Users/lishaowei/Desktop/东方无限`（仅存交接包 zip，勿删勿动）
- 实际源码根目录：`/Users/lishaowei/heiyao/src`
- 当前分支：`test`
- 当前 HEAD：见 `git log --oneline -1`（2026-09-29 起含 v1.22.9 提交；v1.22.10 改动尚未提交，见 §1.2）
- 工作树：**源码零改动**；`docs/handover/` 为 untracked（**故意不提交，勿顺手入库**）
- 回退点：git tag **`dfwx-pre-255-import`**（2.5.5 导入前）；vendor 备份 `~/heiyao/vendor-backup-before-255-20260928.tar.gz`（43MB）；旧补丁台账 `~/heiyao/PATCHES-231-backup-20260928.md`（三者**勿删**）
- applicationId：`dfwx.dongdang`；宿主 Java 包：`cc.nkbr.lanzouplus`
- `rikkahub/`：Kotlin/Compose vendor（**上游锚点 = re-ovo/rikkahub tag `2.5.5`，versionCode 190**）与东方无限补丁层
- 上游源码快照 `/tmp/rikkahub-255/`：**已清空**（临时目录），需要时重新下载
- 桌面交接包 `东方无限_交接总包_20260920.zip`：禁止删除或移动
- 版本号：versionCode `10000` / versionName `1.0.0`（`app/build.gradle.kts`）
  - [DFW-118 2026-10-02] **路线 B：只写版本名，序号自动算**（用户选定）。
    `val appVersionName = "x.y.z"` 是**唯一可改的数字**；
    `versionCode = major*10000 + minor*100 + patch` 由它推导 —— 改不错。
    用户原话：「版本序号能不能和版本号一样？都是 1.0.0，然后 1.0.1，这样不断叠加下去？」
    「路线 B 吧，底子打得好是很好的。反正你都能可以给我新包，我完全不怕重新安装一个呀。」
    - 改版本用 `bash tools/bump-version.sh`（默认修订号 +1；`--minor` / `--major` / 显式 `x.y.z`）。
    - ⚠️ **切换后的第一版必须卸载重装**：手机上是旧计数器的 `10034`，
      而新方案 `1.0.0` 算出 `10000`，安卓会当降级拒绝安装。之后永远对齐。
  - [DFW-97，已被路线 B 取代] 曾有一段时间 versionName 固定 `1.0.0`、`buildCode` 手写递增。
    那个计数器**纯靠人记得加**，DFW-117 就是这么出的：后台填 `10028` 而手机装着 `10034`，
    软件判定"这更新比我还旧"，更新弹窗永远不出现。路线 B 让这个错**物理上不可能再发生**。
  - **versionCode 仍然只增不减** —— 安卓靠它判断能不能覆盖安装；
    把它调小会让新包被当成降级直接拒绝安装（提示"应用未安装"），界面上完全看不出来。
  - **2026-10-01 由 1.0.0 → 1.0.1 → … → 1.0.25**（旧计数器 `10001`–`10025`）：修好更新链路时需要真实的版本递增来验证（见 DFW-82）；
    1.0.2 带上东方助手与头像（DFW-84）；1.0.3 按官方提示工程建议把提示词改成 XML 分段结构；
    **1.0.21 = DFW-94 删除下载悬浮窗**（下载页与全部记录保留）；
    **1.0.22 = DFW-102 下载完成/失败通知**（顶部滑下、带文件名）；
    **1.0.23 = DFW-91 版本号只留一处**（「检查更新」右侧）。
    **1.0.24 = DFW-90/95/96/103 一批**（更新机制解耦、搜索页底部留白+返回淡化、全局搜索每源 3 页、公告弹窗秒弹）。
    **1.0.25 = DFW-97 反馈入口**（设置页「检查更新」上方；FlowUs 页面按计算样式重映射换肤成 OLED 黑+紫）。
    ⚠️ **v1.0.15（GitHub 预发布 tag `test-20261001-24`）已作废**（2026-10-01，DFW-122 第 4 条标注）：
    发版时用 `;` 而不是 `&&` 串联「跑门禁」和「构建/发布」，**门禁红了还是发出去了** ——
    红的原因是 `NetworkSecurityConfigJvmTest` 的白名单没跟上 `d427ed0`（该提交只改了代码与
    `network_security_config.xml`，没改测试）。**包内容本身没问题**（就是 `d427ed0` 的 ilanzou 修复），
    作废是**流程错误**，不是包坏了；补上测试白名单后以 **1.0.16** 重发（`7044bff`：
    `app/build.gradle.kts` 的 `10015`→`10016`）。
    **与 v1.18.0 的 ABI 翻转事故无关**（那是 2026-09-30 归零前的旧编号线）。
    注：GitHub 上那个预发布**未加任何作废标记、仍可下载**，服务器也仍提供 `v1.0.15.apk`。
    这些旧序号（`10001`–`10034`）**已随路线 B 作废**，不会再出现；用户重装一次后从 `10000` 重新往上走。
    **改版本号必须同步本行**（`DocTimelinessJvmTest` 会红）。
  - **2026-09-30 归零**（用户决定）：从 `1039039` / `1.22.19` 重新计为 `1.0.0`，之后的更新基于此。
  - 编号规则：`versionCode = major*10000 + minor*100 + patch`（1.0.1 → 10001，1.1.0 → 10100）。
  - 守卫测试 `VersionSchemeJvmTest`：确保 `versionCode` 确实等于由 `versionName` 推出的值，
    且不低于已发布过的最高版本 —— "改了版本名却没改版本号"这类错误无法通过。
  - 旧版本 v1.2.x–v1.22.x 全部转入 GitHub **预发布**作"测试专区"存档（不删，见 DFW-63）。

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
- **产物**：`dongfang-wuxian-v1.22.10.apk`，37046462 字节，sha256 `d3bf6a76b09730e59030cca70e32f985d6c7f8d4071cc5ccc02792ee642a683a`，badging `versionCode=1039030 / versionName=1.22.10 / native-code: arm64-v8a`，已归档 `~/heiyao/黑曜/03-构建产物/`。

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
- **产物**：`dongfang-wuxian-v1.22.11.apk`，badging `versionCode=1039031 / versionName=1.22.11 / native-code: arm64-v8a`，已归档 `~/heiyao/黑曜/03-构建产物/`。

## 1.4 v1.22.12–1.22.13 变更（2026-09-30，按看板队列逐个推进）

- **DFW-3（UI-006）**：删除 AI 页空会话两行占位文案（P19/P20/P21 整块移除）。
  删前用上游快照 `re-ovo/rikkahub` tag `2.5.5` 做 diff 核验，`ChatList.kt` 差异只剩已声明保留的 P16。
- **DFW-7（T14）**：更新检查复活。`maybeCheckForUpdates()` 原本**零调用点**（自 v1.0.2 基线起就是死代码），
  现补：启动后 6 秒静默查一次 + **24h 落盘节流**（旧实现只有进程内 AtomicBoolean，当天反复冷启会反复打网络）
  + 设置页「检查更新 · 当前版本」手动入口；顺手纠正品牌（对话框/下载文件名原带上游包名 `LanzouPlus`）。
  更新源继续走 GitHub HTTPS，**未接**自有服务器（那条路是明文 HTTP + sha256 为空，接它等于放宽 NET-001 红线）。
- **DFW-8（SEC-004）**：AI 日志脱敏。取证发现任务卡只说对一半——点名的自研拦截器其实默认关闭，
  真正 release 常开的是 okhttp `Level.HEADERS` 与 Provider 的 SSE 全量响应体。
  三处全部收敛，新增 `common/.../dfwx/DfwxLogRedactor.kt`，并把脱敏做进 `Logging.logRequest()` 这个**唯一存储入口**
  （调用方忘了脱敏也绕不过去）。
- **DFW-9**：Firebase Analytics 整链移除（含 5 个上报事件与 Koin 装配）。
  **APK 级证据**：权限 31 → 27 条，消失的 4 条全是广告/归因权限（`AD_ID`、`ACCESS_ADSERVICES_*`、
  `BIND_GET_INSTALL_REFERRER_SERVICE`）。BOM 保留（MLKit 传递依赖需要版本对齐，BOM 自身不引入依赖）。
- **DFW-2（UI-005）**：仍**无法取证**，未改代码。真机 `adb` 连不上；且卡片建议的 Robolectric 兜底路走不通——
  AI 页在 JVM 下必然进不去（`App.DEGRADED` 早退，`ToolsSweepJvmTest.kt:13-16` 有记录），
  承载 ime 监听器的 `wrap` 不会被创建。复核记录已追加到 `docs/tasks/DFWX-UI-005.md`，等真机。

## 1.5 v1.22.14–1.22.18 与 CI 门禁（2026-09-30，按看板队列逐个推进）

| 卡 | 版本 | 结论摘要 |
|---|---|---|
| DFW-12 | v1.22.14 | 接收系统分享/链接：manifest 加 SEND(`text/plain` 非 `*/*`) + VIEW(`http`/`https`)，代码侧第二道 scheme 校验 |
| DFW-13 | v1.22.15 | 字体缩放适配：新增 `dpText()`，文字容器随 fontScale 等比放大（上限 1.8×）。**取证发现 Robolectric 字体度量是桩**（`measureText("中")=1px`），所以断言改为不依赖字形的数值关系 |
| DFW-14 | v1.22.16 | 补 `onTrimMemory` 分级释放；crash.log 加锁。**卡片 5 条里 2 条与事实不符**（清理槽早已分离、缓存本就有界）；帧率类改动因无真机测量而**不做** |
| DFW-15 | — | 两个调试残留 `git rm --cached`（本地文件保留）；ignore 规则全部锚定到仓库根；快照目录实测未被跟踪，无需处理 |
| DFW-16 | v1.22.17 | 分享密码可一键清除（只清密码不删记录）；删除死配置 `requestLegacyExternalStorage`；**APK 级 aapt 证据**确认 `allowBackup=false`/明文 false/NSC 生效 |
| DFW-17 | v1.22.18 | 预测性返回：**卡片引用的是已修掉的旧状态**（v1.22.1 已走 androidx 动画回调）；本轮修网页页无条件注册裸回调导致无预览，并修掉"方法引用每次新对象导致注销无效"的坑 |
| DFW-18 | — | README 去掉手表承诺（按 decisions #13）、纠正"内置渠道"；SECURITY.md 整体重写（原停在 1.8.x 且三处信息已变错） |
| DFW-31 | — | **CI 分层门禁**：新增 `tools/ci-gate.sh`（本地与 CI 同一脚本，满足"结论可对照"），三层 docs/unit/apk；`.github/workflows/ci-gates.yml` 替换"从不跑测试"的旧工作流 |

**共性做法**：每卡都先取证再改，测试**逐一验过鉴别力**（把修复改回去必须变红，避免"写了就绿"的空测试）；
事实与卡片不符时**如实报告而不是照抄**（DFW-14 两条、DFW-17 前提、DFW-18 工具数量）。

## 1.6 本轮全部产出（2026-09-30，24 个提交）

**新增的工程设施**（这些是"给下一个接力者"的东西，比单个修复更重要）：
- `tools/ci-gate.sh` —— 本地与 CI **共用**的分层门禁（docs / unit / apk 三层），
  解决"CI 从不跑测试"与"本地与 CI 结论对不上"两个问题。
- `tools/release.sh` —— 把 `dfwx-release` 技能的红线变成**可执行断言**
  （verify / build / publish 三个子命令，publish 默认 dry-run）。
- `tools/diagnostics/CrashLogRaceProbe.java` —— crash.log 并发竞态的可独立运行复现器。
- `docs/THIRD-PARTY-NOTICES.md` —— SBOM 简版（含已移除组件记录，避免"以为还在"）。
- `.github/workflows/ci-gates.yml` —— 三层 CI，替换"从不跑任何 test 任务"的旧流程。

**测试规模**：宿主 51（基线）→ **160+ 例**；vendor 240 → **252 例**；common **14 例**。
所有新增守卫都**逐一验过鉴别力**（把修复改回去必须变红），避免"写了就绿"的空测试。

**本轮修掉的真实缺陷**（不是文档整理）：
- 更新检查是死代码（用户永远收不到新版本提示）；
- AI 日志在 release 常开地记录 Authorization/Cookie/SSE 完整响应体；
- Firebase Analytics 仍在活装配上报（含 4 条广告/归因权限）；
- 全局明文流量开关（无网络安全配置）；
- 条目卡片等文字容器不随系统大字体缩放（适老化）；
- 秒表计圈逐条覆盖（等于没有计圈）、UUID 工具按钮整行重复；
- 网页页无条件注册裸返回回调导致无侧滑预览（及"方法引用注销无效"）；
- **静默安装在中文系统下"成功"被判为失败**（语言依赖缺陷）；
- 局域网 Web 控制台默认监听 `0.0.0.0` 且 JWT 关闭；
- `sqlite-android` 依赖用 `-SNAPSHOT`（不可复现、绕过评审）；
- 崩溃日志页两处真机反馈（文件夹没建出来、顶部按钮被自动滚走）。

**仍待用户**：真机验收（本机 adb 连不上手机、无模拟器）；DFW-2/23/26/29/24/25/27 等
需要真机取证或审美选型的卡；DFW-19/5/6 等需立项或拍板的卡。

## 2. 当前完成度（v1.22.8 覆盖范围）

2.5.1 → 2.5.5 clean import 已完成并已入库（提交 `6eb27b7`），补丁按 `rikkahub/PATCHES.md` 重放：

- **P16 顶层化**：上游 `RouteActivity.AppRoutes()` 抽为顶层 `ui/routes/AppRoutes.kt`（差异仅 4 处，文件头有清单）；`RouteActivity` 瘦身为外壳；`dfwx/VolumeKeyBridge.kt` + `dfwx/RikkaHubEmbed.kt`（约 60 行壳）。**不再维护 400+ 行路由复制品——这是"上游更新我们也能跟"的长期可维护性底座。**
- ~~**AI-004 落地**：删除内置渠道播种与构建期 resValue 注入……**不再内置任何 API。**~~
  **⚠️ 2026-10-01 已被推翻**：用户拍板**恢复「内置渠道」**（DFW-73），走服务端中转 ——
  APK 里只有我们自己服务器的地址与应用令牌，上游地址和真 Key 只在服务器上。
  - **2026-10-01 更正（本行上一版写错了）**：上一版写"AI-004 的删除动作由 `DfwxBuiltinProviderCleanup`
    执行，现在该清理**已被撤销**"——**这句是错的，清理器没有被撤销，仍在跑**。
    实测：`rikkahub/app/src/main/java/me/rerere/rikkahub/RikkaHubApp.kt:118` 仍调用
    `DfwxBuiltinProviderCleanup.removeLegacySeedIfNeeded(...)`，实现体 `DfwxBuiltinProviderCleanup.kt:45-73` 未删。
    正确关系是**两者并存、各管一个身份**（`RikkaHubApp.kt:120-123` 的注释原文）：
    清理器认的是老身份「智能中转(内置)」（`baseUrl=https://www.aizhongzhuan.cc/v1` + 模型 `glm-5.3`，
    见 `DfwxBuiltinProviderCleanup.kt:37-43`），负责把老版本播过的这条一次性移除；
    **新身份「内置渠道」**由 `DfwxBuiltinChannel.syncIfNeeded()`（`RikkaHubApp.kt:123`）每次启动同步播种，
    走自有服务器中转（`DfwxBuiltinChannel.kt:49` `DEFAULT_BASE_URL = "https://39.106.33.135/ai/v1"`）。
    所以"新装不再播老渠道"与"新装要播新渠道"同时成立，并不矛盾。
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
- **内部服务器（已上线且 APP 端已完整接入）**：阿里云轻量 `39.106.33.135`，nginx + PocketBase。
  **⚠️ 2026-10-01 更正**：旧文写"APP 端接入代码尚未开发"**已不成立**。现在已接入并跑通的有：
  远程公告、远程更新清单、APK 分发、内置 AI 渠道中转、以及一个网页控制台 `/admin/`
  （可远程改公告 / 发新版 / 切内置模型 / 换令牌，改完用户**不用更新软件**）。
  **任何窗口可直接 `ssh dfwx '<命令>'`**；手册见技能 `dfwx-server`。

### 4.3 用户待办（只有用户能做）

1. **去 aizhongzhuan.cc 停用/更换 v1.22.6 公开 APK 中已泄露的 AI Key** ——
   **2026-10-01 升级为"建议尽快做"**：用户这一轮拍板恢复「内置渠道」，走**服务端中转**
   （上游地址与真 Key 只在服务器 `/etc/nginx/dfwx-ai-secret.conf`，600；APK 里只有自己服务器地址 +
   应用令牌）。中转本身是安全的，但这把 Key **早已在公开 APK 里泄露过**，任何人拿着它都能绕过我们的
   中转直接刷上游额度——花的还是用户每周那 10 块钱。换 Key 只需改服务器那一个文件 + `nginx -s reload`，
   用户不用更新软件（后台还能远程下发新应用令牌）。
2. 阿里云控制台开启 MFA + 操作保护。

## 5. 当前第一执行任务

**⚠️ 本节已过期。** 当前进度一律以任务面板 + `docs/handover/README.md` 为准。

v1.22.x 时期的队列（BRAND-003 → UI-005 → UI-006 → TEST-001 → AI-005 → SEC-004）**早已全部走完**，
版本号也已在 2026-09-30 归零重排为 1.0.x。不要按这一节排活。

研究报告与当前代码有出入时，以当前代码为准。

## 6. 证据等级

- A：当前源码、当前测试输出或真实产物检查；
- B：项目规范和已核对的任务文档；
- C：历史计划、旧截图、旧版本行号；
- D：模型推测。

任何任务完成说明必须写明证据等级和未验证部分。
