package cc.nkbr.lanzouplus;

import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/* DFWX-ADB-001 状态车道。
   Shizuku 的状态检查（pingBinder/checkSelfPermission/getUid）和 bind/unbind 都是 binder 事务，
   服务被 ROM 冻结时可能长时间无超时阻塞（v1.19.3 曾在主线程黑屏 ANR）。
   本类所有平台调用只允许发生在 lane 单线程上；refresh()/requestPermission() 对外立即返回。
   每次状态请求领一个 generation 号，评估开始与发布前各校验一次号仍是当前号，旧代次结果一律丢弃——
   否则 binder 线程 / 安装线程 / 主线程的晚到结果会覆盖新状态（审计 H-P1-4：旧 CONNECTING 覆盖
   READY 会让 ready() 误判，静默安装失败被误报成"服务未连接"）。 */

/** Tracks Shizuku/Sui authorization and exposes only the constrained APK install operation. */
final class AdbShellManager implements AutoCloseable {
  static final int PERMISSION_REQUEST=7201;
  enum State {NOT_INSTALLED,NOT_RUNNING,UNSUPPORTED,DENIED,NEEDS_PERMISSION,CONNECTING,READY_SHELL,READY_ROOT,ERROR}
  static final class Snapshot {
    final State state;final String title,detail;final int uid;
    Snapshot(State state,String title,String detail,int uid){this.state=state;this.title=title;this.detail=detail;this.uid=uid;}
    boolean ready(){return state==State.READY_SHELL||state==State.READY_ROOT;}
  }
  static final class InstallResult {
    final boolean success;final String message;
    InstallResult(boolean success,String message){this.success=success;this.message=message==null?"":message.trim();}
  }
  interface Listener {void changed(Snapshot snapshot);}

  /* 平台接缝：真实实现见 ShizukuPlatform；测试注入可控 fake 覆盖延迟、超时、权限变化与重连。 */
  interface Platform {
    boolean installed();
    boolean pingBinder();
    boolean isPreV11();
    int checkSelfPermission();
    boolean shouldShowRequestPermissionRationale();
    int getUid();
    void requestPermission(int requestCode);
    void bindUserService(ServiceConnection connection);
    void unbindUserService(ServiceConnection connection);
    void addEvents(Events events);
    void removeEvents(Events events);
  }

  /** 平台事件（binder 到达 / binder 死亡 / 授权结果）；回调线程不受本类控制，实现只做入队。 */
  interface Events {void binderReceived();void binderDead();void permissionResult(int requestCode,int result);}

  private final Context context;private final Platform platform;private final Listener listener;private final Executor lane;
  private final Object gate=new Object();
  private volatile IAdbShellService service;private volatile Snapshot snapshot;
  private volatile boolean binding;
  private int generation;
  private boolean started,closed;

  private final Events events=new Events(){
    @Override public void binderReceived(){refresh();}
    @Override public void binderDead(){dropService();refresh();}
    @Override public void permissionResult(int requestCode,int result){if(requestCode==PERMISSION_REQUEST)refresh();}
  };
  private final ServiceConnection connection=new ServiceConnection(){
    @Override public void onServiceConnected(ComponentName name,IBinder binder){service=IAdbShellService.Stub.asInterface(binder);binding=false;refresh();}
    @Override public void onServiceDisconnected(ComponentName name){dropService();refresh();}
  };

  AdbShellManager(Context context,Listener listener){this(context,new ShizukuPlatform(context.getApplicationContext()),listener,newLane());}

  /** 测试入口：平台与车道可注入；纯状态用例 context 可传 null，仅 install 暂存需要。 */
  AdbShellManager(Platform platform,Listener listener,Executor lane){this(null,platform,listener,lane);}

  private AdbShellManager(Context context,Platform platform,Listener listener,Executor lane){
    this.context=context==null?null:context.getApplicationContext();this.platform=platform;this.listener=listener;this.lane=lane;
    snapshot=new Snapshot(State.NOT_RUNNING,"正在检测","正在检测 Shizuku / Sui 服务",-1);
  }

  private static Executor newLane(){
    return Executors.newSingleThreadExecutor(runnable->{Thread thread=new Thread(runnable,"adb-shell-state");thread.setDaemon(true);return thread;});
  }

