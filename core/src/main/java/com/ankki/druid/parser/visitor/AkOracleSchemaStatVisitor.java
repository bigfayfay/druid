package com.ankki.druid.parser.visitor;

import com.alibaba.druid.sql.ast.expr.SQLVariantRefExpr;
import com.alibaba.druid.sql.dialect.oracle.visitor.OracleSchemaStatVisitor;

public class AkOracleSchemaStatVisitor extends OracleSchemaStatVisitor {
    @Override
    public boolean visit(SQLVariantRefExpr x) {
        return DbVisitorUtils.visit(x);
    }

}
