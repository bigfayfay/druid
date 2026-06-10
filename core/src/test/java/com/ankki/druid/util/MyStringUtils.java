package com.ankki.druid.util;

public class MyStringUtils {

    public static String replaceAllInvisibleChars(String str) {
        return str == null ? null :
                str.replaceAll("\\u0011", "\\\\")
                                    .replaceAll("\\u0012", "\\\"")
                                    .replaceAll("\\u0013", "\\\r")
                                    .replaceAll("\\u0014", "\\\n");
    }
}
