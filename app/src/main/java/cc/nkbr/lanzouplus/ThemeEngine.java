package cc.nkbr.lanzouplus;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;

/** 主题引擎：设计 token 的唯一真源（单文件、零第三方依赖）。
 *  <p>v1.5.0 起仅两主题（用户指令：删除「高级材质」nova）：
 *  <p>legacy = 「原生安卓」：经典紫配色（默认主题）。
 *  <p>apple  = 「高级苹果」：iOS 质感（分组白卡 + systemBlue + 快脆弹簧）。
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

/** legacy：经典紫温（原 applySystemColors 原色值），默认主题 */
static final Design LEGACY=new Design("legacy","原生安卓","系统默认 · 经典紫配色",
    0xFF0B0A12,0xFF16141F,0xFF262332,0xFF262332,
    0xFFA78BFA,0xFFC494FF,0xFF8B5CF6,0xFF8FB8F0,
    0xFFF2F0F7,0xFF9A93AB,0xFFFFB4AB);

/** apple：v1.4.0「高级苹果」——iOS 13–15 经典质感（非 iOS 26）：分组灰底白卡 + systemBlue + 分隔线描边。
 *  色值来源 iOS 官方资产目录（systemGroupedBackground/secondarySystemGroupedBackground/systemBlue/systemIndigo/
 *  opaqueSeparator/secondaryLabel 白底合成/systemRed），研究依据 07-研究报告/高级苹果主题与质感动效-深度研究汇总-v1.4.0.md §1.1。
 *  性格：浅色分组体系 + 无阴影分层 + 按压去 ripple（scale+变暗）+ 快脆弹簧。 */
static final Design APPLE=new Design("apple","高级苹果","iOS 质感 · 系统蓝 · 分组白卡",
    0xFFF2F2F7,0xFFFFFFFF,0xFFF2F2F7,0xFFC6C6C8,
    0xFF007AFF,0xFF0066D6,0xFFE5F0FF,0xFF5856D6,
    0xFF000000,0xFF8A8A8E,0xFFFF3B30);

static final Design[] ALL={LEGACY,APPLE};

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
      try{SharedPreferences.Editor e=p.edit();if(e!=null)e.putBoolean(KEY_MIGRATED_131,true).putString(KEY_THEME,id).apply();}catch(Throwable ignored){}
    }
    if("nova".equals(id)){// v1.5.0 删主题迁移：nova 已不存在，落回 legacy 并写盘
      id="legacy";
      try{SharedPreferences.Editor e=p.edit();if(e!=null)e.putString(KEY_THEME,id).apply();}catch(Throwable ignored){}
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
  /** 主题性格判断（v1.4.0 动效分支用）：apple=快脆弹簧去ripple；legacy=Material规整 */
  static boolean isApple(Context c){return "apple".equals(activeId(c));}
  static boolean isLegacy(Context c){return "legacy".equals(activeId(c));}

  /** v1.5.0：nova「高级材质」主题整体删除，存量用户的 nova 偏好一次性迁回 legacy */
  static Design byId(String id){for(Design d:ALL)if(d.id.equals(id))return d;return LEGACY;}
  static Design active(Context c){return byId(activeId(c));}
  /** 当前主题的选中态底色（v1.3.0 起按主色动态合成，避免硬编码紫） */
  static int selectedFill(Context c){return selectedFill(active(c).primary);}

  private static SharedPreferences prefs(Context c){return c.getSharedPreferences(PREF_FILE,Context.MODE_PRIVATE);}

  private ThemeEngine(){}
}