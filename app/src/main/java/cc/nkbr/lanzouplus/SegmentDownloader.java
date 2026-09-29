package cc.nkbr.lanzouplus;

import android.content.*;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.*;
import java.net.*;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.*;

/** Bounded process-wide transfer engine with restart-safe Range continuation. */
final class SegmentDownloader {
  interface Listener { void progress(long done,long total); void completed(); void failed(String error); default void paused(long done,long total){} default void cancelled(long done,long total){} }
  private static final String UA="Mozilla/5.0 (Linux; Android 15; Pixel 9 Pro) AppleWebKit/537.36 Chrome/138 Mobile Safari/537.36";
  private static final Pattern CONTENT_RANGE=Pattern.compile("(?i)bytes\\s+(\\d+)-(\\d+)/(\\d+)");
  private static final int WORKER_COUNT=TransferCoordinator.adaptiveUnlimitedLimit();
  private static final long WORKER_STACK_BYTES=524288L;
  private static final AtomicInteger ACTIVE_WORKERS=new AtomicInteger();
  private static final ThreadPoolExecutor WORKERS=new ThreadPoolExecutor(WORKER_COUNT,WORKER_COUNT,20L,TimeUnit.SECONDS,new LinkedBlockingQueue<>(),r->{Thread t=new Thread(null,r,"lanzou-download",WORKER_STACK_BYTES);t.setDaemon(true);return t;});
  static{WORKERS.allowCoreThreadTimeOut(true);}
  private final Context context;
  private final TransferTerminal terminal=new TransferTerminal();
  private volatile HttpURLConnection active;

  SegmentDownloader(Context context){this.context=context.getApplicationContext();}
  boolean pause(){return stop(1);}
  boolean cancel(){return stop(2);}
  private boolean stop(int mode){if(!terminal.request(mode))return false;HttpURLConnection connection=active;if(connection!=null)connection.disconnect();return true;}

  void startResolved(String directUrl,Uri destination,long expectedTotal,Listener listener){start(directUrl,destination,expectedTotal,listener,0);}
  /** Downloads a trusted GitHub/official-site update URL without Lanzou resolution. */
  void startDirect(String directUrl,Uri destination,Listener listener){start(directUrl,destination,0,listener,1);}
  /** Downloads a user/external HTTPS URL with redirect validation on every hop. */
  void startExternal(String directUrl,Uri destination,long expectedTotal,Listener listener){start(directUrl,destination,expectedTotal,listener,2);}

  private void start(String url,Uri destination,long expectedTotal,Listener listener,int urlPolicy){
    try{WORKERS.execute(()->runTransfer(url,destination,expectedTotal,listener,urlPolicy));}
    catch(OutOfMemoryError exhausted){listener.failed("无法下载：系统内存不足，已降低并发后请重试");}
    catch(RejectedExecutionException rejected){listener.failed("无法下载：下载任务队列不可用");}
  }

