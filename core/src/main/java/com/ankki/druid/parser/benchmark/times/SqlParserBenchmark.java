package com.ankki.druid.parser.benchmark.times;

import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.visitor.ParameterizedOutputVisitorUtils;
import com.ankki.druid.parser.AkDbTypeEnum;
import com.ankki.druid.parser.CustomerOutputVisitorUtils;
import com.ankki.druid.parser.benchmark.BaseData;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.ArrayList;
import java.util.List;
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
@State(Scope.Benchmark)  // 所有线程共享实例
@Warmup(iterations = 3, time = 5, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 60, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1, jvmArgs = {"-Xmx1g", "-XX:+UseG1GC"})
@Threads(Threads.MAX) // 默认使用所有可用核心数，可通过命令行 -t 覆盖
public class SqlParserBenchmark {

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
        CustomerOutputVisitorUtils.getSqlTemplate_v2(sql, akDbTypeId);
    }

    @Benchmark
    public void benchmarkGetSqlTemplate_v2() {
        CustomerOutputVisitorUtils.getSqlTemplate_v2(sql, akDbTypeId);
    }


    @Benchmark
    public void benchmarkGetSqlTemplate_office() {
        ParameterizedOutputVisitorUtils.parameterize(sql, dbType);
    }


    @Benchmark
    public void benchmarkGetSqlTemplate_office_params() {
        List<Object> objects = new ArrayList<>();
        ParameterizedOutputVisitorUtils.parameterize(sql, dbType, objects);
    }

    /**
     * 在服务器上直接运行 main 方法启动 JMH 测试
     * <p>
     * 命令行参数示例：
     * <pre>
     *   -t 4          # 4线程（覆盖默认矩阵）
     *   -f 1          # 1个fork
     *   -wi 2 -i 3    # 2次预热, 3次测量
     *   -heap 128m,1g,4g  # 自定义堆大小矩阵
     * </pre>
     */
    public static void main(String[] args) throws RunnerException {
        // 解析命令行参数
        int warmupIterations = BaseData.WARMUP_ITERATIONS;
        int measurementIterations = BaseData.MEASUREMENT_ITERATIONS;

        // 确定测试矩阵
        int[] threadCounts = BaseData.THREAD_COUNTS;
        String[] heapSizes = BaseData.HEAP_SIZES;
        
        String resultDir = "/data/logs/druid";
        long ts = System.currentTimeMillis();

        System.out.println("=== SqlParserBenchmark 测试矩阵 ===");
        System.out.printf("  线程数: %s%n", java.util.Arrays.toString(threadCounts));
        System.out.printf("  堆大小: %s%n", java.util.Arrays.toString(heapSizes));
        System.out.printf("  组合总数: %d%n", threadCounts.length * heapSizes.length);
        System.out.printf("  预热/测量: %d/%d 轮%n", warmupIterations, measurementIterations);
        System.out.println();

        int runIndex = 0;
        for (int threads : threadCounts) {
            for (String heap : heapSizes) {
                runIndex++;

                String label = String.format("t%d_heap%s", threads, heap);
                String resultFile = String.format("%s/jmh_%s_%d.json", resultDir, label, ts);

                System.out.printf("[%d/%d] 启动: threads=%d, heap=%s%n", 
                        runIndex, threadCounts.length * heapSizes.length, threads, heap);

                Options opt = new OptionsBuilder()
                        .include(SqlParserBenchmark.class.getSimpleName())
                        .threads(threads)
                        .forks(1)
                        .jvmArgs("-Xmx" + heap, "-XX:+UseG1GC")
                        .resultFormat(ResultFormatType.JSON)
                        .result(resultFile)
                        .build();

                new Runner(opt).run();
                System.out.printf("[%d/%d] 完成: %s → %s%n%n", runIndex,
                        threadCounts.length * heapSizes.length, label, resultFile);
            }
        }

        System.out.println("=== 全部测试完成 ===");
    }

}
