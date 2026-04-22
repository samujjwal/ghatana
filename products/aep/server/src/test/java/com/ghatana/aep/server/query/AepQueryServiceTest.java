/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.aep.server.query;

import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.DataCloudClient.Entity;
import com.ghatana.datacloud.DataCloudClient.Filter;
import com.ghatana.datacloud.DataCloudClient.Sort;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AepQueryService}.
 *
 * @doc.type class
 * @doc.purpose Tests for AepQueryService — pagination, aggregation, and collection queries
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("AepQueryService [GH-90000]")
class AepQueryServiceTest {

    @Mock
    DataCloudClient client;

    AepQueryService service;

    @BeforeEach
    void setUp() { // GH-90000
        service = new AepQueryService(client, new SimpleMeterRegistry()); // GH-90000
    }

    // ─────────────────────────────────────────────────────────
    //  Constructor Validation
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("should reject null DataCloudClient [GH-90000]")
    void rejectsNullClient() { // GH-90000
        assertThatThrownBy(() -> new AepQueryService(null, new SimpleMeterRegistry())) // GH-90000
                .isInstanceOf(NullPointerException.class); // GH-90000
    }

    @Test
    @DisplayName("should reject null MeterRegistry [GH-90000]")
    void rejectsNullRegistry() { // GH-90000
        assertThatThrownBy(() -> new AepQueryService(client, null)) // GH-90000
                .isInstanceOf(NullPointerException.class); // GH-90000
    }

    // ─────────────────────────────────────────────────────────
    //  Pattern Queries
    // ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Pattern Queries [GH-90000]")
    class PatternQueries {

        @Test
        @DisplayName("should map entity fields to PatternSummary [GH-90000]")
        void mapsEntityToPatternSummary() { // GH-90000
            Instant now = Instant.now(); // GH-90000
            Entity e = entityWith("p1", Map.of( // GH-90000
                    "tenantId", "t1",
                    "name", "LoginPattern",
                    "status", "ACTIVE",
                    "priority", 5,
                    "description", "Login event pattern",
                    "createdAt", now.toString(), // GH-90000
                    "updatedAt", now.toString() // GH-90000
            ));

            when(client.query(eq("t1 [GH-90000]"), eq(AepQueryService.COLLECTION_PATTERNS), any()))
                    .thenReturn(Promise.of(List.of(e))); // GH-90000

            AepQueryService.PagedResult<AepQueryService.PatternSummary> result =
                    service.queryPatterns("t1", AepQueryService.QuerySpec.builder().limit(20).build()) // GH-90000
                           .getResult(); // GH-90000

            assertThat(result.items()).hasSize(1); // GH-90000
            AepQueryService.PatternSummary s = result.items().get(0); // GH-90000
            assertThat(s.id()).isEqualTo("p1 [GH-90000]");
            assertThat(s.name()).isEqualTo("LoginPattern [GH-90000]");
            assertThat(s.status()).isEqualTo("ACTIVE [GH-90000]");
            assertThat(s.priority()).isEqualTo(5); // GH-90000
            assertThat(s.tenantId()).isEqualTo("t1 [GH-90000]");
        }

        @Test
        @DisplayName("should not have more pages when fetched count equals limit [GH-90000]")
        void noMorePages() { // GH-90000
            when(client.query(any(), any(), any())) // GH-90000
                    .thenReturn(Promise.of(List.of(entityWith("p1", Map.of("tenantId", "t1"))))); // GH-90000

            AepQueryService.PagedResult<AepQueryService.PatternSummary> result =
                    service.queryPatterns("t1", AepQueryService.QuerySpec.builder().limit(20).build()) // GH-90000
                           .getResult(); // GH-90000

            assertThat(result.hasMore()).isFalse(); // GH-90000
            assertThat(result.nextOffset()).isEqualTo(-1); // GH-90000
        }

        @Test
        @DisplayName("should signal hasMore when extra entity is fetched [GH-90000]")
        void hasMoreWhenFetchedExtraEntity() { // GH-90000
            // Request limit=1 → service fetches limit+1=2 entities to detect hasMore
            List<Entity> twoEntities = List.of( // GH-90000
                    entityWith("p1", Map.of("tenantId", "t1")), // GH-90000
                    entityWith("p2", Map.of("tenantId", "t1")) // GH-90000
            );

            when(client.query(any(), any(), any())) // GH-90000
                    .thenReturn(Promise.of(twoEntities)); // GH-90000

            AepQueryService.PagedResult<AepQueryService.PatternSummary> result =
                    service.queryPatterns("t1", AepQueryService.QuerySpec.builder().limit(1).build()) // GH-90000
                           .getResult(); // GH-90000

            // Only the first entity should be in the page
            assertThat(result.items()).hasSize(1); // GH-90000
            assertThat(result.hasMore()).isTrue(); // GH-90000
            assertThat(result.nextOffset()).isEqualTo(1); // GH-90000
        }

