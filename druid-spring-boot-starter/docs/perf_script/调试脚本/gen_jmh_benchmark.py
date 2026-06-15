# -*- coding: utf-8 -*-
"""读取 smples.sql 生成嵌入式 JMH 基准 Java 源码"""
import re

with open("../smples.sql", "r", encoding="utf-8") as f:
    lines = [l.strip() for l in f if l.strip()]

# 转义 Java 字符串中的特殊字符
def escape_java(s):
    return s.replace("\\", "\\\\").replace('"', '\\"').replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")

# 按类型分组
inserts = []; updates = []; selects = []; begins = []
for i, l in enumerate(lines, 1):
    u = l.upper().strip()
    if u.startswith("INSERT"): inserts.append((i, l))
    elif u.startswith("UPDATE"): updates.append((i, l))
    elif u.startswith("SELECT"): selects.append((i, l))
    elif u.startswith("BEGIN") or "BEGIN;" in u: begins.append((i, l))

java = """package com.ankki.druid.parser.utils;

import com.ankki.druid.parser.AkDbTypeEnum;
import com.ankki.druid.parser.template.LightweightCachedAkOutputVisitorUtils;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

/**
 * JMH 基准测试：100 条 SQL 混合场景
 * 包含所有类型（INSERT/UPDATE/SELECT/BEGIN;INSERT）
 * 同模板不同参数的 SQL 组用于测试缓存命中
 */
@BenchmarkMode({Mode.Throughput, Mode.AverageTime})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 5, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@State(Scope.Thread)
public class LightweightCachedAkOutputVisitorUtilsAllSqlBenchmark {

    private Integer dbType;
    private String[] allSqls;

    @Setup
    public void setup() {
        this.dbType = AkDbTypeEnum.PostgreSQL.getTypeId();
        this.allSqls = new String[] {
"""

for idx, sql in enumerate(lines, 1):
    escaped = escape_java(sql)
    java += f'            /* {idx} */ "{escaped}",\n'

java += """        };

        // 预热：全量过一次确保缓存
        for (String sql : allSqls) {
            LightweightCachedAkOutputVisitorUtils.getSqlTemplate_v2(sql, dbType);
        }
    }

    /**
     * 混合场景：所有 100 条 SQL 依次执行（缓存全命中）
     */
    @Benchmark
    public void mixedScenario(Blackhole bh) {
        for (String sql : allSqls) {
            bh.consume(LightweightCachedAkOutputVisitorUtils.getSqlTemplate_v2(sql, dbType));
        }
    }

    /**
     * 单场景：仅 INSERT 组
     */
    @Benchmark
    public void insertOnly(Blackhole bh) {
"""

for idx, sql in inserts:
    escaped = escape_java(sql)
    java += f'        bh.consume(LightweightCachedAkOutputVisitorUtils.getSqlTemplate_v2("{escaped}", dbType));\n'

java += """    }

    /**
     * 单场景：仅 UPDATE 组
     */
    @Benchmark
    public void updateOnly(Blackhole bh) {
"""

for idx, sql in updates:
    escaped = escape_java(sql)
    java += f'        bh.consume(LightweightCachedAkOutputVisitorUtils.getSqlTemplate_v2("{escaped}", dbType));\n'

java += """    }

    /**
     * 单场景：仅 SELECT 组
     */
    @Benchmark
    public void selectOnly(Blackhole bh) {
"""

for idx, sql in selects:
    escaped = escape_java(sql)
    java += f'        bh.consume(LightweightCachedAkOutputVisitorUtils.getSqlTemplate_v2("{escaped}", dbType));\n'

java += """    }

    /**
     * 单场景：仅 BEGIN;INSERT 组
     */
    @Benchmark
    public void beginOnly(Blackhole bh) {
"""

for idx, sql in begins:
    escaped = escape_java(sql)
    java += f'        bh.consume(LightweightCachedAkOutputVisitorUtils.getSqlTemplate_v2("{escaped}", dbType));\n'

java += """    }

    public static void main(String[] args) throws Exception {
        org.openjdk.jmh.runner.options.Options opt = new org.openjdk.jmh.runner.options.OptionsBuilder()
                .include(LightweightCachedAkOutputVisitorUtilsAllSqlBenchmark.class.getSimpleName())
                .build();
        new org.openjdk.jmh.runner.Runner(opt).run();
    }
}
"""

with open("../druid-ak/com/ankki/druid/parser/utils/LightweightCachedAkOutputVisitorUtilsAllSqlBenchmark.java", "w", encoding="utf-8") as f:
    f.write(java)

print(f"Generated: {len(lines)} SQLs embedded")
print(f"  INSERT: {len(inserts)}, UPDATE: {len(updates)}, SELECT: {len(selects)}, BEGIN: {len(begins)}")
print(f"File size: {len(java)} chars")
