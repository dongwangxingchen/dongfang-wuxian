package cc.nkbr.lanzouplus;

import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
/** v1.2.2:零依赖 Markdown 轻渲染 → Spannable(代码块/行内码/粗体/标题/列表/链接与裸 URL 着色)。 */
final class AiMarkdown {
  private static final int VIOLET = 0xFFA78BFA;
  private static final Pattern BOLD = Pattern.compile("\\*\\*(.+?)\\*\\*");
  private static final Pattern CODE = Pattern.compile("`([^`\\n]+)`");
  private static final Pattern URL = Pattern.compile("(https?://[A-Za-z0-9._~:/?#@!$&'()*+,;=%\\[\\]-]+)");
  private static final Pattern MD_LINK = Pattern.compile("\\[([^\\]]+)\\]\\(([^)\\s]+)\\)");
  private static final Pattern HEADING = Pattern.compile("^(#{1,4})\\s+(.*)$");
  private static final Pattern BULLET = Pattern.compile("^\\s*[-*•]\\s+(.*)$");
  private static final Pattern NUMBERED = Pattern.compile("^\\s*(\\d{1,3})[.)]\\s+(.*)$");
  static CharSequence render(String raw) {
    SpannableStringBuilder out = new SpannableStringBuilder();
    if (raw == null) return out;
    String[] lines = raw.replace("\r\n", "\n").split("\n", -1);
    boolean inCode = false;
    StringBuilder codeBuf = new StringBuilder();
    for (String line : lines) {
      if (line.trim().startsWith("```")) {
        if (inCode) { appendCode(out, codeBuf.toString()); codeBuf.setLength(0); }
        inCode = !inCode;
        continue;
      }
      if (inCode) { codeBuf.append(line).append('\n'); continue; }
      Matcher h = HEADING.matcher(line);
      if (h.matches()) {
        int start = out.length();
        appendInline(out, h.group(2));
        float scale = h.group(1).length() >= 4 ? 1.05f : h.group(1).length() == 3 ? 1.1f : h.group(1).length() == 2 ? 1.2f : 1.35f;
        out.setSpan(new StyleSpan(Typeface.BOLD), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        out.setSpan(new RelativeSizeSpan(scale), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        out.setSpan(new ForegroundColorSpan(VIOLET), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        out.append("\n");
        continue;
      }
      Matcher b = BULLET.matcher(line);
      if (b.matches()) { out.append("•  "); appendInline(out, b.group(1)); out.append("\n"); continue; }
      Matcher n = NUMBERED.matcher(line);
      if (n.matches()) { out.append(n.group(1)).append(".  "); appendInline(out, n.group(2)); out.append("\n"); continue; }
      if (line.trim().isEmpty() && out.length() > 0 && out.charAt(out.length() - 1) == '\n') continue;
      appendInline(out, line);
      out.append("\n");
    }
    if (codeBuf.length() > 0) appendCode(out, codeBuf.toString());
    int len = out.length();
    while (len > 0 && out.charAt(len - 1) == '\n') { out.delete(len - 1, len); len--; }
    return out;
  }
  private static void appendCode(SpannableStringBuilder out, String code) {
    int start = out.length();
    out.append(code);
    if (out.charAt(out.length() - 1) != '\n') out.append('\n');
    out.setSpan(new TypefaceSpan("monospace"), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    out.setSpan(new BackgroundColorSpan(0xFF1E1B2E), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    out.setSpan(new ForegroundColorSpan(0xFFD8D2E8), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
  }
  private static void appendInline(SpannableStringBuilder out, String rawText) {
    String t = MD_LINK.matcher(rawText).find() ? MD_LINK.matcher(rawText).replaceAll("$1（$2）") : rawText;
    int base = out.length();
    out.append(t);
    Matcher m = CODE.matcher(t);
    while (m.find()) {
      out.setSpan(new TypefaceSpan("monospace"), base + m.start(1), base + m.end(1), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      out.setSpan(new BackgroundColorSpan(0xFF241F38), base + m.start(1), base + m.end(1), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
    m = BOLD.matcher(t);
    while (m.find()) out.setSpan(new StyleSpan(Typeface.BOLD), base + m.start(1), base + m.end(1), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    m = URL.matcher(t);
    while (m.find()) out.setSpan(new ForegroundColorSpan(VIOLET), base + m.start(), base + m.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
  }
}
