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
| 版本号漏改 / 发版门禁被绕过 | **十二-1、十二-2**（唯一来源 + `&&` 与管道退出码） |
| shell 脚本坑（中文变量名 / BSD sed / 退出码） | **十二-2、十二-3、十二-4** |
| 更新校验该删哪条 / 缓存设计 / 第三方网页换肤 | **十二-5、十二-8、十二-10** |
| 布局视觉类问题（先量再改） | **十二-6** |
| 搜索/加载慢的提速思路 | **十二-7**（先查预算被谁共用） |

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

> ## 🛑 构建/测试一失败，**先读这一节再动手**
>
> **2026-10-02 实测教训**：一个 `OutOfMemoryError: unable to create native thread`
> 被我误判成"测试写法不对"，还发消息让队友去改测试 —— **错得离谱**。
> 正确原因就在下面第 1 条，我只要先读一眼就能省掉一整轮返工。
>
> **规则**：构建或测试失败时，先在这一节里找症状，**找不到再怀疑代码**。
> 这一节的每一条都是真金白银换来的，不是参考建议。


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
4. **「先删后量」会把基线一起删掉，导致差异无法归因 —— 要删先存到仓库外**：
   症状 = 重跑对比之后发现某个数字变了（多几条 / 少几条），**但你无法证明是谁造成的**，
   因为你为了"制造干净的重跑"先 `rm -rf` 了旧结果。2026-10-02 实测：
   DFW-123 清 lint 产物时执行了 `rm -rf app/build/reports/lint-results-*.{txt,xml}`，
   之后 warning 从 **116 变 118**，而旧报告已不存在 → 这 +2 条**永远无法归因**，
   只能退而给"我改的行上 0 条 warning"这种弱证据。
   处理：**先 `cp` 到仓库外**（`/tmp/xxx-before.*`）或先记录汇总行，**再**删；
   对比时逐条 diff，不要只看总数。
   **判据**：只要你的下一步动作里有"删掉旧产物再跑一次"，就先问一句"我待会要跟谁比"。
   适用范围不只 lint —— 测试报告、构建产物、`--rerun` 全量前后对比都一样。

---

## 八、2026-10-02 这一轮踩到的坑（都是"看起来没事、其实一直坏着"的类型）

### 1. `gradlew` 在 git 里的模式是 100644 —— CI 长期全红的真正根因

**现象**：GitHub Actions 241 次运行几乎全红，所有人都以为是 Android SDK 装不上
（那也确实是一个原因，`platforms;android-37` 真名是 `platforms;android-37.0`）。
修完 SDK 之后 CI **还是红**，日志里只有一行：

```
tools/ci-gate.sh: line 80: ./gradlew: Permission denied
##[error]Process completed with exit code 126.
```

**根因**：`git ls-files -s gradlew` → `100644`（普通文件），而磁盘上是 `-rwxr-xr-x`。
**git 只记录两种模式：100644 和 100755**，可执行位是**跟着索引走的**。
检出到 CI 后文件没有 x 位，`./gradlew` 直接 126。

**为什么一直没人发现**：本地是 `-rwxr-xr-x`，跑得好好的；而且红得太久，
所有人（包括我）都默认"CI 就是红的"，没人去看那行 `Permission denied`。

**修法**：`git update-index --chmod=+x gradlew`（顺带把 `rikkahub/gradlew` 和 4 个
`tools/*.sh` 一起补上），并在工作流里显式 `chmod +x gradlew` 兜底。

**教训**：**"红了很久"本身就是最大的掩护**。一条长期为红的检查，
等价于没有检查 —— 甚至更糟，因为它会让人以为"CI 在跑"。

### 2. 假绿的反向探针：字面量自比

我给 5 个测试类写"反向探针"时写成了这样：

```kotlin
assertTrue("坏代码（碰 pointer-events）必须被识别",
    "el.style.pointerEvents='none'".contains("pointerEvents"))
```

两个操作数都是**字符串字面量**，编译期就恒真，**跟产品代码一点关系都没有**。
后果是"守卫配了反向探针"这条检查项在评审时显示为**已满足**，实际零鉴别力。

**正确写法**（模板见 `RenderFolderNullSafetyJvmTest.theGuardActuallyDetectsAnUnguardedCall`）：
把守卫抽成一个函数，**把坏代码真的喂进去**：

```kotlin
private fun skinTouchesPointerEvents(src: String) =
    src.contains("pointer-events") || src.contains("pointerEvents")

assertTrue(skinTouchesPointerEvents("el.style.pointerEvents='none'"))
assertFalse(skinTouchesPointerEvents(skin))   // 好代码必须为假
```

**教训**：反向探针必须**跨过被测边界**。只要断言里没有出现"被测对象"，
它就只是自我安慰。

**第二种假探针：红了，但红的原因是错的（2026-10-02 DFW-124 实测）**

同一个坑的另一面。我给 `RemoteConfigClient` 的网络守卫写反向探针，
把守卫注释掉之后**它确实红了** —— 看上去探针有效：

```
x theNetworkChokePoint_isActivelyRefused_notJustUnreachable:
    java.lang.RuntimeException: Method toString in org.json.JSONObject not mocked
```

