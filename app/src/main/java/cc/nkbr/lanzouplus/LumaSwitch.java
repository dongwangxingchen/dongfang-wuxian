package cc.nkbr.lanzouplus;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.os.Build;
import android.view.animation.PathInterpolator;
import android.widget.CompoundButton;

/** T5-S §9.1 M3 规格重画：轨道 52×32、handle 关 16/开 24/按 28 三态呼吸（变化 ≥25% 可感阈值）、
 *  按压主色低 alpha 状态层、去 shadowLayer 保 HARDWARE（§9.5-4）、关态描边补层次、M3 emphasized 曲线替线性。 */
final class LumaSwitch extends CompoundButton{
  private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
  private final RectF rect=new RectF();
  private float progress;
  private ValueAnimator animator;
  LumaSwitch(Context context){super(context);setButtonDrawable(null);setMinWidth(dp(54));setMinHeight(dp(36));setPadding(0,0,0,0);setClickable(true);progress=isChecked()?1f:0f;}
  @Override public void setChecked(boolean checked){boolean changed=checked!=isChecked();super.setChecked(checked);float target=checked?1f:0f;if(changed&&getWindowToken()!=null&&Build.VERSION.SDK_INT>=11){
    if(animator!=null)animator.cancel();
    animator=ValueAnimator.ofFloat(progress,target);
    animator.setDuration(220);
    animator.setInterpolator(new PathInterpolator(0.2f,0f,0f,1f));// M3 emphasized
    animator.addUpdateListener(a->{progress=(Float)a.getAnimatedValue();invalidate();});animator.start();}else{progress=target;invalidate();}}
  @Override protected void drawableStateChanged(){super.drawableStateChanged();invalidate();// 按压态呼吸需要重绘
  }
  @Override protected void onMeasure(int widthSpec,int heightSpec){setMeasuredDimension(resolveSize(dp(54),widthSpec),resolveSize(dp(36),heightSpec));}
  @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);ThemeEngine.Design d=ThemeEngine.active(getContext());
    int on=d.primary,off=d.border,offStroke=d.muted,thumbOff=d.muted,thumbOn=d.surface;
    float p=isEnabled()?progress:progress*.45f,alpha=isEnabled()?1f:.48f;int w=getWidth(),h=getHeight();float trackH=dp(32),trackW=Math.min(w-dp(2),dp(52)),left=(w-trackW)/2f,top=(h-trackH)/2f;rect.set(left,top,left+trackW,top+trackH);
    paint.setStyle(Paint.Style.FILL);paint.setColor(mix(off,on,p));paint.setAlpha((int)(255*alpha));canvas.drawRoundRect(rect,trackH/2f,trackH/2f,paint);
    if(isPressed()&&isEnabled()){// 状态层:主色低 alpha,代替阴影(§9.2-4)
      paint.setColor(on);paint.setAlpha((int)(255*.14f));canvas.drawRoundRect(rect,trackH/2f,trackH/2f,paint);}
    paint.setAlpha((int)(255*alpha));paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(1));paint.setColor(mix(offStroke,on,p));canvas.drawRoundRect(rect,trackH/2f,trackH/2f,paint);
    paint.setStyle(Paint.Style.FILL);float handleD=dp(16)+dp(8)*p;if(isPressed()&&isEnabled())handleD=Math.min(dp(28),handleD+dp(4));// M3 三态呼吸 16/24/28
    float cx=left+dp(4)+handleD/2f+p*(trackW-handleD-dp(8)),cy=h/2f;
    paint.setAlpha((int)(255*alpha));paint.setColor(mix(thumbOff,thumbOn,p));canvas.drawCircle(cx,cy,handleD/2f,paint);
    if(p<.5f&&!(isPressed()&&isEnabled())){// 关态描边(§9.5-5)
      paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(1));paint.setColor(mix(offStroke,on,p));canvas.drawCircle(cx,cy,handleD/2f-dp(.5f),paint);paint.setStyle(Paint.Style.FILL);}}
  private int mix(int a,int b,float t){t=Math.max(0f,Math.min(1f,t));return Color.argb((int)(Color.alpha(a)+(Color.alpha(b)-Color.alpha(a))*t),(int)(Color.red(a)+(Color.red(b)-Color.red(a))*t),(int)(Color.green(a)+(Color.green(b)-Color.green(a))*t),(int)(Color.blue(a)+(Color.blue(b)-Color.blue(a))*t));}
  private int dp(float v){return(int)(v*getResources().getDisplayMetrics().density+.5f);} }
