Deep thinking
```The user is asking about SQL bind variables - their concept, meaning, syntax/usage, standard writing and differences, and how to simulate them through tools like DBeaver.

Let me first check if there are relevant memories about SQL binding that might help me provide context-specific information.
```
**Searched memory**

**Searched memory**


## SQL 绑定变量（Bind Variables）介绍

### 一、概念与含义

**SQL 绑定变量**（Bind Variable / Parameterized Query）是指在 SQL 语句中使用**占位符**代替具体的字面值，在执行时再将实际值"绑定"到占位符上。

**核心目的：**
1. **性能优化**：数据库可以复用 SQL 的执行计划（硬解析 → 软解析），避免每次都重新编译
2. **安全性**：防止 SQL 注入攻击
3. **代码可维护性**：SQL 与数据分离

**举例：**
```sql
-- 不使用绑定变量（字面量硬编码）
SELECT * FROM users WHERE id = 123 AND name = '张三';

-- 使用绑定变量
SELECT * FROM users WHERE id = ? AND name = ?;
-- 绑定: [123, '张三']
```


---

### 二、不同数据库的语法差异

| 数据库 | 占位符语法 | 示例 |
|--------|-----------|------|
| **JDBC/MySQL/通用** | `?`（位置参数） | `WHERE id = ?` |
| **PostgreSQL** | `$1, $2, $3...`（序号参数） | `WHERE id = $1 AND name = $2` |
| **Oracle** | `:name`（命名参数） | `WHERE id = :userId` |
| **SQL Server** | `@name`（命名参数） | `WHERE id = @userId` |
| **SQLite** | `?` 或 `?1, ?2` 或 `:name` | `WHERE id = ?1` |

---

### 三、标准写法（Java JDBC 为例）

```java
// PreparedStatement 使用 ? 占位符
String sql = "SELECT * FROM users WHERE id = ? AND status = ?";
PreparedStatement ps = conn.prepareStatement(sql);
ps.setInt(1, 123);        // 绑定第1个参数
ps.setString(2, "active"); // 绑定第2个参数
ResultSet rs = ps.executeQuery();
```


**PostgreSQL 原生协议格式：**
```sql
PREPARE my_query AS SELECT * FROM users WHERE id = $1 AND name = $2;
EXECUTE my_query(123, '张三');
```


---

### 四、与你项目中 Druid 的关系

在你的项目中，Druid 的 `ParameterizedOutputVisitorUtils` 可以将带字面量的 SQL **参数化**（即提取绑定变量）：

```java
// 原始 SQL
"SELECT * FROM t WHERE id = 123 AND name = '张三'"

// 参数化后（sqlBind）
"SELECT * FROM t WHERE id = ? AND name = ?"
// 参数列表: [123, "张三"]
```


而你实现的 `sqlUnbind` 则是**反向操作**：将 `($1='137813', $2='/path', $3=123)` 这样的绑定参数字符串解析回 `List<Object>`，再回填到 SQL 中生成完整可执行的 SQL。

---

### 五、如何在 DBeaver 中模拟绑定变量

DBeaver 本身不直接暴露"绑定变量"的底层协议，但可以通过以下方式模拟：

#### 方法 1：使用 DBeaver 的参数化查询功能

在 SQL 编辑器中直接写带 `:参数名` 或 `?` 的 SQL：

```sql
SELECT * FROM users WHERE id = :userId AND status = :status
```


执行时 DBeaver 会**弹出对话框**让你输入参数值。

> 注意：需要在 **窗口 → 首选项 → 数据库 → SQL编辑器** 中确认开启了"绑定变量"支持。

#### 方法 2：使用 PREPARE / EXECUTE（PostgreSQL）

```sql
PREPARE test_stmt(int, text) AS 
  SELECT * FROM users WHERE id = $1 AND name = $2;

EXECUTE test_stmt(123, '张三');

DEALLOCATE test_stmt;
```


#### 方法 3：使用匿名块（Oracle）

```sql
DECLARE
  v_id NUMBER := 123;
  v_name VARCHAR2(50) := '张三';
BEGIN
  FOR r IN (SELECT * FROM users WHERE id = v_id AND name = v_name) LOOP
    DBMS_OUTPUT.PUT_LINE(r.name);
  END LOOP;
END;
```


#### 方法 4：MySQL 的 PREPARE 语法

```sql
SET @id = 123;
SET @name = '张三';
PREPARE stmt FROM 'SELECT * FROM users WHERE id = ? AND name = ?';
EXECUTE stmt USING @id, @name;
DEALLOCATE PREPARE stmt;
```


---

### 六、绑定变量 vs 字面量的对比

| 维度 | 绑定变量 | 字面量 |
|------|---------|--------|
| 执行计划复用 | 可复用（软解析） | 每次重新编译（硬解析） |
| SQL注入风险 | 无 | 有 |
| 数据库 SQL 缓存 | 命中率高 | 命中率低（每个值生成不同SQL） |
| 适用场景 | OLTP 高并发 | OLAP 分析、一次性查询 |

---

**总结**：绑定变量的本质就是"SQL 模板 + 参数列表"的分离，数据库层面实现执行计划复用，应用层面防止注入。你项目中 Druid 的参数化/反参数化功能正是围绕这个核心概念构建的。