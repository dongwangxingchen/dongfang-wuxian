# DFW-35 全站弹窗盘点

> **基线**：`MainActivity.java` @ `a51e739`。调查期间 Lead 在并行改这个文件
> （5616 行 → **5623 行**，sha256 前缀 `e1a232b7…` → `d180abaa…`），
> 我**每次引用前都重核过行号**：交付前对着**当时最新**的文件复核了本文全部 16 处行号，**16/16 命中**。
> 但 Lead 的改动仍在继续，**请以方法名为准定位，行号仅作辅助**。
> 只读调查，未改任何 `.java`/`.kt`（`git status` 里 `MainActivity.java` 与 3 个 `*JvmTest.kt`
> 的改动**都是 Lead 的**），未跑 gradle。每条结论带 `文件:行号`。

## 〇、口径说明（为什么是 63 不是 61）

- 全仓 `show*` 方法共 **67** 个，但其中 **28 个是页面导航**，不弹窗：
  `showHomeLanding` / `showFolderPage` / `showDownloads` / `showSettings` / `showTools` /
  `showAiEmbedded` / `showAboutPage` / `showSupportPage` / `showCrashLogPage` / `showSources` …
- 本文口径 = **方法体内真的创建了 `AlertDialog` / `PopupWindow` 的方法**，共 **63** 个，
  已排除两个底座自身（`showRounded:878`、`roundDialog:876`）。
- 因此「61 个 `show*` 弹窗方法」这个说法**把导航页也算进去了**，实际弹窗数是 63。

## 一、共享底座（全站唯一）

```java
// MainActivity.java:876-878
void roundDialog(AlertDialog dialog){
  Window window=dialog.getWindow();
  if(window!=null){
    window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
    int viewport=safeContentWidth(),width=Math.max(dp(1),Math.min(dp(560),viewport-dp(28)));   // ← 宽度上限 560dp
    WindowManager.LayoutParams p=window.getAttributes();
    p.gravity=Gravity.CENTER;p.x=0;p.y=0;p.width=width;p.height=WindowManager.LayoutParams.WRAP_CONTENT;
    window.setAttributes(p);
  }
  …
  panel.setBackground(solidShape(SURFACE,22));panel.setClipToOutline(true);panel.setElevation(dp(10));
  styleDialogButton(dialog.getButton(BUTTON_NEGATIVE),false);   // ← 三个按钮统一塑形
  styleDialogButton(dialog.getButton(BUTTON_POSITIVE),true);
  styleDialogButton(dialog.getButton(BUTTON_NEUTRAL),true);
}
void showRounded(AlertDialog dialog){dialog.show();roundDialog(dialog);}
```

63 个弹窗里 **62 个**走这套底座，**1 个例外**（见问题 1）。

---

## 二、⭐ 真正有问题的（3 条，全部为本次新发现）

### 问题 1（中）`showWebDownloadHistoryDialogNow:5378` 是唯一绕过底座的弹窗，且**宽度与按钮样式都和全站分叉**

它是 63 个里**唯一不用 `showRounded`/`roundDialog`** 的，改成手工复制底座逻辑。复制得不全，产生三处分叉：

| 项 | 底座 `:876-877` | 本弹窗 `:5378` | 后果 |
|---|---|---|---|
| 宽度上限 | `dp(560)` | `dp(600)` | **同一屏幕上宽 40dp** |
| 宽度基准 | `safeContentWidth()`（`:1046`，扣掉 host 左右内边距） | `getResources().getDisplayMetrics().widthPixels`（原始屏宽，不扣内边距） | 刘海/圆角屏上可能贴边 |
| 按钮样式 | 调 `styleDialogButton(...)` ×3 | **没有调用** | 取消/确认按钮**不受全站按钮样式约束** |
| 高度 | `WRAP_CONTENT` | 写死 `Math.min(dp(520), Math.max(dp(220), heightPixels-…))` | 与其它弹窗留白不一致 |

- 宽度原式：`width=Math.min(dp(600),host.getResources().getDisplayMetrics().widthPixels-dp(28))`
- 它确实补了 `solidShape` / `setClipToOutline` / `setElevation` / 透明背景（各 1 次），**只漏了按钮塑形**。
- **最小修法**：把 `roundDialog` 里 `dp()`/`safeContentWidth()` 依赖的 `this` 换成传入的 `Activity host`，
  让它也能复用（它的 `host` 参数本来就有）；或至少补上 `styleDialogButton` 并把 600 改回 560。

### 问题 2（中）`confirmDownload:5049` 内容无上界且**没有 ScrollView** —— 简介长时按钮会被顶出屏幕

