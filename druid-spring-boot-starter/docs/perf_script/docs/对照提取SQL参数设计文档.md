# LightweightCachedAkOutputVisitorUtils 设计文档

**版本**: v4 — 三级缓存 + AS 标准化 + 动态类型转换对照提取
**日期**: 2026-05-20

---

## 1. 方案概述

通过三级 LRU 缓存 Druid SQL 解析结果，在缓存命中时使用逐字符对照提取参数值，跳过 Druid 完整解析，提升 SQL 模板提取性能。

核心难点在于：
1. Druid `parameterize()` 对 PostgreSQL 类型函数的重写（`int4(expr)` → `expr::int4`），导致原始 SQL 和模板结构不一致
2. PostgreSQL 允许 `table AS alias` 和 `table alias` 两种写法，Druid 输出可能不同，导致缓存 key 不一致

v4 通过动态栈匹配解决类型转换问题，通过 `normalizeTableAliasAS` 统一去掉表别名 AS 解决缓存 key 不一致问题，并引入三级缓存架构提升整体命中率。

---

## 2. 三级缓存架构

| 层级 | 名称 | 容量 | 匹配方式 | 适用场景 |
|------|------|------|----------|----------|
| **L1** | `NO_PARAM_CACHE` | 500 | 精确匹配（`akDbTypeId\|normSql`） | Druid 参数化后模板无 `?` 也无 `$N` |
| **L2** | `BIND_CACHE` | 200 | 精确匹配（`B\|akDbTypeId\|normSql`） | SQL 含 bindMetadata，走 `SQLUtils.format()` |
| **L3** | `CACHE` (LRU) | 1000 | 前缀匹配 + compareExtract | SQL 含 `?` 参数，走 `Druid parameterize()` |

### 2.1 L1 无参缓存

当 Druid 参数化后的模板中既没有 `?` 占位符也没有 `$N` 绑定参数时，说明原始 SQL 不含可提取的参数值。此类 SQL 的结果完全由输入 SQL 决定，无需 compareExtract，用归一化后的完整 SQL 做精确匹配，直接缓存最终结果。

```
示例:
  输入: "SELECT service_id, dr_type FROM dr_ggprs u, dr_db d WHERE d.id=u.service_id"
  Druid 输出: "SELECT service_id, dr_type\nFROM dr_ggprs u, dr_db d\nWHERE d.id = u.service_id"
  → 模板无 ? 占位符 → 写入 L1
  → 缓存 Key:   "akDbTypeId|SELECT service_id, dr_type FROM dr_ggprs u, dr_db d WHERE d.id=u.service_id"
  → 缓存 Value: {Success, md5, "SELECT service_id, dr_type\nFROM dr_ggprs u, dr_db d\nWHERE d.id = u.service_id", ""}
  → 第二次相同 SQL → L1 精确命中 → 直接返回
```

### 2.2 L2 bind 缓存

输入 SQL 以 `;(` 分隔符携带绑定元数据时，Druid 不做参数化（parameterize），而是调用 `SQLUtils.format()` 做格式化输出（保留 `$1/$2` 而非替换为 `?`）。format 输出仅由 SQL 主体决定，可将结果按 SQL 主体缓存，每次调用时拼上当前 bindMetadata。

```
示例:
  输入: "UPDATE t SET col=$1 WHERE id=$2;($1='value1', $2='123')"
                    ├─ sqlForParsing ────────────────┤├─ bindMetadata ─────────┤

  拆分后:
    sqlForParsing = "UPDATE t SET col=$1 WHERE id=$2"
    bindMetadata   = "($1='value1', $2='123')"

  → SQLUtils.format(sqlForParsing) 输出格式化模板（保留 $1/$2）
  → 缓存 Key:   "B|akDbTypeId|UPDATE t SET col=$1 WHERE id=$2"
  → 缓存 Value: {Success, md5, "UPDATE t SET col = $1 WHERE id = $2"}
  → 下次相同 sqlForParsing + 不同 bindMetadata → L2 命中 → 拼接当前 bindMetadata 返回
```

### 2.3 L3 主缓存

