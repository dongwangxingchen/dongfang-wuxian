# 《东方无限》APP 持续优化工作规范

> 2026-09-25 瘦身版（用户授权："只保留有用的部分"）。历史规范/踩坑/原始工作流全文在
> `docs/agents/lessons.md`，按第四节索引按需读，不默认加载。用户原话："测试期版本号
> 一直递增只为覆盖安装，最终发布重置 1.0.0"。

## 一、会话开始

1. ⚠️ **不要再把 `~/heiyao/东方无限-项目认知.md` 当事实源**（DFW-5）：
   该文件停更于 **v1.2.1 / 2026-09-09**，内含 **Windows 路径**（`D:\heiyao\src`）、旧分支与 commit、
   minSdk 24 / compileSdk 36 / AGP 8.11、模拟器路线，以及**已被移除的"签名口令明文写在源码里"**
   （v1.9.0 起口令只在 `local.properties`，源码里没有）。照它建立全貌会**被带偏**（本窗口已实际踩到）。
   它现在只作**历史背景**。**当前事实一律以 `docs/plan/current-state.md` 为准**，并配合
   `git log --oneline -15` 核时效。
2. 读 `~/heiyao/东方无限-用户偏好.md`（用户习惯、审美偏好、沟通规则），全程遵守。
3. `git status` 查工作区：存在未提交改动先弄清是什么、是否验证过，不假设干净。
4. 目标只有一个短句且含义模糊时，先给出理解和拆解，确认后再开工。
5. 做任何"可能已有前人成果"的东西（审美/组件/方案）：先查本地档案（黑曜/06 参考库→
   05 调研报告→spec）→结构化工具（codebase-memory/context7/gh）→最后才外网，有现成好
   方案就取用，不自己造轮子。

## 二、项目事实（Mac 现行版，不许凭记忆）

- 仓库：`~/heiyao/src`（git 分支 test）。applicationId `dfwx.dongdang`，包名 `cc.nkbr.lanzouplus`（上游遗留，勿改）。
- 双区结构：`rikkahub/` 是 vendor 区（RikkaHub 上游源码 + DFWX PATCH 薄层，改动必须记 `rikkahub/PATCHES.md`）；其余为主机区（纯 Java 程序化 View，无 XML 布局）。AI 对话 = Kotlin + Compose；蓝奏云/工具 = 纯 Java。
- 构建：`JAVA_HOME=/Users/lishaowei/sdk/jdk-21.0.11.jdk/Contents/Home ~/heiyao/src/gradlew -p ~/heiyao/src :app:assembleEmptyRelease`；日常迭代调试用 `assembleDebug`；**严禁混入任何含 "Debug" 字样的任务名执行 release 构建**（v1.18.0 ABI 翻转事故）。compileSdk 37 / minSdk 26。
- 终验：`~/Library/Android/sdk/build-tools/37.0.0/aapt dump badging` 断言 `native-code: arm64-v8a`。
- 自测环境：**AVD `dfwx-phone34` 已于 2026-09-25 删除**（本机 16GB 内存不足），现行唯一自测层 = **真机 vivo V2425A**（无线 adb `192.168.1.133:5555`，Android 16 / SM8650），跑法见技能 `dfwx-emulator`；**只测手机，手表永久停测**；设备一律装 release 包（debug 只含 x86_64，真机 arm64 装不上）；启动用 `am start -n dfwx.dongdang/cc.nkbr.lanzouplus.MainActivity`。gradle 与设备截图严禁并行（CPU 饥饿会让 SystemUI ANR 污染证据）。
- 发布：GitHub Release `dongwangxingchen/dongfang-wuxian`，标题=纯 `vX.Y.Z`，资产=`dongfang-wuxian-vX.Y.Z.apk`，**禁任何描述性后缀**；归档到 `~/heiyao/黑曜/03-构建产物/` 不删旧包；发版前必读技能 `dfwx-release`。
- 网络现实：github.com:443 直连不通（git push 走 SSH over 443，已配）；资产上传走 `uploads.github.com`；网页抓取走代理 `curl -x http://127.0.0.1:7890`。
- 安全红线：签名密码在 `~/heiyao/src/local.properties` 严禁外传/入脚本/入日志/local.properties 不进 git/CI；`~/heiyao/_保险箱/` 与 `~/Desktop/东方无限/交接包zip` 勿删勿动。
- 远程后端服务器（2026-09-28 上线）：阿里云轻量 北京 `39.106.33.135`（Ubuntu 24.04，2核2G，2027-09-28 到期），做远程公告/版本清单/APK 分发，nginx 已跑。**任何窗口直接用 `ssh dfwx '<命令>'`**（别名在 `~/.ssh/config`，密钥免密，admin 有免密 sudo）；完整手册读技能 `~/.zcode/skills/dfwx-server/SKILL.md`；配置记录 `~/heiyao/server.txt`（本地，勿外传）。铁律：不 `rm -rf` 站点目录、不打印私钥、不建议买域名/备案、非标端口外网不通。APP 端接入代码**尚未开发**。

