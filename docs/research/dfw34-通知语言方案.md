# DFW-34 · 通知卡片统一语言方案

> 2026-10-03。**只读调研，未改动任何代码。** 依据：`docs/design/wear-ui-system.md`（v2，token 唯一真相源）、项目源码实际行号。
> 目标（用户原话）：**「采用自研通知卡片，有自己的辨识度；不能和软件一模一样，也不能脱离整体风格。」**

---

## 0. 结论先行

现在有三条互不相识的提示路径，**圆角 18 / 16 / 8dp 三套、时长 2500/5000 与 4000 两套、锚点三个**，且三个圆角**全都不在规范 §4 的四档内**（Circle / Pill / Card 20dp / Panel 26–30dp）。

推荐 **方案 B「双锚点」**：顶部锚点放瞬时提示（短提示 / 结果反馈 / 需行动的错误），右下锚点放常驻进度（下载 / 批量测试），两者圆角统一到 **Card 20dp**，时长统一到 **3000 / 4000 / 常驻** 三档，语义只用**图标色 + 左侧 2dp 色条**区分而不换底色（守住 OLED 真黑 + §5 亮度阶梯）。

---

## 1. 现状盘点

### 1.1 三条路径对照

| 维度 | ① `showNotice` → `NoticeBanner` | ② 下载 / 批量任务卡 | ③ AI 侧 Sonner |
|---|---|---|---|
| 源码位置 | `MainActivity.java:5444` `showNotice` → `:5505` `showTopBanner` → `NoticeBanner.java` | `MainActivity.java:5382` `toastPanel()` → `:5383` `addToastPanel` → `:5393` `dismissPanel` | `AppRoutes.kt:208` `Toaster(...)`；vendor `io.github.dokar3:sonner:0.4.0` |
| 圆角 | **18dp**（`solidShape(SURFACE,18)`） | **16dp**（`solidShape(SURFACE,16)`） | **8dp**（`ToasterDefaults.Shape = RoundedCornerShape(8.dp)`） |
| 底色 | `SURFACE` | `SURFACE` | Compose `MaterialTheme` + `richColors = true`（语义色底） |
| 阴影 | `setElevation(dp(10))` | `setElevation(dp(6))` | `Elevation = 12.dp` |
| 内距 | 14 / 12 / 14 / 12dp | 10 / 8 / 10 / 8dp；卡高**写死 58 或 62dp** | `contentPadding = PaddingValues(16.dp)` |
| 字号 | 正文 **14sp** / `TEXT`（§6 Body 档 13.5–14.5 ✓） | **11–12sp** / `TEXT` | `BodyMedium` ≈14sp |
| 图标 | 20dp，`PRIMARY`；普通=铃铛 `ic_notifications`，更新=刷新 | 无图标，只有 `ProgressBar` 4dp | 20dp（`IconSize = 20.dp`），按类型换 |
| 位置 | 顶部，`topMargin = statusBarInset() + 8dp`，左右各 12dp，通栏 | 右上角，`statusBarInset() + 64dp`，宽 `min(250dp, 内容宽-24dp)`，`Gravity.TOP\|END` | `Alignment.TopCenter` |
| 时长 | 短 2500ms / 长 5000ms | 常驻至任务完成 | 默认 **4000ms**（短 2000 / 长 8000） |
| 入场 | `translationY: -高度 → 0` + alpha，**300ms** `PathInterpolator(0.2,0,0,1)` | `translationY: -8dp → 0` + alpha，**240ms** 同曲线 | Sonner 内置 |
| 退场 | **200ms** 同上 | alpha 0 + `-8dp`，**150ms** | Sonner 内置 |
| 滑动关闭 | 上 / 左 / 右 ≥ **72dp** 关闭；向下不关 | `swipePanel` 可左右滑 | Sonner 内置（水平） |
| 同时条数 | **1 条可见 + 队列 2**（`NOTICE_QUEUE_MAX = 2`） | 可叠加多条，视口最高 **204dp** | Sonner 自动堆叠 |
| 语义区分 | **只有图标**（铃铛 / 刷新），**无成功/失败色** | 无 | `richColors` 四色 |

