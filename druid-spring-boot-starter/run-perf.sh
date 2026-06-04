#!/bin/bash
#
# Druid SQL Parser 性能测试启动脚本
# 用法: ./run-perf.sh [options]
#
# 示例:
#   ./run-perf.sh                          # 使用默认参数启动
#   ./run-perf.sh --start-id 50000         # 从 id=50000 开始（断点续跑）
#   ./run-perf.sh --threads 8 --write-db   # 8线程 + 写DB
#

# ============== 默认参数 ==============
# perf param
PERF_ENABLED=true
# thread_parse_nums
PERF_THREAD_COUNT=4
PERF_WARMUP_BATCHES=2

# fetch param
PERF_DB_TYPE=oracle
# 20260601_at_1780243200
# 20260101_at_1767196800
# 20250101_at_1735660800
PERF_START_ID=1780243200100
PERF_BATCH_SIZE=2500
PERF_QUEUE_CAPACITY=18
PERF_MAX_RECORDS=500010000
PERF_MAX_CONSECUTIVE_EMPTY=3600
PERF_TENANT_ID=0
PERF_DATA_FETCHER_TYPE="audit"
# 8-cache, 57-iris, 1-sql_server,3-oracle
PERF_DB_TYPE_MAP="8=1,57=1"


# post handle
PERF_DATA_POST_HANDLER="res-insert"
PERF_WRITE_FAILED_FILE=false
PERF_WRITE_RESULT_DB=true
PERF_FLUSH_THRESHOLD=10
PERF_ERROR_ONLY=true

# VmOptions 监控参数（可选，不设置则使用 Java 代码内置默认值）
MONITOR="true"
MONITOR_DIR=""
MONITOR_INTERVAL="10"
CACHE_USE=""

# 数据库配置
DB_HOST=localhost
DB_PORT=3306
DB_NAME=bs_audit
DB_USER=sroot

DB_HOST_CK=localhost
DB_PORT_CK=8123
DB_NAME_CK=bs_audit
DB_USER_CK=root

# JVM 参数（实时审计场景调优，暂停目标 50ms，基于 5千万级 SQL 解析实测数据）
# 不同硬件配置请参考 docs/perf_param/JVM调优方案-v1.md
JVM_OPTS="-Xms1g -Xmx2g"
JVM_OPTS="$JVM_OPTS -XX:+UseG1GC"
#JVM_OPTS="$JVM_OPTS -XX:MaxGCPauseMillis=50"
#JVM_OPTS="$JVM_OPTS -XX:G1NewSizePercent=10"
#JVM_OPTS="$JVM_OPTS -XX:G1MaxNewSizePercent=25"
#JVM_OPTS="$JVM_OPTS -XX:G1HeapRegionSize=2m"
#JVM_OPTS="$JVM_OPTS -XX:ConcGCThreads=2"
#JVM_OPTS="$JVM_OPTS -XX:ParallelGCThreads=4"
#JVM_OPTS="$JVM_OPTS -XX:InitiatingHeapOccupancyPercent=35"
#JVM_OPTS="$JVM_OPTS -XX:G1ReservePercent=15"
#JVM_OPTS="$JVM_OPTS -XX:+ParallelRefProcEnabled"
#JVM_OPTS="$JVM_OPTS -XX:+UseStringDeduplication"
#JVM_OPTS="$JVM_OPTS -XX:+AlwaysPreTouch"
#JVM_OPTS="$JVM_OPTS -XX:+ExitOnOutOfMemoryError"
#JVM_OPTS="$JVM_OPTS -XX:+PerfDisableSharedMem"
#JVM_OPTS="$JVM_OPTS -Xlog:gc*:file=/data/logs/druid/gc.log:time,uptime:filecount=10,filesize=50m"

# JAR 路径（相对或绝对）
JAR_FILE="druid-spring-boot-starter-1.2.28.jar"

