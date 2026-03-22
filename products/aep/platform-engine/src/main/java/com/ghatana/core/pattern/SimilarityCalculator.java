package com.ghatana.core.pattern;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Similarity calculator for fuzzy correlation and string matching.
 *
 * <p><b>Purpose</b><br>
 * Provides multiple similarity algorithms for comparing strings, numbers,
 * and complex objects. Supports Levenshtein distance, Jaccard similarity,
 * cosine similarity, and custom similarity functions.
 *
 * @see AdvancedCorrelationEngine
 * @see FuzzyCorrelationHandler
 * @doc.type class
 * @doc.purpose Similarity calculation for fuzzy matching
 * @doc.layer core
 * @doc.pattern Utility
 */
public class SimilarityCalculator {

    private final SimilarityConfig config;
    private final Map<String, Double> similarityCache;

    /**
     * Create similarity calculator with default configuration.
     */
    public SimilarityCalculator() {
        this(new SimilarityConfig());
    }

    /**
     * Create similarity calculator with custom configuration.
     */
    public SimilarityCalculator(SimilarityConfig config) {
        this.config = config;
        this.similarityCache = new ConcurrentHashMap<>();
    }

    /**
     * Calculate similarity between two strings.
     */
    public double calculateSimilarity(String str1, String str2) {
        if (str1 == null || str2 == null) {
            return 0.0;
        }
        
        if (str1.equals(str2)) {
            return 1.0;
        }
        
        // Check cache first
        String cacheKey = str1 + ":" + str2;
        Double cached = similarityCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        
        double similarity = calculateSimilarityInternal(str1, str2);
        
        // Cache result
        similarityCache.put(cacheKey, similarity);
        
        return similarity;
    }

    /**
     * Calculate similarity between two numbers.
     */
    public double calculateSimilarity(Number num1, Number num2) {
        if (num1 == null || num2 == null) {
            return 0.0;
        }
        
        double val1 = num1.doubleValue();
        double val2 = num2.doubleValue();
        
        if (val1 == val2) {
            return 1.0;
        }
        
        double max = Math.max(Math.abs(val1), Math.abs(val2));
        if (max == 0.0) {
            return 1.0;
        }
        
        double difference = Math.abs(val1 - val2);
        return Math.max(0.0, 1.0 - (difference / max));
    }

    /**
     * Calculate similarity between two collections.
     */
    public double calculateSimilarity(Collection<?> col1, Collection<?> col2) {
        if (col1 == null || col2 == null) {
            return 0.0;
        }
        
        if (col1.isEmpty() && col2.isEmpty()) {
            return 1.0;
        }
        
        // Jaccard similarity
        Set<Object> set1 = new HashSet<>(col1);
        Set<Object> set2 = new HashSet<>(col2);
        
        Set<Object> intersection = new HashSet<>(set1);
        intersection.retainAll(set2);
        
        Set<Object> union = new HashSet<>(set1);
        union.addAll(set2);
        
        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }

    /**
     * Calculate similarity between two maps.
     */
    public double calculateSimilarity(Map<?, ?> map1, Map<?, ?> map2) {
        if (map1 == null || map2 == null) {
            return 0.0;
        }
        
        if (map1.isEmpty() && map2.isEmpty()) {
            return 1.0;
        }
        
        Set<?> commonKeys = new HashSet<>(map1.keySet());
        commonKeys.retainAll(map2.keySet());
        
        if (commonKeys.isEmpty()) {
            return 0.0;
        }
        
        double totalSimilarity = 0.0;
        int comparedPairs = 0;
        
        for (Object key : commonKeys) {
            Object val1 = map1.get(key);
            Object val2 = map2.get(key);
            
            double similarity = calculateObjectSimilarity(val1, val2);
            totalSimilarity += similarity;
            comparedPairs++;
        }
        
        return comparedPairs > 0 ? totalSimilarity / comparedPairs : 0.0;
    }

    /**
     * Calculate similarity between two generic objects.
     */
    public double calculateObjectSimilarity(Object obj1, Object obj2) {
        if (obj1 == null || obj2 == null) {
            return 0.0;
        }
        
        if (obj1.equals(obj2)) {
            return 1.0;
        }
        
        if (obj1 instanceof String && obj2 instanceof String) {
            return calculateSimilarity((String) obj1, (String) obj2);
        }
        
        if (obj1 instanceof Number && obj2 instanceof Number) {
            return calculateSimilarity((Number) obj1, (Number) obj2);
        }
        
        if (obj1 instanceof Collection && obj2 instanceof Collection) {
            return calculateSimilarity((Collection<?>) obj1, (Collection<?>) obj2);
        }
        
        if (obj1 instanceof Map && obj2 instanceof Map) {
            return calculateSimilarity((Map<?, ?>) obj1, (Map<?, ?>) obj2);
        }
        
        // Fallback to string comparison
        return calculateSimilarity(obj1.toString(), obj2.toString());
    }

