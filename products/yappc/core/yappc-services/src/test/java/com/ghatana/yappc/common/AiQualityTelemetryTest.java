package com.ghatana.yappc.common;

import com.ghatana.ai.llm.CompletionResult;
import com.ghatana.platform.observability.MetricsCollector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("AiQualityTelemetry")
class AiQualityTelemetryTest {

    @Test
    @DisplayName("recordCompletion records token, request, and estimated cost metrics")
    void shouldRecordCompletionTelemetry() {
        MetricsCollector metrics = mock(MetricsCollector.class);
        CompletionResult result = CompletionResult.builder()
                .text("Generated response with enough detail for confidence scoring.")
                .promptTokens(300)
                .completionTokens(120)
                .tokensUsed(420)
                .finishReason("stop")
                .modelUsed("gpt-4o")
                .build();

        AiQualityTelemetry.recordCompletion(metrics, "yappc.ai.test", result, Map.of("tenant", "t-1"));

        verify(metrics).incrementCounter(eq("yappc.ai.test.request"), anyMap());
        verify(metrics).increment(eq("yappc.ai.test.tokens.total"), eq(420.0), anyMap());
        verify(metrics).increment(eq("yappc.ai.test.tokens.prompt"), eq(300.0), anyMap());
        verify(metrics).increment(eq("yappc.ai.test.tokens.completion"), eq(120.0), anyMap());
        verify(metrics).increment(eq("yappc.ai.test.cost.estimated_usd"), eq(0.0033), anyMap());
    }

    @Test
    @DisplayName("recordFallback tags error type and fallback flag")
    void shouldRecordFallbackTelemetry() {
        MetricsCollector metrics = mock(MetricsCollector.class);

        AiQualityTelemetry.recordFallback(
                metrics,
                "yappc.ai.intent.capture",
                new IllegalStateException("failed"),
                Map.of("tenant", "t-1"));

        verify(metrics).incrementCounter(eq("yappc.ai.intent.capture.fallback"), anyMap());
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
}
