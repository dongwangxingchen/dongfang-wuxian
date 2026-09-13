package cc.nkbr.lanzouplus;

import android.view.View;
import android.widget.LinearLayout;
import app.cash.paparazzi.Paparazzi;
import org.junit.Rule;
import org.junit.Test;

public class StopwatchLayoutTest {
  @Rule public final Paparazzi paparazzi = new Paparazzi();

  @Test public void dumpPositions() {
    ToolPagePreviewTest.PreviewHost host = new ToolPagePreviewTest.PreviewHost(paparazzi.getContext(), ThemeEngine.LEGACY);
    host.newRoot();
    ToolHost toolHost = new ToolHost(host);
    toolHost.renderTool("stopwatch");
    host.rootView.measure(
        View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(2400, View.MeasureSpec.EXACTLY));
    host.rootView.layout(0, 0, 1080, 2400);
    LinearLayout body = toolHost.toolBody;
    System.out.println("DFWX body w=" + body.getWidth() + " h=" + body.getHeight());
    for (int i = 0; i < body.getChildCount(); i++) {
      View c = body.getChildAt(i);
      System.out.println("DFWX child " + i + " " + c.getClass().getSimpleName()
          + " y=" + c.getY() + " w=" + c.getWidth() + " h=" + c.getHeight()
          + " vis=" + c.getVisibility());
    }
    System.out.println("DFWX scroll=" + body.getParent().getClass().getName()
        + " scrollH=" + ((android.view.ViewGroup) body.getParent()).getHeight());
  }
}
