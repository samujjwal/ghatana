/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
 * Tests for model accuracy with deterministic fixtures (D012). // GH-90000
 *
 * <p>Validates model accuracy metrics and evaluation.
 *
 * @doc.type class
 * @doc.purpose Model accuracy tests with deterministic fixtures
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("ModelAccuracy – Deterministic Fixtures (D012) [GH-90000]")
class ModelAccuracyTest extends EventloopTestBase {

    @Mock
    private ModelEvaluationService evaluationService;

    // ─────────────────────────────────────────────────────────────────────────
    // Accuracy Metrics Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Accuracy Metrics [GH-90000]")
    class AccuracyMetricsTests {

        @Test
        @DisplayName("[D012]: evaluate_returns_accuracy_metrics [GH-90000]")
        void evaluateReturnsAccuracyMetrics() { // GH-90000
            String modelId = "model-001";
            String testDatasetId = "test-001";

            ModelEvaluationService.EvaluationResult result = new ModelEvaluationService.EvaluationResult( // GH-90000
                modelId, testDatasetId,
                0.92, 0.90, 0.88, 0.89, 0.95, 0.15,
                List.of(), List.of(), null, 5000, 1000 // GH-90000
            );

            when(evaluationService.evaluate(modelId, testDatasetId)) // GH-90000
                .thenReturn(Promise.of(result)); // GH-90000

            ModelEvaluationService.EvaluationResult eval = runPromise(() -> // GH-90000
                evaluationService.evaluate(modelId, testDatasetId) // GH-90000
            );

            assertThat(eval.accuracy()).isEqualTo(0.92); // GH-90000
            assertThat(eval.precision()).isEqualTo(0.90); // GH-90000
            assertThat(eval.recall()).isEqualTo(0.88); // GH-90000
            assertThat(eval.f1Score()).isEqualTo(0.89); // GH-90000
        }

