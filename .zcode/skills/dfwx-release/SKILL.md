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

## 命名与归档(2026-09-22 用户红线:描述性后缀=画蛇添足,禁加)

- 本地归档命名:`东方无限-vX.Y.Z.apk`(无主题后缀)
- GitHub Release:标题=纯 `vX.Y.Z`,资产=`dongfang-wuxian-vX.Y.Z.apk`,说明一两句人话即可;**"修复版/治理版/升级版/体验版"等描述性后缀标题和资产都禁**(用户原话:只会让别人知道这是 AI 做的)
- 归档到 `~/heiyao/黑曜/03-构建产物/`,不删旧 APK

## 发版前自测门禁(2026-09-22 增,v1.19.8 真机卡死事故后;全过才许发)

1. 全量 `:app:testEmptyDebugUnitTest`(只跑 HomeShotsJvmTest 会漏 LibrariesCatalogJvmTest 这类断言过期)
2. 模拟器一律装 release 包(buildTypes 静态声明 debug=x86_64 装不进 arm64 模拟器);启动用 `am start -n dfwx.dongdang/cc.nkbr.lanzouplus.MainActivity`(monkey 会把应用挤出前台,`-n pkg/.MainActivity` 会拼错类名)
3. 静态页(设置页)gfxinfo 指标:jank% 与 p90/p95/p99 相比上一版有异常恶化必须解释后才许发;网络页(软件库/AI)数字噪声大,不可用作构建对比
4. CPU 饥饿模拟(adb shell nohup sh while 循环×3)+连点压力+monkey 之后,logcat 断言无 ANR/FATAL
5. **只测手机视口(2026-09-24 用户指令)**:模拟器=AVD `dfwx-phone34`(1080x2400,GPU host 渲染+4GB 内存,配置见 dfwx-emulator 技能);手表模拟器已全部删除(用户真手表是方的,圆表镜像与真机不符,禁止再建再测);App 页面天然自适应手机屏,截图过目即可,无"黑框"问题(圆表遮罩已随圆表镜像删除)
6. 真机验收铁律不变:模拟器全过≠真机可用,发完必须等用户真机反馈

## 排障速查

- 用户报「应用未安装」→ 先查 badging 的 native-code 与 ABI,再查签名
- 构建产物体积异常 → 先怀疑 ABI 翻转(见上)
- Windows 时代教训(PowerShell 5.1 GBK 读无 BOM 文件、cmd 内联 PowerShell 引号断裂)仅在回到 Windows 环境时适用,Mac 上以 zsh + 独立脚本文件为准
