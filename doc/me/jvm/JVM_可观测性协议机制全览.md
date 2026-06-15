# JVM 可观测性协议/机制全览

---

## 一、全景图

```
                        JVM 可观测性技术栈
┌──────────────────────────────────────────────────────────────────┐
│                                                                  │
│  监控                  调试                  诊断               │
│  ────                  ────                  ────               │
│  JMX (RMI)            JDWP                  jcmd                │
│  PerfData              JVMTI                jstack/jmap/jstat   │
│  MXBeans               SA (post-mortem)     GC Log              │
│  JFR (event stream)    JDI (Java API)       Heap Dump (HPROF)   │
│  Unified Logging                                                  │
│                                                                  │
│  原生层                辅助                                  │
│  ────                  ────                                  │
│  DTrace probes         Attach API                           │
│  JVMTI (native)        Instrumentation (javaagent)          │
│  AsyncGetCallTrace     JVMTI agent (-agentlib/agentpath)    │
│  perf (Linux)                                                │
└──────────────────────────────────────────────────────────────────┘
```

---

## 二、分层详解

### 第一层：标准化 API 层（Java 代码直接调用）

#### 1. JMX / Platform MXBeans
```
协议：RMI（远程）或本地进程内调用
端口：自定义（默认无远程端口）
开启：-Dcom.sun.management.jmxremote.port=9999
```
- `ManagementFactory.getRuntimeMXBean()` → JVM 运行时
- `getThreadMXBean()` → 线程
- `getMemoryMXBean()` → 内存
- `getOperatingSystemMXBean()` → OS
- `getGarbageCollectorMXBeans()` → GC
- `getMemoryPoolMXBeans()` → 各内存池

> **已在前两篇文档详述。**

#### 2. java.lang.management 之外的管理 API
```java
// JDK 9+ 新增
Runtime.getRuntimeVersion()      // JDK 版本详情
StackWalker.getInstance(...)      // 高效栈帧遍历（替代 Thread.getStackTrace()）
ProcessHandle.current()           // 进程 PID、子进程管理
ProcessHandle.allProcesses()      // 枚举所有系统进程
ModuleLayer.boot()                // 模块系统层信息
```

---

### 第二层：工具协议层（外部工具连接 JVM）

#### 3. JDWP（Java Debug Wire Protocol）— 调试协议
```
协议：JDWP（自有二进制协议，基于 socket 或共享内存）
端口：5005（惯例）
开启：-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005
```
- IDE 断点调试的底层协议
- 支持：断点、单步、变量读写、表达式求值
- 传输层：dt_socket（TCP）或 dt_shmem（Windows 共享内存）

#### 4. PerfData（hsperfdata）— 性能计数器共享内存
```
协议：共享内存文件映射
路径：/tmp/hsperfdata_<user>/<pid>（Linux）
     %TEMP%\hsperfdata_<user>\<pid>（Windows）
开启：默认自动开启，-XX:-UsePerfData 可关闭
```
- `jstat` 工具的底层数据源
- 暴露 JVM 内部数百个计数器：GC 次数、类加载数、编译线程数等
- 极低开销，只写不读，外部工具主动去读

```bash
jstat -gc <pid> 1000          # 每秒打印 GC 统计
jstat -class <pid>            # 类加载统计
jstat -compiler <pid>         # JIT 编译统计
jstat -gcutil <pid>           # GC 各代使用率百分比
```

#### 5. jcmd（Diagnostic Command Framework）— 诊断命令
```
协议：Attach API（本地）或 JMX（远程）
开启：默认开启
```
```bash
jcmd <pid> help                          # 列出该进程支持的所有命令
jcmd <pid> VM.version                    # JVM 版本
jcmd <pid> VM.system_properties          # 系统属性
jcmd <pid> VM.flags                      # JVM 参数
jcmd <pid> Thread.print                  # 线程堆栈（相当于 jstack）
jcmd <pid> GC.run                        # 触发 GC
jcmd <pid> GC.class_histogram            # 类实例直方图（相当于 jmap -histo）
jcmd <pid> VM.command_line               # 启动命令行
jcmd <pid> GC.heap_dump /path/to/dump.hprof  # 堆转储
```

---

### 第三层：原生 JVM 内部机制

#### 6. JFR（JDK Flight Recorder）— 事件记录
```
协议：JFR 事件流（二进制环形缓冲区 → 文件/流）
开启：-XX:StartFlightRecording=duration=60s,filename=recording.jfr
     或 jcmd <pid> JFR.start
```
- **极低开销**（<2%）的事件记录引擎
- 记录：锁竞争、GC、IO、线程、方法采样、自定义事件
- JDK 11 前是商业特性，11+ 开源
- `jfr` 命令行工具分析 `.jfr` 文件