  void start(){
    synchronized(gate){if(started||closed)return;started=true;}
    platform.addEvents(events);
    refresh();
  }

  Snapshot snapshot(){return snapshot;}
  boolean ready(){return snapshot.ready()&&service!=null;}

  /** 请求一次状态评估；立即返回，binder 检查在状态车道串行执行。 */
  void refresh(){submitRefresh();}

  /* 授权申请按最近一次已发布状态决定走向，不在调用线程做 binder 检查：
     ready/CONNECTING 视为已授权（刷新即可）；NEEDS_PERMISSION 才真正发出申请。
     返回值只作界面提示（是否展示"已发起申请"）；真正调用前会在车道上重新校验一次
     连通性与授权状态，避免用过期快照向已卸载/已授权的 Shizuku 发申请。 */
  boolean requestPermission(){
    Snapshot current=snapshot;
    if(current.ready()||current.state==State.CONNECTING){submitRefresh();return true;}
    if(current.state==State.NEEDS_PERMISSION){
      int token;
      synchronized(gate){if(closed)return false;token=++generation;}
      submit(()->requestOnLane(token));
      return true;
    }
    submitRefresh();
    return false;
  }

  /* 车道内的申请：重新读一次真实状态，只有在"连得上且确实未授权且不该走说明页"时才发申请；
     其余情况发布最新状态让界面自行纠正（原实现是同步做同样判断，这里只是搬到车道上）。 */
  private void requestOnLane(int token){
    synchronized(gate){if(closed||token!=generation)return;}
    Snapshot value;
    try{
      if(!platform.pingBinder()){value=new Snapshot(State.NOT_RUNNING,"未连接","Shizuku 已连接状态已变化，请重新检测",-1);publish(value,token);return;}
      if(platform.checkSelfPermission()==PackageManager.PERMISSION_GRANTED){value=null;}
      else if(platform.shouldShowRequestPermissionRationale()){value=new Snapshot(State.DENIED,"授权已拒绝","请在 Shizuku 的应用管理中重新允许本应用",-1);}
      else{value=null;platform.requestPermission(PERMISSION_REQUEST);}
    }catch(Throwable error){value=new Snapshot(State.ERROR,"申请失败",safeMessage(error),-1);}
    if(value!=null){publish(value,token);return;}
    submitRefresh();
  }

  private void submitRefresh(){
    int token;
    synchronized(gate){if(closed)return;token=++generation;}
    submit(()->evaluate(token));
  }

  private void submit(Runnable task){try{lane.execute(task);}catch(RejectedExecutionException ignored){}}

  private void evaluate(int token){
    synchronized(gate){if(closed||token!=generation)return;}
    Snapshot value;
    try{
      if(!platform.pingBinder()){value=platform.installed()?new Snapshot(State.NOT_RUNNING,"未连接","Shizuku 已安装但服务未运行；可用无线调试、USB 调试或 root 启动",-1):new Snapshot(State.NOT_INSTALLED,"未安装 Shizuku","安装并启动 Shizuku 后可申请 ADB Shell 权限",-1);}
      else if(platform.isPreV11()){value=new Snapshot(State.UNSUPPORTED,"版本过旧","当前 Shizuku API 版本不支持 UserService，请升级 Shizuku",-1);}
      else{
        int permission=platform.checkSelfPermission();
        if(permission!=PackageManager.PERMISSION_GRANTED){boolean denied=platform.shouldShowRequestPermissionRationale();value=new Snapshot(denied?State.DENIED:State.NEEDS_PERMISSION,denied?"授权已拒绝":"等待授权",denied?"请在 Shizuku 的应用管理中重新允许本应用":"点击申请 Shizuku 的 ADB Shell 权限",-1);}
        else{
          int uid=platform.getUid();
          if(service==null){
            /* 先发布 CONNECTING 再发起绑定：绑定失败会用 ERROR 覆盖它（与原语义一致） */
            publish(new Snapshot(State.CONNECTING,"正在连接 Shell","权限已授予，正在启动隔离安装服务",uid),token);
            bind(token);
            return;
          }
          value=new Snapshot(uid==0?State.READY_ROOT:State.READY_SHELL,uid==0?"Root Shell 已连接":"ADB Shell 已连接",uid==0?"Sui / root 服务可用，可启用静默安装":"Shizuku shell (UID 2000) 可用，可启用静默安装",uid);
        }
      }
    }catch(Throwable error){dropService();value=new Snapshot(State.ERROR,"检测失败",safeMessage(error),-1);}
    publish(value,token);
  }