## 三、铁律

1. **每条结论给出处**：项目代码给 `文件:行号`，参考项目给仓库内路径，规范给链接。无来源 = 无效论证。
2. **禁止发明 API**：不确定的类/方法先找到出处，找不到就明说"没找到，需要验证"。
3. **禁止引入第三方依赖**，除非用户明确批准（vendor 区按上游 catalog 例外）。
4. **验证后才算完成**：无验证证据（命令输出/测试/截图）不得宣称修复或测试通过。
5. 需求模糊就停下来问；研究发现与既有代码冲突就报告冲突，不擅自二选一。
6. 同一问题被用户纠正两次 = 上下文已污染，建议重开会话并附教训浓缩段。
7. **先研究再质疑用户**：用户指令可能是错的——研究明白后，有更好方案就摆理由用更好的，不盲从不硬顶（用户 2026-09-25 明确授权）。
8. 审美/方案选型先给 2-4 个选项让用户挑（豁免：纯技术修复、已钉死 token、用户明说直接做）。
9. **输出精炼、不做过程播报**：结论+证据；直接说人话，不画蛇添足。禁止"干一段说一句"式进度汇报和开工预告——闷头干完一次性汇报；只有发现推翻方向的重大事实、需要用户拍板或被阻塞才中途开口（2026-09-28 用户要求：效率、简洁、质量）。
10. 注释永远独立成行（一行式长方法里行尾注释会吞代码，本项目三次踩坑）。
11. **反空转（2026-10-03 二次修正 —— 本条第一版是错的，已实测证伪）**。
    模型会在「长输入 + 琐碎下一步」之后陷入**生成退化**：思考里把同一小撮句子反复吐出来，
    **永远不结束这条消息**，于是整个步骤 **0 次工具调用**。2026-10-03 实测 5 次，
    合计 **15 分钟 / 114 万字符**（最长一次 448,648 字符、34,707 行、只有 64 个不同句子）。

    ⚠️ **本条第一版写的五条机械约束（清单前置 / 小目标 / 一个意图只说一次 / 动作账本 /
    报告限长）实测无效** —— 写进本文件之后**紧接着又循环了一次**。
    根因：它们全都作用在「**我打算做什么**」这一层，而故障发生在「**我正在吐字**」这一层。
    凡是依赖模型自觉的规则，对它的拦截率是 0。**别靠纪律，要靠外部机械判据。**

    **真正的解法（已装好，不在本仓库）**：`~/.dsh/patches/60-loop-guard/`
    —— DSH 插件，挂在官方 `llm/stream` 瀑布上，实时统计「不同句子 ÷ 总句子数」。
    实测判据：健康输出中位数 **96.4%**，退化输出 **0.2~0.8%**，中间是真空带；
    阈值取 30% 时 **5/5 命中、0/250 误报**（用真实会话日志跑的），
    线上在 3000~7500 字符处即可刹住，省掉 98% 的浪费。
    默认 `mode: log` 只记录不动手，`enforce` 才拦停。详见该目录 `README.md`。

    下面五条**保留，但只当良好习惯，不当防线**（它们对防退化无效，对别的事有用）：
    - **清单前置**：收到长输入后先 `todo_write` 写具体动作。
    - **小目标**：一次只做清单里的一条：改 → 测 → 提交。
    - **一个意图只许说一次**：出现第二次就发调用或停下说话。
    - **动作账本**：每轮回复末尾一行写"本轮实际动作"。
    - **报告限长**：派活写死「结论 + `文件:行号`，正文 ≤60 行；细节写进文件」。

## 四、按需检索（不默认加载，按任务读对应节）

> **技能路径约定**：ZCode 在 `.zcode/skills/<名>/`，WorkBuddy 在 `~/.workbuddy/skills/<名>/`。
> 两边已同步 `dfwx-release` / `dfwx-verify` / `dfwx-emulator` / `codebase-memory-cli`，按技能名引用即可。