- 简介文本**来自网络**：`:5062` `core.fileDescription(original)` 拉回后写进 `description`。
- `:5085 setSelectableLinks(TextView,String)` **只 `setText`，不设 `maxLines`** → 文本长度无上界。
- 该弹窗 `:5061` 是 `setView(panel)` 且 **panel 内没有 `ScrollView`**
  （机械扫描全仓「`setView(` 且无 `ScrollView`」的方法共 4 个，另 3 个已逐条核实内容有界）。
- 底座 `:876` 高度是 `WRAP_CONTENT` → **内容多高对话框就多高**。
- 后果：AlertDialog 的按钮条在对话框**内部**，对话框长过屏幕时「取消 / 下载」一起被顶出去，
  用户**够不到按钮**（也滚不动）。
- 对比：同类长内容弹窗都加了 `ScrollView` —— `showBatchDownloadDetails:4159`、`showSourceCategorySelection:3974`。
- **最小修法**：给 `description` 设 `setMaxLines(6)` + `TruncateAt.END`，或把它包进 `ScrollView` 并限高。

### 问题 3（低，**待真机确认**）`showLanzouAccessPrompt:4486` 缺 `SOFT_INPUT_ADJUST_RESIZE`

- 它在 `setOnShowListener` 里只设 `WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE`，
  **没有 `ADJUST_RESIZE`**；而底座的 `prepareRoundedInputDialog:879` 是两个一起设的：
  `SOFT_INPUT_ADJUST_RESIZE|SOFT_INPUT_STATE_ALWAYS_VISIBLE`。
- 这是个人机验证链路上的**输入弹窗**，键盘弹出时若窗口不 resize，可能盖住按钮。
- ⚠️ **但我不能断定**：`ADJUST_UNSPECIFIED` 时系统可能自行 resize/pan。
  **无真机截图前这条只算"待确认"，不算已证实缺陷。**

---

## 三、已被既有审计记录（**引用，不重复上报**）

以下 3 个弹窗是**死代码**（零调用点），但**已在 `docs/audit/20261002-code-health.md:23` 记为「严重 #3：
73 个方法定义了但零调用点」**，本次不重复计为新问题：

| 行号 | 方法 | 现状 |
|---|---|---|
| `:2711` | `showDirectoryParseRecoveryDialog` | 0 调用点。该审计已指出「目录解析失败」现场**拿不到「换基础链接」出口** |
| `:4943` | `downloadMenu`（7 项长按菜单） | 0 调用点。下载行现在挂的是 `showBatchDownloadActions`（`:4133` `actions.setOnClickListener(v->showBatchDownloadActions(batchId))`） |
| `:4853` | `confirmRestoreOfficialSources` | 0 调用点，同属那 73 个。（同组的 `confirmResetAllSources:4855` **是活的**，`:867` `recoveryGroup()` 有调用，勿误删） |

---

## 四、明确的「没有发现」（这四类我都逐条核过，**没凑数**）

1. **破坏性操作缺二次确认 —— 0 个。** 逐条验过：
   - `downloadMenu:4943` 的「删除记录」「删除文件」分别进 `removeDownloadRecord:5041` 与
     `confirmDeleteFile:5043`，**两者都自带确认框**（不是无确认）。
   - `confirmDownloadEntriesAction:4155` 对 action 1/2/3 都有 `setPositiveButton` 确认，只有 action 0（暂停/继续）不需要。
   - `showDownloadMaintenanceMenu:4254-4258` 四个破坏项**全部**转 `confirmDeleteAll*` / `confirmClearStoredSharePasswords`。
   - 全部 `confirmDelete*` / `confirmClear*` / `restoreSettingsDefaults` 都带「取消 + 确认」两个按钮。
2. **同一功能两个弹窗入口 —— 0 个。** 查了三对疑似：
   `showCurrentSourceDestinationPicker:3869` 是 `showSourceDestinationPicker:3872` 的**薄包装（正确复用）**；
   单条（`downloadMenu`）/ 批量（`showBatchDownloadActions:4154`）/ 全量（`showDownloadMaintenanceMenu:4242`）
   三处删除是**不同作用域**，各自确认，不算重复入口。
3. **长列表没有滚动 —— 只有问题 2 一处。** 其余含列表的弹窗用 `setItems`/`setSingleChoiceItems`/
   `setMultiChoiceItems`（AlertDialog 自带 ListView 滚动）；自定义 view 的都核实了内容有界
   （`showSearchModeDialog:3797` 固定 3 行；`showDownloadMaintenanceMenu:4242` 最多 5 项 + 1 行说明）。
