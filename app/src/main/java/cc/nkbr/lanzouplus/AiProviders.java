package cc.nkbr.lanzouplus;

import java.util.Locale;
/** AI 服务商预制目录（v1.2.2）：纯数据，UI 在 MainActivity。baseURL 允许自带版本段（/v1 /v3 /v4），由 AiChatCore.normalizeBase 保护不再追加。 */
final class AiProviders {
  static final class Provider {
    final String id, name, url, hint;
    final String[] models;
    Provider(String id, String name, String url, String hint, String[] models) {this.id=id;this.name=name;this.url=url;this.hint=hint;this.models=models;}
  }
  static final Provider[] ALL = {
    new Provider("deepseek", "DeepSeek", "https://api.deepseek.com", "到 platform.deepseek.com 创建 API Key", new String[]{"deepseek-chat", "deepseek-reasoner"}),
    new Provider("zhipu", "智谱 GLM", "https://open.bigmodel.cn/api/paas/v4", "到 open.bigmodel.cn 领取免费的 glm-4-flash", new String[]{"glm-4-flash", "glm-4-air", "glm-4-plus"}),
    new Provider("kimi", "Kimi", "https://api.moonshot.cn", "到 platform.moonshot.cn 创建 API Key", new String[]{"kimi-latest", "moonshot-v1-8k", "moonshot-v1-32k"}),
    new Provider("qwen", "通义千问", "https://dashscope.aliyuncs.com/compatible-mode/v1", "到 bailian.console.aliyun.com 开通并创建 Key", new String[]{"qwen-turbo", "qwen-plus", "qwen-max"}),
    new Provider("doubao", "豆包", "https://ark.cn-beijing.volces.com/api/v3", "到火山方舟创建推理接入点，模型填接入点 ID", new String[]{"doubao-lite-32k", "doubao-pro-32k"}),
    new Provider("siliconflow", "硅基流动", "https://api.siliconflow.cn/v1", "到 siliconflow.cn 注册即送额度", new String[]{"deepseek-ai/DeepSeek-V3", "Qwen/Qwen2.5-72B-Instruct"}),
    new Provider("openai", "OpenAI", "https://api.openai.com/v1", "到 platform.openai.com 创建 API Key", new String[]{"gpt-4o-mini", "gpt-4o"}),
    new Provider("openrouter", "OpenRouter", "https://openrouter.ai/api/v1", "到 openrouter.ai 一个 Key 聚合多家模型", new String[]{"openrouter/auto"}),
    new Provider("custom", "自定义", "", "任意 OpenAI 兼容地址，缺省自动补 /v1", new String[]{}),
  };
  static Provider byId(String id) {
    if (id == null || id.isEmpty()) return ALL[ALL.length - 1];
    for (Provider p : ALL) if (p.id.equals(id)) return p;
    return ALL[ALL.length - 1];
  }
  static String nameOf(String id) {return byId(id).name;}
}
