package com.ankki.perf.benchmark;

import com.alibaba.druid.DbType;
import com.ankki.druid.parser.AkDbTypeEnum;
import com.ankki.druid.parser.CustomerOutputVisitorUtils;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;

/**
 * JMH 基准测试 - Druid SQL 模板解析性能
 *
 * <p>使用方式（服务器上执行）：</p>
 * <pre>
 *   # 方式1：直接通过 main 方法运行（打包后 java -cp 执行）
 *   java -cp "druid-spring-boot-starter.jar:lib/*" com.ankki.perf.benchmark.SqlParserBenchmark
 *
 *   # 方式2：使用 maven-shade-plugin 生成 benchmarks.jar
 *   java -jar benchmarks.jar
 *
 *   # 方式3：JVM 参数调优
 *   java -Xmx4g -XX:+UseG1GC -jar benchmarks.jar -t 8 -f 2
 * </pre>
 *
 * <p>关键参数说明：</p>
 * <ul>
 *   <li>-t N: 线程数（覆盖 @Threads 注解）</li>
 *   <li>-f N: fork 数（独立 JVM 进程数）</li>
 *   <li>-wi N: 预热迭代次数</li>
 *   <li>-i N: 测量迭代次数</li>
 *   <li>-p sql=xxx: 参数化 SQL（覆盖 @Param）</li>
 * </ul>
 */
@BenchmarkMode({Mode.Throughput, Mode.AverageTime})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 5, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 10, timeUnit = TimeUnit.SECONDS)
@Fork(value = 2, jvmArgs = {"-Xmx2g", "-XX:+UseG1GC"})
@Threads(Threads.MAX) // 默认使用所有可用核心数，可通过命令行 -t 覆盖
public class SqlParserBenchmark {

    @Param({"oracle", "mysql"})
    private String dbType;

    @Param({
            "SELECT * FROM users WHERE id = 1 AND name = 'test'",
            "INSERT INTO orders (id, user_id, amount, status) VALUES (1, 100, 99.99, 'PENDING')",
            "UPDATE accounts SET balance = balance - 100 WHERE account_id = 12345 AND status = 'ACTIVE'",
            "SELECT t1.id, t2.name FROM table1 t1 JOIN table2 t2 ON t1.id = t2.ref_id WHERE t1.status = 1 ORDER BY t1.create_time DESC"
    })
    private String sql;

    private Integer akDbTypeId;

    @Setup(Level.Trial)
    public void setup() {
        AkDbTypeEnum akDbTypeEnum = AkDbTypeEnum.of(DbType.of(dbType));
        this.akDbTypeId = akDbTypeEnum.getTypeId();
        // 预热一次，确保类加载完成
        CustomerOutputVisitorUtils.getSqlTemplate_v2(sql, akDbTypeId);
    }

    @Benchmark
    public void benchmarkGetSqlTemplate_v2(Blackhole bh) {
        String[] result = CustomerOutputVisitorUtils.getSqlTemplate_v2(sql, akDbTypeId);
        bh.consume(result);
    }

    /**
     * 在服务器上直接运行 main 方法启动 JMH 测试
     * <p>
     * 命令行参数示例：
     * <pre>
     *   -t 4          # 4线程
     *   -t 8          # 8线程
     *   -t max        # CPU核心数线程
     *   -f 1          # 1个fork（快速测试）
     *   -wi 2 -i 3    # 2次预热, 3次测量
     * </pre>
     */
    public static void main(String[] args) throws RunnerException {
        // 默认线程数：CPU核心数
        int defaultThreads = Runtime.getRuntime().availableProcessors();

        Options opt = new OptionsBuilder()
                .include(SqlParserBenchmark.class.getSimpleName())
                .threads(defaultThreads)
                .resultFormat(ResultFormatType.JSON)
                .result("/data/logs/druid/jmh_result_" + System.currentTimeMillis() + ".json")
                .build();

        new Runner(opt).run();
    }
}
