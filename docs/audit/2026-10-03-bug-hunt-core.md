# 核心逻辑与崩溃风险深审（第 1 路）

> 任务：在《东方无限》核心逻辑里找**真缺陷**（真 bug / 崩溃 / 资源泄漏）。
> 代码冻结：**commit `953eb26`**（`git log` 已核对，工作区仅 `docs/agents/lessons.md`、`tools/release.sh` 有他人未提交改动，与本报告无关）。
> 方法：只读源码 + 用 **JDK 直接跑最小复现程序**（`java Foo.java`，不经过 gradle）+ 逐行回源码确认。
> 约束遵守：**未跑 gradle、未改任何产品代码/测试/脚本、未 git add/commit/push、未碰真机、未读 `local.properties`**。

## 结论摘要

**找到 3 条真缺陷**：2 条崩溃（都能用最小程序复现异常本身）、1 条用户可见的功能回退。

| 编号 | 严重度 | 一句话 | 置信度 |
|---|---|---|---|
| **BUG-1** | 🔴 崩溃 | 时间戳工具输入超长数字 → `NumberFormatException` 未捕获 → 主线程崩溃 | 实测（异常已复现）+ 读码（无捕获路径） |
| **BUG-2** | 🔴 崩溃 | 随机数工具「下限 0 / 上限 2147483647」→ `span` 整数溢出为负 → `nextInt` 抛 `IllegalArgumentException` → 崩溃 | 实测（异常已复现）+ 读码（无捕获路径） |
| **BUG-3** | 🟠 功能回退 | 公告接口单独失败、其它三路正常时，**本地已读集合被整片清空** → 已读过的「一次性」公告**再次弹出** | 读码推断（调用链已逐行追通，无测试覆盖） |

---

## BUG-1　时间戳工具：超长数字导致主线程崩溃

**文件:行号**：`app/src/main/java/cc/nkbr/lanzouplus/Toolbox.java:109`（调用点 `ToolHost.java:1326`）

**现象**：崩溃。用户输入一个超出 `long` 范围的长数字（≥19 位且大于 `Long.MAX_VALUE`，或 ≥20 位）时，`Long.parseLong` 抛 `NumberFormatException`，**没有任何 try/catch**，异常从 `TextWatcher` 抛到主线程消息循环 → 应用直接退出。

**触发路径**：
1. 打开「工具箱」→「时间戳转换」；
2. 在输入框里**粘贴**一个 20 位以上的数字（例如从别处复制的订单号 / 纳秒时间戳 / 一串 ID）；
   或者手工连按 19 个 `9`（`9999999999999999999` 已经超过 `Long.MAX_VALUE`）；
3. 输入变化的**那一刻**就崩（`afterTextChanged` → `recalc.run()`）。

**代码证据**：

```java
// Toolbox.java:106-111
static String timestampAuto(String value){
  String v=value==null?"":value.trim();
  if(v.isEmpty())return "输入时间戳（秒/毫秒）或日期（yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss）";
  if(v.matches("\\d+"))return stampToDate(Long.parseLong(v));   // ← :109 无 try/catch
  return dateToStamp(v);                                        // 这一支有 try/catch（:116 起）
}
```

```java
// ToolHost.java:1324-1328 —— 调用点同样没有捕获
Runnable recalc=()->{
  String v=field.getText().toString();
  live.setText(v.trim().isEmpty()?"":Toolbox.timestampAuto(v));   // ← :1326
};
field.addTextChangedListener(new android.text.TextWatcher(){ ...
  public void afterTextChanged(android.text.Editable s){recalc.run();}});
```

**实测证据**（JDK 21 直接运行，未经过 gradle）：

```
OK    "1700000000" (10 位) -> 1700000000
THROW "9999999999999999999" (19 位) -> java.lang.NumberFormatException: For input string: "9999999999999999999"
THROW "99999999999999999999" (20 位) -> java.lang.NumberFormatException: For input string: "99999999999999999999"
THROW "12345678901234567890" (20 位) -> java.lang.NumberFormatException: For input string: "12345678901234567890"
Long.MAX_VALUE = 9223372036854775807
```

