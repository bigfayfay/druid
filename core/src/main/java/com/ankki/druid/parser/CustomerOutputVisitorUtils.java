package com.ankki.druid.parser;

import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.SQLStatement;
import com.alibaba.druid.sql.visitor.ParameterizedOutputVisitorUtils;
import com.alibaba.druid.sql.visitor.SQLASTOutputVisitor;
import com.alibaba.druid.util.StringUtils;
import com.ankki.druid.parser.visitor.AkSchemaStatVisitor;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

public class CustomerOutputVisitorUtils {
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
        DbType dbType = getDbType(akDbTypeId);
        if (dbType == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(sql.length());
        try {
            String backFillSql = sql;
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
            List<SQLStatement> stmtList = SQLUtils.parseStatements(backFillSql, dbType);

            AkSchemaStatVisitor akVisitor = new AkSchemaStatVisitor(dbType);
            //替换绑定变量
            for (SQLStatement stmt : stmtList) {
                stmt.accept(akVisitor);
            }
            //输出SQL
            SQLASTOutputVisitor visitor = SQLUtils.createOutputVisitor(out, dbType);
            for (SQLStatement stmt : stmtList) {
                stmt.accept(visitor);
            }
            return out.toString();
        } catch (Exception e) {
        }
        return "";
    }

    private static DbType getDbType(Integer akDbTypeId) {
        return AkDruidSqlParser.akDruidDbTypeMap.get(akDbTypeId);
    }


    public static String removeBindingByIndexOf(String sql) {
        if (StringUtils.isEmpty(sql)) {
            return null;
        }

        int lastIndexOfSemicolon = sql.lastIndexOf(";");
        int lastIndexOfSemicolonLeftParenthesis = sql.lastIndexOf(";(");

        if (lastIndexOfSemicolonLeftParenthesis > 0) {
            return sql.substring(0, lastIndexOfSemicolonLeftParenthesis);
        } else if (lastIndexOfSemicolon > 0) {
            return sql.substring(0, lastIndexOfSemicolon);
        } else {
            return sql;
        }
    }

    private static final String SLASH = "|";

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
    public static String getSqlTemplate(String sql, Integer akDbTypeId) {
        //处理绑定变量
        String removeParameterSql = removeBindingByIndexOf(sql);
        if (StringUtils.isEmpty(removeParameterSql)) {
            return AkSqlParserStatusEnum.Failure.toString();
        }
        DbType dbType = getDbType(akDbTypeId);
        if (dbType == null) {
            return AkSqlParserStatusEnum.NonSupport.toString();
        }
        try {
            String sqlTemplate = ParameterizedOutputVisitorUtils.parameterize(removeParameterSql, dbType);
            if (StringUtils.isEmpty(sqlTemplate)) {
                return AkSqlParserStatusEnum.Failure.toString("模板解析为空");
            }

            String md5Hash = AkDruidSqlParser.generateMD5(sqlTemplate);
            // 50 > 39 = 7(success) +  32(md5)
            StringBuilder stringBuilder = new StringBuilder(50 + sqlTemplate.length());
            stringBuilder.append(AkSqlParserStatusEnum.Success.name())
                    .append(SLASH)
                    .append(md5Hash)
                    .append(SLASH)
                    .append(sqlTemplate);
            return stringBuilder.toString();
        } catch (Exception e) {
            return new StringBuilder()
                    .append(AkSqlParserStatusEnum.Failure.name())
                    .append(SLASH)
                    .append(e.getMessage())
                    .toString();
        }
    }


    public static void main(String[] args) {
        LocalDateTime start = LocalDateTime.now();

        for (String sqlBound : sqlListBind()) {
            System.out.println(getSqlTemplate(sqlBound, 2));
        }

        System.out.println("===============");

        for (String sqlBound : sqlList()) {
            System.out.println(getSqlTemplate(sqlBound, 2));
        }
    }

    private static String originStr(String sqlTemplate, String md5Hash) {
        return AkSqlParserStatusEnum.Success.toString(md5Hash + "|" + sqlTemplate);
    }

    private static String stringBuilder(String sqlTemplate, String md5Hash) {
        StringBuilder stringBuilder = new StringBuilder(50 + sqlTemplate.length());
        stringBuilder.append(AkSqlParserStatusEnum.Success.name())
                .append(SLASH)
                .append(md5Hash)
                .append(SLASH)
                .append(sqlTemplate);
        return stringBuilder.toString();
    }



