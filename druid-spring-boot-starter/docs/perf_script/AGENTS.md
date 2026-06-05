# AGENTS.md

## 仓库概述

`druid-ak` 是基于 Alibaba Druid 1.2.27 定制修改的 SQL 解析工具包，核心功能是 `CustomerOutputVisitorUtils.getSqlTemplate_v2()` 从 SQL 语句提取标准化模板。当前工作：性能优化（LRU 缓存方案）。

## 回答语言

所有中文文本、解释说明默认使用中文。代码块内容保持原始语言。

## 本地开发环境（非默认路径）

- JDK: `D:\Program Files\Java\jdk-1.8-411` (系统默认 1.8 过低，需显式指定)
- Maven: `D:\Program Files\maven-3.5.3` (系统默认 3.5.3 过低)
- 构建编码: UTF-8 (javac 需加 `-encoding UTF-8` 参数，否则中文注释会在 GBK 环境下报编码错误)

## 项目结构（非标准 Maven 布局）

- 核心定制类: `druid-ak/com/ankki/druid/`（反编译后的 .java 文件）
- Druid 源码: `druid-ak/src/main/java/`
- Maven 只处理 `src/main/java/`，定制类需单独编译后注入 JAR

## 修改代码后重新打包流程

```bash
# 1. 编译（注意加 -encoding UTF-8）
cd druid-ak
"D:/Program Files/Java/jdk-1.8-411/bin/javac" \
  -encoding UTF-8 \
  -cp ../druid-ak-1.2.27-jar-with-dependencies.jar \
  -d target/classes \
  com/ankki/druid/parser/YourModifiedClass.java

# 2. 备份原 JAR
cp ../druid-ak-1.2.27-jar-with-dependencies.jar ../druid-ak-1.2.27-jar-with-dependencies.jar.bak

# 3. 注入 JAR
cd target/classes
"D:/Program Files/Java/jdk-1.8-411/bin/jar" uf \
  "../../druid-ak-1.2.27-jar-with-dependencies.jar" \
  com/ankki/druid/parser/YourModifiedClass.class

# 4. 验证
py -c "import zipfile; jar = zipfile.ZipFile('../druid-ak-1.2.27-jar-with-dependencies.jar'); print('OK' if 'com/ankki/druid/parser/YourModifiedClass.class' in jar.namelist() else 'NOT FOUND')"
```

## 性能压测（推荐自动化方式）

```bash
cd 调试脚本
python auto_jmh_test.py
```

自动完成：上传 JAR → 执行 JMH（吞吐量+平均耗时两种模式）→ 下载结果 → 生成 HTML 和 Markdown 报告。

**输出目录**: `性能测试结果/YYYYMMDDHHMM/`

**服务器**: 172.19.4.41 / root / `@1fw#2soc$3vpn`

## 手动执行 JMH 测试

```bash
# 上传
scp druid-ak-1.2.27-jar-with-dependencies.jar root@172.19.4.41:/root/

# 运行（吞吐量 + 平均耗时）
ssh root@172.19.4.41 "cd /root && java -cp druid-ak-1.2.27-jar-with-dependencies.jar org.openjdk.jmh.Main -bm thrpt,avgt '.*CustomerOutputVisitorUtilsBenchmark.*' 2>&1 | tee /root/jmh_results.txt"

# 下载结果
scp root@172.19.4.41:/root/jmh_results.txt ./

# 生成报告
cd 调试脚本
python generate_report_fixed.py --input jmh_results.txt --output report.html
```

## 性能优化现状

- **方案 A**（热路径优化）：已实施，效果不显著（~1% 变化），SQL 解析占 95%+ 时间
- **方案 B**（LRU 缓存）：已实施，`LightweightCachedAkOutputVisitorUtils` 已完成多轮 CPU 优化

## LightweightCachedAkOutputVisitorUtils 优化历史

