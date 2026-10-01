package cc.nkbr.lanzouplus;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;

/** 主题引擎：设计 token 的唯一真源（单文件、零第三方依赖）。
 *  <p>v1.21.3 起仅一主题 legacy = 「原生安卓」经典紫（默认主题，也是唯一主题）；
 *  v1.21.5 清除「高级苹果」全部代码残留（弹窗/导航/LumaSwitch/状态栏的 apple 分支、isApple、GlassNavBar）。
 *  <p>所有色值经此表解析，MainActivity.applySystemColors() 只在启动/切换时从本引擎取值，
 *  其余 UI 组件一律通过 MainActivity 的 token 字段取色，保证一套色板贯穿全 App（含 ToolHost/AiChat）。 */
final class ThemeEngine {

  /** 单个主题的完整 token 表 */
  static final class Design {
    final String id, label, tagline;
    final int bg, surface, surface2, border, primary, primaryHi, primaryLo, secondary, text, muted, error;
    /** 浅色 chrome 标记（v1.4.0）：true = bg 为浅色，状态栏/导航栏要走深色字（LIGHT_STATUS_BAR） */
    final boolean bgIsLight;
    Design(String id,String label,String tagline,int bg,int surface,int surface2,int border,
           int primary,int primaryHi,int primaryLo,int secondary,int text,int muted,int error) {
      this(id,label,tagline,bg,surface,surface2,border,primary,primaryHi,primaryLo,secondary,text,muted,error,
          (Color.red(bg)+Color.green(bg)+Color.blue(bg))/3>127);
    }
    Design(String id,String label,String tagline,int bg,int surface,int surface2,int border,
           int primary,int primaryHi,int primaryLo,int secondary,int text,int muted,int error,boolean light){
      this.id=id;this.label=label;this.tagline=tagline;
      this.bg=bg;this.surface=surface;this.surface2=surface2;this.border=border;
      this.primary=primary;this.primaryHi=primaryHi;this.primaryLo=primaryLo;
      this.secondary=secondary;this.text=text;this.muted=muted;this.error=error;this.bgIsLight=light;
    }
    static int rgb(String hex) {
      try {return Color.parseColor(hex);}catch(Exception e){return 0xFFA78BFA;}
    }
  }

/** legacy：经典紫温（原 applySystemColors 原色值），默认主题。v1.19.7 底色改 OLED 真黑（wear-ui-system.md §5：Screen=#000000，表面阶梯更清晰且省电） */
/* 描边与表面必须分离（原缺陷：surface2 与 border **是同一个色值** #262332）。
   一个色既当"面"又当"描边"，结果就是**等于没有描边**：纯黑底上的卡片/胶囊是一片平的深灰紫，
   没有边缘定义、没有层次。两者职责不同 → 取值必然不同：
     · surface2 = **面**（填充），要在 §5 亮度阶梯的 Surface 档里；
     · border   = **描边**，必须比它所描的那个面**更亮**，否则描边看不见。
   修正① border #262332 → **#3A3548**：
     出处 `docs/archive/plan/20260926-long-term-execution/cards/T5-S-settings-redesign.md`
     §10.6 第 1 条「描边 #262332 → 提亮到 #3A3548 档（现 1.19:1 太糊，目标 ≥1.5:1）」，
     同文件 §10.5 自检清单亦写「描边 ≥1.5:1」。WCAG 2.2 公式本窗口复算：
     #262332 对纯黑 1.37:1、对卡面 #16141F 1.19:1（都不达标）；#3A3548 对纯黑 1.78:1、对 #16141F 1.54:1（达标）。
     注意 wear-ui-system.md §5 那句「普通描边 白 8–12%」换算到纯黑只有 1.08–1.26:1，达不到 ≥1.5:1，
     两份文档此处冲突，按"对比度硬指标优先"取 §10.6 的实测值（§5 的 8–12% 档仍用于带色表面的描边，
     见 PremiumSurface.STROKE_NORMAL，与本 token 不冲突）。
   修正② surface2 #262332 → **#1D1B26**：
     出处同卡 §10/§11 交叉裁决（§284「卡 #16141F → 内嵌面板 #1D1B26 档」），
     白叠加等效约 11–15%（含品牌紫偏），落在 §5「High 至 11–14%」的上档，是卡面之上的一档内嵌面板色。
     四层阶梯因此自洽且单调：页底 #000000 < 卡面 #16141F(1.15:1) < 面板 #1D1B26(1.24:1) < 描边 #3A3548(1.78:1)，
     卡面比页底亮 1.15:1 亦落在 §10.5 要求的 1.1–1.3:1 区间内。 */
static final Design LEGACY=new Design("legacy","原生安卓","系统默认 · 经典紫配色",
    0xFF000000,0xFF16141F,0xFF1D1B26,0xFF3A3548,
    0xFFA78BFA,0xFFC494FF,0xFF8B5CF6,0xFF8FB8F0,
    0xFFF2F0F7,0xFF9A93AB,0xFFFFB4AB);

// v1.21.3：「高级苹果」（apple，iOS 13–15 质感，v1.4.0–v1.21.2）整体移除；存量 apple 偏好在 activeId 读取时一次性迁回 legacy。

static final Design[] ALL={LEGACY};

/** 把品牌色（或任意色）按 alpha 合成半透明底，随主题自动联动（选中底/徽标底/气泡底等） */
static int tint(int color,int alpha){return Color.argb(alpha,Color.red(color),Color.green(color),Color.blue(color));}
/** 选中/激活态底色：按当前主题主色合成（质感=clay 暖橙底，legacy=紫温底），Text 对比达标（v1.3.0 计算化） */
static int selectedFill(int primary){return tint(primary,30);}

