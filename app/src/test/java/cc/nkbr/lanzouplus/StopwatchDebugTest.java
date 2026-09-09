package cc.nkbr.lanzouplus;

import android.view.View;
import android.widget.LinearLayout;
import app.cash.paparazzi.Paparazzi;
import org.junit.Rule;
import org.junit.Test;

public class StopwatchDebugTest {
  @Rule public final Paparazzi paparazzi = new Paparazzi();

  @Test public void dumpChildren() {
    ToolPagePreviewTest.PreviewHost host = new ToolPagePreviewTest.PreviewHost(paparazzi.getContext(), ThemeEngine.NOVA);
    host.newRoot();
    ToolHost toolHost = new ToolHost(host);
    toolHost.renderTool("stopwatch");
    LinearLayout body = toolHost.toolBody;
    System.out.println("DFWX body children=" + (body == null ? -1 : body.getChildCount()));
    if (body != null) for (int i = 0; i < body.getChildCount(); i++) {
      View c = body.getChildAt(i);
      c.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
      System.out.println("DFWX child " + i + " " + c.getClass().getSimpleName() + " h=" + c.getMeasuredHeight() + " visible=" + (c.getVisibility() == View.VISIBLE));
    }
    paparazzi.snapshot(host.rootView, "stopwatch-debug");
  }
}
