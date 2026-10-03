package cc.nkbr.lanzouplus;

import android.content.*;
import android.os.*;
import java.io.*;
import java.text.*;
import java.nio.charset.*;
import java.util.*;
import java.util.concurrent.*;

/** Dynamic direct-link scheduling with process-wide cache, URL single-flight and password retry. */
final class DirectLinkResolver implements AutoCloseable {
  interface Callback { void resolved(String directUrl,long resolvedAt,boolean cached); void failed(String error); }
  interface PasswordCallback extends Callback { void passwordRequired(boolean rejectedPrevious); }
  interface Ticket { boolean cancel(); }
  interface Clock { long now(); }
  static final long TTL_MS=60*60*1000L;
  private static final long WORKER_STACK_BYTES=262144L;
  private static final String PREFS="direct_links",DIRECT="d:",TIME="t:",PASS="pw:",SCHEMA="schema";
  private static final int CACHE_SCHEMA=4;
  private final LanzouCore core;
  private final SharedPreferences prefs;
  private final Clock clock;
  /** [DFW-128] 「上游限流」静默重试的次数上限。超过就判失败，绝不允许无限重试。 */
  private static final int MAX_PRESSURE_RETRIES=3;
  private final Object lock=new Object();
  private final ConcurrentHashMap<String,Request> inflight=new ConcurrentHashMap<>();
  private final PriorityQueue<Request> pending=new PriorityQueue<>((first,second)->first.confirmed==second.confirmed?Long.compare(first.sequence,second.sequence):(first.confirmed?-1:1));
  private final ThreadPoolExecutor executor;
  private final ScheduledThreadPoolExecutor retries=new ScheduledThreadPoolExecutor(1,r->{Thread t=new Thread(r,"lanzou-resolve-retry");t.setDaemon(true);return t;});
  private volatile boolean closed;
  private volatile int parallelism;
    private int emergencyWorkerCeiling=Integer.MAX_VALUE,active,pressureSuccesses;
  private long nextSequence;

  DirectLinkResolver(Context context,LanzouCore core){this(context,core,System::currentTimeMillis);}
  DirectLinkResolver(Context context,LanzouCore core,Clock clock){
    this.core=core;this.clock=clock;this.prefs=context.getApplicationContext().getSharedPreferences(PREFS,Context.MODE_PRIVATE);
    parallelism=normalizeParallelism(context.getApplicationContext().getSharedPreferences("download_settings-v1",Context.MODE_PRIVATE).getInt("parallel_resolves",0));
    executor=new ThreadPoolExecutor(0,Integer.MAX_VALUE,60L,TimeUnit.SECONDS,new SynchronousQueue<>(),r->{Thread t=new Thread(null,r,"lanzou-resolve",WORKER_STACK_BYTES);t.setDaemon(true);return t;});
    executor.allowCoreThreadTimeOut(true);migrateCacheSchema();cleanupExpired();
  }

  private void migrateCacheSchema(){if(prefs.getInt(SCHEMA,0)==CACHE_SCHEMA)return;SharedPreferences.Editor edit=prefs.edit();for(String key:prefs.getAll().keySet())if(key.startsWith(DIRECT)||key.startsWith(TIME))edit.remove(key);edit.putInt(SCHEMA,CACHE_SCHEMA).apply();}

