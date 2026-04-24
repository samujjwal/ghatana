package com.ghatana.plugin.fraud.impl;

import com.ghatana.platform.plugin.PluginContext;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.plugin.fraud.FraudDetectionPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Explainability tests for {@link StandardFraudDetectionPlugin}.
 *
 * <p>Verifies that every fraud assessment includes a human-readable explanation,
 * that triggered rules are surfaced in the result, that risk levels are calibrated
 * to scores, and that model metrics expose the precision/recall/F1 needed for
 * offline model evaluation.
 *
 * @doc.type class
 * @doc.purpose Explainability and assessment quality tests for fraud detection plugin
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("Fraud detection – explainability")
@ExtendWith(MockitoExtension.class)
class FraudDetectionExplainabilityTest extends EventloopTestBase {

    @Mock
    private PluginContext mockContext;

    private StandardFraudDetectionPlugin plugin;

    @BeforeEach
    void setUp() {
        plugin = new StandardFraudDetectionPlugin();
        runPromise(() -> plugin.initialize(mockContext));
        runPromise(() -> plugin.start());
    }

    // =========================================================================
    // Assessment explanation content
    // =========================================================================

    @Nested
    @DisplayName("Assessment explanation")
    class AssessmentExplanation {

        @Test
        @DisplayName("clean transaction produces a non-null, non-blank explanation")
        void cleanTransactionExplanationIsPresent() {
            FraudDetectionPlugin.FraudDetectionRequest request = new FraudDetectionPlugin.FraudDetectionRequest(
                "entity-1", "PAYMENT", Map.of("amount", 50.0), "model-1"
            );
            FraudDetectionPlugin.FraudAssessment assessment =
                runPromise(() -> plugin.assessTransaction("txn-clean", request));

            assertThat(assessment.explanation())
                .as("explanation must be non-null for clean transaction")
                .isNotBlank();
        }

        @Test
        @DisplayName("clean transaction reports zero triggered rules and explains it")
        void cleanTransactionHasNoTriggeredRules() {
            FraudDetectionPlugin.FraudDetectionRequest request = new FraudDetectionPlugin.FraudDetectionRequest(
                "entity-1", "PAYMENT", Map.of("amount", 50.0), "model-1"
            );
            FraudDetectionPlugin.FraudAssessment assessment =
                runPromise(() -> plugin.assessTransaction("txn-clean-2", request));

            assertThat(assessment.triggeredRules())
                .as("clean transaction should trigger no rules")
                .isEmpty();
            assertThat(assessment.explanation())
                .contains("No fraud indicators detected");
        }

        @Test
        @DisplayName("amount-threshold rule triggers when amount exceeds threshold")
        void amountThresholdRuleTriggers() {
            FraudDetectionPlugin.FraudRule rule = new FraudDetectionPlugin.FraudRule(
                "rule-amount-1", "AMOUNT_THRESHOLD", "High value transaction", 1000.0
            );
            runPromise(() -> plugin.registerRule("PAYMENT", rule));

            FraudDetectionPlugin.FraudDetectionRequest request = new FraudDetectionPlugin.FraudDetectionRequest(
                "entity-2", "PAYMENT", Map.of("amount", 5000.0), "model-1"
            );
            FraudDetectionPlugin.FraudAssessment assessment =
                runPromise(() -> plugin.assessTransaction("txn-high-amount", request));

            assertThat(assessment.triggeredRules())
                .as("amount threshold rule must be listed in triggered rules")
                .contains("rule-amount-1");
            assertThat(assessment.explanation())
                .as("explanation must reference triggered rule ID")
                .contains("rule-amount-1");
        }

