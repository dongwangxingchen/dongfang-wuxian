# 历史规范与踩坑记录（按需检索）

> 2026-09-25 从 AGENTS.md 迁出（用户授权瘦身："只保留有用的部分"）。内容未删只搬家；
> 本文不进每次会话上下文，按 AGENTS.md 第四节的索引按需读对应节。

## 索引（按任务查）

| 任务场景 | 读哪节 |
|---|---|
| 手势/滚动/搜索类 bug | 六-踩坑 2026-09-21晚、09-22 v1.19.5① |
| AI 对话页改动 | 六-踩坑 09-22 v1.19.5②③ |
| 发版/上传 GitHub Release | 六-踩坑 2026-09-21①②、09-22深夜⑤⑥ |
| 弹簧/动画/按压反馈 | 六-踩坑 09-22 v1.19.7、09-22深夜（覆盖弹簧结论） |
| 视觉/质感/圆角/字重 | 五-2026-09-22 消解④、六-踩坑 09-22 v1.19.7④⑤ |
| 构建/依赖/SDK 问题 | 三-2026-09-14 覆盖声明 |
| AI 页键盘/insets 问题 | 六-2026-09-28 v1.22.8（IME 根治，含"禁自研动画"红线） |
| Release 资产名/上传 404 | 六-2026-09-28 v1.22.8（ASCII 名 + 上传后核对 sha256） |
| dexdump/APK 产物分析假阳性 | 六-2026-09-28 v1.22.8（dexdump 绝对路径 + `-a` 才出注解） |
| 大功能立项流程 | 一-五阶段工作流 |
| bug 诊断方法 | 七-AI 开发协议（DfLog/技能路由） |
| 审美选型流程 | 八-决策协议 |

## 一、五阶段工作流（原始全文）

### 阶段一：大规模搜证（只读，不写业务代码）

1. 先复用已有研究成果：`~/heiyao/` 下的 m3tok.json、easing.json、m3dir.json、Unitto/gogh/tinted 分析文件，以及 `黑曜\07-研究报告` 等归档——已覆盖的部分不重复研究，标注引用即可。
2. 派出**至少 3 个并行子智能体**，按维度分工（如：布局与留白 / 动效与手感 / 组件细节与色彩质感），分工不重叠、互不依赖。
3. 每个子智能体的研究规范：案例**总量不少于 100 个**（全部子智能体合计），来源：GitHub 高星项目、Material 3 官方规范、Google 官方示例仓库、其他权威设计规范；**必须实际查看截图/图片**；只收录第一梯队案例并说明筛选标准。
4. 汇总输出《研究报告》：每条结论带出处和**可量化参数**——具体 dp 间距、圆角半径、动效时长、缓动曲线、透明度、阴影/描边值。"更精致""更高级"这类没有数字的形容词视为无效研究。

### 阶段二：学习确认

输出《模式清单》：将逐条采纳的做法 + 对应参考出处 + 落到本项目的具体改法。经确认后进入下一阶段。

### 阶段三：实现计划

列出要改的文件/方法（精确到行号或方法名）、顺序、每步验证方式、风险点。经批准后动手。

### 阶段四：实现与验证

- 小步实现，每步编译验证，编译不过不算完成。
- 完成后构建 APK 并按发版惯例归档，输出《待真机验收清单》：用户装到手机后看哪些效果、和之前对比什么。
- 每处与研究结论的偏离都要说明理由。

### 阶段五：复盘与延续

- 根据真机反馈修正，全部通过后把本轮确立的新规范和踩过的坑蒸馏归档。
- 主动提出下一轮最值得优化的方向，由用户决定是否继续。

## 二、2026-09-13 规范化迭代（依据 07-研究报告/规范化迭代研究报告-2026-09-13.md）

