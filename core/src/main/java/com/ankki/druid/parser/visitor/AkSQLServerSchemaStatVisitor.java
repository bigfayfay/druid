/*
 * Decompiled with CFR 0.152.
 */
package com.ankki.druid.parser.visitor;

import com.alibaba.druid.sql.ast.expr.SQLVariantRefExpr;
import com.alibaba.druid.sql.dialect.sqlserver.visitor.SQLServerSchemaStatVisitor;
import com.ankki.druid.parser.visitor.DbVisitorUtils;

public class AkSQLServerSchemaStatVisitor
extends SQLServerSchemaStatVisitor {
    @Override
    public boolean visit(SQLVariantRefExpr x) {
        return DbVisitorUtils.visit(x);
    }
}

