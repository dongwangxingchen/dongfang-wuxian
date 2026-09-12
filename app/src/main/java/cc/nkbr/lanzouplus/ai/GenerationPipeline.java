package cc.nkbr.lanzouplus.ai;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

/**
 * 生成管线辅助（移植自 RikkaHub {@code app/src/main/java/me/rerere/rikkahub/data/ai/GenerationLoop.kt}
 * 与 {@code GenerationPrompts.kt} 的核心装配逻辑）。
 *
 * <p><b>上游对照</b>：
 * <ul>
 *   <li>{@code GenerationLoop.kt}：GenerationLoop 主循环（system 装配 → limitContext 截断 →
 *       transformer 管道 → provider.streamText → 工具检查 → 审批暂停）。</li>
 *   <li>{@code GenerationPrompts.kt}：buildMemoryPrompt 等提示词装配。</li>
 * </ul>
 *
 * <p><b>本版实现范围（P0）</b>：请求装配（system + 上下文截断 + 消息序列化 + 助手参数覆盖）。
 * 工具调用循环、审批状态机、MCP 属上游高级能力，本版不实现（已在 AiAssistant 中标注占位）。
 *
 * <p><b>上游更新跟随</b>：若上游调整 system 装配顺序或消息序列化格式，
 * 只需同步本类 {@code buildChatRequest} 与 {@code serializeMessages} 两个方法。
 */
public final class GenerationPipeline {

  /** 上下文条数上限的全局默认（助手未指定时使用） */
  public static final int DEFAULT_CONTEXT_LIMIT = 12;

  /**
   * 装配 OpenAI 兼容的 /chat/completions 请求体。
   *
   * <p>对齐上游装配顺序：system 提示词 → 阶梯截断后的消息 → 参数覆盖。
   *
   * @param model       模型名（助手覆盖优先，调用方已解析）
   * @param assistant   助手（提供 systemPrompt 与参数覆盖，可为 null）
   * @param messages    完整消息序列（时间正序）
   * @param toolsPrompt 工具推荐手册（本项目的差异化能力，拼在 system 末尾；可为空）
   * @param defaultMaxTokens 全局默认最大输出（助手未覆盖时使用）
   */
  public static JSONObject buildChatRequest(String model, AiAssistant assistant, List<AiMessage> messages,
                                           String toolsPrompt, int defaultMaxTokens, boolean stream) throws Exception {
    int limit = assistant != null && assistant.contextMessageLimit > 0
        ? assistant.contextMessageLimit : DEFAULT_CONTEXT_LIMIT;
    List<AiMessage> trimmed = ContextLimiter.limitContext(messages, limit);

    JSONArray payload = new JSONArray();
    String system = buildSystemPrompt(assistant, toolsPrompt);
    if (!system.isEmpty()) {
      payload.put(new JSONObject().put("role", AiMessage.ROLE_SYSTEM).put("content", system));
    }
    for (AiMessage m : trimmed) {
      JSONObject o = serializeMessage(m);
      if (o != null) payload.put(o);
    }

    JSONObject body = new JSONObject();
    body.put("model", model);
    body.put("messages", payload);
    body.put("stream", stream);
    body.put("max_tokens", assistant != null && assistant.maxTokens != null ? assistant.maxTokens : defaultMaxTokens);
    // "可空=跟随"：只有助手显式设了值才覆盖
    if (assistant != null && assistant.temperature != null) body.put("temperature", assistant.temperature.doubleValue());
    if (assistant != null && assistant.topP != null) body.put("top_p", assistant.topP.doubleValue());
    return body;
  }

  /** system 提示词装配：助手人格 + 工具手册（对齐上游"人格在前、能力说明在后"的顺序） */
  public static String buildSystemPrompt(AiAssistant assistant, String toolsPrompt) {
    StringBuilder sb = new StringBuilder();
    if (assistant != null && assistant.systemPrompt != null && !assistant.systemPrompt.trim().isEmpty()) {
      sb.append(assistant.systemPrompt.trim());
    }
    if (toolsPrompt != null && !toolsPrompt.trim().isEmpty()) {
      if (sb.length() > 0) sb.append("\n\n");
      sb.append(toolsPrompt.trim());
    }
    return sb.toString();
  }

  /**
   * 单条消息序列化。
   *
   * <p>对齐上游：assistant 消息若含 tool_calls 需带 tool_calls 字段；tool 消息带 tool_call_id。
   * 本版工具调用未启用，但保留分支以对齐上游结构（便于将来补齐时不改装配层）。
   */
  private static JSONObject serializeMessage(AiMessage m) throws Exception {
    if (m == null) return null;
    JSONObject o = new JSONObject();
    o.put("role", m.role);
    o.put("content", m.text());
    // 工具调用（上游结构保留）
    JSONArray toolCalls = null;
    for (AiMessage.Part p : m.parts) {
      if (AiMessage.PART_TOOL.equals(p.type) && !p.toolCallId.isEmpty()) {
        if (toolCalls == null) toolCalls = new JSONArray();
        toolCalls.put(new JSONObject()
            .put("id", p.toolCallId)
            .put("type", "function")
            .put("function", new JSONObject().put("name", p.toolName).put("arguments",
                p.toolInput == null || p.toolInput.isEmpty() ? "{}" : p.toolInput)));
      }
    }
    if (toolCalls != null) o.put("tool_calls", toolCalls);
    return o;
  }

  private GenerationPipeline() {}
}