有参数 SQL（含 `?`）走 Druid `parameterize()`，由于模板与原始 SQL 之间存在类型转换重写差异（`int4(expr)` → `expr::int4`），无法做精确匹配。采用前缀匹配 + `compareExtract` 逐字符对照提取参数。

```
示例:
  输入: "INSERT INTO ud.dr_ggprs AS a (id, name) VALUES (int4(oracle.to_number('53001'::text)), 'test')"

  generateCacheKey 流程:
    1. 去掉 AS → "INSERT INTO ud.dr_ggprs (id, name) VALUES ..."
    2. 截取前缀 → "INSERT INTO ud.dr_ggprs (id, name) VALUES"
    3. 结构签名 → "A0O0I0J0Q0D0G0H0L0V1S0"

  → 缓存 Key: "akDbTypeId|INSERT INTO ud.dr_ggprs (id, name) VALUES|A0O0I0J0Q0D0G0H0L0V1S0"

  Druid parameterize → 模板: "INSERT INTO ud.dr_ggprs (id, name) VALUES (?, ?)"
  → 缓存 Value: CachedResult{template, md5, placeholderCount=2, compareSuffix="?, ?)"}

  下次输入: "INSERT INTO ud.dr_ggprs AS a (id, name) VALUES (123, 'hello')"
    → 去 AS → 相同前缀+签名 → L3 命中
    → buildResult: 截取后缀，compareExtract("123, 'hello')", "?, ?)") 提取参数 [123, hello]
    → 返回 {Success, md5, "INSERT INTO ud.dr_ggprs (id, name) VALUES (?, ?)", "(123,hello)"}
```

---

## 3. 完整执行流程

```
getSqlTemplate_v2(sql, akDbTypeId)
  │
  ├─ [1] 分离参数绑定
  │     查找最后一个 ";(" 分隔符
  │     sqlForParsing = 分隔符之前
  │     bindMetadata  = 分隔符之后（如 "($1='val1',$2='val2')"）
  │
  ├─ [2] basicNormalize(sqlForParsing) → normSql（后续各环节复用，避免重复归一化）
  │
  ├─ [3] L1 无参缓存查询（仅 bindMetadata==null 时）
  │     noParamGet(akDbTypeId, normSql)
  │     命中 → noParamHitCount++ → 直接返回
  │
  ├─ [4] L2 bind 缓存查询（仅 bindMetadata!=null 时）
  │     bindGet(akDbTypeId, normSql)
  │     命中 → bindHitCount++ → 拼接当前 bindMetadata 返回
  │
  ├─ [5] 判断是否走 L3 缓存
  │     shouldCache = (bindMetadata==null) && isCacheableSqlType(sqlForParsing)
  │     isCacheableSqlType: INSERT/SELECT/UPDATE/BEGIN;INSERT 且长度 ≥ 200
  │
  ├─ [6] L3 主缓存查询（shouldCache==true 时）
  │     cacheKey = generateCacheKey(akDbTypeId, sqlForParsing)
  │       → 先 normalizeTableAliasAS 去掉表别名 AS
  │       → 再 truncateSqlBeforeValues 截取前缀
  │       → 再 keywordSignature 计算结构签名
  │       → 拼接: akDbTypeId + "|" + 前缀 + "|" + 签名
  │
  │     CACHE.get(cacheKey) → Set<CachedResult>
  │     命中 → hitCount++ → buildResult() → [7]
  │     未命中 → missCount++ → parseAndCache() → [8]
  │
  └─ [不缓存] shouldCache==false → skipCount++ → parseAndCache() → [8]


  [7] buildResult(resultSet, originalSql, akDbTypeId, cacheKey, normSql)
    │
    ├─ 对原始 SQL 后缀去掉 AS
    │   operationType = getOperationType(originalSql)
    │   compareOrig = normalizeTableAliasAS(operationType, 后缀部分)
    │
    ├─ 遍历 CachedResult 集合
    │   compareExtract(compareOrig, cached.compareSuffix)
    │     ├─ 匹配成功 且 参数数量 == placeholderCount
    │     │   → ParameterValuesFormatter.sqlBind(params) → 返回结果
    │     └─ 匹配失败 → 继续下一个候选
    │
    └─ 全部候选失败 → parseAndCache() 解析新模板并写入 L3


  [8] parseAndCache(sqlForParsing, bindMetadata, akDbTypeId, cacheKey, shouldCache, normSql)
    │
    ├─ Druid 解析
    │   bindMetadata!=null → SQLUtils.format()（保留 $1/$2）
    │   bindMetadata==null → ParameterizedOutputVisitorUtils.parameterize()（? 替换参数）
    │
    ├─ 分流写入缓存
    │   ├─ bindMetadata!=null              → 写入 L2 BIND_CACHE
    │   ├─ 模板无 ? 且无 $                 → 写入 L1 NO_PARAM_CACHE
    │   └─ shouldCache && cacheKey!=null   → 写入 L3 CACHE（CachedResult）
    │
    └─ 返回 [status, md5, template, bindResult]
```

