# Druid 项目约束与规范汇总

---

## 一、代码编写规范

### 1.1 时间单位统一为毫秒
对外暴露的时间字段（如日志、API、配置）必须统一使用毫秒（ms）并以 `ms` 结尾；内部高精度计时可使用纳秒，但记录到 LongAdder 前须转为毫秒（`TimeUnit.NANOSECONDS.toMillis()`），确保统计一致性。

### 1.2 状态统计输出格式
状态统计输出格式为：`状态名=数量(百分比%)`，例如 `Failure=2322(1.01%)`，百分比保留两位小数。

### 1.3 失败SQL模板记录格式
解析失败的 SQL 模板记录需以 `id,template` 格式写入文件，每行一条记录。

---

## 二、编程实践规范

### 2.1 性能速度计算
性能指标中速度（ops/s）的计算必须基于全局总次数除以全局总耗时，禁止对各状态速度值直接求和；正确公式为：`总调用次数 × 1000 ÷ 总执行耗时（ms）`。

### 2.2 日志报告工程化设计（直观紧凑版）
- 报表总行数控制在约 8 行（依状态数量浮动）
- 多字段强制合并为单行，使用 `|` 分隔（如 `Calls=5000 | ExecSpeed=120.50 ops/s`）
- 提供 `row(...)` 和 `labeled(...)` 等链式 API 支持字段聚合，增删指标仅需修改参数列表
- 格式统一由 `ReportBuilder` 内有限方法（`row`/`labeled`/`end`）控制，避免散落格式化逻辑

### 2.3 监控指标时间维度与速度计算
需区分两个关键时间维度：
- `elapsedTime`：实际运行时长（从 start 到当前）
- `totalCostMs`：所有操作累计耗时

对应速度指标：
- `costMsSpeed = total / elapsedTime`（真实吞吐量）
- `totalCostSpeed = total / totalCostMs`（处理吞吐量）

日志输出必须包含 `elapsedTime` 字段。

### 2.4 高并发计数器选型
在高并发计数场景下，优先使用 `LongAdder` 或 `LongAccumulator` 替代 `AtomicLong`，以提升性能。

### 2.5 监控使用 CPU 时间排除等待时间
性能监控应使用 CPU 时间（`ThreadMXBean.getCurrentThreadCpuTime`）而非墙上时钟时间（`System.nanoTime`），以排除 GC 暂停、线程调度等待、锁竞争等非计算耗时，确保纯计算密集型任务（如 SQL 解析）的性能度量准确。

---

## 三、架构设计约束

### 3.1 监控逻辑独立为单独类
SQL 模板生成方法的监控逻辑独立为单独的监控类 `AkSqlTemplateMonitor`，实现监控与业务逻辑的职责分离。监控类提供调用统计、执行时间分析、错误记录、直方图统计和定时报告等功能，可被其他模块复用。

### 3.2 直方图微秒级 6 桶分布策略
直方图统计单位由毫秒调整为微秒（μs），采用 10 倍等比递进的 6 桶分布：
| 桶 | 范围 | 标签 |
|----|------|------|
| 0 | <10,000 ns | `<10μs` |
| 1 | 10K–100K ns | `10-100μs` |
| 2 | 100K–1M ns | `100μs-1ms` |
| 3 | 1M–10M ns | `1-10ms` |
| 4 | 10M–100M ns | `10-100ms` |
| 5 | >100M ns | `>100ms` |

### 3.3 状态分类统计
监控系统支持按 `AkSqlParserStatusEnum` 枚举值进行分类统计，包含 Success、Failure、NonSupport、Exception 四种状态的调用次数及占比。

### 3.4 OS 系统级监控指标
监控能力扩展包含以下操作系统级指标：
- CPU 核心总数（cores）
- OS 物理内存总量与空闲量（total/free）
- Swap 交换空间总量与空闲量（total/free）

### 3.5 SqlTemplateResService 核心功能逻辑
- 对 id 字段实现"不存在则 INSERT，存在则 UPDATE"（即 UPSERT）语义
- 基于 `getSqlTemplate_v2` 执行结果构建记录，累积一定数量（默认 500 条）后触发批量写入
- 支持多线程安全添加与最终 flush 保障数据完整性

---

## 四、环境配置约束

### 4.1 日志级别
对 `com.alibaba.druid` 包的日志输出需设置为 `OFF` 级别，以彻底屏蔽包括 ERROR 在内的所有日志。

