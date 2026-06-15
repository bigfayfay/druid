#!/bin/bash

SERVER="172.19.4.41"
USER="root"
PASSWORD="@1fw#2soc$3vpn"
JAR_FILE="druid-ak-1.2.27-jar-with-dependencies.jar"

echo "========================================"
echo "JMH 基准测试自动执行脚本"
echo "========================================"
echo "服务器: $SERVER"
echo "JAR 包: $JAR_FILE"
echo ""

# 检查 jar 文件是否存在
if [ ! -f "$JAR_FILE" ]; then
    echo "错误: 找不到 $JAR_FILE"
    exit 1
fi

echo "正在将 jar 文件编码为 base64..."
# 使用 tr -d '\r' 移除 Windows 换行符
base64 "$JAR_FILE" | tr -d '\r' > "${JAR_FILE}.b64"

echo "正在创建远程执行脚本..."
cat > remote_run.sh << 'REMOTE_EOF'
#!/bin/bash
cd /root
if [ -f "druid-ak-1.2.27-jar-with-dependencies.jar.b64" ]; then
    echo "正在解码 jar 文件..."
    base64 -d druid-ak-1.2.27-jar-with-dependencies.jar.b64 > druid-ak-1.2.27-jar-with-dependencies.jar
    
    echo "正在运行 JMH 基准测试..."
    java -cp /root/druid-ak-1.2.27-jar-with-dependencies.jar org.openjdk.jmh.Main ".*CustomerOutputVisitorUtilsBenchmark.*" > /root/jmh_results.txt 2>&1
    
    echo "测试完成，正在读取结果..."
    cat /root/jmh_results.txt
    
    # 清理
    rm -f /root/druid-ak-1.2.27-jar-with-dependencies.jar.b64
fi
REMOTE_EOF

chmod +x remote_run.sh

echo ""
echo "========================================"
echo "手动操作步骤:"
echo "========================================"
echo "由于 SSH 需要密码认证，请手动执行以下操作:"
echo ""
echo "1. 上传文件到服务器:"
echo "   scp $JAR_FILE ${JAR_FILE}.b64 ${USER}@${SERVER}:/root/"
echo ""
echo "2. 在服务器上运行测试:"
echo "   ssh ${USER}@${SERVER}"
echo "   (输入密码后执行)"
echo "   cd /root"
echo "   base64 -d ${JAR_FILE}.b64 > ${JAR_FILE}"
echo "   java -cp ${JAR_FILE} org.openjdk.jmh.Main \".*CustomerOutputVisitorUtilsBenchmark.*\""
echo ""
echo "3. 或者上传并执行 remote_run.sh 脚本"
echo ""