---

## 4. 核心算法：compareExtract 动态类型转换对照提取

### 4.1 问题根源

Druid `parameterize()` 对 PostgreSQL SQL 做了三种重写，导致模板与原始 SQL 结构不一致：

| 重写类型 | 原始 SQL | 模板（Druid 输出） |
|---------|---------|-------------------|
| 类型函数转换 | `int4(oracle.to_number('0'::text))` | `oracle.to_number('0'::text)::int4` |
| 分组括号简化 | `((expr))` | `(expr)` |
| 大小写差异 | `without time zone` | `WITHOUT TIME ZONE` |

### 4.2 解决方案

- **类型函数转换**：通过 castStack 动态压栈/出栈处理
- **分组括号**：检测多余 `(` `)` 并跳过
- **大小写差异**：`charEquals()` 使用 `Character.toLowerCase()` 不敏感比较
- **表别名 AS**：由 `normalizeTableAliasAS()` 在缓存 key 生成和 buildResult 比对前统一移除

### 4.3 compareExtract 伪代码

```
function compareExtract(original, template) → List<Object> 或 null:
    params = []
    ti = 0, oi = 0                          // ti=模板游标, oi=原始SQL游标
    castStack = []                           // 类型转换函数名栈

    while ti < len(template) and oi < len(original):
        tc = template[ti]
        oc = original[oi]

        // [1] 模板占位符 → 从原始SQL提取参数
        if tc == '?':
            endPos = extractParam(original, oi)
            if endPos == null: return null
            value = original[oi : endPos]
            if value 是引号字符串: value = 去掉外层引号
            if value == "NULL" (不区分大小写): params.add(null)
            else: params.add(value)
            oi = endPos, ti++
            continue

        // [2] 字符匹配（大小写不敏感）
        if toLower(tc) == toLower(oc):
            ti++, oi++
            continue

        // [3] 空白灵活跳过
        if isWhitespace(tc): ti++; continue
        if isWhitespace(oc): oi++; continue

        // ---- 以下为不匹配处理 ----

        // [4] 类型函数入栈: original 中 "word(" → Druid 转成 "::word"
        if isLetter(oc):
            word = readWord(original, oi)    // 读标识符（如 int4）
            if original[oi + len(word)] == '(':
                castStack.push(word.toLowerCase())
                oi = oi + len(word) + 1      // 跳过 "word("
                continue

        // [5] 多余分组括号: original 有多余 ( 或 ) → Druid 简化去掉的
        if oc == '(' and tc != '(':
            if toLower(original[oi+1]) == toLower(tc):
                oi++; continue                // 跳过多余 '('
        if oc == ')' and tc != ')':
            if toLower(original[oi+1]) == toLower(tc):
                oi++; continue                // 跳过多余 ')'

        // [6] 类型转换出栈: 模板 "::typeName" 且栈顶匹配
        if tc == ':' and template[ti+1] == ':':
            typeName = readWord(template, ti+2)
            if typeName != null and castStack 不为空:
                if castStack.top == typeName.toLowerCase():
                    castStack.pop()
                    ti = ti + 2 + len(typeName)
                    if original[oi] == ')': oi++  // 跳过对应的 ')'
                    continue

        // 以上都不匹配 → 比对失败
        return null

    // 结束检查
    if castStack 不为空: return null
    if 模板剩余非空白字符: return null
    if 原始SQL剩余非空白字符: return null

    return params
```