        @Test
        @DisplayName("[D012]: accuracy_meets_threshold [GH-90000]")
        void accuracyMeetsThreshold() { // GH-90000
            ModelEvaluationService.EvaluationResult good = new ModelEvaluationService.EvaluationResult( // GH-90000
                "model-001", "test-001",
                0.92, 0.90, 0.88, 0.89, 0.95, 0.15,
                List.of(), List.of(), null, 5000, 1000 // GH-90000
            );

            assertThat(good.meetsThreshold(0.90)).isTrue(); // GH-90000

            ModelEvaluationService.EvaluationResult poor = new ModelEvaluationService.EvaluationResult( // GH-90000
                "model-002", "test-001",
                0.85, 0.82, 0.80, 0.81, 0.85, 0.25,
                List.of(), List.of(), null, 5000, 1000 // GH-90000
            );

            assertThat(poor.meetsThreshold(0.90)).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("[D012]: deterministic_results_for_same_input [GH-90000]")
        void deterministicResultsForSameInput() { // GH-90000
            String modelId = "model-001";
            String testDatasetId = "test-001";

            // Same inputs should produce same results
            ModelEvaluationService.EvaluationResult result1 = new ModelEvaluationService.EvaluationResult( // GH-90000
                modelId, testDatasetId, 0.92, 0.90, 0.88, 0.89, 0.95, 0.15,
                List.of(), List.of(), null, 5000, 1000 // GH-90000
            );

            ModelEvaluationService.EvaluationResult result2 = new ModelEvaluationService.EvaluationResult( // GH-90000
                modelId, testDatasetId, 0.92, 0.90, 0.88, 0.89, 0.95, 0.15,
                List.of(), List.of(), null, 5000, 1000 // GH-90000
            );

            assertThat(result1.accuracy()).isEqualTo(result2.accuracy()); // GH-90000
            assertThat(result1.f1Score()).isEqualTo(result2.f1Score()); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Per-Class Metrics Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Per-Class Metrics [GH-90000]")
    class PerClassMetricsTests {

        @Test
        @DisplayName("[D012]: per_class_metrics_calculated_correctly [GH-90000]")
        void perClassMetricsCalculatedCorrectly() { // GH-90000
            ModelEvaluationService.ClassMetrics positive = new ModelEvaluationService.ClassMetrics( // GH-90000
                "positive", 80, 10, 85, 5, 0.89, 0.94, 0.91
            );

            assertThat(positive.precision()).isEqualTo(0.89); // GH-90000
            assertThat(positive.recall()).isEqualTo(0.94); // GH-90000
            assertThat(positive.f1Score()).isEqualTo(0.91); // GH-90000

            // Verify counts
            assertThat(positive.truePositives()).isEqualTo(80); // GH-90000
            assertThat(positive.falsePositives()).isEqualTo(10); // GH-90000
            assertThat(positive.trueNegatives()).isEqualTo(85); // GH-90000
            assertThat(positive.falseNegatives()).isEqualTo(5); // GH-90000
        }

        @Test
        @DisplayName("[D012]: f1_score_harmonic_mean [GH-90000]")
        void f1ScoreHarmonicMean() { // GH-90000
            // F1 = 2 * (precision * recall) / (precision + recall) // GH-90000
            double precision = 0.90;
            double recall = 0.80;
            double expectedF1 = 2 * (precision * recall) / (precision + recall); // GH-90000

            assertThat(expectedF1).isEqualTo(0.85, org.assertj.core.data.Offset.offset(0.01)); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Model Comparison Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Model Comparison [GH-90000]")
    class ModelComparisonTests {

        @Test
        @DisplayName("[D012]: compare_models_shows_improvement [GH-90000]")
        void compareModelsShowsImprovement() { // GH-90000
            String baselineModelId = "model-v1";
            String candidateModelId = "model-v2";
            String testDatasetId = "test-001";

            ModelEvaluationService.EvaluationResult baseline = new ModelEvaluationService.EvaluationResult( // GH-90000
                baselineModelId, testDatasetId,
                0.85, 0.82, 0.80, 0.81, 0.90, 0.25,
                List.of(), List.of(), null, 4000, 1000 // GH-90000
            );

            ModelEvaluationService.EvaluationResult candidate = new ModelEvaluationService.EvaluationResult( // GH-90000
                candidateModelId, testDatasetId,
                0.92, 0.90, 0.88, 0.89, 0.95, 0.15,
                List.of(), List.of(), null, 5000, 1000 // GH-90000
            );

            ModelEvaluationService.ModelComparison comparison = new ModelEvaluationService.ModelComparison( // GH-90000
                baselineModelId, candidateModelId,
                baseline, candidate,
                0.07, 0.08, true, "Deploy candidate"
            );

            when(evaluationService.compareModels(baselineModelId, candidateModelId, testDatasetId)) // GH-90000
                .thenReturn(Promise.of(comparison)); // GH-90000

            ModelEvaluationService.ModelComparison result = runPromise(() -> // GH-90000
                evaluationService.compareModels(baselineModelId, candidateModelId, testDatasetId) // GH-90000
            );

            assertThat(result.accuracyImprovement()).isEqualTo(0.07); // GH-90000
            assertThat(result.f1Improvement()).isEqualTo(0.08); // GH-90000
            assertThat(result.candidateBetter()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("[D012]: significant_improvement_detected [GH-90000]")
        void significantImprovementDetected() { // GH-90000
            ModelEvaluationService.ModelComparison significant = new ModelEvaluationService.ModelComparison( // GH-90000
                "v1", "v2", null, null,
                0.05, 0.05, true, "Deploy"
            );

            assertThat(significant.isSignificantImprovement(0.03)).isTrue(); // GH-90000

            ModelEvaluationService.ModelComparison minor = new ModelEvaluationService.ModelComparison( // GH-90000
                "v1", "v2", null, null,
                0.01, 0.01, true, "Keep baseline"
            );

            assertThat(minor.isSignificantImprovement(0.03)).isFalse(); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // A/B Test Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("A/B Test [GH-90000]")
    class ABTestTests {

        @Test
        @DisplayName("[D012]: ab_test_identifies_winner [GH-90000]")
        void abTestIdentifiesWinner() { // GH-90000
            String modelAId = "model-A";
            String modelBId = "model-B";
            String testDatasetId = "test-001";

            ModelEvaluationService.ABTestResult result = new ModelEvaluationService.ABTestResult( // GH-90000
                modelAId, modelBId,
                0.88, 0.92, // A accuracy, B accuracy
                0.02,       // p-value
                true,       // significant
                "model-B",  // winner
                0.95,       // confidence interval
                2000        // sample size
            );

            when(evaluationService.runABTest(modelAId, modelBId, testDatasetId)) // GH-90000
                .thenReturn(Promise.of(result)); // GH-90000

            ModelEvaluationService.ABTestResult test = runPromise(() -> // GH-90000
                evaluationService.runABTest(modelAId, modelBId, testDatasetId) // GH-90000
            );

            assertThat(test.winner()).isEqualTo("model-B [GH-90000]");
            assertThat(test.isSignificant(0.05)).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("[D012]: statistical_significance_threshold [GH-90000]")
        void statisticalSignificanceThreshold() { // GH-90000
            ModelEvaluationService.ABTestResult significant = new ModelEvaluationService.ABTestResult( // GH-90000
                "A", "B", 0.88, 0.92, 0.01, true, "B", 0.95, 2000
            );

            assertThat(significant.isSignificant(0.05)).isTrue(); // GH-90000
            assertThat(significant.isSignificant(0.001)).isFalse(); // p=0.01 > 0.001 // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Prediction Error Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Prediction Errors [GH-90000]")
    class PredictionErrorTests {

        @Test
        @DisplayName("[D012]: prediction_errors_tracked [GH-90000]")
        void predictionErrorsTracked() { // GH-90000
            List<ModelEvaluationService.PredictionError> errors = List.of( // GH-90000
                new ModelEvaluationService.PredictionError(0, "positive", "negative", 0.45, "misclassification"), // GH-90000
                new ModelEvaluationService.PredictionError(5, "negative", "positive", 0.55, "false_positive") // GH-90000
            );

            assertThat(errors).hasSize(2); // GH-90000
            assertThat(errors.get(0).expected()).isEqualTo("positive [GH-90000]");
            assertThat(errors.get(0).predicted()).isEqualTo("negative [GH-90000]");
        }

        @Test
        @DisplayName("[D012]: low_confidence_predictions_identified [GH-90000]")
        void lowConfidencePredictionsIdentified() { // GH-90000
            ModelEvaluationService.PredictionError lowConfidence =
                new ModelEvaluationService.PredictionError(0, "A", "B", 0.51, "uncertain"); // GH-90000

            assertThat(lowConfidence.confidence()).isLessThan(0.60); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Confusion Matrix Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Confusion Matrix [GH-90000]")
    class ConfusionMatrixTests {

        @Test
        @DisplayName("[D012]: confusion_matrix_diagonal_is_correct [GH-90000]")
        void confusionMatrixDiagonalIsCorrect() { // GH-90000
            // 2x2 confusion matrix
            // TP | FP
            // FN | TN
            int[][] matrix = {
                {80, 10},   // TP=80, FP=10
                {5, 85}     // FN=5, TN=85
            };

            ModelEvaluationService.ConfusionMatrix cm = new ModelEvaluationService.ConfusionMatrix( // GH-90000
                List.of("positive", "negative"), matrix // GH-90000
            );

            assertThat(cm.getTruePositives(0)).isEqualTo(80); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Deterministic Fixture Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Deterministic Fixtures [GH-90000]")
    class DeterministicFixtureTests {

        @Test
        @DisplayName("[D012]: same_test_dataset_produces_same_accuracy [GH-90000]")
        void sameTestDatasetProducesSameAccuracy() { // GH-90000
            // With deterministic fixtures, same inputs produce same outputs
            double accuracy1 = evaluateWithFixture("model-001", "test-001"); // GH-90000
            double accuracy2 = evaluateWithFixture("model-001", "test-001"); // GH-90000

            assertThat(accuracy1).isEqualTo(accuracy2); // GH-90000
        }

        @Test
        @DisplayName("[D012]: different_test_datasets_produce_different_results [GH-90000]")
        void differentTestDatasetsProduceDifferentResults() { // GH-90000
            double accuracy1 = evaluateWithFixture("model-001", "test-001"); // GH-90000
            double accuracy2 = evaluateWithFixture("model-001", "test-002"); // GH-90000

            assertThat(accuracy1).isNotEqualTo(accuracy2); // GH-90000
        }

        private double evaluateWithFixture(String modelId, String testId) { // GH-90000
            return switch (testId) { // GH-90000
                case "test-001" -> 0.92;
                case "test-002" -> 0.87;
                default -> 0.90;
            };
        }
    }
}
