# 东方无限：长期维护总计划

> 当前总入口。后续 AI、WorkBuddy 和人工维护都先读本文件，再读 `current-state.md`、`decisions.md`、`risk-register.md` 和对应任务卡。
>
> 适用源码根目录：`/Users/<用户名><仓库根>`
> 桌面交接目录：`/Users/<用户名>/Desktop/东方无限`（不是源码根目录）
> 建立日期：2026-09-27

## 1. 总目标

把东方无限做成可以长期维护、稳定回归、快速定位问题的 Android 应用：

- 宿主 Java、RikkaHub Compose、下载、搜索、更新、安装、ADB、存储和生命周期边界清楚；
- 当前旧 AI 对话接入整体替换为经过版本核对的 RikkaHub 稳定代码，再做东方无限适配；
- 赞助、关于、致谢、设置、下载和软件库页面统一成有质感且可回归的产品体验；
- 外部下载、安装、网络、凭据、日志、发布和 AGPL 义务有明确门禁；
- 用户自己的 Provider、模型、聊天记录和设置不被误删；
- 后续轻量模型只能在任务边界内修改，不能靠猜测或旧文档把项目带回混乱状态。

不承诺绝对零 bug；完成标准是已知高风险不带入发布、关键链路有可失败测试、每次改动可验证可回退、文档和代码保持一致。

## 2. 唯一接手入口

按顺序阅读：

1. `AGENTS.md`；
2. `docs/plan/DFWX-MASTER-PLAN.md`（本文件）；
3. `docs/plan/current-state.md`；
4. `docs/plan/decisions.md`；
5. `docs/plan/risk-register.md`；
6. `docs/tasks/README.md`；
7. 历史任务卡：`docs/archive/tasks/legacy-cards/DFWX-*.md`（**已归档，只能当背景**；当前队列在 `taskctl` 任务面板）；
8. `rikkahub/PATCHES.md`（涉及 vendor 时）。

`docs/archive/` 只保存历史材料，不能作为当前实施命令。旧资料与当前代码冲突时，以当前源码、当前测试、`AGENTS.md` 和 `decisions.md` 为准。

## 3. 已确定的产品和工程边界

### AI

- 移除当前补丁堆积的旧 AI 对话接入，不删除 AI 功能；
- 核对 RikkaHub 官方稳定版本或固定 commit 后重新接入；
- 新接入必须适配东方无限 Activity、ComposeView、导航返回、主题、键盘、状态恢复和生命周期；
- 移除东方无限内置 AI 模型、内置渠道、默认 URL、默认 Key 和播种器；
- 不清空 `settings.providers`，不删除用户自建或导入 Provider；
- 普通外部 AI/API 地址只允许 HTTPS；loopback HTTP 若未来需要，必须单独研究、测试和记录；
- 请求、响应、SSE 日志不得记录凭据、完整 body 或用户内容。

### 下载和安装

- 外部 Intent 下载可以保留；
- 外部 APK 安装前必须用户确认；
- 外部输入不得直接触发自动安装、Shizuku 或 ADB Shell 静默安装；
- 蓝奏云、外部下载、官方更新和未知历史记录必须分型；
- 官方更新保留包名、签名、版本号和 SHA-256 校验；
- 外部 APK 还必须补齐包名、签名和版本校验。

### 测试、数据和发布

- 只测手机，手表永久停止；Robolectric/JVM 是主要快速回归层；
- 手机安装、启动和读日志已授权，但卸载、清数据、`pm clear`、删除文件等不可逆操作须先请示；
- `local.properties`、签名密码、Key、Token、Cookie 不得读取输出、写入脚本或日志；
- 宿主不随意引入第三方依赖；vendor 改动必须登记 `rikkahub/PATCHES.md`；
- release 缺少签名配置必须 fail-closed；
- GitHub Release 标题只用 `vX.Y.Z`，APK 资产只用 `dongfang-wuxian-vX.Y.Z.apk`；
- 发布 APK 必须履行 AGPL-3.0 同协议源码义务并保留 RikkaHub 上游署名；
- 不自动提交、push、发布或操作用户手机。

## 4. 分阶段路线

### A｜底座和交接

- 固定唯一总入口、当前状态、决策、风险和任务卡；
- 将旧计划、旧研究和旧专题整体移入 `docs/archive/` 分类保存，保留原文件内容和历史路径说明；
- 任何任务先写清范围、证据、测试、回退和未验证项。

### B｜AI 对话整体替换

1. 核对 RikkaHub 官方稳定版本、当前 vendor 基线和补丁差异；
2. 明确保留的宿主桥接与应删除的旧接入；
3. 在不动用户数据的前提下重建 AI 页面接入；
4. 迁移/保护 Provider、模型、助手和聊天历史引用；
5. 移除东方无限内置 AI 播种及构建注入；
6. 补生命周期、返回、空状态、错误状态、HTTPS 和日志测试；
7. vendor 变更逐条登记并可回退。