        @Test
        @DisplayName("should tolerate missing optional fields in entity data [GH-90000]")
        void toleratesMissingFields() { // GH-90000
            Entity e = entityWith("p1", Map.of("tenantId", "t1")); // only required field // GH-90000

            when(client.query(any(), any(), any())) // GH-90000
                    .thenReturn(Promise.of(List.of(e))); // GH-90000

            AepQueryService.PagedResult<AepQueryService.PatternSummary> result =
                    service.queryPatterns("t1", AepQueryService.QuerySpec.all()) // GH-90000
                           .getResult(); // GH-90000

            AepQueryService.PatternSummary s = result.items().get(0); // GH-90000
            assertThat(s.name()).isNull(); // GH-90000
            assertThat(s.priority()).isEqualTo(0); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────
    //  Pipeline Queries
    // ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Pipeline Queries [GH-90000]")
    class PipelineQueries {

        @Test
        @DisplayName("should map entity fields to PipelineSummary [GH-90000]")
        void mapsPipelineSummary() { // GH-90000
            Instant now = Instant.now(); // GH-90000
            Entity e = entityWith("pipe1", Map.of( // GH-90000
                    "tenantId", "t1",
                    "name", "DataPipeline",
                    "status", "RUNNING",
                    "stageCount", 3,
                    "createdAt", now.toString(), // GH-90000
                    "updatedAt", now.toString() // GH-90000
            ));

            when(client.query(eq("t1 [GH-90000]"), eq(AepQueryService.COLLECTION_PIPELINES), any()))
                    .thenReturn(Promise.of(List.of(e))); // GH-90000

            AepQueryService.PagedResult<AepQueryService.PipelineSummary> result =
                    service.queryPipelines("t1", AepQueryService.QuerySpec.all()) // GH-90000
                           .getResult(); // GH-90000

            assertThat(result.items()).hasSize(1); // GH-90000
            AepQueryService.PipelineSummary s = result.items().get(0); // GH-90000
            assertThat(s.id()).isEqualTo("pipe1 [GH-90000]");
            assertThat(s.stageCount()).isEqualTo(3); // GH-90000
            assertThat(s.status()).isEqualTo("RUNNING [GH-90000]");
        }
    }

    // ─────────────────────────────────────────────────────────
    //  Anomaly Queries
    // ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Anomaly Queries [GH-90000]")
    class AnomalyQueries {

        @Test
        @DisplayName("should map entity fields to AnomalySummary [GH-90000]")
        void mapsAnomalySummary() { // GH-90000
            Instant detected = Instant.parse("2026-03-01T00:00:00Z [GH-90000]");
            Entity e = entityWith("a1", Map.of( // GH-90000
                    "tenantId", "t1",
                    "kpiName", "LoginRate",
                    "severity", "HIGH",
                    "zScore", 3.7,
                    "detectedAt", detected.toString(), // GH-90000
                    "status", "OPEN"
            ));

            when(client.query(eq("t1 [GH-90000]"), eq(AepQueryService.COLLECTION_ANOMALIES), any()))
                    .thenReturn(Promise.of(List.of(e))); // GH-90000

            AepQueryService.PagedResult<AepQueryService.AnomalySummary> result =
                    service.queryAnomalies("t1", AepQueryService.QuerySpec.all()) // GH-90000
                           .getResult(); // GH-90000

            AepQueryService.AnomalySummary s = result.items().get(0); // GH-90000
            assertThat(s.severity()).isEqualTo("HIGH [GH-90000]");
            assertThat(s.zScore()).isEqualTo(3.7); // GH-90000
            assertThat(s.detectedAt()).isEqualTo(detected); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────
    //  KPI Queries
    // ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("KPI Queries [GH-90000]")
    class KpiQueries {

        @Test
        @DisplayName("should map entity fields to KpiSummary [GH-90000]")
        void mapsKpiSummary() { // GH-90000
            Instant recorded = Instant.parse("2026-03-02T10:00:00Z [GH-90000]");
            Entity e = entityWith("k1", Map.of( // GH-90000
                    "tenantId", "t1",
                    "kpiName", "Revenue",
                    "value", 1500.5,
                    "unit", "USD",
                    "recordedAt", recorded.toString() // GH-90000
            ));

            when(client.query(eq("t1 [GH-90000]"), eq(AepQueryService.COLLECTION_KPI), any()))
                    .thenReturn(Promise.of(List.of(e))); // GH-90000

            AepQueryService.PagedResult<AepQueryService.KpiSummary> result =
                    service.queryKpis("t1", AepQueryService.QuerySpec.all()) // GH-90000
                           .getResult(); // GH-90000

            AepQueryService.KpiSummary s = result.items().get(0); // GH-90000
            assertThat(s.kpiName()).isEqualTo("Revenue [GH-90000]");
            assertThat(s.value()).isEqualTo(1500.5); // GH-90000
            assertThat(s.unit()).isEqualTo("USD [GH-90000]");
            assertThat(s.recordedAt()).isEqualTo(recorded); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────
    //  Aggregation
    // ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Aggregation [GH-90000]")
    class Aggregation {

        @Test
        @DisplayName("should group-by a field and count entries [GH-90000]")
        void groupByField() { // GH-90000
            List<Entity> entities = List.of( // GH-90000
                    entityWith("a1", Map.of("tenantId", "t1", "severity", "HIGH")), // GH-90000
                    entityWith("a2", Map.of("tenantId", "t1", "severity", "HIGH")), // GH-90000
                    entityWith("a3", Map.of("tenantId", "t1", "severity", "LOW")) // GH-90000
            );

            when(client.query(eq("t1 [GH-90000]"), eq("aep_anomalies [GH-90000]"), any()))
                    .thenReturn(Promise.of(entities)); // GH-90000

            AepQueryService.AggregateResult result =
                    service.aggregate("t1", "aep_anomalies", // GH-90000
                                    AepQueryService.AggregateSpec.groupBy("severity", 100)) // GH-90000
                           .getResult(); // GH-90000

            assertThat(result.totalCount()).isEqualTo(3); // GH-90000
            @SuppressWarnings("unchecked [GH-90000]")
            Map<String, Long> groups = (Map<String, Long>) result.aggregates().get("groupBy [GH-90000]");
            assertThat(groups).containsEntry("HIGH", 2L).containsEntry("LOW", 1L); // GH-90000
        }

        @Test
        @DisplayName("should compute sum and average for a numeric field [GH-90000]")
        void sumAndAverage() { // GH-90000
            List<Entity> entities = List.of( // GH-90000
                    entityWith("k1", Map.of("tenantId", "t1", "value", 10.0)), // GH-90000
                    entityWith("k2", Map.of("tenantId", "t1", "value", 20.0)), // GH-90000
                    entityWith("k3", Map.of("tenantId", "t1", "value", 30.0)) // GH-90000
            );

            when(client.query(eq("t1 [GH-90000]"), eq("aep_kpi_snapshots [GH-90000]"), any()))
                    .thenReturn(Promise.of(entities)); // GH-90000

            AepQueryService.AggregateResult result =
                    service.aggregate("t1", "aep_kpi_snapshots", // GH-90000
                                    AepQueryService.AggregateSpec.sum("value [GH-90000]"))
                           .getResult(); // GH-90000

            assertThat((Double) result.aggregates().get("sum [GH-90000]")).isEqualTo(60.0);
            assertThat((Double) result.aggregates().get("avg [GH-90000]")).isEqualTo(20.0);
        }

        @Test
        @DisplayName("should collect distinct field values [GH-90000]")
        void distinctValues() { // GH-90000
            List<Entity> entities = List.of( // GH-90000
                    entityWith("p1", Map.of("tenantId", "t1", "status", "ACTIVE")), // GH-90000
                    entityWith("p2", Map.of("tenantId", "t1", "status", "ACTIVE")), // GH-90000
                    entityWith("p3", Map.of("tenantId", "t1", "status", "INACTIVE")) // GH-90000
            );

            when(client.query(eq("t1 [GH-90000]"), eq("aep_patterns [GH-90000]"), any()))
                    .thenReturn(Promise.of(entities)); // GH-90000

            AepQueryService.AggregateResult result =
                    service.aggregate("t1", "aep_patterns", // GH-90000
                                    AepQueryService.AggregateSpec.distinct("status [GH-90000]"))
                           .getResult(); // GH-90000

            assertThat((Integer) result.aggregates().get("distinctCount [GH-90000]")).isEqualTo(2);
        }

        @Test
        @DisplayName("should return zero aggregate when collection is empty [GH-90000]")
        void emptyCollection() { // GH-90000
            when(client.query(any(), any(), any())) // GH-90000
                    .thenReturn(Promise.of(List.of())); // GH-90000

            AepQueryService.AggregateResult result =
                    service.aggregate("t1", "aep_patterns", // GH-90000
                                    AepQueryService.AggregateSpec.sum("value [GH-90000]"))
                           .getResult(); // GH-90000

            assertThat(result.totalCount()).isEqualTo(0); // GH-90000
            assertThat((Double) result.aggregates().get("sum [GH-90000]")).isEqualTo(0.0);
        }
    }

    // ─────────────────────────────────────────────────────────
    //  Tenant Summary
    // ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Tenant Summary [GH-90000]")
    class TenantSummaryTests {

        @Test
        @DisplayName("should aggregate counts from all four AEP collections [GH-90000]")
        void aggregatesAllCollections() { // GH-90000
            when(client.query(eq("t1 [GH-90000]"), eq(AepQueryService.COLLECTION_PATTERNS), any()))
                    .thenReturn(Promise.of(List.of(entityWith("p1", Map.of("tenantId", "t1"))))); // GH-90000
            when(client.query(eq("t1 [GH-90000]"), eq(AepQueryService.COLLECTION_PIPELINES), any()))
                    .thenReturn(Promise.of(List.of( // GH-90000
                            entityWith("pipe1", Map.of("tenantId", "t1")), // GH-90000
                            entityWith("pipe2", Map.of("tenantId", "t1"))))); // GH-90000
            when(client.query(eq("t1 [GH-90000]"), eq(AepQueryService.COLLECTION_ANOMALIES), any()))
                    .thenReturn(Promise.of(List.of())); // GH-90000
            when(client.query(eq("t1 [GH-90000]"), eq(AepQueryService.COLLECTION_KPI), any()))
                    .thenReturn(Promise.of(List.of(entityWith("k1", Map.of("tenantId", "t1"))))); // GH-90000

            AepQueryService.TenantSummary summary =
                    service.tenantSummary("t1 [GH-90000]").getResult();

            assertThat(summary.tenantId()).isEqualTo("t1 [GH-90000]");
            assertThat(summary.patternCount()).isEqualTo(1); // GH-90000
            assertThat(summary.pipelineCount()).isEqualTo(2); // GH-90000
            assertThat(summary.anomalyCount()).isEqualTo(0); // GH-90000
            assertThat(summary.kpiCount()).isEqualTo(1); // GH-90000
            assertThat(summary.computedAt()).isNotNull(); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────
    //  QuerySpec Builder
    // ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("QuerySpec Builder [GH-90000]")
    class QuerySpecBuilderTests {

        @Test
        @DisplayName("should build with defaults when no values provided [GH-90000]")
        void defaultSpec() { // GH-90000
            AepQueryService.QuerySpec spec = AepQueryService.QuerySpec.builder().build(); // GH-90000
            assertThat(spec.offset()).isEqualTo(0); // GH-90000
            assertThat(spec.limit()).isEqualTo(50); // DEFAULT_PAGE_SIZE // GH-90000
            assertThat(spec.filters()).isEmpty(); // GH-90000
            assertThat(spec.sorts()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("should compute offset from page index and size [GH-90000]")
        void pageHelperMethod() { // GH-90000
            AepQueryService.QuerySpec spec = AepQueryService.QuerySpec.builder() // GH-90000
                    .page(3, 20) // GH-90000
                    .build(); // GH-90000
            assertThat(spec.offset()).isEqualTo(60); // GH-90000
            assertThat(spec.limit()).isEqualTo(20); // GH-90000
        }

        @Test
        @DisplayName("should store multiple filters and sorts [GH-90000]")
        void multipleFiltersAndSorts() { // GH-90000
            AepQueryService.QuerySpec spec = AepQueryService.QuerySpec.builder() // GH-90000
                    .filter(Filter.eq("status", "ACTIVE")) // GH-90000
                    .filter(Filter.gt("priority", "3")) // GH-90000
                    .sort(Sort.desc("createdAt [GH-90000]"))
                    .sort(Sort.asc("name [GH-90000]"))
                    .build(); // GH-90000

            assertThat(spec.filters()).hasSize(2); // GH-90000
            assertThat(spec.sorts()).hasSize(2); // GH-90000
        }

        @Test
        @DisplayName("QuerySpec.all() should set max limit and zero offset [GH-90000]")
        void allSpec() { // GH-90000
            AepQueryService.QuerySpec spec = AepQueryService.QuerySpec.all(); // GH-90000
            assertThat(spec.offset()).isEqualTo(0); // GH-90000
            assertThat(spec.limit()).isEqualTo(1000); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────
    //  Helper
    // ─────────────────────────────────────────────────────────

    private static Entity entityWith(String id, Map<String, Object> data) { // GH-90000
        return new Entity(id, "test_collection", data, Instant.now(), Instant.now(), 1L); // GH-90000
    }
}
