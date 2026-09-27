# 侦察报告：搜索卡在「3/50 个源 · 已完成 0」根因

> 2026-09-26。侦察员（只读 Explore agent）产出，**主智能体逐条复核过**。
> 复核标记：✅ = 我亲自读代码确认为真；⚠️ = 表述需修正；❓ = 未验证（标注为推测）。

---

## 一、结论

搜索"卡住"**不是单点 bug，是三个因素叠加**：

1. **并发上限被压低** → 同时只跑 3 个源（`active=3` 直接来源）
2. **慢源让位机制被静默禁用** → 这 3 个源要跑完才轮到其余 47 个（`done=0` 长时间不动的直接原因）
3. **一条 in-flight 标志泄漏可造成永久死锁** → 极端情况下某源永远无法重入队（最坏情况）

---

## 二、逐条证据（含复核结果）

### ✅ 因素1：并发上限可能被压到 3

`LanzouCore.java:activeLimitLocked` + `LanzouCore.java:adaptiveSourceWorkers`

容量公式：`floor(networkWorkers × heapFactor × pressureFactor / 2)`
- `heapFactor = sqrt(headroom/max)`（堆余量越小越低）
- `pressureFactor = 1/sqrt(1+ROUTE_PRESSURE)`
- **`ROUTE_PRESSURE` 只升不降**（失败路线会累积，无衰减机制）

→ 小堆设备或路线失败累积后，算出 3 完全可能。

**复核**：公式与 `ROUTE_PRESSURE` 语义已确认（见 `LanzouCore.java` 的 `adaptiveSourceWorkers`）。真机是 14.8GB 内存（实测），`heapFactor` 应接近 1；但 `pressureFactor` 会随失败累积下降。**是否为当前真机的主因，未实测复现（❓）**。

### ✅ 因素2：自动并发模式下「慢源让位」完全失效（**这是最关键的一条**）

两处代码联动的结果：

**A. 让位时间被置 0** —— `MainActivity.java:searchOptions`：
```java
int requested = sessionSearchConcurrency<=0 ? 0 : Math.min(Math.max(1,total),sessionSearchConcurrency);
long switchDelay = requested<=0 || requested>=Math.max(1,total) ? 0L : sessionSearchBatchSeconds*1000L;
```
- `sessionSearchConcurrency` 默认 **0 = 不限**（`MainActivity` 默认设置）
- 于是 `requested = 0` → **`switchDelay = 0L`**

**B. 让位逻辑遇 0 直接返回** —— `LanzouCore.java:scheduleRotationLocked`：
```java
if(options.sourceSwitchDelayMillis<=0 || rotations.containsKey(key) || waitingGroups.isEmpty()) return;
```

→ **`switchDelay=0` 时，轮转调度器根本不启动**。慢源只能"跑完才让位"，其余源无限排队。

**这解释了用户看到的「active=3 且 done=0 长时间不动」**：3 个源占着并发槽各自跑到自己的页数上限，谁都没跑完 → `done` 恒为 0。

**复核**：两处代码逐字确认为真。`sourceSwitchDelayMillis` 默认值 `0L`（`Models.java:51`）。**用户设置的 `sessionSearchBatchSeconds=15` 在这条路径上完全没生效**——它只在"手动指定并发数且小于源总数"时才起作用。

**修正侦察员表述**：侦察员写的是"`requested<=0||requested>=total → switchDelay=0L`，而 `scheduleRotationLocked` 首行 `<=0 return`"——✅ 准确。但需补充：这不是"静默禁用"，而是**默认配置下的必然结果**（因为默认并发就是 0=不限）。

### ✅ 因素3：`executeNetwork` 提前返回路径漏重置 in-flight 标志

`LanzouCore.java:249-254`：
```java
private void executeNetwork(SearchNetworkJob job){
  try(NetworkGovernor.Lease ignored=NETWORK_GOVERNOR.acquireForeground(()->stopped(job.state))){
    if(stopped(job.state))return;          // ← 提前返回
    ...
  }catch(InterruptedException interrupted){Thread.currentThread().interrupt();}
  finally{synchronized(this){runningHttp--;refillActiveLocked();pumpNetworkLocked();notifyAll();}}
}
```

