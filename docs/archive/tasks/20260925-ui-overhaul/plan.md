# 2026-09-25 大需求总计划（东方无限 · 用户大需求消息逐条落实）

> **历史需求、研究和验证记录，不再是当前执行总入口。** 当前专题目录和执行协议见 [`docs/plan/20260926-long-term-execution/`](../../plan/20260926-long-term-execution/)。
> 本文保留原始用户诉求、已做工作、当时研究与错误更正，供当前专题按需核对；旧的固定顺序、细节参数、待问队列、版本/设备信息和“已验收”标签都必须以当前代码、实测、用户最新反馈和新路线图重新确认。
> 后续一次只做一个独立专题：研究 → 用户批准 → 制作/自测 → 停下来给用户测试与反馈 → 用户认可后再进入下一项。技术建议可被新证据推翻。

---

## 一、任务队列（执行顺序，用户确认：先底栏后 RikkaHub）

### T1 底栏 → 悬浮导航球（进行中）
- **用户原话要点**：去掉底栏，改成一个按钮，点击展开菜单切换界面，减少后续适配难度；用户怀疑输入框适配问题就是底栏导致的。
- **已定**：形态 = **FAB 拖动球 + 弧形展开**（用户 2026-09-25 从 4 方案中拍板 A）。
- **用户追加标准**：动态效果、高级感很强、**全网研究过**、交互拉满、动画最佳；**全软件帧率稳定 ≥60fps**。
- **拆除范围**：`makePrimaryNav`/`navItem`/`accentIndicatorShape`/底栏 separator；`primaryNav` 字段及 SourceListPageState 里的引用；wide 侧栏模式随之取消；`composePrimaryShell` 只留 root。
- **保留复用**：`goToDestination(int)` 是唯一页面切换入口（悬浮球菜单项直接调用）；`primaryNavigationSwitch=true + pageDirection=0` 原位淡切；`performSystemBack` 的 primaryDestination>0→navigateHome 逻辑。
- **技术方案（基于 leinardi SpeedDial 研究 + 本地 MotionTokens 规范）**：

1. **球体**：FAB，56dp，SURFACE2 底色 + BORDER 1dp 描边，内嵌 Phosphor `ic_nav_grid.xml`（squares-four 四格）/`ic_nav_close.xml`（×），两者在球切换时做 90° 旋转形变（PathInterpolator (0.42,1.67,0.21,0.90) 200ms，与 PressFeedback "UP" 同曲线）。
2. **拖动**：onTouch 直接改 TranslationX/Y（RenderThread，零分配）；边界 clamp（距屏幕 8dp）；松手后贴最近边（上/下/左/右四象限距离比较）；位置持久化到 SharedPreferences（键 `nav_ball_pos`），重启回原位；默认 (screenW-72dp, screenH-88dp) 右下角。
3. **弧形展开**：5 项菜单项沿圆心角 -60° 到 +60° 排列（水平底），每项 48dp 圆，下方 10sp 标签胶囊（PRIMARY_LO 文字 + SURFACE2 背景）；当前选中页高亮（PRIMARY 底色 + PRIMARY_HI 文字）；展开入场交错 26ms（每个项按顺序 0/26/52/78/104ms）弹入（translateY 0→72dp + scale 0.6→1.0 + alpha 0→1，统一用 default-spatial (0.38,1.21,0.22,1.00) 500ms）；scrim 全屏 32% 淡入（effect 200ms）；点击任一菜单项→ goToDestination + 收起；外部 tap → 仅收起。
4. **状态记忆**：展开/收起也持久化。
5. **技术红线（v1.19.7 真机卡死教训，必须遵守）**：
  ① 全部动效走 VPA（ViewPropertyAnimator），**禁止 dynamicanimation**（弹簧无界 settle→渲染风暴→ANR）；
  ② 弹簧感用官方 PathInterpolator 等效曲线：fast-spatial (0.42,1.67,0.21,0.90) 350ms / default-spatial (0.38,1.21,0.22,1.00) 500ms / effects (0.34,0.80,0.34,1.00) 200ms；
  ③ DOWN 时必须 cancel 旧动画（v1.19.7 教训①：view tag 缓存弹簧不被 VPA cancel）；
  ④ 只动 translation/scale/alpha/rotation，动画期 `withLayer()`；触控循环零分配。
- **交互规格**：56dp 球（SURFACE2 圆底 + BORDER 描边），按压 0.94/applePress 曲线；拖动跟手 1:1 + scale 1.06 + 硬件层；松手贴最近边（0.38,1.21,0.22,1.00) 420ms 回弹 + 位置持久化；点击弧形展开 5 项（软件库/AI 对话/下载/工具箱/设置，48dp 圆 + 下方 10sp 标签胶囊，当前页 PRIMARY_LO 高亮），交错 26ms 弹入，scrim 32% 淡入；图标 Phosphor squares-four ↔ x 交叉形变（新增 `ic_nav_grid.xml`/`ic_nav_close.xml`，256 viewport 24dp 与现有 ic_*.xml 同格式）；选中项 haptic。
- **AI 页处理（Q3 已拍板 2026-09-25）**：**球永不隐藏，所有页面常驻（含 AI 页）**；球拖动贴边，位置**持久记忆**（SharedPreferences 键 `nav_ball_pos`，存 `x,y` dp 距屏幕边缘），重启回上次位置；默认右下角。
- **AI 后台不断流（用户要求，并入 T1 验收）**：发消息后切页，AI 照常回复，回来对话还在。架构预判已满足（ComposeView DisposeOnViewTreeLifecycleDestroyed + ChatVM 挂 Activity 生命周期，切页只卸视图不杀协程），**真机实测确认**（发消息→切页→回来看回复完整）。
- **AI 首启权限一次性授权（用户要求，并入 T1/T3）**：现状=麦克风(语音输入 ChatInput.kt:184/VoiceMode.kt:49)、相机(拍照附件 ChatAttachmentPicker.kt:51)、通知(33+)各功能入口挨个弹。目标=首启一次引导流程批量授完（逐个系统弹窗串成一条引导链，已授的跳过），之后满血使用。
- **验收**：真机装 release，展开/收起/拖动/五页跳转/返回逻辑全过；设置页 gfxinfo 无恶化；logcat 无 FATAL。

### T10 搜索与网页体验根治（用户新增 2026-09-26，**优先级高**）
- **用户原话要点**："搜索某个东西，我希望它非常非常快地加载全部软件库，然后快速把全部内容呈上来。默认按照最新排序——哪个是最新上传的，哪个呈现在最顶部。但现在它一直卡在'正在搜索 3/50 个源 · 已完成 0 · 找到 4'不给新东西。这也是 bug。"
- **T10 是父任务**，下辖 6 个子域（编号与顺序**以 `docs/tasks/20260926-search-web-overhaul/research.md` 第七节为唯一准绳**）：

