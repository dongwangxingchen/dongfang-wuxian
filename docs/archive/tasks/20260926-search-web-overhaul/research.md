# T10 研究与方案（2026-09-26）

> **历史研究材料，当前执行入口见 [`docs/plan/20260926-long-term-execution/`](../../plan/20260926-long-term-execution/)。** 本文保留 `audit.md` 后的研究结论和当时方案，不直接授权实施，也不把参数、顺序或技术路线固定给后续模型。
> 研究手段包括本地代码勘察、真机实证和历史并行调研。结论应在对应专题用当前代码、样本和用户反馈复核；新证据可纠正本文建议。

---

## 零、本次研究的三个"翻案"（推翻了此前判断）

| 此前判断 | 实测结论 | 证据 |
|---|---|---|
| 「顶部那个条 = WebView 自带进度条」 | **错**。`LanzouWebActivity` 既无 `WebChromeClient` 也无 `FEATURE_PROGRESS`，**App 侧一个进度条都没画**。用户看到的"条"要么是蓝奏网页自身 UI，要么是白底→内容的突变 | 通读全文 31 行 + `WebView` 只有 `getProgress()` 无自绘进度条 |
| 「设备 4GB 内存，所以并发被压低」 | **错**。真机 `MemTotal=15561772 kB ≈ 14.8GB` | `adb shell cat /proc/meminfo` |
| 「图标不显示是网络/缓存问题」 | **部分错**。`LanzouCore.java:606` 兜底图标 URL 实测 **404**，而 1210/1216 用的正确路径 **200**。**缓存救不了 404** | `curl` 实测：`/images/folder.gif`→404、`/assets/images/type/folder.gif`→200 |

---

## 一、搜索卡在「3/50 源 · 已完成 0」——根因链已定位

### 1.1 机制真相

- `onProgress(done,...)` **只在源"完成"时**触发（`finishSourceLocked` 里 `done.incrementAndGet()`）。
- `onActivity(active,total,...)` 在并发槽位变化时触发。
- → **`active=3 且 done=0` 的含义：3 个源在跑，一个都没跑完。** 文案把"完成数"放在最显眼位置，用户看到 `0` 就以为卡死。
- 单源最坏 `SOURCE_PROBE_TIMEOUT_MS=35s`，且 `adaptiveSourceWorkers` 里 `pressureFactor=1/sqrt(1+ROUTE_PRESSURE)` **会动态压低并发**；`heapFactor=sqrt(headroom/max)` 在堆压力大时进一步收缩。

### 1.2 真正的体验病灶（三条，都是"感知"问题）

1. **进度叙事错了**：以"源"为单位报进度（`已完成 0/50`），但用户要的是"内容"（`已找到 N 条`）。**50 个源里前几个最慢的源决定了他看到 `0` 的时长。**
2. **已有本地索引没被"当主角"**：`cachedPartialIndexMatches` / `readDailySearchCache` / `cachedDirectoryMatchesWarm` 三条本地链路**都是异步 fire-and-forget**，结果和网络结果混在同一个流里追加。用户看到的 `找到 4` 就是索引命中，但他不知道，只看到 `已完成 0`。
3. **`percent` 恒为 0 → 进度条变 indeterminate 转圈**（`refreshSearchUi`：`progress.setIndeterminate(running && !paused && percent==0)`），转圈=无限等待感。

### 1.3 方案（三层）

**A. 首屏策略：本地索引立即呈现（0ms）**
- 搜索发起时**同步**读取 `readDailySearchCache` + `cachedPartialIndexMatches`，**先渲染**，状态行明确写 `本地已有 N 条 · 正在联网补充`。
- 这是"非常非常快地把全部内容呈上来"的唯一正解——**本地有就是 0ms**。

**B. 进度叙事重构：以"条数"为主，源为辅**
- 主文案：`已找到 N 条`（大、主色）
- 副文案：`联网补充中 3/50 源`（小、次要色）
- 去掉 `已完成 0` 这种让人误判的表述；**改成 `已扫 3 个源` 且与条数并列**。
- 进度条：`percent>0` 时用真实值，否则**用"已找到条数/预估总条数"驱动**，不再无限转圈。

