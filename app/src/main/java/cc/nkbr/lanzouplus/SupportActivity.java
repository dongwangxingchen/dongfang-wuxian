package cc.nkbr.lanzouplus;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** 支持开发 · 独立付费窗口（用户要求"单独的一个窗口"；设计定稿见 07-研究报告/自愿付费与全App优化-深度研究汇总.md A3）。
 *  结构：开发者信 → 权益 3 条 → 收款码双卡（微信/支付宝，占位 drawable 等用户提供真码后仅换资源）→
 *  「我已完成支付」零验证解锁 → 感谢页（解锁后本页变成状态页）。
 *  红线：不付费也能完整使用；本页任何位置都有"暂时不支持"退出路径。 */
public class SupportActivity extends Activity {
  int BG,SURFACE,SURFACE2,BORDER,PRIMARY,PRIMARY_HI,PRIMARY_LO,TEXT,MUTED;
  LinearLayout root;
  float density;

  /** Paparazzi JVM 渲染时 Activity 未完整 attach（ActivityManager 为空，ContextThemeWrapper.getTheme
   *  → Activity.onApplyThemeResource → setTaskDescription 会 NPE），这里绕开 Activity 主题初始化；
   *  真机上 Activity 正常 attach，走 Activity.onApplyThemeResource，行为不变。 */
  @Override protected void onApplyThemeResource(android.content.res.Resources.Theme theme,int resid,boolean first){
    if(getBaseContext()==null){super.onApplyThemeResource(theme,resid,first);return;}
    try{super.onApplyThemeResource(theme,resid,first);}
    catch(NullPointerException skipped){/* JVM 渲染：无 ActivityManager，跳过 setTaskDescription */}
  }

  @Override public android.content.res.Resources getResources(){
    android.content.Context base=getBaseContext();
    if(base!=null)return base.getResources();
    return super.getResources();
  }

  @Override public void onCreate(Bundle savedInstanceState){
    super.onCreate(savedInstanceState);
    applyPalette();
    density=getResources().getDisplayMetrics().density;
    root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(BG);
    setContentView(root);
    if(Support.unlocked(this))renderThankYou();else renderSupportPage();
  }

  @Override protected void onResume(){
    super.onResume();
    // 从支付 App 扫码回来时刷新解锁态（若用户在其他入口已标记）
    if(Support.unlocked(this)&&!thankYouMode)renderThankYou();
  }
  boolean thankYouMode;