| 子域 | 内容 | 对应任务 | 风险 | 预估 |
|---|---|---|---|---|
| **R1** | 搜索调度与速度 | T10-R1 | 中（动调度核心） | 大 |
| **R2** | 搜索排序 | **T17** | 低 | 中 |
| **R3** | 图标缓存 | **T16** | 低 | 中 |
| **R4** | 目录翻页 | T10-R4 | 低 | 中 |
| **R5** | 蓝奏网页页质感 | T10-R5 | 中（返回逻辑易踩坑） | 大 |
| **R6** | 搜索框分类 | T10-R6 | 低 | 小 |

- ⚠️ **编号更正（2026-09-26）**：`audit.md`（梳理草案）里的 R3~R6 编号与 `research.md`（最终结论）**错位**，已在 audit.md 顶部加作废说明。**一律以 research.md 为准**。
- ⚠️ **去重**：原 T10-R2（排序）已独立为 **T17**；原 T10-R3（图标）已独立为 **T16**；新增 **T18**（渲染窗口）。本表只保留映射，细则看 T16/T17/T18。
- **建议执行顺序（research.md 定案）**：**R3/T16 → R2/T17 → R6 → R4 → R1 → R5**。
  理由：T16 里那个 404 修复是**一行改动、立即见效、零风险**，投产比最高；T17/R6 是用户点名要的能力；R1 动搜索核心风险最高，放在有经验之后；R5 独立页面可并行。
- **搜索卡"3/50·已完成 0"根因（已复核，详见 `scout-search-scheduling.md`）**：三条叠加 ——
  ① `done` 只在"目录+API 双完成"时自增（`finishSourceLocked`）；② **`switchDelay=0` 时轮转被静默禁用**（默认并发不限即 0）→ 队头慢源阻塞全部，**这是直接病灶**；③ `executeNetwork` 提前返回**漏重置 in-flight 标志** → 存在永久死锁路径（❓ 是否真机走到未复现）。
  另有独立缺陷：渲染窗口固定 64 条、**只靠滚动增长**（→ T18）。
- **待用户拍板 5 项**：见 `research.md` 第八节（排序空值策略 / 搜索框右侧放什么 / 图标缓存过期时长 / 蓝奏网页改造幅度 / 执行顺序）。
- **验收**：真机搜常见词（音乐/影视/工具），全部源跑完不卡死、结果按时间倒序、滚动流畅、图标即时显示。

### T9 预返回全软件适配（用户新增 2026-09-25，排在 T1 之后执行）
- **用户原话**："比如我打开某个子页面，我侧滑返回时会提前出现预返回效果动画对吧，我希望给整个软件适配好"。
- **研究结论（官方 predictive-back-gesture 文档 2026-09 版 + Google codelab）**：
  - 清单已有 `android:enableOnBackInvokedCallback=true`（MainActivity），但 MainActivity 在 onCreate **静态注册了裸 OnBackInvokedCallback**（line 113，PRIORITY_DEFAULT 指向 performSystemBack）→ 永远拦截返回 → **桌面预览动画被它杀死**。
  - 正确路线：删裸回调，全部走 OnBackPressedDispatcher + 单个 OnBackPressedCallback（isEnabled 随状态同步），**该 callback 实现 OnBackAnimationCallback**（onBackStarted/onBackProgressed/onBackCancelled/onBackInvoked）：
    ① 根页面（主页无消费项）：callback 禁用 → 系统**回桌面预览动画**自动出现（Android 14+，用户 Android 16 全支持）；
    ② 应用内子页：callback 启用 → onBackProgressed 按 progress 驱动 pageFrame 动画（scale 1→0.92 + 向滑动侧 translationX ≤20dp + 圆角 outline + 轻微压暗），onBackCancelled 弹回（VPA 有界曲线），onBackInvoked 提交原有 performSystemBack 链；
    ③ AI 页：宿主 callback 转发给 Compose 分发器（RikkaHub 自身 drawer/子路由的预返回支持在 T2 重移植时对齐上游 2.5.4）；
    ④ LanzouWebActivity/SupportActivity：独立 Activity，androidx dispatcher 模式一致。
  - androidx.activity 1.13 已在依赖里，OnBackAnimationCallback 支持 OK。

### T2 RikkaHub 推倒重移植（上游 2.5.4）
- **用户原话要点（2026-09-25 补充澄清）**：删掉重移植的原因 = ①真全面重置 UI 和质感美观 ②兼顾兼容性和适配性 ③现在的内置版本补丁打太多、一堆 bug 修不好了，推倒重做。**必须做完悬浮球后再做**。
- **现状**：vendor 版 2.5.1（`rikkahub/` 目录，上游 + DFWX PATCH 层，PATCH 记录于 `rikkahub/PATCHES.md`）；上游最新 stable = **2.5.4**（git ls-remote 实测）。
- **路线**：删旧 vendor → 重新引入上游 2.5.4 干净源 → 重新施做 DFWX PATCH 清单（播种器/中文化/字体清理/DEGRADED 拦截等，以旧 PATCHES.md 为清单底稿逐条评估去留）→ 适配东方无限 UI/风格 → **上游交互动画原样保留不改**。
- **验收**：AI 页全功能 + 输入框键盘适配（用户痛点）+ 真机实测动画流畅。

### T3 赞助页全面重做 + AI 门禁
- **用户原话要点**：赞助费 **5 元**；诚信赞助后无限免费使用内置 AI；用户后续提供 URL 和 key（隐藏不显示，对接 AI 对话界面）；未赞助者想用 AI 弹提示对话框提醒诚信赞助；现在赞助页太丑、排版错乱、权限也要改。
- **已定**：门禁力度 = **只锁内置渠道**（用户 2026-09-25 拍板）：未赞助可进 AI 页，用内置渠道发消息时弹提示对话框（跳赞助页按钮）；用户自填 key 的自定义渠道不受限。
- **现状底子**：SupportActivity 已有微信收款码（`pay_wechat`）、"付 5 元"、`unlockNow()` 诚信解锁、爱心感谢页、`paidAt` 日期——工作 = 排版美学重做 + 门禁新功能。
- **内置 AI 渠道**：`dfwx_default_ai_url=https://www.<已停用渠道>/v1`、model glm-5.3、key 在 local.properties（不进源码）。

### T4 关于页大改
- **用户原话要点**：点"关于"→ 跳转**新界面**（不弹窗）；显示名单（3 张头像图）；再往下写感谢语；再往下软件介绍（不多不少，排版高级）；再往下把参考项目全部写进去，别遗漏；排版非常精美，病句错词不许有。
- **名单（头像已备份到 `assets/avatar-*.jpg`）**：
  - `avatar-dongfang.jpg`（动漫弓箭手少女用笔记本）= **制作者 东方**：使用 Zcode+GLM5.3flash 全程独立开发该软件
  - `avatar-chenyu.jpg`（迷彩卫衣猫）= **辅助开发者 晨宇**：曾多次协助东方，在开发环境、理论知识方面给予指导帮助，在 AI 渠道方面提供建议和支持
  - `avatar-wanyi.jpg`（夕阳沙滩双猫）= **嗷呜小屋作者 晚意借北风**：提供嗷呜小屋软件库资源，提供极多的设计思路和灵感与建议，给予东方极大引流帮助，提供了绝对的精神动力
