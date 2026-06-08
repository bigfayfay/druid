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

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author fay
 * @date 2026-06-09
 * @description
 */
@Slf4j
@Service
@ConditionalOnExpression("'${perf.data-post-handler:res-insert,res-update}'.split(',').contains('res-update')")
public class ResUpdatePostHandler implements PostHandler {

    @Resource
    private SqlSessionFactory sqlSessionFactory;
    @Resource
    private PerfTestConfig config;

    private final List<SqlTemplateRes> buffer = new ArrayList<>(config.getFlushThreshold());
    private final ReentrantLock lock = new ReentrantLock();


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

    }

    @Override
    public void close() {

    }
}
