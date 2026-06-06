package com.ankki.perf.service.fetch;

import cn.hutool.core.collection.CollectionUtil;
import com.ankki.perf.config.PerfTestConfig;
import com.ankki.perf.entity.SqlTemplateQueryRequest;
import com.ankki.perf.entity.SqlTypeBO;
import com.ankki.perf.entity.convertor.SqlTypeConvertor;
import com.ankki.perf.entity.db.AuditBaseDO;
import com.ankki.perf.mapper.AuditBaseMapper;
import com.ankki.perf.service.DataFetcherService;
import com.ankki.perf.util.AuditUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author fay
 * @date 2026-06-04
 * @description
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "perf.data-fetcher-type", havingValue = "audit")
public class AuditDataFetcher implements DataFetcherService {
    @Resource
    private PerfTestConfig config;
    @Resource
    private AuditBaseMapper auditBaseMapper;

    private static final Long PRE_MISTAKE_SECONDS = 5L;
    private static final Long PST_MISTAKE_SECONDS = 60L;

    @Override
    public List<SqlTypeBO> fetchBatch(SqlTemplateQueryRequest request) {
        List<AuditBaseDO> dataList = null;
        try {
            LambdaQueryWrapper<AuditBaseDO> wrapper = new LambdaQueryWrapper<>();
            wrapper.select(AuditBaseDO::getId,
                    AuditBaseDO::getHappenTime,
                    AuditBaseDO::getDbType,
                    AuditBaseDO::getOperType,
                    AuditBaseDO::getOperSentence
            );
            wrapper.eq(AuditBaseDO::getTenantId, config.getTenantId());
            // 起始 ID 条件
            Long auditId = initSecIfNeed(request.getStartId());
            if (auditId != null) {
                Long auditIdSec = AuditUtils.auditIdSec(auditId);
                wrapper.gt(AuditBaseDO::getId, auditId);
                wrapper.ge(AuditBaseDO::getHappenTime, AuditUtils.sec2DateTimeStr(auditIdSec - PRE_MISTAKE_SECONDS));
                wrapper.le(AuditBaseDO::getHappenTime, AuditUtils.sec2DateTimeStr(auditIdSec + PST_MISTAKE_SECONDS));

            }

            // 排序
            wrapper.orderByAsc(AuditBaseDO::getHappenTime, AuditBaseDO::getId);

            // 分页限制
            wrapper.last("LIMIT " + request.getBatchSize());
            dataList = auditBaseMapper.selectList(wrapper);
            if (CollectionUtil.isEmpty(dataList) && auditId != null) {
                // 前10位(秒)+PST_MISTAKE_SECONDS，后面位数保留，组合为新起始ID（时间窗口前移）
                String idStr = String.valueOf(auditId);
                long toNextIdSec = AuditUtils.auditIdSec(auditId) + PST_MISTAKE_SECONDS;
                String tailPart = idStr.substring(10);
                request.setStartId(Long.valueOf(("" + toNextIdSec) + tailPart));
            }
        } catch (Exception e) {
            log.error("[fetch_error]", e);
            dataList = new ArrayList<>();
        }

        return dataList.stream().map(SqlTypeConvertor.INSTANCE::mapToEntity).collect(Collectors.toList());
    }

    private Long initSecIfNeed(Long auditId) {
        if (Objects.nonNull(auditId) && auditId >= 1000_000_000L) {
            return auditId;
        }

        String happenTime = auditBaseMapper.selectMinTime();
        log.info("initSecIfNeed: happenTime = {}", happenTime);
        if (happenTime == null) {
            return null;
        }
        Long sec = AuditUtils.dateTimeStr2Sec(happenTime);
        // 19位的id
        return Long.valueOf("" + (sec * 1000_000L));
    }
}
