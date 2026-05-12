package com.ankki.druid.parser.v2;

import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.SQLStatement;
import com.alibaba.druid.sql.visitor.ParameterizedOutputVisitorUtils;
import com.alibaba.druid.sql.visitor.SQLASTOutputVisitor;
import com.alibaba.druid.sql.visitor.SQLASTVisitor;
import com.alibaba.druid.util.StringUtils;
import com.ankki.druid.parser.AkDruidSqlParser;
import com.ankki.druid.parser.AkSqlParserStatusEnum;
import com.ankki.druid.parser.bind.ParameterValuesFormatter;
import com.ankki.druid.parser.template.AkTemplateResult;
import com.ankki.druid.parser.visitor.AkSchemaStatVisitor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class CustomerOutputVisitorUtils {
  private static final String BIND_APPEND = ";(";
  
  private static final String OPERATION_TYPE_INSERT = "INSERT";
  
  private static final String OPERATION_TYPE_SELECT = "SELECT";
  
  private static final char[] OPERATION_TYPE_INSERT_ARRAY = "INSERT".toCharArray();
  
  private static final Map<String, char[]> OPER_MAP = (Map)new HashMap<>();
  
  static {
    OPER_MAP.put("INSERT", "INSERT".toCharArray());
    OPER_MAP.put("SELECT", "SELECT".toCharArray());
  }
  
  public static String getRemoveBindingParameterSql(String sql, DbType dbType) {
    int lastIndexOfBindAppend = sql.lastIndexOf(";(");
    if (lastIndexOfBindAppend <= -1)
      return sql; 
    StringBuilder out = new StringBuilder(sql.length());
    String backFillSql = sql.substring(0, lastIndexOfBindAppend + 1);
    List<SQLStatement> stmtList = SQLUtils.parseStatements(backFillSql, dbType);
    AkSchemaStatVisitor akVisitor = new AkSchemaStatVisitor(dbType);
    for (SQLStatement stmt : stmtList)
      stmt.accept((SQLASTVisitor)akVisitor); 
    SQLASTOutputVisitor visitor = SQLUtils.createOutputVisitor(out, dbType);
    for (SQLStatement stmt : stmtList)
      stmt.accept((SQLASTVisitor)visitor); 
    return out.toString();
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
          if (param.length == 2 && param[0] != null)
            parameterMap.put(param[0].trim(), param[1]); 
        } 
      } 
    } 
    return backFillSql;
  }
  
  private static DbType getDbType(Integer akDbTypeId) {
    return AkDruidSqlParser.akDruidDbTypeMap.get(akDbTypeId);
  }
  
  public static String getSqlTemplate(String sql, Integer akDbTypeId) {
    AkTemplateResult sqlTemplate = getSqlTemplate(sql, akDbTypeId, false);
    AkSqlParserStatusEnum status = sqlTemplate.getStatus();
    if (AkSqlParserStatusEnum.Success != status)
      return status.toString(sqlTemplate.getExceptionMsg()); 
    return status.toString(sqlTemplate.getMd5() + "|" + sqlTemplate.getTemplate());
  }
  
  public static String[] getSqlTemplate_v2(String sql, Integer akDbTypeId) {
    String[] result = new String[4];
    AkTemplateResult templateResult = getSqlTemplate(sql, akDbTypeId, true);
    result[0] = templateResult.getStatus().name();
    result[1] = templateResult.getMd5();
    result[2] = templateResult.getTemplate();
    result[3] = ParameterValuesFormatter.sqlBind(templateResult.getParameterValues());
    return result;
  }
  
  public static AkTemplateResult getSqlTemplate(String sql, Integer akDbTypeId, boolean parameterValues) {
    AkTemplateResult result = new AkTemplateResult();
    DbType dbType = getDbType(akDbTypeId);
    if (dbType == null) {
      result.setStatus(AkSqlParserStatusEnum.NonSupport);
      return result;
    } 
    result.setDbType(dbType);
    result.setAkDbTypeId(akDbTypeId);
    if (parameterValues)
      result.setParameterValues(new ArrayList()); 
    try {
      String removeParameterSql = getRemoveBindingParameterSql(sql, dbType);
      if (StringUtils.isEmpty(removeParameterSql)) {
        result.setStatus(AkSqlParserStatusEnum.Failure);
        result.setExceptionMsg("binding parameter fail");
        return result;
      } 
      String sqlTemplate = processSqlTemplate(removeParameterSql, result);
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
    } catch (Exception e) {
      result.setStatus(AkSqlParserStatusEnum.Failure);
      result.setExceptionMsg(e.getMessage());
      return result;
    } 
  }
  
  private static String processSqlTemplate(String removeParameterSql, AkTemplateResult result) throws Exception {
    DbType dbType = result.getDbType();

    return ParameterizedOutputVisitorUtils.parameterize(removeParameterSql, dbType, result.getParameterValues());
  }
  
  private static String getOperationType(String sql, Integer akDbTypeId) {
    if (StringUtils.isEmpty(sql))
      return ""; 
    DbType dbType = getDbType(akDbTypeId);
    if (dbType == null)
      return ""; 
    try {
      List<SQLStatement> statements = SQLUtils.parseStatements(sql, dbType);
      if (Objects.isNull(statements) || statements.isEmpty())
        return ""; 
      SQLStatement stmt = statements.get(0);
      if (stmt instanceof com.alibaba.druid.sql.ast.statement.SQLSelectStatement)
        return "SELECT"; 
      if (stmt instanceof com.alibaba.druid.sql.ast.statement.SQLInsertStatement)
        return "INSERT"; 
      if (stmt instanceof com.alibaba.druid.sql.ast.statement.SQLUpdateStatement)
        return "UPDATE"; 
      if (stmt instanceof com.alibaba.druid.sql.ast.statement.SQLDeleteStatement)
        return "DELETE"; 
      String className = stmt.getClass().getSimpleName();
      if (className.endsWith("Statement")) {
        String typeName = className.substring(0, className.length() - "Statement".length());
        if (typeName.startsWith("SQL"))
          typeName = typeName.substring(3); 
        if (typeName.startsWith("Create"))
          return "CREATE"; 
        if (typeName.startsWith("Drop"))
          return "DROP"; 
        if (typeName.startsWith("Alter"))
          return "ALTER"; 
        if (typeName.startsWith("Truncate"))
          return "TRUNCATE"; 
        if (typeName.startsWith("Replace"))
          return "REPLACE"; 
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
