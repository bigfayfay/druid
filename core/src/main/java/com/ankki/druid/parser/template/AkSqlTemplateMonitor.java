package com.ankki.druid.parser.template;

import com.ankki.druid.parser.AkSqlParserStatusEnum;
import com.ankki.druid.parser.config.VmOptions;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.lang.management.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;

/**
 * SQL模板解析性能监控器
 * <p>按 {@link AkSqlParserStatusEnum} 分类统计，每个状态独立维护：
 * total, totalCostMs, idleCosts, costMs, costMsSpeed, totalCostMsSpeed, histogram</p>
 */
public class AkSqlTemplateMonitor {


    // ==================== 按状态分类的指标 Map ====================

    private static final Map<AkSqlParserStatusEnum, StatusMetrics> statusMetrics = new EnumMap<>(AkSqlParserStatusEnum.class);

    static {
        for (AkSqlParserStatusEnum s : AkSqlParserStatusEnum.values()) {
            statusMetrics.put(s, new StatusMetrics(s));
        }
    }

    // ==================== 配置常量 ====================

    /**
     * 单个状态计数器最大阈值，超过后自动重置（防止溢出）
     */
    private static final long MAX_TOTAL_THRESHOLD;


    public static final boolean MONITOR_ENABLED = VmOptions.getBoolean(VmOptions.MONITOR);
    public static final boolean CACHE_ENABLED = VmOptions.getBoolean(VmOptions.CACHE_USE);
    private static final AtomicBoolean MONITOR_STARTED = new AtomicBoolean(false);
    /** 类加载时间，用于计算累计运行时长 */
    private static final LocalDateTime CLASS_LOAD_TIME = LocalDateTime.now();
    private static final String MONITOR_PID;
    private static final String MONITOR_DIR;
    private static final int MONITOR_INTERVAL;
    private static final ThreadMXBean THREAD_MX_BEAN = ManagementFactory.getThreadMXBean();
//    private static final boolean CPU_TIME_SUPPORTED = THREAD_MX_BEAN.isCurrentThreadCpuTimeSupported();

    static {
        // Max threshold
        long threshold = Long.MAX_VALUE - Integer.MAX_VALUE;
        try {
            threshold = VmOptions.getLong(VmOptions.MONITOR_MAX_THRESHOLD);
        } catch (NumberFormatException e) {
            // use default
        }
        MAX_TOTAL_THRESHOLD = threshold;

        // PID
        String pid = "unknown";
        try {
            String name = ManagementFactory.getRuntimeMXBean().getName();
            int atIdx = name.indexOf(64);
            if (atIdx > 0) {
                pid = name.substring(0, atIdx);
            }
        } catch (Exception e) {
            // ignore
        }
        MONITOR_PID = pid;

        // Dir
        MONITOR_DIR = VmOptions.getValue(VmOptions.MONITOR_DIR);

        // Interval
        int interval = 60;
        try {
            interval = VmOptions.getInt(VmOptions.MONITOR_INTERVAL);
        } catch (NumberFormatException e) {
            // ignore
        }
        MONITOR_INTERVAL = Math.max(10, interval);
    }

    // ==================== 速率直方图分桶定义 ====================