### 4.4 匹配场景详解

#### 场景 1：类型函数转换

```
原始: int4(oracle.to_number('53001'::text))
模板: oracle.to_number(?, ?)::int4

逐字符对照:
  original        template        动作
  ──────────────  ──────────────  ──────────────
  i               o               不匹配，读词 "int4"，下一个字符是 '('
                                  castStack.push("int4")，跳过 "int4("
  o               o               匹配
  r               r               匹配
  ...             ...             继续匹配 oracle.to_number(
  '               ?               提取参数 '53001'::text → 53001
  ,               ,               匹配
  '               ?               提取参数 'YYYYMMDD...' → YYYYMMDD...
  :               )               不匹配
  :               ::              模板是 "::int4"，读类型名 "int4"
  i                   栈顶 "int4" == "int4" → 出栈
  n                   ti 跳过 "::int4"
  t4              )               oi 跳过 ")"
                  (结束)           castStack 为空 ✓
```

#### 场景 2：多余分组括号

```
原始: ((oracle.ora_sys_now())::oracle.date)::timestamp
模板: (oracle.ora_sys_now())::oracle.date::timestamp

对照时:
  oc='('  tc='('  匹配 ✓
  oc='('  tc='o'  不匹配，检查 original[oi+1]='o' == tc='o' → 跳过多余 '('
  oc='o'  tc='o'  匹配 ✓
  ...继续正常匹配
```

#### 场景 3：大小写差异

```
原始: without time zone
模板: WITHOUT TIME ZONE

charEquals() 使用 Character.toLowerCase() 比较，大小写不敏感
```

#### 场景 4：参数值引号处理

```
原始: '53001'
模板: ?

extractParam() 提取到 '53001'（含引号），返回 endPos
compareExtract() 中去掉外层引号 → 53001
与 Druid parameterize() 输出的参数值格式一致
```

### 4.5 涉及的类型函数

生产 SQL 中出现的类型转换函数：

| 函数 | 对应模板 | castStack 操作 |
|------|---------|---------------|
| `int4(expr)` | `expr::int4` | push "int4" / pop on "::int4" |
| `int2(expr)` | `expr::int2` | push "int2" / pop on "::int2" |
| `int8(expr)` | `expr::int8` | push "int8" / pop on "::int8" |
| `float8(expr)` | `expr::float8` | push "float8" / pop on "::float8" |

算法不硬编码函数名，任何 `word(` 形式都会入栈，在模板中遇到 `::word` 时匹配出栈。

---

## 5. 表别名 AS 标准化

### 5.1 问题

PostgreSQL 允许两种表别名写法：

```
写法 A: INSERT INTO ud.dr_ggprs_20260424 AS a (...) VALUES (...)
写法 B: INSERT INTO ud.dr_ggprs_20260424 a (...) VALUES (...)
```

Druid 解析后两种写法可能输出不同的模板，导致：
- `写法 A` 和 `写法 B` 生成不同的 cacheKey，无法命中同一缓存
- `compareExtract` 比对时 `AS` 关键字导致字符对不上

### 5.2 解决方案

`normalizeTableAliasAS()` 使用预编译正则表达式，在以下两个位置统一移除表别名 AS：

1. **generateCacheKey**：生成 cacheKey 前先去 AS，确保两种写法产生相同的 key
2. **buildResult**：比对前对原始 SQL 和缓存模板都去 AS，确保字符能对齐

仅处理三种场景（不影响列别名和 CTE）：

