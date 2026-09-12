package cc.nkbr.lanzouplus.ai;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 对话消息模型（移植自 RikkaHub {@code me.rerere.ai.ui.UIMessage} / {@code UIMessagePart}）。
 *
 * <p><b>上游对照</b>：本文件对应 RikkaHub 的
 * {@code ai/src/main/java/me/rerere/ai/ui/Message.kt}（UIMessage）
 * 与 {@code ai/src/main/java/me/rerere/ai/ui/UIMessagePart.kt}（UIMessagePart）。
 * 上游更新时，只需对照这两个文件同步本类的字段与语义。
 *
 * <p><b>设计要点（忠实对齐上游）</b>：
 * <ul>
 *   <li>一条消息由多个 <b>Part</b> 组成（Text / Reasoning / Tool / Image…），而非单一 content 字符串——
 *       这是上游支持"思考过程 + 正文 + 工具调用"混合内容的根基。</li>
 *   <li>每条消息有稳定的 {@code id}：流式重试时按 id 覆盖而非追加（上游 GenerationLoop 的策略）。</li>
 *   <li>{@code usage} 记录 token 用量，供 UI 显示 NERD 行。</li>
 * </ul>
 *
 * <p><b>本地化取舍</b>：上游用 kotlinx.serialization 的 sealed class 多态序列化；
 * 本实现用 {@code type} 字段做判别（"text"/"reasoning"/"tool"/"image"），
 * 在 org.json 上手工读写，保持零第三方依赖。
 */
public final class AiMessage {

  //—— 角色（对齐上游 MessageRole）——
  public static final String ROLE_SYSTEM = "system";
  public static final String ROLE_USER = "user";
  public static final String ROLE_ASSISTANT = "assistant";
  public static final String ROLE_TOOL = "tool";

  //—— Part 类型（对齐上游 UIMessagePart 的 @SerialName）——
  public static final String PART_TEXT = "text";
  public static final String PART_REASONING = "reasoning";
  public static final String PART_TOOL = "tool";
  public static final String PART_IMAGE = "image";

  /** 消息唯一 id：流式重试按 id 覆盖（上游策略：固定消息 ID 覆盖分支） */
  public String id;
  public String role = ROLE_USER;
  /** 内容部件（有序）：思考过程通常在最前，正文在后 */
  public final List<Part> parts = new ArrayList<>();
  public long createdAt;
  public long finishedAt;
  /** 生成该消息的模型 id（上游 UIMessage.modelId） */
  public String modelId = "";
  /** token 用量（上游 UIMessage.usage） */
  public Usage usage;

  public AiMessage() {
    this.id = newId();
    this.createdAt = System.currentTimeMillis();
  }

  public AiMessage(String role, String text) {
    this();
    this.role = role;
    if (text != null && !text.isEmpty()) parts.add(Part.text(text));
  }

  public static String newId() {
    return "m" + System.currentTimeMillis() + "-" + Integer.toHexString((int) (Math.random() * 0xFFFFFF));
  }

  //—— 便捷读写（UI 层最常用）——

  /** 拼接全部 text 部件（对齐上游 UIMessage.toText()） */
  public String text() {
    StringBuilder sb = new StringBuilder();
    for (Part p : parts) if (PART_TEXT.equals(p.type) && p.text != null) sb.append(p.text);
    return sb.toString();
  }

  /** 拼接全部 reasoning 部件（对齐上游 UIMessage.reasoning） */
  public String reasoning() {
    StringBuilder sb = new StringBuilder();
    for (Part p : parts) if (PART_REASONING.equals(p.type) && p.text != null) sb.append(p.text);
    return sb.toString();
  }

  /** 是否含思考过程 */
  public boolean hasReasoning() {
    for (Part p : parts) if (PART_REASONING.equals(p.type) && p.text != null && !p.text.isEmpty()) return true;
    return false;
  }

  public Part firstTextPart() {
    for (Part p : parts) if (PART_TEXT.equals(p.type)) return p;
    return null;
  }

  /** 取（或创建）正文部件：流式追加用 */
  public Part ensureTextPart() {
    Part p = firstTextPart();
    if (p == null) {
      p = Part.text("");
      parts.add(p);
    }
    return p;
  }

  /** 取（或创建）思考部件 */
  public Part ensureReasoningPart() {
    for (Part p : parts) if (PART_REASONING.equals(p.type)) return p;
    Part p = Part.reasoning("");
    parts.add(0, p);// 思考过程排在最前（上游渲染顺序）
    return p;
  }

  /** 是否为空消息（无正文无思考无工具） */
  public boolean isEmpty() {
    for (Part p : parts) {
      if (PART_TEXT.equals(p.type) && p.text != null && !p.text.isEmpty()) return false;
      if (PART_REASONING.equals(p.type) && p.text != null && !p.text.isEmpty()) return false;
      if (PART_TOOL.equals(p.type)) return false;
    }
    return true;
  }

  public AiMessage copy() {
    AiMessage m = new AiMessage();
    m.id = id;
    m.role = role;
    m.createdAt = createdAt;
    m.finishedAt = finishedAt;
    m.modelId = modelId;
    m.usage = usage == null ? null : usage.copy();
    for (Part p : parts) m.parts.add(p.copy());
    return m;
  }

  //—— 序列化（对齐上游字段名，便于将来与上游格式互通）——