### 4.2 日志目录
日志输出目录默认为 `/data/logs/druid-perf`，可通过 JVM 参数 `-DLOG_DIR=xxx` 在启动时覆盖。

### 4.3 JVM 与环境路径配置
- 默认使用 ZGC 垃圾收集器
- 推荐 JVM 参数：`-Xms1g -Xmx4g -XX:+UseZGC`
- 监控日志目录默认为 `/data/logs/druid_monitor`，可通过 `--monitor-dir` 覆盖

### 4.4 run-perf.sh 日志输出配置
支持 `--log <file>` 参数指定日志输出路径；前台模式通过 tee 同步输出到终端和日志文件，后台模式直接重定向至日志文件，并自动创建日志父目录。

### 4.5 Spring Boot 主类
项目 Spring Boot 主启动类为 `com.ankki.perf.DruidPerfApplication`

### 4.6 DB_HOST_NAME 启动参数
启动脚本支持 `DB_HOST_NAME` 数据库主机配置，默认值为 `172.19.4.41`，可通过 `--db-host` 参数在命令行中覆盖。

### 4.7 断点续跑配置
支持 `perf.start-id` 配置项，默认值为 0，用于指定数据拉取的起始 ID，可通过 JVM 参数 `-Dperf.start-id=12345` 设置。

### 4.8 日志依赖配置
在 druid-spring-boot-starter 模块的 pom.xml 中引入 `spring-boot-starter-logging` 依赖，提供 Logback 日志实现及 SLF4J 桥接器。

---

## 五、构建配置约束

### 5.1 Maven 构建
- **构建工具**: Apache Maven
- **核心流程**: `mvn install`（含编译、测试、打包、安装到本地仓库）
- **关键插件**:
  - `maven-compiler-plugin`: 显式分离 java-compile/testCompile 执行阶段
  - `maven-checkstyle-plugin`: 启用 druid-checks.xml 校验，failOnViolation=true
  - `jacoco-maven-plugin`: test 阶段生成覆盖率报告
  - `central-publishing-maven-plugin`: 发布至 Maven Central

### 5.2 技术栈
- **开发语言**: Java 8 (JDK 1.8)
- **框架兼容**: Spring 4.3.20/5.3.27/6.0.8, Spring Boot 2.7.9/3.0.6
- **质量工具**: Checkstyle、JaCoCo、JMH

---

## 六、常见问题与经验

### 6.1 VmOptions.getBoolean 误用
`VmOptions.getBoolean()` 方法内部错误使用了 `Boolean.getBoolean(String)`（该方法读取系统属性名等于参数值的布尔属性）；正确做法是使用 `Boolean.parseBoolean(getValue(key))`。

### 6.2 Java FileWriter 需手动创建父目录
Java 中 `FileWriter` 可以自动创建文件，但**无法自动创建父目录**；若指定路径的父目录不存在，会抛出 `FileNotFoundException`。必须在 `new FileWriter` 前显式调用 `File.mkdirs()` 确保目录存在。

### 6.3 Lombok 与 JDK 21+ 兼容性
Lombok 1.18.20 及更早版本不兼容 JDK 21+（因 JDK 21 重构 javac 内部类 JCTree$JCImport，移除了 qualid 字段）；必须升级至 Lombok 1.18.30 或更高版本。

### 6.4 Maven annotationProcessorPaths 覆盖自动发现
当 `maven-compiler-plugin` 显式配置 `annotationProcessorPaths` 时，Maven 将仅使用该列表中的注解处理器，忽略 classpath 中自动发现的处理器（如 Lombok）。必须将 Lombok 明确添加到 `annotationProcessorPaths` 中。

### 6.5 Shell 脚本兼容 POSIX
POSIX shell（如 dash）不支持 `[[` 条件判断语法，必须使用 `[`（单中括号）以保证脚本在 `sh` 下可执行。

### 6.6 Logback 不支持 FATAL 级别
Logback 不支持 FATAL 日志级别，设置 `level="FATAL"` 属于无效配置；如需完全关闭某包日志（包括 ERROR），必须使用 `level="OFF"`。

### 6.7 Spring Boot 项目必须配置 repackage goal
Spring Boot 项目必须配置 `spring-boot-maven-plugin` 并启用 `repackage` goal，否则 `mvn package` 生成的是普通 JAR（约几十 KB），无法通过 `java -jar` 启动；只有经 repackage 处理后才会生成包含所有依赖的可执行 fat JAR。
