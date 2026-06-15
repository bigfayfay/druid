# CLAUDE.md

## 默认回答语言

所有解释性内容、步骤说明、分析、文字回答均默认使用 **中文**。
所有 代码块 (code) 、文档中的内容保持原始语言，不进行中文化或翻译。

## 仓库概述

**druid-ak** 是基于 Alibaba Druid 1.2.27 定制修改的 SQL 解析工具包，核心功能是从 SQL 语句中提取标准化 SQL 模板。

| 项目 | 说明 |
|------|------|
| **GAV** | `com.alibaba:druid-ak:1.2.27` |
| **JAR** | `druid-ak-1.2.27-jar-with-dependencies.jar` |
| **基准类** | `com.ankki.druid.parser.CustomerOutputVisitorUtils` |
| **优化类** | `com.ankki.druid.parser.template.LightweightCachedAkOutputVisitorUtils` |
| **优化目标** | `getSqlTemplate_v2(sql, akDbTypeId)` 方法性能优化 |
| **基准测试** | `SqlTemplateComparisonBenchmark` (JMH 1.19, avgt 模式) |
| **当前版本** | v4（三级 LRU 缓存 + compareExtract 对照提取） |

**当前状态**: v4 优化已完成，所有场景正向优化（4%~20%），测试报告已归档。

## 本地开发环境

| 工具 | 路径 | 版本 |
|------|------|------|
| **JDK** | `D:\Program Files\Java\jdk-1.8-411` | 1.8.411 |
| **Maven** | `D:\Program Files\maven-3.5.3` | 3.5.3 |

系统默认 JDK(1.8) 和 Maven(3.5.3) 版本过低，构建时必须显式指定上述路径。javac 编译需加 `-encoding UTF-8` 参数。

## 优化方案：三级 LRU 缓存（v4，已实施）

### 架构

`LightweightCachedAkOutputVisitorUtils` 采用三级 LRU 缓存：

| 级别 | 缓存名 | 容量 | 匹配方式 | 适用场景 |
|------|--------|------|----------|----------|
| **L1** | NO_PARAM_CACHE | 500 | 精确匹配 | 无参 SQL（SELECT/DDL） |
| **L2** | BIND_CACHE | 200 | 精确匹配 | 绑定参数 SQL（`$1/$2` 格式） |
| **L3** | CACHE | 1000 | 前缀匹配 + compareExtract 对照提取 | 普通参数 SQL |

### 核心算法

- **compareExtract**: 字符级逐一比较模板与原始 SQL，用 castStack 处理 PostgreSQL 类型转换（`int4`, `oracle.to_number`, `oracle.to_date` 等）
- **normalizeTableAliasAS**: 移除表别名 AS，确保缓存键一致性
- **CachedResult**: 预计算参数偏移量数组，缓存命中后零拷贝提取参数

### v4 性能测试结果（2026-05-20，公平测试：独立 JVM 进程）

| 场景 | 基准 (ns/op) | 优化 (ns/op) | 变化 |
|------|-------------|-------------|------|
| single_insert | 249,397 | 222,652 | **-10.7%** |
| single_select | 17,586 | 15,782 | **-10.3%** |
| single_begin | 227,156 | 217,906 | **-4.1%** |
| single_update | 189,009 | 181,809 | **-3.8%** |
| begin_only (23条) | 3,949,872 | 3,269,136 | **-17.2%** |
| insert_only (29条) | 8,749,555 | 7,014,355 | **-19.8%** |
| select_only (24条) | 2,596,606 | 2,344,825 | **-9.7%** |
| update_only (24条) | 1,076,659 | 1,014,797 | **-5.7%** |
| mixed_100 (100条) | 15,012,672 | 13,408,575 | **-10.7%** |

## 性能压测

### 自动化测试脚本

| 脚本 | 用途 | 说明 |
|------|------|------|
| `fair_jmh_test.py` | 公平对比测试（推荐） | baseline/optimized 分开运行在独立 JVM 进程，4 阶段测试 |
| `auto_jmh_test.py` | 快速综合测试 | 同一 JVM 进程内对比，耗时较短 |

### 公平对比测试（推荐）

```bash
cd 调试脚本
python fair_jmh_test.py
```

**测试流程**（4 个独立 JVM 进程）：
1. Phase 1a: 单条 SQL — 基准版本（预热 3 轮 × 1s，测试 10 轮 × 3s）
2. Phase 1b: 单条 SQL — 优化版本
3. Phase 2a: 批量/混合 — 基准版本
4. Phase 2b: 批量/混合 — 优化版本

每个阶段之间会 `pkill` JVM 并清理 `/tmp/jmh.lock`，确保公平性。

**输出目录**：`性能测试结果/YYYYMMDDHHMM/`