**C. 超时分级 + 快速失败**
- `SOURCE_PROBE_TIMEOUT_MS=35s` → **分级**：首屏快速探测 8~10s 出结果就推，超时的源转入后台继续（不阻塞"已完成"计数）。
- `ROUTE_PRESSURE` 收缩逻辑保留（它是防封的正确设计），但要**在 UI 上体现**（如"网络受限，已降速"），不要静默变慢。

---

## 二、默认按最新排序 —— 方案已定（研究结论明确）

### 2.1 研究结论（4 家主流实践一致）

| 产品 | 默认排序 | 时间的位置 |
|---|---|---|
| Google Play / App Store / eBay / Amazon | 相关性 + 热度加权 | **时间不是主因子**（可切换） |
| 华为云盘 / Google 按日期排序 API | **时间倒序** | **就是默认** |

**规律**：有热度/评分信号的产品用相关性加权；**没有热度信号、内容是"文件"而非"商品"的产品，默认就是时间倒序**。本 App 属于后者 → **用户要"最新在最顶部"是符合主流实践的，不是任性**。

**"不埋没好内容"的正解**：本 App **没有任何质量信号**（无下载量/评分/点赞），所以"不埋没"**不能靠排序公式**（HN/Reddit 那类公式全靠票数 P，套进来会退化成纯时间序）。只能靠**可见性手段**：
1. 排序切换入口（最新/最旧/大文件/小文件）
2. NEW 角标（近 7 天）
3. 同源打散（防某个库刚批量上传老文件就霸屏整页）

### 2.2 排序器设计（五键比较器）

```
compare(a, b):
  1. 有无时间：有时间(0) < 无时间(1)        // 空时间一律沉底
  2. 时间降序：解析 YYYY-M-D 为整数，大者在前
  3. 文件夹优先（仅当时间完全相同时）
  4. 来源库名（字典序，保证跨批次稳定）
  5. url（唯一值，最终 tie-breaker）
```

**第 5 条是硬要求**：Solr 官方文档明确警告，所有排序键并列时引擎用内部 docID 兜底，该值在段合并时会变，会导致顺序意外变化。流式追加场景没有确定性 tie-breaker 就会看到同一天的条目在批次间乱跳。

**空值处理**：一律沉底，UI 显示"时间未知"。**不要用 1970 或极大值填充**（前者像最旧、后者像最新，都误导）。

### 2.3 流式追加的排序稳定性（关键 UX 问题）

- **用户未滚动时**：新内容插到正确位置（顶部），正是他要的。
- **用户已滚动时**：位置不动（用稳定 key `item.url` 维持可视锚点）。
- **不要做"点击加载 N 条新内容"药丸**——那是社交时间线（无终点）的解法，本场景是有限结果集，药丸会让用户误以为内容被藏着。

### 2.4 时间解析

`item.time` 已是 `YYYY-M-D` 文本（`LanzouCore.java:596` 正则归一化），需补**解析成可比整数**（`int y*10000+m*100+d`）。注意 `MainActivity` 已有"日期未知"兜底文案可复用。

---

## 三、搜索框右侧放什么 —— 推荐「排序」为主 + 「内容类型」为辅

### 3.1 用户质疑是对的，但冗余是"状态相关"的

- **落地态**（未聚焦）：下方 `homeLibsBand` 显示全部软件库胶囊 → 下拉里的"源分类"确实重复。
- **搜索态**（已聚焦）：`showHomeSearchMode()` 执行了 `homeLibsBand.setVisibility(View.GONE)` → **软件库列表消失，此时下拉是唯一的范围控制**。

→ 正解不是删掉范围筛选，而是**把范围选择挪到它该在的地方**（结果页顶部来源胶囊 / 点结果上的来源徽章就地筛选），腾出搜索框右侧放**正交维度**。

### 3.2 推荐方案

搜索框右侧 → **排序选择器**（默认"最新在前"）：

```
排序   ● 最新在前（默认）
       ○ 最旧在前
       ○ 大文件在前
       ○ 小文件在前
```

