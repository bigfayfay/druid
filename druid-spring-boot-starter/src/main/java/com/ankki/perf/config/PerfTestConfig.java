package com.ankki.perf.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Data
@Component
public class PerfTestConfig {

    // -- ------------------------------ fetch-param
    /**
     * select-dataList.size
     */
    @Value("${perf.batch-size:1000}")
    private int batchSize;

    /**
     * MAX records to process
     */
    @Value("${perf.max-records:0}")
    private long maxRecords;
    /**
     * 起始id
     */
    @Value("${perf.start-id:0}")
    private long startId;

    /**
     * 连续空批次阈值：连续多次返回空结果才认为数据已耗尽（处理时间窗口间隙问题）
     */
    @Value("${perf.max-consecutive-empty:15}")
    private int maxConsecutiveEmpty;

    /**
     * 租户ID：用于数据拉取时过滤租户
     */
    @Value("${perf.tenant-id:0}")
    private String tenantId;


    @Value("${perf.db-type:oracle}")
    private String dbType;

    /**
     * 数据获取器类型：sql-template 或 audit
     */
    @Value("${perf.data-fetcher-type:sql-template}")
    private String dataFetcherType;



    // -- ------------------------------ perf-param

    @Value("${perf.enabled:true}")
    private boolean enabled;
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

    @Value("${perf.warmup-batches:2}")
    private int warmupBatches;

    // -- ------------------------------ post-handler

    @Value("${perf.write-failed-file:true}")
    private boolean writeFailedFile;

    @Value("${perf.write-result-db:true}")
    private boolean writeResultDb;


    /**
     * DB批量刷新阈值：缓冲区达到该数量后执行批量写入
     */
    @Value("${perf.flush-threshold:180}")
    private int flushThreshold;

    /**
     * 仅处理错误记录：开启后只有解析失败的记录才写入DB（跳过 Success 状态）
     */
    @Value("${perf.error-only:true}")
    private boolean errorOnly;

}
