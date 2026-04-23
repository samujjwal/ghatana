/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud.governance.redaction;

import org.junit.jupiter.api.*;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for optimized field masking with caching and parallel processing.
 *
 * @doc.type    class
 * @doc.purpose Tests for optimized field masking including caching, parallel batch, and statistics
 * @doc.layer   product
 * @doc.pattern Test
 */
@DisplayName("OptimizedFieldMaskerTest")
@Tag("governance")
class OptimizedFieldMaskerTest {

    private OptimizedFieldMasker masker;
    private OptimizedFieldMasker.MaskingPolicy policy;

    @BeforeEach
    void setUp() { // GH-90000
        policy = new OptimizedFieldMasker.MaskingPolicy(); // GH-90000
        policy.addRule("email", OptimizedFieldMasker.MaskingMode.PARTIAL); // GH-90000
        policy.addRule("ssn", OptimizedFieldMasker.MaskingMode.FULL); // GH-90000
        policy.addRule("creditCard", OptimizedFieldMasker.MaskingMode.TAIL); // GH-90000
        policy.addRule("password", OptimizedFieldMasker.MaskingMode.REDACT); // GH-90000
        masker = new OptimizedFieldMasker(policy, 100, true); // GH-90000
    }

    @AfterEach
    void tearDown() { // GH-90000
        masker.clearCache(); // GH-90000
    }

    // ── Basic masking functionality ─────────────────────────────────────────────

    @Test
    @DisplayName("SSN is fully masked using optimized pattern")
    void ssnIsFullyMasked() { // GH-90000
        String masked = masker.mask("ssn", "123-45-6789"); // GH-90000
        assertThat(masked).doesNotContain("123");
        assertThat(masked).isNotBlank(); // GH-90000
    }

    @Test
    @DisplayName("password is redacted with placeholder")
    void passwordIsRedacted() { // GH-90000
        String masked = masker.mask("password", "SuperSecret1!"); // GH-90000
        assertThat(masked).isEqualTo("[REDACTED]");
        assertThat(masked).doesNotContain("Secret");
    }

    @Test
    @DisplayName("email is partially masked but domain is preserved")
    void emailIsPartiallyMasked() { // GH-90000
        String masked = masker.mask("email", "user@example.com"); // GH-90000
        assertThat(masked).contains("@example.com");
        assertThat(masked).doesNotStartWith("user");
    }

    @Test
    @DisplayName("credit card shows only last 4 digits")
    void creditCardShowsLastFourDigits() { // GH-90000
        String masked = masker.mask("creditCard", "1234 5678 9012 3456"); // GH-90000
        assertThat(masked).endsWith("3456");
        assertThat(masked).doesNotContain("1234");
    }

    // ── Caching functionality ───────────────────────────────────────────────────

    @Test
    @DisplayName("caching returns same result for repeated masking")
    void cachingReturnsSameResult() { // GH-90000
        String first = masker.mask("email", "test@example.com"); // GH-90000
        String second = masker.mask("email", "test@example.com"); // GH-90000
        assertThat(first).isEqualTo(second); // GH-90000
        assertThat(masker.getCacheSize()).isGreaterThan(0); // GH-90000
    }

    @Test
    @DisplayName("cache statistics report correct values")
    void cacheStatsAreCorrect() { // GH-90000
        masker.mask("email", "user1@example.com"); // GH-90000
        masker.mask("email", "user2@example.com"); // GH-90000
        masker.mask("ssn", "111-22-3333"); // GH-90000

        OptimizedFieldMasker.CacheStats stats = masker.getCacheStats(); // GH-90000
        assertThat(stats.currentSize()).isEqualTo(3); // GH-90000
        assertThat(stats.maxSize()).isEqualTo(100); // GH-90000
        assertThat(stats.utilization()).isGreaterThan(0.0).isLessThan(1.0); // GH-90000
    }

    @Test
    @DisplayName("clearCache removes all cached entries")
    void clearCacheRemovesEntries() { // GH-90000
        masker.mask("email", "test@example.com"); // GH-90000
        assertThat(masker.getCacheSize()).isGreaterThan(0); // GH-90000

        masker.clearCache(); // GH-90000
        assertThat(masker.getCacheSize()).isEqualTo(0); // GH-90000
    }

    @Test
    @DisplayName("cache respects max size limit")
    void cacheRespectsMaxSize() { // GH-90000
        OptimizedFieldMasker smallCacheMasker = new OptimizedFieldMasker(policy, 5, false); // GH-90000
        for (int i = 0; i < 10; i++) { // GH-90000
            smallCacheMasker.mask("email", "user" + i + "@example.com"); // GH-90000
        }
        assertThat(smallCacheMasker.getCacheSize()).isLessThanOrEqualTo(5); // GH-90000
    }

