#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""分析 smples.sql 文件中的 SQL 样例"""

import re
import json
from collections import Counter

def extract_table_name(sql):
    """提取表名"""
    # 匹配 INSERT INTO table_name 或 INSERT INTO schema.table_name
    match = re.search(r'INSERT INTO\s+([\w.]+)', sql, re.IGNORECASE)
    if match:
        return match.group(1)
    return None

def normalize_table_name(table_name):
    """归一化表名（去除日期后缀）"""
    # 去除日期后缀，如 _20260512
    normalized = re.sub(r'_\d{8}$', '', table_name)
    return normalized

def analyze_sql_file(filepath, max_samples=100):
    """分析 SQL 文件"""
    print(f"分析文件: {filepath}\n")
    print("=" * 100)

    # 读取文件（假设每行一个 SQL）
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()

    # 分割 SQL 语句（根据 " 分隔）
    sqls = content.split('"\n"')
    print(f"总共 {len(sqls)} 条 SQL 语句\n")

    # 统计表名
    table_counter = Counter()
    normalized_table_counter = Counter()
    sql_templates = {}

    sample_count = 0
    for sql in sqls[:max_samples]:
        sql = sql.strip('"')
        if not sql:
            continue

        sample_count += 1

        # 提取表名
        table_name = extract_table_name(sql)
        if table_name:
            table_counter[table_name] += 1

            # 归一化表名
            normalized = normalize_table_name(table_name)
            normalized_table_counter[normalized] += 1

            # 提取 SQL 模板（使用占位符）
            # 将所有数字替换为 ?
            template = re.sub(r'\d+', '?', sql)
            # 将所有字符串字面量替换为 ?
            template = re.sub(r"'[^']*'", '?', template)
            # 将所有 ::text 等类型转换简化
            template = re.sub(r'::[\w\s]+(?:\([^)]*\))?', '', template)

            if normalized not in sql_templates:
                sql_templates[normalized] = {
                    'original_table': table_name,
                    'template': template[:500],  # 只保存前 500 字符
                    'count': 0
                }
            sql_templates[normalized]['count'] += 1

    # 输出统计结果
    print(f"【表名统计】TOP 20（按原始表名）\n")
    for table, count in table_counter.most_common(20):
        print(f"  {count:5d} 次: {table}")

    print(f"\n【表名统计】TOP 20（按归一化表名，去除日期后缀）\n")
    for table, count in normalized_table_counter.most_common(20):
        print(f"  {count:5d} 次: {table} (如 {table}_20260512)")

    print(f"\n【SQL 模板统计】TOP 10\n")
    sorted_templates = sorted(sql_templates.items(), key=lambda x: x[1]['count'], reverse=True)
    for i, (normalized, info) in enumerate(sorted_templates[:10], 1):
        print(f"\n【模板 {i}】出现 {info['count']} 次")
        print(f"  原始表名: {info['original_table']}")
        print(f"  归一化表名: {normalized}")
        print(f"  模板预览: {info['template'][:200]}...")

    # 分析关键发现
    print(f"\n\n【关键发现】\n")
    print(f"1. 分析了 {sample_count} 条 SQL 语句")
    print(f"2. 共有 {len(table_counter)} 个不同的原始表名")
    print(f"3. 共有 {len(normalized_table_counter)} 个归一化表名（去除日期后缀）")
    print(f"4. 表名模式：大部分表名带日期后缀，如 _20260512")

    if len(normalized_table_counter) < len(table_counter):
        print(f"\n✓ 确认：表名带日期后缀导致缓存命中率降低")
        print(f"  建议：归一化表名后再生成缓存键，可以提升缓存命中率")

    # 保存分析结果
    result = {
        'total_sqls': sample_count,
        'unique_tables': len(table_counter),
        'normalized_tables': len(normalized_table_counter),
        'top_tables': dict(table_counter.most_common(20)),
        'top_normalized_tables': dict(normalized_table_counter.most_common(20)),
        'templates': {k: {'count': v['count'], 'template': v['template'][:200]}
                      for k, v in list(sorted_templates[:10])}
    }

    with open('sql_analysis_result.json', 'w', encoding='utf-8') as f:
        json.dump(result, f, ensure_ascii=False, indent=2)
    print(f"\n分析结果已保存到: sql_analysis_result.json")

if __name__ == '__main__':
    analyze_sql_file(r'f:\2026年\03-现场项目\AAS-B07\02-中广电移动网络\smples.sql', max_samples=500)
