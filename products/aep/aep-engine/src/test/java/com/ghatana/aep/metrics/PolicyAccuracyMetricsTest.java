/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.aep.metrics;

import com.ghatana.platform.observability.Metrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for PolicyAccuracyMetrics.
 *
 * @doc.type class
 * @doc.purpose Test policy accuracy metrics
 * @doc.layer product
 * @doc.pattern UnitTest
 */
@DisplayName("PolicyAccuracyMetrics")
class PolicyAccuracyMetricsTest {

    private PolicyAccuracyMetrics collector;
    private Metrics metrics;

    @BeforeEach
    void setUp() { // GH-90000
        metrics = new Metrics(new SimpleMeterRegistry()); // GH-90000
        collector = new PolicyAccuracyMetrics(metrics); // GH-90000
    }

    @Nested
    @DisplayName("Policy Decision Recording")
    class DecisionRecordingTests {

        @Test
        @DisplayName("records true positive")
        void recordsTruePositive() { // GH-90000
            collector.recordTruePositive("policy-1", "block"); // GH-90000
            
            PolicyAccuracyMetrics.PolicyStats stats = collector.getStats("policy-1");
            assertThat(stats.truePositives()).isEqualTo(1); // GH-90000
            assertThat(stats.falsePositives()).isEqualTo(0); // GH-90000
        }

        @Test
        @DisplayName("records false positive")
        void recordsFalsePositive() { // GH-90000
            collector.recordFalsePositive("policy-1", "block"); // GH-90000
            
            PolicyAccuracyMetrics.PolicyStats stats = collector.getStats("policy-1");
            assertThat(stats.falsePositives()).isEqualTo(1); // GH-90000
        }

        @Test
        @DisplayName("records false negative")
        void recordsFalseNegative() { // GH-90000
            collector.recordFalseNegative("policy-1");
            
            PolicyAccuracyMetrics.PolicyStats stats = collector.getStats("policy-1");
            assertThat(stats.falseNegatives()).isEqualTo(1); // GH-90000
        }

        @Test
        @DisplayName("records true negative")
        void recordsTrueNegative() { // GH-90000
            collector.recordTrueNegative("policy-1");
            
            PolicyAccuracyMetrics.PolicyStats stats = collector.getStats("policy-1");
            assertThat(stats.trueNegatives()).isEqualTo(1); // GH-90000
        }

        @Test
        @DisplayName("records policy promotion")
        void recordsPolicyPromotion() { // GH-90000
            collector.recordPolicyPromotion("policy-1", "auto"); // GH-90000
            
            PolicyAccuracyMetrics.PolicyStats stats = collector.getStats("policy-1");
            assertThat(stats.promotions()).isEqualTo(1); // GH-90000
            assertThat(stats.demotions()).isEqualTo(0); // GH-90000
        }

        @Test
        @DisplayName("records policy demotion")
        void recordsPolicyDemotion() { // GH-90000
            collector.recordPolicyDemotion("policy-1", "low_accuracy"); // GH-90000
            
            PolicyAccuracyMetrics.PolicyStats stats = collector.getStats("policy-1");
            assertThat(stats.demotions()).isEqualTo(1); // GH-90000
        }

