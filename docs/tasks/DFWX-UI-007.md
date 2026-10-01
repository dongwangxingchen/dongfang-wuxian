# DFWX-UI-007 —— 六页平级 / 返回语义 / 全站转场 / 内置渠道（DFW-73，2026-10-01 第二轮真机反馈）

> 对应任务板卡片：**DFW-73**（`dfwx-android`）。全部结论均为 2026-10-01 用户口述要求。

## 1. 八条要求与处置

| # | 用户要求 | 根因 / 做法 | 主要落点 |
|---|---|---|---|
| 1 | 公告页点悬浮球的「软件库」没反应 | 公告页用 `primaryBase(0)`（=软件库那一档）当底座 → `goToDestination(0)` 开头的 `destination==primaryDestination` 守卫把它整个吞掉。给公告一个**独立档位** `DEST_NOTICE=6` | `MainActivity.showNoticeCenter()` |
| 2 | 六个界面全部平级；侧滑返回不要总是回软件库 | 删掉 `performSystemBack` 结尾的 `if(primaryDestination>0) navigateHome()`；`showNoticeCenter` 补 `primaryNavigationSwitch=true;pageDirection=0`，让它与另外五项**进场动画一致**（原来是"从右推入"的子页动画）；NavBall 点自己也没反应这一条也补齐 | `MainActivity`、`NavBall` |
| 3 | 顶级页且没有子页面：先提示「再返回一次退出软件」，第二次才退出 | 新增 `atTopLevelRoot()` / `confirmExitToSoftware()` / `showExitConfirmBanner()`；`canHandleBack()` 改为**恒 true**（顶级页也必须自己吃掉这次返回，否则系统直接 finish，横幅没机会出现） | `MainActivity` |
| 4 | 设置里加「初始界面」选项（软件库 / AI 对话 / 工具箱） | 新增 `buildStartDestinationRow()` 三芯片行 + `startDestinationPreference()`；非法值一律回落 0=软件库；AI 分支走权限引导链再进 | `MainActivity` |
| 5 | 诚信付费页顶部被状态栏挡住；权益改 1 条；删两行文案 | `SupportActivity` 是独立 Activity，**此前完全没处理窗口 insets**。改 edge-to-edge + 内容根吃 `systemBars()` padding | `SupportActivity` |
| 6 | 设置入口删小字、大字左边加奖杯 | `IntegrityPayButton`：未付费态副标整行删除；标题行加 `ic_trophy`（Phosphor MIT，20dp，与标题同色，两种状态都加） | `IntegrityPayButton`、`ic_trophy.xml` |
| 7 | 全站预测式返回；"过渡像碎纸条"；软件库/AI 保持原动画；弹窗动画统一；公告切换动画与另外 5 项一致 | `animatePage` 的推入/返回从"两页同时位移+淡出"改成**实体层级位移**（下层页不透明留在原地）；跟手预览去掉淡出（淡出露黑底就是"碎纸条"）；AI 页与顶级根不做跟手预览；弹窗动画统一挂 `AppTheme→alertDialogTheme→windowAnimationStyle`，所有 `AlertDialog` 自动一致，零调用点改动 | `MainActivity.animatePage` / `installBackAnimationCallback`、`res/anim/dfwx_dialog_*.xml`、`res/values{,-night}/styles.xml` |
| 8 | 内置渠道（服务端中转） | 上游地址与真 Key **只在服务器** `/etc/nginx/dfwx-ai-secret.conf`（600）；APK 里只有自己服务器地址 + 应用令牌。不记次数、不做配额。模型名 `deepseek-v4.1-flash`，最大输出 8192，渠道只读，未付费点它弹「关闭」/「知道了」引导窗，地址/令牌/模型/最大输出/开关后台可改 | 新增 `BuiltinAiChannel.java`、`DfwxBuiltinChannel.kt`；改 `App.java`、`RikkaHubApp.kt`、`ModelList.kt`、`SettingProviderPage.kt`、`RemoteConfigClient.java`、`app/build.gradle.kts`、`server/dfwx-admin/index.html` |

## 2. 关键设计决定（以及为什么不是另一种做法）

### 2.1 公告页为什么保留"从哪来回哪去"

用户要的是"六项平级"，但 DFW-72 已经钉死"从设置进公告，返回回设置"（用户当时明确否决了"返回一律回设置"）。
二者不冲突：**平级**说的是"能直接从任何一页到达公告、不经过设置当底座"，
而公告自己记着 `systemBackAction = noticeReturnAction()`，所以它在 `atTopLevelRoot()` 里**不算顶级根**。
其余五项才是真正的根，返回语义 = 「再返回一次退出软件」。

### 2.2 预测式返回为什么在根页/AI 页被关掉

- **根页**：返回语义只提示、不换页。若还做跟手缩放，页面会"缩一下又弹回去"，看着像故障。
- **AI 页**：用户明确要求"AI 对话保持原有动画"，且 AI 页的返回由 Compose dispatcher 接管（抽屉/子路由）。

两处都在 `predictiveBackPreviewAllowed()` 里，一处判定、两处生效。

### 2.3 弹窗动画为什么走 theme 而不是改调用点

