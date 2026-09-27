# 06｜安全与数据治理

## 1. 数据分级

| 类别 | 示例 | 规则 |
|---|---|---|
| 凭据 | API Key、Bearer、Cookie、签名材料 | 不进源码、APK、日志、计划和测试输出 |
| 用户秘密 | 分享密码、直链密码 | 最小留存、可清理、日志脱敏 |
| 用户内容 | 对话、文件名、文件内容、搜索词 | 默认不进网络日志；必要时截断和脱敏 |
| 诊断 | 错误、堆栈、URL、设备信息 | 过滤敏感参数，内部存储，限量 |
| 普通状态 | 页面、开关、非敏感偏好 | 可持久化，但有迁移和版本 |

## 2. 外部下载和安装

外部 Intent、蓝奏分享、解析直链和官方更新是四种不同信任等级：

1. 外部 Intent：不可信输入，只能进入受控、待确认流程；
2. 蓝奏分享 URL：必须经过蓝奏协议和 host 校验；
3. 解析直链：只能由解析器产生，不能由普通字符串伪造；
4. 官方更新 URL：使用已有 UpdateClient allowlist、摘要、包名和签名链。

外部下载安装时：

- 不允许仅凭文件名后缀自动安装；
- 不允许外部 Intent 直接触发自动安装或 Shizuku 静默安装；
- 必须明确告诉用户来源、包名、版本和风险；
- 用户确认后才进入安装；
- 失败不能静默重试成安装。

## 3. AI 边界

用户已决定当前内置 AI 模型和内置渠道直接移除。后续实现要求：

- 停止播种当前内置 Provider；
- 不删除用户自行创建的 Provider；
- 不把长期共享 Key 放入 APK、远程配置或日志；
- 若未来恢复产品内置 AI，必须先有服务端代理、认证、限流、撤销和滥用监控；
- 服务未就绪时，安全关闭内置入口，不放空壳 Key。

## 4. 网络策略

- 普通外部 AI/API 地址只允许 HTTPS；
- HTTP 不因为“用户自己填的”就自动放行；
- localhost/127.0.0.1/IPv6 loopback 未来如需保留，必须是明确例外并限制端口、用途和重定向；
- 更新链保持 HTTPS 和 host allowlist；
- WebView 的未知 scheme、外部跳转和混合内容按最小权限处理；
- 禁止通过关闭全局安全开关解决单个兼容问题。

## 5. 日志、备份和剪贴板

- release 默认关闭详细请求/响应日志；
- headers 采用 allowlist，不采用只补几个黑名单字段的方式；
- body、SSE、完整 URL query 默认不记录；
- crash logger 只安装一次，异常文本过滤，内部存储和限额；
- 备份策略逐 variant 核对，排除凭据、AI 对话、分享密码、下载直链和诊断；
- 复制密码后应有清理策略，不能假设系统剪贴板永远安全。

## 6. 权限和导出组件

建立“组件/权限—业务使用点—替代方案—下线条件”台账，重点复核：

- `MANAGE_EXTERNAL_STORAGE`；
- `REQUEST_INSTALL_PACKAGES`；
- Camera、Microphone、Notification；
- DocumentsProvider、FileProvider；
- MainActivity、RouteActivity、ShortcutHandlerActivity；
- ShizukuProvider 和后台服务。

导出不是漏洞本身，但每个导出入口必须有明确的 scheme、权限、输入校验和负向测试。
