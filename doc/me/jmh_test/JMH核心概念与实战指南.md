# JMH 核心概念与实战指南

## 一、JMH 简介

**JMH (Java Microbenchmark Harness)** 是 OpenJDK 提供的 Java 微基准测试框架，用于精确测量代码性能。

## 二、核心架构图

```
┌─────────────────────────────────────────────────────┐
│                  JMH 执行层级                         │
├─────────────────────────────────────────────────────┤
│  Fork (独立JVM进程)                                  │
│  └── Iteration (迭代轮次)                            │
│      ├── Warmup (预热，消除JIT影响)                   │
│      └── Measurement (测量，收集数据)                 │
│          └── Invocation (方法调用)                    │
│              └── Operation (操作单元)                 │
└─────────────────────────────────────────────────────┘
```

## 三、核心注解详解

### 3.1 @Benchmark - 标记测试方法

```java
@Benchmark
public void benchmarkMethod() {
    // 要测试的代码
}
```

**多个 @Benchmark 方法：**
- 默认全部执行
- 可通过 `.include("方法名正则")` 选择性执行

### 3.2 @Fork - 进程隔离

```java
@Fork(value = 2, jvmArgs = {"-Xmx1g", "-XX:+UseG1GC"})
```

| 参数 | 说明 | 示例 |
|------|------|------|
| `value` | JVM 进程数 | `2`（推荐 2-3） |
| `jvmArgs` | JVM 启动参数 | `-Xmx1g -XX:+UseG1GC` |

**为什么需要 Fork？**
```
Fork 1 → 独立JVM → 纯净环境 → 结果A
Fork 2 → 独立JVM → 纯净环境 → 结果B
                              ↓
                        取平均值，消除偏差
```

### 3.3 @BenchmarkMode - 测量模式

```java
@BenchmarkMode(Mode.Throughput)
```

| 模式 | 单位 | 说明 |
|------|------|------|
| `Throughput` | ops/s | 吞吐量（每秒操作数） |
| `AverageTime` | time/op | 平均耗时 |
| `SampleTime` | time/op | 采样耗时分布 |
| `SingleShotTime` | time/op | **单次批次耗时**（精确控制调用次数） |

### 3.4 @State - 状态管理

```java
@State(Scope.Benchmark)  // 所有线程共享
@State(Scope.Thread)     // 每线程独立
@State(Scope.Group)      // 线程组共享
```

### 3.5 @Param - 参数化测试

```java
@Param({"mysql", "postgresql"})
private String dbType;

@Param({"SELECT * FROM t", "INSERT INTO t"})
private String sql;
```

会自动生成测试矩阵：2种数据库 × 2种SQL = 4种组合

### 3.6 @Warmup / @Measurement - 迭代配置

```java
@Warmup(iterations = 3, batchSize = 100_000)
@Measurement(iterations = 5, batchSize = 1_000_000)
```

**两种执行模式：**

| 模式 | 配置方式 | 适用场景 |
|------|----------|----------|
| 时间模式 | `time=10, timeUnit=SECONDS` | 长时间运行，自动计算吞吐量 |
| 批次模式 | `batchSize=100_000` | **精确控制调用次数** |

## 四、配置优先级

```
命令行参数 (最高)
    ↓
OptionsBuilder API
    ↓
注解 (最低)
```

**示例：**
```java
@BenchmarkMode(Mode.Throughput)  // 最低优先级
@Threads(4)
public class Benchmark {
    public static void main(String[] args) {
        Options opt = new OptionsBuilder()
                .mode(Mode.SingleShotTime)  // ✅ 覆盖注解
                .threads(10)                // ✅ 覆盖注解
                .build();
    }
}
```

## 五、BatchSize 计算逻辑

**SingleShotTime 模式下：**
```
总调用次数 = threads × iterations × batchSize
```

**因此：**
```
batchSize = TOTAL_INVOCATIONS / (threads × iterations)
```

**示例（总调用 500,000 次）：**

| 线程数 | 迭代次数 | batchSize | 实际总调用 |
|--------|----------|-----------|-----------|
| 1 | 5 | 100,000 | 1×5×100,000 = 500,000 |
| 4 | 5 | 25,000 | 4×5×25,000 = 500,000 |
| 10 | 5 | 10,000 | 10×5×10,000 = 500,000 |