  private void runTransfer(String url,Uri destination,long expectedTotal,Listener listener,int urlPolicy){
    ACTIVE_WORKERS.incrementAndGet();
    try{
      try{android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);}catch(RuntimeException ignored){android.util.Log.w("SegmentDownloader.java", "SegmentDownloader.java RuntimeException: "+ignored.getMessage(), ignored);}
      Transfer stats=new Transfer();Exception failure=null;try{download(url,destination,expectedTotal,listener,urlPolicy,stats);}catch(Exception error){failure=error;}
      int outcome=terminal.claim();active=null;if(failure==null&&outcome==0){listener.completed();return;}if(stats.total>0)listener.progress(Math.min(stats.done,stats.total),stats.total);if(outcome==1)listener.paused(stats.done,stats.total);else if(outcome==2)listener.cancelled(stats.done,stats.total);else listener.failed(failureMessage(failure==null?new IOException("下载终态冲突"):failure));
    }finally{ACTIVE_WORKERS.decrementAndGet();}
  }

  private static long progressIntervalMs(){int active=ACTIVE_WORKERS.get();return active>=64?750L:active>=24?500L:240L;}

  private static final class Transfer { long done,total; }
  private static final class Response {
    final HttpURLConnection connection;final long start,end,total;
    Response(HttpURLConnection connection,long start,long end,long total){this.connection=connection;this.start=start;this.end=end;this.total=total;}
  }
  private static final class Sink implements Closeable {
    final OutputStream out;final Closeable owner;
    Sink(OutputStream out,Closeable owner){this.out=out;this.owner=owner;}
    @Override public void close()throws IOException{IOException failure=null;try{out.close();}catch(IOException error){failure=error;}if(owner!=null)try{owner.close();}catch(IOException error){if(failure==null)failure=error;}if(failure!=null)throw failure;}
  }

  private void download(String url,Uri destination,long expectedTotal,Listener listener,int urlPolicy,Transfer stats)throws Exception{
    checkStopped();long existing=destinationLength(destination);Response response=existing>0?openResume(url,existing,expectedTotal,urlPolicy):openFirst(url,urlPolicy);active=response.connection;checkStopped();
    long total=response.total,done=response.start;stats.total=total;stats.done=done;
    listener.progress(done,total);
    Sink destinationOut;
    try{destinationOut=openDestination(destination,response.start);}catch(Exception error){response.connection.disconnect();throw error;}
    try(Sink sink=destinationOut){
      OutputStream out=sink.out;long lastEvent=0;byte[] buffer=new byte[32768];
      while(done<total){
        checkStopped();
        if(response.start!=done||response.total!=total){response.connection.disconnect();throw new IOException("Range 连续性校验失败");}
        long expected=response.end-response.start+1,written=0;
        try(InputStream in=response.connection.getInputStream()){
          for(int count;(count=in.read(buffer))>0;){
            checkStopped();out.write(buffer,0,count);written+=count;done+=count;stats.done=done;
            long now=System.currentTimeMillis();if(now-lastEvent>=progressIntervalMs()){lastEvent=now;listener.progress(Math.min(done,total),total);}
          }
        }finally{response.connection.disconnect();}
        if(written!=expected)throw new IOException(response.end+1<total?"Range 分段长度校验失败":"文件长度校验失败");
        if(done>total)throw new IOException("文件总长度校验失败");
        if(done<total){response=openRange(url,done,total,urlPolicy);active=response.connection;checkStopped();}
      }
      out.flush();
    }
        if(done!=total)throw new IOException("文件总长度校验失败");long persisted=destinationLength(destination);if(persisted!=total)throw new IOException("本地文件长度校验失败");listener.progress(total,total);
  }

  private void checkStopped()throws IOException{int stop=terminal.stopMode();if(stop!=0)throw new IOException(stop==1?"下载已暂停":"下载已取消");}

    private long destinationLength(Uri destination)throws Exception{
    if("file".equals(destination.getScheme())){File file=new File(destination.getPath());return file.isFile()?file.length():0;}
    try(ParcelFileDescriptor descriptor=context.getContentResolver().openFileDescriptor(destination,"r")){
      if(descriptor==null)return 0;long size=descriptor.getStatSize();if(size>=0)return size;
      try(FileInputStream input=new FileInputStream(descriptor.getFileDescriptor())){try{return input.getChannel().size();}catch(Exception ignored){android.util.Log.w("SegmentDownloader.java", "SegmentDownloader.java Exception: "+ignored.getMessage(), ignored);}}
    }catch(FileNotFoundException missing){return 0;}
    try(InputStream input=context.getContentResolver().openInputStream(destination)){if(input==null)return 0;long size=0;byte[] buffer=new byte[32768];for(int count;(count=input.read(buffer))>0;)size+=count;return size;}
  }

    private Sink openDestination(Uri destination,long offset)throws Exception{
    if("file".equals(destination.getScheme())){
      File file=new File(destination.getPath());long length=file.isFile()?file.length():0;if(offset>0&&length!=offset)throw new IOException("本地续传长度已变化");
      return new Sink(new FileOutputStream(file,offset>0),null);
    }
    ParcelFileDescriptor descriptor=null;Exception seekFailure=null;
    try{
      descriptor=context.getContentResolver().openFileDescriptor(destination,"rw");if(descriptor==null)throw new IOException("无法写入 Download 目录");FileOutputStream output=new FileOutputStream(descriptor.getFileDescriptor());FileChannel channel=output.getChannel();long length=channel.size();
      if(offset==0)channel.truncate(0);else if(length!=offset)throw new IOException("本地续传长度已变化");channel.position(offset);return new Sink(Channels.newOutputStream(channel),descriptor);
    }catch(SecurityException denied){if(descriptor!=null)try{descriptor.close();}catch(Exception ignored){android.util.Log.w("SegmentDownloader.java", "SegmentDownloader.java Exception: "+ignored.getMessage(), ignored);}throw denied;}catch(Exception error){seekFailure=error;if(descriptor!=null)try{descriptor.close();}catch(Exception ignored){android.util.Log.w("SegmentDownloader.java", "SegmentDownloader.java Exception: "+ignored.getMessage(), ignored);}}
    OutputStream fallback=context.getContentResolver().openOutputStream(destination,offset>0?"wa":"wt");if(fallback==null)throw seekFailure==null?new IOException("无法写入 Download 目录"):seekFailure;return new Sink(fallback,null);
  }

  private Response openResume(String url,long existing,long expectedTotal,int urlPolicy)throws Exception{
    HttpURLConnection connection=open(url,"bytes="+existing+"-",urlPolicy);int code=connection.getResponseCode();
    if(code==206){Response response=rangeResponse(connection,existing,expectedTotal);if(response!=null)return response;connection.disconnect();throw new IOException("续传 Range 响应校验失败");}
    if(code==200)return plainResponse(connection);
    connection.disconnect();if(code==400||code==405||code==416)return openWithoutRange(url,urlPolicy);throw new IOException("续传请求失败 HTTP "+code);
  }

  private Response openFirst(String url,int urlPolicy)throws Exception{
    HttpURLConnection connection=open(url,"bytes=0-",urlPolicy);int code=connection.getResponseCode();
    if(code==200)return plainResponse(connection);
    if(code==206){Response response=rangeResponse(connection,0,-1);if(response!=null)return response;connection.disconnect();return openWithoutRange(url,urlPolicy);}
    connection.disconnect();if(code==400||code==405||code==416)return openWithoutRange(url,urlPolicy);throw new IOException("直链请求失败 HTTP "+code);
  }

  private Response openWithoutRange(String url,int urlPolicy)throws Exception{
    HttpURLConnection connection=open(url,null,urlPolicy);int code=connection.getResponseCode();
    if(code==200)return plainResponse(connection);
    if(code==206){Response response=rangeResponse(connection,0,-1);if(response!=null)return response;}
    connection.disconnect();throw new IOException("直链请求失败 HTTP "+code);
  }

  private Response openRange(String url,long start,long total,int urlPolicy)throws Exception{
    HttpURLConnection connection=open(url,"bytes="+start+"-",urlPolicy);int code=connection.getResponseCode();
    if(code==206){Response response=rangeResponse(connection,start,total);if(response!=null)return response;connection.disconnect();throw new IOException("Range 响应校验失败");}
    connection.disconnect();if(code==200)throw new IOException("服务器中途拒绝 Range 请求");throw new IOException("续传请求失败 HTTP "+code);
  }

  private Response plainResponse(HttpURLConnection connection)throws IOException{
    try{rejectHtml(connection);long total=connection.getContentLengthLong();if(total<=0)throw new IOException("服务器未提供文件大小");return new Response(connection,0,total-1,total);}catch(IOException|RuntimeException error){connection.disconnect();throw error;}
  }

  private Response rangeResponse(HttpURLConnection connection,long expectedStart,long expectedTotal)throws IOException{
    try{
      rejectHtml(connection);
      // [DFWX DFW-22] 解析抽成纯函数 parseContentRange：原来与 HTTP 响应对象耦合在一起，
      // 导致审计点名的"分段下载核心零测试"——没有任何办法在不发真请求的情况下验证解析规则。
      // 抽出来之后，畸形输入/畸形范围这些**出错代价很高**（写坏文件、算错百分比）的分支可以被逐条测试。
      long[] parsed=parseContentRange(connection.getHeaderField("Content-Range"));
      if(parsed==null)return null;
      long start=parsed[0],end=parsed[1],total=parsed[2];
      if(start!=expectedStart||end<start||end>=total||total<=0||(expectedTotal>0&&total!=expectedTotal))return null;
      return new Response(connection,start,end,total);
    }catch(IOException|RuntimeException error){connection.disconnect();throw error;}
  }

  /**
   * 解析 Content-Range 头（形如 bytes start-end/total），返回 {start,end,total}；无法解析返回 null。
   *
   * 契约（逐条有测试）：
   *  - 大小写不敏感、必须整串匹配（"bytes 0-9/100 oops" 不算）；
   *  - 不可满足范围（bytes 星号/总数形式）、缺字段、非数字一律 null（不得抛异常）；
   *  - 抽象成 static 纯函数纯粹是为了可测——它不碰网络也不碰状态。
   */
  private static long[] parseContentRange(String value){
    try{
      Matcher match=value==null?null:CONTENT_RANGE.matcher(value);
      if(match==null||!match.matches())return null;
      return new long[]{Long.parseLong(match.group(1)),Long.parseLong(match.group(2)),Long.parseLong(match.group(3))};
    }catch(RuntimeException error){
      return null;
    }
  }

  private static void rejectHtml(HttpURLConnection connection)throws IOException{String type=connection.getContentType();if(type!=null){type=type.toLowerCase(Locale.ROOT);if(type.contains("text/html")||type.contains("application/json"))throw new IOException("直链仍是验证页面");}}
  private static String failureMessage(Throwable error){Throwable current=error;while(current.getCause()!=null)current=current.getCause();String message=current.getMessage();if(message==null||message.trim().isEmpty())message=current.getClass().getSimpleName();return message.startsWith("无法下载：")?message:"无法下载："+message;}

  private static HttpURLConnection open(String value,String range,int urlPolicy)throws Exception{
    URL url=new URL(value);
    if(urlPolicy==0){HttpURLConnection connection=connection(url,true);if(range!=null)connection.setRequestProperty("Range",range);return connection;}
    for(int redirects=0;redirects<8;redirects++){
      String reason=urlPolicy==1
          ?(UpdateClient.isAllowedDownloadUrl(url)?"":"更新下载地址不受信任")
          :DownloadUrlPolicy.resolvedHostRejectionReason(url);
      if(!reason.isEmpty())throw new IOException(reason);
      HttpURLConnection connection=connection(url,false);if(range!=null)connection.setRequestProperty("Range",range);int code=connection.getResponseCode();
      if(!redirect(code))return connection;
      String location=connection.getHeaderField("Location");connection.disconnect();if(location==null||location.isEmpty())throw new IOException(urlPolicy==1?"更新下载跳转无效":"外部下载跳转无效");url=new URL(url,location);
    }
    throw new IOException(urlPolicy==1?"更新下载跳转过多":"外部下载跳转过多");
  }
  private static HttpURLConnection connection(URL url,boolean follow)throws Exception{HttpURLConnection connection=(HttpURLConnection)url.openConnection();connection.setConnectTimeout(15000);connection.setReadTimeout(30000);connection.setInstanceFollowRedirects(follow);connection.setRequestProperty("User-Agent",UA);connection.setRequestProperty("Accept-Encoding","identity");return connection;}
  private static boolean redirect(int code){return code==301||code==302||code==303||code==307||code==308;}
}
