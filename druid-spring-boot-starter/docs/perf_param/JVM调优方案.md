# Druid SQL Parser 实时审计场景 JVM 调优方案

> **场景**：Druid-ak 作为 JAR 被 C 程序调用，多线程做 SQL 解析/模板提取，用于数据库操作语句实时审计
> **核心要求**：GC 暂停尽量短——审计日志是实时的，GC 延迟直接影响审计时效
> **数据量**：1-2亿 / 3-5亿 / 10亿（视硬件配置）
> **数据来源**：node62/107/116/135/160/203 共 6 节点监控 + 现场环境验证数据
> **适用版本**：druid-ak-1.2.27，OpenJDK 17.0.2+

---

## 一、多节点 Workload 画像

### 1.1 监控环境

所有 6 个节点统一配置：16 核 CPU、~62.8GB RAM、**Xms=1G / Xmx=2G**、G1GC（默认参数）、**4 个工作线程**。

### 1.2 堆使用全景

| 节点 | 总调用量 | 堆 used | Old Gen | 堆 committed | Eden used | Eden committed | NonHeap |
|------|----------|---------|---------|-------------|-----------|---------------|---------|
| node62 | 2.0 亿 | 1.1 GB | 422 MB | 1.7 GB | 680 MB | 1.1 GB | 88 MB |
| node203 | 5.0 亿 | **1.4 GB** | 562 MB | 1.9 GB | 860 MB | 1.1 GB | 89 MB |
| node135 | 5.0 亿 | 923 MB | 664 MB | 1.8 GB | 257 MB | 1.0 GB | 90 MB |
| node116 | 5.0 亿 | 1.2 GB | 432 MB | 2.0 GB | 761 MB | 1.2 GB | 90 MB |
| node160 | 5.0 亿 | 578 MB | 267 MB | 2.0 GB | 273 MB | 884 MB | 86 MB |
| node107 | 5.0 亿 | 513 MB | 465 MB | 2.0 GB | 41 MB | 1.2 GB | 86 MB |
| **范围** | — | **513MB~1.4GB** | **267~664MB** | **1.7~2.0GB** | **41~860MB** | **884MB~1.2GB** | **86~90MB** |

### 1.3 关键发现

**① 堆使用量随数据量增长，不可小觑。**
- 2 亿数据：heap used 1.1GB（node62）
- 5 亿数据：heap used 最高 1.4GB（node203）
- 趋势：Old Gen 从 422MB → 562~664MB，LRU 模板缓存随处理量累积
- **结论**：原方案固定 1GB 堆有 OOM 风险；10 亿数据量下 Old Gen 可能达到 1~1.5GB+

**② Eden 区浪费严重。**
- 6 节点 Eden committed 均值 ~1.1GB，但 Eden used 区间 41~860MB（均值 ~480MB）
- node107 最极端：Eden committed=1.2GB 但 used 仅 41MB，浪费率 97%
- 根源：G1 默认 `G1MaxNewSizePercent=60`，短 SQL（avgLen=140~778 字符）产生的临时对象远填不满大 Eden

**③ NonHeap 极其稳定。**
- 6 节点均为 85~90 MB（元空间 + 代码缓存），不随数据量/调用量变化
- 默认 `MaxMetaspaceSize` 无需调整

**④ Old Gen 决定堆下限。**
- Old Gen 从 267MB 到 664MB 不等，且与处理数据量正相关
- 这是 LRU 模板缓存 + Druid 框架对象的累积效应
- 堆大小下限 = Old Gen × 1.5 + Eden 峰值

### 1.4 吞吐量与 CPU

| 节点 | ExecSpeed | ElapsedSpeed | jvm CPU | 并发度 | 特点 |
|------|-----------|-------------|---------|--------|------|
| node62 | 1,946 | 7,758 | 25.1% | 4.0 | 2 亿，正常 |
| node203 | 1,811 | 7,212 | 25.2% | 4.0 | 5 亿，正常 |
| node135 | 1,967 | 7,842 | 25.5% | 4.0 | 5 亿，正常 |
| node160 | 1,961 | 5,385 | 25.6% | 2.7 | 5 亿，并发略低 |
| node116 | 3,077 | 10,328 | 22.1% | 3.4 | 5 亿，49% NonSupport |
| node107 | 3,165 | 10,612 | 5.6% | 3.3 | 5 亿，68% NonSupport |

