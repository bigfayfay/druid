/*
 * Decompiled with CFR 0.152.
 */
package com.ankki.druid.parser.bind;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SqlBindValueParser {
    public static List<Object> parse(String input) {
        if ((input = input.trim()).startsWith("(") && input.endsWith(")")) {
            input = input.substring(1, input.length() - 1);
        }
        ArrayList<Object> result = new ArrayList<Object>();
        Pattern pattern = Pattern.compile("\\$(\\d+)\\s*=\\s*'([^']*)'");
        Matcher matcher = pattern.matcher(input);
        int maxIndex = 0;
        HashMap<Integer, String> map = new HashMap<Integer, String>();
        while (matcher.find()) {
            int index = Integer.parseInt(matcher.group(1));
            String value = matcher.group(2);
            maxIndex = Math.max(maxIndex, index);
            if (Objects.isNull(value) || value.isEmpty()) {
                map.put(index, null);
                continue;
            }
            map.put(index, value);
        }
        for (int i = 1; i <= maxIndex; ++i) {
            result.add(map.get(i));
        }
        return result;
    }
}

