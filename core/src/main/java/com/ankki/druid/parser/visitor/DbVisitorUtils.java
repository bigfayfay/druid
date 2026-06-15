/*
 * Decompiled with CFR 0.152.
 */
package com.ankki.druid.parser.visitor;

import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.SQLExpr;
import com.alibaba.druid.sql.ast.expr.SQLExprUtils;
import com.alibaba.druid.sql.ast.expr.SQLVariantRefExpr;
import com.alibaba.druid.sql.visitor.SchemaStatVisitor;
import com.ankki.druid.parser.visitor.AkH2SchemaStatVisitor;
import com.ankki.druid.parser.visitor.AkMySqlSchemaStatVisitor;
import com.ankki.druid.parser.visitor.AkOracleSchemaStatVisitor;
import com.ankki.druid.parser.visitor.AkSchemaStatVisitor;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

public class DbVisitorUtils {
    private static final char[] BOUND_PREFIX_ARR = System.getProperty("boundPrefix", ":@").toCharArray();
    private static Map<String, Object> parameters = new HashMap<String, Object>();
    private static TimeZone timeZone;
    private static Map<DbType, Class<? extends SchemaStatVisitor>> visitorMap;
    public static final String HASH = "#";
    public static final String QUESTION_MARK = "?";

    public static SchemaStatVisitor getDruidVisit(DbType dbType) {
        if (dbType == null) {
            return new AkSchemaStatVisitor();
        }
        Class<? extends SchemaStatVisitor> visitorClass = visitorMap.get((Object)dbType);
        if (visitorClass == null) {
            return new AkSchemaStatVisitor();
        }
        try {
            return visitorClass.newInstance();
        }
        catch (IllegalAccessException | InstantiationException e) {
            return new AkSchemaStatVisitor();
        }
    }

    public static boolean visit(SQLVariantRefExpr x) {
        String name = x.getName();
        int length = name.length();
        if (length < 2) {
            return true;
        }
        char c0 = name.charAt(0);
        if (length > 3 && name.startsWith(HASH)) {
            char c1 = name.charAt(1);
            char c1x = name.charAt(length - 1);
            if (c0 == '#' && c1 == '{' && c1x == '}') {
                String key = name.substring(2, length - 1);
                Object value = parameters.get(key);
                if (value == null) {
                    value = QUESTION_MARK;
                }
                SQLExpr expr = SQLExprUtils.fromJavaObject(value, timeZone);
                SQLUtils.replaceInParent(x, expr);
            }
        } else if (DbVisitorUtils.boundContain(c0)) {
            String key = name.substring(1);
            Object value = parameters.get(key);
            if (value == null) {
                value = QUESTION_MARK;
            }
            SQLExpr expr = SQLExprUtils.fromJavaObject(value, timeZone);
            SQLUtils.replaceInParent(x, expr);
        }
        return true;
    }

    public static void setParameters(Map<String, Object> parameters) {
        DbVisitorUtils.parameters = parameters;
    }

    public static boolean boundContain(char pre) {
        for (char bound : BOUND_PREFIX_ARR) {
            if (pre != bound) continue;
            return true;
        }
        return false;
    }

    static {
        visitorMap = new HashMap<DbType, Class<? extends SchemaStatVisitor>>(){
            {
                this.put(DbType.h2, AkH2SchemaStatVisitor.class);
                this.put(DbType.mysql, AkMySqlSchemaStatVisitor.class);
                this.put(DbType.oracle, AkOracleSchemaStatVisitor.class);
                this.put(DbType.sqlserver, AkOracleSchemaStatVisitor.class);
                this.put(DbType.other, AkOracleSchemaStatVisitor.class);
            }
        };
    }
}