行号依据：`NoticeBanner.java:40` `ENTER_MS=300`、`:41` `EXIT_MS=200`、`:42` `RESET_MS=180`、`:43` `DISMISS_DP=72`、`:45` `MAX_LINES=4`；`MainActivity.java:1038` 初始布局、`:1174` reflow、`:5376` `updateToastViewport`（上限 204dp）、`:5415` `statusBarInset()`。

### 1.2 问题清单（每条都能验证）

| # | 问题 | 证据 |
|---|---|---|
| P1 | **三个圆角都不在 §4 四档内**，违反规范 §0 第 3 条「禁止页面内 hardcode / 无 token 外任意圆角」 | 18dp / 16dp / 8dp vs §4 的 20dp、26–30dp、Pill、Circle |
| P2 | 全仓库圆角共 **14 个档位**（16×12、14×9、9×5、22×3、24×2、18×2、20/21/28… 各 1），四档体系事实上已失效 | `grep -o "solidShape([A-Za-z0-9_]*,\([0-9]*\))"` 统计 |
| P3 | **同一条路径宽度自相矛盾**：初始化 250dp、reflow 后 286dp | `:1038` vs `:1174` |
| P4 | **需用户行动的错误也会 5 秒自动消失**，用户没看到就等于没提示 | `showNotice(msg, true)` 大量调用点，如 `:2494`「下载完成但不能作为更新安装」 |
| P5 | **无成功 / 警告语义色**：`ThemeEngine.Design` 只有 `error` 字段（`ThemeEngine.java:17`），成功与普通提示长得一模一样 | 同上 |
| P6 | 用 **elevation 阴影**表达层级，与 §5「不用 elevation 阴影，用亮度阶梯」冲突 | 6dp / 10dp / 12dp |
| P7 | 主机区时长 2500/5000 与 AI 侧 4000 **两套**；跨区切换观感断裂 | 1.1 表 |
| P8 | 下载卡卡高**写死** 58dp / 62dp，长文案两行时靠 `setMaxLines(2)` 硬截 | `:2903`、`:3994` |

---

## 2. 三个统一方案

> 三方案共用同一套 **token 基线**（全部取自规范，不新造颜色）：
> - 形状：**Card = 20dp**（§4 唯一 Card 值）；若要"悬浮胶囊"另用 **Pill = height/2**，高 44–48dp（§4）
> - 底色：**`SURFACE`**（§5 Surface Low/High = 白或品牌色 5–7% / 8–10%）
> - 描边：白 **8–12%**（§5 普通描边）
> - 文字：主 `TEXT`（§5 Primary Text 92–100%）；次 `MUTED`（§5 Secondary Text 60–72%）
> - 字号：正文 **14sp**（§6 Body 13.5–14.5）；标题 **12–13sp / 字重 550–650**（§6 Label）；角标级 **11sp**（§6 Micro）
> - 图标：**20dp**（与 Sonner `IconSize = 20.dp` 天然对齐，且落在 §7「glyph 20–22dp」内）
> - 动效：入场 **300ms**、退场 **200ms**、回位 **180ms**，统一 `PathInterpolator(0.2, 0f, 0f, 1f)`（项目既有值，`NoticeBanner.java:40-42`；规范 §2 effects 档）
> - 层级：**去掉 elevation，改 `SURFACE2` 填充或白色 8–12% 描边**（§5）

### 方案 A ·「单片式」——最小改动，一个组件管全部

