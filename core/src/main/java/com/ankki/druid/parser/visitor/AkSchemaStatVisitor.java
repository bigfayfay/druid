package com.ankki.druid.parser.visitor;

import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.ast.expr.SQLVariantRefExpr;
import com.alibaba.druid.sql.ast.statement.*;
import com.alibaba.druid.sql.dialect.mysql.visitor.MySqlASTVisitor;
import com.alibaba.druid.sql.dialect.oracle.visitor.OracleASTVisitor;
import com.alibaba.druid.sql.dialect.sqlserver.visitor.SQLServerASTVisitor;
import com.alibaba.druid.sql.visitor.SchemaStatVisitor;

public class AkSchemaStatVisitor extends SchemaStatVisitor implements OracleASTVisitor, MySqlASTVisitor, SQLServerASTVisitor {
    public AkSchemaStatVisitor() {
        super(DbType.mysql);
    }

    public AkSchemaStatVisitor(DbType dbType) {
        super(dbType);
    }
    /**
     * 绑定变量回填
     *
     * @param x
     * @return
     */
    public boolean visit(SQLVariantRefExpr x) {
        return DbVisitorUtils.visit(x);
    }
}
