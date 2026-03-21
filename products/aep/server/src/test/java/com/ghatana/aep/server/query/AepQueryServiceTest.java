/*
 * Copyright (c) 2026 Ghatana Inc.
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
@ExtendWith(MockitoExtension.class)
@DisplayName("AepQueryService")
class AepQueryServiceTest {

    @Mock
    DataCloudClient client;

    AepQueryService service;

    @BeforeEach
    void setUp() {
        service = new AepQueryService(client, new SimpleMeterRegistry());
    }

    // ─────────────────────────────────────────────────────────
    //  Constructor Validation
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("should reject null DataCloudClient")
    void rejectsNullClient() {
        assertThatThrownBy(() -> new AepQueryService(null, new SimpleMeterRegistry()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("should reject null MeterRegistry")
    void rejectsNullRegistry() {
        assertThatThrownBy(() -> new AepQueryService(client, null))
                .isInstanceOf(NullPointerException.class);
    }

    // ─────────────────────────────────────────────────────────
    //  Pattern Queries
    // ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Pattern Queries")
    class PatternQueries {

        @Test
        @DisplayName("should map entity fields to PatternSummary")
        void mapsEntityToPatternSummary() {
            Instant now = Instant.now();
            Entity e = entityWith("p1", Map.of(
                    "tenantId", "t1",
                    "name", "LoginPattern",
                    "status", "ACTIVE",
                    "priority", 5,
                    "description", "Login event pattern",
                    "createdAt", now.toString(),
                    "updatedAt", now.toString()
            ));

            when(client.query(eq("t1"), eq(AepQueryService.COLLECTION_PATTERNS), any()))
                    .thenReturn(Promise.of(List.of(e)));

            AepQueryService.PagedResult<AepQueryService.PatternSummary> result =
                    service.queryPatterns("t1", AepQueryService.QuerySpec.builder().limit(20).build())
                           .getResult();

            assertThat(result.items()).hasSize(1);
            AepQueryService.PatternSummary s = result.items().get(0);
            assertThat(s.id()).isEqualTo("p1");
            assertThat(s.name()).isEqualTo("LoginPattern");
            assertThat(s.status()).isEqualTo("ACTIVE");
            assertThat(s.priority()).isEqualTo(5);
            assertThat(s.tenantId()).isEqualTo("t1");
        }

        @Test
        @DisplayName("should not have more pages when fetched count equals limit")
        void noMorePages() {
            when(client.query(any(), any(), any()))
                    .thenReturn(Promise.of(List.of(entityWith("p1", Map.of("tenantId", "t1")))));

            AepQueryService.PagedResult<AepQueryService.PatternSummary> result =
                    service.queryPatterns("t1", AepQueryService.QuerySpec.builder().limit(20).build())
                           .getResult();

            assertThat(result.hasMore()).isFalse();
            assertThat(result.nextOffset()).isEqualTo(-1);
        }

        @Test
        @DisplayName("should signal hasMore when extra entity is fetched")
        void hasMoreWhenFetchedExtraEntity() {
            // Request limit=1 → service fetches limit+1=2 entities to detect hasMore
            List<Entity> twoEntities = List.of(
                    entityWith("p1", Map.of("tenantId", "t1")),
                    entityWith("p2", Map.of("tenantId", "t1"))
            );

            when(client.query(any(), any(), any()))
                    .thenReturn(Promise.of(twoEntities));

            AepQueryService.PagedResult<AepQueryService.PatternSummary> result =
                    service.queryPatterns("t1", AepQueryService.QuerySpec.builder().limit(1).build())
                           .getResult();

            // Only the first entity should be in the page
            assertThat(result.items()).hasSize(1);
            assertThat(result.hasMore()).isTrue();
            assertThat(result.nextOffset()).isEqualTo(1);
        }

        @Test
        @DisplayName("should tolerate missing optional fields in entity data")
        void toleratesMissingFields() {
            Entity e = entityWith("p1", Map.of("tenantId", "t1")); // only required field

            when(client.query(any(), any(), any()))
                    .thenReturn(Promise.of(List.of(e)));

            AepQueryService.PagedResult<AepQueryService.PatternSummary> result =
                    service.queryPatterns("t1", AepQueryService.QuerySpec.all())
                           .getResult();

            AepQueryService.PatternSummary s = result.items().get(0);
            assertThat(s.name()).isNull();
            assertThat(s.priority()).isEqualTo(0);
        }
    }

    // ─────────────────────────────────────────────────────────
    //  Pipeline Queries
    // ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Pipeline Queries")
    class PipelineQueries {

        @Test
        @DisplayName("should map entity fields to PipelineSummary")
        void mapsPipelineSummary() {
            Instant now = Instant.now();
            Entity e = entityWith("pipe1", Map.of(
                    "tenantId", "t1",
                    "name", "DataPipeline",
                    "status", "RUNNING",
                    "stageCount", 3,
                    "createdAt", now.toString(),
                    "updatedAt", now.toString()
            ));

            when(client.query(eq("t1"), eq(AepQueryService.COLLECTION_PIPELINES), any()))
                    .thenReturn(Promise.of(List.of(e)));

            AepQueryService.PagedResult<AepQueryService.PipelineSummary> result =
                    service.queryPipelines("t1", AepQueryService.QuerySpec.all())
                           .getResult();

            assertThat(result.items()).hasSize(1);
            AepQueryService.PipelineSummary s = result.items().get(0);
            assertThat(s.id()).isEqualTo("pipe1");
            assertThat(s.stageCount()).isEqualTo(3);
            assertThat(s.status()).isEqualTo("RUNNING");
        }
    }

    // ─────────────────────────────────────────────────────────
    //  Anomaly Queries
    // ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Anomaly Queries")
    class AnomalyQueries {

        @Test
        @DisplayName("should map entity fields to AnomalySummary")
        void mapsAnomalySummary() {
            Instant detected = Instant.parse("2026-03-01T00:00:00Z");
            Entity e = entityWith("a1", Map.of(
                    "tenantId", "t1",
                    "kpiName", "LoginRate",
                    "severity", "HIGH",
                    "zScore", 3.7,
                    "detectedAt", detected.toString(),
                    "status", "OPEN"
            ));

            when(client.query(eq("t1"), eq(AepQueryService.COLLECTION_ANOMALIES), any()))
                    .thenReturn(Promise.of(List.of(e)));

            AepQueryService.PagedResult<AepQueryService.AnomalySummary> result =
                    service.queryAnomalies("t1", AepQueryService.QuerySpec.all())
                           .getResult();

            AepQueryService.AnomalySummary s = result.items().get(0);
            assertThat(s.severity()).isEqualTo("HIGH");
            assertThat(s.zScore()).isEqualTo(3.7);
            assertThat(s.detectedAt()).isEqualTo(detected);
        }
    }

    // ─────────────────────────────────────────────────────────
    //  KPI Queries
    // ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("KPI Queries")
    class KpiQueries {

        @Test
        @DisplayName("should map entity fields to KpiSummary")
        void mapsKpiSummary() {
            Instant recorded = Instant.parse("2026-03-02T10:00:00Z");
            Entity e = entityWith("k1", Map.of(
                    "tenantId", "t1",
                    "kpiName", "Revenue",
                    "value", 1500.5,
                    "unit", "USD",
                    "recordedAt", recorded.toString()
            ));

            when(client.query(eq("t1"), eq(AepQueryService.COLLECTION_KPI), any()))
                    .thenReturn(Promise.of(List.of(e)));

            AepQueryService.PagedResult<AepQueryService.KpiSummary> result =
                    service.queryKpis("t1", AepQueryService.QuerySpec.all())
                           .getResult();

            AepQueryService.KpiSummary s = result.items().get(0);
            assertThat(s.kpiName()).isEqualTo("Revenue");
            assertThat(s.value()).isEqualTo(1500.5);
            assertThat(s.unit()).isEqualTo("USD");
            assertThat(s.recordedAt()).isEqualTo(recorded);
        }
    }

    // ─────────────────────────────────────────────────────────
    //  Aggregation
    // ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Aggregation")
    class Aggregation {

        @Test
        @DisplayName("should group-by a field and count entries")
        void groupByField() {
            List<Entity> entities = List.of(
                    entityWith("a1", Map.of("tenantId", "t1", "severity", "HIGH")),
                    entityWith("a2", Map.of("tenantId", "t1", "severity", "HIGH")),
                    entityWith("a3", Map.of("tenantId", "t1", "severity", "LOW"))
            );

            when(client.query(eq("t1"), eq("aep_anomalies"), any()))
                    .thenReturn(Promise.of(entities));

            AepQueryService.AggregateResult result =
                    service.aggregate("t1", "aep_anomalies",
                                    AepQueryService.AggregateSpec.groupBy("severity", 100))
                           .getResult();

            assertThat(result.totalCount()).isEqualTo(3);
            @SuppressWarnings("unchecked")
            Map<String, Long> groups = (Map<String, Long>) result.aggregates().get("groupBy");
            assertThat(groups).containsEntry("HIGH", 2L).containsEntry("LOW", 1L);
        }

        @Test
        @DisplayName("should compute sum and average for a numeric field")
        void sumAndAverage() {
            List<Entity> entities = List.of(
                    entityWith("k1", Map.of("tenantId", "t1", "value", 10.0)),
                    entityWith("k2", Map.of("tenantId", "t1", "value", 20.0)),
                    entityWith("k3", Map.of("tenantId", "t1", "value", 30.0))
            );

            when(client.query(eq("t1"), eq("aep_kpi_snapshots"), any()))
                    .thenReturn(Promise.of(entities));

            AepQueryService.AggregateResult result =
                    service.aggregate("t1", "aep_kpi_snapshots",
                                    AepQueryService.AggregateSpec.sum("value"))
                           .getResult();

            assertThat((Double) result.aggregates().get("sum")).isEqualTo(60.0);
            assertThat((Double) result.aggregates().get("avg")).isEqualTo(20.0);
        }

        @Test
        @DisplayName("should collect distinct field values")
        void distinctValues() {
            List<Entity> entities = List.of(
                    entityWith("p1", Map.of("tenantId", "t1", "status", "ACTIVE")),
                    entityWith("p2", Map.of("tenantId", "t1", "status", "ACTIVE")),
                    entityWith("p3", Map.of("tenantId", "t1", "status", "INACTIVE"))
            );

            when(client.query(eq("t1"), eq("aep_patterns"), any()))
                    .thenReturn(Promise.of(entities));

            AepQueryService.AggregateResult result =
                    service.aggregate("t1", "aep_patterns",
                                    AepQueryService.AggregateSpec.distinct("status"))
                           .getResult();

            assertThat((Integer) result.aggregates().get("distinctCount")).isEqualTo(2);
        }

        @Test
        @DisplayName("should return zero aggregate when collection is empty")
        void emptyCollection() {
            when(client.query(any(), any(), any()))
                    .thenReturn(Promise.of(List.of()));

            AepQueryService.AggregateResult result =
                    service.aggregate("t1", "aep_patterns",
                                    AepQueryService.AggregateSpec.sum("value"))
                           .getResult();

            assertThat(result.totalCount()).isEqualTo(0);
            assertThat((Double) result.aggregates().get("sum")).isEqualTo(0.0);
        }
    }

    // ─────────────────────────────────────────────────────────
    //  Tenant Summary
    // ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Tenant Summary")
    class TenantSummaryTests {

        @Test
        @DisplayName("should aggregate counts from all four AEP collections")
        void aggregatesAllCollections() {
            when(client.query(eq("t1"), eq(AepQueryService.COLLECTION_PATTERNS), any()))
                    .thenReturn(Promise.of(List.of(entityWith("p1", Map.of("tenantId", "t1")))));
            when(client.query(eq("t1"), eq(AepQueryService.COLLECTION_PIPELINES), any()))
                    .thenReturn(Promise.of(List.of(
                            entityWith("pipe1", Map.of("tenantId", "t1")),
                            entityWith("pipe2", Map.of("tenantId", "t1")))));
            when(client.query(eq("t1"), eq(AepQueryService.COLLECTION_ANOMALIES), any()))
                    .thenReturn(Promise.of(List.of()));
            when(client.query(eq("t1"), eq(AepQueryService.COLLECTION_KPI), any()))
                    .thenReturn(Promise.of(List.of(entityWith("k1", Map.of("tenantId", "t1")))));

            AepQueryService.TenantSummary summary =
                    service.tenantSummary("t1").getResult();

            assertThat(summary.tenantId()).isEqualTo("t1");
            assertThat(summary.patternCount()).isEqualTo(1);
            assertThat(summary.pipelineCount()).isEqualTo(2);
            assertThat(summary.anomalyCount()).isEqualTo(0);
            assertThat(summary.kpiCount()).isEqualTo(1);
            assertThat(summary.computedAt()).isNotNull();
        }
    }

    // ─────────────────────────────────────────────────────────
    //  QuerySpec Builder
    // ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("QuerySpec Builder")
    class QuerySpecBuilderTests {

        @Test
        @DisplayName("should build with defaults when no values provided")
        void defaultSpec() {
            AepQueryService.QuerySpec spec = AepQueryService.QuerySpec.builder().build();
            assertThat(spec.offset()).isEqualTo(0);
            assertThat(spec.limit()).isEqualTo(50); // DEFAULT_PAGE_SIZE
            assertThat(spec.filters()).isEmpty();
            assertThat(spec.sorts()).isEmpty();
        }

        @Test
        @DisplayName("should compute offset from page index and size")
        void pageHelperMethod() {
            AepQueryService.QuerySpec spec = AepQueryService.QuerySpec.builder()
                    .page(3, 20)
                    .build();
            assertThat(spec.offset()).isEqualTo(60);
            assertThat(spec.limit()).isEqualTo(20);
        }

        @Test
        @DisplayName("should store multiple filters and sorts")
        void multipleFiltersAndSorts() {
            AepQueryService.QuerySpec spec = AepQueryService.QuerySpec.builder()
                    .filter(Filter.eq("status", "ACTIVE"))
                    .filter(Filter.gt("priority", "3"))
                    .sort(Sort.desc("createdAt"))
                    .sort(Sort.asc("name"))
                    .build();

            assertThat(spec.filters()).hasSize(2);
            assertThat(spec.sorts()).hasSize(2);
        }

        @Test
        @DisplayName("QuerySpec.all() should set max limit and zero offset")
        void allSpec() {
            AepQueryService.QuerySpec spec = AepQueryService.QuerySpec.all();
            assertThat(spec.offset()).isEqualTo(0);
            assertThat(spec.limit()).isEqualTo(1000);
        }
    }

    // ─────────────────────────────────────────────────────────
    //  Helper
    // ─────────────────────────────────────────────────────────

    private static Entity entityWith(String id, Map<String, Object> data) {
        return new Entity(id, "test_collection", data, Instant.now(), Instant.now(), 1L);
    }
}
