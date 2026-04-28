/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
 * {@link Promise#of(Object)} values, so no Eventloop is required — promises // GH-90000
 * are already resolved and {@code .getResult()} is safe to call. // GH-90000
 *
 * @doc.type class
 * @doc.purpose Unit tests for DataCloudAnalyticsStore — KPI, Anomaly, Metrics collections
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("DataCloudAnalyticsStore")
class DataCloudAnalyticsStoreTest {

    private static final String TENANT = "tenant-analytics";

    @Mock
    private DataCloudClient client;

    private DataCloudAnalyticsStore store;

    @BeforeEach
    void setUp() { // GH-90000
        store = new DataCloudAnalyticsStore(client); // GH-90000
    }

    // =========================================================================
    // KPI Snapshots
    // =========================================================================

    @Nested
    @DisplayName("KPI Snapshots")
    class KpiSnapshotTests {

        @Test
        @DisplayName("saveKpiSnapshot: writes to KPI_COLLECTION and returns hydrated snapshot")
        void saveKpiSnapshot_writeToKpiCollection() { // GH-90000
            KpiSnapshot input = KpiSnapshot.of("cpu.usage", 72.3, "%"); // GH-90000
            String assignedId = UUID.randomUUID().toString(); // GH-90000

            Entity stubEntity = entity(assignedId, Map.of( // GH-90000
                    "id", assignedId,
                    "kpiName", "cpu.usage",
                    "value", 72.3,
                    "unit", "%",
                    "capturedAt", Instant.now().toString(), // GH-90000
                    "tags", ""
            ));
            when(client.save(eq(TENANT), eq(DataCloudAnalyticsStore.KPI_COLLECTION), anyMap())) // GH-90000
                    .thenReturn(Promise.of(stubEntity)); // GH-90000

            KpiSnapshot result = store.saveKpiSnapshot(TENANT, input).getResult(); // GH-90000

            assertThat(result).isNotNull(); // GH-90000
            assertThat(result.kpiName()).isEqualTo("cpu.usage");
            assertThat(result.value()).isEqualTo(72.3); // GH-90000
            assertThat(result.unit()).isEqualTo("%");
        }

        @Test
        @DisplayName("saveKpiSnapshot: assigns ID before persisting")
        void saveKpiSnapshot_assignsId() { // GH-90000
            KpiSnapshot input = KpiSnapshot.of("event.latency", 45.0, "ms"); // GH-90000
            when(client.save(anyString(), anyString(), anyMap())) // GH-90000
                    .thenReturn(Promise.of(entity("new-id", Map.of( // GH-90000
                            "id", "new-id", "kpiName", "event.latency",
                            "value", 45.0, "unit", "ms",
                            "capturedAt", Instant.now().toString(), "tags", "" // GH-90000
                    ))));

            store.saveKpiSnapshot(TENANT, input).getResult(); // GH-90000

            ArgumentCaptor<Map<String, Object>> captor = mapCaptor(); // GH-90000
            verify(client).save(eq(TENANT), eq(DataCloudAnalyticsStore.KPI_COLLECTION), captor.capture()); // GH-90000
            assertThat(captor.getValue().get("id")).isNotNull().asString().isNotBlank();
        }

        @Test
        @DisplayName("queryKpiSnapshots: queries KPI collection and maps all results")
        void queryKpiSnapshots_mapsAllEntities() { // GH-90000
            String kpiName = "throughput";
            List<Entity> stubList = List.of( // GH-90000
                    entity("e1", kpiData("e1", kpiName, 100.0)), // GH-90000
                    entity("e2", kpiData("e2", kpiName, 110.0)) // GH-90000
            );
            when(client.query(eq(TENANT), eq(DataCloudAnalyticsStore.KPI_COLLECTION), any(Query.class))) // GH-90000
                    .thenReturn(Promise.of(stubList)); // GH-90000

            List<KpiSnapshot> results = store.queryKpiSnapshots(TENANT, kpiName, null, null, 10).getResult(); // GH-90000

            assertThat(results).hasSize(2); // GH-90000
            assertThat(results.get(0).kpiName()).isEqualTo(kpiName); // GH-90000
            assertThat(results.get(1).value()).isEqualTo(110.0); // GH-90000
        }

        @Test
        @DisplayName("getLatestKpi: returns present Optional when entity found")
        void getLatestKpi_whenEntityFound_returnsPresent() { // GH-90000
            String kpiName = "error.rate";
            Entity stub = entity("e1", kpiData("e1", kpiName, 0.02)); // GH-90000
            when(client.query(eq(TENANT), eq(DataCloudAnalyticsStore.KPI_COLLECTION), any(Query.class))) // GH-90000
                    .thenReturn(Promise.of(List.of(stub))); // GH-90000

            Optional<KpiSnapshot> result = store.getLatestKpi(TENANT, kpiName).getResult(); // GH-90000

            assertThat(result).isPresent(); // GH-90000
            assertThat(result.get().kpiName()).isEqualTo(kpiName); // GH-90000
        }

        @Test
        @DisplayName("getLatestKpi: returns empty Optional when no entities found")
        void getLatestKpi_whenNotFound_returnsEmpty() { // GH-90000
            when(client.query(eq(TENANT), eq(DataCloudAnalyticsStore.KPI_COLLECTION), any(Query.class))) // GH-90000
                    .thenReturn(Promise.of(List.of())); // GH-90000

            Optional<KpiSnapshot> result = store.getLatestKpi(TENANT, "missing.kpi").getResult(); // GH-90000

            assertThat(result).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("purgeOldKpiSnapshots: queries then deletes each entity individually")
        void purgeOldKpiSnapshots_deletesEachEntity() { // GH-90000
            List<Entity> stale = List.of( // GH-90000
                    entity("old1", kpiData("old1", "cpu", 50.0)), // GH-90000
                    entity("old2", kpiData("old2", "cpu", 60.0)) // GH-90000
            );
            when(client.query(eq(TENANT), eq(DataCloudAnalyticsStore.KPI_COLLECTION), any(Query.class))) // GH-90000
                    .thenReturn(Promise.of(stale)); // GH-90000
            when(client.delete(eq(TENANT), eq(DataCloudAnalyticsStore.KPI_COLLECTION), anyString())) // GH-90000
                    .thenReturn(Promise.of((Void) null)); // GH-90000

            int purged = store.purgeOldKpiSnapshots(TENANT, Instant.now()).getResult(); // GH-90000

            assertThat(purged).isEqualTo(2); // GH-90000
            verify(client).delete(TENANT, DataCloudAnalyticsStore.KPI_COLLECTION, "old1"); // GH-90000
            verify(client).delete(TENANT, DataCloudAnalyticsStore.KPI_COLLECTION, "old2"); // GH-90000
        }

        @Test
        @DisplayName("saveKpiSnapshot: returns failed promise when client fails")
        void saveKpiSnapshot_whenClientFails_propagatesException() { // GH-90000
            KpiSnapshot input = KpiSnapshot.of("cpu", 50.0, "%"); // GH-90000
            when(client.save(anyString(), anyString(), anyMap())) // GH-90000
                    .thenReturn(Promise.ofException(new RuntimeException("storage down")));

            Promise<KpiSnapshot> result = store.saveKpiSnapshot(TENANT, input); // GH-90000

            assertThat(result.isException()).isTrue(); // GH-90000
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
        void saveAnomaly_writesToAnomalyCollection() { // GH-90000
            AnomalyRecord input = AnomalyRecord.of("FREQUENCY_SPIKE", "HIGH", 0.92, "Detected spike"); // GH-90000
            String assignedId = UUID.randomUUID().toString(); // GH-90000

            when(client.save(eq(TENANT), eq(DataCloudAnalyticsStore.ANOMALY_COLLECTION), anyMap())) // GH-90000
                    .thenReturn(Promise.of(entity(assignedId, anomalyData(assignedId, "FREQUENCY_SPIKE", "HIGH", false)))); // GH-90000

            AnomalyRecord result = store.saveAnomaly(TENANT, input).getResult(); // GH-90000

            assertThat(result).isNotNull(); // GH-90000
            assertThat(result.anomalyType()).isEqualTo("FREQUENCY_SPIKE");
            assertThat(result.severity()).isEqualTo("HIGH");
            assertThat(result.resolved()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("saveAnomaly: persisted data has resolved=false by default")
        void saveAnomaly_capturedDataHasResolvedFalse() { // GH-90000
            AnomalyRecord input = AnomalyRecord.of("PATTERN_DRIFT", "CRITICAL", 0.99, "Drift detected"); // GH-90000
            when(client.save(anyString(), anyString(), anyMap())) // GH-90000
                    .thenReturn(Promise.of(entity("aid", anomalyData("aid", "PATTERN_DRIFT", "CRITICAL", false)))); // GH-90000

            store.saveAnomaly(TENANT, input).getResult(); // GH-90000

            ArgumentCaptor<Map<String, Object>> cap = mapCaptor(); // GH-90000
            verify(client).save(eq(TENANT), eq(DataCloudAnalyticsStore.ANOMALY_COLLECTION), cap.capture()); // GH-90000
            assertThat(cap.getValue().get("resolved")).isEqualTo(false);
        }

        @Test
        @DisplayName("queryAnomalies: queries and maps all anomaly entities")
        void queryAnomalies_mapsEntities() { // GH-90000
            when(client.query(eq(TENANT), eq(DataCloudAnalyticsStore.ANOMALY_COLLECTION), any(Query.class))) // GH-90000
                    .thenReturn(Promise.of(List.of( // GH-90000
                            entity("a1", anomalyData("a1", "SPIKE", "HIGH", false)), // GH-90000
                            entity("a2", anomalyData("a2", "DRIFT", "LOW", false)) // GH-90000
                    )));

            List<AnomalyRecord> results = store.queryAnomalies(TENANT, (String) null, null, null, 20).getResult(); // GH-90000

            assertThat(results).hasSize(2); // GH-90000
        }

        @Test
        @DisplayName("countUnresolvedAnomalies: returns count from query results")
        void countUnresolvedAnomalies_returnsCount() { // GH-90000
            when(client.query(eq(TENANT), eq(DataCloudAnalyticsStore.ANOMALY_COLLECTION), any(Query.class))) // GH-90000
                    .thenReturn(Promise.of(List.of( // GH-90000
                            entity("a1", anomalyData("a1", "SPIKE", "HIGH", false)), // GH-90000
                            entity("a2", anomalyData("a2", "DRIFT", "MEDIUM", false)) // GH-90000
                    )));

            long count = store.countUnresolvedAnomalies(TENANT, "HIGH").getResult(); // GH-90000

            assertThat(count).isEqualTo(2); // GH-90000
        }

        @Test
        @DisplayName("resolveAnomaly: updates entity resolved=true and resolvedBy fields")
        void resolveAnomaly_marksResolved() { // GH-90000
            String anomalyId = "anomaly-42";
            when(client.findById(TENANT, DataCloudAnalyticsStore.ANOMALY_COLLECTION, anomalyId)) // GH-90000
                    .thenReturn(Promise.of(Optional.of( // GH-90000
                            entity(anomalyId, anomalyData(anomalyId, "DRIFT", "HIGH", false)) // GH-90000
                    )));
            when(client.save(eq(TENANT), eq(DataCloudAnalyticsStore.ANOMALY_COLLECTION), anyMap())) // GH-90000
                    .thenReturn(Promise.of(entity(anomalyId, anomalyData(anomalyId, "DRIFT", "HIGH", true)))); // GH-90000

            store.resolveAnomaly(TENANT, anomalyId, "ops-team").getResult(); // GH-90000

            ArgumentCaptor<Map<String, Object>> cap = mapCaptor(); // GH-90000
            verify(client).save(eq(TENANT), eq(DataCloudAnalyticsStore.ANOMALY_COLLECTION), cap.capture()); // GH-90000
            assertThat(cap.getValue().get("resolved")).isEqualTo(true);
            assertThat(cap.getValue().get("resolvedBy")).isEqualTo("ops-team");
        }

        @Test
        @DisplayName("resolveAnomaly: is no-op when anomaly not found")
        void resolveAnomaly_whenNotFound_noSave() { // GH-90000
            String anomalyId = "ghost-anomaly";
            when(client.findById(TENANT, DataCloudAnalyticsStore.ANOMALY_COLLECTION, anomalyId)) // GH-90000
                    .thenReturn(Promise.of(Optional.empty())); // GH-90000

            store.resolveAnomaly(TENANT, anomalyId, "ops-team").getResult(); // GH-90000

            verify(client, never()).save(anyString(), anyString(), anyMap()); // GH-90000
        }

        @Test
        @DisplayName("markFalsePositive: writes false-positive metadata and resolves anomaly")
        void markFalsePositive_updatesEntity() { // GH-90000
            String anomalyId = "anomaly-42";
            when(client.findById(TENANT, DataCloudAnalyticsStore.ANOMALY_COLLECTION, anomalyId)) // GH-90000
                    .thenReturn(Promise.of(Optional.of(
                            entity(anomalyId, anomalyData(anomalyId, "DRIFT", "HIGH", false)) // GH-90000
                    )));
            when(client.save(eq(TENANT), eq(DataCloudAnalyticsStore.ANOMALY_COLLECTION), anyMap())) // GH-90000
                    .thenReturn(Promise.of(entity(anomalyId, anomalyData(anomalyId, "DRIFT", "HIGH", true)))); // GH-90000

            AnomalyRecord result = store.markFalsePositive( // GH-90000
                    TENANT,
                    anomalyId,
                    "ops-team",
                    "Known deploy spike",
                    "Reviewed against release calendar").getResult();

            assertThat(result.resolved()).isTrue(); // GH-90000
            ArgumentCaptor<Map<String, Object>> cap = mapCaptor(); // GH-90000
            verify(client).save(eq(TENANT), eq(DataCloudAnalyticsStore.ANOMALY_COLLECTION), cap.capture()); // GH-90000
            assertThat(cap.getValue()).containsEntry("falsePositive", true); // GH-90000
            assertThat(cap.getValue()).containsEntry("falsePositiveBy", "ops-team"); // GH-90000
            assertThat(cap.getValue()).containsEntry("falsePositiveReason", "Known deploy spike"); // GH-90000
            assertThat(cap.getValue()).containsEntry("resolved", true); // GH-90000
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
        void saveMetricDataPoint_writesToMetricsCollection() { // GH-90000
            MetricDataPoint input = MetricDataPoint.of("event.latency.p99", 120.0, "ms"); // GH-90000
            String id = UUID.randomUUID().toString(); // GH-90000

            when(client.save(eq(TENANT), eq(DataCloudAnalyticsStore.METRICS_COLLECTION), anyMap())) // GH-90000
                    .thenReturn(Promise.of(entity(id, metricData(id, "event.latency.p99", 120.0)))); // GH-90000

            MetricDataPoint result = store.saveMetricDataPoint(TENANT, input).getResult(); // GH-90000

            assertThat(result).isNotNull(); // GH-90000
            assertThat(result.metricName()).isEqualTo("event.latency.p99");
            assertThat(result.value()).isEqualTo(120.0); // GH-90000
        }

        @Test
        @DisplayName("queryMetrics: queries METRICS_COLLECTION and maps results")
        void queryMetrics_mapsAllEntities() { // GH-90000
            when(client.query(eq(TENANT), eq(DataCloudAnalyticsStore.METRICS_COLLECTION), any(Query.class))) // GH-90000
                    .thenReturn(Promise.of(List.of( // GH-90000
                            entity("m1", metricData("m1", "cpu.usage", 45.0)), // GH-90000
                            entity("m2", metricData("m2", "cpu.usage", 55.0)) // GH-90000
                    )));

            List<MetricDataPoint> results = store.queryMetrics(TENANT, null, "cpu.usage", null, null, 50).getResult(); // GH-90000

            assertThat(results).hasSize(2); // GH-90000
            assertThat(results).extracting(MetricDataPoint::metricName).containsOnly("cpu.usage");
        }

        @Test
        @DisplayName("saveMetricsBatch: saves each data point and returns successfully saved ones")
        void saveMetricsBatch_savesAll() { // GH-90000
            List<MetricDataPoint> batch = List.of( // GH-90000
                    MetricDataPoint.of("mem.usage", 60.0, "%"), // GH-90000
                    MetricDataPoint.of("disk.io", 200.0, "MB/s") // GH-90000
            );
            when(client.save(eq(TENANT), eq(DataCloudAnalyticsStore.METRICS_COLLECTION), anyMap())) // GH-90000
                    .thenReturn(Promise.of(entity("m1", metricData("m1", "mem.usage", 60.0)))) // GH-90000
                    .thenReturn(Promise.of(entity("m2", metricData("m2", "disk.io", 200.0)))); // GH-90000

            List<MetricDataPoint> saved = store.saveMetricsBatch(TENANT, batch).getResult(); // GH-90000

            assertThat(saved).hasSize(2); // GH-90000
        }

        @Test
        @DisplayName("saveMetricsBatch: partial failure — returns only successfully saved points")
        void saveMetricsBatch_partialFailure_returnsSuccessfulOnes() { // GH-90000
            List<MetricDataPoint> batch = List.of( // GH-90000
                    MetricDataPoint.of("good.metric", 1.0, ""), // GH-90000
                    MetricDataPoint.of("bad.metric", 2.0, "") // GH-90000
            );
            when(client.save(eq(TENANT), eq(DataCloudAnalyticsStore.METRICS_COLLECTION), anyMap())) // GH-90000
                    .thenReturn(Promise.of(entity("m1", metricData("m1", "good.metric", 1.0)))) // GH-90000
                    .thenReturn(Promise.ofException(new RuntimeException("partition full")));

            List<MetricDataPoint> saved = store.saveMetricsBatch(TENANT, batch).getResult(); // GH-90000

            // partial failure: only one saved
            assertThat(saved).hasSize(1); // GH-90000
        }
    }

    // =========================================================================
    // Helper factories
    // =========================================================================

    private static Entity entity(String id, Map<String, Object> data) { // GH-90000
        return new Entity(id, "test-collection", data, // GH-90000
                Instant.now(), Instant.now(), 1L); // GH-90000
    }

        @SuppressWarnings({"unchecked", "rawtypes"}) // GH-90000
        private static ArgumentCaptor<Map<String, Object>> mapCaptor() { // GH-90000
                return (ArgumentCaptor) ArgumentCaptor.forClass(Map.class); // GH-90000
        }

    private static Map<String, Object> kpiData(String id, String kpiName, double value) { // GH-90000
        return Map.of( // GH-90000
                "id", id,
                "kpiName", kpiName,
                "value", value,
                "unit", "%",
                "capturedAt", Instant.now().toString(), // GH-90000
                "tags", ""
        );
    }

    private static Map<String, Object> anomalyData(String id, String type, String severity, boolean resolved) { // GH-90000
        return Map.of( // GH-90000
                "id", id,
                "anomalyType", type,
                "severity", severity,
                "score", 0.85,
                "description", "test anomaly",
                "entityId", "",
                "patternId", "",
                "detectedAt", Instant.now().toString(), // GH-90000
                "resolved", resolved
        );
    }

    private static Map<String, Object> metricData(String id, String metricName, double value) { // GH-90000
        return Map.of( // GH-90000
                "id", id,
                "metricName", metricName,
                "value", value,
                "unit", "ms",
                "entityId", "",
                "recordedAt", Instant.now().toString(), // GH-90000
                "tags", ""
        );
    }
}