    /**
     * 速率直方图分桶枚举（按耗时升序排列）
     * <p>集中定义阈值、速率标签、耗时标签，消除并行数组的隐式索引耦合。</p>
     *
     *
     * 桶                * 耗时区间 (ns)            * 含义
     * RATE_15K_PLUS     * [0, 67_000)            * ns < 67K 归入此桶
     * RATE_10K_15K      * [67_000, 100_000)      * 67K ≤ ns < 100K
     * RATE_7K5_10K      * [100_000, 133_000)     * 100K ≤ ns < 133K
     * RATE_5K_7K5       * [133_000, 200_000)     * 133K ≤ ns < 200K
     * RATE_3K_5K        * [200_000, 333_000)     * 200K ≤ ns < 333K
     * RATE_2K_3K        * [333_000, 500_000)     * 333K ≤ ns < 500K
     * RATE_1K5_2K       * [500_000, 667_000)     * 500K ≤ ns < 667K
     * RATE_1K_1K5       * [667_000, 1_000_000)   * 667K ≤ ns < 1M
     * RATE_500_1K       * [1_000_000, 2_000_000) * 1M ≤ ns < 2M
     * RATE_LT_500       * [2_000_000, +∞)        * ns ≥ 2M 归入此桶
     *
     */
    enum HistBucket {
        RATE_15K_PLUS(67_000,        ">15K     ops/s", "<67μs"),
        RATE_10K_15K (100_000,       "10-15K   ops/s", "67-100μs"),
        RATE_7K5_10K (133_000,       "7.5-10K  ops/s", "100-133μs"),
        RATE_5K_7K5  (200_000,       "5-7.5K   ops/s", "133-200μs"),
        RATE_3K_5K   (333_000,       "3-5K     ops/s", "200-333μs"),
        RATE_2K_3K   (500_000,       "2-3K     ops/s", "333-500μs"),
        RATE_1K5_2K  (667_000,       "1.5-2K   ops/s", "500-667μs"),
        RATE_1K_1K5  (1_000_000,     "1-1.5K   ops/s", "667μs-1ms"),
        RATE_500_1K  (2_000_000,     "500-1K   ops/s", "1-2ms"),
        RATE_LT_500  (Long.MAX_VALUE,           "<500     ops/s", ">2ms");

        /** 上界阈值（ns），耗时小于此值归入本桶 */
        final long thresholdNs;
        /** ops/s 维度标签 */
        final String speedLabel;
        /** 耗时维度标签 */
        final String costLabel;

        HistBucket(long thresholdNs, String speedLabel, String costLabel) {
            this.thresholdNs = thresholdNs;
            this.speedLabel = speedLabel;
            this.costLabel = costLabel;
        }

        /** 桶数量 */
        static final int COUNT = values().length;
        /** 类加载时提取阈值数组，供热路径使用（避免枚举遍历开销） */
        static final long[] THRESHOLDS;
        static {
            HistBucket[] vals = values();
            THRESHOLDS = new long[vals.length];
            for (int i = 0; i < vals.length; i++) {
                THRESHOLDS[i] = vals[i].thresholdNs;
            }
        }
    }

    // ==================== 按状态统计的数据结构 ====================
    /**
     * 单个状态的完整指标集合（线程安全，高并发优化）
     * <p>使用 {@link LongAdder} 替代 AtomicLong 提升高竞争场景下的写入性能。</p>
     * <pre>
     * 数据结构: {status, total, totalCostNs, idleCostMs, costMs, costMsSpeed, totalCostMsSpeed, HISTOGRAM[]}
     * </pre>
     */
    public static class StatusMetrics {
        /**
         * 对应的解析状态
         */
        final AkSqlParserStatusEnum status;
        /**
         * 统计起始时间（毫秒），用于计算实际运行时长
         */
        volatile long startTimeMs;
        volatile LocalDateTime startTime;
        /**
         * 总调用次数
         */
        final LongAdder total = new LongAdder();
        /**
         * 总耗时（纳秒）
         */
        final LongAdder execTotalCostNs = new LongAdder();
        /**
         * SQL 总字符长度（用于计算平均长度）
         */
        final LongAdder totalSqlLen = new LongAdder();

        /** 总空闲/等待时间（毫秒） */
//        private final LongAdder idleCostMs = new LongAdder();

        /** 单次最大耗时（毫秒） */
//        final LongAccumulator maxCostMs = new LongAccumulator(Math::max, 0L);
        /**
         * 毫秒级单位比较大，调小单位，使用微秒作为基本单位；
         * 1000/s ~ 200000/s 对应每条耗时 1ms ~ 0.005ms(5 μs)，也就是 1,000,000ns ~ 5,000ns。
         * 耗时直方图（微秒区间）: [0-10μs), [10-100μs), [100μs-1ms), [1-10ms), [10-100ms), [100ms+)
         *
         *
         *
         * 速率基直方图，按单次操作速率(ops/s)分桶：
         * >15K, 10-15K, 7.5-10K, 5-7.5K, 3-5K, 2-3K, 1.5-2K, 1-1.5K, 500-1K, <500
         * 对应的 ns 阈值： 67K, 100K, 133K, 200K, 333K, 500K, 667K, 1M, 2M
         */
        final LongAdder[] histogram = new LongAdder[HistBucket.COUNT];

