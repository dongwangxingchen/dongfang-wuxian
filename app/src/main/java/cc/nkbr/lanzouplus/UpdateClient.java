package cc.nkbr.lanzouplus;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.json.*;

/** Minimal, blocking GitHub Release client. Call {@link #check(String)} off the UI thread. */
final class UpdateClient {
  /** Release assets are named {@code dongfang-wuxian-v<version>.apk}; match by prefix, not a fixed name. */
  static final String ASSET_PREFIX="dongfang-wuxian-v";
  private static final String REPO_PATH="dongwangxingchen/dongfang-wuxian";
  private static final String GITHUB_LATEST="https://api.github.com/repos/"+REPO_PATH+"/releases/latest";
  /** Optional self-hosted mirror; empty until a mirror endpoint is deployed (see docs/tasks/20260926-search-web-overhaul/backend-plan.md). */
  private static final String SITE_LATEST="";
  private static final String SITE_APK="";
  private static final int JSON_LIMIT=256*1024;

  static final class UpdateInfo {
    final String version,body,browserDownloadUrl,mirrorUrl,digest;
    final long size;
    final boolean preferMirror;
    UpdateInfo(String version,String body,String browserDownloadUrl,String mirrorUrl,String digest,long size,boolean preferMirror){this.version=version;this.body=body;this.browserDownloadUrl=browserDownloadUrl;this.mirrorUrl=mirrorUrl;this.digest=digest;this.size=size;this.preferMirror=preferMirror;}
    String primaryUrl(){return preferMirror?mirrorUrl:browserDownloadUrl;}
    String fallbackUrl(){return preferMirror?browserDownloadUrl:mirrorUrl;}
  }

  /** Returns null when the latest stable release is not newer than currentVersion. */
  static UpdateInfo check(String currentVersion)throws IOException{
    long[] current=parseVersion(currentVersion);
    boolean china="CN".equalsIgnoreCase(Locale.getDefault().getCountry());
    boolean hasMirror=!SITE_LATEST.isEmpty();
    String[] endpoints;
    if(!hasMirror)endpoints=new String[]{GITHUB_LATEST};
    else endpoints=china?new String[]{SITE_LATEST,GITHUB_LATEST}:new String[]{GITHUB_LATEST,SITE_LATEST};
    IOException first=null;
    boolean sawNoRelease=false;
    for(String endpoint:endpoints)try{return parse(fetch(endpoint),current,hasMirror&&SITE_LATEST.equals(endpoint));}
      catch(IOException error){
        /* [DFW-73] 404 = 「这个源上没有任何正式版本」，**不是故障**。
           我们把全部旧版本标成预发布之后，GitHub 的 /releases/latest 就是 404；
           旧实现把它当失败 → 用户点「检查更新」看到"检查更新失败"，而正确答案是"已是最新版本"。 */
        if(isNoReleaseError(error.getMessage())){sawNoRelease=true;continue;}
        if(first==null)first=error;
      }
    if(sawNoRelease&&first==null)return null;
    throw new IOException("无法获取更新信息",first);
  }

