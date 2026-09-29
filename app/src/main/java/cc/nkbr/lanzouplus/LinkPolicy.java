package cc.nkbr.lanzouplus;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * [DFWX] DFW-29（ARCH-001）宿主领域拆分 · 第一步：**链接识别与归一化策略**。
 *
 * ## 为什么先抽这一块
 * 卡片给的建议顺序里这是最"纯"的一刀：这些方法
 * **不碰 Android UI、不碰 SharedPreferences、不碰网络**，只做字符串判定，
 * 却散落在 2200+ 行的 MainActivity 里。抽出来之后：
 *  1. 它们第一次变成**可被 JVM 测试直接覆盖**的（以前只能通过启动整个 Activity 间接测）；
 *  2. MainActivity 少一处概念负担，后续拆分其他领域时不用再回来动这里。
 *
 * ## 行为等价性
 * 方法体是从 MainActivity **原样搬过来的**，唯一改动是把 `android.net.Uri.parse(...).getHost()`
 * 换成下面的 `hostOf(...)`（纯字符串解析）——因为 `Uri.parse` 在没有 Android 运行时时不可用，
 * 而这里原本只是拿它取 host，等价且让规则第一次可测。
 * `preferredLanzouUrl` 原实现是 `Uri.parse(value).toString()`（对已是字符串的 URL 做一次
 * "解析再序列化"），在值不变时等价于原值，故直接返回原值。
 *
 * 主 Activity 保留同名私有方法转发到这里，**外部调用点一行都不用改**（卡片要求"每次可独立回退"）。
 */
final class LinkPolicy {
  private LinkPolicy(){}

  /** 蓝奏域名形态（与 MainActivity 原常量一致）。 */
  private static final Pattern LANZOU_CLOUD_HOST =
      Pattern.compile("^(?:[a-z0-9-]+[.])*(?:lanzou[a-z0-9]?|lanzov)[.]com$");

  /**
   * 从文本里找出网址的结束位置：剔除末尾的句读符号。
   * （原 MainActivity.WEB_URL_CJK 用于扫描，这里只保留"收尾"这一步。）
   */
  static int webUrlEnd(CharSequence text,int start,int end){
    while(end>start&&".,;:!?)]}".indexOf(text.charAt(end-1))>=0)end--;
    return end;
  }

  /** 取 host（纯字符串实现，不依赖 android.net.Uri，便于 JVM 测试）。 */
  static String hostOf(String value){
    if(value==null||value.isEmpty())return"";
    String v=value.trim();
    int schemeEnd=v.indexOf("://");
    if(schemeEnd>=0)v=v.substring(schemeEnd+3);
    int at=v.indexOf('@');
    if(at>=0)v=v.substring(at+1);
    int cut=v.length();
    for(int i=0;i<v.length();i++){char c=v.charAt(i);if(c=='/'||c=='?'||c=='#'){cut=i;break;}}
    v=v.substring(0,cut);
    int colon=v.indexOf(':');
    if(colon>=0)v=v.substring(0,colon);
    return v.toLowerCase(Locale.ROOT);
  }

  /** 归一化：补 https 前缀、去掉尾部句读；空文本返回空串。 */
  static String normalizedWebUrl(String raw){
    String value=raw==null?"":raw.trim();
    int end=webUrlEnd(value,0,value.length());
    value=value.substring(0,end);
    if(value.isEmpty())return"";
    if(!value.matches("(?i)^[a-z][a-z0-9+.-]*://.*"))value="https://"+value;
    return value;
  }

  /** 本产品自己的发布域名（.nkbr.cc 上的 lanzou* 主机）不算蓝奏云真实域名。 */
  static boolean isProductReleaseHost(String host){
    return host!=null&&host.endsWith(".nkbr.cc")&&host.startsWith("lanzou");
  }

  static boolean isRealLanzouHost(String host){
    if(host==null)return false;
    host=host.toLowerCase(Locale.ROOT);
    while(host.endsWith("."))host=host.substring(0,host.length()-1);
    if(isProductReleaseHost(host))return false;
    return LANZOU_CLOUD_HOST.matcher(host).matches();
  }

  static boolean isLanzouUrl(String value){String normalized=normalizedWebUrl(value);if(normalized.isEmpty())return false;return isRealLanzouHost(hostOf(normalized));}

  static String preferredLanzouUrl(String raw){String value=normalizedWebUrl(raw);if(value.isEmpty()||!isLanzouUrl(value))return value;return value;}

  /** 只接受 http/https 且 host 非空——分享/深链的输入面收窄（DFW-12）。 */
  static boolean isShareableWebUrl(String value){
    if(value==null)return false;
    String trimmed=value.trim();
    if(trimmed.isEmpty())return false;
    String host=hostOf(trimmed);
    if(host.isEmpty())return false;
    String scheme=schemeOf(trimmed);
    return scheme.equals("http")||scheme.equals("https");
  }

  static String schemeOf(String value){
    if(value==null)return"";
    int i=value.indexOf("://");
    if(i<=0)return"";
    return value.substring(0,i).toLowerCase(Locale.ROOT);
  }

  private static final Pattern SHARED_URL =
      Pattern.compile("(?i)(?<![A-Z0-9._%+-])(?:(?:https?|ftp)://)?(?:[A-Z0-9-]+\\.)+[A-Z]{2,63}(?::[0-9]{1,5})?(?:/[A-Z0-9._~%!$&'()*+,;=:@/-]*)?");

  /** 从聊天软件那种"标题 + 链接 + 提取码"混合文本里挑出第一条可用 http(s) 链接。 */
  static String extractSharedUrl(String text){
    if(text==null||text.trim().isEmpty())return"";
    Matcher m=SHARED_URL.matcher(text);
    while(m.find()){
      String candidate=webUrlEnd(text,m.start(),m.end())>m.start()
          ?text.substring(m.start(),webUrlEnd(text,m.start(),m.end()))
          :m.group();
      if(isShareableWebUrl(candidate))return candidate;
    }
    return text.trim();
  }
}
