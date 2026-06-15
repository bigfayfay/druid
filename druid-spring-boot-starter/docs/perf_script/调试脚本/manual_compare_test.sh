#!/bin/bash
# 手动性能对比测试脚本

echo "========================================"
echo "性能对比测试"
echo "========================================"

SERVER="root@172.19.4.41"
JAR_FILE="druid-ak-1.2.27-jar-with-dependencies.jar"
OUTPUT_DIR="../性能测试结果/$(date +%Y%m%d%H%M)"
mkdir -p "$OUTPUT_DIR"

echo ""
echo "[1/4] 上传JAR文件..."
scp "../$JAR_FILE" $SERVER:/root/

echo ""
echo "[2/4] 运行原始版本测试..."
ssh $SERVER "cd /root && java -cp $JAR_FILE org.openjdk.jmh.Main '.*CustomerOutputVisitorUtilsBenchmark.*' 2>&1 | tee /root/original_results.txt"
scp $SERVER:/root/original_results.txt "$OUTPUT_DIR/"

echo ""
echo "[3/4] 运行缓存版本测试..."
ssh $SERVER "cd /root && java -cp $JAR_FILE org.openjdk.jmh.Main '.*EnhancedCachedBenchmark.*' 2>&1 | tee /root/cached_results.txt"
scp $SERVER:/root/cached_results.txt "$OUTPUT_DIR/"

echo ""
echo "[4/4] 查看结果..."
echo "原始版本结果:"
tail -5 "$OUTPUT_DIR/original_results.txt"

echo ""
echo "缓存版本结果:"
tail -5 "$OUTPUT_DIR/cached_results.txt"

echo ""
echo "结果保存在: $OUTPUT_DIR"
