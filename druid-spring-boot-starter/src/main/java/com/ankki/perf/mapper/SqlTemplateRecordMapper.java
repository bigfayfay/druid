package com.ankki.perf.mapper;

import com.ankki.perf.entity.db.SqlTemplateRecord;
import com.ankki.perf.entity.db.SqlTemplateRes;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SqlTemplateRecordMapper extends BaseMapper<SqlTemplateRecord> {


    /**
     * INSERT ON DUPLICATE KEY UPDATE：id 或 sql_md5 冲突则 update
     */
    @Insert("INSERT INTO bs_audit.sql_template_record (id, db_type, md5, oper_type, tableName, fieldName, template, oper_sentence, create_time, tenantId) " +
            "VALUES (#{r.id}, #{r.dbType}, #{r.md5}, #{r.operType}, #{r.tableName}, #{r.fieldName}, #{r.template}, #{r.operSentence}, #{r.createTime}, #{r.tenantId}) " +
            "ON DUPLICATE KEY UPDATE " +
            "oper_type = #{r.operType}")
    int insertDuplicateKey(@Param("r") SqlTemplateRecord record);


}
