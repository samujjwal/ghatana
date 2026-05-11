package com.ghatana.datacloud.launcher.http.handlers;
import com.ghatana.datacloud.launcher.http.handlers.HttpHandlerSupport;
import com.ghatana.datacloud.launcher.http.handlers.HttpHandlerSupport.TenantResolutionResult;

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
import java.util.Map;

import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
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
        when(httpSupport.requireTenantIdWithError(any())).thenReturn(TenantResolutionResult.success("tenant-dc", null));
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
        when(httpSupport.requireTenantIdWithError(any())).thenReturn(TenantResolutionResult.success("tenant-dc", null));
        when(httpSupport.objectMapper()).thenReturn(new com.fasterxml.jackson.databind.ObjectMapper());
        io.activej.http.HttpResponse errorResponse = mock(io.activej.http.HttpResponse.class);
        when(httpSupport.errorResponse(anyInt(), anyString(), any()))
                .thenReturn(errorResponse);
        lenient().when(httpSupport.errorResponse(anyInt(), anyString()))
                .thenReturn(errorResponse);

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
        when(httpSupport.requireTenantIdWithError(any())).thenReturn(TenantResolutionResult.error(400, "X-Tenant-Id header is required"));
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

    // ── DC-P2-001: Sensitive-data logging safety ──────────────────────────────

    /**
     * DC-P2-001: Error responses must NEVER contain raw query text.
     *
     * <p>The engine is allowed to surface exception messages with query text
     * in server-side logs, but the HTTP response returned to the caller must
     * be a stable, sanitised message that contains no user-supplied query
     * content.  This guards against accidental PII/SQL exposure.
     */
    @Test
    @DisplayName("DC-P2-001: error response does not leak raw query text to caller")
    void errorResponseMustNotContainRawQueryText() {
        String sensitiveQuery = "SELECT password, ssn FROM users WHERE id = 42";

        // Engine fails with a message that echoes the query text — must not reach the client
        when(analyticsEngine.submitQuery(anyString(), anyString(), anyMap()))
                .thenReturn(Promise.ofException(
                        new RuntimeException("syntax error near: " + sensitiveQuery)));
        when(httpSupport.requireTenantIdWithError(any())).thenReturn(TenantResolutionResult.success("tenant-dc", null));
        when(httpSupport.objectMapper()).thenReturn(new com.fasterxml.jackson.databind.ObjectMapper());

        io.activej.http.HttpResponse errorResponse = mock(io.activej.http.HttpResponse.class);
        when(httpSupport.errorResponse(anyInt(), anyString(), any())).thenReturn(errorResponse);
        lenient().when(httpSupport.errorResponse(anyInt(), anyString())).thenReturn(errorResponse);

        io.activej.http.HttpRequest request = mock(io.activej.http.HttpRequest.class);
        when(request.loadBody()).thenReturn(Promise.of(
                io.activej.bytebuf.ByteBuf.wrapForReading(
                        ("{\"query\":\"" + sensitiveQuery + "\"}").getBytes(
                                java.nio.charset.StandardCharsets.UTF_8))));

        runPromise(() -> handler.handleAnalyticsQuery(request));

        // Capture the message passed to errorResponse
        @SuppressWarnings("unchecked")
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(httpSupport).errorResponse(eq(500), messageCaptor.capture(), any());
        String clientMessage = messageCaptor.getValue();

        assertThat(clientMessage)
                .as("Error response must not expose raw query text to the caller")
                .doesNotContain(sensitiveQuery)
                .doesNotContain("password")
                .doesNotContain("ssn")
                .isEqualTo("Query execution failed");
    }

    /**
     * DC-P2-001: Truncated result sets must be flagged in the response and must
     * still emit a successful (200) request metric — truncation is not an error.
     */
    @Test
    @DisplayName("DC-P2-001: truncated result emits 200 metric and sets truncated=true in response")
    void truncatedResultEmitsSuccessMetricAndFlagsTruncationInResponse() {
        QueryResult queryResult = mock(QueryResult.class);
        when(queryResult.getQueryId()).thenReturn("q-trunc");
        when(queryResult.getQueryType()).thenReturn("SELECT");
        when(queryResult.getColumnCount()).thenReturn(2);
        // 2 rows returned, but 15000 total — result is truncated
        when(queryResult.getRows()).thenReturn(
                List.of(Map.of("id", "1"), Map.of("id", "2")));
        when(queryResult.getTotalRows()).thenReturn(15000);
        when(queryResult.getExecutionTimeMs()).thenReturn(120L);
        when(queryResult.isOptimized()).thenReturn(false);

        when(analyticsEngine.submitQuery(anyString(), anyString(), anyMap()))
                .thenReturn(Promise.of(queryResult));
        when(httpSupport.requireTenantIdWithError(any())).thenReturn(TenantResolutionResult.success("tenant-dc", null));
        when(httpSupport.objectMapper()).thenReturn(new com.fasterxml.jackson.databind.ObjectMapper());

        io.activej.http.HttpResponse successResponse = mock(io.activej.http.HttpResponse.class);
        when(successResponse.getCode()).thenReturn(200);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> bodyCaptor = ArgumentCaptor.forClass(Map.class);
        when(httpSupport.jsonResponse(bodyCaptor.capture())).thenReturn(successResponse);

        io.activej.http.HttpRequest request = mock(io.activej.http.HttpRequest.class);
        when(request.loadBody()).thenReturn(Promise.of(
                io.activej.bytebuf.ByteBuf.wrapForReading(
                        "{\"query\":\"SELECT * FROM big_table\"}".getBytes(
                                java.nio.charset.StandardCharsets.UTF_8))));

        runPromise(() -> handler.handleAnalyticsQuery(request));

        // Verify truncation flag is set in the response body
        Map<String, Object> body = bodyCaptor.getValue();
        assertThat(body)
                .as("Response body must flag truncation when result is cut off")
                .containsEntry("truncated", true)
                .containsEntry("rowCount", 2);

        // Verify request metric is still 200 — truncation is not an error
        verify(metricsCollector).incrementCounter(
                eq(DataCloudHttpMetrics.METRIC_REQUESTS),
                eq(DataCloudHttpMetrics.TAG_HANDLER), eq("AnalyticsHandler"),
                eq(DataCloudHttpMetrics.TAG_OPERATION), eq("handleAnalyticsQuery"),
                eq(DataCloudHttpMetrics.TAG_TENANT), eq("tenant-dc"),
                eq(DataCloudHttpMetrics.TAG_STATUS), eq("200")
        );
        // No error metric emitted for truncated (non-error) results
        verify(metricsCollector, never()).incrementCounter(
                eq(DataCloudHttpMetrics.METRIC_ERRORS), any(String[].class));
    }

    /**
     * DC-P2-001: Row count from engine result must flow into response body
     * so consumers can determine result completeness without inspecting rows.
     */
    @Test
    @DisplayName("DC-P2-001: response body reflects correct rowCount from result set")
    void responseBodyReflectsResultRowCount() {
        QueryResult queryResult = mock(QueryResult.class);
        when(queryResult.getQueryId()).thenReturn("q-sized");
        when(queryResult.getQueryType()).thenReturn("SELECT");
        when(queryResult.getColumnCount()).thenReturn(3);
        when(queryResult.getRows()).thenReturn(
                List.of(Map.of("a", 1), Map.of("a", 2), Map.of("a", 3)));
        when(queryResult.getTotalRows()).thenReturn(3); // no truncation
        when(queryResult.getExecutionTimeMs()).thenReturn(8L);
        when(queryResult.isOptimized()).thenReturn(true);

        when(analyticsEngine.submitQuery(anyString(), anyString(), anyMap()))
                .thenReturn(Promise.of(queryResult));
        when(httpSupport.requireTenantIdWithError(any())).thenReturn(TenantResolutionResult.success("tenant-dc", null));
        when(httpSupport.objectMapper()).thenReturn(new com.fasterxml.jackson.databind.ObjectMapper());

        io.activej.http.HttpResponse successResponse = mock(io.activej.http.HttpResponse.class);
        when(successResponse.getCode()).thenReturn(200);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> bodyCaptor = ArgumentCaptor.forClass(Map.class);
        when(httpSupport.jsonResponse(bodyCaptor.capture())).thenReturn(successResponse);

        io.activej.http.HttpRequest request = mock(io.activej.http.HttpRequest.class);
        when(request.loadBody()).thenReturn(Promise.of(
                io.activej.bytebuf.ByteBuf.wrapForReading(
                        "{\"query\":\"SELECT a FROM t\"}".getBytes(
                                java.nio.charset.StandardCharsets.UTF_8))));

        runPromise(() -> handler.handleAnalyticsQuery(request));

        Map<String, Object> body = bodyCaptor.getValue();
        assertThat(body)
                .containsEntry("rowCount", 3)
                .containsEntry("columnCount", 3)
                .containsEntry("truncated", false)
                .containsEntry("optimized", true);
    }
}
