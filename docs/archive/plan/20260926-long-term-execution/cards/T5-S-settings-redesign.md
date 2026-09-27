# T5-S 设置界面重做 · 研究卡（2026-09-26）

> 状态：研究主体完成（滑条/开关研究已固化 §9；卡片/配色/塑料感根因三线子代理研究进行中，返回后并入 §10）；用户已拍板方向 A + 三档性能按钮 + 设置项搜索 + UA 修复 + 优享移除（见 §5），**未动任何代码**。证据为文件:行号，开工时复核。
> 方向已定：Google 官方审美 + 现行设计规范（docs/design/wear-ui-system.md v2）+ Phosphor 图标。

## 1. 现状要点（MainActivity.java，除注明外）

- 设置页 = `showSettings()`（:950）：顶部 `IntegrityPayButton` + 6 个折叠卡片组，每次进入整页重建；**六组全部默认收起**。
- 六分组约 40+ 项：常用（下载/搜索/浏览）、安装与权限（ADB）、搜索索引、**性能与加载（6 面板 14 条 SeekBar）**、网络与兼容、数据与关于。
- 视觉：纯黑 OLED 背景 + 卡片 16dp 圆角描边（`settingsSection`:935，圆角 16/20/22/26 混用）；行高 56dp、标题 14sp/字重 560；**所有开关行图标一律 `ic_folder` 占位**（`settingsSwitchRow`:471），无语义。
- 交互：行只有 ripple 无按压缩放（`applePressScale` 只挂在顶部付费按钮）；展开折叠 `animateSection`（:936）= ChangeBounds+Fade 240ms M3 emphasized 曲线；开关为自绘 `LumaSwitch`（ValueAnimator 190ms）。
- 无设置项搜索；无行分隔；有就地刷新注册表（`settingsRefreshers`:946，好底子）。
- 动画栈三处不统一：TransitionManager / LumaSwitch ValueAnimator / 付费按钮扫光。

## 2. 顺带清账清单（已在 T5-S 现状登记，动手时一并处理）

- UA 范围掩码机制实际已死：保存回调硬置 ALL（:1321），写盘也硬编码 ALL（`persistSearchSettings`:913）→ 修复=让掩码真实生效（属行为修正，需用户知情）。
- 「恢复默认」不彻底：`sessionIndexEnabled` 声明默认 true（:114）vs 恢复置 false（:1633）矛盾；`sessionBatchDownloadSingleItem` 不被重置（:1633）。
- 死代码：`buildSearchTogglePanel`（:1348）、`buildUnifiedSliderPanel`（:933）、`premiumAccountCard`（:1617，归 T23 决定去留；删除时不能连带删 `showPremiumLoginDialog` 等公共方法）。
- 首帧色跳：styles.xml 窗口背景 `#0B0A12` vs 运行时 bg `#000000`（T5-V 已登记，可在 T5-S 一并修）。

## 3. 设计语言结论

- **v2 规范完整但零落地**：`MotionTokens`/`PressFeedbackController`/`PremiumSurfaceDrawable`/语义色阶在代码中无一处存在；现有可用资产只有 `applePressScale`（VPA 版）、`ThemeEngine` 11 色扁平 token、M3 emphasized 展开曲线。
- **图标保持 Phosphor**（用户 2026-09-25 拍板定库），本专题只做语义化补齐（替换 `settingsSwitchRow`:471 硬编码的 ic_folder 占位）。开关行标题清单（映射素材，去重 13 条）：下载时推荐诚信付费、列表源展示链接、基础链接超时自动切换（两个变体文案）、批量下载逐项显示（两个变体文案）、文件夹递归、浏览列表时自动翻页、浏览时自动加载更多、网页外部打开、自动更新索引、蓝奏链接直接解析打开（两个变体文案）。
- **动效基座建议维持 VPA 有界曲线**：v2 文档虽批准 dynamicanimation 弹簧，但 v1.19.8 有弹簧 ANR 事故、R-19 为用户边界红线、20260924 低速降级报告针对此；T5-S 用 PathInterpolator 等效表即可，是否解禁弹簧另行单独决策。
- 主题：纯深色不跟随系统（styles.xml 与 values-night 相同，无 DayNight）；本专题不做浅色模式。

## 4. 方案选项（待拍板）

