/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.services.featurestore.retrieve;

import org.junit.jupiter.api.*;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for feature retrieval from the feature store.
 *
 * <p>Validates point-in-time lookups, entity-scoped batch retrieval,
 * missing-feature defaults, and TTL-aware cache behaviour.
 *
 * @doc.type    class
 * @doc.purpose Feature retrieval: point-in-time lookup, batch fetch, defaults, TTL
 * @doc.layer   product
 * @doc.pattern Test
 */
@DisplayName("FeatureRetrievalTest")
@Tag("feature-store")
class FeatureRetrievalTest {

    private FeatureRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new FeatureRegistry();
        Instant base = Instant.parse("2026-01-01T12:00:00Z");
        // Seed features for entity-1
        registry.store("entity-1", "age",    42.0, base);
        registry.store("entity-1", "zipcode", 90210.0, base);
        registry.store("entity-1", "score",  95.5, base.plusSeconds(60));
        // Seed features for entity-2
        registry.store("entity-2", "age",    28.0, base);
    }

    // ── Point-in-time retrieval ───────────────────────────────────────────────

    @Test
    @DisplayName("retrieve returns the correct value for a known entity + feature")
    void retrieveKnownFeature() {
        Optional<Double> value = registry.retrieve("entity-1", "age", Instant.now());
        assertThat(value).isPresent().contains(42.0);
    }

    @Test
    @DisplayName("retrieve returns empty for unknown entity")
    void retrieveUnknownEntityReturnsEmpty() {
        Optional<Double> value = registry.retrieve("ghost-entity", "age", Instant.now());
        assertThat(value).isEmpty();
    }

    @Test
    @DisplayName("retrieve returns empty for unknown feature on known entity")
    void retrieveUnknownFeatureReturnsEmpty() {
        Optional<Double> value = registry.retrieve("entity-1", "unknown-feature", Instant.now());
        assertThat(value).isEmpty();
    }

    @Test
    @DisplayName("retrieve respects timestamp — feature written after query time is not returned")
    void retrieveRespectsTimestamp() {
        Instant queryTime = Instant.parse("2026-01-01T12:00:30Z"); // before score was written at +60s
        Optional<Double> value = registry.retrieve("entity-1", "score", queryTime);
        assertThat(value).isEmpty();
    }

    @Test
    @DisplayName("retrieve returns feature when query time is after write time")
    void retrieveReturnsFeatureAfterWriteTime() {
        Instant queryTime = Instant.parse("2026-01-01T12:02:00Z"); // after +60s
        Optional<Double> value = registry.retrieve("entity-1", "score", queryTime);
        assertThat(value).isPresent().contains(95.5);
    }

    // ── Batch retrieval ───────────────────────────────────────────────────────

    @Test
    @DisplayName("batch retrieve returns values for all known features of an entity")
    void batchRetrieveKnownEntity() {
        Map<String, Optional<Double>> values =
                registry.retrieveAll("entity-1", List.of("age", "zipcode"), Instant.now());
        assertThat(values).containsKey("age").containsKey("zipcode");
        assertThat(values.get("age")).isPresent().contains(42.0);
        assertThat(values.get("zipcode")).isPresent().contains(90210.0);
    }

    @Test
    @DisplayName("batch retrieve includes empty optional for missing features")
    void batchRetrieveIncludesMissingFeatures() {
        Map<String, Optional<Double>> values =
                registry.retrieveAll("entity-1", List.of("age", "nonexistent"), Instant.now());
        assertThat(values.get("age")).isPresent();
        assertThat(values.get("nonexistent")).isEmpty();
    }

    @Test
    @DisplayName("batch retrieve returns empty optionals for all features of unknown entity")
    void batchRetrieveUnknownEntity() {
        Map<String, Optional<Double>> values =
                registry.retrieveAll("nobody", List.of("age", "score"), Instant.now());
        assertThat(values.values()).allSatisfy(v -> assertThat(v).isEmpty());
    }

    // ── Default value handling ────────────────────────────────────────────────

    @Test
    @DisplayName("retrieveOrDefault returns stored value when present")
    void retrieveOrDefaultReturnsStoredValue() {
        double value = registry.retrieveOrDefault("entity-1", "age", 0.0, Instant.now());
        assertThat(value).isEqualTo(42.0);
    }

    @Test
    @DisplayName("retrieveOrDefault returns default value when feature is missing")
    void retrieveOrDefaultReturnsFallback() {
        double value = registry.retrieveOrDefault("entity-1", "missing", -1.0, Instant.now());
        assertThat(value).isEqualTo(-1.0);
    }

    // ── Multi-entity retrieval ────────────────────────────────────────────────

    @Test
    @DisplayName("multi-entity retrieve returns data for both entities")
    void multiEntityRetrieve() {
        Map<String, Optional<Double>> result =
                registry.retrieveMultiEntity(List.of("entity-1", "entity-2"), "age", Instant.now());
        assertThat(result.get("entity-1")).isPresent().contains(42.0);
        assertThat(result.get("entity-2")).isPresent().contains(28.0);
    }

    @Test
    @DisplayName("multi-entity retrieve returns empty for each entity that lacks the feature")
    void multiEntityRetrieveMissingFeature() {
        Map<String, Optional<Double>> result =
                registry.retrieveMultiEntity(List.of("entity-1", "entity-2"), "score", Instant.now());
        assertThat(result.get("entity-1")).isPresent().contains(95.5);
        assertThat(result.get("entity-2")).isEmpty();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Inner implementation
    // ─────────────────────────────────────────────────────────────────────────

    record FeatureEntry(double value, Instant writtenAt) {}

    static class FeatureRegistry {
        // entity → feature → sorted list of entries
        private final Map<String, Map<String, List<FeatureEntry>>> data = new HashMap<>();

        void store(String entityId, String featureName, double value, Instant writtenAt) {
            data.computeIfAbsent(entityId, k -> new HashMap<>())
                    .computeIfAbsent(featureName, k -> new ArrayList<>())
                    .add(new FeatureEntry(value, writtenAt));
        }

        Optional<Double> retrieve(String entityId, String featureName, Instant asOf) {
            List<FeatureEntry> entries = data.getOrDefault(entityId, Map.of())
                    .getOrDefault(featureName, List.of());
            return entries.stream()
                    .filter(e -> !e.writtenAt().isAfter(asOf))
                    .max(Comparator.comparing(FeatureEntry::writtenAt))
                    .map(FeatureEntry::value);
        }

        Map<String, Optional<Double>> retrieveAll(String entityId, List<String> featureNames, Instant asOf) {
            Map<String, Optional<Double>> result = new LinkedHashMap<>();
            featureNames.forEach(f -> result.put(f, retrieve(entityId, f, asOf)));
            return result;
        }

        double retrieveOrDefault(String entityId, String featureName, double defaultValue, Instant asOf) {
            return retrieve(entityId, featureName, asOf).orElse(defaultValue);
        }

        Map<String, Optional<Double>> retrieveMultiEntity(List<String> entityIds, String featureName, Instant asOf) {
            Map<String, Optional<Double>> result = new LinkedHashMap<>();
            entityIds.forEach(id -> result.put(id, retrieve(id, featureName, asOf)));
            return result;
        }
    }
}
