package com.ankki.perf.entity.db;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("audit_record")
public class AuditBaseDO {
    @TableId("id")
    private Long id;

    @TableField("happenTime")
    private Long happenTime;

    @TableField("dbType")
    private Integer dbType;

    @TableField("operType")
    private String operType;

    @TableField("operSentence")
    private String operSentence;

    @TableField("operSentenceLen")
    private Integer operSentenceLen;

    @TableField("protectObjectName")
    private String protectObjectName;

    @TableField("tenantId")
    private String tenantId;
}