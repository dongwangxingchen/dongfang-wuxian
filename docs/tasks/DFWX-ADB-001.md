# DFWX-ADB-001：ADB/Shizuku 后台状态控制

状态：代码与 JVM 测试已完成（2026-09-28）；真机 Shizuku 实机复测待用户授权后补
前置：BASE-001、STAB-001

## 目标

ADB/Shizuku 状态检查和安装操作不阻塞主线程，旧查询结果不能覆盖新状态。

## 允许修改

- `AdbShellManager`、状态控制器、fake 和测试；
- 必要的宿主调用边界。

## 禁止

不操作用户手机；不改变外部 APK 禁止静默安装的策略；不增加新的宿主第三方依赖；不把 Binder 调用散落到页面。

## 验收

可控 fake 覆盖延迟、超时、权限变化、重连和旧 generation；主线程断言通过；安装错误可定位；无手机破坏性操作。

## 实施记录（2026-09-28）

审计证据：`docs/audit/20260927-full-audit.md` H-P1-4——`refresh()` 同步执行且无代次防护，
被 Shizuku 四类回调（binderReceived / binderDead / permissionResult / ServiceConnection）
从不受控线程触发，慢的旧评估可覆盖新状态。

改动：

- `AdbShellManager`：新增状态车道（单线程）与 generation 闸门。所有平台调用只在车道上执行；
  `refresh()` / `requestPermission()` / `close()` 对调用方立即返回，不再在主线程做 binder 事务
  （v1.19.3 的 ANR 根因当时只靠延迟 3 秒规避，现在从结构上消除）。评估开始、绑定前、发布前
  三处校验代次，旧代次结果与旧代次绑定请求一律丢弃；`dropService()` 断链时配合新一代次，
  保证在途的旧 READY/CONNECTING 无法再发布。
- `ShizukuPlatform`（新增）：Shizuku 真实平台实现，只做转发；`AdbShellManager.Platform`
  接缝用于测试注入。
- `install()` 失败原因补上当前状态标题（"ADB Shell 服务未连接（未连接）"），便于定位。

测试：`AdbShellStateTest` 11 例（手动车道 + 可控 fake）——调用线程不做平台工作、旧代次评估
丢弃、检查中 binder 死亡、权限在检查中变化、断链重连二次绑定、平台异常可定位、
申请前重新核实（防重复申请）、未连接安装原因、close 延迟解绑与关闭后停发、平台卡住不阻塞调用方。

反向验证：临时移除 `evaluate`/`publish`/`bind` 三处代次校验后重跑，`staleEvaluation…`、
`binderDeathMidCheck…`、`staleReadyCannotOverrideNewerDeniedState` 三个用例失败；恢复后全绿，
证明测试确实能捕获该类竞态。

验收命令与结果：`:app:testEmptyDebugUnitTest --rerun-tasks` 51 例全绿（原 40 + 新 11）；
`:app:assembleEmptyDebug` 通过。

未验证：真机 Shizuku 实机行为（需要用户手机在 Shizuku 运行状态下复测）；
`HomeShotsJvmTest` / `ToolsShotsJvmTest` 的 Robolectric 原生图形加载在本机偶发失败
（`FileSystemAlreadyExistsException` / `RenderNode` UnsatisfiedLinkError），
属本机已知环境问题（见交接文档），非本次改动引入——单独运行与重跑均通过。

回退方式：还原 `AdbShellManager.java` 单文件、删除 `ShizukuPlatform.java` 与
`AdbShellStateTest.java` 即可，无数据迁移、无对外协议变化。
