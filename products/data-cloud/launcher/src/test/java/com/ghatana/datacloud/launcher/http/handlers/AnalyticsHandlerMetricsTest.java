package com.ghatana.datacloud.launcher.http.handlers;

import com.ghatana.datacloud.analytics.AnalyticsQueryEngine;
import com.ghatana.datacloud.analytics.QueryResult;
import com.ghatana.datacloud.launcher.http.DataCloudHttpMetrics;
import com.ghatana.platform.observability.MetricsCollector;
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
 * @doc.purpose Regression coverage for AnalyticsHandler metrics wiring (DC-010) // GH-90000
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("AnalyticsHandler metrics wiring [GH-90000]")
class AnalyticsHandlerMetricsTest {

    @Mock
    private MetricsCollector metricsCollector;

    @Mock
    private AnalyticsQueryEngine analyticsEngine;

    @Mock
    private HttpHandlerSupport httpSupport;

    private AnalyticsHandler handler;
    private DataCloudHttpMetrics metrics;

    @BeforeEach
    void setUp() { // GH-90000
        metrics = new DataCloudHttpMetrics(metricsCollector); // GH-90000
        handler = new AnalyticsHandler(analyticsEngine, httpSupport) // GH-90000
                .withMetrics(metrics); // GH-90000
    }