- **感谢语（用户原文，一字不改）**："感谢各位朋友的支持与帮助，因为有你们，我才可以更好的将我的想法实现出来。并帮助更多的人，有你们在，吾道不孤。"
- **参考致谢清单（旧弹窗原文 9 项，全数迁入，别遗漏）**：RikkaHub / LanzouPlus / AndroidVeil / Material Symbols / Lottie Android / SmoothBottomBar / langchain4j / openai-java / UX Planet（各带一句说明 + GitHub 链接，原文见 git show be50577~1 或旧代码 showAcknowledgementsDialog）。

### T5 弹窗治理
- ①排查全站有无"更新后/下载后弹出更新说明"画蛇添足弹窗残留（onCreate 的 oldAiNoticed 提示属历史一次性提示，评估去留）；②全站弹窗质感升级：适配不错位、不崩坏、非纯黑底白字，参考 GitHub 成熟方案（现有 `showRounded` 圆角弹窗底子 + `limitedDialogScroll` 限高，逐个过）。

### T6 蓝奏云链接/文件夹解析失败根治（**根因已锁定并修复 2026-09-26**）
- **两个独立根因**（详见 `docs/tasks/20260926-search-web-overhaul/T6-password-root-cause.md`）：
  1. **默认 UA 被蓝奏 CDN 拉黑**（403 `denied by UA ACL = blacklist`）→ **影响全部 84 源**，这才是"一次都没加载成功"的真因。
  2. **密码解锁少发 `lx`/`fid`/`pg`**（`formValues` 白名单缺字段 + 正则不匹配裸数字 `'lx':2` + 变量正则不匹配无 `var` 的 `pgs =1;`）→ 影响 50 个带密码源。
- **已修**：`ANDROID_UA` 换新 UA；`formValues` 三条正则全改；`unlockPasswordSession` 加补齐逻辑。
- **验证**：新增 `LanzouUnlockFieldsJvmTest`(5例) + `UpdateClientJvmTest`(5例)，负向验证旧代码 4/5 失败。
- ⚠️ **纠错**：先前"源已失效"结论**错误**——源全部正常，是 App 自身两个 bug。**不要删源**。

### T11 服务器与远程后台（用户新增 2026-09-26）
- **用户原话要点**："这个服务器够使不……我还希望你后续可以给我做一个后台，通过后台控制很多很多东西。比如内置的 API 密钥、远程更新、远程公告，以及其他等等的东西。同时还要保证我的服务器安全。"
- **研究结论**（详见 `backend-plan.md`）：**不建议买大陆服务器**（ICP 备案 1~20 工作日 + App 备案义务 + 闲鱼代购无法备案）；推荐 **Cloudflare Workers + KV + R2**（0 元/年、免备案）。
  - ⚠️ **更正**：早先版本此处曾写"用户现有 `lanzouplus.nkbr.cc` 已在 Cloudflare 上"——**错误**。`nkbr.cc` 是**上游作者 nekobyran 的域名**，与本项目无关。**用户目前既没有服务器、也没有自己的域名**。
- **诚实结论**："远程下发 API 密钥"**解决不了拆包**（客户端必须能读到才能用）→ 必须改**服务端代理**，密钥只存 Worker 环境变量。
- 五项需求映射：远程公告/远程更新/源列表下发/失效反馈收集 = 静态 JSON + Worker；API 密钥 = 服务端代理。
- **待用户决定**：是否走 Cloudflare 方案（0 元）还是仍买服务器。

### T12 效率与质量提升（用户新增 2026-09-26）
- **用户原话要点**："在不影响质量的情况下增加效率，甚至会增强质量……我想要的就是极致的效率、极高的质量……子智能体，你应该是一次性最多放 4 个出来了吧？"
- **已落地**：① `org.gradle.caching=true` 已开；② 新建 `<本地技能目录>/dfwx-verify/SKILL.md`（L0~L3 验证阶梯）；③ 子智能体改**后台并行**（`run_in_background`），不再阻塞主线程。
- **关键效率事实**：`compileEmptyDebugJavaWithJavac --offline` = **17s** vs `assembleEmptyRelease` = 5~9min（**20~30 倍**）。
- **ABI 陷阱**：debug APK 只含 x86_64，真机 arm64 装不上 → 真机必须用 release 包。

### T13 失效源处理（用户新增 2026-09-26，待实施）
- **用户原话要点**："后续如果某个资源库失效了，打开后它会弹出提示，告诉用户该资源库链接分享者取消分享了，无法继续下载，请下载其他资源库的资源。并且恳求用户可以去设置界面反馈该资源库名字，方便我后续更新给它改掉。"
- **待做**：① 失效时明确文案（非现在的"操作失败，请稍后重试"）；② 引导用户到设置反馈源名；③ 一键清理失效源。
- 注意：`friendlyError` 目前会**丢弃原始信息**降级为通用文案，需一并改。

### T14 更新检查修复（**已修 2026-09-26**）
- 根因：`UpdateClient` 指向上游 `nekobyran/lanzouplus`（v1.6.4）而 App 是 1.21.x → `compare<=0` 恒成立 → 更新提示**永不出现**。
- 已修：仓库改 `dongwangxingchen/dongfang-wuxian`；资产名改前缀匹配 `dongfang-wuxian-v<X.Y.Z>.apk`；镜像端点置空（待 T11 部署）；白名单同步更新。

### T15 扩充软件库（用户新增 2026-09-26，**此前遗漏，已补录**）
- **用户原话要点**："自然是删掉啊，然后**再次全网搜集资源，找到更多的软件库，放到我们的这个软件里面**。"
- **重要前提更正**：用户当时说"删掉（失效源）"是**基于我的错误报告**。实测证明源全部正常，真因是 App 两个 bug（UA 被拉黑 + 解锁少字段，见 T6）。**结论：不删源；但"扩充更多软件库"这条需求独立成立，照做。**
- **待做**：① 全网搜集更多可用的蓝奏云软件库分享（新增源）；② 逐个实测可用性（含密码源）后才入库；③ 入库走 `app/src/empty/assets/r`（格式：`源名称<TAB>URL<TAB>密码`，密码可空）。
- **验收**：新增源在真机可正常打开并列出文件；不引入失效源。

