package com.ankki.druid.sql;

import com.alibaba.druid.DbType;
import com.ankki.druid.parser.CustomerOutputVisitorUtils;
import junit.framework.TestCase;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Paths;

/**
 * 批量SQL解析测试类
 * 
 * <p>功能：从指定文件读取SQL（一行一个），批量调用解析方法</p>
 * 
 * <p>使用方式：</p>
 * <pre>
 *   BatchSqlParserTest.test("path/to/sqls.txt", DbType.mysql);
 * </pre>
 * 
 * @author AI Assistant
 * @date 2026-06-10
 */
public class BatchSqlParserTest extends TestCase {
    String test_resource_base_dIr = "C:\\Users\\yangfei\\ai\\ds_tmp\\druid-git\\druid-github\\druid\\core\\src\\test\\";

    public void test_zdzsj_inode116_2() {
        _test(Paths.get(test_resource_base_dIr, "sql\\2\\zdzsj_node116").toString(), DbType.mysql);
    }


    public void test_zdzsj_inode62_2() {
        _test(Paths.get(test_resource_base_dIr, "sql\\2\\zdzsj_node_62").toString(), DbType.mysql);
    }


    /**
     * 批量解析SQL文件
     * 
     * @param filePath SQL文件路径（一行一个SQL）
     * @param dbType 数据库类型
     */
    public static void _test(String filePath, DbType dbType) {
        // 验证文件
        File file = new File(filePath);
        if (!file.exists()) {
            System.err.println("错误: 文件不存在 - " + filePath);
            return;
        }

        // 获取akDbTypeId
        Integer akDbTypeId = getAkDbTypeId(dbType);
        if (akDbTypeId == null) {
            System.err.println("错误: 不支持的数据库类型 - " + dbType);
            return;
        }

        System.out.println("开始解析SQL文件: " + filePath);
        System.out.println("数据库类型: " + dbType);
        System.out.println("----------------------------------------");

        int lineCount = 0;
        int successCount = 0;
        int failCount = 0;

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String sql;
            while ((sql = reader.readLine()) != null) {
                // 跳过空行
                if (sql.trim().isEmpty()) {
                    continue;
                }
                
                lineCount++;
                
                try {
                    // 调用解析方法
                    String sqlStr1 = replaceAllInvisibleChars(sql);
                    System.out.println("[" + lineCount + "] " + sqlStr1);
                    String[] result = CustomerOutputVisitorUtils.getSqlTemplate_v2(sqlStr1, akDbTypeId);
                    
                    // 打印结果
                    System.out.println("[" + lineCount + "] " + result[0] + " | " + result[4]);
                    
                    if ("SUCCESS".equals(result[0]) || result[0].startsWith("SUCCESS|")) {
                        successCount++;
                    } else {
                        failCount++;
                    }
                } catch (Exception e) {
                    failCount++;
                    System.err.println("[" + lineCount + "] 解析异常: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("读取文件失败: " + e.getMessage());
            return;
        }

        // 输出统计
        System.out.println("----------------------------------------");
        System.out.println("解析完成！");
        System.out.println("总数: " + lineCount);
        System.out.println("成功: " + successCount);
        System.out.println("失败: " + failCount);
    }

    /**
     * 获取akDbTypeId
     */
    private static Integer getAkDbTypeId(DbType dbType) {
        switch (dbType) {
            case mysql:
                return 1;
            case oracle:
                return 3;
            case postgresql:
                return 2;
            case sqlserver:
                return 4;
            default:
                return null;
        }
    }


    public static String replaceAllInvisibleChars(String str) {
        return str == null ? null :
                str.replaceAll("\\u0011", "\\\\")
                .replaceAll("\\u0012", "\\\"")
                .replaceAll("\\u0013", "\\\r")
                .replaceAll("\\u0014", "\\\n");
    }
}
