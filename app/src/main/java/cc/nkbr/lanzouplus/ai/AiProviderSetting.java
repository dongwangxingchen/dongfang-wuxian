package cc.nkbr.lanzouplus.ai;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 供应商设置（移植自 RikkaHub ai/src/main/java/me/rerere/ai/provider/ProviderSetting.kt）。
 * 上游对照：ProviderSetting.OpenAI 子类（OpenAI 兼容型）。
 * 上游更新时对照该文件同步字段名与逻辑。
 *
 * 忠实对齐上游的字段（与 RikkaHub 逐字段对应）：
 * - id: Uuid -> id (String)
 * - enabled: Boolean -> enabled
 * - name: String -> name
 * - apiKey: String -> apiKey
 * - baseUrl: String -> baseUrl
 * - chatCompletionsPath: String -> chatCompletionsPath
 * - balanceOption: BalanceOption -> balanceEnabled / balanceApiPath / balanceResultPath
 * - models: List of Model -> models (JSON 数组)
 *
 * 本地化取舍：上游用 sealed class（OpenAI/Google/Claude）+ Room；
 * 本实现统一为 OpenAI 兼容型（只对接中转站），SharedPreferences JSON 存储。
 * 相对旧 AiChatCore.Settings 新增：enabled / balanceEnabled / balanceApiPath / balanceResultPath / chatCompletionsPath。
 * 桥接逻辑（AiProviderSetting <-> AiChatCore.Settings）由 MainActivity 中的转换方法处理。
 */
public final class AiProviderSetting {

  // 公共字段（对齐上游 ProviderSetting 基类）
  public String id = "";
  public boolean enabled = true;
  public String name = "OpenAI";
  public List<String> models = new ArrayList<>();

  // OpenAI 兼容型字段（对齐上游 ProviderSetting.OpenAI）
  public String apiKey = "";
  public String baseUrl = "https://api.openai.com/v1";
  /** 补全路径（上游 chatCompletionsPath，默认 /chat/completions） */
  public String chatCompletionsPath = "/chat/completions";

  // 余额查询（对齐上游 BalanceOption）
  public boolean balanceEnabled = false;
  public String balanceApiPath = "/credits";
  public String balanceResultPath = "data.total_usage";

  public AiProviderSetting() {}

  public AiProviderSetting(String id, String name, String url, String key) {
    this.id = id;
    this.name = name;
    this.baseUrl = url;
    this.apiKey = key;
  }

  public JSONObject toJson() {
    try {
      JSONObject o = new JSONObject();
      o.put("id", id);
      o.put("enabled", enabled);
      o.put("name", name);
      o.put("apiKey", apiKey);
      o.put("baseUrl", baseUrl);
      o.put("chatCompletionsPath", chatCompletionsPath);
      o.put("balanceEnabled", balanceEnabled);
      o.put("balanceApiPath", balanceApiPath);
      o.put("balanceResultPath", balanceResultPath);
      JSONArray arr = new JSONArray();
      for (String m : models) arr.put(m);
      o.put("models", arr);
      return o;
    } catch (Exception e) {
      return new JSONObject();
    }
  }

  public static AiProviderSetting fromJson(JSONObject o) {
    AiProviderSetting p = new AiProviderSetting();
    if (o == null) return p;
    p.id = o.optString("id", "");
    p.enabled = o.optBoolean("enabled", true);
    p.name = o.optString("name", "OpenAI");
    p.apiKey = o.optString("apiKey", "");
    p.baseUrl = o.optString("baseUrl", "");
    p.chatCompletionsPath = o.optString("chatCompletionsPath", "/chat/completions");
    p.balanceEnabled = o.optBoolean("balanceEnabled", false);
    p.balanceApiPath = o.optString("balanceApiPath", "/credits");
    p.balanceResultPath = o.optString("balanceResultPath", "data.total_usage");
    JSONArray arr = o.optJSONArray("models");
    if (arr != null) {
      for (int i = 0; i < arr.length(); i++) {
        String m = arr.optString(i, "");
        if (!m.isEmpty()) p.models.add(m);
      }
    }
    return p;
  }
}