**理由**：用户已在需求里点名要"默认按最新排序"，而这恰是全 App **唯一完全不存在的能力**（`sortSearchItems` 只有一行文件夹优先，`rg 排序` 在 MainActivity 零命中，没有任何排序 UI）。且流式搜索下结果按"哪个源先返回"追加到尾部，用户看到的是**乱序堆积**——排序是唯一能治这个的维度。

**「内容类型」作为可选第二组**（同一面板内分组，不额外占搜索框宽度）：
- 风险：搜索结果**没有真实扩展名**，只有标题。现有 `fileKindLabel` 只认 `.apk/.apks/.xapk/.zip/.rar/.7z` 六种后缀，其余一律"文件"；很多标题（尤其文件夹和合集）没有后缀 → 会大量落进"其他"，筛完可能只剩零头。
- → **建议先做真实数据采样**（统计一批搜索结果标题的后缀命中率）再决定是否上。

**一个有力证据**：YouTube 在 2026 年初移除"按上传日期排序"引发大量用户抗议，被认为"移除了小频道最后的可靠曝光方式"。说明**时间排序不是可有可无的装饰**——对"资源会失效"的网盘聚合搜索，价值只会更高。

---

## 四、图标缓存 —— 方案已定，含两个确定性根因修复

### 4.1 确定性根因（缓存救不了的部分）

1. **`LanzouCore.java:606` 兜底图标 URL 是 404**：`https://images.bakstotre.com/images/folder.gif` 实测 404，正确路径是 `/assets/images/type/folder.gif`（同文件 1210/1216 行用的就是对的）。**凡是走这条兜底的文件夹图标，无论缓存怎么加都永远显示不出来。**
2. **`loadCachedFolderImage` 静默 return**：内存无缓存且当前无 in-flight 请求时直接 return，既不加载也不投递 → View 永久停留占位。
3. **失败无重试无兜底**：`requestImage` 的 catch 后 `bitmap==null` 不投递，但 `imageWaiters.remove(url)` 已执行 → 等待者被丢弃。

### 4.2 实测发现：图标服务器**支持条件请求**

子智能体实测了真实图标服务器（伪装 UA + Referer）：

```
bakstotre: ETag "5cc53464-33f" + Last-Modified + Cache-Control: max-age=2592000
  If-None-Match  → 304 Not Modified ✅ 支持
  If-Modified-Since → 304 Not Modified ✅ 支持
dmpdmp: ETag 在 CDN 不同边缘返回不同值
  If-None-Match → 200（不生效）❌ 不可信
```

→ **"换了就更新、没换就用缓存"不需要猜**，用条件请求即可精确实现，带宽几乎为零。但 dmpdmp 的 ETag 不可信 → **必须 TTL 兜底**。

### 4.3 方案：L1 内存 + L2 磁盘 LRU + 条件请求 + 负缓存

| 项 | 建议值 | 依据 |
|---|---|---|
| L2 目录 | `getCacheDir()/icon-cache/` | cacheDir 可被系统随时回收，读时必须容错 |
| 文件名 | `SHA-256(url)` 十六进制 | 符合 DiskLruCache 键规则 `[a-z0-9_-]{1,120}`，避免 URL 特殊字符 |
| L2 存什么 | **原始字节 + 元数据侧车（ETag/Last-Modified/长度/写入时间）** | Glide `AUTOMATIC`：远程数据只存未修改源数据，解码参数变了也不用重下 |
| L2 容量 | 32 MB（图标 0.5–20 KB/张） | Coil 默认策略的量级 |
| TTL | 软过期 7 天：先投递缓存，再后台条件请求续期 | 纯 LRU 不按时间淘汰会导致陈旧内容长期占盘 |
| 重试 | 2 次，300ms/900ms + 抖动，仅对 IOException/5xx/429 | 4xx 重试无意义 |
| 负缓存 | **仅内存**：网络错误 60s、404/410 10min，**绝不落盘** | 负缓存必须用独立 TTL，避免放大故障 |

