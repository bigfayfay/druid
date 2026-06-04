package com.ankki.perf.runner;

import cn.hutool.core.io.FileUtil;
import com.ankki.druid.parser.AkSqlParserStatusEnum;
import com.ankki.druid.parser.CustomerOutputVisitorUtils;
import com.ankki.perf.config.PerfTestConfig;
import com.ankki.perf.entity.SqlTemplateQueryRequest;
import com.ankki.perf.entity.SqlTypeBO;
import com.ankki.perf.entity.db.SqlTemplateRes;
import com.ankki.perf.service.DataFetcherService;
import com.ankki.perf.service.PerfStats;
import com.ankki.perf.service.PostHandler;
import com.ankki.perf.service.handler.FilePostHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

@Component
public class PerfTestRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(PerfTestRunner.class);

    /** 毒丸对象，通知消费者线程停止 */
    private static final List<SqlTypeBO> POISON_PILL = Collections.emptyList();

    @Resource
    private DataFetcherService dataFetcherService;

    @Resource
    private List<PostHandler> postHandlers;

    @Resource
    private PerfTestConfig config;

    @Override
    public void run(String... args) {
        if (!config.isEnabled()) {
            log.info("Performance test is disabled. Set -Dperf.enabled=true to enable.");
            return;
        }

        int threadCount = config.getThreadCount();
        log.info("=== Druid SQL Parser Multi-Thread Performance Test ===");
        log.info("Config: {}", config.toString());

        PerfStats stats = new PerfStats();

        String path = "/data/logs/druid";
        FileUtil.mkdir(path);

        // 用 BlockingQueue 实现生产者-消费者模式
        BlockingQueue<List<SqlTypeBO>> queue = new LinkedBlockingQueue<>(config.getQueueCapacity());
        AtomicLong totalProcessed = new AtomicLong(0);

        stats.markStart();

        // --- 生产者线程：从 DB 读取数据放入队列 ---
        Thread producer = new Thread(() -> {
            long lastId = config.getStartId();
            int batchNumber = 0;
            long produced = 0;
            try {
                while (true) {
                    long dbStart = System.nanoTime();
                    List<SqlTypeBO> records = fetchBatch(lastId, config.getBatchSize());
                    long dbElapsed = System.nanoTime() - dbStart;
                    stats.recordDbQuery(dbElapsed);

                    if (records.isEmpty()) {
                        log.info("Producer: no more records. Total fetched: {}", produced);
                        break;
                    }

                    batchNumber++;
                    queue.put(records);

                    lastId = records.get(records.size() - 1).getId();
                    produced += records.size();

                    if (batchNumber % 100 == 0) {
                        log.info("Producer progress: batch={}, fetched={}, lastId={}, queueSize={}",
                                batchNumber, produced, lastId, queue.size());
                    }

                    if (records.size() < config.getBatchSize()
                            || (config.getMaxRecords() > 0 && produced >= config.getMaxRecords())) {
                        log.info("Producer: reached limit. fetched={}", produced);
                        break;
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Producer interrupted");
            }

            // 放入毒丸通知所有消费者停止
            for (int i = 0; i < threadCount; i++) {
                try {
                    queue.put(POISON_PILL);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }, "perf-producer");

        // --- 消费者线程池：并行解析 SQL ---
        ExecutorService consumers = Executors.newFixedThreadPool(threadCount, r -> {
            Thread t = new Thread(r);
            t.setName("perf-consumer-" + t.getId());
            t.setDaemon(true);
            return t;
        });

        List<Future<?>> futures = new ArrayList<>();
        // 每个消费者线程独立的后处理器列表
        final boolean writeFile = config.isWriteFailedFile();
        final boolean writeDb = config.isWriteResultDb();
        
        // 用于存储每个线程的统计信息
        List<ThreadStats> threadStatsList = Collections.synchronizedList(new ArrayList<>());
        
        for (int i = 0; i < threadCount; i++) {
            final int threadIdx = i;
            
            // 为每个线程创建独立的统计对象
            ThreadStats threadStats = new ThreadStats(threadIdx);
            threadStatsList.add(threadStats);
            
            // 为每个线程创建独立的后处理器
            List<PostHandler> threadPostHandlers = new ArrayList<>();
            
            // 文件后处理器（每个线程一个文件）
            FilePostHandler filePostHandler = null;
            if (writeFile) {
                try {
                    filePostHandler = new FilePostHandler(path, threadIdx, true);
                    threadPostHandlers.add(filePostHandler);
                } catch (IOException e) {
                    log.error("Failed to create FilePostHandler for thread-{}", threadIdx, e);
                }
            }
            
            // DB后处理器（所有线程共享）
            if (writeDb) {
                threadPostHandlers.addAll(postHandlers);
            }
            
            final List<PostHandler> finalPostHandlers = threadPostHandlers;
            final FilePostHandler finalFilePostHandler = filePostHandler;
            
            futures.add(consumers.submit(() -> {
                try {
                    while (true) {
                        List<SqlTypeBO> batch = queue.take();
                        if (batch == POISON_PILL) {
                            break;
                        }
                        for (SqlTypeBO record : batch) {
                            long parseStart = System.nanoTime();
                            String[] sqlTemplateV2 = CustomerOutputVisitorUtils.getSqlTemplate_v2(
                                    record.getOperSentence(), record.getDbType());
                            long costMs = (System.nanoTime() - parseStart) / 1_000_000;
                            long costNanos = System.nanoTime() - parseStart;

                            AkSqlParserStatusEnum statusEnum = AkSqlParserStatusEnum.fastValueOf(sqlTemplateV2[0]);

                            // 构建结果记录
                            SqlTemplateRes res = new SqlTemplateRes();
                            res.setId(record.getId());
                            res.setStatus(sqlTemplateV2[0]);
                            res.setCostMs(costMs);
                            res.setSqlLen(record.getOperSentence() != null ? record.getOperSentence().length() : 0);
                            if (AkSqlParserStatusEnum.Success != statusEnum) {
                                res.setFailReason(sqlTemplateV2.length > 4 ? sqlTemplateV2[4] : null);
                            }

                            // 记录线程级别的统计
                            threadStats.recordParse(costNanos, statusEnum == AkSqlParserStatusEnum.Success);

                            // 通过后处理器统一处理
                            for (PostHandler handler : finalPostHandlers) {
                                // 如果是FilePostHandler，传入原始SQL
                                if (handler instanceof FilePostHandler) {
                                    ((FilePostHandler) handler).addRecord(res, record.getOperSentence());
                                } else {
                                    handler.addRecord(res);
                                }
                            }
                        }
                        totalProcessed.addAndGet(batch.size());
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    // 线程结束时刷新并关闭文件
                    if (finalFilePostHandler != null) {
                        finalFilePostHandler.flush();
                        finalFilePostHandler.close();
                    }
                    // 打印线程级别的统计
                    threadStats.printStats();
                }
            }));
        }

        // 启动生产者
        producer.start();

        // 等待所有消费者完成
        for (Future<?> f : futures) {
            try {
                f.get();
            } catch (InterruptedException | ExecutionException e) {
                log.error("Consumer error", e);
            }
        }
        consumers.shutdown();

        // 刷新DB后处理器中剩余的记录
        postHandlers.forEach(PostHandler::flush);

        stats.markEnd();
        log.info("Total processed records: {}", totalProcessed.get());
        log.info(stats.generateReport());
        
        // 打印全局汇总统计
        printGlobalStats(threadStatsList, stats);
        
        log.error("exit ...");
        System.exit(0);
    }

    private List<SqlTypeBO> fetchBatch(long lastId, int batchSize) {
        SqlTemplateQueryRequest request = new SqlTemplateQueryRequest(lastId, batchSize);
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
        
        log.info("\n========== 全局性能统计汇总 ==========");
        log.info("{:<15} | {:>10} | {:>12} | {:>12} | {:>10} | {:>12}", 
                "线程", "调用次数", "总耗时(ms)", "成功数", "失败数", "速度(rec/s)");
        log.info("{}", createSeparatorLine());
        
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
            
            log.info("{:<15} | {:>10} | {:>12} | {:>12} | {:>10} | {:>12.2f}",
                    "Thread-" + ts.getThreadId(), 
                    calls, 
                    parseNanos / 1_000_000.0,
                    success, 
                    failure,
                    speed);
        }
        
        log.info("{}", createSeparatorLine());
        
        // 全局汇总
        long globalElapsedMs = globalStats.getEndTimeMillis() - globalStats.getStartTimeMillis();
        double globalSpeed = globalElapsedMs > 0 ? (totalCalls * 1000.0 / globalElapsedMs) : 0;
        double avgParseMs = totalCalls > 0 ? (totalParseNanos / 1_000_000.0 / totalCalls) : 0;
        
        log.info("{:<15} | {:>10} | {:>12} | {:>12} | {:>10} | {:>12.2f}",
                "全局汇总", 
                totalCalls, 
                totalParseNanos / 1_000_000.0,
                totalSuccess, 
                totalFailure,
                globalSpeed);
        log.info("全局平均解析耗时: {:.3f} ms", avgParseMs);
        log.info("全局Wall Time: {} ms", globalElapsedMs);
        log.info("全局吞吐量: {:.2f} rec/s", globalSpeed);
        log.info("========================================\n");
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
        private final int threadId;
        private final LongAdder totalCalls = new LongAdder();
        private final LongAdder successCount = new LongAdder();
        private final LongAdder failureCount = new LongAdder();
        private final LongAdder totalParseNanos = new LongAdder();
        private final long startTime;
        private volatile long endTime;

        public ThreadStats(int threadId) {
            this.threadId = threadId;
            this.startTime = System.currentTimeMillis();
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
            
            log.info("\n[Thread-{}] 性能统计:", threadId);
            log.info("  调用次数: {}", calls);
            log.info("  成功/失败: {} / {}", getSuccessCount(), getFailureCount());
            log.info("  总耗时: {} ms", elapsedMs);
            log.info("  平均耗时: {:.3f} ms", avgMs);
            log.info("  处理速度: {:.2f} rec/s", speed);
        }
    }
}
