package com.ankki.perf.mapper;

import com.ankki.perf.entity.db.SqlTemplateRes;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SqlTemplateResMapper extends BaseMapper<SqlTemplateRes> {

    /**
     * INSERT ON DUPLICATE KEY UPDATE：id 或 sql_md5 冲突则 update
     */
    @Insert("INSERT INTO sql_template_res (id, oper_type, db_type, oper_sentence, status, cost_ns, sql_md5, sql_len, fail_num, fail_reason, remark) " +
            "VALUES (#{r.id}, #{r.operType}, #{r.dbType}, #{r.operSentence}, #{r.status}, #{r.costNs}, #{r.sqlMd5}, #{r.sqlLen}, #{r.failNum}, #{r.failReason}, #{r.remark}) " +
            "ON DUPLICATE KEY UPDATE " +
            "fail_num = IFNULL(fail_num, 0) + 1")
    int insertOrUpdate(@Param("r") SqlTemplateRes record);
}