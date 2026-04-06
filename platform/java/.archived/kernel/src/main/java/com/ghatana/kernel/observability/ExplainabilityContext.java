package com.ghatana.kernel.observability;

import java.util.Map;

/**
 * Context for tracking AI decision explainability.
 *
 * <p>Captures decision-making process, inputs, outputs, and reasoning
 * for AI/ML model decisions to support transparency and auditability.</p>
 *
 * @doc.type interface
 * @doc.purpose AI decision explainability tracking
 * @doc.layer core
 * @doc.pattern ValueObject
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
public interface ExplainabilityContext {

    /**
     * Gets the decision identifier.
     *
     * @return decision ID
     */
    String getDecisionId();

    /**
     * Gets the agent that made the decision.
     *
     * @return agent identifier
     */
    String getAgentId();

    /**
     * Gets the model used for the decision.
     *
     * @return model identifier
     */
    String getModelId();

    /**
     * Gets the input data for the decision.
     *
     * @return input data map
     */
    Map<String, Object> getInputs();

    /**
     * Gets the output/decision result.
     *
     * @return output data map
     */
    Map<String, Object> getOutputs();

    /**
     * Gets the reasoning/explanation for the decision.
     *
     * @return explanation text
     */
    String getExplanation();

    /**
     * Gets the confidence score for the decision.
     *
     * @return confidence score (0.0 to 1.0)
     */
    double getConfidence();

    /**
     * Gets feature importance scores.
     *
     * @return map of feature names to importance scores
     */
    Map<String, Double> getFeatureImportance();

    /**
     * Gets the timestamp when decision was made.
     *
     * @return timestamp in milliseconds
     */
    long getTimestamp();

    /**
     * Records the decision explanation.
     *
     * @param explanation the explanation text
     */
    void recordExplanation(String explanation);

    /**
     * Records feature importance.
     *
     * @param feature the feature name
     * @param importance the importance score
     */
    void recordFeatureImportance(String feature, double importance);
}
