package cc.nkbr.lanzouplus;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;

/** 主题引擎：设计 token 的唯一真源（单文件、零第三方依赖）。
 *  <p>nova   = v1.2.4 新默认主题：细边框驱动层次 + 双面浮层 + 次级强调色（Claude Code / 终端美学，B 阶段定稿）；
 *  <p>legacy = v1.2.3 之前的紫温经典主题（封存保留，可在设置-外观中切换）。
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

  /** nova：边框驱动 + 双面浮层 + 次级强调（计划 08 定稿，对比度全部 ≥4.5:1） */
  static final Design NOVA=new Design("nova","nova 新主题","细边框 · 双面浮层 · 低饱和强调",
    0xFF0F0D16,0xFF16141F,0xFF1E1B2A,0xFF2B2736,
    0xFFA78BFA,0xFFC494FF,0xFF8B5CF6,0xFF8FB8F0,
    0xFFEDEBF6,0xFF9B96AD,0xFFF07183);

  /** legacy：v1.2.3 及以前的紫温经典（原 applySystemColors 原色值），封存可切换 */
  static final Design LEGACY=new Design("legacy","经典紫温","v1.2.3 经典配色",
      0xFF0B0A12,0xFF16141F,0xFF262332,0xFF262332,
      0xFFA78BFA,0xFFC494FF,0xFF8B5CF6,0xFF8FB8F0,
      0xFFF2F0F7,0xFF9A93AB,0xFFFFB4AB);

  static final Design[] ALL={NOVA,LEGACY};

  /** 把品牌色（或任意色）按 alpha 合成半透明底，随主题自动联动（选中底/徽标底/气泡底等） */
  static int tint(int color,int alpha){return Color.argb(alpha,Color.red(color),Color.green(color),Color.blue(color));}
  /** 选中/激活态底色：两主题通用的紫温深底（较 SURFACE2 提一档，Text 对比达标） */
  static final int SELECTED_FILL=0xFF2E2644;

  private static final String PREF_FILE="ui_prefs_v1";
  private static final String KEY_THEME="theme";
  private static volatile Design cache;

  static String activeId(Context c){
    Design hit=cache;
    if(hit!=null)return hit.id;
    String id=prefs(c).getString(KEY_THEME,"nova");
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

  private static SharedPreferences prefs(Context c){return c.getSharedPreferences(PREF_FILE,Context.MODE_PRIVATE);}

  private ThemeEngine(){}
}