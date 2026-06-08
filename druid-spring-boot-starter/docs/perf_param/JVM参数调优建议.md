# Druid-ak JVM 参数调优建议

> **场景**：druid-ak.jar 被 C 程序调用，多线程（1-10）做 SQL 解析/模板提取，用于数据库操作语句实时审计。
> **核心约束**：GC 暂停尽量短（实时场景），内存与其他程序共享，不独占。
> **数据规模**：1-2 亿/天（~1100-2350 SQL/s）、3-5 亿/天（~3500-5800 SQL/s）、峰值 10 亿/天（~11600 SQL/s）。
> **线程-内存映射**：8G→1线程，16G→4线程，32G→4线程，64-128G→10线程。
> **JDK 版本**：推荐 OpenJDK 17+（ZGC 可用），最低 JDK 8（G1）。

---

## 一、总体思路

### 1.1 问题本质

Druid SQL 解析的内存行为是**"流式分配-快速回收"**：

```
SQL 进入 → Parser 词法/LALR 分析 → 构建 AST → 输出模板 → 对象立即变垃圾
         └────────── ~0.5ms ──────────┘
```

绝大部分对象生命周期 < 1ms，在下次 Young GC 时被回收。因此：

- **年轻代（Eden）**：由吞吐量（SQL/s × SQL长度）决定 —— 新对象在这里分配和死亡
- **老年代（Old Gen）**：由模板种类数决定 —— LRU 模板缓存随运行时间累积，不随吞吐量波动
- **Metaspace**：极其稳定，~90MB，无需调优

### 1.2 核心原则

| 原则 | 说明 |
|------|------|
| **低暂停优先** | `MaxGCPauseMillis=50`（默认 200），宁频勿长 |
| **固定堆** | `Xms=Xmx`，避免运行时 shrink/expand 的 ms 级抖动 |
| **Old Gen 决定堆下限** | 模板缓存只增不减，堆太小 → OOM |
| **Eden 决定吞吐量** | Eden 太小 → Young GC 过于频繁 → CPU 浪费 |
| **堆不能太大** | 内存与其他程序共享；堆越大 G1 扫堆越慢，ZGC 无此问题 |

### 1.3 GC 选择

| GC | 暂停 | 适用 JDK | 适用堆 | 推荐场景 |
|----|------|---------|--------|---------|
| **G1** | <50ms | 8+ | 1-6GB | 通用，所有档位 |
| **ZGC** | <1ms | 17+ | 任意 | XL 档（64G+ / 10 线程）首选 |

---

## 二、监控数据分析

### 2.1 环境与数据概览

6 个节点统一配置：16 核 CPU、~62.8GB RAM、**Xms=1G / Xmx=2G**、G1GC 默认参数、**4 个工作线程**。

| 节点 | 总调用量 | 堆 used | Old Gen | Eden used | NonSupport | 特征 |
|------|---------|---------|---------|-----------|------------|------|
| node62 | 2.0 亿 | 1.1 GB | 422 MB | 680 MB | 0.06% | 正常对照 |
| node203 | 5.0 亿 | **1.4 GB** | 562 MB | 860 MB | 0.17% | 正常对照 |
| node135 | 5.0 亿 | 923 MB | **664 MB** | 257 MB | 0% | Old Gen 最大 |
| node160 | 5.0 亿 | 578 MB | 267 MB | 273 MB | 0.22% | Old Gen 最小 |
| node116 | 5.0 亿 | 1.2 GB | 432 MB | 761 MB | 49% | NonSupport 多 |
| node107 | 5.0 亿 | 513 MB | 465 MB | 41 MB | 68% | NonSupport 极多 |

### 2.2 关键发现

**① 堆使用量随数据量增长。**
- 2 亿数据：heap used 1.1GB → Old Gen 422MB
- 5 亿数据：heap used 最高 1.4GB → Old Gen 峰值 664MB
- 趋势线：Old Gen ≈ 200 + 数据量亿数 × 80 MB
- **结论**：原 1GB 固定堆无法承载 5 亿+ 数据量。10 亿数据 Old Gen 预估 1~1.5GB