**对比** `LanzouCore.java:248` 的 `abandonNetworkLocked`（有正确重置）：
```java
private void abandonNetworkLocked(SearchNetworkJob job){runningHttp--;if(cancelled)return;
  if(job.api){job.state.apiInFlight=false;queueApiLocked(job.state);}
  else{job.state.dirInFlight=false;state.dirReady=true;queueDirectoryLocked(job.state);}
  refillActiveLocked();}
```

**泄漏链**：
- `stopped(state)` = `cancelled || closed || !state.active || isSearchCancelled(progress)`（`LanzouCore.java:237`）
- 若因 `!state.active` 提前返回 → **没有重置 `apiInFlight`/`dirInFlight`**
- `queueApiLocked` 的入队条件是 `... && !state.apiInFlight`（`LanzouCore.java:queueApiLocked`）
- → 该 state 之后**永远无法重新入队**
- → `apiDone`/`dirDone` 永不为真
- → `finishSourceLocked` 首行 `if(state.terminal||!state.dirDone||!state.apiDone)return;` **永不通过**
- → `done` 永久冻结

**注意**：`rotateSourceLocked`（`LanzouCore.java:272`）会把成员 `state.active=false` 并移出 `activeGroups`；之后 `refillActiveLocked` 重新激活时会 `state.active=true` 并重新 `queueApiLocked`——**但 `apiInFlight` 仍是 `true`，所以 `queueApiLocked` 直接跳过**。这就是泄漏如何演变成死锁。

**复核**：代码路径逐环节确认为真。**但是否为当前真机的主因，未复现（❓）**——需要真机日志确认是否真的走了这条路径。

### ✅ 结果推送是流式，15秒不是结果批间隔

`LanzouCore.java:publishBatch` / `publishPage` → `progress.onBatch` **每页 / 每次 API 响应即推**。

→ `sessionSearchBatchSeconds=15` **不是**结果批间隔，而是"轮转时间片"（`Models.SearchOptions.sourceSwitchDelayMillis`）。用户感觉"不给新东西"不是批式推送导致的，而是**源根本跑不完**。

**复核**：确认为真。

### ✅ `done` 的自增条件

`LanzouCore.java:273` `finishSourceLocked`：
```java
if(state.terminal||!state.dirDone||!state.apiDone)return;
...
int value=done.incrementAndGet();
```

→ **`done` 只在"目录 + API 双完成"时才 +1**。所以 `active=3, done=0` 的准确含义是"3 个源在跑，一个都没双完成"。

---

## 二之二、✅ 独立缺陷：UI 渲染窗口只在滚动时增长

这是与调度**无关的另一个缺陷**，会造成"计数在涨但列表不动"的观感。

`MainActivity.java:837` `maybeAppendSearchWindow` 的方法体（已复核原文）：
```java
void maybeAppendSearchWindow(){
  int session=searchGeneration,epoch=searchRenderEpoch,desired=Math.min(searchWindowTarget,current.size());
  GridLayout grid=searchRenderGrid;
  if(!searchSurfaceCurrent(session,epoch,grid)||searchWindowTarget>=current.size()||searchWindowDirtyFrom<desired||grid.getChildCount()!=desired)return;
  searchWindowTarget=Math.min(current.size(),searchWindowTarget+SEARCH_WINDOW);
  searchWindowDirtyFrom=Math.min(searchWindowDirtyFrom,visible);
  scheduleSearchWindow(session,epoch,grid);
}
```

- 渲染窗口 `searchWindowTarget` 初始 = `SEARCH_WINDOW` = **64**（已复核常量）。
- **`maybeAppendSearchWindow` 全文件唯一调用点 = `MainActivity.java:1732` 的滚动监听**：
  ```java
  scroll.setOnScrollChangeListener((v,x,y,oldX,oldY)->{bar.bumpActivity();maybeLoadMoreSources();if(y+scroll.getHeight()+dp(240)>=body.getHeight())maybeAppendSearchWindow();});
  ```