  void setParallelism(int value){int selected=normalizeParallelism(value);synchronized(lock){parallelism=selected;pumpLocked();}}
  int parallelism(){return parallelism;}
  int effectiveParallelism(){synchronized(lock){return effectiveLimitLocked();}}
  private static int normalizeParallelism(int value){return Math.max(0,value);}
    private int effectiveLimitLocked(){int device=LanzouCore.adaptiveNetworkWorkers(Integer.MAX_VALUE),desired=parallelism==0?device:parallelism;return Math.max(1,Math.min(Math.min(desired,device),emergencyWorkerCeiling));}
  /**
   * 这个异常是不是"上游在限流"（应当**降并发后重试**）？
   *
   * ## [DFW-128 2026-10-03] 这里原来是"用报错文字猜语义"，正是「永远解析中」的根因
   * 旧实现只看消息里有没有「验证」等关键词。而真机抛的是
   * `new DirectRetryException("蓝奏 ACW 验证未完成",1000,false)`（`LanzouCore.java:674`/`:1769`）——
   * **含「验证」二字，必然命中**，于是被当成上游限流，走进 {@link Request#resolveNow()} 那条
   * **静默重试**分支：`return` 而**不调用 `finished()`** ⇒ 回调永远不发生 ⇒ 界面永远停在「解析中」。
   *
   * 但 ACW 验证失败要的是**一个新 cookie**，不是更少的并发 —— 降并发永远修不好它，
   * 只会无限重试。用户从 v1.0.5 报到 v1.0.12 的 bug，根子就在这一行。
   *
   * ## 现在的顺序
   * 1. 异常自己带 `rateLimited=true` → 是限流（结构化信号，最可信）
   * 2. 异常自己带 `rateLimited=false` → **明确不是限流**，直接排除（这一条修 bug）
   * 3. 其余异常（网络层等）才退回文字兜底，且**关键词里已去掉「验证」**
   */
  /** [DFW-128] 包内可见（原来是 private）—— 这是「永远解析中」的判据本身，必须能直接测。 */
  static boolean upstreamPressure(Throwable error){
    if(LanzouCore.isRateLimited(error))return true;
    if(LanzouCore.isExplicitlyNotRateLimited(error))return false;
    String value=failureMessage(error).toLowerCase(Locale.ROOT);
    return value.contains("captcha")||value.contains("429")||value.contains("频率")||value.contains("限流")||value.contains("rate limit")||value.contains("too many requests");
  }
  private boolean adaptToUpstreamPressure(){synchronized(lock){int limit=effectiveLimitLocked();boolean serial=limit<=1&&active<=1;if(!serial&&active<=limit){int reduced=Math.max(1,limit/2);emergencyWorkerCeiling=Math.min(emergencyWorkerCeiling,reduced);pressureSuccesses=0;pumpLocked();}return !serial;}}
  private void recordPressureFreeSuccess(){synchronized(lock){if(emergencyWorkerCeiling==Integer.MAX_VALUE)return;int current=Math.max(1,emergencyWorkerCeiling);pressureSuccesses++;if(pressureSuccesses<Math.max(1,current/2))return;int device=LanzouCore.adaptiveNetworkWorkers(Integer.MAX_VALUE),raised=Math.min(device,current+Math.max(1,current/2));emergencyWorkerCeiling=raised>=device?Integer.MAX_VALUE:raised;pressureSuccesses=0;pumpLocked();}}


  void prewarm(String shareUrl){resolve(shareUrl,false,null);}
  void prewarm(String shareUrl,Callback callback){resolve(shareUrl,false,callback);}
  void prewarmAll(Collection<String> shareUrls){if(shareUrls!=null)for(String url:shareUrls)prewarm(url);}
  boolean hasFresh(String shareUrl){return cached(shareUrl)!=null;}
  void remember(String shareUrl,String directUrl,long resolvedAt){String share=clean(shareUrl),direct=clean(directUrl);long age=clock.now()-resolvedAt;if(!share.isEmpty()&&!direct.isEmpty()&&resolvedAt>0&&age>=0&&age<TTL_MS)prefs.edit().putString(DIRECT+share,direct).putLong(TIME+share,resolvedAt).apply();}
  String cachedPassword(String shareUrl){String url=clean(shareUrl);return url.isEmpty()?"":prefs.getString(PASS+url,"");}
  void rememberPassword(String shareUrl,String value){String url=clean(shareUrl),secret=value==null?"":value.trim();if(url.isEmpty()||!validPassword(secret))return;prefs.edit().putString(PASS+url,secret).apply();}
  void forgetPassword(String shareUrl){String url=clean(shareUrl);if(!url.isEmpty())prefs.edit().remove(PASS+url).apply();}

