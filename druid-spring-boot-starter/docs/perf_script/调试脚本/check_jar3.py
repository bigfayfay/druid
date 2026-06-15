# -*- coding: utf-8 -*-
import paramiko

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect("172.19.4.41", username="root", password="@1fw#2soc$3vpn", timeout=30)

# Use unzip -l to list contents and grep
cmd = 'unzip -l /root/druid-ak-1.2.27-jar-with-dependencies.jar 2>/dev/null | grep -i "Benchmark\\|AllSql"'
stdin, stdout, stderr = ssh.exec_command(cmd, timeout=30)
out = stdout.read().decode("utf-8", errors="ignore")
err = stderr.read().decode("utf-8", errors="ignore")
print("Benchmark classes:")
if out.strip():
    print(out)
else:
    print("(none found)")
    # Try ls to check file exists
    stdin2, stdout2, stderr2 = ssh.exec_command("ls -la /root/druid-ak-1.2.27-jar-with-dependencies.jar", timeout=10)
    print("JAR file check:", stdout2.read().decode())
ssh.close()
