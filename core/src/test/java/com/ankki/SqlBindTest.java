package com.ankki;

import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.visitor.ParameterizedOutputVisitorUtils;
import junit.framework.TestCase;

import java.util.ArrayList;
import java.util.List;

public class SqlBindTest extends TestCase {


    public void test_som() {
        // 1. 参数化
        List<Object> outParameters = new ArrayList<>();
        String parameterizedSql = ParameterizedOutputVisitorUtils.parameterize(
                BaseData.pg_insert_sql_4701,
                DbType.postgresql,
                outParameters
        );
        System.out.println(parameterizedSql);
        // parameterizedSql = "SELECT * FROM t WHERE id = ? AND name = ?"
        // outParameters = [1, "test"]

        // 2. 还原
        String restoredSql = SQLUtils.format(parameterizedSql, DbType.postgresql, outParameters);
        // restoredSql = "SELECT * FROM t WHERE id = 1 AND name = 'test'"
        System.out.println(restoredSql);
    }


}
