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
- 结果：通过。截图五页目检与基线一致；全量单测 EXIT=0；随 B/C 同批跑切 tab 基准见下。

## Phase B（下载页分帧 + 搜索去抖）

- 改动：renderDownloads 拆两段——同步收集行规格（纯数据），renderDownloadsFrame 按帧建行
  （generation/session 双守卫，空态与选择态语义保持）；pageSearch 加 debounceMs 重载，
  下载页 200ms；basePage teardown 清 searchDebounceRunnable+generation++。
- 预期：下载记录多时切页/搜索不再单帧卡顿；功能行为不变。
- 结果：通过。单测 EXIT=0；空态与有记录路径截图目检正常；切 tab 基准见下。

## Phase C（设置页就地刷新）

- 改动：settingsRefreshers 注册表 + refreshSettingsInPlace()；searchMode/UA/baseOrigin 三行动态
  值注册刷新；5 处 `if(pageKind==4)showSettings()` 换就地刷新（askOnDownload 开关无伴随显示，
  直接去重建；搜索模式弹窗额外 refreshIndexProgressCard）。
- 预期：弹窗保存不再整页重建、不再丢失滚动位置；行值仍即时更新。
- 结果：通过。单测 EXIT=0；设置页截图目检正常。

## Phase A+B+C 合验（切 tab×30 逐帧，2026-09-24）

- framestats main 段：**p50 2ms / p90 5ms / max 7ms**（基线 p90 36ms/max 64ms）→ 提交 28a2145。
- 截图：/tmp/dfwx_phaseABC/（c_*.png）目检与基线一致。

## Phase D（弱设备转场降级；外壳/导航常驻经复评后裁撤）

- 改动（提交 aec8044）：新增 weakDevice 字段 + detectWeakDevice()
  （isLowRamDevice 或 totalMem≤1536MB，onCreate 调用，异常安全）；animatePage 条件
  `direction==0` → `direction==0||weakDevice`，弱设备页切换走 140/190ms 淡切替代 350-400ms
  主题推入；标准设备行为零变化。
- 裁撤说明：外壳/导航常驻在 A+B+C 把切 tab main 段压到 p90 5ms 后剩余收益小，且 teardown
  80 字段语义风险高，故不做；A3 启动 IO 同理由推迟。
- 门禁：编译修正一处（android.os.MemoryInfo → android.app.ActivityManager.MemoryInfo，
  MemoryInfo 是 ActivityManager 内部类）→ 全量 `assembleEmptyRelease+testEmptyDebugUnitTest`
  EXIT=0 → 装机截图主页/设置页目检正常（/tmp/dfwx_phaseD/）→ 切 tab×30 framestats
  main 段 p50 2ms / p90 5ms / p95 6ms / max 17ms（与 A+B+C 参照持平，无回归；本模拟器
  4GB RAM 不触发弱设备路径，降级分支由单测与代码审查兜底）→ logcat 0 FATAL/0 ANR，
  进程无重启。
- 注：API 34 上逐帧数据必须用 `dumpsys gfxinfo <pkg> framestats`（带 framestats 关键字
  才有 ---PROFILEDATA--- 节），reset 后普通 dumpsys 只有汇总直方图。

## 附注

- 全屏截图 md5 不可用作视觉比对（状态栏时钟每分钟变化）——比对看 app 内容区目检 + JVM
  截图测试 golden。
- 手机模拟器 GPU host 段数字只作趋势；真机验收以用户手机实测为准。
- 待办移交：四方向美学改造（字体→图标→列表→转场）等用户挑选后另行开工，届时以
  "学习谷歌官方设计"为原则；wear-ui-system.md 圆屏章节待改方屏。