4. **其它三类也是 0**：新建了 `AlertDialog` 却从不 `show()` 的 **0 个**；
   宽度写死未 clamp 的 **0 个**（所有宽度都能追到 `Math.min(dp(...), 视口-dp(...))`）；
   键盘遮挡 **0 个确定**——
   ⚠️ 我的机械扫描一度把 `showImportRulesLinkDialog:4015` 标红（它不用 `prepareRoundedInputDialog`），
   **回源码核实是假阳性**：它在 `:4020` 自己设了 `ADJUST_RESIZE|STATE_ALWAYS_VISIBLE`。**已排除。**

---

## 五、方法与边界

**方法**：先用脚本按花括号配对抽出全部方法体，再按「体内是否出现 `AlertDialog.Builder`/`new AlertDialog`/
`PopupWindow`」筛弹窗；触发时机与调用点数由**全仓正则搜索**得到（不是猜的）；
问题 1/2/3 的每条断言都**回源码逐行确认**过，且做了 2 次假阳性剔除（见上）。

**边界（没做到的）**：
- **没有真机截图**，所以「是否遮挡」「是否裁切」全部是**读码推断**，不是实测；
  问题 3 已明确标为待确认。
- **没有逐条通读 63 个弹窗的全部文案**：`用途` 列是按方法名 + 体内按钮类型分类的，
  **不是逐个读了 title/message**。「话太多」这个用户抱怨**需要逐条读文案才能定量**，本次没做。
- **`rikkahub/`（AI 页，Kotlin + Compose）不在本次范围** —— 那边的对话框不走 `showRounded`，
  是 Compose 自己的 `AlertDialog`，**全站统一要连它一起算，本次没盘**。
- `MainActivity` 之外的文件（`NoticeBanner.java` 等）没有系统扫描，只按需读了 `MainActivity`。
- 没跑测试；本次是文档任务，无可执行断言。

---

## 附录：63 个弹窗总表

> `调用点` = 全仓该方法的调用处数量（不含定义行）。`底座` = 是否走 `showRounded`。
> `破坏性` = 体内是否出现 删除/清空/卸载/重置/覆盖/清除 之一（仅作线索，**是否真删数据以正文第四节为准**）。

