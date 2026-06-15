# -*- coding: utf-8 -*-
"""
上传 JAR、smples.sql、ServerTimingBenchmark.java 到服务器，
编译并运行自定义基准测试，下载结果。
"""
import paramiko
import os
import time

HOST = "172.19.4.41"
USER = "root"
PASS = "@1fw#2soc$3vpn"
BASE = r"F:\2026年\03-现场项目\AAS-B07\02-中广电移动网络"

files = {
    f"{BASE}\\druid-ak-1.2.27-jar-with-dependencies.jar": "/root/druid-ak-1.2.27-jar-with-dependencies.jar",
    f"{BASE}\\调试脚本\\check_jar.py": "/root/check_jar.py",
    f"{BASE}\\smples.sql": "/root/smples.sql",
    f"{BASE}\\druid-ak\\ServerTimingBenchmark.java": "/root/ServerTimingBenchmark.java",
}

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
print("Connecting...")
ssh.connect(HOST, username=USER, password=PASS, timeout=30)
print("Connected!")
sftp = ssh.open_sftp()

# Upload files
for local, remote in files.items():
    print(f"Uploading {os.path.basename(local)} ({os.path.getsize(local) / 1e6:.1f} MB)...")
    start = time.time()
    sftp.put(local, remote)
    elapsed = time.time() - start
    print(f"  Done in {elapsed:.1f}s ({os.path.getsize(local) / 1e6 / elapsed:.1f} MB/s)")

sftp.close()
print("\nAll files uploaded.")

# Step 1: Check JAR contents
print("\n--- Check JAR for LightweightCached ---")
stdin, stdout, stderr = ssh.exec_command("python3 /root/check_jar.py")
print(stdout.read().decode())
err = stderr.read().decode()
if err:
    print("STDERR:", err)

# Step 2: Compile ServerTimingBenchmark
print("--- Compile ServerTimingBenchmark ---")
compile_cmd = (
    'javac -cp "/root/druid-ak-1.2.27-jar-with-dependencies.jar" '
    '-d /root /root/ServerTimingBenchmark.java'
)
stdin, stdout, stderr = ssh.exec_command(compile_cmd, timeout=120)
out = stdout.read().decode()
err = stderr.read().decode()
if out:
    print(out)
if err:
    print("COMPILE ERR:", err)
else:
    print("Compiled OK")

# Step 3: Run benchmark (最长10分钟)
print("\n--- Run ServerTimingBenchmark ---")
run_cmd = (
    'java -cp "/root/druid-ak-1.2.27-jar-with-dependencies.jar:/root" '
    'ServerTimingBenchmark'
)
stdin, stdout, stderr = ssh.exec_command(run_cmd, timeout=600)
out = stdout.read().decode()
err = stderr.read().decode()

# Save results
results = ""
if out:
    results += out
    print(out[-2000:] if len(out) > 2000 else out)
if err:
    results += "\n--- STDERR ---\n" + err
    print("STDERR:", err[-1000:])

# Download results
local_result = os.path.join(BASE, "调试脚本", "server_benchmark_result.txt")
with open(local_result, "w", encoding="utf-8") as f:
    f.write(results)
print(f"\nResults saved to: {local_result}")

ssh.close()