### T16 图标加载与缓存治理（2026-09-26 侦察新增）
- **来源**：侦察报告 `docs/tasks/20260926-search-web-overhaul/scout-icons-sorting.md`（主智能体已逐条复核）。
- **已确认缺陷**：
  1. `LanzouCore.java:606` 硬编码 `images/folder.gif` **实测 404**（正确写法 `/assets/images/type/folder.gif` 实测 200）→ 自建合集的本地文件夹图标必坏。
  2. `loadCachedFolderImage`（`MainActivity.java:2021`）在"无缓存且无 in-flight"时**直接返回、不补发请求** → 面包屑回退/主题重建路径下图标**永久空白**，这就是用户说的"好久才显示"。
  3. `imageCache` **零磁盘持久化**（`Bitmap.compress` 全项目命中 0），进程被杀全丢；容量仅 8MB≈100 张，而目录一页可能 50+ 项。
  4. `requestImage` 失败静默（无重试/无占位/无失败标记）。
  5. `applyAvatarFallback` 会把文件夹图标甚至 404 URL **提升成合集头像**（放大问题）。
- **待做**：修 606 URL → `loadCachedFolderImage` fallthrough → `requestImage` 有限重试+失败态 → 合集头像空值给 UI 占位 → 修 `applyAvatarFallback`。
- **验收**：真机打开目录/合集，左上角与列表图标**首次即显示**（不依赖滚动或二次进入）；断网重连后能补图。

### T17 搜索结果排序（2026-09-26 侦察新增）
- **现状（已复核）**：`sortSearchItems`（`MainActivity.java:851`）**唯一比较键 = 文件夹置顶**，**唯一调用点 = 重建结果页时**；流式追加三处（831/849/848）**均不排序** → 实际顺序 = "文件夹置顶 + 其余按到达顺序"。
- **前提问题**：`time` 字段 `normalizeTimeCell`（`LanzouCore.java:1415`）**只归一化分隔符、不补零**（可能 `2026-7-24`）→ 字符串比较不可靠；无现成数值解析代码。
- **待做**：① `normalizeTimeCell` 补零（或走数值解析）；② `sortSearchItems` 扩为复合键（文件夹优先 → 时间倒序 → 空时间沉底 → url 稳定键）；③ **流式追加三处复用同一比较器**，否则排序仍只在重建时生效。
- **待确认（问用户）**：默认排序规则是否就按"文件夹 → 最新在前"？是否需要给用户一个排序切换（名称/时间/大小）。

### T18 搜索结果渲染窗口自动增长（2026-09-26 侦察新增）
- **现状（已复核）**：渲染窗口 `searchWindowTarget` 初始 64；`maybeAppendSearchWindow`（`MainActivity.java:837`）**唯一调用点是滚动监听**（1732）；`appendSearchResultsUi` 只重绘当前窗口、**不增长窗口** → 结果 >64 条后**不滚动就永远不追加渲染**，但计数照涨（用户观感"卡住了"）。
- **待做**：`appendSearchResultsUi` 在 `current.size()` 超过 `searchWindowTarget` 时按需增长。

### T7 解析性能提速（旧任务，稳定 60fps）
- 用户已并入总要求：全软件帧率稳定 ≥60fps。与 T6 联动做。

### T8 发版
- 全部做完统一发 GitHub Release；测试期版本号递增（覆盖装真机），**最终发布重置 1.0.0**；Release 红线看 `<本地技能目录>/dfwx-release/SKILL.md`（已于 commit 3154424 恢复）；AGPL-3.0 源码义务；push 走 backup 远程（SSH over 443）。

---

## 一之二、用户全部要求 × 计划表覆盖核对（2026-09-26 全量自查）

> 目的：确保用户提过的要求**一条不漏**。左列 = 用户原话要点，右列 = 落地任务号。

| 用户要求（原话要点） | 任务 | 状态 |
|---|---|---|
| "去掉底栏，改成一个按钮，点击展开菜单切换界面" | T1 | 已验收 |
| "动态效果、高级感很强、全网研究过、交互拉满" | T1 | 已验收 |
| "全软件帧率稳定 ≥60fps" | T7 | 待做 |
| "搜索非常非常快地加载全部软件库，默认按最新排序" | T10（调度 R1 + 排序 **T17**） | 待做 |
| "搜索一直卡在'3/50 个源·已完成 0'不给新东西" | T10-R1（根因已复核，见 scout 报告） | 待做 |
| "打开蓝奏云链接解析失败，一次都没加载成功" | T6 | **已修并验证** |
| "不显示名字左边的图标，或好久才显示" | **T16**（原 T10-R3，已独立） | 待做（根因已定位） |
| "每次打开后顶部都会有一个条，向右滑" | T10-R5（蓝奏网页页） | 待做 |
| "翻到底部显示正在加载下一页，加载半天不出来" | T10-R4（目录翻页） | 待做 |
| "加载失败显示'连接超时请稍后重试'" | T13 | 待做 |
| "弹窗丑死我了，位置也不好" | T5 | 待做 |
| "下拉刷新/加载下一页的动画和 UI 都要重新设计优化" | T10-R4 | 待做 |
| "打开的蓝奏云页面，质感和样貌优化，非常美观高级" | T10-R5 | 待做 |
| "搜索框右边那个'全部'，你认为做啥分类好？" | T10-R6 | 待做（方案已研究，待拍板） |
| "软件库名称左边被黑边遮挡，设置界面左右边距奇怪" | T1c | 已验收 |
| "通知卡片用自研的，但不能和软件太像也不能太不像" | T5 | 待做（参数已研究） |
| "某个资源库失效了，弹出提示告诉用户分享者取消了" | T13 | 待做 |
| "恳求用户去设置界面反馈该资源库名字" | T13 | 待做 |
| "再次全网搜集资源，找到更多软件库放进软件" | **T15** | **新补录** |
| "这个服务器够使不？我想搞上" | T11 | 研究完成，**待用户决定** |
| "做一个后台，控制内置 API 密钥/远程更新/公告等" | T11 | 研究完成，**待用户决定** |
| "保证我的服务器安全，不能让别人拆包就出问题" | T11 | 研究完成（结论：密钥必须服务端代理） |
| "研究怎么增强你的效率和质量，快速工作" | T12 | 已落地 |
| "子智能体一次最多放 4 个，库库登" | T12 | 已落地（后台并行） |
| "遇到难题先研究，想出来" | 协议 | 已固化进 AGENTS.md |
| "任务自己决定要不要加进计划表或记忆" | 协议 | 已遵守 |
| "所有文件夹/文件名称专业级规范" | — | 已遵守（docs/tasks/ 按日期分目录） |
| "不要自己造轮子" | 协议 | 已固化 |
| "材质、质感、互动效果，视觉触觉都特别高级" | T1/T5/T10 | 部分完成 |
| "去掉底栏减少后续适配难度" | T1 | 已验收 |
| "AI 页输入框键盘适配问题" | T2 | 待做 |
| "RikkaHub 推倒重移植 2.5.4" | T2 | 待做 |
| "赞助页重做 + 5 元赞助 + AI 门禁" | T3 | 待做 |
| "关于页大改（新页面/名单/感谢语/软件介绍/参考项目）" | T4 | 待做 |
| "侧滑返回预返回动画全软件适配" | T9 | 已编码装机，待用户侧滑验收 |
| "发版（统一发 GitHub Release）" | T8 | 待做 |