**但红的原因不是我的断言**，是 `org.json` 走了 AGP 的桩、`toString()` 崩了。
后果：同一个测试类里**第二条**用例（`fetch()` 应报「不可达」）在删掉守卫后**依然绿** ——
因为桩一崩就被 `fetch()` 内部的 `catch (Exception ignored)` 吞掉，它照样返回「不可达」。
**一条真红的用例，掩护了一条假绿的用例。**

**判据**：反向探针跑完，不要只看"红了"，要看**红在哪一行、报的是什么**：

| 报错内容 | 判定 |
|---|---|
| 出现**你自己写的断言消息** | ✅ 有效 |
| 框架 / 桩 / 环境抛的异常 | ❌ **无效**，得先把测试环境修成真的 |

本例的修法：给测试挂上 `@RunWith(RobolectricTestRunner::class)`，让 `org.json` 变成真实现。
修好后再跑探针，报错变成了我自己的断言消息，而且**直接打印出真读到的生产数据**
（`公告 1 条 / 版本 1.0.0`）—— 那才是因果证据。

> **一句话**：探针的价值不在"变红"，在于**红是你造成的**。

### 3. Robolectric 里测不出"动画被打断"

给 `crossFadeHomeSection` 补了 600ms 幂等结算兜底，并写了一条"快速来回切 + 推进时钟"的测试。
**反向探针（去掉兜底）实测不红** —— 因为 Robolectric 里 `animate().cancel()`
照样会触发 `withEndAction`，"结算丢失"这个极端情况复现不出来。

**处理**：把这段如实写进测试的注释里（"这条测试**没有**证明什么"），
不把它当成"验过了"。那条兜底是**防御性代码**，依据是 `animatePage` 里同款兜底的既有注释。

**教训**：测试通过 ≠ 被测的东西验过了。**写清楚"没验到什么"和写清楚"验到了什么"同样重要。**

### 4. 测试 JVM 线程只增不减 → 一次红 184 条

761 条 Robolectric 用例跑下来，测试 JVM 里的线程只增不减；加上系统里浏览器/输入法/微信
常驻进程本来就占着 2600+ 线程，顶到 `ulimit -u` 之后后面所有用例成批报
`OutOfMemoryError: unable to create native thread`。

**看起来像代码全炸了，其实一行代码的问题都没有。**

**修法（三层）**：
1. `tools/ci-gate.sh` / `release.sh` 开头把**软上限顶到硬上限**（`ulimit -u "$(ulimit -Hu)"`）；
2. `app/build.gradle.kts` 的 `testOptions` 加 **`forkEvery = 24`** —— 定期换测试 JVM，
   旧进程退出、线程全部归还。这是真正治本的一层；
3. 仍然建议跑之前 `./gradlew --stop && pkill -9 -f GradleDaemon`。

### 5. Java/Kotlin 字符串里不许出现内层 ASCII 双引号

一天之内踩了 **5 次**：`assertTrue("必须尊重"关闭动效"开关", ...)`。
Java 和 Kotlin 都会把它解析成字符串结束。

**规矩**：中文文案里的引号一律用 **「」**，不要用 `"..."`。
写完立刻编译一次，别攒着。

### 6. shell 里 `$变量` 紧跟中文会被吞

`echo "$CODE（安卓靠它判断）"` → bash 把 `（` 的字节当成变量名的一部分 →
`CODE?: unbound variable`。**一律写 `${CODE}`**。

`VersionNumberSinglePlaceJvmTest` 里有一条守卫会扫 `tools/*.sh` 抓这个模式，
**扫描范围必须是"目录下所有 .sh"而不是写死几个文件名** ——
这次新加的 `tools/bump-version.sh` 就因为不在写死列表里，立刻又踩了一遍。

### 7. 同一份代码跑出「2 红 / 3 红 / 0 红」—— 先怀疑**你怎么跑的**，不要先怀疑测试之间污染

**踩坑（2026-10-02，DFW-124）**：全量 `:app` 连跑几次，结果三次都不一样：

| 第几次 | 结果 |
|---|---|
| 1 | 819 条 / 2 失败 |
| 2 | 819 条 / 3 失败（**换成另外 3 条**） |
| 3 | 819 条 / 0 失败 |

失败的那几条**单独跑全绿**，看起来特别像"测试之间有污染"，差点按那个方向查下去。

**真因有两个，都不是"污染"**：

**(a) 我绕过了环境准备。** `ci-gate.sh` / `release.sh` 开头都有
`ulimit -u "$(ulimit -Hu)"`（本机软上限 **2666**、硬上限 **4000**），
但我图快**直接敲 `./gradlew`** —— 保护全都没生效。中间还并发跑过 gradle、
`pkill -f GradleWorkerMain` 误杀过还在跑的 worker。
这就是本文件第 4 条那个坑的翻版。

