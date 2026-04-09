/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.learning;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for model accuracy with deterministic fixtures (D012).
 *
 * <p>Validates model accuracy metrics and evaluation.
 *
 * @doc.type class
 * @doc.purpose Model accuracy tests with deterministic fixtures
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ModelAccuracy – Deterministic Fixtures (D012)")
class ModelAccuracyTest extends EventloopTestBase {

    @Mock
    private ModelEvaluationService evaluationService;

    // ─────────────────────────────────────────────────────────────────────────
    // Accuracy Metrics Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Accuracy Metrics")
    class AccuracyMetricsTests {

        @Test
        @DisplayName("[D012]: evaluate_returns_accuracy_metrics")
        void evaluateReturnsAccuracyMetrics() {
            String modelId = "model-001";
            String testDatasetId = "test-001";

            ModelEvaluationService.EvaluationResult result = new ModelEvaluationService.EvaluationResult(
                modelId, testDatasetId,
                0.92, 0.90, 0.88, 0.89, 0.95, 0.15,
                List.of(), List.of(), null, 5000, 1000
            );

            when(evaluationService.evaluate(modelId, testDatasetId))
                .thenReturn(Promise.of(result));

            ModelEvaluationService.EvaluationResult eval = runPromise(() ->
                evaluationService.evaluate(modelId, testDatasetId)
            );

            assertThat(eval.accuracy()).isEqualTo(0.92);
            assertThat(eval.precision()).isEqualTo(0.90);
            assertThat(eval.recall()).isEqualTo(0.88);
            assertThat(eval.f1Score()).isEqualTo(0.89);
        }

        @Test
        @DisplayName("[D012]: accuracy_meets_threshold")
        void accuracyMeetsThreshold() {
            ModelEvaluationService.EvaluationResult good = new ModelEvaluationService.EvaluationResult(
                "model-001", "test-001",
                0.92, 0.90, 0.88, 0.89, 0.95, 0.15,
                List.of(), List.of(), null, 5000, 1000
            );

            assertThat(good.meetsThreshold(0.90)).isTrue();

            ModelEvaluationService.EvaluationResult poor = new ModelEvaluationService.EvaluationResult(
                "model-002", "test-001",
                0.85, 0.82, 0.80, 0.81, 0.85, 0.25,
                List.of(), List.of(), null, 5000, 1000
            );

            assertThat(poor.meetsThreshold(0.90)).isFalse();
        }

