package com.ghatana.appplatform.sanctions.service;

import java.text.Normalizer;
import java.util.regex.Pattern;

/**
 * @doc.type    Service (Application)
 * @doc.purpose Implements Levenshtein edit distance for name matching (D14-004).
 *              Normalizes names before comparison: lowercase, strip diacritics, remove common suffixes.
 * @doc.layer   Application Service
 * @doc.pattern Algorithm
 */
public class LevenshteinMatchingService {

    private static final Pattern DIACRITIC_PATTERN = Pattern.compile("[\\p{InCombiningDiacriticalMarks}]");
    private static final Pattern SUFFIX_PATTERN =
            Pattern.compile("\\s+(ltd|corp|inc|pvt|llc|co|limited|private|company)\\s*$",
                    Pattern.CASE_INSENSITIVE);
    private static final Pattern NON_ALPHA = Pattern.compile("[^a-z0-9 ]");

    /**
     * Compute a normalized match score in [0.0, 1.0].
     * 1.0 = identical after normalization; lower = more dissimilar.
     */
    public double score(String a, String b) {
        String na = normalize(a);
        String nb = normalize(b);
        if (na.isEmpty() && nb.isEmpty()) return 1.0;
        if (na.isEmpty() || nb.isEmpty()) return 0.0;

        int distance = editDistance(na, nb);
        int maxLen = Math.max(na.length(), nb.length());
        return 1.0 - ((double) distance / maxLen);
    }

    /** Token-set score: allows word reordering (e.g. "Smith John" vs "John Smith"). */
    public double tokenSetScore(String a, String b) {
        var tokensA = java.util.Arrays.stream(normalize(a).split("\\s+"))
                .sorted().toList();
        var tokensB = java.util.Arrays.stream(normalize(b).split("\\s+"))
                .sorted().toList();
        return score(String.join(" ", tokensA), String.join(" ", tokensB));
    }

    /** Best of direct score and token-set score. */
    public double bestScore(String a, String b) {
        return Math.max(score(a, b), tokenSetScore(a, b));
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    public String normalize(String name) {
        if (name == null) return "";
        // Decompose unicode → remove diacritics
        String normalized = Normalizer.normalize(name, Normalizer.Form.NFD);
        normalized = DIACRITIC_PATTERN.matcher(normalized).replaceAll("");
        normalized = normalized.toLowerCase();
        // Remove legal suffixes
        normalized = SUFFIX_PATTERN.matcher(normalized).replaceAll("");
        // Remove non-alpha non-digit characters
        normalized = NON_ALPHA.matcher(normalized).replaceAll(" ");
        return normalized.trim().replaceAll("\\s+", " ");
    }

    private int editDistance(String s, String t) {
        int m = s.length(), n = t.length();
        int[] prev = new int[n + 1];
        int[] curr = new int[n + 1];
        for (int j = 0; j <= n; j++) prev[j] = j;
        for (int i = 1; i <= m; i++) {
            curr[0] = i;
            for (int j = 1; j <= n; j++) {
                if (s.charAt(i - 1) == t.charAt(j - 1)) {
                    curr[j] = prev[j - 1];
                } else {
                    curr[j] = 1 + Math.min(prev[j - 1], Math.min(prev[j], curr[j - 1]));
                }
            }
            System.arraycopy(curr, 0, prev, 0, n + 1);
        }
        return prev[n];
    }
}