**② Eden 区严重浪费。**
- 6 节点 Eden committed 均值 ~1.1GB，但 used 均值仅 ~480MB
- 浪费率 ~56%，根源是 G1 默认 `G1MaxNewSizePercent=60` 太大
- **结论**：限制 Eden 上限到 ~25%（而非默认 60%）

**③ NonHeap 极其稳定。** 6 节点均为 85~90 MB，不随数据量变化。

**④ NonSupport 高的节点特殊。** node107（68% NonSupport）heap used 仅 513MB，因为不支持语法走快速失败路径不产生 AST 对象。CPU 仅 5.6%，吞吐虚高至 10600 ops/s。调优参数与正常节点兼容。

### 2.3 吞吐量与 CPU

| 节点 | ExecSpeed | ElapsedSpeed | JVM CPU% | 并发度 |
|------|-----------|-------------|----------|--------|
| node62 | 1,946 | 7,758 | 25.1% | 4.0 |
| node203 | 1,811 | 7,212 | 25.2% | 4.0 |
| node135 | 1,967 | 7,842 | 25.5% | 4.0 |
| node160 | 1,961 | 5,385 | 25.6% | 2.7 |

4 线程下 CPU 仅用 ~25%（16 核），吞吐量瓶颈不在 JVM 而在数据喂入端。

---

## 三、内存估算公式

### 3.1 问题

> 是否可以依据 **SQL/s + 并发数 + SQL 长度** 来估算 JVM 堆大小？

**答案：可以。** 分两层估算：年轻代由吞吐量驱动、老年代由模板种类驱动。

### 3.2 分配膨胀系数 K 的推导

SQL 解析时，每字节输入在堆上产生的临时对象包括：
- 字符数组拷贝（词法分析缓冲、归一化输出）
- AST 节点（每个 token/表达式一个对象，含双向引用）
- 中间字符串（标识符规范化、字面量提取）
- HashMap Entry（缓存查找）

定义 **K = 堆分配字节数 / SQL 原始字节数**。

**从监控数据回算 K**（G1 默认参数，Young GC 间隔约 2~3 分钟）：

| 节点 | R (SQL/s) | L (bytes) | 分配速率 | per-SQL | **K** |
|------|-----------|-----------|---------|---------|-------|
| node62 | 1,946 | 377 | ~6.7 MB/s | ~3.6 KB | **9.5×** |
| node203 | 1,811 | 405 | ~6.3 MB/s | ~3.6 KB | **8.9×** |
| node135 | 1,967 | 198 | ~5.5 MB/s | ~2.9 KB | **14.6×** |
| **保守取整** | — | — | — | — | **K = 15** |

> 分配速率 `AR = R × L × K`，**不乘以线程数**。线程数影响的是并发度（同时处理的 SQL 数），总分配速率仅取决于总吞吐量 R。

### 3.3 分步计算公式

#### Step 1：分配速率
```
AR = R × L × K    [bytes/s]
```

#### Step 2：Eden 容量
```
E = AR × T_gc

T_gc = 目标 Young GC 间隔：
  - MaxGCPauseMillis=50  → T_gc ≈ 60s
  - MaxGCPauseMillis=200 → T_gc ≈ 150s
```

#### Step 3：Survivor
```
S = E × 0.15    （两个 Survivor 区合计）
```

#### Step 4：Old Gen
```
O_MB ≈ 200 + N_billion × 80

N_billion = 累计处理记录数（亿）
```

| 累计处理量 | 实测 Old Gen | 公式估算 | 误差 |
|-----------|-------------|---------|------|
| 2 亿 (node62) | 422 MB | 360 MB | -15% |
| 5 亿 (node203) | 562 MB | 600 MB | +7% |
| 5 亿 (node135) | 664 MB | 600 MB | -10% |
| 10 亿 (预测) | — | 1000 MB | — |

