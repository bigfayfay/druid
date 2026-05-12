package com.ankki.druid.parser.visitor;

import com.alibaba.druid.sql.ast.expr.SQLVariantRefExpr;
import com.alibaba.druid.sql.dialect.h2.visitor.H2SchemaStatVisitor;

public class AkH2SchemaStatVisitor extends H2SchemaStatVisitor {
    public boolean visit(SQLVariantRefExpr x) {
        return DbVisitorUtils.visit(x);
    }
}