**(b) 新加的看门狗在 Robolectric 里必然误报。** 判据是
「往主线程 post 打卡任务，超时没跑到 = 卡死」，而 **Robolectric 的 Looper 默认 PAUSED**，
打卡永远不会完成 → 每个活得超过 5 秒的测试类都被判成"主线程卡死" →
白写现场（还要抓 10 条线程栈）。**它会往日志里灌假事故**，而这份日志的用途
恰恰是"留真实现场"。

**以后怎么做**：

1. **跑测试一律用 `bash tools/run-tests.sh`**（2026-10-02 新增）。
   它把三层保护抽成一个入口：顶 `ulimit -u` 到硬上限 / 清残留 worker /
   失败时按本节给排查顺序。`--rerun` 强制重跑，`--class XxxJvmTest` 单类，
   `--vendor` 跑 vendor 区。
2. **`--rerun` 是必须的**：`rm -rf test-results` 之后再跑，Gradle 会**从缓存恢复**
   那些 XML 而不是重跑，你会看到 `BUILD SUCCESSFUL in 1s` 和一堆"全绿"的假象。
   想确认测试真跑了，看 XML 里的 `timestamp`。
3. **失败的是"每次不一样"的几条时，先按环境排查**，不要先改代码。
   判据：**单独跑那条测试是不是绿的** —— 是，就基本可以确定是环境。
4. **不要在别人跑 gradle 时并发跑 gradle**，更不要用无差别的
   `pkill -f GradleWorkerMain` —— 它会杀掉对方正在跑的 worker。
   2026-10-02 就这么把队友的一次测试跑断了（两边都报 `java.io.EOFException`）。

**"跑得动"本身也是被测对象**：一套会随机变红的测试，等于没有测试 ——
因为红了之后没人再相信它。

### 8. 反向探针的第三种假象：**编译失败，你读到的是上一轮的旧 XML**（2026-10-03 实测）

**踩坑**：给 `verifyUpdateApk` 的签名校验打反向探针时，我用 `//` 把那一句注释掉：

```java
// if(!signatureDigests(current).equals(signatureDigests(archive)))throw new UpdateNotInstallableException("更新包签名不一致");
```

但**那个方法的整个方法体写在一行上** —— `//` 会把它后面**全部**注释掉，包括方法的闭合括号。
结果是**编译失败**，而 `app/build/test-results/` 里**上一轮的 XML 还在**，
我读到的"失败 0"其实是**上一轮全绿的结果**。

差点得出"我的去注释修复没用"的结论 —— 实际上是**探针本身写错了**。

**修法**：一行式方法里要注释掉一句，必须用 `/* ... */`（块注释不会吃掉后面的代码）。

**判据（比"看红没红"更靠前）**：

| 现象 | 判定 |
|---|---|
| 报错里是**你自己写的断言消息** | ✅ 探针有效 |
| 报错是框架/环境异常 | ❌ 无效（见本节第 2 条） |
| **一条都没红，而且你没先删 XML** | ⚠️ **先去查编译过没过**，别急着下结论 |

**操作**：打探针前先 `rm -rf app/build/test-results/testEmptyDebugUnitTest`，
这样"没有 XML"就等于"编译没过"，一眼可分。`tools/run-tests.sh --rerun` 也做这件事。

**通用规则**：**探针红了要知道为什么红；探针绿了要先怀疑"它到底跑了没有"。**

### 9. 反向探针的第四种假象：**阈值卡在"修好"与"没修"两个实测值之外**（2026-10-03 实测）

**现象**：给「页头保存路径被挤成 4 个字」写了一条测试，阈值拍脑袋取 100dp，跑出来**是绿的**。
按规矩打反向探针（把修复撤掉），**它还是绿的** —— 也就是说**那条测试当时是空的**。

**根因**：这一轮同时做了三处改动，其中两处（去掉冗余的文字标签、图标 25→22dp）**本身也腾出了宽度**。
于是"只撤回其中一处"之后，路径仍有 **101.7dp**，正好压在 100dp 阈值之上。

| 状态 | 实测路径宽 |
|---|---|
| 三处都改（修好） | **233.9dp** |
| 只撤回 weight 修复 | **101.7dp** |
| 我拍的阈值 | ~~100dp~~ ← 落在 101.7 **下面**，永远绿 |

**做法**：阈值必须**量出来**，且必须落在「修好」与「没修」两个**实测值之间**（两边各留余量）。
改成 150dp 后探针正确变红。**"看起来够低就行"的阈值等于没有阈值。**

**通用规则**：**先量两个状态的真实数字，再定阈值**；定完必须打一次反向探针确认它会红。
八-2/八-8 讲的是"探针本身要能识别坏代码"，这条讲的是**判据的松紧也得验**。

### 10. 审别人的度量结论：必须跟到**最终生效的那一层**（2026-10-03 实测）

**现象**：一份质量很高的只读体检报告（205 行、逐条带 `文件:行号`）指出
「下载卡片圆角 16dp ≠ token 的 Card 20dp」。看起来无懈可击 —— 源码里确实写的是 `solidShape(SURFACE,16)`。
**但实际渲染出来的就是 20dp。**

**根因**：`MainActivity.solidShape()` 对圆角做了**量化**：

```java
g.setCornerRadius(dp(radius>=24?26:radius>=14?20:radius>=5?8:radius));
```

