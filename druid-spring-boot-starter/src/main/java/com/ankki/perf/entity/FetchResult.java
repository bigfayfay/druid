package com.ankki.perf.entity;

import java.util.Collections;
import java.util.List;

/**
 * 批量拉取结果包装类
 * 由 DataFetcher 返回，明确标识是否还有更多数据
 *
 * @author fay
 */
public class FetchResult {

    /** 本批次拉取到的数据 */
    private final List<SqlTypeBO> records;

    /** 是否还有下一批数据（由 DataFetcher 判断） */
    private final boolean hasMore;

    public FetchResult(List<SqlTypeBO> records, boolean hasMore) {
        this.records = records != null ? records : Collections.emptyList();
        this.hasMore = hasMore;
    }

    public List<SqlTypeBO> getRecords() {
        return records;
    }

    public boolean hasMore() {
        return hasMore;
    }

    public boolean isEmpty() {
        return records.isEmpty();
    }

    public int size() {
        return records.size();
    }

    /** 快捷构造：无数据且无后续 */
    public static FetchResult empty() {
        return new FetchResult(Collections.emptyList(), false);
    }
}
