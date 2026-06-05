package com.ankki.perf.service;

import com.ankki.perf.entity.SqlTemplateQueryRequest;
import com.ankki.perf.entity.SqlTypeBO;

import java.util.List;

/**
 * SQL 模板数据获取服务接口
 * 
 * @author fay
 * @date 2026-06-04
 */
public interface DataFetcherService {

    /**
     * 批量获取 SQL 模板记录
     * 
     * @param request 查询请求参数
     * @return SQL 模板记录列表
     */
    List<SqlTypeBO> fetchBatch(SqlTemplateQueryRequest request);

}