- **双区边界**：`rikkahub/` 为 vendor 区（上游源码，路径一一对应），任何改动必须同步记入 `rikkahub/PATCHES.md`；蓝奏云/工具为主机区（纯 Java）。禁止把主机区代码写进 vendor 区，禁止直接改 vendor 区而不留补丁记录。
- **发版三件套**：每版发布附"用户视角更新说明"（≤10 条，少用技术名词）；vendor 大改动可先发预览版灰度；发版节奏优先于功能堆量——发版断档=项目死亡（OpenCalc 反例）。
- **提交信息 Conventional Commits 化**：commit 前缀用 feat/fix/chore/docs（保留中文描述）。
- **大功能 spec 先行**：预计超过一轮会话的功能，先写 `specs/<slug>.md`（需求+验收标准）再动手；小改直接做。
- **issue 闭环**：仓库 issue 当 roadmap（bug/enhancement 两标签起步），修 bug 的 commit 带 `close #xxxx`；每版 release notes 由 git log 蒸馏。
- **踩坑蒸馏**：被复现的 bug 修完后，根因一句话追加进踩坑记录（CrossPaste 模式）。
- **已有 skill 化流程**：工具精修用 `tool-refine` skill；上游更新用 `upstream-sync` skill（拉 diff→PATCHES.md 重放→构建验收）。

## 三、2026-09-14 v1.8.x 项目事实覆盖声明（问题表单#7）

第二节"项目事实"中以下条目已随 RikkaHub 整搬过期，**以本节为准**：
- 技术栈：AI 对话部分 = Kotlin + Jetpack Compose 全家桶（vendor 目录 `rikkahub/`，12 模块）；蓝奏云/工具部分维持纯 Java 程序化 View。
- SDK/工具链：**AGP 9.4.0 / Gradle 9.6（wrapper 走腾讯镜像）/ compileSdk 37 / targetSdk 37 / minSdk 26**；构建 JDK 用 JDK21（JDK17 仅能过配置阶段）。
- 依赖与体积："零第三方依赖、APK<1MiB"仅适用于宿主模块；vendor 区依赖上游 catalog（`rikkahub/gradle/libs.versions.toml`），release APK 约 35MB（arm64-v8a）。
- 快照测试：paparazzi 与 AGP9 不兼容已停用，测试停泊 `tools/parked-tests/`，快照目检暂以真机截图代替。
- 命令速查（Windows 时代，仅回 Windows 适用）：`set JAVA_HOME=D:\DevTools\jdk-21.0.12.1+1 && gradlew.bat :app:assembleEmptyRelease`；本地 android-37 平台为手动安装（package.xml 伪造自 36），勿用老 sdkmanager 重装。

## 四、2026-09-12 RikkaHub 整搬决策（用户拍板）

- 用户明确裁决：AI 对话部分**整体搬运 RikkaHub**——上游的 UI、交互、设置页全部原样进来；本项目自写的 Java AI 实现（ai/ 包 918 行）不再作为交付物冒充移植。
- **解禁**："零第三方依赖"与"APK<1MiB"对 RikkaHub 相关模块不再适用。用户原话："我不介意你给它增加它的那个软件大小"。蓝奏云搜索/35 工具部分维持纯 Java 不动。
- **合规基础**：项目整体 AGPL-3.0 开源（用户已同意），满足 RikkaHub AGPL-3.0 衍生义务；README/致谢页署名已落实。
- **可维护性是硬指标**：上游源码以 vendor 目录进仓库、文件路径与上游一一对应（可 diff）；本项目定制做成薄 patch 层；上游更新流程 = 拉 diff → 覆盖 vendor → 重放 patch。
- **研究前置**：动手前必须完整摸清上游构建体系、依赖、UI 架构。

## 五、2026-09-22 全套规范一致性消解（全套 audit 后追加）

以下条目在旧流程条款与新协议冲突时，**以本节为准**：

