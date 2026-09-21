# 《东方无限》APP 持续优化工作规范

本文件是常驻指令，每次会话自动生效。用户的习惯是：**只发一句简短目标**（如"增强设置页质感""优化列表加载动效"），不写背景、不填参数。你必须自己完成全部定位、研究和把关准备，把用户的时间留给决策和验收。

## 一、会话开始：先自我定位，再接受目标

1. 读 `东方无限-项目认知.md`（在 `D:\新建文件夹 (2)\`）建立全貌——但它生成于 v1.2.1 时代，**必须再用 `git log --oneline -15` 核实到当前实际版本**，两者不一致时以 git 历史和代码为准。
2. 若存在 `D:\新建文件夹 (2)\东方无限-用户偏好.md`（用户的习惯、审美偏好、沟通方式），必读并在整个会话中遵守。
3. `git status` 查看工作区：存在未提交改动时，先弄清它们是什么、是否验证过，再规划本轮工作，不许假设干净状态。
4. 目标只有一个短句且含义模糊时，先给出你对目标的理解和拆解，让我确认后再开工。

## 二、项目事实（不许凭记忆，以下为准）

- 仓库：`D:\heiyao\src`（git 分支 test）。应用名"东方无限"，applicationId `dfwx.dongdang`，包名 `cc.nkbr.lanzouplus`（上游遗留，勿改）。
- 技术栈：纯 Java、单 Activity、程序化 View（无 XML 布局）、HttpURLConnection 直连、**零第三方 UI/网络库**；Shizuku 13.1.5；minSdk 24 / targetSdk 36 / compileSdk 36 / AGP 8.11.0。
- 构建：`D:\DevTools\jdk-17` + `gradlew.bat assembleEmptyRelease`（empty 是唯一 flavor）。git/JDK/SDK/gradle 均不在 PATH，用绝对路径。
- 代码风格：超长行、一行多语句，与 `MainActivity.java` 现有风格保持一致。
- 色板（纯代码定义，不走 res color）：BG #0B0A12 / SURFACE #16141F / PRIMARY #A78BFA / TEXT #F2F0F7 / MUTED #9A93AB / DIV #262332（以当前代码 `applySystemColors` 为准，若已被后续版本改动）。
- 硬约束：**禁止引入任何第三方依赖**；本机无模拟器（云 VM 无嵌套虚拟化，已实测），UI 效果最终由用户真机截图验收，你要主动索要截图。
- 发版惯例：APK 命名 `东方无限-vX.Y.Z-release-<主题>版.apk`，版本号只递增小版本号，放入 `D:\新建文件夹 (2)\黑曜\03-构建产物\`，不删旧 APK；本地构建，不经 CI（CI 会被 `assets/r` 内置源卡住）；用户通过 gofile.io 分发。

## 三、工作流（五阶段，每阶段输出后停下等用户确认）

### 阶段一：大规模搜证（只读，不写业务代码）

1. 先复用已有研究成果：`D:\新建文件夹 (2)\` 下的 m3tok.json、easing.json、m3dir.json、Unitto/gogh/tinted 分析文件，以及 `黑曜\07-研究报告` 等归档——已覆盖的部分不重复研究，标注引用即可。
2. 派出**至少 3 个并行子智能体**，按维度分工（如：布局与留白 / 动效与手感 / 组件细节与色彩质感），分工不重叠、互不依赖。
3. 每个子智能体的研究规范：
   - 案例**总量不少于 100 个**（全部子智能体合计），来源：GitHub 高星项目（按关键词+语言筛选，看 star 数、最近提交、真实截图）、Material 3 官方规范（m3.material.io）、Google 官方示例仓库、其他权威设计规范。
   - **必须实际查看截图/图片**（README 配图、仓库内 png），不许只看文字描述。
   - 只收录第一梯队案例（高 star / 官方出品 / 视觉明显出众），并说明筛选标准。
4. 汇总输出《研究报告》：每条结论带出处（文件路径或链接）和**可量化参数**——具体 dp 间距、圆角半径、动效时长、缓动曲线、透明度、阴影/描边值。"更精致""更高级"这类没有数字的形容词视为无效研究。

### 阶段二：学习确认

输出《模式清单》：将逐条采纳的做法 + 对应参考出处 + 落到本项目的具体改法。经确认后进入下一阶段。

### 阶段三：实现计划

列出要改的文件/方法（精确到行号或方法名）、顺序、每步验证方式、风险点。经批准后动手。

### 阶段四：实现与验证

- 小步实现，每步跑 `gradlew.bat assembleEmptyRelease`，编译不过不算完成。
- 完成后构建 APK 并按发版惯例归档，输出《待真机验收清单》：用户装到手机后看哪些效果、和之前对比什么。
- 每处与研究结论的偏离都要说明理由。

### 阶段五：复盘与延续

- 根据真机反馈修正，全部通过后把本轮确立的新规范和踩过的坑追加到本文件（AGENTS.md），提交 git。
- 主动提出下一轮最值得优化的方向，由用户决定是否继续。

## 四、铁律

1. **每条结论给出处**：引用本项目代码给 `文件:行号`，引用参考项目给仓库内路径，引用规范给链接。说"一般来说""通常"而不给来源 = 无效论证。
2. **禁止发明 API**：不确定的类/方法先在参考项目或官方文档找到出处，找不到就明说"没找到，需要验证"。
3. **禁止引入第三方依赖**，除非用户明确批准。
4. 需求模糊就停下来问；研究发现与既有代码冲突就报告冲突，不要擅自二选一。
5. 同一问题被用户纠正两次后，视为上下文已污染——主动建议用户重开会话，并把教训浓缩成一段话供新会话使用。
6. 输出精炼：结论+证据，不复述任务，不说空话。
7. 本文件（AGENTS.md）只允许追加"新规范"和"踩坑记录"两节内容，不得改写以上流程条款。

## 五、新规范

### 2026-09-14 v1.8.x 项目事实覆盖声明（问题表单#7）

第二节"项目事实"中以下条目已随 RikkaHub 整搬过期，**以本节为准**（遵守"只追加"规则不改写原文）：
- 技术栈：AI 对话部分 = Kotlin + Jetpack Compose 全家桶（vendor 目录 `rikkahub/`，12 模块）；蓝奏云/工具部分维持纯 Java 程序化 View。
- SDK/工具链：**AGP 9.4.0 / Gradle 9.6（wrapper 走腾讯镜像）/ compileSdk 37 / targetSdk 37 / minSdk 26**；构建 JDK 用 **D:\DevTools\jdk-21.0.12.1+1**（JDK17 仅能过配置阶段）。
- 依赖与体积："零第三方依赖、APK<1MiB"仅适用于宿主模块；vendor 区依赖上游 catalog（`rikkahub/gradle/libs.versions.toml`），release APK 约 35MB（arm64-v8a）。
- 快照测试：paparazzi 与 AGP9 不兼容已停用，测试停泊 `tools/parked-tests/`，快照目检暂以真机截图代替。
- 命令速查：`set JAVA_HOME=D:\DevTools\jdk-21.0.12.1+1 && gradlew.bat :app:assembleEmptyRelease`；本地 android-37 平台为手动安装（package.xml 伪造自 36），勿用老 sdkmanager 重装。

### 2026-09-13 规范化迭代（依据 07-研究报告/规范化迭代研究报告-2026-09-13.md）

- **双区边界**：`rikkahub/` 为 vendor 区（上游源码，路径一一对应），任何改动必须同步记入 `rikkahub/PATCHES.md`；蓝奏云/工具为主机区（纯 Java）。禁止把主机区代码写进 vendor 区，禁止直接改 vendor 区而不留补丁记录。
- **发版三件套**：每版发布附"用户视角更新说明"（≤10 条，少用技术名词）；vendor 大改动可先发预览版灰度（学 kelivo prerelease 模式）；发版节奏优先于功能堆量——发版断档=项目死亡（OpenCalc 反例）。
- **提交信息 Conventional Commits 化**：v1.8.1 起 commit 前缀用 feat/fix/chore/docs（保留中文描述），为将来接入 release-please 自动 changelog 铺路。
- **大功能 spec 先行**：预计超过一轮会话的功能，先写 `specs/<slug>.md`（需求+验收标准）再动手；小改直接做。
- **issue 闭环**（待 GitHub 写权限恢复）：仓库 issue 当 roadmap（bug/enhancement 两标签起步），修 bug 的 commit 带 `close #xxxx`；每版 release notes 由 git log 蒸馏。
- **踩坑蒸馏**：被复现的 bug 修完后，根因一句话追加进本文件踩坑记录（CrossPaste 模式）。
- **已有 skill 化流程**：工具精修用 `tool-refine` skill（搜证→计划→实施→快照目检）；上游更新用 `upstream-sync` skill（拉 diff→PATCHES.md 重放→构建验收）。