  /* 绑定前再校验一次代次：评估期间平台调用可能很慢，等它走到这里时可能已发生 binder 死亡/
     关闭，此时不得再发起绑定（否则 binding 会被卡住，后续合法绑定被跳过）。 */
  private void bind(int token){
    synchronized(gate){if(closed||token!=generation)return;if(binding||service!=null)return;binding=true;}
    try{platform.bindUserService(connection);}
    catch(Throwable error){binding=false;publish(new Snapshot(State.ERROR,"Shell 服务连接失败",safeMessage(error),-1),token);}
  }

  /** 断链：先清本地引用，配合新一代次让在途评估的 READY 无法再发布。 */
  private void dropService(){service=null;binding=false;}

  /* 发布闸门：代次非当前号或已关闭则丢弃。通知放在锁内保证"新状态先于旧状态"发出；
     listener 实现只做 UI 转发（MainActivity 走 runOnUiThread），不得回调本类方法。 */
  private void publish(Snapshot value,int token){
    synchronized(gate){
      if(closed||token!=generation)return;
      snapshot=value;
      if(listener!=null)listener.changed(value);
    }
  }
  private static String safeMessage(Throwable error){String message=error.getMessage();return message==null||message.trim().isEmpty()?error.getClass().getSimpleName():message.trim();}

  InstallResult install(ContentResolver resolver,Uri uri,long expectedSize){
    IAdbShellService target=service;Snapshot current=snapshot;
    if(target==null||!current.ready())return new InstallResult(false,"ADB Shell 服务未连接（"+current.title+"）");
    File staged=null;
    try{
      ParcelFileDescriptor source=resolver.openFileDescriptor(uri,"r");if(source==null)return new InstallResult(false,"无法读取安装包");
      long size=source.getStatSize();
      if(size<=0)size=expectedSize;
      if(size<=0){source.close();staged=File.createTempFile("silent-install-",".apk",context.getCacheDir());try(InputStream input=resolver.openInputStream(uri);FileOutputStream output=new FileOutputStream(staged)){if(input==null)throw new java.io.IOException("无法读取安装包");byte[] buffer=new byte[64*1024];for(int count;(count=input.read(buffer))>=0;)if(count>0)output.write(buffer,0,count);}size=staged.length();source=ParcelFileDescriptor.open(staged,ParcelFileDescriptor.MODE_READ_ONLY);}
      String raw;try(ParcelFileDescriptor descriptor=source){raw=target.installApk(descriptor,size);}
      boolean ok=raw!=null&&raw.startsWith("OK\n");String message=raw==null?"Shell 未返回安装结果":raw.replaceFirst("^(?:OK|ERROR)\\n","").trim();return new InstallResult(ok,message);
    }catch(Throwable error){dropService();refresh();return new InstallResult(false,safeMessage(error));}finally{if(staged!=null&&!staged.delete())staged.deleteOnExit();}
  }

  /* 关闭同样不在调用线程做 binder 工作（onDestroy 走主线程）：注销监听与解绑排到状态车道，
     代次先失效保证已排队的评估不会再发布；shutdown() 只停止收新任务，已排队任务照常执行。 */
  @Override public void close(){
    boolean wasStarted;
    synchronized(gate){if(closed)return;closed=true;generation++;wasStarted=started;started=false;}
    if(wasStarted)submit(()->{
      try{platform.removeEvents(events);}catch(Throwable ignored){}
      try{platform.unbindUserService(connection);}catch(Throwable error){android.util.Log.w("AdbShellManager.java", "AdbShellManager.java Throwable: "+error.getMessage(), error);}
    });
    service=null;binding=false;
    if(lane instanceof ExecutorService)((ExecutorService)lane).shutdown();
  }
}