- **做法**：保留 `NoticeBanner` 骨架与位置（顶部 `statusBarInset()+8dp`，左右 12dp，通栏）。把圆角 18 → **20dp**；下载卡的 `toastPanel()` 圆角 16 → **20dp**，并**并入** `NoticeBanner` 作为"常驻变体"（`persistent = true` + 4dp 进度条 + 无自动消失）。语义色只用**图标色 + 左侧 2dp 通高色条**（成功 `PRIMARY`、失败 `ERROR_TOKEN`、普通 `MUTED`）。
- **一次可见条数**：**1 条**（沿用 DFW-137），队列上限 **2**。
- **改动面**：`NoticeBanner.java`（常量）+ `MainActivity.java:5382`（圆角）+ `:5505`（加语义参数）。**约 3 处。**
- **缺点**：下载进度与瞬时提示**争同一个顶部锚点**——进度卡是常驻的，会把瞬时提示挤到队列里，用户点完「复制」要等下载卡收起才看到反馈。这是它最大的硬伤。

### 方案 B ·「双锚点」——推荐

- **做法**：
  - **顶部锚点（瞬时）**：`statusBarInset() + 8dp`，左右 12dp，宽度 `min(safeContentWidth()-24dp, 420dp)`（不再写死 250/286dp，修 P3），圆角 **20dp**，用于短提示 / 结果反馈 / 需行动的错误。
  - **右下锚点（常驻）**：底部 `max(navigationBarInset, imeInset) + 12dp`，右侧 12dp，宽度 `min(safeContentWidth()-24dp, 340dp)`，形状 **Pill（height/2, 高 48dp）**，用于下载 / 批量测试 / 索引进度。
  - 两个锚点**互不占用**，各自独立堆叠（顶部仍 1 可见 + 队列 2；右下最多 **2 条**，超出合并为"还有 N 个任务"）。
- **与 Sonner 的关系**：顶部锚点与 AI 侧 `TopCenter` **同侧同向**，跨区（主机 ↔ AI）切换时提示不会突然从上面跳到下面，这是选顶部而非底部做主锚点的关键理由。
- **改动面**：`NoticeBanner.java` 常量与圆角；`MainActivity.java:5382`（圆角+形状）、`:5383`（常驻卡走右下锚点）、`:1038`/`:1174`（宽度统一）、`:5393`（退场方向改为向下）。**约 6 处。**
- **缺点**：要新增一个右下锚点容器，且要和悬浮球协商位置（见 §4.1）。

### 方案 C ·「全底部贴 M3 Snackbar」——**评估后不推荐**，仅列代价

- **做法**：全部移到屏幕底部，完全对齐 M3 Snackbar 官方 token：圆角 **4dp**（`mtrl_snackbar_background_corner_radius`）、底色 `colorSurfaceInverse`、外边距 **8dp**（`m3_snackbar_margin`）、文本 `BodyMedium`、内距水平 8dp（`mtrl_snackbar_padding_horizontal`）。
- **为什么不推荐**（三条都是硬伤，不是审美偏好）：
  1. **`colorSurfaceInverse` 是反色面板**（真黑底 → 浅底），直接违反 §5「OLED 真黑 + 亮度阶梯」，在全黑界面上会是一块刺眼的浅色砖；
  2. **圆角 4dp 又引入第五个档位**，和 §4 四档体系正面冲突（等于用"统一"的名义再破一次统一）；
  3. **底部锚点与悬浮球（`NavBall`）、系统手势条三方争地**，避让复杂度远高于顶部。
- **可以只借一条**：M3 的"**错误提示带一个动作按钮**"（`Snackbar.setAction`）——这条与 §3 的「需行动的错误」完全对得上，建议吸收进方案 B。

---

## 3. 四类消息在各方案下的形态

> 下表的 dp/ms/sp 对三方案通用（差异只在"锚点"列）。**语义色只用图标色 + 左侧 2dp 通高色条**，底色恒为 `SURFACE`。

