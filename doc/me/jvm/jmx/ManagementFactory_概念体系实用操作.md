# ManagementFactory 全面解析（以本 Druid 项目为实战参考）

---

## 一、概念体系

`java.lang.management.ManagementFactory` 是 JVM **JMX（Java Management Extensions）**监控体系的门面工厂类，提供对 JVM 运行时状态的**只读监控入口**。它本质上是 MXBean（Management Extension Bean）的工厂。

### 核心关系链

```
ManagementFactory (工厂/门面)
    ├── getPlatformMBeanServer()     →  MBeanServer (JMX 注册中心)
    ├── getRuntimeMXBean()           →  RuntimeMXBean (JVM 运行时)
    ├── getThreadMXBean()            →  ThreadMXBean (线程)
    ├── getMemoryMXBean()            →  MemoryMXBean (内存)
    ├── getOperatingSystemMXBean()   →  OperatingSystemMXBean (OS)
    ├── getGarbageCollectorMXBeans() →  List<GarbageCollectorMXBean> (GC)
    └── getMemoryPoolMXBeans()       →  List<MemoryPoolMXBean> (内存池)
```

### 两大类用途

| 类别 | 方法 | 用途 |
|------|------|------|
| **JMX 基础设施** | `getPlatformMBeanServer()` | 获取 Platform MBeanServer，用于注册/查询/注销 MBean |
| **JVM 监控 MXBean** | 上述 6 类 getXxxMXBean() | 获取 CPU、内存、线程、GC 等运行时指标 |

---

## 二、5 类 MXBean 体系

### 1. RuntimeMXBean —— JVM 运行时

```java
RuntimeMXBean rt = ManagementFactory.getRuntimeMXBean();

rt.getName()              // "pid@hostname" — 获取进程 PID
rt.getUptime()            // JVM 启动以来的毫秒数
rt.getStartTime()         // JVM 启动时间戳 (ms)
rt.getInputArguments()    // JVM 启动参数列表（-Xmx, -D 等）
rt.getVmName()            // 虚拟机名称
rt.getVmVendor()          // 虚拟机厂商
```

> **本项目实战**：`AkLightweightCachedDruidSqlMonitor.java:152-158` 用 `getName()` 取 PID，用 `getUptime()` 和 `getStartTime()` 打印运行时长和启动时间，用 `getInputArguments()` 输出全部 JVM 启动参数。

### 2. ThreadMXBean —— 线程监控

```java
ThreadMXBean thread = ManagementFactory.getThreadMXBean();

thread.getThreadCount()        // 当前活跃线程数
thread.getPeakThreadCount()    // 历史峰值线程数
thread.getDaemonThreadCount()  // 守护线程数
thread.getTotalStartedThreadCount() // 自启动以来创建过的线程总数

// 死锁检测（高级）
thread.findDeadlockedThreads()      // 死锁线程 ID 数组
thread.getThreadInfo(threadId)      // 线程详细信息（栈帧等）
```

> **本项目实战**：`AkLightweightCachedDruidSqlMonitor.java:115-117` 将三者打包为 `"Threads: live=%d / peak=%d / daemon=%d"` 格式输出。

### 3. MemoryMXBean —— 内存监控

```java
MemoryMXBean mem = ManagementFactory.getMemoryMXBean();

MemoryUsage heap = mem.getHeapMemoryUsage();
heap.getUsed()      // 堆已使用 (bytes)
heap.getCommitted() // 堆已提交
heap.getMax()       // 堆最大可用 (-Xmx)

MemoryUsage nonHeap = mem.getNonHeapMemoryUsage();
// 同上，Non-Heap 对应元空间/永久代

// 主动触发 GC
mem.gc();  // 等价于 System.gc()
```

> **本项目实战**：`AkLightweightCachedDruidSqlMonitor.java:108-113` 输出格式：
> `"Heap: used=%dMB / max=%dMB (%.1f%%) | NonHeap: used=%dMB / max=%dMB"`

### 4. OperatingSystemMXBean —— 操作系统监控

这是**两层设计**的典型：

```java
// JDK 标准接口（跨平台）
OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
os.getAvailableProcessors()       // CPU 核数
os.getSystemLoadAverage()         // 系统平均负载 (Unix)
os.getName() / os.getArch() / os.getVersion()  // OS 信息

// Sun/Oracle JDK 扩展接口（向下转型获得更丰富指标）
if (os instanceof com.sun.management.OperatingSystemMXBean) {
    com.sun.management.OperatingSystemMXBean sunOs = 
        (com.sun.management.OperatingSystemMXBean) os;
    
    sunOs.getProcessCpuTime()        // 进程累计 CPU 时间 (纳秒)
    sunOs.getProcessCpuLoad()        // 进程 CPU 使用率 [0, 1]
    sunOs.getSystemCpuLoad()         // 系统 CPU 使用率 [0, 1]
    sunOs.getFreePhysicalMemorySize() // 空闲物理内存
    sunOs.getTotalPhysicalMemorySize()// 总物理内存
}
```

> **本项目实战**：`AkLightweightCachedDruidSqlMonitor.java:65-106` 大量使用此模式：
> - `getProcessCpuTime()` → 计算 CPU/SQL 耗时
> - `getProcessCpuLoad()` → 进程 CPU 占比
> - `getSystemCpuLoad()` → 系统整体 CPU 占比
> - `getAvailableProcessors()` → 核数

### 5. GarbageCollectorMXBean —— GC 监控