1. **阶段四"每步跑 assembleEmptyRelease"修正为 debug 构建**：日常迭代验证一律 debug 构建（快、不被混淆裁剪）；release 构建只在发版时跑。
2. **阶段一"至少 3 个并行子智能体、合计 100+ 案例"的大规模搜证默认跳过**：2026-09-21/22 两份深度研究报告 + `docs/design/wear-ui-system.md` v2 已完成通用 UI 研究并把全部参数固化；常规 UI 任务直接引用 spec 与报告（引用即合规）。只有进入全新领域才重新走搜证。
3. **§一 Windows 路径的 Mac 落点**：`D:\新建文件夹 (2)\` → `~/heiyao/`。项目认知在 `~/heiyao/东方无限-项目认知.md`；m3tok.json/easing.json/m3dir.json 在 `~/heiyao/` 根。
4. **§二 旧色板（BG #0B0A12 等）被 spec v2 §5 OLED 亮度阶梯取代**：新 UI 一律取 spec token；存量页面按 spec §11 顺序渐进迁移，迁移完成期两套并存不视为违规，但禁止新码引用旧色值常量。
5. **快照测试路线澄清**："paparazzi 与 AGP9 不兼容已停用"不阻塞 spec §12 的 Roborazzi 视觉回归计划——Roborazzi 走 Robolectric 管线，是替代路线。
6. **技能路由规则（防过载）**：web 向设计技能只允许"审美评审框架"用途；其规则与 spec 冲突时，一律以 `wear-ui-system.md` §9 的冲突过滤为准。UI 任务只读：AGENTS.md + spec 对应节，禁止遍历技能库找规矩。
7. **§二"提交信息 Conventional Commits 化"条文废止（2026-09-27 审计消解）**：历史 60+ 提交均为"vX.Y.Z + 中文要点"风格，前缀条文从未执行过；规范与现实对齐——提交信息沿用"vX.Y.Z + 中文要点"，不引入 feat/fix 前缀。
8. **2026-09-27 WorkBuddy 全仓审计（抽查 12 项全属实，报告 `docs/audit/20260927-full-audit.md`）**：关键增量——①PATCHES.md 台账断档（P20/P21/P24 代码存在未登记、P18 标记错位为强制深色、P13 零定义）；②数据库迁移弹窗用户可见乱码 `RikkaHubEmbed.kt:409`；③Firebase Analytics 仍活装配（AppModule.kt:57 + ChatVM 5 处 logEvent），P15 只摘了 crashlytics；④sqlite-android `-SNAPSHOT` 未钉版（libs.versions.toml:64）；⑤CI 零测试任务；⑥审计给出的 M-*/H-*/V-*/B-* 编号可作为对应任务卡的证据引用。

## 六、踩坑记录全文（按时间）

- **2026-09-19 v1.18.0 x86_64 误发**：ABI 曾按"任务名含 Debug"判断被翻转成 x86_64-only，arm64 手表「应用未安装」；终验只核 versionCode 未核 native-code，体积 +684KB 异常被忽略。根修：ABI 改按构建类型静态判断；发版脚本上传前强制 `aapt dump badging` 断言 `native-code: arm64-v8a`；**发版终验必查 ABI，体积异常必须解释**。
- **2026-09-19 PowerShell 无 BOM UTF-8 脚本坑**（仅回 Windows 适用）：PowerShell 5.1 把无 BOM 的 .ps1 按 ANSI/GBK 读。铁律：脚本纯 ASCII；中文放独立 UTF-8 数据文件显式读取。
- **2026-09-21 v1.19.3 启动 ANR 修复**：①启动关键路径三禁——主线程 binder transact(Shizuku sticky 注册即触发)、启动期 Compose 预热挂载、任何无超时跨进程调用，一律 postDelayed 挪出 onCreate 窗口；模拟器复现不了真机 ANR 时，按"主线程可阻塞点"静态收敛后修复是合规路径。②GitHub Release 资产名严禁中文(会被剥离)；`gh release upload` 不支持 `文件#标签` 语法，用临时 ASCII 文件名上传；`gh release delete-asset` 的 `--` 分隔符后不能再放 `-R`。③无锚 `.gitignore` 规则会吞任意层级同名源码包，交接完整性必须靠 CI 实测验证。④一行式长方法里插行尾注释会把该行剩余代码全部吞掉，注释必须独立成行或抽小方法。
- **2026-09-21晚 v1.19.4 搜索滚动修复**：①嵌套 ScrollView 铁律——普通 ScrollView.onInterceptTouchEvent 超过 touchSlop 会无条件抢走竖向手势；内层列表必须在外层 dispatchTouchEvent 的 DOWN 时 requestDisallowInterceptTouchEvent(true)、UP/CANCEL 释放(SearchDragBar/FolderPullScrollView/pageScroll 三处同款)。②嵌套滚动被抢的连锁症状：内层 onScrollChangeListener 里的分批渲染/懒加载全部失活(搜索底部留白截断的根因)。③模拟器复现手法：wm size/density 调成用户真机视口；input swipe 坐标必须落在目标 ScrollView 内；OnTouchListener 只在"该 view 自身是触摸目标"时触发，要截获子 view 的事件流必须匿名子类重载 dispatchTouchEvent/onInterceptTouchEvent。
- **2026-09-22 v1.19.5 体验修复**：①自绘拖动条与原生滚动条互斥——自绘方案必须 setVerticalScrollBarEnabled(false)；自绘 thumb 靠 bumpActivity 唤醒时只改 alpha 不够，必须显式 invalidate()。②首开重初始化界面必须有同步占位——AI 对话页 ComposeView 首次创建重，正确姿势：点击瞬间同步挂轻量占位，ui.post 里异步建 ComposeView 再替换，替换前用 pageKind 守卫防错挂，catch(Throwable) 兜底。③模拟器太快抓不到占位帧属正常；功能验证看最终态。
- **2026-09-22 v1.19.6 软件库治理**：①批量导入探测会用蓝奏云页面标题覆盖传入标题落库——内置清单改名的存量同步必须按 URL 无条件强推一次(flag 版本化)，且同步要跑两次：导入前+导入后。②搜索结果 folder 条目必须固定 ic_folder 不 loadImage，meta 标"文件夹"。③"安卓机器人图标的软件"=源内 APK 自带默认 icon，APP 端改不了，改进方向是类型标注；压缩包标"不是应用,下载后需解压"。
- **2026-09-22 v1.19.7 质感升级**：①一行式方法插行尾注释吞代码第三次——铁律重申：注释永远独立成行。②SpringForce 严禁两个 SpringAnimation 共享同一实例——共享实例的 finalPosition 保持 UNSET，start() 抛异常真机即崩。③按压反馈统一 applePressScale(v)。④视觉 token 归档：solidShape 内做 radius 映射(≥24→26 Panel,14–23→20 Card,5–13→8 Micro,<5 原样)，历史 14 种圆角收敛四档；text(s,sp,color,weight) 重载用于标题字重(≥650 BOLD,≥500 medium)，标题靠字重不靠放大。⑤视觉改动流程：先量化混乱度→只改 helper/token 一处全局生效→编译+冷启动+全 tab 疯狂点击+monkey+JVM 截图回归，五步缺一不可。
- **2026-09-22 深夜 v1.19.8 真机卡死修复（覆盖 v1.19.7 ③的弹簧结论）**：①dynamicanimation 弹簧按压在弱真机不可用——tag 缓存的弹簧下次 ACTION_DOWN 不会被取消，连点时旧弹簧把 0.96 按压态拽回 1.0 显得"点不动"；stiffness 350 软弹簧 settle 无上界，弱手表每次点击近 0.5s 逐帧重绘，连点即渲染风暴→ANR 闪退；弹簧与 VPA 双系统同写 SCALE_X/Y 互相打架。v1.19.8 已删弹簧机制，全主题统一 VPA 曲线(220ms PathInterpolator(0.2,0.9,0.3,1.05))；**铁律：触摸路径禁用物理弹簧，按压回位一律 VPA**。②模拟器验证门禁有判别力的做法：静态页(设置页)A/B gfxinfo 对比；软件库/AI 等网络页数字噪声不可用作构建间对比；CPU 饥饿模拟+ANR/FATAL logcat 断言。③发版回归必须跑全量 :app:testEmptyDebugUnitTest。④模拟器截图"黑框"=圆屏镜像测试伪影（现已随圆表镜像删除，问题不再存在）。⑤debug 构建 ABI=x86_64 装不进 arm64 模拟器，模拟器一律装 release 包。⑥monkey 随机键会把应用挤出前台；启动一律 am start -n dfwx.dongdang/cc.nkbr.lanzouplus.MainActivity。