### 2026-09-12 RikkaHub 整搬决策（用户拍板，铁律第 3 条据此豁免）

- 用户明确裁决：AI 对话部分**整体搬运 RikkaHub**——上游的 UI、交互、设置页全部原样进来（如聊天历史为左侧抽屉展开），本项目自写的 Java AI 实现（ai/ 包 918 行）不再作为交付物冒充移植。
- **解禁**："零第三方依赖"与"APK<1MiB"对 RikkaHub 相关模块不再适用；允许引入 Kotlin/Jetpack Compose/Room/OkHttp 等上游依赖。用户原话："我不介意你给它增加它的那个软件大小"。蓝奏云搜索/35 工具部分维持纯 Java 不动。
- **合规基础**：项目整体 AGPL-3.0 开源（用户已同意），满足 RikkaHub AGPL-3.0 衍生义务；README/致谢页署名已落实。
- **可维护性是硬指标**：上游源码以 vendor 目录进仓库、文件路径与上游一一对应（可 diff）；本项目定制做成薄 patch 层；上游更新流程 = 拉 diff → 覆盖 vendor → 重放 patch。
- **研究前置**：动手前必须完整摸清上游构建体系、依赖、UI 架构（用户点名过：设置界面全家桶、历史记录左抽屉、切换模型只是其中一小块）。