传 16 → 落到 `>=14` 档 → **20dp**，正好等于 token 值。报告读的是源码**入参**，没跟到量化后的**结果**。
**照它改等于什么都没改**，还会在提交信息里写一句"修了圆角"。

同一份报告里另一条是对的：`solidShape(SURFACE2,11)` → 落到 `>=5` 档 → **8dp**，而 token 要求 circle。

**通用规则**：别人的度量结论（队友的、也包括我自己的）**必须跟到最终生效的那一层**再动手 ——
封装函数、量化、单位换算、默认值回落，任何一层都可能把入参改掉。

### 11. 守卫里的 `contains("text(")` 会撞子串 —— 守卫误报一次，下一个人就会把它删掉（2026-10-03 实测）

**现象**：全量 900 条里红 1 条：`VersionNumberSinglePlaceJvmTest` 说
「版本号只允许在「检查更新」那一行出现；这几行又在渲染它了：
`if(noticeCenter==null)noticeCenter=NoticeCenter.forContext(this,BuildConfig.VERSION_NAME);`」。

**根因**：守卫判据是 `line.contains("BuildConfig.VERSION_NAME") && line.contains("text(")`，
而 `forCon`**`text(`**`(` 里**含子串 `text(`**。那行根本没渲染任何 UI，只是把版本名传给公告中心做分版本过滤。

**修法**：改成词边界匹配 `Regex("(?<![A-Za-z0-9_])text\\(")`，并补一条探针把这个假阳性钉死。

**为什么必须修而不是绕过**：守卫一旦开始误报，下一个人面对"改一行无关代码就红"的局面，
最省事的做法是**把守卫删掉** —— 那时损失的不是这一条，而是它守的那条规矩（版本号只留一处）。

**通用规则**：**`contains` 判据在代码文本上必然撞子串**（`text(`/`forContext(`、`id`/`valid`、`run`/`runtime`…）。
凡是拿字符串去匹配代码，一律用词边界正则，并且**给假阳性也写一条探针**。

### 12. `setMinimumWidth/Height` 在固定 LayoutParams 下**不起作用**（2026-10-03 实测）

**现象**：`iconButton()` 里明明写了 `setMinimumWidth(dp(44)); setMinimumHeight(dp(44))`，
但下载页行内按钮实测只有 **38×42dp** —— 不达 token 要求的 48dp 触控区，而且**看代码以为已经守住了**。

**根因**：调用方给的是固定尺寸 `new LinearLayout.LayoutParams(dp(38),dp(42))`。
固定尺寸会以 `MeasureSpec.EXACTLY` 测量，`View.getDefaultSize` 在 EXACTLY 分支**直接取 specSize、忽略 minimum**。

**做法**：触控区**只能由调用方给的 LayoutParams 决定**。把「48dp 视图 + 13dp 内边距 = 22dp glyph」
收成一个方法（`rowIconButton` / `rowIconButtonLp`），避免每个调用点各写一个数、又慢慢漂回去。

**通用规则**：**`minimumWidth/Height` 只对 `WRAP_CONTENT` 生效**。
要保证触控区，就得保证**没有任何一层给它固定尺寸**；而"保证"的办法是收成一个方法，不是靠注释提醒。

### 13. `View.getVisibility()` **不看祖先** —— 用它判断"用户看不看得见"必错（2026-10-03 实测）

**现象**：给「多选时行内动作按钮要隐藏」写了一条测试，断言
`actionButtons().all { it.visibility != View.VISIBLE }`。产品代码明明把动作容器设成了 `GONE`，
**测试却是红的** —— 一度以为产品没生效，去查产品代码。

**根因**：`View.getVisibility()` 返回的是这个视图**自己**的标志，**完全不看父容器**。
父容器设成 `GONE` 之后，里面的 `ImageButton` 照样报告 `VISIBLE`。

**做法**：要判断"用户到底看不看得见"，必须**沿父链往上查**：

```kotlin
fun effectivelyVisible(v: View): Boolean {
    var cur: View? = v
    while (cur != null) {
        if (cur.visibility != View.VISIBLE) return false
        cur = cur.parent as? View
    }
    return true
}
```

（`View.isShown()` 也能用，但它额外要求 `mAttachedToWindow`，在 Robolectric 的
"手工 measure/layout 但不 attach"场景下会给出误导性的 false，所以本项目用上面这个显式版本。）

**通用规则**：**测"隐藏"要测"实际可见性"，不是测自己的 flag**。
凡是"父容器一关、子里一堆视图都该消失"的结构（多选模式、折叠区、空态切换），
都容易踩这个 —— 而且踩了之后**看起来像产品 bug**，会白白去查产品代码。

## 十一、Agent 编排

### 十一-1 长任务必须用常驻队友，不能用一次性 subagent

**踩坑（2026-10-02）**：派了一个 `subagent` 去做「下载界面重做」，
它**中途失败且没留下任何结论**，等于白跑一轮。想唤醒它继续，系统直接回：

```
Error: active teammate "51d6042f-..." not found
```

