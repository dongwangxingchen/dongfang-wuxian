# 侦察报告：图标（头像）加载失败与缓存缺失 + 搜索结果排序现状

> 2026-09-26。由只读侦察子智能体产出，主智能体**独立复核**后落盘。
> 标记约定：✅ = 主智能体已亲自复核为真；⚠️ = 侦察员结论经我复核后**有修正**；❓ = 未验证，勿当结论。

---

## 一、图标问题

### 1.1 ✅ 确定坏掉的 URL（唯一一条，实测确认）

| 位置 | URL | 实测 |
|---|---|---|
| `LanzouCore.java:606`（`itemFromMember`） | `https://images.bakstotre.com/images/folder.gif` | **404** ❌ |
| 对照 `LanzouCore.java:1213`/`1219`（正确写法） | `https://images.bakstotre.com/assets/images/type/folder.gif` | **200** ✅ |

606 行代码实况（已复核原文）：
```java
if(out.folder&&out.iconUrl.isEmpty())out.iconUrl="https://images.bakstotre.com/images/folder.gif";
```
即：**自建合集里的本地文件夹**（`addCompositeFolder` 不设 `iconUrl`）会拿到这个 404 URL。

### 1.2 ✅ type 图标部分 404（实测抽样）

| URL | 结果 |
|---|---|
| `type/apk.gif`、`type/zip.gif`、`type/folder.gif` | 200 |
| `type/unknown.gif`、`type/apks.gif` | **404** |

所以 `LanzouCore.java:1411` 拼出的 URL **并非全部可用**，取决于 `type` 字段取值。

### 1.3 ✅ 合集头像空值面（左上角图标不显示的主因链）

- `builtInSources`（`LanzouCore.java:671`）**从不设置 `avatarUrl`** → 官方源头像**只能**靠在线解析 `sharePageAvatar`（1195）。
- 解析失败/为空时，`renderFolder`（`MainActivity.java:754`）**没有 fallback** → 永久空白圆角底。
- `applyAvatarFallback`（`LanzouCore.java:948`）在头像为空时**取第一个非空 `item.iconUrl` 当头像** → 会把上面那个 **404 URL 甚至文件夹图标提升成合集头像**（放大问题）。

### 1.4 ✅ `loadCachedFolderImage` 不补发请求（"好久才显示"的直接原因）

`MainActivity.java:2021` 原文已复核：
```java
void loadCachedFolderImage(String url,ImageView view){
  if(view==null||url==null||url.isEmpty())return;
  view.setTag(url);
  Bitmap bitmap=activeFolderState==null?null:activeFolderState.pinnedIcons.get(url);
  if(bitmap==null)bitmap=imageCache.get(url);
  if(bitmap!=null&&url.equals(view.getTag())){view.setImageBitmap(bitmap);return;}
  synchronized(imageLock){
    List<WeakReference<ImageView>> waiters=imageWaiters.get(url);
    if(waiters!=null)waiters.add(new WeakReference<>(view));
  }
}
```
**关键**：内存没命中、且**没有 in-flight 请求**时，它只是"看看有没有 waiters 可挂"，**什么都不做就返回**——**永远不会自己发起下载**。

调用它的唯一入口是 `loadImage`（2020）：`if(restoringFolderState){loadCachedFolderImage(...);return;}`。
`restoringFolderState=true` 的触发者：面包屑回退、主题重建等（`restoreFolderPageState`，709）。

→ **后果**：这些路径下只有内存里恰好残留的位图能显示，其余**永久空白**，必须触发一次非 restore 渲染（如刷新）才补图。这就是"好久才显示"。

> ⚠️ **修正侦察员一处推断**：它说这里会产生 `imageWaiters` 悬空泄漏。**不成立**——`waiters` 为 null 时压根不写入，无泄漏。已复核代码确认。

### 1.5 ✅ `imageCache` 容量与持久化（已复核原文）

`MainActivity.java:69`：
```java
final LruCache<String,Bitmap> imageCache=new LruCache<String,Bitmap>(
  Math.max(1024,Math.min(8192,(int)(Runtime.getRuntime().maxMemory()/1024/24)))){
  @Override protected int sizeOf(String key,Bitmap value){return Math.max(1,value.getByteCount()/1024);}};
```
- 容量 = `clamp(堆/24, 1024, 8192)` **KB**；256MB 堆 → 8MB ≈ **100 张**（144px ARGB_8888 约 81KB/张），而目录一页就可能 50+ 项。
- key = **原始 URL 字符串**（不归一化、不哈希）。
- **零磁盘持久化**：全项目 `Bitmap.compress` 命中数 = **0**（已复核）。进程被杀 → 全丢。
- ❓ 侦察员称"无 `onTrimMemory`/`onLowMemory` 钩子"——未复核。

### 1.6 ✅ `requestImage` 失败行为（无重试、无占位）

`MainActivity.java:2028`：
- 超时 3s/5s；同 URL 并发去重（只发一次）；失败 `catch(Exception ignored)` 仅打 Log。
- **无重试、无占位图、无失败标记**；`imageWaiters` 成功失败都清理 → **不会无限重试**（好）。
- 次生问题 ❓：`imageIo` 池大小取自 `adaptiveSourceWorkers`，低内存设备可能仅 1~2 线程，且与 `postFolderIconPrefetch`（2019，进目录就排 14 张）**抢同一池**；且 `requestImage` **不走 `NetworkGovernor`**。

### 1.7 ❓ 未验证项

1. `image.dmpdmp.com/image/ico/<custom>` 在 custom 非法时是否 404（侦察员称任意路径返 200，无法区分真图/占位图）。
2. `icon`（type）字段真实取值分布——是否真会出现 `unknown`/`apks` 这类 404 值。
3. `assets/c` 里 time 原始文本形态（仓库内该文件 0 字节，内容构建时注入）。

