# -*- coding: utf-8 -*-
"""Upload JAR, run JMH thrpt+avgt on AllSqlBenchmark, download results."""
import paramiko, os, time

SERVER = "172.19.4.41"
USER = "root"
PASSWORD = "@1fw#2soc$3vpn"
JAR_FILE = "../druid-ak-1.2.27-jar-with-dependencies.jar"
REMOTE_PATH = "/root/druid-ak-1.2.27-jar-with-dependencies.jar"
RESULT_FILE = "/root/jmh_results.txt"

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect(SERVER, username=USER, password=PASSWORD, timeout=30)
sftp = ssh.open_sftp()

# Upload JAR
print("Uploading JAR...")
sftp.put(JAR_FILE, REMOTE_PATH)
print("OK")

# Verify BenchmarkList has AllSql entries
stdin2, stdout2, stderr2 = ssh.exec_command("unzip -p /root/druid-ak-1.2.27-jar-with-dependencies.jar META-INF/BenchmarkList | grep -c 'AllSqlBenchmark'", timeout=15)
cnt = stdout2.read().decode().strip()
print(f"AllSqlBenchmark entries in BenchmarkList: {cnt}")

# Run JMH
print("Running JMH (thrpt + avgt, all 5 methods, ~10 min)...")
cmd = f"cd /root && java -cp druid-ak-1.2.27-jar-with-dependencies.jar org.openjdk.jmh.Main -bm thrpt,avgt '.*AllSqlBenchmark.*' 2>&1 | tee {RESULT_FILE}"
stdin, stdout, stderr = ssh.exec_command(cmd, timeout=900)
exit_status = stdout.channel.recv_exit_status()
out = stdout.read().decode("utf-8", errors="ignore")
err = stderr.read().decode("utf-8", errors="ignore")

print(f"Exit: {exit_status}")

# Download results
print("Downloading results...")
local_result = "jmh_server_results.txt"
sftp.get(RESULT_FILE, local_result)
print(f"Downloaded to {local_result}")

# Print summary
with open(local_result, "r", encoding="utf-8", errors="ignore") as f:
    content = f.read()
# Extract benchmark summary lines
import re
for line in content.split("\n"):
    if "AllSqlBenchmark" in line and ("thrpt" in line or "avgt" in line):
        print(line.strip())

sftp.close()
ssh.close()
print("Done")