**核心逻辑**：
```java
// 读盘命中 → 立即投递；仅当 age > TTL 时才后台再验证
c.setRequestProperty("If-None-Match", meta.etag);        // 有 ETag 优先
c.setRequestProperty("If-Modified-Since", meta.lastModified); // 无 ETag 时退化
int code = c.getResponseCode();
if (code == 304) { touch(entry); return cachedBytes; }   // 续期，复用原字节
if (code == 200) { replace(entry, body, etagOf(c)); }    // 源真的换了
```

**无缓存头的服务器兜底**（Glide `signature()` 思路）：把版本信息混进缓存键——用 `sha256(url + "|" + time + "|" + size)` 当键，源更新时自然产生新键，旧条目走 LRU 淘汰。

**磁盘 LRU 三要点**（照抄 DiskLruCache 核心）：
1. **原子写**：写 `<key>.tmp` → `renameTo(<key>)`（同分区 rename 原子）
2. **崩溃恢复**：打开时删掉"有 DIRTY 无 CLEAN"的条目；journal 解析失败就清空重来
3. **容量淘汰**：单后台线程 `trimToSize()`；所有写操作串到单线程 executor（项目已有 `searchIndexIo` 这个模式）

**失败处理**：投递统一化——成功投递 bitmap，**失败也投递一个"失败信号"**，让 View 落到明确错误态（稳定默认图标 + 可点重试），而不是无限悬空。占位图（加载中）与错误图（已知失败）语义必须区分。

**列表滚动错图**：项目已有正确基础（`view.setTag(url)` + 投递时校验）。要补的是把 tag 校验扩展到**所有**投递路径（内存命中、磁盘命中、失败回调），并加 `view.isAttachedToWindow()` 校验。

**可选捷径（30 行拿 80% 收益）**：`android.net.http.HttpResponseCache` 是 SDK 自带（AOSP master 仍在，未标 deprecated），装在 `getCacheDir()/http` 即可让 `HttpURLConnection` 自动处理条件请求与磁盘缓存。**但只适合作为过渡**（无逐 URL 控制、无负缓存策略、可能缓存 404）。

---

## 五、蓝奏云网页页 —— 方案已定（含一个"当前实现是错的"）

### 5.1 预测性返回：当前实现同时踩了两个坑

`LanzouWebActivity` 现在**同时**做了两件致命的事：裸 `registerOnBackInvokedCallback(PRIORITY_DEFAULT, ...)` + 覆写 `onBackPressed()`。

官方原文：**"如果您的应用启用了 OnBackPressedCallback 或 OnBackInvokedCallback（带有 PRIORITY_DEFAULT 或 PRIORITY_OVERLAY），则预测性返回动画不会运行"**。所以注册回调本身就会吃掉系统动画。

**正确做法**：改用 `androidx.activity.ComponentActivity` + `OnBackPressedCallback`（唯一两条路都通的写法，因为 `enableOnBackInvokedCallback=false` 时 `OnBackPressedCallback` 仍生效）：

```java
public final class LanzouWebActivity extends androidx.activity.ComponentActivity {
  // 删除 onBackPressed() 覆写 + 删除裸 registerOnBackInvokedCallback
  backCallback = new androidx.activity.OnBackPressedCallback(false) {
    @Override public void handleOnBackStarted(BackEventCompat e){
      backStartCanGoBack = web.canGoBack();   // 手势开始即冻结判定
    }
    @Override public void handleOnBackProgressed(BackEventCompat e){
      if (!backStartCanGoBack) return;        // 不能返回就不跟手，交给系统
      float p = Math.max(0f, Math.min(1f, e.getProgress()));
      shell.setScaleX(1f - 0.04f * p);
      shell.setScaleY(1f - 0.04f * p);
      shell.setTranslationX((e.getSwipeEdge()==EDGE_LEFT?-1:1) * dp(20) * p);
      shell.setAlpha(1f - 0.12f * p);
    }
    @Override public void handleOnBackCancelled(){ /* VPA 220ms 弹回 */ }
    @Override public void handleOnBackPressed(){
      if (backStartCanGoBack && web.canGoBack()) web.goBack(); else finish();
    }
  };
  getOnBackPressedDispatcher().addCallback(this, backCallback);

  @Override public void doUpdateVisitedHistory(WebView v, String url, boolean reload){
    backCallback.setEnabled(v.canGoBack());   // 能返回→应用内跟手；不能→系统播"返回桌面"
  }
}
```
Manifest 显式补 `android:enableOnBackInvokedCallback="true"`。

