package com.ankki.perf.util;

import cn.hutool.core.date.DatePattern;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

public class AuditUtils {


    public static Long auditIdSec(Long auditId) {
        return auditId < 1000000000L ? auditId : Long.valueOf(("" + auditId).substring(0, 10));
    }



    public static String sec2DateTimeStr(Long second) {
        if (Objects.isNull(second)) {
            return null;
        } else {
            LocalDateTime localDateTime = instant2LocalDateTime(Instant.ofEpochSecond(second));
            return localDateTime.format(DatePattern.NORM_DATETIME_FORMATTER);
        }
    }

    public static Long dateTimeStr2Sec(String dateTimeStr) {
        DateTimeFormatter df = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return LocalDateTime.parse(dateTimeStr, df).atZone(ZoneId.systemDefault()).toEpochSecond();
    }


    public static LocalDateTime instant2LocalDateTime(Instant second) {
        return LocalDateTime.ofInstant(second, ZoneId.systemDefault());
    }
}
