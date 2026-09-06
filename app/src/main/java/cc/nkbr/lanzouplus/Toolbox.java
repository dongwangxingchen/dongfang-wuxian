package cc.nkbr.lanzouplus;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;

/** 工具箱:注册表即目录(名称/说明/关键词),后续 AI 对接时可直接枚举 toolCatalogJson()。
 *  全部为纯 Java 原生实现,零第三方依赖;每个工具一屏:输入 → 动作 → 输出 → 复制。 */
final class Toolbox {
  static final String[][] TOOLS = {
    {"img_compress","图片压缩","选图后按质量压缩并保存到相册","图片 压缩 照片 变小 省空间 相册"},
    {"text_stats","文本统计","统计字符、汉字、行数，支持去重去空行排序","文本 字数 统计 去重 排序"},
    {"base64","Base64 编解码","文本与 Base64 互转","base64 编码 解码 转换"},
    {"url_codec","URL 编解码","文本与 URL 百分号编码互转","url 编码 解码 转义"},
    {"hash","哈希计算","计算 MD5 / SHA-1 / SHA-256 摘要","哈希 md5 sha1 sha256 校验 摘要"},
    {"json","JSON 格式化","美化或压缩 JSON 并校验合法性","json 格式化 校验 压缩 美化"},
    {"timestamp","时间戳转换","Unix 时间戳与日期时间互转","时间戳 日期 转换 unix 秒"},
    {"radix","进制转换","二 / 八 / 十 / 十六进制互转","进制 二进制 十六进制 转换"},
    {"uuid","UUID 生成","批量生成 UUID v4 并复制","uuid 唯一标识 生成"},
    {"password","密码生成","按长度与字符类型生成随机密码","密码 随机 生成 安全 强密码"},
    {"regex","正则测试","测试正则表达式并列出全部匹配","正则 表达式 regex 匹配 测试"}
  };
  static String toolCatalogJson() {
    JSONArray array = new JSONArray();
    for (String[] tool : TOOLS) {try{array.put(new JSONObject().put("id", tool[0]).put("name", tool[1]).put("description", tool[2]).put("keywords", tool[3]));}catch(Exception ignored){}}
    try{return new JSONObject().put("tools", array).toString();}catch(Exception e){return "{\"tools\":[]}";}
  }
  static String toolName(String id) {for (String[] t : TOOLS) if (t[0].equals(id)) return t[1];return "";}
  static String toolDesc(String id) {for (String[] t : TOOLS) if (t[0].equals(id)) return t[2];return "";}
  static String textStats(String value) {
    int chars = value == null ? 0 : value.length(), han = 0, words = 0;
    boolean inWord = false;
    for (int i = 0; i < chars; i++) {
      char c = value.charAt(i);
      if (c >= 0x4E00 && c <= 0x9FFF) han++;
      boolean wordChar = !Character.isWhitespace(c) && Character.isLetterOrDigit(c);
      if (wordChar && !inWord) words++;
      inWord = wordChar;
    }
    int lines = value == null || value.isEmpty() ? 0 : value.split("\n", -1).length;
    return "字符 " + chars + " · 汉字 " + han + " · 行 " + lines + " · 词 " + words;
  }
  static String dedupeLines(String value, boolean sort, boolean dropEmpty) {
    TreeSet<String> unique = new TreeSet<>();
    List<String> keep = new ArrayList<>();
    for (String line : value.split("\n", -1)) {
      String trimmed = line.trim();
      if (dropEmpty && trimmed.isEmpty()) continue;
      if (unique.add(trimmed)) keep.add(trimmed);
    }
    if (sort) return String.join("\n", unique);
    return String.join("\n", keep);
  }
  static String base64(boolean encode, String value) {
    try {return encode ? android.util.Base64.encodeToString(value.getBytes(StandardCharsets.UTF_8), android.util.Base64.NO_WRAP) : new String(android.util.Base64.decode(value.trim(), android.util.Base64.DEFAULT), StandardCharsets.UTF_8);}
    catch (Exception e) {return "解码失败：不是有效的 Base64";}
  }
  static String url(boolean encode, String value) {
    try {return encode ? java.net.URLEncoder.encode(value, "UTF-8") : java.net.URLDecoder.decode(value.trim(), "UTF-8");}
    catch (Exception e) {return "转换失败：" + e.getMessage();}
  }
  static String hashes(String value) {
    StringBuilder out = new StringBuilder();
    for (String algo : new String[]{"MD5", "SHA-1", "SHA-256"}) {
      try {byte[] digest = MessageDigest.getInstance(algo).digest(value.getBytes(StandardCharsets.UTF_8));StringBuilder hex = new StringBuilder();for (byte b : digest) hex.append(String.format("%02x", b));out.append(algo.replace("-", "")).append("  ").append(hex).append("\n");}
      catch (Exception e) {out.append(algo).append("  计算失败\n");}
    }
    return out.toString().trim();
  }
  static String json(boolean pretty, String value) {
    try {return pretty ? new JSONObject(value).toString(2) : new JSONObject(value).toString();}
    catch (Exception ignored) {}
    try {return pretty ? new JSONArray(value).toString(2) : new JSONArray(value).toString();}
    catch (Exception e) {return "JSON 不合法：" + e.getMessage();}
  }
  static String timestampToDate(String seconds) {
    try {long value = Long.parseLong(seconds.trim());if (seconds.trim().length() >= 13) value /= 1000L;return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.CHINA).format(new java.util.Date(value * 1000L));}
    catch (Exception e) {return "格式：10 位秒级时间戳";}
  }
  static String dateToTimestamp(String date) {
    try {java.text.SimpleDateFormat format = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.CHINA);format.setTimeZone(java.util.TimeZone.getDefault());return String.valueOf(format.parse(date.trim()).getTime() / 1000L);}
    catch (Exception e) {return "格式：2026-01-01 12:00:00";}
  }
  static String radix(String value, int fromBase) {
    try {long number = Long.parseLong(value.trim(), fromBase);return "二进制 " + Long.toString(number, 2) + "\n八进制 " + Long.toString(number, 8) + "\n十进制 " + Long.toString(number, 10) + "\n十六进制 " + Long.toString(number, 16).toUpperCase();}
    catch (Exception e) {return "无法按 " + fromBase + " 进制解析";}
  }
  static String uuidBatch(int count) {
    StringBuilder out = new StringBuilder();
    for (int i = 0; i < Math.max(1, Math.min(50, count)); i++) out.append(UUID.randomUUID().toString()).append('\n');
    return out.toString().trim();
  }
  static String generatePassword(int length, boolean upper, boolean lower, boolean digits, boolean symbols) {
    String pool = (upper ? "ABCDEFGHJKLMNPQRSTUVWXYZ" : "") + (lower ? "abcdefghijkmnpqrstuvwxyz" : "") + (digits ? "23456789" : "") + (symbols ? "!@#$%^&*_-+=?" : "");
    if (pool.isEmpty()) return "至少选择一种字符";
    SecureRandom random = new SecureRandom();
    int size = Math.max(6, Math.min(64, length));
    StringBuilder out = new StringBuilder();
    for (int i = 0; i < size; i++) out.append(pool.charAt(random.nextInt(pool.length())));
    return out.toString();
  }
  static String regex(String pattern, String text) {
    if (pattern == null || pattern.trim().isEmpty()) return "先输入正则表达式";
    try {
      Matcher matcher = Pattern.compile(pattern.trim()).matcher(text == null ? "" : text);
      StringBuilder out = new StringBuilder();
      int count = 0;
      while (matcher.find()) {
        count++;
        if (count > 200) {out.append("…（已截断）");break;}
        out.append('#').append(count).append("  ").append(matcher.group()).append("  @").append(matcher.start());
        if (matcher.groupCount() > 0) for (int g = 1; g <= matcher.groupCount(); g++) out.append("  组").append(g).append('=').append(matcher.group(g) == null ? "-" : matcher.group(g));
        out.append('\n');
      }
      if (count == 0) return "无匹配";
      out.insert(0, "匹配 " + count + " 处\n");
      return out.toString().trim();
    } catch (Exception e) {return "正则有误：" + e.getMessage();}
  }
}