| 行号 | 方法 | 调用点 | 用途 | 底座 | 破坏性 |
|---|---|---|---|---|---|
| 727 | `show` | 7 处 | 展示信息 | PopupWindow | — |
| 1298 | `confirmClearSearchHistory` | 1 处 | 确认·破坏性 | showRounded | ⚠ 是 |
| 1312 | `showBreadcrumbChildren` | 3 处 | 展示信息 | PopupWindow | — |
| 1314 | `dismissBreadcrumbChooser` | 5 处 | 展示信息 | PopupWindow | — |
| 1318 | `showFolderParseFailure` | 3 处 | 提示/错误 | showRounded | — |
| 1414 | `showChangelogDialog` | 2 处 | 设置/输入 | showRounded | — |
| 1683 | `showAlternativeDownloads` | 1 处 | 选择 | showRounded | — |
| 1844 | `showBackdoorNotice` | 2 处 | 展示信息 | showRounded | — |
| 2711 | `showDirectoryParseRecoveryDialog` | **零调用点** | 提示/错误 | showRounded | — |
| 2892 | `renameSelectedCompositeFolder` | 1 处 | 展示信息 | showRounded | — |
| 2895 | `showSelectionCopyOptions` | 1 处 | 选择 | showRounded | — |
| 2899 | `confirmBulkDownload` | 1 处 | 确认 | showRounded | — |
| 3223 | `confirmClearStoredSharePasswords` | 1 处 | 确认·破坏性 | showRounded | ⚠ 是 |
| 3233 | `confirmClearCrashLog` | 1 处 | 确认·破坏性 | showRounded | ⚠ 是 |
| 3699 | `showAiPermissionIntro` | 1 处 | 展示信息 | showRounded | — |
| 3759 | `showAdbPermissionDialog` | 7 处 | 设置/输入 | showRounded | — |
| 3760 | `showAdbDebugMethods` | 1 处 | 选择 | showRounded | — |
| 3789 | `showUaPresetPicker` | 1 处 | 选择 | showRounded | — |
| 3791 | `showUaSettingsDialog` | 2 处 | 设置/输入 | showRounded | ⚠ 是 |
| 3797 | `showSearchModeDialog` | 1 处 | 设置/输入 | showRounded | — |
| 3817 | `checkStartupLanzouBaseOrigin` | 1 处 | 展示信息 | showRounded | — |
| 3872 | `showSourceDestinationPicker` | 3 处 | 提示/错误 | showRounded | — |
| 3923 | `showCreateDestinationFolderDialog` | 1 处 | 设置/输入 | showRounded | — |
| 3929 | `renameSelectedCompositeSource` | 1 处 | 展示信息 | showRounded | — |
| 3974 | `showSourceCategorySelection` | 1 处 | 提示/错误 | showRounded | ⚠ 是 |
| 3976 | `showRenameSourceCategory` | 1 处 | 设置/输入 | showRounded | — |
| 3977 | `confirmDeleteSourceCategories` | 1 处 | 确认·破坏性 | showRounded | ⚠ 是 |
| 3979 | `confirmRemoveSelectedSources` | 1 处 | 确认·破坏性 | showRounded | — |
| 3983 | `showSourceTypeSelectionDialog` | 1 处 | 提示/错误 | showRounded | ⚠ 是 |
| 3990 | `shareSources` | 3 处 | 选择 | showRounded | — |
| 3993 | `confirmRetestSelectedSources` | 1 处 | 确认 | showRounded | — |
| 4000 | `showAddCompositeFolderDialog` | 1 处 | 设置/输入 | showRounded | — |
| 4001 | `showAddCompositeNodeDialog` | 1 处 | 设置/输入 | showRounded | — |
| 4004 | `showSourceAddFailures` | 1 处 | 提示/错误 | showRounded | — |
| 4005 | `addSourceDialog` | 1 处 | 展示信息 | showRounded | — |
| 4012 | `showImportRulesMenu` | 1 处 | 选择 | showRounded | — |
| 4015 | `showImportRulesLinkDialog` | 1 处 | 设置/输入 | showRounded | — |
| 4154 | `showBatchDownloadActions` | 1 处 | 选择 | showRounded | ⚠ 是 |
| 4155 | `confirmDownloadEntriesAction` | 9 处 | 确认 | showRounded | ⚠ 是 |
| 4159 | `showBatchDownloadDetails` | 2 处 | 展示信息 | showRounded | ⚠ 是 |
| 4242 | `showDownloadMaintenanceMenu` | 1 处 | 展示信息 | showRounded | ⚠ 是 |
| 4486 | `showLanzouAccessPrompt` | 7 处 | 设置/输入 | showRounded | — |
| 4850 | `restoreSettingsDefaults` | 1 处 | 确认·破坏性 | showRounded | ⚠ 是 |
| 4853 | `confirmRestoreOfficialSources` | **零调用点** | 确认·破坏性 | showRounded | — |
| 4855 | `confirmResetAllSources` | **零调用点** | 确认·破坏性 | showRounded | ⚠ 是 |
| 4878 | `openSelectedDownloadLocations` | 1 处 | 选择 | showRounded | — |
| 4879 | `confirmDeleteAllDownloadRecords` | 1 处 | 确认·破坏性 | showRounded | ⚠ 是 |
| 4880 | `confirmDeleteAllDownloadedFiles` | 1 处 | 确认·破坏性 | showRounded | ⚠ 是 |
| 4881 | `confirmDeleteSelectedRecords` | 1 处 | 确认·破坏性 | showRounded | ⚠ 是 |
| 4882 | `confirmDeleteSelectedFiles` | 1 处 | 确认·破坏性 | showRounded | ⚠ 是 |
| 4894 | `showExternalInstallConfirmation` | 2 处 | 确认 | showRounded | — |
| 4941 | `showSilentInstallReport` | 1 处 | 展示信息 | showRounded | — |
| 4943 | `downloadMenu` | **零调用点** | 选择 | showRounded | ⚠ 是 |
| 4984 | `requestManageAllFilesAccess` | 9 处 | 展示信息 | showRounded | ⚠ 是 |
| 5032 | `chooseDownloadDirectory` | 3 处 | 展示信息 | showRounded | — |
| 5043 | `confirmDeleteFile` | 2 处 | 确认·破坏性 | showRounded | ⚠ 是 |
| 5048 | `confirmDownload` | 5 处 | 确认 | showRounded | — |
| 5095 | `showLanzouBaseOriginDialog` | 5 处 | 选择 | showRounded | — |
| 5096 | `showCustomLanzouBaseOriginDialog` | 1 处 | 设置/输入 | showRounded | — |
| 5098 | `showLanzouOpenDialog` | 1 处 | 设置/输入 | showRounded | — |
| 5378 | `showWebDownloadHistoryDialogNow` | 1 处 | 设置/输入 | **裸 AlertDialog** | — |