  private static UpdateInfo parse(JSONObject release,long[] current,boolean preferMirror)throws IOException{
    if(release.optBoolean("draft")||release.optBoolean("prerelease"))throw new IOException("更新信息不是正式版本");
    String rawTag=release.optString("tag_name",release.optString("version","")).trim();
    long[] latest=parseVersion(rawTag);
    if(compare(latest,current)<=0)return null;
    JSONArray assets=release.optJSONArray("assets");
    if(assets==null)throw new IOException("更新信息缺少安装包");
    JSONObject asset=null;
    for(int i=0;i<assets.length();i++){
      JSONObject candidate=assets.optJSONObject(i);
      if(candidate==null||!isReleaseAsset(candidate.optString("name"))||!"uploaded".equals(candidate.optString("state","uploaded")))continue;
      if(asset!=null)throw new IOException("更新安装包不唯一");
      asset=candidate;
    }
    if(asset==null)throw new IOException("更新信息缺少指定安装包");
    long size=asset.optLong("size",-1);
    if(size<=0)throw new IOException("更新安装包大小无效");
    String digest=asset.optString("digest",release.optString("digest","")).toLowerCase(Locale.ROOT);
    if(!digest.matches("sha256:[0-9a-f]{64}"))throw new IOException("更新安装包摘要无效");
    String github=asset.optString("browser_download_url",release.optString("browser_download_url","")).trim();
    String mirror=asset.optString("mirror_url",release.optString("mirror_url",SITE_APK)).trim();
    String version=normalizeVersion(rawTag);
    requireGithubAsset(github,rawTag);
    requireMirrorAsset(mirror);
    return new UpdateInfo(version,release.optString("body","").trim(),github,mirror,digest,size,preferMirror&&!mirror.isEmpty());
  }

  /**
   * Accepts exactly {@code dongfang-wuxian-v<X.Y.Z>.apk}. Descriptive suffixes are rejected on purpose:
   * the release red line mandates a bare version in both tag and asset name, so a suffixed name is a
   * naming violation worth surfacing rather than silently accepting.
   */
  private static boolean isReleaseAsset(String name){
    if(name==null)return false;
    String value=name.trim();
    if(!value.startsWith(ASSET_PREFIX)||!value.endsWith(".apk"))return false;
    String middle=value.substring(ASSET_PREFIX.length(),value.length()-4);
    return middle.matches("(?:0|[1-9][0-9]*)\\.(?:0|[1-9][0-9]*)\\.(?:0|[1-9][0-9]*)");
  }

  /**
   * [DFW-73] **404 = "这个源上没有任何正式版本"，不是故障。**
   *
   * 我们把全部旧版本标成预发布之后，GitHub 的 `/releases/latest` 就是 404；
   * 旧实现把它当失败 → 用户点「检查更新」看到的是"检查更新失败"，而正确答案是"已是最新版本"。
   * 其余错误码仍然是真故障，不许被这条吞掉。
   */
  static boolean isNoReleaseError(String message){
    return message != null && message.contains("HTTP 404");
  }

  private static JSONObject fetch(String endpoint)throws IOException{
    URL current=new URL(endpoint);
    String expectedHost=current.getHost().toLowerCase(Locale.ROOT);
    for(int redirects=0;redirects<4;redirects++){
      if(!"https".equalsIgnoreCase(current.getProtocol())||!expectedHost.equals(current.getHost().toLowerCase(Locale.ROOT))||current.getUserInfo()!=null||!defaultHttpsPort(current))throw new IOException("更新地址不受信任");
      HttpURLConnection connection=(HttpURLConnection)current.openConnection();
      connection.setConnectTimeout(7000);connection.setReadTimeout(10000);connection.setInstanceFollowRedirects(false);
      connection.setRequestProperty("User-Agent","DongfangWuxian-Update");connection.setRequestProperty("Accept","application/vnd.github+json, application/json");connection.setRequestProperty("Accept-Encoding","identity");
      try{
        int code=connection.getResponseCode();
        if(isRedirect(code)){
          String location=connection.getHeaderField("Location");
          if(location==null||location.isEmpty())throw new IOException("更新地址跳转无效");
          current=new URL(current,location);continue;
        }
        if(code!=HttpURLConnection.HTTP_OK)throw new IOException("更新请求失败 HTTP "+code);
        long length=connection.getContentLengthLong();
        if(length>JSON_LIMIT)throw new IOException("更新信息过大");
        try(InputStream input=connection.getInputStream();ByteArrayOutputStream output=new ByteArrayOutputStream(length>0?(int)length:4096)){
          byte[] buffer=new byte[4096];int total=0;
          for(int count;(count=input.read(buffer))>0;){total+=count;if(total>JSON_LIMIT)throw new IOException("更新信息过大");output.write(buffer,0,count);}
          try{return new JSONObject(new String(output.toByteArray(),StandardCharsets.UTF_8));}catch(JSONException error){throw new IOException("更新信息格式无效",error);}
        }
      }finally{connection.disconnect();}
    }
    throw new IOException("更新地址跳转过多");
  }

