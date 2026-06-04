/*
 * Decompiled with CFR 0.152.
 */
package com.ankki.druid.parser.template;

import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.visitor.ParameterizedOutputVisitorUtils;
import com.alibaba.druid.util.StringUtils;
import com.ankki.druid.parser.AkDruidSqlParser;
import com.ankki.druid.parser.AkSqlParserStatusEnum;
import com.ankki.druid.parser.bind.ParameterValuesFormatter;
import com.ankki.druid.parser.utils.AkDruidUtil;

import java.util.ArrayList;

public class AkOutputVisitorUtils {
    private static final String BIND_APPEND = ";(";

    public static String[] getSqlTemplate_v2(String sql, Integer akDbTypeId) {
        String[] result = new String[5];
        AkTemplateResult templateResult = AkOutputVisitorUtils.getSqlTemplateBindValues(sql, akDbTypeId);
        result[0] = templateResult.getStatus().name();
        result[1] = templateResult.getMd5();
        result[2] = templateResult.getTemplate();
        String sqlBind = templateResult.getSqlBind();
        result[3] = sqlBind != null ? sqlBind : ParameterValuesFormatter.sqlBind(templateResult.getParameterValues());
        result[4] = templateResult.getExceptionMsg();
        return result;
    }

    public static AkTemplateResult getSqlTemplateBindValues(String sql, Integer akDbTypeId) {
        AkTemplateResult result = new AkTemplateResult();
        DbType dbType = AkDruidUtil.getDbType(akDbTypeId);
        if (dbType == null) {
            result.setStatus(AkSqlParserStatusEnum.NonSupport);
            return result;
        }
        result.setDbType(dbType);
        result.setAkDbTypeId(akDbTypeId);
        try {
            String sqlTemplate;
            int bindIdx = sql.lastIndexOf(BIND_APPEND);
            if (bindIdx > 0) {
                sqlTemplate = SQLUtils.format(sql.substring(0, bindIdx), dbType);
                String sqlBind = sql.substring(bindIdx + 1);
                result.setSqlBind(sqlBind);
            } else {
                result.setParameterValues(new ArrayList<Object>());
                sqlTemplate = ParameterizedOutputVisitorUtils.parameterize(sql, dbType, result.getParameterValues());
            }
            if (StringUtils.isEmpty(sqlTemplate)) {
                result.setStatus(AkSqlParserStatusEnum.Failure);
                result.setExceptionMsg("sql template is empty");
                return result;
            }
            String md5Hash = AkDruidSqlParser.generateMD5(sqlTemplate);
            result.setMd5(md5Hash);
            result.setTemplate(sqlTemplate);
            result.setStatus(AkSqlParserStatusEnum.Success);
            return result;
        }
        catch (Exception e) {
            result.setStatus(AkSqlParserStatusEnum.Failure);
            result.setExceptionMsg(e.getMessage());
            return result;
        }
    }
}