  Ticket resolve(String shareUrl,boolean confirmed,Callback callback){
    String url=clean(shareUrl);if(url.isEmpty()){if(callback!=null)callback.failed("缺少蓝奏云链接");return()->false;}
    trace("resolve 收到请求 url="+url);
    if(closed){if(callback!=null)callback.failed("直链解析已取消");return()->false;}
    String rememberedPassword=cachedPassword(url);Cache hit=rememberedPassword.isEmpty()?cached(url):null;if(hit!=null){if(callback!=null)callback.resolved(hit.url,hit.at,true);return()->false;}
    boolean cancelled=false;
    synchronized(lock){
      if(closed)cancelled=true;
      else{
        Request request=inflight.get(url);
        if(request!=null){
          if(callback!=null)request.callbacks.add(callback);
          if(confirmed&&!request.confirmed){request.confirmed=true;if(request.queued&&pending.remove(request))pending.add(request);pumpLocked();}
          Request joined=request;return callback==null?()->false:()->cancel(joined,callback);
        }
        rememberedPassword=cachedPassword(url);hit=rememberedPassword.isEmpty()?cached(url):null;
        if(hit==null){request=new Request(url,confirmed,++nextSequence,rememberedPassword);if(callback!=null)request.callbacks.add(callback);inflight.put(url,request);enqueueLocked(request);Request started=request;return callback==null?()->false:()->cancel(started,callback);}
      }
    }
    if(callback!=null)if(cancelled)callback.failed("直链解析已取消");else callback.resolved(hit.url,hit.at,true);
    return()->false;
  }

  /**
   * [DFW-128 2026-10-03] 这个链接是不是**正在等用户输入访问密码**？
   *
   * 为什么需要它：`awaitPassword()`（本文件 :125）把请求挂起、弹密码框，
   * **等多久取决于用户**（可能去找密码、可能先干别的）。
   * 而 MainActivity 的解析看门狗是 25 秒超时 —— 如果不区分这种情况，
   * 用户正打字的时候任务就被判成「解析超时」，比不修还糟。
   *
   * 所以看门狗拿到 true 时**重新计时**，而不是判失败：
   * 看门狗要防的是「解析器**静默**卡住」，不是「在等用户」。
   */
  boolean isAwaitingPassword(String shareUrl){
    String url=clean(shareUrl);
    if(url.isEmpty())return false;
    synchronized(lock){Request request=inflight.get(url);return request!=null&&!request.done&&request.awaitingPassword;}
  }
  boolean providePassword(String shareUrl,String value){String url=clean(shareUrl),secret=value==null?"":value.trim();if(!validPassword(secret))return false;synchronized(lock){Request request=inflight.get(url);if(request==null||request.done||!request.awaitingPassword||closed)return false;request.password=secret;request.awaitingPassword=false;request.failures=0;if(request.running)request.resumePending=true;else enqueueLocked(request);return true;}}
  boolean cancelPasswordRequest(String shareUrl){String url=clean(shareUrl);Request request;synchronized(lock){request=inflight.get(url);if(request==null||request.done||!request.awaitingPassword)return false;request.awaitingPassword=false;}finished(request,null,0,"直链解析已取消");return true;}