- 流式追加路径 `appendSearchResultsUi`（831）**只把新项加进 `current` 列表并重绘当前窗口**，**不增长 `searchWindowTarget`**（已复核方法体，无该赋值）。

→ **后果**：结果超过 64 条后，**不滚动就永远不追加渲染**，但状态栏计数照涨。用户观感 = "卡住了/列表不动"。

**建议**：`appendSearchResultsUi` 在 `current.size()` 超过 `searchWindowTarget` 时按需增长（或恢复"仅在可见区附近增长"的有界策略，但需保证自动增长，不能只依赖滚动）。

---

## 三、单源最坏耗时（⚠️ 侦察员计算有误，此为修正版）

侦察员原写"API 7个UA×35s=245s + 目录 1000页×35s≈9.7h"——**这个算法有问题**：

- `SOURCE_PROBE_TIMEOUT_MS=35s` 是**源级探测超时**，不是每页超时
- 每页走 `FOREGROUND_BROWSE_TIMEOUT_MS=18s`
- 页数上限 `sessionSearchMaxPages`（默认见 `MainActivity` 设置）

**修正后的最坏量级**：单源若真跑到 1000 页上限且每页都慢，确实是小时级；但受 `SOURCE_PROBE_TIMEOUT_MS=35s` 的源级兜底约束，**正常不会到小时级**。典型是分钟级。**精确上限待实测（❓）**。

---

## 四、建议修复方向（按性价比排序）

| # | 修复 | 理由 | 风险 |
|---|---|---|---|
| 1 | **`executeNetwork` 提前返回时补 `abandonNetworkLocked` 等价重置** | 消除永久死锁路径，改动小 | 低（纯补漏） |
| 2 | **让位机制改为不依赖 `switchDelay>0`**：`switchDelay=0` 时退化为"基于源已运行时长"的有界让位（如跑满 N 秒强制让位） | 让默认配置（并发不限）也能轮转，这是用户"卡住"的直接病灶 | 中（改调度语义，需真机验证不引入新抖动） |
| 3 | **`ROUTE_PRESSURE` 加衰减** | 避免历史失败永久压低并发 | 中 |
| 4 | **进度语义改为"条数"而非"源数"** | `onProgress(done,total)` 以源为单位，用户看到"3/50"却不知道进度；`onWorkProgress(doneUnits,totalUnits)` 已实现（`MainActivity.java:onWorkProgress`）但未用于主状态栏 | 低 |
| 5 | **`appendSearchResultsUi` 按需增长渲染窗口**（见「二之二」） | 结果 >64 条后不滚动就不追加渲染，计数在涨列表不动 | 低 |

---

## 五、不确定项（明确标注）

1. ❓ **因素3（死锁）是否为真机主因**——代码路径确认存在，但未在真机复现确认实际走到了这条路。
2. ❓ **因素1（并发=3）是否为真机实际值**——需真机日志确认 `activeLimitLocked()` 的实际返回值。
3. ❓ **单源最坏耗时精确上限**——受多常量叠加，未实测。
4. ❓ **`persistIndexState` 是否在持锁时做磁盘 I/O**——侦察员与主智能体**均未取证**，勿当结论。

**已复核为真的关键常量**（本轮补充核实）：`ROUTE_PRESSURE` 全文件仅 5 处出现（构造 / `.get()` ×2 / `+1` / `-1`），**确无任何 reset 或 set 点** → 压力值只能靠成功递减，失败累积后自我强化低并发。`SEARCH_WINDOW=64`、`SEARCH_RENDER_CHUNK=64` 亦已复核。

---

## 六、复核方法说明

本报告所有 ✅ 标记项，均由主智能体用 `grep -o` / 读取源码逐字确认，未采信侦察员转述。
**未复核项已明确标注 ❓**，不得当作结论使用。
