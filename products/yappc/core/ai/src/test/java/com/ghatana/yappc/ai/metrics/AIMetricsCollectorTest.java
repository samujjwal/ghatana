package com.ghatana.yappc.ai.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link AIMetricsCollector}.
 *
 * <p>Uses a {@link SimpleMeterRegistry} for synchronous metric inspection.
 * No async code — no {@code EventloopTestBase} required.
 */
@DisplayName("AIMetricsCollector")
class AIMetricsCollectorTest {

    private MeterRegistry      registry;
    private AIMetricsCollector collector;

    @BeforeEach
    void setUp() { 
        registry  = new SimpleMeterRegistry(); 
        collector = new AIMetricsCollector(registry); 
    }

    // ── Constructor ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("constructor throws on null MeterRegistry")
    void constructorThrowsOnNull() { 
        assertThatThrownBy(() -> new AIMetricsCollector(null)) 
                .isInstanceOf(NullPointerException.class); 
    }

    // ── recordLlmCall ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("recordLlmCall")
    class RecordLlmCall {

        @Test
        @DisplayName("increments LLM requests counter on success")
        void incrementsCounterOnSuccess() { 
            collector.recordLlmCall("claude-3-sonnet", "suggestion", "tenant-1", 
                    true, 500L, 100, 200, 0.01);

            double count = counter("yappc.ai.llm.requests.total", "status", "success").count(); 
            assertThat(count).isEqualTo(1.0); 
        }

        @Test
        @DisplayName("increments LLM error counter on failure")
        void incrementsErrorCounterOnFailure() { 
            collector.recordLlmCall("claude-3-sonnet", "generation", "tenant-1", 
                    false, 1000L, 50, 0, 0.0);

            double errorCount = counter("yappc.ai.llm.errors.total", "status", "error").count(); 
            assertThat(errorCount).isEqualTo(1.0); 
        }

        @Test
        @DisplayName("does not increment error counter on successful call")
        void doesNotIncrementErrorCounterOnSuccess() { 
            collector.recordLlmCall("gpt-4o", "review", "tenant-1", 
                    true, 300L, 80, 120, 0.005);

            // Error counter should not exist (or be 0) for this model+feature tag set 
            var errorMeter = registry.find("yappc.ai.llm.errors.total")
                    .tag("model", "gpt-4o") 
                    .tag("status", "success") 
                    .counter(); 
            assertThat(errorMeter).isNull(); 
        }

        @Test
        @DisplayName("records latency timer")
        void recordsLatencyTimer() { 
            collector.recordLlmCall("claude-3-sonnet", "suggestion", "tenant-1", 
                    true, 750L, 100, 150, 0.008);

            double totalTime = registry.find("yappc.ai.llm.latency.seconds")
                    .tag("model", "claude-3-sonnet") 
                    .timer() 
                    .totalTime(java.util.concurrent.TimeUnit.MILLISECONDS); 
            assertThat(totalTime).isEqualTo(750.0); 
        }

        @Test
        @DisplayName("records input and output token distributions")
        void recordsTokenDistributions() { 
            collector.recordLlmCall("claude-3-sonnet", "generation", "tenant-2", 
                    true, 200L, 300, 450, 0.02);

            double inputMean = registry.find("yappc.ai.llm.tokens.input")
                    .tag("model", "claude-3-sonnet") 
                    .summary().mean(); 
            double outputMean = registry.find("yappc.ai.llm.tokens.output")
                    .tag("model", "claude-3-sonnet") 
                    .summary().mean(); 

            assertThat(inputMean).isEqualTo(300.0); 
            assertThat(outputMean).isEqualTo(450.0); 
        }

        @Test
        @DisplayName("records cost distribution")
        void recordsCostDistribution() { 
            collector.recordLlmCall("gpt-4o", "review", "tenant-3", 
                    true, 400L, 200, 180, 0.05);

            double costMean = registry.find("yappc.ai.llm.cost.usd")
                    .tag("model", "gpt-4o") 
                    .summary().mean(); 
            assertThat(costMean).isEqualTo(0.05); 
        }
    }

    // ── recordLlmError ────────────────────────────────────────────────────────

    @Test
    @DisplayName("recordLlmError increments error counter with error_type tag")
    void recordLlmErrorIncrementsCounter() { 
        collector.recordLlmError("claude-3-sonnet", "generation", "tenant-1", "TIMEOUT"); 

        double count = registry.find("yappc.ai.llm.errors.total")
                .tag("error_type", "TIMEOUT") 
                .counter() 
                .count(); 
        assertThat(count).isEqualTo(1.0); 
    }

