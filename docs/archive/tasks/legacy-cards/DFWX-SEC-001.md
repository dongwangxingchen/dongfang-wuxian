# DFWX-SEC-001：外部下载和安装边界

状态：⚠️ 代码与 JVM 策略测试已完成；手机 release、真实重定向和外部 APK 完整校验**未做，且用户 2026-09-27 已豁免日常下载/安装类真机验证 → 余项不再补做**（护栏仍生效：外部/legacy APK 一律不允许静默安装，安装前必须用户确认）
前置：BASE-001

## 目标

确保外部 Intent/WebView 下载不能直接进入自动安装或 ADB/Shizuku 静默安装，并与蓝奏云解析直链缓存隔离。

## 当前证据

详细记录见 `docs/archive/plan/20260925-maintainability-governance/DFWX-SEC-001-external-download-install-boundary.md`（已归档的旧资料，仅作历史证据）。

已完成：来源分型、HTTPS/私网策略、重定向复核、外部/legacy 确认安装、JVM 测试和完整 JVM 回归。

## 待完成

- 外部 APK 包名、签名、versionCode/versionName 校验；
- 本地 fake HTTPS 重定向；
- DNS rebinding/连接 IP 固定研究；
- 手机 release 外部 Intent 负向验证；
- WebView、FileProvider、安装器和各 variant Manifest 证据。

## 禁止

不清数据、不卸载、不删除用户文件；不把未验证项写成 SSRF 或手机已修复；不绕过用户确认。

## 完成标准

所有待完成证据有测试/产物/设备记录，外部来源不能自动或静默安装，官方更新链不被破坏。
