/*
 * Decompiled with CFR 0.152.
 */
package com.ankki.druid.parser.visitor;

import com.alibaba.druid.sql.ast.expr.SQLVariantRefExpr;
import com.alibaba.druid.sql.dialect.oracle.visitor.OracleSchemaStatVisitor;
import com.ankki.druid.parser.visitor.DbVisitorUtils;

public class AkOracleSchemaStatVisitor
extends OracleSchemaStatVisitor {
    @Override
    public boolean visit(SQLVariantRefExpr x) {
        return DbVisitorUtils.visit(x);
    }
}

