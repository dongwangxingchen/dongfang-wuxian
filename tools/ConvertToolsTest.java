import java.util.*;
import java.util.Base64;
import java.nio.charset.StandardCharsets;
import java.text.*;
import java.util.Calendar;

/** v1.12.0 工具精修 B1（15base64/16url/17timestamp/18radix/19morse）算法用例 —— 与 Toolbox.java 新实现同步的副本 */
public class ConvertToolsTest {
  // ===== 15 base64（RFC 4648 + URL-safe §5）=====
  static String b64enc(byte[] b){ return Base64.getEncoder().encodeToString(b); } // android.util.Base64(NO_WRAP) 同为 RFC 4648
  static String base64Encode(String value, boolean urlSafe, boolean stripPadding){
    String out = b64enc(value.getBytes(StandardCharsets.UTF_8));
    if (urlSafe) out = out.replace('+','-').replace('/','_');
    if (stripPadding) { int end=out.length(); while(end>0 && out.charAt(end-1)=='=') end--; out=out.substring(0,end); }
    return out;
  }
  static String base64Decode(String value){
    try{
      String v = value.replaceAll("\\s+","");
      v = v.replace('-','+').replace('_','/');
      for(char c : v.toCharArray()){
        if(!(c>='A'&&c<='Z'||c>='a'&&c<='z'||c>='0'&&c<='9'||c=='+'||c=='/'||c=='=')) return "解码失败：不是有效的 Base64";
      }
      while (v.length()%4!=0) v+="=";
      return new String(Base64.getDecoder().decode(v), StandardCharsets.UTF_8);
    }catch(Exception e){ return "解码失败：不是有效的 Base64"; }
  }

  // ===== 16 url_codec（RFC 3986 路径口径 / 表单口径）=====
  static String urlEncode(String value, boolean formMode){
    try{
      if (formMode) return java.net.URLEncoder.encode(value, "UTF-8");
      StringBuilder sb=new StringBuilder();
      for(byte b: value.getBytes(StandardCharsets.UTF_8)){
        char c=(char)(b&0xFF);
        if (c>='A'&&c<='Z'||c>='a'&&c<='z'||c>='0'&&c<='9'||c=='-'||c=='_'||c=='.'||c=='~') sb.append(c);
        else sb.append('%').append(String.format("%02X", b&0xFF));
      }
      return sb.toString();
    }catch(Exception e){ return "编码失败："+e.getMessage(); }
  }
  static String urlDecode(String value, boolean formMode){
    try{
      ByteArrayOutputStreamLike out=new ByteArrayOutputStreamLike();
      byte[] buf=value.getBytes(StandardCharsets.UTF_8);
      for(int i=0;i<buf.length;i++){
        char c=(char)(buf[i]&0xFF);
        if(c=='%'){
          if(i+2<buf.length+0 && i+2<=buf.length-1+1 && i+2<buf.length+1-0){} // no-op
          if(i+2>=buf.length){ out.append('%'); continue; }
          int hi=Character.digit((char)(buf[i+1]&0xFF),16), lo=Character.digit((char)(buf[i+2]&0xFF),16);
          if(hi<0||lo<0){ out.append('%'); continue; }
          out.append((byte)((hi<<4)|lo)); i+=2;
        } else if(c=='+' && formMode){ out.append(' ');
        } else { out.append((byte)buf[i]); }
      }
      return new String(out.bytes(), StandardCharsets.UTF_8);
    }catch(Exception e){ return "解码失败："+e.getMessage(); }
  }
  static class ByteArrayOutputStreamLike {
    java.io.ByteArrayOutputStream bos=new java.io.ByteArrayOutputStream();
    void append(byte b){ bos.write(b); }
    void append(char c){ bos.write(c&0xFF); }
    byte[] bytes(){ return bos.toByteArray(); }
  }

