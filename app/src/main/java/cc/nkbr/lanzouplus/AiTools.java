package cc.nkbr.lanzouplus;

import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
/** v1.2.2:AI headless 工具层——把工具箱纯函数暴露给大模型 function calling。全部同步执行、本地无网络。 */
final class AiTools {
  interface Fn {String execute(JSONObject args) throws Exception;}
  static final class Tool {
    final String name, desc;
    final JSONObject schema;
    final Fn fn;
    Tool(String name, String desc, JSONObject schema, Fn fn) {this.name=name;this.desc=desc;this.schema=schema;this.fn=fn;}
  }
  private static JSONObject obj(String type, String desc) {try{return new JSONObject().put("type",type).put("description",desc);}catch(Exception e){return new JSONObject();}}
  private static JSONObject str(String desc, String... enumVals) {
    JSONObject o = obj("string",desc);
    if (enumVals.length>0) try {o.put("enum",new JSONArray(enumVals));}catch(Exception ignored){}
    return o;
  }
  private static JSONObject num(String desc) {return obj("number",desc);}
  private static JSONObject intg(String desc) {return obj("integer",desc);}
  private static JSONObject bool(String desc) {return obj("boolean",desc);}
  private static Tool tool(String name, String desc, String[] required, Object[][] props, Fn fn) {
    try {
      JSONObject properties = new JSONObject();
      for (Object[] p : props) properties.put((String)p[0], p[1]);
      JSONObject schema = new JSONObject().put("type","object").put("properties",properties);
      if (required.length>0) schema.put("required",new JSONArray(required));
      return new Tool(name,desc,schema,fn);
    } catch (Exception e) {return new Tool(name,desc,new JSONObject(),fn);}
  }
  private static String s(JSONObject a, String k, String dft) {String v=a.optString(k,"");return v.isEmpty()?dft:v;}
  private static double d(JSONObject a, String k, double dft) {double v=a.optDouble(k,Double.NaN);return Double.isNaN(v)?dft:v;}
  private static boolean b(JSONObject a, String k, boolean dft) {return a.has(k)?a.optBoolean(k,dft):dft;}
  private static final Tool[] TOOLS = {
    tool("calculator","计算数学表达式，支持 + - * / % 和括号",new String[]{"expression"},new Object[][]{{"expression",str("如 (3+4)*2")}},
      a -> Toolbox.calculate(s(a,"expression",""))),
    tool("unit_convert","单位换算：长度/面积/重量/温度/数据/速度",new String[]{"category","value","from","to"},new Object[][]{
        {"category",str("类别","length","area","weight","temperature","data","speed")},{"value",num("数值")},{"from",str("源单位，如 km/mi/GB/C")},{"to",str("目标单位")}},
      a -> Toolbox.convertUnit(s(a,"category","length"),d(a,"value",0),s(a,"from",""),s(a,"to",""))),
    tool("hash","计算文本的 MD5 / SHA-1 / SHA-256",new String[]{"text"},new Object[][]{{"text",str("原文")}},
      a -> Toolbox.hashes(s(a,"text",""))),
    tool("base64","Base64 编码或解码",new String[]{"encode","text"},new Object[][]{{"encode",bool("true=编码 false=解码")},{"text",str("内容")}},
      a -> Toolbox.base64(b(a,"encode",true),s(a,"text",""))),
    tool("url_codec","URL 百分号编码或解码",new String[]{"encode","text"},new Object[][]{{"encode",bool("true=编码 false=解码")},{"text",str("内容")}},
      a -> Toolbox.url(b(a,"encode",true),s(a,"text",""))),
    tool("json_format","美化或压缩 JSON 并校验",new String[]{"pretty","text"},new Object[][]{{"pretty",bool("true=美化 false=压缩")},{"text",str("JSON 文本")}},
      a -> Toolbox.json(b(a,"pretty",true),s(a,"text",""))),
    tool("text_stats","统计文本字符数/汉字数/词数/行数",new String[]{"text"},new Object[][]{{"text",str("文本")}},
      a -> Toolbox.textStats(s(a,"text",""))),
    tool("dedupe_lines","文本按行去重，可排序/去空行",new String[]{"text"},new Object[][]{{"text",str("文本")},{"sort",bool("是否排序")},{"drop_empty",bool("是否去空行")}},
      a -> Toolbox.dedupeLines(s(a,"text",""),b(a,"sort",false),b(a,"drop_empty",false))),
    tool("uuid","批量生成 UUID v4",new String[]{"count"},new Object[][]{{"count",intg("数量 1-100")}},
      a -> Toolbox.uuidBatch((int)Math.max(1,Math.min(100,d(a,"count",1))))),
    tool("password","生成强密码",new String[]{"length"},new Object[][]{{"length",intg("长度 8-64")},{"upper",bool("含大写")},{"lower",bool("含小写")},{"digits",bool("含数字")},{"symbols",bool("含符号")}},
      a -> Toolbox.generatePassword((int)Math.max(8,Math.min(64,d(a,"length",16))),b(a,"upper",true),b(a,"lower",true),b(a,"digits",true),b(a,"symbols",true))),
    tool("date_diff","计算两个日期(yyyy-MM-dd)间隔天数",new String[]{"start","end"},new Object[][]{{"start",str("如 2026-01-01")},{"end",str("如 2026-09-01")}},
      a -> Toolbox.dateDiff(s(a,"start",""),s(a,"end",""))),
    tool("date_offset","计算某日期 N 天前/后",new String[]{"base","offset"},new Object[][]{{"base",str("yyyy-MM-dd")},{"offset",intg("正为之后负为之前")}},
      a -> Toolbox.dateOffset(s(a,"base",""),(int)d(a,"offset",0))),
    tool("weekday","查询日期是星期几",new String[]{"date"},new Object[][]{{"date",str("yyyy-MM-dd")}},
      a -> Toolbox.weekdayOf(s(a,"date",""))),
    tool("age_calc","按出生日期计算周岁与生活天数",new String[]{"birth"},new Object[][]{{"birth",str("yyyy-MM-dd")}},
      a -> Toolbox.ageCalc(s(a,"birth",""))),
    tool("zodiac","查询日期的生肖与星座",new String[]{"date"},new Object[][]{{"date",str("yyyy-MM-dd")}},
      a -> Toolbox.zodiac(s(a,"date",""))),
    tool("idcard","解析 18 位身份证号(出生日期/性别/校验)",new String[]{"number"},new Object[][]{{"number",str("身份证号")}},
      a -> Toolbox.parseIdCard(s(a,"number",""))),
    tool("bmi","计算 BMI 与参考区间",new String[]{"height_cm","weight_kg"},new Object[][]{{"height_cm",num("身高厘米")},{"weight_kg",num("体重公斤")}},
      a -> Toolbox.bmiInfo(d(a,"height_cm",0),d(a,"weight_kg",0))),
    tool("coin_flip","抛硬币",new String[]{},new Object[][]{},
      a -> Toolbox.coinFlip()),
    tool("random_number","生成范围随机数",new String[]{"min","max","count"},new Object[][]{{"min",intg("最小值")},{"max",intg("最大值")},{"count",intg("个数")}},
      a -> Toolbox.randomNumbers((int)d(a,"min",0),(int)d(a,"max",100),(int)Math.max(1,Math.min(100,d(a,"count",1))),b(a,"unique",false))),
  };
  static List<Tool> all() {List<Tool> out=new ArrayList<>();for (Tool t : TOOLS) out.add(t);return out;}
  static Tool byName(String name) {for (Tool t : TOOLS) if (t.name.equals(name)) return t;return null;}
  static String toolTitle(String name) {Tool t=byName(name);return t==null?(name==null?"未知工具":name):t.name;}
  static String execute(String name, JSONObject args) throws Exception {
    Tool t=byName(name);
    if (t==null) throw new IllegalArgumentException("未知工具:"+name);
    return t.fn.execute(args==null?new JSONObject():args);
  }
  /** OpenAI function calling 的 tools 数组 */
  static JSONArray openaiToolSchemas() {
    JSONArray out = new JSONArray();
    for (Tool t : TOOLS) {
      try {
        out.put(new JSONObject().put("type","function").put("function",
          new JSONObject().put("name",t.name).put("description",t.desc).put("parameters",t.schema)));
      } catch (Exception ignored) {}
    }
    return out;
  }
}
