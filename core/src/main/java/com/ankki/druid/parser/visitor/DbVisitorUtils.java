package com.ankki.druid.parser.visitor;

import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.SQLExpr;
import com.alibaba.druid.sql.ast.expr.SQLExprUtils;
import com.alibaba.druid.sql.ast.expr.SQLVariantRefExpr;
import com.alibaba.druid.sql.visitor.SchemaStatVisitor;

import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

public class DbVisitorUtils {
    private static Map<String, Object> parameters = new HashMap<>();
    private static TimeZone timeZone;
    private static Map<DbType, Class<? extends SchemaStatVisitor>> visitorMap = new HashMap<DbType, Class<? extends SchemaStatVisitor>>() {{
        put(DbType.h2, AkH2SchemaStatVisitor.class);
        put(DbType.mysql, AkMySqlSchemaStatVisitor.class);
        put(DbType.oracle, AkOracleSchemaStatVisitor.class);
        put(DbType.sqlserver, AkOracleSchemaStatVisitor.class);
        put(DbType.other, AkOracleSchemaStatVisitor.class);
    }};

    public static SchemaStatVisitor getDruidVisit(DbType dbType) {
        if (dbType == null) {
            return new AkSchemaStatVisitor();
        }
        Class<? extends SchemaStatVisitor> visitorClass = visitorMap.get(dbType);
        if (visitorClass == null) {
            return new AkSchemaStatVisitor();
        }
        try {
            return visitorClass.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            return new AkSchemaStatVisitor();
        }
    }


    /**
     * 绑定变量回填
     *
     * @param x
     * @return
     */
    public static boolean visit(SQLVariantRefExpr x) {
        String name = x.getName();
        if (name.length() > 3 && name.startsWith("#")) {
            char c0 = name.charAt(0);
            char c1 = name.charAt(1);
            char c1x = name.charAt(name.length() - 1);

            if (c0 == '#' && c1 == '{' && c1x == '}') {
                String key = name.substring(2, name.length() - 1);
                Object value = parameters.get(key);
                SQLExpr expr = SQLExprUtils.fromJavaObject(value, timeZone);
                SQLUtils.replaceInParent(x, expr);
            }
        } else if (name.length() >= 2 && name.startsWith(":")) {
            char c0 = name.charAt(0);
            if (c0 == ':') {
                String key = name.substring(1);
                Object value = parameters.get(key);
                SQLExpr expr = SQLExprUtils.fromJavaObject(value, timeZone);
                SQLUtils.replaceInParent(x, expr);
            }
        }
        return true;
    }

    public static void setParameters(Map<String, Object> parameters) {
        DbVisitorUtils.parameters = parameters;
    }
}
