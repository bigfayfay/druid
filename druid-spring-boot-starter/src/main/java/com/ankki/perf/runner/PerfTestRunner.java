package com.ankki.perf.runner;

import cn.hutool.core.io.FileUtil;
import com.alibaba.druid.DbType;
import com.ankki.druid.parser.AkDbTypeEnum;
import com.ankki.druid.parser.AkSqlParserStatusEnum;
import com.ankki.druid.parser.CustomerOutputVisitorUtils;
import com.ankki.perf.config.PerfTestConfig;
import com.ankki.perf.entity.SqlTemplateRecord;
import com.ankki.perf.entity.SqlTemplateRes;
import com.ankki.perf.mapper.SqlTemplateRecordMapper;
import com.ankki.perf.service.PerfStats;
import com.ankki.perf.service.SqlTemplateResService;
import com.ankki.perf.util.PerfUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class PerfTestRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(PerfTestRunner.class);

    /** 毒丸对象，通知消费者线程停止 */
    private static final List<SqlTemplateRecord> POISON_PILL = Collections.emptyList();

    @Resource
    private SqlTemplateRecordMapper sqlTemplateRecordMapper;

    @Resource
    private SqlTemplateResService sqlTemplateResService;

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

        AkDbTypeEnum akDbTypeEnum = AkDbTypeEnum.of(DbType.of(config.getDbType()));
        PerfStats stats = new PerfStats();

        String path = "/data/logs/druid";
        FileUtil.mkdir(path);

        // 用 BlockingQueue 实现生产者-消费者模式
        BlockingQueue<List<SqlTemplateRecord>> queue = new LinkedBlockingQueue<>(config.getQueueCapacity());
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
                    List<SqlTemplateRecord> records = fetchBatch(lastId, config.getBatchSize());
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
        // 每个消费者线程写自己的失败文件，避免写锁竞争
        final boolean writeFile = config.isWriteFailedFile();
        final boolean writeDb = config.isWriteResultDb();
        for (int i = 0; i < threadCount; i++) {
            final int threadIdx = i;
            futures.add(consumers.submit(() -> {
                Path failedFile = Paths.get(path,
                        "failed_sql_" + PerfUtil.pureHourMin() + "_" + PerfUtil.MONITOR_PID + "_t" + threadIdx + ".info");
                try (BufferedWriter writer = writeFile
                        ? new BufferedWriter(new FileWriter(failedFile.toFile()))
                        : null) {
                    while (true) {
                        List<SqlTemplateRecord> batch = queue.take();
                        if (batch == POISON_PILL) {
                            break;
                        }
                        for (SqlTemplateRecord record : batch) {
                            long parseStart = System.nanoTime();
                            String[] sqlTemplateV2 = CustomerOutputVisitorUtils.getSqlTemplate_v2(
                                    record.getTemplate(), akDbTypeEnum.getTypeId());
                            long costMs = (System.nanoTime() - parseStart) / 1_000_000;

                            AkSqlParserStatusEnum statusEnum = AkSqlParserStatusEnum.fastValueOf(sqlTemplateV2[0]);

                            // 构建结果记录
                            SqlTemplateRes res = new SqlTemplateRes();
                            res.setId(record.getId());
                            res.setStatus(sqlTemplateV2[0]);
                            res.setCostMs(costMs);
                            res.setSqlLen(record.getTemplate() != null ? record.getTemplate().length() : 0);
                            if (AkSqlParserStatusEnum.Success != statusEnum) {
                                res.setFailReason(sqlTemplateV2.length > 4 ? sqlTemplateV2[4] : null);
                                // 失败记录写文件（可选）
                                if (writer != null) {
                                    writer.write(record.getId() + "|||" + sqlTemplateV2[4] + "|||" + record.getTemplate());
                                    writer.newLine();
                                }
                            }

                            // 累积到 service 缓冲区，达到阈值自动批量写入 DB（可选）
                            if (writeDb) {
                                sqlTemplateResService.addRecord(res);
                            }
                        }
                        totalProcessed.addAndGet(batch.size());
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (IOException e) {
                    log.error("Consumer-{} failed to write file: {}", threadIdx, failedFile, e);
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

        // 刷新缓冲区中剩余的记录到 DB
        sqlTemplateResService.flush();

        stats.markEnd();
        log.info("Total processed records: {}", totalProcessed.get());
        log.info(stats.generateReport());
        log.error("exit ...");
        System.exit(0);
    }

    private List<SqlTemplateRecord> fetchBatch(long lastId, int batchSize) {
        LambdaQueryWrapper<SqlTemplateRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.gt(SqlTemplateRecord::getId, lastId)
                .orderByAsc(SqlTemplateRecord::getId)
                .last("LIMIT " + batchSize);
        return sqlTemplateRecordMapper.selectList(wrapper);
    }
}
