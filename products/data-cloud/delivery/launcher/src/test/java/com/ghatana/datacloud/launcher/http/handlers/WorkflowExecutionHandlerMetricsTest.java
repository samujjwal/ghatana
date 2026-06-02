/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.http.handlers;
import com.ghatana.datacloud.launcher.http.handlers.HttpHandlerSupport;
import com.ghatana.datacloud.launcher.http.handlers.HttpHandlerSupport.TenantResolutionResult;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.launcher.http.DataCloudHttpMetrics;
import com.ghatana.datacloud.launcher.http.plugins.WorkflowExecutionCapability;
import com.ghatana.datacloud.launcher.http.plugins.WorkflowExecutionCapability.ExecutionSnapshot;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.bytebuf.ByteBuf;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Verifies that {@link WorkflowExecutionHandler} correctly emits HTTP metrics
 * via {@link DataCloudHttpMetrics} on success, error, and noop paths.
 *
 * <p>Covers DC-P2-004: started / completed / cancelled / retried / rolled-back /
 * checkpoint metrics and correlation-ID logging wiring.
 *
 * @doc.type class
 * @doc.purpose Regression coverage for WorkflowExecutionHandler metrics wiring (DC-P2-004)
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WorkflowExecutionHandler — metrics and observability (DC-P2-004)")
class WorkflowExecutionHandlerMetricsTest extends EventloopTestBase {

    private static final String TENANT = "tenant-wf";
    private static final String PIPELINE_ID = "pipeline-abc";
    private static final String EXEC_ID = "exec-xyz";
    private static final String CORRELATION_ID = "corr-001";
        private static final String HANDLER_NAME = "WorkflowExecutionHandler";

    @Mock private MetricsCollector metricsCollector;
    @Mock private WorkflowExecutionCapability executionCapability;
    @Mock private HttpHandlerSupport http;
    @Mock private DataCloudClient client;

    private WorkflowExecutionHandler handler;
    private DataCloudHttpMetrics metrics;

    /** A minimal valid snapshot returned by the mock capability. */
    private static ExecutionSnapshot snapshot(String id, String status) {
        return new ExecutionSnapshot(id, TENANT, PIPELINE_ID, "My Pipeline",
                                status, 0, "2026-01-01T00:00:00Z", null, null, List.of(), null, null,
                                null, null, null, null, null, null, null, null);
    }

