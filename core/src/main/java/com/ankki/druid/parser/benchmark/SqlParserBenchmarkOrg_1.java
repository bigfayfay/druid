package com.ankki.druid.parser.benchmark;

import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.visitor.ParameterizedOutputVisitorUtils;
import com.ankki.druid.parser.AkDbTypeEnum;
import com.ankki.druid.parser.CustomerOutputVisitorUtils;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import org.openjdk.jmh.runner.options.TimeValue;

import java.util.concurrent.TimeUnit;

/**
 * JMH 基准测试 - Druid SQL 模板解析性能
 *
 * <p>测试矩阵（9种组合）：</p>
 * <ul>
 *   <li>线程数: 1, 4, 10</li>
 *   <li>堆大小: 128m, 1g, 4g</li>
 *   <li>模式: Throughput (ops/s) - 每秒操作数</li>
 * </ul>
 *
 * <p>使用方式：</p>
 * <pre>
 *   java -cp "druid-spring-boot-starter.jar:lib/*" com.ankki.druid.parser.benchmark.SqlParserBenchmark
 * </pre>
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 5, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 10, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1, jvmArgs = {"-XX:+UseG1GC"})
@Threads(1)
public class SqlParserBenchmarkOrg_1 {

    /** 预热迭代次数 */
    private static final int WARMUP_ITERATIONS = 3;
    /** 测量迭代次数 */
    private static final int MEASUREMENT_ITERATIONS = 5;
    /** 预热迭代时间（秒） */
    private static final int WARMUP_TIME = 5;
    /** 测量迭代时间（秒） */
    private static final int MEASUREMENT_TIME = 10;

    private static final int[] THREAD_COUNTS = {1, 4, 10};
    private static final String[] HEAP_SIZES = {"128m", "1g", "4g"};

    @Param({"mysql"})
    private String dbTypeStr;
    private DbType dbType;

    @Param({
            "SELECT * FROM `report_antibiotic_condition_statis` limit 5"
    })
    private String sql;

    private Integer akDbTypeId;

    @Setup(Level.Trial)
    public void setup() {
        AkDbTypeEnum akDbTypeEnum = AkDbTypeEnum.of(DbType.of(dbTypeStr));
        this.akDbTypeId = akDbTypeEnum.getTypeId();
        dbType = akDbTypeEnum.getDruidDbType();
        // 预热一次，确保类加载完成
        ParameterizedOutputVisitorUtils.parameterize(sql, dbType);
    }

    @Benchmark
    public void benchmarkGetSqlTemplate_v2() {
        ParameterizedOutputVisitorUtils.parameterize(sql, dbType);
    }

    /**
     * 运行全部 9 种组合（3线程数 × 3堆大小）
     *
     * <p>Throughput 模式下：JMH 在指定时间内尽可能多地执行，输出 ops/s（每秒操作数）</p>
     * <p>3轮预热(5秒) + 5轮测量(10秒)，总耗时约 65秒/组合</p>
     */
    public static void main(String[] args) throws RunnerException {
        String resultDir = "/data/logs/druid";
        long ts = System.currentTimeMillis();

        System.out.println("=== SqlParserBenchmark 测试矩阵 ===");
        System.out.printf("  线程数: %s%n", java.util.Arrays.toString(THREAD_COUNTS));
        System.out.printf("  堆大小: %s%n", java.util.Arrays.toString(HEAP_SIZES));
        System.out.printf("  组合总数: %d%n", THREAD_COUNTS.length * HEAP_SIZES.length);
        System.out.printf("  模式: Throughput (ops/s)%n");
        System.out.println();

        int runIndex = 0;
        for (int threads : THREAD_COUNTS) {
            for (String heap : HEAP_SIZES) {
                runIndex++;

                String label = String.format("t%d_heap%s", threads, heap);
                String resultFile = String.format("%s/jmh_%s_%d.json", resultDir, label, ts);

                System.out.printf("[%d/%d] 启动: threads=%d, heap=%s, warmup=%d×%ds, measure=%d×%ds%n",
                        runIndex, THREAD_COUNTS.length * HEAP_SIZES.length,
                        threads, heap, WARMUP_ITERATIONS, WARMUP_TIME, MEASUREMENT_ITERATIONS, MEASUREMENT_TIME);

                Options opt = new OptionsBuilder()
                        .include(SqlParserBenchmarkOrg_1.class.getSimpleName())
                        .mode(Mode.Throughput)
                        .threads(threads)
                        .forks(1)
                        .jvmArgs("-Xmx" + heap, "-XX:+UseG1GC")
                        .warmupIterations(WARMUP_ITERATIONS)
                        .warmupTime(TimeValue.seconds(WARMUP_TIME))
                        .measurementIterations(MEASUREMENT_ITERATIONS)
                        .measurementTime(TimeValue.seconds(MEASUREMENT_TIME))
                        .resultFormat(ResultFormatType.JSON)
                        .result(resultFile)
                        .build();

                new Runner(opt).run();
                System.out.printf("[%d/%d] 完成: %s → %s%n%n", runIndex,
                        THREAD_COUNTS.length * HEAP_SIZES.length, label, resultFile);
            }
        }

        System.out.println("=== 全部测试完成 ===");
    }
}
