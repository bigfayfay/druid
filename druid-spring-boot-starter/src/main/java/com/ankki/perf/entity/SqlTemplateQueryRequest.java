package com.ankki.perf.entity;

import lombok.Data;

/**
 * SQL 模板查询请求参数
 * 
 * @author fay
 * @date 2026-06-04
 */
@Data
public class SqlTemplateQueryRequest {

    /**
     * 起始 ID（查询 ID > startId 的记录）
     */
    private Long startId;

    /**
     * 批次大小
     */
    private Integer batchSize;

    /**
     * SQL 类型列表（可选过滤条件）
     */
    private java.util.List<SqlTypeBO> sqlTypes;

    public SqlTemplateQueryRequest() {
    }

    public SqlTemplateQueryRequest(Long startId, Integer batchSize) {
        this.startId = startId;
        this.batchSize = batchSize;
    }

    public SqlTemplateQueryRequest(Long startId, Integer batchSize, java.util.List<SqlTypeBO> sqlTypes) {
        this.startId = startId;
        this.batchSize = batchSize;
        this.sqlTypes = sqlTypes;
    }
}
