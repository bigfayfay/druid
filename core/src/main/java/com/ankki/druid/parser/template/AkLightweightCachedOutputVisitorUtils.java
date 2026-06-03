/*
 * Decompiled with CFR 0.152.
 */
package com.ankki.druid.parser.template;

import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.visitor.ParameterizedOutputVisitorUtils;
import com.alibaba.druid.util.StringUtils;
import com.ankki.druid.parser.AkSqlParserStatusEnum;
import com.ankki.druid.parser.bind.ParameterValuesFormatter;
import com.ankki.druid.parser.utils.AkDruidUtil;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.LongAdder;
import java.util.regex.Pattern;

public class AkLightweightCachedOutputVisitorUtils {
    private static final String BIND_APPEND = ";(";
    private static final int MAX_ENTRIES_PER_KEY = 10;
    private static final int MAX_CACHE_SIZE = 1000;
    private static final Map<String, Set<CachedResult>> CACHE = Collections.synchronizedMap(new LRUCache(1000));
    private static final LongAdder hitCount = new LongAdder();
    private static final LongAdder missCount = new LongAdder();
    private static final LongAdder skipCount = new LongAdder();
    private static final LongAdder noParamHitCount = new LongAdder();
    private static final LongAdder bindHitCount = new LongAdder();
    private static final int NO_PARAM_MAX_SIZE = 500;
    private static final Map<String, String[]> NO_PARAM_CACHE = Collections.synchronizedMap(new LinkedHashMap<String, String[]>(666, 0.75f, true){

        @Override
        protected boolean removeEldestEntry(Map.Entry<String, String[]> eldest) {
            return this.size() > 500;
        }
    });
    private static final int BIND_CACHE_MAX_SIZE = 200;
    private static final Map<String, String[]> BIND_CACHE = Collections.synchronizedMap(new LinkedHashMap<String, String[]>(266, 0.75f, true){

        @Override
        protected boolean removeEldestEntry(Map.Entry<String, String[]> eldest) {
            return this.size() > 200;
        }
    });
    private static final Pattern INSERT_AS_PATTERN = Pattern.compile("(INSERT\\s+INTO\\s+[\\w.]+)\\s+AS\\s+(\\w+)(?=[\\s(])", 2);
    private static final Pattern SELECT_AS_PATTERN = Pattern.compile("((?:FROM|JOIN|LEFT\\s+JOIN|RIGHT\\s+JOIN|INNER\\s+JOIN|OUTER\\s+JOIN|CROSS\\s+JOIN|FULL\\s+JOIN)\\s+[\\w.]+)\\s+AS\\s+(\\w+)(?=[\\s,)])", 2);
    private static final Pattern UPDATE_AS_PATTERN = Pattern.compile("(UPDATE\\s+[\\w.]+)\\s+AS\\s+(\\w+)(\\s+SET)", 2);

    private static String noParamKey(Integer akDbTypeId, String normSql) {
        return akDbTypeId + "|" + normSql;
    }

    private static String[] noParamGet(Integer akDbTypeId, String normSql) {
        return NO_PARAM_CACHE.get(AkLightweightCachedOutputVisitorUtils.noParamKey(akDbTypeId, normSql));
    }

    private static void noParamPut(Integer akDbTypeId, String normSql, String[] result) {
        NO_PARAM_CACHE.put(AkLightweightCachedOutputVisitorUtils.noParamKey(akDbTypeId, normSql), result);
    }

    private static String bindKey(Integer akDbTypeId, String normSql) {
        return "B|" + akDbTypeId + "|" + normSql;
    }

    private static String[] bindGet(Integer akDbTypeId, String normSql) {
        return BIND_CACHE.get(AkLightweightCachedOutputVisitorUtils.bindKey(akDbTypeId, normSql));
    }

    private static void bindPut(Integer akDbTypeId, String normSql, String[] templateResult) {
        BIND_CACHE.put(AkLightweightCachedOutputVisitorUtils.bindKey(akDbTypeId, normSql), templateResult);
    }

