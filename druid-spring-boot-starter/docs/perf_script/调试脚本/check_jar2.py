# -*- coding: utf-8 -*-
import paramiko

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect("172.19.4.41", username="root", password="@1fw#2soc$3vpn", timeout=30)

cmd = 'cd /root && python3 -c "import zipfile; z=zipfile.ZipFile(\"druid-ak-1.2.27-jar-with-dependencies.jar\"); [print(n) for n in z.namelist() if \"Benchmark\" in n or \"AllSql\" in n]" 2>&1'
stdin, stdout, stderr = ssh.exec_command(cmd, timeout=30)
out = stdout.read().decode("utf-8", errors="ignore")
err = stderr.read().decode("utf-8", errors="ignore")
print("Benchmark classes in JAR:")
print(out)
if err:
    print("ERR:", err[:500])
ssh.close()