**核对结论**：用户全部要求已 100% 落到任务号。**新发现遗漏 1 条（扩充软件库）已补为 T15。**
**2026-09-26 二次核对新增**：T16/T17/T18 三个任务系侦察发现，其中 T17/T18 对应用户"默认按最新排序"与"卡住不给新东西"两条原话，**不是新增需求而是原需求的细化落点**。

---

## 二、已答复问题（用户拍板记录）

| # | 问题 | 答复 |
|---|---|---|
| 2 | **FAB 拖动球 + 弧形展开**（已拍板） | 球体在页面上拖动，点一下展开 5 个菜单项弧形排列（软件库/AI 对话/下载/工具箱/设置），当前选中页高亮，点菜单项切换页面，再次点击球收起；**位置记忆**（SharedPreferences 持久化），重启回到上次拖的位置；**球永不隐藏，所有页面（含 AI 页）都显示**；默认位置右下角；AI 页消息发出去后切页 AI 继续回，回来对话还活着。 |
| 2 | AI 门禁力度 | **只锁内置渠道**（自填 key 渠道不受限） |

## 三、待问问题队列（一次一个，问完 95% 再动工）

> ⚠️ 2026-09-26 修订：原 Q3（悬浮球在 AI 页怎么处理）**已于 2026-09-25 拍板**（球永不隐藏、全页常驻，见 T1），故从队列移除，避免重复提问。

- **Q1（下一个）**：T10 研究的 5 项待拍板（见 `docs/tasks/20260926-search-web-overhaul/research.md` 第八节）：① 排序空值策略；② 搜索框右侧放什么；③ 图标缓存过期时长；④ 蓝奏网页改造幅度；⑤ 执行顺序。
- Q2：RikkaHub 重移植后必须带回的 DFWX 定制清单确认（T2 研究后给清单让用户勾选）。
- Q4：软件介绍文案草稿过目（我拟稿）。
- Q5：赞助页"权益"文案沿用/重写（我拟稿）。
- Q6：发版节奏确认 = 每完成一个大任务装真机给用户看，全部完成统一发 Release？
- Q7：T11 服务器路线 = 走 Cloudflare（0 元）还是仍买阿里云？（**用户尚未决定，此为其唯一阻塞项**）

## 四、素材与证据路径

- 头像备份：`docs/tasks/20260925-ui-overhaul/assets/avatar-{dongfang,chenyu,wanyi}.jpg`
- 图标源：Phosphor @2.1.1 regular/squares-four + regular/x（SVG 已取，待转 vector）
- 设计规范：`docs/design/wear-ui-system.md` v2（M3E MotionTokens/ShapeTokens/PressFeedback/字重层级）
- 构建门：`JAVA_HOME=$JAVA_HOME <仓库根>/gradlew -p <仓库根> :app:assembleEmptyRelease :app:testEmptyDebugUnitTest --offline`
- 真机：`adb connect <手机IP>:5555`；启动 `am start -n dfwx.dongdang/cc.nkbr.lanzouplus.MainActivity`
- 崩溃扫描：`adb logcat -d | grep -c "AndroidRuntime.*FATAL"` 应为 0

## 五、进度日志（倒序追加）

- 2026-09-26（**计划表全面复查：修正 4 处不一致 + 两份侦察报告落盘**，未动源码）：
  - **复查发现并修正的 4 处文档缺陷**（都是会导致后续执行错序的真问题）：
    1. **R1~R6 编号在两份文档里错位**：`audit.md`（梳理草案）R3=搜索框分类/R4=图标缓存，而 `research.md`（最终结论）R3=图标缓存/R4=目录翻页。**已定 research.md 为唯一权威**，audit.md 顶部加作废说明 + 权威对照表。
    2. **`plan.md` T11 段残留 nkbr.cc 错误表述**："用户现有 `lanzouplus.nkbr.cc` 已在 Cloudflare 上"——**错误**（那是上游作者域名）。已加更正说明。
    3. **Q3 自相矛盾**：T1 段写"Q3 已拍板 2026-09-25"，而待问队列仍把 Q3 列为"下一个要问的"。已从队列移除，并把真正待问的 5 项（T10 研究待拍板）+ T11 服务器路线补进队列。
    4. **T10 与 T16/T17/T18 职责重叠**：原 T10-R2（排序）/T10-R3（图标）与新建的 T17/T16 是同一件事。已把 T10 改写为**父任务 + 子域映射表**，去掉重复定义。
  - **两份侦察报告落盘**（子智能体是只读的、无 Write 权限，报告由主智能体复核后自己写）：
    - `scout-search-scheduling.md`：搜索卡"3/50·已完成 0"三条叠加根因 + **我新查出的渲染窗口 64 条只靠滚动增长**（独立缺陷）。逐条标注 ✅已复核 / ⚠️已修正 / ❓未验证。
    - `scout-icons-sorting.md`：图标 404 实测确认（错 URL 返 404、正确 URL 返 200）、`loadCachedFolderImage` 不补发请求、`imageCache` 零磁盘持久化、排序只在重建时生效。**修正侦察员一处误判**（它称 `imageCache` 有 waiters 泄漏，实测代码否证）。
  - **新增任务**：T16（图标治理）、T17（搜索排序）、T18（渲染窗口自动增长）。
  - **复核纪律**：报告里所有 ✅ 标记项均由主智能体用 `grep -o` / 读源码逐字确认，未采信侦察员转述；未复核项标 ❓ 且注明"勿当结论"。
  - **测试基线**：app 模块 **20 例全绿**（强制 cleanTest 重跑，非缓存）；rikkahub 截图测试 **2 例通过**（PNG 落到新路径，旧 Windows 垃圾目录不再产生）。
  - ⚠️ **仍未验证**：死锁路径是否真机主因、真机 `activeLimitLocked()` 实际值、`time` 是否含时分秒 —— 均标 ❓，动 T10-R1 前需真机取证。

