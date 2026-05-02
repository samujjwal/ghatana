/*
 * Copyright (c) 2026 Ghatana Inc. 
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
    void setUp() { 
        policy = new OptimizedFieldMasker.MaskingPolicy(); 
        policy.addRule("email", OptimizedFieldMasker.MaskingMode.PARTIAL); 
        policy.addRule("ssn", OptimizedFieldMasker.MaskingMode.FULL); 
        policy.addRule("creditCard", OptimizedFieldMasker.MaskingMode.TAIL); 
        policy.addRule("password", OptimizedFieldMasker.MaskingMode.REDACT); 
        masker = new OptimizedFieldMasker(policy, 100, true); 
    }

    @AfterEach
    void tearDown() { 
        masker.clearCache(); 
    }

    // ── Basic masking functionality ─────────────────────────────────────────────

    @Test
    @DisplayName("SSN is fully masked using optimized pattern")
    void ssnIsFullyMasked() { 
        String masked = masker.mask("ssn", "123-45-6789"); 
        assertThat(masked).doesNotContain("123");
        assertThat(masked).isNotBlank(); 
    }

    @Test
    @DisplayName("password is redacted with placeholder")
    void passwordIsRedacted() { 
        String masked = masker.mask("password", "SuperSecret1!"); 
        assertThat(masked).isEqualTo("[REDACTED]");
        assertThat(masked).doesNotContain("Secret");
    }

    @Test
    @DisplayName("email is partially masked but domain is preserved")
    void emailIsPartiallyMasked() { 
        String masked = masker.mask("email", "user@example.com"); 
        assertThat(masked).contains("@example.com");
        assertThat(masked).doesNotStartWith("user");
    }

    @Test
    @DisplayName("credit card shows only last 4 digits")
    void creditCardShowsLastFourDigits() { 
        String masked = masker.mask("creditCard", "1234 5678 9012 3456"); 
        assertThat(masked).endsWith("3456");
        assertThat(masked).doesNotContain("1234");
    }

    // ── Caching functionality ───────────────────────────────────────────────────

    @Test
    @DisplayName("caching returns same result for repeated masking")
    void cachingReturnsSameResult() { 
        String first = masker.mask("email", "test@example.com"); 
        String second = masker.mask("email", "test@example.com"); 
        assertThat(first).isEqualTo(second); 
        assertThat(masker.getCacheSize()).isGreaterThan(0); 
    }

    @Test
    @DisplayName("cache statistics report correct values")
    void cacheStatsAreCorrect() { 
        masker.mask("email", "user1@example.com"); 
        masker.mask("email", "user2@example.com"); 
        masker.mask("ssn", "111-22-3333"); 

        OptimizedFieldMasker.CacheStats stats = masker.getCacheStats(); 
        assertThat(stats.currentSize()).isEqualTo(3); 
        assertThat(stats.maxSize()).isEqualTo(100); 
        assertThat(stats.utilization()).isGreaterThan(0.0).isLessThan(1.0); 
    }

    @Test
    @DisplayName("clearCache removes all cached entries")
    void clearCacheRemovesEntries() { 
        masker.mask("email", "test@example.com"); 
        assertThat(masker.getCacheSize()).isGreaterThan(0); 

        masker.clearCache(); 
        assertThat(masker.getCacheSize()).isEqualTo(0); 
    }

    @Test
    @DisplayName("cache respects max size limit")
    void cacheRespectsMaxSize() { 
        OptimizedFieldMasker smallCacheMasker = new OptimizedFieldMasker(policy, 5, false); 
        for (int i = 0; i < 10; i++) { 
            smallCacheMasker.mask("email", "user" + i + "@example.com"); 
        }
        assertThat(smallCacheMasker.getCacheSize()).isLessThanOrEqualTo(5); 
    }

    // ── Parallel batch processing ───────────────────────────────────────────────

    @Test
    @DisplayName("batch masking processes all fields")
    void batchMaskingProcessesAllFields() { 
        Map<String, String> record = new LinkedHashMap<>(); 
        record.put("email", "person@example.com"); 
        record.put("password", "P@ssw0rd"); 
        record.put("username", "jdoe"); 

        Map<String, String> masked = masker.maskRecord(record); 

        assertThat(masked.get("email")).doesNotStartWith("person");
        assertThat(masked.get("password")).isEqualTo("[REDACTED]");
        assertThat(masked.get("username")).isEqualTo("jdoe");
    }

    @Test
    @DisplayName("large batch uses parallel processing")
    void largeBatchUsesParallel() { 
        Map<String, String> largeRecord = new LinkedHashMap<>(); 
        for (int i = 0; i < 20; i++) { 
            largeRecord.put("email" + i, "user" + i + "@example.com"); 
        }

        Map<String, String> masked = masker.maskRecord(largeRecord); 
        assertThat(masked).hasSize(20); 
        assertThat(masked.get("email0")).doesNotStartWith("user0");
    }

    @Test
    @DisplayName("small batch uses sequential processing")
    void smallBatchUsesSequential() { 
        Map<String, String> smallRecord = new LinkedHashMap<>(); 
        smallRecord.put("email", "test@example.com"); 
        smallRecord.put("password", "secret"); 

        Map<String, String> masked = masker.maskRecord(smallRecord); 
        assertThat(masked).hasSize(2); 
    }

    @Test
    @DisplayName("batch masking with parallel disabled uses sequential")
    void batchWithParallelDisabled() { 
        OptimizedFieldMasker sequentialMasker = new OptimizedFieldMasker(policy, 100, false); 
        Map<String, String> record = new LinkedHashMap<>(); 
        record.put("email", "test@example.com"); 
        record.put("password", "secret"); 

        Map<String, String> masked = sequentialMasker.maskRecord(record); 
        assertThat(masked).hasSize(2); 
    }

    // ── Edge cases ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("null value returns safe placeholder")
    void nullValueReturnsPlaceholder() { 
        String masked = masker.mask("email", null); 
        assertThat(masked).isEqualTo("[NULL]");
    }

    @Test
    @DisplayName("empty string returns empty placeholder")
    void emptyStringReturnsPlaceholder() { 
        String masked = masker.mask("email", ""); 
        assertThat(masked).isEqualTo("[EMPTY]");
    }

    @Test
    @DisplayName("unregistered field is returned as-is")
    void unregisteredFieldReturnedAsIs() { 
        String masked = masker.mask("username", "john_doe"); 
        assertThat(masked).isEqualTo("john_doe");
    }

    @Test
    @DisplayName("empty record returns empty map")
    void emptyRecordReturnsEmpty() { 
        assertThat(masker.maskRecord(Map.of())).isEmpty(); 
    }

    // ── Performance characteristics ─────────────────────────────────────────────

    @Test
    @DisplayName("cached operations are faster than uncached")
    void cachedOperationsAreFaster() { 
        // Warm up cache
        masker.mask("email", "test@example.com"); 

        long startCached = System.nanoTime(); 
        for (int i = 0; i < 1000; i++) { 
            masker.mask("email", "test@example.com"); 
        }
        long endCached = System.nanoTime(); 

        long startUncached = System.nanoTime(); 
        for (int i = 0; i < 1000; i++) { 
            masker.mask("email", "user" + i + "@example.com"); 
        }
        long endUncached = System.nanoTime(); 

        // Cached should be faster (though this is a soft assertion due to JIT) 
        assertThat(endCached - startCached).isLessThan(endUncached - startUncached); 
    }
}
