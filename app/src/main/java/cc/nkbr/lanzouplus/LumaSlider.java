package cc.nkbr.lanzouplus;

import android.content.Context;
import android.graphics.*;
import android.view.*;

/** T5-S §9.1 M3 原味滑条（方案 A 骨架）：4×44dp 竖条 handle + 16dp 轨道 + 6dp 间隙 + stop 圆点，零阴影；
 *  防塑料感六手法（§9.2）：轨道带主色相、handle 尺寸即形态不靠阴影、stop 点补 3:1 对比（#5C5866 实测 3.05）。 */
final class LumaSlider extends View{
  interface OnChange{void onProgress(int value,boolean fromUser);}
  private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
  private final RectF rect=new RectF();
  private int max=100,progress;private OnChange listener;
  LumaSlider(Context context){super(context);setClickable(true);}
  void setMax(int value){max=Math.max(1,value);progress=Math.min(progress,max);invalidate();}
  int getProgress(){return progress;}
  void setProgressValue(int value,boolean notify){int next=Math.max(0,Math.min(max,value));if(next==progress)return;progress=next;invalidate();if(notify&&listener!=null)listener.onProgress(progress,false);}
  void setOnChangeListener(OnChange change){listener=change;}
  @Override protected void onMeasure(int widthSpec,int heightSpec){setMeasuredDimension(resolveSize(dp(48),widthSpec),resolveSize(dp(56),heightSpec));}
  @Override protected void onDraw(Canvas canvas){ThemeEngine.Design d=ThemeEngine.active(getContext());
    float alpha=isEnabled()?1f:.42f;int w=getWidth(),h=getHeight();
    float trackH=dp(16),trackW=w-dp(6),left=dp(3),top=(h-trackH)/2f;
    float ratio=max==0?0:(float)progress/max,handleX=left+dp(2)+(trackW-dp(4))*ratio;
    paint.setStyle(Paint.Style.FILL);
    int dimTrack=mix(MainActivity.SET_LOW,d.primary,.32f);// inactive 轨=暗面混主色相（§9.2-6）
    rect.set(left,top,Math.max(left,handleX-dp(3)),top+trackH);paint.setColor(d.primary);paint.setAlpha((int)(255*alpha));canvas.drawRoundRect(rect,trackH/2f,trackH/2f,paint);// active 段
    rect.set(Math.min(w-dp(3),handleX+dp(3)),top,left+trackW,top+trackH);paint.setColor(dimTrack);canvas.drawRoundRect(rect,trackH/2f,trackH/2f,paint);// inactive 段
    paint.setColor(0xFF5C5866);paint.setAlpha((int)(255*alpha));canvas.drawCircle(left+dp(12),h/2f,dp(2),paint);canvas.drawCircle(left+trackW-dp(12),h/2f,dp(2),paint);// stop 圆点
    rect.set(handleX-dp(2),h/2f-dp(22),handleX+dp(2),h/2f+dp(22));paint.setColor(d.primary);canvas.drawRoundRect(rect,dp(2),dp(2),paint);// 竖条 handle,零阴影
  }
  @Override public boolean onTouchEvent(MotionEvent event){if(!isEnabled())return false;getParent().requestDisallowInterceptTouchEvent(true);
    switch(event.getActionMasked()){case MotionEvent.ACTION_DOWN:case MotionEvent.ACTION_MOVE:track(event.getX());return true;
      case MotionEvent.ACTION_UP:case MotionEvent.ACTION_CANCEL:track(event.getX());getParent().requestDisallowInterceptTouchEvent(false);return true;}
    return super.onTouchEvent(event);}
  private void track(float x){float trackW=getWidth()-dp(6)-dp(4);float ratio=Math.max(0f,Math.min(1f,(x-dp(3)-dp(2))/trackW));int value=Math.round(ratio*max);
    if(value!=progress){progress=value;invalidate();if(listener!=null)listener.onProgress(progress,true);}}
  private int mix(int a,int b,float t){t=Math.max(0f,Math.min(1f,t));return Color.argb((int)(Color.alpha(a)+(Color.alpha(b)-Color.alpha(a))*t),(int)(Color.red(a)+(Color.red(b)-Color.red(a))*t),(int)(Color.green(a)+(Color.green(b)-Color.green(a))*t),(int)(Color.blue(a)+(Color.blue(b)-Color.blue(a))*t));}
  private int dp(float v){return(int)(v*getResources().getDisplayMetrics().density+.5f);}}
