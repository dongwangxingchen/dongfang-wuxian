# 2026-09-26 搜索 / 蓝奏云网页 / 图标缓存 —— 现状梳理（只勘察，未动代码）

> **历史勘察材料，当前执行入口见 [`docs/plan/20260926-long-term-execution/`](../../plan/20260926-long-term-execution/)。** 本文保存当时的用户诉求、代码盘点和研究线索；不直接授权实施，也不把旧编号、方案、顺序或设备信息当成当前命令。
> 后续只在当前专题重新核对所需部分；推测必须继续验证，新的实测和用户反馈优先。

---

## 一、用户原始诉求（逐条，原话要点）

1. **搜索要"非常非常快"**地加载全部软件库，快速把全部内容呈上来。
2. **默认按最新排序**：最新上传的在最顶部。
3. **搜索卡住**：截图显示「正在搜索 3/50 个源 · 已完成 0 · 找到 4 · 电脑·PC软件 ｜ 已显示 4 ｜ 暂停」→ 长时间不追加新内容。
4. **搜索框右侧"全部"该放什么**：下面已经列出全部软件库了，所以"全部"里没必要再塞软件库列表让用户二次选择；那这里放什么分类才科学？
5. **蓝奏云链接**：有的解析失败、有的**名字左边不显示图标** → 想要一套**合适的缓存**，既能让图标稳定显示，又不会在源更新后还显示旧图标。
6. **顶部那个条**：打开后顶部出现一条，向右慢慢滑显示"正在加载" → 因为这个动画，**感觉加载特别慢**（体验很差）。
7. **翻到底部**：显示"正在加载下一页"，结果**加载半天不出来**。
8. **加载失败**：显示"加载下一页失败，连接超时，请稍后重试" → 这些是问题。
9. **有的蓝奏云链接直接打开就解析失败**（那搜索时更搜不到）。
10. **动画/UI 重设计**：下拉刷新、正在加载下一页的动画效果、展示样子、UI 都要重新设计和优化。
11. **蓝奏云页面质感优化**：做得非常美观、好看、高级；效率极高、质量极好；能看到绝大多数需要看到的信息；**不要像专业工具那样冷硬，要真正适合大众**。

---

## 二、代码现状勘察（全部有据可查）

### 2.1 搜索结果排序 —— 确认缺失时间排序

`MainActivity.java:851`
```java
static void sortSearchItems(List<Models.Item> items){
    items.sort((left,right)->Boolean.compare(right.folder,left.folder));
}
```
- **只做"文件夹优先"**，没有任何时间维度 → 用户要的"最新在顶部"当前**根本不存在**。
- 调用点：`renderSearchResults()`（MainActivity.java:828）。
- 数据基础**已具备**：`Models.Item.time` 字段存在，由 `LanzouCore` 用正则从蓝奏页面抽取：
  `LanzouCore.java:596` → `out.time=cap(html,"((?:19|20)\\d{2}[-/.]\\d{1,2}[-/.]\\d{1,2})").replace('/','-').replace('.','-')`
  → 格式统一为 `YYYY-M-D`（如 `2026-9-25`）。
- **风险**：并非所有条目都有 `time`（单文件页/部分目录页可能抓不到 → 空字符串）；成员规则表（assets/c）里的 `time` 是原始文本，未规范化。**空时间的排序位置需要策略**。

### 2.2 搜索卡住 —— 关键机制已定位

`LanzouCore.java` 搜索调度器（`SourceSearchWorker` 内部类）：

- **进度回调语义（关键）**：
  - `onActivity(active,total,source)`：由 `publishActivityLocked()` 触发，**只在并发槽位变化时**（`refillActiveLocked` 里 `changed=true`）→ 所以"3/50"表示**当前有 3 个源在跑**。
  - `onProgress(done,total,found,source)`：**只在某个源"完成"时**触发（`int value=done.incrementAndGet()` 在源结束时）。
  - → **`active=3 且 done=0` 的含义：有 3 个源正在跑，但一个都还没跑完。**
