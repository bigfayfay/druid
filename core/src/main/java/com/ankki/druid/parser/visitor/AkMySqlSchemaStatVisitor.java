/*
 * Decompiled with CFR 0.152.
 */
package com.ankki.druid.parser.visitor;

import com.alibaba.druid.sql.ast.expr.SQLVariantRefExpr;
import com.alibaba.druid.sql.dialect.mysql.visitor.MySqlSchemaStatVisitor;
import com.ankki.druid.parser.visitor.DbVisitorUtils;

public class AkMySqlSchemaStatVisitor
extends MySqlSchemaStatVisitor {
    @Override
    public boolean visit(SQLVariantRefExpr x) {
        return DbVisitorUtils.visit(x);
    }
}