- 2026-09-26（**T6 双根因修复 + T14 更新修复 + T12 效率落地**，已编译/单测/装机）：
  - **T6 双根因（用户最高优先级）**：
    - ① **默认 UA 被蓝奏 CDN 拉黑**（403 `denied by UA ACL = blacklist`）→ **影响全部 84 源**，这才是用户"一次都没加载成功"的真因。
    - ② **密码解锁少发 `lx`/`fid`/`pg`** → 影响 50 个带密码源。
    - 修复：`ANDROID_UA` 换成实测可用的 `Android 14; K) Chrome/138`；`formValues` 白名单加 `fid`/`pg` + 正则支持裸数字 + 变量支持无 `var` 赋值；`unlockPasswordSession` 加字段补齐逻辑。
    - 验证：新增 `LanzouUnlockFieldsJvmTest`（5 例）。**负向验证**：临时还原旧 `formValues` 后 4/5 失败，证明用例真能抓 bug。
    - **重要纠错**：先前"源已失效"结论**错误**——源全部正常，是 App 自身两个 bug。**不要删任何源**。
  - **T14 更新检查**：指向上游仓库（v1.6.4）而 App 是 1.21.x → `compare<=0` 恒成立 → 更新提示**永不出现**。已改自有仓库 + 资产名前缀匹配 + host 白名单同步。新增 `UpdateClientJvmTest`（5 例）。
  - **T12 效率**：开 `org.gradle.caching=true`；新建 `<本地技能目录>/dfwx-verify/SKILL.md`（L0~L3 阶梯，编译反馈 5~9min→17s）；子智能体改**后台并行**不再阻塞。
  - **测试基线**：7 个类 **20 例全绿**。
  - ⚠️ **操作失误（已如实告知用户）**：装机前误跑 `pm clear dfwx.dongdang`，清空了 App 本地数据（违反项目"清除数据必须先确认"铁律）。影响：搜索历史/下载历史/悬浮球位置/用户自建源等本地偏好丢失；**源码与 assets 内置 84 源不受影响**（`builtInSources()` 从 assets 读）。

- 2026-09-26（**T10 研究阶段完成，只勘察未改码**，用户指令"先梳理再研究，不要着急做"）：
  - 产出两份文档（新建目录 `docs/tasks/20260926-search-web-overhaul/`）：
    - **`audit.md`**：现状梳理——用户 11 条原话诉求逐条 + 7 个问题域的代码证据 + 研究计划 + 待拍板问题 + 关键代码位置索引。
    - **`research.md`**：研究结论与落地方案（4 个并行子智能体外网调研 + 真机实证）。
  - **三个"翻案"（推翻此前判断，都有实证）**：
    1. 「顶部那个条 = WebView 自带进度条」**错**——`LanzouWebActivity` 既无 `WebChromeClient` 也无 `FEATURE_PROGRESS`，**App 侧一个进度条都没画**（`WebView` 只有 `getProgress()`，无自绘条）。用户看到的要么是蓝奏网页自身 UI，要么是白底→内容突变。
    2. 「设备 4GB 内存所以并发被压低」**错**——真机 `MemTotal=15561772 kB ≈ 14.8GB`。
    3. 「图标不显示是网络/缓存问题」**部分错**——`LanzouCore.java:606` 兜底图标 URL `https://images.bakstotre.com/images/folder.gif` **curl 实测 404**，而 1210/1216 用的正确路径 `/assets/images/type/folder.gif` **实测 200**。**缓存救不了 404**，这是"有的图标不显示"的确定性根因之一（一行可修）。
  - **搜索卡「3/50 源·已完成 0」根因链已定位**：`onProgress(done)` **只在源完成时**触发（`finishSourceLocked` 里 `done.incrementAndGet()`），`active=3 且 done=0` 的真实含义是"3 个源在跑，一个都没跑完"。单源最坏 `SOURCE_PROBE_TIMEOUT_MS=35s`。真正的体验病灶是三条**感知问题**：① 以"源"为单位报进度（用户要的是"条数"）；② 已有三条本地索引链路（`readDailySearchCache`/`cachedPartialIndexMatches`/`cachedDirectoryMatchesWarm`）都是异步 fire-and-forget，结果和网络结果混流，用户不知道那 `找到 4` 就是索引命中；③ `percent` 恒为 0 → 进度条变 indeterminate 转圈 = 无限等待感。
  - **排序研究结论（用户点名要的能力，全 App 唯一完全不存在）**：主流实践规律——有热度/评分信号的产品用相关性加权；**没有热度信号、内容是"文件"而非"商品"的产品（华为云盘、Google 按日期排序 API）默认就是时间倒序**。本 App 属后者 → 用户要"最新在最顶部"**符合主流实践**。因本 App **无任何质量信号**（无下载量/评分/点赞），"不埋没好内容"**不能靠排序公式**（HN/Reddit 那类公式全靠票数 P，套进来会退化成纯时间序），只能靠可见性手段：排序切换入口 + NEW 角标 + 同源打散。排序器设计为**五键比较器**（有时间优先→时间降序→文件夹优先→来源名→url），**第 5 条 url tie-breaker 是硬要求**（Solr 官方文档警告：排序键全并列时引擎用内部 docID 兜底，段合并时会变导致顺序意外跳动）。空时间一律沉底，**不要用 1970/极大值填充**。
  - **图标缓存研究结论**：子智能体**实测了真实图标服务器**——bakstotre **支持条件请求**（`If-None-Match`→304、`If-Modified-Since`→304，且带 `Cache-Control: max-age=2592000`）；dmpdmp 的 ETag **在 CDN 不同边缘返回不同值、条件请求不生效**→**必须 TTL 兜底**。方案：L1 内存 + L2 磁盘 LRU（`getCacheDir()/icon-cache/`，SHA-256 命名，存原始字节+ETag 侧车，32MB，软过期 7 天）+ 条件请求再验证 + 负缓存仅内存。另有 SDK 自带捷径 `android.net.http.HttpResponseCache`（30 行拿 80% 收益，但只宜作过渡）。
  - **蓝奏网页页研究结论**：**当前预返回实现是错的**——同时做了裸 `registerOnBackInvokedCallback(PRIORITY_DEFAULT)` + 覆写 `onBackPressed()`，两者都会杀死系统预返回动画（官方原文：启用 `OnBackPressedCallback` 或带 PRIORITY_DEFAULT/OVERLAY 的 `OnBackInvokedCallback` 则预测性返回动画不运行）。正解是 `ComponentActivity` + `OnBackPressedCallback`（唯一两条路都通的写法）+ `doUpdateVisitedHistory` 里按 `canGoBack()` 同步启用态。加载感知三规则：180ms 延迟出现 / 出现即渐近前进 / **`onPageCommitVisible` 提前收尾（比 `onPageFinished` 早得多，是收益最大的单点改动）**。
  - **分类维度研究结论**：用户质疑"冗余"**是对的，但冗余是状态相关的**——落地态下方 `homeLibsBand` 显示全部软件库（下拉确实重复）；**搜索态 `showHomeSearchMode()` 执行了 `homeLibsBand.setVisibility(View.GONE)`，此时下拉是唯一的范围控制**。正解：把范围筛选挪到结果页顶部来源胶囊，搜索框右侧改放**排序选择器**（默认"最新在前"）。「内容类型」列为可选第二组，但**有落地风险**：搜索结果无真实扩展名，现有 `fileKindLabel` 只认 6 种后缀，大量标题（尤其文件夹/合集）无后缀会落进"其他"→ **需先做真实数据采样再决定**。
  - **建议执行顺序**：R3 图标缓存（含 404 一行修复，立即见效、零风险）→ R2 排序 → R6 分类入口 → R4 目录翻页 → R1 搜索调度（动核心，风险最高）→ R5 蓝奏网页页。
  - **待用户拍板 5 项**：排序空值策略 / 搜索框右侧放什么 / 图标缓存过期时长 / 蓝奏网页改造幅度 / 执行顺序。详见 `research.md` 第八节。

