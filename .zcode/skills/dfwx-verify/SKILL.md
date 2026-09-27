---
name: dfwx-verify
description: 东方无限验证阶梯 L0-L3 —— 编辑后按改动风险选最小验证档，避免误用发版档（5~9min）拖慢迭代。任何代码改动后、声称"完成/修好"之前必读。
---

# 东方无限验证阶梯（L0~L3）

> 依据：`docs/tasks/20260926-search-web-overhaul/efficiency-plan.md`（实测数据）。
> 核心事实：**发版档比语法档慢 20~30 倍**。日常迭代误用 `assembleEmptyRelease` 是最大的时间浪费。

## 铁律

**"写完了"不等于"验证过"。** 声称修复前，必须至少跑过 L0；声称 bug 修好，必须跑过 L1 并**看到用例先失败、修复后通过**（否则用例证明不了任何事）。

## 四档阶梯（按改动风险选最小够用的一档）

| 档 | 命令 | 实测耗时 | 何时用 |
|---|---|---|---|
| **L0** | `:app:compileEmptyDebugJavaWithJavac --offline` | **17s** | **每次编辑后**（语法/类型自检） |
| **L1** | `+ :app:testEmptyDebugUnitTest --tests "<相关类>" --offline` | **~13s** | 改了逻辑、正则、解析、比较器 |
| **L2** | `:app:assembleEmptyDebug --offline` | **2m08s** | 需要真机装机看效果 |
| **L3** | `:app:assembleEmptyRelease -x lintVitalEmptyRelease` | **5~9min** | **仅发版**（R8/minify/shrinkResources） |

统一前缀：
```bash
cd <本地目录>/src && JAVA_HOME=/Users/<用户名>/sdk/jdk-21.0.11.jdk/Contents/Home ./gradlew -p <本地目录>/src <档位命令>
```

### 组合示例
```bash
# L0：编辑后立刻自检（17s）
JAVA_HOME=... ./gradlew -p <本地目录>/src :app:compileEmptyDebugJavaWithJavac --offline

# L1：跑单个测试类（比全量快）
JAVA_HOME=... ./gradlew -p <本地目录>/src :app:testEmptyDebugUnitTest --offline --tests "cc.nkbr.lanzouplus.LanzouUnlockFieldsJvmTest"

# L1 全量（~28s，改动面广时用）
JAVA_HOME=... ./gradlew -p <本地目录>/src :app:testEmptyDebugUnitTest --offline
```

## 装机注意（ABI 陷阱）

**debug APK 只含 x86_64（给模拟器），真机是 arm64-v8a → 装不上。**

```
INSTALL_FAILED_NO_MATCHING_ABIS: Failed to extract native libraries, res=-113
```

- 真机验证 → 必须用 **L3 release APK**（`app/build/outputs/apk/empty/release/`）
- 模拟器验证 → 用 debug APK
- 这是 `app/build.gradle.kts` 按构建类型配置 `abiFilters` 的**设计行为**，不是 bug

```bash
ADB=~/Library/Android/sdk/platform-tools/adb
$ADB connect <手机IP>:5555
$ADB -s <手机IP>:5555 install -r <release-apk>
```

## 测试写法的本项目约定

**新增 JVM 测试放** `app/src/test/java/cc/nkbr/lanzouplus/`，类名 `<被测对象>JvmTest.kt`。

**必做负向验证**（否则用例是装饰品）：
1. 先写用例 → 跑一次，确认**在未修复的代码上失败**
2. 再改代码 → 跑一次，确认通过
3. 若用例在旧代码上也通过，说明它没测到真正的东西

本项目已有先例：`LanzouUnlockFieldsJvmTest` 用页面原文片段（含裸数字 `'lx':2`、无 `var` 的 `pgs =1;`），
在旧版 `formValues` 上 **4/5 失败**，修复后 5/5 通过。

**测试私有静态方法的写法**（`formValues` 等）：
```kotlin
val m = LanzouCore::class.java.getDeclaredMethod("formValues", String::class.java)
m.isAccessible = true
@Suppress("UNCHECKED_CAST")
val fields = m.invoke(null, html) as Map<String, String>
```

## 当前测试基线

`app/src/test/java/cc/nkbr/lanzouplus/` 下 7 个类，**共 20 例，全绿**（2026-09-26）：
`HomeShotsJvmTest`(3) / `LanzouUnlockFieldsJvmTest`(5) / `LibrariesCatalogJvmTest`(2) /
`ToolboxLogicJvmTest`(3) / `ToolsShotsJvmTest`(1) / `ToolsSweepJvmTest`(1) / `UpdateClientJvmTest`(5)

**新增改动不应让这个数字下降。**
