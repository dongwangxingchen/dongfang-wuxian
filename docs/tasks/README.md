# 任务卡：已迁移

> **2026-10-01 起，本目录不再是任务队列。**

## 现在去哪里找任务

**任务面板是唯一队列**（`taskctl`，项目 `dfwx-android`）：

```bash
taskctl issue list --project dfwx-android                       # 全部
taskctl issue list --project dfwx-android --status in_review    # 只等验收的
taskctl issue get DFW-19                                        # 单卡详情（含 version）
```

> ⚠️ **`issue list` 不接受 `--thread-id`**（会报 `Unknown option --thread-id`，2026-10-01 实测）。
> 本文档旧版示例带了它，已更正。`issue get` / `issue update` / `issue move` / `comment add`
> 这几个命令**才**支持 `--thread-id dfwx`。

- 在做的卡：状态 `in_progress`
- 等验收的卡：状态 `in_review`
- 加评论：`taskctl comment add <taskId> --thread-id dfwx --body "..."`
  （**注意**：`comment list <taskId>` 不要加 `--thread-id`，会报 `Unknown option`）
- 改状态：`taskctl issue move <taskId> --thread-id dfwx --status in_review`
- 改标题/描述：**必须先 `issue get` 拿最新 `version`**，再
  `taskctl issue update <taskId> --thread-id dfwx --title "..." --description "..." --if_version <N>`
  （`--if_version` 是乐观锁，传旧值会被拒；描述很长时先写进文件再 `--description-file 文件`，
  避免命令行里的反引号被 shell 吃掉）

## 历史卡在哪

2026-09-26 之前的 30 张 `DFWX-*.md` 卡片已归档到
**`docs/archive/tasks/legacy-cards/`** —— 只能当**历史背景**读，
里面的"当前任务""下一步"等字样**全部已过期**，不要照着排活。

## 为什么迁移

旧卡是"一个 markdown 文件一张卡"，状态靠人肉维护在文件里，换窗口就容易读到过期结论
（本项目实际踩到过：README 和 current-state.md 互相矛盾、都声称自己是准的）。
任务面板是单一数据源，状态不会腐烂。