| 场景 | 正则 | 转换 |
|------|------|------|
| INSERT INTO t AS alias | `INSERT\s+INTO\s+[\w.]+\s+AS\s+(\w+)` | `INSERT INTO t alias` |
| FROM/JOIN t AS alias | `(?:FROM\|JOIN\|...)\s+[\w.]+\s+AS\s+(\w+)` | `FROM t alias` |
| UPDATE t AS alias SET | `UPDATE\s+[\w.]+\s+AS\s+(\w+)\s+SET` | `UPDATE t alias SET` |

### 5.3 伪代码

```
function normalizeTableAliasAS(operationType, sql) → String:
    if sql 不含 " AS " 和 " as ": return sql    // 快速跳过

    switch operationType:
        case "INSERT":
            return INSERT_AS_PATTERN.matcher(sql).replaceAll("$1 $2")
        case "SELECT":
            return SELECT_AS_PATTERN.matcher(sql).replaceAll("$1 $2")
        case "UPDATE":
            return UPDATE_AS_PATTERN.matcher(sql).replaceAll("$1 $2$3")
        default:
            return sql
```

---

## 6. CachedResult 预计算

`CachedResult` 在构造时预计算多个字段，以空间换时间，避免 `buildResult` 每次重复计算：

```
CachedResult {
    // 原始数据（来自 Druid 解析）
    String status              // "Success" / "Failure" / "NonSupport"
    String md5                 // 模板的 MD5 摘要
    String template            // Druid parameterize 输出
    Integer placeholderCount   // ? 占位符个数

    // 预计算字段（构造时一次性计算）
    String normFull            // basicNormalize(template)
    int    normSuffixStart     // findSuffixStart(normFull)
    String normSuffix          // normFull.substring(normSuffixStart)
    String compareSuffix       // normalizeTableAliasAS(opType, normSuffix)  ← buildResult 直接用
    String compareFull         // normalizeTableAliasAS(opType, normFull)    ← 无后缀的 SELECT 用
}
```

**预计算收益**：同一 CachedResult 可能被多次 `buildResult` 使用，预计算避免每次重复执行 `basicNormalize` + `findSuffixStart` + `normalizeTableAliasAS`。

---

## 7. 辅助方法

### 7.1 basicNormalize

```java
// 将换行/制表符 → 空格，合并连续空格为单个空格
// 保留原始大小写（compareExtract 内部做大小写不敏感比较）
static String basicNormalize(String s)
```

### 7.2 extractParam

从原始 SQL 的 `start` 位置提取一个参数值，返回 `[endPos]`：

| 参数类型 | 识别规则 | 示例 |
|---------|---------|------|
| 字符串 | `'...'` 或 `"..."`，支持连续引号转义 | `'53001'` → endPos 跳过引号 |
| NULL | `NULL`（4 字符，不区分大小写） | `NULL::text` → endPos +4 |
| 数字 | 数字开头或 `-` + 数字，含小数点 | `3.14`, `-5` |
| 单词 | 字母开头连续字母 | `true`, `false` |

### 7.3 keywordSignature

生成 SQL 结构指纹，统计关键关键字出现次数：

```
签名格式: A{AND}O{OR}I{IN}J{JOIN}Q{?}D{$}G{GROUP BY}H{HAVING}L{LIMIT}V{VALUES}S{SELECT}
示例:     "A2O0I0J0Q5D0G0H0L0V1S1"  → 2个AND, 5个?, 1个VALUES, 1个SELECT
```

使用空格分隔的关键字模式（如 `" AND "`）避免与标识符/值中的同名文本混淆。

### 7.4 truncateSqlBeforeValues

截取 SQL 前缀，用于生成 L3 cacheKey：

| SQL 类型 | 截取规则 | 示例 |
|---------|---------|------|
| INSERT | 截到 `VALUES` 后（+6） | `INSERT INTO t (c1,c2) VALUES` |
| SELECT | 截到 ` WHERE ` 后（+7） | `SELECT c1 FROM t WHERE ` |
| UPDATE | 截到 ` SET ` 后（+5） | `UPDATE t SET ` |
| DELETE | 截到 ` WHERE ` 后（+7） | `DELETE FROM t WHERE ` |

---

## 8. 线程安全