**注意对照**：同一个方法里的 `dateToStamp` 分支**是有 try/catch 的**（`Toolbox.java:116` 起），说明作者本来就知道这里会抛——只是**纯数字这一支漏了**。这是"漏写"而不是"设计如此"。

**建议修法**：把 `:109` 改成 `try{return stampToDate(Long.parseLong(v));}catch(NumberFormatException e){return "数字太大，超出时间戳可表示范围";}`（一行）。

**置信度**：异常本身**实测过**；"到达该分支即崩溃"是**读码推断**（`TextWatcher` → 主线程，全链路无捕获）。我**没有**在真机/模拟器上实际触发过（本任务禁跑 gradle 与真机）。

---

## BUG-2　随机数工具：区间整数溢出导致崩溃

**文件:行号**：`app/src/main/java/cc/nkbr/lanzouplus/Toolbox.java:315`（调用点 `ToolHost.java:715`）

**现象**：崩溃。当 `max - min + 1` 超过 `Integer.MAX_VALUE` 时，`span` 溢出成**负数**，`SecureRandom.nextInt(负数)` 抛 `IllegalArgumentException: bound must be positive`，同样无捕获 → 崩溃。

**触发路径**：
1. 打开「工具箱」→「随机数」；
2. **下限填 `0`**（该输入框是 `TYPE_CLASS_NUMBER`，可输入 0）；
3. **上限填 `2147483647`**（该输入框带 `TYPE_NUMBER_FLAG_SIGNED`，可输入完整 10 位数）；
4. **不要勾「去重」**（默认就是未勾选）；
5. 输入完成的瞬间崩。

**代码证据**：

```java
// Toolbox.java:313-318
static String randomNumbers(int min,int max,int count,boolean unique,int sort){
  if(max<min)return"上限需不小于下限";
  SecureRandom r=new SecureRandom();int span=max-min+1;      // ← :315 溢出
  if(unique&&count>span)return"去重模式下数量不能超过区间大小 "+span;
  java.util.LinkedHashSet<Integer> set=new LinkedHashSet<>();List<Integer> list=new ArrayList<>();
  while((unique?set:list).size()<Math.max(1,Math.min(200,count))){int v=min+r.nextInt(span);   // ← span 为负 → 抛
```

```java
// ToolHost.java:706 —— 上限框允许有符号输入
min.setInputType(InputType.TYPE_CLASS_NUMBER);
max.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_SIGNED);
```

```java
// ToolHost.java:714-715 —— 调用点在 TextWatcher 里，无 try/catch
Runnable recalcRnd=()->{
  String r=Toolbox.randomNumbers(parseInt(minF,1),parseInt(maxF,100),parseInt(countF,1),uniqueF.isChecked(),sort[0]);
```

**实测证据**（JDK 21 直接运行）：

```
min=0 max=2147483647
span = max-min+1 = 2147483648  (long 真值)
span 存进 int = -2147483648   <-- 溢出为负
THROW -> java.lang.IllegalArgumentException: bound must be positive

对照组 min=1: span=2147483647 (未溢出) -> nextInt 正常
```

**为什么"勾去重"反而不崩**：`:316` 的 `count>span` 判断在 `span` 为负时恒为真，会提前返回提示串。所以**恰恰是默认（不去重）那条路会崩**。

**建议修法**：`span` 改用 `long` 计算，或在 `:315` 后加一句 `if(span<=0)return"区间过大，请缩小范围";`。

**置信度**：异常本身**实测过**；触发条件（两个输入框的可输入范围）**读码确认**；未在真机上实际点过。

---

## BUG-3　公告已读集合被误清空 → 已读公告重复弹出

**文件:行号**：`NoticeCenter.java:117-120`（配合 `RemoteConfigClient.java:340 / 359-364 / 370`）

**现象**：**用户可见的功能回退**。当「公告」这一路请求失败、而另外三路（control / release / changelog）任意一路成功时，本地已读集合会被**整片清空并落盘**。结果是：用户已经读过、本该"只弹一次"的公告，**下次启动会再次弹出**，红点数字也重新变多。

