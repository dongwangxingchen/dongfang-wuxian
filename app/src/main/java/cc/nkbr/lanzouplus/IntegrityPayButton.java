package cc.nkbr.lanzouplus;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** 设置页顶部「诚信付费」独立按钮（v1.4.2，用户指定：不叫"支持"、放顶部单独一个大按钮才显眼、
 *  付费前不亮光 / 付费后亮光并循环播放简约扫光特效）。
 *  双状态由 Support.unlocked 驱动：未付费 = primary 底 + 「诚信付费 · 自愿」，点击进付费页；
 *  已付费 = primaryHi 亮底 + 白高光描边 + 「已诚信付费 ✓」+ 扫光循环，点击弹感谢弹窗（回调方区分）。
 *  标题左边一律带一枚奖杯图标（ic_trophy，与标题同色，装饰性不单独播报）——
 *  用户 2026-10-01 口述：两种状态都加、保持一致；未付费态的副标小字
 *  「￥5 · 学生免费 · 不付费也可完整使用」整行删除（只有已付费才显示「感谢支持 · 全部权限已开放」）。
 *  扫光 = 白色**水平**渐变亮带 translationX 从左扫到右（facebook/shimmer 遮罩扫光的纯 View 版），
 *  亮带铺满按钮整个高度、带宽随按钮宽度走，RESTART + LinearInterpolator，2600ms 一轮，
 *  峰值白光仍是 35%（与旧版同值，不变刺眼），再乘 alpha 包络 sin(πt)*0.9 → 实际峰值约 31%；
 *  t=0 时 alpha=0，快照/首帧无残影；动画关（motionEnabled=false）不启动，
 *  onDetachedFromWindow 取消，省电。
 *  <p>2026-10-01 真机反馈修复（用户逐字："白色的流光效果只覆盖了 2/3 的地方，看起来有那种割裂感"）。
 *  病根两条：
 *  ① 旧扫光层是**固定 40dp 高 + 垂直居中**，而按钮实测高约 63dp（JVM 实测 166px @420dpi，
 *     上下内距 12dp + 标题行 20dp + 副标约 15dp）—— 40/63≈63%，正好就是用户说的"2/3"：
 *     上下各留一条**永远扫不到**的硬边，这两条边就是割裂感的来源；
 *  ② 旧渐变是 TL_BR **斜向**，白光是一条斜带，在圆角矩形里被 setClipToOutline 切出斜硬边。
 *  修法：高度铺满整块按钮（见 onMeasure/onLayout），渐变改水平方向，亮带宽度随按钮宽度走。
 *  顺带修掉旧位移公式的"空转"：旧式 -sw + t*(w + 2sw) 会让亮带亮心在 t≈0.70 就移出按钮右缘
 *  （整条移出约 t≈0.79），之后约 30% 的周期按钮上基本无光；新式见 shineOffset。 */
public class IntegrityPayButton extends FrameLayout {
  private final boolean paid;
  private final boolean motionEnabled;
  /** 按钮密度：onLayout 里算亮带宽度下限用。 */
  private final float density;
  /** 扫光层（仅已付费态存在）。包级可见：同包 JVM 用例要断言"高度与按钮同高"。 */
  View shine;
  /** 扫光动画器：null = 未启动或已取消。包级可见：同包 JVM 用例要断言"动画关/退场后必须停"。 */
  ValueAnimator shineAnimator;

