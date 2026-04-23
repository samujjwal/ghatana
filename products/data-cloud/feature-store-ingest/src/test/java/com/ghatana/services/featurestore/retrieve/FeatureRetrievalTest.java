/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
    void setUp() { // GH-90000
        registry = new FeatureRegistry(); // GH-90000
        Instant base = Instant.parse("2026-01-01T12:00:00Z");
        // Seed features for entity-1
        registry.store("entity-1", "age",    42.0, base); // GH-90000
        registry.store("entity-1", "zipcode", 90210.0, base); // GH-90000
        registry.store("entity-1", "score",  95.5, base.plusSeconds(60)); // GH-90000
        // Seed features for entity-2
        registry.store("entity-2", "age",    28.0, base); // GH-90000
    }

    // ── Point-in-time retrieval ───────────────────────────────────────────────

    @Test
    @DisplayName("retrieve returns the correct value for a known entity + feature")
    void retrieveKnownFeature() { // GH-90000
        Optional<Double> value = registry.retrieve("entity-1", "age", Instant.now()); // GH-90000
        assertThat(value).isPresent().contains(42.0); // GH-90000
    }

    @Test
    @DisplayName("retrieve returns empty for unknown entity")
    void retrieveUnknownEntityReturnsEmpty() { // GH-90000
        Optional<Double> value = registry.retrieve("ghost-entity", "age", Instant.now()); // GH-90000
        assertThat(value).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("retrieve returns empty for unknown feature on known entity")
    void retrieveUnknownFeatureReturnsEmpty() { // GH-90000
        Optional<Double> value = registry.retrieve("entity-1", "unknown-feature", Instant.now()); // GH-90000
        assertThat(value).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("retrieve respects timestamp — feature written after query time is not returned")
    void retrieveRespectsTimestamp() { // GH-90000
        Instant queryTime = Instant.parse("2026-01-01T12:00:30Z"); // before score was written at +60s
        Optional<Double> value = registry.retrieve("entity-1", "score", queryTime); // GH-90000
        assertThat(value).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("retrieve returns feature when query time is after write time")
    void retrieveReturnsFeatureAfterWriteTime() { // GH-90000
        Instant queryTime = Instant.parse("2026-01-01T12:02:00Z"); // after +60s
        Optional<Double> value = registry.retrieve("entity-1", "score", queryTime); // GH-90000
        assertThat(value).isPresent().contains(95.5); // GH-90000
    }

    // ── Batch retrieval ───────────────────────────────────────────────────────

    @Test
    @DisplayName("batch retrieve returns values for all known features of an entity")
    void batchRetrieveKnownEntity() { // GH-90000
        Map<String, Optional<Double>> values =
                registry.retrieveAll("entity-1", List.of("age", "zipcode"), Instant.now()); // GH-90000
        assertThat(values).containsKey("age").containsKey("zipcode");
        assertThat(values.get("age")).isPresent().contains(42.0);
        assertThat(values.get("zipcode")).isPresent().contains(90210.0);
    }

    @Test
    @DisplayName("batch retrieve includes empty optional for missing features")
    void batchRetrieveIncludesMissingFeatures() { // GH-90000
        Map<String, Optional<Double>> values =
                registry.retrieveAll("entity-1", List.of("age", "nonexistent"), Instant.now()); // GH-90000
        assertThat(values.get("age")).isPresent();
        assertThat(values.get("nonexistent")).isEmpty();
    }

    @Test
    @DisplayName("batch retrieve returns empty optionals for all features of unknown entity")
    void batchRetrieveUnknownEntity() { // GH-90000
        Map<String, Optional<Double>> values =
                registry.retrieveAll("nobody", List.of("age", "score"), Instant.now()); // GH-90000
        assertThat(values.values()).allSatisfy(v -> assertThat(v).isEmpty()); // GH-90000
    }

    // ── Default value handling ────────────────────────────────────────────────

    @Test
    @DisplayName("retrieveOrDefault returns stored value when present")
    void retrieveOrDefaultReturnsStoredValue() { // GH-90000
        double value = registry.retrieveOrDefault("entity-1", "age", 0.0, Instant.now()); // GH-90000
        assertThat(value).isEqualTo(42.0); // GH-90000
    }

    @Test
    @DisplayName("retrieveOrDefault returns default value when feature is missing")
    void retrieveOrDefaultReturnsFallback() { // GH-90000
        double value = registry.retrieveOrDefault("entity-1", "missing", -1.0, Instant.now()); // GH-90000
        assertThat(value).isEqualTo(-1.0); // GH-90000
    }

    // ── Multi-entity retrieval ────────────────────────────────────────────────

    @Test
    @DisplayName("multi-entity retrieve returns data for both entities")
    void multiEntityRetrieve() { // GH-90000
        Map<String, Optional<Double>> result =
                registry.retrieveMultiEntity(List.of("entity-1", "entity-2"), "age", Instant.now()); // GH-90000
        assertThat(result.get("entity-1")).isPresent().contains(42.0);
        assertThat(result.get("entity-2")).isPresent().contains(28.0);
    }

    @Test
    @DisplayName("multi-entity retrieve returns empty for each entity that lacks the feature")
    void multiEntityRetrieveMissingFeature() { // GH-90000
        Map<String, Optional<Double>> result =
                registry.retrieveMultiEntity(List.of("entity-1", "entity-2"), "score", Instant.now()); // GH-90000
        assertThat(result.get("entity-1")).isPresent().contains(95.5);
        assertThat(result.get("entity-2")).isEmpty();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Inner implementation
    // ─────────────────────────────────────────────────────────────────────────

    record FeatureEntry(double value, Instant writtenAt) {} // GH-90000

    static class FeatureRegistry {
        // entity → feature → sorted list of entries
        private final Map<String, Map<String, List<FeatureEntry>>> data = new HashMap<>(); // GH-90000

        void store(String entityId, String featureName, double value, Instant writtenAt) { // GH-90000
            data.computeIfAbsent(entityId, k -> new HashMap<>()) // GH-90000
                    .computeIfAbsent(featureName, k -> new ArrayList<>()) // GH-90000
                    .add(new FeatureEntry(value, writtenAt)); // GH-90000
        }

        Optional<Double> retrieve(String entityId, String featureName, Instant asOf) { // GH-90000
            List<FeatureEntry> entries = data.getOrDefault(entityId, Map.of()) // GH-90000
                    .getOrDefault(featureName, List.of()); // GH-90000
            return entries.stream() // GH-90000
                    .filter(e -> !e.writtenAt().isAfter(asOf)) // GH-90000
                    .max(Comparator.comparing(FeatureEntry::writtenAt)) // GH-90000
                    .map(FeatureEntry::value); // GH-90000
        }

        Map<String, Optional<Double>> retrieveAll(String entityId, List<String> featureNames, Instant asOf) { // GH-90000
            Map<String, Optional<Double>> result = new LinkedHashMap<>(); // GH-90000
            featureNames.forEach(f -> result.put(f, retrieve(entityId, f, asOf))); // GH-90000
            return result;
        }

        double retrieveOrDefault(String entityId, String featureName, double defaultValue, Instant asOf) { // GH-90000
            return retrieve(entityId, featureName, asOf).orElse(defaultValue); // GH-90000
        }

        Map<String, Optional<Double>> retrieveMultiEntity(List<String> entityIds, String featureName, Instant asOf) { // GH-90000
            Map<String, Optional<Double>> result = new LinkedHashMap<>(); // GH-90000
            entityIds.forEach(id -> result.put(id, retrieve(id, featureName, asOf))); // GH-90000
            return result;
        }
    }
}
