/*
 * Decompiled with CFR 0.152.
 */
package com.ankki.druid.parser;

import com.alibaba.druid.DbType;
import java.util.Arrays;

public enum AkDbTypeEnum {
    NoSupports(0, "", null),
    SQLSERVER(1, "SQLSERVER", DbType.sqlserver),
    MySQL(2, "MySQL", DbType.mysql),
    Oracle(3, "Oracle", DbType.oracle),
    Sybase(4, "Sybase", DbType.sybase),
    DB2(5, "DB2", DbType.db2),
    Informix(6, "Informix", DbType.informix),
    PostgreSQL(7, "PostgreSQL", DbType.postgresql),
    DMDbms(12, "DMDbms", DbType.dm),
    Kingbase(13, "Kingbase", DbType.kingbase),
    Gbase(14, "Gbase", DbType.gbase),
    Hive(19, "Hive", DbType.hive),
    GaussDB(38, "GaussDB", DbType.gaussdb),
    MariaDB(40, "MariaDB", DbType.mariadb),
    XUGU(40, "XUGU", DbType.xugu),
    Greenplum(54, "Greenplum", DbType.greenplum),
    Highgo(55, "Highgo", DbType.highgo),
    TiDB(56, "TiDB", DbType.tidb),
    RDS_MySQL(2001, "RDS_MySQL", DbType.mysql);

    private int typeId;
    private String akDbTypeName;
    private DbType druidDbType;

    private AkDbTypeEnum(int typeId, String akDbTypeName, DbType druidDbType) {
        this.typeId = typeId;
        this.akDbTypeName = akDbTypeName;
        this.druidDbType = druidDbType;
    }

    public static DbType of(int typeId) {
        return Arrays.asList(AkDbTypeEnum.values()).stream().filter(type -> typeId == type.getTypeId()).findFirst().orElse(NoSupports).getDruidDbType();
    }

    public int getTypeId() {
        return this.typeId;
    }

    public String getAkDbTypeName() {
        return this.akDbTypeName;
    }

    public DbType getDruidDbType() {
        return this.druidDbType;
    }
}

