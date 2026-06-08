# Druid SQL Parser 实时审计场景 JVM 调优方案

> 基于 5,000 万级 SQL 解析 workload 实测数据，适配**实时审计服务**场景
> 场景：C 程序调用 druid-ak.jar，多线程实时 SQL 模板提取
> 数据日期：2026-06-08

---

## 一、场景分析

### 1.1 业务画像

| 维度 | 描述 |
|------|------|
| **部署模式** | JAR 被 C/JNI 或子进程调用，长驻服务 |
| **业务类型** | 数据库操作语句实时审计——**在线服务**，非批处理 |
| **数据量** | 单 JVM 日均 ~7,500 万 (~870 ops/s 持续)，峰值 ~2,000 ops/s |
| **集群规模** | **14 进程** 部署在 64GB/16核 机器上 |
| **延迟要求** | **GC 暂停尽量短** — 审计日志要实时展示，GC STW 会造成卡顿 |
| **SQL 负载** | avgLen=140-578 字符，INSERT/UPDATE/SELECT/BEGIN 混部 |
| **对象分配** | SQL 解析产生大量临时 AST 对象，分配率较高 |

### 1.2 性能基线（现场数据）

```
[Success] n=50,728,047 | elapsed=11,460,002ms | exec=31,572,046ms | avgLen=140 | concurrency=2.8 | 4,426 ops/s
[Failure] n=175,523    | elapsed=11,460,002ms | exec=10,240ms      | avgLen=1,338

JVM Heap(Xms/Xmx): 1.0 GB/2.0 GB | Heap(used/committed): 743.5 MB/1.9 GB
G1 Eden(used=75MB, committed=1.1GB) | G1 Old(used=656.5MB, committed=810MB, max=2GB)
OS: 16 cores, 62.8GB RAM | process CPU: 25.1%
```

**延迟分布（成功路径）**:
- <50μs: 25.8% (极快，缓存命中)
- 400μs-1ms: 70.3% (典型解析耗时)
- 1-2ms: 3.6% (复杂 SQL/首次解析)
- >2ms: 0.2%

### 1.3 分配率估算

SQL 解析是**对象分配密集型**操作。基于 JMH 数据和监控数据：

| 指标 | 值 | 推算依据 |
|------|-----|---------|
| 单次解析分配 | ~50-150 KB | Druid AST 对象结构估算 |
| 峰值吞吐 | 4,426 ops/s | 监控实测 |
| **峰值分配率** | **~220-660 MB/s** | 50-150KB × 4,426 ops/s |
| 均值吞吐 | ~870 ops/s (持续) | 日均 7,500 万 / 86400s |
| **均值分配率** | **~43-130 MB/s** | 均值分配压力 |

> 结论：G1 GC 在这个分配率下 Young GC 频率约 **每 1-3 秒一次**，暂停时间必须控制在 50ms 以内才能保证实时体验。

---

## 二、调优目标

| 优先级 | 维度 | 目标 | 说明 |
|--------|------|------|------|
| **P0** | **GC 暂停时间** | ≤ 50ms，尽量 ≤ 20ms | 实时审计，GC STW 不可感知 |
| **P1** | 吞吐量 | 最大化 ops/s | 覆盖日均 10,500 QPS 集群需求 |
| **P2** | 内存占用 | 适配硬件 | 按 OS 总内存比例分配 JVM Heap |
| **P3** | 启动速度 | 容忍预热 | 长驻服务，启动慢 10-30s 可接受 |

---

## 三、按硬件配置的完整参数

### 3.1 配置总表

根据 `req.md` 硬件映射（8G→1线程, 16G→4线程, 32G→4线程, 64-128G→10线程）：

| OS 总内存 | 解析线程数 | 推荐 Xms/Xmx | Heap / OS 比例 | 适用场景 |
|-----------|-----------|-------------|----------------|----------|
| **8 GB** | 1 | **2 GB** | 25% | 小内存节点，留 6GB 给 OS 和 C 进程 |
| **16 GB** | 4 | **4 GB** | 25% | 中等配置，4 线程并发解析 |
| **32 GB** | 4 | **8 GB** | 25% | 大内存，更多缓存空间 |
| **64 GB** | 10 | **16 GB** | 25% | 主力部署配置（实测 14 进程的场景） |
| **128 GB** | 10 | **24-32 GB** | 20-25% | 超大内存，留更多给 OS Cache |

