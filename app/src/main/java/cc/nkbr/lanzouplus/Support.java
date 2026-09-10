package cc.nkbr.lanzouplus;

import android.content.Context;
import android.content.SharedPreferences;

/** 自愿付费解锁状态（本地记录，无服务端、无验证——10 元档信任用户，见 07-研究报告/自愿付费与全App优化-深度研究汇总.md A 部分）。
 *  原则：不付费同样可以完整使用；所有付费 UI 都带"继续下载"降级路径。 */
final class Support {
  private static final String PREF_FILE="support";
  private static final String KEY_UNLOCKED="unlocked";
  private static final String KEY_PAID_AT="paid_at";
  private static final String KEY_LAST_NAG_AT="last_nag_at";
  private static final String KEY_ASK_ON_DOWNLOAD="ask_on_download";
  /** 下载拦截冷却：24h 内最多自动弹 1 次（NN/g 弹窗纪律；会话内拒绝一次后由 MainActivity 的内存标记静默放行） */
  static final long NAG_INTERVAL_MS=24L*60L*60L*1000L;

  static boolean unlocked(Context c){return prefs(c).getBoolean(KEY_UNLOCKED,false);}
  static long paidAt(Context c){return prefs(c).getLong(KEY_PAID_AT,0L);}
  static long lastNagAt(Context c){return prefs(c).getLong(KEY_LAST_NAG_AT,0L);}
  static boolean askOnDownload(Context c){return prefs(c).getBoolean(KEY_ASK_ON_DOWNLOAD,true);}
  static void setAskOnDownload(Context c,boolean value){
    SharedPreferences p=prefs(c);
    try{SharedPreferences.Editor e=p.edit();if(e!=null)e.putBoolean(KEY_ASK_ON_DOWNLOAD,value).apply();}catch(Throwable ignored){}
  }
  /** 记录"刚弹过支持请求"，进入 24h 冷却 */
  static void touchNag(Context c){
    SharedPreferences p=prefs(c);
    try{SharedPreferences.Editor e=p.edit();if(e!=null)e.putLong(KEY_LAST_NAG_AT,System.currentTimeMillis()).apply();}catch(Throwable ignored){}
  }
  /** 零验证零延迟解锁（Seal/MiXplorer 捐赠型产品的信任模式；付费页小字已说明换机后重按一次即可） */
  static void unlock(Context c){
    SharedPreferences p=prefs(c);
    try{SharedPreferences.Editor e=p.edit();if(e!=null)e.putBoolean(KEY_UNLOCKED,true).putLong(KEY_PAID_AT,System.currentTimeMillis()).apply();}catch(Throwable ignored){}
  }
  private static SharedPreferences prefs(Context c){return c.getSharedPreferences(PREF_FILE,Context.MODE_PRIVATE);}

  private Support(){}
}