  void applyPalette(){
    ThemeEngine.Design d=ThemeEngine.active(this);
    BG=d.bg;SURFACE=d.surface;SURFACE2=d.surface2;BORDER=d.border;PRIMARY=d.primary;PRIMARY_HI=d.primaryHi;PRIMARY_LO=d.primaryLo;TEXT=d.text;MUTED=d.muted;
    Window window=getWindow();
    window.setStatusBarColor(BG);window.setNavigationBarColor(BG);
    if(Build.VERSION.SDK_INT>=23){
      int flags=window.getDecorView().getSystemUiVisibility();
      flags=flags&~(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
      window.getDecorView().setSystemUiVisibility(flags);
    }
  }

  /** 未解锁：支持页 */
  void renderSupportPage(){
    thankYouMode=false;
    root.removeAllViews();
    LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setPadding(dp(20),dp(10),dp(20),dp(16));
    // 顶栏：左上小字"支持开发者"，右上 × 永远可关（44dp 触达）
    LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);
    TextView kicker=text("支持开发者",12,MUTED);top.addView(kicker,new LinearLayout.LayoutParams(0,dp(44),1));
    TextView close=tool("✕",16);close.setContentDescription("关闭支持页面");
    close.setOnClickListener(v->finish());
    top.addView(close,new LinearLayout.LayoutParams(dp(44),dp(44)));
    page.addView(top,new LinearLayout.LayoutParams(-1,dp(44)));
    // 大标题 + 副标
    TextView title=text("支持 "+MainActivity.PRODUCT_NAME,24,TEXT);
    title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);title.setIncludeFontPadding(false);
    page.addView(title,new LinearLayout.LayoutParams(-2,dp(40)));
    TextView subtitle=text("制作软件花费了大量时间和金钱",13,MUTED);
    subtitle.setPadding(dp(2),dp(2),0,dp(12));
    page.addView(subtitle,new LinearLayout.LayoutParams(-2,-2));
    // 开发者信（诚意区，3 行第一人称）
    LinearLayout letterCard=card();
    TextView letter=new TextView(this);
    letter.setText("这个应用没有广告，也不会强制付费。\n如果你觉得它好用，愿意支持 10 元，\n它会变成我继续更新的底气。");
    letter.setTextColor(TEXT);letter.setTextSize(14);letter.setLineSpacing(dp(4),1f);
    letter.setPadding(dp(16),dp(14),dp(16),dp(14));
    letterCard.addView(letter,new LinearLayout.LayoutParams(-1,-2));
    page.addView(letterCard,new LinearLayout.LayoutParams(-1,-2));
    // 权益 3 条（图标 + 短语，≤14 字）
    page.addView(benefitRow("🔓","全部下载权限直接开放"),new LinearLayout.LayoutParams(-1,dp(34)));
    page.addView(benefitRow("🛠","35 个本地工具永久全功能"),new LinearLayout.LayoutParams(-1,dp(34)));
    page.addView(benefitRow("🤖","AI 对话不限次 · 一次付费长期有效"),new LinearLayout.LayoutParams(-1,dp(34)));
    // 收款区：两码并排 + 中间"或"
    LinearLayout codes=new LinearLayout(this);codes.setGravity(Gravity.CENTER);
    codes.addView(codeCard("微信收款码",R.drawable.pay_wechat,"微信扫码 · 付 10 元"),new LinearLayout.LayoutParams(0,-2,1));
    TextView or=text("或",12,MUTED);
    LinearLayout.LayoutParams orParams=new LinearLayout.LayoutParams(-2,dp(120));orParams.setMargins(dp(8),0,dp(8),0);
    codes.addView(or,orParams);
    codes.addView(codeCard("支付宝收款码",R.drawable.pay_alipay,"支付宝扫码 · 付 10 元"),new LinearLayout.LayoutParams(0,-2,1));
    page.addView(codes,new LinearLayout.LayoutParams(-1,-2));
    // 复制金额小按钮
    TextView copyAmount=tool("复制金额 ¥10",13);
    copyAmount.setGravity(Gravity.CENTER);
    GradientDrawable chip=solidShape(SURFACE2,20);chip.setStroke(dp(1),BORDER);
    copyAmount.setBackground(chip);
    copyAmount.setOnClickListener(v->{
      ((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(android.content.ClipData.newPlainText("支持金额","10"));
      MainActivity.showSupportNotice(this,"金额 ¥10 已复制");
    });
    LinearLayout copyWrap=new LinearLayout(this);copyWrap.setGravity(Gravity.CENTER);
    copyWrap.addView(copyAmount,new LinearLayout.LayoutParams(dp(150),dp(38)));
    LinearLayout.LayoutParams copyParams=new LinearLayout.LayoutParams(-1,-2);copyParams.setMargins(0,dp(12),0,0);
    page.addView(copyWrap,copyParams);
    // 主 CTA：第一人称动词句，零验证解锁
    Button confirm=new Button(this);
    confirm.setText("我已完成支付，解锁全部下载权限");
    confirm.setAllCaps(false);confirm.setTextSize(15);confirm.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
    confirm.setTextColor(BG);
    GradientDrawable cta=solidShape(PRIMARY,24);
    confirm.setBackground(ripple(cta));
    confirm.setOnClickListener(v->unlockNow());
    LinearLayout.LayoutParams ctaParams=new LinearLayout.LayoutParams(-1,dp(52));ctaParams.setMargins(0,dp(14),0,0);
    page.addView(confirm,ctaParams);
    // 辅助链接：暂时不支持（降级路径永远存在）
    TextView skip=text("暂时不支持，继续使用",13,PRIMARY);
    skip.setGravity(Gravity.CENTER);skip.setPadding(0,dp(10),0,0);skip.setClickable(true);skip.setFocusable(true);
    skip.setOnClickListener(v->finish());
    page.addView(skip,new LinearLayout.LayoutParams(-1,dp(40)));
    // 底部小字：诚实说明本地标记
    TextView footnote=text("解锁记录保存在本机 · 换机或清除数据后在本页重新点一次即可\n不付费也可以完整使用，感谢每一份支持",11,MUTED);
    footnote.setGravity(Gravity.CENTER);footnote.setPadding(0,dp(8),0,0);
    page.addView(footnote,new LinearLayout.LayoutParams(-1,-2));
    ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);
    scroll.addView(page,new ScrollView.LayoutParams(-1,-2));
    root.addView(scroll,new LinearLayout.LayoutParams(-1,-1));
  }

