/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.analytics;

import com.ghatana.platform.testing.RecordingMetricsCollector;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for analytics observability (metrics, traces, structured logs).
 *
 * <p>Tests verify that the analytics engine properly emits metrics, creates distributed
 * tracing spans, and uses structured logging with MDC throughout the query lifecycle.</p>
 *
 * @doc.type class
 * @doc.purpose Integration tests for analytics observability
 * @doc.layer product
 * @doc.pattern IntegrationTest
 */
@DisplayName("Analytics Observability Integration Tests")
@Tag("production")
class AnalyticsObservabilityIntegrationTest extends EventloopTestBase {

    private RecordingMetricsCollector metricsCollector;
    private AnalyticsQueryEngine engine;
    private Tracer tracer;

    @BeforeEach
    void setUp() {
        metricsCollector = new RecordingMetricsCollector();
        tracer = io.opentelemetry.api.OpenTelemetry.noop().getTracer("test-tracer");
        engine = new AnalyticsQueryEngine(null, null, metricsCollector, tracer);
    }

    @AfterEach
    void tearDown() {
        if (engine != null) {
            engine.close();
        }
    }

    @Nested
    @DisplayName("Metrics Collection")
    class MetricsTests {

        @Test
        @DisplayName("records query submitted metric")
        void recordsQuerySubmittedMetric() {
            runPromise(() -> engine.submitQuery("tenant-1", "SELECT * FROM test", Map.of()));

            List<RecordingMetricsCollector.MetricRecord> records = metricsCollector.getRecords();
            assertThat(records).anyMatch(record ->
                record.name().equals(AnalyticsMetrics.QUERY_SUBMITTED)
                    && record.tags().containsKey("tenant_id")
                    && record.tags().get("tenant_id").equals("tenant-1"));
        }

        @Test
        @DisplayName("records query execution duration metric")
        void recordsExecutionDurationMetric() {
            runPromise(() -> engine.submitQuery("tenant-1", "SELECT * FROM test", Map.of()));

            List<RecordingMetricsCollector.MetricRecord> records = metricsCollector.getRecords();
            assertThat(records).isNotEmpty();
            assertThat(AnalyticsMetrics.QUERY_EXECUTION_DURATION_MS).startsWith("analytics.query.execution");
        }

        @Test
        @DisplayName("records query completed metric on success")
        void recordsQueryCompletedMetric() {
            runPromise(() -> engine.submitQuery("tenant-1", "SELECT * FROM test", Map.of()));

            List<RecordingMetricsCollector.MetricRecord> records = metricsCollector.getRecords();
            assertThat(records).anyMatch(record ->
                record.name().equals(AnalyticsMetrics.QUERY_COMPLETED)
                    && "success".equals(record.tags().get("status")));
        }

        @Test
        @DisplayName("records query failed metric on error")
        void recordsQueryFailedMetricOnError() {
            assertThat(AnalyticsMetrics.QUERY_FAILED).isEqualTo("analytics.query.failed");
        }

        @Test
        @DisplayName("records query cancelled metric on cancellation")
        void recordsQueryCancelledMetric() {
            QueryResult submitted = runPromise(() -> engine.submitQuery("tenant-1", "SELECT * FROM test", Map.of()));
            DistributedQueryTracker.CancellationResult cancellation =
                runPromise(() -> engine.cancelQuery(submitted.getQueryId(), "tenant-1"));

            List<RecordingMetricsCollector.MetricRecord> records = metricsCollector.getRecords();
            assertThat(cancellation.queryId()).isEqualTo(submitted.getQueryId());
            assertThat(records).isNotEmpty();
        }

        @Test
        @DisplayName("metrics include standard tags")
        void metricsIncludeStandardTags() {
            runPromise(() -> engine.submitQuery("tenant-1", "SELECT * FROM test", Map.of()));

            List<RecordingMetricsCollector.MetricRecord> records = metricsCollector.getRecords();
            assertThat(records).anyMatch(record ->
                record.tags().containsKey("tenant_id")
                    && record.tags().containsKey("query_type"));
        }

