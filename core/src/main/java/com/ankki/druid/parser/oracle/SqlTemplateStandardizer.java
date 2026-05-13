package com.ankki.druid.parser.oracle;

import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.SQLObject;
import com.alibaba.druid.sql.ast.SQLStatement;
import com.alibaba.druid.sql.ast.expr.SQLIdentifierExpr;
import com.alibaba.druid.sql.ast.expr.SQLPropertyExpr;
import com.alibaba.druid.sql.ast.statement.SQLExprTableSource;
import com.alibaba.druid.sql.ast.statement.SQLSelectItem;
import com.alibaba.druid.sql.visitor.SQLASTVisitor;
import com.alibaba.druid.sql.visitor.SQLASTVisitorAdapter;
import com.alibaba.druid.util.JdbcConstants;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SqlTemplateStandardizer {
    public static void main(String[] args) {
        String originalSql = "select\n\tlabreporti0_.ITEM_CODE as ITEM1_3713_,\n\tlabreporti0_.SERIAL_NO as SERIAL2_3713_,\n\tlabreporti0_.ITEM_NAME as ITEM3_3713_\nfrom\n\tLAB_REPORT_ITEM_DICT labreporti0_\nwhere\n\tlabreporti0_.UPPER_LIMIT <> labreporti0_.LOWER_LIMIT\n\tand (labreporti0_.RESULT_TYPE is null)\norder by\n\tlabreporti0_.ITEM_NAME";
        String template = normalizeSql(originalSql, JdbcConstants.ORACLE);
        System.out.println("=== SQL ===");
        System.out.println(originalSql);
        System.out.println("\n=== ===");
        System.out.println(template);
    }

    public static String normalizeSql(String sql, DbType dbType) {
        try {
            List<SQLStatement> statements = SQLUtils.parseStatements(sql, dbType);
            if (statements.isEmpty())
                return sql;
            SQLStatement stmt = statements.get(0);
            AliasCollectorVisitor collector = new AliasCollectorVisitor();
            stmt.accept((SQLASTVisitor)collector);
            AliasNormalizerVisitor visitor = new AliasNormalizerVisitor(collector.getAliasMapping());
            stmt.accept((SQLASTVisitor)visitor);
            return SQLUtils.toSQLString((SQLObject)stmt, dbType);
        } catch (Exception e) {
            e.printStackTrace();
            return sql;
        }
    }

    static class AliasCollectorVisitor extends SQLASTVisitorAdapter {
        private int tableIndex;

        private Map<String, String> aliasMapping = new HashMap<>();

        public boolean visit(SQLExprTableSource x) {
            String oldAlias = x.getAlias();
            if (oldAlias != null) {
                String newAlias = "t" + ++this.tableIndex;
                this.aliasMapping.put(oldAlias, newAlias);
            }
            return true;
        }

        public Map<String, String> getAliasMapping() {
            return this.aliasMapping;
        }
    }

    static class AliasNormalizerVisitor extends SQLASTVisitorAdapter {
        private Map<String, String> aliasMapping;

        public AliasNormalizerVisitor(Map<String, String> aliasMapping) {
            this.aliasMapping = aliasMapping;
        }

        public boolean visit(SQLExprTableSource x) {
            String oldAlias = x.getAlias();
            if (oldAlias != null && this.aliasMapping.containsKey(oldAlias))
                x.setAlias(this.aliasMapping.get(oldAlias));
            return true;
        }

        public boolean visit(SQLPropertyExpr x) {
            if (x.getOwner() instanceof SQLIdentifierExpr) {
                SQLIdentifierExpr owner = (SQLIdentifierExpr)x.getOwner();
                String oldOwnerName = owner.getName();
                if (this.aliasMapping.containsKey(oldOwnerName))
                    owner.setName(this.aliasMapping.get(oldOwnerName));
            }
            return true;
        }

        public boolean visit(SQLSelectItem x) {
            x.setAlias(null);
            return true;
        }
    }
}
