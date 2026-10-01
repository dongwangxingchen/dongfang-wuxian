# DFW-94：删除「下载悬浮窗」

> 用户原话（2026-10-01）：**"没啥用，还丑的慌，而且还挡地方。"**
> 追问「要不要连下载记录一起删」时用户明确：**"我都已经有个下载界面了，怎么可能不保留所有的记录呢？"**
> → **只删悬浮卡；下载页与全部历史记录必须原样保留。**

## 1. 问题定义

`MainActivity` 里有一套「下载悬浮卡」：文件一开始解析就在顶部浮层（`toastLayer`）插一张卡片，
显示图标/文件名/进度条，点一下暂停、长按取消、右滑关掉；超过 3 个任务时合并成一张
「N 个下载任务 · 解析 x · 下载 y」的汇总卡，长按还能弹一个「下载任务」网格对话框。

它和**必须保留**的三样东西共用了同一套底座，所以不能整块删：

| 必须保留 | 位置 | 为什么 |
|---|---|---|
| 下载页（列表/批量/筛选） | `renderDownloads` 及 `drainDownloadUi` 的 `downloadsPage` 分支 | 用户要的"记录" |
| 下载历史弹窗 | `showWebDownloadHistoryDialogNow` (`:4174`) | 网页下载入口的历史 |
| 顶部通知条 | `showNotice` / `showTopBanner` / `addToastPanel` / `dismissPanel` | 全站 227 处共用 |

## 2. 取证（改前实测，`MainActivity.java`）

悬浮窗专属（**删**）：

- 字段 `:105`：`downloadToastEntries`、`taskToasts`、`toastLabels`、`toastBars`
- 字段 `:118`：`mergedDownloadToast`、`mergedDownloadToastLabel`、`mergedDownloadToastBar`
- 内部类 `:119` `BatchToastState`、`:137` `DownloadPopupHolder`
- 方法：`createDownloadToast` `:4155`、`tapDownloadToast` `:4160`、`holdDownloadToast` `:4161`、
  `updateDownloadToast` `:4162`、`visibleDownloadToastEntries` `:4163`、`detachDownloadToast` `:4167`、
  `detachMergedDownloadToast` `:4168`、`createMergedDownloadToast` `:4169`、
  `updateMergedDownloadToast` `:4170`、`syncDownloadToastPresentation` `:4171`、
  `showDownloadToastHistory` `:4172`、`dismissDownloadToast` `:4190`、
  `createBatchParsingToast` `:4191`、`finishBatchParsing` `:4192`、`finishDownloadResolution` `:4060`
- `detachToastNow` `:4166`：改完只剩它自己没人调 → 一并删（`updateToastViewport` 仍被 `addToastPanel`/`dismissPanel` 用，保留）

连带变成死概念（**删**）：

- `DownloadEntry`（`:128`）的 `toastDismissed` / `dismissScheduled` / `suppressParseToast` / `resolveDone` / `resolutionAdvanced`
- `PendingRetry`（`:121`）的 `preserveToast`
- `newDownloadEntry(..., boolean suppressParseToast, ...)` 形参
- `beginDownload(..., boolean showParseToast, ...)` / `beginDownloadGated(...)` 形参
- **`beginDownload(Models.Item, boolean, Runnable)` 三参重载（`:4062`）改前已无任何调用点** → 顺带删

保留不动：

- `toastPanel()` `:4178`、`addToastPanel` `:4179`、`swipePanel` `:4188`、`dismissPanel` `:4189`、
  `toastContentHeight` `:4164`、`updateToastViewport` `:4165`、`toastLayer` / `toastScroll`
- `DownloadEntry.nextBatch` / `batchAdvanced` / `advanceBatch` `:4089` —— 这是**批量下载队列**的推进机制，与提示卡无关

## 3. 影响面

- `drainDownloadUi` `:4220`：删掉末尾的 toast 注册/自动消失/刷新三段；`downloadsPage` 分支一字不动。
  局部变量 `terminal` 只被 toast 分支用 → 删；`parsing` 仍被下载页分支用 → 留。
- 入口 `requestRetryDownload` / `retryDownload` / `retryLanzouPlusUpdate`：去掉 `preserveToast` 穿参
  （原本只用来决定"重试前要不要把旧卡片关掉"）。
- 用户可见行为：**下载不再有悬浮卡**；下载页、下载历史、顶部通知、失败提示全部不变。

## 4. 验证

1. 反向探针守卫测试 `DownloadToastRemovalJvmTest`：断言 15 个已删符号在源码里 **0 命中**，
   同时断言 6 个必须保留的底座符号 **≥1 命中**（防止删过头把 `toastPanel`/`addToastPanel` 也带走）。
   **先写测试跑红，再改代码跑绿。**
2. `DFWX_OFFLINE=1 bash tools/ci-gate.sh docs` + `unit`。
3. `assembleEmptyRelease` + `aapt dump badging` 断言 `native-code: arm64-v8a`。

## 5. 前车之鉴（本卡必须避开的坑）

- **DFW-98 删过头事故**：当时按"最近的上一个空行"定位方法起点，把 `showSupportNotice` 和
  `onTrimMemory` 一起删了。**本卡一律用「唯一标记串定位 + 断言命中行数」删除，不按空行猜边界。**
- 本项目 `MainActivity` 是**超长单行方法**风格，不能用「整行替换」的长字符串做锚点
  （一行 2000+ 字符），必须用**短唯一子串**做锚点。
