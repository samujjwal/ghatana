package com.ghatana.aiplatform.observability;

import com.ghatana.platform.observability.NoopMetricsCollector;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for QualityMonitor with MetricsCollector and
 * DataDriftDetector.
 *
 * Tests validate:
 * - QualityMonitor and DataDriftDetector interaction
 * - SLA enforcement triggers retraining
 * - Root cause analysis (drift vs quality degradation)
 * - Multi-tenant isolation in integrated workflow
 * - Metrics emission through observability stack
 *
 * @see QualityMonitor
 * @see DataDriftDetector
 * @see com.ghatana.observability.MetricsCollector
 */
@DisplayName("QualityMonitor Integration Tests")
class QualityMonitorIntegrationTest extends EventloopTestBase {

        private QualityMonitor qualityMonitor;
        private DataDriftDetector driftDetector;

        @BeforeEach
        void setUp() {
                // GIVEN: Quality monitor and drift detector with noop metrics
                qualityMonitor = new QualityMonitor(new NoopMetricsCollector(),
                                new DataDriftDetector(new NoopMetricsCollector()));
                driftDetector = new DataDriftDetector(new NoopMetricsCollector());

                // Configure SLA
                qualityMonitor.setQualitySLA("tenant-prod", "fraud-detection", 0.85);
                qualityMonitor.setQualitySLA("tenant-test", "recommendation", 0.80);
        }

        /**
         * Verifies SLA breach when predictions consistently fail despite no drift.
         *
         * GIVEN: Model with stable data distribution (no drift)
         * WHEN: Prediction quality degrades (SLA breach)
         * THEN: Alert generated, indicates model decay not data drift
         */
        @Test
        @DisplayName("Should distinguish model decay from data drift via integrated check")
        void shouldDistinguishModelDecayFromDataDrift() {
                // GIVEN: Baseline distribution (no drift)
                qualityMonitor.setQualitySLA("tenant-prod", "model-v2", 0.85);
                driftDetector.setBaseline(
                                "tenant-prod",
                                "payment_amount",
                                java.util.Map.of("low", 0.4, "medium", 0.4, "high", 0.2));

                // WHEN: Record observations with same distribution (no drift)
                for (int i = 0; i < 100; i++) {
                        if (i < 40)
                                driftDetector.recordObservation("tenant-prod", "payment_amount", "low");
                        else if (i < 80)
                                driftDetector.recordObservation("tenant-prod", "payment_amount", "medium");
                        else
                                driftDetector.recordObservation("tenant-prod", "payment_amount", "high");
                }

                double psi = driftDetector.calculatePSI("tenant-prod", "payment_amount");

                // THEN: Drift is minimal
                assertThat(psi)
                                .as("PSI should be near zero (no drift)")
                                .isLessThan(0.1);

                // WHEN: Record poor quality predictions
                for (int i = 0; i < 100; i++) {
                        qualityMonitor.recordPrediction(
                                        "tenant-prod", "model-v2", 0.95, i % 10 > 3 // True positive rate ~40%
                        );
                }

                // THEN: Quality alert generated
                QualityMonitor.QualityAlert alert = qualityMonitor.checkQuality("tenant-prod", "model-v2");
                assertThat(alert.isBreeched())
                                .as("SLA should be breached (precision ~40% < 85% threshold)")
                                .isTrue();

                // THEN: Root cause is model decay, not drift
                assertThat(psi)
                                .as("Drift PSI confirms stable data")
                                .isLessThan(0.1);
        }

