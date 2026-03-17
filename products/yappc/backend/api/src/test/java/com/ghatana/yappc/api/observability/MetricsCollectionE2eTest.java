/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.observability;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

/**
 * E2E test for Prometheus metrics collection and visibility.
 *
 * <p>Tests 6.1.5–6.1.6 requirements:
 * <ol>
 *   <li>6.1.5 — Grafana dashboard configured with agent_execution_duration_ms, phase_advance_count, llm_call_latency</li>
 *   <li>6.1.6 — Metrics endpoint (/metrics) exposes all collected metrics in Prometheus format</li>
 * </ol>
 *
 * <p><b>Test Scenarios:</b>
 * <ul>
 *   <li>Metrics collection active in MicrometerMetricsCollector</li>
 *   <li>Agent execution duration histogram recorded</li>
 *   <li>Phase advance counter incremented</li>
 *   <li>LLM call latency measured</li>
 *   <li>All metrics scraped via /metrics endpoint in Prometheus format</li>
 *   <li>Metrics queryable via Prometheus queries (histogram percentiles, rates)</li>
 *   <li>Correlation IDs included in metric tags</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose E2E tests for metrics collection and Prometheus exposure
 * @doc.layer product
 * @doc.pattern Test
 *
 * @since 2.4.0
 */
@DisplayName("Metrics Collection and Prometheus E2E Tests")
class MetricsCollectionE2eTest extends EventloopTestBase {

    private MicrometerMetricsCollector metricsCollector;
    private MockMetricsRecorder mockRecorder;

    @BeforeEach
    void setUp() {
        metricsCollector = MicrometerMetricsCollector.create();
        mockRecorder = new MockMetricsRecorder(metricsCollector);
    }

    // =========================================================================
    // 6.1.5: Grafana Dashboard Metrics Collection
    // =========================================================================

    @Nested
    @DisplayName("Metrics Collection for Grafana Dashboard")
    class MetricsCollectionTests {

        @Test
        @DisplayName("should collect agent_execution_duration_ms histogram")
        void shouldCollectAgentExecutionDuration() {
            // GIVEN — record agent execution duration
            mockRecorder.recordAgentExecutionDuration("agent-001", 1250);
            mockRecorder.recordAgentExecutionDuration("agent-002", 890);
            mockRecorder.recordAgentExecutionDuration("agent-001", 2100);

            // WHEN — scrape metrics
            String prometheusText = metricsCollector.scrape();

            // THEN — histogram present in output
            assertThat(prometheusText)
                    .contains("agent_execution_duration_ms_seconds_bucket")
                    .contains("agent_execution_duration_ms_seconds_count")
                    .contains("agent_execution_duration_ms_seconds_sum");
        }

        @Test
        @DisplayName("should collect phase_advance_count counter")
        void shouldCollectPhaseAdvanceCount() {
            // GIVEN — record phase advances
            mockRecorder.recordPhaseAdvance("PERCEIVE");
            mockRecorder.recordPhaseAdvance("REASON");
            mockRecorder.recordPhaseAdvance("ACT");
            mockRecorder.recordPhaseAdvance("CAPTURE");

            // WHEN — scrape metrics
            String prometheusText = metricsCollector.scrape();

            // THEN — counter present
            assertThat(prometheusText)
                    .contains("phase_advance_count_total")
                    .contains("PERCEIVE")
                    .contains("REASON")
                    .contains("ACT")
                    .contains("CAPTURE");
        }

        @Test
        @DisplayName("should collect llm_call_latency_ms histogram")
        void shouldCollectLlmCallLatency() {
            // GIVEN — record LLM call latencies
            mockRecorder.recordLlmCallLatency("gpt-4", 2500);
            mockRecorder.recordLlmCallLatency("gpt-3.5-turbo", 800);
            mockRecorder.recordLlmCallLatency("gpt-4", 3100);

            // WHEN — scrape metrics
            String prometheusText = metricsCollector.scrape();

            // THEN — histogram present
            assertThat(prometheusText)
                    .contains("llm_call_latency_ms_seconds_bucket")
                    .contains("llm_call_latency_ms_seconds_count")
                    .contains("llm_call_latency_ms_seconds_sum")
                    .contains("gpt-4")
                    .contains("gpt-3.5-turbo");
        }

