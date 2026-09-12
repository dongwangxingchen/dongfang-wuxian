package cc.nkbr.lanzouplus;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
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
  static String timestampConvert(String mode,String value){
    try{
      java.text.SimpleDateFormat fmt=new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.CHINA);
      if("now".equals(mode))return "当前时间戳："+System.currentTimeMillis()/1000+" 秒 / "+System.currentTimeMillis()+" 毫秒\n本地时间："+fmt.format(new java.util.Date());
      if("to_date".equals(mode)){long v=Long.parseLong(value.trim());if(v<100000000000L)v*=1000;return "对应时间："+fmt.format(new java.util.Date(v));}
      java.util.Date d=fmt.parse(value.trim());if(d==null)return"无法解析";
      return "时间戳（秒）："+d.getTime()/1000+"\n时间戳（毫秒）："+d.getTime();
    }catch(Exception e){return"格式错误：日期用 yyyy-MM-dd HH:mm:ss，时间戳为纯数字";}
  }
  static String radixConvert(String value,int from){
    try{long v=Long.parseLong(value.trim(),from);
      return "二进制："+Long.toString(v,2)+"\n八进制："+Long.toString(v,8)+"\n十进制："+v+"\n十六进制："+Long.toString(v,16).toUpperCase(Locale.ROOT);
    }catch(Exception e){return"无法按所选进制解析该数字（范围限 64 位整数）";}
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
  static String morseConvert(boolean toMorse,String value){
    String[] codes={".-","-...","-.-.","-..",".","..-.","--.","....","..",".---","-.-",".-..","--","-.","---",".--.","--.-",".-.","...","-","..-","...-",".--","-..-","-.--","--.."};
    if(toMorse){
      StringBuilder out=new StringBuilder();
      for(char c:value.toUpperCase(Locale.ROOT).toCharArray()){
        if(c>='A'&&c<='Z'){if(out.length()>0)out.append(' ');out.append(codes[c-'A']);}
        else if(c==' '&&out.length()>0)out.append(" / ");
      }
      return out.length()==0?"输入英文字母（A-Z 和空格）":out.toString();
    }
    java.util.Map<String,Character> map=new java.util.HashMap<>();
    for(int i=0;i<26;i++)map.put(codes[i],(char)('A'+i));
    StringBuilder out=new StringBuilder();
    for(String p:value.trim().split("\\s+")){
      if(p.equals("/")){out.append(' ');continue;}
      Character c=map.get(p);if(c!=null)out.append(c);
    }
    return out.length()==0?"输入摩斯电码（. - 间隔，/ 分隔单词）":out.toString();
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
      return"周岁 "+years+" 岁 "+months+" 个月 "+days+" 天\n共生活 "+total+" 天";
    }catch(Exception e){return"格式：2000-06-15";}
  }

  //——— 随机 ———
  static String coinFlip(){return new SecureRandom().nextBoolean()?"正面（花）":"反面（字）";}
  static int diceRoll(int sides){return new SecureRandom().nextInt(Math.max(2,Math.min(100,sides)))+1;}
  static String decide(String[] options){if(options==null||options.length==0)return"填几个候选";return options[new SecureRandom().nextInt(options.length)];}
  static String randomNumbers(int min,int max,int count,boolean unique){
    if(max<min)return"上限需 ≥ 下限";
    SecureRandom r=new SecureRandom();int span=max-min+1;
    if(unique&&count>span)return"去重时数量不能超过范围";
    TreeSet<Integer> set=new TreeSet<>();List<Integer> list=new ArrayList<>();
    while((unique?set:list).size()<Math.max(1,Math.min(200,count))){int v=min+r.nextInt(span);if(unique)set.add(v);else list.add(v);}
    StringBuilder out=new StringBuilder();int i=0;
    for(int v:unique?set:list){out.append(v);if(++i<(unique?set:list).size())out.append(i%10==0?"\n":"  ");}
    return out.toString();
  }

  //——— 文本工具（沿用已验证实现）———
  static String textStats(String value){
    int chars=value==null?0:value.length(),han=0,words=0;boolean inWord=false;
    for(int i=0;i<chars;i++){char c=value.charAt(i);if(c>=0x4E00&&c<=0x9FFF)han++;boolean word=!Character.isWhitespace(c)&&Character.isLetterOrDigit(c);if(word&&!inWord)words++;inWord=word;}
    int lines=value==null||value.isEmpty()?0:value.split("\n",-1).length;
    return"字符 "+chars+" · 汉字 "+han+" · 行 "+lines+" · 词 "+words;
  }
  static String dedupeLines(String value,boolean sort,boolean dropEmpty){
    TreeSet<String> unique=new TreeSet<>();List<String> keep=new ArrayList<>();
    for(String line:value.split("\n",-1)){String trimmed=line.trim();if(dropEmpty&&trimmed.isEmpty())continue;if(unique.add(trimmed))keep.add(trimmed);}
    return sort?String.join("\n",unique):String.join("\n",keep);
  }
  static String base64(boolean encode,String value){
    try{return encode?android.util.Base64.encodeToString(value.getBytes(StandardCharsets.UTF_8),android.util.Base64.NO_WRAP):new String(android.util.Base64.decode(value.trim(),android.util.Base64.DEFAULT),StandardCharsets.UTF_8);}
    catch(Exception e){return"解码失败：不是有效的 Base64";}
  }
  static String url(boolean encode,String value){
    try{return encode?java.net.URLEncoder.encode(value,"UTF-8"):java.net.URLDecoder.decode(value.trim(),"UTF-8");}
    catch(Exception e){return"转换失败："+e.getMessage();}
  }
  static String hashes(String value){
    StringBuilder out=new StringBuilder();
    for(String algo:new String[]{"MD5","SHA-1","SHA-256"}){
      try{byte[] digest=MessageDigest.getInstance(algo).digest(value.getBytes(StandardCharsets.UTF_8));StringBuilder hex=new StringBuilder();for(byte b:digest)hex.append(String.format("%02x",b));out.append(algo.replace("-","")).append("  ").append(hex).append("\n");}
      catch(Exception e){out.append(algo).append("  计算失败\n");}
    }
    return out.toString().trim();
  }
  static String json(boolean pretty,String value){
    try{return pretty?new JSONObject(value).toString(2):new JSONObject(value).toString();}
    catch(Exception ignored){}
    try{return pretty?new JSONArray(value).toString(2):new JSONArray(value).toString();}
    catch(Exception e){return"JSON 不合法："+e.getMessage();}
  }
  static String regex(String pattern,String text){
    if(pattern==null||pattern.trim().isEmpty())return"先输入正则表达式";
    try{
      Matcher matcher=Pattern.compile(pattern.trim()).matcher(text==null?"":text);
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
  static String uuidBatch(int count){
    StringBuilder out=new StringBuilder();
    for(int i=0;i<Math.max(1,Math.min(50,count));i++)out.append(UUID.randomUUID().toString()).append('\n');
    return out.toString().trim();
  }
  static String generatePassword(int length,boolean upper,boolean lower,boolean digits,boolean symbols){
    String pool=(upper?"ABCDEFGHJKLMNPQRSTUVWXYZ":"")+(lower?"abcdefghijkmnpqrstuvwxyz":"")+(digits?"23456789":"")+(symbols?"!@#$%^&*_-+=?":"");
    if(pool.isEmpty())return"至少选择一种字符";
    SecureRandom random=new SecureRandom();
    int size=Math.max(6,Math.min(64,length));
    StringBuilder out=new StringBuilder();
    for(int i=0;i<size;i++)out.append(pool.charAt(random.nextInt(pool.length())));
    return out.toString();
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
  static String zodiac(String yyyymmdd){
    try{java.text.SimpleDateFormat f=new java.text.SimpleDateFormat("yyyy-MM-dd",Locale.CHINA);f.setLenient(false);
      Calendar c=Calendar.getInstance();c.setTime(f.parse(yyyymmdd.trim()));
      int y=c.get(Calendar.YEAR),m=c.get(Calendar.MONTH)+1,d=c.get(Calendar.DAY_OF_MONTH);
      return"生肖："+zodiacOf(y)+"\n星座："+starSign(m,d);
    }catch(Exception e){return"格式：2000-06-15";}
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
    return"出生："+year+"-"+String.format(Locale.US,"%02d",month)+"-"+String.format(Locale.US,"%02d",day)+"\n性别："+gender+"\n校验位："+(valid?"有效 ✓":"无效 ✗");
  }
  static String bmiInfo(double heightCm,double weightKg){
    if(heightCm<50||heightCm>260||weightKg<10||weightKg>500)return"请输入合理的身高体重";
    double v=weightKg/((heightCm/100)*(heightCm/100));
    String level=v<18.5?"偏瘦":v<24?"正常":v<28?"偏胖":"肥胖";
    double lo=18.5*(heightCm/100)*(heightCm/100),hi=24*(heightCm/100)*(heightCm/100);
    return String.format(Locale.US,"BMI %.1f（%s）\n正常体重范围 %.1f - %.1f kg",v,level,lo,hi);
  }
}
