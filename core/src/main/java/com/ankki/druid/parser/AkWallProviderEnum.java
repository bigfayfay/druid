/*
 * Decompiled with CFR 0.152.
 */
package com.ankki.druid.parser;

import com.alibaba.druid.DbType;
import com.alibaba.druid.wall.WallCheckResult;
import com.alibaba.druid.wall.WallProvider;
import com.alibaba.druid.wall.spi.CKWallProvider;
import com.alibaba.druid.wall.spi.DB2WallProvider;
import com.alibaba.druid.wall.spi.MySqlWallProvider;
import com.alibaba.druid.wall.spi.OracleWallProvider;
import com.alibaba.druid.wall.spi.PGWallProvider;
import com.alibaba.druid.wall.spi.SQLServerWallProvider;
import com.alibaba.druid.wall.spi.SQLiteWallProvider;
import java.util.Arrays;

public enum AkWallProviderEnum {
    other(DbType.other, new MySqlWallProvider()),
    mysql(DbType.mysql, new MySqlWallProvider()),
    oracle(DbType.oracle, new OracleWallProvider()),
    postgresql(DbType.postgresql, new PGWallProvider()),
    sqlserver(DbType.sqlserver, new SQLServerWallProvider()),
    db2(DbType.db2, new DB2WallProvider()),
    sqlite(DbType.sqlite, new SQLiteWallProvider()),
    clickhouse(DbType.clickhouse, new CKWallProvider());

    private DbType dbType;
    private WallProvider provider;

    private AkWallProviderEnum(DbType dbType, WallProvider provider) {
        this.dbType = dbType;
        this.provider = provider;
    }

    public static AkWallProviderEnum of(DbType dbType) {
        if (dbType == null) {
            return other;
        }
        return Arrays.stream(AkWallProviderEnum.values()).filter(row -> row.getDbType().name().equals(dbType.name())).findFirst().orElse(other);
    }

    public WallCheckResult checkSqlInject(String sql) {
        return this.provider.check(sql);
    }

    public DbType getDbType() {
        return this.dbType;
    }

    public WallProvider getProvider() {
        return this.provider;
    }
}