**这正是代码注释里声称已经修掉的那个用户投诉**（`NoticeCenter.java:111-116` 原话：「用户 2026-10-01 反馈的『发布后只弹一次你没做』就是它」）——但那次修的是 `snapshot == null` 的情况，**没有覆盖"快照可达、但公告这一路失败"**。

**触发路径**：
1. 用户第一次看到公告、读完（`read_ids` 里有该公告 id）；
2. 某次启动时，「公告」这一路 HTTP 请求失败（超时 / 非 200 / 响应格式坏 —— `RemoteConfigClient.get()` 在这些情况下**抛 `IOException`**），
   而 control / release / changelog 任意一路成功；
3. `fetch()` 返回 `Snapshot(reachable=true, notices=空)`；
4. `MainActivity.maybeFetchNotices` 的回调看到 `result.reachable==true` → 放行 → `refreshNoticeBell()` → `unreadCount()` → `prunedReadIds()`；
5. `alive` 为空集 → `read.retainAll(空集)` 返回 true → **写盘清空**。

**代码证据**：

```java
// RemoteConfigClient.java:337-374（fetch）
List<Notice> notices = Collections.emptyList();      // :340 初始为空
boolean anyOk = false;
try { ... control ... } catch (Exception ignored) { }
try { ... release ... } catch (Exception ignored) { }
try {
  JSONArray items = items("notice");                 // :360 ← 失败会抛
  if (sink != null) sink.notice = items;
  notices = parseNotices(items);                     // :362 ← 只有成功才赋值
  anyOk = true;                                      // :363
} catch (Exception ignored) { /* 公告失败不影响更新 */ }   // :364 ← 失败时 notices 仍是空表
try { ... changelog ...; anyOk = true; }             // :370 ← 任意一路成功即 anyOk
return new Snapshot(anyOk, control, release, notices, changelogs);   // :373
```

**关键点**：`reachable` 的语义是「**四个集合里任意一个**拉到了」（`RemoteConfigClient.java:265-266` 的注释自己写明），
而 `prunedReadIds` 把它当成了「**公告集合**拉到了」。

```java
// NoticeCenter.java:109-122
private Set<String> prunedReadIds(RemoteConfigClient.Snapshot snapshot) {
  Set<String> read = store.readIds();
  if (snapshot == null || !snapshot.reachable) return read;   // :117 ← 只挡了"完全不可达"
  Set<String> alive = new LinkedHashSet<>();
  for (RemoteConfigClient.Notice notice : visible(snapshot)) alive.add(notice.id);
  if (read.retainAll(alive)) store.setReadIds(read);          // :120 ← alive 为空 → 全清并落盘
  return read;
}
```

```java
// MainActivity.java:1806-1822（调用链，节选）
void maybeFetchNotices(){
  ...
  runOnUiThread(()->{
    if(isFinishing()||isDestroyed())return;
    if(result==null||!result.reachable)return;      // ← reachable 就放行
    RemoteConfigClient.storeCache(MainActivity.this,collected);
    noticeSnapshot=result;
    refreshNoticeBell();                             // → :1832 unreadCount(noticeSnapshot) → prunedReadIds
    maybePopupNotices();                             // → popupNotices → prunedReadIds
  });
}
```

**为什么这不是"公告真被删了"的正常清理**：正常清理的前提是「后台确实没有这条公告了」。而这里后台**可能还有这条公告**，只是**这一次没取到**。两者在数据结构上完全不可区分——`notices` 都是空表。

**建议修法**（二选一）：
1. 给 `Snapshot` 增加一个「公告这一路是否成功」的标志（例如 `noticesKnown`），`prunedReadIds` 改为 `if (snapshot == null || !snapshot.noticesKnown) return read;`；
2. 或者更省事：`fetch()` 在公告失败时**不要把 `notices` 留成空表**，而是让整份快照的 `reachable` 语义收紧到"公告这一路成功才算"（会影响其它三路，需评估）。

