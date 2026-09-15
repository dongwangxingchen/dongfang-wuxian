import java.util.*;

/** v1.14.0 工具精修 B3（25zodiac/26idcard/27agecalc）新增算法用例 —— 与 Toolbox.java 新实现同步的副本 */
public class B3ToolsTest {
  static final String[] ZODIAC_ANIMALS={"鼠","牛","虎","兔","龙","蛇","马","羊","猴","鸡","狗","猪"};
  static String zodiacOf(int year){int idx=((year-4)%12+12)%12;return ZODIAC_ANIMALS[idx];}
  /** 寿星公式立春日（2001-2099，与万年历节气同源） */
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
  // 26 idcard 省份表（GB/T 2260 省级行政区划码，含空洞）
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
  // 27 ageCalc 虚岁：出生即 1 岁，每过一个公历元旦加 1 岁
  static int nominalAge(Calendar birth,Calendar now){
    int v=now.get(Calendar.YEAR)-birth.get(Calendar.YEAR)+1;
    return Math.max(1,v);
  }

  static int fail=0;
  static void eq(String name,Object got,Object want){
    if(!String.valueOf(got).equals(String.valueOf(want))){System.out.println("FAIL "+name+" got="+got+" want="+want);fail++;}
  }
  public static void main(String[] a){
    // 立春日抽查（万年历节气公式同源）：2024 立春=2/4，2023=2/4，2021=2/3
    eq("lichun2024",lichunDay(2024),4);
    eq("lichun2023",lichunDay(2023),4);
    eq("lichun2021",lichunDay(2021),3);
    // 生肖精确判定：2024-02-03 立春前 → 兔；2024-02-04 起 → 龙；2023-02-03 → 兔（2023 立春 2/4）
    eq("zodiac-before-lichun",zodiacPrecise(2024,2,3),"兔");
    eq("zodiac-on-lichun",zodiacPrecise(2024,2,4),"龙");
    eq("zodiac-after",zodiacPrecise(2024,6,15),"龙");
    eq("zodiac-2023-02-03",zodiacPrecise(2023,2,3),"虎");// 2023 立春 2/4，前一日仍属虎（2022）
    eq("zodiac-2023-02-04",zodiacPrecise(2023,2,4),"兔");// 立春当日换属相
    // 范围外回退按公历年
    eq("zodiac-out-of-range",zodiacPrecise(1990,5,20),zodiacOf(1990));
    // 省份
    eq("prov-bj",provinceOf("110101199001011234"),"北京");
    eq("prov-gd",provinceOf("440101199001011234"),"广东");
    eq("prov-hk",provinceOf("810101199001012345"),"香港");
    eq("prov-xj",provinceOf("650101199001011234"),"新疆");
    // 虚岁：出生即 1 岁，每跨一个公历元旦 +1
    Calendar b=Calendar.getInstance();b.set(2000,5,15);
    Calendar n=Calendar.getInstance();n.set(2026,8,15);
    eq("nominal-age",nominalAge(b,n),27);
    Calendar born31=Calendar.getInstance();born31.set(2025,11,31);
    Calendar now11=Calendar.getInstance();now11.set(2026,0,1);
    eq("nominal-newyear",nominalAge(born31,now11),2);
    System.out.println(fail==0?"ALL PASS":"FAILED "+fail);
    if(fail>0)System.exit(1);
  }
}