**根因**：手上有两种派活方式，性质完全不同：

| 工具 | 性质 | 失败后能否续 |
|---|---|---|
| `subagent` | **一次性**，跑完/失败即销毁 | ❌ 不能，中间成果全部蒸发 |
| `spawn_teammate` | **常驻**，有信箱，可反复发消息 | ✅ 能，发条消息就接着干 |

**规则**：
- **长任务 / 多步任务 / 有失败风险的任务 → 一律 `spawn_teammate`。**
- `subagent` 只用于**短、独立、失败也无所谓**的查询（比如"读一下这个文件告诉我结论"）。
- 派活前先问自己：**"如果它中途挂了，我愿不愿意从头再来一遍？"** 不愿意就用常驻队友。

**用户当时就指出过这点**（原话：「按理来说，我给他发消息，再让他继续，他还会继续，
而不是需要重新的研究」）—— **用户是对的，是选型错了，不是工具的局限。**

### 十一-2 「常驻」只解决"能不能续"，**真正保命的是状态落在磁盘上**（2026-10-02 实测）

用户问过：「如果是让其他东西帮助你干活，你用那种即便死了也能重新继续工作的」。
十一-1 说选 `spawn_teammate` —— 对，但**只做对了一半**。今晚补上了后半句。

#### 实测：常驻队友确实能续跑

`audit-code-health` 做 DFW-101 时中转站连挂两次。处理与结果：

| 动作 | 结果 |
|---|---|
| `list_agents` 看状态 | 是 `inactive` **不是被删除** —— 成员还在 |
| `send_message` 发一条 | 立刻变 `running`，**从断点继续，没有从头来** |

**结论：`spawn_teammate` 的续跑能力是真的，已实测。**

#### 但「续跑」不等于「接着做对」——三层保险缺一不可

队友复活后，它**脑子里的细节可能已经不可靠**了（尤其最后那轮它还按我的错误建议改坏了代码）。
真正救回这份工作的是**磁盘**，不是"它还记得"：

| 层 | 做法 | 今晚的效果 |
|---|---|---|
| 1 | 常驻队友（可叫醒） | 生效 —— 它确实醒了 |
| 2 | **状态落磁盘**：改动随时在 git 工作区/提交里，不藏在智能体脑子里 | 生效 |
| 3 | **动手前先另存一份仓库外备份** + 任务卡里写进度检查点 | **救命的一层** |

第 3 层是今晚真正的功臣：队友断线前把两个测试文件改成了
`Unresolved reference 'shadowOf' / 'Looper'`（**编译不过**），
工作区是坏的。**是那份备份把编译得过的版本还原回来的。**

**规则**：
- 把活派出去**之前**，先 `cp` 一份当前状态到仓库外（本项目：`~/heiyao/_wip检查点/<卡号>-<时间戳>/`）
- 队友一断，立刻在任务卡写一条「进度检查点」：做完什么、卡在哪、下一步、怎么恢复
- **不要指望"它复活了所以它记得"** —— 复活的是成员，不是记忆

#### 接手一个断线队友的半成品，第一步永远是「先编译、先跑测试」

不要先读它的代码、不要先猜它想干什么。先回答一个问题：
**现在这个工作区是好的还是坏的？**

```bash
./gradlew :app:compileEmptyDebugUnitTestKotlin   # 编译过不过
./gradlew :app:testEmptyDebugUnitTest            # 测试什么状态
```

- **过** → 收尾（跑全量、修剩余失败、提交）
- **不过** → 从备份还原，再决定是收拾还是重做

今晚实测：队友留下的状态**编译不过**；还原后立刻 `BUILD SUCCESSFUL`。

#### ⚠️ Lead 的判断也要先核实，别让队友替你执行错误

今晚我犯的错：把 `OutOfMemoryError: unable to create native thread`
**误判成"测试写法不对"**，还发消息让队友把测试改成 `Thread.currentThread()`。
队友照做了 —— 幸好我及时发现自己错了、拦住并还原。

**这个坑项目记忆里早就记着**（见本文件第九节第 1 条），我只是**没先查**。

**规则**：
- 下判断前**先查本项目 lessons**（尤其构建/测试类故障，第九节几乎都有）
- 发给队友的"修复建议"如果基于猜测，**务必带上"先验证再动手"**，
  别让它直接改代码
- 队友照做不是它的错 —— **是 Lead 没核实**

---

---

## 十、版本号方案（DFW-118，2026-10-02 路线 B）

### 十-1 手写计数器必然出错，别再用

**旧方案**：`val buildCode = 10034` 一个手写计数器 + 固定 `versionName = "1.0.0"`。
**实际后果（DFW-117）**：后台发布记录填了 `10028`，而用户手机里装着 `10034`。
`UpdatePromptPolicy.java:23-25` 是 **versionCode 优先比较**，10028 < 10034
→ 软件判定"这更新比我还旧" → **更新弹窗永远不出现**，而界面上完全看不出来。

**现方案（路线 B）**：`val appVersionName = "x.y.z"` 是唯一可改的数字，
`versionCode = major*10000 + minor*100 + patch` 由它推导。
"忘改序号"和"序号填得比用户装的还小"**在物理上不可能再发生**。

