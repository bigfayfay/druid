# JMH 性能测试完全自动化脚本
# 使用 SSH.NET 库实现自动化

$ErrorActionPreference = "Stop"

# 配置
$Server = "172.19.4.41"
$User = "root"
$Password = "@1fw#2soc$3vpn"
$JarFile = "druid-ak-1.2.27-jar-with-dependencies.jar"
$LocalPath = Join-Path $PSScriptRoot $JarFile
$RemotePath = "/root/$JarFile"
$ResultFile = "/root/jmh_results.txt"

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "JMH 性能测试自动化执行" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# 检查文件
if (-not (Test-Path $LocalPath)) {
    Write-Host "错误: 找不到 $JarFile" -ForegroundColor Red
    exit 1
}

$fileSize = (Get-Item $LocalPath).Length / 1MB
Write-Host "✓ 找到 JAR 包: $JarFile ($([math]::Round($fileSize, 2)) MB)" -ForegroundColor Green

# 尝试使用不同的方法上传和执行

# 方法1: 使用 PowerShell SSH 会话 (如果可用)
Write-Host ""
Write-Host "步骤 1: 连接服务器..." -ForegroundColor Yellow

# 创建 SSH 命令执行器
function Invoke-SSHCommandWithPassword {
    param(
        [string]$Command,
        [string]$Server,
        [string]$User,
        [string]$Password
    )

    # 使用 PowerShell 的方式执行 SSH 命令
    $sshCommand = "ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null ${User}@${Server} `"$Command`""

    # 由于需要密码，我们使用 expect 或者其他方法
    # 在 Windows 上，我们可以尝试使用 plink 或者创建一个脚本

    # 尝试使用 plink (PuTTY)
    $plinkPath = "plink.exe"
    if (Get-Command $plinkPath -ErrorAction SilentlyContinue) {
        $result = & $plinkPath -pw $Password -batch "${User}@${Server}" $Command 2>&1
        return $result
    }

    # 尝试使用 sshpass (Git Bash 可能包含)
    if (Get-Command sshpass -ErrorAction SilentlyContinue) {
        $result = sshpass -p $Password $sshCommand 2>&1
        return $result
    }

    return $null
}

# 方法2: 创建 expect 脚本
function Create-ExpectScript {
    param(
        [string]$OutputPath,
        [string]$Server,
        [string]$User,
        [string]$Password,
        [string]$Command
    )

    $script = @"
#!/usr/bin/expect -f
set timeout 300
spawn ssh {$User}@{$Server} {$Command}
expect {
    "password:" {
        send "{$Password}\r"
        exp_continue
    }
    "yes/no" {
        send "yes\r"
        exp_continue
    }
    eof
}
"@

    $script | Out-File -FilePath $OutputPath -Encoding ASCII
    return $OutputPath
}

# 检查 expect 是否可用
$expectAvailable = $false
try {
    $null = expect -version 2>&1
    $expectAvailable = $true
} catch {
    $expectAvailable = $false
}

if ($expectAvailable) {
    Write-Host "✓ 检测到 expect，将使用自动化方式" -ForegroundColor Green

    # 步骤 1: 上传文件
    Write-Host ""
    Write-Host "步骤 2: 上传 JAR 包..." -ForegroundColor Yellow

    $scpScript = "/tmp/scp_upload.exp"
    Create-ExpectScript -OutputPath $scpScript -Server $Server -User $User -Password $Password -Command "mkdir -p /root"
    expect $scpScript

    $scpScript = "/tmp/scp_upload.exp"
    $scpExpect = @"
#!/usr/bin/expect -f
set timeout 300
spawn scp {$LocalPath} {$User}@{$Server}:/root/
expect {
    "password:" {
        send "{$Password}\r"
        exp_continue
    }
    "yes/no" {
        send "yes\r"
        exp_continue
    }
    "100%" {}
    eof
}
"@
    $scpExpect | Out-File -FilePath $scpScript -Encoding ASCII
    expect $scpScript

    Write-Host "✓ 上传完成" -ForegroundColor Green

    # 步骤 2: 运行测试
    Write-Host ""
    Write-Host "步骤 3: 运行 JMH 测试..." -ForegroundColor Yellow
    Write-Host " (约需 1-2 分钟，请耐心等待)" -ForegroundColor Gray

    $testScript = "/tmp/run_test.exp"
    Create-ExpectScript -OutputPath $testScript -Server $Server -User $User -Password $Command -Command "java -cp $RemotePath org.openjdk.jmh.Main '.*CustomerOutputVisitorUtilsBenchmark.*' 2>&1 | tee $ResultFile"
    expect $testScript

    Write-Host "✓ 测试完成" -ForegroundColor Green

    # 步骤 3: 下载结果
    Write-Host ""
    Write-Host "步骤 4: 下载测试结果..." -ForegroundColor Yellow

    $downloadScript = "/tmp/download.exp"
    $downloadExpect = @"
#!/usr/bin/expect -f
set timeout 300
spawn scp {$User}@{$Server}:{$ResultFile} {$PSScriptRoot}/
expect {
    "password:" {
        send "{$Password}\r"
        exp_continue
    }
    "100%" {}
    eof
}
"@
    $downloadExpect | Out-File -FilePath $downloadScript -Encoding ASCII
    expect $downloadScript

    Write-Host "✓ 下载完成" -ForegroundColor Green

} else {
    Write-Host "未检测到 expect，使用手动方式..." -ForegroundColor Yellow
    Write-Host ""
    Write-Host "请手动执行以下命令:" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "1. 上传文件:" -ForegroundColor White
    Write-Host "   scp $JarFile ${User}@${Server}:/root/" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "2. 连接服务器:" -ForegroundColor White
    Write-Host "   ssh ${User}@${Server}" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "3. 运行测试:" -ForegroundColor White
    Write-Host "   java -cp $RemotePath org.openjdk.jmh.Main '.*CustomerOutputVisitorUtilsBenchmark.*'" -ForegroundColor Cyan
    Write-Host ""
    exit 0
}

# 步骤 4: 生成报告
Write-Host ""
Write-Host "步骤 5: 生成测试报告..." -ForegroundColor Yellow

if (Test-Path "$PSScriptRoot/jmh_results.txt") {
    & python "$PSScriptRoot/generate_report.py" --input "$PSScriptRoot/jmh_results.txt" --output "$PSScriptRoot/report.html" --format both

    if (Test-Path "$PSScriptRoot/report.html") {
        Write-Host "✓ 报告已生成: report.html" -ForegroundColor Green
        Write-Host ""
        Write-Host "========================================" -ForegroundColor Cyan
        Write-Host "测试完成！" -ForegroundColor Green
        Write-Host "========================================" -ForegroundColor Cyan

        # 显示结果摘要
        Write-Host ""
        Write-Host "测试结果摘要:" -ForegroundColor Yellow
        Get-Content "$PSScriptRoot/jmh_results.txt" | Select-String -Pattern "Benchmark.*Mode|ns/op" | Select-Object -Last 5

        Write-Host ""
        Write-Host "查看完整报告: file:///$PSScriptRoot/report.html" -ForegroundColor Cyan
    } else {
        Write-Host "⚠ 报告生成失败" -ForegroundColor Yellow
    }
} else {
    Write-Host "⚠ 未找到测试结果文件" -ForegroundColor Yellow
}
