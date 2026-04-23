/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 */
package com.ghatana.datacloud.launcher.ai;

import com.ghatana.platform.observability.NoopMetricsCollector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.within;

/**
 * Unit tests for {@link AiRecommendationMetrics}.
 *
 * <p>All tests are synchronous — this class contains no ActiveJ Promises and
 * does not require {@code EventloopTestBase}.
 *
 * @doc.type class
 * @doc.purpose Unit tests for AI recommendation quality metrics (DC-E3) // GH-90000
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("AiRecommendationMetrics")
class AiRecommendationMetricsTest {

    // ─────────────────────────────────────────────────────────────────────────
    // NOOP sentinel
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("NOOP singleton")
    class NoopTests {

        @Test
        @DisplayName("NOOP is not null")
        void noopIsNotNull() { // GH-90000
            assertThat(AiRecommendationMetrics.NOOP).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("NOOP.recordRecommendation does not throw")
        void noopRecordRecommendation_doesNotThrow() { // GH-90000
            assertThatCode(() -> // GH-90000
                AiRecommendationMetrics.NOOP.recordRecommendation( // GH-90000
                    AiRecommendationMetrics.TYPE_ENTITY_SUGGEST, "tenant-1", 0.9, false, 42L))
                .doesNotThrowAnyException(); // GH-90000
        }

        @Test
        @DisplayName("NOOP.recordError does not throw with null cause")
        void noopRecordError_doesNotThrow() { // GH-90000
            assertThatCode(() -> // GH-90000
                AiRecommendationMetrics.NOOP.recordError( // GH-90000
                    AiRecommendationMetrics.TYPE_ANALYTICS_SUGGEST, "tenant-1", null))
                .doesNotThrowAnyException(); // GH-90000
        }

        @Test
        @DisplayName("NOOP.recordFeedback does not throw")
        void noopRecordFeedback_doesNotThrow() { // GH-90000
            assertThatCode(() -> // GH-90000
                AiRecommendationMetrics.NOOP.recordFeedback( // GH-90000
                    AiRecommendationMetrics.TYPE_VOICE_INTENT, "tenant-1", true))
                .doesNotThrowAnyException(); // GH-90000
        }

        @Test
        @DisplayName("NOOP.getMeanConfidence returns NaN (no data, null metrics)")
        void noopGetMeanConfidence_isNaN() { // GH-90000
            assertThat(AiRecommendationMetrics.NOOP.getMeanConfidence( // GH-90000
                AiRecommendationMetrics.TYPE_ENTITY_SUGGEST)).isNaN(); // GH-90000
        }

        @Test
        @DisplayName("NOOP.getRequestCount returns 0 (null metrics path exits early)")
        void noopGetRequestCount_isZero() { // GH-90000
            assertThat(AiRecommendationMetrics.NOOP.getRequestCount( // GH-90000
                AiRecommendationMetrics.TYPE_PIPELINE_HINT)).isZero(); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // recordRecommendation — in-process counters
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("recordRecommendation – in-process counters")
    class RecordRecommendationTests {

        @Test
        @DisplayName("getRequestCount increments on each call")
        void requestCountIncrements() { // GH-90000
            AiRecommendationMetrics m = withNoopCollector(); // GH-90000
            m.recordRecommendation(AiRecommendationMetrics.TYPE_ENTITY_SUGGEST, "t1", 0.8, false, 10L); // GH-90000
            m.recordRecommendation(AiRecommendationMetrics.TYPE_ENTITY_SUGGEST, "t1", 0.6, true,  20L); // GH-90000
            assertThat(m.getRequestCount(AiRecommendationMetrics.TYPE_ENTITY_SUGGEST)).isEqualTo(2); // GH-90000
        }

        @Test
        @DisplayName("different types have independent counters")
        void differentTypesHaveIndependentCounters() { // GH-90000
            AiRecommendationMetrics m = withNoopCollector(); // GH-90000
            m.recordRecommendation(AiRecommendationMetrics.TYPE_ENTITY_SUGGEST,    "t1", 0.7, false, 5L); // GH-90000
            m.recordRecommendation(AiRecommendationMetrics.TYPE_ANALYTICS_SUGGEST, "t1", 0.8, false, 8L); // GH-90000
            m.recordRecommendation(AiRecommendationMetrics.TYPE_ANALYTICS_SUGGEST, "t1", 0.6, true,  12L); // GH-90000

            assertThat(m.getRequestCount(AiRecommendationMetrics.TYPE_ENTITY_SUGGEST)).isEqualTo(1); // GH-90000
            assertThat(m.getRequestCount(AiRecommendationMetrics.TYPE_ANALYTICS_SUGGEST)).isEqualTo(2); // GH-90000
            assertThat(m.getRequestCount(AiRecommendationMetrics.TYPE_PIPELINE_HINT)).isZero(); // GH-90000
        }

        @Test
        @DisplayName("getMeanConfidence returns mean of recorded values")
        void meanConfidenceIsAccurate() { // GH-90000
            AiRecommendationMetrics m = withNoopCollector(); // GH-90000
            m.recordRecommendation(AiRecommendationMetrics.TYPE_BRAIN_EXPLAIN, "t1", 0.80, false, 5L); // GH-90000
            m.recordRecommendation(AiRecommendationMetrics.TYPE_BRAIN_EXPLAIN, "t1", 0.60, false, 7L); // GH-90000
            m.recordRecommendation(AiRecommendationMetrics.TYPE_BRAIN_EXPLAIN, "t1", 0.40, true,  9L); // GH-90000
            // Expected mean = (0.80 + 0.60 + 0.40) / 3 = 0.60 // GH-90000
            assertThat(m.getMeanConfidence(AiRecommendationMetrics.TYPE_BRAIN_EXPLAIN)) // GH-90000
                .isCloseTo(0.60, within(0.001)); // GH-90000
        }

        @Test
        @DisplayName("getMeanConfidence is NaN before any observations")
        void meanConfidenceIsNaNBeforeAnyData() { // GH-90000
            AiRecommendationMetrics m = withNoopCollector(); // GH-90000
            assertThat(m.getMeanConfidence(AiRecommendationMetrics.TYPE_VOICE_INTENT)).isNaN(); // GH-90000
        }

        @Test
        @DisplayName("null type is silently ignored (no counter increment)")
        void nullType_isIgnored() { // GH-90000
            AiRecommendationMetrics m = withNoopCollector(); // GH-90000
            assertThatCode(() -> m.recordRecommendation(null, "t1", 0.5, false, 10L)) // GH-90000
                .doesNotThrowAnyException(); // GH-90000
            // Count must remain 0 for all known types
            assertThat(m.getRequestCount(AiRecommendationMetrics.TYPE_ENTITY_SUGGEST)).isZero(); // GH-90000
        }

        @Test
        @DisplayName("blank type is silently ignored")
        void blankType_isIgnored() { // GH-90000
            AiRecommendationMetrics m = withNoopCollector(); // GH-90000
            assertThatCode(() -> m.recordRecommendation("  ", "t1", 0.5, false, 10L)) // GH-90000
                .doesNotThrowAnyException(); // GH-90000
        }

        @Test
        @DisplayName("single observation: mean equals that observation")
        void singleObservation_meanEqualsThatValue() { // GH-90000
            AiRecommendationMetrics m = withNoopCollector(); // GH-90000
            m.recordRecommendation(AiRecommendationMetrics.TYPE_PIPELINE_HINT, "t1", 0.77, false, 15L); // GH-90000
            assertThat(m.getMeanConfidence(AiRecommendationMetrics.TYPE_PIPELINE_HINT)) // GH-90000
                .isCloseTo(0.77, within(0.001)); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // recordFeedback — does not throw
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("recordFeedback")
    class RecordFeedbackTests {

        @Test
        @DisplayName("thumbs-up feedback does not throw")
        void thumbsUp_doesNotThrow() { // GH-90000
            assertThatCode(() -> // GH-90000
                withNoopCollector().recordFeedback( // GH-90000
                    AiRecommendationMetrics.TYPE_ENTITY_SUGGEST, "t1", true))
                .doesNotThrowAnyException(); // GH-90000
        }

        @Test
        @DisplayName("thumbs-down feedback does not throw")
        void thumbsDown_doesNotThrow() { // GH-90000
            assertThatCode(() -> // GH-90000
                withNoopCollector().recordFeedback( // GH-90000
                    AiRecommendationMetrics.TYPE_ANALYTICS_SUGGEST, "t1", false))
                .doesNotThrowAnyException(); // GH-90000
        }

        @Test
        @DisplayName("null type does not throw")
        void nullType_doesNotThrow() { // GH-90000
            assertThatCode(() -> // GH-90000
                withNoopCollector().recordFeedback(null, "t1", true)) // GH-90000
                .doesNotThrowAnyException(); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // recordError — does not throw
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("recordError")
    class RecordErrorTests {

        @Test
        @DisplayName("records error without throwing")
        void recordsErrorWithoutThrowing() { // GH-90000
            AiRecommendationMetrics m = withNoopCollector(); // GH-90000
            Exception cause = new RuntimeException("LLM timeout");
            assertThatCode(() -> m.recordError( // GH-90000
                AiRecommendationMetrics.TYPE_BRAIN_EXPLAIN, "t1", cause))
                .doesNotThrowAnyException(); // GH-90000
        }

        @Test
        @DisplayName("null cause does not throw")
        void nullCause_doesNotThrow() { // GH-90000
            assertThatCode(() -> // GH-90000
                withNoopCollector().recordError( // GH-90000
                    AiRecommendationMetrics.TYPE_PIPELINE_HINT, "t1", null))
                .doesNotThrowAnyException(); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Tenant tag sanitisation
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Tenant tag sanitisation")
    class TenantTagTests {

        @Test
        @DisplayName("null tenant does not throw and still records")
        void nullTenant_doesNotThrow() { // GH-90000
            AiRecommendationMetrics m = withNoopCollector(); // GH-90000
            assertThatCode(() -> m.recordRecommendation( // GH-90000
                AiRecommendationMetrics.TYPE_ENTITY_SUGGEST, null, 0.8, false, 5L))
                .doesNotThrowAnyException(); // GH-90000
            assertThat(m.getRequestCount(AiRecommendationMetrics.TYPE_ENTITY_SUGGEST)).isEqualTo(1); // GH-90000
        }

        @Test
        @DisplayName("very long tenant ID (> 64 chars) does not throw")
        void longTenantId_doesNotThrow() { // GH-90000
            String longTenant = "t".repeat(200); // GH-90000
            AiRecommendationMetrics m = withNoopCollector(); // GH-90000
            assertThatCode(() -> m.recordRecommendation( // GH-90000
                AiRecommendationMetrics.TYPE_ANALYTICS_SUGGEST, longTenant, 0.5, true, 20L))
                .doesNotThrowAnyException(); // GH-90000
        }

        @Test
        @DisplayName("tenant with special characters does not throw")
        void tenantWithSpecialChars_doesNotThrow() { // GH-90000
            AiRecommendationMetrics m = withNoopCollector(); // GH-90000
            assertThatCode(() -> m.recordRecommendation( // GH-90000
                AiRecommendationMetrics.TYPE_VOICE_INTENT, "tenant!@#$%", 0.7, false, 8L))
                .doesNotThrowAnyException(); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // All 5 TYPE_* constants
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("TYPE_* constants")
    class TypeConstantTests {

        @ParameterizedTest
        @ValueSource(strings = { // GH-90000
            AiRecommendationMetrics.TYPE_ENTITY_SUGGEST,
            AiRecommendationMetrics.TYPE_ANALYTICS_SUGGEST,
            AiRecommendationMetrics.TYPE_PIPELINE_HINT,
            AiRecommendationMetrics.TYPE_BRAIN_EXPLAIN,
            AiRecommendationMetrics.TYPE_VOICE_INTENT
        })
        @DisplayName("each TYPE_* constant is accepted by recordRecommendation")
        void eachTypeConstant_isAccepted(String type) { // GH-90000
            AiRecommendationMetrics m = withNoopCollector(); // GH-90000
            assertThatCode(() -> m.recordRecommendation(type, "tenant-1", 0.75, false, 10L)) // GH-90000
                .doesNotThrowAnyException(); // GH-90000
            assertThat(m.getRequestCount(type)).isEqualTo(1); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Thread-safety
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Thread-safety")
    class ThreadSafetyTests {

        @Test
        @DisplayName("concurrent recordRecommendation calls produce consistent count")
        void concurrentCalls_produceConsistentCount() throws InterruptedException { // GH-90000
            AiRecommendationMetrics m = withNoopCollector(); // GH-90000
            int threads = 10;
            int callsPerThread = 50;
            CountDownLatch ready = new CountDownLatch(threads); // GH-90000
            CountDownLatch start = new CountDownLatch(1); // GH-90000
            CountDownLatch done  = new CountDownLatch(threads); // GH-90000

            ExecutorService pool = Executors.newFixedThreadPool(threads); // GH-90000
            for (int i = 0; i < threads; i++) { // GH-90000
                pool.submit(() -> { // GH-90000
                    ready.countDown(); // GH-90000
                    try { start.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } // GH-90000
                    for (int j = 0; j < callsPerThread; j++) { // GH-90000
                        m.recordRecommendation( // GH-90000
                            AiRecommendationMetrics.TYPE_ENTITY_SUGGEST, "tenant-x", 0.7, false, 5L);
                    }
                    done.countDown(); // GH-90000
                });
            }

            ready.await(); // GH-90000
            start.countDown(); // GH-90000
            done.await(); // GH-90000
            pool.shutdown(); // GH-90000

            assertThat(m.getRequestCount(AiRecommendationMetrics.TYPE_ENTITY_SUGGEST)) // GH-90000
                .isEqualTo((long) threads * callsPerThread); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helper
    // ─────────────────────────────────────────────────────────────────────────

    private static AiRecommendationMetrics withNoopCollector() { // GH-90000
        return new AiRecommendationMetrics(new NoopMetricsCollector()); // GH-90000
    }
}
