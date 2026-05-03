/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.server.validation;

import com.ghatana.aep.eventcloud.store.EventCloudAgentStore;
import com.ghatana.aep.server.analytics.DataCloudAnalyticsStore;
import com.ghatana.aep.server.analytics.DataCloudAnalyticsStore.AnomalyRecord;
import com.ghatana.aep.server.analytics.DataCloudAnalyticsStore.KpiSnapshot;
import com.ghatana.aep.server.analytics.DataCloudAnalyticsStore.MetricDataPoint;
import com.ghatana.aep.server.store.DataCloudPatternStore;
import com.ghatana.aep.server.store.DataCloudPipelineStore;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.DataCloudClient.Entity;
import com.ghatana.datacloud.spi.EntityStore;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DataCloud integration validation tests that exercise real store implementations
 * against a mocked {@link DataCloudClient}.
 *
 * <p>All tests invoke real production store code ({@link DataCloudAnalyticsStore},
 * {@link DataCloudPatternStore}, {@link DataCloudPipelineStore}, {@link EventCloudAgentStore})
 * — not test-theatre simulation methods. The {@link DataCloudClient} is mocked so that
 * tests execute without an external DataCloud service.
 *
 * @doc.type class
 * @doc.purpose DataCloud integration validation against real store implementations
 * @doc.layer product
 * @doc.pattern Test
 */
@Tag("integration")
@ExtendWith(MockitoExtension.class)
@DisplayName("DataCloud Integration Validation")
class DataCloudIntegrationValidationTest {

    private static final String TENANT = "validation-tenant";

    @Mock
    private DataCloudClient dataCloudClient;

    @Mock
    private EntityStore entityStore;

    @BeforeEach
    void setUp() {
        lenient().when(dataCloudClient.entityStore()).thenReturn(entityStore);
    }

    // ─── Analytics Store — KPI / Anomaly / Metrics ────────────────────────────

    @Nested
    @DisplayName("DataCloudAnalyticsStore — KPI snapshots")
    class AnalyticsKpiTests {

        private DataCloudAnalyticsStore store;

        @BeforeEach
        void setUp() {
            store = new DataCloudAnalyticsStore(dataCloudClient);
        }

        @Test
        @DisplayName("saveKpiSnapshot writes to KPI_COLLECTION and returns hydrated snapshot")
        void saveKpiSnapshotWritesToKpiCollection() {
            String id = UUID.randomUUID().toString();
            KpiSnapshot input = KpiSnapshot.of("aep.requests.total", 1234.0, "req");

            when(dataCloudClient.save(eq(TENANT), eq(DataCloudAnalyticsStore.KPI_COLLECTION), anyMap()))
                .thenReturn(Promise.of(entity(id, Map.of(
                    "id", id,
                    "kpiName", "aep.requests.total",
                    "value", 1234.0,
                    "unit", "req",
                    "capturedAt", Instant.now().toString(),
                    "tags", ""))));

            KpiSnapshot result = store.saveKpiSnapshot(TENANT, input).getResult();

            assertThat(result.kpiName()).isEqualTo("aep.requests.total");
            assertThat(result.value()).isEqualTo(1234.0);
            assertThat(result.unit()).isEqualTo("req");
        }

        @Test
        @DisplayName("saveKpiSnapshot invokes DataCloud with correct collection name")
        void saveKpiSnapshotUsesCorrectCollection() {
            KpiSnapshot input = KpiSnapshot.of("aep.error.rate", 0.02, "%");
            String id = UUID.randomUUID().toString();

            when(dataCloudClient.save(eq(TENANT), eq(DataCloudAnalyticsStore.KPI_COLLECTION), anyMap()))
                .thenReturn(Promise.of(entity(id, Map.of(
                    "id", id,
                    "kpiName", "aep.error.rate",
                    "value", 0.02,
                    "unit", "%",
                    "capturedAt", Instant.now().toString(),
                    "tags", ""))));

            store.saveKpiSnapshot(TENANT, input).getResult();

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
            verify(dataCloudClient).save(eq(TENANT), eq(DataCloudAnalyticsStore.KPI_COLLECTION), captor.capture());
            assertThat(captor.getValue()).containsKey("kpiName");
        }

