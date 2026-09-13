package cc.nkbr.lanzouplus;

/**
 * v1.8.0 整搬 RikkaHub：Application 必须在进程内任何 Koin 消费者（含 WorkManager 的
 * Configuration.Provider 流程与 androidx.startup ContentProvider）之前完成 startKoin。
 * 上游 me.rerere.rikkahub.RikkaHubApp#onCreate 已包含：startKoin 四模块装配、通知渠道、
 * QuickJS 原生库初始化、崩溃处理器、备份恢复、Web 服务按需启动。
 * 上游清单里的 android:name=".RikkaHubApp" 作为库清单合并时不生效（宿主属性优先），
 * 因此由本类显式继承并挂到宿主 application 节点上。
 *
 * v1.9.0（问题表单#2）：onCreate 全链加兜底——备份恢复 journal 损坏、QuickJS 原生库
 * 加载失败等任一环抛非受控异常时，置 DEGRADED=true 保住蓝奏云/工具主功能（Application
 * 阶段的崩溃到不了 RouteActivity 的安全模式检查，不兜底就是无限崩溃循环变砖）。
 * DEGRADED 时 MainActivity 的 AI 入口拦截并提示。上游各环自身仍按原逻辑运行。
 */
public class App extends me.rerere.rikkahub.RikkaHubApp {
    public static volatile boolean DEGRADED = false;

    @Override
    public void onCreate() {
        try {
            super.onCreate();
        } catch (Throwable t) {
            DEGRADED = true;
            android.util.Log.e("DfwxApp", "RikkaHub init failed, entering degraded mode", t);
        }
    }
}