  private void enqueueLocked(Request request){if(closed||request.done||request.queued||request.running||request.awaitingPassword)return;request.queued=true;pending.add(request);pumpLocked();}
  private void pumpLocked(){if(closed)return;int limit=effectiveLimitLocked();while(active<limit&&!pending.isEmpty()){Request request=pending.poll();request.queued=false;if(request.done||request.awaitingPassword)continue;request.running=true;active++;try{executor.execute(()->runAdmitted(request));}catch(RejectedExecutionException rejected){request.running=false;active--;request.done=true;inflight.remove(request.url,request);}catch(OutOfMemoryError exhausted){request.running=false;if(active>0)active--;int reduced=Math.max(1,Math.max(active,limit/2));emergencyWorkerCeiling=Math.min(emergencyWorkerCeiling,reduced);request.queued=true;pending.add(request);if(active==0){pending.remove(request);request.queued=false;request.done=true;inflight.remove(request.url,request);for(Callback callback:new ArrayList<>(request.callbacks))try{callback.failed("系统资源不足，请降低解析并发");}catch(RuntimeException ignored){android.util.Log.w("DirectLinkResolver.java", "DirectLinkResolver.java RuntimeException: "+ignored.getMessage(), ignored);}}return;}}}
  private void runAdmitted(Request request){try{request.resolveNow();}finally{synchronized(lock){if(request.running){request.running=false;if(active>0)active--;}if(request.resumePending&&!request.done&&!request.awaitingPassword){request.resumePending=false;enqueueLocked(request);}pumpLocked();}}}
  private boolean cancel(Request request,Callback callback){synchronized(lock){if(request.done||!request.callbacks.remove(callback))return false;if(request.callbacks.isEmpty()&&!request.running){request.done=true;request.awaitingPassword=false;if(request.queued){pending.remove(request);request.queued=false;}inflight.remove(request.url,request);}return true;}}

  void invalidate(String shareUrl,String directUrl){String url=clean(shareUrl);if(url.isEmpty())return;String stored=prefs.getString(DIRECT+url,"");if(directUrl==null||directUrl.isEmpty()||directUrl.equals(stored))prefs.edit().remove(DIRECT+url).remove(TIME+url).apply();}

  private Cache cached(String url){url=clean(url);if(url.isEmpty())return null;long at=prefs.getLong(TIME+url,0),age=clock.now()-at;String direct=prefs.getString(DIRECT+url,"");if(!direct.isEmpty()&&at>0&&age>=0&&age<TTL_MS)return new Cache(direct,at);if(at!=0||!direct.isEmpty())prefs.edit().remove(DIRECT+url).remove(TIME+url).apply();return null;}
  private void cleanupExpired(){long now=clock.now();SharedPreferences.Editor edit=null;for(Map.Entry<String,?> entry:prefs.getAll().entrySet())if(entry.getKey().startsWith(TIME)){long at=entry.getValue() instanceof Number?((Number)entry.getValue()).longValue():0;if(at<=0||now-at<0||now-at>=TTL_MS){if(edit==null)edit=prefs.edit();String url=entry.getKey().substring(TIME.length());edit.remove(entry.getKey()).remove(DIRECT+url);}}if(edit!=null)edit.apply();}

  private void finished(Request request,String direct,long at,String error){trace("回调发出 "+(error==null?"resolved":"failed: "+error)+" url="+request.url);List<Callback> callbacks;synchronized(lock){if(request.done)return;request.done=true;request.awaitingPassword=false;if(request.queued){pending.remove(request);request.queued=false;}inflight.remove(request.url,request);callbacks=new ArrayList<>(request.callbacks);}if(error==null)prefs.edit().putString(DIRECT+request.url,direct).putLong(TIME+request.url,at).apply();if(error==null)for(Callback callback:callbacks)try{callback.resolved(direct,at,false);}catch(RuntimeException ignored){android.util.Log.w("DirectLinkResolver.java", "DirectLinkResolver.java RuntimeException: "+ignored.getMessage(), ignored);}else for(Callback callback:callbacks)try{callback.failed(error);}catch(RuntimeException ignored){android.util.Log.w("DirectLinkResolver.java", "DirectLinkResolver.java RuntimeException: "+ignored.getMessage(), ignored);}}
  private void defer(Request request,long delay){synchronized(lock){if(request.done||closed||request.awaitingPassword)return;}try{retries.schedule(()->{synchronized(lock){if(request.done||closed||request.awaitingPassword)return;enqueueLocked(request);}},Math.max(1,delay),TimeUnit.MILLISECONDS);}catch(RejectedExecutionException rejected){if(!closed)finished(request,null,0,"直链解析已取消");}}
  private void awaitPassword(Request request,boolean rejectedPrevious){PasswordCallback interactive=null;synchronized(lock){if(request.done||closed)return;request.awaitingPassword=true;request.password="";for(Callback callback:request.callbacks)if(callback instanceof PasswordCallback){interactive=(PasswordCallback)callback;break;}}if(interactive==null){finished(request,null,0,"无法解析下载链接：需要访问密码");return;}try{interactive.passwordRequired(rejectedPrevious);}catch(RuntimeException ignored){android.util.Log.w("DirectLinkResolver.java", "DirectLinkResolver.java RuntimeException: "+ignored.getMessage(), ignored);}}

