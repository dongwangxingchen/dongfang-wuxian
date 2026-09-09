package cc.nkbr.lanzouplus;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.view.View;
import android.view.animation.LinearInterpolator;

/** 黑曜骨架屏:与真实目录卡片同构的占位块 + 紫光扫过动画。
 *  理念二改自 skydoves/AndroidVeil(shimmer 骨架屏,Apache-2.0),纯自绘零依赖。
 *  数据到达时由宿主做交叉淡化,骨架与内容形状一致,视觉上"一气呵成"。 */
final class HeiYaoSkeleton extends View {
  private final Paint blockPaint=new Paint(Paint.ANTI_ALIAS_FLAG),shinePaint=new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Matrix shineMatrix=new Matrix();
  private final int blockColor,columns;
  private final float density;
  private final int cellW(int width){return (width-dp(12)*(columns-1))/Math.max(1,columns);}
  private final int iconSize,rowHeight,barH,cellPad;
  private ValueAnimator animator;
  private int rows;
  private float shineOffset;
  private LinearGradient shine;

  HeiYaoSkeleton(Context context,int blockColor,int columns,int rows){super(context);this.blockColor=blockColor;this.columns=Math.max(1,columns);this.rows=Math.max(rows,4);density=getResources().getDisplayMetrics().density;iconSize=dp(40);rowHeight=dp(72);barH=dp(13);cellPad=dp(6);setWillNotDraw(false);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);setContentDescription("目录加载中");}
  void setRows(int value){if(rows!=Math.max(value,4)){rows=Math.max(value,4);invalidate();}}
  private int dp(float v){return (int)(v*density+.5f);}
  @Override protected void onAttachedToWindow(){super.onAttachedToWindow();startShine();}
  @Override protected void onDetachedFromWindow(){super.onDetachedFromWindow();stopShine();}
  void startShine(){if(animator!=null&&animator.isRunning())return;animator=ValueAnimator.ofFloat(-1.2f,1.2f);animator.setDuration(1250);animator.setRepeatCount(ValueAnimator.INFINITE);animator.setInterpolator(new LinearInterpolator());animator.addUpdateListener(a->{shineOffset=(Float)a.getAnimatedValue();invalidate();});animator.start();}
  void stopShine(){if(animator!=null){animator.cancel();animator=null;}}
  @Override protected void onDraw(Canvas canvas){
    int w=getWidth(),h=getHeight();if(w<=0||h<=0)return;int cell=cellW(w);int rowPairs=(rows+1)/2;blockPaint.setColor(blockColor);
    for(int pair=0;pair<rowPairs;pair++){
      int y=pair*rowHeight;if(y>h)break;
      for(int col=0;col<columns;col++){
        int x=col*(cell+dp(12));if(x>=w)break;
        int iconX=x+cellPad,iconY=y+dp(8);
        canvas.drawRoundRect(iconX,iconY,iconX+iconSize,iconY+iconSize,dp(12),dp(12),blockPaint);
        float barX=iconX+iconSize+dp(12),barMaxW=Math.max(dp(24),x+cell-iconX-iconSize-dp(16));
        canvas.drawRoundRect(barX,y+dp(12),barX+barMaxW*.62f,y+dp(12)+barH,barH/2f,barH/2f,blockPaint);
        canvas.drawRoundRect(barX,y+dp(12)+barH+dp(9),barX+barMaxW*.38f,y+dp(12)+barH*2+dp(9),barH/2f,barH/2f,blockPaint);
      }
    }
    if(shine==null)shine=new LinearGradient(-w*.55f,0,w*.15f,0,new int[]{Color.TRANSPARENT,ThemeEngine.tint(ThemeEngine.active(getContext()).primary,46),Color.TRANSPARENT},new float[]{0f,.5f,1f},Shader.TileMode.CLAMP);
    shineMatrix.reset();shineMatrix.postTranslate(shineOffset*w,0);shine.setLocalMatrix(shineMatrix);shinePaint.setShader(shine);
    canvas.drawRect(0,0,w,h,shinePaint);shinePaint.setShader(null);
  }
}
