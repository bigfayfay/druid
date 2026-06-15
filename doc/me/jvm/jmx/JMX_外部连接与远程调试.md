# JMX 外部连接与远程调试

---

## 一、外部工具是怎么连接的

JMX 连接分三种场景，复杂度递增：

### 场景 1：本地连接（最简单，零配置）

```
JConsole / VisualVM 和 Java 进程在同一台机器、同一个用户
```

**原理**：JDK 通过 `/tmp/hsperfdata_<user>/<pid>` 共享文件发现本地 JVM，走本地进程间通信，**不走网络**。

```bash
# 启动任何 Java 程序后，直接
jconsole          # GUI 工具，自动列出本地 Java 进程，双击即可
jcmd              # 列出所有本地 JVM 进程
jcmd <pid> help   # 看该进程支持哪些诊断命令
```

> **Druid 项目的本地连接测试**：`JMXExporterTest.java:33` 就是直接用 `ManagementFactory.getPlatformMBeanServer().isRegistered(objectName)` 在**同一个 JVM 内**验证注册状态，本质就是本地连接。

---

### 场景 2：远程连接（要开端口，配认证）

**核心协议链**：

```
外部工具 ──JMX 数据──▶ RMI 适配器 ──RMI──▶ MBeanServer
         ◀──响应────                ◀──────

整个基于 RMI（Remote Method Invocation），默认端口 1099（RMI Registry）+ 随机端口（RMI Server）
```

#### 关键 JVM 启动参数

```bash
java \
  -Dcom.sun.management.jmxremote \                    # ① 开启 JMX 远程
  -Dcom.sun.management.jmxremote.port=9999 \          # ② JMX 端口
  -Dcom.sun.management.jmxremote.rmi.port=9999 \      # ③ RMI 端口（最好设同一个）
  -Dcom.sun.management.jmxremote.ssl=false \          # ④ 关闭 SSL（生产开 true）
  -Dcom.sun.management.jmxremote.authenticate=false \ # ⑤ 关闭认证（生产开 true）
  -Djava.rmi.server.hostname=192.168.1.100 \          # ⑥ 本机 IP（多网卡必设）
  -jar myapp.jar
```

#### 用代码建立远程连接

```java
// JMXServiceURL 格式: service:jmx:rmi:///jndi/rmi://host:port/jmxrmi
String url = "service:jmx:rmi:///jndi/rmi://192.168.1.100:9999/jmxrmi";
JMXServiceURL serviceURL = new JMXServiceURL(url);
JMXConnector connector = JMXConnectorFactory.connect(serviceURL);
MBeanServerConnection mbsc = connector.getMBeanServerConnection();

// 现在可以像本地一样读任何 MBean
ObjectName name = new ObjectName("com.alibaba.druid:type=DruidStatService");
String result = (String) mbsc.invoke(name, "service",
    new Object[]{"/basic.json"}, new String[]{"java.lang.String"});
```

#### 连接流程步骤

```
步骤 1: JVM 启动时加 -Dcom.sun.management.jmxremote.port=9999
         ↓
步骤 2: JVM 在 9999 端口启动 RMI Registry
         ↓
步骤 3: JConsole 打开 → 选"远程进程" → 输入 192.168.1.100:9999
         ↓
步骤 4: RMI 协议握手、协商
         ↓
步骤 5: 连接建立，JConsole 拉取 MBeanServer 上所有注册的 MBean
         ↓
步骤 6: 后续每次展开属性/调用操作都是 RMI 远程调用
```

---

### 场景 3：HTTP 适配器（不直接走 JMX 协议）

Druid 项目其实**主要用 HTTP 暴露指标**，不走 RMI/JMX 协议。它的 `StatViewServlet` 是一个 HTTP 接口：

```java
// 注册 Servlet（web.xml 或 @WebServlet）
// 访问: http://localhost:8080/druid/datasource.json
// 返回 JSON 格式的连接池统计数据
```

这是**更轻量的替代方案**——数据还是从注册的 MBean 里读的，但传输协议走 HTTP 而非 RMI。

---

## 二、远程调试 vs JMX —— 两回事

远程调试和 JMX 是**完全不同的两套机制**。

### 对比表

| | **JMX（监控/管理）** | **Remote Debug（远程调试）** |
|---|---|---|
| **目的** | 看运行指标、调参数 | 断点、单步执行、看变量 |
| **JVM 参数** | `-Dcom.sun.management.jmxremote.port=9999` | `-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005` |
| **协议** | RMI（JMX over RMI） | **JDWP（Java Debug Wire Protocol）** |
| **端口** | 9999（可自定义） | 5005（惯例，可自定义） |
| **工具** | JConsole, VisualVM, Zabbix | IDE（IDEA, Eclipse）, jdb |
| **能做什么** | 读属性、调方法、收通知 | 断点暂停、单步执行、修改变量值 |
| **性能开销** | 很低，定期拉数据 | 较高，断点会暂停线程 |
| **内核模块** | JMX Agent（`management-agent`） | JVMTI Agent（`jdwp`） |

### 参数拆解

```bash
# 远程调试 — 完全不是 JMX
java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005 -jar app.jar
#     ↑          ↑                    ↑         ↑           ↑
#     JDWP协议    socket传输          作为服务端  不等待连接   监听5005端口

# JMX 远程 — 不是调试
java -Dcom.sun.management.jmxremote.port=9999 -jar app.jar
```

它们底层的实现机制也不同：

```
远程调试:
  IDE ──JDWP协议──▶ JVMTI Agent (jdwp) ──直接操纵──▶ JVM 内部线程/栈/变量
  可以"暂停整个世界"

JMX:
  JConsole ──RMI──▶ MBeanServer ──调用 getter/method──▶ MBean 对象
  永远只读/调方法，不暂停 JVM
```

### 一句话区分

| 你想干什么 | 用什么 |
|---|---|
| 想看连接池用了几个、堆内存还剩多少 | **JMX** |
| 想知道为什么走到这行代码时 password 是 null | **Remote Debug**（`-agentlib:jdwp`） |
| 想动态改日志级别、清缓存 | **JMX** |
| 想在某个条件满足时断点暂停分析堆栈 | **Remote Debug** |

---

## 三、完整连接体系图

```
                    ┌──────────────┐
                    │   JConsole   │  ← GUI 工具
                    │  VisualVM    │
                    │  Prometheus  │  ← 通过 JMX Exporter
                    └──┬───┬───┬──┘
                       │   │   │
          ┌────────────┼───┼───┼────────┐
          │ RMI        │   │   │ HTTP    │
          ▼            ▼   ▼   ▼         ▼
   ┌──────────┐  ┌─────────────────┐  ┌────────────────┐
   │ MBeanServer│  │ Druid          │  │ IDE (IDEA)     │
   │ (JMX总线)  │  │ StatViewServlet│  │                │
   │            │  │ /druid/*.json  │  │ -agentlib:jdwp │
   └──────────┘  └─────────────────┘  └────────────────┘
        ↑                                   ↑
   注册 MBean                           JDWP 协议
   (监控/管理)                         (断点调试)
```

两套体系，互不干扰，可以同时开启。
