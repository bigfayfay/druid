# AkSqlTemplateMonitor 项目约束与规范

## 1. 代码编写规范

### 1.1 时间单位统一为毫秒
- 对外暴露的时间字段（日志、API、配置）**必须统一使用毫秒（ms）**并以 `ms` 结尾
- 内部高精度计时可使用纳秒，但记录到 LongAdder 前须转为毫秒（`TimeUnit.NANOSECONDS.toMillis()`），确保统计一致性

### 1.2 状态统计输出格式
- 格式：`状态名=数量(百分比%)`
- 示例：`Failure=2322(1.01%)`
- 百分比保留两位小数

### 1.3 失败SQL模板记录格式
- 解析失败的SQL模板记录需以 `id,template` 格式写入文件
- 每行一条记录

## 2. 编程实践规范

### 2.1 高并发计数器选型
- 在高并发计数场景下，**优先使用 `LongAdder` 或 `LongAccumulator`** 替代 `AtomicLong`，以提升性能

### 2.2 监控使用CPU时间排除等待时间
- 性能监控应使用CPU时间（`ThreadMXBean.getCurrentThreadCpuTime`）而非墙上时钟时间（`System.nanoTime`）
- 目的：排除 GC 暂停、线程调度等待、锁竞争等非计算耗时
- 适用场景：纯计算密集型任务（如SQL解析）的性能度量

### 2.3 性能速度计算
- 速度（ops/s）的计算必须基于 **全局总次数 ÷ 全局总耗时**
- **禁止** 对各状态速度值直接求和
- 正确公式：`总调用次数 × 1000 ÷ 总执行耗时（ms）`

### 2.4 监控指标时间维度与速度计算
- 区分两个关键时间维度：
  - `elapsedTime`：实际运行时长（从 start 到当前）
  - `totalCostMs`：所有操作累计耗时
- 对应速度指标：
  - `costMsSpeed = total / elapsedTime`（真实吞吐量）
  - `totalCostSpeed = total / totalCostMs`（处理吞吐量）
- 日志输出**必须**包含 `elapsedTime` 字段

### 2.5 日志报告工程化设计（直观紧凑版）
- 报表总行数控制在约 **8行**（依状态数量浮动）
- 多字段强制合并为单行，使用 `|` 分隔（如 `Calls=5000 | ExecSpeed=120.50 ops/s`）
- 提供 `row(...)` 和 `labeled(...)` 等链式API支持字段聚合
- 增删指标仅需修改参数列表
- 格式统一由 `ReportBuilder` 内有限方法（`row`/`labeled`/`end`）控制，避免散落格式化逻辑

### 2.6 直方图微秒级6桶分布策略
- 直方图统计单位由毫秒调整为**微秒（μs）**
- 采用 10 倍等比递进的 6 桶分布：
  - 桶0：`<10μs`（<10,000 ns）
  - 桶1：`10-100μs`（10K–100K ns）
  - 桶2：`100μs-1ms`（100K–1M ns）
  - 桶3：`1-10ms`（1M–10M ns）
  - 桶4：`10-100ms`（10M–100M ns）
  - 桶5：`>100ms`（>100M ns）

### 2.7 监控标题行显示累计运行时长
- 通过类加载时记录 `CLASS_LOAD_TIME_MS` 作为基准时间
- 使用 `fmtDuration` 方法格式化输出（如 `1h23m45s`）
- 避免依赖易变的 `startTimeMs` 实例字段

## 3. 数据结构设计约束

### 3.1 成员变量精简
- 避免大量散落的 `AtomicLong` 变量
- 使用内部数据结构封装重复模式：
  - `TimeStats`：封装 total/max/min
  - `StatusMetrics`：封装 count/totalTime/maxTime
- 使用 `EnumMap<AkSqlParserStatusEnum, StatusMetrics>` 管理按状态分类统计
- 直方图用数组替代独立变量

### 3.2 按状态分类统计耗时
- 每个 `AkSqlParserStatusEnum` 状态（Success/Failure/NonSupport/Exception）各自独立统计：
  - 调用次数
  - 累计耗时
  - 最大耗时
  - 平均耗时

## 4. 常见陷阱

### 4.1 Java FileWriter 需手动创建父目录
- `FileWriter` 可以自动创建文件，但**无法自动创建父目录**
- 若父目录不存在，会抛出 `FileNotFoundException`（被 catch 后导致写入静默失败）
- **必须**在 `new FileWriter` 前显式调用 `File.mkdirs()` 确保目录存在

### 4.2 Logback 不支持 FATAL 级别
- 设置 `level="FATAL"` 属于无效配置，会被忽略
- 如需完全关闭某包日志（包括ERROR），**必须使用 `level="OFF"`**

### 4.3 Shell 脚本兼容 POSIX
- POSIX shell（如 dash）不支持 `[[` 条件判断语法
- **必须使用 `[`（单中括号）** 以保证脚本在 `sh` 下可执行
- 若需保留 `[[`，应显式用 `bash script.sh` 运行

### 4.4 VmOptions.getBoolean 误用
- `Boolean.getBoolean(key)` 读取的是系统属性而非布尔值解析
- 注意与 `Boolean.parseBoolean()` 的区别

### 4.5 Maven annotationProcessorPaths 覆盖
- `annotationProcessorPaths` 显式配置会覆盖自动发现机制
- 需确保 Lombok 等注解处理器被显式包含

### 4.6 Lombok 与 JDK 21+ 兼容性
- Lombok 最低要求 **1.18.30** 以适配 JDK 21+ 内部 API 变更

## 5. 环境配置约束

### 5.1 Druid 包日志级别
- 对 `com.alibaba.druid` 包的日志输出需设置为 **OFF** 级别
- 目的：彻底屏蔽包括 ERROR 在内的所有日志

### 5.2 监控同时记录墙上时钟时间和CPU时间
- 墙上时钟时间：总耗时（含等待）
- CPU时间：纯计算耗时
- 等待时间 = 墙上时钟时间 - CPU时间
- 三者都需分别统计 avg/max/min