### 5.2 加载态：让"慢"消失的三条规则

用户抱怨"这个动画让我觉得加载特别慢"，根因是**无界等待 + 停滞的进度**。`onProgressChanged` 的返回值不可信（DNS/TLS 阶段长期停在 10% 附近然后跳到 100%），直接绑定就会出现"爬得很慢"的观感。

```
规则1 延迟出现：180ms 内不显示任何进度。多数页面 300ms 内出内容 → 进度条从不出现 → 没有"慢"的记忆点。
规则2 出现即前进：显示后 displayed = max(real, displayed)，并由 80ms tick 的 Runnable 渐近逼近 0.9，永不静止。
规则3 提前结束：onPageCommitVisible 就收尾（比 onPageFinished 早得多），120ms 补到 100% 再 140ms 淡出。
```

**`onPageFinished` 会等所有子资源，用它结束进度条是"感觉慢"的直接原因**——换成 `onPageCommitVisible` 是收益最大的单点改动。

WebView 无超时 API：`onPageStarted` 起一个 15s `postDelayed` 看门狗，commit/finish/error 时清除；超时即走错误页。**有界失败远好于无限爬行。**

### 5.3 顶部栏重构

| 区域 | 现在 | 改为 |
|---|---|---|
| 左 | `‹` | `‹`（`canGoBack()==false` 时降为 40% 透明，暗示"再返回就退出"） |
| 中 | URL 单行 MIDDLE 省略 | **两行**：上行=页面标题（`onReceivedTitle`），下行=host |
| 右 | `⋮` | `⋮`（新增"刷新""字体大小"） |
| 底边 | — | **2dp 进度线**（`capsule.setClipToOutline(true)` 已存在 → 自动被圆角裁切，零成本） |

关键：`MainActivity:1752 openWebPage()` 已持有 `source.title`，**加一个 `EXTRA_TITLE` 透传，首帧就能显示真实标题**——投入产出比最高的一招（零风险、零动效），用户第一眼看到的是"某个软件名"而不是空白/URL。

### 5.4 错误态

```java
onReceivedError(v, req, err)     → 必须 if(!req.isForMainFrame()) return;
onReceivedHttpError(...)         → 同样必须判 isForMainFrame()
onReceivedSslError(...)          → handler.cancel()，绝不 proceed()
```

错误页内容：图标 + 一句人话标题 + 一句原因 + 主按钮「重试」+ 次按钮「用浏览器打开/复制链接/返回」+ 底部小字显示 URL。

文案按 `errorCode` 映射（**不要统一"请稍后重试"**）：`ERROR_HOST_LOOKUP`→"找不到这个网址，域名可能已失效"；`ERROR_TIMEOUT`→"网络太慢，超时了"；`ERROR_CONNECT`→"连不上服务器"；SSL→"网站证书有问题，为安全已停止"。

**展示前先静默自动重试 1 次**——蓝奏云域名池抖动很常见，多数瞬时失败能自愈。

### 5.5 质感提升（纯 SDK）

```java
WebSettings s = web.getSettings();
if (Build.VERSION.SDK_INT >= 33) s.setAlgorithmicDarkeningAllowed(true);
else if (Build.VERSION.SDK_INT >= 29) s.setForceDark(WebSettings.FORCE_DARK_ON);  // 33 已废弃，必须版本分支
web.setBackgroundColor(BG);              // 消除白闪（Chrome 官方建议）
web.setVerticalScrollBarEnabled(false);
web.setHorizontalScrollBarEnabled(false);
web.setOverScrollMode(View.OVER_SCROLL_NEVER);  // 去掉与深色主题违和的边缘辉光
web.setLongClickable(false);             // 关闭长按选中/搜索弹窗
s.setSupportZoom(true); s.setBuiltInZoomControls(true); s.setDisplayZoomControls(false);  // 留双指缩放、去掉丑陋 +/- 按钮
s.setAllowFileAccess(false); s.setAllowContentAccess(false);
s.setCacheMode(WebSettings.LOAD_DEFAULT);  // 复用 HTTP 缓存 = 二次访问近瞬开
```