    public static List<String> sqlListBind(){
        List<String> sqlList = new ArrayList<>();
        sqlList.add("SELECT c.* FROM ALL_TAB_COLS cWHERE c.OWNER=:1  AND c.TABLE_NAME=:2 ;(1 = CESHI,2 = AB_phone)");
        sqlList.add("SELECT  i.OWNER,i.INDEX_NAME,i.INDEX_TYPE,i.TABLE_OWNER,i.TABLE_NAME,i.UNIQUENESS,i.TABLESPACE_NAME,i.STATUS,i.NUM_ROWS,i.SAMPLE_SIZE,ic.COLUMN_NAME,ic.COLUMN_POSITION,ic.COLUMN_LENGTH,ic.DESCEND,iex.COLUMN_EXPRESSIONFROM ALL_INDEXES iJOIN ALL_IND_COLUMNS ic ON ic.INDEX_OWNER=i.OWNER AND ic.INDEX_NAME=i.INDEX_NAME LEFT OUTER JOIN ALL_IND_EXPRESSIONS iex ON iex.INDEX_OWNER=i.OWNER AND iex.INDEX_NAME=i.INDEX_NAME AND iex.COLUMN_POSITION=ic.COLUMN_POSITIONWHERE i.TABLE_OWNER=:1  AND i.TABLE_NAME=:2 ORDER BY i.INDEX_NAME,ic.COLUMN_POSITION;(1 = CESHI,2 = AB_phone)");

        sqlList.add("SELECT COLUMN_NAME,COMMENTS FROM ALL_COL_COMMENTS cc WHERE CC.OWNER=:1  AND cc.TABLE_NAME=:2 ;(1 = CESHI,2 = AB_phone)");
        sqlList.add("UPDATE CESHI.AB_Aaddress xSET x.id=:1 WHERE x.ROWID=:2 ;(1 = 12,2 = '',)");
        sqlList.add("SELECT *FROM ALL_TRIGGERS WHERE TABLE_OWNER=:1  AND TABLE_NAME=:2 ORDER BY TRIGGER_NAME;(1 = CESHI,2 = AB_Aaddress)");
        return sqlList;
    }

    public static List<String> sqlList(){
        List<String> sqlList = new ArrayList<>();
        sqlList.add("SELECT c.* FROM ALL_TAB_COLS cWHERE c.OWNER='CESHI'  AND c.TABLE_NAME='AB_phone' ");
        sqlList.add("SELECT  i.OWNER,i.INDEX_NAME,i.INDEX_TYPE,i.TABLE_OWNER,i.TABLE_NAME,i.UNIQUENESS,i.TABLESPACE_NAME,i.STATUS,i.NUM_ROWS,i.SAMPLE_SIZE,ic.COLUMN_NAME,ic.COLUMN_POSITION,ic.COLUMN_LENGTH,ic.DESCEND,iex.COLUMN_EXPRESSIONFROM ALL_INDEXES iJOIN ALL_IND_COLUMNS ic ON ic.INDEX_OWNER=i.OWNER AND ic.INDEX_NAME=i.INDEX_NAME LEFT OUTER JOIN ALL_IND_EXPRESSIONS iex ON iex.INDEX_OWNER=i.OWNER AND iex.INDEX_NAME=i.INDEX_NAME AND iex.COLUMN_POSITION=ic.COLUMN_POSITIONWHERE i.TABLE_OWNER='CESHI'  AND i.TABLE_NAME='AB_phone' ORDER BY i.INDEX_NAME,ic.COLUMN_POSITION");

        sqlList.add("SELECT COLUMN_NAME,COMMENTS FROM ALL_COL_COMMENTS cc WHERE CC.OWNER='CESHI'  AND cc.TABLE_NAME='AB_phone' ");
        sqlList.add("UPDATE CESHI.AB_Aaddress xSET x.id=12 WHERE x.ROWID='' ");
        sqlList.add("SELECT *FROM ALL_TRIGGERS WHERE TABLE_OWNER='CESHI'  AND TABLE_NAME='AB_Aaddress' ORDER BY TRIGGER_NAME");
        return sqlList;
    }

}