# ============== 解析命令行参数 ==============
while [ $# -gt 0 ]; do
    case "$1" in
        --enabled)
            PERF_ENABLED="$2"; shift 2 ;;
        --batch-size)
            PERF_BATCH_SIZE="$2"; shift 2 ;;
        --db-type)
            PERF_DB_TYPE="$2"; shift 2 ;;
        --warmup-batches)
            PERF_WARMUP_BATCHES="$2"; shift 2 ;;
        --max-records)
            PERF_MAX_RECORDS="$2"; shift 2 ;;
        --threads)
            PERF_THREAD_COUNT="$2"; shift 2 ;;
        --queue-capacity)
            PERF_QUEUE_CAPACITY="$2"; shift 2 ;;
        --write-file)
            PERF_WRITE_FAILED_FILE=true; shift ;;
        --no-write-file)
            PERF_WRITE_FAILED_FILE=false; shift ;;
        --write-db)
            PERF_WRITE_RESULT_DB=true; shift ;;
        --no-write-db)
            PERF_WRITE_RESULT_DB=false; shift ;;
        --start-id)
            PERF_START_ID="$2"; shift 2 ;;
        --max-consecutive-empty)
            PERF_MAX_CONSECUTIVE_EMPTY="$2"; shift 2 ;;
        --flush-threshold)
            PERF_FLUSH_THRESHOLD="$2"; shift 2 ;;
        --error-only)
            PERF_ERROR_ONLY=true; shift ;;
        --no-error-only)
            PERF_ERROR_ONLY=false; shift ;;
        --tenant-id)
            PERF_TENANT_ID="$2"; shift 2 ;;
        --db-type-map)
            PERF_DB_TYPE_MAP="$2"; shift 2 ;;
        --db-name)
            DB_NAME="$2"; shift 2 ;;
        --db-port)
            DB_PORT="$2"; shift 2 ;;
        --db-host)
            DB_HOST="$2"; shift 2 ;;
        --db-host-ck)
            DB_HOST_CK="$2"; shift 2 ;;
        --db-user)
            DB_USER="$2"; shift 2 ;;
        --db-port-ck)
            DB_PORT_CK="$2"; shift 2 ;;
        --db-name-ck)
            DB_NAME_CK="$2"; shift 2 ;;
        --db-user-ck)
            DB_USER_CK="$2"; shift 2 ;;
        --jvm)
            JVM_OPTS="$2"; shift 2 ;;
        --jar)
            JAR_FILE="$2"; shift 2 ;;
        --monitor)
            MONITOR="true"; shift ;;
        --no-monitor)
            MONITOR="false"; shift ;;
        --monitor-dir)
            MONITOR_DIR="$2"; shift 2 ;;
        --monitor-interval)
            MONITOR_INTERVAL="$2"; shift 2 ;;
        --cache)
            CACHE_USE="true"; shift ;;
        --no-cache)
            CACHE_USE="false"; shift ;;
        -h|--help)
            echo "Usage: $0 [options]"
            echo ""
            echo "Options:"
            echo "  --enabled <true|false>      启用/禁用测试 (default: true)"
            echo "  --batch-size <n>            每批拉取记录数 (default: 1000)"
            echo "  --db-type <type>            SQL方言类型 (default: mysql)"
            echo "  --warmup-batches <n>        预热批次数 (default: 2)"
            echo "  --max-records <n>           最大处理记录数, 0=无限 (default: 10000000)"
            echo "  --threads <n>               消费者线程数 (default: 1)"
            echo "  --queue-capacity <n>        队列容量 (default: 20)"
            echo "  --write-file                启用失败SQL写文件"
            echo "  --no-write-file             禁用失败SQL写文件 (default)"
            echo "  --write-db                  启用结果写DB"
            echo "  --no-write-db               禁用结果写DB (default)"
            echo "  --start-id <n>              起始ID, 用于断点续跑 (default: 0)"
            echo "  --max-consecutive-empty <n> 连续空批次阈值 (default: 15)"
            echo "  --flush-threshold <n>       DB批量刷新阈值 (default: 180)"
            echo "  --error-only                仅处理错误记录写入DB"
            echo "  --no-error-only             处理所有记录 (default)"
            echo "  --tenant-id <id>              租户ID (default: 0)"
            echo "  --db-type-map <map>           dbType转换配置，格式：key1=value1,key2=value2 (default: 1=2,27=3)"
            echo "  --db-name <name>            数据库名 (default: test_sql_zbzq_20251125)"
            echo "  --db-host <host>            MySQL主机地址 (default: 172.19.4.41)"
            echo "  --db-host-ck <host>         ClickHouse主机地址 (default: 172.19.4.41)"
            echo "  --db-user <user>            数据库用户名 (default: sroot)"
            echo "  --jvm '<opts>'              JVM参数 (default: -Xms1g -Xmx1g -XX:+UseG1GC ...)"
            echo "  --jar <path>                JAR文件路径"
            echo "  --monitor                   启用SQL模板监控"
            echo "  --no-monitor                禁用SQL模板监控 (default)"
            echo "  --monitor-dir <path>        监控日志目录 (default: /data/logs/druid)"
            echo "  --monitor-interval <s>      监控报告间隔秒数 (default: 5)"
            echo "  --monitor-max-threshold <n> 监控计数重置阈值 (default: MAX)"
            echo "  --cache                     启用SQL模板缓存"
            echo "  --no-cache                  禁用SQL模板缓存 (default)"
            echo "  -h, --help                  显示帮助"
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            exit 1
            ;;
    esac
done

# ============== 检查 JAR ==============
if [ ! -f "$JAR_FILE" ]; then
    echo "[ERROR] JAR not found: $JAR_FILE"
    echo "Please run 'mvn package -DskipTests' first."
    exit 1
fi