    // ── Cache metrics ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("cache metrics")
    class CacheMetrics {

        @Test
        @DisplayName("recordCacheHit increments cache hits counter")
        void recordCacheHitIncrementsCounter() { 
            collector.recordCacheHit("suggestion", "tenant-1"); 

            double count = registry.find("yappc.ai.cache.hits.total")
                    .tag("feature", "suggestion") 
                    .counter().count(); 
            assertThat(count).isEqualTo(1.0); 
        }

        @Test
        @DisplayName("recordCacheMiss increments cache misses counter")
        void recordCacheMissIncrementsCounter() { 
            collector.recordCacheMiss("generation", "tenant-1"); 

            double count = registry.find("yappc.ai.cache.misses.total")
                    .tag("feature", "generation") 
                    .counter().count(); 
            assertThat(count).isEqualTo(1.0); 
        }
    }

    // ── Fallback metric ───────────────────────────────────────────────────────

    @Test
    @DisplayName("recordFallback increments fallback counter")
    void recordFallbackIncrementsCounter() { 
        collector.recordFallback("claude-3-sonnet", "RATE_LIMIT", "tenant-1"); 

        double count = registry.find("yappc.ai.fallback.total")
                .tag("model", "claude-3-sonnet") 
                .counter().count(); 
        assertThat(count).isEqualTo(1.0); 
    }

    // ── Suggestion feedback ───────────────────────────────────────────────────

    @Nested
    @DisplayName("suggestion feedback")
    class SuggestionFeedback {

        @Test
        @DisplayName("recordSuggestionAccepted increments accepted counter")
        void recordSuggestionAcceptedIncrementsCounter() { 
            collector.recordSuggestionAccepted("suggestion", "tenant-1"); 

            double count = registry.find("yappc.ai.suggestion.accepted.total")
                    .tag("feature", "suggestion") 
                    .counter().count(); 
            assertThat(count).isEqualTo(1.0); 
        }

        @Test
        @DisplayName("recordSuggestionRejected increments rejected counter")
        void recordSuggestionRejectedIncrementsCounter() { 
            collector.recordSuggestionRejected("suggestion", "tenant-1"); 

            double count = registry.find("yappc.ai.suggestion.rejected.total")
                    .tag("feature", "suggestion") 
                    .counter().count(); 
            assertThat(count).isEqualTo(1.0); 
        }
    }

    @Nested
    @DisplayName("quality and drift metrics")
    class QualityMetrics {

        @Test
        @DisplayName("recordQualitySignal records confidence distribution")
        void recordQualitySignalRecordsConfidence() {
            collector.recordQualitySignal("claude-3-sonnet", "code_generation", "tenant-1", 0.82, 0.55);

            double mean = registry.find("yappc.ai.quality.confidence")
                    .tag("model", "claude-3-sonnet")
                    .summary()
                    .mean();
            assertThat(mean).isEqualTo(0.82);
        }

        @Test
        @DisplayName("recordQualitySignal emits low-confidence and drift counters")
        void recordQualitySignalEmitsLowConfidenceAndDrift() {
            collector.recordQualitySignal("claude-3-sonnet", "code_generation", "tenant-1", 0.21, 0.55);

            double lowConfidence = registry.find("yappc.ai.quality.low_confidence.total")
                    .tag("feature", "code_generation")
                    .counter()
                    .count();
            double drift = registry.find("yappc.ai.quality.drift.total")
                    .tag("feature", "code_generation")
                    .counter()
                    .count();

            assertThat(lowConfidence).isEqualTo(1.0);
            assertThat(drift).isEqualTo(1.0);
        }
    }

    @Test
    @DisplayName("recordHallucinationBlocked increments blocked counter")
    void recordHallucinationBlockedIncrementsCounter() {
        collector.recordHallucinationBlocked("gpt-4o", "reasoning", "tenant-1", "hallucination_marker");

        double count = registry.find("yappc.ai.quality.hallucination.blocked.total")
                .tag("reason", "hallucination_marker")
                .counter()
                .count();
        assertThat(count).isEqualTo(1.0);
    }

    // ── null safety ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("null model tag does not crash")
    void nullModelTagDoesNotCrash() { 
        // Should complete without exception (safe() converts null to empty string) 
        collector.recordLlmCall(null, "generation", "tenant-1", true, 100L, 50, 60, 0.001); 
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Counter counter(String name, String tagKey, String tagValue) { 
        Counter c = registry.find(name).tag(tagKey, tagValue).counter(); 
        assertThat(c).as("Counter '%s' with tag %s=%s must be registered", name, tagKey, tagValue) 
                .isNotNull(); 
        return c;
    }
}
