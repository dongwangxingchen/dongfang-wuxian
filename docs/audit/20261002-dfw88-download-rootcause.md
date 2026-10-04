# DFW-88「下载卡在解析中」—— 根因已定位（2026-10-02，实测取证）

> **结论先行：不是人机验证卡住，是蓝奏云换了整套下载流程，我们的解析器还在按旧流程找。**
> 而且**新版流程全程不需要用户点任何东西** —— 之前"必须真人过验证"的判断已经过期了。

## 一、为什么以前判断错了

`LanzouCore.java:288-293` 写着：

> 阿里云 WAF 有两种挑战：
> · `acw_sc__v2` = JS 校验，可计算
> · `acw_sc__v3` = **滑块，必须真人交互**，纯算法无解
> **我们撞上的是第二种，所以这是社区未解问题，只能走人工。**

这段结论**当时是对的，现在不成立了**。2026-10-02 实测：撞上的是 `acw_sc__v2`（可计算），
而且**根本不需要滑块** —— 浏览器加载后 1 秒内自己算完 cookie，直接就给下载。

## 二、实测证据（全部可复现）

测试对象：`https://pan.lanzoui.com/b240011/` 里的 `SD Maid.apk`（3.6 M）

### 步骤 1：文件夹页 → 文件列表（AJAX，通）

```bash
curl -X POST "https://pan.lanzoui.com/filemoreajax.php?file=240011" \
  --data "lx=2&fid=240011&uid=516395&puid=<从页面拿>&pg=1&rep=0&t=<页面变量>&k=<页面变量>&up=1"
# → {"zt":1,"info":"sucess","text":[{"id":"iOqAA2dyudc","name_all":"SD Maid.apk",...}]}
```
**注意：`t` / `k` 这两个看起来像 JS 算出来的校验参数，服务端根本没校验**，照抄页面里的值就行。

### 步骤 2：单文件页 `/iXXXX` → 下载入口（**新版流程**）

```bash
curl "https://pan.lanzoui.com/iOqAA2dyudc"
```
返回的 HTML 里有一段 JS：

```js
const link = document.getElementById('ddown');
link.href = '/tp/iOqAA2dyudc?webtp=AjMAYApuA2cCbVQxUzRVYFY_bUGFUdw..._c_c';
```

**注意特征：`/tp/<id>?webtp=<token>`** —— 这是**新版**入口，
而解析器里完全没有 `/tp/`、`webtp`、`ddown` 这几个特征词。

### 步骤 3：`/tp/...` → 真正的下载地址

```bash
curl "https://pan.lanzoui.com/tp/iOqAA2dyudc?webtp=..."
```
返回的 JS 里：

```js
var vkjxld = 'https://slsstm2.dmpdmp.com/file/';
var hyggid = '?AmRQbgAxADEDCgc/V2IBbVNsDjYADApMUXZSGQI/...';
var lanosso = '&lanosso2';
submit.href = vkjxld + hyggid + lanosso;
```

### 步骤 4：抓那个地址 → **撞上阿里云 WAF 的 JS 挑战**

```bash
curl "https://slsstm2.dmpdmp.com/file/?<hyggid>&lanosso2"
```
返回 2714 字节（gzip），解压后是一个**混淆的 JS 反爬脚本**：

```html
<html><script>var arg1='F77233DFEDA62054F49DC87FA10FB5F66BA7C277';
(function(a,c){var G=a0j,d=a();while(!![]){...}})(a0i,0x760bf)...
```

**这就是"解析中"永远不动的现场**：HTTP 客户端拿到的是这段脚本，
它不执行 JS，所以永远等不到文件，也不报错 —— 和用户描述的"卡在解析中"完全一致。

### 步骤 5：用浏览器加载 → **自动过关，拿到直链**（决定性证据）

用 Playwright（真实 Chromium）加载同一个地址：

```
页面 URL: https://slsstm2.dmpdmp.com/file/?...
标题: （空）
cookie 数: 3   →  acw_tc, cdn_sec_tc, acw_sc__v2
最后一个响应: https://c1031.dmpdmp.com/853077b4.../800f35e2f9007db0a191f5076f2efe66.apk?fn=SD%20Maid.apk  (200)
下载事件: SD Maid.apk  ← 真的开始下载了
正文: 如果长时间未下载请手动下载 | 立即下载
```

**`acw_sc__v2` = `6abf74188c6f4d858f38dc65def97ab45998323d`**

**全程零人工交互** —— 没有滑块、没有点选、没有验证码。

### 步骤 6：拿到 cookie 后，纯 HTTP 也能拿到直链（**这是修法的依据**）

```bash
curl -H "Cookie: acw_tc=...; cdn_sec_tc=...; acw_sc__v2=6abf74188c6f4d858f38dc65def97ab45998323d" \
     "https://slsstm2.dmpdmp.com/file/?<hyggid>&lanosso2"
# → HTTP 200，2359 字节 HTML（不再是挑战脚本！）
```
这个 HTML 里就是直链（`<a href>` 与 `window.location.href` 两处）：

```
https://c1031.dmpdmp.com/a14be6c39c39d44571422fbfec03b456/6abf7b2a/2018/11/16/800f35e2f9007db0a191f5076f2efe66.apk?fn=SD%20Maid.apk
```

## 三、修复方案（已由上面 6 步验证可行性）

**核心：加一个"WAF 挑战预热器"，用隐藏 WebView 跑一次 JS，把 cookie 交给 HTTP 客户端。**

1. **补新版流程解析**：认识 `/tp/<id>?webtp=` 与 `slsstm2.dmpdmp.com/file/?<hyggid>`。
   （现有代码只认旧的 `downprocess` + `sign`。）
2. **加 WebView 预热**：
   - 隐藏 WebView 加载挑战 URL → 等 `CookieManager.getCookie(host)` 里出现 `acw_sc__v2`（实测 1 秒内）
   - 把 cookie 交给 HTTP 下载器 → 重抓 → 解析 `<a href>` 拿直链
   - 预热结果**按域名缓存**（cookie 有效期内的后续下载不用再预热）
3. **保留人工兜底**：万一将来又升级成 `acw_sc__v3`（真滑块），
   仍走现有的 `WafChallengeException` + 前端提示，不删。

## 四、诚实声明

- 上述 1-6 步全部是 2026-10-02 在本机实测的原始输出，不是推测。
- 测试走的是本地代理；App 在手机上走直连，
  **域名连通性可能不同**（`slsstm2.dmpdmp.com`、`c1031.dmpdmp.com` 在手机网络下是否可达未验证）。
- 步骤 5 用的是桌面 Chromium；App 内是 Android WebView，
  两者对这段混淆脚本的执行能力**应当一致但未在真机验证**。
