package com.ankki;

import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.visitor.ParameterizedOutputVisitorUtils;
import com.ankki.druid.parser.AkDbTypeEnum;
import com.ankki.druid.parser.bind.ParameterValuesFormatter;
import com.ankki.druid.parser.v2.CustomerOutputVisitorUtils;
import junit.framework.TestCase;

import java.util.ArrayList;
import java.util.List;

public class SqlBindTest extends TestCase {


    public void test_raw() {
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

    public void test_unbind() {
        // 1. 参数化
        String s = "($1 = '137813', $2 = '/data1/xfer/stat/...',$3=123456, $4='', $5=, $6 = 'a01\\'s005')";
        String sqlBind = "($1 = '137813', $2   =   '/data1/xfer/stat/...',$3=123456, $4='', $5=, $6 = 'a01''s005', $7 = 'test')";
        List<Object> objects = ParameterValuesFormatter.sqlUnbind(sqlBind);
        System.out.println(objects);
    }

    public void test_bind() {
        // 1. 参数化
        String[] template = CustomerOutputVisitorUtils.getSqlTemplate_v2(
                BaseData.pg_update_sql_177,
                AkDbTypeEnum.PostgreSQL.getTypeId()
        );
        String parameterizedSql = template[2];
        System.out.println(parameterizedSql);
        List<Object> parameters = ParameterValuesFormatter.sqlUnbind(template[3]);
        System.out.println(parameters);
        // 2. 还原
        String restoredSql = SQLUtils.format(parameterizedSql, DbType.postgresql, parameters);
        System.out.println(restoredSql);
    }


}
