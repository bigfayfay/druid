package com.ankki.perf.mapper;


import com.ankki.perf.entity.db.AuditBaseDO;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@DS("ck")
@Mapper
public interface AuditBaseMapper extends BaseMapper<AuditBaseDO> {

}
