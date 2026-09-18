package cc.nkbr.lanzouplus;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;

/** 工具箱逻辑层：注册表即目录（分类/图标/热度），全部纯 Java/原生 API 本地实现，零服务器、零第三方依赖。
 *  UI 一律在 ToolHost；本类不含任何 android.widget 引用，便于单元化自查。 */
final class Toolbox {
  static final String CALC="calc",RULER="ruler",CALENDAR="calendar",DICE="dice",EDIT="edit",TEXT="text",COPY="copy",REFRESH="refresh",OPEN_WITH="open_with",SEARCH="search",IMAGE="image",PALETTE="palette",INFO="info",CHECK="check",TORCH="torch",AUDIO="audio",VOICE="voice",STAR="star",IDCARD="idcard",HEART="heart",QR="qr";  /** 每个图标后缀的固定色相（与暗夜紫主题和谐的柔和彩板，Chip 图标底色用 18% 透明度） */
  /** [id, 名称, 一句话说明, 关键词, 图标res后缀, 分类名, 热度] */
  static final String[][] TOOLS = {
    {"calculator","计算器","四则运算与括号，实时出结果","计算 算术 加减乘除 calculator",CALC,"常用工具","95"},
    {"unit","单位换算","长度/重量/温度/数据/速度互转","单位 换算 公里 磅 摄氏 kb mb",RULER,"常用工具","72"},
    {"datecalc","日期计算","两日期间隔天数与N天后日期","日期 天数 间隔 倒计时 推算",CALENDAR,"常用工具","60"},
    {"decision","随机决策","抛硬币/掷骰子/做个决定","硬币 骰子 随机 决定 抉择",DICE,"常用工具","58"},
    {"scorecard","记分牌","双人计分，大按钮加减","记分 比分 计数 计分器",EDIT,"常用工具","40"},
    {"calendar","万年历","月历视图，今日高亮可翻页","日历 万年历 农历 月份",CALENDAR,"常用工具","55"},
    {"randomnum","随机数","范围随机数生成，可去重排序","随机数 抽签 抽奖 号码",DICE,"常用工具","42"},
    {"text_stats","文本统计","字符/汉字/词数/行数统计与去重","文本 字数 统计 去重 排序",TEXT,"文字处理","50"},
    {"base64","Base64 编解码","文本与 Base64 互转","base64 编码 解码 转换",COPY,"文字处理","48"},
    {"url_codec","URL 编解码","文本与 URL 百分号编码互转","url 编码 解码 转义 percent",REFRESH,"文字处理","36"},
    {"hash","哈希计算","MD5 / SHA-1 / SHA-256 摘要","哈希 md5 sha1 sha256 校验 摘要",OPEN_WITH,"文字处理","45"},
    {"json","JSON 格式化","美化或压缩 JSON 并校验合法性","json 格式化 校验 压缩 美化",EDIT,"文字处理","52"},
    {"regex","正则测试","正则匹配测试，列出全部结果","正则 表达式 regex 匹配 测试",SEARCH,"文字处理","38"},
    {"password","密码生成","按长度与字符类型生成强密码","密码 随机 生成 安全 强密码",EDIT,"文字处理","62"},
    {"uuid","UUID 生成","批量生成 UUID v4","uuid 唯一标识 生成",COPY,"文字处理","30"},
    {"img_compress","图片压缩","选图按质量压缩，存到相册","图片 压缩 照片 变小 省空间",IMAGE,"图片工具","78"},
    {"sketch","简易画板","手绘涂鸦，保存 PNG 到相册","画板 画画 涂鸦 手绘 素描",PALETTE,"图片工具","44"},
    {"deviceinfo","设备信息","品牌/屏幕/内存/电池/Android 版本","设备 信息 参数 硬件 手机",INFO,"设备相关","66"},
    {"screen_test","屏幕检测","全屏纯色循环，找坏点烧屏","屏幕 坏点 检测 漏光 烧屏",CHECK,"设备相关","46"},
    {"ruler","直尺","屏幕标尺，厘米刻度","尺子 测量 长度 厘米 直尺",RULER,"设备相关","50"},
    {"torch","手电筒","闪光灯常亮开关","手电筒 闪光灯 照明 灯",TORCH,"设备相关","68"},
    {"noise","白噪音","棕噪音循环，助眠专注","白噪音 棕噪音 助眠 睡觉 专注",AUDIO,"设备相关","56"},
    {"tts","文字朗读","输入文字，TTS 朗读","朗读 tts 语音 读出来 说话",VOICE,"设备相关","40"},
    {"zodiac","生肖星座","日期查生肖与星座","生肖 星座 运势 属相",STAR,"生活查询","48"},
    {"idcard","身份证解析","18 位身份证解析出生/性别并校验","身份证 解析 校验 证件",IDCARD,"生活查询","44"},
    {"agecalc","年龄计算","按出生日期算周岁与生活天数","年龄 周岁 生日 天数",CALENDAR,"生活查询","38"},
    {"bmi","健康计算","BMI 体质指数与参考区间","bmi 健康 体重 身高 肥胖",HEART,"生活查询","52"},
    {"level","水平仪","气泡水平仪，挂画找平","水平 仪 气泡 平衡 挂画 角度",CHECK,"设备相关","36"},
    {"stopwatch","秒表计时","正计时/倒计时/计圈","秒表 计时 倒计时 定时 停表",CHECK,"常用工具","58"},
    {"timestamp","时间戳转换","Unix 时间戳与日期互转","时间戳 unix 秒 毫秒 日期",EDIT,"文字处理","44"},
    {"radix","进制转换","2/8/10/16 进制互转","进制 二进制 十六进制 hex bin 转换",TEXT,"文字处理","40"},
    {"compass","指南针","实时指向，度数+方位读数","指南针 方向 北 东南西北 罗盘",INFO,"设备相关","42"},
    {"freqgen","频率发生器","正弦波发声 20Hz-20kHz","频率 声音 正弦 测试 音叉 发生器",AUDIO,"设备相关","30"},
    {"picker","随机抽取","名单随机抽 N 个，可点名","抽取 随机 点名 抽奖 名单 抽签",DICE,"生活查询","36"},
    {"morse","摩斯电码","英文与摩斯电码互转","摩斯 电码 morse 报文 密码",COPY,"文字处理","26"},
    {"soundmeter","分贝仪","环境声音大小估算，实时显示","分贝 噪音 声音 测量 声级",AUDIO,"设备相关","30"},
    {"colorpicker","取色器","选图取色，HEX/RGB/HSL 互转","取色 颜色 色值 hex rgb 调色",PALETTE,"图片工具","30"},
  };
  static final String[] CATEGORIES={"常用工具","文字处理","图片工具","设备相关","生活查询"};
  static final String CAT_ALL="全部";
  // 图标 res 名后缀（R.drawable.ic_tool_ 前缀）
  static int iconColor(String suffix){
    switch(suffix){
      case CALC:return 0xFFF59E0B; case RULER:return 0xFF10B981; case CALENDAR:return 0xFF3B82F6;
      case DICE:return 0xFF8B5CF6; case EDIT:return 0xFFEC4899; case TEXT:return 0xFF14B8A6;
      case COPY:return 0xFF06B6D4; case REFRESH:return 0xFFF97316; case OPEN_WITH:return 0xFFA78BFA;
      case SEARCH:return 0xFF60A5FA; case IMAGE:return 0xFFEF4444; case PALETTE:return 0xFFF472B6;
      case INFO:return 0xFF38BDF8; case CHECK:return 0xFF34D399; case TORCH:return 0xFFFBBF24;
      case AUDIO:return 0xFFC084FC; case VOICE:return 0xFF2DD4BF; case STAR:return 0xFFFDE047;
      case IDCARD:return 0xFF818CF8; case HEART:return 0xFFFB7185; case QR:return 0xFF94A3B8;
      default:return 0xFFA78BFA;
    }
  }
  static int categoryCount(){return CATEGORIES.length;}
  static List<String> toolsInCategory(int cat){List<String> out=new ArrayList<>();if(cat<0)return allToolIds();String name=CATEGORIES[cat];for(String[] t:TOOLS)if(name.equals(t[5]))out.add(t[0]);return out;}
  static List<String> allToolIds(){List<String> out=new ArrayList<>();for(String[] t:TOOLS)out.add(t[0]);return out;}
  static List<String> searchTools(String query){
    List<String> out=new ArrayList<>();String q=query==null?"":query.trim().toLowerCase(Locale.ROOT);if(q.isEmpty())return out;
    for(String[] t:TOOLS){
      boolean hit=t[1].toLowerCase(Locale.ROOT).contains(q)||t[3].toLowerCase(Locale.ROOT).contains(q)||t[2].toLowerCase(Locale.ROOT).contains(q)||t[5].contains(q);
      if(hit)out.add(t[0]);
    }
    return out;
  }
  static List<String> hotTools(){List<String> out=new ArrayList<>();List<String[]> by=new ArrayList<>();for(String[] t:TOOLS)by.add(t);
    java.util.Collections.sort(by,(a,b)->Integer.parseInt(b[6])-Integer.parseInt(a[6]));for(String[] t:by)out.add(t[0]);return out;}
  static int categoryIndex(String cat){for(int i=0;i<CATEGORIES.length;i++)if(CATEGORIES[i].equals(cat))return i;return 0;}
  static String toolId(int pos){return TOOLS[pos][0];}
  static String toolName(String id){for(String[] t:TOOLS)if(t[0].equals(id))return t[1];return "";}
  static String toolDesc(String id){for(String[] t:TOOLS)if(t[0].equals(id))return t[2];return "";}
  static String toolKeywords(String id){for(String[] t:TOOLS)if(t[0].equals(id))return t[3];return "";}
  static String toolIcon(String id){for(String[] t:TOOLS)if(t[0].equals(id))return t[4];return CALC;}
  static String toolCategory(String id){for(String[] t:TOOLS)if(t[0].equals(id))return t[5];return CATEGORIES[0];}
  static int toolHeat(String id){for(String[] t:TOOLS)if(t[0].equals(id))return Integer.parseInt(t[6]);return 0;}
  static String toolCatalogJson(){try{JSONArray array=new JSONArray();for(String[] t:TOOLS)array.put(new JSONObject().put("id",t[0]).put("name",t[1]).put("description",t[2]).put("keywords",t[3]).put("category",t[5]));return new JSONObject().put("tools",array).toString();}catch(Exception e){return "{\"tools\":[]}";}}
  //——— v1.2.2 新增工具纯逻辑 ———
  /** v1.12.0 工具精修17：时间戳自动判别方向（纯数字=时间戳，其余=日期）；ISO 8601+星期 */
  static String timestampNow(){
    SimpleDateFormat fmt=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.CHINA);
    return "当前时间戳："+System.currentTimeMillis()/1000+" 秒 / "+System.currentTimeMillis()+" 毫秒\n本地时间："+fmt.format(new java.util.Date());
  }
  static String timestampAuto(String value){
    String v=value==null?"":value.trim();
    if(v.isEmpty())return "输入时间戳（秒/毫秒）或日期（yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss）";
    if(v.matches("\\d+"))return stampToDate(Long.parseLong(v));
    return dateToStamp(v);
  }
  static String stampToDate(long v){
    long ms=v<100000000000L?v*1000:v;
    java.util.Date d=new java.util.Date(ms);
    SimpleDateFormat fmt=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.CHINA);
    SimpleDateFormat iso=new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX",Locale.CHINA);
    Calendar c=Calendar.getInstance();c.setTime(d);
    String[] weeks={"周日","周一","周二","周三","周四","周五","周六"};// Calendar.DAY_OF_WEEK: 1=周日
    return "对应时间："+fmt.format(d)+"\nISO 8601："+iso.format(d)+"\n星期："+weeks[c.get(Calendar.DAY_OF_WEEK)-1];
  }
  static String dateToStamp(String value){
    try{
      ParsePosition pp=new ParsePosition(0);
      SimpleDateFormat f2=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.CHINA);f2.setLenient(false);
      java.util.Date d=f2.parse(value.trim(),pp);
      if(d==null){f2.applyPattern("yyyy-MM-dd");pp.setIndex(0);d=f2.parse(value.trim(),pp);}
      if(d==null)return "无法解析：日期用 yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss";
      return "时间戳（秒）："+d.getTime()/1000+"\n时间戳（毫秒）："+d.getTime();
    }catch(Exception e){return "无法解析：日期用 yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss";}
  }
  /** v1.12.0 工具精修18：+0x/0b 前缀剥离；负号与 2-36 进制由 Long.parseLong 原生支持 */
  static String radixConvert(String value,int from){
    String v=value.trim();
    if(from==16&&v.length()>2&&(v.startsWith("0x")||v.startsWith("0X")))v=v.substring(2);
    if(from==2&&v.length()>2&&(v.startsWith("0b")||v.startsWith("0B")))v=v.substring(2);
    try{long r=Long.parseLong(v,from);
      return "二进制："+Long.toString(r,2)+"\n八进制："+Long.toString(r,8)+"\n十进制："+r+"\n十六进制："+Long.toString(r,16).toUpperCase(Locale.ROOT);
    }catch(Exception e){return"无法按 "+from+" 进制解析该数字（范围限 64 位整数）";}
  }
  static String pickFrom(String names,int count){
    java.util.List<String> pool=new ArrayList<>();
    for(String n:names.split("[,，\\n;；]+"))if(!n.trim().isEmpty())pool.add(n.trim());
    if(pool.isEmpty())return"先粘贴名单（换行或逗号分隔）";
    if(count<1)count=1;if(count>pool.size())count=pool.size();
    java.util.Collections.shuffle(pool,new SecureRandom());
    StringBuilder out=new StringBuilder("共 "+pool.size()+" 项，抽出 "+count+" 个：\n");
    for(int i=0;i<count;i++)out.append(i+1).append(". ").append(pool.get(i)).append('\n');
    return out.toString().trim();
  }
  /** v1.12.0 工具精修19：ITU 全表（字母 26+数字 10+标点 14），·— 容错，未知电码诚实计数（基准 ozdemirburak/morse-code-translator 280★，见研究报告） */
  static final String[] MORSE_LETTERS={".-","-...","-.-.","-..",".","..-.","--.","....","..",".---","-.-",".-..","--","-.","---",".--.","--.-",".-.","...","-","..-","...-",".--","-..-","-.--","--.."};
  static final String[] MORSE_DIGITS={"-----",".----","..---","...--","....-",".....","-....","--...","---..","----."};
  static final String[][] MORSE_PUNCT={{".",".-.-.-"},{",","--..--"},{"?","..--.."},{"!","-.-.--"},{"/","-..-."},{"=","-...-"},{"+",".-.-."},{"-","-....-"},{"@",".--.-."},{"(","-.--."},{")","-.--.-"},{"&",".-..."},{"'",".----."},{"\"",".-..-."}};
  static String morseConvert(boolean toMorse,String value){
    java.util.Map<String,String> enc=new java.util.HashMap<>();
    java.util.Map<String,String> dec=new java.util.HashMap<>();
    for(int i=0;i<26;i++){enc.put(String.valueOf((char)('A'+i)),MORSE_LETTERS[i]);dec.put(MORSE_LETTERS[i],String.valueOf((char)('A'+i)));}
    for(int i=0;i<10;i++){enc.put(String.valueOf((char)('0'+i)),MORSE_DIGITS[i]);dec.put(MORSE_DIGITS[i],String.valueOf((char)('0'+i)));}
    for(String[] p:MORSE_PUNCT){enc.put(p[0],p[1]);dec.put(p[1],p[0]);}
    if(toMorse){
      StringBuilder out=new StringBuilder();int skipped=0;
      for(char c:value.toUpperCase(Locale.ROOT).toCharArray()){
        String code=enc.get(String.valueOf(c));
        if(code!=null){if(out.length()>0)out.append(' ');out.append(code);}
        else if(c==' '){if(out.length()>0&&!out.toString().endsWith(" / "))out.append(" / ");}
        else skipped++;
      }
      String s=out.toString().replaceAll(" / $","");
      if(skipped>0)s+="\n（跳过 "+skipped+" 个无法编码的字符）";
      return s.isEmpty()?"输入英文、数字或常用标点":s;
    }
    StringBuilder out=new StringBuilder();int unknown=0;
    for(String p:value.trim().split("\\s+")){
      if(p.isEmpty())continue;
      if(p.equals("/")){out.append(' ');continue;}
      String norm=p.replace('·','.').replace('—','-').replace('−','-');
      String ch=dec.get(norm);
      if(ch!=null)out.append(ch);else unknown++;
    }
    String s=out.toString();
    if(unknown>0)s+="\n（"+unknown+" 个未知电码已跳过）";
    return s.isEmpty()?"输入摩斯电码（. - 间隔，/ 分隔单词）":s;
  }

  //——— 计算器：递归下降解析，支持 + - * / % ( ) ———
  static String calculate(String expr){
    if(expr==null||expr.trim().isEmpty())return"请输入表达式";
    try{double v=new Parser(expr.replaceAll("\\s+","")).parse();if(Double.isNaN(v)||Double.isInfinite(v))return"结果未定义";return trimNum(v);}
    catch(Exception e){return"表达式有误";}
  }
  static String trimNum(double v){if(v==Math.rint(v)&&Math.abs(v)<1e15)return String.valueOf((long)v);return new java.math.BigDecimal(v).setScale(10,java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();}
  static final class Parser{
    final String s;int i=0;
    Parser(String s){this.s=s;}
    double parse(){double v=expr();if(i<s.length())throw new IllegalArgumentException("残留字符");return v;}
    double expr(){double v=term();while(i<s.length()){char c=s.charAt(i);if(c=='+'||c=='-'){i++;double r=term();v=c=='+'?v+r:v-r;}else break;}return v;}
    double term(){double v=unary();while(i<s.length()){char c=s.charAt(i);if(c=='*'||c=='/'||c=='%'){i++;double r=unary();if(c=='*')v*=r;else if(r==0)throw new ArithmeticException("除零");else v=c=='/'?v/r:v%r;}else break;}return v;}
    double unary(){if(i<s.length()&&(s.charAt(i)=='-'||s.charAt(i)=='+')){char c=s.charAt(i++);double v=unary();return c=='-'?-v:v;}return primary();}
    double primary(){
      if(i<s.length()&&s.charAt(i)=='('){i++;double v=expr();if(i>=s.length()||s.charAt(i)!=')')throw new IllegalArgumentException("括号不闭合");i++;return v;}
      int st=i;while(i<s.length()&&(Character.isDigit(s.charAt(i))||s.charAt(i)=='.'))i++;
      if(i==st)throw new IllegalArgumentException("缺数字");
      return Double.parseDouble(s.substring(st,i));
    }
  }

  //——— 单位换算：先转基准单位（米/克/字节/米每秒/平方米/摄氏度），再转到目标单位 ———
  static String convertUnit(String category,double value,String from,String to){
    try{
      if("温度".equals(category))return trimNum(tempConvert(value,from,to));
      double base,fromF=unitFactor(category,from),toF=unitFactor(category,to);
      base=value*fromF;
      return trimNum(base/toF);
    }catch(Exception e){return"单位不支持";}
  }
  static double unitFactor(String category,String u){
    if("长度".equals(category))switch(u){case"毫米":return 0.001;case"厘米":return 0.01;case"米":return 1;case"千米":return 1000;case"英寸":return 0.0254;case"英尺":return 0.3048;case"英里":return 1609.344;}
    else if("重量".equals(category))switch(u){case"毫克":return 1e-6;case"克":return 0.001;case"千克":return 1;case"吨":return 1000;case"磅":return 0.45359237;case"盎司":return 0.028349523125;}
    else if("数据".equals(category))switch(u){case"字节":return 1;case"千字节":return 1024;case"兆字节":return 1048576;case"吉字节":return 1073741824;}
    else if("速度".equals(category))switch(u){case"米/秒":return 1;case"公里/时":return 1/3.6;case"英里/时":return 0.44704;case"节":return 0.514444;}
    else if("面积".equals(category))switch(u){case"平方米":return 1;case"平方千米":return 1e6;case"公顷":return 1e4;case"亩":return 2000.0/3;}
    throw new IllegalArgumentException(category+"/"+u);
  }
  static double tempConvert(double v,String from,String to){
    double c;if("摄氏度".equals(from))c=v;else if("华氏度".equals(from))c=(v-32)/1.8;else c=v-273.15;
    if("摄氏度".equals(to))return c;if("华氏度".equals(to))return c*1.8+32;return c+273.15;
  }

  //——— 日期计算 ———
  static String dateDiff(String a,String b){
    try{java.text.SimpleDateFormat f=new java.text.SimpleDateFormat("yyyy-MM-dd",Locale.CHINA);f.setLenient(false);
      Calendar ca=Calendar.getInstance(),cb=Calendar.getInstance();ca.setTime(f.parse(a.trim()));cb.setTime(f.parse(b.trim()));
      boolean neg=cb.before(ca);if(neg){Calendar t=ca;ca=cb;cb=t;}
      long days=Math.round((cb.getTimeInMillis()-ca.getTimeInMillis())/86400000.0);
      // v1.7.4 修复：旧实现用「余天数/30」估算月份（不准，文案还自认"左右"）。
      // 标准算法：逐级借位——日为负时向前借「起始日所在月」的天数（借位基准必须是减数侧，否则会出现负天数）。
      int y=cb.get(Calendar.YEAR)-ca.get(Calendar.YEAR),m=cb.get(Calendar.MONTH)-ca.get(Calendar.MONTH),d=cb.get(Calendar.DAY_OF_MONTH)-ca.get(Calendar.DAY_OF_MONTH);
      if(d<0){m--;d+=ca.getActualMaximum(Calendar.DAY_OF_MONTH);}
      if(m<0){y--;m+=12;}
      StringBuilder sb=new StringBuilder();
      if(neg)sb.append("反向 ");
      sb.append(days).append(" 天");
      if(y>0||m>0)sb.append("（").append(y).append(" 年 ").append(m).append(" 个月 ").append(d).append(" 天）");
      sb.append(" · ").append(days/7).append(" 周").append(days%7>0?" 余 "+days%7+" 天":"");
      return sb.toString();
    }catch(Exception e){return"格式：2026-01-31";}
  }
  /** v1.7.7 万年历：二十四节气 + 主要公历节日标记（返回空串表示该日无标记）。
   *  节气 = 寿星通用公式 D=[Y*0.2422+C]-L（Y=年份后2位，L=Y/4 闰年数），21 世纪 C 值表；
   *  适用 2001~2099，绝大多数年份精确（个别年份可能差一天）。完整农历（月日/干支）为后续阶段。 */
  static String solarTermOrFestival(int year,int month,int day){
    String festival=fixedFestival(month,day);
    if(!festival.isEmpty())return festival;
    if(year>=2001&&year<=2099){
      String[] terms={"小寒","大寒","立春","雨水","惊蛰","春分","清明","谷雨","立夏","小满","芒种","夏至","小暑","大暑","立秋","处暑","白露","秋分","寒露","霜降","立冬","小雪","大雪","冬至"};
      double[] c={5.4055,20.12,3.87,18.73,5.63,20.646,4.81,20.1,5.52,21.04,5.678,21.37,7.108,22.83,7.5,23.13,7.646,23.042,8.318,23.438,7.438,22.36,7.18,21.94};
      int y2=year%100;
      int idx=(month-1)*2;
      if(day==(int)(y2*0.2422+c[idx])-(int)((y2-1)/4))return terms[idx];
      if(day==(int)(y2*0.2422+c[idx+1])-(int)((y2-1)/4))return terms[idx+1];
    }
    return "";
  }
  private static String fixedFestival(int month,int day){
    switch(month){
      case 1:if(day==1)return"元旦";break;
      case 2:if(day==14)return"情人节";break;
      case 3:if(day==8)return"妇女节";if(day==12)return"植树节";break;
      case 4:if(day==1)return"愚人节";break;
      case 5:if(day==1)return"劳动节";if(day==4)return"青年节";break;
      case 6:if(day==1)return"儿童节";break;
      case 7:if(day==1)return"建党节";break;
      case 8:if(day==1)return"建军节";break;
      case 9:if(day==10)return"教师节";break;
      case 10:if(day==1)return"国庆节";break;
      case 12:if(day==25)return"圣诞节";break;
    }
    return "";
  }
  static String dateOffset(String base,int offset){
    try{java.text.SimpleDateFormat f=new java.text.SimpleDateFormat("yyyy-MM-dd",Locale.CHINA);f.setLenient(false);
      Calendar c=Calendar.getInstance();c.setTime(f.parse(base.trim()));c.add(Calendar.DAY_OF_MONTH,offset);
      return f.format(c.getTime())+"（星期"+"日一二三四五六".charAt(c.get(Calendar.DAY_OF_WEEK)-1)+"）";
    }catch(Exception e){return"格式：2026-01-31";}
  }
  static String weekdayOf(String base){
    try{java.text.SimpleDateFormat f=new java.text.SimpleDateFormat("yyyy-MM-dd",Locale.CHINA);f.setLenient(false);
      Calendar c=Calendar.getInstance();c.setTime(f.parse(base.trim()));return"星期"+"日一二三四五六".charAt(c.get(Calendar.DAY_OF_WEEK)-1);
    }catch(Exception e){return"格式：2026-01-31";}
  }
  static int[] calendarGrid(int year,int month){month--;int[] out=new int[42];Calendar c=Calendar.getInstance();c.clear();c.set(year,month,1);int lead=(c.get(Calendar.DAY_OF_WEEK)+6)%7,max=c.getActualMaximum(Calendar.DAY_OF_MONTH);for(int d=0;d<max;d++)out[lead+d]=d+1;return out;}
  static String ageCalc(String birth){
    try{java.text.SimpleDateFormat f=new java.text.SimpleDateFormat("yyyy-MM-dd",Locale.CHINA);f.setLenient(false);
      Calendar b=Calendar.getInstance();b.setTime(f.parse(birth.trim()));Calendar now=Calendar.getInstance();
      if(b.after(now))return"出生日期在未来";
      int years=now.get(Calendar.YEAR)-b.get(Calendar.YEAR),months=now.get(Calendar.MONTH)-b.get(Calendar.MONTH),days=now.get(Calendar.DAY_OF_MONTH)-b.get(Calendar.DAY_OF_MONTH);
      if(days<0){months--;Calendar prev=Calendar.getInstance();prev.setTime(now.getTime());prev.add(Calendar.MONTH,-1);days+=prev.getActualMaximum(Calendar.DAY_OF_MONTH);}// v1.7.4 修复：借「上一个月」天数（旧实现用当前月，月份边界算错）
      if(months<0){years--;months+=12;}
      long total=Math.round((now.getTimeInMillis()-b.getTimeInMillis())/86400000.0);
      int nominal=now.get(Calendar.YEAR)-b.get(Calendar.YEAR)+1;// v1.14.0 精修27：虚岁——出生即 1 岁，每跨一个公历元旦 +1
      Calendar nextBirthday=(Calendar)b.clone();nextBirthday.set(Calendar.YEAR,now.get(Calendar.YEAR));
      if(nextBirthday.before(now))nextBirthday.add(Calendar.YEAR,1);
      int daysToBirthday=(int)Math.round((nextBirthday.getTimeInMillis()-now.getTimeInMillis())/86400000.0);
      return"周岁 "+years+" 岁 "+months+" 个月 "+days+" 天\n虚岁 "+nominal+" 岁\n共生活 "+total+" 天\n距下一生日 "+daysToBirthday+" 天";
    }catch(Exception e){return"格式：2000-06-15";}
  }

  //——— 随机 ———
  static String coinFlip(){return new SecureRandom().nextBoolean()?"正面（花）":"反面（字）";}
  static int diceRoll(int sides){return new SecureRandom().nextInt(Math.max(2,Math.min(100,sides)))+1;}
  static String decide(String[] options){if(options==null||options.length==0)return"填几个候选";return options[new SecureRandom().nextInt(options.length)];}
  /** v1.10.4 工具精修11：sort 0=不排序（抽签序） 1=升序 2=降序；SecureRandom 全程 */
  static String randomNumbers(int min,int max,int count,boolean unique,int sort){
    if(max<min)return"上限需不小于下限";
    SecureRandom r=new SecureRandom();int span=max-min+1;
    if(unique&&count>span)return"去重模式下数量不能超过区间大小 "+span;
    java.util.LinkedHashSet<Integer> set=new java.util.LinkedHashSet<>();List<Integer> list=new ArrayList<>();
    while((unique?set:list).size()<Math.max(1,Math.min(200,count))){int v=min+r.nextInt(span);if(unique)set.add(v);else list.add(v);}
    List<Integer> out=new ArrayList<>(unique?set:list);
    if(sort==1)java.util.Collections.sort(out);
    if(sort==2){java.util.Collections.sort(out);java.util.Collections.reverse(out);}
    StringBuilder sb=new StringBuilder();int i=0;
    for(int v:out){sb.append(v);if(++i<out.size())sb.append(i%10==0?"\n":"  ");}
    return sb.toString();
  }

  //——— 文本工具（沿用已验证实现）———
  /** v1.11.2 工具精修14：全维度文本统计（对齐 Countable live 口径与 Word 字数约定，见 07-研究报告/工具精修-14）。
   *  返回 int[10]：0 字数(汉字+英文单词) 1 汉字 2 英文单词 3 数字串 4 字符(含空白) 5 字符(不含空白) 6 句数 7 段落 8 行数 9 标点 */
  static int[] textStatsAll(String value){
    int[] s=new int[10];
    if(value==null||value.isEmpty())return s;
    int total=value.length();s[4]=total;
    boolean inWord=false,inNum=false;
    for(int i=0;i<total;i++){
      char c=value.charAt(i);
      boolean ws=Character.isWhitespace(c);
      if(!ws)s[5]++;
      boolean han=c>=0x3400&&c<=0x4DBF||c>=0x4E00&&c<=0x9FFF;
      if(han)s[1]++;
      boolean letterOrDigit=!ws&&!han&&Character.isLetterOrDigit(c);
      if(letterOrDigit&&!inWord)s[2]++;
      inWord=letterOrDigit;
      boolean digit=!ws&&Character.isDigit(c);
      if(digit&&!inNum)s[3]++;
      inNum=digit;
      if(c=='。'||c=='！'||c=='？'||c=='!'||c=='?'||c=='…'||c=='；'||c==';')s[6]++;
      if(!ws&&!Character.isLetterOrDigit(c))s[9]++;
    }
    // 句末终止符之后仍有非空白内容则补一句（Countable sentences 切分约定）
    int lastTerm=-1;
    for(int i=total-1;i>=0;i--){char c=value.charAt(i);if(c=='。'||c=='！'||c=='？'||c=='!'||c=='?'||c=='…'||c=='；'||c==';'){lastTerm=i;break;}}
    for(int i=lastTerm+1;i<total;i++)if(!Character.isWhitespace(value.charAt(i))){s[6]++;break;}
    String[] paras=value.split("\n+");
    for(String p:paras)if(!p.trim().isEmpty())s[7]++;
    s[8]=value.split("\n",-1).length;
    s[0]=s[1]+s[2];
    return s;
  }
  /** v1.11.2 工具精修14：高频汉字/英文单词 Top5（无词典近似，灵感取自 TextAnalyzer 高频词提取；空文本返回空串） */
  static String textTopFreq(String value){
    if(value==null||value.isEmpty())return "";
    java.util.HashMap<String,Integer> hanMap=new java.util.HashMap<>(),wordMap=new java.util.HashMap<>();
    StringBuilder word=new StringBuilder();
    int total=value.length();
    for(int i=0;i<=total;i++){
      char c=i<total?value.charAt(i):' ';
      boolean han=i<total&&(c>=0x3400&&c<=0x4DBF||c>=0x4E00&&c<=0x9FFF);
      boolean asciiWord=i<total&&(c>='a'&&c<='z'||c>='A'&&c<='Z'||c>='0'&&c<='9');
      if(han){hanMap.merge(String.valueOf(c),1,Integer::sum);}
      if(han||!asciiWord){
        if(word.length()>=2)wordMap.merge(word.toString().toLowerCase(java.util.Locale.US),1,Integer::sum);
        word.setLength(0);
      }else word.append(c);
    }
    StringBuilder out=new StringBuilder();
    appendTopFreq(out,"高频汉字",hanMap);
    appendTopFreq(out,"高频单词",wordMap);
    return out.toString();
  }
  private static void appendTopFreq(StringBuilder out,String label,java.util.HashMap<String,Integer> map){
    if(map.isEmpty())return;
    List<java.util.Map.Entry<String,Integer>> entries=new ArrayList<>(map.entrySet());
    entries.sort((a,b)->b.getValue()-a.getValue());
    out.append(label).append("：");
    for(int i=0;i<Math.min(5,entries.size());i++){if(i>0)out.append("  ");out.append(entries.get(i).getKey()).append('×').append(entries.get(i).getValue());}
    out.append('\n');
  }
  static String dedupeLines(String value,boolean sort,boolean dropEmpty){
    TreeSet<String> unique=new TreeSet<>();List<String> keep=new ArrayList<>();
    for(String line:value.split("\n",-1)){String trimmed=line.trim();if(dropEmpty&&trimmed.isEmpty())continue;if(unique.add(trimmed))keep.add(trimmed);}
    return sort?String.join("\n",unique):String.join("\n",keep);
  }
  /** v1.12.0 工具精修15：+URL-safe(-_)与去填充变体（RFC 4648 §5）；解码剥空白、-_ 自动还原、字母表预校验（垃圾输入诚实报错） */
  static String base64Encode(String value,boolean urlSafe,boolean stripPadding){
    String out=android.util.Base64.encodeToString(value.getBytes(StandardCharsets.UTF_8),android.util.Base64.NO_WRAP);
    if(urlSafe)out=out.replace('+','-').replace('/','_');
    if(stripPadding){int end=out.length();while(end>0&&out.charAt(end-1)=='=')end--;out=out.substring(0,end);}
    return out;
  }
  static String base64Decode(String value){
    try{
      String v=value.replaceAll("\\s+","").replace('-','+').replace('_','/');
      for(char c:v.toCharArray())if(!(c>='A'&&c<='Z'||c>='a'&&c<='z'||c>='0'&&c<='9'||c=='+'||c=='/'||c=='='))return"解码失败：不是有效的 Base64";
      while(v.length()%4!=0)v+="=";
      return new String(android.util.Base64.decode(v,android.util.Base64.NO_WRAP),StandardCharsets.UTF_8);
    }catch(Exception e){return"解码失败：不是有效的 Base64";}
  }
  /** v1.12.0 工具精修16：双口径——路径（RFC 3986，空格=%20）与表单（空格=+）；解码 %XX 一律解，+ 仅表单口径还原 */
  static String urlEncode(String value,boolean formMode){
    try{
      if(formMode)return java.net.URLEncoder.encode(value,"UTF-8");
      StringBuilder sb=new StringBuilder();
      for(byte b:value.getBytes(StandardCharsets.UTF_8)){
        char c=(char)(b&0xFF);
        if(c>='A'&&c<='Z'||c>='a'&&c<='z'||c>='0'&&c<='9'||c=='-'||c=='_'||c=='.'||c=='~')sb.append(c);
        else sb.append('%').append(String.format("%02X",b&0xFF));
      }
      return sb.toString();
    }catch(Exception e){return"编码失败："+e.getMessage();}
  }
  static String urlDecode(String value,boolean formMode){
    try{
      java.io.ByteArrayOutputStream bos=new java.io.ByteArrayOutputStream();
      byte[] buf=value.getBytes(StandardCharsets.UTF_8);
      for(int i=0;i<buf.length;i++){
        char c=(char)(buf[i]&0xFF);
        if(c=='%'){
          if(i+2>=buf.length){bos.write('%');continue;}
          int hi=Character.digit((char)(buf[i+1]&0xFF),16),lo=Character.digit((char)(buf[i+2]&0xFF),16);
          if(hi<0||lo<0){bos.write('%');continue;}
          bos.write((hi<<4)|lo);i+=2;
        }else if(c=='+'&&formMode)bos.write(' ');
        else bos.write(buf[i]);
      }
      return new String(bos.toByteArray(),StandardCharsets.UTF_8);
    }catch(Exception e){return"解码失败："+e.getMessage();}
  }
  /** v1.10.1 工具精修08：单算法摘要（null=该算法不可用）；hex 小写标准编码 */
  static final String[] HASH_ALGOS={"MD5","SHA-1","SHA-224","SHA-256","SHA-384","SHA-512"};
  static String hashHex(String algo,String value){
    try{byte[] digest=MessageDigest.getInstance(algo).digest(value.getBytes(StandardCharsets.UTF_8));StringBuilder hex=new StringBuilder();for(byte b:digest)hex.append(String.format("%02x",b));return hex.toString();}
    catch(Exception e){return null;}
  }
  /** v1.10.2 工具精修09：格式化（indentSpaces=0 即压缩）；ensureAscii 把非 ASCII 转义为 Unicode 序列（合法输出上逐字符变换安全）；解析失败抛 JSONException 供页面显示行列 */
  static String jsonFormat(String value,int indentSpaces,boolean ensureAscii) throws org.json.JSONException{
    Object o=new org.json.JSONTokener(value).nextValue();
    String out;
    if(o instanceof org.json.JSONObject)out=new org.json.JSONObject(value).toString(indentSpaces);
    else if(o instanceof org.json.JSONArray)out=new org.json.JSONArray(value).toString(indentSpaces);
    else out=String.valueOf(o);
    return ensureAscii?ensureAscii(out):out;
  }
  /** 校验报告：合法返回统计，非法返回含行列的中文错误（永不抛出） */
  static String jsonValidate(String value){
    Object o;
    try{o=new org.json.JSONTokener(value).nextValue();}
    catch(org.json.JSONException e){return"解析失败："+jsonError(e);}
    catch(Exception e){return"解析失败："+e.getMessage();}
    int top=(o instanceof org.json.JSONObject)?((org.json.JSONObject)o).length():(o instanceof org.json.JSONArray)?((org.json.JSONArray)o).length():1;
    return"JSON 合法 · 顶层 "+top+" 个"+((o instanceof org.json.JSONArray)?"元素":"键")+" · 深度 "+jsonDepth(o)+" · 原始 "+value.getBytes(StandardCharsets.UTF_8).length+" 字节";
  }
  static int jsonDepth(Object o){
    if(o instanceof org.json.JSONObject){int m=0;java.util.Iterator<String> it=((org.json.JSONObject)o).keys();while(it.hasNext()){String k=it.next();m=Math.max(m,1+jsonDepth(((org.json.JSONObject)o).opt(k)));}return m+1;}
    if(o instanceof org.json.JSONArray){int m=0;for(int i=0;i<((org.json.JSONArray)o).length();i++)m=Math.max(m,1+jsonDepth(((org.json.JSONArray)o).opt(i)));return m+1;}
    return 1;
  }
  /** 从 org.json 异常消息提取行列（格式 "… at 156 [character 9 line 5]"），无行列则原样返回 */
  static String jsonError(org.json.JSONException e){
    String m=e.getMessage()==null?e.toString():e.getMessage();
    java.util.regex.Matcher mc=java.util.regex.Pattern.compile("character (\\d+) line (\\d+)").matcher(m);
    if(mc.find())return"第 "+mc.group(2)+" 行 第 "+mc.group(1)+" 列附近："+m;
    java.util.regex.Matcher mi=java.util.regex.Pattern.compile("at (\\d+)").matcher(m);
    if(mi.find())return"第 "+mi.group(1)+" 个字符附近："+m;
    return m;
  }
  private static String ensureAscii(String s){
    StringBuilder out=new StringBuilder(s.length()+16);
    for(int i=0;i<s.length();i++){char c=s.charAt(i);
      if(c<128)out.append(c);
      else if(c>0xFFFF){int cp=Character.codePointAt(s,i);out.append(String.format("\\u%04x",cp));i+=Character.charCount(cp)-1;}
      else out.append(String.format("\\u%04x",(int)c));
    }
    return out.toString();
  }
  static String regex(String pattern,String text,boolean ignoreCase){
    if(pattern==null||pattern.trim().isEmpty())return"先输入正则表达式";
    try{
      Matcher matcher=Pattern.compile(pattern.trim(),ignoreCase?Pattern.CASE_INSENSITIVE:0).matcher(text==null?"":text);
      StringBuilder out=new StringBuilder();int count=0;
      while(matcher.find()){
        count++;
        if(count>200){out.append("…（已截断）");break;}
        out.append('#').append(count).append("  ").append(matcher.group()).append("  @").append(matcher.start());
        if(matcher.groupCount()>0)for(int g=1;g<=matcher.groupCount();g++)out.append("  组").append(g).append('=').append(matcher.group(g)==null?"-":matcher.group(g));
        out.append('\n');
      }
      if(count==0)return"无匹配";
      out.insert(0,"匹配 "+count+" 处\n");
      return out.toString().trim();
    }catch(Exception e){return"正则有误："+e.getMessage();}
  }
  /** v1.10.3 工具精修10：匹配区间（前 500 处），区间无效（正则有误）返回 null；供界面 Spannable 高亮 */
  static int[][] regexRanges(String pattern,String text,boolean ignoreCase){
    try{
      Matcher matcher=Pattern.compile(pattern,ignoreCase?Pattern.CASE_INSENSITIVE:0).matcher(text==null?"":text);
      java.util.ArrayList<int[]> list=new java.util.ArrayList<>();
      while(matcher.find()&&list.size()<500)list.add(new int[]{matcher.start(),matcher.end()});
      return list.toArray(new int[0][]);
    }catch(Exception e){return null;}
  }
  /** v1.10.3：替换（replaceAll 支持 $1 组引用）；正则有误返回错误说明 */
  static String regexReplace(String pattern,String text,String replacement,boolean ignoreCase){
    if(pattern==null||pattern.trim().isEmpty())return"先输入正则表达式";
    try{return text==null?"":Pattern.compile(pattern,ignoreCase?Pattern.CASE_INSENSITIVE:0).matcher(text).replaceAll(replacement);}
    catch(Exception e){return"替换失败："+e.getMessage();}
  }
  /** v1.10.5 工具精修12：RFC 4122 多版本——v4 随机批量（大写/去连字符可选）；count 上限 100 */
  static String uuidBatch(int count,boolean upper,boolean noDash){
    StringBuilder out=new StringBuilder();
    for(int i=0;i<Math.max(1,Math.min(100,count));i++)out.append(uuidFormat(UUID.randomUUID().toString(),upper,noDash)).append('\n');
    return out.toString().trim();
  }
  /** v1.10.5：RFC 4122 名称型 UUID（version=3 MD5 / 5 SHA-1；nsHex=RFC 内置命名空间小写无连字符） */
  static String uuidNameBased(int version,String name,String nsHex){
    try{
      java.security.MessageDigest md=java.security.MessageDigest.getInstance(version==5?"SHA-1":"MD5");
      md.update(hexBytes(nsHex));md.update((name==null?"":name).getBytes(StandardCharsets.UTF_8));
      byte[] h=md.digest();
      h[6]&=0x0F;h[6]|=(byte)(version<<4);h[8]&=0x3F;h[8]|=(byte)0x80;
      StringBuilder sb=new StringBuilder();for(byte b:h)sb.append(String.format("%02x",b));
      return sb.insert(8,'-').insert(13,'-').insert(18,'-').insert(23,'-').toString();
    }catch(Exception e){return null;}
  }
  static String uuidFormat(String u,boolean upper,boolean noDash){
    if(u==null)return u;
    String s=upper?u.toUpperCase(java.util.Locale.US):u;
    return noDash?s.replace("-",""):s;
  }
  private static byte[] hexBytes(String hex){
    byte[] out=new byte[hex.length()/2];
    for(int i=0;i<out.length;i++)out[i]=(byte)Integer.parseInt(hex.substring(i*2,i*2+2),16);
    return out;
  }
  /** v1.7.8 工具精修07：SecureRandom + 每类至少一个 + Fisher-Yates 洗牌；形近字符(lI|O01)可排除（Bitwarden/KeePassDX 默认集，见 07-研究报告） */
  static String generatePassword(int length,boolean upper,boolean lower,boolean digits,boolean symbols,boolean avoidAmbiguous){
    String pu=avoidAmbiguous?"ABCDEFGHJKLMNPQRSTUVWXYZ":"ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    String pl=avoidAmbiguous?"abcdefghijkmnopqrstuvwxyz":"abcdefghijklmnopqrstuvwxyz";
    String pd=avoidAmbiguous?"23456789":"0123456789";
    String ps="!@#$%^&*";
    String[] classes=new String[4];int n=0;
    if(upper)classes[n++]=pu;if(lower)classes[n++]=pl;if(digits)classes[n++]=pd;if(symbols)classes[n++]=ps;
    if(n==0)return null;
    int size=Math.max(4,Math.min(128,length));
    SecureRandom random=new SecureRandom();
    StringBuilder pool=new StringBuilder();for(int i=0;i<n;i++)pool.append(classes[i]);
    StringBuilder out=new StringBuilder();
    for(int i=0;i<n&&out.length()<size;i++)out.append(classes[i].charAt(random.nextInt(classes[i].length())));
    while(out.length()<size)out.append(pool.charAt(random.nextInt(pool.length())));
    char[] sh=out.toString().toCharArray();
    for(int i=sh.length-1;i>0;i--){int j=random.nextInt(i+1);char t=sh[i];sh[i]=sh[j];sh[j]=t;}
    return new String(sh);
  }
  /** 熵 bits = 长度 × log2(字符池大小)；分级阈值见 07-研究报告（<28 非常弱 / 28-35 弱 / 36-59 一般 / 60-127 强 / ≥128 极强） */
  static double passwordEntropyBits(int length,int poolSize){return poolSize<=1?0:length*(Math.log(poolSize)/Math.log(2));}
  static int passwordPoolSize(boolean upper,boolean lower,boolean digits,boolean symbols,boolean avoidAmbiguous){
    int size=0;
    if(upper)size+=avoidAmbiguous?24:26;
    if(lower)size+=avoidAmbiguous?25:26;
    if(digits)size+=avoidAmbiguous?8:10;
    if(symbols)size+=8;
    return size;
  }

  //——— 生活查询 ———
  static final String[] ZODIAC_ANIMALS={"鼠","牛","虎","兔","龙","蛇","马","羊","猴","鸡","狗","猪"};
  static String zodiacOf(int year){int idx=((year-4)%12+12)%12;return ZODIAC_ANIMALS[idx];}
  static final String[] STAR_SIGNS={"摩羯","水瓶","双鱼","白羊","金牛","双子","巨蟹","狮子","处女","天秤","天蝎","射手"};
  static String starSign(int month,int day){
    int[] cut={20,19,21,20,21,22,23,23,23,24,23,22};// 复审3:每月 cut 前属上一个星座,从 cut 起属本月星座;12 月 22+ 绕回摩羯(idx=12%12=0)
    int idx=day<cut[month-1]?month-1:month;
    return STAR_SIGNS[idx%12]+"座";
  }
  /** v1.14.0 精修25：生肖按立春精确判定——立春前出生属上一年生肖（寿星公式 2001-2099，与万年历节气同源）；范围外回退按公历年 */
  static int lichunDay(int year){
    int y2=year%100;
    return (int)(y2*0.2422+3.87)-(int)((y2-1)/4);
  }
  static String zodiacPrecise(int year,int month,int day){
    int idxYear=year;
    if(year>=2001&&year<=2099){
      int lichun=lichunDay(year);
      if(month<2||(month==2&&day<lichun))idxYear=year-1;
    }
    return zodiacOf(idxYear);
  }
  static String zodiac(String yyyymmdd){
    try{java.text.SimpleDateFormat f=new java.text.SimpleDateFormat("yyyy-MM-dd",Locale.CHINA);f.setLenient(false);
      Calendar c=Calendar.getInstance();c.setTime(f.parse(yyyymmdd.trim()));
      int y=c.get(Calendar.YEAR),m=c.get(Calendar.MONTH)+1,d=c.get(Calendar.DAY_OF_MONTH);
      String note=(y>=2001&&y<=2099)?"（按立春校正）":"";
      return"生肖："+zodiacPrecise(y,m,d)+note+"\n星座："+starSign(m,d);
    }catch(Exception e){return"格式：2000-06-15";}
  }
  /** v1.14.0 精修26：+省份（GB/T 2260 省级行政区划码）+周岁 */
  static String provinceOf(String id){
    int code=Integer.parseInt(id.substring(0,2));
    switch(code){
      case 11:return"北京";case 12:return"天津";case 13:return"河北";case 14:return"山西";case 15:return"内蒙古";
      case 21:return"辽宁";case 22:return"吉林";case 23:return"黑龙江";
      case 31:return"上海";case 32:return"江苏";case 33:return"浙江";case 34:return"安徽";case 35:return"福建";case 36:return"江西";case 37:return"山东";
      case 41:return"河南";case 42:return"湖北";case 43:return"湖南";case 44:return"广东";case 45:return"广西";case 46:return"海南";
      case 50:return"重庆";case 51:return"四川";case 52:return"贵州";case 53:return"云南";case 54:return"西藏";
      case 61:return"陕西";case 62:return"甘肃";case 63:return"青海";case 64:return"宁夏";case 65:return"新疆";
      case 71:return"台湾";case 81:return"香港";case 82:return"澳门";
      default:return"未知地区码";
    }
  }
  static String parseIdCard(String raw){
    String id=raw==null?"":raw.trim().toUpperCase(Locale.ROOT);
    if(!id.matches("\\d{17}[0-9X]"))return"需 18 位身份证号";
    String weights="79A584216379A5842",codes="10X98765432";
    int sum=0;
    for(int i=0;i<17;i++){int w=Integer.parseInt(String.valueOf(weights.charAt(i)),16);sum+=w*(id.charAt(i)-'0');}
    boolean valid=id.charAt(17)==codes.charAt(sum%11);
    int year=Integer.parseInt(id.substring(6,10)),month=Integer.parseInt(id.substring(10,12)),day=Integer.parseInt(id.substring(12,14));
    try{java.text.SimpleDateFormat f=new java.text.SimpleDateFormat("yyyy-MM-dd",Locale.CHINA);f.setLenient(false);f.parse(year+"-"+month+"-"+day);}catch(Exception e){return"出生日期无效";}
    String gender=(id.charAt(16)-'0')%2==1?"男":"女";
    int age=Calendar.getInstance().get(Calendar.YEAR)-year;
    return"省份："+provinceOf(id)+"\n出生："+year+"-"+String.format(Locale.US,"%02d",month)+"-"+String.format(Locale.US,"%02d",day)+"（"+age+" 岁）\n性别："+gender+"\n校验位："+(valid?"有效 ✓":"无效 ✗");
  }
  static String bmiInfo(double heightCm,double weightKg){
    if(heightCm<50||heightCm>260||weightKg<10||weightKg>500)return"请输入合理的身高体重";
    double v=weightKg/((heightCm/100)*(heightCm/100));
    String level=v<18.5?"偏瘦":v<24?"正常":v<28?"偏胖":"肥胖";
    double lo=18.5*(heightCm/100)*(heightCm/100),hi=24*(heightCm/100)*(heightCm/100);
    return String.format(Locale.US,"BMI %.1f（%s）\n正常体重范围 %.1f - %.1f kg",v,level,lo,hi);
  }
  // v1.18.0 精修36：取色器——RGB→HSL（HSL and HSV 规范公式；H 取整角度 0-360，S/L 取整百分比）
  static int[] rgbToHsl(int r,int g,int b){
    double rn=r/255.0,gn=g/255.0,bn=b/255.0;
    double max=Math.max(rn,Math.max(gn,bn)),min=Math.min(rn,Math.min(gn,bn));
    double h=0,s=0,l=(max+min)/2;
    if(max!=min){
      double d=max-min;
      s=l>0.5?d/(2-max-min):d/(max+min);
      if(max==rn)h=60*(((gn-bn)/d)%6);
      else if(max==gn)h=60*((bn-rn)/d+2);
      else h=60*((rn-gn)/d+4);
      if(h<0)h+=360;
    }
    return new int[]{(int)Math.round(h),(int)Math.round(s*100),(int)Math.round(l*100)};
  }
  static String colorInfo(int color){
    int r=(color>>16)&0xFF,g=(color>>8)&0xFF,b=color&0xFF;
    int[] hsl=rgbToHsl(r,g,b);
    return String.format(Locale.US,"HEX #%02X%02X%02X\nRGB(%d, %d, %d)\nHSL(%d°, %d%%, %d%%)",r,g,b,r,g,b,hsl[0],hsl[1],hsl[2]);
  }
  // v1.18.0 精修37：分贝仪——dBFS=20*log10(rms/32768)，AudioRecord 16bit 满幅 32768（同式出处：cambridge-cares/TheWorldAvatar SoundLevelHandler.java、interdroid/interdroid-swan SoundSensor.java）；0 返回 -999 哨兵
  static double rmsToDb(double rms){
    if(rms<=0)return-999;
    return 20*Math.log10(rms/32768.0);
  }
}