# ============== 打印配置 ==============
echo "========================================"
echo " Druid SQL Parser Perf Test"
echo "========================================"
echo " JAR:              $JAR_FILE"
echo " JVM:              $JVM_OPTS"
echo " DB_HOST:           $DB_HOST"
echo " DB_HOST_CK:       $DB_HOST_CK"
echo " DB_NAME:          $DB_NAME"
echo " DB_PORT:          $DB_PORT"
echo " DB_USER:          $DB_USER"
echo " DB_PORT_CK:       $DB_PORT_CK"
echo " DB_NAME_CK:       $DB_NAME_CK"
echo " DB_USER_CK:       $DB_USER_CK"
echo " perf.enabled:     $PERF_ENABLED"
echo " perf.batch-size:  $PERF_BATCH_SIZE"
echo " perf.db-type:     $PERF_DB_TYPE"
echo " perf.warmup:      $PERF_WARMUP_BATCHES"
echo " perf.max-records: $PERF_MAX_RECORDS"
echo " perf.threads:     $PERF_THREAD_COUNT"
echo " perf.queue-cap:   $PERF_QUEUE_CAPACITY"
echo " perf.write-file:  $PERF_WRITE_FAILED_FILE"
echo " perf.write-db:    $PERF_WRITE_RESULT_DB"
echo " perf.start-id:    $PERF_START_ID"
echo " perf.max-empty:   $PERF_MAX_CONSECUTIVE_EMPTY"
echo " perf.flush-thr:   $PERF_FLUSH_THRESHOLD"
echo " perf.error-only:  $PERF_ERROR_ONLY"
echo " perf.tenant-id:   $PERF_TENANT_ID"
echo " perf.db-type-map: $PERF_DB_TYPE_MAP"
[ -n "$MONITOR" ]               && echo " monitor:          $MONITOR"
[ -n "$MONITOR_DIR" ]           && echo " monitor.dir:      $MONITOR_DIR"
[ -n "$MONITOR_INTERVAL" ]      && echo " monitor.interval: $MONITOR_INTERVAL"
[ -n "$CACHE_USE" ]             && echo " cacheUse:         $CACHE_USE"
echo "========================================"

# ============== 构建 VmOptions 可选参数 ==============
VM_OPTS=""
[ -n "$MONITOR" ]               && VM_OPTS="$VM_OPTS -Dmonitor=$MONITOR"
[ -n "$MONITOR_DIR" ]           && VM_OPTS="$VM_OPTS -Dmonitor.dir=$MONITOR_DIR"
[ -n "$MONITOR_INTERVAL" ]      && VM_OPTS="$VM_OPTS -Dmonitor.interval=$MONITOR_INTERVAL"
[ -n "$CACHE_USE" ]             && VM_OPTS="$VM_OPTS -DcacheUse=$CACHE_USE"

# ============== 构建启动命令 ==============
JAVA_CMD="java $JVM_OPTS \
    -Dperf.enabled=$PERF_ENABLED \
    -Dperf.batch-size=$PERF_BATCH_SIZE \
    -Dperf.db-type=$PERF_DB_TYPE \
    -Dperf.warmup-batches=$PERF_WARMUP_BATCHES \
    -Dperf.max-records=$PERF_MAX_RECORDS \
    -Dperf.thread-count=$PERF_THREAD_COUNT \
    -Dperf.queue-capacity=$PERF_QUEUE_CAPACITY \
    -Dperf.data-fetcher-type=$PERF_DATA_FETCHER_TYPE \
    -Dperf.data-post-handler=$PERF_DATA_POST_HANDLER \
    -Dperf.write-failed-file=$PERF_WRITE_FAILED_FILE \
    -Dperf.write-result-db=$PERF_WRITE_RESULT_DB \
    -Dperf.start-id=$PERF_START_ID \
    -Dperf.max-consecutive-empty=$PERF_MAX_CONSECUTIVE_EMPTY \
    -Dperf.flush-threshold=$PERF_FLUSH_THRESHOLD \
    -Dperf.error-only=$PERF_ERROR_ONLY \
    -Dperf.tenant-id=$PERF_TENANT_ID \
    -Dperf.db-type-map=$PERF_DB_TYPE_MAP \
    $VM_OPTS \
    -DDB_HOST=$DB_HOST \
    -DDB_HOST_CK=$DB_HOST_CK \
    -DDB_NAME=$DB_NAME \
    -DDB_PORT=$DB_PORT \
    -DDB_USER=$DB_USER \
    -DDB_PORT_CK=$DB_PORT_CK \
    -DDB_NAME_CK=$DB_NAME_CK \
    -DDB_USER_CK=$DB_USER_CK \
    -jar $JAR_FILE"

# ============== 检查是否已启动 ==============
EXISTING_PID=$(pgrep -f "$JAR_FILE" 2>/dev/null)
if [ -n "$EXISTING_PID" ]; then
    echo "[WARN] Process already running, PID=$EXISTING_PID"
    echo "[WARN] Please stop it first or use: kill $EXISTING_PID"
    exit 1
fi

# ============== 启动 ==============
# 后台模式
nohup bash -c "$JAVA_CMD" > /dev/null 2>&1 &
PID=$!
echo "[INFO] Started in background, PID=$PID"

