/*
 * Decompiled with CFR 0.152.
 */
package com.ankki.druid.parser.template;

import com.ankki.druid.parser.CustomerOutputVisitorUtils;
import com.ankki.druid.parser.template.AkLightweightCachedOutputVisitorUtils;
import com.sun.management.OperatingSystemMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.ThreadMXBean;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class AkLightweightCachedDruidSqlMonitor {
    private static final long NANOS_PER_SEC = 1000000000L;
    private static long prevTotalCalls = 0L;
    private static long prevTimestampNanos = System.nanoTime();
    private static long prevProcessCpuNanos = 0L;
    private static boolean firstSample = true;
    private static volatile String qpsStr = "QPS: N/A";
    private static volatile String cpuPerSqlStr = "CPU/SQL: N/A";

    static void sampleMetrics() {
        long currentCalls = CustomerOutputVisitorUtils.TOTAL_CALLS.longValue();
        long currentNanos = System.nanoTime();
        long currentCpuNanos = AkLightweightCachedDruidSqlMonitor.getProcessCpuNanos();
        if (firstSample) {
            prevTotalCalls = currentCalls;
            prevTimestampNanos = currentNanos;
            prevProcessCpuNanos = currentCpuNanos;
            firstSample = false;
            qpsStr = "QPS: N/A (first sample)";
            cpuPerSqlStr = "CPU/SQL: N/A (first sample)";
            return;
        }
        long deltaCalls = currentCalls - prevTotalCalls;
        long deltaNanos = currentNanos - prevTimestampNanos;
        long deltaCpuNanos = currentCpuNanos - prevProcessCpuNanos;
        prevTotalCalls = currentCalls;
        prevTimestampNanos = currentNanos;
        prevProcessCpuNanos = currentCpuNanos;
        if (deltaCalls > 0L && deltaNanos > 0L) {
            long qps = deltaCalls * 1000000000L / deltaNanos;
            qpsStr = String.format("QPS: %d (total: %d, interval: %.1fs)", qps, currentCalls, (double)deltaNanos / 1.0E9);
        }
        if (deltaCalls > 0L && deltaCpuNanos > 0L) {
            long cpuUsPerSql = deltaCpuNanos / deltaCalls / 1000L;
            double cpuPct = currentCpuNanos > 0L ? 100.0 * (double)deltaCpuNanos / (double)deltaNanos : -1.0;
            cpuPerSqlStr = String.format("CPU/SQL: %d us (%.1f%% CPU)", cpuUsPerSql, cpuPct);
        }
    }

    public static String getQpsStr() {
        return qpsStr;
    }

    public static String getCpuPerSqlStr() {
        return cpuPerSqlStr;
    }

    private static long getProcessCpuNanos() {
        try {
            java.lang.management.OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
            if (os instanceof OperatingSystemMXBean) {
                return ((OperatingSystemMXBean)os).getProcessCpuTime();
            }
        }
        catch (Exception exception) {
            // empty catch block
        }
        return -1L;
    }

    public static double getProcessCpuLoad() {
        try {
            java.lang.management.OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
            if (os instanceof OperatingSystemMXBean) {
                return ((OperatingSystemMXBean)os).getProcessCpuLoad();
            }
        }
        catch (Exception exception) {
            // empty catch block
        }
        return -1.0;
    }

    public static double getSystemCpuLoad() {
        try {
            java.lang.management.OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
            if (os instanceof OperatingSystemMXBean) {
                return ((OperatingSystemMXBean)os).getSystemCpuLoad();
            }
        }
        catch (Exception exception) {
            // empty catch block
        }
        return -1.0;
    }

    public static int getAvailableProcessors() {
        return ManagementFactory.getOperatingSystemMXBean().getAvailableProcessors();
    }

    public static String getMemoryInfo() {
        MemoryMXBean mem = ManagementFactory.getMemoryMXBean();
        MemoryUsage heap = mem.getHeapMemoryUsage();
        MemoryUsage nonHeap = mem.getNonHeapMemoryUsage();
        return String.format("Heap: used=%dMB / max=%dMB (%.1f%%) | NonHeap: used=%dMB / max=%dMB", heap.getUsed() >> 20, heap.getMax() >> 20, 100.0 * (double)heap.getUsed() / (double)Math.max(heap.getMax(), 1L), nonHeap.getUsed() >> 20, nonHeap.getMax() >> 20);
    }

    public static String getThreadInfo() {
        ThreadMXBean thread = ManagementFactory.getThreadMXBean();
        return String.format("Threads: live=%d / peak=%d / daemon=%d", thread.getThreadCount(), thread.getPeakThreadCount(), thread.getDaemonThreadCount());
    }

    public static String getGCInfo() {
        StringBuilder sb = new StringBuilder("GC: ");
        List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
        for (GarbageCollectorMXBean gc : gcBeans) {
            long count = gc.getCollectionCount();
            long time = gc.getCollectionTime();
            sb.append(gc.getName()).append("=").append(count).append("(").append(time).append("ms) ");
        }
        return sb.toString();
    }

    public static String getCacheStats() {
        try {
            return AkLightweightCachedOutputVisitorUtils.getCacheStats();
        }
        catch (Exception e) {
            return "Cache stats unavailable: " + e.getMessage();
        }
    }

    public static String getJvmArgs() {
        try {
            return String.join((CharSequence)" ", ManagementFactory.getRuntimeMXBean().getInputArguments());
        }
        catch (Exception e) {
            return "N/A";
        }
    }

    public static void printAll(Appendable out) {
        try {
            AkLightweightCachedDruidSqlMonitor.sampleMetrics();
            long uptimeMs = ManagementFactory.getRuntimeMXBean().getUptime();
            long startTimeMs = ManagementFactory.getRuntimeMXBean().getStartTime();
            String now = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
            String start = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(startTimeMs));
            out.append("=== DruidSqlMonitor ===\n");
            out.append("Start: ").append(start).append(", Current: ").append(now).append(",").append(", Uptime: ").append(String.valueOf(uptimeMs / 1000L)).append("s\n");
            out.append("PID: ").append(ManagementFactory.getRuntimeMXBean().getName()).append("\n");
            out.append("Processors: ").append(String.valueOf(AkLightweightCachedDruidSqlMonitor.getAvailableProcessors())).append("\n");
            out.append("Process CPU: ").append(AkLightweightCachedDruidSqlMonitor.formatPct(AkLightweightCachedDruidSqlMonitor.getProcessCpuLoad())).append("\n");
            out.append("System CPU:  ").append(AkLightweightCachedDruidSqlMonitor.formatPct(AkLightweightCachedDruidSqlMonitor.getSystemCpuLoad())).append("\n");
            out.append(AkLightweightCachedDruidSqlMonitor.getQpsStr()).append("\n");
            out.append(AkLightweightCachedDruidSqlMonitor.getCpuPerSqlStr()).append("\n");
            out.append(AkLightweightCachedDruidSqlMonitor.getMemoryInfo()).append("\n");
            out.append(AkLightweightCachedDruidSqlMonitor.getThreadInfo()).append("\n");
            out.append(AkLightweightCachedDruidSqlMonitor.getGCInfo()).append("\n");
            out.append("Cache: ").append(AkLightweightCachedDruidSqlMonitor.getCacheStats()).append("\n");
            out.append("JVM args: ").append(AkLightweightCachedDruidSqlMonitor.getJvmArgs()).append("\n");
            out.append("=== End ===\n");
        }
        catch (Exception e) {
            try {
                out.append("Monitor error: ").append(e.getMessage()).append("\n");
            }
            catch (Exception exception) {
                // empty catch block
            }
        }
    }

    private static String formatPct(double v) {
        return v < 0.0 ? "N/A" : String.format("%.1f%%", v * 100.0);
    }

    public static String getOsMonitorScript() {
        return "#!/bin/bash\necho === $(date '+%Y-%m-%d %H:%M:%S') ===\necho '--- Top CPU processes ---'\nps aux --sort=-%cpu | head -20\necho '--- Java processes ---'\nps aux | grep -i java | grep -v grep\necho '--- Load average ---'\ncat /proc/loadavg\necho '--- Memory ---'\nfree -m\necho '--- Disk I/O ---'\niostat -x 1 1 2>/dev/null | tail -20 || echo 'iostat not available'\necho '--- Network ---'\nsar -n DEV 1 1 2>/dev/null | tail -10 || echo 'sar not available'\n";
    }

    public static String getJavaThreadCpuScript() {
        return "#!/bin/bash\necho '=== Java Thread CPU per PID ==='\nfor pid in $(pgrep -f 'druid-ak'); do\n  if [ -z \"$pid\" ]; then continue; fi\n  echo \"--- PID $pid $(ps -p $pid -o cmd= | head -c 80) ---\"\n  ps -p $pid -o pid,%cpu,%mem,rss,vsz --no-headers\n  echo 'Top 10 threads by CPU:'\n  top -H -b -n 1 -p $pid 2>/dev/null | tail -12 | head -10\n  echo 'Thread dump (signal):'\n  kill -3 $pid 2>/dev/null && echo '  Sent to stdout' || echo '  Cannot signal'\ndone\n";
    }
}

