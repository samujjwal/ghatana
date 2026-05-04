package com.ghatana.datacloud.launcher.http.handlers;

import com.ghatana.datacloud.analytics.AnalyticsQueryEngine;
import com.ghatana.datacloud.analytics.QueryResult;
import com.ghatana.datacloud.launcher.http.DataCloudHttpMetrics;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Verifies that {@link AnalyticsHandler} correctly emits HTTP metrics via
 * {@link DataCloudHttpMetrics} on success and error paths.
 *
 * @doc.type class
 * @doc.purpose Regression coverage for AnalyticsHandler metrics wiring (DC-010)
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AnalyticsHandler metrics wiring")
class AnalyticsHandlerMetricsTest extends EventloopTestBase {

    @Mock
    private MetricsCollector metricsCollector;

    @Mock
    private AnalyticsQueryEngine analyticsEngine;

    @Mock
    private HttpHandlerSupport httpSupport;

    private AnalyticsHandler handler;
    private DataCloudHttpMetrics metrics;

    @BeforeEach
    void setUp() {
        metrics = new DataCloudHttpMetrics(metricsCollector);
        lenient().when(httpSupport.resolveCorrelationId(any())).thenReturn("trace-id");
        handler = new AnalyticsHandler(analyticsEngine, httpSupport)
                .withMetrics(metrics);
    }

    @Test
    @DisplayName("handleAnalyticsQuery emits request and latency metrics on success")
    void handleAnalyticsQueryShouldEmitRequestAndLatencyMetricsOnSuccess() {
        QueryResult queryResult = mock(QueryResult.class);
        when(queryResult.getQueryId()).thenReturn("q-1");
        when(queryResult.getQueryType()).thenReturn("SELECT");
        when(queryResult.getColumnCount()).thenReturn(3);
        when(queryResult.getRows()).thenReturn(List.of());
        when(queryResult.getExecutionTimeMs()).thenReturn(10L);
        when(queryResult.isOptimized()).thenReturn(false);

        when(analyticsEngine.submitQuery(anyString(), anyString(), anyMap()))
                .thenReturn(Promise.of(queryResult));
        when(httpSupport.requireTenantIdOrFail(any())).thenReturn("tenant-dc");
        when(httpSupport.objectMapper()).thenReturn(new com.fasterxml.jackson.databind.ObjectMapper());
        io.activej.http.HttpResponse successResponse = mock(io.activej.http.HttpResponse.class);
        when(successResponse.getCode()).thenReturn(200);
        when(httpSupport.jsonResponse(anyMap())).thenReturn(successResponse);

        io.activej.http.HttpRequest request = mock(io.activej.http.HttpRequest.class);
        when(request.loadBody()).thenReturn(Promise.of(
                io.activej.bytebuf.ByteBuf.wrapForReading(
                        "{\"query\":\"SELECT * FROM events\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8))));

        runPromise(() -> handler.handleAnalyticsQuery(request));

        // Verify request metric emitted
        verify(metricsCollector).incrementCounter(
                eq(DataCloudHttpMetrics.METRIC_REQUESTS),
                eq(DataCloudHttpMetrics.TAG_HANDLER), eq("AnalyticsHandler"),
                eq(DataCloudHttpMetrics.TAG_OPERATION), eq("handleAnalyticsQuery"),
                eq(DataCloudHttpMetrics.TAG_TENANT), eq("tenant-dc"),
                eq(DataCloudHttpMetrics.TAG_STATUS), eq("200")
        );
        // Verify latency metric emitted
        verify(metricsCollector).recordTimer(
                eq(DataCloudHttpMetrics.METRIC_LATENCY),
                anyLong(),
                eq(DataCloudHttpMetrics.TAG_HANDLER), eq("AnalyticsHandler"),
                eq(DataCloudHttpMetrics.TAG_OPERATION), eq("handleAnalyticsQuery")
        );
    }

    @Test
    @DisplayName("handleAnalyticsQuery emits error metric when engine fails")
    void handleAnalyticsQueryShouldEmitErrorMetricWhenEngineFails() {
        when(analyticsEngine.submitQuery(anyString(), anyString(), anyMap()))
                .thenReturn(Promise.ofException(new RuntimeException("engine down")));
        when(httpSupport.requireTenantIdOrFail(any())).thenReturn("tenant-dc");
        when(httpSupport.objectMapper()).thenReturn(new com.fasterxml.jackson.databind.ObjectMapper());
        when(httpSupport.errorResponse(anyInt(), anyString()))
                .thenReturn(mock(io.activej.http.HttpResponse.class));

        io.activej.http.HttpRequest request = mock(io.activej.http.HttpRequest.class);
        when(request.loadBody()).thenReturn(Promise.of(
                io.activej.bytebuf.ByteBuf.wrapForReading(
                        "{\"query\":\"SELECT 1\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8))));

        runPromise(() -> handler.handleAnalyticsQuery(request));

        verify(metricsCollector).incrementCounter(
                eq(DataCloudHttpMetrics.METRIC_ERRORS),
                eq(DataCloudHttpMetrics.TAG_HANDLER), eq("AnalyticsHandler"),
                eq(DataCloudHttpMetrics.TAG_OPERATION), eq("handleAnalyticsQuery"),
                eq(DataCloudHttpMetrics.TAG_ERROR_TYPE), eq("RuntimeException")
        );
    }

    @Test
    @DisplayName("handleAnalyticsQuery rejects missing tenant header")
    void handleAnalyticsQueryRejectsMissingTenantHeader() {
        io.activej.http.HttpResponse badRequest = mock(io.activej.http.HttpResponse.class);
        when(httpSupport.requireTenantIdOrFail(any())).thenReturn(null);
        when(httpSupport.errorResponse(400, "X-Tenant-Id header is required")).thenReturn(badRequest);

        io.activej.http.HttpRequest request = mock(io.activej.http.HttpRequest.class);

        runPromise(() -> handler.handleAnalyticsQuery(request));

        verify(httpSupport).errorResponse(400, "X-Tenant-Id header is required");
        verifyNoInteractions(metricsCollector);
        verifyNoInteractions(analyticsEngine);
    }

    @Test
    @DisplayName("withMetrics(noop) does not emit any metrics")
    void withNoopMetricsShouldNotCallCollector() {
        AnalyticsHandler noopHandler = new AnalyticsHandler(null, httpSupport)
                .withMetrics(DataCloudHttpMetrics.noop());

        when(httpSupport.errorResponse(anyInt(), anyString()))
                .thenReturn(mock(io.activej.http.HttpResponse.class));

        io.activej.http.HttpRequest request = mock(io.activej.http.HttpRequest.class);
        runPromise(() -> noopHandler.handleAnalyticsQuery(request));

        verifyNoInteractions(metricsCollector);
    }
}