**置信度**：**读码推断**。调用链我逐行追通了（`fetch` → `maybeFetchNotices` → `refreshNoticeBell`/`maybePopupNotices` → `unread`/`popupNotices` → `prunedReadIds`），`get()` 在非 200 / 超时 / 格式错时确实抛 `IOException`（`RemoteConfigClient.java:512-513`、`:530`、`:534`）也已确认。
**但我没有实际制造出"单路失败"的网络环境**（无真机、不跑 gradle）。建议用一条单元测试坐实：构造 `Snapshot(reachable=true, notices=空)`，断言 `unreadCount` **不得**清空已读集合 —— **我查过现有测试，这个场景目前没有覆盖**（`NoticeCenterJvmTest.kt` 的 `snapshot()` 辅助方法永远带着公告，`deletedNotices_arePrunedFromReadIds_notAccumulatingForever` 等用例都传了公告）。

---

## 疑似但**未能构造触发**（明确标注，不算发现）

| 位置 | 疑点 | 为什么没定性 |
|---|---|---|
| `SegmentDownloader.java:85` | `if(written!=expected)throw new IOException("文件长度校验失败")` —— 普通 200 响应中途断流时，会**直接失败**而不是断点续传；且错误文案指向"长度校验"而非"网络中断"，用户看到的原因与真实原因不符 | 属"错误处理不理想"而非确定缺陷；且断流时机依赖网络，无法静态构造 |
| `SegmentDownloader.java:50` | `int outcome=terminal.claim();` —— 若用户恰好在**最后一字节落盘后、claim 之前**点暂停，会走到 `listener.paused(...)`，而文件其实已完整；下载历史可能记成"已暂停" | 竞态窗口极窄，需要真实时序才能复现；**未证实** |

---

## 查过但没发现问题（同样重要）