### 1.8 建议修复方向（图标）

1. **改 606 行为 `/assets/images/type/folder.gif`**（一行，确定收益）。
2. `loadCachedFolderImage` 在"无缓存且无 in-flight"时 **fallthrough 到 `requestImage`**（治"好久才显示"）。
3. `requestImage` 加**有限重试**（1~2 次退避）+ 失败态标记；失败**不写缓存**。
4. 合集头像为空时给 UI 占位（现在只有 `placeholder:directory` 才有）。
5. 顺带修 `applyAvatarFallback` 把文件夹图标/404 URL 提升成头像的问题。
6. （可选，成本较高）加磁盘 L2 缓存 `getCacheDir()/icon-cache/`。

---

## 二、搜索结果排序现状

### 2.1 ✅ 现状：只有"文件夹置顶"，其余按到达顺序

`MainActivity.java:851` 原文（已复核）：
```java
static void sortSearchItems(List<Models.Item> items){items.sort((left,right)->Boolean.compare(right.folder,left.folder));}
```
- **唯一比较键** = `folder` 降序（true 在前）。**无第二键、无稳定兜底键**。
- → 同类型内顺序 = `List.sort` 稳定性保留的**插入顺序** = "哪个源/哪页先返回"。

### 2.2 ✅ 排序只在重建时生效（流式追加不排序）

- `sortSearchItems` **唯一调用点** = `renderSearchResults()`（`MainActivity.java:828` 那一段），即**只在重建搜索结果页时排一次**。
- 流式追加路径**全部不排序**（已复核三处方法体，无 sort 调用）：
  - `appendSearchResultsUi`（831）
  - `insertGlobalSearchItem`（849）
  - `insertSearchItem`（848）

→ **现状 = "文件夹置顶 + 其余按到达顺序"，且排序只在重建时生效**。

> 注：复核时我先用文本窗口扫到 `insertGlobalSearchItem` 附近有 `.sort(`，实为**相邻方法** `sortSearchItems` 的文本落入同一窗口，**不是**该方法在排序。已定位方法体确认。

### 2.3 ✅ `time` 字段形态（能否作为排序第二键的前提）

- 写入路径 `LanzouCore.item()`（1412）→ `normalizeTimeCell`（1415）：**只归一化分隔符为 `-`，不保证补零**（可能出现 `2026-7-24`）→ **字符串比较不可靠**。
- 单文件页（596）正则抓不到即 `""`；`staticFolderItem`（1213）**恒为空**。
- 自建合集 `assets/c` 成员的 time 是**原始文本、未归一化**；且 `fromJson`（783）对 lightweight 成员**直接丢弃 time**。
- ❓ **无真实 API 响应样本**，是否出现 `YYYY-MM-DD HH:MM:SS` 形式**未验证**（侦察时 curl 打 `filemoreajax.php` 返回 `zt:4 请刷新重试`，没拿到样本）。
- **无现成可比值解析代码**（`parseTime|timeValue|timeKey` 零命中）。可复用近似实现：`Toolbox.dateToStamp`（124，`SimpleDateFormat("yyyy-MM-dd")` + `setLenient(false)`）。

### 2.4 建议修复方向（排序）

`sortSearchItems` 扩为复合比较键：**文件夹优先 → 时间倒序（解析 `y*10000+m*100+d`，空时间沉底）→ url/source 稳定键**，并让流式追加三处**复用同一比较器**，否则排序仍只在重建时生效。

> 前提：先把 `normalizeTimeCell` 补零（否则字符串比较仍不可靠），或走数值解析。

---

## 三、摘要（12 行）

1. **确定坏的 URL**：`LanzouCore.java:606` 的 `images/folder.gif` **实测 404**（正确写法 `/assets/images/type/folder.gif` 实测 200）。
2. **type 图标部分 404**：`unknown`/`apks` 实测 404，`apk`/`zip`/`folder` 200。
3. **左上角图标不显示主因链**：`builtInSources` 从不设 `avatarUrl` → 全靠在线解析 → 失败时 `renderFolder` **无 fallback 永久空白**。
4. **`applyAvatarFallback` 放大问题**：会把 404 URL/文件夹图标提升成合集头像。
5. **`loadCachedFolderImage`（2021）**：无缓存且无 in-flight 时**直接返回，不补发请求** → restore 路径下永久空白（"好久才显示"）。
6. ⚠️ 侦察员称此处有 `imageWaiters` 泄漏——**不成立**，已复核代码否证。
7. **`imageCache`**：`clamp(堆/24,1024,8192)`KB（256MB 堆≈8MB≈100 张），key=原始 URL，**零磁盘持久化**（`Bitmap.compress` 命中 0），进程重启全丢。
8. **`requestImage`**：无重试、无占位、无失败标记；不会无限重试（好）；池可能与预取抢线程，且不走 `NetworkGovernor`。
9. **排序现状**：`sortSearchItems`（851）唯一键 = 文件夹置顶，**唯一调用点 = 重建时**；流式追加三处（831/849/848）**均不排序**。
10. **`time` 形态**：只归一化分隔符、**不补零** → 字符串比较不可靠；无现成可比值解析；`staticFolderItem` 恒空。
11. **建议（图标）**：修 606 → `loadCachedFolderImage` fallthrough → `requestImage` 有限重试+失败态 → 头像空值占位 → 修 `applyAvatarFallback`。
12. **建议（排序）**：复合比较键（文件夹 → 时间倒序 → 空沉底 → url 稳定键）+ 流式路径复用同一比较器；前提是先修 `normalizeTimeCell` 补零。