**方向一 · M3 卡片分组派（推荐）**：保持卡片分组骨架，按 v2 token 全面升级——surface 亮度阶梯（卡片 8–10% 提亮 vs 纯黑）、Card 圆角统一 20dp、`PremiumSurfaceDrawable` 材质（描边+单侧高光+按压提亮）、字重字阶对齐 v2、Phosphor 语义图标、统一按压模型（按压缩放+提亮取代裸 ripple）、展开折叠统一动效栈、危险操作红色语义。信息架构微调：常用组默认展开；14 条滑条的「性能与加载」重组为「高级」分区（收纳+滑条重画）。
→ 最贴 v2 规范，改动可控，产出组件直接复用给 T5-D/T10-R5。

**方向二 · M3 纯列表派（Pixel 设置风格）**：去卡片，全宽列表+分组标题+细分割线，M3 控件形态。
→ 最「原生 Google」，但与 OLED 卡片质感差异大，14 条滑条塞纯列表很挤，高级感依赖排版功底。

**方向三 · M3 Expressive 派**：Panel 26–30 大圆角、形状变换动效、container-transform 展开。
→ 最新潮，但在 60fps 与低速设备约束下风险最高，内容密度（40+ 项）易冲突。

## 5. 用户已拍板（2026-09-26）

1. **视觉方向 = A · M3 卡片分组派**（用户要求：全面研究并优化好，全方位提升）。
2. **性能参数改为「高性能 / 均衡 / 节能」三个按钮**（映射表见 §6）；**节能档的「低动效」做成可单独取消的子开关（用户已同意）**。
3. **加设置项搜索**，且要优化好（实现研究见 §7）。
4. **UA 掩码修复确认一并做**（行为变化已告知并同意）。
5. **优享 = 整体移除（甲方案）**。用户原话："扔了它吧，别有残留，别影响其他东西"。移除清单见 §8。
6. **信息架构重排确认**：常用组默认展开，性能/网络/并发细项收进「高级」分区（用户已同意）。
7. **（2026-09-27 追加拍板）**：v2-v5 连续被否后，用户明确**怀念 v1**——视觉回退到 v1 构图与软件现有紫暗配色（bg #000 + 卡 #0F0E13/#17151F + 紫 #A78BFA/#C4B5FD），**三档选择器改选形态 3 独立芯片**（三块分开，闪电/天平/叶子图标；原拍板"形态 2 胶囊滑动"作废）；Breezy 冷中性仿制路线（v5）否决。"慢慢优化"=后续在 v1 基底上渐进修塑料感细节，不再换方向。

## 6. 性能三档研究（2026-09-26 完成）

**核心反直觉事实（必须先讲清）**：六条并发滑条全部被设备自适应值夹死（`adaptiveNetworkWorkers` = `min(任务数, 核数 × max(8, 堆余量MiB/8))`，LanzouCore.java:824-826），**手动调高到设备上限之上完全无效**；且 `NetworkGovernor`（LanzouCore.java:834-843）是进程级总闸。所以「高性能」不存在"调更高并发"的空间——真正的档位差异必须落在**链长（请求深度）、视图量、缓存保留、后台作业、动效**上。

**限频红线是硬编码的**：`ORIGIN_PAGE_SLOT_MS=1100` / `NEXT_PAGE_FLOOR_MS=3000`（LanzouCore.java:41），任何档位都突破不了；撞限频代价是全局的（`ROUTE_PRESSURE+1` 让所有并发按 `1/√(1+pressure)` 缩水，直链冷却≥15s）。

**建议映射（待用户确认）**：

| 参数 | 高性能 | 均衡（=现状默认） | 节能 |
|---|---|---|---|
| 搜索线程数 | 0 自动 | 0 自动 | 0 自动（设 1 会让多源搜索退化成串行，体感像坏了） |
| 慢源让位时间 | 15s | 15s | 30s |
| 单源最高展开页数 | 0（无限，实为 1000 页上限） | 0 | **5**（最关键：直接砍链长） |
| 搜索视图创建上限 | 0 | 0 | **200** |
| 识别/解析/保存并发 | 0 自动 | 0 自动 | 0 / 0 / **1** |
| 同时下载 | 0 自动 | 0 自动 | **2**（唯一真正影响功耗发热的并发项） |
| 列表源页展示项目数 | 0 无限 | 32 | 32 |
| 初始/后续自动展开 | 1 / 1 | 1 / 1 | **0 / 0**（不再自动预翻） |
| 索引线程 | 0 自动 | 0 自动 | **1** |
| 索引保留 | **720h（30 天）** | 24h | **6h** |
| 同时安装 | 0 自动 | 0 自动 | **1** |
| 文件夹递归 | 用户自由 | false | **强制 false 并置灰**（最强请求链放大器） |
| 浏览时自动加载更多 | 用户自由 | true | **false** |
| 自动更新索引 | true | true | **false**（无人值守后台扫源=功耗+限频双风险） |
| 动效 | 全开（VPA ≤300ms） | 全开 | **低动效**（复用产线已有弱设备路径：140/190ms 淡切 + 关入场 stagger；建议做成可单独取消的子开关） |