### 已实施的 CPU 优化
1. **CachedResult 预计算字段**: normFull, normSuffixStart, normSuffix, compareSuffix, compareFull 在构建时计算好，减少运行时重复 normalize
2. **LongAdder 替代 volatile long**: 计数器从 `volatile long` + `synchronized` 改为 `LongAdder`，消除 CAS 争用
3. **Quick AS check**: `normalizeTableAliasAS()` 先检查 `indexOf(" AS ")` 再决定是否截断
4. **NO_PARAM_CACHE**: 无参数 SQL 独立 LRU 缓存（`LinkedHashMap` 1000 条），精确匹配，命中直接返回不走 compareExtract
5. **BIND_CACHE**: 绑路径缓存（`;` 分割的 UPDATE），存的是 `SQLUtils.format` 输出（不含 `?`），减少 Druid 重解析
6. **basicNormalize 复用**: 一次 normalize 结果在多路径共享（no-param/bind/compare）
7. **命中计数器**: 为 no-param/bind 缓存添加 `noParamHitCount`/`bindHitCount`（LongAdder）
8. **编译编码修复**: 本地 Windows GBK 环境需加 `-encoding UTF-8` 避免中文注释报错

### 待验证/考虑
- compareExtract 字符级比对的进一步优化（长 SQL 场景）
- 缓存命中率真实数据（需 JMH 压测或生产监控）

## 基准指标参考

| 指标 | 数值 |
|------|------|
| 吞吐量 | ~3.45 ops/ms (~3,450 ops/s) |
| 平均耗时 | ~290,000 ns/op |
| 测试轮次 | 5 轮/模式 |

## 自检要求

修改代码后，需人工确认后再进行测试与验证。

## Goal
- Production monitoring for `rule_engine` (JVM process running Druid SQL template extraction); analyze & optimize CPU usage for ~9亿 daily calls.

## Constraints & Preferences
- 14 processes running on server, each with multiple threads; JNDI invocation to Druid parser.
- `;(` bind-path UPDATEs skip LRU cache entirely (`bindMetadata != null` → `shouldCache = false`).
- Server is Linux (JDK 17, 40 cores); local dev uses JDK 1.8 on Windows.
- Monitoring files must include PID + date to separate 14 JVM outputs.
- `DruidSqlMonitor` runs inside each JVM — captures that process only, not system-wide.

## Progress
### Done
- Built monitoring solution: `DruidSqlMonitor.java` collects PID, Process/System CPU, Heap, GC, cache stats (hit/miss/skip), QPS, CPU/SQL, JVM args per-JVM.
- Modified `CustomerOutputVisitorUtils.getSqlTemplate_v2()` to lazy-start a daemon timer (60s interval) that writes DruidSqlMonitor output to `/tmp/druid_monitor/druid_monitor_${PID}_${yyyyMMdd}.log`; auto-creates directory.
- Added `TOTAL_CALLS` counter in `CustomerOutputVisitorUtils` incremented on every `getSqlTemplate_v2()` call; used by DruidSqlMonitor for QPS + CPU/SQL calculation.
- Modified `LightweightCachedAkOutputVisitorUtils` to add `skipCount` — separates bind-path skips from real cache misses in `getCacheStats()` output (`hit=X miss=Y skip=Z`).
- Deleted duplicate `parser/LightweightCachedAkOutputVisitorUtils.java` (stale, wrong package); only `template/` version kept.
- Built and tested `druid-ak-1.2.27-jar-with-dependencies-v3.jar` (32/33 tests pass; 1 expected fail = UPDATE bind-path uses `$1` not `?`).
- Analyzed 3 production monitoring snapshots at -Xmx <2G: QPS ~8.5-9.5K, 85% cache hit, **300 Young GC/s** → GC was dominant CPU consumer (~17% of CPU).
- Analyzed 3 snapshots at -Xmx 4G: QPS ~11K, **100% cache hit** (cacheable), GC dropped to **3.5/s** (~1.6% CPU), skip≈15% (bind-path), cache size 273/1000.
- Added CPU/SQL metric to DruidSqlMonitor output (via `OperatingSystemMXBean.getProcessCpuTime()` — whole JVM, not just Druid).
- Analyzed `ps aux`: rule_engine (PID 1361978) uses **1266% CPU ≈ 12.7 cores**; 14× parse_engine 40-75% each ~7.5 cores; clickhouse 793% ~7.9 cores.
- Identified that -Xmx 6G gave no further CPU reduction (GC already resolved at 4G); remaining CPU dominated by rule_engine business logic + JNI/JNA overhead + GC baseline.
- LightweightCachedAkOutputVisitorUtils CPU 优化：CachedResult 预计算、LongAdder、quick AS check、NO_PARAM_CACHE、BIND_CACHE、basicNormalize 复用、命中计数器、编译编码修复。

