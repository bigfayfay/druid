package com.ankki.perf.mapper;


import com.ankki.perf.entity.db.AuditBaseDO;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@DS("ck")
@Mapper
public interface AuditBaseMapper extends BaseMapper<AuditBaseDO> {

    @Select("select min(happenTime) from audit_record where tenantId = #{tenantId} and happenTime >= '2021-01-01 00:00:00'")
    String selectMinTime(@Param("tenantId") String tenantId);

    /**
     * 查找指定 ID 之后的下一条记录的 happenTime（用于跳过时间间隙）
     */
    @Select("select happenTime from audit_record where id > #{lastId} order by happenTime, id limit 1")
    String selectNextTimeAfterId(@Param("lastId") Long lastId);
}