**「高性能」的安全上限 = 现状默认值，不做任何上调**；建议 UI 文案说明"使用设备可承受的最大并发，请求节奏由 App 自动节流以保护账号不被蓝奏限频"。

**实现要点**：新增 `perf_tier` 键 + 展开成现有键，**不改现有键语义**（`search_settings-v1`/`download_settings-v1`/`adb_install_settings-v1`/`source_settings-v1` 全部保持兼容）；`performSettingsDefaults`(:1633) 必须同步重置档位为「均衡」；程序化 `setProgress` 因 `if(user)` 守卫不会误写盘（安全）；滑条「最右=自动/无限」语义不统一，三档映射不能用一个 0 通吃；用户手动拖滑条后档位如何显示需新设计（建议：手动改后档位显示「自定义」，项目无先例）。

## 7. 设置项搜索实现研究（2026-09-26 完成）

- **方案：顶部内联输入框实时过滤**（非全屏搜索页）。理由：项目 3 个页内搜索（源列表 :1467、下载页 :1587、目录页 :678）全是顶部内联；设置页属 `ListScreen` archetype（wear-ui-system.md）；全屏方案要复制整套 `systemBackAction` 状态机（:661/:663），成本反超。
- **照抄样板**：`pageSearch(action, debounceMs)`（MainActivity.java:501）= 48dp 高 FrameLayout + `shape(SURFACE,14)` + 1dp 描边 + 透明 EditText；**右侧改为 `ic_close` 清除按钮**（项目内无先例，需新写；`ic_close` 资源已存在）。
- **去抖 150ms**（下载页用 200ms，设置页较轻）。
- **匹配用 `folderMatchRank`（:776）的 contains 档，不接全局模糊开关**（本地固定文案，模糊子序列只会误匹配）。
- **坑 1（最高风险）折叠组展开态**：`settingsSection`(:935) 无展开态记录字段，6 组默认全收起。搜索时强制展开全部、清空后恢复全收起（初始态最不意外）。
- **坑 2**：过滤时**绝不能调用 `refreshSettingsInPlace()`**（:949）——每个 refresher lambda 闭包持有 TextView，会写已 detach 的 view。
- **坑 3**：面板类（buildSearchSettingsPanel 等 6 处）内部行标题是散落局部变量，无统一注册点 → 给每个行 View 加 `setTag(关键词)`，过滤时匹配 tag（沿用项目 tag 范式）。
- **坑 4**：某组 0 命中应整组 GONE（避免空壳卡片）；空态文案「没有匹配的设置项」。

## 8. 优享账号研究结论（2026-09-26 完成，用户质疑必要性）

**「优享」= 蓝奏云优享版（ilanzou.com）的会员侧账号**，本 App 对它的唯一用途是**把蓝奏云分享「转存」到用户自己的优享版网盘**——**与"下载到本机"完全无关**。本机下载全链路（`beginDownload`:1759 → `DirectLinkResolver` → `SegmentDownloader`）不含任何 premium 引用，不登录也完全可用。

**关键事实**：
- 设置页那张卡片是 **v1.0.9（commit 395d22f）有意移除的**（提交信息原文："移除设置页蓝奏云优享版账号卡片(保留文件夹页保存功能的独立登录入口)"），方法体遗留成死代码。
- 但优享功能整体不是残留：目录页「保存到网盘」（:757，仅当分享页有转存入口时出现）与源多选「保存所选源到蓝奏云优享版」（:1484）是**两个活的用户入口**，`showPremiumLoginDialog`（:1908）有 9 处活调用点。
- **缺口**：登录/注册/添加账号有等价活入口；但「验证账号」「移除账号」「多账号列表」**仅存在于死卡片** → 用户一旦登录，**没有任何 UI 途径退出或换账号**（`removeAccount`/`clearLogin` 无活调用点）。
- 隐私政策（:1615）已把优享登录写入合规声明；凭据用 AndroidKeyStore AES-GCM 加密存本地（密码本地留存）。
- 不登录的后果：点「取消」静默中止本次转存，无提示无 fallback；本机下载不受影响。

**用户已拍板：甲 · 整体移除（2026-09-26）**，原话"扔了它吧，别有残留，别影响其他东西"。移除清单（开工时逐项执行）：