  @Override public void close(){List<Callback> callbacks=new ArrayList<>();synchronized(lock){if(closed)return;closed=true;pending.clear();for(Request request:inflight.values())if(!request.done){request.done=true;request.queued=false;request.awaitingPassword=false;callbacks.addAll(request.callbacks);}inflight.clear();}retries.shutdownNow();executor.shutdownNow();for(Callback callback:callbacks)try{callback.failed("直链解析已取消");}catch(RuntimeException ignored){android.util.Log.w("DirectLinkResolver.java", "DirectLinkResolver.java RuntimeException: "+ignored.getMessage(), ignored);}}
  /**
   * [DFW-88 2026-10-01] 失败原因要**说得清是哪一个环节、哪一个异常**。
   *
   * 原来只取**根因**的 message 就完事，结果用户看到的是
   * `无法解析下载链接：Trust anchor for certification path not found`
   * —— 既不知道是哪个网址，也看不出异常类型（SSL？超时？WAF？），
   * 排查时只能靠猜，来回好几轮。
   *
   * 现在带上**异常链的类名**（去掉包名前缀）与根因消息，形如：
   *   `无法解析下载链接：[SSLHandshakeException] Trust anchor for certification path not found`
   * 这样用户截一张图，就能直接定位到是哪一类故障。
   */
  // ── [DFW-100 2026-10-01] 直链解析诊断埋点 ──────────────────────────────────
  //
  // 为什么加这个：用户连续 5 个版本报"下载一直显示解析中"，我猜了 5 轮
  // （VPN 证书 / 域名证书过期 / TLS 换线开关 / 下载付费门 / renderFolder 空指针）
  // **全都没解决**。崩溃日志里没有任何报错 —— 说明解析请求既没成功也没失败，
  // 卡在中间某个环节，而代码里没有任何地方记录它走到哪了。
  //
  // 结论：**不要再猜，让软件自己说**。这里在解析链路的关键节点各记一行，
  // 写到 `Download/东方无限/崩溃日志/download.log`（和 crash.log 同一个目录，
  // 用户在「崩溃日志」页导出时能一并取走）。
  //
  // 定位完成后这段埋点可以保留（开销极小，只在解析时各写一行），
  // 因为它解决的是"以后同类问题怎么快速定位"，不是一次性的。
  private static void trace(String message){
    /*
     * [DFW-101] 两个出口，各管一段，缺一不可：
     *  ① 统一事件流（DfLog）—— 落在**应用私有目录**，不需要任何权限，一定写得进去；
     *  ② 历史遗留的 download.log —— 落在公共目录，用户能直接翻到，
     *     但 Android 11+ 没有「管理所有文件」权限时写入会**静默失败**。
     * 以前只有 ②，于是在新系统上"解析现场"经常是空的 —— 埋点等于没埋。
     *
     * 两处写入都走 DfLog 的**同一把锁**：历史上 `DirectLinkResolver` 与 `LanzouCore`
     * 各写一份 download.log、谁都没加锁，并发时行会互相插入（DFW-14 同类问题，
     * 当时只修了 crash.log，这个文件漏掉了）。
     */
    DfLog.event("resolve","trace","msg",message);
    try{
      File dir=new File(Environment.getExternalStorageDirectory(),"Download/东方无限/崩溃日志");
      if(!dir.exists()&&!dir.mkdirs())return;
      File file=new File(dir,"download.log");
      String line=new SimpleDateFormat("MM-dd HH:mm:ss.SSS",Locale.US).format(new Date())+"  "+message;
      DfLog.appendLocked(file,line);
    }catch(Throwable ignored){
      // 埋点绝不能影响主流程
    }
  }

