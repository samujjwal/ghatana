/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.learning;

import io.activej.promise.Promise;

import java.util.List;
import java.util.Map;

/**
 * Service for model evaluation and accuracy testing.
 *
 * <p>Validates model accuracy and schema compliance.
 *
 * @doc.type interface
 * @doc.purpose Model evaluation and accuracy validation
 * @doc.layer product
 * @doc.pattern Service Interface
 */
public interface ModelEvaluationService {

    /**
     * Evaluate model on test dataset.
     *
     * @param modelId model identifier
         * @param testDatasetId test dataset
     * @return promise of evaluation results
     */
    Promise<EvaluationResult> evaluate(String modelId, String testDatasetId);

    /**
     * Validate input schema.
     *
     * @param modelId model identifier
     * @param sampleInput sample input data
     * @return promise of validation result
     */
    Promise<SchemaValidationResult> validateInputSchema(String modelId, Map<String, Object> sampleInput);

    /**
     * Validate output schema.
     *
     * @param modelId model identifier
     * @param sampleOutput sample output data
     * @return promise of validation result
     */
    Promise<SchemaValidationResult> validateOutputSchema(String modelId, Map<String, Object> sampleOutput);

    /**
     * Compare models.
     *
     * @param baselineModelId baseline model
     * @param candidateModelId candidate model
     * @param testDatasetId test dataset
     * @return promise of comparison results
     */
    Promise<ModelComparison> compareModels(String baselineModelId, String candidateModelId, String testDatasetId);

    /**
     * Run A/B test.
     *
     * @param modelAId first model
     * @param modelBId second model
     * @param testDatasetId test dataset
     * @return promise of A/B test results
     */
    Promise<ABTestResult> runABTest(String modelAId, String modelBId, String testDatasetId);

    /**
     * Evaluation result.
     */
    record EvaluationResult(
        String modelId,
        String testDatasetId,
        double accuracy,
        double precision,
        double recall,
        double f1Score,
        double auc,
        double logLoss,
        List<ClassMetrics> perClassMetrics,
        List<PredictionError> errors,
        ConfusionMatrix confusionMatrix,
        long evaluationTimeMs,
        int samplesEvaluated
    ) {
        public boolean meetsThreshold(double minAccuracy) {
            return accuracy >= minAccuracy;
        }
    }

    /**
     * Class metrics.
     */
    record ClassMetrics(
        String className,
        int truePositives,
        int falsePositives,
        int trueNegatives,
        int falseNegatives,
        double precision,
        double recall,
        double f1Score
    ) {}

    /**
     * Prediction error.
     */
    record PredictionError(
        int sampleIndex,
        String expected,
        String predicted,
        double confidence,
        String errorType
    ) {}

    /**
     * Confusion matrix.
     */
    record ConfusionMatrix(
        List<String> labels,
        int[][] matrix
    ) {
        public int getTruePositives(int classIndex) {
            return matrix[classIndex][classIndex];
        }
    }

    /**
     * Schema validation result.
     */
    record SchemaValidationResult(
        boolean valid,
        List<SchemaError> errors,
        List<String> warnings
    ) {
        public boolean isValid() {
            return valid && (errors == null || errors.isEmpty());
        }
    }

    /**
     * Schema error.
     */
    record SchemaError(
        String field,
        String expectedType,
        String actualType,
        String message
    ) {}

    /**
     * Model comparison.
     */
    record ModelComparison(
        String baselineModelId,
        String candidateModelId,
        EvaluationResult baseline,
        EvaluationResult candidate,
        double accuracyImprovement,
        double f1Improvement,
        boolean candidateBetter,
        String recommendation
    ) {
        public boolean isSignificantImprovement(double threshold) {
            return accuracyImprovement >= threshold && f1Improvement >= 0;
        }
    }

    /**
     * A/B test result.
     */
    record ABTestResult(
        String modelAId,
        String modelBId,
        double modelAAccuracy,
        double modelBAccuracy,
        double pValue,
        boolean statisticallySignificant,
        String winner,
        double confidenceInterval,
        int sampleSize
    ) {
        public boolean isSignificant(double alpha) {
            return pValue < alpha;
        }
    }
}