#### Step 5：总堆
```
H = (E + S + O) / (1 - ReservePercent)

取 ReservePercent=15%：
H_MB = (R × L × K × T_gc / 1048576 × 1.15 + O_MB) / 0.85
```

### 3.4 快速估算公式

代入推荐值 K=15, T_gc=60：
```
H_MB ≈ (R × L × 0.00099 + 200 + N_billion×80) / 0.85
```

### 3.5 公式验证

| 场景 | R | L | 数据量 | 公式 H | 推荐档位 | 评估 |
|------|---|---|--------|--------|---------|------|
| node62 | 1,946 | 377 | 2 亿 | ~1.2 GB | M(2GB) | 有余量 ✓ |
| node203 | 1,811 | 405 | 5 亿 | ~1.5 GB | M(2GB) | 有余量 ✓ |
| 10亿中等 | 5,000 | 500 | 10 亿 | ~3.9 GB | L(4GB) | 吻合 ✓ |
| **10亿峰值** | **11,600** | 500 | 10 亿 | ~7.9 GB | XL(6GB) | ⚠️ 偏紧 |

> ⚠️ 峰值 11600 SQL/s 时 XL 档 6GB 偏紧，推荐升级 ZGC + 增大堆至 8GB。

### 3.6 特殊场景：高重复率 SQL

从 req.md 线索：`Xmx=128m 曾处理 7000 avgLen 9亿/天`。当 SQL 模板高度集中时（重复率 >99%），LRU 缓存命中率极高，Old Gen 极小，128MB 堆即可。**反之**，若模板分散，Old Gen 将按上述公式增长。

> 建议：先按公式粗估 → 部署 → 观察 GC 日志 Old Gen 实际占用 → 第 2 次调整。

---

## 四、分档配置建议

根据 **OS 内存 → 线程数** 映射，分四档。所有档位 `Xms=Xmx` 固定堆。

### 4.1 配置速查

| 档位 | OS 内存 | 线程 | 建议 Xmx | GC | Region | 适用数据量 |
|------|---------|------|---------|-----|--------|-----------|
| **S** | 8 GB | 1 | **1 GB** | G1 | 1 MB | 1-2 亿 |
| **M** | 16 GB | 4 | **2 GB** | G1 | 2 MB | 1-5 亿 |
| **L** | 32 GB | 4 | **4 GB** | G1 | 2 MB | 3-10 亿 |
| **XL** | 64-128 GB | 10 | **6 GB** | ZGC 优先 | 4 MB(G1) | 5-10 亿 |

### 4.2 档位 S：8GB OS / 1 线程

8GB 与其他程序共享。JVM 堆固定 1GB，留 6.7GB 给 C 宿主 + OS。

```bash
-Xms1g -Xmx1g
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

**设计要点**：Region=1MB（1024 regions），GC 线程=1，IOHP=40（小堆宽容）。

### 4.3 档位 M：16GB OS / 4 线程

最接近现有监控环境的配置。Xmx=2g 已验证可承载 5 亿数据。

```bash
-Xms2g -Xmx2g
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

**设计要点**：Region=2MB（1024 regions），Eden 上限 25%（~500MB，等于实测均值 480MB）。IOHP=35 提前触发并发标记防 Full GC。

### 4.4 档位 L：32GB OS / 4 线程

```bash
-Xms4g -Xmx4g
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

**设计要点**：Region=2MB（2048 regions），Eden 上限 20%（~800MB）。4 线程 + 4GB 堆，余量充足。

### 4.5 档位 XL：64-128GB OS / 10 线程

10 线程高并发，推荐 **ZGC**（JDK 17+）。若只能用 JDK 8，用 G1。

#### 方案 A：ZGC（推荐，JDK 17+）

```bash
-Xms6g -Xmx6g
-XX:+UseZGC
-XX:+ZGenerational        # JDK 21+ 分代 ZGC，吞吐更高
-XX:ConcGCThreads=2
-XX:+AlwaysPreTouch
-XX:+ExitOnOutOfMemoryError
-XX:+PerfDisableSharedMem
-Xlog:gc*:file=/data/logs/druid/gc.log:time,uptime:filecount=10,filesize=100m
```

#### 方案 B：G1（兼容 JDK 8+）

```bash
-Xms6g -Xmx6g
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

