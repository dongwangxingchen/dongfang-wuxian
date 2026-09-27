# 03｜目标架构与边界

## 1. 当前结构问题

宿主核心类同时承担 UI 构造、导航、状态、网络、持久化、线程和平台服务：

- `MainActivity.java`：页面、设置、下载、搜索、WebView、更新、ADB、历史和图片缓存；
- `LanzouCore.java`：解析、目录、搜索、限频、UA、缓存和网络调度；
- `ToolHost.java`：工具 UI、传感器、媒体、导航和宿主回调。

vendor 还通过跨模块调用进入 AI、文档、搜索和公共层。问题不是“文件行数大”本身，而是修改一个局部功能会同时碰生命周期、线程、UI 和数据。

## 2. 目标分层

```text
Activity / Compose UI
        ↓ 用户事件、状态订阅
Feature Coordinator
        ↓ 用例和生命周期
Domain Policy / State Machine
        ↓ 可测试规则
Repository / Platform Adapter
        ↓ Android、网络、文件、Shizuku、vendor
外部系统
```

UI 不直接决定网络信任、文件路径、安装权限或凭据留存。策略应是纯逻辑或窄适配器，先可在 JVM 测试。

## 3. 推荐领域边界

### Settings

- `SettingsRepository`：读写和迁移设置；
- `SettingsState`：可观察的只读状态；
- `SettingsPageBuilder`：只构造 View 和绑定事件；
- 不让页面直接散落 SharedPreferences key。

### ExternalAction

- `ExternalActionRouter`：解析 ACTION、来源、参数和用户确认；
- `ExternalDownloadPolicy`：HTTPS、host、IP、重定向、文件类型规则；
- 外部输入不能直接构造“已解析直链”。

### Download

- `DownloadCoordinator`：任务生命周期、暂停、恢复、取消、完成；
- `DownloadHistoryStore`：异步、原子、可恢复持久化；
- `SegmentDownloader`：传输协议和 Range 校验；
- 安装器：包名、签名、版本、来源和用户确认策略。

### Search / Lanzou

- `SearchCoordinator`：并发、进度和取消；
- `LanzouCore`：蓝奏协议解析；
- `DirectLinkResolver`：分享地址到解析结果的受控转换；
- 缓存对象必须区分来源类型，不能用普通 String 混淆信任等级。

### Update

- `UpdateCoordinator`：检查时机和 UI 状态；
- `UpdateClient`：官方更新 URL、资产、摘要和签名策略；
- 更新链与普通下载链不能共用宽松策略。

### ADB

- `AdbStateController`：后台刷新、去重、generation；
- `AdbShellManager`：Shizuku 服务绑定和受控安装 API；
- 不向上层暴露任意 shell 能力。

### Vendor

- vendor 上游代码尽量保持独立；
- 宿主定制通过明确的 patch、适配器或入口扩展完成；
- 每次 vendor 变更必须写入 `rikkahub/PATCHES.md`；
- 不在一次宿主重构中顺手升级 vendor。

## 4. 渐进拆分顺序

1. 先为策略和状态机补测试；
2. 抽 `SettingsRepository`；
3. 抽 `ExternalActionRouter` 和 URL policy；
4. 抽 `DownloadCoordinator`/`DownloadHistoryStore`；
5. 抽 `AdbStateController`；
6. 抽 `UpdateCoordinator`；
7. 抽 `SearchCoordinator`；
8. 最后移动 `SettingsPageBuilder` 和更大 UI 构造。

每一步只改一个领域，保留旧入口作为窄适配层，测试通过后再删除重复路径。

## 5. 前端与动画原则

- 宿主 Java View 与 Compose 分别遵循各自平台最佳实践，但共享颜色、间距、字阶、状态语义和可访问性目标；
- 先查本地黑曜参考库、现有 Token、官方 Android/Material 文档和已批准 Skill，再决定实现；
- 复用现有能力，不为单个控件引入依赖；
- 动画必须可打断、可取消、可测量，不能用特殊分支掩盖状态错误；
- `LumaSwitch` 当前异常先取帧级证据，确认根因后才改参数；
- 只测手机，不恢复手表和圆屏路径。
