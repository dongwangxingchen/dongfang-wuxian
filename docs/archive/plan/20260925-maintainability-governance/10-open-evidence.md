# 10｜待补证据清单

这些事项不能只靠静态代码或旧文档下最终结论。验证时必须记录设备、版本、前置条件、操作和结果；任何不可逆手机操作都要先取得用户明确同意。

| 编号 | 事项 | 最小证据 | 当前禁止的过度结论 |
|---|---|---|---|
| E-01 | 外部 Intent 是否完整触发自动/静默安装 | fake Intent 测试 + 手机 release 负向测试 | 当前已有静态隔离和 URL policy JVM 测试；不能只凭导出 Activity 断言手机安装器行为已验收 |
| E-02 | 普通下载最终是否访问任意 host/私网 | URL policy 单测 + 本地重定向测试 | 当前已有 HTTPS/私网/loopback/DNS fail-closed 单测；仍不能把设计测试直接称为完整 SSRF 证明 |
| E-03 | 外部 HTTP AI/API 是否实际发送敏感内容 | 配置扫描 + 网络请求测试 | 不能只凭 cleartext 开关断言已有泄露 |
| E-04 | ADB Binder 是否阻塞主线程 | 可控 fake、超时和主线程断言 | 不能只凭 API 名称断言必然 ANR |
| E-05 | 下载历史进程重建和原子写入 | 延迟/中断 fake + 重启恢复 | 不能只看 Future 代码推断数据必丢 |
| E-06 | 动画异常是重复 setter、cancel/restart、布局还是掉帧 | setter 日志、帧间隔、gfxinfo 和对照开关 | 不能直接改 LumaSwitch duration |
| E-07 | 各变体 merged Manifest 的真实权限和导出组件 | 每个 variant 的 manifest/aapt 输出 | 不能只看宿主 Manifest |
| E-08 | APK 是否真的包含非空默认 AI Key | release 产物资源扫描，值不得输出 | 不能由资源名推断既有包已泄露 |
| E-09 | Firebase/其他遥测实际是否初始化和上报 | 依赖、初始化路径和网络观察 | 不能由依赖存在直接认定正在上报 |
| E-10 | vendor WebServer 暴露面 | 默认配置、绑定地址和认证测试 | 不能把上游功能当成宿主已公开服务 |
| E-11 | 字体缩放、冷启动、内存和 jank | 手机 release 同场景数据 | 不能用旧截图或平均帧率替代 |
| E-12 | 更新入口是否仍然可达 | 当前调用图、无更新/有更新/失败测试 | 不能用旧版本已修记录代替当前验证 |

每项完成后回填 `02-risk-register.md`，保留“验证方法和真实结果”。