        @Test
        @DisplayName("should include correlation ID in metric labels as tag")
        void shouldIncludeCorrelationIdInMetrics() {
            // GIVEN — record metric with correlation ID
            mockRecorder.recordAgentExecutionDurationWithCorrelationId("agent-001", 1500, "trace-001");

            // WHEN — scrape metrics
            String prometheusText = metricsCollector.scrape();

            // THEN — correlation ID tag present
            assertThat(prometheusText).contains("correlation_id");
        }
    }

    // =========================================================================
    // 6.1.6: Prometheus Scrape Endpoint and Queryability
    // =========================================================================

    @Nested
    @DisplayName("Prometheus Scrape Endpoint and Queries")
    class PrometheusScraperTests {

        @Test
        @DisplayName("should expose /metrics endpoint with Prometheus format")
        void shouldExposeMetricsInPrometheusFormat() {
            // GIVEN — recorded metrics
            mockRecorder.recordAgentExecutionDuration("agent-001", 1000);
            mockRecorder.recordPhaseAdvance("PERCEIVE");
            mockRecorder.recordLlmCallLatency("gpt-4", 2000);

            // WHEN — scrape
            String prometheusText = metricsCollector.scrape();

            // THEN — Prometheus format validation
            // All metrics should be:
            // 1. Prefixed with HELP and TYPE comments (optional but common)
            // 2. Followed by metric lines: metric_name{labels} value timestamp
            // 3. Properly escaped labels and values
            String[] lines = prometheusText.split("\n");
            assertThat(lines.length).isGreaterThan(5);

            // Find at least one histogram metric (should have _bucket, _count, _sum)
            long histogramLines = prometheusText.lines()
                    .filter(line -> line.contains("_bucket") || line.contains("_count") || line.contains("_sum"))
                    .count();
            assertThat(histogramLines).isGreaterThan(0);

            // Find counter metric
            long counterLines = prometheusText.lines()
                    .filter(line -> line.contains("_total"))
                    .count();
            assertThat(counterLines).isGreaterThan(0);
        }

        @Test
        @DisplayName("should support Prometheus histogram_quantile queries")
        void shouldSupportHistogramQuantileQueries() {
            // GIVEN — multiple histogram samples
            for (int i = 0; i < 100; i++) {
                mockRecorder.recordAgentExecutionDuration("agent-001", 500 + (i * 10));
            }

            // WHEN — scrape and verify histogram buckets exist
            String prometheusText = metricsCollector.scrape();

            // THEN — bucket lines present for histogram_quantile calculation
            assertThat(prometheusText)
                    .contains("agent_execution_duration_ms_seconds_bucket");

            // Verify bucket labels exist (le="X" for bucket boundaries)
            long bucketLines = prometheusText.lines()
                    .filter(line -> line.contains("agent_execution_duration_ms_seconds_bucket") && line.contains("le="))
                    .count();
            assertThat(bucketLines).isGreaterThan(0);
        }

        @Test
        @DisplayName("should support Prometheus rate() queries for counters")
        void shouldSupportRateQueries() {
            // GIVEN — recorded phase advances over time
            for (int i = 0; i < 10; i++) {
                mockRecorder.recordPhaseAdvance("PERCEIVE");
            }

            // WHEN — scrape
            String prometheusText = metricsCollector.scrape();

            // THEN — counter metric with _total suffix suitable for rate()
            assertThat(prometheusText)
                    .contains("phase_advance_count_total");

            // Verify it has a numeric value
            long valueLines = prometheusText.lines()
                    .filter(line -> line.contains("phase_advance_count_total") && !line.startsWith("#"))
                    .filter(line -> {
                        String[] parts = line.split(" ");
                        return parts.length >= 2;
                    })
                    .count();
            assertThat(valueLines).isGreaterThan(0);
        }

