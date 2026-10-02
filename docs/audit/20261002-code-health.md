# 复查① 代码健康审计（死代码 / 崩溃风险 / 资源泄漏）

- **日期**：2026-10-02 10:30 (+0800)
- **审计对象**：`/Users/<用户名><仓库根>`（《东方无限》Android，包名 `cc.nkbr.lanzouplus`）
- **测量基线**：`HEAD = 93858db 复查① 自查发现并修掉两处`
  - `app/src/main/java/cc/nkbr/lanzouplus/MainActivity.java` = **4527 行**，md5 `67fe416ae5422fee5110559363fa8718`
  - 主源码 47 个 `.java`，共 15129 行；测试 90 个文件（`.kt` + `.java`）
- **方法**：只读。全程只用 `grep` / `read` / `git` / `wc` / `md5`；**未运行 gradle**（用户侧在跑构建/测试）。未改任何代码、未提交、未 push。
- **证据规则**：每条结论都带 `文件:行号`；查了没事的项在文末「已查、无问题」清单里逐条列出，避免与「没查」混淆。

> **审计期间基线变动声明**：审计开始（10:0x）时 `MainActivity.java` 为 **4507 行**、HEAD 为 `485cff3`；
> 10:18:51 该文件被改动，10:22:24 提交为 `93858db`（+20 行，只给 `crossFadeHomeSection` 补了 600ms 结算兜底，
> 无新增/删除方法）。**本报告全部行号已按 `93858db` 重新测量**，死代码扫描也已在新基线上完整重跑一遍。

---

## 摘要：最严重 3 条

| # | 级别 | 一句话 | 位置 |
|---|---|---|---|
| 1 | **严重** | `LanzouWebActivity` 有 WebView 但**整个类没有 `onDestroy()`**，`web.destroy()` 永不调用——这是本仓库唯一漏掉 WebView 销毁的页面 | `LanzouWebActivity.java:13,22,31`（全文件 63 行，无 `onDestroy`） |
| 2 | **严重** | 远程配置缓存 `storeCache` 是**整串覆盖**，而调用方只要 4 路里**任意 1 路**成功就落盘 → 公告接口单次抖动会**抹掉已缓存的公告**，用户下次启动红点/弹窗全没，且无日志 | `RemoteConfigClient.java:260-271`、`305-342`；`MainActivity.java:1709-1730` |
| 3 | **严重** | **73 个方法定义了但零调用点**（`MainActivity` 占 29 个），其中 `showDirectoryParseRecoveryDialog`、`downloadMenu` 是**整块用户可见功能，永远不可达** | 见「附录 A」全表；`MainActivity.java:2342`、`4091` |

---

## 严重

### S-1 WebView 未销毁：`LanzouWebActivity` 缺 `onDestroy()`

- **位置**：`app/src/main/java/cc/nkbr/lanzouplus/LanzouWebActivity.java:13`（字段 `WebView web;`）、`:22`（`web=new WebView(this);`）、`:31`（`root.addView(web,...)`）
- **证据**：
  ```
  $ wc -l app/src/main/java/cc/nkbr/lanzouplus/LanzouWebActivity.java
        63
  $ grep -n "onDestroy\|destroy()" app/src/main/java/cc/nkbr/lanzouplus/LanzouWebActivity.java
  (无输出)
  ```
  对照同批新增的 `FeedbackPage.java:280-287` **有**正确的销毁：
  ```
  280:  @Override protected void onDestroy() {
  281:    if (web != null) {
  282:      host.removeView(web);
  283:      web.destroy();
  284:      web = null;
  285:    }
  ```
  全仓库 `web.destroy()` 只出现在 `FeedbackPage.java:283` 一处。
- **影响**：`WebView` 持有 Activity 的 `Context`，且带独立的 native 渲染进程侧资源。`LanzouWebActivity` 每次从网页页返回都被销毁，但 WebView 只被 GC 回收、不做 `destroy()`，属于 Android 官方明确点名的内存泄漏形态。在低内存设备（本项目 `detectWeakDevice()` 自己就把 ≤1.5GB 设备标为弱机，`MainActivity.java:859`）上反复进出网页页会累积 native 内存。
- **建议**：照 `FeedbackPage.onDestroy()` 补一份（`host.removeView(web)` → `web.destroy()` → `web=null`）；顺带在 `onDestroy` 里 `menuPopup.dismiss()`（`LanzouWebActivity.java:13` 的 `PopupWindow menuPopup` 同样从不主动 dismiss）。
- **测试缺口**：`FeedbackPageSmokeJvmTest.kt` 覆盖了 FeedbackPage 的启动，但**没有任何测试启动过 `LanzouWebActivity`**。

### S-2 部分拉取失败会抹掉已缓存的公告（静默失效，无日志）

- **位置**：
  - `RemoteConfigClient.java:260-271`（`storeCache`）
  - `RemoteConfigClient.java:305-342`（`fetch(Raw sink)`，四路各自 `catch (Exception ignored)`）
  - `MainActivity.java:1709-1730`（`maybeFetchNotices` 的落盘条件）
- **证据**（原文）：
  ```java
  // RemoteConfigClient.java:262-269
  JSONObject o = new JSONObject();
  if (raw.control != null) o.put("control", raw.control);
  if (raw.release != null) o.put("release", raw.release);
  if (raw.notice  != null) o.put("notice",  raw.notice);
  if (raw.changelog != null) o.put("changelog", raw.changelog);
  ctx.getSharedPreferences(CACHE_PREFS, MODE_PRIVATE).edit().putString("raw", o.toString()).apply();   // ← 整串覆盖
  ```
  ```java
  // MainActivity.java:1723-1726
  if(result==null||!result.reachable)return;
  RemoteConfigClient.storeCache(MainActivity.this,collected);
  ```
  而 `reachable` 的定义是「**任意一路**成功」（`RemoteConfigClient.java:310,331,338` 各处置 `anyOk=true`，`:341` 返回 `new Snapshot(anyOk,...)`）。