        @Test
        @DisplayName("tracks multiple policies separately")
        void tracksMultiplePoliciesSeparately() { // GH-90000
            collector.recordTruePositive("policy-1", "block"); // GH-90000
            collector.recordTruePositive("policy-2", "warn"); // GH-90000
            
            assertThat(collector.getStats("policy-1").truePositives()).isEqualTo(1);
            assertThat(collector.getStats("policy-2").truePositives()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Metric Calculations")
    class MetricCalculationTests {

        @Test
        @DisplayName("calculates precision")
        void calculatesPrecision() { // GH-90000
            collector.recordTruePositive("policy-1", "block"); // GH-90000
            collector.recordTruePositive("policy-1", "block"); // GH-90000
            collector.recordFalsePositive("policy-1", "block"); // GH-90000
            
            double precision = collector.getPrecision("policy-1");
            assertThat(precision).isEqualTo(0.6666666666666666); // GH-90000
        }

        @Test
        @DisplayName("calculates recall")
        void calculatesRecall() { // GH-90000
            collector.recordTruePositive("policy-1", "block"); // GH-90000
            collector.recordTruePositive("policy-1", "block"); // GH-90000
            collector.recordFalseNegative("policy-1");
            
            double recall = collector.getRecall("policy-1");
            assertThat(recall).isEqualTo(0.6666666666666666); // GH-90000
        }

        @Test
        @DisplayName("calculates accuracy")
        void calculatesAccuracy() { // GH-90000
            collector.recordTruePositive("policy-1", "block"); // GH-90000
            collector.recordTruePositive("policy-1", "block"); // GH-90000
            collector.recordTrueNegative("policy-1");
            collector.recordFalsePositive("policy-1", "block"); // GH-90000
            
            double accuracy = collector.getAccuracy("policy-1");
            assertThat(accuracy).isEqualTo(0.75); // GH-90000
        }

        @Test
        @DisplayName("calculates F1 score")
        void calculatesF1Score() { // GH-90000
            collector.recordTruePositive("policy-1", "block"); // GH-90000
            collector.recordTruePositive("policy-1", "block"); // GH-90000
            collector.recordFalsePositive("policy-1", "block"); // GH-90000
            collector.recordFalseNegative("policy-1");
            
            double f1 = collector.getF1Score("policy-1");
            assertThat(f1).isEqualTo(0.6666666666666666); // GH-90000
        }

        @Test
        @DisplayName("calculates promotion success rate")
        void calculatesPromotionSuccessRate() { // GH-90000
            collector.recordPolicyPromotion("policy-1", "auto"); // GH-90000
            collector.recordPolicyPromotion("policy-1", "auto"); // GH-90000
            collector.recordPolicyDemotion("policy-1", "low_accuracy"); // GH-90000
            
            double rate = collector.getPromotionSuccessRate("policy-1");
            assertThat(rate).isEqualTo(0.5); // GH-90000
        }

        @Test
        @DisplayName("perfect policy has 1.0 metrics")
        void perfectPolicyHas10Metrics() { // GH-90000
            collector.recordTruePositive("policy-1", "block"); // GH-90000
            collector.recordTrueNegative("policy-1");
            
            PolicyAccuracyMetrics.PolicyStats stats = collector.getStats("policy-1");
            assertThat(stats.precision()).isEqualTo(1.0); // GH-90000
            assertThat(stats.recall()).isEqualTo(1.0); // GH-90000
            assertThat(stats.f1Score()).isEqualTo(1.0); // GH-90000
            assertThat(stats.accuracy()).isEqualTo(1.0); // GH-90000
        }

        @Test
        @DisplayName("no decisions has 0.0 metrics")
        void noDecisionsHas00Metrics() { // GH-90000
            double precision = collector.getPrecision("policy-1");
            double recall = collector.getRecall("policy-1");
            double accuracy = collector.getAccuracy("policy-1");
            
            assertThat(precision).isEqualTo(0.0); // GH-90000
            assertThat(recall).isEqualTo(0.0); // GH-90000
            assertThat(accuracy).isEqualTo(0.0); // GH-90000
        }
    }

    @Nested
    @DisplayName("PolicyStats")
    class PolicyStatsTests {

        @Test
        @DisplayName("stats start at zero")
        void statsStartAtZero() { // GH-90000
            PolicyAccuracyMetrics.PolicyStats stats = new PolicyAccuracyMetrics.PolicyStats(); // GH-90000
            
            assertThat(stats.truePositives()).isEqualTo(0); // GH-90000
            assertThat(stats.falsePositives()).isEqualTo(0); // GH-90000
            assertThat(stats.falseNegatives()).isEqualTo(0); // GH-90000
            assertThat(stats.trueNegatives()).isEqualTo(0); // GH-90000
            assertThat(stats.promotions()).isEqualTo(0); // GH-90000
            assertThat(stats.demotions()).isEqualTo(0); // GH-90000
        }

        @Test
        @DisplayName("increment operations are thread-safe")
        void incrementOperationsAreThreadSafe() throws InterruptedException { // GH-90000
            PolicyAccuracyMetrics.PolicyStats stats = new PolicyAccuracyMetrics.PolicyStats(); // GH-90000
            
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
        @DisplayName("returns empty stats for unknown policy")
        void returnsEmptyStatsForUnknownPolicy() { // GH-90000
            PolicyAccuracyMetrics.PolicyStats stats = collector.getStats("unknown");
            
            assertThat(stats.truePositives()).isEqualTo(0); // GH-90000
            assertThat(stats.falsePositives()).isEqualTo(0); // GH-90000
        }

        @Test
        @DisplayName("returns stats for known policy")
        void returnsStatsForKnownPolicy() { // GH-90000
            collector.recordTruePositive("policy-1", "block"); // GH-90000
            
            PolicyAccuracyMetrics.PolicyStats stats = collector.getStats("policy-1");
            assertThat(stats.truePositives()).isEqualTo(1); // GH-90000
        }
    }
}
