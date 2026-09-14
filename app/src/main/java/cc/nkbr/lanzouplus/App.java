package cc.nkbr.lanzouplus;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * v1.8.0 整搬 RikkaHub：Application 必须在进程内任何 Koin 消费者（含 WorkManager 的
 * Configuration.Provider 流程与 androidx.startup ContentProvider）之前完成 startKoin。
 * 上游清单里的 android:name=".RikkaHubApp" 作为库清单合并时不生效（宿主属性优先），
 * 因此由本类显式继承并挂到宿主 application 节点上。
 *
 * v1.9.0（问题表单#2）：onCreate 全链兜底——备份恢复 journal 损坏、QuickJS 原生库
 * 加载失败等任一环抛非受控异常时，置 DEGRADED=true 保住蓝奏云/工具主功能（Application
 * 阶段的崩溃到不了 RouteActivity 的安全模式检查，不兜底就是无限崩溃循环变砖）。
 *
 * v1.9.1：崩溃日志落盘——真机（Android 8.1 手表）出现"停止运行"但无 adb 可查，
 * 把未捕获异常与 DEGRADED 原因写入 getExternalFilesDir/crash.log（无需存储权限，
 * 文件管理器路径：内部存储/Android/data/dfwx.dongdang/files/crash.log），供截图回传定位。
 */
public class App extends me.rerere.rikkahub.RikkaHubApp {
    public static volatile boolean DEGRADED = false;
    private static volatile App instance;

    {
        instance = this;
    }

    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(base);
        // 日志器尽量早装：ContentProvider 比 Application.onCreate 更早执行，
        // 崩在 Provider（如 FirebaseInitProvider）时 onCreate 里的日志器来不及装。
        installCrashLogger();
    }

    @Override
    public void onCreate() {
        installCrashLogger();
        try {
            super.onCreate();
        } catch (Throwable t) {
            DEGRADED = true;
            android.util.Log.e("DfwxApp", "RikkaHub init failed, entering degraded mode", t);
            writeCrashLog("DEGRADED_MODE", t);
        }
    }

    private void installCrashLogger() {
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            writeCrashLog("UNCAUGHT thread=" + thread.getName(), throwable);
            if (previous != null) previous.uncaughtException(thread, throwable);
        });
    }

    static void writeCrashLog(String kind, Throwable t) {
        try {
            App app = instance;
            if (app == null) return;
            File dir = app.getExternalFilesDir(null);
            if (dir == null) dir = app.getFilesDir();
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
            File file = new File(dir, "crash.log");
            // 超 512KB 清掉重写，避免无限膨胀
            if (file.exists() && file.length() > 512 * 1024) //noinspection ResultOfMethodCallIgnored
                file.delete();
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            pw.println("==== " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()) + " " + kind);
            if (t != null) t.printStackTrace(pw);
            pw.println("device: " + android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL
                    + " android " + android.os.Build.VERSION.RELEASE + " (API " + android.os.Build.VERSION.SDK_INT + ")");
            try (FileWriter fw = new FileWriter(file, true)) {
                fw.write(sw.toString());
            }
        } catch (Throwable ignored) {
            // 日志器自身绝不许再抛
        }
    }
}
