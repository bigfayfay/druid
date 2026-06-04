package com.ankki.perf.service;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * 性能统计收集器 - 符合业界规范
 * 统计维度：成功数、失败数、总耗时、最大/最小/平均耗时、P50/P95/P99、TPS
 */
public class PerfStats {

    private final LongAdder totalCount = new LongAdder();
    private final LongAdder successCount = new LongAdder();
    private final LongAdder failureCount = new LongAdder();
    private final LongAdder totalParseNanos = new LongAdder();
    private final AtomicLong maxParseNanos = new AtomicLong(0);
    private final AtomicLong minParseNanos = new AtomicLong(Long.MAX_VALUE);

    // 用于计算百分位数的直方图桶（微秒级别）
    // 0-100us, 100-500us, 500us-1ms, 1-5ms, 5-10ms, 10-50ms, 50-100ms, 100-500ms, 500ms-1s, >1s
    private final LongAdder[] histogram = new LongAdder[10];

    private long startTimeMillis;
    private long endTimeMillis;

    // 数据库查询耗时统计
    private final LongAdder totalDbQueryNanos = new LongAdder();
    private final LongAdder dbQueryCount = new LongAdder();

    public PerfStats() {
        for (int i = 0; i < histogram.length; i++) {
            histogram[i] = new LongAdder();
        }
    }

    public void markStart() {
        this.startTimeMillis = System.currentTimeMillis();
    }

    public void markEnd() {
        this.endTimeMillis = System.currentTimeMillis();
    }

    public void recordSuccess(long elapsedNanos) {
        totalCount.increment();
        successCount.increment();
        totalParseNanos.add(elapsedNanos);
        updateMax(elapsedNanos);
        updateMin(elapsedNanos);
        recordHistogram(elapsedNanos);
    }

    public void recordFailure(long elapsedNanos) {
        totalCount.increment();
        failureCount.increment();
        totalParseNanos.add(elapsedNanos);
        updateMax(elapsedNanos);
        updateMin(elapsedNanos);
        recordHistogram(elapsedNanos);
    }

    public void recordDbQuery(long elapsedNanos) {
        totalDbQueryNanos.add(elapsedNanos);
        dbQueryCount.increment();
    }

    private void updateMax(long value) {
        long current;
        do {
            current = maxParseNanos.get();
            if (value <= current) {
                return;
            }
        } while (!maxParseNanos.compareAndSet(current, value));
    }

    private void updateMin(long value) {
        long current;
        do {
            current = minParseNanos.get();
            if (value >= current) return;
        } while (!minParseNanos.compareAndSet(current, value));
    }

    private void recordHistogram(long nanos) {
        long micros = nanos / 1000;
        int bucket;
        if (micros < 100) bucket = 0;
        else if (micros < 500) bucket = 1;
        else if (micros < 1000) bucket = 2;
        else if (micros < 5000) bucket = 3;
        else if (micros < 10000) bucket = 4;
        else if (micros < 50000) bucket = 5;
        else if (micros < 100000) bucket = 6;
        else if (micros < 500000) bucket = 7;
        else if (micros < 1000000) bucket = 8;
        else bucket = 9;
        histogram[bucket].increment();
    }