- **触发路径**（可复现，不需要改代码）：
  1. 某次启动四路全成功 → 缓存含 `notice`；
  2. 下次启动 `notice` 端点超时（`READ_TIMEOUT_MS=4000`，`RemoteConfigClient.java:45`）但 `control` 成功 → `anyOk=true`、`reachable=true`；
  3. `collected.notice == null` → `storeCache` 写出的 JSON **不含 `notice` 键**，覆盖掉旧串；
  4. 再下次启动 `loadCache` 走 `RemoteConfigClient.java:298` 的 `parseNotices(new JSONArray())` → 公告为空 → `refreshNoticeBell()` 无红点、`maybePopupNotices()` 不弹。
- **影响**：DFW-103「公告秒弹 + 红点」整个设计的本地兜底被一次网络抖动清空，**用户看不到任何错误**，日志里也没有一行（两个 catch 都是 `/* 注释 */`，无 `Log.w`）。这正是任务描述里「`catch(...ignored){}` 藏了会静默失效的逻辑」的同类，只是这次藏在**写缓存**而不是读缓存。
- **测试缺口**：`NoticeSnapshotCacheJvmTest.kt` 共 8 例（`grep -c "@Test"` 实测），覆盖了往返、损坏、缺集合、解析器复用、sink NPE 回归（`:221-244`）。但 `loadCache_toleratesMissingCollections`（`:160-168`）只验证「**存**部分集合能读」，**没有**任何一例验证「部分集合的新数据不许冲掉旧数据」。该用例的命名反而会让人误以为这条路径已被保护。
- **建议**（二选一，都不大）：
  - **改 `storeCache` 为合并**：读出现有串、`optJSONArray` 取旧值，本次为 null 的键沿用旧值，再整串写回；
  - 或**改调用条件**：只在四路都成功（`control`/`release`/`notice`/`changelog` 全非 null）时才 `storeCache`。
  - 同时给两处 `catch (Exception ignored)` 补 `Log.w`，否则同类问题下次还是查不到。
- **注**：这条**不是**「看起来像」，是读 `storeCache` 的写语义 + `reachable` 的定义 + `MainActivity` 的调用条件三处拼出来的完整链路，全部有行号。

### S-3 73 个方法零调用点；其中 2 个是整块不可达的用户功能

- **统计口径**（可复现）：对 `app/src` 下 **207 个文件**（`.java` + `.kt` + `.js` + `.xml`，含 90 个测试文件）做全量标识符词频统计，再取「声明行存在、但该标识符在全仓库**只出现 1 次**」的方法——出现 1 次即「只有定义、没人调用」。框架回调（`@Override`）已手工剔除。
- **结果**：**73 个**。按文件分布：

  | 文件 | 数量 |
  |---|---|
  | `MainActivity.java` | 29 |
  | `LanzouCore.java` | 13 |
  | `Toolbox.java` | 11 |
  | `Support.java` | 3 |
  | `DiceView.java` | 3 |
  | `TransferCoordinator.java` / `ThemeEngine.java` / `Models.java` / `DirectLinkResolver.java` | 各 2 |
  | `ToolHost.java` / `NavBall.java` / `DownloadSourcePolicy.java` / `DfwxSkeleton.java` / `DfwxLoadingRing.java` / `AiPermissionGate.java` | 各 1 |

- **其中两条是「整块功能不可达」，不只是死重**：

  **(a) 目录解析失败的恢复对话框永远弹不出来** — `MainActivity.java:2342`
  ```java
  void showDirectoryParseRecoveryDialog(Models.Source source,String message){ ... }
  ```
  这个对话框带「稍后 / 更换基础链接 / UA 设置」三个按钮 + 「不再提醒」勾选（写 `SharedPreferences("directory_parse_recovery_v147")`）。
  证据：全仓库唯一提到 `directory_parse_recovery_v147` 的地方就是它自己的方法体内部（`grep -rn "directory_parse_recovery_v147" app/src` 只命中 `MainActivity.java:2342` 这一行）。
  `showLanzouBaseOriginDialog(` 的调用点共 4 处：`MainActivity.java:2342`（**就在这个死方法体内**）、`:3360`、`:3363`（设置页的「基础链接」行）、`:4200`（无参重载转发）。
  也就是说这个对话框在**设置页仍然可达**，但在**「目录解析失败」这条路径上不可达**。
  真实路径是 `openFolder()` 的失败分支（`MainActivity.java:2294-2295`）：只渲染一张「目录解析失败」占位页 + 一句 toast「回到顶部下拉可重试」。**用户在这个现场拿不到「换基础链接」这个出口**，只能自己进设置页找。

  **(b) 下载项的 7 项长按菜单没有任何入口** — `MainActivity.java:4091`
  ```java
  void downloadMenu(DownloadEntry entry){String[] actions={"跳转 Download 目录","安装","删除记录","删除文件","分享文件","分享蓝奏云链接","更多方式"}; ... }
  ```
  证据：下载行走的是 `bindDownloadActions()`（`MainActivity.java:3633`）的行内图标按钮，**没有任何 `setOnLongClickListener` 挂在下载行上**；`downloadMenu` 自身零调用。

- **连带死代码（传递性）**：`shareDownloadedFile`（`MainActivity.java:4152`）与 `shareDownloadLink`（`MainActivity.java:4153`）**唯一调用点就在 `downloadMenu` 体内（`:4091`）**，因此一并不可达 —— 计入后实际死方法 **73** 个（29 个 MainActivity 含这 2 个）。
  反例（已核实**仍然活着**，不要误删）：`openWithMore` 另有调用点 `:4036`（`openSelectedWithMore`）；`openDownloadDirectory` 另有 `:175, :3599`；`installEntry` 另有 `:2236, :3599, :3631, :4047` 等。
