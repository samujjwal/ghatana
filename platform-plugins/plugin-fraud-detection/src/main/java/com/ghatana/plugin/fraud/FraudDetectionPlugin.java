package com.ghatana.plugin.fraud;

import com.ghatana.platform.plugin.Plugin;
import io.activej.promise.Promise;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Fraud Detection Plugin - Platform-level behavioral anomaly and fraud signal framework.
 *
 * <p>Provides product-agnostic fraud detection capabilities:</p>
 * <ul>
 *   <li>Transaction anomaly detection</li>
 *   <li>Behavioral fraud signals and pattern recognition</li>
 *   <li>Identity fraud and account takeover signals</li>
 *   <li>Velocity-based abuse detection</li>
 * </ul>
 *
 * <p>Products supply domain-specific scoring models via registered evaluators;
 * no product terms appear in this interface.</p>
 *
 * @doc.type interface
 * @doc.purpose Platform fraud detection and behavioral anomaly signal framework
 * @doc.layer platform
 * @doc.pattern Plugin
 * @since 1.0.0
 */
public interface FraudDetectionPlugin extends Plugin {

    /**
     * Assesses a transaction for fraud risk.
     *
     * @param transactionId the transaction identifier
     * @param request the fraud detection request
     * @return Promise containing the fraud assessment
     */
    Promise<FraudAssessment> assessTransaction(String transactionId, FraudDetectionRequest request);

    /**
     * Detects fraud patterns across a time window.
     *
     * @param productId the product identifier
     * @param window the time window
     * @return Promise containing detected patterns
     */
    Promise<FraudPattern> detectPatterns(String productId, TimeWindow window);

    /**
     * Trains a fraud detection model.
     *
     * @param modelId the model identifier
     * @param data the training data
     * @return Promise completing when training is done
     */
    Promise<Void> trainModel(String modelId, TrainingData data);

    /**
     * Gets model performance metrics.
     *
     * @param modelId the model identifier
     * @return Promise containing model metrics
     */
    Promise<ModelMetrics> getModelMetrics(String modelId);

    /**
     * Registers a custom fraud rule for a product.
     *
     * @param productId the product identifier
     * @param rule the fraud rule
     * @return Promise completing when rule is registered
     */
    Promise<Void> registerRule(String productId, FraudRule rule);

    /**
     * Fraud detection request.
     */
    record FraudDetectionRequest(
        String entityId,
        String entityType,
        Map<String, Object> features,
        String modelId
    ) {}

    /**
     * Fraud assessment result.
     */
    record FraudAssessment(
        String transactionId,
        double riskScore,
        RiskLevel riskLevel,
        List<String> triggeredRules,
        String explanation,
        Instant assessedAt
    ) {
        public enum RiskLevel {
            LOW, MEDIUM, HIGH, CRITICAL
        }
    }

    /**
     * Detected fraud pattern.
     */
    record FraudPattern(
        String patternId,
        String patternType,
        List<String> affectedEntities,
        double confidence,
        Instant detectedAt
    ) {}

    /**
     * Fraud rule.
     */
    record FraudRule(
        String ruleId,
        String ruleType,
        String description,
        double threshold
    ) {}

    /**
     * Model metrics.
     */
    record ModelMetrics(
        String modelId,
        double precision,
        double recall,
        double f1Score,
        int totalPredictions,
        int falsePositives,
        int falseNegatives,
        Instant calculatedAt
    ) {}

    /**
     * Training data.
     */
    record TrainingData(
        List<TrainingExample> examples,
        Map<String, Object> parameters
    ) {
        public record TrainingExample(
            Map<String, Object> features,
            boolean isFraud
        ) {}
    }

    /**
     * Time window for pattern detection.
     */
    record TimeWindow(Instant start, Instant end) {}
}
