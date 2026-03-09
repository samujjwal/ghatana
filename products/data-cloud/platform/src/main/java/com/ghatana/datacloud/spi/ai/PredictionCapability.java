package com.ghatana.datacloud.spi.ai;

import io.activej.promise.Promise;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.Map;

/**
 * Optional AI capability for plugins that support predictive analytics.
 *
 * <p>
 * <b>Purpose</b><br>
 * Enables storage plugins to provide ML-powered predictions about:
 * <ul>
 * <li>Query performance (latency, resource usage)</li>
 * <li>Data patterns and trends</li>
 * <li>Capacity and scaling needs</li>
 * <li>Optimal storage tier placement</li>
 * </ul>
 *
 * <p>
 * <b>Safety Contract</b><br>
 * Predictions are <b>advisory only</b>:
 * <ul>
 * <li>Never mutate Tier-1 state autonomously</li>
 * <li>Always return confidence scores</li>
 * <li>Include rationale/explanation</li>
 * <li>Log all predictions as learning signals</li>
 * </ul>
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * if (plugin instanceof PredictionCapability predictor) {
 *     PredictionResult result = predictor.predict(
 *         PredictionRequest.builder()
 *             .predictionType(PredictionType.QUERY_LATENCY)
 *             .context(Map.of(
 *                 "query", queryText,
 *                 "rowCount", estimatedRows
 *             ))
 *             .build()
 *     ).getResult();
 *
 *     if (result.confidence() > 0.8) {
 *         // Use prediction to optimize routing
 *     }
 * }
 * }</pre>
 *
 * @see com.ghatana.datacloud.spi.StoragePlugin
 * @see com.ghatana.datacloud.spi.ai.RecommendationCapability
 * @doc.type interface
 * @doc.purpose AI prediction capability
 * @doc.layer core
 * @doc.pattern Capability Interface
 */
public interface PredictionCapability {

    /**
     * Makes a prediction based on the provided context.
     *
     * @param request Prediction request with type and context
     * @return Promise with prediction result
     */
    Promise<PredictionResult> predict(PredictionRequest request);

    /**
     * Gets the supported prediction types for this plugin.
     *
     * @return Promise with list of supported types
     */
    Promise<java.util.List<PredictionType>> getSupportedPredictionTypes();

    /**
     * Prediction request containing type and context.
     */
    @Value
    @Builder
    class PredictionRequest {
        /**
         * Type of prediction requested.
         */
        PredictionType predictionType;

        /**
         * Tenant ID for multi-tenancy.
         */
        String tenantId;

        /**
         * Collection name if applicable.
         */
        String collectionName;

        /**
         * Context data for prediction (query text, historical metrics, etc.).
         */
        Map<String, Object> context;

        /**
         * Timestamp of the request.
         */
        @Builder.Default
        Instant timestamp = Instant.now();

        /**
         * Correlation ID for tracing.
         */
        String correlationId;
    }

    /**
     * Prediction result with confidence and explanation.
     */
    @Value
    @Builder
    class PredictionResult {
        /**
         * Type of prediction.
         */
        PredictionType predictionType;

        /**
         * Predicted value (numeric, categorical, or complex object).
         */
        Object predictedValue;

        /**
         * Confidence score (0.0 to 1.0).
         */
        double confidence;

        /**
         * Human-readable explanation of the prediction.
         */
        String explanation;

        /**
         * Supporting evidence and features used.
         */
        Map<String, Object> evidence;

        /**
         * Model metadata (name, version, training date).
         */
        Map<String, String> modelMetadata;

        /**
         * Timestamp of prediction.
         */
        @Builder.Default
        Instant timestamp = Instant.now();

        /**
         * Whether this prediction should be used (high confidence + determinism).
         */
        public boolean isActionable() {
            return confidence >= 0.8;
        }

        /**
         * Determinism level of the prediction.
         */
        @Builder.Default
        DeterminismLevel determinism = DeterminismLevel.MEDIUM;
    }

    /**
     * Types of predictions supported.
     */
    enum PredictionType {
        /**
         * Predict query execution latency.
         */
        QUERY_LATENCY,

        /**
         * Predict query cost/resource usage.
         */
        QUERY_COST,

        /**
         * Predict optimal storage tier for data.
         */
        STORAGE_TIER,

        /**
         * Predict capacity needs.
         */
        CAPACITY_FORECAST,

        /**
         * Predict data hotness/access patterns.
         */
        DATA_HOTNESS,

        /**
         * Predict optimal index strategy.
         */
        INDEX_RECOMMENDATION,

        /**
         * Predict data quality issues.
         */
        DATA_QUALITY,

        /**
         * Custom plugin-specific prediction.
         */
        CUSTOM
    }

    /**
     * Determinism level of predictions.
     */
    enum DeterminismLevel {
        /**
         * High determinism - based on rules/constraints.
         */
        HIGH,

        /**
         * Medium determinism - statistical/ML-based.
         */
        MEDIUM,

        /**
         * Low determinism - exploratory/experimental.
         */
        LOW
    }
}