- **影响**：`MainActivity.java` 已经 4527 行且被 `docs/plan/risk-register.md` R-10 标为 P1（行数核对见 M-5）。死方法让「哪条路径才是真的」无法从代码上判断——本次审计就出现了「读 `openFolder` 以为有恢复对话框、实际没有」的误导。
- **建议**：按 R-10 的渐进拆分顺序，**先删这 73 个再做拆分**；对 (a)(b) 两条先做产品决策——是要恢复接线（那 `openFolder` 失败分支应改调 `showDirectoryParseRecoveryDialog`），还是确认废弃（那就删）。删之前建议先补一条「死代码不许回流」的守卫测试（现有 `DocsConsistencyJvmTest` / `RepoHygieneJvmTest` 是合适的位置）。
- **全表见「附录 A」。**

---

## 中等

### M-1 `values-night/styles.xml` 是与默认值完全同效的冗余覆盖

- **位置**：`app/src/main/res/values-night/styles.xml:1`（整文件 1 行）
- **证据**：
  ```
  $ grep -o 'style name="[A-Za-z]*"' app/src/main/res/values/styles.xml
  style name="AppTheme" / style name="DfwxDialog" / style name="DfwxDialogAnim"
  $ grep -o 'style name="[A-Za-z]*"' app/src/main/res/values-night/styles.xml
  style name="AppTheme" / style name="DfwxDialog"
  ```
  两个文件里 `AppTheme`、`DfwxDialog` 的**每一个 `<item>` 都逐字相同**（含 `android:windowAnimationStyle`→`@style/DfwxDialogAnim`）。夜间文件唯一「少」的是没重复定义 `DfwxDialogAnim`，而 Android 的资源限定符是**逐资源**回退的，`@style/DfwxDialogAnim` 会落到默认 `values/` 去解析 —— **最终效果与默认完全一致**。
- **影响**：无功能影响，但这是一份「看着像有夜间适配、实际什么也没改」的假象；后人若只改 `values/` 会以为夜间漏改，只改 `values-night/` 又会以为生效了（其实被默认值覆盖的那部分不会）。属于会持续误导维护者的资源。
- **建议**：整个 `values-night/styles.xml` 删除；或反过来，如果确实想要夜间差异，就把差异项真正写进去并补一条资源断言测试。

### M-2 三个「安装入口」的守卫链抄了 3 份，且守卫会被执行两次

- **位置**：`MainActivity.java:4047-4052`（`installEntry`）与 `MainActivity.java:4076-4082`（`installEntryWithSystemInstaller`）
- **证据**：`installEntry` 的前 4 条守卫与 `installEntryWithSystemInstaller` 的前 4 条**逐字相同**：
  ```java
  // 4048-4050  ≡  4077-4079
  if(!readyFile(entry))return;
  if(!entry.name.toLowerCase(Locale.ROOT).endsWith(".apk")){showNotice("该文件不是 APK，无法安装",false);return;}
  if(DownloadSourcePolicy.requiresInstallConfirmation(entry.source)&&!entry.installConfirmationGranted){showExternalInstallConfirmation(entry);return;}
  if(!entry.expectedUpdateVersion.isEmpty()&&!entry.updateVerified){verifyCloudUpdateEntry(entry);return;}
  ```
  而 `installEntry` 的**最后一行就是 `installEntryWithSystemInstaller(entry);`**（`:4052`），所以这几条守卫在一次调用里**跑两遍**。第三个入口 `autoInstallCompletedEntry`（`:4075`）又抄了其中 3 条（少了「确认外部来源」那条）。
- **影响**：今天只是重复计算；风险在于「两个地方各改一半」。例如将来只给 `installEntry` 加一条新守卫、忘了给 `installEntryWithSystemInstaller` 加，就会在「自动安装完成」路径（`autoInstallCompletedEntry`）上被绕过——那正是安装安全边界所在。
- **建议**：让 `installEntryWithSystemInstaller` 只保留它**独有**的那一条（`canRequestPackageInstalls()` 检查，`:4080`），把公共守卫收敛成一个 `boolean passesInstallGuards(DownloadEntry)`；`autoInstallCompletedEntry` 同理复用。

### M-3 赞助/感谢页整块 UI 在 `MainActivity` 与 `SupportActivity` 各存一份

- **位置**：`MainActivity.java:3915-3955`（`renderThankYou()`）↔ `SupportActivity.java:312-343`（`renderThankYou()`）
- **证据**：把两份 `renderThankYou()` 的代码体去掉缩进后取交集，**逐字相同的有 19 行**；且重复的不只是碎片，是整段结构：
  ```
  TextView badge=text("诚信支持者",12,PRIMARY);
  GradientDrawable badgeBg=solidShape(ThemeEngine.tint(PRIMARY,28),20);
  long paidAt=Support.paidAt(this);
  String date=paidAt>0?android.text.format.DateFormat.getDateFormat(this).format(new java.util.Date(paidAt)):"";
  TextView detail=text(date.isEmpty()?"全部下载权限已开放":"解锁于 "+date+" · 全部下载权限已开放",13,MUTED);
  TextView footnote=text("本软件承诺永久更新 · 绝不停更\n这份支持会变成继续更新的底气",11,MUTED);
  ```
  连文案字面量都一模一样。差异只有容器字段名（`supportBody` vs `root`、`supportThankYouMode` vs `thankYouMode`）。
  同一对文件里还重复了 `codeCard(String,int,String)`（两边同名同签名）、价格卡、开发者信卡、`skip.setContentDescription("暂时不支持，继续使用（不付费也能完整使用其它功能…")` 等；按「长度 ≥40 字符的非注释行、去掉缩进后取交集」统计，**`MainActivity` 与 `SupportActivity` 两个文件之间逐字相同的行共 46 行**。
- **影响**：两份都是活代码（`MainActivity.java:4005`、`SupportActivity.java:386` 各有一处调用），改文案/配色必须改两处，漏一处就会出现「同一个感谢页两种样子」。
- **建议**：抽一个 `ThankYouPage`（或把 `SupportActivity.renderThankYou()` 做成唯一实现、`MainActivity` 直接 `startActivity(SupportActivity)` 并带状态）。注意 `SupportActivity` 有独立状态页语义（`thankYouMode`），抽取时先补一条「两页文案一致」的断言测试再动。

### M-4 4 处 `openInputStream` 的 `close()` 不在 `finally`，异常路径泄漏