    /**
     * Internal similarity calculation with configured algorithm.
     */
    private double calculateSimilarityInternal(String str1, String str2) {
        switch (config.getAlgorithm()) {
            case LEVENSHTEIN:
                return calculateLevenshteinSimilarity(str1, str2);
            case JACCARD:
                return calculateJaccardSimilarity(str1, str2);
            case COSINE:
                return calculateCosineSimilarity(str1, str2);
            case JARO_WINKLER:
                return calculateJaroWinklerSimilarity(str1, str2);
            case HYBRID:
            default:
                return calculateHybridSimilarity(str1, str2);
        }
    }

    /**
     * Levenshtein distance similarity.
     */
    private double calculateLevenshteinSimilarity(String str1, String str2) {
        int distance = calculateLevenshteinDistance(str1, str2);
        int maxLength = Math.max(str1.length(), str2.length());
        return maxLength == 0 ? 1.0 : 1.0 - (double) distance / maxLength;
    }

    /**
     * Calculate Levenshtein distance between two strings.
     */
    private int calculateLevenshteinDistance(String str1, String str2) {
        int[][] dp = new int[str1.length() + 1][str2.length() + 1];
        
        for (int i = 0; i <= str1.length(); i++) {
            dp[i][0] = i;
        }
        
        for (int j = 0; j <= str2.length(); j++) {
            dp[0][j] = j;
        }
        
        for (int i = 1; i <= str1.length(); i++) {
            for (int j = 1; j <= str2.length(); j++) {
                if (str1.charAt(i - 1) == str2.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1];
                } else {
                    dp[i][j] = 1 + Math.min(Math.min(dp[i - 1][j], dp[i][j - 1]), dp[i - 1][j - 1]);
                }
            }
        }
        
