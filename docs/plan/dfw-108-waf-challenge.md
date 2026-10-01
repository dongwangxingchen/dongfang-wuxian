# DFW-108：蓝奏滑块验证 —— WebView 人工过一次 + cookie 复用

> 状态：**进行中**（第 1 步已完成，第 2-5 步未做）
> 起始日期：2026-10-01
> 用户批准：**方案 B**（原话："那没法了，做b吧，要做就做好"）

## 背景：为什么必须走人工

用户从 v1.0.5 报到 v1.0.19，下载**始终失败**。逐层排除后确认最终障碍是
**蓝奏的阿里云 WAF 滑块验证页**（`captchaV2`）。

子智能体读了 **9 个开源仓库的全部源码**，全量 grep
`aliyun_waf|captchaV2|请完成以下操作|验证您是真人|人机验证|滑块` → **0 命中**。

阿里云 WAF 有两种挑战：

| 挑战 | cookie | 能否算法绕过 |
|---|---|---|
| JS 校验 | `acw_sc__v2` | ✅ 可计算（社区都在解，**我们的算法也是对的**） |
| **滑块** | `acw_sc__v3` | ❌ **必须真人交互，纯算法无解** |

**我们撞上的是第二种。这是社区未解问题**，所以只能走人工。

## 用户明确要求：UI 要做好

> "那个需要我认证的UI可以优化好吗，还是必须用他们原生的"

**答**：滑块控件本身**必须用原生的**（它的行为由阿里云的 JS 驱动，改内容会导致验证失效）；
但**框住它的整个体验全是我们的**：

| 部分 | 归属 |
|---|---|
| 滑块/验证控件 | ❌ 阿里云原生（不可改） |
| 窗口外壳、标题栏、圆角、背景 | ✅ 我们（OLED 黑 + 紫） |
| 说明文案 | ✅ 我们 |
| 加载动画 | ✅ 我们（`DfwxsLoadingRing`） |
| 进出场动画 | ✅ 我们（`DUR_*` 时长体系） |
| **验证完自动关闭 + 自动续传** | ✅ 我们（**体验关键**） |

## 实施步骤

### ✅ 第 1 步（已完成）
- 新增 `LanzouCore.WafChallengeException`，**携带 `challengeUrl`**
- `requireActiveShare()` 撞上滑块时抛它，不再混在通用 `PageCapabilityException` 里

### ⬜ 第 2 步：把信号送到 UI 层（**有坑，必须做**）
`DirectLinkResolver` 的回调签名是 `failed(String error)` ——
异常在 `finished(...)` 里被 `failureMessage(error)` **转成了字符串，类型丢失**。
必须改成能携带"这是滑块挑战 + challengeUrl"的结构，
否则 UI 层无法区分"普通失败"和"要弹验证"。

**建议**：给 `Callback` 增加 `challenge(String url)` 默认方法，
`DirectLinkResolver` 捕获 `WafChallengeException` 时走它而不是 `failed(...)`。

### ⬜ 第 3 步：WebView 验证弹窗（`MainActivity`）
- 自绘外壳：OLED 黑 + 紫，标题「需要过一次验证」，
  副文案「蓝奏要求确认你不是机器人，几秒就好」
- `WebView` 加载 `challengeUrl`（**必须是同一个 URL**，阿里云挑战绑定 URL + 会话）
- 安全基线照抄现有 `LanzouWebActivity` 的 WebView 设置（DFW-10 的最小权限基线）
- 加载中用 `DfwxsLoadingRing`

### ⬜ 第 4 步：判定验证通过
- 监听 `onPageFinished`，检查 `CookieManager.getInstance().getCookie(url)`
  是否出现 `acw_sc__v3`（或页面不再是挑战页）
- 通过后：`CookieManager` 取全部 cookie → 关闭弹窗 → 继续

### ⬜ 第 5 步：cookie 注入回下载会话 + 自动续传
- `LanzouCore` 需要一个入口把外部 cookie 注入 `DirectCookiePool` / `NetSession`
- 注入后**自动重试**原来的下载（用户不用再点一次）

## ⚠️ 未验证的关键假设（必须实测）

1. **`acw_sc__v3` cookie 能否跨请求复用** —— 无任何公开记录
2. **有效期多久** —— 未知
3. **是否绑定 IP / UA** —— 未知；如果绑定 UA，注入时必须保持 UA 一致

如果 cookie 无法复用，则退化为"**每次解析都要过一次**"，
那时要考虑：能否在 WebView 里直接把整个下载也走完。

## 前车之鉴（不要再走）

- **服务器中转**：`hanximeng/LanzouAPI` README 首行明写
  「因调用量过大导致**服务器IP被屏蔽**，今后不再提供示例站点」
  → 会让用户唯一的生产服务器 IP 被蓝奏封掉。**用户已放弃此方案。**
- **换域名**：`LanzouAPI` 源码注释「风控 Cookie 与域名关联，强制换域名会导致校验失败」
  → 已在 DFW-106 改为"域名优先、UA 其次"
