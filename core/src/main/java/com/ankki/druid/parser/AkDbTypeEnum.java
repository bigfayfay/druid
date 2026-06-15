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
    // es不是SQL
    //    ES(37, "ES", DbType.elastic_search),
    GaussDB(38, "GaussDB", DbType.gaussdb),
    MariaDB(40, "MariaDB", DbType.mariadb),
    XUGU(42, "XUGU", DbType.xugu),
    Impala(50, "Impala", DbType.impala),
    DorisDB(52, "DorisDB", DbType.doris),
    Greenplum(54, "Greenplum", DbType.greenplum),
    Highgo(55, "Highgo", DbType.highgo),
    TiDB(56, "TiDB", DbType.tidb),

    // [B03-B07)
    ClickHouse(71, "ClickHouse", DbType.clickhouse),
    Teradata(91, "Teradata", DbType.teradata),
    OceanBase_MySQL(94, "OceanBase_MySQL", DbType.oceanbase),
    OceanBase_Oracle(95, "OceanBase_Oracle", DbType.oceanbase_oracle),

    // 20260606 -- B07
    Starrocks(100, "Starrocks", DbType.starrocks),

    // protocol-mysql
    PerconaServer(117, "PerconaServer", DbType.mysql),
    AliSQL(118, "AliSQL", DbType.mysql),
    WebScaleSQL(119, "WebScaleSQL", DbType.mysql),
    InnoSQL(120, "InnoSQL", DbType.mysql),
    CascaDB(121, "CascaDB", DbType.mysql),
    Cotton(122, "Cotton", DbType.mysql),
    MyRocks(123, "MyRocks", DbType.mysql),
    Drizzle(124, "Drizzle", DbType.mysql),
    TokuDB(125, "TokuDB", DbType.mysql),
    XtraDB(126, "XtraDB", DbType.mysql),
    SpiderForMySQL(127, "SpiderForMySQL", DbType.mysql),
    MyRelay(128, "MyRelay", DbType.mysql),
    MySQLMaria(129, "MySQLMaria", DbType.mysql),
    MepSQL(130, "MepSQL", DbType.postgresql),
    Mroonga(131, "Mroonga", DbType.mysql),
    MySQLEnterprise(132, "MySQLEnterprise", DbType.mysql),
    OurDelta(133, "OurDelta", DbType.mysql),
    OQGRAPH(134, "OQGRAPH", DbType.mysql),
    // protocol-teradata
    AsterData(135, "AsterData", DbType.teradata),

    // protocol-postgresql
    BDR(136, "BDR", DbType.postgresql),
    GresCube(137, "GresCube", DbType.postgresql),
    izgres(138, "izgres", DbType.postgresql),
    Citus(139, "Citus", DbType.postgresql),
    CyberCluster(140, "CyberCluster", DbType.postgresql),
    ExtenDB(141, "ExtenDB", DbType.postgresql),
    FUJITSUEnterprisePG(142, "FUJITSUEnterprisePG", DbType.postgresql),
    GridSQL(144, "GridSQL", DbType.postgresql),
    GreatBridgePG(145, "GreatBridgePG", DbType.postgresql),

    Mammoth(148, "Mammoth", DbType.mysql),

    Netezza(149, "Netezza", DbType.postgresql),
    NuSphereUltraSQL(150, "NuSphereUltraSQL", DbType.postgresql),
    ParAccel(151, "ParAccel", DbType.postgresql),
    PervasivePostgreSQL(152, "PervasivePostgreSQL", DbType.postgresql),
    PGCluster(153, "PGCluster", DbType.postgresql),
    PGClusterII(154, "PGClusterII", DbType.postgresql),
    PGPoolII(155, "PGPoolII", DbType.postgresql),
    PipelineDB(156, "PipelineDB", DbType.postgresql),
    PostgresForest(157, "PostgresForest", DbType.postgresql),
    PostgresPlus(158, "PostgresPlus", DbType.postgresql),
    PostgresXC(159, "PostgresXC", DbType.postgresql),
    StadoPostgresPlusAdvancedServer(160, "StadoPostgresPlusAdvancedServer", DbType.postgresql),
    PostgresR(161, "PostgresR", DbType.postgresql),
    PostgresX2(162, "PostgresX2", DbType.postgresql),
    RecDB(163, "RecDB", DbType.postgresql),
    RedShift(164, "RedShift", DbType.postgresql),
    PostgresXL(165, "PostgresXL", DbType.postgresql),
    PowerGres(166, "PowerGres", DbType.postgresql),
    PowerGresPlus(167, "PowerGresPlus", DbType.postgresql),
    PostgreSQLForSolaris(168, "PostgreSQLForSolaris", DbType.postgresql),
    ToroDB(169, "ToroDB", DbType.postgresql),
    TelegraphCQ(171, "TelegraphCQ", DbType.postgresql),
    TruCQ(172, "TruCQ", DbType.postgresql),
    YahooEverest(173, "YahooEverest", DbType.postgresql),

    // protocol-
    VerticalPartitioningForMySQL(174, "VerticalPartitioningForMySQL", DbType.mysql),
    RDS_MySQL(2001, "RDS_MySQL", DbType.mysql);

    private final int typeId;
    /**
     * not use
     */
    private final String akDbTypeName;
    private final DbType druidDbType;

    AkDbTypeEnum(int typeId, String akDbTypeName, DbType druidDbType) {
        this.typeId = typeId;
        this.akDbTypeName = akDbTypeName;
        this.druidDbType = druidDbType;
    }

    public static DbType of(int typeId) {
        return Arrays.stream(AkDbTypeEnum.values()).filter(type -> typeId == type.getTypeId()).findFirst().orElse(NoSupports).getDruidDbType();
    }

    public static AkDbTypeEnum of(DbType type) {
        return Arrays.stream(AkDbTypeEnum.values()).filter(t -> type == t.getDruidDbType()).findFirst().orElse(NoSupports);
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