### In Progress
- *(none)*

### Blocked
- *(none)*

## Key Decisions
- **Monitoring approach**: timer thread inside `CustomerOutputVisitorUtils.getSqlTemplate_v2()` (lazy-started via `AtomicBoolean.compareAndSet`), not external JMX — avoids modifying JNDI interface.
- **Cache skip separation**: `skipCount` in `LightweightCachedAkOutputVisitorUtils` splits bind-path bypass from real cache misses, so production `命中率` is accurate (hit/(hit+miss), excludes skip).
- **CPU/SQL metric**: derived from `getProcessCpuTime()` (whole JVM CPU time) divided by call delta — not pure Druid CPU, but the only practical proxy.
- **Output path**: `/tmp/druid_monitor/` directory with `${PID}_${yyyyMMdd}` per-file to isolate 14 processes.
- **GC not the current bottleneck**: at -Xmx 4G+ the remaining 1266% CPU is from rule_engine business logic + JNI cross-process overhead, not GC.

## Next Steps
1. Confirm whether remaining CPU is JNI-heavy (`vmstat 1`: if `sy` > 15% and `cs` > 50k/s → JNI/cross-process overhead is the bottleneck).
2. Consider taskset binding to reduce NUMA cross-socket cost (40 cores likely dual-socket).
3. If rule_engine business logic dominates, focus reductions there (not in Druid layer).
4. 对 LightweightCachedAkOutputVisitorUtils 优化后的 JAR 进行 JMH 压测，验证 no-param/bind 缓存效果。

## Critical Context
- **CPU breakdown per production snapshot at -Xmx 4G**: JVM=32.7%×40=13 cores for ~11K QPS; each SQL consumed ~1190µs CPU (includes Druid compareExtract ~200µs + rule_engine business + GC ~60µs + JNI ~200-400µs + context switch).
- **CGroup memory limit suspected** as cause of <2G GC storm; verify with `cat /sys/fs/cgroup/memory/memory.limit_in_bytes`.
- `JVM args:` was empty in production (user confirmed only `-Xmx4G` was set); no `-XX:+UseG1GC` or GC tuning flags.
- `getProcessCpuTime()` may not be available on all JDK builds (falls back to `-1`; Linux Oracle/OpenJDK with `com.sun.management` works).
- Druid source file location: `druid-ak/com/ankki/druid/parser/template/` (not `parser/`).

## Relevant Files
- `F:\2026年\03-现场项目\AAS-B07\02-中广电移动网络\druid-ak\com\ankki\druid\parser\template\DruidSqlMonitor.java`: JVM-level monitoring (CPU, memory, GC, QPS, cache stats, CPU/SQL)
- `F:\2026年\03-现场项目\AAS-B07\02-中广电移动网络\druid-ak\com\ankki\druid\parser\CustomerOutputVisitorUtils.java`: modified — monitor timer startup + TOTAL_CALLS counter
- `F:\2026年\03-现场项目\AAS-B07\02-中广电移动网络\druid-ak\com\ankki\druid\parser\template\LightweightCachedAkOutputVisitorUtils.java`: LRU 缓存实现 + CPU 优化系列
- `F:\2026年\03-现场项目\AAS-B07\02-中广电移动网络\druid-ak-1.2.27-jar-with-dependencies.jar`: 当前最新 JAR（已注入最新编译的 class）
- `F:\2026年\03-现场项目\AAS-B07\02-中广电移动网络\调试脚本\生产环境监控分析.md`: monitoring analysis guide with diagnosis flow