        return dp[str1.length()][str2.length()];
    }

    /**
     * Jaccard similarity for strings (based on character n-grams).
     */
    private double calculateJaccardSimilarity(String str1, String str2) {
        Set<String> ngrams1 = generateNGrams(str1, config.getNGramSize());
        Set<String> ngrams2 = generateNGrams(str2, config.getNGramSize());
        
        Set<String> intersection = new HashSet<>(ngrams1);
        intersection.retainAll(ngrams2);
        
        Set<String> union = new HashSet<>(ngrams1);
        union.addAll(ngrams2);
        
        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }

    /**
     * Generate n-grams from string.
     */
    private Set<String> generateNGrams(String str, int n) {
        Set<String> ngrams = new HashSet<>();
        
        if (str.length() < n) {
            ngrams.add(str.toLowerCase());
            return ngrams;
        }
        
        for (int i = 0; i <= str.length() - n; i++) {
            ngrams.add(str.substring(i, i + n).toLowerCase());
        }
        
        return ngrams;
    }

    /**
     * Cosine similarity for strings (based on character vectors).
     */
    private double calculateCosineSimilarity(String str1, String str2) {
        Map<Character, Integer> vector1 = buildCharacterVector(str1);
        Map<Character, Integer> vector2 = buildCharacterVector(str2);
        
        Set<Character> allChars = new HashSet<>();
        allChars.addAll(vector1.keySet());
        allChars.addAll(vector2.keySet());
        
        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;
        
        for (char c : allChars) {
            int v1 = vector1.getOrDefault(c, 0);
            int v2 = vector2.getOrDefault(c, 0);
            
            dotProduct += v1 * v2;
            norm1 += v1 * v1;
            norm2 += v2 * v2;
        }
        
        if (norm1 == 0.0 || norm2 == 0.0) {
            return 0.0;
        }
        
        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }

    /**
     * Build character frequency vector.
     */
    private Map<Character, Integer> buildCharacterVector(String str) {
        Map<Character, Integer> vector = new HashMap<>();
        
        for (char c : str.toCharArray()) {
            vector.put(c, vector.getOrDefault(c, 0) + 1);
        }
        
        return vector;
    }

    /**
     * Jaro-Winkler similarity.
     */
    private double calculateJaroWinklerSimilarity(String str1, String str2) {
        double jaro = calculateJaroSimilarity(str1, str2);
        
        // Find common prefix
        int prefixLength = 0;
        int maxPrefix = Math.min(Math.min(str1.length(), str2.length()), 4);
        
        for (int i = 0; i < maxPrefix; i++) {
            if (str1.charAt(i) == str2.charAt(i)) {
                prefixLength++;
            } else {
                break;
            }
        }
        
        return jaro + (0.1 * prefixLength * (1.0 - jaro));
    }

    /**
     * Calculate Jaro similarity.
     */
    private double calculateJaroSimilarity(String str1, String str2) {
        if (str1.equals(str2)) {
            return 1.0;
        }
        
        int len1 = str1.length();
        int len2 = str2.length();
        
        if (len1 == 0 || len2 == 0) {
            return 0.0;
        }
        
        int matchDistance = Math.max(len1, len2) / 2 - 1;
        boolean[] str1Matches = new boolean[len1];
        boolean[] str2Matches = new boolean[len2];
        
        int matches = 0;
        int transpositions = 0;
        
        // Find matches
        for (int i = 0; i < len1; i++) {
            int start = Math.max(0, i - matchDistance);
            int end = Math.min(i + matchDistance + 1, len2);
            
            for (int j = start; j < end; j++) {
                if (!str2Matches[j] && str1.charAt(i) == str2.charAt(j)) {
                    str1Matches[i] = true;
                    str2Matches[j] = true;
                    matches++;
                    break;
                }
            }
        }
        
        if (matches == 0) {
            return 0.0;
        }
        
        // Count transpositions
        int k = 0;
        for (int i = 0; i < len1; i++) {
            if (str1Matches[i]) {
                while (!str2Matches[k]) {
                    k++;
                }
                if (str1.charAt(i) != str2.charAt(k)) {
                    transpositions++;
                }
                k++;
            }
        }
        
        return ((matches / (double) len1) + (matches / (double) len2) + 
                ((matches - transpositions / 2.0) / matches)) / 3.0;
    }

    /**
     * Hybrid similarity combining multiple algorithms.
     */
    private double calculateHybridSimilarity(String str1, String str2) {
        double levenshtein = calculateLevenshteinSimilarity(str1, str2);
        double jaccard = calculateJaccardSimilarity(str1, str2);
        double jaroWinkler = calculateJaroWinklerSimilarity(str1, str2);
        
        // Weighted average
        return (levenshtein * 0.4 + jaccard * 0.3 + jaroWinkler * 0.3);
    }

    /**
     * Clear similarity cache.
     */
    public void clearCache() {
        similarityCache.clear();
    }

    /**
     * Get cache statistics.
     */
    public CacheStatistics getCacheStatistics() {
        return new CacheStatistics(similarityCache.size());
    }

    /**
     * Similarity algorithm enumeration.
     */
    public enum SimilarityAlgorithm {
        LEVENSHTEIN,    // Edit distance based
        JACCARD,        // N-gram based
        COSINE,         // Vector based
        JARO_WINKLER,   // Character matching
        HYBRID          // Combination of algorithms
    }

    /**
     * Similarity configuration.
     */
    public static class SimilarityConfig {
        private SimilarityAlgorithm algorithm = SimilarityAlgorithm.HYBRID;
        private int nGramSize = 2;
        private boolean caseSensitive = false;
        private boolean ignoreWhitespace = true;

        public SimilarityAlgorithm getAlgorithm() { return algorithm; }
        public int getNGramSize() { return nGramSize; }
        public boolean isCaseSensitive() { return caseSensitive; }
        public boolean isIgnoreWhitespace() { return ignoreWhitespace; }

        public Builder toBuilder() {
            return new Builder()
                    .algorithm(algorithm)
                    .nGramSize(nGramSize)
                    .caseSensitive(caseSensitive)
                    .ignoreWhitespace(ignoreWhitespace);
        }

        public static class Builder {
            private SimilarityAlgorithm algorithm = SimilarityAlgorithm.HYBRID;
            private int nGramSize = 2;
            private boolean caseSensitive = false;
            private boolean ignoreWhitespace = true;

            public Builder algorithm(SimilarityAlgorithm algorithm) {
                this.algorithm = algorithm;
                return this;
            }

            public Builder nGramSize(int nGramSize) {
                this.nGramSize = nGramSize;
                return this;
            }

            public Builder caseSensitive(boolean caseSensitive) {
                this.caseSensitive = caseSensitive;
                return this;
            }

            public Builder ignoreWhitespace(boolean ignoreWhitespace) {
                this.ignoreWhitespace = ignoreWhitespace;
                return this;
            }

            public SimilarityConfig build() {
                SimilarityConfig config = new SimilarityConfig();
                config.algorithm = this.algorithm;
                config.nGramSize = this.nGramSize;
                config.caseSensitive = this.caseSensitive;
                config.ignoreWhitespace = this.ignoreWhitespace;
                return config;
            }
        }
    }

    /**
     * Cache statistics.
     */
    public static class CacheStatistics {
        private final int size;

        public CacheStatistics(int size) {
            this.size = size;
        }

        public int getSize() { return size; }

        @Override
        public String toString() {
            return String.format("CacheStatistics{size=%d}", size);
        }
    }
}