- **位置**：
  - `MainActivity.java:3000`（`probe`）、`:3004`（`in`）、`:3008`（`exIn`）
  - `ToolHost.java:1427`（`is`）、`:1430`（`is2`）
- **证据**：写法是「开流 → `decodeStream` → 下一句才 `close()`」：
  ```java
  // MainActivity.java:3004
  java.io.InputStream in=getContentResolver().openInputStream(uri);
  android.graphics.Bitmap bitmap=android.graphics.BitmapFactory.decodeStream(in,null,opts);
  if(in!=null)try{in.close();}catch(Exception ignored){...}
  ```
  `MainActivity.java:2996-2997` 外层是 `void runImageCompress(Uri uri){ try{ ... }catch(Exception error){...} }` —— 解码抛异常（OOM 是最现实的：这一步正好在处理大图）时，`close()` 那一句不会执行，外层 catch 把异常吞掉继续跑。
  `ToolHost.java:1427/1430` 同样在一个 `try{...}catch(Exception e){act.showNotice("图片读取失败："+e.getMessage(),true);}` 里。
- **影响**：泄漏的是 `ContentResolver` 打开的 `ParcelFileDescriptor` 流；量级小（每次一张图），靠 GC/finalizer 最终回收，**不会稳定复现崩溃**，所以列中等而不是严重。真正的风险是「大图连续压缩」场景下叠加 native fd 占用。
- **建议**：改成 try-with-resources（本仓库其它地方已经这么写了，例如 `RemoteConfigClient.java:470-471`、`SegmentDownloader.java:79`、`MainActivity.java:2238`），是纯机械改写、零行为变化。

### M-5 `MainActivity` 行数与三处台账全对不上（且又涨了）

- **位置**：`app/src/main/java/cc/nkbr/lanzouplus/MainActivity.java`
- **实测**（`93858db`）：
  ```
  $ wc -l app/src/main/java/cc/nkbr/lanzouplus/MainActivity.java
      4527
  $ git show 93858db:app/src/main/java/cc/nkbr/lanzouplus/MainActivity.java | wc -l
      4527
  ```
- **对照**：

  | 台账位置 | 写的数字 | 与实测 4527 的关系 |
  |---|---|---|
  | `docs/plan/risk-register.md:23`（R-10） | `185e747`=4242、`5d9dd40`=4267 | 两个历史值经 `git show … \| wc -l` 复核**都精确正确**；但**没有当前值**，实际又涨 **+260 行** |
  | 任务面板 **DFW-29** 标题 | 「MainActivity **1981 行**」 | **过期 2.28 倍**（1981 → 4527） |
  | `docs/plan/long-term-maintainability-plan.md:175` | 引用了「MainActivity **1981 行**」并批评其过期 | 该批评本身**仍然成立且更严重了** |
  | `docs/audit/20261002-delivery-consistency.md:347` | 「当前文件 **4527** 行（比台账记录又涨 260 行）」 | ✅ **与本次实测完全一致**（并行队友同时测的） |

- **影响**：R-10 是 P1 风险项，判断「风险上升还是持平」全靠这个数字。现在台账里最新值是 4267，实际 4527，**差 260 行**——R-10 的结论方向（上升）是对的，但支撑数字已过期。
- **建议**：R-10 补一行「`93858db` 实测 4527 行」；DFW-29 标题的 1981 改成实测值并加测量基线（`git show <ref>:… | wc -l`）。更根本的是让 `DocsConsistencyJvmTest` 之类把行数做成**自动断言**，而不是靠人手抄——`long-term-maintainability-plan.md:175` 自己就写了「这不是某个人粗心，是没有机制」。

---

## 轻微

### L-1 `FeedbackPage.hardenWebView()` 里有一句自赋值的空操作

- **位置**：`FeedbackPage.java:227`
- **证据**：`ws.setUserAgentString(ws.getUserAgentString());` —— 把 UA 设成它自己，**零效果**。`ws.setGeolocationEnabled(false)`（`:222`）之前的一整段都是逐条显式钉死官方基线（`setAllowFileAccess(false)` 等，每条都有明确目的），只有这一行是空转。
- **影响**：无功能影响。但从「逐条钉死安全基线」的上下文看，这行很可能本意是设一个**固定 UA**（对照 `LanzouWebActivity.java:22` 也没设 UA，而 `LanzouCore` 那边有一整套 UA 策略）。留着一句空操作会让后来人以为 UA 已被显式控制。
- **建议**：删掉；若本意是固定 UA，就写真实值并补注释说明为什么。

### L-2 `Toolbox` 的 11 个公开静态方法是纯死代码（含一个越界风险）

- **位置**：`Toolbox.java:78 categoryCount`、`:79 toolsInCategory`、`:91 categoryIndex`、`:92 toolId`、`:95 toolKeywords`、`:97 toolCategory`、`:98 toolHeat`、`:99 toolCatalogJson`、`:286 weekdayOf`、`:309 coinFlip`、`:519 uuidBatch`
- **证据**：每个标识符在全 `app/src`（含 `ToolboxLogicJvmTest.kt`）**只出现 1 次**（即声明本身）。例：
  ```
  $ grep -rho "\btoolCatalogJson\b" app/src | wc -l
  1
  ```
- **附带风险**：`:92` `static String toolId(int pos){return TOOLS[pos][0];}` —— **无边界检查**的数组下标。今天没人调用所以不炸；一旦有人按名字接上去且传了越界 `pos`，就是 `ArrayIndexOutOfBoundsException`。`:98 toolHeat` 的 `Integer.parseInt(t[6])` 同理依赖常量表永远合法。
- **建议**：删。若 `Toolbox` 的 `TOOLS` 表将来要对外暴露目录，那应该走一条**带边界检查**的入口，而不是这 11 个散装 getter。

### L-3 `ToolHost.collectDeviceInfo()` 是「注释说防外部引用、但外部引用为零」的空壳

