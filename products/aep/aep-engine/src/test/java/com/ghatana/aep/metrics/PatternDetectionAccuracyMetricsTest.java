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
@DisplayName("PatternDetectionAccuracyMetrics [GH-90000]")
class PatternDetectionAccuracyMetricsTest {

    private PatternDetectionAccuracyMetrics collector;
    private Metrics metrics;

    @BeforeEach
    void setUp() { // GH-90000
        metrics = mock(Metrics.class); // GH-90000
        collector = new PatternDetectionAccuracyMetrics(metrics); // GH-90000
    }

    @Nested
    @DisplayName("Detection Recording [GH-90000]")
    class DetectionRecordingTests {

        @Test
        @DisplayName("records true positive [GH-90000]")
        void recordsTruePositive() { // GH-90000
            collector.recordTruePositive("THRESHOLD", "pattern-1"); // GH-90000
            
            PatternDetectionAccuracyMetrics.PatternAccuracyStats stats = collector.getStats("THRESHOLD [GH-90000]");
            assertThat(stats.truePositives()).isEqualTo(1); // GH-90000
            assertThat(stats.falsePositives()).isEqualTo(0); // GH-90000
            assertThat(stats.falseNegatives()).isEqualTo(0); // GH-90000
            assertThat(stats.trueNegatives()).isEqualTo(0); // GH-90000
        }

        @Test
        @DisplayName("records false positive [GH-90000]")
        void recordsFalsePositive() { // GH-90000
            collector.recordFalsePositive("ANOMALY", "pattern-1"); // GH-90000
            
            PatternDetectionAccuracyMetrics.PatternAccuracyStats stats = collector.getStats("ANOMALY [GH-90000]");
            assertThat(stats.falsePositives()).isEqualTo(1); // GH-90000
        }

        @Test
        @DisplayName("records false negative [GH-90000]")
        void recordsFalseNegative() { // GH-90000
            collector.recordFalseNegative("SEQUENCE", "pattern-1"); // GH-90000
            
            PatternDetectionAccuracyMetrics.PatternAccuracyStats stats = collector.getStats("SEQUENCE [GH-90000]");
            assertThat(stats.falseNegatives()).isEqualTo(1); // GH-90000
        }

        @Test
        @DisplayName("records true negative [GH-90000]")
        void recordsTrueNegative() { // GH-90000
            collector.recordTrueNegative("CUSTOM", "pattern-1"); // GH-90000
            
            PatternDetectionAccuracyMetrics.PatternAccuracyStats stats = collector.getStats("CUSTOM [GH-90000]");
            assertThat(stats.trueNegatives()).isEqualTo(1); // GH-90000
        }

        @Test
        @DisplayName("increments counters correctly [GH-90000]")
        void incrementsCountersCorrectly() { // GH-90000
            collector.recordTruePositive("THRESHOLD", "p1"); // GH-90000
            collector.recordTruePositive("THRESHOLD", "p2"); // GH-90000
            collector.recordFalsePositive("THRESHOLD", "p1"); // GH-90000
            
            PatternDetectionAccuracyMetrics.PatternAccuracyStats stats = collector.getStats("THRESHOLD [GH-90000]");
            assertThat(stats.truePositives()).isEqualTo(2); // GH-90000
            assertThat(stats.falsePositives()).isEqualTo(1); // GH-90000
        }