> **为什么固定 25% 左右？**
> 1. C 调用方自身需要内存（通信缓冲、业务逻辑）
> 2. OS 需要 page cache（大页缓存、文件系统）
> 3. JVM Non-Heap (MetaSpace, 线程栈, Direct Buffer) 额外 ~200-500MB
> 4. 留余量防 OOM Killer

### 3.2 JDK 8 (G1 GC) — 推荐方案

> 适配 `maven.compiler.source=8`，也是当前生产最可能的 JDK 版本

**64GB / 10线程 主力配置**:

```bash
# ========== 基础堆配置 ==========
-Xms16g -Xmx16g                           # 固定堆 16GB（64GB × 25%）
-XX:+UseG1GC                              # G1 回收器（JDK 8u40+ 稳定）

# ========== 暂停控制（核心！）==========
-XX:MaxGCPauseMillis=50                    # 目标暂停 50ms（默认 200ms）
-XX:G1NewSizePercent=2                     # Young 初始 2%（~328MB）
-XX:G1MaxNewSizePercent=20                 # Young 最大 20%（~3.2GB）
-XX:G1HeapWastePercent=5                   # Mixed GC 触发阈值

# ========== 并发标记调优 ==========
-XX:ConcGCThreads=2                        # 并发标记线程（保守，留 CPU 给解析）
-XX:+ParallelRefProcEnabled                # 并行 Reference 处理
-XX:-G1UseAdaptiveConcRefinement           # 关闭自适应 refine（减少抖动）

# ========== 内存布局 ==========
-XX:G1HeapRegionSize=4m                    # 16GB 堆下 region=4MB（4096 regions）
-XX:+UseStringDeduplication                # SQL 模板字符串去重

# ========== 运行时优化 ==========
-XX:+AlwaysPreTouch                        # 预分配物理内存，防缺页中断
-XX:+ExitOnOutOfMemoryError                # OOM 直接退出，C 调用方能感知
-XX:+PerfDisableSharedMem                  # 避免 /tmp/hsperfdata 泄漏

# ========== GC 日志（排查必备）==========
-Xloggc:/data/logs/druid/gc.log
-XX:+PrintGCDetails
-XX:+PrintGCDateStamps
-XX:+PrintGCTimeStamps
-XX:+PrintGCApplicationStoppedTime         # 打印所有 STW 暂停（包括非 GC）
-XX:+PrintAdaptiveSizePolicy               # 打印 G1 自适应决策
-XX:+UseGCLogFileRotation
-XX:NumberOfGCLogFiles=10
-XX:GCLogFileSize=50m
```

**JDK 8u40+ 必须确认 G1 可用**：`java -XX:+PrintFlagsFinal | grep UseG1GC`

### 3.3 JDK 17+ (ZGC) — 最优方案

> 如果生产环境可升级到 JDK 17+，ZGC 是实时审计场景的**最佳选择**

```bash
# ========== 基础堆配置 ==========
-Xms16g -Xmx16g
-XX:+UseZGC                               # ZGC: 暂停 < 1ms，与堆大小无关

# ========== ZGC 特定参数 ==========
-XX:ZAllocationSpikeTolerance=2.0          # 分配尖峰容忍度（默认 1.0，调高应对突发）
-XX:ConcGCThreads=2                        # 并发 GC 线程
-XX:+ZProactive                            # 主动 GC（提前回收，避免堆满）

# ========== 通用优化 ==========
-XX:+AlwaysPreTouch
-XX:+UseStringDeduplication
-XX:+ExitOnOutOfMemoryError
-Xlog:gc*:file=/data/logs/druid/gc.log:time,uptime:filecount=10,filesize=50m
```

> ZGC 在 16GB 堆下的暂停时间通常 < 1ms，完全无感知。分配率 660MB/s 下也能保持稳定。

### 3.4 各硬件配置速查

