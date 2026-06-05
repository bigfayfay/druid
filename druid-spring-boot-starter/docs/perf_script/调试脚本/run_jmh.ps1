# JMH Performance Test Automation
$Server = "172.19.4.41"
$User = "root"
$JarFile = "druid-ak-1.2.27-jar-with-dependencies.jar"
$ErrorActionPreference = "Continue"

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "JMH Performance Test Automation" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

$localJar = Join-Path $PSScriptRoot "..\$JarFile"
if (-not (Test-Path $localJar)) {
    Write-Host "[ERROR] JAR file not found: $JarFile" -ForegroundColor Red
    exit 1
}

$fileSize = (Get-Item $localJar).Length / 1MB
Write-Host "[OK] Found JAR file ($([math]::Round($fileSize, 2)) MB)" -ForegroundColor Green
Write-Host ""

# Step 1: Upload JAR
Write-Host "[1/4] Uploading JAR file..." -ForegroundColor Green
$scp1 = Start-Process -FilePath "scp" -ArgumentList "-o StrictHostKeyChecking=no", $localJar, "$User@${Server}:/root/" -NoNewWindow -PassThru -Wait
Write-Host "Done!" -ForegroundColor Green

# Step 2: Run test
Write-Host ""
Write-Host "[2/4] Running JMH test (1-2 minutes)..." -ForegroundColor Green
$ssh = Start-Process -FilePath "ssh" -ArgumentList "-o StrictHostKeyChecking=no", "$User@${Server}", "java -cp /root/$JarFile org.openjdk.jmh.Main '.*CustomerOutputVisitorUtilsBenchmark.*' 2>&1 | tee /root/jmh_results.txt" -NoNewWindow -PassThru -Wait
Write-Host "Done!" -ForegroundColor Green

# Step 3: Download results
Write-Host ""
Write-Host "[3/4] Downloading results..." -ForegroundColor Green
$localResult = Join-Path $PSScriptRoot "jmh_results.txt"
$scp2 = Start-Process -FilePath "scp" -ArgumentList "-o StrictHostKeyChecking=no", "$User@${Server}:/root/jmh_results.txt", $localResult -NoNewWindow -PassThru -Wait
Write-Host "Done!" -ForegroundColor Green

# Step 4: Generate report
Write-Host ""
Write-Host "[4/4] Generating report..." -ForegroundColor Green
$reportScript = Join-Path $PSScriptRoot "generate_report.py"
if (Test-Path $reportScript) {
    & python $reportScript --input $localResult --output (Join-Path $PSScriptRoot "report.html")
    Write-Host "Done!" -ForegroundColor Green
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Test Complete!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Results: jmh_results.txt" -ForegroundColor White
Write-Host "Report: report.html" -ForegroundColor White

if (Test-Path $localResult) {
    Write-Host ""
    Write-Host "Summary:" -ForegroundColor Yellow
    Get-Content $localResult | Select-String -Pattern "Benchmark|Mode|Score|ns/op" | Select-Object -Last 5
}