        @Test
        @DisplayName("tracks multiple pattern types separately [GH-90000]")
        void tracksMultiplePatternTypesSeparately() { // GH-90000
            collector.recordTruePositive("THRESHOLD", "p1"); // GH-90000
            collector.recordTruePositive("ANOMALY", "p1"); // GH-90000
            
            assertThat(collector.getStats("THRESHOLD [GH-90000]").truePositives()).isEqualTo(1);
            assertThat(collector.getStats("ANOMALY [GH-90000]").truePositives()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Metric Calculations [GH-90000]")
    class MetricCalculationTests {

        @Test
        @DisplayName("calculates precision [GH-90000]")
        void calculatesPrecision() { // GH-90000
            collector.recordTruePositive("THRESHOLD", "p1"); // GH-90000
            collector.recordTruePositive("THRESHOLD", "p2"); // GH-90000
            collector.recordFalsePositive("THRESHOLD", "p1"); // GH-90000
            
            double precision = collector.getPrecision("THRESHOLD [GH-90000]");
            assertThat(precision).isEqualTo(0.6666666666666666); // GH-90000
        }

        @Test
        @DisplayName("returns 0 precision when no positive predictions [GH-90000]")
        void returns0PrecisionWhenNoPositivePredictions() { // GH-90000
            collector.recordFalsePositive("THRESHOLD", "p1"); // GH-90000
            
            double precision = collector.getPrecision("THRESHOLD [GH-90000]");
            assertThat(precision).isEqualTo(0.0); // GH-90000
        }

        @Test
        @DisplayName("calculates recall [GH-90000]")
        void calculatesRecall() { // GH-90000
            collector.recordTruePositive("THRESHOLD", "p1"); // GH-90000
            collector.recordTruePositive("THRESHOLD", "p2"); // GH-90000
            collector.recordFalseNegative("THRESHOLD", "p1"); // GH-90000
            
            double recall = collector.getRecall("THRESHOLD [GH-90000]");
            assertThat(recall).isEqualTo(0.6666666666666666); // GH-90000
        }

        @Test
        @DisplayName("returns 0 recall when no actual positives [GH-90000]")
        void returns0RecallWhenNoActualPositives() { // GH-90000
            collector.recordFalseNegative("THRESHOLD", "p1"); // GH-90000
            
            double recall = collector.getRecall("THRESHOLD [GH-90000]");
            assertThat(recall).isEqualTo(0.0); // GH-90000
        }

        @Test
        @DisplayName("calculates F1 score [GH-90000]")
        void calculatesF1Score() { // GH-90000
            collector.recordTruePositive("THRESHOLD", "p1"); // GH-90000
            collector.recordTruePositive("THRESHOLD", "p2"); // GH-90000
            collector.recordFalsePositive("THRESHOLD", "p1"); // GH-90000
            collector.recordFalseNegative("THRESHOLD", "p1"); // GH-90000
            
            double f1 = collector.getF1Score("THRESHOLD [GH-90000]");
            assertThat(f1).isEqualTo(0.6666666666666666); // GH-90000
        }

        @Test
        @DisplayName("calculates accuracy [GH-90000]")
        void calculatesAccuracy() { // GH-90000
            collector.recordTruePositive("THRESHOLD", "p1"); // GH-90000
            collector.recordTruePositive("THRESHOLD", "p2"); // GH-90000
            collector.recordTrueNegative("THRESHOLD", "p1"); // GH-90000
            collector.recordTrueNegative("THRESHOLD", "p2"); // GH-90000
            collector.recordFalsePositive("THRESHOLD", "p1"); // GH-90000
            collector.recordFalseNegative("THRESHOLD", "p1"); // GH-90000
            
            PatternDetectionAccuracyMetrics.PatternAccuracyStats stats = collector.getStats("THRESHOLD [GH-90000]");
            assertThat(stats.accuracy()).isEqualTo(0.6666666666666666); // GH-90000
        }

        @Test
        @DisplayName("perfect detection has 1.0 metrics [GH-90000]")
        void perfectDetectionHas10Metrics() { // GH-90000
            collector.recordTruePositive("THRESHOLD", "p1"); // GH-90000
            collector.recordTrueNegative("THRESHOLD", "p1"); // GH-90000
            
            PatternDetectionAccuracyMetrics.PatternAccuracyStats stats = collector.getStats("THRESHOLD [GH-90000]");
            assertThat(stats.precision()).isEqualTo(1.0); // GH-90000
            assertThat(stats.recall()).isEqualTo(1.0); // GH-90000
            assertThat(stats.f1Score()).isEqualTo(1.0); // GH-90000
            assertThat(stats.accuracy()).isEqualTo(1.0); // GH-90000
        }

        @Test
        @DisplayName("no detection has 0.0 metrics [GH-90000]")
        void noDetectionHas00Metrics() { // GH-90000
            double precision = collector.getPrecision("THRESHOLD [GH-90000]");
            double recall = collector.getRecall("THRESHOLD [GH-90000]");
            double f1 = collector.getF1Score("THRESHOLD [GH-90000]");
            
            assertThat(precision).isEqualTo(0.0); // GH-90000
            assertThat(recall).isEqualTo(0.0); // GH-90000
            assertThat(f1).isEqualTo(0.0); // GH-90000
        }
    }

    @Nested
    @DisplayName("PatternAccuracyStats [GH-90000]")
    class PatternAccuracyStatsTests {

        @Test
        @DisplayName("stats start at zero [GH-90000]")
        void statsStartAtZero() { // GH-90000
            PatternDetectionAccuracyMetrics.PatternAccuracyStats stats = 
                new PatternDetectionAccuracyMetrics.PatternAccuracyStats(); // GH-90000
            
            assertThat(stats.truePositives()).isEqualTo(0); // GH-90000
            assertThat(stats.falsePositives()).isEqualTo(0); // GH-90000
            assertThat(stats.falseNegatives()).isEqualTo(0); // GH-90000
            assertThat(stats.trueNegatives()).isEqualTo(0); // GH-90000
        }

        @Test
        @DisplayName("increment operations are thread-safe [GH-90000]")
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
    @DisplayName("Get Stats [GH-90000]")
    class GetStatsTests {

        @Test
        @DisplayName("returns empty stats for unknown pattern type [GH-90000]")
        void returnsEmptyStatsForUnknownPatternType() { // GH-90000
            PatternDetectionAccuracyMetrics.PatternAccuracyStats stats = 
                collector.getStats("UNKNOWN [GH-90000]");
            
            assertThat(stats.truePositives()).isEqualTo(0); // GH-90000
            assertThat(stats.falsePositives()).isEqualTo(0); // GH-90000
        }

        @Test
        @DisplayName("returns stats for known pattern type [GH-90000]")
        void returnsStatsForKnownPatternType() { // GH-90000
            collector.recordTruePositive("THRESHOLD", "p1"); // GH-90000
            
            PatternDetectionAccuracyMetrics.PatternAccuracyStats stats = collector.getStats("THRESHOLD [GH-90000]");
            assertThat(stats.truePositives()).isEqualTo(1); // GH-90000
        }
    }
}
