# 01｜基线与范围

## 1. 当前事实

| 项目 | 当前值 | 证据 |
|---|---|---|
| 工作根目录 | `/Users/<用户名><仓库根>` | `src/AGENTS.md:17-26` |
| 分支 | `test` | 当前 `git status --short --branch` |
| HEAD | `30af1f0` | 当前 `git log --oneline -15` |
| 当前版本 | v1.22.4 | `30af1f0`、`app/build.gradle.kts` |
| applicationId | `dfwx.dongdang` | `src/AGENTS.md:19-23` |
| Java 包名 | `cc.nkbr.lanzouplus` | `src/AGENTS.md:19-20` |
| 宿主 | Java 程序化 View | `src/AGENTS.md:19-20` |
| vendor | `rikkahub/`，Kotlin + Compose | `src/AGENTS.md:19-20` |
| 最低 SDK | 26 | 当前构建配置和 `src/AGENTS.md:21-23` |
| 测试设备范围 | 只测手机，手表永久停止 | `src/AGENTS.md:22-24`、用户最新决定 |

## 2. 已有验证基线

当前已有记录的宿主 JVM 测试和全仓 JVM 测试均通过，但这只证明已有测试覆盖的路径通过，不代表：

- 外部 Intent 到自动/静默安装链路已安全；
- 真机动画帧率和主线程阻塞已达标；
- 下载进程重建、ADB 异常和 WebView 边界已覆盖；
- release 签名、Manifest、依赖供应链和真实 APK 都已完成发布验收。

以后每份验证记录必须标注：代码确认、实测确认或推测/待验证。

## 3. 工作树保护

当前 `git status` 显示 `docs/plan/`、`docs/tasks/20260925-ui-overhaul/`、`docs/tasks/20260926-search-web-overhaul/` 存在未跟踪内容。它们是已有研究资产，不能因建立治理包而覆盖、清理或自动提交。

禁止：

- `git reset --hard`、`git checkout --`；
- 删除或覆盖<本地加密备份>、桌面<本地备份>、已有计划和研究目录；
- 读取、复制或输出 `local.properties` 中的凭据；
- 清数据、卸载、删除用户源或修改用户手机；
- 在治理阶段修改源码、依赖、Manifest、版本和发布配置。

## 4. 当前不应直接重写的正确控制

下列能力已有较完整的安全控制，应优先补测试而不是无证据重写：

- `UpdateClient.java`：HTTPS、host/路径、资产名、size、SHA-256、包名和签名校验；
- `DownloadFileProvider.java`：canonical path、目录边界和导出限制；
- `AdbShellService.java`：固定 `pm install` 参数、文件大小和超时；
- WebView 的 `intent://` 清洗链；
- 现有 Phosphor 图标资产和设计 Token。

## 5. 需要重新核实的旧材料

- `docs/plan/20260926-long-term-execution/README.md:90-96` 的 HEAD 和工作树快照已经过时；
- `SECURITY.md` 仍写旧版本、旧资产名和旧内置 AI/HTTP 事实；它是待治理文档，不是当前安全承诺；
- `docs/design/wear-ui-system.md` 含旧 Wear/弹簧语境，当前手机和动画红线以最新 `src/AGENTS.md`、代码和实测为准；
- 旧计划中的“已验收”不能替代当前版本复测；
- 旧模型的“根因已锁定”必须回到当前源码和测试重新核对。

## 6. 治理范围

本轮治理覆盖：

1. 宿主前端：设置、下载、软件库、工具箱、WebView 外壳；
2. Compose 前端：AI 页面、输入、返回和生命周期；
3. 业务后端：搜索、解析、下载、更新、安装、ADB、持久化和并发；
4. 安全与隐私：外部输入、网络、凭据、日志、备份、Provider 和导出组件；
5. 工程基础：依赖、vendor patch、CI、测试、许可证和发布；
6. 文档：计划、架构、证据、任务交接和回退。

不等于一次性修改所有文件。每次任务必须有自己的边界、验证和回退。
