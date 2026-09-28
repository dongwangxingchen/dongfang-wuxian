# 《东方无限》APP 持续优化工作规范

> 2026-09-25 瘦身版（用户授权："只保留有用的部分"）。历史规范/踩坑/原始工作流全文在
> `docs/agents/lessons.md`，按第四节索引按需读，不默认加载。用户原话："测试期版本号
> 一直递增只为覆盖安装，最终发布重置 1.0.0"。

## 一、会话开始

1. 读 `~/heiyao/东方无限-项目认知.md` 建立全貌，必须用 `git log --oneline -15` 核时效，以代码为准。
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

## 四、按需检索（不默认加载，按任务读对应节）

> **技能路径约定**：ZCode 在 `.zcode/skills/<名>/`，WorkBuddy 在 `~/.workbuddy/skills/<名>/`。
> 两边已同步 `dfwx-release` / `dfwx-verify` / `dfwx-emulator` / `codebase-memory-cli`，按技能名引用即可。

| 任务场景 | 读哪里 |
|---|---|
| 手势/滚动/搜索 bug | `docs/agents/lessons.md` 六-踩坑 2026-09-21晚 / 09-22 v1.19.5 |
| AI 对话页改动 | `lessons.md` 六-踩坑 v1.19.5②③；`rikkahub/PATCHES.md` |
| 发版/上传 Release | 技能 `dfwx-release`；`lessons.md` 六-踩坑 09-21 |
| 动画/弹簧/按压 | `lessons.md` 六-踩坑 v1.19.7 / v1.19.8（覆盖结论：触摸路径禁物理弹簧，一律 VPA） |
| 视觉/质感/圆角/字重 | `docs/design/wear-ui-system.md`（v2，token 唯一真相）；`lessons.md` 五/六 |
| 大功能立项 | `lessons.md` 一-五阶段工作流（大规模搜证默认跳过，常规任务直接引 spec） |
| bug 诊断 | `lessons.md` 七（DfLog 结构化日志）；`dfwx-verify`（验证阶梯） |
| 构建问题 | `lessons.md` 三-覆盖声明 |
| 审美选型 | `~/heiyao/黑曜/06-开源参考库/00-总索引.md`；`lessons.md` 八 |
| 服务器/远程公告/后端 | 技能 `dfwx-server`（连接、接口格式、运维命令、安全现状全在里面） |

## 五、当前唯一交接入口（2026-09-27）

后续 AI、WorkBuddy 和人工维护必须先读：

1. `docs/plan/DFWX-MASTER-PLAN.md`
2. `docs/plan/current-state.md`
3. `docs/plan/decisions.md`
4. `docs/plan/risk-register.md`
5. `docs/tasks/README.md`
6. 对应的 `docs/tasks/DFWX-*.md`

`docs/archive/` 只保存历史计划、研究和专题资料，不能直接作为当前实施命令。开工前必须运行 `git status --short`，保护现有未提交改动；一次只接一个任务卡，不得顺手跨领域重构。任务没有测试、编译和明确证据，不得标记完成。不得读取或输出 `local.properties`，不得执行手机不可逆操作、提交、push 或发布。涉及 vendor 时必须同步更新 `rikkahub/PATCHES.md`。