- **2026-09-25 v1.19.9 AI 页治理与空态 v2**：①空态贴输入框"32dp 滚底空间误吃"的根因=键盘两种状态的滚底预留差值靠推算，真机双键盘态实测应为 26dp；铁律：输入框贴合类布局参数必须双键盘态真机实测，不能推算。②AI 页四项修复细节见归档 `docs/archive/tasks/perf-interaction/`；性能 backlog：RecyclerView 批量渲染、启动 IO、外壳常驻。
- **2026-09-25 pm clear 误清豆包数据事故**：`pm clear` 误对豆包(com.bytedance.android.doubaoime)执行，用户数据丢失。铁律：**任何 adb/pm 操作前先对照禁止清单；豆包永久禁止触碰**（pm clear/卸载/force-stop 一律不做）。
- **2026-09-26 pm clear 误清 dfwx.dongdang 用户数据事故（二次）**：验证"干净启动"改用 `am force-stop` + 重新启动应用，**不用 pm clear**；pm clear/卸载/`adb shell rm` 一律先说明并等用户明确同意。
- **2026-09-26 真机禁盲点**：用户正在使用手机时，禁止坐标点击类自动化（盲点会点进任意界面）；只读验证（截图/日志/UI dump）优先，需要点击先请示并停下等待。
- **2026-09-26 蓝奏云"源失效"误判翻案**：双根因——默认 UA 被蓝奏 CDN 拉黑（影响全部 84 源）+ 解锁请求少发 lx/fid/pg 字段（影响 50 源）；修复后源全部正常。铁律：**"源失效"先查 UA/请求字段再下结论，禁止删源**；同批被真机截图复核推翻的还有"无进度条""14.8GB 内存""兜底图标 URL 404"等旧结论——真机实测优于静态分析。

