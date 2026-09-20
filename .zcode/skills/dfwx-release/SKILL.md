---
name: dfwx-release
description: 《东方无限》发版红线与验收清单(源自 v1.18.0 误发事故复盘,2026-09-20 Mac 环境版)。发布 APK、构建 release、上传 GitHub Release、改版本号之前必读。
---

# 《东方无限》发版红线(Mac 环境,2026-09-20)

## 构建

- 构建命令(JDK 21,绝对路径):`JAVA_HOME=/Users/lishaowei/sdk/jdk-21.0.11.jdk/Contents/Home ~/heiyao/src/gradlew -p ~/heiyao/src :app:assembleEmptyRelease`
- empty 是唯一 flavor;Android SDK 在 `~/Library/Android/sdk`(platform-37.0 / build-tools 37.0.0)
- **严禁**混入任何含 "Debug" 字样的任务名执行 release 构建——v1.18.0 事故根因:ABI 曾按"任务名含 Debug"判断被翻转成 x86_64-only,导致 arm64 手表「应用未安装」。ABI 现为 buildTypes 静态声明(release=arm64-v8a / debug=x86_64),不要动它;AGP9 已移除 `variant.ndk` API,`androidComponents` 里也不可用

## 终验断言(全过才许发)

1. `~/Library/Android/sdk/build-tools/37.0.0/aapt dump badging <apk>` 断言 `native-code: arm64-v8a`
2. 断言 versionName / versionCode 与本次预期一致(版本号只递增小版本号)
3. 任何体积异常必须给出解释;解释不了就查,不许带疑点发布
4. 签名密码在 `~/heiyao/src/local.properties`,严禁外传或写入脚本/日志

## 发布通道(本机网络现实)

- 只走 GitHub Release:发版仓库 `dongwangxingchen/dongfang-wuxian`(2026-09-20 起,原 hucxi57-collab/lanzouplus 废弃);资产上传必须走 `uploads.github.com`(走 api.github.com 上传 404)
- 本机 github.com:443 直连不通(仅 api.github.com 可达);git push 走 SSH over 443(`~/.ssh/config` 已配 Host github.com → ssh.github.com:443),push 失败先想到这条,勿反复重试 https
- gh CLI 已登录用户 dongwangxingchen(含 workflow scope);`.ghtoken` 仅 repo scope(能发 Release 不能推 workflow 文件)
- gofile 在本机网络不通,勿用
- 发 APK 必须履行 AGPL-3.0 同协议源码义务;README 的上游 RikkaHub 署名不得移除

## 命名与归档

- APK 命名:`东方无限-vX.Y.Z-release-<主题>版.apk`
- 归档到 `~/heiyao/黑曜/03-构建产物/`,不删旧 APK

## 排障速查

- 用户报「应用未安装」→ 先查 badging 的 native-code 与 ABI,再查签名
- 构建产物体积异常 → 先怀疑 ABI 翻转(见上)
- Windows 时代教训(PowerShell 5.1 GBK 读无 BOM 文件、cmd 内联 PowerShell 引号断裂)仅在回到 Windows 环境时适用,Mac 上以 zsh + 独立脚本文件为准
