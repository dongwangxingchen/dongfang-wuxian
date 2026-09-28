package cc.nkbr.lanzouplus;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import rikka.shizuku.Shizuku;

/** DFWX-ADB-001：Shizuku 的真实平台实现。
 *  <p>本类只做转发，不含状态逻辑；所有方法都必须在 AdbShellManager 的状态车道上调用
 *  （binder 事务可能无超时阻塞）。监听注册/注销转发给 Shizuku 的全局监听表。 */
final class ShizukuPlatform implements AdbShellManager.Platform {
  private final Context context;
  private final Shizuku.UserServiceArgs serviceArgs;
  /* eventsRef 必须先于三个转发监听声明：监听 lambda 在字段初始化时捕获它。 */
  private volatile AdbShellManager.Events eventsRef=new AdbShellManager.Events(){
    @Override public void binderReceived(){}
    @Override public void binderDead(){}
    @Override public void permissionResult(int requestCode,int result){}
  };
  private final Shizuku.OnBinderReceivedListener binderReceived=()->eventsRef.binderReceived();
  private final Shizuku.OnBinderDeadListener binderDead=()->eventsRef.binderDead();
  private final Shizuku.OnRequestPermissionResultListener permissionResult=(requestCode,result)->eventsRef.permissionResult(requestCode,result);
  private boolean registered;

  ShizukuPlatform(Context context){
    this.context=context;
    serviceArgs=new Shizuku.UserServiceArgs(new ComponentName(context.getPackageName(),AdbShellService.class.getName())).daemon(false).processNameSuffix("adb-shell").debuggable(BuildConfig.DEBUG).version(BuildConfig.VERSION_CODE);
  }

  @Override public boolean installed(){try{context.getPackageManager().getPackageInfo("moe.shizuku.privileged.api",0);return true;}catch(Throwable ignored){return false;}}
  @Override public boolean pingBinder(){return Shizuku.pingBinder();}
  @Override public boolean isPreV11(){return Shizuku.isPreV11();}
  @Override public int checkSelfPermission(){return Shizuku.checkSelfPermission();}
  @Override public boolean shouldShowRequestPermissionRationale(){return Shizuku.shouldShowRequestPermissionRationale();}
  @Override public int getUid(){return Shizuku.getUid();}
  @Override public void requestPermission(int requestCode){Shizuku.requestPermission(requestCode);}
  @Override public void bindUserService(ServiceConnection connection){Shizuku.bindUserService(serviceArgs,connection);}
  @Override public void unbindUserService(ServiceConnection connection){if(Shizuku.pingBinder())Shizuku.unbindUserService(serviceArgs,connection,false);}

  @Override public void addEvents(AdbShellManager.Events events){
    eventsRef=events;registered=true;
    Shizuku.addBinderReceivedListenerSticky(binderReceived);Shizuku.addBinderDeadListener(binderDead);Shizuku.addRequestPermissionResultListener(permissionResult);
  }

  @Override public void removeEvents(AdbShellManager.Events events){
    if(!registered)return;registered=false;
    Shizuku.removeBinderReceivedListener(binderReceived);Shizuku.removeBinderDeadListener(binderDead);Shizuku.removeRequestPermissionResultListener(permissionResult);
  }
}