        @Test
        @DisplayName("velocity rule triggers when transaction count exceeds threshold")
        void velocityRuleTriggers() {
            FraudDetectionPlugin.FraudRule rule = new FraudDetectionPlugin.FraudRule(
                "rule-vel-1", "VELOCITY", "Too many transactions in 24h", 10.0
            );
            runPromise(() -> plugin.registerRule("PAYMENT", rule));

            FraudDetectionPlugin.FraudDetectionRequest request = new FraudDetectionPlugin.FraudDetectionRequest(
                "entity-3", "PAYMENT", Map.of("transaction_count_24h", 50), "model-1"
            );
            FraudDetectionPlugin.FraudAssessment assessment =
                runPromise(() -> plugin.assessTransaction("txn-velocity", request));

            assertThat(assessment.triggeredRules()).contains("rule-vel-1");
            assertThat(assessment.riskScore())
                .as("velocity violation should raise risk score above zero")
                .isGreaterThan(0.0);
        }

        @Test
        @DisplayName("geo-anomaly rule triggers when geo_anomaly feature is true")
        void geoAnomalyRuleTriggers() {
            FraudDetectionPlugin.FraudRule rule = new FraudDetectionPlugin.FraudRule(
                "rule-geo-1", "GEO_ANOMALY", "Geographic anomaly detected", 0.5
            );
            runPromise(() -> plugin.registerRule("PAYMENT", rule));

            FraudDetectionPlugin.FraudDetectionRequest request = new FraudDetectionPlugin.FraudDetectionRequest(
                "entity-4", "PAYMENT",
                Map.of("amount", 200.0, "geo_anomaly", true),
                "model-1"
            );
            FraudDetectionPlugin.FraudAssessment assessment =
                runPromise(() -> plugin.assessTransaction("txn-geo", request));

            assertThat(assessment.triggeredRules()).contains("rule-geo-1");
            assertThat(assessment.explanation()).contains("rule-geo-1");
        }

        @Test
        @DisplayName("multiple triggered rules are all reflected in the explanation")
        void multipleTriggeredRulesAllAppearInExplanation() {
            runPromise(() -> plugin.registerRule("PAYMENT", new FraudDetectionPlugin.FraudRule(
                "rule-A", "AMOUNT_THRESHOLD", "Large amount", 100.0)));
            runPromise(() -> plugin.registerRule("PAYMENT", new FraudDetectionPlugin.FraudRule(
                "rule-B", "VELOCITY", "High velocity", 5.0)));

            FraudDetectionPlugin.FraudDetectionRequest request = new FraudDetectionPlugin.FraudDetectionRequest(
                "entity-5", "PAYMENT",
                Map.of("amount", 999.0, "transaction_count_24h", 20),
                "model-1"
            );
            FraudDetectionPlugin.FraudAssessment assessment =
                runPromise(() -> plugin.assessTransaction("txn-multi", request));

            assertThat(assessment.triggeredRules())
                .as("both rules must be listed")
                .containsExactlyInAnyOrder("rule-A", "rule-B");
            assertThat(assessment.explanation())
                .contains("rule-A")
                .contains("rule-B");
        }
    }

    // =========================================================================
    // Risk level calibration
    // =========================================================================

    @Nested
    @DisplayName("Risk level calibration")
    class RiskLevelCalibration {

        @Test
        @DisplayName("score=0.0 maps to LOW risk level")
        void scoreZeroIsLow() {
            FraudDetectionPlugin.FraudAssessment assessment =
                runPromise(() -> plugin.assessTransaction("txn-low",
                    new FraudDetectionPlugin.FraudDetectionRequest(
                        "entity-low", "PAYMENT", Map.of("amount", 1.0), "model-1")));

            assertThat(assessment.riskScore()).isGreaterThanOrEqualTo(0.0);
            assertThat(assessment.riskLevel()).isEqualTo(FraudDetectionPlugin.FraudAssessment.RiskLevel.LOW);
        }

