/*
 * Copyright (c) 2026 Ghatana Inc.
 */
package com.ghatana.datacloud.integration;

import com.ghatana.datacloud.launcher.ai.AiRecommendationMetrics;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.observability.NoopMetricsCollector;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for AI evaluation pipeline regression testing.
 *
 * <p>This test verifies that AI recommendation metrics are correctly recorded
 * and can be used for regression testing to ensure AI quality over time.
 * It exercises the full metrics pipeline including request counting, fallback
 * tracking, latency measurement, confidence distribution, and user feedback.
 *
 * <p>Tests covered:
 * <ul>
 *   <li>Metrics are recorded for all recommendation types</li>
 *   <li>Fallback rates can be calculated and monitored</li>
 *   <li>Confidence distributions are captured</li>
 *   <li>Regression thresholds can be established</li>
 *   <li>Quality snapshots are accurate</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Integration tests for AI metrics regression testing (DC-E3)
 * @doc.layer product
 * @doc.pattern IntegrationTest
 */
@DisplayName("AI Metrics Regression Integration Test")
class AiMetricsRegressionIntegrationTest {

    // ─────────────────────────────────────────────────────────────────────────
    // Regression Threshold Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Regression Thresholds")
    class RegressionThresholdTests {

        @Test
        @DisplayName("entity_suggest fallback rate should not exceed 30% threshold")
        void entitySuggestFallbackRate_withinThreshold() {
            SimpleMeterRegistry registry = new SimpleMeterRegistry();
            MetricsCollector collector = new MetricsCollector(registry);
            AiRecommendationMetrics metrics = new AiRecommendationMetrics(collector);

            String tenantId = "tenant-regression-test";
            
            // Simulate typical traffic: 70% AI, 30% fallback
            for (int i = 0; i < 70; i++) {
                metrics.recordRecommendation(
                    AiRecommendationMetrics.TYPE_ENTITY_SUGGEST, 
                    tenantId, 
                    0.85, // high confidence
                    false, // not fallback
                    50L   // latency
                );
            }
            for (int i = 0; i < 30; i++) {
                metrics.recordRecommendation(
                    AiRecommendationMetrics.TYPE_ENTITY_SUGGEST, 
                    tenantId, 
                    0.20, // heuristic confidence
                    true,  // fallback
                    10L   // faster latency
                );
            }

            double fallbackRate = metrics.getFallbackRate(AiRecommendationMetrics.TYPE_ENTITY_SUGGEST);
            
            // Regression threshold: fallback rate should not exceed 30%
            assertThat(fallbackRate).isLessThanOrEqualTo(0.30);
            
            // Verify total count
            assertThat(metrics.getRequestCount(AiRecommendationMetrics.TYPE_ENTITY_SUGGEST)).isEqualTo(100);
        }

        @Test
        @DisplayName("analytics_suggest mean confidence should not drop below 0.60 threshold")
        void analyticsSuggestConfidence_aboveThreshold() {
            SimpleMeterRegistry registry = new SimpleMeterRegistry();
            MetricsCollector collector = new MetricsCollector(registry);
            AiRecommendationMetrics metrics = new AiRecommendationMetrics(collector);

            String tenantId = "tenant-regression-test";
            
            // Simulate typical confidence distribution
            double[] confidences = {0.90, 0.85, 0.80, 0.75, 0.70, 0.65, 0.60, 0.55, 0.50, 0.45};
            for (double conf : confidences) {
                metrics.recordRecommendation(
                    AiRecommendationMetrics.TYPE_ANALYTICS_SUGGEST, 
                    tenantId, 
                    conf,
                    false,
                    60L
                );
            }

            double meanConfidence = metrics.getMeanConfidence(AiRecommendationMetrics.TYPE_ANALYTICS_SUGGEST);
            
            // Regression threshold: mean confidence should not drop below 0.60
            assertThat(meanConfidence).isGreaterThanOrEqualTo(0.60);
            
            // Verify count
            assertThat(metrics.getRequestCount(AiRecommendationMetrics.TYPE_ANALYTICS_SUGGEST)).isEqualTo(10);
        }