    @Test
    @DisplayName("handleAnalyticsQuery emits request and latency metrics on success [GH-90000]")
    void handleAnalyticsQueryShouldEmitRequestAndLatencyMetricsOnSuccess() { // GH-90000
        QueryResult queryResult = mock(QueryResult.class); // GH-90000
        when(queryResult.getQueryId()).thenReturn("q-1 [GH-90000]");
        when(queryResult.getQueryType()).thenReturn("SELECT [GH-90000]");
        when(queryResult.getRowCount()).thenReturn(5); // GH-90000
        when(queryResult.getColumnCount()).thenReturn(3); // GH-90000
        when(queryResult.getRows()).thenReturn(List.of()); // GH-90000
        when(queryResult.getExecutionTimeMs()).thenReturn(10L); // GH-90000
        when(queryResult.isOptimized()).thenReturn(false); // GH-90000

        when(analyticsEngine.submitQuery(anyString(), anyString(), anyMap())) // GH-90000
                .thenReturn(Promise.of(queryResult)); // GH-90000
        when(httpSupport.requireTenantIdOrFail(any())).thenReturn("tenant-dc [GH-90000]");
        when(httpSupport.objectMapper()).thenReturn(new com.fasterxml.jackson.databind.ObjectMapper()); // GH-90000
        io.activej.http.HttpResponse successResponse = mock(io.activej.http.HttpResponse.class); // GH-90000
        when(successResponse.getCode()).thenReturn(200); // GH-90000
        when(httpSupport.jsonResponse(anyMap())).thenReturn(successResponse); // GH-90000

        // Simulate request with valid JSON body using mocked HttpRequest
        io.activej.http.HttpRequest request = mock(io.activej.http.HttpRequest.class); // GH-90000
        when(request.loadBody()).thenReturn(Promise.of( // GH-90000
                io.activej.bytebuf.ByteBuf.wrapForReading( // GH-90000
                        "{\"query\":\"SELECT * FROM events\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8)))); // GH-90000

        handler.handleAnalyticsQuery(request).getResult(); // GH-90000

        // Verify request metric emitted
        verify(metricsCollector).incrementCounter( // GH-90000
                eq(DataCloudHttpMetrics.METRIC_REQUESTS), // GH-90000
                eq(DataCloudHttpMetrics.TAG_HANDLER), eq("AnalyticsHandler [GH-90000]"),
                eq(DataCloudHttpMetrics.TAG_OPERATION), eq("handleAnalyticsQuery [GH-90000]"),
                eq(DataCloudHttpMetrics.TAG_TENANT), eq("tenant-dc [GH-90000]"),
                eq(DataCloudHttpMetrics.TAG_STATUS), eq("200 [GH-90000]")
        );
        // Verify latency metric emitted
        verify(metricsCollector).recordTimer( // GH-90000
                eq(DataCloudHttpMetrics.METRIC_LATENCY), // GH-90000
                anyLong(), // GH-90000
                eq(DataCloudHttpMetrics.TAG_HANDLER), eq("AnalyticsHandler [GH-90000]"),
                eq(DataCloudHttpMetrics.TAG_OPERATION), eq("handleAnalyticsQuery [GH-90000]")
        );
    }

    @Test
    @DisplayName("handleAnalyticsQuery emits error metric when engine fails [GH-90000]")
    void handleAnalyticsQueryShouldEmitErrorMetricWhenEngineFails() { // GH-90000
        when(analyticsEngine.submitQuery(anyString(), anyString(), anyMap())) // GH-90000
                .thenReturn(Promise.ofException(new RuntimeException("engine down [GH-90000]")));
        when(httpSupport.requireTenantIdOrFail(any())).thenReturn("tenant-dc [GH-90000]");
        when(httpSupport.objectMapper()).thenReturn(new com.fasterxml.jackson.databind.ObjectMapper()); // GH-90000
        when(httpSupport.errorResponse(anyInt(), anyString())) // GH-90000
                .thenReturn(mock(io.activej.http.HttpResponse.class)); // GH-90000

        io.activej.http.HttpRequest request = mock(io.activej.http.HttpRequest.class); // GH-90000
        when(request.loadBody()).thenReturn(Promise.of( // GH-90000
                io.activej.bytebuf.ByteBuf.wrapForReading( // GH-90000
                        "{\"query\":\"SELECT 1\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8)))); // GH-90000

        handler.handleAnalyticsQuery(request).getResult(); // GH-90000

        verify(metricsCollector).incrementCounter( // GH-90000
                eq(DataCloudHttpMetrics.METRIC_ERRORS), // GH-90000
                eq(DataCloudHttpMetrics.TAG_HANDLER), eq("AnalyticsHandler [GH-90000]"),
                eq(DataCloudHttpMetrics.TAG_OPERATION), eq("handleAnalyticsQuery [GH-90000]"),
                eq(DataCloudHttpMetrics.TAG_ERROR_TYPE), eq("RuntimeException [GH-90000]")
        );
    }

        @Test
        @DisplayName("handleAnalyticsQuery rejects missing tenant header [GH-90000]")
        void handleAnalyticsQueryRejectsMissingTenantHeader() { // GH-90000
                io.activej.http.HttpResponse badRequest = mock(io.activej.http.HttpResponse.class); // GH-90000
                when(httpSupport.requireTenantIdOrFail(any())).thenReturn(null); // GH-90000
                when(httpSupport.errorResponse(400, "X-Tenant-Id header is required")).thenReturn(badRequest); // GH-90000

                io.activej.http.HttpRequest request = mock(io.activej.http.HttpRequest.class); // GH-90000

                handler.handleAnalyticsQuery(request).getResult(); // GH-90000

                verify(httpSupport).errorResponse(400, "X-Tenant-Id header is required"); // GH-90000
                verifyNoInteractions(metricsCollector); // GH-90000
                verifyNoInteractions(analyticsEngine); // GH-90000
        }

    @Test
    @DisplayName("withMetrics(noop) does not emit any metrics [GH-90000]")
    void withNoopMetricsShouldNotCallCollector() { // GH-90000
        AnalyticsHandler noopHandler = new AnalyticsHandler(null, httpSupport) // GH-90000
                .withMetrics(DataCloudHttpMetrics.noop()); // GH-90000

        when(httpSupport.errorResponse(anyInt(), anyString())) // GH-90000
                .thenReturn(mock(io.activej.http.HttpResponse.class)); // GH-90000

        io.activej.http.HttpRequest request = mock(io.activej.http.HttpRequest.class); // GH-90000
        noopHandler.handleAnalyticsQuery(request).getResult(); // GH-90000

        verifyNoInteractions(metricsCollector); // GH-90000
    }
}
