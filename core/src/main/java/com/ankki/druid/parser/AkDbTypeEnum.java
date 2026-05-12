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
    PostgreSQL(6, "PostgreSQL", DbType.postgresql),
    Kingbase(13, "Kingbase", DbType.kingbase),
    Gbase(14, "Gbase", DbType.gbase),
    Hive(19, "Hive", DbType.hive),
    GaussDB(38, "GaussDB", DbType.gaussdb),
    MariaDB(40, "MariaDB", DbType.mariadb),
    XUGU(40, "XUGU", DbType.xugu),
    Greenplum(54, "Greenplum", DbType.greenplum),
    Highgo(55, "Highgo", DbType.highgo),
    TiDB(56, "TiDB", DbType.tidb),
    RDS_MySQL(2001, "RDS_MySQL", DbType.mysql),;

    /**
     * Ak数据库类型id
     */
    private int typeId;
    /**
     * Ak数据库类型名称
     */
    private String akDbTypeName;
    /**
     * druid数据库类型
     */
    private DbType druidDbType;

    AkDbTypeEnum(int typeId, String akDbTypeName, DbType druidDbType) {
        this.typeId = typeId;
        this.akDbTypeName = akDbTypeName;
        this.druidDbType = druidDbType;
    }

    public static DbType of(int typeId) {
        return Arrays.asList(AkDbTypeEnum.values()).stream().filter(type -> typeId == type.getTypeId()).findFirst().orElse(AkDbTypeEnum.NoSupports).getDruidDbType();
    }

    public int getTypeId() {
        return typeId;
    }

    public String getAkDbTypeName() {
        return akDbTypeName;
    }

    public DbType getDruidDbType() {
        return druidDbType;
    }

}
