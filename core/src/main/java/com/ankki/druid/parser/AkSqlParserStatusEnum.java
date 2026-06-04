/*
 * Decompiled with CFR 0.152.
 */
package com.ankki.druid.parser;

import com.alibaba.druid.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

public enum AkSqlParserStatusEnum {
    Failure(0),
    Success(1),
    NonSupport(2),
    EXCEPTION(3);

    public Integer status;

    /**
     * 预构建的名称->枚举映射，避免 valueOf() 的反射开销
     */
    private static final Map<String, AkSqlParserStatusEnum> NAME_MAP = new HashMap<>(8);
    static {
        for (AkSqlParserStatusEnum e : values()) {
            NAME_MAP.put(e.name(), e);
        }
    }

    /**
     * 高性能名称查找（O(1) HashMap查找，替代反射式 valueOf）
     */
    public static AkSqlParserStatusEnum fastValueOf(String name) {
        return NAME_MAP.get(name);
    }

    private AkSqlParserStatusEnum(Integer status) {
        this.status = status;
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