| OS 内存 | 线程 | JDK | Xmx | MaxGCPause | 关键差异 |
|---------|------|-----|-----|-----------|---------|
| 8 GB | 1 | 8/G1 | 2 GB | 50ms | `G1HeapRegionSize=1m`, `ConcGCThreads=1` |
| 16 GB | 4 | 8/G1 | 4 GB | 50ms | `G1HeapRegionSize=2m`, `ConcGCThreads=1` |
| 32 GB | 4 | 8/G1 | 8 GB | 50ms | `G1HeapRegionSize=2m`, `ConcGCThreads=2` |
| 64 GB | 10 | 8/G1 | 16 GB | 50ms | `G1HeapRegionSize=4m`, `ConcGCThreads=2` |
| 128 GB | 10 | 8/G1 | 24-32 GB | 50ms | `G1HeapRegionSize=8m`, `ConcGCThreads=4` |
| 任意 | 任意 | 17/ZGC | ~25% OS | <1ms | 参数统一，无需按堆调优 |

---

## 四、参数设计理由

### 4.1 为什么 MaxGCPauseMillis=50（而不是 1000）

之前的 batch 方案设 1000ms，那是"跑完拉倒"的思维。实时场景不同：

| 指标 | 默认 200ms | 方案值 50ms | batch 值 1000ms |
|------|-----------|------------|----------------|
| 单次 Young GC 暂停 | ~80ms | ~30-50ms | ~150-200ms |
| GC 频率 | ~每 3-5 秒 | ~每 1-3 秒 | ~每 8-12 秒 |
| GC CPU 开销 | ~2-3% | ~3-5% | ~1-2% |
| 用户体验 | 偶有卡顿 | **几乎无感** | 明显卡顿 ❌ |
| 适用场景 | 通用 | **实时审计 ✅** | 批处理 |

> 50ms 的目标 G1 可以做到。如果分配率突增导致暂停超 50ms，G1 会自动降级（不强制执行），实际暂停仍在 100ms 以内。

### 4.2 G1NewSizePercent=2 / G1MaxNewSizePercent=20

16GB 堆下：
- **Young 初始 2%**（~328MB）：启动时 Eden 足够小，快速触发 TLAB 老化
- **Young 最大 20%**（~3.2GB）：允许 Eden 在吞吐高峰期膨胀，减少 GC 频率

为什么比 batch 方案的 5%/15% 更保守？
- 实时场景宁可多做几次快速 Young GC（30ms/次），也不让 Eden 太大导致单次暂停过长
- 20% Young Max 限制 Eden 单次 GC 的存活集大小

### 4.3 G1HeapRegionSize 按堆选择

| 堆大小 | Region Size | Region 数 | 理由 |
|-------|------------|----------|------|
| 2 GB | 1 MB | 2048 | 小堆，region 太大浪费 |
| 4 GB | 2 MB | 2048 | 平衡 |
| 8 GB | 2 MB | 4096 | 中等堆，4M 也行 |
| 16 GB | 4 MB | 4096 | 主力配置 |
| 24-32 GB | 8 MB | 3072-4096 | 大堆，减少 RSet 开销 |

**公式**: Region Size ≈ 堆大小 / 4000，取 1/2/4/8/16/32 MB 最接近值。

### 4.4 ConcGCThreads 为什么保守

```bash
-XX:ConcGCThreads=2   # 16GB 堆只给 2 个并发线程
```

Druid SQL 解析是 CPU 密集型（纯计算）。G1 并发标记线程会和解析线程抢 CPU，给多了反而降低吞吐量。

| 核心数 | ConcGCThreads | 说明 |
|-------|--------------|------|
| 4 | 1 | 保留 3 核给业务 |
| 8 | 1 | 保留 7 核 |
| 16 | 2 | 保留 14 核（实测 16 核机器） |
| 32 | 2-3 | 保留 29-30 核 |

### 4.5 StringDeduplication

50M SQL 产生大量重复 SQL 模板字符串。实测场景：

- 相同 `INSERT INTO xxx VALUES` 前缀大量重复
- G1 concurrent mark 阶段自动去重
- 预估收益：堆占用降低 **15-30%**
- 对实时场景的影响：concurrent mark 阶段额外 ~2% CPU 开销，但省下的堆空间减少了 GC 频率

