package com.ankki;

import com.ankki.druid.parser.AkDruidSqlParser;
import com.ankki.druid.parser.CustomerOutputVisitorUtils;
import junit.framework.TestCase;

/**
 * Unit test for simple App.
 */
public class SqlTemplateTest extends TestCase {
    public void test_select() {
        String selectSql = "UPDATE SCOTT.'a_email' SET \u0012email\u0012 = 'bbb' WHERE  \"email\" = 'fhh';";
        //String selectSql = "inser user (nam )select name from user";
        System.out.println("SQL模   板：" + AkDruidSqlParser.getSqlTemplate(selectSql, 3, true));
        this.print();
    }

    public void test_select2() {
        String selectSql = "SELECT * FROM dual";
        System.out.println("SQL模   板：" + AkDruidSqlParser.getSqlTemplate(selectSql, 3, true));
        this.print();
    }

    public void test_select3() {
        String selectSql = "SELECT * FROM USER WHERE NAME=:AAA;(AAA=张珊)";
        System.out.println("SQL模   板：" + AkDruidSqlParser.getSqlTemplate(selectSql, 3, true));
        this.print();
    }

    public void test_update() {
        String updateSQL = "UPDATE outpdoct.outp_presc SET charge_indicator = 'zhangshan' WHERE (presc_id, item_no) IN ( SELECT sheet_no, sheet_item_no FROM outp_presc WHERE rcpt_no = 'zhangshan' AND sheet_indicator = 'zhangshan' AND item_class IN ('zhangshan') )";
        System.out.println("SQL模   板：" + AkDruidSqlParser.getSqlTemplate(updateSQL, 3, true));
        this.print();
    }

    public void test_ALTER() {
        String sql = "ALTER SESSION SET CURRENT_SCHEMA = \u0012ABC\u0012;";
        System.out.println("SQL模   板：" + AkDruidSqlParser.getSqlTemplate(sql, 3, true));
        this.print();
    }

    public void test_ALTER_Customer() {
        String sql = "ALTER SESSION SET CURRENT_SCHEMA = :ABC;";
        System.out.println("SQL模   板：" + CustomerOutputVisitorUtils.getSqlTemplate(sql, 3));
        this.print();
    }

    public void test_update_Customer() {
        String updateSQL = "UPDATE outpdoct.outp_presc SET charge_indicator = 'zhangshan' WHERE (presc_id, item_no) IN ( SELECT sheet_no, sheet_item_no FROM outp_presc WHERE rcpt_no = 'zhangshan' AND sheet_indicator = 'zhangshan' AND item_class IN ('zhangshan') )";
        System.out.println("SQL模   板：" + CustomerOutputVisitorUtils.getSqlTemplate(updateSQL, 3));
        this.print();
    }


    public void test_select_Customer() {
        String selectSql = "SELECT * FROM USER WHERE NAME=:AAA;(AAA=张珊)";
        System.out.println("SQL模   板：" + CustomerOutputVisitorUtils.getSqlTemplate(selectSql, 3));
        this.print();
    }

    public void test_select_Customer2() {
        String selectSql = "SELECT * FROM dual";
        System.out.println("SQL模   板：" + CustomerOutputVisitorUtils.getSqlTemplate(selectSql, 3));
        this.print();
    }


    public void print() {
        System.out.println("SQL模板中表名：" + AkDruidSqlParser.getTables());
        System.out.println("SQL模板中字段：" + AkDruidSqlParser.getColumns());
        System.out.println("SQL模板中函数：" + AkDruidSqlParser.getFunctions());
        System.out.println("SQL模板中条件：" + AkDruidSqlParser.getConditions());
        System.out.println("SQL模板中参数值：" + AkDruidSqlParser.getParameterValues());
    }
}
