#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
生成对照测试类 SqlTemplateComparisonBenchmark.java
"""

import re

# 读取 smples_new1.sql
with open('../smples_new1.sql', 'r', encoding='utf-8') as f:
    sqls = [line.strip() for line in f if line.strip()]

# 转义 SQL 字符串（Java 格式）
def escape_sql(sql):
    # 压缩多行 SQL 为单行
    sql = ' '.join(sql.split())
    # 转义双引号和反斜杠
    sql = sql.replace('\\', '\\\\').replace('"', '\\"')
    return sql

# 分析 SQL 类型并生成索引数组
insert_indices = []
update_indices = []
select_indices = []
begin_indices = []

for i, sql in enumerate(sqls):
    upper = sql.upper()
    if upper.startswith('INSERT'):
        insert_indices.append(i)
    elif upper.startswith('UPDATE'):
        update_indices.append(i)
    elif upper.startswith('SELECT') or upper.startswith('WITH'):
        select_indices.append(i)
    elif upper.startswith('BEGIN'):
        begin_indices.append(i)

# 选择代表 SQL（中等长度）
def get_median_idx(indices, sqls):
    lengths = [(i, len(' '.join(sqls[i].split()))) for i in indices]
    lengths.sort(key=lambda x: x[1])
    return lengths[len(lengths) // 2][0]

insert_rep = get_median_idx(insert_indices, sqls)
update_rep = get_median_idx(update_indices, sqls)
select_rep = get_median_idx(select_indices, sqls)
begin_rep = get_median_idx(begin_indices, sqls)

print(f'INSERT: {len(insert_indices)} 条, 代表: 索引 {insert_rep}')
print(f'UPDATE: {len(update_indices)} 条, 代表: 索引 {update_rep}')
print(f'SELECT: {len(select_indices)} 条, 代表: 索引 {select_rep}')
print(f'BEGIN: {len(begin_indices)} 条, 代表: 索引 {begin_rep}')

# 生成 Java 类
java_code = f'''package com.ankki.druid.parser.utils;

import com.ankki.druid.parser.AkDbTypeEnum;
import com.ankki.druid.parser.CustomerOutputVisitorUtils;
import com.ankki.druid.parser.template.LightweightCachedAkOutputVisitorUtils;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

/**
 * SQL 模板提取性能对照测试
 *
 * 测试数据来源: smples_new1.sql (100 条)
 * - INSERT: {len(insert_indices)} 条
 * - UPDATE: {len(update_indices)} 条
 * - SELECT: {len(select_indices)} 条 (含 CTE)
 * - BEGIN;INSERT: {len(begin_indices)} 条
 *
 * 对照组:
 * - baseline: CustomerOutputVisitorUtils (无缓存)
 * - optimized: LightweightCachedAkOutputVisitorUtils (LRU 缓存)
 */
@BenchmarkMode({{Mode.Throughput, Mode.AverageTime}})
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 10, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@State(Scope.Thread)
public class SqlTemplateComparisonBenchmark {{

    private Integer dbType;

    // ========== 共享测试数据 (smples_new1.sql) ==========

    public static final String[] ALL_SQLS = {{
'''

# 添加所有 SQL
for i, sql in enumerate(sqls):
    sql_type = 'UNKNOWN'
    if i in insert_indices:
        sql_type = 'INSERT'
    elif i in update_indices:
        sql_type = 'UPDATE'
    elif i in select_indices:
        sql_type = 'SELECT'
    elif i in begin_indices:
        sql_type = 'BEGIN'

    java_code += f'        /* {i+1} {sql_type} */ "{escape_sql(sql)}",\n'

java_code += f'''    }};

    public static final int[] INSERT_INDICES = {{{','.join(map(str, insert_indices))}}};
    public static final int[] UPDATE_INDICES = {{{','.join(map(str, update_indices))}}};
    public static final int[] SELECT_INDICES = {{{','.join(map(str, select_indices))}}};
    public static final int[] BEGIN_INDICES = {{{','.join(map(str, begin_indices))}}};

    // ========== 单条 SQL 测试 ==========

    // INSERT 代表 (索引 {insert_rep})
    public static final int INSERT_REP_IDX = {insert_rep};

    @Benchmark
    public String[] baseline_single_insert() {{
        return CustomerOutputVisitorUtils.getSqlTemplate_v2(ALL_SQLS[INSERT_REP_IDX], dbType);
    }}

    @Benchmark
    public String[] optimized_single_insert() {{
        return LightweightCachedAkOutputVisitorUtils.getSqlTemplate_v2(ALL_SQLS[INSERT_REP_IDX], dbType);
    }}

    // UPDATE 代表 (索引 {update_rep})
    public static final int UPDATE_REP_IDX = {update_rep};

    @Benchmark
    public String[] baseline_single_update() {{
        return CustomerOutputVisitorUtils.getSqlTemplate_v2(ALL_SQLS[UPDATE_REP_IDX], dbType);
    }}

    @Benchmark
    public String[] optimized_single_update() {{
        return LightweightCachedAkOutputVisitorUtils.getSqlTemplate_v2(ALL_SQLS[UPDATE_REP_IDX], dbType);
    }}

    // SELECT 代表 (索引 {select_rep})
    public static final int SELECT_REP_IDX = {select_rep};

    @Benchmark
    public String[] baseline_single_select() {{
        return CustomerOutputVisitorUtils.getSqlTemplate_v2(ALL_SQLS[SELECT_REP_IDX], dbType);
    }}

    @Benchmark
    public String[] optimized_single_select() {{
        return LightweightCachedAkOutputVisitorUtils.getSqlTemplate_v2(ALL_SQLS[SELECT_REP_IDX], dbType);
    }}

    // BEGIN 代表 (索引 {begin_rep})
    public static final int BEGIN_REP_IDX = {begin_rep};

    @Benchmark
    public String[] baseline_single_begin() {{
        return CustomerOutputVisitorUtils.getSqlTemplate_v2(ALL_SQLS[BEGIN_REP_IDX], dbType);
    }}

    @Benchmark
    public String[] optimized_single_begin() {{
        return LightweightCachedAkOutputVisitorUtils.getSqlTemplate_v2(ALL_SQLS[BEGIN_REP_IDX], dbType);
    }}

    // ========== 混合场景测试 (100 条依次执行) ==========

    @Benchmark
    public void baseline_mixed_100(Blackhole bh) {{
        for (String sql : ALL_SQLS) {{
            bh.consume(CustomerOutputVisitorUtils.getSqlTemplate_v2(sql, dbType));
        }}
    }}

    @Benchmark
    public void optimized_mixed_100(Blackhole bh) {{
        for (String sql : ALL_SQLS) {{
            bh.consume(LightweightCachedAkOutputVisitorUtils.getSqlTemplate_v2(sql, dbType));
        }}
    }}

    // ========== 单类型场景测试 ==========

    @Benchmark
    public void baseline_insert_only(Blackhole bh) {{
        for (int idx : INSERT_INDICES) {{
            bh.consume(CustomerOutputVisitorUtils.getSqlTemplate_v2(ALL_SQLS[idx], dbType));
        }}
    }}

    @Benchmark
    public void optimized_insert_only(Blackhole bh) {{
        for (int idx : INSERT_INDICES) {{
            bh.consume(LightweightCachedAkOutputVisitorUtils.getSqlTemplate_v2(ALL_SQLS[idx], dbType));
        }}
    }}

    @Benchmark
    public void baseline_update_only(Blackhole bh) {{
        for (int idx : UPDATE_INDICES) {{
            bh.consume(CustomerOutputVisitorUtils.getSqlTemplate_v2(ALL_SQLS[idx], dbType));
        }}
    }}

    @Benchmark
    public void optimized_update_only(Blackhole bh) {{
        for (int idx : UPDATE_INDICES) {{
            bh.consume(LightweightCachedAkOutputVisitorUtils.getSqlTemplate_v2(ALL_SQLS[idx], dbType));
        }}
    }}

    @Benchmark
    public void baseline_select_only(Blackhole bh) {{
        for (int idx : SELECT_INDICES) {{
            bh.consume(CustomerOutputVisitorUtils.getSqlTemplate_v2(ALL_SQLS[idx], dbType));
        }}
    }}

    @Benchmark
    public void optimized_select_only(Blackhole bh) {{
        for (int idx : SELECT_INDICES) {{
            bh.consume(LightweightCachedAkOutputVisitorUtils.getSqlTemplate_v2(ALL_SQLS[idx], dbType));
        }}
    }}

    @Benchmark
    public void baseline_begin_only(Blackhole bh) {{
        for (int idx : BEGIN_INDICES) {{
            bh.consume(CustomerOutputVisitorUtils.getSqlTemplate_v2(ALL_SQLS[idx], dbType));
        }}
    }}

    @Benchmark
    public void optimized_begin_only(Blackhole bh) {{
        for (int idx : BEGIN_INDICES) {{
            bh.consume(LightweightCachedAkOutputVisitorUtils.getSqlTemplate_v2(ALL_SQLS[idx], dbType));
        }}
    }}

    // ========== Setup ==========

    @Setup
    public void setup() {{
        this.dbType = AkDbTypeEnum.PostgreSQL.getTypeId();
        // 预清空缓存（确保测试一致性）
        LightweightCachedAkOutputVisitorUtils.clearCache();
    }}

    @TearDown
    public void tearDown() {{
        // 打印缓存统计
        // System.out.println("Cache stats: " + LightweightCachedAkOutputVisitorUtils.getCacheStats());
    }}

    public static void main(String[] args) throws Exception {{
        org.openjdk.jmh.runner.options.Options opt = new org.openjdk.jmh.runner.options.OptionsBuilder()
                .include(SqlTemplateComparisonBenchmark.class.getSimpleName())
                .build();
        new org.openjdk.jmh.runner.Runner(opt).run();
    }}
}}
'''

# 写入文件
output_path = '../druid-ak/com/ankki/druid/parser/utils/SqlTemplateComparisonBenchmark.java'
with open(output_path, 'w', encoding='utf-8') as f:
    f.write(java_code)

print(f'\n已生成: {output_path}')
print(f'文件大小: {len(java_code)} 字节')