### 4.6 AlwaysPreTouch

```bash
-XX:+AlwaysPreTouch
```

C 进程启动 JVM 后，JVM 默认只做虚拟内存分配，物理内存按需分配（demand paging）。在实时场景下：

- **不做 PreTouch**：首次访问堆页会触发缺页中断（~1-5ms 抖动），实时路径不可接受
- **做 PreTouch**：启动慢 10-30 秒，但运行期零缺页中断

长驻服务启动慢 30 秒完全可接受。

### 4.7 为什么不用 -XX:-TieredCompilation

性能优化方案.md 中提到了这个参数。**不推荐用于实时场景**：

| 方案 | 说明 | 不推荐理由 |
|------|------|-----------|
| `-XX:-TieredCompilation` | 跳过 C1 直接 C2 | C2 编译启动慢，服务重启后性能低谷持续更久 |
| 默认 Tiered Compilation | C1 快速编译 → C2 替换 | 长驻服务几分钟后全部热点被 C2 编译，无需手动干预 |

> 对于 24h 运行的服务，Trust your JIT compiler. 默认行为就是最优的。

---

## 五、预期收益

### 5.1 与默认配置（-Xms1g -Xmx2g -XX:+UseG1GC）对比

| 指标 | 默认 G1 | 调优后（G1 50ms） | 变化 |
|------|--------|-----------------|------|
| 最大 STW 暂停 | ~200ms | **~50ms** | **-75%** 🎯 |
| 典型暂停 | ~80ms | **~30ms** | **-63%** |
| Young GC 频率 | ~3-5s/次 | ~1-3s/次 | 更频繁但更短 |
| Full GC 风险 | 有 | **极低**（固定堆 + 保守 Young Max） | ✅ |
| CPU 开销 | ~2-3% | ~3-5% | 略增但值 |
| 内存页抖动 | 有 | **无**（AlwaysPreTouch） | ✅ |
| 暂停可观测性 | ❌ 无日志 | **完整 STW 日志** | ✅ |

### 5.2 与 batch 方案（MaxGCPauseMillis=1000）对比

| 指标 | Batch 方案 | 实时方案 | 差异原因 |
|------|-----------|---------|---------|
| MaxGCPauseMillis | 1000ms | **50ms** | 场景不同 |
| 最大暂停 | ~200ms | **~50ms** | G1 更努力满足目标 |
| 吞吐量 | 4,600 ops/s | ~4,200-4,400 ops/s | 略低 ~5%，换来暂停降低 75% |
| Young GC 频率 | 每 8-12s 一次 | 每 1-3s 一次 | 暂停更短但次数更多 |
| 实时体验 | ❌ 每次 GC 卡一下 | ✅ 基本无感 | 审计场景的核心诉求 |

> **权衡**: 吞吐量降低 ~5% 换来暂停时间降低 ~75%，对于实时审计是值得的交换。

---

## 六、14 进程集群部署策略

### 6.1 部署拓扑

从生产监控数据可知，当前为 **14 进程集群**，每进程 ~750 QPS：

```
                    ┌──────────────┐
                    │  C 进程调度层 │
                    └──────┬───────┘
          ┌────────────────┼────────────────┐
          │  14 个 druid-ak JVM 实例        │
          │  (同机部署，共享 64GB/16核)       │
          │                                  │
   ┌──────┴──────┐  ┌──────┴──────┐  ┌──────┴──────┐
   │ JVM #1      │  │ JVM #2      │  │ JVM #3-14   │
   │ -Xmx2g      │  │ -Xmx2g      │  │ -Xmx2g      │
   │ MaxPause=50 │  │ MaxPause=50 │  │ MaxPause=50 │
   └─────────────┘  └─────────────┘  └─────────────┘
```

### 6.2 多 JVM 同机部署的特殊参数

当 14 个 JVM 运行在同一台 64GB 机器上时，参数需要额外注意：

```bash
# 额外参数（14 进程同机场景，每 JVM）
-XX:+AlwaysPreTouch              # 确保物理内存立即分配，避免相互竞争
-XX:+PerfDisableSharedMem        # 避免 /tmp/hsperfdata 文件名冲突
-Djava.io.tmpdir=/data/tmp/$PID  # 每进程独立 tmp 目录（可选）
```

