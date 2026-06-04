package com.ankki.perf.service;

import com.ankki.perf.entity.SqlTemplateRes;
import com.ankki.perf.mapper.SqlTemplateResMapper;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * SqlTemplateRes 批量写入服务
 * <p>
 * 线程安全：内部使用缓冲区累积记录，达到阈值后批量执行 INSERT ON DUPLICATE KEY UPDATE。
 * 多个消费者线程可并发调用 {@link #addRecord(SqlTemplateRes)}。
 */
@Service
public class SqlTemplateResService {

    private static final Logger log = LoggerFactory.getLogger(SqlTemplateResService.class);

    /** 默认批量刷新阈值 */
    private static final int DEFAULT_FLUSH_THRESHOLD = 180;

    @Resource
    private SqlTemplateResMapper sqlTemplateResMapper;

    @Resource
    private SqlSessionFactory sqlSessionFactory;

    private final int flushThreshold;
    private final List<SqlTemplateRes> buffer;
    private final ReentrantLock lock = new ReentrantLock();

    public SqlTemplateResService() {
        this(DEFAULT_FLUSH_THRESHOLD);
    }

    public SqlTemplateResService(int flushThreshold) {
        this.flushThreshold = flushThreshold;
        this.buffer = new ArrayList<>(flushThreshold + 16);
    }

    /**
     * 添加一条记录到缓冲区，达到阈值自动批量刷新
     */
    public void addRecord(SqlTemplateRes record) {
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
