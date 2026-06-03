/*
 * Decompiled with CFR 0.152.
 */
package com.ankki.druid.parser.visitor;

import com.alibaba.druid.sql.ast.expr.SQLVariantRefExpr;
import com.alibaba.druid.sql.dialect.h2.visitor.H2SchemaStatVisitor;
import com.ankki.druid.parser.visitor.DbVisitorUtils;

public class AkH2SchemaStatVisitor
extends H2SchemaStatVisitor {
    @Override
    public boolean visit(SQLVariantRefExpr x) {
        return DbVisitorUtils.visit(x);
    }
}

