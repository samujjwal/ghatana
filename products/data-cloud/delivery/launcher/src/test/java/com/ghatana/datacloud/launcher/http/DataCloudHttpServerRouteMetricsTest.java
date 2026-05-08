/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.http;

import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.analytics.AnalyticsQueryEngine;
import com.ghatana.datacloud.analytics.QueryResult;
import com.ghatana.datacloud.spi.EntityStore;
import com.ghatana.platform.audit.AuditService;
import com.ghatana.platform.observability.MetricsCollectorFactory;
import io.activej.promise.Promise;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * DC-OPS-002 — Route-level HTTP metrics coverage.
 *
 * <p>Verifies that {@code dc.http.requests}, {@code dc.http.request.latency}, and
 * {@code dc.http.errors} are emitted for critical routes and appear in the Prometheus
 * {@code /metrics} scrape endpoint. These metric labels ({@code handler}, {@code operation},
 * {@code tenant}, {@code status}) are sufficient to build operational dashboards.
 *
 * @doc.type class
 * @doc.purpose Verifies dc.http.* route-level metrics are emitted for critical routes
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DC-OPS-002 — Route-level HTTP metrics coverage")
class DataCloudHttpServerRouteMetricsTest extends DataCloudHttpServerTestBase {

    private DataCloudClient mockClient;
    private AnalyticsQueryEngine mockAnalyticsEngine;

    @BeforeEach
    void setUp() throws Exception {
        mockClient = mock(DataCloudClient.class);
        EntityStore mockEntityStore = mock(EntityStore.class);
        when(mockClient.entityStore()).thenReturn(mockEntityStore);
        mockAnalyticsEngine = mock(AnalyticsQueryEngine.class);
        port = findFreePort();
    }

    @Override
    protected void startServer() throws Exception {
        AuditService mockAuditService = mock(AuditService.class);
        when(mockAuditService.record(any())).thenReturn(Promise.of(null));
        server = new DataCloudHttpServer(mockClient, port, null, null, mockAnalyticsEngine)
                .withMetricsCollector(MetricsCollectorFactory.create(
                        new PrometheusMeterRegistry(PrometheusConfig.DEFAULT)))
                .withAuditService(mockAuditService);
        server.start();
        waitForServerReady(TestConstants.TIMEOUT_SERVER_START_MS);
    }

    @Test
    @DisplayName("DC-OPS-002a — analytics query route emits dc_http_requests and dc_http_request_latency")
    void analyticsQueryRouteEmitsRequestAndLatencyMetrics() throws Exception {
        QueryResult queryResult = QueryResult.builder()
                .queryId("qry-ops-1")
                .queryType("SELECT")
                .rows(List.of(Map.of("col", "val")))
                .rowCount(1)
                .columnCount(1)
                .totalRows(1)
                .executionTimeMs(12L)
                .optimized(true)
                .build();
        when(mockAnalyticsEngine.submitQuery(eq("tenant-ops"), any(), any()))
                .thenReturn(Promise.of(queryResult));

        startServer();

        HttpResponse<String> resp = postJson(
                "/api/v1/analytics/query",
                Map.of("query", "SELECT * FROM products", "tenantId", "tenant-ops"),
                Map.of("X-Tenant-ID", "tenant-ops"));
        assertThat(resp.statusCode()).isEqualTo(200);

        HttpResponse<String> metricsResp = get("/metrics");
        assertThat(metricsResp.statusCode()).isEqualTo(200);

        String body = metricsResp.body();
        assertThat(body)
                .as("dc_http_requests counter must appear after hitting analytics query route. Full body:\n%s", body)
                .contains(DataCloudHttpMetrics.METRIC_REQUESTS.replace(".", "_"));
        assertThat(body)
                .as("dc_http_request_latency timer must appear after hitting analytics query route. Full body:\n%s", body)
                .contains(DataCloudHttpMetrics.METRIC_LATENCY.replace(".", "_"));
        assertThat(body)
                .as("handler label AnalyticsHandler must be present for dashboard filtering. Full body:\n%s", body)
                .contains("AnalyticsHandler");
        assertThat(body)
                .as("tenant label must be present for per-tenant dashboards. Full body:\n%s", body)
                .contains("tenant-ops");
    }

    @Test
    @DisplayName("DC-OPS-002b — analytics query error path emits dc_http_errors metric")
    void analyticsQueryErrorPathEmitsErrorMetric() throws Exception {
        when(mockAnalyticsEngine.submitQuery(eq("tenant-err"), any(), any()))
                .thenReturn(Promise.ofException(new RuntimeException("simulated analytics failure")));

        startServer();

        HttpResponse<String> resp = postJson(
                "/api/v1/analytics/query",
                Map.of("query", "SELECT * FROM broken"),
                Map.of("X-Tenant-ID", "tenant-err"));
        assertThat(resp.statusCode()).isEqualTo(500);

        HttpResponse<String> metricsResp = get("/metrics");
        assertThat(metricsResp.statusCode()).isEqualTo(200);

        String body = metricsResp.body();
        assertThat(body)
                .as("dc_http_errors counter must appear after analytics route error. Full body:\n%s", body)
                .contains(DataCloudHttpMetrics.METRIC_ERRORS.replace(".", "_"));
        assertThat(body)
                .as("AnalyticsHandler label must be present on error metric. Full body:\n%s", body)
                .contains("AnalyticsHandler");
    }

    @Test
    @DisplayName("DC-OPS-002c — metrics labels include handler and operation for all critical dimensions")
    void metricsLabelsIncludeHandlerAndOperationForDashboards() throws Exception {
        QueryResult queryResult = QueryResult.builder()
                .queryId("qry-ops-2")
                .queryType("AGGREGATE")
                .rows(List.of())
                .rowCount(0)
                .columnCount(2)
                .totalRows(0)
                .executionTimeMs(5L)
                .optimized(false)
                .build();
        when(mockAnalyticsEngine.submitQuery(eq("tenant-dash"), any(), any()))
                .thenReturn(Promise.of(queryResult));

        startServer();

        postJson(
                "/api/v1/analytics/query",
                Map.of("query", "SELECT region, COUNT(*) FROM sales GROUP BY region"),
                Map.of("X-Tenant-ID", "tenant-dash"));

        HttpResponse<String> metricsResp = get("/metrics");
        String body = metricsResp.body();

        // These label key names must appear so dashboards can slice by handler, operation, tenant, status
        assertThat(body)
                .as("handler label key required for dashboard grouping. Full body:\n%s", body)
                .contains("handler=");
        assertThat(body)
                .as("operation label key required for per-endpoint dashboards. Full body:\n%s", body)
                .contains("operation=");
        assertThat(body)
                .as("tenant label key required for per-tenant dashboards. Full body:\n%s", body)
                .contains("tenant=");
        assertThat(body)
                .as("status label key required for error-rate dashboards. Full body:\n%s", body)
                .contains("status=");
    }
}