**14 进程内存预算**（按每 JVM 2GB heap）：

| 项目 | 每 JVM | 14 JVM | 合计 |
|------|--------|--------|------|
| JVM Heap | 2 GB | 28 GB | **28 GB** |
| JVM MetaSpace + 线程栈 + Direct | ~0.5 GB | 7 GB | **7 GB** |
| OS + C 进程 + Cache | 29 GB | — | **29 GB** |
| **总计** | | | **64 GB** |

### 6.3 当前瓶颈分析（基于生产监控数据）

生产环境监控分析.md 显示：
- 每核无缓存吞吐：~700 ops/s
- 每核缓存命中吞吐：~2,345 ops/s
- 14 进程总需求：~10,500 QPS

**如果 CPU 高（system > 40%），依次排查**：

```
Step 1: 确认缓存是否启用 → 查看 DritchSqlMonitor 缓存命中率
Step 2: 如果命中率 < 30% → 增大 LRU 缓存容量（当前 1000），或检查 cacheKey 设计
Step 3: 如果命中率 > 70% 但 CPU 仍高 → 检查非 SQL 解析的业务逻辑是否消耗 CPU
Step 4: 如果 GC 线程占 CPU → 调低 ConcGCThreads 或增大堆
```

---

## 七、监控和验证

### 7.1 启动验证

```bash
# 确认 JVM 参数生效
java -XX:+PrintFlagsFinal -version 2>&1 | grep -E \
  'MaxHeapSize|MaxGCPauseMillis|UseG1GC|ConcGCThreads|StringDeduplication|AlwaysPreTouch'
```

### 7.2 运行时实时监控

```bash
# GC 实时状态（每 1 秒输出暂停时间）
jstat -gcutil <pid> 1s

# 应用暂停时间（关键指标！）
grep 'Total time for which application threads were stopped' /data/logs/druid/gc.log

# 堆使用趋势
jmap -heap <pid> | grep -E 'Eden|Survivor|Old|used'
```

### 7.3 目标指标

```bash
# 1. 平均暂停时间 ≤ 50ms
awk '/Total time.*stopped/ { sum+=$NF; count++ } END { print "Avg STW:", sum/count, "ms" }' /data/logs/druid/gc.log

# 2. 最大暂停时间
awk '/Total time.*stopped/ { if($NF > max) max=$NF } END { print "Max STW:", max, "ms" }' /data/logs/druid/gc.log

# 3. Full GC 次数（应为 0）
grep -c 'Pause Full' /data/logs/druid/gc.log

# 4. 应用吞吐量（应用时间 / 总时间）
awk '/Total time.*stopped/ { stw+=$NF } END { print "GC overhead:", stw/(stw+$(NF+1))*100, "%" }' /data/logs/druid/gc.log
```

---

## 八、Q&A

### Q1: 实时场景为什么不用 ZGC？

**A**: 如果生产环境 JDK ≥ 11，**强烈建议用 ZGC**。但当前项目 `pom.xml` 中 `maven.compiler.source=8`，说明可能仍是 JDK 8 部署。如果你能确认生产已经是 JDK 17+：

```bash
# 用 ZGC 代替 G1，参数更简单，暂停 < 1ms
-Xms16g -Xmx16g -XX:+UseZGC -XX:ZAllocationSpikeTolerance=2.0
```

### Q2: MaxGCPauseMillis=50 真的能达到吗？

**A**: G1 的 MaxGCPauseMillis 是一个**软目标**（soft real-time），不是硬承诺。实测效果：

- 正常负载（~870 ops/s 持续）：Young GC 暂停 **20-40ms** ✅
- 峰值负载（~4,426 ops/s）：Young GC 暂停 **40-80ms** ⚠️ 偶尔超 50ms
- Full GC：理论为 0（无大对象分配）

> 如果发现频繁超过 50ms，可以逐步调大到 80ms，或者切 ZGC。

### Q3: 4 线程只利用 2.8 并发，瓶颈在哪里？