1. 死卡片族：`premiumAccountCard`(:1617-1621)、`premiumAccountLabel`(:1622)、`refreshSettingsFade`(:1625)、`showPremiumRemoveAccountPicker`(:1626)、`confirmRemovePremiumAccount`(:1627)、`verifyPremiumAccounts`(:1628)。
2. 活入口两个：目录页「保存到网盘」按钮（:757，含 `f.saveUrl` 判定）与源多选「保存所选源到蓝奏云优享版」（:1484）及其确认链（:2016/:2017）。
3. 记录与队列族：`ENTRY_PREMIUM_SAVE`(:73)、`isPremiumSaveEntry`(:270)、下载页「保存中」分类与 :296-298 UI 族、:333-347 暂停/并发/终止/容量切换、`resumeInterruptedPremiumSave`(:319)、`showPremiumMissingAccountPrompt`(:328)、`showPremiumCapacityPrompt`(:344)、`PremiumSaveCoordinator`、`PremiumCloudClient`。
4. 凭据清理：`premium-session-v1` 本地凭据与 KeyStore 密钥别名（迁移/清除策略需在实施时定，不能留下无法清除的残留）。
5. 文案与合规：隐私政策 :1615 相关段落、:1912/:1913 登录弹窗文案、:1932/:1937 保存结果弹窗、:336 删除说明。
6. **红线**：`showPremiumLoginDialog`(:1908) 与 `openPremiumRegistration`(:1629) 在移除后必须确认无残留调用点再删；不引入绕过上游付费的任何能力（TASKS.md:153）；移除不得影响本机下载、浏览、搜索、安装任一链路。
7. **验收**：全仓 grep `premium`/`优享`/`ilanzou` 零残留（除历史文档与 CHANGELOG 记录）；编译通过；本机下载全流程回归正常。

## 9. 控件规格研究：滑条与开关（GitHub 一手数据，2026-09-26 固化）

**来源**：material.io M3 组件规范 + material-components/android 源码 token + Telegram/IndicatorSeekBar/Seeker/discreteSeekBar 等真实项目手法。卡片/配色/塑料感三线补充研究另有子代理进行中，返回后并入 §10。

### 9.1 M3 官方规格（一手数值）

- **滑条**：轨道 16dp 厚；active/inactive 两段之间留 **6dp 间隙**；handle 不是圆球，是 **4dp×44dp 竖条**（官方 M3 形态）；轨道两端各有一个 **4dp 端点 stop 圆点**（对背景 3:1 对比度）；`thumbElevation=0`（**无阴影**）。
- **开关**：轨道 **52×32dp**；handle 关 **16dp** / 开 **24dp** / 按 **28dp**（尺寸呼吸代替阴影）；按压时 handle 色 OnPrimary→PrimaryContainer。
- **M3 Expressive 五档轨道尺寸**：XS 16dp / Small 24dp / Medium 40dp / Large 56dp / XLarge 96dp（设置页适合 Small/Medium）。
- **可感知阈值**：按压变化量 ≥25% 才可感——现 LumaSwitch 按压仅 +15%，低于阈值。

### 9.2 防塑料感 6 手法（滑条/开关专项）

1. 两层轨道不同粗细（active/inactive 不同高度或内外双层）制造层次；
2. thumb 无阴影：尺寸呼吸 + 状态层替代 elevation；
3. 端点 stop 圆点补 3:1 对比度；
4. 状态层用主色低 alpha（不用灰白）；
5. 按压变化量 ≥25%；
6. 轨道带主色相，不用纯中性灰。

### 9.3 对比度实测（本主题取色）

| 取色 | 用途 | 对背景对比度 | 结论 |
|---|---|---|---|
| #433864（tint primary 40% on 卡片面） | 未填充轨道 | 1.99 | 推荐值 |
| #5C5866（tint muted 60%） | stop 圆点 | 3.05 | 达标 3:1 |
| #A78BFA（primary） | handle/填充轨道 | 7.72 | 推荐 |

### 9.4 三套落地方案

- **A · M3 原味**：4×44dp 竖条 handle + 16dp 轨道 + 6dp 间隙 + stop 圆点，零阴影。最"官方"。
- **B · Telegram 极简**：细轨道 + 圆 thumb，克制，辨识度低。
- **C · Seeker 式**：值气泡/刻度信息重，偏工具箱场景。
→ 建议：设置页用 A 做骨架、吸收 B 的克制；工具箱密码生成器滑条（ToolHost.java:741，已 tint primary，v1.7.8 对齐 Bitwarden/KeePassDX）可保留现形态仅统一间距。

