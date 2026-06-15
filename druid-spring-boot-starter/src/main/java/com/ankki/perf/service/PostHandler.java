package com.ankki.perf.service;

import com.ankki.perf.entity.db.SqlTemplateRes;

/**
 * @author fay
 * @date 2026-06-05
 * @description
 */
public interface PostHandler {

    public void addRecord(SqlTemplateRes record);

    public void flush();

    public void close();

}
