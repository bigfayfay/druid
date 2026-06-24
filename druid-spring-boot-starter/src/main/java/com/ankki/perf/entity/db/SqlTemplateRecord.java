package com.ankki.perf.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("sql_template_record")
public class SqlTemplateRecord {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String md5;

    private Integer dbType;

    @TableField("oper_type")
    private String operType;

    @TableField("tableName")
    private String tableName;

    @TableField("fieldName")
    private String fieldName;

    private String template;

    private String operSentence;

    @TableField("create_time")
    private Long createTime;

    @TableField("tenantId")
    private String tenantId;
}
