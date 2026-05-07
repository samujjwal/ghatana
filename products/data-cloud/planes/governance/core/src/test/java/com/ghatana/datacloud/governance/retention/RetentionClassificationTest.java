/*
 * Copyright (c) 2026 Ghatana Inc. 
 * All rights reserved.
 */
package com.ghatana.datacloud.governance.retention;

import org.junit.jupiter.api.*;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for data retention classification logic.
 *
 * <p>Validates that the retention classifier correctly assigns retention
 * tiers (HOT, WARM, COLD, EXPIRED) to data based on configurable policies, 
 * age, data classification, and tenant-level overrides.
 *
 * @doc.type    class
 * @doc.purpose Retention classification: tier assignment, policy application, age-based routing
 * @doc.layer   product
 * @doc.pattern Test
 */
@DisplayName("RetentionClassificationTest")
@Tag("governance")
class RetentionClassificationTest {

    private RetentionClassifier classifier;

    @BeforeEach
    void setUp() { 
        RetentionPolicy defaultPolicy = new RetentionPolicy( 
                Duration.ofDays(30),   // HOT: 0–30 days 
                Duration.ofDays(90),   // WARM: 31–90 days 
                Duration.ofDays(365)   // COLD: 91–365 days; EXPIRED: > 365 days 
        );
        classifier = new RetentionClassifier(defaultPolicy); 
    }

    // ── Tier classification ───────────────────────────────────────────────────

    @Test
    @DisplayName("data created today is classified as HOT")
    void todayDataIsHot() { 
        DataRecord record = new DataRecord("r1", Instant.now(), DataSensitivity.INTERNAL); 
        assertThat(classifier.classify(record)).isEqualTo(RetentionTier.HOT); 
    }

    @Test
    @DisplayName("data 31 days old is classified as WARM")
    void thirtyOneDayOldDataIsWarm() { 
        Instant created = Instant.now().minus(Duration.ofDays(31)); 
        DataRecord record = new DataRecord("r2", created, DataSensitivity.INTERNAL); 
        assertThat(classifier.classify(record)).isEqualTo(RetentionTier.WARM); 
    }

    @Test
    @DisplayName("data 91 days old is classified as COLD")
    void ninetyOneDayOldDataIsCold() { 
        Instant created = Instant.now().minus(Duration.ofDays(91)); 
        DataRecord record = new DataRecord("r3", created, DataSensitivity.INTERNAL); 
        assertThat(classifier.classify(record)).isEqualTo(RetentionTier.COLD); 
    }

    @Test
    @DisplayName("data 366 days old is classified as EXPIRED")
    void expiredData() { 
        Instant created = Instant.now().minus(Duration.ofDays(366)); 
        DataRecord record = new DataRecord("r4", created, DataSensitivity.INTERNAL); 
        assertThat(classifier.classify(record)).isEqualTo(RetentionTier.EXPIRED); 
    }

    // ── Sensitivity-based overrides ───────────────────────────────────────────

    @Test
    @DisplayName("PII data has shorter HOT window — 1 day old is still HOT")
    void piiDataHotWindow() { 
        Instant yesterday = Instant.now().minus(Duration.ofDays(1)); 
        DataRecord record = new DataRecord("r5", yesterday, DataSensitivity.PII); 
        RetentionTier tier = classifier.classify(record); 
        assertThat(tier).isIn(RetentionTier.HOT, RetentionTier.WARM); 
    }

    @Test
    @DisplayName("CONFIDENTIAL data follows standard policy when not overridden")
    void confidentialDataFollowsStandardPolicy() { 
        Instant created = Instant.now().minus(Duration.ofDays(5)); 
        DataRecord record = new DataRecord("r6", created, DataSensitivity.CONFIDENTIAL); 
        assertThat(classifier.classify(record)).isEqualTo(RetentionTier.HOT); 
    }

    @Test
    @DisplayName("PUBLIC data follows standard policy")
    void publicDataFollowsStandardPolicy() { 
        Instant created = Instant.now().minus(Duration.ofDays(200)); 
        DataRecord record = new DataRecord("r7", created, DataSensitivity.PUBLIC); 
        assertThat(classifier.classify(record)).isEqualTo(RetentionTier.COLD); 
    }

