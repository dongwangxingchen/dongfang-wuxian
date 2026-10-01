# DFWX-UI-006：AI 页空会话占位文案删除

状态：待执行（用户已明确要求删除，方案待确认）
前置：无
来源：用户真机实测（v1.22.8）原话——"下面这些文字（红框内的）也会崩坏，我认为你可以把文字删掉，这些无用的"

## 用户要求（逐字）

> "下面这些文字（红框内的）也会崩坏，我认为你可以把文字删掉，这些无用的。"

红框内 = AI 对话页空会话时屏幕下方的两行提示：

- 第一行：`开始你的对话`
- 第二行：`在下方输入框提问，AI 会在这里回复`

## 现状与出处

两行文字是**本地补丁，不是上游代码**（上游空会话只有滚动 Spacer，整页空白）：

| 补丁 | 文件 | 内容 |
|---|---|---|
| P19 | `rikkahub/app/src/main/java/me/rerere/rikkahub/ui/pages/chat/ChatList.kt:326-355` | 新增 `messageNodes.isEmpty()` 分支（:330）：`item(key="DfwxEmptyChatState")`（:331）内 `Box(Modifier.fillParentMaxSize(), contentAlignment=BottomCenter)`（:332-335）包一个 `Column` 显示两行 `Text`（:341-351）；:355 起 `else { itemsIndexed(...) }`。**注意**：`fillParentMaxSize` 是 `LazyItemScope` 的成员函数，无需 import（`PATCHES.md` P19 行写"新增 import"有误，移除时不必找它） |
| P20 | 同上（:327-329 注释 + :334） | 居中改沉底（`BottomCenter`），让文案贴输入框、随键盘一起浮起 |
| P21 | 同上（:311 注释 + :314 `contentPadding` + :338-339） | 空态 `contentPadding` 去掉非空列表专用的 `32dp` 滚底空间；`Column` 用 `padding(bottom = 10.dp)` 精确控制视觉缝 ≈24dp |

收尾大括号在 :411-412（`} // [DFWX P19] itemsIndexed 内容闭包` / `} // [DFWX P19] else 闭包`），移除时一并处理。

配套：`app/src/main/AndroidManifest.xml:15` MainActivity 的 `windowSoftInputMode="adjustResize"`。

三处补丁的完整描述在 `rikkahub/PATCHES.md` 的 P19/P20/P21 行。

## 用户报告的问题

键盘弹出时这两行文字"崩坏"（位置异常/抖动）。用户截图（键盘已弹出）显示：两行文字**没有贴住输入框**，与输入框之间有明显空隙（约 40dp 量级），与 P21 声称的"视觉缝 ≈24dp"不符。

**可能机制（待验证，勿当结论）**：

1. `Modifier.fillParentMaxSize()` 在 LazyColumn viewport 高度变化（键盘弹出）时的行为——它取的是 **viewport 尺寸**，键盘弹出后 viewport 变矮，Box 应随之变矮并让文字跟着下沉。若某条路径下该 modifier 的尺寸未随 viewport 更新，文字就会停在旧位置。
2. `contentPadding` 与 `innerPadding.calculateBottomPadding()` 的叠加时机：`innerPadding` 来自 `Scaffold`，而 `ChatInput` 自身的 `imePadding()` 会让 bottomBar 高度逐帧变化；若 `innerPadding` 更新滞后于 bottomBar，空态 Box 的底边就会暂时对不上。
3. `verticalArrangement = Arrangement.spacedBy(12.dp)` 对**单个** item 不产生额外间距，理论上无害——但需确认空态分支是否真的只渲染 1 个 item（若上游在 `ChatList` 里另插了别的 item，间距会被算进去）。

**注意**：用户已明确"这些无用的，可以删掉"。因此本卡的默认方案是**删除**，上面三条机制只用于判断"删干净后是否还有残留问题"（例如删掉后 LazyColumn 空态是否还有别的错位来源）。

## 允许修改

- `rikkahub/app/src/main/java/me/rerere/rikkahub/ui/pages/chat/ChatList.kt`：移除 P19/P20/P21 三处空态定制（`if/else` 包裹结构 + `DfwxEmptyChatState` item 整块；无 import 需要清理——`fillParentMaxSize` 是 `LazyItemScope` 的成员函数，见 :333）；
- `rikkahub/PATCHES.md`：把 P19/P20/P21 标记为已移除（**不得删行**——保留历史与"上游同步时不要再重放"的说明，格式参照 P13 编号空洞的处理方式）；
- 对应测试：检查 `rikkahub/app/src/test/java/me/rerere/rikkahub/dfwx/` 下是否有测试依赖这两行文字（本次只读复核未见引用；若发现则同步更新）。

## 禁止修改

- 不得删除 `ChatList.kt` 里其它与空态无关的逻辑（`ScrollBottom` spacer、`innerPadding` 通用处理等）；
- 不得改 `ChatPage.kt` 的 Scaffold/TopBar 结构（那是 UI-005 的范围）；
- 不得动其他页面空态；
- 不得读取或输出 `local.properties`；不得提交/push/发布。

## 删前必须确认的三件事

1. **上游同步影响**：删除后 `ChatList.kt` 要与上游恢复一致（差异应为零或仅剩与空态无关的既有补丁）。用 `diff` 对照上游 2.5.5 原文件确认——上游快照 `/tmp/rikkahub-255/` 已清理，**需重新下载**（上游仓库 `re-ovo/rikkahub` tag `2.5.5`）。
   **不要用本地 `dfwx-pre-255-import` tag 当上游原件**：它只是"2.5.5 导入前"的那个提交，里面 P19 补丁早就存在（空态补丁引入于 `46e30a3`，v1.19.1 时代），拿它做对照会得出"差异为零"的错误结论。若无法联网下载上游原件，退化为结构验收：确认全仓再无 `DfwxEmptyChatState` 引用、`LazyColumn` 体内 `itemsIndexed` 前不再有 `if/else` 包裹、编译与测试全绿。
   **这一点很重要**：删掉后这个文件就不需要每次同步都重放三处补丁了，维护成本下降。
2. **空会话观感**：删掉两行字后空会话=纯黑背景 + 顶栏 + 输入框。确认这符合预期（用户已说"无用的"，且上游原样即如此）。
3. **是否留下其他提示**：产品上是否需要一个更克制的空态（例如只有光标闪烁的输入框）。**默认不做**——用户明确要删；若想加回来，属新需求，另开卡并先向用户简报。

## 验收

- `ChatList.kt` 与上游 2.5.5 该文件差异为零（或仅剩已声明保留的补丁）；
- vendor 全量测试 `:rikkahub-app:testDebugUnitTest` 全绿；
- `rikkahub/PATCHES.md` 中 P19/P20/P21 状态更新；
- 真机确认：空会话无两行文字，键盘弹出时输入框正常跟随、上方无残留元素（与 UI-005 一起验收）。

## 回退

`git checkout -- rikkahub/app/src/main/java/me/rerere/rikkahub/ui/pages/chat/ChatList.kt` 即可恢复三处补丁（`PATCHES.md` 文本改动一并回退）。
