# JMH 性能基准测试自动化脚本
# 需要 PowerShell 5.1 或更高版本

param(
    [string]$Server = "172.19.4.41",
    [string]$User = "root",
    [string]$Password = "@1fw#2soc$3vpn",
    [string]$LocalJarPath = "druid-ak-1.2.27-jar-with-dependencies.jar",
    [string]$RemotePath = "/root",
    [switch]$SkipUpload = $false
)

$ErrorActionPreference = "Stop"

function Write-SectionHeader {
    param([string]$Text)
    Write-Host ""
    Write-Host "=" * 70 -ForegroundColor Cyan
    Write-Host $Text -ForegroundColor Cyan
    Write-Host "=" * 70 -ForegroundColor Cyan
}

function Write-Step {
    param([string]$Text)
    Write-Host ""
    Write-Host "[INFO] $Text" -ForegroundColor Green
}

function Write-Error-Step {
    param([string]$Text)
    Write-Host ""
    Write-Host "[ERROR] $Text" -ForegroundColor Red
}

# 检查本地文件
if (-not $SkipUpload) {
    Write-SectionHeader "步骤 1: 检查本地文件"

    if (-not (Test-Path $LocalJarPath)) {
        Write-Error-Step "找不到 JAR 文件: $LocalJarPath"
        Write-Host "当前目录: $(Get-Location)"
        exit 1
    }

    $fileSize = (Get-Item $LocalJarPath).Length / 1MB
    Write-Step "找到 JAR 文件: $LocalJarPath ($([math]::Round($fileSize, 2)) MB)"
}

# 创建 SSH 连接并执行命令的函数
function Invoke-SSHCommand {
    param(
        [string]$Command,
        [string]$Server,
        [string]$User,
        [string]$Password
    )

    # 使用 plink 或 OpenSSH
    $sshCommand = "ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null ${User}@${Server} `"$Command`""

    # 创建临时脚本处理密码
    $tempScript = [System.IO.Path]::GetTempFileName()

    @"
@echo off
set /p password|"${Password}" >nul 2>&1
echo ${Password} | ${sshCommand}
"@ | Out-File -FilePath $tempScript -Encoding ASCII

    try {
        $result = Invoke-Expression $tempScript 2>&1
        return $result
    } finally {
        Remove-Item $tempScript -ErrorAction SilentlyContinue
    }
}

# 使用 scp 上传文件
function Upload-FileViaSCP {
    param(
        [string]$LocalPath,
        [string]$RemotePath,
        [string]$Server,
        [string]$User,
        [string]$Password
    )

    Write-Step "正在上传文件到服务器..."

    # 使用 PowerShell 和 .NET 上传文件
    $fileName = Split-Path $LocalPath -Leaf
    $remoteFullPath = "$RemotePath/$fileName"

    # 创建期望脚本
    $expectScript = @"
#!/usr/bin/expect -f
set timeout 300
spawn scp "$LocalPath" "${User}@${Server}:${remoteFullPath}"
expect {
    "password:" {
        send "$Password\r"
        exp_continue
    }
    "100%" {
        # 传输完成
    }
    eof
}
"@

    $expectFile = [System.IO.Path]::GetTempFileName()
    $expectScript | Out-File -FilePath $expectFile -Encoding ASCII

    try {
        # 由于 expect 可能不可用，提供手动命令
        Write-Host ""
        Write-Host "由于 SSH 自动化限制，请手动执行以下命令上传文件:" -ForegroundColor Yellow
        Write-Host ""
        Write-Host "scp `"$LocalPath`" ${User}@${Server}:${remoteFullPath}" -ForegroundColor White
        Write-Host ""
        Write-Host "输入密码: $Password" -ForegroundColor Yellow

        return $false
    } finally {
        Remove-Item $expectFile -ErrorAction SilentlyContinue
    }
}

# 主执行流程
Write-SectionHeader "JMH 性能基准测试自动化执行"
Write-Host "服务器: $Server"
Write-Host "用户: $User"
Write-Host "JAR 文件: $LocalJarPath"
Write-Host "远程路径: $RemotePath"

# 创建报告目录
$reportDir = "benchmark_reports"
if (-not (Test-Path $reportDir)) {
    New-Item -ItemType Directory -Path $reportDir | Out-Null
}

# 生成手动执行命令
Write-SectionHeader "手动执行命令"

$commands = @"

# 1. 上传 JAR 包
scp druid-ak-1.2.27-jar-with-dependencies.jar root@172.19.4.41:/root/
# 密码: $Password

# 2. 连接服务器
ssh root@172.19.4.41
# 密码: $Password

# 3. 在服务器上执行测试
cd $RemotePath

# 运行 JMH 基准测试（约需 1-2 分钟）
java -cp druid-ak-1.2.27-jar-with-dependencies.jar org.openjdk.jmh.Main ".*CustomerOutputVisitorUtilsBenchmark.*" 2>&1 | tee jmh_results.txt

# 4. 下载结果到本地
exit
scp root@172.19.4.41:$RemotePath/jmh_results.txt ./

# 5. 生成报告
python generate_report.py --input jmh_results.txt --output benchmark_report.html

"@

Write-Host $commands -ForegroundColor White

# 保存命令到文件
$commands | Out-File -FilePath "manual_commands.txt" -Encoding UTF8
Write-Step "已保存手动命令到 manual_commands.txt"

# 生成结果收集脚本
$resultScript = @'
#!/bin/bash
# 在服务器上运行此脚本收集结果

echo "=========================================="
echo "收集 JMH 测试结果"
echo "=========================================="

RESULT_FILE="jmh_results.txt"
REPORT_FILE="benchmark_summary.txt"

if [ -f "$RESULT_FILE" ]; then
    echo "找到测试结果文件: $RESULT_FILE"
    echo ""

    echo "=== 测试配置 ===" > "$REPORT_FILE"
    grep -E "(JMH version|VM version|Warmup|Measurement)" "$RESULT_FILE" >> "$REPORT_FILE" 2>/dev/null
    echo "" >> "$REPORT_FILE"

    echo "=== 测试结果 ===" >> "$REPORT_FILE"
    grep -A 5 "Benchmark.*Mode" "$RESULT_FILE" >> "$REPORT_FILE" 2>/dev/null
    echo "" >> "$REPORT_FILE"

    echo "=== 性能数据 ===" >> "$REPORT_FILE"
    tail -20 "$RESULT_FILE" >> "$REPORT_FILE" 2>/dev/null

    cat "$REPORT_FILE"
    echo ""
    echo "摘要已保存到: $REPORT_FILE"
else
    echo "错误: 找不到测试结果文件 $RESULT_FILE"
    echo "请先运行 JMH 测试"
fi
'@

$resultScript | Out-File -FilePath "collect_results.sh" -Encoding UTF8
Write-Step "已创建结果收集脚本: collect_results.sh"

# 完成
Write-SectionHeader "准备完成"
Write-Host ""
Write-Host "后续步骤:" -ForegroundColor Cyan
Write-Host "1. 复制上述命令到终端执行" -ForegroundColor White
Write-Host "2. 或者查看 manual_commands.txt 获取完整命令" -ForegroundColor White
Write-Host "3. 测试完成后，运行 python generate_report.py 生成报告" -ForegroundColor White
Write-Host ""