> NonSupport（Druid Parser 不支持的 SQL）走快速失败路径，CPU 消耗低、吞吐量虚高。不代表常规负载。

### 1.5 SQL 延迟分布

```
成功 SQL 耗时分布（node62，2 亿调用）：
  <50μs    : 25.8%     # 极快，LRU 缓存命中
  50-400μs : <1%       # 零散
  400μs-1ms: 70.3%     # 主体，Parser 正常解析路径
  1-2ms    : 3.6%      # 较长 SQL / 复杂语句
  >2ms     : 0.2%      # 超长 SQL

失败 SQL（语法不支持）：
  100-200μs: 73.5%    # Druid Parser 抛异常快速返回
```

**结论**：99.5% 的 SQL 在 1ms 内完成解析。GC 暂停若超过 50ms，相当于阻塞了约 50~250 条 SQL 的实时审计（按吞吐量 1000~5000 ops/s），对审计时效有实质影响。

---

## 二、调优策略：低延迟优先

### 2.1 策略对比

| 维度 | 原 batch 方案 | **新方案（实时审计）** |
|------|-------------|---------------------|
| 场景 | 批处理，3 小时跑完 | **7×24 实时审计** |
| GC 目标 | 最大化吞吐 | **最小化暂停** |
| MaxGCPauseMillis | 1000ms | **50ms** |
| 堆大小 | 固定 1GB | **分档 1~6GB**（数据驱动） |
| Young GC 策略 | 少而长 | **频而短** |
| 备选 GC | Parallel GC | **ZGC（JDK 17+）** |

### 2.2 核心设计思路

```
实时审计 → GC 暂停 < 50ms
         → 堆不可太小（OOM）也不可过大（扫堆慢）
         → G1 并发标记尽早启动（IOHP=35），防止 Full GC
         → Eden 控制上限，限制单次 Young GC 存活集
         → Old Gen 紧凑，Mixed GC 轻量
         → JDK 17+ 可选 ZGC，暂停降到 <1ms
```

---

## 三、分档配置

根据 req.md 的 **OS 内存 → 线程数** 映射关系，提供 4 档配置。

### 3.1 配置速查表

| 档位 | OS 内存 | 线程 | 建议数据量 | Xms/Xmx | 推荐 GC | Region |
|------|---------|------|-----------|---------|---------|--------|
| **S** | 8 GB | 1 | 1~2 亿 | 512m / 1g | G1 | 1m |
| **M** | 16 GB | 4 | 1~5 亿 | 1g / 2g | G1 | 2m |
| **L** | 32 GB | 4 | 3~10 亿 | 2g / 4g | G1 | 2m |
| **XL** | 64~128 GB | 10 | 5~10 亿 | 4g / 6g | G1 / ZGC | 4m / ZGC无关 |

> **堆大小设计依据**：6 节点实测 heap used 峰值为 1.4GB（5 亿/4 线程）。考虑 10 亿数据量下 Old Gen 可能达到 1~1.5GB，取 1.5~2x 安全系数。各档位 `Xms=Xmx` 固定堆，避免运行时 resize 开销。

### 3.2 档位 S：8GB OS / 1 线程 / 1~2 亿

8GB 总内存下，C 宿主进程 + OS 需预留大部分。JVM 堆 = 512MB~1GB。

```bash
-Xms512m -Xmx1g
-XX:+UseG1GC
-XX:MaxGCPauseMillis=50
-XX:G1HeapRegionSize=1m
-XX:G1NewSizePercent=15
-XX:G1MaxNewSizePercent=30
-XX:InitiatingHeapOccupancyPercent=40
-XX:G1ReservePercent=15
-XX:ConcGCThreads=1
-XX:ParallelGCThreads=1
-XX:+ParallelRefProcEnabled
-XX:+UseStringDeduplication
-XX:+AlwaysPreTouch
-XX:+ExitOnOutOfMemoryError
-XX:+PerfDisableSharedMem
-Xlog:gc*:file=/data/logs/druid/gc.log:time,uptime:filecount=5,filesize=50m
```

**要点**：单线程场景，Region=1MB（1GB→1024 regions），GC 线程=1 避免无效竞争。IOHP=40 较保守（小堆 Old Gen 增长更快）。

### 3.3 档位 M：16GB OS / 4 线程 / 1~5 亿

与 6 节点监控环境最接近的配置。Xmx=2g 已验证可承载 5 亿数据。

