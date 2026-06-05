# Druid性能测试框架重构 - 会话记录

## 📅 会话信息
- **日期**: 2026-06-05
- **项目**: druid-spring-boot-starter (perf模块)
- **参与者**: 用户(fay) + AI助手
- **会话类型**: 代码重构与架构设计

---

## 💬 对话流程

### 第一轮：配置化注入需求
**用户提问**: 
> "只会注入一个。请增加配置项目来实现"

**上下文**: 
- 选中的代码: `PerfTestRunner.java#L41-L42` 注入DataFetcherService
- 问题: 存在多个DataFetcherService实现(SqlTemplateDataFetcher, AuditDataFetcher)，Spring会注入冲突

**我的解决方案**:
1. 在PerfTestConfig添加`dataFetcherType`配置项
2. 使用`@ConditionalOnProperty`注解实现条件注入
3. SqlTemplateDataFetcher设为默认(matchIfMissing = true)
4. AuditDataFetcher需要显式配置

**修改的文件**:
- PerfTestConfig.java - 添加dataFetcherType字段
- SqlTemplateDataFetcher.java - 添加@ConditionalOnProperty
- AuditDataFetcher.java - 添加@ConditionalOnProperty

---

### 第二轮：文件写入抽象设计咨询
**用户提问**: 
> "想要将 writer 抽象为 FilePostHandler 但文件又是每个线程一个；是否可以抽象；是否建议抽象"

**上下文**:
- 选中的代码: `PerfTestRunner.java#L138-L140` BufferedWriter创建
- 核心矛盾: 每个线程需要独立文件 vs 抽象为统一接口

**我的分析与建议**: 
强烈建议抽象，理由：
1. 职责分离 - PerfTestRunner不应关心文件写入细节
2. 统一接口 - 与DB PostHandler一致
3. 可扩展性 - 未来可能增加其他后处理器
4. 可测试性 - 独立类更容易测试
5. 符合开闭原则

**设计方案**:
- FilePostHandler实现PostHandler接口
- 每个消费者线程创建独立实例（构造函数传入threadIdx）
- 实例化管理：在循环中为每个线程创建自己的PostHandler列表

**修改的文件**:
- 新建 FilePostHandler.java
- 重构 PerfTestRunner.java - 移除内嵌的文件写入逻辑

---

### 第三轮：性能统计增强
**用户提问**: 
> "从全局的视角统计下 totalCalls, costs, elapsed;并计算对应的速度(单线程内+总体全局视角的速度)"

**上下文**:
- 选中的代码: SQL解析位置 `PerfTestRunner.java#L162-L163`
- 需求: 双视角统计（线程级 + 全局级）

**我的实现方案**:
1. 创建ThreadStats内部类 - 每个线程独立统计
   - totalCalls, successCount, failureCount
   - totalParseNanos, startTime, endTime
   - 速度计算: calls * 1000.0 / elapsedMs

2. 全局汇总统计 - printGlobalStats方法
   - 汇总所有线程数据
   - 计算全局吞吐量和平均耗时
   - 表格化输出

3. PerfStats增强
   - 添加getStartTimeMillis()方法
   - 添加getEndTimeMillis()方法

**修改的文件**:
- PerfTestRunner.java - 添加ThreadStats类和printGlobalStats方法
- PerfStats.java - 添加getter方法

**遇到的技术障碍**:
- Java 8不支持String.repeat()方法
- 解决方案: 使用StringBuilder循环append替代

---

## 🎯 关键技术决策

### 决策1: 条件注入 vs 其他方案
**选择**: @ConditionalOnProperty
**原因**: 
- 配置驱动，零代码侵入
- Spring Boot标准做法
- 类型安全
- 支持默认值

**备选方案及放弃原因**:
- 工厂模式: 需要额外代码，过度设计
- 策略模式: 本场景不需要运行时切换

### 决策2: 文件写入抽象模式
**选择**: 每个线程独立实例 + 统一接口
**原因**:
- 避免锁竞争（关键！）
- 保持接口一致性
- 易于扩展

**备选方案及放弃原因**:
- 单文件+锁: 性能差，不适合高并发
- 单文件+无锁: 数据混乱，不可接受

### 决策3: 性能统计数据结构
**选择**: LongAdder而非AtomicLong
**原因**:
- LongAdder在高并发下性能更好
- 分段累加减少CAS冲突
- 适合"只增不减"的计数场景

---

## 🔧 代码变更总结

### 新建文件
1. FilePostHandler.java - 文件后处理器
2. perf-test-refactoring-spec.md - 设计规范文档

### 修改文件
1. PerfTestConfig.java - 添加dataFetcherType配置
2. SqlTemplateDataFetcher.java - 添加条件注解
3. AuditDataFetcher.java - 添加条件注解
4. PerfTestRunner.java - 三大重构
   - 配置化注入
   - 后处理器抽象
   - 性能统计增强
5. PerfStats.java - 添加时间getter方法

