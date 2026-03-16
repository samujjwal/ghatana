package com.ghatana.appplatform.sanctions.service;

/**
 * @doc.type    Service (Application)
 * @doc.purpose Implements Jaro-Winkler similarity and Soundex phonetic matching (D14-005).
 *              Combined with Levenshtein for optimal personal name matching.
 * @doc.layer   Application Service
 * @doc.pattern Algorithm
 */
public class JaroWinklerMatchingService {

    private static final double WINKLER_SCALING = 0.1;
    private static final int    WINKLER_MAX_PREFIX = 4;

    /** Jaro-Winkler score in [0.0, 1.0]. Higher = more similar. */
    public double score(String a, String b) {
        if (a == null || b == null) return 0.0;
        if (a.equalsIgnoreCase(b)) return 1.0;

        double jaro = jaroSimilarity(a.toLowerCase(), b.toLowerCase());
        int prefixLen = commonPrefixLength(a.toLowerCase(), b.toLowerCase());
        return jaro + prefixLen * WINKLER_SCALING * (1.0 - jaro);
    }

    /**
     * Combined score: max(Jaro-Winkler, Levenshtein, phonetic match).
     * Used as the final match score per D14-005 spec.
     */
    public double combinedScore(String a, String b, LevenshteinMatchingService levenshtein) {
        double jw = score(a, b);
        double lev = levenshtein.bestScore(a, b);
        double phonetic = soundexMatch(a, b) ? 0.80 : 0.0;
        return Math.max(jw, Math.max(lev, phonetic));
    }

    /** True if both strings have the same Soundex code. */
    public boolean soundexMatch(String a, String b) {
        return soundex(a).equals(soundex(b));
    }

    // ─── Jaro similarity ─────────────────────────────────────────────────────

    private double jaroSimilarity(String s, String t) {
        if (s.isEmpty() && t.isEmpty()) return 1.0;
        if (s.isEmpty() || t.isEmpty()) return 0.0;

        int matchWindow = Math.max(s.length(), t.length()) / 2 - 1;
        if (matchWindow < 0) matchWindow = 0;

        boolean[] sMatched = new boolean[s.length()];
        boolean[] tMatched = new boolean[t.length()];
        int matches = 0;
        int transpositions = 0;

        for (int i = 0; i < s.length(); i++) {
            int start = Math.max(0, i - matchWindow);
            int end = Math.min(i + matchWindow + 1, t.length());
            for (int j = start; j < end; j++) {
                if (!tMatched[j] && s.charAt(i) == t.charAt(j)) {
                    sMatched[i] = tMatched[j] = true;
                    matches++;
                    break;
                }
            }
        }

        if (matches == 0) return 0.0;

        int k = 0;
        for (int i = 0; i < s.length(); i++) {
            if (sMatched[i]) {
                while (!tMatched[k]) k++;
                if (s.charAt(i) != t.charAt(k)) transpositions++;
                k++;
            }
        }

        return (matches / (double) s.length()
                + matches / (double) t.length()
                + (matches - transpositions / 2.0) / matches) / 3.0;
    }

    private int commonPrefixLength(String a, String b) {
        int len = 0;
        int max = Math.min(WINKLER_MAX_PREFIX, Math.min(a.length(), b.length()));
        while (len < max && a.charAt(len) == b.charAt(len)) len++;
        return len;
    }

    // ─── Soundex ─────────────────────────────────────────────────────────────

    public String soundex(String s) {
        if (s == null || s.isEmpty()) return "0000";
        s = s.toUpperCase().replaceAll("[^A-Z]", "");
        if (s.isEmpty()) return "0000";

        char[] table = { '0', '1', '2', '3', '0', '1', '2', '0', '0', '2', '2', '4',
                          '5', '5', '0', '1', '2', '6', '2', '3', '0', '1', '0', '2', '0', '2' };
        StringBuilder code = new StringBuilder();
        code.append(s.charAt(0));
        char last = table[s.charAt(0) - 'A'];
        for (int i = 1; i < s.length() && code.length() < 4; i++) {
            char c = table[s.charAt(i) - 'A'];
            if (c != '0' && c != last) {
                code.append(c);
            }
            last = c;
        }
        while (code.length() < 4) code.append('0');
        return code.toString();
    }
}
