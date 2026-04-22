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

@DisplayName("AiQualityTelemetry [GH-90000]")
class AiQualityTelemetryTest {

    @Test
    @DisplayName("recordCompletion records token, request, and estimated cost metrics [GH-90000]")
    void shouldRecordCompletionTelemetry() { // GH-90000
        MetricsCollector metrics = mock(MetricsCollector.class); // GH-90000
        CompletionResult result = CompletionResult.builder() // GH-90000
                .text("Generated response with enough detail for confidence scoring. [GH-90000]")
                .promptTokens(300) // GH-90000
                .completionTokens(120) // GH-90000
                .tokensUsed(420) // GH-90000
                .finishReason("stop [GH-90000]")
                .modelUsed("gpt-4o [GH-90000]")
                .build(); // GH-90000

        AiQualityTelemetry.recordCompletion(metrics, "yappc.ai.test", result, Map.of("tenant", "t-1")); // GH-90000

        verify(metrics).incrementCounter(eq("yappc.ai.test.request [GH-90000]"), anyMap());
        verify(metrics).increment(eq("yappc.ai.test.tokens.total [GH-90000]"), eq(420.0), anyMap());
        verify(metrics).increment(eq("yappc.ai.test.tokens.prompt [GH-90000]"), eq(300.0), anyMap());
        verify(metrics).increment(eq("yappc.ai.test.tokens.completion [GH-90000]"), eq(120.0), anyMap());
        verify(metrics).increment(eq("yappc.ai.test.cost.estimated_usd [GH-90000]"), eq(0.0033), anyMap());
    }

    @Test
    @DisplayName("recordFallback tags error type and fallback flag [GH-90000]")
    void shouldRecordFallbackTelemetry() { // GH-90000
        MetricsCollector metrics = mock(MetricsCollector.class); // GH-90000

        AiQualityTelemetry.recordFallback( // GH-90000
                metrics,
                "yappc.ai.intent.capture",
                new IllegalStateException("failed [GH-90000]"),
                Map.of("tenant", "t-1")); // GH-90000

        verify(metrics).incrementCounter(eq("yappc.ai.intent.capture.fallback [GH-90000]"), anyMap());
    }

    @Test
    @DisplayName("estimateConfidence returns bounded score [GH-90000]")
    void shouldEstimateBoundedConfidence() { // GH-90000
        CompletionResult result = CompletionResult.builder() // GH-90000
                .text("This is a sufficiently detailed response to produce non-trivial confidence score. [GH-90000]")
                .tokensUsed(100) // GH-90000
                .finishReason("stop [GH-90000]")
                .build(); // GH-90000

        double score = AiQualityTelemetry.estimateConfidence(result); // GH-90000
        assertThat(score).isGreaterThanOrEqualTo(0.0); // GH-90000
        assertThat(score).isLessThanOrEqualTo(1.0); // GH-90000
    }

    @Test
    @DisplayName("estimateCostUsd returns zero for local/unknown models [GH-90000]")
    void shouldEstimateZeroCostForLocalModel() { // GH-90000
        CompletionResult result = CompletionResult.builder() // GH-90000
                .modelUsed("llama3.2 [GH-90000]")
                .promptTokens(1000) // GH-90000
                .completionTokens(1000) // GH-90000
                .build(); // GH-90000

        double cost = AiQualityTelemetry.estimateCostUsd(result); // GH-90000
        assertThat(cost).isEqualTo(0.0); // GH-90000
    }
}