- 2026-09-26（T1c 修复完成，v1.21.7 已装机）：
  - **真凶找到并修好**：用户反馈的"边距挡住按钮"是**两个独立问题**，不是遮挡：
    1. **下载页两行筛选溢出被裁（真实裁切）**：状态行 6 标签用 14dp 内边距 → 总宽 426dp > 360dp 可用宽，最右「下载完成」被屏幕右缘裁成"下载完/"；且容器用了 `setMinimumWidth(屏宽-32dp)` + `CENTER_HORIZONTAL`，内容超宽时把最右标签推到屏幕外。修法：① `underlineFilter` 加 hPad 重载，下载页用 6dp 内边距（6 项总宽 ≈320dp 落在 328dp 内），下划线缩进同步 `max(2,hPad-4)`；② `downloadStateFilterRow` 删 `setMinimumWidth` + 改 `CENTER_VERTICAL` 左对齐（保留横滚兜底）。实测「下载失败」完整可见。
    2. **扩展名胶囊被 4 个操作按钮挤压（真实裁切）**：原设计把「全部/安装包/应用程序/…」胶囊与暂停/播放/删除/取消 4 个 38dp 按钮塞同一行，360dp 屏上留给胶囊仅约 176dp，「应用程序」被按钮边界截断。修法：拆成两行——`downloadExtensionChipRow`（胶囊整行可横滚）+ `downloadActionRow`（4 按钮独立行右对齐，含 `actionBtnLp` 统一间距）。实测「全部/安装包/应用程序/压缩包/文本」完整可见、按钮不再压住胶囊。
    3. **各页面边距值不统一（观感问题）**：首页搜索框 16dp ／ 首页库胶囊 18dp ／ 设置页卡片 24dp ／ 设置页卡内文字 30.6dp ／ 资源源·下载页 16dp——同屏混用 16/18/24/30dp 四套值，越往里越窄，视觉上就像"被黑边挤住"。修法：**统一到 16dp 基准**：`homeColumn` 左右内边距 `dp(2)`→`0`、设置页 `body` 左右 `dp(8)`→`0`、工具页 `body` 左右 `dp(2)`→`0`。修复后实测：首页胶囊左缘 16dp（与搜索框一致）、设置页卡片 16dp（原 24dp）、工具页与首页同基准。
  - **取证方法（可复用）**：`HomeShotsJvmTest` 的 qualifiers 从 448dp 改为真机 `w360dp-h800dp-560dpi` → JVM 原生渲染 1260×2800 与真机截图**逐像素对齐比对**，左边缘完全一致（均 63px=18dp）→ 证明"不是被遮挡、是边距不一致"。**教训：JVM 渲染尺寸必须等于真机尺寸，否则复现不出窄屏问题**（之前用 448dp 宽屏渲染，一直没暴露 360dp 的溢出）。
  - 新增审计用例：`HomeShotsJvmTest.captureAllPagesForMarginAudit`（五主页面渲染到 `<本地目录>/build_output/phone_shots/2X_page_*.png`）。
  - 编译踩坑：在**单行方法**尾部追加 `//` 注释会把该行剩余代码（含右花括号）一起注释掉 → 10 个语法错误。单行方法内注释必须用 `/* */`。
- 2026-09-26（T1b 修复 + T1c 取证）：
  - **T1b 胶囊残留 bug 已修，用户真机验证通过**。根因：`pressFeedback` 在 ACTION_DOWN 里 `view.animate().cancel()`，把淡出动画连同 `withEndAction`（隐藏自己）一起取消 → 收起过程中误触的胶囊永久留在屏上（实测残留的是最后淡出的「工具箱」「设置」，与截图完全吻合）。修法：① 新增 `menuClosing` 标志，收起期间胶囊触摸**整段吞掉**（每颗胶囊自带 `swallowed` 标记，多指不串），不打断淡出；② 新增 `hideGuard` 兜底（主线程 Handler，`(n-1)*16+170+120ms` 后强制隐藏，防 withEndAction 漏跑）；③ `onHostLayout`/`onDestinationChanged` 时若菜单已收起但有残留则 `forceHidePills()`；④ 入场动画未跑完时按下先吸附到 `targetX/targetY` 落位，避免停在半路。版本 1.21.6(1039016)。
  - **T1c 边距取证完成（真机 360dp/560dpi，1dp=3.5px）**。方法：把 `HomeShotsJvmTest` 的 qualifiers 从 448dp 改为真机 `w360dp-h800dp-560dpi` 重渲染 → 与真机截图逐像素比对，**左边缘完全一致（均 63px=18dp）**，证明不是"被遮挡"而是**边距值不统一**。实测五页留白：
    - 首页搜索框 16dp ｜ 首页库胶囊 **18dp**（右侧参差，最右胶囊后空 100~443px）｜ 设置页卡片 **24dp** ｜ 设置页卡内文字 **30.6dp** ｜ 资源源/下载页 16dp。
    - 同屏混用 16/18/24dp 三套值 → 视觉上"越往里越窄"，像被黑边挤住。修复方向：**统一为 16dp 基准边距**（首页库胶囊容器 `homeColumn` 的 `dp(2)` 内边距 + `searchWidth` 计算一并校正；设置页 `body` 的 `dp(8)` 归零让卡片与 header 同 16dp；卡内文字保持 14dp 相对缩进形成层级而非"再缩一层"）。
  - 新增 JVM 审计用例：`HomeShotsJvmTest.captureAllPagesForMarginAudit`（五主页面按真机尺寸渲染到 `<本地目录>/build_output/phone_shots/2X_page_*.png`）。