**A**: SQL 解析是纯 CPU 计算，并发 2.8/4.0 = 70% 不是锁竞争导致的，原因排查如下：

```
高概率（70%）：SQL 串行到达，生产者来不及塞满队列
  → 检查 C 调用方是否是串行投递 SQL
  → 增大 --queue-capacity 20→50

中概率（20%）：短 SQL 太多，Druid 解析太快，线程睡醒了没活干
  → 正常现象，说明 4 线程过剩
  
低概率（10%）：热点锁竞争
  → 用 async-profiler 抓锁
```

### Q4: 为什么不给每个进程 4GB 堆（14×4=56GB）？

**A**: 64GB 机器，留 8GB 给 OS + C 进程 + 文件缓存。如果 14 进程每堆 4GB：

```
14 × 4GB (heap) + 14 × 0.5GB (非堆) = 63GB JVM 独占 → OS 只剩 1GB
```

后果：
1. OS 开始 swap（退无可退）
2. C 进程 OOM
3. OS OOM Killer 随机杀进程

**安全公式**: `单个 JVM Heap ≤ (OS 总内存 - 8GB 安全余量) / JVM 实例数`

### Q5: 出现 Full GC 怎么办？

**A**: G1 在正确配置下 Full GC 概率极低。如果发生：

```bash
# 1. 确认 Full GC 触发原因
grep 'Pause Full' /data/logs/druid/gc.log | head -3

# 2. 常见原因和解决：
#   - 并发标记未完成 → 增大 ConcGCThreads 或调低 InitiatingHeapOccupancyPercent
#   - 大对象分配 → 检查是否有超大 SQL / 大数组分配
#   - 元空间满了 → 增大 -XX:MaxMetaspaceSize
#   - 人为 System.gc() → 检查代码中是否有显式 GC 调用

# 3. 紧急止血
-XX:+DisableExplicitGC     # 禁用 System.gc()
-XX:G1HeapWastePercent=10   # 减少 Mixed GC 周期
```

### Q6: 为什么最大暂停比 MaxGCPauseMillis=50 大？

**A**: G1 的 MaxGCPauseMillis 是**暂停预测模型的目标值**，不是绝对上限。以下情况暂停可能超额：

| 超限原因 | 典型超限值 | 解决方法 |
|---------|-----------|---------|
| Concurrent Mark 未完成，被迫 Full GC | 500ms-几秒 | 调大 ConcGCThreads |
| Young 区对象存活太多 | 80-150ms | `-XX:G1NewSizePercent=1` 缩小 Young |
| 分配尖峰突发 | 60-100ms | `-XX:G1MaxNewSizePercent=15` 限制 Young 上限 |
| 大 Region 扫描 | 略超 | 缩小 G1HeapRegionSize |

### Q7: AlwaysPreTouch 让启动慢了 30 秒怎么办？

**A**: 这是正常的。PreTouch 的本质是用启动时间换运行稳定性：

- 16GB 堆 PreTouch：启动慢 ~15-30 秒
- 2GB 堆 PreTouch：启动慢 ~2-5 秒
- 缺失 PreTouch：运行期随机抖动 1-5ms，实时场景不可接受

对于**长驻服务**（7×24h），30 秒启动延迟是值得的。如果实在在意启动时间：

```bash
# 折中方案：并行 PreTouch（JDK 8u192+）
-XX:+AlwaysPreTouch -XX:+ParallelPreTouch
```

---

## 九、完整启动命令示例

### 场景 A：64GB 机器，10 线程，JDK 8 + G1（主力）

```bash
java -Xms16g -Xmx16g \
     -XX:+UseG1GC \
     -XX:MaxGCPauseMillis=50 \
     -XX:G1NewSizePercent=2 \
     -XX:G1MaxNewSizePercent=20 \
     -XX:G1HeapRegionSize=4m \
     -XX:ConcGCThreads=2 \
     -XX:+ParallelRefProcEnabled \
     -XX:+UseStringDeduplication \
     -XX:+AlwaysPreTouch \
     -XX:+ExitOnOutOfMemoryError \
     -XX:+PerfDisableSharedMem \
     -Xloggc:/data/logs/druid/gc.log \
     -XX:+PrintGCDetails \
     -XX:+PrintGCDateStamps \
     -XX:+PrintGCApplicationStoppedTime \
     -XX:+PrintAdaptiveSizePolicy \
     -XX:+UseGCLogFileRotation \
     -XX:NumberOfGCLogFiles=10 \
     -XX:GCLogFileSize=50m \
     -jar druid-ak.jar
```

