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
    void setUp() {
        metrics = mock(Metrics.class);
        collector = new PolicyAccuracyMetrics(metrics);
    }

    @Nested
    @DisplayName("Policy Decision Recording")
    class DecisionRecordingTests {

        @Test
        @DisplayName("records true positive")
        void recordsTruePositive() {
            collector.recordTruePositive("policy-1", "block");
            
            PolicyAccuracyMetrics.PolicyStats stats = collector.getStats("policy-1");
            assertThat(stats.truePositives()).isEqualTo(1);
            assertThat(stats.falsePositives()).isEqualTo(0);
        }

        @Test
        @DisplayName("records false positive")
        void recordsFalsePositive() {
            collector.recordFalsePositive("policy-1", "block");
            
            PolicyAccuracyMetrics.PolicyStats stats = collector.getStats("policy-1");
            assertThat(stats.falsePositives()).isEqualTo(1);
        }

        @Test
        @DisplayName("records false negative")
        void recordsFalseNegative() {
            collector.recordFalseNegative("policy-1");
            
            PolicyAccuracyMetrics.PolicyStats stats = collector.getStats("policy-1");
            assertThat(stats.falseNegatives()).isEqualTo(1);
        }

        @Test
        @DisplayName("records true negative")
        void recordsTrueNegative() {
            collector.recordTrueNegative("policy-1");
            
            PolicyAccuracyMetrics.PolicyStats stats = collector.getStats("policy-1");
            assertThat(stats.trueNegatives()).isEqualTo(1);
        }

        @Test
        @DisplayName("records policy promotion")
        void recordsPolicyPromotion() {
            collector.recordPolicyPromotion("policy-1", "auto");
            
            PolicyAccuracyMetrics.PolicyStats stats = collector.getStats("policy-1");
            assertThat(stats.promotions()).isEqualTo(1);
            assertThat(stats.demotions()).isEqualTo(0);
        }

        @Test
        @DisplayName("records policy demotion")
        void recordsPolicyDemotion() {
            collector.recordPolicyDemotion("policy-1", "low_accuracy");
            
            PolicyAccuracyMetrics.PolicyStats stats = collector.getStats("policy-1");
            assertThat(stats.demotions()).isEqualTo(1);
        }

        @Test
        @DisplayName("tracks multiple policies separately")
        void tracksMultiplePoliciesSeparately() {
            collector.recordTruePositive("policy-1", "block");
            collector.recordTruePositive("policy-2", "warn");
            
            assertThat(collector.getStats("policy-1").truePositives()).isEqualTo(1);
            assertThat(collector.getStats("policy-2").truePositives()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Metric Calculations")
    class MetricCalculationTests {

        @Test
        @DisplayName("calculates precision")
        void calculatesPrecision() {
            collector.recordTruePositive("policy-1", "block");
            collector.recordTruePositive("policy-1", "block");
            collector.recordFalsePositive("policy-1", "block");
            
            double precision = collector.getPrecision("policy-1");
            assertThat(precision).isEqualTo(0.6666666666666666);
        }

        @Test
        @DisplayName("calculates recall")
        void calculatesRecall() {
            collector.recordTruePositive("policy-1", "block");
            collector.recordTruePositive("policy-1", "block");
            collector.recordFalseNegative("policy-1");
            
            double recall = collector.getRecall("policy-1");
            assertThat(recall).isEqualTo(0.6666666666666666);
        }

        @Test
        @DisplayName("calculates accuracy")
        void calculatesAccuracy() {
            collector.recordTruePositive("policy-1", "block");
            collector.recordTruePositive("policy-1", "block");
            collector.recordTrueNegative("policy-1");
            collector.recordFalsePositive("policy-1");
            
            double accuracy = collector.getAccuracy("policy-1");
            assertThat(accuracy).isEqualTo(0.75);
        }

        @Test
        @DisplayName("calculates F1 score")
        void calculatesF1Score() {
            collector.recordTruePositive("policy-1", "block");
            collector.recordTruePositive("policy-1", "block");
            collector.recordFalsePositive("policy-1");
            collector.recordFalseNegative("policy-1");
            
            double f1 = collector.getF1Score("policy-1");
            assertThat(f1).isEqualTo(0.6666666666666666);
        }

        @Test
        @DisplayName("calculates promotion success rate")
        void calculatesPromotionSuccessRate() {
            collector.recordPolicyPromotion("policy-1", "auto");
            collector.recordPolicyPromotion("policy-1", "auto");
            collector.recordPolicyDemotion("policy-1", "low_accuracy");
            
            double rate = collector.getPromotionSuccessRate("policy-1");
            assertThat(rate).isEqualTo(0.5);
        }

        @Test
        @DisplayName("perfect policy has 1.0 metrics")
        void perfectPolicyHas10Metrics() {
            collector.recordTruePositive("policy-1", "block");
            collector.recordTrueNegative("policy-1");
            
            PolicyAccuracyMetrics.PolicyStats stats = collector.getStats("policy-1");
            assertThat(stats.precision()).isEqualTo(1.0);
            assertThat(stats.recall()).isEqualTo(1.0);
            assertThat(stats.f1Score()).isEqualTo(1.0);
            assertThat(stats.accuracy()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("no decisions has 0.0 metrics")
        void noDecisionsHas00Metrics() {
            double precision = collector.getPrecision("policy-1");
            double recall = collector.getRecall("policy-1");
            double accuracy = collector.getAccuracy("policy-1");
            
            assertThat(precision).isEqualTo(0.0);
            assertThat(recall).isEqualTo(0.0);
            assertThat(accuracy).isEqualTo(0.0);
        }
    }

    @Nested
    @DisplayName("PolicyStats")
    class PolicyStatsTests {

        @Test
        @DisplayName("stats start at zero")
        void statsStartAtZero() {
            PolicyAccuracyMetrics.PolicyStats stats = new PolicyAccuracyMetrics.PolicyStats();
            
            assertThat(stats.truePositives()).isEqualTo(0);
            assertThat(stats.falsePositives()).isEqualTo(0);
            assertThat(stats.falseNegatives()).isEqualTo(0);
            assertThat(stats.trueNegatives()).isEqualTo(0);
            assertThat(stats.promotions()).isEqualTo(0);
            assertThat(stats.demotions()).isEqualTo(0);
        }

        @Test
        @DisplayName("increment operations are thread-safe")
        void incrementOperationsAreThreadSafe() throws InterruptedException {
            PolicyAccuracyMetrics.PolicyStats stats = new PolicyAccuracyMetrics.PolicyStats();
            
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
        @DisplayName("returns empty stats for unknown policy")
        void returnsEmptyStatsForUnknownPolicy() {
            PolicyAccuracyMetrics.PolicyStats stats = collector.getStats("unknown");
            
            assertThat(stats.truePositives()).isEqualTo(0);
            assertThat(stats.falsePositives()).isEqualTo(0);
        }

        @Test
        @DisplayName("returns stats for known policy")
        void returnsStatsForKnownPolicy() {
            collector.recordTruePositive("policy-1", "block");
            
            PolicyAccuracyMetrics.PolicyStats stats = collector.getStats("policy-1");
            assertThat(stats.truePositives()).isEqualTo(1);
        }
    }
}