        public StatusMetrics(AkSqlParserStatusEnum status) {
            this.status = status;
            this.startTimeMs = System.currentTimeMillis();
            this.startTime = LocalDateTime.now();
            for (int i = 0; i < histogram.length; i++) {
                histogram[i] = new LongAdder();
            }
        }

        /**
         * 记录一次调用
         *
         * @param wallTimeNs 墙上时钟耗时（纳秒）
         * @param sqlLen     SQL 字符长度
         */
        public void record(long wallTimeNs, int sqlLen) {
            total.increment();
            execTotalCostNs.add(wallTimeNs);
            totalSqlLen.add(sqlLen);
//            maxCostMs.accumulate(wallTimeMs);
            recordHistogram(wallTimeNs);
            checkAndReset();
        }

        // ========== 计算属性 ==========

        /**
         * 获取实际运行时长（毫秒）
         */
        public Duration getElapsedDuration() {
            return Duration.between(startTime, LocalDateTime.now());
        }

        /**
         * 获取实际运行时长（毫秒）
         */
        public long getElapsedMs() {
            return System.currentTimeMillis() - startTimeMs;
        }

        /**
         * 基于实际运行时间的吞吐量 (ops/sec): total / elapsedSeconds
         */
        public double getElapsedSpeed() {
            long elapsedMs = getElapsedMs();
            return elapsedMs > 0 ? total.sum() * 1000.0 / elapsedMs : 0;
        }

        /**
         * 基于总处理耗时的吞吐量 (ops/sec): total / totalCostSeconds
         */
        public double getExecSpeed() {
            long totalNs = execTotalCostNs.sum();
            return totalNs > 0 ? total.sum() * 1_000_000_000.0 / totalNs : 0;
        }

        /**
         * 区间吞吐量 (ops/sec)：最近一个报告周期内的真实多线程吞吐
         * <p>每次调用会更新快照，因此每个报告周期只应调用一次</p>
         */

        /**
         * 平均并发度：累计执行耗时 / 墙上时钟时间
         * <p>反映平均有多少线程同时在执行解析；值越接近线程数说明利用率越高</p>
         */
        public double getAvgConcurrency() {
            long elapsedMs = getElapsedMs();
            long execMs = TimeUnit.NANOSECONDS.toMillis(execTotalCostNs.sum());
            return elapsedMs > 0 ? (double) execMs / elapsedMs : 0;
        }

        /**
         * 平均 SQL 长度（字符数）
         */
        public long getAvgSqlLen() {
            long cnt = total.sum();
            return cnt > 0 ? totalSqlLen.sum() / cnt : 0;
        }

        /**
         * 获取直方图快照
         */
        public long[] getHistogramSnapshot() {
            long[] result = new long[histogram.length];
            for (int i = 0; i < histogram.length; i++) {
                result[i] = histogram[i].sum();
            }
            return result;
        }

        /**
         * 重置所有指标
         */
        public void reset() {
            total.reset();
            execTotalCostNs.reset();
            totalSqlLen.reset();
            for (LongAdder h : histogram) {
                h.reset();
            }
            startTimeMs = System.currentTimeMillis();
            startTime = LocalDateTime.now();
        }

        // ========== 内部方法 ==========

        private void checkAndReset() {
            if (total.sum() >= MAX_TOTAL_THRESHOLD) {
                reset();
            }
        }

        private void recordHistogram(long ns) {
            // 按速率分桶：耗时越小→速率越高→idx越小
            long[] t = HistBucket.THRESHOLDS;
            int idx = 0;
            while (idx < t.length - 1 && ns >= t[idx]) {
                idx++;
            }
            histogram[idx].increment();
        }
    }


    // ==================== 公共 API ====================


    /**
     * 启动监控（如果尚未启动）
     */
    public static void ensureMonitorStarted() {
        // 快速路径：AtomicBoolean.get() 无锁读，已启动直接返回
        if (!MONITOR_ENABLED || MONITOR_STARTED.get()) {
            return;
        }
        if (MONITOR_STARTED.compareAndSet(false, true)) {
            startMonitor();
        }
    }

    /**
     * 记录方法调用开始（零分配：直接返回原始 long）
     *
     * @return 墙上时钟时间戳（纳秒）
     */
    public static long startTiming() {
        return System.nanoTime();
    }

