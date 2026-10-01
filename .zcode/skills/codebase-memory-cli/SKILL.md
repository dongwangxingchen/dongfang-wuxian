---
name: codebase-memory-cli
description: 代码图谱 codebase-memory 的两种用法——MCP stdio 形态已可在 2.6s 握手挂载（v0.10.0 复测,scout profile 3.4k tokens）;CLI 模式仍适用于脚本与批量查询。dongfang-wuxian 全仓库图谱 20348 节点。
---

# codebase-memory 用法（2026-09-29 复测更新）

## 结论先行：MCP 形态已可用，2026-09-25 那条"禁止挂载"结论作废

**历史结论**（2026-09-25 实测）：stdio 模式要加载全部图谱（mem.init budget_mb=4096），
JSON-RPC 握手 150 秒以上未完成，客户端连接超时，因此当时定为一律走 cli 模式。

**2026-09-29 在 v0.10.0 上复测，三点全部改善**：

| 测试条件 | 结果 |
|---|---|
| 常规环境 initialize | **2.6s** ✅ |
| 裸环境（`env -i`，模拟 MCP 客户端 spawn） | **2.6s** ✅ |
| 仓库目录内 initialize | **0.0s**（图谱已预热）✅ |

15 个工具正常列出，`list_projects` 调用 0.0s 返回。**当前仓库 `.mcp.json` 因此已挂载它。**

## MCP 形态的成本控制：`--tool-profile=scout`

默认暴露 15 个工具、schema 24,673 字符（≈6,168 tokens）。加 `--tool-profile=scout` 后：

- 7 个工具、schema 13,403 字符（**≈3,350 tokens，成本腰斩**）
- 保留全部查询能力：`search_graph` / `trace_path` / `get_code_snippet` /
  `get_architecture` / `list_projects` / `index_status` / `check_index_coverage`
- 去掉写图操作（`index_repository` / `delete_project` / `manage_adr` / `ingest_traces` 等）

**这是当前推荐形态**——本环境的 Tool Search 已实测失效（上游 GLM 静默忽略 `tool_reference`），
工具 schema 会全部常驻上下文，所以必须靠 profile 收窄而不是靠延迟加载。

需要全量工具时改回默认（去掉 `args`）或用 CLI 模式。

## CLI 模式（脚本与批量场景仍适用，每次 2.5-3 秒）

```bash
CB=/Users/lishaowei/.local/lib/codebase-memory-mcp/v0.10.0/codebase-memory-mcp
$CB cli --json <tool> '<json参数>' 2>/dev/null | tail -1
```

- stderr 有 mem.init 等日志噪声：`2>/dev/null` 丢掉，`tail -1` 取最后一行 JSON。
- 输出 JSON 的正文在 `content[0].text` 里（结构化结果在 `structuredContent`）。
- 每次调用都重新加载（2.5-3s 固定开销），**批量问题攒一次查**，不要一条一条问。
- 判断依据：脚本里调用、或一次性跑多个查询、或不想让 schema 占上下文时用 CLI。

## 可用工具（15 个，stdio tools/list 实测）

index_repository / search_graph / query_graph / trace_path / get_code_snippet /
get_graph_schema / get_architecture / search_code / list_projects / delete_project /
index_status / check_index_coverage / detect_changes / manage_adr / ingest_traces

## 实测可跑的示例

```bash
# 已索引项目（dongfang-wuxian: ~/heiyao/src, 20348 节点/119643 边; 另有 verity-je-6.1-cn）
$CB cli --json list_projects 2>/dev/null | tail -1

# 全文/符号检索（参数名是 pattern 不是 query；project 可省略时默认按名匹配）
$CB cli --json search_code '{"pattern":"detectWeakDevice","project":"dongfang-wuxian","limit":5}' 2>/dev/null | tail -1
# → 返回 qn(全限定名)/label/file/lines/matches，单查询内部耗时 ~300ms
```

## 何时用它 vs 何时用 rg

- **用它**：找符号的引用/被引用关系（search_graph/query_graph）、看架构（get_architecture）、
  跨模块追调用链（trace_path）——这些 rg 做不到或很费轮次。
- **用 rg**：纯文本搜索（改前找位置、找字符串）——rg 毫秒级，别为文本搜索等 3 秒。

## 版本升级提示

二进制是固定路径版本号目录（v0.10.0）。若 `~/heiyao/src` 大改后图谱过期，用
`$CB cli --json detect_changes '{"project":"dongfang-wuxian"}'` 检查，再 index_repository 增量重建。
