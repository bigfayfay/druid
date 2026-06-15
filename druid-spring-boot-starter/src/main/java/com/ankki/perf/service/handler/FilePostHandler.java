package com.ankki.perf.service.handler;

import cn.hutool.core.util.StrUtil;
import com.ankki.druid.parser.AkSqlParserStatusEnum;
import com.ankki.perf.entity.db.SqlTemplateRes;
import com.ankki.perf.service.PostHandler;
import com.ankki.perf.util.PerfUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 文件后处理器 - 将失败的SQL记录写入文件
 * 每个线程应该创建独立的实例，避免文件写入竞争
 *
 * @author fay
 * @date 2026-06-05
 */
public class FilePostHandler implements PostHandler {

    private static final Logger log = LoggerFactory.getLogger(FilePostHandler.class);

    private final BufferedWriter writer;
    private final Path filePath;
    private final int threadIdx;

    /**
     * 创建文件后处理器
     *
     * @param basePath  基础路径
     * @param threadIdx 线程索引，用于区分不同线程的文件
     * @param enabled   是否启用文件写入
     * @throws IOException 文件创建失败时抛出
     */
    public FilePostHandler(String basePath, int threadIdx, boolean enabled) throws IOException {
        this.threadIdx = threadIdx;
        this.filePath = Paths.get(basePath,
                "failed_sql_" + PerfUtil.pureHourMin() + "_" + PerfUtil.MONITOR_PID + "_t" + threadIdx + ".info");

        if (enabled) {
            this.writer = new BufferedWriter(new FileWriter(this.filePath.toFile()));
            log.debug("FilePostHandler created for thread-{}, file: {}", threadIdx, this.filePath);
        } else {
            this.writer = null;
            log.debug("FilePostHandler disabled for thread-{}", threadIdx);
        }
    }

    @Override
    public void addRecord(SqlTemplateRes record) {
        if (writer == null) {
            return;
        }

        AkSqlParserStatusEnum status = AkSqlParserStatusEnum.fastValueOf(record.getStatus());
        if (status == AkSqlParserStatusEnum.Success || status == AkSqlParserStatusEnum.NonSupport) {
            return;
        }

        // 只写入失败的记录
        if (record.getFailReason() != null) {
            try {
                writer.write(
                        String.join("|||",
                        ""+record.getId(),
                        record.getOperType(),
                        StrUtil.emptyIfNull(record.getFailReason()),
                        record.getOperSentence())
                );
                // 注意：这里需要原始SQL，但SqlTemplateRes中没有保存，可能需要调整
                // 暂时留空或者从其他地方获取
                writer.newLine();
            } catch (IOException e) {
                log.error("Failed to write to file for thread-{}, path: {}", threadIdx, filePath, e);
            }
        }
    }


    @Override
    public void flush() {
        if (writer != null) {
            try {
                writer.flush();
                log.debug("FilePostHandler flushed for thread-{}", threadIdx);
            } catch (IOException e) {
                log.error("Failed to flush file for thread-{}, path: {}", threadIdx, filePath, e);
            }
        }
    }

    /**
     * 关闭文件写入器
     */
    public void close() {
        if (writer != null) {
            try {
                writer.close();
                log.debug("FilePostHandler closed for thread-{}, file: {}", threadIdx, filePath);
            } catch (IOException e) {
                log.error("Failed to close file for thread-{}, path: {}", threadIdx, filePath, e);
            }
        }
    }
}