- **位置**：`ToolHost.java:1084`
- **证据**：`String collectDeviceInfo(){return"";}// 已由 deviceinfo 分组卡片取代（保留空壳防外部引用）`
  但 `grep -rho "\bcollectDeviceInfo\b" app/src | wc -l` = **1** —— 外部引用数为零，注释里说的那个「防」并不存在。
- **影响**：注释与事实不符，属于会误导后来人的死代码（保留理由不成立）。
- **建议**：直接删；若要保留，把注释改成真实理由。

### L-4 `LanzouCore.read(File)` 用单次 `read()` 读满文件，可能截断

- **位置**：`LanzouCore.java:1752`
- **证据**：
  ```java
  private static String read(File f)throws Exception{try(FileInputStream in=new FileInputStream(f)){byte[]b=new byte[(int)f.length()];int n=in.read(b);return new String(b,0,n,StandardCharsets.UTF_8);}}
  ```
  两处不严谨：① `InputStream.read(byte[])` **不保证**一次读满，返回 `n` 可能小于 `f.length()`；② `(int)f.length()` 对 >2GB 文件会截断。
- **实际影响**：唯一调用点是 `LanzouCore.java:1324` 读**目录缓存 JSON**（`cached=fromJson(read(cache))`），失败被 `catch(Exception ignored){cached=null;}` 接住 → 退化成**缓存未命中**（多一次网络请求），不会崩、不会显示坏数据。所以列轻微。
- **建议**：换成 `Files.readAllBytes(f.toPath())` 或循环读满（本文件 `:865` 的 asset 读取就是循环读满的正确写法，可直接照抄）。

### L-5 `feedback_skin.js` 的提交检测是 1.2s 一次的永久轮询

- **位置**：`app/src/main/assets/feedback_skin.js:108`（`setInterval(detectSubmitted, 1200);`）
- **证据**：`detectSubmitted()`（`:100-107`）每次执行都读 `document.body.innerText`。`innerText` 会**强制样式重算 + 布局**，FlowUs 表单页 DOM 不小，每 1.2 秒强制一次布局是持续的隐性开销。该 `setInterval` 没有任何 `clearInterval`。
- **实际影响**：WebView 在 `FeedbackPage.onDestroy()`（`FeedbackPage.java:280-287`）里被 `destroy()`，JS 上下文随之销毁，所以**不是泄漏**；只是停留在该页期间的持续耗电/掉帧风险。同文件的 `MutationObserver`（`:84-89`）已经做了 120ms 去抖，是对的——两者可以统一。
- **建议**：把提交检测也挂到已有的 `MutationObserver` 上（或轮询里加「页面已提交就 `clearInterval`」）。另：`skipPreviewMode` 的两次 `setTimeout`（`:127-128`）已经是有界的，不用动。

### L-6 `SketchView.setFixedSize(int,int)` 是空实现且零调用

- **位置**：`MainActivity.java:3128`
- **证据**：`void setFixedSize(int width,int height){invalidate();}` —— 两个参数**一个都没用**，函数体只有一个 `invalidate()`。它是 `SketchView`（`MainActivity.java:3075` 的 `toolHostSketch` 内部类）的方法，全仓库只出现 1 次。
- **影响**：名字承诺「设定固定尺寸」，实际什么都不做。已计入 S-3 的 73 个，这里单列是因为它是**唯一一个方法名与行为明确矛盾**的死代码。
- **建议**：删。

---

## 已查、无问题（明确「查了没事」，不是「没查」）

