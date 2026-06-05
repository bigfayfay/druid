# JMH 性能测试自动化 - PowerShell 版本
# 一键执行，只需输入一次密码

param(
    [string]$Server = "172.19.4.41",
    [string]$User = "root",
    [string]$JarFile = "druid-ak-1.2.27-jar-with-dependencies.jar"
)

$ErrorActionPreference = "Stop"

function Write-Header {
    param([string]$Text)
    Write-Host ""
    Write-Host ("=" * 60) -ForegroundColor Cyan
    Write-Host $Text -ForegroundColor Cyan
    Write-Host ("=" * 60) -ForegroundColor Cyan
}

function Write-Step {
    param([string]$Text, [int]$Step, [int]$Total)
    Write-Host ""
    Write-Host "[$Step/$Total] $Text" -ForegroundColor Green
}

function Write-Info {
    param([string]$Text)
    Write-Host $Text -ForegroundColor White
}

function Write-Success {
    param([string]$Text)
    Write-Host $Text -ForegroundColor Green
}

function Write-Error {
    param([string]$Text)
    Write-Host $Text -ForegroundColor Red
}

# 开始
Write-Header "JMH 性能测试自动化执行"

Write-Info "服务器: $Server"
Write-Info "用户: $User"
Write-Info "JAR 文件: $JarFile"

# 检查文件
$localJar = Join-Path $PSScriptRoot "..\$JarFile"
if (-not (Test-Path $localJar)) {
    Write-Error "错误: 找不到 $JarFile"
    Write-Info "路径: $localJar"
    exit 1
}

$fileSize = (Get-Item $localJar).Length / 1MB
Write-Info "JAR 文件大小: $([math]::Round($fileSize, 2)) MB"
Write-Info ""

# 创建远程脚本
$remoteScript = @'
#!/bin/bash
set -e

JAR_FILE="druid-ak-1.2.27-jar-with-dependencies.jar"
REMOTE_PATH="/root/$JAR_FILE"
RESULT_FILE="/root/jmh_results.txt"

echo "检查 Java 环境..."
java -version 2>&1 | head -1

echo ""
echo "检查 JAR 包..."
if [ -f "$REMOTE_PATH" ]; then
    SIZE=$(stat -c%s "$REMOTE_PATH" 2>/dev/null || stat -f%z "$REMOTE_PATH" 2>/dev/null)
    SIZE_MB=$(echo "scale=2; $SIZE / 1048576" | bc)
    echo "JAR 包已就位 ($SIZE_MB MB)"
else
    echo "错误: JAR 包不存在，请等待上传完成"
    sleep 10
    if [ ! -f "$REMOTE_PATH" ]; then
        exit 1
    fi
fi

echo ""
echo "========================================"
echo "开始运行 JMH 基准测试"
echo "========================================"
echo ""
echo "测试配置:"
echo "  - Warmup: 5 iterations x 1 second"
echo "  - Measurement: 5 iterations x 10 seconds"
echo "  - 预计耗时: 1-2 分钟"
echo ""

# 运行测试
java -cp "$REMOTE_PATH" org.openjdk.jmh.Main ".*CustomerOutputVisitorUtilsBenchmark.*" 2>&1 | tee "$RESULT_FILE"

TEST_EXIT=${PIPESTATUS[0]}

echo ""
echo "========================================"
if [ $TEST_EXIT -eq 0 ]; then
    echo "测试完成!"
else
    echo "测试执行中出现问题 (退出码: $TEST_EXIT)"
fi
echo "========================================"
echo ""
echo "结果摘要:"
tail -20 "$RESULT_FILE"

exit $TEST_EXIT
'@

$scriptPath = Join-Path $env:TEMP "jmh_test.sh"
$remoteScript | Out-File -FilePath $scriptPath -Encoding ASCII

try {
    # 步骤1: 上传 JAR 包
    Write-Step "上传 JAR 包到服务器" 1 5

    $scpArgs = @(
        "-o", "StrictHostKeyChecking=no",
        "-o", "UserKnownHostsFile=/dev/null",
        $localJar,
        "$User@${Server}:/root/$JarFile"
    )

    $scpProcess = Start-Process -FilePath "scp" -ArgumentList $scpArgs -NoNewWindow -PassThru -Wait
    if ($scpProcess.ExitCode -ne 0) {
        Write-Error "上传失败，请检查网络和密码"
        exit 1
    }
    Write-Success "上传完成!"

    # 步骤2: 上传测试脚本
    Write-Step "上传测试脚本" 2 5

    $scpArgs2 = @(
        "-o", "StrictHostKeyChecking=no",
        "-o", "UserKnownHostsFile=/dev/null",
        $scriptPath,
        "$User@${Server}:/tmp/jmh_test.sh"
    )

    $scpProcess2 = Start-Process -FilePath "scp" -ArgumentList $scpArgs2 -NoNewWindow -PassThru -Wait
    Write-Success "脚本上传完成!"

    # 步骤3: 执行测试
    Write-Step "执行 JMH 基准测试 (约需 1-2 分钟)" 3 5
    Write-Info "请耐心等待..."

    $sshArgs = @(
        "-o", "StrictHostKeyChecking=no",
        "-o", "UserKnownHostsFile=/dev/null",
        "$User@${Server}",
        "bash /tmp/jmh_test.sh"
    )

    $sshProcess = Start-Process -FilePath "ssh" -ArgumentList $sshArgs -NoNewWindow -PassThru -Wait

    if ($sshProcess.ExitCode -eq 0) {
        Write-Success "测试执行完成!"
    } else {
        Write-Info "测试完成 (退出码: $($sshProcess.ExitCode))"
    }

    # 步骤4: 下载结果
    Write-Step "下载测试结果" 4 5

    $localResult = Join-Path $PSScriptRoot "jmh_results.txt"
    $scpArgs3 = @(
        "-o", "StrictHostKeyChecking=no",
        "-o", "UserKnownHostsFile=/dev/null",
        "$User@${Server}:/root/jmh_results.txt",
        $localResult
    )

    $scpProcess3 = Start-Process -FilePath "scp" -ArgumentList $scpArgs3 -NoNewWindow -PassThru -Wait
    Write-Success "下载完成!"

    # 步骤5: 生成报告
    Write-Step "生成测试报告" 5 5

    if (Test-Path $localResult) {
        $reportScript = Join-Path $PSScriptRoot "generate_report.py"
        if (Test-Path $reportScript) {
            & python $reportScript --input $localResult --output (Join-Path $PSScriptRoot "report.html") --format both

            Write-Header "测试完成!"
            Write-Info ""
            Write-Info "生成的文件:"
            Write-Info "  - jmh_results.txt (测试结果)"
            Write-Info "  - report.html (HTML 报告)"
            Write-Info "  - report.md (Markdown 报告)"
            Write-Info ""

            # 显示结果摘要
            Write-Info "性能测试结果摘要:"
            Write-Info ("-" * 60)
            Get-Content $localResult | Select-String -Pattern "Benchmark|Mode|Score|ns/op" | Select-Object -Last 5
            Write-Info ("-" * 60)

        } else {
            Write-Info "未找到 generate_report.py，跳过报告生成"
        }
    } else {
        Write-Error "未找到测试结果文件"
    }

} finally {
    # 清理
    Remove-Item $scriptPath -ErrorAction SilentlyContinue
}

Write-Info ""
Write-Info "执行完成!"
