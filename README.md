# 东方无限 (DongFangWuXian)

一个轻量、零第三方运行时依赖的原生 Android 工具箱：蓝奏云目录浏览 / 全源搜索 / 下载管理，内置 35 个本地小工具，以及可接入任意 OpenAI 兼容中转站的 AI 对话。

- **包名**：`dfwx.dongdang`（Java namespace `cc.nkbr.lanzouplus`，上游遗留，未改动）
- **当前版本**：1.6.3（versionCode 1030014）
- **最低系统**：Android 7.0（API 24） / targetSdk 36
- **技术栈**：纯 Java、单 Activity、程序化 View（无 XML 布局）、`HttpURLConnection` 直连、零第三方 UI/网络库
- **许可证**：AGPL-3.0（见 [LICENSE](LICENSE)）

## 功能

### 软件库 / 搜索 / 下载
- 导入、导出并合并本地或 HTTPS 源规则；并发搜索、分页进度、暂停与继续
- 蓝奏目录与文件夹浏览、缓存与路径导航；下载历史、失败重试、系统安装与文件管理器联动
- 默认下载目录 `Download/东方无限`，所有下载统一归集

### 35 个本地小工具
计算器、单位换算、日期计算、随机决策、记分牌、万年历、随机数、文本统计、Base64、URL 编解码、哈希、JSON、正则、密码生成、UUID、图片压缩、画板、设备信息、屏幕检测、直尺、手电筒、白噪音、文字朗读、生肖星座、身份证解析、年龄计算、BMI、水平仪、秒表、时间戳、进制转换、指南针、频率发生器、随机抽取、摩斯电码。

### AI 对话
- 支持任意 OpenAI 兼容中转站（多渠道管理、`/models` 拉取、连接测试）
- SSE 流式输出、思考过程展示、Markdown 渲染、Token 用量、复制 / 重新生成
- 工具推荐：AI 在回复末尾推荐合适的本地工具，点按钮直达对应工具页
- 助手系统、对话数据模型、生成管线正在按 RikkaHub 的架构逐步完善

## 主题

内置两套主题，可在「设置 → 外观」切换：
- **原生安卓**：经典紫配色（默认）
- **高级苹果**：iOS 分组白卡质感 + 系统蓝 + 快速弹簧动效（含长按底栏的液态玻璃胶囊交互）

## 构建

需要 JDK 17+ 与 Android SDK（build-tools 36.0.0）。

```bash
# 1. 配置 SDK 路径与默认 AI Key（此文件不入库）
cat > local.properties <<'EOF'
sdk.dir=/path/to/android-sdk
ai.default.key=sk-your-own-key
EOF

# 2. 构建 release APK
./gradlew assembleEmptyRelease
```

产物：`app/build/outputs/apk/empty/release/app-empty-release.apk`

> `local.properties`、keystore、`assets/{s,r,c}`（内置源）均已在 `.gitignore` 中排除，不会进入公开仓库。

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

本项目以 AGPL-3.0 发布：你可以自由使用、修改和分发，但**分发修改版本时必须同样以 AGPL-3.0 开源**（包括通过网络提供服务的情形）。
