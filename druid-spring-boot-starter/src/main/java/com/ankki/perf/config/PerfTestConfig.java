package com.ankki.perf.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

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

    /**
     * dbType转换配置：将数据库中的dbType值转换为解析器需要的值
     * 格式：key1=value1,key2=value2 （例如：1=2,27=3）
     */
    @Value("${perf.db-type-map:}")
    private String dbTypeMapStr;

    /**
     * dbType转换Map（懒加载）
     */
    private volatile Map<Integer, Integer> dbTypeMap;

    /**
     * 获取dbType转换Map
     */
    public Map<Integer, Integer> getDbTypeMap() {
        if (dbTypeMap == null) {
            synchronized (this) {
                if (dbTypeMap == null) {
                    dbTypeMap = parseDbTypeMap(dbTypeMapStr);
                }
            }
        }
        return dbTypeMap;
    }

    /**
     * 转换dbType值
     * @param originalDbType 原始dbType值
     * @return 转换后的dbType值，如果没有配置转换则返回原值
     */
    public Integer convertDbType(Integer originalDbType) {
        if (originalDbType == null) {
            return null;
        }
        Map<Integer, Integer> map = getDbTypeMap();
        return map.getOrDefault(originalDbType, originalDbType);
    }

    /**
     * 解析dbType转换配置字符串
     * @param configStr 配置字符串，格式：key1=value1,key2=value2
     * @return 转换Map
     */
    private Map<Integer, Integer> parseDbTypeMap(String configStr) {
        Map<Integer, Integer> map = new HashMap<>();
        if (configStr == null || configStr.trim().isEmpty()) {
            return map;
        }

        String[] pairs = configStr.split(",");
        for (String pair : pairs) {
            String[] kv = pair.trim().split("=");
            if (kv.length == 2) {
                try {
                    Integer key = Integer.parseInt(kv[0].trim());
                    Integer value = Integer.parseInt(kv[1].trim());
                    map.put(key, value);
                } catch (NumberFormatException e) {
                    // 忽略无效配置
                }
            }
        }
        return map;
    }

}
