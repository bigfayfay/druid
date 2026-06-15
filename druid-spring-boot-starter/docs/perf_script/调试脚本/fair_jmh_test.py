#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Fair JMH Benchmark: baseline and optimized run in SEPARATE JVM processes.
Outputs results for report generation.
"""

import paramiko
import os
import re
import time
from datetime import datetime

# Configuration
SERVER = "172.19.4.41"
USER = "root"
PASSWORD = "@1fw#2soc$3vpn"
JAR = "druid-ak-1.2.27-jar-with-dependencies.jar"
REMOTE_JAR = f"/root/{JAR}"

# JMH parameters
WARMUP_ITER = 3
WARMUP_TIME = 1        # seconds per iteration
MEASURE_ITER = 10
MEASURE_TIME = 3        # seconds per iteration
FORK = 1
MODE = "avgt"

OUTPUT_DIR = os.path.join("..", "性能测试结果", datetime.now().strftime("%Y%m%d%H%M"))

def ssh_connect():
    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    ssh.connect(SERVER, username=USER, password=PASSWORD, timeout=30)
    return ssh

def run_jmh(ssh, regex_filter):
    """Run JMH with the given regex filter, return raw output."""
    cmd = (
        f'cd /root && java -cp {REMOTE_JAR} org.openjdk.jmh.Main'
        f' -bm {MODE}'
        f' -wi {WARMUP_ITER} -w {WARMUP_TIME}s'
        f' -i {MEASURE_ITER} -r {MEASURE_TIME}s'
        f' -f {FORK}'
        f' "{regex_filter}"'
        f' 2>&1'
    )
    print(f"  CMD: {cmd}")
    start = time.time()
    stdin, stdout, stderr = ssh.exec_command(cmd, timeout=1800)
    exit_code = stdout.channel.recv_exit_status()
    elapsed = time.time() - start
    output = stdout.read().decode('utf-8', errors='ignore')
    print(f"  Exit code: {exit_code}, elapsed: {elapsed:.0f}s")
    return output

def parse_results(raw_output):
    """Parse JMH output, return dict of {method_name: {score, error, unit}}."""
    results = {}
    # Match the final result summary lines like:
    # SqlTemplateComparisonBenchmark.baseline_single_insert   avgt   10   249397.113   ±   5030.806   ns/op
    pattern = r'(\w+\.\w+)\s+avgt\s+(\d+)\s+([\d.]+)\s+[±]\s+([\d.]+)\s+(ns/op)'
    for match in re.finditer(pattern, raw_output):
        full_name = match.group(1)
        short_name = full_name.split('.')[-1]
        results[short_name] = {
            'full_name': full_name,
            'iterations': int(match.group(2)),
            'score_ns': float(match.group(3)),
            'error_ns': float(match.group(4)),
            'unit': match.group(5),
        }
    return results

def ops_per_second(ns_per_op):
    """Convert ns/op to ops/s."""
    if ns_per_op <= 0:
        return 0
    return 1_000_000_000.0 / ns_per_op

def percent_change(old, new):
    """Calculate percentage change."""
    if old == 0:
        return 0
    return (new - old) * 100.0 / old

def main():
    print("=" * 70)
    print("Fair JMH Benchmark — baseline & optimized in SEPARATE JVM processes")
    print("=" * 70)
    print(f"Warmup: {WARMUP_ITER}×{WARMUP_TIME}s, Measure: {MEASURE_ITER}×{MEASURE_TIME}s, Fork: {FORK}")
    print()

    # Create output directory
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    print(f"Output: {OUTPUT_DIR}")

    # Upload JAR
    print("\n[1/5] Uploading JAR...")
    ssh = ssh_connect()
    sftp = ssh.open_sftp()
    local_jar = os.path.join("..", JAR)
    sftp.put(local_jar, REMOTE_JAR)
    print("  OK")
    sftp.close()

    # Remove old lock
    ssh.exec_command('rm -f /tmp/jmh.lock')

    # ---- Phase 1: Single SQL - baseline ----
    print("\n[2/5] Phase 1a: Single SQL - BASELINE (separate JVM)...")
    baseline_single_raw = run_jmh(ssh, 'SqlTemplateComparisonBenchmark.baseline_single_.*')
    with open(os.path.join(OUTPUT_DIR, "baseline_single.txt"), 'w', encoding='utf-8') as f:
        f.write(baseline_single_raw)
    baseline_single = parse_results(baseline_single_raw)
    print(f"  Parsed {len(baseline_single)} results")

    # Clean up JVM state
    ssh.exec_command('pkill -f org.openjdk.jmh.Main; sleep 2; rm -f /tmp/jmh.lock')
    time.sleep(3)

    # ---- Phase 1: Single SQL - optimized ----
    print("\n[3/5] Phase 1b: Single SQL - OPTIMIZED (separate JVM)...")
    optimized_single_raw = run_jmh(ssh, 'SqlTemplateComparisonBenchmark.optimized_single_.*')
    with open(os.path.join(OUTPUT_DIR, "optimized_single.txt"), 'w', encoding='utf-8') as f:
        f.write(optimized_single_raw)
    optimized_single = parse_results(optimized_single_raw)
    print(f"  Parsed {len(optimized_single)} results")

    # Clean up JVM state
    ssh.exec_command('pkill -f org.openjdk.jmh.Main; sleep 2; rm -f /tmp/jmh.lock')
    time.sleep(3)

    # ---- Phase 2: Batch/Mixed - baseline ----
    print("\n[4/5] Phase 2a: Batch/Mixed - BASELINE (separate JVM)...")
    baseline_batch_raw = run_jmh(ssh, 'SqlTemplateComparisonBenchmark.baseline_(begin_only|insert_only|select_only|update_only|mixed_100)')
    with open(os.path.join(OUTPUT_DIR, "baseline_batch.txt"), 'w', encoding='utf-8') as f:
        f.write(baseline_batch_raw)
    baseline_batch = parse_results(baseline_batch_raw)
    print(f"  Parsed {len(baseline_batch)} results")

    # Clean up JVM state
    ssh.exec_command('pkill -f org.openjdk.jmh.Main; sleep 2; rm -f /tmp/jmh.lock')
    time.sleep(3)

    # ---- Phase 2: Batch/Mixed - optimized ----
    print("\n[5/5] Phase 2b: Batch/Mixed - OPTIMIZED (separate JVM)...")
    optimized_batch_raw = run_jmh(ssh, 'SqlTemplateComparisonBenchmark.optimized_(begin_only|insert_only|select_only|update_only|mixed_100)')
    with open(os.path.join(OUTPUT_DIR, "optimized_batch.txt"), 'w', encoding='utf-8') as f:
        f.write(optimized_batch_raw)
    optimized_batch = parse_results(optimized_batch_raw)
    print(f"  Parsed {len(optimized_batch)} results")

    ssh.close()

    # ---- Save parsed results ----
    import json
    all_results = {
        'baseline_single': baseline_single,
        'optimized_single': optimized_single,
        'baseline_batch': baseline_batch,
        'optimized_batch': optimized_batch,
        'meta': {
            'date': datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
            'warmup': f'{WARMUP_ITER}x{WARMUP_TIME}s',
            'measure': f'{MEASURE_ITER}x{MEASURE_TIME}s',
            'fork': FORK,
            'mode': MODE,
        }
    }
    with open(os.path.join(OUTPUT_DIR, "parsed_results.json"), 'w', encoding='utf-8') as f:
        json.dump(all_results, f, ensure_ascii=False, indent=2)

    print(f"\n{'=' * 70}")
    print("TEST COMPLETE. Results saved to:")
    print(f"  {OUTPUT_DIR}/")
    print(f"\nParsed {len(baseline_single) + len(optimized_single) + len(baseline_batch) + len(optimized_batch)} benchmark results.")

if __name__ == "__main__":
    main()
