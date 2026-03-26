package com.ghatana.kernel.observability;

import com.ghatana.kernel.context.KernelContext;

import java.util.Map;

/**
 * Framework for AI decision explainability.
 *
 * <p>Generates human-readable explanations for AI/ML decisions,
 * supporting transparency, compliance, and trust.</p>
 *
 * @doc.type interface
 * @doc.purpose AI decision explainability and transparency
 * @doc.layer core
 * @doc.pattern Service
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
public interface ExplainabilityFramework {

    /**
     * Generates an explanation for an agent action.
     *
     * @param action the agent action
     * @param context the execution context
     * @return explanation
     */
    Explanation generateExplanation(KernelTelemetryManager.AgentAction action, ExecutionContext context);

    /**
     * Records a decision explanation.
     *
     * @param decisionId the decision identifier
     * @param explanation the explanation
     */
    void recordDecisionExplanation(String decisionId, Explanation explanation);

    /**
     * Gets a recorded explanation.
     *
     * @param decisionId the decision identifier
     * @return explanation or null if not found
     */
    Explanation getExplanation(String decisionId);

    /**
     * Validates explanation quality.
     *
     * @param explanation the explanation to validate
     * @return validation result
     */
    ValidationResult validateExplanation(Explanation explanation);

    /**
     * Represents an AI decision explanation.
     */
    class Explanation {
        private final String decisionId;
        private final String summary;
        private final String detailedReasoning;
        private final Map<String, Double> featureContributions;
        private final double confidence;
        private final String modelId;
        private final Map<String, Object> metadata;

        private Explanation(Builder builder) {
            this.decisionId = builder.decisionId;
            this.summary = builder.summary;
            this.detailedReasoning = builder.detailedReasoning;
            this.featureContributions = builder.featureContributions;
            this.confidence = builder.confidence;
            this.modelId = builder.modelId;
            this.metadata = builder.metadata;
        }

        public String getDecisionId() { return decisionId; }
        public String getSummary() { return summary; }
        public String getDetailedReasoning() { return detailedReasoning; }
        public Map<String, Double> getFeatureContributions() { return featureContributions; }
        public double getConfidence() { return confidence; }
        public String getModelId() { return modelId; }
        public Map<String, Object> getMetadata() { return metadata; }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String decisionId;
            private String summary;
            private String detailedReasoning;
            private Map<String, Double> featureContributions;
            private double confidence;
            private String modelId;
            private Map<String, Object> metadata;

            public Builder decisionId(String decisionId) {
                this.decisionId = decisionId;
                return this;
            }

            public Builder summary(String summary) {
                this.summary = summary;
                return this;
            }

            public Builder detailedReasoning(String detailedReasoning) {
                this.detailedReasoning = detailedReasoning;
                return this;
            }

            public Builder featureContributions(Map<String, Double> featureContributions) {
                this.featureContributions = featureContributions;
                return this;
            }

            public Builder confidence(double confidence) {
                this.confidence = confidence;
                return this;
            }

            public Builder modelId(String modelId) {
                this.modelId = modelId;
                return this;
            }

            public Builder metadata(Map<String, Object> metadata) {
                this.metadata = metadata;
                return this;
            }

            public Explanation build() {
                return new Explanation(this);
            }
        }
    }

    /**
     * Execution context for explanation generation.
     */
    interface ExecutionContext {
        KernelContext getKernelContext();
        Map<String, Object> getInputs();
        Map<String, Object> getOutputs();
        String getAgentId();
    }

    /**
     * Validation result for explanation quality.
     */
    class ValidationResult {
        private final boolean valid;
        private final double qualityScore;
        private final String feedback;

        public ValidationResult(boolean valid, double qualityScore, String feedback) {
            this.valid = valid;
            this.qualityScore = qualityScore;
            this.feedback = feedback;
        }

        public boolean isValid() { return valid; }
        public double getQualityScore() { return qualityScore; }
        public String getFeedback() { return feedback; }
    }
}