- **超时常量（LanzouCore.java:46-47）**：
  ```java
  SOURCE_PROBE_TIMEOUT_MS = 35 * 1000L;        // 单源探测 35 秒！
  FOREGROUND_BROWSE_TIMEOUT_MS = 18 * 1000L;
  METADATA_BROWSE_TIMEOUT_MS = 8 * 1000L;
  STARTUP_BASE_ORIGIN_TIMEOUT_MS = 5 * 1000L;
  DIRECT_RESOLVE_TIMEOUT_MS = 12 * 1000L;
  ```
  → **一个源最坏要 35 秒才出结果**；3 个源并发跑，用户等 35 秒看不到 `done` 增加 = 观感"卡死"。
- **并发数计算（自适应，受内存与压力影响）**：
  `adaptiveSourceWorkers(requested, taskCount)`（LanzouCore.java:826）：
  ```java
  double heapFactor = sqrt(min(1, headroom/max));
  double pressureFactor = 1/sqrt(1 + max(0, ROUTE_PRESSURE.get()));
  long capacity = floor(network * heapFactor * pressureFactor / 2);
  return max(1, min(demand, capacity));
  ```
  → `ROUTE_PRESSURE`（线路压力）升高时会**动态压低并发**，极端情况降到 1。**这可能是"3 个源跑很久不动"的直接原因之一**。
- **分批切换**：`searchOptions()`（MainActivity.java:903）：
  ```java
  int requested = sessionSearchConcurrency<=0 ? 0 : min(max(1,total), sessionSearchConcurrency);
  long switchDelay = (requested<=0 || requested>=max(1,total)) ? 0 : sessionSearchBatchSeconds*1000L;
  ```
  → 并发设为"不限"时 `requested=0` → `switchDelay=0` → 每源跑到底；**默认 `sessionSearchConcurrency` 存的值是多少需要查设置页实测**（`loadSearchSettings` 默认 `p.getInt("threads",0)` = 不限）。
  → `scheduleRotationLocked` 在 `switchDelay>0` 时才轮换源。
- **每源内部两条链路**：API 搜索（`queueApiLocked`）+ 目录搜索（`queueDirectoryLocked`），各自完成后 `publishWorkProgressLocked`。

### 2.3 图标缓存 —— 只有内存缓存，无磁盘缓存

- **内存**：`MainActivity.java:69` `LruCache<String,Bitmap>`（容量按堆内存 1/24 估算，1024~8192 KB）。**进程被杀即全部丢失**。
- **网络加载**：`requestImage()`（MainActivity.java:2028）：
  - 连接超时 3000ms / 读超时 5000ms
  - UA 伪装 `Chrome/138 Mobile`，Referer `https://www.lanzouw.com/`
  - 两段式解码 `decodeIcon` → `inSampleSize` 目标 144px（省内存）
  - 等待者队列 `imageWaiters` + 分批投递 `drainImageDeliveries`（每帧 ≤ `IMAGE_UI_CHUNK` 个，16ms 间隔）
- **磁盘缓存：不存在**（`grep getCacheDir` 只命中更新包校验的临时文件）。
  → **冷启动/切页后所有图标要重新走网络** → 用户说的"有的不显示图标"很可能就是**网络请求失败后无重试、无兜底**（失败只 `Log.w`，图标停留在 `ic_file` 占位）。
- **失败重试**：无。`catch(Exception ignored){Log.w(...)}` 后直接 `finally disconnect`，`bitmap==null` 时**不投递**，ImageView 永远停在占位图标。
- **缓存失效**：`pinFolderImage`（目录页切换时把当前 Bitmap 钉进 `FolderPageState.pinnedIcons`）是唯一"保活"机制；**没有 URL→Bitmap 的过期策略**，也没有"源更新后刷新图标"的机制。

### 2.4 蓝奏云网页 —— WebView 实现，App 侧没有任何进度条

