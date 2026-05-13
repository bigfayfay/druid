package com.ankki.druid.parser.oracle;

import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.SQLExpr;
import com.alibaba.druid.sql.ast.SQLObject;
import com.alibaba.druid.sql.ast.SQLStatement;
import com.alibaba.druid.sql.ast.expr.SQLMethodInvokeExpr;
import com.alibaba.druid.sql.ast.expr.SQLNullExpr;
import com.alibaba.druid.sql.ast.expr.SQLVariantRefExpr;
import com.alibaba.druid.sql.ast.statement.SQLInsertStatement;
import com.alibaba.druid.sql.ast.statement.SQLSelect;
import com.alibaba.druid.sql.ast.statement.SQLSelectQuery;
import com.alibaba.druid.sql.ast.statement.SQLSelectQueryBlock;
import com.alibaba.druid.sql.ast.statement.SQLUnionQuery;
import com.alibaba.druid.sql.dialect.oracle.visitor.OracleASTVisitorAdapter;
import com.alibaba.druid.sql.visitor.SQLASTVisitor;
import com.alibaba.druid.sql.visitor.VisitorFeature;
import com.alibaba.druid.util.JdbcConstants;
import java.util.List;

public class InsertTemplateStandardizer {
    public static void main(String[] args) {
        String dirtySql = "INSERT INTO doc_order_temp_new (PATIENT_ID, VISIT_ID, ORDER_CLASS, ORDER_NO, ORDER_SUB_NO, ORDER_TEXT, ADMINISTRATION, ORDER_CODE, order_status, REPEAT_INDICATOR, ENTER_DATE_TIME, START_STOP_INDICATOR, DOSAGE, DOSAGE_UNITS, DURATION, DURATION_UNITS, FREQUENCY, FREQ_COUNTER, FREQ_INTERVAL, FREQ_INTERVAL_UNIT, FREQ_DETAIL, ORDERING_DEPT, DOCTOR, NURSE, PROCESSING_DATE_TIME, BILLING_ATTR, ORDER_PRINT_INDICATOR, RELATED_ORDER_NO, RELATED_ORDER_SUB_NO, DRUG_BILLING_ATTR, START_DATE_TIME, RELATIVE_NO, ORDER_TYPE_NAME, ORDER_PERFORM_STATUS, fallback_order, query_order_no, query_order_sub_no, YB_FLAG) SELECT 'Val_PATIENT_ID', 'Val_VISIT_ID', 'Val_ORDER_CLASS', 'Val_ORDER_NO', 'Val_ORDER_SUB_NO', 'Val_ORDER_TEXT', NULL, 'Val_ORDER_CODE', 'Val_order_status', 'Val_REPEAT_INDICATOR', to_date('2024-01-01 12:00:00', 'yyyy-mm-dd hh24:mi:ss'), 'Val_START_STOP_INDICATOR', NULL, NULL, NULL, NULL, NULL, 'Val_FREQ_COUNTER', 'Val_FREQ_INTERVAL', 'Val_FREQ_INTERVAL_UNIT', NULL, 'Val_ORDERING_DEPT', 'Val_DOCTOR', NULL, to_date(NULL, 'yyyy-mm-dd hh24:mi:ss'), NULL, 'Val_ORDER_PRINT_INDICATOR', NULL, NULL, 'Val_DRUG_BILLING_ATTR', to_date('2024-01-01 12:00:00', 'yyyy-mm-dd hh24:mi:ss'), NULL, NULL, NULL, NULL, 'Val_query_order_no', 'Val_query_order_sub_no', NULL FROM dual UNION ALL SELECT 'Val_PATIENT_ID', 'Val_VISIT_ID', 'Val_ORDER_CLASS', 'Val_ORDER_NO', 'Val_ORDER_SUB_NO', 'Val_ORDER_TEXT', NULL, 'Val_ORDER_CODE', 'Val_order_status', 'Val_REPEAT_INDICATOR', to_date('2024-01-01 12:00:00', 'yyyy-mm-dd hh24:mi:ss'), 'Val_START_STOP_INDICATOR', NULL, NULL, NULL, NULL, NULL, 'Val_FREQ_COUNTER', 'Val_FREQ_INTERVAL', 'Val_FREQ_INTERVAL_UNIT', NULL, 'Val_ORDERING_DEPT', 'Val_DOCTOR', NULL, to_date(NULL, 'yyyy-mm-dd hh24:mi:ss'), NULL, 'Val_ORDER_PRINT_INDICATOR', NULL, NULL, 'Val_DRUG_BILLING_ATTR', to_date('2024-01-01 12:00:00', 'yyyy-mm-dd hh24:mi:ss'), NULL, NULL, NULL, NULL, 'Val_query_order_no', 'Val_query_order_sub_no', NULL FROM dual";
        String template = extractInsertTemplate(dirtySql, JdbcConstants.ORACLE);
        System.out.println("=== SQL (===");
        System.out.println(dirtySql);
        System.out.println("\n=== ===");
        System.out.println(template);
    }

    public static String extractInsertTemplate(String sql, DbType dbType) {
        try {
            List<SQLStatement> statements = SQLUtils.parseStatements(sql, dbType);
            if (statements.isEmpty())
                return sql;
            SQLStatement stmt = statements.get(0);
            stmt.accept((SQLASTVisitor)new InsertNormalizeVisitor());
            return SQLUtils.toSQLString((SQLObject)stmt, dbType, null, new VisitorFeature[] { VisitorFeature.OutputParameterized });
        } catch (Exception e) {
            e.printStackTrace();
            return sql;
        }
    }

    static class InsertNormalizeVisitor extends OracleASTVisitorAdapter {
        public boolean visit(SQLInsertStatement x) {
            SQLSelect select = x.getQuery();
            if (select != null) {
                SQLSelectQuery query = select.getQuery();
                if (query instanceof SQLUnionQuery) {
                    SQLSelectQueryBlock firstSelect = findFirstSelect((SQLUnionQuery)query);
                    select.setQuery((SQLSelectQuery)firstSelect);
                }
            }
            return true;
        }

        private SQLSelectQueryBlock findFirstSelect(SQLUnionQuery union) {
            SQLSelectQuery left = union.getLeft();
            if (left instanceof SQLUnionQuery)
                return findFirstSelect((SQLUnionQuery)left);
            return (SQLSelectQueryBlock)left;
        }

        public boolean visit(SQLNullExpr x) {
            SQLUtils.replaceInParent((SQLExpr)x, (SQLExpr)new SQLVariantRefExpr("?"));
            return true;
        }

        public boolean visit(SQLMethodInvokeExpr x) {
            return true;
        }
    }
}