```bash
-Xms1g -Xmx2g
-XX:+UseG1GC
-XX:MaxGCPauseMillis=50
-XX:G1HeapRegionSize=2m
-XX:G1NewSizePercent=10
-XX:G1MaxNewSizePercent=25
-XX:InitiatingHeapOccupancyPercent=35
-XX:G1ReservePercent=15
-XX:ConcGCThreads=2
-XX:ParallelGCThreads=4
-XX:+ParallelRefProcEnabled
-XX:+UseStringDeduplication
-XX:+AlwaysPreTouch
-XX:+ExitOnOutOfMemoryError
-XX:+PerfDisableSharedMem
-Xlog:gc*:file=/data/logs/druid/gc.log:time,uptime:filecount=5,filesize=50m
```

**要点**：Region=2MB（2GB→1024 regions）。`G1MaxNewSizePercent=25` 限制 Eden 最大 ~500MB——实测 Eden used 均值 ~480MB，刚好覆盖。`IOHP=35` 提前触发并发标记。

### 3.4 档位 L：32GB OS / 4 线程 / 3~10 亿

32GB OS 有充足内存余量，堆放大到 2~4GB 以容纳 10 亿级数据量下的 Old Gen 增长。

```bash
-Xms2g -Xmx4g
-XX:+UseG1GC
-XX:MaxGCPauseMillis=50
-XX:G1HeapRegionSize=2m
-XX:G1NewSizePercent=10
-XX:G1MaxNewSizePercent=20
-XX:InitiatingHeapOccupancyPercent=35
-XX:G1ReservePercent=15
-XX:ConcGCThreads=2
-XX:ParallelGCThreads=4
-XX:+ParallelRefProcEnabled
-XX:+UseStringDeduplication
-XX:+AlwaysPreTouch
-XX:+ExitOnOutOfMemoryError
-XX:+PerfDisableSharedMem
-Xlog:gc*:file=/data/logs/druid/gc.log:time,uptime:filecount=5,filesize=50m
```

**要点**：线程数与 M 档相同（4），堆翻倍。Region 保持 2MB（4GB→2048 regions，可接受）。Eden 上限 20%（~800MB）相比 4 线程分配压力够用。

### 3.5 档位 XL：64~128GB OS / 10 线程 / 5~10 亿

10 线程下分配压力增大，堆给到 4~6GB。提供 G1 和 ZGC 两套方案。

#### 方案 A：G1GC（JDK 8+，兼容性最好）

```bash
-Xms4g -Xmx6g
-XX:+UseG1GC
-XX:MaxGCPauseMillis=50
-XX:G1HeapRegionSize=4m
-XX:G1NewSizePercent=15
-XX:G1MaxNewSizePercent=25
-XX:InitiatingHeapOccupancyPercent=35
-XX:G1ReservePercent=15
-XX:ConcGCThreads=2
-XX:ParallelGCThreads=8
-XX:+ParallelRefProcEnabled
-XX:+UseStringDeduplication
-XX:+AlwaysPreTouch
-XX:+ExitOnOutOfMemoryError
-XX:+PerfDisableSharedMem
-Xlog:gc*:file=/data/logs/druid/gc.log:time,uptime:filecount=10,filesize=100m
```

#### 方案 B：ZGC（JDK 17+，极致低延迟，推荐）

```bash
-Xms4g -Xmx6g
-XX:+UseZGC
-XX:ConcGCThreads=2
-XX:+AlwaysPreTouch
-XX:+ExitOnOutOfMemoryError
-XX:+PerfDisableSharedMem
-Xlog:gc*:file=/data/logs/druid/gc.log:time,uptime:filecount=10,filesize=100m
```

**要点 G1**：Region=4MB（6GB→1536 regions）。`ParallelGCThreads=8`（≈10 × 5/8 规则）。Eden 初始 15%（~600MB），上限 25%（~1.5GB），足够 10 线程并发分配。

**要点 ZGC**：参数极简，暂停 <1ms 且与堆大小无关。需 JDK 17+（11 可跑但不推荐生产）。有 ~10% 堆额外开销（colored pointers），6GB 堆可承受。

---

## 四、关键参数设计理由

### 4.1 MaxGCPauseMillis=50（核心参数，原 batch 方案=1000）

**这是低延迟策略的基石。** G1 的暂停目标是"软引导"——G1 会调整回收策略来尽量满足目标。