- `LanzouWebActivity.java`（仅 31 行，但每行很长）：纯 `WebView` + 自绘顶栏（返回 / 地址 / ⋮ 菜单）。
- **【已修正初判】顶部那个"慢慢向右滑的条" ≠ WebView 自带进度条**。通读全文 31 行确认：既无 `WebChromeClient`（→ `onProgressChanged`/`onReceivedTitle` 从未被调用）也无 `requestWindowFeature(FEATURE_PROGRESS)`，**App 侧一个进度条都没画**；`WebView` 本身只有 `getProgress()`，没有任何自绘进度条。用户看到的"条"要么是蓝奏云网页自身的加载 UI，要么是"白底→内容"的突变。**无论哪种，结论相同：必须由 App 接管加载叙事**（详见 research.md 第五节）。
- 当前顶栏：`‹` 返回 + 地址文本（MIDDLE 省略）+ `⋮`（下载历史/系统浏览器/复制/分享）。
- **没有**：加载进度可视化、错误页、重试按钮、返回手势预返回支持（`onBackPressed` 覆写 + 裸 `OnBackInvokedCallback` → **会杀死系统预返回动画**，与 T9 同类问题）。
- **下载监听**：`web.setDownloadListener` → `download()` → 转交 `MainActivity.enqueueWebDownload`。

### 2.5 目录页"加载下一页" —— 延迟机制 + 失败文案

- `expandMore()`（MainActivity.java:811）：
  - 若 `visible<current.size()`：本地追加（`appendFolderRowsAsync`）
  - 否则若 `folderHasMore`：`folderLoadingMore=true` + **`ui.postDelayed(folderLoadRunnable, wait)`**，`wait = folderNextReadyAt - now`
  - → **`folderNextReadyAt` 是"下一页最早可请求时间"**（限速保护），等待期间 UI 显示"正在加载下一页"但**实际还没发请求** → 用户观感"加载半天不出来"。
- 失败文案：`MainActivity.java:422 friendlyError()` 把各种异常映射为固定文案：
  - `超时/timeout` → **"连接超时，请稍后重试"**（用户截图里那句）
  - `过快/频率/受限` → "请求频率受限，请稍后重试"
  - `ACW` → "蓝奏验证暂不可用，请稍后重试"
  - 兜底 → "操作失败，请稍后重试"
  → **所有失败都是"请稍后重试"，没有具体原因、没有重试按钮、没有自动重试**。
- 限速来源：`LanzouCore` 的 `reservePageSlot/deferPageSlot`（`ORIGIN_PAGE_SLOT_MS`、`NEXT_PAGE_FLOOR_MS`、`PageRateLimitedException`）+ `zt==4` 退避。

### 2.6 搜索框右侧"全部" —— 当前放的是"用户自建分类"

- `SearchCategoryPicker`（MainActivity.java:388）：一个 `PopupWindow` 下拉，选项来自 `sourceCategories`（`LinkedHashMap<String,LinkedHashSet<String>>`）——**用户自己创建的源分类**（通过"加入分类"功能建）。
- 构造处（MainActivity.java:629）：
  ```java
  loadSourceCategories();
  List<String> searchCategories = new ArrayList<>();
  searchCategories.add("全部");
  searchCategories.addAll(sourceCategories.keySet());
  ```
- → 当前语义 = **"搜索范围限定在某个自建分类内"**。用户质疑：下面已经列出全部软件库了，这里再放软件库分类是**重复**的。**需要研究"这里放什么才有增量价值"**（例如：内容类型 / 排序方式 / 时间范围 / 文件类型）。

### 2.7 蓝奏云解析失败 —— 已知失败面

- 域名池：`LANZOU_BASE_ORIGINS`（10 个）+ `LANZOU_HOST` 正则（`lanzou?/lanzov`）+ `LANZOU_CLOUD_HOST`。
- 多线路竞速：`raceRoutes` / `routeCandidates` / `learnedRoute`（线路学习）+ `probeSourceUa`（多 UA 探测）+ `NetworkGovernor`（前台租约）。
- 已知失败类型（`sourceTestError` LanzouCore.java:646）：
  - `ShareCancelledException` → "分享已取消"（源失效）
  - `SocketTimeoutException` → "连接超时"
  - `UnknownHostException/ConnectException/NoRouteToHostException` → "网络连接失败"
  - `PageCapabilityException("当前 UA 返回蓝奏验证页")` / `isWafChallengePage` → WAF 验证页
  - `PageRateLimitedException("目录请求过快")` → `zt==4`
  - `DirectPasswordException` → 密码问题
  - `isUnavailableShareShell(html)` → 不可用页面壳
