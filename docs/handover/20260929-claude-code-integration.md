# Claude Code 接入档案（2026-09-29）

> 本目录不提交 git，属本机本地文件。这份档案说明 Claude Code 在本项目的接入形态，
> 供后续维护（人工或 AI）与排障使用。

## 一句话

Claude Code 是 `~/.agents` 资产池的**薄适配层**，不持有任何真身。所有真身在 `~/.agents/skills`
与 `~/.zcode/skills`，Claude 侧只放链接与它独有的运行时配置。

**配置放哪一层是踩过的坑**：项目配置必须放 `~/heiyao/.claude/`（用户打开的工作区根），
不是 `~/heiyao/src/.claude/`。Claude Code 从 cwd 向**上**查找，放 src 层会完全不被加载。

## 文件归属（谁是"主"）

| 路径 | 归属 | 内容 | 进 git |
|---|---|---|---|
| `~/heiyao/.claude/settings.json` | 本项目 | 权限、hooks、autoMemory 开关 | 是 |
| `~/heiyao/.claude/hooks/guard-dangerous.sh` | 本项目 | 危险命令闸门 | 是 |
| `~/heiyao/.claude/agents/*.md` | 本项目 | 7 个 Android 子智能体 | 是 |
| `~/heiyao/.claude/skills/*` | 本项目 | 4 个项目技能（**相对**链接到 `src/.zcode/skills/`） | 是 |
| `~/.claude.json` → `mcpServers.codegraph` | 用户 | MCP 注册（**用户级，免项目批准**） | 否 |
| `~/.claude/skills/*` | 用户 | 35 个技能链接 → `~/.agents/skills` / `~/.zcode/skills` | 否 |
| `~/.claude/settings.json` | **cc-switch** | base URL、模型映射、代理 env | 否 |
| `~/.agents/adapters/claude/` | 用户 | allowlist + 同步脚本 | 否 |

**不要往 `~/.claude/settings.json` 加你的长期字段**——它由 cc-switch 覆写。

## 常用维护命令

```bash
# 增删技能后重跑同步（--prune 会删除已从清单移除的链接，不动真身）
bash ~/.agents/adapters/claude/sync.sh --prune

# 改清单：直接编辑 allowlist 后重跑
$EDITOR ~/.agents/adapters/claude/skills.allowlist

# 安全 hook 自测
printf '{"tool_name":"Bash","tool_input":{"command":"adb shell pm clear x"}}' \
  | bash ~/heiyao/.claude/hooks/guard-dangerous.sh; echo $?   # 应为 2

# 断链自检
find ~/.claude/skills -maxdepth 1 -type l ! -exec test -e {} \; -print
find ~/heiyao/.claude/skills -maxdepth 1 -type l ! -exec test -e {} \; -print
```

## 实测结论（别再用旧说法）

1. **Tool Search 无效**：第三方站静默忽略 `tool_reference`（返 200 但不生效），
   故 `ENABLE_TOOL_SEARCH=false`。MCP schema 全量常驻，选 MCP 要按 token 称重。
2. **codegraph 可以挂 MCP 了**：v0.10.0 握手 2.6s（旧结论"150s 超时禁止挂载"已作废）。
   用 `--tool-profile=scout` 把成本从 6,168 砍到 3,350 tokens。
3. **不建 `~/.agents/mcp/registry.json`**：`~/.agents/mcp.json` 已由同步脚本生成，再建是第三套事实源。
4. **不建 CLAUDE.md**：v2.1.283 默认策略 `claude-md-or-agents-md` 已直接加载 `AGENTS.md`；
   建了 CLAUDE.md 反而会抢占 AGENTS.md 的加载。
