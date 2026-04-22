/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.services.featurestore.ingest;

import com.ghatana.services.featurestore.exception.FeatureExtractionException;
import com.ghatana.services.featurestore.exception.FeatureIngestException;
import com.ghatana.services.featurestore.exception.FeatureStoreWriteException;
import org.junit.jupiter.api.*;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Error-handling tests for the feature ingestion pipeline.
 *
 * <p>Validates that extraction failures, write failures, and schema
 * violations are caught, categorised, and routed to the dead-letter path
 * while allowing valid records to succeed.
 *
 * @doc.type    class
 * @doc.purpose Feature ingestion error handling: extraction failures, write errors, DLQ routing
 * @doc.layer   product
 * @doc.pattern Test
 */
@DisplayName("FeatureIngestionErrorHandlingTest [GH-90000]")
@Tag("feature-store [GH-90000]")
class FeatureIngestionErrorHandlingTest {

    private FaultInjectionIngestPipeline pipeline;
    private List<String> deadLetterQueue;

    @BeforeEach
    void setUp() { // GH-90000
        deadLetterQueue = new ArrayList<>(); // GH-90000
        pipeline = new FaultInjectionIngestPipeline(deadLetterQueue); // GH-90000
    }

    // ── Extraction failures ───────────────────────────────────────────────────

    @Test
    @DisplayName("null payload causes FeatureExtractionException [GH-90000]")
    void nullPayloadCausesExtractionException() { // GH-90000
        assertThatThrownBy(() -> // GH-90000
                pipeline.ingestStrict("entity-1", null, Instant.now())) // GH-90000
                .isInstanceOf(FeatureExtractionException.class); // GH-90000
    }

    @Test
    @DisplayName("empty payload causes FeatureExtractionException [GH-90000]")
    void emptyPayloadCausesExtractionException() { // GH-90000
        assertThatThrownBy(() -> // GH-90000
                pipeline.ingestStrict("entity-1", Map.of(), Instant.now())) // GH-90000
                .isInstanceOf(FeatureExtractionException.class); // GH-90000
    }

    @Test
    @DisplayName("payload with only non-numeric values is routed to DLQ [GH-90000]")
    void nonNumericPayloadRoutedToDlq() { // GH-90000
        Map<String, Object> payload = Map.of("name", "Alice", "label", "premium"); // GH-90000
        pipeline.ingestFault("entity-1", payload, Instant.now()); // GH-90000
        assertThat(deadLetterQueue).hasSize(1); // GH-90000
        assertThat(deadLetterQueue.get(0)).contains("entity-1 [GH-90000]");
    }

    @Test
    @DisplayName("payload with mixed valid and invalid keys extracts valid numeric features [GH-90000]")
    void mixedPayloadExtractsNumericFeatures() { // GH-90000
        Map<String, Object> payload = new HashMap<>(); // GH-90000
        payload.put("score", 0.95); // GH-90000
        payload.put("label", "gold");   // non-numeric — skipped // GH-90000
        payload.put("amount", 150.0); // GH-90000
        List<ExtractedFeature> features = pipeline.extractNumeric("entity-1", payload, Instant.now()); // GH-90000
        assertThat(features).extracting(ExtractedFeature::name) // GH-90000
                .containsExactlyInAnyOrder("score", "amount"); // GH-90000
    }

    // ── Write failures ────────────────────────────────────────────────────────

    @Test
    @DisplayName("write failure routes record to DLQ and does not throw [GH-90000]")
    void writeFailureRoutesToDlq() { // GH-90000
        pipeline.simulateWriteFailure(true); // GH-90000
        Map<String, Object> payload = Map.of("score", 0.9); // GH-90000
        pipeline.ingestFault("entity-1", payload, Instant.now()); // GH-90000
        assertThat(deadLetterQueue).hasSize(1); // GH-90000
        assertThat(deadLetterQueue.get(0)).contains("WRITE_FAILURE [GH-90000]");
    }

    @Test
    @DisplayName("FeatureStoreWriteException is not swallowed on strict path [GH-90000]")
    void writeExceptionNotSwallowedOnStrictPath() { // GH-90000
        pipeline.simulateWriteFailure(true); // GH-90000
        Map<String, Object> payload = Map.of("score", 0.9); // GH-90000
        assertThatThrownBy(() -> // GH-90000
                pipeline.ingestStrict("entity-1", payload, Instant.now())) // GH-90000
                .isInstanceOf(FeatureStoreWriteException.class); // GH-90000
    }

    // ── Partial batch failures ────────────────────────────────────────────────

    @Test
    @DisplayName("batch ingest continues for valid records despite one failure [GH-90000]")
    void batchIngestContinuesOnPartialFailure() { // GH-90000
        List<Map<String, Object>> batch = List.of( // GH-90000
                Map.of("score", 0.9), // GH-90000
                Map.of(),               // empty — extraction failure // GH-90000
                Map.of("amount", 50.0) // GH-90000
        );
        BatchResult result = pipeline.ingestBatch(List.of("e1", "e2", "e3"), batch, Instant.now()); // GH-90000
        assertThat(result.succeeded()).isEqualTo(2); // GH-90000
        assertThat(result.failed()).isEqualTo(1); // GH-90000
    }