  private static long[] parseVersion(String value)throws IOException{
    String normalized=normalizeVersion(value);
    if(!normalized.matches("(?:0|[1-9][0-9]*)\\.(?:0|[1-9][0-9]*)\\.(?:0|[1-9][0-9]*)"))throw new IOException("版本号格式无效");
    String[] parts=normalized.split("\\.");long[] out=new long[3];
    try{for(int i=0;i<3;i++)out[i]=Long.parseLong(parts[i]);}catch(NumberFormatException error){throw new IOException("版本号超出范围",error);}
    return out;
  }

  private static String normalizeVersion(String value){String out=value==null?"":value.trim();return out.startsWith("v")?out.substring(1):out;}
  private static int compare(long[] left,long[] right){for(int i=0;i<3;i++){int value=Long.compare(left[i],right[i]);if(value!=0)return value;}return 0;}
  private static boolean isRedirect(int code){return code==301||code==302||code==303||code==307||code==308;}
  private static boolean defaultHttpsPort(URL url){return url.getPort()==-1||url.getPort()==443;}

  private static void requireGithubAsset(String value,String tag)throws IOException{
    try{
      URL url=new URL(value);
      if(!"https".equalsIgnoreCase(url.getProtocol())||!"github.com".equalsIgnoreCase(url.getHost())||url.getUserInfo()!=null||!defaultHttpsPort(url))throw new IOException("GitHub 安装包地址不受信任");
      String path=url.getPath(),prefix="/"+REPO_PATH+"/releases/download/";
      if(!path.startsWith(prefix)||!isReleaseAsset(lastPathSegment(path)))throw new IOException("GitHub 安装包地址不受信任");
    }catch(IOException error){throw error;}catch(Exception error){throw new IOException("GitHub 安装包地址无效",error);}
  }

  /** Empty until a mirror is deployed; a non-empty mirror must be https on an owned host and carry a release asset name. */
  private static void requireMirrorAsset(String value)throws IOException{
    if(value==null||value.isEmpty())return;
    try{
      URL url=new URL(value);String host=url.getHost().toLowerCase(Locale.ROOT);
      if(!"https".equalsIgnoreCase(url.getProtocol())||url.getUserInfo()!=null||!defaultHttpsPort(url)||!(host.endsWith(".nkbr.cc")||host.equals("github.com")||host.endsWith(".githubusercontent.com")))throw new IOException("镜像安装包地址不受信任");
      if(!isReleaseAsset(lastPathSegment(url.getPath())))throw new IOException("镜像安装包地址不受信任");
    }catch(IOException error){throw error;}catch(Exception error){throw new IOException("镜像安装包地址无效",error);}
  }

  private static String lastPathSegment(String path){int slash=path.lastIndexOf('/');return slash<0?path:path.substring(slash+1);}

  /**
   * [2026-10-03] **自有服务器**的主机名 —— 从 {@link RemoteConfigClient#BASE} 推导，不在这里另抄一份。
   *
   * ## 为什么必须推导而不是写死
   * 这正是本次要修的 bug 的形态：**"服务器在哪"这个事实被写在两个地方**——
   * `RemoteConfigClient.BASE` 是 `39.106.33.135`，而下面那张下载白名单里**根本没有它**。
   * 于是后台记录里的 apkUrl 指向自己的服务器，App 却判定"不受信任"直接拒绝，
   * 用户看到的是「该源已失效或跳转异常」——**而服务器上那个文件明明好好的**。
   *
   * 抄两份就会分叉（改了一处忘了另一处，表现就是"某些下载又开始莫名其妙失败"）。
   * 所以这里**只推导，不复制**：哪天换服务器/换域名，只改 `BASE` 一处。
   */
  private static String ownServerHost(){
    try{return new URL(RemoteConfigClient.BASE).getHost().toLowerCase(Locale.ROOT);}catch(Exception error){return "";}
  }

