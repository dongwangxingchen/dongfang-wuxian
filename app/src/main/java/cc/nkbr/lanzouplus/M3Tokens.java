package cc.nkbr.lanzouplus;

/**
 * [DFWX] DFW-67：**Material Design 3 官方 token**（手写常量表，零依赖）。
 *
 * ## 这些值从哪来（不凭记忆）
 * - **颜色**：直接采用仓库里**已存在**的 M3 角色色板
 *   `rikkahub/.../ui/theme/presets/DongfangTheme.kt` 的 `dongfangDarkScheme`。
 *   那套色是项目为「东方无限」定制过、且 AI 页（Compose）已经在跑的——所以主机区采用它，
 *   等于让 App 的两半**第一次用同一套色**。**不是我新编的颜色。**
 * - **形状阶**：从项目实际依赖的 `androidx.compose.material3:material3-android:1.5.0-alpha29`
 *   的 `ShapeTokens` 类里反编译出来的常量（`javap` 读 `<clinit>`），比查文档权威——
 *   因为这就是我们 APK 里跑的那一版。
 * - **字阶**：M3 官方 15 个 type role 的 size / lineHeight / weight。
 *   已用同一个 aar 的 `TypeScaleTokens` 交叉核对过**行高**序列（64/52/44/40/36/32/28/24/20/24/20/16/20/16/16）完全吻合。
 *
 * ## 为什么不直接引 com.google.android.material
 * 调研结论（参考库 `06/07` 专档 D 节）：它**不是加一个包**那么简单——
 * 会带 **18 个 compile 传递依赖**（appcompat / coordinatorlayout / constraintlayout / recyclerview…），
 * 而本项目 `MainActivity extends androidx.activity.ComponentActivity`（非 AppCompat）、
 * `AppTheme` 是自绘主题 → 引它等于**换掉全 App 的主题基座**。
 * 所以本类走"手写 token 表"路线，与项目"零第三方依赖"铁律一致。
 *
 * ## 当前状态
 * 这是**试衣间专用**的并行 token 表，**尚未接管全站**。
 * 用户要先看效果再决定是否全量重置（见 docs/plan/decisions.md）。
 */
final class M3Tokens {

  private M3Tokens() {}

  // ── 颜色角色（M3 Color Roles，取 DongfangTheme 的 dark scheme）────────────────

  static final int PRIMARY = 0xFFA78BFA;
  static final int ON_PRIMARY = 0xFF221A3D;
  static final int PRIMARY_CONTAINER = 0xFF3B2E63;
  static final int ON_PRIMARY_CONTAINER = 0xFFE9DEFB;

  static final int SECONDARY = 0xFFC9C2DA;
  static final int ON_SECONDARY = 0xFF322C44;
  static final int SECONDARY_CONTAINER = 0xFF3A3450;
  static final int ON_SECONDARY_CONTAINER = 0xFFE6DFF5;

  static final int TERTIARY = 0xFF8FD0C6;
  static final int ON_TERTIARY = 0xFF003736;
  static final int TERTIARY_CONTAINER = 0xFF1F4E50;
  static final int ON_TERTIARY_CONTAINER = 0xFFBFEAEF;

  static final int ERROR = 0xFFFFB4AB;
  static final int ON_ERROR = 0xFF690005;
  static final int ERROR_CONTAINER = 0xFF93000A;
  static final int ON_ERROR_CONTAINER = 0xFFFFDAD6;

  static final int BACKGROUND = 0xFF000000;
  static final int ON_BACKGROUND = 0xFFF2F0F7;
  static final int SURFACE = 0xFF000000;
  static final int ON_SURFACE = 0xFFF2F0F7;
  static final int SURFACE_VARIANT = 0xFF262332;
  static final int ON_SURFACE_VARIANT = 0xFF9A93AB;

  static final int OUTLINE = 0xFF4A4460;
  static final int OUTLINE_VARIANT = 0xFF262332;
  static final int SCRIM = 0xFF000000;

  static final int INVERSE_SURFACE = 0xFFF2F0F7;
  static final int INVERSE_ON_SURFACE = 0xFF000000;
  static final int INVERSE_PRIMARY = 0xFF6B4FD8;

  /** surface 容器阶梯：M3 用**色调叠加**表达层级，而不是阴影（纯黑下阴影本来就看不见）。 */
  static final int SURFACE_DIM = 0xFF000000;
  static final int SURFACE_BRIGHT = 0xFF3A3550;
  static final int SURFACE_CONTAINER_LOWEST = 0xFF08070E;
  static final int SURFACE_CONTAINER_LOW = 0xFF16141F;
  static final int SURFACE_CONTAINER = 0xFF1C1928;
  static final int SURFACE_CONTAINER_HIGH = 0xFF242032;
  static final int SURFACE_CONTAINER_HIGHEST = 0xFF2C2840;

  // ── 形状阶（官方 ShapeTokens 反编译值）────────────────────────────────────

  static final int SHAPE_NONE = 0;
  static final int SHAPE_EXTRA_SMALL = 4;
  static final int SHAPE_SMALL = 8;
  static final int SHAPE_MEDIUM = 12;
  static final int SHAPE_LARGE = 16;
  static final int SHAPE_LARGE_INCREASED = 20;
  static final int SHAPE_EXTRA_LARGE = 28;
  static final int SHAPE_EXTRA_LARGE_INCREASED = 32;
  static final int SHAPE_EXTRA_EXTRA_LARGE = 48;

