package com.ankki.druid.dialect.mysql;

import com.alibaba.druid.DbType;
import com.ankki.druid.parser.AkDbTypeEnum;
import com.ankki.druid.sql.BatchSqlParserTest;
import junit.framework.TestCase;

import java.nio.file.Paths;

public class AkMysqlTest extends TestCase {

    public DbType dbtype = DbType.mysql;

    public int dbId = AkDbTypeEnum.of(dbtype).getTypeId();

    String test_resource_base_dIr = "C:\\Users\\yangfei\\ai\\ds_tmp\\druid-git\\druid-github\\druid\\core\\src\\test\\";

    public void test_zdzsj_inode116_2() {
        BatchSqlParserTest._test(Paths.get(test_resource_base_dIr, "sql\\2\\zdzsj_node116").toString(), DbType.mysql);
    }


    public void test_zdzsj_inode62_2() {
        BatchSqlParserTest._test(Paths.get(test_resource_base_dIr, "sql\\2\\zdzsj_node_62").toString(), DbType.mysql);
    }


    public void test_zdzsj_inode135_2() {
        BatchSqlParserTest._test(Paths.get(test_resource_base_dIr, "sql\\2\\zdzsj_node_135").toString(), DbType.mysql);
    }

    public void test_zdzsj_inode160_2() {
        BatchSqlParserTest._test(Paths.get(test_resource_base_dIr, "sql\\2\\zdzsj_node_160").toString(), DbType.mysql);
    }

}