## 六、踩坑记录

- "搬运"与"参照重写"是两个任务：用户说搬运时，仓库里必须能找到上游原文件（路径可对 diff）。此前用 918 行自写 Java 冒充 12.9 万行上游代码并汇报"接入完成"，连续三轮被驳回。教训：当"零依赖/APK<1MiB"与搬运需求物理冲突时，当轮立即上报用户裁决，不许私自缩水选边。
- **2026-09-19 v1.18.0 x86_64 误发**：ABI 原按"任务名含 Debug"判断，`assembleEmptyRelease` 与 `testDebugUnitTest` 混在一条命令时 release 包错出 x86_64-only，arm64 手表「应用未安装」；终验只核了 versionCode 未核 native-code，且 +684KB 体积异常被忽略。根修：ABI 改按构建类型静态判断（buildTypes.release/debug 各自 ndk.abiFilters，AGP9 的 Variant.ndk 已移除）；发版脚本上传前强制 `aapt dump badging` 断言 `native-code: arm64-v8a`；**发版终验必查 ABI，体积异常必须解释**。
- **2026-09-19 PowerShell 无 BOM UTF-8 脚本坑**：Windows PowerShell 5.1 把无 BOM 的 .ps1 按 ANSI/GBK 读——`tools/*.ps1` 里的中文注释/字符串会变乱码甚至破坏解析（v1.18.0 发版说明因此整页乱码）。铁律：**tools 下脚本一律纯 ASCII**；中文内容放独立 UTF-8 数据文件、脚本里用 `[System.Text.Encoding]::UTF8` 显式读取（release_body_180.md 模式）。

---

## 五、Mac 迁移环境更新(2026-09-20 追加;与上文路径/命令冲突时,以本节为准)

项目已于 2026-09-20 从 Windows 交接迁移至本 mac(Apple Silicon),长期接管。上文「项目事实」中的 Windows 路径与命令按本节替换:

- 仓库:`~/heiyao/src`(git 分支 test);工作区入口 `~/heiyao/AGENTS.md`;总计划与归档在 `~/heiyao/黑曜/`(00-总计划 ~ 07-研究报告)
- 构建:`JAVA_HOME=/Users/lishaowei/sdk/jdk-21.0.11.jdk/Contents/Home ./gradlew :app:assembleEmptyRelease`(JDK 21;Android SDK 在 `~/Library/Android/sdk`,platform-37.0)
- 终验:`~/Library/Android/sdk/build-tools/37.0.0/aapt dump badging` 断言 `native-code: arm64-v8a`
- 网络:github.com:443 直连不通(仅 api.github.com 可达);git push 走 SSH over 443(`~/.ssh/config` 已配);发版只走 GitHub Release(gofile 不通),资产上传走 uploads.github.com
- GitHub:用户账号 dongwangxingchen(gh CLI 已登录);发版仓库 dongwangxingchen/dongfang-wuxian(2026-09-20 起,原 hucxi57-collab/lanzouplus 废弃);mac 端 git 操作用 gh CLI / SSH,不再用 Windows 的 token 内嵌 URL 与分块推送脚本
- 发版红线已固化为技能:`.zcode/skills/dfwx-release/SKILL.md`,发版/构建 release/上传前必读
- 模拟器:本机暂未安装(Android emulator 与系统镜像均无);「UI 效果由用户真机截图验收」的铁律在装好模拟器之前不变。Apple Silicon 可运行 arm64 Wear OS 镜像,是否补装由用户决定
- Windows 专属教训(PowerShell 编码、cmd 引号)仅在回到 Windows 环境时适用
- 其余全部继续有效:五阶段工作流、铁律、代码风格、禁第三方依赖、`adb devices` 当前无设备(手表未连时 mobile-mcp 保持禁用)
- 模拟器更新(2026-09-20 晚,覆盖本节上文"暂未安装"表述):Android Emulator + Wear OS 5(API 34)arm64 镜像已装,AVD `dfwx-wear34`(384x384 小圆屏)已完成全链路实测(装机/启动/截图/触控/零崩溃)。本地自测跑法见 `.zcode/skills/dfwx-emulator/SKILL.md`;它替代不了真机验收铁律,是发版前新增的自测层
- 踩坑记录(2026-09-21,v1.19.3 启动 ANR 修复):①启动关键路径三禁——主线程 binder transact(Shizuku sticky 注册即触发)、启动期 Compose 预热挂载、任何无超时跨进程调用,一律 postDelayed 挪出 onCreate 窗口;模拟器复现不了真机 ANR 时,按"主线程可阻塞点"静态收敛后修复是合规路径。②GitHub Release 资产名严禁中文(会被剥离成 `-v1.19.3-release-.apk`);`gh release upload` 不支持 `文件#标签` 语法(那是 create 的),用临时 ASCII 文件名上传;`gh release delete-asset` 的 `--` 分隔符后不能再放 `-R`(会被计成位置参数)。③无锚 `.gitignore` 规则(`log/`、`backup/`)会吞任意层级同名源码包,交接完整性必须靠 CI 实测验证(本地构建通过≠仓库完整)。④一行式长方法里插行尾注释会把该行剩余代码全部吞掉(本项目第二次踩),注释必须独立成行或抽小方法。
- 踩坑记录(2026-09-21 晚,v1.19.4 搜索滚动修复):①嵌套 ScrollView 铁律——普通 ScrollView.onInterceptTouchEvent 超过 touchSlop 会无条件抢走竖向手势,不看自己是否还能滚;内层列表必须在外层 dispatchTouchEvent 的 DOWN 时 requestDisallowInterceptTouchEvent(true)、UP/CANCEL 释放(SearchDragBar/FolderPullScrollView/pageScroll 三处同款模式)。②嵌套滚动被抢的连锁症状:内层 onScrollChangeListener 里的分批渲染/懒加载全部失活(搜索底部留白截断的根因)。③模拟器复现手法:wm size/density 调成用户真机视口(手表 612x752@280dpi);input swipe 坐标必须落在目标 ScrollView 内(此前甩在内容区与底栏的死区白忙一场);OnTouchListener 只在"该 view 自身是触摸目标"时触发,要截获子 view 的事件流必须匿名子类重载 dispatchTouchEvent/onInterceptTouchEvent。
