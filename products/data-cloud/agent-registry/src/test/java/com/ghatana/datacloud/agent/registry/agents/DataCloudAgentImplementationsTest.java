/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud.agent.registry.agents;

import com.ghatana.agent.AgentConfig;
import com.ghatana.agent.AgentDescriptor;
import com.ghatana.agent.AgentResult;
import com.ghatana.agent.AgentType;
import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for the three Data Cloud agent implementations:
 * {@link SchemaValidatorAgent}, {@link DataSyncAgent}, and {@link DataAnomalyDetectorAgent}.
 *
 * @doc.type class
 * @doc.purpose Test coverage for Data Cloud agent implementations
 * @doc.layer product
 * @doc.pattern Unit Test
 */
@DisplayName("Data Cloud Agent Implementations")
class DataCloudAgentImplementationsTest extends EventloopTestBase {

    private AgentContext ctx;

    @BeforeEach
    void setUp() { // GH-90000
        ctx = mock(AgentContext.class); // GH-90000
    }

    // ─── SchemaValidatorAgent ─────────────────────────────────────────────────

    @Nested
    @DisplayName("SchemaValidatorAgent")
    class SchemaValidatorAgentTests {

        private SchemaValidatorAgent agent;

        @BeforeEach
        void setUp() throws Exception { // GH-90000
            agent = new SchemaValidatorAgent(); // GH-90000
            runPromise(() -> agent.initialize(mock(AgentConfig.class))); // GH-90000
        }

        @Test
        @DisplayName("descriptor has correct id and type")
        void descriptorHasCorrectMetadata() { // GH-90000
            AgentDescriptor d = agent.descriptor(); // GH-90000
            assertThat(d.getAgentId()).isEqualTo("data-cloud:agent.data-cloud.schema-validator");
            assertThat(d.getType()).isEqualTo(AgentType.DETERMINISTIC); // GH-90000
            assertThat(d.getCapabilities()).contains("schema-validation");
        }

        @Test
        @DisplayName("passes when all required fields are present")
        void passesWhenAllRequiredFieldsPresent() throws Exception { // GH-90000
            var schema = new SchemaValidatorAgent.FieldSchema( // GH-90000
                    List.of("name", "email"), // GH-90000
                    Map.of("name", "string", "email", "string")); // GH-90000
            var request = new SchemaValidatorAgent.SchemaValidationRequest( // GH-90000
                    "tenant-1", "users",
                    Map.of("name", "Alice", "email", "alice@example.com"), // GH-90000
                    schema);

            AgentResult<SchemaValidatorAgent.SchemaValidationResult> result =
                    runPromise(() -> agent.process(ctx, request)); // GH-90000

            assertThat(result.getOutput().valid()).isTrue(); // GH-90000
            assertThat(result.getOutput().violations()).isEmpty(); // GH-90000
            assertThat(result.getConfidence()).isEqualTo(1.0); // GH-90000
        }

        @Test
        @DisplayName("fails when required field is missing")
        void failsWhenRequiredFieldMissing() throws Exception { // GH-90000
            var schema = new SchemaValidatorAgent.FieldSchema( // GH-90000
                    List.of("name", "email"), // GH-90000
                    Map.of("name", "string", "email", "string")); // GH-90000
            var request = new SchemaValidatorAgent.SchemaValidationRequest( // GH-90000
                    "tenant-1", "users",
                    Map.of("name", "Alice"), // email missing // GH-90000
                    schema);

            AgentResult<SchemaValidatorAgent.SchemaValidationResult> result =
                    runPromise(() -> agent.process(ctx, request)); // GH-90000

            assertThat(result.getOutput().valid()).isFalse(); // GH-90000
            assertThat(result.getOutput().violations()).hasSize(1); // GH-90000
            assertThat(result.getOutput().violations().get(0)).contains("email");
        }

        @Test
        @DisplayName("fails when field type does not match")
        void failsOnTypeMismatch() throws Exception { // GH-90000
            var schema = new SchemaValidatorAgent.FieldSchema( // GH-90000
                    List.of("age"),
                    Map.of("age", "integer")); // GH-90000
            var request = new SchemaValidatorAgent.SchemaValidationRequest( // GH-90000
                    "tenant-1", "profiles",
                    Map.of("age", "not-a-number"), // String instead of Integer // GH-90000
                    schema);

            AgentResult<SchemaValidatorAgent.SchemaValidationResult> result =
                    runPromise(() -> agent.process(ctx, request)); // GH-90000

            assertThat(result.getOutput().valid()).isFalse(); // GH-90000
            assertThat(result.getOutput().violations()).anyMatch(v -> v.contains("age"));
        }