  private static String failureMessage(Throwable error){
    StringBuilder chain=new StringBuilder();
    for(Throwable current=error;current!=null;current=current.getCause()){
      String name=current.getClass().getSimpleName();
      if(chain.length()>0)chain.append(" <- ");
      chain.append(name);
      if(chain.length()>160)break;
    }
    Throwable root=error;while(root.getCause()!=null)root=root.getCause();
    String value=root.getMessage();
    if(value==null||value.trim().isEmpty())value=root.getClass().getSimpleName();
    String text="无法解析下载链接："+(chain.length()>0?"["+chain+"] ":"")+value;
    return text;
  }
  private static boolean validPassword(String value){if(value==null||value.isEmpty()||value.length()>64)return false;for(int i=0;i<value.length();i++)if(Character.isISOControl(value.charAt(i)))return false;return true;}
  private static String clean(String value){return value==null?"":value.trim();}
  private static final class Cache { final String url;final long at;Cache(String url,long at){this.url=url;this.at=at;} }
  private final class Request {
    final String url;final List<Callback> callbacks=new ArrayList<>();final long sequence;volatile boolean confirmed,running,queued,done,awaitingPassword,resumePending;String password;int failures;/** [DFW-128] 因「上游限流」而静默重试的次数，必须有上限。 */int pressureRetries;
    Request(String url,boolean confirmed,long sequence,String password){this.url=url;this.confirmed=confirmed;this.sequence=sequence;this.password=password==null?"":password;}
        void resolveNow(){trace("开始解析 url="+url+" pwd="+(password.isEmpty()?"无":"有"));try{LanzouCore.DirectLink link=core.resolveDirect(url,password);trace("解析成功 -> "+link.url);if(link==null||link.url==null||link.url.isEmpty())throw new IllegalStateException("未解析到下载直链");long at=clock.now();if(!password.isEmpty())rememberPassword(url,password);recordPressureFreeSuccess();finished(this,link.url,at,null);}catch(LanzouCore.DirectPasswordException rejected){boolean hadPassword=!password.isEmpty();if(hadPassword)forgetPassword(url);awaitPassword(this,hadPassword);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();if(!closed)finished(this,null,0,failureMessage(interrupted));}catch(Exception error){++failures;/* [DFW-128 2026-10-03] 压力退避**必须有界**。
   原来这里是无条件 return —— 只要 upstreamPressure 一直命中、adaptToUpstreamPressure 一直返回 true，
   这个请求就会**无限重试且永不回调**（连下面 directRetryDelay 的 5 次上限都够不着）。
   用户看到的「永远解析中」就是这么来的。
   现在超过 MAX_PRESSURE_RETRIES 次就掉到下面那条路，由 directRetryDelay 兜底 → 最终一定 finished()。 */if(pressureRetries<MAX_PRESSURE_RETRIES&&upstreamPressure(error)&&adaptToUpstreamPressure()){++pressureRetries;synchronized(lock){if(!done&&!closed)resumePending=true;}return;}long delay=LanzouCore.directRetryDelay(error,failures);if(delay>0)defer(this,delay);else{trace("解析失败 -> "+failureMessage(error));finished(this,null,0,failureMessage(error));}}}
  }
}