  /** 组件默认用的形状档（官方组件规格）。 */
  static final int SHAPE_BUTTON = SHAPE_EXTRA_LARGE;      // 按钮 = 全圆（pill），用大值近似
  static final int SHAPE_CARD = SHAPE_MEDIUM;            // 卡片 12dp
  static final int SHAPE_DIALOG = SHAPE_EXTRA_LARGE;     // 对话框 28dp
  static final int SHAPE_LIST_ITEM = SHAPE_NONE;
  static final int SHAPE_CHIP = SHAPE_SMALL;             // 芯片 8dp
  static final int SHAPE_FAB = SHAPE_LARGE;              // FAB 16dp
  static final int SHAPE_TEXT_FIELD = SHAPE_EXTRA_SMALL; // 输入框 4dp
  static final int SHAPE_BOTTOM_SHEET = SHAPE_EXTRA_LARGE; // 底部面板 28dp（顶部两角）

  // ── 字阶（M3 Type Scale，15 个 role）──────────────────────────────────────

  /** 一个 type role 的完整规格。 */
  static final class TypeRole {
    final String name;
    final float sizeSp, lineHeightSp, trackingEm;
    final int weight;
    TypeRole(String name, float sizeSp, float lineHeightSp, int weight, float trackingEm) {
      this.name = name; this.sizeSp = sizeSp; this.lineHeightSp = lineHeightSp;
      this.weight = weight; this.trackingEm = trackingEm;
    }
  }

  static final TypeRole DISPLAY_LARGE = new TypeRole("Display Large", 57, 64, 400, -0.25f);
  static final TypeRole DISPLAY_MEDIUM = new TypeRole("Display Medium", 45, 52, 400, 0f);
  static final TypeRole DISPLAY_SMALL = new TypeRole("Display Small", 36, 44, 400, 0f);

  static final TypeRole HEADLINE_LARGE = new TypeRole("Headline Large", 32, 40, 400, 0f);
  static final TypeRole HEADLINE_MEDIUM = new TypeRole("Headline Medium", 28, 36, 400, 0f);
  static final TypeRole HEADLINE_SMALL = new TypeRole("Headline Small", 24, 32, 400, 0f);

  static final TypeRole TITLE_LARGE = new TypeRole("Title Large", 22, 28, 400, 0f);
  static final TypeRole TITLE_MEDIUM = new TypeRole("Title Medium", 16, 24, 500, 0.15f);
  static final TypeRole TITLE_SMALL = new TypeRole("Title Small", 14, 20, 500, 0.1f);

  static final TypeRole BODY_LARGE = new TypeRole("Body Large", 16, 24, 400, 0.5f);
  static final TypeRole BODY_MEDIUM = new TypeRole("Body Medium", 14, 20, 400, 0.25f);
  static final TypeRole BODY_SMALL = new TypeRole("Body Small", 12, 16, 400, 0.4f);

  static final TypeRole LABEL_LARGE = new TypeRole("Label Large", 14, 20, 500, 0.1f);
  static final TypeRole LABEL_MEDIUM = new TypeRole("Label Medium", 12, 16, 500, 0.5f);
  static final TypeRole LABEL_SMALL = new TypeRole("Label Small", 11, 16, 500, 0.5f);

  static final TypeRole[] TYPE_SCALE = {
      DISPLAY_LARGE, DISPLAY_MEDIUM, DISPLAY_SMALL,
      HEADLINE_LARGE, HEADLINE_MEDIUM, HEADLINE_SMALL,
      TITLE_LARGE, TITLE_MEDIUM, TITLE_SMALL,
      BODY_LARGE, BODY_MEDIUM, BODY_SMALL,
      LABEL_LARGE, LABEL_MEDIUM, LABEL_SMALL,
  };

  // ── 状态层（State Layer）不透明度 ─────────────────────────────────────────

  /** M3 用**叠加一层半透明前景色**表达交互状态，而不是换背景色。 */
  static final float STATE_HOVER = 0.08f;
  static final float STATE_FOCUS = 0.10f;
  static final float STATE_PRESSED = 0.10f;
  static final float STATE_DRAGGED = 0.16f;
  static final float STATE_DISABLED_CONTAINER = 0.12f;
  static final float STATE_DISABLED_CONTENT = 0.38f;

  /** 遮罩（scrim）不透明度。 */
  static final float SCRIM_ALPHA = 0.32f;

  // ── 组件尺寸（官方规格）──────────────────────────────────────────────────

  static final int BUTTON_HEIGHT = 40;
  static final int BUTTON_PADDING_H = 24;
  static final int BUTTON_ICON_GAP = 8;
  static final int CARD_PADDING = 16;
  static final int LIST_ITEM_HEIGHT = 56;
  static final int LIST_ITEM_PADDING_H = 16;
  static final int CHIP_HEIGHT = 32;
  static final int CHIP_PADDING_H = 16;
  static final int FAB_SIZE = 56;
  static final int TOP_APP_BAR_HEIGHT = 64;
  static final int NAV_BAR_HEIGHT = 80;
  static final int NAV_ITEM_ICON_BOX = 64;
  static final int NAV_INDICATOR_WIDTH = 64;
  static final int NAV_INDICATOR_HEIGHT = 32;
  static final int TEXT_FIELD_HEIGHT = 56;
  static final int SWITCH_TRACK_WIDTH = 52;
  static final int SWITCH_TRACK_HEIGHT = 32;
  static final int SWITCH_THUMB = 24;

  // ── 动效（沿用项目规范 §2，与 M3 一致的部分）──────────────────────────────

  static final long DURATION_SHORT_4 = 200;
  static final long DURATION_MEDIUM_2 = 300;
  static final long DURATION_LONG_2 = 500;

  /** M3 standard 缓动 `(0.2, 0, 0, 1)`。 */
  static final float[] EASING_STANDARD = {0.2f, 0f, 0f, 1f};
  /** M3 emphasized 减速段（入场用）。 */
  static final float[] EASING_EMPHASIZED_DECELERATE = {0.05f, 0.7f, 0.1f, 1f};
}
