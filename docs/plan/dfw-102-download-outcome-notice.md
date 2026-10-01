# DFW-102：下载完成 / 失败通知（顶部滑下 + 带文件名）

> 用户原话（2026-10-01）：
> **「你可以加入下载完毕或下载失败的通知，而且写明白啥东西下载好了，别只写个下载完成。
> 用你推荐的那个从上往下显示的通知吧。」**

## 1. 为什么现在必须补

DFW-94 删掉下载悬浮窗之后，**下载完成就没有任何反馈了**（失败也只剩旧的小黑胶囊）。
用户当时明确"只删悬浮窗"，所以没擅自加东西；这次用户主动要求补上。

## 2. 设计

用**已有的**顶部通知条 `showTopBanner(消息, 时长, 图标)`（DFW-66 建的，从顶部滑下，
用户 2026-09-30 说过"像手机收到的消息一样从顶部滑下来"），不新造组件。

| 结果 | 文案 | 时长 | 图标 |
|---|---|---|---|
| 完成 | `已下载 · <文件名>` | 3200ms | `ic_download` |
| 失败 | `下载失败 · <文件名>：<原因>` | 5200ms | `ic_close` |
| 批量完成 | `已下载 N 个文件 · 最新：<文件名>` | 3200ms | `ic_download` |

原因截断到 60 字（通知条正文最多 4 行，超出自动省略号）。

## 3. 关键决定：单一收口点

下载状态出口有七八个（外部直链 / 解析后传输 / 更新包 / 重试 / 续传收尾 / 段下载器回调……），
**分散写必然漏**。所以挂在 `drainDownloadUi` 里 —— 它是所有状态变化的唯一汇聚点：

```java
boolean stateChanged=!entry.state.equals(entry.uiState);
entry.uiState=entry.state;
if(stateChanged)noteDownloadOutcome(entry);
```

## 4. 两个必须堵的坑（否则这个改动会让体验变差）

**坑 1：`drainDownloadUi` 每 100ms 处理一次同一条目。**
不加 `stateChanged` 判断，下载过程中会每秒弹十次通知。
→ 守卫 `noticeOnlyFiresOnARealStateChange`。

**坑 2：启动时从历史记录恢复的旧条目会重播通知。**
恢复路径原先只同步 `savedState`，没同步 `uiState`；而 `uiState` 默认空串，
于是一条几天前「已完成」的记录第一次流经 `drainDownloadUi` 时会被判成"刚刚变化"，
**每开一次软件就重播一遍旧通知**。
→ 修法：恢复时 `entry.savedState=entry.state;entry.uiState=entry.state;`
→ 守卫 `historyRestoreSyncsUiStateSoOldNoticesDoNotReplay`。

## 5. 同时清掉的重复提示

下面 4 处旧的小黑胶囊会和顶部条**重复弹**，已移除：

- `retryDownload` catch：`showNotice("重试失败："+message,true)`
- `retryLanzouPlusUpdate` catch：`showNotice("重试失败："+message,true)`
- `startExternalTransfer` 的 failed 回调：`if(entry.percent==0)showNotice("下载失败："+entry.error,true)`
- `startResolvedTransfer` 的失败回调：`showNotice("下载失败："+entry.error,true)`

失败详情仍然完整保留在**下载页**（点条目能看原因），通知条只负责"提醒"。

## 6. 验证

- 守卫 `DownloadOutcomeNoticeJvmTest`（4 例），**两条反向探针实测变红**：
  去掉 `if(stateChanged)` → 红；去掉恢复时的 `uiState` 同步 → 红
- `ci-gate.sh docs` + `ci-gate.sh unit`
- `assembleEmptyRelease` + `aapt dump badging` 断言 `native-code: arm64-v8a`

## 7. 前车之鉴

- **不要用"最近的上一个空行"定位方法边界**（DFW-98 因此误删 `onTrimMemory`）——
  守卫里取方法体一律**花括号配对**。
- 通知条是**全站共用**组件，改它要跑 `TopBannerAndUpdateJvmTest`。
