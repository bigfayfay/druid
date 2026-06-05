# Druid性能测试框架重构 - 需求与设计规范文档

> 创建时间: 2026-06-05  
> 项目: druid-spring-boot-starter  
> 模块: perf (性能测试)

---

## 📋 一、需求清单

### 1.1 配置化注入需求
**原始需求**: "只会注入一个。请增加配置项目来实现"

**具体要求**:
- DataFetcherService有多个实现（SqlTemplateDataFetcher、AuditDataFetcher）
- 需要通过配置文件选择注入哪个实现
- 避免多个实现同时注册导致注入冲突

**实现方案**:
- 在PerfTestConfig中添加`dataFetcherType`配置项
- 使用Spring的`@ConditionalOnProperty`注解实现条件注入
- 默认值为`sql-template`

### 1.2 文件写入抽象需求
**原始需求**: "想要将 writer 抽象为 FilePostHandler 但文件又是每个线程一个；是否可以抽象；是否建议抽象"

**具体要求**:
- 将内嵌在PerfTestRunner中的文件写入逻辑抽象为独立的PostHandler
- 每个消费者线程需要独立的文件（避免写锁竞争）
- 需要与现有的DB PostHandler统一接口

**设计决策**: ✅ **强烈建议抽象**

### 1.3 性能统计需求
**原始需求**: "从全局的视角统计下 totalCalls, costs, elapsed;并计算对应的速度(单线程内+总体全局视角的速度)"

**具体要求**:
- 统计每个线程的: 调用次数、总耗时、成功/失败数、处理速度
- 统计全局的: 汇总调用次数、总耗时、成功/失败数、全局吞吐量
- 计算单线程速度: `calls * 1000.0 / elapsedMs` (rec/s)
- 计算全局速度: `totalCalls * 1000.0 / globalElapsedMs` (rec/s)
- 计算平均解析耗时

---

## 🏗️ 二、架构设计

### 2.1 条件注入设计模式

```java
// 配置类
@Data
@Component
public class PerfTestConfig {
    /**
     * 数据获取器类型：sql-template 或 audit
     */
    @Value("${perf.data-fetcher-type:sql-template}")
    private String dataFetcherType;
}

// 实现类1 - 默认启用
@Service
@ConditionalOnProperty(name = "perf.data-fetcher-type", 
                       havingValue = "sql-template", 
                       matchIfMissing = true)
public class SqlTemplateDataFetcher implements DataFetcherService { }

// 实现类2 - 显式启用
@Service
@ConditionalOnProperty(name = "perf.data-fetcher-type", 
                       havingValue = "audit")
public class AuditDataFetcher implements DataFetcherService { }
```

**设计优势**:
- ✅ 零代码侵入配置
- ✅ 类型安全
- ✅ 支持默认值
- ✅ 易于扩展新实现

### 2.2 后处理器抽象模式

#### 统一接口设计
```java
public interface PostHandler {
    void addRecord(SqlTemplateRes record);
    void flush();
}
```

#### 文件后处理器（线程级别实例化）
```java
public class FilePostHandler implements PostHandler {
    private final BufferedWriter writer;
    private final Path filePath;
    private final int threadIdx;
    
    // 每个线程创建独立实例，写入独立文件
    public FilePostHandler(String basePath, int threadIdx, boolean enabled) {
        this.threadIdx = threadIdx;
        this.filePath = Paths.get(basePath,
            "failed_sql_" + timestamp + "_t" + threadIdx + ".info");
        this.writer = enabled ? new BufferedWriter(...) : null;
    }
}
```

#### DB后处理器（全局单例）
```java
@Service
public class PostHandler implements com.ankki.perf.service.PostHandler {
    private final List<SqlTemplateRes> buffer;
    private final ReentrantLock lock = new ReentrantLock();
    
    // 使用缓冲+批量刷新+锁保证线程安全
    public void addRecord(SqlTemplateRes record) {
        lock.lock();
        try {
            buffer.add(record);
            if (buffer.size() >= flushThreshold) {
                flush();
            }
        } finally {
            lock.unlock();
        }
    }
}
```