- **待深挖**：各类失败的**真实占比**（需要真机日志采样）、哪些是"可自动恢复"（换线路/换 UA/退避重试）而当前直接放弃了。

---

## 三、问题归类（按"根因域"合并，避免重复劳动）

| 域 | 涉及用户诉求 | 核心机制 | 已知根因 |
|---|---|---|---|
| **A. 搜索调度与速度** | 1, 3, 9 | `LanzouCore` 搜索调度器（并发/超时/轮换/进度回调） | 单源 35s 超时；`ROUTE_PRESSURE` 压低并发；`done` 只在源完成时+1 → 观感卡死 |
| **B. 搜索排序** | 2 | `sortSearchItems` + `item.time` | 完全没有时间排序；空时间条目策略未定 |
| **C. 搜索框分类语义** | 4 | `SearchCategoryPicker` + `sourceCategories` | 当前=自建分类（与下方软件库列表重复）；该放什么需研究 |
| **D. 图标缓存** | 5 | `imageCache`(内存) + `requestImage` | **无磁盘缓存**；失败不重试无兜底；无过期/更新策略 |
| **E. 蓝奏网页质感** | 6, 10, 11 | `LanzouWebActivity`(WebView) | 顶部进度=WebView 自带；无进度可视化/错误页/重试；预返回被杀死；顶栏简陋 |
| **F. 目录翻页体验** | 7, 8, 10 | `expandMore` + `folderNextReadyAt` + `friendlyError` | 等待期"假加载"；失败文案单一无重试；无自动恢复 |
| **G. 解析失败根治** | 9 | `LanzouCore` 线路/UA/WAF 体系 | 失败类型已分类，但**真实占比与可恢复性未采样** |

---

## 四、研究计划（排期，先研究后动手）

> 原则：**同一域一次研究透**，产出可执行方案 + 风险清单，再动手。研究手段优先级：本地代码/黑曜参考库 → 结构化工具（codebase-memory / Context7 / gh）→ 外网 + 子智能体外包。

### R1 搜索调度与速度（域 A + G，最高优先，用户痛点核心）
- 采真实失败/耗时样本（真机 logcat 抓 `LanzouCore` 输出，统计各源耗时分布、失败类型占比）。
- 研究：`ROUTE_PRESSURE` 的来源与调节逻辑、`adaptiveSourceWorkers` 是否过度保守、`SOURCE_PROBE_TIMEOUT_MS=35s` 是否可缩短为分级超时（快速失败 + 后台继续）。
- 研究：**首屏快速呈现策略**——用户要"非常快地把全部内容呈上来"。可行方向：本地索引先行（已有 `search-index-v1.json`）+ 目录缓存（已有 `directory-index-v2`）+ 网络增量补充；进度文案改为"已呈现 N 条"而不是"已完成 X 源"。
- 产出：超时分级方案、并发策略调整方案、首屏策略方案。

### R2 搜索排序（域 B）
- 研究主流做法：时间倒序 + 相关性 + 文件夹优先的**复合排序**，以及"无时间条目"的业界处理（置底/按源顺序）。
- 研究 `item.time` 的真实分布（真机采样：多少条目有时间、格式是否统一）。
- 产出：排序器设计 + 空值策略 + 是否需要"排序切换"入口。

### R3 搜索框分类语义（域 C）
- 研究：主流搜索框右侧选择器的**高价值维度**（内容类型/时间范围/排序/来源），以及"下面已有列表"时如何避免重复。
- 候选方向：① 内容类型（安装包/压缩包/文本/视频/音乐，与下载页扩展名分类复用）② 时间范围（今天/本周/本月）③ 排序方式 ④ 保持"自建分类"但改名/重定位。
- 产出：3 个候选方案 + 推荐 + 理由（需用户拍板）。

### R4 图标缓存（域 D）
- 研究：Android 图片库的**磁盘缓存 + 内存缓存两级**标准做法（Coil/Glide 策略）、失效与更新语义（"源换图后要更新"）。
- **约束**：项目禁引第三方依赖（host 模块）→ 需**自研轻量两级缓存**（磁盘 LruDiskCache + 内存 LruCache + URL 版本键）。
- 研究"不显示图标"的真实原因（真机采样：是超时？403？Referer 问题？还是解码失败？）。
- 产出：两级缓存设计 + 失效策略 + 失败重试/兜底方案。