        @Test
        @DisplayName("passes in lenient mode when schema is null")
        void passesInLenientMode() throws Exception { // GH-90000
            var request = new SchemaValidatorAgent.SchemaValidationRequest( // GH-90000
                    "tenant-1", "anything",
                    Map.of("x", 1, "y", 2), // GH-90000
                    null); // no schema = lenient

            AgentResult<SchemaValidatorAgent.SchemaValidationResult> result =
                    runPromise(() -> agent.process(ctx, request)); // GH-90000

            assertThat(result.getOutput().valid()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("re-initialisation without errors")
        void reInitializesSuccessfully() throws Exception { // GH-90000
            AgentConfig config = mock(AgentConfig.class); // GH-90000
            // agent is already READY from @BeforeEach; re-initialize should still work
            runPromise(() -> agent.initialize(config)); // GH-90000
            // no exception = pass
        }
    }

    // ─── DataSyncAgent ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("DataSyncAgent")
    class DataSyncAgentTests {

        private DataSyncAgent agent;

        @BeforeEach
        void setUp() throws Exception { // GH-90000
            agent = new DataSyncAgent(); // GH-90000
            runPromise(() -> agent.initialize(mock(AgentConfig.class))); // GH-90000
        }

        @Test
        @DisplayName("descriptor has correct id and type")
        void descriptorHasCorrectMetadata() { // GH-90000
            AgentDescriptor d = agent.descriptor(); // GH-90000
            assertThat(d.getAgentId()).isEqualTo("data-cloud:agent.data-cloud.data-sync");
            assertThat(d.getType()).isEqualTo(AgentType.PLANNING); // GH-90000
            assertThat(d.getCapabilities()).contains("data-sync");
        }

        @Test
        @DisplayName("syncs all valid records in delta strategy")
        void syncsAllValidRecords() throws Exception { // GH-90000
            var records = List.of( // GH-90000
                    Map.<String, Object>of("id", "rec-1", "value", 42), // GH-90000
                    Map.<String, Object>of("id", "rec-2", "value", 99)); // GH-90000
            var request = new DataSyncAgent.DataSyncRequest( // GH-90000
                    "tenant-1", "source-col", "target-col",
                    DataSyncAgent.SyncStrategy.DELTA, records);

            AgentResult<DataSyncAgent.DataSyncResult> result =
                    runPromise(() -> agent.process(ctx, request)); // GH-90000

            assertThat(result.getOutput().success()).isTrue(); // GH-90000
            assertThat(result.getOutput().synced()).isEqualTo(2); // GH-90000
            assertThat(result.getOutput().failed()).isEqualTo(0); // GH-90000
            assertThat(result.getOutput().totalRecords()).isEqualTo(2); // GH-90000
        }

        @Test
        @DisplayName("counts records without id as failed")
        void countsRecordsWithoutIdAsFailed() throws Exception { // GH-90000
            var records = List.of( // GH-90000
                    Map.<String, Object>of("value", 42) // no id field // GH-90000
            );
            var request = new DataSyncAgent.DataSyncRequest( // GH-90000
                    "tenant-1", "src", "tgt",
                    DataSyncAgent.SyncStrategy.DELTA, records);

            AgentResult<DataSyncAgent.DataSyncResult> result =
                    runPromise(() -> agent.process(ctx, request)); // GH-90000

            assertThat(result.getOutput().success()).isFalse(); // GH-90000
            assertThat(result.getOutput().failed()).isEqualTo(1); // GH-90000
        }

        @Test
        @DisplayName("skips null and empty records")
        void skipsNullAndEmptyRecords() throws Exception { // GH-90000
            var records = List.<Map<String, Object>>of( // GH-90000
                    Map.of("id", "rec-1"), // GH-90000
                    Map.of()  // empty record // GH-90000
            );
            var request = new DataSyncAgent.DataSyncRequest( // GH-90000
                    "tenant-1", "src", "tgt",
                    DataSyncAgent.SyncStrategy.INCREMENTAL, records);

            AgentResult<DataSyncAgent.DataSyncResult> result =
                    runPromise(() -> agent.process(ctx, request)); // GH-90000

            assertThat(result.getOutput().skipped()).isEqualTo(1); // GH-90000
            assertThat(result.getOutput().synced()).isEqualTo(1); // GH-90000
        }
    }

    // ─── DataAnomalyDetectorAgent ─────────────────────────────────────────────

    @Nested
    @DisplayName("DataAnomalyDetectorAgent")
    class DataAnomalyDetectorAgentTests {

        private DataAnomalyDetectorAgent agent;

        @BeforeEach
        void setUp() throws Exception { // GH-90000
            agent = new DataAnomalyDetectorAgent(); // GH-90000
            runPromise(() -> agent.initialize(mock(AgentConfig.class))); // GH-90000
        }

        @Test
        @DisplayName("descriptor has correct id and type")
        void descriptorHasCorrectMetadata() { // GH-90000
            AgentDescriptor d = agent.descriptor(); // GH-90000
            assertThat(d.getAgentId()).isEqualTo("data-cloud:agent.data-cloud.anomaly-detector");
            assertThat(d.getType()).isEqualTo(AgentType.PROBABILISTIC); // GH-90000
            assertThat(d.getCapabilities()).contains("anomaly-detection");
        }

        @Test
        @DisplayName("returns no anomalies for uniform data")
        void returnsNoAnomaliesForUniformData() throws Exception { // GH-90000
            var samples = List.of(1.0, 1.1, 0.9, 1.05, 0.95, 1.02, 0.98, 1.01); // GH-90000
            var request = new DataAnomalyDetectorAgent.AnomalyDetectionRequest( // GH-90000
                    "tenant-1", "cpu-usage", samples, 2.5);

            AgentResult<DataAnomalyDetectorAgent.AnomalyDetectionResult> result =
                    runPromise(() -> agent.process(ctx, request)); // GH-90000

            assertThat(result.getOutput().hasAnomalies()).isFalse(); // GH-90000
            assertThat(result.getOutput().anomalyCount()).isEqualTo(0); // GH-90000
        }

        @Test
        @DisplayName("detects obvious outlier in dataset")
        void detectsObviousOutlier() throws Exception { // GH-90000
            // 100.0 is a clear outlier among values near 1.0
            var samples = List.of(1.0, 1.1, 0.9, 1.05, 100.0, 0.95, 1.02, 0.98); // GH-90000
            var request = new DataAnomalyDetectorAgent.AnomalyDetectionRequest( // GH-90000
                    "tenant-1", "latency-ms", samples, 2.0);

            AgentResult<DataAnomalyDetectorAgent.AnomalyDetectionResult> result =
                    runPromise(() -> agent.process(ctx, request)); // GH-90000

            assertThat(result.getOutput().hasAnomalies()).isTrue(); // GH-90000
            assertThat(result.getOutput().anomalyIndices()).contains(4); // index of 100.0 // GH-90000
            assertThat(result.getOutput().anomalyValues()).contains(100.0); // GH-90000
        }

        @Test
        @DisplayName("returns low-confidence result for insufficient data")
        void returnsLowConfidenceForInsufficientData() throws Exception { // GH-90000
            var request = new DataAnomalyDetectorAgent.AnomalyDetectionRequest( // GH-90000
                    "tenant-1", "metric", List.of(1.0, 2.0), -1); // GH-90000

            AgentResult<DataAnomalyDetectorAgent.AnomalyDetectionResult> result =
                    runPromise(() -> agent.process(ctx, request)); // GH-90000

            assertThat(result.getConfidence()).isEqualTo(0.0); // GH-90000
            assertThat(result.getOutput().hasAnomalies()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("uses default threshold when request threshold is zero")
        void usesDefaultThresholdWhenZero() throws Exception { // GH-90000
            // All near-identical values — no anomaly
            var samples = List.of(5.0, 5.1, 4.9, 5.2, 4.8, 5.05, 4.95, 5.0); // GH-90000
            var request = new DataAnomalyDetectorAgent.AnomalyDetectionRequest( // GH-90000
                    "tenant-1", "response-time", samples, 0); // 0 = use default

            AgentResult<DataAnomalyDetectorAgent.AnomalyDetectionResult> result =
                    runPromise(() -> agent.process(ctx, request)); // GH-90000

            assertThat(result.getOutput().hasAnomalies()).isFalse(); // GH-90000
        }
    }
}