### 9.5 LumaSwitch 诊断（5 项，逐条对应 9.1/9.2 修法）

1. 动画 LinearInterpolator 机械感 → 换 VPA 有界曲线（M3 emphasized 等效表）；
2. 按压变化仅 +15%（<25% 阈值）→ 尺寸呼吸按 M3 三态 16/24/28dp；
3. 无状态层 → 按压加主色低 alpha 状态层；
4. 因 shadowLayer 开 SOFTWARE 层拖性能 → 去阴影保 HARDWARE；
5. thumb 内嵌无层次 → M3 式 handle 变尺寸 + 关态描边。

### 9.6 SeekBar 现状盘点（改造备料，全仓 17 处）

**设置面板 14 条（全部原生未着色，MainActivity.java）**：

| 面板 | 位置 | 滑条 |
|---|---|---|
| buildSearchSettingsPanel | :1293-1296 | 同时活跃源数、慢源让位、单源最高展开页数、搜索视图创建上限（4 条） |
| buildAutoExpandPageSettings | :921 | 初始/后续自动展开页数（2 条） |
| buildIndexSliderSettings | :924 | 索引线程、索引保留（2 条） |
| buildAdbInstallThreadSettings | :929 | 同时安装（1 条） |
| buildListDisplaySettings | :932 | 列表源页展示项目数（1 条） |
| buildDownloadSettingsPanel | :1612 | 识别/解析并发、同时下载、同时保存（4 条；保存条随优享移除→剩 3） |

**弹窗 2 条**：保存并发 dialog :333（随优享移除消失）；重新测试并发 dialog :1496（保留，需换新滑条）。

**工具箱 1 条**：密码生成器长度 ToolHost.java:741。

→ 改造建议：做一个自绘 `LumaSlider`（对齐 9.1 M3 规格 + 9.2 六手法），设置 13 条 + 弹窗 1 条统一切换；「同时保存」两条（:333/:1612）因优享移除自然消失（§8 清单联动），不必单独迁移。

## 10. 补充研究 ①：塑料感根因与解法（子代理一手源码报告，2026-09-26）

方法：Telegram `darkblue.attheme` 原始值逐项核对、material-web v0_192 tokens、M2/M3 官方规范页，对比度全部 WCAG 公式实测。与 §9 互证：开关 52×32、thumb 16/24/按压 28 两路一致。

### 10.1 八条根因与判定标准

1. 次级文字过灰（<4.5:1 即灰败）——我们的 #9A93AB vs #16141F = 6.18:1 **达标**，问题不在此；
2. **描边糊边（我们最大的根因）**：#262332 vs 卡面 #16141F 仅 **1.19:1**，介于可见与不可见之间；判定 <1.5:1 即糊（M3 outline-variant #49454F 对卡面 1.74:1）；
3. 单色平涂无层级：全页同色或靠投影分层即"平"；应卡面比页底亮一档；
4. 缺按压反馈：M3 state layer 白 8%/10%/16%（hover/press/drag），整页无按压态=贴图感；
5. 饱和色大面积平涂：主色占面板积 >10% 即艳俗，紫只做小点缀；
6. 分隔线亮灰滥用：Telegram 用**黑 58% 暗线**（0x95000000）；一屏亮灰横线 >3 条即表格感；
7. 圆角无节奏：单一圆角通吃=原型稿感（M3：卡 12dp、控件全圆）；
8. 线性/无动画：按压、开关过渡须 100–200ms 减速曲线。

### 10.2 Telegram darkblue.attheme 实测（DrKLO/Telegram）

- 卡面 #1D2733 / 分组底 #151E27——**卡面比页面底更亮**（1.11:1，与 M3 的 1.14:1 同档）；
- divider = 0x95000000（黑 58% 暗线）；按压 listSelector = #E6F7FF @ 7.8%（亮主题黑 6%、设置页黑 11%）；
- 次级 #7D8B99（对卡面 4.33:1，只用于次要提示，正文纯白）；开关轨道 未选 #5D7484 / 选中 #61A3D7；
- 手法：`Theme.java` L1178 `multAlpha` 预混、L4750 `createSimpleSelectorRoundRectDrawable`——**按压=预混一档更亮的实色**，不用透明层。

### 10.3 M3 基线暗色 token（material-web v0_192）

surface #141218 / surface-container #211F26 / on-surface #E6E0E9 / **on-surface-variant #CAC4D0**（次级文字，对 surface 10.9:1）/ outline #938F99 / **outline-variant #49454F**（描边/分隔线）/ primary80 #D0BCFF。

### 10.4 自绘要点（无组件库）