#### 在PerfTestRunner中使用
```java
for (int i = 0; i < threadCount; i++) {
    final int threadIdx = i;
    
    // 为每个线程创建独立的后处理器组合
    List<PostHandler> threadPostHandlers = new ArrayList<>();
    
    // 文件后处理器（每个线程一个实例）
    if (writeFile) {
        FilePostHandler filePostHandler = new FilePostHandler(path, threadIdx, true);
        threadPostHandlers.add(filePostHandler);
    }
    
    // DB后处理器（所有线程共享）
    if (writeDb) {
        threadPostHandlers.addAll(postHandlers);
    }
    
    // 消费者线程使用自己的后处理器列表
    consumers.submit(() -> {
        for (PostHandler handler : threadPostHandlers) {
            handler.addRecord(res);
        }
    });
}
```

### 2.3 双视角性能统计设计

#### 线程级别统计（ThreadStats）
```java
private static class ThreadStats {
    private final int threadId;
    private final LongAdder totalCalls = new LongAdder();
    private final LongAdder successCount = new LongAdder();
    private final LongAdder failureCount = new LongAdder();
    private final LongAdder totalParseNanos = new LongAdder();
    private final long startTime;
    private volatile long endTime;
    
    public void recordParse(long elapsedNanos, boolean success) {
        totalCalls.increment();
        totalParseNanos.add(elapsedNanos);
        if (success) {
            successCount.increment();
        } else {
            failureCount.increment();
        }
    }
    
    // 计算速度: records per second
    public double getSpeed() {
        long elapsedMs = getElapsedMillis();
        return elapsedMs > 0 ? (getTotalCalls() * 1000.0 / elapsedMs) : 0;
    }
}
```

#### 全局汇总统计
```java
private void printGlobalStats(List<ThreadStats> threadStatsList, PerfStats globalStats) {
    long totalCalls = 0;
    long totalParseNanos = 0;
    
    // 汇总所有线程数据
    for (ThreadStats ts : threadStatsList) {
        totalCalls += ts.getTotalCalls();
        totalParseNanos += ts.getTotalParseNanos();
        // ...
    }
    
    // 计算全局指标
    long globalElapsedMs = globalStats.getEndTimeMillis() - globalStats.getStartTimeMillis();
    double globalSpeed = totalCalls * 1000.0 / globalElapsedMs;
    double avgParseMs = totalParseNanos / 1_000_000.0 / totalCalls;
}
```

---

## 📐 三、设计规范与最佳实践

### 3.1 多线程环境下的设计原则

#### 原则1: 避免共享可变状态
- ✅ **文件写入**: 每个线程独立文件，无锁竞争
- ✅ **线程统计**: 每个线程独立ThreadStats实例
- ⚠️ **DB写入**: 共享缓冲区，必须使用锁保护

#### 原则2: 使用无锁数据结构
```java
// ✅ 推荐: LongAdder（高并发场景优于AtomicLong）
private final LongAdder totalCalls = new LongAdder();

// ❌ 避免: synchronized或ReentrantLock保护计数器
```

#### 原则3: 职责分离
- **PerfTestRunner**: 专注于流程控制（生产者-消费者）
- **FilePostHandler**: 专注于文件写入
- **PostHandler(DB)**: 专注于数据库写入
- **ThreadStats**: 专注于性能统计

### 3.2 配置驱动设计

#### 命名规范
```yaml
perf:
  # 布尔值: enabled/disabled, true/false
  enabled: true
  write-failed-file: true
  write-result-db: true
  
  # 枚举值: 使用连字符分隔
  data-fetcher-type: sql-template  # 或 audit
  db-type: oracle
  
  # 数值: 使用连字符分隔
  batch-size: 1000
  thread-count: 4
  queue-capacity: 20
```

#### 默认值策略
```java
// ✅ 提供合理的默认值
@Value("${perf.batch-size:1000}")
private int batchSize;

@Value("${perf.data-fetcher-type:sql-template}")
private String dataFetcherType;

@Value("${perf.enabled:true}")
private boolean enabled;
```

### 3.3 性能统计规范

#### 指标分类
1. **计数指标**: totalCalls, successCount, failureCount
2. **时间指标**: elapsedMillis, totalParseNanos, costMs
3. **速率指标**: speed (rec/s), tps, avgParseMs
4. **分布指标**: histogram (P50, P95, P99)

