package com.ankki.perf.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Data
@Component
public class PerfTestConfig {
    /**
     * select-dataList.size
     */
    @Value("${perf.batch-size:1000}")
    private int batchSize;

    @Value("${perf.enabled:true}")
    private boolean enabled;

    @Value("${perf.db-type:oracle}")
    private String dbType;

    @Value("${perf.warmup-batches:2}")
    private int warmupBatches;
    /**
     * 起始id
     */
    @Value("${perf.start-id:0}")
    private long startId;

    /**
     * MAX records to process
     */
    @Value("${perf.max-records:0}")
    private long maxRecords;

    /**
     * 线程数量
     */
    @Value("${perf.thread-count:4}")
    private int threadCount;
    /**
     * queue.tatalData = batchSize * queueCapacity
     */
    @Value("${perf.queue-capacity:20}")
    private int queueCapacity;

    @Value("${perf.write-failed-file:true}")
    private boolean writeFailedFile;

    @Value("${perf.write-result-db:true}")
    private boolean writeResultDb;

    /**
     * 数据获取器类型：sql-template 或 audit
     */
    @Value("${perf.data-fetcher-type:sql-template}")
    private String dataFetcherType;

    /**
     * 连续空批次阈值：连续多次返回空结果才认为数据已耗尽（处理时间窗口间隙问题）
     */
    @Value("${perf.max-consecutive-empty:15}")
    private int maxConsecutiveEmpty;
}
