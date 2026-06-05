#!/usr/bin/env python3
"""
SSH 连接工具 - 用于连接服务器并运行 JMH 基准测试
需要安装: pip install paramiko
"""

import subprocess
import sys

def run_command_with_password(command, password):
    """通过管道将密码传递给命令"""
    try:
        process = subprocess.Popen(
            command,
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            shell=True
        )

        # 发送密码并获取输出
        stdout, stderr = process.communicate(input=password.encode())

        return process.returncode, stdout.decode('utf-8', errors='ignore'), stderr.decode('utf-8', errors='ignore')
    except Exception as e:
        return -1, "", str(e)


def main():
    server = "172.19.4.41"
    user = "root"
    password = "@1fw#2soc$3vpn"
    jar_file = "druid-ak-1.2.27-jar-with-dependencies.jar"
    local_path = f"f:\\2026年\\03-现场项目\\AAS-B07\\02-中广电移动网络\\{jar_file}"
    remote_path = f"/root/{jar_file}"

    print("=" * 60)
    print("JMH 性能基准测试 - 服务器执行工具")
    print("=" * 60)
    print(f"服务器: {server}")
    print(f"用户: {user}")
    print(f"JAR 包: {jar_file}")
    print()

    # 方案1: 使用 PowerShell + SSH
    print("尝试使用 PowerShell 上传文件...")

    ps_script = f'''
$jarPath = "{local_path}"
$server = "{server}"
$user = "{user}"
$password = "{password}"
$remotePath = "/root/{jar_file}"

$secpasswd = ConvertTo-SecureString $password -AsPlainText -Force
$credential = New-Object System.Management.Automation.PSCredential ($user, $secpasswd)

# 创建 SSH 会话
$session = New-SSHSession -ComputerName $server -Credential $credential -AcceptKey

# 上传文件
Set-SCPFile -SessionId $session.SessionId -LocalFile $jarPath -Path "/root" -Force

# 关闭会话
Remove-SSHSession -SessionId $session.SessionId

Write-Host "Upload complete."
'''

    print("正在生成 PowerShell 脚本...")
    with open("upload_jar.ps1", "w", encoding='utf-8') as f:
        f.write(ps_script)
    print("已生成 upload_jar.ps1，请手动执行该脚本上传文件")

    print("\n" + "=" * 60)
    print("手动操作指南:")
    print("=" * 60)
    print(f"1. 将文件 {jar_file} 上传到服务器 {server}")
    print(f"2. 使用以下命令运行 JMH 基准测试:")
    print()
    print(f"   java -cp /root/{jar_file} org.openjdk.jmh.Main \".*CustomerOutputVisitorUtilsBenchmark.*\"")
    print()
    print("3. 收集测试结果并保存为报告")
    print()


if __name__ == "__main__":
    main()
