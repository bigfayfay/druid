/*
 * Decompiled with CFR 0.152.
 */
package com.ankki.druid.parser;

import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.SQLStatement;
import com.alibaba.druid.sql.ast.expr.SQLMethodInvokeExpr;
import com.alibaba.druid.sql.visitor.ParameterizedOutputVisitorUtils;
import com.alibaba.druid.sql.visitor.SQLASTOutputVisitor;
import com.alibaba.druid.sql.visitor.SchemaStatVisitor;
import com.alibaba.druid.stat.TableStat;
import com.alibaba.druid.util.StringUtils;
import com.alibaba.druid.wall.WallCheckResult;
import com.ankki.druid.parser.AkDbTypeEnum;
import com.ankki.druid.parser.AkDruidResult;
import com.ankki.druid.parser.AkSqlParserStatusEnum;
import com.ankki.druid.parser.AkWallProviderEnum;
import com.ankki.druid.parser.visitor.DbVisitorUtils;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class AkDruidSqlParser
extends ParameterizedOutputVisitorUtils {
    private static SchemaStatVisitor visitor;
    private static DbType dbType;
    private static AkDruidResult akResult;
    public static Map<Integer, DbType> akDruidDbTypeMap;
    private static final char[] HEX_ARRAY;

    public static void addDbType(int akDbTypeId, String druidTypeName) {
        if (akDbTypeId <= 0 || StringUtils.isEmpty(druidTypeName)) {
            return;
        }
        DbType druidDbType = DbType.of(druidTypeName);
        if (druidDbType == null) {
            return;
        }
        akDruidDbTypeMap.put(akDbTypeId, druidDbType);
    }

    public static String checkSqlInject(String sql, int akDbTypeId) {
        AkDruidSqlParser.clear();
        dbType = AkDruidSqlParser.getDbType(akDbTypeId);
        if (dbType == null) {
            return AkSqlParserStatusEnum.NonSupport.toString();
        }
        try {
            WallCheckResult wallCheckResult = AkWallProviderEnum.of(dbType).checkSqlInject(sql);
            if (wallCheckResult.getViolations().isEmpty()) {
                akResult.setSqlInject(Boolean.TRUE);
                akResult.setStatus(AkSqlParserStatusEnum.Success);
                return AkSqlParserStatusEnum.Success.toString();
            }
            akResult.setSqlInject(Boolean.FALSE);
            akResult.setStatus(AkSqlParserStatusEnum.Success);
            return AkSqlParserStatusEnum.Failure.toString(wallCheckResult.getViolations().toString());
        }
        catch (Exception e) {
            akResult.setStatus(AkSqlParserStatusEnum.EXCEPTION);
            akResult.setExceptionMsg(e.getMessage());
            return AkSqlParserStatusEnum.Failure.toString(e.getMessage());
        }
    }

    public static String getRemoveBindingParameterSql(String sql, Integer akDbTypeId) {
        AkDruidSqlParser.clear();
        dbType = AkDruidSqlParser.getDbType(akDbTypeId);
        if (dbType == null) {
            akResult.setStatus(AkSqlParserStatusEnum.NonSupport);
            return AkSqlParserStatusEnum.NonSupport.toString();
        }
        StringBuilder out = new StringBuilder(sql.length());
        try {
            String backFillSql = sql;
            HashMap<String, Object> parameterMap = new HashMap<String, Object>();
            if (backFillSql.contains(";(")) {
                backFillSql = sql.substring(0, sql.lastIndexOf(";(") + 1);
                String parameterString = sql.substring(sql.lastIndexOf(";(") + 2, sql.length() - 1);
                if (!StringUtils.isEmpty(backFillSql) && !StringUtils.isEmpty(parameterString)) {
                    List<String> parameters = Arrays.asList(parameterString.split(","));
                    Iterator iterator = parameters.iterator();
                    while (iterator.hasNext()) {
                        String parameter = (String)iterator.next();
                        String[] param = parameter.split("=");
                        if (param.length != 2 || param[0] == null) continue;
                        parameterMap.put(param[0].trim(), param[1]);
                    }
                }
            }
            List<SQLStatement> stmtList = SQLUtils.parseStatements(backFillSql, dbType);
            DbVisitorUtils.setParameters(parameterMap);
            visitor = DbVisitorUtils.getDruidVisit(dbType);
            for (SQLStatement sQLStatement : stmtList) {
                sQLStatement.accept(visitor);
            }
            SQLASTOutputVisitor visitor = SQLUtils.createOutputVisitor(out, dbType);
            for (SQLStatement stmt : stmtList) {
                stmt.accept(visitor);
            }
            String string = out.toString();
            akResult.setStatus(AkSqlParserStatusEnum.Success);
            akResult.setRemoveBindingParameterSql(string);
            return AkSqlParserStatusEnum.Success.toString(string);
        }
        catch (Exception e) {
            visitor = null;
            akResult.setStatus(AkSqlParserStatusEnum.EXCEPTION);
            akResult.setExceptionMsg(e.getMessage());
            return AkSqlParserStatusEnum.Failure.toString(e.getMessage());
        }
    }

    public static void main(String[] args) {
        String selectSql = "SELECT * FROM dual";
        System.out.println(AkDruidSqlParser.getSqlTemplate(selectSql, 3, true));
    }

    public static String getSqlTemplate(String sql, Integer akDbTypeId, Boolean hasBindingParameter) {
        AkDruidSqlParser.clear();
        if (hasBindingParameter.booleanValue()) {
            String result = AkDruidSqlParser.getRemoveBindingParameterSql(sql, akDbTypeId);
            sql = akResult.getRemoveBindingParameterSql();
            if (StringUtils.isEmpty(akResult.getRemoveBindingParameterSql())) {
                return result;
            }
        } else {
            dbType = AkDruidSqlParser.getDbType(akDbTypeId);
        }
        if (dbType == null) {
            akResult.setStatus(AkSqlParserStatusEnum.NonSupport);
            return AkSqlParserStatusEnum.NonSupport.toString();
        }
        try {
            String sqlTemplate = ParameterizedOutputVisitorUtils.parameterize(sql, dbType, akResult.getOutParameterValue());
            if (StringUtils.isEmpty(sqlTemplate)) {
                return AkSqlParserStatusEnum.Failure.toString("\u6a21\u677f\u89e3\u6790\u4e3a\u7a7a");
            }
            String md5Hash = AkDruidSqlParser.generateMD5(sqlTemplate);
            akResult.setStatus(AkSqlParserStatusEnum.Success);
            akResult.setTemplate(sqlTemplate);
            akResult.setMd5(md5Hash);
            return AkSqlParserStatusEnum.Success.toString(md5Hash + "|" + sqlTemplate);
        }
        catch (Exception e) {
            akResult.setStatus(AkSqlParserStatusEnum.EXCEPTION);
            akResult.setExceptionMsg(e.getMessage());
            return AkSqlParserStatusEnum.Failure.toString(e.getMessage());
        }
    }

    public static String getTables() {
        if (dbType == null) {
            return AkSqlParserStatusEnum.NonSupport.toString();
        }
        if (visitor == null) {
            return AkSqlParserStatusEnum.Failure.toString();
        }
        try {
            StringBuffer buf = new StringBuffer();
            Map<TableStat.Name, TableStat> visitorTables = visitor.getTables();
            if (visitorTables != null && !visitorTables.isEmpty()) {
                visitorTables.forEach((key, value) -> {
                    String firstOprType = AkDruidSqlParser.getFirstOperationType(value);
                    buf.append(key.toString()).append(":").append(firstOprType).append("|");
                });
                buf.deleteCharAt(buf.length() - 1);
            }
            return AkSqlParserStatusEnum.Success.toString(buf.toString());
        }
        catch (Exception e) {
            return AkSqlParserStatusEnum.Failure.toString(e.getMessage());
        }
    }

    public static String getColumns() {
        if (dbType == null) {
            return AkSqlParserStatusEnum.NonSupport.toString();
        }
        if (visitor == null) {
            return AkSqlParserStatusEnum.Failure.toString();
        }
        try {
            StringBuffer buf = new StringBuffer();
            Collection<TableStat.Column> columns = visitor.getColumns();
            if (columns != null && !columns.isEmpty()) {
                columns.forEach(row -> buf.append(row.getName()).append("|"));
                buf.deleteCharAt(buf.length() - 1);
            }
            return AkSqlParserStatusEnum.Success.toString(buf.toString());
        }
        catch (Exception e) {
            return AkSqlParserStatusEnum.Failure.toString(e.getMessage());
        }
    }

    public static String getFunctions() {
        if (dbType == null) {
            return AkSqlParserStatusEnum.NonSupport.toString();
        }
        if (visitor == null) {
            return AkSqlParserStatusEnum.Failure.toString();
        }
        try {
            StringBuffer buf = new StringBuffer();
            List<SQLMethodInvokeExpr> functions = visitor.getFunctions();
            if (functions != null && !functions.isEmpty()) {
                functions.forEach(row -> buf.append(row.toString()).append("|"));
                buf.deleteCharAt(buf.length() - 1);
            }
            return AkSqlParserStatusEnum.Success.toString(buf.toString());
        }
        catch (Exception e) {
            return AkSqlParserStatusEnum.Failure.toString(e.getMessage());
        }
    }

    public static String getConditions() {
        if (dbType == null) {
            return AkSqlParserStatusEnum.NonSupport.toString();
        }
        if (visitor == null) {
            return AkSqlParserStatusEnum.Failure.toString();
        }
        try {
            StringBuffer buf = new StringBuffer();
            List<TableStat.Condition> conditions = visitor.getConditions();
            if (conditions != null && !conditions.isEmpty()) {
                conditions.forEach(row -> buf.append(row.toString()).append("|"));
                buf.deleteCharAt(buf.length() - 1);
            }
            return AkSqlParserStatusEnum.Success.toString(buf.toString());
        }
        catch (Exception e) {
            return AkSqlParserStatusEnum.Failure.toString(e.getMessage());
        }
    }

    public static String getParameterValues() {
        if (dbType == null) {
            return AkSqlParserStatusEnum.NonSupport.toString();
        }
        try {
            return AkSqlParserStatusEnum.Success.toString(akResult.getOutParameterValue().toString());
        }
        catch (Exception e) {
            return AkSqlParserStatusEnum.Failure.toString(e.getMessage());
        }
    }

    public static AkDruidResult getResolver(String sql, int akDbType) {
        List<SQLMethodInvokeExpr> functions;
        List<TableStat.Condition> conditions;
        Collection<TableStat.Column> columns;
        AkDruidSqlParser.getSqlTemplate(sql, akDbType, true);
        if (dbType == null) {
            akResult.setStatus(AkSqlParserStatusEnum.NonSupport);
            return akResult;
        }
        if (visitor == null) {
            akResult.setStatus(AkSqlParserStatusEnum.Failure);
            return akResult;
        }
        Map<TableStat.Name, TableStat> visitorTables = visitor.getTables();
        if (visitorTables != null && !visitorTables.isEmpty()) {
            visitorTables.forEach((key, value) -> {
                akResult.getTableName().add(key.toString());
                akResult.getTableNameAndOperType().add(key + ":" + value);
                if (akResult.getFirstOperType() == null || akResult.getFirstOperType().equals("")) {
                    akResult.setFirstOperType(value.toString());
                }
            });
        }
        if ((columns = visitor.getColumns()) != null && !columns.isEmpty()) {
            columns.forEach(row -> akResult.getFieldName().add(row.getName()));
        }
        if ((conditions = visitor.getConditions()) != null && !conditions.isEmpty()) {
            conditions.forEach(row -> akResult.getConditions().add(row.toString()));
        }
        if ((functions = visitor.getFunctions()) != null && !functions.isEmpty()) {
            functions.forEach(row -> akResult.getFunctions().add(row.toString()));
        }
        return akResult;
    }

    private static DbType getDbType(Integer akDbTypeId) {
        if (dbType == null) {
            dbType = akDruidDbTypeMap.get(akDbTypeId);
            akResult.setDbType(dbType);
        }
        return dbType;
    }

    public static void clear() {
        visitor = null;
        dbType = null;
        akResult = new AkDruidResult();
    }

    private static String getFirstOperationType(TableStat tableStat) {
        if (tableStat == null) {
            return "";
        }
        if (tableStat.getMergeCount() > 0) {
            return "Merge";
        }
        if (tableStat.getInsertCount() > 0) {
            return "Insert";
        }
        if (tableStat.getUpdateCount() > 0) {
            return "Update";
        }
        if (tableStat.getSelectCount() > 0) {
            return "Select";
        }
        if (tableStat.getDeleteCount() > 0) {
            return "Delete";
        }
        if (tableStat.getDropCount() > 0) {
            return "Drop";
        }
        if (tableStat.getCreateCount() > 0) {
            return "Create";
        }
        if (tableStat.getAlterCount() > 0) {
            return "Alter";
        }
        if (tableStat.getCreateIndexCount() > 0) {
            return "CreateIndex";
        }
        if (tableStat.getDropIndexCount() > 0) {
            return "DropIndex";
        }
        if (tableStat.getAddCount() > 0) {
            return "Add";
        }
        if (tableStat.getAddPartitionCount() > 0) {
            return "AddPartition";
        }
        if (tableStat.getAnalyzeCount() > 0) {
            return "Analyze";
        }
        return "";
    }

    public static String generateMD5(String input) throws NoSuchAlgorithmException {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(input.getBytes());
            byte[] digest = md.digest();
            StringBuilder hexString = new StringBuilder(2 * digest.length);
            for (byte b : digest) {
                hexString.append(HEX_ARRAY[b >> 4 & 0xF]);
                hexString.append(HEX_ARRAY[b & 0xF]);
            }
            return hexString.toString();
        }
        catch (NoSuchAlgorithmException e) {
            throw new NoSuchAlgorithmException("MD5\u751f\u6210\u5931\u8d25");
        }
    }

    static {
        akResult = new AkDruidResult();
        akDruidDbTypeMap = new HashMap<Integer, DbType>(){
            {
                for (AkDbTypeEnum akDbType : AkDbTypeEnum.values()) {
                    this.put(akDbType.getTypeId(), akDbType.getDruidDbType());
                }
            }
        };
        HEX_ARRAY = "0123456789abcdef".toCharArray();
    }
}

