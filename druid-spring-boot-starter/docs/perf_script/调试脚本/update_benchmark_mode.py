#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Update JMH Benchmark to include Throughput mode
"""

import re
import os

benchmark_file = "../druid-ak/com/ankki/druid/parser/utils/CustomerOutputVisitorUtilsBenchmark.java"

if not os.path.exists(benchmark_file):
    print(f"[ERROR] File not found: {benchmark_file}")
    exit(1)

print(f"[INFO] Reading file: {benchmark_file}")

with open(benchmark_file, 'r', encoding='utf-8') as f:
    content = f.read()

print("[INFO] Current configuration:")
print(content)

# Update to include both AverageTime and Throughput modes
updated_content = content.replace(
    "@BenchmarkMode(value={Mode.AverageTime})",
    "@BenchmarkMode(value={Mode.Throughput, Mode.AverageTime})"
)

# Update time unit to milliseconds for better throughput display
updated_content = updated_content.replace(
    "@OutputTimeUnit(value=TimeUnit.NANOSECONDS)",
    "@OutputTimeUnit(value=TimeUnit.MILLISECONDS)"
)

print("\n" + "=" * 60)
print("[INFO] Updated configuration:")
print("=" * 60)
print(updated_content)

# Backup original file
backup_file = benchmark_file + ".backup"
with open(backup_file, 'w', encoding='utf-8') as f:
    f.write(content)
print(f"\n[OK] Backup saved: {backup_file}")

# Write updated file
with open(benchmark_file, 'w', encoding='utf-8') as f:
    f.write(updated_content)
print(f"[OK] File updated: {benchmark_file}")

print("\n" + "=" * 60)
print("UPDATE SUMMARY")
print("=" * 60)
print("Changes made:")
print("  1. Added Mode.Throughput to benchmark modes")
print("  2. Changed time unit from NANOSECONDS to MILLISECONDS")
print("")
print("New benchmark modes:")
print("  - Throughput: operations per millisecond")
print("  - AverageTime: average time per operation")
print("")
print("Note: You need to rebuild the JAR file to apply these changes.")
print("=" * 60)