    /**
     * 记录方法调用结束，按状态分类统计耗时
     *
     * @param startTimeNs 开始时间戳（纳秒）
     * @param status      解析状态
     * @param sqlLen      SQL 字符长度
     */
    public static void endTiming(long startTimeNs, AkSqlParserStatusEnum status, int sqlLen) {
        long exeCostTimeNs = System.nanoTime() - startTimeNs;
        if (status != null) {
            statusMetrics.get(status).record(exeCostTimeNs, sqlLen);
        }
    }

    // ==================== 查询 API ====================

    /**
     * 获取指定状态的指标
     */
    public static StatusMetrics getMetrics(AkSqlParserStatusEnum status) {
        return statusMetrics.get(status);
    }

    /**
     * 获取所有状态的总调用次数
     */
    public static long getTotalCalls() {
        long total = 0;
        for (StatusMetrics m : statusMetrics.values()) {
            total += m.total.sum();
        }
        return total;
    }


    public static String digestInfo() {
        long total = 0;
        long totalExecCostNs = 0;
        long maxElapsedMs = 0;
        long[] counts = new long[AkSqlParserStatusEnum.values().length];
        int i = 0;
        for (AkSqlParserStatusEnum s : AkSqlParserStatusEnum.values()) {
            StatusMetrics m = statusMetrics.get(s);
            long cnt = m.total.sum();
            counts[i++] = cnt;
            total += cnt;
            totalExecCostNs += m.execTotalCostNs.sum();
            long elapsed = m.getElapsedMs();
            if (elapsed > maxElapsedMs) {
                maxElapsedMs = elapsed;
            }
        }
        // 整体速度 = 总次数 / 总时间
        double allExecSpeed = totalExecCostNs > 0 ? total * 1_000_000_000.0 / totalExecCostNs : 0;
        double allElapsedSpeed = maxElapsedMs > 0 ? total * 1000.0 / maxElapsedMs : 0;

        // 各状态数量 + 比例
        StringBuilder statusCounts = new StringBuilder();
        i = 0;
        for (AkSqlParserStatusEnum s : AkSqlParserStatusEnum.values()) {
            long cnt = counts[i++];
            double pct = total > 0 ? cnt * 100.0 / total : 0;
            statusCounts.append(s.name()).append('=').append(cnt)
                    .append(String.format("(%.2f%%)", pct)).append(' ');
        }
        return "TotalCalls=" + total
                + " | " + statusCounts.toString().trim()
                + String.format(" | ExecSpeed=%.2f ops/s | ElapsedSpeed=%.2f ops/s", allExecSpeed, allElapsedSpeed);
    }


    /**
     * 重置所有监控数据
     */
    public static void resetAll() {
        for (StatusMetrics m : statusMetrics.values()) {
            m.reset();
        }
    }

    // ==================== 内部方法 ====================
    private static String monitorFilePath() {
        // 文件数： 每天_每次执行
        Path path = Paths.get(
                MONITOR_DIR,
                "/druid_monitor_" + new SimpleDateFormat("yyyyMMdd").format(new Date())
                        + "_" + MONITOR_PID + ".log"
        );
        String logFile = path.toString();
        // 确保监控日志目录存在
        File dir = new File(logFile);
        if (!dir.getParentFile().exists()) {
            dir.getParentFile().mkdirs();
        }
        return logFile;
    }

