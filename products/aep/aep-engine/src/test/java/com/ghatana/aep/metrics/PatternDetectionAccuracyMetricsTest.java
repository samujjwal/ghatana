/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.metrics;

import com.ghatana.platform.observability.Metrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Tests for PatternDetectionAccuracyMetrics.
 *
 * @doc.type class
 * @doc.purpose Test pattern detection accuracy metrics
 * @doc.layer product
 * @doc.pattern UnitTest
 */
@DisplayName("PatternDetectionAccuracyMetrics")
class PatternDetectionAccuracyMetricsTest {

    private PatternDetectionAccuracyMetrics collector;
    private Metrics metrics;

    @BeforeEach
    void setUp() {
        metrics = mock(Metrics.class);
        collector = new PatternDetectionAccuracyMetrics(metrics);
    }

    @Nested
    @DisplayName("Detection Recording")
    class DetectionRecordingTests {

        @Test
        @DisplayName("records true positive")
        void recordsTruePositive() {
            collector.recordTruePositive("THRESHOLD", "pattern-1");
            
            PatternDetectionAccuracyMetrics.PatternAccuracyStats stats = collector.getStats("THRESHOLD");
            assertThat(stats.truePositives()).isEqualTo(1);
            assertThat(stats.falsePositives()).isEqualTo(0);
            assertThat(stats.falseNegatives()).isEqualTo(0);
            assertThat(stats.trueNegatives()).isEqualTo(0);
        }

        @Test
        @DisplayName("records false positive")
        void recordsFalsePositive() {
            collector.recordFalsePositive("ANOMALY", "pattern-1");
            
            PatternDetectionAccuracyMetrics.PatternAccuracyStats stats = collector.getStats("ANOMALY");
            assertThat(stats.falsePositives()).isEqualTo(1);
        }

        @Test
        @DisplayName("records false negative")
        void recordsFalseNegative() {
            collector.recordFalseNegative("SEQUENCE", "pattern-1");
            
            PatternDetectionAccuracyMetrics.PatternAccuracyStats stats = collector.getStats("SEQUENCE");
            assertThat(stats.falseNegatives()).isEqualTo(1);
        }

        @Test
        @DisplayName("records true negative")
        void recordsTrueNegative() {
            collector.recordTrueNegative("CUSTOM", "pattern-1");
            
            PatternDetectionAccuracyMetrics.PatternAccuracyStats stats = collector.getStats("CUSTOM");
            assertThat(stats.trueNegatives()).isEqualTo(1);
        }

        @Test
        @DisplayName("increments counters correctly")
        void incrementsCountersCorrectly() {
            collector.recordTruePositive("THRESHOLD", "p1");
            collector.recordTruePositive("THRESHOLD", "p2");
            collector.recordFalsePositive("THRESHOLD", "p1");
            
            PatternDetectionAccuracyMetrics.PatternAccuracyStats stats = collector.getStats("THRESHOLD");
            assertThat(stats.truePositives()).isEqualTo(2);
            assertThat(stats.falsePositives()).isEqualTo(1);
        }

