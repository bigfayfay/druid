package com.ankki.perf.service.fetch;

import com.alibaba.druid.DbType;
import com.ankki.druid.parser.AkDbTypeEnum;
import com.ankki.perf.config.PerfTestConfig;
import com.ankki.perf.entity.SqlTemplateQueryRequest;
import com.ankki.perf.entity.SqlTypeBO;
import com.ankki.perf.entity.convertor.SqlTypeConvertor;
import com.ankki.perf.entity.db.SqlTemplateRes;
import com.ankki.perf.mapper.SqlTemplateResMapper;
import com.ankki.perf.service.DataFetcherService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * SQL 模板数据获取服务实现
 *
 * @author fay
 * @date 2026-06-04
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "perf.data-fetcher-type", havingValue = "sql-res", matchIfMissing = true)
public class SqlTemplateResFetcher implements DataFetcherService {
    @Resource
    private PerfTestConfig config;
    @Resource
    private SqlTemplateResMapper SqlTemplateResMapper;

    @Override
    public List<SqlTypeBO> fetchBatch(SqlTemplateQueryRequest request) {
        AkDbTypeEnum akDbTypeEnum = AkDbTypeEnum.of(DbType.of(config.getDbType()));
        List<SqlTemplateRes> dataList = null;
        try {
            LambdaQueryWrapper<SqlTemplateRes> wrapper = new LambdaQueryWrapper<>();


            // 起始 ID 条件
            if (request.getStartId() != null) {
                wrapper.gt(SqlTemplateRes::getId, request.getStartId());
            }

            // SQL 类型过滤（如果有）
            if (request.getSqlTypes() != null && !request.getSqlTypes().isEmpty()) {
                List<String> operTypes = request.getSqlTypes().stream()
                        .map(SqlTypeBO::getOperType)
                        .filter(operType -> operType != null && !operType.isEmpty())
                        .distinct()
                        .collect(Collectors.toList());

                if (!operTypes.isEmpty()) {
                    wrapper.in(SqlTemplateRes::getOperType, operTypes);
                }
            }

            // 排序
            wrapper.orderByAsc(SqlTemplateRes::getId);

            // 分页限制
            wrapper.last("LIMIT " + request.getBatchSize());
            dataList = SqlTemplateResMapper.selectList(wrapper);
        } catch (Exception e) {
            log.error("[fetch_error]", e);
            dataList = new ArrayList<>();
        }

        return dataList.stream().map(SqlTypeConvertor.INSTANCE::resToEntity).collect(Collectors.toList());
    }

}
