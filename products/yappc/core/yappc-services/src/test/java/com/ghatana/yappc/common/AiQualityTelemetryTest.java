package com.ghatana.yappc.common;

import com.ghatana.ai.llm.CompletionResult;
import com.ghatana.platform.observability.MetricsCollector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AiQualityTelemetry")
class AiQualityTelemetryTest {

    @Test
    @DisplayName("recordCompletion records token, request, and estimated cost metrics")
    void shouldRecordCompletionTelemetry() {
        InMemoryMetricsCollector metrics = new InMemoryMetricsCollector();
        CompletionResult result = CompletionResult.builder()
                .text("Generated response with enough detail for confidence scoring.")
                .promptTokens(300)
                .completionTokens(120)
                .tokensUsed(420)
                .finishReason("stop")
                .modelUsed("gpt-4o")
                .build();

        AiQualityTelemetry.recordCompletion(metrics, "yappc.ai.test", result, Map.of("tenant", "t-1"));

        assertThat(metrics.getIncrementCounterCalls()).hasSize(1);
        assertThat(metrics.getIncrementCounterCalls().get(0)).isEqualTo("yappc.ai.test.request");
        assertThat(metrics.getIncrementCalls()).containsExactlyInAnyOrder(
            Map.entry("yappc.ai.test.tokens.total", 420.0),
            Map.entry("yappc.ai.test.tokens.prompt", 300.0),
            Map.entry("yappc.ai.test.tokens.completion", 120.0),
            Map.entry("yappc.ai.test.cost.estimated_usd", 0.0033)
        );
    }

    @Test
    @DisplayName("recordFallback tags error type and fallback flag")
    void shouldRecordFallbackTelemetry() {
        InMemoryMetricsCollector metrics = new InMemoryMetricsCollector();

        AiQualityTelemetry.recordFallback(
                metrics,
                "yappc.ai.intent.capture",
                new IllegalStateException("failed"),
                Map.of("tenant", "t-1"));

        assertThat(metrics.getIncrementCounterCalls()).containsExactly("yappc.ai.intent.capture.fallback");
    }

    @Test
    @DisplayName("estimateConfidence returns bounded score")
    void shouldEstimateBoundedConfidence() {
        CompletionResult result = CompletionResult.builder()
                .text("This is a sufficiently detailed response to produce non-trivial confidence score.")
                .tokensUsed(100)
                .finishReason("stop")
                .build();

        double score = AiQualityTelemetry.estimateConfidence(result);
        assertThat(score).isGreaterThanOrEqualTo(0.0);
        assertThat(score).isLessThanOrEqualTo(1.0);
    }

    @Test
    @DisplayName("estimateCostUsd returns zero for local/unknown models")
    void shouldEstimateZeroCostForLocalModel() {
        CompletionResult result = CompletionResult.builder()
                .modelUsed("llama3.2")
                .promptTokens(1000)
                .completionTokens(1000)
                .build();

        double cost = AiQualityTelemetry.estimateCostUsd(result);
        assertThat(cost).isEqualTo(0.0);
    }

    private static final class InMemoryMetricsCollector implements MetricsCollector {
        private final java.util.List<String> incrementCounterCalls = new java.util.ArrayList<>();
        private final java.util.List<Map.Entry<String, Double>> incrementCalls = new java.util.ArrayList<>();

        java.util.List<String> getIncrementCounterCalls() {
            return incrementCounterCalls;
        }

        java.util.List<Map.Entry<String, Double>> getIncrementCalls() {
            return incrementCalls;
        }

        @Override
        public void incrementCounter(String name, Map<String, String> tags) {
            incrementCounterCalls.add(name);
        }

        @Override
        public void increment(String name, double amount, Map<String, String> tags) {
            incrementCalls.add(Map.entry(name, amount));
        }

        public void gauge(String name, double value, Map<String, String> tags) {
        }

        public void histogram(String name, double value, Map<String, String> tags) {
        }

        @Override
        public io.micrometer.core.instrument.MeterRegistry getMeterRegistry() {
            return io.micrometer.core.instrument.Metrics.globalRegistry;
        }

        @Override
        public void recordError(String metricName, Exception e, Map<String, String> tags) {
        }

        @Override
        public void incrementCounter(String metricName, String... keyValues) {
            incrementCounterCalls.add(metricName);
        }
    }
}
