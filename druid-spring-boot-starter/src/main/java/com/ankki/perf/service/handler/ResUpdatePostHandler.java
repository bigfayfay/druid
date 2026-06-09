package com.ankki.perf.service.handler;

import com.ankki.perf.config.PerfTestConfig;
import com.ankki.perf.entity.db.SqlTemplateRes;
import com.ankki.perf.mapper.SqlTemplateResMapper;
import com.ankki.perf.service.PostHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author fay
 * @date 2026-06-09
 * @description
 *
 * @ConditionalOnExpression("'${perf.data-post-handler:res-insert,res-update}'.split(',').contains('res-insert')")
 *
 * 在 SpEL 中，String.split(',') 返回的是 String[]（Java 数组），而数组没有 contains() 方法。contains() 是 Collection（如 List）才有的方法。
 * 需要将 split() 返回的数组通过 T(java.util.Arrays).asList() 转换为 List，然后再调用 contains()：
 * @ConditionalOnExpression("T(java.util.Arrays).asList('${perf.data-post-handler:res-insert,res-update}'.split(',')).contains('res-update')")
 *
 * 问题找到了。SpEL 中将 String[]（split() 的返回值）传给 Arrays.asList() 时，SpEL 不会正确展开 varargs，而是将整个数组当作一个单一元素放入 List，导致 .contains('res-update') 永远返回 false。
 * 修复方案： 使用「逗号包裹匹配」技巧，直接在 String.contains() 上做精确 token 匹配，避免数组转 List 的问题
 * @ConditionalOnExpression("',${perf.data-post-handler:res-update},'.contains(',res-update,')")
 */
@Slf4j
@Service
@ConditionalOnExpression("',${perf.data-post-handler:res-update},'.contains(',res-update,')")
public class ResUpdatePostHandler implements PostHandler {

    @Resource
    private SqlSessionFactory sqlSessionFactory;
    @Resource
    private PerfTestConfig config;

    private List<SqlTemplateRes> buffer;
    private final ReentrantLock lock = new ReentrantLock();

    @PostConstruct
    private void init() {
        this.buffer = new ArrayList<>(config.getFlushThreshold());
    }


    @Override
    public void addRecord(SqlTemplateRes record) {
        List<SqlTemplateRes> toFlush = null;
        lock.lock();
        try {
            // 特殊字符，引起的解析失败，再次刷新
            record.setRemark(record.getStatus());
            buffer.add(record);
            if (buffer.size() >= config.getFlushThreshold()) {
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
                mapper.updateRemark(record);
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

    @Override
    public void close() {

    }
}
