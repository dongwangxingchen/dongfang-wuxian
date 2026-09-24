# verification.md — 交互性能优化验证记录

门禁顺序：编译 → 全量单测 → 装机截图比对（宿主机必须空闲，gradle 与截图不得并行——
2026-09-24 实测 gradle 满载会把模拟器 SystemUI 饿到 ANR 弹窗循环，污染截图与帧数据）→
gfxinfo 帧分解 A/B → commit。

## 基线（改前，2026-09-24 下午，dfwx-phone34 GPU host）

- 全量 `:app:testEmptyDebugUnitTest` EXIT=0
- 五页截图：/tmp/dfwx_baseline/（base_home/ai/downloads/tools/settings.png）
- framestats main 段（应用主线程帧耗时，/tmp/framebreak.py 解析）：
  - 真实切 tab×12：p50 5ms / **p90 36ms / p95 48ms / max 64ms**
  - 设置页滚动 p90 6ms；主页滚动 p90 8ms；工具二级页 max 7ms；详情页 max 19ms
  - gpu 段 16-33ms=模拟器 Metal 转发底噪（真机约 2-5ms），判应用看 main 段
- 冷启动 TotalTime 744ms；GC 2000 行日志 1 次；PSS 158MB

## Phase A（去 overdraw + 图标 inSampleSize；A3 启动 IO 推迟）

- 改动：basePage 移除 root/primaryShell 的 setBackgroundColor(BG)（host 单层背景，官方
  overdraw 规则"同色容器只留窗口一层"）；requestImage 改官方两段式解码（inJustDecodeBounds→
  calculateIconInSampleSize 144px→inSampleSize 解码），新增 decodeIcon/calculateIconInSampleSize/
  readAllBytes 三个 helper。
- 预期：视觉零变化；GPU 段每帧减 2 次全屏填充；图标位图内存降 4-16 倍。
- 结果：待填。

## Phase B（下载页分帧 + 搜索去抖）

- 改动：renderDownloads 拆两段——同步收集行规格（纯数据），renderDownloadsFrame 按帧建行
  （generation/session 双守卫，空态与选择态语义保持）；pageSearch 加 debounceMs 重载，
  下载页 200ms；basePage teardown 清 searchDebounceRunnable+generation++。
- 预期：下载记录多时切页/搜索不再单帧卡顿；功能行为不变。
- 结果：待填。

## Phase C（设置页就地刷新）

- 改动：settingsRefreshers 注册表 + refreshSettingsInPlace()；searchMode/UA/baseOrigin 三行动态
  值注册刷新；5 处 `if(pageKind==4)showSettings()` 换就地刷新（askOnDownload 开关无伴随显示，
  直接去重建；搜索模式弹窗额外 refreshIndexProgressCard）。
- 预期：弹窗保存不再整页重建、不再丢失滚动位置；行值仍即时更新。
- 结果：待填。

## Phase D（外壳/导航常驻 + 弱机转场降级）

- 计划：primaryDestination>=0 且 shell 已存在时复用 root/shell/nav（teardown 语义全保留，
  只清内容子 View）；isLowRamDevice 或 totalMem≤1.5GB 时转场降 ≤150ms 淡切。
- 结果：待填。

## 附注

- 全屏截图 md5 不可用作视觉比对（状态栏时钟每分钟变化）——比对看 app 内容区目检 + JVM
  截图测试 golden。
- 手机模拟器 GPU host 段数字只作趋势；真机验收以用户手机实测为准。
