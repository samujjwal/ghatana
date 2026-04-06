package com.ghatana.kernel.ai;

import java.util.Map;

/**
 * Model governance service for AI/ML model management.
 *
 * <p>Provides governance, compliance, and lifecycle management for AI/ML models.
 * Ensures models are approved, compliant, and performing within acceptable bounds.</p>
 *
 * @doc.type interface
 * @doc.purpose AI/ML model governance and compliance
 * @doc.layer core
 * @doc.pattern Service
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
public interface ModelGovernanceService {

    /**
     * Gets model approval status.
     *
     * @param modelId the model identifier
     * @return model approval
     */
    ModelApproval getModelApproval(String modelId);

    /**
     * Validates model usage for a request.
     *
     * @param modelId the model identifier
     * @param request the agent request
     * @throws ModelNotApprovedException if model is not approved
     */
    void validateModelUsage(String modelId, AgentOrchestrator.AgentRequest request);

    /**
     * Records model performance metrics.
     *
     * @param modelId the model identifier
     * @param metrics the performance metrics
     */
    void recordModelPerformance(String modelId, ModelPerformanceMetrics metrics);

    /**
     * Checks if model is compliant with policy.
     *
     * @param modelId the model identifier
     * @param policy the compliance policy
     * @return true if compliant
     */
    boolean isModelCompliant(String modelId, CompliancePolicy policy);

    /**
     * Registers a new model for governance.
     *
     * @param model the model to register
     */
    void registerModel(ModelRegistration model);

    /**
     * Gets model metadata.
     *
     * @param modelId the model identifier
     * @return model metadata
     */
    ModelMetadata getModelMetadata(String modelId);

    /**
     * Model approval status.
     */
    class ModelApproval {
        private final String modelId;
        private final boolean approved;
        private final String approver;
        private final long approvalDate;
        private final String version;
        private final Map<String, Object> conditions;

        private ModelApproval(Builder builder) {
            this.modelId = builder.modelId;
            this.approved = builder.approved;
            this.approver = builder.approver;
            this.approvalDate = builder.approvalDate;
            this.version = builder.version;
            this.conditions = builder.conditions;
        }

        public String getModelId() { return modelId; }
        public boolean isApproved() { return approved; }
        public String getApprover() { return approver; }
        public long getApprovalDate() { return approvalDate; }
        public String getVersion() { return version; }
        public Map<String, Object> getConditions() { return conditions; }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String modelId;
            private boolean approved;
            private String approver;
            private long approvalDate;
            private String version;
            private Map<String, Object> conditions;

            public Builder modelId(String modelId) {
                this.modelId = modelId;
                return this;
            }

            public Builder approved(boolean approved) {
                this.approved = approved;
                return this;
            }

            public Builder approver(String approver) {
                this.approver = approver;
                return this;
            }

            public Builder approvalDate(long approvalDate) {
                this.approvalDate = approvalDate;
                return this;
            }

            public Builder version(String version) {
                this.version = version;
                return this;
            }

            public Builder conditions(Map<String, Object> conditions) {
                this.conditions = conditions;
                return this;
            }

            public ModelApproval build() {
                return new ModelApproval(this);
            }
        }
    }

    /**
     * Model performance metrics.
     */
    class ModelPerformanceMetrics {
        private final double confidence;
        private final double accuracy;
        private final long latencyMillis;
        private final Map<String, Double> customMetrics;

        public ModelPerformanceMetrics(double confidence, double accuracy, long latencyMillis, 
                                      Map<String, Double> customMetrics) {
            this.confidence = confidence;
            this.accuracy = accuracy;
            this.latencyMillis = latencyMillis;
            this.customMetrics = customMetrics;
        }

        public ModelPerformanceMetrics(double confidence, double accuracy) {
            this(confidence, accuracy, 0, Map.of());
        }

        public double getConfidence() { return confidence; }
        public double getAccuracy() { return accuracy; }
        public long getLatencyMillis() { return latencyMillis; }
        public Map<String, Double> getCustomMetrics() { return customMetrics; }
    }

    /**
     * Compliance policy for models.
     */
    interface CompliancePolicy {
        String getPolicyId();
        String getName();
        boolean evaluate(ModelMetadata metadata);
        java.util.List<String> getRequirements();
    }

    /**
     * Model registration information.
     */
    class ModelRegistration {
        private final String modelId;
        private final String name;
        private final String version;
        private final String type;
        private final Map<String, Object> metadata;

        public ModelRegistration(String modelId, String name, String version, String type, 
                                Map<String, Object> metadata) {
            this.modelId = modelId;
            this.name = name;
            this.version = version;
            this.type = type;
            this.metadata = metadata;
        }

        public String getModelId() { return modelId; }
        public String getName() { return name; }
        public String getVersion() { return version; }
        public String getType() { return type; }
        public Map<String, Object> getMetadata() { return metadata; }
    }

    /**
     * Model metadata.
     */
    interface ModelMetadata {
        String getModelId();
        String getName();
        String getVersion();
        String getType();
        Map<String, Object> getAttributes();
        long getCreatedDate();
        long getLastUpdated();
    }

    /**
     * Exception thrown when model is not approved.
     */
    class ModelNotApprovedException extends RuntimeException {
        public ModelNotApprovedException(String message) {
            super(message);
        }
    }
}