### AI 工具链与协作环境踩坑（2026-09-27 固化）

- **Mimosa hook 拦截组合 Bash 命令**：对源码/配置的写操作（git add/commit/push、gradle）会被 Mimosa 安全 hook 检查，`cmd1 && cmd2` 组合命令整条被拒。铁律：git add/commit/push 每条单独执行；`git add app/build.gradle.kts` 单独一条。
- **Edit 前必须重新 Read**：会话中途文件可能被外部改动，拿旧内容直接 Edit 会失配或错改。
- **行尾注释吞代码**：2026-09-21、09-22、09-25 三次踩坑（见上 v1.19.7①），铁律重申：注释永远独立成行。
- **Robolectric 位图在本机不可用**：T5-S 设置重做时 Robolectric 无法生成位图断言，改用结构断言（view 树/文本/id）兜底。
- **aapt/aapt2 实际路径**：`$HOME/Library/Android/sdk/build-tools/37.0.0/`；发版终验用它 `dump badging` 断言 `native-code: arm64-v8a`。
- **设计稿出图用 headless_shell**：T5-S 设计稿截图走 headless_shell 无头截图，不是 Playwright MCP（2026-09-26 用户指定）。
- **gh release 的 jq 表达式不能用中文键名**；Release 资产名禁中文见 2026-09-21 条。
- **push 走 SSH over 443**：github.com:443 直连不通，`~/.ssh/config` 已配 ssh.github.com:443；push 失败勿反复重试 https。
- **本机 shell 偶发 command not found**：Bash 会话偶发 PATH 哈希失效（mv/mkdir 等基础命令报错），用绝对路径（/bin/mv）绕过，勿误判为命令缺失。
- **Shizuku 状态检查必须离开主线程 + 带代次闸门**（ADB-001，2026-09-28）：`Shizuku.pingBinder()/checkSelfPermission()/getUid()` 都是 binder 事务，服务被 ROM 冻结时可不超时阻塞；四类回调（binder 到达/死亡/授权结果/ServiceConnection）线程不受控，慢的旧评估会覆盖新状态，导致 `ready()` 误判、静默安装失败被误报成"服务未连接"（审计 H-P1-4）。修法：单线程状态车道承载全部平台调用 + 请求领 generation 号，评估开始/绑定前/发布前三处校验，旧代次一律丢弃。v1.19.3 用"延迟 3 秒启动"只是绕开主线程 ANR，改结构才是根治。
- **把"能捕获 bug"当作测试写完的判据**：ADB-001 的竞态用例写完先全绿，把三处代次校验临时删掉重跑，确认三个用例真的失败，再恢复；不会失败的正向用例只是装饰。其中一个用例最初在有无防护时都通过，说明它断言错了位置（实际走 CONNECTING 分支），改断言后才具备鉴别力。
- **robots/Robolectric 原生图形偶发失败**：全量跑 `testEmptyDebugUnitTest` 时 `HomeShotsJvmTest`/`ToolsShotsJvmTest` 可能报 `FileSystemAlreadyExistsException`（字体解压）或 `RenderNode` UnsatisfiedLinkError；单独跑或重跑即过，属本机环境问题，不要误判为代码回归——先重跑一次再排查。