**设计要点**：ZGC 暂停 <1ms 且与堆大小无关，配置极简（~10% 堆额外开销可接受）。G1 方案 Region=4MB（1536 regions），ParallelGCThreads=8。

---

## 五、关键参数说明

| 参数 | 推荐值 | 默认值 | 理由 |
|------|--------|--------|------|
| `MaxGCPauseMillis` | **50** | 200 | 暂停从 80ms 降到 20-40ms |
| `InitiatingHeapOccupancyPercent` | **35** | 45 | 提前并发标记，防 Full GC |
| `G1ReservePercent` | **15** | 10 | 增加 evacuation 缓冲，换确定性 |
| `G1MaxNewSizePercent` | 20-30 | 60 | 限制 Eden 上限，控制单次 GC 存活集 |
| `G1HeapRegionSize` | 堆/2048 | 自动 | 控制 region 数在 1024~2048 |
| `Xms=Xmx` | **固定堆** | — | 避免 resize 抖动（C 调用场景必须） |
| `AlwaysPreTouch` | **true** | false | 启动时锁页，避免运行时缺页中断 |
| `ExitOnOutOfMemoryError` | **true** | false | OOM 时退出进程，C 侧可感知 |
| `PerfDisableSharedMem` | **true** | false | 多次启停避免 `/tmp/hsperfdata_*` 泄漏 |

---

## 六、预期效果

| 指标 | 调优前 | S | M | L | XL(ZGC) |
|------|--------|---|---|---|---------|
| GC 暂停 P99 | ~80ms | <30ms | <50ms | <50ms | **<1ms** |
| Full GC 风险 | 低 | 极低 | 极低 | 极低 | **无** |
| OOM 风险 | 有(1.4GB>1GB) | 低 | 低 | 低 | 低 |
| 吞吐量影响 | 基准 | -3% | 持平 | 持平 | -5~10% |

---

## 七、验证方法

### 7.1 启动前检查

```bash
java -XX:+PrintFlagsFinal -version 2>&1 | grep -E \
  'MaxHeapSize|UseG1GC|MaxGCPauseMillis|InitiatingHeapOccupancyPercent'
```

### 7.2 运行时监控

```bash
# GC 实时状态（每 2 秒刷新）
jstat -gcutil <pid> 2s
# 关键列：YGC(次数) YGCT(耗时) FGC(必须=0)

# GC 日志
tail -f /data/logs/druid/gc.log
```

### 7.3 运行后分析

```bash
GC_LOG=/data/logs/druid/gc.log

# Full GC 次数（必须为 0）
grep -c 'Pause Full' $GC_LOG

# GC 暂停 P50 / P99 / Max
grep -oP '\d+\.\d+(?=ms)' $GC_LOG | sort -n | awk '
  { a[NR]=$1 }
  END {
    print "P50:", a[int(NR*0.5)], "ms"
    print "P99:", a[int(NR*0.99)], "ms"
    print "Max:", a[NR], "ms"
  }'

# 观察 Old Gen 峰值（决定是否需要调整 Xmx）
grep 'G1 Old Gen' $GC_LOG | tail -20
```

### 7.4 对比清单

调优前后收集：
1. 总处理 SQL 数、ExecSpeed / ElapsedSpeed
2. `jstat -gcutil` 的 YGC / FGC / GCT（GC 总耗时）
3. GC 暂停 P50 / P99 / Max
4. 堆 used / committed 峰值
5. Failure 率 / NonSupport 率

---

*适用版本：druid-ak-1.2.27，OpenJDK 8+/17+*
*数据来源：node62/107/116/135/160/203 共 6 节点监控 + 现场环境验证*
