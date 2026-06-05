package com.ankki.perf.mapper;

import com.ankki.perf.entity.db.SqlTemplateRes;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SqlTemplateResMapper extends BaseMapper<SqlTemplateRes> {

    /**
     * INSERT ON DUPLICATE KEY UPDATE：id 不存在则 insert，存在则 update
     */
    @Insert("INSERT INTO sql_template_res (id, oper_type, oper_sentence, status, cost_ns, sql_len, fail_reason, remark) " +
            "VALUES (#{r.id}, #{r.operType}, #{r.operSentence}, #{r.status}, #{r.costNs}, #{r.sqlLen}, #{r.failReason}, #{r.remark}) " +
            "ON DUPLICATE KEY UPDATE " +
            "oper_type = VALUES(oper_type), " +
            "oper_sentence = VALUES(oper_sentence), " +
            "status = VALUES(status), " +
            "cost_ns = VALUES(cost_ns), " +
            "sql_len = VALUES(sql_len), " +
            "fail_reason = VALUES(fail_reason), " +
            "remark = VALUES(remark)")
    int insertOrUpdate(@Param("r") SqlTemplateRes record);
}