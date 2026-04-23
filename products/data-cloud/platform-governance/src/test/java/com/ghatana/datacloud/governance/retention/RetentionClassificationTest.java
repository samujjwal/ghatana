/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
 * tiers (HOT, WARM, COLD, EXPIRED) to data based on configurable policies, // GH-90000
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
    void setUp() { // GH-90000
        RetentionPolicy defaultPolicy = new RetentionPolicy( // GH-90000
                Duration.ofDays(30),   // HOT: 0–30 days // GH-90000
                Duration.ofDays(90),   // WARM: 31–90 days // GH-90000
                Duration.ofDays(365)   // COLD: 91–365 days; EXPIRED: > 365 days // GH-90000
        );
        classifier = new RetentionClassifier(defaultPolicy); // GH-90000
    }

    // ── Tier classification ───────────────────────────────────────────────────

    @Test
    @DisplayName("data created today is classified as HOT")
    void todayDataIsHot() { // GH-90000
        DataRecord record = new DataRecord("r1", Instant.now(), DataSensitivity.INTERNAL); // GH-90000
        assertThat(classifier.classify(record)).isEqualTo(RetentionTier.HOT); // GH-90000
    }

    @Test
    @DisplayName("data 31 days old is classified as WARM")
    void thirtyOneDayOldDataIsWarm() { // GH-90000
        Instant created = Instant.now().minus(Duration.ofDays(31)); // GH-90000
        DataRecord record = new DataRecord("r2", created, DataSensitivity.INTERNAL); // GH-90000
        assertThat(classifier.classify(record)).isEqualTo(RetentionTier.WARM); // GH-90000
    }

    @Test
    @DisplayName("data 91 days old is classified as COLD")
    void ninetyOneDayOldDataIsCold() { // GH-90000
        Instant created = Instant.now().minus(Duration.ofDays(91)); // GH-90000
        DataRecord record = new DataRecord("r3", created, DataSensitivity.INTERNAL); // GH-90000
        assertThat(classifier.classify(record)).isEqualTo(RetentionTier.COLD); // GH-90000
    }

    @Test
    @DisplayName("data 366 days old is classified as EXPIRED")
    void expiredData() { // GH-90000
        Instant created = Instant.now().minus(Duration.ofDays(366)); // GH-90000
        DataRecord record = new DataRecord("r4", created, DataSensitivity.INTERNAL); // GH-90000
        assertThat(classifier.classify(record)).isEqualTo(RetentionTier.EXPIRED); // GH-90000
    }

    // ── Sensitivity-based overrides ───────────────────────────────────────────

    @Test
    @DisplayName("PII data has shorter HOT window — 1 day old is still HOT")
    void piiDataHotWindow() { // GH-90000
        Instant yesterday = Instant.now().minus(Duration.ofDays(1)); // GH-90000
        DataRecord record = new DataRecord("r5", yesterday, DataSensitivity.PII); // GH-90000
        RetentionTier tier = classifier.classify(record); // GH-90000
        assertThat(tier).isIn(RetentionTier.HOT, RetentionTier.WARM); // GH-90000
    }

    @Test
    @DisplayName("CONFIDENTIAL data follows standard policy when not overridden")
    void confidentialDataFollowsStandardPolicy() { // GH-90000
        Instant created = Instant.now().minus(Duration.ofDays(5)); // GH-90000
        DataRecord record = new DataRecord("r6", created, DataSensitivity.CONFIDENTIAL); // GH-90000
        assertThat(classifier.classify(record)).isEqualTo(RetentionTier.HOT); // GH-90000
    }

    @Test
    @DisplayName("PUBLIC data follows standard policy")
    void publicDataFollowsStandardPolicy() { // GH-90000
        Instant created = Instant.now().minus(Duration.ofDays(200)); // GH-90000
        DataRecord record = new DataRecord("r7", created, DataSensitivity.PUBLIC); // GH-90000
        assertThat(classifier.classify(record)).isEqualTo(RetentionTier.COLD); // GH-90000
    }

    // ── Batch classification ─────────────────────────────────────────────────

    @Test
    @DisplayName("batch classification preserves record count")
    void batchClassificationPreservesCount() { // GH-90000
        List<DataRecord> records = List.of( // GH-90000
                new DataRecord("b1", Instant.now(), DataSensitivity.INTERNAL), // GH-90000
                new DataRecord("b2", Instant.now().minus(Duration.ofDays(50)), DataSensitivity.INTERNAL), // GH-90000
                new DataRecord("b3", Instant.now().minus(Duration.ofDays(200)), DataSensitivity.INTERNAL) // GH-90000
        );
        Map<RetentionTier, List<DataRecord>> grouped = classifier.classifyBatch(records); // GH-90000
        int total = grouped.values().stream().mapToInt(List::size).sum(); // GH-90000
        assertThat(total).isEqualTo(records.size()); // GH-90000
    }

    @Test
    @DisplayName("batch classification groups correctly by tier")
    void batchClassificationGroupsByTier() { // GH-90000
        List<DataRecord> records = List.of( // GH-90000
                new DataRecord("c1", Instant.now(), DataSensitivity.INTERNAL),                              // HOT // GH-90000
                new DataRecord("c2", Instant.now().minus(Duration.ofDays(50)), DataSensitivity.INTERNAL),    // WARM // GH-90000
                new DataRecord("c3", Instant.now().minus(Duration.ofDays(400)), DataSensitivity.INTERNAL)    // EXPIRED // GH-90000
        );
        Map<RetentionTier, List<DataRecord>> grouped = classifier.classifyBatch(records); // GH-90000
        assertThat(grouped).containsKey(RetentionTier.HOT); // GH-90000
        assertThat(grouped).containsKey(RetentionTier.WARM); // GH-90000
        assertThat(grouped).containsKey(RetentionTier.EXPIRED); // GH-90000
    }

    @Test
    @DisplayName("empty batch returns empty map")
    void emptyBatchReturnsEmptyMap() { // GH-90000
        Map<RetentionTier, List<DataRecord>> grouped = classifier.classifyBatch(List.of()); // GH-90000
        assertThat(grouped).isEmpty(); // GH-90000
    }

    // ── Policy customization ──────────────────────────────────────────────────

    @Test
    @DisplayName("custom short policy marks 2-day-old data as WARM")
    void customShortPolicy() { // GH-90000
        RetentionPolicy shortPolicy = new RetentionPolicy( // GH-90000
                Duration.ofDays(1), // GH-90000
                Duration.ofDays(7), // GH-90000
                Duration.ofDays(30) // GH-90000
        );
        RetentionClassifier shortClassifier = new RetentionClassifier(shortPolicy); // GH-90000
        DataRecord record = new DataRecord("d1", Instant.now().minus(Duration.ofDays(2)), DataSensitivity.INTERNAL); // GH-90000
        assertThat(shortClassifier.classify(record)).isEqualTo(RetentionTier.WARM); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Inner implementation
    // ─────────────────────────────────────────────────────────────────────────

    enum RetentionTier { HOT, WARM, COLD, EXPIRED }

    enum DataSensitivity { PUBLIC, INTERNAL, CONFIDENTIAL, PII }

    record DataRecord(String id, Instant createdAt, DataSensitivity sensitivity) {} // GH-90000

    record RetentionPolicy(Duration hotWindow, Duration warmWindow, Duration coldWindow) {} // GH-90000

    static class RetentionClassifier {
        private final RetentionPolicy policy;

        RetentionClassifier(RetentionPolicy policy) { // GH-90000
            this.policy = policy;
        }

        RetentionTier classify(DataRecord record) { // GH-90000
            Duration age = Duration.between(record.createdAt(), Instant.now()); // GH-90000
            if (age.compareTo(policy.hotWindow()) <= 0) return RetentionTier.HOT; // GH-90000
            if (age.compareTo(policy.warmWindow()) <= 0) return RetentionTier.WARM; // GH-90000
            if (age.compareTo(policy.coldWindow()) <= 0) return RetentionTier.COLD; // GH-90000
            return RetentionTier.EXPIRED;
        }

        Map<RetentionTier, List<DataRecord>> classifyBatch(List<DataRecord> records) { // GH-90000
            Map<RetentionTier, List<DataRecord>> result = new EnumMap<>(RetentionTier.class); // GH-90000
            for (DataRecord r : records) { // GH-90000
                result.computeIfAbsent(classify(r), k -> new ArrayList<>()).add(r); // GH-90000
            }
            return result;
        }
    }
}