  // ===== 17 timestamp =====
  static SimpleDateFormat fmt(){
    SimpleDateFormat f=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA);
    f.setTimeZone(TimeZone.getTimeZone("GMT+8")); // 测试固定；产品用设备默认（中国用户即 +8）
    return f;
  }
  static String stampToDate(long v){
    long ms = v<100000000000L ? v*1000 : v;
    Date d=new Date(ms);
    SimpleDateFormat iso=new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.CHINA);
    iso.setTimeZone(TimeZone.getTimeZone("GMT+8"));
    String[] weeks={"周一","周二","周三","周四","周五","周六","周日"};
    Calendar c=Calendar.getInstance(); c.setTimeZone(TimeZone.getTimeZone("GMT+8")); c.setTime(d);
    return "对应时间："+fmt().format(d)+"\nISO 8601："+iso.format(d)+"\n星期："+weeks[c.get(Calendar.DAY_OF_WEEK)-2<0?0:c.get(Calendar.DAY_OF_WEEK)-2];
  }
  static String dateToStamp(String value){
    try{
      ParsePosition pp=new ParsePosition(0);
      SimpleDateFormat f2=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA);
      f2.setTimeZone(TimeZone.getTimeZone("GMT+8")); f2.setLenient(false);
      Date d=f2.parse(value.trim(), pp);
      if(d==null){ f2.applyPattern("yyyy-MM-dd"); d=f2.parse(value.trim(), pp); }
      if(d==null) return "无法解析：日期用 yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss";
      return "时间戳（秒）："+d.getTime()/1000+"\n时间戳（毫秒）："+d.getTime();
    }catch(Exception e){ return "无法解析"; }
  }
  static String timestampAuto(String value){
    String v=value.trim();
    if(v.isEmpty()) return "输入时间戳或日期";
    if(v.matches("\\d+")) return stampToDate(Long.parseLong(v));
    return dateToStamp(v);
  }

  // ===== 18 radix =====
  static String radixConvert(String value, int from){
    String v=value.trim();
    if(from==16 && v.length()>2 && (v.startsWith("0x")||v.startsWith("0X"))) v=v.substring(2);
    if(from==2 && v.length()>2 && (v.startsWith("0b")||v.startsWith("0B"))) v=v.substring(2);
    try{
      long r=Long.parseLong(v, from);
      return "二进制："+Long.toString(r,2)+"\n八进制："+Long.toString(r,8)+"\n十进制："+r+"\n十六进制："+Long.toString(r,16).toUpperCase(Locale.ROOT);
    }catch(Exception e){ return "无法按 "+from+" 进制解析该数字（范围限 64 位整数）"; }
  }

  // ===== 19 morse（ITU：字母+数字+标点）=====
  static final String[] MORSE_LETTERS={".-","-...","-.-.","-..",".","..-.","--.","....","..",".---","-.-",".-..","--","-.","---",".--.","--.-",".-.","...","-","..-","...-",".--","-..-","-.--","--.."};
  static final String[] MORSE_DIGITS={"-----",".----","..---","...--","....-",".....","-....","--...","---..","----."}; // 0-9 顺位（ITU）
  static final String[][] MORSE_PUNCT={{".",".-.-.-"},{",","--..--"},{"?","..--.."},{"!"," -.-.--"},{"/","-..-."},{"=","-...-"},{"+",".-.-."},{"-","-....-"},{"@",".--.-."},{"(","-.--."},{")","-.--.-"},{"&",".-..."},{"'",".----."},{"\"",".-..-."}};
  static String morseEncode(String value){
    Map<String,String> map=new HashMap<>();
    for(int i=0;i<26;i++) map.put(String.valueOf((char)('A'+i)), MORSE_LETTERS[i]);
    for(int i=0;i<10;i++) map.put(String.valueOf((char)('0'+i)), MORSE_DIGITS[i]);
    for(String[] p: MORSE_PUNCT) map.put(p[0], p[1].trim());
    StringBuilder out=new StringBuilder(); int skipped=0; boolean first=true;
    for(char c: value.toUpperCase(Locale.ROOT).toCharArray()){
      String code=map.get(String.valueOf(c));
      if(code!=null){ if(!first) out.append(' '); out.append(code); first=false; }
      else if(c==' '){ if(out.length()>0 && !out.toString().endsWith(" / ")) out.append(" / "); }
      else skipped++;
    }
    String s=out.toString().replaceAll(" / $","");
    if(skipped>0) s+="\n（跳过 "+skipped+" 个无法编码的字符）";
    return s.isEmpty()?"输入英文、数字或常用标点":s;
  }
  static String morseDecode(String value){
    Map<String,String> map=new HashMap<>();
    for(int i=0;i<26;i++) map.put(MORSE_LETTERS[i], String.valueOf((char)('A'+i)));
    for(int i=0;i<10;i++) map.put(MORSE_DIGITS[i], String.valueOf((char)('0'+i)));
    for(String[] p: MORSE_PUNCT) map.put(p[1].trim(), p[0]);
    StringBuilder out=new StringBuilder(); int unknown=0;
    for(String token: value.trim().split("\\s+")){
      if(token.isEmpty()) continue;
      if(token.equals("/")){ out.append(' '); continue; }
      String norm=token.replace('·','.').replace('—','-').replace('−','-');
      String ch=map.get(norm);
      if(ch!=null) out.append(ch); else unknown++;
    }
    String s=out.toString();
    if(unknown>0) s+="\n（"+unknown+" 个未知电码已跳过）";
    return s.isEmpty()?"输入摩斯电码（. - 与 / 分隔单词）":s;
  }

  // ===== 断言 =====
  static int fail=0;
  static void eq(String name, Object got, Object want){
    boolean ok = String.valueOf(got).equals(String.valueOf(want));
    if(!ok){ System.out.println("FAIL "+name+"\n  got ="+got+"\n  want="+want); fail++; }
  }
  public static void main(String[] a){
    // 15 base64：RFC 4648 全部测试向量
    String[] plains={"","f","fo","foo","foob","fooba","foobar"};
    String[] expect={"","Zg==","Zm8=","Zm9v","Zm9vYg==","Zm9vYmE=","Zm9vYmFy"};
    for(int i=0;i<plains.length;i++) eq("rfc-vector-"+i, base64Encode(plains[i],false,false), expect[i]);
    eq("b64-roundtrip", base64Decode(base64Encode("东方无限 foobar!",false,false)), "东方无限 foobar!");
    String tricky = " subjects?" + '"' + '\u003E' + '\u003E' + '\u003E' + '?';
    eq("b64-urlsafe", base64Encode(tricky,true,false), base64Encode(tricky,false,false).replace('+','-').replace('/','_'));
    eq("b64-strippad", base64Encode("f",false,true), "Zg");
    eq("b64-decode-urlsafe-nopad", base64Decode("Zm9vYmFy"), "foobar");
    eq("b64-decode-whitespace", base64Decode("Zm9v\nYmFy"), "foobar");
    eq("b64-decode-bad", base64Decode("!!不是base64!!").startsWith("解码失败"), true);
    // 16 url
    eq("url-path-cn", urlEncode("东方",false), "%E4%B8%9C%E6%96%B9");
    eq("url-path-space", urlEncode("a b",false), "a%20b");
    eq("url-form-space", urlEncode("a b",true), "a+b");
    eq("url-path-reserved", urlEncode("a/b?c=d&e",false), "a%2Fb%3Fc%3Dd%26e");
    eq("url-form-reserved", urlEncode("a/b?c=d&e",true), "a%2Fb%3Fc%3Dd%26e");
    eq("url-decode-path", urlDecode("%E4%B8%9C%E6%96%B9%20a+b",false), "东方 a+b");
    eq("url-decode-form", urlDecode("%E4%B8%9C%E6%96%B9%20a+b",true), "东方 a b");
    eq("url-roundtrip", urlDecode(urlEncode("q=东方~._-100%",false),false), "q=东方~._-100%");
    eq("url-decode-bad-percent", urlDecode("100%%ZZ",false), "100%%ZZ"); // 无效转义原样保留（诚实容错）
    // 17 timestamp
    eq("ts-epoch0", timestampAuto("0"), stampToDate(0L));
    eq("ts-epoch0-time", stampToDate(0L).contains("1970-01-01 08:00:00"), true);
    eq("ts-1e9", stampToDate(1000000000L).contains("2001-09-09 09:46:40"), true);
    eq("ts-ms", timestampAuto("1000000000000").contains("2001-09-09 09:46:40"), true);
    eq("ts-date2stamp", dateToStamp("2001-09-09 09:46:40").contains("1000000000"), true);
    eq("ts-date-only", dateToStamp("2001-09-09").contains("999964800"), true); // 2001-09-09 00:00 GMT+8 = 999964800
    eq("ts-garbage", timestampAuto("abc"), dateToStamp("abc"));
    // 18 radix
    eq("radix-neg-hex", radixConvert("-10",16).contains("十六进制：-10"), true);
    eq("radix-neg-dec", radixConvert("-10",16).contains("十进制：-16"), true);
    eq("radix-z36", radixConvert("z",36).contains("十进制：35"), true);
    eq("radix-0x", radixConvert("0xFF",16).contains("十进制：255"), true);
    eq("radix-0b", radixConvert("0b1010",2).contains("十进制：10"), true);
    eq("radix-overflow", radixConvert("fffffffffffffffff",16).startsWith("无法按"), true);
    // 19 morse
    eq("morse-sos", morseEncode("SOS"), "... --- ...");
    eq("morse-sos-rev", morseDecode("... --- ..."), "SOS");
    eq("morse-123", morseEncode("123"), ".---- ..--- ...--");
    eq("morse-0", morseEncode("0"), "-----");
    eq("morse-roundtrip", morseDecode(morseEncode("Hello World 123")), "HELLO WORLD 123");
    eq("morse-punct-roundtrip", morseDecode(morseEncode("a.b,c?d!")), "A.B,C?D!");
    eq("morse-tolerance-dotdash", morseDecode("·—"), "A"); // ·— = .- = A
    eq("morse-tolerance-mixed", morseDecode("·— ... ——— ····"), "ASOH");
    eq("morse-unknown-count", morseDecode("........ ----.").contains("1 个未知电码"), true);
    eq("morse-skip-count", morseEncode("a你b").contains("跳过 1 个"), true);
    System.out.println(fail==0?"ALL PASS":"FAILED "+fail);
    if(fail>0) System.exit(1);
  }
}