| 类别 | 图标 | 色条 | 文字 | 位置（A / B） | 时长 | 动作 | 可滑关 |
|---|---|---|---|---|---|---|---|
| **短提示**<br>「已复制可解析链接」 | 20dp `MUTED` | 无 | 14sp `TEXT`，1 行，超 4 行省略 | 顶部 / 顶部 | **3000ms** 自动消失 | 无 | ✅ 上/左/右 ≥72dp |
| **结果反馈**<br>「测试完成：12 成功，2 错误」 | 20dp `PRIMARY`（成功）/ `ERROR_TOKEN`（失败） | 2dp 同色，圆角 20dp 内左侧 | 12–13sp 标题 + 14sp 正文；正文最多 4 行 | 顶部 / 顶部 | **4000ms**（对齐 Sonner `DurationDefault`） | 可选「查看」（≤1 个） | ✅ |
| **下载进度**<br>「正在下载 · 42%」 | 无图标，或 16dp 文件图标 | 2dp `PRIMARY` | 11–12sp `MUTED` 正文 + 4dp 进度条（§4：加载环 stroke 2–2.5dp 口径一致） | 顶部常驻 / **右下 Pill** | **常驻**，完成后再显示 4000ms 结果 | 「暂停」「取消」 | ⚠️ 滑掉只收起视觉，**不中止任务**（见 §4.3） |
| **需行动的错误**<br>「未获得管理所有文件权限」 | 20dp `ERROR_TOKEN` | 2dp `ERROR_TOKEN` | 12–13sp 标题（`TEXT`）+ 14sp 说明（`MUTED`） | 顶部 / 顶部 | **常驻，不自动消失**（修 P4） | 1 个主行动（「去授权」「重试」）+ 1 个「知道了」 | ❌ 不可滑关 |

补充规则（三方案共通）：

- **「结果反馈」只在失败时用 `ERROR_TOKEN` 色条**。按 §5 的克制原则，**成功不铺色块**，只用一个 `PRIMARY` 图标；满屏绿色成功块是廉价感来源（§3「廉价感六根源」第 1 条）。
- **同一时刻视觉上只允许 1 条"带色条"的提示**。多条同时带色 = 用户分不清哪条重要。
- 触觉：仅「需行动的错误」和「下载完成」各给一次 haptic（§7.5「触觉只传达状态真的变了，不做滚动装饰震」）。
- 无障碍：每条提供 `contentDescription`，例如「提示：已复制可解析链接，3 秒后自动消失」（项目现有 `setContentDescription` 惯例）。

---

## 4. 三个硬问题

### 4.1 键盘 / 悬浮球避让

| 场景 | 处理 | 依据 |
|---|---|---|
| **顶部锚点 vs 键盘** | 不用管。IME 在底部，顶部 `statusBarInset()+8dp` 天然不被遮挡 | `statusBarInset()` `:5415` |
| **右下锚点 vs 键盘** | `bottom = max(navigationBarInset, imeInset) + 12dp`。IME inset 取 `WindowInsets.Type.ime()` | 与 `installSystemNavigationInsets` 同一套 insets API |
| **⚠️ 避让必须瞬时生效，禁止加过渡动画** | 键盘弹起/收起时直接 `setLayoutParams`，**不做位移动画** | `lessons.md` v1.22.8 覆盖结论：**AI 页键盘/insets「禁自研动画」红线**，重犯即 ANR 风险 |
| **右下锚点 vs 悬浮球** | 右下 Pill 与球心矩形做碰撞检测；重叠时 Pill **上移一个自身高度**（不做左右躲，避免和输入区打架） | 球心与安全区已有现成算法：`NavBall.java:61` `AI_SAFE_RATIO = 0.78f`、`:434` 的 `cy = min(cy, ch * AI_SAFE_RATIO - r)` |
| **重排时机** | 复用 `host.addOnLayoutChangeListener` → `reflowNavBall` 的既有回调（`MainActivity.java:1035`），在同一处顺带 `reflowNoticeAnchors()`，不要新开监听 | 同上 |