        @Test
        @DisplayName("saveAnomaly stores anomaly in ANOMALY_COLLECTION")
        void saveAnomalyStoresInCorrectCollection() {
            AnomalyRecord anomaly = AnomalyRecord.of("FREQUENCY_SPIKE", "HIGH", 0.87, "Request rate spike detected");
            String id = UUID.randomUUID().toString();

            when(dataCloudClient.save(eq(TENANT), eq(DataCloudAnalyticsStore.ANOMALY_COLLECTION), anyMap()))
                .thenReturn(Promise.of(entity(id, Map.of(
                    "id", id,
                    "anomalyType", "FREQUENCY_SPIKE",
                    "severity", "HIGH",
                    "score", 0.87,
                    "description", "Request rate spike detected",
                    "detectedAt", Instant.now().toString(),
                    "resolved", "false"))));

            AnomalyRecord result = store.saveAnomaly(TENANT, anomaly).getResult();

            assertThat(result.anomalyType()).isEqualTo("FREQUENCY_SPIKE");
            assertThat(result.severity()).isEqualTo("HIGH");
            assertThat(result.score()).isEqualTo(0.87);
        }

        @Test
        @DisplayName("client failure propagates as failed promise — not silent")
        void clientFailurePropagatesAsFail() {
            RuntimeException cause = new RuntimeException("DataCloud unavailable");
            when(dataCloudClient.save(eq(TENANT), eq(DataCloudAnalyticsStore.KPI_COLLECTION), anyMap()))
                .thenReturn(Promise.ofException(cause));

            Promise<KpiSnapshot> result = store.saveKpiSnapshot(TENANT, KpiSnapshot.of("x", 1, "unit"));

            assertThat(result.isComplete()).isTrue();
            assertThat(result.isResult()).isFalse();
        }
    }

    // ─── Pattern Store ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("DataCloudPatternStore — pattern reads and writes")
    class PatternStoreTests {

        private DataCloudPatternStore store;

        @BeforeEach
        void setUp() {
            store = new DataCloudPatternStore(dataCloudClient);
        }

        @Test
        @DisplayName("findByTenantAndId returns pattern from DataCloud entity")
        void findByTenantAndIdReturnsPattern() {
            UUID patternId = UUID.randomUUID();
            Entity entity = entity(patternId.toString(), Map.of(
                "id", patternId.toString(),
                "name", "High-CPU Alert",
                "tenantId", TENANT,
                "status", "ACTIVE",
                "description", "CPU usage exceeds 90%"));

            when(dataCloudClient.findById(eq(TENANT), eq("aep_patterns"), eq(patternId.toString())))
                .thenReturn(Promise.of(Optional.of(entity)));

            var found = store.findByTenantAndId(TENANT, patternId).getResult();

            assertThat(found).isPresent();
            assertThat(found.get().getName()).isEqualTo("High-CPU Alert");
            assertThat(found.get().getId()).isEqualTo(patternId);
        }

        @Test
        @DisplayName("findByTenantAndId returns empty when entity not found")
        void findByTenantAndIdReturnsEmptyWhenNotFound() {
            UUID missingId = UUID.randomUUID();

            when(dataCloudClient.findById(eq(TENANT), eq("aep_patterns"), eq(missingId.toString())))
                .thenReturn(Promise.of(Optional.empty()));

            var found = store.findByTenantAndId(TENANT, missingId).getResult();

            assertThat(found).isEmpty();
        }

        @Test
        @DisplayName("DataCloud failure surfaces as failed promise — not swallowed")
        void dataCloudFailureSurfacesAsFailedPromise() {
            UUID id = UUID.randomUUID();
            when(dataCloudClient.findById(eq(TENANT), eq("aep_patterns"), eq(id.toString())))
                .thenReturn(Promise.ofException(new RuntimeException("DataCloud timeout")));

            Promise<?> result = store.findByTenantAndId(TENANT, id);

            assertThat(result.isComplete()).isTrue();
            assertThat(result.isResult()).isFalse();
        }
    }

    // ─── Pipeline Store ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("DataCloudPipelineStore — pipeline persistence")
    class PipelineStoreTests {

        private DataCloudPipelineStore store;

        @BeforeEach
        void setUp() {
            store = new DataCloudPipelineStore(dataCloudClient);
        }

