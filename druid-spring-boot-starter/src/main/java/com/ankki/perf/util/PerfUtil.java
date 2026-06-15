package com.ankki.perf.util;

import java.lang.management.ManagementFactory;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class PerfUtil {
    public static String MONITOR_PID = "unknown";

    static {
        // PID
        String pid = "unknown";
        try {
            String name = ManagementFactory.getRuntimeMXBean().getName();
            int atIdx = name.indexOf(64);
            if (atIdx > 0) {
                pid = name.substring(0, atIdx);
            }
        } catch (Exception e) {
            // ignore
        }
        MONITOR_PID = pid;
    }

    public static String pureHourMin() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"));
    }

}
