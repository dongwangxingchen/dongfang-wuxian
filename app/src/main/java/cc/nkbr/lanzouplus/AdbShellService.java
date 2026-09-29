package cc.nkbr.lanzouplus;

import android.content.Context;
import android.os.ParcelFileDescriptor;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** Runs only inside Shizuku/Sui with the real shell or root identity. */
public final class AdbShellService extends IAdbShellService.Stub {
  private static final long INSTALL_TIMEOUT_MS=5*60*1000L;

  public AdbShellService(){}
  public AdbShellService(Context ignored){}

  @Override public void destroy(){System.exit(0);}

  @Override public String installApk(ParcelFileDescriptor source,long size){
    if(source==null||size<=0)return "ERROR\n安装包大小无效";
    Process process=null;
    try(ParcelFileDescriptor descriptor=source;InputStream input=new FileInputStream(descriptor.getFileDescriptor())){
      process=new ProcessBuilder("/system/bin/pm","install","-r","-S",Long.toString(size)).redirectErrorStream(true).start();
      long written=0;byte[] buffer=new byte[64*1024];
      try(OutputStream output=process.getOutputStream()){
        for(int count;(count=input.read(buffer))>=0;){if(count==0)continue;output.write(buffer,0,count);written+=count;}
      }
      if(written!=size){process.destroy();return "ERROR\n安装包读取不完整（"+written+"/"+size+"）";}
      long deadline=System.currentTimeMillis()+INSTALL_TIMEOUT_MS;int exit;
      while(true){try{exit=process.exitValue();break;}catch(IllegalThreadStateException running){if(System.currentTimeMillis()>=deadline){process.destroy();return "ERROR\n静默安装超时";}Thread.sleep(100);}}
      ByteArrayOutputStream response=new ByteArrayOutputStream();
      try(InputStream result=process.getInputStream()){for(int count;(count=result.read(buffer))>=0;){if(count>0)response.write(buffer,0,count);}}
      String message=new String(response.toByteArray(),StandardCharsets.UTF_8).trim();
      return installOutcome(exit,message);
    }catch(Throwable error){if(process!=null)process.destroy();String message=error.getMessage();return "ERROR\n"+(message==null||message.trim().isEmpty()?error.getClass().getSimpleName():message.trim());}
  }

  /**
   * [DFWX DFW-53] 判断 pm install 是否成功。
   *
   * **原实现的真实缺陷**：条件是 `exit==0 && message 含 "success"`。
   * `pm install` 的输出是**本地化**的——中文系统返回"成功"，日文返回"成功しました"，
   * 只有英文系统才含 "success"。于是在非英文系统上，**安装成功也会被判为失败**
   * （用户看到"失败"但其实已装好，重试还会遇到"已存在"更混乱）。
   *
   * 新判定：**以退出码为准**（这是语言无关的客观事实），文本只用于展示与辅助判断。
   * exit==0 视为成功；exit!=0 视为失败。若 exit==0 但输出里明显是错误关键字
   * （少数 ROM 会返回 0 却带失败文本），也按失败处理——这是保守方向的兜底。
   */
  static String installOutcome(int exit,String rawMessage){
    String message=rawMessage==null?"":rawMessage.trim();
    if(exit!=0)return "ERROR\n"+(message.isEmpty()?"pm install 返回 "+exit:message);
    if(looksLikeInstallFailure(message))return "ERROR\n"+message;
    return "OK\n"+(message.isEmpty()?"安装完成":message);
  }

  /**
   * 少量 ROM 在失败时也返回退出码 0，只能靠文本兜底。
   * 这里同时匹配英文与中文的失败词，**绝不**把"成功/成功しました"之类误判为失败。
   */
  private static boolean looksLikeInstallFailure(String message){
    if(message.isEmpty())return false;
    String lower=message.toLowerCase(java.util.Locale.ROOT);
    for(String marker:new String[]{"failure","failed","error","denied","not allowed","incompatible","no space","canceled","cancelled"}){
      if(lower.contains(marker))return true;
    }
    for(String marker:new String[]{"失败","拒绝","不允许","空间不足","已取消","不兼容"}){
      if(message.contains(marker))return true;
    }
    return false;
  }
}
