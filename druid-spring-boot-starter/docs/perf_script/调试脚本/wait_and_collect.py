#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
等待 JMH 测试完成并收集结果和监控数据
"""
import paramiko
import time
import os
from datetime import datetime

SERVER = '172.19.4.41'
USER = 'root'
PASSWORD = '@1fw#2soc$3vpn'
RESULT_FILE = '/root/jmh_results.txt'
MONITOR_FILE = '/root/jmh_monitor.log'

print('=== JMH 测试监控 ===')
print(f'开始时间: {datetime.now().strftime("%Y-%m-%d %H:%M:%S")}')
print()

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect(SERVER, username=USER, password=PASSWORD, timeout=30)

# 更新监控脚本（添加 CPU 百分比）
monitor_script = '''nohup bash -c "
while true; do
    echo '===== '$(date '+%Y-%m-%d %H:%M:%S')' ======' >> /root/jmh_monitor.log

    # Java 进程 CPU 和内存
    echo '=== Java Processes ===' >> /root/jmh_monitor.log
    ps aux | grep '[j]ava' | head -5 | awk '{print $2, $3"%", $4"%", $11}' >> /root/jmh_monitor.log

    # 系统内存
    echo '=== System Memory ===' >> /root/jmh_monitor.log
    free -m | grep Mem >> /root/jmh_monitor.log

    sleep 30
done
" > /dev/null 2>&1 &'''

stdin, stdout, stderr = ssh.exec_command(monitor_script)
stdout.read()

# 等待测试完成
max_wait = 1800  # 30 分钟
check_interval = 60
waited = 0

while waited < max_wait:
    time.sleep(check_interval)
    waited += check_interval

    # 检查 ForkedMain 进程
    stdin, stdout, stderr = ssh.exec_command("ps aux | grep '[F]orkedMain' | wc -l")
    running = int(stdout.read().decode().strip())

    # 检查结果文件大小
    try:
        stdin, stdout, stderr = ssh.exec_command(f'wc -l {RESULT_FILE}')
        lines = int(stdout.read().decode().strip().split()[0])
    except:
        lines = 0

    elapsed_min = waited // 60
    print(f'[{elapsed_min:2d} 分钟] ForkedMain: {running}, 结果行数: {lines}')

    if running == 0 and lines > 100:
        print('\\n测试完成!')
        break

# 等待文件写入完成
time.sleep(5)

# 下载结果
output_dir = '../性能测试结果'
os.makedirs(output_dir, exist_ok=True)
timestamp = datetime.now().strftime('%Y%m%d%H%M')

sftp = ssh.open_sftp()

# 下载测试结果
result_path = f'{output_dir}/{timestamp}_jmh_results.txt'
sftp.get(RESULT_FILE, result_path)
print(f'\\n测试结果: {result_path}')

# 下载监控日志
monitor_path = f'{output_dir}/{timestamp}_jmh_monitor.log'
try:
    sftp.get(MONITOR_FILE, monitor_path)
    print(f'监控日志: {monitor_path}')
except:
    print('监控日志: 无')

sftp.close()
ssh.close()

print(f'\\n结束时间: {datetime.now().strftime("%Y-%m-%d %H:%M:%S")}')
print(f'总耗时: {waited//60} 分钟')
