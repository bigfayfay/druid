package com.ankki.druid.parser.config;

import java.util.HashMap;
import java.util.Map;

public class VmOptions {
    public static final String MONITOR = "monitor";
    public static final String MONITOR_DIR = "monitor.dir";
    // 监控-重置计数
    public static final String MONITOR_MAX_THRESHOLD = "monitor.max.threshold";
    // 监控-间隔
    public static final String MONITOR_INTERVAL = "monitor.interval";
    // 开启缓存
    public static final String CACHE_USE = "cacheUse";


    static final Map<String, String> OPTION_MAP = new HashMap<String, String>();

    static {
        OPTION_MAP.put(MONITOR, "false");
        OPTION_MAP.put(MONITOR_DIR, "/data/logs/druid");
        OPTION_MAP.put(MONITOR_INTERVAL, "10");
        OPTION_MAP.put(MONITOR_MAX_THRESHOLD, ""+(Long.MAX_VALUE - Integer.MAX_VALUE));
        OPTION_MAP.put(CACHE_USE, "false");
    }

    public static String getValue(String key) {
        return System.getProperty(key, OPTION_MAP.get(key));
    }

    public static boolean getBoolean(String key) {
        return Boolean.parseBoolean(getValue(key));
    }

    public static Integer getInt(String key) {
        return Integer.valueOf(getValue(key));
    }

    public static Long getLong(String key) {
        return Long.valueOf(getValue(key));
    }
}
