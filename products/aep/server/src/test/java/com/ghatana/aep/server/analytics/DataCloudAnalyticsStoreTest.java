/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.server.analytics;

import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.DataCloudClient.Entity;
import com.ghatana.datacloud.DataCloudClient.Query;
import com.ghatana.aep.server.analytics.DataCloudAnalyticsStore.AnomalyRecord;
import com.ghatana.aep.server.analytics.DataCloudAnalyticsStore.KpiSnapshot;
import com.ghatana.aep.server.analytics.DataCloudAnalyticsStore.MetricDataPoint;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link DataCloudAnalyticsStore}.
 *
 * <p>All DataCloud I/O is replaced by Mockito stubs returning synchronous
 * {@link Promise#of(Object)} values, so no Eventloop is required — promises
 * are already resolved and {@code .getResult()} is safe to call.
 *
 * @doc.type class
 * @doc.purpose Unit tests for DataCloudAnalyticsStore — KPI, Anomaly, Metrics collections
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DataCloudAnalyticsStore")
class DataCloudAnalyticsStoreTest {

    private static final String TENANT = "tenant-analytics";

    @Mock
    private DataCloudClient client;

    private DataCloudAnalyticsStore store;

    @BeforeEach
    void setUp() {
        store = new DataCloudAnalyticsStore(client);
    }

    // =========================================================================
    // KPI Snapshots
    // =========================================================================

    @Nested
    @DisplayName("KPI Snapshots")
    class KpiSnapshotTests {

        @Test
        @DisplayName("saveKpiSnapshot: writes to KPI_COLLECTION and returns hydrated snapshot")
        void saveKpiSnapshot_writeToKpiCollection() {
            KpiSnapshot input = KpiSnapshot.of("cpu.usage", 72.3, "%");
            String assignedId = UUID.randomUUID().toString();

            Entity stubEntity = entity(assignedId, Map.of(
                    "id", assignedId,
                    "kpiName", "cpu.usage",
                    "value", 72.3,
                    "unit", "%",
                    "capturedAt", Instant.now().toString(),
                    "tags", ""
            ));
            when(client.save(eq(TENANT), eq(DataCloudAnalyticsStore.KPI_COLLECTION), anyMap()))
                    .thenReturn(Promise.of(stubEntity));

            KpiSnapshot result = store.saveKpiSnapshot(TENANT, input).getResult();

            assertThat(result).isNotNull();
            assertThat(result.kpiName()).isEqualTo("cpu.usage");
            assertThat(result.value()).isEqualTo(72.3);
            assertThat(result.unit()).isEqualTo("%");
        }

        @Test
        @DisplayName("saveKpiSnapshot: assigns ID before persisting")
        void saveKpiSnapshot_assignsId() {
            KpiSnapshot input = KpiSnapshot.of("event.latency", 45.0, "ms");
            when(client.save(anyString(), anyString(), anyMap()))
                    .thenReturn(Promise.of(entity("new-id", Map.of(
                            "id", "new-id", "kpiName", "event.latency",
                            "value", 45.0, "unit", "ms",
                            "capturedAt", Instant.now().toString(), "tags", ""
                    ))));

            store.saveKpiSnapshot(TENANT, input).getResult();

            ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
            verify(client).save(eq(TENANT), eq(DataCloudAnalyticsStore.KPI_COLLECTION), captor.capture());
            assertThat(captor.getValue().get("id")).isNotNull().asString().isNotBlank();
        }

        @Test
        @DisplayName("queryKpiSnapshots: queries KPI collection and maps all results")
        void queryKpiSnapshots_mapsAllEntities() {
            String kpiName = "throughput";
            List<Entity> stubList = List.of(
                    entity("e1", kpiData("e1", kpiName, 100.0)),
                    entity("e2", kpiData("e2", kpiName, 110.0))
            );
            when(client.query(eq(TENANT), eq(DataCloudAnalyticsStore.KPI_COLLECTION), any(Query.class)))
                    .thenReturn(Promise.of(stubList));

            List<KpiSnapshot> results = store.queryKpiSnapshots(TENANT, kpiName, null, null, 10).getResult();

            assertThat(results).hasSize(2);
            assertThat(results.get(0).kpiName()).isEqualTo(kpiName);
            assertThat(results.get(1).value()).isEqualTo(110.0);
        }

        @Test
        @DisplayName("getLatestKpi: returns present Optional when entity found")
        void getLatestKpi_whenEntityFound_returnsPresent() {
            String kpiName = "error.rate";
            Entity stub = entity("e1", kpiData("e1", kpiName, 0.02));
            when(client.query(eq(TENANT), eq(DataCloudAnalyticsStore.KPI_COLLECTION), any(Query.class)))
                    .thenReturn(Promise.of(List.of(stub)));

            Optional<KpiSnapshot> result = store.getLatestKpi(TENANT, kpiName).getResult();

            assertThat(result).isPresent();
            assertThat(result.get().kpiName()).isEqualTo(kpiName);
        }

        @Test
        @DisplayName("getLatestKpi: returns empty Optional when no entities found")
        void getLatestKpi_whenNotFound_returnsEmpty() {
            when(client.query(eq(TENANT), eq(DataCloudAnalyticsStore.KPI_COLLECTION), any(Query.class)))
                    .thenReturn(Promise.of(List.of()));

            Optional<KpiSnapshot> result = store.getLatestKpi(TENANT, "missing.kpi").getResult();

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("purgeOldKpiSnapshots: queries then deletes each entity individually")
        void purgeOldKpiSnapshots_deletesEachEntity() {
            List<Entity> stale = List.of(
                    entity("old1", kpiData("old1", "cpu", 50.0)),
                    entity("old2", kpiData("old2", "cpu", 60.0))
            );
            when(client.query(eq(TENANT), eq(DataCloudAnalyticsStore.KPI_COLLECTION), any(Query.class)))
                    .thenReturn(Promise.of(stale));
            when(client.delete(eq(TENANT), eq(DataCloudAnalyticsStore.KPI_COLLECTION), anyString()))
                    .thenReturn(Promise.of((Void) null));

            int purged = store.purgeOldKpiSnapshots(TENANT, Instant.now()).getResult();

            assertThat(purged).isEqualTo(2);
            verify(client).delete(TENANT, DataCloudAnalyticsStore.KPI_COLLECTION, "old1");
            verify(client).delete(TENANT, DataCloudAnalyticsStore.KPI_COLLECTION, "old2");
        }

        @Test
        @DisplayName("saveKpiSnapshot: returns failed promise when client fails")
        void saveKpiSnapshot_whenClientFails_propagatesException() {
            KpiSnapshot input = KpiSnapshot.of("cpu", 50.0, "%");
            when(client.save(anyString(), anyString(), anyMap()))
                    .thenReturn(Promise.ofException(new RuntimeException("storage down")));

            Promise<KpiSnapshot> result = store.saveKpiSnapshot(TENANT, input);

            assertThat(result.isException()).isTrue();
        }
    }

    // =========================================================================
    // Anomaly Records
    // =========================================================================

    @Nested
    @DisplayName("Anomaly Records")
    class AnomalyRecordTests {

        @Test
        @DisplayName("saveAnomaly: writes to ANOMALY_COLLECTION with resolved=false")
        void saveAnomaly_writesToAnomalyCollection() {
            AnomalyRecord input = AnomalyRecord.of("FREQUENCY_SPIKE", "HIGH", 0.92, "Detected spike");
            String assignedId = UUID.randomUUID().toString();

            when(client.save(eq(TENANT), eq(DataCloudAnalyticsStore.ANOMALY_COLLECTION), anyMap()))
                    .thenReturn(Promise.of(entity(assignedId, anomalyData(assignedId, "FREQUENCY_SPIKE", "HIGH", false))));

            AnomalyRecord result = store.saveAnomaly(TENANT, input).getResult();

            assertThat(result).isNotNull();
            assertThat(result.anomalyType()).isEqualTo("FREQUENCY_SPIKE");
            assertThat(result.severity()).isEqualTo("HIGH");
            assertThat(result.resolved()).isFalse();
        }

        @Test
        @DisplayName("saveAnomaly: persisted data has resolved=false by default")
        void saveAnomaly_capturedDataHasResolvedFalse() {
            AnomalyRecord input = AnomalyRecord.of("PATTERN_DRIFT", "CRITICAL", 0.99, "Drift detected");
            when(client.save(anyString(), anyString(), anyMap()))
                    .thenReturn(Promise.of(entity("aid", anomalyData("aid", "PATTERN_DRIFT", "CRITICAL", false))));

            store.saveAnomaly(TENANT, input).getResult();

            ArgumentCaptor<Map<String, Object>> cap = ArgumentCaptor.forClass(Map.class);
            verify(client).save(eq(TENANT), eq(DataCloudAnalyticsStore.ANOMALY_COLLECTION), cap.capture());
            assertThat(cap.getValue().get("resolved")).isEqualTo(false);
        }

        @Test
        @DisplayName("queryAnomalies: queries and maps all anomaly entities")
        void queryAnomalies_mapsEntities() {
            when(client.query(eq(TENANT), eq(DataCloudAnalyticsStore.ANOMALY_COLLECTION), any(Query.class)))
                    .thenReturn(Promise.of(List.of(
                            entity("a1", anomalyData("a1", "SPIKE", "HIGH", false)),
                            entity("a2", anomalyData("a2", "DRIFT", "LOW", false))
                    )));

            List<AnomalyRecord> results = store.queryAnomalies(TENANT, (String) null, null, null, 20).getResult();

            assertThat(results).hasSize(2);
        }

        @Test
        @DisplayName("countUnresolvedAnomalies: returns count from query results")
        void countUnresolvedAnomalies_returnsCount() {
            when(client.query(eq(TENANT), eq(DataCloudAnalyticsStore.ANOMALY_COLLECTION), any(Query.class)))
                    .thenReturn(Promise.of(List.of(
                            entity("a1", anomalyData("a1", "SPIKE", "HIGH", false)),
                            entity("a2", anomalyData("a2", "DRIFT", "MEDIUM", false))
                    )));

            long count = store.countUnresolvedAnomalies(TENANT, "HIGH").getResult();

            assertThat(count).isEqualTo(2);
        }

        @Test
        @DisplayName("resolveAnomaly: updates entity resolved=true and resolvedBy fields")
        void resolveAnomaly_marksResolved() {
            String anomalyId = "anomaly-42";
            when(client.findById(TENANT, DataCloudAnalyticsStore.ANOMALY_COLLECTION, anomalyId))
                    .thenReturn(Promise.of(Optional.of(
                            entity(anomalyId, anomalyData(anomalyId, "DRIFT", "HIGH", false))
                    )));
            when(client.save(eq(TENANT), eq(DataCloudAnalyticsStore.ANOMALY_COLLECTION), anyMap()))
                    .thenReturn(Promise.of(entity(anomalyId, anomalyData(anomalyId, "DRIFT", "HIGH", true))));

            store.resolveAnomaly(TENANT, anomalyId, "ops-team").getResult();

            ArgumentCaptor<Map<String, Object>> cap = ArgumentCaptor.forClass(Map.class);
            verify(client).save(eq(TENANT), eq(DataCloudAnalyticsStore.ANOMALY_COLLECTION), cap.capture());
            assertThat(cap.getValue().get("resolved")).isEqualTo(true);
            assertThat(cap.getValue().get("resolvedBy")).isEqualTo("ops-team");
        }

        @Test
        @DisplayName("resolveAnomaly: is no-op when anomaly not found")
        void resolveAnomaly_whenNotFound_noSave() {
            String anomalyId = "ghost-anomaly";
            when(client.findById(TENANT, DataCloudAnalyticsStore.ANOMALY_COLLECTION, anomalyId))
                    .thenReturn(Promise.of(Optional.empty()));

            store.resolveAnomaly(TENANT, anomalyId, "ops-team").getResult();

            verify(client, never()).save(anyString(), anyString(), anyMap());
        }
    }

    // =========================================================================
    // Metric Data Points
    // =========================================================================

    @Nested
    @DisplayName("Metric Data Points")
    class MetricDataPointTests {

        @Test
        @DisplayName("saveMetricDataPoint: writes to METRICS_COLLECTION")
        void saveMetricDataPoint_writesToMetricsCollection() {
            MetricDataPoint input = MetricDataPoint.of("event.latency.p99", 120.0, "ms");
            String id = UUID.randomUUID().toString();

            when(client.save(eq(TENANT), eq(DataCloudAnalyticsStore.METRICS_COLLECTION), anyMap()))
                    .thenReturn(Promise.of(entity(id, metricData(id, "event.latency.p99", 120.0))));

            MetricDataPoint result = store.saveMetricDataPoint(TENANT, input).getResult();

            assertThat(result).isNotNull();
            assertThat(result.metricName()).isEqualTo("event.latency.p99");
            assertThat(result.value()).isEqualTo(120.0);
        }

        @Test
        @DisplayName("queryMetrics: queries METRICS_COLLECTION and maps results")
        void queryMetrics_mapsAllEntities() {
            when(client.query(eq(TENANT), eq(DataCloudAnalyticsStore.METRICS_COLLECTION), any(Query.class)))
                    .thenReturn(Promise.of(List.of(
                            entity("m1", metricData("m1", "cpu.usage", 45.0)),
                            entity("m2", metricData("m2", "cpu.usage", 55.0))
                    )));

            List<MetricDataPoint> results = store.queryMetrics(TENANT, null, "cpu.usage", null, null, 50).getResult();

            assertThat(results).hasSize(2);
            assertThat(results).extracting(MetricDataPoint::metricName).containsOnly("cpu.usage");
        }

        @Test
        @DisplayName("saveMetricsBatch: saves each data point and returns successfully saved ones")
        void saveMetricsBatch_savesAll() {
            List<MetricDataPoint> batch = List.of(
                    MetricDataPoint.of("mem.usage", 60.0, "%"),
                    MetricDataPoint.of("disk.io", 200.0, "MB/s")
            );
            when(client.save(eq(TENANT), eq(DataCloudAnalyticsStore.METRICS_COLLECTION), anyMap()))
                    .thenReturn(Promise.of(entity("m1", metricData("m1", "mem.usage", 60.0))))
                    .thenReturn(Promise.of(entity("m2", metricData("m2", "disk.io", 200.0))));

            List<MetricDataPoint> saved = store.saveMetricsBatch(TENANT, batch).getResult();

            assertThat(saved).hasSize(2);
        }

        @Test
        @DisplayName("saveMetricsBatch: partial failure — returns only successfully saved points")
        void saveMetricsBatch_partialFailure_returnsSuccessfulOnes() {
            List<MetricDataPoint> batch = List.of(
                    MetricDataPoint.of("good.metric", 1.0, ""),
                    MetricDataPoint.of("bad.metric", 2.0, "")
            );
            when(client.save(eq(TENANT), eq(DataCloudAnalyticsStore.METRICS_COLLECTION), anyMap()))
                    .thenReturn(Promise.of(entity("m1", metricData("m1", "good.metric", 1.0))))
                    .thenReturn(Promise.ofException(new RuntimeException("partition full")));

            List<MetricDataPoint> saved = store.saveMetricsBatch(TENANT, batch).getResult();

            // partial failure: only one saved
            assertThat(saved).hasSize(1);
        }
    }

    // =========================================================================
    // Helper factories
    // =========================================================================

    private static Entity entity(String id, Map<String, Object> data) {
        return new Entity(id, "test-collection", data,
                Instant.now(), Instant.now(), 1L);
    }

    private static Map<String, Object> kpiData(String id, String kpiName, double value) {
        return Map.of(
                "id", id,
                "kpiName", kpiName,
                "value", value,
                "unit", "%",
                "capturedAt", Instant.now().toString(),
                "tags", ""
        );
    }

    private static Map<String, Object> anomalyData(String id, String type, String severity, boolean resolved) {
        return Map.of(
                "id", id,
                "anomalyType", type,
                "severity", severity,
                "score", 0.85,
                "description", "test anomaly",
                "entityId", "",
                "patternId", "",
                "detectedAt", Instant.now().toString(),
                "resolved", resolved
        );
    }

    private static Map<String, Object> metricData(String id, String metricName, double value) {
        return Map.of(
                "id", id,
                "metricName", metricName,
                "value", value,
                "unit", "ms",
                "entityId", "",
                "recordedAt", Instant.now().toString(),
                "tags", ""
        );
    }
}
