package com.ankki.perf.service.handler;

import com.ankki.druid.parser.AkDruidSqlParser;
import com.ankki.perf.entity.db.SqlTemplateRes;
import com.ankki.perf.mapper.SqlTemplateResMapper;
import com.ankki.perf.service.PostHandler;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import org.springframework.beans.factory.annotation.Value;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

/**
 * SqlTemplateRes 批量写入服务
 * <p>
 * 线程安全：内部使用缓冲区累积记录，达到阈值后批量执行 INSERT ON DUPLICATE KEY UPDATE。
 * 多个消费者线程可并发调用 {@link #addRecord(SqlTemplateRes)}。
 */
@Service
public class DataBasePostHandler implements PostHandler {

    private static final Logger log = LoggerFactory.getLogger(DataBasePostHandler.class);

    /** LRU 去重缓存容量：保留最近见过的 N 个 MD5，淘汰最冷的 */
    private static final int LRU_CAPACITY = 500_000;

    @Resource
    private SqlTemplateResMapper sqlTemplateResMapper;

    @Resource
    private SqlSessionFactory sqlSessionFactory;

    private final int flushThreshold;
    private final List<SqlTemplateRes> buffer;
    private final ReentrantLock lock = new ReentrantLock();

    /**
     * 基于 LRU 的 MD5 去重集合（固定容量，淘汰最久未见的）。
     * 使用 Collections.synchronizedMap 保证多线程安全。
     * 超出容量时自动淘汰最旧条目，内存占用恒定。
     */
    private final Set<String> seenMd5 = Collections.newSetFromMap(
            Collections.synchronizedMap(new LinkedHashMap<String, Boolean>(LRU_CAPACITY, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                    return size() > LRU_CAPACITY;
                }
            })
    );

    public DataBasePostHandler(@Value("${perf.flush-threshold:180}") int flushThreshold) {
        this.flushThreshold = flushThreshold;
        this.buffer = new ArrayList<>(flushThreshold);
    }

    /**
     * 添加一条记录到缓冲区，达到阈值自动批量刷新。
     * 如果 sqlMd5 已经出现过，则跳过该记录（去重）。
     */
    @Override
    public void addRecord(SqlTemplateRes record) {
        // MD5 去重：相同 SQL 只写入一次
        // 设置原始 SQL 的 MD5，用于后处理去重
        try {
            record.setSqlMd5(AkDruidSqlParser.generateMD5(record.getOperSentence()));
        } catch (Exception ignored) {
        }
        
        String md5 = record.getSqlMd5();
        if (md5 != null && !seenMd5.add(md5)) {
            return; // LRU 中已存在，跳过（重复 SQL）
        }

        List<SqlTemplateRes> toFlush = null;
        lock.lock();
        try {
            buffer.add(record);
            if (buffer.size() >= flushThreshold) {
                toFlush = new ArrayList<>(buffer);
                buffer.clear();
            }
        } finally {
            lock.unlock();
        }
        if (toFlush != null) {
            doFlush(toFlush);
        }
    }

    /**
     * 批量添加记录，达到阈值自动刷新
     */
    public void addRecords(List<SqlTemplateRes> records) {
        List<SqlTemplateRes> toFlush = null;
        lock.lock();
        try {
            buffer.addAll(records);
            if (buffer.size() >= flushThreshold) {
                toFlush = new ArrayList<>(buffer);
                buffer.clear();
            }
        } finally {
            lock.unlock();
        }
        if (toFlush != null) {
            doFlush(toFlush);
        }
    }

    /**
     * 强制刷新缓冲区中的剩余记录（任务结束时调用）
     */
    @Override
    public void flush() {
        List<SqlTemplateRes> toFlush = null;
        lock.lock();
        try {
            if (!buffer.isEmpty()) {
                toFlush = new ArrayList<>(buffer);
                buffer.clear();
            }
        } finally {
            lock.unlock();
        }
        if (toFlush != null) {
            doFlush(toFlush);
        }
    }

    /**
     * 批量执行 INSERT ... ON DUPLICATE KEY UPDATE（使用 MyBatis batch executor）
     */
    private void doFlush(List<SqlTemplateRes> records) {
        if (records.isEmpty()) {
            return;
        }

        long start = System.currentTimeMillis();
        try (SqlSession session = sqlSessionFactory.openSession(ExecutorType.BATCH, false)) {
            SqlTemplateResMapper mapper = session.getMapper(SqlTemplateResMapper.class);
            for (SqlTemplateRes record : records) {
                mapper.insertOrUpdate(record);
            }
            session.commit();
        } catch (Exception e) {
            log.error("Batch flush failed, size={}", records.size(), e);
        }
        long elapsed = System.currentTimeMillis() - start;
        if (log.isDebugEnabled()) {
            log.debug("Batch flushed {} records in {} ms", records.size(), elapsed);
        }
    }
}