```bash
# 启动飞行记录
jcmd <pid> JFR.start name=rec1 duration=60s filename=/tmp/rec.jfr

# JDK 14+ 支持流式事件（实时推送到外部）
jcmd <pid> JFR.start name=stream1 settings=profile
# 代码中通过 EventStream 消费
```

#### 7. Unified JVM Logging（统一日志框架）— JDK 9+
```
协议：日志输出到文件/stdout/stderr
开启：-Xlog:gc*=info:file=/tmp/gc.log
     -Xlog:all=debug:stdout
```
```bash
-Xlog:gc+heap=debug        # GC + 堆详情
-Xlog:class+load=info      # 类加载
-Xlog:thread+os=info       # 线程
-Xlog:compilation=info     # JIT 编译
-Xlog:all=trace            # 一切（性能冲击大）
```

统一了此前散乱的 `-XX:+PrintGC`、`-verbose:class` 等参数。

#### 8. GC Log（GC 日志）— 传统的 GC 日志（JDK 8-）
```
JDK 8: -XX:+PrintGCDetails -XX:+PrintGCDateStamps -Xloggc:/tmp/gc.log
JDK 9+: -Xlog:gc*=info:file=/tmp/gc.log
```
与 Unified Logging 合并，但概念上独立，专门记录 GC 行为。

---

### 第四层：原生 Agent 机制

#### 9. JVMTI（JVM Tool Interface）— 原生工具接口
```
协议：C/C++ 原生库，编译为 .so / .dll / .dylib
开启：-agentlib:<name>=<options>     # 用 JVM 预装 agent
      -agentpath:<path>=<options>   # 用自定义路径 agent
```
```bash
-agentlib:jdwp=transport=dt_socket,address=5005    # JDWP 调试（基于 JVMTI）
-agentlib:hprof=cpu=samples,file=profile.hprof     # HPROF 性能采样
-agentpath:/path/to/custom-agent.so=arg1,arg2      # 自定义 JVMTI agent
```

JVMTI 是 JDWP 和所有原生分析工具（YourKit、JProfiler）的**底层基础**。它提供了约 200+ 个回调/函数，可以：
- 拦截类加载
- 拦截方法进入/退出
- 修改字节码
- 读/设 局部变量
- 强制 GC
- 获取所有线程

#### 10. java.lang.instrument（Java Agent）— Java 层 Agent
```
协议：Java 代码，打包为 jar，通过 MANIFEST.MF 声明
开启：-javaagent:<jar-path>=<options>
```
```bash
-javaagent:bytebuddy-agent.jar        # 字节码增强（Mockito, ByteBuddy）
-javaagent:jacocoagent.jar            # 代码覆盖率
-javaagent:skywalking-agent.jar       # APM 探针
```

**与 JVMTI 的区别**：
| JVMTI Agent | Java Agent |
|---|---|
| C/C++ 代码 | Java 代码 |
| `-agentlib/agentpath` | `-javaagent` |
| 可操作原生内存 | 只能操作 Java 对象 |
| 最强大的能力 | 更安全，易开发 |

#### 11. Attach API — 动态挂载
```
协议：本地 socket 或信号
开启：默认开启，-XX:+DisableAttachMechanism 可关闭
```
```java
// 代码中动态 attach 到另一个 JVM
VirtualMachine vm = VirtualMachine.attach("12345"); // 目标进程 PID
vm.loadAgent("/path/to/agent.jar");    // 动态加载 agent
vm.loadAgentLibrary("/path/to/lib.so");// 动态加载原生 agent
Properties props = vm.getSystemProperties();
vm.detach();
```

这就是 `jcmd`、`jstack`、`jmap` 等工具能连接到**已经运行的 JVM** 的原理。Arthas（阿里开源诊断工具）也是基于此。

---

### 第五层：高级诊断/分析机制

#### 12. AsyncGetCallTrace — 异步栈采样
```
不是公开 API，但几乎所有 Java profiler 都依赖它
```
- `sun.misc.Unsafe` 或 JVMTI 中类似函数
- 在信号处理器（signal handler）中安全地获取线程栈
- **无需 Safepoint**——这是关键，Safepoint 偏斜会让普通采样失真
- async-profiler 项目将此发挥到极致

#### 13. Heap Dump（HPROF 格式）
```
触发方式：
  jcmd <pid> GC.heap_dump /tmp/dump.hprof
  jmap -dump:live,format=b,file=/tmp/dump.hprof <pid>
  -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/tmp
```
- 标准二进制格式，记录堆中所有对象及引用关系
- Eclipse MAT / JProfiler / VisualVM 分析

