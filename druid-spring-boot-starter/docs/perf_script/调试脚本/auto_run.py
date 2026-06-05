#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
SSH Automation Tool - Automatically execute JMH benchmark
"""

import subprocess
import sys
import os
import time

SERVER = "172.19.4.41"
USER = "root"
PASSWORD = "@1fw#2soc$3vpn"
JAR_FILE = "druid-ak-1.2.27-jar-with-dependencies.jar"
REMOTE_PATH = f"/root/{JAR_FILE}"
RESULT_FILE = "/root/jmh_results.txt"

def main():
    print("=" * 60)
    print("JMH Performance Test Automation")
    print("=" * 60)
    print(f"Server: {SERVER}")
    print(f"User: {USER}")
    print(f"JAR File: {JAR_FILE}")
    print()

    # Check local file
    if not os.path.exists(JAR_FILE):
        print(f"[ERROR] JAR file not found: {JAR_FILE}")
        return 1

    file_size = os.path.getsize(JAR_FILE) / (1024 * 1024)
    print(f"[OK] Found JAR file ({file_size:.2f} MB)")
    print()

    # Generate PowerShell automation script
    print("Generating PowerShell automation script...")

    ps_script = f'''
# JMH Automation Script
$ErrorActionPreference = "Stop"

$Server = "{SERVER}"
$User = "{USER}"
$Password = "{PASSWORD}"
$JarFile = "{JAR_FILE}"
$LocalPath = Join-Path $PSScriptRoot $JarFile
$RemotePath = "/root/$JarFile"
$ResultFile = "/root/jmh_results.txt"

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "JMH Performance Test Automation" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

# Check file
if (-not (Test-Path $LocalPath)) {{
    Write-Host "[ERROR] JAR file not found: $JarFile" -ForegroundColor Red
    exit 1
}}

$fileSize = (Get-Item $LocalPath).Length / 1MB
Write-Host "[OK] Found JAR file ($([math]::Round($fileSize, 2)) MB)" -ForegroundColor Green

# Step 1: Upload file
Write-Host ""
Write-Host "Step 1: Uploading JAR file..." -ForegroundColor Yellow

# Manual upload command (due to SSH password requirement)
Write-Host ""
Write-Host "Due to SSH authentication requirements, please execute:" -ForegroundColor Yellow
Write-Host ""
Write-Host "1. Upload file:" -ForegroundColor White
Write-Host "   scp $JarFile $User@$Server:/root/" -ForegroundColor Cyan
Write-Host "   Password: $Password" -ForegroundColor Gray
Write-Host ""
Write-Host "2. Connect to server:" -ForegroundColor White
Write-Host "   ssh $User@$Server" -ForegroundColor Cyan
Write-Host "   Password: $Password" -ForegroundColor Gray
Write-Host ""
Write-Host "3. Run test:" -ForegroundColor White
Write-Host "   java -cp $RemotePath org.openjdk.jmh.Main '.*CustomerOutputVisitorUtilsBenchmark.*' 2>&1 | tee $ResultFile" -ForegroundColor Cyan
Write-Host ""
Write-Host "4. Download results:" -ForegroundColor White
Write-Host "   exit" -ForegroundColor Cyan
Write-Host "   scp $User@$Server:$ResultFile ./jmh_results.txt" -ForegroundColor Cyan
Write-Host ""
Write-Host "5. Generate report:" -ForegroundColor White
Write-Host "   python generate_report.py --input jmh_results.txt --output report.html" -ForegroundColor Cyan
Write-Host ""
'''

    script_path = "auto_execute_now.ps1"
    with open(script_path, 'w', encoding='utf-8') as f:
        f.write(ps_script)

    print(f"[OK] Generated script: {script_path}")
    print()

    # Execute PowerShell script
    print("Executing automation script...")
    print("-" * 60)

    result = subprocess.run(
        ["powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", script_path],
        capture_output=True,
        text=True,
        encoding='utf-8',
        errors='ignore',
        timeout=120
    )

    print(result.stdout)
    if result.stderr:
        print("[ERROR]", result.stderr)

    print()
    print("=" * 60)
    print("Automation script execution completed")
    print("=" * 60)
    print()
    print("Due to SSH password authentication requirements,")
    print("please manually execute the commands shown above.")
    print()
    print("Quick commands:")
    print(f"  1. scp {JAR_FILE} {USER}@{SERVER}:/root/")
    print(f"  2. ssh {USER}@{SERVER}")
    print(f"  3. java -cp {REMOTE_PATH} org.openjdk.jmh.Main '.*CustomerOutputVisitorUtilsBenchmark.*'")
    print()

    return 0

if __name__ == "__main__":
    try:
        sys.exit(main())
    except KeyboardInterrupt:
        print("\n\nCancelled by user")
        sys.exit(1)
    except Exception as e:
        print(f"\n[ERROR] {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)