| 任务场景 | 读哪里 |
|---|---|
| 刚接手/换窗口 | **`docs/handover/README.md`** —— 30 秒接手的单一入口（**该目录故意不提交**，是本机现场） |
| 手势/滚动/搜索 bug | `docs/agents/lessons.md` 六-踩坑 2026-09-21晚 / 09-22 v1.19.5 |
| AI 对话页改动 | `lessons.md` 六-踩坑 v1.19.5②③；`rikkahub/PATCHES.md` |
| AI 页键盘/insets/顶栏错位 | `lessons.md` 六-踩坑 2026-09-28 v1.22.8（IME 根治 + **禁自研动画红线**）；任务卡 `DFWX-UI-005` |
| 发版/上传 Release | 技能 `dfwx-release`；`lessons.md` 六-踩坑 09-21、09-28（**资产名必须 ASCII + 上传后核对 sha256**） |
| 动画/弹簧/按压 | `lessons.md` 六-踩坑 v1.19.7 / v1.19.8（覆盖结论：触摸路径禁物理弹簧，一律 VPA） |
| 视觉/质感/圆角/字重 | `docs/design/wear-ui-system.md`（v2，token 唯一真相）；`lessons.md` 五/六 |
| 大功能立项 | `lessons.md` 一-五阶段工作流（大规模搜证默认跳过，常规任务直接引 spec） |
| bug 诊断 | `lessons.md` 七（DfLog 结构化日志）；`dfwx-verify`（验证阶梯） |
| 构建问题 | `lessons.md` 三-覆盖声明 |
| **构建/测试失败**（任何红） | **`lessons.md` 第九节 —— 先读它再动手**。2026-10-02 实测：一个 OOM 被误判成"测试写法不对"，白返工一轮；正确原因第九节第 1 条就写着。**失败面很宽、且每次失败的是不同几条 → 先怀疑环境，不要先改代码** |
| **跑测试** | **一律用 `bash tools/run-tests.sh`，不要直接敲 `./gradlew`** —— 它会顶 `ulimit -u` 到硬上限并清残留 worker。2026-10-02 就是因为绕过它，同一份代码跑出「2 红 / 3 红 / 0 红」三种结果，白查一轮。支持 `--rerun` / `--class XxxJvmTest` / `--vendor` |
| **派活给子智能体/队友** | **`lessons.md` 第十一节**。铁律：**长任务一律 `spawn_teammate`（常驻可续），不用 `subagent`（一次性，死了没法唤醒）**；派活前先另存仓库外备份；队友断了先「编译+跑测试」判断工作区好坏再接手 |
| APK/dex 产物分析（dexdump/aapt） | `lessons.md` 六-踩坑 2026-09-28（dexdump 用绝对路径；`-a` 才输出注解，否则假阳性） |
| 审美选型 | `~/heiyao/黑曜/06-开源参考库/00-总索引.md`；`lessons.md` 八 |
| 服务器/远程公告/后端 | 技能 `dfwx-server`（连接、接口格式、运维命令、安全现状全在里面） |

## 五、当前唯一交接入口（2026-10-01 更新）

接手/换窗口时**先读 `docs/handover/README.md`** —— 它是 30 秒接手的单一入口
（一句话现状 + 开工三步 + 进度快照 + 铁律红线 + 下一步 + 更深文档的索引）。
它自己写明了怎么维护：**每次收尾只改三处，每处一两行**。

`docs/handover/` 里 2026-09-28 / 09-29 的那几份是历史现场，只在需要考古时看。
**该目录故意不提交、不 push，是本机本地文件**——若不存在，说明换了机器或被人误删，
用 `git show` 找不回，改按下面第 1-6 条从 `docs/plan/` 读起。

后续 AI、WorkBuddy 和人工维护必须先读：

1. `docs/plan/DFWX-MASTER-PLAN.md`
2. `docs/plan/current-state.md`
3. `docs/plan/decisions.md`
4. `docs/plan/risk-register.md`
5. `docs/tasks/README.md`（**已迁移说明**：任务队列现在在 `taskctl` 任务面板，历史卡在 `docs/archive/tasks/legacy-cards/`）

**当前优先队列（真机反馈驱动，优先于原计划）**：`DFWX-BRAND-003`（赞助闪退，需先拿真机堆栈）→ `DFWX-UI-005`（顶栏错位，禁自研 insets 动画）→ `DFWX-UI-006`（删空态文案），之后回原队列 `DFWX-TEST-001` 起。

`docs/archive/` 只保存历史计划、研究和专题资料，不能直接作为当前实施命令。开工前必须运行 `git status --short`，保护现有未提交改动；一次只接一个任务卡，不得顺手跨领域重构。任务没有测试、编译和明确证据，不得标记完成。不得读取或输出 `local.properties`，不得执行手机不可逆操作、提交、push 或发布。涉及 vendor 时必须同步更新 `rikkahub/PATCHES.md`。
