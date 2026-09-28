# 东方无限：任务执行入口

> 所有后续 AI 只从这里挑任务。任务卡名称保持稳定，不使用“最新版、修改版、终版”等后缀。

## 接手顺序

**已完成（v1.22.8 为止，均已入库并 push `backup` 远程）**：

```text
DFWX-BASE-001 ✅
DFWX-AI-001 ✅（研究）
DFWX-AI-002 ✅（设计报告，已按报告落地 P16 顶层化）
DFWX-AI-003 ✅（SettingsDataGuard 数据保护层）
DFWX-AI-004 ✅（已移除内置 API 播种与构建注入）
DFWX-SEC-001 ✅（代码+JVM 策略；真机负向链路按用户豁免不补）
DFWX-SEC-003 ✅（签名 fail-closed）
DFWX-NET-001 ✅（AI 链 HTTPS-only 四道防线）
DFWX-STAB-001 ✅（下载历史异步原子落盘 + generation/owner 双校验）
DFWX-ADB-001 ✅（ADB 状态车道 + generation 闸门）
RikkaHub 2.5.1 → 2.5.5 clean import ✅
```

**当前优先队列（真机反馈驱动，优先于原计划队列）**：

```text
DFWX-BRAND-003  赞助按钮闪退          ← 第一优先：用户可感知、需真机堆栈
DFWX-UI-005     AI 页顶栏键盘错位      ← 用户已报告，禁止重犯自研动画红线
DFWX-UI-006     AI 页空态文案删除      ← 用户已明确要求，小改动
```

**原计划队列（上述三项后继续）**：

```text
DFWX-TEST-001
→ DFWX-AI-005
→ DFWX-SEC-004
→ DFWX-BRAND-001 / DFWX-BRAND-002
→ DFWX-UI-001 / DFWX-UI-002 / DFWX-UI-003 / DFWX-UI-004
→ DFWX-ARCH-001
→ DFWX-DEP-001 / DFWX-CI-001 / DFWX-RELEASE-001
```

只读研究可以并行；修改 `MainActivity`、PreferencesStore、vendor 核心或构建配置时不得并行改同一文件。

**UI-005 与 UI-006 建议同一轮做**（同属 AI 页键盘场景收尾），但分开提交以便单独回退。

## 当前任务卡

- `DFWX-BASE-001`：唯一入口、当前状态和旧文档归档；
- `DFWX-AI-001`：核对 RikkaHub 稳定版本和当前 vendor 差异；
- `DFWX-AI-002`：设计新版 AI 宿主接入边界；
- `DFWX-AI-003`：Provider、模型、助手和聊天数据保护；
- `DFWX-AI-004`：移除东方无限内置 AI 播种和构建注入；
- `DFWX-AI-005`：AI 生命周期、日志、HTTPS 和回归门禁；
- `DFWX-SEC-001`：外部下载和安装边界；
- `DFWX-SEC-003`：release 签名 fail-closed；
- `DFWX-SEC-004`：AI 日志最小化和脱敏；
- `DFWX-NET-001`：普通外部 AI/API HTTPS-only 与重定向；
- `DFWX-STAB-001`：下载历史、并发和生命周期；
- `DFWX-ADB-001`：ADB/Shizuku 后台状态控制；
- `DFWX-TEST-001`：高风险链路测试矩阵；
- **`DFWX-BRAND-003`：AI 设置页赞助按钮点击闪退（真机反馈，第一优先）；**
- `DFWX-BRAND-001`：赞助页重做；
- `DFWX-BRAND-002`：关于、致谢、隐私和许可证页；
- `DFWX-UI-001`：动画根因与性能证据；
- `DFWX-UI-002`：设置页；
- `DFWX-UI-003`：下载页和软件库入口；
- `DFWX-UI-004`：新版 AI Compose 页面；
- **`DFWX-UI-005`：AI 页键盘弹出时顶部顶栏错位（真机反馈）；**
- **`DFWX-UI-006`：AI 页空会话占位文案删除（用户明确要求）；**
- `DFWX-ARCH-001`：宿主领域渐进拆分；
- `DFWX-DEP-001`：vendor、依赖、SBOM、LICENSE/NOTICE；
- `DFWX-CI-001`：CI 分层门禁；
- `DFWX-RELEASE-001`：release 回归和 GitHub 交付。

