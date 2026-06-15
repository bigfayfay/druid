#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
JMH 性能测试报告生成器
解析 JMH 测试结果并生成 HTML 报告
"""

import re
import json
import argparse
from datetime import datetime
from pathlib import Path


class JMHResultParser:
    """解析 JMH 测试结果"""

    def __init__(self, result_file):
        self.result_file = result_file
        self.results = {}
        self.metadata = {}

    def parse(self):
        """解析 JMH 输出文件"""
        with open(self.result_file, 'r', encoding='utf-8', errors='ignore') as f:
            content = f.read()

        # 解析元数据
        metadata_patterns = {
            'jmh_version': r'# JMH version: (.+)',
            'vm_version': r'# VM version: (.+)',
            'warmup': r'# Warmup: (.+)',
            'measurement': r'# Measurement: (.+)',
            'timeout': r'# Timeout: (.+)',
            'threads': r'# Threads: (.+)',
        }

        for key, pattern in metadata_patterns.items():
            match = re.search(pattern, content)
            if match:
                self.metadata[key] = match.group(1)

        # 解析基准测试结果
        # 查找结果行格式: Benchmark Mode Cnt Score Error Units
        result_pattern = r'(\w+(?:\.\w+)*)\s+(\w+)\s+(\d+)\s+([\d.]+)\s+±([\d.]+)\s+(\w+/op)'

        for match in re.finditer(result_pattern, content):
            benchmark, mode, cnt, score, error, units = match.groups()
            self.results[benchmark] = {
                'mode': mode,
                'count': int(cnt),
                'score': float(score),
                'error': float(error),
                'units': units,
            }

        return self.results, self.metadata


class ReportGenerator:
    """生成 HTML 报告"""

    def __init__(self, results, metadata):
        self.results = results
        self.metadata = metadata

    def generate_html(self, output_file):
        """生成 HTML 报告"""

        html_template = """<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>JMH 性能测试报告</title>
    <style>
        * {{
            margin: 0;
            padding: 0;
            box-sizing: border-box;
        }}

        body {{
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            padding: 20px;
            line-height: 1.6;
        }}

        .container {{
            max-width: 1200px;
            margin: 0 auto;
            background: white;
            border-radius: 12px;
            box-shadow: 0 20px 60px rgba(0,0,0,0.3);
            overflow: hidden;
        }}

        .header {{
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            color: white;
            padding: 40px;
            text-align: center;
        }}

        .header h1 {{
            font-size: 2.5em;
            margin-bottom: 10px;
        }}

        .header p {{
            opacity: 0.9;
            font-size: 1.1em;
        }}

        .content {{
            padding: 40px;
        }}

        .section {{
            margin-bottom: 40px;
        }}

        .section h2 {{
            color: #333;
            border-bottom: 3px solid #667eea;
            padding-bottom: 10px;
            margin-bottom: 20px;
        }}

        .metadata-grid {{
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
            gap: 20px;
        }}

        .metadata-item {{
            background: #f8f9fa;
            padding: 15px;
            border-radius: 8px;
            border-left: 4px solid #667eea;
        }}

        .metadata-label {{
            font-weight: bold;
            color: #555;
            font-size: 0.9em;
        }}

        .metadata-value {{
            color: #333;
            font-size: 1.1em;
            margin-top: 5px;
        }}

        table {{
            width: 100%;
            border-collapse: collapse;
            margin-top: 20px;
        }}

        table th {{
            background: #667eea;
            color: white;
            padding: 15px;
            text-align: left;
            font-weight: 600;
        }}

        table td {{
            padding: 15px;
            border-bottom: 1px solid #eee;
        }}

        table tr:hover {{
            background: #f8f9fa;
        }}

        .score {{
            font-weight: bold;
            color: #28a745;
        }}

        .error {{
            color: #dc3545;
            font-size: 0.9em;
        }}

        .benchmark-name {{
            font-family: "Courier New", monospace;
            background: #f0f0f0;
            padding: 2px 6px;
            border-radius: 4px;
        }}

        .summary {{
            background: linear-gradient(135deg, #11998e 0%, #38ef7d 100%);
            color: white;
            padding: 25px;
            border-radius: 8px;
            margin-bottom: 30px;
        }}

        .summary h3 {{
            margin-bottom: 15px;
        }}

        .footer {{
            background: #f8f9fa;
            padding: 20px;
            text-align: center;
            color: #666;
            font-size: 0.9em;
        }}

        .no-results {{
            text-align: center;
            padding: 40px;
            color: #999;
            font-size: 1.2em;
        }}
    </style>
</head>
<body>
    <div class="container">
        <div class="header">
            <h1>🚀 JMH 性能测试报告</h1>
            <p>生成时间: {timestamp}</p>
        </div>

        <div class="content">
            {summary}

            <div class="section">
                <h2>📋 测试环境</h2>
                <div class="metadata-grid">
                    {metadata}
                </div>
            </div>

            <div class="section">
                <h2>📊 测试结果</h2>
                {results_table}
            </div>

            <div class="section">
                <h2>💡 性能分析</h2>
                <div class="summary">
                    <h3>总体评估</h3>
                    {analysis}
                </div>
            </div>
        </div>

        <div class="footer">
            <p>由 JMH Benchmark 生成 | 测试方法: CustomerOutputVisitorUtils.getSqlTemplate_v2()</p>
        </div>
    </div>
</body>
</html>"""

        timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S")

        # 生成摘要
        total_tests = len(self.results)
        avg_score = sum(r['score'] for r in self.results.values()) / total_tests if total_tests > 0 else 0

        summary = f"""
        <div class="summary">
            <h3>测试摘要</h3>
            <p>✅ 完成 <strong>{total_tests}</strong> 个基准测试</p>
            <p>📈 平均性能: <strong>{avg_score:.2f} ns/op</strong></p>
        </div>
        """

        # 生成元数据
        metadata_html = ""
        metadata_display = {
            'jmh_version': 'JMH 版本',
            'vm_version': 'JVM 版本',
            'warmup': '预热配置',
            'measurement': '测量配置',
            'timeout': '超时设置',
            'threads': '线程数'
        }

        for key, label in metadata_display.items():
            value = self.metadata.get(key, 'N/A')
            metadata_html += f"""
            <div class="metadata-item">
                <div class="metadata-label">{label}</div>
                <div class="metadata-value">{value}</div>
            </div>
            """

        # 生成结果表格
        if self.results:
            results_table = """
            <table>
                <thead>
                    <tr>
                        <th>基准测试</th>
                        <th>模式</th>
                        <th>轮次</th>
                        <th>性能分数</th>
                        <th>误差</th>
                        <th>单位</th>
                    </tr>
                </thead>
                <tbody>
            """

            for name, result in self.results.items():
                results_table += f"""
                <tr>
                    <td><span class="benchmark-name">{name}</span></td>
                    <td>{result['mode']}</td>
                    <td>{result['count']}</td>
                    <td class="score">{result['score']:.3f}</td>
                    <td class="error">±{result['error']:.3f}</td>
                    <td>{result['units']}</td>
                </tr>
                """

            results_table += """
                </tbody>
            </table>
            """
        else:
            results_table = '<div class="no-results">暂无测试结果</div>'

        # 生成分析
        if self.results:
            best = min(self.results.items(), key=lambda x: x[1]['score'])
            worst = max(self.results.items(), key=lambda x: x[1]['score'])

            analysis = f"""
            <ul>
                <li>🏆 最佳性能: <strong>{best[0]}</strong> ({best[1]['score']:.2f} ns/op)</li>
                <li>⚠️ 需要关注: <strong>{worst[0]}</strong> ({worst[1]['score']:.2f} ns/op)</li>
                <li>📊 性能波动: {((worst[1]['score'] - best[1]['score']) / best[1]['score'] * 100):.1f}%</li>
            </ul>
            """
        else:
            analysis = "<p>暂无足够数据进行分析</p>"

        # 填充模板
        html = html_template.format(
            timestamp=timestamp,
            summary=summary,
            metadata=metadata_html,
            results_table=results_table,
            analysis=analysis
        )

        # 写入文件
        with open(output_file, 'w', encoding='utf-8') as f:
            f.write(html)

        print(f"✅ 报告已生成: {output_file}")

    def generate_markdown(self, output_file):
        """生成 Markdown 报告"""
        lines = [
            "# JMH 性能测试报告",
            "",
            f"**生成时间**: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}",
            "",
            "## 测试环境",
            ""
        ]

        for key, value in self.metadata.items():
            lines.append(f"- **{key}**: {value}")

        lines.extend([
            "",
            "## 测试结果",
            "",
            "| 基准测试 | 模式 | 轮次 | 性能分数 | 误差 | 单位 |",
            "|---------|------|------|----------|------|------|",
        ])

        for name, result in self.results.items():
            lines.append(f"| {name} | {result['mode']} | {result['count']} | {result['score']:.3f} | ±{result['error']:.3f} | {result['units']} |")

        with open(output_file, 'w', encoding='utf-8') as f:
            f.write('\n'.join(lines))

        print(f"✅ Markdown 报告已生成: {output_file}")


def main():
    parser = argparse.ArgumentParser(description='JMH 性能测试报告生成器')
    parser.add_argument('--input', '-i', required=True, help='JMH 测试结果文件')
    parser.add_argument('--output', '-o', default='benchmark_report.html', help='输出报告文件')
    parser.add_argument('--format', '-f', choices=['html', 'md', 'both'], default='both', help='报告格式')

    args = parser.parse_args()

    # 解析结果
    print(f"📖 正在解析 JMH 结果: {args.input}")
    jmh_parser = JMHResultParser(args.input)
    results, metadata = jmh_parser.parse()

    print(f"✅ 解析完成: 找到 {len(results)} 个基准测试结果")

    # 生成报告
    generator = ReportGenerator(results, metadata)

    if args.format in ['html', 'both']:
        output_html = args.output if args.format == 'html' else args.output.replace('.md', '.html')
        generator.generate_html(output_html)

    if args.format in ['md', 'both']:
        output_md = args.output if args.format == 'md' else args.output.replace('.html', '.md')
        generator.generate_markdown(output_md)


if __name__ == '__main__':
    main()