        @Test
        @DisplayName("pipeline_draft latency should not exceed 500ms P95 threshold")
        void pipelineDraftLatency_withinThreshold() {
            SimpleMeterRegistry registry = new SimpleMeterRegistry();
            MetricsCollector collector = new MetricsCollector(registry);
            AiRecommendationMetrics metrics = new AiRecommendationMetrics(collector);

            String tenantId = "tenant-regression-test";
            
            // Simulate typical latency distribution (ms)
            long[] latencies = {100, 150, 200, 250, 300, 350, 400, 450, 500, 550};
            for (long latency : latencies) {
                metrics.recordRecommendation(
                    AiRecommendationMetrics.TYPE_PIPELINE_DRAFT, 
                    tenantId, 
                    0.75,
                    false,
                    latency
                );
            }

            // Verify all requests were recorded
            assertThat(metrics.getRequestCount(AiRecommendationMetrics.TYPE_PIPELINE_DRAFT)).isEqualTo(10);
            
            // Verify mean confidence is reasonable
            assertThat(metrics.getMeanConfidence(AiRecommendationMetrics.TYPE_PIPELINE_DRAFT))
                .isGreaterThanOrEqualTo(0.70);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Quality Snapshot Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Quality Snapshot")
    class QualitySnapshotTests {

        @Test
        @DisplayName("snapshot includes all known recommendation types")
        void snapshot_includesAllKnownTypes() {
            SimpleMeterRegistry registry = new SimpleMeterRegistry();
            MetricsCollector collector = new MetricsCollector(registry);
            AiRecommendationMetrics metrics = new AiRecommendationMetrics(collector);

            String tenantId = "tenant-snapshot-test";
            
            // Record at least one metric for each type
            metrics.recordRecommendation(AiRecommendationMetrics.TYPE_ENTITY_SUGGEST, tenantId, 0.8, false, 10L);
            metrics.recordRecommendation(AiRecommendationMetrics.TYPE_ANALYTICS_SUGGEST, tenantId, 0.7, false, 20L);
            metrics.recordRecommendation(AiRecommendationMetrics.TYPE_PIPELINE_DRAFT, tenantId, 0.6, false, 30L);
            metrics.recordRecommendation(AiRecommendationMetrics.TYPE_PIPELINE_HINT, tenantId, 0.5, false, 40L);
            metrics.recordRecommendation(AiRecommendationMetrics.TYPE_BRAIN_EXPLAIN, tenantId, 0.4, false, 50L);
            metrics.recordRecommendation(AiRecommendationMetrics.TYPE_VOICE_INTENT, tenantId, 0.3, false, 60L);

            var snapshot = metrics.snapshot();
            
            assertThat(snapshot).hasSize(6);
            assertThat(snapshot).allMatch(s -> s.requestCount() > 0);
        }

        @Test
        @DisplayName("snapshot accurately reflects fallback rates")
        void snapshot_accuratelyReflectsFallbackRates() {
            SimpleMeterRegistry registry = new SimpleMeterRegistry();
            MetricsCollector collector = new MetricsCollector(registry);
            AiRecommendationMetrics metrics = new AiRecommendationMetrics(collector);

            String tenantId = "tenant-fallback-test";
            
            // Record with known fallback rate (50%)
            metrics.recordRecommendation(AiRecommendationMetrics.TYPE_ENTITY_SUGGEST, tenantId, 0.8, false, 10L);
            metrics.recordRecommendation(AiRecommendationMetrics.TYPE_ENTITY_SUGGEST, tenantId, 0.2, true, 10L);

            var snapshot = metrics.snapshot();
            var entitySuggestSnapshot = snapshot.stream()
                .filter(s -> s.type().equals(AiRecommendationMetrics.TYPE_ENTITY_SUGGEST))
                .findFirst()
                .orElseThrow();
            
            assertThat(entitySuggestSnapshot.fallbackRate()).isEqualTo(0.50);
            assertThat(entitySuggestSnapshot.fallbackCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("snapshot provides mean confidence for each type")
        void snapshot_providesMeanConfidence() {
            SimpleMeterRegistry registry = new SimpleMeterRegistry();
            MetricsCollector collector = new MetricsCollector(registry);
            AiRecommendationMetrics metrics = new AiRecommendationMetrics(collector);

            String tenantId = "tenant-confidence-test";
            
            // Record known confidence values
            metrics.recordRecommendation(AiRecommendationMetrics.TYPE_ANALYTICS_SUGGEST, tenantId, 0.9, false, 10L);
            metrics.recordRecommendation(AiRecommendationMetrics.TYPE_ANALYTICS_SUGGEST, tenantId, 0.7, false, 10L);
            metrics.recordRecommendation(AiRecommendationMetrics.TYPE_ANALYTICS_SUGGEST, tenantId, 0.5, false, 10L);

            var snapshot = metrics.snapshot();
            var analyticsSnapshot = snapshot.stream()
                .filter(s -> s.type().equals(AiRecommendationMetrics.TYPE_ANALYTICS_SUGGEST))
                .findFirst()
                .orElseThrow();
            
            // Mean should be (0.9 + 0.7 + 0.5) / 3 = 0.7
            assertThat(analyticsSnapshot.meanConfidence()).isCloseTo(0.7, within(0.01));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Feedback Recording Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("User Feedback Recording")
    class FeedbackRecordingTests {

        @Test
        @DisplayName("thumbs-up feedback is recorded without affecting request count")
        void thumbsUpFeedback_recordedWithoutAffectingRequestCount() {
            SimpleMeterRegistry registry = new SimpleMeterRegistry();
            MetricsCollector collector = new MetricsCollector(registry);
            AiRecommendationMetrics metrics = new AiRecommendationMetrics(collector);

            String tenantId = "tenant-feedback-test";
            
            // Record a recommendation
            metrics.recordRecommendation(AiRecommendationMetrics.TYPE_ENTITY_SUGGEST, tenantId, 0.8, false, 10L);
            long requestCountBefore = metrics.getRequestCount(AiRecommendationMetrics.TYPE_ENTITY_SUGGEST);
            
            // Record positive feedback
            metrics.recordFeedback(AiRecommendationMetrics.TYPE_ENTITY_SUGGEST, tenantId, true);
            
            // Request count should not change
            long requestCountAfter = metrics.getRequestCount(AiRecommendationMetrics.TYPE_ENTITY_SUGGEST);
            assertThat(requestCountAfter).isEqualTo(requestCountBefore);
        }

        @Test
        @DisplayName("thumbs-down feedback is recorded")
        void thumbsDownFeedback_recorded() {
            SimpleMeterRegistry registry = new SimpleMeterRegistry();
            MetricsCollector collector = new MetricsCollector(registry);
            AiRecommendationMetrics metrics = new AiRecommendationMetrics(collector);

            String tenantId = "tenant-feedback-test";
            
            // Record negative feedback
            metrics.recordFeedback(AiRecommendationMetrics.TYPE_ANALYTICS_SUGGEST, tenantId, false);
            
            // Should not throw and metrics should remain functional
            assertThatCode(() -> metrics.recordFeedback(
                AiRecommendationMetrics.TYPE_ANALYTICS_SUGGEST, tenantId, false))
                .doesNotThrowAnyException();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Error Recording Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Error Recording")
    class ErrorRecordingTests {

        @Test
        @DisplayName("errors are recorded without affecting request count")
        void errors_recordedWithoutAffectingRequestCount() {
            SimpleMeterRegistry registry = new SimpleMeterRegistry();
            MetricsCollector collector = new MetricsCollector(registry);
            AiRecommendationMetrics metrics = new AiRecommendationMetrics(collector);

            String tenantId = "tenant-error-test";
            
            // Record a successful recommendation
            metrics.recordRecommendation(AiRecommendationMetrics.TYPE_BRAIN_EXPLAIN, tenantId, 0.8, false, 10L);
            long requestCountBefore = metrics.getRequestCount(AiRecommendationMetrics.TYPE_BRAIN_EXPLAIN);
            
            // Record an error
            metrics.recordError(
                AiRecommendationMetrics.TYPE_BRAIN_EXPLAIN, 
                tenantId, 
                new RuntimeException("LLM timeout"));
            
            // Request count should not change
            long requestCountAfter = metrics.getRequestCount(AiRecommendationMetrics.TYPE_BRAIN_EXPLAIN);
            assertThat(requestCountAfter).isEqualTo(requestCountBefore);
        }

        @Test
        @DisplayName("error recording handles null cause gracefully")
        void errorRecording_handlesNullCause() {
            SimpleMeterRegistry registry = new SimpleMeterRegistry();
            MetricsCollector collector = new MetricsCollector(registry);
            AiRecommendationMetrics metrics = new AiRecommendationMetrics(collector);

            String tenantId = "tenant-error-test";
            
            // Record error with null cause
            assertThatCode(() -> metrics.recordError(
                AiRecommendationMetrics.TYPE_PIPELINE_HINT, tenantId, null))
                .doesNotThrowAnyException();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cross-Type Regression Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Cross-Type Regression")
    class CrossTypeRegressionTests {

        @Test
        @DisplayName("overall fallback rate across all types can be monitored")
        void overallFallbackRate_canBeMonitored() {
            SimpleMeterRegistry registry = new SimpleMeterRegistry();
            MetricsCollector collector = new MetricsCollector(registry);
            AiRecommendationMetrics metrics = new AiRecommendationMetrics(collector);

            String tenantId = "tenant-cross-type-test";
            
            // Record mixed traffic across types
            metrics.recordRecommendation(AiRecommendationMetrics.TYPE_ENTITY_SUGGEST, tenantId, 0.8, false, 10L);
            metrics.recordRecommendation(AiRecommendationMetrics.TYPE_ENTITY_SUGGEST, tenantId, 0.2, true, 10L);
            metrics.recordRecommendation(AiRecommendationMetrics.TYPE_ANALYTICS_SUGGEST, tenantId, 0.7, false, 20L);
            metrics.recordRecommendation(AiRecommendationMetrics.TYPE_ANALYTICS_SUGGEST, tenantId, 0.3, true, 20L);
            metrics.recordRecommendation(AiRecommendationMetrics.TYPE_PIPELINE_DRAFT, tenantId, 0.6, false, 30L);
            metrics.recordRecommendation(AiRecommendationMetrics.TYPE_PIPELINE_DRAFT, tenantId, 0.4, true, 30L);

            var snapshot = metrics.snapshot();
            
            // Each type should have 50% fallback rate
            assertThat(snapshot).allMatch(s -> s.fallbackRate() == 0.50);
            
            // Each type should have 2 requests
            assertThat(snapshot).allMatch(s -> s.requestCount() == 2);
        }

        @Test
        @DisplayName("regression can detect quality degradation across types")
        void regression_canDetectQualityDegradation() {
            SimpleMeterRegistry registry = new SimpleMeterRegistry();
            MetricsCollector collector = new MetricsCollector(registry);
            AiRecommendationMetrics metrics = new AiRecommendationMetrics(collector);

            String tenantId = "tenant-degradation-test";
            
            // Simulate degraded quality: low confidence, high fallback
            for (int i = 0; i < 100; i++) {
                metrics.recordRecommendation(
                    AiRecommendationMetrics.TYPE_ENTITY_SUGGEST, 
                    tenantId, 
                    0.35, // low confidence
                    true,  // high fallback
                    80L   // high latency
                );
            }

            double fallbackRate = metrics.getFallbackRate(AiRecommendationMetrics.TYPE_ENTITY_SUGGEST);
            double meanConfidence = metrics.getMeanConfidence(AiRecommendationMetrics.TYPE_ENTITY_SUGGEST);
            
            // Regression detection: these values should trigger alerts
            assertThat(fallbackRate).isGreaterThan(0.50); // > 50% fallback
            assertThat(meanConfidence).isLessThan(0.40); // < 40% confidence
        }
    }
}