### C｜安全和发布边界

- 外部下载重定向、DNS、私网和 APK 完整校验；
- release 签名 fail-closed；
- AI 日志最小化和脱敏；
- Manifest、WebView、FileProvider、导出组件和各 variant 产物检查。

### D｜稳定性和状态机

- 下载历史异步原子保存；
- 进程重建、暂停/恢复/取消/重试状态；
- 移除 `onDestroy()` 主线程同步 I/O；
- ADB/Shizuku 后台状态与 generation 防旧结果覆盖；
- crash logger 单次安装、脱敏和有限保留；
- 清理静态 Activity 引用和线程生命周期。

### E｜产品页面专题

按领域逐个做，不跨专题混改：

1. 赞助页：信息层级、入口、说明、外部跳转、加载/失败/返回、日志和真机验收；
2. 关于、致谢、隐私和许可证页：文案、上游署名、原作者介绍、外部链接和返回链；
3. 设置页：结构、状态、统一按压和展开收起；
4. 下载页和软件库/蓝奏云入口：状态一致、空态、错误态、进度和安装边界；
5. 新 AI Compose 页：输入、键盘、空态、流式响应、返回和主题；
6. 工具箱及通用弹窗、空状态和错误状态。

### F｜宿主渐进拆分

按顺序一次抽一个领域：`SettingsRepository`、`ExternalActionRouter`、`DownloadCoordinator`、`DownloadHistoryStore`、`AdbStateController`、`UpdateCoordinator`、`SearchCoordinator`、`SettingsPageBuilder`。每次先测试、再移动、再删旧入口；不一次性重写 `MainActivity`。

### G｜动效、性能和发布

- 先采集 setter、cancel/restart、attach、layout、帧间隔和 gfxinfo 证据；
- 根因确认前不凭感觉调 `LumaSwitch` 参数；
- 用手机 release 数据验收启动、jank、主线程耗时、内存、下载恢复和 ADB 延迟；
- 固定 vendor、依赖、SBOM、LICENSE/NOTICE 和 CI 门禁；
- 完成发布前回归后才生成测试版 Release。

## 5. 任务执行规则

- 没有任务卡不改代码；
- 开工先读当前状态和 `git status`，保护所有未提交改动；
- 一次只改一个领域，不顺手重构相邻模块；
- 先写可失败测试，再做最小实现；
- 每次改动必须编译并运行对应测试；
- 测试没跑或失败，不能写“已完成”；
- 完成后更新任务卡的证据、未验证项、回退方式和下一步；
- 任何手机不可逆操作、提交、push、发布都停止并请示。

## 6. 当前推荐顺序（2026-09-28 晚更新）

**已完成阶段（A 底座 + B AI 替换主体 + C/D 的核心项）**：

```text
BASE-001 ✅ → AI-001 ✅ → AI-002 ✅ → AI-003 ✅ → AI-004 ✅
→ RikkaHub 2.5.1 → 2.5.5 clean import ✅（P16 顶层化落地）
→ SEC-001 ✅（真机负向链路按用户豁免不补）/ SEC-003 ✅ / NET-001 ✅
→ STAB-001 ✅ / ADB-001 ✅（真机 Shizuku 复测待授权）
→ v1.22.8 已发（含宿主 IME 补偿根治）
```

**当前优先队列（真机反馈驱动，优先于原计划）**：

```text
BRAND-003 赞助闪退（第一优先，需先拿真机崩溃堆栈）
→ UI-005 AI 页顶栏键盘错位（禁止重犯自研 insets 动画红线）
→ UI-006 AI 页空态文案删除（小改动，建议与 UI-005 同轮分开提交）
```

**原计划队列（上述三项后继续）**：

```text
TEST-001 → AI-005 → SEC-004
→ BRAND-001 / BRAND-002
→ UI-001 / UI-002 / UI-003 / UI-004
→ ARCH-001
→ DEP-001 / CI-001 / RELEASE-001
```

只读研究可以并行，修改共享核心文件不能并行。真机反馈可插队，但插队项同样遵守"先取证、再改码"。

**插队理由**：用户可感知的问题优先，且真机现场（v1.22.8 装机状态）热度最高，趁现象可复现时取证成本最低。

## 7. 完成定义

- 当前总计划、状态、决策、风险、任务卡和归档目录互相链接；
- 后续 AI 不需要从历史文档猜当前要求；
- 关键安全和数据边界有可失败测试；
- AI 重做有版本、补丁、迁移、生命周期和回退证据；
- 赞助、关于、设置、下载、软件库和 AI 页面分别验收；
- vendor、依赖、CI、签名、许可证和发布可追溯；
- 未验证的手机和 release 证据明确写出，不冒充完成。
