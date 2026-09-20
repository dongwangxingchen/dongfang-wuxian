# 东方无限 DongFangWuXian

> 一款纯净的原生 Android 工具箱：软件库资源获取 · 37 个离线小工具 · 完整 AI 聊天，手机与手表都好用

![License](https://img.shields.io/github/license/dongwangxingchen/dongfang-wuxian) ![Release](https://img.shields.io/github/v/release/dongwangxingchen/dongfang-wuxian) ![Platform](https://img.shields.io/badge/Android-8.0%2B%20%7C%20arm64--v8a-brightgreen)

**东方无限**是一款原生 Android 应用，把三件事装进一个干净的小体积 APP 里：

- **软件库** —— 多源聚合的资源搜索与下载
- **工具箱** —— 37 个纯离线小工具，覆盖日常方方面面
- **AI 对话** —— 完整内嵌的开源 AI 助手，多渠道多模型

无广告、无追踪、不申请多余权限，所有核心功能开箱即用。

## 五大板块

APP 底部导航即五大板块，各司其职：

### 📚 软件库 —— 找资源

- 内置多分类源路，开箱即用：分类导航 + 好软合集 + 热门应用更新页
- 接入自定义源规则：导入 / 导出 / 合并（本地或 HTTPS），离线首启可重试
- **全源并发搜索**：分页进度、暂停与继续、文件夹递归
- 目录浏览带缓存与路径导航，支持批量提取码配对

### 🤖 AI 对话 —— 问问题

内置完整开源 AI 聊天（RikkaHub），不是一个简化的网页壳：

- 多渠道多模型：OpenAI 兼容 / Claude / Google 协议通吃，随时切换
- 流式输出、思考过程、消息分叉、Token 用量统计
- 助手系统、提示词模板、Skills、联网搜索
- 数据备份（WebDAV / S3）、Web 局域网控制台、TTS 语音朗读
- 新装预置一个**有限额的公共体验渠道**，正式使用建议在「设置 → 渠道」填入自己的 API 地址与 Key

### ⬇️ 下载 —— 管下载

- 统一下载管理：历史记录、失败重试、批量队列
- 下载归集到 `Download/东方无限`，与系统安装器、文件管理器联动
- 可申请电池优化豁免，后台下载不被打断
- 可选 **ADB 静默安装**：免确认自动装（需授权）

### 🧰 工具箱 —— 37 个小工具

全部离线可用，按五类组织：

| 分类 | 工具 |
|---|---|
| 常用工具 | 计算器、单位换算、日期计算、随机决策、记分牌、万年历、随机数、秒表计时 |
| 文字处理 | 文本统计、Base64、URL 编解码、哈希计算（MD5/SHA）、JSON 格式化、正则测试、密码生成、UUID、时间戳转换、进制转换、摩斯电码 |
| 图片工具 | 图片压缩、简易画板、取色器（HEX/RGB/HSL） |
| 设备相关 | 设备信息、屏幕检测、直尺、手电筒、白噪音、文字朗读、水平仪、指南针、频率发生器、分贝仪 |
| 生活查询 | 生肖星座、身份证解析、年龄计算、健康计算（BMI）、随机抽取 |

每个工具都经过算法用例验证与真机验收流程，不是凑数的摆设。

### ⚙️ 设置 —— 随心调

- **两套主题**：原生安卓（经典紫）/ 高级苹果（iOS 白卡质感 + 液态玻璃动效）
- 性能调节：并发数、分页、缓存策略、后台搜索索引
- 网络兼容：UA 自定义、基础链接超时自动切换
- 数据与关于：资源源管理、崩溃日志查看、应用信息

## 为手表优化

东方无限不是手机 APP 的简单缩小：AI 对话全部页面与工具箱均按手表小屏（343dp 级别）逐页截图验收，FAB 遮挡、溢出、触控目标都经过调整——在 arm64 手表上是一个真正可用的独立 APP。

## 下载

前往 **[Releases](https://github.com/dongwangxingchen/dongfang-wuxian/releases)** 下载最新 APK（约 35 MB，arm64-v8a）。

- 系统要求：Android 8.0（API 26）及以上，手机 / 手表均可
- 完整源码即本仓库（AGPL-3.0，与发版同步）

## 支持作者

东方无限**免费、开源**，也不在应用内放任何广告。如果它对你有用，欢迎在「设置 → 诚信付费」自愿支持——金额随意，全凭心意。你的支持是持续更新的动力。

AI 对话功能会消耗模型服务费用：预置的公共体验渠道有额度限制，重度使用请填入自己的 Key。

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

> `local.properties`、keystore 与签名口令不入库。AI 部分上游代码在 `rikkahub/` 目录，与上游 [re-ovo/rikkahub](https://github.com/re-ovo/rikkahub) 保持路径一一对应，定制全部记录在 [rikkahub/PATCHES.md](rikkahub/PATCHES.md) 以便跟随上游更新。

## 致谢与第三方参考

| 项目 | 用途 | 许可证 |
|---|---|---|
| [RikkaHub](https://github.com/rikkahub/rikkahub) | **AI 对话功能的核心**：助手系统、对话数据模型、生成管线、设置体系的信息架构与交互设计 | AGPL-3.0 |
| [LanzouPlus](https://github.com/nekobyran/lanzouplus) | 软件库的蓝奏云目录浏览 / 搜索 / 下载核心引擎 | MIT |
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
