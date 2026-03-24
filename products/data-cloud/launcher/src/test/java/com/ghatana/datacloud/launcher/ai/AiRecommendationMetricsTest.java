/*
 * Copyright (c) 2026 Ghatana Inc.
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
 * @doc.purpose Unit tests for AI recommendation quality metrics (DC-E3)
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
        void noopIsNotNull() {
            assertThat(AiRecommendationMetrics.NOOP).isNotNull();
        }

        @Test
        @DisplayName("NOOP.recordRecommendation does not throw")
        void noopRecordRecommendation_doesNotThrow() {
            assertThatCode(() ->
                AiRecommendationMetrics.NOOP.recordRecommendation(
                    AiRecommendationMetrics.TYPE_ENTITY_SUGGEST, "tenant-1", 0.9, false, 42L))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("NOOP.recordError does not throw with null cause")
        void noopRecordError_doesNotThrow() {
            assertThatCode(() ->
                AiRecommendationMetrics.NOOP.recordError(
                    AiRecommendationMetrics.TYPE_ANALYTICS_SUGGEST, "tenant-1", null))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("NOOP.recordFeedback does not throw")
        void noopRecordFeedback_doesNotThrow() {
            assertThatCode(() ->
                AiRecommendationMetrics.NOOP.recordFeedback(
                    AiRecommendationMetrics.TYPE_VOICE_INTENT, "tenant-1", true))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("NOOP.getMeanConfidence returns NaN (no data, null metrics)")
        void noopGetMeanConfidence_isNaN() {
            assertThat(AiRecommendationMetrics.NOOP.getMeanConfidence(
                AiRecommendationMetrics.TYPE_ENTITY_SUGGEST)).isNaN();
        }

        @Test
        @DisplayName("NOOP.getRequestCount returns 0 (null metrics path exits early)")
        void noopGetRequestCount_isZero() {
            assertThat(AiRecommendationMetrics.NOOP.getRequestCount(
                AiRecommendationMetrics.TYPE_PIPELINE_HINT)).isZero();
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
        void requestCountIncrements() {
            AiRecommendationMetrics m = withNoopCollector();
            m.recordRecommendation(AiRecommendationMetrics.TYPE_ENTITY_SUGGEST, "t1", 0.8, false, 10L);
            m.recordRecommendation(AiRecommendationMetrics.TYPE_ENTITY_SUGGEST, "t1", 0.6, true,  20L);
            assertThat(m.getRequestCount(AiRecommendationMetrics.TYPE_ENTITY_SUGGEST)).isEqualTo(2);
        }

        @Test
        @DisplayName("different types have independent counters")
        void differentTypesHaveIndependentCounters() {
            AiRecommendationMetrics m = withNoopCollector();
            m.recordRecommendation(AiRecommendationMetrics.TYPE_ENTITY_SUGGEST,    "t1", 0.7, false, 5L);
            m.recordRecommendation(AiRecommendationMetrics.TYPE_ANALYTICS_SUGGEST, "t1", 0.8, false, 8L);
            m.recordRecommendation(AiRecommendationMetrics.TYPE_ANALYTICS_SUGGEST, "t1", 0.6, true,  12L);

            assertThat(m.getRequestCount(AiRecommendationMetrics.TYPE_ENTITY_SUGGEST)).isEqualTo(1);
            assertThat(m.getRequestCount(AiRecommendationMetrics.TYPE_ANALYTICS_SUGGEST)).isEqualTo(2);
            assertThat(m.getRequestCount(AiRecommendationMetrics.TYPE_PIPELINE_HINT)).isZero();
        }

        @Test
        @DisplayName("getMeanConfidence returns mean of recorded values")
        void meanConfidenceIsAccurate() {
            AiRecommendationMetrics m = withNoopCollector();
            m.recordRecommendation(AiRecommendationMetrics.TYPE_BRAIN_EXPLAIN, "t1", 0.80, false, 5L);
            m.recordRecommendation(AiRecommendationMetrics.TYPE_BRAIN_EXPLAIN, "t1", 0.60, false, 7L);
            m.recordRecommendation(AiRecommendationMetrics.TYPE_BRAIN_EXPLAIN, "t1", 0.40, true,  9L);
            // Expected mean = (0.80 + 0.60 + 0.40) / 3 = 0.60
            assertThat(m.getMeanConfidence(AiRecommendationMetrics.TYPE_BRAIN_EXPLAIN))
                .isCloseTo(0.60, within(0.001));
        }

        @Test
        @DisplayName("getMeanConfidence is NaN before any observations")
        void meanConfidenceIsNaNBeforeAnyData() {
            AiRecommendationMetrics m = withNoopCollector();
            assertThat(m.getMeanConfidence(AiRecommendationMetrics.TYPE_VOICE_INTENT)).isNaN();
        }

        @Test
        @DisplayName("null type is silently ignored (no counter increment)")
        void nullType_isIgnored() {
            AiRecommendationMetrics m = withNoopCollector();
            assertThatCode(() -> m.recordRecommendation(null, "t1", 0.5, false, 10L))
                .doesNotThrowAnyException();
            // Count must remain 0 for all known types
            assertThat(m.getRequestCount(AiRecommendationMetrics.TYPE_ENTITY_SUGGEST)).isZero();
        }

        @Test
        @DisplayName("blank type is silently ignored")
        void blankType_isIgnored() {
            AiRecommendationMetrics m = withNoopCollector();
            assertThatCode(() -> m.recordRecommendation("  ", "t1", 0.5, false, 10L))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("single observation: mean equals that observation")
        void singleObservation_meanEqualsThatValue() {
            AiRecommendationMetrics m = withNoopCollector();
            m.recordRecommendation(AiRecommendationMetrics.TYPE_PIPELINE_HINT, "t1", 0.77, false, 15L);
            assertThat(m.getMeanConfidence(AiRecommendationMetrics.TYPE_PIPELINE_HINT))
                .isCloseTo(0.77, within(0.001));
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
        void thumbsUp_doesNotThrow() {
            assertThatCode(() ->
                withNoopCollector().recordFeedback(
                    AiRecommendationMetrics.TYPE_ENTITY_SUGGEST, "t1", true))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("thumbs-down feedback does not throw")
        void thumbsDown_doesNotThrow() {
            assertThatCode(() ->
                withNoopCollector().recordFeedback(
                    AiRecommendationMetrics.TYPE_ANALYTICS_SUGGEST, "t1", false))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("null type does not throw")
        void nullType_doesNotThrow() {
            assertThatCode(() ->
                withNoopCollector().recordFeedback(null, "t1", true))
                .doesNotThrowAnyException();
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
        void recordsErrorWithoutThrowing() {
            AiRecommendationMetrics m = withNoopCollector();
            Exception cause = new RuntimeException("LLM timeout");
            assertThatCode(() -> m.recordError(
                AiRecommendationMetrics.TYPE_BRAIN_EXPLAIN, "t1", cause))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("null cause does not throw")
        void nullCause_doesNotThrow() {
            assertThatCode(() ->
                withNoopCollector().recordError(
                    AiRecommendationMetrics.TYPE_PIPELINE_HINT, "t1", null))
                .doesNotThrowAnyException();
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
        void nullTenant_doesNotThrow() {
            AiRecommendationMetrics m = withNoopCollector();
            assertThatCode(() -> m.recordRecommendation(
                AiRecommendationMetrics.TYPE_ENTITY_SUGGEST, null, 0.8, false, 5L))
                .doesNotThrowAnyException();
            assertThat(m.getRequestCount(AiRecommendationMetrics.TYPE_ENTITY_SUGGEST)).isEqualTo(1);
        }

        @Test
        @DisplayName("very long tenant ID (> 64 chars) does not throw")
        void longTenantId_doesNotThrow() {
            String longTenant = "t".repeat(200);
            AiRecommendationMetrics m = withNoopCollector();
            assertThatCode(() -> m.recordRecommendation(
                AiRecommendationMetrics.TYPE_ANALYTICS_SUGGEST, longTenant, 0.5, true, 20L))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("tenant with special characters does not throw")
        void tenantWithSpecialChars_doesNotThrow() {
            AiRecommendationMetrics m = withNoopCollector();
            assertThatCode(() -> m.recordRecommendation(
                AiRecommendationMetrics.TYPE_VOICE_INTENT, "tenant!@#$%", 0.7, false, 8L))
                .doesNotThrowAnyException();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // All 5 TYPE_* constants
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("TYPE_* constants")
    class TypeConstantTests {

        @ParameterizedTest
        @ValueSource(strings = {
            AiRecommendationMetrics.TYPE_ENTITY_SUGGEST,
            AiRecommendationMetrics.TYPE_ANALYTICS_SUGGEST,
            AiRecommendationMetrics.TYPE_PIPELINE_HINT,
            AiRecommendationMetrics.TYPE_BRAIN_EXPLAIN,
            AiRecommendationMetrics.TYPE_VOICE_INTENT
        })
        @DisplayName("each TYPE_* constant is accepted by recordRecommendation")
        void eachTypeConstant_isAccepted(String type) {
            AiRecommendationMetrics m = withNoopCollector();
            assertThatCode(() -> m.recordRecommendation(type, "tenant-1", 0.75, false, 10L))
                .doesNotThrowAnyException();
            assertThat(m.getRequestCount(type)).isEqualTo(1);
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
        void concurrentCalls_produceConsistentCount() throws InterruptedException {
            AiRecommendationMetrics m = withNoopCollector();
            int threads = 10;
            int callsPerThread = 50;
            CountDownLatch ready = new CountDownLatch(threads);
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done  = new CountDownLatch(threads);

            ExecutorService pool = Executors.newFixedThreadPool(threads);
            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    ready.countDown();
                    try { start.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                    for (int j = 0; j < callsPerThread; j++) {
                        m.recordRecommendation(
                            AiRecommendationMetrics.TYPE_ENTITY_SUGGEST, "tenant-x", 0.7, false, 5L);
                    }
                    done.countDown();
                });
            }

            ready.await();
            start.countDown();
            done.await();
            pool.shutdown();

            assertThat(m.getRequestCount(AiRecommendationMetrics.TYPE_ENTITY_SUGGEST))
                .isEqualTo((long) threads * callsPerThread);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helper
    // ─────────────────────────────────────────────────────────────────────────

    private static AiRecommendationMetrics withNoopCollector() {
        return new AiRecommendationMetrics(new NoopMetricsCollector());
    }
}