### 测试服务器

| 项目 | 值 |
|------|------|
| **主机** | 172.19.4.41 |
| **用户名** | root |
| **密码** | @1fw#2soc$3vpn |
| **上传路径** | /root/ |

### 测试数据库

| 项目 | 值 |
|------|------|
| **数据库类型** | clickhouse |
| **主机** | 172.19.4.41:8123 |
| **用户名** | root |
| **密码** | Ankki_cK123 |
| **数据库** | bs_audit |
| **SQL语句** | audit_record.operSentence |
| **数据库客户端工具** | python pymysql 库 |

## 性能优化历史

### 方案 A：热路径优化（已实施，效果不显著）

**实施时间**: 2026-05-16

**优化内容**：`OptimizedAkOutputVisitorUtils` — ThreadLocal MD5 复用、UTF-8 显式编码、char[32] 替代 StringBuilder。

**结果**：平均耗时 +1.2%，吞吐量 -1.3%。SQL 解析占 95%+ 时间，热路径优化收益有限。

### 方案 B：三级 LRU 缓存（v4，已完成）

**实施时间**: 2026-05-17 ~ 2026-05-20

**优化内容**：`LightweightCachedAkOutputVisitorUtils` — L1 无参缓存 + L2 绑定参数缓存 + L3 前缀匹配对照提取缓存。

**结果**：全场景正向优化 4%~20%，批量 INSERT/BEGIN 提升 17%~20%。

## 文档结构

```
docs/
├── 对照提取SQL参数设计文档.md    # v4 优化方案设计文档（流程、伪代码、缓存架构）
├── 单条SQL Benchmark测试报告.md  # 单条 SQL 性能对比报告
└── 混合场景 Performance测试报告.md # 批量/混合场景性能对比报告

性能测试结果/
├── v4测试总结报告.md             # v4 测试总结
├── v4综合性能测试报告.md          # v4 综合性能报告（含完整 JMH 数据）
└── YYYYMMDDHHMM/                # 各次测试的原始数据目录

调试脚本/
├── fair_jmh_test.py              # 公平对比测试（独立 JVM）
├── auto_jmh_test.py              # 快速综合测试
└── generate_report_fixed.py      # 报告生成工具

druid-ak/com/ankki/druid/parser/
├── CustomerOutputVisitorUtils.java              # 基准版本（未修改）
└── template/
    └── LightweightCachedAkOutputVisitorUtils.java # 优化版本 v4
```

## 构建与打包

### 项目结构说明

本项目为非标准 Maven 布局：
- Druid 核心源码在 `druid-ak/src/main/java/`
- 定制类源码在 `druid-ak/com/ankki/druid/`（反编译后的 .java 文件）
- Maven 构建只处理 `src/main/java/`，定制类需单独编译

### 修改代码后重新打包（推荐流程）

```bash
# 1. 编译修改/新增的 Java 文件（注意 -encoding UTF-8）
cd druid-ak
"D:/Program Files/Java/jdk-1.8-411/bin/javac" -encoding UTF-8 \
  -cp ../druid-ak-1.2.27-jar-with-dependencies.jar \
  -d target/classes \
  com/ankki/druid/parser/template/LightweightCachedAkOutputVisitorUtils.java

# 2. 备份原 JAR
cp ../druid-ak-1.2.27-jar-with-dependencies.jar ../druid-ak-1.2.27-jar-with-dependencies.jar.bak

# 3. 更新 JAR（注入新的 class 文件）
cd target/classes
"D:/Program Files/Java/jdk-1.8-411/bin/jar" uf \
  "../../druid-ak-1.2.27-jar-with-dependencies.jar" \
  com/ankki/druid/parser/template/LightweightCachedAkOutputVisitorUtils.class

# 4. 验证新类已注入
py -c "import zipfile; jar = zipfile.ZipFile('../druid-ak-1.2.27-jar-with-dependencies.jar'); print('OK' if 'com/ankki/druid/parser/template/LightweightCachedAkOutputVisitorUtils.class' in jar.namelist() else 'NOT FOUND')"
```

### 常见编译目标

| 修改文件 | 编译命令 |
|---------|---------|
| `LightweightCachedAkOutputVisitorUtils.java` | `javac -encoding UTF-8 -cp ../druid-ak-1.2.27-jar-with-dependencies.jar -d target/classes com/ankki/druid/parser/template/LightweightCachedAkOutputVisitorUtils.java` |
| `CustomerOutputVisitorUtils.java` | `javac -encoding UTF-8 -cp ../druid-ak-1.2.27-jar-with-dependencies.jar -d target/classes com/ankki/druid/parser/CustomerOutputVisitorUtils.java` |

## 自检和校验

修改代码后，需要我确认后再做测试与验证。
