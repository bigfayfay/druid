@echo off
chcp 65001 > nul
setlocal enabledelayedexpansion

echo ========================================
echo JMH Performance Test - Interactive
echo ========================================
echo.
echo Server: 172.19.4.41
echo User: root
echo.
echo Please enter SSH password when prompted.
echo ========================================
echo.

echo [1/4] Uploading JAR file...
scp -o StrictHostKeyChecking=no ..\druid-ak-1.2.27-jar-with-dependencies.jar root@172.19.4.41:/root/
if errorlevel 1 (
    echo.
    echo Upload failed. Please check your password.
    pause
    exit /b 1
)

echo.
echo [2/4] Running JMH test...
echo This will take 1-2 minutes. Please wait...
echo.

ssh -o StrictHostKeyChecking=no root@172.19.4.41 "cd /root ^&^& java -cp druid-ak-1.2.27-jar-with-dependencies.jar org.openjdk.jmh.Main '.*CustomerOutputVisitorUtilsBenchmark.*' 2^>^&1 ^| tee /root/jmh_results.txt"

if errorlevel 1 (
    echo.
    echo Test execution had issues.
)

echo.
echo [3/4] Downloading results...
scp -o StrictHostKeyChecking=no root@172.19.4.41:/root/jmh_results.txt ./jmh_results.txt

if errorlevel 1 (
    echo Download failed.
    pause
    exit /b 1
)

echo.
echo [4/4] Generating report...
python generate_report.py --input jmh_results.txt --output report.html

echo.
echo ========================================
echo Test Complete!
echo ========================================
echo.
echo Results: jmh_results.txt
echo Report: report.html
echo.

echo Summary:
echo ----------------------------------------
findstr /C:"Benchmark" /C:"Mode" /C:"Score" /C:"ns/op" jmh_results.txt 2>nul | more
echo ----------------------------------------

echo.
echo Press any key to open report...
pause > nul

start report.html
