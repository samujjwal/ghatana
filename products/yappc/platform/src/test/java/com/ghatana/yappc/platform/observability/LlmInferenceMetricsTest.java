package com.ghatana.yappc.platform.observability;

import com.ghatana.platform.observability.MetricsCollector;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link LlmInferenceMetrics}.
 *
 * <p>Verifies that correct counters, timers, and token summaries are emitted for each
 * inference lifecycle event, and that constructor guards reject nulls.
 */
@DisplayName("LlmInferenceMetrics")
@ExtendWith(MockitoExtension.class) // GH-90000
class LlmInferenceMetricsTest {

    @Mock
    private MetricsCollector metricsCollector;

    private MeterRegistry meterRegistry;
    private LlmInferenceMetrics metrics;

    @BeforeEach
    void setUp() { // GH-90000
        meterRegistry = new SimpleMeterRegistry(); // GH-90000
        metrics = new LlmInferenceMetrics(metricsCollector, meterRegistry); // GH-90000
    }

    // ─── Constructor guards ────────────────────────────────────────────────

    @Test
    @DisplayName("constructor rejects null MetricsCollector")
    void constructor_rejectsNullMetricsCollector() { // GH-90000
        assertThatThrownBy(() -> new LlmInferenceMetrics(null, meterRegistry)) // GH-90000
                .isInstanceOf(NullPointerException.class); // GH-90000
    }

    @Test
    @DisplayName("constructor rejects null MeterRegistry")
    void constructor_rejectsNullMeterRegistry() { // GH-90000
        assertThatThrownBy(() -> new LlmInferenceMetrics(metricsCollector, null)) // GH-90000
                .isInstanceOf(NullPointerException.class); // GH-90000
    }

    // ─── startInference ───────────────────────────────────────────────────

    @Test
    @DisplayName("startInference increments invoked counter and returns a non-null Sample")
    void startInference_incrementsCounterAndReturnsSample() { // GH-90000
        Timer.Sample sample = metrics.startInference("scaffolding", "gpt-4o"); // GH-90000

        verify(metricsCollector).incrementCounter( // GH-90000
                LlmInferenceMetrics.METRIC_INVOKED,
                "workflow", "scaffolding",
                "model",    "gpt-4o");
        assertThat(sample).isNotNull(); // GH-90000
    }

    // ─── recordInferenceSuccess ───────────────────────────────────────────

    @Test
    @DisplayName("recordInferenceSuccess increments succeeded counter and stops timer")
    void recordInferenceSuccess_incrementsCounterAndStopsTimer() { // GH-90000
        Timer.Sample sample = metrics.startInference("scaffolding", "gpt-4o"); // GH-90000

        metrics.recordInferenceSuccess(sample, "scaffolding", "gpt-4o", 512); // GH-90000

        verify(metricsCollector).incrementCounter( // GH-90000
                LlmInferenceMetrics.METRIC_SUCCEEDED,
                "workflow", "scaffolding",
                "model",    "gpt-4o");

        // Verify timer was recorded in the in-memory registry
        assertThat(meterRegistry.find(LlmInferenceMetrics.METRIC_DURATION).timers()).isNotEmpty(); // GH-90000
    }

    @Test
    @DisplayName("recordInferenceSuccess records token usage when tokensUsed > 0")
    void recordInferenceSuccess_recordsTokenUsageWhenPositive() { // GH-90000
        Timer.Sample sample = metrics.startInference("requirements-nlp", "claude-3-opus"); // GH-90000

        metrics.recordInferenceSuccess(sample, "requirements-nlp", "claude-3-opus", 1024); // GH-90000

        // Token distribution summary should be registered
        assertThat(meterRegistry.find(LlmInferenceMetrics.METRIC_TOKENS).summaries()).isNotEmpty(); // GH-90000
        double totalTokens = meterRegistry.find(LlmInferenceMetrics.METRIC_TOKENS) // GH-90000
                .summary().totalAmount(); // GH-90000
        assertThat(totalTokens).isEqualTo(1024.0); // GH-90000
    }

