/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
    void setUp() { // GH-90000
        metrics = mock(Metrics.class); // GH-90000
        collector = new PatternDetectionAccuracyMetrics(metrics); // GH-90000
    }

    @Nested
    @DisplayName("Detection Recording")
    class DetectionRecordingTests {

        @Test
        @DisplayName("records true positive")
        void recordsTruePositive() { // GH-90000
            collector.recordTruePositive("THRESHOLD", "pattern-1"); // GH-90000
            
            PatternDetectionAccuracyMetrics.PatternAccuracyStats stats = collector.getStats("THRESHOLD");
            assertThat(stats.truePositives()).isEqualTo(1); // GH-90000
            assertThat(stats.falsePositives()).isEqualTo(0); // GH-90000
            assertThat(stats.falseNegatives()).isEqualTo(0); // GH-90000
            assertThat(stats.trueNegatives()).isEqualTo(0); // GH-90000
        }

        @Test
        @DisplayName("records false positive")
        void recordsFalsePositive() { // GH-90000
            collector.recordFalsePositive("ANOMALY", "pattern-1"); // GH-90000
            
            PatternDetectionAccuracyMetrics.PatternAccuracyStats stats = collector.getStats("ANOMALY");
            assertThat(stats.falsePositives()).isEqualTo(1); // GH-90000
        }

        @Test
        @DisplayName("records false negative")
        void recordsFalseNegative() { // GH-90000
            collector.recordFalseNegative("SEQUENCE", "pattern-1"); // GH-90000
            
            PatternDetectionAccuracyMetrics.PatternAccuracyStats stats = collector.getStats("SEQUENCE");
            assertThat(stats.falseNegatives()).isEqualTo(1); // GH-90000
        }

        @Test
        @DisplayName("records true negative")
        void recordsTrueNegative() { // GH-90000
            collector.recordTrueNegative("CUSTOM", "pattern-1"); // GH-90000
            
            PatternDetectionAccuracyMetrics.PatternAccuracyStats stats = collector.getStats("CUSTOM");
            assertThat(stats.trueNegatives()).isEqualTo(1); // GH-90000
        }

        @Test
        @DisplayName("increments counters correctly")
        void incrementsCountersCorrectly() { // GH-90000
            collector.recordTruePositive("THRESHOLD", "p1"); // GH-90000
            collector.recordTruePositive("THRESHOLD", "p2"); // GH-90000
            collector.recordFalsePositive("THRESHOLD", "p1"); // GH-90000
            
            PatternDetectionAccuracyMetrics.PatternAccuracyStats stats = collector.getStats("THRESHOLD");
            assertThat(stats.truePositives()).isEqualTo(2); // GH-90000
            assertThat(stats.falsePositives()).isEqualTo(1); // GH-90000
        }

        @Test
        @DisplayName("tracks multiple pattern types separately")
        void tracksMultiplePatternTypesSeparately() { // GH-90000
            collector.recordTruePositive("THRESHOLD", "p1"); // GH-90000
            collector.recordTruePositive("ANOMALY", "p1"); // GH-90000
            
            assertThat(collector.getStats("THRESHOLD").truePositives()).isEqualTo(1);
            assertThat(collector.getStats("ANOMALY").truePositives()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Metric Calculations")
    class MetricCalculationTests {

        @Test
        @DisplayName("calculates precision")
        void calculatesPrecision() { // GH-90000
            collector.recordTruePositive("THRESHOLD", "p1"); // GH-90000
            collector.recordTruePositive("THRESHOLD", "p2"); // GH-90000
            collector.recordFalsePositive("THRESHOLD", "p1"); // GH-90000
            
            double precision = collector.getPrecision("THRESHOLD");
            assertThat(precision).isEqualTo(0.6666666666666666); // GH-90000
        }

        @Test
        @DisplayName("returns 0 precision when no positive predictions")
        void returns0PrecisionWhenNoPositivePredictions() { // GH-90000
            collector.recordFalsePositive("THRESHOLD", "p1"); // GH-90000
            
            double precision = collector.getPrecision("THRESHOLD");
            assertThat(precision).isEqualTo(0.0); // GH-90000
        }

        @Test
        @DisplayName("calculates recall")
        void calculatesRecall() { // GH-90000
            collector.recordTruePositive("THRESHOLD", "p1"); // GH-90000
            collector.recordTruePositive("THRESHOLD", "p2"); // GH-90000
            collector.recordFalseNegative("THRESHOLD", "p1"); // GH-90000
            
            double recall = collector.getRecall("THRESHOLD");
            assertThat(recall).isEqualTo(0.6666666666666666); // GH-90000
        }

        @Test
        @DisplayName("returns 0 recall when no actual positives")
        void returns0RecallWhenNoActualPositives() { // GH-90000
            collector.recordFalseNegative("THRESHOLD", "p1"); // GH-90000
            
            double recall = collector.getRecall("THRESHOLD");
            assertThat(recall).isEqualTo(0.0); // GH-90000
        }

        @Test
        @DisplayName("calculates F1 score")
        void calculatesF1Score() { // GH-90000
            collector.recordTruePositive("THRESHOLD", "p1"); // GH-90000
            collector.recordTruePositive("THRESHOLD", "p2"); // GH-90000
            collector.recordFalsePositive("THRESHOLD", "p1"); // GH-90000
            collector.recordFalseNegative("THRESHOLD", "p1"); // GH-90000
            
            double f1 = collector.getF1Score("THRESHOLD");
            assertThat(f1).isEqualTo(0.6666666666666666); // GH-90000
        }

        @Test
        @DisplayName("calculates accuracy")
        void calculatesAccuracy() { // GH-90000
            collector.recordTruePositive("THRESHOLD", "p1"); // GH-90000
            collector.recordTruePositive("THRESHOLD", "p2"); // GH-90000
            collector.recordTrueNegative("THRESHOLD", "p1"); // GH-90000
            collector.recordTrueNegative("THRESHOLD", "p2"); // GH-90000
            collector.recordFalsePositive("THRESHOLD", "p1"); // GH-90000
            collector.recordFalseNegative("THRESHOLD", "p1"); // GH-90000
            
            PatternDetectionAccuracyMetrics.PatternAccuracyStats stats = collector.getStats("THRESHOLD");
            assertThat(stats.accuracy()).isEqualTo(0.6666666666666666); // GH-90000
        }

        @Test
        @DisplayName("perfect detection has 1.0 metrics")
        void perfectDetectionHas10Metrics() { // GH-90000
            collector.recordTruePositive("THRESHOLD", "p1"); // GH-90000
            collector.recordTrueNegative("THRESHOLD", "p1"); // GH-90000
            
            PatternDetectionAccuracyMetrics.PatternAccuracyStats stats = collector.getStats("THRESHOLD");
            assertThat(stats.precision()).isEqualTo(1.0); // GH-90000
            assertThat(stats.recall()).isEqualTo(1.0); // GH-90000
            assertThat(stats.f1Score()).isEqualTo(1.0); // GH-90000
            assertThat(stats.accuracy()).isEqualTo(1.0); // GH-90000
        }

        @Test
        @DisplayName("no detection has 0.0 metrics")
        void noDetectionHas00Metrics() { // GH-90000
            double precision = collector.getPrecision("THRESHOLD");
            double recall = collector.getRecall("THRESHOLD");
            double f1 = collector.getF1Score("THRESHOLD");
            
            assertThat(precision).isEqualTo(0.0); // GH-90000
            assertThat(recall).isEqualTo(0.0); // GH-90000
            assertThat(f1).isEqualTo(0.0); // GH-90000
        }
    }

    @Nested
    @DisplayName("PatternAccuracyStats")
    class PatternAccuracyStatsTests {

        @Test
        @DisplayName("stats start at zero")
        void statsStartAtZero() { // GH-90000
            PatternDetectionAccuracyMetrics.PatternAccuracyStats stats = 
                new PatternDetectionAccuracyMetrics.PatternAccuracyStats(); // GH-90000
            
            assertThat(stats.truePositives()).isEqualTo(0); // GH-90000
            assertThat(stats.falsePositives()).isEqualTo(0); // GH-90000
            assertThat(stats.falseNegatives()).isEqualTo(0); // GH-90000
            assertThat(stats.trueNegatives()).isEqualTo(0); // GH-90000
        }

        @Test
        @DisplayName("increment operations are thread-safe")
        void incrementOperationsAreThreadSafe() throws InterruptedException { // GH-90000
            PatternDetectionAccuracyMetrics.PatternAccuracyStats stats = 
                new PatternDetectionAccuracyMetrics.PatternAccuracyStats(); // GH-90000
            
            Thread t1 = new Thread(() -> { // GH-90000
                for (int i = 0; i < 1000; i++) stats.incrementTruePositives(); // GH-90000
            });
            Thread t2 = new Thread(() -> { // GH-90000
                for (int i = 0; i < 1000; i++) stats.incrementFalsePositives(); // GH-90000
            });
            
            t1.start(); // GH-90000
            t2.start(); // GH-90000
            t1.join(); // GH-90000
            t2.join(); // GH-90000
            
            assertThat(stats.truePositives()).isEqualTo(1000); // GH-90000
            assertThat(stats.falsePositives()).isEqualTo(1000); // GH-90000
        }
    }

    @Nested
    @DisplayName("Get Stats")
    class GetStatsTests {

        @Test
        @DisplayName("returns empty stats for unknown pattern type")
        void returnsEmptyStatsForUnknownPatternType() { // GH-90000
            PatternDetectionAccuracyMetrics.PatternAccuracyStats stats = 
                collector.getStats("UNKNOWN");
            
            assertThat(stats.truePositives()).isEqualTo(0); // GH-90000
            assertThat(stats.falsePositives()).isEqualTo(0); // GH-90000
        }

        @Test
        @DisplayName("returns stats for known pattern type")
        void returnsStatsForKnownPatternType() { // GH-90000
            collector.recordTruePositive("THRESHOLD", "p1"); // GH-90000
            
            PatternDetectionAccuracyMetrics.PatternAccuracyStats stats = collector.getStats("THRESHOLD");
            assertThat(stats.truePositives()).isEqualTo(1); // GH-90000
        }
    }
}