  /**
   * Redirect allowlist used by SegmentDownloader's direct update entry point.
   *
   * ## [2026-10-03] 自有服务器为什么**不要求 https**
   * 这是本次唯一一处刻意放宽的地方，理由必须写清楚，否则下一个人会以为是漏了：
   *
   * 1. **平台层本来就放行了明文。** `network_security_config.xml` 的 base-config 拒绝明文，
   *    但**专门给 `39.106.33.135` 开了一条 `cleartextTrafficPermitted="true"`** ——
   *    那是一个有记录的决定，不是疏忽。
   * 2. **要求 https 只是"看起来更安全"。** 版本元数据（versionCode / sha256 / apkUrl）
   *    本身就是从同一台服务器的 `http://39.106.33.135/pb` 读来的。能改包的人**早就能改元数据**，
   *    给下载单独上 https 挡不住他。
   * 3. **真正的安全边界在下载之后**：`verifyUpdateApk()` 会校验
   *    sha256 + 包名 + **签名证书**（`MainActivity:2383`）。签名对不上就装不上——
   *    这条与走 http 还是 https 无关，它才是拦得住伪造的那一道。
   * 4. **IP 证书的信任面不可控**：服务器用的是 Let's Encrypt 的**短期 IP 证书**
   *    （链：leaf → YE2 → ISRG Root YE → ISRG Root X2）。ISRG Root X2 自 2022 年中
   *    才进 Android 信任库，`Root YE` 更是**不在任何系统信任库里**（靠交叉签名工作）。
   *    把更新这条路**吊死在"设备一定信得过这张 IP 证书"上**，风险大于收益。
   *
   * 注意放宽是**有界**的：只认 `BASE` 里那一个**精确主机名**，不是后缀匹配、不是通配。
   * 其余主机仍然一律要求 https + 端口 443（下面那行）。
   */
  static boolean isAllowedDownloadUrl(URL url){
    if(url==null||url.getUserInfo()!=null)return false;
    String protocol=url.getProtocol()==null?"":url.getProtocol().toLowerCase(Locale.ROOT);
    if(!"http".equals(protocol)&&!"https".equals(protocol))return false;
    String host=url.getHost()==null?"":url.getHost().toLowerCase(Locale.ROOT);
    String own=ownServerHost();
    if(!own.isEmpty()&&host.equals(own))return standardWebPort(url);
    if(!"https".equals(protocol)||!defaultHttpsPort(url))return false;
    return host.equals("github.com")||host.equals("githubusercontent.com")||host.endsWith(".githubusercontent.com")||host.endsWith(".nkbr.cc");
  }

  /**
   * [2026-10-03 补] **自有服务器分支原来不检查端口。**
   *
   * `host.equals(own)` 直接 `return true`，于是 `http://39.106.33.135:8080/x.apk`
   * 也会被放行 —— 和上面那句注释自称的「放宽是**有界**的：只认精确主机名」直接矛盾：
   * **主机名有界，端口无界。**
   *
   * 2026-10-03 对抗性复查用 JDK 实跑了 17 个 URL 确认了这一条；另外 16 个绕过尝试
   * （`@` 混淆、后缀欺骗、末尾点、`%2e`、反斜杠、IPv6、ftp、`:8443`）全部被正确拒绝，
   * 所以这是**唯一**的漏。
   *
   * 当前不可利用（`AGENTS.md` 铁律写着"非标端口外网不通"），
   * 但"有界"这句话必须名副其实 —— 否则下一个改这里的人会以为它已经守住了。
   */
  private static boolean standardWebPort(URL url){int port=url.getPort();return port==-1||port==80||port==443;}
}