### 5.6 坑与陷阱（必读）

1. **`onReceivedError` 对子资源也触发**（API23 起）——不判 `isForMainFrame()` 就会因为一张图挂了而误报整页错误。同理 `onReceivedHttpError`。
2. **陈旧回调覆盖新页面**：用自增 `loadGeneration` + 比对 `req.getUrl()` 丢弃过期回调。
3. **`onReceivedSslError` 里 `proceed()` 会被 Google Play 判违规**，必须 `cancel()`。
4. **`setForceDark` 在 API33 已废弃**，必须版本分支否则 lint 报错。
5. **无 `WebChromeClient` ⇒ JS `alert/confirm` 被静默丢弃**——蓝奏云的密码/提示弹窗可能失效。应一并实现 `onJsAlert/onJsConfirm`。
6. **`setSupportMultipleWindows(false)` 下 `target=_blank` 行为随 WebView 版本而异**，蓝奏云下载按钮可能静默无响应。建议在 `onCreateWindow` 里把 URL 拉回当前 WebView，并真机验证。
7. **下拉刷新与边缘返回手势冲突**：自研下拉只在 `web.getScrollY()==0` 且手势以纵向为主时激活，**绝不能消费左右边缘手势**。

### 5.7 动效参数（与项目既有约定一致）

| 场景 | 时长 | 曲线 |
|---|---|---|
| 进度条淡入 | 140ms | `PathInterpolator(0.2,0,0,1)` |
| 进度条收尾 + 淡出 | 120 + 140ms | 同上 / 线性 |
| 返回跟手 | 0ms（直接 set） | — |
| 松手弹回 | 220ms | `PathInterpolator(0.34,0.80,0.34,1)` = 项目 EFFECT 曲线 |
| 错误页入场 | 200ms | `PathInterpolator(0.2,0,0,1)` |

全部 ≤300ms、全部 `ViewPropertyAnimator`，复用 `motionEnabled()` 做降级。

---

## 六、目录页翻页体验

### 6.1 "加载半天不出来"的根因

`expandMore()`（MainActivity.java:811）：
- 若 `visible < current.size()`：本地追加
- 否则若 `folderHasMore`：`folderLoadingMore=true` + **`ui.postDelayed(folderLoadRunnable, wait)`**，`wait = folderNextReadyAt - now`
- → **`folderNextReadyAt` 是"下一页最早可请求时间"**（限速保护），等待期间 UI 显示"正在加载下一页"但**实际还没发请求**。

**限速常量**（LanzouCore.java:41）：`ORIGIN_PAGE_SLOT_MS=1100`、`NEXT_PAGE_FLOOR_MS=3000` → **下一页最少等 3 秒**（`Math.max(profile.interval, page>1 ? 3000 : 0)`）。

这是**防封的必要设计**，不能删。但当前 UI **完全没有体现"在等限速"**，用户只看到"正在加载下一页"转圈。

### 6.2 方案

1. **等待期给出真实反馈**：`正在加载下一页 · 还需 2 秒`（用 `folderNextReadyAt` 算倒计时），或进度环显示等待进度。
2. **失败后自动重试 1 次 + 手动重试按钮**（当前只有"请稍后重试"文字，没有按钮）。
3. **加载态/失败态/到底态 UI 重设计**（替换"正在加载下一页"文字条）。

---

## 七、研究顺序与工作量评估

| 序 | 域 | 关键动作 | 风险 | 预估 | 计划表任务号 |
|---|---|---|---|---|---|
| **R1** | 搜索调度与速度 | 首屏本地索引同步呈现 + 进度叙事重构 + 超时分级 | 中（动调度核心） | 大 | T10-R1 |
| **R2** | 搜索排序 | 五键比较器 + 时间解析 + 排序切换入口 | 低 | 中 | **T17** |
| **R3** | 图标缓存 | **先修 404 根因** + 两级缓存 + 条件请求 + 失败兜底 | 低（新增独立模块） | 中 | **T16** |
| **R4** | 目录翻页 | 等待倒计时 + 重试按钮 + 状态 UI 重设计 | 低 | 中 | T10-R4 |
| **R5** | 蓝奏网页页 | 预返回重写 + 进度条 + 错误页 + 顶栏重构 + 深色 | 中（返回逻辑易踩坑） | 大 | T10-R5 |
| **R6** | 搜索框分类 | 排序选择器（依赖 R2） | 低 | 小 | T10-R6 |

