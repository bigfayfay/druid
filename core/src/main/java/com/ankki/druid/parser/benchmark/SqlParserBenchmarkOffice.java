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

import java.util.concurrent.TimeUnit;

/**
 * JMH 基准测试 - Druid SQL 模板解析性能
 *
 * <p>测试矩阵（9种组合）：</p>
 * <ul>
 *   <li>线程数: 1, 4, 10</li>
 *   <li>堆大小: 128m, 1g, 4g</li>
 *   <li>每组合精确调用: 5,000,000 次（SingleShotTime 模式）</li>
 * </ul>
 *
 * <p>使用方式：</p>
 * <pre>
 *   java -cp "druid-spring-boot-starter.jar:lib/*" com.ankki.druid.parser.benchmark.SqlParserBenchmark
 * </pre>
 */
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, batchSize = 100_000)
@Measurement(iterations = 5, batchSize = 1_000_000)
@Fork(value = 1, jvmArgs = {"-XX:+UseG1GC"})
@Threads(1)
public class SqlParserBenchmarkOffice {

    /** 总调用次数 */
    private static final int TOTAL_INVOCATIONS = 5_000_000;
    /** 测量迭代次数 */
    private static final int MEASUREMENT_ITERATIONS = 5;
    /** 预热迭代次数 */
    private static final int WARMUP_ITERATIONS = 3;
    /** 预热批次大小 */
    private static final int WARMUP_BATCH = 100_000;

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
     * 运行全部 9 种组合（3线程数 × 3堆大小），每组合精确 5,000,000 次调用
     *
     * <p>SingleShotTime 模式下：总调用 = threads × iterations × batchSize</p>
     * <p>因此 batchSize = TOTAL_INVOCATIONS / (threads × iterations)</p>
     */
    public static void main(String[] args) throws RunnerException {
        String resultDir = "/data/logs/druid";
        long ts = System.currentTimeMillis();

        System.out.println("=== SqlParserBenchmark 测试矩阵 ===");
        System.out.printf("  总调用次数: %,d%n", TOTAL_INVOCATIONS);
        System.out.printf("  线程数: %s%n", java.util.Arrays.toString(THREAD_COUNTS));
        System.out.printf("  堆大小: %s%n", java.util.Arrays.toString(HEAP_SIZES));
        System.out.printf("  组合总数: %d%n", THREAD_COUNTS.length * HEAP_SIZES.length);
        System.out.println();

        int runIndex = 0;
        for (int threads : THREAD_COUNTS) {
            for (String heap : HEAP_SIZES) {
                runIndex++;
                // batchSize = totalOps / (threads × iterations)
                int batchSize = TOTAL_INVOCATIONS / (threads * MEASUREMENT_ITERATIONS);

                String label = String.format("t%d_heap%s", threads, heap);
                String resultFile = String.format("%s/jmh_%s_%d.json", resultDir, label, ts);

                System.out.printf("[%d/%d] 启动: threads=%d, heap=%s, batchSize=%,d, totalOps=%,d%n",
                        runIndex, THREAD_COUNTS.length * HEAP_SIZES.length,
                        threads, heap, batchSize,
                        (long) threads * MEASUREMENT_ITERATIONS * batchSize);

                Options opt = new OptionsBuilder()
                        .include(SqlParserBenchmarkOffice.class.getSimpleName())
                        .mode(Mode.SingleShotTime)
                        .threads(threads)
                        .forks(1)
                        .jvmArgs("-Xmx" + heap, "-XX:+UseG1GC")
                        .warmupIterations(WARMUP_ITERATIONS)
                        .warmupBatchSize(WARMUP_BATCH)
                        .measurementIterations(MEASUREMENT_ITERATIONS)
                        .measurementBatchSize(batchSize)
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