        @Test
        @DisplayName("tracks multiple pattern types separately")
        void tracksMultiplePatternTypesSeparately() {
            collector.recordTruePositive("THRESHOLD", "p1");
            collector.recordTruePositive("ANOMALY", "p1");
            
            assertThat(collector.getStats("THRESHOLD").truePositives()).isEqualTo(1);
            assertThat(collector.getStats("ANOMALY").truePositives()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Metric Calculations")
    class MetricCalculationTests {

        @Test
        @DisplayName("calculates precision")
        void calculatesPrecision() {
            collector.recordTruePositive("THRESHOLD", "p1");
            collector.recordTruePositive("THRESHOLD", "p2");
            collector.recordFalsePositive("THRESHOLD", "p1");
            
            double precision = collector.getPrecision("THRESHOLD");
            assertThat(precision).isEqualTo(0.6666666666666666);
        }

        @Test
        @DisplayName("returns 0 precision when no positive predictions")
        void returns0PrecisionWhenNoPositivePredictions() {
            collector.recordFalsePositive("THRESHOLD", "p1");
            
            double precision = collector.getPrecision("THRESHOLD");
            assertThat(precision).isEqualTo(0.0);
        }

        @Test
        @DisplayName("calculates recall")
        void calculatesRecall() {
            collector.recordTruePositive("THRESHOLD", "p1");
            collector.recordTruePositive("THRESHOLD", "p2");
            collector.recordFalseNegative("THRESHOLD", "p1");
            
            double recall = collector.getRecall("THRESHOLD");
            assertThat(recall).isEqualTo(0.6666666666666666);
        }

        @Test
        @DisplayName("returns 0 recall when no actual positives")
        void returns0RecallWhenNoActualPositives() {
            collector.recordFalseNegative("THRESHOLD", "p1");
            
            double recall = collector.getRecall("THRESHOLD");
            assertThat(recall).isEqualTo(0.0);
        }

        @Test
        @DisplayName("calculates F1 score")
        void calculatesF1Score() {
            collector.recordTruePositive("THRESHOLD", "p1");
            collector.recordTruePositive("THRESHOLD", "p2");
            collector.recordFalsePositive("THRESHOLD", "p1");
            collector.recordFalseNegative("THRESHOLD", "p1");
            
            double f1 = collector.getF1Score("THRESHOLD");
            assertThat(f1).isEqualTo(0.6666666666666666);
        }

        @Test
        @DisplayName("calculates accuracy")
        void calculatesAccuracy() {
            collector.recordTruePositive("THRESHOLD", "p1");
            collector.recordTruePositive("THRESHOLD", "p2");
            collector.recordTrueNegative("THRESHOLD", "p1");
            collector.recordTrueNegative("THRESHOLD", "p2");
            collector.recordFalsePositive("THRESHOLD", "p1");
            collector.recordFalseNegative("THRESHOLD", "p1");
            
            PatternDetectionAccuracyMetrics.PatternAccuracyStats stats = collector.getStats("THRESHOLD");
            assertThat(stats.accuracy()).isEqualTo(0.6666666666666666);
        }

        @Test
        @DisplayName("perfect detection has 1.0 metrics")
        void perfectDetectionHas10Metrics() {
            collector.recordTruePositive("THRESHOLD", "p1");
            collector.recordTrueNegative("THRESHOLD", "p1");
            
            PatternDetectionAccuracyMetrics.PatternAccuracyStats stats = collector.getStats("THRESHOLD");
            assertThat(stats.precision()).isEqualTo(1.0);
            assertThat(stats.recall()).isEqualTo(1.0);
            assertThat(stats.f1Score()).isEqualTo(1.0);
            assertThat(stats.accuracy()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("no detection has 0.0 metrics")
        void noDetectionHas00Metrics() {
            double precision = collector.getPrecision("THRESHOLD");
            double recall = collector.getRecall("THRESHOLD");
            double f1 = collector.getF1Score("THRESHOLD");
            
            assertThat(precision).isEqualTo(0.0);
            assertThat(recall).isEqualTo(0.0);
            assertThat(f1).isEqualTo(0.0);
        }
    }

    @Nested
    @DisplayName("PatternAccuracyStats")
    class PatternAccuracyStatsTests {

        @Test
        @DisplayName("stats start at zero")
        void statsStartAtZero() {
            PatternDetectionAccuracyMetrics.PatternAccuracyStats stats = 
                new PatternDetectionAccuracyMetrics.PatternAccuracyStats();
            
            assertThat(stats.truePositives()).isEqualTo(0);
            assertThat(stats.falsePositives()).isEqualTo(0);
            assertThat(stats.falseNegatives()).isEqualTo(0);
            assertThat(stats.trueNegatives()).isEqualTo(0);
        }

        @Test
        @DisplayName("increment operations are thread-safe")
        void incrementOperationsAreThreadSafe() throws InterruptedException {
            PatternDetectionAccuracyMetrics.PatternAccuracyStats stats = 
                new PatternDetectionAccuracyMetrics.PatternAccuracyStats();
            
            Thread t1 = new Thread(() -> {
                for (int i = 0; i < 1000; i++) stats.incrementTruePositives();
            });
            Thread t2 = new Thread(() -> {
                for (int i = 0; i < 1000; i++) stats.incrementFalsePositives();
            });
            
            t1.start();
            t2.start();
            t1.join();
            t2.join();
            
            assertThat(stats.truePositives()).isEqualTo(1000);
            assertThat(stats.falsePositives()).isEqualTo(1000);
        }
    }

    @Nested
    @DisplayName("Get Stats")
    class GetStatsTests {

        @Test
        @DisplayName("returns empty stats for unknown pattern type")
        void returnsEmptyStatsForUnknownPatternType() {
            PatternDetectionAccuracyMetrics.PatternAccuracyStats stats = 
                collector.getStats("UNKNOWN");
            
            assertThat(stats.truePositives()).isEqualTo(0);
            assertThat(stats.falsePositives()).isEqualTo(0);
        }

        @Test
        @DisplayName("returns stats for known pattern type")
        void returnsStatsForKnownPatternType() {
            collector.recordTruePositive("THRESHOLD", "p1");
            
            PatternDetectionAccuracyMetrics.PatternAccuracyStats stats = collector.getStats("THRESHOLD");
            assertThat(stats.truePositives()).isEqualTo(1);
        }
    }
}
