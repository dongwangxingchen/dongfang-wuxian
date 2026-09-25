---
name: codebase-memory-cli
description: 代码图谱 codebase-memory 的命令行用法——stdio MCP 形态握手超时(150s+)禁止再挂载,一律走 cli 模式(2.5-3s/次)。dongfang-wuxian 全仓库图谱 20348 节点。
---

# codebase-memory CLI 用法（2026-09-25 实测固化）

## 铁律：不要尝试挂载它的 MCP 形态

`mcp.servers` 里的 codebase-memory 每次会话都挂不上的原因是**硬性的**：stdio 模式启动要加载
全部图谱（mem.init budget_mb=4096），实测 JSON-RPC 握手 **150 秒以上**仍未完成，任何客户端
的连接超时都等不起。看到它"注册了但没挂上"**不是配置坏了，不要再修、不要重装、不要加超时**。

## 正确用法：cli 模式（每次 2.5-3 秒，含加载）

```bash
CB=/Users/lishaowei/.local/lib/codebase-memory-mcp/v0.10.0/codebase-memory-mcp
$CB cli --json <tool> '<json参数>' 2>/dev/null | tail -1
```

- stderr 有 mem.init 等日志噪声：`2>/dev/null` 丢掉，`tail -1` 取最后一行 JSON。
- 输出 JSON 的正文在 `content[0].text` 里（结构化结果在 `structuredContent`）。
- 每次调用都重新加载（2.5-3s 固定开销），**批量问题攒一次查**，不要一条一条问。

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