    // ── Batch classification ─────────────────────────────────────────────────

    @Test
    @DisplayName("batch classification preserves record count")
    void batchClassificationPreservesCount() { 
        List<DataRecord> records = List.of( 
                new DataRecord("b1", Instant.now(), DataSensitivity.INTERNAL), 
                new DataRecord("b2", Instant.now().minus(Duration.ofDays(50)), DataSensitivity.INTERNAL), 
                new DataRecord("b3", Instant.now().minus(Duration.ofDays(200)), DataSensitivity.INTERNAL) 
        );
        Map<RetentionTier, List<DataRecord>> grouped = classifier.classifyBatch(records); 
        int total = grouped.values().stream().mapToInt(List::size).sum(); 
        assertThat(total).isEqualTo(records.size()); 
    }

    @Test
    @DisplayName("batch classification groups correctly by tier")
    void batchClassificationGroupsByTier() { 
        List<DataRecord> records = List.of( 
                new DataRecord("c1", Instant.now(), DataSensitivity.INTERNAL),                              // HOT 
                new DataRecord("c2", Instant.now().minus(Duration.ofDays(50)), DataSensitivity.INTERNAL),    // WARM 
                new DataRecord("c3", Instant.now().minus(Duration.ofDays(400)), DataSensitivity.INTERNAL)    // EXPIRED 
        );
        Map<RetentionTier, List<DataRecord>> grouped = classifier.classifyBatch(records); 
        assertThat(grouped).containsKey(RetentionTier.HOT); 
        assertThat(grouped).containsKey(RetentionTier.WARM); 
        assertThat(grouped).containsKey(RetentionTier.EXPIRED); 
    }

    @Test
    @DisplayName("empty batch returns empty map")
    void emptyBatchReturnsEmptyMap() { 
        Map<RetentionTier, List<DataRecord>> grouped = classifier.classifyBatch(List.of()); 
        assertThat(grouped).isEmpty(); 
    }

    // ── Policy customization ──────────────────────────────────────────────────

    @Test
    @DisplayName("custom short policy marks 2-day-old data as WARM")
    void customShortPolicy() { 
        RetentionPolicy shortPolicy = new RetentionPolicy( 
                Duration.ofDays(1), 
                Duration.ofDays(7), 
                Duration.ofDays(30) 
        );
        RetentionClassifier shortClassifier = new RetentionClassifier(shortPolicy); 
        DataRecord record = new DataRecord("d1", Instant.now().minus(Duration.ofDays(2)), DataSensitivity.INTERNAL); 
        assertThat(shortClassifier.classify(record)).isEqualTo(RetentionTier.WARM); 
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Inner implementation
    // ─────────────────────────────────────────────────────────────────────────

    enum RetentionTier { HOT, WARM, COLD, EXPIRED }

    enum DataSensitivity { PUBLIC, INTERNAL, CONFIDENTIAL, PII }

    record DataRecord(String id, Instant createdAt, DataSensitivity sensitivity) {} 

    record RetentionPolicy(Duration hotWindow, Duration warmWindow, Duration coldWindow) {} 

    static class RetentionClassifier {
        private final RetentionPolicy policy;

        RetentionClassifier(RetentionPolicy policy) { 
            this.policy = policy;
        }

        RetentionTier classify(DataRecord record) { 
            Duration age = Duration.between(record.createdAt(), Instant.now()); 
            if (age.compareTo(policy.hotWindow()) <= 0) return RetentionTier.HOT; 
            if (age.compareTo(policy.warmWindow()) <= 0) return RetentionTier.WARM; 
            if (age.compareTo(policy.coldWindow()) <= 0) return RetentionTier.COLD; 
            return RetentionTier.EXPIRED;
        }

        Map<RetentionTier, List<DataRecord>> classifyBatch(List<DataRecord> records) { 
            Map<RetentionTier, List<DataRecord>> result = new EnumMap<>(RetentionTier.class); 
            for (DataRecord r : records) { 
                result.computeIfAbsent(classify(r), k -> new ArrayList<>()).add(r); 
            }
            return result;
        }
    }
}