全仓 `AlertDialog` 有几十个调用点，逐个加动画必然漏。挂在
`AppTheme → android:alertDialogTheme(DfwxDialog) → android:windowAnimationStyle` 上，
**所有** `AlertDialog` 自动一致；系统"动画时长缩放=0"时窗口动画本身就会被系统跳过，不用额外门控。

## 3. 验证要求

- 反向探针：每条修复都要能"改回原样 → 对应断言变红"。至少覆盖：公告档位、`canHandleBack`、
  `atTopLevelRoot`、初始界面三选项、insets padding、删掉的小字、新增的权益文案。
- 门禁：`DFWX_OFFLINE=1 bash tools/ci-gate.sh docs` + `unit` 全绿。
- 产物：release APK 重建，`aapt dump badging` 断言 `arm64-v8a` + 版本号。
- 服务器侧：内置渠道中转已实测（401 / 200 / 真流式 2362 chunk / models 静态列表 / 响应头无上游特征 / 429 限速 / 日志无 Key）。

## 4. 遗留与提醒

1. **上游 Key 曾随公开 APK 泄露**（`docs/plan/current-state.md` §4.3 第 1 条）。
   服务端中转本身安全，但那把 Key 任何人拿到都能**绕过我们的中转**直接刷上游额度——花的还是用户每周那 10 块钱。
   换 Key 只需改 `/etc/nginx/dfwx-ai-secret.conf` 一个文件 + `nginx -s reload`，**用户不用更新软件**
   （应用令牌还能从后台远程下发）。
2. 上游模型在被问"你是谁"时自称 Claude（实测）。这是 `deepseek-v4.1-flash` 这个别名背后的真实模型行为，
   不是我们的 bug；若用户在意，属于要不要在文案上回避的产品决定。
3. `Access-Control-Allow-Origin: *` 由上游带回，`/ai/` 未剥离（原生 Android 客户端无影响）。
   若将来要连浏览器侧滥用一起堵，加一行 `proxy_hide_header Access-Control-Allow-Origin;` 即可。

---

## 5. 追加：2026-10-01 真机试用后的四条反馈 + DFW-26 审计查出的两条硬伤

用户装 `test-20261001-3` 试用后反馈 4 条（本轮全部修完）：

| # | 反馈原话要点 | 根因 | 处置 |
|---|---|---|---|
| 1 | 付费成功后扫光"只覆盖 2/3、有割裂感" | shine 只有 40dp 高（按钮约 76dp），且是对角渐变被圆角裁出硬边 | `IntegrityPayButton`：铺满整高、改水平柔和亮带 |
| 2 | 付费页预返回动画与其他页不一致 | `SupportActivity` 是独立 Activity，走系统默认转场 | 用 `overridePendingTransition` + `res/anim/dfwx_page_*` 对齐站内推入/返回语义 |
| 2b | 崩溃日志"点进去卡一下，上半部分打开、下半部分透明" | ① `buildCrashReport()`+文件 IO 在**转场那一帧**同步跑；② 页面帧没有底色，滑入时下面那截透出旧页 | 正文延后一帧再填；`basePage` 给页面帧统一刷 BG |
| 3 | 点「检查更新」显示"检查更新失败，无法获取更新信息" | 后台可达但版本不比当前新 → 继续走 GitHub 兜底 → `/releases/latest` 因全部旧版本标成预发布而 **404** → 抛异常 | 后台能回答就以它为准（不再打 GitHub）；`UpdateClient.isNoReleaseError()` 把 404 视为"该源没有正式版本" |
| 4 | 设置里点初始界面芯片后弹出的提示"特别割裂"，要求全部改用图三那种顶部样式 | `showNotice`（227 处共用底座）是右上角 250dp 小胶囊 + 从**左边**横滑 + 淡入 | 全站统一到 DFW-66 的 `NoticeBanner`（顶部滑下、无遮罩、可滑动关闭）；抽出唯一构造点 `showTopBanner()` |

同时 DFW-26（AI 页整体验收）审计查出两条**必现**硬伤，也一并修了：

- **AI 页根态系统返回键完全失效**：`performSystemBack` 里那句「AI 页若 Compose 还有回调就转交给它」用的
  `getOnBackPressedDispatcher().hasEnabledCallbacks()` 语义是"任意 enabled handler"，而我们自己的
  backCallback 在 DFW-73 之后恒启用 → 判据恒真 → **自我派发** → 被 260ms 节流挡回 → 毫无反应。
  正确判据不用写：dispatcher 后注册优先，Compose 要消费根本轮不到我们。
- **内置渠道每次启动重造模型 UUID** → `chatModelId` 悬空、**每次重启都要重选模型**；
  且 `next == settings` 永假 → 每次启动白写 DataStore。同步时改为沿用已存在的 id；
  顺带修掉 `maxTokens=8192` 静默失效（`Assistant.chatModelId` 默认是 null，判据要覆盖"跟随全局"这一种）。

### 一个必须记住的教训

`getOnBackPressedDispatcher().hasEnabledCallbacks()` **不是**"除我之外还有人能处理返回"的意思，
而是"任意 enabled handler"——只要自己的回调是启用的，它就恒为 true。
凡是想用它判断"要不要把返回让给别人"的地方，都是错的。