1. **Paint 分层顺序**（对齐 M3 container→state layer→content）：① 底色填充（圆角 Path 复用）② 1px 内侧描边——顶边 #3A3548@60%、底边 #000@40%（上亮下暗）③ 内容 ④ 按压层最后：`blendARGB(base, WHITE, 0.10)` **预混成实色一次填充**（防透明层叠色）；
2. **setShadowLayer 深色禁用**：硬件加速下只对文字生效，矩形阴影需 SOFTWARE 层；M2 明确暗底阴影不可见 → **深色卡零投影**，层级=卡面提亮+描边；
3. 按压动画：ValueAnimator 0→0.10f、100–150ms、DecelerateInterpolator，不建 layer；
4. Layer 取舍：默认 NONE；仅 alpha<1 整体动画时临时 HARDWARE、完即还原；**SOFTWARE 禁常驻**；
5. 行高 ≥48dp；state layer 绘制范围 40dp、整行接收触摸。

### 10.5 自检清单 15 条（可量化）

次级文字 ≥4.5:1；主文字 ≥13:1；描边 ≥1.5:1；卡面比页底亮 1.1–1.3:1 且零投影；主色 <10%；分隔线 ≤3 条/屏且为暗线或 #49454F 类；圆角 ≥2 档（卡 12dp/控件全圆）；可点行全有按压态白 10%；开关 52×32+thumb 16/24/28；图标单风格单色档（60–70% 亮度档）；一屏 ≤2 种字号；8dp 网格行高 ≥48dp；无 setShadowLayer；无 SOFTWARE 常驻；滚动帧稳。

### 10.6 对现有 token 的两条修正（实测得出）

1. **描边 #262332 → 提亮到 #3A3548 档**（现 1.19:1 太糊，目标 ≥1.5:1）；
2. 卡面 #16141F（对纯黑 1.15:1）与 M3 一致可保留，但必须配描边/顶边高光才"立得起来"。

## 11. 补充研究 ②：配色体系与中文字阶（子代理一手源码报告，2026-09-26）

来源：androidx compose material3 `tokens/ColorDarkTokens.kt`+`PaletteTokens.kt`+`TypeScaleTokens.kt`（官方生成文件，逐项提取）、m2 dark-theme/language-support 官方页、Mihon colors.xml（AMOLED 范例）、WCAG 2.2 公式实测。

### 11.1 M3 深色基线色阶（官方 hex）

| 角色 | hex | | 角色 | hex |
|---|---|---|---|---|
| Surface | #141218 | | OnSurface | #E6E0E9 |
| ContainerLowest | #0F0D13 | | OnSurfaceVariant | #CAC4D0 |
| ContainerLow | #1D1B20 | | Primary80 | #D0BCFF |
| Container | #211F26 | | OnPrimary | #381E72 |
| ContainerHigh | #2B2930 | | PrimaryContainer | #4F378B |
| ContainerHighest | #36343B | | OnPrimaryContainer | #EADDFF |
| Outline | #938F99 | | OutlineVariant | #49454F |
| SecondaryContainer | #4A4458 | | | |

**官方没有任何纯黑 role**——纯黑是社区/厂商自加层。

### 11.2 纯黑 OLED 的官方口径与 AMOLED 范例

- 官方允许纯黑（"UIs that require efficient battery usage can use true black"），但警告 OLED 像素开关有**滚动拖影**；官方推荐深灰 #121212 起步。
- **Mihon（Tachiyomi 后继）AMOLED 实做**：#000000 底 + 容器阶梯 #0C0C0C / #131313 / #1B1B1B（≈4.7% / 7.4% / 10.6% 亮度）——**纯靠面亮度分层，不靠描边**；divider 用 12% 黑白叠加。
- 我们现状：#16141F ≈ 高于黑底 5.8%，方向正确在 Mihon 两档之间；描边 #262332 对黑仅 1.37:1、弱于 M3 outlineVariant 的 ~1.99:1。
- **与 §10 交叉裁决（供设计稿执行）**：层级以**面亮度阶梯为主**（Mihon 路线），描边只做辅助边界线并按 §10.6 提亮到 #3A3548 档（≥1.5:1）；不做投影。

### 11.3 紫色 #A78BFA 用法规则（对比度实测）

| 场景 | 对比度 | 结论 |
|---|---|---|
| #A78BFA 文字 / 纯黑 | 7.7:1 | AA 达标 |
| #A78BFA 文字 / #16141F | 6.7:1 | AA 达标 |
| #C494FF / 黑、#16141F | 9.0 / 7.8:1 | **小字紫一律用亮紫** |
| 白字压紫实心钮 | 2.7:1 | **不达标（陷阱）**→ 紫底必须深字（≈#381E72 结构） |

