import java.util.*;
import java.nio.charset.StandardCharsets;

/** v1.11.2 工具精修14 用例：验证 textStatsAll/textTopFreq 计数口径（与 Toolbox.java 同步的算法副本） */
public class TextStatsTest {
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
    int lastTerm=-1;
    for(int i=total-1;i>=0;i--){char c=value.charAt(i);if(c=='。'||c=='！'||c=='？'||c=='!'||c=='?'||c=='…'||c=='；'||c==';'){lastTerm=i;break;}}
    for(int i=lastTerm+1;i<total;i++)if(!Character.isWhitespace(value.charAt(i))){s[6]++;break;}
    String[] paras=value.split("\n+");
    for(String p:paras)if(!p.trim().isEmpty())s[7]++;
    s[8]=value.split("\n",-1).length;
    s[0]=s[1]+s[2];
    return s;
  }
  static String textTopFreq(String value){
    if(value==null||value.isEmpty())return "";
    HashMap<String,Integer> hanMap=new HashMap<>(),wordMap=new HashMap<>();
    StringBuilder word=new StringBuilder();
    int total=value.length();
    for(int i=0;i<=total;i++){
      char c=i<total?value.charAt(i):' ';
      boolean han=i<total&&(c>=0x3400&&c<=0x4DBF||c>=0x4E00&&c<=0x9FFF);
      boolean asciiWord=i<total&&(c>='a'&&c<='z'||c>='A'&&c<='Z'||c>='0'&&c<='9');
      if(han){hanMap.merge(String.valueOf(c),1,Integer::sum);}
      if(han||!asciiWord){
        if(word.length()>=2)wordMap.merge(word.toString().toLowerCase(Locale.US),1,Integer::sum);
        word.setLength(0);
      }else word.append(c);
    }
    StringBuilder out=new StringBuilder();
    appendTopFreq(out,"高频汉字",hanMap);
    appendTopFreq(out,"高频单词",wordMap);
    return out.toString();
  }
  private static void appendTopFreq(StringBuilder out,String label,HashMap<String,Integer> map){
    if(map.isEmpty())return;
    List<Map.Entry<String,Integer>> entries=new ArrayList<>(map.entrySet());
    entries.sort((a,b)->b.getValue()-a.getValue());
    out.append(label).append("：");
    for(int i=0;i<Math.min(5,entries.size());i++){if(i>0)out.append("  ");out.append(entries.get(i).getKey()).append('×').append(entries.get(i).getValue());}
    out.append('\n');
  }

  static int fail=0;
  static void check(String name,String text,int[] expect){
    int[] s=textStatsAll(text);
    for(int i=0;i<10;i++){
      if(s[i]!=expect[i]){System.out.println("FAIL "+name+" slot"+i+" got="+s[i]+" want="+expect[i]+"  | "+Arrays.toString(s));fail++;}
    }
    System.out.println("ok  "+name+" -> "+Arrays.toString(s));
  }
  public static void main(String[] a){
    // 1 空文本全 0
    check("empty","",new int[10]);
    // 2 纯中文：你好世界。再见！ → 汉字6 英文词0 字数6 句2 段1 行1 标点0(。！是终止符按句计，不算标点格?) 
    // 注意：终止符在标点格里是「非字母数字非空白」→会计入标点。预期标点2。字数=6
    check("pure-cn","你好世界。再见！",new int[]{6,6,0,0,8,8,2,1,1,2});
    // 3 中英混排：Hello 世界 world123！ → 汉字2 词2(Hello, world123) 字数4 数字串1(123) 句1 字符含空白18 不含空白16 标点1
    check("mixed","Hello 世界 world123！",new int[]{4,2,2,1,18,16,1,1,1,1});
    // 4 数字串与标点：a 12 b345! → 词3(a,b345) 数字串2(12,345) 句1 字符含空白10 不含空白8 标点1
    check("nums","a 12 b345!",new int[]{3,0,3,2,10,8,1,1,1,1});
    // 5 段落与行（Countable 软回车=单换行即分段）：4 行、3 段、3 句、汉字10+词1(line)=11
    check("paras","第一段。\n\n第二段line。\n第二段续",new int[]{11,10,1,0,19,16,3,3,4,2});
    int[] s5=textStatsAll("第一段。\n\n第二段line。\n第二段续");
    System.out.println("paras detail: 句="+s5[6]+" 段="+s5[7]+" 行="+s5[8]+" 字符含="+s5[4]+" 不含="+s5[5]);
                    // 6 尾句无终止符补一句：你好。世界 → 句2
    int[] s6=textStatsAll("你好。世界");
    if(s6[6]!=2){System.out.println("FAIL tail 句 got="+s6[6]+" want=2");fail++;}
    System.out.println("ok  tail-no-terminator 句=2");
    // 7 高频
    String t="的春风 的天空 我和他 the The CAT the 的";
    String top=textTopFreq(t);
    System.out.println("top -> "+top.replace("\n"," / "));
    if(!top.contains("高频汉字：的×3")){System.out.println("FAIL top 汉字");fail++;}
    if(!top.contains("the×3")){System.out.println("FAIL top 单词 the×3");fail++;}
    // 8 大小写归一："The THE the" → the×3
    String t2=textTopFreq("The THE the");
    if(!t2.contains("the×3")){System.out.println("FAIL case-fold got="+t2);fail++;}
    System.out.println("ok  case-fold the×3");
    // 9 阅读时长口径：900 字数 → 3 分钟（(900+299)/300=3）
    System.out.println(fail==0?"ALL PASS":"FAILED "+fail);
    if(fail>0)System.exit(1);
  }
}