> **编号权威声明**：本表是 R1~R6 的**唯一权威定义**。`audit.md`（梳理草案）里的 R3~R6 编号与此**错位**，已在该文件加作废说明，勿引用。
> **额外任务**：**T18**（搜索结果渲染窗口自动增长）由侦察发现，不属于 R1~R6 任一域，但属同一批体验缺陷，建议与 R1 一起做。
> **计划表**：`docs/tasks/20260925-ui-overhaul/plan.md`

**建议执行顺序：R3（立即见效、零风险）→ R2 → R6 → R4 → R1 → R5**

理由：R3 里那个 **404 根因修复是一行改动，立刻消除"图标不显示"的一部分确定性原因**，投入产出比最高。R2/R6 是用户明确点名要的能力。R1 动搜索核心，风险最高，放在有 R2/R3 经验之后。R5 独立页面，可并行。

---

## 八、待用户拍板

1. **排序空值策略**：抓不到时间的条目置底（推荐）/ 夹中间 / 按源顺序？
2. **搜索框右侧**：只放"排序"（推荐）/ 排序 + 内容类型（需先采样后缀命中率）/ 保留现有分类但改形态？
3. **图标缓存失效**：URL 变化即刷新 + 最长 7 天过期（推荐）？还是更短/更长？
4. **蓝奏网页改造幅度**：保持 WebView + 质感重构（推荐，风险最低）/ 换原生渲染（工作量大）？
5. **执行顺序**：按上面建议的 R3→R2→R6→R4→R1→R5，还是用户有别的优先级？

---

## 九、关键代码位置索引（研究结论对应）

| 结论 | 位置 |
|---|---|
| 排序器（待重写） | `MainActivity.java:851 sortSearchItems` |
| 搜索进度文案 | `MainActivity.java:838 refreshSearchUi` |
| 搜索入口/本地索引三条链路 | `MainActivity.java:1362 runSearch` |
| 每日搜索缓存 | `MainActivity.java:1357 readDailySearchCache` / `:1358 writeDailySearchCache` |
| 本地索引匹配 | `LanzouCore.java:980 cachedPartialIndexMatches` / `:1008 searchBackfillIndexForSearch` |
| 搜索调度器 | `LanzouCore.java:240 refillActiveLocked` / `:273 finishSourceLocked` |
| 并发计算 | `LanzouCore.java:824 adaptiveNetworkWorkers` / `:826 adaptiveSourceWorkers` |
| 超时常量 | `LanzouCore.java:46` `SOURCE_PROBE_TIMEOUT_MS=35s` |
| 限速常量 | `LanzouCore.java:41` `ORIGIN_PAGE_SLOT_MS=1100` / `NEXT_PAGE_FLOOR_MS=3000` |
| **404 兜底图标（必修）** | `LanzouCore.java:606` |
| 正确图标路径参考 | `LanzouCore.java:1210` / `:1216` |
| 图标加载（待重构） | `MainActivity.java:2028 requestImage` / `:2021 loadCachedFolderImage` |
| 图标内存缓存 | `MainActivity.java:69 imageCache` |
| 蓝奏网页页（待重构） | `LanzouWebActivity.java`（全文 31 行） |
| 网页打开入口（透传标题） | `MainActivity.java:1752 openWebPage` |
| 目录翻页 | `MainActivity.java:811 expandMore` / `:800 startMoreWaiting` |
| 错误文案 | `MainActivity.java:422 friendlyError` / `LanzouCore.java:646 sourceTestError` |
| 搜索框分类选择器 | `MainActivity.java:388 SearchCategoryPicker` |