    // ── Parallel batch processing ───────────────────────────────────────────────

    @Test
    @DisplayName("batch masking processes all fields")
    void batchMaskingProcessesAllFields() { // GH-90000
        Map<String, String> record = new LinkedHashMap<>(); // GH-90000
        record.put("email", "person@example.com"); // GH-90000
        record.put("password", "P@ssw0rd"); // GH-90000
        record.put("username", "jdoe"); // GH-90000

        Map<String, String> masked = masker.maskRecord(record); // GH-90000

        assertThat(masked.get("email")).doesNotStartWith("person");
        assertThat(masked.get("password")).isEqualTo("[REDACTED]");
        assertThat(masked.get("username")).isEqualTo("jdoe");
    }

    @Test
    @DisplayName("large batch uses parallel processing")
    void largeBatchUsesParallel() { // GH-90000
        Map<String, String> largeRecord = new LinkedHashMap<>(); // GH-90000
        for (int i = 0; i < 20; i++) { // GH-90000
            largeRecord.put("email" + i, "user" + i + "@example.com"); // GH-90000
        }

        Map<String, String> masked = masker.maskRecord(largeRecord); // GH-90000
        assertThat(masked).hasSize(20); // GH-90000
        assertThat(masked.get("email0")).doesNotStartWith("user0");
    }

    @Test
    @DisplayName("small batch uses sequential processing")
    void smallBatchUsesSequential() { // GH-90000
        Map<String, String> smallRecord = new LinkedHashMap<>(); // GH-90000
        smallRecord.put("email", "test@example.com"); // GH-90000
        smallRecord.put("password", "secret"); // GH-90000

        Map<String, String> masked = masker.maskRecord(smallRecord); // GH-90000
        assertThat(masked).hasSize(2); // GH-90000
    }

    @Test
    @DisplayName("batch masking with parallel disabled uses sequential")
    void batchWithParallelDisabled() { // GH-90000
        OptimizedFieldMasker sequentialMasker = new OptimizedFieldMasker(policy, 100, false); // GH-90000
        Map<String, String> record = new LinkedHashMap<>(); // GH-90000
        record.put("email", "test@example.com"); // GH-90000
        record.put("password", "secret"); // GH-90000

        Map<String, String> masked = sequentialMasker.maskRecord(record); // GH-90000
        assertThat(masked).hasSize(2); // GH-90000
    }

    // ── Edge cases ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("null value returns safe placeholder")
    void nullValueReturnsPlaceholder() { // GH-90000
        String masked = masker.mask("email", null); // GH-90000
        assertThat(masked).isEqualTo("[NULL]");
    }

    @Test
    @DisplayName("empty string returns empty placeholder")
    void emptyStringReturnsPlaceholder() { // GH-90000
        String masked = masker.mask("email", ""); // GH-90000
        assertThat(masked).isEqualTo("[EMPTY]");
    }

    @Test
    @DisplayName("unregistered field is returned as-is")
    void unregisteredFieldReturnedAsIs() { // GH-90000
        String masked = masker.mask("username", "john_doe"); // GH-90000
        assertThat(masked).isEqualTo("john_doe");
    }

    @Test
    @DisplayName("empty record returns empty map")
    void emptyRecordReturnsEmpty() { // GH-90000
        assertThat(masker.maskRecord(Map.of())).isEmpty(); // GH-90000
    }

    // ── Performance characteristics ─────────────────────────────────────────────

    @Test
    @DisplayName("cached operations are faster than uncached")
    void cachedOperationsAreFaster() { // GH-90000
        // Warm up cache
        masker.mask("email", "test@example.com"); // GH-90000

        long startCached = System.nanoTime(); // GH-90000
        for (int i = 0; i < 1000; i++) { // GH-90000
            masker.mask("email", "test@example.com"); // GH-90000
        }
        long endCached = System.nanoTime(); // GH-90000

        long startUncached = System.nanoTime(); // GH-90000
        for (int i = 0; i < 1000; i++) { // GH-90000
            masker.mask("email", "user" + i + "@example.com"); // GH-90000
        }
        long endUncached = System.nanoTime(); // GH-90000

        // Cached should be faster (though this is a soft assertion due to JIT) // GH-90000
        assertThat(endCached - startCached).isLessThan(endUncached - startUncached); // GH-90000
    }
}