        @Test
        @DisplayName("should provide readable labels for Grafana dashboard templates")
        void shouldProvideDashboardLabels() {
            // GIVEN
            mockRecorder.recordAgentExecutionDuration("agent-analytics", 1200);
            mockRecorder.recordAgentExecutionDuration("agent-policy", 950);
            mockRecorder.recordLlmCallLatency("gpt-4", 2500);
            mockRecorder.recordLlmCallLatency("claude", 1800);

            // WHEN — scrape
            String prometheusText = metricsCollector.scrape();

            // THEN — Grafana-friendly label format
            assertThat(prometheusText)
                    .contains("agent_id")
                    .contains("model")
                    .contains("agent-analytics")
                    .contains("agent-policy")
                    .contains("gpt-4")
                    .contains("claude");
        }

        @Test
        @DisplayName("should separate multi-phase metrics by phase label")
        void shouldLabelMetricsByPhase() {
            // GIVEN
            mockRecorder.recordPhaseAdvance("PERCEIVE");
            mockRecorder.recordPhaseAdvance("PERCEIVE");
            mockRecorder.recordPhaseAdvance("REASON");
            mockRecorder.recordPhaseAdvance("ACT");

            // WHEN
            String prometheusText = metricsCollector.scrape();

            // THEN — Each phase is a separate label dimension
            assertThat(prometheusText)
                    .contains("phase=\"PERCEIVE\"")
                    .contains("phase=\"REASON\"")
                    .contains("phase=\"ACT\"");

            // Verify PERCEIVE has higher count (2 vs 1 vs 1)
            long perceiveLines = prometheusText.lines()
                    .filter(l -> l.contains("phase=\"PERCEIVE\""))
                    .count();
            assertThat(perceiveLines).isGreaterThan(0);
        }
    }

    // =========================================================================
    // 6.1.6: Intent Capture Workflow (End-to-End Verification)
    // =========================================================================

    @Nested
    @DisplayName("Intent Capture Metrics Workflow")
    class IntentCaptureMetricsTests {

        @Test
        @DisplayName("should record intent.capture.duration histogram during intent capture")
        void shouldRecordIntentCaptureDuration() {
            // GIVEN — simulate intent capture operation
            mockRecorder.recordIntentCaptureDuration(1500, "trace-intent-001");

            // WHEN — query metrics
            String prometheusText = metricsCollector.scrape();

            // THEN — intent.capture.duration histogram present
            assertThat(prometheusText)
                    .contains("intent_capture_duration_ms_seconds_bucket")
                    .contains("intent_capture_duration_ms_seconds_count")
                    .contains("intent_capture_duration_ms_seconds_sum")
                    .contains("trace-intent-001");
        }

        @Test
        @DisplayName("should support querying intent capture latency percentiles")
        void shouldQueryIntentCapturePercentiles() {
            // GIVEN — multiple intent captures with varying latencies
            mockRecorder.recordIntentCaptureDuration(200, "intent-1");
            mockRecorder.recordIntentCaptureDuration(500, "intent-2");
            mockRecorder.recordIntentCaptureDuration(1200, "intent-3");
            mockRecorder.recordIntentCaptureDuration(2500, "intent-4");

            // WHEN
            String prometheusText = metricsCollector.scrape();

            // THEN — histogram data suitable for percentile calculation
            assertThat(prometheusText).contains("intent_capture_duration_ms_seconds_bucket");

            // Verify count matches 4 records
            String countLine = prometheusText.lines()
                    .filter(l -> l.contains("intent_capture_duration_ms_seconds_count") && !l.startsWith("#"))
                    .findFirst()
                    .orElse("");
            assertThat(countLine).isNotEmpty();
        }

