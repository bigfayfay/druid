#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
JMH Performance Test Automation using Paramiko
Fully automated SSH test execution
"""

import paramiko
import os
import sys
import time
from datetime import datetime

# Configuration
SERVER = "172.19.4.41"
USER = "root"
PASSWORD = "@1fw#2soc$3vpn"
JAR_FILE = "druid-ak-1.2.27-jar-with-dependencies.jar"
REMOTE_PATH = f"/root/{JAR_FILE}"
RESULT_FILE = "/root/jmh_results.txt"

# Create output directory with timestamp
def create_output_dir():
    """Create output directory with timestamp"""
    base_dir = "../性能测试结果"
    timestamp = datetime.now().strftime("%Y%m%d%H%M")
    output_dir = os.path.join(base_dir, timestamp)

    if not os.path.exists(output_dir):
        os.makedirs(output_dir)

    return output_dir

def print_header(text):
    print("\n" + "=" * 60)
    print(text)
    print("=" * 60)

def print_step(step_num, total, text):
    print(f"\n[{step_num}/{total}] {text}")

def print_success(text):
    print(f"[OK] {text}")

def print_error(text):
    print(f"[ERROR] {text}")

def upload_file(sftp, local_path, remote_path):
    """Upload file via SFTP"""
    try:
        sftp.put(local_path, remote_path)
        return True
    except Exception as e:
        print_error(f"Upload failed: {e}")
        return False

def execute_command(ssh, command, timeout=300):
    """Execute command via SSH"""
    try:
        stdin, stdout, stderr = ssh.exec_command(command, timeout=timeout)
        exit_status = stdout.channel.recv_exit_status()
        output = stdout.read().decode('utf-8', errors='ignore')
        error = stderr.read().decode('utf-8', errors='ignore')
        return exit_status, output, error
    except Exception as e:
        return -1, "", str(e)

def download_file(sftp, remote_path, local_path):
    """Download file via SFTP"""
    try:
        sftp.get(remote_path, local_path)
        return True
    except Exception as e:
        print_error(f"Download failed: {e}")
        return False

def generate_report(result_file, html_file, md_file):
    """Generate HTML and Markdown reports"""
    try:
        with open(result_file, 'r', encoding='utf-8', errors='ignore') as f:
            content = f.read()

        # Parse results
        import re
        metadata = {}
        results = {}

        patterns = {
            'jmh_version': r'# JMH version: (.+)',
            'vm_version': r'# VM version: (.+)',
            'warmup': r'# Warmup: (.+)',
            'measurement': r'# Measurement: (.+)',
            'benchmark_mode': r'# Benchmark mode: (.+)',
        }

        for key, pattern in patterns.items():
            match = re.search(pattern, content)
            if match:
                metadata[key] = match.group(1)

        # Enhanced pattern to match both Throughput and AverageTime results
        # Also handle scientific notation like "≈ 10⁻⁵ ops/ns"
        result_pattern = r'(\w+(?:\.\w+)*)\s+(\w+)\s+(\d+)\s+([\d.±≈⁻]+)\s+([±]?\s*[\d.]+)?\s+(\S+)'

        for match in re.finditer(result_pattern, content):
            name, mode, cnt, score_str, error_str, units = match.groups()

            # Handle scientific notation
            score_str = score_str.replace('≈', '').replace('⁻', 'e-').replace('⁺', 'e+').strip()
            if error_str:
                error_str = error_str.replace('±', '').strip()

            # Convert units for better display
            if 'ops/ns' in units:
                # Convert ops/ns to ops/ms (multiply by 1000000)
                try:
                    score_val = float(score_str.replace('10', '1e-5'))  # Handle 10⁻⁵ format
                    score_val = score_val * 1000000  # Convert to ops/ms
                    units = 'ops/ms'
                except:
                    score_val = 0.0
                error_val = 0.0
            elif 'ns/op' in units:
                # Convert ns/op to ms/op (divide by 1000000)
                try:
                    score_val = float(score_str) / 1000000
                    units = 'ms/op'
                except:
                    score_val = 0.0
                try:
                    error_val = float(error_str) / 1000000 if error_str else 0.0
                except:
                    error_val = 0.0
            else:
                try:
                    score_val = float(score_str)
                except:
                    score_val = 0.0
                try:
                    error_val = float(error_str) if error_str else 0.0
                except:
                    error_val = 0.0

            # Store results with mode suffix to distinguish between modes
            result_key = f"{name}_{mode}" if mode else name
            results[result_key] = {
                'name': name,
                'mode': mode,
                'count': int(cnt),
                'score': score_val,
                'error': error_val,
                'units': units,
            }

        # Generate HTML report
        timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S")

        # Extract key metrics
        throughput_result = None
        avg_time_result = None

        for key, result in results.items():
            if result['mode'] == 'thrpt':
                throughput_result = result
            elif result['mode'] == 'avgt':
                avg_time_result = result

        html_content = """<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>JMH 性能测试报告</title>
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body {
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            padding: 20px;
            line-height: 1.6;
        }
        .container {
            max-width: 1000px;
            margin: 0 auto;
            background: white;
            border-radius: 12px;
            box-shadow: 0 20px 60px rgba(0,0,0,0.3);
            overflow: hidden;
        }
        .header {
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            color: white;
            padding: 40px;
            text-align: center;
        }
        .header h1 { font-size: 2.5em; margin-bottom: 10px; }
        .content { padding: 40px; }
        .section { margin-bottom: 40px; }
        .section h2 {
            color: #333;
            border-bottom: 3px solid #667eea;
            padding-bottom: 10px;
            margin-bottom: 20px;
        }
        .metadata-grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
            gap: 20px;
        }
        .metadata-item {
            background: #f8f9fa;
            padding: 15px;
            border-radius: 8px;
            border-left: 4px solid #667eea;
        }
        .metadata-label { font-weight: bold; color: #555; font-size: 0.9em; }
        .metadata-value { color: #333; font-size: 1.1em; margin-top: 5px; }
        table { width: 100%; border-collapse: collapse; margin-top: 20px; }
        table th {
            background: #667eea;
            color: white;
            padding: 15px;
            text-align: left;
            font-weight: 600;
        }
        table td { padding: 15px; border-bottom: 1px solid #eee; }
        table tr:hover { background: #f8f9fa; }
        .score { font-weight: bold; color: #28a745; }
        .error { color: #dc3545; font-size: 0.9em; }
        .benchmark-name {
            font-family: "Courier New", monospace;
            background: #f0f0f0;
            padding: 2px 6px;
            border-radius: 4px;
        }
        .summary {
            background: linear-gradient(135deg, #11998e 0%, #38ef7d 100%);
            color: white;
            padding: 25px;
            border-radius: 8px;
            margin-bottom: 30px;
        }
        .metric {
            display: inline-block;
            margin: 10px 20px;
            text-align: center;
        }
        .metric-value {
            font-size: 2em;
            font-weight: bold;
        }
        .metric-label {
            font-size: 0.9em;
            opacity: 0.9;
        }
        .footer {
            background: #f8f9fa;
            padding: 20px;
            text-align: center;
            color: #666;
            font-size: 0.9em;
        }
        .mode-badge {
            display: inline-block;
            padding: 4px 8px;
            border-radius: 4px;
            font-size: 0.85em;
            font-weight: bold;
        }
        .mode-thrpt { background: #28a745; color: white; }
        .mode-avgt { background: #007bff; color: white; }
    </style>
</head>
<body>
    <div class="container">
        <div class="header">
            <h1>JMH 性能测试报告</h1>
            <p>生成时间: {}</p>
        </div>

        <div class="content">
            <div class="summary">
                <h3>性能摘要</h3>
                <div class="metric">
                    <div class="metric-value">{:.2f}</div>
                    <div class="metric-label">吞吐量 (ops/ms)</div>
                </div>
                <div class="metric">
                    <div class="metric-value">{:.3f}</div>
                    <div class="metric-label">平均耗时 (ms/op)</div>
                </div>
                <div class="metric">
                    <div class="metric-value">{}</div>
                    <div class="metric-label">测试模式</div>
                </div>
            </div>

            <div class="section">
                <h2>测试环境</h2>
                <div class="metadata-grid">{}</div>
            </div>

            <div class="section">
                <h2>测试结果</h2>
                <table>
                    <thead>
                        <tr>
                            <th>测试方法</th>
                            <th>模式</th>
                            <th>轮次</th>
                            <th>性能分数</th>
                            <th>误差</th>
                            <th>单位</th>
                        </tr>
                    </thead>
                    <tbody>{}</tbody>
                </table>
            </div>
        </div>

        <div class="footer">
            <p>JMH 基准测试 | 测试方法: CustomerOutputVisitorUtils.getSqlTemplate_v2()</p>
        </div>
    </div>
</body>
</html>""".format(
            timestamp,
            throughput_result['score'] if throughput_result else 0,
            avg_time_result['score'] if avg_time_result else 0,
            len(set(r['mode'] for r in results.values())),
            ''.join('<div class="metadata-item"><div class="metadata-label">{}</div><div class="metadata-value">{}</div></div>'.format(k, v) for k, v in {
                'JMH 版本': metadata.get('jmh_version', 'N/A'),
                'JVM 版本': metadata.get('vm_version', 'N/A'),
                '预热': metadata.get('warmup', 'N/A'),
                '测量': metadata.get('measurement', 'N/A'),
                '测试模式': metadata.get('benchmark_mode', 'N/A'),
            }.items()),
            ''.join('<tr><td><span class="benchmark-name">{}</span></td><td><span class="mode-badge mode-{}">{}</span></td><td>{}</td><td class="score">{:.3f}</td><td class="error">±{:.3f}</td><td>{}</td></tr>'.format(
                r['name'], r['mode'], r['mode'].upper(), r['count'], r['score'], r['error'], r['units']
            ) for r in results.values())
        )

        with open(html_file, 'w', encoding='utf-8') as f:
            f.write(html_content)

        # Generate Markdown report
        md_content = """# JMH 性能测试报告

**生成时间**: {}

---

## 性能摘要

| 指标 | 数值 |
|------|------|
| **吞吐量** | {:.2f} ops/ms |
| **平均耗时** | {:.3f} ms/op |
| **测试模式数** | {} |

---

## 测试环境

""".format(
            timestamp,
            throughput_result['score'] if throughput_result else 0,
            avg_time_result['score'] if avg_time_result else 0,
            len(set(r['mode'] for r in results.values()))
        )

        for key, value in metadata.items():
            md_content += "- **{}**: {}\n".format(key, value)

        md_content += "\n## 测试结果\n\n"
        md_content += "| 测试方法 | 模式 | 轮次 | 性能分数 | 误差 | 单位 |\n"
        md_content += "|---------|------|------|----------|------|------|\n"

        for result in results.values():
            mode_display = {
                'thrpt': '吞吐量',
                'avgt': '平均时间'
            }.get(result['mode'], result['mode'].upper())

            md_content += "| {} | {} | {} | {:.3f} | ±{:.3f} | {} |\n".format(
                result['name'], mode_display, result['count'], result['score'], result['error'], result['units']
            )

        md_content += "\n## 性能分析\n\n"

        if throughput_result:
            md_content += "### 吞吐量\n\n"
            md_content += "- **性能**: {:.2f} ± {:.3f} ops/ms\n".format(
                throughput_result['score'], throughput_result['error']
            )
            md_content += "- **每秒操作数**: 约 {:.0f} ops/s\n\n".format(
                throughput_result['score'] * 1000
            )

        if avg_time_result:
            md_content += "### 平均耗时\n\n"
            md_content += "- **性能**: {:.3f} ± {:.3f} ms/op\n".format(
                avg_time_result['score'], avg_time_result['error']
            )
            md_content += "- **换算**: 约 {:.0f} ns/op\n\n".format(
                avg_time_result['score'] * 1000000
            )

        md_content += "---\n\n**报告生成时间**: {}\n".format(timestamp)

        with open(md_file, 'w', encoding='utf-8') as f:
            f.write(md_content)

        print_success("报告生成成功!")

    except Exception as e:
        print_error(f"Failed to generate reports: {e}")

def main():
    print_header("JMH Performance Test Automation")

    print(f"Server: {SERVER}")
    print(f"User: {USER}")
    print(f"JAR File: {JAR_FILE}")

    # Check local file
    local_jar = os.path.join("..", JAR_FILE)
    if not os.path.exists(local_jar):
        print_error(f"JAR file not found: {local_jar}")
        return 1

    file_size = os.path.getsize(local_jar) / (1024 * 1024)
    print(f"JAR file size: {file_size:.2f} MB")

    # Create output directory
    output_dir = create_output_dir()
    print(f"Output directory: {output_dir}")

    try:
        # Connect to server
        print_step(0, 5, "Connecting to server...")
        ssh = paramiko.SSHClient()
        ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())

        try:
            ssh.connect(SERVER, username=USER, password=PASSWORD, timeout=30)
            print_success("Connected!")
        except Exception as e:
            print_error(f"Connection failed: {e}")
            return 1

        sftp = ssh.open_sftp()

        # Step 1: Upload JAR file
        print_step(1, 5, "Uploading JAR file...")
        if not upload_file(sftp, local_jar, REMOTE_PATH):
            return 1
        print_success("Upload complete!")

        # Step 2: Check Java
        print_step(2, 5, "Checking Java environment...")
        exit_code, output, error = execute_command(ssh, "java -version 2>&1 | head -1")
        if exit_code == 0:
            java_version = output.strip()
            print_success(f"Java: {java_version}")
        else:
            print_error("Java not found")
            return 1

        # Step 3: Run JMH test
        print_step(3, 5, "Running JMH benchmark test...")
        print("(This will take 2-3 minutes for multiple modes, please wait...)")

        # Run with AverageTime mode only (same config as previous successful runs)
        # 36 benchmarks × (3×1s warmup + 5×10s measure) ≈ 32 minutes
        test_command = f'cd /root && java -cp {REMOTE_PATH} org.openjdk.jmh.Main -bm avgt -wi 3 -i 5 "SqlTemplateComparisonBenchmark" 2>&1 | tee {RESULT_FILE}'

        start_time = time.time()
        exit_code, output, error = execute_command(ssh, test_command, timeout=2400)
        elapsed = time.time() - start_time

        if exit_code == 0:
            print_success(f"Test complete! (took {elapsed:.1f} seconds)")
        else:
            print(f"Test completed with exit code: {exit_code}")

        # Step 4: Download results
        print_step(4, 5, "Downloading test results...")
        local_result = os.path.join(output_dir, "jmh_results.txt")

        # Wait a moment for file to be written
        time.sleep(2)

        if download_file(sftp, RESULT_FILE, local_result):
            print_success("Download complete!")
        else:
            print("Warning: Could not download results")

        # Step 5: Generate report
        print_step(5, 5, "Generating report...")

        if os.path.exists(local_result):
            report_html = os.path.join(output_dir, "report.html")
            report_md = os.path.join(output_dir, "report.md")

            # Generate reports directly
            generate_report(local_result, report_html, report_md)

            if os.path.exists(report_html):
                print_success("Report generated!")

                print_header("Test Complete!")

                print(f"\nOutput directory: {output_dir}")
                print("\nGenerated files:")
                print(f"  - {os.path.basename(local_result)} (test results)")
                print(f"  - {os.path.basename(report_html)} (HTML report)")
                print(f"  - {os.path.basename(report_md)} (Markdown report)")

                # Parse and show summary
                print("\nPerformance Test Summary:")
                print("-" * 60)

                with open(local_result, 'r', encoding='utf-8', errors='ignore') as f:
                    lines = f.readlines()
                    for line in lines:
                        if any(keyword in line for keyword in ['Benchmark', 'Mode', 'Score', 'ns/op', 'Units']):
                            print(line.strip())

                print("-" * 60)

                # Try to extract specific metrics
                for line in lines:
                    if 'testGetSqlTemplate_v2' in line and 'avgt' in line:
                        parts = line.split()
                        if len(parts) >= 5:
                            print(f"\nKey Metric: {parts[4]} ns/op")
                            if len(parts) >= 7:
                                print(f"Error Margin: {parts[6]}")
                            break
        else:
            print_error("Results file not found")

        # Close connections
        sftp.close()
        ssh.close()

        print("\nTest execution complete!")
        return 0

    except KeyboardInterrupt:
        print("\n\nTest interrupted by user")
        return 1
    except Exception as e:
        print_error(f"Unexpected error: {e}")
        import traceback
        traceback.print_exc()
        return 1

if __name__ == "__main__":
    try:
        sys.exit(main())
    except Exception as e:
        print(f"Fatal error: {e}")
        sys.exit(1)
