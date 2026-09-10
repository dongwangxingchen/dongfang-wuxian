package cc.nkbr.lanzouplus;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;

/** 主题引擎：设计 token 的唯一真源（单文件、零第三方依赖）。
 *  <p>nova   = v1.3.0 「高级材质」：Claude/Anthropic 暖陶土美学（暖灰阶 + clay 唯一主色 + 纸感 1dp 描边）；
 *  <p>legacy = 「原生安卓」：v1.2.3 经典紫配色（封存保留，可在设置-外观中切换）。
 *  <p>所有色值经此表解析，MainActivity.applySystemColors() 只在启动/切换时从本引擎取值，
 *  其余 UI 组件一律通过 MainActivity 的 token 字段取色，保证一套色板贯穿全 App（含 ToolHost/AiChat）。 */
final class ThemeEngine {

  /** 单个主题的完整 token 表 */
  static final class Design {
    final String id, label, tagline;
    final int bg, surface, surface2, border, primary, primaryHi, primaryLo, secondary, text, muted, error;
    Design(String id,String label,String tagline,int bg,int surface,int surface2,int border,
           int primary,int primaryHi,int primaryLo,int secondary,int text,int muted,int error) {
      this.id=id;this.label=label;this.tagline=tagline;
      this.bg=bg;this.surface=surface;this.surface2=surface2;this.border=border;
      this.primary=primary;this.primaryHi=primaryHi;this.primaryLo=primaryLo;
      this.secondary=secondary;this.text=text;this.muted=muted;this.error=error;
    }
    static int rgb(String hex) {
      try {return Color.parseColor(hex);}catch(Exception e){return 0xFFA78BFA;}
    }
  }

/** nova：边框驱动 + 双面浮层 + 次级强调（计划 08 定稿，对比度全部 ≥4.5:1） 
 *  v1.3.0 重做：Claude/Anthropic 官方暗色（claude.com gray 色阶 + clay 唯一主色 + 低饱和状态色，
 *  研究归档 01-研究资料/09-1-anthropic-claude美学.md R1–R22、09-2 定稿） */
static final Design NOVA=new Design("nova","高级材质","Claude 暖灰 · 陶土橙 · 纸感描边",
  0xFF141413,0xFF1D1C1A,0xFF26241F,0xFF2E2C28,
  0xFFD97757,0xFFE89A86,0xFFC6613F,0xFF61AAF2,
  0xFFEAE7DF,0xFFA9A39A,0xFFD47563);

/** legacy：v1.2.3 及以前的紫温经典（原 applySystemColors 原色值），封存可切换 */
static final Design LEGACY=new Design("legacy","原生安卓","系统默认 · 经典紫配色",
    0xFF0B0A12,0xFF16141F,0xFF262332,0xFF262332,
    0xFFA78BFA,0xFFC494FF,0xFF8B5CF6,0xFF8FB8F0,
    0xFFF2F0F7,0xFF9A93AB,0xFFFFB4AB);

static final Design[] ALL={NOVA,LEGACY};

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
    cache=byId(id);
    return cache.id;
  }
  static void setActive(Context c,String id){
    cache=byId(id);
    prefs(c).edit().putString(KEY_THEME,cache.id).apply();
  }
  static String label(Context c){return byId(activeId(c)).label;}
  static String tagline(Context c){return byId(activeId(c)).tagline;}

  static Design byId(String id){for(Design d:ALL)if(d.id.equals(id))return d;return NOVA;}
  static Design active(Context c){return byId(activeId(c));}
  /** 当前主题的选中态底色（v1.3.0 起按主色动态合成，避免硬编码紫） */
  static int selectedFill(Context c){return selectedFill(active(c).primary);}

  private static SharedPreferences prefs(Context c){return c.getSharedPreferences(PREF_FILE,Context.MODE_PRIVATE);}

  private ThemeEngine(){}
}