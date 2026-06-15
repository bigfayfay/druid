/*
 * Decompiled with CFR 0.152.
 */
package com.ankki.druid.parser.utils;

import com.alibaba.druid.DbType;
import com.ankki.druid.parser.AkDruidSqlParser;

public class AkDruidUtil {
    public static DbType getDbType(Integer akDbTypeId) {
        return AkDruidSqlParser.akDruidDbTypeMap.get(akDbTypeId);
    }
}