    @Test
    @DisplayName("recordInferenceSuccess skips token recording when tokensUsed is zero")
    void recordInferenceSuccess_skipsTokenRecordingForZero() { // GH-90000
        Timer.Sample sample = metrics.startInference("canvas-layout", "unknown"); // GH-90000

        metrics.recordInferenceSuccess(sample, "canvas-layout", "unknown", 0); // GH-90000

        // No token summary should be registered
        assertThat(meterRegistry.find(LlmInferenceMetrics.METRIC_TOKENS).summaries()).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("recordInferenceSuccess handles null sample gracefully")
    void recordInferenceSuccess_handlesNullSampleGracefully() { // GH-90000
        // Should not throw
        metrics.recordInferenceSuccess(null, "scaffolding", "gpt-4o", 256); // GH-90000

        verify(metricsCollector).incrementCounter( // GH-90000
                LlmInferenceMetrics.METRIC_SUCCEEDED,
                "workflow", "scaffolding",
                "model",    "gpt-4o");
    }

    // ─── recordInferenceFailure ───────────────────────────────────────────

    @Test
    @DisplayName("recordInferenceFailure increments failed counter with error_type tag")
    void recordInferenceFailure_incrementsFailedCounterWithErrorType() { // GH-90000
        Timer.Sample sample = metrics.startInference("scaffolding", "gpt-4o"); // GH-90000
        RuntimeException cause = new RuntimeException("rate limit exceeded");

        metrics.recordInferenceFailure(sample, "scaffolding", "gpt-4o", "rate_limit", cause); // GH-90000

        verify(metricsCollector).incrementCounter( // GH-90000
                LlmInferenceMetrics.METRIC_FAILED,
                "workflow",   "scaffolding",
                "model",      "gpt-4o",
                "error_type", "rate_limit");
    }

    @Test
    @DisplayName("recordInferenceFailure records error event when cause is provided")
    void recordInferenceFailure_recordsErrorEventWithCause() { // GH-90000
        Timer.Sample sample = metrics.startInference("scaffolding", "gpt-4o"); // GH-90000
        RuntimeException cause = new RuntimeException("timeout");

        metrics.recordInferenceFailure(sample, "scaffolding", "gpt-4o", "timeout", cause); // GH-90000

        verify(metricsCollector).recordError(eq(LlmInferenceMetrics.METRIC_FAILED), eq(cause), any()); // GH-90000
    }

    @Test
    @DisplayName("recordInferenceFailure skips recordError when cause is null")
    void recordInferenceFailure_skipsRecordErrorWhenCauseIsNull() { // GH-90000
        Timer.Sample sample = metrics.startInference("scaffolding", "gpt-4o"); // GH-90000

        metrics.recordInferenceFailure(sample, "scaffolding", "gpt-4o", "parse_error", null); // GH-90000

        verify(metricsCollector, never()).recordError(anyString(), any(), any()); // GH-90000
    }

    @Test
    @DisplayName("recordInferenceFailure stops timer for failure status")
    void recordInferenceFailure_stopsTimerWithFailureStatus() { // GH-90000
        Timer.Sample sample = metrics.startInference("requirements-nlp", "claude-3-opus"); // GH-90000

        metrics.recordInferenceFailure(sample, "requirements-nlp", "claude-3-opus", "completion_error", null); // GH-90000

        assertThat(meterRegistry.find(LlmInferenceMetrics.METRIC_DURATION).timers()).isNotEmpty(); // GH-90000
    }

    @Test
    @DisplayName("recordInferenceFailure handles null sample gracefully")
    void recordInferenceFailure_handlesNullSampleGracefully() { // GH-90000
        // Should not throw
        metrics.recordInferenceFailure(null, "scaffolding", "gpt-4o", "timeout", null); // GH-90000

        verify(metricsCollector).incrementCounter( // GH-90000
                LlmInferenceMetrics.METRIC_FAILED,
                "workflow",   "scaffolding",
                "model",      "gpt-4o",
                "error_type", "timeout");
    }

    // ─── Multiple workflows and models ────────────────────────────────────

    @Test
    @DisplayName("independent invocations do not cross-contaminate counters")
    void independentInvocations_doNotCrossContaminateCounters() { // GH-90000
        Timer.Sample s1 = metrics.startInference("scaffolding", "gpt-4o"); // GH-90000
        Timer.Sample s2 = metrics.startInference("requirements-nlp", "claude-3-opus"); // GH-90000

        metrics.recordInferenceSuccess(s1, "scaffolding", "gpt-4o", 300); // GH-90000
        metrics.recordInferenceFailure(s2, "requirements-nlp", "claude-3-opus", "timeout", null); // GH-90000

        verify(metricsCollector).incrementCounter( // GH-90000
                LlmInferenceMetrics.METRIC_SUCCEEDED,
                "workflow", "scaffolding",
                "model",    "gpt-4o");
        verify(metricsCollector).incrementCounter( // GH-90000
                LlmInferenceMetrics.METRIC_FAILED,
                "workflow",   "requirements-nlp",
                "model",      "claude-3-opus",
                "error_type", "timeout");
    }
}