**推广的教训**：凡是"两个值必须保持一致、但各自手写"的设计，
早晚会不一致，而且**不一致时往往不报错**（这才是最贵的）。
能推导就推导，不能推导就加守卫测试。

### 十-2 换编号方案那一次，链路上的"只增不减"守卫会把你堵死

`versionCode 只增不减` 这个守卫在**两处**独立存在，都是对的、平时都必须留：

| 位置 | 形态 |
|---|---|
| `server/dfwx-pb/set-release.py` | `if code < current: 拒绝` |
| `server/dfwx-admin/index.html` | `if (nextCode <= currentCode) throw` |

> ⚠️ **[2026-10-03 更正] 上表第二行已经过时。** 用户 2026-10-03 明确要求控制台"别拦我，
> 版本号我自己填"，那个 `throw`（`guardVersionTransition`）**已被删除**，
> 现在控制台对"相等"和"变小"都只给一句警告（`versionTransitionWarning`），点了就发。
>
> 于是两条路变成**不对称**：CLI 更严（变小硬拒，除非 `ALLOW_DOWNGRADE=1`），
> 控制台更松（只警告）。**这个不对称是有意保留的**（用户要自己测后台推送能力），
> 但它意味着**网页上可以静默发出降级版本** —— 风险登记在 `docs/plan/publish-chain-audit.md`。
>
> **教训（又一条"文档与代码脱节"）**：删掉一个函数时，引用它的注释/文档不会自己更新。
> 本项目已有三处引用同一个已删函数（本表、`set-release.py`、`publish-chain-audit.md`）。
> **删函数前先 `grep -rn "<函数名>"`，把引用一起改掉。**

但**换编号方案时第一版必然是"回退"**（旧计数器 10034 → 新方案 1.0.0 = 10000）。
堵死的结果只有一个：**去手改 PocketBase** —— 那才是真正危险的操作。

**正确做法**：默认仍拦下 + 显式开关放行 + **把后果打在日志/弹窗里**（不静默）。
- 脚本侧：`ALLOW_DOWNGRADE=1`
- 控制台侧：`confirm()` 写明"装了旧版的用户收不到更新提示，必须手动卸载重装一次"
  （**2026-10-03 起控制台改成只警告不拦**，见上面的更正框）

**别做**：直接删掉守卫（平时就没人拦了），或静默放行（用户不知道代价）。

**注意区分「相等」和「变小」**：相等要**硬拦**——那是 DFW-82 的老病
（静默发出去 = 用户永远"已是最新版本"）；只有变小才需要确认。

### 十-3 改了 `val xxx` 这种唯一来源，记得全链路 grep 一遍

删掉 `val buildCode` 后，**四个地方**会静默或半静默地坏掉：

1. `tools/bump-version.sh` — grep 不到 → `exit 1`
2. `tools/release.sh` — grep 不到 → 发版校验失败
3. `VersionSchemeJvmTest` — 断言 `>= 10027` 是计数器时代的产物，新方案 10000 会**误伤**
4. `VersionNumberSinglePlaceJvmTest` / `DocTimelinessJvmTest` — `Regex(...).find()!!` → **NPE**

第 3 条最阴：它**不是**"旧规则作废"，而是"旧规则里混进了一个和新方案冲突的魔数"。
改方案时要把守卫测试逐条读一遍，问"这条守的是规则，还是守的是当时的那个值？"

**命令**：`grep -rn "<旧标识符>" --include=*.kt --include=*.sh --include=*.java --include=*.kts .`

### 十-4 服务器上的口令副本会静默过期

`/root/.dfwx-pb-admin-pass` 存的是 PocketBase 超级管理员口令的副本。
用户"找不到用户名"那次重置过口令，**但没人想到去同步这个文件** ——
于是 `tools/dfwx-publish-apk.sh` 的同步步骤一直以 `HTTP 400` 失败：
**APK 传上去了、release 记录没更新**。

这正是 DFW-82 的形态：用户能下到新包，但软件永远不提示更新，
而日志里只有一段看不懂的 `urllib` 堆栈，很容易被当成"网络抖动"忽略。

**规则**：**凡是口令/密钥在服务器上有第二份副本的，重置时把它列进清单一起改。**
改完立刻验证一次鉴权（不要只看"文件写进去了"）。

**顺带一个 shell 坑**：`sudo wc -c < /root/file` 的重定向由**非 root 的 shell** 执行，
会 `Permission denied`。要写 `sudo wc -c /root/file`（或 `sudo cat`）。

---

## 十二、2026-10-02 交付批次（DFW-90/91/95/96/97/103）的教训

> 来源：任务面板卡 DFW-90 / DFW-91 / DFW-94 / DFW-95 / DFW-96（**卡号**；标题里写的是
> DFW-91/92/95/96/97，编号错位是历史遗留，见 `handover/README.md` §4 的提醒），
> 以及 DFW-103「公告弹窗秒弹」——最后这一条**在任务面板里没有独立卡**，
> 证据只有交付文档 `docs/plan/dfw-90-95-96-103-batch.md` §④ 与提交 `02506e5`（**卡里信息有限**）。
> 本节只写"以后能复用"的部分，不复述任务内容。