  public IntegrityPayButton(Context context, ThemeEngine.Design design, float density,
                            boolean paid, boolean motionEnabled, Runnable onClick) {
    super(context);
    this.paid = paid;
    this.motionEnabled = motionEnabled;
    this.density = density;
    boolean apple = "apple".equals(design.id);
    int radius = apple ? 12 : 16;
    // 付费亮光态底色：legacy 的 primaryHi 本就是亮档；apple 的 primaryHi 是 iOS 按压态深蓝，
    // 改为向白混合 18% 的亮蓝（#007AFF→约 #2E93FF），符合「付费后亮光」的要求
    int paidFill = design.primaryHi;
    if (apple) {
      float m = 0.18f;
      paidFill = Color.rgb(
          Math.round(Color.red(design.primary) + (255 - Color.red(design.primary)) * m),
          Math.round(Color.green(design.primary) + (255 - Color.green(design.primary)) * m),
          Math.round(Color.blue(design.primary) + (255 - Color.blue(design.primary)) * m));
    }
    GradientDrawable bg = new GradientDrawable();
    bg.setCornerRadius(density * radius);
    bg.setColor(paid ? paidFill : design.primary);
    if (paid) bg.setStroke(Math.round(density), 0x66FFFFFF);
    setBackground(bg);
    setClipToOutline(true);
    setClickable(true);
    setFocusable(true);
    setPadding(Math.round(density * 16), Math.round(density * 12), Math.round(density * 16), Math.round(density * 12));
    int onPrimary = design.bg;
    int onPrimarySub = (design.bg & 0x00FFFFFF) | 0xB0000000;
    LinearLayout box = new LinearLayout(context);
    box.setOrientation(LinearLayout.VERTICAL);
    box.setGravity(Gravity.CENTER);
    addView(box, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
    // 标题行：奖杯图标 + 大字（用户 2026-10-01 口述：大字左边加一个 20dp 左右的奖杯，与标题同色、垂直居中，两种状态都加）
    LinearLayout titleRow = new LinearLayout(context);
    titleRow.setOrientation(LinearLayout.HORIZONTAL);
    titleRow.setGravity(Gravity.CENTER);
    box.addView(titleRow, new LinearLayout.LayoutParams(-2, -2));
    ImageView trophy = new ImageView(context);
    trophy.setImageResource(R.drawable.ic_trophy);
    trophy.setColorFilter(onPrimary);
    trophy.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    titleRow.addView(trophy, new LinearLayout.LayoutParams(Math.round(density * 20), Math.round(density * 20)));
    TextView title = new TextView(context);
    title.setText(paid ? "已诚信付费 ✓" : "诚信付费 · 自愿");
    title.setTextColor(onPrimary);
    title.setTextSize(16);
    title.setTypeface(AppFonts.bold(getContext()));
    LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(-2, -2);
    titleLp.leftMargin = Math.round(density * 6);
    titleRow.addView(title, titleLp);
    // 用户 2026-10-01 口述：未付费态那行小字（原「￥5 · 学生免费 · 不付费也可完整使用」）整行删除，直接不加 View；
    // 已付费态的「感谢支持 · 全部权限已开放」保留。
    if (paid) {
      TextView sub = new TextView(context);
      sub.setText("感谢支持 · 全部权限已开放");
      sub.setTextColor(onPrimarySub);
      sub.setTextSize(11);
      sub.setTypeface(AppFonts.normal(getContext()));
      LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-2, -2);
      subLp.topMargin = Math.round(density * 3);
      box.addView(sub, subLp);
    }
    if (paid) {
      shine = new View(context);
      // 水平三段渐变 + 两侧柔肩：白光是**竖向的柔和亮带**，不再是对角斜带。
      // 峰值仍是 0x59（35% 白）—— 与旧实现同值，所以不会比现在更刺眼；
      // 两头透明保证亮带在左右两端没有硬边，肩部 0x1A 让亮带边缘是渐隐而不是刀切。
      GradientDrawable sweep = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
          new int[]{0x00FFFFFF, 0x1AFFFFFF, 0x59FFFFFF, 0x1AFFFFFF, 0x00FFFFFF});
      shine.setBackground(sweep);
      shine.setAlpha(0f);
      // 尺寸由 onMeasure/onLayout 接管：MATCH_PARENT 只是表达"要铺满"的意图，
      // 真实绘制框在 onLayout 里按按钮实际大小钉死（见那两个 override 的注释）。
      LayoutParams shineLp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, Gravity.CENTER_VERTICAL);
      addView(shine, shineLp);
    }
    setContentDescription(paid ? "已诚信付费，全部权限已开放" : "诚信付费，自愿支持开发");
    setOnClickListener(v -> { if (onClick != null) onClick.run(); });
  }

  /** 高度是 wrap_content 时，MATCH_PARENT 的扫光层**不能**参与按钮高度决策：
   *  FrameLayout 在非 EXACTLY 规格下会把 MATCH_PARENT 子视图按"父容器给的可用高度"来量，
   *  结果是把按钮撑到父容器给的最大高度（v1.4.2 快照踩过：整块按钮变成一屏高）。
   *  做法：高度改用 UNSPECIFIED 规格量一次，让内容（标题行 + 副标）决定按钮高度；
   *  扫光层的真实绘制框交给 onLayout 钉（它要的是"整块按钮"，含 padding 区）。 */
  @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    int heightMode = MeasureSpec.getMode(heightMeasureSpec);
    if (shine == null || heightMode == MeasureSpec.EXACTLY) {
      super.onMeasure(widthMeasureSpec, heightMeasureSpec);
      return;
    }
    super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
    int height = getMeasuredHeight();
    if (heightMode == MeasureSpec.AT_MOST) height = Math.min(height, MeasureSpec.getSize(heightMeasureSpec));
    setMeasuredDimension(getMeasuredWidth(), height);
  }

  /** 扫光层铺满整块按钮（**含 padding 区**）：FrameLayout 的子视图一定会被父 padding 内缩，
   *  只靠 LayoutParams 只能覆盖到内容区，上下仍各留一条 padding 高的硬边 —— 那正是用户看到的割裂感。
   *  所以 super.onLayout 之后把扫光层的绘制框直接钉到 (0,0,亮带宽,按钮高)：
   *  既绕开 padding 内缩，也补上 onMeasure 里"为了不被撑高而量成 0"的那个高度。
   *  亮带宽度 = 按钮宽的 62%（下限 132dp）：旧实现固定 110dp，在宽屏上只是"一小块"，
   *  现在窄屏不显小、宽屏就是"一整片光扫过去"，且随按钮宽度自适应、不用改代码。 */
  @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
    super.onLayout(changed, left, top, right, bottom);
    if (shine == null) return;
    int width = getWidth(), height = getHeight();
    if (width <= 0 || height <= 0) return;
    int band = Math.max(Math.round(density * 132f), Math.round(width * 0.62f));
    shine.layout(0, 0, band, height);
  }

  @Override protected void onAttachedToWindow() {
    super.onAttachedToWindow();
    if (paid && motionEnabled && shine != null) startShine();
  }

  @Override protected void onDetachedFromWindow() {
    if (shineAnimator != null) {shineAnimator.cancel();shineAnimator = null;}
    super.onDetachedFromWindow();
  }

  /** 扫光位移（纯函数，抽出来是为了让 JVM 用例能直接推演几何）：
   *  t=0 时亮带**右缘正好贴住按钮左缘**、t=1 时亮带**左缘正好贴住按钮右缘**，
   *  中间单调平移 —— 于是按钮宽度上每一点都会被扫到、且周期里没有任何"完全无光"的时段。
   *  旧实现是 -sw + t*(w + 2sw)：亮带亮心在 t≈0.70 就移出按钮右缘（整条移出约 t≈0.79），
   *  之后约 30% 的周期按钮上基本无光（约 780ms 空转）。 */
  static float shineOffset(float t, float buttonWidth, float bandWidth) {
    return -bandWidth + t * (buttonWidth + bandWidth);
  }

  private void startShine() {
    if (shineAnimator != null) return;
    shineAnimator = ValueAnimator.ofFloat(0f, 1f);
    shineAnimator.setDuration(2600);
    shineAnimator.setInterpolator(new android.view.animation.LinearInterpolator());
    shineAnimator.setRepeatCount(ValueAnimator.INFINITE);
    shineAnimator.setRepeatMode(ValueAnimator.RESTART);
    shineAnimator.addUpdateListener(a -> {
      View s = shine;
      if (s == null) return;
      float w = getWidth(), sw = s.getWidth();
      if (w <= 0 || sw <= 0) return;
      float t = (float) a.getAnimatedValue();
      s.setTranslationX(shineOffset(t, w, sw));
      s.setAlpha((float) Math.sin(Math.PI * t) * 0.9f);
    });
    shineAnimator.start();
  }
}