```java
List<GarbageCollectorMXBean> gcs = ManagementFactory.getGarbageCollectorMXBeans();
for (GarbageCollectorMXBean gc : gcs) {
    gc.getName()             // "G1 Young Generation" / "G1 Old Generation"
    gc.getCollectionCount()  // GC 次数
    gc.getCollectionTime()   // GC 累计耗时 (ms)
}
```

> **本项目实战**：`AkLightweightCachedDruidSqlMonitor.java:120-129` 遍历并输出 `"GC: G1_Young=123(456ms) G1_Old=78(234ms)"`。

---

## 三、JMX 注册/注销体系（MBeanServer）

`ManagementFactory.getPlatformMBeanServer()` 返回 JVM 内置的 Platform MBeanServer，本项目中 Druid 用它来**暴露数据源统计信息为 MBean**，供 JConsole / VisualVM 等工具消费。

### 注册 MBean（本项目 `JMXUtils.java`）

```java
public static ObjectName register(String name, Object mbean) {
    ObjectName objectName = new ObjectName(name);
    MBeanServer mbeanServer = ManagementFactory.getPlatformMBeanServer();
    try {
        mbeanServer.registerMBean(mbean, objectName);
    } catch (InstanceAlreadyExistsException ex) {
        // 已存在则先注销再重新注册（幂等处理）
        mbeanServer.unregisterMBean(objectName);
        mbeanServer.registerMBean(mbean, objectName);
    }
    return objectName;
}
```

### 注销 MBean

```java
public static void unregister(String name) {
    MBeanServer mbeanServer = ManagementFactory.getPlatformMBeanServer();
    mbeanServer.unregisterMBean(new ObjectName(name));
}
```

### Druid 中注册的典型 MBean 名称

| ObjectName | 对应类 | 功能 |
|---|---|---|
| `com.alibaba.druid:type=DruidStatService` | `DruidStatService` | 统计服务（重置、查询 SQL 统计） |
| `com.alibaba.druid:type=DruidDataSourceStat` | `DruidDataSourceStatManager` | 全局数据源统计汇总 |
| `com.alibaba.druid:type=DruidDataSource,id=xxx` | 各 `DruidDataSource` | 单个数据源的连接池指标 |

---

## 四、本项目实用操作速查表

| 监控目标 | 关键代码 | 文件位置 |
|---|---|---|
| **取 PID** | `ManagementFactory.getRuntimeMXBean().getName()` | `AkLightweightCachedDruidSqlMonitor:158` |
| **JVM 启动参数** | `getRuntimeMXBean().getInputArguments()` | `AkLightweightCachedDruidSqlMonitor:142` |
| **进程运行时长** | `getRuntimeMXBean().getUptime()` | `AkLightweightCachedDruidSqlMonitor:152` |
| **CPU 核数** | `getOperatingSystemMXBean().getAvailableProcessors()` | `AkLightweightCachedDruidSqlMonitor:105` |
| **进程 CPU 时间** | `((sun) os).getProcessCpuTime()` | `AkLightweightCachedDruidSqlMonitor:69` |
| **进程 CPU 率** | `((sun) os).getProcessCpuLoad()` | `AkLightweightCachedDruidSqlMonitor:82` |
| **系统 CPU 率** | `((sun) os).getSystemCpuLoad()` | `AkLightweightCachedDruidSqlMonitor:95` |
| **堆内存** | `getMemoryMXBean().getHeapMemoryUsage()` | `AkLightweightCachedDruidSqlMonitor:110` |
| **非堆内存** | `getMemoryMXBean().getNonHeapMemoryUsage()` | `AkLightweightCachedDruidSqlMonitor:111` |
| **线程数** | `getThreadMXBean().getThreadCount()` | `AkLightweightCachedDruidSqlMonitor:117` |
| **GC 统计** | `getGarbageCollectorMXBeans()` 遍历 | `AkLightweightCachedDruidSqlMonitor:122-127` |
| **内存池详情** | `getMemoryPoolMXBeans()` 遍历 | `AkSqlTemplateMonitor:644` |
| **内存使用量测试** | `getMemoryMXBean().getHeapMemoryUsage().getUsed()` 前后对比 | `HashMapMemoryTest:14-23` |
| **注册 MBean** | `getPlatformMBeanServer().registerMBean()` | `JMXUtils:39` |
| **注销 MBean** | `getPlatformMBeanServer().unregisterMBean()` | `JMXUtils:55` |
| **检查 MBean** | `getPlatformMBeanServer().isRegistered()` | `JMXExporterTest:33` |

---

## 五、最佳实践总结

1. **`getName()` 取 PID 最简洁**：`ManagementFactory.getRuntimeMXBean().getName()` 返回 `"pid@hostname"` 格式，比任何第三方方案都轻量。

2. **Sun 扩展需要 instanceof 检查**：`getProcessCpuLoad()` 等高级指标仅在 HotSpot/JRockit 可用，必须做 `instanceof com.sun.management.OperatingSystemMXBean` 安全检查。

3. **MBean 重复注册做幂等处理**：本项目 `JMXUtils.register()` 的 catch `InstanceAlreadyExistsException` → unregister → re-register 是标准做法。

4. **内存测试模板**：`getHeapMemoryUsage().getUsed()` 在操作前后各取一次，差值即为该操作的内存开销（`HashMapMemoryTest`, `HistogramMemoryTest` 均用此模式）。
