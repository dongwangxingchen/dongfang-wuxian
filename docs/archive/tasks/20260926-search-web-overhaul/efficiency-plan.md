# 工作效率提升方案（实测驱动）

> 2026-09-26。用户诉求："在不影响质量的情况下增加效率，甚至会增强质量……我想要的就是极致的效率、极高的质量。"
> 全部结论基于**本机实测**，非推测。

---

## 一、最高价值的 5 条（按收益排序）

### 1. 编译反馈从 5~9 分钟降到 **17 秒**（最大收益）

**实测数据**（`<仓库根>`，2026-09-26）：

| 命令 | 实测耗时 | 用途 |
|---|---|---|
| `:app:compileEmptyDebugJavaWithJavac --offline` | **17s** | 语法/类型自检 |
| 同上（no-op 再跑） | **0.78s** | 缓存命中 |
| `:app:testEmptyDebugUnitTest --tests <单个类>` | **7.6s** | 逻辑回归 |
| `:app:assembleEmptyDebug --offline` | **2m08s** | 需要装机时 |
| `:app:assembleEmptyRelease` | 5~9min | **仅发版** |

**结论**：`assembleEmptyRelease`（R8 + minify + shrinkResources）是发版档，**不该出现在日常迭代循环里**。日常只需 `compileEmptyDebugJavaWithJavac`（17s），比之前快 **20~30 倍**。

**注意**：项目此前记录"不能加 `--offline`（会报 kotlin-dsl 6.6.4 插件解析失败）"，但实测**可以加**——可能因为依赖已全部缓存。若 `--offline` 失败，去掉它仍比 release 快得多。

### 2. 开启 Gradle Build Cache（一行配置）

`gradle.properties` 目前只有 `parallel` 和 `configuration-cache`，**`org.gradle.caching` 未开**（`~/.gradle/caches/build-cache-1` 目录不存在）。

```properties
org.gradle.caching=true
```
收益：跨分支 / clean 后任务产物复用。风险：`~/.gradle` 磁盘增长（可定期清）。

### 3. 建立 L0~L3 验证阶梯（防止误用 release 档）

| 级 | 命令 | 耗时 | 何时用 |
|---|---|---|---|
| **L0** | `:app:compileEmptyDebugJavaWithJavac --offline` | 17s | **每次编辑后** |
| **L1** | `+ :app:testEmptyDebugUnitTest --tests <相关类>` | ~25s | 逻辑改动 |
| **L2** | `:app:assembleEmptyDebug --offline` | 2m08s | 需真机装机 |
| **L3** | `:app:assembleEmptyRelease` | 5~9min | **仅发版** |

### 4. 子智能体契约（防跑偏、防 token 浪费）

**业界实测数据**（Google Research 180 组配置对照实验）：
- 多智能体在**可并行任务**上 **+81%**
- 在**顺序任务**上 **−70%**
- "独立并行（互不通信）"把错误放大 **17.2 倍**；集中式（有 orchestrator）只放大 **4.4 倍**
- Anthropic 实测：多智能体比单智能体多耗 **~15 倍 token**

**可操作判据**（三个问题全"是"才并行）：
1. 是**只读**探索吗？（写 → 串行）
2. 子任务之间**不需要对方产出**吗？（需要 → 是流水线，不是并行负载）
3. 汇总时**不需要统一风格决策**吗？（需要 → 并行产出会冲突）

**本项目具体切法**：4 个只读侦察员按**互不重叠的维度**分工（如：①手势/滚动 ②网络与缓存 ③工具页渲染 ④数据持久化），**写入者永远只有 1 个**。

**子智能体提示词四要素**（Anthropic 官方：缺一个就会跑偏）：目标 + 输出格式 + 工具/来源指引 + 清晰的任务边界。特别要写**"不要做什么"**。

**返回格式契约**（把长报告写文件，只回传摘要）：
```
交付物：完整发现写入 docs/tasks/<slug>/scout-<维度>.md
返回给我：不超过 8 行：
1) 结论一句话
2) 证据：文件:方法名
3) 不确定项
4) 建议下一步
禁止：贴代码原文、复述任务、写"我已完成"的客套话
```
Anthropic 参考量级：子智能体可烧几万 token 探索，但**只返回 1,000~2,000 token 的蒸馏摘要**。

### 5. 验证必须由"没见过生成过程"的独立方做

Anthropic 工程团队结论："**自我评估是陷阱**，对抗式评估智能体才有效。"

本项目的最佳实践不是"另一个 LLM 读代码"，而是**机械验证**：
- `javac` 编译（L0）
- Robolectric 截图逐像素比对（已有 `HomeShotsJvmTest` 管线）
- 结构化日志断言（已有 `DfLog` 的 `DFX|area=|case=|view=|event=` 约定）

---

## 二、工具分工（codebase-memory / Grep / 子智能体）

已确认代码图谱状态：**20,348 节点 / 119,643 边，status=ready**。`parse_partial` 仅 10 个文件且全在 rikkahub vendor 区（非主战语言），`skipped` 为 0。但 **`tools/` 目录按设计未索引**。