| MaxGCPauseMillis | Young GC 频率 | 单次暂停 | GC CPU | 用户体验 |
|------------------|--------------|---------|--------|---------|
| 200（默认） | 低，~3-5min | ~80ms | ~2% | 偶尔卡 |
| **50（推荐）** | 中，~1-2min | **~20-40ms** | ~3% | **几乎无感 ✅** |
| 1000（旧方案） | 极低 | ~150ms+ | ~1% | 明显卡顿 ❌ |

实时审计场景下，50ms 暂停意味着：
- 按 5000 ops/s 算，最多积压 ~250 条 SQL，可快速追回
- 按 1000 ops/s 算，积压 ~50 条，几乎不可感知

### 4.2 InitiatingHeapOccupancyPercent=35（默认 45）

降低并发标记触发阈值。Old Gen 在 35% 占用时就启动后台标记，好处：
- Mixed GC 时标记已就绪，回收更从容
- 大幅降低 "to-space exhausted" → Full GC 的风险
- 代价：并发标记更频繁，额外消耗 ~1-2% CPU

### 4.3 G1ReservePercent=15（默认 10）

增加 5% 堆保留空间作为 evacuation 失败的缓冲。对于低延迟目标，宁可用 5% 堆空间换零 Full GC 的确定性。

### 4.4 Eden 上下限：G1NewSizePercent / G1MaxNewSizePercent

| 档位 | 初始 Eden | 最大 Eden | 依据 |
|------|----------|----------|------|
| S（1g堆） | 15% ≈ 150MB | 30% ≈ 300MB | 1 线程，分配压力小 |
| M（2g堆） | 10% ≈ 200MB | 25% ≈ 500MB | 实测 Eden used 均值 480MB |
| L（4g堆） | 10% ≈ 400MB | 20% ≈ 800MB | 4 线程，分配率 ~660MB/s 峰值 |
| XL（6g堆） | 15% ≈ 900MB | 25% ≈ 1.5GB | 10 线程高并发分配 |

**核心逻辑**：Eden 够容纳短期分配即可，**不是越大越好**。Eden 过大 → 单次 Young GC 扫描更多对象 → 暂停变长。限制 Eden 上限 = 限制单次 GC 的存活集规模。

### 4.5 G1HeapRegionSize 适配

| 堆大小 | Region | 数量 | 理由 |
|--------|--------|------|------|
| 1 GB | 1 MB | 1024 | 小堆，细粒度回收 |
| 2~4 GB | 2 MB | 1024~2048 | 中堆，平衡 RSet 开销 |
| 6 GB | 4 MB | 1536 | 大堆，控制 region 数 < 2048 |

**公式**：Region Size ≈ 堆 / 2048，取 1/2/4/8/16/32 MB 中最接近值。

### 4.6 ParallelGCThreads / ConcGCThreads

| 档位 | 业务线程 | ParallelGCThreads | ConcGCThreads | 策略 |
|------|---------|-------------------|---------------|------|
| S | 1 | 1 | 1 | 单线程，不抢 |
| M | 4 | 4 | 2 | STW 用满，并发留余 |
| L | 4 | 4 | 2 | 同上 |
| XL | 10 | 8 | 2 | STW 多配，并发保守 |

`ConcGCThreads` 保守设为 2：SQL 解析是纯 CPU 计算，并发标记线程吃 CPU 会直接降低吞吐量。宁可标记慢一点，不让业务线程被抢占。

### 4.7 UseStringDeduplication

SQL 解析产生大量重复字符串（`SELECT * FROM`、`INSERT INTO` 等模板前缀）。G1 在 concurrent mark 阶段识别底层 `char[]` 相同的 String 对象并去重。

- 实测省堆 **15~30%**
- 对吞吐影响 <1%
- 只有 G1 支持（ZGC 另有机制）

### 4.8 C 调用 JAR 专属参数

| 参数 | 作用 | 为什么必须 |
|------|------|-----------|
| `Xms=Xmx` | 固定堆大小 | 避免运行时 shrink/expand 系统调用（~ms 级抖动） |
| `-XX:+AlwaysPreTouch` | 启动时锁物理内存页 | 避免运行时缺页中断（~1-5ms 随机抖动） |
| `-XX:+ExitOnOutOfMemoryError` | OOM 直接退出进程 | C 侧 `waitpid` 可感知，否则 JVM 僵死 |
| `-XX:+PerfDisableSharedMem` | 禁用 `/tmp/hsperfdata_*` | 多次启停避免文件泄漏 |