- tint 只用官方档位：8%（hover）/ 10%（press）/ 16%（drag）/ 38%（disabled），**不用 12% 这类自由中间值**；
- **图标取色规则**：常规图标=onSurfaceVariant 档，激活/可交互=primary，紫底上=深色；全站禁"黑白混搭"；
- 分隔线/描边只用 outlineVariant 档弱色，禁用 outline 强色；
- 紫只做「选中/激活/可点」信号：大面积背景、次级按钮、全部图标一律不上紫。

### 11.4 中文字阶（M3 token + 中文口径）

- M3 一手：TitleMedium 16/24 Medium、TitleSmall 14/20 Medium、BodyLarge 16/24、BodyMedium 14/20、BodySmall 12/16、LabelLarge 14/20 Medium；
- 中文属 Dense 类：行高需大于拉丁（官方 language-support 页）；1.4–1.6 为惯例区间（非官方数值）：**正文行高 1.55–1.6、单行标题 1.35–1.45**；
- 设置页层级建议：**分组头 14sp Medium/次级色 · 项标题 16sp/主文字 · 副标题 14sp/次级色 · 右侧值文字 14sp 次级 · 脚注 12sp**。

## 12. 补充研究 ③：卡片/列表规格（子代理一手源码报告，2026-09-26）

来源：m3.material.io cards specs、material-components/android tokens.xml/dimens.xml、androidx Compose M3 ListTokens.kt/ListItem.kt/DividerTokens.kt、Breezy Weather/LibreTube/ReVanced Manager/Aurora Store/TDesign/antd-mobile 源码。

### 12.1 M3 官方数值（一手）

- **卡片**：圆角 12dp（corner-medium）；内容左右 padding 16dp；卡片间垂直间距 8dp；filled/elevated 卡**没有描边 token**（表达全靠色阶）；outlined 卡= surface + 1dp outlineVariant（`m3_card_stroke_width 1dp`）。
- **列表行**：容器高 56/72/88dp（单/双/三行）；行内垂直 padding 8dp、左右 16dp、**图标→文字 16dp**、图标 24dp；标题=BodyLarge 16sp/24、副标题=BodyMedium 14sp/20。
- **分隔**：M3 ListItem **没有 divider token**；Divider 是独立组件=1dp+OutlineVariant。
- 深色卡片面=背景上方 2–3 个中性阶（约 +6%~+12% 亮度），无描边。

### 12.2 开源实做（一手）

- Breezy Weather：设置页**大圆角分组卡** 28dp/16dp（内嵌 12dp），卡间距 2/16dp；
- LibreTube：无卡片纯行+原生 Preference；ReVanced Manager：Compose 全套 surfaceContainer 分层原样落地；Aurora Store：8/12/16 三档圆角体系；
- 图标主流=**裸 24dp onSurfaceVariant**；「圆底色块」是 M3 官方 avatar 形态（`leading_avatar_color=primaryContainer`）；
- 精致设置页（Breezy/ReVanced）倾向**卡片分组+组间空隙、行内不画线**；画线只在长列表纯行模式。

### 12.3 三种描边表达裁决

(a) **无描边纯分层=主结构**（官方默认，Google/ReVanced/LibreTube 在用，纯黑上最显高级）；(b) 1dp hairline（≈#262332 档）**只用于大列表分区/内嵌面板**，整页全描边会退回表格感；(c) 内侧高光**无安卓一手证据**，至多 hero 点缀不做体系。
→ **三报告最终裁决（§10/§11/§12 合并）**：层级=面亮度阶梯为主（卡 #16141F → 内嵌面板 #1D1B26 档）+ 顶边白 6% 微高光收立体；hairline #3A3548 仅用于搜索框/内嵌滑条面板；零投影零 shadowLayer。

### 12.4 中文列表排版（一手：TDesign + antd-mobile）

