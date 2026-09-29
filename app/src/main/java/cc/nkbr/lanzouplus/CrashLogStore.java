package cc.nkbr.lanzouplus;

import java.io.File;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * [DFWX] DFW-29（ARCH-001）宿主领域拆分 · 第二步：**崩溃日志的落盘与读取**。
 *
 * ## 责任边界（这是本类的全部职责）
 * 决定"崩溃日志放哪里、怎么命名、怎么写、怎么读、怎么清"，**不涉及任何 UI**。
 * 展示层（崩溃日志页面、Toast、分享 Intent）仍留在 `MainActivity`。
 *
 * ## 为什么要先给出这些静态方法而不是直接把 File 逻辑搬过来
 * 原实现直接调 `MainActivity.getExternalFilesDir()` / `App.publicCrashFolder()`，
 * 与 Activity 生命周期绑死。这里改成**接收目录参数**，于是：
 *  1. 逻辑可以在 JVM 里用临时目录直测（不必启动 Activity）；
 *  2. 主 Activity 用两个一行的 provider 把真实目录喂进来，行为完全不变。
 *
 * ## 行为等价性
 * 方法体逐字搬自 `MainActivity`（v1.22.10 起的双写与固定名副本语义、12K 尾部读取、
 * `dfwx-crash-` 前缀过滤、静默失败吞异常等都保持一致），
 * 只把"目录从哪来"这一步参数化。抽取后全量宿主测试必须保持守恒。
 */
final class CrashLogStore {
  private CrashLogStore(){}

  /**
   * 日志出口。默认**空实现**，由 App 侧注入真实的 `android.util.Log`。
   *
   * 为什么要这么绕：`android.util.Log` 在**纯 JVM 单测**里是未 mock 的桩，一调用就抛
   * `RuntimeException: Method w in android.util.Log not mocked`。
   * 而这个类的价值恰恰在于"可以用临时目录直测"——若为了记一行日志就必须拉起 Robolectric，
   * 抽取的意义就打了对折。默认空实现让它在两种环境下都能跑。
   */
  interface Logger { void warn(String message,Throwable error); }

  private static volatile Logger logger=(message,error)->{};

  static void setLogger(Logger value){logger=value==null?(message,error)->{}:value;}

  private static void warn(String message,Throwable error){
    try{logger.warn(message,error);}catch(Throwable ignored){/* 日志器自身不许抛 */}
  }

  /** 尾部读取上限（原实现写死 12000 字节）。 */
  static final int TAIL_BYTES = 12000;

  /** 报告文件名前缀，用于"只清理自己的文件、不误删用户文件"。 */
  static final String REPORT_PREFIX = "dfwx-crash-";

  /** 固定名副本（方便用户下次直接取用）。 */
  static final String LATEST_NAME = "dfwx-crash-latest.txt";

  /** 私有目录里的日志名。 */
  static final String PRIVATE_LOG_NAME = "crash.log";

  /** 报告文件名：纯 ASCII（时间 + 版本号）。中文名会在分享/保存链路上被截断（v1.22.8 事故同源）。 */
  static String reportFileName(String versionName){
    String stamp=new SimpleDateFormat("yyyyMMdd-HHmmss",Locale.US).format(new Date());
    return REPORT_PREFIX+stamp+"-v"+versionName+".txt";
  }

  /** 写入字节；失败静默（崩溃路径与导出路径都不许因为写盘失败再抛）。 */
  static void writeBytesQuietly(File file,byte[] bytes){
    if(file==null)return;
    try{
      File parent=file.getParentFile();
      if(parent!=null&&!parent.isDirectory()) //noinspection ResultOfMethodCallIgnored
        parent.mkdirs();
      FileOutputStream out=new FileOutputStream(file);
      try{out.write(bytes);}finally{out.close();}
    }catch(Exception ignored){
      warn("write failed: "+ignored.getMessage(),ignored);
    }
  }

  static void deleteQuietly(File file){
    try{if(file!=null&&file.exists()) //noinspection ResultOfMethodCallIgnored
      file.delete();}catch(Exception ignored){
      warn("delete failed: "+ignored.getMessage(),ignored);
    }
  }

  /**
   * 把报告写入给定目录，同时刷新固定名副本。目录为 null 时返回 null。
   * 返回写出的主文件（供调用方提示路径）。
   */
  static File writeReport(File dir,String versionName,String content){
    if(dir==null)return null;
    try{
      //noinspection ResultOfMethodCallIgnored
      dir.mkdirs();
      byte[] bytes=content.getBytes(StandardCharsets.UTF_8);
      File file=new File(dir,reportFileName(versionName));
      writeBytesQuietly(file,bytes);
      writeBytesQuietly(new File(dir,LATEST_NAME),bytes);
      return file;
    }catch(Exception error){
      return null;
    }
  }

  /** 目录下由本应用生成的报告（只认 `dfwx-crash-` 前缀，避免误删用户自己的文件）。 */
  static List<File> reportFiles(File dir){
    List<File> out=new ArrayList<>();
    if(dir!=null&&dir.isDirectory()){
      File[] files=dir.listFiles();
      if(files!=null)for(File file:files){
        if(file.isFile()&&file.getName().startsWith(REPORT_PREFIX))out.add(file);
      }
    }
    return out;
  }

  /** 读日志尾部（最多 {@link #TAIL_BYTES} 字节）；任何异常都退化为空串。 */
  static String readTail(File file){
    try{
      if(file==null||!file.exists()||file.length()==0)return "";
      long size=file.length();
      long start=Math.max(0,size-TAIL_BYTES);
      RandomAccessFile raf=new RandomAccessFile(file,"r");
      try{
        raf.seek(start);
        byte[] buf=new byte[(int)(size-start)];
        raf.readFully(buf);
        return new String(buf,StandardCharsets.UTF_8);
      }finally{
        raf.close();
      }
    }catch(Exception error){
      return "";
    }
  }
}