## 每张任务卡必须包含

1. 当前问题和代码证据；
2. 用户边界；
3. 前置任务；
4. 允许修改文件；
5. 明确禁止文件和操作；
6. 最小实现；
7. 可失败测试；
8. 验收命令；
9. 未验证证据；
10. 回退方式；
11. 完成后更新哪些文档。

## AI 接手模板

```text
先读 AGENTS.md、docs/plan/DFWX-MASTER-PLAN.md、current-state.md、decisions.md、risk-register.md 和本任务卡。
先运行 git status --short，不得清理未提交改动。
只修改任务卡允许范围；发现范围不足先停止并报告，不顺手扩展。
先补可失败测试，再做最小实现；跑完测试、编译和 diff 检查后才能标记完成。
不要读取或输出 local.properties，不操作手机不可逆动作，不提交、push 或发布。
```

## 当前现场速查（2026-09-28 晚复核，证据等级 A）

| 项 | 值 |
|---|---|
| 源码根 / 分支 | `/Users/<用户名><仓库根>` / `test` |
| 当前 HEAD | `5a854f1` + 一个文档治理提交（2026-09-28 晚；`git log --oneline -1` 查最新） |
| 工作树 | 源码零改动；`docs/handover/` untracked **故意不提交**（见该目录说明） |
| 版本号 | versionCode `1039028` / versionName `1.22.8`（`app/build.gradle.kts:26-27`） |
| 最新发版 | v1.22.8（GitHub Release，asset `dongfang-wuxian-v1.22.8.apk`） |
| 测试基线 | **628 例全绿**（宿主 51 + vendor app 240 + ai 199 + common 1 + search 16 + highlight 53 + material3 1 + oauth 2 + speech 43 + web 1 + workspace 20 + document 1） |
| 宿主测试命令 | `JAVA_HOME=/Users/<用户名>/sdk/jdk-21.0.11.jdk/Contents/Home <本地目录>/src/gradlew -p <本地目录>/src :app:testEmptyDebugUnitTest` |
| vendor 测试命令 | 同上，任务名 `:rikkahub-app:testDebugUnitTest`（**vendor 无 flavor，不叫 testEmptyDebugUnitTest**——这是踩过的坑） |
| release 构建 | `... :app:assembleEmptyRelease`（严禁混入含 "Debug" 的任务名） |
| vendor 基线 | 上游 `re-ovo/rikkahub` tag `2.5.5`（versionCode 190），见 `rikkahub/PATCHES.md` |

## 开工前必须先向用户汇报（铁规矩）

每张任务卡动手之前，先用大白话向用户汇报，得到用户同意后才开工：

1. 这张卡要干什么事（允许打比方，禁止术语轰炸）；
2. 大概分几步、会动到软件里哪些地方；
3. 做完之后用户能看到什么变化；
4. 有没有风险（尤其是可能弄坏数据或已有功能的地方）。

汇报默认在对话里等用户答复；用户明确说"你看着办"才可以跳过等待。干完之后同样用大白话汇报：做了什么、验证过什么、哪些还没验证。

## 用户提问规则

向用户提问时一律用正文文字列出选项，不用按钮/选项组件。

旧任务、旧研究和旧截图在 `docs/archive/`，只作证据，不作当前命令。

## 全仓审计（2026-09-27）

`docs/audit/20260927-full-audit.md` 是开工前的全仓只读审计，抽查发现全部属实。执行 SEC-003 / SEC-004 / NET-001 / ADB-001 / DEP-001 / CI-001 / STAB-001 等卡前，先读报告对应节——报告里的 M-*/H-*/V-*/B-* 编号可直接作为任务卡证据引用。两处修正：H-P0b（pm install pipe 死锁）为理论风险降 P2；B-3（debug 只含 x86_64）是已知约定（lessons 六 2026-09-22 条），非新问题。
