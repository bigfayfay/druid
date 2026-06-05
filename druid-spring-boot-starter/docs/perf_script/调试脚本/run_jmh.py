# -*- coding: utf-8 -*-
import paramiko, time

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect("172.19.4.41", username="root", password="@1fw#2soc$3vpn", timeout=30)

# Run the benchmark main() directly
cmd = 'cd /root && java -cp druid-ak-1.2.27-jar-with-dependencies.jar com.ankki.druid.parser.utils.LightweightCachedAkOutputVisitorUtilsAllSqlBenchmark 2>&1'
print("Running benchmark...")
stdin, stdout, stderr = ssh.exec_command(cmd, timeout=600)
out = stdout.read().decode("utf-8", errors="ignore")
err = stderr.read().decode("utf-8", errors="ignore")
print(out[-3000:])
if err:
    print("ERR:", err[:1000])
ssh.close()
