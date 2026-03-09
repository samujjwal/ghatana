package com.ghatana.datacloud.spi.ai;

import io.activej.promise.Promise;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Optional AI capability for plugins that provide explainability.
 *
 * <p>
 * <b>Purpose</b><br>
 * Enables transparent explanation of:
 * <ul>
 * <li>Query execution plans and optimizations</li>
 * <li>AI/ML model decisions and predictions</li>
 * <li>Performance characteristics</li>
 * <li>Data lineage and provenance</li>
 * <li>Policy enforcement decisions</li>
 * </ul>
 *
 * <p>
 * <b>Transparency Principles</b><br>
 * <ul>
 * <li>All AI outputs must be explainable</li>
 * <li>Deterministic decisions have full provenance</li>
 * <li>ML predictions include feature importance</li>
 * <li>Healthcare/enterprise audit ready</li>
 * </ul>
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * if (plugin instanceof ExplanationCapability explainer) {
 *     Explanation explanation = explainer.explain(
 *         ExecutionContext.builder()
 *             .tenantId("tenant-123")
 *             .operationType(OperationType.QUERY)
 *             .operationId("query-123")
 *             .build()
 *     ).getResult();
 *
 *     // Display to user or log for audit
 *     System.out.println(explanation.summary());
 *     for (ExplanationStep step : explanation.steps()) {
 *         System.out.println(" - " + step.description());
 *     }
 * }
 * }</pre>
 *
 * @see com.ghatana.datacloud.spi.StoragePlugin
 * @see PredictionCapability
 * @doc.type interface
 * @doc.purpose AI explainability capability
 * @doc.layer core
 * @doc.pattern Capability Interface
 */
public interface ExplanationCapability {

    /**
     * Generates an explanation for an operation or decision.
     *
     * @param context Execution context to explain
     * @return Promise with explanation
     */
    Promise<Explanation> explain(ExecutionContext context);

    /**
     * Gets the provenance chain for a piece of data.
     *
     * @param tenantId Tenant ID
     * @param recordId Record ID
     * @return Promise with provenance chain
     */
    Promise<ProvenanceChain> getProvenance(String tenantId, java.util.UUID recordId);

    /**
     * Explains why a prediction was made (for AI capabilities).
     *
     * @param predictionId Prediction ID to explain
     * @return Promise with prediction explanation
     */
    Promise<PredictionExplanation> explainPrediction(String predictionId);

    /**
     * Context for generating explanations.
     */
    @Value
    @Builder
    class ExecutionContext {
        /**
         * Tenant ID.
         */
        String tenantId;

        /**
         * Type of operation to explain.
         */
        OperationType operationType;

        /**
         * Unique operation ID (query ID, prediction ID, etc.).
         */
        String operationId;

        /**
         * Additional context data.
         */
        Map<String, Object> metadata;

        /**
         * Detail level requested.
         */
        @Builder.Default
        DetailLevel detailLevel = DetailLevel.MEDIUM;

        /**
         * Correlation ID for tracing.
         */
        String correlationId;

        /**
         * Timestamp of request.
         */
        @Builder.Default
        Instant timestamp = Instant.now();
    }

    /**
     * Comprehensive explanation of an operation or decision.
     */
    @Value
    @Builder
    class Explanation {
        /**
         * Operation type being explained.
         */
        OperationType operationType;

        /**
         * Operation ID.
         */
        String operationId;

        /**
         * High-level summary.
         */
        String summary;

        /**
         * Detailed explanation steps.
         */
        List<ExplanationStep> steps;

        /**
         * Determinism level of the operation.
         */
        PredictionCapability.DeterminismLevel determinism;

        /**
         * Key decisions made.
         */
        List<Decision> decisions;

        /**
         * Performance metrics.
         */
        Map<String, Object> metrics;

        /**
         * Data sources used.
         */
        List<String> dataSources;

        /**
         * Policies applied.
         */
        List<String> policiesApplied;

        /**
         * Timestamp of explanation.
         */
        @Builder.Default
        Instant timestamp = Instant.now();
    }

    /**
     * A single step in an explanation.
     */
    @Value
    @Builder
    class ExplanationStep {
        /**
         * Step number.
         */
        int stepNumber;

        /**
         * Step type.
         */
        StepType type;

        /**
         * Human-readable description.
         */
        String description;

        /**
         * Why this step was taken.
         */
        String rationale;

        /**
         * Input data for this step.
         */
        Map<String, Object> inputs;

        /**
         * Output data from this step.
         */
        Map<String, Object> outputs;

        /**
         * Duration of this step.
         */
        java.time.Duration duration;

