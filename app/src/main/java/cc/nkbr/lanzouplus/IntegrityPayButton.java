package cc.nkbr.lanzouplus;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** 设置页顶部「诚信付费」独立按钮（v1.4.2，用户指定：不叫"支持"、放顶部单独一个大按钮才显眼、
 *  付费前不亮光 / 付费后亮光并循环播放简约扫光特效）。
 *  双状态由 Support.unlocked 驱动：未付费 = primary 底 + 「诚信付费 · 自愿」，点击进付费页；
 *  已付费 = primaryHi 亮底 + 白高光描边 + 「已诚信付费 ✓」+ 扫光循环，点击弹感谢弹窗（回调方区分）。
 *  扫光 = 白色斜向渐变窄条 translationX 扫过（facebook/shimmer 遮罩扫光的纯 View 版），
 *  RESTART + LinearInterpolator，2600ms 一轮，峰值白光约 31%（简约不晃眼）；t=0 时 alpha=0，
 *  快照/首帧无残影；动画关（motionEnabled=false）不启动，onDetachedFromWindow 取消，省电。 */
public class IntegrityPayButton extends FrameLayout {
  private final boolean paid;
  private final boolean motionEnabled;
  private View shine;
  private ValueAnimator shineAnimator;

  public IntegrityPayButton(Context context, ThemeEngine.Design design, float density,
                            boolean paid, boolean motionEnabled, Runnable onClick) {
    super(context);
    this.paid = paid;
    this.motionEnabled = motionEnabled;
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
    TextView title = new TextView(context);
    title.setText(paid ? "已诚信付费 ✓" : "诚信付费 · 自愿");
    title.setTextColor(onPrimary);
    title.setTextSize(16);
    title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
    box.addView(title, new LinearLayout.LayoutParams(-2, -2));
    TextView sub = new TextView(context);
    sub.setText(paid ? "感谢支持 · 全部权限已开放" : "¥10 · 学生免费 · 不付费也可完整使用");
    sub.setTextColor(onPrimarySub);
    sub.setTextSize(11);
    LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-2, -2);
    subLp.topMargin = Math.round(density * 3);
    box.addView(sub, subLp);
    if (paid) {
      shine = new View(context);
      GradientDrawable sweep = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
          new int[]{0x00FFFFFF, 0x59FFFFFF, 0x00FFFFFF});
      shine.setBackground(sweep);
      shine.setAlpha(0f);
      // 固定尺寸+垂直居中：wrap_content 父布局下子视图 MATCH_PARENT 会把按钮撑满整屏（v1.4.2 快照发现）
      LayoutParams shineLp = new LayoutParams(Math.round(density * 110), Math.round(density * 40), Gravity.CENTER_VERTICAL);
      addView(shine, shineLp);
    }
    setContentDescription(paid ? "已诚信付费，全部权限已开放" : "诚信付费，自愿支持开发");
    setOnClickListener(v -> { if (onClick != null) onClick.run(); });
  }

  @Override protected void onAttachedToWindow() {
    super.onAttachedToWindow();
    if (paid && motionEnabled && shine != null) startShine();
  }

  @Override protected void onDetachedFromWindow() {
    if (shineAnimator != null) {shineAnimator.cancel();shineAnimator = null;}
    super.onDetachedFromWindow();
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
      s.setTranslationX(-sw + t * (w + sw * 2f));
      s.setAlpha((float) Math.sin(Math.PI * t) * 0.9f);
    });
    shineAnimator.start();
  }
}
