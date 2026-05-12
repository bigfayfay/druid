package com.ankki.druid.parser;

import com.alibaba.druid.DbType;
import com.alibaba.druid.wall.WallCheckResult;
import com.alibaba.druid.wall.WallProvider;
import com.alibaba.druid.wall.spi.*;

import java.util.Arrays;

public enum AkWallProviderEnum {
    other(DbType.other, new MySqlWallProvider()),
    mysql(DbType.mysql, new MySqlWallProvider()),
    oracle(DbType.oracle, new OracleWallProvider()),
    postgresql(DbType.postgresql, new PGWallProvider()),
    sqlserver(DbType.sqlserver, new SQLServerWallProvider()),
    db2(DbType.db2, new DB2WallProvider()),
    sqlite(DbType.sqlite, new SQLiteWallProvider()),
    clickhouse(DbType.clickhouse, new ClickhouseWallProvider()),;

    private DbType dbType;
    private WallProvider provider;

    AkWallProviderEnum(DbType dbType, WallProvider provider) {
        this.dbType = dbType;
        this.provider = provider;
    }

    public static AkWallProviderEnum of(DbType dbType) {
        if (dbType == null) {
            return AkWallProviderEnum.other;
        }
        return Arrays.stream(AkWallProviderEnum.values()).filter(row -> row.getDbType().name().equals(dbType.name())).findFirst().orElse(AkWallProviderEnum.other);
    }

    public WallCheckResult checkSqlInject(String sql) {
        return this.provider.check(sql);
    }

    public DbType getDbType() {
        return dbType;
    }

    public WallProvider getProvider() {
        return provider;
    }

}
