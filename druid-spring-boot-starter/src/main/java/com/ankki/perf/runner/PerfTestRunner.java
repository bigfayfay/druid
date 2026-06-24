package com.ankki.perf.runner;

import cn.hutool.core.io.FileUtil;
import com.ankki.druid.parser.AkSqlParserStatusEnum;
import com.ankki.druid.parser.CustomerOutputVisitorUtils;
import com.ankki.druid.parser.config.VmOptions;
import com.ankki.druid.parser.template.AkLightweightCachedOutputVisitorUtils;
import com.ankki.druid.parser.template.AkOutputVisitorUtils;
import com.ankki.druid.parser.template.AkSqlTemplateMonitor;
import com.ankki.perf.config.PerfTestConfig;
import com.ankki.perf.entity.SqlTemplateQueryRequest;
import com.ankki.perf.entity.SqlTypeBO;
import com.ankki.perf.entity.db.SqlTemplateRes;
import com.ankki.perf.service.DataFetcherService;
import com.ankki.perf.service.PerfStats;
import com.ankki.perf.service.PostHandler;
import com.ankki.perf.service.handler.FilePostHandler;
import com.ankki.perf.util.AuditUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import org.springframework.beans.factory.annotation.Autowired;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.LongAdder;

@Component
public class PerfTestRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(PerfTestRunner.class);

    /** 毒丸对象，通知消费者线程停止 */
    private static final List<SqlTypeBO> POISON_PILL = Collections.emptyList();

    @Resource
    private DataFetcherService dataFetcherService;

    @Autowired(required = false)
    private List<PostHandler> postHandlers = new ArrayList<>();

    @Resource
    private PerfTestConfig config;

    String path = "/data/logs/druid";

    @Override
    public void run(String... args) {
        if (!config.isEnabled()) {
            log.info("[APP] Performance test is disabled. Set -Dperf.enabled=true to enable.");
            return;
        }

        int threadCount = config.getThreadCount();
        log.info("[APP] === Druid SQL Parser Multi-Thread Performance Test ===");
        log.info("[APP] Config: {}", config.toString());

        PerfStats stats = new PerfStats();
        FileUtil.mkdir(path);

        // 用 BlockingQueue 实现生产者-消费者模式
        BlockingQueue<List<SqlTypeBO>> queue = new LinkedBlockingQueue<>(config.getQueueCapacity());
        LongAdder totalProcessed = new LongAdder();

        stats.markStart();

        // --- 生产者线程：从 DB 读取数据放入队列 ---
        // 连续空批次阈值：连续多次返回空结果才认为数据已耗尽（处理时间窗口间隙问题）
        Thread producer = producerThread(stats, queue);

        // --- 消费者线程池：并行解析 SQL ---
        ExecutorService consumers = Executors.newFixedThreadPool(threadCount, r -> {
            Thread t = new Thread(r);
            t.setName("perf-consumer-" + t.getId());
            t.setDaemon(true);
            return t;
        });

        List<Future<?>> futures = new ArrayList<>();
        
        // 用于存储每个线程的统计信息
        List<ThreadStats> threadStatsList = Collections.synchronizedList(new ArrayList<>());
        
        for (int i = 0; i < threadCount; i++) {
            final int threadIdx = i;
            // 为每个线程创建独立的统计对象
            ThreadStats threadStats = new ThreadStats(threadIdx);
            threadStatsList.add(threadStats);

            futures.add(consumers.submit(() -> {
                consumerThread(queue, threadStats, stats);
            }));
        }

        // 启动生产者
        producer.start();

        // 等待所有消费者完成
        for (Future<?> f : futures) {
            try {
                f.get();
            } catch (InterruptedException | ExecutionException e) {
                log.error("[APP] Consumer error", e);
            }
        }
        consumers.shutdown();

        // 刷新DB后处理器中剩余的记录
        postHandlers.forEach(PostHandler::flush);

        stats.markEnd();
        log.info("[APP] Total processed records: {}", totalProcessed.sum());
        log.info(stats.generateReport());

        // 打印全局汇总统计
        printGlobalStats(threadStatsList, stats);

        log.error("[APP] exit ...");
        System.exit(0);
    }

    private void consumerThread(BlockingQueue<List<SqlTypeBO>> queue, ThreadStats threadStats, PerfStats stats) {
        // 为每个线程创建独立的后处理器
        List<PostHandler> threadPostHandlers = new ArrayList<>();

        try {
            // 每个消费者线程独立的后处理器列表
            final boolean writeFile = config.isWriteFailedFile();
            final boolean writeDb = config.isWriteResultDb();

            int threadIdx = threadStats.getThreadId();

            // 文件后处理器（每个线程一个文件）
            FilePostHandler filePostHandler = null;
            if (writeFile) {
                try {
                    filePostHandler = new FilePostHandler(path, threadIdx, true);
                    threadPostHandlers.add(filePostHandler);
                } catch (IOException e) {
                    log.error("[APP] Failed to create FilePostHandler for thread-{}", threadIdx, e);
                }
            }

            // DB后处理器（所有线程共享）
            if (writeDb) {
                threadPostHandlers.addAll(postHandlers);
            }

            while (true) {
                List<SqlTypeBO> batch = queue.take();
                if (batch == POISON_PILL) {
                    break;
                }
                for (SqlTypeBO record : batch) {

                    String sql = AuditUtils.restoreAllInvisibleChars(record.getOperSentence());
                    long parseStart = System.nanoTime();
                    // 应用 dbType 转换配置
                    Integer convertedDbType = config.convertDbType(record.getDbType());
                    String[] sqlRes = CustomerOutputVisitorUtils.getSqlTemplate_v2(sql, convertedDbType);
//                            String[] sqlRes = getSqlTemplate_v3(record.getOperSentence(), record.getDbType());
                    long costNanos = System.nanoTime() - parseStart;

                    AkSqlParserStatusEnum statusEnum = AkSqlParserStatusEnum.fastValueOf(sqlRes[0]);

                    // 构建结果记录
                    SqlTemplateRes res = record.toSqlReds();
                    res.setCostNs(costNanos);
                    res.setStatus(sqlRes[0]);
                    // 失败的,没有sql模板
                    res.setSqlMd5(sqlRes[1]);
                    res.setTemplate(sqlRes[2]);
                    res.setOperSentence(sql);
                    res.setTenantId(record.getTenantId());
                    if (AkSqlParserStatusEnum.Success != statusEnum) {
                        res.setFailReason(sqlRes.length > 4 ? sqlRes[4] : null);
                    }
                    if (!Objects.equals(record.getDbType(), convertedDbType)) {
                        res.setRemark(res.getDbType() + " -> " + convertedDbType);
                    }

                    // 记录线程级别的统计
                    threadStats.recordParse(costNanos, statusEnum == AkSqlParserStatusEnum.Success);

                    // 通过后处理器统一处理
                    threadPostHandlers.forEach(handler -> handler.addRecord(res));
                }

                stats.recordTotal(batch.size());
                if (threadStats.printIfMilestone()) {
                    threadPostHandlers.forEach(PostHandler::flush);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            // 线程结束时刷新并关闭文件
            threadPostHandlers.forEach(PostHandler::flush);
            threadPostHandlers.forEach(PostHandler::close);
            // 打印线程级别的统计
            threadStats.printStats();
        }
    }

    private Thread producerThread(PerfStats stats, BlockingQueue<List<SqlTypeBO>> queue) {
        final int maxConsecutiveEmpty = config.getMaxConsecutiveEmpty();
        Thread producer = new Thread(() -> {
            long lastId = config.getStartId();
            SqlTemplateQueryRequest fetchRequest = new SqlTemplateQueryRequest(lastId, config.getBatchSize());
            int batchNumber = 0;
            long produced = 0;
            int consecutiveEmpty = 0;
            try {
                while (true) {
                    long dbStart = System.nanoTime();
                    List<SqlTypeBO> records = dataFetcherService.fetchBatch(fetchRequest);
                    long dbElapsed = System.nanoTime() - dbStart;
                    stats.recordDbQuery(dbElapsed);

                    if (records.isEmpty()) {
                        consecutiveEmpty++;
                        if (consecutiveEmpty >= maxConsecutiveEmpty) {
                            log.info("[APP] [Producer_e]: [no_more] records after {} consecutive empty batches. Total fetched: {}",
                                    consecutiveEmpty, produced);
                            break;
                        }
                        log.debug("[APP] Producer: empty batch #{}, retrying...", consecutiveEmpty);
                        continue;
                    }

                    // 获取到数据，重置空批次计数
                    consecutiveEmpty = 0;
                    batchNumber++;
                    queue.put(records);

                    lastId = records.get(records.size() - 1).getId();
                    fetchRequest.setStartId(lastId);
                    produced += records.size();

                    if (batchNumber % 1000_000 == 0) {
                        log.info("[APP] Producer: batch={}, fetched={}, lastId={}, queueSize={}",
                                batchNumber, produced, lastId, queue.size());
                    }

                    // 仅通过 maxRecords 配置限制来终止，不再依赖 records.size() 判断
                    if (config.getMaxRecords() > 0 && produced >= config.getMaxRecords()) {
                        log.info("[APP] [Producer_e]: to_exit, reached maxRecords limit. fetched={}", produced);
                        break;
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Producer interrupted");
            }

            // 放入毒丸通知所有消费者停止
            for (int i = 0; i < config.getThreadCount(); i++) {
                try {
                    queue.put(POISON_PILL);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }, "perf-producer");
        return producer;
    }


    static final boolean CACHE_USE = VmOptions.getBoolean(VmOptions.CACHE_USE);
    public static String[] getSqlTemplate_v3(String sql, Integer akDbTypeId) {
        // 快速路径：监控禁用时零开销直接执行
        if (!AkSqlTemplateMonitor.MONITOR_ENABLED) {
            if (CACHE_USE) {
                return AkLightweightCachedOutputVisitorUtils.getSqlTemplate_v2(sql, akDbTypeId);
            } else {
                return AkOutputVisitorUtils.getSqlTemplate_v2(sql, akDbTypeId);
            }
        }

        // 监控启用路径
        AkSqlTemplateMonitor.ensureMonitorStarted();
        long startTimeNs = AkSqlTemplateMonitor.startTiming();
        AkSqlParserStatusEnum status = null;
        try {
            String[] result;
            if (CACHE_USE) {
                result = AkLightweightCachedOutputVisitorUtils.getSqlTemplate_v2(sql, akDbTypeId);
            } else {
                result = AkOutputVisitorUtils.getSqlTemplate_v2(sql, akDbTypeId);
            }
            status = AkSqlParserStatusEnum.fastValueOf(result[0]);
            return result;
        } finally {
            AkSqlTemplateMonitor.endTiming(startTimeNs, status, sql.length());
        }
    }


    private List<SqlTypeBO> fetchBatch(SqlTemplateQueryRequest request) {
        return dataFetcherService.fetchBatch(request);
    }

    /**
     * 打印全局汇总统计信息
     */
    private void printGlobalStats(List<ThreadStats> threadStatsList, PerfStats globalStats) {
        long totalCalls = 0;
        long totalParseNanos = 0;
        long totalSuccess = 0;
        long totalFailure = 0;
        
        log.info("[APP] \n========== 全局性能统计汇总 ==========");
        // [stat].title
        log.info("[APP] {}", String.format("%-15s | %10s | %12s | %12s | %10s | %12s", 
                "线程", "调用次数", "总耗时(ms)", "成功数", "失败数", "速度(rec/s)"));
        log.info("[APP] {}", createSeparatorLine());
        
        for (ThreadStats ts : threadStatsList) {
            long calls = ts.getTotalCalls();
            long parseNanos = ts.getTotalParseNanos();
            long success = ts.getSuccessCount();
            long failure = ts.getFailureCount();
            long elapsedMs = ts.getElapsedMillis();
            double speed = elapsedMs > 0 ? (calls * 1000.0 / elapsedMs) : 0;
            
            totalCalls += calls;
            totalParseNanos += parseNanos;
            totalSuccess += success;
            totalFailure += failure;
            /*
             * SLF4J 只认 {}，格式化指令被忽略
             * 先用 String.format 格式化，再传给 SLF4J
             */
            log.info("[APP] {}", String.format("%-15s | %10d | %12.2f | %12d | %10d | %12.2f",
                    "Thread-" + ts.getThreadId(), 
                    calls, 
                    parseNanos / 1_000_000.0,
                    success, 
                    failure,
                    speed));
        }
        
        log.info("[APP] {}", createSeparatorLine());
        
        // [stat].gloal 全局汇总
        long globalElapsedMs = globalStats.getEndTimeMillis() - globalStats.getStartTimeMillis();
        double globalSpeed = globalElapsedMs > 0 ? (totalCalls * 1000.0 / globalElapsedMs) : 0;
        double avgParseMs = totalCalls > 0 ? (totalParseNanos / 1_000_000.0 / totalCalls) : 0;
        
        log.info("[APP] {}", String.format("%-15s | %10d | %12.2f | %12d | %10d | %12.2f",
                "全局汇总", 
                totalCalls, 
                totalParseNanos / 1_000_000.0,
                totalSuccess, 
                totalFailure,
                globalSpeed));
        log.info("[APP] 全局平均解析耗时: {} ms", String.format("%.3f", avgParseMs));
        log.info("[APP] 全局Wall Time: {} ms", globalElapsedMs);
        log.info("[APP] 全局吞吐量: {} rec/s", String.format("%.2f", globalSpeed));
        log.info("[APP] ========================================\n");
    }

    /**
     * 创建分隔线（Java 8兼容）
     */
    private String createSeparatorLine() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 85; i++) {
            sb.append('-');
        }
        return sb.toString();
    }

    /**
     * 单个线程的性能统计
     */
    private static class ThreadStats {
        private static final long PRINT_INTERVAL = 250000; // 每处理10万条打印一次

        private final int threadId;
        private final LongAdder totalCalls = new LongAdder();
        private final LongAdder successCount = new LongAdder();
        private final LongAdder failureCount = new LongAdder();
        private final LongAdder totalParseNanos = new LongAdder();
        private final long startTime;
        private volatile long endTime;
        private long lastPrintMilestone = 0; // 里程碑：上次打印时的万级编号

        public ThreadStats(int threadId) {
            this.threadId = threadId;
            this.startTime = System.currentTimeMillis();
        }

        /**
         * 里程碑跨越法：当处理量跨过下一个万级时自动打印统计
         */
        public boolean printIfMilestone() {
            long currentMilestone = getTotalCalls() / PRINT_INTERVAL;
            if (currentMilestone > lastPrintMilestone) {
                lastPrintMilestone = currentMilestone;
                printStats();
                return true;
            }
            return false;
        }

        public void recordParse(long elapsedNanos, boolean success) {
            totalCalls.increment();
            totalParseNanos.add(elapsedNanos);
            if (success) {
                successCount.increment();
            } else {
                failureCount.increment();
            }
        }

        public void finish() {
            this.endTime = System.currentTimeMillis();
        }

        public int getThreadId() {
            return threadId;
        }

        public long getTotalCalls() {
            return totalCalls.sum();
        }

        public long getTotalParseNanos() {
            return totalParseNanos.sum();
        }

        public long getSuccessCount() {
            return successCount.sum();
        }

        public long getFailureCount() {
            return failureCount.sum();
        }

        public long getElapsedMillis() {
            return endTime > 0 ? endTime - startTime : System.currentTimeMillis() - startTime;
        }

        public void printStats() {
            long calls = getTotalCalls();
            long elapsedMs = getElapsedMillis();
            double speed = elapsedMs > 0 ? (calls * 1000.0 / elapsedMs) : 0;
            double avgMs = calls > 0 ? (getTotalParseNanos() / 1_000_000.0 / calls) : 0;
            
            log.info("[APP] \n[Thread-{}] 性能统计:", threadId);
            log.info("[APP]   调用次数: {}", calls);
            log.info("[APP]   成功/失败: {} / {}", getSuccessCount(), getFailureCount());
            log.info("[APP]   总耗时: {} ms", elapsedMs);
            log.info("[APP]   平均耗时: {} ms", String.format("%.3f", avgMs));
            log.info("[APP]   处理速度: {} rec/s", String.format("%.2f", speed));
        }
    }
}
