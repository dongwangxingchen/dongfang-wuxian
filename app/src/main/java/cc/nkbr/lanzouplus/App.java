package cc.nkbr.lanzouplus;

/**
 * v1.8.0 整搬 RikkaHub：Application 必须在进程内任何 Koin 消费者（含 WorkManager 的
 * Configuration.Provider 流程与 androidx.startup ContentProvider）之前完成 startKoin。
 * 上游 me.rerere.rikkahub.RikkaHubApp#onCreate 已包含：startKoin 四模块装配、通知渠道、
 * QuickJS 原生库初始化、崩溃处理器、备份恢复、Web 服务按需启动。
 * 上游清单里的 android:name=".RikkaHubApp" 作为库清单合并时不生效（宿主属性优先），
 * 因此由本类显式继承并挂到宿主 application 节点上。
 */
public class App extends me.rerere.rikkahub.RikkaHubApp {
}
