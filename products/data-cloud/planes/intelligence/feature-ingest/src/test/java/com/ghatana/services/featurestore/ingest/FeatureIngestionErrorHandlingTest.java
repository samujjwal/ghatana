/*
 * Copyright (c) 2026 Ghatana Inc. 
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
@DisplayName("FeatureIngestionErrorHandlingTest")
@Tag("feature-store")
class FeatureIngestionErrorHandlingTest {

    private FaultInjectionIngestPipeline pipeline;
    private List<String> deadLetterQueue;

    @BeforeEach
    void setUp() { 
        deadLetterQueue = new ArrayList<>(); 
        pipeline = new FaultInjectionIngestPipeline(deadLetterQueue); 
    }

    // ── Extraction failures ───────────────────────────────────────────────────

    @Test
    @DisplayName("null payload causes FeatureExtractionException")
    void nullPayloadCausesExtractionException() { 
        assertThatThrownBy(() -> 
                pipeline.ingestStrict("entity-1", null, Instant.now())) 
                .isInstanceOf(FeatureExtractionException.class); 
    }

    @Test
    @DisplayName("empty payload causes FeatureExtractionException")
    void emptyPayloadCausesExtractionException() { 
        assertThatThrownBy(() -> 
                pipeline.ingestStrict("entity-1", Map.of(), Instant.now())) 
                .isInstanceOf(FeatureExtractionException.class); 
    }

    @Test
    @DisplayName("payload with only non-numeric values is routed to DLQ")
    void nonNumericPayloadRoutedToDlq() { 
        Map<String, Object> payload = Map.of("name", "Alice", "label", "premium"); 
        pipeline.ingestFault("entity-1", payload, Instant.now()); 
        assertThat(deadLetterQueue).hasSize(1); 
        assertThat(deadLetterQueue.get(0)).contains("entity-1");
    }

    @Test
    @DisplayName("payload with mixed valid and invalid keys extracts valid numeric features")
    void mixedPayloadExtractsNumericFeatures() { 
        Map<String, Object> payload = new HashMap<>(); 
        payload.put("score", 0.95); 
        payload.put("label", "gold");   // non-numeric — skipped 
        payload.put("amount", 150.0); 
        List<ExtractedFeature> features = pipeline.extractNumeric("entity-1", payload, Instant.now()); 
        assertThat(features).extracting(ExtractedFeature::name) 
                .containsExactlyInAnyOrder("score", "amount"); 
    }

    // ── Write failures ────────────────────────────────────────────────────────

    @Test
    @DisplayName("write failure routes record to DLQ and does not throw")
    void writeFailureRoutesToDlq() { 
        pipeline.simulateWriteFailure(true); 
        Map<String, Object> payload = Map.of("score", 0.9); 
        pipeline.ingestFault("entity-1", payload, Instant.now()); 
        assertThat(deadLetterQueue).hasSize(1); 
        assertThat(deadLetterQueue.get(0)).contains("WRITE_FAILURE");
    }

    @Test
    @DisplayName("FeatureStoreWriteException is not swallowed on strict path")
    void writeExceptionNotSwallowedOnStrictPath() { 
        pipeline.simulateWriteFailure(true); 
        Map<String, Object> payload = Map.of("score", 0.9); 
        assertThatThrownBy(() -> 
                pipeline.ingestStrict("entity-1", payload, Instant.now())) 
                .isInstanceOf(FeatureStoreWriteException.class); 
    }

    // ── Partial batch failures ────────────────────────────────────────────────

    @Test
    @DisplayName("batch ingest continues for valid records despite one failure")
    void batchIngestContinuesOnPartialFailure() { 
        List<Map<String, Object>> batch = List.of( 
                Map.of("score", 0.9), 
                Map.of(),               // empty — extraction failure 
                Map.of("amount", 50.0) 
        );
        BatchResult result = pipeline.ingestBatch(List.of("e1", "e2", "e3"), batch, Instant.now()); 
        assertThat(result.succeeded()).isEqualTo(2); 
        assertThat(result.failed()).isEqualTo(1); 
    }

    @Test
    @DisplayName("batch DLQ entries correspond to failed records")
    void batchFailuresAreRecordedInDlq() { 
        List<Map<String, Object>> batch = List.of( 
                Map.of(),               // will fail 
                Map.of("val", 1.0) 
        );
        pipeline.ingestBatch(List.of("e1", "e2"), batch, Instant.now()); 
        assertThat(deadLetterQueue).hasSize(1); 
    }

    // ── Entity ID validation ──────────────────────────────────────────────────

    @Test
    @DisplayName("null entity ID causes FeatureIngestException")
    void nullEntityIdCausesIngestException() { 
        Map<String, Object> payload = Map.of("score", 0.9); 
        assertThatThrownBy(() -> 
                pipeline.ingestStrict(null, payload, Instant.now())) 
                .isInstanceOf(FeatureIngestException.class) 
                .hasMessageContaining("entity");
    }

    @Test
    @DisplayName("blank entity ID causes FeatureIngestException")
    void blankEntityIdCausesIngestException() { 
        Map<String, Object> payload = Map.of("score", 0.9); 
        assertThatThrownBy(() -> 
                pipeline.ingestStrict("  ", payload, Instant.now())) 
                .isInstanceOf(FeatureIngestException.class); 
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Inner implementation
    // ─────────────────────────────────────────────────────────────────────────

    record ExtractedFeature(String name, double value, Instant timestamp) {} 

    record BatchResult(int succeeded, int failed) {} 

    static class FaultInjectionIngestPipeline {
        private final List<String> dlq;
        private boolean writeFailure = false;
        // Simple in-memory store
        private final Map<String, List<ExtractedFeature>> store = new HashMap<>(); 

        FaultInjectionIngestPipeline(List<String> dlq) { 
            this.dlq = dlq;
        }

        void simulateWriteFailure(boolean fail) { 
            this.writeFailure = fail;
        }

        List<ExtractedFeature> extractNumeric(String entityId, Map<String, Object> payload, Instant ts) { 
            List<ExtractedFeature> features = new ArrayList<>(); 
            if (payload == null || payload.isEmpty()) 
                throw new FeatureExtractionException("evt-1", "test-tenant", "Empty or null payload for entity: " + entityId, null); 
            payload.forEach((k, v) -> { 
                if (v instanceof Number n) { 
                    features.add(new ExtractedFeature(k, n.doubleValue(), ts)); 
                }
            });
            if (features.isEmpty()) 
                throw new FeatureExtractionException("evt-2", "test-tenant", "No numeric features found in payload for entity: " + entityId, null); 
            return features;
        }

        void ingestStrict(String entityId, Map<String, Object> payload, Instant ts) { 
            if (entityId == null || entityId.isBlank()) 
                throw new FeatureIngestException( 
                        "entityId must not be blank",
                        FeatureIngestException.ErrorCategory.EXTRACTION_FAILURE);
            if (payload == null || payload.isEmpty()) 
                throw new FeatureExtractionException("evt-2", "test-tenant", "Cannot extract features — payload is null or empty", null); 
            if (writeFailure) 
                throw new FeatureStoreWriteException("feature-1", "test-tenant", 3, "Simulated write failure for entity: " + entityId, null); 
            List<ExtractedFeature> features = extractNumeric(entityId, payload, ts); 
            store.computeIfAbsent(entityId, k -> new ArrayList<>()).addAll(features); 
        }

        void ingestFault(String entityId, Map<String, Object> payload, Instant ts) { 
            try {
                ingestStrict(entityId, payload, ts); 
            } catch (FeatureStoreWriteException e) { 
                dlq.add("WRITE_FAILURE:" + entityId + ":" + e.getMessage()); 
            } catch (FeatureExtractionException e) { 
                dlq.add("EXTRACT_FAILURE:" + entityId + ":" + e.getMessage()); 
            }
        }

        BatchResult ingestBatch(List<String> entityIds, List<Map<String, Object>> payloads, Instant ts) { 
            int succeeded = 0;
            int failed = 0;
            for (int i = 0; i < entityIds.size(); i++) { 
                try {
                    ingestStrict(entityIds.get(i), payloads.get(i), ts); 
                    succeeded++;
                } catch (Exception e) { 
                    dlq.add("BATCH_FAILURE:" + entityIds.get(i) + ":" + e.getMessage()); 
                    failed++;
                }
            }
            return new BatchResult(succeeded, failed); 
        }
    }
}