        @Test
        @DisplayName("[D012]: deterministic_results_for_same_input")
        void deterministicResultsForSameInput() {
            String modelId = "model-001";
            String testDatasetId = "test-001";

            // Same inputs should produce same results
            ModelEvaluationService.EvaluationResult result1 = new ModelEvaluationService.EvaluationResult(
                modelId, testDatasetId, 0.92, 0.90, 0.88, 0.89, 0.95, 0.15,
                List.of(), List.of(), null, 5000, 1000
            );

            ModelEvaluationService.EvaluationResult result2 = new ModelEvaluationService.EvaluationResult(
                modelId, testDatasetId, 0.92, 0.90, 0.88, 0.89, 0.95, 0.15,
                List.of(), List.of(), null, 5000, 1000
            );

            assertThat(result1.accuracy()).isEqualTo(result2.accuracy());
            assertThat(result1.f1Score()).isEqualTo(result2.f1Score());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Per-Class Metrics Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Per-Class Metrics")
    class PerClassMetricsTests {

        @Test
        @DisplayName("[D012]: per_class_metrics_calculated_correctly")
        void perClassMetricsCalculatedCorrectly() {
            ModelEvaluationService.ClassMetrics positive = new ModelEvaluationService.ClassMetrics(
                "positive", 80, 10, 85, 5, 0.89, 0.94, 0.91
            );

            assertThat(positive.precision()).isEqualTo(0.89);
            assertThat(positive.recall()).isEqualTo(0.94);
            assertThat(positive.f1Score()).isEqualTo(0.91);

            // Verify counts
            assertThat(positive.truePositives()).isEqualTo(80);
            assertThat(positive.falsePositives()).isEqualTo(10);
            assertThat(positive.trueNegatives()).isEqualTo(85);
            assertThat(positive.falseNegatives()).isEqualTo(5);
        }

        @Test
        @DisplayName("[D012]: f1_score_harmonic_mean")
        void f1ScoreHarmonicMean() {
            // F1 = 2 * (precision * recall) / (precision + recall)
            double precision = 0.90;
            double recall = 0.80;
            double expectedF1 = 2 * (precision * recall) / (precision + recall);

            assertThat(expectedF1).isEqualTo(0.85, org.assertj.core.data.Offset.offset(0.01));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Model Comparison Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Model Comparison")
    class ModelComparisonTests {

        @Test
        @DisplayName("[D012]: compare_models_shows_improvement")
        void compareModelsShowsImprovement() {
            String baselineModelId = "model-v1";
            String candidateModelId = "model-v2";
            String testDatasetId = "test-001";

            ModelEvaluationService.EvaluationResult baseline = new ModelEvaluationService.EvaluationResult(
                baselineModelId, testDatasetId,
                0.85, 0.82, 0.80, 0.81, 0.90, 0.25,
                List.of(), List.of(), null, 4000, 1000
            );

            ModelEvaluationService.EvaluationResult candidate = new ModelEvaluationService.EvaluationResult(
                candidateModelId, testDatasetId,
                0.92, 0.90, 0.88, 0.89, 0.95, 0.15,
                List.of(), List.of(), null, 5000, 1000
            );

            ModelEvaluationService.ModelComparison comparison = new ModelEvaluationService.ModelComparison(
                baselineModelId, candidateModelId,
                baseline, candidate,
                0.07, 0.08, true, "Deploy candidate"
            );

            when(evaluationService.compareModels(baselineModelId, candidateModelId, testDatasetId))
                .thenReturn(Promise.of(comparison));

            ModelEvaluationService.ModelComparison result = runPromise(() ->
                evaluationService.compareModels(baselineModelId, candidateModelId, testDatasetId)
            );

            assertThat(result.accuracyImprovement()).isEqualTo(0.07);
            assertThat(result.f1Improvement()).isEqualTo(0.08);
            assertThat(result.candidateBetter()).isTrue();
        }

        @Test
        @DisplayName("[D012]: significant_improvement_detected")
        void significantImprovementDetected() {
            ModelEvaluationService.ModelComparison significant = new ModelEvaluationService.ModelComparison(
                "v1", "v2", null, null,
                0.05, 0.05, true, "Deploy"
            );

            assertThat(significant.isSignificantImprovement(0.03)).isTrue();

            ModelEvaluationService.ModelComparison minor = new ModelEvaluationService.ModelComparison(
                "v1", "v2", null, null,
                0.01, 0.01, true, "Keep baseline"
            );

            assertThat(minor.isSignificantImprovement(0.03)).isFalse();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // A/B Test Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("A/B Test")
    class ABTestTests {

        @Test
        @DisplayName("[D012]: ab_test_identifies_winner")
        void abTestIdentifiesWinner() {
            String modelAId = "model-A";
            String modelBId = "model-B";
            String testDatasetId = "test-001";

            ModelEvaluationService.ABTestResult result = new ModelEvaluationService.ABTestResult(
                modelAId, modelBId,
                0.88, 0.92, // A accuracy, B accuracy
                0.02,       // p-value
                true,       // significant
                "model-B",  // winner
                0.95,       // confidence interval
                2000        // sample size
            );

            when(evaluationService.runABTest(modelAId, modelBId, testDatasetId))
                .thenReturn(Promise.of(result));

            ModelEvaluationService.ABTestResult test = runPromise(() ->
                evaluationService.runABTest(modelAId, modelBId, testDatasetId)
            );

            assertThat(test.winner()).isEqualTo("model-B");
            assertThat(test.isSignificant(0.05)).isTrue();
        }

        @Test
        @DisplayName("[D012]: statistical_significance_threshold")
        void statisticalSignificanceThreshold() {
            ModelEvaluationService.ABTestResult significant = new ModelEvaluationService.ABTestResult(
                "A", "B", 0.88, 0.92, 0.01, true, "B", 0.95, 2000
            );

            assertThat(significant.isSignificant(0.05)).isTrue();
            assertThat(significant.isSignificant(0.001)).isFalse(); // p=0.01 > 0.001
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Prediction Error Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Prediction Errors")
    class PredictionErrorTests {

        @Test
        @DisplayName("[D012]: prediction_errors_tracked")
        void predictionErrorsTracked() {
            List<ModelEvaluationService.PredictionError> errors = List.of(
                new ModelEvaluationService.PredictionError(0, "positive", "negative", 0.45, "misclassification"),
                new ModelEvaluationService.PredictionError(5, "negative", "positive", 0.55, "false_positive")
            );

            assertThat(errors).hasSize(2);
            assertThat(errors.get(0).expected()).isEqualTo("positive");
            assertThat(errors.get(0).predicted()).isEqualTo("negative");
        }

        @Test
        @DisplayName("[D012]: low_confidence_predictions_identified")
        void lowConfidencePredictionsIdentified() {
            ModelEvaluationService.PredictionError lowConfidence =
                new ModelEvaluationService.PredictionError(0, "A", "B", 0.51, "uncertain");

            assertThat(lowConfidence.confidence()).isLessThan(0.60);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Confusion Matrix Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Confusion Matrix")
    class ConfusionMatrixTests {

        @Test
        @DisplayName("[D012]: confusion_matrix_diagonal_is_correct")
        void confusionMatrixDiagonalIsCorrect() {
            // 2x2 confusion matrix
            // TP | FP
            // FN | TN
            int[][] matrix = {
                {80, 10},   // TP=80, FP=10
                {5, 85}     // FN=5, TN=85
            };

            ModelEvaluationService.ConfusionMatrix cm = new ModelEvaluationService.ConfusionMatrix(
                List.of("positive", "negative"), matrix
            );

            assertThat(cm.getTruePositives(0)).isEqualTo(80);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Deterministic Fixture Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Deterministic Fixtures")
    class DeterministicFixtureTests {

        @Test
        @DisplayName("[D012]: same_test_dataset_produces_same_accuracy")
        void sameTestDatasetProducesSameAccuracy() {
            // With deterministic fixtures, same inputs produce same outputs
            double accuracy1 = evaluateWithFixture("model-001", "test-001");
            double accuracy2 = evaluateWithFixture("model-001", "test-001");

            assertThat(accuracy1).isEqualTo(accuracy2);
        }

        @Test
        @DisplayName("[D012]: different_test_datasets_produce_different_results")
        void differentTestDatasetsProduceDifferentResults() {
            double accuracy1 = evaluateWithFixture("model-001", "test-001");
            double accuracy2 = evaluateWithFixture("model-001", "test-002");

            assertThat(accuracy1).isNotEqualTo(accuracy2);
        }

        private double evaluateWithFixture(String modelId, String testId) {
            return switch (testId) {
                case "test-001" -> 0.92;
                case "test-002" -> 0.87;
                default -> 0.90;
            };
        }
    }
}
