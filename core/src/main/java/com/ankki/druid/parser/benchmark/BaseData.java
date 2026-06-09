package com.ankki.druid.parser.benchmark;

public class BaseData {
    /** 总调用次数 */
    public static final int TOTAL_INVOCATIONS = 500_000;
    /** 测量迭代次数 */
    public static final int MEASUREMENT_ITERATIONS = 5;
    /** 预热迭代次数 */
    public static final int WARMUP_ITERATIONS = 3;
    /** 预热批次大小 */
    public static final int WARMUP_BATCH = 100_000;

    public static final int[] THREAD_COUNTS = {1, 4, 10};
    public static final String[] HEAP_SIZES = {"128m", "1g", "4g"};
}