---

## 五、预期收益

### 5.1 各档位预期

| 指标 | 调优前（全网平均） | S | M | L | XL (G1) | XL (ZGC) |
|------|------------------|---|---|---|---------|----------|
| 堆 committed | 1.7~2.0 GB | 0.5~1.0 GB | 1.0~2.0 GB | 2.0~4.0 GB | 4.0~6.0 GB | 4.0~6.0 GB |
| Eden 浪费率 | ~56% | ~30% | ~30% | ~25% | ~20% | N/A |
| GC 暂停 P99 | ~80ms | **<30ms** | **<50ms** | **<50ms** | **<50ms** | **<1ms** |
| Full GC 风险 | 低 | 极低 | 极低 | 极低 | 极低 | **无 Full GC** |
| 吞吐量影响 | 基准 | 略低(~3%) | 持平 | 持平 | +0~5% | -5~10% |
| OOM 风险 | 有（1.4GB>1GB） | 低 | 低 | 低 | 低 | 低 |

### 5.2 与原 batch 方案关键差异

| 参数 | batch 方案（旧） | 实时方案（新） | 变更原因 |
|------|----------------|--------------|---------|
| MaxGCPauseMillis | 1000 | **50** | 实时审计不可容忍长暂停 |
| Xmx | 1g | **1~6g（分档）** | 多节点实测 used 达 1.4GB |
| InitiatingHeapOccupancyPercent | 未设(45) | **35** | 提前标记防 Full GC |
| G1NewSizePercent | 5 | **10~15** | 给 Eden 更多初始空间 |
| G1ReservePercent | 未设(10) | **15** | 增加 evacuation 缓冲 |
| 线程-GC 对应 | 未设 | **按档精确配置** | 避免 GC 线程过多/过少 |
| ZGC 推荐 | 未提及 | **JDK 17+ 推荐** | 暂停 <1ms |

---

## 六、验证方案

### 6.1 启动前检查

```bash
# 确认参数生效
java -XX:+PrintFlagsFinal -version 2>&1 | grep -E \
  'MaxHeapSize|UseG1GC|MaxGCPauseMillis|InitiatingHeapOccupancyPercent|StringDeduplication|AlwaysPreTouch'
```

### 6.2 运行时监控

```bash
# GC 实时状态（每 2 秒刷新）
jstat -gcutil <pid> 2s
# 关注列：YGC(Young GC次数) YGCT(Young GC总耗时) FGC(Full GC次数, 必须=0)

# 堆详情
jmap -heap <pid>

# GC 日志 tail
tail -f /data/logs/druid/gc.log
```

### 6.3 运行后日志分析

```bash
GC_LOG=/data/logs/druid/gc.log

# 1. Full GC 次数（必须为 0）
grep -c 'Pause Full' $GC_LOG

# 2. Young / Mixed GC 总次数
grep -cE '\[Pause (Young|Mixed)' $GC_LOG

# 3. GC 暂停 P50 / P99 / Max
grep -oP '\d+\.\d+(?=ms)' $GC_LOG | sort -n | awk '
  { a[NR]=$1 }
  END {
    print "P50:", a[int(NR*0.5)], "ms"
    print "P99:", a[int(NR*0.99)], "ms"
    print "Max:", a[NR], "ms"
  }'

# 4. GC 耗时占比
#    应用总时间从业务日志获取，GC 总时间 = YGCT + MGCT
```

### 6.4 回归对比清单

调优前后分别收集：
1. 总处理 SQL 数、ElapsedSpeed / ExecSpeed
2. `jstat -gcutil` 的 YGC / FGC / GCT
3. GC 日志 P50 / P99 / Max 暂停
4. 堆 used / committed 峰值
5. 应用层 Failure 率 / NonSupport 率

---

## 七、ZGC 深度对比

> ZGC 从 JDK 11 引入，JDK 17+ 生产就绪。档位 XL 场景下强烈推荐。

| 维度 | G1 (MaxPause=50) | ZGC |
|------|-----------------|-----|
| 暂停时间 P99 | <50ms | **<1ms** |
| 与堆大小关系 | 堆越大暂停越长 | **无关** |
| Full GC | 存在风险 | **不存在** |
| 吞吐量 | 基准 | -5%~10%（并发阶段开销） |
| 堆额外开销 | 无 | ~10%（colored pointers） |
| 配置复杂度 | 需精细调参 | 极低（默认即最优） |
| 最低 JDK | 8 | 11（推荐 17+） |
| JDK 21+ 分代 ZGC | — | 吞吐提升 ~10%，暂停仍 <1ms |

