#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""从 ClickHouse 数据库获取 SQL 语句样例（使用 HTTP 接口）"""

import requests
import json

# 数据库连接配置
DB_HOST = '172.19.4.41'
DB_PORT = 8123
DB_USER = 'root'
DB_PASSWORD = 'Ankki_cK123'
DB_DATABASE = 'bs_audit'

def get_sql_samples(limit=20):
    """获取 SQL 语句样例"""
    try:
        # 构造查询 - 简化查询，只获取最常用的几条
        query = f"""
        SELECT
            operSentence,
            length(operSentence) as sql_length
        FROM audit_record
        WHERE operSentence != '' AND length(operSentence) < 1000
        LIMIT {limit}
        FORMAT JSON
        """

        # 构造 URL
        url = f'http://{DB_HOST}:{DB_PORT}/'

        # 发送请求
        print(f"连接到 ClickHouse: {DB_HOST}:{DB_PORT}/{DB_DATABASE}")
        print("正在查询 SQL 语句样例...\n")

        params = {
            'user': DB_USER,
            'password': DB_PASSWORD,
            'database': DB_DATABASE,
            'query': query
        }

        response = requests.post(url, params=params, timeout=120)

        if response.status_code == 200:
            data = response.json()
            rows = data.get('data', [])

            print(f"获取到 {len(rows)} 条 SQL 语句样例：\n")
            print("=" * 100)

            samples = []
            for i, row in enumerate(rows, 1):
                sql = row.get('operSentence', '')
                sql_len = row.get('sql_length', 0)

                print(f"\n【样例 {i}】长度: {sql_len}")
                print("-" * 100)
                print(sql)
                print()

                samples.append({
                    'index': i,
                    'length': sql_len,
                    'sql': sql
                })

            # 保存到文件
            output_file = 'sql_samples_analysis.json'
            with open(output_file, 'w', encoding='utf-8') as f:
                json.dump(samples, f, ensure_ascii=False, indent=2)
            print(f"\n样例已保存到: {output_file}")

            return samples
        else:
            print(f"查询失败: HTTP {response.status_code}")
            print(response.text[:500])
            return []

    except Exception as e:
        print(f"错误: {e}")
        import traceback
        traceback.print_exc()
        return []

if __name__ == '__main__':
    get_sql_samples(20)