### 2026-09-28 v1.22.8 三条新踩坑（IME 根治 + 发布资产名 + 工具链假阳性）

- **AI 内嵌页 IME 崩坏，根因是"自研动画替代几何换算"（v1.22.8 根治）**：宿主曾给 AI 内嵌页叠了 250ms 自研 IME 滑行器（`DfwxImeGlide`）+ `stickyBelow` 粘滞缓存 + 动画计数器，试图"平滑"键盘弹出。它与系统逐帧派发 insets 形成竞态，输入框崩坏、越打补丁越糟。根治 = 全删，只留一条纯几何换算 `target = ime.bottom - host.getPaddingBottom()`（`MainActivity.java` ime 重算监听器 :541-552；构造器 :523；源码 :527-537 留有根因注释原文）。**红线：宿主侧 `installSystemNavigationInsets()`（:491，裁 statusBars/navigationBars/displayCutout，但 ime 不裁）已做一次性裁剪，Compose 侧 `ui/components/ai/ChatInput.kt:225-226` 走标准 `.imePadding().navigationBarsPadding()`，中间禁止再插入任何自研 insets 动画或状态缓存**。同批删掉的还有动画计数器与 sticky 缓存——"看起来更顺滑"的自研中间层在本项目多次被证明是 bug 源。
- **GitHub Release 资产名中文被剥离（v1.22.8 二次事故）**：`gh release upload` 传中文本地文件名（`东方无限-v1.22.8.apk`）时中文被剥掉，资产实际变成 `-v1.22.8.apk`，而对外公布的链接是 `dongfang-wuxian-v1.22.8.apk` → 用户点击 404。防复发流程（已写入技能 `dfwx-release`）：先把文件 `cp` 成 ASCII 名再传，传完**必须核对资产名与 sha256 digest 与本地一致**，并用 `curl -r 0-1023` 实拉一段确认返回 `PK` 文件头。2026-09-21 已有同类记录（"资产名严禁中文"），本次是执行环节漏了"上传后核对"这一步。
- **Android 工具链两条假阳性陷阱（2026-09-28 复核 BRAND-003 时踩到）**：① `dexdump` 不在 PATH（在 `~/Library/Android/sdk/build-tools/<ver>/`），不经绝对路径调用会返回"command not found"，被管道吞掉后 `grep -c` 得 0——会被误读为"类/字符串不存在"。**必须用绝对路径**。② `dexdump -f`（无 `-a`）**完全不输出注解块**，看起来像"注解被 R8 剥离"；对照实验（`Sponsor` 模型类源码有 `@Serializable`，`-f` 下同样无注解输出）证明是工具输出限制，改用 `dexdump -a -f` 后确认 `@GET("/sponsors")` 注解**存活**。**结论：用 dexdump 下判断前，先拿一个已知有注解的类做对照。**

### ZCode 子智能体成本治理（2026-09-28 固化）

- **187 个子智能体每个都吃 token**：`~/.zcode/agents/*.md` 的角色 name+description 会全量进入每次会话的系统提示（187 个约 6-7k token/次），会话数一多就是纯浪费。已精简为 **10 个东方无限专用角色**。
- **"关闭"的正确做法 = 移出加载目录**：ZCode 只扫 `~/.zcode/agents/`（**非递归到同级目录**），把定义移到 `~/.zcode/agents-disabled/` 即不再加载且随时可恢复，不要删除。
- **防回装必须改同步脚本**：`~/.zcode/scripts/codex-agents-convert.py` 会把 `~/.codex/agents/*.toml`（175 个）重新转成 `~/.zcode/agents/*.md`。已在脚本加停用检查：`agents-disabled/<name>.md` 存在则跳过。不改脚本的话，下次 `sync-codex-shared.sh` 跑起来就全装回来了。
- **全量备份**：`~/.zcode/backups/agents-20260928-full/`（187 个，含 12 个手写角色）。
- 内置的 `general-purpose` / `Explore` 不在这个目录，不受影响（官方不可停用）。

## 七、AI 开发协议强化（2026-09-21，依据 tryallai 深度研究报告）

**构建分层**：日常调试一律 debug 构建；性能问题用接近 release 的 profileable 构建；release 只管发布。

