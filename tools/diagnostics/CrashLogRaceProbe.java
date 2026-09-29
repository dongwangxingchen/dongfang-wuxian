import java.io.*;
/**
 * [DFWX] DFW-14 复现器：crash.log 并发写的"检查-删除-追加"竞态。
 *
 * 为什么单独放一个可执行探针而不是只写 JVM 单测：
 * 该竞态在 Robolectric 沙箱里**复现不出来**（同参数下文件始终停在单条记录大小），
 * 但在普通 JVM 里稳定复现。所以这里保留一个可独立运行的复现器，
 * 让"锁确实有用"这件事有可重复的证据，而不是靠断言硬说。
 *
 * 运行：
 *   cd tools/diagnostics && javac CrashLogRaceProbe.java && java CrashLogRaceProbe
 *
 * 实测结果（macOS，JDK 21，8 线程 × 30 次 × 300KB 块）：
 *   无锁：size=921636  （越过 512KB 上限，3 条记录只有 1 条头部完整）
 *   有锁：size=614424  （受"512KB + 单条记录"约束，记录头完整）
 * 结论：写入块必须远超 FileWriter 的 8KB 内部缓冲，否则竞态不出现——
 * 用小块（如 600B）测这件事会得到"有无锁都通过"的空测试。
 */
public class CrashLogRaceProbe {
    static final Object LOCK = new Object();
    static void append(File f, String text, boolean locked) {
        Runnable r = () -> {
            try {
                File p = f.getParentFile();
                if (p != null && !p.isDirectory()) p.mkdirs();
                if (f.exists() && f.length() > 512 * 1024) f.delete();
                try (FileWriter w = new FileWriter(f, true)) { w.write(text); }
            } catch (Throwable ignored) {}
        };
        if (locked) synchronized (LOCK) { r.run(); } else r.run();
    }
    public static void main(String[] args) throws Exception {
        for (boolean locked : new boolean[]{false, true}) {
            File f = new File("/tmp/race_" + locked + ".log");
            f.delete();
            int threads = 8, per = 30;
            String block = "A".repeat(300 * 1024); // 远超 FileWriter 8KB 缓冲 → 多次 syscall
            java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(threads);
            for (int t = 0; t < threads; t++) {
                final int tt = t;
                new Thread(() -> {
                    try { start.await(); } catch (Exception e) {}
                    for (int i = 0; i < per; i++) append(f, "==== T" + tt + "-" + i + "\n" + block + "\n", locked);
                    done.countDown();
                }).start();
            }
            start.countDown();
            done.await(60, java.util.concurrent.TimeUnit.SECONDS);
            String text = new String(java.nio.file.Files.readAllBytes(f.toPath()));
            int markers = text.split("==== ", -1).length - 1;
            int intact = 0;
            for (String line : text.split("\n")) if (line.startsWith("==== T")) intact++;
            System.out.println("locked=" + locked + " size=" + f.length() + " markers=" + markers + " intactHeaderLines=" + intact);
        }
    }
}