### 十二-1 "版本号只留一处"要解决的是漏改，不是排版（DFW-91）

**坑**：用户可见的版本号散在 4 处（设置页、关于页底部、维护页底部、"已是最新版本"），
构建文件里还要手改两行（`10023` 与 `"1.0.23"`）。用户原话点破了要害：
「**我主要是想减轻你的维护负担，别忘记改某些地方的版本号**」。

**根因**：**漏改的代价不对称**。改错了用户看得见、报错也看得见；
改漏 `versionCode` 是"用户永远收不到更新"，**界面上完全看不出来**。

**以后怎么做**：
1. 一个数字做唯一来源（`val buildCode`，后来演化为 `val appVersionName` 由它推导 `versionCode`）；
2. 文档从"不同步就报错"改成**自动写入**——靠人记得改文档不可靠，上一窗口就真漏过一次；
3. 加守卫 `VersionNumberSinglePlaceJvmTest`，**且验过鉴别力**（别处再渲染版本号 → 红）。

**通用规则**：凡是"需要人记得改"的地方，改造成**机器自动写**或**守卫测试会红**，别靠纪律。

### 十二-2 "门禁"与"发布"之间只能有 `&&`；管道会吃掉退出码（DFW-91，v1.0.15 作废）

**坑（真实损失）**：v1.0.15 用 `;` 而不是 `&&` 串联「跑门禁」和「提交/构建/发布」，
**门禁红了还是发出去了**，整个版本作废（`7044bff` 提交信息原话：
「发布一个门禁红的版本本身就是错的」）。

**二次坑（差点再犯）**：`bash gate.sh unit | tail -4 && build` —— `&&` 看到的是 **`tail` 的退出码**，
不是 `gate.sh` 的。门禁红照样过。

**以后怎么做**：`release.sh build` 内部强制先跑 `verify`（把正确顺序固化成代码，不靠人记）；
要么不加管道，要么脚本开头 `set -o pipefail`。

### 十二-3 `$变量` 紧跟中文全角字符会吞掉变量名 —— 且这类 bug 只在出错分支才执行（DFW-91）

**坑**：shell 里写 `"$name（自动写入）"`，bash 把中文字节当成变量名的一部分，
报 `name?: unbound variable`。本次 `tools/release.sh:78` **实际踩到**，`verify` 直接崩。
另两处（`tools/ci-gate.sh:113` 的 `$bad（`、`tools/dfwx-publish-apk.sh:40` 的 `$REMOTE_SHA）`）
**只在门禁报错 / sha256 不一致时才执行** —— 也就是"出错时反而崩"，把原本要看的错误盖掉。

**以后怎么做**：变量一律写 `${变量}`；加守卫 `shellScriptsNeverGlueAVariableToChineseText` 防复发。

**通用规则**：**只在错误分支执行的代码，日常路径永远测不到**——所以它们最容易带着 bug 躺很久。
写错误处理时，要么在本地故意触发一次，要么至少逐字符检查一遍。

### 十二-4 macOS 是 BSD sed：替换用 `\1`，不是 `\g<1>`；改文件前先备份（DFW-91）

**坑**：用 `\g<1>` 写 BSD sed 的替换，把 `docs/plan/current-state.md` 写坏成
`g<1>10022g<2>1.0.22g<3>（...`，靠备份才还原。

**以后怎么做**：脚本要就地改文件时**先备份**；sed 用 `\1`/`\2`；改完立刻回读确认（不要只看退出码）。

### 十二-5 删一条校验前，先问"它挡住的是哪一类真实故障"（DFW-90）

**坑**：后台改版本号推不动更新，因为装包前有一道「后台版本名必须和包里一字不差」的校验。

**关键判断**：四道校验里该删的只有一条，其余必须留：

| 校验 | 决定 | 理由（这是重点，不是结论） |
|---|---|---|
| 包名一致 | 留 | 确认这确实是《东方无限》 |
| **签名一致** | **必须留** | 后台走明文 http，中间人能改下载地址但**改不了签名** —— 这是真正的防线 |
| 版本名一字不差 | **删** | 纯维护负担 |
| 真实版本序号更大 | 留 | 后台版本号只决定"要不要提醒"，用户真装的还是那个包；包不比当前新 → 装完版本没变 → 后台还提示有新版本 → **无限循环提示** |

顺带：版本名格式校验从"严格 `x.y.z`"放宽成"非空 + 过滤非法字符"，
因为**后台控制台没有格式校验**——用户填个 `1.0.24-beta` 会让所有用户收不到更新，
而报错只有一句"版本信息无效"，极难排查。

### 十二-6 视觉/布局问题"先量再改"，并把排除掉的假设也写下来（DFW-94）

**坑**：搜索页底部有 83dp 黑留白。两个"看起来很有道理"的假设**实测都不成立**：
① 窗口化渲染被截断（实测滚到底时渲染格数正常增长）；② 那块是可见色带（三层容器都没设背景色）。