  /** 已解锁：感谢页（状态页） */
  void renderThankYou(){
    thankYouMode=true;
    root.removeAllViews();
    LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setGravity(Gravity.CENTER);page.setPadding(dp(20),dp(10),dp(20),dp(16));
    TextView top=tool("✕",16);top.setContentDescription("关闭支持页面");
    top.setGravity(Gravity.CENTER);
    LinearLayout topWrap=new LinearLayout(this);topWrap.setGravity(Gravity.END);
    topWrap.addView(top,new LinearLayout.LayoutParams(dp(44),dp(44)));
    page.addView(topWrap,new LinearLayout.LayoutParams(-1,dp(44)));
    TextView heart=text("❤",56,PRIMARY);
    heart.setGravity(Gravity.CENTER);
    page.addView(heart,new LinearLayout.LayoutParams(-1,dp(96)));
    TextView title=text("已支持 · 谢谢你",24,TEXT);
    title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);title.setGravity(Gravity.CENTER);
    title.setPadding(0,dp(12),0,0);
    page.addView(title,new LinearLayout.LayoutParams(-1,dp(44)));
    long paidAt=Support.paidAt(this);
    String date=paidAt>0?android.text.format.DateFormat.getDateFormat(this).format(new java.util.Date(paidAt)):"";
    TextView detail=text(date.isEmpty()?"全部下载权限已开放":"支持于 "+date+" · 全部下载权限已开放",13,MUTED);
    detail.setGravity(Gravity.CENTER);detail.setPadding(0,dp(8),0,0);
    page.addView(detail,new LinearLayout.LayoutParams(-1,dp(30)));
    TextView footnote=text("这份支持会变成继续更新的底气",11,MUTED);
    footnote.setGravity(Gravity.CENTER);footnote.setPadding(0,dp(16),0,0);
    page.addView(footnote,new LinearLayout.LayoutParams(-1,dp(30)));
    root.addView(page,new LinearLayout.LayoutParams(-1,-1));
    playUnlockAnimation(heart);
  }

  LinearLayout benefitRow(String emoji,String phrase){
    LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);
    TextView icon=text(emoji,14,TEXT);
    row.addView(icon,new LinearLayout.LayoutParams(dp(30),dp(30)));
    TextView label=text(phrase,13,TEXT);
    label.setPadding(dp(10),0,0,0);
    row.addView(label,new LinearLayout.LayoutParams(0,-2,1));
    return row;
  }

  /** 收款码卡片：白底浮起（深色页面上收款码必须白底才可扫），占位 drawable 等用户给码后仅替换资源 */
  LinearLayout codeCard(String label,int drawableRes,String hint){
    LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setGravity(Gravity.CENTER_HORIZONTAL);
    GradientDrawable bg=solidShape(Color.WHITE,16);bg.setStroke(dp(1),BORDER);
    card.setBackground(bg);card.setElevation(dp(2));
    card.setPadding(dp(10),dp(10),dp(10),dp(10));
    ImageView code=new ImageView(this);
    code.setImageResource(drawableRes);
    code.setScaleType(ImageView.ScaleType.FIT_CENTER);
    code.setContentDescription(label+"，扫码支付 10 元");
    card.addView(code,new LinearLayout.LayoutParams(-1,dp(120)));
    TextView name=text(label,12,Color.DKGRAY);name.setGravity(Gravity.CENTER);name.setPadding(0,dp(6),0,0);
    card.addView(name,new LinearLayout.LayoutParams(-1,dp(22)));
    return card;
  }

  void unlockNow(){
    Support.unlock(this);
    renderThankYou();
    MainActivity.showSupportNotice(this,"已解锁全部下载权限 · 谢谢你");
  }

  /** 解锁反馈：克制的单次缩放+淡入（<500ms，无循环；MotionScale 门控在系统动画关闭时跳过） */
  void playUnlockAnimation(View target){
    if(!motionEnabled())return;
    target.setScaleX(0.6f);target.setScaleY(0.6f);target.setAlpha(0f);
    target.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(360)
      .setInterpolator(new android.view.animation.PathInterpolator(0.2f,0f,0f,1f)).start();
  }
  boolean motionEnabled(){
    try{return android.provider.Settings.Global.getFloat(getContentResolver(),android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,1f)>0f;}
    catch(Exception ignored){return true;}
  }

  TextView text(String s,int sp,int color){
    TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setTextColor(color);v.setFontFeatureSettings("kern");return v;
  }
  TextView tool(String s,int sp){
    TextView v=text(s,sp,TEXT);v.setGravity(Gravity.CENTER);v.setClickable(true);v.setFocusable(true);
    v.setBackground(ripple(new ColorDrawable(Color.TRANSPARENT)));return v;
  }
  LinearLayout card(){
    LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);
    GradientDrawable bg=solidShape(SURFACE,20);bg.setStroke(dp(1),BORDER);
    card.setBackground(bg);
    return card;
  }
  GradientDrawable solidShape(int color,int radius){
    GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));return g;
  }
  android.graphics.drawable.Drawable ripple(android.graphics.drawable.Drawable content){
    if(Build.VERSION.SDK_INT>=21)return new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(BORDER),content,null);
    return content;
  }
  int dp(int v){return(int)(v*density+.5f);}
}
