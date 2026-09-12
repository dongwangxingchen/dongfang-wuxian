package cc.nkbr.lanzouplus.ai;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 助手（人格）模型（移植自 RikkaHub {@code app/src/main/java/me/rerere/rikkahub/data/model/Assistant.kt}）。
 *
 * <p><b>上游对照</b>：{@code Assistant.kt}。上游更新时对照该文件同步字段。
 *
 * <p><b>核心设计（忠实对齐上游，这是它体验的关键）</b>：
 * <ul>
 *   <li><b>"可空=跟随"双层语义</b>：{@code temperature}/{@code topP}/{@code maxTokens}/{@code chatModelId}
 *       为 {@code null} 表示"继承全局/不覆盖"，有值表示"助手级覆盖"。
 *       这样助手既能定制参数，又不会与全局设置互相污染——上游原文注释：
 *       "如果为null, 使用全局默认模型"。</li>
 *   <li>{@code contextMessageLimit}：上下文消息条数上限，超出后阶梯式截断（配合 {@link ContextLimiter}）；0 表示不限制。</li>
 *   <li>{@code streamOutput}：是否流式输出，默认 true。</li>
 *   <li>{@code systemPrompt}：人格提示词，支持模板变量（{{ message }} 等）。</li>
 * </ul>
 *
 * <p><b>本地化取舍</b>：上游有 30+ 字段（含 MCP、Lorebook、workspace、regexes 等我们暂不实现的高级能力）。
 * 本实现保留对当前功能有意义的字段；未实现字段在注释中标注「上游有，本版未实现」，便于后续按需补齐。
 * 用 Double 包装类型表达"可空"语义（Java 无 Kotlin 的 nullable 语法糖）。
 */
public final class AiAssistant {

  /** 助手 id（上游用 Uuid） */
  public String id = "";
  /** 指定模型 id；null = 跟随全局默认模型（上游 chatModelId: Uuid?） */
  public String chatModelId = null;
  /** 助手名称 */
  public String name = "";
  /** 人格提示词（上游 systemPrompt） */
  public String systemPrompt = "";

  //—— 生成参数（null = 不覆盖，跟随全局）——
  /** 采样温度 0~2；null = 不覆盖 */
  public Double temperature = null;
  /** 核采样 topP 0~1；null = 不覆盖 */
  public Double topP = null;
  /** 最大输出 tokens；null = 不覆盖 */
  public Integer maxTokens = null;
  /** 上下文消息条数上限，0 = 不限制（上游 contextMessageLimit） */
  public int contextMessageLimit = 0;
  /** 是否流式输出（上游 streamOutput，默认 true） */
  public boolean streamOutput = true;

  //—— 上游有、本版未实现（保留占位便于后续补齐）——
  // enableMemory / useGlobalMemory / enableRecentChatsReference / messageTemplate /
  // presetMessages / quickMessageIds / regexes / reasoningLevel / customHeaders /
  // customBodies / mcpServers / localTools / enableWebSearch / workspaceId /
  // background / backgroundOpacity / useGradientBackground / modeInjectionIds /
  // lorebookIds / enabledSkills / enableTimeReminder / timeReminderIntervalMinutes /
  // allowConversationSystemPrompt / allowConversationPromptInjection

  public AiAssistant() {
    this.id = "a" + System.currentTimeMillis() + "-" + Integer.toHexString((int) (Math.random() * 0xFFFF));
  }

  public AiAssistant copy() {
    AiAssistant a = new AiAssistant();
    a.id = id;
    a.chatModelId = chatModelId;
    a.name = name;
    a.systemPrompt = systemPrompt;
    a.temperature = temperature;
    a.topP = topP;
    a.maxTokens = maxTokens;
    a.contextMessageLimit = contextMessageLimit;
    a.streamOutput = streamOutput;
    return a;
  }

  /** 显示名（空名回退，对齐上游"空名默认助手"） */
  public String displayName() {
    return name == null || name.trim().isEmpty() ? "默认助手" : name.trim();
  }

  public JSONObject toJson() {
    try {
      JSONObject o = new JSONObject();
      o.put("id", id);
      o.put("name", name);
      o.put("systemPrompt", systemPrompt);
      if (chatModelId != null) o.put("chatModelId", chatModelId);
      if (temperature != null) o.put("temperature", temperature.doubleValue());
      if (topP != null) o.put("topP", topP.doubleValue());
      if (maxTokens != null) o.put("maxTokens", maxTokens.intValue());
      o.put("contextMessageLimit", contextMessageLimit);
      o.put("streamOutput", streamOutput);
      return o;
    } catch (Exception e) {
      return new JSONObject();
    }
  }

  public static AiAssistant fromJson(JSONObject o) {
    AiAssistant a = new AiAssistant();
    if (o == null) return a;
    a.id = o.optString("id", a.id);
    a.name = o.optString("name", "");
    a.systemPrompt = o.optString("systemPrompt", "");
    // "可空=跟随"：用 has() 区分「字段缺失/null」与「显式值」
    if (o.has("chatModelId") && !o.isNull("chatModelId")) a.chatModelId = o.optString("chatModelId");
    if (o.has("temperature") && !o.isNull("temperature")) a.temperature = o.optDouble("temperature");
    if (o.has("topP") && !o.isNull("topP")) a.topP = o.optDouble("topP");
    if (o.has("maxTokens") && !o.isNull("maxTokens")) a.maxTokens = o.optInt("maxTokens");
    a.contextMessageLimit = o.optInt("contextMessageLimit", 0);
    a.streamOutput = o.optBoolean("streamOutput", true);
    return a;
  }

  /**
   * 预置助手（对齐上游"预置助手 2 个：空名默认助手 + helpful assistant"）。
   * 首次使用时写入，启动时若列表为空则补回。
   */
  public static List<AiAssistant> defaults() {
    List<AiAssistant> list = new ArrayList<>();
    AiAssistant plain = new AiAssistant();
    plain.name = "默认助手";
    plain.systemPrompt = "";
    list.add(plain);
    AiAssistant helpful = new AiAssistant();
    helpful.name = "万能助手";
    helpful.systemPrompt = "你是一个乐于助人的助手。回答简洁、准确、友好，必要时分点说明。";
    list.add(helpful);
    return list;
  }

  public static JSONArray listToJson(List<AiAssistant> list) {
    JSONArray arr = new JSONArray();
    for (AiAssistant a : list) arr.put(a.toJson());
    return arr;
  }

  public static List<AiAssistant> listFromJson(JSONArray arr) {
    List<AiAssistant> list = new ArrayList<>();
    if (arr == null) return list;
    for (int i = 0; i < arr.length(); i++) {
      JSONObject o = arr.optJSONObject(i);
      if (o != null) list.add(fromJson(o));
    }
    return list;
  }
}