### R5 蓝奏云网页质感（域 E）
- 研究：WebView 进度可视化标准做法（`onProgressChanged` + 自绘进度条）、错误页设计（`onReceivedError`）、返回手势预返回支持（`OnBackAnimationCallback`）。
- 研究**质感方向**：顶栏重构（地址栏 → 更轻量的"来源+标题"）、加载态设计、错误态设计、整体视觉与 app 主色调统一。
- 产出：新版 LanzouWebActivity 设计稿 + 预返回适配方案。

### R6 目录翻页体验（域 F）
- 研究：`folderNextReadyAt` 限速的合理值、等待期的**真实反馈**（进度百分比/预计时间）、失败后的**自动重试 + 手动重试按钮**。
- 研究：加载态/失败态/到底态的 UI 设计（替换"正在加载下一页"文字条）。
- 产出：翻页状态机 + UI 设计 + 重试策略。

### 研究顺序
> ⚠️ **2026-09-26 修订：本节编号已作废**。本文件是**梳理阶段**的草案，其 R3~R6 编号与最终结论 `research.md` **错位**（草案 R3=搜索框分类、R4=图标缓存；最终 R3=图标缓存、R4=目录翻页）。
> **一切以 `research.md` 第七节的编号与顺序为准**：
>
> | 序 | 域 |
> |---|---|
> | R1 | 搜索调度与速度 |
> | R2 | 搜索排序 |
> | R3 | 图标缓存 |
> | R4 | 目录翻页 |
> | R5 | 蓝奏网页页 |
> | R6 | 搜索框分类 |
>
> **建议执行顺序（research.md 定案）：R3 → R2 → R6 → R4 → R1 → R5**

~~R1 → R2 → R4 → R6 → R5 → R3~~（旧草案顺序，已作废。）

---

## 五、待用户拍板的问题（研究过程中会逐步收口）

> ⚠️ **2026-09-26 修订**：本节是**梳理阶段的草案**。最终待拍板清单（5 项）已收口到 `research.md` 第八节，**以那里为准**。下列条目保留作历史记录。

1. **搜索排序空值策略**：抓不到时间的条目放最下、还是夹在中间、还是按源顺序？（倾向置底）
2. **搜索框右侧放什么**：出 3 个候选后拍板。
3. **图标缓存失效语义**：源更新后多久刷新图标？（倾向：URL 变化即刷新 + 最长 N 天过期）
4. **蓝奏网页改造幅度**：保持 WebView 还是换原生渲染？（倾向：保持 WebView + 质感重构，风险最低）
5. **执行顺序**：按 research.md 的 R3→R2→R6→R4→R1→R5，还是用户有别的优先级？

---

## 六、附：关键代码位置索引

| 功能 | 位置 |
|---|---|
| 搜索排序 | `MainActivity.java:851 sortSearchItems` |
| 搜索入口/进度 | `MainActivity.java:1360 runSearch` / `:838 refreshSearchUi` |
| 搜索调度器 | `LanzouCore.java`（`refillActiveLocked` / `publishActivityLocked` / `publishWorkProgressLocked`） |
| 并发计算 | `LanzouCore.java:824 adaptiveNetworkWorkers` / `:826 adaptiveSourceWorkers` |
| 超时常量 | `LanzouCore.java:46-47` |
| 图标加载 | `MainActivity.java:2028 requestImage` / `:69 imageCache` |
| 图标解码 | `MainActivity.java decodeIcon / calculateIconInSampleSize` |
| 分类选择器 | `MainActivity.java:388 SearchCategoryPicker` |
| 蓝奏网页 | `LanzouWebActivity.java`（全文 31 行） |
| 目录翻页 | `MainActivity.java:811 expandMore` / `:789 folderPullAvailable` |
| 错误文案 | `MainActivity.java:422 friendlyError` / `LanzouCore.java:646 sourceTestError` |
| 时间抽取 | `LanzouCore.java:596`（`out.time=cap(...)`） |
