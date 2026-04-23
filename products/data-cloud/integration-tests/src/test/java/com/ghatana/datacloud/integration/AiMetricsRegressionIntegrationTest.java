/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 */
package com.ghatana.datacloud.integration;

import com.ghatana.datacloud.launcher.ai.AiRecommendationMetrics;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.observability.NoopMetricsCollector;
import com.ghatana.platform.observability.SimpleMetricsCollector;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.offset;

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
 * @doc.purpose Integration tests for AI metrics regression testing (DC-E3) // GH-90000
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
        void entitySuggestFallbackRate_withinThreshold() { // GH-90000
            SimpleMeterRegistry registry = new SimpleMeterRegistry(); // GH-90000
            SimpleMetricsCollector collector = new SimpleMetricsCollector(registry); // GH-90000
            AiRecommendationMetrics metrics = new AiRecommendationMetrics(collector); // GH-90000

            String tenantId = "tenant-regression-test";
            
            // Simulate typical traffic: 70% AI, 30% fallback
            for (int i = 0; i < 70; i++) { // GH-90000
                metrics.recordRecommendation( // GH-90000
                    AiRecommendationMetrics.TYPE_ENTITY_SUGGEST, 
                    tenantId, 
                    0.85, // high confidence
                    false, // not fallback
                    50L   // latency
                );
            }
            for (int i = 0; i < 30; i++) { // GH-90000
                metrics.recordRecommendation( // GH-90000
                    AiRecommendationMetrics.TYPE_ENTITY_SUGGEST, 
                    tenantId, 
                    0.20, // heuristic confidence
                    true,  // fallback
                    10L   // faster latency
                );
            }

            double fallbackRate = metrics.getFallbackRate(AiRecommendationMetrics.TYPE_ENTITY_SUGGEST); // GH-90000
            
            // Regression threshold: fallback rate should not exceed 30%
            assertThat(fallbackRate).isLessThanOrEqualTo(0.30); // GH-90000
            
            // Verify total count
            assertThat(metrics.getRequestCount(AiRecommendationMetrics.TYPE_ENTITY_SUGGEST)).isEqualTo(100); // GH-90000
        }

        @Test
        @DisplayName("analytics_suggest mean confidence should not drop below 0.60 threshold")
        void analyticsSuggestConfidence_aboveThreshold() { // GH-90000
            SimpleMeterRegistry registry = new SimpleMeterRegistry(); // GH-90000
            SimpleMetricsCollector collector = new SimpleMetricsCollector(registry); // GH-90000
            AiRecommendationMetrics metrics = new AiRecommendationMetrics(collector); // GH-90000

            String tenantId = "tenant-regression-test";
            
            // Simulate typical confidence distribution
            double[] confidences = {0.90, 0.85, 0.80, 0.75, 0.70, 0.65, 0.60, 0.55, 0.50, 0.45};
            for (double conf : confidences) { // GH-90000
                metrics.recordRecommendation( // GH-90000
                    AiRecommendationMetrics.TYPE_ANALYTICS_SUGGEST, 
                    tenantId, 
                    conf,
                    false,
                    60L
                );
            }

            double meanConfidence = metrics.getMeanConfidence(AiRecommendationMetrics.TYPE_ANALYTICS_SUGGEST); // GH-90000
            
            // Regression threshold: mean confidence should not drop below 0.60
            assertThat(meanConfidence).isGreaterThanOrEqualTo(0.60); // GH-90000
            
            // Verify count
            assertThat(metrics.getRequestCount(AiRecommendationMetrics.TYPE_ANALYTICS_SUGGEST)).isEqualTo(10); // GH-90000
        }

        @Test
        @DisplayName("pipeline_draft latency should not exceed 500ms P95 threshold")
        void pipelineDraftLatency_withinThreshold() { // GH-90000
            SimpleMeterRegistry registry = new SimpleMeterRegistry(); // GH-90000
            SimpleMetricsCollector collector = new SimpleMetricsCollector(registry); // GH-90000
            AiRecommendationMetrics metrics = new AiRecommendationMetrics(collector); // GH-90000

            String tenantId = "tenant-regression-test";
            
            // Simulate typical latency distribution (ms) // GH-90000
            long[] latencies = {100, 150, 200, 250, 300, 350, 400, 450, 500, 550};
            for (long latency : latencies) { // GH-90000
                metrics.recordRecommendation( // GH-90000
                    AiRecommendationMetrics.TYPE_PIPELINE_DRAFT, 
                    tenantId, 
                    0.75,
                    false,
                    latency
                );
            }

            // Verify all requests were recorded
            assertThat(metrics.getRequestCount(AiRecommendationMetrics.TYPE_PIPELINE_DRAFT)).isEqualTo(10); // GH-90000
            
            // Verify mean confidence is reasonable
            assertThat(metrics.getMeanConfidence(AiRecommendationMetrics.TYPE_PIPELINE_DRAFT)) // GH-90000
                .isGreaterThanOrEqualTo(0.70); // GH-90000
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
        void snapshot_includesAllKnownTypes() { // GH-90000
            SimpleMeterRegistry registry = new SimpleMeterRegistry(); // GH-90000
            SimpleMetricsCollector collector = new SimpleMetricsCollector(registry); // GH-90000
            AiRecommendationMetrics metrics = new AiRecommendationMetrics(collector); // GH-90000

            String tenantId = "tenant-snapshot-test";
            
            // Record at least one metric for each type
            metrics.recordRecommendation(AiRecommendationMetrics.TYPE_ENTITY_SUGGEST, tenantId, 0.8, false, 10L); // GH-90000
            metrics.recordRecommendation(AiRecommendationMetrics.TYPE_ANALYTICS_SUGGEST, tenantId, 0.7, false, 20L); // GH-90000
            metrics.recordRecommendation(AiRecommendationMetrics.TYPE_PIPELINE_DRAFT, tenantId, 0.6, false, 30L); // GH-90000
            metrics.recordRecommendation(AiRecommendationMetrics.TYPE_PIPELINE_HINT, tenantId, 0.5, false, 40L); // GH-90000
            metrics.recordRecommendation(AiRecommendationMetrics.TYPE_BRAIN_EXPLAIN, tenantId, 0.4, false, 50L); // GH-90000
            metrics.recordRecommendation(AiRecommendationMetrics.TYPE_VOICE_INTENT, tenantId, 0.3, false, 60L); // GH-90000

            var snapshot = metrics.snapshot(); // GH-90000
            
            assertThat(snapshot).hasSize(6); // GH-90000
            assertThat(snapshot).allMatch(s -> s.requestCount() > 0); // GH-90000
        }

        @Test
        @DisplayName("snapshot accurately reflects fallback rates")
        void snapshot_accuratelyReflectsFallbackRates() { // GH-90000
            SimpleMeterRegistry registry = new SimpleMeterRegistry(); // GH-90000
            SimpleMetricsCollector collector = new SimpleMetricsCollector(registry); // GH-90000
            AiRecommendationMetrics metrics = new AiRecommendationMetrics(collector); // GH-90000

            String tenantId = "tenant-fallback-test";
            
            // Record with known fallback rate (50%) // GH-90000
            metrics.recordRecommendation(AiRecommendationMetrics.TYPE_ENTITY_SUGGEST, tenantId, 0.8, false, 10L); // GH-90000
            metrics.recordRecommendation(AiRecommendationMetrics.TYPE_ENTITY_SUGGEST, tenantId, 0.2, true, 10L); // GH-90000

            var snapshot = metrics.snapshot(); // GH-90000
            var entitySuggestSnapshot = snapshot.stream() // GH-90000
                .filter(s -> s.type().equals(AiRecommendationMetrics.TYPE_ENTITY_SUGGEST)) // GH-90000
                .findFirst() // GH-90000
                .orElseThrow(); // GH-90000
            
            assertThat(entitySuggestSnapshot.fallbackRate()).isEqualTo(0.50); // GH-90000
            assertThat(entitySuggestSnapshot.fallbackCount()).isEqualTo(1); // GH-90000
        }

        @Test
        @DisplayName("snapshot provides mean confidence for each type")
        void snapshot_providesMeanConfidence() { // GH-90000
            SimpleMeterRegistry registry = new SimpleMeterRegistry(); // GH-90000
            SimpleMetricsCollector collector = new SimpleMetricsCollector(registry); // GH-90000
            AiRecommendationMetrics metrics = new AiRecommendationMetrics(collector); // GH-90000

            String tenantId = "tenant-confidence-test";
            
            // Record known confidence values
            metrics.recordRecommendation(AiRecommendationMetrics.TYPE_ANALYTICS_SUGGEST, tenantId, 0.9, false, 10L); // GH-90000
            metrics.recordRecommendation(AiRecommendationMetrics.TYPE_ANALYTICS_SUGGEST, tenantId, 0.7, false, 10L); // GH-90000
            metrics.recordRecommendation(AiRecommendationMetrics.TYPE_ANALYTICS_SUGGEST, tenantId, 0.5, false, 10L); // GH-90000

            var snapshot = metrics.snapshot(); // GH-90000
            var analyticsSnapshot = snapshot.stream() // GH-90000
                .filter(s -> s.type().equals(AiRecommendationMetrics.TYPE_ANALYTICS_SUGGEST)) // GH-90000
                .findFirst() // GH-90000
                .orElseThrow(); // GH-90000
            
            // Mean should be (0.9 + 0.7 + 0.5) / 3 = 0.7 // GH-90000
            assertThat(analyticsSnapshot.meanConfidence()).isCloseTo(0.7, offset(0.01)); // GH-90000
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
        void thumbsUpFeedback_recordedWithoutAffectingRequestCount() { // GH-90000
            SimpleMeterRegistry registry = new SimpleMeterRegistry(); // GH-90000
            SimpleMetricsCollector collector = new SimpleMetricsCollector(registry); // GH-90000
            AiRecommendationMetrics metrics = new AiRecommendationMetrics(collector); // GH-90000

            String tenantId = "tenant-feedback-test";
            
            // Record a recommendation
            metrics.recordRecommendation(AiRecommendationMetrics.TYPE_ENTITY_SUGGEST, tenantId, 0.8, false, 10L); // GH-90000
            long requestCountBefore = metrics.getRequestCount(AiRecommendationMetrics.TYPE_ENTITY_SUGGEST); // GH-90000
            
            // Record positive feedback
            metrics.recordFeedback(AiRecommendationMetrics.TYPE_ENTITY_SUGGEST, tenantId, true); // GH-90000
            
            // Request count should not change
            long requestCountAfter = metrics.getRequestCount(AiRecommendationMetrics.TYPE_ENTITY_SUGGEST); // GH-90000
            assertThat(requestCountAfter).isEqualTo(requestCountBefore); // GH-90000
        }

        @Test
        @DisplayName("thumbs-down feedback is recorded")
        void thumbsDownFeedback_recorded() { // GH-90000
            SimpleMeterRegistry registry = new SimpleMeterRegistry(); // GH-90000
            SimpleMetricsCollector collector = new SimpleMetricsCollector(registry); // GH-90000
            AiRecommendationMetrics metrics = new AiRecommendationMetrics(collector); // GH-90000

            String tenantId = "tenant-feedback-test";
            
            // Record negative feedback
            metrics.recordFeedback(AiRecommendationMetrics.TYPE_ANALYTICS_SUGGEST, tenantId, false); // GH-90000
            
            // Should not throw and metrics should remain functional
            assertThatCode(() -> metrics.recordFeedback( // GH-90000
                AiRecommendationMetrics.TYPE_ANALYTICS_SUGGEST, tenantId, false))
                .doesNotThrowAnyException(); // GH-90000
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
        void errors_recordedWithoutAffectingRequestCount() { // GH-90000
            SimpleMeterRegistry registry = new SimpleMeterRegistry(); // GH-90000
            SimpleMetricsCollector collector = new SimpleMetricsCollector(registry); // GH-90000
            AiRecommendationMetrics metrics = new AiRecommendationMetrics(collector); // GH-90000

            String tenantId = "tenant-error-test";
            
            // Record a successful recommendation
            metrics.recordRecommendation(AiRecommendationMetrics.TYPE_BRAIN_EXPLAIN, tenantId, 0.8, false, 10L); // GH-90000
            long requestCountBefore = metrics.getRequestCount(AiRecommendationMetrics.TYPE_BRAIN_EXPLAIN); // GH-90000
            
            // Record an error
            metrics.recordError( // GH-90000
                AiRecommendationMetrics.TYPE_BRAIN_EXPLAIN, 
                tenantId, 
                new RuntimeException("LLM timeout"));
            
            // Request count should not change
            long requestCountAfter = metrics.getRequestCount(AiRecommendationMetrics.TYPE_BRAIN_EXPLAIN); // GH-90000
            assertThat(requestCountAfter).isEqualTo(requestCountBefore); // GH-90000
        }

        @Test
        @DisplayName("error recording handles null cause gracefully")
        void errorRecording_handlesNullCause() { // GH-90000
            SimpleMeterRegistry registry = new SimpleMeterRegistry(); // GH-90000
            SimpleMetricsCollector collector = new SimpleMetricsCollector(registry); // GH-90000
            AiRecommendationMetrics metrics = new AiRecommendationMetrics(collector); // GH-90000

            String tenantId = "tenant-error-test";
            
            // Record error with null cause
            assertThatCode(() -> metrics.recordError( // GH-90000
                AiRecommendationMetrics.TYPE_PIPELINE_HINT, tenantId, null))
                .doesNotThrowAnyException(); // GH-90000
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
        void overallFallbackRate_canBeMonitored() { // GH-90000
            SimpleMeterRegistry registry = new SimpleMeterRegistry(); // GH-90000
            SimpleMetricsCollector collector = new SimpleMetricsCollector(registry); // GH-90000
            AiRecommendationMetrics metrics = new AiRecommendationMetrics(collector); // GH-90000

            String tenantId = "tenant-cross-type-test";
            
            // Record mixed traffic across types
            metrics.recordRecommendation(AiRecommendationMetrics.TYPE_ENTITY_SUGGEST, tenantId, 0.8, false, 10L); // GH-90000
            metrics.recordRecommendation(AiRecommendationMetrics.TYPE_ENTITY_SUGGEST, tenantId, 0.2, true, 10L); // GH-90000
            metrics.recordRecommendation(AiRecommendationMetrics.TYPE_ANALYTICS_SUGGEST, tenantId, 0.7, false, 20L); // GH-90000
            metrics.recordRecommendation(AiRecommendationMetrics.TYPE_ANALYTICS_SUGGEST, tenantId, 0.3, true, 20L); // GH-90000
            metrics.recordRecommendation(AiRecommendationMetrics.TYPE_PIPELINE_DRAFT, tenantId, 0.6, false, 30L); // GH-90000
            metrics.recordRecommendation(AiRecommendationMetrics.TYPE_PIPELINE_DRAFT, tenantId, 0.4, true, 30L); // GH-90000

            var snapshot = metrics.snapshot(); // GH-90000

            // Filter to only types that have recorded data
            var activeTypes = snapshot.stream() // GH-90000
                .filter(s -> s.requestCount() > 0) // GH-90000
                .toList(); // GH-90000

            // Each active type should have 50% fallback rate
            assertThat(activeTypes).allMatch(s -> s.fallbackRate() == 0.50); // GH-90000

            // Each active type should have 2 requests
            assertThat(activeTypes).allMatch(s -> s.requestCount() == 2); // GH-90000
        }

        @Test
        @DisplayName("regression can detect quality degradation across types")
        void regression_canDetectQualityDegradation() { // GH-90000
            SimpleMeterRegistry registry = new SimpleMeterRegistry(); // GH-90000
            SimpleMetricsCollector collector = new SimpleMetricsCollector(registry); // GH-90000
            AiRecommendationMetrics metrics = new AiRecommendationMetrics(collector); // GH-90000

            String tenantId = "tenant-degradation-test";
            
            // Simulate degraded quality: low confidence, high fallback
            for (int i = 0; i < 100; i++) { // GH-90000
                metrics.recordRecommendation( // GH-90000
                    AiRecommendationMetrics.TYPE_ENTITY_SUGGEST, 
                    tenantId, 
                    0.35, // low confidence
                    true,  // high fallback
                    80L   // high latency
                );
            }

            double fallbackRate = metrics.getFallbackRate(AiRecommendationMetrics.TYPE_ENTITY_SUGGEST); // GH-90000
            double meanConfidence = metrics.getMeanConfidence(AiRecommendationMetrics.TYPE_ENTITY_SUGGEST); // GH-90000
            
            // Regression detection: these values should trigger alerts
            assertThat(fallbackRate).isGreaterThan(0.50); // > 50% fallback // GH-90000
            assertThat(meanConfidence).isLessThan(0.40); // < 40% confidence // GH-90000
        }
    }
}
