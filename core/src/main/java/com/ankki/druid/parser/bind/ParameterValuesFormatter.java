/*
 * Decompiled with CFR 0.152.
 */
package com.ankki.druid.parser.bind;

import java.util.List;

public class ParameterValuesFormatter {
    public static String sqlBind(List<Object> parameterValues) {
        if (parameterValues == null || parameterValues.isEmpty()) {
            return " ";
        }
        StringBuilder sb = new StringBuilder(parameterValues.size() * 16);
        sb.append('(');
        for (int i = 0; i < parameterValues.size(); ++i) {
            if (i > 0) {
                sb.append(',');
            }
            int index = i + 1;
            Object value = parameterValues.get(i);
            sb.append('$').append(index).append('=');
            if (value instanceof Number) {
                sb.append(value);
                continue;
            }
            if (value == null) {
                sb.append("''");
                continue;
            }
            sb.append('\'').append(value).append('\'');
        }
        sb.append(')');
        return sb.toString();
    }
}

