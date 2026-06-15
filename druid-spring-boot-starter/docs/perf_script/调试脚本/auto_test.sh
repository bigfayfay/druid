#!/bin/bash
# JMH 性能测试一键式自动化脚本
# 只需输入一次密码即可完成所有操作

SERVER="172.19.4.41"
USER="root"
JAR_FILE="druid-ak-1.2.27-jar-with-dependencies.jar"
REMOTE_PATH="/root/$JAR_FILE"
RESULT_FILE="/root/jmh_results.txt"

echo "========================================"
echo "JMH 性能测试自动化"
echo "========================================"
echo "服务器: $SERVER"
echo "用户: $USER"
echo "========================================"
echo ""

# 检查本地jar文件
if [ ! -f "../$JAR_FILE" ]; then
    echo "错误: 找不到 $JAR_FILE"
    echo "当前目录: $(pwd)"
    exit 1
fi

echo "[1/5] 准备上传 JAR 包..."
echo ""

# 创建一次性执行脚本，在服务器上运行
cat > /tmp/jmh_remote_setup.sh << 'REMOTE_SCRIPT'
#!/bin/bash
set -e

JAR_FILE="druid-ak-1.2.27-jar-with-dependencies.jar"
REMOTE_PATH="/root/$JAR_FILE"
RESULT_FILE="/root/jmh_results.txt"

echo "检查 Java 环境..."
java -version || { echo "错误: 未找到 Java"; exit 1; }

echo ""
echo "检查 JAR 包..."
if [ ! -f "$REMOTE_PATH" ]; then
    echo "警告: JAR 包未找到，请先上传"
    echo "等待上传完成..."
    sleep 5
fi

if [ -f "$REMOTE_PATH" ]; then
    FILE_SIZE=$(stat -c%s "$REMOTE_PATH" 2>/dev/null || stat -f%z "$REMOTE_PATH" 2>/dev/null)
    echo "JAR 包已就位 ($(echo "scale=2; $FILE_SIZE / 1048576" | bc) MB)"
else
    echo "错误: JAR 包不存在"
    exit 1
fi

echo ""
echo "开始运行 JMH 基准测试..."
echo "预计耗时: 1-2 分钟"
echo ""

java -cp "$REMOTE_PATH" org.openjdk.jmh.Main ".*CustomerOutputVisitorUtilsBenchmark.*" 2>&1 | tee "$RESULT_FILE"

echo ""
echo "========================================"
echo "测试完成!"
echo "========================================"
echo ""
echo "结果摘要:"
tail -20 "$RESULT_FILE" | grep -E "Benchmark|Mode|ns/op|Score" || tail -10 "$RESULT_FILE"
REMOTE_SCRIPT

chmod +x /tmp/jmh_remote_setup.sh

# 上传 JAR 包
echo "正在上传 JAR 包到服务器..."
scp -o StrictHostKeyChecking=no "../$JAR_FILE" "${USER}@${SERVER}:${REMOTE_PATH}"

if [ $? -eq 0 ]; then
    echo "上传完成!"
else
    echo "上传失败，请检查密码"
    exit 1
fi

echo ""
echo "[2/5] JAR 包已上传"
echo ""
echo "[3/5] 上传测试脚本并执行..."
echo ""

# 上传并执行远程脚本
scp -o StrictHostKeyChecking=no /tmp/jmh_remote_setup.sh "${USER}@${SERVER}:/tmp/"
ssh -o StrictHostKeyChecking=no "${USER}@${SERVER}" "bash /tmp/jmh_remote_setup.sh"

echo ""
echo "[4/5] 下载测试结果..."
scp -o StrictHostKeyChecking=no "${USER}@${SERVER}:${RESULT_FILE}" ./jmh_results.txt

if [ $? -eq 0 ]; then
    echo "下载完成!"
else
    echo "下载失败"
    exit 1
fi

echo ""
echo "[5/5] 生成测试报告..."
echo ""

# 生成报告
if [ -f "generate_report.py" ]; then
    python generate_report.py --input jmh_results.txt --output report.html --format both

    if [ -f "report.html" ]; then
        echo ""
        echo "========================================"
        echo "测试完成! 报告已生成"
        echo "========================================"
        echo ""
        echo "测试结果: jmh_results.txt"
        echo "HTML 报告: report.html"
        echo "Markdown 报告: report.md"
        echo ""

        # 显示关键结果
        echo "性能测试结果摘要:"
        echo "----------------------------------------"
        grep -E "Benchmark|Mode|Score|ns/op" jmh_results.txt | tail -5 || echo "无法解析结果"
        echo "----------------------------------------"
    else
        echo "警告: 报告生成失败"
    fi
else
    echo "警告: 未找到 generate_report.py"
fi

# 清理临时文件
rm -f /tmp/jmh_remote_setup.sh

echo ""
echo "完成!"
