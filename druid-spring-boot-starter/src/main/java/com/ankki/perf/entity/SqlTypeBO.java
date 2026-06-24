package com.ankki.perf.entity;

import com.ankki.perf.entity.db.SqlTemplateRes;
import lombok.Data;

/**
 * @author fay
 * @date 2026-06-04
 * @description
 */
@Data
public class SqlTypeBO {
    private Long id;

    private Integer dbType;

    private String operType;

    private String operSentence;

    private String tenantId;

    public SqlTemplateRes toSqlReds(){
        SqlTemplateRes res = new SqlTemplateRes();
        res.setId(id);
        res.setDbType(dbType);
        res.setOperType(operType);
        res.setOperSentence(operSentence);
        res.setSqlLen(operSentence != null ? operSentence.length() : 0);
        res.setTenantId(tenantId);
        return res;
    }
}
