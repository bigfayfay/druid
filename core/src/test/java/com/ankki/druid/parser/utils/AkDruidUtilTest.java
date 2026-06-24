/*
 * Decompiled with CFR 0.152.
 */
package com.ankki.druid.parser.utils;

import com.alibaba.druid.DbType;

/**
 * AkDruidUtil测试类
 */
public class AkDruidUtilTest {
    public static void main(String[] args) {
        System.out.println("=== AkDruidUtil akDbTypeId转换测试 ===");
        
        // 测试1: 正常转换(53 -> 3)
        Integer sourceId = 53;
        Integer mappedId = AkDruidUtil.getMappedAkDbTypeId(sourceId);
        System.out.println("测试1 - 映射转换: " + sourceId + " -> " + mappedId);
        
        // 测试2: 获取DbType
        DbType dbType = AkDruidUtil.getDbType(53);
        System.out.println("测试2 - 获取DbType(53): " + dbType);
        
        // 测试3: 未配置的ID应该返回原值
        Integer unmappedId = AkDruidUtil.getMappedAkDbTypeId(999);
        System.out.println("测试3 - 未配置ID(999): " + unmappedId);
        
        // 测试4: null值处理
        Integer nullId = AkDruidUtil.getMappedAkDbTypeId(null);
        System.out.println("测试4 - null值处理: " + nullId);
        
        System.out.println("=== 测试完成 ===");
    }
}
