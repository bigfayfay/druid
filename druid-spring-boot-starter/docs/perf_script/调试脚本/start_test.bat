@echo off
chcp 65001 > nul
setlocal enabledelayedexpansion

echo ============================================================
echo JMH Performance Test - Quick Start
echo ============================================================
echo.
echo Server: 172.19.4.41
echo User: root
echo Password: @1fw#2soc$3vpn
echo.
echo ============================================================
echo.

echo [Step 1] Uploading JAR file...
scp druid-ak-1.2.27-jar-with-dependencies.jar root@172.19.4.41:/root/
if errorlevel 1 (
    echo.
    echo [ERROR] Upload failed. Please check network and password.
    pause
    exit /b 1
)

echo.
echo [Step 2] Running JMH test...
echo This will take 1-2 minutes. Please wait...
echo.

ssh root@172.19.4.41 "cd /root && java -cp druid-ak-1.2.27-jar-with-dependencies.jar org.openjdk.jmh.Main '.*CustomerOutputVisitorUtilsBenchmark.*' 2>&1 | tee /root/jmh_results.txt"

if errorlevel 1 (
    echo.
    echo [ERROR] Test execution failed.
    pause
    exit /b 1
)

echo.
echo [Step 3] Downloading results...
scp root@172.19.4.41:/root/jmh_results.txt ./
if errorlevel 1 (
    echo.
    echo [ERROR] Download failed.
    pause
    exit /b 1
)

echo.
echo [Step 4] Generating report...
python generate_report.py --input jmh_results.txt --output report.html

echo.
echo ============================================================
echo Test Complete!
echo ============================================================
echo.
echo Results saved to: jmh_results.txt
echo Report generated: report.html
echo.
echo Press any key to open the report...
pause > nul

start report.html