    public static String[] getSqlTemplate_v2(String sql, Integer akDbTypeId) {
        String[] cached;
        String sqlForParsing;
        String bindMetadata = null;
        int bindIdx = sql.lastIndexOf(BIND_APPEND);
        if (bindIdx > 0) {
            sqlForParsing = sql.substring(0, bindIdx);
            bindMetadata = sql.substring(bindIdx + 1);
        } else {
            sqlForParsing = sql;
        }
        String normSql = AkLightweightCachedOutputVisitorUtils.basicNormalize(sqlForParsing);
        if (bindMetadata == null && (cached = AkLightweightCachedOutputVisitorUtils.noParamGet(akDbTypeId, normSql)) != null) {
            noParamHitCount.increment();
            return cached;
        }
        if (bindMetadata != null && (cached = AkLightweightCachedOutputVisitorUtils.bindGet(akDbTypeId, normSql)) != null) {
            bindHitCount.increment();
            return new String[]{cached[0], cached[1], cached[2], bindMetadata};
        }
        boolean shouldCache = bindMetadata == null && AkLightweightCachedOutputVisitorUtils.isCacheableSqlType(sqlForParsing);
        String cacheKey = null;
        if (shouldCache) {
            cacheKey = AkLightweightCachedOutputVisitorUtils.generateCacheKey(akDbTypeId, sqlForParsing);
            Set<CachedResult> resultSet = CACHE.get(cacheKey);
            if (resultSet != null && !resultSet.isEmpty()) {
                hitCount.increment();
                return AkLightweightCachedOutputVisitorUtils.buildResult(resultSet, sqlForParsing, akDbTypeId, cacheKey, normSql);
            }
            missCount.increment();
            return AkLightweightCachedOutputVisitorUtils.parseAndCache(sqlForParsing, null, akDbTypeId, cacheKey, true, normSql);
        }
        skipCount.increment();
        return AkLightweightCachedOutputVisitorUtils.parseAndCache(sqlForParsing, bindMetadata, akDbTypeId, cacheKey, false, normSql);
    }

