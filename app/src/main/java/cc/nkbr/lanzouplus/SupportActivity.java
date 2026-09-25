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
 *  结构：开发者信 → 权益 3 条 → 收款码微信单卡全宽（真实码 v1.3.3 嵌入；v1.4.1 起确认不收款支付宝）→
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
    // v1.5.1 用户定调：无论是否已解锁，进来永远先看到赞助码页；点下方解锁按钮才进爱心感谢页
    renderSupportPage();
  }

  @Override protected void onResume(){
    super.onResume();
    // thankYouMode 时无需处理；未解锁的赞助码页保持不动（扫码回来解锁仍由解锁按钮触发）
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
      // v1.4.0「高级苹果」为浅色底（#F2F2F7）：状态栏/导航栏改深色字，否则浅底浅字不可读
      if(ThemeEngine.isApple(this))flags|=View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
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
    TextView kicker=text("诚信付费 · 自愿",12,MUTED);top.addView(kicker,new LinearLayout.LayoutParams(0,dp(44),1));
    TextView close=tool("✕",16);close.setContentDescription("关闭支持页面");
    close.setOnClickListener(v->finish());
    top.addView(close,new LinearLayout.LayoutParams(dp(44),dp(44)));
    page.addView(top,new LinearLayout.LayoutParams(-1,dp(44)));
    // 大标题 + 副标
    TextView title=text("支持 "+MainActivity.PRODUCT_NAME,24,TEXT);
    title.setTypeface(AppFonts.bold(this));title.setIncludeFontPadding(false);
    page.addView(title,new LinearLayout.LayoutParams(-2,dp(40)));
    TextView subtitle=text("诚信付费 ￥5 · 一次付清 · 承诺永久更新",13,MUTED);
    subtitle.setPadding(dp(2),dp(2),0,dp(12));
    page.addView(subtitle,new LinearLayout.LayoutParams(-2,-2));
    // 开发者信（诚意区，第一人称，v1.5.0 用户定调：委婉、少小字）——成本与坚持 + 学生分层委婉化 + 感谢
    LinearLayout letterCard=card();
    TextView letter=new TextView(this);
    letter.setText("这个应用没有广告，也不强制付费。\n维护和更新都需要成本，我想高质量地一直做下去。\n还在读书、暂时没有收入的朋友，点击下方按钮直接使用即可；\n如果力所能及，这 5 元会成为我继续更新的动力和底气。\n谢谢你的支持。");
    letter.setTextColor(TEXT);letter.setTextSize(14);letter.setLineSpacing(dp(4),1f);letter.setTypeface(AppFonts.normal(this));
    letter.setPadding(dp(16),dp(14),dp(16),dp(14));
    letterCard.addView(letter,new LinearLayout.LayoutParams(-1,-2));
    page.addView(letterCard,new LinearLayout.LayoutParams(-1,-2));
    // 权益 3 条（图标 + 短语，≤14 字）
    page.addView(benefitRow("🔓","全部下载权限直接开放"),new LinearLayout.LayoutParams(-1,dp(34)));
    page.addView(benefitRow("🛠","35 个本地工具永久全功能"),new LinearLayout.LayoutParams(-1,dp(34)));
    page.addView(benefitRow("🤖","AI 对话不限次 · 一次付费长期有效"),new LinearLayout.LayoutParams(-1,dp(34)));
    // 价格大字（成熟付费页惯例：价格必须一眼可见）+ 永久更新承诺
    LinearLayout price=new LinearLayout(this);price.setGravity(Gravity.CENTER_VERTICAL);
    TextView amount=text("￥5",30,PRIMARY);amount.setTypeface(AppFonts.bold(this));
    price.addView(amount,new LinearLayout.LayoutParams(-2,-2));
    LinearLayout priceCol=new LinearLayout(this);priceCol.setOrientation(LinearLayout.VERTICAL);
    TextView priceNote1=text("诚信付费 · 一次付清",14,TEXT);priceNote1.setTypeface(AppFonts.bold(this));
    TextView priceNote2=text("承诺永久更新 · 绝不停更",11,MUTED);
    priceCol.addView(priceNote1,new LinearLayout.LayoutParams(-2,-2));
    LinearLayout.LayoutParams note2Lp=new LinearLayout.LayoutParams(-2,-2);note2Lp.topMargin=dp(2);
    priceCol.addView(priceNote2,note2Lp);
    LinearLayout.LayoutParams priceColLp=new LinearLayout.LayoutParams(-2,-2);priceColLp.leftMargin=dp(12);
    price.addView(priceCol,priceColLp);
    LinearLayout.LayoutParams priceLp=new LinearLayout.LayoutParams(-1,-2);priceLp.topMargin=dp(4);priceLp.bottomMargin=dp(6);
    page.addView(price,priceLp);
    // 收款区：微信单卡全宽（用户仅收款微信；码图撑满卡宽，消除两侧留白）
    page.addView(codeCard("微信收款码",R.drawable.pay_wechat,"微信扫码 · 付 5 元"),new LinearLayout.LayoutParams(-1,-2));
    // 主 CTA：第一人称动词句，零验证解锁（v1.5.1 删「复制金额」小按钮——重复无用，减小字）
    Button confirm=new Button(this);
    confirm.setText("诚信付费，解锁全部权限");
    confirm.setAllCaps(false);confirm.setTextSize(15);confirm.setTypeface(AppFonts.bold(this));
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
    TextView footnote=text("解锁记录保存在本机 · 不付费也可以完整使用",11,MUTED);
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
    TextView title=text("已解锁 · 谢谢你",24,TEXT);
    title.setTypeface(AppFonts.bold(this));title.setGravity(Gravity.CENTER);
    title.setPadding(0,dp(12),0,0);
    page.addView(title,new LinearLayout.LayoutParams(-1,dp(44)));
    // 诚信徽章（研究 M3：支持后即时反馈=动效+徽章；静态徽章，无循环动画，不画蛇添足）
    TextView badge=text("诚信支持者",12,PRIMARY);badge.setTypeface(AppFonts.bold(this));
    GradientDrawable badgeBg=solidShape(ThemeEngine.tint(PRIMARY,28),20);
    badge.setBackground(badgeBg);badge.setPadding(dp(14),dp(5),dp(14),dp(5));
    LinearLayout badgeWrap=new LinearLayout(this);badgeWrap.setGravity(Gravity.CENTER);
    badgeWrap.addView(badge,new LinearLayout.LayoutParams(-2,dp(28)));
    LinearLayout.LayoutParams badgeLp=new LinearLayout.LayoutParams(-1,-2);badgeLp.topMargin=dp(10);
    page.addView(badgeWrap,badgeLp);
    long paidAt=Support.paidAt(this);
    String date=paidAt>0?android.text.format.DateFormat.getDateFormat(this).format(new java.util.Date(paidAt)):"";
    TextView detail=text(date.isEmpty()?"全部下载权限已开放":"解锁于 "+date+" · 全部下载权限已开放",13,MUTED);
    detail.setGravity(Gravity.CENTER);detail.setPadding(0,dp(10),0,0);
    page.addView(detail,new LinearLayout.LayoutParams(-1,dp(30)));
    TextView footnote=text("本软件承诺永久更新 · 绝不停更\n这份支持会变成继续更新的底气",11,MUTED);
    footnote.setGravity(Gravity.CENTER);footnote.setPadding(0,dp(12),0,0);
    page.addView(footnote,new LinearLayout.LayoutParams(-1,-2));
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

  /** 收款码卡片：白底浮起（深色页面上收款码必须白底才可扫）；码图 adjustViewBounds 撑满卡宽、
   *  高度按原图比例自适应（v1.4.2 消除 FIT_CENTER 定高造成的两侧大留白），卡片 padding 归零 +
   *  clipToOutline 让码图边缘贴合卡片圆角 */
  LinearLayout codeCard(String label,int drawableRes,String hint){
    LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);
    GradientDrawable bg=solidShape(Color.WHITE,16);bg.setStroke(dp(1),BORDER);
    card.setBackground(bg);card.setElevation(dp(2));card.setClipToOutline(true);
    ImageView code=new ImageView(this);
    code.setImageResource(drawableRes);
    code.setScaleType(ImageView.ScaleType.FIT_CENTER);
    code.setAdjustViewBounds(true);
    code.setContentDescription(label+"，扫码支付 ￥5 元");
    card.addView(code,new LinearLayout.LayoutParams(-1,-2));
    TextView name=text(label,12,Color.DKGRAY);name.setGravity(Gravity.CENTER);name.setPadding(dp(6),dp(10),dp(6),dp(12));
    card.addView(name,new LinearLayout.LayoutParams(-1,-2));
    return card;
  }

  /** 零验证解锁：唯一按钮，付费者与暂无收入者同一入口（文案已委婉分层，不再设独立免费链接） */
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
    TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setTextColor(color);v.setFontFeatureSettings("kern");v.setTypeface(AppFonts.normal(this));return v;
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