#### 计算公式
```java
// 速度 (records per second)
speed = totalCalls * 1000.0 / elapsedMs

// 平均耗时 (milliseconds)
avgMs = totalParseNanos / 1_000_000.0 / totalCalls

// 成功率
successRate = successCount * 100.0 / totalCalls

// 吞吐量 (全局)
tps = totalCalls * 1000.0 / globalWallTime
```

#### 输出格式规范
```
========== 全局性能统计汇总 ==========
线程            |       调用次数 |      总耗时(ms) |          成功数 |        失败数 |     速度(rec/s)
-------------------------------------------------------------------------------------
Thread-0        |         2500 |      5847.123 |         2450 |         50 |       166.67
Thread-1        |         2480 |      5923.456 |         2430 |         50 |       165.33
-------------------------------------------------------------------------------------
全局汇总          |        10000 |     23339.713 |         9800 |        200 |       428.57
全局平均解析耗时: 2.334 ms
全局Wall Time: 6000 ms
全局吞吐量: 1666.67 rec/s
========================================
```

### 3.4 Java 8兼容性规范

#### ❌ 不支持的API (Java 11+)
```java
// String.repeat() - Java 11+
String separator = "-".repeat(85);
```

#### ✅ 兼容的替代方案
```java
// Java 8兼容
private String createSeparatorLine() {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < 85; i++) {
        sb.append('-');
    }
    return sb.toString();
}
```

### 3.5 日志记录规范

#### 日志级别使用
- **ERROR**: 系统错误、异常（`log.error()`）
- **WARN**: 警告信息（`log.warn()`）
- **INFO**: 关键流程节点、进度、统计结果（`log.info()`）
- **DEBUG**: 调试信息（`log.debug()`）

#### 日志格式
```java
// ✅ 使用占位符
log.info("Producer progress: batch={}, fetched={}, lastId={}, queueSize={}",
        batchNumber, produced, lastId, queue.size());

// ✅ 异常日志包含完整堆栈
log.error("Failed to create FilePostHandler for thread-{}", threadIdx, e);

// ❌ 避免字符串拼接
log.info("Producer progress: batch=" + batchNumber + "...");
```

---

## 🔍 四、关键洞察与经验总结

### 4.1 何时应该抽象？

**判断标准**:
1. ✅ **职责单一**: 代码承担了不属于当前类的职责
2. ✅ **可复用**: 逻辑可能在其他地方使用
3. ✅ **可测试**: 抽象后更容易单元测试
4. ✅ **可扩展**: 未来可能有多种实现
5. ✅ **降低复杂度**: 抽象后主流程更清晰

**本案例**:
- 文件写入逻辑内嵌在PerfTestRunner中 → 职责不清
- 未来可能需要其他输出方式（日志、消息队列） → 可扩展性
- 多线程文件写入是独立关注点 → 职责单一
- **结论**: ✅ 强烈建议抽象

### 4.2 多线程文件写入的设计权衡

| 方案 | 优点 | 缺点 | 适用场景 |
|------|------|------|----------|
| 单文件+锁 | 文件集中管理 | 锁竞争严重，性能差 | 低并发 |
| 单文件+无锁 | 性能最好 | 写入混乱，数据损坏 | ❌ 不推荐 |
| **多文件+无锁** | **无竞争，高性能** | **文件分散** | **✅ 高并发推荐** |
| 内存缓冲+定时刷 | 减少IO | 可能丢失数据 | 可接受丢失场景 |

**本案例选择**: 多文件+无锁（每个线程一个文件）

### 4.3 性能统计的两个视角

#### 视角1: 线程级别统计
- **目的**: 发现线程间负载不均衡
- **指标**: 每个线程的调用次数、耗时、速度
- **洞察**: 如果某个线程明显慢于其他线程，可能存在:
  - 数据倾斜（某些SQL特别复杂）
  - 线程调度问题
  - GC暂停影响

#### 视角2: 全局汇总统计
- **目的**: 评估整体系统性能
- **指标**: 总吞吐量、全局平均耗时、Wall Time
- **洞察**: 
  - 全局速度 vs 单线程速度之和 → 评估并行效率
  - Wall Time vs 各线程耗时之和 → 评估调度开销

### 4.4 条件注入 vs 工厂模式

| 方案 | 优点 | 缺点 | 适用场景 |
|------|------|------|----------|
| **@ConditionalOnProperty** | **配置驱动，零代码侵入** | **需要Spring环境** | **✅ Spring Boot应用** |
| 工厂模式 | 灵活，可运行时切换 | 需要编写工厂类 | 复杂实例化逻辑 |
| 策略模式 | 运行时动态切换 | 需要管理策略实例 | 频繁切换场景 |

