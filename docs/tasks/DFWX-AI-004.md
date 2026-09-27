# DFWX-AI-004：移除东方无限内置 AI 播种和构建注入

状态：待执行（必须在 AI 数据保护方案后实施）
前置：DFWX-AI-003

## 目标

停止东方无限新安装自动创建默认 AI 渠道，并移除 APK 构建期注入的默认 URL、模型和 Key；不删除用户已有 Provider。

## 允许修改

- `BuiltinProviderSeeder` 及其调用；
- `app/build.gradle.kts` 中东方无限 AI 资源注入；
- `app/src/main/res/raw/keep.xml`；
- 旧 AI 提示文案；
- 对应测试、`rikkahub/PATCHES.md`、`SECURITY.md` 和治理任务文档。

## 禁止修改

- 不读取或输出 `local.properties`；
- 不删除所有 OpenAI Provider；
- 不删除所有 `builtIn=true` 的上游默认 Provider；
- 不清空 DataStore；
- 不把用户同地址 Provider 当作内置渠道删除；
- 不改变与本任务无关的下载、设置和 UI 逻辑。

## 完成标准

- 新安装不再播种东方无限内置渠道；
- 构建资源不再注入东方无限默认 URL、模型和 Key；
- 用户 Provider 迁移测试通过；
- 旧内置记录无法可靠识别时保留；
- 相关旧文案和补丁说明不再与代码矛盾；
- 编译和 JVM 回归通过。
