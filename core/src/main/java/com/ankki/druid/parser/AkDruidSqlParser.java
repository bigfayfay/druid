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
import com.ankki.druid.parser.visitor.DbVisitorUtils;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

public class AkDruidSqlParser extends ParameterizedOutputVisitorUtils {

    private static SchemaStatVisitor visitor;
    private static DbType dbType;
    private static AkDruidResult akResult = new AkDruidResult();
    /**
     * AK数据库类型与Druid数据库类型映射关键 </br>
     */
    public static Map<Integer, DbType> akDruidDbTypeMap = new HashMap<Integer, DbType>() {{
        for (AkDbTypeEnum akDbType : AkDbTypeEnum.values()) {
            put(akDbType.getTypeId(), akDbType.getDruidDbType());
        }
    }};

    /**
     * 添加AK数据库类型与Druid数据库类型绑定关系,如果缓存中存在将会覆盖。</br>
     *
     * @param akDbTypeId    Ak数据库类型id </br>
     * @param druidTypeName Druid数据库类型名称 </br>
     */
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

    /**
     * 验证SQL注入
     * Druid已支持的类型请查看AkWallProviderEnum枚举类，不支持的类型默认使用MySqlWallProvider验证</br>
     *
     * @param sql        SQL语句 </br>
     * @param akDbTypeId AK数据库类型 </br>
     * @return 返回示例 </br>
     * 1、不支持该数据库类型，返回：NonSupport </br>
     * 2、不存在SQL注入，返回: Success </br>
     * 3、存在SQL注入，返回: Failure|错误内容或异常信息 </br>
     */
    public static String checkSqlInject(String sql, int akDbTypeId) {
        //清空之前缓存
        clear();
        dbType = getDbType(akDbTypeId);
        if (dbType == null) {
            return AkSqlParserStatusEnum.NonSupport.toString();
        }
        try {
            WallCheckResult wallCheckResult = AkWallProviderEnum.of(dbType).checkSqlInject(sql);
            if (wallCheckResult.getViolations().isEmpty()) {
                akResult.setSqlInject(Boolean.TRUE);
                akResult.setStatus(AkSqlParserStatusEnum.Success);
                return AkSqlParserStatusEnum.Success.toString();
            } else {
                akResult.setSqlInject(Boolean.FALSE);
                akResult.setStatus(AkSqlParserStatusEnum.Success);
                return AkSqlParserStatusEnum.Failure.toString(wallCheckResult.getViolations().toString());
            }
        } catch (Exception e) {
            akResult.setStatus(AkSqlParserStatusEnum.EXCEPTION);
            akResult.setExceptionMsg(e.getMessage());
            return AkSqlParserStatusEnum.Failure.toString(e.getMessage());
        }
    }