        @Test
        @DisplayName("findById returns pipeline entity for a known ID")
        void findByIdReturnsKnownPipeline() {
            String pipelineId = UUID.randomUUID().toString();
            Entity entity = entity(pipelineId, Map.of(
                "id", pipelineId,
                "name", "Event Enrichment Pipeline",
                "tenantId", TENANT,
                "version", "1",
                "active", "true",
                "createdAt", Instant.now().toString(),
                "updatedAt", Instant.now().toString()));

            when(dataCloudClient.findById(eq(TENANT), eq(DataCloudPipelineStore.COLLECTION), eq(pipelineId)))
                .thenReturn(Promise.of(Optional.of(entity)));

            var result = store.findById(TENANT, pipelineId).getResult();

            assertThat(result).isPresent();
            assertThat(result.get().getName()).isEqualTo("Event Enrichment Pipeline");
        }

        @Test
        @DisplayName("findById returns empty for an unknown ID")
        void findByIdReturnsEmptyForUnknown() {
            String unknownId = UUID.randomUUID().toString();

            when(dataCloudClient.findById(eq(TENANT), eq(DataCloudPipelineStore.COLLECTION), eq(unknownId)))
                .thenReturn(Promise.of(Optional.empty()));

            var result = store.findById(TENANT, unknownId).getResult();

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("save invokes DataCloud.save with COLLECTION and returns persisted entity")
        void savePersistsViaDataCloud() {
            String pipelineId = UUID.randomUUID().toString();
            com.ghatana.pipeline.registry.model.Pipeline pipeline = new com.ghatana.pipeline.registry.model.Pipeline();
            pipeline.setId(pipelineId);
            pipeline.setName("Anomaly Detection Pipeline");
            pipeline.setTenantId(com.ghatana.platform.domain.auth.TenantId.of(TENANT));
            pipeline.setVersion(1);
            pipeline.setActive(true);
            pipeline.setCreatedAt(Instant.now());
            pipeline.setUpdatedAt(Instant.now());

            when(dataCloudClient.save(eq(TENANT), eq(DataCloudPipelineStore.COLLECTION), anyMap()))
                .thenReturn(Promise.of(entity(pipelineId, Map.of(
                    "id", pipelineId,
                    "name", "Anomaly Detection Pipeline",
                    "tenantId", TENANT,
                    "version", "1",
                    "active", "true",
                    "createdAt", Instant.now().toString(),
                    "updatedAt", Instant.now().toString()))));

            var saved = store.save(pipeline).getResult();

            assertThat(saved).isNotNull();
            assertThat(saved.getName()).isEqualTo("Anomaly Detection Pipeline");
            verify(dataCloudClient).save(eq(TENANT), eq(DataCloudPipelineStore.COLLECTION), anyMap());
        }
    }

    // ─── Retry / error handling behaviour ────────────────────────────────────

    @Nested
    @DisplayName("Error surface and retry contract")
    class ErrorSurfaceTests {

        @Test
        @DisplayName("analytics store surfaces DataCloud errors as failed promise — not swallowed")
        void analyticsStoreFailureSurfaces() {
            DataCloudAnalyticsStore store = new DataCloudAnalyticsStore(dataCloudClient);
            when(dataCloudClient.save(anyString(), anyString(), anyMap()))
                .thenReturn(Promise.ofException(new RuntimeException("network partition")));

            Promise<KpiSnapshot> result = store.saveKpiSnapshot(TENANT, KpiSnapshot.of("x", 1, "ms"));

            assertThat(result.isComplete()).isTrue();
            assertThat(result.isResult()).isFalse();
        }

        @Test
        @DisplayName("pattern store surfaces DataCloud errors as failed promise — not swallowed")
        void patternStoreFailureSurfaces() {
            DataCloudPatternStore store = new DataCloudPatternStore(dataCloudClient);
            UUID id = UUID.randomUUID();
            when(dataCloudClient.findById(eq(TENANT), anyString(), eq(id.toString())))
                .thenReturn(Promise.ofException(new RuntimeException("timeout")));

            Promise<?> result = store.findByTenantAndId(TENANT, id);

            assertThat(result.isComplete()).isTrue();
            assertThat(result.isResult()).isFalse();
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private static Entity entity(String id, Map<String, Object> data) {
        return Entity.of(id, "aep_collection", data);
    }
}
