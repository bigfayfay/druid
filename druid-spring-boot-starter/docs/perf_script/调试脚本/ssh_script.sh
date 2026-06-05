#!/bin/bash

# Function to run SSH command with password
ssh_with_pass() {
    local server=$1
    local user=$2
    local pass=$3
    local command=$4

    # Use SSH with password (requires sshpass or manual input)
    if command -v sshpass >/dev/null 2>&1; then
        sshpass -p "$pass" ssh -o StrictHostKeyChecking=no "$user@$server" "$command"
    else
        echo "sshpass not found. Please enter password manually."
        ssh -o StrictHostKeyChecking=no "$user@$server" "$command"
    fi
}

# Function to SCP file with password
scp_with_pass() {
    local local_file=$1
    local server=$2
    local user=$3
    local pass=$4
    local remote_path=$5

    if command -v sshpass >/dev/null 2>&1; then
        sshpass -p "$pass" scp -o StrictHostKeyChecking=no "$local_file" "$user@$server:$remote_path"
    else
        echo "Please enter password for SCP:"
        scp -o StrictHostKeyChecking=no "$local_file" "$user@$server:$remote_path"
    fi
}

# Main execution
SERVER="172.19.4.41"
USER="root"
PASSWORD="@1fw#2soc$3vpn"
JAR_FILE="druid-ak-1.2.27-jar-with-dependencies.jar"

echo "[Step 1] Uploading JAR file..."
scp_with_pass "$JAR_FILE" "$SERVER" "$USER" "$PASSWORD" "/root/"

if [ $? -eq 0 ]; then
    echo "[OK] Upload complete"
else
    echo "[ERROR] Upload failed"
    exit 1
fi

echo ""
echo "[Step 2] Running JMH test..."
echo "This will take 1-2 minutes..."
echo ""

ssh_with_pass "$SERVER" "$USER" "$PASSWORD" "cd /root && java -cp $JAR_FILE org.openjdk.jmh.Main '.*CustomerOutputVisitorUtilsBenchmark.*' 2>&1 | tee /root/jmh_results.txt"

if [ $? -eq 0 ]; then
    echo "[OK] Test complete"
else
    echo "[ERROR] Test failed"
    exit 1
fi

echo ""
echo "[Step 3] Downloading results..."
scp_with_pass "$SERVER" "$USER" "$PASSWORD" "/root/jmh_results.txt" "./"

if [ $? -eq 0 ]; then
    echo "[OK] Results downloaded"
else
    echo "[ERROR] Download failed"
    exit 1
fi

echo ""
echo "[Step 4] Generating report..."
python generate_report.py --input jmh_results.txt --output report.html

echo ""
echo "============================================================"
echo "TEST COMPLETE!"
echo "============================================================"
echo "Results: jmh_results.txt"
echo "Report: report.html"
echo "============================================================"