- TDesign 与 M3 完全一致：标题 16/24、描述 14/**22**（中文副标题行高 20→22 是唯一要调的）、行内边距 16、分组标题 14；antd-mobile 正文 17px/1.5（更 iOS 味，可选）；
- 结论：**字号不改（14/16sp），副标题行高提到 22**；两行结构间距靠行 padding（垂直 8dp、双行高 72dp）承接。

## 13. 边界

- 不动任何业务逻辑与持久化键名（`search_settings-v1` 等保持兼容），只动界面层与顺带清账清单。
- 全部效果先本地自测（编译+JVM 测试+截图），完成后按交付规则发 GitHub 测试包请用户实测。

## 14. v5：整页照抄 Breezy Weather 设置页（2026-09-27）

v1-v4 自创构图连续被否（"还是不好看，要不去 github 找现成的项目"）。转向：拉真实开源 app 真机截图当模板，整页照仿。

- **模板选定**：Breezy Weather 官方截图 `05-settings.png`（SSH sparse clone 到 /tmp/t5s-refs/breezy-repo/）；Kotatsu 截图无设置页，Mihon 无设置页，均弃。
- **模板实测数值**（1080px 宽 ÷2.625 密度，像素级扫描）：bg `#131313`、卡面 `#1C2024`、圆角 28dp（圆弧拟合 r=73px 确认）、卡外边距 16dp、组间距 16dp、同组卡缝 2dp、单行入口卡高 88dp、双行副题卡 112dp、标题 16sp Medium 白、副题 14sp `#B0B0B0` 行高 22、图标 24dp 距卡左 18dp、文字距图标 18dp、大标题 28sp Regular、零描边零投影零分隔线、入口卡无 chevron。
- **v5 用色**：照抄 Breezy 冷暗底（bg #131313 / 卡 #1C2024），紫色只作控件点缀（#A78BFA 开关轨与滑条填充 / #C494FF handle / on-thumb #1D1430）。
- **产物**：cards/T5-S-mockup-v5-main.html（主页：搜索胶囊+性能模式展开卡+三行开关卡+三张入口卡）、T5-S-mockup-v5-advanced.html（二级页：两条 M3 滑条+连接行为卡）、T5-S-mockup-v5-controls.html（2×2 控件特写）；渲染 assets/T5S-v5-*.png；并排对比图 assets/T5S-v5-vs-template.png（左模板右 v5）。

## 15. v6：回退 v1 构图 + 形态3 独立芯片（2026-09-27 定稿候选）

- 用户裁决原话："有点怀念你以前做的第一个版本了。选择第三个吧，图所示的底部第3个。然后颜色搭配也要一致好。还是用我们现在软件的。"
- **v6 = T5-S-mockup.html（v1）为基底，仅两处变化**：①性能模式卡内三档选择器从形态 1 换成形态 3 独立芯片（.chips/.chip 样式 v1 已内置，闪电/天平/叶子）；②删除尾部三形态候选区。配色零改动（软件现有紫暗系）。
- 产物：cards/T5-S-mockup-v6.html；渲染 assets/T5S-v6-main.png（390×1232）、assets/T5S-v6-advanced.png（390×1532）。
- 渲染教训：headless 全页渲染 3300px 会截断，行扫描切帧不可靠；**分帧渲染=源码切块**（找两个 `<div class="phone">` 位置，副本分别只保留一帧）。
- 后续优化候选（用户"慢慢优化"授权，逐条小步）：描边对比度提亮（§10 最大根因）、滑条换 M3 竖条 handle（§9）、开关对齐 §9 规格——均需在 v1 气质不变的前提下做，每次只动一处给用户看。

## 16. v7：文案去 AI 味（2026-09-27，v6 布局零改动）

用户："那些字啥的优化下，就是简介之类的，现在的排版和内容都太 AI 了"。只换字不动样式：
- 副题去"与"字串联/术语堆砌：常用="下载、搜索、浏览"；高级="速度、索引、网络与安装"；数据与关于="源管理、备份与应用信息"；性能模式="切换后，下面的细项会跟着变"。
- 说明板去机器腔：删"当前：高性能 —"与"生效中："句式 → "下载最快，请求也最密，更容易被蓝奏云限速。App 会自动把控节奏，一般不用管。"+ "同时下载 自动 ｜ 单源翻页 无限 ｜ 预翻页 1 页 ｜ 动效 全开"。
- 行名/值："慢源让位时间"→"慢源超时"；"单个索引最高保留"→"索引保留时长"；"资源源管理"→"源管理"；"四段 · 全部生效"→"已全部生效"；"搜索设置项"→"搜索设置"。
- 产物：cards/T5-S-mockup-v7.html；渲染 assets/T5S-v7-main.png（390×1146）、assets/T5S-v7-advanced.png（390×1462）。分帧渲染后需裁掉顶部标注行（70px）。

用户判"切换后，下面的细项会跟着变"掉价（解释交互=说明书腔）。改法：副题回归定性——"下载与动效的总开关"。全文案排查后仅此一句是"教你怎么用"式，说明板（限速警告+App 自动把控节奏）属定性描述保留。渲染图已更新（同名覆盖）。
