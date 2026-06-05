
# JMH Automation Script
$ErrorActionPreference = "Stop"

$Server = "172.19.4.41"
$User = "root"
$Password = "@1fw#2soc$3vpn"
$JarFile = "druid-ak-1.2.27-jar-with-dependencies.jar"
$LocalPath = Join-Path $PSScriptRoot $JarFile
$RemotePath = "/root/$JarFile"
$ResultFile = "/root/jmh_results.txt"

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "JMH Performance Test Automation" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

# Check file
if (-not (Test-Path $LocalPath)) {
    Write-Host "[ERROR] JAR file not found: $JarFile" -ForegroundColor Red
    exit 1
}

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