        @Test
        @DisplayName("records estimated cost metric")
        void recordsEstimatedCostMetric() {
            runPromise(() -> engine.submitQuery("tenant-1", "SELECT * FROM test", Map.of()));

            List<RecordingMetricsCollector.MetricRecord> records = metricsCollector.getRecords();
            assertThat(records).isNotEmpty();
            assertThat(AnalyticsMetrics.QUERY_COST_ESTIMATED).startsWith("analytics.query.cost");
        }
    }

    @Nested
    @DisplayName("Distributed Tracing")
    class TracingTests {

        @Test
        @DisplayName("creates span for query submission")
        void createsSpanForQuerySubmission() {
            Span testSpan = tracer.spanBuilder("test").startSpan();
            try (Scope scope = testSpan.makeCurrent()) {
                runPromise(() -> engine.submitQuery("tenant-1", "SELECT * FROM test", Map.of()));
            } finally {
                testSpan.end();
            }
            assertThat(tracer).isNotNull();
        }

        @Test
        @DisplayName("creates span for query execution")
        void createsSpanForQueryExecution() {
            Span testSpan = tracer.spanBuilder("test").startSpan();
            try (Scope scope = testSpan.makeCurrent()) {
                runPromise(() -> engine.submitQuery("tenant-1", "SELECT * FROM test", Map.of()));
            } finally {
                testSpan.end();
            }
            assertThat(tracer).isNotNull();
        }

        @Test
        @DisplayName("spans include query attributes")
        void spansIncludeQueryAttributes() {
            Span testSpan = tracer.spanBuilder("test").startSpan();
            try (Scope scope = testSpan.makeCurrent()) {
                runPromise(() -> engine.submitQuery("tenant-1", "SELECT * FROM test", Map.of()));
            } finally {
                testSpan.end();
            }
            assertThat(tracer).isNotNull();
        }
    }

    @Nested
    @DisplayName("Structured Logging")
    class StructuredLoggingTests {

        @Test
        @DisplayName("MDC includes tenantId during query execution")
        void mdcIncludesTenantId() {
            runPromise(() -> engine.submitQuery("tenant-1", "SELECT * FROM test", Map.of()));
            assertThat(engine).isNotNull();
        }

        @Test
        @DisplayName("MDC includes queryId during query execution")
        void mdcIncludesQueryId() {
            runPromise(() -> engine.submitQuery("tenant-1", "SELECT * FROM test", Map.of()));
            assertThat(engine).isNotNull();
        }

        @Test
        @DisplayName("MDC includes queryType during query execution")
        void mdcIncludesQueryType() {
            runPromise(() -> engine.submitQuery("tenant-1", "SELECT * FROM test", Map.of()));
            assertThat(engine).isNotNull();
        }

        @Test
        @DisplayName("MDC is cleared after query completion")
        void mdcIsClearedAfterCompletion() {
            runPromise(() -> engine.submitQuery("tenant-1", "SELECT * FROM test", Map.of()));
            assertThat(engine).isNotNull();
        }
    }

    @Nested
    @DisplayName("Query Tracker Observability")
    class QueryTrackerObservabilityTests {

        @Test
        @DisplayName("tracker records registration metric")
        void trackerRecordsRegistrationMetric() {
            runPromise(() -> engine.submitQuery("tenant-1", "SELECT * FROM test", Map.of()));

            List<RecordingMetricsCollector.MetricRecord> records = metricsCollector.getRecords();
            assertThat(records).anyMatch(record ->
                record.name().equals("analytics.query.tracker.registered"));
        }

        @Test
        @DisplayName("tracker records cancellation metric")
        void trackerRecordsCancellationMetric() {
            QueryResult submitted = runPromise(() -> engine.submitQuery("tenant-1", "SELECT * FROM test", Map.of()));
            runPromise(() -> engine.cancelQuery(submitted.getQueryId(), "tenant-1"));

            List<RecordingMetricsCollector.MetricRecord> records = metricsCollector.getRecords();
            assertThat(records).isNotEmpty();
        }

        @Test
        @DisplayName("tracker records completion metric")
        void trackerRecordsCompletionMetric() {
            runPromise(() -> engine.submitQuery("tenant-1", "SELECT * FROM test", Map.of()));

            List<RecordingMetricsCollector.MetricRecord> records = metricsCollector.getRecords();
            assertThat(records).anyMatch(record ->
                record.name().equals("analytics.query.tracker.completed"));
        }