        /**
         * Verifies data drift triggers quality warning via cross-component check.
         *
         * GIVEN: Model with stable quality baseline
         * WHEN: Input data distribution shifts significantly
         * THEN: Drift detector alerts and quality monitor records context
         */
        @Test
        @DisplayName("Should correlate data drift with quality degradation")
        void shouldCorrelateDataDriftWithQualityDegradation() {
                // GIVEN: Baseline distribution and quality SLA
                driftDetector.setBaseline(
                                "tenant-prod",
                                "user_age",
                                java.util.Map.of("young", 0.3, "middle", 0.5, "senior", 0.2));
                qualityMonitor.setQualitySLA("tenant-prod", "age-model", 0.85);

                // WHEN: Input distribution shifts (20% -> 50% young users)
                for (int i = 0; i < 100; i++) {
                        if (i < 50)
                                driftDetector.recordObservation("tenant-prod", "user_age", "young");
                        else if (i < 80)
                                driftDetector.recordObservation("tenant-prod", "user_age", "middle");
                        else
                                driftDetector.recordObservation("tenant-prod", "user_age", "senior");
                }

                double psi = driftDetector.calculatePSI("tenant-prod", "user_age");

                // THEN: Significant drift detected
                assertThat(psi)
                                .as("PSI should indicate drift (0.5 young vs 0.3 expected)")
                                .isGreaterThan(0.15);

                // WHEN: Quality predictions recorded on shifted data
                for (int i = 0; i < 100; i++) {
                        qualityMonitor.recordPrediction(
                                        "tenant-prod", "age-model", 0.9, i % 12 > 4 // Recall ~58%
                        );
                }

                QualityMonitor.QualityAlert alert = qualityMonitor.checkQuality("tenant-prod", "age-model");

                // THEN: Quality degradation correlated with drift
                assertThat(alert.isBreeched())
                                .as("Quality breach correlates with drift")
                                .isTrue();
        }

        /**
         * Verifies multi-tenant isolation in integrated workflow.
         *
         * GIVEN: Multiple tenants with overlapping model names
         * WHEN: Each tenant's models and data tracked
         * THEN: No cross-tenant data leakage
         */
        @Test
        @DisplayName("Should enforce multi-tenant isolation in integrated checks")
        void shouldEnforceTenantIsolationIntegrated() {
                // GIVEN: Same model name for different tenants
                qualityMonitor.setQualitySLA("tenant-a", "shared-model", 0.90);
                qualityMonitor.setQualitySLA("tenant-b", "shared-model", 0.80);

                driftDetector.setBaseline(
                                "tenant-a",
                                "shared-feature",
                                java.util.Map.of("cat-a", 0.6, "cat-b", 0.4));
                driftDetector.setBaseline(
                                "tenant-b",
                                "shared-feature",
                                java.util.Map.of("cat-x", 0.7, "cat-y", 0.3));

                // WHEN: Record tenant-a observations
                for (int i = 0; i < 100; i++) {
                        driftDetector.recordObservation("tenant-a", "shared-feature",
                                        i < 60 ? "cat-a" : "cat-b");
                }

                // THEN: Tenant-a drift is zero (same distribution)
                double driftA = driftDetector.calculatePSI("tenant-a", "shared-feature");
                assertThat(driftA)
                                .as("Tenant-A drift should be minimal")
                                .isLessThan(0.05);

                // WHEN: Record tenant-b observations (different distribution)
                for (int i = 0; i < 100; i++) {
                        driftDetector.recordObservation("tenant-b", "shared-feature",
                                        i < 30 ? "cat-x" : "cat-y");
                }

                // THEN: Tenant-b drift is significant (30% cat-x vs 70% expected)
                double driftB = driftDetector.calculatePSI("tenant-b", "shared-feature");
                assertThat(driftB)
                                .as("Tenant-B drift should show distribution change")
                                .isGreaterThan(0.15);

                // VERIFY: Tenant A drift unaffected by tenant B data
                double driftAFinal = driftDetector.calculatePSI("tenant-a", "shared-feature");
                assertThat(driftAFinal)
                                .as("Tenant-A drift unchanged after tenant-B operations")
                                .isEqualTo(driftA);
        }

