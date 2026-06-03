/*
 * Decompiled with CFR 0.152.
 */
package com.ankki.druid.parser;

import com.alibaba.druid.util.StringUtils;

public enum AkSqlParserStatusEnum {
    Failure(0),
    Success(1),
    NonSupport(2),
    EXCEPTION(3);

    public Integer status;

    private AkSqlParserStatusEnum(Integer status) {
        this.status = status;
    }

    public String toString() {
        return this.name();
    }

    public String toString(String str) {
        if (!StringUtils.isEmpty(str)) {
            return this.name() + "|" + str + "";
        }
        return this.name();
    }

    public Integer getStatus() {
        return this.status;
    }
}

