package cc.nkbr.lanzouplus;

import me.rerere.rikkahub.dfwx.DfwxBuiltinChannel;

// 说明：DfwxBuiltinChannel 是 Kotlin `object`，Java 侧没有静态方法，必须走它的单例 INSTANCE。

/**
 * [DFW-73] 内置渠道（服务端中转）在宿主机侧的注入点。
 *
 * ## 为什么要有这个类
 * 上游地址、应用令牌、模型名、最大输出写在 `app/build.gradle.kts` 的 `resValue("string","dfwx_ai_*")`，
 * 而播种逻辑在 vendor 区 `rikkahub` 模块（库模块**不能**反向引用宿主 `cc.nkbr.lanzouplus` 的类）。
 * 所以由宿主在这里读自己的资源、推给 `DfwxBuiltinChannel`，vendor 侧保持零宿主依赖。
 *
 * ## 用户拍板的安全边界（2026-10-01）
 * > "你不用给他记次数……你只需要做好防止被别人抓包的就行" / "防君子就行了，靠诚信就行"
 *
 * 所以：**不做配额、不做计数**。APK 里只有我们自己的服务器地址 + 一个应用令牌，
 * 上游中转站地址与真实 Key 一个字节都不进客户端（只在服务器 `/etc/nginx/dfwx-ai-secret.conf`，600）。
 * 令牌可被扒是已知且接受的代价；真被白用就换服务器令牌，不必发版。
 *
 * 必须由 {@link App#onCreate()} 在 `super.onCreate()` **之前**调用——RikkaHub 的 Application.onCreate
 * 会立刻触发渠道同步，晚一步这一轮就同步不到配置。
 */
final class BuiltinAiChannel {
  /** APK 内置值（resValue），后台留空的字段一律回落到这里。 */
  private static volatile String bakedUrl, bakedToken, bakedModel;
  private static volatile int bakedMaxTokens;

  private BuiltinAiChannel() {}

  /** 读资源并注入。任何异常都不允许影响启动（内置渠道坏了不能连累主功能）。 */
  static void install(android.content.Context context) {
    try {
      String url = context.getString(R.string.dfwx_ai_url).trim();
      String token = context.getString(R.string.dfwx_ai_token).trim();
      String model = context.getString(R.string.dfwx_ai_model).trim();
      int maxTokens = Integer.parseInt(context.getString(R.string.dfwx_ai_max_tokens).trim());
      bakedUrl = url;
      bakedToken = token;
      bakedModel = model;
      bakedMaxTokens = maxTokens;
      push(url, token, model, maxTokens, true);
      // 付费门：读的是实时状态（Support.unlocked 每次现读 SharedPreferences），
      // 所以用户刚在诚信付费页解锁，回 AI 页就已经放行，不需要重启。
      DfwxBuiltinChannel.INSTANCE.setPaidProvider(() -> Support.unlocked(context));
    } catch (Throwable t) {
      android.util.Log.w("DfwxBuiltinAi", "install builtin channel failed: " + t.getMessage(), t);
    }
  }

  /**
   * [DFW-73] 后台下发的渠道覆盖（`control` 行的 ai_base_url / ai_token / ai_model / ai_max_tokens / ai_disabled）。
   *
   * 全部**留空即沿用 APK 内置值**（fail-open）：后台字段没建、或误填成空，都不会把内置渠道弄坏。
   * 换服务器地址、换应用令牌、换模型名、调最大输出，都改后台就行，用户不用更新 APK。
   */
  static void applyRemote(RemoteConfigClient.Control control) {
    if (control == null || bakedUrl == null) return;
    try {
      String url = control.aiBaseUrl.isEmpty() ? bakedUrl : control.aiBaseUrl;
      String token = control.aiToken.isEmpty() ? bakedToken : control.aiToken;
      String model = control.aiModel.isEmpty() ? bakedModel : control.aiModel;
      int maxTokens = control.aiMaxTokens > 0 ? control.aiMaxTokens : bakedMaxTokens;
      // 本次启动已同步过的渠道按新配置重播一次；配置没变时 syncIfNeeded 内部会因为"无差异"直接返回。
      boolean changed = !url.equals(DfwxBuiltinChannel.INSTANCE.current().getBaseUrl())
          || !token.equals(DfwxBuiltinChannel.INSTANCE.current().getToken())
          || !model.equals(DfwxBuiltinChannel.INSTANCE.current().getModelId())
          || maxTokens != DfwxBuiltinChannel.INSTANCE.current().getMaxTokens()
          || control.aiDisabled == DfwxBuiltinChannel.INSTANCE.current().getEnabled();
      if (!changed) return;
      push(url, token, model, maxTokens, !control.aiDisabled);
      DfwxBuiltinChannel.INSTANCE.syncNow();
      android.util.Log.i("DfwxBuiltinAi", "builtin channel overridden by backend (disabled=" + control.aiDisabled + ")");
    } catch (Throwable t) {
      android.util.Log.w("DfwxBuiltinAi", "applyRemote failed: " + t.getMessage(), t);
    }
  }

  private static void push(String url, String token, String model, int maxTokens, boolean enabled) {
    boolean usable = enabled && !url.isEmpty() && !token.isEmpty() && !model.isEmpty() && maxTokens > 0;
    DfwxBuiltinChannel.INSTANCE.install(new DfwxBuiltinChannel.Config(url, token, model, maxTokens, usable));
    if (!usable) android.util.Log.w("DfwxBuiltinAi", "builtin channel config incomplete, disabled");
  }
}