| # | 检查项 | 结论 | 证据 |
|---|---|---|---|
| N-1 | **v1.0.25 新增符号是否死代码**：`crossFadeHomeSection`、`noteDownloadOutcome`、`SEARCH_MAX_PAGES_GLOBAL`、`BACKDOOR_NOTICE`、`showBackdoorNotice`、`homeHistoryH`、`settingsAction` 四参重载 | **7 个全部存活，无死代码** | 逐个统计出现次数（含 `.kt`/`.js`/`.xml`）：`crossFadeHomeSection` 3（定义 `:1110`，调用 `:1094`、`:1148`）；`noteDownloadOutcome` 2（定义 `:4272`，调用 `:4403`）；`SEARCH_MAX_PAGES_GLOBAL` 3（定义 `:2573`，用 `:2575`、`:2866`）；`BACKDOOR_NOTICE` 2（定义 `:1642`，用 `:1648`）；`showBackdoorNotice` 2（调用 `:1616`，定义 `:1645`）；`homeHistoryH` 3（定义 `:1091`，用 `:1076`、`:1094`）；`settingsAction` 四参重载（定义 `:733`）**有真实调用点** `MainActivity.java:2955`（`settingsAction(R.drawable.ic_refresh,"检查更新",BuildConfig.VERSION_NAME,v->manualCheckForUpdates())`） |
| N-2 | **未使用的 drawable** | **0 个未使用**（52 个 `ic_*.xml` + 4 个 `drawable-nodpi` 图片全部有引用） | 单遍收集全部 `R.drawable.<name>` 引用后与文件名取差集，空集 |
| N-3 | **未使用的 anim** | **0 个未使用**（3 个 `dfwx_*.xml` 全部被引用） | `dfwx_dialog_in/out` 被 `values/styles.xml` 的 `DfwxDialogAnim` 用 `@anim/` 引用；`dfwx_page_open_in` 有代码引用 |
| N-4 | **未使用的 layout** | **不适用**：`app/src/main/res/layout/` 目录不存在（本项目是纯程序化 View，零 XML 布局） | `ls app/src/main/res/layout` → 目录不存在 |
| N-5 | **未使用的 string** | **0 个**：`values/strings.xml` + 6 个语言目录各自只有 `app_name` 一条，被 `AndroidManifest.xml` 的 `android:label` 静态引用，且 `raw/keep.xml` 显式 `tools:keep="@string/dfwx_app_name"` 防收缩 | `app/src/main/res/values/strings.xml:8`、`app/src/main/res/raw/keep.xml:6` |
| N-6 | **未使用的 id** | **0 个**：`values/ids.xml` 的 `dfwx_section_arrow`、`dfwx_section_title` 都有引用 | `MainActivity.java:2629`（`setId`）、`:2641`、`:2659`、`:2663`；测试 `SearchAndSettingsPolishJvmTest.kt:127` |
| N-7 | **未使用的 style** | **0 个**：`AppTheme`（Manifest）、`DfwxDialog`（`AppTheme` 的 `alertDialogTheme`）、`DfwxDialogAnim`（`DfwxDialog` 的 `windowAnimationStyle`）都在引用链上 | `app/src/main/res/values/styles.xml:1` |
| N-8 | **`raw/keep.xml` 是否算未使用资源** | **不算**，是有意的收缩器保留规则，非 `R.` 引用 | `app/src/main/res/raw/keep.xml:6` 的 `tools:keep`，文件头注释写明了理由 |
| N-9 | **除零** | **未发现**。逐条核对了全部 `/` 与 `%` 的算术位置 | 可疑点全部有守卫或定义域保证：`MainActivity.java:875 gridCellWidth` 的 `columns` 唯一来源是 `itemColumns()`（`:873` 恒返回 2 或 4）；`:1030 historyCellWidth` 有 `Math.max(1,columns)`；`:3587 batchDownloadPercent` 有 `isEmpty()` 前置；`:3650 formatEta` 是常量除；`Toolbox.java:632 bmiInfo` 有 `heightCm<50` 前置；`Toolbox.java:645-646 rgbToHsl` 的 `d/(max+min)` 与 `d/(2-max-min)` 都在 `if(max!=min)` 内且分母不可能为 0；`ToolHost.java:1479` 有 `n>0` 前置；`LanzouCore.java:1024/1028/1030/1032/1042` 的分母全走 `Math.max(1,…)`；`Toolbox.java:197` 计算器显式 `throw new ArithmeticException("除零")` |
| N-10 | **数组越界** | **未发现**（活代码中）。`info[0]`/`info[1]`（`MainActivity.java:3247`）来源 `AiPermissionGate.rationale()`（`AiPermissionGate.java:70-81`）**四条分支全部返回长度 2 的数组**；`UpdateOffer.java:155-165` 用 `long[3]` + `for(i<3)` + `NumberFormatException` 兜底；`Toolbox.java:259-260` 的 `c[idx]`/`c[idx+1]` 由 `idx=(month-1)*2` 且 `c.length==24` 保证，`month∈[1,12]` 时 `idx+1≤23`；`MainActivity.java:3216 event.values[0]` 是 `TYPE_ROTATION_VECTOR` 事件（恒 ≥3 分量）。唯一的无界下标 `Toolbox.java:92 toolId(int pos)` 已单列为 L-2（且它是死代码，无调用点） |
| N-11 | **`MainActivity` 的资源释放** | **完整，无问题** | `onDestroy()`（`MainActivity.java:358`）按顺序做了：`ui.removeCallbacksAndMessages(null)` → `flushDownloadHistoryNow()` → `releaseToolMedia()` → `core.close()` → `directResolver.close()` → `adbShell.close()` → `searchIndexIo/io/imageIo.shutdownNow()`。另有 `imageWaiters.clear()` + `imageDeliveries.clear()`（`:214`），且图片加载完成后立即 `imageWaiters.remove(url)`（`:4518`），无 map 累积 |
| N-12 | **`FeedbackPage` 的 WebView 释放** | **正确** | `FeedbackPage.java:280-287`：`host.removeView(web)` → `web.destroy()` → `web=null` |
| N-13 | **`SegmentDownloader` 的流/描述符释放** | **正确** | `Sink.close()`（`SegmentDownloader.java:64`）同时关 `out` 与 `owner` 并保留首个异常；`download()` 用 `try(Sink sink=destinationOut)`（`:73`）；`destinationLength()` 用 try-with-resources（`:100`、`:102`）；`openDestination()` 的两条失败分支都显式 `descriptor.close()`（`:114`） |
| N-14 | **`HttpURLConnection` 释放** | **正确** | 全部走 `finally{connection.disconnect()}`：`RemoteConfigClient.java:483-485`、`UpdateClient.java:120`、`LanzouCore.java:124/635/644/650/887/1719/1721`、`MainActivity.java:4516` 的 `finally{if(c!=null)c.disconnect();}`。`LanzouCore` 另有 `openConnections` 集合 + `cancelLocked()` 统一 `disconnect()`（`:345, :383, :384, :410`） |
| N-15 | **`Cursor` 释放** | **无问题**（无需释放） | 全仓库唯一的 `Cursor` 是 `DownloadFileProvider.java:16` 返回的 `MatrixCursor`——`MatrixCursor` 是**内存游标**，无底层资源，由调用方（系统）关闭，不需要 `close()` |
| N-16 | **`catch(...ignored){}` 是否有静默失效** | **27 处「无日志的静默 catch」逐个看过**（13 处完全空体 `{}` + 14 处只有注释），除 S-2 的机制外**均无问题** | **13 处完全空体**：`MainActivity.java:181`（KeyStore 清理）、`:336`（二次标题同步，首次失败已 `Log.w`）、`:859`（`detectWeakDevice` 探测失败→按不弱处理）、`:1258`（更新记录拉取，`result==null` 已在下游兜底）、`:1719`（公告拉取，同上）、`:1975/:1979/:1989`（模糊监听注册/注销/回调，纯防御）、`:2034`（`dialog.dismiss()`）、`:2843`（剪贴板写入）、`:4056`（URL 解析取 host，失败回落「未知来源」）、`AdbShellManager.java:141`（`RejectedExecutionException`）、`:209`（`removeEvents`）。**14 处只有注释**：`RemoteConfigClient.java:316/325/332/339`（← **S-2 的机制就在这四处**）、`:270`（写缓存失败）、`UpdatePromptPolicy.java:83/116/123`、`UpdateThrottle.java:48`、`NoticeCenter.java:61`、`CrashLogStore.java:49`、`MainActivity.java:1539`（拉不到按正常放行）、`SupportActivity.java:55`（`catch(NullPointerException skipped)`，JVM 渲染无 `ActivityManager`，已注释说明）、`FeedbackPage.java:243`。**注**：S-2 的问题不在「空 catch」本身，而在「被吞掉的异常导致**写缓存丢数据**」——吞异常 + 全量覆盖写，两者叠加才成为缺陷 |
| N-17 | **调试残留**（`printStackTrace` / `System.out` / `System.err`） | **未发现残留** | 全 `app/src/main` 唯一命中是 `App.java:97` 的 `t.printStackTrace(pw)`，其中 `pw` 是 `StringWriter`，即**把堆栈写进崩溃报告文本**，是正常实现而非控制台调试输出 |
| N-18 | **重复代码**：`NoticeBanner` ↔ `SlideSheet` | 已识别，**不单列问题** | 两者共享滑动消除物理：`touchSlop` 计算、`dp()`、`Math.min(1f,Math.abs(dx)/Math.max(1f,dp(260)))`、`requestDisallowInterceptTouchEvent`、复位动画（各 5 行逐字相同）。属于两个独立组件间的小工具重复，抽公共基类的收益低于耦合成本，**建议维持现状** |
| N-19 | **重复代码**：`LumaSlider` ↔ `LumaSwitch` | 已识别，**不单列问题** | `mix(int,int,float)`（`LumaSlider.java:37` / `LumaSwitch.java:39`）**逐字完全相同**；`paint` 字段、`dp()` 亦同。仅 3 行量级，**建议维持现状**或顺手抽一个包内工具类 |
| N-20 | **`MainActivity` 的 `onDestroy` 之后是否还有 UI 回调** | **无问题** | `ui.removeCallbacksAndMessages(null)`（`MainActivity.java:358`）在 `core.close()`/`directResolver.close()` **之前**执行，注释也写明了理由（「先摘掉 UI 待执行消息再 close 组件」）；配合 `drainDownloadUi` 的 `isDestroyed()` 防线 |