    /**
     * 获取移除绑定变量的SQL
     *
     * @param sql        SQL语句
     *                   示例 select id from user where name=:PNAME and age=#{age} and email='yy@126.com' </br>
     * @param akDbTypeId AK数据库类型</br>
     * @return 返回示例  </br>
     * 1、不支持该数据库类型，返回：NonSupport </br>
     * 2、解析成功，返回: Success|select id from user where name='' and age=''  and email='yy@126.com' </br>
     * 3、解析失败，返回: Failure|异常信息 </br>
     */
    public static String getRemoveBindingParameterSql(String sql, Integer akDbTypeId) {
        //清空之前缓存
        clear();
        dbType = getDbType(akDbTypeId);
        if (dbType == null) {
            akResult.setStatus(AkSqlParserStatusEnum.NonSupport);
            return AkSqlParserStatusEnum.NonSupport.toString();
        }
        StringBuilder out = new StringBuilder(sql.length());
        try {
            String backFillSql = sql;
            Map<String, Object> parameterMap = new HashMap<>();
            if (backFillSql.contains(";(")) {
                backFillSql = sql.substring(0, sql.lastIndexOf(";("));
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
            List<SQLStatement> stmtList = SQLUtils.parseStatements(backFillSql, dbType);
            DbVisitorUtils.setParameters(parameterMap);
            visitor = DbVisitorUtils.getDruidVisit(dbType);
            //替换绑定变量
            for (SQLStatement stmt : stmtList) {
                stmt.accept(visitor);
            }
            //输出SQL
            SQLASTOutputVisitor visitor = SQLUtils.createOutputVisitor(out, dbType);
            for (SQLStatement stmt : stmtList) {
                stmt.accept(visitor);
            }
            String removeBindingParameterSql = out.toString();
            akResult.setStatus(AkSqlParserStatusEnum.Success);
            akResult.setRemoveBindingParameterSql(removeBindingParameterSql);
            return AkSqlParserStatusEnum.Success.toString(removeBindingParameterSql);
        } catch (Exception e) {
            visitor = null;
            akResult.setStatus(AkSqlParserStatusEnum.EXCEPTION);
            akResult.setExceptionMsg(e.getMessage());
            return AkSqlParserStatusEnum.Failure.toString(e.getMessage());
        }
    }

    /**
     * 获取SQL模板(包含移除绑定变量)
     *
     * @param sql        SQL语句
     *                   示例 select id from user where name=:PNAME and age=#{age} and email='yy@126.com' </br>
     * @param akDbTypeId AK数据库类型 </br>
     * @return 返回示例 </br>
     * 1、不支持该数据库类型，返回：NonSupport </br>
     * 2、解析成功,模板语句转换大写，返回: Success|模板ID|SELECT ID FROM USER WHERE NAME=? AND AGE=?  AND EMAIL= ? </br>
     * 3、解析失败，               返回: Failure|错误message </br>
     */
    public static String getSqlTemplate(String sql, Integer akDbTypeId, Boolean hasBindingParameter) {
        //清空之前缓存
        clear();
        //处理绑定变量
        if (hasBindingParameter) {
            String result = getRemoveBindingParameterSql(sql, akDbTypeId);
            sql = akResult.getRemoveBindingParameterSql();
            if (StringUtils.isEmpty(akResult.getRemoveBindingParameterSql())) {
                return result;
            }
        }

        if (dbType == null) {
            akResult.setStatus(AkSqlParserStatusEnum.NonSupport);
            return AkSqlParserStatusEnum.NonSupport.toString();
        }

        try {
            String sqlTemplate = ParameterizedOutputVisitorUtils.parameterize(sql, dbType, akResult.getOutParameterValue());
            if (StringUtils.isEmpty(sqlTemplate)) {
                return AkSqlParserStatusEnum.Failure.toString("模板解析为空");
            }
//            String md5Hash = generateMD5(sqlTemplate);
            String md5Hash = "md5Hash";
            akResult.setStatus(AkSqlParserStatusEnum.Success);
            akResult.setTemplate(sqlTemplate);
            akResult.setMd5(md5Hash);
            return AkSqlParserStatusEnum.Success.toString(md5Hash + "|" + sqlTemplate);
        } catch (Exception e) {
            akResult.setStatus(AkSqlParserStatusEnum.EXCEPTION);
            akResult.setExceptionMsg(e.getMessage());
            return AkSqlParserStatusEnum.Failure.toString(e.getMessage());
        }
    }

    /**
     * 获取SQL语句中使用表名 </br>
     *
     * @return 返回示例 </br>
     * 1、不支持该数据库类型，返回：NonSupport </br>
     * 2、解析成功，[表名:操作]转换为大写，返回: Success|表名:操作|表名:操作|.... </br>
     * 3、解析失败，                   返回: Failure|错误内容 </br>
     */
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
                    String firstOprType = value != null && value.getStatType().size() > 0 ? value.getStatType().get(0) : "";
                    buf.append(key.toString()).append(":").append(firstOprType).append("|");
                });
                buf.deleteCharAt(buf.length() - 1);
            }
            return AkSqlParserStatusEnum.Success.toString(buf.toString());
        } catch (Exception e) {
            return AkSqlParserStatusEnum.Failure.toString(e.getMessage());
        }
    }


    /**
     * 获取SQL语句中使用字段 </br>
     *
     * @return 返回示例 </br>
     * 1、不支持该数据库类型，返回：NonSupport </br>
     * 2、解析成功，[表名:字段]转换为大写， 返回: Success|表名:字段|表名:字段|.... </br>
     * 3、解析失败，                    返回: Failure|错误信息 </br>
     */
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
                columns.forEach((row) -> {
                    buf.append(row.getName()).append("|");
                });
                buf.deleteCharAt(buf.length() - 1);
            }
            return AkSqlParserStatusEnum.Success.toString(buf.toString());
        } catch (Exception e) {
            return AkSqlParserStatusEnum.Failure.toString(e.getMessage());
        }
    }

    /**
     * 获取SQL语句中使用函数 </br>
     *
     * @return 返回示例 </br>
     * 1、不支持该数据库类型，返回：NonSupport </br>
     * 2、解析成功，返回: Success|函数(参数列表)|函数(参数列表)|.... </br>
     * 3、解析失败，返回: Failure|错误信息 </br>
     */
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
                functions.forEach((row) -> {
                    buf.append(row.toString()).append("|");
                });
                buf.deleteCharAt(buf.length() - 1);
            }
            return AkSqlParserStatusEnum.Success.toString(buf.toString());
        } catch (Exception e) {
            return AkSqlParserStatusEnum.Failure.toString(e.getMessage());
        }
    }


    /**
     * 获取SQL语句中查询条件 </br>
     *
     * @return 返回示例 </br>
     * 1、不支持该数据库类型，返回：NonSupport </br>
     * 2、解析成功，返回: Success|user.id=|age.id=|.... </br>
     * 3、解析失败，返回: Failure|错误信息</br>
     */
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
                conditions.forEach((row) -> {
                    buf.append(row.toString()).append("|");
                });
                buf.deleteCharAt(buf.length() - 1);
            }
            return AkSqlParserStatusEnum.Success.toString(buf.toString());
        } catch (Exception e) {
            return AkSqlParserStatusEnum.Failure.toString(e.getMessage());
        }
    }


    /**
     * 获取SQL语句中参数值 </br>
     *
     * @return 返回示例 </br>
     * 1、不支持该数据库类型，返回：NonSupport </br>
     * 2、解析成功，返回: Success|张三,李四</br>
     * 3、解析失败，返回: Failure|错误信息</br>
     */
    public static String getParameterValues() {
        if (dbType == null) {
            return AkSqlParserStatusEnum.NonSupport.toString();
        }
        if (visitor == null) {
            return AkSqlParserStatusEnum.Failure.toString();
        }
        try {
            return AkSqlParserStatusEnum.Success.toString(akResult.getOutParameterValue().toString());
        } catch (Exception e) {
            return AkSqlParserStatusEnum.Failure.toString(e.getMessage());
        }
    }


    public static AkDruidResult getResolver(String sql, int akDbType) {
        //模板解析
        getSqlTemplate(sql, akDbType, true);
        if (dbType == null) {
            akResult.setStatus(AkSqlParserStatusEnum.NonSupport);
            return akResult;
        }
        if (visitor == null) {
            akResult.setStatus(AkSqlParserStatusEnum.Failure);
            return akResult;
        }
        //获取表名
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
        //获取字段名
        Collection<TableStat.Column> columns = visitor.getColumns();
        if (columns != null && !columns.isEmpty()) {
            columns.forEach((row) -> {
                akResult.getFieldName().add(row.getName());
            });
        }
        //获取查询条件
        List<TableStat.Condition> conditions = visitor.getConditions();
        if (conditions != null && !conditions.isEmpty()) {
            conditions.forEach((row) -> {
                akResult.getConditions().add(row.toString());
            });
        }
        //获取方法名
        List<SQLMethodInvokeExpr> functions = visitor.getFunctions();
        if (functions != null && !functions.isEmpty()) {
            functions.forEach((row) -> {
                akResult.getFunctions().add(row.toString());
            });
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

    public static String generateMD5(String input) throws NoSuchAlgorithmException {
        StringBuilder hexString = new StringBuilder();
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(input.getBytes());
            byte[] digest = md.digest();
            for (byte b : digest) {
                hexString.append(String.format("%02x", b));
            }
        } catch (NoSuchAlgorithmException e) {
            throw new NoSuchAlgorithmException("MD5生成失败");
        }
        return hexString.toString();
    }
}