**结构化日志**：新调试日志一律走 `DfLog` 约定——`DFX|area=gesture|case=<bug编号>|view=<View名>|event=<动作>|handled=…|scrollY=…`；按 case 一次拉完整因果链，禁止散插无关联 TAG。手势类 bug 强制先走 `.zcode/skills/gesture-debug/SKILL.md`；所有 bug 诊断走 `.zcode/skills/android-debug-triage/SKILL.md` 八步闭环。

**UI 规范**：颜色/圆角/间距/字体/动效只允许取自 `docs/design/wear-ui-system.md`（v2：M3E 官方弹簧值；按压 0.94/0.96+确认态 haptic；形状四档 Circle/Pill/Card20/Panel26-30；OLED 亮度阶梯替 elevation 阴影；中文用字重不用字号做层级）的 Token 层与四类页面 archetype(ListScreen/ToolScreen/DetailScreen/Conversation)，禁止新 magic number；UI 改动必须走截图矩阵+12 点评审(wear-ui-system.md §9)；Compose 只许做孤立岛屿(ComposeView)且五项视觉 token 与主 UI 同源，85 源列表与 37 工具页不迁移。

**AI 编程闭环**：任务分三档——微型直改+验证；中型先 Explore→Plan 再 TDD→Verify；大型/高风险走 Spec-lite(`docs/tasks/<slug>/` 下 spec.md/plan.md/verification.md)。bug 先复现再改产线码；没有验证证据不得宣称完成。

**多窗口协作**：调度窗口只做探索/计划/审查/工具落地；**同一文件同一时间只允许一个写入者**，真要并行实现用独立 git worktree。

**Agent-模拟器接口**：优先用结构化运行时证据，替代"猜+反复 logcat"。

## 八、决策协议与自进化机制（2026-09-22，用户要求"先问再做、越用越强、知识可检索"）

1. **能力菜单先行(先问再做)**：涉及审美/风格/方案选型的任务，动手前先向用户呈现 2-4 个可选方案——各带效果说明或截图对比、适用场景、工作量；用户挑选后才实施。豁免：纯技术修复(编译错误、崩溃、性能)、规范已钉死的 token 取值、用户明说"直接做"的场景。
2. **错误记忆协议**：用户每纠正一次做法，当场把教训蒸馏成一条"症状→根因→防再犯"追加进踩坑记录；开工前先检索踩坑记录与任务相关条目；条目一行一条不写长文，防上下文膨胀。
3. **偏好沉淀**：用户表达审美/交互/工作方式偏好时，追加进 `~/heiyao/东方无限-用户偏好.md`。
4. **开源参考库可检索化**：新核验的 GitHub 项目/设计规范/库必须落到 `黑曜/06-开源参考库/` 对应分类文件，条目带 star 快照/协议/用途/采用裁决；任何会话开工前先读 00-总索引.md 再按需取用。

## 九、构建/测试环境坑（2026-09-30 实测）

1. **被 kill 的测试任务会留下残留 worker JVM，导致后续测试大面积假失败**：
   症状 = `java.lang.OutOfMemoryError: unable to create native thread: possibly out of memory or process/resource limits reached`，
   表现为几十上百个测试类同时 `classMethod FAILED`，**看起来像代码改崩了，其实是环境**。
   实测系统线程 2569/20480、单进程最高 121/4096 —— **都没到上限**，是残留进程累积。
   处理：`./gradlew --stop` + `pkill -f GradleWorkerMain`，然后重跑即全绿。
   **判据**：如果失败信息是 `OutOfMemoryError at Thread.java` 且失败面很宽，先怀疑环境，不要改代码。
2. **`android.graphics.Color` 在纯 JVM 测试里是未实现的桩**：直接用会抛
   `Method red in android.graphics.Color not mocked`。凡是要做颜色运算的测试，**必须挂 Robolectric**
   （`@RunWith(RobolectricTestRunner::class)` + `@Config(sdk=[35])`）。
3. **`github.com:443` 本机直连不通**：验证 GitHub Release 直链必须走代理
   （`curl -sL -x http://127.0.0.1:7890 ...`），否则会长时间挂住或返回 `http=000`。
   上传走 `gh`（已登录），不受影响。