### 4.2 连续事件合并

现状（DFW-137，`MainActivity.java:5447-5495`）已经很对，**只需补一层"同源合并"**：

| 情形 | 现状 | 建议 |
|---|---|---|
| **同一句正在显示** | 直接忽略，不入队 ✓ | 保持 |
| **不同句，已有一条在显示** | 入队，上限 **2**，超出挤掉最旧 ✓ | 保持（`NOTICE_QUEUE_MAX = 2` 已论证 2×2.5s≈5s 是陈旧度上限） |
| **同一来源的进度型文案**（例：`正在读取 3 个项目简介` → `正在读取 5 个项目简介`） | ❌ 会排队，用户先看到过期的「3」 | **原地更新文案 + 重置计时**，不入队。实现：`showNotice` 增加 `key` 参数（如 `"clipboard"`、`"index"`、`"download:"+entry.id`），同 key 且正在显示 → `label.setText()` 替换 |
| **计数聚合**（`已复制 1 条` → `已复制 3 条`） | ❌ 排队闪两次 | 同 key 原地更新 |
| **下载 / 进度类** | 走 `toastLayer` 独立通道 | **带 key 常驻，永不入队**（进度排队没有意义），且**不参与瞬时提示的队列计数** |

业界同一思路：Sonner 的 `toast(id, ...)` 用 **id 原地更新**；Notistack 的 `preventDuplicate` + `key` 去重。

### 4.3 可滑动关闭

| 类别 | 手势 | 阈值 | 回位 | 说明 |
|---|---|---|---|---|
| 瞬时提示（顶部） | 上 / 左 / 右 | **72dp** | **180ms VPA** | 现状 `NoticeBanner.java:43/42` 已经是这个值，**保持**；跟手用 `set` 直写、松手走有界 VPA（符合 v1.19.8「触摸路径禁物理弹簧」） |
| 瞬时提示 | **向下不关** | — | — | 现状已如此，语义正确（它是从上面下来的），保持 |
| 常驻进度（右下 Pill） | 左 / 右 | **72dp** | 180ms | **滑掉 = 只收起视觉，任务继续跑**，并在收起后于同一位置留一个"进度恢复"小圆点（48dp 触控区），点它重新展开 |
| 需行动的错误 | **禁滑** | — | — | 必须走动作按钮或「知道了」，否则用户会误以为"滑掉=处理完了" |
| 快捷键 | 松手后**重新计时** | — | — | 现状已实现（`NoticeBanner.java:238`），保持 |

---

## 5. 参考项目与出处

| 项目 | 协议 | 用它的哪一点 | 链接 |
|---|---|---|---|
| **Material Design 3 · Snackbar** | 规范 | 底部锚点、**带动作按钮**、body 用 `BodyMedium`、外边距 8dp | https://m3.material.io/components/snackbar/guidelines |
| SNACKBAR（Android 实现） | Apache-2.0 | 官方 token 实测值：圆角 4dp（`mtrl_snackbar_background_corner_radius`）、外边距 8dp（`m3_snackbar_margin`）、水平内距 8dp、垂直 14/16dp、文本 14sp、底色 `colorSurfaceInverse`、文本色 `colorOnSurfaceInverse` | https://github.com/material-components/material-components-android/tree/master/lib/java/com/google/android/material/snackbar |
| **Sonner**（React 原版） | MIT | **默认 4 秒**（Emil Kowalski 自述）、hover 暂停计时、堆叠、`id` 原地更新、水平滑动关闭 | https://sonner.emilkowal.ski/ · https://github.com/emilkowalski/sonner |
| **compose-sonner**（本仓库 vendor 实际在用） | Apache-2.0 | `DurationShort=2000 / DurationDefault=4000 / DurationLong=8000`、`Shape=8dp`、`Elevation=12dp`、`IconSize=20dp`、`contentPadding=16dp`、默认 `Alignment.BottomCenter`（本项目覆写为 `TopCenter`）、`richColors`、`showCloseButton` | https://github.com/dokar3/compose-sonner |
| **Notistack** | MIT | `autoHideDuration` 默认 **5000ms**、`maxSnack` 默认 3（本项目取 2）、`preventDuplicate`、`anchorOrigin` | https://notistack.com/api-reference · https://github.com/iamhosseindhv/notistack |