| 需求 | 用什么 | 为什么 |
|---|---|---|
| 符号是谁、调用链、影响面 | **codebase-memory** | 结构化、token 便宜、有 in/out 度数 |
| 字符串字面量、资源名、日志 TAG、`tools/` 脚本 | **Grep** | 图谱不索引这些 |
| 广度探索且原始输出不需留在上下文 | **子智能体** | 上下文隔离（最贵，最后手段） |

**口诀**：要"身份"用图谱，要"文本"用 grep，要"隔离噪声"用子智能体。

---

## 三、大文件策略（`MainActivity.java` 实测数据）

**实测**：2037 行、**平均每行 324 字符**、662,374 字节。

**关键推论：行号在这个文件里是不可靠锚点。** 平均每行 324 字符意味着"第 1200 行"是一个巨大的、且随编辑剧烈漂移的位置。

**规范调整建议**：本项目 `AGENTS.md` 要求"给 `文件:行号`"，在这个文件上应改为 **`文件:方法名`**（如 `MainActivity.java:startWebDirectDownload`）——更稳定、更短、对子智能体也不歧义。

**读取策略**：grep 定位 + 局部 read，**永不整文件读**。
**编辑策略**：符号级工具（serena）优先，避免长字符串匹配（项目已三次栽在"一行式方法里插行尾注释吞掉代码"）。

**中期正解**：渐进拆分该文件。662KB 单文件对 AI 协作是系统性负担（读取贵、编辑风险高、不能有第二个并行写入者）。按工具页分组渐进外迁，每组用截图 diff 验证。

---

## 四、顺带发现的规范缺陷（需修）

`<本地技能目录>/` 下**只有 3 个**技能：`codebase-memory-cli`、`dfwx-emulator`、`dfwx-release`。

但项目 `AGENTS.md` §七要求：
- "手势类 bug 强制先走 `<本地技能目录>/gesture-debug/SKILL.md`"
- "所有 bug 诊断走 `<本地技能目录>/android-debug-triage/SKILL.md` 八步闭环"

**这两个技能不存在** → 任何遵守规范的会话都会先撞一次空指针再即兴发挥。这正是"读了 Skill 反而更慢"的典型成因，且是**规范层的错误**。

**建议**：要么恢复这两个技能，要么删掉引用。倾向恢复（手势类 bug 的诊断流程确实值得固化）。

---

## 五、可落地的改进清单（按优先级）

### P0（立即可做）
1. **建立 L0~L3 验证阶梯**，写进 `<本地技能目录>/dfwx-verify/SKILL.md`，并在 `AGENTS.md` 指向它。
   - 收益：编译反馈 **5~9min → 17s**（约 95% 缩减）
   - 风险：无
2. **开启 `org.gradle.caching=true`**。
   - 收益：跨分支/clean 后产物复用
   - 风险：`~/.gradle` 磁盘增长
3. **修掉 `android-debug-triage` / `gesture-debug` 的悬空引用**（恢复或删除）。

### P1（本周）
4. **子智能体契约模板**（四要素 + 固定 8 行返回 + 写入文件）。
   - 收益：防重复劳动、返回 token 从数万降到 1~2k
5. **定义 4 个只读侦察员的标准切分维度 + 1 个写入者规则**，写进 `docs/agents/`。
6. **截图 diff 自动化**（Roborazzi 或自写 PNG diff）。
   - 收益：视觉回归从人工目检变成机器判定
7. **大文件锚点规范改为 `文件:方法名`**。

### P2（结构性）
8. **渐进拆分 `MainActivity.java`**。
   - 收益：解除"同文件单写入者"约束，使多文件并行成为可能
   - 风险：**高风险重构**，必须逐组外迁 + 每组截图 diff 验证
9. **并行写入隔离用 `git worktree`**（需要真并行实现时）。

---

## 六、值得新增的工具

| 工具 | 用途 | 说明 |
|---|---|---|
| **Roborazzi**（`github.com/takahirom/roborazzi`） | Robolectric 截图测试框架 | 直接接已有 `HomeShotsJvmTest` 管线；项目 `lessons.md` 已点名此方向 |
| **Gradle Build Cache** | 构建加速 | 配置项，非新工具，收益/成本比最高 |
| **git worktree** | 并行写入隔离 | Claude Code 官方推荐做法 |

---

## 七、来源

- Anthropic《How we built our multi-agent research system》https://www.anthropic.com/engineering/multi-agent-research-system
- Anthropic《Effective context engineering for AI agents》https://www.anthropic.com/engineering/effective-context-engineering-for-ai-agents
- Anthropic《Building effective agents》https://www.anthropic.com/engineering/building-effective-agents
- Cognition《Don't Build Multi-Agents》https://cognition.com/blog/dont-build-multi-agents
- Google Research《Towards a science of scaling agent systems》arXiv:2512.08296
- Claude Code《Run agents in parallel》https://code.claude.com/docs/en/agents
- UIUC 多智能体 token 成本研究 arXiv:2505.18286
