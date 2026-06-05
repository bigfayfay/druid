#!/usr/bin/env python3
"""
对比原始版本和缓存版本的性能
"""
import subprocess
import paramiko
import os
from datetime import datetime

# 配置
SERVER = "172.19.4.41"
USER = "root"
PASSWORD = "@1fw#2soc$3vpn"
JAR_FILE = "druid-ak-1.2.27-jar-with-dependencies.jar"
OUTPUT_DIR = f"../性能测试结果/{datetime.now().strftime('%Y%m%d%H%M')}"

# 创建输出目录
os.makedirs(OUTPUT_DIR, exist_ok=True)

def ssh_command(ssh, command):
    """执行SSH命令"""
    stdin, stdout, stderr = ssh.exec_command(command)
    output = stdout.read().decode('utf-8')
    error = stderr.read().decode('utf-8')
    return output, error

def main():
    print("=" * 60)
    print("性能对比测试 - 原始版本 vs 缓存版本")
    print("=" * 60)

    # 连接服务器
    print(f"\n[1/6] 连接服务器 {SERVER}...")
    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    ssh.connect(SERVER, username=USER, password=PASSWORD)
    print("[OK] 已连接")

    # 上传JAR文件
    print(f"\n[2/6] 上传JAR文件...")
    sftp = ssh.open_sftp()
    sftp.put(f"../{JAR_FILE}", f"/root/{JAR_FILE}")
    sftp.close()
    print("[OK] 上传完成")

    # 测试原始版本
    print(f"\n[3/6] 运行原始版本测试...")
    print("(约2分钟，请耐心等待...)")
    cmd = f"cd /root && java -cp {JAR_FILE} org.openjdk.jmh.Main '.*CustomerOutputVisitorUtilsBenchmark.*' 2>&1 | tee /root/original_results.txt"
    ssh_command(ssh, cmd)
    print("[OK] 原始版本测试完成")

    # 测试缓存版本
    print(f"\n[4/6] 运行缓存版本测试...")
    print("(约2分钟，请耐心等待...)")
    cmd = f"cd /root && java -cp {JAR_FILE} org.openjdk.jmh.Main '.*EnhancedCachedBenchmark.*' 2>&1 | tee /root/cached_results.txt"
    ssh_command(ssh, cmd)
    print("[OK] 缓存版本测试完成")

    # 下载结果
    print(f"\n[5/6] 下载测试结果...")
    sftp = ssh.open_sftp()
    sftp.get("/root/original_results.txt", f"{OUTPUT_DIR}/original_results.txt")
    sftp.get("/root/cached_results.txt", f"{OUTPUT_DIR}/cached_results.txt")
    sftp.close()
    print("[OK] 下载完成")

    # 生成对比报告
    print(f"\n[6/6] 生成对比报告...")
    generate_report(OUTPUT_DIR)
    print("[OK] 报告生成完成")

    ssh.close()

    print(f"\n{'='*60}")
    print(f"测试完成！结果保存在: {OUTPUT_DIR}")
    print("=" * 60)

def generate_report(output_dir):
    """生成对比报告"""
    # 读取结果
    with open(f"{output_dir}/original_results.txt", "r", encoding="utf-8") as f:
        original_text = f.read()
    with open(f"{output_dir}/cached_results.txt", "r", encoding="utf-8") as f:
        cached_text = f.read()

    # 提取平均时间
    import re
    original_match = re.search(r'([\d.]+) ±[\d.]+ ns/op.*?Average', original_text)
    cached_match = re.search(r'([\d.]+) ±[\d.]+ ns/op.*?Average', cached_text)

    original_time = float(original_match.group(1)) if original_match else 0
    cached_time = float(cached_match.group(1)) if cached_match else 0

    # 计算提升
    if cached_time > 0:
        improvement = ((original_time - cached_time) / original_time) * 100
        speedup = original_time / cached_time
    else:
        improvement = 0
        speedup = 0

    # 生成报告
    report = f"""# 性能对比测试报告

## 测试信息

- 测试时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}
- 服务器: {SERVER}
- JDK: OpenJDK 17.0.2
- JMH版本: 1.19

## 测试结果对比

| 版本 | 平均耗时 (ns/op) | 平均耗时 (ms/op) | 吞吐量估算 (ops/s) |
|------|------------------|------------------|-------------------|
| **原始版本** | {original_time:,.0f} | {original_time/1000000:.3f} | {1_000_000_000/original_time:,.0f} |
| **缓存版本** | {cached_time:,.0f} | {cached_time/1000000:.3f} | {1_000_000_000/cached_time:,.0f} |

## 性能提升

- **提升幅度**: {improvement:+.1f}%
- **加速比**: {speedup:.2f}x

"""

    with open(f"{output_dir}/comparison_report.md", "w", encoding="utf-8") as f:
        f.write(report)

    print(f"\n{'='*60}")
    print("性能对比结果:")
    print(f"  原始版本: {original_time/1000000:.3f} ms/op")
    print(f"  缓存版本: {cached_time/1000000:.3f} ms/op")
    print(f"  性能提升: {improvement:+.1f}% ({speedup:.2f}x)")
    print("=" * 60)

if __name__ == "__main__":
    main()