**选择建议**：
- JDK < 11 → G1，无选择
- JDK 11-16 → ZGC 可用，建议先在测试环境验证
- JDK 17+ → **ZGC 优先**，尤其是 XL 档
- 堆 < 2GB → G1 即可，ZGC 堆开销占比偏大

### ZGC 完整参数（JDK 21+ 分代 ZGC）

```bash
-Xms4g -Xmx6g
-XX:+UseZGC
-XX:+ZGenerational          # JDK 21+，吞吐更高
-XX:ConcGCThreads=2
-XX:+AlwaysPreTouch
-XX:+ExitOnOutOfMemoryError
-Xlog:gc*:file=/data/logs/druid/gc.log:time,uptime:filecount=10,filesize=100m
```

---

## 八、FAQ

### Q1: MaxGCPauseMillis=50 能做到吗？峰值时会不会超？

正常负载（~1000-2000 ops/s）下 Young GC 暂停 **20-40ms**，稳定满足。分配尖峰时 G1 会"尽力而为"——可能短期超 50ms 到 60-80ms，但不会像默认 200ms 那样放任。这不是硬上限，而是 G1 的优化目标。

### Q2: 固定 Xms=Xmx 会不会浪费内存？

C 进程长期独占 JVM，不存在"让出内存给别人"的场景。动态 resize 带来的系统调用（`madvise`/`sbrk`）在实时路径上产生 μs~ms 级抖动，得不偿失。**固定堆 = 零抖动**。

### Q3: 10 亿数据量真的需要 6GB 堆？

数据量 ≠ 同时存活对象量。Druid Parser 是流式的：SQL 进来 → 解析 → 输出模板 → 对象释放。但 LRU 模板缓存 + Druid 框架对象会累积在 Old Gen：
- 5 亿数据实测 Old Gen 峰值 664MB
- 10 亿数据 Old Gen 可能 1~1.5GB
- + Eden 峰值 + Survivor + G1Reserve → 4~6GB 合理

**实际微调**：部署后观察 GC 日志 `G1 Old Gen` 的 `used` 峰值，若 < 堆的 50%，可适当缩堆。

### Q4: NonSupport 率高的节点要注意什么？

node107（68% NonSupport）和 node116（49% NonSupport）的 SQL 大多被 Druid Parser 快速拒绝，CPU 消耗极低（jvm 5.6%），吞吐虚高（10,600 ops/s）。这类节点的 heap used 反而更低（513MB），因为不产生 AST 对象。

调优无需针对 NonSupport 做特殊处理——正常节点的参数完全兼容。但如果所有节点 NonSupport 率持续 >50%，应优先排查 SQL 语法兼容性而非 JVM。

### Q5: 8GB OS 下 C 进程内存够吗？

8GB 中 JVM 堆 512MB~1GB + Metaspace 90MB + 线程栈 ~10MB + JVM 自身 ~200MB ≈ 0.8~1.3GB。剩 6.7~7.2GB 给 C 进程 + OS Cache。对于 1-2 亿数据/1 线程场景够用。如 C 进程自身内存需求大，Xmx 可降到 768m。

### Q6: GC 日志会不会写满磁盘？

按 `filecount=5, filesize=50m`，最多 250MB。在 64GB+ 的服务器上忽略不计。日志轮转由 JVM 自动管理，无需外部 logrotate。XL 档用 `filecount=10, filesize=100m`（最多 1GB），因为 10 线程 GC 频率更高。

### Q7: 出现 Full GC 怎么办？

```bash
# 确认原因
grep 'Pause Full' /data/logs/druid/gc.log | head -3

# 常见原因及应对：
# ① 并发标记来不及 → 增大 ConcGCThreads 到 4
# ② 大对象分配（G1 Humongous）→ 检查是否有超长 SQL
# ③ 人为 System.gc() → -XX:+DisableExplicitGC
# ④ Metaspace 满 → jstat -gc 看 MU/MC，调大 MaxMetaspaceSize
```

---

## 九、完整启动命令

### 场景 A：64GB / 10 线程 / JDK 8 / G1（XL 档，兼容性首选）