#### 14. Thread Dump（线程转储）
```
触发方式：
  jstack <pid>
  jcmd <pid> Thread.print
  kill -3 <pid>  (Linux，信号触发)
  Thread.getAllStackTraces() (编程方式)
```
- 文本格式，列出所有线程的栈帧和锁信息
- 用于死锁检测、线程池分析

#### 15. JDI（Java Debug Interface）— 调试 Java API
```
JDWP 的 Java 编程接口，允许用 Java 代码写调试器
```
```java
VirtualMachineManager vmm = Bootstrap.virtualMachineManager();
AttachingConnector connector = ...;
VirtualMachine vm = connector.attach(...);
// 程序化地设置断点、获取变量等
```

#### 16. SA（Serviceability Agent）— 事后诊断
```
用途：分析 core dump 或 crash 后的 JVM 状态
工具：
  jhsdb jmap --heap --pid <pid>       # 查看堆
  jhsdb jstack --pid <pid>            # 线程栈
  jhsdb jsnap --pid <pid>             # 快照
  jhsdb hsdb                          # GUI 工具，可以看内存里的对象
```
与普通 jmap/jstack 不同，SA **不依赖活的 JVM**，可以分析 core dump。

---

### 第六层：平台相关

#### 17. DTrace Probes（Solaris/macOS/Linux）
```
JVM 内置的 DTrace/USDT 探针
开启：-XX:+ExtendedDTraceProbes
```
```bash
# 追踪方法进入（有性能开销）
dtrace -n 'hotspot*:::method-entry { @[copyinstr(arg0)] = count(); }'
```

#### 18. Linux perf 集成
```
JDK 8u60+ 支持 -XX:+PreserveFramePointer
配合 perf 可看到 Java 方法栈的火焰图
```
```bash
perf record -g -p <pid> -- sleep 30
perf script | FlameGraph/stackcollapse-perf.pl | FlameGraph/flamegraph.pl > flame.svg
```

---

## 三、速查总表

| 机制 | 协议 | 开启方式 | 用途 | 开销 |
|------|------|----------|------|------|
| **JMX** | RMI | `-Dcom.sun.management.jmxremote.port=` | 监控、管理 | 极低 |
| **JDWP** | JDWP | `-agentlib:jdwp=` | 断点调试 | 中（断点时高） |
| **PerfData** | 共享内存 | 默认开启 | `jstat` 计数器 | 极低 |
| **jcmd** | Attach API | 默认开启 | 诊断命令 | 极低 |
| **JFR** | 二进制流/文件 | `-XX:StartFlightRecording` | 事件记录 | 极低(<2%) |
| **Unified Logging** | 文件/stdout | `-Xlog:` | 各类日志 | 可配置 |
| **JVMTI** | 原生库 | `-agentlib/agentpath` | 原生工具 | 取决于 agent |
| **Java Agent** | jar | `-javaagent:` | 字节码增强 | 取决于 agent |
| **Attach API** | 本地 socket | 默认开启 | 动态 attach | 极低 |
| **Heap Dump** | HPROF | `jcmd/jmap` 或 OOM 触发 | 堆分析 | 快照瞬间 STW |
| **Thread Dump** | 文本 | `jstack` / `kill -3` | 线程分析 | Safepoint |
| **SA (Serviceability Agent)** | 独立进程 | `jhsdb` | post-mortem | 无 |
| **AsyncGetCallTrace** | 函数调用 | profiler 调用 | 采样 profiling | 采样频率相关 |
| **DTrace** | DTrace | `-XX:+ExtendedDTraceProbes` | 系统级追踪 | 较高 |

---

## 四、一句话总结

JVM 的可观测性技术每代都在进化：

```
JDK 1.0-1.4: 几乎什么都没有（靠打日志）
JDK 5:       JMX + java.lang.management 横空出世（标准化监控的开端）
JDK 6:       Attach API（终于能动态挂载了）
JDK 7:       JFR 首次出现（商业版）
JDK 8:       成熟期，jcmd/jstat/jstack/jmap 工具链完备
JDK 9+:      Unified Logging 统一日志、模块化
JDK 11:      JFR 开源（所有 JVM 都能用了）
JDK 14+:     JFR Event Streaming（实时流式事件给外部）
JDK 17+:     Foreign Function & Memory API（未来可能替代部分 JVMTI）
```

从只能靠 `System.out.println` 调试，到 JMX 标准化监控、到 JFR 零开销记录、到实时事件流——JVM 的"自我观察能力"在持续增强。
