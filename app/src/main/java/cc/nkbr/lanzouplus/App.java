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
        super.attachBaseContext(wrapSimplifiedChinese(base));
        // 日志器尽量早装：ContentProvider 比 Application.onCreate 更早执行，
        // 崩在 Provider（如 FirebaseInitProvider）时 onCreate 里的日志器来不及装。
        installCrashLogger();
    }

    /**
     * v1.19.9 整包默认简体中文：vendor（RikkaHub）资源跟随系统语言，系统是英文时 AI 页变英文
     * （主 app Java 字符串硬编码中文才显得中英混合）。官方 per-app language 的 API<33 路径
     * 需要 appcompat 依赖（项目禁），改用 createConfigurationContext 包装——零依赖全版本生效。
     * 各 Activity 需在自身 attachBaseContext 里同样包装（见 MainActivity）。
     */
    public static android.content.Context wrapSimplifiedChinese(android.content.Context base) {
        try {
            android.content.res.Configuration config = new android.content.res.Configuration(base.getResources().getConfiguration());
            java.util.Locale zh = java.util.Locale.SIMPLIFIED_CHINESE;
            config.setLocale(zh);
            config.setLocales(new android.os.LocaleList(zh));
            return base.createConfigurationContext(config);
        } catch (Throwable t) {
            android.util.Log.w("DfwxApp", "wrapSimplifiedChinese failed, fallback to system locale", t);
            return base;
        }
    }

    @Override
    public void onCreate() {
        // DFW-29：把真实日志出口注入 CrashLogStore（默认是空实现，便于纯 JVM 单测）。
        CrashLogStore.setLogger((message,error)->android.util.Log.w("CrashLogStore",message,error));
        installCrashLogger();
        // [DFW-73] 内置渠道配置必须赶在 super.onCreate() 之前注入：RikkaHub 的 Application.onCreate
        // 会立刻触发渠道同步，晚一步这一轮就同步不到配置（下一次启动才生效）。
        BuiltinAiChannel.install(this);
        /*
         * [DFW-88 2026-10-02] 注册 WAF 求解器所需的 Context。
         *
         * 蓝奏云的下载链路有一道阿里云 WAF 的 JS 挑战（`acw_sc__v2`）。
         * 项目自己实现的算法**已经算错了**（阿里云改过算法，社区常量失效，有实测证据），
         * 导致算出的 cookie 不被接受、下载永远停在「解析中」。
         * 现在改用隐藏 WebView 让它自己算 —— 但 WebView 要 Context，
         * 而 LanzouCore 全是静态方法，所以在这里注册一次。
         *
         * 放在 super.onCreate() 之前：注册本身零开销（只存一个引用），
         * 早注册可以保证**任何**路径（含 RikkaHub 侧触发的下载）都能用上。
         */
        LanzouCore.installWafSolver(this);
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

    /** 测试专用入口：暴露给 JVM 测试验证并发写不交错（生产代码走 installCrashLogger 的处理器）。 */
    static void writeCrashLogForTest(String kind, Throwable t) {
        writeCrashLog(kind, t);
    }

    static void writeCrashLog(String kind, Throwable t) {
        try {
            App app = instance;
            if (app == null) return;
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            pw.println("==== " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()) + " " + kind);
            if (t != null) t.printStackTrace(pw);
            pw.print(diagnostics());
            String text = sw.toString();
            // 双写（v1.22.10，用户 2026-09-29 反馈"在 MT 管理器里找不到"）：
            // ① 公共目录 Download/东方无限/崩溃日志/crash.log —— 文件管理器直接可见，用户能手取；
            // ② 应用私有目录 —— 无需任何权限，作为权威副本，保证没授权时也不丢现场。
            // 私有副本必须始终写：崩溃发生在授权之前、或用户点了"暂不"时，公共写入会失败。
            File publicFile = publicCrashLogFile();
            if (publicFile != null) appendCrashText(publicFile, text);
            File privateDir = app.getExternalFilesDir(null);
            if (privateDir == null) privateDir = app.getFilesDir();
            if (privateDir != null) appendCrashText(new File(privateDir, "crash.log"), text);
        } catch (Throwable ignored) {
            // 日志器自身绝不许再抛
        }
    }

    /**
     * 崩溃日志的写锁（DFW-14）。
     *
     * 为什么必须锁：日志器装在**所有线程**的未捕获异常处理器上，多线程同时崩溃时
     * 两个 `FileWriter(append)` 会并发写同一文件，日志内容互相交错、行被撕开，
     * 恰恰在最需要看清现场的时候把现场毁掉。用一把静态锁把"检查大小 + 清空 + 追加"
     * 整段串行化（只锁写入这一段，不做别的耗时操作，崩溃路径不会被拖住）。
     */
    private static final Object CRASH_LOG_LOCK = new Object();

    /** 追加写入，超 512KB 先清空，避免无限膨胀；失败静默（崩溃路径不许再抛）。 */
    private static void appendCrashText(File file, String text) {
      synchronized (CRASH_LOG_LOCK) {
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.isDirectory()) //noinspection ResultOfMethodCallIgnored
                parent.mkdirs();
            if (file.exists() && file.length() > 512 * 1024) //noinspection ResultOfMethodCallIgnored
                file.delete();
            try (FileWriter fw = new FileWriter(file, true)) {
                fw.write(text);
            }
        } catch (Throwable ignored) {
            // 单个位置写入失败不影响另一个位置
        }
      }
    }

    /**
     * 面向用户的公共目录：`Download/东方无限/崩溃日志/`（v1.22.10）。
     * 之所以不放 `getExternalFilesDir()`：Android 11+ 的 `Android/data/<包名>/` 在文件管理器里
     * 默认不可见（用户反馈"甚至没找到那个文件夹"），导出给用户看的东西必须放在公共目录。
     * 返回 null 表示环境不支持（由调用方回退到私有目录）。
     */
    public static File publicCrashFolder() {
        try {
            File downloads = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS);
            if (downloads == null) return null;
            return new File(new File(downloads, "东方无限"), "崩溃日志");
        } catch (Throwable t) {
            return null;
        }
    }

    /** 公共目录下的 crash.log；预先建好目录，便于用户第一时间在文件管理器里看到它。 */
    public static File publicCrashLogFile() {
        File folder = publicCrashFolder();
        if (folder == null) return null;
        //noinspection ResultOfMethodCallIgnored
        folder.mkdirs();
        return folder.isDirectory() ? new File(folder, "crash.log") : null;
    }


    /**
     * 诊断信息块：崩溃报告与 crash.log 共用同一份事实源，避免两处各写各的导致字段漂移。
     * 字段选取参照 ACRA 的报告字段约定（应用版本 / 设备 / 系统 / ABI / 屏幕 / 内存 / 运行时长），
     * 全部为只读的公开环境信息，不含任何凭据、账号或用户内容。
     */
    public static String diagnostics() {
        StringBuilder sb = new StringBuilder();
        sb.append("-- 应用 --\n");
        App app = instance;
        try {
            if (app != null) {
                String pkg = app.getPackageName();
                android.content.pm.PackageInfo info = app.getPackageManager().getPackageInfo(pkg, 0);
                long code = android.os.Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
                sb.append("包名: ").append(pkg).append('\n');
                sb.append("版本: ").append(info.versionName).append(" (").append(code).append(")\n");
            }
        } catch (Throwable ignored) {
            sb.append("版本: 读取失败\n");
        }
        sb.append("降级模式: ").append(DEGRADED ? "是" : "否").append('\n');
        sb.append("\n-- 设备 --\n");
        sb.append("厂商: ").append(android.os.Build.MANUFACTURER).append('\n');
        sb.append("品牌: ").append(android.os.Build.BRAND).append('\n');
        sb.append("型号: ").append(android.os.Build.MODEL).append('\n');
        sb.append("设备代号: ").append(android.os.Build.DEVICE).append('\n');
        sb.append("主板: ").append(android.os.Build.BOARD).append('\n');
        sb.append("ABI: ").append(android.os.Build.SUPPORTED_ABIS == null ? "未知" : String.join(", ", android.os.Build.SUPPORTED_ABIS)).append('\n');
        sb.append("\n-- 系统 --\n");
        sb.append("Android: ").append(android.os.Build.VERSION.RELEASE).append(" (API ").append(android.os.Build.VERSION.SDK_INT).append(")\n");
        sb.append("安全补丁: ").append(android.os.Build.VERSION.SDK_INT >= 23 ? android.os.Build.VERSION.SECURITY_PATCH : "未知").append('\n');
        sb.append("构建号: ").append(android.os.Build.ID).append('\n');
        sb.append("指纹: ").append(android.os.Build.FINGERPRINT).append('\n');
        try {
            if (app != null) {
                java.util.Locale locale = app.getResources().getConfiguration().getLocales().get(0);
                sb.append("语言: ").append(locale == null ? "未知" : locale.toString()).append('\n');
                android.util.DisplayMetrics metrics = app.getResources().getDisplayMetrics();
                sb.append("屏幕: ").append(metrics.widthPixels).append('x').append(metrics.heightPixels)
                        .append(" @").append(metrics.densityDpi).append("dpi").append('\n');
                android.app.ActivityManager manager = (android.app.ActivityManager) app.getSystemService(android.content.Context.ACTIVITY_SERVICE);
                if (manager != null) {
                    android.app.ActivityManager.MemoryInfo memory = new android.app.ActivityManager.MemoryInfo();
                    manager.getMemoryInfo(memory);
                    sb.append("物理内存: 总 ").append(megabytes(memory.totalMem)).append(" / 可用 ").append(megabytes(memory.availMem)).append('\n');
                    sb.append("低内存状态: ").append(memory.lowMemory ? "是" : "否").append('\n');
                }
            }
        } catch (Throwable ignored) {
            // 环境信息缺失不阻断报告生成
        }
        Runtime runtime = Runtime.getRuntime();
        sb.append("堆内存: 上限 ").append(megabytes(runtime.maxMemory()))
                .append(" / 已用 ").append(megabytes(runtime.totalMemory() - runtime.freeMemory())).append('\n');
        sb.append("设备运行时长: ").append(android.os.SystemClock.elapsedRealtime() / 1000L).append(" 秒\n");
        sb.append("生成时间: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date())).append('\n');
        return sb.toString();
    }

    private static String megabytes(long bytes) {
        return (bytes / (1024L * 1024L)) + "MB";
    }
}
