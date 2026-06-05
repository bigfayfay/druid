#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""为 smples.sql 中的 SQL 生成模拟参数值，用于性能测试"""

import re
import json
import random
from datetime import datetime, timedelta

def count_placeholders(sql):
    """统计 SQL 中的占位符数量"""
    return sql.count('?')

def generate_mock_values(count):
    """生成模拟参数值"""
    values = []
    for i in range(1, count + 1):
        # 根据参数索引生成不同类型的值
        if i % 10 == 1:
            values.append(f"'value_{i}'")  # 字符串
        elif i % 10 == 2:
            values.append(str(random.randint(1, 1000000)))  # 数字
        elif i % 10 == 3:
            values.append(str(random.randint(10000000000, 99999999999)))  # 手机号
        elif i % 10 == 4:
            values.append(f"'imsi_{random.randint(100000, 999999)}'")  # IMSI
        elif i % 10 == 5:
            values.append(f"'4600{random.randint(1000000, 9999999)}'")  # 手机号
        elif i % 10 == 6:
            # 生成日期时间
            date = datetime.now() - timedelta(days=random.randint(0, 30))
            values.append(f"'{date.strftime('%Y-%m-%d %H:%M:%S')}'")
        elif i % 10 == 7:
            values.append(str(random.randint(0, 1)))  # 布尔值
        elif i % 10 == 8:
            values.append(f"'user_{random.randint(1000, 9999)}'")  # 用户名
        elif i % 10 == 9:
            values.append(str(random.randint(100000000, 999999999)))  # ID
        else:
            values.append(f"'param_{i}'")  # 默认字符串
    return values

def generate_param_bind_metadata(values):
    """生成参数绑定元数据格式：(#1=value1,#2=value2,...)"""
    params = []
    for i, value in enumerate(values, 1):
        params.append(f"#{i}={value}")
    return f";({','.join(params)})"

def process_sql_file(input_file, output_file, max_samples=50):
    """处理 SQL 文件，添加模拟参数值"""
    print(f"读取文件: {input_file}\n")

    sqls = []
    with open(input_file, 'r', encoding='utf-8') as f:
        for line in f:
            line = line.strip()
            # 移除首尾的引号
            if line.startswith('"') and line.endswith('"'):
                line = line[1:-1]
            if line:
                sqls.append(line)

    print(f"文件共 {len(sqls)} 行，处理前 {max_samples} 条\n")

    results = []
    processed_count = 0

    for sql in sqls[:max_samples]:
        if not sql or not sql.strip():
            continue

        processed_count += 1

        # 统计占位符数量
        placeholder_count = count_placeholders(sql)

        # 生成模拟参数值
        mock_values = generate_mock_values(placeholder_count)

        # 生成参数绑定元数据
        param_metadata = generate_param_bind_metadata(mock_values)

        # 构造完整的 SQL（带参数绑定元数据）
        sql_with_params = sql + param_metadata

        results.append({
            'index': processed_count,
            'placeholder_count': placeholder_count,
            'original_sql': sql[:200] + '...' if len(sql) > 200 else sql,
            'sql_with_params': sql_with_params[:200] + '...' if len(sql_with_params) > 200 else sql_with_params,
            'param_metadata': param_metadata[:100] + '...' if len(param_metadata) > 100 else param_metadata
        })

        print(f"【SQL {processed_count}】占位符数量: {placeholder_count}")
        print(f"  原始SQL: {sql[:100]}...")
        print(f"  参数绑定: {param_metadata[:80]}...")
        print()

    # 保存到 JSON 文件
    with open(output_file, 'w', encoding='utf-8') as f:
        json.dump(results, f, ensure_ascii=False, indent=2)

    print(f"\n处理完成:")
    print(f"  - 处理了 {processed_count} 条 SQL")
    print(f"  - 结果已保存到: {output_file}")

    # 生成用于基准测试的 Java 文件
    generate_benchmark_java(results, 'benchmark_sql_samples.java')

    return results

def generate_benchmark_java(results, output_file):
    """生成用于 JMH 基准测试的 Java 代码"""

    java_code = f"""package com.ankki.druid.parser.utils;

import java.util.ArrayList;
import java.util.List;

/**
 * SQL 样例数据（用于性能基准测试）
 *
 * <p>生成时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}
 * <p>SQL 数量: {len(results)}
 */
public class BenchmarkSqlSamples {{

    private static final String[] SQLS_WITH_PARAMS = new String[{len(results)}];
    private static final Integer[] DB_TYPE_IDS = new Integer[{len(results)}];

    static {{
"""

    for i, result in enumerate(results):
        sql_escaped = result['sql_with_params'].replace('\\', '\\\\').replace('"', '\\"')
        java_code += f"""
        SQLS_WITH_PARAMS[{i}] = "{sql_escaped}";
        DB_TYPE_IDS[{i}] = 3;  // 假设都是 Oracle
"""

    java_code += """
    }

    public static String[] getSqlSamples() {
        return SQLS_WITH_PARAMS;
    }

    public static Integer[] getDbTypeIds() {
        return DB_TYPE_IDS;
    }

    public static String getSqlSample(int index) {
        if (index >= 0 && index < SQLS_WITH_PARAMS.length) {
            return SQLS_WITH_PARAMS[index];
        }
        return null;
    }

    public static int getSampleCount() {
        return SQLS_WITH_PARAMS.length;
    }
}
"""

    with open(output_file, 'w', encoding='utf-8') as f:
        f.write(java_code)

    print(f"  - JMH 测试数据已生成: {output_file}")

if __name__ == '__main__':
    process_sql_file(
        r'f:\2026年\03-现场项目\AAS-B07\02-中广电移动网络\smples.sql',
        'benchmark_sql_with_params.json',
        max_samples=50
    )