**本案例选择**: @ConditionalOnProperty（配置驱动，启动时确定）

---

## 📝 五、配置文件示例

### application.yml
```yaml
perf:
  # 基础配置
  enabled: true
  batch-size: 1000
  thread-count: 4
  queue-capacity: 20
  
  # 数据源配置
  db-type: oracle
  start-id: 0
  max-records: 0  # 0表示不限制
  
  # 数据获取器选择
  data-fetcher-type: sql-template  # 或 audit
  
  # 输出配置
  write-failed-file: true
  write-result-db: true
  
  # 预热配置
  warmup-batches: 2
```

### 切换数据获取器
```yaml
# 使用SQL模板数据源
perf:
  data-fetcher-type: sql-template

# 使用审计数据源
perf:
  data-fetcher-type: audit
```

---

## 🎯 六、重构任务流程总结

### 阶段1: 配置化注入
1. 在PerfTestConfig添加`dataFetcherType`配置项
2. 为SqlTemplateDataFetcher添加`@ConditionalOnProperty`（默认）
3. 为AuditDataFetcher添加`@ConditionalOnProperty`（显式）
4. 验证只有一个实现被注入

### 阶段2: 后处理器抽象
1. 定义统一的PostHandler接口
2. 创建FilePostHandler类（每个线程独立实例）
3. 重构PerfTestRunner，移除内嵌的文件写入逻辑
4. 为每个消费者线程创建独立的PostHandler列表
5. 验证文件写入和DB写入都正常工作

### 阶段3: 性能统计增强
1. 创建ThreadStats内部类（线程级别统计）
2. 为每个消费者线程创建独立的ThreadStats实例
3. 在解析循环中记录性能数据
4. 实现printGlobalStats方法（全局汇总）
5. 为PerfStats添加getStartTimeMillis/getEndTimeMillis方法
6. 验证统计输出格式正确

---

## 💡 七、未来扩展建议

### 7.1 可能的扩展点
1. **更多的PostHandler实现**:
   - LogPostHandler: 写入日志系统
   - MqPostHandler: 发送到消息队列
   - HttpPostHandler: 调用HTTP API

2. **更丰富的性能指标**:
   - CPU使用率
   - 内存使用率
   - GC次数和耗时
   - 线程池活跃度

3. **实时监控**:
   - 集成Micrometer/Prometheus
   - Grafana Dashboard
   - 实时告警

4. **动态配置**:
   - 支持运行时修改线程数
   - 支持运行时切换数据源
   - 支持动态开启/关闭统计

### 7.2 代码质量提升
1. 添加单元测试（特别是ThreadStats和FilePostHandler）
2. 添加集成测试（验证完整流程）
3. 添加性能基准测试（JMH）
4. 代码覆盖率目标: >80%

---

## 📚 八、参考资源

### Spring Boot条件注解
- `@ConditionalOnProperty`: 基于配置属性条件化
- `@ConditionalOnClass`: 基于类路径条件化
- `@ConditionalOnBean`: 基于Bean存在条件化
- `@ConditionalOnMissingBean`: 基于Bean不存在条件化

### 并发编程最佳实践
- `LongAdder` vs `AtomicLong`: 高并发场景LongAdder性能更好
- `ConcurrentLinkedQueue`: 无锁队列
- `CompletableFuture`: 异步编程
- `StampedLock`: 读写锁的更高效替代

### 性能测试工具
- JMH (Java Microbenchmark Harness)
- JMeter
- Gatling
- Wrk (HTTP性能测试)

---

## ✅ 检查清单

在提交代码前，请确认:
- [ ] 所有编译错误已解决
- [ ] 配置文件示例已更新
- [ ] 日志输出格式正确
- [ ] 线程安全的代码已验证
- [ ] Java 8兼容性已确认
- [ ] 默认值设置合理
- [ ] 异常处理完整
- [ ] 资源正确关闭（文件、连接池等）
- [ ] 统计指标计算准确
- [ ] 代码注释清晰

---

> **文档维护**: 本文档应随代码演进同步更新  
> **最后更新**: 2026-06-05  
> **维护者**: fay
