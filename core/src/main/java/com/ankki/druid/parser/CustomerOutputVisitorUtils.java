package com.ankki.druid.parser;

import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.SQLStatement;
import com.alibaba.druid.sql.visitor.ParameterizedOutputVisitorUtils;
import com.alibaba.druid.sql.visitor.SQLASTOutputVisitor;
import com.alibaba.druid.sql.visitor.SQLASTVisitor;
import com.alibaba.druid.util.StringUtils;
import com.ankki.druid.parser.oracle.InsertTemplateStandardizer;
import com.ankki.druid.parser.oracle.SqlTemplateStandardizer;
import com.ankki.druid.parser.template.AkOutputVisitorUtils;
import com.ankki.druid.parser.visitor.AkSchemaStatVisitor;

import java.util.*;

public class CustomerOutputVisitorUtils {
    private static final String BIND_APPEND = ";(";

    private static final String OPERATION_TYPE_INSERT = "INSERT";

    private static final String OPERATION_TYPE_SELECT = "SELECT";

    public static String getRemoveBindingParameterSql(String sql, Integer akDbTypeId) {
        DbType dbType = getDbType(akDbTypeId);
        if (dbType == null) {
            return "";
        }
        try {
            int lastIndexOfBindAppend = sql.lastIndexOf(";(");
            if (lastIndexOfBindAppend <= -1) {
                return sql;
            }
            StringBuilder out = new StringBuilder(sql.length());
            String backFillSql = sql.substring(0, lastIndexOfBindAppend + 1);
            List<SQLStatement> stmtList = SQLUtils.parseStatements(backFillSql, dbType);
            AkSchemaStatVisitor akVisitor = new AkSchemaStatVisitor(dbType);
            for (SQLStatement stmt : stmtList) {
                stmt.accept((SQLASTVisitor) akVisitor);
            }
            SQLASTOutputVisitor visitor = SQLUtils.createOutputVisitor(out, dbType);
            for (SQLStatement stmt : stmtList) {
                stmt.accept((SQLASTVisitor) visitor);
            }
            return out.toString();
        } catch (Exception e) {
            return AkSqlParserStatusEnum.Failure.toString(e.getMessage());
        }
    }

    private static String sqlBindParamMap(String sql, String backFillSql) {
        Map<String, Object> parameterMap = new HashMap<>();
        if (backFillSql.contains(";(")) {
            backFillSql = sql.substring(0, sql.lastIndexOf(";(") + 1);
            String parameterString = sql.substring(sql.lastIndexOf(";(") + 2, sql.length() - 1);
            if (!StringUtils.isEmpty(backFillSql) && !StringUtils.isEmpty(parameterString)) {
                List<String> parameters = Arrays.asList(parameterString.split(","));
                for (String parameter : parameters) {
                    String[] param = parameter.split("=");
                    if (param.length == 2 && param[0] != null) {
                        parameterMap.put(param[0].trim(), param[1]);
                    }
                }
            }
        }
        return backFillSql;
    }

    private static DbType getDbType(Integer akDbTypeId) {
        return AkDruidSqlParser.akDruidDbTypeMap.get(akDbTypeId);
    }

    public static String getSqlTemplate(String sql, Integer akDbTypeId) {
        String removeParameterSql = getRemoveBindingParameterSql(sql, akDbTypeId);
        if (StringUtils.isEmpty(removeParameterSql)) {
            return AkSqlParserStatusEnum.Failure.toString("");
        }
        if (removeParameterSql.contains(AkSqlParserStatusEnum.Failure.name() + "|")) {
            return removeParameterSql;
        }
        DbType dbType = getDbType(akDbTypeId);
        if (dbType == null) {
            return AkSqlParserStatusEnum.NonSupport.toString();
        }
        try {
            String sqlTemplate = processSqlTemplate(removeParameterSql, dbType, akDbTypeId);
            if (StringUtils.isEmpty(sqlTemplate)) {
                return AkSqlParserStatusEnum.Failure.toString("");
            }
            String md5Hash = AkDruidSqlParser.generateMD5(sqlTemplate);
            return AkSqlParserStatusEnum.Success.toString(md5Hash + "|" + sqlTemplate);
        } catch (Exception e) {
            return AkSqlParserStatusEnum.Failure.toString(e.getMessage());
        }
    }

    public static String[] getSqlTemplate_v2(String sql, Integer akDbTypeId) {
        return AkOutputVisitorUtils.getSqlTemplate_v2(sql, akDbTypeId);
    }

    private static String processSqlTemplate(String removeParameterSql, DbType dbType, Integer akDbTypeId) throws Exception {
        if (dbType == DbType.oracle) {
            String operationType = getOperationType(removeParameterSql, akDbTypeId);
            if ("INSERT".equals(operationType)) {
                return InsertTemplateStandardizer.extractInsertTemplate(removeParameterSql, dbType);
            }
            if ("SELECT".equals(operationType)) {
                String normalizedSql = SqlTemplateStandardizer.normalizeSql(removeParameterSql, dbType);
                return ParameterizedOutputVisitorUtils.parameterize(normalizedSql, dbType);
            }
        }
        return ParameterizedOutputVisitorUtils.parameterize(removeParameterSql, dbType);
    }

    private static String getOperationType(String sql, Integer akDbTypeId) {
        if (StringUtils.isEmpty(sql)) {
            return "";
        }
        DbType dbType = getDbType(akDbTypeId);
        if (dbType == null) {
            return "";
        }
        try {
            List<SQLStatement> statements = SQLUtils.parseStatements(sql, dbType);
            if (Objects.isNull(statements) || statements.isEmpty()) {
                return "";
            }
            SQLStatement stmt = statements.get(0);
            if (stmt instanceof com.alibaba.druid.sql.ast.statement.SQLSelectStatement) {
                return "SELECT";
            }
            if (stmt instanceof com.alibaba.druid.sql.ast.statement.SQLInsertStatement) {
                return "INSERT";
            }
            if (stmt instanceof com.alibaba.druid.sql.ast.statement.SQLUpdateStatement) {
                return "UPDATE";
            }
            if (stmt instanceof com.alibaba.druid.sql.ast.statement.SQLDeleteStatement) {
                return "DELETE";
            }
            String className = stmt.getClass().getSimpleName();
            if (className.endsWith("Statement")) {
                String typeName = className.substring(0, className.length() - "Statement".length());
                if (typeName.startsWith("SQL")) {
                    typeName = typeName.substring(3);
                }
                if (typeName.startsWith("Create")) {
                    return "CREATE";
                }
                if (typeName.startsWith("Drop")) {
                    return "DROP";
                }
                if (typeName.startsWith("Alter")) {
                    return "ALTER";
                }
                if (typeName.startsWith("Truncate")) {
                    return "TRUNCATE";
                }
                if (typeName.startsWith("Replace")) {
                    return "REPLACE";
                }
                return typeName.toUpperCase();
            }
            return "";
        } catch (Exception e) {
            return "";
        }
    }

    public static void main(String[] args) {
        String selectSql = "SELECT * FROM dual";
        System.out.println(getSqlTemplate(selectSql, Integer.valueOf(3)));
        System.out.println(AkDruidSqlParser.getSqlTemplate(selectSql, Integer.valueOf(3), Boolean.valueOf(true)));
    }
}
