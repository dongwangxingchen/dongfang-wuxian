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

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

/** 支持开发 · 独立付费窗口（用户要求"单独的一个窗口"；设计定稿见 07-研究报告/自愿付费与全App优化-深度研究汇总.md A3）。
 *  结构：开发者信 → 权益 1 条（用户 2026-10-01 口述：原 3 条 emoji 短语精简为 1 条长句）→ 收款码微信单卡全宽
 *  （真实码 v1.3.3 嵌入；v1.4.1 起确认不收款支付宝）→「我已完成支付」零验证解锁 → 感谢页（解锁后本页变成状态页）。
 *  独立 Activity 自己处理窗口 insets（见 installSystemBarInsets）：页头返回箭头与底部小字都不被系统栏压住。
 *  [DFW-74] 独立 Activity 的**进出场动画也自己设**，语义与站内子页 MainActivity.animatePage 完全一致
 *  （推入 300ms 右滑入 + 旧页压暗 / 返回 240ms 右滑走淡出），见 installPageTransitions 与 closePage。
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

  /**
   * [DFW-74] **本页进出场与站内子页转场统一**。
   *
   * 用户 2026-10-01：
   * > "诚信付费的那个预返回动画与其他的不一样，就是崩溃日志或者是其他的那种返回效果并不一样。
   * >  能不能给它们统一为一个非常好的效果？"
   *
   * 病根：本页是**独立 Activity**，此前完全没设转场动画 —— 进场/退场走系统默认转场，
   * 与站内子页（MainActivity.animatePage 的 sharedAxis 推入/抽纸式返回）完全不是一套。
   * 现在把 animatePage 的语义原样搬成窗口动画资源，对应关系写在 res/anim/dfwx_page_*.xml 顶部注释里：
   *   推入 300ms：新页从右侧 28% 屏宽滑入（dfwx_page_open_in）+ 旧页原地缩到 94% 并压暗到 55%（dfwx_page_open_out）；
   *   返回 240ms：上层页向右滑走并淡出（dfwx_page_close_out）+ 下层页不透明不动（dfwx_page_close_in）。
   * 曲线一律用系统 @android:interpolator/fast_out_slow_in（= 站内那条 M3 emphasized），不自己造。
   *
   * **为什么两条 API 都设**（这是本页必须自己设、又必须设对的关键）：
   * ① 任务指定的 {@code overridePendingTransition} 在**被启动页的 onCreate 里调用会被系统直接丢弃** ——
   *    AOSP `ActivityClientController.overridePendingTransition` 有状态门禁：
   *    `if (r != null && r.isState(RESUMED, PAUSING))` 才生效，而 onCreate 时本页还是 INITIALIZING/STARTED
   *    （android13-release 与 main 分支实现一致，均已核对）。也就是说只靠它，付费页的**进场**动画在真机上
   *    根本不会出现（用户反馈的正是真机观感）。它仍然保留：低版本只有这一条路，且官方文档说它的优先级
   *    高于下面这条，两条设成同一组资源，谁生效结果都一样。
   * ② API 34+ 用 {@code overrideActivityTransition}：官方文档明确写了"想定制从 A 打开 B 的转场，
   *    就在 B 的 onCreate 里用 OVERRIDE_TRANSITION_OPEN"，且 AMS 侧
   *    `ActivityClientController.overrideActivityTransition` **没有状态门禁**（直接写 ActivityRecord）。
   *    这才是"被启动页自己定进场动画"的可靠做法，用户真机 Android 16 走的正是这条。
   *
   * 系统"动画时长缩放 = 0"时，窗口动画会被系统整体跳过（动画时长按 0 处理），这里不需要额外门控。
   */
  void installPageTransitions(){
    if(Build.VERSION.SDK_INT>=34){
      overrideActivityTransition(OVERRIDE_TRANSITION_OPEN,R.anim.dfwx_page_open_in,R.anim.dfwx_page_open_out);
      overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE,R.anim.dfwx_page_close_in,R.anim.dfwx_page_close_out);
    }
    overridePendingTransition(R.anim.dfwx_page_open_in,R.anim.dfwx_page_open_out);
  }

  /** [DFW-74] 本页唯一的退场出口：所有返回路径都走这里，保证退场动画与站内子页一致。
   *  先设动画再 finish()：此刻本页确定处于 RESUMED（正是 overridePendingTransition 状态门禁要求的），
   *  设完再退，AMS 会把这条动画用在紧随其后的关闭转场里。 */
  void closePage(){
    overridePendingTransition(R.anim.dfwx_page_close_in,R.anim.dfwx_page_close_out);
    finish();
  }

  /** 系统返回（边缘滑动 / 返回键）也走同一个退场：本页没有 enableOnBackInvokedCallback，
   *  走的是传统 onBackPressed 链路；默认实现就是裸 finish()，不接管的话这条路径的退场
   *  又会退回系统默认动画，跟左上角返回箭头不一致（用户要求的就是"统一"）。 */
  @Override public void onBackPressed(){
    closePage();
  }

  @Override public void onCreate(Bundle savedInstanceState){
    super.onCreate(savedInstanceState);
    installPageTransitions();
    applyPalette();
    density=getResources().getDisplayMetrics().density;
    root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(BG);
    setContentView(root);
    // 用户 2026-10-01 口述要求：本页顶部返回箭头被状态栏挡住（截图证据），独立窗口自己吃 insets
    installSystemBarInsets();
    // v1.5.1 用户定调：无论是否已解锁，进来永远先看到赞助码页；点下方解锁按钮才进爱心感谢页
    renderSupportPage();
  }

  @Override protected void onResume(){
    super.onResume();
    // thankYouMode 时无需处理；未解锁的赞助码页保持不动（扫码回来解锁仍由解锁按钮触发）
  }
  boolean thankYouMode;
  /** [BRAND-001] 解锁进行中：防连点导致整页重建途中被再次触发。 */
  boolean unlocking;
  /** [BRAND-001] 解锁流程真正被执行的次数（供测试证明"连点只进一次"）。 */
  int unlockInvocations;

  void applyPalette(){
    ThemeEngine.Design d=ThemeEngine.active(this);
    BG=d.bg;SURFACE=d.surface;SURFACE2=d.surface2;BORDER=d.border;PRIMARY=d.primary;PRIMARY_HI=d.primaryHi;PRIMARY_LO=d.primaryLo;TEXT=d.text;MUTED=d.muted;
    Window window=getWindow();
    // 用户 2026-10-01：本页改 edge-to-edge（见 installSystemBarInsets）后，新系统上系统栏透明、直接露出根 View 的 BG；
    // 旧系统上这两行仍把系统栏刷成 BG —— 两种系统观感一致（深色底 + 浅色图标），下面清 LIGHT_* 即浅色图标。
    window.setStatusBarColor(BG);window.setNavigationBarColor(BG);
    if(Build.VERSION.SDK_INT>=23){
      int flags=window.getDecorView().getSystemUiVisibility();
      flags=flags&~(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
      window.getDecorView().setSystemUiVisibility(flags);
    }
  }

  /** 用户 2026-10-01 口述要求（截图：页面顶部返回箭头被状态栏挡住）。
   *  病根：本页是**独立 Activity**，此前完全没处理窗口 insets —— 既没 setDecorFitsSystemWindows(false)，
   *  也没有 OnApplyWindowInsetsListener，窗口默认把内容摆在系统栏之下，返回箭头自然被状态栏压住。
   *  做法（现代路线）：窗口装饰不再自动避让系统栏，改由内容根 View 把 systemBars 的四个方向吃成 padding，
   *  顶部箭头、底部小字都让开；根 View 背景仍是 BG（padding 只缩内容不缩背景），系统栏区域观感不变。
   *  写法参考 MainActivity.installSystemNavigationInsets()（那边只垫导航条，本页独立窗口要连状态栏一起兜住）。 */
  void installSystemBarInsets(){
    WindowCompat.setDecorFitsSystemWindows(getWindow(),false);
    ViewCompat.setOnApplyWindowInsetsListener(root,(view,insets)->{
      androidx.core.graphics.Insets bars=insets.getInsets(WindowInsetsCompat.Type.systemBars());
      view.setPadding(bars.left,bars.top,bars.right,bars.bottom);
      return insets;
    });
    ViewCompat.requestApplyInsets(root);
  }

  /**
   * [DFW-70] **统一页头：左上角「←」返回**。
   *
   * 用户 2026-10-01：
   * > "我希望你把这种类型的子页面返回按钮统一一下，别这个子页面左上角是叉号关闭，
   * >  那个子页面是右上角箭头关闭，这不纯胡闹呢？"
   *
   * 全站约定（用户已拍板）：**`ic_back` = 返回上一页，一律在左上角；`ic_close` 只留给弹窗/浮层**。
   * 本页原来是右上角一个**文字「✕」**（全项目唯一用文字字形当关闭按钮的地方），
   * 与设置子页的左上角箭头不一致，现在统一。
   *
   * 用户 2026-10-01 口述补充：**页头只留返回箭头，右边不要任何文字**——
   * 原来那句 kicker「诚信付费 · 自愿」已删，参数与右侧 TextView 一并清掉（两处调用都传空串，留着就是死代码）。
   */
  LinearLayout pageHeader(){
    LinearLayout top=new LinearLayout(this);
    top.setGravity(Gravity.CENTER_VERTICAL);
    android.widget.ImageButton back=new android.widget.ImageButton(this);
    back.setImageResource(R.drawable.ic_back);
    back.setColorFilter(PRIMARY);
    back.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
    back.setPadding(dp(10),dp(10),dp(10),dp(10));
    back.setBackground(ripple(new ColorDrawable(Color.TRANSPARENT)));
    back.setContentDescription("返回");
    back.setOnClickListener(v->closePage());
    top.addView(back,new LinearLayout.LayoutParams(dp(44),dp(44)));
    return top;
  }

  /** 未解锁：支持页 */
  void renderSupportPage(){
    thankYouMode=false;
    root.removeAllViews();
    LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setPadding(dp(20),dp(10),dp(20),dp(16));
    // [DFW-70] 统一页头：原来这里是**右上角一个文字「✕」**，与设置子页的左上角箭头不一致。
    // 用户 2026-10-01 口述：页头只要左上角返回箭头，右边那句「诚信付费 · 自愿」kicker 删掉。
    page.addView(pageHeader(),new LinearLayout.LayoutParams(-1,dp(44)));
    // 大标题（用户 2026-10-01 口述：标题下那行「诚信付费 ￥5 · 一次付清 · 承诺永久更新」删掉，
    // ￥5 只留在中段价格区可见 —— 价格区本身保留不动）
    TextView title=text("支持 "+MainActivity.PRODUCT_NAME,24,TEXT);
    title.setTypeface(AppFonts.bold(this));title.setIncludeFontPadding(false);
    page.addView(title,new LinearLayout.LayoutParams(-2,dp(40)));
    // 开发者信（诚意区，第一人称，v1.5.0 用户定调：委婉、少小字）——成本与坚持 + 学生分层委婉化 + 感谢
    LinearLayout letterCard=card();
    TextView letter=new TextView(this);
    letter.setText("这个应用没有广告，也不强制付费。\n维护和更新都需要成本，我想高质量地一直做下去。\n还在读书、暂时没有收入的朋友，点击下方按钮直接使用即可；\n如果力所能及，这 5 元会成为我继续更新的动力和底气。\n谢谢你的支持。");
    letter.setTextColor(TEXT);letter.setTextSize(14);letter.setLineSpacing(dp(4),1f);letter.setTypeface(AppFonts.normal(this));
    letter.setPadding(dp(16),dp(14),dp(16),dp(14));
    letterCard.addView(letter,new LinearLayout.LayoutParams(-1,-2));
    letterCard.setContentDescription("开发者的话：这个应用没有广告，也不强制付费；还在读书的朋友可以直接使用。");
    LinearLayout.LayoutParams letterLp=new LinearLayout.LayoutParams(-1,-2);
    letterLp.topMargin=dp(12);
    page.addView(letterCard,letterLp);
    // 权益行（用户 2026-10-01 口述：原来 3 条 emoji 短语全部删掉，只留这一条长句；原话照抄，仅补句末句号）
    LinearLayout perks=card();
    // [BRAND-001] 内边距与开发者信同基准（原来 14/6，与信的 16/14 不在一个节奏上）
    perks.setPadding(dp(16),dp(8),dp(16),dp(8));
    perks.addView(benefitRow(R.drawable.ic_trophy,"每周自费续 1 万次 DeepSeek v4.1，自动刷新，专供诚信付费的朋友，请诚信付费。"),new LinearLayout.LayoutParams(-1,-2));
    LinearLayout.LayoutParams perksLp=new LinearLayout.LayoutParams(-1,-2);
    perksLp.topMargin=dp(12);
    perksLp.bottomMargin=dp(12);
    page.addView(perks,perksLp);
    // [BRAND-001] 价格区从"裸行"升级成一张卡：上面是开发者信、中间是权益、这里是价格，
    // 三块同一节奏才像一页设计过的产品页；￥5 仍然在**中段**一眼可见（用户 2026-10-01 的要求）。
    LinearLayout priceCard=card();
    priceCard.setPadding(dp(16),dp(14),dp(16),dp(14));
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
    priceCard.addView(price,new LinearLayout.LayoutParams(-1,-2));
    LinearLayout.LayoutParams priceLp=new LinearLayout.LayoutParams(-1,-2);priceLp.bottomMargin=dp(12);
    page.addView(priceCard,priceLp);
    // 收款区：微信单卡全宽（用户仅收款微信；码图撑满卡宽，消除两侧留白）
    page.addView(codeCard("微信收款码",R.drawable.pay_wechat,"微信扫码 · 付 5 元"),new LinearLayout.LayoutParams(-1,-2));
    // 主 CTA：第一人称动词句，零验证解锁（v1.5.1 删「复制金额」小按钮——重复无用，减小字）
    Button confirm=new Button(this);
    confirm.setText("诚信付费，解锁全部权限");
    confirm.setAllCaps(false);confirm.setTextSize(15);confirm.setTypeface(AppFonts.bold(this));
    confirm.setTextColor(BG);
    // 真胶囊：`solidShape` 的半径量化会把 24 压成 26（做出来是圆角方块而不是胶囊），
    // 所以直接走 PremiumSurface.pill（半径 = 高度一半）。高度也统一到 56dp。
    confirm.setBackground(ripple(PremiumSurface.pill(PRIMARY,dp(56),0,0,PremiumSurface.HIGHLIGHT)));
    confirm.setContentDescription("诚信付费，解锁全部下载权限；不付费也可以完整使用其它功能");
    // [BRAND-001] 防连点：解锁会整页重建，连点两次会在重建途中再触发一次，
    // 表现为按钮闪一下/白屏一帧。解锁是本地幂等写，但重建不是幂等的。
    confirm.setOnClickListener(v->{
      if(unlocking)return;
      unlocking=true;
      confirm.setEnabled(false);
      confirm.setAlpha(0.6f);
      unlockNow();
    });
    LinearLayout.LayoutParams ctaParams=new LinearLayout.LayoutParams(-1,dp(56));ctaParams.setMargins(0,dp(16),0,0);
    page.addView(confirm,ctaParams);
    // 辅助链接：暂时不支持（降级路径永远存在）
    TextView skip=text("暂时不支持，继续使用",13,PRIMARY);
    skip.setGravity(Gravity.CENTER);skip.setClickable(true);skip.setFocusable(true);
    // [BRAND-001] 降级路径原来是一个**没有任何按压反馈**的裸 TextView：点下去毫无回应，
    // 而这恰恰是"不付费也能走"的唯一出口，最不该让人怀疑自己点没点到。
    skip.setBackground(ripple(new ColorDrawable(Color.TRANSPARENT)));
    skip.setContentDescription("暂时不支持，继续使用（不付费也能完整使用其它功能）");
    skip.setOnClickListener(v->closePage());
    LinearLayout.LayoutParams skipLp=new LinearLayout.LayoutParams(-1,dp(44));
    skipLp.topMargin=dp(6);
    page.addView(skip,skipLp);
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
    // [DFW-70] 两个状态页的返回控件必须长得一样：同样左上角「←」。
    page.addView(pageHeader(),new LinearLayout.LayoutParams(-1,dp(44)));
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

  /** 权益行：图标 + 一句说明（用户 2026-10-01 口述要求）。图标走 Phosphor 矢量（ic_trophy），与文字同色；
   *  行高由原来的固定 38dp 改 WRAP_CONTENT + 上下内距 —— 换成一条长句后会折行，定高会把第二行裁掉。 */
  LinearLayout benefitRow(int iconRes,String phrase){
    LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);
    row.setPadding(0,dp(9),0,dp(9));
    ImageView icon=new ImageView(this);
    icon.setImageResource(iconRes);
    icon.setColorFilter(PRIMARY);
    icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    row.addView(icon,new LinearLayout.LayoutParams(dp(20),dp(20)));
    TextView label=text(phrase,13,TEXT);
    label.setLineSpacing(dp(3),1f);
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
    unlockInvocations++;
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
