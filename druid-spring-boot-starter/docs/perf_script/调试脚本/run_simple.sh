#!/bin/bash
# JMH 性能测试简化执行脚本

echo "=========================================="
echo "JMH 性能测试 - 快速执行指南"
echo "=========================================="
echo ""
echo "服务器: 172.19.4.41"
echo "用户: root"
echo "密码: @1fw#2soc\$3vpn"
echo ""

# 检查 jar 包
if [ ! -f "druid-ak-1.2.27-jar-with-dependencies.jar" ]; then
    echo "错误: 找不到 druid-ak-1.2.27-jar-with-dependencies.jar"
    echo "请确保在正确的目录中运行此脚本"
    exit 1
fi

echo "找到 JAR 包，准备执行测试"
echo ""

# 创建一个完整的可执行命令文件
cat > execute_test.txt << 'EOF'
# 在服务器上依次执行以下命令:

# 1. 解码 jar 包 (如果使用了 base64 上传)
base64 -d druid-ak-1.2.27-jar-with-dependencies.jar.b64 > druid-ak-1.2.27-jar-with-dependencies.jar

# 2. 运行 JMH 基准测试
java -cp /root/druid-ak-1.2.27-jar-with-dependencies.jar org.openjdk.jmh.Main ".*CustomerOutputVisitorUtilsBenchmark.*" 2>&1 | tee /root/jmh_results.txt

# 3. 查看结果摘要
tail -30 /root/jmh_results.txt
EOF

echo "已生成服务器端命令文件: execute_test.txt"
echo ""

# 生成本地上传命令
cat > local_commands.txt << 'EOF'
# 本地执行命令 (Git Bash / PowerShell):

# 1. 上传 jar 包到服务器
scp druid-ak-1.2.27-jar-with-dependencies.jar root@172.19.4.41:/root/
# 密码: @1fw#2soc$3vpn

# 2. 连接到服务器
ssh root@172.19.4.41
# 密码: @1fw#2soc$3vpn

# 3. 在服务器上运行测试 (复制 execute_test.txt 中的命令)
# 或者直接运行:
java -cp /root/druid-ak-1.2.27-jar-with-dependencies.jar org.openjdk.jmh.Main ".*CustomerOutputVisitorUtilsBenchmark.*"

# 4. 下载测试结果
exit
scp root@172.19.4.41:/root/jmh_results.txt ./

# 5. 生成报告
python generate_report.py --input jmh_results.txt --output report.html
EOF

echo "已生成本地命令文件: local_commands.txt"
echo ""

echo "=========================================="
echo "快速开始"
echo "=========================================="
echo ""
echo "方式一: 直接复制命令"
echo "  scp druid-ak-1.2.27-jar-with-dependencies.jar root@172.19.4.41:/root/"
echo ""
echo "方式二: 查看详细命令"
echo "  cat local_commands.txt"
echo ""
echo "方式三: 运行 PowerShell 脚本"
echo "  PowerShell -ExecutionPolicy Bypass -File run_benchmark.ps1"
echo ""