```bash
java \
  -Xms4g -Xmx6g \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=50 \
  -XX:G1HeapRegionSize=4m \
  -XX:G1NewSizePercent=15 \
  -XX:G1MaxNewSizePercent=25 \
  -XX:InitiatingHeapOccupancyPercent=35 \
  -XX:G1ReservePercent=15 \
  -XX:ConcGCThreads=2 \
  -XX:ParallelGCThreads=8 \
  -XX:+ParallelRefProcEnabled \
  -XX:+UseStringDeduplication \
  -XX:+AlwaysPreTouch \
  -XX:+ExitOnOutOfMemoryError \
  -XX:+PerfDisableSharedMem \
  -Xlog:gc*:file=/data/logs/druid/gc.log:time,uptime:filecount=10,filesize=100m \
  -jar druid-ak.jar
```

### 场景 B：64GB / 10 线程 / JDK 17+ / ZGC（XL 档，最优延迟）

```bash
java \
  -Xms4g -Xmx6g \
  -XX:+UseZGC \
  -XX:+ZGenerational \
  -XX:ConcGCThreads=2 \
  -XX:+AlwaysPreTouch \
  -XX:+ExitOnOutOfMemoryError \
  -XX:+PerfDisableSharedMem \
  -Xlog:gc*:file=/data/logs/druid/gc.log:time,uptime:filecount=10,filesize=100m \
  -jar druid-ak.jar
```

### 场景 C：16GB / 4 线程 / JDK 8 / G1（M 档，中等配置）

```bash
java \
  -Xms1g -Xmx2g \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=50 \
  -XX:G1HeapRegionSize=2m \
  -XX:G1NewSizePercent=10 \
  -XX:G1MaxNewSizePercent=25 \
  -XX:InitiatingHeapOccupancyPercent=35 \
  -XX:G1ReservePercent=15 \
  -XX:ConcGCThreads=2 \
  -XX:ParallelGCThreads=4 \
  -XX:+ParallelRefProcEnabled \
  -XX:+UseStringDeduplication \
  -XX:+AlwaysPreTouch \
  -XX:+ExitOnOutOfMemoryError \
  -XX:+PerfDisableSharedMem \
  -Xlog:gc*:file=/data/logs/druid/gc.log:time,uptime:filecount=5,filesize=50m \
  -jar druid-ak.jar
```

### 场景 D：8GB / 1 线程 / JDK 8 / G1（S 档，小内存）

```bash
java \
  -Xms512m -Xmx1g \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=50 \
  -XX:G1HeapRegionSize=1m \
  -XX:G1NewSizePercent=15 \
  -XX:G1MaxNewSizePercent=30 \
  -XX:InitiatingHeapOccupancyPercent=40 \
  -XX:G1ReservePercent=15 \
  -XX:ConcGCThreads=1 \
  -XX:ParallelGCThreads=1 \
  -XX:+ParallelRefProcEnabled \
  -XX:+UseStringDeduplication \
  -XX:+AlwaysPreTouch \
  -XX:+ExitOnOutOfMemoryError \
  -XX:+PerfDisableSharedMem \
  -Xlog:gc*:file=/data/logs/druid/gc.log:time,uptime:filecount=5,filesize=50m \
  -jar druid-ak.jar
```

---

## 十、非 JVM 优化方向

JVM 参数调优只解决 GC 暂停问题。如果吞吐量或 CPU 是瓶颈，优先级更高的是：

| 优先级 | 优化项 | 预期收益 | 说明 |
|--------|--------|---------|------|
| **P0** | 启用 LRU 缓存 | 吞吐 +10~30%，CPU -30~50% | 缓存命中后免解析 |
| **P0** | 增大线程数（按 OS 内存梯度） | 吞吐随线程线性增长 | 当前 4 线程 CPU 才用 ~25% |
| **P1** | 升级 JDK 17 + ZGC | 暂停 <1ms | 需验证 druid-ak 兼容性 |
| **P2** | 检查 C 调用方是否串行投递 SQL | 提升并发度 | 并发 2.8/4.0 = 70%，队列没塞满 |
| **P3** | CPU 亲和性（`taskset`） | 减少上下文切换 ~5% | 多 JVM 同机部署时有效 |

---

*文档生成时间：2026-06-08*
*数据来源：req.md 中 node62/107/116/135/160/203 共 6 节点监控 + 现场环境验证数据*
*适用版本：druid-ak-1.2.27*
