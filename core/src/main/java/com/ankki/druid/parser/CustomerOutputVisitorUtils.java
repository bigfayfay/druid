/*
 * Decompiled with CFR 0.152.
 */
package com.ankki.druid.parser;

import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.SQLStatement;
import com.alibaba.druid.sql.ast.statement.SQLDeleteStatement;
import com.alibaba.druid.sql.ast.statement.SQLInsertStatement;
import com.alibaba.druid.sql.ast.statement.SQLSelectStatement;
import com.alibaba.druid.sql.ast.statement.SQLUpdateStatement;
import com.alibaba.druid.sql.visitor.ParameterizedOutputVisitorUtils;
import com.alibaba.druid.sql.visitor.SQLASTOutputVisitor;
import com.alibaba.druid.util.StringUtils;
import com.ankki.druid.parser.config.VmOptions;
import com.ankki.druid.parser.oracle.InsertTemplateStandardizer;
import com.ankki.druid.parser.oracle.SqlTemplateStandardizer;
import com.ankki.druid.parser.template.AkLightweightCachedDruidSqlMonitor;
import com.ankki.druid.parser.template.AkLightweightCachedOutputVisitorUtils;
import com.ankki.druid.parser.template.AkOutputVisitorUtils;
import com.ankki.druid.parser.template.AkSqlTemplateMonitor;
import com.ankki.druid.parser.visitor.AkSchemaStatVisitor;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class CustomerOutputVisitorUtils {
    private static final String BIND_APPEND = ";(";
    private static final String OPERATION_TYPE_INSERT = "INSERT";
    private static final String OPERATION_TYPE_SELECT = "SELECT";
    private static final AtomicBoolean MONITOR_STARTED = new AtomicBoolean(false);
    private static final String MONITOR_PID;
    private static final String MONITOR_DIR;
    private static final int MONITOR_INTERVAL;
    public static final AtomicLong TOTAL_CALLS;

    public static String getRemoveBindingParameterSql(String sql, Integer akDbTypeId) {
        DbType dbType = CustomerOutputVisitorUtils.getDbType(akDbTypeId);
        if (dbType == null) {
            return "";
        }
        try {
            int lastIndexOfBindAppend = sql.lastIndexOf(BIND_APPEND);
            if (lastIndexOfBindAppend <= -1) {
                return sql;
            }
            StringBuilder out = new StringBuilder(sql.length());
            String backFillSql = sql.substring(0, lastIndexOfBindAppend + 1);
            List<SQLStatement> stmtList = SQLUtils.parseStatements(backFillSql, dbType);
            AkSchemaStatVisitor akVisitor = new AkSchemaStatVisitor(dbType);
            for (SQLStatement stmt : stmtList) {
                stmt.accept(akVisitor);
            }
            SQLASTOutputVisitor visitor = SQLUtils.createOutputVisitor(out, dbType);
            for (SQLStatement stmt : stmtList) {
                stmt.accept(visitor);
            }
            return out.toString();
        } catch (Exception e) {
            return AkSqlParserStatusEnum.Failure.toString(e.getMessage());
        }
    }

    private static String sqlBindParamMap(String sql, String backFillSql) {
        HashMap<String, String> parameterMap = new HashMap<String, String>();
        if (backFillSql.contains(BIND_APPEND)) {
            backFillSql = sql.substring(0, sql.lastIndexOf(BIND_APPEND) + 1);
            String parameterString = sql.substring(sql.lastIndexOf(BIND_APPEND) + 2, sql.length() - 1);
            if (!StringUtils.isEmpty(backFillSql) && !StringUtils.isEmpty(parameterString)) {
                List<String> parameters = Arrays.asList(parameterString.split(","));
                for (String parameter : parameters) {
                    String[] param = parameter.split("=");
                    if (param.length != 2 || param[0] == null) continue;
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
        String removeParameterSql = CustomerOutputVisitorUtils.getRemoveBindingParameterSql(sql, akDbTypeId);
        if (StringUtils.isEmpty(removeParameterSql)) {
            return AkSqlParserStatusEnum.Failure.toString("\u6a21\u677f\u89e3\u6790\u5931\u8d25");
        }
        if (removeParameterSql.contains(AkSqlParserStatusEnum.Failure.name() + "|")) {
            return removeParameterSql;
        }
        DbType dbType = CustomerOutputVisitorUtils.getDbType(akDbTypeId);
        if (dbType == null) {
            return AkSqlParserStatusEnum.NonSupport.toString();
        }
        try {
            String sqlTemplate = CustomerOutputVisitorUtils.processSqlTemplate(removeParameterSql, dbType, akDbTypeId);
            if (StringUtils.isEmpty(sqlTemplate)) {
                return AkSqlParserStatusEnum.Failure.toString("\u6a21\u677f\u89e3\u6790\u4e3a\u7a7a");
            }
            String md5Hash = AkDruidSqlParser.generateMD5(sqlTemplate);
            return AkSqlParserStatusEnum.Success.toString(md5Hash + "|" + sqlTemplate);
        } catch (Exception e) {
            return AkSqlParserStatusEnum.Failure.toString(e.getMessage());
        }
    }

    static final boolean CACHE_USE = VmOptions.getBoolean(VmOptions.CACHE_USE);

    public static String[] getSqlTemplate_v2(String sql, Integer akDbTypeId) {
        // 快速路径：监控禁用时零开销直接执行
        if (!AkSqlTemplateMonitor.MONITOR_ENABLED) {
            if (CACHE_USE) {
                return AkLightweightCachedOutputVisitorUtils.getSqlTemplate_v2(sql, akDbTypeId);
            } else {
                return AkOutputVisitorUtils.getSqlTemplate_v2(sql, akDbTypeId);
            }
        }

        // 监控启用路径
        AkSqlTemplateMonitor.ensureMonitorStarted();
        long startTimeNs = AkSqlTemplateMonitor.startTiming();
        AkSqlParserStatusEnum status = null;
        try {
            String[] result;
            if (CACHE_USE) {
                result = AkLightweightCachedOutputVisitorUtils.getSqlTemplate_v2(sql, akDbTypeId);
            } else {
                result = AkOutputVisitorUtils.getSqlTemplate_v2(sql, akDbTypeId);
            }
            status = AkSqlParserStatusEnum.fastValueOf(result[0]);
            return result;
        } finally {
            AkSqlTemplateMonitor.endTiming(startTimeNs, status, sql.length());
        }
    }

    private static String processSqlTemplate(String removeParameterSql, DbType dbType, Integer akDbTypeId) throws Exception {
        if (dbType == DbType.oracle) {
            String operationType = CustomerOutputVisitorUtils.getOperationType(removeParameterSql, akDbTypeId);
            if (OPERATION_TYPE_INSERT.equals(operationType)) {
                return InsertTemplateStandardizer.extractInsertTemplate(removeParameterSql, dbType);
            }
            if (OPERATION_TYPE_SELECT.equals(operationType)) {
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
        DbType dbType = CustomerOutputVisitorUtils.getDbType(akDbTypeId);
        if (dbType == null) {
            return "";
        }
        try {
            List<SQLStatement> statements = SQLUtils.parseStatements(sql, dbType);
            if (Objects.isNull(statements) || statements.isEmpty()) {
                return "";
            }
            SQLStatement stmt = statements.get(0);
            if (stmt instanceof SQLSelectStatement) {
                return OPERATION_TYPE_SELECT;
            }
            if (stmt instanceof SQLInsertStatement) {
                return OPERATION_TYPE_INSERT;
            }
            if (stmt instanceof SQLUpdateStatement) {
                return "UPDATE";
            }
            if (stmt instanceof SQLDeleteStatement) {
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

    private static String monitorFilePath() {
        return MONITOR_DIR + "/druid_monitor_" + MONITOR_PID + "_" + new SimpleDateFormat("yyyyMMdd").format(new Date()) + ".log";
    }

    private static void startMonitor() {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "druid-monitor");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(() -> {
            try (PrintWriter pw = new PrintWriter(new FileWriter(CustomerOutputVisitorUtils.monitorFilePath(), true));) {
                AkLightweightCachedDruidSqlMonitor.printAll(pw);
            } catch (Exception exception) {
                // empty catch block
            }
        }, MONITOR_INTERVAL, MONITOR_INTERVAL, TimeUnit.SECONDS);
        try (PrintWriter pw = new PrintWriter(new FileWriter(CustomerOutputVisitorUtils.monitorFilePath(), true));) {
            AkLightweightCachedDruidSqlMonitor.printAll(pw);
        } catch (Exception exception) {
            // empty catch block
        }
    }

    public static void main(String[] args) {
        String selectSql = "SELECT * FROM dual";
        System.out.println(CustomerOutputVisitorUtils.getSqlTemplate(selectSql, 3));
        System.out.println(AkDruidSqlParser.getSqlTemplate(selectSql, 3, true));
    }

    static {
        TOTAL_CALLS = new AtomicLong(0L);
        String pid = "unknown";
        try {
            String name = ManagementFactory.getRuntimeMXBean().getName();
            int atIdx = name.indexOf(64);
            if (atIdx > 0) {
                pid = name.substring(0, atIdx);
            }
        } catch (Exception name) {
            // empty catch block
        }
        MONITOR_PID = pid;
        String dir = System.getProperty("druid.monitor.dir");
        MONITOR_DIR = dir != null && !dir.isEmpty() ? dir : "/tmp/druid_monitor";
        int interval = 60;
        try {
            interval = Integer.parseInt(System.getProperty("druid.monitor.interval", "60"));
        } catch (NumberFormatException numberFormatException) {
            // empty catch block
        }
        MONITOR_INTERVAL = Math.max(10, interval);
    }
}

