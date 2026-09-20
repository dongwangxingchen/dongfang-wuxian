# 东方无限 DongFangWuXian

> 轻量的原生 Android 工具箱：蓝奏云资源库 · 37 个本地小工具 · 内置完整 AI 聊天

![License](https://img.shields.io/badge/License-AGPL--3.0-blue) ![Release](https://img.shields.io/badge/Release-v1.19.0-green) ![Platform](https://img.shields.io/badge/Android-8.0%2B%20%7C%20arm64--v8a-brightgreen)

- **当前版本**：1.19.0（versionCode 1038000）
- **系统要求**：Android 8.0（API 26）及以上，arm64-v8a 设备
- **技术栈**：蓝奏云/工具部分为纯 Java + 程序化 View（零第三方依赖）；AI 对话部分自 v1.8.0 起整体内嵌开源项目 [RikkaHub](https://github.com/rikkahub/rikkahub)（Kotlin + Jetpack Compose，见 `rikkahub/` 目录与 [rikkahub/PATCHES.md](rikkahub/PATCHES.md)）
- **许可证**：AGPL-3.0（见 [LICENSE](LICENSE)）

## 功能

### 软件库（蓝奏云资源浏览与下载）

- **85 条内置源路**：423down 九分类 + 好软分享合集 + 50 热门应用更新页，开箱即用
- 源规则导入 / 导出 / 合并（本地文件或 HTTPS），导入标记版本化（.v2），探测提交成功后才落旗——离线首启可重试
- **全源并发搜索**：分页进度、暂停与继续
- 蓝奏目录与文件夹浏览、缓存与路径导航
- 下载历史、失败重试、批量提取码配对、系统安装器与文件管理器联动
- 所有下载统一归集到 `Download/东方无限`

### 工具箱 · 37 个本地小工具

| 分类 | 工具 |
|---|---|
| 常用工具 | 计算器、单位换算、日期计算、随机决策、记分牌、万年历、随机数、秒表计时 |
| 文字处理 | 文本统计、Base64 编解码、URL 编解码、哈希计算（MD5/SHA-1/SHA-256）、JSON 格式化、正则测试、密码生成、UUID 生成、时间戳转换、进制转换、摩斯电码 |
| 图片工具 | 图片压缩、简易画板、取色器（HEX/RGB/HSL） |
| 设备相关 | 设备信息、屏幕检测、直尺、手电筒、白噪音、文字朗读、水平仪、指南针、频率发生器、分贝仪 |
| 生活查询 | 生肖星座、身份证解析、年龄计算、健康计算（BMI）、随机抽取 |

全部工具离线可用，无广告、无追踪、不申请多余权限。

### AI 对话（内置 RikkaHub 全功能）

- 聊天历史左侧抽屉、模型底部弹窗切换、完整设置页体系（渠道管理 / 助手管理 / 联网搜索 / TTS·ASR / MCP / 数据备份 WebDAV·S3 / 主题 / Web 局域网控制台等）
- 支持 OpenAI 兼容 / Claude / Google 多协议：SSE 流式、思考过程、消息分叉、Token 用量、联网搜索、提示词模板、Skills、工作区终端
- **关于 AI 渠道**：新装应用预置一个公共体验渠道（服务端已限额，仅供试用）；正式使用建议在「设置 → 渠道」删除后填入自己的 API 地址与 Key

### 主题

内置两套主题，可在「设置 → 外观」切换：

- **原生安卓**：经典紫配色（默认）
- **高级苹果**：iOS 分组白卡质感 + 系统蓝 + 快速弹簧动效（含长按底栏的液态玻璃胶囊交互）

## 下载

| 渠道 | 地址 |
|---|---|
| APK 发版 | [hucxi57-collab/lanzouplus/releases](https://github.com/hucxi57-collab/lanzouplus/releases)（最新 v1.19.0，约 35 MB，arm64-v8a） |
| 完整源码 | 即本仓库（AGPL-3.0，随版同步） |

## 构建

需要 JDK 21、Android SDK（platform 37 + build-tools 37.0.0）、Gradle 9.6（wrapper 已配置；`services.gradle.org` 不可达的环境可改用镜像源，见 `gradle/wrapper/gradle-wrapper.properties`）。

```bash
# 1. 配置 SDK 路径与默认 AI Key（此文件不入库，Key 可省略）
cat > local.properties <<'EOF'
sdk.dir=/path/to/android-sdk
ai.default.key=sk-your-own-key
EOF

# 2. 构建 release APK（约 35MB，arm64-v8a）
./gradlew :app:assembleEmptyRelease
```

产物：`app/build/outputs/apk/empty/release/app-empty-release.apk`

> `local.properties`、keystore 与签名口令不入库；`app/src/empty/assets/`（内置源清单）为有意入库。AI 部分上游代码在 `rikkahub/` 目录，与上游 [re-ovo/rikkahub](https://github.com/re-ovo/rikkahub) 保持路径一一对应，定制全部记录在 [rikkahub/PATCHES.md](rikkahub/PATCHES.md) 以便跟随上游更新。

## 致谢与第三方参考

本项目在设计与实现上参考了以下优秀开源项目，特此致谢：

| 项目 | 用途 | 许可证 |
|---|---|---|
| [RikkaHub](https://github.com/rikkahub/rikkahub) | **AI 对话功能的核心参考**：助手系统、对话数据模型、生成管线、设置体系的信息架构与交互设计 | AGPL-3.0 |
| [LanzouPlus](https://github.com/nekobyran/lanzouplus) | 蓝奏云目录浏览 / 搜索 / 下载核心引擎 | MIT |
| [AndroidVeil](https://github.com/skydoves/AndroidVeil) | 骨架屏与微光扫过理念 | Apache-2.0 |
| [Material Symbols](https://github.com/google/material-design-icons) | Google 官方图标语义体系 | Apache-2.0 |
| [Lottie Android](https://github.com/airbnb/lottie-android) | 动效与无缝过渡的行业标准参考 | Apache-2.0 |
| [SmoothBottomBar](https://github.com/ibrahimsn98/SmoothBottomBar) | 导航激活指示器动效参考 | Apache-2.0 |
| [langchain4j](https://github.com/langchain4j/langchain4j) | AI 对话流式协议解析参考 | Apache-2.0 |
| [openai-java](https://github.com/TheoKanning/openai-java) | OpenAI 兼容 API 规范参考 | MIT |

**关于 RikkaHub**：本项目的 AI 对话模块在信息架构、数据模型与交互逻辑上深度参考了 [RikkaHub](https://github.com/rikkahub/rikkahub)。RikkaHub 采用 AGPL-3.0 许可，本项目同样以 AGPL-3.0 发布，符合其许可条款。感谢 RikkaHub 作者的杰出工作。

## 许可证

[GNU Affero General Public License v3.0](LICENSE)

本项目以 AGPL-3.0 发布：你可以自由使用、修改和分发，也可以对副本收取费用，但**分发（无论是否收费）时必须向获得方提供同样以 AGPL-3.0 授权的完整源码**，且不得附加额外限制（包括通过网络提供服务的情形）。