| 区域 | 看了什么 | 结论 |
|---|---|---|
| `SegmentDownloader.java`（200 行，全读） | 断点续传的 `Range` 连续性校验、重定向白名单、`total` 校验、`active` 连接的 volatile 可见性、`Sink` 的 seek/truncate 与降级路径、`ACTIVE_WORKERS` 计数、`OutOfMemoryError`/`RejectedExecutionException` 捕获 | **无发现**。续传逻辑逐分支走过：每个循环至少推进 1 字节（`end<start` 被拒），不会死循环；`stop()` 与 `active` 的竞态有 `checkStopped()` 兜底 |
| `TransferTerminal.java`（9 行，全读） | `request`/`claim` 的线性化：pause 可被 cancel 覆盖、终态只能被认领一次 | **无发现**。语义自洽 |
| `TransferCoordinator.java`（33 行，全读） | `drain()` 的 `active`/`draining` 状态机、重入保护、`starter` 抛异常时是否补 `completed` | **无发现**。`completed` 有 `AtomicBoolean` 去重，重入被 `draining` 挡住 |
| `DownloadHistoryStore.java`（96 行，全读） | debounce 与 `flushAndClose` 的竞态、`drain` 与 `markChanged` 交错、`io.shutdown()` 后仍能完成写、重复 `close()` | **无发现**。逐一推演了 4 种交错，最后一次写都不会丢；`io.shutdown()` 允许已提交任务跑完，`drain` 内的直接 `writeOnce()` 不受影响 |
| `UpdateClient.java`（170 行，全读） | 版本比较方向、404 当"无正式版本"、重定向 host 白名单、摘要/大小校验、`fallbackUrl` 为空时的使用 | **无发现**。版本比较用 `compare(latest,current)<=0` 方向正确；`fallbackUrl` 的消费方 `UpdateOffer` 全部有 `isEmpty()` 保护 |
| `RemoteConfigClient.java`（543 行，重点段全读） | `storeCache` 的合并语义（已是"按集合合并"而非整串覆盖）、`loadCache` 的解析容错、`get()` 的 host 校验与大小上限、`parseRelease` 的必填校验、`parseNotices` 的 `popupMode` 存在性判断 | **除 BUG-3 外无发现**。`storeCache` 的合并修复是有效的（DFW-97 那次修对了） |
| `NoticeCenter.java`（184 行，全读） | 排序稳定性、无 id 公告丢弃、`badgeText` 99+ 边界、`markAllRead`、`isRead` | **除 BUG-3 外无发现**。排序比较器满足传递性（先 pinned 后时间），`badgeVisible(0)==false` 正确 |
| `Toolbox.java`（664 行，全读 + 24 个静态方法的风险普查） | 星座日期映射（12 个月逐月核对，**全部正确**）、身份证校验位权重与码表（`79A584216379A5842`/`10X98765432` **正确**）、密码池大小与 `passwordPoolSize`（24/25/8 **一致**）、`uuidNameBased` 的版本位/variant 位、`String.format("%02x", byte)` 的负数行为（Formatter 对 Byte 有特判，**不是 bug**）、`randomNumbers` 之外的 `nextInt` 调用（`diceRoll` 硬编码 6/100、`decide` 有长度保护、`generatePassword` 池非空）、`urlDecode` 的 `%` 越界处理、`calculate` 的除零与溢出 | **除 BUG-1/BUG-2 外无发现**。24 个含风险调用的方法逐个过了调用点 |
| `LanzouCore.java` 的并发原语（重点抽查） | 三个 `ThreadLocal`（`ASYNC_PAGE_CANCEL` / `UA_SCOPE` / `SEARCH_CONNECTIONS`）的 set/remove 配对、线程池关闭、`volatile` 字段 | **无发现**。三个 ThreadLocal **全部**有 `finally` 内的 `remove()`（`:379`、`:387` 的 finally、`restoreUaScope`），不会在线程池线程上残留旧值 |
| `MainActivity.java` 生命周期（重点抽查） | `onDestroy` 的清理完整性、静态 Activity 引用 | **无发现**。`onDestroy` 依次做了：清 `ACTIVE_INSTANCE`/`ACTIVE_OWNER`、`ui.removeCallbacksAndMessages(null)`、flush 历史、释放媒体、关 `core`/`directResolver`/`adbShell`、三个线程池 `shutdownNow()` —— 覆盖得相当完整；`ACTIVE_INSTANCE` 是 `WeakReference`，`ACTIVE_OWNER` 在 `onDestroy` 里置空 |
| `ToolHost.java` 的输入解析 | `parseInt(EditText,int)` 的兜底 | **无发现**。`:1214` 用 try/catch 包了 `Integer.parseInt`，非法输入回落到默认值——这是**对的**写法，反衬出 BUG-1/BUG-2 是漏写 |

---

## 盲区（想查但没查成）

1. **没有真机 / 模拟器验证**。两条崩溃的"异常 + 无捕获路径"都已确认，但**没有在设备上实际点出来**。要坐实，最快的方式是各加一条 Robolectric/纯 JVM 用例（`Toolbox.timestampAuto("99999999999999999999")` 与 `Toolbox.randomNumbers(0,2147483647,1,false,0)`），断言它们**不抛异常**。
2. **`MainActivity.java` 只做了抽查，不是全审**。5074 行里我重点看了 `onDestroy`、更新/公告链路、静态引用；**没有逐行读完**。页面状态机、`pageKind` 切换、`View` 回收路径、下载 UI 的 `drainDownloadUi` 队列都只看了片段。
3. **`LanzouCore.java`（1918 行）只抽查了并发原语与线程池**，搜索状态机（`SourceSearchState` 的 `active/terminal/apiInFlight` 等十几个标志位）**没有逐条推演**。这是全项目状态最复杂的地方，也是我最不放心的一块。
4. **`rikkahub/`（vendor 区）完全没看**。按任务要求只该审 DFWX PATCH 层，我没有开始。
5. **网络相关缺陷无法静态构造**：BUG-3 需要一个"单路失败"的真实网络环境，本任务无法制造。
6. **下载链路只审了引擎层**（`SegmentDownloader` / `TransferCoordinator` / `TransferTerminal` / `DownloadHistoryStore`），**没审** `DownloadEntry` 状态机与 `MainActivity` 里下载页 UI 的联动。
