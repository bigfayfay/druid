package com.ankki.perf.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("sql_template_res")
public class SqlTemplateRes {

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField(exist = false)
    private String operType;

    /**
     * 状态
     */
    private String status;

    /**
     * 耗时(ns)
     */
    private Long costNs;

    /**
     * SQL长度
     */
    private Integer sqlLen;

    /**
     * 失败原因
     */
    private String failReason;

    /**
     * 备注
     */
    private String remark;
}