**关键参照结论**：本项目 AI 侧已经跑着 **compose-sonner**（`io.github.dokar3:sonner:0.4.0`，配置见 `AppRoutes.kt:208-213`：`TopCenter` + `richColors` + `showCloseButton`）。主机区**没必要另创一套语言**——把顶部锚点的圆角/时长/图标尺寸向它靠（20dp 圆角是我们自己的辨识度，20dp 图标与它一致），跨区就自然连续了。**这也正是"不能和软件一模一样，也不能脱离整体风格"的落点**：底色与亮度阶梯用自己的（OLED 真黑 + `SURFACE`），骨架（顶部滑入、20dp 图标、4 秒档）与 AI 侧同源。

---

### 5.1 与 DFW-36 的分工（同目录 `dfw36-失效源识别方案.md`，勿重复打架）

两份文档是**"走哪个通道"与"通道长什么样"的分工**，不重叠：

| DFW-36 的层 | 通道 | 是否走本方案的通知卡 |
|---|---|---|
| L1 源级标记（单源失败） | 源列表条目变灰 + 小标记、状态行变可点 | **否**，就地标记，不弹卡 |
| L2 可点开的清单 | 复用 `retestSelectedSources()` `:3994` 已有的进度面板/对话框 | **否**，是面板不是通知 |
| L3 全局横幅（多域名同时失败） | 「蓝奏整体连不上，不是你的源坏了」 | **是**，走本方案**顶部锚点**，按 §3「需行动的错误」处理：`ERROR_TOKEN` 色条 + 常驻不自动消失 + 1 个主行动（「测速并更换基础链接」） |

即：**DFW-36 决定"什么情况升级成全局提示"，本方案决定"升级后那张卡长什么样"。** 两边数值若有冲突，以本方案 §2 的 token 基线为准。

---

## 6. 落地清单（供实施用，本次未改任何代码）

| 动作 | 文件:行号 | 说明 |
|---|---|---|
| 圆角 18 → 20dp | `MainActivity.java:5552`（`showTopBanner` 内 `solidShape(SURFACE,18)`） | 对齐 §4 Card |
| 圆角 16 → 20dp | `MainActivity.java:5382` `toastPanel()` | 对齐 §4 Card |
| 宽度统一 | `:1038`（250dp）与 `:1174`（286dp）取同一个值 | 修 P3 |
| 去 elevation，改描边 | `:5382`（6dp）、`showTopBanner` 内（10dp） | 修 P6，符合 §5 |
| 错误提示不自动消失 | `showNotice` `:5444` 增加第 3 档时长（`persistent`） | 修 P4 |
| 加 `key` 参数做同源合并 | `:5444`、`:5505` | §4.2 |
| 右下锚点容器 + 悬浮球避让 | 新锚点 + `:1035` 既有 layout 回调内顺带重排 | §4.1 |
| 卡高写死 58/62dp 改 wrap | `:2903`、`:3994` | 修 P8 |
| 补 success / warning 语义色 | `ThemeEngine.java:17`（`Design` 现仅有 `error`） | 修 P5；或按方案规定"成功不铺色"，则此项可不做 |

**Compose 侧（AI 区）一行都不改。** `AppRoutes.kt` 属 vendor 区，任何改动都要登记 `rikkahub/PATCHES.md`；本方案的思路正是**让主机区向 AI 侧已有的 Sonner 靠**，因此不需要动 vendor。