        @Test
        @DisplayName("triggered rule accumulates score; high enough triggers HIGH or CRITICAL level")
        void accumulatedScoreElevatesRiskLevel() {
            runPromise(() -> plugin.registerRule("COMMERCE",
                new FraudDetectionPlugin.FraudRule("rule-big", "AMOUNT_THRESHOLD", "Huge amount", 0.9)));

            FraudDetectionPlugin.FraudAssessment assessment =
                runPromise(() -> plugin.assessTransaction("txn-crit",
                    new FraudDetectionPlugin.FraudDetectionRequest(
                        "entity-crit", "COMMERCE", Map.of("amount", 99999.0), "model-1")));

            assertThat(assessment.riskLevel())
                .as("rule with threshold 0.9 should cause HIGH or CRITICAL")
                .isIn(
                    FraudDetectionPlugin.FraudAssessment.RiskLevel.HIGH,
                    FraudDetectionPlugin.FraudAssessment.RiskLevel.CRITICAL
                );
        }

        @Test
        @DisplayName("risk score is capped at 1.0 even with many triggered rules")
        void riskScoreIsCappedAtOne() {
            for (int i = 0; i < 10; i++) {
                runPromise(() -> plugin.registerRule("COMMERCE",
                    new FraudDetectionPlugin.FraudRule(
                        "rule-cap-" + System.nanoTime(),
                        "AMOUNT_THRESHOLD",
                        "Cap test rule",
                        0.5
                    )));
            }

            FraudDetectionPlugin.FraudAssessment assessment =
                runPromise(() -> plugin.assessTransaction("txn-cap",
                    new FraudDetectionPlugin.FraudDetectionRequest(
                        "entity-cap", "COMMERCE", Map.of("amount", 9999999.0), "model-1")));

            assertThat(assessment.riskScore())
                .as("risk score must never exceed 1.0")
                .isLessThanOrEqualTo(1.0);
        }
    }

    // =========================================================================
    // Model metrics (explainability for ML evaluation)
    // =========================================================================

    @Nested
    @DisplayName("Model metrics explainability")
    class ModelMetrics {

        @Test
        @DisplayName("training produces non-null metrics with expected fields")
        void trainingProducesMetrics() {
            List<FraudDetectionPlugin.TrainingData.TrainingExample> examples = List.of(
                new FraudDetectionPlugin.TrainingData.TrainingExample(
                    Map.of("amount", 100.0), false),
                new FraudDetectionPlugin.TrainingData.TrainingExample(
                    Map.of("amount", 9999.0), true),
                new FraudDetectionPlugin.TrainingData.TrainingExample(
                    Map.of("amount", 50.0), false),
                new FraudDetectionPlugin.TrainingData.TrainingExample(
                    Map.of("amount", 5000.0), true)
            );
            FraudDetectionPlugin.TrainingData data = new FraudDetectionPlugin.TrainingData(
                examples, Map.of("epochs", 10));

            runPromise(() -> plugin.trainModel("model-eval", data));

            FraudDetectionPlugin.ModelMetrics metrics =
                runPromise(() -> plugin.getModelMetrics("model-eval"));

            assertThat(metrics).isNotNull();
            assertThat(metrics.modelId()).isEqualTo("model-eval");
            assertThat(metrics.precision()).isGreaterThan(0.0).isLessThanOrEqualTo(1.0);
            assertThat(metrics.recall()).isGreaterThan(0.0).isLessThanOrEqualTo(1.0);
            assertThat(metrics.f1Score()).isGreaterThan(0.0).isLessThanOrEqualTo(1.0);
        }

        @Test
        @DisplayName("metrics reflect correct total prediction count")
        void metricsReflectExampleCount() {
            List<FraudDetectionPlugin.TrainingData.TrainingExample> examples = List.of(
                new FraudDetectionPlugin.TrainingData.TrainingExample(Map.of("amount", 1.0), false),
                new FraudDetectionPlugin.TrainingData.TrainingExample(Map.of("amount", 2.0), false),
                new FraudDetectionPlugin.TrainingData.TrainingExample(Map.of("amount", 3.0), true)
            );
            runPromise(() -> plugin.trainModel("model-count",
                new FraudDetectionPlugin.TrainingData(examples, Map.of())));

            FraudDetectionPlugin.ModelMetrics metrics =
                runPromise(() -> plugin.getModelMetrics("model-count"));

            assertThat(metrics.totalPredictions()).isEqualTo(3);
            assertThat(metrics.falseNegatives()).isEqualTo(1);
            assertThat(metrics.falsePositives()).isEqualTo(2);
        }