  public JSONObject toJson() {
    try {
      JSONObject o = new JSONObject();
      o.put("id", id);
      o.put("role", role);
      o.put("createdAt", createdAt);
      if (finishedAt > 0) o.put("finishedAt", finishedAt);
      if (!modelId.isEmpty()) o.put("modelId", modelId);
      if (usage != null) o.put("usage", usage.toJson());
      JSONArray arr = new JSONArray();
      for (Part p : parts) arr.put(p.toJson());
      o.put("parts", arr);
      return o;
    } catch (Exception e) {
      return new JSONObject();
    }
  }

  public static AiMessage fromJson(JSONObject o) {
    AiMessage m = new AiMessage();
    if (o == null) return m;
    m.id = o.optString("id", m.id);
    m.role = o.optString("role", ROLE_USER);
    m.createdAt = o.optLong("createdAt", m.createdAt);
    m.finishedAt = o.optLong("finishedAt", 0);
    m.modelId = o.optString("modelId", "");
    JSONObject u = o.optJSONObject("usage");
    if (u != null) m.usage = Usage.fromJson(u);
    JSONArray arr = o.optJSONArray("parts");
    if (arr != null) {
      for (int i = 0; i < arr.length(); i++) {
        Part p = Part.fromJson(arr.optJSONObject(i));
        if (p != null) m.parts.add(p);
      }
    } else {
      // 兼容旧格式（单一 content 字段）
      String legacy = o.optString("content", "");
      if (!legacy.isEmpty()) m.parts.add(Part.text(legacy));
    }
    return m;
  }

  /** 内容部件（对齐上游 UIMessagePart 的常用子类） */
  public static final class Part {
    public String type;
    public String text;
    /** 工具调用 id（PART_TOOL 用） */
    public String toolCallId = "";
    public String toolName = "";
    public String toolInput = "";
    public String toolOutput = "";
    /** 审批状态（对齐上游 ToolApprovalState：auto/pending/approved/denied/answered） */
    public String approvalState = APPROVAL_AUTO;

    public static final String APPROVAL_AUTO = "auto";
    public static final String APPROVAL_PENDING = "pending";
    public static final String APPROVAL_APPROVED = "approved";
    public static final String APPROVAL_DENIED = "denied";
    public static final String APPROVAL_ANSWERED = "answered";

    public static Part text(String value) {
      Part p = new Part();
      p.type = PART_TEXT;
      p.text = value == null ? "" : value;
      return p;
    }

    public static Part reasoning(String value) {
      Part p = new Part();
      p.type = PART_REASONING;
      p.text = value == null ? "" : value;
      return p;
    }

    public static Part tool(String callId, String name, String input) {
      Part p = new Part();
      p.type = PART_TOOL;
      p.toolCallId = callId == null ? "" : callId;
      p.toolName = name == null ? "" : name;
      p.toolInput = input == null ? "" : input;
      return p;
    }

    public Part copy() {
      Part p = new Part();
      p.type = type;
      p.text = text;
      p.toolCallId = toolCallId;
      p.toolName = toolName;
      p.toolInput = toolInput;
      p.toolOutput = toolOutput;
      p.approvalState = approvalState;
      return p;
    }

    public JSONObject toJson() {
      try {
        JSONObject o = new JSONObject();
        o.put("type", type);
        if (text != null) o.put("text", text);
        if (!toolCallId.isEmpty()) o.put("toolCallId", toolCallId);
        if (!toolName.isEmpty()) o.put("toolName", toolName);
        if (!toolInput.isEmpty()) o.put("toolInput", toolInput);
        if (!toolOutput.isEmpty()) o.put("toolOutput", toolOutput);
        if (PART_TOOL.equals(type)) o.put("approvalState", approvalState);
        return o;
      } catch (Exception e) {
        return new JSONObject();
      }
    }

    public static Part fromJson(JSONObject o) {
      if (o == null) return null;
      String type = o.optString("type", PART_TEXT);
      Part p = new Part();
      p.type = type;
      p.text = o.optString("text", "");
      p.toolCallId = o.optString("toolCallId", "");
      p.toolName = o.optString("toolName", "");
      p.toolInput = o.optString("toolInput", "");
      p.toolOutput = o.optString("toolOutput", "");
      p.approvalState = o.optString("approvalState", APPROVAL_AUTO);
      return p;
    }
  }

  /** token 用量（对齐上游 TokenUsage） */
  public static final class Usage {
    public int promptTokens;
    public int completionTokens;
    public int cachedTokens;
    public int totalTokens;

    public Usage copy() {
      Usage u = new Usage();
      u.promptTokens = promptTokens;
      u.completionTokens = completionTokens;
      u.cachedTokens = cachedTokens;
      u.totalTokens = totalTokens;
      return u;
    }

    public JSONObject toJson() {
      try {
        return new JSONObject().put("promptTokens", promptTokens).put("completionTokens", completionTokens)
            .put("cachedTokens", cachedTokens).put("totalTokens", totalTokens);
      } catch (Exception e) {
        return new JSONObject();
      }
    }

    public static Usage fromJson(JSONObject o) {
      Usage u = new Usage();
      if (o == null) return u;
      u.promptTokens = o.optInt("promptTokens", 0);
      u.completionTokens = o.optInt("completionTokens", 0);
      u.cachedTokens = o.optInt("cachedTokens", 0);
      u.totalTokens = o.optInt("totalTokens", 0);
      return u;
    }
  }
}