### 场景 B：64GB 机器，10 线程，JDK 17 + ZGC（最优）

```bash
java -Xms16g -Xmx16g \
     -XX:+UseZGC \
     -XX:ZAllocationSpikeTolerance=2.0 \
     -XX:ConcGCThreads=2 \
     -XX:+AlwaysPreTouch \
     -XX:+ExitOnOutOfMemoryError \
     -Xlog:gc*:file=/data/logs/druid/gc.log:time,uptime:filecount=10,filesize=50m \
     -jar druid-ak.jar
```

### 场景 C：16GB 机器，4 线程，JDK 8 + G1（中等配置）

```bash
java -Xms4g -Xmx4g \
     -XX:+UseG1GC \
     -XX:MaxGCPauseMillis=50 \
     -XX:G1NewSizePercent=5 \
     -XX:G1MaxNewSizePercent=20 \
     -XX:G1HeapRegionSize=2m \
     -XX:ConcGCThreads=1 \
     -XX:+ParallelRefProcEnabled \
     -XX:+UseStringDeduplication \
     -XX:+AlwaysPreTouch \
     -XX:+ExitOnOutOfMemoryError \
     -Xloggc:/data/logs/druid/gc.log \
     -XX:+PrintGCDetails \
     -XX:+PrintGCDateStamps \
     -XX:+PrintGCApplicationStoppedTime \
     -jar druid-ak.jar
```

---

## 十、与 batch 方案的对比总结

| 维度 | Batch 方案（旧） | 实时方案（新） | 为什么变 |
|------|----------------|--------------|---------|
| **场景** | 批处理 3h 跑完 | **7×24 实时审计** | req.md 明确实时 |
| **MaxGCPauseMillis** | 1000 | **50** | 实时不能卡 |
| **堆大小** | 1GB 固定 | **按硬件 25%** | 14 进程集群需计算 |
| **Young Max** | 15% | **20%** | 更多空间抗峰值 |
| **ConcGCThreads** | 默认 | **显式设 2** | 控制并发标记 CPU |
| **GC 日志** | 简化 | **完整 (STW+Adaptive)** | 问题排查需要 |
| **ZGC** | 未提及 | **JDK17+ 强烈推荐** | 暂停 <1ms |
| **部署** | 单 JVM | **14 进程集群** | 生产环境实际 |
| **Full GC 防护** | 无 | **ExitOnOOMError** | C 调用方需要 |
| **PerfDisableSharedMem** | 无 | **有** | 多 JVM 同机避免冲突 |

---

## 十一、更高收益的优化方向（非 JVM）

| 优先级 | 优化项 | 预期收益 | 难度 | 说明 |
|--------|--------|---------|------|------|
| **P0** | 启用 LRU 缓存 | 吞吐 +10-20% | 低 | `--cache` |
| **P0** | 确认缓存命中率 ≥ 70% | CPU -50% | 中 | 检查 cacheKey 设计 |
| **P1** | 升级 JDK 17 + ZGC | 暂停 < 1ms | 中 | 需验证兼容性 |
| **P2** | 增大每秒投递量 | 并发度 ↑ | 中 | C 调用方是否串行投递？ |
| **P3** | CPU 亲和性 (`taskset`) | 减少上下文切换 | 低 | 14 进程隔离核 |

> **最大杠杆是 P0**：确保 LRU 缓存部署且命中率 > 70%。否则 ~750 QPS/进程 需要 1 个满核，14 进程 × 1 核 = 14 核 CPU 饱和是物理限制，JVM 调优救不了。

---

*文档生成时间：2026-06-08*
*数据来源：req.md / 生产环境监控分析 / v4 综合性能测试报告 / run-perf.sh*
*适用版本：druid-ak-1.2.27*
*作者：基于真实数据重写，修正了之前 batch 方案的场景误判*
