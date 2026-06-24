/*
 * Decompiled with CFR 0.152.
 */
package com.ankki.druid.parser.utils;

import com.alibaba.druid.DbType;
import com.alibaba.druid.pool.ha.PropertiesUtils;
import com.alibaba.druid.util.Utils;
import com.ankki.druid.parser.AkDruidSqlParser;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class AkDruidUtil {

    public static final String AK_DB_MAPPING_FILE = "META-INF/druid-akdbtype-mapping.properties";

    // akDbTypeId特殊类型转换映射表(静态缓存,提高性能)
    private static final Map<Integer, Integer> AK_DB_TYPE_MAPPING = new HashMap<>();
    
    static {
        loadAkDbTypeMapping();
    }
    
    /**
     * 加载akDbTypeId特殊类型转换配置
     */
    private static void loadAkDbTypeMapping() {
        try {
            Properties props = Utils.loadProperties(AK_DB_MAPPING_FILE);
            System.out.println("[Loading mapping]: Loaded " + props.size() + " in files.");
            for (String key : props.stringPropertyNames()) {
                try {
                    System.out.println("[Loading akDbType mapping]: " + key + "=" + props.getProperty(key));
                    Integer sourceId = Integer.valueOf(key.trim());
                    Integer targetId = Integer.valueOf(props.getProperty(key).trim());
                    AK_DB_TYPE_MAPPING.put(sourceId, targetId);
                    System.out.println("[Loading akDbType mapping]: " + key + "=" + props.getProperty(key));
                } catch (NumberFormatException e) {
                    // 忽略无效配置
                    System.err.println("[Loading akDbType mapping]: Invalid, " + key + "=" + props.getProperty(key));
                }
            }
            System.out.println("[Loading mapping]: Loaded " + AK_DB_TYPE_MAPPING.size() + " mappings.");
        } catch (Exception e) {
            System.err.println("[Failed to load druid-akdbtype-mapping.properties]: " + e.getMessage());
        }
    }
    
    /**
     * 获取转换后的akDbTypeId
     * @param akDbTypeId 原始akDbTypeId
     * @return 转换后的akDbTypeId,如果没有配置转换则返回原值
     */
    public static Integer getMappedAkDbTypeId(Integer akDbTypeId) {
        if (akDbTypeId == null) {
            return null;
        }
        return AK_DB_TYPE_MAPPING.getOrDefault(akDbTypeId, akDbTypeId);
    }
    
    /**
     * 获取DbType(支持特殊类型转换)
     * @param akDbTypeId 原始akDbTypeId
     * @return DbType对象
     */
    public static DbType getDbType(Integer akDbTypeId) {
        Integer mappedId = getMappedAkDbTypeId(akDbTypeId);
        return AkDruidSqlParser.akDruidDbTypeMap.get(mappedId);
    }

}