---

## 附录 A：73 个零调用点方法全表

> 口径：对 `app/src` 下 207 个文件（含 90 个测试文件、`assets/*.js`、全部 `res/**/*.xml`）做标识符全量词频统计，取「有声明行、但标识符全局仅出现 1 次」的方法；`@Override` 框架回调已剔除。
> 复现命令（无副作用，不写文件）：
> ```bash
> cd <本地目录>/src
> find app/src -type f \( -name "*.java" -o -name "*.kt" -o -name "*.js" -o -name "*.xml" \) | sort > /tmp/f.txt
> LC_ALL=C awk '{while(match($0,/[A-Za-z_][A-Za-z0-9_]*/)){w=substr($0,RSTART,RLENGTH);c[w]++;$0=substr($0,RSTART+RLENGTH)}}END{for(k in c)print k"\t"c[k]}' $(cat /tmp/f.txt) | sort > /tmp/freq.txt
> # 再与声明清单比对，取频次==1 者
> ```

### `MainActivity.java`（29）

| 行号 | 方法 | 备注 |
|---|---|---|
| 627 | `writeDownloadHistoryNow()` | `ToolHost`/测试均未用（`tools/test_premium_save_queue_semantics.py:200` 只做源码字符串提取，不构成调用） |
| 719 | `lumaSlider(int,int,String,LumaSlider.OnChange)` | |
| 748 | `sessionFilterChip(String,boolean,Consumer<Boolean>)` | |
| 756 | `prepareRoundedInputDialog(AlertDialog,View)` | |
| 1156 | `recommendedSourceCard(Models.Source)` | |
| 1676 | `settingsScrollYForTest()` | **名为测试钩子但没有任何测试调用它** |
| 2234 | `showLanzouPlusUpdate(UpdateClient.UpdateInfo)` | |
| 2342 | `showDirectoryParseRecoveryDialog(Models.Source,String)` | **S-3(a)：整块恢复对话框不可达** |
| 2370 | `scheduleInitialFolderAutoExpand()` | 方法体只剩一条说明性注释，是有意留的空壳 |
| 2489 | `rebuildSearchGrid(GridLayout,List<Models.Item>)` | |
| 2522 | `addItemSelectionToCategory(List<Models.Item>,String)` | |
| 2851 | `crashLogFile()` | 同文件有 `privateCrashLogFile()` 与 `crashLogFileForReading()` 在用，这个转发层没人用 |
| 2975 | `rebuildToolStackTop()` | |
| 3128 | `setFixedSize(int,int)` | **L-6：空实现** |
| 3283 | `enterPlaceholderMotion(View)` | |
| 3284 | `placeholderPanel(int,String,String)` | |
| 3311 | `fileListApiSelected()` | 与 `:3312/:3314/:3315` 是同一组模式选择器，四个全死 |
| 3312 | `fileListDirectorySelected()` | |
| 3314 | `searchApiSelected()` | |
| 3315 | `searchDirectorySelected()` | |
| 3359 | `checkStartupLanzouBaseOrigin()` | 方法体非空，是真的逻辑，但没人调 |
| 3398 | `sourceShareText(Models.Source)` | |
| 3465 | `addCurrentSourceToCategory(Models.Source,String)` | |
| 3468 | `refreshSourcesAfterCategoryChange(Collection<Models.Source>)` | |
| 4025 | `confirmRestoreOfficialSources()` | 带确认对话框的完整流程 |
| 4091 | `downloadMenu(DownloadEntry)` | **S-3(b)：7 项长按菜单无入口** |
| 4152 | `shareDownloadedFile(DownloadEntry)` | **传递性死代码**：唯一调用点在 `:4091` |
| 4153 | `shareDownloadLink(DownloadEntry)` | **传递性死代码**：唯一调用点在 `:4091` |
| 4303 | `downloadTargetMime(String)` | 同文件 `downloadMimeType(DownloadEntry)`（`:4092`）是实际在用的那个 |

### `LanzouCore.java`（13）

