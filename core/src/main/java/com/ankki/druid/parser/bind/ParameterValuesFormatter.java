package com.ankki.druid.parser.bind;

import java.util.ArrayList;
import java.util.List;

public class ParameterValuesFormatter {

    public static String sqlBind(List<Object> parameterValues) {
        if (parameterValues == null || parameterValues.isEmpty())
            return " ";
        StringBuilder sb = new StringBuilder(parameterValues.size() * 16);
        sb.append('(');
        for (int i = 0; i < parameterValues.size(); i++) {
            if (i > 0)
                sb.append(',');
            int index = i + 1;
            Object value = parameterValues.get(i);
            sb.append('$').append(index).append('=');
            if (value instanceof Number) {
                sb.append(value);
            } else if (value == null) {
                sb.append("''");
            } else {
                // 值内的单引号用 '' 转义（SQL标准转义方式）
                sb.append('\'');
                String strVal = value.toString();
                for (int j = 0; j < strVal.length(); j++) {
                    char c = strVal.charAt(j);
                    if (c == '\'') sb.append('\''); // ' -> ''
                    sb.append(c);
                }
                sb.append('\'');
            }
        }
        sb.append(')');
        return sb.toString();
    }

    /**
     * 将绑定参数字符串解析为 List<Object>（sqlBind的逆操作）
     * <p>
     * 示例输入: ($1 = '137813', $2 = '/data1/xfer/stat/...',$3=123456, $4='', $5=, $6 = 'a01\'s005',)
     * 示例输出: ["137813", "/data1/xfer/stat/...", 123456, "", null]
     * <p>
     * 解析规则:
     * 1. 有引号的值 -> String（引号内为空则为空字符串""）
     * 2. 无引号的数值 -> Number类型（int范围内为Integer，否则Long，含小数点为Double）
     * 3. 无引号且为空（等号后直接逗号或结尾） -> null
     * <p>
     * 性能优化: 使用char[]避免反复charAt调用，单次遍历O(n)，无正则开销
     */
    public static List<Object> sqlUnbind(String bindStr) {
        if (bindStr == null || bindStr.isEmpty())
            return new ArrayList<>();

        // 定位有效内容的起止位置，跳过前后空格和外层括号
        final char[] chars = bindStr.toCharArray();
        int start = 0;
        int end = chars.length - 1;

        // 跳过前导空格
        while (start <= end && chars[start] == ' ') start++;
        // 跳过尾部空格
        while (end >= start && chars[end] == ' ') end--;

        if (start > end)
            return new ArrayList<>();

        // 去掉外层括号
        if (chars[start] == '(' && chars[end] == ')') {
            start++;
            end--;
        }

        List<Object> result = new ArrayList<>(8);
        int i = start;

        while (i <= end) {
            // 跳过空格，定位到 '$'
            while (i <= end && chars[i] == ' ') i++;
            if (i > end) break;

            // 快速跳过 "$N" 部分（$后面跟数字）和空格，找到 '='
            while (i <= end && chars[i] != '=') i++;
            if (i > end) break;
            i++; // 跳过 '='

            // 跳过等号后的空格
            while (i <= end && chars[i] == ' ') i++;

            if (i > end || chars[i] == ',') {
                // 值为空 -> null
                result.add(null);
            } else if (chars[i] == '\'') {
                // 带引号的字符串值：支持 '' 转义（两个连续单引号表示一个字面量 ')
                i++; // 跳过开始引号
                StringBuilder valBuf = new StringBuilder();
                while (i <= end) {
                    if (chars[i] == '\'') {
                        // 检查是否为 '' 转义
                        if (i + 1 <= end && chars[i + 1] == '\'') {
                            valBuf.append('\''); // '' -> '
                            i += 2;
                        } else {
                            break; // 结束引号
                        }
                    } else {
                        valBuf.append(chars[i]);
                        i++;
                    }
                }
                result.add(valBuf.toString());
                if (i <= end) i++; // 跳过结束引号
            } else {
                // 无引号的值：读取到逗号或结尾，尝试转为数字
                int valStart = i;
                while (i <= end && chars[i] != ',') i++;
                // 去掉值尾部空格
                int valEnd = i - 1;
                while (valEnd >= valStart && chars[valEnd] == ' ') valEnd--;
                if (valEnd < valStart) {
                    result.add(null);
                } else {
                    result.add(parseNumber(chars, valStart, valEnd + 1));
                }
            }

            // 跳过逗号分隔符
            if (i <= end && chars[i] == ',') i++;
        }
        return result;
    }

    /**
     * 尝试将char[]片段解析为数字，失败则返回字符串
     * 避免先substring再parseLong的额外对象分配
     */
    private static Object parseNumber(char[] chars, int start, int end) {
        boolean hasDecimal = false;
        boolean negative = false;
        int i = start;

        // 处理负号
        if (i < end && chars[i] == '-') {
            negative = true;
            i++;
        }

        // 校验是否全为数字（可含一个小数点）
        for (int j = i; j < end; j++) {
            char c = chars[j];
            if (c == '.') {
                if (hasDecimal) // 多个小数点，非数字
                    return new String(chars, start, end - start);
                hasDecimal = true;
            } else if (c < '0' || c > '9') {
                // 非数字字符，直接返回字符串
                return new String(chars, start, end - start);
            }
        }

        // 没有有效数字位
        if (i >= end)
            return new String(chars, start, end - start);

        String numStr = new String(chars, start, end - start);
        try {
            if (hasDecimal)
                return Double.parseDouble(numStr);
            long v = Long.parseLong(numStr);
            // int范围内用Integer，减少装箱开销
            if (v >= Integer.MIN_VALUE && v <= Integer.MAX_VALUE)
                return (int) v;
            return v;
        } catch (NumberFormatException e) {
            return numStr;
        }
    }
}