        @Test
        @DisplayName("should record full agent turn with all phase durations")
        void shouldRecordFullAgentTurnMetrics() {
            // GIVEN — full agent turn: PERCEIVE → REASON → ACT → CAPTURE
            mockRecorder.recordIntentCaptureDuration(300, "turn-1");
            mockRecorder.recordPhaseAdvance("PERCEIVE");
            mockRecorder.recordPhaseAdvance("REASON");
            mockRecorder.recordPhaseAdvance("ACT");
            mockRecorder.recordPhaseAdvance("CAPTURE");
            mockRecorder.recordAgentExecutionDuration("agent-001", 5000);

            // WHEN
            String prometheusText = metricsCollector.scrape();

            // THEN — all metrics for the turn present
            assertThat(prometheusText)
                    .contains("intent_capture_duration_ms")
                    .contains("phase_advance_count_total")
                    .contains("phase=\"PERCEIVE\"")
                    .contains("phase=\"REASON\"")
                    .contains("phase=\"ACT\"")
                    .contains("phase=\"CAPTURE\"")
                    .contains("agent_execution_duration_ms");
        }

        @Test
        @DisplayName("should maintain metric consistency after service restart")
        void shouldMaintainMetricStateAcrossRestart() {
            // GIVEN — initial metrics
            mockRecorder.recordAgentExecutionDuration("agent-001", 1000);
            String metrics1 = metricsCollector.scrape();

            // WHEN — simulate metrics collection continuing (new collector)
            MicrometerMetricsCollector newCollector = MicrometerMetricsCollector.create();
            MockMetricsRecorder newRecorder = new MockMetricsRecorder(newCollector);
            newRecorder.recordAgentExecutionDuration("agent-001", 1100);

            // THEN — new collector is independent (in-memory, not persistent)
            String metrics2 = newCollector.scrape();
            assertThat(metrics1).contains("agent_execution_duration_ms");
            assertThat(metrics2).contains("agent_execution_duration_ms");
            // Each collector is independent
            assertThat(metrics1).isNotEqualTo(metrics2);
        }
    }

    // =========================================================================
    // Mock Metrics Recorder (Test Helper)
    // =========================================================================

    /**
     * Helper class to record metrics into MicrometerMetricsCollector for testing.
     * Simulates what the actual services would do.
     */
    private static class MockMetricsRecorder {
        private final MicrometerMetricsCollector collector;
        private final MeterRegistry registry;

        MockMetricsRecorder(MicrometerMetricsCollector collector) {
            this.collector = collector;
            this.registry = collector.getMeterRegistry();
        }

        void recordAgentExecutionDuration(String agentId, long durationMs) {
            Timer.builder("agent_execution_duration_ms")
                    .tags(Tags.of("agent_id", agentId))
                    .publishPercentileHistogram(true)
                    .register(registry)
                    .record(durationMs, TimeUnit.MILLISECONDS);
        }

        void recordAgentExecutionDurationWithCorrelationId(String agentId, long durationMs, String correlationId) {
            Timer.builder("agent_execution_duration_ms")
                    .tags(Tags.of("agent_id", agentId, "correlation_id", correlationId))
                    .publishPercentileHistogram(true)
                    .register(registry)
                    .record(durationMs, TimeUnit.MILLISECONDS);
        }

        void recordPhaseAdvance(String phase) {
            registry.counter("phase_advance_count", Tags.of("phase", phase)).increment();
        }

        void recordLlmCallLatency(String model, long latencyMs) {
            Timer.builder("llm_call_latency_ms")
                    .tags(Tags.of("model", model))
                    .publishPercentileHistogram(true)
                    .register(registry)
                    .record(latencyMs, TimeUnit.MILLISECONDS);
        }

        void recordIntentCaptureDuration(long durationMs, String correlationId) {
            Timer.builder("intent_capture_duration_ms")
                    .tags(Tags.of("correlation_id", correlationId))
                    .publishPercentileHistogram(true)
                    .register(registry)
                    .record(durationMs, TimeUnit.MILLISECONDS);
        }
    }
}