  private static final String PREF_FILE="ui_prefs_v1";
  private static final String KEY_THEME="theme";
  /** v1.3.1 一次性迁移标记：v1.3.0 把 nova 整体从旧紫黑换成了 Claude 陶土材质，且默认主题被误设为 nova；
   *  老用户升级后画面整体变色（应保持「原生安卓」经典紫）——首次运行迁回 legacy 并写标记，之后用户的手动选择不再被重置 */
  private static final String KEY_MIGRATED_131="migrated_131";
  private static volatile Design cache;

  static String activeId(Context c){
    Design hit=cache;
    if(hit!=null)return hit.id;
    SharedPreferences p=prefs(c);
    String id=p.getString(KEY_THEME,"legacy");
    if(!p.getBoolean(KEY_MIGRATED_131,false)){
      id="legacy";
      try{SharedPreferences.Editor e=p.edit();if(e!=null)e.putBoolean(KEY_MIGRATED_131,true).putString(KEY_THEME,id).apply();}catch(Throwable ignored){android.util.Log.w("ThemeEngine.java", "ThemeEngine.java Throwable: "+ignored.getMessage(), ignored);}
    }
    if("nova".equals(id)||"apple".equals(id)){// v1.5.0 删 nova、v1.21.3 删 apple 的删主题迁移：已不存在，落回 legacy 并写盘
      id="legacy";
      try{SharedPreferences.Editor e=p.edit();if(e!=null)e.putString(KEY_THEME,id).apply();}catch(Throwable ignored){android.util.Log.w("ThemeEngine.java", "ThemeEngine.java Throwable: "+ignored.getMessage(), ignored);}
    }
    cache=byId(id);
    return cache.id;
  }
  static void setActive(Context c,String id){
    cache=byId(id);
    prefs(c).edit().putString(KEY_THEME,cache.id).apply();
  }
  static String label(Context c){return byId(activeId(c)).label;}
  static String tagline(Context c){return byId(activeId(c)).tagline;}
  static boolean isLegacy(Context c){return "legacy".equals(activeId(c));}

  /** v1.5.0 删 nova、v1.21.3 删 apple：未知 id 一律落回 legacy */
  static Design byId(String id){for(Design d:ALL)if(d.id.equals(id))return d;return LEGACY;}
  static Design active(Context c){return byId(activeId(c));}
  /** 当前主题的选中态底色（v1.3.0 起按主色动态合成，避免硬编码紫） */
  static int selectedFill(Context c){return selectedFill(active(c).primary);}

  private static SharedPreferences prefs(Context c){return c.getSharedPreferences(PREF_FILE,Context.MODE_PRIVATE);}

  private ThemeEngine(){}
}