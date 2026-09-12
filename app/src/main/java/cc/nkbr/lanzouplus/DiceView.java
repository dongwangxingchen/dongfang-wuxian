package cc.nkbr.lanzouplus;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/**
 * 骰子视图（自绘，零依赖）。
 *
 * <p><b>研究依据</b>：Google 官方 Dice Roller 示例（basic-android-kotlin-compose-training-dice-roller）
 * 的核心设计是「结果用图形呈现而非文字」——见 07-研究报告/工具精修-04-随机决策-研究.md。
 * 官方用 dice_1~dice_6 图片资源；本实现改为 Canvas 点阵自绘（零资源、任意尺寸清晰、可换主题色）。
 *
 * <p>点数布局遵循标准骰子排列：
 * <pre>
 *  1: 中心           2: 左上+右下      3: 左上+中心+右下
 *  4: 四角           5: 四角+中心      6: 两列各三点
 * </pre>
 */
public class DiceView extends View {

  private final Paint face = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint pip = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final RectF rect = new RectF();
  private int value = 1;
  private int radiusDp = 18;
  private float density = 1f;

  public DiceView(Context context, int faceColor, int pipColor, int radiusDp) {
    super(context);
    this.density = context.getResources().getDisplayMetrics().density;
    this.radiusDp = radiusDp;
    face.setStyle(Paint.Style.FILL);
    face.setColor(faceColor);
    pip.setStyle(Paint.Style.FILL);
    pip.setColor(pipColor);
    setContentDescription("骰子，点数 " + value);
  }

  /** 设置点数（1~6）并重绘 */
  public void setValue(int v) {
    value = Math.max(1, Math.min(6, v));
    setContentDescription("骰子，点数 " + value);
    invalidate();
  }

  public int value() {return value;}

  public void setFaceColor(int color) {face.setColor(color);invalidate();}
  public void setPipColor(int color) {pip.setColor(color);invalidate();}

  private float dp(float v) {return v * density;}

  @Override protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    float w = getWidth(), h = getHeight();
    float size = Math.min(w, h);
    float left = (w - size) / 2f, top = (h - size) / 2f;
    float r = dp(radiusDp);
    rect.set(left, top, left + size, top + size);
    canvas.drawRoundRect(rect, r, r, face);

    // 点阵位置（相对 0~1 的归一化坐标）
    float q1 = 0.28f, q2 = 0.5f, q3 = 0.72f;// 左/中/右
    float pipR = size * 0.085f;
    float[][] points = pipLayout(value, q1, q2, q3);
    for (float[] p : points) {
      canvas.drawCircle(left + size * p[0], top + size * p[1], pipR, pip);
    }
  }

  /** 各点数的点阵坐标（标准骰子布局） */
  private static float[][] pipLayout(int v, float q1, float q2, float q3) {
    switch (v) {
      case 1: return new float[][]{{q2, q2}};
      case 2: return new float[][]{{q1, q1}, {q3, q3}};
      case 3: return new float[][]{{q1, q1}, {q2, q2}, {q3, q3}};
      case 4: return new float[][]{{q1, q1}, {q3, q1}, {q1, q3}, {q3, q3}};
      case 5: return new float[][]{{q1, q1}, {q3, q1}, {q2, q2}, {q1, q3}, {q3, q3}};
      default: return new float[][]{// 6：两列各三点
          {q1, q1}, {q1, q2}, {q1, q3},
          {q3, q1}, {q3, q2}, {q3, q3}};
    }
  }

  /** 硬币视图（自绘圆形 + 正/反标识） */
  public static class CoinView extends View {
    private final Paint circle = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean heads = true;
    private final String headsText, tailsText;

    public CoinView(Context context, int coinColor, int textColor, String headsText, String tailsText) {
      super(context);
      this.headsText = headsText;
      this.tailsText = tailsText;
      circle.setStyle(Paint.Style.FILL);
      circle.setColor(coinColor);
      textPaint.setColor(textColor);
      textPaint.setTextAlign(Paint.Align.CENTER);
      textPaint.setFakeBoldText(true);
      setContentDescription("硬币，" + headsText);
    }

    public void setHeads(boolean v) {
      heads = v;
      setContentDescription("硬币，" + (heads ? headsText : tailsText));
      invalidate();
    }

    public boolean isHeads() {return heads;}

    @Override protected void onDraw(Canvas canvas) {
      super.onDraw(canvas);
      float w = getWidth(), h = getHeight();
      float size = Math.min(w, h);
      float cx = w / 2f, cy = h / 2f, r = size / 2f - size * 0.04f;
      canvas.drawCircle(cx, cy, r, circle);
      String label = heads ? headsText : tailsText;
      textPaint.setTextSize(size * 0.42f);
      Paint.FontMetrics fm = textPaint.getFontMetrics();
      float baseline = cy - (fm.ascent + fm.descent) / 2f;
      canvas.drawText(label, cx, baseline, textPaint);
    }
  }
}
