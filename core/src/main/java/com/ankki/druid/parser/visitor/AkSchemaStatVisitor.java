/*
 * Decompiled with CFR 0.152.
 */
package com.ankki.druid.parser.visitor;

import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.ast.expr.SQLVariantRefExpr;
import com.alibaba.druid.sql.dialect.mysql.visitor.MySqlASTVisitor;
import com.alibaba.druid.sql.dialect.oracle.visitor.OracleASTVisitor;
import com.alibaba.druid.sql.dialect.sqlserver.visitor.SQLServerASTVisitor;
import com.alibaba.druid.sql.visitor.SchemaStatVisitor;
import com.ankki.druid.parser.visitor.DbVisitorUtils;

public class AkSchemaStatVisitor
extends SchemaStatVisitor
implements OracleASTVisitor,
MySqlASTVisitor,
SQLServerASTVisitor {
    public AkSchemaStatVisitor() {
        super(DbType.mysql);
    }

    public AkSchemaStatVisitor(DbType dbType) {
        super(dbType);
    }

    @Override
    public boolean visit(SQLVariantRefExpr x) {
        return DbVisitorUtils.visit(x);
    }
}