### 代码行数变化
- 新增: ~400行
- 修改: ~100行
- 删除: ~50行（内嵌逻辑）

---

## 💡 设计洞察与最佳实践

### 洞察1: 何时应该抽象？
判断标准：
- ✅ 职责单一原则被违反
- ✅ 代码可复用
- ✅ 需要提高可测试性
- ✅ 未来需要扩展
- ✅ 抽象后主流程更清晰

### 洞察2: 多线程文件写入的权衡
核心原则：**用空间换时间**
- 多文件 = 空间增加
- 无锁竞争 = 时间大幅减少
- 适合场景：高并发、性能敏感

### 洞察3: 性能统计的双视角价值
- **线程级统计**: 发现负载不均衡、数据倾斜
- **全局统计**: 评估整体性能、并行效率
- **对比分析**: 全局速度 vs 单线程速度之和 = 并行效率指标

### 最佳实践1: 配置驱动设计
```yaml
# 好的配置设计
perf:
  data-fetcher-type: sql-template  # 枚举值，有默认值
  batch-size: 1000                  # 数值，有默认值
  enabled: true                     # 布尔值，有默认值
```

### 最佳实践2: Java 8兼容性
```java
// ❌ Java 11+
String separator = "-".repeat(85);

// ✅ Java 8兼容
StringBuilder sb = new StringBuilder();
for (int i = 0; i < 85; i++) sb.append('-');
```

### 最佳实践3: 线程安全计数
```java
// ✅ 高并发推荐
private final LongAdder counter = new LongAdder();

// ⚠️ 低并发可用
private final AtomicLong counter = new AtomicLong();

// ❌ 避免
private long counter; // + synchronized
```

---

## 📊 输出示例

### 线程级统计输出
```
[Thread-0] 性能统计:
  调用次数: 2500
  成功/失败: 2450 / 50
  总耗时: 15000 ms
  平均耗时: 2.345 ms
  处理速度: 166.67 rec/s
```

### 全局统计输出
```
========== 全局性能统计汇总 ==========
线程            |       调用次数 |      总耗时(ms) |          成功数 |        失败数 |     速度(rec/s)
-------------------------------------------------------------------------------------
Thread-0        |         2500 |      5847.123 |         2450 |         50 |       166.67
Thread-1        |         2480 |      5923.456 |         2430 |         50 |       165.33
Thread-2        |         2520 |      5756.789 |         2470 |         50 |       168.00
Thread-3        |         2500 |      5812.345 |         2450 |         50 |       166.67
-------------------------------------------------------------------------------------
全局汇总          |        10000 |     23339.713 |         9800 |        200 |       428.57
全局平均解析耗时: 2.334 ms
全局Wall Time: 6000 ms
全局吞吐量: 1666.67 rec/s
========================================
```

---

## ⚠️ 遇到的问题与解决

### 问题1: IDE缓存导致误报编译错误
**现象**: get_problems显示Cannot resolve method 'createSeparatorLine'
**原因**: IDE缓存未刷新
**解决**: 方法实际已存在，重新构建或重启IDE即可

### 问题2: String.repeat()在Java 8不可用
**现象**: "Usage of API documented as @since 11+"
**解决**: 用StringBuilder循环替代

### 问题3: search_replace工具匹配失败
**现象**: Failed to match original_text
**原因**: 文本中有不可见字符或格式差异
**解决**: 改用edit_file工具，或重新read_file获取最新内容

---

## 🎓 学到的经验

### 对用户有价值的经验
1. **配置化设计**比硬编码更灵活
2. **职责分离**让代码更易维护
3. **多线程设计**要考虑锁竞争问题
4. **性能统计**需要多视角才有价值
5. **抽象的时机**很重要：过早抽象是浪费，过晚抽象是债务

### 对AI助手的经验
1. 用户说"只会注入一个" → 意味着有多个实现导致冲突
2. 用户问"是否可以抽象" → 需要给出明确的yes/no + 理由
3. 用户提供代码位置 → 要结合上下文理解意图
4. 涉及多线程 → 优先考虑线程安全和性能
5. Java 8项目 → 避免使用Java 9+的API

---

## 📝 后续建议

### 短期（本周）
- [ ] 运行实际测试验证统计准确性
- [ ] 添加单元测试覆盖ThreadStats和FilePostHandler
- [ ] 验证配置切换是否正常工作

### 中期（本月）
- [ ] 集成Micrometer实现指标导出
- [ ] 添加Grafana Dashboard
- [ ] 编写性能测试报告模板

### 长期（本季度）
- [ ] 支持动态调整线程数
- [ ] 支持实时性能监控
- [ ] 集成CI/CD性能基线检查

---

## 📚 参考文档
- [设计规范文档](./perf-test-refactoring-spec.md)
- Spring Boot Conditional Annotations
- Java Concurrency in Practice
- LongAdder vs AtomicLong性能对比

---

> **记录时间**: 2026-06-05  
> **记录者**: AI助手  
> **用途**: 会话复盘、知识沉淀、经验传承

