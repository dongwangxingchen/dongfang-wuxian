package cc.nkbr.lanzouplus;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.*;
import android.os.Build;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.CompoundButton;

final class LumaSwitch extends CompoundButton{
  private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
  private final RectF rect=new RectF();
  private float progress;
  private ValueAnimator animator;
  LumaSwitch(Context context){super(context);setButtonDrawable(null);setMinWidth(dp(54));setMinHeight(dp(34));setPadding(0,0,0,0);setLayerType(LAYER_TYPE_SOFTWARE,null);progress=isChecked()?1f:0f;setClickable(true);}
  @Override public void setChecked(boolean checked){boolean changed=checked!=isChecked();super.setChecked(checked);float target=checked?1f:0f;if(changed&&getWindowToken()!=null&&Build.VERSION.SDK_INT>=11){
    // v1.5.0 苹果开关（研究 R2 规格表#11）：knob 250ms (0.25,0.85,0.3,1.05) ζ≈0.7 弱过冲；legacy=Material 规整（190ms 线性）
    if(animator!=null)animator.cancel();
    boolean apple=ThemeEngine.isApple(getContext());
    animator=ValueAnimator.ofFloat(progress,target);
    animator.setDuration(apple?250:190);
    if(apple)animator.setInterpolator(new android.view.animation.PathInterpolator(0.25f,0.85f,0.3f,1.05f));
    else animator.setInterpolator(new LinearInterpolator());
    animator.addUpdateListener(a->{progress=(Float)a.getAnimatedValue();invalidate();});animator.start();}else{progress=target;invalidate();}}
  @Override protected void onMeasure(int widthSpec,int heightSpec){setMeasuredDimension(resolveSize(dp(54),widthSpec),resolveSize(dp(34),heightSpec));}
  @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);ThemeEngine.Design d=ThemeEngine.active(getContext());
    // v1.4.0「高级苹果」：on 轨 = iOS systemGreen #34C759（official），off 轨浅灰填充——这是苹果开关最强的身份识别；legacy 维持主题主色
    boolean apple=ThemeEngine.isApple(getContext());
    int on=apple?0xFF34C759:d.primary,off=apple?0xFFE9E9EB:d.border,offStroke=apple?0xFFD1D1D6:d.muted,thumbOff=apple?0xFFFFFFFF:d.muted,thumbOn=d.surface;
    float p=isEnabled()?progress:progress*.45f,alpha=isEnabled()?1f:.48f;int w=getWidth(),h=getHeight();float trackH=dp(28),trackW=Math.min(w-dp(2),dp(52)),left=(w-trackW)/2f,top=(h-trackH)/2f;rect.set(left,top,left+trackW,top+trackH);paint.setStyle(Paint.Style.FILL);paint.setColor(mix(off,on,p));paint.setAlpha((int)(255*alpha));canvas.drawRoundRect(rect,trackH/2f,trackH/2f,paint);
    if(!apple){paint.setAlpha((int)(255*alpha));paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(1));paint.setColor(mix(offStroke,on,p));canvas.drawRoundRect(rect,trackH/2f,trackH/2f,paint);}
    paint.setStyle(Paint.Style.FILL);float r=dp(10)+dp(1.5f)*p,cx=left+dp(14)+p*(trackW-dp(28)),cy=h/2f;paint.setShadowLayer(dp(1.5f),0,dp(.8f),Color.argb(90,0,0,0));paint.setColor(mix(thumbOff,thumbOn,p));canvas.drawCircle(cx,cy,r,paint);paint.clearShadowLayer();}
  private int mix(int a,int b,float t){t=Math.max(0f,Math.min(1f,t));return Color.argb((int)(Color.alpha(a)+(Color.alpha(b)-Color.alpha(a))*t),(int)(Color.red(a)+(Color.red(b)-Color.red(a))*t),(int)(Color.green(a)+(Color.green(b)-Color.green(a))*t),(int)(Color.blue(a)+(Color.blue(b)-Color.blue(a))*t));}
  private int dp(float v){return(int)(v*getResources().getDisplayMetrics().density+.5f);} }
