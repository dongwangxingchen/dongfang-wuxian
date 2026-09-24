---
name: dfwx-emulator
description: 《东方无限》本地模拟器自测跑法(2026-09-24 改为手机-only)。构建出 APK 后在手机模拟器 dfwx-phone34 上无人值守验证:开机→装机→启动→截图→触控→崩溃扫描。任务涉及本地测试 APP、验证 APK 能否运行、发版前自测时使用。
---

# 《东方无限》手机模拟器自测跑法(2026-09-24 起,手表已删)

**红线(用户 2026-09-24 指令):只测手机,不测手表。** 用户真手表是方屏,圆表 Wear 镜像与真机不符,
`dfwx-wear34` AVD 与 android-wear 系统镜像已删,**禁止再建再测**。App 页面天然自适应,手机模拟器即唯一自测环境。

环境:Android Emulator 37.1.11 + AVD `dfwx-phone34`(pixel_6,**1080x2400@420**,android-34 google_apis arm64)。
**关键配置(GPU/内存错了会整台模拟器卡死+系统UI反复ANR,已写进 AVD config):**
`hw.gpu.enabled = yes`、`hw.gpu.mode = host`(Apple Silicon Metal/gfxstream 直通)、`hw.ramSize = 4096M`。
改 AVD 配置:`~/.android/avd/dfwx-phone34.avd/config.ini`,改完必须冷重启(`-no-snapshot`)才生效。

## 1. 后台开机

```bash
~/Library/Android/sdk/emulator/emulator -avd dfwx-phone34 \
  -gpu host -no-snapshot -no-audio &
```
- `-gpu host` 必带(双保险,与 config 一致);省略会用 config,config 已是 host
- 等待:`adb wait-for-device` + 循环 `getprop sys.boot_completed` 到 `1`(实测 2-4 分钟)
- 正常设备名 `emulator-5556`(5554 空出但不要再挂任何手表镜像)

## 2. 安装与启动(顺序不可反)

```bash
adb=~/Library/Android/sdk/platform-tools/adb
$adb install -r <最新APK>        # 必须 release 包:debug=x86_64 装不进 arm64 模拟器
$adb shell am start -n dfwx.dongdang/cc.nkbr.lanzouplus.MainActivity
```
- **必须完整类名** `cc.nkbr.lanzouplus.MainActivity`——`-n pkg/.MainActivity` 会拼错类名,
  `monkey -p` 会把应用挤出前台污染后续截图
- 验证:`$adb shell pidof dfwx.dongdang` 有 pid;冷启动 TotalTime 实测 ~600ms

## 3. 视觉与交互验证

```bash
$adb exec-out screencap -p > /tmp/dfwx-test.png   # 用 Read 工具看图
$adb shell uiautomator dump /sdcard/ui.xml && $adb shell cat /sdcard/ui.xml   # 精确定位控件坐标
```
- 首页应见"东方无限"品牌 + 底部五标签(软件库/AI对话/下载/工具箱/设置,y≈2274)
- 交互流畅度口径:**静态设置页** gfxinfo 有判别力(网络页软件库/AI 噪声大不可用作构建对比);
  本机 GPU host 实测参考:设置页滚动 p50≈24ms/p90≈57ms,全流程含冷启转场 p90≈44ms
- 崩溃扫描:`adb logcat -d | grep -c "AndroidRuntime.*FATAL"` 应为 0;非 0 抓全栈
- 模拟器绝对帧时间仅供趋势对比,真机性能以用户手机实测为准

## 4. 收尾

```bash
$adb emu kill    # 干净关机;确认 adb devices 里模拟器消失
```

## 约束与注意

- **磁盘**:手机镜像+userdata 占用更大,开跑前确认余量 ≥5GB(`df -h /System/Volumes/Data`)
- 本地模拟器测试**不能替代**用户真机验收(AGENTS.md 铁律),它是发版前的自测层
- SwiftShader 软渲染(配置错 gpu.enabled=no 时)下 1080x2400 系统 UI 会反复 ANR 弹
  "System UI isn't responding"——那不是应用 bug,先查 GPU 配置
- 构建新 APK 的红线看 `dfwx-release` 技能;sdkmanager 日后加包必须加 `--no_https`(本机 Java HTTPS 故障)