    @Test
    @DisplayName("batch DLQ entries correspond to failed records [GH-90000]")
    void batchFailuresAreRecordedInDlq() { // GH-90000
        List<Map<String, Object>> batch = List.of( // GH-90000
                Map.of(),               // will fail // GH-90000
                Map.of("val", 1.0) // GH-90000
        );
        pipeline.ingestBatch(List.of("e1", "e2"), batch, Instant.now()); // GH-90000
        assertThat(deadLetterQueue).hasSize(1); // GH-90000
    }

    // ── Entity ID validation ──────────────────────────────────────────────────

    @Test
    @DisplayName("null entity ID causes FeatureIngestException [GH-90000]")
    void nullEntityIdCausesIngestException() { // GH-90000
        Map<String, Object> payload = Map.of("score", 0.9); // GH-90000
        assertThatThrownBy(() -> // GH-90000
                pipeline.ingestStrict(null, payload, Instant.now())) // GH-90000
                .isInstanceOf(FeatureIngestException.class) // GH-90000
                .hasMessageContaining("entity [GH-90000]");
    }

    @Test
    @DisplayName("blank entity ID causes FeatureIngestException [GH-90000]")
    void blankEntityIdCausesIngestException() { // GH-90000
        Map<String, Object> payload = Map.of("score", 0.9); // GH-90000
        assertThatThrownBy(() -> // GH-90000
                pipeline.ingestStrict("  ", payload, Instant.now())) // GH-90000
                .isInstanceOf(FeatureIngestException.class); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Inner implementation
    // ─────────────────────────────────────────────────────────────────────────

    record ExtractedFeature(String name, double value, Instant timestamp) {} // GH-90000

    record BatchResult(int succeeded, int failed) {} // GH-90000

    static class FaultInjectionIngestPipeline {
        private final List<String> dlq;
        private boolean writeFailure = false;
        // Simple in-memory store
        private final Map<String, List<ExtractedFeature>> store = new HashMap<>(); // GH-90000

        FaultInjectionIngestPipeline(List<String> dlq) { // GH-90000
            this.dlq = dlq;
        }

        void simulateWriteFailure(boolean fail) { // GH-90000
            this.writeFailure = fail;
        }

        List<ExtractedFeature> extractNumeric(String entityId, Map<String, Object> payload, Instant ts) { // GH-90000
            List<ExtractedFeature> features = new ArrayList<>(); // GH-90000
            if (payload == null || payload.isEmpty()) // GH-90000
                throw new FeatureExtractionException("evt-1", "test-tenant", "Empty or null payload for entity: " + entityId, null); // GH-90000
            payload.forEach((k, v) -> { // GH-90000
                if (v instanceof Number n) { // GH-90000
                    features.add(new ExtractedFeature(k, n.doubleValue(), ts)); // GH-90000
                }
            });
            if (features.isEmpty()) // GH-90000
                throw new FeatureExtractionException("evt-2", "test-tenant", "No numeric features found in payload for entity: " + entityId, null); // GH-90000
            return features;
        }

        void ingestStrict(String entityId, Map<String, Object> payload, Instant ts) { // GH-90000
            if (entityId == null || entityId.isBlank()) // GH-90000
                throw new FeatureIngestException( // GH-90000
                        "entityId must not be blank",
                        FeatureIngestException.ErrorCategory.EXTRACTION_FAILURE);
            if (payload == null || payload.isEmpty()) // GH-90000
                throw new FeatureExtractionException("evt-2", "test-tenant", "Cannot extract features — payload is null or empty", null); // GH-90000
            if (writeFailure) // GH-90000
                throw new FeatureStoreWriteException("feature-1", "test-tenant", 3, "Simulated write failure for entity: " + entityId, null); // GH-90000
            List<ExtractedFeature> features = extractNumeric(entityId, payload, ts); // GH-90000
            store.computeIfAbsent(entityId, k -> new ArrayList<>()).addAll(features); // GH-90000
        }

        void ingestFault(String entityId, Map<String, Object> payload, Instant ts) { // GH-90000
            try {
                ingestStrict(entityId, payload, ts); // GH-90000
            } catch (FeatureStoreWriteException e) { // GH-90000
                dlq.add("WRITE_FAILURE:" + entityId + ":" + e.getMessage()); // GH-90000
            } catch (FeatureExtractionException e) { // GH-90000
                dlq.add("EXTRACT_FAILURE:" + entityId + ":" + e.getMessage()); // GH-90000
            }
        }

        BatchResult ingestBatch(List<String> entityIds, List<Map<String, Object>> payloads, Instant ts) { // GH-90000
            int succeeded = 0;
            int failed = 0;
            for (int i = 0; i < entityIds.size(); i++) { // GH-90000
                try {
                    ingestStrict(entityIds.get(i), payloads.get(i), ts); // GH-90000
                    succeeded++;
                } catch (Exception e) { // GH-90000
                    dlq.add("BATCH_FAILURE:" + entityIds.get(i) + ":" + e.getMessage()); // GH-90000
                    failed++;
                }
            }
            return new BatchResult(succeeded, failed); // GH-90000
        }
    }
}