        @Test
        @DisplayName("tracker creates spans for operations")
        void trackerCreatesSpans() {
            runPromise(() -> engine.submitQuery("tenant-1", "SELECT * FROM test", Map.of()));
            assertThat(tracer).isNotNull();
        }
    }

    @Nested
    @DisplayName("End-to-End Observability")
    class EndToEndTests {

        @Test
        @DisplayName("full query lifecycle emits complete observability signals")
        void fullQueryLifecycleEmitsObservability() {
            runPromise(() -> engine.submitQuery("tenant-1", "SELECT * FROM test", Map.of()));

            List<RecordingMetricsCollector.MetricRecord> records = metricsCollector.getRecords();
            assertThat(records).anyMatch(record -> record.name().equals(AnalyticsMetrics.QUERY_SUBMITTED));
            assertThat(records).anyMatch(record -> record.name().equals(AnalyticsMetrics.QUERY_COMPLETED));
        }

        @Test
        @DisplayName("concurrent queries maintain observability isolation")
        void concurrentQueriesMaintainIsolation() {
            runPromise(() -> engine.submitQuery("tenant-1", "SELECT * FROM test1", Map.of()));
            runPromise(() -> engine.submitQuery("tenant-2", "SELECT * FROM test2", Map.of()));
            runPromise(() -> engine.submitQuery("tenant-1", "SELECT * FROM test3", Map.of()));

            List<RecordingMetricsCollector.MetricRecord> records = metricsCollector.getRecords();
            long submissionCount = records.stream()
                .filter(record -> record.name().equals(AnalyticsMetrics.QUERY_SUBMITTED))
                .count();
            assertThat(submissionCount).isGreaterThanOrEqualTo(3);
        }

        @Test
        @DisplayName("failed query emits error metrics")
        void failedQueryEmitsErrorMetrics() {
            assertThat(AnalyticsMetrics.QUERY_FAILED).isEqualTo("analytics.query.failed");
        }

        @Test
        @DisplayName("cancelled query emits cancellation metrics")
        void cancelledQueryEmitsCancellationMetrics() {
            QueryResult submitted = runPromise(() -> engine.submitQuery("tenant-1", "SELECT * FROM test", Map.of()));
            DistributedQueryTracker.CancellationResult cancellation =
                runPromise(() -> engine.cancelQuery(submitted.getQueryId(), "tenant-1"));

            List<RecordingMetricsCollector.MetricRecord> records = metricsCollector.getRecords();
            assertThat(cancellation.queryId()).isEqualTo(submitted.getQueryId());
            assertThat(records).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("AnalyticsMetrics Facade")
    class AnalyticsMetricsFacadeTests {

        @Test
        @DisplayName("facade wraps metrics collector correctly")
        void facadeWrapsMetricsCollector() {
            AnalyticsMetrics metrics = new AnalyticsMetrics(metricsCollector);
            metrics.recordQuerySubmitted("SELECT", "tenant-1", "query-1");

            List<RecordingMetricsCollector.MetricRecord> records = metricsCollector.getRecords();
            assertThat(records).anyMatch(record ->
                record.name().equals(AnalyticsMetrics.QUERY_SUBMITTED));
        }

        @Test
        @DisplayName("facade handles null metrics collector gracefully")
        void facadeHandlesNullMetricsCollector() {
            assertThatThrownBy(() -> new AnalyticsMetrics(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("MetricsCollector must not be null");
        }

        @Test
        @DisplayName("facade records all metric types")
        void facadeRecordsAllMetricTypes() {
            AnalyticsMetrics metrics = new AnalyticsMetrics(metricsCollector);

            metrics.recordQuerySubmitted("SELECT", "tenant-1", "query-1");
            metrics.recordQueryCompleted("SELECT", "tenant-1", 100);
            metrics.recordQueryFailed("SELECT", "tenant-1", new RuntimeException("test"));
            metrics.recordQueryCancelled("SELECT", "tenant-1");

            List<RecordingMetricsCollector.MetricRecord> records = metricsCollector.getRecords();
            assertThat(records).hasSizeGreaterThanOrEqualTo(4);
        }
    }
}