## 六、实战配置示例

### 6.1 精确调用次数测试

```java
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, batchSize = 100_000)
@Measurement(iterations = 5, batchSize = 1_000_000)
@Fork(value = 1, jvmArgs = {"-Xmx1g", "-XX:+UseG1GC"})
@Threads(1)
public class SqlParserBenchmark {
    
    private static final int TOTAL_INVOCATIONS = 500_000;
    
    @Benchmark
    public void benchmark() {
        // 测试代码
    }
    
    public static void main(String[] args) throws RunnerException {
        int threads = 4;
        int batchSize = TOTAL_INVOCATIONS / (threads * 5);
        
        Options opt = new OptionsBuilder()
                .include(SqlParserBenchmark.class.getSimpleName())
                .mode(Mode.SingleShotTime)
                .threads(threads)
                .forks(1)
                .jvmArgs("-Xmx1g")
                .measurementBatchSize(batchSize)
                .resultFormat(ResultFormatType.JSON)
                .result("jmh_result.json")
                .build();
        
        new Runner(opt).run();
    }
}
```

### 6.2 吞吐量测试

```java
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 5, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 10, timeUnit = TimeUnit.SECONDS)
@Fork(value = 2, jvmArgs = {"-Xmx2g", "-XX:+UseG1GC"})
@Threads(4)
public class ThroughputBenchmark {
    
    @Benchmark
    public void benchmark() {
        // 测试代码
    }
}
```

**输出示例：**
```
Benchmark                    Mode  Cnt    Score   Error  Units
ThroughputBenchmark.test    thrpt   10  4850.12 ± 12.34  ops/s
```

## 七、命令行参数支持

```java
public static void main(String[] args) throws RunnerException {
    int customThreads = -1;
    String customHeaps = null;
    
    // 手动解析参数
    for (int i = 0; i < args.length; i++) {
        switch (args[i]) {
            case "-t":
                customThreads = Integer.parseInt(args[++i]);
                break;
            case "-heap":
                customHeaps = args[++i];
                break;
            case "-wi":
                warmupIterations = Integer.parseInt(args[++i]);
                break;
            case "-i":
                measurementIterations = Integer.parseInt(args[++i]);
                break;
        }
    }
    
    // 应用参数
    int[] threads = (customThreads > 0) ? new int[]{customThreads} : DEFAULT_THREADS;
}
```

**运行命令：**
```bash
# 默认矩阵测试
java -cp "..." SqlParserBenchmark

# 单线程测试
java -cp "..." SqlParserBenchmark -t 1

# 自定义堆大小
java -cp "..." SqlParserBenchmark -heap 512m,2g

# 完全自定义
java -cp "..." SqlParserBenchmark -t 4 -heap 1g -wi 2 -i 3
```

## 八、常见问题

### Q1: 为什么需要 @Fork？

**A:** JVM 有 JIT 编译效应，第一次执行慢，后续快。Fork 确保每次测试在纯净 JVM 中运行。

### Q2: batchSize 和 time 有什么区别？

**A:** 
- `time`: 运行固定时间，JMH 自动决定调用次数
- `batchSize`: 精确控制调用次数

### Q3: OptionsBuilder 和注解冲突怎么办？

**A:** OptionsBuilder 优先级更高，会覆盖注解。

### Q4: 如何只运行特定的 @Benchmark 方法？

**A:** 使用正则过滤：
```java
.include("SqlParserBenchmark.*v2.*")
```

### Q5: Warmup 的结果会被记录吗？

**A:** 不会。Warmup 仅用于 JIT 预热，不计入最终结果。

## 九、最佳实践

1. **Fork 数**: 生产基准测试推荐 2-3
2. **预热时间**: 至少 3-5 轮，确保 JIT 充分编译
3. **测量模式**: 需要精确调用次数用 `SingleShotTime + batchSize`
4. **结果输出**: 始终保存 JSON 格式，便于后续分析
5. **JVM 参数**: 通过 `jvmArgs` 明确指定，避免环境差异

## 十、参考资源

- 官方文档: https://openjdk.org/projects/code-tools/jmh/
- 示例仓库: https://github.com/openjdk/jmh-samples
- 源码: https://github.com/openjdk/jmh
