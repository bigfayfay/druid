# JMH Performance Test Automation
# PowerShell Script for Windows

param(
    [string]$Server = "172.19.4.41",
    [string]$User = "root",
    [string]$Password = "@1fw#2soc$3vpn",
    [string]$JarFile = "druid-ak-1.2.27-jar-with-dependencies.jar"
)

$ErrorActionPreference = "Continue"

function Write-Header {
    param([string]$Text)
    Write-Host ""
    Write-Host ("=" * 70) -ForegroundColor Cyan
    Write-Host $Text -ForegroundColor Cyan
    Write-Host ("=" * 70) -ForegroundColor Cyan
}

function Write-Step {
    param([string]$Text)
    Write-Host ""
    Write-Host "[STEP] $Text" -ForegroundColor Green
}

function Write-Info {
    param([string]$Text)
    Write-Host $Text -ForegroundColor White
}

function Write-Command {
    param([string]$Text)
    Write-Host $Text -ForegroundColor Cyan
}

# Start
Write-Header "JMH Performance Test Automation"

Write-Info "Server: $Server"
Write-Info "User: $User"
Write-Info "JAR File: $JarFile"
Write-Info ""

# Check file
$localPath = Join-Path $PSScriptRoot $JarFile
if (-not (Test-Path $localPath)) {
    Write-Host "[ERROR] JAR file not found: $localPath" -ForegroundColor Red
    exit 1
}

$fileSize = (Get-Item $localPath).Length / 1MB
Write-Info "[OK] Found JAR file ($([math]::Round($fileSize, 2)) MB)"

# Commands to execute
Write-Header "Execution Commands"

Write-Step "Step 1: Upload JAR file"
Write-Command "scp $JarFile ${User}@${Server}:/root/"
Write-Info "Password: $Password"
Write-Info ""

Write-Step "Step 2: Connect to server"
Write-Command "ssh ${User}@${Server}"
Write-Info "Password: $Password"
Write-Info ""

Write-Step "Step 3: Run JMH test"
Write-Command "cd /root"
Write-Command "java -cp /root/$JarFile org.openjdk.jmh.Main '.*CustomerOutputVisitorUtilsBenchmark.*' 2>&1 | tee /root/jmh_results.txt"
Write-Info "(This will take 1-2 minutes)"
Write-Info ""

Write-Step "Step 4: Download results"
Write-Command "exit"
Write-Command "scp ${User}@${Server}:/root/jmh_results.txt ./"
Write-Info ""

Write-Step "Step 5: Generate report"
Write-Command "python generate_report.py --input jmh_results.txt --output report.html"
Write-Info ""

Write-Header "Ready to Execute"
Write-Info ""
Write-Info "Due to SSH password authentication requirements,"
Write-Info "please open a new terminal and execute the commands above."
Write-Info ""
Write-Info "Or copy and paste this single line:"
Write-Info ""
Write-Command "scp $JarFile ${User}@${Server}:/root/ && ssh ${User}@${Server} `"java -cp /root/$JarFile org.openjdk.jmh.Main '.*CustomerOutputVisitorUtilsBenchmark.*' 2>&1 | tee /root/jmh_results.txt`""
Write-Info ""

# Save commands to file
$commandsFile = Join-Path $PSScriptRoot "quick_commands.txt"
@"
scp $JarFile ${User}@${Server}:/root/
ssh ${User}@${Server}
cd /root
java -cp /root/$JarFile org.openjdk.jmh.Main '.*CustomerOutputVisitorUtilsBenchmark.*' 2>&1 | tee /root/jmh_results.txt
exit
scp ${User}@${Server}:/root/jmh_results.txt ./
python generate_report.py --input jmh_results.txt --output report.html
"@ | Out-File -FilePath $commandsFile -Encoding ASCII

Write-Info "[OK] Commands saved to: quick_commands.txt"