        /**
         * Cost/resource usage.
         */
        Map<String, Double> cost;
    }

    /**
     * A decision point in the execution.
     */
    @Value
    @Builder
    class Decision {
        /**
         * Decision point name.
         */
        String name;

        /**
         * What was decided.
         */
        String decision;

        /**
         * Why this decision was made.
         */
        String rationale;

        /**
         * Alternatives considered.
         */
        List<Alternative> alternatives;

        /**
         * Confidence in decision (if ML-based).
         */
        Double confidence;
    }

    /**
     * An alternative that was considered but not chosen.
     */
    @Value
    @Builder
    class Alternative {
        /**
         * Alternative option.
         */
        String option;

        /**
         * Why it was not chosen.
         */
        String reason;

        /**
         * Estimated impact if chosen.
         */
        Map<String, Object> estimatedImpact;
    }

    /**
     * Provenance chain showing data lineage.
     */
    @Value
    @Builder
    class ProvenanceChain {
        /**
         * Record ID.
         */
        java.util.UUID recordId;

        /**
         * Provenance events in chronological order.
         */
        List<ProvenanceEvent> events;

        /**
         * Complete lineage graph.
         */
        Map<String, Object> lineageGraph;
    }

    /**
     * A single provenance event.
     */
    @Value
    @Builder
    class ProvenanceEvent {
        /**
         * Event type.
         */
        String eventType;

        /**
         * Actor (user, system, service).
         */
        String actor;

        /**
         * Action performed.
         */
        String action;

        /**
         * Timestamp of event.
         */
        Instant timestamp;

        /**
         * Additional context.
         */
        Map<String, Object> context;
    }

    /**
     * Explanation specific to ML predictions.
     */
    @Value
    @Builder
    class PredictionExplanation {
        /**
         * Prediction ID.
         */
        String predictionId;

        /**
         * Prediction type.
         */
        PredictionCapability.PredictionType predictionType;

        /**
         * Predicted value.
         */
        Object predictedValue;

        /**
         * Confidence score.
         */
        double confidence;

        /**
         * Feature importance (most influential features).
         */
        List<FeatureImportance> featureImportances;

        /**
         * Model information.
         */
        ModelInfo modelInfo;

        /**
         * Similar examples from training data.
         */
        List<Map<String, Object>> similarExamples;

        /**
         * Counterfactual: what would change the prediction.
         */
        String counterfactual;
    }

    /**
     * Feature importance in a prediction.
     */
    @Value
    @Builder
    class FeatureImportance {
        /**
         * Feature name.
         */
        String featureName;

        /**
         * Importance score (relative contribution).
         */
        double importance;

        /**
         * Feature value used.
         */
        Object featureValue;

        /**
         * Direction of influence (positive/negative).
         */
        String influence;
    }

    /**
     * Information about the ML model.
     */
    @Value
    @Builder
    class ModelInfo {
        /**
         * Model name.
         */
        String modelName;

        /**
         * Model version.
         */
        String modelVersion;

        /**
         * Training date.
         */
        Instant trainingDate;

        /**
         * Training accuracy metrics.
         */
        Map<String, Double> accuracy;

        /**
         * Model type (e.g., "RandomForest", "NeuralNetwork").
         */
        String modelType;
    }

    /**
     * Types of operations that can be explained.
     */
    enum OperationType {
        /**
         * Query execution.
         */
        QUERY,

        /**
         * Prediction.
         */
        PREDICTION,

        /**
         * Recommendation.
         */
        RECOMMENDATION,

        /**
         * Anomaly detection.
         */
        ANOMALY_DETECTION,

        /**
         * Policy enforcement.
         */
        POLICY_ENFORCEMENT,

        /**
         * Routing decision.
         */
        ROUTING,

        /**
         * Custom operation.
         */
        CUSTOM
    }

    /**
     * Level of detail in explanation.
     */
    enum DetailLevel {
        /**
         * High-level summary only.
         */
        LOW,

        /**
         * Moderate detail.
         */
        MEDIUM,

        /**
         * Full detailed trace.
         */
        HIGH,

        /**
         * Debug-level information.
         */
        DEBUG
    }

    /**
     * Types of explanation steps.
     */
    enum StepType {
        /**
         * Parsing/validation.
         */
        PARSE,

        /**
         * Planning/optimization.
         */
        PLAN,

        /**
         * Execution.
         */
        EXECUTE,

        /**
         * Aggregation/transformation.
         */
        TRANSFORM,

        /**
         * Result assembly.
         */
        ASSEMBLE,

        /**
         * Custom step type.
         */
        CUSTOM
    }
}