5. **MCP 免批准只能靠用户级注册**（结论已细化，见下）：项目级 `.mcp.json` 的批准状态
   **按项目根隔离**——同一个文件在 `~/heiyao`（cwd）下是 `✔ Connected`，但从 `~/heiyao/src`
   （自带 `.git`，是独立项目根）看又是 `⏸ Pending approval`。
   而 `~/.claude.json` 顶层 `mcpServers` 的注册**处处可用**，实测两个目录都是
   `Scope: User config / ✔ Connected`。所以最终只保留用户级，
   项目级副本已删除（备份留在 `~/heiyao/.claude/.mcp.json.bak-moved-to-user-level`）。

   排查这类问题的两个坑：trust 登记必须用**解析后的真实路径**——`/tmp/xx` 要写成
   `/private/tmp/xx`，否则信任不生效、连带批准也不生效（实测：写错路径时项目级一直 Pending，
   改成解析路径后立刻可用）；另外 `enableAllProjectMcpServers` 在当前版本并不能免掉批准门。
6. **settings.json 不向上遍历，但 CLAUDE.md/AGENTS.md 向上遍历**——两套机制方向相反。
   Claude Code 只在 cwd 找 `.claude/settings.json`，**不含父目录**
   （官方仍是待实现特性，GitHub issue #12962；本机 `/tmp` 双场景实测：父有子有，
   子只加载子；给父加 `.git` 也不改变行为）。
   而 memory 文件是「cwd 及其所有祖先目录全加载，按文件系统根→cwd 排序」。
   这解释了为什么配置放错层会静默失效，而 AGENTS.md 放哪都能被读到。
7. **AGENTS.md 是"替补"而非"并列"**：官方策略 `claude-md-or-agents-md`（默认）——
   只有 cwd 及其祖先目录里**完全没有** `CLAUDE.md` / `.claude/CLAUDE.md` / `CLAUDE.local.md` 时，
   `AGENTS.md` 才被读；一旦有，AGENTS.md 被整体忽略（不只是不加载那一个文件）。
   本机全链路已盘查确认无任何 CLAUDE.md，故 AGENTS.md 正常生效。
   想两个都读需在 `~/.claude/settings.json` 的
   `pluginConfigs["agents-md@builtin"].options.instructionFiles` 设为 `claude-md-and-agents-md`
   （**项目级设置会被忽略**，只认用户级/`--settings`/managed）。
   注意：**`CLAUDE.local.md` 也算抑制项**——别在 `~/heiyao` 放这个文件。

## 通道归属：cc-switch 单端口多槽（重要）

cc-switch 只监听 `127.0.0.1:15721`，**靠 URL 路径前缀区分客户端**，每个客户端各有一套独立槽位：

| 请求路径 | 归属 | 当前 provider | 状态 |
|---|---|---|---|
| `/v1/messages`（无前缀） | `claude`（终端 CLI） | 便宜 pianyitoken.lat | 已死 → 502 |
| `/claude-desktop/v1/messages` | `claude-desktop` | **dk api.dshapi.icu** | **健康 → 200** |
| `/v1/chat/completions` | `codex` | DeepSeek 等 | 独立 |

- Claude Desktop（3p 模式）注入的 base URL 带 `/claude-desktop` 前缀 → 走 dk ✓
- 终端 `claude` CLI 读 `~/.claude/settings.json` 的无前缀 URL → 走便宜 ✗

**终端 CLI 的解法：`claude-dk` wrapper**（2026-09-29 实测通过）

```bash
claude-dk -p "你好"        # 等价于 claude，但走健康通道
```

脚本在 `~/.local/bin/claude-dk`（已在 PATH）。原理：不改任何现有配置，只在本次调用叠加一个
临时 `--settings`，把 base URL 加 `/claude-desktop` 前缀。token 从
`~/.cc-switch/cc-switch.db` 现读现用，写进 `mktemp` 出的 600 权限文件，进程退出即删。

三个已实测的坑：

- `--settings` 叠加**只覆盖它写了 key**，其余照旧继承——这是官方明确的 merge 语义，实测一致。
- `/claude-desktop` 槽**不认 `PROXY_MANAGED` 占位符**（返 401 "gateway token 无效"），
  必须用真实 token；而终端 CLI 走 cc-switch 时正是靠 `PROXY_MANAGED` 冒充，两者不通用。
