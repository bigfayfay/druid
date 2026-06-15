# -*- coding: utf-8 -*-
import paramiko
import sys

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect("172.19.4.41", username="root", password="@1fw#2soc$3vpn", timeout=30)

cmd = 'cd /root && java -cp druid-ak-1.2.27-jar-with-dependencies.jar org.openjdk.jmh.Main -l ".*AllSqlBenchmark.*" 2>&1'
stdin, stdout, stderr = ssh.exec_command(cmd, timeout=60)
out = stdout.read().decode("utf-8", errors="ignore")
err = stderr.read().decode("utf-8", errors="ignore")
print(out[-3000:])
if err:
    print("ERR:", err[:500])
ssh.close()
