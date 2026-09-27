# 05｜测试与质量门禁

## 1. 证据层级

| 变化类型 | 最低证据 | 不能代替的证据 |
|---|---|---|
| 纯策略/解析 | JVM 负向和正向测试 | 仅编译通过 |
| 生命周期/线程 | fake backend、竞态测试、主线程断言 | 只看最终 UI |
| UI/交互 | JVM 结构/渲染 + 手机 release 操作 | 只看布局代码 |
| 动画/性能 | 同设备帧间隔、jank、主线程和内存数据 | “看起来流畅” |
| 安全/发布 | 产物扫描、负向路径、签名/ABI/版本核验 | 只跑 debug |

## 2. 必补测试矩阵

### 外部输入与下载

- 缺少 URL、未知 scheme、HTTP、HTTPS、userinfo；
- localhost、127.0.0.1、IPv6 loopback、RFC1918 和保留地址；
- 每次重定向重新校验；
- 缓存命中仍执行来源策略；
- 外部 Intent 不能绕过用户确认；
- `.apk` 后缀不能替代包名、签名和版本校验；
- 更新 URL 与普通下载 URL 使用不同 policy。

### 下载状态机

- 开始、进度、暂停、恢复、取消、失败、完成；
- Range 连续性、文件长度、HTML/JSON 验证页；
- Activity 重建和进程重启后的历史恢复；
- 多任务交错和终态只提交一次；
- 关闭 Activity 不等待长时间磁盘 I/O。

### ADB/Shizuku

- Binder 不返回时主线程不阻塞；
- 快速 refresh 只采纳最新 generation；
- service died 不被旧 READY 覆盖；
- 权限拒绝和安装失败文案稳定；
- AIDL 不暴露任意 shell。

### AI 和数据

- APK 资源不存在默认长期 AI Key；
- 内置 Provider 不再播种；用户自建 Provider 不受影响；
- Authorization、Cookie、API Key、body、SSE 日志脱敏或不记录；
- HTTP 外部地址被拒绝；loopback 例外单独测试；
- backup 排除凭据、对话、密码直链和诊断文件。

### WebView/Provider/Manifest

- `intent://`、fallback、未知 scheme、恶意重定向；
- JavaScript bridge 和 file access 最小化；
- FileProvider canonical path、符号链接和路径穿越；
- DocumentsProvider 恶意 docId；
- 每个 variant 的 exported、permission 和 backup 结果。

### 动画

在根因确认前只采集，不改正式参数：

- `setChecked()` 调用来源、目标和同值次数；
- animator start/cancel/end；
- View attach/detach、layout pass；
- 主线程阻塞和帧间隔；
- 自动安装开关与普通开关的对照。

## 3. 现有命令基线

当前已有记录的命令：

```bash
./gradlew :app:testEmptyDebugUnitTest --no-daemon --console=plain
./gradlew test --no-daemon --console=plain --rerun-tasks
```

实际执行时以当前 `AGENTS.md`、`dfwx-verify` Skill 和当前 Gradle 配置为准；`UP-TO-DATE` 不能作为新证据。

## 4. 手机和发布门禁

- 只测手机，模拟器和真机都使用 release 包做最终 UI/ABI 验收；
- 用户手机操作前先汇报并请示；不做卸载、清数据、`pm clear`、删除文件等不可逆动作；
- release 与 Debug 任务分开；
- `aapt` 检查包名、version、arm64-v8a；
- 正式发布前读取 `.zcode/skills/dfwx-release/SKILL.md`；
- GitHub Release 标题纯 `vX.Y.Z`，资产名 `dongfang-wuxian-vX.Y.Z.apk`；
- 未完成 P0/P1 证据不得发布。