**做法**：用 `@GraphicsMode(NATIVE)` 把页面渲染成 PNG、**逐行扫像素**定位
（改前结果区 y=2119 结束，下方 218px 恒空），才找到真根因：
`homeHistoryH()` 写 `vh - dp(64)`，而注释里的"64 = 框52 + 间距12"是**兄弟节点**的尺寸
（实测 框 0..137px、间距 137..169px，`homeScroll` 从 169px 才开始），本来就在滚动区外面——
**再扣一次 = 在底部永久挖掉 64dp**。

**以后怎么做**：布局/视觉问题**先量再改**；把**排除掉的错误假设**也写进文档，免得后人重走一遍。

### 十二-7 提速前先查"这份预算被谁共用"，并且不要砍用户自己设的值（DFW-96）

**坑**：全局搜索"跑满 3 分钟"是常态。根因是全局搜索**复用了单源搜索的设置**
`sessionSearchMaxPages`，而默认值 `0` 被当成 **1000 页**（`LanzouCore.java:393`），
再叠上同域名每页 ≥1100ms 与文件夹递归，直接吃满 180 秒预算（`:86`）。

**关键克制**：**没有把单源搜索一起砍**。那个设置在界面上叫「单源翻页」，
是用户自己主动缩小的范围；用户抱怨的是全局搜索，一起砍就是**用户没要求过的功能回退**。
拆成两个独立预算（`SEARCH_MAX_PAGES_GLOBAL=3` 只管全局）。

**以后怎么做**：优化性能先分清"哪个入口共用同一份预算"；砍预算前确认砍的不是用户自己设的值。

### 十二-8 缓存要存"原始数据"而不是"解析后的对象"，并复用同一套解析器（DFW-103 公告弹窗秒弹）

**坑**：公告弹窗启动时**先发完整网络请求、拿到数据才弹**，且没有任何本地缓存，
超时 6s(连接)+8s(读取) —— 最坏 **14 秒**。

**做法**：把上一次成功拉到的四个集合的**原始 JSON** 存盘，启动先用它渲染弹窗（零延迟），
网络回来再覆盖。**存原始 JSON 而不是解析后的对象**，是为了还原时**复用同一套解析器**——
否则会出现"缓存格式和线上格式不一致"这种**只在离线路径才炸**的坑。

**第二个坑**：启动现在会两次走到弹窗逻辑（先缓存、后网络），
**必须按 id 去重**，否则用户看到两个叠在一起的公告对话框。

**通用规则**：凡"缓存 + 线上"两路数据汇合的地方，一律**缓存原始形态 + 复用同一解析器 + 按 id 幂等去重**。
（出处：`docs/plan/dfw-90-95-96-103-batch.md` §④，无独立任务卡）

### 十二-9 空 `catch` 会把你自己的低级错误变成"玄学"（DFW-103 公告）

**坑**：第一版把 `sink.notice` 直接传进 `parseNotices(...)`。正常路径是 `fetch()` → `fetch(null)`，
此时 `sink` 是 null → **NPE**，而它被外面那圈 `catch (Exception ignored)` **静默吞掉**——
表现为"**公告和更新记录永远拉不到，日志里一个字都没有**"。

**以后怎么做**：需要兜底的解析/网络调用用**局部变量接住**可疑的 null；
加回归守卫 `fetchMustNotDereferenceTheNullableSink`。
写新代码时顺手想一遍"它最可能在哪报 NPE"，比事后加日志便宜。

**通用规则**：`catch (Exception ignored) {}` 是"把 bug 变成灵异事件"最快的办法。
要吞异常，至少留一行原因注释 + 一个 `Log.w`。

### 十二-10 给第三方网页换肤：改"计算样式"，不是"猜类名"；只动颜色不动交互（DFW-95）

**坑**：第一版用 `[class*="flowus"]` + 把 `body` 刷黑、文字刷浅，
**真机渲染出来是"白底浅字、几乎看不见"，比不换肤还糟** ——
真正承载白底的是**内层容器**，刷 `body` 根本管不到它。

**做法**：遍历 DOM，读每个元素**真实的**计算样式（`background-color` / `color` / `border-color`），
按明度做重映射（接近白的底 → 深色、接近黑的字 → 浅色、饱和亮蓝 → 品牌紫），
用 `MutationObserver` 跟上 SPA 的动态重建。实测：换肤前 `dark=3/light=277`，换肤后 `dark=277/light=3`。

**三条铁律**（换任何第三方页面都适用）：
1. **只改颜色与可见性，绝不改布局**（`display` 是唯一例外，用来隐藏对方自己的顶栏/底栏）；
2. **绝不碰 `pointer-events`** —— 碰了用户就点不动表单（最容易"为了好看把功能弄坏"）；
3. **不做"猜着点"** —— 只在确实找到目标文字且元素真实可见时才点，脚本整体包 try，失败只是不换肤。

**另外**：自动化做不到的事（FlowUs 视图切换被一层全屏遮罩吞掉合成点击），
**如实登记比写一段时灵时不灵的代码更好**——`dfw-97-feedback-page.md` §五就是这么处理的。