| 组件 | 线程安全方式 |
|------|------------|
| `CACHE` (L3) | `Collections.synchronizedMap` + `parseAndCache` 中 `synchronized (CACHE)` 保护写入 |
| `NO_PARAM_CACHE` (L1) | `Collections.synchronizedMap` |
| `BIND_CACHE` (L2) | `Collections.synchronizedMap` |
| `hitCount/missCount/skipCount` | `LongAdder`（无锁 CAS，减少多线程缓存行颠簸） |

---

## 9. 性能测试结果

### 9.1 v4 JMH 基准测试（2026-05-20，172.19.4.41/JDK17，100条生产SQL）

**单条 SQL 测试**：

| 场景 | 基准 (ns/op) | 优化 (ns/op) | 耗时变化 | 基准吞吐量 | 优化吞吐量 | 吞吐量提升 |
|------|-------------|-------------|----------|-----------|-----------|-----------|
| single_insert | 249,397 | 222,652 | -10.7% | 4,010 ops/s | 4,491 ops/s | **+12.0%** |
| single_select | 17,586 | 15,782 | -10.3% | 56,865 ops/s | 63,366 ops/s | **+11.4%** |
| single_begin | 227,156 | 217,906 | -4.1% | 4,402 ops/s | 4,589 ops/s | **+4.2%** |
| single_update | 189,009 | 181,809 | -3.8% | 5,291 ops/s | 5,500 ops/s | **+4.0%** |

**批量场景测试**：

| 场景 | SQL 数量 | 基准 (ns/op) | 优化 (ns/op) | 耗时变化 | 吞吐量提升 |
|------|---------|-------------|-------------|----------|-----------|
| begin_only | 23 条 | 3,949,872 | 3,269,136 | -17.2% | **+20.9%** |
| insert_only | 29 条 | 8,749,555 | 7,014,355 | -19.8% | **+25.4%** |
| select_only | 24 条 | 2,596,606 | 2,344,825 | -9.7% | **+10.9%** |
| update_only | 24 条 | 1,076,659 | 1,014,797 | -5.7% | **+6.4%** |

**混合场景测试**：

| 场景 | 基准 (ns/op) | 优化 (ns/op) | 耗时变化 | 基准吞吐量 | 优化吞吐量 | 吞吐量提升 |
|------|-------------|-------------|----------|-----------|-----------|-----------|
| mixed_100 | 15,012,672 | 13,408,575 | -10.7% | 66.6 ops/s | 74.6 ops/s | **+12.0%** |

**关键结论**：所有场景均为正向优化，无性能退步。单条 SQL 吞吐量提升 4%~12%，批量场景提升 6%~25%，混合 100 条提升 12%。v4 修复了 v3 中 BEGIN/UPDATE 的退步问题。

---

## 10. 对外接口

```java
// 主入口
public static String[] getSqlTemplate_v2(String sql, Integer akDbTypeId)
// 返回 String[4]: [status, md5, template, bindResult]

// 监控 — 返回三级缓存命中率、命中数、缓存大小
public static String getCacheStats()
// 示例输出: "命中率: 100.0% (hit=1200 miss=0 skip=50), 缓存大小: 45/1000, 无参缓存: 12/500 (hit=100), bind缓存: 3/200 (hit=50)"

// 清空三级缓存及所有计数器
public static void clearCache()
```

---

## 11. 相关文件

| 文件 | 路径 | 说明 |
|------|------|------|
| 缓存实现 | `com/ankki/druid/parser/template/LightweightCachedAkOutputVisitorUtils.java` | v4 核心类 |
| 基准入口 | `com/ankki/druid/parser/CustomerOutputVisitorUtils.java` | 应用层入口 |
| 无缓存实现 | `com/ankki/druid/parser/template/AkOutputVisitorUtils.java` | Baseline 对照 |
| 参数绑定 | `com/ankki/druid/parser/bind/ParameterValuesFormatter.java` | 格式化参数值 |
| 测试类 | `com/ankki/druid/parser/template/TableAliasASTest.java` | AS 标准化正确性验证 |
| 测试报告 | `性能测试结果/v4综合性能测试报告.md` | v4 JMH 测试报告 |
