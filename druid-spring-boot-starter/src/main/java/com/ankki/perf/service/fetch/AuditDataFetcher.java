package com.ankki.perf.service.fetch;

import com.ankki.perf.config.PerfTestConfig;
import com.ankki.perf.entity.SqlTemplateQueryRequest;
import com.ankki.perf.entity.SqlTypeBO;
import com.ankki.perf.entity.convertor.SqlTypeConvertor;
import com.ankki.perf.entity.db.AuditBaseDO;
import com.ankki.perf.mapper.AuditBaseMapper;
import com.ankki.perf.service.DataFetcherService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author fay
 * @date 2026-06-04
 * @description
 */
@Service
@ConditionalOnProperty(name = "perf.data-fetcher-type", havingValue = "audit")
public class AuditDataFetcher implements DataFetcherService {
    @Resource
    private PerfTestConfig config;
    @Resource
    private AuditBaseMapper auditBaseMapper;

    @Override
    public List<SqlTypeBO> fetchBatch(SqlTemplateQueryRequest request) {
        LambdaQueryWrapper<AuditBaseDO> wrapper = new LambdaQueryWrapper<>();

        // 起始 ID 条件
        if (request.getStartId() != null) {
            wrapper.gt(AuditBaseDO::getId, request.getStartId());
        }
        // 排序
        wrapper.orderByAsc(AuditBaseDO::getId);

        // 分页限制
        wrapper.last("LIMIT " + request.getBatchSize());

        return auditBaseMapper.selectList(wrapper).stream()
                .map(SqlTypeConvertor.INSTANCE::mapToEntity).collect(Collectors.toList());
    }
}