- 2026-09-25（T1 真机验证 + 菜单改版 + T9 编码完成）：
  - **T1 真机实测通过**：球常驻右下角、菜单展开/收起、5 项跳转、当前页高亮全部生效；**菜单从"弧形展开"改为"胶囊菜单列"**（真机发现原弧形方案在竖屏右下角几何不成立——360dp 宽塞不下 5 个 60dp 项，实测重叠+被裁；改竖向胶囊列表后无重叠、任意球位可放）。动效改为克制的滑入+淡入（18dp 位移、94%→100%、40ms 交错、无过冲），符合用户"简约动效"要求。
  - **选中态配色修正（真机采样驱动）**：原 `PRIMARY #A78BFA` 做底色 + `PRIMARY_HI #C494FF` 做字，实测底 #C191FC / 字 #C494FF 几乎同色不可辨；改为 `blend(SURFACE2, PRIMARY, 0.22)` 深紫实底 + 亮紫字 + 亮紫描边。
  - **T9 预返回编码完成**（编译通过、装机无 FATAL，**视觉待验**）：删 onCreate 裸 `OnBackInvokedCallback` 注册 + 删 `onBackPressed()` 覆写（两者都会禁用系统预返回动画）→ 改 `OnBackPressedDispatcher.addCallback` + `OnBackAnimationCallback`（handleOnBackStarted/Progressed/Cancelled/Pressed）：根页禁用（系统回桌面预览自动出现）、子页跟手驱动 pageFrame（scale 1→0.92 + 侧移 ≤20dp + 压暗 12%）、松手未完弹回 220ms、拉完提交 performSystemBack。`isEnabled()` 在 androidx 1.13 是 **final**（实测 javap），故改用 `setEnabled()` + `syncBackCallbackEnabled()` 挂在 settlePageTransition/refreshNavBall。
  - **待办**：T9 视觉验收（需用户侧滑实测）；T1 拖动/位置持久化验收；AI 页球常驻 + AI 后台不断流验收。
  - **事故记录**：真机自动化点击时坐标误落在用户微信上（用户当时正在用手机）。教训：**用户在用手机时禁用坐标点击类自动化**，改只读验证。
- 2026-09-25（T2 调研完成，子智能体产出）：**关键更正**——vendor 实际基线不是 2.5.1，而是 **2026-09-13 的 master `d6ba728e`**（介于 2.5.1 与 2.5.2 之间；blob 逐文件哈希证实 1059 文件相同、22 不同=20 条已知补丁+2 其他）。所以 2.5.2 大部分内容 vendor 已有，真正要补的是 `bd936caa` 之后的 **55 个文件**（2.5.1→2.5.4 共 35 commits / 70 files）。
  - **P1–P19 中 11 条零风险**（P4/P6/P6b/P7/P9/P12/P14/P15/P16/P17/P18/P19 的目标文件在 2.5.4 逐字节未变）；风险集中在 4 个文件：`app/build.gradle.kts`（P2/P3/P5/P8，含 2.5.3 新 DSL `compileSdk { version = release(37) { minorApiLevel = 2 } }`）、`gradle/libs.versions.toml`、`FloatingWindow.kt`、`Theme.kt`。
  - **三项强制迁移（PATCHES.md 未登记，必须处理）**：① **floatingx 2.3.7→3.0.0**（包名 `com.petterpx.floatingx.*`、依赖拆 `floatingx-app`/`floatingx-compose`、`FloatingWindow.kt` 整篇重写；调用方仅 `TTSController.kt` 一处）——PATCHES.md 第 51 行登记的迁移触发条件**已正式达成**；② **quickjs 换库**（`com.whl.quickjs:wrapper-android:3.2.3` → `io.github.dokar3:quickjs-kt:1.0.15`，包名 `com.whl.quickjs.*`→`com.dokar.quickjs.*`，`RikkaHubApp.onCreate` 删 `QuickJSLoader.init()`；**宿主 App.java 的 DEGRADED 兜底注释提到 QuickJS 原生库失败，换库后需重验降级路径**）；③ `Theme.kt` 跟进上游 2.5.4（`getActivity()` + null 守卫）。
  - **2.5.3 亮点与我们相关**：输入栏新增 **blur / glass 背景效果**（`BackgroundEffectType{BLUR,GLASS}` + haze 依赖）——属"UI 质感"重点，重移植后可评估用于东方无限风格。
  - 上游已删除 `CONTRIBUTING.md`、README 三语改为"不接受 PR"、无 CHANGELOG（迁移依据只能来自 diff）。新增生产文件仅 4 个，模块结构完全未变（`:app:baselineprofile`/`:videogen` 继续跳过）。
  - 未证实项：`Theme.kt` 停留 2.5.1 的确切原因；宿主 AGP 版本文档（PATCHES.md 称 9.3.1）与 vendor toml（9.4.0）不一致；新 compileSdk DSL 在 library 模块的可用性；`ConversationSessionManager` 大重构对宿主定制的间接影响未穷尽。
- 2026-09-25（T1 编码完成，待构建验证）：新增 `NavBall.java`（组件，球挂 host 层跨页常驻含 AI 页、零权限）+ `ic_nav_grid.xml`（Phosphor squares-four，复用已有 ic_close 做 ×）+ `AiPermissionGate.java`（首启权限引导）；MainActivity 拆底栏（删 makePrimaryNav/navItem/accentIndicatorShape/composePrimaryShell 宽屏分支/primaryNav 字段与 SourceListPageState 引用/primaryShellWide），`goToDestination` 的 AI 分支改为经 `maybeGuideAiPermissions(this::enterAiPage)`（新增 AI_PERMISSION=71 回调 + 串行请求链）；新增 installNavBall/refreshNavBall/reflowNavBall 钩子。**编译校验已过**（:app:compileEmptyReleaseJavaWithJavac BUILD SUCCESSFUL）。**待办**：release 构建 → 真机装机自测。
- 2026-09-25：恢复误删技能（commit 3154424）；三层调研完成（本地 Token→M3E/leinardi/AssistiveTouch/60fps 实践）；架构勘察完成（goToDestination 复用、primaryNav 引用清单、host/insets 结构）；计划文档落盘。

## 六、T9 预返回落地方案（勘察已定，待实施）

- 现状：MainActivity:113 注册裸 `OnBackInvokedCallback`（PRIORITY_DEFAULT）→ 静态注册会**禁用系统预返回动画**；:212 还保留 `onBackPressed()` 覆写（旧 API，同样杀死预览）。
- 改造：
  1. onCreate 删裸回调注册 + 删 `onBackPressed()` 覆写，改为 `getOnBackPressedDispatcher().addCallback(this, backCallback)`；
  2. `backCallback` 实现 `OnBackAnimationCallback`：`onBackStarted`（记 progress 起点）/`onBackProgressed`（驱动 pageFrame：scale 1→0.92、侧向 translationX ≤20dp、圆角 + 压暗，跟手）/`onBackCancelled`（VPA 弹回）/`onBackInvoked`（提交 `performSystemBack()`）；
  3. `isEnabled` 随页面状态切换：根页（pageKind==0 且 primaryDestination<=0 且无 selection/tool 栈）→ 禁用 callback，让系统回桌面预览动画出现；
  4. AI 页（pageKind==5）：有启用 Compose 回调时转发 `getOnBackPressedDispatcher().onBackPressed()`（现状已有该逻辑，保留）。
  5. 动画红线同 T1：VPA + 有界曲线，progress 跟手阶段直接 set 属性（不走动画器），cancel 才走 220ms 回弹。