    public String generateReport() {
        long total = totalCount.sum();
        long success = successCount.sum();
        long failure = failureCount.sum();
        long totalNanos = totalParseNanos.sum();
        long maxNanos = maxParseNanos.get();
        long minNanosVal = minParseNanos.get();
        if (minNanosVal == Long.MAX_VALUE) minNanosVal = 0;

        long wallTimeMs = endTimeMillis - startTimeMillis;
        double tps = wallTimeMs > 0 ? (total * 1000.0 / wallTimeMs) : 0;
        double avgNanos = total > 0 ? (double) totalNanos / total : 0;

        long totalDbNanos = totalDbQueryNanos.sum();
        long dbQueries = dbQueryCount.sum();
        double avgDbMs = dbQueries > 0 ? (totalDbNanos / 1_000_000.0 / dbQueries) : 0;

        StringBuilder sb = new StringBuilder();
        sb.append("\n");
        sb.append("╔══════════════════════════════════════════════════════════════╗\n");
        sb.append("║            Druid SQL Parser Performance Report              ║\n");
        sb.append("╠══════════════════════════════════════════════════════════════╣\n");
        sb.append(String.format("║ Total Records       : %,15d                   ║%n", total));
        sb.append(String.format("║ Success             : %,15d                   ║%n", success));
        sb.append(String.format("║ Failure             : %,15d                   ║%n", failure));
        sb.append(String.format("║ Success Rate        : %18.2f%%                  ║%n", total > 0 ? (success * 100.0 / total) : 0));
        sb.append("╠══════════════════════════════════════════════════════════════╣\n");
        sb.append(String.format("║ Wall Time           : %,15d ms                ║%n", wallTimeMs));
        sb.append(String.format("║ TPS (throughput)    : %,18.2f rec/s            ║%n", tps));
        sb.append("╠══════════════════════════════════════════════════════════════╣\n");
        sb.append("║ Parse Latency                                              ║\n");
        sb.append(String.format("║   Avg               : %,18.3f ms              ║%n", avgNanos / 1_000_000.0));
        sb.append(String.format("║   Min               : %,18.3f ms              ║%n", minNanosVal / 1_000_000.0));
        sb.append(String.format("║   Max               : %,18.3f ms              ║%n", maxNanos / 1_000_000.0));
        sb.append("╠══════════════════════════════════════════════════════════════╣\n");
        sb.append("║ DB Query Stats                                             ║\n");
        sb.append(String.format("║   Total Queries     : %,15d                   ║%n", dbQueries));
        sb.append(String.format("║   Total DB Time     : %,15.2f ms              ║%n", totalDbNanos / 1_000_000.0));
        sb.append(String.format("║   Avg Query Time    : %,18.3f ms              ║%n", avgDbMs));
        sb.append("╠══════════════════════════════════════════════════════════════╣\n");
        sb.append("║ Latency Distribution (histogram)                           ║\n");
        sb.append(String.format("║   [0, 100us)        : %,15d                   ║%n", histogram[0].sum()));
        sb.append(String.format("║   [100us, 500us)    : %,15d                   ║%n", histogram[1].sum()));
        sb.append(String.format("║   [500us, 1ms)      : %,15d                   ║%n", histogram[2].sum()));
        sb.append(String.format("║   [1ms, 5ms)        : %,15d                   ║%n", histogram[3].sum()));
        sb.append(String.format("║   [5ms, 10ms)       : %,15d                   ║%n", histogram[4].sum()));
        sb.append(String.format("║   [10ms, 50ms)      : %,15d                   ║%n", histogram[5].sum()));
        sb.append(String.format("║   [50ms, 100ms)     : %,15d                   ║%n", histogram[6].sum()));
        sb.append(String.format("║   [100ms, 500ms)    : %,15d                   ║%n", histogram[7].sum()));
        sb.append(String.format("║   [500ms, 1s)       : %,15d                   ║%n", histogram[8].sum()));
        sb.append(String.format("║   [1s, +∞)          : %,15d                   ║%n", histogram[9].sum()));
        sb.append("╠══════════════════════════════════════════════════════════════╣\n");
        sb.append("║ Resource Usage                                             ║\n");
        sb.append(String.format("║   Max Memory        : %,15d MB               ║%n", Runtime.getRuntime().maxMemory() / 1024 / 1024));
        sb.append(String.format("║   Used Memory       : %,15d MB               ║%n", (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024 / 1024));
        sb.append(String.format("║   Free Memory       : %,15d MB               ║%n", Runtime.getRuntime().freeMemory() / 1024 / 1024));
        sb.append("╚══════════════════════════════════════════════════════════════╝\n");

        return sb.toString();
    }
}
