# 第三方组件与许可证清单（SBOM 简版）

> 生成时间：2026-09-30 · 对应版本 `1.22.18`
> 用途：满足 DFW-30 的"依赖来源与许可证有记录"要求，并让 AGPL-3.0 分发义务可审计。
>
> **这不是完整 SBOM**。完整 SBOM（含全部传递依赖与版本）应由构建期工具生成
> （见文末"如何生成完整 SBOM"）。本文件登记的是**直接依赖与需要特别署名的组件**，
> 以及供应链上需要人工关注的项目。

## 一、本仓库自身的许可

本项目以 **AGPL-3.0** 发布（见 `LICENSE`）。分发（含通过网络提供服务）必须向获得方
提供同样以 AGPL-3.0 授权的完整源码。

## 二、上游核心组件（必须署名）

| 组件 | 用途 | 许可 | 来源 | 锚定方式 |
|---|---|---|---|---|
| [RikkaHub](https://github.com/rikkahub/rikkahub) | AI 对话模块核心（信息架构、数据模型、生成管线） | **AGPL-3.0** | `rikkahub/` 目录，源码级引入 | 上游 tag `2.5.5`（`rikkahub/PATCHES.md` 登记补丁） |
| [LanzouPlus](https://github.com/nekobyran/lanzouplus) | 蓝奏云目录浏览/搜索/下载引擎 | MIT | 宿主包 `cc.nkbr.lanzouplus`（上游遗留包名） | 已深度定制，无上游同步计划 |

> **RikkaHub 的 AGPL-3.0 署名与源码义务不得移除**（`README.md`「致谢与第三方参考」一节）。
> 有自动化守卫：`DocsConsistencyJvmTest.licenseAndUpstreamAttributionPreserved`。

## 三、供应链风险处置（本次 DFW-30 的重点）

| 风险项 | 原状态 | 处置 |
|---|---|---|
| `com.github.rikkahub:sqlite-android` | 版本写作 **`-SNAPSHOT`**（JitPack 浮动版本 = 默认分支最新 commit）→ 不可复现、绕过评审 | **已钉到不可变 commit `80cedc8888df2fe1d22d7f9bf8d9287f621624be`**（由 JitPack 构建记录确认该 commit 构建成功；pom/aar 均可取） |
| vendor 版本号字面量 | `rikkahub/app/build.gradle.kts` 手改 `"2.5.5"/"190"`，同步上游时易漏改 | 保留现状但标注（PATCHES.md P2）；见"待办" |
| 上游快照 | 原用 master 快照 | 已固定 tag `2.5.5` |
| 浮动版本全局检查 | 未知 | **已核对 `libs.versions.toml` 与各 `build.gradle.kts`：全仓再无 `SNAPSHOT`/`latest.release`/`+` 动态版本** |

有自动化守卫：`DependencyHygieneJvmTest`（浮动版本不得回归）。

## 四、其他直接依赖（按许可归类）

### Apache-2.0
- AndroidX 全家桶（Compose、Activity、Lifecycle、Room、WorkManager 等）
- OkHttp / okio（Square）
- Kotlin / kotlinx（JetBrains）
- Coil（图片加载）
- Ktor（Web 模块）
- `com.termux.termux-app:terminal-view`（终端视图）
- `org.jmdns:jmdns`（局域网发现）
- `org.apache.commons:commons-text`
- `com.google.guava:listenablefuture`（占位，避免与 Guava 冲突）

### MIT
- Koin（依赖注入）
- `io.github.dokar3:quickjs-kt:1.0.15`（JS 引擎绑定，**已由 jvm 变体替换 android 变体**）
- `com.github.Petterpx:floatingx:3.0.0`（悬浮窗）
- `dev.chrisbanes.haze:haze`（毛玻璃）
- `org.slf4j:slf4j-api`

### BSD / 其他
- `ai.sqlite:vector:1.0.0`（向量检索）
- `org.tukaani:xz:1.12`（Public Domain）
- `uk.uuid.slf4j:slf4j-android`（MIT）
- `com.github.rikkahub.jlatexmath-android:jlatexmath:1.5`（数学公式渲染；JitPack）

### 测试专用（不进入发布包）
- JUnit 4（EPL-1.0）
- Robolectric（MIT）
- Kotlin 测试库（Apache-2.0）

> 各依赖的**精确版本**以 `rikkahub/gradle/libs.versions.toml` 与
> `gradle/libs.versions.toml` 为准（本表不复制版本号，避免两份真相源漂移）。

## 五、已移除的组件（保留记录，避免"以为还在"）

| 组件 | 移除版本 | 原因 |
|---|---|---|
| Firebase Analytics | v1.22.13（DFW-9） | 对外承诺"无追踪"；移除后包内不再含广告/归因权限 |
| Firebase Crashlytics | v1.22.x（P15） | 无官方构建插件时在 `FirebaseInitProvider` 阶段崩溃 |
| Paparazzi | 已停用 | 与 AGP 9 不兼容；6 个快照测试停泊在 `tools/parked-tests/` |

## 六、如何生成完整 SBOM

完整依赖树（含传递依赖）建议在构建机上执行：

```bash
# 需要 Gradle 的 dependencyLicenseReport 或 CycloneDX 插件；本项目未内置该插件
# （红线：不擅自引入新依赖）。需要时由用户批准后再加。
```

在插件获批前，可用以下只读命令导出依赖清单作为过渡证据：

```bash
./gradlew :app:dependencies --configuration emptyReleaseRuntimeClasspath > /tmp/deps.txt
```

## 七、待办（诚实记录）

1. **vendor 版本号字面量**（PATCHES.md P2）：同步上游时需人工改 `"2.5.5"/"190"`，
   没有自动化守卫。建议后续用一个 `vendor-version.properties` 单一来源 + 测试校验。
2. **完整 SBOM/许可证报告**：需引入 CycloneDX 或 license-report 插件（属"引入第三方依赖"，
   按项目红线需用户批准），本轮未做。
3. **JitPack 依赖的长期可用性**：`com.github.rikkahub:*` 托管在 JitPack，若上游删库则不可取。
   已通过钉 commit 保证"内容不变"，但**不保证长期可取**；必要时需镜像到自有存储。