        @Test
        @DisplayName("unknown model returns null metrics (no phantom data)")
        void unknownModelReturnsNull() {
            FraudDetectionPlugin.ModelMetrics metrics =
                runPromise(() -> plugin.getModelMetrics("model-unknown"));

            assertThat(metrics).isNull();
        }

        @Test
        @DisplayName("metrics have a non-null calculatedAt timestamp")
        void metricsHaveTimestamp() {
            runPromise(() -> plugin.trainModel("model-ts",
                new FraudDetectionPlugin.TrainingData(
                    List.of(new FraudDetectionPlugin.TrainingData.TrainingExample(
                        Map.of("amount", 10.0), false)),
                    Map.of())));

            FraudDetectionPlugin.ModelMetrics metrics =
                runPromise(() -> plugin.getModelMetrics("model-ts"));

            assertThat(metrics.calculatedAt()).isNotNull();
            assertThat(metrics.calculatedAt()).isBeforeOrEqualTo(Instant.now());
        }
    }

    // =========================================================================
    // Pattern detection explainability
    // =========================================================================

    @Nested
    @DisplayName("Pattern detection explainability")
    class PatternDetection {

        @Test
        @DisplayName("fewer than 5 high-risk assessments returns null pattern (no false positives)")
        void insufficientHighRiskReturnsNoPattern() {
            FraudDetectionPlugin.FraudRule rule = new FraudDetectionPlugin.FraudRule(
                "rule-hi", "AMOUNT_THRESHOLD", "High value", 0.9);
            runPromise(() -> plugin.registerRule("RETAIL", rule));

            // Produce only 3 HIGH/CRITICAL assessments
            for (int i = 0; i < 3; i++) {
                runPromise(() -> plugin.assessTransaction("hi-txn-" + System.nanoTime(),
                    new FraudDetectionPlugin.FraudDetectionRequest(
                        "ent", "RETAIL", Map.of("amount", 999999.0), "m")));
            }

            Instant start = Instant.now().minusSeconds(60);
            Instant end = Instant.now().plusSeconds(60);
            FraudDetectionPlugin.FraudPattern pattern =
                runPromise(() -> plugin.detectPatterns("RETAIL",
                    new FraudDetectionPlugin.TimeWindow(start, end)));

            assertThat(pattern)
                .as("fewer than 5 high-risk transactions should not trigger a pattern")
                .isNull();
        }

        @Test
        @DisplayName("5+ high-risk assessments in window produces a pattern with type and confidence")
        void fiveHighRiskTransactionsTriggersPattern() {
            FraudDetectionPlugin.FraudRule rule = new FraudDetectionPlugin.FraudRule(
                "rule-vel2", "AMOUNT_THRESHOLD", "Velocity pattern rule", 0.9);
            runPromise(() -> plugin.registerRule("ECOM", rule));

            for (int i = 0; i < 6; i++) {
                runPromise(() -> plugin.assessTransaction("pattern-txn-" + System.nanoTime(),
                    new FraudDetectionPlugin.FraudDetectionRequest(
                        "ent-p", "ECOM", Map.of("amount", 999999.0), "m")));
            }

            Instant start = Instant.now().minusSeconds(10);
            Instant end = Instant.now().plusSeconds(10);
            FraudDetectionPlugin.FraudPattern pattern =
                runPromise(() -> plugin.detectPatterns("ECOM",
                    new FraudDetectionPlugin.TimeWindow(start, end)));

            assertThat(pattern).isNotNull();
            assertThat(pattern.patternType()).isEqualTo("HIGH_VELOCITY_RISK");
            assertThat(pattern.confidence())
                .as("confidence must be between 0 and 1")
                .isGreaterThan(0.0).isLessThanOrEqualTo(1.0);
            assertThat(pattern.affectedEntities())
                .as("pattern must reference triggering transaction IDs")
                .isNotEmpty();
        }
    }
}
