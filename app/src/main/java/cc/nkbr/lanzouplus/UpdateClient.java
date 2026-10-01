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

  /** Redirect allowlist used by SegmentDownloader's direct update entry point. */
  static boolean isAllowedDownloadUrl(URL url){
    if(url==null||!"https".equalsIgnoreCase(url.getProtocol())||url.getUserInfo()!=null||!defaultHttpsPort(url))return false;
    String host=url.getHost().toLowerCase(Locale.ROOT);
    return host.equals("github.com")||host.equals("githubusercontent.com")||host.endsWith(".githubusercontent.com")||host.endsWith(".nkbr.cc");
  }
}