    private static String[] buildResult(Set<CachedResult> resultSet, String originalSql, Integer akDbTypeId, String cacheKey, String normSql) {
        String operationType = AkLightweightCachedOutputVisitorUtils.getOperationType(originalSql);
        String normFullOrig = normSql;
        int origStart = AkLightweightCachedOutputVisitorUtils.findSuffixStart(normFullOrig);
        String normOrig = origStart > 0 ? normFullOrig.substring(origStart) : normFullOrig;
        String compareOrig = AkLightweightCachedOutputVisitorUtils.normalizeTableAliasAS(operationType, normOrig);
        for (CachedResult cached : resultSet) {
            String compareTpl = origStart > 0 ? cached.compareSuffix : cached.compareFull;
            List<Object> parameterValues = AkLightweightCachedOutputVisitorUtils.compareExtract(compareOrig, compareTpl);
            if (parameterValues == null || cached.placeholderCount != null && parameterValues.size() != cached.placeholderCount.intValue()) continue;
            String bindResult = ParameterValuesFormatter.sqlBind(parameterValues);
            return new String[]{cached.status, cached.md5, cached.template, bindResult};
        }
        return AkLightweightCachedOutputVisitorUtils.parseAndCache(originalSql, null, akDbTypeId, cacheKey, true, normSql);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private static String[] parseAndCache(String sqlForParsing, String bindMetadata, Integer akDbTypeId, String cacheKey, boolean shouldCache, String normSql) {
        try {
            String bindResult;
            DbType dbType = AkDruidUtil.getDbType(akDbTypeId);
            if (dbType == null) {
                return new String[]{AkSqlParserStatusEnum.NonSupport.name(), "", "", ""};
            }
            ArrayList<Object> parameterValues = new ArrayList<Object>();
            String sqlTemplate = bindMetadata != null ? SQLUtils.format(sqlForParsing, dbType) : ParameterizedOutputVisitorUtils.parameterize(sqlForParsing, dbType, parameterValues);
            if (StringUtils.isEmpty(sqlTemplate)) {
                return new String[]{AkSqlParserStatusEnum.Failure.name(), "", "", ""};
            }
            String templateMd5 = AkLightweightCachedOutputVisitorUtils.calculateMD5(sqlTemplate);
            int placeholderCount = AkLightweightCachedOutputVisitorUtils.countChar(sqlTemplate, '?');
            String string = bindResult = bindMetadata != null ? bindMetadata : ParameterValuesFormatter.sqlBind(parameterValues);
            if (bindMetadata != null) {
                AkLightweightCachedOutputVisitorUtils.bindPut(akDbTypeId, normSql, new String[]{AkSqlParserStatusEnum.Success.name(), templateMd5, sqlTemplate});
            } else if (sqlTemplate.indexOf(63) < 0 && sqlTemplate.indexOf(36) < 0) {
                AkLightweightCachedOutputVisitorUtils.noParamPut(akDbTypeId, normSql, new String[]{AkSqlParserStatusEnum.Success.name(), templateMd5, sqlTemplate, bindResult});
            } else if (shouldCache && cacheKey != null) {
                CachedResult result = new CachedResult(AkSqlParserStatusEnum.Success.name(), templateMd5, sqlTemplate, placeholderCount);
                Map<String, Set<CachedResult>> map = CACHE;
                synchronized (map) {
                    Set<CachedResult> resultSet = CACHE.get(cacheKey);
                    if (resultSet == null) {
                        resultSet = new HashSet<CachedResult>();
                        CACHE.put(cacheKey, resultSet);
                    }
                    if (resultSet.size() >= 10) {
                        resultSet.clear();
                    }
                    resultSet.add(result);
                }
            }
            return new String[]{AkSqlParserStatusEnum.Success.name(), templateMd5, sqlTemplate, bindResult};
        }
        catch (Exception e) {
            return new String[]{AkSqlParserStatusEnum.Failure.name(), "", "", ""};
        }
    }

    static String basicNormalize(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        boolean lastSpace = false;
        for (int i = 0; i < s.length(); ++i) {
            char c = s.charAt(i);
            if (c == '\n' || c == '\r' || c == '\t') {
                c = ' ';
            }
            if (c == ' ') {
                if (lastSpace) continue;
                sb.append(c);
                lastSpace = true;
                continue;
            }
            sb.append(c);
            lastSpace = false;
        }
        return sb.toString();
    }

    static List<Object> compareExtract(String original, String template) {
        try {
            int i;
            ArrayList<Object> params = new ArrayList<Object>();
            int ti = 0;
            int oi = 0;
            int tLen = template.length();
            int oLen = original.length();
            ArrayList<String> castStack = new ArrayList<String>();
            while (ti < tLen && oi < oLen) {
                String top;
                String typeName;
                String word;
                int afterWord;
                char tc = template.charAt(ti);
                char oc = original.charAt(oi);
                if (tc == '?') {
                    int[] result = AkLightweightCachedOutputVisitorUtils.extractParam(original, oi);
                    if (result == null) {
                        return null;
                    }
                    String value = original.substring(oi, result[0]);
                    if (value.length() >= 2 && (value.charAt(0) == '\'' || value.charAt(0) == '\"')) {
                        value = value.substring(1, value.length() - 1);
                    }
                    if (value.equalsIgnoreCase("NULL")) {
                        params.add(null);
                    } else {
                        params.add(value);
                    }
                    oi = result[0];
                    ++ti;
                    continue;
                }
                if (AkLightweightCachedOutputVisitorUtils.charEquals(tc, oc)) {
                    ++ti;
                    ++oi;
                    continue;
                }
                if (Character.isWhitespace(tc)) {
                    ++ti;
                    continue;
                }
                if (Character.isWhitespace(oc)) {
                    ++oi;
                    continue;
                }
                if (Character.isLetter(oc) && (afterWord = oi + (word = AkLightweightCachedOutputVisitorUtils.readWord(original, oi)).length()) < oLen && original.charAt(afterWord) == '(') {
                    castStack.add(word.toLowerCase());
                    oi = afterWord + 1;
                    continue;
                }
                if (oc == '(' && tc != '(' && oi + 1 < oLen && AkLightweightCachedOutputVisitorUtils.charEquals(original.charAt(oi + 1), tc)) {
                    ++oi;
                    continue;
                }
                if (oc == ')' && tc != ')' && oi + 1 < oLen && AkLightweightCachedOutputVisitorUtils.charEquals(original.charAt(oi + 1), tc)) {
                    ++oi;
                    continue;
                }
                if (tc == ':' && ti + 1 < tLen && template.charAt(ti + 1) == ':' && (typeName = AkLightweightCachedOutputVisitorUtils.readWord(template, ti + 2)) != null && !castStack.isEmpty() && (top = (String)castStack.get(castStack.size() - 1)).equals(typeName.toLowerCase())) {
                    castStack.remove(castStack.size() - 1);
                    ti = ti + 2 + typeName.length();
                    if (oi >= oLen || original.charAt(oi) != ')') continue;
                    ++oi;
                    continue;
                }
                return null;
            }
            if (!castStack.isEmpty()) {
                return null;
            }
            for (i = ti; i < tLen; ++i) {
                if (Character.isWhitespace(template.charAt(i))) continue;
                return null;
            }
            for (i = oi; i < oLen; ++i) {
                if (Character.isWhitespace(original.charAt(i))) continue;
                return null;
            }
            return params;
        }
        catch (Exception e) {
            return null;
        }
    }

    static boolean charEquals(char a, char b) {
        return Character.toLowerCase(a) == Character.toLowerCase(b);
    }

    static String readWord(String s, int start) {
        int end;
        if (start >= s.length() || !Character.isLetter(s.charAt(start))) {
            return null;
        }
        for (end = start; end < s.length() && (Character.isLetterOrDigit(s.charAt(end)) || s.charAt(end) == '_'); ++end) {
        }
        return s.substring(start, end);
    }

    static int[] extractParam(String sql, int start) {
        if (start >= sql.length()) {
            return null;
        }
        char c = sql.charAt(start);
        if (c == '\'' || c == '\"') {
            char quote = c;
            for (int i = start + 1; i < sql.length(); ++i) {
                if (sql.charAt(i) != quote) continue;
                if (i + 1 < sql.length() && sql.charAt(i + 1) == quote) {
                    ++i;
                    continue;
                }
                return new int[]{i + 1};
            }
            return null;
        }
        if (sql.length() >= start + 4 && sql.substring(start, start + 4).equalsIgnoreCase("NULL")) {
            return new int[]{start + 4};
        }
        if (Character.isDigit(c) || c == '-' && start + 1 < sql.length() && Character.isDigit(sql.charAt(start + 1))) {
            int i;
            for (i = start + 1; i < sql.length() && (Character.isDigit(sql.charAt(i)) || sql.charAt(i) == '.'); ++i) {
            }
            return new int[]{i};
        }
        if (Character.isLetter(c)) {
            int i;
            for (i = start; i < sql.length() && Character.isLetter(sql.charAt(i)); ++i) {
            }
            return new int[]{i};
        }
        return null;
    }

    private static int findSuffixStart(String sql) {
        if (sql == null || sql.isEmpty()) {
            return 0;
        }
        String upper = sql.toUpperCase();
        if (upper.startsWith("INSERT") || upper.startsWith("BEGIN")) {
            int pos = upper.indexOf("VALUES");
            return pos >= 0 ? pos + 6 : 0;
        }
        if (upper.startsWith("SELECT")) {
            int pos = upper.indexOf(" WHERE ");
            return pos >= 0 ? pos + 7 : 0;
        }
        if (upper.startsWith("UPDATE")) {
            int pos = upper.indexOf(" SET ");
            return pos >= 0 ? pos + 5 : 0;
        }
        if (upper.startsWith("DELETE")) {
            int pos = upper.indexOf(" WHERE ");
            return pos >= 0 ? pos + 7 : 0;
        }
        return 0;
    }

    private static String getOperationType(String sql) {
        if (sql == null || sql.isEmpty()) {
            return "";
        }
        String upper = sql.toUpperCase().trim();
        if (upper.startsWith("INSERT")) {
            return "INSERT";
        }
        if (upper.startsWith("SELECT")) {
            return "SELECT";
        }
        if (upper.startsWith("UPDATE")) {
            return "UPDATE";
        }
        if (upper.startsWith("DELETE")) {
            return "DELETE";
        }
        if (upper.startsWith("BEGIN")) {
            int sc = sql.indexOf(59);
            if (sc > 0) {
                String remaining = sql.substring(sc + 1).trim();
                return AkLightweightCachedOutputVisitorUtils.getOperationType(remaining);
            }
            return "BEGIN";
        }
        return "";
    }

    private static String normalizeTableAliasAS(String operationType, String sql) {
        if (sql == null || sql.isEmpty()) {
            return sql;
        }
        if (sql.indexOf(" AS ") < 0 && sql.indexOf(" as ") < 0) {
            return sql;
        }
        switch (operationType.toUpperCase()) {
            case "INSERT": {
                return INSERT_AS_PATTERN.matcher(sql).replaceAll("$1 $2");
            }
            case "SELECT": {
                return SELECT_AS_PATTERN.matcher(sql).replaceAll("$1 $2");
            }
            case "UPDATE": {
                return UPDATE_AS_PATTERN.matcher(sql).replaceAll("$1 $2$3");
            }
        }
        return sql;
    }

    private static boolean isCacheableSqlType(String sql) {
        if (sql == null || sql.isEmpty() || sql.length() < 200) {
            return false;
        }
        String upper = sql.toUpperCase().trim();
        return upper.startsWith("UPDATE") || upper.startsWith("SELECT") || upper.startsWith("INSERT") || upper.startsWith("BEGIN") && AkLightweightCachedOutputVisitorUtils.isBatchInsertStatement(sql);
    }

    private static boolean isBatchInsertStatement(String sql) {
        int sc = sql.indexOf(59);
        return sc > 0 && sql.substring(sc + 1).trim().toUpperCase().startsWith("INSERT");
    }

    private static String generateCacheKey(Integer akDbTypeId, String sql) {
        String operationType = AkLightweightCachedOutputVisitorUtils.getOperationType(sql);
        String normalizedSql = AkLightweightCachedOutputVisitorUtils.normalizeTableAliasAS(operationType, sql);
        return akDbTypeId + "|" + AkLightweightCachedOutputVisitorUtils.truncateSqlBeforeValues(normalizedSql) + "|" + AkLightweightCachedOutputVisitorUtils.keywordSignature(normalizedSql);
    }

    private static String truncateSqlBeforeValues(String sql) {
        if (sql == null || sql.isEmpty()) {
            return sql;
        }
        String upper = sql.toUpperCase().trim();
        if (upper.startsWith("BEGIN") || upper.startsWith("INSERT")) {
            int pos = sql.toUpperCase().indexOf("VALUES");
            return pos > 0 ? sql.substring(0, pos + 6) : sql;
        }
        if (upper.startsWith("SELECT")) {
            int pos = sql.toUpperCase().indexOf(" WHERE ");
            return pos > 0 ? sql.substring(0, pos + 7) : sql;
        }
        if (upper.startsWith("UPDATE")) {
            int pos = sql.toUpperCase().indexOf(" SET ");
            return pos > 0 ? sql.substring(0, pos + 5) : sql;
        }
        if (upper.startsWith("DELETE")) {
            int pos = sql.toUpperCase().indexOf(" WHERE ");
            return pos > 0 ? sql.substring(0, pos + 7) : sql;
        }
        return sql;
    }

    private static String calculateMD5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(32);
            for (byte b : digest) {
                sb.append(String.format("%02x", b & 0xFF));
            }
            return sb.toString();
        }
        catch (Exception e) {
            return String.valueOf(input.hashCode());
        }
    }

    static String keywordSignature(String sql) {
        String upper = sql.toUpperCase();
        return "A" + AkLightweightCachedOutputVisitorUtils.countOccurrences(upper, " AND ") + "O" + AkLightweightCachedOutputVisitorUtils.countOccurrences(upper, " OR ") + "I" + AkLightweightCachedOutputVisitorUtils.countOccurrences(upper, " IN ") + "J" + AkLightweightCachedOutputVisitorUtils.countOccurrences(upper, " JOIN ") + "Q" + AkLightweightCachedOutputVisitorUtils.countChar(sql, '?') + "D" + AkLightweightCachedOutputVisitorUtils.countOccurrences(upper, "$") + "G" + AkLightweightCachedOutputVisitorUtils.countOccurrences(upper, " GROUP BY ") + "H" + AkLightweightCachedOutputVisitorUtils.countOccurrences(upper, " HAVING ") + "L" + AkLightweightCachedOutputVisitorUtils.countOccurrences(upper, " LIMIT ") + "V" + AkLightweightCachedOutputVisitorUtils.countOccurrences(upper, "VALUES") + "S" + AkLightweightCachedOutputVisitorUtils.countOccurrences(upper, "SELECT ");
    }

    static int countOccurrences(String s, String pattern) {
        int count = 0;
        int idx = 0;
        while ((idx = s.indexOf(pattern, idx)) >= 0) {
            ++count;
            idx += pattern.length();
        }
        return count;
    }

    private static int countChar(String s, char c) {
        int count = 0;
        for (int i = 0; i < s.length(); ++i) {
            if (s.charAt(i) != c) continue;
            ++count;
        }
        return count;
    }

    public static String getCacheStats() {
        long h = hitCount.sum();
        long m = missCount.sum();
        long s = skipCount.sum();
        long total = h + m;
        double rate = total > 0L ? (double)h * 100.0 / (double)total : 0.0;
        return String.format("\u547d\u4e2d\u7387: %.1f%% (hit=%d miss=%d skip=%d), \u7f13\u5b58\u5927\u5c0f: %d/%d, \u65e0\u53c2\u7f13\u5b58: %d/%d (hit=%d), bind\u7f13\u5b58: %d/%d (hit=%d)", rate, h, m, s, CACHE.size(), 1000, NO_PARAM_CACHE.size(), 500, noParamHitCount.sum(), BIND_CACHE.size(), 200, bindHitCount.sum());
    }

    public static void clearCache() {
        CACHE.clear();
        NO_PARAM_CACHE.clear();
        BIND_CACHE.clear();
        hitCount.reset();
        missCount.reset();
        skipCount.reset();
        noParamHitCount.reset();
        bindHitCount.reset();
    }

    private static class LRUCache<K, V>
    extends LinkedHashMap<K, V> {
        private final int maxSize;

        LRUCache(int maxSize) {
            super(maxSize * 4 / 3, 0.75f, true);
            this.maxSize = maxSize;
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
            return this.size() > this.maxSize;
        }
    }

    private static class CachedResult {
        final String status;
        final String md5;
        final String template;
        final Integer placeholderCount;
        final String normFull;
        final int normSuffixStart;
        final String normSuffix;
        final String compareSuffix;
        final String compareFull;

        CachedResult(String status, String md5, String template, Integer placeholderCount) {
            String ns;
            int ss;
            String nf;
            this.status = status;
            this.md5 = md5;
            this.template = template;
            this.placeholderCount = placeholderCount;
            this.normFull = nf = AkLightweightCachedOutputVisitorUtils.basicNormalize(template);
            this.normSuffixStart = ss = AkLightweightCachedOutputVisitorUtils.findSuffixStart(nf);
            this.normSuffix = ns = ss > 0 ? nf.substring(ss) : nf;
            String opType = AkLightweightCachedOutputVisitorUtils.getOperationType(nf);
            this.compareSuffix = AkLightweightCachedOutputVisitorUtils.normalizeTableAliasAS(opType, ns);
            this.compareFull = AkLightweightCachedOutputVisitorUtils.normalizeTableAliasAS(opType, nf);
        }

        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null || this.getClass() != obj.getClass()) {
                return false;
            }
            return this.template != null && this.template.equals(((CachedResult)obj).template);
        }

        public int hashCode() {
            return this.template != null ? this.template.hashCode() : 0;
        }
    }
}

