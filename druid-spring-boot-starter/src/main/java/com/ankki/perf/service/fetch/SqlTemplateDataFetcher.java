package com.ankki.perf.service.fetch;

import com.alibaba.druid.DbType;
import com.ankki.druid.parser.AkDbTypeEnum;
import com.ankki.perf.config.PerfTestConfig;
import com.ankki.perf.entity.SqlTemplateQueryRequest;
import com.ankki.perf.entity.SqlTypeBO;
import com.ankki.perf.entity.convertor.SqlTypeConvertor;
import com.ankki.perf.entity.db.SqlTemplateRecord;
import com.ankki.perf.mapper.SqlTemplateRecordMapper;
import com.ankki.perf.service.DataFetcherService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

/**
 * SQL 模板数据获取服务实现
 *
 * @author fay
 * @date 2026-06-04
 */
@Service
@ConditionalOnProperty(name = "perf.data-fetcher-type", havingValue = "sql-template", matchIfMissing = true)
public class SqlTemplateDataFetcher implements DataFetcherService {
    @Resource
    private PerfTestConfig config;
    @Resource
    private SqlTemplateRecordMapper sqlTemplateRecordMapper;

    @Override
    public List<SqlTypeBO> fetchBatch(SqlTemplateQueryRequest request) {
        LambdaQueryWrapper<SqlTemplateRecord> wrapper = new LambdaQueryWrapper<>();
        AkDbTypeEnum akDbTypeEnum = AkDbTypeEnum.of(DbType.of(config.getDbType()));

        // 起始 ID 条件
        if (request.getStartId() != null) {
            wrapper.gt(SqlTemplateRecord::getId, request.getStartId());
        }

        // SQL 类型过滤（如果有）
        if (request.getSqlTypes() != null && !request.getSqlTypes().isEmpty()) {
            List<String> operTypes = request.getSqlTypes().stream()
                    .map(SqlTypeBO::getOperType)
                    .filter(operType -> operType != null && !operType.isEmpty())
                    .distinct()
                    .collect(java.util.stream.Collectors.toList());

            if (!operTypes.isEmpty()) {
                wrapper.in(SqlTemplateRecord::getOperType, operTypes);
            }
        }

        // 排序
        wrapper.orderByAsc(SqlTemplateRecord::getId);

        // 分页限制
        wrapper.last("LIMIT " + request.getBatchSize());

        return sqlTemplateRecordMapper.selectList(wrapper).stream()
                .map(d -> {
                    SqlTypeBO sqlTypeBO = SqlTypeConvertor.INSTANCE.mapToEntity(d);
                    sqlTypeBO.setDbType(akDbTypeEnum.getTypeId());
                    return sqlTypeBO;
                }).collect(Collectors.toList());
    }

}