    @BeforeEach
    void setUp() {
        metrics = new DataCloudHttpMetrics(metricsCollector);
        handler = new WorkflowExecutionHandler(client, http)
                .withExecutionCapability(executionCapability)
                .withMetrics(metrics);

        // Common stubs used by most tests
        lenient().when(http.resolveCorrelationId(any())).thenReturn(CORRELATION_ID);
        lenient().when(http.requireTenantIdWithError(any())).thenReturn(TenantResolutionResult.success(TENANT, null));
        lenient().when(http.objectMapper()).thenReturn(new ObjectMapper());
        lenient().when(http.errorResponse(anyInt(), anyString()))
                .thenReturn(mock(HttpResponse.class));
        lenient().when(http.jsonResponse(any())).thenReturn(mock(HttpResponse.class));
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private HttpRequest mockRequestWithBody(String json) {
        HttpRequest req = mock(HttpRequest.class);
        lenient().when(req.loadBody()).thenReturn(Promise.of(
                ByteBuf.wrapForReading(json.getBytes(StandardCharsets.UTF_8))));
        lenient().when(req.getQueryParameter("tenantId")).thenReturn(null);
        lenient().when(req.getPathParameter("pipelineId")).thenReturn(PIPELINE_ID);
        lenient().when(req.getPathParameter("executionId")).thenReturn(EXEC_ID);
        return req;
    }

    private HttpRequest mockRequestNoBody() {
        HttpRequest req = mock(HttpRequest.class);
        lenient().when(req.getQueryParameter("tenantId")).thenReturn(null);
        lenient().when(req.getPathParameter("pipelineId")).thenReturn(PIPELINE_ID);
        lenient().when(req.getPathParameter("executionId")).thenReturn(EXEC_ID);
        return req;
    }

    // ── Execute pipeline ────────────────────────────────────────────────────

    @Nested
    @DisplayName("Execution lifecycle metrics")
    class ExecutionLifecycleMetrics {

        @Test
        @DisplayName("executePipeline success emits request counter and latency")
        void executePipelineSuccessEmitsRequestAndLatency() {
            when(executionCapability.execute(eq(TENANT), eq(PIPELINE_ID), any()))
                    .thenReturn(Promise.of(snapshot(EXEC_ID, "RUNNING")));

            runPromise(() -> handler.handleExecutePipeline(
                    mockRequestWithBody("{\"param\":\"value\"}")));

            verify(metricsCollector).incrementCounter(
                    eq(DataCloudHttpMetrics.METRIC_REQUESTS),
                    eq(DataCloudHttpMetrics.TAG_HANDLER), eq(HANDLER_NAME),
                    eq(DataCloudHttpMetrics.TAG_OPERATION), eq("executePipeline"),
                    eq(DataCloudHttpMetrics.TAG_TENANT), eq(TENANT),
                    eq(DataCloudHttpMetrics.TAG_STATUS), eq("200"));
            verify(metricsCollector).recordTimer(
                    eq(DataCloudHttpMetrics.METRIC_LATENCY),
                    anyLong(),
                    eq(DataCloudHttpMetrics.TAG_HANDLER), eq(HANDLER_NAME),
                    eq(DataCloudHttpMetrics.TAG_OPERATION), eq("executePipeline"));
        }

        @Test
        @DisplayName("executePipeline failure emits error counter")
        void executePipelineFailureEmitsErrorCounter() {
            when(executionCapability.execute(eq(TENANT), eq(PIPELINE_ID), any()))
                    .thenReturn(Promise.ofException(new RuntimeException("engine failure")));

            runPromise(() -> handler.handleExecutePipeline(
                    mockRequestWithBody("{}")));

            verify(metricsCollector).incrementCounter(
                    eq(DataCloudHttpMetrics.METRIC_ERRORS),
                    eq(DataCloudHttpMetrics.TAG_HANDLER), eq(HANDLER_NAME),
                    eq(DataCloudHttpMetrics.TAG_OPERATION), eq("executePipeline"),
                    eq(DataCloudHttpMetrics.TAG_ERROR_TYPE), eq("RuntimeException"));
        }

        @Test
        @DisplayName("executePipeline with invalid body emits error counter, not request counter")
        void executePipelineInvalidBodyEmitsErrorNotRequest() {
            HttpRequest req = mock(HttpRequest.class);
            lenient().when(req.getQueryParameter("tenantId")).thenReturn(null);
            lenient().when(req.getPathParameter("pipelineId")).thenReturn(PIPELINE_ID);
            // Simulate body that parses as a non-Map JSON value → ObjectMapper will throw
            when(req.loadBody()).thenReturn(Promise.of(
                    ByteBuf.wrapForReading("[1,2,3]".getBytes(StandardCharsets.UTF_8))));

            runPromise(() -> handler.handleExecutePipeline(req));

            verify(metricsCollector).incrementCounter(
                    eq(DataCloudHttpMetrics.METRIC_ERRORS),
                    eq(DataCloudHttpMetrics.TAG_HANDLER), eq(HANDLER_NAME),
                    eq(DataCloudHttpMetrics.TAG_OPERATION), eq("executePipeline"),
                    eq(DataCloudHttpMetrics.TAG_ERROR_TYPE), eq("InvalidRequest"));
            verify(metricsCollector, never()).incrementCounter(
                    eq(DataCloudHttpMetrics.METRIC_REQUESTS),
                    eq(DataCloudHttpMetrics.TAG_HANDLER), eq(HANDLER_NAME),
                    eq(DataCloudHttpMetrics.TAG_OPERATION), eq("executePipeline"),
                    eq(DataCloudHttpMetrics.TAG_TENANT), eq(TENANT),
                    eq(DataCloudHttpMetrics.TAG_STATUS), eq("200"));
        }
    }

    // ── Cancel ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Cancellation metrics")
    class CancellationMetrics {

        @Test
        @DisplayName("cancelExecution success emits request and latency metrics")
        void cancelExecutionSuccessEmitsMetrics() {
            when(executionCapability.cancelExecution(eq(TENANT), eq(EXEC_ID)))
                    .thenReturn(Promise.of(snapshot(EXEC_ID, "CANCELLED")));

            runPromise(() -> handler.handleCancelExecution(mockRequestNoBody()));

            verify(metricsCollector).incrementCounter(
                    eq(DataCloudHttpMetrics.METRIC_REQUESTS),
                    eq(DataCloudHttpMetrics.TAG_HANDLER), eq(HANDLER_NAME),
                    eq(DataCloudHttpMetrics.TAG_OPERATION), eq("cancelExecution"),
                    eq(DataCloudHttpMetrics.TAG_TENANT), eq(TENANT),
                    eq(DataCloudHttpMetrics.TAG_STATUS), eq("200"));
            verify(metricsCollector).recordTimer(
                    eq(DataCloudHttpMetrics.METRIC_LATENCY),
                    anyLong(),
                    eq(DataCloudHttpMetrics.TAG_HANDLER), eq(HANDLER_NAME),
                    eq(DataCloudHttpMetrics.TAG_OPERATION), eq("cancelExecution"));
        }

        @Test
        @DisplayName("cancelPipelineExecution success emits request and latency metrics")
        void cancelPipelineExecutionSuccessEmitsMetrics() {
            when(executionCapability.cancelExecution(eq(TENANT), eq(EXEC_ID)))
                    .thenReturn(Promise.of(snapshot(EXEC_ID, "CANCELLED")));

            runPromise(() -> handler.handleCancelPipelineExecution(mockRequestNoBody()));

            verify(metricsCollector).incrementCounter(
                    eq(DataCloudHttpMetrics.METRIC_REQUESTS),
                    eq(DataCloudHttpMetrics.TAG_HANDLER), eq(HANDLER_NAME),
                    eq(DataCloudHttpMetrics.TAG_OPERATION), eq("cancelPipelineExecution"),
                    eq(DataCloudHttpMetrics.TAG_TENANT), eq(TENANT),
                    eq(DataCloudHttpMetrics.TAG_STATUS), eq("200"));
        }

        @Test
        @DisplayName("cancelExecution failure emits error counter")
        void cancelExecutionFailureEmitsError() {
            when(executionCapability.cancelExecution(eq(TENANT), eq(EXEC_ID)))
                    .thenReturn(Promise.ofException(new IllegalStateException("already terminal")));

            runPromise(() -> handler.handleCancelExecution(mockRequestNoBody()));

            verify(metricsCollector).incrementCounter(
                    eq(DataCloudHttpMetrics.METRIC_ERRORS),
                    eq(DataCloudHttpMetrics.TAG_HANDLER), eq(HANDLER_NAME),
                    eq(DataCloudHttpMetrics.TAG_OPERATION), eq("cancelExecution"),
                    eq(DataCloudHttpMetrics.TAG_ERROR_TYPE), eq("IllegalStateException"));
        }
    }

    // ── Retry ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Retry metrics")
    class RetryMetrics {

        @Test
        @DisplayName("retryExecution success emits request and latency metrics")
        void retryExecutionSuccessEmitsMetrics() {
            when(executionCapability.retryExecution(eq(TENANT), eq(EXEC_ID)))
                    .thenReturn(Promise.of(snapshot(EXEC_ID, "RUNNING")));

            runPromise(() -> handler.handleRetryExecution(mockRequestNoBody()));

            verify(metricsCollector).incrementCounter(
                    eq(DataCloudHttpMetrics.METRIC_REQUESTS),
                    eq(DataCloudHttpMetrics.TAG_HANDLER), eq(HANDLER_NAME),
                    eq(DataCloudHttpMetrics.TAG_OPERATION), eq("retryExecution"),
                    eq(DataCloudHttpMetrics.TAG_TENANT), eq(TENANT),
                    eq(DataCloudHttpMetrics.TAG_STATUS), eq("200"));
            verify(metricsCollector).recordTimer(
                    eq(DataCloudHttpMetrics.METRIC_LATENCY),
                    anyLong(),
                    eq(DataCloudHttpMetrics.TAG_HANDLER), eq(HANDLER_NAME),
                    eq(DataCloudHttpMetrics.TAG_OPERATION), eq("retryExecution"));
        }

        @Test
        @DisplayName("retryExecution failure emits error counter")
        void retryExecutionFailureEmitsError() {
            when(executionCapability.retryExecution(eq(TENANT), eq(EXEC_ID)))
                    .thenReturn(Promise.ofException(new RuntimeException("retry limit")));

            runPromise(() -> handler.handleRetryExecution(mockRequestNoBody()));

            verify(metricsCollector).incrementCounter(
                    eq(DataCloudHttpMetrics.METRIC_ERRORS),
                    eq(DataCloudHttpMetrics.TAG_HANDLER), eq(HANDLER_NAME),
                    eq(DataCloudHttpMetrics.TAG_OPERATION), eq("retryExecution"),
                    eq(DataCloudHttpMetrics.TAG_ERROR_TYPE), eq("RuntimeException"));
        }
    }

    // ── Rollback ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Rollback metrics")
    class RollbackMetrics {

        @Test
        @DisplayName("rollbackExecution success emits request and latency metrics")
        void rollbackExecutionSuccessEmitsMetrics() {
            when(executionCapability.cancelExecution(eq(TENANT), eq(EXEC_ID)))
                    .thenReturn(Promise.of(snapshot(EXEC_ID, "CANCELLED")));

            runPromise(() -> handler.handleRollbackExecution(
                    mockRequestWithBody("{\"reason\":\"user-initiated\"}")));

            verify(metricsCollector).incrementCounter(
                    eq(DataCloudHttpMetrics.METRIC_REQUESTS),
                    eq(DataCloudHttpMetrics.TAG_HANDLER), eq(HANDLER_NAME),
                    eq(DataCloudHttpMetrics.TAG_OPERATION), eq("rollbackExecution"),
                    eq(DataCloudHttpMetrics.TAG_TENANT), eq(TENANT),
                    eq(DataCloudHttpMetrics.TAG_STATUS), eq("200"));
            verify(metricsCollector).recordTimer(
                    eq(DataCloudHttpMetrics.METRIC_LATENCY),
                    anyLong(),
                    eq(DataCloudHttpMetrics.TAG_HANDLER), eq(HANDLER_NAME),
                    eq(DataCloudHttpMetrics.TAG_OPERATION), eq("rollbackExecution"));
        }

        @Test
        @DisplayName("rollbackExecution failure emits error counter")
        void rollbackExecutionFailureEmitsError() {
            when(executionCapability.cancelExecution(eq(TENANT), eq(EXEC_ID)))
                    .thenReturn(Promise.ofException(new RuntimeException("cannot rollback")));

            runPromise(() -> handler.handleRollbackExecution(
                    mockRequestWithBody("{}")));

            verify(metricsCollector).incrementCounter(
                    eq(DataCloudHttpMetrics.METRIC_ERRORS),
                    eq(DataCloudHttpMetrics.TAG_HANDLER), eq(HANDLER_NAME),
                    eq(DataCloudHttpMetrics.TAG_OPERATION), eq("rollbackExecution"),
                    eq(DataCloudHttpMetrics.TAG_ERROR_TYPE), eq("RuntimeException"));
        }
    }

    // ── Checkpoint ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Checkpoint metrics")
    class CheckpointMetrics {

        @Test
        @DisplayName("checkpointExecution success emits request and latency metrics")
        void checkpointExecutionSuccessEmitsMetrics() {
            DataCloudClient.Entity savedEntity = mock(DataCloudClient.Entity.class);
            when(client.save(eq(TENANT), eq("dc_execution_checkpoints"), any()))
                    .thenReturn(Promise.of(savedEntity));

            runPromise(() -> handler.handleCheckpointExecution(
                    mockRequestWithBody("{\"step\":\"node-1\",\"data\":{}}")));

            verify(metricsCollector).incrementCounter(
                    eq(DataCloudHttpMetrics.METRIC_REQUESTS),
                    eq(DataCloudHttpMetrics.TAG_HANDLER), eq(HANDLER_NAME),
                    eq(DataCloudHttpMetrics.TAG_OPERATION), eq("checkpointExecution"),
                    eq(DataCloudHttpMetrics.TAG_TENANT), eq(TENANT),
                    eq(DataCloudHttpMetrics.TAG_STATUS), eq("200"));
            verify(metricsCollector).recordTimer(
                    eq(DataCloudHttpMetrics.METRIC_LATENCY),
                    anyLong(),
                    eq(DataCloudHttpMetrics.TAG_HANDLER), eq(HANDLER_NAME),
                    eq(DataCloudHttpMetrics.TAG_OPERATION), eq("checkpointExecution"));
        }

        @Test
        @DisplayName("checkpointExecution persistence failure emits error counter")
        void checkpointExecutionPersistenceFailureEmitsError() {
            when(client.save(eq(TENANT), eq("dc_execution_checkpoints"), any()))
                    .thenReturn(Promise.ofException(new RuntimeException("disk full")));

            runPromise(() -> handler.handleCheckpointExecution(
                    mockRequestWithBody("{}")));

            verify(metricsCollector).incrementCounter(
                    eq(DataCloudHttpMetrics.METRIC_ERRORS),
                    eq(DataCloudHttpMetrics.TAG_HANDLER), eq(HANDLER_NAME),
                    eq(DataCloudHttpMetrics.TAG_OPERATION), eq("checkpointExecution"),
                    eq(DataCloudHttpMetrics.TAG_ERROR_TYPE), eq("RuntimeException"));
        }
    }

    // ── Noop metrics ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Noop metrics")
    class NoopMetrics {

        @Test
        @DisplayName("withMetrics(noop) does not call the underlying collector")
        void withNoopMetricsNeverCallsCollector() {
            WorkflowExecutionHandler noopHandler = new WorkflowExecutionHandler(client, http)
                    .withExecutionCapability(executionCapability)
                    .withMetrics(DataCloudHttpMetrics.noop());

            when(executionCapability.cancelExecution(eq(TENANT), eq(EXEC_ID)))
                    .thenReturn(Promise.of(snapshot(EXEC_ID, "CANCELLED")));

            runPromise(() -> noopHandler.handleCancelExecution(mockRequestNoBody()));

            verifyNoInteractions(metricsCollector);
        }

        @Test
        @DisplayName("withMetrics(null) falls back to noop without NPE")
        void withNullMetricsFallsBackToNoop() {
            WorkflowExecutionHandler nullMetricsHandler = new WorkflowExecutionHandler(client, http)
                    .withExecutionCapability(executionCapability)
                    .withMetrics(null);

            when(executionCapability.retryExecution(eq(TENANT), eq(EXEC_ID)))
                    .thenReturn(Promise.of(snapshot(EXEC_ID, "RUNNING")));

            // Should not throw
            runPromise(() -> nullMetricsHandler.handleRetryExecution(mockRequestNoBody()));

            verifyNoInteractions(metricsCollector);
        }
    }

    // ── Correlation ID logging ────────────────────────────────────────────────

    @Nested
    @DisplayName("Correlation ID wiring")
    class CorrelationIdWiring {

        @Test
        @DisplayName("resolveCorrelationId is called on execute path for log correlation")
        void resolveCorrelationIdCalledOnExecutePath() {
            when(executionCapability.execute(eq(TENANT), eq(PIPELINE_ID), any()))
                    .thenReturn(Promise.of(snapshot(EXEC_ID, "RUNNING")));

            runPromise(() -> handler.handleExecutePipeline(
                    mockRequestWithBody("{}")));

            verify(http, atLeastOnce()).resolveCorrelationId(any());
        }

        @Test
        @DisplayName("resolveCorrelationId is called on cancel path for log correlation")
        void resolveCorrelationIdCalledOnCancelPath() {
            when(executionCapability.cancelExecution(eq(TENANT), eq(EXEC_ID)))
                    .thenReturn(Promise.of(snapshot(EXEC_ID, "CANCELLED")));

            runPromise(() -> handler.handleCancelExecution(mockRequestNoBody()));

            verify(http, atLeastOnce()).resolveCorrelationId(any());
        }
    }
}