- 想改 cc-switch 那个死掉的 `claude` 槽，需在 cc-switch 里把 `visibleApps.claude`
  从 `false` 改回 `true`（现为隐藏状态，UI 上看不到就没法切）——在
  `~/.cc-switch/settings.json`。这是 GUI 应用的地盘，改前先备份。

**所以"终端 502"不代表"用户的通道死了"**。排查时先确认客户端身份：
读会话文件的 `entrypoint` 字段（`claude-desktop-3p` = 桌面端内嵌 Code；`sdk-cli` = 终端），
再按 `app_type` 分组看 `proxy_request_logs`，**不要跨槽推断**。

`/claude-desktop` 路径只认它声明过的模型名（claude-opus-5 / sonnet-5 / haiku-4-5 / fable-5），
认证必须用 `Authorization: Bearer <gateway token>`（token 在
`~/.cc-switch/cc-switch.db` 的 `settings.claude_desktop_gateway_token`）。

## 未接入项（刻意不入，不是遗漏）

| 项 | 原因 |
|---|---|
| `board` (dashi-taskboard) MCP | 8,678 tokens 常驻，改用 CLI `dashi-taskboard` |
| `serena` MCP | 9,730 tokens 常驻，无查询 CLI，按需再评估 |
| `anysearch` MCP | 5,971 tokens 常驻，技能自带 CLI |
| `repomix` / `chrome-devtools` / `playwright` / `context7` MCP | 非 Android 日常必需，需要时临时挂 |
| Claude auto memory | 已关闭，避免与 AGENTS.md + docs/tasks + docs/handover 形成第二套记忆 |
| `context-curator` agent | 报告明确不迁，最容易变成第二套记忆系统 |
| marketplace 插件 | 现有 35 技能已覆盖，重复引入只会制造第二套工作流 |

## 排障

**Q: Claude 说工具不可用 / MCP 没连上**
A: 先 `claude mcp list`。若显示 `⏸ Pending approval`，说明是项目级 `.mcp.json`——
把它移到用户级 `~/.claude.json` 的 `mcpServers`。本机 codegraph 路径含固定版本号
（`v0.10.0`），升级后路径会变，需同步改。

**Q: 技能没生效 / agent 看不到**
A: 确认 cwd 是 `~/heiyao`（或它的子目录）。`ls ~/.claude/skills/` 看链接在不在；
断链用上面的 find 命令查；确认技能名在 `skills.allowlist` 里且 `SKILL.md` 有 `name`/`description`。

**Q: 报 "this workspace has not been trusted"**
A: `~/.claude.json` 的 `projects["/Users/lishaowei/heiyao"].hasTrustDialogAccepted` 需为 `true`。
已登记。

**Q: hook 没拦住危险命令**
A: 确认 `settings.json` 里 `hooks.PreToolUse` 的路径用了 `$CLAUDE_PROJECT_DIR`；
手动喂 JSON 测（见上面维护命令，应为 exit 2）。

**Q: 模型请求 502**
A: 先分清是哪个客户端。`~/.claude/settings.json` 里那个**无前缀**的 base URL
属于终端 CLI 槽（provider = 便宜，上游连不通，日志里 175 条 502 全是 `client error (Connect)`）；
桌面端走 `/claude-desktop` 前缀，正常。详见上面「通道归属」节。
终端要用健康通道，直接用 `claude-dk`。

**Q: 加了配置但 Claude 没反应（权限/hooks 不生效）**
A: 九成是层级放错。settings.json **不向上遍历**：cwd 是 `~/heiyao` 就只读
`~/heiyao/.claude/settings.json`，放 `~/heiyao/src/.claude/` 不会被加载。
反过来 AGENTS.md 是**向上遍历**的，放高层也能被读到——别用这个现象去推断 settings.json。
