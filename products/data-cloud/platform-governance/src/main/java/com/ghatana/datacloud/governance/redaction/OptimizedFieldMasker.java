/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.governance.redaction;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Optimized field-level masking and redaction with caching and compiled patterns.
 *
 * <p>This implementation provides performance optimizations over basic field masking:
 * <ul>
 *   <li>Pre-compiled regex patterns for consistent performance</li>
 *   <li>Result caching for frequently masked values</li>
 *   <li>Parallel batch processing for large records</li>
 *   <li>Efficient string operations avoiding unnecessary allocations</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Optimized field masking with caching, compiled patterns, and parallel processing
 * @doc.layer product
 * @doc.pattern Optimizer
 */
public final class OptimizedFieldMasker {

    private static final String REDACTED = "[REDACTED]";
    private static final String NULL_PLACEHOLDER = "[NULL]";
    private static final String EMPTY_PLACEHOLDER = "[EMPTY]";

    // Pre-compiled patterns for better performance
    private static final Pattern ALPHANUMERIC_PATTERN = Pattern.compile("[0-9A-Za-z]");

    private final MaskingPolicy policy;
    private final Map<String, String> cache;
    private final int maxCacheSize;
    private final boolean enableParallelBatch;

    /**
     * Creates an optimized field masker with default cache size (1000 entries).
     *
     * @param policy the masking policy to apply
     */
    public OptimizedFieldMasker(MaskingPolicy policy) {
        this(policy, 1000, true);
    }

    /**
     * Creates an optimized field masker with custom configuration.
     *
     * @param policy the masking policy to apply
     * @param maxCacheSize maximum number of cached masking results
     * @param enableParallelBatch whether to enable parallel batch processing
     */
    public OptimizedFieldMasker(MaskingPolicy policy, int maxCacheSize, boolean enableParallelBatch) {
        this.policy = policy;
        this.maxCacheSize = maxCacheSize;
        this.cache = new ConcurrentHashMap<>(maxCacheSize);
        this.enableParallelBatch = enableParallelBatch;
    }

    /**
     * Masks a single field value based on the policy rules.
     *
     * @param field the field name
     * @param value the field value
     * @return the masked value
     */
    public String mask(String field, String value) {
        if (value == null) {
            return NULL_PLACEHOLDER;
        }
        if (value.isEmpty()) {
            return EMPTY_PLACEHOLDER;
        }

        Optional<MaskingMode> mode = policy.modeFor(field);
        if (mode.isEmpty()) {
            return value;
        }

        // Check cache for pre-computed result
        String cacheKey = field + ":" + value;
        String cached = cache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        String masked = applyMode(mode.get(), value);

        // Cache the result if cache not full
        if (cache.size() < maxCacheSize) {
            cache.put(cacheKey, masked);
        }

        return masked;
    }

    /**
     * Masks all registered fields in a record.
     *
     * @param record the record to mask
     * @return a new map with masked values
     */
    public Map<String, String> maskRecord(Map<String, String> record) {
        if (record.isEmpty()) {
            return Map.of();
        }

        if (enableParallelBatch && record.size() > 10) {
            return record.entrySet().parallelStream()
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            e -> mask(e.getKey(), e.getValue()),
                            (a, b) -> a,
                            java.util.LinkedHashMap::new));
        } else {
            Map<String, String> result = new java.util.LinkedHashMap<>(record.size());
            record.forEach((k, v) -> result.put(k, mask(k, v)));
            return result;
        }
    }

    /**
     * Clears the masking result cache.
     */
    public void clearCache() {
        cache.clear();
    }

    /**
     * Returns the current cache size.
     *
     * @return number of cached entries
     */
    public int getCacheSize() {
        return cache.size();
    }

    /**
     * Returns cache statistics.
     *
     * @return cache statistics including size and max capacity
     */
    public CacheStats getCacheStats() {
        return new CacheStats(cache.size(), maxCacheSize);
    }

    private String applyMode(MaskingMode mode, String value) {
        return switch (mode) {
            case REDACT -> REDACTED;
            case FULL -> ALPHANUMERIC_PATTERN.matcher(value).replaceAll("*");
            case PARTIAL -> applyPartialMasking(value);
            case TAIL -> applyTailMasking(value);
        };
    }

    private String applyPartialMasking(String value) {
        int atIdx = value.indexOf('@');
        if (atIdx > 0) {
            // Email-style partial masking: u***@domain.com
            String local = value.charAt(0) + "***";
            return local + value.substring(atIdx);
        }
        // Generic partial masking: half masked
        int half = Math.max(1, value.length() / 2);
        return "*".repeat(half) + value.substring(half);
    }

    private String applyTailMasking(String value) {
        if (value.length() <= 4) {
            return value;
        }
        int tailLength = 4;
        String tail = value.substring(value.length() - tailLength);
        String head = value.substring(0, value.length() - tailLength);
        // Use pre-compiled pattern for head
        String maskedHead = ALPHANUMERIC_PATTERN.matcher(head).replaceAll("*");
        return maskedHead + tail;
    }

    /**
     * Masking mode enumeration.
     */
    public enum MaskingMode {
        FULL,       // Replace all alphanumeric characters with *
        PARTIAL,    // Show first character and domain (for emails) or half the value
        TAIL,       // Show only last 4 characters
        REDACT      // Replace entire value with [REDACTED]
    }

    /**
     * Cache statistics record.
     */
    public record CacheStats(int currentSize, int maxSize) {
        public double utilization() {
            return maxSize > 0 ? (double) currentSize / maxSize : 0.0;
        }
    }
}
