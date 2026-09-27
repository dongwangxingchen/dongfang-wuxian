# 04｜分阶段整改路线

## G0｜基线和治理文档

**状态：当前阶段。** 只写治理文档，不改业务源码、配置、版本、依赖、Manifest，不构建、不装机、不发布。

退出条件：治理包通过链接、敏感信息、编号和 diff 范围检查。

## G1｜安全和产品边界

顺序：

1. 外部 Intent、普通下载、蓝奏解析直链、官方更新 URL 分型；
2. 外部下载只能进入待确认下载，禁止直接自动/静默安装；
3. APK 安装前校验包名、签名、版本和来源；
4. 移除当前内置 AI 模型和内置渠道播种，不误删用户自建 Provider；
5. release 签名配置缺失即失败；
6. AI request/response/SSE 日志默认不记录敏感信息；
7. 普通外部 AI/API 只允许 HTTPS，loopback 另行审核。

每个子任务单独测试、单独回退；安全边界没有测试前不进入 UI 美化。

## G2｜生命周期、并发和数据

- 移除 Activity 销毁阶段同步磁盘等待；
- 历史和缓存采用异步、原子、可恢复写入；
- ADB/Shizuku 查询后台化并丢弃过时结果；
- crash logger 只安装一次、脱敏、限量；
- 检查线程池、静态 Activity 引用和进程重建。

## G3｜关键测试

先补高风险测试，再做大范围架构抽取：

- URL、重定向、私网/loopback、Intent；
- APK 安装和签名；
- 下载暂停、恢复、取消、Range、进程重建；
- FileProvider、DocumentsProvider、WebView；
- AI 日志、备份、权限和导出组件；
- ADB 状态竞态；
- 动画 setter/cancel/restart/帧间隔。

## G4｜宿主渐进拆分

按 `SettingsRepository` → `ExternalActionRouter` → `DownloadCoordinator` → `DownloadHistoryStore` → `AdbStateController` → `UpdateCoordinator` → `SearchCoordinator` → `SettingsPageBuilder` 推进。

不做一次性重写 `MainActivity`、`LanzouCore` 或 `ToolHost`。

## G5｜vendor、依赖、CI 和合规

- 固定 vendor 上游 tag/commit；
- patch 自动重放和冲突检查；
- 消除 SNAPSHOT/JitPack 的生产不确定性；
- dependency locking/verification；
- SBOM、License/NOTICE 和 AGPL 源码交付证据；
- PR、test、release 三层 CI 门禁。

## G6｜前端质感和性能

前端不是最后才“随便美化”，而是在底层边界清晰后按专题推进：

1. 设置页：信息架构、控件、折叠、搜索、无障碍和动效；
2. 下载页：状态模型、进度、批量操作、空态和长列表；
3. 软件库/蓝奏 WebView：加载、失败、返回、下载和视觉层次；
4. Compose AI：输入、键盘、返回、切页不断流和上游边界；
5. 全站统一 Token、通知卡片、弹窗和性能基线。

每次 UI 专题前，先选择最贴合的设计 Skill，查本地参考和官方方案；动画用真机 release 数据验收，不以截图或主观“丝滑”结案。

## 并行规则

可以并行做只读研究和互不写同一文件的测试设计；不能并行修改：

- 安全与下载核心；
- vendor 与宿主边界；
- UI 动画与生命周期；
- 依赖升级与业务代码；
- release 配置与其他构建改动。

## 完成定义

“完成”不是绝对没有 bug，而是：已知 P0/P1 不带入发布；关键路径有自动化和实测证据；改动可回退；文档与实现一致；用户可感知的 UI 变化经过手机 release 验收。