| 行号 | 方法 | 备注 |
|---|---|---|
| 573 | `directShareNeedsLanzouxMirror(String)` | 含 WAF 特征判断的完整逻辑，未被接上 |
| 814 | `firstWhitespace(String)` | |
| 996 | `uaScopeLabel(int)` | 拼「文件列表、目录搜索、API 搜索、直链解析」标签串 |
| 1007 | `configuredUserAgent(int)` | |
| 1009 | `scopedUserAgent(byte,int)` | 与 `:1007/:1012` 构成一组 UA 决策链，三处全死 |
| 1012 | `firstUaCandidate(int,byte)` | |
| 1096 | `browseSourceMetadata(Models.Source)` | **带网络请求的完整方法**，无人调用 |
| 1130 | `hydrateCompositeMetadata(Models.SourceMember)` | |
| 1136 | `cachedCompositeMember(Models.SourceMember)` | |
| 1158 | `browseFreshPage(String,String,int)` | |
| 1180 | `cachedSearchIndexMatches(String,Set<String>)` | 只是 `cachedPartialIndexMatches(...)` 的转发层 |
| 1526 | `compositeMemberMatches(Models.SourceMember,String)` | |
| 1745 | `capMulti(String,String)` | 多分组捕获版本，同文件 `cap(...)` 才是在用的 |

### `Toolbox.java`（11）

| 行号 | 方法 |
|---|---|
| 78 | `categoryCount()` |
| 79 | `toolsInCategory(int)` |
| 91 | `categoryIndex(String)` |
| 92 | `toolId(int)` ← **L-2：无边界检查** |
| 95 | `toolKeywords(String)` |
| 97 | `toolCategory(String)` |
| 98 | `toolHeat(String)` |
| 99 | `toolCatalogJson()` |
| 286 | `weekdayOf(String)` |
| 309 | `coinFlip()` |
| 519 | `uuidBatch(int,boolean,boolean)` |

### 其余文件（20）

| 文件:行号 | 方法 |
|---|---|
| `Support.java:19` | `lastNagAt(Context)` |
| `Support.java:21` | `setAskOnDownload(Context,boolean)` |
| `Support.java:26` | `touchNag(Context)` |
| `DiceView.java:52` | `setFaceColor(int)` |
| `DiceView.java:53` | `setPipColor(int)` |
| `DiceView.java:114` | `isHeads()` |
| `TransferCoordinator.java:31` | `activeCount()` |
| `TransferCoordinator.java:32` | `pendingCount()` |
| `ThemeEngine.java:93` | `setActive(Context,String)` |
| `ThemeEngine.java:99` | `isLegacy(Context)` |
| `Models.java:78` | `SearchOptions.withMode(int)` |
| `Models.java:102` | `SearchOptions.directoryOnly()` |
| `DirectLinkResolver.java:46` | `effectiveParallelism()` |
| `DirectLinkResolver.java:56` | `prewarmAll(Collection<String>)` |
| `ToolHost.java:1084` | `collectDeviceInfo()` ← **L-3：注释与事实不符** |
| `NavBall.java:371` | `blend(int,int,float)` |
| `DownloadSourcePolicy.java:17` | `isExternal(String)` |
| `DfwxSkeleton.java:30` | `setRows(int)` |
| `DfwxLoadingRing.java:62` | `setRingColor(int)` |
| `AiPermissionGate.java:89` | `openAppSettings(Activity)` |

### 已剔除的假阳性（**不是**死代码，勿删）

| 位置 | 方法 | 剔除理由 |
|---|---|---|
| `AdbShellManager.java:74` | `onServiceDisconnected` | `@Override`，`ServiceConnection` 回调，系统调用 |
| `DownloadFileProvider.java:14` | `openFile` | `@Override`，`ContentProvider` 回调，系统调用 |
| `GlassSurface.java:307` | `getOpacity` | `@Override`，`Drawable` 回调，框架调用 |
| `MainActivity.java:400` | `handleOnBackPressed` | `@Override`，`OnBackPressedCallback` 回调 |
| `ToolHost.java:805/806/807` | `onProgressChanged` / `onStartTrackingTouch` / `onStopTrackingTouch` | `@Override`，`SeekBar.OnSeekBarChangeListener` 回调 |
| `MainActivity.java:1624` | `addContentView` | **提取脚本误判**：这是**调用语句**（调用继承自 `Activity` 的方法），不是声明 |
| `MainActivity.java:1662` | `noticeBellRowGoneForTest` | **不是死代码**：`NoticeCenterUiJvmTest.kt:86` 在调用它。**但**它的实现是 `{return true;}`，断言它等于「断言常量」——该钩子本身不携带任何信息，建议交测试可信度审计处理 |
| `NavBall.java:329/337/341/346/355` | `activePillCountForTest` / `itemCount` / `hasNoticeItem` / `noticeDotVisible` / `clickNoticeItem` | 均被测试调用，是活代码：`activePillCountForTest`→`NoticeFlowJvmTest.kt`；`itemCount`/`hasNoticeItem`/`noticeDotVisible`→`NoticeCenterUiJvmTest.kt`；`clickNoticeItem`→`NavPeerJvmTest.kt` |
| `NoticeCenter.java:181` | `badgeVisible` | 被 `NoticeCenterJvmTest.kt` 调用，活代码 |

---

## 附录 B：本次审计未覆盖 / 需他人接手的部分

| 项 | 为什么没做 | 建议接手方 |
|---|---|---|
| 真机验证 S-1 的 WebView 内存曲线 | 需真机 + 内存采样，且用户侧正在跑构建（禁并行占 CPU） | 真机自测环节 |
| S-2 的修复方案二选一（合并写 / 收紧落盘条件） | 属代码改动，本次只查不改 | 需用户/Lead 拍板后由实现方执行 |
| `noticeBellRowGoneForTest()` 这类「恒真钩子」的鉴别力评估 | 属测试可信度范畴，本次只登记不判级 | `audit-test-integrity`（已在并行进行） |
| `rikkahub/` vendor 区代码 | 本次范围限定在宿主区（`app/src`）；vendor 区改动须走 `rikkahub/PATCHES.md` 流程 | 如需覆盖请另开任务 |
| gradle 编译 / 单元测试实跑 | 用户明确要求「不要跑 gradle」 | 用户侧构建完成后由 Lead 汇总 |