    private static void startMonitor() {


        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "akdruid-monitor");
            t.setDaemon(true);
            return t;
        });

        scheduler.scheduleAtFixedRate(() -> {
            try (PrintWriter pw = new PrintWriter(new FileWriter(monitorFilePath(), true))) {
                printMonitorStats(pw);
            } catch (Exception e) {
                // ignore
            }
        }, MONITOR_INTERVAL, MONITOR_INTERVAL, TimeUnit.SECONDS);

        // 立即输出一次
        try (PrintWriter pw = new PrintWriter(new FileWriter(monitorFilePath(), true))) {
            printMonitorStats(pw);
        } catch (Exception e) {
            // ignore
        }
    }

    // ==================== 报告格式化工具 ====================

    /**
     * 轻量报告构建器，数据收集与格式化分离，增删字段只需改一行
     */
    private static class ReportBuilder {
        private static final String LINE = "------------------------------------------------------------";
        private final StringBuilder buf = new StringBuilder(512);

        /** 标题区 */
        ReportBuilder title(String title) {
            buf.append('\n').append(LINE).append('\n');
            buf.append(title).append('\n');
            buf.append(LINE).append('\n');
            return this;
        }

        /** 多个 key=value 合并在一行，用 " | " 分隔 */
        ReportBuilder row(String... items) {
            buf.append(String.join(" | ", items)).append('\n');
            return this;
        }

        /** 带前缀标签的行，用于状态段 */
        ReportBuilder labeled(String label, String... items) {
            buf.append(String.format("  %-10s ", label));
            buf.append(String.join(" | ", items)).append('\n');
            return this;
        }

        ReportBuilder end() {
            buf.append(LINE).append('\n');
            return this;
        }

        void writeTo(PrintWriter pw) {
            pw.print(buf);
            pw.flush();
        }
    }


    private static final String[] SIZE_UNITS = {"B", "KB", "MB", "GB", "TB"};

    /**
     * 字节数格式化为人类可读单位（自动选择 B/KB/MB/GB/TB）
     */
    private static String fmtSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        int unitIdx = 0;
        double size = bytes;
        while (size >= 1024 && unitIdx < SIZE_UNITS.length - 1) {
            size /= 1024;
            unitIdx++;
        }
        return String.format("%.1f %s", size, SIZE_UNITS[unitIdx]);
    }

    private static void printMonitorStats(PrintWriter pw) {
        try {

            ReportBuilder r = new ReportBuilder();
            r.title("SqlTemplateMonitor (CACHE_ENABLED=" + CACHE_ENABLED + ")| " + LocalDateTime.now() + " | PID=" + MONITOR_PID + " | Uptime=" + Duration.between(CLASS_LOAD_TIME, LocalDateTime.now()));

            // CPU 指标
            String cpuInfo = "CPU: N/A";
            String osMemInfo = "";
            OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
            if (osBean instanceof com.sun.management.OperatingSystemMXBean) {
                com.sun.management.OperatingSystemMXBean sunOs = (com.sun.management.OperatingSystemMXBean) osBean;
                cpuInfo = getOsCpuInfo(sunOs);
                osMemInfo = getOsMemInfo(sunOs);
            }

            r.row(digestInfo());
            r.row(cpuInfo);
            if (!osMemInfo.isEmpty()) {
                r.row(osMemInfo);
            }
            /* jvmMemInfo
             *
             * used        当前实际使用的内存量（活跃对象占用）
             * committed   JVM 已向 OS 申请并保留的内存量（已分配但不一定全部在用）
             * max         该内存池允许的最大值（-1 表示无上限，如 G1 的 Eden/Survivor 动态调整）
             */
            Runtime rt = Runtime.getRuntime();
            MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
            MemoryUsage nonHeap = ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage();
            r.row("JVM Heap(Xms/Xmx): " + fmtSize(heap.getInit()) + "/" + fmtSize(heap.getMax()),
                    "Heap(used/committed): " + fmtSize(heap.getUsed()) + "/" + fmtSize(heap.getCommitted()),
                    "NonHeap(used): " + fmtSize(nonHeap.getUsed()),
                    "Free/Total: " + fmtSize(rt.freeMemory()) + "/" + fmtSize(rt.totalMemory()));
            r.row(jvmMemHeapPoolDetail());

            // jvm 线程信息
            r.row(String.format("JVM Threads: live=%d, peak=%d, daemon=%d",
                    THREAD_MX_BEAN.getThreadCount(), THREAD_MX_BEAN.getPeakThreadCount(), THREAD_MX_BEAN.getDaemonThreadCount()));

            for (AkSqlParserStatusEnum s : AkSqlParserStatusEnum.values()) {
                StatusMetrics m = statusMetrics.get(s);
                long cnt = m.total.sum();
                /* status-
                 * [Success   ] n=1898798              | elapsed=350005              ms | exec=2790670             ms | avgLen=4793     | speed(exec/elapsed)=   680.4/5425.1   ops/s
                 */
                String line = String.format("  [%-10s] n=%-20d | elapsed=%-20dms | exec=%-20dms | avgLen=%-6d | speed(exec/elapsed)=%8.1f/%8.1f ops/s | concurrency=%.1f",
                        s.name(), cnt, m.getElapsedMs(), TimeUnit.NANOSECONDS.toMillis(m.execTotalCostNs.sum()),
                        m.getAvgSqlLen(), m.getExecSpeed(), m.getElapsedSpeed(), m.getAvgConcurrency());
                r.row(line);
                if (cnt > 0) {
                    long[] hist = m.getHistogramSnapshot();
                    HistBucket[] buckets = HistBucket.values();
                    for (int i = 0; i < hist.length; i++) {
                        double pct = hist[i] * 100.0 / cnt;
                        /* status-detail
                         *               <50μs     : 1180                 (  0.1%) [>20K     ops/s]
                         *               50-67μs   : 910                  (  0.0%) [15K-20K  ops/s]
                         *               67-100μs  : 1751                 (  0.1%) [10K-15K  ops/s]
                         *               100-133μs : 2652                 (  0.1%) [7.5K-10K ops/s]
                         *               133-200μs : 669                  (  0.0%) [5K-7.5K  ops/s]
                         *               200-400μs : 107                  (  0.0%) [2.5K-5K  ops/s]
                         *               400μs-1ms : 123990               (  6.5%) [1K-2.5K  ops/s]
                         *               1-2ms     : 1653138              ( 87.1%) [500-1K   ops/s]
                         *               >2ms      : 114401               (  6.0%) [<500     ops/s]
                         */
                        r.row(String.format("              %-10s: %-20d (%5.1f%%) [%s]", buckets[i].costLabel, hist[i], pct, buckets[i].speedLabel));
                    }
                }
            }
            r.end().writeTo(pw);
        } catch (Exception e) {
            // ignore
        }
    }

    private static String getOsCpuInfo(com.sun.management.OperatingSystemMXBean sunOs) {
        String cpuInfo;
        double processCpu = sunOs.getProcessCpuLoad() * 100;
        double systemCpu = sunOs.getSystemCpuLoad() * 100;
        long processCpuTimeMs = sunOs.getProcessCpuTime() / 1_000_000;
        int cpuCount = sunOs.getAvailableProcessors();
        /*
         * cpuCount — OS CPU 核心总数
         * systemUsage   — 整个操作系统的 CPU 使用率
         * processUsage  — 当前 JVM 进程的 CPU 使用率
         * processCpuTime — 当前进程累计使用的 CPU 时间（毫秒）
         *
         * OS Memory(total/free) — 操作系统物理内存总量和空闲量
         * Swap(total/free) — 交换空间总量和空闲量
         */
        cpuInfo = String.format("OS CPU[cores=%d, system=%.1f%%, jvm_process=%.1f%%, processCpuTime=%dms]",
                cpuCount, systemCpu, processCpu, processCpuTimeMs);
        return cpuInfo;
    }

    private static String getOsMemInfo(com.sun.management.OperatingSystemMXBean sunOs) {
        String osMemInfo;
        long totalPhysMem = sunOs.getTotalPhysicalMemorySize();
        long freePhysMem = sunOs.getFreePhysicalMemorySize();
        long totalSwap = sunOs.getTotalSwapSpaceSize();
        long freeSwap = sunOs.getFreeSwapSpaceSize();
        osMemInfo = String.format("OS Memory(total=%s, free=%s) | Swap(total=%s, free=%s)",
                fmtSize(totalPhysMem), fmtSize(freePhysMem),
                fmtSize(totalSwap), fmtSize(freeSwap));
        return osMemInfo;
    }

    private static String jvmMemHeapPoolDetail() {
        /* 堆内存池详细划分 (Eden, Survivor, Old Gen 等)
         * Eden Space           * 新对象分配区，大部分对象在这里诞生并快速死亡
         * Survivor Space       * Eden GC 后存活的对象暂存区（S0/S1 交替使用）
         * Old Gen              * 长期存活对象（经过多次 Minor GC 后晋升到此）
         */
        StringBuilder heapPools = new StringBuilder("JVM Heap Pools: ");
        for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            if (pool.getType() == MemoryType.HEAP) {
                MemoryUsage pu = pool.getUsage();
                heapPools.append(String.format("%s(used=%s, committed=%s, max=%s) ",
                        pool.getName(), fmtSize(pu.getUsed()), fmtSize(pu.getCommitted()),
                        pu.getMax() == -1 ? "N/A" : fmtSize(pu.getMax())));
            }
        }
        return heapPools.toString();
    }
}
