# -*- coding: utf-8 -*-
"""
Upload ServerTimingBenchmark.class, run on server, download results
"""
import paramiko, os, time

HOST = "172.19.4.41"
USER = "root"
PASS = "@1fw#2soc$3vpn"
BASE = r"F:\2026年\03-现场项目\AAS-B07\02-中广电移动网络"

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
print("Connecting...")
ssh.connect(HOST, username=USER, password=PASS, timeout=30)
print("Connected!")
sftp = ssh.open_sftp()

# Upload .class
local_class = os.path.join(BASE, "ServerTimingBenchmark.class")
remote_class = "/root/ServerTimingBenchmark.class"
print(f"Uploading {os.path.basename(local_class)}...")
sftp.put(local_class, remote_class)
print("Uploaded!")

sftp.close()

# Run
print("\n--- Running ServerTimingBenchmark ---")
run_cmd = (
    'java -cp "/root/druid-ak-1.2.27-jar-with-dependencies.jar:/root" '
    'ServerTimingBenchmark'
)
stdin, stdout, stderr = ssh.exec_command(run_cmd, timeout=600)

out = stdout.read().decode(errors="replace")
err = stderr.read().decode(errors="replace")

# Save locally
result_path = os.path.join(BASE, "调试脚本", "server_benchmark_result.txt")
with open(result_path, "w", encoding="utf-8") as f:
    f.write(out)
    if err:
        f.write("\n--- STDERR ---\n" + err)

print(out)
if err:
    print("STDERR:", err[-2000:])

print(f"\nSaved: {result_path}")
ssh.close()