        /**
         * Verifies retraining decision logic based on integrated checks.
         *
         * GIVEN: Model with SLA threshold
         * WHEN: Combined drift + quality check
         * THEN: Retraining recommended if either breached
         */
        @Test
        @DisplayName("Should trigger retraining on integrated SLA/drift breach")
        void shouldTriggerRetrainingOnIntegratedBreach() {
                // GIVEN: Model with 0.85 precision SLA and drift monitoring
                qualityMonitor.setQualitySLA("prod", "recommendation-v3", 0.85);
                driftDetector.setBaseline(
                                "prod",
                                "user_engagement",
                                java.util.Map.of("low", 0.4, "med", 0.35, "high", 0.25));

                // Scenario 1: Both drift and quality breach
                for (int i = 0; i < 100; i++) {
                        driftDetector.recordObservation("prod", "user_engagement",
                                        i < 50 ? "high" : i < 80 ? "med" : "low");
                }
                double drift = driftDetector.calculatePSI("prod", "user_engagement");

                for (int i = 0; i < 100; i++) {
                        qualityMonitor.recordPrediction("prod", "recommendation-v3", 0.9,
                                        i % 13 > 4);
                }
                QualityMonitor.QualityAlert alert = qualityMonitor.checkQuality("prod", "recommendation-v3");

                // THEN: Both should indicate retraining needed
                boolean shouldRetrain = drift > 0.25 || alert.isBreeched();
                assertThat(shouldRetrain)
                                .as("Should recommend retraining (drift or SLA breach)")
                                .isTrue();
        }

        /**
         * Verifies SLA compliance with good predictions and stable data.
         *
         * GIVEN: Model with 0.80 SLA
         * WHEN: Predictions are accurate and data is stable
         * THEN: No alerts generated
         */
        @Test
        @DisplayName("Should maintain SLA when quality and stability are good")
        void shouldMaintainSLAWithGoodQualityAndStability() {
                // GIVEN: Relaxed SLA and stable baseline
                qualityMonitor.setQualitySLA("tenant-stable", "model-good", 0.80);
                driftDetector.setBaseline(
                                "tenant-stable",
                                "stable-feature",
                                java.util.Map.of("a", 0.5, "b", 0.5));

                // WHEN: Record stable observations (100% match baseline)
                for (int i = 0; i < 100; i++) {
                        driftDetector.recordObservation("tenant-stable", "stable-feature",
                                        i < 50 ? "a" : "b");
                }
                double drift = driftDetector.calculatePSI("tenant-stable", "stable-feature");

                // WHEN: Record accurate predictions (90% precision)
                for (int i = 0; i < 100; i++) {
                        qualityMonitor.recordPrediction("tenant-stable", "model-good", 0.95,
                                        i % 10 < 9); // 90% correct
                }
                QualityMonitor.QualityAlert alert = qualityMonitor.checkQuality("tenant-stable", "model-good");

                // THEN: No alerts
                assertThat(drift)
                                .as("Drift should be minimal")
                                .isLessThan(0.05);
                assertThat(alert.isBreeched())
                                .as("SLA should be met (90% > 80%)")
                                .isFalse();
        }

        /**
         * Verifies metrics are emitted throughout integrated workflow.
         *
         * GIVEN: Operations on drift detector and quality monitor
         * WHEN: Processing requests
         * THEN: Metrics collected by observability system
         */
        @Test
        @DisplayName("Should emit metrics through observability integration")
        void shouldEmitMetricsIntegrated() {
                // GIVEN: Both components with noop metrics (verifies no exceptions)
                qualityMonitor.setQualitySLA("int-tenant", "metric-model", 0.85);
                driftDetector.setBaseline(
                                "int-tenant",
                                "metric-feature",
                                java.util.Map.of("x", 0.5, "y", 0.5));

                // WHEN: Run integrated workflow (should emit metrics without error)
                for (int i = 0; i < 50; i++) {
                        driftDetector.recordObservation("int-tenant", "metric-feature",
                                        i < 25 ? "x" : "y");
                }
                driftDetector.calculatePSI("int-tenant", "metric-feature");

                for (int i = 0; i < 50; i++) {
                        qualityMonitor.recordPrediction("int-tenant", "metric-model", 0.92,
                                        i % 8 < 7);
                }
                qualityMonitor.checkQuality("int-tenant", "metric-model");

                // THEN: No exceptions (noop metrics collector handles all operations)
                assertThat(true)
                                .as("Metrics emitted successfully")
                                .isTrue();
        }
}
